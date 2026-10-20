package com.taemoi.project.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.taemoi.project.entities.Sede;

import jakarta.persistence.LockModeType;

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

	/**
	 * Pieza E8-2: TODAS las sedes de la escuela activa, bloqueadas ({@code SELECT ... FOR UPDATE}, por id) para activar
	 * o desactivar sin que dos peticiones a la vez dejen la escuela sin sedes activas. Llamarla antes de leer ninguna
	 * sede en la transacción, para que las entidades lleguen con el estado ya confirmado.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT s FROM Sede s ORDER BY s.id")
	List<Sede> bloquearDeLaEscuela();

	/** Pieza E8-2: nombre ya usado en la escuela activa, con la colación de la columna (como la clave única). */
	boolean existsByNombre(String nombre);

	/** Ídem, sin contar la sede {@code id} (renombrar una sede a su propio nombre vale). */
	boolean existsByNombreAndIdNot(String nombre, Long id);
}
