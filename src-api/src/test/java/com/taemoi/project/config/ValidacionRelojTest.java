package com.taemoi.project.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.taemoi.project.dtos.request.ConsentimientoRequest;
import com.taemoi.project.dtos.request.PreinscripcionRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class ValidacionRelojTest {

	private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
	// 15/10 23:30 UTC = 16/10 01:30 en Madrid
	private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-15T23:30:00Z"), MADRID);

	private static ValidatorFactory fabrica;
	private static Validator validador;

	@BeforeAll
	static void crearValidador() {
		fabrica = Validation.byDefaultProvider().configure()
				.clockProvider(ValidacionRelojConfig.proveedorDeReloj(RELOJ))
				.buildValidatorFactory();
		validador = fabrica.getValidator();
	}

	@AfterAll
	static void cerrar() {
		fabrica.close();
	}

	@Test
	void consentimientoAceptaHoyEnMadridYRechazaMananaConElMensaje() {
		assertThat(validarFechaConsentimiento(LocalDate.of(2026, 10, 16))).isEmpty();
		Set<ConstraintViolation<ConsentimientoRequest>> futuras = validarFechaConsentimiento(LocalDate.of(2026, 10, 17));
		assertThat(futuras).singleElement()
				.extracting(ConstraintViolation::getMessage).isEqualTo("La fecha no puede ser futura");
	}

	@Test
	void preinscripcionRechazaHoyEnMadridComoFechaDeNacimiento() {
		assertThat(validador.validateValue(PreinscripcionRequest.class, "fechaNacimiento", LocalDate.of(2026, 10, 15)))
				.isEmpty();
		assertThat(validador.validateValue(PreinscripcionRequest.class, "fechaNacimiento", LocalDate.of(2026, 10, 16)))
				.hasSize(1);
	}

	@Test
	void validadorDeBootYDeMvcUsanElRelojDeMadrid() {
		// JVM en UTC (como la CI): sin esta configuración el reloj por defecto saldría en UTC, no en Madrid
		TimeZone original = TimeZone.getDefault();
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		try {
			comprobarRelojDeMadridEnLosValidadores();
		} finally {
			TimeZone.setDefault(original);
		}
	}

	private void comprobarRelojDeMadridEnLosValidadores() {
		new WebApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
				.withUserConfiguration(MvcConfig.class, ValidacionRelojConfig.class)
				.run(contexto -> {
					assertThat(contexto).hasNotFailed();
					for (String nombre : new String[] { "defaultValidator", "mvcValidator" }) {
						org.springframework.validation.Validator v =
								contexto.getBean(nombre, org.springframework.validation.Validator.class);
						assertThat(v).as(nombre).isInstanceOf(jakarta.validation.Validator.class);
						ZoneId zona = ((ValidatorFactory) v).getClockProvider().getClock().getZone();
						assertThat(zona).as(nombre).isEqualTo(MADRID);
					}
				});
	}

	private Set<ConstraintViolation<ConsentimientoRequest>> validarFechaConsentimiento(LocalDate fecha) {
		return validador.validateValue(ConsentimientoRequest.class, "fecha", fecha);
	}

	@EnableWebMvc
	static class MvcConfig {
	}
}
