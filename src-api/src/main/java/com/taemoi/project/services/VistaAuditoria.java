package com.taemoi.project.services;

/**
 * Qué parte del registro de auditoría de la escuela activa se consulta (fase3-sedes-permisos.md, D2).
 */
public enum VistaAuditoria {

	/** Todo el registro de la escuela, con payload y parámetros: administrador de plataforma. */
	COMPLETA,

	/**
	 * «Registro de acciones» del propietario: solo peticiones con sesión y respuesta 2xx que sean altas, cambios o
	 * bajas, más las lecturas de datos de salud; sin errores técnicos (4xx/5xx), sin peticiones anónimas y sin
	 * payload ni parámetros (solo el resumen). Filtrado en el servidor.
	 */
	PROPIETARIO
}
