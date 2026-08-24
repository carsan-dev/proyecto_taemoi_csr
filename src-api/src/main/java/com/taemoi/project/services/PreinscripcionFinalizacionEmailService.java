package com.taemoi.project.services;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.EstadoEmailFinalizacion;
import com.taemoi.project.entities.Preinscripcion;
import com.taemoi.project.entities.Turno;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.repositories.PreinscripcionRepository;
import com.taemoi.project.repositories.UsuarioRepository;

@Service
public class PreinscripcionFinalizacionEmailService {
	private static final Logger log = LoggerFactory.getLogger(PreinscripcionFinalizacionEmailService.class);

	private final PreinscripcionRepository preinscripciones;
	private final UsuarioRepository usuarios;
	private final EmailService emailService;

	@Value("${app.frontend.base-url:http://localhost:4200}")
	private String frontendBaseUrl;

	public PreinscripcionFinalizacionEmailService(PreinscripcionRepository preinscripciones,
			UsuarioRepository usuarios, EmailService emailService) {
		this.preinscripciones = preinscripciones;
		this.usuarios = usuarios;
		this.emailService = emailService;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void enviar(String referencia) {
		Preinscripcion preinscripcion = preinscripciones.findByReferenciaForUpdate(referencia)
				.orElseThrow(() -> new NoSuchElementException("Preinscripción no encontrada."));
		preinscripcion.setEmailFinalizacionIntentos(preinscripcion.getEmailFinalizacionIntentos() + 1);
		preinscripcion.setEstadoEmailFinalizacion(EstadoEmailFinalizacion.PENDIENTE);
		preinscripcion.setEmailFinalizacionEnviadoEn(null);
		preinscripcion.setEmailFinalizacionUltimoError(null);

		try {
			String destinatario = emailDefinitivo(preinscripcion);
			Usuario cuenta = usuarios.findByEmailIgnoreCase(destinatario).orElse(null);
			emailService.sendEmailSync(destinatario, "Inscripción confirmada · " + preinscripcion.getReferencia(),
					html(preinscripcion, destinatario, cuenta));
			preinscripcion.setEstadoEmailFinalizacion(EstadoEmailFinalizacion.ENVIADO);
			preinscripcion.setEmailFinalizacionEnviadoEn(Instant.now());
		} catch (Exception ex) {
			preinscripcion.setEstadoEmailFinalizacion(EstadoEmailFinalizacion.ERROR);
			preinscripcion.setEmailFinalizacionUltimoError(errorResumido(ex));
			log.warn("No se pudo enviar la confirmación final de {}: {}", referencia, ex.getMessage());
		}

		preinscripciones.save(preinscripcion);
	}

	String html(Preinscripcion p, String destinatario, Usuario cuenta) {
		List<Turno> horarios = horarios(p);
		Set<String> grupos = new LinkedHashSet<>();
		for (Turno turno : horarios) {
			if (turno.getGrupo() != null && turno.getGrupo().getNombre() != null) {
				grupos.add(turno.getGrupo().getNombre());
			}
		}
		if (grupos.isEmpty() && p.getGrupo() != null) {
			grupos.add(p.getGrupo().getNombre());
		}

		String filasHorarios = horarios.stream()
				.map(t -> "<li><strong>" + esc(t.getDiaSemana()) + "</strong> de " + esc(t.getHoraInicio())
						+ " a " + esc(t.getHoraFin()) + (t.getGrupo() == null ? "" : " · " + esc(t.getGrupo().getNombre()))
						+ "</li>")
				.reduce("", String::concat);
		String gruposTexto = grupos.isEmpty() ? "Según ficha de inscripción" : String.join(", ", grupos);
		String urlBase = frontendBaseUrl.replaceAll("/+$", "");
		String ctaUrl;
		String ctaTexto;
		String acceso;
		if (cuenta == null) {
			ctaUrl = urlBase + "/login?modo=registro&email=" + url(destinatario);
			ctaTexto = "Crear mi cuenta";
			acceso = "Aún no hay una cuenta asociada a este correo. Créala con el mismo correo de la ficha para acceder.";
		} else {
			ctaUrl = urlBase + "/login";
			ctaTexto = "Iniciar sesión";
			acceso = cuenta.getAuthProvider() == AuthProvider.GOOGLE
					? "Tu cuenta usa Google. En la pantalla de acceso, elige <strong>Continuar con Google</strong> y utiliza "
							+ esc(destinatario) + "."
					: "Ya tienes una cuenta asociada a este correo. Puedes iniciar sesión con tus credenciales habituales.";
		}

		return "<div style='font-family:Arial,sans-serif;color:#172126;line-height:1.55;max-width:680px'>"
				+ "<h2>Inscripción confirmada</h2>"
				+ "<p>Hola, " + esc(p.getNombre()) + ". El alta de <strong>" + esc(nombreCompleto(p))
				+ "</strong> se ha completado correctamente.</p>"
				+ "<table cellpadding='7' cellspacing='0' border='1' style='border-collapse:collapse;width:100%'>"
				+ fila("Deporte", nombreDeporte(p)) + fila("Temporada", p.getTemporada())
				+ fila("Grupo(s)", gruposTexto) + fila("Referencia", p.getReferencia()) + "</table>"
				+ "<h3>Horarios</h3><ul>" + (filasHorarios.isBlank() ? "<li>Consulta el horario asignado en tu perfil.</li>" : filasHorarios) + "</ul>"
				+ "<h3>Tu portal</h3><p>" + acceso + "</p>"
				+ "<p>Desde el portal podrás consultar el perfil y los horarios de todos los alumnos vinculados a este correo, "
				+ "sus documentos y temario, vídeos, eventos, novedades y retos.</p>"
				+ "<p style='margin:24px 0'><a href='" + esc(ctaUrl)
				+ "' style='display:inline-block;padding:12px 20px;background:#a52229;color:#fff;text-decoration:none;border-radius:4px;font-weight:bold'>"
				+ ctaTexto + "</a></p>"
				+ "<p>Conserva la referencia <strong>" + esc(p.getReferencia()) + "</strong> para cualquier consulta.</p>"
				+ "<p>Un saludo,<br><strong>Moi's Kim Do</strong></p></div>";
	}

	private String emailDefinitivo(Preinscripcion p) {
		String email = p.getAlumno() == null ? null : p.getAlumno().getEmail();
		if (email == null || email.isBlank()) {
			throw new IllegalStateException("La ficha finalizada no tiene correo electrónico.");
		}
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private List<Turno> horarios(Preinscripcion p) {
		if (p.getTurnos() != null && !p.getTurnos().isEmpty()) {
			return p.getTurnos().stream().sorted(java.util.Comparator.comparing(Turno::getId)).toList();
		}
		if (p.getTurno() != null) {
			return List.of(p.getTurno());
		}
		return new ArrayList<>();
	}

	private String nombreCompleto(Preinscripcion p) {
		return (p.getNombre() + " " + p.getApellidos()).trim();
	}

	private String nombreDeporte(Preinscripcion p) {
		return switch (p.getDeporte()) {
			case TAEKWONDO -> "Taekwondo";
			case KICKBOXING -> "Kickboxing";
			case PILATES -> "Pilates";
			case DEFENSA_PERSONAL_FEMENINA -> "Defensa personal femenina";
		};
	}

	private String fila(String etiqueta, String valor) {
		return "<tr><td><strong>" + esc(etiqueta) + "</strong></td><td>" + esc(valor) + "</td></tr>";
	}

	private String url(String valor) {
		return URLEncoder.encode(valor, StandardCharsets.UTF_8).replace("+", "%20");
	}

	private String errorResumido(Exception ex) {
		String mensaje = ex.getMessage();
		if ((mensaje == null || mensaje.isBlank()) && ex.getCause() != null) {
			mensaje = ex.getCause().getMessage();
		}
		if (mensaje == null || mensaje.isBlank()) {
			mensaje = ex.getClass().getSimpleName();
		}
		mensaje = mensaje.replaceAll("[\\r\\n]+", " ").trim();
		return mensaje.length() <= 500 ? mensaje : mensaje.substring(0, 500);
	}

	private String esc(String value) {
		return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
				.replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
	}
}
