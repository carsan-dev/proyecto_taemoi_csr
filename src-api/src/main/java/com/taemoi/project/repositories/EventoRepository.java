package com.taemoi.project.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.taemoi.project.entities.Evento;

public interface EventoRepository extends JpaRepository<Evento, Long> {
}