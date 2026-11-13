package com.taemoi.project.plataforma.importacion;

import java.util.Map;

/**
 * Una fila de datos de la hoja «Alumnos».
 *
 * @param fila    número de fila de Excel (la cabecera es la 1)
 * @param valores valor de cada columna conocida presente en la hoja
 */
record FilaHoja(int fila, Map<String, Valor> valores) {

	FilaHoja {
		valores = Map.copyOf(valores);
	}

	/** El valor de la columna; vacío si la columna no está en la hoja. */
	Valor valor(String columna) {
		return valores.getOrDefault(columna, Valor.VACIO);
	}
}
