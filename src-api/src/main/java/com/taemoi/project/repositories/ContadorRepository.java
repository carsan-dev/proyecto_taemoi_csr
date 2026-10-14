package com.taemoi.project.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.taemoi.project.entities.Contador;

import jakarta.persistence.LockModeType;

/** Filtrado automáticamente por la organización activa ({@code @TenantId}). */
public interface ContadorRepository extends JpaRepository<Contador, Long> {

	/**
	 * Comprobación sin bloqueo que no carga la entidad. No añadir lecturas de la entidad sin
	 * bloqueo: dejarían un valor antiguo en el contexto de persistencia (ver ContadorService).
	 */
	boolean existsByClave(String clave);

	/** {@code SELECT ... FOR UPDATE}: el bloqueo dura hasta el final de la transacción. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT c FROM Contador c WHERE c.clave = :clave")
	Optional<Contador> findByClaveParaActualizar(@Param("clave") String clave);
}
