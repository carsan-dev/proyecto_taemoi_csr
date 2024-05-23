package com.taemoi.project.servicios.impl;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.taemoi.project.dtos.AlumnoDTO;
import com.taemoi.project.entidades.Alumno;
import com.taemoi.project.entidades.Categoria;
import com.taemoi.project.entidades.Grado;
import com.taemoi.project.entidades.Grupo;
import com.taemoi.project.entidades.Imagen;
import com.taemoi.project.entidades.TipoCategoria;
import com.taemoi.project.entidades.TipoGrado;
import com.taemoi.project.entidades.TipoTarifa;
import com.taemoi.project.repositorios.AlumnoRepository;
import com.taemoi.project.repositorios.CategoriaRepository;
import com.taemoi.project.repositorios.GradoRepository;
import com.taemoi.project.repositorios.GrupoRepository;
import com.taemoi.project.repositorios.ImagenRepository;
import com.taemoi.project.repositorios.UsuarioRepository;
import com.taemoi.project.servicios.AlumnoService;

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
	
	/**
     * Inyección del PasswordEncoder para codificar la contraseña del usuario creado.
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
     * @return Un objeto Optional que contiene el alumno si se encuentra, de lo contrario, un Optional vacío.
     */
	@Override
	public Optional<Alumno> obtenerAlumnoPorId(@NonNull Long id) {
		return alumnoRepository.findById(id);
	}
	
    /**
     * Obtiene un alumno DTO por su ID.
     *
     * @param id El ID del alumno a buscar.
     * @return Un objeto Optional que contiene el alumno DTO si se encuentra, de lo contrario, un Optional vacío.
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
     * @return Una página de objetos Alumno que cumplen con los criterios de búsqueda.
     * @throws IllegalArgumentException Si no se proporciona al menos un criterio de filtrado.
     */
	@Override
	public Page<Alumno> obtenerAlumnosFiltrados(String nombre, Long gradoId, Long categoriaId, Pageable pageable) {
	    if (nombre != null && gradoId != null && categoriaId != null) {
	        return alumnoRepository.findByNombreContainingIgnoreCaseAndGradoIdAndCategoriaId(nombre, gradoId, categoriaId, pageable);
	    } else if (nombre != null && gradoId != null) {
	        return alumnoRepository.findByNombreContainingIgnoreCaseAndGradoId(nombre, gradoId, pageable);
	    } else if (nombre != null && categoriaId != null) {
	        return alumnoRepository.findByNombreContainingIgnoreCaseAndCategoriaId(nombre, categoriaId, pageable);
	    } else if (gradoId != null && categoriaId != null) {
	        return alumnoRepository.findByGradoIdAndCategoriaId(gradoId, categoriaId, pageable);
	    } else if (nombre != null) {
	        return alumnoRepository.findByNombreContainingIgnoreCase(nombre, pageable);
	    } else if (gradoId != null) {
	        return alumnoRepository.findByGradoId(gradoId, pageable);
	    } else if (categoriaId != null) {
	        return alumnoRepository.findByCategoriaId(categoriaId, pageable);
	    } else {
	        throw new IllegalArgumentException("Debe proporcionar al menos un criterio de filtrado");
	    }
	}
	
    /**
     * Obtiene una lista de alumnos filtrados según los parámetros especificados.
     *
     * @param nombre      El nombre a filtrar.
     * @param gradoId     El ID del grado a filtrar.
     * @param categoriaId El ID de la categoría a filtrar.
     * @return Una lista de objetos Alumno que cumplen con los criterios de búsqueda.
     * @throws IllegalArgumentException Si no se proporciona al menos un criterio de filtrado.
     */
	@Override
	public List<Alumno> obtenerAlumnosFiltrados(String nombre, Long gradoId, Long categoriaId) {
	    if (nombre != null && gradoId != null && categoriaId != null) {
	        return alumnoRepository.findByNombreContainingIgnoreCaseAndGradoIdAndCategoriaId(nombre, gradoId, categoriaId);
	    } else if (nombre != null && gradoId != null) {
	        return alumnoRepository.findByNombreContainingIgnoreCaseAndGradoId(nombre, gradoId);
	    } else if (nombre != null && categoriaId != null) {
	        return alumnoRepository.findByNombreContainingIgnoreCaseAndCategoriaId(nombre, categoriaId);
	    } else if (gradoId != null && categoriaId != null) {
	        return alumnoRepository.findByGradoIdAndCategoriaId(gradoId, categoriaId);
	    } else if (nombre != null) {
	        return alumnoRepository.findByNombreContainingIgnoreCase(nombre);
	    } else if (gradoId != null) {
	        return alumnoRepository.findByGradoId(gradoId);
	    } else if (categoriaId != null) {
	        return alumnoRepository.findByCategoriaId(categoriaId);
	    } else {
	        throw new IllegalArgumentException("Debe proporcionar al menos un criterio de filtrado");
	    }
	}

    /**
     * Crea un nuevo alumno.
     *
     * @param alumno El objeto Alumno a crear.
     * @return El alumno creado.
     */
	@Override
	public Alumno crearAlumno(@NonNull Alumno alumno) {
		return alumnoRepository.save(alumno);
	}

	/**
	 * Actualiza un alumno existente.
	 *
	 * @param id El ID del alumno a actualizar.
	 * @param alumnoActualizado El objeto AlumnoDTO con los datos actualizados.
	 * @param nuevaFechaNacimiento La nueva fecha de nacimiento del alumno.
	 * @param imagen La nueva imagen del alumno, si se proporciona.
	 * @return El alumno actualizado.
	 * @throws RuntimeException Si no se encuentra el alumno con el ID especificado.
	 */
	@Override
	public Alumno actualizarAlumno(@NonNull Long id, AlumnoDTO alumnoActualizado, Date nuevaFechaNacimiento, Imagen imagen) {
	    Optional<Alumno> optionalAlumno = alumnoRepository.findById(id);
	    if (optionalAlumno.isPresent()) {
	        Alumno alumnoExistente = optionalAlumno.get();
	        alumnoExistente.setNombre(alumnoActualizado.getNombre());
	        alumnoExistente.setApellidos(alumnoActualizado.getApellidos());
	        alumnoExistente.setFechaNacimiento(nuevaFechaNacimiento);

	        int nuevaEdad = calcularEdad(nuevaFechaNacimiento);
	        Categoria nuevaCategoria = asignarCategoriaSegunEdad(nuevaEdad);
	        alumnoExistente.setCategoria(nuevaCategoria);

	        alumnoExistente.setNumeroExpediente(alumnoActualizado.getNumeroExpediente());
	        alumnoExistente.setNif(alumnoActualizado.getNif());
	        alumnoExistente.setDireccion(alumnoActualizado.getDireccion());
	        alumnoExistente.setEmail(alumnoActualizado.getEmail());
	        alumnoExistente.setTelefono(alumnoActualizado.getTelefono());
	        alumnoExistente.setTipoTarifa(alumnoActualizado.getTipoTarifa());
	        alumnoExistente.setFechaAlta(alumnoActualizado.getFechaAlta());
	        alumnoExistente.setFechaBaja(alumnoActualizado.getFechaBaja());

	        if ((imagen != null && alumnoExistente.getFotoAlumno() == null) || (imagen != null && alumnoExistente.getFotoAlumno() != null)) {
	            alumnoExistente.setFotoAlumno(imagen);
	        }
	        return alumnoRepository.save(alumnoExistente);
	    } else {
	        throw new RuntimeException("No se encontró el alumno con ID: " + id);
	    }
	}
	
	/**
	 * Elimina la imagen asociada a un alumno especificado por su ID.
	 * 
	 * @param id El ID del alumno cuya imagen se eliminará.
	 * @throws RuntimeException Si no se encuentra el alumno con el ID especificado o si el alumno no tiene una imagen asociada.
	 */
	@Override
    public void eliminarImagenAlumno(@NonNull Long id) {
        Optional<Alumno> optionalAlumno = alumnoRepository.findById(id);
        if (optionalAlumno.isPresent()) {
            Alumno alumno = optionalAlumno.get();
            Imagen imagen = alumno.getFotoAlumno();
            if (imagen != null) {
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
	@SuppressWarnings("null")
	@Override
	public boolean eliminarAlumno(@NonNull Long id) {
	    return alumnoRepository.findById(id).map(alumno -> {
	        for (Grupo grupo : grupoRepository.findAll()) {
	            if (grupo.getAlumnos().contains(alumno)) {
	                grupo.getAlumnos().remove(alumno);
	                grupoRepository.save(grupo);
	            }
	        }
	        
	        if (alumno.getUsuario() != null) {
	            usuarioRepository.delete(alumno.getUsuario());
	        }

	        if (alumno.getFotoAlumno() != null) {
	            imagenRepository.delete(alumno.getFotoAlumno());
	        }

	        alumnoRepository.delete(alumno);
	        return true;
	    }).orElse(false);
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
		if (edad >= 3 && edad <= 7) {
			tipoCategoria = TipoCategoria.PRETKD;
		} else if (edad >= 8 && edad <= 9) {
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
	    LocalDate fechaNacimiento = nuevoAlumnoDTO.getFechaNacimiento().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
	    int edad = Period.between(fechaNacimiento, LocalDate.now()).getYears();
	    boolean esMenorDeCatorce = edad < 14;

	    List<TipoGrado> gradosDisponibles = esMenorDeCatorce
	            ? Arrays.asList(TipoGrado.BLANCO, TipoGrado.BLANCO_AMARILLO, TipoGrado.AMARILLO, 
	                            TipoGrado.AMARILLO_NARANJA, TipoGrado.NARANJA, TipoGrado.NARANJA_VERDE, 
	                            TipoGrado.VERDE, TipoGrado.VERDE_AZUL, TipoGrado.AZUL, TipoGrado.AZUL_ROJO, 
	                            TipoGrado.ROJO, TipoGrado.ROJO_NEGRO)
	            : Arrays.asList(TipoGrado.BLANCO, TipoGrado.AMARILLO, TipoGrado.NARANJA, 
	                            TipoGrado.VERDE, TipoGrado.AZUL, TipoGrado.ROJO, TipoGrado.NEGRO);

	    TipoGrado tipoGradoAsignado = gradosDisponibles.get(new Random().nextInt(gradosDisponibles.size()));
	    
	    Grado gradoExistente = gradoRepository.findByTipoGrado(tipoGradoAsignado);
	    if (gradoExistente != null) {
	        return gradoExistente;
	    }

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
		if (alumnoDTO.getNumeroExpediente() == null || alumnoDTO.getNumeroExpediente() <= 0
				|| alumnoDTO.getNif() == null || alumnoDTO.getNif().isEmpty()) {
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
	 * Genera una contraseña codificada a partir del nombre y apellidos de un usuario.
	 *
	 * @param nombre El nombre del usuario.
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
				alumno.getFechaBaja(), categoriaNombre, gradoTipo, alumno.getFotoAlumno());
	}
}