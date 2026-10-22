package com.taemoi.project.repositories;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.taemoi.project.entities.ProductoAlumno;

/**
 * Pieza 8b-2 (decisiones 242 y 267): los cargos candidatos a «pendiente vencido» de una escuela. Consulta nativa con
 * {@code organizacion_id} explícito en cada tabla (no pasa por el filtro {@code @TenantId}). Mismo universo que la
 * tesorería de hoy ({@code ProductoAlumnoRepository#contarMovimientosTesoreria} con {@code pagado = false} y
 * {@code soloActivos = true}): no pagado ({@code FALSE} o {@code NULL}), sin cancelar, de un alumno activo (el del cargo o
 * el de su inscripción) y en las sedes del filtro. Trae de más (todo concepto con «MENSUALIDAD», «EXAMEN» o «RECOMPENSA»
 * y todo producto de esos tipos); la clasificación fina y la regla del plazo las hace {@code PendienteVencidoService}.
 */
public interface PendienteVencidoRepository extends Repository<ProductoAlumno, Long> {

	interface CargoCandidato {
		Long getId();

		String getConcepto();

		BigDecimal getImporte();

		LocalDate getFechaAsignacion();

		LocalDate getFechaPago();

		LocalDate getFechaConvocatoria();

		Long getSedeId();

		String getSedeNombre();

		Long getAlumnoId();

		String getAlumnoNombre();

		String getAlumnoApellidos();

		String getProductoTipo();

		String getProductoCodigo();
	}

	/** Consulta común: la de {@link #candidatos} y, con el filtro por alumno, la de {@link #candidatosDeAlumnos}. */
	String CANDIDATOS = """
			SELECT pa.id AS id, pa.concepto AS concepto,
			  CAST(COALESCE(pa.precio, 0) AS DECIMAL(12, 2)) AS importe,
			  DATE(pa.fecha_asignacion) AS fechaAsignacion, DATE(pa.fecha_pago) AS fechaPago,
			  (SELECT MIN(c.fecha_convocatoria) FROM alumno_convocatoria ac
			     JOIN convocatoria c ON c.id = ac.convocatoria_id AND c.organizacion_id = :organizacion
			    WHERE ac.producto_alumno_id = pa.id AND ac.organizacion_id = :organizacion) AS fechaConvocatoria,
			  pa.sede_id AS sedeId, s.nombre AS sedeNombre,
			  COALESCE(a.id, ada.id) AS alumnoId,
			  COALESCE(a.nombre, ada.nombre) AS alumnoNombre,
			  COALESCE(a.apellidos, ada.apellidos) AS alumnoApellidos,
			  p.tipo AS productoTipo, p.codigo AS productoCodigo
			FROM producto_alumno pa
			LEFT JOIN alumno a ON a.id = pa.alumno_id AND a.organizacion_id = :organizacion
			LEFT JOIN alumno_deporte ad ON ad.id = pa.alumno_deporte_id AND ad.organizacion_id = :organizacion
			LEFT JOIN alumno ada ON ada.id = ad.alumno_id AND ada.organizacion_id = :organizacion
			LEFT JOIN producto p ON p.id = pa.producto_id AND p.organizacion_id = :organizacion
			LEFT JOIN sede s ON s.id = pa.sede_id AND s.organizacion_id = :organizacion
			WHERE pa.organizacion_id = :organizacion
			  AND (pa.pagado = 0 OR pa.pagado IS NULL)
			  AND pa.cancelado_en IS NULL
			  AND (COALESCE(a.activo, 0) = 1 OR COALESCE(ada.activo, 0) = 1)
			  AND (:todasLasSedes = TRUE OR pa.sede_id IN (:sedes))
			  AND (UPPER(pa.concepto) LIKE '%MENSUALIDAD%' OR UPPER(pa.concepto) LIKE '%EXAMEN%'
			    OR UPPER(pa.concepto) LIKE '%RECOMPENSA%' OR p.tipo IN ('MENSUALIDAD', 'EXAMEN', 'RECOMPENSA'))
			""";

	@Query(value = CANDIDATOS, nativeQuery = true)
	List<CargoCandidato> candidatos(
			@Param("organizacion") Long organizacion,
			@Param("todasLasSedes") boolean todasLasSedes,
			@Param("sedes") Collection<Long> sedes);

	/**
	 * Pieza 8b-3a: los mismos candidatos, solo de los alumnos indicados (el del cargo o el de su inscripción, como
	 * {@code alumnoId} en {@link #candidatos}). Los ids de otra escuela no casan con ningún alumno de esta.
	 */
	@Query(value = CANDIDATOS + "  AND COALESCE(a.id, ada.id) IN (:alumnos)", nativeQuery = true)
	List<CargoCandidato> candidatosDeAlumnos(
			@Param("organizacion") Long organizacion,
			@Param("todasLasSedes") boolean todasLasSedes,
			@Param("sedes") Collection<Long> sedes,
			@Param("alumnos") Collection<Long> alumnos);
}
