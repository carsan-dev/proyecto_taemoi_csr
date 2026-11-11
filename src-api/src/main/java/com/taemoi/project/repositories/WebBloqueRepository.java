package com.taemoi.project.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.taemoi.project.entities.WebBloque;

/** Bloques de la web de la escuela activa ({@code @TenantId}: Hibernate filtra por organización). Pieza W12a. */
public interface WebBloqueRepository extends JpaRepository<WebBloque, Long> {

	List<WebBloque> findAllByOrderByOrdenAscClaveAsc();
}
