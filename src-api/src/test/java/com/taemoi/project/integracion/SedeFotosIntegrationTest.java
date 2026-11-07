package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.config.AuditoriaRequestFilter.EscrituraConRegistroPropio;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Web por plantilla: subir, sustituir y quitar las dos fotos de una sede ({@code PUT/DELETE
 * /api/sedes/{id}/fotos/{1|2}}) con la cadena de filtros completa contra MySQL real. Lo crítico: el fichero va SIEMPRE a
 * la carpeta pública de la web ({@code org-<id>/web/sedes/}), nunca a la de alumnos; solo JPEG, PNG o WebP; sin
 * metadatos; una sede de otra escuela no existe (404); y las fotos de alumnos siguen sin ser públicas.
 *
 * <p>La raíz de imágenes apunta a un directorio temporal. Escuelas sintéticas ({@link EscuelaConSedesDePrueba}); nada en
 * Moiskimdo. Datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SedeFotosIntegrationTest extends MySqlIntegrationTest {

	private static final String NO_EXISTE = "La sede no existe.";
	/** Resumen de la fila genérica del filtro HTTP ({@code AuditoriaServiceImpl#construirResumen}). */
	private static final String RESUMEN_GENERICO = "^(CREATE|UPDATE|DELETE|READ|OPERACION) \\S+( #\\d+)? -> \\d+$";
	private static final Path RAIZ;
	private static final Path IMAGENES;
	private static final Path DOCUMENTOS;

	static {
		try {
			RAIZ = Files.createTempDirectory("taemoi-it-sede-fotos-").toAbsolutePath().normalize();
			IMAGENES = Files.createDirectories(RAIZ.resolve("imagenes"));
			DOCUMENTOS = Files.createDirectories(RAIZ.resolve("documentos"));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@DynamicPropertySource
	static void raicesDeFicheros(DynamicPropertyRegistry registro) {
		registro.add("app.imagenes.directorio.linux", () -> IMAGENES + "/");
		registro.add("app.imagenes.directorio.windows", () -> IMAGENES + "/");
		registro.add("app.documentos.directorio.linux", () -> DOCUMENTOS + "/");
		registro.add("app.documentos.directorio.windows", () -> DOCUMENTOS + "/");
	}

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	long propietario;
	String tPropietario, tGestor, tFamilia, tRespA, tRespAC, tProfesor, tPlataforma, tPropietarioOtra;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-sede-fotos");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-sede-fotos-otra");

		propietario = e.cuenta(usuarios, "propietario");
		membresia(e.org, propietario, "PROPIETARIO");
		long familia = e.cuentaFamilia(usuarios, "familia");
		long profesor = e.cuenta(usuarios, "profesor");
		membresia(e.org, profesor, "PROFESOR");
		long plataforma = e.cuenta(usuarios, "plataforma");
		jdbc.update("UPDATE usuario SET es_admin_plataforma = TRUE WHERE id = ?", plataforma);
		long propietarioOtra = otra.cuenta(usuarios, "propietario");
		membresia(otra.org, propietarioOtra, "PROPIETARIO");

		tPropietario = e.token(jwtService, usuarios, propietario);
		tGestor = e.token(jwtService, usuarios, e.gestor);
		tFamilia = e.token(jwtService, usuarios, familia);
		tRespA = e.token(jwtService, usuarios, e.respA);
		tRespAC = e.token(jwtService, usuarios, e.respAC);
		tProfesor = e.token(jwtService, usuarios, profesor);
		Usuario cuentaPlataforma = TenantContext.calcularCon(EscuelaConSedesDePrueba.ORG1,
				() -> usuarios.findById(plataforma).orElseThrow());
		tPlataforma = jwtService.generateToken(cuentaPlataforma, 600_000, EscuelaConSedesDePrueba.ORG1);
		tPropietarioOtra = otra.token(jwtService, usuarios, propietarioOtra);
	}

	@AfterEach
	void sinFugas() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	@AfterAll
	void borrarRaiz() throws IOException {
		try (Stream<Path> rutas = Files.walk(RAIZ)) {
			rutas.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		}
	}

	// ------------------------------------------------------------------ subir

	@Test
	void subirFoto1_vaALaCarpetaWeb_yLaSirveAAnonimos() throws Exception {
		long sede = sedeNueva("subir");
		JsonNode dto = leer(subida(sede, 1, png(800, 600), "Foto de Lucía en clase.png", "image/png"), tGestor, 200);

		assertEquals(sede, dto.get("id").asLong());
		long imagen = dto.get("fotoImagenId").asLong();
		assertTrue(dto.get("foto2ImagenId").isNull(), "la foto 2 no se toca");
		assertEquals(imagen, (long) foto(sede, 1));
		assertNull(foto(sede, 2));

		Map<String, Object> fila = imagenFila(imagen);
		String ruta = (String) fila.get("ruta");
		assertTrue(ruta.startsWith("org-" + e.org + "/web/sedes/"), ruta);
		assertFalse(ruta.toLowerCase(Locale.ROOT).contains("alumnos"), ruta);
		assertFalse(ruta.contains("Luc") || ruta.contains("clase"), "el nombre del fichero subido no se usa: " + ruta);
		assertEquals("/imagenes/" + ruta, fila.get("url"));
		assertEquals(e.org, ((Number) fila.get("organizacion_id")).longValue());
		assertTrue(Files.isRegularFile(IMAGENES.resolve(ruta)));
		assertFalse(Files.exists(IMAGENES.resolve("org-" + e.org).resolve("alumnos")), "nada en la carpeta de alumnos");

		// Pública: anónimo, con caché pública.
		MockHttpServletResponse r = mvc.perform(get("/imagenes/" + ruta)).andReturn().getResponse();
		assertEquals(200, r.getStatus());
		assertArrayEquals(Files.readAllBytes(IMAGENES.resolve(ruta)), r.getContentAsByteArray());
		assertTrue(r.getHeader("Cache-Control").contains("public"), r.getHeader("Cache-Control"));

		// La API de sedes la devuelve.
		assertEquals(imagen, leer(get("/api/sedes/" + sede), tPropietario, 200).get("fotoImagenId").asLong());
	}

	@Test
	void subirFoto2_noTocaLaFoto1() throws Exception {
		long sede = sedeNueva("foto2");
		long uno = leer(subida(sede, 1, png(), "a.png", "image/png"), tPropietario, 200).get("fotoImagenId").asLong();
		JsonNode dto = leer(subida(sede, 2, png(), "b.png", "image/png"), tPropietario, 200);
		long dos = dto.get("foto2ImagenId").asLong();

		assertNotEquals(uno, dos);
		assertEquals(uno, dto.get("fotoImagenId").asLong());
		assertEquals(uno, (long) foto(sede, 1));
		assertEquals(dos, (long) foto(sede, 2));
		assertTrue(((String) imagenFila(dos).get("ruta")).startsWith("org-" + e.org + "/web/sedes/"));
	}

	@Test
	void jpegYWebp_admitidos() throws Exception {
		long sede = sedeNueva("formatos");
		leer(subida(sede, 1, imagen("jpeg"), "foto.jpg", "image/jpeg"), tPropietario, 200);
		leer(subida(sede, 2, imagen("webp"), "foto.webp", "image/webp"), tPropietario, 200);
		assertNotNull(foto(sede, 1));
		assertNotNull(foto(sede, 2));
	}

	@Test
	void sustituir_borraLaAnterior_filaYFichero() throws Exception {
		long sede = sedeNueva("sustituir");
		long antigua = leer(subida(sede, 1, png(), "a.png", "image/png"), tPropietario, 200).get("fotoImagenId")
				.asLong();
		Path ficheroAntiguo = IMAGENES.resolve((String) imagenFila(antigua).get("ruta"));
		assertTrue(Files.exists(ficheroAntiguo));

		long nueva = leer(subida(sede, 1, png(), "b.png", "image/png"), tPropietario, 200).get("fotoImagenId").asLong();

		assertNotEquals(antigua, nueva);
		assertEquals(nueva, (long) foto(sede, 1));
		assertEquals(0, contar("SELECT COUNT(*) FROM imagen WHERE id = " + antigua), "la fila anterior se borra");
		assertFalse(Files.exists(ficheroAntiguo), "el fichero anterior se borra");
		assertTrue(Files.exists(IMAGENES.resolve((String) imagenFila(nueva).get("ruta"))));
	}

	/** Una imagen que no es de la carpeta de las sedes (puesta a mano) se suelta, pero ni su fila ni su fichero se tocan. */
	@Test
	void sustituirUnaImagenAjenaALaCarpeta_soloLaSuelta() throws Exception {
		long sede = sedeNueva("ajena");
		String nombre = "ajena-" + UUID.randomUUID() + ".webp";
		String ruta = "org-" + e.org + "/eventos/" + nombre;
		Path fichero = Files.createDirectories(IMAGENES.resolve("org-" + e.org).resolve("eventos")).resolve(nombre);
		Files.write(fichero, png());
		jdbc.update("INSERT INTO imagen (nombre, ruta, tipo, url, organizacion_id) VALUES (?, ?, 'image/png', ?, ?)",
				nombre, ruta, "/imagenes/" + ruta, e.org);
		long ajena = jdbc.queryForObject("SELECT id FROM imagen WHERE nombre = ?", Long.class, nombre);
		jdbc.update("UPDATE sede SET foto_imagen_id = ? WHERE id = ?", ajena, sede);

		long nueva = leer(subida(sede, 1, png(), "b.png", "image/png"), tPropietario, 200).get("fotoImagenId").asLong();

		assertEquals(nueva, (long) foto(sede, 1));
		assertEquals(1, contar("SELECT COUNT(*) FROM imagen WHERE id = " + ajena), "su fila sigue");
		assertTrue(Files.exists(fichero), "su fichero sigue");
	}

	// ------------------------------------------------------------------ quitar

	@Test
	void quitar_dejaElHuecoVacio_yBorraFilaYFichero() throws Exception {
		long sede = sedeNueva("quitar");
		long uno = leer(subida(sede, 1, png(), "a.png", "image/png"), tPropietario, 200).get("fotoImagenId").asLong();
		long dos = leer(subida(sede, 2, png(), "b.png", "image/png"), tPropietario, 200).get("foto2ImagenId").asLong();
		Path ficheroDos = IMAGENES.resolve((String) imagenFila(dos).get("ruta"));

		JsonNode dto = leer(delete("/api/sedes/" + sede + "/fotos/2"), tGestor, 200);

		assertTrue(dto.get("foto2ImagenId").isNull());
		assertEquals(uno, dto.get("fotoImagenId").asLong(), "la foto 1 sigue");
		assertNull(foto(sede, 2));
		assertEquals(uno, (long) foto(sede, 1));
		assertEquals(0, contar("SELECT COUNT(*) FROM imagen WHERE id = " + dos));
		assertFalse(Files.exists(ficheroDos));

		// Quitar otra vez: 200 sin cambios.
		dto = leer(delete("/api/sedes/" + sede + "/fotos/2"), tGestor, 200);
		assertTrue(dto.get("foto2ImagenId").isNull());
	}

	// ------------------------------------------------------------------ validación

	@Test
	void tipoNoAdmitido_400_sinFicheroNiFila() throws Exception {
		long sede = sedeNueva("tipos");
		long ficheros = ficheros();
		long imagenes = contar("SELECT COUNT(*) FROM imagen WHERE organizacion_id = " + e.org);
		List<MockHttpServletRequestBuilder> malas = List.of(
				subida(sede, 1, imagen("gif"), "foto.gif", "image/gif"),
				subida(sede, 1, imagen("bmp"), "foto.bmp", "image/bmp"),
				subida(sede, 1, "no soy una imagen".getBytes(StandardCharsets.UTF_8), "foto.png", "image/png"),
				subida(sede, 1, new byte[0], "foto.png", "image/png"),
				subida(sede, 1, png(), "foto.heic", "image/png"),
				subida(sede, 1, png(), "foto.png", "image/heic"),
				// Más de 60 megapíxeles en la cabecera: no se decodifica.
				subida(sede, 1, cabeceraPng(10_000, 7_000), "grande.png", "image/png"));
		for (MockHttpServletRequestBuilder mala : malas) {
			MvcResult r = peticion(mala, tPropietario);
			assertEquals(400, r.getResponse().getStatus(), contenido(r));
			assertFalse(mensaje(r).isBlank(), "con el motivo");
		}
		assertNull(foto(sede, 1));
		assertEquals(ficheros, ficheros(), "ningún fichero");
		assertEquals(imagenes, contar("SELECT COUNT(*) FROM imagen WHERE organizacion_id = " + e.org), "ninguna fila");
	}

	@Test
	void numeroDeFotoNoValido_404() throws Exception {
		long sede = sedeNueva("numero");
		for (String numero : List.of("0", "3", "-1")) {
			assertEquals(404, estado(multipart(HttpMethod.PUT, "/api/sedes/" + sede + "/fotos/" + numero)
					.file(archivo(png(), "a.png", "image/png")), tPropietario), numero);
			assertEquals(404, estado(delete("/api/sedes/" + sede + "/fotos/" + numero), tPropietario), numero);
		}
		assertNull(foto(sede, 1));
		assertNull(foto(sede, 2));
	}

	/** La foto se vuelve a codificar desde los píxeles: no queda ningún metadato (EXIF con GPS, nombres…). */
	@Test
	void sinMetadatos() throws Exception {
		long sede = sedeNueva("exif");
		String secreto = "SECRETO-GPS-37.1234N-4.5678W";
		byte[] jpeg = conSegmentoApp1(imagen("jpeg"), secreto);
		assertTrue(new String(jpeg, StandardCharsets.ISO_8859_1).contains(secreto), "la prueba lleva el secreto");

		long imagen = leer(subida(sede, 1, jpeg, "foto.jpg", "image/jpeg"), tPropietario, 200).get("fotoImagenId")
				.asLong();

		String guardado = new String(Files.readAllBytes(IMAGENES.resolve((String) imagenFila(imagen).get("ruta"))),
				StandardCharsets.ISO_8859_1);
		assertFalse(guardado.contains(secreto));
		assertFalse(guardado.contains("Exif"));
	}

	// ------------------------------------------------------------------ escuela y permisos

	@Test
	void sedeDeOtraEscuela_404_yNadaCambia() throws Exception {
		long ajena = otra.sA;
		long suFoto = leer(subida(ajena, 1, png(), "a.png", "image/png"), tPropietarioOtra, 200).get("fotoImagenId")
				.asLong();
		long ficheros = ficheros();

		for (long sede : List.of(ajena, 999_999_999L)) {
			assertMensaje(peticion(subida(sede, 1, png(), "a.png", "image/png"), tPropietario), 404, NO_EXISTE);
			assertMensaje(peticion(delete("/api/sedes/" + sede + "/fotos/1"), tPropietario), 404, NO_EXISTE);
		}
		assertEquals(suFoto, (long) foto(ajena, 1), "la de la otra escuela sigue");
		assertEquals(1, contar("SELECT COUNT(*) FROM imagen WHERE id = " + suFoto));
		assertEquals(ficheros, ficheros(), "ningún fichero nuevo ni borrado");
		assertTrue(((String) imagenFila(suFoto).get("ruta")).startsWith("org-" + otra.org + "/web/sedes/"),
				"cada escuela en su carpeta");
	}

	@Test
	void soloPropietarioYGestor() throws Exception {
		long sede = sedeNueva("permisos");
		long ficheros = ficheros();
		for (String token : List.of(tRespA, tRespAC, tProfesor, tFamilia, tPlataforma)) {
			assertEquals(403, estado(subida(sede, 1, png(), "a.png", "image/png"), token));
			assertEquals(403, estado(delete("/api/sedes/" + sede + "/fotos/1"), token));
		}
		assertEquals(401, mvc.perform(subida(sede, 1, png(), "a.png", "image/png")).andReturn().getResponse()
				.getStatus());
		assertEquals(401, mvc.perform(delete("/api/sedes/" + sede + "/fotos/1")).andReturn().getResponse().getStatus());
		assertNull(foto(sede, 1));
		assertEquals(ficheros, ficheros(), "nada escrito");

		assertEquals(200, estado(subida(sede, 1, png(), "a.png", "image/png"), tPropietario));
		assertEquals(200, estado(subida(sede, 2, png(), "a.png", "image/png"), tGestor));
		assertEquals(200, estado(delete("/api/sedes/" + sede + "/fotos/1"), tGestor));
		assertEquals(200, estado(delete("/api/sedes/" + sede + "/fotos/2"), tPropietario));
	}

	// ------------------------------------------------------------------ lo público

	/** La regla nueva solo abre {@code org-<id>/web/sedes/}: alumnos, galería de eventos y lo demás siguen cerrados. */
	@Test
	void lasFotosDeAlumnosSiguenSinSerPublicas() throws Exception {
		String org = "org-" + e.org;
		List<String> privadas = List.of(org + "/alumnos/foto.webp", "alumnos/foto.webp",
				org + "/web/sedes/alumnos/foto.webp", org + "/fotos-eventos/foto.webp", org + "/web/otra/foto.webp",
				org + "/web/foto.webp", "web/sedes/foto.webp", org + "/logo/logo.png");
		for (String ruta : privadas) {
			Path fichero = IMAGENES.resolve(ruta);
			Files.createDirectories(fichero.getParent());
			Files.write(fichero, png());
		}
		for (String ruta : privadas) {
			int estado = mvc.perform(get("/imagenes/" + ruta)).andReturn().getResponse().getStatus();
			assertTrue(estado == 401 || estado == 403 || estado == 404, ruta + " -> " + estado);
		}
		String publica = org + "/web/sedes/puesta-a-mano.webp";
		Files.write(IMAGENES.resolve(publica), png());
		assertEquals(200, mvc.perform(get("/imagenes/" + publica)).andReturn().getResponse().getStatus());
	}

	// ------------------------------------------------------------------ registro

	@Test
	void cadaEscrituraDejaSuRegistro_soloConIds() throws Exception {
		long sede = sedeNueva("registro");

		long antes = ultimoEvento();
		long primera = leer(subida(sede, 1, png(), "Foto de Lucía.png", "image/png"), tPropietario, 200)
				.get("fotoImagenId").asLong();
		long segunda = leer(subida(sede, 1, png(), "b.png", "image/png"), tPropietario, 200).get("fotoImagenId")
				.asLong();
		leer(delete("/api/sedes/" + sede + "/fotos/1"), tPropietario, 200);
		leer(delete("/api/sedes/" + sede + "/fotos/1"), tPropietario, 200);

		List<Map<String, Object>> filas = eventosDesde(antes);
		assertEquals(List.of(
				"SEDES SEDE_FOTO_CAMBIADA sede=#" + sede + " foto=FOTO imagen=#" + primera + " anterior=NINGUNO",
				"SEDES SEDE_FOTO_CAMBIADA sede=#" + sede + " foto=FOTO imagen=#" + segunda + " anterior=#" + primera,
				"SEDES SEDE_FOTO_QUITADA sede=#" + sede + " foto=FOTO imagen=#" + segunda,
				"SEDES SEDE_FOTO_QUITADA sede=#" + sede + " foto=FOTO sinCambios=SI"), resumenes(filas));
		for (Map<String, Object> fila : filas) {
			assertEquals(propietario, ((Number) fila.get("usuario_id")).longValue());
			assertEquals("sedes", fila.get("modulo"));
			assertEquals(sede, ((Number) fila.get("recurso_id")).longValue());
			for (String columna : List.of("resumen", "payload_json", "query_params_json")) {
				String valor = String.valueOf(fila.get(columna));
				assertFalse(valor.contains("Luc") || valor.contains("PNG"), columna + ": " + fila);
			}
			JsonNode visto = leer(get("/api/admin/auditoria/eventos/" + ((Number) fila.get("id")).longValue()),
					tPropietario, 200);
			assertEquals(fila.get("resumen"), visto.get("resumen").asText(), "el propietario la ve");
		}
	}

	/** Las dos rutas están en EscrituraConRegistroPropio: el 2xx deja solo su registro; el 4xx, solo el genérico. */
	@Test
	void elFiltroNoDuplica_yLosFallosQuedanEnElGenerico() throws Exception {
		long sede = sedeNueva("filtro");
		String ruta = "/api/sedes/" + sede + "/fotos/2";
		assertTrue(EscrituraConRegistroPropio.SEDE_FOTO_CAMBIAR.reconoce("PUT", ruta));
		assertTrue(EscrituraConRegistroPropio.SEDE_FOTO_QUITAR.reconoce("DELETE", ruta));

		long antes = ultimoEvento();
		peticionCorrecta(subida(sede, 2, png(), "a.png", "image/png"));
		peticionCorrecta(delete(ruta));
		List<Map<String, Object>> filas = eventosDesde(antes);
		assertEquals(2, filas.size(), filas.toString());
		for (Map<String, Object> fila : filas) {
			assertFalse(((String) fila.get("resumen")).matches(RESUMEN_GENERICO), "sin la fila genérica: " + fila);
		}

		antes = ultimoEvento();
		assertEquals(400, estado(subida(sede, 2, imagen("gif"), "a.gif", "image/gif"), tPropietario));
		assertEquals(404, estado(delete("/api/sedes/" + otra.sA + "/fotos/2"), tPropietario));
		filas = eventosDesde(antes);
		assertEquals(2, filas.size(), filas.toString());
		for (Map<String, Object> fila : filas) {
			assertTrue(((String) fila.get("resumen")).matches(RESUMEN_GENERICO), fila.toString());
			String carga = String.valueOf(fila.get("payload_json"));
			assertFalse(carga.contains("GIF8"), "sin la imagen: " + carga);
		}
	}

	/** Si la transacción falla, el fichero recién escrito no se queda y la foto anterior sigue. */
	@Test
	void falloEnLaTransaccion_noDejaFichero() throws Exception {
		long sede = sedeNueva("fallo");
		long anterior = leer(subida(sede, 1, png(), "a.png", "image/png"), tPropietario, 200).get("fotoImagenId")
				.asLong();
		long ficheros = ficheros();
		String trigger = "it_sede_foto_falla_" + e.marca.replace('-', '_');
		jdbc.execute("CREATE TRIGGER " + trigger + " BEFORE UPDATE ON sede FOR EACH ROW BEGIN IF NEW.id = " + sede
				+ " THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'fallo de prueba'; END IF; END");
		try {
			// Sin manejador para el error de la base: MockMvc lo relanza (en el servidor, 500).
			Exception error = assertThrows(Exception.class,
					() -> estado(subida(sede, 1, png(), "b.png", "image/png"), tPropietario));
			assertTrue(String.valueOf(error.getMessage()).contains("fallo de prueba"), error.toString());
		} finally {
			jdbc.execute("DROP TRIGGER " + trigger);
		}
		assertEquals(anterior, (long) foto(sede, 1));
		assertEquals(1, contar("SELECT COUNT(*) FROM imagen WHERE id = " + anterior));
		assertTrue(Files.exists(IMAGENES.resolve((String) imagenFila(anterior).get("ruta"))));
		assertEquals(ficheros, ficheros(), "el fichero nuevo no se queda");
	}

	// ------------------------------------------------------------------ datos

	private void membresia(long organizacion, long usuario, String rol) {
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", usuario, organizacion,
				rol);
	}

	private long sedeNueva(String nombre) throws Exception {
		String cuerpo = json.writeValueAsString(Map.of("nombre", "IT fotos " + nombre + " " + e.marca));
		MvcResult r = peticion(post("/api/sedes").contentType(MediaType.APPLICATION_JSON).content(cuerpo), tPropietario);
		assertEquals(201, r.getResponse().getStatus(), contenido(r));
		return json.readTree(contenido(r)).get("id").asLong();
	}

	private Long foto(long sede, int numero) {
		String columna = numero == 1 ? "foto_imagen_id" : "foto2_imagen_id";
		return jdbc.queryForObject("SELECT " + columna + " FROM sede WHERE id = ?", Long.class, sede);
	}

	private Map<String, Object> imagenFila(long imagen) {
		return jdbc.queryForMap("SELECT ruta, url, organizacion_id FROM imagen WHERE id = ?", imagen);
	}

	private long contar(String sql) {
		return jdbc.queryForObject(sql, Long.class);
	}

	/** Todos los ficheros bajo la raíz de imágenes. */
	private static long ficheros() throws IOException {
		try (Stream<Path> rutas = Files.walk(IMAGENES)) {
			return rutas.filter(Files::isRegularFile).count();
		}
	}

	private MockHttpServletRequestBuilder subida(long sede, int numero, byte[] contenido, String nombre, String tipo) {
		return multipart(HttpMethod.PUT, "/api/sedes/" + sede + "/fotos/" + numero).file(archivo(contenido, nombre, tipo));
	}

	private static MockMultipartFile archivo(byte[] contenido, String nombre, String tipo) {
		return new MockMultipartFile("archivo", nombre, tipo, contenido);
	}

	private static byte[] png() throws IOException {
		return png(640, 480);
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

	/** Un segmento APP1 «Exif» con {@code texto} justo tras el SOI del JPEG. */
	private static byte[] conSegmentoApp1(byte[] jpeg, String texto) {
		byte[] carga = ("Exif\0\0" + texto).getBytes(StandardCharsets.ISO_8859_1);
		int longitud = carga.length + 2;
		ByteBuffer salida = ByteBuffer.allocate(jpeg.length + 4 + carga.length);
		salida.put(jpeg, 0, 2);
		salida.put((byte) 0xFF).put((byte) 0xE1).put((byte) (longitud >> 8)).put((byte) longitud);
		salida.put(carga);
		salida.put(jpeg, 2, jpeg.length - 2);
		return salida.array();
	}

	// ------------------------------------------------------------------ auditoría y HTTP

	private long ultimoEvento() {
		return jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM auditoria_evento", Long.class);
	}

	private List<Map<String, Object>> eventosDesde(long id) {
		return jdbc.queryForList("SELECT * FROM auditoria_evento WHERE id > ? AND organizacion_id = ? ORDER BY id", id,
				e.org);
	}

	private static List<String> resumenes(List<Map<String, Object>> filas) {
		List<String> resumenes = new ArrayList<>();
		filas.forEach(f -> resumenes.add((String) f.get("resumen")));
		return resumenes;
	}

	private void peticionCorrecta(MockHttpServletRequestBuilder peticion) throws Exception {
		MvcResult r = peticion(peticion, tPropietario);
		assertEquals(200, r.getResponse().getStatus(), contenido(r));
	}

	private JsonNode leer(MockHttpServletRequestBuilder peticion, String token, int esperado) throws Exception {
		MvcResult r = peticion(peticion, token);
		assertEquals(esperado, r.getResponse().getStatus(), contenido(r));
		return json.readTree(contenido(r));
	}

	private int estado(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return peticion(peticion, token).getResponse().getStatus();
	}

	private MvcResult peticion(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
	}

	private void assertMensaje(MvcResult r, int estado, String mensaje) throws Exception {
		assertEquals(estado, r.getResponse().getStatus(), contenido(r));
		assertEquals(mensaje, mensaje(r));
	}

	private String mensaje(MvcResult r) throws Exception {
		String cuerpo = contenido(r);
		if (cuerpo.isBlank()) {
			return "";
		}
		JsonNode nodo = json.readTree(cuerpo);
		return nodo.hasNonNull("mensaje") ? nodo.get("mensaje").asText() : "";
	}

	private static String contenido(MvcResult r) throws Exception {
		return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}
}
