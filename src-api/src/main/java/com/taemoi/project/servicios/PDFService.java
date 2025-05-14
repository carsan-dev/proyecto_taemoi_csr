package com.taemoi.project.servicios;

public interface PDFService {
	byte[] generarInformeAlumnosPorGrado();
	byte[] generarInformeTaekwondoPorGrado();
	byte[] generarInformeKickboxingPorGrado();
	byte[] generarInformeLicencias();
	byte[] generarInformeInfantilesAPromocionar();
}
