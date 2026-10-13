package com.taemoi.project.archivos;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * Serializa {@code Imagen.url} / {@code Documento.url} como URL ABSOLUTA al leer: en base de datos se guarda
 * sin host ({@code /imagenes/…}, {@code /documentos/…}) y aquí se antepone {@code app.base.url}. Las URL ya
 * absolutas (filas heredadas, {@code /api/…} con host) se dejan tal cual.
 *
 * <p>Spring crea la instancia ({@code SpringHandlerInstantiator}) e inyecta {@code app.base.url}; con un
 * {@code ObjectMapper} sin Spring la URL sale sin cambios.
 */
public class UrlArchivoSerializer extends StdSerializer<String> {

	private static final long serialVersionUID = 1L;

	@Value("${app.base.url:}")
	private transient String baseUrl;

	public UrlArchivoSerializer() {
		super(String.class);
	}

	public UrlArchivoSerializer(String baseUrl) {
		this();
		this.baseUrl = baseUrl;
	}

	@Override
	public void serialize(String valor, JsonGenerator generador, SerializerProvider proveedor) throws IOException {
		generador.writeString(absoluta(valor, baseUrl));
	}

	/** {@code base + valor} si {@code valor} es una ruta sin host ({@code /…}, no {@code //…}). */
	public static String absoluta(String valor, String base) {
		if (valor == null || base == null || base.isBlank() || !valor.startsWith("/") || valor.startsWith("//")) {
			return valor;
		}
		String sinBarra = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
		return sinBarra + valor;
	}
}
