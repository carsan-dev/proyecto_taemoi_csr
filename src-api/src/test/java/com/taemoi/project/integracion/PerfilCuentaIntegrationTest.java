package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
 * Decisión 322 contra MySQL, con la cadena de filtros completa: {@code GET} y {@code PUT /api/panel/mi-perfil} solo para
 * P y G y solo sobre la cuenta de la sesión; la ficha manda en la escuela activa y no cruza escuelas; el cambio se ve en
 * el siguiente {@code GET /api/auth/permisos}; y ni el registro ni el JSON de la entidad sacan el nombre ni la fecha. Dos
 * escuelas sintéticas ({@link EscuelaConSedesDePrueba}). Datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PerfilCuentaIntegrationTest extends MySqlIntegrationTest {

	private static final String RUTA = "/api/panel/mi-perfil";
	private static final String PERMISOS = "/api/auth/permisos";
	private static final Set<String> CLAVES = Set.of("nombrePreferido", "nombreBase", "cumpleDia", "cumpleMes",
			"cumpleFicha");

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;
	@Autowired Clock reloj;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	String tGestor, tFamilia, tSoloProf, tResponsable;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-322");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-322-otra");
		tGestor = e.token(jwtService, usuarios, e.gestor);
		tFamilia = e.token(jwtService, usuarios, e.cuentaFamilia(usuarios, "familia"));
		long soloProf = e.cuenta(usuarios, "soloprof");
		membresia(e, soloProf, "PROFESOR");
		tSoloProf = e.token(jwtService, usuarios, soloProf);
		tResponsable = e.token(jwtService, usuarios, e.respA);
	}

	@AfterEach
	void sinFugas() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ quién entra

	@Test
	void propietarioYGestor_200_conTodasLasClaves() throws Exception {
		long propietario = cuentaCon(e, "PROPIETARIO");
		JsonNode p = leer(get(RUTA), token(e, propietario), 200);
		assertEquals(CLAVES, campos(p));
		assertTrue(p.get("nombrePreferido").isNull(), p.toString());
		assertEquals("Usuario P.", p.get("nombreBase").asText());
		assertTrue(p.get("cumpleDia").isNull(), p.toString());
		assertTrue(p.get("cumpleMes").isNull(), p.toString());
		assertTrue(p.get("cumpleFicha").isNull(), p.toString());
		assertEquals(200, estado(head(RUTA), token(e, propietario)));

		long gestor = cuentaCon(e, "ADMINISTRADOR");
		assertEquals(CLAVES, campos(leer(get(RUTA), token(e, gestor), 200)));
		JsonNode guardado = guardar(token(e, gestor), "{\"nombrePreferido\":\"Luci\",\"cumpleDia\":14,\"cumpleMes\":3}");
		assertEquals(CLAVES, campos(guardado));
		assertEquals("Luci", guardado.get("nombrePreferido").asText());
		assertEquals("Usuario P.", guardado.get("nombreBase").asText(), "la base no lleva el preferido");
		assertEquals(14, guardado.get("cumpleDia").asInt());
		assertEquals(3, guardado.get("cumpleMes").asInt());
		assertEquals(guardado, leer(get(RUTA), token(e, gestor), 200), "el PUT devuelve lo mismo que el GET");
	}

	@Test
	void soloProfesorFamiliaYResponsable_403() throws Exception {
		for (String token : List.of(tSoloProf, tFamilia, tResponsable)) {
			assertEquals(403, estado(get(RUTA), token));
			assertEquals(403, estado(cuerpo("{\"nombrePreferido\":\"Luci\"}"), token));
		}
	}

	@Test
	void sinSesion_401() throws Exception {
		assertEquals(401, mvc.perform(get(RUTA)).andReturn().getResponse().getStatus());
		assertEquals(401, mvc.perform(cuerpo("{}")).andReturn().getResponse().getStatus());
		assertEquals(401, mvc.perform(get(RUTA).cookie(new Cookie("jwt", "no-es-un-token"))).andReturn().getResponse()
				.getStatus());
	}

	// ------------------------------------------------------------------ solo la cuenta de la sesión

	@Test
	void unaCuentaNoCambiaAOtra_aunqueMandeSuIdEnElCuerpo() throws Exception {
		long una = cuentaCon(e, "ADMINISTRADOR");
		long otraCuenta = cuentaCon(e, "PROPIETARIO");
		guardar(token(e, otraCuenta), "{\"nombrePreferido\":\"Sin tocar\",\"cumpleDia\":2,\"cumpleMes\":5}");

		guardar(token(e, una), json.writeValueAsString(Map.of("id", otraCuenta, "usuarioId", otraCuenta,
				"nombrePreferido", "Luci", "cumpleDia", 14, "cumpleMes", 3)));

		assertEquals("Luci", columna(una, "nombre_preferido"));
		assertEquals("Sin tocar", columna(otraCuenta, "nombre_preferido"));
		assertEquals("2", columna(otraCuenta, "cumple_dia"));
		assertEquals("5", columna(otraCuenta, "cumple_mes"));
		assertEquals("Sin tocar", leer(get(RUTA), token(e, otraCuenta), 200).get("nombrePreferido").asText());
	}

	// ------------------------------------------------------------------ la ficha

	@Test
	void conFichaEnLaEscuelaActiva_cumpleFicha_yElCuerpoSeIgnora() throws Exception {
		long cuenta = cuentaCon(e, "ADMINISTRADOR");
		ficha(e, cuenta, "PROPIO", LocalDate.of(1990, 3, 14));
		JsonNode p = guardar(token(e, cuenta), "{\"nombrePreferido\":\"Luci\",\"cumpleDia\":20,\"cumpleMes\":7}");
		assertEquals(14, p.get("cumpleFicha").get("dia").asInt());
		assertEquals(3, p.get("cumpleFicha").get("mes").asInt());
		assertEquals(Set.of("dia", "mes"), campos(p.get("cumpleFicha")));
		assertTrue(p.get("cumpleDia").isNull(), p.toString());
		assertTrue(p.get("cumpleMes").isNull(), p.toString());
		assertNull(columna(cuenta, "cumple_dia"));
		assertEquals("Luci", columna(cuenta, "nombre_preferido"), "el nombre sí se guarda");
		// Ignorado también si fuera imposible: no es un error.
		guardar(token(e, cuenta), "{\"nombrePreferido\":\"Luci\",\"cumpleDia\":31,\"cumpleMes\":4}");
	}

	/** Invariante multiescuela: la misma cuenta, con ficha en una escuela y sin ella en la otra. */
	@Test
	void fichaDeOtraEscuela_noCuenta_yLoGuardadoSobreviveEnLaEscuelaConFicha() throws Exception {
		long cuenta = cuentaCon(e, "ADMINISTRADOR");
		membresia(otra, cuenta, "PROPIETARIO");
		ficha(e, cuenta, "PROPIO", LocalDate.of(1990, 3, 14));

		JsonNode enOtra = guardar(token(otra, cuenta), "{\"cumpleDia\":20,\"cumpleMes\":7}");
		assertTrue(enOtra.get("cumpleFicha").isNull(), "la ficha es de la otra escuela: " + enOtra);
		assertEquals(20, enOtra.get("cumpleDia").asInt());

		JsonNode enE = guardar(token(e, cuenta), "{\"cumpleDia\":1,\"cumpleMes\":1}");
		assertEquals(14, enE.get("cumpleFicha").get("dia").asInt(), enE.toString());
		assertEquals(20, enE.get("cumpleDia").asInt(), "lo guardado no se toca");
		assertEquals("20", columna(cuenta, "cumple_dia"));
		assertEquals("7", columna(cuenta, "cumple_mes"));

		JsonNode vuelta = leer(get(RUTA), token(otra, cuenta), 200);
		assertTrue(vuelta.get("cumpleFicha").isNull());
		assertEquals(20, vuelta.get("cumpleDia").asInt());
		assertEquals(7, vuelta.get("cumpleMes").asInt());
	}

	/** Sin fecha no se puede probar aquí ({@code alumno.fecha_nacimiento} es NOT NULL): está en PerfilCuentaServiceTest. */
	@Test
	void fichaDeTutorOAnonimizada_noCuentan() throws Exception {
		long tutor = cuentaCon(e, "ADMINISTRADOR");
		ficha(e, tutor, "TUTOR", LocalDate.of(1990, 3, 14));
		assertTrue(leer(get(RUTA), token(e, tutor), 200).get("cumpleFicha").isNull());

		long anonimizada = cuentaCon(e, "ADMINISTRADOR");
		long alumno = ficha(e, anonimizada, "PROPIO", LocalDate.of(1990, 3, 14));
		jdbc.update("UPDATE alumno SET anonimizado_en = NOW() WHERE id = ?", alumno);
		assertTrue(leer(get(RUTA), token(e, anonimizada), 200).get("cumpleFicha").isNull());
	}

	@Test
	void conVariasFichas_laDeMenorId() throws Exception {
		long cuenta = cuentaCon(e, "ADMINISTRADOR");
		ficha(e, cuenta, "PROPIO", LocalDate.of(1990, 3, 14));
		ficha(e, cuenta, "PROPIO", LocalDate.of(1991, 7, 2));
		JsonNode p = leer(get(RUTA), token(e, cuenta), 200);
		assertEquals(14, p.get("cumpleFicha").get("dia").asInt());
		assertEquals(3, p.get("cumpleFicha").get("mes").asInt());
	}

	// ------------------------------------------------------------------ validación

	@Test
	void errores_400ConMensaje_yNoCambianNada() throws Exception {
		long cuenta = cuentaCon(e, "ADMINISTRADOR");
		String t = token(e, cuenta);
		guardar(t, "{\"nombrePreferido\":\"Luci\",\"cumpleDia\":14,\"cumpleMes\":3}");
		assertEquals("Esa fecha no existe.", error("{\"nombrePreferido\":\"Otro\",\"cumpleDia\":31,\"cumpleMes\":4}", t));
		assertEquals("Elige día y mes, o deja los dos vacíos.", error("{\"nombrePreferido\":\"Otro\",\"cumpleDia\":14}", t));
		assertEquals("El nombre no puede tener más de 40 caracteres.",
				error("{\"nombrePreferido\":\"" + "a".repeat(41) + "\"}", t));
		assertEquals("El nombre tiene caracteres no válidos.", error("{\"nombrePreferido\":\"Lu\\u0000ci\"}", t));
		assertEquals(400, estado(put(RUTA), t), "sin cuerpo: no borra lo guardado");
		assertEquals(400, estado(cuerpo("{\"cumpleDia\":\"catorce\"}"), t));
		assertEquals(400, estado(cuerpo("{ no es json"), t));
		assertEquals("Luci", columna(cuenta, "nombre_preferido"));
		assertEquals("14", columna(cuenta, "cumple_dia"));
		assertEquals("3", columna(cuenta, "cumple_mes"));
	}

	@Test
	void veintinueveDeFebrero_seGuarda() throws Exception {
		long cuenta = cuentaCon(e, "ADMINISTRADOR");
		assertEquals(29, guardar(token(e, cuenta), "{\"cumpleDia\":29,\"cumpleMes\":2}").get("cumpleDia").asInt());
	}

	/** Contra la columna real: 40 caracteres de cuatro bytes caben y vuelven iguales. */
	@Test
	void cuarentaEmojis_viajanEnteros() throws Exception {
		long cuenta = cuentaCon(e, "ADMINISTRADOR");
		String cuarenta = "😀".repeat(40);
		JsonNode p = guardar(token(e, cuenta), json.writeValueAsString(Map.of("nombrePreferido", cuarenta)));
		assertEquals(cuarenta, p.get("nombrePreferido").asText());
		assertEquals(cuarenta, columna(cuenta, "nombre_preferido"));
	}

	@Test
	void vaciarElNombre_loDejaEnNull() throws Exception {
		long cuenta = cuentaCon(e, "ADMINISTRADOR");
		guardar(token(e, cuenta), "{\"nombrePreferido\":\"Luci\"}");
		assertTrue(guardar(token(e, cuenta), "{\"nombrePreferido\":\"   \"}").get("nombrePreferido").isNull());
		assertNull(columna(cuenta, "nombre_preferido"));
	}

	// ------------------------------------------------------------------ la sesión

	/** Sin caché: el principal se carga en cada petición, así que el cambio se ve en el siguiente GET. */
	@Test
	void elCambioSeVeEnElSiguienteGetDePermisos() throws Exception {
		long cuenta = cuentaCon(e, "ADMINISTRADOR");
		String t = token(e, cuenta);
		JsonNode antes = leer(get(PERMISOS), t, 200);
		assertEquals("Usuario P.", antes.get("nombreVisible").asText());
		assertEquals("Usuario", antes.get("nombreSaludo").asText());

		guardar(t, "{\"nombrePreferido\":\"Maestra Luci\"}");
		JsonNode despues = leer(get(PERMISOS), t, 200);
		assertEquals("Maestra Luci", despues.get("nombreVisible").asText());
		assertEquals("Maestra Luci", despues.get("nombreSaludo").asText());

		guardar(t, "{\"nombrePreferido\":null}");
		JsonNode vuelta = leer(get(PERMISOS), t, 200);
		assertEquals("Usuario P.", vuelta.get("nombreVisible").asText());
		assertEquals("Usuario", vuelta.get("nombreSaludo").asText());
	}

	@Test
	void cumpleanos_lleganDeLaCuenta_siNoHayFicha() throws Exception {
		long cuenta = cuentaCon(e, "ADMINISTRADOR");
		String t = token(e, cuenta);
		assertFalse(leer(get(PERMISOS), t, 200).get("cumpleanos").asBoolean());
		LocalDate hoy = LocalDate.now(reloj);
		guardar(t, cumple(hoy));
		assertTrue(leer(get(PERMISOS), t, 200).get("cumpleanos").asBoolean());
		guardar(t, cumple(hoy.plusDays(1)));
		assertFalse(leer(get(PERMISOS), t, 200).get("cumpleanos").asBoolean());
	}

	@Test
	void cumpleanos_laFichaConFechaDecideSola() throws Exception {
		long cuenta = cuentaCon(e, "ADMINISTRADOR");
		membresia(otra, cuenta, "PROPIETARIO");
		LocalDate hoy = LocalDate.now(reloj);
		// Lo guarda desde la escuela sin ficha; en la escuela con ficha (que no cumple hoy) no cuenta.
		guardar(token(otra, cuenta), cumple(hoy));
		ficha(e, cuenta, "PROPIO", hoy.minusYears(8).plusDays(1));
		assertTrue(leer(get(PERMISOS), token(otra, cuenta), 200).get("cumpleanos").asBoolean());
		assertFalse(leer(get(PERMISOS), token(e, cuenta), 200).get("cumpleanos").asBoolean());
	}

	// ------------------------------------------------------------------ datos personales

	@Test
	void elRegistro_nuncaGuardaElNombreNiLaFecha() throws Exception {
		String nombre = "Nombre" + UUID.randomUUID().toString().substring(0, 8);
		long cuenta = cuentaCon(e, "ADMINISTRADOR");
		String t = token(e, cuenta);
		long antes = ultimoEvento();

		String correcto = "{\"nombrePreferido\":\"" + nombre + "\",\"cumpleDia\":17,\"cumpleMes\":11}";
		guardar(t, correcto);
		List<Map<String, Object>> filas = eventosDesde(antes);
		assertEquals(1, filas.size(), filas.toString());
		assertEquals("PANEL PERFIL_CAMBIADO usuario=#" + cuenta, filas.get(0).get("resumen"));
		assertEquals(RUTA, filas.get(0).get("endpoint"));
		assertEquals("panel", filas.get(0).get("modulo"));
		assertNull(filas.get(0).get("payload_json"), "sin cuerpo");

		antes = ultimoEvento();
		guardar(t, correcto);
		assertEquals("PANEL PERFIL_CAMBIADO usuario=#" + cuenta + " sinCambios=SI",
				eventosDesde(antes).get(0).get("resumen"));

		antes = ultimoEvento();
		assertEquals(400, estado(cuerpo("{\"nombrePreferido\":\"" + nombre + "\",\"cumpleDia\":31,\"cumpleMes\":4}"), t));
		assertEquals(400, estado(cuerpo("{ \"nombrePreferido\":\"" + nombre), t));
		assertEquals(403, estado(cuerpo("{\"nombrePreferido\":\"" + nombre + "\"}"), tSoloProf));
		List<Map<String, Object>> fallidas = eventosDesde(antes);
		assertEquals(3, fallidas.size(), fallidas.toString());
		for (Map<String, Object> fila : fallidas) {
			assertFalse(String.valueOf(fila).contains(nombre), "ni rastro del nombre: " + fila);
		}
		assertTrue(String.valueOf(fallidas.get(0).get("payload_json")).contains("\"cumpleDia\":\"[redactado]\""),
				fallidas.get(0).toString());
		assertFalse(String.valueOf(fallidas.get(0).get("payload_json")).contains("31"), fallidas.get(0).toString());
		assertEquals(0L, jdbc.queryForObject("""
				SELECT COUNT(*) FROM auditoria_evento
				WHERE resumen LIKE ? OR payload_json LIKE ? OR query_params_json LIKE ?""", Long.class, "%" + nombre + "%",
				"%" + nombre + "%", "%" + nombre + "%"));
	}

	/** {@code Alumno#getUsuario} saca la entidad en el JSON de alumno: el perfil de esa cuenta no puede ir dentro. */
	@Test
	void elJsonDeAlumno_noLlevaElPerfilDeSuCuenta() throws Exception {
		String nombre = "Nombre" + UUID.randomUUID().toString().substring(0, 8);
		long vinculada = cuentaCon(e, "ADMINISTRADOR");
		long alumno = e.alumno("E322VINCULADO" + e.marca, null, true);
		jdbc.update("UPDATE usuario SET alumno_id = ? WHERE id = ?", alumno, vinculada);
		guardar(token(e, vinculada), "{\"nombrePreferido\":\"" + nombre + "\",\"cumpleDia\":17,\"cumpleMes\":11}");

		long propietario = cuentaCon(e, "PROPIETARIO");
		MvcResult r = mvc.perform(get("/api/admin/" + alumno).cookie(new Cookie("jwt", token(e, propietario))))
				.andReturn();
		String cuerpo = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertEquals(200, r.getResponse().getStatus(), cuerpo);
		JsonNode usuario = json.readTree(cuerpo).get("usuario");
		assertTrue(usuario != null && usuario.has("email"), "la entidad sale en el JSON de alumno: " + cuerpo);
		assertFalse(usuario.has("nombrePreferido"), cuerpo);
		assertFalse(usuario.has("cumpleDia"), cuerpo);
		assertFalse(usuario.has("cumpleMes"), cuerpo);
		assertFalse(cuerpo.contains(nombre), cuerpo);
	}

	// ------------------------------------------------------------------ utilidades

	private long cuentaCon(EscuelaConSedesDePrueba escuela, String rol) {
		long cuenta = escuela.cuenta(usuarios, "c" + UUID.randomUUID().toString().substring(0, 8));
		membresia(escuela, cuenta, rol);
		return cuenta;
	}

	private String token(EscuelaConSedesDePrueba escuela, long cuenta) {
		return escuela.token(jwtService, usuarios, cuenta);
	}

	private void membresia(EscuelaConSedesDePrueba escuela, long usuario, String rol) {
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", usuario, escuela.org, rol);
	}

	private long ficha(EscuelaConSedesDePrueba escuela, long cuenta, String relacion, LocalDate nacimiento) {
		long alumno = escuela.alumno("E322" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), null, true);
		jdbc.update("UPDATE alumno SET fecha_nacimiento = ? WHERE id = ?", java.sql.Date.valueOf(nacimiento), alumno);
		jdbc.update("""
				INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen)
				VALUES (?, ?, ?, ?, 'MANUAL')""", cuenta, alumno, escuela.org, relacion);
		return alumno;
	}

	private static String cumple(LocalDate fecha) {
		return "{\"cumpleDia\":" + fecha.getDayOfMonth() + ",\"cumpleMes\":" + fecha.getMonthValue() + "}";
	}

	private String columna(long cuenta, String nombre) {
		return jdbc.queryForObject("SELECT " + nombre + " FROM usuario WHERE id = ?", String.class, cuenta);
	}

	private JsonNode guardar(String token, String cuerpo) throws Exception {
		return leer(cuerpo(cuerpo), token, 200);
	}

	private String error(String cuerpo, String token) throws Exception {
		return leer(cuerpo(cuerpo), token, 400).get("mensaje").asText();
	}

	private static MockHttpServletRequestBuilder cuerpo(String cuerpo) {
		return put(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo.getBytes(StandardCharsets.UTF_8));
	}

	private JsonNode leer(MockHttpServletRequestBuilder peticion, String token, int esperado) throws Exception {
		MvcResult r = mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
		String cuerpo = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertEquals(esperado, r.getResponse().getStatus(), cuerpo);
		return json.readTree(cuerpo);
	}

	private int estado(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn().getResponse().getStatus();
	}

	private long ultimoEvento() {
		return jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM auditoria_evento", Long.class);
	}

	/** Filas de auditoría de la escuela de prueba posteriores a {@code id} con la ruta del perfil, en orden. */
	private List<Map<String, Object>> eventosDesde(long id) {
		return jdbc.queryForList("SELECT * FROM auditoria_evento WHERE id > ? AND organizacion_id = ? AND endpoint = ? "
				+ "ORDER BY id", id, e.org, RUTA);
	}

	private static Set<String> campos(JsonNode nodo) {
		Set<String> campos = new HashSet<>();
		for (Iterator<String> it = nodo.fieldNames(); it.hasNext();) {
			campos.add(it.next());
		}
		return campos;
	}
}
