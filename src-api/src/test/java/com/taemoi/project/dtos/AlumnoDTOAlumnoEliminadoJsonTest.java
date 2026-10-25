package com.taemoi.project.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.entities.Alumno;

/**
 * Pieza 8b-3n (decisión 299), aditivo: {@code alumnoEliminado} solo sale en {@code GET /api/alumnos/{id}} (el servicio
 * lo rellena ahí); en las demás respuestas va nulo y no sale. Es solo de salida: si llega en el JSON del alta o de la
 * edición (que lee {@code new ObjectMapper()}, sin aceptar propiedades desconocidas), se ignora sin error.
 */
class AlumnoDTOAlumnoEliminadoJsonTest {

	private final ObjectMapper json = new ObjectMapper();

	@Test
	void sinRellenar_noSale() throws Exception {
		JsonNode nodo = json.valueToTree(AlumnoDTO.deAlumno(alumno()));

		assertFalse(nodo.has("alumnoEliminado"), "las demás respuestas no cambian");
	}

	@Test
	void relleno_saleTrueOFalse() throws Exception {
		AlumnoDTO dto = AlumnoDTO.deAlumno(alumno());
		dto.setAlumnoEliminado(false);
		assertFalse(json.valueToTree(dto).get("alumnoEliminado").asBoolean(true));

		dto.setAlumnoEliminado(true);
		assertTrue(json.valueToTree(dto).get("alumnoEliminado").asBoolean(false));
	}

	@Test
	void enLaEntrada_seIgnoraSinError() throws Exception {
		AlumnoDTO leido = json.readValue("{\"nombre\":\"Lucía\",\"apellidos\":\"Ortega\",\"alumnoEliminado\":true}",
				AlumnoDTO.class);

		assertEquals("Lucía", leido.getNombre());
		assertNull(leido.getAlumnoEliminado(), "solo de salida");
	}

	private static Alumno alumno() {
		Alumno alumno = new Alumno();
		alumno.setId(7L);
		alumno.setNombre("Lucía");
		alumno.setApellidos("Ortega");
		return alumno;
	}
}
