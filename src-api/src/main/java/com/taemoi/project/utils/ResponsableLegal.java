package com.taemoi.project.utils;

import java.util.Comparator;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Tutor;

/**
 * Lo que la API sigue exponiendo como {@code responsableLegalNombre}/{@code responsableLegalNif}:
 * el primer tutor responsable legal del alumno (por {@code orden}, {@code id}). Si solo se conoce su
 * NIF (tutor sin nombre), el nombre sale {@code null}; si no tiene ninguno, ambos son {@code null}.
 *
 * <p>Lee {@code alumno.getTutores()}: hay que llamarlo con la sesión abierta (petición web o
 * transacción), como el resto de los mapeos de la ficha.
 */
public record ResponsableLegal(String nombre, String nif) {

	/** Longitud del antiguo campo único {@code responsable_legal_nombre}; la API no devuelve más. */
	public static final int MAX_NOMBRE = 180;

	/** Orden de los tutores: {@code orden} y, a igualdad, {@code id} (los nuevos sin id, al final). */
	public static final Comparator<Tutor> ORDEN = Comparator
			.comparing((Tutor t) -> t.getOrden() == null ? 0 : t.getOrden())
			.thenComparing(t -> t.getId() == null ? Long.MAX_VALUE : t.getId());

	public static ResponsableLegal de(Alumno alumno) {
		if (alumno == null || alumno.getTutores() == null) {
			return new ResponsableLegal(null, null);
		}
		return alumno.getTutores().stream()
				.filter(Tutor::isResponsableLegal)
				.min(ORDEN)
				.map(t -> new ResponsableLegal(truncar(t.getNombreCompleto(), MAX_NOMBRE), t.getNif()))
				.orElse(new ResponsableLegal(null, null));
	}

	public static String truncar(String valor, int maximo) {
		return valor == null || valor.length() <= maximo ? valor : valor.substring(0, maximo);
	}
}
