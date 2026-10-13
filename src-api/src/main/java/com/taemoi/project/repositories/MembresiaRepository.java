package com.taemoi.project.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.taemoi.project.entities.Membresia;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Usuario;

/**
 * {@link Membresia} no se filtra por organización automáticamente: cada método filtra
 * explícitamente por usuario y/o organización. No añadir consultas sin ese filtro.
 */
public interface MembresiaRepository extends JpaRepository<Membresia, Long> {

	List<Membresia> findByUsuarioId(Long usuarioId);

	List<Membresia> findByUsuarioIdAndOrganizacionId(Long usuarioId, Long organizacionId);

	List<Membresia> findByOrganizacionId(Long organizacionId);

	boolean existsByUsuarioId(Long usuarioId);

	boolean existsByUsuarioIdAndOrganizacionId(Long usuarioId, Long organizacionId);

	/** Membresías activas del usuario en organizaciones activas, de todas sus escuelas. */
	@Query("""
			SELECT m FROM Membresia m
			WHERE m.usuarioId = :usuarioId
			  AND m.activa = true
			  AND EXISTS (SELECT 1 FROM Organizacion o WHERE o.id = m.organizacionId AND o.activa = true)
			ORDER BY m.organizacionId ASC
			""")
	List<Membresia> findActivasDeUsuario(@Param("usuarioId") Long usuarioId);

	/** Roles activos del usuario en una organización (vacío si la organización no está activa). */
	@Query("""
			SELECT m.rol FROM Membresia m
			WHERE m.usuarioId = :usuarioId
			  AND m.organizacionId = :organizacionId
			  AND m.activa = true
			  AND EXISTS (SELECT 1 FROM Organizacion o WHERE o.id = m.organizacionId AND o.activa = true)
			""")
	List<RolMembresia> findRolesActivos(@Param("usuarioId") Long usuarioId,
			@Param("organizacionId") Long organizacionId);

	/**
	 * Usuarios visibles para la gestión de una organización: con alguna membresía en ella (activa o
	 * no) y, si {@code incluirSinMembresia}, también los que no tienen ninguna membresía (legado).
	 */
	@Query("""
			SELECT u FROM Usuario u
			WHERE EXISTS (SELECT 1 FROM Membresia m WHERE m.usuarioId = u.id AND m.organizacionId = :organizacionId)
			   OR (:incluirSinMembresia = true
			       AND NOT EXISTS (SELECT 1 FROM Membresia m2 WHERE m2.usuarioId = u.id))
			""")
	List<Usuario> findUsuariosDeOrganizacion(@Param("organizacionId") Long organizacionId,
			@Param("incluirSinMembresia") boolean incluirSinMembresia);
}
