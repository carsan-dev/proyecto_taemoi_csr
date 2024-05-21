package com.taemoi.project.repositorios;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entidades.Examen;

/**
 * Repositorio para la entidad Examen.
 */
@Repository
public interface ExamenRepository  extends JpaRepository<Examen, Long>{

}
