package com.taemoi.project.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.taemoi.project.entities.Consentimiento;
import com.taemoi.project.entities.TipoConsentimiento;

/** Consentimientos de la organización activa. Solo se añaden filas (salvo al borrar al alumno). */
public interface ConsentimientoRepository extends JpaRepository<Consentimiento, Long> {

	/** Del último registrado al primero. */
	List<Consentimiento> findByAlumnoIdOrderByIdDesc(Long alumnoId);

	/** El vigente de un tipo: el último registrado (nunca se deshace una retirada posterior). */
	Optional<Consentimiento> findFirstByAlumnoIdAndTipoOrderByIdDesc(Long alumnoId, TipoConsentimiento tipo);

	boolean existsByPreinscripcionIdAndTipo(Long preinscripcionId, TipoConsentimiento tipo);

	boolean existsByDocumentoId(Long documentoId);

	/** Solo para el borrado completo del alumno (supresión de sus datos personales). */
	@Modifying(flushAutomatically = true, clearAutomatically = false)
	@Query("DELETE FROM Consentimiento c WHERE c.alumnoId = :alumnoId")
	int borrarDelAlumno(@Param("alumnoId") Long alumnoId);
}
