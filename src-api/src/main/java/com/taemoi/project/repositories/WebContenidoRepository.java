package com.taemoi.project.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.taemoi.project.entities.WebContenido;

/** Versión del contenido de la web de cada escuela (V32_9), por id de organización. Se lee con la escuela activa. */
public interface WebContenidoRepository extends JpaRepository<WebContenido, Long> {
}
