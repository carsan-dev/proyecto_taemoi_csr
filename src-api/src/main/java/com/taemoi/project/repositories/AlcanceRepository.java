package com.taemoi.project.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.taemoi.project.entities.Membresia;

/**
 * Alcance por sede y grupo de una cuenta en una escuela (entrega 5b-2, docs/multi-escuela/entrega5-plan.md §3.3).
 * Solo lo usa {@code IdentificadorSesionJwt}, y solo para sesiones con RESPONSABLE_SEDE o PROFESOR y sin gestión.
 *
 * <p>Es la SEGUNDA excepción al filtro de escuela, después de {@link AlumnoRepository#ACCESO_FAMILIA}: se ejecuta al
 * verificar el JWT, antes de fijar la escuela, así que es nativa y NO depende de {@code @TenantId}. Filtra
 * {@code organizacion_id} explícitamente en cada tabla. {@code membresia_sede} y {@code asignacion_profesor} no son
 * {@code @TenantId} y solo tienen FK simples: los {@code JOIN} con {@code sede} y {@code grupo} por la organización
 * son la defensa entre escuelas (una sede o un grupo de otra escuela no aparecen nunca).
 *
 * <ul>
 * <li>{@link #TIPO_SEDE}: sedes de {@code membresia_sede} de una membresía RESPONSABLE_SEDE activa.
 * {@code sede.activa} no se filtra (D5-5: el responsable de una sede cerrada sigue viendo su histórico).</li>
 * <li>{@link #TIPO_GRUPO}: grupos de {@code asignacion_profesor} si la cuenta tiene una membresía PROFESOR activa en
 * esa escuela.</li>
 * </ul>
 * La organización activa ya la exige {@code MembresiaRepository.findRolesActivos}, que decide las autoridades.
 */
public interface AlcanceRepository extends Repository<Membresia, Long> {

	String TIPO_SEDE = "SEDE";
	String TIPO_GRUPO = "GRUPO";

	String ALCANCE = """
			SELECT 'SEDE' AS tipo, ms.sede_id AS id
			FROM membresia m
			JOIN membresia_sede ms ON ms.membresia_id = m.id
			JOIN sede s ON s.id = ms.sede_id AND s.organizacion_id = m.organizacion_id
			WHERE m.usuario_id = :usuarioId AND m.organizacion_id = :organizacionId
			  AND m.activa = TRUE AND m.rol = 'RESPONSABLE_SEDE'
			UNION ALL
			SELECT 'GRUPO' AS tipo, ap.grupo_id AS id
			FROM asignacion_profesor ap
			JOIN grupo g ON g.id = ap.grupo_id AND g.organizacion_id = ap.organizacion_id
			WHERE ap.usuario_id = :usuarioId AND ap.organizacion_id = :organizacionId
			  AND EXISTS (SELECT 1 FROM membresia p
			              WHERE p.usuario_id = ap.usuario_id AND p.organizacion_id = ap.organizacion_id
			                AND p.activa = TRUE AND p.rol = 'PROFESOR')
			""";

	/** Fila de {@link #ALCANCE}: {@code tipo} es {@link #TIPO_SEDE} o {@link #TIPO_GRUPO}. */
	interface FilaAlcance {
		String getTipo();

		Long getId();
	}

	/** Filas de {@link #ALCANCE}. Usar {@code AlcanceSesion.desde}. */
	@Query(value = ALCANCE, nativeQuery = true)
	List<FilaAlcance> filasDeAlcance(@Param("usuarioId") Long usuarioId, @Param("organizacionId") Long organizacionId);
}
