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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;

/**
 * Fotos de eventos (decisión 342, fase3-e8c-plan.md, pieza F1) con la cadena de filtros completa contra MySQL real. Lo
 * crítico: pueden mostrar menores, y la foto de un evento oculto no se sirve a anónimos ni a familias por ninguna vía. La
 * raíz de imágenes apunta a un directorio temporal. Los fallos de transacción se provocan con un trigger en MySQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FotosEventoIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final int TOPE = 40;
	private static final Path RAIZ;
	private static final Path IMAGENES;
	private static final Path DOCUMENTOS;

	static {
		try {
			RAIZ = Files.createTempDirectory("taemoi-it-fotos-").toAbsolutePath().normalize();
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
	@Autowired PasswordEncoder encoder;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired MembresiaRepository membresias;
	@Autowired OrganizacionRepository organizaciones;
	@Autowired EntityManagerFactory emf;
	@Value("${app.base.url}") String baseUrl;

	final String sufijo = UUID.randomUUID().toString().substring(0, 8);
	long org2;
	String tokenPropietario;
	String tokenGestor;
	String tokenFamilia;
	String tokenPropietario2;

	@BeforeAll
	void preparar() {
		org2 = TenantContext.calcularCon(ORG1, () -> {
			Organizacion org = new Organizacion();
			org.setSlug("it-fotos-" + sufijo);
			org.setNombre("Escuela fotos " + sufijo);
			return organizaciones.save(org).getId();
		});
		tokenPropietario = token(ORG1, RolMembresia.PROPIETARIO, "prop");
		tokenGestor = token(ORG1, RolMembresia.ADMINISTRADOR, "gestor");
		tokenFamilia = token(ORG1, RolMembresia.FAMILIA, "familia");
		tokenPropietario2 = token(org2, RolMembresia.PROPIETARIO, "prop2");
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	@AfterAll
	void borrarRaiz() throws IOException {
		try (Stream<Path> rutas = Files.walk(RAIZ)) {
			rutas.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		}
	}

	// ------------------------------------------------------------------ visibilidad

	@Test
	void eventoVisible_laListaYLaImagenSonPublicas() throws Exception {
		long evento = evento(ORG1, true);
		JsonNode foto = subir(evento, tokenGestor);
		long fotoId = foto.get("id").asLong();
		String imagen = "/api/eventos/" + evento + "/fotos/" + fotoId;

		for (String token : Arrays.asList(null, tokenFamilia, tokenGestor, tokenPropietario)) {
			JsonNode lista = leer("/api/eventos/" + evento + "/fotos", token);
			assertEquals(1, lista.size());
			assertEquals(fotoId, lista.get(0).get("id").asLong());
			assertEquals(baseUrl + imagen, lista.get(0).get("url").asText());

			MockHttpServletResponse r = mvc.perform(conToken(get(imagen), token)).andReturn().getResponse();
			assertEquals(200, r.getStatus());
			assertTrue(r.getContentAsByteArray().length > 0);
			assertTrue(r.getHeader("Cache-Control").contains("public"), r.getHeader("Cache-Control"));
			assertTrue(r.getHeader("Cache-Control").contains("must-revalidate"), r.getHeader("Cache-Control"));
			assertNotNull(r.getHeader("ETag"));
			assertNotNull(r.getHeader("Last-Modified"));
		}
	}

	@Test
	void eventoOculto_404ParaAnonimoYFamilia_enListaEImagen() throws Exception {
		long evento = evento(ORG1, true);
		long fotoId = subir(evento, tokenGestor).get("id").asLong();
		long sinFotos = evento(ORG1, false);
		String lista = "/api/eventos/" + evento + "/fotos";
		String imagen = lista + "/" + fotoId;
		String etag = mvc.perform(get(imagen)).andReturn().getResponse().getHeader("ETag");
		ocultar(evento);

		for (String token : Arrays.asList(null, tokenFamilia)) {
			assertNoEncontrado(conToken(get(lista), token));
			assertNoEncontrado(conToken(head(lista), token));
			assertNoEncontrado(conToken(get(imagen), token));
			assertNoEncontrado(conToken(head(imagen), token));
			assertNoEncontrado(conToken(get(imagen).param("w", "480"), token));
			// Con caché de antes de ocultarlo: un 304 sería responder que la foto sigue ahí.
			assertNoEncontrado(conToken(get(imagen).header("If-None-Match", etag), token));
			// Mismo 404 que si no existiera nada.
			assertNoEncontrado(conToken(get(lista + "/999999999"), token));
			assertNoEncontrado(conToken(get("/api/eventos/" + sinFotos + "/fotos"), token));
		}
	}

	@Test
	void eventoSinVisibilidad_seTrataComoOculto() throws Exception {
		long evento = evento(ORG1, true);
		long fotoId = subir(evento, tokenGestor).get("id").asLong();
		jdbc.update("UPDATE evento SET visible = NULL WHERE id = ?", evento);

		assertNoEncontrado(get("/api/eventos/" + evento + "/fotos"));
		assertNoEncontrado(get("/api/eventos/" + evento + "/fotos/" + fotoId));
		assertEquals(1, leer("/api/eventos/" + evento + "/fotos", tokenGestor).size());
	}

	@Test
	void eventoOculto_gestionLoVeSinCacheCompartida() throws Exception {
		long evento = evento(ORG1, false);
		long fotoId = subir(evento, tokenGestor).get("id").asLong();
		String imagen = "/api/eventos/" + evento + "/fotos/" + fotoId;

		for (String token : List.of(tokenGestor, tokenPropietario)) {
			assertEquals(1, leer("/api/eventos/" + evento + "/fotos", token).size());
			MockHttpServletResponse r = mvc.perform(conToken(get(imagen), token)).andReturn().getResponse();
			assertEquals(200, r.getStatus());
			assertTrue(r.getHeader("Cache-Control").contains("private"), r.getHeader("Cache-Control"));
			assertFalse(r.getHeader("Cache-Control").contains("public"), r.getHeader("Cache-Control"));
		}
	}

	@Test
	void anchoSolicitado_seRespetaEnElETag_eIfNoneMatchDa304() throws Exception {
		long evento = evento(ORG1, true);
		long fotoId = subir(evento, tokenGestor).get("id").asLong();
		String imagen = "/api/eventos/" + evento + "/fotos/" + fotoId;

		MockHttpServletResponse r = mvc.perform(get(imagen).param("w", "480")).andReturn().getResponse();

		assertEquals(200, r.getStatus());
		assertTrue(r.getHeader("ETag").contains("-480-"), r.getHeader("ETag"));
		assertEquals(304, estado(get(imagen).param("w", "480").header("If-None-Match", r.getHeader("ETag"))));
	}

	// ------------------------------------------------------------------ nada de rutas

	@Test
	void laApiNuncaDevuelveLaRutaDelFichero() throws Exception {
		long evento = evento(ORG1, true);
		JsonNode foto = subir(evento, tokenGestor);
		long fotoId = foto.get("id").asLong();
		String fichero = Path.of(ruta(fotoId)).getFileName().toString();
		assertEquals(Set.of("id", "url", "orden"), campos(foto));

		List<String> respuestas = new ArrayList<>();
		respuestas.add(foto.toString());
		respuestas.add(leer("/api/eventos/" + evento + "/fotos", null).toString());
		respuestas.add(leer("/api/eventos/" + evento, null).toString());
		respuestas.add(leer("/api/eventos", null).toString());
		respuestas.add(leer("/api/eventos/admin/todos", tokenGestor).toString());
		respuestas.add(reordenar(evento, tokenGestor, List.of(fotoId)).getContentAsString());
		for (String respuesta : respuestas) {
			assertFalse(respuesta.contains("fotos-eventos"), respuesta);
			assertFalse(respuesta.contains("/imagenes/"), respuesta);
			assertFalse(respuesta.contains(fichero), respuesta);
		}

		MockHttpServletResponse imagen = mvc.perform(get("/api/eventos/" + evento + "/fotos/" + fotoId)).andReturn()
				.getResponse();
		assertFalse(imagen.getHeader("Content-Disposition").contains(fichero), imagen.getHeader("Content-Disposition"));
	}

	/** Ni colección en el JSON del evento: la lista se pide por su endpoint. */
	@Test
	void elJsonDelEventoNoLlevaLasFotos() throws Exception {
		long evento = evento(ORG1, true);
		subir(evento, tokenGestor);

		JsonNode detalle = leer("/api/eventos/" + evento, null);
		assertFalse(detalle.has("fotos"), detalle.toString());
		assertFalse(fila(leer("/api/eventos", null), evento).has("fotos"));
		assertTrue(detalle.get("fotoEvento").isNull(), "El cartel sigue siendo aparte");
	}

	// ------------------------------------------------------------------ fichero directo

	@Test
	void elFicheroDirectoNoSeSirveSinSesionNiAUnaFamilia() throws Exception {
		long evento = evento(ORG1, true);
		long fotoId = subir(evento, tokenGestor).get("id").asLong();
		String ruta = "/imagenes/" + ruta(fotoId);
		assertTrue(ruta.startsWith("/imagenes/org-1/fotos-eventos/"), ruta);
		assertTrue(Files.isRegularFile(IMAGENES.resolve(ruta(fotoId))));

		for (MockHttpServletRequestBuilder peticion : List.of(get(ruta), head(ruta), get(ruta).cookie(jwt(tokenFamilia)),
				head(ruta).cookie(jwt(tokenFamilia)), get(ruta).cookie(jwt(tokenPropietario2)))) {
			assertNoEncontrado(peticion);
		}
		ocultar(evento);
		assertNoEncontrado(get(ruta));
		assertNoEncontrado(get(ruta).cookie(jwt(tokenFamilia)));
	}

	/** Como las fotos de alumnos: el personal de la escuela, con fila y sin caché. Nunca se le da esta URL. */
	@Test
	void elFicheroDirectoSoloLoVeElPersonalDeLaEscuela_sinCache() throws Exception {
		long evento = evento(ORG1, false);
		long fotoId = subir(evento, tokenGestor).get("id").asLong();

		MockHttpServletResponse r = mvc.perform(get("/imagenes/" + ruta(fotoId)).cookie(jwt(tokenGestor))).andReturn()
				.getResponse();

		assertEquals(200, r.getStatus());
		assertTrue(r.getHeader("Cache-Control").contains("no-store"), r.getHeader("Cache-Control"));
	}

	// ------------------------------------------------------------------ aislamiento

	@Test
	void otraEscuelaNoVeNiTocaLasFotos() throws Exception {
		long evento = evento(ORG1, true);
		long fotoId = subir(evento, tokenGestor).get("id").asLong();
		String lista = "/api/eventos/" + evento + "/fotos";
		String imagen = lista + "/" + fotoId;

		assertNoEncontrado(get(lista).cookie(jwt(tokenPropietario2)));
		assertNoEncontrado(get(imagen).cookie(jwt(tokenPropietario2)));
		assertEquals(404, estado(subida(evento, png(), "foto.png", "image/png").cookie(jwt(tokenPropietario2))));
		assertEquals(404, reordenar(evento, tokenPropietario2, List.of(fotoId)).getStatus());
		assertEquals(404, estado(delete(imagen).cookie(jwt(tokenPropietario2))));

		assertEquals(1, leer(lista, tokenGestor).size());
		assertTrue(Files.isRegularFile(IMAGENES.resolve(ruta(fotoId))));
	}

	@Test
	void lasFotosDeOtraEscuelaVanASuCarpeta_yElAnonimoNoLasVe() throws Exception {
		long eventoOrg2 = evento(org2, true);
		long fotoId = subir(eventoOrg2, tokenPropietario2).get("id").asLong();
		assertTrue(ruta(fotoId).startsWith("org-" + org2 + "/fotos-eventos/"), ruta(fotoId));
		assertEquals(org2, jdbc.queryForObject("SELECT organizacion_id FROM evento_foto WHERE id = ?", Long.class, fotoId));
		assertEquals(org2, jdbc.queryForObject("""
				SELECT i.organizacion_id FROM imagen i JOIN evento_foto f ON f.imagen_id = i.id WHERE f.id = ?""",
				Long.class, fotoId));

		// El anónimo es de la escuela por defecto (decisión 10): la de otra no existe para él.
		assertNoEncontrado(get("/api/eventos/" + eventoOrg2 + "/fotos"));
		assertNoEncontrado(get("/api/eventos/" + eventoOrg2 + "/fotos/" + fotoId));
		assertNoEncontrado(get("/api/eventos/" + eventoOrg2 + "/fotos").cookie(jwt(tokenGestor)));
		assertEquals(200, estado(get("/api/eventos/" + eventoOrg2 + "/fotos/" + fotoId).cookie(jwt(tokenPropietario2))));
	}

	@Test
	void unaFotoDeOtroEventoEs404() throws Exception {
		long evento = evento(ORG1, true);
		long otro = evento(ORG1, true);
		long fotoId = subir(evento, tokenGestor).get("id").asLong();
		String cruzada = "/api/eventos/" + otro + "/fotos/" + fotoId;

		assertNoEncontrado(get(cruzada));
		assertNoEncontrado(get(cruzada).cookie(jwt(tokenGestor)));
		assertEquals(404, estado(delete(cruzada).cookie(jwt(tokenGestor))));
		assertTrue(Files.isRegularFile(IMAGENES.resolve(ruta(fotoId))));
	}

	/** Un evento visible no destapa la foto de uno oculto por la URL del visible. */
	@Test
	void unaFotoDeUnEventoOcultoNoSaleDesdeUnEventoVisible() throws Exception {
		long visible = evento(ORG1, true);
		long oculto = evento(ORG1, false);
		long fotoOculta = subir(oculto, tokenGestor).get("id").asLong();

		assertNoEncontrado(get("/api/eventos/" + visible + "/fotos/" + fotoOculta));
		assertNoEncontrado(get("/api/eventos/" + visible + "/fotos/" + fotoOculta).cookie(jwt(tokenFamilia)));
	}

	@Test
	void eventoInexistente_es404() throws Exception {
		assertNoEncontrado(get("/api/eventos/999999999/fotos"));
		assertNoEncontrado(get("/api/eventos/999999999/fotos/1"));
		assertEquals(404, estado(subida(999999999L, png(), "foto.png", "image/png").cookie(jwt(tokenGestor))));
		assertEquals(404, reordenar(999999999L, tokenGestor, List.of()).getStatus());
		assertEquals(404, estado(delete("/api/eventos/999999999/fotos/1").cookie(jwt(tokenGestor))));
	}

	// ------------------------------------------------------------------ permisos de escritura

	@Test
	void soloGestionEscribe() throws Exception {
		long evento = evento(ORG1, true);
		long fotoId = subir(evento, tokenGestor).get("id").asLong();
		String imagen = "/api/eventos/" + evento + "/fotos/" + fotoId;
		String ficheroAntes = ruta(fotoId);

		for (String token : Arrays.asList(null, tokenFamilia)) {
			assertTrue(rechazado(conToken(subida(evento, png(), "foto.png", "image/png"), token)));
			assertTrue(rechazado(conToken(put("/api/eventos/" + evento + "/fotos/orden")
					.contentType(MediaType.APPLICATION_JSON).content("[" + fotoId + "]"), token)));
			assertTrue(rechazado(conToken(delete(imagen), token)));
		}
		assertEquals(1, leer("/api/eventos/" + evento + "/fotos", tokenGestor).size());
		assertTrue(Files.isRegularFile(IMAGENES.resolve(ficheroAntes)));

		assertEquals(201, estado(subida(evento, png(), "otra.png", "image/png").cookie(jwt(tokenPropietario))));
	}

	// ------------------------------------------------------------------ validación

	@Test
	void heicYContenidoNoValidoSeRechazan_sinDejarNada() throws Exception {
		long evento = evento(ORG1, true);
		long ficherosAntes = ficheros(ORG1);
		byte[] heic = new byte[] { 0, 0, 0, 24, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c' };

		MockHttpServletResponse porExtension = mvc.perform(subida(evento, heic, "IMG_0001.HEIC", "application/octet-stream")
				.cookie(jwt(tokenGestor))).andReturn().getResponse();
		MockHttpServletResponse porTipo = mvc.perform(subida(evento, heic, "foto.jpg", "image/heic")
				.cookie(jwt(tokenGestor))).andReturn().getResponse();
		MockHttpServletResponse noImagen = mvc.perform(subida(evento, "MZ no".getBytes(), "foto.png", "image/png")
				.cookie(jwt(tokenGestor))).andReturn().getResponse();

		assertEquals(400, porExtension.getStatus());
		assertEquals(400, porTipo.getStatus());
		assertEquals(400, noImagen.getStatus());
		assertFalse(json.readTree(porExtension.getContentAsString()).get("mensaje").asText().isBlank());
		assertEquals(0, filas(evento));
		assertEquals(ficherosAntes, ficheros(ORG1));
	}

	@Test
	void sinFichero_es400() throws Exception {
		long evento = evento(ORG1, true);
		assertEquals(400, estado(multipart("/api/eventos/" + evento + "/fotos").cookie(jwt(tokenGestor))));
	}

	// ------------------------------------------------------------------ tope

	@Test
	void tope_laFoto41SeRechazaConMensaje_sinDejarFichero() throws Exception {
		long evento = evento(ORG1, true);
		rellenar(evento, TOPE - 1);

		assertEquals(TOPE, subir(evento, tokenGestor).get("orden").asInt());
		long ficherosAntes = ficheros(ORG1);

		MockHttpServletResponse r = mvc.perform(subida(evento, png(), "foto.png", "image/png").cookie(jwt(tokenGestor)))
				.andReturn().getResponse();

		assertEquals(409, r.getStatus());
		assertTrue(json.readTree(r.getContentAsString()).get("mensaje").asText().contains("40"), r.getContentAsString());
		assertEquals(TOPE, filas(evento));
		assertEquals(ficherosAntes, ficheros(ORG1));
	}

	/** Ocho subidas a la vez con sitio para dos: solo dos entran y las demás no dejan fichero. */
	@Test
	void tope_aguantaSubidasSimultaneas() throws Exception {
		int hilos = 8;
		long evento = evento(ORG1, true);
		rellenar(evento, TOPE - 2);
		long ficherosAntes = ficheros(ORG1);
		byte[] imagen = png();
		CyclicBarrier salida = new CyclicBarrier(hilos);
		ExecutorService ejecutor = Executors.newFixedThreadPool(hilos);
		List<Future<Integer>> resultados = new ArrayList<>();
		try {
			for (int i = 0; i < hilos; i++) {
				resultados.add(ejecutor.submit(() -> {
					salida.await(30, TimeUnit.SECONDS);
					return estado(subida(evento, imagen, "foto.png", "image/png").cookie(jwt(tokenGestor)));
				}));
			}
			Map<Integer, Integer> porEstado = new HashMap<>();
			for (Future<Integer> resultado : resultados) {
				porEstado.merge(resultado.get(120, TimeUnit.SECONDS), 1, Integer::sum);
			}

			assertEquals(Map.of(201, 2, 409, hilos - 2), porEstado);
		} finally {
			ejecutor.shutdownNow();
		}
		assertEquals(TOPE, filas(evento));
		assertEquals(ficherosAntes + 2, ficheros(ORG1));
		assertEquals(TOPE, jdbc.queryForObject("SELECT COUNT(DISTINCT orden) FROM evento_foto WHERE evento_id = ?",
				Integer.class, evento), "Cada foto con su orden");
	}

	// ------------------------------------------------------------------ orden

	@Test
	void reordenar_guardaElOrdenPedido_yLaSiguienteVaAlFinal() throws Exception {
		long evento = evento(ORG1, true);
		long a = subir(evento, tokenGestor).get("id").asLong();
		long b = subir(evento, tokenGestor).get("id").asLong();
		long c = subir(evento, tokenGestor).get("id").asLong();
		assertEquals(List.of(a, b, c), ids(leer("/api/eventos/" + evento + "/fotos", null)));

		MockHttpServletResponse r = reordenar(evento, tokenPropietario, List.of(c, a, b));

		assertEquals(200, r.getStatus());
		assertEquals(List.of(c, a, b), ids(json.readTree(r.getContentAsString())));
		assertEquals(List.of(c, a, b), ids(leer("/api/eventos/" + evento + "/fotos", null)));

		long d = subir(evento, tokenGestor).get("id").asLong();
		assertEquals(List.of(c, a, b, d), ids(leer("/api/eventos/" + evento + "/fotos", null)));
	}

	@Test
	void reordenar_conUnConjuntoDistintoSeRechaza_sinCambiarNada() throws Exception {
		long evento = evento(ORG1, true);
		long a = subir(evento, tokenGestor).get("id").asLong();
		long b = subir(evento, tokenGestor).get("id").asLong();
		long otro = evento(ORG1, true);
		long ajena = subir(otro, tokenGestor).get("id").asLong();

		List<List<Long>> malos = List.of(List.of(), List.of(a), List.of(a, a), List.of(a, b, b), List.of(a, b, ajena),
				List.of(a, ajena), List.of(a, 999999999L));
		for (List<Long> malo : malos) {
			MockHttpServletResponse r = reordenar(evento, tokenGestor, malo);
			assertEquals(400, r.getStatus(), malo.toString());
			assertFalse(json.readTree(r.getContentAsString()).get("mensaje").asText().isBlank());
		}
		MockHttpServletResponse conNulo = mvc.perform(put("/api/eventos/" + evento + "/fotos/orden")
				.contentType(MediaType.APPLICATION_JSON).content("[" + a + ",null]").cookie(jwt(tokenGestor)))
				.andReturn().getResponse();
		assertEquals(400, conNulo.getStatus());

		assertEquals(List.of(a, b), ids(leer("/api/eventos/" + evento + "/fotos", null)));
	}

	@Test
	void reordenar_unEventoSinFotosConListaVacia_noHaceNada() throws Exception {
		assertEquals(200, reordenar(evento(ORG1, true), tokenGestor, List.of()).getStatus());
	}

	// ------------------------------------------------------------------ borrado

	@Test
	void borrarUnaFoto_quitaFilaImagenYFichero_yElRestoSigue() throws Exception {
		long evento = evento(ORG1, true);
		long a = subir(evento, tokenGestor).get("id").asLong();
		long b = subir(evento, tokenGestor).get("id").asLong();
		Path ficheroA = IMAGENES.resolve(ruta(a));
		Path ficheroB = IMAGENES.resolve(ruta(b));
		long imagenA = imagenDe(a);

		assertEquals(200, estado(delete("/api/eventos/" + evento + "/fotos/" + a).cookie(jwt(tokenGestor))));

		assertFalse(Files.exists(ficheroA));
		assertTrue(Files.exists(ficheroB));
		assertEquals(0, contar("SELECT COUNT(*) FROM imagen WHERE id = " + imagenA));
		assertEquals(List.of(b), ids(leer("/api/eventos/" + evento + "/fotos", null)));
		assertEquals(404, estado(delete("/api/eventos/" + evento + "/fotos/" + a).cookie(jwt(tokenGestor))));
	}

	@Test
	void borrarElEvento_seLlevaSusFotosYFicheros_yElCartel() throws Exception {
		long evento = eventoConCartel();
		String rutaCartel = jdbc.queryForObject(
				"SELECT i.ruta FROM imagen i JOIN evento e ON e.foto_evento_id = i.id WHERE e.id = ?", String.class, evento);
		long a = subir(evento, tokenGestor).get("id").asLong();
		long b = subir(evento, tokenGestor).get("id").asLong();
		Path ficheroA = IMAGENES.resolve(ruta(a));
		Path ficheroB = IMAGENES.resolve(ruta(b));
		List<Long> imagenes = List.of(imagenDe(a), imagenDe(b));

		assertEquals(200, estado(delete("/api/eventos/" + evento).cookie(jwt(tokenGestor))));

		assertEquals(0, contar("SELECT COUNT(*) FROM evento WHERE id = " + evento));
		assertEquals(0, filas(evento));
		assertEquals(0, contar("SELECT COUNT(*) FROM imagen WHERE id IN (" + imagenes.get(0) + "," + imagenes.get(1) + ")"));
		assertFalse(Files.exists(ficheroA));
		assertFalse(Files.exists(ficheroB));
		assertFalse(Files.exists(IMAGENES.resolve(rutaCartel)), "El cartel se borra como siempre");
	}

	@Test
	void borrarUnEventoSinFotos_sigueFuncionando() throws Exception {
		long evento = evento(ORG1, true);
		assertEquals(200, estado(delete("/api/eventos/" + evento).cookie(jwt(tokenGestor))));
		assertEquals(0, contar("SELECT COUNT(*) FROM evento WHERE id = " + evento));
	}

	// ------------------------------------------------------------------ transacción que falla

	@Test
	void siFallaLaTransaccionAlSubir_seBorraElFichero() throws Exception {
		long evento = evento(ORG1, true);
		long ficherosAntes = ficheros(ORG1);
		long imagenesAntes = contar("SELECT COUNT(*) FROM imagen");

		conTriggerQueFalla("BEFORE INSERT", "NEW", evento, () -> {
			assertEquals(500, estado(subida(evento, png(), "foto.png", "image/png").cookie(jwt(tokenGestor))));
		});

		assertEquals(ficherosAntes, ficheros(ORG1), "El fichero ya escrito se borra");
		assertEquals(imagenesAntes, contar("SELECT COUNT(*) FROM imagen"), "La fila de imagen se deshace");
		assertEquals(0, filas(evento));
	}

	@Test
	void siFallaLaTransaccionAlBorrarLaFoto_elFicheroSeQueda() throws Exception {
		long evento = evento(ORG1, true);
		long foto = subir(evento, tokenGestor).get("id").asLong();
		Path fichero = IMAGENES.resolve(ruta(foto));

		conTriggerQueFalla("BEFORE DELETE", "OLD", evento, () -> {
			assertEquals(500, estado(delete("/api/eventos/" + evento + "/fotos/" + foto).cookie(jwt(tokenGestor))));
		});

		assertTrue(Files.exists(fichero), "La fila sigue: el fichero no se puede ir");
		assertEquals(1, filas(evento));
		assertEquals(200, estado(get("/api/eventos/" + evento + "/fotos/" + foto)));
	}

	@Test
	void siFallaLaTransaccionAlBorrarElEvento_susFotosSiguen() throws Exception {
		long evento = evento(ORG1, true);
		long foto = subir(evento, tokenGestor).get("id").asLong();
		Path fichero = IMAGENES.resolve(ruta(foto));

		conTriggerQueFalla("BEFORE DELETE", "OLD", evento, () -> {
			assertEquals(500, estado(delete("/api/eventos/" + evento).cookie(jwt(tokenGestor))));
		});

		assertTrue(Files.exists(fichero));
		assertEquals(1, filas(evento));
		assertEquals(1, contar("SELECT COUNT(*) FROM evento WHERE id = " + evento));
	}

	// ------------------------------------------------------------------ numeroFotos

	@Test
	void numeroFotos_enLaListaPublicaYEnLaDeGestion() throws Exception {
		long conFotos = evento(ORG1, true);
		long sinFotos = evento(ORG1, true);
		long oculto = evento(ORG1, false);
		subir(conFotos, tokenGestor);
		subir(conFotos, tokenGestor);
		subir(oculto, tokenGestor);

		JsonNode publica = leer("/api/eventos", null);
		assertEquals(2, fila(publica, conFotos).get("numeroFotos").asInt());
		assertEquals(0, fila(publica, sinFotos).get("numeroFotos").asInt());
		assertFalse(ids(publica).contains(oculto), "La lista pública no trae ocultos");

		JsonNode gestion = leer("/api/eventos/admin/todos", tokenGestor);
		assertEquals(2, fila(gestion, conFotos).get("numeroFotos").asInt());
		assertEquals(0, fila(gestion, sinFotos).get("numeroFotos").asInt());
		assertEquals(1, fila(gestion, oculto).get("numeroFotos").asInt());
	}

	/** Las mismas sentencias con fotos que sin ellas, y una sola consulta de recuento sea cual sea el número de eventos. */
	@Test
	void numeroFotos_sinConsultasPorEvento() throws Exception {
		List<Long> eventos = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			eventos.add(evento(ORG1, true));
		}
		for (String[] caso : new String[][] { { "/api/eventos", null }, { "/api/eventos/admin/todos", tokenGestor } }) {
			Statistics st = emf.unwrap(SessionFactory.class).getStatistics();
			boolean activas = st.isStatisticsEnabled();
			st.setStatisticsEnabled(true);
			try {
				leer(caso[0], caso[1]);
				st.clear();
				leer(caso[0], caso[1]);
				long sinFotos = st.getPrepareStatementCount();
				assertTrue(sinFotos > 0, "las estadísticas cuentan");

				for (long evento : eventos) {
					rellenar(evento, 2);
				}
				st.clear();
				JsonNode lista = leer(caso[0], caso[1]);
				long conFotos = st.getPrepareStatementCount();

				assertEquals(sinFotos, conFotos, "ninguna consulta por evento para sus fotos");
				assertTrue(lista.size() >= eventos.size());
				long recuentos = Arrays.stream(st.getQueries()).filter(q -> q.contains("EventoFoto"))
						.mapToLong(q -> st.getQueryStatistics(q).getExecutionCount()).sum();
				assertEquals(1, recuentos, "un recuento para toda la lista");
				for (long evento : eventos) {
					jdbc.update("DELETE FROM evento_foto WHERE evento_id = ?", evento);
				}
			} finally {
				st.setStatisticsEnabled(activas);
			}
		}
	}

	// ------------------------------------------------------------------ auditoría

	@Test
	void lasEscriturasQuedanAuditadas_sinGuardarLaImagen() throws Exception {
		long evento = evento(ORG1, true);
		String ruta = "/api/eventos/" + evento + "/fotos";

		subir(evento, tokenGestor);

		String sql = "SELECT COUNT(*) FROM auditoria_evento WHERE endpoint = '" + ruta + "' AND metodo_http = 'POST'"
				+ " AND estado_http = 201";
		assertEquals(1, contar(sql));
		List<String> cargas = jdbc.queryForList("SELECT payload_json FROM auditoria_evento WHERE endpoint = ?", String.class,
				ruta);
		cargas.stream().filter(c -> c != null).forEach(c -> assertFalse(c.contains("PNG"), c));
	}

	// ------------------------------------------------------------------ utilidades

	private long evento(long organizacion, boolean visible) {
		String titulo = "IT fotos " + UUID.randomUUID();
		jdbc.update("""
				INSERT INTO evento (titulo, descripcion, visible, orden, organizacion_id)
				VALUES (?, 'Descripción de prueba', ?, 1000, ?)""", titulo, visible, organizacion);
		return jdbc.queryForObject("SELECT id FROM evento WHERE titulo = ?", Long.class, titulo);
	}

	private long eventoConCartel() throws Exception {
		String titulo = "IT cartel " + UUID.randomUUID();
		String nuevo = "{\"titulo\":\"" + titulo + "\",\"descripcion\":\"Con cartel\",\"orden\":1000}";
		MockHttpServletResponse r = mvc.perform(multipart("/api/eventos/crear").file(new MockMultipartFile("file",
				"cartel.png", "image/png", png())).param("nuevo", nuevo).cookie(jwt(tokenGestor))).andReturn().getResponse();
		assertEquals(201, r.getStatus(), r.getContentAsString());
		return json.readTree(r.getContentAsString()).get("id").asLong();
	}

	private void ocultar(long evento) {
		jdbc.update("UPDATE evento SET visible = 0 WHERE id = ?", evento);
	}

	private JsonNode subir(long evento, String token) throws Exception {
		MockHttpServletResponse r = mvc.perform(subida(evento, png(), "foto.png", "image/png").cookie(jwt(token)))
				.andReturn().getResponse();
		assertEquals(201, r.getStatus(), r.getContentAsString());
		return json.readTree(r.getContentAsString());
	}

	private MockMultipartHttpServletRequestBuilder subida(long evento, byte[] contenido, String nombre, String tipo) {
		return multipart("/api/eventos/" + evento + "/fotos")
				.file(new MockMultipartFile("archivo", nombre, tipo, contenido));
	}

	private MockHttpServletResponse reordenar(long evento, String token, List<Long> ids) throws Exception {
		return mvc.perform(put("/api/eventos/" + evento + "/fotos/orden").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(ids)).cookie(jwt(token))).andReturn().getResponse();
	}

	/** Filas sin fichero, directas: llenar un evento con subidas de verdad costaría minutos. */
	private void rellenar(long evento, int cuantas) {
		long organizacion = jdbc.queryForObject("SELECT organizacion_id FROM evento WHERE id = ?", Long.class, evento);
		for (int i = 0; i < cuantas; i++) {
			String nombre = "relleno-" + UUID.randomUUID() + ".webp";
			String ruta = "org-" + organizacion + "/fotos-eventos/" + nombre;
			jdbc.update("INSERT INTO imagen (nombre, ruta, tipo, url, organizacion_id) VALUES (?, ?, 'image/webp', ?, ?)",
					nombre, ruta, "/imagenes/" + ruta, organizacion);
			long imagen = jdbc.queryForObject("SELECT id FROM imagen WHERE nombre = ?", Long.class, nombre);
			jdbc.update("INSERT INTO evento_foto (organizacion_id, evento_id, imagen_id, orden) VALUES (?, ?, ?, ?)",
					organizacion, evento, imagen, i + 1);
		}
	}

	private String ruta(long foto) {
		return jdbc.queryForObject("SELECT i.ruta FROM imagen i JOIN evento_foto f ON f.imagen_id = i.id WHERE f.id = ?",
				String.class, foto);
	}

	private long imagenDe(long foto) {
		return jdbc.queryForObject("SELECT imagen_id FROM evento_foto WHERE id = ?", Long.class, foto);
	}

	private int filas(long evento) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM evento_foto WHERE evento_id = ?", Integer.class, evento);
	}

	private long contar(String sql) {
		return jdbc.queryForObject(sql, Long.class);
	}

	private long ficheros(long organizacion) throws IOException {
		Path carpeta = IMAGENES.resolve("org-" + organizacion).resolve("fotos-eventos");
		if (!Files.isDirectory(carpeta)) {
			return 0;
		}
		try (Stream<Path> rutas = Files.list(carpeta)) {
			return rutas.count();
		}
	}

	private interface Accion {
		void ejecutar() throws Exception;
	}

	/** Un fallo real de MySQL a mitad de la transacción, solo para las filas de ese evento. */
	private void conTriggerQueFalla(String momento, String fila, long evento, Accion accion) throws Exception {
		String nombre = "it_foto_falla_" + sufijo.replace('-', '_');
		jdbc.execute("CREATE TRIGGER " + nombre + " " + momento + " ON evento_foto FOR EACH ROW BEGIN IF " + fila
				+ ".evento_id = " + evento + " THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'fallo de prueba'; END IF; END");
		try {
			accion.ejecutar();
		} finally {
			jdbc.execute("DROP TRIGGER " + nombre);
		}
	}

	private JsonNode leer(String ruta, String token) throws Exception {
		MockHttpServletResponse r = mvc.perform(conToken(get(ruta), token)).andReturn().getResponse();
		assertEquals(200, r.getStatus(), ruta + ": " + r.getContentAsString());
		return json.readTree(r.getContentAsString());
	}

	private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token) {
		return token == null ? peticion : peticion.cookie(jwt(token));
	}

	private int estado(MockHttpServletRequestBuilder peticion) throws Exception {
		return mvc.perform(peticion).andReturn().getResponse().getStatus();
	}

	private boolean rechazado(MockHttpServletRequestBuilder peticion) throws Exception {
		int estado = estado(peticion);
		return estado == 401 || estado == 403;
	}

	private void assertNoEncontrado(MockHttpServletRequestBuilder peticion) throws Exception {
		MockHttpServletResponse r = mvc.perform(peticion).andReturn().getResponse();
		assertEquals(404, r.getStatus());
		assertEquals(0, r.getContentAsByteArray().length, "No se devuelve nada");
	}

	private static Set<String> campos(JsonNode nodo) {
		Set<String> campos = new HashSet<>();
		nodo.fieldNames().forEachRemaining(campos::add);
		return campos;
	}

	private static List<Long> ids(JsonNode lista) {
		List<Long> ids = new ArrayList<>();
		lista.forEach(n -> ids.add(n.get("id").asLong()));
		return ids;
	}

	private static JsonNode fila(JsonNode lista, long id) {
		for (JsonNode n : lista) {
			if (n.get("id").asLong() == id) {
				return n;
			}
		}
		throw new AssertionError("sin fila " + id + ": " + lista);
	}

	private static Cookie jwt(String token) {
		return new Cookie("jwt", token);
	}

	private static byte[] png() throws IOException {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB), "png", salida);
		return salida.toByteArray();
	}

	private String token(long organizacion, RolMembresia rol, String prefijo) {
		Usuario usuario = TenantContext.calcularCon(ORG1, () -> {
			Usuario nuevo = new Usuario();
			nuevo.setNombre("Usuario");
			nuevo.setApellidos("Fotos");
			nuevo.setEmail(prefijo + "-" + sufijo + "@example.test");
			nuevo.setContrasena(encoder.encode("Clave-Segura-123"));
			nuevo.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(nuevo);
		});
		TenantContext.ejecutarCon(ORG1, () -> membresias.save(new Membresia(usuario.getId(), organizacion, rol)));
		return jwtService.generateToken(usuario, 600_000, organizacion);
	}
}
