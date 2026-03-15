package com.taemoi.project.repositories;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.taemoi.project.entities.AuditoriaEvento;

public interface AuditoriaEventoRepository
		extends JpaRepository<AuditoriaEvento, Long>, JpaSpecificationExecutor<AuditoriaEvento> {

	@Query("SELECT DISTINCT ae.modulo FROM AuditoriaEvento ae WHERE ae.modulo IS NOT NULL AND ae.modulo <> '' ORDER BY ae.modulo")
	List<String> findDistinctModulos();

	long deleteByFechaEventoBefore(Date fechaLimite);
}
