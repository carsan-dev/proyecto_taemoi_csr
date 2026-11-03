package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.charset.StandardCharsets;

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
import com.taemoi.project.services.ProductosCatalogoService;

import jakarta.servlet.http.Cookie;

/**
 * Decisión del propietario (03/11/2026) contra MySQL: la gestión asigna los exámenes y las recompensas eligiendo el
 * deporte del alumno. Por la vía genérica, 400; por la del deporte, el deporte solo da el concepto y el 400 de otra
 * disciplina: el cargo NO queda ligado a la inscripción, así que ni nacer pagado ni cobrarlo después sube el grado (como
 * la vía genérica de antes). La matrícula sí queda ligada. En kickboxing el grado ROJO se llama como en su catálogo con
 * la misma regla que los cargos automáticos: solo con el comportamiento heredado de Taemoi y en sus productos de ROJO
 * (escuela {@code legado}, «MARRÓN»); en una escuela sin él (aquí, con el grado ROJO llamado «Café»), el concepto del
 * catálogo tal cual. El catálogo de la gestión ({@code GET /api/productos/todos}) dice qué productos piden deporte.
 * Escuelas sintéticas ({@link EscuelaConSedesDePrueba}) con kickboxing; datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AsignarExamenPorDeporteIntegrationTest extends MySqlIntegrationTest {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;
	@Autowired ProductosCatalogoService productosCatalogo;
	@Autowired org.springframework.transaction.support.TransactionTemplate tx;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	String tGestor;
	long kickboxing;
	long examenRojo, examenRojoBordado, examenDeKickboxing, camiseta;
	long examenSinClasificar, matricula, cinturonConGrado, cinturonSinClasificar;
	String conceptoRojo, conceptoBordado;

	/** Escuela con el comportamiento heredado de Taemoi: kickboxing con AZUL y ROJO («Marrón») y productos de ROJO. */
	EscuelaConSedesDePrueba legado;
	String tGestorLegado;
	long kickboxingLegado, examenRojoLegado, recompensaRojoLegado;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-kb-marron");
		tGestor = e.token(jwtService, usuarios, e.gestor);

		jdbc.update("INSERT INTO sistema_grados (organizacion_id, nombre, plantilla_origen) VALUES (?, 'Kick IT', NULL)",
				e.org);
		long sistema = jdbc.queryForObject("SELECT id FROM sistema_grados WHERE organizacion_id = ?", Long.class, e.org);
		jdbc.update("""
				INSERT INTO grado (organizacion_id, sistema_grados_id, codigo, nombre, orden, color_1, color_2, aplica_a)
				VALUES (?, ?, 'ROJO', 'Café', 1, '#8B4513', NULL, 'TODOS')""", e.org, sistema);
		long rojo = jdbc.queryForObject("SELECT id FROM grado WHERE sistema_grados_id = ? AND codigo = 'ROJO'",
				Long.class, sistema);
		jdbc.update("""
				INSERT INTO disciplina (organizacion_id, codigo, nombre, sistema_grados_id, edad_adulto, orden)
				VALUES (?, 'KICKBOXING', 'Kickboxing IT', ?, 15, 2)""", e.org, sistema);
		kickboxing = jdbc.queryForObject(
				"SELECT id FROM disciplina WHERE organizacion_id = ? AND codigo = 'KICKBOXING'", Long.class, e.org);

		conceptoRojo = "DERECHO A EXAMEN ROJO " + e.marca;
		examenRojo = producto(conceptoRojo, "EXAMEN_ROJO_IT", "EXAMEN", null, rojo);
		conceptoBordado = "DERECHO A EXAMEN ROJO BORDADO " + e.marca;
		examenRojoBordado = producto(conceptoBordado, "EXAMEN_ROJO_BORDADO_IT", "EXAMEN", null, rojo);
		examenDeKickboxing = producto("EXAMEN SOLO KICK " + e.marca, "EXAMEN_KICK_IT", "EXAMEN", kickboxing, rojo);
		camiseta = producto("CAMISETA KB " + e.marca, null, "OTRO", null, null);
		examenSinClasificar = producto("DERECHO A EXAMEN AZUL " + e.marca, null, "OTRO", null, null);
		matricula = producto("MATRÍCULA KB " + e.marca, null, "OTRO", null, null);
		// Mismo concepto (con otras mayúsculas) que un producto que concede grado: el catálogo lo resuelve por concepto.
		cinturonConGrado = producto("CINTURÓN CAFÉ " + e.marca, "CINTURON_CAFE_IT", "OTRO", null, rojo);
		cinturonSinClasificar = producto("cinturón café " + e.marca, null, "OTRO", null, null);
		producto("PASE 1º NIVEL " + e.marca, "PASE_1_NIVEL_IT", "OTRO", null, rojo);

		prepararEscuelaConComportamientoHeredado();
	}

	private void prepararEscuelaConComportamientoHeredado() {
		legado = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-kb-marron-legado");
		jdbc.update("UPDATE organizacion SET comportamiento_legado_taemoi = TRUE WHERE id = ?", legado.org);
		tGestorLegado = legado.token(jwtService, usuarios, legado.gestor);
		jdbc.update("INSERT INTO sistema_grados (organizacion_id, nombre, plantilla_origen) VALUES (?, 'Kick IT L', NULL)",
				legado.org);
		long sistema = jdbc.queryForObject("SELECT id FROM sistema_grados WHERE organizacion_id = ?", Long.class,
				legado.org);
		jdbc.update("""
				INSERT INTO grado (organizacion_id, sistema_grados_id, codigo, nombre, orden, color_1, color_2, aplica_a)
				VALUES (?, ?, 'AZUL', 'Azul', 1, '#0000FF', NULL, 'TODOS'),
				       (?, ?, 'ROJO', 'Marrón', 2, '#8B4513', NULL, 'TODOS')""", legado.org, sistema, legado.org, sistema);
		long rojo = jdbc.queryForObject("SELECT id FROM grado WHERE sistema_grados_id = ? AND codigo = 'ROJO'",
				Long.class, sistema);
		jdbc.update("""
				INSERT INTO disciplina (organizacion_id, codigo, nombre, sistema_grados_id, edad_adulto, orden)
				VALUES (?, 'KICKBOXING', 'Kickboxing IT L', ?, 15, 2)""", legado.org, sistema);
		kickboxingLegado = jdbc.queryForObject(
				"SELECT id FROM disciplina WHERE organizacion_id = ? AND codigo = 'KICKBOXING'", Long.class, legado.org);
		examenRojoLegado = producto(legado, "DERECHO A EXAMEN ROJO", "EXAMEN_ROJO", "EXAMEN", rojo);
		recompensaRojoLegado = producto(legado, "DERECHO DE CAMBIO A ROJO POR RECOMPENSA", "RECOMPENSA_ROJO",
				"RECOMPENSA", rojo);
	}

	@AfterEach
	void sinFugas() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	@Test
	void viaGenerica_conExamen_400SinCargo() throws Exception {
		long alumno = alumnoConDosDeportes("KBGEN");
		JsonNode r = leer(post("/api/productos-alumno/alumno/" + alumno + "/producto/" + examenRojo)
				.contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":1,\"pagado\":true}"), 400);
		assertEquals("Seleccione un deporte del alumno para asignar este producto.", r.get("mensaje").asText());
		assertEquals(0L, cargosDe(alumno));
		assertEquals(0L, cobrosDe(alumno));
	}

	/** Sin comportamiento heredado, el concepto del catálogo tal cual (como el cargo automático), sin inscripción. */
	@Test
	void kickboxing_sinComportamientoHeredado_examenRojo_conceptoDelCatalogoYSinInscripcion() throws Exception {
		long alumno = alumnoConDosDeportes("KBROJO");
		JsonNode cargo = asignar(alumno, examenRojo, "KICKBOXING", false);
		assertEquals(conceptoRojo, cargo.get("concepto").asText());
		long id = cargo.get("id").asLong();
		assertEquals(conceptoRojo, jdbc.queryForObject("SELECT concepto FROM producto_alumno WHERE id = ?", String.class,
				id));
		assertNull(inscripcionDelCargo(id));
	}

	@Test
	void kickboxing_sinComportamientoHeredado_examenRojoBordado_sinCambios() throws Exception {
		long alumno = alumnoConDosDeportes("KBBORD");
		assertEquals(conceptoBordado, asignar(alumno, examenRojoBordado, "KICKBOXING", false).get("concepto").asText());
	}

	@Test
	void taekwondo_examenRojo_sinCambiosYSinInscripcion() throws Exception {
		long alumno = alumnoConDosDeportes("KBTKD");
		JsonNode cargo = asignar(alumno, examenRojo, "TAEKWONDO", false);
		assertEquals(conceptoRojo, cargo.get("concepto").asText());
		assertNull(inscripcionDelCargo(cargo.get("id").asLong()));
	}

	@Test
	void examenPagado_porDeporte_derechoAExamenYUnCobroDeTesoreria() throws Exception {
		long alumno = alumnoConDosDeportes("KBPAG");
		assertFalse(derechoAExamen(alumno));
		long cargo = asignar(alumno, examenRojo, "KICKBOXING", true).get("id").asLong();
		assertTrue(derechoAExamen(alumno));
		assertEquals(1L, cobrosDe(alumno));
		assertEquals("TESORERIA", jdbc.queryForObject(
				"SELECT origen FROM cobro WHERE producto_alumno_id = ? AND estado = 'ACTIVO'", String.class, cargo));
		assertNotNull(jdbc.queryForObject("SELECT fecha_pago FROM producto_alumno WHERE id = ?", java.util.Date.class,
				cargo));
		assertNull(inscripcionDelCargo(cargo));
	}

	/** La matrícula sí queda ligada a la inscripción del deporte elegido, como antes. */
	@Test
	void matricula_porDeporte_ligadaALaInscripcion() throws Exception {
		long alumno = alumnoConDosDeportes("KBMAT");
		JsonNode cargo = asignar(alumno, matricula, "KICKBOXING", false);
		assertEquals("MATRÍCULA KB " + e.marca + " - KICKBOXING", cargo.get("concepto").asText());
		assertEquals(kickboxing, jdbc.queryForObject("""
				SELECT ad.disciplina_id FROM producto_alumno pa JOIN alumno_deporte ad ON ad.id = pa.alumno_deporte_id
				WHERE pa.id = ?""", Long.class, cargo.get("id").asLong()));
	}

	// ------------------------------------------------------------------ escuela con comportamiento heredado

	/** Examen a ROJO en kickboxing: «MARRÓN», sin inscripción; nace pagado: derecho a examen y el grado no sube. */
	@Test
	void legado_kickboxing_examenRojoPagado_marronDerechoAExamenYGradoSinCambios() throws Exception {
		long alumno = alumnoLegadoEnKickboxingAzul("LGEXPAG");
		JsonNode cargo = asignarEn(legado, tGestorLegado, alumno, examenRojoLegado, "KICKBOXING", true);
		assertEquals("DERECHO A EXAMEN MARRÓN", cargo.get("concepto").asText());
		assertNull(inscripcionDelCargo(cargo.get("id").asLong()));
		assertTrue(derechoAExamen(alumno));
		assertEquals("AZUL", gradoEnKickboxingLegado(alumno));
	}

	/** Pendiente y cobrado después por el PUT del cargo: el grado tampoco sube. */
	@Test
	void legado_kickboxing_examenRojoCobradoDespues_gradoSinCambios() throws Exception {
		long alumno = alumnoLegadoEnKickboxingAzul("LGEXPUT");
		long id = asignarEn(legado, tGestorLegado, alumno, examenRojoLegado, "KICKBOXING", false).get("id").asLong();
		assertNull(inscripcionDelCargo(id));
		cobrarPorElPut(id);
		assertTrue(derechoAExamen(alumno));
		assertEquals("AZUL", gradoEnKickboxingLegado(alumno));
	}

	@Test
	void legado_kickboxing_recompensaRojo_marronYGradoSinCambiosPagadaOCobradaDespues() throws Exception {
		long pagada = alumnoLegadoEnKickboxingAzul("LGREPAG");
		JsonNode cargo = asignarEn(legado, tGestorLegado, pagada, recompensaRojoLegado, "KICKBOXING", true);
		assertEquals("DERECHO DE CAMBIO A MARRÓN POR RECOMPENSA", cargo.get("concepto").asText());
		assertNull(inscripcionDelCargo(cargo.get("id").asLong()));
		assertEquals("AZUL", gradoEnKickboxingLegado(pagada));

		long despues = alumnoLegadoEnKickboxingAzul("LGREPUT");
		long id = asignarEn(legado, tGestorLegado, despues, recompensaRojoLegado, "KICKBOXING", false).get("id")
				.asLong();
		cobrarPorElPut(id);
		assertNull(inscripcionDelCargo(id));
		assertEquals("AZUL", gradoEnKickboxingLegado(despues));
	}

	@Test
	void examenDeOtraDisciplina_400SinCargo() throws Exception {
		long alumno = alumnoConDosDeportes("KBOTRA");
		JsonNode r = leer(post("/api/productos-alumno/alumno/" + alumno + "/producto/" + examenDeKickboxing
				+ "/deporte/TAEKWONDO").contentType(MediaType.APPLICATION_JSON)
				.content("{\"cantidad\":1,\"pagado\":false}"), 400);
		assertEquals("Este producto es de otra disciplina: no se puede asignar en TAEKWONDO.", r.get("mensaje").asText());
		assertEquals(0L, cargosDe(alumno));
		// En su disciplina, sí.
		assertEquals("EXAMEN SOLO KICK " + e.marca,
				asignar(alumno, examenDeKickboxing, "KICKBOXING", false).get("concepto").asText());
	}

	/**
	 * El panel de la gestión necesita saber cuándo pedir el deporte: «requiereDeporte», la misma regla con la que la vía
	 * genérica da 400. El resto del producto, igual que antes.
	 */
	@Test
	void catalogoDeLaGestion_diceQueProductosRequierenDeporte() throws Exception {
		JsonNode todos = leer(get("/api/productos/todos"), 200);
		JsonNode examen = buscar(todos, examenRojo);
		assertTrue(examen.get("requiereDeporte").asBoolean(), examen.toString());
		assertEquals(conceptoRojo, examen.get("concepto").asText());
		assertEquals(20.0, examen.get("precio").asDouble(), 0.0);
		assertTrue(examen.has("vendibleProfesor"), examen.toString());
		assertFalse(examen.has("tipo") || examen.has("codigo") || examen.has("disciplina") || examen.has("grado"),
				examen.toString());
		for (long producto : new long[] { examenSinClasificar, matricula, cinturonConGrado, cinturonSinClasificar }) {
			assertTrue(buscar(todos, producto).get("requiereDeporte").asBoolean(), buscar(todos, producto).toString());
		}
		assertFalse(buscar(todos, camiseta).get("requiereDeporte").asBoolean());
		// La ficha de un producto no cambia.
		JsonNode ficha = leer(get("/api/productos/" + examenRojo), 200);
		assertFalse(ficha.has("requiereDeporte") || ficha.has("tipo"), ficha.toString());
	}

	/**
	 * El catálogo preparado en memoria (el de «requiereDeporte») resuelve el concepto como la consulta por concepto de
	 * siempre contra la columna ({@code utf8mb4_0900_ai_ci} en V1: sin mayúsculas ni acentos y NO PAD, así que un
	 * espacio de más al final ya no es el mismo concepto).
	 */
	@Test
	void catalogoEnMemoria_resuelveLosConceptosComoLaConsulta() {
		String base = "CINTURÓN CAFÉ " + e.marca;
		String pase = "PASE 1º NIVEL " + e.marca;
		java.util.List<String> conceptos = java.util.List.of(base, base.toLowerCase(java.util.Locale.ROOT),
				"CINTURON CAFE " + e.marca, base + " ", base + "  ", " " + base, base + " - KICKBOXING", pase,
				"PASE 1o NIVEL " + e.marca, "PASE 1O NIVEL " + e.marca, pase + " ");
		java.util.Map<String, java.util.Optional<String>> porConsulta = new java.util.LinkedHashMap<>();
		java.util.Map<String, java.util.Optional<String>> enMemoria = new java.util.LinkedHashMap<>();
		TenantContext.ejecutarCon(e.org, () -> tx.executeWithoutResult(s -> {
			java.util.function.Function<String, java.util.Optional<String>> preparado = productosCatalogo
					.prepararGradoConcedidoPorConcepto();
			for (String concepto : conceptos) {
				porConsulta.put(concepto, productosCatalogo.buscarCodigoGradoConcedido(null, concepto));
				enMemoria.put(concepto, preparado.apply(concepto));
			}
		}));
		assertEquals(porConsulta, enMemoria);
		assertEquals(java.util.Optional.of("ROJO"), enMemoria.get("CINTURON CAFE " + e.marca));
		assertEquals(java.util.Optional.empty(), enMemoria.get(base + " "), "NO PAD: el espacio final cuenta");
	}

	/** Lo que el catálogo marca como «requiereDeporte», la vía genérica lo rechaza. */
	@Test
	void viaGenerica_rechazaLoQueElCatalogoMarca() throws Exception {
		long alumno = alumnoConDosDeportes("KBREGLA");
		for (long producto : new long[] { examenSinClasificar, cinturonSinClasificar }) {
			JsonNode r = leer(post("/api/productos-alumno/alumno/" + alumno + "/producto/" + producto)
					.contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":1,\"pagado\":false}"), 400);
			assertEquals("Seleccione un deporte del alumno para asignar este producto.", r.get("mensaje").asText());
		}
		assertEquals(400, mvc.perform(post("/api/productos-alumno/alumno/" + alumno + "/producto/" + matricula)
				.contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":1,\"pagado\":false}")
				.cookie(new Cookie("jwt", tGestor))).andReturn().getResponse().getStatus());
		assertEquals(0L, cargosDe(alumno));
		leer(post("/api/productos-alumno/alumno/" + alumno + "/producto/" + camiseta)
				.contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":1,\"pagado\":false}"), 200);
	}

	// ------------------------------------------------------------------ ayudantes

	private long producto(String concepto, String codigo, String tipo, Long disciplina, Long grado) {
		jdbc.update("""
				INSERT INTO producto (concepto, precio, codigo, tipo, organizacion_id, vendible_profesor, disciplina_id,
				  grado_id)
				VALUES (?, 20.0, ?, ?, ?, FALSE, ?, ?)""", concepto, codigo, tipo, e.org, disciplina, grado);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = ?", Long.class, e.org);
	}

	private long producto(EscuelaConSedesDePrueba escuela, String concepto, String codigo, String tipo, long grado) {
		jdbc.update("""
				INSERT INTO producto (concepto, precio, codigo, tipo, organizacion_id, vendible_profesor, grado_id)
				VALUES (?, 20.0, ?, ?, ?, FALSE, ?)""", concepto, codigo, tipo, escuela.org, grado);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = ?", Long.class, escuela.org);
	}

	/** Alumno nuevo de la sede A de la escuela con comportamiento heredado, en kickboxing con grado AZUL. */
	private long alumnoLegadoEnKickboxingAzul(String nombre) {
		long alumno = legado.alumno(nombre + legado.marca, legado.sA, true);
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
				  organizacion_id, fecha_alta, fecha_alta_inicial, disciplina_id, grado_id)
				SELECT TRUE, 1, ?, 0, 1, 0, ?, '2023-09-01', '2023-09-01', ?, g.id
				FROM grado g JOIN disciplina d ON d.sistema_grados_id = g.sistema_grados_id
				WHERE d.id = ? AND g.codigo = 'AZUL'""", alumno, legado.org, kickboxingLegado, kickboxingLegado);
		return alumno;
	}

	private String gradoEnKickboxingLegado(long alumno) {
		return jdbc.queryForObject("""
				SELECT g.codigo FROM alumno_deporte ad JOIN grado g ON g.id = ad.grado_id
				WHERE ad.alumno_id = ? AND ad.disciplina_id = ?""", String.class, alumno, kickboxingLegado);
	}

	private Long inscripcionDelCargo(long cargo) {
		return jdbc.queryForObject("SELECT alumno_deporte_id FROM producto_alumno WHERE id = ?", Long.class, cargo);
	}

	private void cobrarPorElPut(long cargo) throws Exception {
		MvcResult r = mvc.perform(put("/api/productos-alumno/" + cargo).contentType(MediaType.APPLICATION_JSON)
				.content("{\"pagado\":true,\"metodoPago\":\"EFECTIVO\"}").cookie(new Cookie("jwt", tGestorLegado)))
				.andReturn();
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString(StandardCharsets.UTF_8));
		assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT pagado FROM producto_alumno WHERE id = ?", Boolean.class,
				cargo));
	}

	/** Alumno nuevo de la sede A inscrito en taekwondo y en kickboxing. */
	private long alumnoConDosDeportes(String nombre) {
		long alumno = e.alumno(nombre + e.marca, e.sA, true);
		e.taekwondo(alumno, false);
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
				  organizacion_id, fecha_alta, fecha_alta_inicial, disciplina_id)
				VALUES (TRUE, 0, ?, 0, 0, 0, ?, '2023-09-01', '2023-09-01', ?)""", alumno, e.org, kickboxing);
		return alumno;
	}

	private JsonNode asignar(long alumno, long producto, String deporte, boolean pagado) throws Exception {
		return asignarEn(e, tGestor, alumno, producto, deporte, pagado);
	}

	private JsonNode asignarEn(EscuelaConSedesDePrueba escuela, String token, long alumno, long producto,
			String deporte, boolean pagado) throws Exception {
		MvcResult r = mvc.perform(post("/api/productos-alumno/alumno/" + alumno + "/producto/" + producto + "/deporte/"
				+ deporte).contentType(MediaType.APPLICATION_JSON)
				.content("{\"cantidad\":1,\"pagado\":" + pagado + ",\"metodoPago\":\"EFECTIVO\"}")
				.cookie(new Cookie("jwt", token))).andReturn();
		assertEquals(200, r.getResponse().getStatus(),
				escuela.marca + ": " + r.getResponse().getContentAsString(StandardCharsets.UTF_8));
		return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

	private JsonNode buscar(JsonNode productos, long id) {
		for (JsonNode p : productos) {
			if (p.get("id").asLong() == id) {
				return p;
			}
		}
		throw new AssertionError("Falta el producto " + id + " en " + productos);
	}

	private long cargosDe(long alumno) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM producto_alumno WHERE alumno_id = ?", Long.class, alumno);
	}

	private long cobrosDe(long alumno) {
		return jdbc.queryForObject("""
				SELECT COUNT(*) FROM cobro c JOIN producto_alumno pa ON pa.id = c.producto_alumno_id
				WHERE pa.alumno_id = ?""", Long.class, alumno);
	}

	private boolean derechoAExamen(long alumno) {
		return Boolean.TRUE.equals(jdbc.queryForObject("SELECT tiene_derecho_examen FROM alumno WHERE id = ?",
				Boolean.class, alumno));
	}

	private JsonNode leer(MockHttpServletRequestBuilder peticion, int esperado) throws Exception {
		MvcResult r = mvc.perform(peticion.cookie(new Cookie("jwt", tGestor))).andReturn();
		assertEquals(esperado, r.getResponse().getStatus(), r.getResponse().getContentAsString(StandardCharsets.UTF_8));
		return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
	}
}
