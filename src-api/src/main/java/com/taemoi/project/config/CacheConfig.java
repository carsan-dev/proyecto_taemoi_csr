package com.taemoi.project.config;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.taemoi.project.organizacion.CacheManagerPorOrganizacion;
import com.taemoi.project.organizacion.ResolutorOrganizacion;

/**
 * Configuración de cache para mejorar el rendimiento de la aplicación.
 * Utiliza Caffeine como proveedor de cache.
 */
@Configuration
@EnableCaching
public class CacheConfig {

	/** Únicos nombres de caché admitidos. */
	static final List<String> NOMBRES_CACHE = List.of(
			"alumnos",
			"grupos",
			"turnos",
			"eventos",
			"productos",
			"usuarios",
			"grados",
			"categorias");

    /**
     * Configura el CacheManager con Caffeine, separado por organización: cada nombre de caché
     * tiene una instancia propia por escuela (ver {@link CacheManagerPorOrganizacion}).
     *
     * @return CacheManager configurado
     */
    @Bean
    CacheManager cacheManager(ResolutorOrganizacion resolutorOrganizacion) {
		// Sin nombres fijos: modo dinámico, crea "alumnos@org-<id>" bajo demanda con la misma configuración.
		CaffeineCacheManager caffeine = new CaffeineCacheManager();
		caffeine.setCaffeine(caffeineCacheBuilder());
		return new CacheManagerPorOrganizacion(caffeine, NOMBRES_CACHE, resolutorOrganizacion::organizacionActual);
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
