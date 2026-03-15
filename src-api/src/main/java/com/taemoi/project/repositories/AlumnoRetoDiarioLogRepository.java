package com.taemoi.project.repositories;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entities.AlumnoRetoDiarioLog;

@Repository
public interface AlumnoRetoDiarioLogRepository extends JpaRepository<AlumnoRetoDiarioLog, Long> {

	boolean existsByAlumnoIdAndFechaCompletado(Long alumnoId, LocalDate fechaCompletado);

	boolean existsByAnioIsoAndSemanaIso(Integer anioIso, Integer semanaIso);

	@Query("SELECT l.alumno.id AS alumnoId, COUNT(l.id) AS diasCompletados, MAX(l.fechaCompletado) AS ultimaFechaCompletado, "
			+ "MAX(l.id) AS ultimaMarcaCompletadoId "
			+ "FROM AlumnoRetoDiarioLog l "
			+ "WHERE l.anioIso = :anioIso AND l.semanaIso = :semanaIso AND l.alumno.id IN :alumnoIds "
			+ "GROUP BY l.alumno.id")
	List<AlumnoRetoDiarioScoreProjection> obtenerPuntuacionesSemana(
			@Param("anioIso") Integer anioIso,
			@Param("semanaIso") Integer semanaIso,
			@Param("alumnoIds") List<Long> alumnoIds);

	@Query("SELECT l.alumno.id AS alumnoId, l.fechaCompletado AS fechaCompletado, l.id AS logId "
			+ "FROM AlumnoRetoDiarioLog l "
			+ "WHERE l.alumno.id IN :alumnoIds "
			+ "ORDER BY l.alumno.id ASC, l.fechaCompletado ASC, l.id ASC")
	List<AlumnoRetoDiarioFechaProjection> obtenerFechasCompletadoHistorico(
			@Param("alumnoIds") List<Long> alumnoIds);

	interface AlumnoRetoDiarioScoreProjection {
		Long getAlumnoId();

		Long getDiasCompletados();

		LocalDate getUltimaFechaCompletado();

		Long getUltimaMarcaCompletadoId();
	}

	interface AlumnoRetoDiarioFechaProjection {
		Long getAlumnoId();

		LocalDate getFechaCompletado();

		Long getLogId();
	}
}
