package com.taemoi.project.acceso;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Sede;
import com.taemoi.project.organizacion.AlcanceActual;
import com.taemoi.project.organizacion.AlcanceSesion;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.SedeRepository;
import com.taemoi.project.services.SedeService;

/**
 * Comprobaciones de servicio de la entrega 6 (entrega6-plan.md §2.3): listados y datos que llegan en el cuerpo, donde
 * el {@code @PreAuthorize} no puede mirar el recurso.
 *
 * <p>Solo restringe una sesión con {@code ROLE_RESPONSABLE_SEDE} sin alcance total ({@link #restriccion()}). Con
 * gestión, sin sesión (tareas, tests de servicio) o con cualquier otra sesión, los servicios se comportan como antes
 * de la entrega 6: NO es un control de acceso por sí solo. Las rutas que llegan a estos servicios ya exigen gestión
 * o responsable en la regla de URL y en el {@code @PreAuthorize}; un servicio nuevo que lo use desde una ruta sin
 * esa guarda no queda restringido.
 *
 * <p>Las denegaciones lanzan {@link AccessDeniedException}: Spring Security responde 403 {@code "Acceso denegado."},
 * como las de {@code @PreAuthorize}. Los datos no válidos lanzan {@link IllegalArgumentException} (400 en
 * {@code GlobalExceptionHandler}).
 */
@Component
public class GestionPorSede {

	private final AlcanceActual alcanceActual;
	private final SedeRepository sedes;
	private final AlumnoRepository alumnos;
	private final ConsultaAccesoRecursos consulta;
	private final SedeService sedeService;

	public GestionPorSede(AlcanceActual alcanceActual, SedeRepository sedes, AlumnoRepository alumnos,
			ConsultaAccesoRecursos consulta, SedeService sedeService) {
		this.alcanceActual = alcanceActual;
		this.sedes = sedes;
		this.alumnos = alumnos;
		this.consulta = consulta;
		this.sedeService = sedeService;
	}

	/**
	 * Alcance del responsable de sede de la sesión, si hay que restringir: sesión con {@code ROLE_RESPONSABLE_SEDE} y
	 * sin alcance total. Vacío = sin restricción (comportamiento anterior a la entrega 6).
	 */
	public Optional<AlcanceSesion> restriccion() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion == null || !autenticacion.isAuthenticated()
				|| autenticacion instanceof AnonymousAuthenticationToken) {
			return Optional.empty();
		}
		boolean responsable = false;
		for (GrantedAuthority autoridad : autenticacion.getAuthorities()) {
			if (Roles.ROLE_RESPONSABLE_SEDE.name().equals(autoridad.getAuthority())) {
				responsable = true;
			}
		}
		if (!responsable) {
			return Optional.empty();
		}
		AlcanceSesion alcance = alcanceActual.obtener();
		return alcance.total() ? Optional.empty() : Optional.of(alcance);
	}

	/**
	 * Sede de un alta (alumno D6-2, grupo D6-5).
	 * <ul>
	 * <li>Con sede: debe ser de la escuela activa (si no, 400); para el responsable, una de las suyas (si no, 403);
	 * con {@code exigirActiva}, activa (D6-17; si no, 400).</li>
	 * <li>Sin sede, responsable: su única sede; con varias, 400 (obligatoria).</li>
	 * <li>Sin sede, resto: la única sede activa de la escuela ({@link SedeService#sedePorDefecto}), o {@code null}
	 * con varias o ninguna (como hasta ahora).</li>
	 * </ul>
	 */
	public Long sedeDeAlta(Long solicitada, boolean exigirActiva) {
		Optional<AlcanceSesion> restriccion = restriccion();
		Long sedeId = solicitada;
		if (sedeId == null) {
			if (restriccion.isEmpty()) {
				return sedeService.sedePorDefecto().orElse(null);
			}
			Set<Long> suyas = restriccion.get().sedes();
			if (suyas.size() != 1) {
				throw new IllegalArgumentException("Indica la sede.");
			}
			sedeId = suyas.iterator().next();
		}
		Sede sede = sedes.buscarEnEscuelaActiva(sedeId)
				.orElseThrow(() -> new IllegalArgumentException("La sede no existe en la escuela."));
		if (restriccion.isPresent() && !restriccion.get().sedes().contains(sede.getId())) {
			throw new AccessDeniedException("Acceso denegado.");
		}
		if (exigirActiva && !sede.isActiva()) {
			throw new IllegalArgumentException("La sede está inactiva.");
		}
		return sede.getId();
	}

	/** D6-17: en una sede inactiva no se crean grupos ni turnos. Sin sede no se comprueba. */
	public void exigirSedeActiva(Long sedeId) {
		if (sedeId == null) {
			return;
		}
		Optional<Sede> sede = sedes.buscarEnEscuelaActiva(sedeId);
		if (sede.isPresent() && !sede.get().isActiva()) {
			throw new IllegalArgumentException("La sede está inactiva.");
		}
	}

	/** Para el responsable: la sede es una de las suyas (si no, 403). Sin restricción, nada. */
	public void exigirSedePropia(Long sedeId) {
		Optional<AlcanceSesion> restriccion = restriccion();
		if (restriccion.isPresent() && (sedeId == null || !restriccion.get().sedes().contains(sedeId))) {
			throw new AccessDeniedException("Acceso denegado.");
		}
	}

	/** Para el responsable: el grupo es de una de sus sedes (si no, 403; también si no existe). Sin restricción, nada. */
	public void exigirGrupoPropio(Long grupoId) {
		Optional<AlcanceSesion> restriccion = restriccion();
		if (restriccion.isPresent() && !consulta.grupoEnSedes(grupoId, restriccion.get().sedes())) {
			throw new AccessDeniedException("Acceso denegado.");
		}
	}

	/**
	 * D6-6: para el responsable, todos los alumnos están en su alcance (D9). Una sola consulta que compara el
	 * recuento; un id que no existe en la escuela también deniega. Sin restricción, nada.
	 */
	public void exigirAlumnosEnAlcance(Collection<Long> ids) {
		Optional<AlcanceSesion> restriccion = restriccion();
		if (restriccion.isEmpty() || ids == null) {
			return;
		}
		Set<Long> distintos = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
		if (distintos.isEmpty()) {
			return;
		}
		Specification<Alumno> porIds = (root, query, cb) -> root.get("id").in(distintos);
		long visibles = alumnos.count(porIds.and(PredicadosAlcance.alumnosDeSusSedes(restriccion.get())));
		if (visibles != distintos.size()) {
			throw new AccessDeniedException("Acceso denegado.");
		}
	}

	/** Ids de los alumnos del alcance del responsable (D9), activos o no; vacío si no hay restricción. */
	public Optional<Set<Long>> idsAlumnosEnAlcance() {
		return restriccion().map(alcance -> alumnos.buscarIds(PredicadosAlcance.alumnosDeSusSedes(alcance), true)
				.stream().collect(Collectors.toSet()));
	}
}
