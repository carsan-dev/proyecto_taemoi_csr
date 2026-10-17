package com.taemoi.project.utils;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.Function;

/**
 * Orden alfabético de las listas que el servidor devuelve sin orden propio (R2-P1, decisión D2): los alumnos de un
 * grupo y los grupos de un alumno salen de un {@code Set} de la entidad, cuyo orden cambiaba de una carga a otra.
 *
 * <p>Elección: {@link Collator} de {@code es-ES} con fuerza {@link Collator#PRIMARY}. Así «Álvaro», «alvaro» y
 * «ÁLVARO» empatan (no distingue mayúsculas ni tildes), la «Ñ» va entre la «N» y la «O» como en un listado en
 * español, y no depende de la configuración regional de la máquina (la CI va en otra). Los textos nulos van al
 * final y el empate se resuelve por id (nulos al final): el orden es total y estable entre peticiones. Se ordena al
 * construir el DTO, no en la entidad ({@code @OrderBy} ordenaría por la columna en bruto de MySQL y en todas las
 * cargas).
 */
public final class OrdenAlfabetico {

	/** {@link Collator} no es seguro entre hilos: uno por hilo. */
	private static final ThreadLocal<Collator> COLLATOR = ThreadLocal.withInitial(OrdenAlfabetico::crearCollator);

	/** Texto en orden alfabético español, sin distinguir mayúsculas ni tildes; nulos al final. */
	public static final Comparator<String> TEXTO = Comparator.nullsLast(OrdenAlfabetico::comparar);

	private static final Comparator<Long> ID = Comparator.nullsLast(Comparator.naturalOrder());

	private OrdenAlfabetico() {
	}

	/** Alumnos: por nombre, luego apellidos, luego id. */
	public static <T> Comparator<T> porNombreYApellidos(Function<? super T, String> nombre,
			Function<? super T, String> apellidos, Function<? super T, Long> id) {
		return Comparator.<T, String>comparing(nombre, TEXTO).thenComparing(apellidos, TEXTO).thenComparing(id, ID);
	}

	/** Grupos (o cualquier cosa con nombre): por nombre, luego id. */
	public static <T> Comparator<T> porNombre(Function<? super T, String> nombre, Function<? super T, Long> id) {
		return Comparator.<T, String>comparing(nombre, TEXTO).thenComparing(id, ID);
	}

	private static Collator crearCollator() {
		Collator collator = Collator.getInstance(Locale.forLanguageTag("es-ES"));
		collator.setStrength(Collator.PRIMARY);
		return collator;
	}

	private static int comparar(String a, String b) {
		return COLLATOR.get().compare(a, b);
	}
}
