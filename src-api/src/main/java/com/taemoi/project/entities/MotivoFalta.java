package com.taemoi.project.entities;

/**
 * Motivo opcional de una falta en una lista guardada (decisiones 455-463, columna {@code asistencia_alumno.motivo_falta}
 * de V32_2). Solo para faltas: un alumno presente no lleva motivo. Sin motivo, la falta es como hasta ahora. «Médica» es
 * un dato de salud mínimo que el propietario aceptó (decisión 455): no lleva ningún detalle. {@code letra} es la marca
 * del PDF de asistencia marcado.
 */
public enum MotivoFalta {
	INJUSTIFICADA("I"), MEDICA("M"), FAMILIAR("F"), OTRA_JUSTIFICADA("J");

	private final String letra;

	MotivoFalta(String letra) {
		this.letra = letra;
	}

	public String letra() {
		return letra;
	}

	/** El motivo con ese nombre exacto; {@code null} si no hay ninguno con ese nombre. */
	public static MotivoFalta deNombre(String nombre) {
		if (nombre != null) {
			for (MotivoFalta motivo : values()) {
				if (motivo.name().equals(nombre)) {
					return motivo;
				}
			}
		}
		return null;
	}
}
