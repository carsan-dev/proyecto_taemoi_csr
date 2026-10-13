package com.taemoi.project.organizacion;

import java.util.Set;

import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.services.TokenJwt;

/**
 * Resultado de verificar el JWT de una petición (firma, caducidad, usuario y membresía activa en la
 * organización). Lo calcula UNA vez {@link OrganizacionRequestFilter} y lo consume
 * {@code JwtAuthenticationFilter} desde el atributo de petición {@link #ATRIBUTO}.
 *
 * @param autoridades autoridades del usuario en {@code organizacionId}, ya traducidas.
 * @param token       contenido verificado del JWT con el que se identificó.
 */
public record SesionVerificada(Usuario usuario, Long organizacionId, Set<Roles> autoridades, TokenJwt token) {

	public static final String ATRIBUTO = SesionVerificada.class.getName();

	/** Marca que la identificación ya se hizo para esta petición (con o sin sesión). */
	public static final String ATRIBUTO_RESUELTA = SesionVerificada.class.getName() + ".resuelta";

	public SesionVerificada {
		autoridades = Set.copyOf(autoridades);
	}
}
