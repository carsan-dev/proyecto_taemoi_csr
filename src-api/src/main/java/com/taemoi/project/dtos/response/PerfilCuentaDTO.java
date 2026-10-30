package com.taemoi.project.dtos.response;

/**
 * Respuesta de {@code GET} y {@code PUT /api/panel/mi-perfil} (decisión 322). Solo sale a la propia cuenta.
 *
 * @param nombrePreferido el nombre por el que quiere que la llamen; {@code null} si no eligió ninguno.
 * @param nombreBase      «Nombre A.» de la cuenta, sin el preferido; {@code null} sin nombre.
 * @param cumpleDia       día guardado en la cuenta; {@code null} si no hay.
 * @param cumpleMes       mes guardado en la cuenta; {@code null} si no hay.
 * @param cumpleFicha     día y mes de la ficha de alumno PROPIA en la escuela activa, no anonimizada y con fecha de
 *                        nacimiento (con varias, la de menor id); {@code null} si no hay. Si existe, manda sobre lo
 *                        guardado en la cuenta.
 */
public record PerfilCuentaDTO(String nombrePreferido, String nombreBase, Integer cumpleDia, Integer cumpleMes,
		DiaMes cumpleFicha) {

	/** Un cumpleaños sin año. */
	public record DiaMes(int dia, int mes) {
	}
}
