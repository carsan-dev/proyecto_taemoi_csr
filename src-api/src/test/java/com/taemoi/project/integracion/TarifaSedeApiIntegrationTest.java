package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
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
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.TarifaRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Entrega 6-4 (D6-30, D6-31) contra MySQL: API mínima de tarifas por sede. Escriben propietario y gestor; el responsable
 * de sede solo lee las de sus sedes; familia y profesor, 403. Validaciones (400), duplicado (409), borrado solo de meses
 * futuros en hora de Madrid (409) y aislamiento entre escuelas. Escuela de {@link EscuelaConSedesDePrueba}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TarifaSedeApiIntegrationTest extends MySqlIntegrationTest {

	private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;
	@Autowired TarifaRepository tarifas;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	String tokenGestor, tokenPropietario, tokenA, tokenAC, tokenFamilia, tokenProfesor, tokenGestorOrg1;
	long umbrete;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e64-api");
		TenantContext.ejecutarCon(e.org, () -> {
			DisciplinasDePrueba.tarifa(tarifas, "ADULTO", "35.00", null);
			DisciplinasDePrueba.tarifa(tarifas, "PADRES_HIJOS", "0.00", null);
		});
		jdbc.update("UPDATE tarifa SET importe_adulto = 28.00, importe_menor = 26.00 WHERE organizacion_id = ? "
				+ "AND codigo = 'PADRES_HIJOS'", e.org);
		umbrete = jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = 1 ORDER BY id LIMIT 1", Long.class);

		tokenGestor = e.token(jwtService, usuarios, e.gestor);
		tokenA = e.token(jwtService, usuarios, e.respA);
		tokenAC = e.token(jwtService, usuarios, e.respAC);
		tokenFamilia = e.token(jwtService, usuarios, e.cuentaFamilia(usuarios, "familiatarifas"));
		long profesor = e.cuentaFamilia(usuarios, "profesortarifas");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROFESOR')", profesor,
				e.org);
		tokenProfesor = e.token(jwtService, usuarios, profesor);
		long propietario = cuenta("propietario");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROPIETARIO')",
				propietario, e.org);
		tokenPropietario = e.token(jwtService, usuarios, propietario);
		long gestorOrg1 = cuenta("gestor-org1");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, 1, 'ADMINISTRADOR')",
				gestorOrg1);
		Usuario u = TenantContext.calcularCon(1L, () -> usuarios.findById(gestorOrg1).orElseThrow());
		tokenGestorOrg1 = jwtService.generateToken(u, 600_000, 1L);
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ permisos (D6-31)

	@Test
	void escribenPropietarioYGestor_familiaYProfesor403() throws Exception {
		assertEquals(201, estado(alta("ADULTO", e.sA, "30", null, null, "2030-01"), tokenGestor));
		assertEquals(201, estado(alta("ADULTO", e.sA, "31", null, null, "2030-02"), tokenPropietario));
		long id = idDe("ADULTO", e.sA, "2030-02");
		for (String token : List.of(tokenA, tokenAC, tokenFamilia, tokenProfesor)) {
			assertEquals(403, estado(alta("ADULTO", e.sA, "30", null, null, "2030-03"), token));
			assertEquals(403, estado(delete("/api/tarifas-sede/" + id), token));
		}
		for (String token : List.of(tokenFamilia, tokenProfesor)) {
			assertEquals(403, estado(get("/api/tarifas-sede"), token));
		}
		assertEquals(200, estado(get("/api/tarifas-sede"), tokenPropietario));
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM tarifa_sede WHERE organizacion_id = ? "
				+ "AND vigente_desde = '2030-03-01'", Long.class, e.org));
		assertEquals(204, estado(delete("/api/tarifas-sede/" + id), tokenPropietario));
		assertEquals(204, estado(delete("/api/tarifas-sede/" + idDe("ADULTO", e.sA, "2030-01")), tokenGestor));
	}

	@Test
	void responsable_leeSoloLasDeSusSedes() throws Exception {
		assertEquals(201, estado(alta("ADULTO", e.sA, "30", null, null, "2031-01"), tokenGestor));
		assertEquals(201, estado(alta("ADULTO", e.sB, "29", null, null, "2031-01"), tokenGestor));
		assertEquals(201, estado(alta("ADULTO", e.sC, "28", null, null, "2031-01"), tokenGestor));
		assertEquals(Set.of(e.sA), sedes(leer("/api/tarifas-sede", tokenA), "2031-01"));
		assertEquals(Set.of(e.sA, e.sC), sedes(leer("/api/tarifas-sede", tokenAC), "2031-01"));
		assertEquals(Set.of(e.sA, e.sB, e.sC), sedes(leer("/api/tarifas-sede", tokenGestor), "2031-01"));
		JsonNode fila = fila(leer("/api/tarifas-sede", tokenA), e.sA, "2031-01");
		assertEquals("ADULTO", fila.get("codigoTarifa").asText());
		assertEquals(30.0, fila.get("importe").asDouble());
		assertTrue(fila.get("importeAdulto").isNull());
	}

	// ------------------------------------------------------------------ validaciones (400) y duplicado (409)

	@Test
	void validaciones_400() throws Exception {
		assertEquals(400, estado(alta("NO_EXISTE", e.sA, "30", null, null, "2032-01"), tokenGestor));
		assertEquals(400, estado(alta("ADULTO", null, "30", null, null, "2032-01"), tokenGestor));
		assertEquals(400, estado(alta("ADULTO", 999_999_999L, "30", null, null, "2032-01"), tokenGestor));
		for (String mes : List.of("2032-13", "2032-00", "2032/01", "2032-1", "enero", "2032-01-01")) {
			assertEquals(400, estado(alta("ADULTO", e.sA, "30", null, null, mes), tokenGestor), mes);
		}
		assertEquals(400, estado(post("/api/tarifas-sede").contentType(MediaType.APPLICATION_JSON)
				.content("{\"codigoTarifa\":\"ADULTO\",\"sedeId\":" + e.sA + ",\"importe\":30}"), tokenGestor),
				"sin mes");
		for (String importe : List.of("-5", "-0.01", "30.001", "1000000")) {
			assertEquals(400, estado(alta("ADULTO", e.sA, importe, null, null, "2032-01"), tokenGestor), importe);
		}
		assertEquals(400, estado(post("/api/tarifas-sede").contentType(MediaType.APPLICATION_JSON)
				.content("{\"codigoTarifa\":\"ADULTO\",\"sedeId\":" + e.sA + ",\"vigenteDesde\":\"2032-01\"}"),
				tokenGestor), "sin importe");
		// PADRES_HIJOS cobra por rol: sin importeAdulto un PADRE pagaría el importe general (0 €).
		assertEquals(400, estado(alta("PADRES_HIJOS", e.sA, "20", null, "22", "2032-01"), tokenGestor));
		assertEquals(400, estado(alta("PADRES_HIJOS", e.sA, "20", "25", null, "2032-01"), tokenGestor));
		assertEquals(400, estado(alta("ADULTO", e.sA, "30", "25", null, "2032-01"), tokenGestor),
				"la tarifa base no cobra por rol");
		MvcResult r = peticion(alta("ADULTO", e.sA, "-1", null, null, "2032-01"), tokenGestor);
		assertTrue(json.readTree(r.getResponse().getContentAsString()).has("mensaje"), "formato de error de siempre");
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM tarifa_sede WHERE organizacion_id = ? "
				+ "AND vigente_desde = '2032-01-01'", Long.class, e.org));

		assertEquals(201, estado(alta("PADRES_HIJOS", e.sA, "20", "25", "22", "2032-01"), tokenGestor));
		JsonNode creada = fila(leer("/api/tarifas-sede", tokenGestor), e.sA, "2032-01");
		assertEquals(25.0, creada.get("importeAdulto").asDouble());
		assertEquals(22.0, creada.get("importeMenor").asDouble());
		assertEquals("2032-01-01", jdbc.queryForObject("SELECT CAST(vigente_desde AS CHAR) FROM tarifa_sede WHERE id = ?",
				String.class, creada.get("id").asLong()), "siempre el día 1 (D6-34)");
	}

	@Test
	void duplicado_409() throws Exception {
		assertEquals(201, estado(alta("ADULTO", e.sB, "30", null, null, "2033-01"), tokenGestor));
		MvcResult otra = peticion(alta("ADULTO", e.sB, "29", null, null, "2033-01"), tokenGestor);
		assertEquals(409, otra.getResponse().getStatus());
		assertTrue(json.readTree(otra.getResponse().getContentAsString()).has("mensaje"));
		assertEquals(201, estado(alta("ADULTO", e.sB, "29", null, null, "2033-02"), tokenGestor), "otro mes");
		assertEquals(201, estado(alta("ADULTO", e.sA, "29", null, null, "2033-01"), tokenGestor), "otra sede");
	}

	// ------------------------------------------------------------------ borrado: solo meses futuros (Madrid)

	@Test
	void borrar_soloMesesFuturos() throws Exception {
		YearMonth actual = YearMonth.now(MADRID);
		assertEquals(201, estado(alta("ADULTO", e.sC, "30", null, null, actual.toString()), tokenGestor));
		// Una fila de un mes pasado ya no se crea por la API (D6-37): se siembra por SQL, como un histórico.
		jdbc.update("""
				INSERT INTO tarifa_sede (organizacion_id, tarifa_id, sede_id, importe, vigente_desde)
				VALUES (?, (SELECT id FROM tarifa WHERE organizacion_id = ? AND codigo = 'ADULTO'), ?, 30.00, ?)""",
				e.org, e.org, e.sC, actual.minusMonths(1).atDay(1));
		assertEquals(201, estado(alta("ADULTO", e.sC, "30", null, null, actual.plusMonths(1).toString()),
				tokenGestor));
		assertEquals(409, estado(delete("/api/tarifas-sede/" + idDe("ADULTO", e.sC, actual.toString())),
				tokenGestor), "mes en curso");
		assertEquals(409, estado(delete("/api/tarifas-sede/" + idDe("ADULTO", e.sC,
				actual.minusMonths(1).toString())), tokenGestor), "pasada");
		long futura = idDe("ADULTO", e.sC, actual.plusMonths(1).toString());
		assertEquals(204, estado(delete("/api/tarifas-sede/" + futura), tokenGestor));
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM tarifa_sede WHERE id = ?", Long.class, futura));
		assertEquals(404, estado(delete("/api/tarifas-sede/" + futura), tokenGestor));
	}

	@Test
	void crear_mesPasado400_mesEnCurso201() throws Exception {
		YearMonth actual = YearMonth.now(MADRID);
		MvcResult pasado = peticion(alta("ADULTO", e.sA, "30", null, null, actual.minusMonths(1).toString()),
				tokenGestor);
		assertEquals(400, pasado.getResponse().getStatus());
		assertTrue(json.readTree(pasado.getResponse().getContentAsString()).get("mensaje").asText()
				.contains("meses pasados"));
		assertEquals(400, estado(alta("ADULTO", e.sA, "30", null, null, actual.minusYears(1).toString()),
				tokenGestor));
		assertEquals(201, estado(alta("ADULTO", e.sA, "30", null, null, actual.toString()), tokenGestor),
				"el mes en curso sí");
		assertEquals(409, estado(delete("/api/tarifas-sede/" + idDe("ADULTO", e.sA, actual.toString())),
				tokenGestor), "y después ya no se borra");
	}

	@Test
	void padresHijos_conImporteGeneralCero_comoElCatalogo() throws Exception {
		assertEquals(201, estado(alta("PADRES_HIJOS", e.sB, "0", "25", "22", "2035-01"), tokenGestor));
		JsonNode fila = fila(leer("/api/tarifas-sede", tokenGestor), e.sB, "2035-01");
		assertEquals("PADRES_HIJOS", fila.get("codigoTarifa").asText());
		assertEquals(0.0, fila.get("importe").asDouble());
		assertEquals(25.0, fila.get("importeAdulto").asDouble());
		assertEquals(22.0, fila.get("importeMenor").asDouble());
		assertEquals(201, estado(alta("PADRES_HIJOS", e.sB, "0.00", "0", "0", "2035-02"), tokenGestor),
				"0 también vale en los importes por rol");
	}

	// ------------------------------------------------------------------ aislamiento entre escuelas

	@Test
	void aislamiento_tarifaYSedeDeOtraEscuela_yFilasAjenas() throws Exception {
		// Sede de Moiskimdo y tarifa que solo existe en Moiskimdo (FAMILIAR): 400.
		assertEquals(400, estado(alta("ADULTO", umbrete, "30", null, null, "2034-01"), tokenGestor));
		assertEquals(400, estado(alta("FAMILIAR", e.sA, "30", null, null, "2034-01"), tokenGestor));
		assertEquals(201, estado(alta("ADULTO", e.sB, "30", null, null, "2034-06"), tokenGestor));
		long propia = idDe("ADULTO", e.sB, "2034-06");
		// Moiskimdo no la ve ni la borra; y su sede, desde la otra escuela, tampoco existe.
		JsonNode deOrg1 = leer("/api/tarifas-sede", tokenGestorOrg1);
		deOrg1.forEach(f -> assertTrue(f.get("id").asLong() != propia));
		assertEquals(404, estado(delete("/api/tarifas-sede/" + propia), tokenGestorOrg1));
		assertEquals(400, estado(alta("ADULTO", e.sB, "30", null, null, "2034-07"), tokenGestorOrg1));
		assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM tarifa_sede WHERE id = ?", Long.class, propia));
		assertEquals(e.org, jdbc.queryForObject("SELECT organizacion_id FROM tarifa_sede WHERE id = ?", Long.class,
				propia));
	}

	// ------------------------------------------------------------------ utilidades

	private MockHttpServletRequestBuilder alta(String codigo, Long sede, String importe, String adulto, String menor,
			String mes) {
		StringBuilder cuerpo = new StringBuilder("{\"codigoTarifa\":\"").append(codigo).append('"');
		if (sede != null) {
			cuerpo.append(",\"sedeId\":").append(sede);
		}
		cuerpo.append(",\"importe\":").append(importe);
		if (adulto != null) {
			cuerpo.append(",\"importeAdulto\":").append(adulto);
		}
		if (menor != null) {
			cuerpo.append(",\"importeMenor\":").append(menor);
		}
		cuerpo.append(",\"vigenteDesde\":\"").append(mes).append("\"}");
		return post("/api/tarifas-sede").contentType(MediaType.APPLICATION_JSON).content(cuerpo.toString());
	}

	private long idDe(String codigo, long sede, String mes) {
		return jdbc.queryForObject("""
				SELECT ts.id FROM tarifa_sede ts JOIN tarifa t ON t.id = ts.tarifa_id
				WHERE ts.organizacion_id = ? AND t.codigo = ? AND ts.sede_id = ? AND ts.vigente_desde = ?""", Long.class,
				e.org, codigo, sede, mes + "-01");
	}

	private static Set<Long> sedes(JsonNode lista, String mes) {
		Set<Long> sedes = new HashSet<>();
		lista.forEach(f -> {
			if (mes.equals(f.get("vigenteDesde").asText())) {
				sedes.add(f.get("sedeId").asLong());
			}
		});
		return sedes;
	}

	private static JsonNode fila(JsonNode lista, long sede, String mes) {
		for (JsonNode f : lista) {
			if (f.get("sedeId").asLong() == sede && mes.equals(f.get("vigenteDesde").asText())) {
				return f;
			}
		}
		throw new AssertionError("sin fila " + sede + " " + mes + " en " + lista);
	}

	private long cuenta(String nombre) {
		String email = "it-e64-" + nombre + "-" + e.marca + "@example.test";
		return TenantContext.calcularCon(1L, () -> {
			Usuario usuario = new Usuario();
			usuario.setNombre("Usuario");
			usuario.setApellidos("Prueba");
			usuario.setEmail(email);
			usuario.setContrasena("fake-Password-123");
			usuario.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(usuario).getId();
		});
	}

	private int estado(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return peticion(peticion, token).getResponse().getStatus();
	}

	private MvcResult peticion(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
	}

	private JsonNode leer(String ruta, String token) throws Exception {
		MvcResult r = peticion(get(ruta), token);
		assertEquals(200, r.getResponse().getStatus(), ruta + ": " + r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString());
	}
}
