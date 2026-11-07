package com.taemoi.project.organizacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Fase 4, escuela por dominio (pieza 1): el Host de la petición, normalizado, da la escuela; un host que no está en
 * {@code organizacion_dominio} da la escuela por defecto (como hasta ahora). La tabla se lee en una caché que solo se
 * recarga a propósito ({@link ResolutorEscuelaPorHost#recargar()}): buscar no consulta la base.
 */
class ResolutorEscuelaPorHostTest {

	private static final long POR_DEFECTO = 1L;

	private final AtomicInteger cargas = new AtomicInteger();
	private final AtomicReference<Map<String, Long>> tabla = new AtomicReference<>(
			Map.of("moiskimdo.es", 1L, "x.test", 5L));
	private final Supplier<Map<String, Long>> cargador = () -> {
		cargas.incrementAndGet();
		return tabla.get();
	};
	private final ResolutorEscuelaPorHost resolutor = new ResolutorEscuelaPorHost(cargador, POR_DEFECTO);

	@Test
	void hostRegistrado_daSuEscuela() {
		assertEquals(5L, resolutor.escuelaDelHost("x.test"));
		assertEquals(1L, resolutor.escuelaDelHost("moiskimdo.es"));
	}

	@Test
	void mayusculasPuertoYPuntoFinal_seNormalizan() {
		assertEquals(5L, resolutor.escuelaDelHost("X.TEST"));
		assertEquals(5L, resolutor.escuelaDelHost("x.test:8443"));
		assertEquals(5L, resolutor.escuelaDelHost("x.test."));
		assertEquals(5L, resolutor.escuelaDelHost(" X.Test.:443 "));
		assertEquals("x.test", ResolutorEscuelaPorHost.normalizar("X.Test.:443"));
	}

	@Test
	void hostDesconocidoVacioOIp_daLaPorDefecto() {
		assertEquals(POR_DEFECTO, resolutor.escuelaDelHost("desconocido.test"));
		assertEquals(POR_DEFECTO, resolutor.escuelaDelHost("localhost"));
		assertEquals(POR_DEFECTO, resolutor.escuelaDelHost("127.0.0.1"));
		assertEquals(POR_DEFECTO, resolutor.escuelaDelHost("[::1]:8080"));
		assertEquals(POR_DEFECTO, resolutor.escuelaDelHost(""));
		assertEquals(POR_DEFECTO, resolutor.escuelaDelHost(null));
		assertEquals(POR_DEFECTO, resolutor.escuelaDelHost("sub.x.test"), "búsqueda exacta, sin comodines");
		assertEquals(Optional.empty(), resolutor.escuelaRegistrada("desconocido.test"));
		assertNull(ResolutorEscuelaPorHost.normalizar("   "));
		assertNull(ResolutorEscuelaPorHost.normalizar("a".repeat(254)), "más largo que un nombre DNS");
	}

	@Test
	void laPeticionUsaGetServerName() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Host", "X.test:443");
		assertEquals(5L, resolutor.escuelaDe(request));
		assertEquals(POR_DEFECTO, resolutor.escuelaDe(new MockHttpServletRequest()), "localhost");
	}

	@Test
	void cache_buscarNoVuelveACargarHastaRecargar() {
		for (int i = 0; i < 50; i++) {
			resolutor.escuelaDelHost(i % 2 == 0 ? "x.test" : "desconocido.test");
		}
		assertEquals(1, cargas.get(), "una sola lectura de la tabla, la primera vez");

		tabla.set(Map.of("moiskimdo.es", 1L, "x.test", 5L, "y.test", 6L));
		assertEquals(POR_DEFECTO, resolutor.escuelaDelHost("y.test"), "la caché no ve la fila nueva todavía");
		resolutor.recargar();
		assertEquals(6L, resolutor.escuelaDelHost("y.test"));
		assertEquals(2, cargas.get());
	}

	@Test
	void hostsDeLaTabla_tambienSeNormalizan() {
		tabla.set(Map.of("Z.Test.", 7L));
		resolutor.recargar();
		assertEquals(7L, resolutor.escuelaDelHost("z.test"));
	}

	@Test
	void siLaRecargaFalla_seQuedaLoAnterior() {
		AtomicInteger intentos = new AtomicInteger();
		ResolutorEscuelaPorHost conFallo = new ResolutorEscuelaPorHost(() -> {
			if (intentos.incrementAndGet() > 1) {
				throw new IllegalStateException("base caída");
			}
			return Map.of("x.test", 5L);
		}, POR_DEFECTO);
		assertEquals(5L, conFallo.escuelaDelHost("x.test"));
		conFallo.recargar();
		assertEquals(5L, conFallo.escuelaDelHost("x.test"), "sigue con la última tabla buena");
	}

	@Test
	void siLaPrimeraCargaFalla_todoVaALaPorDefecto() {
		ResolutorEscuelaPorHost sinBase = new ResolutorEscuelaPorHost(() -> {
			throw new IllegalStateException("base caída");
		}, POR_DEFECTO);
		assertEquals(POR_DEFECTO, sinBase.escuelaDelHost("x.test"));
		assertEquals(POR_DEFECTO, sinBase.escuelaDelHost("moiskimdo.es"));
	}

	@Test
	void sinDominios_siempreLaPorDefecto() {
		ResolutorEscuelaPorHost sin = ResolutorEscuelaPorHost.sinDominios(9L);
		assertEquals(9L, sin.escuelaDelHost("x.test"));
		assertEquals(9L, sin.getOrganizacionPorDefecto());
		assertTrue(sin.escuelaRegistrada("x.test").isEmpty());
	}
}
