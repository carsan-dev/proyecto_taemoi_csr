package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.config.AuditoriaRequestFilter.EscrituraConRegistroPropio;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.ResolutorEscuelaPorHost;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza W12a de la web por plantilla (fase4-w12-plan.md): el contenido de la web para el editor del propietario
 * ({@code GET/PUT /api/escuela/web/contenido}), la subida de sus fotos ({@code POST /api/escuela/web/imagenes}), lo que
 * sale de él en la web pública ({@code oculto}, nunca la constancia del permiso) y la descripción de las fotos de sede
 * por la API de Sedes (d523). Cadena de filtros completa contra MySQL real; la raíz de imágenes, en un directorio
 * temporal. Cada test usa su propia escuela sintética; la 1 (Moiskimdo) no se toca. Datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WebContenidoIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String CONTENIDO = "/api/escuela/web/contenido";
	private static final String IMAGENES = "/api/escuela/web/imagenes";
	private static final String PUBLICO = "/api/publico/web";
	private static final String RESUMEN_GENERICO = "^(CREATE|UPDATE|DELETE|READ|OPERACION) \\S+( #\\d+)? -> \\d+$";
	private static final Path RAIZ;
	private static final Path CARPETA_IMAGENES;
	private static final Path CARPETA_DOCUMENTOS;

	static {
		try {
			RAIZ = Files.createTempDirectory("taemoi-it-web-contenido-").toAbsolutePath().normalize();
			CARPETA_IMAGENES = Files.createDirectories(RAIZ.resolve("imagenes"));
			CARPETA_DOCUMENTOS = Files.createDirectories(RAIZ.resolve("documentos"));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@DynamicPropertySource
	static void raicesDeFicheros(DynamicPropertyRegistry registro) {
		registro.add("app.imagenes.directorio.linux", () -> CARPETA_IMAGENES + "/");
		registro.add("app.imagenes.directorio.windows", () -> CARPETA_IMAGENES + "/");
		registro.add("app.documentos.directorio.linux", () -> CARPETA_DOCUMENTOS + "/");
		registro.add("app.documentos.directorio.windows", () -> CARPETA_DOCUMENTOS + "/");
	}

	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired ObjectMapper json;
	@Autowired JwtService jwt;
	@Autowired UsuarioRepository usuarios;
	@Autowired ResolutorEscuelaPorHost porHost;

	@MockitoBean JavaMailSender mailSender;

	final String marca = UUID.randomUUID().toString().substring(0, 8);
	final List<String> hosts = new ArrayList<>();

	/** Una escuela de plantilla con su propietario, dos sedes, taekwondo y su host. */
	record Escuela(long org, long propietario, Cookie sesion, long sedeA, long sedeB, String host) {
	}

	@AfterAll
	void limpiar() throws IOException {
		hosts.forEach(host -> jdbc.update("DELETE FROM organizacion_dominio WHERE host = ?", host));
		porHost.recargar();
		try (Stream<Path> rutas = Files.walk(RAIZ)) {
			rutas.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		}
	}

	@BeforeAll
	void comprobarMoiskimdo() {
		assertEquals(0, contar("SELECT COUNT(*) FROM web_bloque WHERE organizacion_id = 1"));
	}

	// ------------------------------------------------------------------ leer y guardar

	/** Escuela que aún no ha guardado ni publicado: versión 0 y sin bloques (estado «nueva» del boceto). */
	@Test
	void escuelaNueva_sinWeb_versionCero() throws Exception {
		Escuela e = escuela("nueva", false);
		JsonNode contenido = cuerpo(mvc.perform(get(CONTENIDO).cookie(e.sesion())).andReturn());
		assertEquals(0, contenido.get("version").asLong());
		assertEquals(0, contenido.get("bloques").size());

		// Guarda sin tener web publicada: la versión pasa a 1 y web_escuela sigue sin fila.
		JsonNode guardado = cuerpo(guardar(e, 0, minimo(e)));
		assertEquals(1, guardado.get("version").asLong());
		assertEquals(0, contar("SELECT COUNT(*) FROM web_escuela WHERE organizacion_id = " + e.org()));
	}

	/**
	 * El propietario guarda el contenido entero y lo lee igual; la constancia del permiso del profesor guarda quién y
	 * cuándo; el segundo guardado la conserva aunque no llegue la casilla; lo que no llega se quita.
	 */
	@Test
	void propietario_guardaYLee_conConstanciaDelPermiso() throws Exception {
		Escuela e = escuela("guarda", true);
		long foto = subir(e);
		long galeria1 = subir(e);
		List<Map<String, Object>> bloques = new ArrayList<>(minimo(e));
		bloques.add(fila("menu.horarios", "Horarios y sedes", null, 0, false, null));
		bloques.add(fila("presentacion", null, null, 2, false, null));
		bloques.add(fila("presentacion.titulo", "Clases cerca de casa", null, 0, false, null));
		bloques.add(fila("presentacion.texto", "Primer párrafo.\n\nSegundo párrafo.", null, 0, false, null));
		bloques.add(fila("presentacion.foto", "Clase en el tatami", foto, 0, false, null));
		bloques.add(fila("profesores", null, null, 3, false, null));
		bloques.add(fila("profesor.1.nombre", "Profesora Inventada", null, 0, false, true));
		bloques.add(fila("profesor.1.cargo", "Cinturón negro 3.º dan", null, 0, false, null));
		bloques.add(fila("profesor.1.sedes", e.sedeB() + ", " + e.sedeA(), null, 0, false, null));
		bloques.add(fila("galeria", null, null, 4, false, null));
		bloques.add(fila("galeria.rotulo", "Galería", null, 0, false, null));
		bloques.add(fila("galeria.1", "Niños en fila", galeria1, 1, false, null));
		bloques.add(fila("texto_foto.1", null, null, 5, true, null));
		bloques.add(fila("texto_foto.1.titulo", "Qué traer el primer día", null, 0, false, null));
		bloques.add(fila("disciplina.taekwondo.descripcion", "Arte marcial coreano.", null, 0, false, null));
		bloques.add(fila("disciplinas", null, null, 1, false, null));
		bloques.add(fila("legal.aviso.p1", "Texto legal propio.", null, 0, false, null));
		bloques.add(fila("pre.vacio_titulo", "Ahora no hay grupos", null, 0, false, null));

		long antes = ultimoEvento();
		JsonNode guardado = cuerpo(guardar(e, 0, bloques));
		assertEquals(1, guardado.get("version").asLong());
		Map<String, JsonNode> porClave = porClave(guardado);
		assertEquals(bloques.size(), porClave.size());
		assertEquals("Primer párrafo.\n\nSegundo párrafo.", porClave.get("presentacion.texto").get("texto").asText());
		assertEquals(e.sedeB() + "," + e.sedeA(), porClave.get("profesor.1.sedes").get("texto").asText(),
				"normalizado, en el orden elegido");
		assertEquals(foto, porClave.get("presentacion.foto").get("imagenId").asLong());
		assertTrue(porClave.get("presentacion.foto").get("imagenUrl").asText()
				.endsWith("/imagenes/org-" + e.org() + "/web/bloques/" + nombreDe(foto)));
		assertTrue(porClave.get("texto_foto.1").get("oculto").asBoolean());
		assertTrue(porClave.get("presentacion").get("texto").isNull());
		JsonNode permiso = porClave.get("profesor.1.nombre").get("permiso");
		assertEquals("PANEL", permiso.get("origen").asText());
		assertEquals("Usuario Prueba-" + marca, permiso.get("por").asText());
		assertNotNull(LocalDateTime.parse(permiso.get("en").asText()));
		assertTrue(porClave.get("profesor.1.cargo").get("permiso").isNull());
		Map<String, Object> fila = jdbc.queryForMap("SELECT permiso_origen, permiso_por, permiso_en FROM web_bloque "
				+ "WHERE organizacion_id = ? AND clave = 'profesor.1.nombre'", e.org());
		assertEquals("PANEL", fila.get("permiso_origen"));
		assertEquals(e.propietario(), ((Number) fila.get("permiso_por")).longValue());
		assertEquals(cuerpo(mvc.perform(get(CONTENIDO).cookie(e.sesion())).andReturn()), guardado, "GET = PUT");

		// Registro: una fila explícita, solo recuentos (nunca textos ni nombres).
		List<Map<String, Object>> eventos = eventosDesde(e.org(), antes);
		assertEquals(1, eventos.size(), eventos.toString());
		Map<String, Object> evento = eventos.get(0);
		assertEquals("WEB CONTENIDO_GUARDADO organizacion=#" + e.org() + " version=1 bloques=20 textos=12 fotos=3"
				+ " ocultos=1 quitados=0 fotosBorradas=0 permisosNuevos=1", evento.get("resumen"));
		assertEquals("CREATE", evento.get("accion"));
		assertEquals("PUT", evento.get("metodo_http"), "el método real, también en el primer guardado");
		assertEquals("web", evento.get("modulo"));
		assertEquals(CONTENIDO, evento.get("endpoint"));
		assertEquals(e.propietario(), ((Number) evento.get("usuario_id")).longValue());
		assertEquals(200, ((Number) evento.get("estado_http")).intValue());
		assertTrue(EscrituraConRegistroPropio.WEB_CONTENIDO_GUARDAR.reconoce("PUT", CONTENIDO));

		// Segundo guardado: mismo nombre y sin la casilla, la constancia se conserva (mismo autor y fecha); sin la
		// galería, su foto se borra (fila y fichero).
		Path ficheroGaleria = CARPETA_IMAGENES.resolve(ruta(galeria1));
		assertTrue(Files.exists(ficheroGaleria));
		jdbc.update("UPDATE web_bloque SET permiso_en = '2026-11-03 10:00:00' WHERE organizacion_id = ? "
				+ "AND clave = 'profesor.1.nombre'", e.org());
		List<Map<String, Object>> segundo = new ArrayList<>(bloques.stream()
				.filter(b -> !String.valueOf(b.get("clave")).startsWith("galeria")).toList());
		segundo.replaceAll(b -> "profesor.1.nombre".equals(b.get("clave"))
				? fila("profesor.1.nombre", "  Profesora Inventada ", null, 0, false, null) : b);
		antes = ultimoEvento();
		JsonNode otra = cuerpo(guardar(e, 1, segundo));
		assertEquals(2, otra.get("version").asLong());
		JsonNode constancia = porClave(otra).get("profesor.1.nombre").get("permiso");
		assertEquals("PANEL", constancia.get("origen").asText());
		assertEquals("2026-11-03T10:00:00", constancia.get("en").asText());
		assertFalse(porClave(otra).containsKey("galeria.1"));
		assertEquals(0, contar("SELECT COUNT(*) FROM imagen WHERE id = " + galeria1), "fila de la foto quitada");
		assertFalse(Files.exists(ficheroGaleria), "fichero de la foto quitada");
		assertEquals(1, contar("SELECT COUNT(*) FROM imagen WHERE id = " + foto), "la que sigue en uso, no");
		eventos = eventosDesde(e.org(), antes);
		assertEquals(1, eventos.size(), eventos.toString());
		assertEquals("UPDATE", eventos.get(0).get("accion"));
		assertEquals("PUT", eventos.get(0).get("metodo_http"));
		assertTrue(((String) eventos.get(0).get("resumen")).endsWith("quitados=3 fotosBorradas=1 permisosNuevos=NINGUNO"),
				eventos.toString());
	}

	/** d526: un profesor del alta («Permiso dado en el alta») se guarda sin casilla y conserva su constancia. */
	@Test
	void profesorDelAlta_conservaLaConstancia() throws Exception {
		Escuela e = escuela("alta", true);
		jdbc.update("""
				INSERT INTO web_bloque (organizacion_id, clave, texto, orden, permiso_origen, permiso_en)
				VALUES (?, 'profesor.1.nombre', 'Profesor del alta', 0, 'ALTA', '2026-11-01 09:00:00')""", e.org());
		JsonNode leido = cuerpo(mvc.perform(get(CONTENIDO).cookie(e.sesion())).andReturn());
		JsonNode permiso = porClave(leido).get("profesor.1.nombre").get("permiso");
		assertEquals("ALTA", permiso.get("origen").asText());
		assertTrue(permiso.get("por").isNull());
		assertEquals("2026-11-01T09:00:00", permiso.get("en").asText());

		List<Map<String, Object>> bloques = new ArrayList<>(minimo(e));
		bloques.add(fila("profesor.1.nombre", "Profesor del alta", null, 0, false, null));
		bloques.add(fila("profesor.2.nombre", "Profesor nuevo", null, 0, false, true));
		Map<String, JsonNode> guardado = porClave(cuerpo(guardar(e, 0, bloques)));
		assertEquals("ALTA", guardado.get("profesor.1.nombre").get("permiso").get("origen").asText());
		assertEquals("PANEL", guardado.get("profesor.2.nombre").get("permiso").get("origen").asText());
	}

	/**
	 * d531: la constancia es de la persona, no del puesto {@code profesor.<n>}. Puesto con la constancia del alta y otro
	 * nombre: sin la casilla, 400 y nada escrito; con ella, constancia nueva (PANEL, quién y cuándo). Con el mismo nombre
	 * y la casilla marcada otra vez, también se renueva; con el mismo nombre y sin casilla, se conserva.
	 */
	@Test
	void nombreNuevoEnElPuesto_exigeLaCasilla_yRenuevaLaConstancia() throws Exception {
		Escuela e = escuela("nombre-nuevo", true);
		cuerpo(guardar(e, 0, minimo(e)));
		jdbc.update("""
				INSERT INTO web_bloque (organizacion_id, clave, texto, orden, permiso_origen, permiso_en)
				VALUES (?, 'profesor.1.nombre', 'Profesor del alta', 0, 'ALTA', '2026-11-01 09:00:00')""", e.org());
		List<Map<String, Object>> antes = filasDe(e.org());
		long ultimo = ultimoEvento();

		// Otro nombre sin la casilla (sin el campo o con false): 400, nada escrito.
		for (Boolean casilla : Arrays.asList(null, false)) {
			List<Map<String, Object>> sinCasilla = new ArrayList<>(minimo(e));
			sinCasilla.add(fila("profesor.1.nombre", "Otra Persona", null, 0, false, casilla));
			MvcResult r = guardar(e, 1, sinCasilla);
			assertError(r, "profesor.1.permiso");
			assertTrue(json.readTree(contenido(r)).get("errores").get("profesor.1.permiso").asText()
					.startsWith("Has cambiado el nombre"), contenido(r));
		}
		assertEquals(antes, filasDe(e.org()), "ningún bloque cambia");
		assertEquals(1L, version(e.org()));
		assertTrue(eventosDesde(e.org(), ultimo).stream()
				.noneMatch(ev -> String.valueOf(ev.get("resumen")).startsWith("WEB CONTENIDO_GUARDADO")));

		// Otro nombre con la casilla: constancia nueva del panel, de quien guarda y de ahora.
		LocalDateTime alta = LocalDateTime.parse("2026-11-01T09:00:00");
		List<Map<String, Object>> conCasilla = new ArrayList<>(minimo(e));
		conCasilla.add(fila("profesor.1.nombre", "Otra Persona", null, 0, false, true));
		JsonNode permiso = porClave(cuerpo(guardar(e, 1, conCasilla))).get("profesor.1.nombre").get("permiso");
		assertEquals("PANEL", permiso.get("origen").asText());
		assertEquals("Usuario Prueba-" + marca, permiso.get("por").asText());
		assertTrue(LocalDateTime.parse(permiso.get("en").asText()).isAfter(alta), permiso.toString());
		Map<String, Object> fila = jdbc.queryForMap("SELECT permiso_origen, permiso_por FROM web_bloque "
				+ "WHERE organizacion_id = ? AND clave = 'profesor.1.nombre'", e.org());
		assertEquals("PANEL", fila.get("permiso_origen"));
		assertEquals(e.propietario(), ((Number) fila.get("permiso_por")).longValue());

		// Mismo nombre y la casilla otra vez: se renueva.
		jdbc.update("UPDATE web_bloque SET permiso_en = '2026-11-02 08:00:00' WHERE organizacion_id = ? "
				+ "AND clave = 'profesor.1.nombre'", e.org());
		JsonNode renovada = porClave(cuerpo(guardar(e, 2, conCasilla))).get("profesor.1.nombre").get("permiso");
		assertNotEquals("2026-11-02T08:00:00", renovada.get("en").asText());
		assertTrue(LocalDateTime.parse(renovada.get("en").asText()).isAfter(alta));

		// Mismo nombre sin la casilla: se conserva tal cual.
		jdbc.update("UPDATE web_bloque SET permiso_en = '2026-11-02 08:00:00' WHERE organizacion_id = ? "
				+ "AND clave = 'profesor.1.nombre'", e.org());
		List<Map<String, Object>> mismoNombre = new ArrayList<>(minimo(e));
		mismoNombre.add(fila("profesor.1.nombre", "Otra Persona", null, 0, false, null));
		JsonNode conservada = porClave(cuerpo(guardar(e, 3, mismoNombre))).get("profesor.1.nombre").get("permiso");
		assertEquals("PANEL", conservada.get("origen").asText());
		assertEquals("2026-11-02T08:00:00", conservada.get("en").asText());
	}

	// ------------------------------------------------------------------ registro sin el cuerpo

	/**
	 * El cuerpo del contenido (textos y nombres de profesores) no se guarda en el registro con ningún resultado: 200
	 * (registro propio, solo recuentos), 400, 409 y JSON malformado (fila genérica con su estado y el cuerpo omitido).
	 */
	@Test
	void registro_sinElCuerpoDelContenido_enNingunCaso() throws Exception {
		Escuela e = escuela("registro", true);
		String secreto = "Nombre Secreto " + marca;
		List<Map<String, Object>> bien = new ArrayList<>(minimo(e));
		bien.add(fila("profesor.1.nombre", secreto, null, 0, false, true));
		long ultimo = ultimoEvento();

		cuerpo(guardar(e, 0, bien));
		List<Map<String, Object>> mal = new ArrayList<>(bien);
		mal.add(fila("profesor.2.nombre", secreto + " Dos", null, 0, false, null));
		assertError(guardar(e, 1, mal), "profesor.2.permiso");
		assertEquals(409, guardar(e, 0, bien).getResponse().getStatus());
		assertEquals(400, peticion(put(CONTENIDO).contentType(MediaType.APPLICATION_JSON)
				.content("{\"version\":1,\"bloques\":[{\"clave\":\"profesor.1.nombre\",\"texto\":\"" + secreto + "\""),
				e.sesion()).getResponse().getStatus());

		List<Map<String, Object>> filas = jdbc.queryForList("""
				SELECT estado_http, metodo_http, payload_json, query_params_json, resumen FROM auditoria_evento
				WHERE id > ? AND organizacion_id = ? AND endpoint = ? AND metodo_http = 'PUT' ORDER BY id""", ultimo,
				e.org(), CONTENIDO);
		List<Integer> estados = filas.stream().map(f -> ((Number) f.get("estado_http")).intValue()).toList();
		assertEquals(List.of(200, 400, 409, 400), estados, filas.toString());
		for (Map<String, Object> f : filas) {
			int estado = ((Number) f.get("estado_http")).intValue();
			if (estado == 200) {
				assertNull(f.get("payload_json"), "registro propio: sin cuerpo");
			} else {
				assertEquals("[payload omitted: contenido de la web]", f.get("payload_json"), f.toString());
			}
		}
		assertEquals(0, contar("SELECT COUNT(*) FROM auditoria_evento WHERE id > " + ultimo
				+ " AND (payload_json LIKE '%" + marca + "%' OR resumen LIKE '%" + marca
				+ "%' OR query_params_json LIKE '%" + marca + "%')"), "ningún texto enviado en el registro");
	}

	// ------------------------------------------------------------------ URL de imagen en el cuerpo

	/**
	 * El cuerpo no puede poner una foto por su URL: sin {@code imagenId} válido, 400 (sea de otra escuela, de alumnos, de
	 * sedes o externa) y nada cambia. La URL que llegue junto a un {@code imagenId} propio se ignora: no hay dónde
	 * guardarla y sale la de la foto propia.
	 */
	@Test
	void urlDeImagenEnElCuerpo_noPoneNingunaFoto() throws Exception {
		Escuela e = escuela("url", true);
		Escuela otra = escuela("url-otra", true);
		cuerpo(guardar(e, 0, minimo(e)));
		long ajena = subir(otra);
		List<String> urls = List.of("/imagenes/" + ruta(ajena),
				"/imagenes/org-" + e.org() + "/web/bloques/" + nombreDe(ajena),
				"/imagenes/org-" + e.org() + "/alumnos/foto-" + marca + ".webp",
				"/imagenes/org-" + e.org() + "/web/sedes/foto-" + marca + ".webp",
				"https://externa-" + marca + ".invalid/foto.webp");
		List<Map<String, Object>> antes = filasDe(e.org());
		for (String url : urls) {
			List<Map<String, Object>> bloques = new ArrayList<>(minimo(e));
			Map<String, Object> galeria = fila("galeria.1", "Foto", null, 1, false, null);
			galeria.put("imagenUrl", url);
			bloques.add(galeria);
			assertError(guardar(e, 1, bloques), "galeria.1");
		}
		assertEquals(antes, filasDe(e.org()));
		assertEquals(1L, version(e.org()));

		long propia = subir(e);
		List<Map<String, Object>> bloques = new ArrayList<>(minimo(e));
		Map<String, Object> galeria = fila("galeria.1", "Foto", propia, 1, false, null);
		galeria.put("imagenUrl", "https://externa-" + marca + ".invalid/foto.webp");
		bloques.add(galeria);
		MvcResult r = guardar(e, 1, bloques);
		JsonNode guardada = porClave(cuerpo(r)).get("galeria.1");
		assertTrue(guardada.get("imagenUrl").asText().endsWith("/org-" + e.org() + "/web/bloques/" + nombreDe(propia)));
		assertFalse(contenido(r).contains("externa-" + marca));
		assertFalse(contenido(mvc.perform(get(PUBLICO).header("Host", e.host())).andReturn()).contains("externa-" + marca));
	}

	// ------------------------------------------------------------------ escuelas aisladas

	/** Lo de la escuela B no se ve ni se toca desde la A: ni al leer, ni al guardar, ni al subir. */
	@Test
	void escuelasAisladas() throws Exception {
		Escuela a = escuela("aislada-a", true);
		Escuela b = escuela("aislada-b", true);
		String secretoB = "Solo de B " + marca;
		long fotoB = subir(b);
		List<Map<String, Object>> bloquesB = new ArrayList<>(minimo(b));
		bloquesB.add(fila("presentacion.titulo", secretoB, null, 0, false, null));
		bloquesB.add(fila("galeria.1", "Foto de B", fotoB, 1, false, null));
		bloquesB.add(fila("profesor.1.nombre", "Profesor de B " + marca, null, 0, false, true));
		cuerpo(guardar(b, 0, bloquesB));
		cuerpo(guardar(b, 1, bloquesB));
		jdbc.update("UPDATE sede SET foto_alt = 'Descripción de B' WHERE id = ?", b.sedeA());
		List<Map<String, Object>> filasB = filasDe(b.org());
		List<Map<String, Object>> sedesB = jdbc.queryForList("SELECT * FROM sede WHERE organizacion_id = ? ORDER BY id",
				b.org());
		long imagenesB = contar("SELECT COUNT(*) FROM imagen WHERE organizacion_id = " + b.org());
		assertEquals(2L, version(b.org()));

		// A lee: nada de B.
		MvcResult leido = peticion(get(CONTENIDO), a.sesion());
		JsonNode contenidoA = cuerpo(leido);
		assertEquals(0, contenidoA.get("version").asLong());
		assertEquals(0, contenidoA.get("bloques").size());
		assertFalse(contenido(leido).contains(marca), contenido(leido));

		// A guarda: la foto de B no vale; la versión de B no es la de A; lo suyo, sí.
		List<Map<String, Object>> conFotoB = new ArrayList<>(minimo(a));
		conFotoB.add(fila("galeria.1", "Foto", fotoB, 1, false, null));
		assertError(guardar(a, 0, conFotoB), "galeria.1");
		assertEquals(409, guardar(a, 2, minimo(a)).getResponse().getStatus());
		List<Map<String, Object>> deA = new ArrayList<>(minimo(a));
		deA.add(fila("presentacion.titulo", "De A", null, 0, false, null));
		MvcResult guardadoA = guardar(a, 0, deA);
		assertEquals(1, cuerpo(guardadoA).get("version").asLong());
		assertFalse(contenido(guardadoA).contains("Solo de B"), contenido(guardadoA));
		assertTrue(ruta(subir(a)).startsWith("org-" + a.org() + "/web/bloques/"));

		// B, intacta.
		assertEquals(filasB, filasDe(b.org()));
		assertEquals(2L, version(b.org()));
		assertEquals(sedesB, jdbc.queryForList("SELECT * FROM sede WHERE organizacion_id = ? ORDER BY id", b.org()));
		assertEquals(imagenesB, contar("SELECT COUNT(*) FROM imagen WHERE organizacion_id = " + b.org()));
		assertFalse(contenido(mvc.perform(get(PUBLICO).header("Host", a.host())).andReturn()).contains("Solo de B"));
	}

	// ------------------------------------------------------------------ a la vez

	/** d522: dos guardados a la vez con la misma versión: uno gana (200) y el otro, 409; la versión sube una vez. */
	@Test
	void dosGuardadosALaVez_unoGana_elOtro409() throws Exception {
		Escuela e = escuela("a-la-vez", true);
		cuerpo(guardar(e, 0, minimo(e)));
		List<Map<String, Object>> uno = new ArrayList<>(minimo(e));
		uno.add(fila("menu.horarios", "Uno", null, 0, false, null));
		List<Map<String, Object>> dos = new ArrayList<>(minimo(e));
		dos.add(fila("menu.horarios", "Dos", null, 0, false, null));
		long ultimo = ultimoEvento();

		ExecutorService hilos = Executors.newFixedThreadPool(2);
		CountDownLatch salida = new CountDownLatch(1);
		try {
			Future<Integer> primero = hilos.submit(() -> {
				salida.await();
				return guardar(e, 1, uno).getResponse().getStatus();
			});
			Future<Integer> segundo = hilos.submit(() -> {
				salida.await();
				return guardar(e, 1, dos).getResponse().getStatus();
			});
			salida.countDown();
			List<Integer> estados = new ArrayList<>(
					List.of(primero.get(60, TimeUnit.SECONDS), segundo.get(60, TimeUnit.SECONDS)));
			estados.sort(Comparator.naturalOrder());
			assertEquals(List.of(200, 409), estados);
		} finally {
			hilos.shutdownNow();
		}
		assertEquals(2L, version(e.org()));
		String texto = jdbc.queryForObject("SELECT texto FROM web_bloque WHERE organizacion_id = ? "
				+ "AND clave = 'menu.horarios'", String.class, e.org());
		assertTrue("Uno".equals(texto) || "Dos".equals(texto), texto);
		assertEquals(1, eventosDesde(e.org(), ultimo).stream()
				.filter(ev -> String.valueOf(ev.get("resumen")).startsWith("WEB CONTENIDO_GUARDADO")).count());
	}

	// ------------------------------------------------------------------ 400

	/** Cada error, 400 con su clave en {@code errores}; nada se guarda (ni filas, ni versión, ni registro propio). */
	@Test
	void datosNoValidos_400_yNadaCambia() throws Exception {
		Escuela e = escuela("errores", true);
		Escuela otra = escuela("errores-otra", true);
		cuerpo(guardar(e, 0, minimo(e)));
		long fotoAjena = subir(otra);
		long fotoAlumnos = imagenSuelta(e.org(), "org-" + e.org() + "/alumnos/foto-" + marca + ".webp");
		long fotoSede = imagenSuelta(e.org(), "org-" + e.org() + "/web/sedes/foto-" + marca + ".webp");
		long sedeAjena = otra.sedeA();
		List<Map<String, Object>> antes = filasDe(e.org());
		long version = 1;

		Map<String, Map<String, Object>> casos = new LinkedHashMap<>();
		casos.put("menu.otro", fila("menu.otro", "x", null, 0, false, null));
		casos.put("menu.horarios", fila("menu.horarios", "x".repeat(21), null, 0, false, null));
		casos.put("cabecera.boton", fila("cabecera.boton", "   ", null, 0, false, null));
		casos.put("presentacion", fila("presentacion", "texto en la cabecera", null, 0, false, null));
		casos.put("tarifas", fila("tarifas", null, null, 0, true, null));
		casos.put("presentacion.titulo", fila("presentacion.titulo", "Oculto", null, 0, true, null));
		casos.put("presentacion.foto", fila("presentacion.foto", "Foto", fotoAjena, 0, false, null));
		casos.put("galeria.1", fila("galeria.1", "Foto", fotoAlumnos, 0, false, null));
		casos.put("galeria.2", fila("galeria.2", "Foto", fotoSede, 0, false, null));
		casos.put("galeria.3", fila("galeria.3", "Foto", 999_999_999L, 0, false, null));
		casos.put("texto_foto.1.foto", fila("texto_foto.1.foto", "", subir(e), 0, false, null));
		casos.put("profesor.3.sedes", fila("profesor.3.sedes", String.valueOf(sedeAjena), null, 0, false, null));
		casos.put("disciplina.kickboxing.descripcion",
				fila("disciplina.kickboxing.descripcion", "No es de la escuela", null, 0, false, null));
		casos.put("menu.sedes", fila("menu.sedes", "Sedes", null, -1, false, null));
		casos.put("portada.frase", fila("portada.frase", "Frase", 123L, 0, false, null));
		// En la galería, orden = puesto de la foto: como mucho 6.
		casos.put("galeria.4", fila("galeria.4", "Foto", subir(e), 7, false, null));
		for (Map.Entry<String, Map<String, Object>> caso : casos.entrySet()) {
			List<Map<String, Object>> bloques = new ArrayList<>(minimo(e));
			bloques.add(caso.getValue());
			assertError(guardar(e, version, bloques), caso.getKey());
		}
		// Galería con 7 fotos.
		List<Map<String, Object>> siete = new ArrayList<>(minimo(e));
		for (int n = 1; n <= 7; n++) {
			siete.add(fila("galeria." + n, "Foto " + n, subir(e), Math.min(n, 6), false, null));
		}
		assertError(guardar(e, version, siete), "galeria");
		// Profesor nuevo sin la casilla, o sin nombre.
		List<Map<String, Object>> sinPermiso = new ArrayList<>(minimo(e));
		sinPermiso.add(fila("profesor.1.nombre", "Sin permiso", null, 0, false, null));
		assertError(guardar(e, version, sinPermiso), "profesor.1.permiso");
		List<Map<String, Object>> sinNombre = new ArrayList<>(minimo(e));
		sinNombre.add(fila("profesor.2.cargo", "Cargo", null, 0, false, null));
		assertError(guardar(e, version, sinNombre), "profesor.2.nombre");
		// Texto con foto sin cabecera; sin la portada; clave repetida.
		List<Map<String, Object>> sinCabecera = new ArrayList<>(minimo(e));
		sinCabecera.add(fila("texto_foto.2.titulo", "Título", null, 0, false, null));
		assertError(guardar(e, version, sinCabecera), "texto_foto.2");
		assertError(guardar(e, version, List.of(fila("portada.titular", "Titular", null, 0, false, null))),
				"portada.foto");
		List<Map<String, Object>> repetida = new ArrayList<>(minimo(e));
		repetida.add(fila("menu.sedes", "A", null, 0, false, null));
		repetida.add(fila("menu.sedes", "B", null, 0, false, null));
		assertError(guardar(e, version, repetida), "menu.sedes");
		// Sin versión o con un cuerpo que no se lee.
		assertEquals(400, peticion(put(CONTENIDO).contentType(MediaType.APPLICATION_JSON)
				.content("{\"bloques\":[]}"), e.sesion()).getResponse().getStatus());
		assertEquals(400, peticion(put(CONTENIDO).contentType(MediaType.APPLICATION_JSON).content("no es json"),
				e.sesion()).getResponse().getStatus());

		// Un error al final de una lista válida tampoco deja nada.
		List<Map<String, Object>> casiBien = new ArrayList<>(minimo(e));
		casiBien.add(fila("menu.horarios", "Horarios nuevo", null, 0, false, null));
		casiBien.add(fila("menu.no_existe", "x", null, 0, false, null));
		long ultimo = ultimoEvento();
		assertError(guardar(e, version, casiBien), "menu.no_existe");
		List<Map<String, Object>> errores = eventosDesde(e.org(), ultimo);
		assertEquals(1, errores.size(), errores.toString());
		assertTrue(((String) errores.get(0).get("resumen")).matches(RESUMEN_GENERICO), "solo la fila genérica");

		assertEquals(antes, filasDe(e.org()), "ningún bloque cambia");
		assertEquals(1L, version(e.org()), "la versión no cambia");
	}

	// ------------------------------------------------------------------ 409

	/** d522 y d530: versión vieja, 409 sin escribir nada y sin decir quién guardó. */
	@Test
	void versionVieja_409_nadaEscrito_yNadieEnElCuerpo() throws Exception {
		Escuela e = escuela("conflicto", true);
		cuerpo(guardar(e, 0, minimo(e)));
		List<Map<String, Object>> antes = filasDe(e.org());
		List<Map<String, Object>> bloques = new ArrayList<>(minimo(e));
		bloques.add(fila("menu.horarios", "Pisado", null, 0, false, null));
		for (long vieja : List.of(0L, 5L)) {
			MvcResult r = guardar(e, vieja, bloques);
			assertEquals(409, r.getResponse().getStatus(), contenido(r));
			JsonNode cuerpo = json.readTree(contenido(r));
			assertEquals(List.of("mensaje", "motivo"), nombres(cuerpo));
			assertTrue(cuerpo.get("mensaje").asText().contains("Otra persona"));
			assertEquals("version", cuerpo.get("motivo").asText());
			assertFalse(contenido(r).contains(marca), "ni nombre ni correo de nadie");
		}
		assertEquals(antes, filasDe(e.org()));
		assertEquals(1L, version(e.org()));
	}

	// ------------------------------------------------------------------ atomicidad

	/** Un fallo de la base a mitad del reemplazo deshace todo: ni filas, ni versión, ni fichero de la foto quitada. */
	@Test
	void falloAMitad_noDejaNada() throws Exception {
		Escuela e = escuela("fallo", true);
		long galeria = subir(e);
		List<Map<String, Object>> primero = new ArrayList<>(minimo(e));
		primero.add(fila("galeria.1", "Foto", galeria, 1, false, null));
		cuerpo(guardar(e, 0, primero));
		List<Map<String, Object>> antes = filasDe(e.org());

		List<Map<String, Object>> segundo = new ArrayList<>(minimo(e));
		segundo.add(fila("menu.horarios", "Uno", null, 0, false, null));
		segundo.add(fila("menu.sedes", "Dos", null, 0, false, null));
		String trigger = "it_web_contenido_falla_" + marca.replace('-', '_');
		jdbc.execute("CREATE TRIGGER " + trigger + " BEFORE INSERT ON web_bloque FOR EACH ROW BEGIN IF NEW.organizacion_id = "
				+ e.org() + " AND NEW.clave = 'menu.sedes' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'fallo de prueba';"
				+ " END IF; END");
		try {
			int estado;
			try {
				estado = guardar(e, 1, segundo).getResponse().getStatus();
			} catch (Exception error) {
				// Sin manejador para el error de la base: MockMvc lo relanza (en el servidor, 500).
				assertTrue(String.valueOf(error.getMessage()).contains("fallo de prueba")
						|| String.valueOf(error.getCause()).contains("fallo de prueba"), error.toString());
				estado = 500;
			}
			assertEquals(500, estado);
		} finally {
			jdbc.execute("DROP TRIGGER " + trigger);
		}
		assertEquals(antes, filasDe(e.org()), "ningún bloque cambia");
		assertEquals(1L, version(e.org()));
		assertEquals(1, contar("SELECT COUNT(*) FROM imagen WHERE id = " + galeria));
		assertTrue(Files.exists(CARPETA_IMAGENES.resolve(ruta(galeria))));
	}

	// ------------------------------------------------------------------ lo público

	/**
	 * Sección oculta: el anónimo solo recibe su cabecera marcada, sin su contenido; la vista previa de la gestión,
	 * todo marcado. La constancia del permiso no sale en ninguna de las dos.
	 */
	@Test
	void oculto_yPermiso_enLaWebPublica() throws Exception {
		Escuela e = escuela("publica", true);
		long foto = subir(e);
		List<Map<String, Object>> bloques = new ArrayList<>(minimo(e));
		bloques.add(fila("presentacion", null, null, 2, true, null));
		bloques.add(fila("presentacion.texto", "Texto secreto " + marca, null, 0, false, null));
		bloques.add(fila("presentacion.foto", "Foto oculta", foto, 0, false, null));
		bloques.add(fila("profesores", null, null, 3, false, null));
		bloques.add(fila("profesor.1.nombre", "Profesora Visible", null, 0, false, true));
		cuerpo(guardar(e, 0, bloques));

		MvcResult anonima = mvc.perform(get(PUBLICO).header("Host", e.host())).andReturn();
		JsonNode web = cuerpo(anonima);
		Map<String, JsonNode> publicos = porClave(web);
		JsonNode cabecera = publicos.get("presentacion");
		assertTrue(cabecera.get("oculto").asBoolean());
		assertTrue(cabecera.get("texto").isNull());
		assertTrue(cabecera.get("imagenUrl").isNull());
		assertFalse(publicos.containsKey("presentacion.texto"));
		assertFalse(publicos.containsKey("presentacion.foto"));
		assertFalse(contenido(anonima).contains("Texto secreto"), "el texto oculto no sale");
		assertFalse(contenido(anonima).contains(nombreDe(foto)), "la foto oculta no sale");
		assertFalse(publicos.get("profesor.1.nombre").get("oculto").asBoolean());
		assertEquals("Profesora Visible", publicos.get("profesor.1.nombre").get("texto").asText());
		sinConstancia(anonima);

		MvcResult previa = mvc.perform(get(PUBLICO).param("vistaPrevia", "true").cookie(e.sesion())).andReturn();
		Map<String, JsonNode> enPrevia = porClave(cuerpo(previa));
		assertTrue(enPrevia.get("presentacion").get("oculto").asBoolean());
		assertEquals("Texto secreto " + marca, enPrevia.get("presentacion.texto").get("texto").asText());
		assertFalse(enPrevia.get("presentacion.texto").get("oculto").asBoolean());
		assertTrue(enPrevia.get("presentacion.foto").get("imagenUrl").asText().contains(nombreDe(foto)));
		sinConstancia(previa);
	}

	// ------------------------------------------------------------------ imágenes

	@Test
	void subirFoto_vaALaCarpetaPublicaDeBloques() throws Exception {
		Escuela e = escuela("subir", true);
		MvcResult r = peticion(multipart(IMAGENES).file(archivo(png(800, 600), "Foto de Lucía.png", "image/png")),
				e.sesion());
		assertEquals(201, r.getResponse().getStatus(), contenido(r));
		JsonNode dto = json.readTree(contenido(r));
		long imagen = dto.get("imagenId").asLong();
		String ruta = ruta(imagen);
		assertTrue(ruta.startsWith("org-" + e.org() + "/web/bloques/"), ruta);
		assertFalse(ruta.contains("Luc"), "el nombre del fichero subido no se usa: " + ruta);
		assertTrue(dto.get("url").asText().endsWith("/imagenes/" + ruta), dto.toString());
		assertTrue(Files.isRegularFile(CARPETA_IMAGENES.resolve(ruta)));
		assertEquals(0, contar("SELECT COUNT(*) FROM web_bloque WHERE organizacion_id = " + e.org()), "no toca bloques");
		// Pública para cualquiera.
		assertEquals(200, mvc.perform(get("/imagenes/" + ruta)).andReturn().getResponse().getStatus());
		for (String formato : List.of("jpeg", "webp")) {
			assertEquals(201, peticion(multipart(IMAGENES).file(archivo(imagen(formato), "f." + formato,
					"image/" + formato)), e.sesion()).getResponse().getStatus(), formato);
		}
	}

	@Test
	void subirFotoNoValida_400_sinFicheroNiFila() throws Exception {
		Escuela e = escuela("subir-mal", true);
		long ficheros = ficheros();
		long imagenes = contar("SELECT COUNT(*) FROM imagen WHERE organizacion_id = " + e.org());
		List<MockMultipartFile> malas = List.of(archivo(imagen("gif"), "f.gif", "image/gif"),
				archivo("no soy una imagen".getBytes(StandardCharsets.UTF_8), "f.png", "image/png"),
				archivo(new byte[0], "f.png", "image/png"), archivo(png(64, 64), "f.heic", "image/png"),
				archivo(png(64, 64), "f.png", "image/heic"), archivo(cabeceraPng(10_000, 7_000), "g.png", "image/png"));
		for (MockMultipartFile mala : malas) {
			MvcResult r = peticion(multipart(IMAGENES).file(mala), e.sesion());
			assertEquals(400, r.getResponse().getStatus(), mala.getOriginalFilename() + " " + contenido(r));
			assertTrue(json.readTree(contenido(r)).hasNonNull("mensaje"));
		}
		assertEquals(400, peticion(multipart(IMAGENES), e.sesion()).getResponse().getStatus(), "sin fichero");
		assertEquals(ficheros, ficheros(), "ningún fichero");
		assertEquals(imagenes, contar("SELECT COUNT(*) FROM imagen WHERE organizacion_id = " + e.org()));
	}

	// ------------------------------------------------------------------ permisos y Moiskimdo

	/** Solo el propietario: gestor, profesor, familia y responsable de sede, 403; sin sesión, 401. */
	@Test
	void soloElPropietario() throws Exception {
		Escuela e = escuela("permisos", true);
		long ficheros = ficheros();
		for (String rol : List.of("ADMINISTRADOR", "PROFESOR", "RESPONSABLE_SEDE", "FAMILIA")) {
			long cuenta = cuenta("rol-" + rol.toLowerCase(), e.org(), rol);
			if ("FAMILIA".equals(rol)) {
				alumnoDe(e.org(), cuenta);
			}
			Cookie sesion = sesion(cuenta, e.org());
			assertEquals(403, peticion(get(CONTENIDO), sesion).getResponse().getStatus(), "GET " + rol);
			assertEquals(403, guardarCon(sesion, 0, List.of()).getResponse().getStatus(), "PUT " + rol);
			assertEquals(403, peticion(multipart(IMAGENES).file(archivo(png(64, 64), "a.png", "image/png")), sesion)
					.getResponse().getStatus(), "POST " + rol);
		}
		assertEquals(401, mvc.perform(get(CONTENIDO)).andReturn().getResponse().getStatus());
		assertEquals(401, mvc.perform(put(CONTENIDO).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
				.andReturn().getResponse().getStatus());
		assertEquals(401, mvc.perform(multipart(IMAGENES).file(archivo(png(64, 64), "a.png", "image/png"))).andReturn()
				.getResponse().getStatus());
		assertEquals(ficheros, ficheros());
		assertEquals(0, contar("SELECT COUNT(*) FROM web_bloque WHERE organizacion_id = " + e.org()));
		assertEquals(0, contar("SELECT COUNT(*) FROM web_contenido WHERE organizacion_id = " + e.org()));
	}

	/** Moiskimdo (web propia): 409 en todo, sin filas ni ficheros; su web pública sigue en 404. */
	@Test
	void moiskimdo_409_yNadaCambia() throws Exception {
		Usuario admin = TenantContext.calcularCon(ORG1,
				() -> usuarios.findByEmailIgnoreCase("admin-it@example.test").orElseThrow());
		Cookie propietario = new Cookie("jwt", jwt.generateToken(admin, 600_000, ORG1));
		long ficheros = ficheros();
		assertEquals(409, peticion(get(CONTENIDO), propietario).getResponse().getStatus());
		MvcResult r = guardarCon(propietario, 0, List.of(fila("portada.titular", "Titular", null, 0, false, null)));
		assertEquals(409, r.getResponse().getStatus(), contenido(r));
		assertTrue(json.readTree(contenido(r)).get("mensaje").asText().contains("web propia"));
		assertEquals(409, peticion(multipart(IMAGENES).file(archivo(png(64, 64), "a.png", "image/png")), propietario)
				.getResponse().getStatus());
		assertEquals(ficheros, ficheros());
		assertEquals(0, contar("SELECT COUNT(*) FROM web_bloque WHERE organizacion_id = 1"));
		assertEquals(0, contar("SELECT COUNT(*) FROM web_contenido WHERE organizacion_id = 1"));
		assertEquals(0, contar("SELECT COUNT(*) FROM web_escuela WHERE organizacion_id = 1"));
		assertEquals(404, mvc.perform(get(PUBLICO)).andReturn().getResponse().getStatus());
	}

	// ------------------------------------------------------------------ descripción de las fotos de sede (d523)

	@Test
	void descripcionDeLasFotosDeSede_porSedes_yEnLaWeb() throws Exception {
		Escuela e = escuela("sede-alt", true);
		String ruta = "/api/sedes/" + e.sedeA();
		Map<String, Object> datos = new HashMap<>();
		datos.put("nombre", "Sede A " + marca);
		datos.put("fotoAlt", "  Pabellón con el tatami montado  ");
		JsonNode dto = cuerpo(peticion(put(ruta).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(datos)), e.sesion()));
		assertEquals("Pabellón con el tatami montado", dto.get("fotoAlt").asText());
		assertTrue(dto.get("foto2Alt").isNull());

		// Sin los campos, se conservan; con null, se vacían.
		JsonNode sinCampos = cuerpo(peticion(put(ruta).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("nombre", "Sede A " + marca))), e.sesion()));
		assertEquals("Pabellón con el tatami montado", sinCampos.get("fotoAlt").asText());
		assertEquals("Pabellón con el tatami montado", cuerpo(peticion(get(ruta), e.sesion())).get("fotoAlt").asText());
		datos.put("fotoAlt", "x".repeat(151));
		assertEquals(400, peticion(put(ruta).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(datos)), e.sesion()).getResponse().getStatus(), "máximo 150");
		datos.put("fotoAlt", "Pabellón con el tatami montado");
		datos.put("foto2Alt", "Entrada lateral");
		cuerpo(peticion(put(ruta).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(datos)),
				e.sesion()));

		JsonNode sedes = cuerpo(mvc.perform(get(PUBLICO).header("Host", e.host())).andReturn()).get("sedes");
		JsonNode a = null;
		JsonNode b = null;
		for (JsonNode sede : sedes) {
			if (sede.get("id").asLong() == e.sedeA()) {
				a = sede;
			} else if (sede.get("id").asLong() == e.sedeB()) {
				b = sede;
			}
		}
		assertNotNull(a);
		assertEquals("Pabellón con el tatami montado", a.get("fotoAlt").asText());
		assertEquals("Entrada lateral", a.get("foto2Alt").asText());
		assertTrue(b.get("fotoAlt").isNull(), "vacía: el tema pone la suya");

		datos.put("fotoAlt", null);
		JsonNode vaciada = cuerpo(peticion(put(ruta).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(datos)), e.sesion()));
		assertTrue(vaciada.get("fotoAlt").isNull());
		assertNull(jdbc.queryForObject("SELECT foto_alt FROM sede WHERE id = ?", String.class, e.sedeA()));
	}

	// ------------------------------------------------------------------ utilidades

	private void sinConstancia(MvcResult r) throws Exception {
		String texto = contenido(r);
		for (String prohibido : List.of("permiso", "PANEL", "Prueba-" + marca, "\"por\"")) {
			assertFalse(texto.contains(prohibido), prohibido + " en " + texto);
		}
	}

	private void assertError(MvcResult r, String clave) throws Exception {
		assertEquals(400, r.getResponse().getStatus(), clave + " → " + contenido(r));
		JsonNode cuerpo = json.readTree(contenido(r));
		assertTrue(cuerpo.get("mensaje").asText().contains("Tu web sigue como estaba"), contenido(r));
		assertTrue(cuerpo.get("errores").has(clave), clave + " → " + contenido(r));
	}

	/** Lo mínimo que se puede guardar: titular y foto de la portada. */
	private List<Map<String, Object>> minimo(Escuela e) throws Exception {
		return List.of(fila("portada.titular", "Taekwondo en " + e.org(), null, 0, false, null),
				fila("portada.foto", "Niños con dobok en fila", portada(e), 0, false, null));
	}

	private final Map<Long, Long> portadas = new HashMap<>();

	private long portada(Escuela e) throws Exception {
		Long foto = portadas.get(e.org());
		if (foto == null) {
			foto = subir(e);
			portadas.put(e.org(), foto);
		}
		return foto;
	}

	private static Map<String, Object> fila(String clave, String texto, Long imagenId, int orden, boolean oculto,
			Boolean permiso) {
		Map<String, Object> fila = new LinkedHashMap<>();
		fila.put("clave", clave);
		fila.put("texto", texto);
		fila.put("imagenId", imagenId);
		fila.put("orden", orden);
		fila.put("oculto", oculto);
		if (permiso != null) {
			fila.put("permiso", permiso);
		}
		return fila;
	}

	private MvcResult guardar(Escuela e, long version, List<Map<String, Object>> bloques) throws Exception {
		return guardarCon(e.sesion(), version, bloques);
	}

	private MvcResult guardarCon(Cookie sesion, long version, List<Map<String, Object>> bloques) throws Exception {
		String cuerpo = json.writeValueAsString(Map.of("version", version, "bloques", bloques));
		return peticion(put(CONTENIDO).contentType(MediaType.APPLICATION_JSON).content(cuerpo), sesion);
	}

	private long subir(Escuela e) throws Exception {
		MvcResult r = peticion(multipart(IMAGENES).file(archivo(png(320, 240), "a.png", "image/png")), e.sesion());
		assertEquals(201, r.getResponse().getStatus(), contenido(r));
		return json.readTree(contenido(r)).get("imagenId").asLong();
	}

	private long imagenSuelta(long org, String ruta) {
		jdbc.update("INSERT INTO imagen (nombre, ruta, tipo, url, organizacion_id) VALUES (?, ?, 'image/webp', ?, ?)",
				ruta.substring(ruta.lastIndexOf('/') + 1), ruta, "/imagenes/" + ruta, org);
		return jdbc.queryForObject("SELECT id FROM imagen WHERE ruta = ? AND organizacion_id = ?", Long.class, ruta, org);
	}

	private String ruta(long imagen) {
		return jdbc.queryForObject("SELECT ruta FROM imagen WHERE id = ?", String.class, imagen);
	}

	private String nombreDe(long imagen) {
		String ruta = ruta(imagen);
		return ruta.substring(ruta.lastIndexOf('/') + 1);
	}

	private List<Map<String, Object>> filasDe(long org) {
		return jdbc.queryForList("SELECT * FROM web_bloque WHERE organizacion_id = ? ORDER BY clave", org);
	}

	private Long version(long org) {
		List<Long> versiones = jdbc.queryForList("SELECT version FROM web_contenido WHERE organizacion_id = ?",
				Long.class, org);
		return versiones.isEmpty() ? 0L : versiones.get(0);
	}

	private static Map<String, JsonNode> porClave(JsonNode contenido) {
		Map<String, JsonNode> porClave = new LinkedHashMap<>();
		contenido.get("bloques").forEach(b -> porClave.put(b.get("clave").asText(), b));
		return porClave;
	}

	private static List<String> nombres(JsonNode nodo) {
		List<String> nombres = new ArrayList<>();
		nodo.fieldNames().forEachRemaining(nombres::add);
		nombres.sort(Comparator.naturalOrder());
		return nombres;
	}

	private long contar(String sql) {
		return jdbc.queryForObject(sql, Long.class);
	}

	private static long ficheros() throws IOException {
		try (Stream<Path> rutas = Files.walk(CARPETA_IMAGENES)) {
			return rutas.filter(Files::isRegularFile).count();
		}
	}

	private static MockMultipartFile archivo(byte[] contenido, String nombre, String tipo) {
		return new MockMultipartFile("archivo", nombre, tipo, contenido);
	}

	private static byte[] png(int ancho, int alto) throws IOException {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB), "png", salida);
		return salida.toByteArray();
	}

	private static byte[] imagen(String formato) throws IOException {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		assertTrue(ImageIO.write(new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB), formato, salida),
				"sin codificador " + formato);
		return salida.toByteArray();
	}

	/** Firma e IHDR: declara el tamaño sin pesar nada. */
	private static byte[] cabeceraPng(int ancho, int alto) {
		ByteBuffer png = ByteBuffer.allocate(33);
		png.put(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A });
		png.putInt(13).put(new byte[] { 'I', 'H', 'D', 'R' }).putInt(ancho).putInt(alto);
		png.put(new byte[] { 8, 2, 0, 0, 0 });
		return png.array();
	}

	private long ultimoEvento() {
		return jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM auditoria_evento", Long.class);
	}

	private List<Map<String, Object>> eventosDesde(long org, long desde) {
		return jdbc.queryForList("""
				SELECT id, accion, metodo_http, modulo, endpoint, usuario_id, estado_http, resumen FROM auditoria_evento
				WHERE organizacion_id = ? AND id > ? AND metodo_http <> 'GET' AND endpoint = ? ORDER BY id""", org, desde,
				CONTENIDO);
	}

	private MvcResult peticion(MockHttpServletRequestBuilder peticion, Cookie sesion) throws Exception {
		return mvc.perform(peticion.cookie(sesion)).andReturn();
	}

	private JsonNode cuerpo(MvcResult r) throws Exception {
		assertEquals(200, r.getResponse().getStatus(), contenido(r));
		return json.readTree(contenido(r));
	}

	private static String contenido(MvcResult r) throws Exception {
		return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	// ------------------------------------------------------------------ escuelas y cuentas

	private Escuela escuela(String sufijo, boolean publicada) {
		String slug = "it-w12a-" + sufijo + "-" + marca;
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES (?, ?)", slug, "Escuela W12a " + sufijo + " " + marca);
		long org = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = ?", Long.class, slug);
		if (publicada) {
			jdbc.update("INSERT INTO web_escuela (organizacion_id, tema, publicada, publicada_en) "
					+ "VALUES (?, 'linea-comarcal', TRUE, '2026-11-01 10:00:00')", org);
		}
		jdbc.update("INSERT INTO sede (organizacion_id, nombre, activa) VALUES (?, 'Sede A', TRUE)", org);
		jdbc.update("INSERT INTO sede (organizacion_id, nombre, activa) VALUES (?, 'Sede B', TRUE)", org);
		long sedeA = jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = ? AND nombre = 'Sede A'",
				Long.class, org);
		long sedeB = jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = ? AND nombre = 'Sede B'",
				Long.class, org);
		jdbc.update("""
				INSERT INTO disciplina (organizacion_id, codigo, nombre, sistema_grados_id, edad_adulto, orden, activa)
				VALUES (?, 'TAEKWONDO', 'Taekwondo', NULL, 14, 1, TRUE)""", org);
		String host = "w12a-" + org + "-" + marca + ".test";
		jdbc.update("INSERT INTO organizacion_dominio (organizacion_id, host, principal) VALUES (?, ?, TRUE)", org, host);
		hosts.add(host);
		porHost.recargar();
		long propietario = cuenta("propietario-" + sufijo, org, "PROPIETARIO");
		return new Escuela(org, propietario, sesion(propietario, org), sedeA, sedeB, host);
	}

	private Cookie sesion(long usuarioId, long org) {
		Usuario usuario = TenantContext.calcularCon(ORG1, () -> usuarios.findById(usuarioId).orElseThrow());
		return new Cookie("jwt", jwt.generateToken(usuario, 600_000, org));
	}

	private long cuenta(String nombre, long org, String rol) {
		String email = "it-w12a-" + nombre + "-" + org + "-" + marca + "@example.test";
		long id = TenantContext.calcularCon(ORG1, () -> {
			Usuario usuario = new Usuario();
			usuario.setNombre("Usuario");
			usuario.setApellidos("Prueba-" + marca);
			usuario.setEmail(email);
			usuario.setContrasena("fake-Password-123");
			usuario.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(usuario).getId();
		});
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", id, org, rol);
		return id;
	}

	/** Alumno activo vinculado a mano a una cuenta de familia (le abre la sesión en {@code org}). */
	private void alumnoDe(long org, long usuario) {
		long expediente = jdbc.queryForObject("SELECT COALESCE(MAX(numero_expediente), 60000) + 1 FROM alumno",
				Long.class);
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id)
				VALUES (TRUE, 'PRUEBA', 1, 'Calle 1', '2010-01-01', 'Familia W12a', ?, '600000000', 0, ?)""",
				expediente, org);
		long alumno = jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = ?",
				Long.class, expediente, org);
		jdbc.update("""
				INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen)
				VALUES (?, ?, ?, 'TUTOR', 'MANUAL')""", usuario, alumno, org);
	}
}
