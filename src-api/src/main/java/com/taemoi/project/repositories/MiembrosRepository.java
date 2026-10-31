package com.taemoi.project.repositories;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Membresia;
import com.taemoi.project.entities.RolMembresia;

/**
 * Consultas de la API de miembros (pieza E8-3; D8-4, D8-5, D8-10). {@code membresia}, {@code membresia_sede} y
 * {@code asignacion_profesor} NO son {@code @TenantId}: cada consulta lleva {@code organizacion_id} explícito (la de
 * {@code membresia_sede}, por la membresía). {@code sede} y {@code grupo} sí son tenant, y además se comparan con la
 * misma escuela. No añadir consultas sin ese filtro.
 */
public interface MiembrosRepository extends Repository<Membresia, Long> {

	/** Fila de la lista: los datos de la cuenta. */
	interface FilaCuenta {
		Long getId();

		String getNombre();

		String getApellidos();

		String getEmail();

		AuthProvider getAuthProvider();

		/** Pieza E8-4: sin contraseña (con LOCAL, invitación pendiente). */
		Boolean getSinContrasena();
	}

	/**
	 * Una página de miembros de la escuela, por apellidos, nombre e id. Miembro: alguna membresía (activa o no) en la
	 * escuela y, si {@code porDefecto}, también la cuenta sin ninguna membresía (legado). Sin
	 * {@code incluirFamilias}, solo quien tiene alguna
	 * membresía distinta de {@code familia}; con {@code filtrarRol}, quien tiene una de {@code rol}; con {@code buscar},
	 * {@code patron} (en minúsculas, con {@code !} como escape) en nombre, apellidos, «nombre apellidos» o email.
	 */
	@Query(value = """
			SELECT u.id AS id, u.nombre AS nombre, u.apellidos AS apellidos, u.email AS email,
			       u.authProvider AS authProvider,
			       CASE WHEN u.contrasena IS NULL THEN true ELSE false END AS sinContrasena
			FROM Usuario u
			WHERE (EXISTS (SELECT 1 FROM Membresia m WHERE m.usuarioId = u.id AND m.organizacionId = :organizacionId)
			       OR (:porDefecto = true AND NOT EXISTS (SELECT 1 FROM Membresia m2 WHERE m2.usuarioId = u.id)))
			  AND (:incluirFamilias = true OR EXISTS (SELECT 1 FROM Membresia m3 WHERE m3.usuarioId = u.id
			       AND m3.organizacionId = :organizacionId AND m3.rol <> :familia))
			  AND (:filtrarRol = false OR EXISTS (SELECT 1 FROM Membresia m4 WHERE m4.usuarioId = u.id
			       AND m4.organizacionId = :organizacionId AND m4.rol = :rol))
			  AND (:buscar = false
			       OR LOWER(u.nombre) LIKE :patron ESCAPE '!'
			       OR LOWER(u.apellidos) LIKE :patron ESCAPE '!'
			       OR LOWER(CONCAT(COALESCE(u.nombre, ''), ' ', COALESCE(u.apellidos, ''))) LIKE :patron ESCAPE '!'
			       OR LOWER(u.email) LIKE :patron ESCAPE '!')
			ORDER BY u.apellidos ASC, u.nombre ASC, u.id ASC""", countQuery = """
			SELECT COUNT(u) FROM Usuario u
			WHERE (EXISTS (SELECT 1 FROM Membresia m WHERE m.usuarioId = u.id AND m.organizacionId = :organizacionId)
			       OR (:porDefecto = true AND NOT EXISTS (SELECT 1 FROM Membresia m2 WHERE m2.usuarioId = u.id)))
			  AND (:incluirFamilias = true OR EXISTS (SELECT 1 FROM Membresia m3 WHERE m3.usuarioId = u.id
			       AND m3.organizacionId = :organizacionId AND m3.rol <> :familia))
			  AND (:filtrarRol = false OR EXISTS (SELECT 1 FROM Membresia m4 WHERE m4.usuarioId = u.id
			       AND m4.organizacionId = :organizacionId AND m4.rol = :rol))
			  AND (:buscar = false
			       OR LOWER(u.nombre) LIKE :patron ESCAPE '!'
			       OR LOWER(u.apellidos) LIKE :patron ESCAPE '!'
			       OR LOWER(CONCAT(COALESCE(u.nombre, ''), ' ', COALESCE(u.apellidos, ''))) LIKE :patron ESCAPE '!'
			       OR LOWER(u.email) LIKE :patron ESCAPE '!')""")
	Page<FilaCuenta> pagina(@Param("organizacionId") Long organizacionId, @Param("porDefecto") boolean porDefecto,
			@Param("incluirFamilias") boolean incluirFamilias, @Param("familia") RolMembresia familia,
			@Param("filtrarRol") boolean filtrarRol, @Param("rol") RolMembresia rol, @Param("buscar") boolean buscar,
			@Param("patron") String patron, Pageable pagina);

	/** Membresías (activas o no) de esas cuentas en la escuela. */
	@Query("""
			SELECT m FROM Membresia m
			WHERE m.organizacionId = :organizacionId AND m.usuarioId IN :usuarios
			ORDER BY m.usuarioId, m.id""")
	List<Membresia> membresiasDe(@Param("organizacionId") Long organizacionId,
			@Param("usuarios") Collection<Long> usuarios);

	/** Sedes de esas membresías de la escuela: filas {@code [membresiaId, sedeId, nombre]}, por id de sede. */
	@Query("""
			SELECT ms.membresiaId, s.id, s.nombre
			FROM MembresiaSede ms
			JOIN Membresia m ON m.id = ms.membresiaId
			JOIN Sede s ON s.id = ms.sedeId
			WHERE m.organizacionId = :organizacionId AND s.organizacionId = :organizacionId
			  AND ms.membresiaId IN :membresias
			ORDER BY s.id""")
	List<Object[]> sedesDeMembresias(@Param("organizacionId") Long organizacionId,
			@Param("membresias") Collection<Long> membresias);

	/** Ids de las sedes de una membresía de la escuela. */
	@Query("""
			SELECT ms.sedeId FROM MembresiaSede ms JOIN Membresia m ON m.id = ms.membresiaId
			WHERE ms.membresiaId = :membresiaId AND m.organizacionId = :organizacionId
			ORDER BY ms.sedeId""")
	List<Long> sedesDeMembresia(@Param("membresiaId") Long membresiaId, @Param("organizacionId") Long organizacionId);

	/** Decisión 389: alumnos vinculados a la cuenta en la escuela ({@code usuario_alumno}). */
	@Query("SELECT COUNT(v) FROM UsuarioAlumno v WHERE v.usuarioId = :usuarioId AND v.organizacionId = :organizacionId")
	long alumnosVinculados(@Param("usuarioId") Long usuarioId, @Param("organizacionId") Long organizacionId);

	/** Borra las sedes de una membresía, solo si la membresía es de la escuela; devuelve las filas borradas. */
	@Modifying(flushAutomatically = true)
	@Query("""
			DELETE FROM MembresiaSede ms
			WHERE ms.membresiaId = :membresiaId
			  AND ms.membresiaId IN (SELECT m.id FROM Membresia m WHERE m.id = :membresiaId
			                         AND m.organizacionId = :organizacionId)""")
	int borrarSedesDeMembresia(@Param("membresiaId") Long membresiaId, @Param("organizacionId") Long organizacionId);

	/** Número de asignaciones de esas cuentas en la escuela: filas {@code [usuarioId, numero]}. */
	@Query("""
			SELECT a.usuarioId, COUNT(a.grupoId) FROM AsignacionProfesor a
			WHERE a.organizacionId = :organizacionId AND a.usuarioId IN :usuarios
			GROUP BY a.usuarioId""")
	List<Object[]> numeroDeGrupos(@Param("organizacionId") Long organizacionId,
			@Param("usuarios") Collection<Long> usuarios);

	/** Grupos asignados a la cuenta en la escuela: filas {@code [grupoId, nombre, sedeId, rol]}, por nombre e id. */
	@Query("""
			SELECT a.grupoId, g.nombre, g.sedeId, a.rol
			FROM AsignacionProfesor a JOIN Grupo g ON g.id = a.grupoId
			WHERE a.organizacionId = :organizacionId AND g.organizacionId = :organizacionId AND a.usuarioId = :usuarioId
			ORDER BY g.nombre, g.id""")
	List<Object[]> gruposDe(@Param("organizacionId") Long organizacionId, @Param("usuarioId") Long usuarioId);

	/** De esos grupos, los que son de la escuela. */
	@Query("SELECT g.id FROM Grupo g WHERE g.id IN :ids AND g.organizacionId = :organizacionId")
	List<Long> gruposDeLaEscuela(@Param("organizacionId") Long organizacionId, @Param("ids") Collection<Long> ids);

	/** De esas sedes, las que son de la escuela (también inactivas). */
	@Query("SELECT s.id FROM Sede s WHERE s.id IN :ids AND s.organizacionId = :organizacionId")
	List<Long> sedesDeLaEscuela(@Param("organizacionId") Long organizacionId, @Param("ids") Collection<Long> ids);
}
