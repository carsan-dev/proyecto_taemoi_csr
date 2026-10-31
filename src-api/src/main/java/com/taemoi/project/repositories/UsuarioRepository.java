package com.taemoi.project.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Usuario;

/**
 * Repositorio para la entidad Usuario. Proporciona métodos para buscar tanto
 * por id como por email y para validar si existe el email buscado
 */
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	/**
	 * Recupera un usuario de la base de datos por su dirección de correo
	 * electrónico.
	 * 
	 * @param email El correo electrónico del usuario a buscar.
	 * @return Un Optional que puede contener el usuario correspondiente al correo
	 *         electrónico especificado.
	 */
	Optional<Usuario> findByEmail(String email);

	Optional<Usuario> findByEmailIgnoreCase(String email);

	Optional<Usuario> findByResetTokenHash(String resetTokenHash);

	/**
	 * Recupera un usuario de la base de datos por su identificador único.
	 * 
	 * @param id El identificador único del usuario a buscar.
	 * @return Un Optional que puede contener el usuario correspondiente al ID
	 *         especificado.
	 * @throws NullPointerException si el argumento id es nulo.
	 */
	@NonNull
	Optional<Usuario> findById(@NonNull Long id);

	/**
	 * Verifica si existe un usuario en la base de datos con la dirección de correo
	 * electrónico especificada.
	 * 
	 * @param email El correo electrónico a verificar.
	 * @return true si existe un usuario con el correo electrónico especificado,
	 *         false de lo contrario.
	 */
	Boolean existsByEmail(String email);

	Boolean existsByEmailIgnoreCase(String email);

	/** Usuarios con el recordatorio activado y una membresía activa con {@code rol} en la organización. */
	@Query("""
			SELECT u FROM Usuario u
			WHERE u.recordatorioRachaEmailHabilitado = true
			  AND EXISTS (
			    SELECT 1 FROM Membresia m
			    WHERE m.usuarioId = u.id
			      AND m.organizacionId = :organizacionId
			      AND m.activa = true
			      AND m.rol = :rol)
			""")
	List<Usuario> findConRecordatorioRachaYMembresiaActiva(@Param("organizacionId") Long organizacionId,
			@Param("rol") RolMembresia rol);

	/** Nombre y apellidos de esas cuentas en una consulta: filas {@code [id, nombre, apellidos]}. */
	@Query("SELECT u.id, u.nombre, u.apellidos FROM Usuario u WHERE u.id IN :ids")
	List<Object[]> nombresPorIds(@Param("ids") java.util.Collection<Long> ids);

	/**
	 * Bloquea (SELECT ... FOR UPDATE) la fila de la cuenta por id, sin joins. Solo en transacción; pieza E7-4: asignar un
	 * profesor la bloquea antes de leer y crear su membresía PROFESOR, para que dos altas a la vez se ordenen.
	 */
	@Query(value = "SELECT u.id FROM usuario u WHERE u.id = :id FOR UPDATE", nativeQuery = true)
	Optional<Long> bloquearPorId(@Param("id") Long id);

	@Modifying
	@Query("UPDATE Usuario u SET u.ultimaOrganizacionId = :organizacionId WHERE u.id = :usuarioId")
	int actualizarUltimaOrganizacion(@Param("usuarioId") Long usuarioId, @Param("organizacionId") Long organizacionId);

	/**
	 * Pieza E8-4: token nuevo de una invitación que SIGUE pendiente (LOCAL o sin proveedor y sin contraseña) y la escuela
	 * que la reenvía; 0 si ya no lo está (otra petición la aceptó entre medias). Solo el hash del token.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			UPDATE Usuario u SET u.resetTokenHash = :hash, u.resetTokenExpiresAt = :caduca,
			       u.ultimaOrganizacionId = :organizacionId
			WHERE u.id = :usuarioId AND u.contrasena IS NULL
			  AND (u.authProvider IS NULL OR u.authProvider = com.taemoi.project.entities.AuthProvider.LOCAL)""")
	int renovarInvitacion(@Param("usuarioId") Long usuarioId, @Param("hash") String hash,
			@Param("caduca") java.time.LocalDateTime caduca, @Param("organizacionId") Long organizacionId);

	/**
	 * Pieza E8-4: acepta la invitación (un solo uso): guarda la contraseña y borra el token solo si el hash es el de la
	 * cuenta, sigue pendiente y no ha caducado. 1 si la aceptó; 0 si no (de dos usos a la vez, uno gana).
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			UPDATE Usuario u SET u.contrasena = :contrasena, u.resetTokenHash = NULL, u.resetTokenExpiresAt = NULL
			WHERE u.id = :usuarioId AND u.resetTokenHash = :hash AND u.resetTokenExpiresAt > :ahora
			  AND u.contrasena IS NULL
			  AND (u.authProvider IS NULL OR u.authProvider = com.taemoi.project.entities.AuthProvider.LOCAL)""")
	int aceptarInvitacion(@Param("usuarioId") Long usuarioId, @Param("hash") String hash,
			@Param("contrasena") String contrasena, @Param("ahora") java.time.LocalDateTime ahora);

	/**
	 * Decisión 389: confirma contra la base que la invitación SIGUE pendiente, sin cambiar nada; 1 si lo está, 0 si otra
	 * petición la aceptó. Es un UPDATE para leer la fila vigente y no la entidad de la sesión: {@code aceptarInvitacion}
	 * no bloquea la fila antes de escribir. El token no se toca porque el de otra escuela debe seguir vivo; se retira
	 * aparte con {@link #retirarTokenInvitacion}. Cuenta las filas ENCONTRADAS, no las cambiadas: es lo que devuelve
	 * Connector/J por defecto; con {@code useAffectedRows=true} daría siempre 0.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			UPDATE Usuario u SET u.resetTokenHash = u.resetTokenHash
			WHERE u.id = :usuarioId AND u.contrasena IS NULL
			  AND (u.authProvider IS NULL OR u.authProvider = com.taemoi.project.entities.AuthProvider.LOCAL)""")
	int confirmarInvitacionPendiente(@Param("usuarioId") Long usuarioId);

	/** Decisión 389: deja sin token vivo una invitación que sigue pendiente; 0 si ya no lo está. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			UPDATE Usuario u SET u.resetTokenHash = NULL, u.resetTokenExpiresAt = NULL
			WHERE u.id = :usuarioId AND u.contrasena IS NULL
			  AND (u.authProvider IS NULL OR u.authProvider = com.taemoi.project.entities.AuthProvider.LOCAL)""")
	int retirarTokenInvitacion(@Param("usuarioId") Long usuarioId);

	/**
	 * Decisión 389: 1 si la cuenta tiene algo más que las filas propias de la invitación en {@code organizacionId} (sus
	 * membresías, con sus sedes, y sus asignaciones), 0 si no. Consulta nativa: mira TODAS las escuelas, sin
	 * {@code @TenantId}. Las membresías de otras escuelas las mira quien llama, para saber además cuáles siguen activas.
	 *
	 * <p>Una línea por cada clave ajena a {@code usuario}: casi todas son {@code ON DELETE SET NULL} o {@code CASCADE}, así
	 * que borrar sin mirarlas no daría error: perdería el autor de un cobro o el vínculo con un alumno. También el vínculo
	 * legado {@code usuario.alumno_id} y la marca de plataforma. Una clave ajena nueva hace fallar
	 * {@code CancelarInvitacionIntegrationTest} hasta añadirla aquí. {@code auditoria_evento} no tiene clave ajena: copia
	 * el id, el email y el nombre, y no impide el borrado. {@code usuario_rol} se va en V32: quitar su línea entonces.
	 */
	@Query(value = """
			SELECT CASE WHEN
			     EXISTS (SELECT 1 FROM usuario u WHERE u.id = :usuarioId
			             AND (u.alumno_id IS NOT NULL OR u.es_admin_plataforma = TRUE))
			  OR EXISTS (SELECT 1 FROM usuario_alumno x WHERE x.usuario_id = :usuarioId)
			  OR EXISTS (SELECT 1 FROM usuario_rol x WHERE x.usuario_id = :usuarioId)
			  OR EXISTS (SELECT 1 FROM asignacion_profesor x WHERE x.usuario_id = :usuarioId
			             AND x.organizacion_id <> :organizacionId)
			  OR EXISTS (SELECT 1 FROM cobro x WHERE x.cobrado_por_usuario_id = :usuarioId)
			  OR EXISTS (SELECT 1 FROM cobro x WHERE x.anulado_por_usuario_id = :usuarioId)
			  OR EXISTS (SELECT 1 FROM entrega_caja x WHERE x.entregado_por_usuario_id = :usuarioId)
			  OR EXISTS (SELECT 1 FROM entrega_caja x WHERE x.recibido_por_usuario_id = :usuarioId)
			  OR EXISTS (SELECT 1 FROM entrega_caja x WHERE x.anulada_por_usuario_id = :usuarioId)
			  OR EXISTS (SELECT 1 FROM movimiento_caja x WHERE x.creado_por_usuario_id = :usuarioId)
			  OR EXISTS (SELECT 1 FROM movimiento_caja x WHERE x.anulado_por_usuario_id = :usuarioId)
			  OR EXISTS (SELECT 1 FROM producto_alumno x WHERE x.cancelado_por_usuario_id = :usuarioId)
			  OR EXISTS (SELECT 1 FROM producto_alumno x WHERE x.vendido_por_usuario_id = :usuarioId)
			  OR EXISTS (SELECT 1 FROM solicitud x WHERE x.solicitado_por_usuario_id = :usuarioId)
			  OR EXISTS (SELECT 1 FROM solicitud x WHERE x.resuelto_por_usuario_id = :usuarioId)
			  OR EXISTS (SELECT 1 FROM solicitud x WHERE x.encargado_por_usuario_id = :usuarioId)
			  OR EXISTS (SELECT 1 FROM solicitud x WHERE x.recibido_por_usuario_id = :usuarioId)
			  OR EXISTS (SELECT 1 FROM clase_asistencia x WHERE x.registrado_por_usuario_id = :usuarioId)
			  OR EXISTS (SELECT 1 FROM clase_asistencia x WHERE x.modificado_por_usuario_id = :usuarioId)
			THEN 1 ELSE 0 END""", nativeQuery = true)
	int tieneDatosFueraDeLaInvitacion(@Param("usuarioId") Long usuarioId, @Param("organizacionId") Long organizacionId);

	/** Decisión 389: borra la cuenta solo si sigue pendiente; 1 si la borró. Antes hay que haber quitado sus filas. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			DELETE FROM Usuario u
			WHERE u.id = :usuarioId AND u.contrasena IS NULL
			  AND (u.authProvider IS NULL OR u.authProvider = com.taemoi.project.entities.AuthProvider.LOCAL)""")
	int borrarCuentaPendiente(@Param("usuarioId") Long usuarioId);
}
