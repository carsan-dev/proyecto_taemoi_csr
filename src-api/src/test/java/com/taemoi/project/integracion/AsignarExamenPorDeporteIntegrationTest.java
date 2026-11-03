package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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

import jakarta.servlet.http.Cookie;

/**
 * Decisión del propietario (03/11/2026) contra MySQL: la gestión asigna los exámenes y las recompensas eligiendo el
 * deporte del alumno. Por la vía genérica, 400; por la del deporte, el cargo queda ligado a esa inscripción y, en
 * kickboxing, el grado ROJO se llama como en su catálogo (aquí «CAFÉ», para distinguirlo del respaldo «MARRÓN»). El
 * catálogo de la gestión ({@code GET /api/productos/todos}) dice el tipo de cada producto. Escuela sintética propia
 * ({@link EscuelaConSedesDePrueba}) con kickboxing; datos inventados.
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

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	String tGestor;
	long kickboxing;
	long examenRojo, examenRojoBordado, examenDeKickboxing, camiseta;
	String conceptoRojo, conceptoBordado;

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

	@Test
	void kickboxing_examenRojo_conElNombreDelCatalogoYLigadoALaInscripcion() throws Exception {
		long alumno = alumnoConDosDeportes("KBROJO");
		JsonNode cargo = asignar(alumno, examenRojo, "KICKBOXING", false);
		String esperado = "DERECHO A EXAMEN CAFÉ " + e.marca;
		assertEquals(esperado, cargo.get("concepto").asText());
		long id = cargo.get("id").asLong();
		assertEquals(esperado, jdbc.queryForObject("SELECT concepto FROM producto_alumno WHERE id = ?", String.class, id));
		assertEquals(kickboxing, jdbc.queryForObject("""
				SELECT ad.disciplina_id FROM producto_alumno pa JOIN alumno_deporte ad ON ad.id = pa.alumno_deporte_id
				WHERE pa.id = ?""", Long.class, id));
	}

	@Test
	void kickboxing_examenRojoBordado() throws Exception {
		long alumno = alumnoConDosDeportes("KBBORD");
		assertEquals("DERECHO A EXAMEN CAFÉ BORDADO " + e.marca,
				asignar(alumno, examenRojoBordado, "KICKBOXING", false).get("concepto").asText());
	}

	@Test
	void taekwondo_examenRojo_sinCambios() throws Exception {
		long alumno = alumnoConDosDeportes("KBTKD");
		JsonNode cargo = asignar(alumno, examenRojo, "TAEKWONDO", false);
		assertEquals(conceptoRojo, cargo.get("concepto").asText());
		assertEquals(e.disciplina, jdbc.queryForObject("""
				SELECT ad.disciplina_id FROM producto_alumno pa JOIN alumno_deporte ad ON ad.id = pa.alumno_deporte_id
				WHERE pa.id = ?""", Long.class, cargo.get("id").asLong()));
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

	/** El panel de la gestión necesita el tipo para pedir el deporte; el resto del producto, igual que antes. */
	@Test
	void catalogoDeLaGestion_diceElTipoDeCadaProducto() throws Exception {
		JsonNode todos = leer(get("/api/productos/todos"), 200);
		JsonNode examen = buscar(todos, examenRojo);
		assertEquals("EXAMEN", examen.get("tipo").asText());
		assertEquals(conceptoRojo, examen.get("concepto").asText());
		assertEquals(20.0, examen.get("precio").asDouble(), 0.0);
		assertTrue(examen.has("vendibleProfesor"), examen.toString());
		assertFalse(examen.has("codigo") || examen.has("disciplina") || examen.has("grado"), examen.toString());
		assertEquals("OTRO", buscar(todos, camiseta).get("tipo").asText());
		// La ficha de un producto no cambia.
		assertFalse(leer(get("/api/productos/" + examenRojo), 200).has("tipo"));
	}

	// ------------------------------------------------------------------ ayudantes

	private long producto(String concepto, String codigo, String tipo, Long disciplina, Long grado) {
		jdbc.update("""
				INSERT INTO producto (concepto, precio, codigo, tipo, organizacion_id, vendible_profesor, disciplina_id,
				  grado_id)
				VALUES (?, 20.0, ?, ?, ?, FALSE, ?, ?)""", concepto, codigo, tipo, e.org, disciplina, grado);
		return jdbc.queryForObject("SELECT id FROM producto WHERE organizacion_id = ? AND concepto = ?", Long.class,
				e.org, concepto);
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
		return leer(post("/api/productos-alumno/alumno/" + alumno + "/producto/" + producto + "/deporte/" + deporte)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"cantidad\":1,\"pagado\":" + pagado + ",\"metodoPago\":\"EFECTIVO\"}"), 200);
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
