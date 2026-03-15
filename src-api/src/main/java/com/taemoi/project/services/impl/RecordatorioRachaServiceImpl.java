package com.taemoi.project.services.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.EmailService;
import com.taemoi.project.services.RecordatorioRachaService;
import com.taemoi.project.utils.EmailUtils;

@Service
public class RecordatorioRachaServiceImpl implements RecordatorioRachaService {

	private static final Logger logger = LoggerFactory.getLogger(RecordatorioRachaServiceImpl.class);
	private static final ZoneId RETO_DIARIO_ZONE_ID = ZoneId.of("Europe/Madrid");

	private final UsuarioRepository usuarioRepository;
	private final AlumnoRepository alumnoRepository;
	private final EmailService emailService;

	@Value("${streak.reminder.enabled:true}")
	private boolean streakReminderEnabled;

	@Value("${streak.reminder.window-hours:3}")
	private int streakReminderWindowHours;

	@Value("${app.frontend.base-url:https://moiskimdo.es}")
	private String frontendBaseUrl;

	private Clock clock = Clock.system(RETO_DIARIO_ZONE_ID);

	public RecordatorioRachaServiceImpl(UsuarioRepository usuarioRepository,
			AlumnoRepository alumnoRepository,
			EmailService emailService) {
		this.usuarioRepository = usuarioRepository;
		this.alumnoRepository = alumnoRepository;
		this.emailService = emailService;
	}

	@Override
	@Transactional
	public void enviarRecordatoriosSiCorresponde() {
		if (!streakReminderEnabled) {
			logger.debug("Recordatorio de racha desactivado por configuración.");
			return;
		}

		ZonedDateTime ahora = ZonedDateTime.now(clock).withZoneSameInstant(RETO_DIARIO_ZONE_ID);
		int ventanaHoras = Math.max(1, streakReminderWindowHours);
		ZonedDateTime proximoReset = LocalDate.now(clock.withZone(RETO_DIARIO_ZONE_ID))
				.plusDays(1)
				.atStartOfDay(RETO_DIARIO_ZONE_ID);
		ZonedDateTime inicioVentana = proximoReset.minusHours(ventanaHoras);

		if (ahora.isBefore(inicioVentana) || !ahora.isBefore(proximoReset)) {
			logger.debug("Fuera de ventana para recordatorios de racha. Ahora={}, inicioVentana={}, reset={}",
					ahora, inicioVentana, proximoReset);
			return;
		}

		LocalDate hoy = ahora.toLocalDate();
		LocalDate objetivoReset = proximoReset.toLocalDate();
		List<Usuario> usuarios = usuarioRepository.findByRolAndRecordatorioRachaEmailHabilitadoTrue(Roles.ROLE_USER);

		int evaluados = 0;
		int enviados = 0;
		int deduplicados = 0;
		int sinRiesgo = 0;
		int errores = 0;

		for (Usuario usuario : usuarios) {
			evaluados++;
			try {
				if (!Boolean.TRUE.equals(usuario.getRecordatorioRachaEmailHabilitado())) {
					continue;
				}
				if (objetivoReset.equals(usuario.getUltimoRecordatorioRachaReset())) {
					deduplicados++;
					continue;
				}

				List<AlumnoRachaEnRiesgo> alumnosEnRiesgo = obtenerAlumnosEnRiesgo(usuario.getEmail(), hoy);
				if (alumnosEnRiesgo.isEmpty()) {
					sinRiesgo++;
					continue;
				}

				String asunto = alumnosEnRiesgo.size() == 1
						? "Tu racha diaria está a punto de perderse"
						: "Hay rachas diarias a punto de perderse";
				String htmlContent = construirHtmlRecordatorio(usuario, alumnosEnRiesgo, ventanaHoras);
				emailService.sendEmail(usuario.getEmail(), asunto, htmlContent);

				usuario.setUltimoRecordatorioRachaReset(objetivoReset);
				usuarioRepository.save(usuario);
				enviados++;
			} catch (Exception e) {
				errores++;
				logger.error("Error enviando recordatorio de racha para usuario {}: {}",
						usuario.getEmail(), e.getMessage(), e);
			}
		}

		logger.info(
				"Recordatorio de racha ejecutado. Evaluados={}, enviados={}, deduplicados={}, sinRiesgo={}, errores={}",
				evaluados, enviados, deduplicados, sinRiesgo, errores);
	}

	private List<AlumnoRachaEnRiesgo> obtenerAlumnosEnRiesgo(String email, LocalDate hoy) {
		String normalizedEmail = EmailUtils.normalizeEmail(email);
		if (normalizedEmail == null || normalizedEmail.isBlank()) {
			return List.of();
		}

		return alumnoRepository.findAllByEmailIgnoreCaseAndActivoTrue(normalizedEmail).stream()
				.map(alumno -> mapearAlumnoEnRiesgo(alumno, hoy))
				.filter(Objects::nonNull)
				.sorted(Comparator
						.comparingInt(AlumnoRachaEnRiesgo::racha).reversed()
						.thenComparing(AlumnoRachaEnRiesgo::nombreCompleto, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	private AlumnoRachaEnRiesgo mapearAlumnoEnRiesgo(Alumno alumno, LocalDate hoy) {
		LocalDate fechaCompletado = toLocalDate(alumno.getFechaRetoDiarioCompletado());
		int rachaPersistida = alumno.getRachaRetoDiario() != null ? Math.max(0, alumno.getRachaRetoDiario()) : 0;
		int rachaActual = calcularRachaActual(rachaPersistida, fechaCompletado, hoy);
		boolean completadoHoy = fechaCompletado != null && fechaCompletado.equals(hoy);

		if (rachaActual <= 0 || completadoHoy) {
			return null;
		}

		String nombre = (alumno.getNombre() == null || alumno.getNombre().isBlank()) ? "Alumno" : alumno.getNombre();
		String apellidos = alumno.getApellidos() == null ? "" : alumno.getApellidos();
		return new AlumnoRachaEnRiesgo(nombre, apellidos, rachaActual);
	}

	private int calcularRachaActual(int rachaPersistida, LocalDate fechaCompletado, LocalDate hoy) {
		if (fechaCompletado == null) {
			return 0;
		}
		if (fechaCompletado.equals(hoy) || fechaCompletado.equals(hoy.minusDays(1))) {
			return Math.max(0, rachaPersistida);
		}
		return 0;
	}

	private LocalDate toLocalDate(Date fecha) {
		if (fecha == null) {
			return null;
		}
		if (fecha instanceof java.sql.Date sqlDate) {
			return sqlDate.toLocalDate();
		}
		return fecha.toInstant().atZone(RETO_DIARIO_ZONE_ID).toLocalDate();
	}

	private String construirHtmlRecordatorio(Usuario usuario, List<AlumnoRachaEnRiesgo> alumnosEnRiesgo, int ventanaHoras) {
		String saludoNombre = (usuario.getNombre() == null || usuario.getNombre().isBlank()) ? ""
				: " " + escapeHtml(usuario.getNombre());
		String saludo = "Hola" + saludoNombre + ",";

		String horasTexto = ventanaHoras == 1 ? "1 hora" : ventanaHoras + " horas";
		String ctaUrl = construirUrlUserPage();

		String filasAlumnos = alumnosEnRiesgo.stream()
				.map(item -> "<li style='margin-bottom:8px;'><strong>" + escapeHtml(item.nombreCompleto())
						+ "</strong>: " + item.racha() + " día(s) de racha</li>")
				.reduce("", String::concat);

		return """
				<!doctype html>
				<html lang="es">
				<head>
				  <meta charset="UTF-8">
				  <meta name="viewport" content="width=device-width, initial-scale=1.0">
				</head>
				<body style="margin:0;padding:0;background-color:#f5f7fa;">
				  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background-color:#f5f7fa;padding:24px 12px;">
				    <tr>
				      <td align="center">
				        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:560px;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 10px 30px rgba(0,0,0,0.08);">
				          <tr>
				            <td style="background:#1b2b2e;color:#ffffff;padding:24px 28px;">
				              <div style="font-size:12px;letter-spacing:1.2px;text-transform:uppercase;color:#b7c4c8;">Club Moiskimdo Taekwondo</div>
				              <h1 style="margin:10px 0 0;font-size:22px;font-weight:700;">Recordatorio de racha diaria</h1>
				            </td>
				          </tr>
				          <tr>
				            <td style="padding:28px;color:#1f2933;font-family:Arial,sans-serif;font-size:15px;line-height:1.6;">
				              <p style="margin:0 0 16px;">%s</p>
				              <p style="margin:0 0 16px;">Quedan menos de <strong>%s</strong> para completar el reto diario y mantener la racha.</p>
				              <ul style="margin:0 0 20px 20px;padding:0;">%s</ul>
				              <div style="text-align:center;margin:24px 0;">
				                <a href="%s" style="background:#0d47a1;color:#ffffff;text-decoration:none;padding:12px 26px;border-radius:999px;display:inline-block;font-weight:600;">
				                  Ir a mi panel
				                </a>
				              </div>
				              <p style="margin:0 0 8px;font-size:13px;color:#6b7280;">
				                Puedes desactivar estos recordatorios desde la tarjeta de reto diario en tu panel de usuario.
				              </p>
				            </td>
				          </tr>
				        </table>
				      </td>
				    </tr>
				  </table>
				</body>
				</html>
				""".formatted(saludo, horasTexto, filasAlumnos, ctaUrl);
	}

	private String construirUrlUserPage() {
		String baseUrl = frontendBaseUrl == null || frontendBaseUrl.isBlank()
				? "https://moiskimdo.es"
				: frontendBaseUrl.trim();
		if (baseUrl.endsWith("/")) {
			baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		}
		return baseUrl + "/userpage";
	}

	private String escapeHtml(String text) {
		if (text == null) {
			return "";
		}
		return text
				.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;")
				.replace("'", "&#39;");
	}

	private record AlumnoRachaEnRiesgo(String nombre, String apellidos, int racha) {
		private String nombreCompleto() {
			return (nombre + " " + apellidos).trim();
		}
	}
}
