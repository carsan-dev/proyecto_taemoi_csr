package com.taemoi.project.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Clase de configuración para la configuración de la aplicación web.
 */
@Configuration
@EnableWebMvc
public class WebConfig implements WebMvcConfigurer {

	@Value("${cors.allowed.origin}")
	private String allowedOrigin;

	@Value("${app.imagenes.directorio.linux}")
	private String directorioImagenesLinux;

	@Value("${app.imagenes.directorio.windows}")
	private String directorioImagenesWindows;

	@Value("${app.documentos.directorio.linux}")
	private String directorioDocumentosLinux;

	@Value("${app.documentos.directorio.windows}")
	private String directorioDocumentosWindows;

	/**
	 * Agrega la configuración de CORS (Cross-Origin Resource Sharing) para permitir
	 * solicitudes desde el origen especificado.
	 *
	 * @param registry El registro de configuración de CORS.
	 */
	@Override
	public void addCorsMappings(@NonNull CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOrigins(allowedOrigin)
				.allowedMethods("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS").allowedHeaders("*")
				.allowCredentials(true);
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		String userProfile = System.getenv("USERPROFILE");

		// Determine which paths to use based on OS
		if (userProfile != null) {
			// Windows environment - use Windows paths from environment variables
			registry.addResourceHandler("/imagenes/**")
					.addResourceLocations("file:" + directorioImagenesWindows)
					.setCachePeriod(3600);
			registry.addResourceHandler("/documentos/**")
					.addResourceLocations("file:" + directorioDocumentosWindows)
					.setCachePeriod(3600);
		} else {
			// Linux/Docker environment - use Linux paths from environment variables
			registry.addResourceHandler("/imagenes/**")
					.addResourceLocations("file:" + directorioImagenesLinux)
					.setCachePeriod(3600);
			registry.addResourceHandler("/documentos/**")
					.addResourceLocations("file:" + directorioDocumentosLinux)
					.setCachePeriod(3600);
		}
	}

}