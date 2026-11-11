package com.taemoi.project.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.taemoi.project.web.ClavesWeb.Clave;
import com.taemoi.project.web.ClavesWeb.Tipo;

/** Pieza W12a: el catálogo de claves del editor (boceto aprobado de «La línea comarcal», d517-d527). */
class ClavesWebTest {

	private static Clave clave(String clave) {
		return ClavesWeb.de(clave).orElseThrow(() -> new AssertionError("debería existir: " + clave));
	}

	@Test
	void textosDeLasSietePaginas_conSuMaximo() {
		// 184 textos del TX del boceto (con los 22 de los casos raros, d528) + 7 cabeceras + 12 campos de bloque.
		assertEquals(203, ClavesWeb.clavesFijas());
		assertEquals(20, clave("menu.horarios").maximo());
		assertTrue(clave("menu.horarios").obligatorio());
		assertEquals(300, clave("pie.creditos").maximo());
		assertFalse(clave("pie.creditos").obligatorio());
		assertEquals(40, clave("sede.preinscribirme_en").maximo());
		assertEquals(160, clave("tarifas.pie_cuadro").maximo());
		assertEquals(1200, clave("legal.aviso.p2").maximo());
		assertEquals(80, clave("legal.priv.h1").maximo());
		assertEquals(40, clave("legal.normas").maximo());
		assertEquals(200, clave("pre.paso_correo_texto").maximo());
		// Casos raros (d528).
		assertEquals(40, clave("sede.sin_clases").maximo());
		assertEquals(160, clave("tarifas.vacio_texto").maximo());
		assertEquals(120, clave("eventos.sin_anteriores").maximo());
		assertEquals(24, clave("evento.una_foto").maximo());
		assertEquals(48, clave("pre.enviar_espera").maximo());
		assertFalse(clave("pre.paso_correo_espera").obligatorio());
		for (String k : List.of("menu.contacto", "cabecera.menu", "horarios.boton_espera", "contacto.sedes_titulo",
				"evento.n_fotos", "legal.ficha_cif", "pre.anio")) {
			assertEquals(Tipo.TEXTO, clave(k).tipo(), k);
			assertNull(clave(k).seccion(), k);
		}
	}

	/**
	 * d528: los 22 textos que solo salen en casos poco frecuentes («Ver el caso» del boceto: grupo lleno o en lista de
	 * espera, sin clases, sin tarifas, sin eventos, sin próximos, sin anteriores, un evento, una foto, preinscripción sin
	 * grupo, completa y en lista de espera), con el máximo y si son obligatorios del {@code TX} del boceto.
	 */
	@Test
	void textosDeLosCasosPocoFrecuentes() {
		String casos = """
				sede.sin_clases                40  obligatorio
				tarifas.vacio_titular          40  obligatorio
				tarifas.vacio_texto           160  obligatorio
				tarifas.vacio_correo           24  obligatorio
				tarifas.vacio_telefono         24  obligatorio
				eventos.sin_proximos_titular   40  obligatorio
				eventos.sin_proximos_texto    160  opcional
				eventos.ver_anteriores         40  obligatorio
				eventos.un_evento              24  obligatorio
				eventos.sin_anteriores        120  obligatorio
				evento.una_foto                24  obligatorio
				pre.vacio_titulo               60  obligatorio
				pre.vacio_otras               160  opcional
				pre.vacio_llamar               80  obligatorio
				pre.vacio_llamar_fin           40  obligatorio
				pre.revisar_fecha              40  obligatorio
				pre.espera_titulo              80  obligatorio
				pre.espera_texto              160  opcional
				pre.enviar_espera              48  obligatorio
				pre.fin_espera                 40  obligatorio
				pre.estado_espera             160  obligatorio
				pre.paso_correo_espera        200  opcional
				""";
		List<String> lineas = casos.strip().lines().toList();
		assertEquals(22, lineas.size());
		for (String linea : lineas) {
			String[] p = linea.strip().split("\\s+");
			Clave c = clave(p[0]);
			assertEquals(Tipo.TEXTO, c.tipo(), p[0]);
			assertEquals(Integer.parseInt(p[1]), c.maximo(), p[0]);
			assertEquals("obligatorio".equals(p[2]), c.obligatorio(), p[0]);
			assertNull(c.seccion(), "no se oculta con ningún bloque: " + p[0]);
		}
		// Grupo lleno y lista de espera en Inicio y en la sede: textos que ya están en sus páginas.
		for (String k : List.of("horarios.lleno", "horarios.espera", "horarios.boton_espera", "sede.leyenda_lleno",
				"sede.leyenda_espera")) {
			assertEquals(Tipo.TEXTO, clave(k).tipo(), k);
		}
	}

	/** d527: los mensajes de error del formulario de preinscripción no son editables. */
	@Test
	void sinErroresDelFormulario_niClavesInventadas() {
		for (String k : List.of("pre.error_dni", "pre.errores", "portada.alt", "menu.otro", "profesor.0.nombre",
				"profesor.100.nombre", "profesor.1.dni", "galeria.0", "galeria.7.foto", "texto_foto.1.foto.x",
				"texto_foto", "disciplina.TAEKWONDO.descripcion", "disciplina.taekwondo.fila0.texto",
				"disciplina.taekwondo.fila10.texto", "tarifas.punto0", "tarifas.punto10", "", "legal.aviso.p9")) {
			assertTrue(ClavesWeb.de(k).isEmpty(), k);
		}
		assertTrue(ClavesWeb.de(null).isEmpty());
	}

	@Test
	void cabecerasDeSeccion() {
		for (String k : List.of("presentacion", "profesores", "galeria", "sedes", "texto_foto.1", "texto_foto.99")) {
			assertEquals(Tipo.CABECERA, clave(k).tipo(), k);
			assertEquals(k, clave(k).seccion(), "una sección movible es su propia sección: " + k);
		}
		for (String k : List.of("disciplinas", "eventos", "tarifas")) {
			assertEquals(Tipo.CABECERA, clave(k).tipo(), k);
			assertNull(clave(k).seccion(), "fija, no se oculta: " + k);
		}
	}

	@Test
	void camposDeLosBloques() {
		assertEquals(60, clave("portada.titular").maximo());
		assertTrue(clave("portada.titular").obligatorio());
		assertEquals(160, clave("portada.frase").maximo());
		assertEquals(Tipo.IMAGEN, clave("portada.foto").tipo());
		assertEquals(150, clave("portada.foto").maximo());
		assertEquals(1200, clave("presentacion.texto").maximo());
		assertEquals("presentacion", clave("presentacion.foto").seccion());
		assertEquals(600, clave("texto_foto.3.texto").maximo());
		assertEquals("texto_foto.3", clave("texto_foto.3.titulo").seccion());
		assertTrue(clave("texto_foto.3.titulo").obligatorio());
		assertEquals(Tipo.IMAGEN, clave("texto_foto.3.foto").tipo());
		assertTrue(clave("galeria.rotulo").obligatorio());
		assertEquals(Tipo.IMAGEN, clave("galeria.6").tipo());
		assertEquals("galeria", clave("galeria.12").seccion());
		assertEquals("sedes", clave("sedes.titulo").seccion());
		assertEquals("sedes", clave("sedes.ver_sede").seccion(), "el enlace de la línea de fotos de sedes");
		assertNull(clave("sedes.proxima_rotulo").seccion(), "es de Horarios");
	}

	@Test
	void profesores() {
		Clave nombre = clave("profesor.7.nombre");
		assertEquals(60, nombre.maximo());
		assertTrue(nombre.obligatorio());
		assertTrue(nombre.esNombreDeProfesor());
		assertEquals(7, nombre.profesor());
		assertEquals("profesores", nombre.seccion());
		assertEquals(40, clave("profesor.7.cargo").maximo());
		assertFalse(clave("profesor.7.cargo").esNombreDeProfesor());
		assertEquals(Tipo.IMAGEN, clave("profesor.7.foto").tipo());
		assertEquals(Tipo.SEDES, clave("profesor.7.sedes").tipo());
		assertEquals("profesores", clave("profesores.titulo").seccion());
	}

	@Test
	void disciplinas_conElCodigoEnMinusculas_yLaClaveHasta60() {
		Clave descripcion = clave("disciplina.taekwondo.descripcion");
		assertEquals(600, descripcion.maximo());
		assertEquals("taekwondo", descripcion.disciplina());
		assertNull(descripcion.seccion());
		assertEquals(24, clave("disciplina.taekwondo.fila4.rotulo").maximo());
		assertEquals(120, clave("disciplina.taekwondo.fila4.texto").maximo());
		assertEquals(Tipo.IMAGEN, clave("disciplina.kickboxing.foto").tipo());
		// Un código real largo cabe.
		String larga = "disciplina.defensa_personal.fila9.rotulo";
		assertEquals("defensa_personal", clave(larga).disciplina());
		// Más de 60 en total no cabe en la columna.
		String demasiado = "disciplina." + "x".repeat(40) + ".fila1.rotulo";
		assertTrue(demasiado.length() > ClavesWeb.MAXIMO_CLAVE);
		assertTrue(ClavesWeb.de(demasiado).isEmpty());
		assertEquals(80, clave("tarifas.punto3").maximo());
	}

	@Test
	void seccionDe() {
		assertEquals("profesores", ClavesWeb.seccionDe("profesor.2.foto"));
		assertEquals("texto_foto.4", ClavesWeb.seccionDe("texto_foto.4.texto"));
		assertNull(ClavesWeb.seccionDe("menu.horarios"));
		assertNull(ClavesWeb.seccionDe("no.existe"));
	}
}
