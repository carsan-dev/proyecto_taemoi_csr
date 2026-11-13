package com.taemoi.project.plataforma.importacion;

/**
 * Fallo inesperado al escribir una fila del importador de alumnos (no un error de negocio): la transacción ya se ha
 * deshecho y no queda nada escrito. Sale como 500 con {@link #MENSAJE}; la causa queda en el log de
 * {@link ImportacionAlumnosService}, no aquí (su mensaje podría citar datos de la hoja).
 */
public class ImportacionFallidaException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public static final String MENSAJE = "No se ha podido terminar por un fallo inesperado: no se ha escrito nada. "
			+ "Avisa al administrador.";

	ImportacionFallidaException() {
		super(MENSAJE, null, false, false);
	}
}
