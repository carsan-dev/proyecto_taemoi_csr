package com.taemoi.project.organizacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** Pieza 2 de la fase 4: base de los enlaces de correo por escuela. */
class UrlsEscuelaTest {

	private static final String GLOBAL = "https://moiskimdo.es";

	private final UrlsEscuela urls = UrlsEscuelaPruebas.conPrincipales(GLOBAL,
			Map.of(1L, "moiskimdo.es", 2L, "escuela2.example.test"));

	@Test
	void moiskimdoDaExactamenteLaBaseDeHoy() {
		assertEquals("https://moiskimdo.es", urls.baseFrontend(1L));
	}

	@Test
	void laEscuelaPorDefectoUsaLaGlobalAunqueTengaDominioPrincipal() {
		UrlsEscuela local = UrlsEscuelaPruebas.conPrincipales("http://localhost:4200",
				Map.of(1L, "moiskimdo.es", 2L, "escuela2.example.test"));

		assertEquals("http://localhost:4200", local.baseFrontend(1L));
		assertEquals("https://escuela2.example.test", local.baseFrontend(2L));
	}

	@Test
	void otraEscuelaConDominioPrincipalDaSuHost() {
		assertEquals("https://escuela2.example.test", urls.baseFrontend(2L));
	}

	@Test
	void escuelaSinDominioOSinEscuelaDaLaBaseGlobal() {
		assertEquals(GLOBAL, urls.baseFrontend(3L));
		assertEquals(GLOBAL, urls.baseFrontend(null));
	}

	@Test
	void sinTablaTodoVaALaGlobalTalCualVenga() {
		assertEquals("https://portal.example.com/",
				UrlsEscuelaPruebas.soloGlobal("https://portal.example.com/").baseFrontend(1L));
	}

	@Test
	void elHostPrincipalSeNormalizaYLaTablaSeLeeUnaSolaVez() {
		int[] lecturas = { 0 };
		ResolutorEscuelaPorHost resolutor = new ResolutorEscuelaPorHost(1L, () -> {
			lecturas[0]++;
			return new ResolutorEscuelaPorHost.Dominios(Map.of("x.test", 2L), Map.of(2L, " Escuela2.Example.TEST:443 "));
		});
		UrlsEscuela escuela = new UrlsEscuela(resolutor, GLOBAL);

		assertEquals("https://escuela2.example.test", escuela.baseFrontend(2L));
		assertEquals("https://escuela2.example.test", escuela.baseFrontend(2L));
		assertEquals(GLOBAL, escuela.baseFrontend(1L));
		assertEquals(1, lecturas[0], "sin lecturas por correo");
		assertTrue(resolutor.hostPrincipalDe(3L).isEmpty());
	}
}
