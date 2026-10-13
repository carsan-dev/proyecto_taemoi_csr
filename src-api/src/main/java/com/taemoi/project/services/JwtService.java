package com.taemoi.project.services;

import org.springframework.security.core.userdetails.UserDetails;

public interface JwtService {

	/** Nombre del claim con la organización (escuela) activa de la sesión. */
	String CLAIM_ORGANIZACION = "org";

	String extractUserName(String token);

	/**
	 * Token SIN organización (formato anterior a la multi-escuela). Solo se mantiene por
	 * compatibilidad; el código de aplicación debe usar
	 * {@link #generateToken(UserDetails, long, Long)}.
	 */
	String generateToken(UserDetails userDetails);

	/** Ver {@link #generateToken(UserDetails)}. */
	String generateToken(UserDetails userDetails, long expirationMillis);

	/** Token de acceso con el claim {@value #CLAIM_ORGANIZACION}. */
	String generateToken(UserDetails userDetails, long expirationMillis, Long organizacionId);

	boolean isTokenValid(String token, UserDetails userDetails);

	/**
	 * Verifica firma y caducidad y devuelve el contenido del token.
	 *
	 * @throws io.jsonwebtoken.JwtException (o {@link IllegalArgumentException}) si la firma no es
	 *         válida, el token ha caducado, está mal formado o el claim {@code org} no es numérico.
	 */
	TokenJwt verificar(String token);
}
