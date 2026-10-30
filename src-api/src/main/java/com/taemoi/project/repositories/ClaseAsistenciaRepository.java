package com.taemoi.project.repositories;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entities.ClaseAsistencia;

/** Clases con lista guardada de la escuela activa ({@code @TenantId}). */
@Repository
public interface ClaseAsistenciaRepository extends JpaRepository<ClaseAsistencia, Long> {

	Optional<ClaseAsistencia> findByTurnoIdAndFecha(Long turnoId, LocalDate fecha);

	/** Clases de un grupo en esas fechas, ambas incluidas, también las de turnos ya borrados. */
	List<ClaseAsistencia> findByGrupoIdAndFechaBetween(Long grupoId, LocalDate desde, LocalDate hasta);

	/**
	 * Lista actual de los turnos de un grupo, por nombre, apellidos e id: el criterio del PDF por turnos
	 * ({@code PDFServiceImpl#generarListadoAsistenciaPorTurnos}): alumnos del turno, que están en el grupo del turno,
	 * activos, con la inscripción activa en la disciplina del grupo y con fecha de nacimiento. Filas
	 * {@code [turnoId, alumnoId]}. Una consulta para todos los turnos, sin N+1.
	 */
	@Query("""
			SELECT t.id, a.id FROM Turno t JOIN t.grupo g JOIN t.alumnos a
			WHERE g.id = :grupo AND a MEMBER OF g.alumnos AND a.activo = TRUE AND a.fechaNacimiento IS NOT NULL
			AND EXISTS (SELECT ad.id FROM AlumnoDeporte ad WHERE ad.alumno = a AND ad.activo = TRUE
			            AND ad.disciplina.codigo = g.disciplina.codigo)
			ORDER BY a.nombre, a.apellidos, a.id""")
	List<Object[]> listaActualDeLosTurnos(@Param("grupo") Long grupoId);
}
