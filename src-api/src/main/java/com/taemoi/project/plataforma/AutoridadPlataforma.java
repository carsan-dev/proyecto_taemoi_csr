package com.taemoi.project.plataforma;

/**
 * Autoridad del administrador de plataforma (docs/multi-escuela/fase3-sedes-permisos.md §4).
 *
 * <p>No es un rol de membresía ni un valor de {@code Roles}: sale de la marca {@code usuario.es_admin_plataforma}
 * (puesta por SQL fuera del repositorio) y se añade en {@code IdentificadorSesionJwt} en cualquier escuela, sumada
 * a las autoridades de las membresías. Sin membresía, la sesión se abre en la escuela por defecto SOLO con ella
 * (ningún dato de escuela). {@code /api/auth/roles} no la lista.
 */
public final class AutoridadPlataforma {

	public static final String ADMIN_PLATAFORMA = "ROLE_ADMIN_PLATAFORMA";

	private AutoridadPlataforma() {
	}
}
