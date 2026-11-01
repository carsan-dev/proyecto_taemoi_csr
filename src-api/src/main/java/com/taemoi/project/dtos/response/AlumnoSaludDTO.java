package com.taemoi.project.dtos.response;

import java.time.Instant;
import java.util.List;

import com.taemoi.project.entities.AlumnoSalud;
import com.taemoi.project.entities.OrigenDato;
import com.taemoi.project.entities.TipoDiscapacidad;

/**
 * Salud de un alumno. {@code registrado = false}: todavía no hay datos (todo nulo salvo {@code tieneDiscapacidad}, que es
 * del alumno, y {@code discapacidadTipos}, que nunca es nulo).
 */
public record AlumnoSaludDTO(Long alumnoId, boolean registrado, Boolean lesionEnfermedad, String descripcion,
		Boolean impideActividad, String impideDetalle, String alergias, OrigenDato origen, Instant actualizadoEn,
		Boolean tieneDiscapacidad, List<TipoDiscapacidad> discapacidadTipos, String discapacidadOtra) {

	public AlumnoSaludDTO {
		discapacidadTipos = discapacidadTipos == null ? List.of() : List.copyOf(discapacidadTipos);
	}

	/** Con los textos ya descifrados por {@code AlumnoSaludService}. */
	public static AlumnoSaludDTO de(AlumnoSalud s, String descripcion, String impideDetalle, String alergias,
			Boolean tieneDiscapacidad, List<TipoDiscapacidad> discapacidadTipos, String discapacidadOtra) {
		return new AlumnoSaludDTO(s.getAlumnoId(), true, s.getLesionEnfermedad(), descripcion,
				s.getImpideActividad(), impideDetalle, alergias, s.getOrigen(), s.getActualizadoEn(),
				tieneDiscapacidad, discapacidadTipos, discapacidadOtra);
	}

	public static AlumnoSaludDTO vacio(Long alumnoId, Boolean tieneDiscapacidad) {
		return new AlumnoSaludDTO(alumnoId, false, null, null, null, null, null, null, null, tieneDiscapacidad,
				List.of(), null);
	}

	/** Sin contenido: nunca debe aparecer en logs. */
	@Override
	public String toString() {
		return "AlumnoSaludDTO[alumnoId=" + alumnoId + ", registrado=" + registrado + "]";
	}
}
