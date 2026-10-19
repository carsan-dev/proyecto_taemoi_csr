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

	/**
	 * Pieza 6b-2: cobro en efectivo del profesor de un cargo pendiente (mismas reglas y efectos que el cobro del PUT del
	 * cargo, con origen PROFESOR). Ya pagado → 409.
	 *
	 * @return el cobro ACTIVO registrado
	 */
	com.taemoi.project.entities.Cobro cobrarEnEfectivoComoProfesor(Long cargoId);

	/**
	 * Pieza 6b-2: el profesor {@code profesorId} deshace su cobro (ACTIVO, origen PROFESOR y sin entrega): el cargo
	 * vuelve a pendiente con los mismos efectos que la reversión del PUT. Ajeno → 403; entregado → 409.
	 */
	void deshacerCobroDeProfesor(Long cobroId, Long profesorId);

	/**
	 * Pieza 6b-2c: como {@link #deshacerCobroDeProfesor(Long, Long)} con el motivo del profesor. {@code NO_HUBO_VENTA},
	 * solo para un cargo nacido de su venta del catálogo: anula el cobro y CANCELA el cargo (motivo «no hubo venta») en la
	 * misma transacción; si el cargo no es una venta suya → 400. {@code null} = {@code AUN_NO_PAGADO}.
	 */
	void deshacerCobroDeProfesor(Long cobroId, Long profesorId,
			com.taemoi.project.dtos.request.CajaRequests.MotivoDeshacer motivo);

	/**
	 * Pieza 6b-2c: cancela un cargo PENDIENTE (motivo obligatorio, hasta 500 caracteres). Pagado o ya cancelado → 409;
	 * cargo de examen unido a una inscripción a convocatoria → 400 (se quita al alumno de la convocatoria). Queda en el
	 * registro de acciones.
	 */
	ProductoAlumnoDTO cancelarProductoAlumno(Long id, String motivo);

	/** Pieza 6b-2c: deja pendiente un cargo cancelado y borra los datos de la cancelación. No cancelado → 409. */
	ProductoAlumnoDTO reactivarProductoAlumno(Long id);

	/**
	 * Pieza 6b-2: el profesor apunta y cobra en efectivo un producto del catálogo (sin efectos colaterales) a un alumno:
	 * cargo ya pagado con precio y concepto del catálogo y su cobro ACTIVO de origen PROFESOR. Producto excluido → 400.
	 *
	 * @return el cobro ACTIVO registrado
	 */
	com.taemoi.project.entities.Cobro apuntarYCobrarProductoComoProfesor(Long alumnoId, Long productoId);

	/**
	 * Pieza 6b-2: productos del catálogo que el profesor puede apuntar y cobrar (la misma lista blanca; desde la 6b-3b-1,
	 * solo los marcados por la gestión como «Lo puede vender el profesor»).
	 */
	List<com.taemoi.project.entities.Producto> productosVendiblesPorProfesor();

	/**
	 * Pieza 6b-3b-1: ids de los cobros de {@code cobros} que son la venta del catálogo de quien los cobró (la condición
	 * que hace válido deshacer con NO_HUBO_VENTA, sin depender de que el producto siga marcado). Sin N+1.
	 */
	java.util.Set<Long> cobrosQueSonVentaDelProfesor(java.util.Collection<com.taemoi.project.entities.Cobro> cobros);

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
