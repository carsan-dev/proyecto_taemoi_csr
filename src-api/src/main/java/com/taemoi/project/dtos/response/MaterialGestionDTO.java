package com.taemoi.project.dtos.response;

import java.util.List;

/** Respuestas de {@code /api/material-examen} (pieza R1). Las fechas van en ISO-8601 UTC. */
public final class MaterialGestionDTO {

	private MaterialGestionDTO() {
	}

	public record Listado(List<DisciplinaConBloques> disciplinas, Totales totales) {
	}

	public record DisciplinaConBloques(String codigo, String nombre, List<BloqueResumen> bloques, Totales totales) {
	}

	public record BloqueResumen(String id, String nombre, List<GradoDelBloque> grados, boolean tieneTemario, int videos,
			long bytes) {
	}

	public record GradoDelBloque(String codigo, String nombre) {
	}

	public record Totales(int temarios, int videos, long bytes) {
	}

	public record Detalle(String disciplina, String id, String nombre, List<GradoDelBloque> grados,
			TemarioDetalle temario, List<VideoDetalle> videos, List<DocumentoDetalle> documentos) {
	}

	public record TemarioDetalle(String nombre, long bytes, String fecha) {
	}

	public record VideoDetalle(String fichero, String titulo, int orden, long bytes, String fecha,
			boolean tambienEnSuperiores) {
	}

	public record DocumentoDetalle(String fichero, String titulo, String mime, long bytes, String fecha) {
	}

	/** Resultado de una subida nueva: el nombre final (puede llevar sufijo) y el bloque tal como queda. */
	public record Subida(String fichero, Detalle bloque) {
	}
}
