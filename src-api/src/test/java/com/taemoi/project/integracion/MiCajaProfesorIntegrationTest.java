package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import com.taemoi.project.config.ValidacionRelojConfig;
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
 * <li>{@code GET /api/caja/profesor/cargos-pendientes}: {@code vencido} con la regla de la tesorería
 * ({@code PendienteVencidoService}, hoy fijado con el {@code Clock} simulado) y {@code grupos} con solo los nombres de SUS
 * grupos, sin N+1.</li>
 * </ul>
 * Escuela sintética ({@link EscuelaConSedesDePrueba}) y otra para el aislamiento. Datos inventados.
 */
@SpringBootTest(properties = {
		"spring.jpa.properties.hibernate.session_factory.statement_inspector=com.taemoi.project.integracion.ContadorSentencias" })
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MiCajaProfesorIntegrationTest extends MySqlIntegrationTest {

	/** Las cinco de la sesión más las tres de {@code cargos-pendientes} (cargos, nombres de grupos y vencidos). */
	private static final int SENTENCIAS_POR_COBRAR = 8;

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;
	@MockitoBean Clock reloj;

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

	/** El reloj real salvo en las pruebas de {@code vencido}, que lo fijan con {@link #hoy}. */
	@BeforeEach
	void relojReal() {
		when(reloj.instant()).thenAnswer(i -> Instant.now());
		when(reloj.getZone()).thenReturn(ValidacionRelojConfig.ZONA);
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

	// ------------------------------------------------------------------ por cobrar: vencido y grupos

	@Test
	void porCobrar_vencido_laMismaReglaQueLaTesoreria() throws Exception {
		long g = grupo("IT 3O Vencido " + e.marca, e.sA);
		String token = e.token(jwtService, usuarios, profesor(e, "profvencido", g));
		long alumno = alumno("VENCE", "PORCOBRAR", true, g);
		long baja = alumno("BAJA", "PORCOBRAR", false, g);
		long pMens = productoDeTipo("CUOTA 3O", "MENSUALIDAD", "MENSUALIDAD_3O");
		long pExamen = productoDeTipo("TASA 3O", "EXAMEN", "EXAMEN_3O");
		long pOtro = productoDeTipo("CAMISETA 3O", "OTRO", null);

		long mesAnterior = cargo(alumno, pMens, "MENSUALIDAD FEBRERO 2026", "2026-02-01 12:00:00");
		long mesEnCurso = cargo(alumno, pMens, "MENSUALIDAD MARZO 2026", "2026-03-01 12:00:00");
		long mesFuturo = cargo(alumno, pMens, "MENSUALIDAD ABRIL 2026", "2026-03-01 12:00:00");
		long sinMes = cargo(alumno, pMens, "CUOTA 3O", "2026-01-15 12:00:00");
		long examenPasado = cargo(alumno, pExamen, "DERECHO A EXAMEN 3O A", "2026-01-10 12:00:00");
		inscribir(alumno, examenPasado, "2026-02-20");
		long examenElDia6 = cargo(alumno, pExamen, "DERECHO A EXAMEN 3O B", "2026-01-10 12:00:00");
		inscribir(alumno, examenElDia6, "2026-03-06");
		long examenFuturo = cargo(alumno, pExamen, "DERECHO A EXAMEN 3O C", "2026-01-10 12:00:00");
		inscribir(alumno, examenFuturo, "2026-06-15");
		long examenSinConvocatoria = cargo(alumno, pExamen, "DERECHO A EXAMEN 3O D", "2026-02-10 12:00:00");
		long camiseta = cargo(alumno, pOtro, "CAMISETA 3O", "2025-01-10 12:00:00");
		long deBaja = cargo(baja, pMens, "MENSUALIDAD ENERO 2026", "2026-01-02 12:00:00");

		// Día 5: la mensualidad del mes en curso aún no vence y el examen del día 6 tampoco.
		hoy("2026-03-05T23:59");
		JsonNode dia5 = leer(get("/api/caja/profesor/cargos-pendientes"), token, 200);
		assertEquals(10, dia5.size());
		assertEquals(Set.of(mesAnterior, sinMes, examenPasado, examenSinConvocatoria), vencidos(dia5));
		assertEquals(vencidosDeLaTesoreria(alumno, baja), vencidos(dia5));

		// Día 6: vencen los dos. Lo futuro, lo que no es mensualidad ni examen y el alumno de baja, nunca.
		hoy("2026-03-06T00:00");
		JsonNode dia6 = leer(get("/api/caja/profesor/cargos-pendientes"), token, 200);
		assertEquals(Set.of(mesAnterior, mesEnCurso, sinMes, examenPasado, examenElDia6, examenSinConvocatoria),
				vencidos(dia6));
		assertEquals(vencidosDeLaTesoreria(alumno, baja), vencidos(dia6));
		for (long noVencido : List.of(mesFuturo, examenFuturo, camiseta, deBaja)) {
			assertFalse(fila(dia6, "cargoId", noVencido).get("vencido").asBoolean(), "cargo " + noVencido);
		}
	}

	@Test
	void porCobrar_grupos_soloLosDelProfesor_porNombre() throws Exception {
		long gB = grupo("IT 3O Grupo B " + e.marca, e.sA);
		long gA = grupo("IT 3O Grupo A " + e.marca, e.sA);
		long ajeno = grupo("IT 3O Grupo Ajeno " + e.marca, e.sA);
		long prof = profesor(e, "profpor", gB);
		asignar(e, prof, gA);
		profesor(e, "profajeno", ajeno);
		String token = e.token(jwtService, usuarios, prof);
		long alumno = alumno("GRUPOS", "PORCOBRAR", true, gA, gB, ajeno);
		long producto = producto("LLAVERO 3O", Boolean.FALSE);
		long c1 = cargo(alumno, producto, "LLAVERO 3O", "2026-02-01 12:00:00");
		long c2 = cargo(alumno, producto, "LLAVERO 3O BIS", "2026-02-02 12:00:00");

		JsonNode cargos = leer(get("/api/caja/profesor/cargos-pendientes"), token, 200);
		assertEquals(2, cargos.size());
		for (long c : List.of(c1, c2)) {
			JsonNode grupos = fila(cargos, "cargoId", c).get("grupos");
			assertEquals(List.of(nombreGrupo(gA), nombreGrupo(gB)), textos(grupos));
		}

		// Otra escuela, con un cargo propio: ni sus cargos ni sus grupos cruzan.
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, concepto, precio, cantidad, pagado, fecha_asignacion,
				  organizacion_id, sede_id)
				VALUES (?, 'LLAVERO OTRA', 30, 1, 0, '2026-02-01 12:00:00', ?, ?)""", otra.aGrupoA, otra.org, otra.sA);
		JsonNode deOtra = leer(get("/api/caja/profesor/cargos-pendientes"), tProfOtra, 200);
		assertEquals(1, deOtra.size(), "el cargo de la otra escuela: " + deOtra);
		assertEquals(1, deOtra.get(0).get("grupos").size());
		assertFalse(deOtra.toString().contains(e.marca), deOtra.toString());
		for (JsonNode c : deOtra) {
			assertTrue(textos(c.get("grupos")).stream().noneMatch(n -> n.contains(e.marca)), c.toString());
		}
	}

	@Test
	void porCobrar_vencido_enSedeAEnSedeBYSinSede() throws Exception {
		long g = grupo("IT 3O Sedes " + e.marca, e.sA);
		long prof = profesor(e, "profporsedes", g);
		String token = e.token(jwtService, usuarios, prof);
		long al = alumno("SEDES", "PORCOBRAR", true, g);
		long p = productoDeTipo("CUOTA 3O SEDES", "MENSUALIDAD", "MENSUALIDAD_3O_S");
		long c1 = cargo(al, p, "MENSUALIDAD ENERO 2026", "2026-01-02 12:00:00");
		long c2 = cargo(al, p, "MENSUALIDAD FEBRERO 2026", "2026-02-02 12:00:00");
		long c3 = cargo(al, p, "MENSUALIDAD DICIEMBRE 2025", "2025-12-02 12:00:00");
		jdbc.update("UPDATE producto_alumno SET sede_id = ? WHERE id = ?", e.sB, c2);
		jdbc.update("UPDATE producto_alumno SET sede_id = NULL WHERE id = ?", c3);
		hoy("2026-03-10T10:00");

		JsonNode l = leer(get("/api/caja/profesor/cargos-pendientes"), token, 200);
		assertEquals(Set.of(c1, c2, c3), vencidos(l), l.toString());
		assertEquals(vencidosDeLaTesoreria(al), vencidos(l));
	}

	@Test
	void porCobrar_sinNMasUno() throws Exception {
		long g = grupo("IT 3O NMas1 " + e.marca, e.sA);
		long prof = profesor(e, "profpornmas1", g);
		String token = e.token(jwtService, usuarios, prof);
		long pMens = productoDeTipo("CUOTA 3O NMAS1", "MENSUALIDAD", "MENSUALIDAD_3O_N");
		cargo(alumno("UNO", "PORNMAS", true, g), pMens, "MENSUALIDAD ENERO 2026", "2026-01-02 12:00:00");
		leer(get("/api/caja/profesor/cargos-pendientes"), token, 200);
		int conUno = ContadorSentencias.contar(() -> estado(get("/api/caja/profesor/cargos-pendientes"), token)).total();

		long g2 = grupo("IT 3O NMas1 B " + e.marca, e.sB);
		asignar(e, prof, g2);
		for (int i = 0; i < 4; i++) {
			long otro = alumno("X" + i, "PORNMAS", true, g, g2);
			cargo(otro, pMens, "MENSUALIDAD ENERO 2026", "2026-01-02 12:00:00");
			cargo(otro, pMens, "MENSUALIDAD FEBRERO 2026", "2026-02-02 12:00:00");
		}
		ContadorSentencias.Conteo<JsonNode> conCinco = ContadorSentencias
				.contar(() -> leer(get("/api/caja/profesor/cargos-pendientes"), token, 200));
		assertEquals(9, conCinco.resultado().size());
		assertEquals(conUno, conCinco.total(), "mismas sentencias con 1 y con 5 alumnos: " + conCinco.sentencias());
		assertEquals(SENTENCIAS_POR_COBRAR, conCinco.total(), "cargos, grupos y vencidos: " + conCinco.sentencias());
	}

	// ------------------------------------------------------------------ utilidades

	/** Fija «hoy» (Europe/Madrid) en el {@code Clock} simulado. */
	private void hoy(String ahora) {
		when(reloj.instant()).thenReturn(LocalDateTime.parse(ahora).atZone(ValidacionRelojConfig.ZONA).toInstant());
	}

	/** Los ids con {@code vencido} verdadero de la respuesta de {@code cargos-pendientes}. */
	private static Set<Long> vencidos(JsonNode cargos) {
		Set<Long> vencidos = new HashSet<>();
		cargos.forEach(c -> {
			if (c.get("vencido").asBoolean()) {
				vencidos.add(c.get("cargoId").asLong());
			}
		});
		return vencidos;
	}

	/** Lo que la tesorería da por vencido de esos alumnos ({@code PendienteVencidoService#porAlumno}). */
	private Set<Long> vencidosDeLaTesoreria(long... alumnos) throws Exception {
		MockHttpServletRequestBuilder peticion = get("/api/tesoreria/pendientes-por-alumno");
		for (long alumno : alumnos) {
			peticion.param("alumnoIds", String.valueOf(alumno));
		}
		Set<Long> vencidos = new HashSet<>();
		for (JsonNode a : leer(peticion, tGestor, 200)) {
			vencidos.addAll(ids(a.get("detalle"), "productoAlumnoId"));
		}
		return vencidos;
	}

	private long productoDeTipo(String concepto, String tipo, String codigo) {
		jdbc.update("INSERT INTO producto (concepto, precio, codigo, tipo, organizacion_id) VALUES (?, 1.0, ?, ?, ?)",
				concepto + " " + e.marca, codigo == null ? null : codigo + "_" + e.marca, tipo, e.org);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = ?", Long.class, e.org);
	}

	private long cargo(long alumno, long producto, String concepto, String fecha) {
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, producto_id, concepto, precio, cantidad, pagado, fecha_asignacion,
				  organizacion_id, sede_id)
				VALUES (?, ?, ?, ?, 1, 0, ?, ?, ?)""", alumno, producto, concepto, new BigDecimal("30.00"), fecha, e.org,
				e.sA);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE organizacion_id = ?", Long.class, e.org);
	}

	private void inscribir(long alumno, long cargo, String fechaConvocatoria) {
		jdbc.update("INSERT INTO convocatoria (fecha_convocatoria, organizacion_id, disciplina_id) VALUES (?, ?, ?)",
				fechaConvocatoria, e.org, e.disciplina);
		long convocatoria = jdbc.queryForObject("SELECT MAX(id) FROM convocatoria WHERE organizacion_id = ?", Long.class,
				e.org);
		jdbc.update("""
				INSERT INTO alumno_convocatoria (cuantia_examen, pagado, alumno_id, convocatoria_id, grado_actual,
				  grado_siguiente, organizacion_id, producto_alumno_id) VALUES (20, 0, ?, ?, NULL, NULL, ?, ?)""",
				alumno, convocatoria, e.org, cargo);
	}

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
