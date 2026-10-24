package com.taemoi.project.repositories;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.taemoi.project.entities.Alumno;

/**
 * Pieza 8b-3l (P-3, decisión 285): los cargos de cada alumno indicado, para avisar antes del borrado definitivo.
 * Consulta nativa con {@code organizacion_id} explícito en cada tabla (no pasa por el filtro {@code @TenantId}), como
 * {@link PendienteVencidoRepository}: un id de otra escuela no casa con ningún alumno de esta y no sale. Cuenta los
 * cargos sin cancelar del alumno ({@link #CARGO_DEL_ALUMNO}); pendiente es {@link #CARGO_SIN_PAGAR} (lo que se cancela
 * al eliminar anonimizando) e importe es {@code precio}, como la tesorería. Un alumno sin cargos sale con ceros.
 */
public interface ResumenCargosAlumnoRepository extends Repository<Alumno, Long> {

	/**
	 * Cargo {@code pa} del alumno {@code a} en la escuela {@code :organizacion}: el suyo, el de su inscripción o el de
	 * su convocatoria.
	 */
	String CARGO_DEL_ALUMNO = " " + """
			pa.organizacion_id = :organizacion
			  AND (pa.alumno_id = a.id
			    OR pa.alumno_deporte_id IN (SELECT ad.id FROM alumno_deporte ad
			      WHERE ad.alumno_id = a.id AND ad.organizacion_id = :organizacion)
			    OR pa.id IN (SELECT ac.producto_alumno_id FROM alumno_convocatoria ac
			      WHERE ac.alumno_id = a.id AND ac.producto_alumno_id IS NOT NULL))
			""";

	/**
	 * Pieza 8b-3l bis (decisión 287): cargo {@code pa} sin pagar, la misma regla que cancelar a mano: sin cancelar, no
	 * marcado pagado y sin un cobro ACTIVO (un cobro ANULADO no cuenta). Es lo que avisa {@link #resumir} como pendiente
	 * y lo que se cancela al eliminar anonimizando ({@code ProductoAlumnoRepository#idsCargosSinPagarDelAlumno}).
	 */
	String CARGO_SIN_PAGAR = " " + """
			pa.cancelado_en IS NULL AND (pa.pagado = 0 OR pa.pagado IS NULL)
			  AND NOT EXISTS (SELECT 1 FROM cobro cs WHERE cs.producto_alumno_id = pa.id
			    AND cs.organizacion_id = :organizacion AND cs.estado <> 'ANULADO')
			""";

	interface ResumenCargos {
		Long getAlumnoId();

		Long getCargos();

		Long getPagados();

		Long getPendientes();

		BigDecimal getImportePagado();

		BigDecimal getImportePendiente();
	}

	@Query(value = """
			SELECT a.id AS alumnoId,
			  COUNT(pa.id) AS cargos,
			  COALESCE(SUM(CASE WHEN pa.pagado = 1 THEN 1 ELSE 0 END), 0) AS pagados,
			  COALESCE(SUM(CASE WHEN pa.id IS NOT NULL AND (""" + CARGO_SIN_PAGAR + """
			    ) THEN 1 ELSE 0 END), 0) AS pendientes,
			  CAST(COALESCE(SUM(CASE WHEN pa.pagado = 1 THEN COALESCE(pa.precio, 0) ELSE 0 END), 0) AS DECIMAL(12, 2))
			    AS importePagado,
			  CAST(COALESCE(SUM(CASE WHEN pa.id IS NOT NULL AND (""" + CARGO_SIN_PAGAR + """
			    ) THEN COALESCE(pa.precio, 0) ELSE 0 END), 0) AS DECIMAL(12, 2)) AS importePendiente
			FROM alumno a
			LEFT JOIN producto_alumno pa ON pa.cancelado_en IS NULL AND """ + CARGO_DEL_ALUMNO + """
			WHERE a.organizacion_id = :organizacion AND a.id IN (:alumnos)
			GROUP BY a.id
			""", nativeQuery = true)
	List<ResumenCargos> resumir(@Param("organizacion") Long organizacion, @Param("alumnos") Collection<Long> alumnos);

	interface PagosAlumno {
		Long getAlumnoId();

		Long getPagos();
	}

	/**
	 * Pieza 8b-3l bis (decisión 287): cuántos pagos tiene cada alumno indicado de la escuela. «Con pagos» es tener al
	 * menos uno. Cuenta (1) cada cargo suyo (el suyo, el de su inscripción o el de su convocatoria; cancelado o no)
	 * marcado pagado o con algún cobro, en cualquier estado (activo, anulado, entregado en caja o devuelto); y (2) cada
	 * cobro con su {@code alumno_id} (la instantánea del cobro) que no cuelga de uno de esos cargos: el de un cargo ya
	 * borrado, por ejemplo. Un pago no se cuenta dos veces: un cargo pagado con su cobro es un pago.
	 */
	@Query(value = """
			SELECT a.id AS alumnoId,
			  (SELECT COUNT(*) FROM producto_alumno pa
			    WHERE pa.organizacion_id = :organizacion
			      AND (pa.alumno_id = a.id
			        OR pa.alumno_deporte_id IN (SELECT ad.id FROM alumno_deporte ad
			          WHERE ad.alumno_id = a.id AND ad.organizacion_id = :organizacion)
			        OR pa.id IN (SELECT ac.producto_alumno_id FROM alumno_convocatoria ac
			          WHERE ac.alumno_id = a.id AND ac.producto_alumno_id IS NOT NULL))
			      AND (pa.pagado = 1 OR EXISTS (SELECT 1 FROM cobro c
			        WHERE c.producto_alumno_id = pa.id AND c.organizacion_id = :organizacion)))
			  + (SELECT COUNT(*) FROM cobro c
			    WHERE c.organizacion_id = :organizacion AND c.alumno_id = a.id
			      AND NOT EXISTS (SELECT 1 FROM producto_alumno pa
			        WHERE pa.id = c.producto_alumno_id AND pa.organizacion_id = :organizacion
			          AND (pa.alumno_id = a.id
			            OR pa.alumno_deporte_id IN (SELECT ad.id FROM alumno_deporte ad
			              WHERE ad.alumno_id = a.id AND ad.organizacion_id = :organizacion)
			            OR pa.id IN (SELECT ac.producto_alumno_id FROM alumno_convocatoria ac
			              WHERE ac.alumno_id = a.id AND ac.producto_alumno_id IS NOT NULL)))) AS pagos
			FROM alumno a
			WHERE a.organizacion_id = :organizacion AND a.id IN (:alumnos)
			""", nativeQuery = true)
	List<PagosAlumno> contarPagos(@Param("organizacion") Long organizacion, @Param("alumnos") Collection<Long> alumnos);
}
