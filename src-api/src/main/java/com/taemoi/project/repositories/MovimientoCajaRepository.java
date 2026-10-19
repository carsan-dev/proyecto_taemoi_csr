package com.taemoi.project.repositories;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.taemoi.project.entities.EstadoMovimientoCaja;
import com.taemoi.project.entities.MovimientoCaja;
import com.taemoi.project.entities.TipoMovimientoCaja;

import jakarta.persistence.LockModeType;

/** Movimientos de caja de la escuela activa (pieza 6b-2); el filtro por organización lo pone {@code @TenantId}. */
public interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, Long> {

	/** Bloquea (SELECT ... FOR UPDATE) el movimiento. Solo en transacción. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT m FROM MovimientoCaja m WHERE m.id = :id")
	Optional<MovimientoCaja> bloquearPorId(@Param("id") Long id);

	/**
	 * Pieza 6c-3: si hay un movimiento de ese tipo y estado enlazado al cobro (p. ej. una DEVOLUCION ACTIVA, 153: un cobro
	 * con devolución no se anula).
	 */
	boolean existsByCobroIdAndTipoAndEstado(Long cobroId, TipoMovimientoCaja tipo, EstadoMovimientoCaja estado);

	/** Movimientos (de cualquier estado) de esas entregas. */
	List<MovimientoCaja> findByEntregaCajaIdInOrderByIdAsc(Collection<Long> entregaIds);

	/** Movimientos de la sede con fecha en [desde, hasta), del más reciente al más antiguo. */
	@Query("SELECT m FROM MovimientoCaja m WHERE m.sedeId = :sedeId AND m.fecha >= :desde AND m.fecha < :hasta "
			+ "ORDER BY m.fecha DESC, m.id DESC")
	List<MovimientoCaja> deSedeEnPeriodo(@Param("sedeId") Long sedeId, @Param("desde") LocalDateTime desde,
			@Param("hasta") LocalDateTime hasta);

	/** Σ de los movimientos ACTIVOS de la sede (todos los tipos, cualquier fecha): parte del saldo de caja. */
	@Query("SELECT COALESCE(SUM(m.importe), 0) FROM MovimientoCaja m WHERE m.sedeId = :sedeId "
			+ "AND m.estado = com.taemoi.project.entities.EstadoMovimientoCaja.ACTIVO")
	BigDecimal sumarActivos(@Param("sedeId") Long sedeId);

	/** Σ de los SOBRANTE y FALTANTE ACTIVOS de la sede con fecha en [desde, hasta). */
	@Query("SELECT COALESCE(SUM(m.importe), 0) FROM MovimientoCaja m WHERE m.sedeId = :sedeId "
			+ "AND m.estado = com.taemoi.project.entities.EstadoMovimientoCaja.ACTIVO "
			+ "AND m.tipo IN (com.taemoi.project.entities.TipoMovimientoCaja.SOBRANTE, "
			+ "com.taemoi.project.entities.TipoMovimientoCaja.FALTANTE) "
			+ "AND m.fecha >= :desde AND m.fecha < :hasta")
	BigDecimal sumarDiferenciasActivas(@Param("sedeId") Long sedeId, @Param("desde") LocalDateTime desde,
			@Param("hasta") LocalDateTime hasta);
}
