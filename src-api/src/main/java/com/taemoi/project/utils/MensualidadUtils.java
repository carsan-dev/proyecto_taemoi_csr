package com.taemoi.project.utils;

public class MensualidadUtils {

	private static final String NOMBRE_LEGADO = "MENSUALIDAD";

	/** «MENSUALIDAD <MES> <AÑO>» (nombre del producto de mensualidad de Taemoi). */
	public static String formatearNombreMensualidad(String mesAno) {
		return formatearNombreMensualidad(NOMBRE_LEGADO, mesAno);
	}

	/**
	 * «<nombreProducto> <MES> <AÑO>»: el concepto de una mensualidad con el nombre del producto de
	 * mensualidad de la organización.
	 */
	public static String formatearNombreMensualidad(String nombreProducto, String mesAno) {
		return nombreProducto + " " + formatearMesAno(mesAno);
	}

	/** «<MES> <AÑO>» en español y mayúsculas, a partir de «YYYY-MM». */
	public static String formatearMesAno(String mesAno) {
	    if (mesAno == null || !mesAno.matches("\\d{4}-\\d{2}")) {
	        throw new IllegalArgumentException("El formato de mes y año debe ser 'YYYY-MM'. Valor recibido: " + mesAno);
	    }

	    String[] partes = mesAno.split("-");
	    int mes = Integer.parseInt(partes[1]);
	    String anio = partes[0];

	    // Nombres de los meses en español
	    String[] meses = {
	        "ENERO", "FEBRERO", "MARZO", "ABRIL", "MAYO", "JUNIO",
	        "JULIO", "AGOSTO", "SEPTIEMBRE", "OCTUBRE", "NOVIEMBRE", "DICIEMBRE"
	    };

	    if (mes < 1 || mes > 12) {
	        throw new IllegalArgumentException("El mes debe estar entre 1 y 12. Valor recibido: " + mes);
	    }

	    return meses[mes - 1] + " " + anio;
	}

}
