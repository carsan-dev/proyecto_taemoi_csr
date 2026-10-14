package com.taemoi.project.dtos.response;

import java.time.Instant;

import com.taemoi.project.entities.AlumnoSalud;
import com.taemoi.project.entities.OrigenDato;

/** Salud de un alumno. {@code registrado = false}: todavía no hay datos (todo nulo). */
public record AlumnoSaludDTO(Long alumnoId, boolean registrado, Boolean lesionEnfermedad, String descripcion,
		Boolean impideActividad, String impideDetalle, String alergias, OrigenDato origen, Instant actualizadoEn) {

	/** Con los textos ya descifrados por {@code AlumnoSaludService}. */
	public static AlumnoSaludDTO de(AlumnoSalud s, String descripcion, String impideDetalle, String alergias) {
		return new AlumnoSaludDTO(s.getAlumnoId(), true, s.getLesionEnfermedad(), descripcion,
				s.getImpideActividad(), impideDetalle, alergias, s.getOrigen(), s.getActualizadoEn());
	}

	public static AlumnoSaludDTO vacio(Long alumnoId) {
		return new AlumnoSaludDTO(alumnoId, false, null, null, null, null, null, null, null);
	}

	/** Sin contenido: nunca debe aparecer en logs. */
	@Override
	public String toString() {
		return "AlumnoSaludDTO[alumnoId=" + alumnoId + ", registrado=" + registrado + "]";
	}
}
