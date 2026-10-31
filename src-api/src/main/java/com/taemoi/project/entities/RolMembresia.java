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
 * autoridades propias; solo pasan las reglas que las nombran (fallan cerradas). Las dos abren sesión
 * ({@link #abreSesion}): RESPONSABLE_SEDE, limitada a sus sedes (decisión 370); PROFESOR sin FAMILIA, gestión ni
 * RESPONSABLE_SEDE, limitada a su caja ({@link #sesionSoloProfesor}, decisión 180); RESPONSABLE_SEDE + PROFESOR entra
 * como responsable y suma Mi caja y Mis grupos (decisión 371, sustituye a la 184); con FAMILIA, PROFESOR entra como
 * familia.</li>
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

	public static Set<Roles> autoridades(Collection<RolMembresia> roles) {
		Set<Roles> resultado = EnumSet.noneOf(Roles.class);
		for (RolMembresia rol : roles) {
			resultado.add(rol.autoridad());
		}
		return resultado;
	}

	/**
	 * Puerta de entrada (D5-1, matizada por la decisión 180; la D6-15 la cierra la 370, pieza 9-5): una sesión de escuela
	 * se abre con {@code ROLE_ADMIN}, {@code ROLE_MANAGER}, {@code ROLE_USER}, {@code ROLE_RESPONSABLE_SEDE} (limitada a
	 * sus sedes por {@code @acceso} y {@code GestionPorSede}; sin sedes entra y no ve nada, decisión 65) o
	 * {@code ROLE_PROFESOR} (solo, una sesión limitada a su caja: {@link #sesionSoloProfesor}). Además, si
	 * {@link #requiereAlumnoActivo}, hace falta algún alumno accesible.
	 */
	public static boolean abreSesion(Set<Roles> autoridades) {
		return autoridades.contains(Roles.ROLE_ADMIN)
				|| autoridades.contains(Roles.ROLE_MANAGER)
				|| autoridades.contains(Roles.ROLE_USER)
				|| autoridades.contains(Roles.ROLE_RESPONSABLE_SEDE)
				|| autoridades.contains(Roles.ROLE_PROFESOR);
	}

	/**
	 * Sesión solo-profesor (decisión 180): tiene {@code ROLE_PROFESOR} y ningún otro rol de escuela. La cadena de
	 * seguridad la limita a su caja, a lo imprescindible de sesión y a lo público
	 * ({@code SesionSoloProfesorAuthorizationManager}). Con {@code ROLE_RESPONSABLE_SEDE} ya no lo es (decisión 371, que
	 * sustituye a la 184): entra como responsable.
	 */
	public static boolean sesionSoloProfesor(Set<Roles> autoridades) {
		return autoridades.contains(Roles.ROLE_PROFESOR)
				&& !autoridades.contains(Roles.ROLE_ADMIN)
				&& !autoridades.contains(Roles.ROLE_MANAGER)
				&& !autoridades.contains(Roles.ROLE_USER)
				&& !autoridades.contains(Roles.ROLE_RESPONSABLE_SEDE);
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
	 * PROFESOR no es gestión: con FAMILIA, también la necesita (D5-2). RESPONSABLE_SEDE ya no (pieza 9-5): entra por sí
	 * mismo, así que quedarse sin alumnos como familia no le cierra su panel de sede.
	 */
	public static boolean requiereAlumnoActivo(Set<Roles> autoridades) {
		return autoridades.contains(Roles.ROLE_USER)
				&& !autoridades.contains(Roles.ROLE_ADMIN)
				&& !autoridades.contains(Roles.ROLE_MANAGER)
				&& !autoridades.contains(Roles.ROLE_RESPONSABLE_SEDE);
	}
}
