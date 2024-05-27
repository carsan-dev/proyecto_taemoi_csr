package com.taemoi.project.repositorios;

import org.springframework.data.jpa.repository.JpaRepository;

import com.taemoi.project.entidades.Evento;

public interface EventoRepository extends JpaRepository<Evento, Long> {
}