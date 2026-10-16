package com.taemoi.project.organizacion;

import java.util.Collection;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import com.taemoi.project.entities.Usuario;

/**
 * Autenticación de una sesión de escuela que deja {@code JwtAuthenticationFilter} (entrega 5b-2): la misma que antes
 * (principal {@link Usuario}, sin credenciales, autenticada, con las autoridades de la sesión) más el
 * {@link AlcanceSesion} tipado. Extiende {@link UsernamePasswordAuthenticationToken}, así que los {@code instanceof}
 * y {@code getPrincipal()} de hoy siguen valiendo. Leer el alcance con {@link AlcanceActual}.
 */
public class AutenticacionEscuela extends UsernamePasswordAuthenticationToken {

	private static final long serialVersionUID = 1L;

	private final AlcanceSesion alcance;

	public AutenticacionEscuela(Usuario usuario, Collection<? extends GrantedAuthority> autoridades,
			AlcanceSesion alcance) {
		super(usuario, null, autoridades);
		this.alcance = alcance == null ? AlcanceSesion.NINGUNO : alcance;
	}

	public AlcanceSesion alcance() {
		return alcance;
	}
}
