package com.taemoi.project.plataforma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

/** Fase 4, pieza F4-2 (decisión 444): el slug sale del nombre. */
class SlugEscuelaTest {

	@Test
	void minusculasSinTildesConGuiones() {
		assertEquals("taekwondo-sierra-sur", SlugEscuela.base("Taekwondo Sierra Sur"));
		assertEquals("club-nandu-avila-cceee", SlugEscuela.base("  Club Ñandú   Ávila — ÇçÉèë!! "));
		assertEquals("a-b-1", SlugEscuela.base("A.B/1"));
	}

	@Test
	void sinLetrasNiCifras_elPorDefecto() {
		assertEquals(SlugEscuela.POR_DEFECTO, SlugEscuela.base("★★"));
		assertEquals(SlugEscuela.POR_DEFECTO, SlugEscuela.base(""));
	}

	@Test
	void comoMuchoSesenta_sinGuionAlFinal() {
		String largo = "palabra ".repeat(20);
		String slug = SlugEscuela.base(largo);
		assertTrue(slug.length() <= 60, slug);
		assertTrue(!slug.endsWith("-") && !slug.startsWith("-"), slug);
	}

	@Test
	void ocupado_sufijoDosTres_recortandoLaBase() {
		assertEquals("club", SlugEscuela.libre("Club", s -> false));
		assertEquals("club-2", SlugEscuela.libre("Club", Set.of("club")::contains));
		assertEquals("club-3", SlugEscuela.libre("CLUB", Set.of("club", "club-2")::contains));
		String base = "x".repeat(60);
		String libre = SlugEscuela.libre(base, Set.of(base)::contains);
		assertEquals("x".repeat(58) + "-2", libre);
		assertEquals(60, libre.length());
	}
}
