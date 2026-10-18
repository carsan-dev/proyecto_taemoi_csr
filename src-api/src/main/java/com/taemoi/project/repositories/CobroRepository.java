package com.taemoi.project.repositories;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.taemoi.project.entities.Cobro;
import com.taemoi.project.entities.EstadoCobro;

/** Cobros de la escuela activa (pieza 6b-1); el filtro por organización lo pone {@code @TenantId}. */
public interface CobroRepository extends JpaRepository<Cobro, Long> {

	/** El cobro en ese estado del cargo; con {@link EstadoCobro#ACTIVO} hay como mucho uno (clave única de V31_5). */
	Optional<Cobro> findFirstByProductoAlumnoIdAndEstado(Long productoAlumnoId, EstadoCobro estado);

	/** Si algún cobro del cargo va ya en una entrega de caja. */
	boolean existsByProductoAlumnoIdAndEntregaCajaIdIsNotNull(Long productoAlumnoId);

	List<Cobro> findByProductoAlumnoIdOrderByIdAsc(Long productoAlumnoId);

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
}
