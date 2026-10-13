package com.taemoi.project.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entities.Tarifa;

/** Tarifas de la organización activa (filtradas por {@code @TenantId}). */
@Repository
public interface TarifaRepository extends JpaRepository<Tarifa, Long> {

	Optional<Tarifa> findByCodigo(String codigo);

	/** Orden de alta (en Taemoi, el de {@code TarifaConfig.obtenerTodasLasCuantias}). */
	List<Tarifa> findByActivaTrueOrderByIdAsc();
}
