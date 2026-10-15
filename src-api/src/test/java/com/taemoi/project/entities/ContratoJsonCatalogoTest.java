package com.taemoi.project.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.catalogo.CatalogoTaemoiEnMemoria;

/**
 * V28 borra las columnas heredadas (deporte, tipo_tarifa, tipo_grado, tipo_categoria) pero el JSON que recibe el
 * frontend no cambia: los mismos campos con los mismos valores, ahora sacados del catálogo.
 */
class ContratoJsonCatalogoTest {

	private final ObjectMapper json = new ObjectMapper();

	@Test
	void alumnoDeporte_deporteYTipoTarifaComoAntes_sinCamposDelCatalogo() {
		AlumnoDeporte ad = new AlumnoDeporte();
		ad.setDisciplina(CatalogoTaemoiEnMemoria.disciplinaTaemoi("KICKBOXING"));
		ad.setTarifa(CatalogoTaemoiEnMemoria.tarifaTaemoi("PADRES_HIJOS"));
		Grado grado = new Grado();
		grado.setId(3L);
		grado.setCodigo("AMARILLO_NARANJA");
		ad.setGrado(grado);
		Categoria categoria = new Categoria();
		categoria.setId(5L);
		categoria.setNombre("Cadete");
		categoria.setCodigo("CADETE");
		ad.setCategoria(categoria);

		JsonNode nodo = json.valueToTree(ad);
		assertEquals("KICKBOXING", nodo.get("deporte").asText());
		assertEquals("PADRES_HIJOS", nodo.get("tipoTarifa").asText());
		assertEquals("AMARILLO_NARANJA", nodo.get("grado").get("tipoGrado").asText());
		assertEquals("CADETE", nodo.get("categoria").get("tipoCategoria").asText());
		assertEquals("Cadete", nodo.get("categoria").get("nombre").asText());
		for (String interno : new String[] { "disciplina", "tarifa", "codigoTarifa", "organizacionId" }) {
			assertFalse(nodo.has(interno), interno);
		}
		assertEquals(java.util.Set.of("id", "nombre", "tipoCategoria"), campos(nodo.get("categoria")));
	}

	@Test
	void sinTarifaNiCodigoDeCategoria_nulos() {
		AlumnoDeporte ad = new AlumnoDeporte();
		ad.setDisciplina(CatalogoTaemoiEnMemoria.disciplinaTaemoi("PILATES"));
		Categoria categoria = new Categoria();
		categoria.setNombre("Infantil");
		ad.setCategoria(categoria);
		JsonNode nodo = json.valueToTree(ad);
		assertTrue(nodo.get("tipoTarifa").isNull());
		assertTrue(nodo.get("categoria").get("tipoCategoria").isNull(), "las categorías sembradas no tenían tipo_categoria");
	}

	@Test
	void grupoConvocatoriaYPreinscripciones_deporteComoAntes() {
		Grupo grupo = new Grupo();
		grupo.setDisciplina(CatalogoTaemoiEnMemoria.disciplinaTaemoi("DEFENSA_PERSONAL_FEMENINA"));
		assertEquals("DEFENSA_PERSONAL_FEMENINA", json.valueToTree(grupo).get("deporte").asText());
		assertFalse(json.valueToTree(grupo).has("disciplina"));

		Convocatoria convocatoria = new Convocatoria();
		convocatoria.setDisciplina(CatalogoTaemoiEnMemoria.disciplinaTaemoi("TAEKWONDO"));
		assertEquals("TAEKWONDO", json.valueToTree(convocatoria).get("deporte").asText());

		PlantillaPreinscripcion plantilla = new PlantillaPreinscripcion();
		plantilla.setDisciplina(CatalogoTaemoiEnMemoria.disciplinaTaemoi("PILATES"));
		assertEquals("PILATES", json.valueToTree(plantilla).get("deporte").asText());
	}

	@Test
	void alumnoConvocatoria_gradosComoTexto() {
		AlumnoConvocatoria ac = new AlumnoConvocatoria();
		ac.setGradoActual("AZUL");
		ac.setGradoSiguiente("AZUL_ROJO");
		JsonNode nodo = json.valueToTree(ac);
		assertEquals("AZUL", nodo.get("gradoActual").asText());
		assertEquals("AZUL_ROJO", nodo.get("gradoSiguiente").asText());
	}

	private static java.util.Set<String> campos(JsonNode nodo) {
		java.util.Set<String> campos = new java.util.TreeSet<>();
		nodo.fieldNames().forEachRemaining(campos::add);
		return campos;
	}
}
