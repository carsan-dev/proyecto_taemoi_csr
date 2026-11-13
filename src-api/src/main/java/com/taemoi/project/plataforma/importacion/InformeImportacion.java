package com.taemoi.project.plataforma.importacion;

import java.util.List;

/**
 * Informe de un ensayo o de una carga de alumnos. Sin nombres, apellidos, DNI, teléfonos ni correos.
 *
 * @param filas      filas de datos leídas (las totalmente vacías no cuentan)
 * @param listas     filas sin error que se crearían (las ya cargadas no cuentan)
 * @param creados    alumnos creados: solo en una carga confirmada; en el ensayo, 0
 * @param yaCargados filas saltadas porque su número de federación ya es una licencia de la disciplina en la escuela
 * @param errores    lo que impide la carga, por fila
 * @param avisos     lo que no la impide, por fila
 */
public record InformeImportacion(int filas, int listas, int creados, int yaCargados, List<Incidencia> errores,
		List<Incidencia> avisos) {

	public InformeImportacion {
		errores = List.copyOf(errores);
		avisos = List.copyOf(avisos);
	}

	boolean conErrores() {
		return !errores.isEmpty();
	}

	InformeImportacion conCreados(int creados) {
		return new InformeImportacion(filas, listas, creados, yaCargados, errores, avisos);
	}

	InformeImportacion conError(Incidencia error) {
		List<Incidencia> todos = new java.util.ArrayList<>(errores);
		todos.add(error);
		todos.sort(java.util.Comparator.comparingInt(Incidencia::fila));
		return new InformeImportacion(filas, listas, 0, yaCargados, todos, avisos);
	}
}
