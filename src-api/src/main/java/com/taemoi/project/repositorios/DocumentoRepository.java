package com.taemoi.project.repositorios;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.taemoi.project.entidades.Documento;

public interface DocumentoRepository extends JpaRepository<Documento, Long>{
	List<Documento> findByAlumnoId(Long alumnoId);
}
