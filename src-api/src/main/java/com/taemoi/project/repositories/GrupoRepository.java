package com.taemoi.project.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import com.taemoi.project.entities.Grupo;

/**
 * Repositorio para la entidad Grupo.
 */
@Repository
public interface GrupoRepository extends JpaRepository<Grupo, Long>, JpaSpecificationExecutor<Grupo> {
	Optional<Grupo> findByNombre(String nombre);

	/**
	 * R2-P4 (resumen de grupos): por grupo, cuántos alumnos cuentan en su lista, los mismos que muestra
	 * {@code GrupoServiceImpl#convertirEntidadADTO}: alumno activo que, si tiene alguna ficha de deporte, la tiene
	 * activa con el deporte del grupo (sin fichas de deporte, entra). Filas {@code [grupoId, número]}; los grupos sin
	 * alumnos que cuenten no salen.
	 */
	@org.springframework.data.jpa.repository.Query("SELECT g.id, COUNT(DISTINCT a.id) FROM Grupo g JOIN g.alumnos a "
			+ "WHERE g.id IN :ids AND a.activo = true AND (NOT EXISTS (SELECT ad.id FROM AlumnoDeporte ad "
			+ "WHERE ad.alumno = a) OR EXISTS (SELECT ad2.id FROM AlumnoDeporte ad2 WHERE ad2.alumno = a "
			+ "AND ad2.activo = true AND ad2.disciplina.codigo = g.disciplina.codigo)) GROUP BY g.id")
	List<Object[]> contarAlumnosPorGrupo(
			@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);

	List<Grupo> findByTipoIgnoreCase(String tipo);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@org.springframework.data.jpa.repository.Query("select g from Grupo g where g.id = :id")
	Optional<Grupo> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
