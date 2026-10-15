package com.taemoi.project.integracion;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.core.io.ClassPathResource;

/**
 * Clasificación de los productos creados por los tests con los nombres de Taemoi, como la dejaba el resync de
 * V24 ({@code db/resync/resync_catalogo_v24.sql}) en producción tras V27. Desde V28 el resync completo ya no se
 * puede ejecutar (sus dos primeras sentencias leen {@code alumno_deporte.tipo_tarifa}, que V28 borra): aquí se
 * ejecutan solo sus sentencias de productos y grados (las que no nombran {@code tipo_tarifa}), con el mismo texto.
 */
final class ResyncProductosDePrueba {

	private ResyncProductosDePrueba() {
	}

	static void clasificarProductos(DataSource dataSource) throws Exception {
		String script = new String(new ClassPathResource("db/resync/resync_catalogo_v24.sql").getInputStream()
				.readAllBytes(), StandardCharsets.UTF_8);
		String sinComentarios = script.replaceAll("(?m)^\\s*--.*$", "");
		try (Connection conexion = dataSource.getConnection(); Statement sentencia = conexion.createStatement()) {
			for (String trozo : sinComentarios.split(";\\s*(\\r?\\n|$)")) {
				String sql = trozo.trim();
				if (sql.isEmpty() || sql.contains("tipo_tarifa")) {
					continue;
				}
				sentencia.execute(sql);
			}
		}
	}
}
