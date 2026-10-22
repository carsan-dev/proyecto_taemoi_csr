package com.taemoi.project.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj de la aplicación como bean (pieza 8b-2): Europe/Madrid, no la zona de la JVM (la CI va en UTC). Lo inyecta
 * {@code PendienteVencidoService}; los tests lo sustituyen por uno fijo ({@code @MockitoBean Clock}).
 */
@Configuration
public class RelojConfig {

	@Bean
	public Clock reloj() {
		return Clock.system(ValidacionRelojConfig.ZONA);
	}
}
