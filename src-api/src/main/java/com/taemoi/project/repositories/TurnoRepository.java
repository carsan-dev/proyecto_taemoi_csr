package com.taemoi.project.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entities.Grupo;
import com.taemoi.project.entities.Turno;

/**
 * Repositorio para la entidad Turno.
 */
@Repository
public interface TurnoRepository extends JpaRepository<Turno, Long> {
	boolean existsByDiaSemana(String diaSemana);

	boolean existsByDiaSemanaAndHoraInicioAndHoraFin(String dia, String horaInicio, String horaFin);

	List<Turno> findByGrupo(Grupo grupo);

	/**
	 * Obtiene todos los turnos con sus alumnos cargados de forma eager para evitar
	 * N+1 queries. Útil para mostrar información completa de turnos con alumnos.
	 *
	 * @return Lista de turnos con sus alumnos cargados
	 */
	@Query("SELECT DISTINCT t FROM Turno t LEFT JOIN FETCH t.alumnos")
	List<Turno> findAllWithAlumnos();
}
