package com.taemoi.project.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.Deporte;

@Repository
public interface AlumnoDeporteRepository extends JpaRepository<AlumnoDeporte, Long> {

	/**
	 * Encuentra todos los deportes de un alumno específico
	 */
	List<AlumnoDeporte> findByAlumnoId(Long alumnoId);

	/**
	 * Encuentra un deporte específico de un alumno
	 */
	Optional<AlumnoDeporte> findByAlumnoIdAndDeporte(Long alumnoId, Deporte deporte);

	/**
	 * Encuentra todos los alumnos que practican un deporte específico
	 */
	List<AlumnoDeporte> findByDeporte(Deporte deporte);

	/**
	 * Encuentra todos los alumnos aptos para examen en un deporte específico
	 */
	List<AlumnoDeporte> findByDeporteAndAptoParaExamenTrue(Deporte deporte);

	/**
	 * Encuentra todos los alumnos activos en un deporte específico
	 */
	List<AlumnoDeporte> findByDeporteAndActivoTrue(Deporte deporte);

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
		   "WHERE ad.deporte = :deporte AND ad.aptoParaExamen = true AND ad.activo = true")
	List<AlumnoDeporte> findAptosParaExamenPorDeporte(@Param("deporte") Deporte deporte);

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
	 * Elimina un deporte de un alumno
	 */
	void deleteByAlumnoIdAndDeporte(Long alumnoId, Deporte deporte);

	/**
	 * Verifica si un alumno ya tiene asignado un deporte
	 */
	boolean existsByAlumnoIdAndDeporte(Long alumnoId, Deporte deporte);

	/**
	 * Encuentra todos los alumnos con grado en deportes específicos (para informes)
	 */
	@Query("SELECT ad FROM AlumnoDeporte ad " +
		   "LEFT JOIN FETCH ad.grado " +
		   "LEFT JOIN FETCH ad.alumno " +
		   "LEFT JOIN FETCH ad.categoria " +
		   "WHERE ad.grado IS NOT NULL AND ad.deporte IN :deportes AND ad.activo = true")
	List<AlumnoDeporte> findByGradoNotNullAndDeporteIn(@Param("deportes") List<Deporte> deportes);
}
