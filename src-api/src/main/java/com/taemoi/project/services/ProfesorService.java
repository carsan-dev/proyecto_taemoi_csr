package com.taemoi.project.services;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.dtos.response.ProfesorDTOs;
import com.taemoi.project.entities.AsignacionProfesor;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.RolAsignacionProfesor;
import com.taemoi.project.entities.Tutor;
import com.taemoi.project.organizacion.AlcanceActual;
import com.taemoi.project.organizacion.AlcanceSesion;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AsignacionProfesorRepository;
import com.taemoi.project.repositories.TutorRepository;

import jakarta.persistence.EntityManager;

/**
 * Lectura del profesor con sus grupos (entrega 7, pieza E7-1; fase3-e7-profesor.md D7-9 a D7-13).
 *
 * <p><b>Filtro único: {@code alcance.grupos()}</b> (D7-11), como {@code CajaService.alumnosDeMisGrupos}. Nunca
 * {@code GestionPorSede.restriccion()} (no restringe al profesor), {@code PredicadosAlcance.alumnos(alcance)} ni
 * {@code AccesoRecursos.alumno/grupo(id, 'LEER')}, que también conceden por las sedes del responsable: en la sesión
 * R+Pr (decisión 184) las sedes no abren nada aquí. Con alcance total (gestión, también con asignación) no hay grupos:
 * lista vacía o 403. El {@code @PreAuthorize} del controlador ya filtra; el servicio lo vuelve a comprobar.
 *
 * <p>Escuela: Grupo, Alumno, AlumnoDeporte, Tutor y Sede se acotan por {@code @TenantId};
 * {@code asignacion_profesor} no es {@code EntidadOrganizacion} y se consulta con {@code organizacion_id} explícito.
 * Solo alumnos activos (D7-13): un alumno de baja de su grupo da 404. Las respuestas son {@link ProfesorDTOs}, sin datos
 * de más (D7-12).
 */
@Service
public class ProfesorService {

	private static final String GRADO_ROJO = CatalogoLegadoTaemoi.GRADO_ROJO;

	private final EntityManager em;
	private final AlcanceActual alcanceActual;
	private final AsignacionProfesorRepository asignaciones;
	private final TutorRepository tutores;
	private final ProgresionGradosService progresionGrados;
	private final CatalogoDisciplinasService catalogoDisciplinas;
	private final ComportamientoLegadoService comportamientoLegado;

	public ProfesorService(EntityManager em, AlcanceActual alcanceActual, AsignacionProfesorRepository asignaciones,
			TutorRepository tutores, ProgresionGradosService progresionGrados,
			CatalogoDisciplinasService catalogoDisciplinas, ComportamientoLegadoService comportamientoLegado) {
		this.em = em;
		this.alcanceActual = alcanceActual;
		this.asignaciones = asignaciones;
		this.tutores = tutores;
		this.progresionGrados = progresionGrados;
		this.catalogoDisciplinas = catalogoDisciplinas;
		this.comportamientoLegado = comportamientoLegado;
	}

	/** Sus grupos (§5.1), por nombre: sede, su rol, disciplina y nº de alumnos activos. Sin grupos, lista vacía. */
	@Transactional(readOnly = true)
	public List<ProfesorDTOs.Grupo> misGrupos() {
		Set<Long> grupos = gruposDelProfesor();
		if (grupos.isEmpty()) {
			return List.of();
		}
		Map<Long, RolAsignacionProfesor> roles = rolesEnSusGrupos(grupos);
		Map<Long, Long> activos = new HashMap<>();
		for (Object[] f : em.createQuery("""
				SELECT g.id, COUNT(a.id) FROM Grupo g JOIN g.alumnos a
				WHERE g.id IN :grupos AND a.activo = TRUE GROUP BY g.id""", Object[].class)
				.setParameter("grupos", grupos).getResultList()) {
			activos.put((Long) f[0], (Long) f[1]);
		}
		List<ProfesorDTOs.Grupo> resultado = new ArrayList<>();
		for (Object[] f : em.createQuery("""
				SELECT g.id, g.nombre, g.sedeId, s.nombre, d.codigo, d.nombre
				FROM Grupo g LEFT JOIN g.disciplina d LEFT JOIN Sede s ON s.id = g.sedeId
				WHERE g.id IN :grupos ORDER BY g.nombre, g.id""", Object[].class)
				.setParameter("grupos", grupos).getResultList()) {
			Long id = (Long) f[0];
			resultado.add(new ProfesorDTOs.Grupo(id, (String) f[1], sede((Long) f[2], (String) f[3]), roles.get(id),
					activos.getOrDefault(id, 0L), f[4] == null ? null
							: new ProfesorDTOs.Disciplina((String) f[4], (String) f[5])));
		}
		return resultado;
	}

	/**
	 * Alumnos ACTIVOS de un grupo suyo (§5.2), por apellidos, nombre e id, cada uno con sus deportes activos (grado con
	 * nombre, fecha de grado y apto). Dos consultas, sin N+1. Grupo que no es suyo → 403.
	 */
	@Transactional(readOnly = true)
	public List<ProfesorDTOs.AlumnoDeGrupo> alumnosDelGrupo(Long grupoId) {
		if (grupoId == null || !gruposDelProfesor().contains(grupoId)) {
			throw new AccessDeniedException("Acceso denegado.");
		}
		List<String> disciplinaDelGrupo = em.createQuery(
				"SELECT d.codigo FROM Grupo g LEFT JOIN g.disciplina d WHERE g.id = :grupo", String.class)
				.setParameter("grupo", grupoId).getResultList();
		if (disciplinaDelGrupo.isEmpty()) {
			throw new AccessDeniedException("Acceso denegado.");
		}
		String disciplina = disciplinaDelGrupo.get(0);
		Map<Long, ProfesorDTOs.AlumnoDeGrupo> porAlumno = new LinkedHashMap<>();
		for (Object[] f : em.createQuery("""
				SELECT a.id, a.nombre, a.apellidos FROM Grupo g JOIN g.alumnos a
				WHERE g.id = :grupo AND a.activo = TRUE ORDER BY a.apellidos, a.nombre, a.id""", Object[].class)
				.setParameter("grupo", grupoId).getResultList()) {
			porAlumno.put((Long) f[0], new ProfesorDTOs.AlumnoDeGrupo((Long) f[0], (String) f[1], (String) f[2],
					new ArrayList<>()));
		}
		for (FilaDeporte d : deportesActivos(porAlumno.keySet())) {
			porAlumno.get(d.alumnoId()).deportes().add(new ProfesorDTOs.DeporteEnLista(d.codigo(), d.nombre(),
					grado(d.grado()), d.fechaGrado(), d.apto(), d.codigo().equals(disciplina)));
		}
		return porAlumno.values().stream().map(a -> new ProfesorDTOs.AlumnoDeGrupo(a.id(), a.nombre(), a.apellidos(),
				List.copyOf(a.deportes()))).toList();
	}

	/**
	 * Ficha reducida de un alumno de sus grupos (§5.3): edad, los grupos que comparte con él, teléfonos de contacto
	 * (D7-2) y deportes con grado, fecha, apto e indicadores de pase. Si no comparte ningún grupo → 403; de baja → 404.
	 */
	@Transactional(readOnly = true)
	public ProfesorDTOs.Ficha ficha(Long alumnoId) {
		Set<Long> grupos = gruposDelProfesor();
		if (alumnoId == null || grupos.isEmpty()) {
			throw new AccessDeniedException("Acceso denegado.");
		}
		List<ProfesorDTOs.GrupoCompartido> compartidos = new ArrayList<>();
		Set<String> disciplinasCompartidas = new HashSet<>();
		for (Object[] f : em.createQuery("""
				SELECT g.id, g.nombre, g.sedeId, s.nombre, d.codigo
				FROM Grupo g JOIN g.alumnos a LEFT JOIN g.disciplina d LEFT JOIN Sede s ON s.id = g.sedeId
				WHERE a.id = :alumno AND g.id IN :grupos ORDER BY g.nombre, g.id""", Object[].class)
				.setParameter("alumno", alumnoId).setParameter("grupos", grupos).getResultList()) {
			compartidos.add(new ProfesorDTOs.GrupoCompartido((Long) f[0], (String) f[1], sede((Long) f[2], (String) f[3])));
			if (f[4] != null) {
				disciplinasCompartidas.add((String) f[4]);
			}
		}
		if (compartidos.isEmpty()) {
			throw new AccessDeniedException("Acceso denegado.");
		}
		List<Object[]> datos = em.createQuery("""
				SELECT a.nombre, a.apellidos, a.fechaNacimiento, a.telefono, a.telefono2
				FROM Alumno a WHERE a.id = :alumno AND a.activo = TRUE""", Object[].class)
				.setParameter("alumno", alumnoId).getResultList();
		if (datos.isEmpty()) {
			throw new NoSuchElementException("El alumno no existe o está de baja.");
		}
		Object[] alumno = datos.get(0);
		String nombre = (String) alumno[0];
		String apellidos = (String) alumno[1];
		Date nacimiento = (Date) alumno[2];

		List<ProfesorDTOs.Contacto> contacto = new ArrayList<>();
		for (Object telefono : new Object[] { alumno[3], alumno[4] }) {
			String t = telefono == null ? "" : telefono.toString().strip();
			if (!t.isEmpty()) {
				contacto.add(new ProfesorDTOs.Contacto("ALUMNO", nombre, apellidos, null, t));
			}
		}
		for (Tutor tutor : tutores.findByAlumnoIdOrderByOrdenAscIdAsc(alumnoId)) {
			String t = tutor.getTelefono() == null ? "" : tutor.getTelefono().strip();
			if (!t.isEmpty()) {
				contacto.add(new ProfesorDTOs.Contacto("TUTOR", tutor.getNombre(), tutor.getApellidos(),
						tutor.getParentesco(), t));
			}
		}

		boolean legado = comportamientoLegado.activo();
		List<ProfesorDTOs.DeporteDelAlumno> deportes = new ArrayList<>();
		for (FilaDeporte d : deportesActivos(List.of(alumnoId))) {
			boolean deMisGrupos = disciplinasCompartidas.contains(d.codigo());
			Grado siguiente = siguienteGrado(d, nacimiento);
			deportes.add(new ProfesorDTOs.DeporteDelAlumno(d.codigo(), d.nombre(), grado(d.grado()), d.fechaGrado(),
					d.apto(), deMisGrupos, deMisGrupos && siguiente != null, grado(siguiente),
					pideRojoBordado(legado, siguiente == null ? null : siguiente.getCodigo())));
		}
		return new ProfesorDTOs.Ficha(alumnoId, nombre, apellidos, edad(aLocalDate(nacimiento)), List.copyOf(compartidos),
				List.copyOf(contacto), List.copyOf(deportes));
	}

	/**
	 * Misma regla que gestión ({@code esSiguienteGradoRojo} del frontend, D11): el pase pide elegir «Rojo» o «Rojo
	 * bordado» solo con el comportamiento de Taemoi y cuando el siguiente grado es {@code ROJO}.
	 */
	static boolean pideRojoBordado(boolean legado, String codigoSiguienteGrado) {
		return legado && GRADO_ROJO.equals(codigoSiguienteGrado);
	}

	// ------------------------------------------------------------------ apoyo

	private record FilaDeporte(Long alumnoId, String codigo, String nombre, Grado grado, LocalDate fechaGrado,
			boolean apto) {
	}

	/** Deportes activos de esos alumnos (escuela activa por {@code @TenantId}): principal primero y por id. */
	private List<FilaDeporte> deportesActivos(Collection<Long> alumnos) {
		if (alumnos.isEmpty()) {
			return List.of();
		}
		List<FilaDeporte> filas = new ArrayList<>();
		for (Object[] f : em.createQuery("""
				SELECT ad.alumno.id, d.codigo, d.nombre, gr, ad.fechaGrado, ad.aptoParaExamen
				FROM AlumnoDeporte ad JOIN ad.disciplina d LEFT JOIN ad.grado gr
				WHERE ad.alumno.id IN :alumnos AND ad.activo = TRUE
				ORDER BY ad.alumno.id, ad.principal DESC, ad.id""", Object[].class)
				.setParameter("alumnos", alumnos).getResultList()) {
			filas.add(new FilaDeporte((Long) f[0], (String) f[1], (String) f[2], (Grado) f[3], aLocalDate((Date) f[4]),
					Boolean.TRUE.equals(f[5])));
		}
		return filas;
	}

	/**
	 * Siguiente grado como en {@code AlumnoServiceImpl.pasarGradoInterno}: disciplina con grados y grado actual; grupo de
	 * edad por {@code disciplina.edad_adulto} (año natural). Sin siguiente (último grado o código fuera del sistema de la
	 * disciplina), {@code null}.
	 */
	private Grado siguienteGrado(FilaDeporte d, Date nacimiento) {
		if (d.grado() == null || !progresionGrados.tieneProgresion(d.codigo())) {
			return null;
		}
		boolean esMenor = catalogoDisciplinas.esMenorPorAnioNatural(nacimiento, d.codigo());
		try {
			return progresionGrados.obtenerSiguienteGrado(d.codigo(), esMenor, d.grado().getCodigo()).orElse(null);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private ProfesorDTOs.Grado grado(Grado grado) {
		return grado == null ? null : new ProfesorDTOs.Grado(grado.getCodigo(), progresionGrados.obtenerNombreVisible(grado));
	}

	/** Su rol en cada uno de sus grupos; {@code asignacion_profesor} con {@code organizacion_id} explícito. */
	private Map<Long, RolAsignacionProfesor> rolesEnSusGrupos(Set<Long> grupos) {
		Long organizacion = TenantContext.get();
		Long usuario = CobroService.usuarioActualId();
		Map<Long, RolAsignacionProfesor> roles = new HashMap<>();
		if (organizacion == null || usuario == null) {
			return roles;
		}
		for (AsignacionProfesor asignacion : asignaciones.findByUsuarioIdAndOrganizacionId(usuario, organizacion)) {
			if (grupos.contains(asignacion.getGrupoId())) {
				roles.put(asignacion.getGrupoId(), asignacion.getRol());
			}
		}
		return roles;
	}

	private Set<Long> gruposDelProfesor() {
		AlcanceSesion alcance = alcanceActual.obtener();
		return alcance.total() ? Set.of() : alcance.grupos();
	}

	private static ProfesorDTOs.Sede sede(Long id, String nombre) {
		return id == null ? null : new ProfesorDTOs.Sede(id, nombre == null ? "Sede " + id : nombre);
	}

	private static Integer edad(LocalDate nacimiento) {
		return nacimiento == null ? null : Period.between(nacimiento, LocalDate.now()).getYears();
	}

	/** Como {@code FechaUtils}: {@code java.sql.Date} sin zona horaria. */
	private static LocalDate aLocalDate(Date fecha) {
		if (fecha == null) {
			return null;
		}
		if (fecha instanceof java.sql.Date fechaSql) {
			return fechaSql.toLocalDate();
		}
		return fecha.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
	}
}
