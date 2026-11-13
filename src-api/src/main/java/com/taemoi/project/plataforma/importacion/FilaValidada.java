package com.taemoi.project.plataforma.importacion;

import java.util.List;

/**
 * Una fila tras validarla.
 *
 * @param fila             número de fila de Excel
 * @param numeroFederacion el número leído, o {@code null}
 * @param yaCargada        su número ya es una licencia de la disciplina en la escuela: se salta, sin errores ni avisos
 * @param errores          lo que impide cargarla
 * @param avisos           lo que no lo impide
 * @param datos            lo que se escribiría; {@code null} si tiene errores o ya está cargada
 */
record FilaValidada(int fila, Integer numeroFederacion, boolean yaCargada, List<Incidencia> errores,
		List<Incidencia> avisos, DatosAlumno datos) {

	FilaValidada {
		errores = List.copyOf(errores);
		avisos = List.copyOf(avisos);
	}

	boolean lista() {
		return !yaCargada && errores.isEmpty();
	}
}
