package com.taemoi.project.repositories;

import java.util.Optional;

import com.taemoi.project.entities.Alumno;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

/**
 * La consulta filtra por la escuela activa ({@code @TenantId}) y solo se refresca lo que devuelve. El refresco lleva el modo
 * de bloqueo para leer lo último confirmado también si la transacción ya hizo otra lectura (REPEATABLE READ): la fila ya es
 * de esta transacción, así que no espera a nadie.
 */
public class AlumnoBloqueoRepositoryImpl implements AlumnoBloqueoRepository {

	@PersistenceContext
	private EntityManager entityManager;

	@Override
	public Optional<Alumno> releerBloqueado(Long id) {
		Optional<Alumno> alumno = entityManager.createQuery("SELECT a FROM Alumno a WHERE a.id = :id", Alumno.class)
				.setParameter("id", id).getResultList().stream().findFirst();
		alumno.ifPresent(ficha -> entityManager.refresh(ficha, LockModeType.PESSIMISTIC_WRITE));
		return alumno;
	}
}
