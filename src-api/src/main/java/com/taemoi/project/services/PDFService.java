package com.taemoi.project.services;

import java.io.IOException;

import com.taemoi.project.entities.Deporte;

public interface PDFService {
	byte[] generarInformeAlumnosPorGrado(boolean soloActivos);

	byte[] generarInformeTaekwondoPorGrado(boolean soloActivos);

	byte[] generarInformeKickboxingPorGrado(boolean soloActivos);

	byte[] generarInformeLicencias(boolean soloActivos);

	byte[] generarInformeInfantilesAPromocionar(boolean soloActivos);

	byte[] generarInformeAdultosAPromocionar(boolean soloActivos);

	byte[] generarInformeInfantilesAPromocionarTaekwondo(boolean soloActivos);

	byte[] generarInformeInfantilesAPromocionarKickboxing(boolean soloActivos);

	byte[] generarInformeAdultosAPromocionarTaekwondo(boolean soloActivos);

	byte[] generarInformeAdultosAPromocionarKickboxing(boolean soloActivos);

	byte[] generarInformeDeudas(boolean soloActivos);

	byte[] generarInformeDeudasCSV(boolean soloActivos);

	byte[] generarInformeMensualidades(boolean soloActivos);

	byte[] generarInformeMensualidadesTaekwondo(boolean soloActivos);

	byte[] generarInformeMensualidadesKickboxing(boolean soloActivos);

	byte[] generarListadoAsistencia(int year, int month, String grupo, Deporte deporte) throws IOException;

	byte[] generarInformeConvocatoria(Long convocatoriaId);
}
