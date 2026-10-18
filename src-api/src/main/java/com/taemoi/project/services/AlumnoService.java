package com.taemoi.project.services;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.dtos.AlumnoDTO;
import com.taemoi.project.dtos.TurnoDTO;
import com.taemoi.project.dtos.response.AlumnoConGruposDTO;
import com.taemoi.project.dtos.response.AlumnoConvocatoriaDTO;
import com.taemoi.project.dtos.response.RetoDiarioRankingGeneralResponse;
import com.taemoi.project.dtos.response.RetoDiarioRankingSemanalResponse;
import com.taemoi.project.dtos.response.RetoDiarioEstadoDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Categoria;
import com.taemoi.project.entities.Documento;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.RolFamiliar;

import jakarta.validation.Valid;

public interface AlumnoService {
	Page<Alumno> obtenerTodosLosAlumnos(@NonNull Pageable pageable);

	List<Alumno> obtenerTodosLosAlumnos();

	Optional<Alumno> obtenerAlumnoPorId(@NonNull Long id);

	Optional<AlumnoDTO> obtenerAlumnoPorIdDTO(@NonNull Long id);

	Page<Alumno> obtenerAlumnosFiltrados(String nombre, Long gradoId, Long categoriaId, boolean incluirInactivos,
			@NonNull Pageable pageable);

	Page<Alumno> obtenerAlumnosFiltrados(String nombre, Long gradoId, Long categoriaId, boolean incluirInactivos,
			boolean aptoParaExamen, @NonNull Pageable pageable);

	List<Alumno> obtenerAlumnosFiltrados(String nombre, Long gradoId, Long categoriaId, boolean incluirInactivos);

	/**
	 * R2-P6 (decisión D5): el listado filtrado por deporte en el servidor, con el criterio que aplicaba el navegador
	 * sobre {@code GET /api/alumnos/con-deportes} (listado-alumnos, {@code actualizarAlumnosPaginadosCliente}):
	 * <ul>
	 * <li>el alumno tiene el deporte, activo o de baja en él;</li>
	 * <li>sin inactivos: ficha activa y al menos un deporte activo (el de baja en ese deporte que sigue activo en otro
	 * sale);</li>
	 * <li>nombre: el texto sin espacios a los lados, en minúsculas, contenido en el nombre o en los apellidos (no en
	 * «nombre apellidos»), distinguiendo tildes y sin comodines.</li>
	 * </ul>
	 * Mismo alcance que el listado (organización activa; el responsable de sede, sus sedes). El orden lo da el
	 * {@code pageable} (el listado: nombre e id).
	 */
	Page<Alumno> obtenerAlumnosPorDeporte(String deporte, String nombre, Long gradoId, Long categoriaId,
			boolean incluirInactivos, boolean aptoParaExamen, @NonNull Pageable pageable);

	List<Long> obtenerIdsAlumnos(boolean incluirInactivos);

	Alumno crearAlumnoDesdeDTO(@Valid @NonNull AlumnoDTO nuevoAlumnoDTO);

	Alumno actualizarAlumno(@Valid @NonNull Long id, AlumnoDTO alumnoActualizado, Date nuevaFechaNacimiento,
			MultipartFile nuevaImagen);

	void eliminarImagenAlumno(@NonNull Long id);

	boolean eliminarAlumno(@Valid @NonNull Long id);

	List<TurnoDTO> obtenerTurnosDelAlumno(Long alumnoId);

	void asignarAlumnoATurno(Long alumnoId, Long turnoId);

	void removerAlumnoDeTurno(Long alumnoId, Long turnoId);

	double asignarCuantiaTarifa(String tipoTarifa);

	double asignarCuantiaTarifa(String tipoTarifa, RolFamiliar rolFamiliar);

	Categoria asignarCategoriaSegunEdad(int edad);

	Grado asignarGradoSegunEdad(AlumnoDTO nuevoAlumnoDTO);

	String calcularSiguienteGrado(Alumno alumno);

	boolean fechaNacimientoValida(Date fechaNacimiento);

	boolean datosAlumnoValidos(AlumnoDTO alumnoDTO);

	boolean datosAlumnoValidos(AlumnoDTO alumnoDTO, boolean requiereTarifaYFechaAlta);

	String generarContrasena(String nombre, String apellidos);

	Alumno actualizarObservaciones(@NonNull Long id, String observaciones);

	Alumno darDeBajaAlumno(Long id);

	Alumno darDeAltaAlumno(Long id);

	List<AlumnoConGruposDTO> obtenerAlumnosAptosConGruposDTO();

	List<AlumnoConGruposDTO> obtenerAlumnosAptosPorDeporte(String deporte, String exclusion);

	Optional<AlumnoConGruposDTO> obtenerAlumnoAptoPorId(Long id);

	List<AlumnoConGruposDTO> obtenerAlumnosElegiblesParaConvocatoria(String deporte);

	AlumnoConvocatoriaDTO agregarAlumnoAConvocatoria(Long alumnoId, Long convocatoriaId, boolean porRecompensa,
			boolean rojoBordado);

	void eliminarAlumnoDeConvocatoria(Long alumnoId, Long convocatoriaId);

	boolean esAptoParaExamen(Alumno alumno);
	
	long countAlumnos();

	Documento agregarDocumentoAAlumno(Long alumnoId, MultipartFile archivo);

	List<Documento> obtenerDocumentosAlumno(Long alumnoId);

	void eliminarDocumentoDeAlumno(Long alumnoId, Long documentoId);

	Documento obtenerDocumentoDeAlumno(Long alumnoId, Long documentoId);

	RetoDiarioEstadoDTO obtenerEstadoRetoDiario(Long alumnoId);

	RetoDiarioEstadoDTO completarRetoDiario(Long alumnoId);

	RetoDiarioRankingSemanalResponse obtenerRankingSemanalRetoDiario(Long alumnoId, String deporte, Integer limit);

	RetoDiarioRankingGeneralResponse obtenerRankingGeneralRetoDiario(Long alumnoId, String deporte, Integer limit);

	// ==================== MÉTODOS MULTI-DEPORTE ====================

	/**
	 * Obtiene todos los deportes de un alumno
	 */
	List<com.taemoi.project.entities.AlumnoDeporte> obtenerDeportesDelAlumno(Long alumnoId);

	/**
	 * Obtiene solo los deportes activos de un alumno
	 */
	List<com.taemoi.project.entities.AlumnoDeporte> obtenerDeportesActivosDelAlumno(Long alumnoId);

	/**
	 * Agrega un deporte a un alumno
	 */
	com.taemoi.project.entities.AlumnoDeporte agregarDeporteAAlumno(Long alumnoId,
			String deporte,
			String gradoInicial,
			java.util.Date fechaAlta,
			java.util.Date fechaAltaInicial,
			java.util.Date fechaGrado);

	/**
	 * Remueve un deporte de un alumno
	 */
	void removerDeporteDeAlumno(Long alumnoId, String deporte);

	/**
	 * Actualiza el grado de un alumno en un deporte específico
	 */
	com.taemoi.project.entities.AlumnoDeporte actualizarGradoPorDeporte(Long alumnoId,
			String deporte,
			String nuevoGrado);

	/**
	 * Pasa de grado por recompensa en un deporte específico y asigna el producto correspondiente
	 */
	com.taemoi.project.entities.AlumnoDeporte pasarGradoPorRecompensa(Long alumnoId,
			String deporte, boolean rojoBordado);

	/**
	 * Pasa de grado con derecho de examen (no recompensa) y asigna el producto correspondiente.
	 * Cuando el producto se marque como pagado, se actualizará el grado.
	 */
	com.taemoi.project.entities.AlumnoDeporte pasarGradoConDerechoExamen(Long alumnoId,
			String deporte, boolean rojoBordado);

	/**
	 * Actualiza la fecha de alta inicial del alumno
	 * Esta fecha afecta el cálculo de antigüedad para todos los deportes
	 *
	 * @param id                    ID del alumno
	 * @param nuevaFechaAltaInicial Nueva fecha de alta inicial
	 * @return Alumno actualizado
	 */
	Alumno actualizarFechaAltaInicial(@NonNull Long id, Date nuevaFechaAltaInicial);

	/**
	 * Busca un alumno por ID (alias de obtenerAlumnoPorId para compatibilidad)
	 *
	 * @param id ID del alumno
	 * @return Alumno encontrado
	 * @throws com.taemoi.project.exceptions.alumno.AlumnoNoEncontradoException si no se encuentra
	 */
	Alumno buscarAlumno(@NonNull Long id);
}
