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
 * cargos sin cancelar del alumno (el del cargo o el de su inscripción); pendiente es pagado {@code FALSE} o {@code NULL}
 * e importe es {@code precio}, como la tesorería. Un alumno sin cargos sale con ceros.
 */
public interface ResumenCargosAlumnoRepository extends Repository<Alumno, Long> {

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
			  COALESCE(SUM(CASE WHEN pa.id IS NOT NULL AND (pa.pagado = 0 OR pa.pagado IS NULL) THEN 1 ELSE 0 END), 0)
			    AS pendientes,
			  CAST(COALESCE(SUM(CASE WHEN pa.pagado = 1 THEN COALESCE(pa.precio, 0) ELSE 0 END), 0) AS DECIMAL(12, 2))
			    AS importePagado,
			  CAST(COALESCE(SUM(CASE WHEN pa.id IS NOT NULL AND (pa.pagado = 0 OR pa.pagado IS NULL)
			    THEN COALESCE(pa.precio, 0) ELSE 0 END), 0) AS DECIMAL(12, 2)) AS importePendiente
			FROM alumno a
			LEFT JOIN producto_alumno pa ON pa.organizacion_id = :organizacion AND pa.cancelado_en IS NULL
			  AND (pa.alumno_id = a.id OR pa.alumno_deporte_id IN (
			    SELECT ad.id FROM alumno_deporte ad WHERE ad.alumno_id = a.id AND ad.organizacion_id = :organizacion))
			WHERE a.organizacion_id = :organizacion AND a.id IN (:alumnos)
			GROUP BY a.id
			""", nativeQuery = true)
	List<ResumenCargos> resumir(@Param("organizacion") Long organizacion, @Param("alumnos") Collection<Long> alumnos);
}
