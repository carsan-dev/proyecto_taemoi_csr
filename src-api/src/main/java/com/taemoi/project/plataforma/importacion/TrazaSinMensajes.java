package com.taemoi.project.plataforma.importacion;

/**
 * Copia de una excepción para el log: la misma traza (y la de sus causas), con la clase de cada una en lugar de su
 * mensaje. Los mensajes de la base de datos o de los servicios pueden citar valores de la hoja (un NIF repetido, por
 * ejemplo); la clase y las líneas bastan para encontrar el fallo.
 */
final class TrazaSinMensajes extends RuntimeException {

	private static final long serialVersionUID = 1L;
	/** Causas encadenadas que se copian, como mucho (evita bucles). */
	private static final int CAUSAS_MAXIMAS = 10;

	private TrazaSinMensajes(Throwable original, TrazaSinMensajes causa) {
		super(original.getClass().getName(), causa, false, true);
		setStackTrace(original.getStackTrace());
	}

	static TrazaSinMensajes de(Throwable original) {
		return copiar(original, 0);
	}

	private static TrazaSinMensajes copiar(Throwable original, int profundidad) {
		Throwable causa = original.getCause();
		TrazaSinMensajes copiaDeLaCausa = causa == null || causa == original || profundidad >= CAUSAS_MAXIMAS ? null
				: copiar(causa, profundidad + 1);
		return new TrazaSinMensajes(original, copiaDeLaCausa);
	}
}
