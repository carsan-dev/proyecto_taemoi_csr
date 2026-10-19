package com.taemoi.project.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
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
				// PATCH desde la 6b-3c (marca «Lo puede vender el profesor» de /api/productos).
				.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")
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

	/*
	 * /imagenes/** y /documentos/** ya no se sirven como recursos estáticos: los atiende
	 * ArchivoEstaticoController, que decide el acceso por escuela activa y fila Imagen/Documento.
	 */

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new RegistroPeticionesInterceptor());
	}

	/**
	 * Traza de cada petición. Con una excepción sin resolver, {@code afterCompletion} llega antes de que el
	 * despacho de error fije el estado final (500, 403...): el estado de la respuesta aún es el inicial (200), así
	 * que no se registra como completada; el despacho de error ({@code /error}) deja después su propia traza.
	 */
	static final class RegistroPeticionesInterceptor implements HandlerInterceptor {

		@Override
		public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
				@NonNull Object handler) {
			String uri = request.getRequestURI();
			String method = request.getMethod();
			logger.info("***** REQUEST INTERCEPTED: {} {} *****", method, uri);
			logger.info("***** Remote Address: {}", request.getRemoteAddr());
			return true;
		}

		@Override
		public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
				@NonNull Object handler, @Nullable Exception ex) {
			String uri = request.getRequestURI();
			if (ex != null) {
				logger.error("***** REQUEST FAILED: {} - Unhandled exception (final status set by error dispatch): {}",
						uri, ex.getMessage());
				return;
			}
			logger.info("***** REQUEST COMPLETED: {} - Status: {} *****", uri, response.getStatus());
		}
	}

}