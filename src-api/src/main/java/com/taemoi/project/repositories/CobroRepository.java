package com.taemoi.project.repositories;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.taemoi.project.entities.Cobro;
import com.taemoi.project.entities.EstadoCobro;

import jakarta.persistence.LockModeType;

/** Cobros de la escuela activa (pieza 6b-1); el filtro por organización lo pone {@code @TenantId}. */
public interface CobroRepository extends JpaRepository<Cobro, Long> {

	/** El cobro en ese estado del cargo; con {@link EstadoCobro#ACTIVO} hay como mucho uno (clave única de V31_5). */
	Optional<Cobro> findFirstByProductoAlumnoIdAndEstado(Long productoAlumnoId, EstadoCobro estado);

	/** Si algún cobro del cargo va ya en una entrega de caja. */
	boolean existsByProductoAlumnoIdAndEntregaCajaIdIsNotNull(Long productoAlumnoId);

	List<Cobro> findByProductoAlumnoIdOrderByIdAsc(Long productoAlumnoId);

	/**
	 * Pieza 6b-3b-1: el primer cobro (menor id, en cualquier estado) de cada cargo: filas {@code [cargoId, cobroId]}.
	 * Una consulta para la marca {@code venta} de las filas de caja.
	 */
	@Query("SELECT c.productoAlumnoId, MIN(c.id) FROM Cobro c WHERE c.productoAlumnoId IN :cargoIds "
			+ "GROUP BY c.productoAlumnoId")
	List<Object[]> primerCobroPorCargo(@Param("cargoIds") Collection<Long> cargoIds);

	/**
	 * Antes de borrar cargos por caminos que no son el DELETE del cargo (borrar un alumno, sacarlo de una convocatoria,
	 * borrar una convocatoria): sus cobros ACTIVOS que no van en una entrega pasan a ANULADO con el motivo dado y dejan
	 * de apuntar al cargo. Los que van en una entrega no se tocan (la FK ON DELETE SET NULL los desvincula). Sin cargar
	 * entidades; sube la versión como lo haría Hibernate.
	 */
	@Modifying
	@Query("UPDATE Cobro c SET c.estado = com.taemoi.project.entities.EstadoCobro.ANULADO, c.anuladoEn = :ahora, "
			+ "c.anuladoPorUsuarioId = :usuarioId, c.motivoAnulacion = :motivo, c.productoAlumnoId = NULL, "
			+ "c.version = c.version + 1 "
			+ "WHERE c.productoAlumnoId IN :ids AND c.estado = com.taemoi.project.entities.EstadoCobro.ACTIVO "
			+ "AND c.entregaCajaId IS NULL")
	int anularDeCargosBorrados(@Param("ids") Collection<Long> ids, @Param("usuarioId") Long usuarioId,
			@Param("ahora") LocalDateTime ahora, @Param("motivo") String motivo);

	/** Ids de los cobros ACTIVOS de esos cargos que ya van en una entrega de caja. */
	@Query("SELECT c.id FROM Cobro c WHERE c.productoAlumnoId IN :ids "
			+ "AND c.estado = com.taemoi.project.entities.EstadoCobro.ACTIVO AND c.entregaCajaId IS NOT NULL")
	List<Long> idsActivosEntregadosDeCargos(@Param("ids") Collection<Long> ids);

	// ------------------------------------------------------------------ pieza 6b-2: bloqueos (solo en transacción)

	/**
	 * Bloquea (SELECT ... FOR UPDATE) el cobro ACTIVO del cargo y lo devuelve. Si la sesión ya lo tenía cargado,
	 * devuelve esa instancia sin releerla: quien lo usa la relee ({@code CobroService#anular}).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT c FROM Cobro c WHERE c.productoAlumnoId = :cargoId "
			+ "AND c.estado = com.taemoi.project.entities.EstadoCobro.ACTIVO")
	Optional<Cobro> bloquearActivoDeCargo(@Param("cargoId") Long cargoId);

	/** Bloquea (FOR UPDATE) un cobro por id. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT c FROM Cobro c WHERE c.id = :id")
	Optional<Cobro> bloquearPorId(@Param("id") Long id);

	/** Bloquea (FOR UPDATE) esos cobros EN ORDEN DE ID (orden único de bloqueo de las entregas). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT c FROM Cobro c WHERE c.id IN :ids ORDER BY c.id")
	List<Cobro> bloquearPorIdsEnOrden(@Param("ids") Collection<Long> ids);

	/** Bloquea (FOR UPDATE) los cobros de una entrega, en orden de id. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT c FROM Cobro c WHERE c.entregaCajaId = :entregaId ORDER BY c.id")
	List<Cobro> bloquearDeEntregaEnOrden(@Param("entregaId") Long entregaId);

	// ------------------------------------------------------------------ pieza 6b-2: consultas de caja

	/** Si el cobro lo cobró ese usuario con ese origen (puerto de {@code @acceso}). */
	boolean existsByIdAndCobradoPorUsuarioIdAndOrigen(Long id, Long cobradoPorUsuarioId,
			com.taemoi.project.entities.OrigenCobro origen);

	/** Cobros del profesor pendientes de entrega: ACTIVOS, origen PROFESOR y sin entrega, del más antiguo al más nuevo. */
	@Query("SELECT c FROM Cobro c WHERE c.cobradoPorUsuarioId = :usuarioId "
			+ "AND c.origen = com.taemoi.project.entities.OrigenCobro.PROFESOR "
			+ "AND c.estado = com.taemoi.project.entities.EstadoCobro.ACTIVO AND c.entregaCajaId IS NULL "
			+ "ORDER BY c.fechaCobro, c.id")
	List<Cobro> pendientesDeEntregaDelProfesor(@Param("usuarioId") Long usuarioId);

	/** Cobros de la sede pendientes de entrega (ACTIVOS, origen PROFESOR, sin entrega), por cobrador y fecha. */
	@Query("SELECT c FROM Cobro c WHERE c.sedeId = :sedeId "
			+ "AND c.origen = com.taemoi.project.entities.OrigenCobro.PROFESOR "
			+ "AND c.estado = com.taemoi.project.entities.EstadoCobro.ACTIVO AND c.entregaCajaId IS NULL "
			+ "ORDER BY c.cobradoPorUsuarioId, c.fechaCobro, c.id")
	List<Cobro> pendientesDeEntregaEnSede(@Param("sedeId") Long sedeId);

	/** Σ de los cobros de la sede pendientes de entrega (ACTIVOS, origen PROFESOR, sin entrega). */
	@Query("SELECT COALESCE(SUM(c.importe), 0) FROM Cobro c WHERE c.sedeId = :sedeId "
			+ "AND c.origen = com.taemoi.project.entities.OrigenCobro.PROFESOR "
			+ "AND c.estado = com.taemoi.project.entities.EstadoCobro.ACTIVO AND c.entregaCajaId IS NULL")
	BigDecimal sumarPendienteDeEntregaEnSede(@Param("sedeId") Long sedeId);

	/**
	 * Σ del efectivo de la sede que ya está en su caja: cobros ACTIVOS en EFECTIVO que no están pendientes de entrega
	 * (origen distinto de PROFESOR, o ya en una entrega).
	 */
	@Query("SELECT COALESCE(SUM(c.importe), 0) FROM Cobro c WHERE c.sedeId = :sedeId "
			+ "AND c.estado = com.taemoi.project.entities.EstadoCobro.ACTIVO "
			+ "AND c.metodo = com.taemoi.project.entities.MetodoPago.EFECTIVO "
			+ "AND (c.origen <> com.taemoi.project.entities.OrigenCobro.PROFESOR OR c.entregaCajaId IS NOT NULL)")
	BigDecimal sumarEfectivoEnCajaDeSede(@Param("sedeId") Long sedeId);

	/** Cobros de esas entregas, en orden de entrega y de id (historial sin N+1). */
	@Query("SELECT c FROM Cobro c WHERE c.entregaCajaId IN :entregaIds ORDER BY c.entregaCajaId, c.id")
	List<Cobro> deEntregas(@Param("entregaIds") Collection<Long> entregaIds);
}
