package com.taemoi.project.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Las columnas del catálogo (V23) no cambian el JSON de {@link Grado} que devuelve la API. */
class GradoJsonTest {

	@Test
	void soloSeExponenIdYTipoGrado() throws Exception {
		Grado grado = new Grado();
		grado.setId(7L);
		grado.setCodigo("ROJO");
		grado.setNombre("MARRÓN");
		grado.setOrden(11);
		grado.setAplicaA(AplicaGrado.TODOS);
		grado.setMesesMinimosMenor(12);
		grado.setMesesMinimosAdulto(12);
		grado.setBloqueTemario("b06_rojo_a_negro_1");
		grado.setSistemaGrados(new SistemaGrados());

		JsonNode json = new ObjectMapper().valueToTree(grado);

		List<String> campos = new ArrayList<>();
		json.fieldNames().forEachRemaining(campos::add);
		assertEquals(List.of("id", "tipoGrado"), campos.stream().sorted().toList());
		assertEquals("ROJO", json.get("tipoGrado").asText());
	}
}
