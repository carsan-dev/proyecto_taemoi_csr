package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
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
 * Pieza 8b-3l bis (decisión 287) contra MySQL: un alumno con pagos no se borra para siempre; se elimina anonimizando.
 *
 * <ul>
 * <li>{@code DELETE /api/alumnos/{id}} de un alumno con pagos (cargo pagado, o cobro en cualquier estado, también el de
 * un cargo ya borrado): 409 «Este alumno tiene pagos. Dale de baja o elimínalo anonimizando.» sin tocar nada. Sin pagos,
 * se borra como hoy.</li>
 * <li>{@code POST /api/alumnos/{id}/anonimizar}: quita sus datos personales (foto y documentos con sus archivos,
 * tutores, salud, acceso de familia, grupos y turnos) y conserva cargos y cobros con el mismo alumno: la tesorería no
 * cambia. Solo el propietario; otra escuela, 404; repetirlo no hace nada; no sale en los listados.</li>
 * </ul>
 * Datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnonimizarAlumnoIntegrationTest extends MySqlIntegrationTest {

	private static final String CON_PAGOS = "Este alumno tiene pagos. Dale de baja o elimínalo anonimizando.";
	private static final String SOLO_PROPIETARIO = "Solo el propietario puede eliminar alumnos definitivamente.";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	long producto;
	String tokenPropietario;
	String tokenGestor;
	String tokenOtraPropietario;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-8b3lbis");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-8b3lbis-otra");
		producto = producto(e);
		tokenPropietario = e.token(jwtService, usuarios, propietario(e, "propietario"));
		tokenGestor = e.token(jwtService, usuarios, e.gestor);
		tokenOtraPropietario = otra.token(jwtService, usuarios, propietario(otra, "propietario-otra"));
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ borrado definitivo

	@Test
	void eliminar_conCargoPagado_409_yNoCambiaNada() throws Exception {
		long alumno = e.alumno("IT8B3LBISPAGADO" + e.marca, e.sA, true);
		cargo(alumno, "30.00", true);
		cargo(alumno, "20.00", false);
		String antes = foto(alumno);

		MvcResult r = mvc.perform(delete("/api/alumnos/" + alumno).cookie(jwt(tokenPropietario))).andReturn();

		assertEquals(409, r.getResponse().getStatus());
		assertEquals(CON_PAGOS, json.readTree(r.getResponse().getContentAsString()).get("mensaje").asText());
		assertEquals(antes, foto(alumno), "ni el alumno, ni sus cargos, ni sus cobros cambian");
	}

	@Test
	void eliminar_conCobroAnuladoOSinCargo_409() throws Exception {
		// Un cobro cuenta en cualquier estado: anulado, sobre un cargo que ya no figura como pagado.
		long anulado = e.alumno("IT8B3LBISANULADO" + e.marca, e.sA, true);
		long cargo = cargo(anulado, "25.00", false);
		cobro(anulado, cargo, "25.00", "ANULADO");
		// Y el cobro de un cargo ya borrado (producto_alumno_id NULL), por su alumno_id.
		long huerfano = e.alumno("IT8B3LBISHUERFANO" + e.marca, e.sA, true);
		cobro(huerfano, null, "12.00", "ACTIVO");

		assertEquals(409, estado(delete("/api/alumnos/" + anulado), tokenPropietario));
		assertEquals(409, estado(delete("/api/alumnos/" + huerfano), tokenPropietario));
		assertEquals(2, contar("SELECT COUNT(*) FROM alumno WHERE id IN (?, ?)", anulado, huerfano));
		assertEquals(1, contar("SELECT COUNT(*) FROM producto_alumno WHERE id = ?", cargo));
	}

	@Test
	void eliminar_sinPagos_borraComoHoy() throws Exception {
		long alumno = e.alumno("IT8B3LBISSINPAGOS" + e.marca, e.sA, true);
		long pendiente = cargo(alumno, "20.00", false);

		assertEquals(200, estado(delete("/api/alumnos/" + alumno), tokenPropietario));
		assertEquals(0, contar("SELECT COUNT(*) FROM alumno WHERE id = ?", alumno));
		assertEquals(0, contar("SELECT COUNT(*) FROM producto_alumno WHERE id = ?", pendiente));
	}

	@Test
	void resumenCargos_cuentaLosPagos() throws Exception {
		long conPagos = e.alumno("IT8B3LBISRESUMEN" + e.marca, e.sA, true);
		long cargo = cargo(conPagos, "30.00", true);
		cobro(conPagos, cargo, "30.00", "ACTIVO");
		cobro(conPagos, null, "5.00", "ACTIVO");
		long sinPagos = e.alumno("IT8B3LBISRESUMEN0" + e.marca, e.sA, true);
		cargo(sinPagos, "10.00", false);

		JsonNode r = leer("/api/alumnos/cargos-resumen?alumnoIds=" + conPagos + "," + sinPagos, tokenPropietario);
		assertEquals(2, r.get(0).get("pagos").asLong(), "el cargo pagado con su cobro es uno; el cobro sin cargo, otro");
		assertEquals(0, r.get(1).get("pagos").asLong());
	}

	// ------------------------------------------------------------------ eliminar anonimizando

	@Test
	void anonimizar_conservaCargosYCobros_yLaTesoreriaNoCambia() throws Exception {
		long alumno = e.alumno("IT8B3LBISTESORERIA" + e.marca, e.sA, true);
		long pagado = cargo(alumno, "30.00", true);
		cobro(alumno, pagado, "30.00", "ACTIVO");
		cargo(alumno, "20.00", false);
		String cargosYCobros = cargosYCobros(alumno);
		String tesoreria = leer("/api/tesoreria/resumen?mes=9&ano=2026&soloActivos=false", tokenPropietario).toString();

		assertEquals(200, estado(post("/api/alumnos/" + alumno + "/anonimizar"), tokenPropietario));

		assertEquals(cargosYCobros, cargosYCobros(alumno), "cargos y cobros iguales, con el mismo alumno");
		assertEquals(tesoreria,
				leer("/api/tesoreria/resumen?mes=9&ano=2026&soloActivos=false", tokenPropietario).toString(),
				"los totales de la tesorería no cambian");
		assertEquals("Alumno eliminado", jdbc.queryForObject(
				"SELECT CONCAT(nombre, ' ', apellidos) FROM alumno WHERE id = ?", String.class, alumno));
	}

	@Test
	void anonimizar_quitaLosDatosPersonales_conSusArchivos() throws Exception {
		long alumno = e.alumno("IT8B3LBISDATOS" + e.marca, e.sA, true);
		long pagado = cargo(alumno, "30.00", true);
		jdbc.update("""
				UPDATE alumno SET nif = '12345678Z', email = 'lucia@example.test', telefono2 = '611111111',
				  localidad = 'Jaén', codigo_postal = '23001', observaciones = 'Notas', genero = 'FEMENINO'
				WHERE id = ?""", alumno);
		Path fotoArchivo = archivo("target/it-files/imagenes/org-" + e.org + "/alumnos/it8b3lbis-" + e.marca + ".jpg");
		jdbc.update("INSERT INTO imagen (nombre, ruta, tipo, url, organizacion_id) VALUES (?, ?, 'image/jpeg', ?, ?)",
				"it8b3lbis-" + e.marca + ".jpg", "org-" + e.org + "/alumnos/it8b3lbis-" + e.marca + ".jpg",
				"/imagenes/org-" + e.org + "/alumnos/it8b3lbis-" + e.marca + ".jpg", e.org);
		long imagen = jdbc.queryForObject("SELECT MAX(id) FROM imagen WHERE organizacion_id = ?", Long.class, e.org);
		jdbc.update("UPDATE alumno SET foto_alumno_id = ? WHERE id = ?", imagen, alumno);
		String rutaDoc = "org-" + e.org + "/alumnos/it8b3lbis-" + e.marca + "/ficha.pdf";
		Path docArchivo = archivo("target/it-files/documentos/" + rutaDoc);
		jdbc.update("""
				INSERT INTO documento (nombre, ruta, tipo, url, alumno_id, organizacion_id)
				VALUES ('ficha.pdf', ?, 'application/pdf', NULL, ?, ?)""", rutaDoc, alumno, e.org);
		jdbc.update("""
				INSERT INTO tutor (organizacion_id, alumno_id, nombre, nif, parentesco, responsable_legal, orden)
				VALUES (?, ?, 'Madre Prueba', NULL, 'MADRE', TRUE, 1)""", e.org, alumno);
		jdbc.update("""
				INSERT INTO alumno_salud (alumno_id, organizacion_id, alergias, origen, actualizado_en)
				VALUES (?, ?, 'x', 'APP', NOW(6))""", alumno, e.org);
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", e.gA, alumno);
		e.enTurno(e.tA, alumno);
		long cuenta = e.cuenta(usuarios, "familia8b3lbis");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'FAMILIA')", cuenta, e.org);
		jdbc.update("UPDATE usuario SET alumno_id = ? WHERE id = ?", alumno, cuenta);
		jdbc.update("""
				INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen)
				VALUES (?, ?, ?, 'TUTOR', 'MANUAL')""", cuenta, alumno, e.org);

		assertEquals(200, estado(post("/api/alumnos/" + alumno + "/anonimizar"), tokenPropietario));

		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM alumno WHERE id = ?", alumno);
		assertEquals("Alumno", fila.get("nombre"));
		assertEquals("eliminado", fila.get("apellidos"));
		for (String campo : List.of("nif", "email", "telefono", "telefono2", "localidad", "codigo_postal",
				"observaciones", "genero", "foto_alumno_id")) {
			assertNull(fila.get(campo), campo + " borrado");
		}
		assertEquals("Sin datos", fila.get("direccion"));
		assertEquals("1900-01-01", fila.get("fecha_nacimiento").toString());
		assertEquals(Boolean.FALSE, toBool(fila.get("activo")));
		assertNotNull(fila.get("fecha_baja"));
		assertNotNull(fila.get("anonimizado_en"));
		assertFalse(Files.exists(fotoArchivo), "el archivo de la foto se borra");
		assertFalse(Files.exists(docArchivo), "el archivo del documento se borra");
		assertEquals(0, contar("SELECT COUNT(*) FROM imagen WHERE id = ?", imagen));
		assertEquals(0, contar("SELECT COUNT(*) FROM documento WHERE alumno_id = ?", alumno));
		assertEquals(0, contar("SELECT COUNT(*) FROM tutor WHERE alumno_id = ?", alumno));
		assertEquals(0, contar("SELECT COUNT(*) FROM alumno_salud WHERE alumno_id = ?", alumno));
		assertEquals(0, contar("SELECT COUNT(*) FROM alumno_grupo WHERE alumno_id = ?", alumno));
		assertEquals(0, contar("SELECT COUNT(*) FROM alumno_turno WHERE alumno_id = ?", alumno));
		assertEquals(0, contar("SELECT COUNT(*) FROM usuario_alumno WHERE alumno_id = ?", alumno));
		assertEquals(0, contar("SELECT COUNT(*) FROM usuario WHERE id = ?", cuenta), "sin membresías, la cuenta cae");
		assertEquals(0, contar("SELECT COUNT(*) FROM alumno_deporte WHERE alumno_id = ? AND activo = TRUE", alumno));
		assertEquals(1, contar("SELECT COUNT(*) FROM producto_alumno WHERE id = ? AND alumno_id = ?", pagado, alumno));
	}

	@Test
	void anonimizar_gestor_403ConMensaje_yNoCambiaNada() throws Exception {
		long alumno = e.alumno("IT8B3LBISGESTOR" + e.marca, e.sA, true);
		cargo(alumno, "30.00", true);
		String antes = foto(alumno);

		MvcResult r = mvc.perform(post("/api/alumnos/" + alumno + "/anonimizar").cookie(jwt(tokenGestor))).andReturn();

		assertEquals(403, r.getResponse().getStatus());
		assertEquals(SOLO_PROPIETARIO, json.readTree(r.getResponse().getContentAsString()).get("mensaje").asText());
		assertEquals(antes, foto(alumno));
	}

	@Test
	void anonimizar_propietarioDeOtraEscuela_404_yNoCambiaNada() throws Exception {
		long alumno = e.alumno("IT8B3LBISAJENO" + e.marca, e.sA, true);
		cargo(alumno, "30.00", true);
		String antes = foto(alumno);

		assertEquals(404, estado(post("/api/alumnos/" + alumno + "/anonimizar"), tokenOtraPropietario));
		assertEquals(antes, foto(alumno));
	}

	@Test
	void anonimizado_noSaleEnLosListados_niPuedeVolverDeAlta() throws Exception {
		String nombre = "IT8B3LBISLISTA" + e.marca;
		long alumno = e.alumno(nombre, e.sA, true);
		e.taekwondo(alumno, false);
		cargo(alumno, "30.00", true);
		assertTrue(leer("/api/alumnos/ids?incluirInactivos=true", tokenPropietario).toString()
				.contains(String.valueOf(alumno)), "antes sale");

		assertEquals(200, estado(post("/api/alumnos/" + alumno + "/anonimizar"), tokenPropietario));

		assertFalse(ids(leer("/api/alumnos/ids?incluirInactivos=true", tokenPropietario)).contains(alumno));
		assertFalse(ids(leer("/api/alumnos?incluirInactivos=true", tokenPropietario)).contains(alumno));
		assertFalse(ids(leer("/api/alumnos?page=1&size=100&incluirInactivos=true&nombre=Alumno%20eliminado",
				tokenPropietario).get("content")).contains(alumno), "ni buscando por su nombre nuevo");
		assertFalse(ids(leer("/api/alumnos?page=1&size=100&incluirInactivos=true&deporte=TAEKWONDO",
				tokenPropietario).get("content")).contains(alumno), "ni por deporte");
		assertEquals(409, estado(put("/api/alumnos/" + alumno + "/alta"), tokenPropietario), "es irreversible");
		assertEquals(0, contar("SELECT COUNT(*) FROM alumno WHERE id = ? AND activo = TRUE", alumno));
	}

	@Test
	void anonimizar_dosVeces_esInofensivo() throws Exception {
		long alumno = e.alumno("IT8B3LBISDOBLE" + e.marca, e.sA, true);
		long pagado = cargo(alumno, "30.00", true);
		cobro(alumno, pagado, "30.00", "ACTIVO");

		assertEquals(200, estado(post("/api/alumnos/" + alumno + "/anonimizar"), tokenPropietario));
		String despues = foto(alumno);
		assertEquals(200, estado(post("/api/alumnos/" + alumno + "/anonimizar"), tokenPropietario));
		assertEquals(despues, foto(alumno), "la segunda vez no cambia nada");
		assertEquals(409, estado(delete("/api/alumnos/" + alumno), tokenPropietario), "y sigue sin borrarse");
	}

	// ------------------------------------------------------------------ ayudantes

	private long propietario(EscuelaConSedesDePrueba escuela, String nombre) {
		long id = escuela.cuenta(usuarios, nombre);
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROPIETARIO')", id,
				escuela.org);
		return id;
	}

	private long producto(EscuelaConSedesDePrueba escuela) {
		jdbc.update("INSERT INTO producto (concepto, precio, codigo, tipo, organizacion_id) VALUES (?, 1.0, ?, 'OTRO', ?)",
				"CARGO IT 8B3LBIS", "CARGO_IT_8B3LBIS", escuela.org);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = ?", Long.class, escuela.org);
	}

	private long cargo(long alumno, String precio, boolean pagado) {
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, producto_id, concepto, precio, cantidad, pagado, fecha_asignacion,
				  fecha_pago, organizacion_id, sede_id)
				VALUES (?, ?, 'CARGO IT 8B3LBIS', ?, 1, ?, '2026-09-02 12:00:00', ?, ?, ?)""", alumno, producto,
				new BigDecimal(precio), pagado, pagado ? "2026-09-02 12:00:00" : null, e.org, e.sA);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE alumno_id = ?", Long.class, alumno);
	}

	private void cobro(long alumno, Long cargo, String importe, String estado) {
		jdbc.update("""
				INSERT INTO cobro (organizacion_id, producto_alumno_id, sede_id, alumno_id, concepto, importe, metodo,
				  fecha_cobro, origen, estado) VALUES (?, ?, ?, ?, 'CARGO IT 8B3LBIS', ?, 'EFECTIVO', '2026-09-02 12:00:00',
				  'TESORERIA', ?)""", e.org, cargo, e.sA, alumno, new BigDecimal(importe), estado);
	}

	/** La fila del alumno, sus cargos y sus cobros, para comparar antes y después. */
	private String foto(long alumno) {
		return jdbc.queryForList("SELECT * FROM alumno WHERE id = ?", alumno) + "|" + cargosYCobros(alumno);
	}

	private String cargosYCobros(long alumno) {
		return jdbc.queryForList("""
				SELECT id, alumno_id, precio, pagado, fecha_pago, cancelado_en FROM producto_alumno
				WHERE alumno_id = ? ORDER BY id""", alumno) + "|" + jdbc.queryForList("""
				SELECT id, producto_alumno_id, alumno_id, importe, estado, entrega_caja_id FROM cobro
				WHERE alumno_id = ? ORDER BY id""", alumno);
	}

	private static Path archivo(String ruta) throws IOException {
		Path p = Path.of(ruta).toAbsolutePath();
		Files.createDirectories(p.getParent());
		Files.writeString(p, "it");
		return p;
	}

	private static Boolean toBool(Object v) {
		return v instanceof Boolean b ? b : v == null ? null : ((Number) v).intValue() != 0;
	}

	private static List<Long> ids(JsonNode lista) {
		List<Long> r = new java.util.ArrayList<>();
		lista.forEach(n -> r.add(n.isNumber() ? n.asLong() : n.get("id").asLong()));
		return r;
	}

	private int contar(String sql, Object... args) {
		return jdbc.queryForObject(sql, Integer.class, args);
	}

	private int estado(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(jwt(token))).andReturn().getResponse().getStatus();
	}

	private JsonNode leer(String ruta, String token) throws Exception {
		MvcResult r = mvc.perform(get(ruta).cookie(jwt(token))).andReturn();
		assertEquals(200, r.getResponse().getStatus(), ruta + ": " + r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString());
	}

	private static Cookie jwt(String token) {
		return new Cookie("jwt", token);
	}
}
