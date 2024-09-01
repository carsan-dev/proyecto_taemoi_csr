package com.taemoi.project.controladores;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.dtos.AlumnoDTO;
import com.taemoi.project.dtos.TurnoDTO;
import com.taemoi.project.dtos.response.GrupoResponseDTO;
import com.taemoi.project.entidades.Alumno;
import com.taemoi.project.entidades.Categoria;
import com.taemoi.project.entidades.Grado;
import com.taemoi.project.entidades.Imagen;
import com.taemoi.project.entidades.Roles;
import com.taemoi.project.entidades.Usuario;
import com.taemoi.project.errores.alumno.AlumnoDuplicadoException;
import com.taemoi.project.errores.alumno.AlumnoNoEncontradoException;
import com.taemoi.project.errores.alumno.DatosAlumnoInvalidosException;
import com.taemoi.project.errores.alumno.FechaNacimientoInvalidaException;
import com.taemoi.project.errores.alumno.ListaAlumnosVaciaException;
import com.taemoi.project.repositorios.AlumnoRepository;
import com.taemoi.project.repositorios.GradoRepository;
import com.taemoi.project.repositorios.UsuarioRepository;
import com.taemoi.project.servicios.AlumnoService;
import com.taemoi.project.servicios.GrupoService;

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
	 * Inyección del repositorio de grado.
	 */
	@Autowired
	private GradoRepository gradoRepository;

	/**
	 * Inyección del servicio de grupo.
	 */
	@Autowired
	private GrupoService grupoService;

	/**
	 * Inyección del repositorio de usuario.
	 */
	@Autowired
	private UsuarioRepository usuarioRepository;

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
	public ResponseEntity<?> obtenerAlumnosDTO(
	        @RequestParam(required = false) Integer page,
	        @RequestParam(required = false) Integer size,
	        @RequestParam(required = false) String nombre,
	        @RequestParam(required = false) Long gradoId,
	        @RequestParam(required = false) Long categoriaId,
			@RequestParam(required = false) Boolean incluirInactivos){

	    logger.info("## AlumnoController :: obtenerAlumnosDTO :: Iniciando método");
	    logger.info(
	            "## AlumnoController :: obtenerAlumnosDTO :: Parámetros recibidos - page: {}, size: {}, nombre: {}, gradoId: {}, categoriaId: {}",
	            page, size, nombre, gradoId, categoriaId);

	    Pageable pageable = (page != null && size != null) ? PageRequest.of(page - 1, size, Sort.by("nombre").ascending()) : Pageable.unpaged();
	    boolean isPaged = page != null && size != null;
	    boolean incluir = incluirInactivos != null ? incluirInactivos : false;

	    Page<Alumno> alumnos = alumnoService.obtenerAlumnosFiltrados(nombre, gradoId, categoriaId, incluir, pageable);

	    if (alumnos.isEmpty()) {
	        logger.warn("## AlumnoController :: obtenerAlumnosDTO :: No hay usuarios registrados en el sistema.");
	        return ResponseEntity.ok(isPaged ? Page.empty(pageable) : Collections.emptyList());
	    }

	    logger.info("## AlumnoController :: obtenerAlumnosDTO :: Se encontraron alumnos, retornando respuesta.");
	    return ResponseEntity.ok(isPaged ? alumnos.map(AlumnoDTO::deAlumno) : alumnos.getContent().stream().map(AlumnoDTO::deAlumno).collect(Collectors.toList()));
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
		Optional<AlumnoDTO> alumno = alumnoService.obtenerAlumnoDTOPorId(id);
		return alumno.map(value -> new ResponseEntity<>(value, HttpStatus.OK))
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + id));
	}

	/**
	 * Obtiene los grupos a los que pertenece un alumno especificado por su ID.
	 *
	 * @param alumnoId El ID del alumno cuyos grupos se desean obtener.
	 * @return ResponseEntity que contiene una lista de GrupoResponseDTO si se encuentran grupos; 
	 *         ResponseEntity.notFound() si no se encuentran grupos para el alumno especificado.
	 */
	@GetMapping("/{alumnoId}/grupos")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN') || hasRole('ROLE_USER')")
	public ResponseEntity<?> obtenerGruposDeAlumno(@PathVariable @NonNull Long alumnoId) {
		List<GrupoResponseDTO> gruposDTO = grupoService.obtenerGruposDelAlumno(alumnoId);
		if (!gruposDTO.isEmpty()) {
			return ResponseEntity.ok(gruposDTO);
		} else {
			return ResponseEntity.notFound().build();
		}
	}

	/**
	 * Crea un nuevo alumno y le crea un usuario automáticamente.
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
			@RequestParam(value = "file", required = false) MultipartFile file) {
		try {
			ObjectMapper objectMapper = new ObjectMapper();
			AlumnoDTO nuevoAlumnoDTO = objectMapper.readValue(alumnoJson, AlumnoDTO.class);

			if (file != null) {
				Imagen img = new Imagen(file.getOriginalFilename(), file.getContentType(), file.getBytes());
				Imagen imagenGuardada = alumnoService.guardarImagen(img);
				nuevoAlumnoDTO.setFotoAlumno(imagenGuardada);
			}
			logger.info("## AlumnoController :: añadirAlumno");

			if (!alumnoService.fechaNacimientoValida(nuevoAlumnoDTO.getFechaNacimiento())) {
				throw new FechaNacimientoInvalidaException("La fecha de nacimiento es inválida.");
			}

			if (!alumnoService.datosAlumnoValidos(nuevoAlumnoDTO)) {
				throw new DatosAlumnoInvalidosException("Los datos del alumno a crear son inválidos.");
			}

			int edad = alumnoService.calcularEdad(nuevoAlumnoDTO.getFechaNacimiento());

			Categoria categoria = alumnoService.asignarCategoriaSegunEdad(edad);
			Grado grado = alumnoService.asignarGradoSegunEdad(nuevoAlumnoDTO);

			Grado gradoGuardado = gradoRepository.findByTipoGrado(grado.getTipoGrado());
			if (gradoGuardado == null) {
				gradoGuardado = gradoRepository.save(grado);
			}

			Optional<Alumno> alumnoExistente = alumnoRepository.findByNif(nuevoAlumnoDTO.getNif());
			if (alumnoExistente.isPresent()) {
				throw new AlumnoDuplicadoException("El alumno con NIF " + nuevoAlumnoDTO.getNif() + " ya existe.");
			}

			Usuario usuarioExistente = usuarioRepository.findByEmail(nuevoAlumnoDTO.getEmail()).orElse(null);
			if (usuarioExistente == null) {
				usuarioExistente = new Usuario();
				usuarioExistente.setNombre(nuevoAlumnoDTO.getNombre());
				usuarioExistente.setApellidos(nuevoAlumnoDTO.getApellidos());
				usuarioExistente.setEmail(nuevoAlumnoDTO.getEmail());
				String contrasena = alumnoService.generarContrasena(nuevoAlumnoDTO.getNombre(),
						nuevoAlumnoDTO.getApellidos());
				usuarioExistente.setContrasena(contrasena);
				Set<Roles> roles = new HashSet<>();
				roles.add(Roles.ROLE_USER);
				usuarioExistente.setRoles(roles);
				usuarioExistente = usuarioRepository.save(usuarioExistente);
			}

			Alumno nuevoAlumno = new Alumno();
			nuevoAlumno.setNombre(nuevoAlumnoDTO.getNombre());
			nuevoAlumno.setApellidos(nuevoAlumnoDTO.getApellidos());
			nuevoAlumno.setFechaNacimiento(nuevoAlumnoDTO.getFechaNacimiento());
			nuevoAlumno.setNif(nuevoAlumnoDTO.getNif());
			nuevoAlumno.setDireccion(nuevoAlumnoDTO.getDireccion());
			nuevoAlumno.setEmail(nuevoAlumnoDTO.getEmail());
			nuevoAlumno.setTelefono(nuevoAlumnoDTO.getTelefono());
			nuevoAlumno.setTipoTarifa(nuevoAlumnoDTO.getTipoTarifa());
	        if (nuevoAlumnoDTO.getCuantiaTarifa() == null || nuevoAlumnoDTO.getCuantiaTarifa() <= 0) {
	            nuevoAlumno.setCuantiaTarifa(alumnoService.asignarCuantiaTarifa(nuevoAlumno.getTipoTarifa()));
	        } else {
	            nuevoAlumno.setCuantiaTarifa(nuevoAlumnoDTO.getCuantiaTarifa());
	        }
			nuevoAlumno.setFechaAlta(nuevoAlumnoDTO.getFechaAlta());
			nuevoAlumno.setFechaBaja(nuevoAlumnoDTO.getFechaBaja());
			nuevoAlumno.setAutorizacionWeb(nuevoAlumnoDTO.getAutorizacionWeb());
			nuevoAlumno.setCategoria(categoria);
			nuevoAlumno.setGrado(grado);
			nuevoAlumno.setFotoAlumno(nuevoAlumnoDTO.getFotoAlumno());

			nuevoAlumno.setUsuario(usuarioExistente);
			
			usuarioExistente.setAlumno(nuevoAlumno);

			Alumno creado = alumnoService.crearAlumno(nuevoAlumno);
			AlumnoDTO creadoDTO = AlumnoDTO.deAlumno(creado);
			return new ResponseEntity<>(creadoDTO, HttpStatus.CREATED);
		} catch (IOException e) {
			return new ResponseEntity<>("Error al procesar la solicitud", HttpStatus.BAD_REQUEST);
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
			@Valid @RequestParam(value = "file", required = false) MultipartFile file,
			@Valid @RequestParam("alumnoEditado") String alumnoJson) {
		logger.info("## AlumnoController :: modificarAlumno");
		try {
			ObjectMapper objectMapper = new ObjectMapper();
			AlumnoDTO nuevoAlumnoDTO = objectMapper.readValue(alumnoJson, AlumnoDTO.class);

			Imagen imagen = null;

			if (file != null) {
				Imagen img = new Imagen(file.getOriginalFilename(), file.getContentType(), file.getBytes());
				imagen = alumnoService.guardarImagen(img);
			}
			if (!alumnoService.fechaNacimientoValida(nuevoAlumnoDTO.getFechaNacimiento())) {
				throw new FechaNacimientoInvalidaException("La fecha de nacimiento es inválida.");
			}

			if (!alumnoService.datosAlumnoValidos(nuevoAlumnoDTO)) {
				throw new DatosAlumnoInvalidosException("Los datos del alumno actualizado son inválidos.");
			}

			Date nuevaFechaNacimiento = nuevoAlumnoDTO.getFechaNacimiento();
			Alumno alumno = alumnoService.actualizarAlumno(id, nuevoAlumnoDTO, nuevaFechaNacimiento, imagen);
			AlumnoDTO alumnoActualizadoDTO = AlumnoDTO.deAlumno(alumno);
			return new ResponseEntity<>(alumnoActualizadoDTO, HttpStatus.OK);

		} catch (IOException e) {
			return new ResponseEntity<>("Error al procesar la solicitud", HttpStatus.BAD_REQUEST);
		}
	}

	/**
	 * Elimina la imagen de un alumno especificado por su ID.
	 *
	 * @param id El ID del alumno cuya imagen se eliminará.
	 * @return ResponseEntity que indica el resultado de la eliminación. Retorna ResponseEntity.ok() si la eliminación es exitosa.
	 * @throws Exception Si ocurre un error durante la eliminación de la imagen del alumno.
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
		List<TurnoDTO> turnos = alumnoService.obtenerTurnosDelAlumno(alumnoId);
		return ResponseEntity.ok(turnos);
	}


	/**
	 * Asigna un alumno a un turno específico dentro de un grupo al que ya pertenece.
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

}