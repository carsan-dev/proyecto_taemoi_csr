package com.taemoi.project.services;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.dtos.request.ConsentimientoRequest;
import com.taemoi.project.dtos.response.ConsentimientoDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Consentimiento;
import com.taemoi.project.entities.Documento;
import com.taemoi.project.entities.OrigenDato;
import com.taemoi.project.entities.Preinscripcion;
import com.taemoi.project.entities.RelacionFirmante;
import com.taemoi.project.entities.TipoConsentimiento;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.exceptions.alumno.AlumnoNoEncontradoException;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.ConsentimientoRepository;
import com.taemoi.project.repositories.DocumentoRepository;

/**
 * Consentimientos de un alumno. Solo se añaden filas (una retirada es una fila con
 * {@code otorgado = false}). {@code alumno.autorizacion_web} sigue al {@code USO_IMAGEN} vigente: el
 * ÚLTIMO REGISTRADO (mayor id), sea cual sea su {@code fecha}; así una retirada posterior nunca se
 * deshace al importar después una ficha antigua.
 */
@Service
public class ConsentimientoService {

	private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

	private final ConsentimientoRepository consentimientos;
	private final AlumnoRepository alumnos;
	private final DocumentoRepository documentos;

	public ConsentimientoService(ConsentimientoRepository consentimientos, AlumnoRepository alumnos,
			DocumentoRepository documentos) {
		this.consentimientos = consentimientos;
		this.alumnos = alumnos;
		this.documentos = documentos;
	}

	@Transactional(readOnly = true)
	public List<ConsentimientoDTO> listar(Long alumnoId) {
		buscarAlumno(alumnoId);
		return consentimientos.findByAlumnoIdOrderByIdDesc(alumnoId).stream().map(ConsentimientoDTO::de).toList();
	}

	@Transactional
	public ConsentimientoDTO registrar(Long alumnoId, ConsentimientoRequest datos) {
		Alumno alumno = buscarAlumno(alumnoId);
		if (datos.tipo() == null || datos.otorgado() == null) {
			throw new IllegalArgumentException("Tipo y otorgado son obligatorios.");
		}
		OrigenDato origen = datos.origen() != null ? datos.origen() : OrigenDato.APP;
		if (origen == OrigenDato.PREINSCRIPCION) {
			throw new IllegalArgumentException("Los consentimientos de preinscripción solo se registran al finalizarla.");
		}
		if (datos.fecha() != null && datos.fecha().isAfter(LocalDate.now(ZONA))) {
			throw new IllegalArgumentException("La fecha del consentimiento no puede ser futura.");
		}
		if (datos.documentoId() != null) {
			Documento documento = documentos.findById(datos.documentoId())
					.orElseThrow(() -> new IllegalArgumentException("Documento no encontrado."));
			if (documento.getAlumno() == null || !alumnoId.equals(documento.getAlumno().getId())) {
				throw new IllegalArgumentException("El documento no pertenece a este alumno.");
			}
		}
		Consentimiento c = new Consentimiento(alumnoId, datos.tipo(), datos.otorgado(), origen);
		c.setFecha(datos.fecha());
		c.setFechaAproximada(Boolean.TRUE.equals(datos.fechaAproximada()));
		c.setFirmanteNombre(limpiar(datos.firmanteNombre()));
		c.setFirmanteRelacion(datos.firmanteRelacion());
		c.setDocumentoId(datos.documentoId());
		c = consentimientos.saveAndFlush(c);
		if (c.getTipo() == TipoConsentimiento.USO_IMAGEN) {
			sincronizarAutorizacionWeb(alumno);
		}
		return ConsentimientoDTO.de(c);
	}

	/**
	 * Al finalizar una preinscripción: normas y uso de imagen firmados en ella (una vez por tipo y
	 * preinscripción) y {@code autorizacion_web} según el uso de imagen vigente.
	 */
	@Transactional
	public void registrarDesdePreinscripcion(Alumno alumno, Preinscripcion p) {
		LocalDate fecha = p.getCreadaEn() != null ? p.getCreadaEn().atZone(ZONA).toLocalDate() : LocalDate.now(ZONA);
		RelacionFirmante relacion = p.getFechaNacimiento() != null
				&& Period.between(p.getFechaNacimiento(), fecha).getYears() < 18
						? RelacionFirmante.TUTOR
						: RelacionFirmante.PROPIO;
		registrarSiFalta(alumno, p, TipoConsentimiento.NORMAS, Boolean.TRUE.equals(p.getAceptacionNormas()), fecha,
				relacion);
		registrarSiFalta(alumno, p, TipoConsentimiento.USO_IMAGEN,
				Boolean.TRUE.equals(p.getConsentimientoFotografico()), fecha, relacion);
		sincronizarAutorizacionWeb(alumno);
	}

	private void registrarSiFalta(Alumno alumno, Preinscripcion p, TipoConsentimiento tipo, boolean otorgado,
			LocalDate fecha, RelacionFirmante relacion) {
		if (p.getId() != null && consentimientos.existsByPreinscripcionIdAndTipo(p.getId(), tipo)) {
			return;
		}
		Consentimiento c = new Consentimiento(alumno.getId(), tipo, otorgado, OrigenDato.PREINSCRIPCION);
		c.setFecha(fecha);
		c.setFechaAproximada(false);
		c.setFirmanteNombre(limpiar(p.getFirmanteNombre()));
		c.setFirmanteRelacion(relacion);
		c.setPreinscripcionId(p.getId());
		consentimientos.saveAndFlush(c);
	}

	/** {@code autorizacion_web} = el último {@code USO_IMAGEN} registrado; sin ninguno, no se toca. */
	public void sincronizarAutorizacionWeb(Alumno alumno) {
		consentimientos.findFirstByAlumnoIdAndTipoOrderByIdDesc(alumno.getId(), TipoConsentimiento.USO_IMAGEN)
				.ifPresent(vigente -> {
					alumno.setAutorizacionWeb(vigente.isOtorgado());
					alumnos.save(alumno);
				});
	}

	/**
	 * La ficha (alta o edición) fija {@code autorizacionWeb}: queda como consentimiento {@code USO_IMAGEN}
	 * de origen {@code APP}, fechado hoy y firmado por quien hace el cambio (personal de la escuela, sin
	 * relación PROPIO/TUTOR). El alumno ya debe tener el valor nuevo y estar guardado.
	 */
	@Transactional
	public void registrarCambioAutorizacionWeb(Alumno alumno, boolean otorgado) {
		Consentimiento c = new Consentimiento(alumno.getId(), TipoConsentimiento.USO_IMAGEN, otorgado, OrigenDato.APP);
		c.setFecha(LocalDate.now(ZONA));
		c.setFechaAproximada(false);
		c.setFirmanteNombre(limpiar(usuarioActual()));
		consentimientos.saveAndFlush(c);
	}

	private static String usuarioActual() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated()) {
			return null;
		}
		if (auth.getPrincipal() instanceof Usuario u) {
			String nombre = ((u.getNombre() == null ? "" : u.getNombre()) + " "
					+ (u.getApellidos() == null ? "" : u.getApellidos())).trim();
			return nombre.isEmpty() ? u.getEmail() : nombre;
		}
		return "anonymousUser".equals(auth.getName()) ? null : auth.getName();
	}

	private Alumno buscarAlumno(Long alumnoId) {
		return alumnos.buscarPorIdEnOrganizacionActiva(alumnoId)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado."));
	}

	private static String limpiar(String valor) {
		if (valor == null || valor.isBlank()) {
			return null;
		}
		String limpio = valor.strip();
		return limpio.length() > 180 ? limpio.substring(0, 180) : limpio;
	}
}
