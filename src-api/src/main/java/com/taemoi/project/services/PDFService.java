package com.taemoi.project.services;

import java.io.IOException;
import java.util.List;

import com.taemoi.project.entities.Preinscripcion;

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

	byte[] generarInformeProductos();

	byte[] generarInformeMensualidades(boolean soloActivos);

	byte[] generarInformeMensualidadesTaekwondo(boolean soloActivos);

	byte[] generarInformeMensualidadesKickboxing(boolean soloActivos);

	List<String> obtenerTemporadasReservasPlaza();

	byte[] generarInformeReservasPlaza(String temporada, boolean soloActivos);

	byte[] generarListadoMensualidadMensual(String mesAno, boolean soloActivos);

	byte[] generarListadoAsistencia(int year, int month, String grupo, String deporte) throws IOException;

	byte[] generarListadoAsistencia(int year, int month, List<String> grupos, String deporte) throws IOException;

	/**
	 * Decisión 142 (pieza 9-6): solo las clases de grupos de esa sede, con todos los que van a ellas: no se filtra por la
	 * sede principal del alumno. Por AND con el alcance del responsable; sin sede, lo de siempre.
	 */
	byte[] generarListadoAsistencia(int year, int month, List<String> grupos, String deporte, Long sedeId)
			throws IOException;

	/**
	 * Pieza E7-2 (D7-15): hoja de asistencia del mes con esos turnos (por id), solo con los alumnos activos del turno
	 * que están en su grupo. La usa el profesor para su grupo; la firma por {@code String} no cambia.
	 */
	byte[] generarListadoAsistenciaPorTurnos(int year, int month, List<Long> turnoIds) throws IOException;

	/**
	 * Como {@link #generarListadoAsistencia(int, int, List, String, Long)} con {@code marcada}: la rejilla sale con lo
	 * guardado en «pasar lista» (X asistió, – faltó, vacía sin lista) y una leyenda. Con {@code false}, el de siempre.
	 */
	byte[] generarListadoAsistencia(int year, int month, List<String> grupos, String deporte, Long sedeId,
			boolean marcada) throws IOException;

	/** Como {@link #generarListadoAsistenciaPorTurnos(int, int, List)} con {@code marcada}, igual que arriba. */
	byte[] generarListadoAsistenciaPorTurnos(int year, int month, List<Long> turnoIds, boolean marcada)
			throws IOException;

	byte[] generarInformeConvocatoria(Long convocatoriaId);

	byte[] generarInformeCompetidores();

	byte[] generarInformeAlumnasDpf(boolean soloActivos);

	byte[] generarPreinscripcionFirmada(Preinscripcion preinscripcion);

	byte[] generarPreviewPreinscripcion(String deporte, String contenido, String instrucciones);
}
