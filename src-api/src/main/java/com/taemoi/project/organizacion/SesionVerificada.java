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
 * @param autoridades     autoridades de las membresías del usuario en {@code organizacionId}, ya traducidas
 *                        (vacías solo en la sesión de un administrador de plataforma sin membresía).
 * @param token           contenido verificado del JWT con el que se identificó.
 * @param adminPlataforma si la sesión lleva además {@code ROLE_ADMIN_PLATAFORMA}
 *                        ({@link com.taemoi.project.plataforma.AutoridadPlataforma}).
 * @param alcance         sedes y grupos de la sesión ({@link AlcanceSesion}); nulo equivale a
 *                        {@link AlcanceSesion#NINGUNO}.
 */
public record SesionVerificada(Usuario usuario, Long organizacionId, Set<Roles> autoridades, TokenJwt token,
		boolean adminPlataforma, AlcanceSesion alcance) {

	public static final String ATRIBUTO = SesionVerificada.class.getName();

	/** Marca que la identificación ya se hizo para esta petición (con o sin sesión). */
	public static final String ATRIBUTO_RESUELTA = SesionVerificada.class.getName() + ".resuelta";

	public SesionVerificada {
		autoridades = Set.copyOf(autoridades);
		alcance = alcance == null ? AlcanceSesion.NINGUNO : alcance;
	}

	/** Sesión sin la autoridad de plataforma y sin alcance ({@link AlcanceSesion#NINGUNO}). */
	public SesionVerificada(Usuario usuario, Long organizacionId, Set<Roles> autoridades, TokenJwt token) {
		this(usuario, organizacionId, autoridades, token, false, AlcanceSesion.NINGUNO);
	}
}
