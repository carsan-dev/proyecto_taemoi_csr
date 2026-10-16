package com.taemoi.project.entities;

/**
 * Autoridades de una sesión de escuela. {@code ROLE_RESPONSABLE_SEDE} y {@code ROLE_PROFESOR} (entrega 5b) no
 * pasan ninguna regla todavía: fallan cerradas hasta que las entregas 6 y 7 abran sus endpoints.
 */
public enum Roles {
	ROLE_USER, ROLE_ADMIN, ROLE_MANAGER, ROLE_RESPONSABLE_SEDE, ROLE_PROFESOR
}
