package com.taemoi.project.services.impl;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.TokenJwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * Implementación del servicio JWT que proporciona funcionalidades para la
 * generación, validación y extracción de información de tokens JWT.
 */
@Service
public class JwtServiceImpl implements JwtService {

	private static final long DEFAULT_EXPIRATION_MILLIS = 1000L * 60 * 60 * 10;

	@Value("${jwt.secret}")
	private String jwtSigningKey;

	/**
	 * Extrae el nombre de usuario del token JWT.
	 *
	 * @param token Token JWT del cual se extraerá el nombre de usuario.
	 * @return El nombre de usuario extraído del token JWT.
	 */
	@Override
	public String extractUserName(String token) {
		return extractClaim(token, Claims::getSubject);
	}

	/**
	 * Genera un nuevo token JWT para el usuario proporcionado.
	 *
	 * @param userDetails Detalles del usuario para los cuales se generará el token.
	 * @return El token JWT generado para el usuario.
	 */
	@Override
	public String generateToken(UserDetails userDetails) {
		return generateToken(new HashMap<>(), userDetails, DEFAULT_EXPIRATION_MILLIS);
	}

	@Override
	public String generateToken(UserDetails userDetails, long expirationMillis) {
		return generateToken(new HashMap<>(), userDetails, expirationMillis);
	}

	@Override
	public String generateToken(UserDetails userDetails, long expirationMillis, Long organizacionId) {
		if (organizacionId == null) {
			throw new IllegalArgumentException("El token de sesión necesita organización");
		}
		Map<String, Object> claims = new HashMap<>();
		claims.put(CLAIM_ORGANIZACION, organizacionId);
		return generateToken(claims, userDetails, expirationMillis);
	}

	// Generar un token de refresco con mayor duración (no se usa hoy; lleva la organización como el de acceso)
	public String generateRefreshToken(UserDetails userDetails, Long organizacionId) {
		return generateToken(userDetails, 1000L * 60 * 60 * 24 * 7, organizacionId); // 7 días de validez
	}

	@Override
	public TokenJwt verificar(String token) {
		Claims claims = extractAllClaims(token); // firma + caducidad (lanza si no son válidas)
		Object org = claims.get(CLAIM_ORGANIZACION);
		Long organizacionId;
		if (org == null) {
			organizacionId = null;
		} else if (org instanceof Number numero) {
			organizacionId = numero.longValue();
		} else {
			throw new IllegalArgumentException("Claim org no numérico");
		}
		return new TokenJwt(claims.getSubject(), organizacionId, claims.getIssuedAt(), claims.getExpiration());
	}

	/**
	 * Verifica si un token JWT dado es válido para los detalles del usuario
	 * proporcionados.
	 *
	 * @param token       Token JWT que se verificará.
	 * @param userDetails Detalles del usuario para los cuales se verificará el
	 *                    token.
	 * @return true si el token es válido para los detalles del usuario, false de lo
	 *         contrario.
	 */
	@Override
	public boolean isTokenValid(String token, UserDetails userDetails) {
		final String username = extractUserName(token);
		return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
	}

	/**
	 * Extrae un reclamo específico del token JWT utilizando un resolvedor de
	 * reclamos.
	 *
	 * @param token           El token JWT del cual extraer el reclamo.
	 * @param claimsResolvers El resolvedor de reclamos que define qué reclamo
	 *                        extraer.
	 * @param <T>             El tipo de dato del reclamo.
	 * @return El reclamo extraído del token JWT.
	 */

	private <T> T extractClaim(String token, Function<Claims, T> claimsResolvers) {
		final Claims claims = extractAllClaims(token);
		return claimsResolvers.apply(claims);
	}

	/**
	 * Genera un token JWT utilizando los detalles del usuario y reclamos
	 * adicionales.
	 *
	 * @param extraClaims Reclamos adicionales para incluir en el token JWT.
	 * @param userDetails Detalles del usuario para el token JWT.
	 * @return El token JWT generado.
	 */
	private String generateToken(Map<String, Object> extraClaims, UserDetails userDetails, long expirationMillis) {
		return Jwts.builder().claims(extraClaims).subject(userDetails.getUsername())
				.issuedAt(new Date(System.currentTimeMillis()))
				.expiration(new Date(System.currentTimeMillis() + expirationMillis))
				.signWith(getSigningKey()).compact();
	}

	/**
	 * Verifica si un token JWT ha expirado.
	 *
	 * @param token El token JWT a verificar.
	 * @return true si el token ha expirado, false de lo contrario.
	 */
	private boolean isTokenExpired(String token) {
		return extractExpiration(token).before(new Date());
	}

	/**
	 * Extrae la fecha de expiración del token JWT.
	 *
	 * @param token El token JWT del cual extraer la fecha de expiración.
	 * @return La fecha de expiración del token JWT.
	 */
	private Date extractExpiration(String token) {
		return extractClaim(token, Claims::getExpiration);
	}

	/**
	 * Extrae todos los reclamos del token JWT.
	 *
	 * @param token El token JWT del cual extraer los reclamos.
	 * @return Todos los reclamos del token JWT.
	 */
	private Claims extractAllClaims(String token) {
		return Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
	}

	/**
	 * Obtiene la clave de firma utilizada para firmar el token JWT.
	 *
	 * @return La clave de firma.
	 */
	private SecretKey getSigningKey() {
		if (jwtSigningKey == null) {
			throw new IllegalStateException("JWT secret key is not set.");
		}
		byte[] keyBytes = Decoders.BASE64.decode(jwtSigningKey);
		return Keys.hmacShaKeyFor(keyBytes);
	}

}
