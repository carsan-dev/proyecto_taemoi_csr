package com.taemoi.project.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entities.AlumnoDeporte;

import jakarta.persistence.LockModeType;

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
	 * Bloquea (SELECT ... FOR UPDATE) la inscripción por id, sin joins (no bloquea la disciplina ni el alumno). Solo en
	 * transacción; pieza E7-3 (D7-17): el pase del profesor la bloquea antes de buscar un pase pendiente.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT ad FROM AlumnoDeporte ad WHERE ad.id = :id")
	Optional<AlumnoDeporte> bloquearPorId(@Param("id") Long id);

	// ------------------------------------------------------------------ rallas hacia el examen (V32_8)
	// Sentencias atómicas (la columna medias_rallas no se escribe desde la entidad): cada una es UN UPDATE de UNA fila,
	// con la condición dentro, así que dos pasos a la vez se aplican los dos, uno tras otro (bloqueo de fila de InnoDB),
	// y ninguno deja el valor fuera de 0 a 10. Nativas y con organizacion_id explícito: el id ya viene de una consulta
	// acotada por @TenantId y la condición lo vuelve a comprobar. Devuelven las filas cambiadas (0 o 1).

	/**
	 * Suma {@code paso} (−2, −1, +1 o +2) si la inscripción sigue en el grado leído y el resultado queda de 0 a 10. 0
	 * filas: fuera de rango o el grado cambió entre medias.
	 */
	@Modifying
	@Query(value = """
			UPDATE alumno_deporte SET medias_rallas = medias_rallas + :paso
			WHERE id = :id AND organizacion_id = :organizacion AND grado_id = :grado
			  AND medias_rallas + :paso BETWEEN 0 AND 10""", nativeQuery = true)
	int sumarMediasRallas(@Param("id") Long id, @Param("organizacion") Long organizacion, @Param("grado") Long grado,
			@Param("paso") int paso);

	/** «Marcar apto» desde la hoja: apto y rallas a 0 a la vez, solo si tiene las 10 medias y sigue en ese grado. */
	@Modifying
	@Query(value = """
			UPDATE alumno_deporte SET apto_para_examen = TRUE, medias_rallas = 0
			WHERE id = :id AND organizacion_id = :organizacion AND grado_id = :grado AND medias_rallas = 10""",
			nativeQuery = true)
	int marcarAptoConRallasCompletas(@Param("id") Long id, @Param("organizacion") Long organizacion,
			@Param("grado") Long grado);

	/** Rallas a 0 (cambio de grado). 0 filas: ya estaban en 0. */
	@Modifying
	@Query(value = """
			UPDATE alumno_deporte SET medias_rallas = 0
			WHERE id = :id AND organizacion_id = :organizacion AND medias_rallas <> 0""", nativeQuery = true)
	int ponerMediasRallasACero(@Param("id") Long id, @Param("organizacion") Long organizacion);

	/** Medias rallas, grado y apto confirmados de la inscripción (escuela activa). */
	@Query("SELECT ad.mediasRallas, g.id, ad.aptoParaExamen FROM AlumnoDeporte ad LEFT JOIN ad.grado g WHERE ad.id = :id")
	List<Object[]> leerRallas(@Param("id") Long id);

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
	 * Como {@link #countByDisciplinaIdAndActivoTrue}, solo para esos alumnos (alcance del responsable de sede,
	 * entrega 6-1c). Una fila por disciplina con inscripciones activas: {@code [disciplinaId, total]}.
	 */
	@Query("SELECT ad.disciplina.id, COUNT(ad) FROM AlumnoDeporte ad WHERE ad.activo = true AND ad.alumno.id IN :alumnos"
			+ " GROUP BY ad.disciplina.id")
	List<Object[]> contarActivosPorDisciplinaDeAlumnos(@Param("alumnos") java.util.Collection<Long> alumnos);

	/**
	 * Pieza 9-5: inscripciones activas en el deporte, solo de esos alumnos (alcance del responsable de sede). Con
	 * {@code soloCompetidores}, las de competidor; con {@code soloAlumnoActivo}, las de alumnos activos. {@code alumnos} no
	 * puede estar vacío.
	 */
	@Query("SELECT COUNT(ad) FROM AlumnoDeporte ad JOIN ad.alumno a "
			+ "WHERE ad.disciplina.codigo = :deporte AND ad.activo = true AND a.id IN :alumnos "
			+ "AND (:soloCompetidores = false OR ad.competidor = true) "
			+ "AND (:soloAlumnoActivo = false OR a.activo = true)")
	long contarPorDeporteDeAlumnos(@Param("deporte") String deporte, @Param("soloCompetidores") boolean soloCompetidores,
			@Param("soloAlumnoActivo") boolean soloAlumnoActivo, @Param("alumnos") java.util.Collection<Long> alumnos);

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
