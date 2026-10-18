package com.taemoi.project.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
public interface TurnoRepository extends JpaRepository<Turno, Long>, JpaSpecificationExecutor<Turno> {
	boolean existsByDiaSemana(String diaSemana);

	boolean existsByDiaSemanaAndHoraInicioAndHoraFin(String dia, String horaInicio, String horaFin);

	List<Turno> findByGrupo(Grupo grupo);

	/** Bloquea (FOR UPDATE) solo la fila del turno, con o sin grupo; R2-P3: sin JOIN FETCH, el grupo (EAGER) se lee aparte, sin bloqueo. */
	@Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT t FROM Turno t WHERE t.id = :id")
	Optional<Turno> findByIdForUpdate(@Param("id") Long id);

	/**
	 * Bloquea (FOR UPDATE) los turnos con grupo de la lista, en orden de id. R2-P3: sin {@code JOIN FETCH t.grupo}, que
	 * bloqueaba también las filas de {@code grupo} intercaladas con las de turno (y sin orden por grupo): dos operaciones
	 * sobre turnos solapados podían interbloquearse. Solo se bloquea {@code turno}; el grupo (EAGER) se lee aparte, sin
	 * bloqueo. Los turnos sin grupo siguen sin devolverse, como con el JOIN.
	 */
	@Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT t FROM Turno t WHERE t.id IN :ids AND t.grupo IS NOT NULL ORDER BY t.id")
	List<Turno> findAllByIdForUpdate(@Param("ids") List<Long> ids);

	/**
	 * Obtiene todos los turnos con sus alumnos cargados de forma eager para evitar
	 * N+1 queries. Útil para mostrar información completa de turnos con alumnos.
	 *
	 * @return Lista de turnos con sus alumnos cargados
	 */
	@Query("SELECT DISTINCT t FROM Turno t LEFT JOIN FETCH t.alumnos")
	List<Turno> findAllWithAlumnos();

	/**
	 * R2-P3 (aforo por lotes): por turno, alumnos distintos que cuentan en el aforo, los de ficha activa con el deporte
	 * del grupo del turno activo (antes, un exists por alumno). Filas {@code [turnoId, número]}; los turnos sin alumnos
	 * que cuenten no salen.
	 */
	@Query("SELECT t.id, COUNT(DISTINCT a.id) FROM Turno t JOIN t.grupo g JOIN t.alumnos a WHERE t.id IN :ids "
			+ "AND a.activo = true AND EXISTS (SELECT ad.id FROM AlumnoDeporte ad WHERE ad.alumno = a "
			+ "AND ad.activo = true AND ad.disciplina.codigo = g.disciplina.codigo) GROUP BY t.id")
	List<Object[]> contarAlumnosQueCuentanPorTurno(@Param("ids") List<Long> ids);
}
