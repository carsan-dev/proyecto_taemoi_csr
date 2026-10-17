package com.taemoi.project.repositories;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.taemoi.project.entities.ProductoAlumno;

public interface ProductoAlumnoRepository extends JpaRepository<ProductoAlumno, Long> {
	interface TesoreriaPeriodoBaseProjection {
		Long getId();
		String getConcepto();
		Date getFechaAsignacion();
		Date getFechaPago();
		Boolean getPagado();
		Double getPrecio();
		Long getAlumnoId();
		/** Tipo del producto cobrado (NULL si el producto se borró). */
		com.taemoi.project.entities.TipoProducto getProductoTipo();
		/** Código del producto cobrado (los heredados de Taemoi se clasifican por concepto). */
		String getProductoCodigo();
		/** Sede del cargo (entrega 6-3, D6-9); NULL si no tiene. */
		Long getSedeId();
	}

	/** Totales de un resumen de tesorería por sede del cargo (entrega 6-3, D6-12). */
	interface TesoreriaResumenPorSedeProjection {
		Long getSedeId();
		Long getTotalMovimientos();
		Long getTotalPagados();
		Long getTotalPendientes();
		Double getImporteTotal();
		Double getImportePagado();
		Double getImportePendiente();
	}

	/** Alumnos con cargos pendientes por sede del cargo (entrega 6-3). */
	interface TesoreriaAlumnosPendientesPorSedeProjection {
		Long getSedeId();
		Long getAlumnosConPendientes();
	}

	List<ProductoAlumno> findByAlumnoId(Long alumnoId);

	/** Entrega 6-3: el cargo (de la escuela activa) es de una de esas sedes ({@code @acceso.productoAlumnoEnSede}). */
	boolean existsByIdAndSedeIdIn(Long id, java.util.Collection<Long> sedeIds);

	Optional<ProductoAlumno> findByAlumnoIdAndProductoId(Long id, Long id2);

	boolean existsByConcepto(String concepto);

	@Query("SELECT pa FROM ProductoAlumno pa JOIN FETCH pa.alumno a WHERE pa.pagado = false ORDER BY a.nombre, a.apellidos, pa.fechaAsignacion")
	List<ProductoAlumno> findAllUnpaidWithAlumno();

	@Query("SELECT pa FROM ProductoAlumno pa LEFT JOIN FETCH pa.alumnoDeporte ad LEFT JOIN FETCH pa.alumno a " +
	       "WHERE pa.concepto LIKE 'MENSUALIDAD%' ORDER BY a.nombre, a.apellidos, pa.fechaAsignacion DESC")
	List<ProductoAlumno> findAllMensualidadesWithAlumno();

	@Query("SELECT pa FROM ProductoAlumno pa LEFT JOIN FETCH pa.alumnoDeporte ad LEFT JOIN FETCH pa.alumno a " +
	       "WHERE (pa.concepto LIKE 'MENSUALIDAD%' OR pa.concepto LIKE 'TARIFA COMPETIDOR%') " +
	       "ORDER BY a.nombre, a.apellidos, pa.fechaAsignacion DESC")
	List<ProductoAlumno> findAllMensualidadesYTarifasCompetidorWithAlumno();

	@Query("SELECT pa FROM ProductoAlumno pa " +
			"LEFT JOIN FETCH pa.alumnoDeporte ad " +
			"LEFT JOIN FETCH ad.alumno adAlumno " +
			"LEFT JOIN FETCH pa.alumno a " +
			"WHERE UPPER(pa.concepto) LIKE '%RESERVA DE PLAZA%' " +
			"AND pa.concepto LIKE CONCAT('%', :temporada, '%') " +
			"AND (:soloActivos = false OR (" +
			"COALESCE(COALESCE(adAlumno.activo, a.activo), false) = true " +
			"AND (ad IS NULL OR COALESCE(ad.activo, false) = true))) " +
			"ORDER BY COALESCE(adAlumno.nombre, a.nombre), COALESCE(adAlumno.apellidos, a.apellidos), pa.concepto")
	List<ProductoAlumno> findReservasPlazaByTemporada(
			@Param("temporada") String temporada,
			@Param("soloActivos") boolean soloActivos);

	@Query("SELECT pa.concepto FROM ProductoAlumno pa " +
			"WHERE pa.concepto IS NOT NULL " +
			"AND UPPER(pa.concepto) LIKE '%RESERVA DE PLAZA%'")
	List<String> findConceptosReservaPlaza();

	@Query("SELECT pa FROM ProductoAlumno pa JOIN FETCH pa.alumnoDeporte ad JOIN ad.disciplina adDisc JOIN FETCH ad.alumno a " +
	       "WHERE pa.concepto LIKE 'MENSUALIDAD%' AND adDisc.codigo = :deporte " +
	       "ORDER BY a.nombre, a.apellidos, pa.fechaAsignacion DESC")
	List<ProductoAlumno> findMensualidadesByDeporteWithAlumno(@Param("deporte") String deporte);

	@Query("SELECT pa FROM ProductoAlumno pa JOIN FETCH pa.alumnoDeporte ad JOIN ad.disciplina adDisc JOIN FETCH ad.alumno a " +
	       "WHERE (pa.concepto LIKE 'MENSUALIDAD%' OR pa.concepto LIKE 'TARIFA COMPETIDOR%') AND adDisc.codigo = :deporte " +
	       "ORDER BY a.nombre, a.apellidos, pa.fechaAsignacion DESC")
	List<ProductoAlumno> findMensualidadesYTarifasCompetidorByDeporteWithAlumno(@Param("deporte") String deporte);

	@Query("SELECT pa FROM ProductoAlumno pa LEFT JOIN FETCH pa.alumnoDeporte ad LEFT JOIN ad.disciplina adDisc LEFT JOIN FETCH pa.alumno a " +
	       "WHERE (pa.concepto LIKE 'MENSUALIDAD%' OR pa.concepto LIKE 'TARIFA COMPETIDOR%') " +
	       "AND (adDisc.codigo = :deporte OR pa.concepto LIKE CONCAT('%', :deporteNombre, '%')) " +
	       "ORDER BY a.nombre, a.apellidos, pa.fechaAsignacion DESC")
	List<ProductoAlumno> findMensualidadesYTarifasCompetidorByDeporteOrConcepto(
			@Param("deporte") String deporte,
			@Param("deporteNombre") String deporteNombre);

	@Query("SELECT pa FROM ProductoAlumno pa JOIN FETCH pa.alumnoDeporte ad JOIN ad.disciplina adDisc JOIN FETCH ad.alumno a " +
	       "WHERE pa.concepto LIKE CONCAT(:conceptoBase, '%') AND (adDisc.codigo = 'TAEKWONDO' " +
	       "OR adDisc.codigo = 'KICKBOXING') ORDER BY a.nombre, a.apellidos")
	List<ProductoAlumno> findMensualidadByConceptoAndDeportes(String conceptoBase);

	@Query("SELECT pa FROM ProductoAlumno pa JOIN FETCH pa.alumnoDeporte ad JOIN ad.disciplina adDisc JOIN FETCH ad.alumno a " +
	       "WHERE (pa.concepto LIKE CONCAT(:conceptoBase, '%') OR pa.concepto LIKE CONCAT('TARIFA COMPETIDOR%', :mesAno, '%')) " +
	       "AND (adDisc.codigo = 'TAEKWONDO' OR adDisc.codigo = 'KICKBOXING') " +
	       "ORDER BY a.nombre, a.apellidos, pa.concepto")
	List<ProductoAlumno> findMensualidadesYTarifasCompetidorByMes(String conceptoBase, String mesAno);

	@Query(
			value = "SELECT pa FROM ProductoAlumno pa " +
					"LEFT JOIN FETCH pa.alumnoDeporte ad " +
			"LEFT JOIN ad.disciplina adDisc " +
					"LEFT JOIN FETCH ad.alumno adAlumno " +
					"LEFT JOIN FETCH pa.alumno a " +
					"LEFT JOIN FETCH pa.producto p " +
					"WHERE (:pagado IS NULL OR " +
					"(:pagado = true AND pa.pagado = true) OR " +
					"(:pagado = false AND (pa.pagado = false OR pa.pagado IS NULL))) " +
					"AND (:deporte IS NULL OR adDisc.codigo = :deporte OR UPPER(pa.concepto) LIKE CONCAT('%', :deporteNombre, '%')) " +
					"AND (:ano IS NULL OR " +
					"FUNCTION('YEAR', COALESCE(pa.fechaAsignacion, pa.fechaPago)) = :ano OR " +
					"(:anoTexto IS NOT NULL AND (" +
					"((UPPER(pa.concepto) LIKE 'MENSUALIDAD%' OR UPPER(pa.concepto) LIKE 'TARIFA COMPETIDOR%' OR " +
					"(p.tipo IN (com.taemoi.project.entities.TipoProducto.MENSUALIDAD, com.taemoi.project.entities.TipoProducto.COMPETIDOR) AND p.codigo IS NOT NULL AND (:legado = false OR p.codigo NOT IN ('MENSUALIDAD', 'COMPETIDOR_TAEKWONDO', 'COMPETIDOR_KICKBOXING')))) " +
					"AND UPPER(pa.concepto) LIKE CONCAT('%', :anoTexto, '%')) OR " +
					"(COALESCE(pa.fechaAsignacion, pa.fechaPago) IS NULL AND UPPER(pa.concepto) LIKE CONCAT('%', :anoTexto, '%'))))) " +
					"AND (:mes IS NULL OR " +
					"FUNCTION('MONTH', COALESCE(pa.fechaAsignacion, pa.fechaPago)) = :mes OR " +
					"(:mesNombre IS NOT NULL AND (" +
					"((UPPER(pa.concepto) LIKE 'MENSUALIDAD%' OR UPPER(pa.concepto) LIKE 'TARIFA COMPETIDOR%' OR " +
					"(p.tipo IN (com.taemoi.project.entities.TipoProducto.MENSUALIDAD, com.taemoi.project.entities.TipoProducto.COMPETIDOR) AND p.codigo IS NOT NULL AND (:legado = false OR p.codigo NOT IN ('MENSUALIDAD', 'COMPETIDOR_TAEKWONDO', 'COMPETIDOR_KICKBOXING')))) " +
					"AND UPPER(pa.concepto) LIKE CONCAT('%', :mesNombre, '%')) OR " +
					"(COALESCE(pa.fechaAsignacion, pa.fechaPago) IS NULL AND UPPER(pa.concepto) LIKE CONCAT('%', :mesNombre, '%'))))) " +
					"AND (:texto IS NULL OR " +
					"UPPER(COALESCE(pa.concepto, '')) LIKE :texto OR " +
					"UPPER(COALESCE(pa.notas, '')) LIKE :texto OR " +
					"UPPER(COALESCE(a.nombre, '')) LIKE :texto OR " +
					"UPPER(COALESCE(a.apellidos, '')) LIKE :texto OR " +
					"UPPER(CONCAT(COALESCE(a.nombre, ''), ' ', COALESCE(a.apellidos, ''))) LIKE :texto OR " +
					"UPPER(COALESCE(adAlumno.nombre, '')) LIKE :texto OR " +
					"UPPER(COALESCE(adAlumno.apellidos, '')) LIKE :texto OR " +
					"UPPER(CONCAT(COALESCE(adAlumno.nombre, ''), ' ', COALESCE(adAlumno.apellidos, ''))) LIKE :texto) " +
					"AND (:soloActivos = false OR COALESCE(a.activo, false) = true OR COALESCE(adAlumno.activo, false) = true) " +
					"AND (:todasLasSedes = true OR pa.sedeId IN :sedes) " +
					"ORDER BY COALESCE(pa.fechaAsignacion, pa.fechaPago) DESC, pa.id DESC",
			countQuery = "SELECT COUNT(pa) FROM ProductoAlumno pa " +
					"LEFT JOIN pa.alumnoDeporte ad " +
			"LEFT JOIN ad.disciplina adDisc " +
					"LEFT JOIN ad.alumno adAlumno " +
					"LEFT JOIN pa.alumno a " +
					"LEFT JOIN pa.producto p " +
					"WHERE (:pagado IS NULL OR " +
					"(:pagado = true AND pa.pagado = true) OR " +
					"(:pagado = false AND (pa.pagado = false OR pa.pagado IS NULL))) " +
					"AND (:deporte IS NULL OR adDisc.codigo = :deporte OR UPPER(pa.concepto) LIKE CONCAT('%', :deporteNombre, '%')) " +
					"AND (:ano IS NULL OR " +
					"FUNCTION('YEAR', COALESCE(pa.fechaAsignacion, pa.fechaPago)) = :ano OR " +
					"(:anoTexto IS NOT NULL AND (" +
					"((UPPER(pa.concepto) LIKE 'MENSUALIDAD%' OR UPPER(pa.concepto) LIKE 'TARIFA COMPETIDOR%' OR " +
					"(p.tipo IN (com.taemoi.project.entities.TipoProducto.MENSUALIDAD, com.taemoi.project.entities.TipoProducto.COMPETIDOR) AND p.codigo IS NOT NULL AND (:legado = false OR p.codigo NOT IN ('MENSUALIDAD', 'COMPETIDOR_TAEKWONDO', 'COMPETIDOR_KICKBOXING')))) " +
					"AND UPPER(pa.concepto) LIKE CONCAT('%', :anoTexto, '%')) OR " +
					"(COALESCE(pa.fechaAsignacion, pa.fechaPago) IS NULL AND UPPER(pa.concepto) LIKE CONCAT('%', :anoTexto, '%'))))) " +
					"AND (:mes IS NULL OR " +
					"FUNCTION('MONTH', COALESCE(pa.fechaAsignacion, pa.fechaPago)) = :mes OR " +
					"(:mesNombre IS NOT NULL AND (" +
					"((UPPER(pa.concepto) LIKE 'MENSUALIDAD%' OR UPPER(pa.concepto) LIKE 'TARIFA COMPETIDOR%' OR " +
					"(p.tipo IN (com.taemoi.project.entities.TipoProducto.MENSUALIDAD, com.taemoi.project.entities.TipoProducto.COMPETIDOR) AND p.codigo IS NOT NULL AND (:legado = false OR p.codigo NOT IN ('MENSUALIDAD', 'COMPETIDOR_TAEKWONDO', 'COMPETIDOR_KICKBOXING')))) " +
					"AND UPPER(pa.concepto) LIKE CONCAT('%', :mesNombre, '%')) OR " +
					"(COALESCE(pa.fechaAsignacion, pa.fechaPago) IS NULL AND UPPER(pa.concepto) LIKE CONCAT('%', :mesNombre, '%'))))) " +
					"AND (:texto IS NULL OR " +
					"UPPER(COALESCE(pa.concepto, '')) LIKE :texto OR " +
					"UPPER(COALESCE(pa.notas, '')) LIKE :texto OR " +
					"UPPER(COALESCE(a.nombre, '')) LIKE :texto OR " +
					"UPPER(COALESCE(a.apellidos, '')) LIKE :texto OR " +
					"UPPER(CONCAT(COALESCE(a.nombre, ''), ' ', COALESCE(a.apellidos, ''))) LIKE :texto OR " +
					"UPPER(COALESCE(adAlumno.nombre, '')) LIKE :texto OR " +
					"UPPER(COALESCE(adAlumno.apellidos, '')) LIKE :texto OR " +
					"UPPER(CONCAT(COALESCE(adAlumno.nombre, ''), ' ', COALESCE(adAlumno.apellidos, ''))) LIKE :texto) " +
					"AND (:soloActivos = false OR COALESCE(a.activo, false) = true OR COALESCE(adAlumno.activo, false) = true) " +
					"AND (:todasLasSedes = true OR pa.sedeId IN :sedes)")
	Page<ProductoAlumno> findMovimientosTesoreriaPaginados(
			@Param("deporte") String deporte,
			@Param("deporteNombre") String deporteNombre,
			@Param("pagado") Boolean pagado,
			@Param("texto") String texto,
			@Param("soloActivos") Boolean soloActivos,
			@Param("ano") Integer ano,
			@Param("anoTexto") String anoTexto,
			@Param("mes") Integer mes,
			@Param("mesNombre") String mesNombre,
			Pageable pageable,
			@Param("legado") Boolean legado,
			@Param("todasLasSedes") Boolean todasLasSedes,
			@Param("sedes") Collection<Long> sedes);

	@Query("SELECT pa FROM ProductoAlumno pa " +
			"LEFT JOIN FETCH pa.alumnoDeporte ad " +
			"LEFT JOIN ad.disciplina adDisc " +
			"LEFT JOIN FETCH ad.alumno adAlumno " +
			"LEFT JOIN FETCH pa.alumno a " +
			"LEFT JOIN FETCH pa.producto p " +
			"WHERE (:pagado IS NULL OR " +
			"(:pagado = true AND pa.pagado = true) OR " +
			"(:pagado = false AND (pa.pagado = false OR pa.pagado IS NULL))) " +
			"AND (:deporte IS NULL OR adDisc.codigo = :deporte OR UPPER(pa.concepto) LIKE CONCAT('%', :deporteNombre, '%')) " +
			"AND (:ano IS NULL OR " +
			"FUNCTION('YEAR', COALESCE(pa.fechaAsignacion, pa.fechaPago)) = :ano OR " +
			"(:anoTexto IS NOT NULL AND (" +
			"((UPPER(pa.concepto) LIKE 'MENSUALIDAD%' OR UPPER(pa.concepto) LIKE 'TARIFA COMPETIDOR%' OR " +
			"(p.tipo IN (com.taemoi.project.entities.TipoProducto.MENSUALIDAD, com.taemoi.project.entities.TipoProducto.COMPETIDOR) AND p.codigo IS NOT NULL AND (:legado = false OR p.codigo NOT IN ('MENSUALIDAD', 'COMPETIDOR_TAEKWONDO', 'COMPETIDOR_KICKBOXING')))) " +
			"AND UPPER(pa.concepto) LIKE CONCAT('%', :anoTexto, '%')) OR " +
			"(COALESCE(pa.fechaAsignacion, pa.fechaPago) IS NULL AND UPPER(pa.concepto) LIKE CONCAT('%', :anoTexto, '%'))))) " +
			"AND (:mes IS NULL OR " +
			"FUNCTION('MONTH', COALESCE(pa.fechaAsignacion, pa.fechaPago)) = :mes OR " +
			"(:mesNombre IS NOT NULL AND (" +
			"((UPPER(pa.concepto) LIKE 'MENSUALIDAD%' OR UPPER(pa.concepto) LIKE 'TARIFA COMPETIDOR%' OR " +
			"(p.tipo IN (com.taemoi.project.entities.TipoProducto.MENSUALIDAD, com.taemoi.project.entities.TipoProducto.COMPETIDOR) AND p.codigo IS NOT NULL AND (:legado = false OR p.codigo NOT IN ('MENSUALIDAD', 'COMPETIDOR_TAEKWONDO', 'COMPETIDOR_KICKBOXING')))) " +
			"AND UPPER(pa.concepto) LIKE CONCAT('%', :mesNombre, '%')) OR " +
			"(COALESCE(pa.fechaAsignacion, pa.fechaPago) IS NULL AND UPPER(pa.concepto) LIKE CONCAT('%', :mesNombre, '%'))))) " +
			"AND (:texto IS NULL OR " +
			"UPPER(COALESCE(pa.concepto, '')) LIKE :texto OR " +
			"UPPER(COALESCE(pa.notas, '')) LIKE :texto OR " +
			"UPPER(COALESCE(a.nombre, '')) LIKE :texto OR " +
			"UPPER(COALESCE(a.apellidos, '')) LIKE :texto OR " +
			"UPPER(CONCAT(COALESCE(a.nombre, ''), ' ', COALESCE(a.apellidos, ''))) LIKE :texto OR " +
			"UPPER(COALESCE(adAlumno.nombre, '')) LIKE :texto OR " +
			"UPPER(COALESCE(adAlumno.apellidos, '')) LIKE :texto OR " +
			"UPPER(CONCAT(COALESCE(adAlumno.nombre, ''), ' ', COALESCE(adAlumno.apellidos, ''))) LIKE :texto) " +
			"AND (:soloActivos = false OR COALESCE(a.activo, false) = true OR COALESCE(adAlumno.activo, false) = true) " +
			"AND (:todasLasSedes = true OR pa.sedeId IN :sedes) " +
			"ORDER BY COALESCE(pa.fechaAsignacion, pa.fechaPago) DESC, pa.id DESC")
	List<ProductoAlumno> findMovimientosTesoreriaFiltrados(
			@Param("deporte") String deporte,
			@Param("deporteNombre") String deporteNombre,
			@Param("pagado") Boolean pagado,
			@Param("texto") String texto,
			@Param("soloActivos") Boolean soloActivos,
			@Param("ano") Integer ano,
			@Param("anoTexto") String anoTexto,
			@Param("mes") Integer mes,
			@Param("mesNombre") String mesNombre,
			@Param("legado") Boolean legado,
			@Param("todasLasSedes") Boolean todasLasSedes,
			@Param("sedes") Collection<Long> sedes);

	@Query("SELECT pa.id AS id, pa.concepto AS concepto, pa.fechaAsignacion AS fechaAsignacion, " +
			"pa.fechaPago AS fechaPago, pa.pagado AS pagado, pa.precio AS precio, p.tipo AS productoTipo, p.codigo AS productoCodigo, " +
			"COALESCE(a.id, adAlumno.id) AS alumnoId, pa.sedeId AS sedeId " +
			"FROM ProductoAlumno pa " +
			"LEFT JOIN pa.alumnoDeporte ad " +
			"LEFT JOIN ad.disciplina adDisc " +
			"LEFT JOIN ad.alumno adAlumno " +
			"LEFT JOIN pa.alumno a " +
			"LEFT JOIN pa.producto p " +
			"WHERE (:pagado IS NULL OR " +
			"(:pagado = true AND pa.pagado = true) OR " +
			"(:pagado = false AND (pa.pagado = false OR pa.pagado IS NULL))) " +
			"AND (:deporte IS NULL OR adDisc.codigo = :deporte OR UPPER(pa.concepto) LIKE CONCAT('%', :deporteNombre, '%')) " +
			"AND (:ano IS NULL OR " +
			"FUNCTION('YEAR', COALESCE(pa.fechaAsignacion, pa.fechaPago)) = :ano OR " +
			"(:anoTexto IS NOT NULL AND (" +
			"((UPPER(pa.concepto) LIKE 'MENSUALIDAD%' OR UPPER(pa.concepto) LIKE 'TARIFA COMPETIDOR%' OR " +
			"(p.tipo IN (com.taemoi.project.entities.TipoProducto.MENSUALIDAD, com.taemoi.project.entities.TipoProducto.COMPETIDOR) AND p.codigo IS NOT NULL AND (:legado = false OR p.codigo NOT IN ('MENSUALIDAD', 'COMPETIDOR_TAEKWONDO', 'COMPETIDOR_KICKBOXING')))) " +
			"AND UPPER(pa.concepto) LIKE CONCAT('%', :anoTexto, '%')) OR " +
			"(COALESCE(pa.fechaAsignacion, pa.fechaPago) IS NULL AND UPPER(pa.concepto) LIKE CONCAT('%', :anoTexto, '%'))))) " +
			"AND (:mes IS NULL OR " +
			"FUNCTION('MONTH', COALESCE(pa.fechaAsignacion, pa.fechaPago)) = :mes OR " +
			"(:mesNombre IS NOT NULL AND (" +
			"((UPPER(pa.concepto) LIKE 'MENSUALIDAD%' OR UPPER(pa.concepto) LIKE 'TARIFA COMPETIDOR%' OR " +
			"(p.tipo IN (com.taemoi.project.entities.TipoProducto.MENSUALIDAD, com.taemoi.project.entities.TipoProducto.COMPETIDOR) AND p.codigo IS NOT NULL AND (:legado = false OR p.codigo NOT IN ('MENSUALIDAD', 'COMPETIDOR_TAEKWONDO', 'COMPETIDOR_KICKBOXING')))) " +
			"AND UPPER(pa.concepto) LIKE CONCAT('%', :mesNombre, '%')) OR " +
			"(COALESCE(pa.fechaAsignacion, pa.fechaPago) IS NULL AND UPPER(pa.concepto) LIKE CONCAT('%', :mesNombre, '%'))))) " +
			"AND (:texto IS NULL OR " +
			"UPPER(COALESCE(pa.concepto, '')) LIKE :texto OR " +
			"UPPER(COALESCE(pa.notas, '')) LIKE :texto OR " +
			"UPPER(COALESCE(a.nombre, '')) LIKE :texto OR " +
			"UPPER(COALESCE(a.apellidos, '')) LIKE :texto OR " +
			"UPPER(CONCAT(COALESCE(a.nombre, ''), ' ', COALESCE(a.apellidos, ''))) LIKE :texto OR " +
			"UPPER(COALESCE(adAlumno.nombre, '')) LIKE :texto OR " +
			"UPPER(COALESCE(adAlumno.apellidos, '')) LIKE :texto OR " +
			"UPPER(CONCAT(COALESCE(adAlumno.nombre, ''), ' ', COALESCE(adAlumno.apellidos, ''))) LIKE :texto) " +
			"AND (:soloActivos = false OR COALESCE(a.activo, false) = true OR COALESCE(adAlumno.activo, false) = true) " +
			"AND (:todasLasSedes = true OR pa.sedeId IN :sedes) " +
			"ORDER BY COALESCE(pa.fechaAsignacion, pa.fechaPago) DESC, pa.id DESC")
	List<TesoreriaPeriodoBaseProjection> findMovimientosTesoreriaPeriodoBase(
			@Param("deporte") String deporte,
			@Param("deporteNombre") String deporteNombre,
			@Param("pagado") Boolean pagado,
			@Param("texto") String texto,
			@Param("soloActivos") Boolean soloActivos,
			@Param("ano") Integer ano,
			@Param("anoTexto") String anoTexto,
			@Param("mes") Integer mes,
			@Param("mesNombre") String mesNombre,
			@Param("legado") Boolean legado,
			@Param("todasLasSedes") Boolean todasLasSedes,
			@Param("sedes") Collection<Long> sedes);

	@Query("SELECT pa FROM ProductoAlumno pa " +
			"LEFT JOIN FETCH pa.alumnoDeporte ad " +
			"LEFT JOIN FETCH ad.alumno adAlumno " +
			"LEFT JOIN FETCH pa.alumno a " +
			"WHERE pa.id IN :ids")
	List<ProductoAlumno> findMovimientosTesoreriaByIds(@Param("ids") List<Long> ids);

	@Query("SELECT COUNT(pa) " +
			"FROM ProductoAlumno pa " +
			"LEFT JOIN pa.alumnoDeporte ad " +
			"LEFT JOIN ad.disciplina adDisc " +
			"LEFT JOIN ad.alumno adAlumno " +
			"LEFT JOIN pa.alumno a " +
			"LEFT JOIN pa.producto p " +
			"WHERE (:pagado IS NULL OR " +
			"(:pagado = true AND pa.pagado = true) OR " +
			"(:pagado = false AND (pa.pagado = false OR pa.pagado IS NULL))) " +
			"AND (:deporte IS NULL OR adDisc.codigo = :deporte OR UPPER(pa.concepto) LIKE CONCAT('%', :deporteNombre, '%')) " +
			"AND (:ano IS NULL OR " +
			"FUNCTION('YEAR', COALESCE(pa.fechaAsignacion, pa.fechaPago)) = :ano OR " +
			"(:anoTexto IS NOT NULL AND (" +
			"((UPPER(pa.concepto) LIKE 'MENSUALIDAD%' OR UPPER(pa.concepto) LIKE 'TARIFA COMPETIDOR%' OR " +
			"(p.tipo IN (com.taemoi.project.entities.TipoProducto.MENSUALIDAD, com.taemoi.project.entities.TipoProducto.COMPETIDOR) AND p.codigo IS NOT NULL AND (:legado = false OR p.codigo NOT IN ('MENSUALIDAD', 'COMPETIDOR_TAEKWONDO', 'COMPETIDOR_KICKBOXING')))) " +
			"AND UPPER(pa.concepto) LIKE CONCAT('%', :anoTexto, '%')) OR " +
			"(COALESCE(pa.fechaAsignacion, pa.fechaPago) IS NULL AND UPPER(pa.concepto) LIKE CONCAT('%', :anoTexto, '%'))))) " +
			"AND (:mes IS NULL OR " +
			"FUNCTION('MONTH', COALESCE(pa.fechaAsignacion, pa.fechaPago)) = :mes OR " +
			"(:mesNombre IS NOT NULL AND (" +
			"((UPPER(pa.concepto) LIKE 'MENSUALIDAD%' OR UPPER(pa.concepto) LIKE 'TARIFA COMPETIDOR%' OR " +
			"(p.tipo IN (com.taemoi.project.entities.TipoProducto.MENSUALIDAD, com.taemoi.project.entities.TipoProducto.COMPETIDOR) AND p.codigo IS NOT NULL AND (:legado = false OR p.codigo NOT IN ('MENSUALIDAD', 'COMPETIDOR_TAEKWONDO', 'COMPETIDOR_KICKBOXING')))) " +
			"AND UPPER(pa.concepto) LIKE CONCAT('%', :mesNombre, '%')) OR " +
			"(COALESCE(pa.fechaAsignacion, pa.fechaPago) IS NULL AND UPPER(pa.concepto) LIKE CONCAT('%', :mesNombre, '%'))))) " +
			"AND (:soloActivos = false OR COALESCE(a.activo, false) = true OR COALESCE(adAlumno.activo, false) = true) " +
			"AND (:todasLasSedes = true OR pa.sedeId IN :sedes)")
	Long contarMovimientosTesoreria(
			@Param("deporte") String deporte,
			@Param("deporteNombre") String deporteNombre,
			@Param("pagado") Boolean pagado,
			@Param("soloActivos") Boolean soloActivos,
			@Param("ano") Integer ano,
			@Param("anoTexto") String anoTexto,
			@Param("mes") Integer mes,
			@Param("mesNombre") String mesNombre,
			@Param("legado") Boolean legado,
			@Param("todasLasSedes") Boolean todasLasSedes,
			@Param("sedes") Collection<Long> sedes);

	@Query("SELECT COALESCE(SUM(COALESCE(pa.precio, 0)), 0) " +
			"FROM ProductoAlumno pa " +
			"LEFT JOIN pa.alumnoDeporte ad " +
			"LEFT JOIN ad.disciplina adDisc " +
			"LEFT JOIN ad.alumno adAlumno " +
			"LEFT JOIN pa.alumno a " +
			"LEFT JOIN pa.producto p " +
			"WHERE (:pagado IS NULL OR " +
			"(:pagado = true AND pa.pagado = true) OR " +
			"(:pagado = false AND (pa.pagado = false OR pa.pagado IS NULL))) " +
			"AND (:deporte IS NULL OR adDisc.codigo = :deporte OR UPPER(pa.concepto) LIKE CONCAT('%', :deporteNombre, '%')) " +
			"AND (:ano IS NULL OR " +
			"FUNCTION('YEAR', COALESCE(pa.fechaAsignacion, pa.fechaPago)) = :ano OR " +
			"(:anoTexto IS NOT NULL AND (" +
			"((UPPER(pa.concepto) LIKE 'MENSUALIDAD%' OR UPPER(pa.concepto) LIKE 'TARIFA COMPETIDOR%' OR " +
			"(p.tipo IN (com.taemoi.project.entities.TipoProducto.MENSUALIDAD, com.taemoi.project.entities.TipoProducto.COMPETIDOR) AND p.codigo IS NOT NULL AND (:legado = false OR p.codigo NOT IN ('MENSUALIDAD', 'COMPETIDOR_TAEKWONDO', 'COMPETIDOR_KICKBOXING')))) " +
			"AND UPPER(pa.concepto) LIKE CONCAT('%', :anoTexto, '%')) OR " +
			"(COALESCE(pa.fechaAsignacion, pa.fechaPago) IS NULL AND UPPER(pa.concepto) LIKE CONCAT('%', :anoTexto, '%'))))) " +
			"AND (:mes IS NULL OR " +
			"FUNCTION('MONTH', COALESCE(pa.fechaAsignacion, pa.fechaPago)) = :mes OR " +
			"(:mesNombre IS NOT NULL AND (" +
			"((UPPER(pa.concepto) LIKE 'MENSUALIDAD%' OR UPPER(pa.concepto) LIKE 'TARIFA COMPETIDOR%' OR " +
			"(p.tipo IN (com.taemoi.project.entities.TipoProducto.MENSUALIDAD, com.taemoi.project.entities.TipoProducto.COMPETIDOR) AND p.codigo IS NOT NULL AND (:legado = false OR p.codigo NOT IN ('MENSUALIDAD', 'COMPETIDOR_TAEKWONDO', 'COMPETIDOR_KICKBOXING')))) " +
			"AND UPPER(pa.concepto) LIKE CONCAT('%', :mesNombre, '%')) OR " +
			"(COALESCE(pa.fechaAsignacion, pa.fechaPago) IS NULL AND UPPER(pa.concepto) LIKE CONCAT('%', :mesNombre, '%'))))) " +
			"AND (:soloActivos = false OR COALESCE(a.activo, false) = true OR COALESCE(adAlumno.activo, false) = true) " +
			"AND (:todasLasSedes = true OR pa.sedeId IN :sedes)")
	Double sumarImporteMovimientosTesoreria(
			@Param("deporte") String deporte,
			@Param("deporteNombre") String deporteNombre,
			@Param("pagado") Boolean pagado,
			@Param("soloActivos") Boolean soloActivos,
			@Param("ano") Integer ano,
			@Param("anoTexto") String anoTexto,
			@Param("mes") Integer mes,
			@Param("mesNombre") String mesNombre,
			@Param("legado") Boolean legado,
			@Param("todasLasSedes") Boolean todasLasSedes,
			@Param("sedes") Collection<Long> sedes);

	@Query("SELECT COUNT(DISTINCT COALESCE(a.id, adAlumno.id)) " +
			"FROM ProductoAlumno pa " +
			"LEFT JOIN pa.alumno a " +
			"LEFT JOIN pa.alumnoDeporte ad " +
			"LEFT JOIN ad.disciplina adDisc " +
			"LEFT JOIN ad.alumno adAlumno " +
			"LEFT JOIN pa.producto p " +
			"WHERE (pa.pagado = false OR pa.pagado IS NULL) " +
			"AND (:deporte IS NULL OR adDisc.codigo = :deporte OR UPPER(pa.concepto) LIKE CONCAT('%', :deporteNombre, '%')) " +
			"AND (:ano IS NULL OR " +
			"FUNCTION('YEAR', COALESCE(pa.fechaAsignacion, pa.fechaPago)) = :ano OR " +
			"(:anoTexto IS NOT NULL AND (" +
			"((UPPER(pa.concepto) LIKE 'MENSUALIDAD%' OR UPPER(pa.concepto) LIKE 'TARIFA COMPETIDOR%' OR " +
			"(p.tipo IN (com.taemoi.project.entities.TipoProducto.MENSUALIDAD, com.taemoi.project.entities.TipoProducto.COMPETIDOR) AND p.codigo IS NOT NULL AND (:legado = false OR p.codigo NOT IN ('MENSUALIDAD', 'COMPETIDOR_TAEKWONDO', 'COMPETIDOR_KICKBOXING')))) " +
			"AND UPPER(pa.concepto) LIKE CONCAT('%', :anoTexto, '%')) OR " +
			"(COALESCE(pa.fechaAsignacion, pa.fechaPago) IS NULL AND UPPER(pa.concepto) LIKE CONCAT('%', :anoTexto, '%'))))) " +
			"AND (:mes IS NULL OR " +
			"FUNCTION('MONTH', COALESCE(pa.fechaAsignacion, pa.fechaPago)) = :mes OR " +
			"(:mesNombre IS NOT NULL AND (" +
			"((UPPER(pa.concepto) LIKE 'MENSUALIDAD%' OR UPPER(pa.concepto) LIKE 'TARIFA COMPETIDOR%' OR " +
			"(p.tipo IN (com.taemoi.project.entities.TipoProducto.MENSUALIDAD, com.taemoi.project.entities.TipoProducto.COMPETIDOR) AND p.codigo IS NOT NULL AND (:legado = false OR p.codigo NOT IN ('MENSUALIDAD', 'COMPETIDOR_TAEKWONDO', 'COMPETIDOR_KICKBOXING')))) " +
			"AND UPPER(pa.concepto) LIKE CONCAT('%', :mesNombre, '%')) OR " +
			"(COALESCE(pa.fechaAsignacion, pa.fechaPago) IS NULL AND UPPER(pa.concepto) LIKE CONCAT('%', :mesNombre, '%'))))) " +
			"AND (:soloActivos = false OR COALESCE(a.activo, false) = true OR COALESCE(adAlumno.activo, false) = true) " +
			"AND (:todasLasSedes = true OR pa.sedeId IN :sedes)")
	Long contarAlumnosConPendientesTesoreria(
			@Param("deporte") String deporte,
			@Param("deporteNombre") String deporteNombre,
			@Param("soloActivos") Boolean soloActivos,
			@Param("ano") Integer ano,
			@Param("anoTexto") String anoTexto,
			@Param("mes") Integer mes,
			@Param("mesNombre") String mesNombre,
			@Param("legado") Boolean legado,
			@Param("todasLasSedes") Boolean todasLasSedes,
			@Param("sedes") Collection<Long> sedes);


	/**
	 * Entrega 6-3 (D6-12): {@link #contarMovimientosTesoreria} y {@link #sumarImporteMovimientosTesoreria} por sede del
	 * cargo, en una consulta y sin cargar entidades. MISMO {@code FROM ... WHERE} que esas dos (copiado; lo comprueba
	 * {@code ProductoAlumnoRepositoryConsultasTest}): la suma de las filas es el total del resumen. Pendiente = pagado
	 * FALSE o NULL, como {@code :pagado = false}.
	 */
	@Query("SELECT pa.sedeId AS sedeId, COUNT(pa) AS totalMovimientos, " +
			"SUM(CASE WHEN pa.pagado = true THEN 1 ELSE 0 END) AS totalPagados, " +
			"SUM(CASE WHEN pa.pagado = true THEN 0 ELSE 1 END) AS totalPendientes, " +
			"COALESCE(SUM(COALESCE(pa.precio, 0)), 0) AS importeTotal, " +
			"COALESCE(SUM(CASE WHEN pa.pagado = true THEN COALESCE(pa.precio, 0) ELSE 0 END), 0) AS importePagado, " +
			"COALESCE(SUM(CASE WHEN pa.pagado = true THEN 0 ELSE COALESCE(pa.precio, 0) END), 0) AS importePendiente " +
			"FROM ProductoAlumno pa " +
			"LEFT JOIN pa.alumnoDeporte ad " +
			"LEFT JOIN ad.disciplina adDisc " +
			"LEFT JOIN ad.alumno adAlumno " +
			"LEFT JOIN pa.alumno a " +
			"LEFT JOIN pa.producto p " +
			"WHERE (:pagado IS NULL OR " +
			"(:pagado = true AND pa.pagado = true) OR " +
			"(:pagado = false AND (pa.pagado = false OR pa.pagado IS NULL))) " +
			"AND (:deporte IS NULL OR adDisc.codigo = :deporte OR UPPER(pa.concepto) LIKE CONCAT('%', :deporteNombre, '%')) " +
			"AND (:ano IS NULL OR " +
			"FUNCTION('YEAR', COALESCE(pa.fechaAsignacion, pa.fechaPago)) = :ano OR " +
			"(:anoTexto IS NOT NULL AND (" +
			"((UPPER(pa.concepto) LIKE 'MENSUALIDAD%' OR UPPER(pa.concepto) LIKE 'TARIFA COMPETIDOR%' OR " +
			"(p.tipo IN (com.taemoi.project.entities.TipoProducto.MENSUALIDAD, com.taemoi.project.entities.TipoProducto.COMPETIDOR) AND p.codigo IS NOT NULL AND (:legado = false OR p.codigo NOT IN ('MENSUALIDAD', 'COMPETIDOR_TAEKWONDO', 'COMPETIDOR_KICKBOXING')))) " +
			"AND UPPER(pa.concepto) LIKE CONCAT('%', :anoTexto, '%')) OR " +
			"(COALESCE(pa.fechaAsignacion, pa.fechaPago) IS NULL AND UPPER(pa.concepto) LIKE CONCAT('%', :anoTexto, '%'))))) " +
			"AND (:mes IS NULL OR " +
			"FUNCTION('MONTH', COALESCE(pa.fechaAsignacion, pa.fechaPago)) = :mes OR " +
			"(:mesNombre IS NOT NULL AND (" +
			"((UPPER(pa.concepto) LIKE 'MENSUALIDAD%' OR UPPER(pa.concepto) LIKE 'TARIFA COMPETIDOR%' OR " +
			"(p.tipo IN (com.taemoi.project.entities.TipoProducto.MENSUALIDAD, com.taemoi.project.entities.TipoProducto.COMPETIDOR) AND p.codigo IS NOT NULL AND (:legado = false OR p.codigo NOT IN ('MENSUALIDAD', 'COMPETIDOR_TAEKWONDO', 'COMPETIDOR_KICKBOXING')))) " +
			"AND UPPER(pa.concepto) LIKE CONCAT('%', :mesNombre, '%')) OR " +
			"(COALESCE(pa.fechaAsignacion, pa.fechaPago) IS NULL AND UPPER(pa.concepto) LIKE CONCAT('%', :mesNombre, '%'))))) " +
			"AND (:soloActivos = false OR COALESCE(a.activo, false) = true OR COALESCE(adAlumno.activo, false) = true) " +
			"AND (:todasLasSedes = true OR pa.sedeId IN :sedes) " +
			"GROUP BY pa.sedeId")
	List<TesoreriaResumenPorSedeProjection> resumirMovimientosTesoreriaPorSede(
			@Param("deporte") String deporte,
			@Param("deporteNombre") String deporteNombre,
			@Param("pagado") Boolean pagado,
			@Param("soloActivos") Boolean soloActivos,
			@Param("ano") Integer ano,
			@Param("anoTexto") String anoTexto,
			@Param("mes") Integer mes,
			@Param("mesNombre") String mesNombre,
			@Param("legado") Boolean legado,
			@Param("todasLasSedes") Boolean todasLasSedes,
			@Param("sedes") Collection<Long> sedes);

	/**
	 * Entrega 6-3: {@link #contarAlumnosConPendientesTesoreria} por sede del cargo (MISMO {@code FROM ... WHERE},
	 * copiado). Un alumno con pendientes en dos sedes cuenta en las dos.
	 */
	@Query("SELECT pa.sedeId AS sedeId, COUNT(DISTINCT COALESCE(a.id, adAlumno.id)) AS alumnosConPendientes " +
			"FROM ProductoAlumno pa " +
			"LEFT JOIN pa.alumno a " +
			"LEFT JOIN pa.alumnoDeporte ad " +
			"LEFT JOIN ad.disciplina adDisc " +
			"LEFT JOIN ad.alumno adAlumno " +
			"LEFT JOIN pa.producto p " +
			"WHERE (pa.pagado = false OR pa.pagado IS NULL) " +
			"AND (:deporte IS NULL OR adDisc.codigo = :deporte OR UPPER(pa.concepto) LIKE CONCAT('%', :deporteNombre, '%')) " +
			"AND (:ano IS NULL OR " +
			"FUNCTION('YEAR', COALESCE(pa.fechaAsignacion, pa.fechaPago)) = :ano OR " +
			"(:anoTexto IS NOT NULL AND (" +
			"((UPPER(pa.concepto) LIKE 'MENSUALIDAD%' OR UPPER(pa.concepto) LIKE 'TARIFA COMPETIDOR%' OR " +
			"(p.tipo IN (com.taemoi.project.entities.TipoProducto.MENSUALIDAD, com.taemoi.project.entities.TipoProducto.COMPETIDOR) AND p.codigo IS NOT NULL AND (:legado = false OR p.codigo NOT IN ('MENSUALIDAD', 'COMPETIDOR_TAEKWONDO', 'COMPETIDOR_KICKBOXING')))) " +
			"AND UPPER(pa.concepto) LIKE CONCAT('%', :anoTexto, '%')) OR " +
			"(COALESCE(pa.fechaAsignacion, pa.fechaPago) IS NULL AND UPPER(pa.concepto) LIKE CONCAT('%', :anoTexto, '%'))))) " +
			"AND (:mes IS NULL OR " +
			"FUNCTION('MONTH', COALESCE(pa.fechaAsignacion, pa.fechaPago)) = :mes OR " +
			"(:mesNombre IS NOT NULL AND (" +
			"((UPPER(pa.concepto) LIKE 'MENSUALIDAD%' OR UPPER(pa.concepto) LIKE 'TARIFA COMPETIDOR%' OR " +
			"(p.tipo IN (com.taemoi.project.entities.TipoProducto.MENSUALIDAD, com.taemoi.project.entities.TipoProducto.COMPETIDOR) AND p.codigo IS NOT NULL AND (:legado = false OR p.codigo NOT IN ('MENSUALIDAD', 'COMPETIDOR_TAEKWONDO', 'COMPETIDOR_KICKBOXING')))) " +
			"AND UPPER(pa.concepto) LIKE CONCAT('%', :mesNombre, '%')) OR " +
			"(COALESCE(pa.fechaAsignacion, pa.fechaPago) IS NULL AND UPPER(pa.concepto) LIKE CONCAT('%', :mesNombre, '%'))))) " +
			"AND (:soloActivos = false OR COALESCE(a.activo, false) = true OR COALESCE(adAlumno.activo, false) = true) " +
			"AND (:todasLasSedes = true OR pa.sedeId IN :sedes) " +
			"GROUP BY pa.sedeId")
	List<TesoreriaAlumnosPendientesPorSedeProjection> contarAlumnosConPendientesTesoreriaPorSede(
			@Param("deporte") String deporte,
			@Param("deporteNombre") String deporteNombre,
			@Param("soloActivos") Boolean soloActivos,
			@Param("ano") Integer ano,
			@Param("anoTexto") String anoTexto,
			@Param("mes") Integer mes,
			@Param("mesNombre") String mesNombre,
			@Param("legado") Boolean legado,
			@Param("todasLasSedes") Boolean todasLasSedes,
			@Param("sedes") Collection<Long> sedes);

	@Query("SELECT DISTINCT FUNCTION('YEAR', COALESCE(pa.fechaAsignacion, pa.fechaPago)) " +
			"FROM ProductoAlumno pa " +
			"WHERE COALESCE(pa.fechaAsignacion, pa.fechaPago) IS NOT NULL " +
			"ORDER BY FUNCTION('YEAR', COALESCE(pa.fechaAsignacion, pa.fechaPago))")
	List<Integer> findAniosDistintosTesoreriaConFecha();

	@Query("SELECT pa.concepto FROM ProductoAlumno pa " +
			"WHERE pa.fechaAsignacion IS NULL AND pa.fechaPago IS NULL AND pa.concepto IS NOT NULL")
	List<String> findConceptosTesoreriaSinFecha();

	@Query("SELECT pa.concepto FROM ProductoAlumno pa " +
			"LEFT JOIN pa.producto p " +
			"WHERE pa.concepto IS NOT NULL " +
			"AND (UPPER(pa.concepto) LIKE 'MENSUALIDAD%' OR UPPER(pa.concepto) LIKE 'TARIFA COMPETIDOR%' OR " +
			"(p.tipo IN (com.taemoi.project.entities.TipoProducto.MENSUALIDAD, com.taemoi.project.entities.TipoProducto.COMPETIDOR) AND p.codigo IS NOT NULL AND (:legado = false OR p.codigo NOT IN ('MENSUALIDAD', 'COMPETIDOR_TAEKWONDO', 'COMPETIDOR_KICKBOXING'))))")
	List<String> findConceptosTesoreriaConPeriodoPotencial(@Param("legado") Boolean legado);

	List<ProductoAlumno> findByAlumnoDeporteId(Long alumnoDeporteId);

	List<ProductoAlumno> findByProductoId(Long productoId);

	boolean existsByProductoId(Long productoId);
}
