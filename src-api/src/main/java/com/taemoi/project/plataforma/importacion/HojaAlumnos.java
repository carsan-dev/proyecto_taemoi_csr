package com.taemoi.project.plataforma.importacion;

import java.util.List;
import java.util.Map;

/**
 * La hoja maestra leída: el fichero tal cual (para devolverlo con la columna {@code revisar}), sus columnas conocidas
 * (nombre → índice de columna, desde 0) y sus filas de datos no vacías. Solo vive en memoria durante la petición.
 */
record HojaAlumnos(byte[] contenido, String nombreOriginal, Map<String, Integer> columnas, List<FilaHoja> filas) {

	HojaAlumnos {
		columnas = Map.copyOf(columnas);
		filas = List.copyOf(filas);
	}
}
