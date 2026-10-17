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

	/**
	 * D6-17: en una sede inactiva no se crean grupos, turnos ni cargos (6-3: la sede del cargo es la sede principal del
	 * alumno). Sin sede no se comprueba. Para todos los roles; en Moiskimdo su única sede está activa.
	 *
	 * @throws SedeInactivaException (un {@link IllegalArgumentException}, 400)
	 */
	public void exigirSedeActiva(Long sedeId) {
		if (sedeId == null) {
			return;
		}
		Optional<Sede> sede = sedes.buscarEnEscuelaActiva(sedeId);
		if (sede.isPresent() && !sede.get().isActiva()) {
			throw new SedeInactivaException();
		}
	}

	/**
	 * D6-17 en las generaciones masivas (solo gestión, D6-10): ids de las sedes inactivas de la escuela activa, para
	 * saltar a sus alumnos sin detener la generación del resto. Vacío si todas están activas (Moiskimdo): la generación
	 * no cambia.
	 */
	public Set<Long> sedesInactivas() {
		return sedes.findAllByOrderByIdAsc().stream().filter(sede -> !sede.isActiva()).map(Sede::getId)
				.collect(Collectors.toSet());
	}

	/**
	 * Entrega 6-3 (D6-12): sedes de la tesorería de esta petición, por la sede del cargo.
	 * <ul>
	 * <li>Gestión (o sin sesión de responsable) sin {@code sedeSolicitada}: {@link FiltroSedesTesoreria#TODAS}, las
	 * consultas de siempre.</li>
	 * <li>Responsable sin {@code sedeSolicitada}: sus sedes.</li>
	 * <li>Con {@code sedeSolicitada}: debe ser de la escuela activa (si no, 400); para el responsable, una de las suyas
	 * (si no, 403). Solo esa sede.</li>
	 * </ul>
	 */
	public FiltroSedesTesoreria filtroTesoreria(Long sedeSolicitada) {
		Optional<AlcanceSesion> restriccion = restriccion();
		if (sedeSolicitada == null) {
			return restriccion.map(alcance -> FiltroSedesTesoreria.de(alcance.sedes()))
					.orElse(FiltroSedesTesoreria.TODAS);
		}
		Sede sede = sedes.buscarEnEscuelaActiva(sedeSolicitada)
				.orElseThrow(() -> new IllegalArgumentException("La sede no existe en la escuela."));
		if (restriccion.isPresent() && !restriccion.get().sedes().contains(sede.getId())) {
			throw new AccessDeniedException("Acceso denegado.");
		}
		return FiltroSedesTesoreria.de(Set.of(sede.getId()));
	}

	/**
	 * Entrega 6-3 (D6-3): para el responsable, la sede principal de CADA alumno es una de las suyas (certificados de
	 * cobros). Una consulta que compara el recuento; un id que no existe en la escuela también deniega. Sin
	 * restricción, nada.
	 */
	public void exigirAlumnosConSedePrincipalPropia(Collection<Long> ids) {
		Optional<AlcanceSesion> restriccion = restriccion();
		if (restriccion.isEmpty() || ids == null) {
			return;
		}
		Set<Long> distintos = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
		if (distintos.isEmpty()) {
			return;
		}
		Specification<Alumno> porIds = (root, query, cb) -> root.get("id").in(distintos);
		long propios = alumnos
				.count(porIds.and(PredicadosAlcance.alumnosConSedePrincipalEn(restriccion.get().sedes())));
		if (propios != distintos.size()) {
			throw new AccessDeniedException("Acceso denegado.");
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
	 * D6-3 (entrega 6-2, vincular una preinscripción a una ficha): para el responsable, la sede principal del alumno es
	 * una de las suyas (si no, 403; también si no existe o no tiene sede principal). Sin restricción, nada.
	 */
	public void exigirAlumnoConSedePrincipalPropia(Long alumnoId) {
		Optional<AlcanceSesion> restriccion = restriccion();
		if (restriccion.isPresent() && !consulta.alumnoConSedePrincipalEn(alumnoId, restriccion.get().sedes())) {
			throw new AccessDeniedException("Acceso denegado.");
		}
	}

	/**
	 * Para el responsable, si puede vincular al alumno a una preinscripción: su sede principal es una de las suyas
	 * (D6-3; los visibles solo por grupo, no). Sin restricción, siempre.
	 */
	public boolean alumnoVinculable(Long alumnoId) {
		Optional<AlcanceSesion> restriccion = restriccion();
		return restriccion.isEmpty() || consulta.alumnoConSedePrincipalEn(alumnoId, restriccion.get().sedes());
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
