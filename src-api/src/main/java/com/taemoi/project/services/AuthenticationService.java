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

	/**
	 * Pieza E8-4: abre sesión (sin «recordarme») a una cuenta que acaba de crear su contraseña con una invitación, en la
	 * escuela que elegiría el login ({@code MembresiaService#elegirOrganizacionParaLogin}: con varias, la de la
	 * invitación, que es su última).
	 *
	 * @throws org.springframework.security.authentication.DisabledException con el mismo motivo que {@link #signin} si
	 *         ninguna escuela abre sesión
	 */
	JwtAuthenticationResponse iniciarSesionTrasInvitacion(com.taemoi.project.entities.Usuario usuario);
}
