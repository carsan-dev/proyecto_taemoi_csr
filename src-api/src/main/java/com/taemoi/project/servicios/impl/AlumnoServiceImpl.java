package com.taemoi.project.servicios.impl;

import java.io.IOException;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.dtos.AlumnoDTO;
import com.taemoi.project.dtos.TurnoDTO;
import com.taemoi.project.entidades.Alumno;
import com.taemoi.project.entidades.Categoria;
import com.taemoi.project.entidades.Grado;
import com.taemoi.project.entidades.Grupo;
import com.taemoi.project.entidades.Imagen;
import com.taemoi.project.entidades.Roles;
import com.taemoi.project.entidades.TipoCategoria;
import com.taemoi.project.entidades.TipoGrado;
import com.taemoi.project.entidades.TipoTarifa;
import com.taemoi.project.entidades.Turno;
import com.taemoi.project.entidades.Usuario;
import com.taemoi.project.errores.alumno.AlumnoDuplicadoException;
import com.taemoi.project.errores.alumno.AlumnoNoEncontradoException;
import com.taemoi.project.repositorios.AlumnoRepository;
import com.taemoi.project.repositorios.CategoriaRepository;
import com.taemoi.project.repositorios.GradoRepository;
import com.taemoi.project.repositorios.GrupoRepository;
import com.taemoi.project.repositorios.ImagenRepository;
import com.taemoi.project.repositorios.TurnoRepository;
import com.taemoi.project.repositorios.UsuarioRepository;
import com.taemoi.project.servicios.AlumnoService;
import com.taemoi.project.servicios.ImagenService;

import jakarta.persistence.criteria.Predicate;

/**
 * Implementación del servicio para operaciones relacionadas con los alumnos.
 */
@Service
public class AlumnoServiceImpl implements AlumnoService {

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
	private ImagenService imagenService;

	/**
	 * Inyección del PasswordEncoder para codificar la contraseña del usuario
	 * creado.
	 */
	@Autowired
	private PasswordEncoder passwordEncoder;

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
	public Optional<AlumnoDTO> obtenerAlumnoDTOPorId(@NonNull Long id) {
		Optional<Alumno> optionalAlumno = obtenerAlumnoPorId(id);
		return optionalAlumno.map(this::mapeoParaAlumnoDTO);
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
		return alumnoRepository.findAll((root, query, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();

			if (nombre != null && !nombre.isEmpty()) {
				predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("nombre")),
						"%" + nombre.toLowerCase() + "%"));
			}
			if (gradoId != null) {
				predicates.add(criteriaBuilder.equal(root.get("grado").get("id"), gradoId));
			}
			if (categoriaId != null) {
				predicates.add(criteriaBuilder.equal(root.get("categoria").get("id"), categoriaId));
			}
			if (!incluirInactivos) {
				predicates.add(criteriaBuilder.equal(root.get("activo"), true));
			}

			return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
		}, pageable);
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
		return alumnoRepository.findAll((root, query, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();

			if (nombre != null && !nombre.isEmpty()) {
				predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("nombre")),
						"%" + nombre.toLowerCase() + "%"));
			}
			if (gradoId != null) {
				predicates.add(criteriaBuilder.equal(root.get("gradoId"), gradoId));
			}
			if (categoriaId != null) {
				predicates.add(criteriaBuilder.equal(root.get("categoriaId"), categoriaId));
			}
			if (!incluirInactivos) {
				predicates.add(criteriaBuilder.equal(root.get("activo"), true));
			}

			return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
		});
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

		// Set a default tarifa amount if it's not set or invalid
		if (alumno.getCuantiaTarifa() == null || alumno.getCuantiaTarifa() <= 0) {
			alumno.setCuantiaTarifa(asignarCuantiaTarifa(alumno.getTipoTarifa()));
		}

		// Set web authorization default if not set
		if (alumno.getAutorizacionWeb() == null) {
			alumno.setAutorizacionWeb(true);
		}

		// Set the current weight date if the student is a competitor
		if (Boolean.TRUE.equals(alumno.getCompetidor())) {
			alumno.setFechaPeso(new Date());
		}

		if (Boolean.TRUE.equals(alumno.getTieneLicencia())) {
			alumno.setFechaLicencia(new Date());
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
		nuevoAlumno.setEmail(nuevoAlumnoDTO.getEmail());
		nuevoAlumno.setTelefono(nuevoAlumnoDTO.getTelefono());
		nuevoAlumno.setTipoTarifa(nuevoAlumnoDTO.getTipoTarifa());

		// Asignar CuantiaTarifa si no está definida o es menor o igual a 0
		if (nuevoAlumnoDTO.getCuantiaTarifa() == null || nuevoAlumnoDTO.getCuantiaTarifa() <= 0) {
			nuevoAlumno.setCuantiaTarifa(asignarCuantiaTarifa(nuevoAlumnoDTO.getTipoTarifa()));
		} else {
			nuevoAlumno.setCuantiaTarifa(nuevoAlumnoDTO.getCuantiaTarifa());
		}

		// Asignar AutorizacionWeb, si no está definida por defecto a true
		nuevoAlumno.setAutorizacionWeb(
				nuevoAlumnoDTO.getAutorizacionWeb() != null ? nuevoAlumnoDTO.getAutorizacionWeb() : true);

		// Asignar Competidor, Peso y FechaPeso si es aplicable
		nuevoAlumno.setCompetidor(nuevoAlumnoDTO.getCompetidor());
		if (nuevoAlumnoDTO.getCompetidor() != null && nuevoAlumnoDTO.getCompetidor()) {
			nuevoAlumno.setPeso(nuevoAlumnoDTO.getPeso());
			nuevoAlumno.setFechaPeso(new Date());
		}

		// Asignar categoría según la edad del Alumno
		int edad = calcularEdad(nuevoAlumnoDTO.getFechaNacimiento());
		Categoria categoria = asignarCategoriaSegunEdad(edad);
		nuevoAlumno.setCategoria(categoria);

		// Asignar Grado según la edad del Alumno
		Grado grado = asignarGradoSegunEdad(nuevoAlumnoDTO);
		nuevoAlumno.setGrado(grado);

		nuevoAlumno.setAptoParaExamen(esAptoParaExamen(nuevoAlumno));

		// Asignar imagen si se proporcionó
		if (nuevoAlumnoDTO.getFotoAlumno() != null) {
			// Guardar la imagen antes de asignarla al alumno
			Imagen imagenGuardada = imagenRepository.save(nuevoAlumnoDTO.getFotoAlumno());
			nuevoAlumno.setFotoAlumno(imagenGuardada);
		}

		// Asignar fecha de alta
		nuevoAlumno.setFechaAlta(nuevoAlumnoDTO.getFechaAlta() != null ? nuevoAlumnoDTO.getFechaAlta() : new Date());

		nuevoAlumno.setTieneLicencia(nuevoAlumnoDTO.getTieneLicencia());
		if (nuevoAlumnoDTO.getTieneLicencia() != null && nuevoAlumnoDTO.getTieneLicencia()) {
			nuevoAlumno.setNumeroLicencia(nuevoAlumnoDTO.getNumeroLicencia());
			nuevoAlumno.setFechaLicencia(nuevoAlumnoDTO.getFechaLicencia());
		}

		// Generar y asignar el número de expediente
		Integer maxNumeroExpediente = alumnoRepository.findMaxNumeroExpediente(); // Asegúrate de tener este método en
																					// tu repositorio
		nuevoAlumno.setNumeroExpediente(maxNumeroExpediente == null ? 1 : maxNumeroExpediente + 1);

		// Guardar primero el Alumno
		Alumno alumnoGuardado = alumnoRepository.save(nuevoAlumno);

		// Verificar si el Usuario ya existe o crear uno nuevo
		Usuario usuarioExistente = usuarioRepository.findByEmail(nuevoAlumnoDTO.getEmail()).orElse(null);
		if (usuarioExistente == null) {
			usuarioExistente = new Usuario();
			usuarioExistente.setNombre(nuevoAlumnoDTO.getNombre());
			usuarioExistente.setApellidos(nuevoAlumnoDTO.getApellidos());
			usuarioExistente.setEmail(nuevoAlumnoDTO.getEmail());

			// Generar y asignar contraseña al Usuario
			String contrasena = generarContrasena(nuevoAlumnoDTO.getNombre(), nuevoAlumnoDTO.getApellidos());
			usuarioExistente.setContrasena(contrasena);

			// Asignar roles de usuario
			Set<Roles> roles = new HashSet<>();
			roles.add(Roles.ROLE_USER);
			usuarioExistente.setRoles(roles);

			// Asignar Alumno guardado al Usuario
			usuarioExistente.setAlumno(alumnoGuardado);

			// Guardar el Usuario
			usuarioRepository.save(usuarioExistente);
		} else {
			// Si el Usuario ya existe, asegurar que esté asociado con el Alumno guardado
			usuarioExistente.setAlumno(alumnoGuardado);
			usuarioRepository.save(usuarioExistente);
		}

		// Finalmente, retornar el Alumno guardado
		return alumnoGuardado;
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
	public Alumno actualizarAlumno(@NonNull Long id, AlumnoDTO alumnoActualizado, Date nuevaFechaNacimiento,
			MultipartFile nuevaImagen) {
		Optional<Alumno> optionalAlumno = alumnoRepository.findById(id);
		if (optionalAlumno.isPresent()) {
			Alumno alumnoExistente = optionalAlumno.get();

			// Actualizar datos generales del alumno
			alumnoExistente.setNombre(alumnoActualizado.getNombre());
			alumnoExistente.setApellidos(alumnoActualizado.getApellidos());
			alumnoExistente.setFechaNacimiento(nuevaFechaNacimiento);

			// Actualizar la edad y categoría según la nueva fecha de nacimiento
			int nuevaEdad = calcularEdad(nuevaFechaNacimiento);
			Categoria nuevaCategoria = asignarCategoriaSegunEdad(nuevaEdad);
			alumnoExistente.setCategoria(nuevaCategoria);

			Grado nuevoGrado = gradoRepository.findByTipoGrado(
					alumnoActualizado.getGrado() != null ? TipoGrado.valueOf(alumnoActualizado.getGrado()) : null);
			alumnoExistente.setGrado(nuevoGrado);
			
			alumnoExistente.setAptoParaExamen(esAptoParaExamen(alumnoExistente));

			// Actualizar otros campos del alumno
			alumnoExistente.setNif(alumnoActualizado.getNif());
			alumnoExistente.setDireccion(alumnoActualizado.getDireccion());
			alumnoExistente.setEmail(alumnoActualizado.getEmail());
			alumnoExistente.setTelefono(alumnoActualizado.getTelefono());
			alumnoExistente.setTipoTarifa(alumnoActualizado.getTipoTarifa());
			alumnoExistente.setFechaAlta(alumnoActualizado.getFechaAlta());
			alumnoExistente.setFechaBaja(alumnoActualizado.getFechaBaja());
			alumnoExistente.setAutorizacionWeb(alumnoActualizado.getAutorizacionWeb());

			alumnoExistente.setTieneLicencia(Optional.ofNullable(alumnoActualizado.getTieneLicencia()).orElse(false));
			if (alumnoActualizado.getTieneLicencia() != null && alumnoActualizado.getTieneLicencia()) {
				alumnoExistente.setNumeroLicencia(alumnoActualizado.getNumeroLicencia());
				alumnoExistente.setFechaLicencia(alumnoActualizado.getFechaLicencia());
			}

			// Actualizar los campos relacionados con "competidor"
			alumnoExistente.setCompetidor(Optional.ofNullable(alumnoActualizado.getCompetidor()).orElse(false));
			if (alumnoActualizado.getCompetidor() != null && alumnoActualizado.getCompetidor()) {
				alumnoExistente.setPeso(alumnoActualizado.getPeso());
				alumnoExistente.setFechaPeso(alumnoActualizado.getFechaPeso());
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

			// Si no se especifica una cuantía de tarifa o es inválida, se asigna una por
			// defecto
			if (alumnoActualizado.getCuantiaTarifa() == null || alumnoActualizado.getCuantiaTarifa() <= 0) {
				alumnoExistente.setCuantiaTarifa(asignarCuantiaTarifa(alumnoActualizado.getTipoTarifa()));
			} else {
				alumnoExistente.setCuantiaTarifa(alumnoActualizado.getCuantiaTarifa());
			}

			// Guardar los cambios en el alumno en la base de datos
			return alumnoRepository.save(alumnoExistente);
		} else {
			throw new RuntimeException("No se encontró el alumno con ID: " + id);
		}
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
				// Eliminar la imagen del sistema de archivos antes de eliminar la referencia en
				// la base de datos
				imagenService.eliminarImagenDeSistema(imagen);

				// Ahora eliminar la referencia en la base de datos
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

	/**
	 * Elimina un alumno por su ID y elimina su imagen.
	 *
	 * @param id El ID del alumno a eliminar.
	 * @return true si se elimina con éxito, false si el alumno no existe.
	 */
	@Override
	public boolean eliminarAlumno(@NonNull Long id) {
		return alumnoRepository.findById(id).map(alumno -> {

			// Eliminar la imagen del alumno si existe
			Imagen imagen = alumno.getFotoAlumno();
			if (imagen != null) {
				// Primero eliminar la imagen del sistema de archivos
				imagenService.eliminarImagenDeSistema(imagen);

				// Luego eliminar la imagen de la base de datos
				imagenRepository.delete(imagen);
			}

			// Eliminar el usuario asociado si existe
			if (alumno.getUsuario() != null) {
				usuarioRepository.delete(alumno.getUsuario());
			}

			// Eliminar las relaciones con grupos si existen
			for (Grupo grupo : grupoRepository.findAll()) {
				if (grupo.getAlumnos().contains(alumno)) {
					grupo.getAlumnos().remove(alumno);
					grupoRepository.save(grupo);
				}
			}

			// Finalmente, eliminar el alumno
			alumnoRepository.delete(alumno);
			return true;
		}).orElse(false);
	}

	@Override
	public Alumno darDeBajaAlumno(@NonNull Long id) {
		Optional<Alumno> optionalAlumno = alumnoRepository.findById(id);
		if (optionalAlumno.isPresent()) {
			Alumno alumno = optionalAlumno.get();
			alumno.setActivo(false);
			alumno.setFechaBaja(new Date());
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
			alumno.setActivo(true);
			alumno.setFechaBaja(null);
			return alumnoRepository.save(alumno);
		} else {
			throw new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + id);
		}
	}

	@Override
	public List<TurnoDTO> obtenerTurnosDelAlumno(Long alumnoId) {
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + alumnoId));

		// Mapeando los turnos asignados al alumno a DTOs
		return alumno.getTurnos().stream().map(
				turno -> new TurnoDTO(turno.getId(), turno.getDiaSemana(), turno.getHoraInicio(), turno.getHoraFin()))
				.collect(Collectors.toList());
	}

	@Override
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

	/**
	 * Asigna la cuantía de la tarifa según el tipo de tarifa.
	 *
	 * @param tipoTarifa El tipo de tarifa del alumno.
	 * @return La cuantía asignada.
	 */
	@Override
	public double asignarCuantiaTarifa(TipoTarifa tipoTarifa) {
		switch (tipoTarifa) {
		case ADULTO:
			return 30.0;
		case ADULTO_GRUPO:
			return 20.0;
		case FAMILIAR:
			return 0.0;
		case INFANTIL:
			return 25.0;
		case INFANTIL_GRUPO:
			return 20.0;
		case HERMANOS:
			return 23.0;
		case PADRES_HIJOS:
			return 0.0;
		default:
			throw new IllegalArgumentException("Tipo de tarifa no válido: " + tipoTarifa);
		}
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
		if (edad >= 8 && edad <= 9) {
			tipoCategoria = TipoCategoria.INFANTIL;
		} else if (edad >= 10 && edad <= 11) {
			tipoCategoria = TipoCategoria.PRECADETE;
		} else if (edad >= 12 && edad <= 14) {
			tipoCategoria = TipoCategoria.CADETE;
		} else if (edad >= 15 && edad <= 17) {
			tipoCategoria = TipoCategoria.JUNIOR;
		} else if (edad >= 16 && edad <= 20) {
			tipoCategoria = TipoCategoria.SUB21;
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
	    LocalDate fechaNacimiento = nuevoAlumnoDTO.getFechaNacimiento().toInstant()
	            .atZone(ZoneId.systemDefault())
	            .toLocalDate();
	    LocalDate fechaActual = LocalDate.now();
	    int edad = Period.between(fechaNacimiento, fechaActual).getYears();

	    // Verificar si cumple 14 años en el año actual
	    boolean cumpleCatorceEsteAno = fechaNacimiento.plusYears(14).getYear() == fechaActual.getYear();

	    // Se considera menor si tiene menos de 13 años o tiene 13 pero no cumple 14 este año
	    boolean esMenor = edad < 13 || (edad == 13 && !cumpleCatorceEsteAno);

	    List<TipoGrado> gradosDisponibles;
	    
	    // Si el alumno es menor, asignar grados correspondientes a menores
	    if (esMenor) {
	        gradosDisponibles = Arrays.asList(
	            TipoGrado.BLANCO, TipoGrado.BLANCO_AMARILLO, TipoGrado.AMARILLO,
	            TipoGrado.AMARILLO_NARANJA, TipoGrado.NARANJA, TipoGrado.NARANJA_VERDE,
	            TipoGrado.VERDE, TipoGrado.VERDE_AZUL, TipoGrado.AZUL,
	            TipoGrado.AZUL_ROJO, TipoGrado.ROJO, TipoGrado.ROJO_NEGRO
	        );
	    } else {
	        // Si el alumno es adulto, asignar grados correspondientes a adultos
	        gradosDisponibles = Arrays.asList(
	            TipoGrado.BLANCO, TipoGrado.AMARILLO, TipoGrado.NARANJA, 
	            TipoGrado.VERDE, TipoGrado.AZUL, TipoGrado.ROJO, TipoGrado.NEGRO
	        );
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
	 * Calcula la edad a partir de la fecha de nacimiento.
	 *
	 * @param fechaNacimiento La fecha de nacimiento del alumno.
	 * @return La edad calculada.
	 */
	@Override
	public int calcularEdad(Date fechaNacimiento) {
		LocalDate fechaNacimientoLocal = fechaNacimiento.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		LocalDate fechaActual = LocalDate.now();
		int edad = Period.between(fechaNacimientoLocal, fechaActual).getYears();

		return edad;
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
		if (alumnoDTO.getTipoTarifa() == null) {
			return false;
		}
		if (alumnoDTO.getFechaAlta() == null || alumnoDTO.getFechaAlta().after(new Date())) {
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
	 * Mapea un objeto Alumno a un objeto AlumnoDTO.
	 *
	 * @param alumno El objeto Alumno a mapear.
	 * @return El objeto AlumnoDTO mapeado.
	 */
	private AlumnoDTO mapeoParaAlumnoDTO(Alumno alumno) {
		if (alumno == null) {
			return null;
		}

		String categoriaNombre = alumno.getCategoria() != null ? alumno.getCategoria().getNombre() : null;
		String gradoTipo = alumno.getGrado() != null && alumno.getGrado().getTipoGrado() != null
				? alumno.getGrado().getTipoGrado().name()
				: null;

		return new AlumnoDTO(alumno.getId(), alumno.getNombre(), alumno.getApellidos(), alumno.getFechaNacimiento(),
				alumno.getNumeroExpediente(), alumno.getNif(), alumno.getDireccion(), alumno.getEmail(),
				alumno.getTelefono(), alumno.getCuantiaTarifa(), alumno.getTipoTarifa(), alumno.getFechaAlta(),
				alumno.getFechaBaja(), alumno.getActivo(), alumno.getAutorizacionWeb(), alumno.getCompetidor(),
				alumno.getPeso(), alumno.getFechaPeso(), categoriaNombre, gradoTipo, alumno.getFotoAlumno(),
				alumno.getTieneLicencia(), alumno.getNumeroLicencia(), alumno.getFechaLicencia(),
				alumno.getAptoParaExamen());
	}

	/**
	 * Calcula si un alumno es apto para examen según su grado y su edad.
	 * 
	 * @param alumno El alumno para el cual se realiza el cálculo.
	 * @return true si el alumno es apto para examen, false en caso contrario.
	 */
	private boolean esAptoParaExamen(Alumno alumno) {
		if (alumno.getGrado() == null || alumno.getGrado().getFechaGrado() == null) {
			return false;
		}

		// Calcular la edad del alumno
		int edad = calcularEdad(alumno.getFechaNacimiento());

		// Obtener los meses requeridos en función de la edad y el tipo de grado
		long mesesRequeridos = obtenerMesesRequeridosParaExamen(edad, alumno.getGrado().getTipoGrado());

		// Calcular la fecha mínima para el próximo examen
		Calendar fechaExamenPosible = Calendar.getInstance();
		fechaExamenPosible.setTime(alumno.getGrado().getFechaGrado());
		fechaExamenPosible.add(Calendar.MONTH, (int) mesesRequeridos);

		// Si la fecha actual es igual o mayor que la fecha mínima, es apto para examen
		return !new Date().before(fechaExamenPosible.getTime());
	}

	/**
	 * Calcula los meses requeridos para ser apto para examen según la edad y el
	 * grado.
	 * 
	 * @param edad      La edad del alumno.
	 * @param tipoGrado El grado actual del alumno.
	 * @return Los meses requeridos para ser apto para examen.
	 */
	private long obtenerMesesRequeridosParaExamen(int edad, TipoGrado tipoGrado) {
		if (edad < 13) {
			switch (tipoGrado) {
			case BLANCO:
				return 2;
			case BLANCO_AMARILLO:
				return 2;
			case AMARILLO:
				return 3;
			case AMARILLO_NARANJA:
				return 3;
			case NARANJA:
				return 4;
			case NARANJA_VERDE:
				return 4;
			case VERDE:
				return 6;
			case VERDE_AZUL:
				return 6;
			case AZUL:
				return 8;
			case AZUL_ROJO:
				return 10;
			case ROJO:
				return 12;
			case ROJO_NEGRO:
				return 12;
			default:
				return Long.MAX_VALUE; // No apto si no coincide
			}
		} else {
			switch (tipoGrado) {
			case BLANCO:
				return 3;
			case AMARILLO:
				return 5;
			case NARANJA:
				return 6;
			case VERDE:
				return 8;
			case AZUL:
				return 10;
			case ROJO:
				return 12;
			default:
				return Long.MAX_VALUE; // No apto si no coincide
			}
		}
	}
}