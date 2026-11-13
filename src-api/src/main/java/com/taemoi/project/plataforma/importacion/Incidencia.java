package com.taemoi.project.plataforma.importacion;

/**
 * Un error o un aviso de una fila de la hoja. NUNCA lleva datos personales: solo la fila, el número de federación (si
 * se pudo leer como número), el nombre de la columna y un motivo cuyos únicos valores citados son de catálogo (sede,
 * grupo, grado, tarifa).
 *
 * @param fila             número de fila de Excel (la cabecera es la 1)
 * @param numeroFederacion el de la fila, o {@code null} si falta o no es un número
 * @param columna          la columna afectada, o {@code null} si es de toda la fila
 * @param motivo           qué pasa y qué hacer, en una frase corta
 */
public record Incidencia(int fila, Integer numeroFederacion, String columna, String motivo) {
}
