package com.taemoi.project.repositories;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.taemoi.project.entities.TarifaSede;

/** Tarifas por sede de la escuela activa ({@code @TenantId}: Hibernate filtra por organización). Entrega 6-4. */
public interface TarifaSedeRepository extends JpaRepository<TarifaSede, Long> {

	/** Todas las de la escuela, en el orden del listado de la API. */
	List<TarifaSede> findAllByOrderByTarifaIdAscSedeIdAscVigenteDesdeAsc();

	boolean existsByTarifaIdAndSedeIdAndVigenteDesde(Long tarifaId, Long sedeId, LocalDate vigenteDesde);

	/** Por id con una consulta (filtrada por la escuela activa), no con {@code findById}, como {@code SedeRepository}. */
	@Query("SELECT t FROM TarifaSede t WHERE t.id = :id")
	Optional<TarifaSede> buscarEnEscuelaActiva(@Param("id") Long id);
}
