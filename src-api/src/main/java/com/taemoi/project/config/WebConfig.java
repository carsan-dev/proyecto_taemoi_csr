package com.taemoi.project.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Clase de configuración para la configuración de la aplicación web.
 */
@Configuration
@EnableWebMvc
public class WebConfig implements WebMvcConfigurer {

	private static final Logger logger = LoggerFactory.getLogger(WebConfig.class);

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
		// API endpoints CORS
		registry.addMapping("/api/**")
				.allowedOrigins(allowedOrigin)
				.allowedMethods("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS")
				.allowedHeaders("*")
				.allowCredentials(true);

		// Static resources CORS - allow direct file access
		registry.addMapping("/imagenes/**")
				.allowedOrigins(allowedOrigin)
				.allowedMethods("GET", "HEAD", "OPTIONS")
				.allowedHeaders("*")
				.allowCredentials(true);

		registry.addMapping("/documentos/**")
				.allowedOrigins(allowedOrigin)
				.allowedMethods("GET", "HEAD", "OPTIONS")
				.allowedHeaders("*")
				.allowCredentials(true);
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		String userProfile = System.getenv("USERPROFILE");

		logger.info("========================================");
		logger.info("Configuring Resource Handlers for static files");
		logger.info("USERPROFILE env variable: {}", userProfile);

		// Determine which paths to use based on OS
		if (userProfile != null) {
			// Windows environment - use Windows paths from environment variables
			// Replace %USERPROFILE% placeholder with actual user profile path
			String imagenesPath = directorioImagenesWindows.replace("%USERPROFILE%", userProfile);
			String documentosPath = directorioDocumentosWindows.replace("%USERPROFILE%", userProfile);

			logger.info("Windows environment detected");
			logger.info("Imagenes handler: /imagenes/** -> file:{}", imagenesPath);
			logger.info("Documentos handler: /documentos/** -> file:{}", documentosPath);

			registry.addResourceHandler("/imagenes/**")
					.addResourceLocations("file:" + imagenesPath)
					.setCachePeriod(3600);
			registry.addResourceHandler("/documentos/**")
					.addResourceLocations("file:" + documentosPath)
					.setCachePeriod(3600);
		} else {
			// Linux/Docker environment - use Linux paths from environment variables
			logger.info("Linux/Docker environment detected");
			logger.info("Imagenes handler: /imagenes/** -> file:{}", directorioImagenesLinux);
			logger.info("Documentos handler: /documentos/** -> file:{}", directorioDocumentosLinux);

			registry.addResourceHandler("/imagenes/**")
					.addResourceLocations("file:" + directorioImagenesLinux)
					.setCachePeriod(3600);
			registry.addResourceHandler("/documentos/**")
					.addResourceLocations("file:" + directorioDocumentosLinux)
					.setCachePeriod(3600);
		}
		logger.info("========================================");
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new HandlerInterceptor() {
			@Override
			public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
				String uri = request.getRequestURI();
				String method = request.getMethod();
				logger.info("***** REQUEST INTERCEPTED: {} {} *****", method, uri);
				logger.info("***** Remote Address: {}", request.getRemoteAddr());
				logger.info("***** Response will be sent with status: {} *****", response.getStatus());
				return true;
			}

			@Override
			public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
				String uri = request.getRequestURI();
				int status = response.getStatus();
				logger.info("***** REQUEST COMPLETED: {} - Status: {} *****", uri, status);
				if (ex != null) {
					logger.error("***** EXCEPTION during request: {}", ex.getMessage());
				}
			}
		});
	}

}