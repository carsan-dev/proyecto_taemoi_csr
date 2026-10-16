package com.taemoi.project.entities;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * Rol de un usuario dentro de una organización ({@link Membresia}).
 *
 * <p>Traducción PROVISIONAL a las autoridades actuales (decisión 9 de
 * docs/multi-escuela/modelo-datos.md), para no reescribir los {@code @PreAuthorize}:
 * <ul>
 * <li>{@code PROPIETARIO} → {@code ROLE_ADMIN}</li>
 * <li>{@code ADMINISTRADOR} → {@code ROLE_MANAGER}</li>
 * <li>{@code FAMILIA} → {@code ROLE_USER}</li>
 * <li>{@code RESPONSABLE_SEDE} y {@code PROFESOR} → {@code ROLE_MANAGER} <b>provisionalmente</b>: ven todo lo
 * que ve un administrador de la escuela. La limitación por sede o grupo llega en la fase 3.</li>
 * </ul>
 */
public enum RolMembresia {
	PROPIETARIO(Roles.ROLE_ADMIN),
	ADMINISTRADOR(Roles.ROLE_MANAGER),
	RESPONSABLE_SEDE(Roles.ROLE_MANAGER),
	PROFESOR(Roles.ROLE_MANAGER),
	FAMILIA(Roles.ROLE_USER);

	private final Roles autoridad;

	RolMembresia(Roles autoridad) {
		this.autoridad = autoridad;
	}

	public Roles autoridad() {
		return autoridad;
	}

	/** Rol canónico que se crea al asignar una autoridad desde la API actual. */
	public static RolMembresia desdeAutoridad(Roles autoridad) {
		return switch (autoridad) {
			case ROLE_ADMIN -> PROPIETARIO;
			case ROLE_MANAGER -> ADMINISTRADOR;
			case ROLE_USER -> FAMILIA;
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
	 * Regla de acceso heredada: una cuenta que solo es familia (sin rol de gestión) necesita
	 * al menos un alumno accesible en la escuela ({@code AccesoFamiliaService.tieneAlgunoAccesible}).
	 */
	public static boolean requiereAlumnoActivo(Set<Roles> autoridades) {
		return autoridades.contains(Roles.ROLE_USER)
				&& !autoridades.contains(Roles.ROLE_ADMIN)
				&& !autoridades.contains(Roles.ROLE_MANAGER);
	}
}
