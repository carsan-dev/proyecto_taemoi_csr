package com.taemoi.project.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import com.taemoi.project.entities.Grupo;

/**
 * Repositorio para la entidad Grupo.
 */
@Repository
public interface GrupoRepository extends JpaRepository<Grupo, Long> {
	Optional<Grupo> findByNombre(String nombre);

	List<Grupo> findByTipoIgnoreCase(String tipo);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@org.springframework.data.jpa.repository.Query("select g from Grupo g where g.id = :id")
	Optional<Grupo> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
