package com.taemoi.project.repositorios;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.taemoi.project.entidades.AlumnoConvocatoria;

public interface AlumnoConvocatoriaRepository extends JpaRepository<AlumnoConvocatoria, Long> {
	Optional<AlumnoConvocatoria> findByProductoAlumnoId(Long productoAlumnoId);
	
	Optional<AlumnoConvocatoria> findByConvocatoriaIdAndAlumnoId(Long convocatoriaId, Long alumnoId);

	List<AlumnoConvocatoria> findByAlumnoId(Long alumnoId);
}
