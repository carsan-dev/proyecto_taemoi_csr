package com.taemoi.project.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.taemoi.project.entities.Documento;

public interface DocumentoRepository extends JpaRepository<Documento, Long>{
	List<Documento> findByAlumnoId(Long alumnoId);
	List<Documento> findByAlumnoIdOrderByNombreAsc(Long alumnoId);
	List<Documento> findByEventoId(Long eventoId);
}
