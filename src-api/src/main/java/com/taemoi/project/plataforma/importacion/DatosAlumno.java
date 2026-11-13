package com.taemoi.project.plataforma.importacion;

import java.time.LocalDate;

import com.taemoi.project.entities.ParentescoTutor;

/**
 * Lo que se escribe de una fila sin errores, ya normalizado y resuelto contra la escuela. Solo vive en memoria durante la
 * petición.
 */
record DatosAlumno(int numeroLicencia, String nombre, String apellidos, LocalDate fechaNacimiento, boolean menor,
		String nif, String telefono, String telefono2, String email, String direccion, String localidad,
		String codigoPostal, String observaciones, Long sedeId, Long grupoId, String grado, String tarifa,
		LocalDate fechaLicencia, String tutorNombre, ParentescoTutor tutorParentesco, String tutorNif,
		UsoImagen usoImagen) {

	/** La autorización de uso de imagen de la ficha de papel. */
	enum UsoImagen {
		SI, NO, SIN_REGISTRAR
	}
}
