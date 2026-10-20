package com.taemoi.project.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.taemoi.project.entities.Organizacion;

import jakarta.persistence.LockModeType;

public interface OrganizacionRepository extends JpaRepository<Organizacion, Long> {

	List<Organizacion> findByActivaTrueOrderByIdAsc();

	Optional<Organizacion> findBySlug(String slug);

	/** Pieza E7-7a: la escuela bloqueada para cambiar su logo (dos subidas a la vez no dejan un logo huérfano). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT o FROM Organizacion o WHERE o.id = :id")
	Optional<Organizacion> findByIdParaActualizar(@Param("id") Long id);
}
