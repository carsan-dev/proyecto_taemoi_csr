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

	/** Web por plantilla: la sede existe en la escuela activa, sin cargarla en la sesión. */
	@Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM Sede s WHERE s.id = :id")
	boolean existeEnEscuelaActiva(@Param("id") Long id);

	/**
	 * Web por plantilla: una sede de la escuela activa, bloqueada ({@code SELECT ... FOR UPDATE}) para cambiar sus fotos
	 * sin que dos subidas a la vez dejen una imagen sin sede. Llamarla antes de leer esa sede en la transacción.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT s FROM Sede s WHERE s.id = :id")
	Optional<Sede> bloquearEnEscuelaActiva(@Param("id") Long id);

	/**
	 * Pieza E8-2: TODAS las sedes de la escuela activa, bloqueadas ({@code SELECT ... FOR UPDATE}, por id) para activar
	 * o desactivar sin que dos peticiones a la vez dejen la escuela sin sedes activas. Llamarla antes de leer ninguna
	 * sede en la transacción, para que las entidades lleguen con el estado ya confirmado.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT s FROM Sede s ORDER BY s.id")
	List<Sede> bloquearDeLaEscuela();

	/**
	 * Pieza 9-7: aforo de las sedes que lo tienen, de una vez. Filas {@code [sedeId, aforoTurno]} de la escuela activa; las
	 * que no lo tienen no salen. Valores leídos de la base de datos, no de las entidades de la sesión.
	 */
	@Query("SELECT s.id, s.aforoTurno FROM Sede s WHERE s.id IN :ids AND s.aforoTurno IS NOT NULL")
	List<Object[]> aforosPorSede(@Param("ids") Collection<Long> ids);

	/**
	 * Decisión 393: filas {@code [id, aforoTurno, nombre, activa]} de las sedes {@code ids} y de todas las activas de la
	 * escuela, en una consulta: da el aforo y el nombre de cada sede pedida y cuántas activas hay.
	 */
	@Query("SELECT s.id, s.aforoTurno, s.nombre, s.activa FROM Sede s WHERE s.id IN :ids OR s.activa = true")
	List<Object[]> datosSedes(@Param("ids") Collection<Long> ids);

	/** Pieza E8-2: nombre ya usado en la escuela activa, con la colación de la columna (como la clave única). */
	boolean existsByNombre(String nombre);

	/** Ídem, sin contar la sede {@code id} (renombrar una sede a su propio nombre vale). */
	boolean existsByNombreAndIdNot(String nombre, Long id);

	/** Web por plantilla (W2): el slug ya lo tiene una sede de la escuela activa. */
	boolean existsBySlug(String slug);

	boolean existsBySlugAndIdNot(String slug, Long id);
}
