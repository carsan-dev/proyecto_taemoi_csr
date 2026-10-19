package com.taemoi.project.repositories;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.taemoi.project.entities.EstadoSolicitud;
import com.taemoi.project.entities.Solicitud;

import jakarta.persistence.LockModeType;

/**
 * Solicitudes de la escuela activa (pieza 6c-2); el filtro por organización lo pone {@code @TenantId}. Los listados y
 * el contador de la bandeja van por {@link JpaSpecificationExecutor} ({@code SolicitudService}).
 */
public interface SolicitudRepository extends JpaRepository<Solicitud, Long>, JpaSpecificationExecutor<Solicitud> {

	/**
	 * Bloquea (SELECT ... FOR UPDATE) la solicitud. Solo en transacción. Si la sesión ya la tenía cargada devuelve esa
	 * instancia sin releerla: quien la usa la relee ({@code SolicitudService#bloquear}), como
	 * {@code CobroService#bloquearCobro}.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT s FROM Solicitud s WHERE s.id = :id")
	Optional<Solicitud> bloquearPorId(@Param("id") Long id);

	/** Si hay una solicitud en ese estado sobre el cobro (la clave única de V31_8 es la última red). */
	boolean existsByCobroIdAndEstado(Long cobroId, EstadoSolicitud estado);

	boolean existsByEntregaCajaIdAndEstado(Long entregaCajaId, EstadoSolicitud estado);

	boolean existsByMovimientoCajaIdAndEstado(Long movimientoCajaId, EstadoSolicitud estado);

	/** Decisión 191: una pendiente de revertir por cargo, haya cobro o no. */
	@Query("SELECT COUNT(s) > 0 FROM Solicitud s WHERE s.productoAlumnoId = :cargoId "
			+ "AND s.estado = com.taemoi.project.entities.EstadoSolicitud.PENDIENTE "
			+ "AND s.tipo = com.taemoi.project.entities.TipoSolicitud.REVERTIR_COBRO")
	boolean existeRevertirPendienteDeCargo(@Param("cargoId") Long cargoId);

	/** Puerto de {@code @acceso.solicitudEnSede}: la solicitud (de la escuela activa) es de una de esas sedes. */
	boolean existsByIdAndSedeIdIn(Long id, Collection<Long> sedes);

	/**
	 * Pieza 6c-5: el cargo es el de un encargo en curso (PEDIDO, ENCARGADO o RECIBIDO). Lectura sin bloqueo: quien la usa
	 * ya tiene el cargo bloqueado y no bloquea una solicitud después de un cargo (orden solicitud → cargo).
	 */
	@Query("SELECT COUNT(s) > 0 FROM Solicitud s WHERE s.productoAlumnoId = :cargoId "
			+ "AND s.tipo = com.taemoi.project.entities.TipoSolicitud.ENCARGO "
			+ "AND s.estado IN (com.taemoi.project.entities.EstadoSolicitud.PEDIDO, "
			+ "com.taemoi.project.entities.EstadoSolicitud.ENCARGADO, "
			+ "com.taemoi.project.entities.EstadoSolicitud.RECIBIDO)")
	boolean existeEncargoEnCursoDeCargo(@Param("cargoId") Long cargoId);

	/**
	 * Pieza 6c-5: el cargo es el de un encargo caído (RETIRADA o RECHAZADO): su cargo cancelado no se reactiva. Lectura
	 * sin bloqueo, como {@link #existeEncargoEnCursoDeCargo}.
	 */
	@Query("SELECT COUNT(s) > 0 FROM Solicitud s WHERE s.productoAlumnoId = :cargoId "
			+ "AND s.tipo = com.taemoi.project.entities.TipoSolicitud.ENCARGO "
			+ "AND s.estado IN (com.taemoi.project.entities.EstadoSolicitud.RETIRADA, "
			+ "com.taemoi.project.entities.EstadoSolicitud.RECHAZADO)")
	boolean existeEncargoCaidoDeCargo(@Param("cargoId") Long cargoId);
}
