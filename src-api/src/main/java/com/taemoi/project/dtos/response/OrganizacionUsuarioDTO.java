package com.taemoi.project.dtos.response;

import java.util.List;

/**
 * Escuela a la que pertenece el usuario autenticado ({@code GET /api/auth/organizaciones}).
 *
 * @param roles  autoridades que tiene en esa escuela, con los nombres actuales ({@code ROLE_*}).
 * @param activa si es la escuela de la sesión actual.
 */
public record OrganizacionUsuarioDTO(Long id, String slug, String nombre, List<String> roles, boolean activa) {
}
