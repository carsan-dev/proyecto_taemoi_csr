package com.taemoi.project.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entities.AlumnoDeporte;

/**
 * Inscripciones por disciplina. Desde V28 el «deporte» de los parámetros es el código de la disciplina
 * ({@code ad.disciplina.codigo}); la columna heredada {@code deporte} ya no existe.
 */
@Repository
public interface AlumnoDeporteRepository extends JpaRepository<AlumnoDeporte, Long> {

	/**
	 * Encuentra todos los deportes de un alumno específico
	 */
	List<AlumnoDeporte> findByAlumnoId(Long alumnoId);

	/**
	 * Encuentra un deporte específico de un alumno
	 */
	@Query("SELECT ad FROM AlumnoDeporte ad WHERE ad.alumno.id = :alumnoId AND ad.disciplina.codigo = :deporte")
	Optional<AlumnoDeporte> findByAlumnoIdAndDeporte(@Param("alumnoId") Long alumnoId, @Param("deporte") String deporte);

	/**
	 * Encuentra todos los alumnos que practican un deporte específico
	 */
	@Query("SELECT ad FROM AlumnoDeporte ad WHERE ad.disciplina.codigo = :deporte")
	List<AlumnoDeporte> findByDeporte(@Param("deporte") String deporte);

	@Query("SELECT COUNT(ad) > 0 FROM AlumnoDeporte ad " +
	       "WHERE ad.alumno.id = :alumnoId AND ad.disciplina.codigo = :deporte AND ad.activo = true")
	boolean existsByAlumnoIdAndDeporteAndActivoTrue(@Param("alumnoId") Long alumnoId, @Param("deporte") String deporte);

	/**
	 * Encuentra todos los deportes de un alumno con grado y categoria cargados (evita N+1)
	 */
	@Query("SELECT ad FROM AlumnoDeporte ad " +
		   "LEFT JOIN FETCH ad.grado " +
		   "LEFT JOIN FETCH ad.categoria " +
		   "WHERE ad.alumno.id = :alumnoId")
	List<AlumnoDeporte> findByAlumnoIdWithGrado(@Param("alumnoId") Long alumnoId);

	/**
	 * Encuentra deportes de un alumno con grado, alumno y categoria cargados (para conversión a DTO)
	 */
	@Query("SELECT ad FROM AlumnoDeporte ad " +
		   "LEFT JOIN FETCH ad.grado " +
		   "LEFT JOIN FETCH ad.alumno " +
		   "LEFT JOIN FETCH ad.categoria " +
		   "WHERE ad.alumno.id = :alumnoId")
	List<AlumnoDeporte> findByAlumnoIdWithRelaciones(@Param("alumnoId") Long alumnoId);

	/**
	 * Encuentra todos los alumnos aptos para examen en un deporte con todas las relaciones
	 */
	@Query("SELECT ad FROM AlumnoDeporte ad " +
		   "LEFT JOIN FETCH ad.grado " +
		   "LEFT JOIN FETCH ad.alumno " +
		   "LEFT JOIN FETCH ad.categoria " +
		   "WHERE ad.disciplina.codigo = :deporte AND ad.aptoParaExamen = true AND ad.activo = true")
	List<AlumnoDeporte> findAptosParaExamenPorDeporte(@Param("deporte") String deporte);

	/**
	 * Cuenta alumnos por deporte con deporte activo
	 */
	@Query("SELECT COUNT(ad) FROM AlumnoDeporte ad WHERE ad.disciplina.codigo = :deporte AND ad.activo = true")
	long countByDeporteAndActivoTrue(@Param("deporte") String deporte);

	long countByDisciplinaIdAndActivoTrue(Long disciplinaId);

	/**
	 * Cuenta alumnos activos por deporte (deporte activo y alumno activo)
	 */
	@Query("SELECT COUNT(ad) FROM AlumnoDeporte ad JOIN ad.alumno a " +
	       "WHERE ad.disciplina.codigo = :deporte AND ad.activo = true AND a.activo = true")
	long countActivosByDeporte(@Param("deporte") String deporte);

	/**
	 * Cuenta competidores por deporte (solo deporte activo)
	 */
	@Query("SELECT COUNT(ad) FROM AlumnoDeporte ad " +
	       "WHERE ad.disciplina.codigo = :deporte AND ad.activo = true AND ad.competidor = true")
	long countCompetidoresByDeporte(@Param("deporte") String deporte);

	/**
	 * Cuenta competidores activos por deporte (deporte activo y alumno activo)
	 */
	@Query("SELECT COUNT(ad) FROM AlumnoDeporte ad JOIN ad.alumno a " +
	       "WHERE ad.disciplina.codigo = :deporte AND ad.activo = true AND ad.competidor = true AND a.activo = true")
	long countCompetidoresActivosByDeporte(@Param("deporte") String deporte);

	/**
	 * Encuentra deportes activos por deporte con alumno cargado
	 */
	@Query("SELECT ad FROM AlumnoDeporte ad " +
	       "LEFT JOIN FETCH ad.alumno a " +
	       "WHERE ad.disciplina.codigo = :deporte AND ad.activo = true")
	List<AlumnoDeporte> findActivosByDeporteWithAlumno(@Param("deporte") String deporte);

	@Query("SELECT ad FROM AlumnoDeporte ad " +
	       "JOIN FETCH ad.alumno a " +
	       "WHERE ad.disciplina.codigo = :deporte AND ad.activo = true AND a.activo = true")
	List<AlumnoDeporte> findActivosConAlumnoActivoByDeporte(@Param("deporte") String deporte);

	/**
	 * Encuentra competidores activos por deporte con alumno cargado
	 */
	@Query("SELECT ad FROM AlumnoDeporte ad " +
	       "LEFT JOIN FETCH ad.alumno a " +
	       "WHERE ad.disciplina.codigo = :deporte AND ad.activo = true AND ad.competidor = true")
	List<AlumnoDeporte> findCompetidoresByDeporteWithAlumno(@Param("deporte") String deporte);

	/**
	 * Encuentra un AlumnoDeporte específico con todas las relaciones cargadas
	 */
	@Query("SELECT ad FROM AlumnoDeporte ad " +
		   "LEFT JOIN FETCH ad.grado " +
		   "LEFT JOIN FETCH ad.alumno " +
		   "LEFT JOIN FETCH ad.categoria " +
		   "WHERE ad.id = :id")
	Optional<AlumnoDeporte> findByIdWithRelaciones(@Param("id") Long id);

	/**
	 * Cuenta cuántos deportes activos tiene un alumno
	 */
	long countByAlumnoIdAndActivoTrue(Long alumnoId);

	/**
	 * Verifica si un alumno ya tiene asignado un deporte
	 */
	@Query("SELECT COUNT(ad) > 0 FROM AlumnoDeporte ad WHERE ad.alumno.id = :alumnoId AND ad.disciplina.codigo = :deporte")
	boolean existsByAlumnoIdAndDeporte(@Param("alumnoId") Long alumnoId, @Param("deporte") String deporte);

	/**
	 * Encuentra todos los alumnos con grado en deportes específicos (para informes)
	 */
	@Query("SELECT ad FROM AlumnoDeporte ad " +
		   "LEFT JOIN FETCH ad.grado " +
		   "LEFT JOIN FETCH ad.alumno " +
		   "LEFT JOIN FETCH ad.categoria " +
		   "WHERE ad.grado IS NOT NULL AND ad.disciplina.codigo IN :deportes AND ad.activo = true")
	List<AlumnoDeporte> findByGradoNotNullAndDeporteIn(@Param("deportes") List<String> deportes);

	/**
	 * Encuentra todos los alumnos activos en deportes específicos con relaciones cargadas
	 */
	@Query("SELECT ad FROM AlumnoDeporte ad " +
		   "LEFT JOIN FETCH ad.grado " +
		   "LEFT JOIN FETCH ad.alumno a " +
		   "WHERE ad.disciplina.codigo IN :deportes AND ad.activo = true AND a.activo = true " +
		   "ORDER BY a.nombre, a.apellidos")
	List<AlumnoDeporte> findActivosByDeporteIn(@Param("deportes") List<String> deportes);

	/**
	 * Encuentra todos los competidores activos en deportes específicos con relaciones cargadas
	 */
	@Query("SELECT ad FROM AlumnoDeporte ad " +
		   "LEFT JOIN FETCH ad.grado " +
		   "LEFT JOIN FETCH ad.alumno a " +
		   "LEFT JOIN FETCH ad.categoria " +
		   "WHERE ad.disciplina.codigo IN :deportes AND ad.activo = true AND a.activo = true AND ad.competidor = true " +
		   "ORDER BY ad.disciplina.codigo, a.nombre, a.apellidos")
	List<AlumnoDeporte> findCompetidoresActivosByDeporteIn(@Param("deportes") List<String> deportes);
}
