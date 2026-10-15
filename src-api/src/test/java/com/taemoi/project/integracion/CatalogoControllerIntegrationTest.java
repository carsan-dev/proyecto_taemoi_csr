package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Membresia;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.MembresiaRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.ContadorService;

import jakarta.servlet.http.Cookie;

/**
 * {@code GET /api/catalogo} y {@code GET /api/catalogo/publico} por HTTP real contra MySQL
 * (docs/multi-escuela/catalogo-frontend.md §1): forma del JSON de Taemoi con los datos de V30, aislamiento
 * de una segunda escuela sintética, tarifas solo para gestión (D14), anónimo, y cabeceras de caché (privada,
 * revalidación, {@code Vary: Cookie}, ETag/304). La base es compartida con otros tests: las comprobaciones
 * de Taemoi se limitan a las filas sembradas por las migraciones.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogoControllerIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String CLAVE = "Clave-Segura-123";
	private static final List<String> DISCIPLINAS_TAEMOI = List.of("TAEKWONDO", "KICKBOXING", "PILATES",
			"DEFENSA_PERSONAL_FEMENINA");

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired PasswordEncoder encoder;
	@Autowired UsuarioRepository usuarios;
	@Autowired MembresiaRepository membresias;
	@Autowired OrganizacionRepository organizaciones;
	@Autowired AlumnoRepository alumnos;
	@Autowired ContadorService contadores;

	@MockitoBean JavaMailSender mailSender;

	final String sufijo = UUID.randomUUID().toString().substring(0, 8);
	long org2;
	long sistemaOrg2;
	String tokenAdmin;
	String tokenManager;
	String tokenFamilia;
	String tokenAdminOtra;

	@BeforeAll
	void crearEscuelasYUsuarios() throws Exception {
		org2 = TenantContext.calcularCon(ORG1, () -> {
			Organizacion org = new Organizacion();
			org.setSlug("it-catalogo-" + sufijo);
			org.setNombre("Escuela catálogo " + sufijo);
			return organizaciones.save(org).getId();
		});
		catalogoSegundaEscuela();

		Usuario admin = crearUsuario("cat-admin-" + sufijo + "@example.test");
		dar(admin, ORG1, RolMembresia.PROPIETARIO);
		Usuario manager = crearUsuario("cat-manager-" + sufijo + "@example.test");
		dar(manager, ORG1, RolMembresia.ADMINISTRADOR);
		Usuario familia = crearUsuario("cat-familia-" + sufijo + "@example.test");
		alumnoConEmail(familia.getEmail());
		dar(familia, ORG1, RolMembresia.FAMILIA);
		Usuario adminOtra = crearUsuario("cat-admin-otra-" + sufijo + "@example.test");
		dar(adminOtra, org2, RolMembresia.PROPIETARIO);
		tokenAdmin = login(admin.getEmail());
		tokenManager = login(manager.getEmail());
		tokenFamilia = login(familia.getEmail());
		tokenAdminOtra = login(adminOtra.getEmail());
	}

	/** Token de la sesión tras cambiar a la escuela indicada ({@code POST /api/auth/organizacion}). */
	private String cambiarA(String token, long organizacion) throws Exception {
		MvcResult cambio = mvc.perform(post("/api/auth/organizacion").cookie(jwt(token))
				.contentType(MediaType.APPLICATION_JSON).content("{\"organizacionId\":" + organizacion + "}"))
				.andExpect(status().isOk())
				.andReturn();
		return json.readTree(cambio.getResponse().getContentAsString()).get("token").asText();
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ Taemoi

	@Test
	void taemoi_organizacionYDisciplinas() throws Exception {
		JsonNode cat = leer(get("/api/catalogo").cookie(jwt(tokenAdmin)));
		assertEquals(ORG1, cat.get("organizacion").get("id").asLong());
		assertTrue(cat.get("organizacion").get("comportamientoLegadoTaemoi").asBoolean());
		assertEquals(List.of("comportamientoLegadoTaemoi", "id"), campos(cat.get("organizacion")));

		List<String> codigos = textos(cat.get("disciplinas"), "codigo");
		assertEquals(DISCIPLINAS_TAEMOI, codigos.subList(0, 4), "orden, id: las cuatro de Taemoi primero");
		JsonNode kb = porCodigo(cat.get("disciplinas"), "KICKBOXING");
		assertEquals(List.of("activa", "codigo", "edadAdulto", "nombre", "orden", "permiteCompeticion",
				"requiereLicencia", "sistemaGradosId", "tieneMaterialExamen"), campos(kb));
		assertEquals("Kickboxing", kb.get("nombre").asText());
		assertEquals(15, kb.get("edadAdulto").asInt());
		assertTrue(kb.get("permiteCompeticion").asBoolean());
		assertTrue(kb.get("requiereLicencia").asBoolean());
		assertTrue(kb.get("tieneMaterialExamen").asBoolean());
		assertEquals(sistemaTaemoi("KICKBOXING"), kb.get("sistemaGradosId").asLong());
		JsonNode dpf = porCodigo(cat.get("disciplinas"), "DEFENSA_PERSONAL_FEMENINA");
		assertEquals("Defensa Personal Femenina", dpf.get("nombre").asText());
		assertTrue(dpf.get("sistemaGradosId").isNull());
		assertFalse(dpf.get("tieneMaterialExamen").asBoolean());
		assertFalse(dpf.get("permiteCompeticion").asBoolean());
	}

	@Test
	void taemoi_gradosConNombresYColoresDeV30() throws Exception {
		JsonNode cat = leer(get("/api/catalogo").cookie(jwt(tokenAdmin)));
		JsonNode tkd = sistema(cat, sistemaTaemoi("TAEKWONDO"));
		assertEquals(List.of("grados", "id", "nombre"), campos(tkd));
		JsonNode grados = tkd.get("grados");
		assertEquals(new ArrayList<>(CatalogoVisualMigracionIntegrationTest.ESPERADO.keySet()), textos(grados, "codigo"));
		for (JsonNode g : grados) {
			String[] esperado = CatalogoVisualMigracionIntegrationTest.ESPERADO.get(g.get("codigo").asText());
			assertEquals(esperado[0], g.get("nombre").asText());
			assertEquals(esperado[1], g.get("color1").asText());
			assertEquals(esperado[2], g.get("color2").isNull() ? null : g.get("color2").asText());
		}
		JsonNode amarilloNaranja = porCodigo(grados, "AMARILLO_NARANJA");
		assertEquals(List.of("aplicaA", "codigo", "color1", "color2", "nombre", "orden", "requiereExamen"),
				campos(amarilloNaranja), "sin meses mínimos, bloque de temario ni productos");
		assertEquals("MENORES", amarilloNaranja.get("aplicaA").asText());
		assertEquals(4, amarilloNaranja.get("orden").asInt());
		assertFalse(porCodigo(grados, "BLANCO").get("requiereExamen").asBoolean());

		JsonNode kbGrados = sistema(cat, sistemaTaemoi("KICKBOXING")).get("grados");
		JsonNode rojo = porCodigo(kbGrados, "ROJO");
		assertEquals("MARRÓN", rojo.get("nombre").asText());
		assertEquals("#8B4513", rojo.get("color1").asText());
		assertTrue(rojo.get("color2").isNull());
		List<String> kb = textos(kbGrados, "codigo");
		assertEquals("ROJO_NEGRO_3_PUM", kb.get(kb.size() - 1), "kickboxing: los PUM al final (orden de progresión)");
		assertEquals("NINGUNO", porCodigo(kbGrados, "ROJO_NEGRO_1_PUM").get("aplicaA").asText());
	}

	@Test
	void taemoi_tarifasEnElOrdenDeV30_conReglasDeTarifasService() throws Exception {
		for (String token : List.of(tokenAdmin, tokenManager)) {
			JsonNode tarifas = leer(get("/api/catalogo").cookie(jwt(token))).get("tarifas");
			List<String> codigos = textos(tarifas, "codigo");
			assertEquals(CatalogoVisualMigracionIntegrationTest.ORDEN_TAEMOI,
					codigos.stream().filter(CatalogoVisualMigracionIntegrationTest.ORDEN_TAEMOI::contains).toList());
			JsonNode padresHijos = porCodigo(tarifas, "PADRES_HIJOS");
			assertEquals(List.of("activa", "codigo", "disciplina", "importe", "importeAdulto", "importeMenor", "nombre",
					"orden", "requiereGrupoFamiliar", "requiereRolFamiliar"), campos(padresHijos));
			assertEquals("Padres e Hijos", padresHijos.get("nombre").asText());
			assertEquals("TAEKWONDO", padresHijos.get("disciplina").asText());
			assertEquals(0, padresHijos.get("importe").decimalValue().compareTo(java.math.BigDecimal.ZERO));
			assertEquals(0, padresHijos.get("importeAdulto").decimalValue().compareTo(new java.math.BigDecimal("28.00")));
			assertEquals(0, padresHijos.get("importeMenor").decimalValue().compareTo(new java.math.BigDecimal("26.00")));
			assertEquals(7, padresHijos.get("orden").asInt());
			assertTrue(padresHijos.get("requiereRolFamiliar").asBoolean());
			assertFalse(padresHijos.get("requiereGrupoFamiliar").asBoolean());
			assertTrue(porCodigo(tarifas, "HERMANOS").get("requiereGrupoFamiliar").asBoolean());
			assertEquals("KICKBOXING", porCodigo(tarifas, "KICKBOXING").get("disciplina").asText());
			assertEquals(1, porCodigo(tarifas, "ADULTO").get("orden").asInt());
		}
	}

	/** D14: la familia (ROLE_USER) recibe el catálogo sin tarifas. */
	@Test
	void familia_sinTarifas() throws Exception {
		JsonNode cat = leer(get("/api/catalogo").cookie(jwt(tokenFamilia)));
		assertTrue(cat.get("tarifas").isArray());
		assertEquals(0, cat.get("tarifas").size());
		assertEquals(DISCIPLINAS_TAEMOI, textos(cat.get("disciplinas"), "codigo").subList(0, 4));
		assertFalse(cat.get("sistemasGrados").isEmpty());
	}

	/** Las categorías de Taemoi llevan el código de su disciplina (o nulo); nunca las de otra escuela. */
	@Test
	void taemoi_categoriasSoloDeTaemoi() throws Exception {
		JsonNode categorias = leer(get("/api/catalogo").cookie(jwt(tokenAdmin))).get("categorias");
		for (JsonNode c : categorias) {
			assertNotEquals("IT-ABS-" + sufijo, c.get("codigo").asText(null));
			assertTrue(c.get("disciplina").isNull() || !c.get("disciplina").asText().startsWith("JUDO"), c.toString());
		}
	}

	// ------------------------------------------------------------------ segunda escuela

	@Test
	void segundaEscuela_soloVeSuCatalogo() throws Exception {
		JsonNode cat = leer(get("/api/catalogo").cookie(jwt(tokenAdminOtra)));
		assertEquals(org2, cat.get("organizacion").get("id").asLong());
		assertFalse(cat.get("organizacion").get("comportamientoLegadoTaemoi").asBoolean());
		assertEquals(List.of("JUDO", "YOGA"), textos(cat.get("disciplinas"), "codigo"),
				"todas, también las inactivas, por orden");
		assertFalse(porCodigo(cat.get("disciplinas"), "YOGA").get("activa").asBoolean());
		assertEquals(1, cat.get("sistemasGrados").size());
		JsonNode sistema = cat.get("sistemasGrados").get(0);
		assertEquals(sistemaOrg2, sistema.get("id").asLong());
		assertEquals(List.of("BLANCO_JUDO", "MARRON_JUDO"), textos(sistema.get("grados"), "codigo"),
				"por orden, sin los de orden nulo");
		assertEquals("#FAFAFA", sistema.get("grados").get(0).get("color1").asText());
		assertEquals(List.of("IT-INF-" + sufijo, "IT-ABS-" + sufijo), textos(cat.get("categorias"), "codigo"));
		JsonNode absoluto = cat.get("categorias").get(1);
		assertEquals(List.of("codigo", "disciplina", "edadMax", "edadMin", "nombre", "orden"), campos(absoluto));
		assertEquals("JUDO", absoluto.get("disciplina").asText());
		assertEquals(18, absoluto.get("edadMin").asInt());
		assertTrue(absoluto.get("edadMax").isNull());
		assertEquals(List.of("BONO", "JUDO_MES"), textos(cat.get("tarifas"), "codigo"), "por orden, no por id");
		assertTrue(cat.get("tarifas").get(0).get("disciplina").isNull());
		String texto = cat.toString();
		for (String ajeno : List.of("TAEKWONDO", "MARRÓN", "PADRES_HIJOS", "Defensa Personal Femenina")) {
			assertFalse(texto.contains(ajeno), "fuga de Taemoi: " + ajeno);
		}
	}

	@Test
	void taemoi_noVeNadaDeLaSegundaEscuela() throws Exception {
		String texto = leer(get("/api/catalogo").cookie(jwt(tokenAdmin))).toString();
		for (String ajeno : List.of("JUDO", "YOGA", "BONO", "Blanco judo", "IT-ABS-" + sufijo)) {
			assertFalse(texto.contains(ajeno), "fuga de la segunda escuela: " + ajeno);
		}
		JsonNode publico = leer(get("/api/catalogo/publico"));
		assertFalse(publico.toString().contains("JUDO"));
	}

	/**
	 * Propietario (ROLE_ADMIN) en Taemoi y familia (ROLE_USER) en la segunda escuela, con la sesión en la
	 * segunda: manda el rol de la escuela de la sesión (sin tarifas) y solo se ven filas de esa escuela.
	 */
	@Test
	void usuarioMultiEscuela_enLaSegundaComoFamilia_sinTarifasYSoloSusFilas() throws Exception {
		Usuario multi = crearUsuario("cat-multi-" + sufijo + "@example.test");
		dar(multi, ORG1, RolMembresia.PROPIETARIO);
		alumnoConEmail(org2, multi.getEmail());
		dar(multi, org2, RolMembresia.FAMILIA);
		String tokenOrg2 = cambiarA(login(multi.getEmail()), org2);

		JsonNode cat = leer(get("/api/catalogo").cookie(jwt(tokenOrg2)));
		assertEquals(org2, cat.get("organizacion").get("id").asLong());
		assertTrue(cat.get("tarifas").isArray());
		assertEquals(0, cat.get("tarifas").size(), "familia en la escuela de la sesión: sin tarifas (D14)");
		assertEquals(List.of("JUDO", "YOGA"), textos(cat.get("disciplinas"), "codigo"));
		assertEquals(List.of(sistemaOrg2), List.of(cat.get("sistemasGrados").get(0).get("id").asLong()));
		assertEquals(1, cat.get("sistemasGrados").size());
		assertEquals(List.of("IT-INF-" + sufijo, "IT-ABS-" + sufijo), textos(cat.get("categorias"), "codigo"));
		String texto = cat.toString();
		for (String ajeno : List.of("TAEKWONDO", "MARRÓN", "PADRES_HIJOS", "Defensa Personal Femenina")) {
			assertFalse(texto.contains(ajeno), "fuga de Taemoi: " + ajeno);
		}

		// La misma persona en Taemoi (propietaria) sí recibe las tarifas.
		String tokenOrg1 = cambiarA(tokenOrg2, ORG1);
		assertFalse(leer(get("/api/catalogo").cookie(jwt(tokenOrg1))).get("tarifas").isEmpty());
	}

	// ------------------------------------------------------------------ público y anónimo

	/** Solo es público {@code /api/catalogo/publico} exacto: con barra final u otra ruta debajo, 401. */
	@Test
	void anonimo_variantesDeLaRutaPublica401() throws Exception {
		mvc.perform(get("/api/catalogo/publico/")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/catalogo/publico/x")).andExpect(status().isUnauthorized());
	}

	@Test
	void anonimo_catalogoCompleto401() throws Exception {
		mvc.perform(get("/api/catalogo")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/catalogo/otra-cosa")).andExpect(status().isUnauthorized());
	}

	@Test
	void anonimo_publico_soloDisciplinasActivasDeLaEscuelaPorDefecto() throws Exception {
		JsonNode publico = leer(get("/api/catalogo/publico"));
		assertEquals(List.of("disciplinas"), campos(publico));
		assertEquals(DISCIPLINAS_TAEMOI, textos(publico.get("disciplinas"), "codigo").subList(0, 4));
		for (JsonNode d : publico.get("disciplinas")) {
			assertEquals(List.of("codigo", "nombre", "orden"), campos(d));
		}
		assertEquals("Defensa Personal Femenina",
				porCodigo(publico.get("disciplinas"), "DEFENSA_PERSONAL_FEMENINA").get("nombre").asText());
	}

	/** Con sesión, /publico es el de la escuela de la sesión (como cualquier petición): solo las activas. */
	@Test
	void publico_conSesionDeLaSegundaEscuela() throws Exception {
		JsonNode publico = leer(get("/api/catalogo/publico").cookie(jwt(tokenAdminOtra)));
		assertEquals(List.of("JUDO"), textos(publico.get("disciplinas"), "codigo"));
	}

	// ------------------------------------------------------------------ cabeceras

	@Test
	void cabeceras_privadas_revalidacion_varyCookie_etag304() throws Exception {
		for (MockHttpServletRequestBuilder peticion : List.of(get("/api/catalogo").cookie(jwt(tokenAdmin)),
				get("/api/catalogo/publico"))) {
			MvcResult r = mvc.perform(peticion).andExpect(status().isOk()).andReturn();
			String cacheControl = r.getResponse().getHeader(HttpHeaders.CACHE_CONTROL);
			assertNotNull(cacheControl);
			assertTrue(cacheControl.contains("private") && cacheControl.contains("no-cache"), cacheControl);
			assertFalse(cacheControl.contains("public"), cacheControl);
			assertTrue(r.getResponse().getHeaders(HttpHeaders.VARY).stream().anyMatch(v -> v.contains("Cookie")),
					String.valueOf(r.getResponse().getHeaders(HttpHeaders.VARY)));
			String etag = r.getResponse().getHeader(HttpHeaders.ETAG);
			assertNotNull(etag);
		}
		String etag = mvc.perform(get("/api/catalogo").cookie(jwt(tokenAdmin))).andReturn().getResponse()
				.getHeader(HttpHeaders.ETAG);
		mvc.perform(get("/api/catalogo").cookie(jwt(tokenAdmin)).header(HttpHeaders.IF_NONE_MATCH, etag))
				.andExpect(status().isNotModified());
		String etagOtra = mvc.perform(get("/api/catalogo").cookie(jwt(tokenAdminOtra))).andReturn().getResponse()
				.getHeader(HttpHeaders.ETAG);
		assertNotEquals(etag, etagOtra, "otra escuela, otro contenido, otro ETag");
		mvc.perform(get("/api/catalogo").cookie(jwt(tokenAdminOtra)).header(HttpHeaders.IF_NONE_MATCH, etag))
				.andExpect(status().isOk());
		String etagFamilia = mvc.perform(get("/api/catalogo").cookie(jwt(tokenFamilia))).andReturn().getResponse()
				.getHeader(HttpHeaders.ETAG);
		assertNotEquals(etag, etagFamilia, "sin tarifas, otro ETag");
	}

	/** El filtro de ETag solo actúa en las dos rutas del catálogo: otro endpoint de la API no lleva ETag. */
	@Test
	void otroEndpointDeLaApi_sinEtag() throws Exception {
		MvcResult r = mvc.perform(get("/api/auth/organizaciones").cookie(jwt(tokenAdmin))).andExpect(status().isOk())
				.andReturn();
		assertNull(r.getResponse().getHeader(HttpHeaders.ETAG), "ETag fuera de /api/catalogo");
	}

	// ---------------------------------------------------------------------------------

	private void catalogoSegundaEscuela() {
		jdbc.update("INSERT INTO sistema_grados (organizacion_id, nombre, plantilla_origen) VALUES (?, 'Judo', NULL)", org2);
		sistemaOrg2 = jdbc.queryForObject("SELECT id FROM sistema_grados WHERE organizacion_id = ?", Long.class, org2);
		jdbc.update("""
				INSERT INTO disciplina (organizacion_id, codigo, nombre, sistema_grados_id, permite_competicion,
				  requiere_licencia, edad_adulto, orden, activa)
				VALUES (?, 'YOGA', 'Yoga', NULL, FALSE, FALSE, 16, 2, FALSE),
				       (?, 'JUDO', 'Judo', ?, TRUE, FALSE, 18, 1, TRUE)""", org2, org2, sistemaOrg2);
		jdbc.update("""
				INSERT INTO grado (organizacion_id, sistema_grados_id, codigo, nombre, orden, color_1, color_2, aplica_a)
				VALUES (?, ?, 'MARRON_JUDO', 'Marrón judo', 2, '#8B4513', NULL, 'TODOS'),
				       (?, ?, 'BLANCO_JUDO', 'Blanco judo', 1, '#FAFAFA', NULL, 'TODOS'),
				       (?, ?, 'SIN_ORDEN', 'Sin orden', NULL, NULL, NULL, 'TODOS')""",
				org2, sistemaOrg2, org2, sistemaOrg2, org2, sistemaOrg2);
		long judo = jdbc.queryForObject("SELECT id FROM disciplina WHERE organizacion_id = ? AND codigo = 'JUDO'",
				Long.class, org2);
		jdbc.update("""
				INSERT INTO categoria (organizacion_id, nombre, codigo, disciplina_id, edad_min, edad_max, orden)
				VALUES (?, ?, ?, ?, 18, NULL, 2), (?, ?, ?, ?, NULL, 11, 1)""",
				org2, "Absoluto " + sufijo, "IT-ABS-" + sufijo, judo, org2, "Infantil " + sufijo, "IT-INF-" + sufijo, judo);
		jdbc.update("""
				INSERT INTO tarifa (organizacion_id, disciplina_id, codigo, nombre, importe, activa, orden)
				VALUES (?, ?, 'JUDO_MES', 'Judo mensual', 40.00, TRUE, 2), (?, NULL, 'BONO', 'Bono', 10.00, TRUE, 1)""",
				org2, judo, org2);
	}

	private long sistemaTaemoi(String disciplina) {
		return jdbc.queryForObject("SELECT sistema_grados_id FROM disciplina WHERE organizacion_id = 1 AND codigo = ?",
				Long.class, disciplina);
	}

	private JsonNode leer(MockHttpServletRequestBuilder peticion) throws Exception {
		MvcResult r = mvc.perform(peticion).andExpect(status().isOk()).andReturn();
		return json.readTree(r.getResponse().getContentAsByteArray());
	}

	private static JsonNode sistema(JsonNode cat, long id) {
		for (JsonNode s : cat.get("sistemasGrados")) {
			if (s.get("id").asLong() == id) {
				return s;
			}
		}
		throw new AssertionError("sin sistema " + id + ": " + cat.get("sistemasGrados"));
	}

	private static JsonNode porCodigo(JsonNode lista, String codigo) {
		for (JsonNode n : lista) {
			if (codigo.equals(n.get("codigo").asText())) {
				return n;
			}
		}
		throw new AssertionError("sin " + codigo + ": " + lista);
	}

	private static List<String> textos(JsonNode lista, String campo) {
		List<String> r = new ArrayList<>();
		lista.forEach(n -> r.add(n.get(campo).asText()));
		return r;
	}

	private static List<String> campos(JsonNode nodo) {
		List<String> r = new ArrayList<>();
		nodo.fieldNames().forEachRemaining(r::add);
		return r.stream().sorted().toList();
	}

	private void alumnoConEmail(String email) {
		alumnoConEmail(ORG1, email);
	}

	private void alumnoConEmail(long organizacion, String email) {
		TenantContext.ejecutarCon(organizacion, () -> {
			Alumno alumno = new Alumno();
			alumno.setNombre("Familia " + sufijo);
			alumno.setApellidos("PRUEBA CATALOGO");
			alumno.setNumeroExpediente(contadores.siguienteNumeroExpediente());
			alumno.setFechaNacimiento(Date.valueOf(LocalDate.now().minusYears(10)));
			alumno.setDireccion("Calle Prueba 1");
			alumno.setTelefono("600000000");
			alumno.setActivo(true);
			alumno.setAutorizacionWeb(true);
			alumno.setEmail(email);
			alumnos.save(alumno);
		});
	}

	private String login(String email) throws Exception {
		MvcResult resultado = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return json.readTree(resultado.getResponse().getContentAsString()).get("token").asText();
	}

	private static Cookie jwt(String token) {
		return new Cookie("jwt", token);
	}

	private Usuario crearUsuario(String email) {
		return TenantContext.calcularCon(ORG1, () -> {
			Usuario usuario = new Usuario();
			usuario.setNombre("Usuario");
			usuario.setApellidos("Prueba");
			usuario.setEmail(email);
			usuario.setContrasena(encoder.encode(CLAVE));
			usuario.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(usuario);
		});
	}

	private Membresia dar(Usuario usuario, Long organizacionId, RolMembresia rol) {
		return TenantContext.calcularCon(ORG1,
				() -> membresias.save(new Membresia(usuario.getId(), organizacionId, rol)));
	}
}
