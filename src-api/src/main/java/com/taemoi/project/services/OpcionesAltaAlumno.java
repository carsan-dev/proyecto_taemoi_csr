package com.taemoi.project.services;

/**
 * Opciones del alta de un alumno ({@link AlumnoService#crearAlumnoDesdeDTO(com.taemoi.project.dtos.AlumnoDTO,
 * OpcionesAltaAlumno)}). {@link #POR_DEFECTO} es el alta de siempre (ficha del panel y preinscripción); el importador de
 * la fase 4 usa {@link #IMPORTACION}.
 *
 * @param consentimientoImagenAutomatico apuntar la autorización de imagen de la ficha como consentimiento
 *                                       {@code USO_IMAGEN} de origen {@code APP}, fechado hoy y firmado por quien da el
 *                                       alta ({@code ConsentimientoService#registrarCambioAutorizacionWeb})
 * @param mensualidadDelMes              cargar la mensualidad del mes en curso si ya está cargada en la escuela
 *                                       ({@code asignarMensualidadGeneralSiCorresponde})
 */
public record OpcionesAltaAlumno(boolean consentimientoImagenAutomatico, boolean mensualidadDelMes) {

	/** El alta de siempre: consentimiento automático y mensualidad del mes si corresponde. */
	public static final OpcionesAltaAlumno POR_DEFECTO = new OpcionesAltaAlumno(true, true);

	/**
	 * Fase 4, importador de alumnos: sin consentimiento automático (el importador apunta el de la ficha de papel, si
	 * consta) y sin ningún cargo.
	 */
	public static final OpcionesAltaAlumno IMPORTACION = new OpcionesAltaAlumno(false, false);
}
