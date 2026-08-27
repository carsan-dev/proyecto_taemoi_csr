package com.taemoi.project.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.taemoi.project.exceptions.preinscripcion.PreinscripcionDuplicadaException;

class GlobalExceptionHandlerTest {
	@Test
	void devuelveConflictParaPreinscripcionDuplicada() {
		GlobalExceptionHandler handler = new GlobalExceptionHandler();

		var respuesta = handler.handlePreinscripcionDuplicada(
				new PreinscripcionDuplicadaException("Preinscripción duplicada."));

		assertEquals(HttpStatus.CONFLICT, respuesta.getStatusCode());
		assertEquals("Preinscripción duplicada.", respuesta.getBody().get("mensaje"));
	}
}
