package com.taemoi.project.repositorios;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entidades.Categoria;

/**
 * Repositorio para la entidad Categoría. Proporciona método para buscar por nombre.
 */
@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
	
    /**
     * Recupera una entidad Categoria de la base de datos por su atributo nombre.
     * 
     * @param nombre El valor del atributo nombre a buscar.
     * @return La entidad Categoria con el nombre especificado, o null si no se encuentra.
     */
	Categoria findByNombre(String nombre);

	boolean existsByNombre(String nombre);
}
