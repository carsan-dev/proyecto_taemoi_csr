package com.taemoi.project.repositories;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.taemoi.project.entities.Alumno;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/** Criteria con {@code select(id)}: Hibernate añade el filtro de escuela ({@code @TenantId}). */
public class AlumnoIdsRepositoryImpl implements AlumnoIdsRepository {

	@PersistenceContext
	private EntityManager entityManager;

	@Override
	public List<Long> buscarIds(Specification<Alumno> predicado, boolean incluirInactivos) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Long> consulta = cb.createQuery(Long.class);
		Root<Alumno> alumno = consulta.from(Alumno.class);
		List<Predicate> condiciones = new ArrayList<>();
		if (!incluirInactivos) {
			condiciones.add(cb.isTrue(alumno.get("activo")));
		}
		Predicate filtro = predicado == null ? null : predicado.toPredicate(alumno, consulta, cb);
		if (filtro != null) {
			condiciones.add(filtro);
		}
		consulta.select(alumno.<Long>get("id")).where(condiciones.toArray(new Predicate[0]))
				.orderBy(cb.asc(alumno.get("id")));
		return entityManager.createQuery(consulta).getResultList();
	}
}
