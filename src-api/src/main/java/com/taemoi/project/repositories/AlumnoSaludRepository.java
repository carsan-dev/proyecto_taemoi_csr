package com.taemoi.project.repositories;

import java.time.Instant;

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

	/**
	 * Quita los tipos de discapacidad y el texto de «Otra» sin leer la fila. Sin fila no hace nada: nunca crea una. Solo
	 * la escuela activa (como toda consulta JPQL sobre una entidad con {@code @TenantId}). Sin {@code clearAutomatically}:
	 * quien llama tiene al alumno a medio modificar y no debe perderlo.
	 */
	@Modifying
	@Query("""
			UPDATE AlumnoSalud s SET s.discapacidadTiposCifrados = NULL, s.discapacidadOtraCifrada = NULL,
			s.actualizadoEn = :ahora WHERE s.alumnoId = :alumnoId""")
	int limpiarDiscapacidad(@Param("alumnoId") Long alumnoId, @Param("ahora") Instant ahora);
}
