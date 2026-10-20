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

	/**
	 * Σ de los movimientos ACTIVOS de la sede (todos los tipos, cualquier fecha): parte del saldo de caja. Pieza 6c-6
	 * (D6c-13): solo los EFECTIVO (una devolución de un cobro por transferencia o Bizum no sale de la caja física). Las
	 * filas anteriores a la 6c-6 son todas EFECTIVO (DEFAULT de V31_8): el saldo de siempre no cambia.
	 */
	@Query("SELECT COALESCE(SUM(m.importe), 0) FROM MovimientoCaja m WHERE m.sedeId = :sedeId "
			+ "AND m.estado = com.taemoi.project.entities.EstadoMovimientoCaja.ACTIVO "
			+ "AND m.metodo = com.taemoi.project.entities.MetodoPago.EFECTIVO")
	BigDecimal sumarActivos(@Param("sedeId") Long sedeId);

	// ------------------------------------------------------------------ pieza 6c-6: devoluciones enlazadas (153)

	/**
	 * Bloquea (SELECT ... FOR UPDATE) la DEVOLUCION ACTIVA del cobro, si la tiene (como mucho una: clave única de V31_8).
	 * Solo en transacción; quien la usa la relee.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT m FROM MovimientoCaja m WHERE m.cobroId = :cobroId "
			+ "AND m.tipo = com.taemoi.project.entities.TipoMovimientoCaja.DEVOLUCION "
			+ "AND m.estado = com.taemoi.project.entities.EstadoMovimientoCaja.ACTIVO")
	Optional<MovimientoCaja> bloquearDevolucionActivaDeCobro(@Param("cobroId") Long cobroId);

	/**
	 * De esos cargos, los devueltos (D6c-12): su cobro ACTIVO tiene una DEVOLUCION ACTIVA enlazada. Filas
	 * {@code [cargoId, Σ −importe]}.
	 */
	@Query("SELECT c.productoAlumnoId, SUM(-m.importe) FROM MovimientoCaja m, Cobro c WHERE m.cobroId = c.id "
			+ "AND m.tipo = com.taemoi.project.entities.TipoMovimientoCaja.DEVOLUCION "
			+ "AND m.estado = com.taemoi.project.entities.EstadoMovimientoCaja.ACTIVO "
			+ "AND c.estado = com.taemoi.project.entities.EstadoCobro.ACTIVO AND c.productoAlumnoId IN :cargoIds "
			+ "GROUP BY c.productoAlumnoId")
	List<Object[]> devueltoDeCargos(@Param("cargoIds") Collection<Long> cargoIds);

	/** Σ de los SOBRANTE y FALTANTE ACTIVOS de la sede con fecha en [desde, hasta). */
	@Query("SELECT COALESCE(SUM(m.importe), 0) FROM MovimientoCaja m WHERE m.sedeId = :sedeId "
			+ "AND m.estado = com.taemoi.project.entities.EstadoMovimientoCaja.ACTIVO "
			+ "AND m.tipo IN (com.taemoi.project.entities.TipoMovimientoCaja.SOBRANTE, "
			+ "com.taemoi.project.entities.TipoMovimientoCaja.FALTANTE) "
			+ "AND m.fecha >= :desde AND m.fecha < :hasta")
	BigDecimal sumarDiferenciasActivas(@Param("sedeId") Long sedeId, @Param("desde") LocalDateTime desde,
			@Param("hasta") LocalDateTime hasta);
}
