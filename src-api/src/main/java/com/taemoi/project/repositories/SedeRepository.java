package com.taemoi.project.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.taemoi.project.entities.Sede;

/** Sedes de la escuela activa ({@code @TenantId}: Hibernate filtra por organización). */
public interface SedeRepository extends JpaRepository<Sede, Long> {

	List<Sede> findAllByOrderByIdAsc();

	List<Sede> findByActivaTrueOrderByIdAsc();
}
