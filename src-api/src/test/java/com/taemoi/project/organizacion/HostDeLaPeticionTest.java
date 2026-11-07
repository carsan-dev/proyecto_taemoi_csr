package com.taemoi.project.organizacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Fase 4, pieza 3: origen, dominio de la cookie y escuela preferida según el Host anotado por el filtro. Un Host fuera
 * de {@code organizacion_dominio} o el de la configuración (moiskimdo.es) deja todo como antes, byte a byte.
 */
class HostDeLaPeticionTest {

	private static final String MOISKIMDO = "https://moiskimdo.es";
	private static final long X = 7L;

	private final ResolutorEscuelaPorHost porHost = new ResolutorEscuelaPorHost(
			() -> Map.of("moiskimdo.es", 1L, "x.example", X, "otra.moiskimdo.es", X), 1L);

	@AfterEach
	void limpiar() {
		RequestContextHolder.resetRequestAttributes();
	}

	// ------------------------------------------------------------------ anotación del filtro

	@Test
	void anotaSoloHostsDeLaTabla_normalizados() {
		MockHttpServletRequest x = peticion("https", "X.Example.", 443);
		HostDeLaPeticion.anotar(x, porHost, 1L);
		assertEquals(Optional.of(new HostDeLaPeticion.Registrado("x.example", X, false)), HostDeLaPeticion.de(x));

		MockHttpServletRequest moiskimdo = peticion("https", "moiskimdo.es", 443);
		HostDeLaPeticion.anotar(moiskimdo, porHost, 1L);
		assertEquals(Optional.of(new HostDeLaPeticion.Registrado("moiskimdo.es", 1L, true)),
				HostDeLaPeticion.de(moiskimdo));

		MockHttpServletRequest ajeno = peticion("https", "evil.example", 443);
		HostDeLaPeticion.anotar(ajeno, porHost, 1L);
		assertEquals(Optional.empty(), HostDeLaPeticion.de(ajeno));
		assertNull(ajeno.getAttribute(HostDeLaPeticion.ATRIBUTO));
	}

	// ------------------------------------------------------------------ origen (archivos, destino de Google)

	@Test
	void hostFueraDeLaTabla_laConfiguracionTalCual() {
		MockHttpServletRequest ajeno = anotada("https", "evil.example", 443);
		assertSame(MOISKIMDO, HostDeLaPeticion.origen(ajeno, MOISKIMDO));
		assertSame(MOISKIMDO, HostDeLaPeticion.origen(new MockHttpServletRequest(), MOISKIMDO));
	}

	@Test
	void moiskimdo_laConfiguracionTalCual_aunqueElEsquemaOElPuertoDifieran() {
		assertSame(MOISKIMDO, HostDeLaPeticion.origen(anotada("https", "moiskimdo.es", 443), MOISKIMDO));
		assertSame(MOISKIMDO, HostDeLaPeticion.origen(anotada("http", "moiskimdo.es", 8080), MOISKIMDO));
		String conWww = "https://www.moiskimdo.es";
		assertSame(conWww, HostDeLaPeticion.origen(anotada("https", "moiskimdo.es", 443), conWww));
		String conBarra = "https://moiskimdo.es/";
		assertSame(conBarra, HostDeLaPeticion.origen(anotada("https", "moiskimdo.es", 443), conBarra));
	}

	@Test
	void hostX_suPropioOrigen() {
		assertEquals("https://x.example", HostDeLaPeticion.origen(anotada("https", "x.example", 443), MOISKIMDO));
		assertEquals("https://x.example:8443",
				HostDeLaPeticion.origen(anotada("https", "x.example", 8443), MOISKIMDO));
		assertEquals("http://x.example", HostDeLaPeticion.origen(anotada("http", "x.example", 80), MOISKIMDO));
		assertEquals("http://x.example:4200", HostDeLaPeticion.origen(anotada("http", "x.example", 4200), MOISKIMDO));
	}

	@Test
	void hostX_conEsquemaRaroOConfiguracionVaciaOIlegible_laConfiguracion() {
		assertSame(MOISKIMDO, HostDeLaPeticion.origen(anotada("ftp", "x.example", 21), MOISKIMDO));
		assertEquals("", HostDeLaPeticion.origen(anotada("https", "x.example", 443), ""));
		assertNull(HostDeLaPeticion.origen(anotada("https", "x.example", 443), null));
		assertEquals("no es una url", HostDeLaPeticion.origen(anotada("https", "x.example", 443), "no es una url"));
	}

	@Test
	void baseArchivosPeticion_usaLaPeticionDelHilo_ySinPeticionLaConfiguracion() {
		assertSame(MOISKIMDO, HostDeLaPeticion.baseArchivosPeticion(MOISKIMDO));

		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(anotada("https", "x.example", 443)));
		assertEquals("https://x.example", HostDeLaPeticion.baseArchivosPeticion(MOISKIMDO));

		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(anotada("https", "moiskimdo.es", 443)));
		assertSame(MOISKIMDO, HostDeLaPeticion.baseArchivosPeticion(MOISKIMDO));
	}

	// ------------------------------------------------------------------ cookie

	@Test
	void dominioCookie_moiskimdoYHostFueraDeLaTabla_elDeSiempre() {
		Optional<String> dominio = Optional.of("moiskimdo.es");
		assertEquals(dominio, HostDeLaPeticion.dominioCookie(anotada("https", "moiskimdo.es", 443), dominio));
		assertEquals(dominio, HostDeLaPeticion.dominioCookie(anotada("https", "evil.example", 443), dominio));
		assertEquals(dominio, HostDeLaPeticion.dominioCookie(new MockHttpServletRequest(), dominio));
	}

	@Test
	void dominioCookie_hostXYSubdominioDeMoiskimdo_soloDelHost() {
		Optional<String> dominio = Optional.of("moiskimdo.es");
		assertEquals(Optional.empty(), HostDeLaPeticion.dominioCookie(anotada("https", "x.example", 443), dominio));
		assertEquals(Optional.empty(),
				HostDeLaPeticion.dominioCookie(anotada("https", "otra.moiskimdo.es", 443), dominio));
	}

	@Test
	void dominioCookie_sinDominioConfigurado_sigueSinDominio() {
		assertEquals(Optional.empty(),
				HostDeLaPeticion.dominioCookie(anotada("https", "moiskimdo.es", 443), Optional.empty()));
	}

	// ------------------------------------------------------------------ escuela preferida al entrar

	@Test
	void escuelaPreferida_soloEnElHostDeOtraEscuela() {
		assertEquals(Optional.of(X), HostDeLaPeticion.escuelaPreferidaParaLogin(anotada("https", "x.example", 443)));
		assertEquals(Optional.empty(),
				HostDeLaPeticion.escuelaPreferidaParaLogin(anotada("https", "moiskimdo.es", 443)));
		assertEquals(Optional.empty(),
				HostDeLaPeticion.escuelaPreferidaParaLogin(anotada("https", "evil.example", 443)));
		assertEquals(Optional.empty(), HostDeLaPeticion.escuelaPreferidaParaLogin(null));
	}

	@Test
	void mismoSitio_ignoraWwwYMayusculas() {
		assertTrue(HostDeLaPeticion.mismoSitio("moiskimdo.es", "WWW.moiskimdo.es"));
		assertTrue(!HostDeLaPeticion.mismoSitio("x.example", "moiskimdo.es"));
		assertTrue(!HostDeLaPeticion.mismoSitio("otra.moiskimdo.es", "moiskimdo.es"));
	}

	private MockHttpServletRequest anotada(String esquema, String host, int puerto) {
		MockHttpServletRequest request = peticion(esquema, host, puerto);
		HostDeLaPeticion.anotar(request, porHost, 1L);
		return request;
	}

	private static MockHttpServletRequest peticion(String esquema, String host, int puerto) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
		request.setScheme(esquema);
		request.setServerName(host);
		request.setServerPort(puerto);
		return request;
	}
}
