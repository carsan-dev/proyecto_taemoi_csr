package com.taemoi.project.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.taemoi.project.entities.ConfiguracionOrganizacion;

/** Filtrado automáticamente por la organización activa ({@code @TenantId}). */
public interface ConfiguracionOrganizacionRepository extends JpaRepository<ConfiguracionOrganizacion, Long> {

	Optional<ConfiguracionOrganizacion> findByClave(String clave);
}
