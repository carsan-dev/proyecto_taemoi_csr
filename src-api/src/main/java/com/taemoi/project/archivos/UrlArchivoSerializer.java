package com.taemoi.project.archivos;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.taemoi.project.organizacion.HostDeLaPeticion;

/**
 * Serializa {@code Imagen.url} / {@code Documento.url} como URL ABSOLUTA al leer: en base de datos se guarda
 * sin host ({@code /imagenes/…}, {@code /documentos/…}) y aquí se antepone {@code app.base.url}. Las URL ya
 * absolutas (filas heredadas, {@code /api/…} con host) se dejan tal cual.
 *
 * <p>Fase 4 (pieza 3): en el Host de otra escuela de {@code organizacion_dominio} se antepone el origen de ese Host
 * ({@link HostDeLaPeticion#baseArchivosPeticion}); en moiskimdo.es, un Host fuera de la tabla o sin petición,
 * {@code app.base.url} como siempre.
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
		generador.writeString(absoluta(valor, HostDeLaPeticion.baseArchivosPeticion(baseUrl)));
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
