package com.taemoi.project.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Configuración del codificador de contraseñas.
 * Esta clase está separada de SecurityConfiguration para evitar dependencias circulares.
 */
@Configuration
public class PasswordEncoderConfig {

	/**
	 * Crea un codificador de contraseñas BCrypt.
	 *
	 * @return Un objeto PasswordEncoder.
	 */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
