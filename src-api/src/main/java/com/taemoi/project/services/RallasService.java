package com.taemoi.project.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.dtos.response.RallasDTOs;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.AsignacionProfesor;
import com.taemoi.project.entities.Disciplina;
import com.taemoi.project.entities.ReglaRallas;
import com.taemoi.project.entities.RolAsignacionProfesor;
import com.taemoi.project.organizacion.AlcanceActual;
import com.taemoi.project.organizacion.AlcanceSesion;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoDeporteRepository;
import com.taemoi.project.repositories.AsignacionProfesorRepository;
import com.taemoi.project.services.AsistenciaService.Via;

import jakarta.persistence.EntityManager;

/**
 * Rallas hacia el examen (decisiones 510, 511 y 513; boceto docs/multi-escuela/boceto-rallas-cinturon.html).
 *
 * <p><b>Qué son.</b> Un contador de medias rallas por alumno y disciplina ({@code alumno_deporte.medias_rallas}, 0 a 10).
 * Se usan solo si la disciplina las tiene activas en la escuela, el alumno tiene grado y el grado no es PUM ni DAN
 * ({@link ReglaRallas}). Desactivar la disciplina no borra nada: dejan de verse y vuelven al reactivarla.
 *
 * <p><b>Quién las cambia</b> (siempre desde un grupo, en la disciplina del grupo): propietario y gestor (alcance total),
 * el responsable de la sede del grupo y el profesor TITULAR del grupo. El ayudante solo las ve (403). Por la ruta del
 * profesor el grupo tiene que ser suyo (403, como pasar lista); por la de gestión manda {@code @acceso.grupoEnSede}.
 *
 * <p><b>Pasos.</b> −2, −1, +1 o +2, cada uno guardado al momento. Un resultado fuera de 0 a 10 se rechaza (409), nunca
 * se recorta. Concurrencia: cada paso es UN {@code UPDATE} atómico con la condición dentro
 * ({@link AlumnoDeporteRepository#sumarMediasRallas}); dos profesores a la vez se aplican los dos, uno tras otro, y
 * ninguno sale de rango. La columna no se escribe desde la entidad, así que guardar la inscripción por otra ruta no
 * pisa un paso. Si el grado cambió entre la lectura y el paso, 409 sin cambiar nada.
 *
 * <p><b>Apto desde la hoja.</b> Solo con 10 medias: en una sentencia marca el apto de esa disciplina y deja las rallas en
 * 0. Marcar apto por otra vía no toca las rallas. Cambiar el grado las deja en 0 ({@link #ponerACeroPorCambioDeGrado}).
 *
 * <p>Cada cambio queda en el registro ({@link RegistroAccionesRallas}) en la misma transacción. Escuela: todo se acota
 * por {@code @TenantId} y las sentencias nativas llevan además {@code organizacion_id}.
 */
@Service
public class RallasService {

	public static final String PASO_NO_VALIDO = "El paso no es válido: −2, −1, +1 o +2.";
	public static final String SOLO_TITULAR = "Las rallas solo las cambian el titular del grupo y la escuela.";
	public static final String GRUPO_NO_EXISTE = AsistenciaService.GRUPO_NO_EXISTE;
	public static final String ALUMNO_NO_ES_DEL_GRUPO = AsistenciaService.ALUMNO_NO_ES_DEL_GRUPO;
	public static final String SIN_DISCIPLINA = "El alumno no tiene la disciplina de este grupo.";
	public static final String DESACTIVADAS = "Las rallas no están activas en esta disciplina.";
	public static final String SIN_GRADO = "El alumno no tiene grado en esta disciplina.";
	public static final String GRADO_SIN_RALLAS = "Su grado no lleva rallas.";
	public static final String NO_HAY_TANTAS = "No tiene tantas rallas para quitar.";
	public static final String NO_CABEN = "No caben más rallas: el máximo son 5.";
	public static final String SOLO_CON_CINCO = "Solo se marca apto desde aquí con las 5 rallas.";
	public static final String CAMBIO_A_LA_VEZ = "El grado ha cambiado mientras tanto. Vuelve a abrir la hoja.";
	public static final String DISCIPLINA_SIN_RALLAS = "La disciplina no existe o no tiene grados.";
	public static final String CONFIGURACION_VACIA = "Indica al menos una disciplina.";

	private final EntityManager em;
	private final AlcanceActual alcanceActual;
	private final AsignacionProfesorRepository asignaciones;
	private final AlumnoDeporteRepository inscripciones;
	private final RegistroAccionesRallas registro;

	public RallasService(EntityManager em, AlcanceActual alcanceActual, AsignacionProfesorRepository asignaciones,
			AlumnoDeporteRepository inscripciones, RegistroAccionesRallas registro) {
		this.em = em;
		this.alcanceActual = alcanceActual;
		this.asignaciones = asignaciones;
		this.inscripciones = inscripciones;
		this.registro = registro;
	}

	// ------------------------------------------------------------------ pasos y apto desde la hoja

	/**
	 * Un paso de la hoja sobre el alumno del grupo, en la disciplina del grupo. Por orden: grupo ajeno (profesor) 403,
	 * grupo inexistente 404, sin permiso para cambiar (ayudante) 403, paso no válido 400, alumno fuera del grupo o de
	 * baja 404, sin la disciplina 404, rallas que no se usan (disciplina apagada, sin grado, PUM o DAN) 409, fuera de 0 a
	 * 10 o grado cambiado a la vez 409. Devuelve cómo queda.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public RallasDTOs.Estado paso(Via via, Long grupoId, Long alumnoId, Integer paso) {
		GrupoDeLasRallas grupo = exigirGrupoQuePuedeCambiar(via, grupoId);
		if (paso == null || !(paso == -2 || paso == -1 || paso == 1 || paso == 2)) {
			throw new IllegalArgumentException(PASO_NO_VALIDO);
		}
		Inscripcion inscripcion = exigirInscripcionConRallas(grupo, grupoId, alumnoId);
		Long organizacion = TenantContext.get();
		if (inscripciones.sumarMediasRallas(inscripcion.id(), organizacion, inscripcion.gradoId(), paso) == 0) {
			Object[] ahora = leer(inscripcion.id());
			if (!inscripcion.gradoId().equals(ahora[1])) {
				throw new IllegalStateException(CAMBIO_A_LA_VEZ);
			}
			throw new IllegalStateException(paso < 0 ? NO_HAY_TANTAS : NO_CABEN);
		}
		Object[] ahora = leer(inscripcion.id());
		int medias = ((Number) ahora[0]).intValue();
		Map<String, Object> valores = new LinkedHashMap<>();
		valores.put("alumno", alumnoId);
		valores.put("grupo", grupoId);
		valores.put("alumnoDeporte", inscripcion.id());
		valores.put("paso", paso);
		valores.put("medias", medias);
		registro.registrar(RegistroAccionesRallas.Accion.PASO, ruta(via, grupoId, alumnoId, ""), valores);
		return new RallasDTOs.Estado(alumnoId, grupo.disciplina(), medias, Boolean.TRUE.equals(ahora[2]));
	}

	/**
	 * «Marcar apto para examen» desde la hoja: con las 10 medias, en una sola sentencia, apto en la disciplina del grupo
	 * y rallas a 0. Mismos códigos que {@link #paso}; con menos de 10 medias, 409 {@value #SOLO_CON_CINCO} sin cambiar
	 * nada.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public RallasDTOs.Estado marcarApto(Via via, Long grupoId, Long alumnoId) {
		GrupoDeLasRallas grupo = exigirGrupoQuePuedeCambiar(via, grupoId);
		Inscripcion inscripcion = exigirInscripcionConRallas(grupo, grupoId, alumnoId);
		if (inscripciones.marcarAptoConRallasCompletas(inscripcion.id(), TenantContext.get(), inscripcion.gradoId()) == 0) {
			Object[] ahora = leer(inscripcion.id());
			if (!inscripcion.gradoId().equals(ahora[1])) {
				throw new IllegalStateException(CAMBIO_A_LA_VEZ);
			}
			throw new IllegalStateException(SOLO_CON_CINCO);
		}
		Object[] ahora = leer(inscripcion.id());
		Map<String, Object> valores = new LinkedHashMap<>();
		valores.put("alumno", alumnoId);
		valores.put("grupo", grupoId);
		valores.put("alumnoDeporte", inscripcion.id());
		registro.registrar(RegistroAccionesRallas.Accion.APTO_DESDE_HOJA, ruta(via, grupoId, alumnoId, "/apto"), valores);
		return new RallasDTOs.Estado(alumnoId, grupo.disciplina(), ((Number) ahora[0]).intValue(),
				Boolean.TRUE.equals(ahora[2]));
	}

	// ------------------------------------------------------------------ cambio de grado

	/**
	 * Cambio de grado de la inscripción (pase de examen o recompensa al cobrar, cambio manual, convocatoria: todo pasa
	 * por {@code AlumnoDeporteService.actualizarGradoPorCodigo}): si el grado es otro, las rallas vuelven a 0 (aunque hoy
	 * no se vean) y queda en el registro. Con el mismo grado, o si ya estaban en 0, nada. Debe llamarse dentro de la
	 * transacción del cambio; la inscripción ya existe.
	 */
	public void ponerACeroPorCambioDeGrado(AlumnoDeporte inscripcion, Long gradoAnterior, Long gradoNuevo) {
		if (inscripcion == null || inscripcion.getId() == null
				|| (gradoAnterior == null ? gradoNuevo == null : gradoAnterior.equals(gradoNuevo))) {
			return;
		}
		if (inscripciones.ponerMediasRallasACero(inscripcion.getId(), TenantContext.get()) == 0) {
			inscripcion.reflejarMediasRallas(0);
			return;
		}
		inscripcion.reflejarMediasRallas(0);
		Map<String, Object> valores = new LinkedHashMap<>();
		valores.put("alumno", inscripcion.getAlumno() == null ? null : inscripcion.getAlumno().getId());
		valores.put("alumnoDeporte", inscripcion.getId());
		valores.put("gradoAnterior", gradoAnterior);
		valores.put("gradoNuevo", gradoNuevo);
		String porDefecto = inscripcion.getAlumno() == null ? "/api/alumnos"
				: "/api/alumnos/" + inscripcion.getAlumno().getId() + "/deportes/" + inscripcion.getDeporte() + "/grado";
		registro.registrar(RegistroAccionesRallas.Accion.A_CERO_POR_GRADO, RegistroAccionesRallas.rutaActualO(porDefecto),
				valores);
	}

	// ------------------------------------------------------------------ lectura (pasar lista, listados)

	/**
	 * Quien hace la petición puede cambiar las rallas de los alumnos de este grupo: alcance total (propietario o gestor),
	 * responsable de la sede del grupo o profesor TITULAR del grupo. El ayudante, no. Sin sesión, no.
	 */
	public boolean puedeCambiar(Long grupoId, Long sedeId) {
		AlcanceSesion alcance = alcanceActual.obtener();
		if (alcance.total()) {
			return true;
		}
		if (sedeId != null && alcance.sedes().contains(sedeId)) {
			return true;
		}
		return esTitular(alcance, grupoId);
	}

	// ------------------------------------------------------------------ configuración

	/** Las disciplinas activas con sistema de grados de la escuela, por orden e id, con su interruptor. */
	@Transactional(readOnly = true)
	public List<RallasDTOs.Disciplina> configuracion() {
		return disciplinasConGrados().stream()
				.map(d -> new RallasDTOs.Disciplina(d.getCodigo(), d.getNombre(), Boolean.TRUE.equals(d.getRallasActivas())))
				.toList();
	}

	/**
	 * Cambia el interruptor de las disciplinas indicadas (código → activas). Todo o nada: vacío, una disciplina que no
	 * está en {@link #configuracion()} o un valor nulo, 400 sin cambiar nada. Cada cambio real queda en el registro;
	 * desactivar no borra las rallas de nadie. Devuelve la configuración completa.
	 */
	@Transactional
	public List<RallasDTOs.Disciplina> cambiarConfiguracion(Map<String, Boolean> cambios) {
		if (cambios == null || cambios.isEmpty()) {
			throw new IllegalArgumentException(CONFIGURACION_VACIA);
		}
		Map<String, Disciplina> porCodigo = new LinkedHashMap<>();
		for (Disciplina disciplina : disciplinasConGrados()) {
			porCodigo.put(disciplina.getCodigo(), disciplina);
		}
		for (Map.Entry<String, Boolean> cambio : cambios.entrySet()) {
			if (cambio.getKey() == null || cambio.getValue() == null || !porCodigo.containsKey(cambio.getKey())) {
				throw new IllegalArgumentException(DISCIPLINA_SIN_RALLAS);
			}
		}
		for (Map.Entry<String, Boolean> cambio : cambios.entrySet()) {
			Disciplina disciplina = porCodigo.get(cambio.getKey());
			boolean antes = Boolean.TRUE.equals(disciplina.getRallasActivas());
			boolean despues = cambio.getValue();
			if (antes == despues) {
				continue;
			}
			disciplina.setRallasActivas(despues);
			Map<String, Object> valores = new LinkedHashMap<>();
			valores.put("disciplina", disciplina.getId());
			registro.registrar(despues ? RegistroAccionesRallas.Accion.ACTIVADAS : RegistroAccionesRallas.Accion.DESACTIVADAS,
					"/api/admin/configuracion/rallas", valores);
		}
		em.flush();
		return configuracion();
	}

	// ------------------------------------------------------------------ apoyo

	/** Código de la disciplina del grupo y su sede. */
	private record GrupoDeLasRallas(String disciplina, Long sedeId) {
	}

	/** La inscripción del alumno en la disciplina del grupo y su grado (ya comprobado que lleva rallas). */
	private record Inscripcion(Long id, Long gradoId) {
	}

	/**
	 * Profesor: el grupo es de {@code alcance.grupos()} (403). El grupo existe en la escuela activa (404). Quien llama
	 * puede cambiar las rallas en él ({@link #puedeCambiar}; si no, 403).
	 */
	private GrupoDeLasRallas exigirGrupoQuePuedeCambiar(Via via, Long grupoId) {
		AlcanceSesion alcance = alcanceActual.obtener();
		if (via == Via.PROFESOR && (grupoId == null || !alcance.grupos().contains(grupoId))) {
			throw new AccessDeniedException("Acceso denegado.");
		}
		List<Object[]> filas = em
				.createQuery("SELECT d.codigo, g.sedeId FROM Grupo g LEFT JOIN g.disciplina d WHERE g.id = :grupo",
						Object[].class)
				.setParameter("grupo", grupoId).getResultList();
		if (filas.isEmpty()) {
			throw new NoSuchElementException(GRUPO_NO_EXISTE);
		}
		GrupoDeLasRallas grupo = new GrupoDeLasRallas((String) filas.get(0)[0], (Long) filas.get(0)[1]);
		if (!puedeCambiar(grupoId, grupo.sedeId())) {
			throw new AccessDeniedException(SOLO_TITULAR);
		}
		return grupo;
	}

	/**
	 * El alumno está en el grupo y activo (404), tiene la disciplina del grupo activa (404) y sus rallas se usan: la
	 * disciplina las tiene activas, tiene grado y no es PUM ni DAN (409).
	 */
	private Inscripcion exigirInscripcionConRallas(GrupoDeLasRallas grupo, Long grupoId, Long alumnoId) {
		if (alumnoId == null) {
			throw new NoSuchElementException(ALUMNO_NO_ES_DEL_GRUPO);
		}
		Long enGrupo = em.createQuery("""
				SELECT COUNT(a.id) FROM Grupo g JOIN g.alumnos a
				WHERE g.id = :grupo AND a.id = :alumno AND a.activo = TRUE""", Long.class)
				.setParameter("grupo", grupoId).setParameter("alumno", alumnoId).getSingleResult();
		if (enGrupo == null || enGrupo == 0) {
			throw new NoSuchElementException(ALUMNO_NO_ES_DEL_GRUPO);
		}
		if (grupo.disciplina() == null) {
			throw new NoSuchElementException(SIN_DISCIPLINA);
		}
		List<Object[]> filas = em.createQuery("""
				SELECT ad.id, gr.id, gr.codigo, d.rallasActivas FROM AlumnoDeporte ad JOIN ad.disciplina d
				LEFT JOIN ad.grado gr
				WHERE ad.alumno.id = :alumno AND d.codigo = :disciplina AND ad.activo = TRUE""", Object[].class)
				.setParameter("alumno", alumnoId).setParameter("disciplina", grupo.disciplina()).getResultList();
		if (filas.isEmpty()) {
			throw new NoSuchElementException(SIN_DISCIPLINA);
		}
		Object[] fila = filas.get(0);
		if (!Boolean.TRUE.equals(fila[3])) {
			throw new IllegalStateException(DESACTIVADAS);
		}
		if (fila[1] == null) {
			throw new IllegalStateException(SIN_GRADO);
		}
		if (!ReglaRallas.gradoLlevaRallas((String) fila[2])) {
			throw new IllegalStateException(GRADO_SIN_RALLAS);
		}
		return new Inscripcion((Long) fila[0], (Long) fila[1]);
	}

	/** {@code [medias, gradoId, apto]} confirmados de la inscripción. */
	private Object[] leer(Long inscripcionId) {
		List<Object[]> filas = inscripciones.leerRallas(inscripcionId);
		if (filas.isEmpty()) {
			throw new NoSuchElementException(SIN_DISCIPLINA);
		}
		return filas.get(0);
	}

	/** Titular del grupo: el grupo es de sus grupos (membresía PROFESOR activa) y su asignación es TITULAR. */
	private boolean esTitular(AlcanceSesion alcance, Long grupoId) {
		if (grupoId == null || !alcance.grupos().contains(grupoId)) {
			return false;
		}
		Long organizacion = TenantContext.get();
		Long usuario = CobroService.usuarioActualId();
		if (organizacion == null || usuario == null) {
			return false;
		}
		for (AsignacionProfesor asignacion : asignaciones.findByUsuarioIdAndOrganizacionId(usuario, organizacion)) {
			if (grupoId.equals(asignacion.getGrupoId()) && asignacion.getRol() == RolAsignacionProfesor.TITULAR) {
				return true;
			}
		}
		return false;
	}

	private List<Disciplina> disciplinasConGrados() {
		return new ArrayList<>(em.createQuery("""
				SELECT d FROM Disciplina d WHERE d.activa = TRUE AND d.sistemaGrados IS NOT NULL
				ORDER BY d.orden, d.id""", Disciplina.class).getResultList());
	}

	private static String ruta(Via via, Long grupoId, Long alumnoId, String sufijo) {
		String prefijo = via == Via.PROFESOR ? "/api/profesor/grupos/" : "/api/grupos/";
		return prefijo + grupoId + "/alumnos/" + alumnoId + "/rallas" + sufijo;
	}
}
