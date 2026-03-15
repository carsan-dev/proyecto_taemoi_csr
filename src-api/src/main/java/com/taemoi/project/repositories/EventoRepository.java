package com.taemoi.project.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.taemoi.project.entities.Evento;

public interface EventoRepository extends JpaRepository<Evento, Long> {
	@Query("select coalesce(max(e.orden), 0) from Evento e")
	Integer findMaxOrden();

	List<Evento> findByVisibleTrue();
	List<Evento> findByVisibleTrueOrderByOrdenAsc();
	List<Evento> findAllByOrderByOrdenAsc();
}
