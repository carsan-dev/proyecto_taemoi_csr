package com.taemoi.project.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.taemoi.project.entities.ProductoAlumno;

public interface ProductoAlumnoRepository extends JpaRepository<ProductoAlumno, Long> {
	List<ProductoAlumno> findByAlumnoId(Long alumnoId);

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

	@Query("SELECT pa FROM ProductoAlumno pa JOIN FETCH pa.alumnoDeporte ad JOIN FETCH ad.alumno a " +
	       "WHERE pa.concepto LIKE 'MENSUALIDAD%' AND ad.deporte = :deporte " +
	       "ORDER BY a.nombre, a.apellidos, pa.fechaAsignacion DESC")
	List<ProductoAlumno> findMensualidadesByDeporteWithAlumno(com.taemoi.project.entities.Deporte deporte);

	@Query("SELECT pa FROM ProductoAlumno pa JOIN FETCH pa.alumnoDeporte ad JOIN FETCH ad.alumno a " +
	       "WHERE (pa.concepto LIKE 'MENSUALIDAD%' OR pa.concepto LIKE 'TARIFA COMPETIDOR%') AND ad.deporte = :deporte " +
	       "ORDER BY a.nombre, a.apellidos, pa.fechaAsignacion DESC")
	List<ProductoAlumno> findMensualidadesYTarifasCompetidorByDeporteWithAlumno(com.taemoi.project.entities.Deporte deporte);

	@Query("SELECT pa FROM ProductoAlumno pa LEFT JOIN FETCH pa.alumnoDeporte ad LEFT JOIN FETCH pa.alumno a " +
	       "WHERE (pa.concepto LIKE 'MENSUALIDAD%' OR pa.concepto LIKE 'TARIFA COMPETIDOR%') " +
	       "AND (ad.deporte = :deporte OR pa.concepto LIKE CONCAT('%', :deporteNombre, '%')) " +
	       "ORDER BY a.nombre, a.apellidos, pa.fechaAsignacion DESC")
	List<ProductoAlumno> findMensualidadesYTarifasCompetidorByDeporteOrConcepto(
			@Param("deporte") com.taemoi.project.entities.Deporte deporte,
			@Param("deporteNombre") String deporteNombre);

	@Query("SELECT pa FROM ProductoAlumno pa JOIN FETCH pa.alumnoDeporte ad JOIN FETCH ad.alumno a " +
	       "WHERE pa.concepto LIKE CONCAT(:conceptoBase, '%') AND (ad.deporte = com.taemoi.project.entities.Deporte.TAEKWONDO " +
	       "OR ad.deporte = com.taemoi.project.entities.Deporte.KICKBOXING) ORDER BY a.nombre, a.apellidos")
	List<ProductoAlumno> findMensualidadByConceptoAndDeportes(String conceptoBase);

	@Query("SELECT pa FROM ProductoAlumno pa JOIN FETCH pa.alumnoDeporte ad JOIN FETCH ad.alumno a " +
	       "WHERE (pa.concepto LIKE CONCAT(:conceptoBase, '%') OR pa.concepto LIKE CONCAT('TARIFA COMPETIDOR%', :mesAno, '%')) " +
	       "AND (ad.deporte = com.taemoi.project.entities.Deporte.TAEKWONDO OR ad.deporte = com.taemoi.project.entities.Deporte.KICKBOXING) " +
	       "ORDER BY a.nombre, a.apellidos, pa.concepto")
	List<ProductoAlumno> findMensualidadesYTarifasCompetidorByMes(String conceptoBase, String mesAno);

	@Query(
			value = "SELECT pa FROM ProductoAlumno pa " +
					"LEFT JOIN FETCH pa.alumnoDeporte ad " +
					"LEFT JOIN FETCH ad.alumno adAlumno " +
					"LEFT JOIN FETCH pa.alumno a " +
					"WHERE (:pagado IS NULL OR " +
					"(:pagado = true AND pa.pagado = true) OR " +
					"(:pagado = false AND (pa.pagado = false OR pa.pagado IS NULL))) " +
					"AND (:deporte IS NULL OR ad.deporte = :deporte OR UPPER(pa.concepto) LIKE CONCAT('%', :deporteNombre, '%')) " +
					"AND (:ano IS NULL OR " +
					"FUNCTION('YEAR', COALESCE(pa.fechaAsignacion, pa.fechaPago)) = :ano OR " +
					"(pa.fechaAsignacion IS NULL AND pa.fechaPago IS NULL AND :anoTexto IS NOT NULL AND UPPER(pa.concepto) LIKE CONCAT('%', :anoTexto, '%'))) " +
					"AND (:mes IS NULL OR " +
					"FUNCTION('MONTH', COALESCE(pa.fechaAsignacion, pa.fechaPago)) = :mes OR " +
					"(pa.fechaAsignacion IS NULL AND pa.fechaPago IS NULL AND :mesNombre IS NOT NULL AND UPPER(pa.concepto) LIKE CONCAT('%', :mesNombre, '%'))) " +
					"AND (:texto IS NULL OR " +
					"UPPER(COALESCE(pa.concepto, '')) LIKE :texto OR " +
					"UPPER(COALESCE(pa.notas, '')) LIKE :texto OR " +
					"UPPER(COALESCE(a.nombre, '')) LIKE :texto OR " +
					"UPPER(COALESCE(a.apellidos, '')) LIKE :texto OR " +
					"UPPER(CONCAT(COALESCE(a.nombre, ''), ' ', COALESCE(a.apellidos, ''))) LIKE :texto OR " +
					"UPPER(COALESCE(adAlumno.nombre, '')) LIKE :texto OR " +
					"UPPER(COALESCE(adAlumno.apellidos, '')) LIKE :texto OR " +
					"UPPER(CONCAT(COALESCE(adAlumno.nombre, ''), ' ', COALESCE(adAlumno.apellidos, ''))) LIKE :texto) " +
					"ORDER BY COALESCE(pa.fechaAsignacion, pa.fechaPago) DESC, pa.id DESC",
			countQuery = "SELECT COUNT(pa) FROM ProductoAlumno pa " +
					"LEFT JOIN pa.alumnoDeporte ad " +
					"LEFT JOIN ad.alumno adAlumno " +
					"LEFT JOIN pa.alumno a " +
					"WHERE (:pagado IS NULL OR " +
					"(:pagado = true AND pa.pagado = true) OR " +
					"(:pagado = false AND (pa.pagado = false OR pa.pagado IS NULL))) " +
					"AND (:deporte IS NULL OR ad.deporte = :deporte OR UPPER(pa.concepto) LIKE CONCAT('%', :deporteNombre, '%')) " +
					"AND (:ano IS NULL OR " +
					"FUNCTION('YEAR', COALESCE(pa.fechaAsignacion, pa.fechaPago)) = :ano OR " +
					"(pa.fechaAsignacion IS NULL AND pa.fechaPago IS NULL AND :anoTexto IS NOT NULL AND UPPER(pa.concepto) LIKE CONCAT('%', :anoTexto, '%'))) " +
					"AND (:mes IS NULL OR " +
					"FUNCTION('MONTH', COALESCE(pa.fechaAsignacion, pa.fechaPago)) = :mes OR " +
					"(pa.fechaAsignacion IS NULL AND pa.fechaPago IS NULL AND :mesNombre IS NOT NULL AND UPPER(pa.concepto) LIKE CONCAT('%', :mesNombre, '%'))) " +
					"AND (:texto IS NULL OR " +
					"UPPER(COALESCE(pa.concepto, '')) LIKE :texto OR " +
					"UPPER(COALESCE(pa.notas, '')) LIKE :texto OR " +
					"UPPER(COALESCE(a.nombre, '')) LIKE :texto OR " +
					"UPPER(COALESCE(a.apellidos, '')) LIKE :texto OR " +
					"UPPER(CONCAT(COALESCE(a.nombre, ''), ' ', COALESCE(a.apellidos, ''))) LIKE :texto OR " +
					"UPPER(COALESCE(adAlumno.nombre, '')) LIKE :texto OR " +
					"UPPER(COALESCE(adAlumno.apellidos, '')) LIKE :texto OR " +
					"UPPER(CONCAT(COALESCE(adAlumno.nombre, ''), ' ', COALESCE(adAlumno.apellidos, ''))) LIKE :texto)")
	Page<ProductoAlumno> findMovimientosTesoreriaPaginados(
			@Param("deporte") com.taemoi.project.entities.Deporte deporte,
			@Param("deporteNombre") String deporteNombre,
			@Param("pagado") Boolean pagado,
			@Param("texto") String texto,
			@Param("ano") Integer ano,
			@Param("anoTexto") String anoTexto,
			@Param("mes") Integer mes,
			@Param("mesNombre") String mesNombre,
			Pageable pageable);

	@Query("SELECT pa FROM ProductoAlumno pa " +
			"LEFT JOIN FETCH pa.alumnoDeporte ad " +
			"LEFT JOIN FETCH ad.alumno adAlumno " +
			"LEFT JOIN FETCH pa.alumno a " +
			"WHERE (:pagado IS NULL OR " +
			"(:pagado = true AND pa.pagado = true) OR " +
			"(:pagado = false AND (pa.pagado = false OR pa.pagado IS NULL))) " +
			"AND (:deporte IS NULL OR ad.deporte = :deporte OR UPPER(pa.concepto) LIKE CONCAT('%', :deporteNombre, '%')) " +
			"AND (:ano IS NULL OR " +
			"FUNCTION('YEAR', COALESCE(pa.fechaAsignacion, pa.fechaPago)) = :ano OR " +
			"(pa.fechaAsignacion IS NULL AND pa.fechaPago IS NULL AND :anoTexto IS NOT NULL AND UPPER(pa.concepto) LIKE CONCAT('%', :anoTexto, '%'))) " +
			"AND (:mes IS NULL OR " +
			"FUNCTION('MONTH', COALESCE(pa.fechaAsignacion, pa.fechaPago)) = :mes OR " +
			"(pa.fechaAsignacion IS NULL AND pa.fechaPago IS NULL AND :mesNombre IS NOT NULL AND UPPER(pa.concepto) LIKE CONCAT('%', :mesNombre, '%'))) " +
			"AND (:texto IS NULL OR " +
			"UPPER(COALESCE(pa.concepto, '')) LIKE :texto OR " +
			"UPPER(COALESCE(pa.notas, '')) LIKE :texto OR " +
			"UPPER(COALESCE(a.nombre, '')) LIKE :texto OR " +
			"UPPER(COALESCE(a.apellidos, '')) LIKE :texto OR " +
			"UPPER(CONCAT(COALESCE(a.nombre, ''), ' ', COALESCE(a.apellidos, ''))) LIKE :texto OR " +
			"UPPER(COALESCE(adAlumno.nombre, '')) LIKE :texto OR " +
			"UPPER(COALESCE(adAlumno.apellidos, '')) LIKE :texto OR " +
			"UPPER(CONCAT(COALESCE(adAlumno.nombre, ''), ' ', COALESCE(adAlumno.apellidos, ''))) LIKE :texto) " +
			"ORDER BY COALESCE(pa.fechaAsignacion, pa.fechaPago) DESC, pa.id DESC")
	List<ProductoAlumno> findMovimientosTesoreriaFiltrados(
			@Param("deporte") com.taemoi.project.entities.Deporte deporte,
			@Param("deporteNombre") String deporteNombre,
			@Param("pagado") Boolean pagado,
			@Param("texto") String texto,
			@Param("ano") Integer ano,
			@Param("anoTexto") String anoTexto,
			@Param("mes") Integer mes,
			@Param("mesNombre") String mesNombre);

	@Query("SELECT COUNT(pa) " +
			"FROM ProductoAlumno pa " +
			"LEFT JOIN pa.alumnoDeporte ad " +
			"WHERE (:pagado IS NULL OR " +
			"(:pagado = true AND pa.pagado = true) OR " +
			"(:pagado = false AND (pa.pagado = false OR pa.pagado IS NULL))) " +
			"AND (:deporte IS NULL OR ad.deporte = :deporte OR UPPER(pa.concepto) LIKE CONCAT('%', :deporteNombre, '%')) " +
			"AND (:ano IS NULL OR " +
			"FUNCTION('YEAR', COALESCE(pa.fechaAsignacion, pa.fechaPago)) = :ano OR " +
			"(pa.fechaAsignacion IS NULL AND pa.fechaPago IS NULL AND :anoTexto IS NOT NULL AND UPPER(pa.concepto) LIKE CONCAT('%', :anoTexto, '%'))) " +
			"AND (:mes IS NULL OR " +
			"FUNCTION('MONTH', COALESCE(pa.fechaAsignacion, pa.fechaPago)) = :mes OR " +
			"(pa.fechaAsignacion IS NULL AND pa.fechaPago IS NULL AND :mesNombre IS NOT NULL AND UPPER(pa.concepto) LIKE CONCAT('%', :mesNombre, '%')))")
	Long contarMovimientosTesoreria(
			@Param("deporte") com.taemoi.project.entities.Deporte deporte,
			@Param("deporteNombre") String deporteNombre,
			@Param("pagado") Boolean pagado,
			@Param("ano") Integer ano,
			@Param("anoTexto") String anoTexto,
			@Param("mes") Integer mes,
			@Param("mesNombre") String mesNombre);

	@Query("SELECT COALESCE(SUM(COALESCE(pa.precio, 0)), 0) " +
			"FROM ProductoAlumno pa " +
			"LEFT JOIN pa.alumnoDeporte ad " +
			"WHERE (:pagado IS NULL OR " +
			"(:pagado = true AND pa.pagado = true) OR " +
			"(:pagado = false AND (pa.pagado = false OR pa.pagado IS NULL))) " +
			"AND (:deporte IS NULL OR ad.deporte = :deporte OR UPPER(pa.concepto) LIKE CONCAT('%', :deporteNombre, '%')) " +
			"AND (:ano IS NULL OR " +
			"FUNCTION('YEAR', COALESCE(pa.fechaAsignacion, pa.fechaPago)) = :ano OR " +
			"(pa.fechaAsignacion IS NULL AND pa.fechaPago IS NULL AND :anoTexto IS NOT NULL AND UPPER(pa.concepto) LIKE CONCAT('%', :anoTexto, '%'))) " +
			"AND (:mes IS NULL OR " +
			"FUNCTION('MONTH', COALESCE(pa.fechaAsignacion, pa.fechaPago)) = :mes OR " +
			"(pa.fechaAsignacion IS NULL AND pa.fechaPago IS NULL AND :mesNombre IS NOT NULL AND UPPER(pa.concepto) LIKE CONCAT('%', :mesNombre, '%')))")
	Double sumarImporteMovimientosTesoreria(
			@Param("deporte") com.taemoi.project.entities.Deporte deporte,
			@Param("deporteNombre") String deporteNombre,
			@Param("pagado") Boolean pagado,
			@Param("ano") Integer ano,
			@Param("anoTexto") String anoTexto,
			@Param("mes") Integer mes,
			@Param("mesNombre") String mesNombre);

	@Query("SELECT COUNT(DISTINCT COALESCE(a.id, adAlumno.id)) " +
			"FROM ProductoAlumno pa " +
			"LEFT JOIN pa.alumno a " +
			"LEFT JOIN pa.alumnoDeporte ad " +
			"LEFT JOIN ad.alumno adAlumno " +
			"WHERE (pa.pagado = false OR pa.pagado IS NULL) " +
			"AND (:deporte IS NULL OR ad.deporte = :deporte OR UPPER(pa.concepto) LIKE CONCAT('%', :deporteNombre, '%')) " +
			"AND (:ano IS NULL OR " +
			"FUNCTION('YEAR', COALESCE(pa.fechaAsignacion, pa.fechaPago)) = :ano OR " +
			"(pa.fechaAsignacion IS NULL AND pa.fechaPago IS NULL AND :anoTexto IS NOT NULL AND UPPER(pa.concepto) LIKE CONCAT('%', :anoTexto, '%'))) " +
			"AND (:mes IS NULL OR " +
			"FUNCTION('MONTH', COALESCE(pa.fechaAsignacion, pa.fechaPago)) = :mes OR " +
			"(pa.fechaAsignacion IS NULL AND pa.fechaPago IS NULL AND :mesNombre IS NOT NULL AND UPPER(pa.concepto) LIKE CONCAT('%', :mesNombre, '%')))")
	Long contarAlumnosConPendientesTesoreria(
			@Param("deporte") com.taemoi.project.entities.Deporte deporte,
			@Param("deporteNombre") String deporteNombre,
			@Param("ano") Integer ano,
			@Param("anoTexto") String anoTexto,
			@Param("mes") Integer mes,
			@Param("mesNombre") String mesNombre);

	@Query("SELECT DISTINCT FUNCTION('YEAR', COALESCE(pa.fechaAsignacion, pa.fechaPago)) " +
			"FROM ProductoAlumno pa " +
			"WHERE COALESCE(pa.fechaAsignacion, pa.fechaPago) IS NOT NULL " +
			"ORDER BY FUNCTION('YEAR', COALESCE(pa.fechaAsignacion, pa.fechaPago))")
	List<Integer> findAniosDistintosTesoreriaConFecha();

	@Query("SELECT pa.concepto FROM ProductoAlumno pa " +
			"WHERE pa.fechaAsignacion IS NULL AND pa.fechaPago IS NULL AND pa.concepto IS NOT NULL")
	List<String> findConceptosTesoreriaSinFecha();

	List<ProductoAlumno> findByAlumnoDeporteId(Long alumnoDeporteId);

	List<ProductoAlumno> findByProductoId(Long productoId);
}
