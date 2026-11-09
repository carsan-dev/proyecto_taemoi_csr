package com.taemoi.project.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;

import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.WebEscuela;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.organizacion.UrlsEscuela;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.WebEscuelaRepository;

class WebRastreoTest {

	private final WebEscuelaRepository webs = mock(WebEscuelaRepository.class);
	private final OrganizacionRepository organizaciones = mock(OrganizacionRepository.class);
	private final UrlsEscuela urls = mock(UrlsEscuela.class);
	private final WebPublicaService web = spy(new WebPublicaService(webs, organizaciones,
			null, null, null, null, null, null, null, null, urls));

	@Test
	void robots_respetaPublicacionYDisponibilidad() {
		TenantContext.ejecutarCon(2L, () -> {
			Organizacion org = mock(Organizacion.class);
			WebEscuela publicada = mock(WebEscuela.class);
			when(organizaciones.findById(2L)).thenReturn(Optional.of(org));
			when(org.getActiva()).thenReturn(true);
			when(webs.findById(2L)).thenReturn(Optional.empty());
			assertEquals("User-agent: *\nDisallow: /\n", web.robots().orElseThrow());
			when(webs.findById(2L)).thenReturn(Optional.of(publicada));
			assertEquals("User-agent: *\nDisallow: /\n", web.robots().orElseThrow());
			when(publicada.isPublicada()).thenReturn(true);
			when(urls.baseFrontend(2L)).thenReturn("https://principal.test/");
			assertEquals("User-agent: *\nAllow: /\nSitemap: https://principal.test/sitemap.xml\n",
					web.robots().orElseThrow());
			when(org.getComportamientoLegadoTaemoi()).thenReturn(true);
			assertTrue(web.robots().isEmpty());
			when(org.getComportamientoLegadoTaemoi()).thenReturn(false);
			when(org.getActiva()).thenReturn(false);
			assertEquals("User-agent: *\nDisallow: /\n", web.robots().orElseThrow());
			when(org.getComportamientoLegadoTaemoi()).thenReturn(true);
			assertTrue(web.robots().isEmpty());
			when(organizaciones.findById(2L)).thenReturn(Optional.empty());
			assertTrue(web.robots().isEmpty());
		});
	}

	@Test
	void sitemap_escapaXmlYOmiteSedesSinSlug() throws Exception {
		WebPublicaDTO datos = mock(WebPublicaDTO.class);
		WebPublicaDTO.Sede sede = mock(WebPublicaDTO.Sede.class);
		WebPublicaDTO.Sede sinSlug = mock(WebPublicaDTO.Sede.class);
		WebPublicaDTO.Sede vacio = mock(WebPublicaDTO.Sede.class);
		when(sede.slug()).thenReturn("a&b<\"'>");
		when(vacio.slug()).thenReturn(" ");
		when(datos.sedes()).thenReturn(List.of(sede, sinSlug, vacio));
		when(datos.proximosEventos()).thenReturn(List.of());
		doReturn(Optional.of(datos)).when(web).web(false, null);
		String xml = TenantContext.calcularCon(2L, () -> {
			when(urls.baseFrontend(2L)).thenReturn("https://principal.test/");
			return web.sitemap().orElseThrow();
		});
		assertTrue(xml.contains("a&amp;b&lt;&quot;&apos;&gt;"));
		assertFalse(xml.contains("test//"));
		var documento = DocumentBuilderFactory.newInstance().newDocumentBuilder()
				.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
		var ubicaciones = documento.getElementsByTagName("loc");
		assertEquals(6, ubicaciones.getLength());
		assertEquals("https://principal.test/sedes/a&b<\"'>", ubicaciones.item(5).getTextContent());
	}

	@Test
	void sitemap_sinWebPublicadaNoExiste() {
		doReturn(Optional.empty()).when(web).web(false, null);
		assertTrue(web.sitemap().isEmpty());
	}
}
