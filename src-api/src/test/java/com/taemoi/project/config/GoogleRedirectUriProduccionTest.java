package com.taemoi.project.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/**
 * Fase 4, pieza 3: en producción, sin {@code GOOGLE_REDIRECT_URI}, la {@code redirect-uri} de Google es la plantilla
 * que Spring rellena con el Host de cada petición ({@code SesionYArchivosPorHostIntegrationTest} comprueba que en
 * moiskimdo.es da la URI de hoy). Con la variable puesta (el {@code .env} del servidor hoy), manda la variable. El
 * perfil local no cambia.
 */
class GoogleRedirectUriProduccionTest {

	private static final String CLAVE = "spring.security.oauth2.client.registration.google.redirect-uri";

	@Test
	void produccionSinVariable_plantillaPorHost() throws Exception {
		assertEquals("{baseUrl}/login/oauth2/code/{registrationId}",
				resolver("application-production.properties", Map.of()));
	}

	@Test
	void produccionConVariable_mandaLaVariable() throws Exception {
		assertEquals("https://moiskimdo.es/login/oauth2/code/google", resolver("application-production.properties",
				Map.of("GOOGLE_REDIRECT_URI", "https://moiskimdo.es/login/oauth2/code/google")));
	}

	@Test
	void localSinVariable_comoAntes() throws Exception {
		assertEquals("http://localhost:8080/login/oauth2/code/google", resolver("application.properties", Map.of()));
	}

	private static String resolver(String fichero, Map<String, Object> entorno) throws Exception {
		Properties propiedades = PropertiesLoaderUtils.loadProperties(new ClassPathResource(fichero));
		MutablePropertySources fuentes = new MutablePropertySources();
		fuentes.addLast(new MapPropertySource("entorno", entorno));
		fuentes.addLast(new PropertiesPropertySource(fichero, propiedades));
		return new PropertySourcesPropertyResolver(fuentes).getProperty(CLAVE);
	}
}
