package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
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
import com.taemoi.project.dtos.response.GrupoConAlumnosDTO;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza 6b-3b-1 contra MySQL: lo que necesitan las pantallas «Mi caja» del profesor (decisiones 180 a 182).
 * <ul>
 * <li>{@code GET /api/caja/profesor/alumnos}: alumnos ACTIVOS de sus grupos, sin duplicados, por apellidos y nombre,
 * con los nombres de SUS grupos; sin datos sensibles; solo el profesor; aislado por escuela; sin N+1.</li>
 * <li>Marca «Lo puede vender el profesor» ({@code producto.vendible_profesor}): sin marcar no se lista ni se vende
 * (400); la gestión la pone por {@code PUT /api/productos/{id}}; un {@code PUT} o un {@code POST} sin el campo la
 * conserva; el producto anidado en un cargo no la lleva.</li>
 * <li>{@code venta} en {@code CobroCaja}: verdadero en la venta del catálogo del profesor, falso al cobrar un cargo de la
 * gestión; deshacer con NO_HUBO_VENTA sigue funcionando si la gestión desmarca el producto después.</li>
 * </ul>
 * Escuela sintética ({@link EscuelaConSedesDePrueba}) y otra para el aislamiento. Datos inventados.
 */
@SpringBootTest(properties = {
		"spring.jpa.properties.hibernate.session_factory.statement_inspector=com.taemoi.project.integracion.ContadorSentencias" })
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MiCajaProfesorIntegrationTest extends MySqlIntegrationTest {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	long propietario, familia, prof1, prof2, profOtra;
	String tGestor, tPropietario, tResp, tFamilia, tProf1, tProf2, tProfOtra;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-6b3b1");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-6b3b1-otra");
		propietario = e.cuenta(usuarios, "propietario");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROPIETARIO')",
				propietario, e.org);
		familia = e.cuentaFamilia(usuarios, "familia");
		prof1 = profesor(e, "prof1", e.gA);
		prof2 = profesor(e, "prof2", e.gB);
		profOtra = profesor(otra, "profotra", otra.gA);
		tGestor = e.token(jwtService, usuarios, e.gestor);
		tPropietario = e.token(jwtService, usuarios, propietario);
		tResp = e.token(jwtService, usuarios, e.respA);
		tFamilia = e.token(jwtService, usuarios, familia);
		tProf1 = e.token(jwtService, usuarios, prof1);
		tProf2 = e.token(jwtService, usuarios, prof2);
		tProfOtra = otra.token(jwtService, usuarios, profOtra);
	}

	@AfterEach
	void sinFugas() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ alumnos de mis grupos

	@Test
	void alumnosDeMisGrupos_activosSinDuplicadosPorApellidosYNombre_conSusGrupos() throws Exception {
		long g1 = grupo("IT 6B3B1 Grupo Uno " + e.marca, e.sA);
		long g2 = grupo("IT 6B3B1 Grupo Dos " + e.marca, e.sB);
		long ajeno = grupo("IT 6B3B1 Grupo Ajeno " + e.marca, e.sA);
		long prof = profesor(e, "profordena", g1);
		asignar(e, prof, g2);
		long zapataAna = alumno("ANA", "ZAPATA", true, g1);
		long alonsoBea = alumno("BEA", "ALONSO", true, g1, g2, ajeno);
		long alonsoAlba = alumno("ALBA", "ALONSO", true, g2);
		long deBaja = alumno("BAJA", "AAAA", false, g1);
		long soloAjeno = alumno("FUERA", "AAAA", true, ajeno);

		JsonNode lista = leer(get("/api/caja/profesor/alumnos"), e.token(jwtService, usuarios, prof), 200);
		assertEquals(List.of(alonsoAlba, alonsoBea, zapataAna), idsEnOrden(lista, "alumnoId"),
				"activos de sus grupos, una vez cada uno, por apellidos y nombre");
		assertFalse(idsEnOrden(lista, "alumnoId").contains(deBaja));
		assertFalse(idsEnOrden(lista, "alumnoId").contains(soloAjeno));
		JsonNode bea = fila(lista, "alumnoId", alonsoBea);
		// 6c-8: «sedes» (las de sus grupos, D6c-9) solo sale si alguno de esos grupos tiene sede.
		Set<String> permitidos = Set.of("alumnoId", "nombre", "apellidos", "grupos", "sedes");
		assertTrue(permitidos.containsAll(campos(bea)) && campos(bea).containsAll(
				Set.of("alumnoId", "nombre", "apellidos", "grupos")), "sin datos sensibles: " + campos(bea));
		assertEquals("BEA", bea.get("nombre").asText());
		assertEquals("ALONSO", bea.get("apellidos").asText());
		assertEquals(List.of("IT 6B3B1 Grupo Dos " + e.marca, "IT 6B3B1 Grupo Uno " + e.marca), textos(bea.get("grupos")),
				"solo SUS grupos (no el ajeno), por nombre");
		assertEquals(List.of("IT 6B3B1 Grupo Uno " + e.marca), textos(fila(lista, "alumnoId", zapataAna).get("grupos")));
	}

	@Test
	void alumnosDeMisGrupos_cadaProfesorLosSuyos_yOtraEscuelaAislada() throws Exception {
		Set<Long> deProf1 = ids(leer(get("/api/caja/profesor/alumnos"), tProf1, 200), "alumnoId");
		assertTrue(deProf1.containsAll(List.of(e.aGrupoA, e.aSinSede)), "los de gA");
		assertFalse(deProf1.contains(e.aB), "gB es de prof2");
		assertFalse(deProf1.contains(e.aA), "sin grupo");
		assertFalse(deProf1.contains(e.aNada));
		assertFalse(deProf1.contains(otra.aGrupoA), "otra escuela");

		JsonNode deProf2 = leer(get("/api/caja/profesor/alumnos"), tProf2, 200);
		assertTrue(ids(deProf2, "alumnoId").containsAll(List.of(e.aB, e.aGrupoA)));
		assertFalse(ids(deProf2, "alumnoId").contains(e.aSinSede));
		// aGrupoA está en gA y gB: a prof2 solo le sale su grupo.
		assertEquals(List.of(nombreGrupo(e.gB)), textos(fila(deProf2, "alumnoId", e.aGrupoA).get("grupos")));

		Set<Long> deOtra = ids(leer(get("/api/caja/profesor/alumnos"), tProfOtra, 200), "alumnoId");
		assertTrue(deOtra.containsAll(List.of(otra.aGrupoA, otra.aSinSede)));
		assertFalse(deOtra.contains(e.aGrupoA), "nada de la otra escuela");
		assertFalse(deOtra.contains(e.aSinSede));
	}

	@Test
	void alumnosDeMisGrupos_soloElProfesor() throws Exception {
		for (String token : List.of(tGestor, tPropietario, tResp, tFamilia)) {
			assertEquals(403, estado(get("/api/caja/profesor/alumnos"), token));
		}
		assertTrue(mvc.perform(get("/api/caja/profesor/alumnos")).andReturn().getResponse().getStatus() >= 400,
				"sin sesión");
	}

	@Test
	void alumnosDeMisGrupos_sinNMasUno() throws Exception {
		long g = grupo("IT 6B3B1 Grupo NMas1 " + e.marca, e.sA);
		long prof = profesor(e, "profnmas1", g);
		String token = e.token(jwtService, usuarios, prof);
		alumno("UNO", "NMASUNO", true, g);
		leer(get("/api/caja/profesor/alumnos"), token, 200);
		int conUno = ContadorSentencias.contar(() -> estado(get("/api/caja/profesor/alumnos"), token)).total();
		long g2 = grupo("IT 6B3B1 Grupo NMas1 B " + e.marca, e.sB);
		asignar(e, prof, g2);
		for (int i = 0; i < 4; i++) {
			alumno("X" + i, "NMASUNO", true, g, g2);
		}
		ContadorSentencias.Conteo<JsonNode> conCinco = ContadorSentencias
				.contar(() -> leer(get("/api/caja/profesor/alumnos"), token, 200));
		assertEquals(5, conCinco.resultado().size());
		assertEquals(conUno, conCinco.total(), "mismas sentencias con 1 y con 5 alumnos: " + conCinco.sentencias());
	}

	// ------------------------------------------------------------------ marca «Lo puede vender el profesor»

	@Test
	void sinMarcar_noSeListaNiSeVende_marcadoSi() throws Exception {
		long alumno = alumno("MARCA", "VENTA", true, e.gA);
		long producto = producto("PULSERA 6B3B1", null);
		assertFalse(ids(leer(get("/api/caja/profesor/productos"), tProf1, 200), "id").contains(producto));
		MvcResult r = peticion(venta(alumno, producto), tProf1);
		assertEquals(400, r.getResponse().getStatus());
		assertTrue(r.getResponse().getContentAsString().contains("no lo ha marcado"),
				r.getResponse().getContentAsString());
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM producto_alumno WHERE alumno_id = ?", Long.class,
				alumno), "sin cargo");

		// La gestión la marca en la ficha (PUT /api/productos/{id}).
		JsonNode marcado = leer(ponerProducto(producto, "PULSERA 6B3B1", 15.0, Boolean.TRUE), tGestor, 200);
		assertTrue(marcado.get("vendibleProfesor").asBoolean());
		assertTrue(ids(leer(get("/api/caja/profesor/productos"), tProf1, 200), "id").contains(producto));
		assertEquals(201, estado(venta(alumno, producto), tProf1));

		// Desmarcada: vuelve a salir de la lista y a dar 400.
		leer(ponerProducto(producto, "PULSERA 6B3B1", 15.0, Boolean.FALSE), tGestor, 200);
		assertFalse(ids(leer(get("/api/caja/profesor/productos"), tProf1, 200), "id").contains(producto));
		assertEquals(400, estado(venta(alumno, producto), tProf1));
	}

	@Test
	void marcarNoSaltaLaReglaFija() throws Exception {
		long alumno = alumno("REGLA", "VENTA", true, e.gA);
		long mensualidad = producto("MENSUALIDAD 6B3B1", Boolean.TRUE);
		assertFalse(ids(leer(get("/api/caja/profesor/productos"), tProf1, 200), "id").contains(mensualidad));
		MvcResult r = peticion(venta(alumno, mensualidad), tProf1);
		assertEquals(400, r.getResponse().getStatus());
		assertTrue(r.getResponse().getContentAsString().contains("no lo puede cobrar un profesor"));
	}

	@Test
	void apiDeProductos_exponeLaMarca_yUnPutOPostSinElCampoLaConserva() throws Exception {
		long producto = producto("CHAQUETA 6B3B1", Boolean.TRUE);
		String nombre = "CHAQUETA 6B3B1 " + e.marca;
		JsonNode leido = leer(get("/api/productos/" + producto), tGestor, 200);
		assertEquals(Set.of("id", "concepto", "precio", "vendibleProfesor"), campos(leido));
		assertTrue(leido.get("vendibleProfesor").asBoolean());

		// Cliente anterior: PUT sin el campo (y con null) → conserva la marca; cambia lo demás.
		JsonNode sinCampo = leer(put("/api/productos/" + producto).contentType(MediaType.APPLICATION_JSON)
				.content("{\"concepto\":\"" + nombre + "\",\"precio\":31.5}"), tGestor, 200);
		assertTrue(sinCampo.get("vendibleProfesor").asBoolean());
		assertEquals(31.5, sinCampo.get("precio").asDouble(), 0.0001);
		assertTrue(marcado(producto));
		leer(put("/api/productos/" + producto).contentType(MediaType.APPLICATION_JSON)
				.content("{\"concepto\":\"" + nombre + "\",\"precio\":31.5,\"vendibleProfesor\":null}"), tGestor,
				200);
		assertTrue(marcado(producto));
		// POST con el id de uno existente (merge, como siempre) sin el campo: también la conserva.
		leer(post("/api/productos").contentType(MediaType.APPLICATION_JSON)
				.content("{\"id\":" + producto + ",\"concepto\":\"" + nombre + "\",\"precio\":32.0}"), tGestor,
				200);
		assertTrue(marcado(producto));
		assertEquals(32.0, jdbc.queryForObject("SELECT precio FROM producto WHERE id = ?", Double.class, producto),
				0.0001);

		// Alta sin el campo → sin marcar; con el campo → marcado.
		JsonNode nuevo = leer(post("/api/productos").contentType(MediaType.APPLICATION_JSON)
				.content("{\"concepto\":\"GORRO 6B3B1\",\"precio\":5.0}"), tGestor, 200);
		assertFalse(nuevo.get("vendibleProfesor").asBoolean());
		assertFalse(marcado(nuevo.get("id").asLong()));
		JsonNode nuevoMarcado = leer(post("/api/productos").contentType(MediaType.APPLICATION_JSON)
				.content("{\"concepto\":\"BOLSA 6B3B1\",\"precio\":5.0,\"vendibleProfesor\":true}"), tPropietario, 200);
		assertTrue(marcado(nuevoMarcado.get("id").asLong()));
		// En el listado paginado, también.
		JsonNode pagina = leer(get("/api/productos").param("concepto", "BOLSA 6B3B1"), tGestor, 200);
		assertTrue(pagina.get("content").get(0).get("vendibleProfesor").asBoolean());

		// Solo la gestión la cambia.
		for (String token : List.of(tResp, tProf1, tFamilia)) {
			assertEquals(403, estado(ponerProducto(producto, "CHAQUETA 6B3B1", 32.0, Boolean.FALSE), token));
		}
		assertTrue(marcado(producto));
	}

	@Test
	void elProductoAnidadoEnUnCargo_noLlevaLaMarca() throws Exception {
		long alumno = alumno("ANIDADO", "VENTA", true, e.gA);
		long producto = producto("BANDERA 6B3B1", Boolean.TRUE);
		long cargo = leer(venta(alumno, producto), tProf1, 201).get("cargoId").asLong();
		JsonNode alumnoAdmin = leer(get("/api/admin/" + alumno), tPropietario, 200);
		JsonNode anidado = null;
		for (JsonNode c : alumnoAdmin.get("productosAlumno")) {
			if (c.get("id").asLong() == cargo) {
				anidado = c.get("producto");
			}
		}
		assertNotNull(anidado, alumnoAdmin.toString());
		assertEquals(Set.of("id", "concepto", "precio"), campos(anidado), "como antes de la 6b-3b-1");
	}

	// ------------------------------------------------------------------ venta en CobroCaja

	@Test
	void venta_verdaderaEnSuVentaDelCatalogo_falsaAlCobrarUnCargoDeLaGestion() throws Exception {
		long alumno = alumno("COBRO", "VENTA", true, e.gA);
		long producto = producto("CAMISETA 6B3B1", Boolean.TRUE);
		JsonNode vendida = leer(venta(alumno, producto), tProf1, 201);
		assertTrue(vendida.get("venta").asBoolean());
		long cargoGestion = cargoDeLaGestion(alumno, producto);
		JsonNode cobrado = leer(post("/api/caja/profesor/cargos/" + cargoGestion + "/cobrar"), tProf1, 201);
		assertFalse(cobrado.get("venta").asBoolean(), "cargo de la gestión cobrado por el profesor");

		JsonNode encima = leer(get("/api/caja/profesor/cobros-pendientes-entrega"), tProf1, 200).get("cobros");
		assertTrue(fila(encima, "cobroId", vendida.get("cobroId").asLong()).get("venta").asBoolean());
		assertFalse(fila(encima, "cobroId", cobrado.get("cobroId").asLong()).get("venta").asBoolean());
		// En la caja de la sede, la misma marca.
		JsonNode sede = leer(get("/api/caja/sedes/" + e.sA + "/pendiente-entrega"), tResp, 200);
		JsonNode suyos = fila(sede, "usuarioId", prof1).get("cobros");
		assertTrue(fila(suyos, "cobroId", vendida.get("cobroId").asLong()).get("venta").asBoolean());
		assertFalse(fila(suyos, "cobroId", cobrado.get("cobroId").asLong()).get("venta").asBoolean());

		// Deshecha como «aún no pagado» y cobrada otra vez: ese segundo cobro ya no es la venta.
		long cargoVenta = vendida.get("cargoId").asLong();
		assertEquals(204, estado(post("/api/caja/profesor/cobros/" + vendida.get("cobroId").asLong() + "/deshacer"),
				tProf1));
		JsonNode otraVez = leer(post("/api/caja/profesor/cargos/" + cargoVenta + "/cobrar"), tProf1, 201);
		assertFalse(otraVez.get("venta").asBoolean());
	}

	@Test
	void desmarcarDespues_noImpideDeshacerLaVentaComoNoHuboVenta() throws Exception {
		long alumno = alumno("DESMARCA", "VENTA", true, e.gA);
		long producto = producto("TOALLA 6B3B1", Boolean.TRUE);
		JsonNode vendida = leer(venta(alumno, producto), tProf1, 201);
		long cobro = vendida.get("cobroId").asLong();
		long cargo = vendida.get("cargoId").asLong();

		leer(ponerProducto(producto, "TOALLA 6B3B1", 15.0, Boolean.FALSE), tGestor, 200);
		assertFalse(ids(leer(get("/api/caja/profesor/productos"), tProf1, 200), "id").contains(producto));
		JsonNode encima = leer(get("/api/caja/profesor/cobros-pendientes-entrega"), tProf1, 200).get("cobros");
		assertTrue(fila(encima, "cobroId", cobro).get("venta").asBoolean(), "sigue siendo su venta");

		assertEquals(204, estado(post("/api/caja/profesor/cobros/" + cobro + "/deshacer")
				.contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"NO_HUBO_VENTA\"}"), tProf1));
		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM producto_alumno WHERE id = ?", cargo);
		assertFalse((Boolean) fila.get("pagado"));
		assertNotNull(fila.get("cancelado_en"));
		assertEquals("no hubo venta", fila.get("motivo_cancelacion"));
		assertEquals("ANULADO", jdbc.queryForObject("SELECT estado FROM cobro WHERE id = ?", String.class, cobro));
	}

	@Test
	void cobrosPendientesDeEntrega_conVenta_sinNMasUno() throws Exception {
		long g = grupo("IT 6B3B1 Grupo Venta NMas1 " + e.marca, e.sA);
		long prof = profesor(e, "profventas", g);
		String token = e.token(jwtService, usuarios, prof);
		long producto = producto("CORDON 6B3B1", Boolean.TRUE);
		long alumno = alumno("VENTAS", "NMASUNO", true, g);
		leer(venta(alumno, producto), token, 201);
		leer(post("/api/caja/profesor/cargos/" + cargoDeLaGestion(alumno, producto) + "/cobrar"), token, 201);
		leer(get("/api/caja/profesor/cobros-pendientes-entrega"), token, 200);
		int conDos = ContadorSentencias.contar(() -> estado(get("/api/caja/profesor/cobros-pendientes-entrega"), token))
				.total();
		for (int i = 0; i < 3; i++) {
			long otro = alumno("VENTAS" + i, "NMASUNO", true, g);
			leer(venta(otro, producto), token, 201);
			leer(post("/api/caja/profesor/cargos/" + cargoDeLaGestion(otro, producto) + "/cobrar"), token, 201);
		}
		ContadorSentencias.Conteo<JsonNode> conOcho = ContadorSentencias
				.contar(() -> leer(get("/api/caja/profesor/cobros-pendientes-entrega"), token, 200));
		JsonNode cobros = conOcho.resultado().get("cobros");
		assertEquals(8, cobros.size());
		int ventas = 0;
		for (JsonNode c : cobros) {
			ventas += c.get("venta").asBoolean() ? 1 : 0;
		}
		assertEquals(4, ventas);
		assertEquals(conDos, conOcho.total(), "mismas sentencias con 2 y con 8 cobros: " + conOcho.sentencias());
	}

	// ------------------------------------------------------------------ utilidades

	private long profesor(EscuelaConSedesDePrueba x, String nombre, long grupo) {
		long id = x.cuentaFamilia(usuarios, nombre);
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROFESOR')", id, x.org);
		asignar(x, id, grupo);
		return id;
	}

	private void asignar(EscuelaConSedesDePrueba x, long profesor, long grupo) {
		jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) "
				+ "VALUES (?, ?, ?, 'TITULAR')", x.org, profesor, grupo);
	}

	private long grupo(String nombre, long sede) {
		GrupoConAlumnosDTO dto = new GrupoConAlumnosDTO();
		dto.setNombre(nombre);
		dto.setDeporte("TAEKWONDO");
		dto.setSedeId(sede);
		return TenantContext.calcularCon(e.org, () -> grupoService.crearGrupo(dto).getId());
	}

	private String nombreGrupo(long grupo) {
		return jdbc.queryForObject("SELECT nombre FROM grupo WHERE id = ?", String.class, grupo);
	}

	/** Alumno de la escuela {@code e} (sede A) con esos apellidos, en los grupos dados. */
	private long alumno(String nombre, String apellidos, boolean activo, long... grupos) {
		long alumno = e.alumno(nombre, e.sA, activo);
		jdbc.update("UPDATE alumno SET nombre = ?, apellidos = ? WHERE id = ?", nombre, apellidos, alumno);
		for (long grupo : grupos) {
			jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", grupo, alumno);
		}
		return alumno;
	}

	/** Producto OTRO de la escuela {@code e}; {@code marcado} null = sin la columna (DEFAULT FALSE). */
	private long producto(String concepto, Boolean marcado) {
		String nombre = concepto + " " + e.marca;
		if (marcado == null) {
			jdbc.update("INSERT INTO producto (concepto, precio, organizacion_id) VALUES (?, 15.0, ?)", nombre, e.org);
		} else {
			jdbc.update("INSERT INTO producto (concepto, precio, organizacion_id, vendible_profesor) VALUES (?, 15.0, ?, ?)",
					nombre, e.org, marcado);
		}
		return jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = ?", Long.class, e.org);
	}

	private boolean marcado(long producto) {
		return Boolean.TRUE.equals(jdbc.queryForObject("SELECT vendible_profesor FROM producto WHERE id = ?",
				Boolean.class, producto));
	}

	private MockHttpServletRequestBuilder ponerProducto(long producto, String concepto, double precio, Boolean marca)
			throws Exception {
		Map<String, Object> cuerpo = new java.util.HashMap<>();
		cuerpo.put("concepto", concepto + " " + e.marca);
		cuerpo.put("precio", precio);
		cuerpo.put("vendibleProfesor", marca);
		return put("/api/productos/" + producto).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(cuerpo));
	}

	/** Cargo pendiente creado por la gestión, como siempre. */
	private long cargoDeLaGestion(long alumno, long producto) throws Exception {
		return leer(post("/api/productos-alumno/alumno/" + alumno + "/producto/" + producto)
				.contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":1,\"pagado\":false}"), tGestor, 200)
				.get("id").asLong();
	}

	private static MockHttpServletRequestBuilder venta(long alumno, long producto) {
		return post("/api/caja/profesor/alumnos/" + alumno + "/productos/" + producto + "/cobrar");
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
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
	}

	private static Set<String> campos(JsonNode nodo) {
		Set<String> campos = new HashSet<>();
		for (Iterator<String> it = nodo.fieldNames(); it.hasNext();) {
			campos.add(it.next());
		}
		return campos;
	}

	private static List<String> textos(JsonNode lista) {
		List<String> textos = new ArrayList<>();
		lista.forEach(n -> textos.add(n.asText()));
		return textos;
	}

	private static Set<Long> ids(JsonNode lista, String campo) {
		return new HashSet<>(idsEnOrden(lista, campo));
	}

	private static List<Long> idsEnOrden(JsonNode lista, String campo) {
		List<Long> ids = new ArrayList<>();
		lista.forEach(n -> ids.add(n.get(campo).asLong()));
		return ids;
	}

	private static JsonNode fila(JsonNode lista, String campo, long valor) {
		for (JsonNode n : lista) {
			if (n.get(campo).asLong() == valor) {
				return n;
			}
		}
		throw new AssertionError("Sin fila con " + campo + " = " + valor + " en " + lista);
	}
}
