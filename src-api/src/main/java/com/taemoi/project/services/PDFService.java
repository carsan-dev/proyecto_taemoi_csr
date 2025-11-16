package com.taemoi.project.services;

import java.io.IOException;

public interface PDFService {
	byte[] generarInformeAlumnosPorGrado();

	byte[] generarInformeTaekwondoPorGrado();

	byte[] generarInformeKickboxingPorGrado();

	byte[] generarInformeLicencias();

	byte[] generarInformeInfantilesAPromocionar();

	byte[] generarInformeAdultosAPromocionar();

	byte[] generarInformeDeudas();

	byte[] generarInformeDeudasCSV();

	byte[] generarInformeMensualidades();

	byte[] generarInformeMensualidadesTaekwondo();

	byte[] generarInformeMensualidadesKickboxing();

	byte[] generarListadoAsistencia(int year, int month, String grupo) throws IOException;

	byte[] generarInformeConvocatoria(Long convocatoriaId);
}
