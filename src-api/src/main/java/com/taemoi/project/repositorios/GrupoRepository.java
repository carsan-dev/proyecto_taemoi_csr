package com.taemoi.project.repositorios;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entidades.Grupo;

/**
 * Repositorio para la entidad Grupo.
 */
@Repository
public interface GrupoRepository extends JpaRepository<Grupo, Long> {
    Optional<Grupo> findByNombre(String nombre);
}
