package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.DelegatingServletInputStream;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.filter.FormContentFilter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.config.AuditoriaRequestFilter.EscrituraConRegistroPropio;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Membresia;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.MembresiaRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.JwtService;
import com.zaxxer.hikari.HikariDataSource;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.Cookie;

/**
 * Pieza R1 (fase3-e8c-plan.md) con la cadena de filtros completa contra MySQL real y una raíz de documentos temporal. Lo
 * que no se ve sin la cadena: los permisos, el cuerpo que nadie retiene en memoria, la conexión que no se queda retenida
 * durante una subida larga, la auditoría y el aislamiento entre escuelas. Las reglas de disco se prueban a fondo en
 * MaterialExamenGestionServiceTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MaterialExamenIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String RESUMEN_GENERICO = "^(CREATE|UPDATE|DELETE|READ|OPERACION) \\S+( #\\d+)? -> \\d+$";
	private static final String RAIZ_API = "/api/material-examen";
	private static final String B01 = "b01_inicio_a_amarillo";
	private static final String BLOQUE = RAIZ_API + "/TAEKWONDO/" + B01;
	private static final String BLOQUE_ORG2 = "bloque_propio_it";
	private static final Path DOCUMENTOS;

	static {
		try {
			DOCUMENTOS = Files.createTempDirectory("taemoi-it-material-").toAbsolutePath().normalize();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@DynamicPropertySource
	static void raizDeDocumentos(DynamicPropertyRegistry registro) {
		registro.add("app.documentos.directorio.linux", () -> DOCUMENTOS + "/");
		registro.add("app.documentos.directorio.windows", () -> DOCUMENTOS + "/");
	}

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired PasswordEncoder encoder;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired MembresiaRepository membresias;
	@Autowired OrganizacionRepository organizaciones;
	@Autowired DataSource dataSource;
	@Autowired ApplicationContext contexto;

	@MockitoBean JavaMailSender mailSender;

	final String sufijo = UUID.randomUUID().toString().substring(0, 8);
	long org2;
	String tPropietario;
	String tGestor;
	String tFamilia;
	String tPropietario2;

	@BeforeAll
	void preparar() {
		org2 = TenantContext.calcularCon(ORG1, () -> {
			Organizacion org = new Organizacion();
			org.setSlug("it-material-" + sufijo);
			org.setNombre("Escuela material " + sufijo);
			return organizaciones.save(org).getId();
		});
		jdbc.update("INSERT INTO sistema_grados (organizacion_id, nombre, plantilla_origen) VALUES (?, 'Sistema IT', NULL)",
				org2);
		long sistema = jdbc.queryForObject("SELECT id FROM sistema_grados WHERE organizacion_id = ?", Long.class, org2);
		jdbc.update("""
				INSERT INTO disciplina (organizacion_id, codigo, nombre, sistema_grados_id, edad_adulto, orden)
				VALUES (?, 'TAEKWONDO', 'Taekwondo IT', ?, 14, 1)""", org2, sistema);
		jdbc.update("""
				INSERT INTO grado (organizacion_id, sistema_grados_id, codigo, nombre, orden, color_1, color_2, aplica_a,
				  bloque_temario)
				VALUES (?, ?, 'BLANCO', 'Blanco IT', 1, NULL, NULL, 'TODOS', ?)""", org2, sistema, BLOQUE_ORG2);
		tPropietario = token(ORG1, RolMembresia.PROPIETARIO, "prop");
		tGestor = token(ORG1, RolMembresia.ADMINISTRADOR, "gestor");
		tFamilia = token(ORG1, RolMembresia.FAMILIA, "familia");
		tPropietario2 = token(org2, RolMembresia.PROPIETARIO, "prop2");
	}

	@BeforeEach
	void vaciarDisco() throws IOException {
		try (Stream<Path> rutas = Files.walk(DOCUMENTOS)) {
			rutas.filter(r -> !r.equals(DOCUMENTOS)).sorted(Comparator.reverseOrder()).forEach(r -> r.toFile().delete());
		}
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	@AfterAll
	void borrarRaiz() throws IOException {
		vaciarDisco();
		Files.deleteIfExists(DOCUMENTOS);
	}

	// ------------------------------------------------------------------ permisos

	@Test
	void soloElPropietarioYElGestor_porHttp() throws Exception {
		assertEquals(401, mvc.perform(get(RAIZ_API)).andReturn().getResponse().getStatus());
		assertFalse(estado(get(RAIZ_API), tFamilia) < 400);
		assertEquals(200, estado(get(RAIZ_API), tGestor));
		assertEquals(200, estado(get(RAIZ_API), tPropietario));
		assertFalse(estado(post(BLOQUE + "/videos").param("nombre", "a.mp4").content(mp4(32)), tFamilia) < 400);
		assertFalse(Files.exists(DOCUMENTOS.resolve("org-1")));
	}

	// ------------------------------------------------------------------ circuito completo

	@Test
	void listaYDetalle_conElCatalogoDeLaEscuela() throws Exception {
		legado("taekwondo", B01, "videos", "01_TAEGUK_IL_JANG_COMP.mp4");
		legado("taekwondo", "b11_negro_5_a_negro_6", "videos", "01_kukkiwon.mp4");

		JsonNode lista = leer(get(RAIZ_API), tGestor);
		JsonNode taekwondo = lista.get("disciplinas").get(0);
		assertEquals("TAEKWONDO", taekwondo.get("codigo").asText());
		assertEquals(11, taekwondo.get("bloques").size());
		JsonNode primero = taekwondo.get("bloques").get(0);
		assertEquals(B01, primero.get("id").asText());
		assertEquals("Blanco a amarillo", primero.get("nombre").asText());
		assertEquals("BLANCO", primero.get("grados").get(0).get("codigo").asText());
		assertEquals(1, primero.get("videos").asInt());
		assertEquals(1, taekwondo.get("bloques").get(10).get("videos").asInt(), "el bloque 11 lee su alias");
		assertEquals(2, lista.get("totales").get("videos").asInt());

		JsonNode detalle = leer(get(BLOQUE), tGestor);
		JsonNode video = detalle.get("videos").get(0);
		assertEquals("01_TAEGUK_IL_JANG_COMP.mp4", video.get("fichero").asText());
		assertEquals("TAEGUK IL JANG", video.get("titulo").asText());
		assertEquals(1, video.get("orden").asInt());
		assertEquals(32, video.get("bytes").asInt());
		assertTrue(video.get("fecha").asText().endsWith("Z"), "fecha ISO: " + video);
		assertTrue(video.get("tambienEnSuperiores").asBoolean());

		assertEquals(404, estado(get(RAIZ_API + "/NATACION/" + B01), tGestor));
		assertEquals(404, estado(get(RAIZ_API + "/TAEKWONDO/b99_no_existe"), tGestor));
		assertFalse(Files.exists(DOCUMENTOS.resolve("org-1")), "leer no adopta");
	}

	@Test
	void subir_ordenar_titular_sustituir_y_eliminar() throws Exception {
		legado("taekwondo", B01, "videos", "01_saludo.mp4");

		MockHttpServletResponse subida = respuesta(post(BLOQUE + "/videos").param("nombre", "Técnica básica.mp4")
				.content(mp4(64)).contentType("video/mp4"), tGestor);
		assertEquals(201, subida.getStatus(), subida.getContentAsString());
		JsonNode creada = json.readTree(subida.getContentAsByteArray());
		assertEquals("Tecnica_basica.mp4", creada.get("fichero").asText());
		assertEquals(2, creada.get("bloque").get("videos").size());
		assertEquals("Técnica básica", creada.get("bloque").get("videos").get(1).get("titulo").asText());
		assertFalse(Files.exists(DOCUMENTOS.resolve("Materiales_Examen/taekwondo").resolve(B01)), "adoptada");

		JsonNode ordenado = leerJson(put(BLOQUE + "/videos/orden")
				.content("[\"Tecnica_basica.mp4\",\"01_saludo.mp4\"]").contentType(MediaType.APPLICATION_JSON), tGestor);
		assertEquals("Tecnica_basica.mp4", ordenado.get("videos").get(0).get("fichero").asText());
		assertEquals(409, estado(put(BLOQUE + "/videos/orden").content("[\"01_saludo.mp4\"]")
				.contentType(MediaType.APPLICATION_JSON), tGestor));

		JsonNode titulado = leerJson(patch(BLOQUE + "/videos/01_saludo.mp4").content("{\"titulo\":\"Saludo inicial\"}")
				.contentType(MediaType.APPLICATION_JSON), tPropietario);
		assertEquals("Saludo inicial", titulado.get("videos").get(1).get("titulo").asText());
		assertEquals(400, estado(patch(BLOQUE + "/videos/01_saludo.mp4").content("{\"titulo\":\"  \"}")
				.contentType(MediaType.APPLICATION_JSON), tPropietario));
		assertEquals(400, estado(patch(BLOQUE + "/videos/01_saludo.mp4").content("no es json")
				.contentType(MediaType.APPLICATION_JSON), tPropietario));

		JsonNode sustituido = leerJson(put(BLOQUE + "/videos/01_saludo.mp4/contenido").content(mp4(128))
				.contentType("video/mp4"), tGestor);
		assertEquals("Saludo inicial", sustituido.get("videos").get(1).get("titulo").asText());
		assertEquals(128, sustituido.get("videos").get(1).get("bytes").asInt());

		JsonNode restante = leerJson(delete(BLOQUE + "/videos/Tecnica_basica.mp4"), tGestor);
		assertEquals(1, restante.get("videos").size());
		assertEquals(404, estado(delete(BLOQUE + "/videos/Tecnica_basica.mp4"), tGestor));
	}

	@Test
	void temarioYDocumentos_porElPanel_conSuDisposicion() throws Exception {
		byte[] pdf = pdf();
		assertEquals(200, estado(put(BLOQUE + "/temario").content(pdf).contentType("application/pdf"), tGestor));
		assertEquals(201, estado(post(BLOQUE + "/documentacion").param("nombre", "Guía.pdf").content(pdf)
				.contentType("application/pdf"), tGestor));
		assertEquals(201, estado(post(BLOQUE + "/documentacion").param("nombre", "notas.txt").content("hola")
				.contentType("text/plain"), tGestor));
		assertEquals(400, estado(post(BLOQUE + "/documentacion").param("nombre", "x.exe").content("MZ"), tGestor));

		MockHttpServletResponse temario = respuesta(get(BLOQUE + "/temario"), tGestor);
		assertEquals(200, temario.getStatus());
		assertArrayEquals(pdf, contenido(temario));
		assertTrue(temario.getHeader(HttpHeaders.CONTENT_DISPOSITION).startsWith("inline"));
		assertTrue(respuesta(get(BLOQUE + "/temario").param("download", "true"), tGestor)
				.getHeader(HttpHeaders.CONTENT_DISPOSITION).startsWith("attachment"));
		assertTrue(respuesta(get(BLOQUE + "/documentacion/Guia.pdf"), tGestor)
				.getHeader(HttpHeaders.CONTENT_DISPOSITION).startsWith("inline"));
		assertTrue(respuesta(get(BLOQUE + "/documentacion/notas.txt"), tGestor)
				.getHeader(HttpHeaders.CONTENT_DISPOSITION).startsWith("attachment"), "como en las familias");
	}

	@Test
	void elVisorDelPanel_pideRangos() throws Exception {
		assertEquals(201, estado(post(BLOQUE + "/videos").param("nombre", "a.mp4").content(mp4(1024)), tGestor));

		MockHttpServletResponse completo = respuesta(get(BLOQUE + "/videos/a.mp4"), tGestor);
		assertEquals(200, completo.getStatus());
		assertEquals("bytes", completo.getHeader(HttpHeaders.ACCEPT_RANGES));

		MockHttpServletResponse trozo = respuesta(get(BLOQUE + "/videos/a.mp4").header(HttpHeaders.RANGE, "bytes=4-7"), tGestor);
		assertEquals(206, trozo.getStatus());
		assertEquals("bytes 4-7/1024", trozo.getHeader(HttpHeaders.CONTENT_RANGE));
		assertArrayEquals("ftyp".getBytes(StandardCharsets.US_ASCII), contenido(trozo));

		assertEquals(416, estado(get(BLOQUE + "/videos/a.mp4").header(HttpHeaders.RANGE, "bytes=5000-6000"), tGestor));
		assertEquals(200, estado(head(BLOQUE + "/videos/a.mp4"), tGestor));
		assertEquals(404, estado(get(BLOQUE + "/videos/no.mp4"), tGestor));
		assertEquals(404, estado(get(BLOQUE + "/temario"), tGestor));
	}

	// ------------------------------------------------------------------ cuerpo crudo

	@Test
	void sinFormContentFilter_elCuerpoDeUnPutNoSeLeeAntesDeLaSeguridad() throws Exception {
		assertTrue(contexto.getBeansOfType(FormContentFilter.class).isEmpty(),
				"con este filtro, un PUT de formulario se leería entero en memoria antes de autenticar");
		AtomicBoolean leido = new AtomicBoolean();
		InputStream vigilado = new InputStream() {
			@Override
			public int read() {
				leido.set(true);
				return -1;
			}
		};

		MockHttpServletResponse anonimo = mvc.perform(put(BLOQUE + "/temario")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED).with(conFlujo(vigilado, 500_000_000L, null)))
				.andReturn().getResponse();

		assertEquals(401, anonimo.getStatus());
		assertFalse(leido.get(), "un anónimo nunca hace leer el cuerpo");
	}

	@Test
	void durante_elStreaming_noSeRetieneNingunaConexion() throws Exception {
		legado("taekwondo", B01, "videos", "01_saludo.mp4");
		HikariDataSource pool = dataSource.unwrap(HikariDataSource.class);
		AtomicInteger activas = new AtomicInteger(-1);
		byte[] video = mp4(4096);
		InputStream vigilado = new ByteArrayInputStream(video) {
			@Override
			public synchronized int read() {
				if (activas.get() < 0) {
					activas.set(pool.getHikariPoolMXBean().getActiveConnections());
				}
				return super.read();
			}
		};

		MockHttpServletResponse subida = respuesta(post(BLOQUE + "/videos").param("nombre", "largo.mp4")
				.with(conFlujo(vigilado, video.length, jwt(tGestor))), null);

		assertEquals(201, subida.getStatus(), subida.getContentAsString());
		assertEquals(0, activas.get(), "una subida de minutos no puede tener una conexión del pool");
		assertTrue(Files.size(DOCUMENTOS.resolve("org-1/materiales/taekwondo").resolve(B01).resolve("videos/largo.mp4")) == 4096);
	}

	@Test
	void losErroresDelCuerpo_conSuEstadoYMensajeEnEspanol() throws Exception {
		MockHttpServletResponse sinLongitud = respuesta(post(BLOQUE + "/videos").param("nombre", "a.mp4"), tGestor);
		assertEquals(411, sinLongitud.getStatus());
		assertTrue(mensaje(sinLongitud).contains("tamaño del fichero"), sinLongitud.getContentAsString());

		MockHttpServletResponse grande = respuesta(post(BLOQUE + "/videos").param("nombre", "a.mp4")
				.with(conFlujo(InputStream.nullInputStream(), 524_288_001L, jwt(tGestor))), null);
		assertEquals(413, grande.getStatus());
		assertEquals("El fichero supera el máximo de 500 MB.", mensaje(grande));

		MockHttpServletResponse falsa = respuesta(post(BLOQUE + "/videos").param("nombre", "a.mp4")
				.content("no soy un video de verdad"), tGestor);
		assertEquals(415, falsa.getStatus());
		assertEquals("El fichero no es un vídeo válido.", mensaje(falsa));

		MockHttpServletResponse traversal = respuesta(post(BLOQUE + "/videos").param("nombre", "../../fuera.mp4")
				.content(mp4(32)), tGestor);
		assertEquals(400, traversal.getStatus());
		assertEquals("El nombre del fichero no es válido.", mensaje(traversal));

		MockHttpServletResponse oculto = respuesta(get(BLOQUE + "/videos/.subida-x"), tGestor);
		assertEquals(400, oculto.getStatus());
	}

	@Test
	void unIndiceDanado_409ConSuMensaje_yElPanelSigueLeyendo() throws Exception {
		legado("taekwondo", B01, "videos", "01_saludo.mp4");
		Path indice = DOCUMENTOS.resolve("Materiales_Examen/taekwondo").resolve(B01).resolve("index.json");
		Files.writeString(indice, "roto");

		MockHttpServletResponse titulo = respuesta(patch(BLOQUE + "/videos/01_saludo.mp4").content("{\"titulo\":\"Otro\"}")
				.contentType(MediaType.APPLICATION_JSON), tGestor);

		assertEquals(409, titulo.getStatus());
		assertTrue(mensaje(titulo).contains("índice"), mensaje(titulo));
		assertEquals("roto", Files.readString(indice));
		assertEquals(1, leer(get(BLOQUE), tGestor).get("videos").size());
	}

	@Test
	void unErrorAlPedirUnFichero_saleAunqueNoSeAcepteJson() throws Exception {
		MockHttpServletResponse respuesta = respuesta(get(BLOQUE + "/videos/.oculto.mp4").accept("video/mp4"), tGestor);

		assertEquals(400, respuesta.getStatus());
		assertEquals("El nombre del fichero no es válido.", mensaje(respuesta));
	}

	// ------------------------------------------------------------------ auditoría

	private record Caso(EscrituraConRegistroPropio entrada, MockHttpServletRequestBuilder correcta, String registro,
			MockHttpServletRequestBuilder fallida, int estadoFallido) {
	}

	@Test
	void cadaEscritura_soloSuRegistro_yLaFallida_soloElGenerico() throws Exception {
		legado("taekwondo", B01, "videos", "01_saludo.mp4");
		legado("taekwondo", B01, "videos", "02_otro.mp4");
		legado("taekwondo", B01, "documentacion", "guia.pdf");
		String video = BLOQUE + "/videos/01_saludo.mp4";
		String documento = BLOQUE + "/documentacion/guia.pdf";
		byte[] pdf = pdf();

		List<Caso> casos = List.of(
				new Caso(EscrituraConRegistroPropio.MATERIAL_VIDEO_SUBIR,
						post(BLOQUE + "/videos").param("nombre", "nuevo.mp4").content(mp4(64)), "subido",
						post(BLOQUE + "/videos").param("nombre", "nuevo.mp4").content("no es un video con cabecera"), 415),
				new Caso(EscrituraConRegistroPropio.MATERIAL_VIDEO_SUSTITUIR, put(video + "/contenido").content(mp4(64)),
						"sustituido", put(BLOQUE + "/videos/no.mp4/contenido").content(mp4(64)), 404),
				new Caso(EscrituraConRegistroPropio.MATERIAL_VIDEO_TITULO,
						patch(video).content("{\"titulo\":\"Hola\"}").contentType(MediaType.APPLICATION_JSON), "Título",
						patch(video).content("{\"titulo\":\"\"}").contentType(MediaType.APPLICATION_JSON), 400),
				new Caso(EscrituraConRegistroPropio.MATERIAL_VIDEO_ORDEN,
						put(BLOQUE + "/videos/orden").contentType(MediaType.APPLICATION_JSON)
								.content("[\"nuevo.mp4\",\"02_otro.mp4\",\"01_saludo.mp4\"]"),
						"Orden",
						put(BLOQUE + "/videos/orden").contentType(MediaType.APPLICATION_JSON).content("[]"), 409),
				new Caso(EscrituraConRegistroPropio.MATERIAL_TEMARIO, put(BLOQUE + "/temario").content(pdf)
						.contentType("application/pdf"), "Temario",
						put(BLOQUE + "/temario").content("no es un pdf").contentType("application/pdf"), 415),
				new Caso(EscrituraConRegistroPropio.MATERIAL_DOCUMENTO_SUBIR,
						post(BLOQUE + "/documentacion").param("nombre", "nuevo.pdf").content(pdf), "subido",
						post(BLOQUE + "/documentacion").param("nombre", "x.exe").content("MZ"), 400),
				new Caso(EscrituraConRegistroPropio.MATERIAL_DOCUMENTO_SUSTITUIR, put(documento + "/contenido").content(pdf),
						"sustituido", put(BLOQUE + "/documentacion/no.pdf/contenido").content(pdf), 404),
				new Caso(EscrituraConRegistroPropio.MATERIAL_DOCUMENTO_ELIMINAR, delete(documento), "eliminado",
						delete(BLOQUE + "/documentacion/no.pdf"), 404),
				new Caso(EscrituraConRegistroPropio.MATERIAL_VIDEO_ELIMINAR, delete(video), "eliminado",
						delete(BLOQUE + "/videos/no.mp4"), 404));
		assertEquals(9, casos.stream().map(Caso::entrada).distinct().count());

		for (Caso caso : casos) {
			long antes = ultimoEvento();
			MockHttpServletResponse correcta = respuesta(caso.correcta(), tGestor);
			assertTrue(correcta.getStatus() >= 200 && correcta.getStatus() < 300,
					caso.entrada() + ": " + correcta.getContentAsString());
			List<Map<String, Object>> filas = eventosDesde(antes);
			assertEquals(1, filas.size(), caso.entrada() + ": " + filas);
			Map<String, Object> fila = filas.get(0);
			assertEquals("materiales", fila.get("modulo"), caso.entrada().toString());
			assertTrue(((String) fila.get("resumen")).contains(caso.registro()), fila.toString());
			assertFalse(((String) fila.get("resumen")).matches(RESUMEN_GENERICO), fila.toString());
			assertNotNull(fila.get("usuario_id"));
			assertEquals(200, leer(get("/api/admin/auditoria/eventos/" + fila.get("id")), tPropietario)
					.get("estadoHttp").asInt(), "el propietario la ve");

			antes = ultimoEvento();
			assertEquals(caso.estadoFallido(), estado(caso.fallida(), tGestor), caso.entrada().toString());
			List<Map<String, Object>> errores = eventosDesde(antes);
			assertEquals(1, errores.size(), caso.entrada() + ": " + errores);
			assertTrue(((String) errores.get(0).get("resumen")).matches(RESUMEN_GENERICO), errores.toString());
			assertFalse(String.valueOf(errores.get(0).get("payload")).contains("ftyp"), "sin el cuerpo");
		}
	}

	@Test
	void pedirUnFichero_noDejaFila_yLasListasSi() throws Exception {
		legado("taekwondo", B01, "videos", "01_saludo.mp4");
		long antes = ultimoEvento();

		assertEquals(200, estado(get(BLOQUE + "/videos/01_saludo.mp4"), tGestor));
		assertEquals(0, eventosDesde(antes).size());

		assertEquals(200, estado(get(BLOQUE), tGestor));
		assertEquals(1, eventosDesde(antes).size());
	}

	// ------------------------------------------------------------------ aislamiento

	@Test
	void otraEscuela_soloVeLoSuyo_ySuCatalogo() throws Exception {
		legado("taekwondo", B01, "videos", "01_saludo.mp4");

		JsonNode lista = leer(get(RAIZ_API), tPropietario2);
		assertEquals(1, lista.get("disciplinas").size());
		JsonNode disciplina = lista.get("disciplinas").get(0);
		assertEquals("Taekwondo IT", disciplina.get("nombre").asText());
		assertEquals(1, disciplina.get("bloques").size());
		assertEquals(BLOQUE_ORG2, disciplina.get("bloques").get(0).get("id").asText());
		assertEquals("Bloque propio it", disciplina.get("bloques").get(0).get("nombre").asText());
		assertEquals(0, lista.get("totales").get("videos").asInt());

		// El bloque de Moiskimdo no existe en su catálogo.
		assertEquals(404, estado(get(BLOQUE), tPropietario2));
		assertEquals(404, estado(get(BLOQUE + "/videos/01_saludo.mp4"), tPropietario2));
		assertEquals(404, estado(post(BLOQUE + "/videos").param("nombre", "a.mp4").content(mp4(32)), tPropietario2));

		String propio = RAIZ_API + "/TAEKWONDO/" + BLOQUE_ORG2;
		assertEquals(201, estado(post(propio + "/videos").param("nombre", "a.mp4").content(mp4(32)), tPropietario2));
		assertTrue(Files.isRegularFile(DOCUMENTOS.resolve("org-" + org2).resolve("materiales/taekwondo")
				.resolve(BLOQUE_ORG2).resolve("videos/a.mp4")));
		assertTrue(Files.isRegularFile(DOCUMENTOS.resolve("Materiales_Examen/taekwondo").resolve(B01)
				.resolve("videos/01_saludo.mp4")));
		assertEquals(404, estado(get(RAIZ_API + "/TAEKWONDO/" + BLOQUE_ORG2), tGestor), "y al revés");
	}

	// ------------------------------------------------------------------ apoyo

	/** Sustituye el cuerpo de la petición por un flujo: permite vigilar quién lo lee y cuándo. */
	private static RequestPostProcessor conFlujo(InputStream flujo, long longitud, Cookie sesion) {
		return original -> {
			MockHttpServletRequest nueva = new MockHttpServletRequest(original.getServletContext(), original.getMethod(),
					original.getRequestURI()) {
				@Override
				public ServletInputStream getInputStream() {
					return new DelegatingServletInputStream(flujo);
				}

				@Override
				public long getContentLengthLong() {
					return longitud;
				}
			};
			nueva.setServletPath(original.getServletPath());
			nueva.setPathInfo(original.getPathInfo());
			nueva.setQueryString(original.getQueryString());
			nueva.setParameters(original.getParameterMap());
			nueva.setContentType(original.getContentType());
			if (sesion != null) {
				nueva.setCookies(sesion);
			}
			return nueva;
		};
	}

	private void legado(String disciplina, String bloque, String carpeta, String nombre) throws IOException {
		Path destino = Files.createDirectories(
				DOCUMENTOS.resolve("Materiales_Examen").resolve(disciplina).resolve(bloque).resolve(carpeta));
		Files.write(destino.resolve(nombre), nombre.endsWith(".pdf") ? pdf() : mp4(32));
	}

	private static byte[] mp4(int bytes) {
		byte[] contenido = new byte[bytes];
		System.arraycopy("ftyp".getBytes(StandardCharsets.US_ASCII), 0, contenido, 4, 4);
		return contenido;
	}

	private static byte[] pdf() {
		return "%PDF-1.7\nprueba".getBytes(StandardCharsets.ISO_8859_1);
	}

	private static byte[] contenido(MockHttpServletResponse respuesta) throws Exception {
		return respuesta.getContentAsByteArray();
	}

	private String mensaje(MockHttpServletResponse respuesta) throws Exception {
		return json.readTree(respuesta.getContentAsByteArray()).get("mensaje").asText();
	}

	private static Cookie jwt(String token) {
		return new Cookie("jwt", token);
	}

	private MockHttpServletResponse respuesta(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(token == null ? peticion : peticion.cookie(jwt(token))).andReturn().getResponse();
	}

	private int estado(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return respuesta(peticion, token).getStatus();
	}

	private JsonNode leer(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		MockHttpServletResponse respuesta = respuesta(peticion, token);
		assertEquals(200, respuesta.getStatus(), respuesta.getContentAsString());
		return json.readTree(respuesta.getContentAsByteArray());
	}

	private JsonNode leerJson(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return leer(peticion, token);
	}

	private long ultimoEvento() {
		return jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM auditoria_evento", Long.class);
	}

	private List<Map<String, Object>> eventosDesde(long id) {
		return jdbc.queryForList("SELECT * FROM auditoria_evento WHERE id > ? ORDER BY id", id);
	}

	private String token(long organizacion, RolMembresia rol, String prefijo) {
		Usuario usuario = TenantContext.calcularCon(ORG1, () -> {
			Usuario nuevo = new Usuario();
			nuevo.setNombre("Usuario");
			nuevo.setApellidos("Material");
			nuevo.setEmail(prefijo + "-" + sufijo + "@example.test");
			nuevo.setContrasena(encoder.encode("Clave-Segura-123"));
			nuevo.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(nuevo);
		});
		TenantContext.ejecutarCon(ORG1, () -> membresias.save(new Membresia(usuario.getId(), organizacion, rol)));
		return jwtService.generateToken(usuario, 600_000, organizacion);
	}
}
