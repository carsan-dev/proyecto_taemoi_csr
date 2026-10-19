package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza 6b-3d contra MySQL: {@code GET /api/productos} admite {@code tipo} y {@code vendibleProfesor}, opcionales y
 * combinables entre sí y con {@code concepto}; pagina en la base de datos (totalElements es el real), responde 400 con
 * {@code {"mensaje"}} ante un tipo inválido y no cambia nada sin los parámetros nuevos. Solo salen productos de la
 * escuela activa. Escuelas sintéticas ({@link EscuelaConSedesDePrueba}); datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductoFiltrosListadoIntegrationTest extends MySqlIntegrationTest {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	String tGestor, tResp, tFamilia, tGestorOtra;
	String marca;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-6b3d");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-6b3d-otra");
		marca = "F6B3D" + e.marca;
		long familia = e.cuentaFamilia(usuarios, "familia");
		tGestor = e.token(jwtService, usuarios, e.gestor);
		tResp = e.token(jwtService, usuarios, e.respA);
		tFamilia = e.token(jwtService, usuarios, familia);
		tGestorOtra = otra.token(jwtService, usuarios, otra.gestor);

		producto(e.org, "dobok blanco", "OTRO", true);
		producto(e.org, "dobok negro", "OTRO", false);
		producto(e.org, "licencia anual", "LICENCIA", true);
		producto(e.org, "licencia federada", "LICENCIA", false);
		producto(e.org, "examen cinta", "EXAMEN", false);
		producto(e.org, "cuota mensual", "MENSUALIDAD", true);
		producto(e.org, "100% algodon", "OTRO", false);
		// Otra escuela: mismos tipos y marcas; nunca deben salir a la primera.
		producto(otra.org, "dobok ajeno", "OTRO", true);
		producto(otra.org, "licencia ajena", "LICENCIA", true);
	}

	@AfterAll
	void retirar() {
		jdbc.update("DELETE FROM producto WHERE concepto LIKE ?", "%" + marca + "%");
	}

	@AfterEach
	void sinFugas() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	@Test
	void tipoSolo() throws Exception {
		assertEquals(Set.of("dobok blanco", "dobok negro", "100% algodon"), conceptos(listar("tipo=OTRO&concepto=" + marca)));
		JsonNode licencias = listar("tipo=LICENCIA&concepto=" + marca);
		assertEquals(2, licencias.get("totalElements").asInt());
		assertEquals(Set.of("licencia anual", "licencia federada"), conceptos(licencias));
	}

	@Test
	void tipoSolo_sinConcepto_soloDeLaEscuelaActiva() throws Exception {
		int esperado = jdbc.queryForObject(
				"SELECT COUNT(*) FROM producto WHERE organizacion_id = ? AND tipo = 'LICENCIA'", Integer.class, e.org);
		JsonNode r = listar("tipo=LICENCIA&size=100");
		assertEquals(esperado, r.get("totalElements").asInt());
		assertTrue(esperado >= 2);
		assertTrue(conceptos(r).stream().noneMatch(c -> c.contains("ajena")));
	}

	@Test
	void vendibleProfesorSolo() throws Exception {
		assertEquals(Set.of("dobok blanco", "licencia anual", "cuota mensual"),
				conceptos(listar("vendibleProfesor=true&concepto=" + marca)));
		assertEquals(Set.of("dobok negro", "licencia federada", "examen cinta", "100% algodon"),
				conceptos(listar("vendibleProfesor=false&concepto=" + marca)));
	}

	@Test
	void tipoYVendibleProfesor() throws Exception {
		assertEquals(Set.of("dobok blanco"), conceptos(listar("tipo=OTRO&vendibleProfesor=true&concepto=" + marca)));
		assertEquals(Set.of("dobok negro", "100% algodon"),
				conceptos(listar("tipo=OTRO&vendibleProfesor=false&concepto=" + marca)));
		assertEquals(0, listar("tipo=EXAMEN&vendibleProfesor=true&concepto=" + marca).get("totalElements").asInt());
	}

	@Test
	void conceptoCombinadoConLosNuevos() throws Exception {
		assertEquals(Set.of("dobok blanco", "dobok negro"), conceptos(listar("concepto=dobok&tipo=OTRO")));
		assertEquals(Set.of("dobok blanco"), conceptos(listar("concepto=dobok&tipo=OTRO&vendibleProfesor=true")));
		assertEquals(Set.of("licencia anual"), conceptos(listar("concepto=licencia&tipo=LICENCIA&vendibleProfesor=true")));
		assertEquals(0, listar("concepto=dobok&tipo=LICENCIA").get("totalElements").asInt());
	}

	@Test
	void conceptoConComodinSeTrataComoTexto() throws Exception {
		// El % del concepto es literal, igual que en la búsqueda de siempre.
		assertEquals(Set.of("100% algodon"), conceptos(listarConcepto("100% algodon", "tipo", "OTRO")));
		assertEquals(0, listarConcepto("%zzz", "tipo", "OTRO").get("totalElements").asInt());
	}

	@Test
	void paginacionEnBaseDeDatos_totalElementsReal() throws Exception {
		JsonNode p1 = listar("tipo=OTRO&concepto=" + marca + "&size=2&page=1&orderBy=concepto&order=asc");
		JsonNode p2 = listar("tipo=OTRO&concepto=" + marca + "&size=2&page=2&orderBy=concepto&order=asc");
		assertEquals(3, p1.get("totalElements").asInt());
		assertEquals(2, p1.get("totalPages").asInt());
		assertEquals(2, p1.get("content").size());
		assertEquals(1, p2.get("content").size());
		assertTrue(p1.get("content").get(0).get("concepto").asText().startsWith("100%"));
	}

	@Test
	void tipoInvalido_400ConMensaje() throws Exception {
		MvcResult r = pedir("tipo=ZAPATO", tGestor);
		assertEquals(400, r.getResponse().getStatus());
		JsonNode cuerpo = json.readTree(r.getResponse().getContentAsString());
		assertTrue(cuerpo.hasNonNull("mensaje"), r.getResponse().getContentAsString());
		assertTrue(cuerpo.get("mensaje").asText().contains("ZAPATO"));
	}

	@Test
	void sinParametrosNuevos_igualQueAntes() throws Exception {
		int total = jdbc.queryForObject("SELECT COUNT(*) FROM producto WHERE organizacion_id = ?", Integer.class, e.org);
		JsonNode sin = listar("size=100");
		assertEquals(total, sin.get("totalElements").asInt());
		assertTrue(conceptos(sin).stream().noneMatch(c -> c.contains("ajen")));
		assertEquals(Set.of("licencia anual", "licencia federada"), conceptos(listar("concepto=licencia")));
		// Un tipo en blanco equivale a no enviarlo.
		assertEquals(total, listar("size=100&tipo=").get("totalElements").asInt());
	}

	@Test
	void otraEscuelaSoloVeLosSuyos() throws Exception {
		assertEquals(Set.of("dobok ajeno"), conceptos(listar("tipo=OTRO&concepto=" + marca, tGestorOtra)));
		assertEquals(Set.of("dobok ajeno", "licencia ajena"),
				conceptos(listar("vendibleProfesor=true&concepto=" + marca, tGestorOtra)));
	}

	@Test
	void permisosSinCambios() throws Exception {
		assertEquals(200, pedir("tipo=OTRO", tResp).getResponse().getStatus());
		assertEquals(403, pedir("tipo=OTRO", tFamilia).getResponse().getStatus());
		assertEquals(401, pedir("tipo=OTRO", null).getResponse().getStatus());
	}

	// ------------------------------------------------------------------ apoyo

	private void producto(long org, String concepto, String tipo, boolean marcado) {
		jdbc.update(
				"INSERT INTO producto (concepto, precio, organizacion_id, tipo, vendible_profesor) VALUES (?, ?, ?, ?, ?)",
				concepto + " " + marca, 5.0, org, tipo, marcado);
	}

	/** Conceptos de la página sin la marca de la prueba. */
	private Set<String> conceptos(JsonNode pagina) {
		Set<String> r = new TreeSet<>();
		for (JsonNode p : pagina.get("content")) {
			String c = p.get("concepto").asText();
			r.add(c.endsWith(" " + marca) ? c.substring(0, c.length() - marca.length() - 1) : c);
		}
		return r;
	}

	private JsonNode listar(String query) throws Exception {
		return listar(query, tGestor);
	}

	private JsonNode listar(String query, String token) throws Exception {
		MvcResult r = pedir(query, token);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString());
	}

	private JsonNode listarConcepto(String concepto, String parametro, String valor) throws Exception {
		MvcResult r = mvc.perform(get("/api/productos").param("concepto", concepto).param(parametro, valor)
				.cookie(new Cookie("jwt", tGestor))).andReturn();
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString());
	}

	private MvcResult pedir(String query, String token) throws Exception {
		var peticion = get("/api/productos?" + query);
		return mvc.perform(token == null ? peticion : peticion.cookie(new Cookie("jwt", token))).andReturn();
	}
}
