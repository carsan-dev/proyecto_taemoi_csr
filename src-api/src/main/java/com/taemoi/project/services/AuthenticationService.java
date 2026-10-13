package com.taemoi.project.services;

import com.taemoi.project.dtos.request.LoginRequest;
import com.taemoi.project.dtos.request.RegistroRequest;
import com.taemoi.project.dtos.response.JwtAuthenticationResponse;

public interface AuthenticationService {

	JwtAuthenticationResponse signup(RegistroRequest request);

	JwtAuthenticationResponse signin(LoginRequest request);

	/**
	 * Emite un token para otra escuela del usuario, con la misma caducidad que la sesión actual.
	 *
	 * @return vacío si el usuario no puede entrar en esa escuela (sin membresía activa, escuela
	 *         inactiva o familia sin alumnos activos allí).
	 */
	java.util.Optional<JwtAuthenticationResponse> cambiarOrganizacion(com.taemoi.project.entities.Usuario usuario,
			Long organizacionId, java.util.Date expiraEn);
}
