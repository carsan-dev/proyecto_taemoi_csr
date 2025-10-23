package com.taemoi.project.services;

import org.springframework.security.oauth2.core.user.OAuth2User;

import com.taemoi.project.entities.Usuario;

/**
 * Servicio para gestionar la autenticación OAuth2 y la creación automática de usuarios.
 */
public interface OAuth2UserService {

	/**
	 * Procesa el login de OAuth2 y crea automáticamente un usuario si existe
	 * un alumno con el email proporcionado por el proveedor OAuth2.
	 *
	 * @param oauth2User El usuario OAuth2 autenticado.
	 * @param registrationId El ID del proveedor de autenticación (ej. "google").
	 * @return El usuario creado o encontrado, o null si no existe alumno con ese email.
	 */
	Usuario processOAuth2Login(OAuth2User oauth2User, String registrationId);
}
