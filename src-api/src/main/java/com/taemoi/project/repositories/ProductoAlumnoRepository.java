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
import com.taemoi.project.utils.ImporteCargo;

public interface ProductoAlumnoRepository extends JpaRepository<ProductoAlumno, Long> {
	/**
	 * Pieza 8b-3l bis (decisión 287): ids de los cargos sin pagar del alumno en la escuela indicada (los suyos, los de
	 * sus inscripciones y los de sus convocatorias), con la misma regla que avisa el resumen de cargos
	 * ({@link ResumenCargosAlumnoRepository#CARGO_SIN_PAGAR}).
	 */
	@org.springframework.data.jpa.repository.Query(value = """
			SELECT pa.id FROM alumno a
			JOIN producto_alumno pa ON """ + ResumenCargosAlumnoRepository.CARGO_DEL_ALUMNO + """
			  AND """ + ResumenCargosAlumnoRepository.CARGO_SIN_PAGAR + """
			WHERE a.organizacion_id = :organizacion AND a.id = :alumno
			ORDER BY pa.id
			""", nativeQuery = true)
	List<Long> idsCargosSinPagarDelAlumno(@org.springframework.data.repository.query.Param("organizacion") Long organizacion,
			@org.springframework.data.repository.query.Param("alumno") Long alumno);

	interface TesoreriaPeriodoBaseProjection {
		Long getId();
		String getConcepto();
		Date getFechaAsignacion();
		Date getFechaPago();
		Boolean getPagado();
		/** Precio unitario; el importe es {@code ImporteCargo.sinRedondear(getPrecio(), getCantidad())} (decisión 297). */
		Double getPrecio();
		/** Cantidad del cargo (decisión 297): nula o menor que 1 cuenta como 1. */
		Integer getCantidad();
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

	/*
	 * Pieza 6b-2c, cargos cancelados (cancelado_en no nulo): NO cuentan como pendientes ni como deuda. Las consultas de
	 * la tesorería (con su countQuery), las deudas y las reservas llevan «pa.canceladoEn IS NULL» (una vez; lo comprueba
	 * ProductoAlumnoRepositoryConsultasTest). Las de los informes de mensualidades NO: el PDF los pinta «Cancelado» y no
	 * los suma. Las que usa la GENERACIÓN para no duplicar (findByAlumnoId, existsByConcepto, findAll) tampoco, a
	 * propósito: un mes cancelado cuenta como existente y no se regenera. No añadir el filtro ahí.
	 */

	List<ProductoAlumno> findByAlumnoId(Long alumnoId);

	/**
	 * Pieza 279 (recibo de un cargo de legado): por id con una consulta (filtrada por la escuela activa), no con
	 * {@code findById}, como {@code SedeRepository#buscarEnEscuelaActiva}.
	 */
	@Query("SELECT pa FROM ProductoAlumno pa WHERE pa.id = :id")
	Optional<ProductoAlumno> buscarEnEscuelaActiva(@Param("id") Long id);

	/** Entrega 6-3: el cargo (de la escuela activa) es de una de esas sedes ({@code @acceso.productoAlumnoEnSede}). */
	boolean existsByIdAndSedeIdIn(Long id, java.util.Collection<Long> sedeIds);

	/**
	 * Pieza 6b-3b-1: de esos cargos, los vendidos por un profesor ({@code vendido_por_usuario_id}), con su producto:
	 * filas {@code [cargoId, vendidoPorUsuarioId, Producto]}. Sin cargar los cargos (ni sus relaciones EAGER).
	 */
	@Query("SELECT pa.id, pa.vendidoPorUsuarioId, p FROM ProductoAlumno pa JOIN pa.producto p "
			+ "WHERE pa.id IN :ids AND pa.vendidoPorUsuarioId IS NOT NULL")
	List<Object[]> cargosVendidosPorProfesorConProducto(@Param("ids") Collection<Long> ids);

	/**
	 * Pieza 6b-2: cuántos cargos con ese id (0 o 1) son de un alumno ({@code COALESCE(alumno, alumnoDeporte.alumno)},
	 * como la tesorería) que está en alguno de esos grupos ({@code @acceso.cargoDeMisGrupos}).
	 */
	@Query("SELECT COUNT(pa) FROM ProductoAlumno pa LEFT JOIN pa.alumnoDeporte ad WHERE pa.id = :id AND EXISTS ("
			+ "SELECT g.id FROM Grupo g JOIN g.alumnos m WHERE g.id IN :grupos "
			+ "AND (m.id = pa.alumno.id OR m.id = ad.alumno.id))")
	long contarCargoDeAlumnoEnGrupos(@Param("id") Long id, @Param("grupos") java.util.Collection<Long> grupos);

	Optional<ProductoAlumno> findByAlumnoIdAndProductoId(Long id, Long id2);

	boolean existsByConcepto(String concepto);

	@Query("SELECT pa FROM ProductoAlumno pa JOIN FETCH pa.alumno a WHERE pa.pagado = false AND pa.canceladoEn IS NULL ORDER BY a.nombre, a.apellidos, pa.fechaAsignacion")
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
			"AND pa.canceladoEn IS NULL " +
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
					"AND (:todasLasSedes = true OR pa.sedeId IN :sedes) AND pa.canceladoEn IS NULL " +
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
					"AND (:todasLasSedes = true OR pa.sedeId IN :sedes) AND pa.canceladoEn IS NULL")
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
			"AND (:todasLasSedes = true OR pa.sedeId IN :sedes) AND pa.canceladoEn IS NULL " +
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
			"pa.fechaPago AS fechaPago, pa.pagado AS pagado, pa.precio AS precio, pa.cantidad AS cantidad, p.tipo AS productoTipo, p.codigo AS productoCodigo, " +
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
			"AND (:todasLasSedes = true OR pa.sedeId IN :sedes) AND pa.canceladoEn IS NULL " +
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
			"AND (:todasLasSedes = true OR pa.sedeId IN :sedes) AND pa.canceladoEn IS NULL")
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

	@Query("SELECT COALESCE(SUM(" + ImporteCargo.SQL + "), 0) " +
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
			"AND (:todasLasSedes = true OR pa.sedeId IN :sedes) AND pa.canceladoEn IS NULL")
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

	/**
	 * Pieza 6c-6 (D6c-12): Σ de lo devuelto (−importe de la DEVOLUCION ACTIVA enlazada a su cobro ACTIVO) de los cargos
	 * que cuenta {@link #sumarImporteMovimientosTesoreria} con los mismos filtros (sin el de pagado). Aditivo: los
	 * totales de siempre no cambian (el cargo devuelto sigue sumando como pagado, decisión 153).
	 */
	@Query("SELECT COALESCE(SUM(-m.importe), 0) " +
			"FROM ProductoAlumno pa " +
			"JOIN Cobro c ON c.productoAlumnoId = pa.id AND c.estado = com.taemoi.project.entities.EstadoCobro.ACTIVO " +
			"JOIN MovimientoCaja m ON m.cobroId = c.id " +
			"AND m.tipo = com.taemoi.project.entities.TipoMovimientoCaja.DEVOLUCION " +
			"AND m.estado = com.taemoi.project.entities.EstadoMovimientoCaja.ACTIVO " +
			"LEFT JOIN pa.alumnoDeporte ad " +
			"LEFT JOIN ad.disciplina adDisc " +
			"LEFT JOIN ad.alumno adAlumno " +
			"LEFT JOIN pa.alumno a " +
			"LEFT JOIN pa.producto p " +
			"WHERE (:deporte IS NULL OR adDisc.codigo = :deporte OR UPPER(pa.concepto) LIKE CONCAT('%', :deporteNombre, '%')) " +
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
			"AND (:todasLasSedes = true OR pa.sedeId IN :sedes) AND pa.canceladoEn IS NULL")
	java.math.BigDecimal sumarImporteDevueltoTesoreria(
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
			"AND (:todasLasSedes = true OR pa.sedeId IN :sedes) AND pa.canceladoEn IS NULL")
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
			"COALESCE(SUM(" + ImporteCargo.SQL + "), 0) AS importeTotal, " +
			"COALESCE(SUM(CASE WHEN pa.pagado = true THEN " + ImporteCargo.SQL + " ELSE 0 END), 0) AS importePagado, " +
			"COALESCE(SUM(CASE WHEN pa.pagado = true THEN 0 ELSE " + ImporteCargo.SQL + " END), 0) AS importePendiente " +
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
			"AND (:todasLasSedes = true OR pa.sedeId IN :sedes) AND pa.canceladoEn IS NULL " +
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
			"AND (:todasLasSedes = true OR pa.sedeId IN :sedes) AND pa.canceladoEn IS NULL " +
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
			"AND (:todasLasSedes = true OR pa.sedeId IN :sedes) " +
			"ORDER BY FUNCTION('YEAR', COALESCE(pa.fechaAsignacion, pa.fechaPago))")
	List<Integer> findAniosDistintosTesoreriaConFecha(@Param("todasLasSedes") Boolean todasLasSedes,
			@Param("sedes") Collection<Long> sedes);

	@Query("SELECT pa.concepto FROM ProductoAlumno pa " +
			"WHERE pa.fechaAsignacion IS NULL AND pa.fechaPago IS NULL AND pa.concepto IS NOT NULL " +
			"AND (:todasLasSedes = true OR pa.sedeId IN :sedes)")
	List<String> findConceptosTesoreriaSinFecha(@Param("todasLasSedes") Boolean todasLasSedes,
			@Param("sedes") Collection<Long> sedes);

	@Query("SELECT pa.concepto FROM ProductoAlumno pa " +
			"LEFT JOIN pa.producto p " +
			"WHERE pa.concepto IS NOT NULL " +
			"AND (UPPER(pa.concepto) LIKE 'MENSUALIDAD%' OR UPPER(pa.concepto) LIKE 'TARIFA COMPETIDOR%' OR " +
			"(p.tipo IN (com.taemoi.project.entities.TipoProducto.MENSUALIDAD, com.taemoi.project.entities.TipoProducto.COMPETIDOR) AND p.codigo IS NOT NULL AND (:legado = false OR p.codigo NOT IN ('MENSUALIDAD', 'COMPETIDOR_TAEKWONDO', 'COMPETIDOR_KICKBOXING')))) " +
			"AND (:todasLasSedes = true OR pa.sedeId IN :sedes)")
	List<String> findConceptosTesoreriaConPeriodoPotencial(@Param("legado") Boolean legado,
			@Param("todasLasSedes") Boolean todasLasSedes, @Param("sedes") Collection<Long> sedes);

	List<ProductoAlumno> findByAlumnoDeporteId(Long alumnoDeporteId);

	List<ProductoAlumno> findByProductoId(Long productoId);

	boolean existsByProductoId(Long productoId);

	/**
	 * Pieza 6b-1: bloquea (SELECT ... FOR UPDATE) la fila del cargo de la escuela activa, sin cargar la entidad ni
	 * bloquear filas de otras tablas. Solo dentro de una transacción ({@code CobroService.bloquearCargo}).
	 */
	@org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT pa.id FROM ProductoAlumno pa WHERE pa.id = :id")
	Optional<Long> bloquearPorId(@Param("id") Long id);

	/**
	 * Pieza 9-1: bloquea (SELECT ... FOR UPDATE) esos cargos de la escuela activa, en orden de id y sin cargar las
	 * entidades, para que el cambio de sede y un cobro se ordenen sobre la misma fila ({@code CambioSedeAlumnoService}).
	 */
	@org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT pa.id FROM ProductoAlumno pa WHERE pa.id IN :ids ORDER BY pa.id")
	List<Long> bloquearPorIdsEnOrden(@Param("ids") Collection<Long> ids);

	/** Pieza 9-1: lo que el cambio de sede necesita de un cargo; el importe sale de {@link ImporteCargo}. */
	interface CargoDeCambioDeSede {
		Long getId();

		Double getPrecio();

		Integer getCantidad();
	}

	/** El alumno del cargo como lo resuelve la tesorería: {@code COALESCE(alumno, alumnoDeporte.alumno)}. */
	String CARGO_DEL_ALUMNO = "(pa.alumno.id = :alumnoId OR (pa.alumno IS NULL AND pa.alumnoDeporte.id IN "
			+ "(SELECT ad.id FROM AlumnoDeporte ad WHERE ad.alumno.id = :alumnoId)))";

	/**
	 * Decisión 369: los cargos del alumno que el cambio de sede mueve a {@code :sedeId}: sin cobrar (pagado falso o
	 * nulo, como en la tesorería), no cancelados y que no estén ya en esa sede. Un cargo sin pagar no debería tener cobro
	 * ACTIVO (el pago y el cobro cambian juntos, con el cargo bloqueado); si lo tuviera, no se mueve: el cobro se quedaría
	 * en otra sede.
	 */
	String CARGO_QUE_PASA_A_SEDE = CARGO_DEL_ALUMNO + " AND (pa.pagado = false OR pa.pagado IS NULL) "
			+ "AND pa.canceladoEn IS NULL AND (pa.sedeId IS NULL OR pa.sedeId <> :sedeId) "
			+ "AND NOT EXISTS (SELECT c.id FROM Cobro c WHERE c.productoAlumnoId = pa.id "
			+ "AND c.estado = com.taemoi.project.entities.EstadoCobro.ACTIVO)";

	/** Pieza 9-1: los cargos que el cambio de sede movería ahora a esa sede (ver {@link #CARGO_QUE_PASA_A_SEDE}). */
	@Query("SELECT pa.id AS id, pa.precio AS precio, pa.cantidad AS cantidad FROM ProductoAlumno pa WHERE "
			+ CARGO_QUE_PASA_A_SEDE + " ORDER BY pa.id")
	List<CargoDeCambioDeSede> cargosQuePasanASede(@Param("alumnoId") Long alumnoId, @Param("sedeId") Long sedeId);

	/** Pieza 9-1: los cargos pagados del alumno en esa sede; con {@code sedeId} nulo, los pagados sin sede. */
	@Query("SELECT pa.id AS id, pa.precio AS precio, pa.cantidad AS cantidad FROM ProductoAlumno pa WHERE "
			+ CARGO_DEL_ALUMNO + " AND pa.pagado = true AND (pa.sedeId = :sedeId OR (:sedeId IS NULL AND pa.sedeId IS NULL))"
			+ " ORDER BY pa.id")
	List<CargoDeCambioDeSede> cargosCobradosEnSede(@Param("alumnoId") Long alumnoId, @Param("sedeId") Long sedeId);

	/**
	 * Decisión 369: la ÚNICA escritura de {@code producto_alumno.sede_id} después del alta; el mapeo de la entidad es
	 * {@code updatable = false}. Mueve los cargos de {@link #CARGO_QUE_PASA_A_SEDE} de la escuela activa y devuelve
	 * cuántos. El importe, el pago y el resto del cargo no cambian. Quien la llama bloquea antes los cargos.
	 */
	@org.springframework.transaction.annotation.Transactional
	@org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE ProductoAlumno pa SET pa.sedeId = :sedeId WHERE " + CARGO_QUE_PASA_A_SEDE)
	int moverASede(@Param("alumnoId") Long alumnoId, @Param("sedeId") Long sedeId);
}
