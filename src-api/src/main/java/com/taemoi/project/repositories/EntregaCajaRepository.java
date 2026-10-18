package com.taemoi.project.repositories;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.taemoi.project.entities.EntregaCaja;

import jakarta.persistence.LockModeType;

/** Entregas de caja de la escuela activa (pieza 6b-2); el filtro por organización lo pone {@code @TenantId}. */
public interface EntregaCajaRepository extends JpaRepository<EntregaCaja, Long> {

	/** Bloquea (SELECT ... FOR UPDATE) la entrega. Solo en transacción. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT e FROM EntregaCaja e WHERE e.id = :id")
	Optional<EntregaCaja> bloquearPorId(@Param("id") Long id);

	/** Historial de la sede, de la más reciente a la más antigua. */
	@Query(value = "SELECT e FROM EntregaCaja e WHERE e.sedeId = :sedeId ORDER BY e.fecha DESC, e.id DESC",
			countQuery = "SELECT COUNT(e) FROM EntregaCaja e WHERE e.sedeId = :sedeId")
	Page<EntregaCaja> historialDeSede(@Param("sedeId") Long sedeId, Pageable pagina);

	/** Σ de lo contado en las entregas ACTIVAS de la sede con fecha en [desde, hasta). */
	@Query("SELECT COALESCE(SUM(e.importeContado), 0) FROM EntregaCaja e WHERE e.sedeId = :sedeId "
			+ "AND e.estado = com.taemoi.project.entities.EstadoEntregaCaja.ACTIVA "
			+ "AND e.fecha >= :desde AND e.fecha < :hasta")
	BigDecimal sumarContadoActivas(@Param("sedeId") Long sedeId, @Param("desde") LocalDateTime desde,
			@Param("hasta") LocalDateTime hasta);
}
