package com.taemoi.project.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entities.ConfiguracionSistema;

/**
 * Repositorio para la entidad ConfiguracionSistema.
 */
@Repository
public interface ConfiguracionSistemaRepository extends JpaRepository<ConfiguracionSistema, Long> {
    Optional<ConfiguracionSistema> findByClave(String clave);
}
