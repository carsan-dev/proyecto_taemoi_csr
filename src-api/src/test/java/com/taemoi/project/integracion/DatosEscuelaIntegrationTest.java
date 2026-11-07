package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.config.ValidacionRelojConfig;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza 394 contra MySQL: {@code GET} y {@code PUT /api/escuela/datos} (datos legales, contacto, firmante y día límite de
 * la mensualidad de la escuela ACTIVA), solo el propietario y el administrador de plataforma; y el día límite de cada
 * escuela en el pendiente vencido (decisiones 267 y 375). Dos escuelas sintéticas ({@link EscuelaConSedesDePrueba}) con
 * su propietario; «hoy» con un {@code Clock} simulado. Datos inventados (CIF G00000000, NIF 00000000T).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatosEscuelaIntegrationTest extends MySqlIntegrationTest {

	private static final String RUTA = "/api/escuela/datos";
	private static final String PENDIENTE = "/api/tesoreria/pendiente-vencido";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;
	@MockitoBean Clock reloj;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	long propietario, propietarioOtra, superadmin;
	String tPropietario, tPropietarioOtra, tGestor, tProfesor, tFamilia, tResponsable, tSuperadmin, tSoloPlataforma;
	String tPropietarioEnOtra, tPropietarioNueva;
	long mensualidadE, mensualidadOtra;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-394-datos");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-394-otra");
		propietario = e.cuenta(usuarios, "propietario");
		membresia(e.org, propietario, "PROPIETARIO");
		propietarioOtra = otra.cuenta(usuarios, "propietario");
		membresia(otra.org, propietarioOtra, "PROPIETARIO");
		long profesor = e.cuenta(usuarios, "profesor");
		membresia(e.org, profesor, "PROFESOR");
		// Superadmin con membresía de gestor en la escuela: entra con ROLE_MANAGER y ROLE_ADMIN_PLATAFORMA.
		superadmin = e.cuenta(usuarios, "superadmin");
		membresia(e.org, superadmin, "ADMINISTRADOR");
		long soloPlataforma = e.cuenta(usuarios, "soloplataforma");
		jdbc.update("UPDATE usuario SET es_admin_plataforma = TRUE WHERE id IN (?, ?)", superadmin, soloPlataforma);

		tPropietario = e.token(jwtService, usuarios, propietario);
		tPropietarioOtra = otra.token(jwtService, usuarios, propietarioOtra);
		tGestor = e.token(jwtService, usuarios, e.gestor);
		tProfesor = e.token(jwtService, usuarios, profesor);
		tFamilia = e.token(jwtService, usuarios, e.cuentaFamilia(usuarios, "familia"));
		tResponsable = e.token(jwtService, usuarios, e.respA);
		tSuperadmin = e.token(jwtService, usuarios, superadmin);
		// Sin membresía: su sesión solo se acepta en la escuela por defecto (Moiskimdo), con ROLE_ADMIN_PLATAFORMA.
		tSoloPlataforma = token(soloPlataforma, EscuelaConSedesDePrueba.ORG1);
		// El propietario de e con un token de la otra escuela, donde no es miembro.
		tPropietarioEnOtra = token(propietario, otra.org);

		// Escuela recién dada de alta (solo la fila, como el INSERT nativo del alta): sin datos legales.
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES (?, 'Escuela IT nueva')", "it-394-nueva-" + e.marca);
		long nueva = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = ?", Long.class,
				"it-394-nueva-" + e.marca);
		long propietarioNueva = e.cuenta(usuarios, "propietarionueva");
		membresia(nueva, propietarioNueva, "PROPIETARIO");
		tPropietarioNueva = token(propietarioNueva, nueva);

		long pMens = producto(e);
		mensualidadE = cargo(e, pMens, "MENSUALIDAD MARZO 2026 - TAEKWONDO");
		long pMensOtra = producto(otra);
		mensualidadOtra = cargo(otra, pMensOtra, "MENSUALIDAD MARZO 2026 - TAEKWONDO");
	}

	@BeforeEach
	void relojPorDefecto() {
		hoy("2026-03-06T10:00");
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertEquals(null, TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ lectura y escritura

	@Test
	void get_escuelaNueva_todoVacio_yElDia5() throws Exception {
		JsonNode datos = leer(get(RUTA), tPropietarioNueva, 200);
		for (String campo : List.of("nombreLegal", "cif", "direccion", "emailContacto", "telefono", "firmanteNombre",
				"firmanteCargo", "firmanteNif")) {
			assertTrue(datos.has(campo) && datos.get(campo).isNull(), campo + ": " + datos);
		}
		assertEquals(9, datos.size(), "solo los campos del formulario: " + datos);
		assertEquals(5, datos.get("diaLimiteMensualidad").asInt());
	}

	@Test
	void put_guardaTodo_normalizaCifYNif_yDevuelveLoGuardado() throws Exception {
		Map<String, Object> cuerpo = validos();
		cuerpo.put("nombreLegal", "  Club Deportivo IT  ");
		cuerpo.put("cif", " g00000000 ");
		cuerpo.put("firmanteNif", "00000000t");
		cuerpo.put("telefono", "  ");
		cuerpo.put("diaLimiteMensualidad", 12);
		JsonNode guardado = leer(conCuerpo(put(RUTA), cuerpo), tPropietario, 200);

		assertEquals("Club Deportivo IT", guardado.get("nombreLegal").asText());
		assertEquals("G00000000", guardado.get("cif").asText());
		assertEquals("00000000T", guardado.get("firmanteNif").asText());
		assertTrue(guardado.get("telefono").isNull(), "teléfono vacío: sin teléfono");
		assertEquals(12, guardado.get("diaLimiteMensualidad").asInt());
		assertEquals(guardado, leer(get(RUTA), tPropietario, 200), "el GET devuelve lo mismo");

		Map<String, Object> fila = jdbc.queryForMap("""
				SELECT nombre_legal, cif, direccion, email_contacto, telefono, firmante_nombre, firmante_cargo, firmante_nif,
				       dia_limite_mensualidad, nombre
				FROM organizacion WHERE id = ?""", e.org);
		assertEquals("Club Deportivo IT", fila.get("nombre_legal"));
		assertEquals("G00000000", fila.get("cif"));
		assertEquals("Calle Ejemplo 1, 41570 Casariche (Sevilla)", fila.get("direccion"));
		assertEquals("secretaria@example.test", fila.get("email_contacto"));
		assertEquals(null, fila.get("telefono"));
		assertEquals("María Ruiz Ejemplo", fila.get("firmante_nombre"));
		assertEquals("Presidenta", fila.get("firmante_cargo"));
		assertEquals("00000000T", fila.get("firmante_nif"));
		assertEquals(12, ((Number) fila.get("dia_limite_mensualidad")).intValue());
		assertEquals("Escuela IT sedes", fila.get("nombre"), "el nombre visible no se toca");

		// Vuelta al día 5 para el resto de casos.
		leer(conCuerpo(put(RUTA), validos()), tPropietario, 200);
	}

	@Test
	void put_conNieDeFirmante_yNifDePersonaComoCif_valen() throws Exception {
		Map<String, Object> cuerpo = validos();
		cuerpo.put("cif", "00000000T");
		cuerpo.put("firmanteNif", "X0000000T");
		cuerpo.put("diaLimiteMensualidad", 28);
		JsonNode guardado = leer(conCuerpo(put(RUTA), cuerpo), tPropietarioOtra, 200);
		assertEquals("00000000T", guardado.get("cif").asText());
		assertEquals("X0000000T", guardado.get("firmanteNif").asText());
		assertEquals(28, guardado.get("diaLimiteMensualidad").asInt());
		cuerpo.put("diaLimiteMensualidad", 1);
		assertEquals(1, leer(conCuerpo(put(RUTA), cuerpo), tPropietarioOtra, 200).get("diaLimiteMensualidad").asInt());
		leer(conCuerpo(put(RUTA), sinDatosLegales()), tPropietarioOtra, 200);
	}

	// ------------------------------------------------------------------ validación

	@Test
	void put_cifMal_400_yNoGuardaNada() throws Exception {
		Map<String, Object> antes = filaDe(e.org);
		for (String cif : List.of("G-0000000", "G 0000000", "G0000000", "G000000000", "I00000000", "000000000",
				"G0000000K")) {
			Map<String, Object> cuerpo = validos();
			cuerpo.put("cif", cif);
			JsonNode error = leer(conCuerpo(put(RUTA), cuerpo), tPropietario, 400);
			assertEquals("El CIF no tiene un formato válido.", error.get("errores").get("cif").asText(), cif);
			assertEquals("El CIF no tiene un formato válido.", error.get("mensaje").asText(), cif);
		}
		assertEquals(antes, filaDe(e.org));
	}

	@Test
	void put_dia0y29_400() throws Exception {
		for (Object dia : new Object[] { 0, 29, -1, 31 }) {
			Map<String, Object> cuerpo = validos();
			cuerpo.put("diaLimiteMensualidad", dia);
			JsonNode error = leer(conCuerpo(put(RUTA), cuerpo), tPropietario, 400);
			assertEquals("Escribe un día del 1 al 28.", error.get("errores").get("diaLimiteMensualidad").asText(),
					String.valueOf(dia));
		}
		Map<String, Object> cuerpo = validos();
		cuerpo.put("diaLimiteMensualidad", "diez");
		assertEquals(400, estado(conCuerpo(put(RUTA), cuerpo), tPropietario), "un día que no es un número");
		assertEquals(5, jdbc.queryForObject("SELECT dia_limite_mensualidad FROM organizacion WHERE id = ?", Integer.class,
				e.org));
	}

	@Test
	void put_sinCampos_400_conUnErrorPorCampoObligatorio() throws Exception {
		JsonNode error = leer(conCuerpo(put(RUTA), Map.of()), tPropietario, 400);
		assertEquals(Map.of(
				"nombreLegal", "El nombre legal es obligatorio.",
				"cif", "El CIF es obligatorio.",
				"direccion", "La dirección fiscal es obligatoria.",
				"emailContacto", "El email de contacto es obligatorio.",
				"firmanteNombre", "El nombre del firmante es obligatorio.",
				"firmanteCargo", "El cargo es obligatorio.",
				"firmanteNif", "El NIF del firmante es obligatorio.",
				"diaLimiteMensualidad", "Escribe un día del 1 al 28."), errores(error));
		assertEquals("El nombre legal es obligatorio.", error.get("mensaje").asText(), "el primero del formulario");

		Map<String, Object> enBlanco = validos();
		enBlanco.put("nombreLegal", "   ");
		enBlanco.put("firmanteCargo", "");
		assertEquals(Set.of("nombreLegal", "firmanteCargo"),
				errores(leer(conCuerpo(put(RUTA), enBlanco), tPropietario, 400)).keySet());

		MvcResult sinCuerpo = mvc.perform(put(RUTA).contentType(MediaType.APPLICATION_JSON)
				.cookie(new Cookie("jwt", tPropietario))).andReturn();
		assertEquals(400, sinCuerpo.getResponse().getStatus());
		assertEquals("Faltan los datos de la escuela o no son válidos.",
				json.readTree(sinCuerpo.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("mensaje").asText());
	}

	@Test
	void put_formatosYLongitudes_400() throws Exception {
		Map<String, Map.Entry<Object, String>> casos = new LinkedHashMap<>();
		casos.put("emailContacto", Map.entry("secretaria@example", "El email no tiene un formato válido."));
		casos.put("firmanteNif", Map.entry("G00000000", "El NIF no tiene un formato válido."));
		casos.put("telefono", Map.entry("600-000-000", "El teléfono solo lleva números y espacios."));
		casos.put("nombreLegal", Map.entry("x".repeat(151), "El nombre legal no puede tener más de 150 caracteres."));
		casos.put("direccion", Map.entry("x".repeat(256), "La dirección fiscal no puede tener más de 255 caracteres."));
		casos.put("firmanteNombre", Map.entry("x".repeat(101),
				"El nombre del firmante no puede tener más de 100 caracteres."));
		casos.put("firmanteCargo", Map.entry("x".repeat(61), "El cargo no puede tener más de 60 caracteres."));
		casos.put("emailContacto ", Map.entry("a".repeat(170) + "@example.test",
				"El email no puede tener más de 180 caracteres."));
		casos.put("direccion ", Map.entry("Calle Uno\nCalle Dos", "La dirección fiscal tiene caracteres no válidos."));
		for (Map.Entry<String, Map.Entry<Object, String>> caso : casos.entrySet()) {
			String campo = caso.getKey().trim();
			Map<String, Object> cuerpo = validos();
			cuerpo.put(campo, caso.getValue().getKey());
			JsonNode error = leer(conCuerpo(put(RUTA), cuerpo), tPropietario, 400);
			assertEquals(Map.of(campo, caso.getValue().getValue()), errores(error), caso.getKey());
		}
		// En el límite, vale.
		Map<String, Object> enElLimite = validos();
		enElLimite.put("nombreLegal", "x".repeat(150));
		enElLimite.put("firmanteCargo", "x".repeat(60));
		enElLimite.put("telefono", "+34 600 000 000");
		leer(conCuerpo(put(RUTA), enElLimite), tPropietario, 200);
		leer(conCuerpo(put(RUTA), validos()), tPropietario, 200);
	}

	@Test
	void put_caracteresInvisibles_400_yAcentosYOrdinales_valen() throws Exception {
		Map<String, Object> antes = filaDe(e.org);
		Map<String, Map.Entry<String, String>> casos = new LinkedHashMap<>();
		// U+202E invierte el sentido del texto; U+2028 es un separador de línea; U+200B y U+FEFF no se ven.
		casos.put("nombreLegal", Map.entry("Club ‮Deportivo", "El nombre legal tiene caracteres no válidos."));
		casos.put("direccion", Map.entry("Calle Uno Calle Dos", "La dirección fiscal tiene caracteres no válidos."));
		casos.put("firmanteNombre", Map.entry("María​Ruiz", "El nombre del firmante tiene caracteres no válidos."));
		casos.put("firmanteCargo", Map.entry("﻿Presidenta", "El cargo tiene caracteres no válidos."));
		for (Map.Entry<String, Map.Entry<String, String>> caso : casos.entrySet()) {
			Map<String, Object> cuerpo = validos();
			cuerpo.put(caso.getKey(), caso.getValue().getKey());
			JsonNode error = leer(conCuerpo(put(RUTA), cuerpo), tPropietario, 400);
			assertEquals(Map.of(caso.getKey(), caso.getValue().getValue()), errores(error), caso.getKey());
		}
		assertEquals(antes, filaDe(e.org));

		Map<String, Object> conAcentos = validos();
		conAcentos.put("direccion", "Calle Ñandú nº 3, 2ª");
		assertEquals("Calle Ñandú nº 3, 2ª",
				leer(conCuerpo(put(RUTA), conAcentos), tPropietario, 200).get("direccion").asText());
		leer(conCuerpo(put(RUTA), validos()), tPropietario, 200);
	}

	// ------------------------------------------------------------------ permisos y aislamiento

	@Test
	void soloPropietarioYAdministradorDePlataforma() throws Exception {
		for (String token : List.of(tGestor, tProfesor, tFamilia, tResponsable)) {
			assertEquals(403, estado(get(RUTA), token));
			assertEquals(403, estado(conCuerpo(put(RUTA), validos()), token));
		}
		assertEquals(200, estado(get(RUTA), tPropietario));
		assertEquals(200, estado(get(RUTA), tSuperadmin), "administrador de plataforma con membresía");
		assertEquals(200, estado(conCuerpo(put(RUTA), validos()), tSuperadmin));
		// Sin membresía, en la escuela por defecto: solo se lee aquí (no se escribe en Moiskimdo desde un test).
		JsonNode moiskimdo = leer(get(RUTA), tSoloPlataforma, 200);
		assertEquals(5, moiskimdo.get("diaLimiteMensualidad").asInt(), "Moiskimdo sigue en el día 5");
		assertEquals(5, jdbc.queryForObject("SELECT dia_limite_mensualidad FROM organizacion WHERE id = 1",
				Integer.class));
	}

	@Test
	void cadaPropietarioSoloSuEscuela() throws Exception {
		Map<String, Object> deOtra = filaDe(otra.org);
		Map<String, Object> cuerpo = validos();
		cuerpo.put("nombreLegal", "Solo de la escuela E");
		leer(conCuerpo(put(RUTA), cuerpo), tPropietario, 200);
		assertEquals(deOtra, filaDe(otra.org), "escribir en E no toca la otra");
		assertNotEquals("Solo de la escuela E", leer(get(RUTA), tPropietarioOtra, 200).get("nombreLegal").asText(""));

		// Con un token de la otra escuela, donde no es miembro, no entra.
		int lectura = estado(get(RUTA), tPropietarioEnOtra);
		int escritura = estado(conCuerpo(put(RUTA), validos()), tPropietarioEnOtra);
		assertTrue(Set.of(401, 403).contains(lectura), "lectura: " + lectura);
		assertTrue(Set.of(401, 403).contains(escritura), "escritura: " + escritura);
		assertEquals(deOtra, filaDe(otra.org));
		leer(conCuerpo(put(RUTA), validos()), tPropietario, 200);
	}

	@Test
	void put_registraUnaAccionSinDatosPersonales() throws Exception {
		long antes = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM auditoria_evento", Long.class);
		Map<String, Object> cuerpo = validos();
		cuerpo.put("diaLimiteMensualidad", 7);
		leer(conCuerpo(put(RUTA), cuerpo), tPropietario, 200);
		leer(conCuerpo(put(RUTA), cuerpo), tPropietario, 200);
		cuerpo.put("cif", "mal");
		leer(conCuerpo(put(RUTA), cuerpo), tPropietario, 400);
		leer(conCuerpo(put(RUTA), validos()), tPropietario, 200);

		List<Map<String, Object>> filas = jdbc.queryForList(
				"SELECT * FROM auditoria_evento WHERE id > ? AND organizacion_id = ? AND endpoint LIKE '/api/escuela/datos%' "
						+ "ORDER BY id", antes, e.org);
		assertEquals(4, filas.size(), filas.toString());
		assertEquals("PANEL DATOS_ESCUELA_CAMBIADOS organizacion=#" + e.org + " diaLimiteCambiado=SI sinCambios=NO",
				filas.get(0).get("resumen"));
		assertEquals("PANEL DATOS_ESCUELA_CAMBIADOS organizacion=#" + e.org + " diaLimiteCambiado=NO sinCambios=SI",
				filas.get(1).get("resumen"));
		assertEquals(400, ((Number) filas.get(2).get("estado_http")).intValue());
		assertEquals("PANEL DATOS_ESCUELA_CAMBIADOS organizacion=#" + e.org + " diaLimiteCambiado=SI sinCambios=NO",
				filas.get(3).get("resumen"));
		for (Map<String, Object> fila : filas) {
			String todo = fila.toString();
			for (String personal : List.of("00000000T", "G00000000", "María Ruiz", "secretaria@example.test",
					"Casariche")) {
				assertFalse(todo.contains(personal), personal + " en " + todo);
			}
		}
	}

	// ------------------------------------------------------------------ día límite en el pendiente vencido

	/** Escuela con el día 10: la mensualidad de marzo vence el 11, no antes; la otra escuela sigue con el 5. */
	@Test
	void pendienteVencido_usaElDiaDeCadaEscuela() throws Exception {
		Map<String, Object> cuerpo = validos();
		cuerpo.put("diaLimiteMensualidad", 10);
		leer(conCuerpo(put(RUTA), cuerpo), tPropietario, 200);
		try {
			hoy("2026-03-06T10:00");
			JsonNode dia6 = leer(get(PENDIENTE), tGestor, 200);
			assertEquals(10, dia6.get("diaLimiteMensualidad").asInt());
			assertFalse(vencidos(dia6).contains(mensualidadE), "el 6 aún no vence con el día 10");
			assertTrue(vencidos(leer(get(PENDIENTE), tPropietarioOtra, 200)).contains(mensualidadOtra),
					"la otra escuela, con el 5, sí");

			hoy("2026-03-10T23:30");
			assertFalse(vencidos(leer(get(PENDIENTE), tGestor, 200)).contains(mensualidadE), "el día 10, tampoco");

			hoy("2026-03-11T00:30");
			JsonNode dia11 = leer(get(PENDIENTE), tGestor, 200);
			assertTrue(vencidos(dia11).contains(mensualidadE), "el 11, vencida");
			assertEquals("2026-03-11", dia11.get("masAntiguos").get(0).get("vencidoDesde").asText());
		} finally {
			leer(conCuerpo(put(RUTA), validos()), tPropietario, 200);
		}
		hoy("2026-03-06T10:00");
		JsonNode deVuelta = leer(get(PENDIENTE), tGestor, 200);
		assertEquals(5, deVuelta.get("diaLimiteMensualidad").asInt(), "el cambio se aplica al momento");
		assertTrue(vencidos(deVuelta).contains(mensualidadE));
	}

	/** Escuela con el día por defecto (como Moiskimdo): vence el 6, el 5 no. */
	@Test
	void pendienteVencido_conElDiaPorDefecto_venceElDia6() throws Exception {
		hoy("2026-03-05T23:30");
		JsonNode dia5 = leer(get(PENDIENTE), tPropietarioOtra, 200);
		assertEquals(5, dia5.get("diaLimiteMensualidad").asInt());
		assertFalse(vencidos(dia5).contains(mensualidadOtra));
		hoy("2026-03-06T00:30");
		assertTrue(vencidos(leer(get(PENDIENTE), tPropietarioOtra, 200)).contains(mensualidadOtra));
	}

	// ------------------------------------------------------------------ ayudantes

	/** Datos válidos (inventados) de la escuela E, con el día 5. */
	private static Map<String, Object> validos() {
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		cuerpo.put("nombreLegal", "Club Deportivo Taekwondo IT");
		cuerpo.put("cif", "G00000000");
		cuerpo.put("direccion", "Calle Ejemplo 1, 41570 Casariche (Sevilla)");
		cuerpo.put("emailContacto", "secretaria@example.test");
		cuerpo.put("telefono", "600 000 000");
		cuerpo.put("firmanteNombre", "María Ruiz Ejemplo");
		cuerpo.put("firmanteCargo", "Presidenta");
		cuerpo.put("firmanteNif", "00000000T");
		cuerpo.put("diaLimiteMensualidad", 5);
		return cuerpo;
	}

	/** Otros datos válidos para la otra escuela (que no compartan nada con los de E). */
	private static Map<String, Object> sinDatosLegales() {
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		cuerpo.put("nombreLegal", "Otra escuela IT");
		cuerpo.put("cif", "B00000000");
		cuerpo.put("direccion", "Calle Otra 2");
		cuerpo.put("emailContacto", "otra@example.test");
		cuerpo.put("telefono", null);
		cuerpo.put("firmanteNombre", "Otro Firmante");
		cuerpo.put("firmanteCargo", "Secretario");
		cuerpo.put("firmanteNif", "11111111H");
		cuerpo.put("diaLimiteMensualidad", 5);
		return cuerpo;
	}

	private Map<String, Object> filaDe(long organizacion) {
		return jdbc.queryForMap("SELECT * FROM organizacion WHERE id = ?", organizacion);
	}

	private static Map<String, String> errores(JsonNode error) {
		Map<String, String> mapa = new LinkedHashMap<>();
		error.get("errores").fields().forEachRemaining(c -> mapa.put(c.getKey(), c.getValue().asText()));
		return mapa;
	}

	private static Set<Long> vencidos(JsonNode pendiente) {
		Set<Long> ids = new java.util.HashSet<>();
		pendiente.get("masAntiguos").forEach(c -> ids.add(c.get("productoAlumnoId").asLong()));
		return ids;
	}

	private void hoy(String ahora) {
		when(reloj.instant()).thenReturn(LocalDateTime.parse(ahora).atZone(ValidacionRelojConfig.ZONA).toInstant());
		when(reloj.getZone()).thenReturn(ValidacionRelojConfig.ZONA);
	}

	private void membresia(long organizacion, long usuario, String rol) {
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", usuario, organizacion,
				rol);
	}

	private String token(long usuarioId, long organizacion) {
		var usuario = TenantContext.calcularCon(EscuelaConSedesDePrueba.ORG1,
				() -> usuarios.findById(usuarioId).orElseThrow());
		return jwtService.generateToken(usuario, 600_000, organizacion);
	}

	private long producto(EscuelaConSedesDePrueba escuela) {
		jdbc.update("INSERT INTO producto (concepto, precio, codigo, tipo, organizacion_id) VALUES (?, 1.0, ?, ?, ?)",
				"CUOTA MENSUAL IT", "MENSUALIDAD_IT", "MENSUALIDAD", escuela.org);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = ?", Long.class, escuela.org);
	}

	private long cargo(EscuelaConSedesDePrueba escuela, long producto, String concepto) {
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, producto_id, concepto, precio, cantidad, pagado, fecha_asignacion,
				  organizacion_id, sede_id)
				VALUES (?, ?, ?, ?, 1, 0, '2026-03-01 12:00:00', ?, ?)""", escuela.aA, producto, concepto,
				new BigDecimal("30.00"), escuela.org, escuela.sA);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE organizacion_id = ?", Long.class,
				escuela.org);
	}

	private static MockHttpServletRequestBuilder conCuerpo(MockHttpServletRequestBuilder peticion, Object cuerpo)
			throws Exception {
		return peticion.contentType(MediaType.APPLICATION_JSON)
				.content(new ObjectMapper().writeValueAsString(cuerpo));
	}

	private int estado(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn().getResponse().getStatus();
	}

	private JsonNode leer(MockHttpServletRequestBuilder peticion, String token, int esperado) throws Exception {
		MvcResult r = mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
		String cuerpo = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertEquals(esperado, r.getResponse().getStatus(), cuerpo);
		return cuerpo.isBlank() ? json.nullNode() : json.readTree(cuerpo);
	}
}
