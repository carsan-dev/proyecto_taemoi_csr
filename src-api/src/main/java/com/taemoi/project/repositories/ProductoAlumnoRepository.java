package com.taemoi.project.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.taemoi.project.entities.ProductoAlumno;

public interface ProductoAlumnoRepository extends JpaRepository<ProductoAlumno, Long> {
	List<ProductoAlumno> findByAlumnoId(Long alumnoId);

	Optional<ProductoAlumno> findByAlumnoIdAndProductoId(Long id, Long id2);
	
	boolean existsByConcepto(String concepto);
}
