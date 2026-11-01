package com.taemoi.project.controllers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import com.taemoi.project.exceptions.MaterialExamenException;

/**
 * Tope de los {@code @RequestBody} de {@link MaterialExamenController}. La {@code location} de nginx de estas rutas admite
 * 520 MB y aquí no pasa el envoltorio de auditoría, pero Jackson leería el cuerpo entero en memoria. Antes de leer exige
 * {@code Content-Length} (411) y un máximo de {@value #MAX_BYTES} bytes (413), y cuenta también al leer: el cuerpo llega a
 * Jackson ya leído, de modo que el 413 no se convierte en el 400 de un JSON roto.
 */
@ControllerAdvice(assignableTypes = MaterialExamenController.class)
class JsonMaterialConTope extends RequestBodyAdviceAdapter {

	static final int MAX_BYTES = 64 * 1024;

	@Override
	public boolean supports(MethodParameter parametro, Type tipo, Class<? extends HttpMessageConverter<?>> conversor) {
		return true;
	}

	@Override
	public HttpInputMessage beforeBodyRead(HttpInputMessage entrada, MethodParameter parametro, Type tipo,
			Class<? extends HttpMessageConverter<?>> conversor) throws IOException {
		HttpHeaders cabeceras = entrada.getHeaders();
		long longitud = cabeceras.getContentLength();
		if (longitud < 0) {
			throw new MaterialExamenException(HttpStatus.LENGTH_REQUIRED,
					"No se ha podido conocer el tamaño de la petición. Vuelve a intentarlo.");
		}
		if (longitud > MAX_BYTES) {
			throw demasiadoGrande();
		}
		byte[] cuerpo = entrada.getBody().readNBytes(MAX_BYTES + 1);
		if (cuerpo.length > MAX_BYTES) {
			throw demasiadoGrande();
		}
		return new HttpInputMessage() {
			@Override
			public InputStream getBody() {
				return new ByteArrayInputStream(cuerpo);
			}

			@Override
			public HttpHeaders getHeaders() {
				return cabeceras;
			}
		};
	}

	private static MaterialExamenException demasiadoGrande() {
		return new MaterialExamenException(HttpStatus.PAYLOAD_TOO_LARGE, "La petición es demasiado grande.");
	}
}
