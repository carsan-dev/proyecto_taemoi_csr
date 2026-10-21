package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.ArrayList;
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
 * Pieza 8b-I contra MySQL (fase3-e8b-plan.md §3 y §6, puntos de revisión 2 y 3): el interruptor del diseño nuevo por
 * escuela y usuario.
 * <ul>
 * <li>Solo el propietario da o quita el permiso, y solo a la gestión de su escuela (PROPIETARIO o ADMINISTRADOR
 * activa): gestor, 403; familia o profesor sin gestión, 400; de otra escuela o inexistente, 404. Al darlo, queda
 * activo.</li>
 * <li>Cada uno con permiso lo enciende o apaga para sí ({@code /api/panel/mi-diseno}); sin permiso, 403.</li>
 * <li>Regla efectiva: con fila y permitido, su activo; si no, el valor por defecto de la escuela.</li>
 * <li>Llega al frontend en {@code GET /api/auth/permisos} ({@code disenoNuevo {activo, permitido}}); apagado por
 * defecto.</li>
 * <li>Una fila del registro por escritura (módulo {@code panel}), solo con ids y sí/no.</li>
 * </ul>
 * Tres escuelas sintéticas ({@link EscuelaConSedesDePrueba}): A y B para el cruce de escuelas y C solo para el valor por
 * defecto (así ningún caso depende del orden). Todo lo que se cambia lo crea el propio caso. Datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DisenoPanelIntegrationTest extends MySqlIntegrationTest {

	private static final String RUTA = "/api/panel/diseno-nuevo";
	private static final String MI_DISENO = "/api/panel/mi-diseno";
	private static final String PERMISOS = "/api/auth/permisos";
	/** Resumen del registro explícito: módulo, acción y solo ids o sí/no. */
	private static final String RESUMEN_PANEL = "^PANEL DISENO_(PERMITIR|QUITAR|ACTIVAR|DESACTIVAR|POR_DEFECTO)"
			+ "( [a-zA-Z]+=(#\\d+|SI|NO))+$";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba a;
	EscuelaConSedesDePrueba b;
	EscuelaConSedesDePrueba c;
	long propietarioA, propietarioB, propietarioC, gestorC, familia, profesor, ambas, soloB;
	String tPropietarioA, tGestorA, tFamilia, tProfesor, tPropietarioB, tAmbasEnA, tAmbasEnB, tPropietarioC, tGestorC;
	final List<Long> cuentasCreadas = new ArrayList<>();

	@BeforeAll
	void preparar() {
		a = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-8b-i-a");
		b = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-8b-i-b");
		c = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-8b-i-c");

		propietarioA = a.cuenta(usuarios, "propietario");
		membresia(a, propietarioA, "PROPIETARIO");
		familia = a.cuentaFamilia(usuarios, "familia");
		profesor = a.cuenta(usuarios, "profesor");
		membresia(a, profesor, "PROFESOR");
		ambas = a.cuenta(usuarios, "ambas");
		membresia(a, ambas, "ADMINISTRADOR");
		membresia(b, ambas, "ADMINISTRADOR");
		propietarioB = b.cuenta(usuarios, "propietario");
		membresia(b, propietarioB, "PROPIETARIO");
		soloB = b.cuenta(usuarios, "solob");
		membresia(b, soloB, "ADMINISTRADOR");
		propietarioC = c.cuenta(usuarios, "propietario");
		membresia(c, propietarioC, "PROPIETARIO");
		gestorC = c.gestor;

		tPropietarioA = a.token(jwtService, usuarios, propietarioA);
		tGestorA = a.token(jwtService, usuarios, a.gestor);
		tFamilia = a.token(jwtService, usuarios, familia);
		tProfesor = a.token(jwtService, usuarios, profesor);
		tAmbasEnA = a.token(jwtService, usuarios, ambas);
		tAmbasEnB = b.token(jwtService, usuarios, ambas);
		tPropietarioB = b.token(jwtService, usuarios, propietarioB);
		tPropietarioC = c.token(jwtService, usuarios, propietarioC);
		tGestorC = c.token(jwtService, usuarios, gestorC);
	}

	@AfterAll
	void retirar() {
		// Las escuelas son de prueba; aun así, la escuela C vuelve a su valor por defecto de siempre.
		jdbc.update("DELETE FROM configuracion_organizacion WHERE organizacion_id = ? AND clave = ?", c.org,
				"panel.diseno_por_defecto");
	}

	@AfterEach
	void sinFugas() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ asignación del propietario

	@Test
	void propietarioPermiteYQuitaAGestor() throws Exception {
		long gestor = gestorNuevo(a);
		JsonNode dado = leer(permiso(gestor, true), tPropietarioA, 200);
		assertEquals(gestor, dado.get("usuarioId").asLong());
		assertTrue(dado.get("permitido").asBoolean());
		assertTrue(dado.get("activo").asBoolean());
		JsonNode fila = deLaLista(leer(get(RUTA), tPropietarioA, 200), gestor);
		assertTrue(fila.get("permitido").asBoolean());
		assertTrue(fila.get("activo").asBoolean());

		JsonNode quitado = leer(permiso(gestor, false), tPropietarioA, 200);
		assertFalse(quitado.get("permitido").asBoolean());
		assertFalse(quitado.get("activo").asBoolean(), "sin permiso, el por defecto de la escuela (VIEJO)");
		fila = deLaLista(leer(get(RUTA), tPropietarioA, 200), gestor);
		assertFalse(fila.get("permitido").asBoolean());
		assertFalse(fila.get("activo").asBoolean());
	}

	@Test
	void listaLaGestionDeLaEscuela_conPorDefectoYSinNombres() throws Exception {
		long gestor = gestorNuevo(a);
		JsonNode lista = leer(get(RUTA), tPropietarioA, 200);
		assertFalse(lista.get("porDefecto").asBoolean(), "sin la clave, VIEJO");
		List<Long> ids = new ArrayList<>();
		lista.get("usuarios").forEach(u -> {
			ids.add(u.get("usuarioId").asLong());
			assertEquals(List.of("usuarioId", "permitido", "activo"), campos(u), "solo ids y booleanos");
		});
		assertTrue(ids.containsAll(List.of(propietarioA, a.gestor, ambas, gestor)), ids.toString());
		assertFalse(ids.contains(familia), "la familia no es gestión");
		assertFalse(ids.contains(profesor), "el profesor sin gestión no sale");
		assertFalse(ids.contains(soloB), "nunca de otra escuela");
		assertEquals(ids.stream().sorted().toList(), ids, "por id");
	}

	@Test
	void permitirLoDejaActivo() throws Exception {
		long gestor = gestorNuevo(a);
		String token = a.token(jwtService, usuarios, gestor);
		JsonNode antes = leer(get(PERMISOS), token, 200).get("disenoNuevo");
		assertFalse(antes.get("activo").asBoolean());
		assertFalse(antes.get("permitido").asBoolean());

		leer(permiso(gestor, true), tPropietarioA, 200);
		JsonNode despues = leer(get(PERMISOS), token, 200).get("disenoNuevo");
		assertTrue(despues.get("activo").asBoolean());
		assertTrue(despues.get("permitido").asBoolean());
	}

	@Test
	void propietarioSePermiteASiMismo() throws Exception {
		long propio = a.cuenta(usuarios, "propio-" + sufijo());
		membresia(a, propio, "PROPIETARIO");
		String token = a.token(jwtService, usuarios, propio);
		leer(permiso(propio, true), token, 200);
		assertTrue(leer(get(PERMISOS), token, 200).get("disenoNuevo").get("activo").asBoolean());
	}

	@Test
	void disenoNuevo_soloPropietarioAsigna() throws Exception {
		long gestor = gestorNuevo(a);
		assertEquals(403, estado(permiso(gestor, true), tGestorA));
		assertEquals(403, estado(permiso(a.gestor, true), tGestorA), "tampoco a sí mismo");
		assertEquals(403, estado(get(RUTA), tGestorA));
		assertEquals(403, estado(porDefecto(true), tGestorA));
		for (String token : List.of(tFamilia, tProfesor)) {
			assertEquals(403, estado(permiso(gestor, true), token));
			assertEquals(403, estado(get(RUTA), token));
			assertEquals(403, estado(porDefecto(true), token));
			assertEquals(403, estado(miDiseno(true), token));
		}
		assertEquals(0, filas(gestor));
	}

	@Test
	void disenoNuevo_soloGestionDeLaEscuela() throws Exception {
		assertEquals(400, estado(permiso(familia, true), tPropietarioA), "familia sin gestión");
		assertEquals(400, estado(permiso(profesor, true), tPropietarioA), "profesor sin gestión");
		long antiguo = gestorNuevo(a);
		jdbc.update("UPDATE membresia SET activa = FALSE WHERE usuario_id = ? AND organizacion_id = ?", antiguo, a.org);
		assertEquals(400, estado(permiso(antiguo, true), tPropietarioA), "gestión desactivada");
		MvcResult r = mvc.perform(permiso(familia, true).cookie(new Cookie("jwt", tPropietarioA))).andReturn();
		assertFalse(json.readTree(r.getResponse().getContentAsString()).get("mensaje").asText().isBlank());
		assertEquals(0, filas(familia) + filas(profesor) + filas(antiguo));
		assertEquals(400, estado(conCuerpo(put(RUTA + "/" + a.gestor), "{}"), tPropietarioA), "sin permitido");
	}

	@Test
	void disenoNuevo_noCruzaEscuelas() throws Exception {
		assertEquals(404, estado(permiso(soloB, true), tPropietarioA), "gestor solo de B");
		assertEquals(404, estado(permiso(999_999_999L, true), tPropietarioA), "inexistente");
		assertEquals(0, filas(soloB));

		// La cuenta de las dos escuelas: activo en A no lo activa en B.
		leer(permiso(ambas, true), tPropietarioA, 200);
		try {
			assertTrue(leer(get(PERMISOS), tAmbasEnA, 200).get("disenoNuevo").get("activo").asBoolean());
			JsonNode enB = leer(get(PERMISOS), tAmbasEnB, 200).get("disenoNuevo");
			assertFalse(enB.get("activo").asBoolean());
			assertFalse(enB.get("permitido").asBoolean());
			JsonNode filaB = deLaLista(leer(get(RUTA), tPropietarioB, 200), ambas);
			assertFalse(filaB.get("permitido").asBoolean(), "B no ve el permiso de A");
			assertEquals(403, estado(miDiseno(true), tAmbasEnB), "en B no tiene permiso");

			// Y quitarlo en B no lo toca en A.
			leer(permiso(ambas, false), tPropietarioB, 200);
			assertTrue(leer(get(PERMISOS), tAmbasEnA, 200).get("disenoNuevo").get("activo").asBoolean());
			assertEquals(1, jdbc.queryForObject("""
					SELECT COUNT(*) FROM diseno_panel_usuario WHERE usuario_id = ? AND organizacion_id = ?
					  AND permitido = TRUE AND activo = TRUE""", Integer.class, ambas, a.org));
		} finally {
			leer(permiso(ambas, false), tPropietarioA, 200);
		}
	}

	// ------------------------------------------------------------------ el propio usuario

	@Test
	void miDiseno_sinPermiso_403() throws Exception {
		long gestor = gestorNuevo(a);
		String token = a.token(jwtService, usuarios, gestor);
		MvcResult r = mvc.perform(miDiseno(true).cookie(new Cookie("jwt", token))).andReturn();
		assertEquals(403, r.getResponse().getStatus());
		assertFalse(json.readTree(r.getResponse().getContentAsString()).get("mensaje").asText().isBlank());
		assertEquals(0, filas(gestor));
		// Con el permiso quitado, tampoco.
		leer(permiso(gestor, true), tPropietarioA, 200);
		leer(permiso(gestor, false), tPropietarioA, 200);
		assertEquals(403, estado(miDiseno(true), token));
	}

	@Test
	void miDiseno_apagaYEnciendeSoloElPropio() throws Exception {
		long gestor = gestorNuevo(a);
		long otro = gestorNuevo(a);
		String token = a.token(jwtService, usuarios, gestor);
		leer(permiso(gestor, true), tPropietarioA, 200);
		leer(permiso(otro, true), tPropietarioA, 200);

		JsonNode apagado = leer(miDiseno(false), token, 200);
		assertFalse(apagado.get("activo").asBoolean());
		assertTrue(apagado.get("permitido").asBoolean());
		JsonNode propio = leer(get(PERMISOS), token, 200).get("disenoNuevo");
		assertFalse(propio.get("activo").asBoolean());
		assertTrue(propio.get("permitido").asBoolean(), "sigue pudiendo volver a encenderlo");
		assertTrue(deLaLista(leer(get(RUTA), tPropietarioA, 200), otro).get("activo").asBoolean(),
				"el de otro no cambia");

		assertTrue(leer(miDiseno(true), token, 200).get("activo").asBoolean());
		assertTrue(leer(get(PERMISOS), token, 200).get("disenoNuevo").get("activo").asBoolean());
		assertEquals(400, estado(conCuerpo(put(MI_DISENO), "{}"), token), "sin activo");
	}

	// ------------------------------------------------------------------ valor por defecto (escuela C)

	@Test
	void porDefectoNuevo_sinFilaActivo() throws Exception {
		long gestor = gestorNuevo(c);
		String token = c.token(jwtService, usuarios, gestor);
		try {
			assertTrue(leer(porDefecto(true), tPropietarioC, 200).get("porDefecto").asBoolean());
			assertTrue(leer(get(RUTA), tPropietarioC, 200).get("porDefecto").asBoolean());
			JsonNode estado = leer(get(PERMISOS), token, 200).get("disenoNuevo");
			assertTrue(estado.get("activo").asBoolean());
			assertTrue(estado.get("permitido").asBoolean(), "con NUEVO puede volver al anterior para sí");
			assertEquals(0, filas(gestor));
			// Volver al anterior para sí deja su fila, que manda sobre el por defecto.
			assertFalse(leer(miDiseno(false), token, 200).get("activo").asBoolean());
			assertFalse(leer(get(PERMISOS), token, 200).get("disenoNuevo").get("activo").asBoolean());
			// La escuela A no se entera.
			assertFalse(leer(get(RUTA), tPropietarioA, 200).get("porDefecto").asBoolean());
		} finally {
			leer(porDefecto(false), tPropietarioC, 200);
		}
		assertFalse(leer(get(PERMISOS), tGestorC, 200).get("disenoNuevo").get("activo").asBoolean());
	}

	@Test
	void porDefectoNuevo_filaApagadaManda() throws Exception {
		long gestor = gestorNuevo(c);
		String token = c.token(jwtService, usuarios, gestor);
		leer(permiso(gestor, true), tPropietarioC, 200);
		leer(miDiseno(false), token, 200);
		try {
			leer(porDefecto(true), tPropietarioC, 200);
			assertFalse(leer(get(PERMISOS), token, 200).get("disenoNuevo").get("activo").asBoolean(),
					"su fila apagada manda");
			// Sin permiso, cuenta el por defecto.
			leer(permiso(gestor, false), tPropietarioC, 200);
			assertTrue(leer(get(PERMISOS), token, 200).get("disenoNuevo").get("activo").asBoolean());
		} finally {
			leer(porDefecto(false), tPropietarioC, 200);
		}
	}

	// ------------------------------------------------------------------ registro y permisos

	@Test
	void registroSoloConIds() throws Exception {
		long gestor = gestorNuevo(a);
		String token = a.token(jwtService, usuarios, gestor);
		String email = jdbc.queryForObject("SELECT email FROM usuario WHERE id = ?", String.class, gestor);

		registraUna(permiso(gestor, true), tPropietarioA, propietarioA,
				"PANEL DISENO_PERMITIR usuario=#" + gestor, email);
		registraUna(permiso(gestor, true), tPropietarioA, propietarioA,
				"PANEL DISENO_PERMITIR usuario=#" + gestor + " sinCambios=SI", email);
		registraUna(miDiseno(false), token, gestor, "PANEL DISENO_DESACTIVAR usuario=#" + gestor, email);
		registraUna(miDiseno(true), token, gestor, "PANEL DISENO_ACTIVAR usuario=#" + gestor, email);
		registraUna(permiso(gestor, false), tPropietarioA, propietarioA,
				"PANEL DISENO_QUITAR usuario=#" + gestor, email);
		try {
			registraUna(porDefecto(true), tPropietarioC, propietarioC, "PANEL DISENO_POR_DEFECTO nuevo=SI", email);
			registraUna(porDefecto(true), tPropietarioC, propietarioC,
					"PANEL DISENO_POR_DEFECTO nuevo=SI sinCambios=SI", email);
		} finally {
			registraUna(porDefecto(false), tPropietarioC, propietarioC, "PANEL DISENO_POR_DEFECTO nuevo=NO", email);
		}

		// Un 4xx deja solo la fila genérica del filtro, como siempre.
		long antes = ultimoEvento();
		assertEquals(400, estado(permiso(familia, true), tPropietarioA));
		List<Map<String, Object>> errores = eventosDesde(antes);
		assertEquals(1, errores.size(), errores.toString());
		assertEquals(400, ((Number) errores.get(0).get("estado_http")).intValue());
		assertFalse(String.valueOf(errores.get(0).get("resumen")).startsWith("PANEL "));
	}

	@Test
	void permisos_incluyeDisenoNuevo() throws Exception {
		for (String token : List.of(tPropietarioA, tGestorA, tFamilia, tProfesor, tPropietarioB)) {
			JsonNode p = leer(get(PERMISOS), token, 200);
			JsonNode diseno = p.get("disenoNuevo");
			assertEquals(List.of("activo", "permitido"), campos(diseno));
			assertFalse(diseno.get("activo").asBoolean(), "apagado por defecto");
			assertFalse(diseno.get("permitido").asBoolean());
		}
	}

	// ------------------------------------------------------------------ utilidades

	private void registraUna(MockHttpServletRequestBuilder peticion, String token, long actor, String resumen,
			String emailDelDestino) throws Exception {
		long antes = ultimoEvento();
		leer(peticion, token, 200);
		List<Map<String, Object>> filas = eventosDesde(antes);
		assertEquals(1, filas.size(), "una fila por escritura: " + filas);
		Map<String, Object> fila = filas.get(0);
		String visto = (String) fila.get("resumen");
		assertEquals(resumen, visto);
		assertTrue(visto.matches(RESUMEN_PANEL), visto);
		assertEquals("panel", fila.get("modulo"));
		assertEquals("UPDATE", fila.get("accion"));
		assertEquals(actor, ((Number) fila.get("usuario_id")).longValue());
		assertEquals(200, ((Number) fila.get("estado_http")).intValue());
		assertNull(fila.get("payload_json"), "sin cuerpo");
		// La columna del actor (usuario_email) la rellena el registro de siempre; el contenido de la acción, nunca.
		for (String columna : List.of("resumen", "endpoint", "payload_json", "query_params_json")) {
			assertFalse(String.valueOf(fila.get(columna)).contains(emailDelDestino), "nunca el email: " + columna);
		}
		assertFalse(visto.contains("Usuario") || visto.contains("Prueba"), "nunca el nombre");
	}

	private long gestorNuevo(EscuelaConSedesDePrueba x) {
		long id = x.cuenta(usuarios, "gestor-" + sufijo());
		membresia(x, id, "ADMINISTRADOR");
		cuentasCreadas.add(id);
		return id;
	}

	private MockHttpServletRequestBuilder permiso(long usuario, boolean permitido) {
		return conCuerpo(put(RUTA + "/" + usuario), "{\"permitido\":" + permitido + "}");
	}

	private MockHttpServletRequestBuilder porDefecto(boolean nuevo) {
		return conCuerpo(put(RUTA + "/por-defecto"), "{\"nuevo\":" + nuevo + "}");
	}

	private MockHttpServletRequestBuilder miDiseno(boolean activo) {
		return conCuerpo(put(MI_DISENO), "{\"activo\":" + activo + "}");
	}

	private static MockHttpServletRequestBuilder conCuerpo(MockHttpServletRequestBuilder peticion, String cuerpo) {
		return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private JsonNode leer(MockHttpServletRequestBuilder peticion, String token, int esperado) throws Exception {
		MvcResult r = mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
		assertEquals(esperado, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		String cuerpo = r.getResponse().getContentAsString();
		return cuerpo.isEmpty() ? null : json.readTree(cuerpo);
	}

	private int estado(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn().getResponse().getStatus();
	}

	private static JsonNode deLaLista(JsonNode lista, long usuario) {
		for (JsonNode u : lista.get("usuarios")) {
			if (u.get("usuarioId").asLong() == usuario) {
				return u;
			}
		}
		throw new AssertionError("La cuenta " + usuario + " no está en la lista: " + lista);
	}

	private static List<String> campos(JsonNode nodo) {
		List<String> campos = new ArrayList<>();
		nodo.fieldNames().forEachRemaining(campos::add);
		return campos;
	}

	private int filas(long usuario) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM diseno_panel_usuario WHERE usuario_id = ?", Integer.class,
				usuario);
	}

	private long ultimoEvento() {
		return jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM auditoria_evento", Long.class);
	}

	/** Las filas posteriores de las escuelas de este test (otros tests pueden escribir en otras escuelas). */
	private List<Map<String, Object>> eventosDesde(long id) {
		return jdbc.queryForList("""
				SELECT * FROM auditoria_evento WHERE id > ? AND organizacion_id IN (?, ?, ?) ORDER BY id""", id, a.org,
				b.org, c.org);
	}

	private void membresia(EscuelaConSedesDePrueba x, long usuario, String rol) {
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", usuario, x.org, rol);
	}

	private static String sufijo() {
		return UUID.randomUUID().toString().substring(0, 6);
	}
}
