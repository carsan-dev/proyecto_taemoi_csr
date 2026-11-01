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

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

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

	private final AuditoriaService auditoria = mock(AuditoriaService.class);
	private final AuditoriaRequestFilter filtro = new AuditoriaRequestFilter(auditoria,
			mock(AuditoriaPayloadSanitizer.class));

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
