package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDate;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.TarifaRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.utils.MensualidadUtils;

import jakarta.servlet.http.Cookie;

/**
 * Entrega 6-4 (D6-27 a D6-34) contra MySQL: las cinco generaciones de mensualidades y el alta cobran la tarifa de la sede
 * del alumno para el MES QUE SE COBRA. Escuela de {@link EscuelaConSedesDePrueba} (sedes A y B activas, C inactiva) con
 * la tarifa ADULTO a 35 € y una excepción de 30 € en la sede B desde enero de 2027 (creada con la API).
 *
 * <pre>
 * ana    sede B, ADULTO, 35 (base)          dic-2026: 35 · ene-2027: 30
 * beto   sede B, ADULTO, 25 (descuento)     25 siempre
 * eva    sede B, ADULTO, 32 (personalizada) 32 siempre; en ene-2027 se lista (D6-28)
 * carla  sede A, ADULTO, 35                 35 siempre (sin excepción)
 * dani   sin sede, ADULTO, 35               35 siempre
 * fede   sede B, sin tarifa, 35             35 siempre
 * padre  sede B, PADRES_HIJOS (0/28/26), PADRE, 28; excepción 20/25/22 → ene-2027: 25
 * gema   sede C (inactiva), ADULTO, 35      omitida en las masivas (D6-17)
 * </pre>
 * Cada prueba usa meses propios: no dependen del orden.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TarifaSedeGeneracionIntegrationTest extends MySqlIntegrationTest {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;
	@Autowired TarifaRepository tarifas;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	String tokenGestor;
	long adulto, padresHijos, infantil;
	long ana, beto, eva, carla, dani, fede, padre, gema;

	@BeforeAll
	void preparar() throws Exception {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e64-generacion");
		TenantContext.ejecutarCon(e.org, () -> {
			DisciplinasDePrueba.tarifa(tarifas, "ADULTO", "35.00", null);
			DisciplinasDePrueba.tarifa(tarifas, "PADRES_HIJOS", "0.00", null);
			DisciplinasDePrueba.tarifa(tarifas, "INFANTIL", "28.00", null);
		});
		adulto = tarifa("ADULTO");
		padresHijos = tarifa("PADRES_HIJOS");
		infantil = tarifa("INFANTIL");
		jdbc.update("UPDATE tarifa SET importe_adulto = 28.00, importe_menor = 26.00 WHERE id = ?", padresHijos);
		jdbc.update("INSERT INTO producto (concepto, precio, organizacion_id) VALUES ('MENSUALIDAD', 30.0, ?)", e.org);
		// Las inscripciones de la escuela de prueba, con la tarifa base.
		jdbc.update("UPDATE alumno_deporte SET tarifa_id = ?, cuantia_tarifa = 35.0 WHERE organizacion_id = ?", adulto,
				e.org);

		ana = alumnoCon("ANA", e.sB, adulto, 35.0, null);
		beto = alumnoCon("BETO", e.sB, adulto, 25.0, null);
		eva = alumnoCon("EVA", e.sB, adulto, 32.0, null);
		carla = alumnoCon("CARLA", e.sA, adulto, 35.0, null);
		dani = alumnoCon("DANI", null, adulto, 35.0, null);
		fede = alumnoCon("FEDE", e.sB, null, 35.0, null);
		padre = alumnoCon("PADRE", e.sB, padresHijos, 28.0, "PADRE");
		gema = alumnoCon("GEMA", e.sC, adulto, 35.0, null);

		tokenGestor = e.token(jwtService, usuarios, e.gestor);
		crearExcepcion("""
				{"codigoTarifa":"ADULTO","sedeId":%d,"importe":30.00,"vigenteDesde":"2027-01"}""".formatted(e.sB));
		crearExcepcion("""
				{"codigoTarifa":"PADRES_HIJOS","sedeId":%d,"importe":20,"importeAdulto":25,"importeMenor":22,
				 "vigenteDesde":"2027-01"}""".formatted(e.sB));
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ general (y el mes es el que se cobra)

	@Test
	void general_diciembre35_enero30_mismaSede_idempotente_yDiciembreEnFechaDeEnero() throws Exception {
		// Diciembre generado con fecha de asignación de enero: manda el mes que se cobra (35), no la fecha.
		JsonNode diciembre = generar(post("/api/productos-alumno/mensualidades/general?fechaAsignacion=2027-01-10"),
				"2026-12");
		assertEquals(Set.of("mensaje", "alumnosOmitidosSedeInactiva"), campos(diciembre), "diciembre: sin lista");
		assertEquals(1, diciembre.get("alumnosOmitidosSedeInactiva").asInt(), "gema, sede C inactiva");
		assertPrecios("2026-12", Map.of(ana, 35.0, beto, 25.0, eva, 32.0, carla, 35.0, dani, 35.0, fede, 35.0,
				padre, 28.0));

		JsonNode enero = generar(post("/api/productos-alumno/mensualidades/general"), "2027-01");
		assertPrecios("2027-01", Map.of(ana, 30.0, beto, 25.0, eva, 32.0, carla, 35.0, dani, 35.0, fede, 35.0,
				padre, 25.0));
		assertEquals(1, enero.get("alumnosOmitidosSedeInactiva").asInt());
		assertSoloEva(enero);
		assertEquals(e.sB, sedeDelCargo(ana, "2027-01"), "el cargo de 30 cae en la caja de la sede con excepción");
		assertEquals(e.sA, sedeDelCargo(carla, "2027-01"));
		assertNull(sedeDelCargo(dani, "2027-01"));
		assertEquals(0L, cargos(gema), "D6-17: sede inactiva, sin cargos");

		// Regenerar no cambia nada (D6-32): ni cargos nuevos ni precios.
		long antes = cargosDelMes("2027-01");
		JsonNode otraVez = generar(post("/api/productos-alumno/mensualidades/general"), "2027-01");
		assertEquals(Set.of("mensaje", "alumnosOmitidosSedeInactiva"), campos(otraVez), "nada creado: sin lista");
		assertEquals(antes, cargosDelMes("2027-01"));
		assertPrecios("2027-01", Map.of(ana, 30.0, beto, 25.0, eva, 32.0, padre, 25.0));
	}

	@Test
	void cargosExistentes_nuncaSeRecalculan() throws Exception {
		// Un cargo ya creado a 35 (p. ej. generado antes de crear la excepción) se queda a 35 al regenerar.
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, alumno_deporte_id, concepto, precio, cantidad, pagado,
				  fecha_asignacion, organizacion_id, sede_id)
				VALUES (?, (SELECT id FROM alumno_deporte WHERE alumno_id = ?), ?, 35.0, 1, 0, '2027-07-01', ?, ?)""",
				ana, ana, concepto("2027-07"), e.org, e.sB);
		generar(post("/api/productos-alumno/mensualidades/general"), "2027-07");
		assertPrecios("2027-07", Map.of(ana, 35.0, beto, 25.0, padre, 25.0));
		assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM producto_alumno WHERE alumno_id = ? AND concepto = ?",
				Long.class, ana, concepto("2027-07")));
	}

	// ------------------------------------------------------------------ por deporte y multi-deporte

	@Test
	void porDeporte_mesDelCobro() throws Exception {
		JsonNode noviembre = generar(post("/api/productos-alumno/mensualidades/deporte?deporte=TAEKWONDO"
				+ "&fechaAsignacion=2027-02-01"), "2026-11");
		assertEquals(Set.of("mensaje", "alumnosOmitidosSedeInactiva"), campos(noviembre));
		assertPrecios("2026-11", Map.of(ana, 35.0, eva, 32.0, padre, 28.0, carla, 35.0));
		JsonNode febrero = generar(post("/api/productos-alumno/mensualidades/deporte?deporte=TAEKWONDO"), "2027-02");
		assertPrecios("2027-02", Map.of(ana, 30.0, beto, 25.0, eva, 32.0, carla, 35.0, dani, 35.0, fede, 35.0,
				padre, 25.0));
		assertEquals(1, febrero.get("alumnosOmitidosSedeInactiva").asInt());
		assertSoloEva(febrero);
		assertEquals(e.sB, sedeDelCargo(ana, "2027-02"));
		assertEquals(0L, cargos(gema));
	}

	@Test
	void multiDeporte_mesDelCobro() throws Exception {
		JsonNode octubre = generar(post("/api/productos-alumno/mensualidades/multi-deporte"), "2026-10");
		assertEquals(Set.of("mensaje", "alumnosOmitidosSedeInactiva"), campos(octubre));
		assertPrecios("2026-10", Map.of(ana, 35.0, eva, 32.0, padre, 28.0));
		JsonNode marzo = generar(post("/api/productos-alumno/mensualidades/multi-deporte"), "2027-03");
		assertPrecios("2027-03", Map.of(ana, 30.0, beto, 25.0, eva, 32.0, carla, 35.0, dani, 35.0, fede, 35.0,
				padre, 25.0));
		assertSoloEva(marzo);
		assertEquals(e.sB, sedeDelCargo(ana, "2027-03"));
		assertEquals(0L, cargos(gema));
	}

	// ------------------------------------------------------------------ individuales

	@Test
	void individual_mesDelCobro_yListaSoloSiHayAlgo() throws Exception {
		JsonNode anaSeptiembre = generar(post("/api/productos-alumno/mensualidades/individual?alumnoId=" + ana
				+ "&fechaAsignacion=2027-04-02"), "2026-09");
		assertEquals(Set.of("mensaje"), campos(anaSeptiembre));
		JsonNode anaAbril = generar(post("/api/productos-alumno/mensualidades/individual?alumnoId=" + ana), "2027-04");
		assertEquals(Set.of("mensaje"), campos(anaAbril), "base resuelta: nada que revisar");
		JsonNode evaAbril = generar(post("/api/productos-alumno/mensualidades/individual?alumnoId=" + eva), "2027-04");
		JsonNode betoAbril = generar(post("/api/productos-alumno/mensualidades/individual?alumnoId=" + beto), "2027-04");
		assertEquals(Set.of("mensaje"), campos(betoAbril), "25 < 30: no se lista");
		assertEquals("Mensualidad individual creada correctamente.", evaAbril.get("mensaje").asText());
		assertSoloEva(evaAbril);
		assertPrecios("2026-09", Map.of(ana, 35.0));
		assertPrecios("2027-04", Map.of(ana, 30.0, eva, 32.0, beto, 25.0));
		assertEquals(e.sB, sedeDelCargo(ana, "2027-04"));
	}

	@Test
	void individualPorDeporte_mesDelCobro() throws Exception {
		JsonNode anaAgosto = generar(post("/api/productos-alumno/mensualidades/individual-deporte?deporte=TAEKWONDO"
				+ "&alumnoId=" + ana + "&fechaAsignacion=2027-05-02"), "2026-08");
		assertEquals(Set.of("mensaje"), campos(anaAgosto));
		generar(post("/api/productos-alumno/mensualidades/individual-deporte?deporte=TAEKWONDO&alumnoId=" + ana),
				"2027-05");
		generar(post("/api/productos-alumno/mensualidades/individual-deporte?deporte=TAEKWONDO&alumnoId=" + padre),
				"2027-05");
		JsonNode evaMayo = generar(post("/api/productos-alumno/mensualidades/individual-deporte?deporte=TAEKWONDO"
				+ "&alumnoId=" + eva), "2027-05");
		assertEquals("Mensualidad individual creada correctamente para TAEKWONDO.", evaMayo.get("mensaje").asText());
		assertSoloEva(evaMayo);
		assertPrecios("2026-08", Map.of(ana, 35.0));
		assertPrecios("2027-05", Map.of(ana, 30.0, padre, 25.0, eva, 32.0));
		assertEquals(e.sB, sedeDelCargo(ana, "2027-05"));
	}

	// ------------------------------------------------------------------ listado mensual sin cargos (D6-33)

	@Test
	void listadoMensual_sinCargoMuestraLaCuantiaResueltaParaEseMes() throws Exception {
		// Sin cargos de esos meses: la columna CUANTÍA enseña la cuantía esperada. En la sede B, las tres inscripciones con
		// la base (ana, aB y aGrupoA) pasan de 35 a 30 desde enero de 2027.
		assertEquals(0, treintas(listado("2026-06")), "junio de 2026: sin excepción");
		assertEquals(3, treintas(listado("2027-08")), "agosto de 2027: ana, aB y aGrupoA a 30");
	}

	// ------------------------------------------------------------------ filas sucesivas y cambio de sede

	@Test
	void dosFilasSucesivas_cadaMesLaVigente() throws Exception {
		TenantContext.ejecutarCon(e.org, () -> DisciplinasDePrueba.tarifa(tarifas, "SUCESIVA", "35.00", null));
		long sucesiva = alumnoCon("SUCESIVA", e.sB, tarifa("SUCESIVA"), 35.0, null);
		crearExcepcion("""
				{"codigoTarifa":"SUCESIVA","sedeId":%d,"importe":30,"vigenteDesde":"2027-01"}""".formatted(e.sB));
		crearExcepcion("""
				{"codigoTarifa":"SUCESIVA","sedeId":%d,"importe":28,"vigenteDesde":"2027-03"}""".formatted(e.sB));
		for (String mes : List.of("2026-12", "2027-01", "2027-02", "2027-03", "2027-04")) {
			generar(post("/api/productos-alumno/mensualidades/individual?alumnoId=" + sucesiva), mes);
		}
		assertPrecios("2026-12", Map.of(sucesiva, 35.0));
		assertPrecios("2027-01", Map.of(sucesiva, 30.0));
		assertPrecios("2027-02", Map.of(sucesiva, 30.0));
		assertPrecios("2027-03", Map.of(sucesiva, 28.0));
		assertPrecios("2027-04", Map.of(sucesiva, 28.0));
	}

	@Test
	void cambioDeSedePrincipal_elPrecioYLaSedeDelCargoSiguenALaSedeAlGenerar() throws Exception {
		TenantContext.ejecutarCon(e.org, () -> DisciplinasDePrueba.tarifa(tarifas, "TRASLADO", "35.00", null));
		long traslado = alumnoCon("TRASLADO", e.sA, tarifa("TRASLADO"), 35.0, null);
		crearExcepcion("""
				{"codigoTarifa":"TRASLADO","sedeId":%d,"importe":31,"vigenteDesde":"2027-01"}""".formatted(e.sB));
		generar(post("/api/productos-alumno/mensualidades/individual-deporte?deporte=TAEKWONDO&alumnoId=" + traslado),
				"2027-01");
		jdbc.update("UPDATE alumno SET sede_principal_id = ? WHERE id = ?", e.sB, traslado);
		generar(post("/api/productos-alumno/mensualidades/individual-deporte?deporte=TAEKWONDO&alumnoId=" + traslado),
				"2027-02");
		assertPrecios("2027-01", Map.of(traslado, 35.0));
		assertEquals(e.sA, sedeDelCargo(traslado, "2027-01"), "enero, en la sede A: sin excepción");
		assertPrecios("2027-02", Map.of(traslado, 31.0));
		assertEquals(e.sB, sedeDelCargo(traslado, "2027-02"), "febrero, ya en la sede B: la de B y en su caja");
	}

	// ------------------------------------------------------------------ alta (mes en curso)

	@Test
	void alta_mensualidadDelMesEnCursoConLaTarifaDeLaSede() throws Exception {
		LocalDate hoy = LocalDate.now();
		String mesActual = hoy.getYear() + "-" + "%02d".formatted(hoy.getMonthValue());
		// El alta usa el mes de la JVM y la API el de Madrid: en la frontera de mes (CI en UTC) no coinciden.
		org.junit.jupiter.api.Assumptions.assumeTrue(
				mesActual.equals(java.time.YearMonth.now(java.time.ZoneId.of("Europe/Madrid")).toString()));
		// Desde el mes en curso (D6-37: no se crean para meses pasados).
		crearExcepcion("""
				{"codigoTarifa":"INFANTIL","sedeId":%d,"importe":24,"vigenteDesde":"%s"}""".formatted(e.sB, mesActual));
		// El alta solo crea la mensualidad si la escuela ya generó la del mes (concepto sin sufijo).
		String sinSufijo = MensualidadUtils.formatearNombreMensualidad(mesActual);
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, concepto, precio, cantidad, pagado, fecha_asignacion, organizacion_id)
				VALUES (?, ?, 30.0, 1, 0, NOW(), ?)""", e.aNada, sinSufijo, e.org);

		long enB = alta("UNO", e.sB, 28.0);
		long enA = alta("DOS", e.sA, 28.0);
		long enBDescuento = alta("TRES", e.sB, 20.0);
		assertEquals(24.0, precio(enB, sinSufijo), "base en la sede B: la de la sede");
		assertEquals(e.sB, jdbc.queryForObject("SELECT sede_id FROM producto_alumno WHERE alumno_id = ? AND concepto = ?",
				Long.class, enB, sinSufijo));
		assertEquals(28.0, precio(enA, sinSufijo), "sede A: sin excepción");
		assertEquals(20.0, precio(enBDescuento, sinSufijo), "personalizada: tal cual");
	}

	// ------------------------------------------------------------------ utilidades

	private String listado(String mesAno) throws Exception {
		MvcResult r = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.get("/api/informes/listado-mensualidad-mensual?mesAno=" + mesAno)
				.cookie(new Cookie("jwt", tokenGestor))).andReturn();
		assertEquals(200, r.getResponse().getStatus());
		try (org.apache.pdfbox.pdmodel.PDDocument pdf = org.apache.pdfbox.pdmodel.PDDocument
				.load(r.getResponse().getContentAsByteArray())) {
			return new org.apache.pdfbox.text.PDFTextStripper().getText(pdf).replaceAll("\\s+", "");
		}
	}

	private static int treintas(String texto) {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?<![0-9])30[.,]00EUR").matcher(texto);
		int n = 0;
		while (m.find()) {
			n++;
		}
		return n;
	}

	private void assertSoloEva(JsonNode respuesta) {
		JsonNode lista = respuesta.get("alumnosCuantiaPersonalizadaSuperior");
		assertTrue(lista != null && lista.isArray(), String.valueOf(respuesta));
		assertEquals(1, lista.size(), String.valueOf(lista));
		JsonNode fila = lista.get(0);
		assertEquals(eva, fila.get("alumnoId").asLong());
		assertTrue(fila.get("nombre").asText().startsWith("EVA"), fila.toString());
		assertEquals("TAEKWONDO", fila.get("deporte").asText());
		assertEquals(32.0, fila.get("cuantia").asDouble());
		assertEquals(30.0, fila.get("cuantiaSede").asDouble());
		assertEquals(Set.of("alumnoId", "nombre", "deporte", "cuantia", "cuantiaSede"), campos(fila));
	}

	private void assertPrecios(String mesAno, Map<Long, Double> esperados) {
		for (Map.Entry<Long, Double> esperado : esperados.entrySet()) {
			List<Double> precios = jdbc.queryForList(
					"SELECT precio FROM producto_alumno WHERE alumno_id = ? AND concepto = ?", Double.class,
					esperado.getKey(), concepto(mesAno));
			assertEquals(List.of(esperado.getValue()), precios, "alumno " + esperado.getKey() + " en " + mesAno);
		}
	}

	private static String concepto(String mesAno) {
		return MensualidadUtils.formatearNombreMensualidad(mesAno) + " - TAEKWONDO";
	}

	private Long sedeDelCargo(long alumno, String mesAno) {
		return jdbc.queryForObject("SELECT sede_id FROM producto_alumno WHERE alumno_id = ? AND concepto = ?",
				Long.class, alumno, concepto(mesAno));
	}

	private long cargos(long alumno) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM producto_alumno WHERE alumno_id = ?", Long.class, alumno);
	}

	private long cargosDelMes(String mesAno) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM producto_alumno WHERE organizacion_id = ? AND concepto = ?",
				Long.class, e.org, concepto(mesAno));
	}

	private Double precio(long alumno, String concepto) {
		return jdbc.queryForObject("SELECT precio FROM producto_alumno WHERE alumno_id = ? AND concepto = ?",
				Double.class, alumno, concepto);
	}

	private long tarifa(String codigo) {
		return jdbc.queryForObject("SELECT id FROM tarifa WHERE organizacion_id = ? AND codigo = ?", Long.class, e.org,
				codigo);
	}

	private long alumnoCon(String nombre, Long sede, Long tarifa, Double cuantia, String rol) {
		long alumno = e.alumno(nombre + " " + e.marca, sede, true);
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
				  organizacion_id, fecha_alta, fecha_alta_inicial, disciplina_id, tarifa_id, cuantia_tarifa, rol_familiar)
				VALUES (TRUE, 0, ?, 0, 1, 0, ?, '2023-09-01', '2023-09-01', ?, ?, ?, ?)""", alumno, e.org, e.disciplina,
				tarifa, cuantia, rol);
		return alumno;
	}

	private void crearExcepcion(String cuerpo) throws Exception {
		MvcResult r = mvc.perform(post("/api/tarifas-sede").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
				.cookie(new Cookie("jwt", tokenGestor))).andReturn();
		assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
	}

	private long alta(String nombre, long sede, double cuantia) throws Exception {
		String cuerpo = """
				{"nombre":"ALTA %s","apellidos":"E64 %s","fechaNacimiento":"2014-03-04","direccion":"Calle 1",
				 "telefono":600000009,"autorizacionWeb":true,"sedePrincipalId":%d,
				 "deportesInicial":[{"deporte":"TAEKWONDO","fechaAlta":"2024-09-01","tipoTarifa":"INFANTIL",
				   "cuantiaTarifa":%s,"competidor":false,"tieneLicencia":false}]}"""
				.formatted(nombre, e.marca, sede, cuantia);
		MvcResult r = mvc.perform(multipart("/api/alumnos/crear").param("nuevo", cuerpo)
				.cookie(new Cookie("jwt", tokenGestor))).andReturn();
		assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
	}

	private JsonNode generar(MockHttpServletRequestBuilder peticion, String mesAno) throws Exception {
		MvcResult r = mvc.perform(peticion.contentType(MediaType.TEXT_PLAIN).content(mesAno)
				.cookie(new Cookie("jwt", tokenGestor))).andReturn();
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString());
	}

	private static Set<String> campos(JsonNode nodo) {
		Set<String> campos = new LinkedHashSet<>();
		for (Iterator<String> it = nodo.fieldNames(); it.hasNext();) {
			campos.add(it.next());
		}
		assertFalse(campos.isEmpty());
		return campos;
	}
}
