package com.taemoi.project.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.taemoi.project.entities.AsignacionProfesor;

/**
 * {@link AsignacionProfesor} no se filtra por organización automáticamente: cada método filtra explícitamente
 * por organización. No añadir consultas sin ese filtro.
 */
public interface AsignacionProfesorRepository extends JpaRepository<AsignacionProfesor, AsignacionProfesor.Clave> {

	List<AsignacionProfesor> findByUsuarioIdAndOrganizacionId(Long usuarioId, Long organizacionId);

	List<AsignacionProfesor> findByGrupoIdAndOrganizacionId(Long grupoId, Long organizacionId);
}
