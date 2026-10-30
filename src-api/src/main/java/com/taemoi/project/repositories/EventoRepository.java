package com.taemoi.project.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.taemoi.project.entities.Evento;

import jakarta.persistence.LockModeType;

public interface EventoRepository extends JpaRepository<Evento, Long> {
	@Query("select coalesce(max(e.orden), 0) from Evento e")
	Integer findMaxOrden();

	List<Evento> findByVisibleTrue();
	List<Evento> findByVisibleTrueOrderByOrdenAsc();
	List<Evento> findAllByOrderByOrdenAsc();

	/** Decisión 342: el evento bloqueado para escribir en su galería (dos subidas a la vez se ordenan). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT e FROM Evento e WHERE e.id = :id")
	Optional<Evento> findByIdParaActualizar(@Param("id") Long id);
}
