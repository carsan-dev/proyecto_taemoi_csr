package com.taemoi.project.configuracion;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Clase de configuración para la configuración de la aplicación web.
 */
@Configuration
@EnableWebMvc
public class WebConfig implements WebMvcConfigurer {

	/**
	 * Agrega la configuración de CORS (Cross-Origin Resource Sharing) para permitir
	 * solicitudes desde el origen especificado.
	 *
	 * @param registry El registro de configuración de CORS.
	 */
	@Override
	public void addCorsMappings(@NonNull CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOrigins("http://localhost:4200", "http://localhost:80", "http://localhost", "http://elasticbeanstalk-us-east-1-368070052967.s3-website-us-east-1.amazonaws.com")
				.allowedMethods("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS", "PATCH", "PROPFIND", "PROPPATCH",
						"MKCOL", "COPY", "MOVE", "LOCK")
				.allowedHeaders("*").allowCredentials(true);
	}
}