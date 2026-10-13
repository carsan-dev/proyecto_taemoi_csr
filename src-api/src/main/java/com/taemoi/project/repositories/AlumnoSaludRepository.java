package com.taemoi.project.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.taemoi.project.entities.AlumnoSalud;

/**
 * Salud de los alumnos de la organización activa. Sin caché (decisión 7): no añadir
 * {@code @Cacheable} ni caché de segundo nivel. Solo debe usarlo {@code AlumnoSaludService}, que
 * audita cada lectura.
 */
public interface AlumnoSaludRepository extends JpaRepository<AlumnoSalud, Long> {

	/** Solo para el borrado completo del alumno. */
	@Modifying(flushAutomatically = true)
	@Query("DELETE FROM AlumnoSalud s WHERE s.alumnoId = :alumnoId")
	int borrarDelAlumno(@Param("alumnoId") Long alumnoId);
}
