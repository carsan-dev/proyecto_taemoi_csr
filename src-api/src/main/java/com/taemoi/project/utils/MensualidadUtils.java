package com.taemoi.project.utils;

public class MensualidadUtils {

	public static String formatearNombreMensualidad(String mesAno) {
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

	    return "MENSUALIDAD " + meses[mes - 1] + " " + anio;
	}

}
