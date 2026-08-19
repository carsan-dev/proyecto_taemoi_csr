package com.taemoi.project.dtos.request;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import com.taemoi.project.entities.Deporte;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class PreinscripcionRequestValidationTest {
	private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
	private static final Validator VALIDATOR = FACTORY.getValidator();

	@AfterAll
	static void cerrar() {
		FACTORY.close();
	}

	@Test
	void permiteMenorSinDniCuandoTieneResponsable() {
		PreinscripcionRequest request = request(null, LocalDate.of(2015, 1, 1), "María García", "87654321X", false);

		assertTrue(VALIDATOR.validate(request).isEmpty());
	}

	@Test
	void exigeDniAUnaPersonaAdulta() {
		PreinscripcionRequest request = request(null, LocalDate.of(1990, 1, 1), null, null, false);

		assertTrue(VALIDATOR.validate(request).stream().anyMatch(v -> v.getMessage().contains("personas adultas")));
	}

	@Test
	void exigeResponsableValidoAlMenor() {
		PreinscripcionRequest request = request(null, LocalDate.of(2015, 1, 1), "", "incorrecto", false);

		assertFalse(VALIDATOR.validate(request).isEmpty());
	}

	@Test
	void exigeRespuestaExplicitaSobreDiscapacidad() {
		PreinscripcionRequest request = request("12345678Z", LocalDate.of(1990, 1, 1), null, null, null);

		assertTrue(VALIDATOR.validate(request).stream().anyMatch(v -> v.getPropertyPath().toString().equals("tieneDiscapacidad")));
	}

	private PreinscripcionRequest request(String dni, LocalDate nacimiento, String tutor, String tutorDni,
			Boolean discapacidad) {
		return new PreinscripcionRequest(Deporte.TAEKWONDO, 7L, null, "Ana", "García López", dni,
				nacimiento, "Calle Mayor 1", "612 345 678", "611 222 333", "ana@example.com",
				tutor, tutorDni, discapacidad, false, true, "María García", "data:image/png;base64,AAAA");
	}
}
