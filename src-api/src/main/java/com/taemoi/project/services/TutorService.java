package com.taemoi.project.services;

import java.util.List;
import java.util.NoSuchElementException;

import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.dtos.request.TutorRequest;
import com.taemoi.project.dtos.response.TutorDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.ParentescoTutor;
import com.taemoi.project.entities.Tutor;
import com.taemoi.project.exceptions.alumno.AlumnoNoEncontradoException;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.TutorRepository;
import com.taemoi.project.utils.EmailUtils;
import com.taemoi.project.utils.NifUtils;
import com.taemoi.project.utils.ResponsableLegal;
import com.taemoi.project.utils.TelefonoUtils;

/**
 * Tutores de un alumno y su puente con los campos heredados {@code responsableLegalNombre/Nif}.
 *
 * <p>Invariante hasta la contracción de {@code alumno.responsable_legal_*}: si el alumno tiene algún
 * tutor responsable legal, esas columnas valen lo mismo que el primero (por {@code orden}, {@code id});
 * si no tiene ninguno, guardan lo último escrito por los campos heredados (puede ser solo un NIF).
 * <ul>
 * <li>Escribir por los campos heredados ({@link #sincronizarDesdeCamposHeredados}) crea o actualiza
 * ese tutor; dejar el nombre vacío quita la marca de responsable legal (no borra a nadie).</li>
 * <li>Escribir por {@code /tutores} vuelve a copiar el primer responsable legal en las columnas.</li>
 * </ul>
 */
@Service
public class TutorService {

	private static final int MAX_NOMBRE_HEREDADO = 180;

	private final TutorRepository tutores;
	private final AlumnoRepository alumnos;

	public TutorService(TutorRepository tutores, AlumnoRepository alumnos) {
		this.tutores = tutores;
		this.alumnos = alumnos;
	}

	@Transactional(readOnly = true)
	public List<TutorDTO> listar(Long alumnoId) {
		buscarAlumno(alumnoId);
		return tutores.findByAlumnoIdOrderByOrdenAscIdAsc(alumnoId).stream().map(TutorDTO::de).toList();
	}

	@Transactional
	public TutorDTO crear(Long alumnoId, TutorRequest datos) {
		Alumno alumno = buscarAlumno(alumnoId);
		Tutor tutor = new Tutor(alumno);
		aplicar(tutor, datos);
		tutor.setResponsableLegal(Boolean.TRUE.equals(datos.responsableLegal()));
		tutor.setOrden(datos.orden() != null ? datos.orden() : tutores.maxOrden(alumnoId) + 1);
		tutor = tutores.saveAndFlush(tutor);
		anadirEnMemoria(alumno, tutor);
		if (tutor.isResponsableLegal()) {
			reflejarEnCamposHeredados(alumno);
		}
		return TutorDTO.de(tutor);
	}

	@Transactional
	public TutorDTO actualizar(Long alumnoId, Long tutorId, TutorRequest datos) {
		Alumno alumno = buscarAlumno(alumnoId);
		Tutor tutor = buscarTutor(alumnoId, tutorId);
		boolean eraResponsable = tutor.isResponsableLegal();
		aplicar(tutor, datos);
		if (datos.responsableLegal() != null) {
			tutor.setResponsableLegal(datos.responsableLegal());
		}
		if (datos.orden() != null) {
			tutor.setOrden(datos.orden());
		}
		tutores.saveAndFlush(tutor);
		if (eraResponsable || tutor.isResponsableLegal()) {
			reflejarEnCamposHeredados(alumno);
		}
		return TutorDTO.de(tutor);
	}

	@Transactional
	public void eliminar(Long alumnoId, Long tutorId) {
		Alumno alumno = buscarAlumno(alumnoId);
		Tutor tutor = buscarTutor(alumnoId, tutorId);
		boolean eraResponsable = tutor.isResponsableLegal();
		if (Hibernate.isInitialized(alumno.getTutores())) {
			alumno.getTutores().remove(tutor);
		}
		tutores.delete(tutor);
		tutores.flush();
		if (eraResponsable) {
			reflejarEnCamposHeredados(alumno);
		}
	}

	/**
	 * Lleva los campos heredados {@code responsableLegalNombre/Nif} del alumno (recién escritos por el
	 * alta, la edición o una preinscripción) al tutor responsable legal. El alumno debe estar guardado.
	 */
	@Transactional
	public void sincronizarDesdeCamposHeredados(Alumno alumno) {
		if (alumno == null || alumno.getId() == null) {
			return;
		}
		String nombre = limpiar(alumno.getResponsableLegalNombre());
		String nif = NifUtils.normalizeForStorage(alumno.getResponsableLegalNif());
		List<Tutor> responsables = tutores.findByAlumnoIdAndResponsableLegalTrueOrderByOrdenAscIdAsc(alumno.getId());

		if (nombre == null) {
			// Se vació el responsable legal: nadie lo es ya; los datos del tutor se conservan.
			responsables.forEach(t -> t.setResponsableLegal(false));
			alumno.setResponsableLegalNombre(null);
			alumno.setResponsableLegalNif(nif);
			return;
		}

		Tutor principal;
		if (responsables.isEmpty()) {
			principal = new Tutor(alumno);
			principal.setNombre(nombre);
			principal.setNif(nif);
			principal.setParentesco(ParentescoTutor.TUTOR_LEGAL);
			principal.setResponsableLegal(true);
			principal.setOrden(tutores.maxOrden(alumno.getId()) + 1);
			principal = tutores.saveAndFlush(principal);
			anadirEnMemoria(alumno, principal);
		} else {
			principal = responsables.get(0);
			// Mismo nombre completo (salvo espacios): se conserva el reparto nombre/apellidos.
			if (!mismoNombre(principal.getNombreCompleto(), nombre)) {
				principal.setNombre(nombre);
				principal.setApellidos(null);
			}
			principal.setNif(nif);
		}
		alumno.setResponsableLegalNombre(ResponsableLegal.truncar(principal.getNombreCompleto(), MAX_NOMBRE_HEREDADO));
		alumno.setResponsableLegalNif(principal.getNif());
	}

	private void reflejarEnCamposHeredados(Alumno alumno) {
		Tutor principal = tutores.findByAlumnoIdAndResponsableLegalTrueOrderByOrdenAscIdAsc(alumno.getId()).stream()
				.findFirst().orElse(null);
		alumno.setResponsableLegalNombre(
				principal == null ? null : ResponsableLegal.truncar(principal.getNombreCompleto(), MAX_NOMBRE_HEREDADO));
		alumno.setResponsableLegalNif(principal == null ? null : principal.getNif());
		alumnos.save(alumno);
	}

	private void aplicar(Tutor tutor, TutorRequest datos) {
		String nombre = limpiar(datos.nombre());
		if (nombre == null) {
			throw new IllegalArgumentException("El nombre del tutor es obligatorio.");
		}
		tutor.setNombre(nombre);
		tutor.setApellidos(limpiar(datos.apellidos()));
		tutor.setNif(NifUtils.normalizeForStorage(datos.nif()));
		tutor.setParentesco(datos.parentesco() != null ? datos.parentesco() : ParentescoTutor.OTRO);
		String telefono = TelefonoUtils.normalizar(datos.telefono());
		if (telefono != null && !TelefonoUtils.esValido(telefono)) {
			throw new IllegalArgumentException("El teléfono del tutor no es válido.");
		}
		tutor.setTelefono(telefono);
		tutor.setEmail(EmailUtils.normalizeEmail(limpiar(datos.email())));
	}

	private Alumno buscarAlumno(Long alumnoId) {
		return alumnos.buscarPorIdEnOrganizacionActiva(alumnoId)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado."));
	}

	private Tutor buscarTutor(Long alumnoId, Long tutorId) {
		return tutores.findByIdAndAlumnoId(tutorId, alumnoId)
				.orElseThrow(() -> new NoSuchElementException("Tutor no encontrado."));
	}

	private static void anadirEnMemoria(Alumno alumno, Tutor tutor) {
		if (alumno.getTutores() != null && Hibernate.isInitialized(alumno.getTutores())
				&& !alumno.getTutores().contains(tutor)) {
			alumno.getTutores().add(tutor);
		}
	}

	private static boolean mismoNombre(String a, String b) {
		return normalizarEspacios(a).equals(normalizarEspacios(b));
	}

	private static String normalizarEspacios(String valor) {
		return valor == null ? "" : valor.trim().replaceAll("\\s+", " ");
	}

	private static String limpiar(String valor) {
		if (valor == null) {
			return null;
		}
		String limpio = valor.trim().replaceAll("\\s+", " ");
		return limpio.isEmpty() ? null : limpio;
	}
}
