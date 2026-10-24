package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

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
 * Pieza 8b-3l (decisión 285) contra MySQL: «Bajas y eliminaciones».
 *
 * <ul>
 * <li><b>P-4</b>: {@code DELETE /api/alumnos/{id}} solo para el propietario (ROLE_ADMIN). El gestor (ADMINISTRADOR,
 * ROLE_MANAGER) recibe 403 con «Solo el propietario puede eliminar alumnos definitivamente.» y el alumno sigue ahí; el
 * propietario de otra escuela, 404 (no lo ve).</li>
 * <li><b>P-3</b>: {@code GET /api/alumnos/cargos-resumen?alumnoIds=…} da, para los alumnos indicados de la escuela
 * activa, sus cargos sin cancelar (los suyos y los de sus inscripciones): cuántos, cuántos pagados y pendientes y sus
 * importes. Solo el propietario; los ids de otra escuela se ignoran; como mucho 25.</li>
 * </ul>
 *
 * <pre>
 * cargo      alumno       importe  estado
 * pagado     aA           30       pagado
 * pendiente  aA           20       pendiente
 * nulo       aA           15       pagado NULL (cuenta como pendiente, como la tesorería)
 * cancelado  aA           99       cancelado (no cuenta)
 * inscrito   aA (insc.)   10       pendiente, sin alumno_id: por su inscripción de taekwondo
 * deB        aB           40       pagado
 * otra       otra.aA      77       pendiente (otra escuela)
 * </pre>
 * Datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BajasEliminacionesIntegrationTest extends MySqlIntegrationTest {

	private static final String RESUMEN = "/api/alumnos/cargos-resumen";
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
	String tokenPropietario;
	String tokenGestor;
	String tokenResponsable;
	String tokenFamilia;
	String tokenOtraPropietario;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-8b3l");
		long producto = producto(e);
		cargo(e, e.aA, producto, "30.00", true, false);
		cargo(e, e.aA, producto, "20.00", false, false);
		cargo(e, e.aA, producto, "15.00", null, false);
		cargo(e, e.aA, producto, "99.00", false, true);
		long inscripcion = jdbc.queryForObject("SELECT id FROM alumno_deporte WHERE alumno_id = ? AND organizacion_id = ?",
				Long.class, e.aA, e.org);
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, alumno_deporte_id, producto_id, concepto, precio, cantidad, pagado,
				  fecha_asignacion, organizacion_id, sede_id)
				VALUES (NULL, ?, ?, 'CARGO IT 8B3L', 10.00, 1, 0, '2026-09-02 12:00:00', ?, ?)""",
				inscripcion, producto, e.org, e.sA);
		cargo(e, e.aB, producto, "40.00", true, false);

		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-8b3l-otra");
		cargo(otra, otra.aA, producto(otra), "77.00", false, false);

		tokenPropietario = e.token(jwtService, usuarios, propietario(e, "propietario"));
		tokenGestor = e.token(jwtService, usuarios, e.gestor);
		tokenResponsable = e.token(jwtService, usuarios, e.respA);
		tokenFamilia = e.token(jwtService, usuarios, e.cuentaFamilia(usuarios, "familia8b3l"));
		tokenOtraPropietario = otra.token(jwtService, usuarios, propietario(otra, "propietario-otra"));
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ P-4: borrado solo del propietario

	@Test
	void eliminar_gestor_403ConMensaje_yNoBorra() throws Exception {
		long alumno = e.alumno("IT8B3LGESTOR" + e.marca, e.sA, true);

		MvcResult r = mvc.perform(delete("/api/alumnos/" + alumno).cookie(jwt(tokenGestor))).andReturn();

		assertEquals(403, r.getResponse().getStatus());
		JsonNode cuerpo = json.readTree(r.getResponse().getContentAsString());
		assertEquals(SOLO_PROPIETARIO, cuerpo.get("mensaje").asText());
		assertEquals(1, contar("SELECT COUNT(*) FROM alumno WHERE id = ?", alumno), "el alumno sigue ahí");
	}

	@Test
	void eliminar_responsableYFamilia_403_yNoBorra() throws Exception {
		long alumno = e.alumno("IT8B3LOTROS" + e.marca, e.sA, true);

		assertEquals(403, estado(delete("/api/alumnos/" + alumno), tokenResponsable));
		assertEquals(403, estado(delete("/api/alumnos/" + alumno), tokenFamilia));
		assertEquals(1, contar("SELECT COUNT(*) FROM alumno WHERE id = ?", alumno));
	}

	@Test
	void eliminar_propietario_200() throws Exception {
		long alumno = e.alumno("IT8B3LPROP" + e.marca, e.sA, true);

		assertEquals(200, estado(delete("/api/alumnos/" + alumno), tokenPropietario));
		assertEquals(0, contar("SELECT COUNT(*) FROM alumno WHERE id = ?", alumno));
	}

	@Test
	void eliminar_propietarioDeOtraEscuela_404_yNoBorra() throws Exception {
		long alumno = e.alumno("IT8B3LAJENO" + e.marca, e.sA, true);

		assertEquals(404, estado(delete("/api/alumnos/" + alumno), tokenOtraPropietario));
		assertEquals(1, contar("SELECT COUNT(*) FROM alumno WHERE id = ?", alumno), "otra escuela no lo borra");
	}

	// ------------------------------------------------------------------ P-3: resumen de cargos

	@Test
	void resumenCargos_cuentaPagadosYPendientes_conLaInscripcion_sinCancelados() throws Exception {
		Map<Long, JsonNode> r = porAlumno(leer(ids(e.aA, e.aB, e.aNada), tokenPropietario));

		assertEquals(3, r.size(), "uno por alumno pedido de la escuela, también sin cargos");
		JsonNode a = r.get(e.aA);
		assertEquals(4, a.get("cargos").asLong(), "pagado, pendiente, pagado NULL e inscripción; sin el cancelado");
		assertEquals(1, a.get("pagados").asLong());
		assertEquals(3, a.get("pendientes").asLong());
		assertImporte("30.00", a.get("importePagado"));
		assertImporte("45.00", a.get("importePendiente"));

		JsonNode b = r.get(e.aB);
		assertEquals(1, b.get("cargos").asLong());
		assertImporte("40.00", b.get("importePagado"));
		assertImporte("0.00", b.get("importePendiente"));

		JsonNode nada = r.get(e.aNada);
		assertEquals(0, nada.get("cargos").asLong());
		assertEquals(0, nada.get("pagados").asLong());
		assertEquals(0, nada.get("pendientes").asLong());
		assertImporte("0.00", nada.get("importePagado"));
	}

	@Test
	void resumenCargos_ordenDeLosIdsPedidos() throws Exception {
		JsonNode r = leer(ids(e.aB, e.aA), tokenPropietario);
		assertEquals(e.aB, r.get(0).get("alumnoId").asLong());
		assertEquals(e.aA, r.get(1).get("alumnoId").asLong());
	}

	@Test
	void resumenCargos_otraEscuela_noVeNadaDeEstosAlumnos() throws Exception {
		// Aislamiento: el propietario de otra escuela pide los ids de esta y no recibe nada.
		assertEquals(0, leer(ids(e.aA, e.aB), tokenOtraPropietario).size());

		// Mezclando ids: solo los suyos, sin nada de la otra escuela.
		JsonNode mezcla = leer(ids(e.aA, otra.aA, 999_999_999L), tokenPropietario);
		assertEquals(1, mezcla.size());
		assertEquals(e.aA, mezcla.get(0).get("alumnoId").asLong());
		assertFalse(mezcla.toString().contains("77"), "nada de la otra escuela");

		JsonNode suyo = leer(ids(otra.aA), tokenOtraPropietario);
		assertEquals(1, suyo.size());
		assertImporte("77.00", suyo.get(0).get("importePendiente"));
	}

	@Test
	void resumenCargos_soloElPropietario() throws Exception {
		String ruta = ids(e.aA);
		assertEquals(200, estado(get(ruta), tokenPropietario));
		assertEquals(403, estado(get(ruta), tokenGestor), "el gestor no borra, no lo necesita");
		assertEquals(403, estado(head(ruta), tokenGestor));
		assertEquals(403, estado(get(ruta), tokenResponsable));
		assertEquals(403, estado(get(ruta), tokenFamilia));
	}

	@Test
	void resumenCargos_entre1y25() throws Exception {
		String veintiseis = LongStream.rangeClosed(1, 26).mapToObj(Long::toString).collect(Collectors.joining(","));
		String veinticinco = LongStream.rangeClosed(1, 25).mapToObj(Long::toString).collect(Collectors.joining(","));
		assertEquals(400, estado(get(RESUMEN + "?alumnoIds=" + veintiseis), tokenPropietario));
		assertEquals(200, estado(get(RESUMEN + "?alumnoIds=" + veinticinco), tokenPropietario));
		assertEquals(400, estado(get(RESUMEN), tokenPropietario), "sin ids");
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
				"CARGO IT 8B3L", "CARGO_IT_8B3L", escuela.org);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = ?", Long.class, escuela.org);
	}

	private void cargo(EscuelaConSedesDePrueba escuela, long alumno, long producto, String precio, Boolean pagado,
			boolean cancelado) {
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, producto_id, concepto, precio, cantidad, pagado, fecha_asignacion,
				  organizacion_id, sede_id, cancelado_en)
				VALUES (?, ?, 'CARGO IT 8B3L', ?, 1, ?, '2026-09-02 12:00:00', ?, ?, ?)""", alumno, producto,
				new BigDecimal(precio), pagado, escuela.org, escuela.sA, cancelado ? "2026-09-03 12:00:00" : null);
	}

	private static String ids(long... alumnos) {
		return RESUMEN + "?alumnoIds=" + LongStream.of(alumnos).mapToObj(Long::toString).collect(Collectors.joining(","));
	}

	private static Map<Long, JsonNode> porAlumno(JsonNode lista) {
		Map<Long, JsonNode> r = new HashMap<>();
		lista.forEach(p -> r.put(p.get("alumnoId").asLong(), p));
		assertEquals(lista.size(), r.size(), "un elemento por alumno");
		return r;
	}

	private static void assertImporte(String esperado, JsonNode valor) {
		assertEquals(0, new BigDecimal(esperado).compareTo(new BigDecimal(valor.asText())),
				"importe " + valor.asText() + " frente a " + esperado);
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
