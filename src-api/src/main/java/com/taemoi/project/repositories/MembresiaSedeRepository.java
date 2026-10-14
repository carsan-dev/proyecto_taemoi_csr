package com.taemoi.project.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.taemoi.project.entities.MembresiaSede;

/**
 * {@link MembresiaSede} no se filtra por organización automáticamente: cada método filtra por membresía (que es
 * de una sola organización). No añadir consultas sin ese filtro.
 */
public interface MembresiaSedeRepository extends JpaRepository<MembresiaSede, MembresiaSede.Clave> {

	List<MembresiaSede> findByMembresiaId(Long membresiaId);
}
