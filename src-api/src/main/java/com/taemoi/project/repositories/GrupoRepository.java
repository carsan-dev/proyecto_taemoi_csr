package com.taemoi.project.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entities.Grupo;

/**
 * Repositorio para la entidad Grupo.
 */
@Repository
public interface GrupoRepository extends JpaRepository<Grupo, Long> {
	Optional<Grupo> findByNombre(String nombre);

	List<Grupo> findByTipoIgnoreCase(String tipo);
}
