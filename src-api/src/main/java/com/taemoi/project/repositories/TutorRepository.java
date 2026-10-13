package com.taemoi.project.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.taemoi.project.entities.Tutor;

/** Tutores de la organización activa (filtrados por {@code @TenantId}). */
public interface TutorRepository extends JpaRepository<Tutor, Long> {

	List<Tutor> findByAlumnoIdOrderByOrdenAscIdAsc(Long alumnoId);

	List<Tutor> findByAlumnoIdAndResponsableLegalTrueOrderByOrdenAscIdAsc(Long alumnoId);

	Optional<Tutor> findByIdAndAlumnoId(Long id, Long alumnoId);

	@Query("SELECT COALESCE(MAX(t.orden), 0) FROM Tutor t WHERE t.alumno.id = :alumnoId")
	int maxOrden(@Param("alumnoId") Long alumnoId);

	/** Solo para el borrado completo del alumno. */
	@Modifying(flushAutomatically = true)
	@Query("DELETE FROM Tutor t WHERE t.alumno.id = :alumnoId")
	int borrarDelAlumno(@Param("alumnoId") Long alumnoId);
}
