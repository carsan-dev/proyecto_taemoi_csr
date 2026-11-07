package com.taemoi.project.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.taemoi.project.entities.WebEscuela;

/** Web por plantilla de cada escuela (V32_5), por id de organización. Se lee siempre con la escuela activa. */
public interface WebEscuelaRepository extends JpaRepository<WebEscuela, Long> {
}
