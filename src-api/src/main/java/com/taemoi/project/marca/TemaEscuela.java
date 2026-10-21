package com.taemoi.project.marca;

/**
 * Pieza 8b-T (fase3-e8b-plan.md §4): los colores del panel de una escuela, derivados de su color guardado por
 * {@link ReglaContraste}. Todos en {@code #RRGGBB} mayúsculas. No se guarda: se calcula al leer.
 *
 * @param primario       fondo de la cabecera (el guardado; o el oscurecido si ni el blanco ni la tinta llegaban).
 * @param sobrePrimario  texto sobre el primario: blanco o la tinta ({@code #1A2230}), siempre ≥ 4,5:1.
 * @param sobrePrimario2 secundario de la cabecera: mezcla del texto con el primario, ≥ 4,5:1 sobre el primario.
 * @param primarioTinta  el color como tinta (botón principal, enlaces, foco): ≥ 4,5:1 sobre blanco.
 * @param primarioSuave  fondo suave (opción actual, selección): el primario al 10 % sobre blanco, con la tinta ≥ 4,5:1
 *                       encima.
 * @param ajustado       el primario se oscureció para que el blanco llegue.
 */
public record TemaEscuela(String primario, String sobrePrimario, String sobrePrimario2, String primarioTinta,
		String primarioSuave, boolean ajustado) {
}
