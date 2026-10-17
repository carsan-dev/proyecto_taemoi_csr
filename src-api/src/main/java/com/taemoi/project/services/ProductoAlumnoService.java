package com.taemoi.project.services;

import java.util.List;
import java.time.LocalDate;

import com.taemoi.project.dtos.ProductoAlumnoDTO;
import com.taemoi.project.dtos.response.CuantiaPersonalizadaSuperiorDTO;
import com.taemoi.project.entities.Alumno;

public interface ProductoAlumnoService {

	ProductoAlumnoDTO asignarProductoAAlumno(Long alumnoId, Long productoId, ProductoAlumnoDTO detallesDTO);

	List<ProductoAlumnoDTO> obtenerProductosDeAlumno(Long alumnoId);

	ProductoAlumnoDTO actualizarProductoAlumno(Long id, ProductoAlumnoDTO detallesDTO);

	void eliminarProductoAlumno(Long id);

	ProductoAlumnoDTO reservarPlaza(Long alumnoId, String concepto, boolean pagado, boolean forzar);

	/**
	 * Reserva plaza para un deporte específico de un alumno
	 * @param alumnoId ID del alumno
	 * @param deporte Nombre del deporte (TAEKWONDO, KICKBOXING, etc.)
	 * @param concepto Concepto de la reserva
	 * @param pagado Si la reserva está pagada
	 * @param forzar Si true, crea la reserva aunque ya exista
	 * @return ProductoAlumnoDTO creado
	 */
	ProductoAlumnoDTO reservarPlazaPorDeporte(Long alumnoId, String deporte, String concepto, boolean pagado, boolean forzar);

	/**
	 * @return alumnos omitidos por ser de una sede inactiva (D6-17, D6-23) y, desde la 6-4, las mensualidades con una
	 *         cuantía personalizada mayor que la tarifa de la sede (D6-28)
	 */
	ResultadoGeneracionMensualidades cargarMensualidadesGenerales(String mesAno, LocalDate fechaAsignacion);

	/** Como {@link #cargarMensualidadesGenerales}, solo para una disciplina. */
	ResultadoGeneracionMensualidades cargarMensualidadesPorDeporte(String mesAno, String deporte,
			LocalDate fechaAsignacion);

	/** @return mensualidades creadas con una cuantía personalizada mayor que la tarifa de la sede (6-4, D6-28). */
	List<CuantiaPersonalizadaSuperiorDTO> cargarMensualidadIndividual(Long alumnoId, String mesAno, boolean forzar,
			LocalDate fechaAsignacion);

	/** @return alumnos omitidos por ser de una sede inactiva (D6-17, D6-23); 0 si todas están activas. */
	int cargarLicenciasGenerales(int ano, String deporte, LocalDate fechaAsignacion);

	void cargarLicenciaIndividual(Long alumnoId, int ano, String deporte, boolean forzar, LocalDate fechaAsignacion);

	void crearAltaLicenciaFederativa(Alumno alumno);

	ProductoAlumnoDTO renovarLicencia(Long alumnoId);

	// ===== MÉTODOS MULTI-DEPORTE =====

	/**
	 * Asigna un producto a un alumno para un deporte específico
	 * @param alumnoId ID del alumno
	 * @param productoId ID del producto
	 * @param deporte Nombre del deporte (TAEKWONDO, KICKBOXING, etc.)
	 * @param detallesDTO Detalles del producto
	 * @return ProductoAlumnoDTO creado
	 */
	ProductoAlumnoDTO asignarProductoAAlumnoDeporte(Long alumnoId, Long productoId, String deporte, ProductoAlumnoDTO detallesDTO);

	/**
	 * Carga mensualidades para todos los alumnos, creando una por cada deporte que practican
	 * @param mesAno Mes y año en formato "MM/YYYY"
	 */
	/** Como {@link #cargarMensualidadesGenerales}, con la fecha de hoy. */
	ResultadoGeneracionMensualidades cargarMensualidadesMultiDeporte(String mesAno);

	/**
	 * Carga mensualidad individual para un deporte específico de un alumno
	 * @param alumnoId ID del alumno
	 * @param deporte Nombre del deporte
	 * @param mesAno Mes y año en formato "MM/YYYY"
	 * @param forzar Si true, crea la mensualidad aunque ya exista
	 * @return mensualidades creadas con una cuantía personalizada mayor que la tarifa de la sede (6-4, D6-28)
	 */
	List<CuantiaPersonalizadaSuperiorDTO> cargarMensualidadIndividualPorDeporte(Long alumnoId, String deporte,
			String mesAno, boolean forzar, LocalDate fechaAsignacion);
}
