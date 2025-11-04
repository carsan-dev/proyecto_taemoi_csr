package com.taemoi.project.entities;

/**
 * Enum que representa el rol familiar de un alumno en tarifas tipo PADRES_HIJOS.
 *
 * - PADRE: El alumno es el tutor/padre en la relación familiar (paga 28€)
 * - HIJO: El alumno es el hijo en la relación familiar (paga 26€)
 * - NINGUNO: El alumno no tiene un rol familiar específico (tarifa estándar)
 */
public enum RolFamiliar {
	PADRE,
	HIJO,
	NINGUNO
}
