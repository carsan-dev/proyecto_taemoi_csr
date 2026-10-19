package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
 * Pieza 6b-3c (decisión 185) contra MySQL: la gestión marca y desmarca «Lo puede vender el profesor»
 * ({@code producto.vendible_profesor}) desde el listado, uno a uno ({@code PATCH /api/productos/{id}/vendible-profesor})
 * y en bloque ({@code PATCH /api/productos/vendible-profesor}), sin tocar ningún otro campo.
 * <ul>
 * <li>Solo gestor y propietario; responsable, familia y la sesión solo-profesor, 403.</li>
 * <li>Un producto de otra escuela es 404 (como uno inexistente), también en bloque; el bloque es todo o nada.</li>
 * <li>Validación con {@code {"mensaje"}}: marca nula o ausente, ids vacíos, nulos o más de 200 distintos.</li>
 * <li>Moiskimdo (organización 1), igual que el resto.</li>
 * </ul>
 * Escuelas sintéticas ({@link EscuelaConSedesDePrueba}); en la organización 1, productos propios que se borran al
 * terminar. Datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductoVendibleProfesorIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	String tGestor, tPropietario, tResp, tFamilia, tProfesor, tGestorOtra, tGestorOrg1;
	final String marcaOrg1 = UUID.randomUUID().toString().substring(0, 8);
	final List<Long> productosOrg1 = new ArrayList<>();
	final List<Long> cuentasOrg1 = new ArrayList<>();

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-6b3c");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-6b3c-otra");
		long propietario = e.cuenta(usuarios, "propietario");
		membresia(propietario, e.org, "PROPIETARIO");
		long familia = e.cuentaFamilia(usuarios, "familia");
		long profesor = e.cuenta(usuarios, "profesor");
		membresia(profesor, e.org, "PROFESOR");
		tGestor = e.token(jwtService, usuarios, e.gestor);
		tPropietario = e.token(jwtService, usuarios, propietario);
		tResp = e.token(jwtService, usuarios, e.respA);
		tFamilia = e.token(jwtService, usuarios, familia);
		tProfesor = e.token(jwtService, usuarios, profesor);
		tGestorOtra = otra.token(jwtService, usuarios, otra.gestor);
		long gestorOrg1 = e.cuenta(usuarios, "gestor-org1");
		cuentasOrg1.add(gestorOrg1);
		membresia(gestorOrg1, ORG1, "ADMINISTRADOR");
		tGestorOrg1 = tokenEn(gestorOrg1, ORG1);
	}

	@AfterAll
	void retirar() {
		for (Long producto : productosOrg1) {
			jdbc.update("DELETE FROM producto WHERE id = ? AND organizacion_id = 1", producto);
		}
		for (Long cuenta : cuentasOrg1) {
			jdbc.update("UPDATE membresia SET activa = FALSE WHERE usuario_id = ? AND organizacion_id = 1", cuenta);
		}
	}

	@AfterEach
	void sinFugas() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ uno a uno

	@Test
	void gestor_marcaYDesmarcaUno_soloCambiaLaMarca_yDevuelveElMismoJsonQueElGet() throws Exception {
		long producto = producto(e.org, "PULSERA", 17.25, false);
		Map<String, Object> antes = fila(producto);

		JsonNode marcado = leer(uno(producto, true), tGestor, 200);
		assertTrue(marcado.get("vendibleProfesor").asBoolean());
		assertTrue(marcado(producto));
		assertEquals(leer(get("/api/productos/" + producto), tGestor, 200), marcado, "mismo JSON que el GET");
		assertSoloCambioLaMarca(antes, fila(producto), true);

		JsonNode desmarcado = leer(uno(producto, false), tGestor, 200);
		assertFalse(desmarcado.get("vendibleProfesor").asBoolean());
		assertFalse(marcado(producto));
		assertEquals(17.25, desmarcado.get("precio").asDouble(), 0.0001);
		assertSoloCambioLaMarca(antes, fila(producto), false);
	}

	@Test
	void propietario_marcaYDesmarcaUno() throws Exception {
		long producto = producto(e.org, "GUANTES", 30.0, false);
		Map<String, Object> antes = fila(producto);
		assertTrue(leer(uno(producto, true), tPropietario, 200).get("vendibleProfesor").asBoolean());
		assertTrue(marcado(producto));
		assertFalse(leer(uno(producto, false), tPropietario, 200).get("vendibleProfesor").asBoolean());
		assertFalse(marcado(producto));
		assertSoloCambioLaMarca(antes, fila(producto), false);
	}

	@Test
	void uno_idempotente() throws Exception {
		long producto = producto(e.org, "CINTA", 5.0, true);
		assertTrue(leer(uno(producto, true), tGestor, 200).get("vendibleProfesor").asBoolean());
		assertTrue(leer(uno(producto, true), tGestor, 200).get("vendibleProfesor").asBoolean());
		assertTrue(marcado(producto));
		long sin = producto(e.org, "CINTA SIN", 5.0, false);
		assertFalse(leer(uno(sin, false), tGestor, 200).get("vendibleProfesor").asBoolean());
		assertFalse(marcado(sin));
	}

	@Test
	void uno_deOtraEscuelaOInexistente_404SinCambios() throws Exception {
		long ajeno = producto(otra.org, "AJENO", 9.0, false);
		Map<String, Object> antes = fila(ajeno);
		MvcResult r = peticion(uno(ajeno, true), tGestor);
		assertEquals(404, r.getResponse().getStatus());
		JsonNode cuerpoAjeno = json.readTree(r.getResponse().getContentAsString());
		assertTrue(cuerpoAjeno.hasNonNull("mensaje"), r.getResponse().getContentAsString());
		assertEquals(antes, fila(ajeno), "sin cambios en la otra escuela");

		// Inexistente: mismo estado y mismo mensaje (no se distingue el ajeno del que no existe).
		long inexistente = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) + 100000 FROM producto", Long.class);
		MvcResult r2 = peticion(uno(inexistente, true), tGestor);
		assertEquals(404, r2.getResponse().getStatus());
		String mensajeInexistente = json.readTree(r2.getResponse().getContentAsString()).get("mensaje").asText();
		assertEquals(cuerpoAjeno.get("mensaje").asText().replace(String.valueOf(ajeno), "#"),
				mensajeInexistente.replace(String.valueOf(inexistente), "#"));

		// Y la otra escuela sí puede con el suyo.
		assertTrue(leer(uno(ajeno, true), tGestorOtra, 200).get("vendibleProfesor").asBoolean());
	}

	@Test
	void uno_validacion400() throws Exception {
		long producto = producto(e.org, "VALIDA", 5.0, false);
		for (String cuerpo : List.of("{}", "{\"vendibleProfesor\":null}")) {
			MvcResult r = peticion(patch("/api/productos/" + producto + "/vendible-profesor")
					.contentType(MediaType.APPLICATION_JSON).content(cuerpo), tGestor);
			assertEquals(400, r.getResponse().getStatus(), cuerpo);
			assertTrue(json.readTree(r.getResponse().getContentAsString()).hasNonNull("mensaje"),
					r.getResponse().getContentAsString());
		}
		assertFalse(marcado(producto));
	}

	// ------------------------------------------------------------------ en bloque

	@Test
	void bloque_marcaYDesmarca_actualizadosN_soloLaMarca() throws Exception {
		long a = producto(e.org, "BLOQUE A", 10.0, false);
		long b = producto(e.org, "BLOQUE B", 11.0, true);
		long c = producto(e.org, "BLOQUE C", 12.0, false);
		long fuera = producto(e.org, "BLOQUE FUERA", 13.0, false);
		Map<Long, Map<String, Object>> antes = Map.of(a, fila(a), b, fila(b), c, fila(c));

		JsonNode r = leer(bloque(List.of(a, b, c), true), tGestor, 200);
		assertEquals(Map.of("actualizados", 3), json.convertValue(r, Map.class));
		for (long id : List.of(a, b, c)) {
			assertTrue(marcado(id));
			assertSoloCambioLaMarca(antes.get(id), fila(id), true);
		}
		assertFalse(marcado(fuera), "el que no va en el bloque, intacto");

		assertEquals(2, leer(bloque(List.of(a, c), false), tPropietario, 200).get("actualizados").asInt());
		assertFalse(marcado(a));
		assertTrue(marcado(b));
		assertFalse(marcado(c));
	}

	@Test
	void bloque_duplicadosSeDeduplican() throws Exception {
		long a = producto(e.org, "DUP A", 10.0, false);
		long b = producto(e.org, "DUP B", 10.0, false);
		JsonNode r = leer(bloque(List.of(a, b, a, a, b), true), tGestor, 200);
		assertEquals(2, r.get("actualizados").asInt());
		assertTrue(marcado(a));
		assertTrue(marcado(b));
	}

	@Test
	void bloque_conUnoAjenoOInexistente_404YNingunoCambia() throws Exception {
		long a = producto(e.org, "TODO A", 10.0, false);
		long b = producto(e.org, "TODO B", 10.0, false);
		long ajeno = producto(otra.org, "TODO AJENO", 10.0, false);
		long inexistente = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) + 100000 FROM producto", Long.class);

		for (List<Long> ids : List.of(List.of(a, ajeno, b), List.of(a, b, inexistente), List.of(ajeno))) {
			MvcResult r = peticion(bloque(ids, true), tGestor);
			assertEquals(404, r.getResponse().getStatus(), ids.toString());
			assertTrue(json.readTree(r.getResponse().getContentAsString()).hasNonNull("mensaje"),
					r.getResponse().getContentAsString());
			assertFalse(marcado(a), "todo o nada: " + ids);
			assertFalse(marcado(b), "todo o nada: " + ids);
			assertFalse(marcado(ajeno), "la otra escuela, intacta: " + ids);
		}
	}

	@Test
	void bloque_validacion400() throws Exception {
		long a = producto(e.org, "VAL BLOQUE", 10.0, false);
		List<Long> doscientosUno = new ArrayList<>();
		for (long i = 0; i < 201; i++) {
			doscientosUno.add(a + 1_000_000 + i);
		}
		List<Long> doscientosConDuplicados = new ArrayList<>();
		doscientosConDuplicados.add(a);
		doscientosConDuplicados.add(a);
		List<String> cuerpos = List.of(
				"{\"ids\":[" + a + "]}",
				"{\"ids\":[" + a + "],\"vendibleProfesor\":null}",
				"{\"vendibleProfesor\":true}",
				"{\"ids\":null,\"vendibleProfesor\":true}",
				"{\"ids\":[],\"vendibleProfesor\":true}",
				"{\"ids\":[" + a + ",null],\"vendibleProfesor\":true}",
				json.writeValueAsString(Map.of("ids", doscientosUno, "vendibleProfesor", true)));
		for (String cuerpo : cuerpos) {
			MvcResult r = peticion(patch("/api/productos/vendible-profesor").contentType(MediaType.APPLICATION_JSON)
					.content(cuerpo), tGestor);
			assertEquals(400, r.getResponse().getStatus(), cuerpo);
			assertTrue(json.readTree(r.getResponse().getContentAsString()).hasNonNull("mensaje"),
					r.getResponse().getContentAsString());
		}
		assertFalse(marcado(a));
		// 200 distintos (con duplicados de más) no supera el límite: aquí da 404 por los inexistentes, no 400.
		List<Long> doscientos = new ArrayList<>(doscientosUno.subList(0, 199));
		doscientos.add(a);
		doscientos.add(a);
		assertEquals(404, estado(bloque(doscientos, true), tGestor));
		assertFalse(marcado(a));
	}

	// ------------------------------------------------------------------ permisos

	@Test
	void soloGestion_responsableFamiliaYSoloProfesor403() throws Exception {
		long producto = producto(e.org, "PERMISOS", 10.0, false);
		for (String token : List.of(tResp, tFamilia, tProfesor)) {
			assertEquals(403, estado(uno(producto, true), token));
			assertEquals(403, estado(bloque(List.of(producto), true), token));
		}
		assertTrue(estado(uno(producto, true), null) >= 400, "sin sesión");
		assertTrue(estado(bloque(List.of(producto), true), null) >= 400, "sin sesión");
		assertFalse(marcado(producto));
	}

	@Test
	void seAuditaComoElPut() throws Exception {
		long producto = producto(e.org, "AUDITADO", 10.0, false);
		String rutaUno = "/api/productos/" + producto + "/vendible-profesor";
		leer(uno(producto, true), tGestor, 200);
		assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_evento WHERE endpoint = ? "
				+ "AND metodo_http = 'PATCH' AND accion = 'UPDATE' AND estado_http = 200 AND organizacion_id = ?",
				Long.class, rutaUno, e.org));
		long antes = jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_evento WHERE organizacion_id = ? "
				+ "AND endpoint = '/api/productos/vendible-profesor' AND metodo_http = 'PATCH' AND accion = 'UPDATE'",
				Long.class, e.org);
		leer(bloque(List.of(producto), false), tGestor, 200);
		assertEquals(antes + 1, jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_evento WHERE organizacion_id = ? "
				+ "AND endpoint = '/api/productos/vendible-profesor' AND metodo_http = 'PATCH' AND accion = 'UPDATE'",
				Long.class, e.org));
	}

	// ------------------------------------------------------------------ Moiskimdo

	@Test
	void moiskimdo_igualQueElResto() throws Exception {
		long a = producto(ORG1, "MOISKIMDO A", 20.0, false);
		long b = producto(ORG1, "MOISKIMDO B", 21.0, false);
		Map<String, Object> antesA = fila(a);
		JsonNode uno = leer(uno(a, true), tGestorOrg1, 200);
		assertTrue(uno.get("vendibleProfesor").asBoolean());
		assertEquals(leer(get("/api/productos/" + a), tGestorOrg1, 200), uno);
		assertSoloCambioLaMarca(antesA, fila(a), true);
		assertEquals(2, leer(bloque(List.of(a, b, b), false), tGestorOrg1, 200).get("actualizados").asInt());
		assertFalse(marcado(a));
		assertFalse(marcado(b));

		// Aislamiento en los dos sentidos.
		long deE = producto(e.org, "NO DE MOISKIMDO", 10.0, false);
		assertEquals(404, estado(uno(deE, true), tGestorOrg1));
		assertEquals(404, estado(bloque(List.of(a, deE), true), tGestorOrg1));
		assertFalse(marcado(a));
		assertFalse(marcado(deE));
		assertEquals(404, estado(uno(a, true), tGestor));
		assertEquals(404, estado(bloque(List.of(a), true), tGestor));
		assertFalse(marcado(a));
	}

	// ------------------------------------------------------------------ utilidades

	private void membresia(long usuario, long org, String rol) {
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", usuario, org, rol);
	}

	private String tokenEn(long usuarioId, long org) {
		var usuario = TenantContext.calcularCon(ORG1, () -> usuarios.findById(usuarioId).orElseThrow());
		return jwtService.generateToken(usuario, 600_000, org);
	}

	/** Producto OTRO de la organización dada, con nombre único. */
	private long producto(long org, String concepto, double precio, boolean marcado) {
		String nombre = "6B3C " + concepto + " " + (org == ORG1 ? marcaOrg1 : e.marca);
		jdbc.update("INSERT INTO producto (concepto, precio, organizacion_id, vendible_profesor) VALUES (?, ?, ?, ?)",
				nombre, precio, org, marcado);
		long id = jdbc.queryForObject("SELECT id FROM producto WHERE concepto = ? AND organizacion_id = ?", Long.class,
				nombre, org);
		if (org == ORG1) {
			productosOrg1.add(id);
		}
		return id;
	}

	private Map<String, Object> fila(long producto) {
		return new HashMap<>(jdbc.queryForMap("SELECT * FROM producto WHERE id = ?", producto));
	}

	private boolean marcado(long producto) {
		return Boolean.TRUE.equals(jdbc.queryForObject("SELECT vendible_profesor FROM producto WHERE id = ?",
				Boolean.class, producto));
	}

	/** Toda la fila igual salvo {@code vendible_profesor}, que vale {@code marca}. */
	private static void assertSoloCambioLaMarca(Map<String, Object> antes, Map<String, Object> despues, boolean marca) {
		Map<String, Object> esperado = new HashMap<>(antes);
		esperado.put("vendible_profesor", marca);
		Map<String, Object> real = new HashMap<>(despues);
		real.put("vendible_profesor", Boolean.TRUE.equals(despues.get("vendible_profesor")));
		assertEquals(esperado, real);
	}

	private MockHttpServletRequestBuilder uno(long producto, boolean marca) throws Exception {
		return patch("/api/productos/" + producto + "/vendible-profesor").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("vendibleProfesor", marca)));
	}

	private MockHttpServletRequestBuilder bloque(List<Long> ids, boolean marca) throws Exception {
		return patch("/api/productos/vendible-profesor").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("ids", ids, "vendibleProfesor", marca)));
	}

	private JsonNode leer(MockHttpServletRequestBuilder peticion, String token, int esperado) throws Exception {
		MvcResult r = peticion(peticion, token);
		assertEquals(esperado, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString());
	}

	private int estado(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return peticion(peticion, token).getResponse().getStatus();
	}

	private MvcResult peticion(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(token == null ? peticion : peticion.cookie(new Cookie("jwt", token))).andReturn();
	}
}
