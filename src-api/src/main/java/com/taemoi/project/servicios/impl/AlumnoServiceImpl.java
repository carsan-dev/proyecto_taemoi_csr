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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import com.taemoi.project.dtos.response.AlumnoConGruposDTO;
import com.taemoi.project.dtos.response.AlumnoConvocatoriaDTO;
import com.taemoi.project.entidades.Alumno;
import com.taemoi.project.entidades.AlumnoConvocatoria;
import com.taemoi.project.entidades.Categoria;
import com.taemoi.project.entidades.Convocatoria;
import com.taemoi.project.entidades.Deporte;
import com.taemoi.project.entidades.Grado;
import com.taemoi.project.entidades.Grupo;
import com.taemoi.project.entidades.Imagen;
import com.taemoi.project.entidades.Producto;
import com.taemoi.project.entidades.ProductoAlumno;
import com.taemoi.project.entidades.Roles;
import com.taemoi.project.entidades.TipoCategoria;
import com.taemoi.project.entidades.TipoGrado;
import com.taemoi.project.entidades.TipoTarifa;
import com.taemoi.project.entidades.Turno;
import com.taemoi.project.entidades.Usuario;
import com.taemoi.project.errores.alumno.AlumnoDuplicadoException;
import com.taemoi.project.errores.alumno.AlumnoNoEncontradoException;
import com.taemoi.project.repositorios.AlumnoConvocatoriaRepository;
import com.taemoi.project.repositorios.AlumnoRepository;
import com.taemoi.project.repositorios.CategoriaRepository;
import com.taemoi.project.repositorios.ConvocatoriaRepository;
import com.taemoi.project.repositorios.GradoRepository;
import com.taemoi.project.repositorios.GrupoRepository;
import com.taemoi.project.repositorios.ImagenRepository;
import com.taemoi.project.repositorios.ProductoAlumnoRepository;
import com.taemoi.project.repositorios.ProductoRepository;
import com.taemoi.project.repositorios.TurnoRepository;
import com.taemoi.project.repositorios.UsuarioRepository;
import com.taemoi.project.servicios.AlumnoService;
import com.taemoi.project.servicios.ImagenService;

import jakarta.persistence.criteria.Expression;
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
	private ConvocatoriaRepository convocatoriaRepository;
	
	@Autowired
	private AlumnoConvocatoriaRepository alumnoConvocatoriaRepository;
	
	@Autowired
	private ProductoRepository productoRepository;
	
	@Autowired
	private ProductoAlumnoRepository productoAlumnoRepository;

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
		return optionalAlumno.map(AlumnoDTO::deAlumno);
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
	            String nombreLower = nombre.toLowerCase();

	            // Crear expresión para el nombre completo (nombre + ' ' + apellidos)
	            Expression<String> fullNameExpression = criteriaBuilder.concat(
	                    criteriaBuilder.lower(root.get("nombre")), 
	                    criteriaBuilder.literal(" ")
	            );
	            fullNameExpression = criteriaBuilder.concat(fullNameExpression, criteriaBuilder.lower(root.get("apellidos")));

	            // Crear predicado que compara el nombre completo con el valor buscado
	            Predicate fullNamePredicate = criteriaBuilder.like(fullNameExpression, "%" + nombreLower + "%");

	            // También comparar individualmente el nombre y los apellidos
	            Predicate nombrePredicate = criteriaBuilder.like(criteriaBuilder.lower(root.get("nombre")), "%" + nombreLower + "%");
	            Predicate apellidosPredicate = criteriaBuilder.like(criteriaBuilder.lower(root.get("apellidos")), "%" + nombreLower + "%");

	            // Combinar los predicados con OR
	            predicates.add(criteriaBuilder.or(fullNamePredicate, nombrePredicate, apellidosPredicate));
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
	public List<Alumno> obtenerAlumnosFiltrados(String nombre, Long gradoId, Long categoriaId, boolean incluirInactivos) {
	    return alumnoRepository.findAll((root, query, criteriaBuilder) -> {
	        List<Predicate> predicates = new ArrayList<>();

	        if (nombre != null && !nombre.isEmpty()) {
	            String nombreLower = nombre.toLowerCase();

	            // Crear expresión para el nombre completo (nombre + ' ' + apellidos)
	            Expression<String> fullNameExpression = criteriaBuilder.concat(
	                    criteriaBuilder.lower(root.get("nombre")), 
	                    criteriaBuilder.literal(" ")
	            );
	            fullNameExpression = criteriaBuilder.concat(fullNameExpression, criteriaBuilder.lower(root.get("apellidos")));

	            // Crear predicado que compara el nombre completo con el valor buscado
	            Predicate fullNamePredicate = criteriaBuilder.like(fullNameExpression, "%" + nombreLower + "%");

	            // También comparar individualmente el nombre y los apellidos
	            Predicate nombrePredicate = criteriaBuilder.like(criteriaBuilder.lower(root.get("nombre")), "%" + nombreLower + "%");
	            Predicate apellidosPredicate = criteriaBuilder.like(criteriaBuilder.lower(root.get("apellidos")), "%" + nombreLower + "%");

	            // Combinar los predicados con OR
	            predicates.add(criteriaBuilder.or(fullNamePredicate, nombrePredicate, apellidosPredicate));
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

		if (alumno.getGrado() != null) {
			alumno.setFechaGrado(new Date()); // Fecha de grado específica para el alumno
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
		nuevoAlumno.setDeporte(nuevoAlumnoDTO.getDeporte());

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

		// Asignar categoría si es competidor el Alumno
		if (nuevoAlumnoDTO.getCompetidor() != null && nuevoAlumnoDTO.getCompetidor()) {
			int edad = calcularEdad(nuevoAlumnoDTO.getFechaNacimiento());
			Categoria categoria = asignarCategoriaSegunEdad(edad);
			nuevoAlumno.setCategoria(categoria);
		}

		if (nuevoAlumnoDTO.getGrado() != null && !nuevoAlumnoDTO.getGrado().isEmpty()) {
			// Buscar y asignar el grado seleccionado
			TipoGrado tipoGradoSeleccionado = TipoGrado.valueOf(nuevoAlumnoDTO.getGrado());
			Grado gradoSeleccionado = gradoRepository.findByTipoGrado(tipoGradoSeleccionado);
			nuevoAlumno.setGrado(gradoSeleccionado);
		} else {
			// Si no se seleccionó un grado, puedes asignar un valor por defecto (ejemplo:
			// Blanco)
			Grado gradoPorDefecto = gradoRepository.findByTipoGrado(TipoGrado.BLANCO);
			nuevoAlumno.setGrado(gradoPorDefecto);
		}

		// **Asignar fecha de grado actual al alumno**
		nuevoAlumno.setFechaGrado(new Date());

		if (nuevoAlumnoDTO.getAptoParaExamen() != null) {
			// Asignar el valor manualmente si está presente en el DTO
			nuevoAlumno.setAptoParaExamen(nuevoAlumnoDTO.getAptoParaExamen());
		} else {
			// Si no está presente, calcular automáticamente si es apto
			nuevoAlumno.setAptoParaExamen(esAptoParaExamen(nuevoAlumno));
		}

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

			// Obtener el grado actual
			Grado gradoActual = alumnoExistente.getGrado();

			// Buscar el nuevo grado (si se actualiza)
			Grado nuevoGrado = gradoRepository.findByTipoGrado(
					alumnoActualizado.getGrado() != null ? TipoGrado.valueOf(alumnoActualizado.getGrado()) : null);

			// **Si el grado cambia, actualizar la fecha de grado**
			if (nuevoGrado != null && !nuevoGrado.equals(gradoActual)) {
				alumnoExistente.setGrado(nuevoGrado);
				alumnoExistente.setFechaGrado(new Date()); // Fecha actual si el grado cambia
			}

			// Actualizar otros campos del alumno
			alumnoExistente.setNif(alumnoActualizado.getNif());
			alumnoExistente.setDireccion(alumnoActualizado.getDireccion());
			alumnoExistente.setEmail(alumnoActualizado.getEmail());
			alumnoExistente.setTelefono(alumnoActualizado.getTelefono());
			alumnoExistente.setTipoTarifa(alumnoActualizado.getTipoTarifa());
			alumnoExistente.setFechaAlta(alumnoActualizado.getFechaAlta());
			alumnoExistente.setFechaBaja(alumnoActualizado.getFechaBaja());
			alumnoExistente.setAutorizacionWeb(alumnoActualizado.getAutorizacionWeb());
			alumnoExistente.setDeporte(alumnoActualizado.getDeporte());

			alumnoExistente.setTieneLicencia(Optional.ofNullable(alumnoActualizado.getTieneLicencia()).orElse(false));
			if (alumnoActualizado.getTieneLicencia() != null && alumnoActualizado.getTieneLicencia()) {
				alumnoExistente.setNumeroLicencia(alumnoActualizado.getNumeroLicencia());
				alumnoExistente.setFechaLicencia(alumnoActualizado.getFechaLicencia());
			}

			// Actualizar los campos relacionados con "competidor"
			alumnoExistente.setCompetidor(Optional.ofNullable(alumnoActualizado.getCompetidor()).orElse(false));
			if (alumnoActualizado.getCompetidor() != null && alumnoActualizado.getCompetidor()) {
				// Si es competidor, actualizar el peso y la fecha de peso
				alumnoExistente.setPeso(alumnoActualizado.getPeso());
				alumnoExistente.setFechaPeso(alumnoActualizado.getFechaPeso());

				// Actualizar la categoría en función de la edad
				int nuevaEdad = calcularEdad(nuevaFechaNacimiento);
				Categoria nuevaCategoria = asignarCategoriaSegunEdad(nuevaEdad);
				alumnoExistente.setCategoria(nuevaCategoria);
			} else {
				// Si ya no es competidor, eliminar la categoría
				alumnoExistente.setCategoria(null);
			}

			if (alumnoActualizado.getCompetidor() != null && alumnoActualizado.getCompetidor()) {
				int nuevaEdad = calcularEdad(nuevaFechaNacimiento);
				Categoria nuevaCategoria = asignarCategoriaSegunEdad(nuevaEdad);
				alumnoExistente.setCategoria(nuevaCategoria);
			}

			if (alumnoActualizado.getAptoParaExamen() != null) {
				alumnoExistente.setAptoParaExamen(alumnoActualizado.getAptoParaExamen());
			} else {
				// Si no se asignó manualmente, calcular si es apto automáticamente
				alumnoExistente.setAptoParaExamen(esAptoParaExamen(alumnoExistente));
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
		return alumno.getTurnos().stream().map(turno -> new TurnoDTO(turno.getId(), turno.getDiaSemana(),
				turno.getHoraInicio(), turno.getHoraFin(), turno.getTipo())).collect(Collectors.toList());
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

	// Método para obtener alumnos aptos y mapear a AlumnoConGruposDTO
	public List<AlumnoConGruposDTO> obtenerAlumnosAptosConGruposDTO() {
		List<Alumno> alumnos = alumnoRepository.findByAptoParaExamenTrue(); // Método existente

		// Mapeo a AlumnoConGruposDTO
		return alumnos.stream().map(AlumnoConGruposDTO::deAlumnoConGrupos) // Reutilizamos el método estático de mapeo
				.collect(Collectors.toList());
	}

	// Servicio para obtener alumnos aptos para examen por deporte, excluyendo
	// "competición"
	@Override
	public List<AlumnoConGruposDTO> obtenerAlumnosAptosPorDeporte(String deporte, String exclusion) {
		List<Alumno> alumnos = alumnoRepository.findAptosParaExamenPorDeporte(deporte, exclusion);

		// Mapeamos la lista de alumnos a AlumnoConGruposDTO
		return alumnos.stream().map(AlumnoConGruposDTO::deAlumnoConGrupos) // Reutilizamos el método de mapeo
				.collect(Collectors.toList());
	}

	// Servicio para obtener un alumno apto para examen por su ID
	@Override
	public Optional<AlumnoConGruposDTO> obtenerAlumnoAptoPorId(Long id) {
		Optional<Alumno> alumno = alumnoRepository.findAptoParaExamenById(id);

		// Convertimos el alumno a AlumnoConGruposDTO si está presente
		return alumno.map(AlumnoConGruposDTO::deAlumnoConGrupos);
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
		case PILATES:
			return 30.0;
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
		} else if (edad >= 15 && edad <= 16) {
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
		LocalDate fechaNacimiento = nuevoAlumnoDTO.getFechaNacimiento().toInstant().atZone(ZoneId.systemDefault())
				.toLocalDate();
		LocalDate fechaActual = LocalDate.now();
		int edad = Period.between(fechaNacimiento, fechaActual).getYears();

		// Verificar si cumple 14 años en el año actual
		boolean cumpleCatorceEsteAno = fechaNacimiento.plusYears(14).getYear() == fechaActual.getYear();

		// Se considera menor si tiene menos de 13 años o tiene 13 pero no cumple 14
		// este año
		boolean esMenor = edad < 13 || (edad == 13 && !cumpleCatorceEsteAno);

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
					TipoGrado.AZUL, TipoGrado.ROJO, TipoGrado.NEGRO_1_DAN, TipoGrado.NEGRO_2_DAN,
					TipoGrado.NEGRO_3_DAN, TipoGrado.NEGRO_4_DAN, TipoGrado.NEGRO_5_DAN);
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
	    LocalDate fechaNacimientoLocal;

	    if (fechaNacimiento instanceof java.sql.Date) {
	        // Convertir java.sql.Date a LocalDate
	        fechaNacimientoLocal = ((java.sql.Date) fechaNacimiento).toLocalDate();
	    } else {
	        // Convertir java.util.Date a LocalDate
	        fechaNacimientoLocal = fechaNacimiento.toInstant()
	                .atZone(ZoneId.systemDefault())
	                .toLocalDate();
	    }

	    LocalDate fechaActual = LocalDate.now();
	    return Period.between(fechaNacimientoLocal, fechaActual).getYears();
	}

	
	@Override
	public TipoGrado calcularSiguienteGrado(Alumno alumno) {
	    TipoGrado gradoActual = alumno.getGrado().getTipoGrado();
	    Deporte deporte = alumno.getDeporte();

	    int edad = calcularEdad(alumno.getFechaNacimiento());

	    boolean esMenor = edad < 13 || (edad == 13 && !cumple14EsteAnio(alumno.getFechaNacimiento()));

	    Map<TipoGrado, TipoGrado> nextGradeMap;

	    if (deporte == Deporte.TAEKWONDO) {
	        nextGradeMap = esMenor ? mapaGradosMenoresTaekwondo() : mapaGradosMayoresTaekwondo();
	    } else if (deporte == Deporte.KICKBOXING) {
	        nextGradeMap = mapaGradosKickboxing();
	    } else {
	        throw new IllegalArgumentException("Deporte no soportado: " + deporte);
	    }

	    return nextGradeMap.getOrDefault(gradoActual, null);
	}

	private Map<TipoGrado, TipoGrado> mapaGradosMenoresTaekwondo() {
	    Map<TipoGrado, TipoGrado> mapa = new LinkedHashMap<>();
	    mapa.put(TipoGrado.BLANCO, TipoGrado.BLANCO_AMARILLO);
	    mapa.put(TipoGrado.BLANCO_AMARILLO, TipoGrado.AMARILLO);
	    mapa.put(TipoGrado.AMARILLO, TipoGrado.AMARILLO_NARANJA);
	    mapa.put(TipoGrado.AMARILLO_NARANJA, TipoGrado.NARANJA);
	    mapa.put(TipoGrado.NARANJA, TipoGrado.NARANJA_VERDE);
	    mapa.put(TipoGrado.NARANJA_VERDE, TipoGrado.VERDE);
	    mapa.put(TipoGrado.VERDE, TipoGrado.VERDE_AZUL);
	    mapa.put(TipoGrado.VERDE_AZUL, TipoGrado.AZUL);
	    mapa.put(TipoGrado.AZUL, TipoGrado.AZUL_ROJO);
	    mapa.put(TipoGrado.AZUL_ROJO, TipoGrado.ROJO);
	    mapa.put(TipoGrado.ROJO, TipoGrado.ROJO_NEGRO_1_PUM);
	    mapa.put(TipoGrado.ROJO_NEGRO_1_PUM, TipoGrado.ROJO_NEGRO_2_PUM);
	    mapa.put(TipoGrado.ROJO_NEGRO_2_PUM, TipoGrado.ROJO_NEGRO_3_PUM);
	    return mapa;
	}

	private Map<TipoGrado, TipoGrado> mapaGradosMayoresTaekwondo() {
	    Map<TipoGrado, TipoGrado> mapa = new LinkedHashMap<>();
	    mapa.put(TipoGrado.BLANCO, TipoGrado.AMARILLO);
	    mapa.put(TipoGrado.AMARILLO, TipoGrado.NARANJA);
	    mapa.put(TipoGrado.NARANJA, TipoGrado.VERDE);
	    mapa.put(TipoGrado.VERDE, TipoGrado.AZUL);
	    mapa.put(TipoGrado.AZUL, TipoGrado.ROJO);
	    mapa.put(TipoGrado.ROJO, TipoGrado.NEGRO_1_DAN);
	    mapa.put(TipoGrado.NEGRO_1_DAN, TipoGrado.NEGRO_2_DAN);
	    mapa.put(TipoGrado.NEGRO_2_DAN, TipoGrado.NEGRO_3_DAN);
	    mapa.put(TipoGrado.NEGRO_3_DAN, TipoGrado.NEGRO_4_DAN);
	    mapa.put(TipoGrado.NEGRO_4_DAN, TipoGrado.NEGRO_5_DAN);
	    return mapa;
	}
	
	private Map<TipoGrado, TipoGrado> mapaGradosKickboxing() {
	    Map<TipoGrado, TipoGrado> mapa = new LinkedHashMap<>();
	    mapa.put(TipoGrado.BLANCO, TipoGrado.AMARILLO);
	    mapa.put(TipoGrado.AMARILLO, TipoGrado.NARANJA);
	    mapa.put(TipoGrado.NARANJA, TipoGrado.VERDE);
	    mapa.put(TipoGrado.VERDE, TipoGrado.AZUL);
	    mapa.put(TipoGrado.AZUL, TipoGrado.ROJO);
	    mapa.put(TipoGrado.ROJO, TipoGrado.NEGRO_1_DAN);
	    mapa.put(TipoGrado.NEGRO_1_DAN, TipoGrado.NEGRO_2_DAN);
	    mapa.put(TipoGrado.NEGRO_2_DAN, TipoGrado.NEGRO_3_DAN);
	    mapa.put(TipoGrado.NEGRO_3_DAN, TipoGrado.NEGRO_4_DAN);
	    mapa.put(TipoGrado.NEGRO_4_DAN, TipoGrado.NEGRO_5_DAN);
	    return mapa;
	}
	
	private boolean cumple14EsteAnio(Date fechaNacimiento) {
	    LocalDate fechaNacimientoLocal = fechaNacimiento.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
	    LocalDate fechaCumple14 = fechaNacimientoLocal.plusYears(14);
	    int anioActual = LocalDate.now().getYear();
	    return fechaCumple14.getYear() == anioActual;
	}
	
	public AlumnoConvocatoriaDTO agregarAlumnoAConvocatoria(Long alumnoId, Long convocatoriaId) {
	    Alumno alumno = alumnoRepository.findById(alumnoId)
	            .orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + alumnoId));

	    if (!alumno.getAptoParaExamen()) {
	        throw new IllegalArgumentException("El alumno no está apto para examen.");
	    }

	    Convocatoria convocatoriaSeleccionada = convocatoriaRepository.findById(convocatoriaId)
	            .orElseThrow(() -> new IllegalArgumentException("No se encontró la convocatoria con ID: " + convocatoriaId));

	    if (!alumno.getDeporte().equals(convocatoriaSeleccionada.getDeporte())) {
	        throw new IllegalArgumentException("El deporte del alumno no coincide con el deporte de la convocatoria.");
	    }

	    // Verificar si el alumno ya está en la convocatoria seleccionada
	    boolean yaInscrito = convocatoriaSeleccionada.getAlumnosConvocatoria().stream()
	            .anyMatch(ac -> ac.getAlumno().getId().equals(alumnoId));

	    if (yaInscrito) {
	        throw new IllegalArgumentException("El alumno ya está inscrito en la convocatoria seleccionada.");
	    }

	    // Calcular el siguiente grado del alumno
	    TipoGrado gradoSiguiente = calcularSiguienteGrado(alumno);
	    if (gradoSiguiente == null) {
	        throw new IllegalArgumentException("No se pudo determinar el siguiente grado para el alumno.");
	    }

	    // Obtener el producto correspondiente al siguiente grado
	    Producto productoExamen = obtenerProductoPorGrado(gradoSiguiente);
	    if (productoExamen == null) {
	        throw new IllegalArgumentException("No se encontró un producto para el grado: " + gradoSiguiente);
	    }

	    // Crear una instancia de ProductoAlumno
	    ProductoAlumno productoAlumno = new ProductoAlumno();
	    productoAlumno.setAlumno(alumno);
	    productoAlumno.setProducto(productoExamen);
	    productoAlumno.setConcepto(productoExamen.getConcepto());
	    productoAlumno.setFechaAsignacion(new Date());
	    productoAlumno.setCantidad(1);
	    productoAlumno.setPrecio(productoExamen.getPrecio());
	    productoAlumno.setPagado(false); // Por defecto, no pagado
	    productoAlumno.setNotas("Asignado automáticamente al agregar al alumno a la convocatoria.");

	    // Guardar ProductoAlumno
	    productoAlumno = productoAlumnoRepository.save(productoAlumno);

	    // Añadir ProductoAlumno a la lista del alumno
	    if (alumno.getProductosAlumno() == null) {
	        alumno.setProductosAlumno(new ArrayList<>());
	    }
	    alumno.getProductosAlumno().add(productoAlumno);

	    // Guardar cambios en el alumno
	    alumnoRepository.save(alumno);

	    // Crear el registro de AlumnoConvocatoria
	    AlumnoConvocatoria alumnoConvocatoria = new AlumnoConvocatoria();
	    alumnoConvocatoria.setAlumno(alumno);
	    alumnoConvocatoria.setConvocatoria(convocatoriaSeleccionada);
	    alumnoConvocatoria.setProductoAlumno(productoAlumno);
	    alumnoConvocatoria.setCuantiaExamen(productoExamen.getPrecio());
	    alumnoConvocatoria.setGradoActual(alumno.getGrado().getTipoGrado());
	    alumnoConvocatoria.setGradoSiguiente(gradoSiguiente);
	    alumnoConvocatoria.setPagado(false); // Por defecto, no pagado

	    // Guardar la relación AlumnoConvocatoria
	    alumnoConvocatoria = alumnoConvocatoriaRepository.save(alumnoConvocatoria);

	    // Añadir la relación a las listas correspondientes
	    if (convocatoriaSeleccionada.getAlumnosConvocatoria() == null) {
	        convocatoriaSeleccionada.setAlumnosConvocatoria(new ArrayList<>());
	    }
	    convocatoriaSeleccionada.getAlumnosConvocatoria().add(alumnoConvocatoria);
	    convocatoriaRepository.save(convocatoriaSeleccionada);

	    if (alumno.getConvocatorias() == null) {
	        alumno.setConvocatorias(new ArrayList<>());
	    }
	    alumno.getConvocatorias().add(alumnoConvocatoria);
	    alumnoRepository.save(alumno);

	    return convertirAAlumnoConvocatoriaDTO(alumnoConvocatoria);
	}
	
	@Override
	public void eliminarAlumnoDeConvocatoria(Long alumnoId, Long convocatoriaId) {
	    // Buscar el registro AlumnoConvocatoria
	    AlumnoConvocatoria alumnoConvocatoria = alumnoConvocatoriaRepository.findByConvocatoriaIdAndAlumnoId(convocatoriaId, alumnoId)
	        .orElseThrow(() -> new IllegalArgumentException("El alumno no está inscrito en esta convocatoria"));

	    // Eliminar la relación con ProductoAlumno antes de eliminar AlumnoConvocatoria
	    ProductoAlumno productoAlumno = alumnoConvocatoria.getProductoAlumno();
	    if (productoAlumno != null) {
	        alumnoConvocatoria.setProductoAlumno(null); // Desvincular antes de eliminar
	        alumnoConvocatoriaRepository.save(alumnoConvocatoria); // Actualizar para garantizar la integridad
	        productoAlumnoRepository.delete(productoAlumno);
	    }

	    // Finalmente, eliminar la relación AlumnoConvocatoria
	    alumnoConvocatoriaRepository.delete(alumnoConvocatoria);
	}
	
	private Producto obtenerProductoPorGrado(TipoGrado grado) {
	    String nombreProducto = obtenerNombreProductoPorGrado(grado);
	    return productoRepository.findByConcepto(nombreProducto)
	            .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado para el grado: " + grado));
	}

	private String obtenerNombreProductoPorGrado(TipoGrado grado) {
	    switch (grado) {
	        case BLANCO_AMARILLO:
	            return "DERECHOS DE EXAMEN BLANCO/AMARILLO";
	        case AMARILLO:
	            return "DERECHOS DE EXAMEN AMARILLO";
	        case AMARILLO_NARANJA:
	            return "DERECHOS DE EXAMEN AMARILLO/NARANJA";
	        case NARANJA:
	            return "DERECHOS DE EXAMEN NARANJA";
	        case NARANJA_VERDE:
	            return "DERECHOS DE EXAMEN NARANJA/VERDE";
	        case VERDE:
	            return "DERECHOS DE EXAMEN VERDE";
	        case VERDE_AZUL:
	            return "DERECHOS DE EXAMEN VERDE/AZUL";
	        case AZUL:
	            return "DERECHOS DE EXAMEN AZUL";
	        case AZUL_ROJO:
	            return "DERECHOS DE EXAMEN AZUL/ROJO";
	        case ROJO:
	            return "DERECHOS DE EXAMEN CINTURÓN ROJO BORDADO";
	        case NEGRO_1_DAN:
	            return "DERECHOS DE EXAMEN 1º DAN";
	        case ROJO_NEGRO_1_PUM:
	            return "DERECHOS DE EXAMEN 1º PUM";
	        case NEGRO_2_DAN:
	            return "DERECHOS DE EXAMEN 2º DAN";
	        case ROJO_NEGRO_2_PUM:
	            return "DERECHOS DE EXAMEN 2º PUM";
	        case NEGRO_3_DAN:
	            return "DERECHOS DE EXAMEN 3º DAN";
	        case ROJO_NEGRO_3_PUM:
	            return "DERECHOS DE EXAMEN 3º PUM";
	        case NEGRO_4_DAN:
	            return "DERECHOS DE EXAMEN 4º DAN";
	        case NEGRO_5_DAN:
	            return "DERECHOS DE EXAMEN 5º DAN";
	        default:
	            throw new IllegalArgumentException("Grado no soportado: " + grado);
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
	 * Calcula si un alumno es apto para examen según su grado y su edad.
	 * 
	 * @param alumno El alumno para el cual se realiza el cálculo.
	 * @return true si el alumno es apto para examen, false en caso contrario.
	 */
	private boolean esAptoParaExamen(Alumno alumno) {
		if (alumno.getGrado() == null || alumno.getFechaGrado() == null) {
			return false;
		}

		// Validar el año de la fecha de grado
		LocalDate fechaGrado = alumno.getFechaGrado().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		int year = fechaGrado.getYear();

		if (year < 1900 || year > 2100) {
			// Si la fecha de grado está fuera de un rango razonable
			return false;
		}

		// Calcular la edad del alumno
		int edad = calcularEdad(alumno.getFechaNacimiento());

		// Obtener los meses requeridos en función de la edad y el tipo de grado
		long mesesRequeridos = obtenerMesesRequeridosParaExamen(edad, alumno.getGrado().getTipoGrado());

		// Calcular la fecha mínima para el próximo examen
		LocalDate fechaExamenPosible = fechaGrado.plusMonths(mesesRequeridos);

		// Si la fecha actual es igual o mayor que la fecha mínima, es apto para examen
		return !LocalDate.now().isBefore(fechaExamenPosible);
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
			case ROJO_NEGRO_1_PUM:
				return 24;
			case ROJO_NEGRO_2_PUM:
				return 36;
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
			case NEGRO_1_DAN:
				return 24;
			case NEGRO_2_DAN:
				return 36;
			case NEGRO_3_DAN:
				return 48;
			case NEGRO_4_DAN:
				return 60;
			default:
				return Long.MAX_VALUE; // No apto si no coincide
			}
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

}