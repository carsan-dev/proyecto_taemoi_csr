package com.taemoi.project.repositories;

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

	/** Por id con una consulta (filtrada por la escuela activa), no con {@code findById}. Entrega 6-1b. */
	@Query("SELECT s FROM Sede s WHERE s.id = :id")
	Optional<Sede> buscarEnEscuelaActiva(@Param("id") Long id);
}
