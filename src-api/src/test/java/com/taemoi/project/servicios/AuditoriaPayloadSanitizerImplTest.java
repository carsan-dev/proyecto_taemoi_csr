package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.services.impl.AuditoriaPayloadSanitizerImpl;

class AuditoriaPayloadSanitizerImplTest {

	private final AuditoriaPayloadSanitizerImpl sanitizer = new AuditoriaPayloadSanitizerImpl(new ObjectMapper());

	@Test
	void sanitizarPayload_json_enmascaraCamposSensibles() {
		String payload = "{\"email\":\"admin@test.com\",\"password\":\"secret123\",\"token\":\"abc\",\"nested\":{\"nuevaContrasena\":\"xyz\"}}";

		String resultado = sanitizer.sanitizarPayload(payload, "application/json");

		assertTrue(resultado.contains("\"password\":\"***\""));
		assertTrue(resultado.contains("\"token\":\"***\""));
		assertTrue(resultado.contains("\"nuevaContrasena\":\"***\""));
		assertFalse(resultado.contains("secret123"));
		assertFalse(resultado.contains("abc"));
	}

	@Test
	void serializarQueryParams_enmascaraQuerySensible() {
		String resultado = sanitizer.serializarQueryParams(Map.of(
				"page", new String[] { "1" },
				"token", new String[] { "jwt-value" },
				"password", new String[] { "123456" }));

		assertTrue(resultado.contains("\"token\":\"***\""));
		assertTrue(resultado.contains("\"password\":\"***\""));
		assertTrue(resultado.contains("\"page\":\"1\""));
		assertFalse(resultado.contains("jwt-value"));
	}

	@Test
	void truncarSiSuperaLimite_truncaCorrectamente() {
		String valor = "1234567890";
		assertEquals("12345", sanitizer.truncarSiSuperaLimite(valor, 5));
		assertEquals("1234567890", sanitizer.truncarSiSuperaLimite(valor, 20));
	}
}
