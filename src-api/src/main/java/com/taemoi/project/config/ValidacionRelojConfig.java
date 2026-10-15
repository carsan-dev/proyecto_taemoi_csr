package com.taemoi.project.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.validation.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.Validator;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.validation.ClockProvider;

/**
 * Fija el reloj de Bean Validation (@Past, @PastOrPresent...) a la zona de Madrid. Por defecto usa el
 * reloj de la JVM, que en CI y producción suele ir en UTC: entre las 00:00 y la 01:00/02:00 de Madrid
 * una fecha de "hoy" se rechazaría como futura. Es la misma zona fija que usan los servicios
 * (p. ej. {@code ConsentimientoService.ZONA}).
 *
 * Con {@code @EnableWebMvc} el validador de Spring MVC no pasa por la configuración de Boot, por eso
 * además se le entrega explícitamente el validador de Boot (ya con este reloj).
 */
@Configuration
public class ValidacionRelojConfig implements WebMvcConfigurer {

	public static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

	private final Validator validadorBoot;

	public ValidacionRelojConfig(@Qualifier("defaultValidator") Validator validadorBoot) {
		this.validadorBoot = validadorBoot;
	}

	@Bean
	public static ValidationConfigurationCustomizer relojValidacionMadrid() {
		return configuracion -> configuracion.clockProvider(proveedorDeReloj(Clock.system(ZONA)));
	}

	/** Proveedor con el reloj indicado (inyectable para poder fijarlo en los tests). */
	public static ClockProvider proveedorDeReloj(Clock reloj) {
		return () -> reloj;
	}

	@Override
	public Validator getValidator() {
		return validadorBoot;
	}
}
