package com.taemoi.project.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.taemoi.project.entities.AuditoriaAccion;
import com.taemoi.project.services.AuditoriaPayloadSanitizer;
import com.taemoi.project.services.AuditoriaService;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Pieza R1: un vídeo de 500 MB nunca pasa por los envoltorios del filtro. Las escrituras no copian el cuerpo y los ficheros
 * no copian la respuesta. Ante una forma rara de escribir la ruta, también: lo que no debe ocurrir es el fichero en memoria.
 */
class AuditoriaRequestFilterMaterialExamenTest {

	private static final String BLOQUE = "/api/material-examen/TAEKWONDO/b01_inicio_a_amarillo";

	private static final String FAMILIA = "/api/alumnos/40/deportes/TAEKWONDO/material-examen";

	private final AuditoriaService auditoria = mock(AuditoriaService.class);
	private final AuditoriaPayloadSanitizer saneador = mock(AuditoriaPayloadSanitizer.class);
	private final AuditoriaRequestFilter filtro = new AuditoriaRequestFilter(auditoria, saneador);

	private record Pase(ServletRequest peticion, ServletResponse respuesta) {
	}

	private Pase pasar(MockHttpServletRequest peticion, MockHttpServletResponse respuesta, int estado) throws Exception {
		AtomicReference<ServletRequest> peticionRecibida = new AtomicReference<>();
		AtomicReference<ServletResponse> respuestaRecibida = new AtomicReference<>();
		filtro.doFilter(peticion, respuesta, (req, res) -> {
			peticionRecibida.set(req);
			respuestaRecibida.set(res);
			((HttpServletResponse) res).setStatus(estado);
		});
		return new Pase(peticionRecibida.get(), respuestaRecibida.get());
	}

	@ParameterizedTest
	@ValueSource(strings = { "POST /videos", "PUT /videos/01_a.mp4/contenido", "PUT /temario", "POST /documentacion",
			"PUT /documentacion/guia.pdf/contenido", "PATCH /videos/01_a.mp4", "PUT /videos/orden",
			"DELETE /videos/01_a.mp4", "DELETE /documentacion/guia.pdf" })
	void lasEscrituras_noEnvuelvenLaPeticion(String ruta) throws Exception {
		String[] partes = ruta.split(" ");
		MockHttpServletRequest peticion = new MockHttpServletRequest(partes[0], BLOQUE + partes[1]);

		Pase pase = pasar(peticion, new MockHttpServletResponse(), 200);

		assertSame(peticion, pase.peticion(), "el cuerpo no se copia en memoria");
		assertFalse(pase.peticion() instanceof ContentCachingRequestWrapper);
		verify(auditoria, never()).registrarEvento(any(), any(), any(), any(), any(), anyBoolean(), anyInt(), any(), any());
	}

	@ParameterizedTest
	@ValueSource(strings = { "/%61pi/material-examen/TAEKWONDO/b01/videos", "/api/material-examen/TAEKWONDO/b01/videos/",
			"/API/MATERIAL-EXAMEN/TAEKWONDO/b01/videos", "/api//material-examen/TAEKWONDO/b01/videos" })
	void unaFormaRaraDeEscribirLaRuta_tampocoEnvuelve(String ruta) throws Exception {
		MockHttpServletRequest peticion = new MockHttpServletRequest("POST", ruta);

		Pase pase = pasar(peticion, new MockHttpServletResponse(), 404);

		assertSame(peticion, pase.peticion());
	}

	@Test
	void unaEscrituraFallida_seApuntaSinCuerpo() throws Exception {
		MockHttpServletRequest peticion = new MockHttpServletRequest("POST", BLOQUE + "/videos");
		peticion.setContent(new byte[64]);
		peticion.setContentType("video/mp4");
		AuditoriaPayloadSanitizer saneador = mock(AuditoriaPayloadSanitizer.class);
		AuditoriaRequestFilter conSaneador = new AuditoriaRequestFilter(auditoria, saneador);
		AtomicReference<ServletResponse> respuesta = new AtomicReference<>();

		conSaneador.doFilter(peticion, new MockHttpServletResponse(), (req, res) -> {
			respuesta.set(res);
			((HttpServletResponse) res).setStatus(415);
		});

		assertInstanceOf(ContentCachingResponseWrapper.class, respuesta.get(), "la respuesta es JSON corto");
		verify(saneador).sanitizarPayload(eq(""), eq("video/mp4"));
		verify(auditoria).registrarEvento(any(), eq("POST"), eq(BLOQUE + "/videos"), any(), any(), anyBoolean(), eq(415),
				any(), any());
	}

	@ParameterizedTest
	@ValueSource(strings = { "/temario", "/videos/01_a.mp4", "/videos/10-Pumse%20Il%20Jang.m4v", "/documentacion/guia.pdf" })
	void losFicheros_noEnvuelvenNadaNiSeApuntan(String ruta) throws Exception {
		for (String metodo : List.of("GET", "HEAD")) {
			MockHttpServletRequest peticion = new MockHttpServletRequest(metodo, BLOQUE + ruta);
			MockHttpServletResponse respuesta = new MockHttpServletResponse();

			Pase pase = pasar(peticion, respuesta, 206);

			assertSame(peticion, pase.peticion(), metodo);
			assertSame(respuesta, pase.respuesta(), metodo + ": un vídeo no se copia en memoria");
		}
		verify(auditoria, never()).registrarEvento(any(), any(), any(), any(), any(), anyBoolean(), anyInt(), any(), any());
	}

	@Test
	void unFicheroConLaRutaEscritaDeOtraForma_tampocoSeCopia() throws Exception {
		MockHttpServletRequest peticion = new MockHttpServletRequest("GET",
				"/api//material-examen/TAEKWONDO/b01/videos/01_a.mp4");
		MockHttpServletResponse respuesta = new MockHttpServletResponse();

		assertSame(respuesta, pasar(peticion, respuesta, 200).respuesta());
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/material-examen", BLOQUE })
	void lasListas_siguenEnvueltasYApuntadas(String ruta) throws Exception {
		Pase pase = pasar(new MockHttpServletRequest("GET", ruta), new MockHttpServletResponse(), 200);

		assertInstanceOf(ContentCachingResponseWrapper.class, pase.respuesta());
		verify(auditoria).registrarEvento(any(), anyString(), eq(ruta), any(), any(), anyBoolean(), eq(200), any(), any());
	}

	// Vídeos de las familias: con R1 llegan a 500 MB, y son las mismas peticiones que antes.

	@ParameterizedTest
	@ValueSource(strings = { "/temario", "/videos/01_a.mp4", "/videos/anterior__b01_inicio_a_amarillo__01_a.mp4",
			"/videos/10-Pumse%20Il%20Jang.m4v", "/documentacion/guia.pdf", "/videos/01_a.mp4/" })
	void losFicherosDeLasFamilias_noSeCopian_yConservanSuFila(String ruta) throws Exception {
		MockHttpServletRequest peticion = new MockHttpServletRequest("GET", FAMILIA + ruta);
		MockHttpServletResponse respuesta = new MockHttpServletResponse();

		Pase pase = pasar(peticion, respuesta, 206);

		assertSame(peticion, pase.peticion());
		assertSame(respuesta, pase.respuesta(), "un vídeo no se copia en memoria");
		verify(auditoria).registrarEvento(eq(AuditoriaAccion.READ), eq("GET"), eq(FAMILIA + ruta), any(), any(), anyBoolean(),
				eq(206), any(), any());
	}

	@Test
	void unFicheroDeFamiliaConLaRutaEscritaDeOtraForma_tampocoSeCopia() throws Exception {
		MockHttpServletResponse respuesta = new MockHttpServletResponse();

		assertSame(respuesta, pasar(new MockHttpServletRequest("GET", "/api//alumnos/40/deportes/TAEKWONDO/material-examen/videos/a.mp4"),
				respuesta, 200).respuesta());
	}

	@Test
	void unErrorAlPedirUnFicheroDeFamilia_seApuntaConSuEstado() throws Exception {
		pasar(new MockHttpServletRequest("GET", FAMILIA + "/videos/no.mp4"), new MockHttpServletResponse(), 404);

		verify(auditoria).registrarEvento(eq(AuditoriaAccion.READ), eq("GET"), eq(FAMILIA + "/videos/no.mp4"), any(), any(),
				anyBoolean(), eq(404), any(), any());
	}

	@ParameterizedTest
	@ValueSource(strings = { FAMILIA, FAMILIA + "/", "/api/alumnos/40", "/api/alumnos/40/deportes/TAEKWONDO/material-examen-otra/a",
			"/api/alumnos/40/deportes/TAEKWONDO/material-examen/videos" })
	void loDemasDeLasFamilias_sigueEnvuelto(String ruta) throws Exception {
		Pase pase = pasar(new MockHttpServletRequest("GET", ruta), new MockHttpServletResponse(), 200);

		assertInstanceOf(ContentCachingResponseWrapper.class, pase.respuesta());
		assertInstanceOf(ContentCachingRequestWrapper.class, pase.peticion());
	}

	// Pedir los parámetros haría a Tomcat leer un formulario o un multipart: antes de autenticar, también el de un anónimo.

	private static MockHttpServletRequest sinLeerParametros(String metodo, String ruta) {
		return new MockHttpServletRequest(metodo, ruta) {
			private static AssertionError cargaElCuerpo() {
				return new AssertionError("pedir los parámetros carga el cuerpo");
			}

			@Override
			public String getParameter(String nombre) {
				throw cargaElCuerpo();
			}

			@Override
			public Map<String, String[]> getParameterMap() {
				throw cargaElCuerpo();
			}

			@Override
			public Enumeration<String> getParameterNames() {
				throw cargaElCuerpo();
			}

			@Override
			public String[] getParameterValues(String nombre) {
				throw cargaElCuerpo();
			}
		};
	}

	@ParameterizedTest
	@CsvSource({ "POST, /videos, 415", "POST, /videos, 401", "POST, /documentacion, 403", "PUT, /temario, 401",
			"PATCH, /videos/a.mp4, 413", "PUT, /videos/orden, 411" })
	void unaEscrituraFallidaDeMaterial_noCargaElCuerpo(String metodo, String ruta, int estado) throws Exception {
		MockHttpServletRequest peticion = sinLeerParametros(metodo, BLOQUE + ruta);
		peticion.setContentType("application/x-www-form-urlencoded");

		pasar(peticion, new MockHttpServletResponse(), estado);

		verify(auditoria).registrarEvento(any(), eq(metodo), eq(BLOQUE + ruta), any(), any(), anyBoolean(), eq(estado), any(),
				any());
	}

	@ParameterizedTest
	@CsvSource({ "GET, /videos/a.mp4, 206", "GET, /temario, 200", "POST, /videos/a.mp4, 401", "POST, /temario, 405",
			"PUT, /documentacion/a.pdf, 401" })
	void unFicheroDeFamilia_noCargaElCuerpoConNingunMetodo(String metodo, String ruta, int estado) throws Exception {
		MockHttpServletRequest peticion = sinLeerParametros(metodo, FAMILIA + ruta);
		peticion.setContentType("multipart/form-data; boundary=x");

		pasar(peticion, new MockHttpServletResponse(), estado);

		verify(auditoria).registrarEvento(any(), eq(metodo), eq(FAMILIA + ruta), any(), any(), anyBoolean(), eq(estado), any(),
				any());
	}

	@SuppressWarnings("unchecked")
	private Map<String, String[]> parametrosGuardados(MockHttpServletRequest peticion, int estado) throws Exception {
		pasar(peticion, new MockHttpServletResponse(), estado);
		ArgumentCaptor<Map<String, String[]>> parametros = ArgumentCaptor.forClass(Map.class);
		verify(saneador).serializarQueryParams(parametros.capture());
		return parametros.getValue();
	}

	@Test
	void losParametrosDeUnFicheroDeFamilia_salenDeLaUrl() throws Exception {
		MockHttpServletRequest peticion = sinLeerParametros("GET", FAMILIA + "/temario");
		peticion.setQueryString("download=true");

		Map<String, String[]> parametros = parametrosGuardados(peticion, 200);

		assertEquals(1, parametros.size());
		assertArrayEquals(new String[] { "true" }, parametros.get("download"));
	}

	@Test
	void losParametrosSalenDeLaUrl_decodificados_repetidos_yElNombreSigueRedactado() throws Exception {
		MockHttpServletRequest peticion = sinLeerParametros("POST", BLOQUE + "/videos");
		peticion.setQueryString("nombre=Clase%20de%20Luc%C3%ADa.mp4&x=1&x=2&a+b=c%20d&solo&roto=%zz&&=sinClave");

		Map<String, String[]> parametros = parametrosGuardados(peticion, 415);

		assertArrayEquals(new String[] { AuditoriaRequestFilter.VALOR_REDACTADO }, parametros.get("nombre"));
		assertArrayEquals(new String[] { "1", "2" }, parametros.get("x"));
		assertArrayEquals(new String[] { "c d" }, parametros.get("a b"));
		assertArrayEquals(new String[] { "" }, parametros.get("solo"));
		assertArrayEquals(new String[] { "%zz" }, parametros.get("roto"), "como llegó, sin fallar");
	}

	@Test
	void sinParametrosEnLaUrl_ningunParametro() throws Exception {
		assertEquals(Map.of(), parametrosGuardados(sinLeerParametros("POST", BLOQUE + "/videos"), 415));
	}

	@Test
	void lasDemasRutas_siguenConLosParametrosDeSiempre() throws Exception {
		MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/alumnos");
		peticion.setParameter("nombre", "Lucía");

		assertArrayEquals(new String[] { "Lucía" }, parametrosGuardados(peticion, 200).get("nombre"));
	}

	@Test
	void reconoceSoloLoSuyo() {
		assertTrue(AuditoriaRequestFilter.esRutaDeMaterial(BLOQUE + "/videos"));
		assertTrue(AuditoriaRequestFilter.esRutaDeMaterial("/api/material-examen"));
		assertFalse(AuditoriaRequestFilter.esRutaDeMaterial("/api/material-examen-otra/x"));
		assertFalse(AuditoriaRequestFilter.esRutaDeMaterial("/api/alumnos/1/deportes/TAEKWONDO/material-examen/videos/a.mp4"));
		assertFalse(AuditoriaRequestFilter.esFicheroDeMaterial("POST", BLOQUE + "/videos/a.mp4"));
		assertFalse(AuditoriaRequestFilter.esFicheroDeMaterial("GET", BLOQUE));
		assertFalse(AuditoriaRequestFilter.esFicheroDeMaterial("GET", BLOQUE + "/videos"));
		assertFalse(AuditoriaRequestFilter.esFicheroDeMaterial("GET", BLOQUE + "/videos/a.mp4/contenido"));
		assertTrue(AuditoriaRequestFilter.esFicheroDeMaterial("GET", BLOQUE + "/videos/a.mp4"));
	}

	@Test
	void elNombreDeUnaSubidaFallida_noSeGuarda() {
		String ruta = BLOQUE + "/videos";

		Map<String, String[]> guardados = AuditoriaRequestFilter.parametrosAuditables(ruta,
				Map.of("nombre", new String[] { "Clase de Lucía.mp4" }));

		assertArrayEquals(new String[] { AuditoriaRequestFilter.VALOR_REDACTADO }, guardados.get("nombre"));
		assertEquals(1, guardados.size());
	}
}
