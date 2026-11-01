package com.taemoi.project.controllers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;

import com.taemoi.project.exceptions.MaterialExamenException;

/** Lo que Tomcat no deja provocar: un flujo que trae más de lo declarado. El resto, en MaterialExamenTomcatIntegrationTest. */
class JsonMaterialConTopeTest {

	private final JsonMaterialConTope tope = new JsonMaterialConTope();

	private static HttpInputMessage mensaje(long longitud, InputStream cuerpo) {
		HttpHeaders cabeceras = new HttpHeaders();
		if (longitud >= 0) {
			cabeceras.setContentLength(longitud);
		}
		return new HttpInputMessage() {
			@Override
			public InputStream getBody() {
				return cuerpo;
			}

			@Override
			public HttpHeaders getHeaders() {
				return cabeceras;
			}
		};
	}

	private static InputStream prohibido() {
		return new InputStream() {
			@Override
			public int read() {
				throw new AssertionError("no se lee el cuerpo");
			}
		};
	}

	private HttpStatus estadoDe(HttpInputMessage mensaje) {
		return assertThrows(MaterialExamenException.class, () -> tope.beforeBodyRead(mensaje, null, null, null)).getEstado();
	}

	@Test
	void sinContentLength_411_sinLeer() {
		assertEquals(HttpStatus.LENGTH_REQUIRED, estadoDe(mensaje(-1, prohibido())));
	}

	@Test
	void masDelTopeDeclarado_413_sinLeer() {
		assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, estadoDe(mensaje(JsonMaterialConTope.MAX_BYTES + 1L, prohibido())));
	}

	@Test
	void masDelTopeContadoAlLeer_413() {
		byte[] largo = new byte[JsonMaterialConTope.MAX_BYTES + 1];

		assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, estadoDe(mensaje(10, new ByteArrayInputStream(largo))));
	}

	@Test
	void hastaElTope_elCuerpoLlegaEntero() throws IOException {
		byte[] justo = new byte[JsonMaterialConTope.MAX_BYTES];
		justo[justo.length - 1] = 7;
		HttpInputMessage entrada = mensaje(justo.length, new ByteArrayInputStream(justo));

		HttpInputMessage salida = tope.beforeBodyRead(entrada, null, null, null);

		assertArrayEquals(justo, salida.getBody().readAllBytes());
		assertEquals(entrada.getHeaders(), salida.getHeaders());
	}

	/** Las subidas de ficheros leen el flujo por su cuenta: con {@code @RequestBody} acabarían en memoria. */
	@Test
	void soloElTituloYElOrdenPasanPorRequestBody() {
		Set<String> conCuerpo = new TreeSet<>();
		for (Method metodo : MaterialExamenController.class.getDeclaredMethods()) {
			if (Arrays.stream(metodo.getParameterAnnotations()).flatMap(Arrays::stream)
					.map(Annotation::annotationType).anyMatch(RequestBody.class::equals)) {
				conCuerpo.add(metodo.getName());
			}
		}

		assertEquals(Set.of("cambiarTitulo", "reordenarVideos"), conCuerpo);
	}
}
