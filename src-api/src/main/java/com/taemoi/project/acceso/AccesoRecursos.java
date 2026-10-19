package com.taemoi.project.acceso;

import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.taemoi.project.entities.Roles;
import com.taemoi.project.organizacion.AlcanceActual;
import com.taemoi.project.organizacion.AlcanceSesion;

/**
 * Acceso por recurso de la sesión en curso (entrega 5c, entrega5-plan.md §4.2). Se usa desde los controladores y,
 * a partir de la 5c-2, en SpEL: {@code @acceso.alumno(#alumnoId, 'LEER')}.
 *
 * <ul>
 * <li>Sin sesión, anónima, id nulo u operación desconocida → {@code false}.</li>
 * <li>Alcance total ({@link AlcanceActual}: {@code ROLE_ADMIN} o {@code ROLE_MANAGER}) → {@code true} SIN
 * consultar nada (coste de hoy).</li>
 * <li>Familia ({@code ROLE_USER}): solo {@code alumno(id, LEER)}, con la regla única de familia.</li>
 * <li>{@code ROLE_RESPONSABLE_SEDE}: el recurso está en sus sedes (alumno por D9; grupo por su sede; turno y
 * preinscripción por su grupo). {@code productoAlumno} → {@code false}: la tesorería del responsable (entrega 6-3)
 * va por {@link #productoAlumnoEnSede}.</li>
 * <li>{@code ROLE_PROFESOR}: solo {@code LEER}, si el recurso está en sus grupos.</li>
 * <li>Con varios roles basta con que uno conceda.</li>
 * </ul>
 * Toda lectura de datos va por {@link ConsultaAccesoRecursos}: aquí solo se decide rol × operación.
 *
 * <p><b>Entrega 6 (entrega6-plan.md §2.1).</b> Las rutas que se abren al responsable de sede usan los métodos
 * {@code ...EnSede} y {@link #alumnoComoFamiliaOSede}: conceden con alcance total o por las sedes de
 * {@code ROLE_RESPONSABLE_SEDE} (y, el último, por la regla de familia de hoy), NUNCA por la rama de profesor. Así
 * una cuenta FAMILIA + PROFESOR o RESPONSABLE_SEDE + PROFESOR no lee por sus grupos antes de la entrega 7 (D5-2).
 * Los métodos {@link #alumno}, {@link #grupo}, {@link #turno} y {@link #preinscripcion} SÍ activan la rama de
 * profesor: quedan para la entrega 7.
 *
 * <p><b>Pieza 6b-2 (caja).</b> Métodos NUEVOS y específicos para las rutas nuevas de {@code /api/caja}, sin abrir ninguna
 * ruta existente al profesor:
 * <ul>
 * <li>{@link #cargoDeMisGrupos}, {@link #alumnoDeMisGrupos} y {@link #cobroPropioDelProfesor}: SOLO la rama de profesor ({@code ROLE_PROFESOR}),
 * también para escribir (cobrar en efectivo y deshacer su cobro, D4). El alcance total NO concede: la gestión cobra por
 * el PUT del cargo y su dinero entra en caja al momento (decisión 1); una cuenta de gestión que además sea profesor no
 * tiene grupos en el alcance ({@link AlcanceSesion#TOTAL}) y no entra por aquí. Abren sesión de profesor la cuenta solo
 * profesor y la responsable de sede + profesor (decisiones 180 y 184, restringidas a su caja por
 * {@code SesionSoloProfesorAuthorizationManager}) y la mixta FAMILIA + PROFESOR: en estas rutas la rama de profesor SÍ
 * cuenta; D5-2 sigue valiendo para las rutas existentes.</li>
 * <li>{@link #cajaDeSede}: alcance total o sedes de {@code ROLE_RESPONSABLE_SEDE}; nunca la rama de profesor.</li>
 * </ul>
 */
@Component("acceso")
public class AccesoRecursos {

	private static final Logger logger = LoggerFactory.getLogger(AccesoRecursos.class);

	private final AlcanceActual alcanceActual;
	private final ConsultaAccesoRecursos consulta;

	public AccesoRecursos(AlcanceActual alcanceActual, ConsultaAccesoRecursos consulta) {
		this.alcanceActual = alcanceActual;
		this.consulta = consulta;
	}

	/**
	 * Regla de las rutas de familia hasta las entregas 6 y 7 (D5-2): alcance total → {@code true} sin consultar; si
	 * no, SOLO la regla de familia de hoy ({@code ROLE_USER} + {@code AccesoFamiliaService}). Sin ramas de sede ni de
	 * grupo: una cuenta mixta FAMILIA + PROFESOR/RESPONSABLE_SEDE entra solo como familia. {@link #alumno} queda para
	 * los endpoints que abran esas entregas.
	 */
	public boolean alumnoComoFamilia(Long alumnoId) {
		return decidir(alumnoId, OperacionAcceso.LEER, a -> consulta.familiaVeAlumno(a, alumnoId), null, null);
	}

	/**
	 * Entrega 6: familia (regla de hoy, solo lectura) o responsable de sede por D9; nunca la rama de profesor. Para
	 * las rutas de lectura que la familia comparte con el responsable (foto, grupos, turnos, deportes, documentos).
	 */
	public boolean alumnoComoFamiliaOSede(Long alumnoId) {
		return decidir(alumnoId, OperacionAcceso.LEER, a -> consulta.familiaVeAlumno(a, alumnoId),
				sedes -> consulta.alumnoEnSedes(alumnoId, sedes), null);
	}

	/**
	 * Entrega 6, solo alcance total o sedes del responsable. {@code LEER}: regla D9 (sede principal o grupo de sus
	 * sedes, D6-1). {@code EDITAR}: solo si su sede principal es una de las suyas (D6-3); sin sede principal, nadie
	 * salvo gestión.
	 */
	public boolean alumnoEnSede(Long alumnoId, String operacion) {
		OperacionAcceso op = operacion(operacion, "alumnoEnSede");
		Predicate<Set<Long>> porSedes = op == OperacionAcceso.EDITAR
				? sedes -> consulta.alumnoConSedePrincipalEn(alumnoId, sedes)
				: sedes -> consulta.alumnoEnSedes(alumnoId, sedes);
		return decidir(alumnoId, op, null, porSedes, null);
	}

	/** Entrega 6, solo alcance total o sedes del responsable: el grupo es de una de sus sedes. */
	public boolean grupoEnSede(Long grupoId, String operacion) {
		return decidir(grupoId, operacion(operacion, "grupoEnSede"), null,
				sedes -> consulta.grupoEnSedes(grupoId, sedes), null);
	}

	/** Entrega 6, solo alcance total o sedes del responsable: el grupo del turno es de una de sus sedes. */
	public boolean turnoEnSede(Long turnoId, String operacion) {
		return decidir(turnoId, operacion(operacion, "turnoEnSede"), null,
				sedes -> consulta.turnoEnSedes(turnoId, sedes), null);
	}

	/**
	 * Entrega 6-2, solo alcance total o sedes del responsable: el grupo de la preinscripción es de una de sus sedes
	 * (con D6-7 todos sus turnos son de la misma sede). Sin grupo, solo gestión.
	 */
	public boolean preinscripcionEnSede(String referencia, String operacion) {
		return decidir(referencia, operacion(operacion, "preinscripcionEnSede"), null,
				sedes -> consulta.preinscripcionEnSedes(referencia, sedes), null);
	}

	/**
	 * Entrega 6-2 (D6-8), solo alcance total o sedes del responsable, por el alumno de la inscripción en la
	 * convocatoria: {@code LEER} por D9; {@code EDITAR} (cuantía y pago del examen) solo si su sede principal es una de
	 * las suyas (D6-3).
	 */
	public boolean alumnoConvocatoriaEnSede(Long alumnoConvocatoriaId, String operacion) {
		OperacionAcceso op = operacion(operacion, "alumnoConvocatoriaEnSede");
		Predicate<Set<Long>> porSedes = op == OperacionAcceso.EDITAR
				? sedes -> consulta.alumnoConvocatoriaConSedePrincipalEn(alumnoConvocatoriaId, sedes)
				: sedes -> consulta.alumnoConvocatoriaEnSedes(alumnoConvocatoriaId, sedes);
		return decidir(alumnoConvocatoriaId, op, null, porSedes, null);
	}

	/**
	 * Entrega 6-3 (D4, D6-9), solo alcance total o sedes del responsable: la sede del cargo (fijada al crearlo) es una
	 * de las suyas, para cualquier operación (cobrar un cargo de una sede inactiva sigue permitido, D6-17). Un cargo
	 * sin sede, solo gestión. Revertir un cobro sigue siendo solo de {@code ROLE_ADMIN} (en el servicio).
	 */
	public boolean productoAlumnoEnSede(Long productoAlumnoId, String operacion) {
		return decidir(productoAlumnoId, operacion(operacion, "productoAlumnoEnSede"), null,
				sedes -> consulta.productoAlumnoEnSedes(productoAlumnoId, sedes), null);
	}

	/** Activa también la rama de profesor (solo {@code LEER}): para la entrega 7. */
	public boolean alumno(Long alumnoId, String operacion) {
		return alumno(alumnoId, operacion(operacion, "alumno"));
	}

	public boolean alumno(Long alumnoId, OperacionAcceso operacion) {
		return decidir(alumnoId, operacion, a -> consulta.familiaVeAlumno(a, alumnoId),
				sedes -> consulta.alumnoEnSedes(alumnoId, sedes), grupos -> consulta.alumnoEnGrupos(alumnoId, grupos));
	}

	/** Activa también la rama de profesor (solo {@code LEER}): para la entrega 7. */
	public boolean grupo(Long grupoId, String operacion) {
		return grupo(grupoId, operacion(operacion, "grupo"));
	}

	public boolean grupo(Long grupoId, OperacionAcceso operacion) {
		return decidir(grupoId, operacion, null, sedes -> consulta.grupoEnSedes(grupoId, sedes),
				grupos -> consulta.grupoEnGrupos(grupoId, grupos));
	}

	/** Activa también la rama de profesor (solo {@code LEER}): para la entrega 7. */
	public boolean turno(Long turnoId, String operacion) {
		return turno(turnoId, operacion(operacion, "turno"));
	}

	public boolean turno(Long turnoId, OperacionAcceso operacion) {
		return decidir(turnoId, operacion, null, sedes -> consulta.turnoEnSedes(turnoId, sedes),
				grupos -> consulta.turnoEnGrupos(turnoId, grupos));
	}

	/**
	 * Las rutas de preinscripción usan su {@code referencia}, no el id. Activa también la rama de profesor: para la
	 * entrega 7 (las rutas de la 6-2 usan {@link #preinscripcionEnSede}).
	 */
	public boolean preinscripcion(String referencia, String operacion) {
		return preinscripcion(referencia, operacion(operacion, "preinscripcion"));
	}

	public boolean preinscripcion(String referencia, OperacionAcceso operacion) {
		return decidir(referencia, operacion, null, sedes -> consulta.preinscripcionEnSedes(referencia, sedes),
				grupos -> consulta.preinscripcionEnGrupos(referencia, grupos));
	}

	public boolean productoAlumno(Long productoAlumnoId, String operacion) {
		return productoAlumno(productoAlumnoId, operacion(operacion, "productoAlumno"));
	}

	/** Solo con alcance total: ni familia, ni responsable (D4), ni profesor. */
	public boolean productoAlumno(Long productoAlumnoId, OperacionAcceso operacion) {
		return decidir(productoAlumnoId, operacion, null, null, null);
	}

	/**
	 * Pieza 6b-2 (D4): el alumno del cargo es de alguno de los grupos del profesor de la sesión. Solo la rama de
	 * profesor, para cualquier operación de las rutas del profesor (ver la cabecera). Gestión → {@code false}.
	 */
	public boolean cargoDeMisGrupos(Long productoAlumnoId) {
		return comoProfesor(productoAlumnoId, grupos -> consulta.productoAlumnoEnGrupos(productoAlumnoId, grupos));
	}

	/**
	 * Pieza 6b-2: el alumno es de alguno de los grupos del profesor de la sesión (apuntar y cobrar un producto). Solo
	 * la rama de profesor, para escribir (ver la cabecera). Gestión → {@code false}.
	 */
	public boolean alumnoDeMisGrupos(Long alumnoId) {
		return comoProfesor(alumnoId, grupos -> consulta.alumnoEnGrupos(alumnoId, grupos));
	}

	/**
	 * Pieza 6b-2 (decisión 3): el cobro lo cobró el profesor de la sesión (cobrado_por = él, origen PROFESOR). Solo
	 * {@code ROLE_PROFESOR}; no exige que el alumno siga en sus grupos (es su propio cobro). Activo y sin entrega lo
	 * comprueba el servicio con la fila bloqueada (409).
	 */
	public boolean cobroPropioDelProfesor(Long cobroId) {
		Authentication autenticacion = sesionValida();
		if (cobroId == null || autenticacion == null || !tieneAutoridad(autenticacion, Roles.ROLE_PROFESOR)) {
			return false;
		}
		Long usuarioId = autenticacion.getPrincipal() instanceof com.taemoi.project.entities.Usuario usuario
				? usuario.getId()
				: null;
		return consulta.cobroDelProfesor(cobroId, usuarioId);
	}

	/**
	 * Pieza 6b-2: caja de la sede. Alcance total (gestor y propietario) → {@code true} sin consultar; responsable de
	 * sede → la sede es una de las suyas (y de la escuela activa). Nunca la rama de profesor.
	 */
	public boolean cajaDeSede(Long sedeId) {
		return decidir(sedeId, OperacionAcceso.EDITAR, null, sedes -> consulta.sedeEnSedes(sedeId, sedes), null);
	}

	/**
	 * Pieza 6c-2 (fase3-6c-solicitudes.md §7.2): la solicitud es de una de las sedes del responsable. Alcance total
	 * (gestor y propietario) → {@code true} sin consultar; responsable de sede → la sede de la solicitud es una de las
	 * suyas (sin sede, nunca). Nunca la rama de profesor ni la de familia. Lo usan las rutas de encargos y devoluciones
	 * de la bandeja (6c-5 y 6c-6).
	 */
	public boolean solicitudEnSede(Long solicitudId) {
		return decidir(solicitudId, OperacionAcceso.EDITAR, null, sedes -> consulta.solicitudEnSedes(solicitudId, sedes),
				null);
	}

	/** Solo la rama de profesor, con su conjunto de grupos no vacío; sin mirar la operación. */
	private boolean comoProfesor(Object id, Predicate<Set<Long>> porGrupos) {
		Authentication autenticacion = sesionValida();
		if (id == null || autenticacion == null || !tieneAutoridad(autenticacion, Roles.ROLE_PROFESOR)) {
			return false;
		}
		AlcanceSesion alcance = alcanceActual.obtener();
		return !alcance.total() && concede(porGrupos, alcance.grupos());
	}

	private static Authentication sesionValida() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion == null || !autenticacion.isAuthenticated()
				|| autenticacion instanceof AnonymousAuthenticationToken) {
			return null;
		}
		return autenticacion;
	}

	private static boolean tieneAutoridad(Authentication autenticacion, Roles rol) {
		for (GrantedAuthority autoridad : autenticacion.getAuthorities()) {
			if (rol.name().equals(autoridad.getAuthority())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @param familia   comprobación de familia (solo con {@code LEER}); {@code null} si el recurso no la admite.
	 * @param porSedes  comprobación del responsable de sede; {@code null} si no la admite.
	 * @param porGrupos comprobación del profesor (solo con {@code LEER}); {@code null} si no la admite.
	 */
	private boolean decidir(Object id, OperacionAcceso operacion, Predicate<Authentication> familia,
			Predicate<Set<Long>> porSedes, Predicate<Set<Long>> porGrupos) {
		if (id == null || operacion == null) {
			return false;
		}
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion == null || !autenticacion.isAuthenticated()
				|| autenticacion instanceof AnonymousAuthenticationToken) {
			return false;
		}
		AlcanceSesion alcance = alcanceActual.obtener();
		if (alcance.total()) {
			return true;
		}
		Set<String> autoridades = autenticacion.getAuthorities().stream().map(GrantedAuthority::getAuthority)
				.collect(Collectors.toSet());
		if (familia != null && operacion == OperacionAcceso.LEER && autoridades.contains(Roles.ROLE_USER.name())
				&& familia.test(autenticacion)) {
			return true;
		}
		if (porSedes != null && autoridades.contains(Roles.ROLE_RESPONSABLE_SEDE.name())
				&& concede(porSedes, alcance.sedes())) {
			return true;
		}
		return porGrupos != null && operacion == OperacionAcceso.LEER
				&& autoridades.contains(Roles.ROLE_PROFESOR.name()) && concede(porGrupos, alcance.grupos());
	}

	/** Con el conjunto vacío no se consulta. */
	private static boolean concede(Predicate<Set<Long>> comprobacion, Set<Long> conjunto) {
		return !conjunto.isEmpty() && comprobacion.test(conjunto);
	}

	private static OperacionAcceso operacion(String nombre, String recurso) {
		if (nombre != null) {
			for (OperacionAcceso operacion : OperacionAcceso.values()) {
				if (operacion.name().equals(nombre)) {
					return operacion;
				}
			}
		}
		logger.warn("Operación de acceso desconocida en {}: {}", recurso, nombre);
		return null;
	}
}
