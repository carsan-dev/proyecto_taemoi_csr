package com.taemoi.project.repositories;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entities.FestivoSede;

/** Sedes de los festivos de la escuela activa ({@code @TenantId}). */
@Repository
public interface FestivoSedeRepository extends JpaRepository<FestivoSede, FestivoSede.Clave> {

	/** Las sedes de esos festivos. Una consulta para todos, sin N+1. */
	List<FestivoSede> findByFestivoIdIn(Collection<Long> festivoIds);
}
