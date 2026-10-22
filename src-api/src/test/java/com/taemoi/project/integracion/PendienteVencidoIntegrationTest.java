package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
 * Pieza 8b-2 (decisiones 242 y 267) contra MySQL: {@code GET /api/tesoreria/pendiente-vencido} suma las mensualidades
 * y los derechos de examen NO pagados (ni cancelados) con el plazo vencido, de cualquier mes. Mensualidad: su mes (el
 * del concepto o, si no lo trae, el de la asignación) es anterior al mes en curso, o es el mes en curso y hoy es día 6
 * o posterior. Derecho de examen: la fecha de su convocatoria es hoy o anterior; sin convocatoria, la fecha de
 * asignación. Mismo universo que la tesorería (alumnos activos, sin cancelados). Hoy fijado:
 * Europe/Madrid con un {@code Clock} simulado ({@code @MockitoBean}). Escuelas sintéticas de {@link EscuelaConSedesDePrueba}; datos inventados.
 *
 * <pre>
 * hoy 06/03/2026         cargo                                   alumno    sede  importe  ¿vencido?
 * mEne                   MENSUALIDAD ENERO 2026                  aA        A     30       sí
 * mFeb                   MENSUALIDAD FEBRERO 2026                aB        B     30       sí
 * mMar                   MENSUALIDAD MARZO 2026                  aA        A     30       sí (día 6; el 5, no)
 * mAbr                   MENSUALIDAD ABRIL 2026 (generada antes) aA        A     30       no (mes futuro)
 * mSinMes                CUOTA IT (producto MENSUALIDAD), dic-25 aSinSede  sin   25       sí (mes de la asignación)
 * mPagada / mCancelada   ENERO pagada / FEBRERO cancelada        aA        A     30       no
 * mInactivo              MENSUALIDAD ENERO 2026                  aInactivo A     30       no (alumno de baja)
 * exPasada               DERECHO A EXAMEN, conv. 20/02/2026      aGrupoA   B     20       sí
 * exHoy                  DERECHO A EXAMEN, conv. 06/03/2026      aA        A     20       sí (mismo día)
 * exFutura               DERECHO A EXAMEN, conv. 15/06/2026      aB        B     20       no
 * exSinConv              DERECHO A EXAMEN sin conv., 10/02/2026  aA        A     20       sí (fecha de asignación)
 * otro                   CAMISETA IT, 2025                       aA        A     10       no (no es de esos tipos)
 * mPagadoNull            MENSUALIDAD FEBRERO 2026, pagado NULL   aB        B     15       sí (NULL = pendiente)
 * mInscripcion           MENSUALIDAD ENERO 2026, sin alumno_id   aGrupoA*  B     10       sí (*por su inscripción)
 * mAnoSinMes             MENSUALIDAD 2027 (año sin mes), ene-26  aA        A     30       no (enero de 2027, como la
 *                                                                                         tesorería: año del concepto)
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PendienteVencidoIntegrationTest extends MySqlIntegrationTest {

	private static final String RUTA = "/api/tesoreria/pendiente-vencido";

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
	long pMens, pExamen, pOtro;
	long mEne, mFeb, mMar, mAbr, mSinMes, mPagada, mCancelada, mInactivo;
	long exPasada, exHoy, exFutura, exSinConv, otro;
	long mPagadoNull, mInscripcion, mAnoSinMes;
	long otraVencido;

	@BeforeAll
	void preparar() throws Exception {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-8b2-pendiente");
		pMens = producto(e, "CUOTA MENSUAL IT", "MENSUALIDAD", "MENSUALIDAD_IT");
		pExamen = producto(e, "DERECHO A EXAMEN IT", "EXAMEN", "EXAMEN_IT");
		pOtro = producto(e, "CAMISETA IT", "OTRO", null);

		mEne = cargo(e, e.aA, pMens, "MENSUALIDAD ENERO 2026 - TAEKWONDO", "30.00", "2026-01-02 12:00:00", e.sA);
		mFeb = cargo(e, e.aB, pMens, "MENSUALIDAD FEBRERO 2026 - TAEKWONDO", "30.00", "2026-02-01 12:00:00", e.sB);
		mMar = cargo(e, e.aA, pMens, "MENSUALIDAD MARZO 2026 - TAEKWONDO", "30.00", "2026-03-01 12:00:00", e.sA);
		mAbr = cargo(e, e.aA, pMens, "MENSUALIDAD ABRIL 2026 - TAEKWONDO", "30.00", "2026-03-01 12:00:00", e.sA);
		mSinMes = cargo(e, e.aSinSede, pMens, "CUOTA MENSUAL IT", "25.00", "2025-12-10 12:00:00", null);
		mPagada = cargo(e, e.aA, pMens, "MENSUALIDAD ENERO 2026 - KICKBOXING", "30.00", "2026-01-02 12:00:00", e.sA);
		jdbc.update("UPDATE producto_alumno SET pagado = 1, fecha_pago = '2026-01-03' WHERE id = ?", mPagada);
		mCancelada = cargo(e, e.aA, pMens, "MENSUALIDAD FEBRERO 2026 - KICKBOXING", "30.00", "2026-02-01 12:00:00",
				e.sA);
		jdbc.update("UPDATE producto_alumno SET cancelado_en = '2026-02-02 10:00:00', motivo_cancelacion = 'IT' "
				+ "WHERE id = ?", mCancelada);
		mInactivo = cargo(e, e.aInactivoA, pMens, "MENSUALIDAD ENERO 2026 - TAEKWONDO", "30.00",
				"2026-01-02 12:00:00", e.sA);

		exPasada = cargo(e, e.aGrupoA, pExamen, "DERECHO A EXAMEN IT", "20.00", "2026-01-15 12:00:00", e.sB);
		inscribir(e, e.aGrupoA, exPasada, "2026-02-20");
		exHoy = cargo(e, e.aA, pExamen, "DERECHO A EXAMEN IT", "20.00", "2026-02-15 12:00:00", e.sA);
		inscribir(e, e.aA, exHoy, "2026-03-06");
		exFutura = cargo(e, e.aB, pExamen, "DERECHO A EXAMEN IT", "20.00", "2026-02-01 12:00:00", e.sB);
		inscribir(e, e.aB, exFutura, "2026-06-15");
		exSinConv = cargo(e, e.aA, pExamen, "DERECHO A EXAMEN IT", "20.00", "2026-02-10 12:00:00", e.sA);
		otro = cargo(e, e.aA, pOtro, "CAMISETA IT", "10.00", "2025-01-10 12:00:00", e.sA);
		mPagadoNull = cargo(e, e.aB, pMens, "MENSUALIDAD FEBRERO 2026 - PILATES", "15.00", "2026-02-01 12:00:00", e.sB);
		jdbc.update("UPDATE producto_alumno SET pagado = NULL WHERE id = ?", mPagadoNull);
		long inscripcion = jdbc.queryForObject("SELECT id FROM alumno_deporte WHERE alumno_id = ? AND organizacion_id = ?",
				Long.class, e.aGrupoA, e.org);
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, alumno_deporte_id, producto_id, concepto, precio, cantidad, pagado,
				  fecha_asignacion, organizacion_id, sede_id)
				VALUES (NULL, ?, ?, 'MENSUALIDAD ENERO 2026 - TAEKWONDO', 10.00, 1, 0, '2026-01-02 12:00:00', ?, ?)""",
				inscripcion, pMens, e.org, e.sB);
		mInscripcion = jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE organizacion_id = ?", Long.class,
				e.org);
		mAnoSinMes = cargo(e, e.aA, pMens, "MENSUALIDAD 2027 - TAEKWONDO", "30.00", "2026-01-02 12:00:00", e.sA);

		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-8b2-otra");
		long pOtraMens = producto(otra, "CUOTA MENSUAL IT", "MENSUALIDAD", "MENSUALIDAD_IT");
		otraVencido = cargo(otra, otra.aA, pOtraMens, "MENSUALIDAD ENERO 2026 - TAEKWONDO", "99.00",
				"2026-01-02 12:00:00", otra.sA);

		tokenGestor = e.token(jwtService, usuarios, e.gestor);
		tokenOtraGestor = otra.token(jwtService, usuarios, otra.gestor);
		tokenResponsable = e.token(jwtService, usuarios, e.respA);
		tokenFamilia = e.token(jwtService, usuarios, e.cuentaFamilia(usuarios, "familiapendiente"));
	}

	/** Sin «hoy» explícito, el 06/03/2026 a las 10:00 (Europe/Madrid). */
	@BeforeEach
	void relojPorDefecto() {
		when(reloj.instant()).thenReturn(LocalDateTime.parse("2026-03-06T10:00").atZone(ValidacionRelojConfig.ZONA).toInstant());
		when(reloj.getZone()).thenReturn(ValidacionRelojConfig.ZONA);
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	@Test
	void pendienteVencido_coincideConMovimientosNoPagadosVencidos() throws Exception {
		JsonNode r = leerEl("2026-03-06T10:00", tokenGestor);
		assertImporte("200.00", r.get("total"));
		assertEquals(9, r.get("cargos").asLong());
		assertEquals(4, r.get("alumnos").asLong(), "aA, aB, aGrupoA y aSinSede");

		// La misma clasificación que la tesorería de hoy: los movimientos no pagados de esas categorías son
		// exactamente los vencidos y los que aún no vencen; los vencidos suman el total.
		JsonNode movimientos = leer("/api/tesoreria/movimientos?pagado=false&size=100", tokenGestor);
		Map<Long, String> categoria = new HashMap<>();
		Map<Long, BigDecimal> importe = new HashMap<>();
		movimientos.get("content").forEach(m -> {
			categoria.put(m.get("productoAlumnoId").asLong(), m.get("categoria").asText());
			importe.put(m.get("productoAlumnoId").asLong(), new BigDecimal(m.get("precio").asText()));
		});
		Set<Long> deEsosTipos = new HashSet<>();
		categoria.forEach((id, c) -> {
			if ("MENSUALIDAD".equals(c) || "EXAMEN".equals(c)) {
				deEsosTipos.add(id);
			}
		});
		Set<Long> vencidos = Set.of(mEne, mFeb, mMar, mSinMes, exPasada, exHoy, exSinConv, mPagadoNull, mInscripcion);
		assertEquals(union(vencidos, Set.of(mAbr, exFutura, mAnoSinMes)), deEsosTipos);
		BigDecimal suma = vencidos.stream().map(importe::get).reduce(BigDecimal.ZERO, BigDecimal::add);
		assertEquals(0, suma.compareTo(new BigDecimal(r.get("total").asText())));
	}

	@Test
	void pendienteVencido_desglosesSumanElTotal() throws Exception {
		JsonNode r = leerEl("2026-03-06T10:00", tokenGestor);
		BigDecimal total = new BigDecimal(r.get("total").asText());
		for (String desglose : List.of("porTipo", "porMes", "porSede")) {
			BigDecimal suma = BigDecimal.ZERO;
			long cargos = 0;
			for (JsonNode fila : r.get(desglose)) {
				suma = suma.add(new BigDecimal(fila.get("importe").asText()));
				cargos += fila.get("cargos").asLong();
			}
			assertEquals(0, total.compareTo(suma), desglose + " suma el total");
			assertEquals(r.get("cargos").asLong(), cargos, desglose + " suma los cargos");
		}
		assertEquals(Map.of("MENSUALIDAD", "140.00", "EXAMEN", "60.00"), filas(r.get("porTipo"), "tipo"));
		assertEquals(Map.of("2025-12", "25.00", "2026-01", "40.00", "2026-02", "85.00", "2026-03", "50.00"),
				filas(r.get("porMes"), "mes"));
		List<String> meses = new ArrayList<>();
		r.get("porMes").forEach(f -> meses.add(f.get("mes").asText()));
		assertEquals(List.of("2025-12", "2026-01", "2026-02", "2026-03"), meses, "del más antiguo al más reciente");
		Map<String, String> porSede = new HashMap<>();
		r.get("porSede").forEach(f -> porSede.put(f.get("sedeId").isNull() ? "sin" : f.get("sedeId").asText(),
				new BigDecimal(f.get("importe").asText()).setScale(2).toPlainString()));
		assertEquals(Map.of(String.valueOf(e.sA), "100.00", String.valueOf(e.sB), "75.00", "sin", "25.00"), porSede);
	}

	@Test
	void pendienteVencido_noCruzaEscuelas() throws Exception {
		JsonNode deE = leerEl("2026-03-06T10:00", tokenGestor);
		assertImporte("200.00", deE.get("total"));
		deE.get("masAntiguos").forEach(c -> assertTrue(c.get("productoAlumnoId").asLong() != otraVencido));

		JsonNode deOtra = leerEl("2026-03-06T10:00", tokenOtraGestor);
		assertImporte("99.00", deOtra.get("total"));
		assertEquals(1, deOtra.get("cargos").asLong());
		assertEquals(otraVencido, deOtra.get("masAntiguos").get(0).get("productoAlumnoId").asLong());
	}

	@Test
	void pendienteVencido_cargoFuturoNoCuenta() throws Exception {
		// El 31/12/2025, enero, febrero, marzo y abril de 2026 y los exámenes posteriores aún no han vencido.
		JsonNode enDiciembre = leerEl("2025-12-31T10:00", tokenGestor);
		assertImporte("25.00", enDiciembre.get("total"));
		assertEquals(Set.of(mSinMes), idsMasAntiguos(enDiciembre), "solo diciembre de 2025");
		// El 06/03/2026, abril (generada por adelantado) y el examen de junio siguen fuera.
		Set<Long> enMarzo = idsMasAntiguos(leerEl("2026-03-06T10:00", tokenGestor));
		assertTrue(!enMarzo.contains(mAbr) && !enMarzo.contains(exFutura) && !enMarzo.contains(mAnoSinMes));
		assertEquals(9, leerEl("2026-03-06T10:00", tokenGestor).get("cargos").asLong(), "ni abril, ni junio, ni 2027");
	}

	@Test
	void pendienteVencido_masAntiguosOrdenados() throws Exception {
		JsonNode r = leerEl("2026-03-06T10:00", tokenGestor);
		List<Long> ids = new ArrayList<>();
		r.get("masAntiguos").forEach(c -> ids.add(c.get("productoAlumnoId").asLong()));
		assertEquals(List.of(mSinMes, mEne, mInscripcion, mFeb, mPagadoNull), ids,
				"los cinco más antiguos por la fecha en que vencieron");
		JsonNode primero = r.get("masAntiguos").get(0);
		assertEquals("MENSUALIDAD", primero.get("tipo").asText());
		assertEquals("2025-12", primero.get("mes").asText());
		assertEquals("2025-12-06", primero.get("vencidoDesde").asText());
		assertImporte("25.00", primero.get("importe"));
		assertEquals(e.aSinSede, primero.get("alumnoId").asLong());
		assertEquals(5, r.get("diaLimiteMensualidad").asInt());
	}

	@Test
	void pendienteVencido_mensualidadMesEnCurso_dia5_noCuenta() throws Exception {
		JsonNode r = leerEl("2026-03-05T23:30", tokenGestor);
		assertImporte("150.00", r.get("total"));
		assertEquals(7, r.get("cargos").asLong(), "sin marzo ni el examen de mañana");
	}

	@Test
	void pendienteVencido_mensualidadMesEnCurso_dia6_cuenta() throws Exception {
		JsonNode r = leerEl("2026-03-06T00:30", tokenGestor);
		assertEquals(Map.of("2025-12", "25.00", "2026-01", "40.00", "2026-02", "85.00", "2026-03", "50.00"),
				filas(r.get("porMes"), "mes"));
	}

	@Test
	void pendienteVencido_derechoExamenElDiaDeLaConvocatoria_cuenta() throws Exception {
		JsonNode antes = leerEl("2026-03-05T12:00", tokenGestor);
		JsonNode ese = leerEl("2026-03-06T08:00", tokenGestor);
		assertEquals(Map.of("MENSUALIDAD", "110.00", "EXAMEN", "40.00"), filas(antes.get("porTipo"), "tipo"));
		assertEquals(Map.of("MENSUALIDAD", "140.00", "EXAMEN", "60.00"), filas(ese.get("porTipo"), "tipo"));
	}

	@Test
	void pendienteVencido_responsableSede_denegadoHastaE9() throws Exception {
		assertEquals(403, estado(get(RUTA), tokenResponsable), "el responsable de sede, hasta la E9");
		assertEquals(403, estado(head(RUTA), tokenResponsable));
		assertEquals(403, estado(get(RUTA), tokenFamilia));
		assertEquals(200, estado(get(RUTA), tokenGestor));
	}

	// ------------------------------------------------------------------ ayudantes

	/** Lee la ruta con «hoy» fijado en el {@code Clock} simulado (Europe/Madrid). */
	private JsonNode leerEl(String ahora, String token) throws Exception {
		when(reloj.instant()).thenReturn(LocalDateTime.parse(ahora).atZone(ValidacionRelojConfig.ZONA).toInstant());
		when(reloj.getZone()).thenReturn(ValidacionRelojConfig.ZONA);
		return leer(RUTA, token);
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

	private static Map<String, String> filas(JsonNode lista, String clave) {
		Map<String, String> filas = new HashMap<>();
		lista.forEach(f -> filas.put(f.get(clave).asText(), new BigDecimal(f.get("importe").asText())
				.setScale(2).toPlainString()));
		return filas;
	}

	private static Set<Long> idsMasAntiguos(JsonNode r) {
		Set<Long> ids = new HashSet<>();
		r.get("masAntiguos").forEach(c -> ids.add(c.get("productoAlumnoId").asLong()));
		return ids;
	}

	private static Set<Long> union(Set<Long> a, Set<Long> b) {
		Set<Long> u = new HashSet<>(a);
		u.addAll(b);
		return u;
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
