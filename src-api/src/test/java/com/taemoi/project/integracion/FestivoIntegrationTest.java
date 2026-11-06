package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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
 * Festivos por sede (decisiones 455-463) contra MySQL: alta, edición y baja, validaciones, permisos (propietario,
 * gestor, responsable con festivo propio, compartido o ajeno, profesor, otra escuela), listado de próximos y pasados con
 * {@code proximaFecha} de los anuales, impacto y auditoría. «Hoy» es el {@code Clock} simulado: el viernes 06/11/2026.
 *
 * <p>Escuela sintética ({@link EscuelaConSedesDePrueba}: sedes A y B activas, C inactiva, S activa) y otra para el
 * aislamiento. Los tests que leen listados crean su propia sede o filtran por sus ids, así que el orden no importa.
 * Datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FestivoIntegrationTest extends MySqlIntegrationTest {

	private static final Set<String> CLAVES_FESTIVO = Set.of("id", "tipo", "fechaDesde", "fechaHasta", "mes", "dia",
			"proximaFecha", "nombre", "sedes", "todasLasSedes", "editable");

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
	long propietario, profesor;
	String tGestor, tPropietario, tResp, tRespAC, tProfesor, tFamilia, tGestorOtra;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-festivo");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-festivo-otra");
		e.crearResponsablesSolos(usuarios);
		propietario = e.cuenta(usuarios, "propietario");
		membresia(propietario, "PROPIETARIO");
		profesor = e.cuenta(usuarios, "profesor");
		membresia(profesor, "PROFESOR");
		long familia = e.cuentaFamilia(usuarios, "familia");

		tGestor = e.token(jwtService, usuarios, e.gestor);
		tPropietario = e.token(jwtService, usuarios, propietario);
		tResp = e.token(jwtService, usuarios, e.respSoloA);
		tRespAC = e.token(jwtService, usuarios, e.respSoloAC);
		tProfesor = e.token(jwtService, usuarios, profesor);
		tFamilia = e.token(jwtService, usuarios, familia);
		tGestorOtra = otra.token(jwtService, usuarios, otra.gestor);
	}

	@BeforeEach
	void viernes() {
		when(reloj.instant())
				.thenReturn(LocalDateTime.parse("2026-11-06T10:00:00").atZone(ValidacionRelojConfig.ZONA).toInstant());
		when(reloj.getZone()).thenReturn(ValidacionRelojConfig.ZONA);
	}

	@AfterEach
	void sinFugas() {
		assertFalse(TenantContext.get() != null, "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ alta, edición y baja

	@Test
	void crearDia_201_conSuForma_yGuardaQuienYCuando() throws Exception {
		JsonNode creado = leer(crear(dia("2026-12-08", "  Inmaculada  ", e.sA)), tGestor, 201);
		assertEquals(CLAVES_FESTIVO, campos(creado));
		assertEquals("DIA", creado.get("tipo").asText());
		assertEquals("2026-12-08", creado.get("fechaDesde").asText());
		assertEquals("2026-12-08", creado.get("fechaHasta").asText(), "un día: desde = hasta");
		assertTrue(creado.get("mes").isNull());
		assertTrue(creado.get("dia").isNull());
		assertEquals("2026-12-08", creado.get("proximaFecha").asText());
		assertEquals("Inmaculada", creado.get("nombre").asText(), "recortado");
		assertEquals(1, creado.get("sedes").size());
		assertEquals(e.sA, creado.get("sedes").get(0).get("id").asLong());
		assertEquals(Set.of("id", "nombre"), campos(creado.get("sedes").get(0)));
		assertFalse(creado.get("sedes").get(0).get("nombre").asText().isBlank());
		assertFalse(creado.get("todasLasSedes").asBoolean());
		assertTrue(creado.get("editable").asBoolean());

		long id = creado.get("id").asLong();
		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM festivo WHERE id = ?", id);
		assertEquals(e.org, ((Number) fila.get("organizacion_id")).longValue());
		assertEquals(e.gestor, ((Number) fila.get("creado_por_usuario_id")).longValue());
		assertEquals(LocalDateTime.parse("2026-11-06T10:00:00"), fila.get("creado_en"));
		assertNull(fila.get("modificado_en"));
		assertEquals(e.org, jdbc.queryForObject("SELECT organizacion_id FROM festivo_sede WHERE festivo_id = ?", Long.class,
				id));
	}

	@Test
	void crearRangoYAnual_yNombreVacioEsNull() throws Exception {
		JsonNode rango = leer(crear(cuerpo("RANGO", "2026-12-22", "2027-01-07", null, null, "   ", e.sA, e.sB)), tGestor,
				201);
		assertEquals("2026-12-22", rango.get("fechaDesde").asText());
		assertEquals("2027-01-07", rango.get("fechaHasta").asText());
		assertEquals("2026-12-22", rango.get("proximaFecha").asText());
		assertTrue(rango.get("nombre").isNull(), "vacío tras recortar: null");
		assertEquals(List.of(e.sA, e.sB), idsDeSedes(rango));

		JsonNode anual = leer(crear(anual(12, 25, "Navidad", e.sA)), tGestor, 201);
		assertTrue(anual.get("fechaDesde").isNull());
		assertTrue(anual.get("fechaHasta").isNull());
		assertEquals(12, anual.get("mes").asInt());
		assertEquals(25, anual.get("dia").asInt());
		assertEquals("2026-12-25", anual.get("proximaFecha").asText());
		// Un periodo de 366 días justos, ambos incluidos, vale.
		assertEquals(201, estado(crear(cuerpo("RANGO", "2026-01-01", "2027-01-01", null, null, null, e.sA)), tGestor));
	}

	/** Lo que no corresponde al tipo se ignora: un DIA con mes y día, o un ANUAL con fechas. */
	@Test
	void camposDeOtroTipo_seIgnoran() throws Exception {
		JsonNode diaConMes = leer(crear(cuerpo("DIA", "2026-12-08", null, 5, 3, null, e.sA)), tGestor, 201);
		assertTrue(diaConMes.get("mes").isNull());
		assertTrue(diaConMes.get("dia").isNull());
		JsonNode anualConFechas = leer(crear(cuerpo("ANUAL", "2026-12-08", "2026-12-09", 1, 6, null, e.sA)), tGestor, 201);
		assertTrue(anualConFechas.get("fechaDesde").isNull());
		assertTrue(anualConFechas.get("fechaHasta").isNull());
	}

	/** ANUAL: la próxima aparición desde hoy, incluido; si ya pasó este año, la del siguiente (cruce de año). */
	@Test
	void proximaFechaDeUnAnual_incluyeHoy_yCruzaDeAnio() throws Exception {
		assertEquals("2026-11-06", leer(crear(anual(11, 6, null, e.sA)), tGestor, 201).get("proximaFecha").asText());
		assertEquals("2027-11-05", leer(crear(anual(11, 5, null, e.sA)), tGestor, 201).get("proximaFecha").asText());
		assertEquals("2027-01-01", leer(crear(anual(1, 1, null, e.sA)), tGestor, 201).get("proximaFecha").asText());
		assertEquals("2026-12-31", leer(crear(anual(12, 31, null, e.sA)), tGestor, 201).get("proximaFecha").asText());
	}

	@Test
	void editar_reemplazaFormaYSedes_yMarcaLaModificacion() throws Exception {
		long id = leer(crear(dia("2026-12-08", "Puente", e.sA, e.sB)), tGestor, 201).get("id").asLong();
		when(reloj.instant())
				.thenReturn(LocalDateTime.parse("2026-11-07T09:30:00").atZone(ValidacionRelojConfig.ZONA).toInstant());

		JsonNode editado = leer(editar(id, cuerpo("RANGO", "2026-12-07", "2026-12-08", null, null, "Puente largo", e.sB,
				e.sSin)), tPropietario, 200);
		assertEquals(id, editado.get("id").asLong());
		assertEquals("RANGO", editado.get("tipo").asText());
		assertEquals("2026-12-07", editado.get("fechaDesde").asText());
		assertEquals("Puente largo", editado.get("nombre").asText());
		assertEquals(List.of(e.sB, e.sSin), idsDeSedes(editado));
		assertEquals(List.of(e.sB, e.sSin), jdbc.queryForList(
				"SELECT sede_id FROM festivo_sede WHERE festivo_id = ? ORDER BY sede_id", Long.class, id));
		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM festivo WHERE id = ?", id);
		assertEquals(propietario, ((Number) fila.get("modificado_por_usuario_id")).longValue());
		assertEquals(LocalDateTime.parse("2026-11-07T09:30:00"), fila.get("modificado_en"));
		assertEquals(e.gestor, ((Number) fila.get("creado_por_usuario_id")).longValue(), "quien lo creó sigue");

		// El mismo cuerpo otra vez: nada cambia.
		assertEquals(editado, leer(editar(id, cuerpo("RANGO", "2026-12-07", "2026-12-08", null, null, "Puente largo",
				e.sB, e.sSin)), tPropietario, 200));
	}

	@Test
	void borrar_204_seLlevaSusSedes_yDespues404() throws Exception {
		long id = leer(crear(dia("2026-12-08", null, e.sA, e.sB)), tGestor, 201).get("id").asLong();
		assertEquals(204, estado(delete("/api/festivos/" + id), tGestor));
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM festivo WHERE id = ?", Long.class, id));
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM festivo_sede WHERE festivo_id = ?", Long.class, id));
		MvcResult otraVez = peticion(delete("/api/festivos/" + id), tGestor);
		assertEquals(404, otraVez.getResponse().getStatus());
		assertEquals("El festivo no existe.", mensaje(otraVez));
		assertEquals(404, estado(editar(id, dia("2026-12-08", null, e.sA)), tGestor));
		assertEquals(404, estado(delete("/api/festivos/999999999"), tGestor));
	}

	/** Cubre todas las sedes ACTIVAS de la escuela (A, B y S; C está inactiva). */
	@Test
	void todasLasSedes_esCubrirLasActivas() throws Exception {
		long nueva = nuevaSede("todas");
		try {
			assertFalse(leer(crear(dia("2026-12-08", null, e.sA, e.sB, e.sSin)), tGestor, 201).get("todasLasSedes")
					.asBoolean(), "falta la nueva");
			assertTrue(leer(crear(dia("2026-12-08", null, e.sA, e.sB, e.sSin, nueva)), tGestor, 201).get("todasLasSedes")
					.asBoolean());
		} finally {
			jdbc.update("UPDATE sede SET activa = FALSE WHERE id = ?", nueva);
		}
	}

	// ------------------------------------------------------------------ validaciones

	@Test
	void validaciones_400_conSuMensaje_sinGuardarNada() throws Exception {
		long antes = festivosDeLaEscuela();
		Map<String, String> casos = new LinkedHashMap<>();
		casos.put("{\"sedeIds\":[" + e.sA + "]}", "Elige si el festivo es un día, un periodo o cada año.");
		casos.put(cuerpo("OTRO", "2026-12-08", null, null, null, null, e.sA), "Elige si el festivo es un día, un periodo o cada año.");
		casos.put(cuerpo("dia", "2026-12-08", null, null, null, null, e.sA), "Elige si el festivo es un día, un periodo o cada año.");
		casos.put(cuerpo("DIA", null, null, null, null, null, e.sA), "Indica la fecha.");
		casos.put(cuerpo("DIA", "2026-02-30", null, null, null, null, e.sA), "La fecha no es válida.");
		casos.put(cuerpo("DIA", "08/12/2026", null, null, null, null, e.sA), "La fecha no es válida.");
		casos.put(cuerpo("DIA", "1800-12-08", null, null, null, null, e.sA), "La fecha no es válida.");
		casos.put(cuerpo("DIA", "2026-12-08", "2026-12-09", null, null, null, e.sA),
				"Un festivo de un día empieza y acaba el mismo día.");
		casos.put(cuerpo("RANGO", "2026-12-08", null, null, null, null, e.sA), "Indica la fecha de inicio y la de fin.");
		casos.put(cuerpo("RANGO", "2026-12-08", "2026-12-08", null, null, null, e.sA),
				"La fecha de fin debe ser posterior a la de inicio.");
		casos.put(cuerpo("RANGO", "2026-12-09", "2026-12-08", null, null, null, e.sA),
				"La fecha de fin debe ser posterior a la de inicio.");
		casos.put(cuerpo("RANGO", "2026-01-01", "2027-01-02", null, null, null, e.sA),
				"Un periodo no puede pasar de 366 días.");
		casos.put(cuerpo("ANUAL", null, null, 12, null, null, e.sA), "Indica el día y el mes.");
		casos.put(cuerpo("ANUAL", null, null, 13, 1, null, e.sA), "El mes no es válido.");
		casos.put(cuerpo("ANUAL", null, null, 0, 1, null, e.sA), "El mes no es válido.");
		casos.put(cuerpo("ANUAL", null, null, 2, 29, null, e.sA),
				"Elige otro día: el 29 de febrero no existe todos los años.");
		casos.put(cuerpo("ANUAL", null, null, 2, 30, null, e.sA), "Ese día no existe en ese mes.");
		casos.put(cuerpo("ANUAL", null, null, 4, 31, null, e.sA), "Ese día no existe en ese mes.");
		casos.put(cuerpo("ANUAL", null, null, 4, 0, null, e.sA), "Ese día no existe en ese mes.");
		casos.put(cuerpo("DIA", "2026-12-08", null, null, null, "x".repeat(61), e.sA),
				"El nombre no puede pasar de 60 caracteres.");
		casos.put(cuerpo("DIA", "2026-12-08", null, null, null, null), "Elige al menos una sede.");
		casos.put("{\"tipo\":\"DIA\",\"fechaDesde\":\"2026-12-08\"}", "Elige al menos una sede.");
		casos.put("{\"tipo\":\"DIA\",\"fechaDesde\":\"2026-12-08\",\"sedeIds\":[null]}", "Elige al menos una sede.");
		for (Map.Entry<String, String> caso : casos.entrySet()) {
			MvcResult alta = peticion(crear(caso.getKey()), tGestor);
			assertEquals(400, alta.getResponse().getStatus(), caso.getKey());
			assertEquals(caso.getValue(), mensaje(alta), caso.getKey());
			MvcResult impacto = peticion(impacto(caso.getKey()), tGestor);
			assertEquals(400, impacto.getResponse().getStatus(), "impacto " + caso.getKey());
		}
		long id = leer(crear(dia("2026-12-08", null, e.sA)), tGestor, 201).get("id").asLong();
		for (String cuerpo : casos.keySet()) {
			assertEquals(400, estado(editar(id, cuerpo), tGestor), "PUT " + cuerpo);
		}
		assertEquals(400, estado(post("/api/festivos").contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":"),
				tGestor), "JSON roto");
		assertEquals(antes + 1, festivosDeLaEscuela(), "solo el del PUT");
		// 60 caracteres justos y sedes repetidas: vale, una fila por sede.
		JsonNode largo = leer(crear(cuerpo("DIA", "2026-12-08", null, null, null, "x".repeat(60), e.sA, e.sA)), tGestor, 201);
		assertEquals(List.of(e.sA), idsDeSedes(largo));
	}

	// ------------------------------------------------------------------ permisos

	@Test
	void profesorFamiliaYAnonimo_403_oSinSesion401() throws Exception {
		long id = leer(crear(dia("2026-12-08", null, e.sA)), tGestor, 201).get("id").asLong();
		for (String token : List.of(tProfesor, tFamilia)) {
			assertEquals(403, estado(get("/api/festivos"), token));
			assertEquals(403, estado(head("/api/festivos"), token));
			assertEquals(403, estado(crear(dia("2026-12-08", null, e.sA)), token));
			assertEquals(403, estado(editar(id, dia("2026-12-09", null, e.sA)), token));
			assertEquals(403, estado(delete("/api/festivos/" + id), token));
			assertEquals(403, estado(impacto(dia("2026-12-08", null, e.sA)), token));
		}
		assertEquals(401, mvc.perform(get("/api/festivos")).andReturn().getResponse().getStatus());
		assertEquals("2026-12-08", jdbc.queryForObject("SELECT fecha_desde FROM festivo WHERE id = ?", String.class, id));
	}

	@Test
	void propietarioYGestor_todasLasSedes() throws Exception {
		JsonNode delPropietario = leer(crear(dia("2026-12-08", null, e.sB, e.sC)), tPropietario, 201);
		assertTrue(delPropietario.get("editable").asBoolean());
		assertEquals(List.of(e.sB, e.sC), idsDeSedes(delPropietario), "también una sede inactiva");
		assertEquals(200, estado(editar(delPropietario.get("id").asLong(), dia("2026-12-09", null, e.sA)), tGestor));
	}

	/** Pieza de la matriz: el responsable crea solo con sedes suyas y su impacto también se limita a ellas. */
	@Test
	void responsable_creaSoloEnSusSedes() throws Exception {
		long antes = festivosDeLaEscuela();
		JsonNode suyo = leer(crear(dia("2026-12-08", null, e.sA)), tResp, 201);
		assertTrue(suyo.get("editable").asBoolean());
		assertEquals(e.respSoloA, jdbc.queryForObject("SELECT creado_por_usuario_id FROM festivo WHERE id = ?", Long.class,
				suyo.get("id").asLong()));
		assertEquals(403, estado(crear(dia("2026-12-08", null, e.sA, e.sB)), tResp), "una sede ajena");
		assertEquals(403, estado(crear(dia("2026-12-08", null, e.sB)), tResp));
		assertEquals(403, estado(crear(dia("2026-12-08", null, otra.sA)), tResp), "de otra escuela");
		assertEquals(403, estado(crear(dia("2026-12-08", null, 999999999L)), tResp), "inexistente");
		assertEquals(403, estado(impacto(dia("2026-12-08", null, e.sB)), tResp));
		assertEquals(200, estado(impacto(dia("2026-12-08", null, e.sA)), tResp));
		assertEquals(201, estado(crear(dia("2026-12-08", null, e.sA, e.sC)), tRespAC), "sus dos sedes");
		assertEquals(antes + 2, festivosDeLaEscuela());
	}

	/** Pieza de la matriz: ve los que tocan alguna de sus sedes; cambia o quita solo los que son enteros suyos. */
	@Test
	void responsable_veLosQueTocanSusSedes_yCambiaSoloLosSuyos() throws Exception {
		long propio = leer(crear(dia("2027-03-01", "Propio", e.sA)), tGestor, 201).get("id").asLong();
		long compartido = leer(crear(dia("2027-03-02", "Compartido", e.sA, e.sB)), tGestor, 201).get("id").asLong();
		long ajeno = leer(crear(dia("2027-03-03", "Ajeno", e.sB)), tGestor, 201).get("id").asLong();

		Map<Long, JsonNode> visibles = porId(leer(get("/api/festivos"), tResp, 200));
		assertTrue(visibles.get(propio).get("editable").asBoolean());
		assertFalse(visibles.get(compartido).get("editable").asBoolean(), "lo ve, no lo cambia");
		assertEquals(List.of(e.sA, e.sB), idsDeSedes(visibles.get(compartido)), "con todas sus sedes");
		assertFalse(visibles.containsKey(ajeno));
		assertEquals(200, estado(head("/api/festivos"), tResp));

		assertEquals(403, estado(editar(compartido, dia("2027-03-02", null, e.sA)), tResp));
		assertEquals(403, estado(delete("/api/festivos/" + compartido), tResp));
		assertEquals(403, estado(editar(ajeno, dia("2027-03-03", null, e.sA)), tResp));
		assertEquals(403, estado(delete("/api/festivos/" + ajeno), tResp));
		assertEquals(403, estado(editar(propio, dia("2027-03-01", null, e.sA, e.sB)), tResp), "no lo amplía a otra sede");
		assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM festivo_sede WHERE festivo_id = ?", Long.class,
				compartido));

		assertEquals(200, estado(editar(propio, dia("2027-03-04", "Propio movido", e.sA)), tResp));
		assertEquals(204, estado(delete("/api/festivos/" + propio), tResp));
		// Filtro por sede: la suya, sí; una ajena, 403; una de otra escuela, 400.
		assertTrue(porId(leer(get("/api/festivos?sedeId=" + e.sA), tResp, 200)).containsKey(compartido));
		assertEquals(403, estado(get("/api/festivos?sedeId=" + e.sB), tResp));
		assertEquals(400, estado(get("/api/festivos?sedeId=" + otra.sA), tResp));
	}

	@Test
	void otraEscuela_404_yNoLosVe() throws Exception {
		long id = leer(crear(dia("2026-12-08", "De la e", e.sA)), tGestor, 201).get("id").asLong();
		assertFalse(porId(leer(get("/api/festivos"), tGestorOtra, 200)).containsKey(id));
		MvcResult editar = peticion(editar(id, dia("2026-12-08", null, otra.sA)), tGestorOtra);
		assertEquals(404, editar.getResponse().getStatus());
		assertEquals(404, estado(delete("/api/festivos/" + id), tGestorOtra));
		assertEquals(403, estado(crear(dia("2026-12-08", null, e.sA)), tGestorOtra), "sede de otra escuela");
		assertEquals(200, estado(impacto(cuerpoConExcluir(id, e.sA)), tGestor), "el excluido de la propia escuela existe");
		assertEquals(404, estado(impacto(cuerpoConExcluir(id, otra.sA)), tGestorOtra), "excluido de otra escuela");
		assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM festivo WHERE id = ?", Long.class, id));
	}

	// ------------------------------------------------------------------ listado

	@Test
	void listado_proximosYAnuales_oPasados_porProximaFecha() throws Exception {
		long sede = nuevaSede("listado");
		try {
			long pasado = leer(crear(dia("2026-10-12", "Pasado", sede)), tGestor, 201).get("id").asLong();
			long ayerAcabado = leer(crear(cuerpo("RANGO", "2026-11-01", "2026-11-05", null, null, null, sede)), tGestor, 201)
					.get("id").asLong();
			long enCurso = leer(crear(cuerpo("RANGO", "2026-11-01", "2026-11-06", null, null, null, sede)), tGestor, 201)
					.get("id").asLong();
			long navidad = leer(crear(anual(12, 25, null, sede)), tGestor, 201).get("id").asLong();
			long anoNuevo = leer(crear(anual(1, 1, null, sede)), tGestor, 201).get("id").asLong();
			long diciembre = leer(crear(dia("2026-12-08", null, sede)), tGestor, 201).get("id").asLong();

			JsonNode proximos = leer(get("/api/festivos?sedeId=" + sede), tGestor, 200);
			assertEquals(List.of(enCurso, diciembre, navidad, anoNuevo), ids(proximos));
			assertEquals(CLAVES_FESTIVO, campos(proximos.get(0)));
			assertEquals(proximos, leer(get("/api/festivos?sedeId=" + sede + "&pasados=false"), tGestor, 200));

			JsonNode pasados = leer(get("/api/festivos?sedeId=" + sede + "&pasados=true"), tGestor, 200);
			assertEquals(List.of(pasado, ayerAcabado), ids(pasados), "sin los anuales");

			assertTrue(porId(leer(get("/api/festivos"), tGestor, 200)).containsKey(enCurso), "sin filtro");
			assertEquals(400, estado(get("/api/festivos?sedeId=" + otra.sA), tGestor), "sede de otra escuela");
		} finally {
			jdbc.update("UPDATE sede SET activa = FALSE WHERE id = ?", sede);
		}
	}

	// ------------------------------------------------------------------ impacto

	/**
	 * Grupo con turnos de lunes y martes en una sede nueva. Del 9 al 16 de noviembre: lunes 9 y 16, martes 10 → 3 clases;
	 * el lunes 9 tiene lista guardada. Otro festivo el martes 10: esa clase ya es festiva y no cuenta, salvo que sea el
	 * excluido.
	 */
	@Test
	void impacto_cuentaClasesYConLista_sinLasYaFestivas() throws Exception {
		long sede = nuevaSede("impacto");
		try {
			long grupo = grupo(sede);
			long lunes = turno(grupo, "Lunes", "18:00");
			turno(grupo, "Martes", "17:00");
			turno(grupo, "Festivo", "09:00"); // Día desconocido: no cuenta, como en pasar lista.
			jdbc.update("""
					INSERT INTO clase_asistencia (organizacion_id, grupo_id, turno_id, fecha, hora_inicio, hora_fin, registrado_en)
					VALUES (?, ?, ?, '2026-11-09', '18:00', '19:00', '2026-11-09 19:00:00')""", e.org, grupo, lunes);
			String periodo = cuerpo("RANGO", "2026-11-09", "2026-11-16", null, null, null, sede);

			JsonNode r = leer(impacto(periodo), tGestor, 200);
			assertEquals(Set.of("clases", "conLista", "conListaDetalle", "clasesDetalle"), campos(r));
			List<String> afectadas = new ArrayList<>();
			r.get("clasesDetalle").forEach(c -> afectadas.add(c.get("fecha").asText() + " " + c.get("horaInicio").asText()
					+ "-" + c.get("horaFin").asText() + " " + c.get("conLista").asBoolean() + " "
					+ c.get("presentes").asText() + "/" + c.get("total").asText()));
			assertEquals(List.of("2026-11-09 18:00-23:00 true 0/0", "2026-11-10 17:00-23:00 false null/null",
					"2026-11-16 18:00-23:00 false null/null"), afectadas, "por fecha y hora");
			assertEquals(Set.of("sedeId", "sede", "fecha", "horaInicio", "horaFin", "grupo", "conLista", "presentes",
					"total"), campos(r.get("clasesDetalle").get(0)));
			assertEquals(sede, r.get("clasesDetalle").get(0).get("sedeId").asLong());
			assertTrue(r.get("clasesDetalle").get(0).get("grupo").asText().startsWith("IT festivo"));
			assertEquals(3, r.get("clases").asInt());
			assertEquals(1, r.get("conLista").asInt());
			JsonNode detalle = r.get("conListaDetalle").get(0);
			assertEquals(Set.of("sedeId", "sede", "fecha", "horaInicio", "grupo"), campos(detalle));
			assertEquals(sede, detalle.get("sedeId").asLong());
			assertEquals("2026-11-09", detalle.get("fecha").asText());
			assertEquals("18:00", detalle.get("horaInicio").asText());
			assertTrue(detalle.get("grupo").asText().startsWith("IT festivo"));
			assertTrue(detalle.get("sede").asText().contains("impacto"));

			long martes = leer(crear(dia("2026-11-10", null, sede)), tGestor, 201).get("id").asLong();
			assertEquals(2, leer(impacto(periodo), tGestor, 200).get("clases").asInt(), "el martes ya es festivo");
			String excluyendo = periodo.replace("{", "{\"excluirFestivoId\":" + martes + ",");
			assertEquals(3, leer(impacto(excluyendo), tGestor, 200).get("clases").asInt(), "salvo el que se edita");

			// ANUAL: solo su próxima aparición (lunes 09/11/2026).
			JsonNode anual = leer(impacto(anual(11, 9, null, sede)), tGestor, 200);
			assertEquals(1, anual.get("clases").asInt());
			assertEquals(1, anual.get("conLista").asInt());
			// Otra sede sin grupos: nada.
			assertEquals(0, leer(impacto(dia("2026-11-09", null, e.sSin)), tGestor, 200).get("clases").asInt());
			assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM festivo f JOIN festivo_sede fs ON fs.festivo_id = f.id"
					+ " WHERE fs.sede_id = ? AND f.id <> ?", Long.class, sede, martes), "el impacto no guarda nada");
		} finally {
			jdbc.update("UPDATE sede SET activa = FALSE WHERE id = ?", sede);
		}
	}

	// ------------------------------------------------------------------ auditoría

	@Test
	void lasEscrituras_quedanEnLaAuditoria() throws Exception {
		long id = leer(crear(dia("2027-05-01", "Auditado", e.sA)), tGestor, 201).get("id").asLong();
		leer(editar(id, dia("2027-05-02", "Auditado", e.sA)), tGestor, 200);
		assertEquals(204, estado(delete("/api/festivos/" + id), tGestor));
		assertEquals(400, estado(crear(dia("2027-05-01", "x".repeat(61), e.sA)), tGestor));

		assertTrue(auditadas("POST", "/api/festivos", 201) >= 1);
		assertTrue(auditadas("POST", "/api/festivos", 400) >= 1);
		assertEquals(1L, auditadas("PUT", "/api/festivos/" + id, 200));
		assertEquals(1L, auditadas("DELETE", "/api/festivos/" + id, 204));
		assertEquals(List.of("UPDATE"), jdbc.queryForList(
				"SELECT accion FROM auditoria_evento WHERE metodo_http = 'PUT' AND endpoint = ?", String.class,
				"/api/festivos/" + id));
	}

	// ------------------------------------------------------------------ utilidades

	private String dia(String fecha, String nombre, long... sedes) {
		return cuerpo("DIA", fecha, null, null, null, nombre, sedes);
	}

	private String anual(int mes, int dia, String nombre, long... sedes) {
		return cuerpo("ANUAL", null, null, mes, dia, nombre, sedes);
	}

	private String cuerpoConExcluir(long excluir, long sede) {
		return dia("2026-12-08", null, sede).replace("{", "{\"excluirFestivoId\":" + excluir + ",");
	}

	private String cuerpo(String tipo, String desde, String hasta, Integer mes, Integer dia, String nombre,
			long... sedes) {
		Map<String, Object> c = new LinkedHashMap<>();
		c.put("tipo", tipo);
		c.put("fechaDesde", desde);
		c.put("fechaHasta", hasta);
		c.put("mes", mes);
		c.put("dia", dia);
		c.put("nombre", nombre);
		List<Long> ids = new ArrayList<>();
		for (long s : sedes) {
			ids.add(s);
		}
		c.put("sedeIds", ids);
		try {
			return json.writeValueAsString(c);
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static MockHttpServletRequestBuilder crear(String cuerpo) {
		return post("/api/festivos").contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private static MockHttpServletRequestBuilder editar(long id, String cuerpo) {
		return put("/api/festivos/" + id).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private static MockHttpServletRequestBuilder impacto(String cuerpo) {
		return post("/api/festivos/impacto").contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private long nuevaSede(String nombre) {
		String n = "IT festivo " + nombre + " " + System.nanoTime();
		jdbc.update("INSERT INTO sede (organizacion_id, nombre, activa) VALUES (?, ?, TRUE)", e.org, n);
		return jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = ? AND nombre = ?", Long.class, e.org, n);
	}

	private long grupo(long sede) {
		return TenantContext.calcularCon(e.org, () -> {
			GrupoConAlumnosDTO dto = new GrupoConAlumnosDTO();
			dto.setNombre("IT festivo " + System.nanoTime());
			dto.setDeporte("TAEKWONDO");
			dto.setSedeId(sede);
			return grupoService.crearGrupo(dto).getId();
		});
	}

	private long turno(long grupo, String dia, String inicio) {
		jdbc.update("""
				INSERT INTO turno (dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id)
				VALUES (?, ?, '23:00', NULL, ?, ?)""", dia, inicio, grupo, e.org);
		return jdbc.queryForObject("SELECT MAX(id) FROM turno WHERE organizacion_id = ?", Long.class, e.org);
	}

	private void membresia(long usuario, String rol) {
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", usuario, e.org, rol);
	}

	private long festivosDeLaEscuela() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM festivo WHERE organizacion_id = ?", Long.class, e.org);
	}

	private long auditadas(String metodo, String ruta, int estado) {
		return jdbc.queryForObject(
				"SELECT COUNT(*) FROM auditoria_evento WHERE metodo_http = ? AND endpoint = ? AND estado_http = ?",
				Long.class, metodo, ruta, estado);
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

	private String mensaje(MvcResult r) throws Exception {
		return json.readTree(r.getResponse().getContentAsString()).get("mensaje").asText();
	}

	private static List<Long> idsDeSedes(JsonNode festivo) {
		List<Long> ids = new ArrayList<>();
		festivo.get("sedes").forEach(s -> ids.add(s.get("id").asLong()));
		return ids;
	}

	private static List<Long> ids(JsonNode festivos) {
		List<Long> ids = new ArrayList<>();
		festivos.forEach(f -> ids.add(f.get("id").asLong()));
		return ids;
	}

	private static Map<Long, JsonNode> porId(JsonNode festivos) {
		Map<Long, JsonNode> porId = new LinkedHashMap<>();
		festivos.forEach(f -> porId.put(f.get("id").asLong(), f));
		return porId;
	}

	private static Set<String> campos(JsonNode nodo) {
		Set<String> campos = new TreeSet<>();
		for (Iterator<String> it = nodo.fieldNames(); it.hasNext();) {
			campos.add(it.next());
		}
		return campos;
	}
}
