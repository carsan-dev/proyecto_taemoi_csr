package com.taemoi.project.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.taemoi.project.entities.Organizacion;

public interface OrganizacionRepository extends JpaRepository<Organizacion, Long> {

	List<Organizacion> findByActivaTrueOrderByIdAsc();

	Optional<Organizacion> findBySlug(String slug);
}
