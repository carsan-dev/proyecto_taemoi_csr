package com.taemoi.project.acceso;

/**
 * Operación sobre un recurso que decide {@link AccesoRecursos} (entrega 5c, entrega5-plan.md §4.2). En SpEL se
 * escribe como texto ({@code 'LEER'}); un nombre desconocido deniega. La entrega 7 puede añadir operaciones
 * (grado, salud, asistencia) si hace falta (D5).
 */
public enum OperacionAcceso {
	LEER, EDITAR
}
