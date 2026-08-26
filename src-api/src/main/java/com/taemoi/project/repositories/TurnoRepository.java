package com.taemoi.project.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
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

	@Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT t FROM Turno t LEFT JOIN FETCH t.grupo WHERE t.id = :id")
	Optional<Turno> findByIdForUpdate(@Param("id") Long id);

	@Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT t FROM Turno t JOIN FETCH t.grupo WHERE t.id IN :ids ORDER BY t.id")
	List<Turno> findAllByIdForUpdate(@Param("ids") List<Long> ids);

	/**
	 * Obtiene todos los turnos con sus alumnos cargados de forma eager para evitar
	 * N+1 queries. Útil para mostrar información completa de turnos con alumnos.
	 *
	 * @return Lista de turnos con sus alumnos cargados
	 */
	@Query("SELECT DISTINCT t FROM Turno t LEFT JOIN FETCH t.alumnos")
	List<Turno> findAllWithAlumnos();
}
