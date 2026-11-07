package com.taemoi.project.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.taemoi.project.entities.OrganizacionDominio;

/** Tabla global ({@link OrganizacionDominio}): no se filtra por escuela. La lee {@code ResolutorEscuelaPorHost}. */
public interface OrganizacionDominioRepository extends JpaRepository<OrganizacionDominio, Long> {
}
