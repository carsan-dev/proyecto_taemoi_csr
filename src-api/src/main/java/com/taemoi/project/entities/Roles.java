package com.taemoi.project.entities;

/**
 * Autoridades de una sesión de escuela. {@code ROLE_RESPONSABLE_SEDE} y {@code ROLE_PROFESOR} (entrega 5b) solo pasan
 * las reglas que las nombran: fallan cerradas. La sesión con {@code ROLE_PROFESOR} sin FAMILIA ni gestión (decisiones 180 y
 * 184, también con {@code ROLE_RESPONSABLE_SEDE}) queda además limitada a su caja en la capa de URL ({@code SesionSoloProfesorAuthorizationManager}).
 */
public enum Roles {
	ROLE_USER, ROLE_ADMIN, ROLE_MANAGER, ROLE_RESPONSABLE_SEDE, ROLE_PROFESOR
}
