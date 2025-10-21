package com.taemoi.project.config;

import java.util.concurrent.TimeUnit;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Configuración de cache para mejorar el rendimiento de la aplicación.
 * Utiliza Caffeine como proveedor de cache.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Configura el CacheManager con Caffeine.
     * Define diferentes caches con configuraciones específicas.
     *
     * @return CacheManager configurado
     */
    @Bean
    CacheManager cacheManager() {
		CaffeineCacheManager cacheManager = new CaffeineCacheManager(
			"alumnos",
			"grupos",
			"turnos",
			"eventos",
			"productos",
			"usuarios",
			"grados",
			"categorias"
		);

		cacheManager.setCaffeine(caffeineCacheBuilder());
		return cacheManager;
	}

	/**
	 * Configura el builder de Caffeine con parámetros de cache.
	 *
	 * @return Caffeine builder configurado
	 */
	Caffeine<Object, Object> caffeineCacheBuilder() {
		return Caffeine.newBuilder()
			.maximumSize(1000) // Máximo 1000 entradas por cache
			.expireAfterWrite(10, TimeUnit.MINUTES) // Expira después de 10 minutos de escritura
			.expireAfterAccess(5, TimeUnit.MINUTES) // Expira después de 5 minutos sin acceso
			.recordStats(); // Habilitar estadísticas de cache
	}
}
