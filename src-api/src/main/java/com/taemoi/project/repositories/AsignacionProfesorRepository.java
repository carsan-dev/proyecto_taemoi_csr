package com.taemoi.project.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.taemoi.project.entities.AsignacionProfesor;

/**
 * {@link AsignacionProfesor} no se filtra por organización automáticamente: cada método filtra explícitamente
 * por organización. No añadir consultas sin ese filtro.
 */
public interface AsignacionProfesorRepository extends JpaRepository<AsignacionProfesor, AsignacionProfesor.Clave> {

	List<AsignacionProfesor> findByUsuarioIdAndOrganizacionId(Long usuarioId, Long organizacionId);

	List<AsignacionProfesor> findByGrupoIdAndOrganizacionId(Long grupoId, Long organizacionId);

	/** Pieza E7-4: la asignación de una cuenta a un grupo, en una escuela. */
	Optional<AsignacionProfesor> findByUsuarioIdAndGrupoIdAndOrganizacionId(Long usuarioId, Long grupoId,
			Long organizacionId);

	/** Pieza E7-4: borra la asignación de una cuenta a un grupo, en una escuela; devuelve las filas borradas (0 o 1). */
	@Modifying(flushAutomatically = true)
	@Query("""
			DELETE FROM AsignacionProfesor a
			WHERE a.usuarioId = :usuarioId AND a.grupoId = :grupoId AND a.organizacionId = :organizacionId""")
	int borrar(@Param("usuarioId") Long usuarioId, @Param("grupoId") Long grupoId,
			@Param("organizacionId") Long organizacionId);
}
