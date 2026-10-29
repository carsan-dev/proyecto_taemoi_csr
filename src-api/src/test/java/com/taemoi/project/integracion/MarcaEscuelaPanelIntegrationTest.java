package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.marca.ReglaContraste;
import com.taemoi.project.marca.TemaEscuela;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza 8b-T (fase3-e8b-plan.md §4 y §7), por HTTP real contra MySQL con la cadena de filtros completa: la marca de la
 * escuela ACTIVA ({@code GET /api/escuela/marca} y {@code /logo}, solo gestión), la escritura del color por el
 * superadmin ({@code PUT /api/plataforma/escuelas/{id}/marca}), su registro en la escuela DESTINO sin el valor del color,
 * y el nombre visible de la sesión en {@code GET /api/auth/permisos}.
 *
 * <p>Escuela A sintética ({@link EscuelaConSedesDePrueba}: gestor, responsable de sede con familia) y escuela B mínima;
 * una cuenta con gestión en las dos. Cada test prepara lo suyo y deja el color de su escuela como estaba; Moiskimdo
 * (organización 1) solo se lee. Datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MarcaEscuelaPanelIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String CLAVE = "Clave-Segura-123";
	/** Resumen de la fila genérica del filtro HTTP ({@code AuditoriaServiceImpl#construirResumen}). */
	private static final String RESUMEN_GENERICO = "^(CREATE|UPDATE|DELETE|READ|OPERACION) \\S+( #\\d+)? -> \\d+$";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;
	@Autowired PasswordEncoder encoder;

	@MockitoBean JavaMailSender mailSender;

	@Value("${app.imagenes.directorio.windows}") String raizImagenes;

	EscuelaConSedesDePrueba e;
	long orgB, propietarioA, profesorA, familiaA, dosEscuelas, gestorOrg1, familiaProfesorA, profesorDosEscuelas;
	String tGestorA, tPropietarioA, tProfesorA, tFamiliaA, tResponsableA, tDosEnA, tDosEnB, tGestorOrg1,
			tFamiliaProfesorA, tProfesorEnB;
	Usuario superadmin;
	Cookie cookieSuperadmin;

	@BeforeAll
	void preparar() throws Exception {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-8bt");
		String slugB = "it-8bt-b-" + e.marca;
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES (?, 'Escuela Ficticia Marca B')", slugB);
		orgB = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = ?", Long.class, slugB);

		propietarioA = e.cuenta(usuarios, "propietario-8bt");
		membresia(e.org, propietarioA, "PROPIETARIO");
		profesorA = e.cuenta(usuarios, "profesor-8bt");
		membresia(e.org, profesorA, "PROFESOR");
		familiaA = e.cuentaFamilia(usuarios, "familia-8bt");
		dosEscuelas = e.cuenta(usuarios, "dos-escuelas-8bt");
		membresia(e.org, dosEscuelas, "ADMINISTRADOR");
		membresia(orgB, dosEscuelas, "ADMINISTRADOR");
		gestorOrg1 = e.cuenta(usuarios, "gestor-org1-8bt");
		membresia(ORG1, gestorOrg1, "ADMINISTRADOR");
		// Pieza 8b-3o: familia + profesor (entra como familia) y un profesor de las dos escuelas (sesión en B).
		familiaProfesorA = e.cuentaFamilia(usuarios, "familia-profesor-8bt");
		membresia(e.org, familiaProfesorA, "PROFESOR");
		profesorDosEscuelas = e.cuenta(usuarios, "profesor-dos-escuelas-8bt");
		membresia(e.org, profesorDosEscuelas, "PROFESOR");
		membresia(orgB, profesorDosEscuelas, "PROFESOR");

		tGestorA = e.token(jwtService, usuarios, e.gestor);
		tPropietarioA = e.token(jwtService, usuarios, propietarioA);
		tProfesorA = e.token(jwtService, usuarios, profesorA);
		tFamiliaA = e.token(jwtService, usuarios, familiaA);
		tResponsableA = e.token(jwtService, usuarios, e.respA);
		tDosEnA = e.token(jwtService, usuarios, dosEscuelas);
		tDosEnB = token(dosEscuelas, orgB);
		tGestorOrg1 = token(gestorOrg1, ORG1);
		tFamiliaProfesorA = e.token(jwtService, usuarios, familiaProfesorA);
		tProfesorEnB = token(profesorDosEscuelas, orgB);

		superadmin = crearSuperadmin("it-8bt-superadmin-" + e.marca + "@example.test");
		cookieSuperadmin = new Cookie("jwt", login(superadmin.getEmail()));
	}

	@AfterAll
	void retirar() {
		jdbc.update("UPDATE membresia SET activa = FALSE WHERE usuario_id = ? AND organizacion_id = 1", gestorOrg1);
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ lectura

	@Test
	void marca_deLaEscuelaActiva() throws Exception {
		conColor(e.org, "#3E9B4F", () -> {
			JsonNode marca = leer(get("/api/escuela/marca"), tGestorA, 200);
			assertEquals("Escuela IT sedes", marca.get("nombre").asText());
			assertEquals("NINGUNO", marca.get("logo").asText());
			assertTrue(marca.get("logoVersion").isNull());
			assertTema(ReglaContraste.tema("#3E9B4F"), marca.get("tema"));
			assertEquals(ReglaContraste.TINTA, marca.get("tema").get("sobrePrimario").asText());
			// También el propietario.
			assertEquals("#3E9B4F", leer(get("/api/escuela/marca"), tPropietarioA, 200).get("tema").get("primario")
					.asText());
		});
	}

	@Test
	void marca_otraEscuelaNoSeVe() throws Exception {
		conColor(e.org, "#3E9B4F", () -> conColor(orgB, "#1B2B2E", () -> {
			JsonNode enA = leer(get("/api/escuela/marca"), tDosEnA, 200);
			JsonNode enB = leer(get("/api/escuela/marca"), tDosEnB, 200);
			assertEquals("Escuela IT sedes", enA.get("nombre").asText());
			assertEquals("#3E9B4F", enA.get("tema").get("primario").asText());
			assertEquals("Escuela Ficticia Marca B", enB.get("nombre").asText());
			assertEquals("#1B2B2E", enB.get("tema").get("primario").asText());
			assertEquals(ReglaContraste.BLANCO, enB.get("tema").get("sobrePrimario").asText());
		}));
	}

	@Test
	void marca_sinColor_porDefecto1A2230() throws Exception {
		conColor(orgB, null, () -> {
			JsonNode marca = leer(get("/api/escuela/marca"), tDosEnB, 200);
			assertEquals("#1A2230", marca.get("tema").get("primario").asText());
			assertEquals(ReglaContraste.BLANCO, marca.get("tema").get("sobrePrimario").asText());
			assertFalse(marca.get("tema").get("ajustado").asBoolean());
		});
	}

	@Test
	void moiskimdo_sinLogoSubido_LEGADO() throws Exception {
		assertNull(jdbc.queryForObject("SELECT logo_imagen_id FROM organizacion WHERE id = 1", Long.class),
				"Moiskimdo empieza sin logo subido");
		String color = jdbc.queryForObject("SELECT color_primario FROM organizacion WHERE id = 1", String.class);
		JsonNode marca = leer(get("/api/escuela/marca"), tGestorOrg1, 200);
		assertEquals("LEGADO", marca.get("logo").asText());
		assertTrue(marca.get("logoVersion").isNull());
		assertTema(ReglaContraste.tema(color), marca.get("tema"));
		// Sin logo subido no hay PNG: el panel usa el SVG de siempre de assets.
		mvc.perform(get("/api/escuela/logo").cookie(new Cookie("jwt", tGestorOrg1))).andExpect(status().isNotFound());
	}

	/**
	 * Gestión y, desde la pieza 8b-3o (decisión 326), la sesión solo-profesor (solo leer). La familia, también la que
	 * además es profesor (entra como familia), el responsable de sede y el superadmin sin membresía, 403.
	 */
	@Test
	void marca_soloGestionYSesionSoloProfesor() throws Exception {
		for (String token : List.of(tFamiliaA, tResponsableA, tFamiliaProfesorA)) {
			for (String ruta : List.of("/api/escuela/marca", "/api/escuela/logo")) {
				mvc.perform(get(ruta).cookie(new Cookie("jwt", token))).andExpect(status().isForbidden());
			}
		}
		for (String ruta : List.of("/api/escuela/marca", "/api/escuela/logo")) {
			mvc.perform(get(ruta).cookie(cookieSuperadmin)).andExpect(status().isForbidden());
			mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
		}
	}

	/** Pieza 8b-3o (decisión 326): la sesión solo-profesor lee la marca de SU escuela activa, y nada más bajo /api/escuela. */
	@Test
	void marca_sesionSoloProfesor_soloLectura_deSuEscuelaActiva() throws Exception {
		conColor(e.org, "#3E9B4F", () -> conColor(orgB, "#1B2B2E", () -> {
			JsonNode enA = leer(get("/api/escuela/marca"), tProfesorA, 200);
			assertEquals("Escuela IT sedes", enA.get("nombre").asText());
			assertEquals("NINGUNO", enA.get("logo").asText());
			assertTrue(enA.get("logoVersion").isNull());
			assertTema(ReglaContraste.tema("#3E9B4F"), enA.get("tema"));
			assertEquals(Set.of("nombre", "logo", "logoVersion", "tema"), campos(enA), "solo la marca");
			mvc.perform(head("/api/escuela/marca").cookie(new Cookie("jwt", tProfesorA))).andExpect(status().isOk());

			// La misma cuenta, profesor también en B y con la sesión en B: la de B.
			JsonNode enB = leer(get("/api/escuela/marca"), tProfesorEnB, 200);
			assertEquals("Escuela Ficticia Marca B", enB.get("nombre").asText());
			assertEquals("#1B2B2E", enB.get("tema").get("primario").asText());
		}));
		// Sin logo subido: 404 (no 403) en su escuela.
		mvc.perform(get("/api/escuela/logo").cookie(new Cookie("jwt", tProfesorA))).andExpect(status().isNotFound());
		// Escribir, o cualquier otra ruta bajo /api/escuela, sigue cerrado.
		for (String ruta : List.of("/api/escuela/marca", "/api/escuela/logo")) {
			mvc.perform(put(ruta).contentType(MediaType.APPLICATION_JSON).content("{}")
					.cookie(new Cookie("jwt", tProfesorA))).andExpect(status().isForbidden());
			mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content("{}")
					.cookie(new Cookie("jwt", tProfesorA))).andExpect(status().isForbidden());
			mvc.perform(delete(ruta).cookie(new Cookie("jwt", tProfesorA))).andExpect(status().isForbidden());
		}
		mvc.perform(get("/api/escuela/otra").cookie(new Cookie("jwt", tProfesorA))).andExpect(status().isForbidden());
	}

	@Test
	void logo_conETagYCache_yNoCruzaEscuelas() throws Exception {
		mvc.perform(get("/api/escuela/logo").cookie(new Cookie("jwt", tGestorA))).andExpect(status().isNotFound());
		subirLogo(e.org);
		try {
			Long imagenId = jdbc.queryForObject("SELECT logo_imagen_id FROM organizacion WHERE id = ?", Long.class,
					e.org);
			assertNotNull(imagenId);
			JsonNode marca = leer(get("/api/escuela/marca"), tGestorA, 200);
			assertEquals("SUBIDO", marca.get("logo").asText());
			assertEquals(imagenId.longValue(), marca.get("logoVersion").asLong());

			MvcResult logo = mvc.perform(get("/api/escuela/logo").cookie(new Cookie("jwt", tGestorA)))
					.andExpect(status().isOk()).andReturn();
			assertEquals(MediaType.IMAGE_PNG_VALUE, logo.getResponse().getContentType());
			String cache = logo.getResponse().getHeader("Cache-Control");
			assertTrue(cache.contains("private") && cache.contains("max-age=300"), cache);
			String etag = logo.getResponse().getHeader("ETag");
			assertNotNull(etag);
			assertTrue(etag.contains(String.valueOf(imagenId)), etag);
			assertTrue(logo.getResponse().getContentAsByteArray().length > 0);

			MvcResult igual = mvc.perform(get("/api/escuela/logo").header("If-None-Match", etag)
					.cookie(new Cookie("jwt", tGestorA))).andExpect(status().isNotModified()).andReturn();
			assertEquals(0, igual.getResponse().getContentAsByteArray().length);
			assertEquals(etag, igual.getResponse().getHeader("ETag"));
			String cache304 = igual.getResponse().getHeader("Cache-Control");
			assertTrue(cache304.contains("private") && cache304.contains("max-age=300"), cache304);

			// Ronda 2: el 304 no lee el fichero. Sin él en disco, con la misma etiqueta sigue 304; sin etiqueta, 404.
			String ruta = jdbc.queryForObject("SELECT ruta FROM imagen WHERE id = ?", String.class, imagenId);
			Path fichero = Path.of(raizImagenes).resolve(ruta);
			Path apartado = fichero.resolveSibling(fichero.getFileName() + ".apartado");
			Files.move(fichero, apartado);
			try {
				mvc.perform(get("/api/escuela/logo").header("If-None-Match", etag).cookie(new Cookie("jwt", tGestorA)))
						.andExpect(status().isNotModified());
				mvc.perform(get("/api/escuela/logo").cookie(new Cookie("jwt", tGestorA)))
						.andExpect(status().isNotFound());
			} finally {
				Files.move(apartado, fichero);
			}

			// La misma cuenta, en B (sin logo): no ve el de A.
			mvc.perform(get("/api/escuela/logo").cookie(new Cookie("jwt", tDosEnB))).andExpect(status().isNotFound());

			// Pieza 8b-3o: la sesión solo-profesor lee el logo de su escuela (200 y 304); con la sesión en B, 404.
			mvc.perform(get("/api/escuela/logo").cookie(new Cookie("jwt", tProfesorA))).andExpect(status().isOk());
			mvc.perform(get("/api/escuela/logo").header("If-None-Match", etag).cookie(new Cookie("jwt", tProfesorA)))
					.andExpect(status().isNotModified());
			assertEquals("SUBIDO", leer(get("/api/escuela/marca"), tProfesorA, 200).get("logo").asText());
			mvc.perform(get("/api/escuela/logo").cookie(new Cookie("jwt", tProfesorEnB)))
					.andExpect(status().isNotFound());
			assertEquals("NINGUNO", leer(get("/api/escuela/marca"), tDosEnB, 200).get("logo").asText());
		} finally {
			mvc.perform(delete("/api/plataforma/escuelas/" + e.org + "/logo").cookie(cookieSuperadmin));
		}
	}

	// ------------------------------------------------------------------ escritura (superadmin)

	@Test
	void marca_soloPlataforma() throws Exception {
		conColor(e.org, "#3E9B4F", () -> {
			for (String token : List.of(tPropietarioA, tGestorA, tFamiliaA, tResponsableA, tProfesorA)) {
				mvc.perform(putColor(e.org, "\"#1B2B2E\"").cookie(new Cookie("jwt", token)))
						.andExpect(status().isForbidden());
				mvc.perform(putColor(e.org, "null").cookie(new Cookie("jwt", token)))
						.andExpect(status().isForbidden());
			}
			mvc.perform(putColor(e.org, "\"#1B2B2E\"")).andExpect(status().isUnauthorized());
			assertEquals("#3E9B4F", colorDe(e.org));
		});
	}

	@Test
	void guardarYQuitar_respondeConElTema_yLaListaLoLee() throws Exception {
		conColor(orgB, null, () -> {
			JsonNode guardada = leer(putColor(orgB, "\"#1b2b2e\""), cookieSuperadmin, 200);
			assertEquals(orgB, guardada.get("id").asLong());
			assertEquals("Escuela Ficticia Marca B", guardada.get("nombre").asText());
			assertEquals("#1B2B2E", guardada.get("colorPrimario").asText());
			assertTrue(guardada.has("tieneLogo") && guardada.has("marcaHeredada"));
			assertTema(ReglaContraste.tema("#1B2B2E"), guardada.get("tema"));
			assertEquals("#1B2B2E", colorDe(orgB));

			JsonNode lista = leer(get("/api/plataforma/escuelas"), cookieSuperadmin, 200);
			JsonNode deB = null;
			for (JsonNode escuela : lista) {
				if (escuela.get("id").asLong() == orgB) {
					deB = escuela;
				}
			}
			assertNotNull(deB);
			assertEquals("#1B2B2E", deB.get("colorPrimario").asText());

			JsonNode quitada = leer(putColor(orgB, "null"), cookieSuperadmin, 200);
			assertTrue(quitada.get("colorPrimario").isNull());
			assertEquals("#1A2230", quitada.get("tema").get("primario").asText());
			assertNull(colorDe(orgB));
		});
	}

	@Test
	void formatoInvalido_400_y_escuelaInexistente_404() throws Exception {
		conColor(orgB, "#3E9B4F", () -> {
			for (String valor : List.of("\"#12345\"", "\"1B2B2E\"", "\"#GGGGGG\"", "\"\"", "\"rojo\"",
					"\"#1B2B2E \"")) {
				JsonNode error = leer(putColor(orgB, valor), cookieSuperadmin, 400);
				assertTrue(error.get("mensaje").asText().contains("#RRGGBB"), error.toString());
			}
			assertEquals("#3E9B4F", colorDe(orgB));
		});
		long inexistente = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) + 1000 FROM organizacion", Long.class);
		assertTrue(leer(putColor(inexistente, "\"#1B2B2E\""), cookieSuperadmin, 404).has("mensaje"));
	}

	/**
	 * Punto de revisión 3: una fila por escritura, en la escuela DESTINO, con ids y booleanos; nunca el valor del color
	 * (ni en el 2xx ni en el cuerpo del 4xx, que el filtro guarda redactado). Visible para su propietario.
	 */
	@Test
	void registroEnLaEscuelaDestino_sinElValor() throws Exception {
		conColor(e.org, null, () -> {
			String ruta = "/api/plataforma/escuelas/" + e.org + "/marca";
			long antes = ultimoEvento();
			leer(putColor(e.org, "\"#2D6A4F\""), cookieSuperadmin, 200);
			leer(putColor(e.org, "\"#2D6A4F\""), cookieSuperadmin, 200); // sin cambios: también registra
			leer(putColor(e.org, "null"), cookieSuperadmin, 200);
			leer(putColor(e.org, "\"#ZZ6A4F\""), cookieSuperadmin, 400);

			List<Map<String, Object>> filas = jdbc.queryForList("SELECT id, organizacion_id, usuario_id, accion, "
					+ "metodo_http, endpoint, modulo, recurso_id, estado_http, resumen, payload_json, "
					+ "query_params_json FROM auditoria_evento WHERE id > ? AND endpoint = ? ORDER BY id", antes, ruta);
			List<Map<String, Object>> correctas = filas.stream()
					.filter(f -> ((Number) f.get("estado_http")).intValue() == 200).toList();
			assertEquals(List.of("PLATAFORMA MARCA_COLOR organizacion=#" + e.org + " quitado=NO",
					"PLATAFORMA MARCA_COLOR organizacion=#" + e.org + " quitado=NO sinCambios=SI",
					"PLATAFORMA MARCA_COLOR organizacion=#" + e.org + " quitado=SI"),
					correctas.stream().map(f -> (String) f.get("resumen")).toList(), filas.toString());
			for (Map<String, Object> fila : correctas) {
				assertEquals(e.org, ((Number) fila.get("organizacion_id")).longValue(), "en la escuela destino");
				assertEquals(superadmin.getId(), ((Number) fila.get("usuario_id")).longValue());
				assertEquals("UPDATE", fila.get("accion"));
				assertEquals("PUT", fila.get("metodo_http"));
				assertEquals("plataforma", fila.get("modulo"));
				assertEquals(e.org, ((Number) fila.get("recurso_id")).longValue());
			}
			// El 400 deja solo la fila genérica, con el color redactado.
			List<Map<String, Object>> errores = filas.stream()
					.filter(f -> ((Number) f.get("estado_http")).intValue() == 400).toList();
			assertEquals(1, errores.size(), filas.toString());
			assertTrue(((String) errores.get(0).get("resumen")).matches(RESUMEN_GENERICO));
			assertEquals(4, filas.size(), "una fila por escritura: " + filas);

			// Ni rastro del valor en la auditoría.
			for (String valor : List.of("2D6A4F", "2d6a4f", "ZZ6A4F")) {
				assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_evento WHERE id > ? AND "
						+ "CONCAT_WS('|', endpoint, resumen, payload_json, query_params_json) LIKE ?", Integer.class,
						antes, "%" + valor + "%"), valor);
			}
			// El propietario lo ve en su registro de acciones.
			MvcResult registro = mvc.perform(get("/api/admin/auditoria/eventos").param("modulo", "plataforma")
					.cookie(new Cookie("jwt", tPropietarioA))).andExpect(status().isOk()).andReturn();
			String visto = registro.getResponse().getContentAsString();
			assertTrue(visto.contains("PLATAFORMA MARCA_COLOR organizacion=#" + e.org + " quitado=SI"), visto);
		});
	}

	/** Ronda 2: la clave colorPrimario es obligatoria (null explícito para quitar); sin ella, 400 y nada cambia. */
	@Test
	void sinClaveColorPrimario_400_yNoQuita() throws Exception {
		conColor(orgB, "#3E9B4F", () -> {
			for (String cuerpo : List.of("{}", "{\"color\":\"#1B2B2E\"}", "{\"colorprimario\":null}", "null")) {
				MvcResult r = mvc.perform(put("/api/plataforma/escuelas/" + orgB + "/marca")
						.contentType(MediaType.APPLICATION_JSON).content(cuerpo).cookie(cookieSuperadmin)).andReturn();
				assertEquals(400, r.getResponse().getStatus(), cuerpo + " " + r.getResponse().getContentAsString());
				assertTrue(json.readTree(r.getResponse().getContentAsString()).has("mensaje"), cuerpo);
				assertEquals("#3E9B4F", colorDe(orgB), cuerpo);
			}
			// Con null explícito, sí quita.
			assertTrue(leer(putColor(orgB, "null"), cookieSuperadmin, 200).get("colorPrimario").isNull());
			assertNull(colorDe(orgB));
		});
	}

	// ------------------------------------------------------------------ nombre visible

	@Test
	void permisos_nombreVisible() throws Exception {
		long cuenta = e.cuenta(usuarios, "nombre-visible-8bt");
		jdbc.update("UPDATE usuario SET nombre = 'Lucía', apellidos = 'Ortega Ruiz' WHERE id = ?", cuenta);
		membresia(e.org, cuenta, "ADMINISTRADOR");
		JsonNode permisos = leer(get("/api/auth/permisos"), e.token(jwtService, usuarios, cuenta), 200);
		assertEquals("Lucía O.", permisos.get("nombreVisible").asText());
	}

	// ------------------------------------------------------------------ apoyo

	@FunctionalInterface
	interface Bloque {
		void ejecutar() throws Exception;
	}

	/** Fija el color por SQL y lo devuelve a su valor anterior al terminar (también si falla). */
	private void conColor(long organizacion, String color, Bloque bloque) throws Exception {
		String anterior = colorDe(organizacion);
		jdbc.update("UPDATE organizacion SET color_primario = ? WHERE id = ?", color, organizacion);
		try {
			bloque.ejecutar();
		} finally {
			jdbc.update("UPDATE organizacion SET color_primario = ? WHERE id = ?", anterior, organizacion);
		}
	}

	private String colorDe(long organizacion) {
		return jdbc.queryForObject("SELECT color_primario FROM organizacion WHERE id = ?", String.class, organizacion);
	}

	private void assertTema(TemaEscuela esperado, JsonNode tema) {
		assertNotNull(tema);
		assertEquals(esperado.primario(), tema.get("primario").asText());
		assertEquals(esperado.sobrePrimario(), tema.get("sobrePrimario").asText());
		assertEquals(esperado.sobrePrimario2(), tema.get("sobrePrimario2").asText());
		assertEquals(esperado.primarioTinta(), tema.get("primarioTinta").asText());
		assertEquals(esperado.primarioSuave(), tema.get("primarioSuave").asText());
		assertEquals(esperado.ajustado(), tema.get("ajustado").asBoolean());
	}

	private MockHttpServletRequestBuilder putColor(long organizacion, String valorJson) {
		return put("/api/plataforma/escuelas/" + organizacion + "/marca").contentType(MediaType.APPLICATION_JSON)
				.content("{\"colorPrimario\":" + valorJson + "}");
	}

	private JsonNode leer(MockHttpServletRequestBuilder peticion, String token, int estado) throws Exception {
		return leer(peticion, new Cookie("jwt", token), estado);
	}

	private JsonNode leer(MockHttpServletRequestBuilder peticion, Cookie cookie, int estado) throws Exception {
		MvcResult resultado = mvc.perform(peticion.cookie(cookie)).andReturn();
		assertEquals(estado, resultado.getResponse().getStatus(), resultado.getResponse().getContentAsString());
		String cuerpo = resultado.getResponse().getContentAsString();
		return cuerpo.isEmpty() ? json.nullNode() : json.readTree(cuerpo);
	}

	private void subirLogo(long organizacion) throws Exception {
		BufferedImage imagen = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
		var g = imagen.createGraphics();
		g.setColor(Color.BLUE);
		g.fillRect(0, 0, 40, 30);
		g.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(imagen, "png", out);
		MockMultipartFile archivo = new MockMultipartFile("archivo", "logo-ficticio.png", "image/png",
				out.toByteArray());
		mvc.perform(multipart(HttpMethod.PUT, "/api/plataforma/escuelas/" + organizacion + "/logo").file(archivo)
				.cookie(cookieSuperadmin)).andExpect(status().isOk());
	}

	private long ultimoEvento() {
		return jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM auditoria_evento", Long.class);
	}

	private static Set<String> campos(JsonNode nodo) {
		Set<String> claves = new TreeSet<>();
		nodo.fieldNames().forEachRemaining(claves::add);
		return claves;
	}

	private void membresia(long organizacion, long usuario, String rol) {
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", usuario, organizacion,
				rol);
	}

	private String token(long usuarioId, long organizacion) {
		Usuario usuario = TenantContext.calcularCon(ORG1, () -> usuarios.findById(usuarioId).orElseThrow());
		return jwtService.generateToken(usuario, 600_000, organizacion);
	}

	private Usuario crearSuperadmin(String email) {
		Usuario creado = TenantContext.calcularCon(ORG1, () -> {
			Usuario usuario = new Usuario();
			usuario.setNombre("Superadmin");
			usuario.setApellidos("Ficticio");
			usuario.setEmail(email);
			usuario.setContrasena(encoder.encode(CLAVE));
			usuario.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(usuario);
		});
		assertEquals(1, jdbc.update("UPDATE usuario SET es_admin_plataforma = TRUE WHERE id = ?", creado.getId()));
		return creado;
	}

	private String login(String email) throws Exception {
		MvcResult resultado = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return json.readTree(resultado.getResponse().getContentAsString()).get("token").asText();
	}
}
