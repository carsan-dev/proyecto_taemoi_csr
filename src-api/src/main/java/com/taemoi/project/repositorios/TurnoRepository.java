package com.taemoi.project.repositorios;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entidades.Grupo;
import com.taemoi.project.entidades.Turno;

/**
 * Repositorio para la entidad Turno.
 */
@Repository
public interface TurnoRepository extends JpaRepository<Turno, Long> {
    boolean existsByDiaSemana(String diaSemana);

	boolean existsByDiaSemanaAndHoraInicioAndHoraFin(String dia, String horaInicio, String horaFin);
	
	List<Turno> findByGrupo(Grupo grupo);
}
