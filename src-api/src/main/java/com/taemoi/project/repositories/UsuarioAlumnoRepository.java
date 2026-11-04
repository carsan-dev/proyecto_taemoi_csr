package com.taemoi.project.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.taemoi.project.entities.OrigenUsuarioAlumno;
import com.taemoi.project.entities.RelacionUsuarioAlumno;
import com.taemoi.project.entities.UsuarioAlumno;

/**
 * {@link UsuarioAlumno} no se filtra por organización automáticamente: cada método filtra explícitamente por
 * organización (y usuario o alumno). No añadir consultas sin ese filtro.
 */
public interface UsuarioAlumnoRepository extends JpaRepository<UsuarioAlumno, UsuarioAlumno.Clave> {

	List<UsuarioAlumno> findByUsuarioIdAndOrganizacionId(Long usuarioId, Long organizacionId);

	List<UsuarioAlumno> findByUsuarioIdAndOrganizacionIdAndRelacion(Long usuarioId, Long organizacionId,
			RelacionUsuarioAlumno relacion);

	List<UsuarioAlumno> findByAlumnoIdAndOrganizacionId(Long alumnoId, Long organizacionId);

	boolean existsByUsuarioIdAndAlumnoIdAndOrganizacionId(Long usuarioId, Long alumnoId, Long organizacionId);

	/**
	 * Decisión 438 (pieza E10-2): filas de la ficha en la organización, en orden de {@code usuario_id}, que dan acceso
	 * a su cuenta con la regla de filas de {@code AlumnoRepository.ACCESO_FAMILIA} (sin mirar si la ficha o la
	 * membresía están activas): en una escuela con {@code comportamiento_legado_taemoi}, {@code MANUAL} siempre y las
	 * demás solo si el email de la cuenta, normalizado ({@code LOWER(TRIM)}), es el de la ficha ({@code LOWER}); en las
	 * demás escuelas, cualquier fila. {@code :emailFicha} es el email de la ficha tal cual o {@code ''} si no tiene.
	 */
	@Query("""
			SELECT ua FROM UsuarioAlumno ua
			WHERE ua.alumnoId = :alumnoId
			  AND ua.organizacionId = :organizacionId
			  AND (ua.origen = :manual
			       OR NOT EXISTS (SELECT 1 FROM Organizacion o
			                      WHERE o.id = :organizacionId AND o.comportamientoLegadoTaemoi = TRUE)
			       OR (:emailFicha <> ''
			           AND EXISTS (SELECT 1 FROM Usuario u
			                       WHERE u.id = ua.usuarioId
			                         AND TRIM(u.email) <> ''
			                         AND LOWER(TRIM(u.email)) = LOWER(:emailFicha))))
			ORDER BY ua.usuarioId
			""")
	List<UsuarioAlumno> filasQueDanAccesoALaFicha(@Param("alumnoId") Long alumnoId,
			@Param("organizacionId") Long organizacionId, @Param("emailFicha") String emailFicha,
			@Param("manual") OrigenUsuarioAlumno manual);
}
