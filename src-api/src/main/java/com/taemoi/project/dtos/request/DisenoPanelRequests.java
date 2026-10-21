package com.taemoi.project.dtos.request;

/**
 * Cuerpos del interruptor del diseño nuevo (pieza 8b-I, fase3-e8b-plan.md §3). Todos obligatorios: sin el valor, 400.
 */
public final class DisenoPanelRequests {

	private DisenoPanelRequests() {
	}

	/** {@code PUT /api/panel/diseno-nuevo/{usuarioId}}: {@code {"permitido": true | false}}. */
	public record Permiso(Boolean permitido) {
	}

	/** {@code PUT /api/panel/diseno-nuevo/por-defecto}: {@code {"nuevo": true | false}} («Para todo el equipo»). */
	public record PorDefecto(Boolean nuevo) {
	}

	/** {@code PUT /api/panel/mi-diseno}: {@code {"activo": true | false}}, para la propia cuenta. */
	public record MiDiseno(Boolean activo) {
	}
}
