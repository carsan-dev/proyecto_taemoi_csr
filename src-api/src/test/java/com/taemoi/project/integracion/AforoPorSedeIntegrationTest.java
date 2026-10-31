package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.dtos.response.GrupoConAlumnosDTO;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.EmailService;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza 9-7 (decisión 245) contra MySQL: el aforo de un turno es el de la sede de su grupo; si la sede no lo tiene o el
 * turno no tiene sede, el límite común de la escuela.
 *
 * <p>Dos escuelas sintéticas ({@link EscuelaConSedesDePrueba}) con sedes de aforo distinto y una sede sin aforo, y
 * Moiskimdo con su sede sin aforo. Las altas son las reales ({@code POST /api/preinscripciones}); la escuela sale del
 * token. Datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AforoPorSedeIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final int LIMITE_E = 2;
	private static final int LIMITE_OTRA = 5;
	private static final String PENDIENTE = "PENDIENTE";
	private static final String EN_ESPERA = "EN_LISTA_ESPERA";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean EmailService email;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	/** Sede activa de {@code e} sin aforo propio, con el grupo gA2 y su turno {@code tD}. */
	long sD, tD;
	String tokenE, tokenOtra;
	int altas;

	@BeforeEach
	void preparar() throws Exception {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-9-7");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-9-7-otra");
		tokenE = e.token(jwtService, usuarios, e.gestor);
		tokenOtra = otra.token(jwtService, usuarios, otra.gestor);

		fijarLimite(e.org, String.valueOf(LIMITE_E));
		fijarLimite(otra.org, String.valueOf(LIMITE_OTRA));
		aforo(e.sA, 1);
		aforo(e.sB, 3);
		aforo(otra.sA, 2);

		jdbc.update("INSERT INTO sede (organizacion_id, nombre, activa) VALUES (?, ?, TRUE)", e.org, "IT 9-7 D " + e.marca);
		sD = jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = ? AND nombre = ?", Long.class, e.org,
				"IT 9-7 D " + e.marca);
		jdbc.update("UPDATE grupo SET sede_id = ? WHERE id = ?", sD, e.gA2);
		tD = turno(e.org, e.gA2);

		plantilla(e);
		plantilla(otra);
	}

	// ------------------------------------------------------------------ lecturas

	@Test
	void resumen_llevaElAforoDeLaSedeOElLimiteComun() throws Exception {
		JsonNode resumen = leer(get("/api/turnos/resumen"), tokenE, 200);
		assertEquals(1, turno(resumen, e.tA).get("aforoEfectivo").asInt(), "sede A, aforo 1");
		assertEquals(3, turno(resumen, e.tB).get("aforoEfectivo").asInt(), "sede B, aforo 3");
		assertEquals(LIMITE_E, turno(resumen, tD).get("aforoEfectivo").asInt(), "sede sin aforo: el límite común");
		assertEquals(LIMITE_E, turno(resumen, e.tSin).get("aforoEfectivo").asInt(), "sin grupo: el límite común");

		long tSinSede = turno(e.org, e.gSin);
		assertEquals(LIMITE_E, turno(leer(get("/api/turnos/resumen"), tokenE, 200), tSinSede).get("aforoEfectivo").asInt(),
				"grupo sin sede: el límite común");
	}

	@Test
	void limitePublico_noCambia() throws Exception {
		assertEquals(LIMITE_E, leer(get("/api/turnos/limite"), tokenE, 200).asInt());
	}

	@Test
	void horariosPublicos_usanElAforoEfectivo() throws Exception {
		JsonNode dto = leer(get("/api/turnos/dto"), tokenE, 200);
		JsonNode a = turno(dto, e.tA);
		assertEquals(1, a.get("aforoEfectivo").asInt());
		assertEquals(1, a.get("ocupacionEfectiva").asInt());
		assertTrue(a.get("completo").asBoolean(), "1 de 1, aunque el límite común es 2");
		JsonNode b = turno(dto, e.tB);
		assertEquals(3, b.get("aforoEfectivo").asInt());
		assertFalse(b.get("completo").asBoolean());
		assertEquals(LIMITE_E, turno(dto, tD).get("aforoEfectivo").asInt());
		assertTrue(turno(dto, e.tSin).get("aforoEfectivo").isNull(), "sin grupo no hay plazas que mostrar");

		JsonNode publicos = leer(get("/api/preinscripciones/public/turnos/TAEKWONDO"), tokenE, 200);
		assertTrue(turno(publicos, e.tA).get("completo").asBoolean());
		assertFalse(turno(publicos, e.tB).get("completo").asBoolean());
	}

	// ------------------------------------------------------------------ preinscripción al límite

	@Test
	void preinscripcion_cadaSedeLlenaConSuAforo() throws Exception {
		assertEquals(EN_ESPERA, alta(tokenE, e.tA), "sede A llena con 1, aunque el límite común es 2");

		assertEquals(PENDIENTE, alta(tokenE, e.tB));
		assertEquals(PENDIENTE, alta(tokenE, e.tB), "sede B admite 3: la tercera plaza supera el límite común");
		assertEquals(EN_ESPERA, alta(tokenE, e.tB));

		assertEquals(PENDIENTE, alta(tokenE, tD));
		assertEquals(PENDIENTE, alta(tokenE, tD));
		assertEquals(EN_ESPERA, alta(tokenE, tD), "sede sin aforo: el límite común de 2");
	}

	@Test
	void preinscripcion_otraEscuelaConSusAforos() throws Exception {
		assertEquals(PENDIENTE, alta(tokenOtra, otra.tA));
		assertEquals(EN_ESPERA, alta(tokenOtra, otra.tA), "su sede A admite 2");

		// Su sede B no tiene aforo: el límite común de ESTA escuela (5), no los de la primera.
		for (int i = 0; i < LIMITE_OTRA - 1; i++) {
			assertEquals(PENDIENTE, alta(tokenOtra, otra.tB));
		}
		assertEquals(EN_ESPERA, alta(tokenOtra, otra.tB));
	}

	// ------------------------------------------------------------------ guardar el aforo

	@Test
	void guardarAforoMenorQueLaOcupacion_seGuardaYAvisa() throws Exception {
		alta(tokenE, e.tB); // 2 en el turno de B (aB y la solicitud pendiente)

		JsonNode editada = editarAforo(e.sB, 1);
		assertEquals(1, editada.get("aforoTurno").asInt());
		assertTrue(editada.get("turnosSobreAforo").asBoolean());
		assertEquals(1, jdbc.queryForObject("SELECT aforo_turno FROM sede WHERE id = ?", Integer.class, e.sB));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM alumno_turno WHERE turno_id = ?", Integer.class, e.tB),
				"no se da de baja a nadie");
		assertEquals(EN_ESPERA, alta(tokenE, e.tB), "no entran plazas nuevas");

		assertFalse(editarAforo(e.sB, 1).get("turnosSobreAforo").asBoolean(), "sin cambio de aforo no se repite el aviso");
	}

	@Test
	void guardarAforoIgualALaOcupacionOSinAforo_noAvisa() throws Exception {
		assertFalse(editarAforo(e.sB, 1).get("turnosSobreAforo").asBoolean(), "1 inscrito en un aforo de 1 no lo supera");
		assertFalse(editarAforo(e.sB, null).get("turnosSobreAforo").asBoolean(), "sin aforo no hay con qué comparar");
		assertFalse(leer(get("/api/sedes/" + e.sB), tokenE, 200).has("turnosSobreAforo"), "solo lo lleva la edición");
	}

	@Test
	void quitarElAforo_avisaSiLaOcupacionSuperaElLimiteComun() throws Exception {
		alta(tokenE, e.tB);
		alta(tokenE, e.tB); // 3 en el turno de B (aB y dos pendientes) frente al límite común de 2

		assertTrue(editarAforo(e.sB, null).get("turnosSobreAforo").asBoolean());
		assertNull(jdbc.queryForObject("SELECT aforo_turno FROM sede WHERE id = ?", Integer.class, e.sB));
	}

	@Test
	void quitarElAforo_noAvisaSiNoSuperaElLimiteComun() throws Exception {
		alta(tokenE, e.tB); // 2 en el turno de B, límite común 2

		assertFalse(editarAforo(e.sB, null).get("turnosSobreAforo").asBoolean());
	}

	@Test
	void aforoMaximo_999999ValidoY1000000Rechazado() throws Exception {
		assertEquals(999_999, editarAforo(e.sB, 999_999).get("aforoTurno").asInt());

		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("nombre", jdbc.queryForObject("SELECT nombre FROM sede WHERE id = ?", String.class, e.sB));
		datos.put("aforoTurno", 1_000_000);
		leer(put("/api/sedes/" + e.sB).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(datos)),
				tokenE, 400);
		assertEquals(999_999, jdbc.queryForObject("SELECT aforo_turno FROM sede WHERE id = ?", Integer.class, e.sB));
	}

	@Test
	void subirElAforo_promocionaLaListaDeEspera() throws Exception {
		assertEquals(EN_ESPERA, alta(tokenE, e.tA));

		editarAforo(e.sA, 2);

		assertEquals(PENDIENTE, jdbc.queryForObject("""
				SELECT p.estado FROM preinscripcion p JOIN preinscripcion_turno pt ON pt.preinscripcion_id = p.id
				WHERE pt.turno_id = ?""", String.class, e.tA), "como al subir el límite común");
	}

	// ------------------------------------------------------------------ Moiskimdo

	@Test
	void moiskimdo_sinAforoSigueConElLimiteComun() throws Exception {
		Long sede = jdbc.queryForObject("SELECT MIN(id) FROM sede WHERE organizacion_id = ?", Long.class, ORG1);
		assertNull(jdbc.queryForObject("SELECT aforo_turno FROM sede WHERE id = ?", Integer.class, sede));
		String limiteOriginal = jdbc.query("SELECT valor FROM configuracion_organizacion WHERE organizacion_id = 1 AND "
				+ "clave = 'limite_turno'", rs -> rs.next() ? rs.getString(1) : null);
		GrupoConAlumnosDTO datos = new GrupoConAlumnosDTO();
		datos.setNombre("IT 9-7 Moiskimdo " + e.marca);
		datos.setDeporte("TAEKWONDO");
		datos.setSedeId(sede);
		long grupo = TenantContext.calcularCon(ORG1, () -> grupoService.crearGrupo(datos).getId());
		long turno = turno(ORG1, grupo);
		try {
			fijarLimite(ORG1, "1");
			assertEquals(1, turno(leer(get("/api/turnos/dto"), null, 200), turno).get("aforoEfectivo").asInt());
			assertEquals(PENDIENTE, alta(null, turno));
			assertEquals(EN_ESPERA, alta(null, turno));
		} finally {
			jdbc.update("DELETE FROM preinscripcion_turno WHERE turno_id = ?", turno);
			jdbc.update("DELETE FROM preinscripcion WHERE grupo_id = ?", grupo);
			jdbc.update("DELETE FROM turno WHERE id = ?", turno);
			jdbc.update("DELETE FROM grupo WHERE id = ?", grupo);
			fijarLimite(ORG1, limiteOriginal);
		}
	}

	// ------------------------------------------------------------------ utilidades

	private void aforo(long sede, int aforo) {
		jdbc.update("UPDATE sede SET aforo_turno = ? WHERE id = ?", aforo, sede);
	}

	private void fijarLimite(long org, String valor) {
		jdbc.update("DELETE FROM configuracion_organizacion WHERE organizacion_id = ? AND clave = 'limite_turno'", org);
		if (valor != null) {
			jdbc.update("INSERT INTO configuracion_organizacion (organizacion_id, clave, valor) VALUES (?, "
					+ "'limite_turno', ?)", org, valor);
		}
	}

	private long turno(long org, long grupo) {
		jdbc.update("""
				INSERT INTO turno (dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id)
				VALUES ('Martes', '18:00', '19:00', NULL, ?, ?)""", grupo, org);
		return jdbc.queryForObject("SELECT MAX(id) FROM turno WHERE organizacion_id = ?", Long.class, org);
	}

	/** La plantilla activa de Moiskimdo, copiada a la escuela sintética (un alta sin plantilla falla). */
	private void plantilla(EscuelaConSedesDePrueba escuela) {
		jdbc.update("""
				INSERT INTO plantilla_preinscripcion (version, activa, contenido, instrucciones, creada_en, organizacion_id,
				  disciplina_id)
				SELECT p.version, p.activa, p.contenido, p.instrucciones, p.creada_en, ?, ?
				FROM plantilla_preinscripcion p JOIN disciplina d ON d.id = p.disciplina_id
				WHERE p.organizacion_id = 1 AND d.codigo = 'TAEKWONDO' AND p.activa = TRUE
				ORDER BY p.version DESC LIMIT 1""", escuela.org, escuela.disciplina);
	}

	private static JsonNode turno(JsonNode lista, long id) {
		for (JsonNode turno : lista) {
			if (turno.get("id").asLong() == id) {
				return turno;
			}
		}
		throw new AssertionError("Falta el turno " + id + " en " + lista);
	}

	private JsonNode editarAforo(long sede, Integer aforo) throws Exception {
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("nombre", jdbc.queryForObject("SELECT nombre FROM sede WHERE id = ?", String.class, sede));
		datos.put("aforoTurno", aforo);
		return leer(put("/api/sedes/" + sede).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(datos)), tokenE, 200);
	}

	/** Da de alta una solicitud real y devuelve su estado. {@code token} null: visitante, en Moiskimdo. */
	private String alta(String token, long turno) throws Exception {
		int n = 40_000_000 + (++altas) + (int) (System.nanoTime() % 1_000_000);
		String dni = n + "" + "TRWAGMYFPDXBNJZSQVHLCKE".charAt(n % 23);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("deporte", "TAEKWONDO");
		body.put("turnoIds", List.of(turno));
		body.put("nombre", "Alta");
		body.put("apellidos", "Aforo Sede");
		body.put("dni", dni);
		body.put("fechaNacimiento", "1990-01-01");
		body.put("direccion", "Calle Sintética 3");
		body.put("telefono", "612345678");
		body.put("telefono2", "");
		body.put("email", "alta-9-7@example.test");
		body.put("observaciones", "");
		body.put("tutorNombre", "");
		body.put("tutorDni", "");
		body.put("tieneDiscapacidad", false);
		body.put("consentimientoFotografico", false);
		body.put("aceptacionNormas", true);
		body.put("firmanteNombre", "Firmante Aforo");
		body.put("firmaBase64", firma());
		MockHttpServletRequestBuilder peticion = post("/api/preinscripciones").contentType(MediaType.APPLICATION_JSON)
				.header("X-Forwarded-For", "10.97.0." + (altas % 250 + 1)).content(json.writeValueAsString(body));
		return leer(peticion, token, 201).get("estado").asText();
	}

	private static String firma() throws Exception {
		BufferedImage imagen = new BufferedImage(320, 100, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = imagen.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, imagen.getWidth(), imagen.getHeight());
		g.setColor(Color.BLACK);
		g.drawString("Firma de prueba de aforo", 20, 50);
		g.dispose();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(imagen, "png", bytes);
		return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
	}

	private JsonNode leer(MockHttpServletRequestBuilder peticion, String token, int esperado) throws Exception {
		if (token != null) {
			peticion.cookie(new Cookie("jwt", token));
		}
		MvcResult r = mvc.perform(peticion).andReturn();
		String contenido = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertEquals(esperado, r.getResponse().getStatus(), contenido);
		return json.readTree(contenido);
	}
}
