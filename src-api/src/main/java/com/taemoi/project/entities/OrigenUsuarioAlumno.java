package com.taemoi.project.entities;

/** De dónde sale un vínculo cuenta-alumno ({@code usuario_alumno.origen}, V31). */
public enum OrigenUsuarioAlumno {
	/** Relleno de V31 desde usuario.alumno_id y las coincidencias de email. */
	MIGRACION,
	/** Registro de la cuenta (formulario o Google). */
	REGISTRO,
	/** Alta o corrección manual desde la gestión. */
	MANUAL
}
