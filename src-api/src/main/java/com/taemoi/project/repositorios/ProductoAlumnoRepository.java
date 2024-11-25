package com.taemoi.project.repositorios;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.taemoi.project.entidades.ProductoAlumno;

public interface ProductoAlumnoRepository extends JpaRepository<ProductoAlumno, Long> {
	List<ProductoAlumno> findByAlumnoId(Long alumnoId);
}
