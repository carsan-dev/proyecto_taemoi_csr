package com.taemoi.project.dtos.response;

import java.util.List;

/**
 * Escuela a la que pertenece el usuario autenticado ({@code GET /api/auth/organizaciones}).
 *
 * @param roles         autoridades que tiene en esa escuela, con los nombres actuales ({@code ROLE_*}).
 * @param activa        si es la escuela de la sesión actual.
 * @param logo          de dónde sale el logo de la escuela (misma regla que {@code GET /api/escuela/marca}).
 * @param logoVersion   id de la imagen del logo subido; {@code null} sin logo subido.
 * @param colorPrimario color de la escuela ({@code #RRGGBB}); {@code null} sin color.
 * @param numeroSedes   sedes activas de la escuela.
 */
public record OrganizacionUsuarioDTO(Long id, String slug, String nombre, List<String> roles, boolean activa,
		MarcaPanelDTO.Logo logo, Long logoVersion, String colorPrimario, int numeroSedes) {
}
