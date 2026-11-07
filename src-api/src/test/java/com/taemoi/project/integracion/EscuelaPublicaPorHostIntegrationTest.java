package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.organizacion.ResolutorEscuelaPorHost;
import com.taemoi.project.plataforma.LogoEscuelaService;

/**
 * Fase 4, escuela por dominio, pieza 4 ({@code fase4-dominio-plan.md} §4.2): {@code GET /api/publico/escuela} y
 * {@code /logo} anónimos, de la escuela del Host. Escuela X sintética con dominio, logo subido y datos privados
 * (CIF, buzón, firmante) que NO deben salir; Moiskimdo (organización 1) solo se lee. Por HTTP real contra MySQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EscuelaPublicaPorHostIntegrationTest extends MySqlIntegrationTest {

	private static final String RUTA = "/api/publico/escuela";
	private static final String LOGO = RUTA + "/logo";
	private static final String CIF = "B99887766";
	private static final String BUZON = "buzon-privado@example.test";
	private static final String FIRMANTE = "Firmante Secreto";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired ResolutorEscuelaPorHost porHost;
	@Autowired LogoEscuelaService logos;

	final String marca = UUID.randomUUID().toString().substring(0, 8);
	final String hostX = "marca-" + marca + ".test";
	final String hostSinLogo = "sinlogo-" + marca + ".test";
	long orgX, orgSinLogo;

	@BeforeAll
	void preparar() throws Exception {
		orgX = escuela("it-pub-x-" + marca, "Escuela Pública " + marca);
		jdbc.update("UPDATE organizacion SET cif = ?, email_contacto = ?, firmante_nombre = ?, "
				+ "color_primario = '#1F5FA8' WHERE id = ?", CIF, BUZON, FIRMANTE, orgX);
		orgSinLogo = escuela("it-pub-y-" + marca, "Escuela Sin Logo " + marca);
		jdbc.update("INSERT INTO organizacion_dominio (organizacion_id, host, principal) VALUES (?, ?, TRUE)", orgX,
				hostX);
		jdbc.update("INSERT INTO organizacion_dominio (organizacion_id, host, principal) VALUES (?, ?, TRUE)",
				orgSinLogo, hostSinLogo);
		porHost.recargar();

		BufferedImage imagen = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
		var g = imagen.createGraphics();
		g.setColor(Color.RED);
		g.fillRect(0, 0, 40, 30);
		g.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(imagen, "png", out);
		logos.subir(orgX, new MockMultipartFile("archivo", "logo-ficticio.png", "image/png", out.toByteArray()));
	}

	@AfterAll
	void retirarDominios() {
		jdbc.update("DELETE FROM organizacion_dominio WHERE host IN (?, ?)", hostX, hostSinLogo);
		porHost.recargar();
	}

	@Test
	void anonimoConHostX_veSuNombreYSuLogo_sinDatosPrivados() throws Exception {
		MvcResult r = mvc.perform(get(RUTA).header("Host", hostX)).andReturn();
		String cuerpo = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertEquals(200, r.getResponse().getStatus(), cuerpo);
		JsonNode dto = json.readTree(cuerpo);

		assertEquals("Escuela Pública " + marca, dto.get("nombre").asText());
		assertEquals("SUBIDO", dto.get("logo").asText());
		Long imagenId = jdbc.queryForObject("SELECT logo_imagen_id FROM organizacion WHERE id = ?", Long.class, orgX);
		assertEquals(imagenId.longValue(), dto.get("logoVersion").asLong());
		assertNotNull(dto.get("tema"));
		assertEquals(Set.of("logo", "logoVersion", "nombre", "tema"), campos(dto), "solo estos cuatro campos");
		for (String privado : List.of(CIF, BUZON, FIRMANTE)) {
			assertFalse(cuerpo.contains(privado), "dato privado en la respuesta pública: " + privado);
		}
		for (String clave : List.of("cif", "emailContacto", "firmanteNombre", "telefono", "email", "nombreLegal")) {
			assertFalse(dto.has(clave), clave);
		}
	}

	@Test
	void logoConHostX_esPngConEtagYCachePublica_y304ConIfNoneMatch() throws Exception {
		MvcResult r = mvc.perform(get(LOGO).header("Host", hostX)).andReturn();
		assertEquals(200, r.getResponse().getStatus());
		assertEquals("image/png", r.getResponse().getContentType());
		assertEquals("max-age=300, public", r.getResponse().getHeader("Cache-Control"));
		String etag = r.getResponse().getHeader("ETag");
		assertNotNull(etag);
		byte[] png = r.getResponse().getContentAsByteArray();
		assertNotNull(ImageIO.read(new ByteArrayInputStream(png)), "es un PNG legible");

		MvcResult cond = mvc.perform(get(LOGO).header("Host", hostX).header("If-None-Match", etag)).andReturn();
		assertEquals(304, cond.getResponse().getStatus());
		assertEquals(etag, cond.getResponse().getHeader("ETag"));
		assertEquals("max-age=300, public", cond.getResponse().getHeader("Cache-Control"));
		assertEquals(0, cond.getResponse().getContentAsByteArray().length);

		MvcResult otra = mvc.perform(get(LOGO).header("Host", hostX).header("If-None-Match", "\"logo-0\"")).andReturn();
		assertEquals(200, otra.getResponse().getStatus());
		assertArrayEquals(png, otra.getResponse().getContentAsByteArray());
	}

	@Test
	void escuelaSinLogoSubido_NINGUNO_y404EnElLogo() throws Exception {
		JsonNode dto = json.readTree(cuerpo(mvc.perform(get(RUTA).header("Host", hostSinLogo)).andReturn()));
		assertEquals("Escuela Sin Logo " + marca, dto.get("nombre").asText());
		assertEquals("NINGUNO", dto.get("logo").asText());
		assertTrue(dto.get("logoVersion").isNull());
		assertEquals(404, mvc.perform(get(LOGO).header("Host", hostSinLogo)).andReturn().getResponse().getStatus());
		assertEquals(404, mvc.perform(get(LOGO).header("Host", hostSinLogo).header("If-None-Match", "\"logo-1\""))
				.andReturn().getResponse().getStatus());
	}

	/** Moiskimdo idéntico: su Host, uno desconocido o ninguno dan la escuela 1 (LEGADO, sin PNG) y X no se cuela. */
	@Test
	void moiskimdoDesconocidoOSinHost_LEGADO_sinPng() throws Exception {
		assertNull(jdbc.queryForObject("SELECT logo_imagen_id FROM organizacion WHERE id = 1", Long.class),
				"Moiskimdo empieza sin logo subido");
		String sinHost = cuerpo(mvc.perform(get(RUTA)).andReturn());
		JsonNode dto = json.readTree(sinHost);
		assertEquals("LEGADO", dto.get("logo").asText());
		assertTrue(dto.get("logoVersion").isNull());
		assertEquals(jdbc.queryForObject("SELECT nombre FROM organizacion WHERE id = 1", String.class),
				dto.get("nombre").asText());
		assertFalse(sinHost.contains(marca));
		for (String host : List.of("moiskimdo.es", "MOISKIMDO.ES:443", "desconocido-" + marca + ".test")) {
			assertEquals(sinHost, cuerpo(mvc.perform(get(RUTA).header("Host", host)).andReturn()), host);
			assertEquals(404, mvc.perform(get(LOGO).header("Host", host)).andReturn().getResponse().getStatus(), host);
		}
		assertEquals(404, mvc.perform(get(LOGO)).andReturn().getResponse().getStatus());
	}

	// ------------------------------------------------------------------ utilidades

	private long escuela(String slug, String nombre) {
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES (?, ?)", slug, nombre);
		return jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = ?", Long.class, slug);
	}

	private static String cuerpo(MvcResult r) throws Exception {
		assertEquals(200, r.getResponse().getStatus());
		return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	private static Set<String> campos(JsonNode nodo) {
		Set<String> claves = new TreeSet<>();
		nodo.fieldNames().forEachRemaining(claves::add);
		return claves;
	}
}
