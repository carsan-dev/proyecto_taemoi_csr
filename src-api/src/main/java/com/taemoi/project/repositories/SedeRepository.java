package com.taemoi.project.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.taemoi.project.entities.Sede;

/** Sedes de la escuela activa ({@code @TenantId}: Hibernate filtra por organización). */
public interface SedeRepository extends JpaRepository<Sede, Long> {

	List<Sede> findAllByOrderByIdAsc();

	List<Sede> findByActivaTrueOrderByIdAsc();

	/** Pieza E8-1: las sedes del responsable de la sesión (filtradas por la escuela activa), por id. */
	List<Sede> findByIdInOrderByIdAsc(Collection<Long> ids);

	/** Pieza E8-1: sedes activas de la escuela activa ({@code variasSedes} de {@code GET /api/auth/permisos}). */
	long countByActivaTrue();

	/** Por id con una consulta (filtrada por la escuela activa), no con {@code findById}. Entrega 6-1b. */
	@Query("SELECT s FROM Sede s WHERE s.id = :id")
	Optional<Sede> buscarEnEscuelaActiva(@Param("id") Long id);
}
