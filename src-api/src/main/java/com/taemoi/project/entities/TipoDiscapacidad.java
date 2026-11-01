package com.taemoi.project.entities;

/**
 * Tipo de discapacidad de un alumno. Es dato de salud: se guarda cifrado en {@code alumno_salud}, por nombre y en este
 * orden, así que renombrar o quitar un valor exige migrar lo guardado.
 */
public enum TipoDiscapacidad {
	FISICA,
	INTELECTUAL,
	VISUAL,
	AUDITIVA,
	PSICOSOCIAL,
	OTRA
}
