package com.taemoi.project.entities;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Rol de un usuario dentro de una organización ({@link Membresia}).
 *
 * <p>Traducción a autoridades (decisión 9 de docs/multi-escuela/modelo-datos.md; entrega 5b,
 * docs/multi-escuela/entrega5-plan.md §2):
 * <ul>
 * <li>{@code PROPIETARIO} → {@code ROLE_ADMIN}</li>
 * <li>{@code ADMINISTRADOR} → {@code ROLE_MANAGER}</li>
 * <li>{@code FAMILIA} → {@code ROLE_USER}</li>
 * <li>{@code RESPONSABLE_SEDE} → {@code ROLE_RESPONSABLE_SEDE} y {@code PROFESOR} → {@code ROLE_PROFESOR}:
 * autoridades propias que todavía no pasan ninguna regla (fallan cerradas). Solas no abren sesión
 * ({@link #abreSesion}); con FAMILIA, la cuenta entra como familia. Sus endpoints se abren en las entregas 6 y 7.</li>
 * </ul>
 */
public enum RolMembresia {
	PROPIETARIO(Roles.ROLE_ADMIN),
	ADMINISTRADOR(Roles.ROLE_MANAGER),
	RESPONSABLE_SEDE(Roles.ROLE_RESPONSABLE_SEDE),
	PROFESOR(Roles.ROLE_PROFESOR),
	FAMILIA(Roles.ROLE_USER);

	private final Roles autoridad;

	RolMembresia(Roles autoridad) {
		this.autoridad = autoridad;
	}

	public Roles autoridad() {
		return autoridad;
	}

	/**
	 * Si la API de roles ({@code PUT /api/admin/users/{userId}/roles}) gestiona este rol: solo PROPIETARIO,
	 * ADMINISTRADOR y FAMILIA. RESPONSABLE_SEDE y PROFESOR los asignará la API de miembros (entrega 8).
	 */
	public boolean gestionadoPorApiDeRoles() {
		return this == PROPIETARIO || this == ADMINISTRADOR || this == FAMILIA;
	}

	/** Si la autoridad se puede pedir en la API de roles: ROLE_ADMIN, ROLE_MANAGER o ROLE_USER. */
	public static boolean asignableDesdeApiDeRoles(Roles autoridad) {
		return autoridad == Roles.ROLE_ADMIN || autoridad == Roles.ROLE_MANAGER || autoridad == Roles.ROLE_USER;
	}

	/**
	 * Rol canónico que se crea al asignar una autoridad desde la API de roles.
	 *
	 * @throws IllegalArgumentException con {@code ROLE_RESPONSABLE_SEDE} o {@code ROLE_PROFESOR}.
	 */
	public static RolMembresia desdeAutoridad(Roles autoridad) {
		return switch (autoridad) {
			case ROLE_ADMIN -> PROPIETARIO;
			case ROLE_MANAGER -> ADMINISTRADOR;
			case ROLE_USER -> FAMILIA;
			case ROLE_RESPONSABLE_SEDE, ROLE_PROFESOR ->
				throw new IllegalArgumentException("El rol " + autoridad + " no se asigna desde la gestión de roles.");
		};
	}

	public static Set<Roles> autoridades(Collection<RolMembresia> roles) {
		Set<Roles> resultado = EnumSet.noneOf(Roles.class);
		for (RolMembresia rol : roles) {
			resultado.add(rol.autoridad());
		}
		return resultado;
	}

	/**
	 * Puerta de entrada (D5-1): una sesión de escuela solo se abre con {@code ROLE_ADMIN}, {@code ROLE_MANAGER} o
	 * {@code ROLE_USER}. Con las autoridades de siempre equivale a «tiene alguna». Además, si
	 * {@link #requiereAlumnoActivo}, hace falta algún alumno accesible.
	 */
	public static boolean abreSesion(Set<Roles> autoridades) {
		return autoridades.contains(Roles.ROLE_ADMIN)
				|| autoridades.contains(Roles.ROLE_MANAGER)
				|| autoridades.contains(Roles.ROLE_USER);
	}

	/**
	 * Regla ÚNICA de la puerta de entrada a una escuela (login, cambio de escuela y cada petición):
	 * {@link #abreSesion} y, si {@link #requiereAlumnoActivo}, algún alumno accesible. {@code algunAlumnoAccesible}
	 * solo se consulta en ese último caso.
	 */
	public static boolean puedeEntrar(Set<Roles> autoridades, BooleanSupplier algunAlumnoAccesible) {
		if (!abreSesion(autoridades)) {
			return false;
		}
		return !requiereAlumnoActivo(autoridades) || algunAlumnoAccesible.getAsBoolean();
	}

	/**
	 * Regla de acceso heredada: una cuenta que solo es familia (sin rol de gestión) necesita
	 * al menos un alumno accesible en la escuela ({@code AccesoFamiliaService.tieneAlgunoAccesible}).
	 * RESPONSABLE_SEDE y PROFESOR no son gestión: con FAMILIA, también la necesitan (D5-2).
	 */
	public static boolean requiereAlumnoActivo(Set<Roles> autoridades) {
		return autoridades.contains(Roles.ROLE_USER)
				&& !autoridades.contains(Roles.ROLE_ADMIN)
				&& !autoridades.contains(Roles.ROLE_MANAGER);
	}
}
