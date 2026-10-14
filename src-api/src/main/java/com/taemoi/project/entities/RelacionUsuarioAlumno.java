package com.taemoi.project.entities;

/**
 * Relación de una cuenta con la ficha que ve ({@code usuario_alumno.relacion}, V31): el propio alumno o su tutor.
 * Informativa en esta fase; V31 la deduce por edad (18 años, decisión D7) y se corrige a mano.
 */
public enum RelacionUsuarioAlumno {
	PROPIO,
	TUTOR
}
