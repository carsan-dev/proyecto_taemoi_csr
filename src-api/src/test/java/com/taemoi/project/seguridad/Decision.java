package com.taemoi.project.seguridad;

/**
 * Decisión de autorización declarada o calculada para un endpoint y un rol.
 *
 * <ul>
 * <li>{@link #PERMITE}: la seguridad declarativa deja pasar sin mirar el recurso.</li>
 * <li>{@link #ALCANCE}: deja pasar SOLO si el recurso está en el alcance o el acceso de la sesión (lo decide
 * {@code @acceso} consultando datos; entrega 5c-2).</li>
 * <li>{@link #DENIEGA}: no deja pasar.</li>
 * </ul>
 */
enum Decision {
	PERMITE, DENIEGA, ALCANCE
}
