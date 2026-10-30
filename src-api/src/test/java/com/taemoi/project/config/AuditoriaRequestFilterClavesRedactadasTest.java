package com.taemoi.project.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Claves del cuerpo que el filtro HTTP no guarda en las filas genéricas (las 4xx de las escrituras con registro
 * propio y las demás). Pieza E8-2: el alta y la edición de una sede llevan datos de contacto (D8-16).
 */
class AuditoriaRequestFilterClavesRedactadasTest {

	private static final Set<String> DATOS_DE_SEDE = Set.of("nombre", "direccion", "localidad", "codigoPostal",
			"telefono", "email", "codigoClubFederacion");

	@Test
	void sedes_altaYEdicion_redactanSusDatos() {
		for (String ruta : List.of("/api/sedes", "/api/sedes/", "/API/SEDES", "/api/sedes/5", "/api/sedes/5/",
				"/api//sedes/5", "/api/%73edes/5")) {
			assertEquals(DATOS_DE_SEDE, AuditoriaRequestFilter.clavesRedactadas(ruta), ruta);
		}
	}

	@Test
	void sedes_activa_noRedacta() {
		assertEquals(Set.of(), AuditoriaRequestFilter.clavesRedactadas("/api/sedes/5/activa"));
		assertEquals(Set.of(), AuditoriaRequestFilter.clavesRedactadas("/api/sedes-otra"));
	}

	/** Pieza E8-3: email, cargo, nombre y apellidos del alta de un miembro; el cargo al dar un rol. */
	@Test
	void miembros_altaYRoles_redactanEmailYCargo() {
		for (String ruta : List.of("/api/miembros", "/api/miembros/", "/API/MIEMBROS", "/api//miembros")) {
			assertEquals(Set.of("email", "cargo", "nombre", "apellidos"), AuditoriaRequestFilter.clavesRedactadas(ruta),
					ruta);
		}
		for (String ruta : List.of("/api/miembros/21/roles/PROFESOR", "/api/miembros/21/roles/profesor/",
				"/api/%6Diembros/21/roles/PROFESOR")) {
			assertEquals(Set.of("cargo"), AuditoriaRequestFilter.clavesRedactadas(ruta), ruta);
		}
	}

	@Test
	void miembros_resto_noRedacta() {
		for (String ruta : List.of("/api/miembros/21", "/api/miembros/21/sedes", "/api/miembros/21/grupos",
				"/api/miembros/21/roles", "/api/miembros-otra")) {
			assertEquals(Set.of(), AuditoriaRequestFilter.clavesRedactadas(ruta), ruta);
		}
	}

	/** Decisión 322: ni el nombre ni la fecha del perfil se guardan en el cuerpo de un 4xx de su PUT. */
	@Test
	void miPerfil_redactaNombreYCumple() {
		for (String ruta : List.of("/api/panel/mi-perfil", "/api/panel/mi-perfil/", "/API/PANEL/MI-PERFIL",
				"/api//panel/mi-perfil")) {
			assertEquals(Set.of("nombrePreferido", "cumpleDia", "cumpleMes"),
					AuditoriaRequestFilter.clavesRedactadas(ruta), ruta);
		}
		assertEquals(Set.of(), AuditoriaRequestFilter.clavesRedactadas("/api/panel/mi-perfil-otra"));
	}

	/** Pieza 8b-T: el color de la escuela no se guarda en el cuerpo de un 4xx del PUT de la marca. */
	@Test
	void marca_redactaElColor() {
		for (String ruta : List.of("/api/plataforma/escuelas/7/marca", "/api/plataforma/escuelas/7/marca/",
				"/API/PLATAFORMA/ESCUELAS/7/MARCA", "/api//plataforma/escuelas/7/marca")) {
			assertEquals(Set.of("colorPrimario"), AuditoriaRequestFilter.clavesRedactadas(ruta), ruta);
		}
		for (String ruta : List.of("/api/plataforma/escuelas/7/logo", "/api/plataforma/escuelas/7/marca/otra",
				"/api/escuela/marca")) {
			assertEquals(Set.of(), AuditoriaRequestFilter.clavesRedactadas(ruta), ruta);
		}
	}

	/** Pieza E8-3: la búsqueda de miembros ({@code q}) no se guarda; los demás parámetros y rutas, como siempre. */
	@Test
	void miembros_q_redactada() {
		for (String ruta : List.of("/api/miembros", "/api/miembros/", "/API/MIEMBROS/", "/api//miembros")) {
			Map<String, String[]> guardados = AuditoriaRequestFilter.parametrosAuditables(ruta,
					parametros("q", "alguien@example.test", "page", "2", "size", "10"));
			assertArrayEquals(new String[] { "[redactado]" }, guardados.get("q"), ruta);
			assertArrayEquals(new String[] { "2" }, guardados.get("page"), ruta);
			assertArrayEquals(new String[] { "10" }, guardados.get("size"), ruta);
		}
		Map<String, String[]> sinQ = parametros("rol", "PROFESOR");
		assertArrayEquals(new String[] { "PROFESOR" },
				AuditoriaRequestFilter.parametrosAuditables("/api/miembros", sinQ).get("rol"));
		for (String ruta : List.of("/api/miembros/21", "/api/alumnos", "/api/preinscripciones", "/api/miembros-otra")) {
			Map<String, String[]> originales = parametros("q", "Ana", "nombre", "Ana");
			assertSame(originales, AuditoriaRequestFilter.parametrosAuditables(ruta, originales), ruta);
		}
	}

	private static Map<String, String[]> parametros(String... claveValor) {
		Map<String, String[]> mapa = new LinkedHashMap<>();
		for (int i = 0; i < claveValor.length; i += 2) {
			mapa.put(claveValor[i], new String[] { claveValor[i + 1] });
		}
		return mapa;
	}

	@Test
	void rutasDeSiempre_igual() {
		assertEquals(Set.of("email"), AuditoriaRequestFilter.clavesRedactadas("/api/grupos/3/profesores"));
		assertEquals(Set.of("grupo"), AuditoriaRequestFilter.clavesRedactadas("/api/admin/dni-compartidos/aplicar"));
		assertEquals(Set.of(), AuditoriaRequestFilter.clavesRedactadas("/api/alumnos/3"));
	}
}
