package com.taemoi.project.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * Funciones SQL propias para HQL y Criteria (se registra en
 * {@code META-INF/services/org.hibernate.boot.model.FunctionContributor}).
 *
 * <p>{@code utf8mb4_bin(x)}: el texto convertido a utf8mb4 y con colación binaria (R2-P6, filtro por deporte del
 * listado de alumnos). Se convierte antes de aplicar la colación para no depender del juego de caracteres de la
 * columna: un {@code COLLATE utf8mb4_bin} directo sobre una columna utf8mb3 o latin1 de un esquema antiguo es el
 * ERROR 1253 de MySQL. Sobre una columna utf8mb4 la conversión no cambia nada.
 *
 * <p>{@code minusculas_utf8mb4_bin(x)}: igual, pasado a minúsculas después de convertir (con la colación por
 * defecto de utf8mb4, que sí distingue mayúsculas de minúsculas para {@code LOWER}; una columna con colación binaria
 * antigua no las cambiaría) y antes de la colación binaria.
 */
public class FuncionesSqlContributor implements FunctionContributor {

	public static final String UTF8MB4_BIN = "utf8mb4_bin";
	public static final String MINUSCULAS_UTF8MB4_BIN = "minusculas_utf8mb4_bin";
	/** SQL de cada función ({@code ?1} = el argumento). */
	public static final String PATRON_UTF8MB4_BIN = "(convert(?1 using utf8mb4) collate utf8mb4_bin)";
	public static final String PATRON_MINUSCULAS_UTF8MB4_BIN = "(lower(convert(?1 using utf8mb4)) collate utf8mb4_bin)";

	@Override
	public void contributeFunctions(FunctionContributions contribuciones) {
		var texto = contribuciones.getTypeConfiguration().getBasicTypeRegistry().resolve(StandardBasicTypes.STRING);
		contribuciones.getFunctionRegistry().registerPattern(UTF8MB4_BIN, PATRON_UTF8MB4_BIN, texto);
		contribuciones.getFunctionRegistry().registerPattern(MINUSCULAS_UTF8MB4_BIN, PATRON_MINUSCULAS_UTF8MB4_BIN, texto);
	}
}
