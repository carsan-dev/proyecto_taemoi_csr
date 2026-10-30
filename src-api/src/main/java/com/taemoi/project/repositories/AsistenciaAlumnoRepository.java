package com.taemoi.project.repositories;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entities.AsistenciaAlumno;

/** Filas de las listas guardadas de la escuela activa ({@code @TenantId}). */
@Repository
public interface AsistenciaAlumnoRepository extends JpaRepository<AsistenciaAlumno, Long> {

	List<AsistenciaAlumno> findByClaseId(Long claseId);

	/**
	 * Filas {@code [claseId, filas, presentes]} de esas clases, sin las de alumnos anonimizados (se borran al anonimizar;
	 * esto cubre un PUT en curso, como la lista). Una clase sin filas no sale.
	 */
	@Query("""
			SELECT f.claseId, COUNT(f.id), SUM(CASE WHEN f.presente = TRUE THEN 1 ELSE 0 END)
			FROM AsistenciaAlumno f JOIN Alumno a ON a.id = f.alumnoId
			WHERE f.claseId IN :clases AND a.anonimizadoEn IS NULL GROUP BY f.claseId""")
	List<Object[]> contarPorClase(@Param("clases") Collection<Long> clases);

	/**
	 * Filas {@code [alumnoId, presentes, clases]} del grupo en esas fechas, ambas incluidas, sin alumnos anonimizados
	 * (como {@link #contarPorClase}).
	 */
	@Query("""
			SELECT f.alumnoId, SUM(CASE WHEN f.presente = TRUE THEN 1 ELSE 0 END), COUNT(f.id)
			FROM AsistenciaAlumno f JOIN ClaseAsistencia c ON c.id = f.claseId JOIN Alumno a ON a.id = f.alumnoId
			WHERE c.grupoId = :grupo AND c.fecha BETWEEN :desde AND :hasta AND a.anonimizadoEn IS NULL
			GROUP BY f.alumnoId ORDER BY f.alumnoId""")
	List<Object[]> resumenDelGrupo(@Param("grupo") Long grupoId, @Param("desde") LocalDate desde,
			@Param("hasta") LocalDate hasta);

	/** Filas {@code [turnoId, fecha, horaInicio, presente]} del alumno en clases del grupo en esas fechas. */
	@Query("""
			SELECT c.turnoId, c.fecha, c.horaInicio, f.presente
			FROM AsistenciaAlumno f JOIN ClaseAsistencia c ON c.id = f.claseId
			WHERE f.alumnoId = :alumno AND c.grupoId = :grupo AND c.fecha BETWEEN :desde AND :hasta""")
	List<Object[]> filasDelAlumno(@Param("alumno") Long alumnoId, @Param("grupo") Long grupoId,
			@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

	/** Al eliminar anonimizando: su asistencia es un dato personal más y no se conserva (ver AlumnoServiceImpl). */
	@Modifying(flushAutomatically = true)
	@Query("DELETE FROM AsistenciaAlumno f WHERE f.alumnoId = :alumnoId")
	int borrarDelAlumno(@Param("alumnoId") Long alumnoId);
}
