package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Web por plantilla, pieza W2 (decisión 466): hora, lugar y sede del evento, opcionales, en el alta
 * ({@code POST /api/eventos/crear}) y la edición ({@code PUT /api/eventos/{id}}) de hoy, contra MySQL.
 * <ul>
 * <li>Sin ellos, el JSON del evento es el de siempre (sin las claves nuevas): la web de Moiskimdo no cambia.</li>
 * <li>La edición del panel de hoy no los envía: si no llegan, se conservan; con {@code null} o en blanco, se vacían.</li>
 * <li>Hora {@code HH:mm}; lugar hasta 160 caracteres; la sede, de la misma escuela (otra escuela o inexistente: 400).</li>
 * </ul>
 * Escuelas sintéticas ({@link EscuelaConSedesDePrueba}). Datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventoDatosWebIntegrationTest extends MySqlIntegrationTest {

	/** Las claves del JSON de un evento de hoy (detalle), sin datos de la web. */
	private static final Set<String> CLAVES_DE_SIEMPRE = Set.of("id", "titulo", "descripcion", "fotoEvento",
			"documentos", "fechaEvento", "visible", "orden");
	private static final String SEDE_NO_EXISTE = "La sede del evento no existe.";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	String tGestor, tGestorOtra;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-w2-ev");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-w2-ev-otra");
		tGestor = e.token(jwtService, usuarios, e.gestor);
		tGestorOtra = otra.token(jwtService, usuarios, otra.gestor);
	}

	@AfterEach
	void sinFugas() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	@Test
	void sinDatosWeb_elJsonEsElDeSiempre() throws Exception {
		JsonNode creado = alta(Map.of("titulo", "IT W2 sin " + e.marca, "descripcion", "Desc",
				"fechaEvento", "2030-01-10"));
		assertEquals(CLAVES_DE_SIEMPRE, campos(creado));
		long id = creado.get("id").asLong();
		JsonNode detalle = leer(get("/api/eventos/" + id), tGestor, 200);
		assertEquals(CLAVES_DE_SIEMPRE, campos(detalle));
		JsonNode deLaLista = buscar(leer(get("/api/eventos"), tGestor, 200), id);
		assertEquals(add(CLAVES_DE_SIEMPRE, "numeroFotos"), campos(deLaLista), "la lista añade numeroFotos, como hoy");
		Map<String, Object> fila = jdbc.queryForMap("SELECT hora, lugar, sede_id FROM evento WHERE id = ?", id);
		assertNull(fila.get("hora"));
		assertNull(fila.get("lugar"));
		assertNull(fila.get("sede_id"));
	}

	@Test
	void conDatosWeb_altaYEdicion() throws Exception {
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("titulo", "IT W2 con " + e.marca);
		datos.put("descripcion", "Desc");
		datos.put("fechaEvento", "2030-02-10");
		datos.put("hora", "18:30");
		datos.put("lugar", "  Polideportivo municipal ");
		datos.put("sedeId", e.sA);
		JsonNode creado = alta(datos);
		long id = creado.get("id").asLong();
		assertEquals(add(CLAVES_DE_SIEMPRE, "hora", "lugar", "sedeId"), campos(creado));
		assertEquals("18:30", creado.get("hora").asText());
		assertEquals("Polideportivo municipal", creado.get("lugar").asText(), "recortado");
		assertEquals(e.sA, creado.get("sedeId").asLong());
		assertEquals("18:30:00", jdbc.queryForObject("SELECT CAST(hora AS CHAR) FROM evento WHERE id = ?", String.class,
				id));
		JsonNode detalle = leer(get("/api/eventos/" + id), tGestor, 200);
		assertEquals("18:30", detalle.get("hora").asText());

		// El cuerpo de la edición del panel de hoy (sin los campos nuevos): se conservan.
		JsonNode editado = edicion(id, Map.of("titulo", "IT W2 con editado " + e.marca, "descripcion", "Otra",
				"fechaEvento", "2030-02-11"), tGestor, 200);
		assertEquals("IT W2 con editado " + e.marca, editado.get("titulo").asText());
		assertEquals("18:30", editado.get("hora").asText());
		assertEquals("Polideportivo municipal", editado.get("lugar").asText());
		assertEquals(e.sA, editado.get("sedeId").asLong());

		// Cambiar y vaciar.
		Map<String, Object> cambio = new LinkedHashMap<>();
		cambio.put("titulo", "IT W2 con editado " + e.marca);
		cambio.put("descripcion", "Otra");
		cambio.put("fechaEvento", "2030-02-11");
		cambio.put("hora", "09:05");
		cambio.put("lugar", "   ");
		cambio.put("sedeId", e.sB);
		editado = edicion(id, cambio, tGestor, 200);
		assertEquals("09:05", editado.get("hora").asText());
		assertFalse(editado.has("lugar"), "en blanco: null, y null no sale");
		assertEquals(e.sB, editado.get("sedeId").asLong());

		cambio.put("hora", null);
		cambio.put("sedeId", null);
		editado = edicion(id, cambio, tGestor, 200);
		assertEquals(CLAVES_DE_SIEMPRE, campos(editado), "todo vacío: el JSON de siempre");
		Map<String, Object> fila = jdbc.queryForMap("SELECT hora, lugar, sede_id FROM evento WHERE id = ?", id);
		assertNull(fila.get("hora"));
		assertNull(fila.get("lugar"));
		assertNull(fila.get("sede_id"));
	}

	@Test
	void validaciones_400() throws Exception {
		long id = alta(Map.of("titulo", "IT W2 validar " + e.marca, "descripcion", "Desc", "fechaEvento", "2030-03-10",
				"lugar", "Pabellón", "sedeId", e.sA)).get("id").asLong();
		long total = eventosDe(e.org);

		for (Map.Entry<String, Object> malo : List.of(Map.entry("lugar", (Object) "l".repeat(161)),
				Map.entry("sedeId", (Object) otra.sA), Map.entry("sedeId", (Object) 999_999_999L),
				Map.entry("hora", (Object) "25:00"), Map.entry("hora", (Object) "tarde"))) {
			Map<String, Object> datos = new LinkedHashMap<>();
			datos.put("titulo", "IT W2 malo " + e.marca);
			datos.put("descripcion", "Desc");
			datos.put(malo.getKey(), malo.getValue());
			MvcResult a = peticion(multipart("/api/eventos/crear").param("nuevo", json.writeValueAsString(datos)),
					tGestor);
			assertEquals(400, a.getResponse().getStatus(), malo + ": " + contenido(a));
			MvcResult b = peticion(multipart(HttpMethod.PUT, "/api/eventos/" + id).param("eventoEditado",
					json.writeValueAsString(datos)), tGestor);
			assertEquals(400, b.getResponse().getStatus(), malo + ": " + contenido(b));
		}
		assertEquals(total, eventosDe(e.org), "no se crea ninguno");
		Map<String, Object> fila = jdbc.queryForMap("SELECT titulo, lugar, sede_id FROM evento WHERE id = ?", id);
		assertEquals("IT W2 validar " + e.marca, fila.get("titulo"), "no cambia");
		assertEquals("Pabellón", fila.get("lugar"));
		assertEquals(e.sA, ((Number) fila.get("sede_id")).longValue());

		// La sede de otra escuela, con su mensaje (aislamiento entre escuelas).
		Map<String, Object> ajena = new LinkedHashMap<>();
		ajena.put("titulo", "IT W2 ajena " + e.marca);
		ajena.put("descripcion", "Desc");
		ajena.put("sedeId", otra.sA);
		MvcResult r = peticion(multipart("/api/eventos/crear").param("nuevo", json.writeValueAsString(ajena)), tGestor);
		assertEquals(400, r.getResponse().getStatus(), contenido(r));
		assertEquals(SEDE_NO_EXISTE, json.readTree(contenido(r)).get("mensaje").asText());

		// En el límite, vale; y la otra escuela sí puede usar su sede.
		ajena.put("lugar", "𝔸".repeat(160));
		ajena.put("sedeId", otra.sA);
		r = peticion(multipart("/api/eventos/crear").param("nuevo", json.writeValueAsString(ajena)), tGestorOtra);
		assertEquals(201, r.getResponse().getStatus(), contenido(r));
		long creado = json.readTree(contenido(r)).get("id").asLong();
		assertEquals(otra.org, jdbc.queryForObject("SELECT organizacion_id FROM evento WHERE id = ?", Long.class,
				creado));
	}

	// ------------------------------------------------------------------ utilidades

	private JsonNode alta(Map<String, Object> datos) throws Exception {
		MvcResult r = peticion(multipart("/api/eventos/crear").param("nuevo", json.writeValueAsString(datos)), tGestor);
		assertEquals(201, r.getResponse().getStatus(), contenido(r));
		return json.readTree(contenido(r));
	}

	private JsonNode edicion(long id, Map<String, Object> datos, String token, int esperado) throws Exception {
		return leer(multipart(HttpMethod.PUT, "/api/eventos/" + id).param("eventoEditado",
				json.writeValueAsString(datos)), token, esperado);
	}

	private long eventosDe(long organizacion) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM evento WHERE organizacion_id = ?", Long.class, organizacion);
	}

	private JsonNode leer(MockHttpServletRequestBuilder peticion, String token, int esperado) throws Exception {
		MvcResult r = peticion(peticion, token);
		assertEquals(esperado, r.getResponse().getStatus(), contenido(r));
		return json.readTree(contenido(r));
	}

	private MvcResult peticion(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
	}

	private static String contenido(MvcResult r) throws Exception {
		return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	private static JsonNode buscar(JsonNode lista, long id) {
		for (JsonNode n : lista) {
			if (n.get("id").asLong() == id) {
				return n;
			}
		}
		throw new AssertionError("sin el evento " + id);
	}

	private static Set<String> add(Set<String> base, String... mas) {
		Set<String> todas = new HashSet<>(base);
		todas.addAll(List.of(mas));
		assertTrue(todas.size() == base.size() + mas.length);
		return todas;
	}

	private static Set<String> campos(JsonNode nodo) {
		Set<String> claves = new HashSet<>();
		for (Iterator<String> it = nodo.fieldNames(); it.hasNext();) {
			claves.add(it.next());
		}
		return claves;
	}
}
