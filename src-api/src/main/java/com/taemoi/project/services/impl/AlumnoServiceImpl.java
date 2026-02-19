package com.taemoi.project.services.impl;

import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.dtos.AlumnoDTO;
import com.taemoi.project.dtos.TurnoDTO;
import com.taemoi.project.dtos.response.AlumnoConGruposDTO;
import com.taemoi.project.dtos.response.AlumnoConvocatoriaDTO;
import com.taemoi.project.dtos.response.RetoDiarioRankingItemResponse;
import com.taemoi.project.dtos.response.RetoDiarioRankingMiPosicionResponse;
import com.taemoi.project.dtos.response.RetoDiarioRankingSemanalResponse;
import com.taemoi.project.dtos.response.RetoDiarioEstadoDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoConvocatoria;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.AlumnoRetoDiarioLog;
import com.taemoi.project.entities.Categoria;
import com.taemoi.project.entities.Convocatoria;
import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.Documento;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.Grupo;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.entities.Producto;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.entities.RolFamiliar;
import com.taemoi.project.entities.TipoCategoria;
import com.taemoi.project.entities.TipoGrado;
import com.taemoi.project.entities.TipoTarifa;
import com.taemoi.project.entities.Turno;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.exceptions.alumno.AlumnoDuplicadoException;
import com.taemoi.project.exceptions.alumno.AlumnoNoEncontradoException;
import com.taemoi.project.repositories.AlumnoConvocatoriaRepository;
import com.taemoi.project.repositories.AlumnoRetoDiarioLogRepository;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.CategoriaRepository;
import com.taemoi.project.repositories.ConvocatoriaRepository;
import com.taemoi.project.repositories.DocumentoRepository;
import com.taemoi.project.repositories.GradoRepository;
import com.taemoi.project.repositories.GrupoRepository;
import com.taemoi.project.repositories.ImagenRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.repositories.ProductoRepository;
import com.taemoi.project.repositories.TurnoRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.AlumnoService;
import com.taemoi.project.services.DocumentoService;
import com.taemoi.project.services.ImagenService;
import com.taemoi.project.services.ProductoAlumnoService;
import com.taemoi.project.utils.AlumnoDeporteUtils;
import com.taemoi.project.utils.FechaUtils;
import com.taemoi.project.utils.EmailUtils;
import com.taemoi.project.utils.MensualidadUtils;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementación del servicio para operaciones relacionadas con los alumnos.
 */
@Service
public class AlumnoServiceImpl implements AlumnoService {

	private static final Logger logger = LoggerFactory.getLogger(AlumnoServiceImpl.class);
	private static final String DERECHO_EXAMEN_ROJO = "DERECHO A EXAMEN ROJO";
	private static final String DERECHO_EXAMEN_ROJO_BORDADO = "DERECHO A EXAMEN ROJO BORDADO";
	private static final String DERECHO_RECOMPENSA_ROJO = "DERECHO DE CAMBIO A ROJO POR RECOMPENSA";
	private static final String DERECHO_RECOMPENSA_ROJO_BORDADO = "DERECHO DE CAMBIO A ROJO BORDADO POR RECOMPENSA";

	/**
	 * Inyección del repositorio de alumno.
	 */
	@Autowired
	private AlumnoRepository alumnoRepository;

	/**
	 * Inyección del repositorio de categoría.
	 */
	@Autowired
	private CategoriaRepository categoriaRepository;

	/**
	 * Inyección del repositorio de grado.
	 */
	@Autowired
	private GradoRepository gradoRepository;

	/**
	 * Inyección del repositorio de imagen.
	 */
	@Autowired
	private ImagenRepository imagenRepository;

	@Autowired
	private GrupoRepository grupoRepository;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private TurnoRepository turnoRepository;

	@Autowired
	private ConvocatoriaRepository convocatoriaRepository;

	@Autowired
	private AlumnoConvocatoriaRepository alumnoConvocatoriaRepository;

	@Autowired
	private ProductoRepository productoRepository;

	@Autowired
	private ProductoAlumnoRepository productoAlumnoRepository;
	
	@Autowired
	private ProductoAlumnoService productoAlumnoService;

	@Autowired
	private ImagenService imagenService;
	
	@Autowired
	private DocumentoRepository documentoRepository;
	
	@Autowired
	private DocumentoService documentoService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private com.taemoi.project.config.GradeProgressionConfig gradeProgressionConfig;

	@Autowired
	private com.taemoi.project.config.ExamEligibilityConfig examEligibilityConfig;

	@Autowired
	private com.taemoi.project.config.TarifaConfig tarifaConfig;

	@Autowired
	private com.taemoi.project.services.AlumnoDeporteService alumnoDeporteService;

	@Autowired
	private com.taemoi.project.repositories.AlumnoDeporteRepository alumnoDeporteRepository;

	@Autowired
	private AlumnoRetoDiarioLogRepository alumnoRetoDiarioLogRepository;

	/**
	 * Obtiene una página de todos los alumnos paginados.
	 *
	 * @param pageable Objeto Pageable para la paginación de resultados.
	 * @return Una página de objetos Alumno.
	 */
	@Override
	public Page<Alumno> obtenerTodosLosAlumnos(@NonNull Pageable pageable) {
		return alumnoRepository.findAll(pageable);
	}

	/**
	 * Obtiene una lista de todos los alumnos.
	 *
	 * @return Una lista de objetos Alumno.
	 */
	@Override
	public List<Alumno> obtenerTodosLosAlumnos() {
		return alumnoRepository.findAll();
	}

	/**
	 * Obtiene un alumno por su ID.
	 *
	 * @param id El ID del alumno a buscar.
	 * @return Un objeto Optional que contiene el alumno si se encuentra, de lo
	 *         contrario, un Optional vacío.
	 */
	@Override
	public Optional<Alumno> obtenerAlumnoPorId(@NonNull Long id) {
		return alumnoRepository.findById(id);
	}

	/**
	 * Obtiene un alumno DTO por su ID.
	 *
	 * @param id El ID del alumno a buscar.
	 * @return Un objeto Optional que contiene el alumno DTO si se encuentra, de lo
	 *         contrario, un Optional vacío.
	 */
	@Override
	public Optional<AlumnoDTO> obtenerAlumnoPorIdDTO(@NonNull Long id) {
		Optional<Alumno> optionalAlumno = obtenerAlumnoPorId(id);
		return optionalAlumno.map(AlumnoDTO::deAlumno);
	}
	
	@Override
	public List<Documento> obtenerDocumentosAlumno(Long alumnoId) {
	    Alumno alumno = alumnoRepository.findById(alumnoId)
	            .orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + alumnoId));
	    return documentoRepository.findByAlumnoIdOrderByNombreAsc(alumno.getId());
	}

	/**
	 * Obtiene una página de alumnos filtrados según los parámetros especificados.
	 *
	 * @param nombre      El nombre a filtrar.
	 * @param gradoId     El ID del grado a filtrar.
	 * @param categoriaId El ID de la categoría a filtrar.
	 * @param pageable    Objeto Pageable para la paginación de resultados.
	 * @return Una página de objetos Alumno que cumplen con los criterios de
	 *         búsqueda.
	 * @throws IllegalArgumentException Si no se proporciona al menos un criterio de
	 *                                  filtrado.
	 */
	@Override
	public Page<Alumno> obtenerAlumnosFiltrados(String nombre, Long gradoId, Long categoriaId, boolean incluirInactivos,
			@NonNull Pageable pageable) {
		return alumnoRepository.findAll(
				buildAlumnoSpecification(nombre, gradoId, categoriaId, incluirInactivos, false),
				pageable);
	}

	@Override
	public Page<Alumno> obtenerAlumnosFiltrados(String nombre, Long gradoId, Long categoriaId, boolean incluirInactivos,
			boolean aptoParaExamen, @NonNull Pageable pageable) {
		return alumnoRepository.findAll(
				buildAlumnoSpecification(nombre, gradoId, categoriaId, incluirInactivos, aptoParaExamen),
				pageable);
	}

	/**
	 * Obtiene una lista de alumnos filtrados según los parámetros especificados.
	 *
	 * @param nombre      El nombre a filtrar.
	 * @param gradoId     El ID del grado a filtrar.
	 * @param categoriaId El ID de la categoría a filtrar.
	 * @return Una lista de objetos Alumno que cumplen con los criterios de
	 *         búsqueda.
	 * @throws IllegalArgumentException Si no se proporciona al menos un criterio de
	 *                                  filtrado.
	 */
	@Override
	public List<Alumno> obtenerAlumnosFiltrados(String nombre, Long gradoId, Long categoriaId,
			boolean incluirInactivos) {
		return alumnoRepository.findAll(
				buildAlumnoSpecification(nombre, gradoId, categoriaId, incluirInactivos, false));
	}

	/**
	 * Crea un nuevo alumno.
	 *
	 * @param alumno El objeto Alumno a crear.
	 * @return El alumno creado.
	 */
	@Override
	public Alumno crearAlumno(@NonNull Alumno alumno) {
		// Save the alumno image first, if exists
		Imagen imagen = alumno.getFotoAlumno();
		if (imagen != null) {
			imagenRepository.save(imagen); // Save image
			alumno.setFotoAlumno(imagen); // Set the saved image to the alumno
		}

		// Generate and assign a unique student number (numeroExpediente)
		Integer maxNumeroExpediente = alumnoRepository.findMaxNumeroExpediente();
		alumno.setNumeroExpediente(maxNumeroExpediente == null ? 1 : maxNumeroExpediente + 1);

		boolean usaLegacy = alumno.getDeportes() == null || alumno.getDeportes().isEmpty();
		if (usaLegacy) {
			// Set the current weight date if the student is a competitor
			if (Boolean.TRUE.equals(alumno.getCompetidor())) {
				alumno.setFechaPeso(new Date());
			}

			if (Boolean.TRUE.equals(alumno.getTieneLicencia())) {
				alumno.setFechaLicencia(new Date());
			}

			if (alumno.getGrado() != null) {
				alumno.setFechaGrado(new Date()); // Fecha de grado especifica para el alumno
			}
		}

		// Save Alumno entity first
		Alumno alumnoGuardado = alumnoRepository.save(alumno);

		// If there is a Usuario associated, save it after Alumno
		if (alumnoGuardado.getUsuario() != null) {
			Usuario usuario = alumnoGuardado.getUsuario();
			usuario.setAlumno(alumnoGuardado); // Ensure user references saved alumno
			usuarioRepository.save(usuario); // Now save the Usuario
		}

		return alumnoGuardado;
	}

	@Override
	@Transactional
	public Alumno crearAlumnoDesdeDTO(@NonNull AlumnoDTO nuevoAlumnoDTO) {
		// Verificar si el Alumno ya existe
		Optional<Alumno> alumnoExistente = alumnoRepository.findByNif(nuevoAlumnoDTO.getNif());
		if (alumnoExistente.isPresent()) {
			throw new AlumnoDuplicadoException("El alumno con NIF " + nuevoAlumnoDTO.getNif() + " ya existe.");
		}

		// Crear nuevo Alumno
		Alumno nuevoAlumno = new Alumno();
		nuevoAlumno.setNombre(nuevoAlumnoDTO.getNombre());
		nuevoAlumno.setApellidos(nuevoAlumnoDTO.getApellidos());
		nuevoAlumno.setFechaNacimiento(nuevoAlumnoDTO.getFechaNacimiento());
		nuevoAlumno.setNif(nuevoAlumnoDTO.getNif());
		nuevoAlumno.setDireccion(nuevoAlumnoDTO.getDireccion());
		String normalizedEmail = EmailUtils.normalizeEmail(nuevoAlumnoDTO.getEmail());
		nuevoAlumno.setEmail(normalizedEmail);
		nuevoAlumno.setTelefono(nuevoAlumnoDTO.getTelefono());
		nuevoAlumno.setTelefono2(nuevoAlumnoDTO.getTelefono2());
		nuevoAlumno.setObservaciones(nuevoAlumnoDTO.getObservaciones());
		nuevoAlumno.setTieneDiscapacidad(Optional.ofNullable(nuevoAlumnoDTO.getTieneDiscapacidad()).orElse(false));

		// Asignar AutorizacionWeb, si no está definida por defecto a true
		nuevoAlumno.setAutorizacionWeb(
				nuevoAlumnoDTO.getAutorizacionWeb() != null ? nuevoAlumnoDTO.getAutorizacionWeb() : true);

		// LEGACY CODE: Only set these fields if NOT using multi-sport mode
		// In multi-sport mode, all these fields are per-sport in AlumnoDeporte
		boolean isMultiSportMode = nuevoAlumnoDTO.getDeportesInicial() != null && !nuevoAlumnoDTO.getDeportesInicial().isEmpty();

		if (!isMultiSportMode) {
			// Legacy single-sport mode
			nuevoAlumno.setTipoTarifa(nuevoAlumnoDTO.getTipoTarifa());
			nuevoAlumno.setRolFamiliar(nuevoAlumnoDTO.getRolFamiliar() != null ? nuevoAlumnoDTO.getRolFamiliar() : RolFamiliar.NINGUNO);
			nuevoAlumno.setGrupoFamiliar(nuevoAlumnoDTO.getGrupoFamiliar());
			nuevoAlumno.setDeporte(nuevoAlumnoDTO.getDeporte());

			// Asignar CuantiaTarifa si no está definida o es menor o igual a 0
			if (nuevoAlumnoDTO.getCuantiaTarifa() == null || nuevoAlumnoDTO.getCuantiaTarifa() <= 0) {
				nuevoAlumno.setCuantiaTarifa(asignarCuantiaTarifa(nuevoAlumnoDTO.getTipoTarifa(), nuevoAlumno.getRolFamiliar()));
			} else {
				nuevoAlumno.setCuantiaTarifa(nuevoAlumnoDTO.getCuantiaTarifa());
			}

			// Asignar Competidor, Peso y FechaPeso si es aplicable
			nuevoAlumno.setCompetidor(nuevoAlumnoDTO.getCompetidor());
			if (Boolean.TRUE.equals(nuevoAlumno.getCompetidor())) {
				nuevoAlumno.setPeso(nuevoAlumnoDTO.getPeso());
				nuevoAlumno.setFechaPeso(new Date());
				// DEPRECATED: Categoria is now per-sport (in AlumnoDeporte)
			// nuevoAlumno.setCategoria(asignarCategoriaSegunEdad(FechaUtils.calcularEdad(nuevoAlumnoDTO.getFechaNacimiento())));
			}

			// Configurar Licencia
			nuevoAlumno.setTieneLicencia(nuevoAlumnoDTO.getTieneLicencia());
			if (Boolean.TRUE.equals(nuevoAlumno.getTieneLicencia())) {
				nuevoAlumno.setNumeroLicencia(nuevoAlumnoDTO.getNumeroLicencia());
				nuevoAlumno.setFechaLicencia(new Date());
			}

			if (nuevoAlumnoDTO.getGrado() != null && !nuevoAlumnoDTO.getGrado().isEmpty()) {
				// Buscar y asignar el grado seleccionado
				TipoGrado tipoGradoSeleccionado = TipoGrado.valueOf(nuevoAlumnoDTO.getGrado());
				Grado gradoSeleccionado = gradoRepository.findByTipoGrado(tipoGradoSeleccionado);
				nuevoAlumno.setGrado(gradoSeleccionado);
			} else {
				// Si no se seleccionó un grado, puedes asignar un valor por defecto (ejemplo: Blanco)
				Grado gradoPorDefecto = gradoRepository.findByTipoGrado(TipoGrado.BLANCO);
				nuevoAlumno.setGrado(gradoPorDefecto);
			}

			// **Asignar fecha de grado actual al alumno**
			nuevoAlumno.setFechaGrado(new Date());

			// Legacy: Calculate aptoParaExamen for single-sport mode
			if (nuevoAlumnoDTO.getAptoParaExamen() != null) {
				nuevoAlumno.setAptoParaExamen(nuevoAlumnoDTO.getAptoParaExamen());
			} else {
				nuevoAlumno.setAptoParaExamen(esAptoParaExamen(nuevoAlumno));
			}
		} else {
			// Multi-sport mode: explicitly set all legacy fields to null
			nuevoAlumno.setTipoTarifa(null);
			nuevoAlumno.setCuantiaTarifa(null);
			nuevoAlumno.setRolFamiliar(null);
			nuevoAlumno.setGrupoFamiliar(null);
			nuevoAlumno.setDeporte(null);
			nuevoAlumno.setCompetidor(null);
			nuevoAlumno.setPeso(null);
			nuevoAlumno.setFechaPeso(null);
			nuevoAlumno.setTieneLicencia(null);
			nuevoAlumno.setNumeroLicencia(null);
			nuevoAlumno.setFechaLicencia(null);
			nuevoAlumno.setGrado(null);
			nuevoAlumno.setFechaGrado(null);
			nuevoAlumno.setAptoParaExamen(null);
			// Note: Categoria is set separately for competitors in AlumnoDeporte
		}

		// Asignar imagen si se proporcionó
		if (nuevoAlumnoDTO.getFotoAlumno() != null) {
			// Guardar la imagen antes de asignarla al alumno
			Imagen imagenGuardada = imagenRepository.save(nuevoAlumnoDTO.getFotoAlumno());
			nuevoAlumno.setFotoAlumno(imagenGuardada);
		}

		// Asignar fecha de alta
		Date fechaAlta = nuevoAlumnoDTO.getFechaAlta() != null ? nuevoAlumnoDTO.getFechaAlta() : new Date();
		nuevoAlumno.setFechaAlta(fechaAlta);

		// Asignar fecha de alta inicial (solo se establece una vez en la creación)
		nuevoAlumno.setFechaAltaInicial(nuevoAlumnoDTO.getFechaAltaInicial() != null ? nuevoAlumnoDTO.getFechaAltaInicial() : fechaAlta);

		// Note: License handling already done above (lines 394-399), duplicate code removed

		// Generar y asignar el número de expediente
		Integer maxNumeroExpediente = alumnoRepository.findMaxNumeroExpediente(); // Asegúrate de tener este método en
																					// tu repositorio
		nuevoAlumno.setNumeroExpediente(maxNumeroExpediente == null ? 1 : maxNumeroExpediente + 1);

		// Guardar primero el Alumno
		Alumno alumnoGuardado = alumnoRepository.save(nuevoAlumno);

		// NEW: Multi-sport support
		if (nuevoAlumnoDTO.getDeportesInicial() != null && !nuevoAlumnoDTO.getDeportesInicial().isEmpty()) {
			// Multi-sport mode: crear deportes desde la lista
			logger.info("Creando alumno con {} deportes", nuevoAlumnoDTO.getDeportesInicial().size());

			for (com.taemoi.project.dtos.request.AlumnoDeporteCreacionDTO deporteDTO : nuevoAlumnoDTO.getDeportesInicial()) {
				TipoGrado gradoTipo = null;
				if (deporteDTO.getGrado() != null && !deporteDTO.getGrado().isEmpty()) {
					gradoTipo = TipoGrado.valueOf(deporteDTO.getGrado());
				}

				Date fechaAltaDeporte = deporteDTO.getFechaAlta() != null ? deporteDTO.getFechaAlta() : new Date();
				Date fechaAltaInicialDeporte = deporteDTO.getFechaAltaInicial() != null ? deporteDTO.getFechaAltaInicial() : fechaAltaDeporte;
				Date fechaGradoDeporte = deporteDTO.getFechaGrado() != null ? deporteDTO.getFechaGrado() : new Date();

				// Use the new method that accepts per-sport fields
				alumnoDeporteService.agregarDeporteAAlumnoCompleto(
					alumnoGuardado.getId(),
					deporteDTO.getDeporte(),
					gradoTipo,
					fechaAltaDeporte,
					fechaAltaInicialDeporte,
					fechaGradoDeporte,
					deporteDTO.getTipoTarifa(),
					deporteDTO.getCuantiaTarifa(),
					deporteDTO.getRolFamiliar(),
					deporteDTO.getGrupoFamiliar(),
					deporteDTO.getCategoria(),
					deporteDTO.getCompetidor(),
					deporteDTO.getFechaAltaCompeticion(),
					deporteDTO.getFechaAltaCompetidorInicial(),
					deporteDTO.getPeso(),
					deporteDTO.getFechaPeso(),
					deporteDTO.getTieneLicencia(),
					deporteDTO.getNumeroLicencia(),
					deporteDTO.getFechaLicencia()
				);
			}
		} else if (nuevoAlumnoDTO.getDeporte() != null) {
			// Backward compatibility: single-sport mode
			logger.info("Creando alumno con deporte único (modo legacy)");
			// El deporte único ya está asignado en nuevoAlumno.setDeporte() (línea 324)
			// No se necesita acción adicional aquí ya que el sistema legacy aún existe
		} else {
			throw new IllegalArgumentException("Al menos un deporte debe ser especificado");
		}

	    boolean tieneLicencia = Boolean.TRUE.equals(nuevoAlumno.getTieneLicencia());
	    if (!tieneLicencia && alumnoGuardado.getDeportes() != null) {
	    	tieneLicencia = alumnoGuardado.getDeportes().stream()
	    			.anyMatch(ad -> Boolean.TRUE.equals(ad.getTieneLicencia()));
	    }
	    if (tieneLicencia) {
	        productoAlumnoService.crearAltaLicenciaFederativa(alumnoGuardado);
	    }

	    asignarMensualidadGeneralSiCorresponde(alumnoGuardado);

		// Finalmente, retornar el Alumno guardado
		return alumnoGuardado;
	}

    @Override
    @Transactional
    public Documento agregarDocumentoAAlumno(Long alumnoId, MultipartFile archivo) {
        Alumno alumno = alumnoRepository.findById(alumnoId)
                .orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + alumnoId));

        try {
            Documento documento = documentoService.guardarDocumento(alumno, archivo);
            alumno.getDocumentos().add(documento);
            alumnoRepository.save(alumno);
            return documento;
        } catch (IOException e) {
            throw new RuntimeException("Error al guardar el documento: " + e.getMessage(), e);
        }
    }

	/**
	 * Actualiza un alumno existente.
	 *
	 * @param id                   El ID del alumno a actualizar.
	 * @param alumnoActualizado    El objeto AlumnoDTO con los datos actualizados.
	 * @param nuevaFechaNacimiento La nueva fecha de nacimiento del alumno.
	 * @param imagen               La nueva imagen del alumno, si se proporciona.
	 * @return El alumno actualizado.
	 * @throws RuntimeException Si no se encuentra el alumno con el ID especificado.
	 */
	@Override
	@Transactional
	public Alumno actualizarAlumno(@NonNull Long id, AlumnoDTO alumnoActualizado, Date nuevaFechaNacimiento,
			MultipartFile nuevaImagen) {
		Optional<Alumno> optionalAlumno = alumnoRepository.findById(id);
		if (optionalAlumno.isPresent()) {
			Alumno alumnoExistente = optionalAlumno.get();
			boolean usaLegacy = alumnoExistente.getDeportes() == null || alumnoExistente.getDeportes().isEmpty();

			// Actualizar datos generales del alumno
			alumnoExistente.setNombre(alumnoActualizado.getNombre());
			alumnoExistente.setApellidos(alumnoActualizado.getApellidos());
			alumnoExistente.setFechaNacimiento(nuevaFechaNacimiento);

			if (usaLegacy) {
				// Obtener el grado actual
				Grado gradoActual = alumnoExistente.getGrado();

				// Buscar el nuevo grado (si se actualiza)
				Grado nuevoGrado = gradoRepository.findByTipoGrado(
						alumnoActualizado.getGrado() != null ? TipoGrado.valueOf(alumnoActualizado.getGrado()) : null);

				// Si el grado cambia, actualizar la fecha de grado
				if (nuevoGrado != null && !nuevoGrado.equals(gradoActual)) {
					alumnoExistente.setGrado(nuevoGrado);
					alumnoExistente.setFechaGrado(new Date());
				}
			}

			// Actualizar otros campos del alumno
			alumnoExistente.setNif(alumnoActualizado.getNif());
			alumnoExistente.setDireccion(alumnoActualizado.getDireccion());
			String normalizedEmail = EmailUtils.normalizeEmail(alumnoActualizado.getEmail());
			Usuario usuario = alumnoExistente.getUsuario();
			if (usuario != null && normalizedEmail != null) {
				String usuarioEmail = usuario.getEmail();
				if (usuarioEmail == null || !usuarioEmail.equals(normalizedEmail)) {
					Optional<Usuario> usuarioConEmail = usuarioRepository.findByEmailIgnoreCase(normalizedEmail);
					if (usuarioConEmail.isPresent() && !usuarioConEmail.get().getId().equals(usuario.getId())) {
						throw new IllegalArgumentException("El correo electronico ya esta asociado a otro usuario.");
					}
					usuario.setEmail(normalizedEmail);
					usuarioRepository.save(usuario);
				}
			}
			alumnoExistente.setEmail(normalizedEmail);
			alumnoExistente.setTelefono(alumnoActualizado.getTelefono());
			alumnoExistente.setTelefono2(alumnoActualizado.getTelefono2());
			if (alumnoActualizado.getObservaciones() != null) {
				alumnoExistente.setObservaciones(alumnoActualizado.getObservaciones());
			}

			if (usaLegacy) {
				alumnoExistente.setTipoTarifa(alumnoActualizado.getTipoTarifa());
				alumnoExistente.setRolFamiliar(alumnoActualizado.getRolFamiliar() != null
						? alumnoActualizado.getRolFamiliar()
						: RolFamiliar.NINGUNO);
				alumnoExistente.setGrupoFamiliar(alumnoActualizado.getGrupoFamiliar());
				alumnoExistente.setDeporte(alumnoActualizado.getDeporte());
			}

			if (alumnoActualizado.getFechaAlta() != null) {
				alumnoExistente.setFechaAlta(alumnoActualizado.getFechaAlta());
			}

			// Actualizar fechaAltaInicial solo si se proporciona un valor
			if (alumnoActualizado.getFechaAltaInicial() != null) {
				alumnoExistente.setFechaAltaInicial(alumnoActualizado.getFechaAltaInicial());
			}

			alumnoExistente.setFechaBaja(alumnoActualizado.getFechaBaja());
			alumnoExistente.setAutorizacionWeb(alumnoActualizado.getAutorizacionWeb());

			if (usaLegacy) {
				alumnoExistente.setTieneLicencia(Optional.ofNullable(alumnoActualizado.getTieneLicencia()).orElse(false));
				if (alumnoActualizado.getTieneLicencia() != null && alumnoActualizado.getTieneLicencia()) {
					alumnoExistente.setNumeroLicencia(alumnoActualizado.getNumeroLicencia());
					alumnoExistente.setFechaLicencia(alumnoActualizado.getFechaLicencia());
				}
			}

			alumnoExistente.setTieneDiscapacidad(Optional.ofNullable(alumnoActualizado.getTieneDiscapacidad()).orElse(false));

			if (usaLegacy) {
				// Actualizar los campos relacionados con "competidor"
				alumnoExistente.setCompetidor(Optional.ofNullable(alumnoActualizado.getCompetidor()).orElse(false));
				if (alumnoActualizado.getCompetidor() != null && alumnoActualizado.getCompetidor()) {
					// Si es competidor, actualizar el peso y la fecha de peso
					alumnoExistente.setPeso(alumnoActualizado.getPeso());
					alumnoExistente.setFechaPeso(alumnoActualizado.getFechaPeso());
				}
			}

			if (usaLegacy) {
				if (alumnoActualizado.getAptoParaExamen() != null) {
					alumnoExistente.setAptoParaExamen(alumnoActualizado.getAptoParaExamen());
				} else {
					// Si no se asigna manualmente, calcular si es apto automaticamente
					alumnoExistente.setAptoParaExamen(esAptoParaExamen(alumnoExistente));
				}
			}

			// Manejo de la imagen del alumno
			try {
				if (nuevaImagen != null && "null".equals(nuevaImagen.getOriginalFilename())) {
					// Si la imagen enviada es 'null', eliminar la imagen existente
					Imagen imagenAnterior = alumnoExistente.getFotoAlumno();
					if (imagenAnterior != null) {
						// Eliminar la imagen del sistema de archivos y de la base de datos
						imagenService.eliminarImagenDeSistema(imagenAnterior);
						imagenRepository.delete(imagenAnterior);
						alumnoExistente.setFotoAlumno(null); // Remover referencia de la imagen
					}
				} else if (nuevaImagen != null && !nuevaImagen.isEmpty()) {
					// Si hay una nueva imagen, reemplazar la imagen existente
					Imagen imagenAnterior = alumnoExistente.getFotoAlumno();
					if (imagenAnterior != null) {
						// Eliminar la imagen anterior
						imagenService.eliminarImagenDeSistema(imagenAnterior);
						imagenRepository.delete(imagenAnterior);
					}

					// Guardar la nueva imagen y asignarla al alumno
					Imagen nuevaImagenGuardada = imagenService.guardarImagen(nuevaImagen);
					alumnoExistente.setFotoAlumno(nuevaImagenGuardada);
				}
			} catch (IOException e) {
				throw new RuntimeException("Error al procesar la imagen", e);
			}

			if (usaLegacy) {
				// Si no se especifica una cuantia de tarifa o es invalida, se asigna una por defecto
				if (alumnoActualizado.getCuantiaTarifa() == null || alumnoActualizado.getCuantiaTarifa() <= 0) {
					alumnoExistente.setCuantiaTarifa(asignarCuantiaTarifa(alumnoActualizado.getTipoTarifa(), alumnoExistente.getRolFamiliar()));
				} else {
					alumnoExistente.setCuantiaTarifa(alumnoActualizado.getCuantiaTarifa());
				}
			}

			// Guardar los cambios en el alumno en la base de datos
			return alumnoRepository.save(alumnoExistente);
		} else {
			throw new RuntimeException("No se encontro el alumno con ID: " + id);
		}
	}

	@Override
	@Transactional
	public Alumno actualizarObservaciones(@NonNull Long id, String observaciones) {
		Alumno alumno = alumnoRepository.findById(id)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + id));
		alumno.setObservaciones(observaciones);
		return alumnoRepository.save(alumno);
	}

	/**
	 * Elimina la imagen asociada a un alumno especificado por su ID.
	 * 
	 * @param id El ID del alumno cuya imagen se eliminará.
	 * @throws RuntimeException Si no se encuentra el alumno con el ID especificado
	 *                          o si el alumno no tiene una imagen asociada.
	 */
	@Override
	public void eliminarImagenAlumno(@NonNull Long id) {
		Optional<Alumno> optionalAlumno = alumnoRepository.findById(id);
		if (optionalAlumno.isPresent()) {
			Alumno alumno = optionalAlumno.get();
			Imagen imagen = alumno.getFotoAlumno();
			if (imagen != null) {

				imagenService.eliminarImagenDeSistema(imagen);

				alumno.setFotoAlumno(null);
				alumnoRepository.save(alumno);
				imagenRepository.delete(imagen);
			} else {
				throw new RuntimeException("El alumno no tiene una imagen asociada.");
			}
		} else {
			throw new RuntimeException("No se encontró el alumno con ID: " + id);
		}
	}
	
    @Override
    public void eliminarDocumentoDeAlumno(Long alumnoId, Long documentoId) {
        Documento documento = obtenerDocumentoDeAlumno(alumnoId, documentoId);
        Alumno alumno = documento.getAlumno();

        documentoService.eliminarDocumento(documento);

        if (alumno != null) {
            alumno.getDocumentos().remove(documento);
            alumnoRepository.save(alumno);
        }
    }

	@Override
	public Documento obtenerDocumentoDeAlumno(Long alumnoId, Long documentoId) {
		logger.info("Getting document {} for alumno {}", documentoId, alumnoId);

		// Verificar que el alumno existe
		if (!alumnoRepository.existsById(alumnoId)) {
			logger.error("Alumno not found with ID: {}", alumnoId);
			throw new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + alumnoId);
		}

		Documento documento = documentoRepository.findById(documentoId)
				.orElseThrow(() -> {
					logger.error("Document not found with ID: {}", documentoId);
					return new RuntimeException("Documento no encontrado con ID: " + documentoId);
				});

		if (documento.getAlumno() == null) {
			logger.error("Document {} has no associated alumno (null)", documentoId);
			throw new RuntimeException("El documento no tiene un alumno asociado.");
		}

		if (!documento.getAlumno().getId().equals(alumnoId)) {
			logger.error("Document {} belongs to alumno {} but was requested for alumno {}",
					documentoId, documento.getAlumno().getId(), alumnoId);
			throw new RuntimeException("El documento no pertenece a este alumno.");
		}

		logger.info("Document {} successfully retrieved for alumno {}", documentoId, alumnoId);
		return documento;
	}

	@Override
	@Transactional(readOnly = true)
	public RetoDiarioEstadoDTO obtenerEstadoRetoDiario(Long alumnoId) {
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + alumnoId));

		LocalDate hoy = LocalDate.now();
		LocalDate fechaCompletado = toLocalDate(alumno.getFechaRetoDiarioCompletado());
		int rachaPersistida = alumno.getRachaRetoDiario() != null ? Math.max(0, alumno.getRachaRetoDiario()) : 0;
		boolean completadoHoy = fechaCompletado != null && fechaCompletado.equals(hoy);
		int rachaActual = calcularRachaActual(rachaPersistida, fechaCompletado, hoy);
		long nextResetAtEpochMs = calcularProximoResetEpochMs();

		return new RetoDiarioEstadoDTO(
				rachaActual,
				completadoHoy,
				fechaCompletado != null ? fechaCompletado.toString() : null,
				nextResetAtEpochMs);
	}

	@Override
	@Transactional
	public RetoDiarioEstadoDTO completarRetoDiario(Long alumnoId) {
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + alumnoId));

		LocalDate hoy = LocalDate.now();
		LocalDate fechaAnterior = toLocalDate(alumno.getFechaRetoDiarioCompletado());
		int rachaAnterior = alumno.getRachaRetoDiario() != null ? Math.max(0, alumno.getRachaRetoDiario()) : 0;
		int nuevaRacha;

		if (fechaAnterior != null && fechaAnterior.equals(hoy)) {
			nuevaRacha = calcularRachaActual(rachaAnterior, fechaAnterior, hoy);
		} else if (fechaAnterior != null && fechaAnterior.equals(hoy.minusDays(1))) {
			nuevaRacha = rachaAnterior + 1;
		} else {
			nuevaRacha = 1;
		}

		alumno.setFechaRetoDiarioCompletado(Date.from(hoy.atStartOfDay(ZoneId.systemDefault()).toInstant()));
		alumno.setRachaRetoDiario(nuevaRacha);
		alumnoRepository.save(alumno);
		registrarLogRetoDiarioSiNoExiste(alumno, hoy);

		return new RetoDiarioEstadoDTO(nuevaRacha, true, hoy.toString(), calcularProximoResetEpochMs());
	}

	@Override
	@Transactional
	public RetoDiarioRankingSemanalResponse obtenerRankingSemanalRetoDiario(Long alumnoId, Deporte deporte,
			Integer limit) {
		if (alumnoId == null) {
			throw new IllegalArgumentException("El alumno es obligatorio.");
		}
		if (deporte == null) {
			throw new IllegalArgumentException("El deporte es obligatorio.");
		}

		Alumno alumnoActual = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + alumnoId));
		if (!Boolean.TRUE.equals(alumnoActual.getActivo())) {
			throw new IllegalArgumentException("El alumno seleccionado no está activo.");
		}
		if (!alumnoDeporteRepository.existsByAlumnoIdAndDeporteAndActivoTrue(alumnoId, deporte)) {
			throw new IllegalArgumentException("El deporte seleccionado no está activo para el alumno.");
		}

		int limiteTop = normalizarLimiteTop(limit);
		LocalDate hoy = LocalDate.now();
		WeekFields weekFields = WeekFields.ISO;
		int anioIso = hoy.get(weekFields.weekBasedYear());
		int semanaIso = hoy.get(weekFields.weekOfWeekBasedYear());

		ejecutarBackfillSemanalSiNecesario(anioIso, semanaIso, hoy);

		List<AlumnoDeporte> participantesDeporte = alumnoDeporteRepository.findActivosConAlumnoActivoByDeporte(deporte);
		List<Long> alumnoIds = participantesDeporte.stream()
				.map(AlumnoDeporte::getAlumno)
				.filter(alumno -> alumno != null && alumno.getId() != null)
				.map(Alumno::getId)
				.distinct()
				.toList();

		Map<Long, AlumnoRetoDiarioLogRepository.AlumnoRetoDiarioScoreProjection> puntuacionesPorAlumno = new HashMap<>();
		if (!alumnoIds.isEmpty()) {
			List<AlumnoRetoDiarioLogRepository.AlumnoRetoDiarioScoreProjection> puntuaciones = alumnoRetoDiarioLogRepository
					.obtenerPuntuacionesSemana(anioIso, semanaIso, alumnoIds);
			for (AlumnoRetoDiarioLogRepository.AlumnoRetoDiarioScoreProjection puntuacion : puntuaciones) {
				puntuacionesPorAlumno.put(puntuacion.getAlumnoId(), puntuacion);
			}
		}

		List<RankingSemanaParticipante> participantesRanking = participantesDeporte.stream()
				.map(AlumnoDeporte::getAlumno)
				.filter(alumno -> alumno != null && alumno.getId() != null)
				.collect(Collectors.toMap(
						Alumno::getId,
						alumno -> construirParticipanteRanking(alumno, alumnoId, puntuacionesPorAlumno.get(alumno.getId())),
						(p1, p2) -> p1))
				.values().stream()
				.collect(Collectors.toCollection(ArrayList::new));

		aplicarDesambiguacionAliasDuplicados(participantesRanking);

		List<RankingSemanaParticipante> ranking = participantesRanking.stream()
				.sorted(Comparator
						.comparingInt(RankingSemanaParticipante::getDiasCompletados).reversed()
						.thenComparing(RankingSemanaParticipante::getUltimaFechaCompletado,
								Comparator.nullsLast(Comparator.reverseOrder()))
						.thenComparing(RankingSemanaParticipante::getAlias, String.CASE_INSENSITIVE_ORDER))
				.toList();

		asignarPosicionesDensas(ranking);

		List<RetoDiarioRankingItemResponse> top = ranking.stream()
				.limit(limiteTop)
				.map(item -> new RetoDiarioRankingItemResponse(
						item.getPosicion(),
						item.getAlias(),
						item.getDiasCompletados(),
						item.getEsUsuarioActual()))
				.toList();

		RankingSemanaParticipante participanteActual = ranking.stream()
				.filter(RankingSemanaParticipante::getEsUsuarioActual)
				.findFirst()
				.orElseGet(() -> new RankingSemanaParticipante(
						alumnoId,
						construirAliasRanking(alumnoActual),
						obtenerInicialSegundoApellido(alumnoActual),
						0,
						null,
						true));

		Integer diasParaSuperarSiguiente = calcularDiasParaSuperarSiguiente(participanteActual, ranking);
		RetoDiarioRankingMiPosicionResponse miPosicion = new RetoDiarioRankingMiPosicionResponse(
				participanteActual.getPosicion(),
				participanteActual.getAlias(),
				participanteActual.getDiasCompletados(),
				diasParaSuperarSiguiente);

		return new RetoDiarioRankingSemanalResponse(
				deporte.name(),
				anioIso,
				semanaIso,
				ranking.size(),
				top,
				miPosicion);
	}

	private void registrarLogRetoDiarioSiNoExiste(Alumno alumno, LocalDate fechaLocal) {
		if (alumno == null || alumno.getId() == null || fechaLocal == null) {
			return;
		}
		if (alumnoRetoDiarioLogRepository.existsByAlumnoIdAndFechaCompletado(alumno.getId(), fechaLocal)) {
			return;
		}
		WeekFields weekFields = WeekFields.ISO;
		AlumnoRetoDiarioLog log = new AlumnoRetoDiarioLog();
		log.setAlumno(alumno);
		log.setFechaCompletado(fechaLocal);
		log.setAnioIso(fechaLocal.get(weekFields.weekBasedYear()));
		log.setSemanaIso(fechaLocal.get(weekFields.weekOfWeekBasedYear()));
		alumnoRetoDiarioLogRepository.save(log);
	}

	private void ejecutarBackfillSemanalSiNecesario(int anioIso, int semanaIso, LocalDate hoy) {
		if (alumnoRetoDiarioLogRepository.existsByAnioIsoAndSemanaIso(anioIso, semanaIso)) {
			return;
		}

		LocalDate inicioSemana = obtenerInicioSemanaIso(hoy);
		LocalDate finSemana = inicioSemana.plusDays(6);
		List<Alumno> alumnosActivos = alumnoRepository.findByActivoTrue();

		for (Alumno alumno : alumnosActivos) {
			if (alumno == null || alumno.getId() == null) {
				continue;
			}
			LocalDate fechaFinRacha = toLocalDate(alumno.getFechaRetoDiarioCompletado());
			int racha = alumno.getRachaRetoDiario() != null ? Math.max(0, alumno.getRachaRetoDiario()) : 0;
			if (fechaFinRacha == null || racha <= 0) {
				continue;
			}

			LocalDate fechaInicioRacha = fechaFinRacha.minusDays(racha - 1L);
			LocalDate inicioInterseccion = fechaInicioRacha.isAfter(inicioSemana) ? fechaInicioRacha : inicioSemana;
			LocalDate finInterseccion = fechaFinRacha.isBefore(finSemana) ? fechaFinRacha : finSemana;

			if (inicioInterseccion.isAfter(finInterseccion)) {
				continue;
			}

			for (LocalDate fecha = inicioInterseccion; !fecha.isAfter(finInterseccion); fecha = fecha.plusDays(1)) {
				if (fecha.isAfter(hoy)) {
					continue;
				}
				registrarLogRetoDiarioSiNoExiste(alumno, fecha);
			}
		}
	}

	private LocalDate obtenerInicioSemanaIso(LocalDate fecha) {
		WeekFields weekFields = WeekFields.ISO;
		return fecha.with(weekFields.dayOfWeek(), 1);
	}

	private int normalizarLimiteTop(Integer limit) {
		if (limit == null) {
			return 10;
		}
		return Math.max(1, Math.min(10, limit));
	}

	private RankingSemanaParticipante construirParticipanteRanking(
			Alumno alumno,
			Long alumnoActualId,
			AlumnoRetoDiarioLogRepository.AlumnoRetoDiarioScoreProjection puntuacion) {
		int dias = 0;
		LocalDate ultimaFecha = null;
		if (puntuacion != null) {
			dias = puntuacion.getDiasCompletados() != null ? Math.max(0, puntuacion.getDiasCompletados().intValue()) : 0;
			ultimaFecha = puntuacion.getUltimaFechaCompletado();
		}
		boolean esUsuarioActual = alumnoActualId != null && alumnoActualId.equals(alumno.getId());
		return new RankingSemanaParticipante(
				alumno.getId(),
				construirAliasRanking(alumno),
				obtenerInicialSegundoApellido(alumno),
				dias,
				ultimaFecha,
				esUsuarioActual);
	}

	private void aplicarDesambiguacionAliasDuplicados(List<RankingSemanaParticipante> participantes) {
		if (participantes == null || participantes.isEmpty()) {
			return;
		}

		Map<String, Integer> conteoAliasBase = new HashMap<>();
		for (RankingSemanaParticipante participante : participantes) {
			String clave = normalizarClaveAlias(participante.getAliasBase());
			conteoAliasBase.put(clave, conteoAliasBase.getOrDefault(clave, 0) + 1);
		}

		for (RankingSemanaParticipante participante : participantes) {
			String clave = normalizarClaveAlias(participante.getAliasBase());
			if (conteoAliasBase.getOrDefault(clave, 0) > 1) {
				participante.aplicarInicialSegundoApellido();
			}
		}
	}

	private String normalizarClaveAlias(String aliasBase) {
		if (aliasBase == null) {
			return "";
		}
		return aliasBase.trim().toLowerCase(Locale.ROOT);
	}

	private void asignarPosicionesDensas(List<RankingSemanaParticipante> ranking) {
		int posicionActual = 0;
		Integer diasPrevios = null;
		for (RankingSemanaParticipante participante : ranking) {
			if (diasPrevios == null || participante.getDiasCompletados() != diasPrevios) {
				posicionActual++;
				diasPrevios = participante.getDiasCompletados();
			}
			participante.setPosicion(posicionActual);
		}
	}

	private Integer calcularDiasParaSuperarSiguiente(RankingSemanaParticipante participanteActual,
			List<RankingSemanaParticipante> rankingCompleto) {
		if (participanteActual == null || participanteActual.getPosicion() == null || participanteActual.getPosicion() <= 1) {
			return null;
		}

		int diasActuales = participanteActual.getDiasCompletados();
		Integer diasSiguienteSuperior = rankingCompleto.stream()
				.map(RankingSemanaParticipante::getDiasCompletados)
				.filter(dias -> dias > diasActuales)
				.min(Integer::compareTo)
				.orElse(null);
		if (diasSiguienteSuperior == null) {
			return null;
		}
		return (diasSiguienteSuperior - diasActuales) + 1;
	}

	private String construirAliasRanking(Alumno alumno) {
		if (alumno == null) {
			return "Alumno";
		}
		String primerNombre = capitalizarNombreSimple(extraerPrimerSegmento(alumno.getNombre()));
		String primerApellido = capitalizarNombreSimple(extraerPrimerSegmento(alumno.getApellidos()));

		if (primerNombre.isBlank()) {
			primerNombre = "Alumno";
		}

		return primerApellido.isBlank() ? primerNombre : primerNombre + " " + primerApellido;
	}

	private String obtenerInicialSegundoApellido(Alumno alumno) {
		if (alumno == null || alumno.getApellidos() == null) {
			return "";
		}

		String apellidos = alumno.getApellidos().trim();
		if (apellidos.isBlank()) {
			return "";
		}

		String[] partes = apellidos.split("\\s+");
		if (partes.length < 2) {
			return "";
		}

		String segundoApellido = capitalizarNombreSimple(partes[1]);
		if (segundoApellido.isBlank()) {
			return "";
		}

		return segundoApellido.substring(0, 1).toUpperCase(Locale.ROOT);
	}

	private String extraerPrimerSegmento(String texto) {
		if (texto == null) {
			return "";
		}
		String limpio = texto.trim();
		if (limpio.isBlank()) {
			return "";
		}
		return limpio.split("\\s+")[0];
	}

	private String capitalizarNombreSimple(String texto) {
		if (texto == null || texto.isBlank()) {
			return "";
		}

		String valor = texto.trim().toLowerCase(Locale.ROOT);
		StringBuilder resultado = new StringBuilder(valor.length());
		boolean capitalizar = true;

		for (int i = 0; i < valor.length(); i++) {
			char caracter = valor.charAt(i);
			if (Character.isLetter(caracter)) {
				resultado.append(capitalizar ? Character.toUpperCase(caracter) : caracter);
				capitalizar = false;
			} else {
				resultado.append(caracter);
				capitalizar = caracter == '-' || caracter == '\'';
			}
		}

		return resultado.toString();
	}

	private int calcularRachaActual(int rachaPersistida, LocalDate fechaCompletado, LocalDate hoy) {
		if (fechaCompletado == null) {
			return 0;
		}
		if (fechaCompletado.equals(hoy) || fechaCompletado.equals(hoy.minusDays(1))) {
			return Math.max(0, rachaPersistida);
		}
		return 0;
	}

	private long calcularProximoResetEpochMs() {
		ZoneId zoneId = ZoneId.systemDefault();
		return LocalDate.now(zoneId)
				.plusDays(1)
				.atStartOfDay(zoneId)
				.toInstant()
				.toEpochMilli();
	}

	private LocalDate toLocalDate(Date fecha) {
		if (fecha == null) {
			return null;
		}
		if (fecha instanceof java.sql.Date sqlDate) {
			return sqlDate.toLocalDate();
		}
		return java.time.Instant.ofEpochMilli(fecha.getTime())
				.atZone(ZoneId.systemDefault())
				.toLocalDate();
	}

	private static class RankingSemanaParticipante {
		private final Long alumnoId;
		private final String aliasBase;
		private final String inicialSegundoApellido;
		private String alias;
		private final int diasCompletados;
		private final LocalDate ultimaFechaCompletado;
		private final boolean esUsuarioActual;
		private Integer posicion;

		RankingSemanaParticipante(Long alumnoId, String aliasBase, String inicialSegundoApellido, int diasCompletados,
				LocalDate ultimaFechaCompletado,
				boolean esUsuarioActual) {
			this.alumnoId = alumnoId;
			this.aliasBase = aliasBase;
			this.inicialSegundoApellido = inicialSegundoApellido == null ? "" : inicialSegundoApellido;
			this.alias = aliasBase;
			this.diasCompletados = diasCompletados;
			this.ultimaFechaCompletado = ultimaFechaCompletado;
			this.esUsuarioActual = esUsuarioActual;
		}

		public Long getAlumnoId() {
			return alumnoId;
		}

		public String getAlias() {
			return alias;
		}

		public String getAliasBase() {
			return aliasBase;
		}

		public int getDiasCompletados() {
			return diasCompletados;
		}

		public LocalDate getUltimaFechaCompletado() {
			return ultimaFechaCompletado;
		}

		public boolean getEsUsuarioActual() {
			return esUsuarioActual;
		}

		public Integer getPosicion() {
			return posicion;
		}

		public void setPosicion(Integer posicion) {
			this.posicion = posicion;
		}

		public void aplicarInicialSegundoApellido() {
			if (inicialSegundoApellido == null || inicialSegundoApellido.isBlank()) {
				return;
			}
			this.alias = aliasBase + " " + inicialSegundoApellido + ".";
		}
	}

	/**
	 * Elimina un alumno por su ID y elimina su imagen.
	 *
	 * @param id El ID del alumno a eliminar.
	 * @return true si se elimina con éxito, false si el alumno no existe.
	 */
	@Override
	@Transactional
	public boolean eliminarAlumno(@NonNull Long id) {
		return alumnoRepository.findById(id).map(alumno -> {
			Imagen imagen = alumno.getFotoAlumno();
			if (imagen != null) {
				imagenService.eliminarImagenDeSistema(imagen);
				imagenRepository.delete(imagen);
			}

			if (alumno.getUsuario() != null) {
				Usuario usuario = alumno.getUsuario();
				alumno.setUsuario(null);
				usuarioRepository.delete(usuario);
			}

			for (Grupo grupo : alumno.getGrupos()) {
				grupo.getAlumnos().remove(alumno);
				grupoRepository.save(grupo);
			}

			for (Turno turno : alumno.getTurnos()) {
				turno.getAlumnos().remove(alumno);
				turnoRepository.save(turno);
			}

			for (ProductoAlumno productoAlumno : alumno.getProductosAlumno()) {
				productoAlumnoRepository.delete(productoAlumno);
			}

			for (AlumnoConvocatoria alumnoConvocatoria : alumno.getConvocatorias()) {
				if (alumnoConvocatoria.getProductoAlumno() != null) {
					productoAlumnoRepository.delete(alumnoConvocatoria.getProductoAlumno());
				}
				alumnoConvocatoriaRepository.delete(alumnoConvocatoria);
			}

			alumnoRepository.delete(alumno);
			return true;
		}).orElse(false);
	}

	@Override
	public Alumno darDeBajaAlumno(@NonNull Long id) {
		Optional<Alumno> optionalAlumno = alumnoRepository.findById(id);
		if (optionalAlumno.isPresent()) {
			Alumno alumno = optionalAlumno.get();
			Date fechaBajaHoy = new Date();

			// Desactivar todos los deportes del alumno
			List<AlumnoDeporte> deportesDelAlumno = alumnoDeporteRepository.findByAlumnoId(id);
			for (AlumnoDeporte alumnoDeporte : deportesDelAlumno) {
				if (Boolean.TRUE.equals(alumnoDeporte.getActivo())) {
					alumnoDeporte.setActivo(false);
					alumnoDeporte.setFechaBaja(fechaBajaHoy);
					alumnoDeporteRepository.save(alumnoDeporte);
				}
			}

			// Marcar al alumno como inactivo
			alumno.setActivo(false);
			alumno.setFechaBaja(fechaBajaHoy);
			return alumnoRepository.save(alumno);
		} else {
			throw new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + id);
		}
	}

	@Override
	public Alumno darDeAltaAlumno(@NonNull Long id) {
		Optional<Alumno> optionalAlumno = alumnoRepository.findById(id);
		if (optionalAlumno.isPresent()) {
			Alumno alumno = optionalAlumno.get();
			Date fechaAltaHoy = new Date();

			// Activar automáticamente el primer deporte inactivo (si existe)
			List<AlumnoDeporte> deportesDelAlumno = alumnoDeporteRepository.findByAlumnoId(id);
			List<AlumnoDeporte> deportesInactivos = deportesDelAlumno.stream()
					.filter(d -> Boolean.FALSE.equals(d.getActivo()))
					.sorted((a, b) -> {
						// Ordenar por fechaAltaInicial para activar el deporte más antiguo
						Date fechaA = a.getFechaAltaInicial() != null ? a.getFechaAltaInicial() : a.getFechaAlta();
						Date fechaB = b.getFechaAltaInicial() != null ? b.getFechaAltaInicial() : b.getFechaAlta();
						if (fechaA == null) return 1;
						if (fechaB == null) return -1;
						return fechaA.compareTo(fechaB);
					})
					.toList();

			// Si hay deportes inactivos, activar el primero (el más antiguo)
			if (!deportesInactivos.isEmpty()) {
				AlumnoDeporte deporteAActivar = deportesInactivos.get(0);
				deporteAActivar.setActivo(true);
				deporteAActivar.setFechaBaja(null);
				deporteAActivar.setFechaAlta(fechaAltaHoy);
				// fechaAltaInicial se mantiene sin cambios
				alumnoDeporteRepository.save(deporteAActivar);
			}

			// Marcar al alumno como activo
			alumno.setActivo(true);
			alumno.setFechaBaja(null);
			alumno.setFechaAlta(fechaAltaHoy);
			// fechaAltaInicial se mantiene sin cambios (conserva la fecha original de alta)
			return alumnoRepository.save(alumno);
		} else {
			throw new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + id);
		}
	}

	@Override
	public List<TurnoDTO> obtenerTurnosDelAlumno(Long alumnoId) {
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + alumnoId));

		// Mapeando los turnos asignados al alumno a DTOs (incluyendo grupoId y grupoNombre)
		return alumno.getTurnos().stream().map(turno -> new TurnoDTO(
				turno.getId(),
				turno.getDiaSemana(),
				turno.getHoraInicio(),
				turno.getHoraFin(),
				turno.getGrupo() != null ? turno.getGrupo().getId() : null,
				turno.getGrupo() != null ? turno.getGrupo().getNombre() : null,
				turno.getTipo()
		)).collect(Collectors.toList());
	}

	@Override
	@Transactional
	public void asignarAlumnoATurno(Long alumnoId, Long turnoId) {
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new IllegalArgumentException("Alumno no encontrado"));

		Turno turno = turnoRepository.findById(turnoId)
				.orElseThrow(() -> new IllegalArgumentException("Turno no encontrado"));

		Grupo grupoDelTurno = turno.getGrupo();

		// Verificar si el alumno ya está asignado al grupo del turno
		if (!alumno.getGrupos().contains(grupoDelTurno)) {
			// Si no está asignado, asignar al alumno al grupo del turno
			alumno.getGrupos().add(grupoDelTurno);
			grupoDelTurno.getAlumnos().add(alumno);
			grupoRepository.save(grupoDelTurno); // Guarda la relación en la tabla intermedia
		}

		// Asignar el turno al alumno si no está ya asignado
		if (!alumno.getTurnos().contains(turno)) {
			alumno.addTurno(turno);
		}

		alumnoRepository.save(alumno); // Guarda la relación en la tabla intermedia

		// Verificar y eliminar el grupo si no quedan turnos del grupo
		verificarYEliminarGrupoSiNoQuedanTurnos(alumno, grupoDelTurno);
	}

	@Override
	public void removerAlumnoDeTurno(Long alumnoId, Long turnoId) {
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new IllegalArgumentException("Alumno no encontrado"));

		Turno turno = turnoRepository.findById(turnoId)
				.orElseThrow(() -> new IllegalArgumentException("Turno no encontrado"));

		// Verificar que el turno esté asignado al alumno antes de removerlo
		if (alumno.getTurnos().contains(turno)) {
			alumno.removeTurno(turno);
			alumnoRepository.save(alumno); // Elimina la relación en la tabla intermedia
		}

		// Verificar y eliminar el grupo si no quedan turnos del grupo
		verificarYEliminarGrupoSiNoQuedanTurnos(alumno, turno.getGrupo());
	}

	private void verificarYEliminarGrupoSiNoQuedanTurnos(Alumno alumno, Grupo grupo) {
		boolean tieneTurnosDelGrupo = alumno.getTurnos().stream().anyMatch(turno -> turno.getGrupo().equals(grupo));

		if (!tieneTurnosDelGrupo) {
			// Si no tiene turnos del grupo, eliminar el alumno de ese grupo
			alumno.getGrupos().remove(grupo);
			grupo.getAlumnos().remove(alumno);
			grupoRepository.save(grupo);
			alumnoRepository.save(alumno);
		}
	}

	// Método para obtener alumnos aptos y mapear a AlumnoConGruposDTO
	public List<AlumnoConGruposDTO> obtenerAlumnosAptosConGruposDTO() {
		// Busca alumnos activos que tengan al menos un deporte activo con aptoParaExamen = true
		List<Alumno> alumnos = alumnoRepository.findAlumnosAptosParaExamen();

		return alumnos.stream()
				.map(alumno -> {
					AlumnoDeporte alumnoDeporte = null;
					if (alumno.getDeportes() != null) {
						alumnoDeporte = alumno.getDeportes().stream()
								.filter(ad -> Boolean.TRUE.equals(ad.getActivo()) && Boolean.TRUE.equals(ad.getAptoParaExamen()))
								.findFirst()
								.orElse(null);
					}
					return AlumnoConGruposDTO.deAlumnoConGrupos(alumno, alumnoDeporte);
				})
				.collect(Collectors.toList());
	}
	// Servicio para obtener alumnos aptos para examen por deporte, excluyendo
	// "competición"
	@Override
	public List<AlumnoConGruposDTO> obtenerAlumnosAptosPorDeporte(String deporte, String exclusion) {
		Deporte deporteEnum;
		try {
			deporteEnum = Deporte.valueOf(deporte.trim().toUpperCase().replace(" ", "_"));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Deporte no válido: " + deporte);
		}

		List<AlumnoDeporte> alumnosDeporte = alumnoDeporteService.obtenerAptosParaExamenPorDeporte(deporteEnum);

		return alumnosDeporte.stream()
				.filter(ad -> ad.getAlumno() != null)
				.map(ad -> AlumnoConGruposDTO.deAlumnoConGrupos(ad.getAlumno(), ad))
				.collect(Collectors.toList());
	}
	// Servicio para obtener un alumno apto para examen por su ID
	@Override
	public Optional<AlumnoConGruposDTO> obtenerAlumnoAptoPorId(Long id) {
		Optional<Alumno> alumno = alumnoRepository.findById(id);

		if (alumno.isEmpty()) {
			return Optional.empty();
		}

		Alumno alumnoEntity = alumno.get();
		AlumnoDeporte alumnoDeporte = null;
		if (alumnoEntity.getDeportes() != null) {
			alumnoDeporte = alumnoEntity.getDeportes().stream()
					.filter(ad -> Boolean.TRUE.equals(ad.getActivo()) && Boolean.TRUE.equals(ad.getAptoParaExamen()))
					.findFirst()
					.orElse(null);
		}

		if (alumnoDeporte == null) {
			return Optional.empty();
		}

		return Optional.ofNullable(AlumnoConGruposDTO.deAlumnoConGrupos(alumnoEntity, alumnoDeporte));
	}
	/**
	 * Obtiene alumnos elegibles para una convocatoria específica
	 * basándose en el deporte y el estado de aptoParaExamen en AlumnoDeporte
	 */
	@Override
	public List<AlumnoConGruposDTO> obtenerAlumnosElegiblesParaConvocatoria(Deporte deporte) {
		List<AlumnoDeporte> alumnosDeporte = alumnoDeporteService.obtenerAptosParaExamenPorDeporte(deporte);

		return alumnosDeporte.stream()
				.filter(ad -> ad.getAlumno() != null)
				.map(ad -> AlumnoConGruposDTO.deAlumnoConGrupos(ad.getAlumno(), ad))
				.collect(Collectors.toList());
	}

	/**
	 * Asigna la cuantía de la tarifa según el tipo de tarifa.
	 * Delegado a TarifaConfig para centralizar la configuración de precios.
	 *
	 * @param tipoTarifa El tipo de tarifa del alumno.
	 * @return La cuantía asignada.
	 */
	@Override
	public double asignarCuantiaTarifa(TipoTarifa tipoTarifa) {
		return tarifaConfig.obtenerCuantia(tipoTarifa);
	}

	/**
	 * Asigna la cuantía de la tarifa según el tipo de tarifa y el rol familiar.
	 * Este método considera el rol familiar para la tarifa PADRES_HIJOS, donde el
	 * precio varía según si el alumno es el padre (28€) o el hijo (26€).
	 *
	 * @param tipoTarifa El tipo de tarifa del alumno.
	 * @param rolFamiliar El rol familiar del alumno (PADRE, HIJO, NINGUNO).
	 * @return La cuantía asignada.
	 */
	@Override
	public double asignarCuantiaTarifa(TipoTarifa tipoTarifa, RolFamiliar rolFamiliar) {
		return tarifaConfig.obtenerCuantiaConRol(tipoTarifa, rolFamiliar);
	}

	/**
	 * Asigna una categoría según la edad del alumno.
	 *
	 * @param edad La edad del alumno.
	 * @return La categoría asignada.
	 */
	@Override
	public Categoria asignarCategoriaSegunEdad(int edad) {
		TipoCategoria tipoCategoria;
		if (edad <= 9) {
			tipoCategoria = TipoCategoria.INFANTIL;
		} else if (edad <= 11) {
			tipoCategoria = TipoCategoria.PRECADETE;
		} else if (edad <= 13) {
			tipoCategoria = TipoCategoria.CADETE;
		} else if (edad <= 16) {
			tipoCategoria = TipoCategoria.JUNIOR;
		} else {
			tipoCategoria = TipoCategoria.SENIOR;
		}

		return categoriaRepository.findByNombre(tipoCategoria.getNombre());
	}

	/**
	 * Asigna un grado según la edad del alumno y otros criterios.
	 *
	 * @param nuevoAlumnoDTO El objeto AlumnoDTO con los datos del alumno.
	 * @return El grado asignado.
	 */
	@Override
	public Grado asignarGradoSegunEdad(AlumnoDTO nuevoAlumnoDTO) {
		Deporte deporte = nuevoAlumnoDTO.getDeporte() != null ? nuevoAlumnoDTO.getDeporte() : Deporte.TAEKWONDO;
		boolean esMenor = FechaUtils.esMenor(nuevoAlumnoDTO.getFechaNacimiento(), deporte);

		List<TipoGrado> gradosDisponibles;

		// Si el alumno es menor, asignar grados correspondientes a menores
		if (esMenor) {
			gradosDisponibles = Arrays.asList(TipoGrado.BLANCO, TipoGrado.BLANCO_AMARILLO, TipoGrado.AMARILLO,
					TipoGrado.AMARILLO_NARANJA, TipoGrado.NARANJA, TipoGrado.NARANJA_VERDE, TipoGrado.VERDE,
					TipoGrado.VERDE_AZUL, TipoGrado.AZUL, TipoGrado.AZUL_ROJO, TipoGrado.ROJO,
					TipoGrado.ROJO_NEGRO_1_PUM, TipoGrado.ROJO_NEGRO_2_PUM, TipoGrado.ROJO_NEGRO_3_PUM);
		} else {
			// Si el alumno es adulto, asignar grados correspondientes a adultos
			gradosDisponibles = Arrays.asList(TipoGrado.BLANCO, TipoGrado.AMARILLO, TipoGrado.NARANJA, TipoGrado.VERDE,
					TipoGrado.AZUL, TipoGrado.ROJO, TipoGrado.NEGRO_1_DAN, TipoGrado.NEGRO_2_DAN, TipoGrado.NEGRO_3_DAN,
					TipoGrado.NEGRO_4_DAN, TipoGrado.NEGRO_5_DAN);
		}

		// Asignar un grado aleatoriamente de la lista de grados disponibles
		TipoGrado tipoGradoAsignado = gradosDisponibles.get(new Random().nextInt(gradosDisponibles.size()));

		// Buscar si el grado ya existe en la base de datos
		Grado gradoExistente = gradoRepository.findByTipoGrado(tipoGradoAsignado);
		if (gradoExistente != null) {
			return gradoExistente;
		}

		// Si no existe, crear un nuevo grado y guardarlo
		Grado nuevoGrado = new Grado();
		nuevoGrado.setTipoGrado(tipoGradoAsignado);
		return gradoRepository.save(nuevoGrado);
	}

	/**
	 * Calcula el siguiente grado para un alumno según su deporte, edad y grado actual.
	 * Delegado a GradeProgressionConfig para centralizar la lógica de progresión.
	 */
	@Override
	public TipoGrado calcularSiguienteGrado(Alumno alumno) {
		AlumnoDeporte deportePrincipal = AlumnoDeporteUtils.seleccionarDeportePrincipal(alumno.getDeportes());

		TipoGrado gradoActual = null;
		Deporte deporte = null;
		if (deportePrincipal != null && deportePrincipal.getGrado() != null) {
			gradoActual = deportePrincipal.getGrado().getTipoGrado();
			deporte = deportePrincipal.getDeporte();
		} else if (alumno.getGrado() != null && alumno.getDeporte() != null) {
			gradoActual = alumno.getGrado().getTipoGrado();
			deporte = alumno.getDeporte();
		}
		if (gradoActual == null || deporte == null) {
			return null;
		}

		// Usar FechaUtils.esMenor para aplicar la regla correcta según el deporte
		boolean esMenor = FechaUtils.esMenor(alumno.getFechaNacimiento(), deporte);

		return gradeProgressionConfig.obtenerSiguienteGrado(deporte, esMenor, gradoActual);
	}

	/**
	 * Calcula el siguiente grado para un alumno según un deporte específico y grado actual.
	 * Útil para sistema multi-deporte.
	 */
	private TipoGrado calcularSiguienteGradoPorDeporte(TipoGrado gradoActual, Deporte deporte, Alumno alumno) {
		// Usar FechaUtils.esMenor para aplicar la regla correcta según el deporte
		boolean esMenor = FechaUtils.esMenor(alumno.getFechaNacimiento(), deporte);
		return gradeProgressionConfig.obtenerSiguienteGrado(deporte, esMenor, gradoActual);
	}

	private String resolverConceptoProducto(TipoGrado gradoSiguiente, boolean porRecompensa, boolean rojoBordado) {
		if (gradoSiguiente == TipoGrado.ROJO) {
			if (porRecompensa) {
				return rojoBordado ? DERECHO_RECOMPENSA_ROJO_BORDADO : DERECHO_RECOMPENSA_ROJO;
			}
			return rojoBordado ? DERECHO_EXAMEN_ROJO_BORDADO : DERECHO_EXAMEN_ROJO;
		}
		return gradoSiguiente.obtenerNombreProducto(porRecompensa);
	}

	private Producto buscarProductoPorConcepto(String conceptoProducto, TipoGrado gradoSiguiente) {
		Optional<Producto> productoExacto = productoRepository.findFirstByConcepto(conceptoProducto);
		if (productoExacto.isPresent()) {
			return productoExacto.get();
		}

		String conceptoNormalizado = normalizarConcepto(conceptoProducto);
		return productoRepository.findAll().stream()
				.filter(producto -> normalizarConcepto(producto.getConcepto()).equals(conceptoNormalizado))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"Producto no encontrado para el grado: " + gradoSiguiente + " (" + conceptoProducto + ")"));
	}

	private String normalizarConcepto(String concepto) {
		if (concepto == null) {
			return "";
		}

		String sinAcentos = Normalizer.normalize(concepto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		return sinAcentos.replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
	}

	@Override
	@Transactional
	public AlumnoConvocatoriaDTO agregarAlumnoAConvocatoria(Long alumnoId, Long convocatoriaId, boolean porRecompensa,
			boolean rojoBordado) {
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new IllegalArgumentException("Alumno no encontrado con ID: " + alumnoId));

		Convocatoria convocatoria = convocatoriaRepository.findById(convocatoriaId)
				.orElseThrow(() -> new IllegalArgumentException("Convocatoria no encontrada"));

		// Find the AlumnoDeporte for the sport of the convocatoria
		AlumnoDeporte alumnoDeporte = alumno.getDeportes().stream()
				.filter(ad -> ad.getDeporte() == convocatoria.getDeporte() && Boolean.TRUE.equals(ad.getActivo()))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no está activo en el deporte " + convocatoria.getDeporte()));

		// Verify the alumno is eligible for exam in this sport
		if (!Boolean.TRUE.equals(alumnoDeporte.getAptoParaExamen())) {
			throw new IllegalArgumentException("El alumno no está apto para examen en el deporte " + convocatoria.getDeporte());
		}

		// Calculate next grade based on the AlumnoDeporte's current grade
		TipoGrado gradoActual = alumnoDeporte.getGrado().getTipoGrado();
		TipoGrado gradoSiguiente = calcularSiguienteGradoPorDeporte(gradoActual, convocatoria.getDeporte(), alumno);
		if (gradoSiguiente == null) {
			throw new IllegalArgumentException("No se pudo determinar el siguiente grado del alumno para el deporte " + convocatoria.getDeporte());
		}

		String conceptoProducto = resolverConceptoProducto(gradoSiguiente, porRecompensa, rojoBordado);
		Producto producto = buscarProductoPorConcepto(conceptoProducto, gradoSiguiente);

		// Construir concepto con el nombre del deporte al final
		String conceptoConDeporte = producto.getConcepto() + " - " + formatearNombreDeporte(convocatoria.getDeporte());

		ProductoAlumno productoAlumno = new ProductoAlumno();
		productoAlumno.setAlumno(alumno);
		productoAlumno.setProducto(producto);
		productoAlumno.setConcepto(conceptoConDeporte);
		productoAlumno.setFechaAsignacion(new Date());
		productoAlumno.setCantidad(1);
		productoAlumno.setPrecio(producto.getPrecio());
		productoAlumno.setPagado(false);
		productoAlumnoRepository.save(productoAlumno);

		AlumnoConvocatoria alumnoConvocatoria = new AlumnoConvocatoria();
		alumnoConvocatoria.setAlumno(alumno);
		alumnoConvocatoria.setConvocatoria(convocatoria);
		alumnoConvocatoria.setProductoAlumno(productoAlumno);
		alumnoConvocatoria.setCuantiaExamen(producto.getPrecio());
		alumnoConvocatoria.setGradoActual(gradoActual);
		alumnoConvocatoria.setGradoSiguiente(gradoSiguiente);
		alumnoConvocatoria.setAlumnoDeporte(alumnoDeporte);
		alumnoConvocatoriaRepository.save(alumnoConvocatoria);

		return convertirAAlumnoConvocatoriaDTO(alumnoConvocatoria);
	}

	@Override
	@Transactional
	public void eliminarAlumnoDeConvocatoria(Long alumnoId, Long convocatoriaId) {
		AlumnoConvocatoria alumnoConvocatoria = alumnoConvocatoriaRepository
				.findByConvocatoriaIdAndAlumnoId(convocatoriaId, alumnoId)
				.orElseThrow(() -> new IllegalArgumentException("El alumno no está inscrito en esta convocatoria"));

		ProductoAlumno productoAlumno = alumnoConvocatoria.getProductoAlumno();
		if (productoAlumno != null) {
			alumnoConvocatoria.setProductoAlumno(null);
			alumnoConvocatoriaRepository.save(alumnoConvocatoria);
			productoAlumnoRepository.delete(productoAlumno);
		}

		alumnoConvocatoriaRepository.delete(alumnoConvocatoria);
	}

	@Override
	@Transactional
	public AlumnoDeporte pasarGradoPorRecompensa(Long alumnoId, Deporte deporte, boolean rojoBordado) {
		return pasarGradoInterno(alumnoId, deporte, rojoBordado, true);
	}

	@Override
	@Transactional
	public AlumnoDeporte pasarGradoConDerechoExamen(Long alumnoId, Deporte deporte, boolean rojoBordado) {
		return pasarGradoInterno(alumnoId, deporte, rojoBordado, false);
	}

	/**
	 * Método interno para pasar de grado (por recompensa o derecho de examen)
	 */
	private AlumnoDeporte pasarGradoInterno(Long alumnoId, Deporte deporte, boolean rojoBordado, boolean porRecompensa) {
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new IllegalArgumentException("Alumno no encontrado con ID: " + alumnoId));

		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		// Validar que el deporte admite grados
		if (deporte == Deporte.PILATES || deporte == Deporte.DEFENSA_PERSONAL_FEMENINA) {
			throw new IllegalArgumentException("El deporte " + deporte + " no tiene sistema de grados");
		}

		// Verificar que el alumno está apto para examen en este deporte
		if (!Boolean.TRUE.equals(alumnoDeporte.getAptoParaExamen())) {
			throw new IllegalArgumentException(
					"El alumno no está apto para examen en el deporte " + deporte);
		}

		TipoGrado gradoActual = alumnoDeporte.getGrado() != null ? alumnoDeporte.getGrado().getTipoGrado() : null;
		if (gradoActual == null) {
			throw new IllegalArgumentException("El alumno no tiene grado asignado en el deporte " + deporte);
		}

		// Calcular siguiente grado con la misma lógica por edad y deporte
		TipoGrado gradoSiguiente = calcularSiguienteGradoPorDeporte(gradoActual, deporte, alumno);
		if (gradoSiguiente == null) {
			throw new IllegalArgumentException(
					"No se pudo determinar el siguiente grado del alumno para el deporte " + deporte);
		}

		// Obtener producto asociado al siguiente grado (recompensa o derecho de examen)
		String conceptoProducto = resolverConceptoProducto(gradoSiguiente, porRecompensa, rojoBordado);
		Producto producto = buscarProductoPorConcepto(conceptoProducto, gradoSiguiente);

		// Construir concepto con el nombre del deporte al final
		String conceptoConDeporte = producto.getConcepto() + " - " + formatearNombreDeporte(deporte);

		ProductoAlumno productoAlumno = new ProductoAlumno();
		productoAlumno.setAlumno(alumno);
		productoAlumno.setAlumnoDeporte(alumnoDeporte);
		productoAlumno.setProducto(producto);
		productoAlumno.setConcepto(conceptoConDeporte);
		productoAlumno.setFechaAsignacion(new Date());
		productoAlumno.setCantidad(1);
		productoAlumno.setPrecio(producto.getPrecio());
		productoAlumno.setPagado(false);
		productoAlumnoRepository.save(productoAlumno);

		// No actualizar el grado aqui; se actualizara cuando el producto se marque como pagado
		return alumnoDeporte;
	}

	/**
	 * Formatea el nombre del deporte para mostrarlo en el concepto
	 */
	private String formatearNombreDeporte(Deporte deporte) {
		switch (deporte) {
			case TAEKWONDO:
				return "TAEKWONDO";
			case KICKBOXING:
				return "KICKBOXING";
			default:
				return deporte.name();
		}
	}


	/**
	 * Verifica si la fecha de nacimiento es válida.
	 *
	 * @param fechaNacimiento La fecha de nacimiento a verificar.
	 * @return true si la fecha de nacimiento es válida, false si no lo es.
	 */
	@Override
	public boolean fechaNacimientoValida(Date fechaNacimiento) {
		Calendar fechaActualMenos3Anios = Calendar.getInstance();
		fechaActualMenos3Anios.add(Calendar.YEAR, -3);

		Calendar fechaNacimientoCalendar = Calendar.getInstance();
		fechaNacimientoCalendar.setTime(fechaNacimiento);

		return fechaNacimientoCalendar.before(fechaActualMenos3Anios);
	}

	/**
	 * Verifica si los datos del alumno son válidos.
	 *
	 * @param alumnoDTO El objeto AlumnoDTO con los datos del alumno.
	 * @return true si los datos son válidos, false si no lo son.
	 */
	@Override
	public boolean datosAlumnoValidos(AlumnoDTO alumnoDTO) {
		return datosAlumnoValidos(alumnoDTO, true);
	}

	@Override
	public boolean datosAlumnoValidos(AlumnoDTO alumnoDTO, boolean requiereTarifaYFechaAlta) {
		if (alumnoDTO.getNombre() == null || alumnoDTO.getNombre().isEmpty() || alumnoDTO.getApellidos() == null
				|| alumnoDTO.getApellidos().isEmpty()) {
			return false;
		}
		if (alumnoDTO.getFechaNacimiento() == null || alumnoDTO.getFechaNacimiento().after(new Date())) {
			return false;
		}

		if (alumnoDTO.getDireccion() == null || alumnoDTO.getDireccion().isEmpty() || alumnoDTO.getEmail() == null
				|| alumnoDTO.getEmail().isEmpty()) {
			return false;
		}
		if (alumnoDTO.getTelefono() != null && alumnoDTO.getTelefono() <= 0) {
			return false;
		}
		if (requiereTarifaYFechaAlta) {
			if (alumnoDTO.getTipoTarifa() == null) {
				return false;
			}
			if (alumnoDTO.getFechaAlta() == null || alumnoDTO.getFechaAlta().after(new Date())) {
				return false;
			}
		} else if (alumnoDTO.getFechaAlta() != null && alumnoDTO.getFechaAlta().after(new Date())) {
			return false;
		}
		return true;
	}

	/**
	 * Genera una contraseña codificada a partir del nombre y apellidos de un
	 * usuario.
	 *
	 * @param nombre    El nombre del usuario.
	 * @param apellidos Los apellidos del usuario.
	 * @return La contraseña codificada generada a partir del nombre y apellidos.
	 */
	@Override
	public String generarContrasena(String nombre, String apellidos) {
		String cadena = (nombre + apellidos).toLowerCase();
		return passwordEncoder.encode(cadena);
	}

	/**
	 * Calcula si un alumno es apto para examen según su grado y su edad.
	 * 
	 * @param alumno El alumno para el cual se realiza el cálculo.
	 * @return true si el alumno es apto para examen, false en caso contrario.
	 */
	@Override
	public boolean esAptoParaExamen(Alumno alumno) {
		if (alumno.getDeportes() != null && !alumno.getDeportes().isEmpty()) {
			return alumno.getDeportes().stream()
					.filter(ad -> Boolean.TRUE.equals(ad.getActivo()))
					.anyMatch(ad -> Boolean.TRUE.equals(ad.getAptoParaExamen()));
		}

		if (alumno.getGrado() == null || alumno.getFechaGrado() == null) {
			return false; // Si no hay grado o fecha de grado, no es apto
		}

		try {
			LocalDate fechaGrado = toLocalDate(alumno.getFechaGrado());
			if (fechaGrado == null) {
				return false;
			}

			int edad = FechaUtils.calcularEdad(alumno.getFechaNacimiento());

			long mesesRequeridos = obtenerMesesRequeridosParaExamen(edad, alumno.getGrado().getTipoGrado());

			LocalDate fechaExamenPosible = fechaGrado.plusMonths(mesesRequeridos);

			// Si la fecha actual es igual o mayor a la fecha posible, es apto
			return !LocalDate.now().isBefore(fechaExamenPosible);
		} catch (Exception e) {
			// Registrar el error para depuración
			System.err.println("Error calculando aptitud para examen: " + e.getMessage());
			return false;
		}
	}

	/**
	 * Calcula los meses requeridos para ser apto para examen según la edad y el grado.
	 * Delegado a ExamEligibilityConfig para centralizar las reglas de elegibilidad.
	 *
	 * @param edad      La edad del alumno.
	 * @param tipoGrado El grado actual del alumno.
	 * @return Los meses requeridos para ser apto para examen.
	 */
	private long obtenerMesesRequeridosParaExamen(int edad, TipoGrado tipoGrado) {
		return examEligibilityConfig.obtenerMesesRequeridos(edad, tipoGrado);
	}
	
	private void asignarMensualidadGeneralSiCorresponde(Alumno alumno) {
	    LocalDate fechaActual = LocalDate.now();
	    String mesAno = fechaActual.getYear() + "-" + "%02d".formatted(fechaActual.getMonthValue());

	    String nombreMensualidad = MensualidadUtils.formatearNombreMensualidad(mesAno);

	    boolean mensualidadCargada = productoAlumnoRepository.existsByConcepto(nombreMensualidad);

	    if (mensualidadCargada) {
	        // Check if MENSUALIDAD product exists before trying to use it
	        var optionalProductoMensualidad = productoRepository.findFirstByConcepto("MENSUALIDAD");
	        if (optionalProductoMensualidad.isEmpty()) {
	            System.out.println("ADVERTENCIA: Producto 'MENSUALIDAD' no encontrado. " +
	                    "Se omitirá la asignación automática de mensualidad para el alumno " + alumno.getId());
	            return;
	        }

	        Producto productoMensualidad = optionalProductoMensualidad.get();

	        ProductoAlumno productoAlumno = new ProductoAlumno();
	        productoAlumno.setAlumno(alumno);
	        productoAlumno.setProducto(productoMensualidad);
	        productoAlumno.setConcepto(nombreMensualidad);
	        productoAlumno.setPrecio(alumno.getCuantiaTarifa());
	        productoAlumno.setFechaAsignacion(Date.from(fechaActual.atStartOfDay(ZoneId.systemDefault()).toInstant()));
	        productoAlumno.setCantidad(1);
	        productoAlumno.setPagado(false);

	        productoAlumnoRepository.save(productoAlumno);
	    }
	}

	private AlumnoConvocatoriaDTO convertirAAlumnoConvocatoriaDTO(AlumnoConvocatoria alumnoConvocatoria) {
		AlumnoConvocatoriaDTO dto = new AlumnoConvocatoriaDTO();
		dto.setAlumnoId(alumnoConvocatoria.getAlumno().getId());
		dto.setNombre(alumnoConvocatoria.getAlumno().getNombre());
		dto.setApellidos(alumnoConvocatoria.getAlumno().getApellidos());
		dto.setCuantiaExamen(alumnoConvocatoria.getCuantiaExamen());
		dto.setGradoActual(alumnoConvocatoria.getGradoActual());
		dto.setGradoSiguiente(alumnoConvocatoria.getGradoSiguiente());
		dto.setPagado(alumnoConvocatoria.getPagado());
		return dto;
	}

	@Override
	public long countAlumnos() {
        return alumnoRepository.count();
	}

	/**
	 * Builds a JPA Specification for filtering Alumno entities.
	 * Extracted to avoid code duplication between paginated and non-paginated filter methods.
	 *
	 * @param nombre          Name filter (searches in nombre, apellidos, and full name)
	 * @param gradoId         Grado ID filter
	 * @param categoriaId     Categoria ID filter
	 * @param incluirInactivos Whether to include inactive students
	 * @param aptoParaExamen  Whether to filter only students eligible for exam
	 * @return Specification for filtering Alumno
	 */
	private org.springframework.data.jpa.domain.Specification<Alumno> buildAlumnoSpecification(
			String nombre, Long gradoId, Long categoriaId, boolean incluirInactivos, boolean aptoParaExamen) {
		return (root, query, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();

			if (nombre != null && !nombre.isEmpty()) {
				String nombreLower = nombre.toLowerCase();

				// Build full name expression (nombre + ' ' + apellidos)
				Expression<String> fullNameExpression = criteriaBuilder
						.concat(criteriaBuilder.lower(root.get("nombre")), criteriaBuilder.literal(" "));
				fullNameExpression = criteriaBuilder.concat(fullNameExpression,
						criteriaBuilder.lower(root.get("apellidos")));

				// Create predicate comparing full name with search value
				Predicate fullNamePredicate = criteriaBuilder.like(fullNameExpression, "%" + nombreLower + "%");

				// Also compare nombre and apellidos individually
				Predicate nombrePredicate = criteriaBuilder.like(criteriaBuilder.lower(root.get("nombre")),
						"%" + nombreLower + "%");
				Predicate apellidosPredicate = criteriaBuilder.like(criteriaBuilder.lower(root.get("apellidos")),
						"%" + nombreLower + "%");

				// Combine predicates with OR
				predicates.add(criteriaBuilder.or(fullNamePredicate, nombrePredicate, apellidosPredicate));
			}

			if (gradoId != null || categoriaId != null) {
				jakarta.persistence.criteria.Subquery<Long> subquery = query.subquery(Long.class);
				jakarta.persistence.criteria.Root<com.taemoi.project.entities.AlumnoDeporte> deporteRoot = subquery.from(com.taemoi.project.entities.AlumnoDeporte.class);
				List<Predicate> deportePredicates = new ArrayList<>();

				deportePredicates.add(criteriaBuilder.equal(deporteRoot.get("alumno").get("id"), root.get("id")));
				deportePredicates.add(criteriaBuilder.equal(deporteRoot.get("activo"), true));

				if (gradoId != null) {
					deportePredicates.add(criteriaBuilder.equal(deporteRoot.get("grado").get("id"), gradoId));
				}
				if (categoriaId != null) {
					deportePredicates.add(criteriaBuilder.equal(deporteRoot.get("categoria").get("id"), categoriaId));
				}

				subquery.select(deporteRoot.get("alumno").get("id"))
						.where(criteriaBuilder.and(deportePredicates.toArray(new Predicate[0])));
				predicates.add(criteriaBuilder.exists(subquery));
			}

			if (!incluirInactivos) {
				predicates.add(criteriaBuilder.equal(root.get("activo"), true));
			}

			// Filter by aptoParaExamen - join with deportes to check if any has aptoParaExamen = true
			if (aptoParaExamen) {
				// Use subquery to check if alumno has at least one deporte with aptoParaExamen = true
				jakarta.persistence.criteria.Subquery<Long> subquery = query.subquery(Long.class);
				jakarta.persistence.criteria.Root<com.taemoi.project.entities.AlumnoDeporte> deporteRoot = subquery.from(com.taemoi.project.entities.AlumnoDeporte.class);
				subquery.select(deporteRoot.get("alumno").get("id"))
						.where(criteriaBuilder.and(
								criteriaBuilder.equal(deporteRoot.get("alumno").get("id"), root.get("id")),
								criteriaBuilder.equal(deporteRoot.get("aptoParaExamen"), true),
								criteriaBuilder.equal(deporteRoot.get("activo"), true)
						));
				predicates.add(criteriaBuilder.exists(subquery));
			}

			return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
		};
	}

	// ==================== IMPLEMENTACIÓN MÉTODOS MULTI-DEPORTE ====================

	@Override
	public List<com.taemoi.project.entities.AlumnoDeporte> obtenerDeportesDelAlumno(Long alumnoId) {
		return alumnoDeporteService.obtenerDeportesDelAlumno(alumnoId);
	}

	@Override
	public List<com.taemoi.project.entities.AlumnoDeporte> obtenerDeportesActivosDelAlumno(Long alumnoId) {
		return alumnoDeporteService.obtenerDeportesActivosDelAlumno(alumnoId);
	}

	@Override
	public com.taemoi.project.entities.AlumnoDeporte agregarDeporteAAlumno(Long alumnoId,
			com.taemoi.project.entities.Deporte deporte,
			com.taemoi.project.entities.TipoGrado gradoInicial,
			java.util.Date fechaAlta,
			java.util.Date fechaAltaInicial,
			java.util.Date fechaGrado) {
		return alumnoDeporteService.agregarDeporteAAlumno(
				alumnoId,
				deporte,
				gradoInicial,
				fechaAlta,
				fechaAltaInicial,
				fechaGrado
		);
	}

	@Override
	public void removerDeporteDeAlumno(Long alumnoId, com.taemoi.project.entities.Deporte deporte) {
		alumnoDeporteService.removerDeporteDeAlumno(alumnoId, deporte);
	}

	@Override
	public com.taemoi.project.entities.AlumnoDeporte actualizarGradoPorDeporte(Long alumnoId,
			com.taemoi.project.entities.Deporte deporte,
			com.taemoi.project.entities.TipoGrado nuevoGrado) {
		return alumnoDeporteService.actualizarGradoPorDeporte(alumnoId, deporte, nuevoGrado);
	}

	@Override
	@Transactional
	public Alumno actualizarFechaAltaInicial(@NonNull Long id, Date nuevaFechaAltaInicial) {
		Alumno alumno = alumnoRepository.findById(id)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + id));

		// Validación opcional: advertir si fecha es posterior a la fecha de alta de algún deporte
		if (alumno.getDeportes() != null) {
			for (com.taemoi.project.entities.AlumnoDeporte deporte : alumno.getDeportes()) {
				if (deporte.getFechaAlta() != null && nuevaFechaAltaInicial.after(deporte.getFechaAlta())) {
					logger.warn("fechaAltaInicial ({}) is after fechaAlta ({}) for sport {}",
							nuevaFechaAltaInicial, deporte.getFechaAlta(), deporte.getDeporte());
					// Permitir pero registrar advertencia
				}
			}
		}

		alumno.setFechaAltaInicial(nuevaFechaAltaInicial);
		return alumnoRepository.save(alumno);
	}

	@Override
	public Alumno buscarAlumno(@NonNull Long id) {
		return alumnoRepository.findById(id)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + id));
	}
}
