package com.taemoi.project.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entities.RegistroPendiente;

@Repository
public interface RegistroPendienteRepository extends JpaRepository<RegistroPendiente, Long> {
	Optional<RegistroPendiente> findByEmail(String email);

	Optional<RegistroPendiente> findByTokenHash(String tokenHash);

	/** Solo para el borrado completo del alumno: un registro pendiente hacia un alumno borrado no puede completarse. */
	@Modifying(flushAutomatically = true)
	@Query("DELETE FROM RegistroPendiente r WHERE r.alumnoId = :alumnoId")
	int borrarDelAlumno(@Param("alumnoId") Long alumnoId);
}
