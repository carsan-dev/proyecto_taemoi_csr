package com.taemoi.project.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.taemoi.project.entities.Convocatoria;

public interface ConvocatoriaRepository extends JpaRepository<Convocatoria, Long> {

	@Query("SELECT c FROM Convocatoria c WHERE c.fechaConvocatoria = CURRENT_DATE AND c.disciplina.codigo = :deporte")
	Optional<Convocatoria> findConvocatoriaActualPorDeporte(@Param("deporte") String deporte);

	@Query("SELECT c FROM Convocatoria c WHERE c.disciplina.codigo = :deporte")
	List<Convocatoria> findByDeporte(@Param("deporte") String deporte);
}
