package com.taemoi.project.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.taemoi.project.entities.Convocatoria;
import com.taemoi.project.entities.Deporte;

public interface ConvocatoriaRepository extends JpaRepository<Convocatoria, Long> {

	@Query("SELECT c FROM Convocatoria c WHERE c.fechaConvocatoria = CURRENT_DATE AND c.deporte = :deporte")
	Optional<Convocatoria> findConvocatoriaActualPorDeporte(@Param("deporte") Deporte deporte);

	List<Convocatoria> findByDeporte(Deporte deporte);
}
