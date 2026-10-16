package com.taemoi.project.services;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.taemoi.project.dtos.response.OrganizacionUsuarioDTO;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Usuario;

/**
 * Permisos por organización. Única fuente de las autoridades de una sesión: traduce las
 * membresías activas del usuario en la organización a {@code ROLE_*} (ver {@link RolMembresia}).
 */
public interface MembresiaService {

	/**
	 * Autoridades del usuario en la organización (vacío si no tiene membresía activa o la escuela
	 * no está activa).
	 */
	Set<Roles> autoridadesEn(Long usuarioId, Long organizacionId);

	/** Organizaciones (activas) en las que el usuario tiene alguna membresía activa, por id. */
	List<Long> organizacionesConMembresiaActiva(Long usuarioId);

	/**
	 * Si el usuario puede abrir sesión en la organización: tiene membresía activa y, si solo es
	 * familia, algún alumno activo con su email en esa escuela.
	 */
	boolean puedeEntrarEn(Usuario usuario, Long organizacionId);

	/**
	 * Escuela con la que se abre la sesión, entre aquellas en las que {@link #puedeEntrarEn}: si solo
	 * hay una, esa; si hay varias, la última usada si sigue siendo válida y si no la de menor id.
	 * Vacío si no puede entrar en ninguna (se rechaza el inicio de sesión), salvo para un administrador de
	 * plataforma ({@code usuario.es_admin_plataforma}): la organización por defecto, donde su sesión no tiene
	 * ninguna autoridad de escuela.
	 */
	Optional<Long> elegirOrganizacionParaLogin(Usuario usuario);

	void registrarUltimaOrganizacion(Usuario usuario, Long organizacionId);

	List<OrganizacionUsuarioDTO> organizacionesDe(Long usuarioId, Long organizacionActiva);

	/**
	 * Deja al usuario exactamente con {@code autoridades} en la organización: desactiva las
	 * membresías cuya autoridad ya no está y crea (o reactiva) el rol canónico de las nuevas. Las
	 * membresías que ya dan una autoridad pedida (p. ej. PROFESOR para ROLE_MANAGER) se conservan.
	 *
	 * @return las autoridades resultantes en esa organización.
	 */
	Set<Roles> reemplazarAutoridades(Long usuarioId, Long organizacionId, Set<Roles> autoridades);

	/** Crea (o reactiva) la membresía {@code rol} del usuario en la organización. */
	void asegurarMembresia(Long usuarioId, Long organizacionId, RolMembresia rol);

	/** Autoridades activas de cada usuario de la organización (clave: id de usuario). */
	Map<Long, Set<Roles>> autoridadesPorUsuario(Long organizacionId);

	/**
	 * Usuarios que gestiona la organización: con alguna membresía en ella (activa o no). En la
	 * organización por defecto se incluyen también los usuarios sin ninguna membresía (legado).
	 */
	List<Usuario> usuariosDeOrganizacion(Long organizacionId);

	/** Si el usuario es uno de {@link #usuariosDeOrganizacion}. */
	boolean esUsuarioDeOrganizacion(Long usuarioId, Long organizacionId);

	/**
	 * Si un administrador de la organización puede cambiar el registro GLOBAL del usuario
	 * (contraseña, email...): solo si TODAS sus membresías (activas o no) son de esa organización.
	 * Sin ninguna membresía (legado), solo desde la organización por defecto.
	 */
	boolean puedeGestionarUsuarioGlobal(Long usuarioId, Long organizacionId);

	/**
	 * Quita la membresía FAMILIA del usuario en la organización (si la tiene).
	 *
	 * @return si al usuario le quedan membresías en alguna organización.
	 */
	boolean retirarFamilia(Long usuarioId, Long organizacionId);
}
