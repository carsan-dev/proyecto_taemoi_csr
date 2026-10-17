package com.taemoi.project.acceso;

import java.util.Set;

import org.springframework.security.core.Authentication;

/**
 * Puerto de datos de {@link AccesoRecursos} (entrega 5c, entrega5-plan.md §4.2): TODA lectura de datos que hace el
 * bean pasa por aquí, y la lógica de rol × operación no toca datos. Así la matriz de permisos (5c-2) puede sustituir
 * este puerto por un registrador y seguir evaluando la lógica real.
 *
 * <p>Cada método responde si el recurso de la escuela ACTIVA ({@code TenantContext}) está en las sedes o en los
 * grupos dados. Con id nulo, conjunto vacío o sin escuela activa responde {@code false}.
 */
public interface ConsultaAccesoRecursos {

	/** Regla única de familia ({@code AccesoFamiliaService.puedeAcceder}) para la cuenta de la autenticación. */
	boolean familiaVeAlumno(Authentication autenticacion, Long alumnoId);

	/** D9: sede principal del alumno en {@code sedes} o alumno de algún grupo de esas sedes. */
	boolean alumnoEnSedes(Long alumnoId, Set<Long> sedes);

	/** Entrega 6 (D6-3): la sede principal del alumno es una de {@code sedes} (sin mirar sus grupos). */
	boolean alumnoConSedePrincipalEn(Long alumnoId, Set<Long> sedes);

	/** Alumno de alguno de esos grupos. */
	boolean alumnoEnGrupos(Long alumnoId, Set<Long> grupos);

	boolean grupoEnSedes(Long grupoId, Set<Long> sedes);

	boolean grupoEnGrupos(Long grupoId, Set<Long> grupos);

	/** Por el grupo del turno; un turno sin grupo no está en ningún alcance. */
	boolean turnoEnSedes(Long turnoId, Set<Long> sedes);

	boolean turnoEnGrupos(Long turnoId, Set<Long> grupos);

	/** Por el grupo de la preinscripción; sin grupo no está en ningún alcance. */
	boolean preinscripcionEnSedes(String referencia, Set<Long> sedes);

	boolean preinscripcionEnGrupos(String referencia, Set<Long> grupos);
}
