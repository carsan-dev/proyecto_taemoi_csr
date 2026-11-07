package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.ResolutorEscuelaPorHost;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza W5 de la web por plantilla (fase4-web-plan.md §5): {@code GET /api/publico/web/eventos} y
 * {@code GET /api/publico/web/eventos/{id}}. Mismas reglas que {@code /api/publico/web} (W1): escuela del Host, 404
 * sin web publicada (salvo vista previa de gestión), 404 en Moiskimdo, misma caché.
 *
 * <pre>
 * X  publicada: próximos (hoy, +3 con hora, lugar y sede, +5 con la sede de Y), anteriores (-10 con cartel, fotos y
 *    documentos, -400), ocultos pasado y futuro, sin fecha.
 * Y  publicada: un próximo y un anterior (aislamiento).
 * V  publicada: 61 anteriores (tope de la lista).
 * Z  con fila sin publicar, un evento visible; propietario y familia.  W  sin fila, un evento visible.
 * </pre>
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
		+ "com.taemoi.project.integracion.ContadorSentencias")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WebEventosPublicaIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String EVENTOS = "/api/publico/web/eventos";
	/** Claves que nunca salen en las dos rutas, en ningún nivel del JSON. */
	private static final Set<String> CLAVES_PRIVADAS = Set.of("visible", "orden", "organizacionId", "ruta", "url",
			"fotoEvento", "alumno", "evento", "numeroFotos", "imagen", "miniatura", "aforo", "email");

	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired ObjectMapper json;
	@Autowired JwtService jwt;
	@Autowired UsuarioRepository usuarios;
	@Autowired ResolutorEscuelaPorHost porHost;

	@MockitoBean JavaMailSender mailSender;

	final String marca = UUID.randomUUID().toString().substring(0, 8);
	LocalDate hoy;
	long x, y, v, z, w;
	long sedeX, sedeY;
	long deHoy, mas3, mas5ConSedeDeY, menos10, menos400, ocultoPasado, ocultoFuturo, sinFecha;
	long fotoA, fotoB, documentoA, documentoB;
	long proximoY, anteriorY, eventoZ, eventoW;
	long propietarioZ, familiaZ, gestorX;
	final List<Long> anterioresV = new ArrayList<>();
	final Map<Long, String> hosts = new HashMap<>();

	@BeforeAll
	void sembrar() {
		hoy = LocalDate.now(ZoneId.of("Europe/Madrid"));

		// ---------------------------------------------------------------- Y (antes, para apuntar a su sede desde X)
		y = organizacion("y", "Escuela Eventos Y " + marca);
		sedeY = sede(y, "Sede de Y " + marca);
		proximoY = evento(y, "Próximo de Y", hoy.plusDays(2), true, null);
		anteriorY = evento(y, "Anterior de Y", hoy.minusDays(3), true, null);
		web(y, true);

		// ---------------------------------------------------------------- X
		x = organizacion("x", "Escuela Eventos X " + marca);
		sedeX = sede(x, "Sede de X " + marca);
		long cartel = imagen(x, "cartel-x");
		menos400 = evento(x, "Hace 400 X", hoy.minusDays(400), true, null);
		mas5ConSedeDeY = evento(x, "Más 5 X", hoy.plusDays(5), true, null);
		menos10 = evento(x, "Hace 10 X", hoy.minusDays(10), true, cartel);
		mas3 = evento(x, "Más 3 X", hoy.plusDays(3), true, null);
		deHoy = evento(x, "Hoy X", hoy, true, null);
		ocultoPasado = evento(x, "Oculto pasado X", hoy.minusDays(1), false, null);
		ocultoFuturo = evento(x, "Oculto futuro X", hoy.plusDays(1), false, null);
		sinFecha = evento(x, "Sin fecha X", null, true, null);
		jdbc.update("UPDATE evento SET hora = '18:30:00', lugar = 'Polideportivo X', sede_id = ? WHERE id = ?", sedeX,
				mas3);
		// Mal por SQL: la sede de otra escuela no debe salir.
		jdbc.update("UPDATE evento SET sede_id = ? WHERE id = ?", sedeY, mas5ConSedeDeY);
		fotoB = foto(x, menos10, 2);
		fotoA = foto(x, menos10, 1);
		documentoA = documento(x, menos10, "Circular X.pdf", "application/pdf");
		documentoB = documento(x, menos10, "Autorización X.doc", "application/msword");
		foto(x, ocultoPasado, 1);
		documento(x, ocultoPasado, "Secreto oculto X.pdf", "application/pdf");
		web(x, true);
		gestorX = cuenta("gestor-x", x, "ADMINISTRADOR");

		// ---------------------------------------------------------------- V (tope de anteriores)
		v = organizacion("v", "Escuela Eventos V " + marca);
		for (int dias = 1; dias <= 61; dias++) {
			anterioresV.add(evento(v, "Anterior V " + dias, hoy.minusDays(dias), true, null));
		}
		web(v, true);

		// ---------------------------------------------------------------- Z y W
		z = organizacion("z", "Escuela Eventos Z " + marca);
		eventoZ = evento(z, "Evento de Z", hoy.plusDays(1), true, null);
		web(z, false);
		propietarioZ = cuenta("propietario-z", z, "PROPIETARIO");
		familiaZ = cuenta("familia-z", z, "FAMILIA");
		alumnoDe(z, familiaZ);
		w = organizacion("w", "Escuela Eventos W " + marca);
		eventoW = evento(w, "Evento de W", hoy.plusDays(1), true, null);

		for (long org : List.of(x, y, v, z, w)) {
			String host = "w5-" + org + "-" + marca + ".test";
			jdbc.update("INSERT INTO organizacion_dominio (organizacion_id, host, principal) VALUES (?, ?, TRUE)", org,
					host);
			hosts.put(org, host);
		}
		porHost.recargar();
	}

	@AfterAll
	void retirarDominios() {
		hosts.values().forEach(host -> jdbc.update("DELETE FROM organizacion_dominio WHERE host = ?", host));
		porHost.recargar();
	}

	// ------------------------------------------------------------------ lista

	/** Próximos desde hoy, del más cercano al más lejano; anteriores, del más reciente al más antiguo. */
	@Test
	void lista_anonimo_proximosYAnteriores() throws Exception {
		JsonNode eventos = cuerpo(anonimo(x, EVENTOS));
		assertEquals(List.of(deHoy, mas3, mas5ConSedeDeY), ids(eventos.get("proximos")));
		assertEquals(List.of(menos10, menos400), ids(eventos.get("anteriores")));

		JsonNode conDatos = eventos.get("proximos").get(1);
		assertEquals("Más 3 X", conDatos.get("titulo").asText());
		assertEquals(hoy.plusDays(3).toString(), conDatos.get("fecha").asText());
		assertEquals("18:30", conDatos.get("hora").asText());
		assertEquals("Polideportivo X", conDatos.get("lugar").asText());
		assertEquals(sedeX, conDatos.get("sedeId").asLong());
		assertEquals("Sede de X " + marca, conDatos.get("sedeNombre").asText());
		assertEquals("Descripción de Más 3 X", conDatos.get("descripcion").asText());
		assertFalse(conDatos.get("conImagen").asBoolean());

		JsonNode conSedeDeY = eventos.get("proximos").get(2);
		assertTrue(conSedeDeY.get("sedeId").isNull(), "la sede de otra escuela no sale");
		assertTrue(conSedeDeY.get("sedeNombre").isNull());
		assertTrue(eventos.get("anteriores").get(0).get("conImagen").asBoolean());

		Set<String> campos = new TreeSet<>();
		conDatos.fieldNames().forEachRemaining(campos::add);
		assertEquals(new TreeSet<>(Set.of("id", "titulo", "fecha", "conImagen", "hora", "lugar", "sedeId", "sedeNombre",
				"descripcion")), campos);
	}

	/** Los anteriores se cortan en los 60 más recientes. */
	@Test
	void lista_anteriores_tope() throws Exception {
		JsonNode eventos = cuerpo(anonimo(v, EVENTOS));
		assertEquals(0, eventos.get("proximos").size());
		assertEquals(anterioresV.subList(0, 60), ids(eventos.get("anteriores")));
	}

	@Test
	void lista_anonimo_cacheYEtag() throws Exception {
		MvcResult r = anonimo(x, EVENTOS);
		String etag = r.getResponse().getHeader("ETag");
		assertNotNull(etag);
		String cache = r.getResponse().getHeader("Cache-Control");
		assertTrue(cache.contains("max-age=60") && cache.contains("public"), cache);
		assertVaria(r);
		assertEquals(304, anonimo(x, get(EVENTOS).header("If-None-Match", etag)).getResponse().getStatus());
	}

	// ------------------------------------------------------------------ detalle

	@Test
	void detalle_anonimo_datosFotosYDocumentos() throws Exception {
		MvcResult r = anonimo(x, EVENTOS + "/" + menos10);
		JsonNode evento = cuerpo(r);
		assertEquals(menos10, evento.get("id").asLong());
		assertEquals("Hace 10 X", evento.get("titulo").asText());
		assertEquals(hoy.minusDays(10).toString(), evento.get("fecha").asText());
		assertEquals("Descripción de Hace 10 X", evento.get("descripcion").asText());
		assertTrue(evento.get("conImagen").asBoolean());
		for (String campo : List.of("hora", "lugar", "sedeId", "sedeNombre")) {
			assertTrue(evento.get(campo).isNull(), campo);
		}
		assertEquals(List.of(fotoA, fotoB), ids(evento.get("fotos")), "por orden");
		assertEquals(List.of(documentoA, documentoB), ids(evento.get("documentos")));
		assertEquals("Circular X.pdf", evento.at("/documentos/0/nombre").asText());
		assertEquals("application/pdf", evento.at("/documentos/0/tipo").asText());

		String cache = r.getResponse().getHeader("Cache-Control");
		assertTrue(cache.contains("max-age=60") && cache.contains("public"), cache);
		assertVaria(r);
		assertEquals(304, anonimo(x, get(EVENTOS + "/" + menos10).header("If-None-Match", r.getResponse().getHeader(
				"ETag"))).getResponse().getStatus());

		JsonNode conDatos = cuerpo(anonimo(x, EVENTOS + "/" + mas3));
		assertEquals("18:30", conDatos.get("hora").asText());
		assertEquals(sedeX, conDatos.get("sedeId").asLong());
		assertEquals(0, conDatos.get("fotos").size());
		assertEquals(0, conDatos.get("documentos").size());
		assertTrue(cuerpo(anonimo(x, EVENTOS + "/" + mas5ConSedeDeY)).get("sedeId").isNull());
	}

	/** Oculto, sin fecha, de otra escuela, inexistente: 404, también en la vista previa de gestión. */
	@Test
	void detalle_404() throws Exception {
		for (long id : List.of(ocultoPasado, ocultoFuturo, sinFecha, proximoY, anteriorY, eventoZ, eventoW,
				Long.MAX_VALUE)) {
			assertEquals(404, anonimo(x, EVENTOS + "/" + id).getResponse().getStatus(), "evento " + id);
			assertEquals(404, mvc.perform(get(EVENTOS + "/" + id).param("vistaPrevia", "true")
					.cookie(sesion(gestorX, x))).andReturn().getResponse().getStatus(), "vista previa, evento " + id);
		}
		// Y no ve los de X.
		assertEquals(404, anonimo(y, EVENTOS + "/" + menos10).getResponse().getStatus());
		assertEquals(200, anonimo(y, EVENTOS + "/" + proximoY).getResponse().getStatus());
	}

	// ------------------------------------------------------------------ privados y aislamiento

	@Test
	void sinCamposPrivados_niOcultos() throws Exception {
		for (String url : List.of(EVENTOS, EVENTOS + "/" + menos10)) {
			MvcResult r = anonimo(x, url);
			Set<String> privadas = claves(cuerpo(r));
			privadas.retainAll(CLAVES_PRIVADAS);
			assertEquals(Set.of(), privadas, url);
			String texto = r.getResponse().getContentAsString();
			for (String secreto : List.of("Oculto pasado X", "Oculto futuro X", "Sin fecha X", "Secreto oculto",
					"Sede de Y", "/imagenes/", "/documentos/", "org-" + x, "it-w5-")) {
				assertFalse(texto.contains(secreto), secreto + " en " + url + ": " + texto);
			}
		}
	}

	@Test
	void aislamiento_entreEscuelas() throws Exception {
		JsonNode deY = cuerpo(anonimo(y, EVENTOS));
		assertEquals(List.of(proximoY), ids(deY.get("proximos")));
		assertEquals(List.of(anteriorY), ids(deY.get("anteriores")));
		String textoX = anonimo(x, EVENTOS).getResponse().getContentAsString();
		assertFalse(textoX.contains("de Y"), textoX);
	}

	// ------------------------------------------------------------------ 404 de la web y vista previa

	@Test
	void moiskimdo_404() throws Exception {
		assertEquals(404, mvc.perform(get(EVENTOS)).andReturn().getResponse().getStatus());
		assertEquals(404, mvc.perform(get(EVENTOS + "/" + menos10)).andReturn().getResponse().getStatus());
		Usuario admin = TenantContext.calcularCon(ORG1,
				() -> usuarios.findByEmailIgnoreCase("admin-it@example.test").orElseThrow());
		Cookie sesion = new Cookie("jwt", jwt.generateToken(admin, 600_000, ORG1));
		assertEquals(404, mvc.perform(get(EVENTOS).param("vistaPrevia", "true").cookie(sesion)).andReturn()
				.getResponse().getStatus());
	}

	@Test
	void sinPublicarOSinFila_anonimo_404_tambienConVistaPrevia() throws Exception {
		for (long org : List.of(z, w)) {
			long evento = org == z ? eventoZ : eventoW;
			for (String url : List.of(EVENTOS, EVENTOS + "/" + evento)) {
				assertEquals(404, anonimo(org, url).getResponse().getStatus(), url);
				MvcResult r = anonimo(org, get(url).param("vistaPrevia", "true"));
				assertEquals(404, r.getResponse().getStatus(), url);
				assertVaria(r);
			}
		}
	}

	/** El propietario ve su web sin publicar en la vista previa, sin caché; sin el parámetro, 404. */
	@Test
	void sinPublicar_vistaPreviaDelPropietario_200_sinCache() throws Exception {
		Cookie sesion = sesion(propietarioZ, z);
		for (String url : List.of(EVENTOS, EVENTOS + "/" + eventoZ)) {
			MvcResult r = mvc.perform(get(url).param("vistaPrevia", "true").cookie(sesion)).andReturn();
			assertEquals(200, r.getResponse().getStatus(), url);
			assertTrue(r.getResponse().getHeader("Cache-Control").contains("no-store"), url);
			assertVaria(r);
			assertEquals(404, mvc.perform(get(url).cookie(sesion)).andReturn().getResponse().getStatus(), url);
		}
		assertEquals(List.of(eventoZ), ids(cuerpo(mvc.perform(get(EVENTOS).param("vistaPrevia", "true")
				.cookie(sesion)).andReturn()).get("proximos")));
	}

	@Test
	void sinPublicar_vistaPreviaDeFamilia_404() throws Exception {
		Cookie sesion = sesion(familiaZ, z);
		assertEquals(200, mvc.perform(get("/api/auth/permisos").cookie(sesion)).andReturn().getResponse().getStatus());
		assertEquals(404, mvc.perform(get(EVENTOS).param("vistaPrevia", "true").cookie(sesion)).andReturn()
				.getResponse().getStatus());
		assertEquals(404, mvc.perform(get(EVENTOS + "/" + eventoZ).param("vistaPrevia", "true").cookie(sesion))
				.andReturn().getResponse().getStatus());
	}

	/** Con vistaPrevia en la URL o con sesión, sin caché compartida; la vista previa anónima ve lo publicado. */
	@Test
	void publicada_vistaPreviaAnonimaOConSesion_sinCache() throws Exception {
		for (String url : List.of(EVENTOS, EVENTOS + "/" + menos10)) {
			MvcResult anonima = anonimo(x, get(url).param("vistaPrevia", "true"));
			assertEquals(200, anonima.getResponse().getStatus(), url);
			assertTrue(anonima.getResponse().getHeader("Cache-Control").contains("no-store"), url);
			MvcResult conSesion = mvc.perform(get(url).cookie(sesion(gestorX, x))).andReturn();
			assertEquals(200, conSesion.getResponse().getStatus(), url);
			assertTrue(conSesion.getResponse().getHeader("Cache-Control").contains("no-store"), url);
		}
		assertEquals(List.of(deHoy, mas3, mas5ConSedeDeY),
				ids(cuerpo(anonimo(x, get(EVENTOS).param("vistaPrevia", "true"))).get("proximos")));
	}

	// ------------------------------------------------------------------ seguridad y sentencias

	/** Solo las dos rutas nuevas y solo lectura: lo de al lado sigue cerrado al anónimo. */
	@Test
	void seguridad_soloLasRutasNuevas() throws Exception {
		assertEquals(200, anonimo(x, get(EVENTOS)).getResponse().getStatus());
		assertEquals(200, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.head(EVENTOS + "/" + menos10).header("Host", hosts.get(x))).andReturn().getResponse().getStatus());
		for (MockHttpServletRequestBuilder cerrada : List.of(get(EVENTOS + "/" + menos10 + "/fotos"),
				get(EVENTOS + "/" + menos10 + "/documentos"),
				org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(EVENTOS),
				org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(EVENTOS + "/" + menos10))) {
			int estado = anonimo(x, cerrada).getResponse().getStatus();
			assertTrue(estado == 401 || estado == 403, "estado " + estado);
		}
	}

	/** Sin N+1: la lista y el detalle cuestan lo mismo con más eventos, fotos y documentos. */
	@Test
	void sentencias_lasMismasConMasDatos() throws Exception {
		anonimo(x, EVENTOS);
		anonimo(y, EVENTOS);
		List<String> listaX = sentencias(() -> anonimo(x, EVENTOS));
		List<String> listaY = sentencias(() -> anonimo(y, EVENTOS));
		assertEquals(listaY.size(), listaX.size(), listaX.toString());
		assertTrue(listaX.size() <= 4, listaX.toString());
		List<String> detalleX = sentencias(() -> anonimo(x, EVENTOS + "/" + menos10));
		List<String> detalleY = sentencias(() -> anonimo(y, EVENTOS + "/" + proximoY));
		assertEquals(detalleY.size(), detalleX.size(), detalleX.toString());
		assertTrue(detalleX.size() <= 5, detalleX.toString());
	}

	private static List<String> sentencias(java.util.concurrent.Callable<?> accion) throws Exception {
		return ContadorSentencias.contar(accion).sentencias().stream()
				.filter(s -> !s.startsWith("insert into auditoria_evento")).toList();
	}

	// ------------------------------------------------------------------ utilidades

	private MvcResult anonimo(long org, String url) throws Exception {
		return anonimo(org, get(url));
	}

	private MvcResult anonimo(long org, MockHttpServletRequestBuilder peticion) throws Exception {
		return mvc.perform(peticion.header("Host", hosts.get(org))).andReturn();
	}

	private static void assertVaria(MvcResult r) {
		String vary = String.join(",", r.getResponse().getHeaders("Vary"));
		assertTrue(vary.contains("Cookie") && vary.contains("Authorization"), "Vary: " + vary);
	}

	private JsonNode cuerpo(MvcResult r) throws Exception {
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString());
	}

	private static List<Long> ids(JsonNode lista) {
		List<Long> ids = new ArrayList<>();
		lista.forEach(n -> ids.add(n.get("id").asLong()));
		return ids;
	}

	private static Set<String> claves(JsonNode nodo) {
		Set<String> claves = new TreeSet<>();
		if (nodo.isObject()) {
			for (Iterator<String> it = nodo.fieldNames(); it.hasNext();) {
				String nombre = it.next();
				claves.add(nombre);
				claves.addAll(claves(nodo.get(nombre)));
			}
		} else if (nodo.isArray()) {
			nodo.forEach(hijo -> claves.addAll(claves(hijo)));
		}
		return claves;
	}

	private Cookie sesion(long usuarioId, long org) {
		Usuario usuario = TenantContext.calcularCon(ORG1, () -> usuarios.findById(usuarioId).orElseThrow());
		return new Cookie("jwt", jwt.generateToken(usuario, 600_000, org));
	}

	private long organizacion(String sufijo, String nombre) {
		String slug = "it-w5-" + sufijo + "-" + marca;
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES (?, ?)", slug, nombre);
		return jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = ?", Long.class, slug);
	}

	private long sede(long org, String nombre) {
		jdbc.update("INSERT INTO sede (organizacion_id, nombre, activa) VALUES (?, ?, TRUE)", org, nombre);
		return jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = ? AND nombre = ?", Long.class, org,
				nombre);
	}

	private long imagen(long org, String nombre) {
		String ruta = "org-" + org + "/eventos/" + nombre + ".jpg";
		jdbc.update("INSERT INTO imagen (nombre, ruta, tipo, url, organizacion_id) VALUES (?, ?, 'image/jpeg', ?, ?)",
				nombre + ".jpg", ruta, "/imagenes/" + ruta, org);
		return jdbc.queryForObject("SELECT MAX(id) FROM imagen WHERE organizacion_id = ?", Long.class, org);
	}

	private long evento(long org, String titulo, LocalDate fecha, boolean visible, Long cartel) {
		jdbc.update("""
				INSERT INTO evento (titulo, descripcion, visible, orden, organizacion_id, fecha_evento, foto_evento_id)
				VALUES (?, ?, ?, 1000, ?, ?, ?)""", titulo, "Descripción de " + titulo, visible, org,
				fecha == null ? null : java.sql.Date.valueOf(fecha), cartel);
		return jdbc.queryForObject("SELECT MAX(id) FROM evento WHERE organizacion_id = ?", Long.class, org);
	}

	private long foto(long org, long evento, int orden) {
		long imagen = imagen(org, "foto-" + evento + "-" + orden);
		long miniatura = imagen(org, "mini-" + evento + "-" + orden);
		jdbc.update("INSERT INTO evento_foto (organizacion_id, evento_id, imagen_id, miniatura_id, orden)"
				+ " VALUES (?, ?, ?, ?, ?)", org, evento, imagen, miniatura, orden);
		return jdbc.queryForObject("SELECT id FROM evento_foto WHERE imagen_id = ?", Long.class, imagen);
	}

	private long documento(long org, long evento, String nombre, String tipo) {
		String ruta = "org-" + org + "/eventos/" + UUID.randomUUID() + ".bin";
		jdbc.update("""
				INSERT INTO documento (nombre, ruta, tipo, url, evento_id, organizacion_id)
				VALUES (?, ?, ?, ?, ?, ?)""", nombre, ruta, tipo, "/documentos/" + ruta, evento, org);
		return jdbc.queryForObject("SELECT id FROM documento WHERE ruta = ?", Long.class, ruta);
	}

	private void web(long org, boolean publicada) {
		jdbc.update("INSERT INTO web_escuela (organizacion_id, tema, publicada, publicada_en) VALUES (?, ?, ?, ?)", org,
				"linea-comarcal", publicada, publicada ? "2026-10-01 10:00:00" : null);
	}

	/** Alumno activo vinculado a mano a una cuenta de familia (le abre la sesión en {@code org}). */
	private void alumnoDe(long org, long usuario) {
		long expediente = jdbc.queryForObject("SELECT COALESCE(MAX(numero_expediente), 60000) + 1 FROM alumno",
				Long.class);
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id)
				VALUES (TRUE, 'PRUEBA', 1, 'Calle 1', '2010-01-01', 'Familia W5', ?, '600000000', 0, ?)""",
				expediente, org);
		long alumno = jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = ?",
				Long.class, expediente, org);
		jdbc.update("""
				INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen)
				VALUES (?, ?, ?, 'TUTOR', 'MANUAL')""", usuario, alumno, org);
	}

	private long cuenta(String nombre, long org, String rol) {
		String email = "it-w5-" + nombre + "-" + marca + "@example.test";
		long id = TenantContext.calcularCon(ORG1, () -> {
			Usuario usuario = new Usuario();
			usuario.setNombre("Usuario");
			usuario.setApellidos("Prueba");
			usuario.setEmail(email);
			usuario.setContrasena("fake-Password-123");
			usuario.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(usuario).getId();
		});
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", id, org, rol);
		return id;
	}
}
