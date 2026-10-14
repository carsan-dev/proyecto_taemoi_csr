package com.taemoi.project.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.taemoi.project.entities.UsuarioAlumno;

/**
 * {@link UsuarioAlumno} no se filtra por organización automáticamente: cada método filtra explícitamente por
 * organización (y usuario o alumno). No añadir consultas sin ese filtro.
 */
public interface UsuarioAlumnoRepository extends JpaRepository<UsuarioAlumno, UsuarioAlumno.Clave> {

	List<UsuarioAlumno> findByUsuarioIdAndOrganizacionId(Long usuarioId, Long organizacionId);

	List<UsuarioAlumno> findByAlumnoIdAndOrganizacionId(Long alumnoId, Long organizacionId);

	boolean existsByUsuarioIdAndAlumnoIdAndOrganizacionId(Long usuarioId, Long alumnoId, Long organizacionId);
}
