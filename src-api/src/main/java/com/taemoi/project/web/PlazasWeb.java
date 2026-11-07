package com.taemoi.project.web;

import java.util.Collection;
import java.util.Comparator;

/**
 * Plazas de un turno o de un grupo en la web por plantilla (fase4-web-plan.md §4.3, decisión d482). Solo el estado y,
 * con plazas, cuántas quedan: ni aforo ni ocupación.
 *
 * <ul>
 * <li>{@code libres >= 1} → {@link Estado#QUEDAN} con {@code quedan = libres} («Quedan N»; sin «Últimas N»).</li>
 * <li>{@code libres == 0} y nadie en lista de espera → {@link Estado#LLENO}.</li>
 * <li>{@code libres == 0} y alguien en lista de espera → {@link Estado#LISTA_ESPERA}.</li>
 * </ul>
 * Las libres son {@code aforoEfectivo − ocupacionEfectiva} (alumnos que cuentan + solicitudes pendientes de la
 * temporada), como en la preinscripción ({@code AforoPreinscripcionService.Ocupacion#plazas}).
 *
 * @param estado estado de las plazas.
 * @param quedan plazas libres con {@link Estado#QUEDAN}; {@code null} en los demás estados.
 */
public record PlazasWeb(Estado estado, Integer quedan) {

	public enum Estado {
		/** «Quedan N». */
		QUEDAN,
		/** «Lleno»: sin plazas y nadie esperando. */
		LLENO,
		/** «Lista de espera»: sin plazas y alguien esperando. */
		LISTA_ESPERA
	}

	/** De peor a mejor: lista de espera, lleno y, con plazas, las que menos tienen. */
	private static final Comparator<PlazasWeb> DE_PEOR_A_MEJOR = Comparator
			.comparingInt((PlazasWeb p) -> switch (p.estado()) {
				case LISTA_ESPERA -> 0;
				case LLENO -> 1;
				case QUEDAN -> 2;
			})
			.thenComparingInt(p -> p.quedan() == null ? 0 : p.quedan());

	/**
	 * @param libres   plazas libres del turno (0 si está lleno o por encima del aforo).
	 * @param enEspera solicitudes de la temporada en lista de espera que piden el turno.
	 */
	public static PlazasWeb deTurno(int libres, int enEspera) {
		if (libres < 0 || enEspera < 0) {
			throw new IllegalArgumentException("Las plazas libres y la lista de espera no pueden ser negativas.");
		}
		if (libres >= 1) {
			return new PlazasWeb(Estado.QUEDAN, libres);
		}
		return new PlazasWeb(enEspera > 0 ? Estado.LISTA_ESPERA : Estado.LLENO, null);
	}

	/** El peor de los turnos de un grupo; {@code null} si no tiene turnos. */
	public static PlazasWeb peor(Collection<PlazasWeb> turnos) {
		return turnos.stream().min(DE_PEOR_A_MEJOR).orElse(null);
	}
}
