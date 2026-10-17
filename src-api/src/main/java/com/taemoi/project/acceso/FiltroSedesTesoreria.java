package com.taemoi.project.acceso;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Sedes que cuentan en la tesorería de la petición (entrega 6-3, D6-12), por la sede del cargo
 * ({@code producto_alumno.sede_id}, D6-9). Lo calcula {@link GestionPorSede#filtroTesoreria}.
 *
 * <ul>
 * <li>{@link #TODAS}: gestión sin filtro de sede. Las consultas añaden {@code (:todasLasSedes = true OR ...)}, que
 * es siempre verdadero: devuelven exactamente lo mismo que antes de la entrega 6 (también los cargos sin sede).</li>
 * <li>Con sedes: solo los cargos de esas sedes (nunca los que no tienen sede). Un conjunto vacío no deja pasar
 * nada.</li>
 * </ul>
 *
 * @param todas sin filtro de sede.
 * @param sedes sedes del filtro (vacío con {@code todas}).
 */
public record FiltroSedesTesoreria(boolean todas, Set<Long> sedes) {

	/** Id que ninguna sede tiene: Hibernate 6 no admite una lista vacía en {@code IN :sedes}. */
	static final Long NINGUNA = -1L;

	public static final FiltroSedesTesoreria TODAS = new FiltroSedesTesoreria(true, Set.of());

	public FiltroSedesTesoreria {
		sedes = sedes == null ? Set.of() : Set.copyOf(sedes);
	}

	/** Solo esas sedes. */
	public static FiltroSedesTesoreria de(Collection<Long> sedes) {
		return new FiltroSedesTesoreria(false, sedes == null ? Set.of() : Set.copyOf(sedes));
	}

	/** Valor del parámetro {@code :sedes} de las consultas: nunca vacío. */
	public Collection<Long> parametro() {
		return sedes.isEmpty() ? List.of(NINGUNA) : sedes;
	}

	/** Si un cargo con esa sede entra en el filtro. */
	public boolean incluye(Long sedeId) {
		return todas || (sedeId != null && sedes.contains(sedeId));
	}
}
