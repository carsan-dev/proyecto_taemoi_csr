package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import com.taemoi.project.config.ValidacionRelojConfig;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza 8b-3a contra MySQL: {@code GET /api/tesoreria/pendientes-por-alumno?alumnoIds=…} da, para los alumnos de la
 * página del listado nuevo, lo vencido por alumno con la MISMA regla que {@code /pendiente-vencido} (decisión 267). Solo
 * alumnos de la escuela activa (los ajenos se ignoran), como mucho 25 ids. Hoy fijado con un {@code Clock} simulado:
 * 06/03/2026 (Europe/Madrid). Datos inventados.
 *
 * <pre>
 * cargo         concepto                               alumno    importe  ¿vencido?
 * mEne          MENSUALIDAD ENERO 2026                 aA        30       sí
 * exHoy         DERECHO A EXAMEN, conv. 06/03/2026     aA        20       sí (mismo día)
 * mAbr          MENSUALIDAD ABRIL 2026                 aA        30       no (futuro)
 * mFeb          MENSUALIDAD FEBRERO 2026               aB        30       sí
 * mInscripcion  MENSUALIDAD ENERO 2026, sin alumno_id  aGrupoA*  10       sí (*por su inscripción)
 * mInactivo     MENSUALIDAD ENERO 2026                 aInactivo 30       no (alumno de baja)
 * otraVencido   MENSUALIDAD ENERO 2026 (otra escuela)  otra.aA   99       sí, solo para la otra escuela
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PendientesPorAlumnoIntegrationTest extends MySqlIntegrationTest {

	private static final String RUTA = "/api/tesoreria/pendientes-por-alumno";

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
	String tokenGestor;
	String tokenOtraGestor;
	String tokenResponsable;
	String tokenFamilia;
	long mEne, exHoy, mAbr, mFeb, mInscripcion, mInactivo, otraVencido;

	@BeforeAll
	void preparar() throws Exception {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-8b3a-pendientes");
		long pMens = producto(e, "CUOTA MENSUAL IT", "MENSUALIDAD", "MENSUALIDAD_IT");
		long pExamen = producto(e, "DERECHO A EXAMEN IT", "EXAMEN", "EXAMEN_IT");

		mEne = cargo(e, e.aA, pMens, "MENSUALIDAD ENERO 2026 - TAEKWONDO", "30.00", "2026-01-02 12:00:00", e.sA);
		exHoy = cargo(e, e.aA, pExamen, "DERECHO A EXAMEN IT", "20.00", "2026-02-15 12:00:00", e.sA);
		inscribir(e, e.aA, exHoy, "2026-03-06");
		mAbr = cargo(e, e.aA, pMens, "MENSUALIDAD ABRIL 2026 - TAEKWONDO", "30.00", "2026-03-01 12:00:00", e.sA);
		mFeb = cargo(e, e.aB, pMens, "MENSUALIDAD FEBRERO 2026 - TAEKWONDO", "30.00", "2026-02-01 12:00:00", e.sB);
		long inscripcion = jdbc.queryForObject("SELECT id FROM alumno_deporte WHERE alumno_id = ? AND organizacion_id = ?",
				Long.class, e.aGrupoA, e.org);
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, alumno_deporte_id, producto_id, concepto, precio, cantidad, pagado,
				  fecha_asignacion, organizacion_id, sede_id)
				VALUES (NULL, ?, ?, 'MENSUALIDAD ENERO 2026 - TAEKWONDO', 10.00, 1, 0, '2026-01-02 12:00:00', ?, ?)""",
				inscripcion, pMens, e.org, e.sB);
		mInscripcion = jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE organizacion_id = ?", Long.class,
				e.org);
		mInactivo = cargo(e, e.aInactivoA, pMens, "MENSUALIDAD ENERO 2026 - TAEKWONDO", "30.00",
				"2026-01-02 12:00:00", e.sA);

		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-8b3a-otra");
		long pOtraMens = producto(otra, "CUOTA MENSUAL IT", "MENSUALIDAD", "MENSUALIDAD_IT");
		otraVencido = cargo(otra, otra.aA, pOtraMens, "MENSUALIDAD ENERO 2026 - TAEKWONDO", "99.00",
				"2026-01-02 12:00:00", otra.sA);

		tokenGestor = e.token(jwtService, usuarios, e.gestor);
		tokenOtraGestor = otra.token(jwtService, usuarios, otra.gestor);
		tokenResponsable = e.token(jwtService, usuarios, e.respA);
		tokenFamilia = e.token(jwtService, usuarios, e.cuentaFamilia(usuarios, "familiapendientes"));
	}

	@BeforeEach
	void relojPorDefecto() {
		when(reloj.instant()).thenReturn(LocalDateTime.parse("2026-03-06T10:00").atZone(ValidacionRelojConfig.ZONA)
				.toInstant());
		when(reloj.getZone()).thenReturn(ValidacionRelojConfig.ZONA);
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	@Test
	void pendientesPorAlumno_soloEscuelaActiva() throws Exception {
		Map<Long, JsonNode> r = porAlumno(leer(ids(e.aA, e.aB, e.aGrupoA, e.aSinSede, e.aInactivoA, e.aNada),
				tokenGestor));
		assertEquals(Set.of(e.aA, e.aB, e.aGrupoA), r.keySet(), "solo los que tienen algo vencido");

		JsonNode a = r.get(e.aA);
		assertImporte("50.00", a.get("importe"));
		assertEquals(2, a.get("cargos").asLong());
		Map<Long, JsonNode> detalle = new HashMap<>();
		a.get("detalle").forEach(d -> detalle.put(d.get("productoAlumnoId").asLong(), d));
		assertEquals(Set.of(mEne, exHoy), detalle.keySet(), "sin abril (futuro)");
		assertEquals("MENSUALIDAD ENERO 2026 - TAEKWONDO", detalle.get(mEne).get("concepto").asText());
		assertImporte("30.00", detalle.get(mEne).get("importe"));
		assertEquals("2026-01-06", detalle.get(mEne).get("vencidoDesde").asText());
		assertEquals("2026-03-06", detalle.get(exHoy).get("vencidoDesde").asText());

		assertImporte("10.00", r.get(e.aGrupoA).get("importe"));
		assertEquals(mInscripcion, r.get(e.aGrupoA).get("detalle").get(0).get("productoAlumnoId").asLong(),
				"el cargo de la inscripción, atribuido al alumno");

		// La otra escuela no ve nada de estos alumnos.
		assertEquals(0, leer(ids(e.aA, e.aB, e.aGrupoA), tokenOtraGestor).size());
	}

	@Test
	void pendientesPorAlumno_idsAjenosSeIgnoran() throws Exception {
		JsonNode r = leer(ids(e.aA, otra.aA, 999_999_999L), tokenGestor);
		Set<Long> alumnos = porAlumno(r).keySet();
		assertEquals(Set.of(e.aA), alumnos);
		assertFalse(r.toString().contains("99.00"), "nada de la otra escuela");
		r.forEach(p -> p.get("detalle").forEach(d -> assertTrue(d.get("productoAlumnoId").asLong() != otraVencido)));

		JsonNode deOtra = leer(ids(otra.aA), tokenOtraGestor);
		assertEquals(1, deOtra.size());
		assertImporte("99.00", deOtra.get(0).get("importe"));
	}

	@Test
	void pendientesPorAlumno_masDe25_400() throws Exception {
		String veintiseis = LongStream.rangeClosed(1, 26).mapToObj(Long::toString).collect(Collectors.joining(","));
		String veinticinco = LongStream.rangeClosed(1, 25).mapToObj(Long::toString).collect(Collectors.joining(","));
		assertEquals(400, estado(get(RUTA + "?alumnoIds=" + veintiseis), tokenGestor));
		assertEquals(200, estado(get(RUTA + "?alumnoIds=" + veinticinco), tokenGestor));
		assertEquals(400, estado(get(RUTA), tokenGestor), "sin ids");
	}

	@Test
	void pendientesPorAlumno_coincideConPendienteVencido() throws Exception {
		JsonNode total = leer("/api/tesoreria/pendiente-vencido", tokenGestor);
		JsonNode r = leer(ids(e.aA, e.aB, e.aGrupoA, e.aSinSede, e.aInactivoA, e.aNada), tokenGestor);
		BigDecimal suma = BigDecimal.ZERO;
		long cargos = 0;
		Set<Long> detalle = new HashSet<>();
		for (JsonNode p : r) {
			suma = suma.add(new BigDecimal(p.get("importe").asText()));
			cargos += p.get("cargos").asLong();
			BigDecimal sumaDetalle = BigDecimal.ZERO;
			for (JsonNode d : p.get("detalle")) {
				sumaDetalle = sumaDetalle.add(new BigDecimal(d.get("importe").asText()));
				detalle.add(d.get("productoAlumnoId").asLong());
			}
			assertEquals(0, sumaDetalle.compareTo(new BigDecimal(p.get("importe").asText())), "el detalle suma");
			assertEquals(p.get("cargos").asLong(), p.get("detalle").size());
		}
		assertEquals(0, suma.compareTo(new BigDecimal(total.get("total").asText())), "misma suma que el total");
		assertEquals(total.get("cargos").asLong(), cargos);
		assertEquals(total.get("alumnos").asLong(), r.size());
		assertEquals(Set.of(mEne, exHoy, mFeb, mInscripcion), detalle);
		assertFalse(detalle.contains(mAbr) || detalle.contains(mInactivo));
	}

	@Test
	void pendientesPorAlumno_responsableSede_soloCargosDeSusSedes() throws Exception {
		String ruta = ids(e.aA, e.aB, e.aGrupoA);
		JsonNode deA = leer(ruta, tokenResponsable);
		assertEquals(1, deA.size(), "pieza 9-5: aB y aGrupoA tienen cargos de la sede B");
		assertEquals(e.aA, deA.get(0).get("alumnoId").asLong());
		assertEquals(200, estado(head(ruta), tokenResponsable));
		assertEquals(403, estado(get(ruta), tokenFamilia));
		assertEquals(200, estado(get(ruta), tokenGestor));
	}

	// ------------------------------------------------------------------ ayudantes

	private static String ids(long... alumnos) {
		return RUTA + "?alumnoIds=" + LongStream.of(alumnos).mapToObj(Long::toString).collect(Collectors.joining(","));
	}

	private static Map<Long, JsonNode> porAlumno(JsonNode lista) {
		Map<Long, JsonNode> r = new HashMap<>();
		lista.forEach(p -> r.put(p.get("alumnoId").asLong(), p));
		assertEquals(lista.size(), r.size(), "un elemento por alumno");
		return r;
	}

	private long producto(EscuelaConSedesDePrueba escuela, String concepto, String tipo, String codigo) {
		jdbc.update("INSERT INTO producto (concepto, precio, codigo, tipo, organizacion_id) VALUES (?, 1.0, ?, ?, ?)",
				concepto, codigo, tipo, escuela.org);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = ? AND concepto = ?",
				Long.class, escuela.org, concepto);
	}

	private long cargo(EscuelaConSedesDePrueba escuela, long alumno, long producto, String concepto, String precio,
			String fecha, Long sede) {
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, producto_id, concepto, precio, cantidad, pagado, fecha_asignacion,
				  organizacion_id, sede_id)
				VALUES (?, ?, ?, ?, 1, 0, ?, ?, ?)""", alumno, producto, concepto, new BigDecimal(precio), fecha,
				escuela.org, sede);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE organizacion_id = ?", Long.class,
				escuela.org);
	}

	private void inscribir(EscuelaConSedesDePrueba escuela, long alumno, long cargo, String fechaConvocatoria) {
		jdbc.update("INSERT INTO convocatoria (fecha_convocatoria, organizacion_id, disciplina_id) VALUES (?, ?, ?)",
				fechaConvocatoria, escuela.org, escuela.disciplina);
		long convocatoria = jdbc.queryForObject("SELECT MAX(id) FROM convocatoria WHERE organizacion_id = ?",
				Long.class, escuela.org);
		jdbc.update("""
				INSERT INTO alumno_convocatoria (cuantia_examen, pagado, alumno_id, convocatoria_id, grado_actual,
				  grado_siguiente, organizacion_id, producto_alumno_id) VALUES (20, 0, ?, ?, NULL, NULL, ?, ?)""",
				alumno, convocatoria, escuela.org, cargo);
	}

	private static void assertImporte(String esperado, JsonNode valor) {
		assertEquals(0, new BigDecimal(esperado).compareTo(new BigDecimal(valor.asText())),
				"importe " + valor.asText() + " frente a " + esperado);
	}

	private int estado(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn().getResponse().getStatus();
	}

	private JsonNode leer(String ruta, String token) throws Exception {
		MvcResult r = mvc.perform(get(ruta).cookie(new Cookie("jwt", token))).andReturn();
		assertEquals(200, r.getResponse().getStatus(), ruta + ": " + r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString());
	}
}
