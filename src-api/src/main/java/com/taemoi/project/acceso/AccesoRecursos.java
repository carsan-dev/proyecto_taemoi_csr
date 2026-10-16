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
 * preinscripción por su grupo). {@code productoAlumno} → {@code false} (D4: sin tesorería).</li>
 * <li>{@code ROLE_PROFESOR}: solo {@code LEER}, si el recurso está en sus grupos.</li>
 * <li>Con varios roles basta con que uno conceda.</li>
 * </ul>
 * Toda lectura de datos va por {@link ConsultaAccesoRecursos}: aquí solo se decide rol × operación.
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

	public boolean alumno(Long alumnoId, String operacion) {
		return alumno(alumnoId, operacion(operacion, "alumno"));
	}

	public boolean alumno(Long alumnoId, OperacionAcceso operacion) {
		return decidir(alumnoId, operacion, a -> consulta.familiaVeAlumno(a, alumnoId),
				sedes -> consulta.alumnoEnSedes(alumnoId, sedes), grupos -> consulta.alumnoEnGrupos(alumnoId, grupos));
	}

	public boolean grupo(Long grupoId, String operacion) {
		return grupo(grupoId, operacion(operacion, "grupo"));
	}

	public boolean grupo(Long grupoId, OperacionAcceso operacion) {
		return decidir(grupoId, operacion, null, sedes -> consulta.grupoEnSedes(grupoId, sedes),
				grupos -> consulta.grupoEnGrupos(grupoId, grupos));
	}

	public boolean turno(Long turnoId, String operacion) {
		return turno(turnoId, operacion(operacion, "turno"));
	}

	public boolean turno(Long turnoId, OperacionAcceso operacion) {
		return decidir(turnoId, operacion, null, sedes -> consulta.turnoEnSedes(turnoId, sedes),
				grupos -> consulta.turnoEnGrupos(turnoId, grupos));
	}

	/** Las rutas de preinscripción usan su {@code referencia}, no el id. */
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
