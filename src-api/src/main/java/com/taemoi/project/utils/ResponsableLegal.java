package com.taemoi.project.utils;

import java.util.Comparator;

import org.hibernate.LazyInitializationException;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Tutor;

/**
 * Lo que la API sigue exponiendo como {@code responsableLegalNombre}/{@code responsableLegalNif}:
 * el primer tutor responsable legal del alumno (por {@code orden}, {@code id}).
 *
 * <p>Si el alumno no tiene ninguno (o sus tutores no se pueden cargar fuera de sesión), se usan las
 * columnas heredadas {@code alumno.responsable_legal_*}, que {@code TutorService} mantiene iguales a
 * ese tutor y que también guardan el caso «solo NIF» (sin nombre no hay tutor).
 */
public record ResponsableLegal(String nombre, String nif) {

	/** Orden de los tutores: {@code orden} y, a igualdad, {@code id} (los nuevos sin id, al final). */
	public static final Comparator<Tutor> ORDEN = Comparator
			.comparing((Tutor t) -> t.getOrden() == null ? 0 : t.getOrden())
			.thenComparing(t -> t.getId() == null ? Long.MAX_VALUE : t.getId());

	public static ResponsableLegal de(Alumno alumno) {
		if (alumno == null) {
			return new ResponsableLegal(null, null);
		}
		try {
			if (alumno.getTutores() != null) {
				Tutor principal = alumno.getTutores().stream()
						.filter(Tutor::isResponsableLegal)
						.min(ORDEN)
						.orElse(null);
				if (principal != null) {
					return new ResponsableLegal(truncar(principal.getNombreCompleto(), 180), principal.getNif());
				}
			}
		} catch (LazyInitializationException e) {
			// Fuera de sesión: las columnas heredadas tienen el mismo valor.
		}
		return new ResponsableLegal(alumno.getResponsableLegalNombre(), alumno.getResponsableLegalNif());
	}

	public static String truncar(String valor, int maximo) {
		return valor == null || valor.length() <= maximo ? valor : valor.substring(0, maximo);
	}
}
