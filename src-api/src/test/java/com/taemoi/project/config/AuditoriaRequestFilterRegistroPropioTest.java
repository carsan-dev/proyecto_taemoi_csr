package com.taemoi.project.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.taemoi.project.config.AuditoriaRequestFilter.EscrituraConRegistroPropio;
import com.taemoi.project.entities.AuditoriaAccion;
import com.taemoi.project.services.AuditoriaPayloadSanitizer;
import com.taemoi.project.services.AuditoriaService;

/**
 * Pieza E7-9 (decisión 214): el filtro HTTP no apunta el 2xx de las escrituras que ya dejan su registro explícito
 * ({@link EscrituraConRegistroPropio}); los 4xx/5xx sí. Cada entrada de la lista tiene aquí su ejemplo
 * ({@link #EJEMPLOS}) y en {@code RegistroUnicoProfesorIntegrationTest} su caso contra MySQL: una entrada nueva sin
 * ejemplo hace fallar {@link #cadaEntradaTieneSuEjemplo()}.
 */
class AuditoriaRequestFilterRegistroPropioTest {

	private record Ejemplo(String metodo, String ruta) {
	}

	private static final Map<EscrituraConRegistroPropio, Ejemplo> EJEMPLOS = new EnumMap<>(Map.ofEntries(
			Map.entry(EscrituraConRegistroPropio.LOGO_ESCUELA_CAMBIAR,
					new Ejemplo("PUT", "/api/plataforma/escuelas/7/logo")),
			Map.entry(EscrituraConRegistroPropio.LOGO_ESCUELA_QUITAR,
					new Ejemplo("DELETE", "/api/plataforma/escuelas/7/logo")),
			Map.entry(EscrituraConRegistroPropio.MARCA_ESCUELA_COLOR,
					new Ejemplo("PUT", "/api/plataforma/escuelas/7/marca")),
			Map.entry(EscrituraConRegistroPropio.PROFESOR_APTO,
					new Ejemplo("PUT", "/api/profesor/alumnos/12/deportes/TAEKWONDO/apto")),
			Map.entry(EscrituraConRegistroPropio.PROFESOR_FECHA_GRADO,
					new Ejemplo("PUT", "/api/profesor/alumnos/12/deportes/TAEKWONDO/fecha-grado")),
			Map.entry(EscrituraConRegistroPropio.PROFESOR_PASE_EXAMEN,
					new Ejemplo("POST", "/api/profesor/alumnos/12/deportes/TAEKWONDO/pase-examen")),
			Map.entry(EscrituraConRegistroPropio.PROFESOR_PASE_RECOMPENSA,
					new Ejemplo("POST", "/api/profesor/alumnos/12/deportes/TAEKWONDO/pase-recompensa")),
			Map.entry(EscrituraConRegistroPropio.CAJA_PROFESOR_COBRAR_CARGO,
					new Ejemplo("POST", "/api/caja/profesor/cargos/88/cobrar")),
			Map.entry(EscrituraConRegistroPropio.CAJA_PROFESOR_DESHACER_COBRO,
					new Ejemplo("POST", "/api/caja/profesor/cobros/91/deshacer")),
			Map.entry(EscrituraConRegistroPropio.CAJA_PROFESOR_VENDER_PRODUCTO,
					new Ejemplo("POST", "/api/caja/profesor/alumnos/12/productos/5/cobrar")),
			Map.entry(EscrituraConRegistroPropio.CAJA_PROFESOR_PEDIR_ENCARGO,
					new Ejemplo("POST", "/api/caja/profesor/alumnos/12/encargos")),
			Map.entry(EscrituraConRegistroPropio.CAJA_PROFESOR_RETIRAR_SOLICITUD,
					new Ejemplo("POST", "/api/caja/profesor/solicitudes/40/retirar")),
			Map.entry(EscrituraConRegistroPropio.CAJA_PROFESOR_ENCARGO_ENTREGADO,
					new Ejemplo("POST", "/api/caja/profesor/solicitudes/40/entregado")),
			Map.entry(EscrituraConRegistroPropio.CAJA_PROFESOR_DEVOLUCION,
					new Ejemplo("POST", "/api/caja/profesor/solicitudes/40/devolucion")),
			Map.entry(EscrituraConRegistroPropio.CAJA_PROFESOR_PEDIR_DESHACER,
					new Ejemplo("POST", "/api/caja/profesor/cobros/91/pedir-deshacer")),
			Map.entry(EscrituraConRegistroPropio.GRUPO_QUITAR_PROFESOR,
					new Ejemplo("DELETE", "/api/grupos/3/profesores/21")),
			Map.entry(EscrituraConRegistroPropio.SEDE_CREAR, new Ejemplo("POST", "/api/sedes")),
			Map.entry(EscrituraConRegistroPropio.SEDE_EDITAR, new Ejemplo("PUT", "/api/sedes/5")),
			Map.entry(EscrituraConRegistroPropio.SEDE_ACTIVA, new Ejemplo("PUT", "/api/sedes/5/activa")),
			Map.entry(EscrituraConRegistroPropio.ALUMNO_CAMBIAR_SEDE, new Ejemplo("PUT", "/api/alumnos/12/sede")),
			Map.entry(EscrituraConRegistroPropio.MIEMBRO_ALTA, new Ejemplo("POST", "/api/miembros")),
			Map.entry(EscrituraConRegistroPropio.MIEMBRO_ROL_DAR,
					new Ejemplo("PUT", "/api/miembros/21/roles/PROFESOR")),
			Map.entry(EscrituraConRegistroPropio.MIEMBRO_ROL_QUITAR,
					new Ejemplo("DELETE", "/api/miembros/21/roles/PROFESOR")),
			Map.entry(EscrituraConRegistroPropio.MIEMBRO_SEDES, new Ejemplo("PUT", "/api/miembros/21/sedes")),
			Map.entry(EscrituraConRegistroPropio.MIEMBRO_GRUPOS, new Ejemplo("PUT", "/api/miembros/21/grupos")),
			Map.entry(EscrituraConRegistroPropio.MIEMBRO_INVITACION,
					new Ejemplo("POST", "/api/miembros/21/invitacion")),
			Map.entry(EscrituraConRegistroPropio.MIEMBRO_INVITACION_CANCELAR,
					new Ejemplo("DELETE", "/api/miembros/21/invitacion")),
			Map.entry(EscrituraConRegistroPropio.PANEL_MI_PERFIL, new Ejemplo("PUT", "/api/panel/mi-perfil")),
			Map.entry(EscrituraConRegistroPropio.ASISTENCIA_PROFESOR,
					new Ejemplo("PUT", "/api/profesor/grupos/3/clases/9/2026-10-26")),
			Map.entry(EscrituraConRegistroPropio.ASISTENCIA_GESTION,
					new Ejemplo("PUT", "/api/grupos/3/clases/9/2026-10-26"))));

	/**
	 * Fuera de la lista: el filtro sigue apuntando su 2xx. El alta de profesor a un grupo con el MISMO rol responde 200
	 * sin registro explícito (por eso queda fuera); las lecturas y las rutas de gestión, igual que siempre.
	 */
	private static final List<Ejemplo> FUERA_DE_LA_LISTA = List.of(
			new Ejemplo("POST", "/api/grupos/3/profesores"),
			new Ejemplo("GET", "/api/grupos/3/profesores"),
			new Ejemplo("GET", "/api/profesor/grupos"),
			new Ejemplo("GET", "/api/profesor/alumnos/12"),
			new Ejemplo("GET", "/api/profesor/alumnos/12/salud"),
			new Ejemplo("GET", "/api/caja/profesor/cargos-pendientes"),
			new Ejemplo("GET", "/api/caja/profesor/solicitudes"),
			new Ejemplo("GET", "/api/plataforma/escuelas/7/logo"),
			new Ejemplo("PUT", "/api/alumnos/12/deportes/TAEKWONDO/apto"),
			new Ejemplo("POST", "/api/alumnos/12/deportes/TAEKWONDO/pase-examen"),
			new Ejemplo("PUT", "/api/productos-alumno/88"),
			new Ejemplo("POST", "/api/productos-alumno/alumno/12/producto/5"),
			new Ejemplo("POST", "/api/caja/sedes/2/entregas"),
			new Ejemplo("POST", "/api/solicitudes/40/devolucion"),
			new Ejemplo("POST", "/api/solicitudes/40/encargado"),
			new Ejemplo("PUT", "/api/alumnos/12/salud"),
			new Ejemplo("PATCH", "/api/productos/5/vendible-profesor"),
			// Rutas vecinas o futuras bajo los mismos prefijos: nada de comodines.
			new Ejemplo("POST", "/api/profesor/alumnos/12/deportes/TAEKWONDO/grado"),
			new Ejemplo("POST", "/api/caja/profesor/cargos/88/cobrar/extra"),
			new Ejemplo("POST", "/api/caja/profesor/nueva-ruta"),
			new Ejemplo("DELETE", "/api/grupos/3/profesores/21/otra"),
			// Decisión 324: las lecturas de pasar lista y las rutas vecinas sin manejador.
			new Ejemplo("GET", "/api/profesor/grupos/3/clases/9/2026-10-26"),
			new Ejemplo("GET", "/api/grupos/3/clases/9/2026-10-26"),
			new Ejemplo("PUT", "/api/grupos/3/clases/9"),
			new Ejemplo("PUT", "/api/grupos/3/clases/9/2026-10-26/otra"),
			// Pieza E8-2: las lecturas de sedes y las rutas vecinas sin manejador.
			new Ejemplo("GET", "/api/sedes"),
			new Ejemplo("GET", "/api/sedes/5"),
			new Ejemplo("POST", "/api/sedes/5"),
			new Ejemplo("PUT", "/api/sedes"),
			new Ejemplo("PUT", "/api/sedes/5/activa/otra"),
			new Ejemplo("DELETE", "/api/sedes/5"),
			// Pieza E8-3: las lecturas de miembros y las rutas vecinas sin manejador.
			new Ejemplo("GET", "/api/miembros"),
			new Ejemplo("GET", "/api/miembros/21"),
			new Ejemplo("POST", "/api/miembros/21/grupos"),
			new Ejemplo("PUT", "/api/miembros/21/grupos/3"),
			new Ejemplo("POST", "/api/miembros/21"),
			new Ejemplo("PUT", "/api/miembros/21/roles"),
			new Ejemplo("PUT", "/api/miembros/21/roles/PROFESOR/otra"),
			new Ejemplo("POST", "/api/miembros/21/sedes"),
			// Pieza E8-4 y decisión 389: reenviar por POST y cancelar por DELETE, en su ruta exacta.
			new Ejemplo("GET", "/api/miembros/21/invitacion"),
			new Ejemplo("PUT", "/api/miembros/21/invitacion"),
			new Ejemplo("POST", "/api/miembros/21/invitacion/otra"),
			new Ejemplo("DELETE", "/api/miembros/21/invitacion/otra"),
			new Ejemplo("POST", "/api/miembros/invitacion"),
			// Decisión 322: la lectura del perfil y las rutas vecinas sin manejador.
			new Ejemplo("GET", "/api/panel/mi-perfil"),
			new Ejemplo("POST", "/api/panel/mi-perfil"),
			new Ejemplo("DELETE", "/api/panel/mi-perfil"),
			new Ejemplo("PUT", "/api/panel/mi-perfil/21"),
			new Ejemplo("PUT", "/api/panel"),
			// Pieza 8b-T: la lectura de la marca y del logo, y las rutas vecinas sin manejador.
			new Ejemplo("GET", "/api/escuela/marca"),
			new Ejemplo("GET", "/api/escuela/logo"),
			new Ejemplo("PUT", "/api/escuela/marca"),
			new Ejemplo("GET", "/api/plataforma/escuelas/7/marca"),
			new Ejemplo("DELETE", "/api/plataforma/escuelas/7/marca"),
			new Ejemplo("POST", "/api/plataforma/escuelas/7/marca"),
			new Ejemplo("PUT", "/api/plataforma/escuelas/7/marca/otra"),
			new Ejemplo("PUT", "/api/plataforma/escuelas/marca"),
			new Ejemplo("POST", "/x/api/caja/profesor/cargos/88/cobrar"));

	private static final List<String> METODOS = List.of("GET", "POST", "PUT", "PATCH", "DELETE");

	@Test
	void cadaEntradaTieneSuEjemplo() {
		assertEquals(EnumSet.allOf(EscrituraConRegistroPropio.class), EJEMPLOS.keySet(),
				"Cada ruta saltada necesita su ejemplo aquí y su caso en RegistroUnicoProfesorIntegrationTest");
	}

	@ParameterizedTest
	@EnumSource(EscrituraConRegistroPropio.class)
	void correctaSeSalta_errorNo(EscrituraConRegistroPropio entrada) {
		Ejemplo ejemplo = EJEMPLOS.get(entrada);
		for (int estado : List.of(200, 201, 204)) {
			assertTrue(AuditoriaRequestFilter.tieneRegistroPropio(ejemplo.metodo(), estado, ejemplo.ruta()),
					entrada + " " + estado);
		}
		for (int estado : List.of(400, 401, 403, 404, 409, 500, 503)) {
			assertFalse(AuditoriaRequestFilter.tieneRegistroPropio(ejemplo.metodo(), estado, ejemplo.ruta()),
					entrada + " " + estado);
		}
	}

	@ParameterizedTest
	@EnumSource(EscrituraConRegistroPropio.class)
	void soloSuMetodo(EscrituraConRegistroPropio entrada) {
		Ejemplo ejemplo = EJEMPLOS.get(entrada);
		String ruta = AuditoriaRequestFilter.normalizarParaComparar(ejemplo.ruta());
		for (String metodo : METODOS) {
			assertEquals(metodo.equals(ejemplo.metodo()), entrada.reconoce(metodo, ruta), entrada + " " + metodo);
		}
		assertEquals(ejemplo.metodo(), entrada.metodo());
	}

	/** Cada ejemplo lo reconoce solo su entrada (sin solapes entre patrones). */
	@ParameterizedTest
	@EnumSource(EscrituraConRegistroPropio.class)
	void sinSolapes(EscrituraConRegistroPropio entrada) {
		Ejemplo ejemplo = EJEMPLOS.get(entrada);
		String ruta = AuditoriaRequestFilter.normalizarParaComparar(ejemplo.ruta());
		Set<EscrituraConRegistroPropio> reconocen = EnumSet.noneOf(EscrituraConRegistroPropio.class);
		for (EscrituraConRegistroPropio otra : EscrituraConRegistroPropio.values()) {
			if (otra.reconoce(ejemplo.metodo(), ruta)) {
				reconocen.add(otra);
			}
		}
		assertEquals(EnumSet.of(entrada), reconocen);
	}

	/** Misma normalización que salud y el logo: barra final, mayúsculas, codificación y parámetros de ruta. */
	@ParameterizedTest
	@EnumSource(EscrituraConRegistroPropio.class)
	void rutaNormalizada(EscrituraConRegistroPropio entrada) {
		Ejemplo ejemplo = EJEMPLOS.get(entrada);
		String ruta = ejemplo.ruta();
		for (String variante : List.of(ruta + "/", ruta.toUpperCase(), ruta.replace("/api/", "/api//"),
				ruta.replace("/api/", "/%61pi/"), ruta.replace("/api/", "/api;x=1/"))) {
			assertTrue(AuditoriaRequestFilter.tieneRegistroPropio(ejemplo.metodo(), 200, variante), variante);
		}
	}

	@Test
	void fueraDeLaLista_seApuntaComoSiempre() {
		for (Ejemplo ejemplo : FUERA_DE_LA_LISTA) {
			for (int estado : List.of(200, 201, 204, 400, 404, 500)) {
				assertFalse(AuditoriaRequestFilter.tieneRegistroPropio(ejemplo.metodo(), estado, ejemplo.ruta()),
						ejemplo + " " + estado);
			}
		}
		assertFalse(AuditoriaRequestFilter.tieneRegistroPropio("POST", 200, null));
		assertFalse(AuditoriaRequestFilter.tieneRegistroPropio(null, 200, "/api/caja/profesor/cargos/88/cobrar"));
	}

	// ------------------------------------------------------------------ el filtro completo

	@ParameterizedTest
	@EnumSource(EscrituraConRegistroPropio.class)
	void filtro_correctaSinFilaGenerica_errorConFila(EscrituraConRegistroPropio entrada) throws Exception {
		Ejemplo ejemplo = EJEMPLOS.get(entrada);
		AuditoriaService auditoria = mock(AuditoriaService.class);
		AuditoriaRequestFilter filtro = new AuditoriaRequestFilter(auditoria, mock(AuditoriaPayloadSanitizer.class));

		pasar(filtro, ejemplo, 200);
		pasar(filtro, ejemplo, 201);
		pasar(filtro, ejemplo, 204);
		verify(auditoria, never()).registrarEvento(any(), anyString(), anyString(), any(), any(), anyBoolean(), any(),
				any(), any());

		pasar(filtro, ejemplo, 409);
		pasar(filtro, ejemplo, 500);
		verify(auditoria, times(1)).registrarEvento(any(AuditoriaAccion.class), eq(ejemplo.metodo()),
				eq(ejemplo.ruta()), any(), any(), anyBoolean(), eq(409), any(), any());
		verify(auditoria, times(1)).registrarEvento(any(AuditoriaAccion.class), eq(ejemplo.metodo()),
				eq(ejemplo.ruta()), any(), any(), anyBoolean(), eq(500), any(), any());
	}

	@Test
	void filtro_fueraDeLaLista_correctaConFila() throws Exception {
		for (Ejemplo ejemplo : FUERA_DE_LA_LISTA) {
			boolean lecturaDeSalud = ejemplo.ruta().endsWith("/salud") && "GET".equals(ejemplo.metodo());
			if (!ejemplo.ruta().startsWith("/api/") || lecturaDeSalud) {
				continue; // fuera de /api/ no se audita; la lectura correcta de salud la registra AlumnoSaludService
			}
			AuditoriaService auditoria = mock(AuditoriaService.class);
			AuditoriaRequestFilter filtro = new AuditoriaRequestFilter(auditoria,
					mock(AuditoriaPayloadSanitizer.class));
			pasar(filtro, ejemplo, 200);
			verify(auditoria, times(1)).registrarEvento(any(AuditoriaAccion.class), eq(ejemplo.metodo()),
					eq(ejemplo.ruta()), any(), any(), anyBoolean(), eq(200), any(), any());
		}
	}

	private static void pasar(AuditoriaRequestFilter filtro, Ejemplo ejemplo, int estado) throws Exception {
		MockHttpServletRequest peticion = new MockHttpServletRequest(ejemplo.metodo(), ejemplo.ruta());
		MockHttpServletResponse respuesta = new MockHttpServletResponse();
		filtro.doFilter(peticion, respuesta, (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res)
				.setStatus(estado));
	}
}
