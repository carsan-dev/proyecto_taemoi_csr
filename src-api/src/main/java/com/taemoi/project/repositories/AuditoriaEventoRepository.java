package com.taemoi.project.repositories;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.taemoi.project.entities.AuditoriaAccion;
import com.taemoi.project.entities.AuditoriaEvento;

public interface AuditoriaEventoRepository
		extends JpaRepository<AuditoriaEvento, Long>, JpaSpecificationExecutor<AuditoriaEvento> {

	@Query("SELECT DISTINCT ae.modulo FROM AuditoriaEvento ae WHERE ae.modulo IS NOT NULL AND ae.modulo <> '' ORDER BY ae.modulo")
	List<String> findDistinctModulos();

	/**
	 * Módulos del registro de acciones del propietario (D2), misma regla que
	 * {@code AuditoriaServiceImpl#filtroRegistroPropietario}: con sesión, estado en el rango, y una mutación o una
	 * lectura del módulo de salud.
	 */
	@Query("""
			SELECT DISTINCT ae.modulo FROM AuditoriaEvento ae
			WHERE ae.modulo IS NOT NULL AND ae.modulo <> ''
			  AND ae.usuarioId IS NOT NULL
			  AND ae.estadoHttp BETWEEN :estadoMinimo AND :estadoMaximo
			  AND (ae.accion IN :mutaciones OR (ae.accion = :lectura AND ae.modulo = :moduloSalud))
			ORDER BY ae.modulo""")
	List<String> findDistinctModulosRegistroPropietario(@Param("estadoMinimo") int estadoMinimo,
			@Param("estadoMaximo") int estadoMaximo, @Param("mutaciones") Collection<AuditoriaAccion> mutaciones,
			@Param("lectura") AuditoriaAccion lectura, @Param("moduloSalud") String moduloSalud);

	long deleteByFechaEventoBefore(Date fechaLimite);
}
