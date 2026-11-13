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
 * @param yaCargadas las filas que se saltan por ya cargadas (plan §4.3), solo con su fila y su número de federación
 */
public record InformeImportacion(int filas, int listas, int creados, int yaCargados, List<Incidencia> errores,
		List<Incidencia> avisos, List<FilaYaCargada> yaCargadas) {

	/**
	 * Una fila ya cargada: no se toca.
	 *
	 * @param fila             número de fila de Excel
	 * @param numeroFederacion su número de federación (ya es una licencia de la disciplina en la escuela)
	 */
	public record FilaYaCargada(int fila, Integer numeroFederacion) {
	}

	public InformeImportacion {
		errores = List.copyOf(errores);
		avisos = List.copyOf(avisos);
		yaCargadas = List.copyOf(yaCargadas);
	}

	boolean conErrores() {
		return !errores.isEmpty();
	}

	InformeImportacion conCreados(int creados) {
		return new InformeImportacion(filas, listas, creados, yaCargados, errores, avisos, yaCargadas);
	}

	InformeImportacion conError(Incidencia error) {
		List<Incidencia> todos = new java.util.ArrayList<>(errores);
		todos.add(error);
		todos.sort(java.util.Comparator.comparingInt(Incidencia::fila));
		return new InformeImportacion(filas, listas, 0, yaCargados, todos, avisos, yaCargadas);
	}
}
