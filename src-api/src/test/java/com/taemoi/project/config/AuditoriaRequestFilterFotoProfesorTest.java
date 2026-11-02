package com.taemoi.project.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.taemoi.project.services.AuditoriaPayloadSanitizer;
import com.taemoi.project.services.AuditoriaService;

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

	/** La ficha, la salud y la ruta antigua siguen como estaban: respuesta envuelta y una fila. */
	@ParameterizedTest
	@ValueSource(strings = { "/api/profesor/alumnos/7", "/api/alumnos/7/imagen" })
	void lasRutasVecinasSiguenEnvueltasYApuntadas(String ruta) throws Exception {
		Pase pase = pasar(new MockHttpServletRequest("GET", ruta), new MockHttpServletResponse(), 200);

		assertInstanceOf(ContentCachingResponseWrapper.class, pase.respuesta());
		verify(auditoria).registrarEvento(any(), eq("GET"), eq(ruta), any(), any(), anyBoolean(), eq(200), any(), any());
	}
}
