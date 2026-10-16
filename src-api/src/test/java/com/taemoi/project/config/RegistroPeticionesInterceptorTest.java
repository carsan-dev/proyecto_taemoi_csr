package com.taemoi.project.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * La traza de fin de petición no debe dar por buena (200) una petición que acaba en una excepción sin resolver:
 * en ese momento el despacho de error aún no ha fijado el estado final.
 */
@ExtendWith(OutputCaptureExtension.class)
class RegistroPeticionesInterceptorTest {

	private final WebConfig.RegistroPeticionesInterceptor interceptor = new WebConfig.RegistroPeticionesInterceptor();

	@Test
	void conExcepcionSinResolverNoRegistraElEstadoInicialComoCompletada(CapturedOutput salida) {
		MockHttpServletRequest peticion = new MockHttpServletRequest("DELETE", "/api/alumnos/7");
		MockHttpServletResponse respuesta = new MockHttpServletResponse();

		interceptor.afterCompletion(peticion, respuesta, new Object(), new IllegalStateException("fallo de prueba"));

		assertThat(salida.getAll()).doesNotContain("REQUEST COMPLETED: /api/alumnos/7")
				.contains("REQUEST FAILED: /api/alumnos/7")
				.contains("fallo de prueba");
	}

	@Test
	void sinExcepcionRegistraElEstadoReal(CapturedOutput salida) {
		MockHttpServletRequest peticion = new MockHttpServletRequest("DELETE", "/api/alumnos/8");
		MockHttpServletResponse respuesta = new MockHttpServletResponse();
		respuesta.setStatus(404);

		interceptor.afterCompletion(peticion, respuesta, new Object(), null);

		assertThat(salida.getAll()).contains("REQUEST COMPLETED: /api/alumnos/8 - Status: 404");
	}
}
