package com.taemoi.project.dtos.response;

/**
 * Respuestas de las rallas hacia el examen (decisiones 510-513). {@code mediasRallas}: de 0 a 10 (dos medias son una
 * ralla; 10 son las 5 rallas del examen).
 */
public final class RallasDTOs {

	private RallasDTOs() {
	}

	/**
	 * Cómo queda la inscripción del alumno en la disciplina del grupo tras un paso o tras marcarlo apto desde la hoja.
	 * {@code deporte}: código de la disciplina; {@code aptoParaExamen}: el apto de esa inscripción.
	 */
	public record Estado(Long alumnoId, String deporte, int mediasRallas, boolean aptoParaExamen) {
	}

	/**
	 * Interruptor de una disciplina en Configuración: {@code deporte} (código), {@code nombre} y {@code activas} (la
	 * escuela usa las rallas en ella). Solo las disciplinas activas con sistema de grados.
	 */
	public record Disciplina(String deporte, String nombre, boolean activas) {
	}
}
