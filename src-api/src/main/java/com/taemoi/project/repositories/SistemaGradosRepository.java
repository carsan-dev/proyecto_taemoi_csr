package com.taemoi.project.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entities.SistemaGrados;

/** Sistemas de grados de la organización activa (filtrados por {@code @TenantId}). */
@Repository
public interface SistemaGradosRepository extends JpaRepository<SistemaGrados, Long> {

	java.util.List<SistemaGrados> findAllByOrderByIdAsc();
}
