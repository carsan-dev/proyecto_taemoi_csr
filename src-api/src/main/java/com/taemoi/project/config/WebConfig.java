package com.taemoi.project.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Clase de configuración para la configuración de la aplicación web.
 */
@Configuration
@EnableWebMvc
public class WebConfig implements WebMvcConfigurer {

	@Value("${cors.allowed.origin}")
	private String allowedOrigin;

	/**
	 * Agrega la configuración de CORS (Cross-Origin Resource Sharing) para permitir
	 * solicitudes desde el origen especificado.
	 *
	 * @param registry El registro de configuración de CORS.
	 */
	@Override
	public void addCorsMappings(@NonNull CorsRegistry registry) {
		// Split the allowed origins by comma to support multiple origins
		String[] allowedOrigins = allowedOrigin.split(",");
		registry.addMapping("/api/**")
				.allowedOrigins(allowedOrigins)
				.allowedMethods("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS").allowedHeaders("*")
				.allowCredentials(true);
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		String linuxPathImagenes = "/var/www/app/imagenes/";
		String userProfile = System.getenv("USERPROFILE");
		String windowsPathImagenes = Path.of(userProfile, "static_resources", "imagenes").toString().replace("\\",
				"/");

		registry.addResourceHandler("/imagenes/**")
				.addResourceLocations("file:" + linuxPathImagenes, "file:" + windowsPathImagenes + "/")
				.setCachePeriod(3600);

		String linuxPathDocumentos = "/var/www/app/documentos/";
		String windowsPathDocumentos = Path.of(userProfile, "static_resources", "documentos").toString().replace("\\",
				"/");

		registry.addResourceHandler("/documentos/**")
				.addResourceLocations("file:" + linuxPathDocumentos, "file:" + windowsPathDocumentos + "/")
				.setCachePeriod(3600);
	}

}