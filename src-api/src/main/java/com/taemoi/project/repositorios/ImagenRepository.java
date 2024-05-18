package com.taemoi.project.repositorios;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entidades.Imagen;

/**
 * Repositorio para la entidad Imagen.
 */
@Repository
public interface ImagenRepository extends JpaRepository<Imagen, Long>{

}
