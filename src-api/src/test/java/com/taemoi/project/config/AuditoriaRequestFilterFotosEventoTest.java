package com.taemoi.project.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.taemoi.project.entities.AuditoriaAccion;
import com.taemoi.project.services.AuditoriaPayloadSanitizer;
import com.taemoi.project.services.AuditoriaService;

import jakarta.servlet.ServletResponse;

/**
 * Decisión 342: las imágenes de la galería quedan fuera de la auditoría (hasta 40 por visita). Solo GET y HEAD de esa ruta
 * exacta: cualquier otra forma se sigue auditando, porque ante la duda el filtro apunta.
 */
class AuditoriaRequestFilterFotosEventoTest {

	private final AuditoriaService auditoria = mock(AuditoriaService.class);
	private final AuditoriaRequestFilter filtro = new AuditoriaRequestFilter(auditoria,
			mock(AuditoriaPayloadSanitizer.class));

	@Test
	void reconoceSoloGetYHeadDeLaImagen() {
		for (String metodo : List.of("GET", "HEAD")) {
			assertTrue(AuditoriaRequestFilter.esImagenDeFotoEvento(metodo, "/api/eventos/7/fotos/12"), metodo);
		}
		for (String metodo : List.of("POST", "PUT", "PATCH", "DELETE")) {
			assertFalse(AuditoriaRequestFilter.esImagenDeFotoEvento(metodo, "/api/eventos/7/fotos/12"), metodo);
		}
	}

	@Test
	void noReconoceNingunaOtraRuta() {
		for (String ruta : List.of("/api/eventos/7/fotos", "/api/eventos/7/fotos/", "/api/eventos/7/fotos/orden",
				"/api/eventos/7/fotos/12/", "/api/eventos/7/fotos/12/x", "/api/eventos/x/fotos/12",
				"/api/eventos/7/imagen", "/api/eventos/7", "/api/eventos/7/fotos/1%32", "/api/eventos/7/fotos/12;x=1",
				"/api/alumnos/7/fotos/12")) {
			assertFalse(AuditoriaRequestFilter.esImagenDeFotoEvento("GET", ruta), ruta);
		}
		assertFalse(AuditoriaRequestFilter.esImagenDeFotoEvento("GET", null));
	}

	@Test
	void laImagenPasaSinEnvolverYSinRegistrar() throws Exception {
		for (String metodo : List.of("GET", "HEAD")) {
			MockHttpServletRequest peticion = new MockHttpServletRequest(metodo, "/api/eventos/7/fotos/12");
			MockHttpServletResponse respuesta = new MockHttpServletResponse();
			AtomicReference<ServletResponse> recibida = new AtomicReference<>();

			filtro.doFilter(peticion, respuesta, (req, res) -> recibida.set(res));

			assertSame(respuesta, recibida.get(), metodo + ": la imagen no se copia en memoria");
		}
		verify(auditoria, never()).registrarEvento(any(), any(), any(), any(), any(), anyBoolean(), anyInt(), any(), any());
	}

	@Test
	void laListaSigueEnvueltaYRegistrada() throws Exception {
		MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/eventos/7/fotos");
		AtomicReference<ServletResponse> recibida = new AtomicReference<>();

		filtro.doFilter(peticion, new MockHttpServletResponse(), (req, res) -> recibida.set(res));

		assertInstanceOf(ContentCachingResponseWrapper.class, recibida.get());
		verify(auditoria).registrarEvento(any(AuditoriaAccion.class), anyString(), anyString(), any(), any(), anyBoolean(),
				anyInt(), any(), any());
	}
}
