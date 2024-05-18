package com.taemoi.project.configuracion;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Clase de configuración para la configuración de la aplicación web.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Agrega la configuración de CORS (Cross-Origin Resource Sharing) para permitir solicitudes desde el origen especificado.
     *
     * @param registry El registro de configuración de CORS.
     */
    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:4200")
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}