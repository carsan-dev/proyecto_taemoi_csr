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
import com.taemoi.project.utils.TelefonoUtils;

/**
 * Tutores de un alumno, incluido el responsable legal que la ficha (alta, edición, preinscripción)
 * sigue enviando y recibiendo como {@code responsableLegalNombre/Nif} (ver {@code ResponsableLegal}).
 *
 * <ul>
 * <li>Escribir por la ficha ({@link #aplicarResponsableLegal}) crea o actualiza el primer tutor
 * responsable legal; dejar el nombre vacío quita la marca de responsable legal (no borra a nadie).</li>
 * <li>Si solo se conoce el NIF, el responsable legal es un tutor sin nombre (marcador solo-NIF,
 * parentesco TUTOR_LEGAL). Invariante: si existe, es el único responsable legal del alumno; desaparece
 * cuando la ficha vacía el NIF o cuando otro tutor (con nombre) pasa a ser responsable legal.</li>
 * </ul>
 */
@Service
public class TutorService {

	/**
	 * Nombre del marcador solo-NIF: {@code null} (V29; la restricción ck_tutor_nombre_o_nif exige entonces
	 * NIF). Quien lee trata igual vacío y {@code null} ({@link Tutor#tieneNombre()}).
	 */
	static final String NOMBRE_SOLO_NIF = null;

	/**
	 * Cambio del responsable legal tal y como llega por la ficha del alumno: en cada campo,
	 * {@code null} = sin cambios y vacío (o en blanco) = quitarlo.
	 */
	public record CambioResponsableLegal(String nombre, String nif) {

		public static final CambioResponsableLegal SIN_CAMBIOS = new CambioResponsableLegal(null, null);

		/** Valores completos (alta, alumno creado desde una preinscripción): {@code null} = sin dato. */
		public static CambioResponsableLegal fijar(String nombre, String nif) {
			return new CambioResponsableLegal(nombre == null ? "" : nombre, nif == null ? "" : nif);
		}

		public boolean sinCambios() {
			return nombre == null && nif == null;
		}
	}

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
			retirarMarcadorSoloNif(alumno, tutor);
		}
		return TutorDTO.de(tutor);
	}

	@Transactional
	public TutorDTO actualizar(Long alumnoId, Long tutorId, TutorRequest datos) {
		Alumno alumno = buscarAlumno(alumnoId);
		Tutor tutor = buscarTutor(alumnoId, tutorId);
		aplicar(tutor, datos);
		if (datos.responsableLegal() != null) {
			tutor.setResponsableLegal(datos.responsableLegal());
		}
		if (datos.orden() != null) {
			tutor.setOrden(datos.orden());
		}
		tutores.saveAndFlush(tutor);
		if (tutor.isResponsableLegal()) {
			retirarMarcadorSoloNif(alumno, tutor);
		}
		return TutorDTO.de(tutor);
	}

	@Transactional
	public void eliminar(Long alumnoId, Long tutorId) {
		Alumno alumno = buscarAlumno(alumnoId);
		Tutor tutor = buscarTutor(alumnoId, tutorId);
		borrar(alumno, tutor);
	}

	/**
	 * Pone {@code nif} a un tutor existente (y opcionalmente lo marca responsable legal) sin tocar el
	 * resto de sus datos.
	 */
	@Transactional
	public TutorDTO asignarNif(Long alumnoId, Long tutorId, String nif, boolean marcarResponsable) {
		Alumno alumno = buscarAlumno(alumnoId);
		Tutor tutor = buscarTutor(alumnoId, tutorId);
		tutor.setNif(NifUtils.normalizeForStorage(nif));
		if (marcarResponsable) {
			tutor.setResponsableLegal(true);
		}
		tutores.saveAndFlush(tutor);
		if (tutor.isResponsableLegal()) {
			retirarMarcadorSoloNif(alumno, tutor);
		}
		return TutorDTO.de(tutor);
	}

	/**
	 * Aplica al primer tutor responsable legal lo que la ficha del alumno envía como
	 * {@code responsableLegalNombre/Nif} (alta, edición o preinscripción). El alumno debe estar guardado.
	 * <ul>
	 * <li>Con nombre: si no hay responsable legal se crea un tutor TUTOR_LEGAL; si lo hay, se le pone el
	 * nombre (salvo que sea el mismo nombre completo, para conservar el reparto nombre/apellidos) y el
	 * NIF. Sin enviar el nombre, el del responsable actual se conserva.</li>
	 * <li>Sin nombre: ningún tutor con nombre sigue siendo responsable legal (sus datos se conservan) y
	 * el NIF, si lo hay, queda en el marcador solo-NIF.</li>
	 * </ul>
	 */
	@Transactional
	public void aplicarResponsableLegal(Alumno alumno, CambioResponsableLegal cambio) {
		if (alumno == null || alumno.getId() == null || cambio == null || cambio.sinCambios()) {
			return;
		}
		List<Tutor> responsables = tutores.findByAlumnoIdAndResponsableLegalTrueOrderByOrdenAscIdAsc(alumno.getId());
		Tutor principal = responsables.isEmpty() ? null : responsables.get(0);
		String nif = cambio.nif() != null ? NifUtils.normalizeForStorage(cambio.nif())
				: principal == null ? null : principal.getNif();
		String nombre;
		if (cambio.nombre() != null) {
			nombre = limpiar(cambio.nombre());
		} else if (principal != null && principal.tieneNombre()) {
			// Solo cambia el NIF del responsable actual.
			principal.setNif(nif);
			return;
		} else {
			nombre = null;
		}

		if (nombre == null) {
			Tutor marcador = null;
			for (Tutor t : responsables) {
				if (t.tieneNombre()) {
					t.setResponsableLegal(false);
				} else if (marcador == null) {
					marcador = t;
				} else {
					borrar(alumno, t);
				}
			}
			if (nif == null) {
				if (marcador != null) {
					borrar(alumno, marcador);
				}
			} else if (marcador != null) {
				marcador.setNif(nif);
			} else {
				nuevoResponsable(alumno, NOMBRE_SOLO_NIF, nif);
			}
			return;
		}

		if (principal == null) {
			nuevoResponsable(alumno, nombre, nif);
			return;
		}
		// Mismo nombre completo (salvo espacios): se conserva el reparto nombre/apellidos.
		if (!mismoNombre(principal.getNombreCompleto(), nombre)) {
			principal.setNombre(nombre);
			principal.setApellidos(null);
		}
		principal.setNif(nif);
	}

	private void nuevoResponsable(Alumno alumno, String nombre, String nif) {
		Tutor tutor = new Tutor(alumno);
		tutor.setNombre(nombre);
		tutor.setNif(nif);
		tutor.setParentesco(ParentescoTutor.TUTOR_LEGAL);
		tutor.setResponsableLegal(true);
		tutor.setOrden(tutores.maxOrden(alumno.getId()) + 1);
		tutor = tutores.saveAndFlush(tutor);
		anadirEnMemoria(alumno, tutor);
	}

	/** Otro tutor (con nombre) es ya responsable legal: el marcador solo-NIF sobra. */
	private void retirarMarcadorSoloNif(Alumno alumno, Tutor responsable) {
		for (Tutor t : tutores.findByAlumnoIdAndResponsableLegalTrueOrderByOrdenAscIdAsc(alumno.getId())) {
			if (!t.getId().equals(responsable.getId()) && !t.tieneNombre()) {
				borrar(alumno, t);
			}
		}
	}

	private void borrar(Alumno alumno, Tutor tutor) {
		if (alumno.getTutores() != null && Hibernate.isInitialized(alumno.getTutores())) {
			alumno.getTutores().remove(tutor);
		}
		tutores.delete(tutor);
		tutores.flush();
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
