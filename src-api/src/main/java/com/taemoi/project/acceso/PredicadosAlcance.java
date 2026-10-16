package com.taemoi.project.acceso;

import java.util.Collection;

import org.springframework.data.jpa.domain.Specification;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Grupo;
import com.taemoi.project.entities.Preinscripcion;
import com.taemoi.project.entities.Turno;
import com.taemoi.project.organizacion.AlcanceSesion;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Predicados de alcance por sede y grupo (entrega5-plan.md §3.4) como {@link Specification} reutilizables, para
 * combinarlos con los filtros de los listados (entregas 6 y 7) y para {@link ConsultaAccesoRecursosJpa}. Son
 * consultas sobre entidades {@code @TenantId}: el filtro de escuela lo añade Hibernate en cada raíz, también en las
 * subconsultas.
 *
 * <ul>
 * <li>Grupos: {@code g.sedeId IN :sedes} / {@code g.id IN :grupos}.</li>
 * <li>Alumnos (D9): sede principal en {@code :sedes} O alumno de algún grupo de esas sedes; por grupos, alumno de
 * alguno de ellos.</li>
 * <li>Turnos y preinscripciones: por su grupo; sin grupo no entran en ningún alcance limitado.</li>
 * </ul>
 * Un conjunto vacío o nulo no deja pasar nada. Las variantes con {@link AlcanceSesion} son las de lectura (sedes
 * O grupos); con alcance total no restringen.
 */
public final class PredicadosAlcance {

	private PredicadosAlcance() {
	}

	// ------------------------------------------------------------------ grupos

	public static Specification<Grupo> gruposEnSedes(Collection<Long> sedes) {
		return (root, query, cb) -> en(cb, root.get("sedeId"), sedes);
	}

	public static Specification<Grupo> gruposEnGrupos(Collection<Long> grupos) {
		return (root, query, cb) -> en(cb, root.get("id"), grupos);
	}

	public static Specification<Grupo> grupos(AlcanceSesion alcance) {
		return lectura(alcance, gruposEnSedes(alcance.sedes()), gruposEnGrupos(alcance.grupos()));
	}

	// ------------------------------------------------------------------ alumnos (D9)

	public static Specification<Alumno> alumnosEnSedes(Collection<Long> sedes) {
		return (root, query, cb) -> vacio(sedes) ? cb.disjunction()
				: cb.or(root.get("sedePrincipalId").in(sedes),
						cb.exists(grupoDelAlumno(root, query, cb, "sedeId", sedes)));
	}

	public static Specification<Alumno> alumnosEnGrupos(Collection<Long> grupos) {
		return (root, query, cb) -> vacio(grupos) ? cb.disjunction()
				: cb.exists(grupoDelAlumno(root, query, cb, "id", grupos));
	}

	public static Specification<Alumno> alumnos(AlcanceSesion alcance) {
		return lectura(alcance, alumnosEnSedes(alcance.sedes()), alumnosEnGrupos(alcance.grupos()));
	}

	// ------------------------------------------------------------------ turnos

	public static Specification<Turno> turnosEnSedes(Collection<Long> sedes) {
		return (root, query, cb) -> grupoEnSedes(root, query, cb, sedes);
	}

	public static Specification<Turno> turnosEnGrupos(Collection<Long> grupos) {
		return (root, query, cb) -> en(cb, root.get("grupo").get("id"), grupos);
	}

	public static Specification<Turno> turnos(AlcanceSesion alcance) {
		return lectura(alcance, turnosEnSedes(alcance.sedes()), turnosEnGrupos(alcance.grupos()));
	}

	// ------------------------------------------------------------------ preinscripciones

	public static Specification<Preinscripcion> preinscripcionesEnSedes(Collection<Long> sedes) {
		return (root, query, cb) -> grupoEnSedes(root, query, cb, sedes);
	}

	public static Specification<Preinscripcion> preinscripcionesEnGrupos(Collection<Long> grupos) {
		return (root, query, cb) -> en(cb, root.get("grupo").get("id"), grupos);
	}

	public static Specification<Preinscripcion> preinscripciones(AlcanceSesion alcance) {
		return lectura(alcance, preinscripcionesEnSedes(alcance.sedes()),
				preinscripcionesEnGrupos(alcance.grupos()));
	}

	// ------------------------------------------------------------------ utilidades

	private static <T> Specification<T> lectura(AlcanceSesion alcance, Specification<T> porSedes,
			Specification<T> porGrupos) {
		if (alcance == null) {
			return (root, query, cb) -> cb.disjunction();
		}
		if (alcance.total()) {
			return Specification.unrestricted();
		}
		return porSedes.or(porGrupos);
	}

	private static Predicate en(CriteriaBuilder cb, Path<?> ruta, Collection<Long> ids) {
		return vacio(ids) ? cb.disjunction() : ruta.in(ids);
	}

	/** Recurso con {@code grupo} (turno, preinscripción) cuyo grupo es de alguna de esas sedes. */
	private static Predicate grupoEnSedes(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder cb,
			Collection<Long> sedes) {
		if (vacio(sedes)) {
			return cb.disjunction();
		}
		Subquery<Long> grupos = query.subquery(Long.class);
		Root<Grupo> g = grupos.from(Grupo.class);
		grupos.select(g.<Long>get("id")).where(g.get("sedeId").in(sedes));
		return cb.in(root.get("grupo").<Long>get("id")).value(grupos);
	}

	/** Algún grupo del alumno con {@code atributo IN ids}. */
	private static Subquery<Long> grupoDelAlumno(Root<Alumno> alumno, CriteriaQuery<?> query, CriteriaBuilder cb,
			String atributo, Collection<Long> ids) {
		Subquery<Long> sub = query.subquery(Long.class);
		Root<Grupo> g = sub.from(Grupo.class);
		Join<Grupo, Alumno> miembro = g.join("alumnos");
		sub.select(g.<Long>get("id")).where(cb.equal(miembro.get("id"), alumno.get("id")), g.get(atributo).in(ids));
		return sub;
	}

	private static boolean vacio(Collection<Long> ids) {
		return ids == null || ids.isEmpty();
	}
}
