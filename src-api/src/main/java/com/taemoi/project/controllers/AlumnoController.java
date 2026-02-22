package com.taemoi.project.controllers;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.dtos.AlumnoDTO;
import com.taemoi.project.dtos.TurnoDTO;
import com.taemoi.project.dtos.request.AlumnoObservacionesDTO;
import com.taemoi.project.dtos.response.AlumnoConGruposDTO;
import com.taemoi.project.dtos.response.AlumnoConvocatoriaDTO;
import com.taemoi.project.dtos.response.GrupoResponseDTO;
import com.taemoi.project.dtos.response.MaterialExamenDTO;
import com.taemoi.project.dtos.response.RetoDiarioEstadoDTO;
import com.taemoi.project.dtos.response.RetoDiarioRankingGeneralResponse;
import com.taemoi.project.dtos.response.RetoDiarioRankingSemanalResponse;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.Documento;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.exceptions.alumno.AlumnoDuplicadoException;
import com.taemoi.project.exceptions.alumno.AlumnoNoEncontradoException;
import com.taemoi.project.exceptions.alumno.DatosAlumnoInvalidosException;
import com.taemoi.project.exceptions.alumno.FechaNacimientoInvalidaException;
import com.taemoi.project.exceptions.alumno.ListaAlumnosVaciaException;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.services.AlumnoAccessControlService;
import com.taemoi.project.services.AlumnoDeporteService;
import com.taemoi.project.services.AlumnoService;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.ImagenService;
import com.taemoi.project.services.MaterialExamenService;
import com.taemoi.project.services.DocumentoService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.Valid;

/**
 * Controlador REST que gestiona las operaciones relacionadas con los alumnos en
 * el sistema. Proporciona endpoints para recuperar, crear, actualizar y
 * eliminar información de los alumnos. Se requiere que el usuario tenga el rol
 * ROLE_USER o ROLE_ADMIN para acceder a estos endpoints.
 */
@RestController
@RequestMapping("/api/alumnos")
public class AlumnoController {
	private static final Logger logger = LoggerFactory.getLogger(AdminController.class);
	private static final int MIN_ANCHO_IMAGEN = 120;
	private static final int MAX_ANCHO_IMAGEN = 1800;
	private static final float CALIDAD_WEBP_REDIMENSION = 0.76f;
	private static final long MAX_TAMANO_ORIGEN_RESIZE_BYTES = 8L * 1024 * 1024;
	private static final long MAX_PIXELES_ORIGEN_RESIZE = 12_000_000L;
	private static final Semaphore RESIZE_SEMAPHORE = new Semaphore(2, true);

	@Value("${app.base.url}")
	private String baseUrl;

	@Value("${app.imagenes.directorio.linux:}")
	private String directorioImagenesLinux;

	@Value("${app.imagenes.directorio.windows:}")
	private String directorioImagenesWindows;

	/**
	 * Inyección del servicio de alumno.
	 */
	@Autowired
	AlumnoService alumnoService;

	/**
	 * Inyección del repositorio de alumno.
	 */
	@Autowired
	AlumnoRepository alumnoRepository;

	/**
	 * Inyección del servicio de grupo.
	 */
	@Autowired
	private GrupoService grupoService;

	@Autowired
	private ImagenService imagenService;

	@Autowired
	private DocumentoService documentoService;

	@Autowired
	private MaterialExamenService materialExamenService;
	
	@Autowired
	private AlumnoDeporteService alumnoDeporteService;

	@Autowired
	private AlumnoAccessControlService alumnoAccessControlService;

	@PersistenceContext
	private EntityManager entityManager;

	/**
	 * Obtiene una lista de alumnos paginada o filtrada según los parámetros
	 * proporcionados.
	 *
	 * @param page        Número de página para paginación (opcional).
	 * @param size        Tamaño de la página para paginación (opcional).
	 * @param nombre      Nombre del alumno para filtrar (opcional).
	 * @param gradoId     ID del grado del alumno para filtrar (opcional).
	 * @param categoriaId ID de la categoría del alumno para filtrar (opcional).
	 * @return ResponseEntity que contiene una lista paginada o filtrada de alumnos.
	 * @throws ListaAlumnosVaciaException si no se encuentran alumnos en el sistema.
	 */
	@GetMapping
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	@Transactional(readOnly = true)
	public ResponseEntity<?> obtenerAlumnosDTO(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) String nombre,
			@RequestParam(required = false) Long gradoId, @RequestParam(required = false) Long categoriaId,
			@RequestParam(required = false) Boolean incluirInactivos,
			@RequestParam(required = false) Boolean aptoParaExamen) {

		logger.info("## AlumnoController :: obtenerAlumnosDTO :: Iniciando método");
		logger.info(
				"## AlumnoController :: obtenerAlumnosDTO :: Parámetros recibidos - page: {}, size: {}, nombre: {}, gradoId: {}, categoriaId: {}, aptoParaExamen: {}",
				page, size, nombre, gradoId, categoriaId, aptoParaExamen);

		Pageable pageable = (page != null && size != null)
				? PageRequest.of(page - 1, size, Sort.by("nombre").ascending())
				: Pageable.unpaged();
		boolean isPaged = page != null && size != null;
		boolean incluir = incluirInactivos != null ? incluirInactivos : false;
		boolean soloAptos = aptoParaExamen != null && aptoParaExamen;

		Page<Alumno> alumnos = alumnoService.obtenerAlumnosFiltrados(nombre, gradoId, categoriaId, incluir, soloAptos, pageable);

		if (alumnos.isEmpty()) {
			logger.warn("## AlumnoController :: obtenerAlumnosDTO :: No hay usuarios registrados en el sistema.");
			return ResponseEntity.ok(isPaged ? Page.empty(pageable) : Collections.emptyList());
		}

		logger.info("## AlumnoController :: obtenerAlumnosDTO :: Se encontraron alumnos, retornando respuesta.");
		if (isPaged) {
			Page<AlumnoDTO> alumnosDTO = alumnos
					.map(AlumnoDTO::deAlumno)
					.map(this::aplicarUrlImagenPublica);
			return ResponseEntity.ok(alumnosDTO);
		}

		List<AlumnoDTO> alumnosDTO = alumnos.getContent().stream()
				.map(AlumnoDTO::deAlumno)
				.map(this::aplicarUrlImagenPublica)
				.collect(Collectors.toList());
		return ResponseEntity.ok(alumnosDTO);
	}

	/**
	 * Obtiene un alumno por su ID.
	 *
	 * @param id ID del alumno.
	 * @return ResponseEntity que contiene el alumno encontrado.
	 * @throws AlumnoNoEncontradoException si no se encuentra ningún alumno con el
	 *                                     ID especificado.
	 */
	@GetMapping("/{id}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<AlumnoDTO> obtenerAlumnoPorIdDTO(@PathVariable @NonNull Long id) {
		logger.info("## AlumnoController :: mostrarAlumnosPorId");
		Optional<AlumnoDTO> alumno = alumnoService.obtenerAlumnoPorIdDTO(id)
				.map(this::aplicarUrlImagenPublica);
		return alumno.map(value -> new ResponseEntity<>(value, HttpStatus.OK))
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + id));
	}

	@GetMapping("/{alumnoId}/imagen")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN') || hasRole('ROLE_USER')")
	public ResponseEntity<Resource> obtenerImagenAlumno(
			@PathVariable @NonNull Long alumnoId,
			@RequestParam(name = "w", required = false) Integer anchoSolicitado) {
		if (!usuarioPuedeAccederAlumno(alumnoId)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		try {
			Alumno alumno = alumnoService.buscarAlumno(alumnoId);
			Imagen imagen = alumno.getFotoAlumno();

			if (imagen == null || imagen.getRuta() == null) {
				Path rutaFallback = resolverRutaImagenAlumno(imagen);
				if (rutaFallback == null) {
					ResponseEntity<Resource> redirect = construirRedireccionImagenAlumno(imagen);
					if (redirect != null) {
						return redirect;
					}
					return ResponseEntity.notFound().build();
				}
			}

			Path rutaArchivo = resolverRutaImagenAlumno(imagen);
			if (rutaArchivo == null) {
				ResponseEntity<Resource> redirect = construirRedireccionImagenAlumno(imagen);
				if (redirect != null) {
					return redirect;
				}
				return ResponseEntity.notFound().build();
			}
			Resource recurso = new UrlResource(rutaArchivo.toUri());
			if (!recurso.exists()) {
				ResponseEntity<Resource> redirect = construirRedireccionImagenAlumno(imagen);
				if (redirect != null) {
					return redirect;
				}
				return ResponseEntity.notFound().build();
			}

			MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
			if (imagen.getTipo() != null) {
				try {
					mediaType = MediaType.parseMediaType(imagen.getTipo());
				} catch (Exception e) {
					mediaType = MediaType.APPLICATION_OCTET_STREAM;
				}
			}

			int anchoNormalizado = normalizarAnchoImagen(anchoSolicitado);
			if (anchoNormalizado > 0) {
				ResponseEntity<Resource> respuestaRedimensionada = intentarConstruirRespuestaRedimensionada(
						rutaArchivo, mediaType, imagen.getNombre(), anchoNormalizado);
				if (respuestaRedimensionada != null) {
					return respuestaRedimensionada;
				}
			}

			return construirRespuestaImagenOriginal(recurso, mediaType, imagen.getNombre(), rutaArchivo, anchoNormalizado);
		} catch (AlumnoNoEncontradoException e) {
			return ResponseEntity.notFound().build();
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}
	
    @GetMapping("/{alumnoId}/documentos")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN') || hasRole('ROLE_USER')")
    public ResponseEntity<List<Documento>> obtenerDocumentosDelAlumno(@PathVariable Long alumnoId) {
		if (!usuarioPuedeAccederAlumno(alumnoId)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
        List<Documento> documentos = alumnoService.obtenerDocumentosAlumno(alumnoId);
        return ResponseEntity.ok(documentos);
    }

	@GetMapping("/{alumnoId}/documentos/{documentoId}/descargar")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN') || hasRole('ROLE_USER')")
	public ResponseEntity<Resource> descargarDocumento(
			@PathVariable Long alumnoId,
			@PathVariable Long documentoId,
			@RequestParam(name = "download", required = false, defaultValue = "false") boolean forceDownload) {
		if (!usuarioPuedeAccederAlumno(alumnoId)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		try {
			logger.info("Attempting to download document - AlumnoId: {}, DocumentoId: {}", alumnoId, documentoId);
			Documento documento = alumnoService.obtenerDocumentoDeAlumno(alumnoId, documentoId);
			logger.info("Document found - Name: {}, Path: {}", documento.getNombre(), documento.getRuta());
			Resource recurso = documentoService.obtenerRecursoDocumento(documento);

			MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
			if (!forceDownload && documento.getTipo() != null && !documento.getTipo().isBlank()) {
				try {
					mediaType = MediaType.parseMediaType(documento.getTipo());
				} catch (Exception e) {
					logger.warn("Tipo MIME inva1lido para documento {}: {}. Se usara application/octet-stream",
							documento.getId(), documento.getTipo());
				}
			}
			String nombreDocumento = asegurarNombreConExtension(documento.getNombre(), documento.getTipo());
			String dispositionType = forceDownload ? "attachment" : "inline";

			logger.info("Sending document download response - Name: {}", documento.getNombre());
			return ResponseEntity.ok()
					.contentType(mediaType)
					.header(HttpHeaders.CONTENT_DISPOSITION,
							dispositionType + "; filename=\"" + nombreDocumento + "\"")
					.body(recurso);
		} catch (Exception e) {
			logger.error("Error downloading document - AlumnoId: {}, DocumentoId: {}. Error: {}",
					alumnoId, documentoId, e.getMessage(), e);
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
	}

	private String asegurarNombreConExtension(String nombreDocumento, String mimeType) {
		String nombreBase = (nombreDocumento == null || nombreDocumento.isBlank()) ? "documento" : nombreDocumento;
		if (tieneExtension(nombreBase)) {
			return nombreBase;
		}

		String extension = obtenerExtensionDesdeMime(mimeType);
		if (extension == null) {
			return nombreBase;
		}
		return nombreBase + "." + extension;
	}

	private boolean tieneExtension(String nombreArchivo) {
		int indiceUltimoPunto = nombreArchivo.lastIndexOf('.');
		return indiceUltimoPunto > 0 && indiceUltimoPunto < nombreArchivo.length() - 1;
	}

	private String obtenerExtensionDesdeMime(String mimeType) {
		if (mimeType == null || mimeType.isBlank()) {
			return null;
		}

		String normalizado = mimeType.split(";")[0].trim().toLowerCase();
		return switch (normalizado) {
			case "application/pdf" -> "pdf";
			case "text/csv" -> "csv";
			case "text/plain" -> "txt";
			case "image/jpeg" -> "jpg";
			case "image/png" -> "png";
			case "image/webp" -> "webp";
			case "image/gif" -> "gif";
			case "application/msword" -> "doc";
			case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
			case "application/vnd.ms-excel" -> "xls";
			case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx";
			default -> null;
		};
	}
	
    @GetMapping("/count")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<Long> countAlumnos() {
        long total = alumnoService.countAlumnos();
        return ResponseEntity.ok(total);
    }

	@GetMapping("/deportes/distribucion")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	@Transactional(readOnly = true)
	public ResponseEntity<Map<String, Long>> obtenerDistribucionActivaPorDeporte() {
		return ResponseEntity.ok(alumnoDeporteService.obtenerDistribucionActivaPorDeporte());
	}

	/**
	 * Obtiene los grupos a los que pertenece un alumno especificado por su ID.
	 *
	 * @param alumnoId El ID del alumno cuyos grupos se desean obtener.
	 * @return ResponseEntity que contiene una lista de GrupoResponseDTO si se
	 *         encuentran grupos; ResponseEntity.notFound() si no se encuentran
	 *         grupos para el alumno especificado.
	 */
	@GetMapping("/{alumnoId}/grupos")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN') || hasRole('ROLE_USER')")
	public ResponseEntity<List<GrupoResponseDTO>> obtenerGruposDeAlumno(@PathVariable @NonNull Long alumnoId) {
		if (!usuarioPuedeAccederAlumno(alumnoId)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		List<GrupoResponseDTO> gruposDTO = grupoService.obtenerGruposDelAlumno(alumnoId);
		return ResponseEntity.ok(gruposDTO);
	}

	/**
	 * Asigna un alumno a un grupo.
	 *
	 * @param alumnoId El ID del alumno.
	 * @param grupoId  El ID del grupo.
	 * @return ResponseEntity con el estado de la operación.
	 */
	@PostMapping("/{alumnoId}/grupos/{grupoId}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> asignarAlumnoAGrupo(@PathVariable Long alumnoId, @PathVariable Long grupoId) {
		try {
			grupoService.agregarAlumnoAGrupo(grupoId, alumnoId);
			return ResponseEntity.ok(Map.of("message", "Alumno asignado al grupo con éxito"));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(Map.of("error", "InvalidArgument", "message", e.getMessage()));
		}
	}

	/**
	 * Remueve a un alumno de un grupo.
	 *
	 * @param alumnoId El ID del alumno.
	 * @param grupoId  El ID del grupo.
	 * @return ResponseEntity con el estado de la operación.
	 */
	@DeleteMapping("/{alumnoId}/grupos/{grupoId}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> removerAlumnoDeGrupo(@PathVariable Long alumnoId, @PathVariable Long grupoId) {
		try {
			grupoService.eliminarAlumnoDeGrupo(grupoId, alumnoId);
			// Obtener la lista actualizada de grupos
			List<GrupoResponseDTO> gruposActualizados = grupoService.obtenerGruposDelAlumno(alumnoId);
			// Retornar la lista actualizada
			return ResponseEntity.ok(gruposActualizados);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(Map.of("error", "InvalidArgument", "message", e.getMessage()));
		}
	}

	/**
	 * Crea un nuevo alumno.
	 *
	 * @param nuevoAlumnoDTO Datos del nuevo alumno a crear en formato JSON.
	 * @param file           Archivo de imagen opcional del alumno.
	 * @return ResponseEntity que contiene el alumno creado en formato JSON.
	 * @throws FechaNacimientoInvalidaException si la fecha de nacimiento
	 *                                          proporcionada es inválida.
	 * @throws DatosAlumnoInvalidosException    si los datos del alumno son
	 *                                          inválidos.
	 * @throws AlumnoDuplicadoException         si ya existe un alumno con el mismo
	 *                                          NIF.
	 */
	@PostMapping(value = "/crear", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> crearAlumno(@Valid @RequestParam("nuevo") String alumnoJson,
			@RequestParam(required = false) MultipartFile file) {
		try {
			// Convertir el JSON recibido en AlumnoDTO
			ObjectMapper objectMapper = new ObjectMapper();
			AlumnoDTO nuevoAlumnoDTO = objectMapper.readValue(alumnoJson, AlumnoDTO.class);

			// Si se proporciona un archivo de imagen, guardar la imagen
			if (file != null && !file.isEmpty()) {
				Imagen img = imagenService.guardarImagen(file); // Guardar la imagen
				nuevoAlumnoDTO.setFotoAlumno(img); // Asignar la imagen al DTO
			}

			// Delegar al servicio para crear el Alumno
			Alumno creado = alumnoService.crearAlumnoDesdeDTO(nuevoAlumnoDTO);

			// Convertir el Alumno creado a DTO para la respuesta
			AlumnoDTO creadoDTO = aplicarUrlImagenPublica(AlumnoDTO.deAlumno(creado));

			// Retornar la respuesta con el Alumno creado en formato JSON
			return new ResponseEntity<>(creadoDTO, HttpStatus.CREATED);

		} catch (AlumnoDuplicadoException e) {
			logger.error("Error de duplicado de alumno: {}", e.getMessage(), e);
			return new ResponseEntity<>(e.getMessage(), HttpStatus.CONFLICT);
		} catch (IOException e) {
			logger.error("Error al procesar el archivo o el JSON: {}", e.getMessage(), e);
			return new ResponseEntity<>("Error al procesar la solicitud", HttpStatus.BAD_REQUEST);
		} catch (Exception e) {
			logger.error("Error inesperado al crear el alumno: {}", e.getMessage(), e); // Log de excepciones genéricas
			return new ResponseEntity<>("Error inesperado al crear el alumno", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
    @PostMapping("/{alumnoId}/documentos")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> subirDocumento(
            @PathVariable Long alumnoId,
            @RequestParam MultipartFile archivo
    ) {
        try {
            Documento documento = alumnoService.agregarDocumentoAAlumno(alumnoId, archivo);
            return ResponseEntity.ok(documento);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body("Error al subir el documento: " + e.getMessage());
        }
    }

	@PutMapping("/{id}/baja")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> darDeBajaAlumno(@PathVariable @NonNull Long id) {
		alumnoService.darDeBajaAlumno(id);
		return ResponseEntity.ok().build();
	}

	@PutMapping("/{id}/alta")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> darDeAltaAlumno(@PathVariable @NonNull Long id) {
		alumnoService.darDeAltaAlumno(id);
		return ResponseEntity.ok().build();
	}

	/**
	 * Actualiza la información de un alumno existente.
	 *
	 * @param id         ID del alumno a actualizar.
	 * @param file       Archivo de imagen opcional del alumno.
	 * @param alumnoJson Datos actualizados del alumno en formato JSON.
	 * @return ResponseEntity que contiene el alumno actualizado en formato JSON.
	 * @throws FechaNacimientoInvalidaException si la fecha de nacimiento
	 *                                          proporcionada es inválida.
	 * @throws DatosAlumnoInvalidosException    si los datos del alumno actualizado
	 *                                          son inválidos.
	 */
	@PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> actualizarAlumno(@PathVariable @NonNull Long id,
			@Valid @RequestParam(required = false) MultipartFile file,
			@Valid @RequestParam("alumnoEditado") String alumnoJson) {
		logger.info("## AlumnoController :: modificarAlumno");
		try {
			ObjectMapper objectMapper = new ObjectMapper();
			AlumnoDTO nuevoAlumnoDTO = objectMapper.readValue(alumnoJson, AlumnoDTO.class);

			if (!alumnoService.fechaNacimientoValida(nuevoAlumnoDTO.getFechaNacimiento())) {
				throw new FechaNacimientoInvalidaException("La fecha de nacimiento es inválida.");
			}

			boolean requiereTarifaYFechaAlta = alumnoDeporteService.obtenerDeportesDelAlumno(id).isEmpty();
			if (!alumnoService.datosAlumnoValidos(nuevoAlumnoDTO, requiereTarifaYFechaAlta)) {
				throw new DatosAlumnoInvalidosException("Los datos del alumno actualizado son inválidos.");
			}

			Date nuevaFechaNacimiento = nuevoAlumnoDTO.getFechaNacimiento();
			// Enviar el MultipartFile directamente
			Alumno alumno = alumnoService.actualizarAlumno(id, nuevoAlumnoDTO, nuevaFechaNacimiento, file);
			AlumnoDTO alumnoActualizadoDTO = aplicarUrlImagenPublica(AlumnoDTO.deAlumno(alumno));
			return new ResponseEntity<>(alumnoActualizadoDTO, HttpStatus.OK);

		} catch (IOException e) {
			return new ResponseEntity<>("Error al procesar la solicitud", HttpStatus.BAD_REQUEST);
		}
	}

	/**
	 * Actualiza las observaciones de un alumno.
	 *
	 * @param id ID del alumno a actualizar.
	 * @param request DTO con las observaciones.
	 * @return ResponseEntity con el alumno actualizado.
	 */
	@PutMapping("/{id}/observaciones")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> actualizarObservaciones(@PathVariable @NonNull Long id,
			@Valid @RequestBody AlumnoObservacionesDTO request) {
		Alumno alumno = alumnoService.actualizarObservaciones(id,
				request != null ? request.getObservaciones() : null);
		AlumnoDTO alumnoActualizadoDTO = aplicarUrlImagenPublica(AlumnoDTO.deAlumno(alumno));
		return new ResponseEntity<>(alumnoActualizadoDTO, HttpStatus.OK);
	}

	/**
	 * Elimina la imagen de un alumno especificado por su ID.
	 *
	 * @param id El ID del alumno cuya imagen se eliminará.
	 * @return ResponseEntity que indica el resultado de la eliminación. Retorna
	 *         ResponseEntity.ok() si la eliminación es exitosa.
	 * @throws Exception Si ocurre un error durante la eliminación de la imagen del
	 *                   alumno.
	 */
	@DeleteMapping("/{id}/imagen")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> eliminarImagenAlumno(@PathVariable @NonNull Long id) {
		try {
			alumnoService.eliminarImagenAlumno(id);
			return ResponseEntity.ok().build();
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("Error al eliminar la imagen del alumno.");
		}
	}
	
    @DeleteMapping("/{alumnoId}/documentos/{documentoId}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> eliminarDocumento(
            @PathVariable Long alumnoId,
            @PathVariable Long documentoId
    ) {
        try {
            alumnoService.eliminarDocumentoDeAlumno(alumnoId, documentoId);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

	/**
	 * Elimina un alumno existente y su imagen asociada.
	 *
	 * @param id ID del alumno a eliminar.
	 * @return ResponseEntity con el estado de la operación.
	 */
	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<Void> eliminarAlumno(@Valid @PathVariable @NonNull Long id) {
		logger.info("## AlumnoController :: eliminarAlumno");
		boolean eliminado = alumnoService.eliminarAlumno(id);
		return eliminado ? new ResponseEntity<>(HttpStatus.OK) : new ResponseEntity<>(HttpStatus.NOT_FOUND);
	}

	/**
	 * Obtiene los turnos asociados a un alumno específico.
	 *
	 * @param alumnoId El ID del alumno.
	 * @return ResponseEntity que contiene una lista de TurnoDTO.
	 */
	@GetMapping("/{alumnoId}/turnos")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN') || hasRole('ROLE_USER')")
	public ResponseEntity<List<TurnoDTO>> obtenerTurnosDelAlumno(@PathVariable Long alumnoId) {
		if (!usuarioPuedeAccederAlumno(alumnoId)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		List<TurnoDTO> turnos = alumnoService.obtenerTurnosDelAlumno(alumnoId);
		return ResponseEntity.ok(turnos);
	}

	@GetMapping("/{alumnoId}/reto-diario")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN') || hasRole('ROLE_USER')")
	public ResponseEntity<RetoDiarioEstadoDTO> obtenerEstadoRetoDiario(@PathVariable Long alumnoId) {
		if (!usuarioPuedeAccederAlumno(alumnoId)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		return ResponseEntity.ok(alumnoService.obtenerEstadoRetoDiario(alumnoId));
	}

	@PutMapping("/{alumnoId}/reto-diario/completar")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN') || hasRole('ROLE_USER')")
	public ResponseEntity<RetoDiarioEstadoDTO> completarRetoDiario(@PathVariable Long alumnoId) {
		if (!usuarioPuedeAccederAlumno(alumnoId)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		return ResponseEntity.ok(alumnoService.completarRetoDiario(alumnoId));
	}

	@GetMapping("/{alumnoId}/reto-diario/ranking-semanal")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN') || hasRole('ROLE_USER')")
	public ResponseEntity<?> obtenerRankingSemanalRetoDiario(
			@PathVariable Long alumnoId,
			@RequestParam(name = "deporte") String deporteParam,
			@RequestParam(name = "limit", required = false) Integer limit) {
		if (!usuarioPuedeAccederAlumno(alumnoId)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		if (deporteParam == null || deporteParam.isBlank()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(Map.of("error", "InvalidArgument", "message", "El parámetro deporte es obligatorio."));
		}

		if (limit != null && (limit < 1 || limit > 10)) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(Map.of("error", "InvalidArgument", "message", "El parámetro limit debe estar entre 1 y 10."));
		}

		Deporte deporte;
		try {
			deporte = Deporte.valueOf(deporteParam.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(Map.of("error", "InvalidArgument", "message", "Deporte inválido: " + deporteParam));
		}

		try {
			RetoDiarioRankingSemanalResponse ranking = alumnoService.obtenerRankingSemanalRetoDiario(alumnoId, deporte,
					limit);
			return ResponseEntity.ok(ranking);
		} catch (AlumnoNoEncontradoException ex) {
			return ResponseEntity.notFound().build();
		} catch (IllegalArgumentException ex) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(Map.of("error", "InvalidArgument", "message", ex.getMessage()));
		}
	}

	@GetMapping("/{alumnoId}/reto-diario/ranking-general")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN') || hasRole('ROLE_USER')")
	public ResponseEntity<?> obtenerRankingGeneralRetoDiario(
			@PathVariable Long alumnoId,
			@RequestParam(name = "deporte") String deporteParam,
			@RequestParam(name = "limit", required = false) Integer limit) {
		if (!usuarioPuedeAccederAlumno(alumnoId)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		if (deporteParam == null || deporteParam.isBlank()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(Map.of("error", "InvalidArgument", "message", "El parámetro deporte es obligatorio."));
		}

		if (limit != null && (limit < 1 || limit > 10)) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(Map.of("error", "InvalidArgument", "message", "El parámetro limit debe estar entre 1 y 10."));
		}

		Deporte deporte;
		try {
			deporte = Deporte.valueOf(deporteParam.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(Map.of("error", "InvalidArgument", "message", "Deporte inválido: " + deporteParam));
		}

		try {
			RetoDiarioRankingGeneralResponse ranking = alumnoService.obtenerRankingGeneralRetoDiario(alumnoId, deporte,
					limit);
			return ResponseEntity.ok(ranking);
		} catch (AlumnoNoEncontradoException ex) {
			return ResponseEntity.notFound().build();
		} catch (IllegalArgumentException ex) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(Map.of("error", "InvalidArgument", "message", ex.getMessage()));
		}
	}

	/**
	 * Asigna un alumno a un turno específico dentro de un grupo al que ya
	 * pertenece.
	 *
	 * @param alumnoId El ID del alumno.
	 * @param turnoId  El ID del turno.
	 * @return ResponseEntity con el estado de la operación.
	 */
	@PostMapping("/{alumnoId}/turnos/{turnoId}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> asignarAlumnoATurno(@PathVariable Long alumnoId, @PathVariable Long turnoId) {
		try {
			alumnoService.asignarAlumnoATurno(alumnoId, turnoId);
			// Respuesta exitosa en formato JSON
			return ResponseEntity.ok(Map.of("message", "Alumno asignado al turno y grupo con éxito"));
		} catch (IllegalArgumentException e) {
			// Respuesta de error en formato JSON
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(Map.of("error", "InvalidArgument", "message", e.getMessage()));
		}
	}

	/**
	 * Remueve a un alumno de un turno específico.
	 *
	 * @param alumnoId El ID del alumno.
	 * @param turnoId  El ID del turno.
	 * @return ResponseEntity con el estado de la operación.
	 */
	@DeleteMapping("/{alumnoId}/turnos/{turnoId}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> removerAlumnoDeTurno(@PathVariable Long alumnoId, @PathVariable Long turnoId) {
		try {
			alumnoService.removerAlumnoDeTurno(alumnoId, turnoId);
			// Obtener la lista actualizada de turnos
			List<TurnoDTO> turnosActualizados = alumnoService.obtenerTurnosDelAlumno(alumnoId);
			// Retornar la lista actualizada
			return ResponseEntity.ok(turnosActualizados);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(Map.of("error", "InvalidArgument", "message", e.getMessage()));
		}
	}

	// Endpoint para obtener todos los alumnos aptos para examen
	@GetMapping("/aptos")
	public ResponseEntity<List<AlumnoConGruposDTO>> obtenerAlumnosAptosParaExamen() {
		List<AlumnoConGruposDTO> alumnos = alumnoService.obtenerAlumnosAptosConGruposDTO();
		return ResponseEntity.ok(alumnos);
	}

	// Endpoint para obtener alumnos aptos para examen por deporte
	@GetMapping("/aptos/deporte")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<List<AlumnoConGruposDTO>> obtenerAlumnosAptosPorDeporte(@RequestParam String deporte) {
		String exclusion = "competición";
		if (deporte.equalsIgnoreCase("kickboxing") || deporte.equalsIgnoreCase("pilates")
				|| deporte.equalsIgnoreCase("defensa personal femenina")) {
			exclusion = "";
		}

		List<AlumnoConGruposDTO> alumnosAptos = alumnoService.obtenerAlumnosAptosPorDeporte(deporte, exclusion);
		return ResponseEntity.ok(alumnosAptos);
	}

	// Endpoint para obtener un alumno apto para examen por su ID
	@GetMapping("/aptos/{id}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<AlumnoConGruposDTO> obtenerAlumnoAptoPorId(@PathVariable Long id) {
		Optional<AlumnoConGruposDTO> alumno = alumnoService.obtenerAlumnoAptoPorId(id);

		// Si el alumno está presente, lo devolvemos en la respuesta
		return alumno.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

	// Endpoint para obtener alumnos elegibles para una convocatoria específica
	@GetMapping("/aptos/convocatoria")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<List<AlumnoConGruposDTO>> obtenerAlumnosElegiblesParaConvocatoria(
			@RequestParam com.taemoi.project.entities.Deporte deporte) {
		List<AlumnoConGruposDTO> alumnosElegibles = alumnoService.obtenerAlumnosElegiblesParaConvocatoria(deporte);
		return ResponseEntity.ok(alumnosElegibles);
	}

	@PostMapping("/{convocatoriaId}/alumno/{alumnoId}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> agregarAlumnoAConvocatoria(@PathVariable Long alumnoId, @PathVariable Long convocatoriaId,
			@RequestBody(required = false) Map<String, Object> params) {

		try {
			boolean porRecompensa = params != null && Boolean.TRUE.equals(params.get("porRecompensa"));
			boolean rojoBordado = params != null && Boolean.TRUE.equals(params.get("rojoBordado"));
			AlumnoConvocatoriaDTO alumnoConvocatoriaDTO = alumnoService.agregarAlumnoAConvocatoria(alumnoId,
					convocatoriaId, porRecompensa, rojoBordado);

			return ResponseEntity.ok(alumnoConvocatoriaDTO);
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		}
	}

	@DeleteMapping("/{convocatoriaId}/alumno/{alumnoId}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> eliminarAlumnoDeConvocatoria(@PathVariable Long convocatoriaId,
			@PathVariable Long alumnoId) {

		alumnoService.eliminarAlumnoDeConvocatoria(alumnoId, convocatoriaId);
		return ResponseEntity.noContent().build();
	}

	// ==================== ENDPOINTS MULTI-DEPORTE ====================

	/**
	 * Obtiene todos los deportes de un alumno (activos e inactivos)
	 * GET /api/alumnos/{id}/deportes
	 */
	@GetMapping("/{id}/deportes")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN') || hasRole('ROLE_USER')")
	public ResponseEntity<List<com.taemoi.project.dtos.AlumnoDeporteDTO>> obtenerDeportesDelAlumno(@PathVariable Long id) {
		if (!usuarioPuedeAccederAlumno(id)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		try {
			// Changed to obtenerDeportesDelAlumno to include inactive sports for reactivation
			List<com.taemoi.project.entities.AlumnoDeporte> deportes = alumnoDeporteService.obtenerDeportesDelAlumno(id);
			if (deportes == null) {
				return ResponseEntity.ok(java.util.Collections.emptyList());
			}
			List<com.taemoi.project.dtos.AlumnoDeporteDTO> deportesDTO = deportes.stream()
					.map(com.taemoi.project.dtos.AlumnoDeporteDTO::deAlumnoDeporte)
					.collect(Collectors.toList());
			return ResponseEntity.ok(deportesDTO);
		} catch (Exception e) {
			logger.error("Error al obtener deportes del alumno {}: {}", id, e.getMessage(), e);
			return ResponseEntity.ok(java.util.Collections.emptyList());
		}
	}

	@GetMapping("/{alumnoId}/deportes/{deporte}/material-examen")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN') || hasRole('ROLE_USER')")
	public ResponseEntity<?> obtenerMaterialExamen(
			@PathVariable Long alumnoId,
			@PathVariable String deporte) {
		if (!usuarioPuedeAccederAlumno(alumnoId)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		try {
			Deporte deporteEnum = Deporte.valueOf(deporte.toUpperCase(Locale.ROOT));
			MaterialExamenDTO material = materialExamenService.obtenerMaterialExamen(alumnoId, deporteEnum);
			return ResponseEntity.ok(material);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			logger.error("Error al obtener material de examen. Alumno: {}, deporte: {}", alumnoId, deporte, e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Error al obtener material de examen"));
		}
	}

	@GetMapping("/{alumnoId}/deportes/{deporte}/material-examen/temario")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN') || hasRole('ROLE_USER')")
	public ResponseEntity<Resource> descargarTemarioMaterialExamen(
			@PathVariable Long alumnoId,
			@PathVariable String deporte,
			@RequestParam(name = "download", required = false, defaultValue = "false") boolean download,
			@RequestHeader HttpHeaders requestHeaders) {
		if (!usuarioPuedeAccederAlumno(alumnoId)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		try {
			Deporte deporteEnum = Deporte.valueOf(deporte.toUpperCase(Locale.ROOT));
			MaterialExamenService.MaterialExamenArchivo temario = materialExamenService.obtenerTemario(alumnoId, deporteEnum);
			if (!esTemarioPermitido(temario.getMimeType())) {
				return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
			}
			return construirRespuestaArchivoConRange(temario, requestHeaders, download);
		} catch (NoSuchElementException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().build();
		} catch (Exception e) {
			logger.error("Error al descargar temario de material de examen. Alumno: {}, deporte: {}", alumnoId, deporte, e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	@GetMapping("/{alumnoId}/deportes/{deporte}/material-examen/documentacion/{documentoFile}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN') || hasRole('ROLE_USER')")
	public ResponseEntity<Resource> descargarDocumentoMaterialExamen(
			@PathVariable Long alumnoId,
			@PathVariable String deporte,
			@PathVariable String documentoFile,
			@RequestParam(name = "download", required = false, defaultValue = "false") boolean download,
			@RequestHeader HttpHeaders requestHeaders) {
		if (!usuarioPuedeAccederAlumno(alumnoId)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		try {
			Deporte deporteEnum = Deporte.valueOf(deporte.toUpperCase(Locale.ROOT));
			MaterialExamenService.MaterialExamenArchivo documento = materialExamenService
					.obtenerDocumento(alumnoId, deporteEnum, documentoFile);
			boolean forzarDownload = download || !esTemarioPermitido(documento.getMimeType());
			return construirRespuestaArchivoConRange(documento, requestHeaders, forzarDownload);
		} catch (NoSuchElementException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().build();
		} catch (Exception e) {
			logger.error("Error al servir documento de material de examen. Alumno: {}, deporte: {}, documento: {}",
					alumnoId, deporte, documentoFile, e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	@GetMapping("/{alumnoId}/deportes/{deporte}/material-examen/videos/{videoFile}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN') || hasRole('ROLE_USER')")
	public ResponseEntity<Resource> streamVideoMaterialExamen(
			@PathVariable Long alumnoId,
			@PathVariable String deporte,
			@PathVariable String videoFile,
			@RequestHeader HttpHeaders requestHeaders) {
		if (!usuarioPuedeAccederAlumno(alumnoId)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		try {
			Deporte deporteEnum = Deporte.valueOf(deporte.toUpperCase(Locale.ROOT));
			MaterialExamenService.MaterialExamenArchivo video = materialExamenService.obtenerVideo(alumnoId, deporteEnum, videoFile);
			if (!esVideoPermitido(video.getMimeType())) {
				return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
			}
			return construirRespuestaVideoConRange(video, requestHeaders);
		} catch (NoSuchElementException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().build();
		} catch (Exception e) {
			logger.error("Error al servir video de material de examen. Alumno: {}, deporte: {}, video: {}",
					alumnoId, deporte, videoFile, e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	/**
	 * Agrega un deporte a un alumno
	 * POST /api/alumnos/{id}/deportes
	 * Body: { "deporte": "TAEKWONDO", "gradoInicial": "BLANCO", "fechaAlta": "2024-01-15", "fechaAltaInicial": "2024-01-15", "fechaGrado": "2024-01-15" }
	 */
	@PostMapping("/{id}/deportes")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> agregarDeporteAAlumno(@PathVariable Long id,
			@RequestBody Map<String, String> params) {
		try {
			String deporteStr = params.get("deporte");
			String gradoStr = params.get("gradoInicial");
			String fechaAltaStr = params.get("fechaAlta");
			String fechaAltaInicialStr = params.get("fechaAltaInicial");
			String fechaGradoStr = params.get("fechaGrado");

			if (deporteStr == null) {
				return ResponseEntity.badRequest().body("El deporte es requerido");
			}

			com.taemoi.project.entities.Deporte deporte = com.taemoi.project.entities.Deporte.valueOf(deporteStr);
			com.taemoi.project.entities.TipoGrado gradoInicial = gradoStr != null
					? com.taemoi.project.entities.TipoGrado.valueOf(gradoStr)
					: null;

			// Parse dates if provided (format: YYYY-MM-DD)
			java.util.Date fechaAlta = null;
			java.util.Date fechaAltaInicial = null;
			java.util.Date fechaGrado = null;

			if (fechaAltaStr != null && !fechaAltaStr.isEmpty()) {
				try {
					fechaAlta = java.sql.Date.valueOf(fechaAltaStr);
				} catch (Exception e) {
					return ResponseEntity.badRequest().body("Formato de fecha de alta inválido. Use YYYY-MM-DD");
				}
			}

			if (fechaAltaInicialStr != null && !fechaAltaInicialStr.isEmpty()) {
				try {
					fechaAltaInicial = java.sql.Date.valueOf(fechaAltaInicialStr);
				} catch (Exception e) {
					return ResponseEntity.badRequest().body("Formato de fecha de alta inicial inválido. Use YYYY-MM-DD");
				}
			}

			if (fechaGradoStr != null && !fechaGradoStr.isEmpty()) {
				try {
					fechaGrado = java.sql.Date.valueOf(fechaGradoStr);
				} catch (Exception e) {
					return ResponseEntity.badRequest().body("Formato de fecha de grado inválido. Use YYYY-MM-DD");
				}
			}

			com.taemoi.project.entities.AlumnoDeporte alumnoDeporte = alumnoDeporteService.agregarDeporteAAlumno(
					id,
					deporte,
					gradoInicial,
					fechaAlta,
					fechaAltaInicial,
					fechaGrado);
			com.taemoi.project.dtos.AlumnoDeporteDTO dto = com.taemoi.project.dtos.AlumnoDeporteDTO.deAlumnoDeporte(alumnoDeporte);

			return ResponseEntity.ok(dto);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			logger.error("Error al agregar deporte al alumno", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al agregar deporte");
		}
	}

	/**
	 * Elimina un deporte de un alumno
	 * DELETE /api/alumnos/{id}/deportes/{deporte}
	 */
	/**
	 * Desactiva un deporte de un alumno (soft delete - mantiene todos los datos)
	 */
	@PutMapping("/{id}/deportes/{deporte}/desactivar")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> desactivarDeporteDeAlumno(@PathVariable Long id, @PathVariable String deporte) {
		try {
			com.taemoi.project.entities.Deporte deporteEnum = com.taemoi.project.entities.Deporte.valueOf(deporte);
			alumnoDeporteService.desactivarDeporteDeAlumno(id, deporteEnum);
			return ResponseEntity.ok().body("Deporte desactivado correctamente");
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			logger.error("Error al desactivar deporte del alumno", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al desactivar deporte");
		}
	}

	/**
	 * Activa un deporte de un alumno que estaba inactivo (preserva todos los datos)
	 */
	@PutMapping("/{id}/deportes/{deporte}/activar")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> activarDeporteDeAlumno(@PathVariable Long id, @PathVariable String deporte) {
		try {
			com.taemoi.project.entities.Deporte deporteEnum = com.taemoi.project.entities.Deporte.valueOf(deporte);
			alumnoDeporteService.activarDeporteDeAlumno(id, deporteEnum);
			return ResponseEntity.ok().body("Deporte activado correctamente");
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			logger.error("Error al activar deporte del alumno", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al activar deporte");
		}
	}

	/**
	 * Remueve completamente un deporte de un alumno (hard delete - eliminación física)
	 */
	/**
	 * Marca un deporte como principal para un alumno
	 * PUT /api/alumnos/{id}/deportes/{deporte}/principal
	 */
	@PutMapping("/{id}/deportes/{deporte}/principal")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> establecerDeportePrincipal(@PathVariable Long id, @PathVariable String deporte) {
		try {
			com.taemoi.project.entities.Deporte deporteEnum = com.taemoi.project.entities.Deporte.valueOf(deporte);
			com.taemoi.project.entities.AlumnoDeporte alumnoDeporte = alumnoDeporteService
					.establecerDeportePrincipal(id, deporteEnum);
			com.taemoi.project.dtos.AlumnoDeporteDTO dto = com.taemoi.project.dtos.AlumnoDeporteDTO
					.deAlumnoDeporte(alumnoDeporte);
			return ResponseEntity.ok(dto);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			logger.error("Error al establecer deporte principal", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al establecer deporte principal");
		}
	}

	@DeleteMapping("/{id}/deportes/{deporte}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> removerDeporteDeAlumno(@PathVariable Long id, @PathVariable String deporte) {
		try {
			com.taemoi.project.entities.Deporte deporteEnum = com.taemoi.project.entities.Deporte.valueOf(deporte);
			alumnoService.removerDeporteDeAlumno(id, deporteEnum);
			return ResponseEntity.noContent().build();
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			logger.error("Error al remover deporte del alumno", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al remover deporte");
		}
	}

	/**
	 * Actualiza el grado de un alumno en un deporte específico
	 * PUT /api/alumnos/{id}/deportes/{deporte}/grado
	 * Body: { "nuevoGrado": "AZUL" }
	 */
	@PutMapping("/{id}/deportes/{deporte}/grado")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> actualizarGradoPorDeporte(@PathVariable Long id, @PathVariable String deporte,
			@RequestBody Map<String, String> params) {
		try {
			String nuevoGradoStr = params.get("nuevoGrado");
			if (nuevoGradoStr == null) {
				return ResponseEntity.badRequest().body("El grado es requerido");
			}

			com.taemoi.project.entities.Deporte deporteEnum = com.taemoi.project.entities.Deporte.valueOf(deporte);
			com.taemoi.project.entities.TipoGrado nuevoGrado = com.taemoi.project.entities.TipoGrado.valueOf(nuevoGradoStr);

			com.taemoi.project.entities.AlumnoDeporte alumnoDeporte = alumnoService.actualizarGradoPorDeporte(id, deporteEnum, nuevoGrado);
			com.taemoi.project.dtos.AlumnoDeporteDTO dto = com.taemoi.project.dtos.AlumnoDeporteDTO.deAlumnoDeporte(alumnoDeporte);

			return ResponseEntity.ok(dto);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			logger.error("Error al actualizar grado por deporte", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al actualizar grado");
		}
	}

	/**
	 * Pasa de grado por recompensa en un deporte específico y asigna el producto correspondiente
	 * POST /api/alumnos/{id}/deportes/{deporte}/pase-recompensa
	 */
	@PostMapping("/{id}/deportes/{deporte}/pase-recompensa")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> pasarGradoPorRecompensa(@PathVariable Long id, @PathVariable String deporte,
			@RequestBody(required = false) Map<String, Object> params) {
		try {
			boolean rojoBordado = params != null && Boolean.TRUE.equals(params.get("rojoBordado"));
			com.taemoi.project.entities.Deporte deporteEnum = com.taemoi.project.entities.Deporte.valueOf(deporte);
			com.taemoi.project.entities.AlumnoDeporte alumnoDeporte = alumnoService.pasarGradoPorRecompensa(id,
					deporteEnum, rojoBordado);
			com.taemoi.project.dtos.AlumnoDeporteDTO dto = com.taemoi.project.dtos.AlumnoDeporteDTO
					.deAlumnoDeporte(alumnoDeporte);
			return ResponseEntity.ok(dto);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			logger.error("Error al pasar de grado por recompensa", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al pasar de grado por recompensa");
		}
	}

	/**
	 * Pasa de grado con derecho de examen (no recompensa) y asigna el producto correspondiente
	 * POST /api/alumnos/{id}/deportes/{deporte}/pase-examen
	 */
	@PostMapping("/{id}/deportes/{deporte}/pase-examen")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> pasarGradoConDerechoExamen(@PathVariable Long id, @PathVariable String deporte,
			@RequestBody(required = false) Map<String, Object> params) {
		try {
			boolean rojoBordado = params != null && Boolean.TRUE.equals(params.get("rojoBordado"));
			com.taemoi.project.entities.Deporte deporteEnum = com.taemoi.project.entities.Deporte.valueOf(deporte);
			com.taemoi.project.entities.AlumnoDeporte alumnoDeporte = alumnoService.pasarGradoConDerechoExamen(id,
					deporteEnum, rojoBordado);
			com.taemoi.project.dtos.AlumnoDeporteDTO dto = com.taemoi.project.dtos.AlumnoDeporteDTO
					.deAlumnoDeporte(alumnoDeporte);
			return ResponseEntity.ok(dto);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			logger.error("Error al pasar de grado con derecho de examen", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al pasar de grado con derecho de examen");
		}
	}

	/**
	 * Actualiza el estado de aptoParaExamen para un deporte específico
	 * PUT /api/alumnos/{id}/deportes/{deporte}/apto-examen
	 * Body: { "aptoParaExamen": true }
	 */
	@PutMapping("/{id}/deportes/{deporte}/apto-examen")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> actualizarAptoParaExamen(@PathVariable Long id, @PathVariable String deporte,
			@RequestBody Map<String, Boolean> params) {
		try {
			Boolean aptoParaExamen = params.get("aptoParaExamen");
			if (aptoParaExamen == null) {
				return ResponseEntity.badRequest().body("El campo aptoParaExamen es requerido");
			}

			com.taemoi.project.entities.Deporte deporteEnum = com.taemoi.project.entities.Deporte.valueOf(deporte);
			com.taemoi.project.entities.AlumnoDeporte alumnoDeporte = alumnoDeporteService
					.actualizarAptoParaExamen(id, deporteEnum, aptoParaExamen);
			com.taemoi.project.dtos.AlumnoDeporteDTO dto = com.taemoi.project.dtos.AlumnoDeporteDTO
					.deAlumnoDeporte(alumnoDeporte);

			return ResponseEntity.ok(dto);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			logger.error("Error al actualizar apto para examen", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al actualizar apto para examen");
		}
	}

	/**
	 * Actualiza la fecha de grado para un deporte específico
	 * PUT /api/alumnos/{id}/deportes/{deporte}/fecha-grado
	 * Body: { "fechaGrado": "2025-01-15" }
	 */
	@PutMapping("/{id}/deportes/{deporte}/fecha-grado")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> actualizarFechaGrado(@PathVariable Long id, @PathVariable String deporte,
			@RequestBody Map<String, String> params) {
		try {
			String fechaGradoStr = params.get("fechaGrado");
			if (fechaGradoStr == null) {
				return ResponseEntity.badRequest().body("La fecha de grado es requerida");
			}

			com.taemoi.project.entities.Deporte deporteEnum = com.taemoi.project.entities.Deporte.valueOf(deporte);
			java.util.Date fechaGrado = java.sql.Date.valueOf(fechaGradoStr);

			com.taemoi.project.entities.AlumnoDeporte alumnoDeporte = alumnoDeporteService
					.actualizarFechaGrado(id, deporteEnum, fechaGrado);
			com.taemoi.project.dtos.AlumnoDeporteDTO dto = com.taemoi.project.dtos.AlumnoDeporteDTO
					.deAlumnoDeporte(alumnoDeporte);

			return ResponseEntity.ok(dto);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			logger.error("Error al actualizar fecha de grado", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al actualizar fecha de grado");
		}
	}

	/**
	 * Actualiza la fecha de alta per-sport
	 * PUT /api/alumnos/{id}/deportes/{deporte}/fecha-alta
	 * Body: { "fechaAlta": "2025-01-15" } o { "fechaAlta": null }
	 */
	@PutMapping("/{id}/deportes/{deporte}/fecha-alta")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> actualizarFechaAltaDeporte(@PathVariable Long id, @PathVariable String deporte,
			@RequestBody Map<String, String> params) {
		try {
			if (!params.containsKey("fechaAlta")) {
				return ResponseEntity.badRequest().body("El campo fechaAlta es requerido");
			}

			String fechaAltaStr = params.get("fechaAlta");
			java.util.Date fechaAlta = null;
			if (fechaAltaStr != null && !fechaAltaStr.isBlank()) {
				fechaAlta = java.sql.Date.valueOf(fechaAltaStr);
			}

			com.taemoi.project.entities.Deporte deporteEnum = com.taemoi.project.entities.Deporte.valueOf(deporte);
			com.taemoi.project.entities.AlumnoDeporte alumnoDeporte = alumnoDeporteService
					.actualizarFechaAlta(id, deporteEnum, fechaAlta);
			com.taemoi.project.dtos.AlumnoDeporteDTO dto = com.taemoi.project.dtos.AlumnoDeporteDTO
					.deAlumnoDeporte(alumnoDeporte);

			return ResponseEntity.ok(dto);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			logger.error("Error al actualizar fecha de alta", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al actualizar fecha de alta");
		}
	}

	/**
	 * Actualiza la fecha de baja per-sport
	 * PUT /api/alumnos/{id}/deportes/{deporte}/fecha-baja
	 * Body: { "fechaBaja": "2025-01-15" }
	 */
	@PutMapping("/{id}/deportes/{deporte}/fecha-baja")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> actualizarFechaBajaDeporte(@PathVariable Long id, @PathVariable String deporte,
			@RequestBody Map<String, String> params) {
		try {
			if (!params.containsKey("fechaBaja")) {
				return ResponseEntity.badRequest().body("El campo fechaBaja es requerido");
			}

			String fechaBajaStr = params.get("fechaBaja");
			java.util.Date fechaBaja = null;
			if (fechaBajaStr != null && !fechaBajaStr.isBlank()) {
				fechaBaja = java.sql.Date.valueOf(fechaBajaStr);
			}

			com.taemoi.project.entities.Deporte deporteEnum = com.taemoi.project.entities.Deporte.valueOf(deporte);
			com.taemoi.project.entities.AlumnoDeporte alumnoDeporte = alumnoDeporteService
					.actualizarFechaBaja(id, deporteEnum, fechaBaja);
			com.taemoi.project.dtos.AlumnoDeporteDTO dto = com.taemoi.project.dtos.AlumnoDeporteDTO
					.deAlumnoDeporte(alumnoDeporte);

			return ResponseEntity.ok(dto);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			logger.error("Error al actualizar fecha de baja", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al actualizar fecha de baja");
		}
	}

	/**
	 * Actualiza la fecha de alta inicial per-sport
	 * PUT /api/alumnos/{id}/deportes/{deporte}/fecha-alta-inicial
	 * Body: { "fechaAltaInicial": "2020-01-15" }
	 */
	@PutMapping("/{id}/deportes/{deporte}/fecha-alta-inicial")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> actualizarFechaAltaInicialDeporte(@PathVariable Long id, @PathVariable String deporte,
			@RequestBody Map<String, String> params) {
		try {
			String fechaStr = params.get("fechaAltaInicial");
			if (fechaStr == null) {
				return ResponseEntity.badRequest().body("La fecha de alta inicial es requerida");
			}

			com.taemoi.project.entities.Deporte deporteEnum = com.taemoi.project.entities.Deporte.valueOf(deporte);
			java.util.Date fechaAltaInicial = java.sql.Date.valueOf(fechaStr);

			com.taemoi.project.entities.AlumnoDeporte alumnoDeporte = alumnoDeporteService
					.actualizarFechaAltaInicial(id, deporteEnum, fechaAltaInicial);
			com.taemoi.project.dtos.AlumnoDeporteDTO dto = com.taemoi.project.dtos.AlumnoDeporteDTO
					.deAlumnoDeporte(alumnoDeporte);

			return ResponseEntity.ok(dto);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			logger.error("Error al actualizar fecha de alta inicial", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al actualizar fecha de alta inicial");
		}
	}

	/**
	 * Actualiza el tipo de tarifa per-sport
	 * PUT /api/alumnos/{id}/deportes/{deporte}/tipo-tarifa
	 * Body: { "tipoTarifa": "INFANTIL" }
	 */
	@PutMapping("/{id}/deportes/{deporte}/tipo-tarifa")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> actualizarTipoTarifa(@PathVariable Long id, @PathVariable String deporte,
			@RequestBody Map<String, String> params) {
		try {
			String tipoTarifaStr = params.get("tipoTarifa");
			if (tipoTarifaStr == null) {
				return ResponseEntity.badRequest().body("El tipo de tarifa es requerido");
			}

			com.taemoi.project.entities.Deporte deporteEnum = com.taemoi.project.entities.Deporte.valueOf(deporte);
			com.taemoi.project.entities.TipoTarifa tipoTarifa = com.taemoi.project.entities.TipoTarifa.valueOf(tipoTarifaStr);

			com.taemoi.project.entities.AlumnoDeporte alumnoDeporte = alumnoDeporteService
					.actualizarTipoTarifa(id, deporteEnum, tipoTarifa);
			com.taemoi.project.dtos.AlumnoDeporteDTO dto = com.taemoi.project.dtos.AlumnoDeporteDTO
					.deAlumnoDeporte(alumnoDeporte);

			return ResponseEntity.ok(dto);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			logger.error("Error al actualizar tipo de tarifa", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al actualizar tipo de tarifa");
		}
	}

	/**
	 * Actualiza la cuantía de tarifa per-sport
	 * PUT /api/alumnos/{id}/deportes/{deporte}/cuantia-tarifa
	 * Body: { "cuantiaTarifa": 45.00 }
	 */
	@PutMapping("/{id}/deportes/{deporte}/cuantia-tarifa")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> actualizarCuantiaTarifa(@PathVariable Long id, @PathVariable String deporte,
			@RequestBody Map<String, Object> params) {
		try {
			Object cuantiaObj = params.get("cuantiaTarifa");
			if (cuantiaObj == null) {
				return ResponseEntity.badRequest().body("La cuantía de tarifa es requerida");
			}

			Double cuantiaTarifa = Double.valueOf(cuantiaObj.toString());
			com.taemoi.project.entities.Deporte deporteEnum = com.taemoi.project.entities.Deporte.valueOf(deporte);

			com.taemoi.project.entities.AlumnoDeporte alumnoDeporte = alumnoDeporteService
					.actualizarCuantiaTarifa(id, deporteEnum, cuantiaTarifa);
			com.taemoi.project.dtos.AlumnoDeporteDTO dto = com.taemoi.project.dtos.AlumnoDeporteDTO
					.deAlumnoDeporte(alumnoDeporte);

			return ResponseEntity.ok(dto);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			logger.error("Error al actualizar cuantía de tarifa", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al actualizar cuantía de tarifa");
		}
	}

	/**
	 * Actualiza el rol familiar per-sport
	 * PUT /api/alumnos/{id}/deportes/{deporte}/rol-familiar
	 * Body: { "rolFamiliar": "PADRE" }
	 */
	@PutMapping("/{id}/deportes/{deporte}/rol-familiar")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> actualizarRolFamiliar(@PathVariable Long id, @PathVariable String deporte,
			@RequestBody Map<String, String> params) {
		try {
			String rolFamiliar = params.get("rolFamiliar");
			if (rolFamiliar == null) {
				return ResponseEntity.badRequest().body("El rol familiar es requerido");
			}

			com.taemoi.project.entities.Deporte deporteEnum = com.taemoi.project.entities.Deporte.valueOf(deporte);

			com.taemoi.project.entities.AlumnoDeporte alumnoDeporte = alumnoDeporteService
					.actualizarRolFamiliar(id, deporteEnum, rolFamiliar);
			com.taemoi.project.dtos.AlumnoDeporteDTO dto = com.taemoi.project.dtos.AlumnoDeporteDTO
					.deAlumnoDeporte(alumnoDeporte);

			return ResponseEntity.ok(dto);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			logger.error("Error al actualizar rol familiar", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al actualizar rol familiar");
		}
	}

	/**
	 * Actualiza el grupo familiar per-sport
	 * PUT /api/alumnos/{id}/deportes/{deporte}/grupo-familiar
	 * Body: { "grupoFamiliar": "Familia Garcia" }
	 */
	@PutMapping("/{id}/deportes/{deporte}/grupo-familiar")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> actualizarGrupoFamiliar(@PathVariable Long id, @PathVariable String deporte,
			@RequestBody Map<String, String> params) {
		try {
			String grupoFamiliar = params.get("grupoFamiliar");
			if (grupoFamiliar == null) {
				return ResponseEntity.badRequest().body("El grupo familiar es requerido");
			}

			com.taemoi.project.entities.Deporte deporteEnum = com.taemoi.project.entities.Deporte.valueOf(deporte);

			com.taemoi.project.entities.AlumnoDeporte alumnoDeporte = alumnoDeporteService
					.actualizarGrupoFamiliar(id, deporteEnum, grupoFamiliar);
			com.taemoi.project.dtos.AlumnoDeporteDTO dto = com.taemoi.project.dtos.AlumnoDeporteDTO
					.deAlumnoDeporte(alumnoDeporte);

			return ResponseEntity.ok(dto);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			logger.error("Error al actualizar grupo familiar", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al actualizar grupo familiar");
		}
	}

	/**
	 * Actualiza si tiene licencia per-sport
	 * PUT /api/alumnos/{id}/deportes/{deporte}/tiene-licencia
	 * Body: { "tieneLicencia": true }
	 */
	@PutMapping("/{id}/deportes/{deporte}/tiene-licencia")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> actualizarTieneLicencia(@PathVariable Long id, @PathVariable String deporte,
			@RequestBody Map<String, Boolean> params) {
		try {
			Boolean tieneLicencia = params.get("tieneLicencia");
			if (tieneLicencia == null) {
				return ResponseEntity.badRequest().body("El campo tieneLicencia es requerido");
			}

			com.taemoi.project.entities.Deporte deporteEnum = com.taemoi.project.entities.Deporte.valueOf(deporte);

			com.taemoi.project.entities.AlumnoDeporte alumnoDeporte = alumnoDeporteService
					.actualizarTieneLicencia(id, deporteEnum, tieneLicencia);
			com.taemoi.project.dtos.AlumnoDeporteDTO dto = com.taemoi.project.dtos.AlumnoDeporteDTO
					.deAlumnoDeporte(alumnoDeporte);

			return ResponseEntity.ok(dto);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			logger.error("Error al actualizar tiene licencia", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al actualizar tiene licencia");
		}
	}

	/**
	 * Actualiza el número de licencia per-sport
	 * PUT /api/alumnos/{id}/deportes/{deporte}/numero-licencia
	 * Body: { "numeroLicencia": 12345 }
	 */
	@PutMapping("/{id}/deportes/{deporte}/numero-licencia")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> actualizarNumeroLicencia(@PathVariable Long id, @PathVariable String deporte,
			@RequestBody Map<String, Object> params) {
		try {
			Object numeroObj = params.get("numeroLicencia");
			if (numeroObj == null) {
				return ResponseEntity.badRequest().body("El número de licencia es requerido");
			}

			Integer numeroLicencia = Integer.valueOf(numeroObj.toString());
			com.taemoi.project.entities.Deporte deporteEnum = com.taemoi.project.entities.Deporte.valueOf(deporte);

			com.taemoi.project.entities.AlumnoDeporte alumnoDeporte = alumnoDeporteService
					.actualizarNumeroLicencia(id, deporteEnum, numeroLicencia);
			com.taemoi.project.dtos.AlumnoDeporteDTO dto = com.taemoi.project.dtos.AlumnoDeporteDTO
					.deAlumnoDeporte(alumnoDeporte);

			return ResponseEntity.ok(dto);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			logger.error("Error al actualizar número de licencia", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al actualizar número de licencia");
		}
	}

	/**
	 * Actualiza la fecha de licencia per-sport
	 * PUT /api/alumnos/{id}/deportes/{deporte}/fecha-licencia
	 * Body: { "fechaLicencia": "2025-01-15" }
	 */
	@PutMapping("/{id}/deportes/{deporte}/fecha-licencia")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> actualizarFechaLicencia(@PathVariable Long id, @PathVariable String deporte,
			@RequestBody Map<String, String> params) {
		try {
			String fechaStr = params.get("fechaLicencia");
			if (fechaStr == null) {
				return ResponseEntity.badRequest().body("La fecha de licencia es requerida");
			}

			com.taemoi.project.entities.Deporte deporteEnum = com.taemoi.project.entities.Deporte.valueOf(deporte);
			java.util.Date fechaLicencia = java.sql.Date.valueOf(fechaStr);

			com.taemoi.project.entities.AlumnoDeporte alumnoDeporte = alumnoDeporteService
					.actualizarFechaLicencia(id, deporteEnum, fechaLicencia);
			com.taemoi.project.dtos.AlumnoDeporteDTO dto = com.taemoi.project.dtos.AlumnoDeporteDTO
					.deAlumnoDeporte(alumnoDeporte);

			return ResponseEntity.ok(dto);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			logger.error("Error al actualizar fecha de licencia", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al actualizar fecha de licencia");
		}
	}

	/**
	 * Actualiza si es competidor per-sport
	 * PUT /api/alumnos/{id}/deportes/{deporte}/competidor
	 * Body: { "competidor": true }
	 */
	@PutMapping("/{id}/deportes/{deporte}/competidor")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> actualizarCompetidor(@PathVariable Long id, @PathVariable String deporte,
			@RequestBody Map<String, Boolean> params) {
		try {
			Boolean competidor = params.get("competidor");
			if (competidor == null) {
				return ResponseEntity.badRequest().body("El campo competidor es requerido");
			}

			com.taemoi.project.entities.Deporte deporteEnum = com.taemoi.project.entities.Deporte.valueOf(deporte);

			com.taemoi.project.entities.AlumnoDeporte alumnoDeporte = alumnoDeporteService
					.actualizarCompetidor(id, deporteEnum, competidor);
			com.taemoi.project.dtos.AlumnoDeporteDTO dto = com.taemoi.project.dtos.AlumnoDeporteDTO
					.deAlumnoDeporte(alumnoDeporte);

			return ResponseEntity.ok(dto);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			logger.error("Error al actualizar competidor", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al actualizar competidor");
		}
	}

	/**
	 * Actualiza el peso per-sport
	 * PUT /api/alumnos/{id}/deportes/{deporte}/peso
	 * Body: { "peso": 65.5 }
	 */
	@PutMapping("/{id}/deportes/{deporte}/peso")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> actualizarPeso(@PathVariable Long id, @PathVariable String deporte,
			@RequestBody Map<String, Object> params) {
		try {
			Object pesoObj = params.get("peso");
			if (pesoObj == null) {
				return ResponseEntity.badRequest().body("El peso es requerido");
			}

			Double peso = Double.valueOf(pesoObj.toString());
			com.taemoi.project.entities.Deporte deporteEnum = com.taemoi.project.entities.Deporte.valueOf(deporte);

			com.taemoi.project.entities.AlumnoDeporte alumnoDeporte = alumnoDeporteService
					.actualizarPeso(id, deporteEnum, peso);
			com.taemoi.project.dtos.AlumnoDeporteDTO dto = com.taemoi.project.dtos.AlumnoDeporteDTO
					.deAlumnoDeporte(alumnoDeporte);

			return ResponseEntity.ok(dto);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			logger.error("Error al actualizar peso", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al actualizar peso");
		}
	}

	/**
	 * Actualiza la fecha de peso per-sport
	 * PUT /api/alumnos/{id}/deportes/{deporte}/fecha-peso
	 * Body: { "fechaPeso": "2025-01-15" }
	 */
	@PutMapping("/{id}/deportes/{deporte}/fecha-peso")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> actualizarFechaPeso(@PathVariable Long id, @PathVariable String deporte,
			@RequestBody Map<String, String> params) {
		try {
			String fechaStr = params.get("fechaPeso");
			if (fechaStr == null) {
				return ResponseEntity.badRequest().body("La fecha de peso es requerida");
			}

			com.taemoi.project.entities.Deporte deporteEnum = com.taemoi.project.entities.Deporte.valueOf(deporte);
			java.util.Date fechaPeso = java.sql.Date.valueOf(fechaStr);

			com.taemoi.project.entities.AlumnoDeporte alumnoDeporte = alumnoDeporteService
					.actualizarFechaPeso(id, deporteEnum, fechaPeso);
			com.taemoi.project.dtos.AlumnoDeporteDTO dto = com.taemoi.project.dtos.AlumnoDeporteDTO
					.deAlumnoDeporte(alumnoDeporte);

			return ResponseEntity.ok(dto);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			logger.error("Error al actualizar fecha de peso", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al actualizar fecha de peso");
		}
	}

	/**
	 * Actualiza la categoría del alumno para un deporte específico
	 * PUT /api/alumnos/{id}/deportes/{deporte}/categoria
	 * Body: { "categoria": "INFANTIL" }
	 */
	@PutMapping("/{id}/deportes/{deporte}/categoria")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> actualizarCategoria(@PathVariable Long id, @PathVariable String deporte,
			@RequestBody Map<String, String> params) {
		try {
			String categoriaNombre = params.get("categoria");
			if (categoriaNombre == null || categoriaNombre.isEmpty()) {
				return ResponseEntity.badRequest().body("La categoría es requerida");
			}

			com.taemoi.project.entities.Deporte deporteEnum = com.taemoi.project.entities.Deporte.valueOf(deporte);

			com.taemoi.project.entities.AlumnoDeporte alumnoDeporte = alumnoDeporteService
					.actualizarCategoria(id, deporteEnum, categoriaNombre);
			com.taemoi.project.dtos.AlumnoDeporteDTO dto = com.taemoi.project.dtos.AlumnoDeporteDTO
					.deAlumnoDeporte(alumnoDeporte);

			return ResponseEntity.ok(dto);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			logger.error("Error al actualizar categoría", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al actualizar categoría");
		}
	}

	/**
	 * Actualiza la fecha de alta como competidor per-sport
	 * PUT /api/alumnos/{id}/deportes/{deporte}/fecha-alta-competicion
	 * Body: { "fechaAltaCompeticion": "2025-01-15" }
	 */
	@PutMapping("/{id}/deportes/{deporte}/fecha-alta-competicion")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> actualizarFechaAltaCompeticion(@PathVariable Long id, @PathVariable String deporte,
			@RequestBody Map<String, String> params) {
		try {
			String fechaStr = params.get("fechaAltaCompeticion");
			if (fechaStr == null) {
				return ResponseEntity.badRequest().body("La fecha de alta competición es requerida");
			}

			com.taemoi.project.entities.Deporte deporteEnum = com.taemoi.project.entities.Deporte.valueOf(deporte);
			java.util.Date fechaAltaCompeticion = java.sql.Date.valueOf(fechaStr);

			com.taemoi.project.entities.AlumnoDeporte alumnoDeporte = alumnoDeporteService
					.actualizarFechaAltaCompeticion(id, deporteEnum, fechaAltaCompeticion);
			com.taemoi.project.dtos.AlumnoDeporteDTO dto = com.taemoi.project.dtos.AlumnoDeporteDTO
					.deAlumnoDeporte(alumnoDeporte);

			return ResponseEntity.ok(dto);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			logger.error("Error al actualizar fecha de alta competición", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al actualizar fecha de alta competición");
		}
	}

	/**
	 * Actualiza la fecha de alta inicial como competidor per-sport
	 * PUT /api/alumnos/{id}/deportes/{deporte}/fecha-alta-competidor-inicial
	 * Body: { "fechaAltaCompetidorInicial": "2025-01-15" }
	 */
	@PutMapping("/{id}/deportes/{deporte}/fecha-alta-competidor-inicial")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> actualizarFechaAltaCompetidorInicial(@PathVariable Long id, @PathVariable String deporte,
			@RequestBody Map<String, String> params) {
		try {
			String fechaStr = params.get("fechaAltaCompetidorInicial");
			if (fechaStr == null) {
				return ResponseEntity.badRequest().body("La fecha de alta inicial competidor es requerida");
			}

			com.taemoi.project.entities.Deporte deporteEnum = com.taemoi.project.entities.Deporte.valueOf(deporte);
			java.util.Date fechaAltaCompetidorInicial = java.sql.Date.valueOf(fechaStr);

			com.taemoi.project.entities.AlumnoDeporte alumnoDeporte = alumnoDeporteService
					.actualizarFechaAltaCompetidorInicial(id, deporteEnum, fechaAltaCompetidorInicial);
			com.taemoi.project.dtos.AlumnoDeporteDTO dto = com.taemoi.project.dtos.AlumnoDeporteDTO
					.deAlumnoDeporte(alumnoDeporte);

			return ResponseEntity.ok(dto);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			logger.error("Error al actualizar fecha de alta inicial competidor", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al actualizar fecha de alta inicial competidor");
		}
	}

	/**
	 * Actualiza todos los datos de competidor en una sola transacción.
	 * Evita condiciones de carrera cuando se actualizan múltiples campos.
	 * PUT /api/alumnos/{id}/deportes/{deporte}/datos-competidor
	 * Body: { "competidor": true, "fechaAltaCompeticion": "2025-01-15", ... }
	 */
	@PutMapping("/{id}/deportes/{deporte}/datos-competidor")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> actualizarDatosCompetidor(@PathVariable Long id, @PathVariable String deporte,
			@RequestBody Map<String, Object> params) {
		try {
			com.taemoi.project.entities.Deporte deporteEnum = com.taemoi.project.entities.Deporte.valueOf(deporte);

			// Parse optional fields
			Boolean competidor = params.containsKey("competidor") ? (Boolean) params.get("competidor") : null;

			java.util.Date fechaAltaCompeticion = null;
			if (params.get("fechaAltaCompeticion") != null && !params.get("fechaAltaCompeticion").toString().isEmpty()) {
				fechaAltaCompeticion = java.sql.Date.valueOf(params.get("fechaAltaCompeticion").toString());
			}

			java.util.Date fechaAltaCompetidorInicial = null;
			if (params.get("fechaAltaCompetidorInicial") != null && !params.get("fechaAltaCompetidorInicial").toString().isEmpty()) {
				fechaAltaCompetidorInicial = java.sql.Date.valueOf(params.get("fechaAltaCompetidorInicial").toString());
			}

			String categoriaNombre = params.get("categoria") != null ? params.get("categoria").toString() : null;

			Double peso = null;
			if (params.get("peso") != null) {
				peso = Double.valueOf(params.get("peso").toString());
			}

			java.util.Date fechaPeso = null;
			if (params.get("fechaPeso") != null && !params.get("fechaPeso").toString().isEmpty()) {
				fechaPeso = java.sql.Date.valueOf(params.get("fechaPeso").toString());
			}

			com.taemoi.project.entities.AlumnoDeporte alumnoDeporte = alumnoDeporteService
					.actualizarDatosCompetidor(id, deporteEnum, competidor, fechaAltaCompeticion,
							fechaAltaCompetidorInicial, categoriaNombre, peso, fechaPeso);
			com.taemoi.project.dtos.AlumnoDeporteDTO dto = com.taemoi.project.dtos.AlumnoDeporteDTO
					.deAlumnoDeporte(alumnoDeporte);

			return ResponseEntity.ok(dto);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			logger.error("Error al actualizar datos de competidor", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al actualizar datos de competidor");
		}
	}

	/**
	 * Actualiza la fecha de alta inicial del alumno (GENERAL - all sports)
	 * Esta fecha afecta el cálculo de antigüedad para todos los deportes
	 * PUT /api/alumnos/{id}/fecha-alta-inicial
	 * Body: { "fechaAltaInicial": "2020-01-15" }
	 */
	@PutMapping("/{id}/fecha-alta-inicial")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> actualizarFechaAltaInicial(@PathVariable @NonNull Long id,
			@RequestBody Map<String, String> body) {
		try {
			String fechaStr = body.get("fechaAltaInicial");
			if (fechaStr == null || fechaStr.isEmpty()) {
				return ResponseEntity.badRequest().body("La fecha de alta inicial es requerida");
			}

			java.util.Date nuevaFecha = java.sql.Date.valueOf(fechaStr);

			com.taemoi.project.entities.Alumno alumnoActualizado = alumnoService.actualizarFechaAltaInicial(id, nuevaFecha);
			com.taemoi.project.dtos.AlumnoDTO dto =
					aplicarUrlImagenPublica(com.taemoi.project.dtos.AlumnoDTO.deAlumno(alumnoActualizado));

			return ResponseEntity.ok(dto);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body("Formato de fecha inválido. Use YYYY-MM-DD");
		} catch (com.taemoi.project.exceptions.alumno.AlumnoNoEncontradoException e) {
			return ResponseEntity.notFound().build();
		} catch (Exception e) {
			logger.error("Error al actualizar fecha de alta inicial", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al actualizar fecha de alta inicial");
		}
	}

	/**
	 * Obtiene un alumno con todos sus deportes (respuesta completa)
	 * GET /api/alumnos/{id}/completo
	 */
	@GetMapping("/{id}/completo")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN') || hasRole('ROLE_USER')")
	public ResponseEntity<com.taemoi.project.dtos.response.AlumnoConDeportesDTO> obtenerAlumnoCompleto(@PathVariable Long id) {
		try {
			Alumno alumno = alumnoService.obtenerAlumnoPorId(id)
					.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + id));
			com.taemoi.project.dtos.response.AlumnoConDeportesDTO dto =
					aplicarUrlImagenPublica(com.taemoi.project.dtos.response.AlumnoConDeportesDTO.deAlumno(alumno));
			return ResponseEntity.ok(dto);
		} catch (AlumnoNoEncontradoException e) {
			return ResponseEntity.notFound().build();
		} catch (Exception e) {
			logger.error("Error al obtener alumno completo", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	/**
	 * Obtiene todos los alumnos con sus deportes (batch endpoint)
	 * GET /api/alumnos/con-deportes
	 */
	@GetMapping("/con-deportes")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<List<com.taemoi.project.dtos.response.AlumnoConDeportesDTO>> obtenerTodosLosAlumnosConDeportes(
			@RequestParam(required = false, defaultValue = "false") Boolean incluirInactivos) {
		try {
			logger.info("## AlumnoController :: obtenerTodosLosAlumnosConDeportes :: incluirInactivos={}", incluirInactivos);
			List<Alumno> alumnos = alumnoService.obtenerAlumnosFiltrados(null, null, null, incluirInactivos);
			List<com.taemoi.project.dtos.response.AlumnoConDeportesDTO> alumnosDTO = alumnos.stream()
					.map(com.taemoi.project.dtos.response.AlumnoConDeportesDTO::deAlumno)
					.map(this::aplicarUrlImagenPublica)
					.collect(Collectors.toList());
			return ResponseEntity.ok(alumnosDTO);
		} catch (Exception e) {
			logger.error("Error al obtener alumnos con deportes", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	/**
	 * MIGRATION ENDPOINT: Migrates categoria_id from alumno table to alumno_deporte table
	 * This is a ONE-TIME migration to copy categoria data from the old location to the new per-sport location.
	 *
	 * POST /api/alumnos/migrate-categorias
	 *
	 * This endpoint:
	 * 1. Finds all alumno_deporte records where categoria_id is NULL
	 * 2. Copies the categoria_id from the alumno table to the alumno_deporte table
	 * 3. Only updates records where categoria_id exists in alumno table
	 *
	 * @return ResponseEntity with migration results
	 */
	@PostMapping("/migrate-categorias")
	@PreAuthorize("hasRole('ROLE_ADMIN')")
	@Transactional
	public ResponseEntity<?> migrateCategorias() {
		try {
			logger.info("## AlumnoController :: migrateCategorias :: Starting categoria migration");

			// IMPROVED: Assign categoria based on student's age instead of copying old IDs
			// This handles the case where categoria IDs changed after schema updates
			String sql = "UPDATE alumno_deporte ad " +
						 "INNER JOIN alumno a ON ad.alumno_id = a.id " +
						 "SET ad.categoria_id = CASE " +
						 "  WHEN TIMESTAMPDIFF(YEAR, a.fecha_nacimiento, CURDATE()) <= 9 THEN " +
						 "    (SELECT id FROM categoria WHERE nombre = 'Infantil') " +
						 "  WHEN TIMESTAMPDIFF(YEAR, a.fecha_nacimiento, CURDATE()) <= 11 THEN " +
						 "    (SELECT id FROM categoria WHERE nombre = 'Precadete') " +
						 "  WHEN TIMESTAMPDIFF(YEAR, a.fecha_nacimiento, CURDATE()) <= 13 THEN " +
						 "    (SELECT id FROM categoria WHERE nombre = 'Cadete') " +
						 "  WHEN TIMESTAMPDIFF(YEAR, a.fecha_nacimiento, CURDATE()) <= 16 THEN " +
						 "    (SELECT id FROM categoria WHERE nombre = 'Junior') " +
						 "  ELSE " +
						 "    (SELECT id FROM categoria WHERE nombre = 'Senior') " +
						 "END " +
						 "WHERE ad.competidor = 1 AND ad.deporte = 'TAEKWONDO'";

			int rowsUpdated = entityManager.createNativeQuery(sql).executeUpdate();

			logger.info("## AlumnoController :: migrateCategorias :: Migration completed. Rows updated: {}", rowsUpdated);

			return ResponseEntity.ok(Map.of(
				"success", true,
				"message", "Categoria migration completed successfully (assigned by age)",
				"rowsUpdated", rowsUpdated
			));
		} catch (Exception e) {
			logger.error("Error during categoria migration", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
				"success", false,
				"message", "Error during categoria migration: " + e.getMessage()
			));
		}
	}

	private AlumnoDTO aplicarUrlImagenPublica(AlumnoDTO dto) {
		if (dto == null || dto.getId() == null || dto.getFotoAlumno() == null) {
			return dto;
		}
		dto.getFotoAlumno().setUrl(baseUrl + "/api/alumnos/" + dto.getId() + "/imagen");
		return dto;
	}

	private com.taemoi.project.dtos.response.AlumnoConDeportesDTO aplicarUrlImagenPublica(
			com.taemoi.project.dtos.response.AlumnoConDeportesDTO dto) {
		if (dto == null || dto.getId() == null || dto.getFotoAlumno() == null) {
			return dto;
		}
		dto.getFotoAlumno().setUrl(baseUrl + "/api/alumnos/" + dto.getId() + "/imagen");
		return dto;
	}

	private ResponseEntity<Resource> construirRespuestaImagenOriginal(
			Resource recurso,
			MediaType mediaType,
			String nombreImagen,
			Path rutaArchivo,
			int anchoNormalizado) throws IOException {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(Duration.ofHours(8)).cachePublic().mustRevalidate())
				.lastModified(Files.getLastModifiedTime(rutaArchivo).toMillis())
				.header(HttpHeaders.VARY, "Accept-Encoding")
				.contentType(mediaType)
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nombreImagen + "\"")
				.header(HttpHeaders.ETAG, generarEtagImagen(rutaArchivo, anchoNormalizado, mediaType.toString()))
				.body(recurso);
	}

	private ResponseEntity<Resource> intentarConstruirRespuestaRedimensionada(
			Path rutaArchivo,
			MediaType mediaTypeOriginal,
			String nombreImagen,
			int anchoObjetivo) {
		if (!esImagenRasterRedimensionable(mediaTypeOriginal)) {
			return null;
		}

		if (!RESIZE_SEMAPHORE.tryAcquire()) {
			return null;
		}

		try {
			long tamanoArchivo = Files.size(rutaArchivo);
			if (tamanoArchivo > MAX_TAMANO_ORIGEN_RESIZE_BYTES) {
				return null;
			}

			int[] dimensionesOriginales = obtenerDimensionesImagen(rutaArchivo);
			if (dimensionesOriginales == null) {
				return null;
			}

			int anchoOriginal = dimensionesOriginales[0];
			int altoOriginal = dimensionesOriginales[1];
			if (anchoOriginal <= 0 || altoOriginal <= 0) {
				return null;
			}
			if (anchoOriginal <= anchoObjetivo) {
				return null;
			}
			if ((long) anchoOriginal * altoOriginal > MAX_PIXELES_ORIGEN_RESIZE) {
				return null;
			}

			BufferedImage original = ImageIO.read(rutaArchivo.toFile());
			if (original == null || original.getWidth() <= 0 || original.getHeight() <= 0) {
				return null;
			}

			int altoObjetivo = Math.max(1,
					(int) Math.round((double) original.getHeight() * anchoObjetivo / original.getWidth()));
			BufferedImage redimensionada = new BufferedImage(anchoObjetivo, altoObjetivo, BufferedImage.TYPE_3BYTE_BGR);

			Graphics2D graphics = redimensionada.createGraphics();
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.drawImage(original, 0, 0, anchoObjetivo, altoObjetivo, null);
			graphics.dispose();

			byte[] imagenBytes = codificarWebp(redimensionada, CALIDAD_WEBP_REDIMENSION);
			MediaType mediaTypeSalida = MediaType.parseMediaType("image/webp");
			ByteArrayResource recurso = new ByteArrayResource(imagenBytes);
			long lastModified = Files.getLastModifiedTime(rutaArchivo).toMillis();

			return ResponseEntity.ok()
					.cacheControl(CacheControl.maxAge(Duration.ofHours(8)).cachePublic().mustRevalidate())
					.lastModified(lastModified)
					.header(HttpHeaders.VARY, "Accept-Encoding")
					.contentType(mediaTypeSalida)
					.contentLength(imagenBytes.length)
					.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nombreImagen + "\"")
					.header(HttpHeaders.ETAG, generarEtagImagen(rutaArchivo, anchoObjetivo, mediaTypeSalida.toString()))
					.body(recurso);
		} catch (Throwable e) {
			return null;
		} finally {
			RESIZE_SEMAPHORE.release();
		}
	}

	private int[] obtenerDimensionesImagen(Path rutaArchivo) {
		try (ImageInputStream input = ImageIO.createImageInputStream(rutaArchivo.toFile())) {
			if (input == null) {
				return null;
			}
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				return null;
			}

			ImageReader reader = readers.next();
			try {
				reader.setInput(input);
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				return new int[] { width, height };
			} finally {
				reader.dispose();
			}
		} catch (Exception e) {
			return null;
		}
	}

	private byte[] codificarWebp(BufferedImage imagen, float quality) throws IOException {
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
		if (!writers.hasNext()) {
			writers = ImageIO.getImageWritersBySuffix("webp");
		}
		if (!writers.hasNext()) {
			throw new IOException("No hay codificador WebP disponible.");
		}

		ImageWriter writer = writers.next();
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		try (ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(outputStream)) {
			writer.setOutput(imageOutputStream);
			ImageWriteParam writeParam = writer.getDefaultWriteParam();
			if (writeParam.canWriteCompressed()) {
				writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
				String[] compressionTypes = writeParam.getCompressionTypes();
				if (compressionTypes != null && compressionTypes.length > 0) {
					writeParam.setCompressionType(seleccionarTipoCompresion(compressionTypes));
				}
				writeParam.setCompressionQuality(quality);
			}
			writer.write(null, new IIOImage(imagen, null, null), writeParam);
			imageOutputStream.flush();
			return outputStream.toByteArray();
		} finally {
			writer.dispose();
		}
	}

	private String seleccionarTipoCompresion(String[] compressionTypes) {
		for (String type : compressionTypes) {
			if (type != null && type.equalsIgnoreCase("Lossy")) {
				return type;
			}
		}
		return compressionTypes[0];
	}

	private boolean esImagenRasterRedimensionable(MediaType mediaType) {
		if (mediaType == null || !mediaType.getType().equalsIgnoreCase("image")) {
			return false;
		}
		String subtype = mediaType.getSubtype() != null ? mediaType.getSubtype().toLowerCase() : "";
		// Evitamos redimensionado runtime para WebP por inestabilidad del decoder nativo en prod (SIGSEGV).
		return !subtype.contains("svg") && !subtype.contains("webp");
	}

	private int normalizarAnchoImagen(Integer anchoSolicitado) {
		if (anchoSolicitado == null || anchoSolicitado <= 0) {
			return 0;
		}
		if (anchoSolicitado < MIN_ANCHO_IMAGEN) {
			return MIN_ANCHO_IMAGEN;
		}
		return Math.min(anchoSolicitado, MAX_ANCHO_IMAGEN);
	}

	private String generarEtagImagen(Path rutaArchivo, int ancho, String formato) throws IOException {
		long tamano = Files.size(rutaArchivo);
		long modificado = Files.getLastModifiedTime(rutaArchivo).toMillis();
		String hash = Long.toHexString(tamano) + "-" + Long.toHexString(modificado) + "-" + ancho + "-" + formato;
		return "\"" + hash + "\"";
	}

	private ResponseEntity<Resource> construirRedireccionImagenAlumno(Imagen imagen) {
		if (imagen == null) {
			return null;
		}

		String nombre = imagen.getNombre();
		String urlImagen = imagen.getUrl();
		List<String> candidatos = new ArrayList<>();

		if (urlImagen != null && !urlImagen.isBlank()) {
			candidatos.add(urlImagen.trim());
		}
		if (nombre != null && !nombre.isBlank()) {
			candidatos.add("/imagenes/alumnos/" + nombre);
			if (baseUrl != null && !baseUrl.isBlank()) {
				candidatos.add(baseUrl + "/imagenes/alumnos/" + nombre);
			}
		}

		for (String candidato : candidatos) {
			if (candidato == null || candidato.isBlank()) {
				continue;
			}
			if (candidato.contains("/api/alumnos/")) {
				continue;
			}
			try {
				return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(candidato)).build();
			} catch (Exception ignored) {
				// Ignore invalid candidates and continue.
			}
		}

		return null;
	}

	private Path resolverRutaImagenAlumno(Imagen imagen) {
		if (imagen == null) {
			return null;
		}

		List<Path> candidatos = new ArrayList<>();
		agregarCandidatoRuta(candidatos, imagen.getRuta());

		String nombre = obtenerNombreImagenPrioritario(imagen);
		if (nombre != null && !nombre.isBlank()) {
			agregarCandidatosDesdeBase(candidatos, directorioImagenesLinux, nombre);
			agregarCandidatosDesdeBase(candidatos, directorioImagenesWindows, nombre);
			agregarCandidatoRuta(candidatos, "/var/www/app/imagenes/alumnos/" + nombre);
			agregarCandidatoRuta(candidatos, "/var/www/app/imagenes/" + nombre);
			agregarCandidatoRuta(candidatos, "static_resources/imagenes/alumnos/" + nombre);
		}

		for (Path candidato : candidatos) {
			try {
				if (candidato != null && Files.exists(candidato) && Files.isRegularFile(candidato)) {
					return candidato.toAbsolutePath().normalize();
				}
			} catch (Exception ignored) {
				// Intentionally ignore invalid/unsupported candidate paths.
			}
		}

		return null;
	}

	private String obtenerNombreImagenPrioritario(Imagen imagen) {
		if (imagen == null) {
			return null;
		}
		if (imagen.getNombre() != null && !imagen.getNombre().isBlank()) {
			return imagen.getNombre().trim();
		}
		String nombreDesdeRuta = extraerNombreDesdeRuta(imagen.getRuta());
		if (nombreDesdeRuta != null && !nombreDesdeRuta.isBlank()) {
			return nombreDesdeRuta;
		}
		return extraerNombreDesdeRuta(imagen.getUrl());
	}

	private String extraerNombreDesdeRuta(String ruta) {
		if (ruta == null || ruta.isBlank()) {
			return null;
		}
		String normalizada = ruta.replace('\\', '/').trim();
		int idx = normalizada.lastIndexOf('/');
		if (idx < 0 || idx >= normalizada.length() - 1) {
			return null;
		}
		String nombre = normalizada.substring(idx + 1);
		int queryIndex = nombre.indexOf('?');
		if (queryIndex >= 0) {
			nombre = nombre.substring(0, queryIndex);
		}
		return nombre.isBlank() ? null : nombre;
	}

	private void agregarCandidatoRuta(List<Path> candidatos, String ruta) {
		if (ruta == null || ruta.isBlank()) {
			return;
		}
		try {
			candidatos.add(Path.of(expandirVariablesRuta(ruta)));
		} catch (Exception ignored) {
			// Ignore malformed paths coming from legacy DB rows.
		}
	}

	private void agregarCandidatosDesdeBase(List<Path> candidatos, String baseDir, String nombreArchivo) {
		if (baseDir == null || baseDir.isBlank() || nombreArchivo == null || nombreArchivo.isBlank()) {
			return;
		}
		try {
			Path base = Path.of(expandirVariablesRuta(baseDir));
			candidatos.add(base.resolve(nombreArchivo));
			candidatos.add(base.resolve("alumnos").resolve(nombreArchivo));
		} catch (Exception ignored) {
			// Ignore malformed configured directories.
		}
	}

	private ResponseEntity<Resource> construirRespuestaArchivoInline(
			MaterialExamenService.MaterialExamenArchivo archivo) throws IOException {
		return construirRespuestaArchivoConDisposition(archivo, false);
	}

	private ResponseEntity<Resource> construirRespuestaArchivoAttachment(
			MaterialExamenService.MaterialExamenArchivo archivo) throws IOException {
		return construirRespuestaArchivoConDisposition(archivo, true);
	}

	private ResponseEntity<Resource> construirRespuestaArchivoConDisposition(
			MaterialExamenService.MaterialExamenArchivo archivo,
			boolean download) throws IOException {
		MediaType mediaType = parsearMediaTypeSeguro(archivo.getMimeType(), MediaType.APPLICATION_OCTET_STREAM);
		InputStreamResource resource = new InputStreamResource(Files.newInputStream(archivo.getPath()));
		String disposition = construirContentDispositionSeguro(download, archivo.getFileName());

		return ResponseEntity.ok()
				.contentType(mediaType)
				.contentLength(archivo.getSize())
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition)
				.body(resource);
	}

	private ResponseEntity<Resource> construirRespuestaVideoConRange(
			MaterialExamenService.MaterialExamenArchivo archivo,
			HttpHeaders requestHeaders) throws IOException {
		return construirRespuestaArchivoConRange(archivo, requestHeaders, false);
	}

	private ResponseEntity<Resource> construirRespuestaArchivoConRange(
			MaterialExamenService.MaterialExamenArchivo archivo,
			HttpHeaders requestHeaders,
			boolean download) throws IOException {
		long fileSize = archivo.getSize();
		MediaType mediaType = parsearMediaTypeSeguro(archivo.getMimeType(), MediaType.APPLICATION_OCTET_STREAM);
		List<HttpRange> ranges = requestHeaders.getRange();
		String disposition = construirContentDispositionSeguro(download, archivo.getFileName());

		if (ranges == null || ranges.isEmpty()) {
			InputStreamResource fullResource = new InputStreamResource(Files.newInputStream(archivo.getPath()));
			return ResponseEntity.ok()
					.contentType(mediaType)
					.contentLength(fileSize)
					.header(HttpHeaders.ACCEPT_RANGES, "bytes")
					.header(HttpHeaders.CONTENT_DISPOSITION, disposition)
					.body(fullResource);
		}

		HttpRange range = ranges.get(0);
		long start = range.getRangeStart(fileSize);
		long end = range.getRangeEnd(fileSize);

		if (start >= fileSize || end >= fileSize || start > end) {
			return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
					.header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize)
					.build();
		}

		long contentLength = end - start + 1;
		InputStream source = Files.newInputStream(archivo.getPath());
		skipFully(source, start);
		InputStreamResource partialResource = new InputStreamResource(new LimitedInputStream(source, contentLength));

		return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
				.contentType(mediaType)
				.contentLength(contentLength)
				.header(HttpHeaders.ACCEPT_RANGES, "bytes")
				.header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileSize)
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition)
				.body(partialResource);
	}

	private void skipFully(InputStream source, long bytesToSkip) throws IOException {
		long totalSkipped = 0;
		while (totalSkipped < bytesToSkip) {
			long skipped = source.skip(bytesToSkip - totalSkipped);
			if (skipped <= 0) {
				int read = source.read();
				if (read == -1) {
					throw new IOException("No se pudo posicionar el stream en el rango solicitado");
				}
				skipped = 1;
			}
			totalSkipped += skipped;
		}
	}

	private String construirContentDispositionSeguro(boolean download, String fileName) {
		String safeFileName = (fileName == null || fileName.isBlank()) ? "archivo" : fileName;
		return ContentDisposition.builder(download ? "attachment" : "inline")
				.filename(safeFileName, StandardCharsets.UTF_8)
				.build()
				.toString();
	}

	private MediaType parsearMediaTypeSeguro(String mediaTypeRaw, MediaType fallback) {
		if (mediaTypeRaw == null || mediaTypeRaw.isBlank()) {
			return fallback;
		}
		try {
			return MediaType.parseMediaType(mediaTypeRaw);
		} catch (Exception e) {
			return fallback;
		}
	}

	private boolean esTemarioPermitido(String mimeType) {
		return mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("application/pdf");
	}

	private boolean esVideoPermitido(String mimeType) {
		if (mimeType == null) {
			return false;
		}
		String normalized = mimeType.toLowerCase(Locale.ROOT);
		return normalized.equals("video/mp4")
				|| normalized.equals("video/webm")
				|| normalized.equals("video/quicktime");
	}

	private static final class LimitedInputStream extends FilterInputStream {
		private long remaining;

		private LimitedInputStream(InputStream in, long maxBytes) {
			super(in);
			this.remaining = Math.max(0, maxBytes);
		}

		@Override
		public int read() throws IOException {
			if (remaining <= 0) {
				return -1;
			}
			int read = super.read();
			if (read >= 0) {
				remaining--;
			}
			return read;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			if (remaining <= 0) {
				return -1;
			}
			int bytesToRead = (int) Math.min(len, remaining);
			int read = super.read(b, off, bytesToRead);
			if (read > 0) {
				remaining -= read;
			}
			return read;
		}
	}

	private String expandirVariablesRuta(String ruta) {
		if (ruta == null || ruta.isBlank()) {
			return ruta;
		}
		String resultado = ruta;
		String userProfile = System.getenv("USERPROFILE");
		if (userProfile != null && !userProfile.isBlank()) {
			resultado = resultado.replace("%USERPROFILE%", userProfile);
		}
		return resultado;
	}

	private boolean usuarioPuedeAccederAlumno(Long alumnoId) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return alumnoAccessControlService.canAccessAlumno(alumnoId, authentication);
	}
}
