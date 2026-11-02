package com.taemoi.project.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.taemoi.project.services.AuditoriaPayloadSanitizer;
import com.taemoi.project.services.AuditoriaService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Foto de un alumno para el profesor: una pantalla de Mis grupos pide hasta 22. Ninguna petición copia el cuerpo y una
 * foto servida no deja fila; lo que no se sirvió (403, 404, 5xx) sí se apunta, sin leer el cuerpo.
 */
class AuditoriaRequestFilterFotoProfesorTest {

	private static final String FOTO = "/api/profesor/alumnos/7/imagen";

	private final AuditoriaService auditoria = mock(AuditoriaService.class);
	private final AuditoriaPayloadSanitizer saneador = mock(AuditoriaPayloadSanitizer.class);
	private final AuditoriaRequestFilter filtro = new AuditoriaRequestFilter(auditoria, saneador);

	private record Pase(ServletRequest peticion, ServletResponse respuesta) {
	}

	private Pase pasar(HttpServletRequest peticion, HttpServletResponse respuesta, int estado) throws Exception {
		AtomicReference<ServletRequest> peticionRecibida = new AtomicReference<>();
		AtomicReference<ServletResponse> respuestaRecibida = new AtomicReference<>();
		filtro.doFilter(peticion, respuesta, (req, res) -> {
			peticionRecibida.set(req);
			respuestaRecibida.set(res);
			((HttpServletResponse) res).setStatus(estado);
		});
		return new Pase(peticionRecibida.get(), respuestaRecibida.get());
	}

	@Test
	void reconoceSoloElGetDeLaFoto() {
		assertTrue(AuditoriaRequestFilter.esFotoDeProfesor("GET", FOTO));
		for (String metodo : List.of("POST", "PUT", "PATCH", "DELETE")) {
			assertFalse(AuditoriaRequestFilter.esFotoDeProfesor(metodo, FOTO), metodo);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/profesor/alumnos/7", "/api/profesor/alumnos/7/salud", "/api/profesor/alumnos/imagen",
			"/api/profesor/alumnos/7/imagen/x", "/api/alumnos/7/imagen", "/api/caja/profesor/alumnos/7/imagen",
			"/api/profesor/grupos/7/imagen" })
	void noReconoceNingunaOtraRuta(String ruta) {
		assertFalse(AuditoriaRequestFilter.esFotoDeProfesor("GET", ruta), ruta);
	}

	@ParameterizedTest
	@ValueSource(ints = { 200, 206 })
	void unaFotoServida_niSeCopiaNiDejaFila(int estado) throws Exception {
		MockHttpServletRequest peticion = new MockHttpServletRequest("GET", FOTO);
		MockHttpServletResponse respuesta = new MockHttpServletResponse();

		Pase pase = pasar(peticion, respuesta, estado);

		assertSame(peticion, pase.peticion());
		assertSame(respuesta, pase.respuesta(), "la foto no se copia en memoria");
		verify(auditoria, never()).registrarEvento(any(), any(), any(), any(), any(), anyBoolean(), anyInt(), any(), any());
	}

	/** Una forma rara de escribir la ruta no copia el cuerpo: Spring la lleva a la misma foto. */
	@ParameterizedTest
	@ValueSource(strings = { "/api/profesor/alumnos/7/%69magen", "/api/profesor/alumnos/7;x=1/imagen",
			"/api//profesor/alumnos/7/imagen" })
	void unaFormaRaraDeEscribirLaRuta_tampocoSeCopia(String ruta) throws Exception {
		MockHttpServletRequest peticion = new MockHttpServletRequest("GET", ruta);
		MockHttpServletResponse respuesta = new MockHttpServletResponse();

		Pase pase = pasar(peticion, respuesta, 200);

		assertSame(respuesta, pase.respuesta());
		verify(auditoria, never()).registrarEvento(any(), any(), any(), any(), any(), anyBoolean(), anyInt(), any(), any());
	}

	@ParameterizedTest
	@ValueSource(ints = { 401, 403, 404, 500 })
	void loQueNoSeSirvio_seApuntaSinCopiarNada(int estado) throws Exception {
		MockHttpServletRequest peticion = new MockHttpServletRequest("GET", FOTO);
		peticion.setQueryString("w=96");
		MockHttpServletResponse respuesta = new MockHttpServletResponse();
		when(saneador.serializarQueryParams(any())).thenAnswer(i -> i.getArgument(0, java.util.Map.class).keySet().toString());

		Pase pase = pasar(peticion, respuesta, estado);

		assertSame(peticion, pase.peticion());
		assertSame(respuesta, pase.respuesta());
		verify(saneador).sanitizarPayload(eq(""), any());
		verify(auditoria).registrarEvento(any(), eq("GET"), eq(FOTO), eq("[w]"), any(), anyBoolean(), eq(estado), any(), any());
	}

	private static Stream<Exception> fallos() {
		return Stream.of(new IOException("disco"), new ServletException("cadena"), new IllegalArgumentException("resto"));
	}

	/** La cadena lanza sin fijar estado, como una {@code IOException} sin handler. */
	private static FilterChain cadenaQueLanza(Exception fallo) {
		return (req, res) -> {
			if (fallo instanceof IOException e) {
				throw e;
			}
			if (fallo instanceof ServletException e) {
				throw e;
			}
			throw (RuntimeException) fallo;
		};
	}

	/**
	 * Al salir de la cadena el estado aún es 200: el 500 lo fija después el contenedor. Sin esta regla la omisión del 2xx se
	 * comería el fallo. El filtro no toca la respuesta ni se traga la excepción: la necesita el contenedor para responder.
	 */
	@ParameterizedTest
	@MethodSource("fallos")
	void siLaCadenaLanza_seApuntaComo500_yLaExcepcionSigue(Exception fallo) {
		MockHttpServletRequest peticion = new MockHttpServletRequest("GET", FOTO);
		peticion.setQueryString("w=96");
		MockHttpServletResponse respuesta = new MockHttpServletResponse();
		when(saneador.serializarQueryParams(any())).thenAnswer(i -> i.getArgument(0, java.util.Map.class).keySet().toString());

		Exception lanzada = assertThrows(Exception.class, () -> filtro.doFilter(peticion, respuesta, cadenaQueLanza(fallo)));

		assertSame(fallo, lanzada);
		assertEquals(200, respuesta.getStatus(), "el 500 no lo pone el filtro");
		verify(saneador).sanitizarPayload(eq(""), any());
		verify(auditoria).registrarEvento(any(), eq("GET"), eq(FOTO), eq("[w]"), any(), anyBoolean(), eq(500), any(), any());
	}

	@Test
	void siLaCadenaLanzaConUnErrorYaFijado_seApuntaEseEstado() {
		MockHttpServletRequest peticion = new MockHttpServletRequest("GET", FOTO);
		FilterChain cadena = (req, res) -> {
			((HttpServletResponse) res).setStatus(404);
			throw new IOException("disco");
		};

		assertThrows(IOException.class, () -> filtro.doFilter(peticion, new MockHttpServletResponse(), cadena));

		verify(auditoria).registrarEvento(any(), eq("GET"), eq(FOTO), any(), any(), anyBoolean(), eq(404), any(), any());
	}

	@Test
	void siLaAuditoriaFalla_laExcepcionDeLaCadenaNoSePierde() {
		when(saneador.serializarQueryParams(any())).thenThrow(new IllegalStateException("auditoria"));
		IOException fallo = new IOException("disco");

		IOException lanzada = assertThrows(IOException.class, () -> filtro.doFilter(new MockHttpServletRequest("GET", FOTO),
				new MockHttpServletResponse(), cadenaQueLanza(fallo)));

		assertSame(fallo, lanzada);
	}

	/** Solo cambia la foto: con una excepción, cualquier otra ruta se apunta (o se omite) como antes, con el estado 200. */
	@ParameterizedTest
	@ValueSource(strings = { "/api/profesor/alumnos/7", "/api/alumnos/7/imagen", "/api/profesor/grupos/7/alumnos" })
	void lasRutasVecinas_siLaCadenaLanza_siguenApuntandoSuEstado(String ruta) {
		assertThrows(IOException.class, () -> filtro.doFilter(new MockHttpServletRequest("GET", ruta),
				new MockHttpServletResponse(), cadenaQueLanza(new IOException("disco"))));

		verify(auditoria).registrarEvento(any(), eq("GET"), eq(ruta), any(), any(), anyBoolean(), eq(200), any(), any());
	}

	@Test
	void laSaludYUnaEscrituraConRegistroPropio_siLaCadenaLanza_siguenSinFila() {
		assertThrows(IOException.class, () -> filtro.doFilter(new MockHttpServletRequest("GET", "/api/profesor/alumnos/7/salud"),
				new MockHttpServletResponse(), cadenaQueLanza(new IOException("disco"))));
		assertThrows(IOException.class,
				() -> filtro.doFilter(new MockHttpServletRequest("PUT", "/api/profesor/alumnos/7/deportes/TAEKWONDO/apto"),
						new MockHttpServletResponse(), cadenaQueLanza(new IOException("disco"))));

		verify(auditoria, never()).registrarEvento(any(), any(), any(), any(), any(), anyBoolean(), anyInt(), any(), any());
	}

	/** La ficha, la salud y la ruta antigua siguen como estaban: respuesta envuelta y una fila. */
	@ParameterizedTest
	@ValueSource(strings = { "/api/profesor/alumnos/7", "/api/alumnos/7/imagen" })
	void lasRutasVecinasSiguenEnvueltasYApuntadas(String ruta) throws Exception {
		Pase pase = pasar(new MockHttpServletRequest("GET", ruta), new MockHttpServletResponse(), 200);

		assertInstanceOf(ContentCachingResponseWrapper.class, pase.respuesta());
		verify(auditoria).registrarEvento(any(), eq("GET"), eq(ruta), any(), any(), anyBoolean(), eq(200), any(), any());
	}
}
