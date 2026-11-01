package com.taemoi.project.entities;

/**
 * Tipo de discapacidad de un alumno. Es dato de salud: se guarda cifrado en {@code alumno_salud}, por nombre y en este
 * orden, así que renombrar o quitar un valor exige migrar lo guardado. La lista completa debe caber en
 * {@code AlumnoSaludService.LONGITUD_TIPOS_EN_CLARO}: se rellena con espacios hasta esa longitud antes de cifrar, para que
 * la longitud del cifrado no delate qué tipos hay.
 */
public enum TipoDiscapacidad {
	FISICA,
	INTELECTUAL,
	VISUAL,
	AUDITIVA,
	PSICOSOCIAL,
	OTRA
}
