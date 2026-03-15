package com.taemoi.project.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entities.Imagen;

/**
 * Repositorio para la entidad Imagen.
 */
@Repository
public interface ImagenRepository extends JpaRepository<Imagen, Long> {

}
