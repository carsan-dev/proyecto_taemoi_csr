package com.taemoi.project.servicios.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.taemoi.project.dtos.response.AlumnoCortoDTO;
import com.taemoi.project.dtos.response.GrupoConAlumnosDTO;
import com.taemoi.project.dtos.response.GrupoResponseDTO;
import com.taemoi.project.dtos.response.TurnoCortoDTO;
import com.taemoi.project.entidades.Alumno;
import com.taemoi.project.entidades.Grupo;
import com.taemoi.project.entidades.NombresGrupo;
import com.taemoi.project.entidades.Turno;
import com.taemoi.project.errores.alumno.AlumnoNoEncontradoEnGrupoException;
import com.taemoi.project.errores.grupo.GrupoNoEncontradoException;
import com.taemoi.project.errores.turno.TurnoNoEncontradoException;
import com.taemoi.project.repositorios.AlumnoRepository;
import com.taemoi.project.repositorios.GrupoRepository;
import com.taemoi.project.repositorios.TurnoRepository;
import com.taemoi.project.servicios.GrupoService;

import jakarta.transaction.Transactional;

/**
 * Implementación del servicio para operaciones relacionadas con los grupos de
 * alumnos.
 */
@Service
public class GrupoServiceImpl implements GrupoService {

	/**
	 * Inyección del repositorio de grupo.
	 */
	@Autowired
	private GrupoRepository grupoRepository;

	/**
	 * Inyección del repositorio de alumno.
	 */
	@Autowired
	private AlumnoRepository alumnoRepository;

	/**
	 * Inyección del repositorio de turno.
	 */
	@Autowired
	private TurnoRepository turnoRepository;

	/**
	 * Obtiene todos los grupos.
	 *
	 * @return Una lista de objetos GrupoConAlumnosDTO que representan todos los
	 *         grupos.
	 */
	@Override
	public List<GrupoConAlumnosDTO> obtenerTodosLosGrupos() {
		List<Grupo> grupos = grupoRepository.findAll();
		return grupos.stream().map(this::convertirEntidadADTO).collect(Collectors.toList());
	}

	/**
	 * Obtiene un grupo con sus alumnos por su ID.
	 *
	 * @param id El ID del grupo a obtener.
	 * @return Un Optional que contiene el objeto GrupoConAlumnosDTO si se encuentra
	 *         el grupo; de lo contrario, un Optional vacío.
	 */
	@Override
	public Optional<GrupoConAlumnosDTO> obtenerGrupoConAlumnosPorId(@NonNull Long id) {
		Optional<Grupo> grupoOptional = grupoRepository.findById(id);
		return grupoOptional.map(this::convertirEntidadADTO);
	}

	/**
	 * Crea un nuevo grupo.
	 *
	 * @param grupoDTO El objeto GrupoConAlumnosDTO con los datos del nuevo grupo.
	 * @return El objeto GrupoConAlumnosDTO que representa el grupo creado.
	 */
	@Override
	public GrupoConAlumnosDTO crearGrupo(GrupoConAlumnosDTO grupoDTO) {
		Grupo grupo = convertirDTOAEntidad(grupoDTO);
		grupo = grupoRepository.save(grupo);
		return convertirEntidadADTO(grupo);
	}

	/**
	 * Actualiza un grupo existente.
	 *
	 * @param id       El ID del grupo a actualizar.
	 * @param grupoDTO El objeto GrupoConAlumnosDTO con los datos actualizados del
	 *                 grupo.
	 * @return El objeto GrupoConAlumnosDTO que representa el grupo actualizado, o
	 *         null si no se encuentra el grupo.
	 */
	@Override
	public GrupoConAlumnosDTO actualizarGrupo(@NonNull Long id, GrupoConAlumnosDTO grupoDTO) {
		Optional<Grupo> grupoOptional = grupoRepository.findById(id);
		if (grupoOptional.isPresent()) {
			Grupo grupo = grupoOptional.get();
			grupo.setNombre(grupoDTO.getNombre());
			grupo = grupoRepository.save(grupo);

			return convertirEntidadADTO(grupo);
		} else {
			return null;
		}
	}

	/**
	 * Elimina un grupo por su ID.
	 *
	 * @param id El ID del grupo a eliminar.
	 */
	@Override
	public void eliminarGrupo(@NonNull Long id) {
		grupoRepository.findById(id).ifPresent(grupo -> {
			for (Turno turno : grupo.getTurnos()) {
				turno.setGrupo(null);
				turnoRepository.delete(turno);
			}

			grupoRepository.delete(grupo);
		});
	}

	/**
	 * Agrega un alumno a un grupo.
	 *
	 * @param grupoId  El ID del grupo.
	 * @param alumnoId El ID del alumno.
	 * @throws GrupoNoEncontradoException Si el grupo o el alumno no existen.
	 */
	@Transactional
	@Override
	public void agregarAlumnoAGrupo(@NonNull Long grupoId, @NonNull Long alumnoId) {
	    // Buscar el grupo por su ID
	    Optional<Grupo> grupoOptional = grupoRepository.findById(grupoId);
	    // Buscar el alumno por su ID
	    Optional<Alumno> alumnoOptional = alumnoRepository.findById(alumnoId);

	    if (grupoOptional.isPresent() && alumnoOptional.isPresent()) {
	        Grupo grupo = grupoOptional.get();
	        Alumno alumno = alumnoOptional.get();

	        // Verificar si el alumno ya está en el grupo
	        if (!grupo.getAlumnos().contains(alumno)) {
	            // Añadir el alumno al grupo
	            grupo.addAlumno(alumno);

	            // Obtener los turnos del grupo y añadir el alumno a esos turnos
	            List<Turno> turnosDelGrupo = grupo.getTurnos();
	            for (Turno turno : turnosDelGrupo) {
	                if (!turno.getAlumnos().contains(alumno)) {
	                    // Añadir el alumno a cada turno del grupo
	                    turno.getAlumnos().add(alumno);
	                    alumno.getTurnos().add(turno);
	                }
	            }

	            // Guardar el grupo y el alumno para actualizar las relaciones en la base de datos
	            grupoRepository.save(grupo);
	            alumnoRepository.save(alumno);
	        } else {
	            throw new IllegalArgumentException("El alumno ya pertenece a este grupo");
	        }
	    } else {
	        throw new GrupoNoEncontradoException("El grupo o el alumno no existen");
	    }
	}

	@Override
	public void agregarAlumnosAGrupo(@NonNull Long grupoId, @NonNull List<Long> alumnosIds) {
	    Optional<Grupo> grupoOptional = grupoRepository.findById(grupoId);

	    if (grupoOptional.isPresent()) {
	        Grupo grupo = grupoOptional.get();
	        List<Alumno> alumnos = alumnoRepository.findAllById(alumnosIds);

	        for (Alumno alumno : alumnos) {
	            // Añadir alumno al grupo si no está ya en el grupo
	            if (!grupo.getAlumnos().contains(alumno)) {
	                grupo.addAlumno(alumno);

	                // Inscribir el alumno en todos los turnos del grupo
	                for (Turno turno : grupo.getTurnos()) {
	                    if (!turno.getAlumnos().contains(alumno)) {
	                        turno.getAlumnos().add(alumno);
	                        alumno.getTurnos().add(turno);
	                    }
	                }
	            }
	        }

	        // Guardar el grupo y los alumnos con las nuevas relaciones
	        grupoRepository.save(grupo);
	        alumnoRepository.saveAll(alumnos);
	    } else {
	        throw new GrupoNoEncontradoException("El grupo con ID " + grupoId + " no existe.");
	    }
	}
	
	@Override
	public Map<String, Long> contarAlumnosPorGrupo() {
	    Map<String, Long> conteoAlumnosPorGrupo = new HashMap<>();

	    // Obtener el número de alumnos en cada grupo
	    conteoAlumnosPorGrupo.put("Taekwondo", alumnoRepository.contarAlumnosPorGrupo(NombresGrupo.TAEKWONDO_LUNES_MIERCOLES_PRIMER_TURNO)
	        + alumnoRepository.contarAlumnosPorGrupo(NombresGrupo.TAEKWONDO_MARTES_JUEVES_PRIMER_TURNO)
	        + alumnoRepository.contarAlumnosPorGrupo(NombresGrupo.TAEKWONDO_LUNES_MIERCOLES_SEGUNDO_TURNO)
	        + alumnoRepository.contarAlumnosPorGrupo(NombresGrupo.TAEKWONDO_MARTES_JUEVES_SEGUNDO_TURNO)
	        + alumnoRepository.contarAlumnosPorGrupo(NombresGrupo.TAEKWONDO_LUNES_MIERCOLES_TERCER_TURNO)
	        + alumnoRepository.contarAlumnosPorGrupo(NombresGrupo.TAEKWONDO_MARTES_JUEVES_TERCER_TURNO));

	    conteoAlumnosPorGrupo.put("Taekwondo Competición", alumnoRepository.contarAlumnosPorGrupo(NombresGrupo.TAEKWONDO_COMPETICION));
	    conteoAlumnosPorGrupo.put("Pilates", alumnoRepository.contarAlumnosPorGrupo(NombresGrupo.PILATES_MARTES_JUEVES));
	    conteoAlumnosPorGrupo.put("Kickboxing", alumnoRepository.contarAlumnosPorGrupo(NombresGrupo.KICKBOXING_LUNES_MIERCOLES));

	    return conteoAlumnosPorGrupo;
	}

	/**
	 * Elimina un alumno de un grupo.
	 *
	 * @param grupoId  El ID del grupo.
	 * @param alumnoId El ID del alumno.
	 * @throws GrupoNoEncontradoException Si el grupo o el alumno no existen.
	 */
	@Override
	public void eliminarAlumnoDeGrupo(@NonNull Long grupoId, @NonNull Long alumnoId) {
	    Optional<Grupo> grupoOptional = grupoRepository.findById(grupoId);
	    Optional<Alumno> alumnoOptional = alumnoRepository.findById(alumnoId);

	    if (grupoOptional.isPresent() && alumnoOptional.isPresent()) {
	        Grupo grupo = grupoOptional.get();
	        Alumno alumno = alumnoOptional.get();

	        // Verificar si el alumno está en el grupo
	        if (grupo.getAlumnos().contains(alumno)) {
	            // Eliminar las relaciones del alumno con los turnos del grupo
	            List<Turno> turnosDelGrupo = grupo.getTurnos();

	            for (Turno turno : turnosDelGrupo) {
	                if (alumno.getTurnos().contains(turno)) {
	                    alumno.getTurnos().remove(turno); // Eliminar relación en la tabla alumno_turno
	                    turno.getAlumnos().remove(alumno); // Eliminar relación en la entidad turno
	                }
	            }

	            // Actualizar la relación del alumno con el grupo
	            grupo.removeAlumno(alumno);  // Remover el alumno del grupo
	            grupoRepository.save(grupo); // Guardar el grupo actualizado
	            alumnoRepository.save(alumno); // Guardar el alumno actualizado
	        } else {
	            throw new AlumnoNoEncontradoEnGrupoException("El alumno no pertenece al grupo.");
	        }
	    } else {
	        throw new GrupoNoEncontradoException("El grupo o el alumno no existen.");
	    }
	}


	/**
	 * Agrega un turno a un grupo.
	 *
	 * @param grupoId El ID del grupo.
	 * @param turnoId El ID del turno.
	 * @throws GrupoNoEncontradoException Si el grupo o el turno no existen.
	 */
	@Override
	public void agregarTurnoAGrupo(@NonNull Long grupoId, @NonNull Long turnoId) {
		Optional<Grupo> grupoOptional = grupoRepository.findById(grupoId);
		Optional<Turno> turnoOptional = turnoRepository.findById(turnoId);

		if (grupoOptional.isPresent() && turnoOptional.isPresent()) {
			Grupo grupo = grupoOptional.get();
			Turno turno = turnoOptional.get();
			grupo.getTurnos().add(turno);
			turno.setGrupo(grupo);
			grupoRepository.save(grupo);
		} else {
			throw new GrupoNoEncontradoException(
					"El grupo con ID " + grupoId + " o el turno con ID " + turnoId + " no existe.");
		}
	}

	/**
	 * Elimina un turno de un grupo.
	 *
	 * @param grupoId El ID del grupo.
	 * @param turnoId El ID del turno.
	 * @throws GrupoNoEncontradoException Si el grupo no existe.
	 * @throws TurnoNoEncontradoException Si el turno no está asignado al grupo.
	 */
	@Override
	public void eliminarTurnoDeGrupo(@NonNull Long grupoId, @NonNull Long turnoId) {
		Optional<Grupo> grupoOptional = grupoRepository.findById(grupoId);

		if (grupoOptional.isPresent()) {
			Grupo grupo = grupoOptional.get();
			Optional<Turno> turnoOptional = grupo.getTurnos().stream().filter(turno -> turno.getId().equals(turnoId))
					.findFirst();

			if (turnoOptional.isPresent()) {
				Turno turno = turnoOptional.get();
				grupo.getTurnos().remove(turno);
				turno.setGrupo(null);
				grupoRepository.save(grupo);
			} else {
				throw new TurnoNoEncontradoException(
						"El turno con ID " + turnoId + " no está asignado al grupo con ID " + grupoId);
			}
		} else {
			throw new GrupoNoEncontradoException("El grupo con ID " + grupoId + " no existe.");
		}
	}

	/**
	 * Obtiene los turnos asignados a un grupo.
	 *
	 * @param grupoId El ID del grupo.
	 * @return Una lista de objetos TurnoDTO que representan los turnos del grupo.
	 */
	@Override
	public List<TurnoCortoDTO> obtenerTurnosDelGrupo(@NonNull Long grupoId) {
	    Optional<Grupo> grupoOptional = grupoRepository.findById(grupoId);
	    if (grupoOptional.isPresent()) {
	        Grupo grupo = grupoOptional.get();
	        // Convertir los turnos del grupo a TurnoCortoDTO
	        return grupo.getTurnos().stream()
	                .map(TurnoCortoDTO::deTurno)
	                .collect(Collectors.toList());
	    }
	    return Collections.emptyList();
	}

	/**
	 * Obtiene los grupos a los que pertenece un alumno.
	 *
	 * @param alumnoId El ID del alumno.
	 * @return Una lista de objetos GrupoResponseDTO que representan los grupos del
	 *         alumno.
	 */
	@Override
	public List<GrupoResponseDTO> obtenerGruposDelAlumno(@NonNull Long alumnoId) {
		Alumno alumno = alumnoRepository.findById(alumnoId).orElse(null);
		if (alumno != null) {
			return alumno.getGrupos().stream().map(this::convertirGrupoADTO).collect(Collectors.toList());
		}
		return Collections.emptyList();
	}
	
	@Override
	public List<TurnoCortoDTO> obtenerTurnosDelAlumnoEnGrupo(Long grupoId, Long alumnoId) {
	    Optional<Grupo> grupoOptional = grupoRepository.findById(grupoId);
	    Optional<Alumno> alumnoOptional = alumnoRepository.findById(alumnoId);

	    if (grupoOptional.isPresent() && alumnoOptional.isPresent()) {
	        Alumno alumno = alumnoOptional.get();
	        // Filtrar los turnos del grupo para los que el alumno está inscrito
	        List<Turno> turnosAlumno = alumno.getTurnos().stream()
	            .filter(turno -> turno.getGrupo().getId().equals(grupoId))
	            .collect(Collectors.toList());

	        // Convertir los turnos a DTO
	        return turnosAlumno.stream()
	            .map(TurnoCortoDTO::deTurno)
	            .collect(Collectors.toList());
	    }
	    return Collections.emptyList();
	}

	/**
	 * Convierte una entidad Grupo a un DTO GrupoConAlumnosDTO.
	 *
	 * @param grupo La entidad Grupo a convertir.
	 * @return El objeto GrupoConAlumnosDTO que representa la entidad Grupo.
	 */
	@Override
	public GrupoConAlumnosDTO convertirEntidadADTO(Grupo grupo) {
		GrupoConAlumnosDTO grupoDTO = new GrupoConAlumnosDTO();
		grupoDTO.setId(grupo.getId());
		grupoDTO.setNombre(grupo.getNombre());

		List<AlumnoCortoDTO> alumnosDTO = grupo.getAlumnos().stream().map(AlumnoCortoDTO::deAlumno)
				.collect(Collectors.toList());
		grupoDTO.setAlumnos(alumnosDTO);

		return grupoDTO;
	}

	/**
	 * Convierte un DTO GrupoConAlumnosDTO a una entidad Grupo.
	 *
	 * @param grupoDTO El objeto GrupoConAlumnosDTO a convertir.
	 * @return La entidad Grupo que representa el objeto GrupoConAlumnosDTO.
	 */
	@Override
	public Grupo convertirDTOAEntidad(GrupoConAlumnosDTO grupoDTO) {
		Grupo grupo = new Grupo();
		grupo.setId(grupoDTO.getId());
		grupo.setNombre(grupoDTO.getNombre());

		List<Alumno> alumnos = new ArrayList<>();
		if (grupoDTO.getAlumnos() != null) {
			alumnos = grupoDTO.getAlumnos().stream().map(alumnoGrupoDTO -> {
				Alumno alumno = new Alumno();
				alumno.setNombre(alumnoGrupoDTO.getNombre());
				alumno.setApellidos(alumnoGrupoDTO.getApellidos());
				alumno.setFotoAlumno(alumnoGrupoDTO.getFotoAlumno());
				return alumno;
			}).collect(Collectors.toList());
		}
		grupo.setAlumnos(alumnos);

		return grupo;
	}

	/**
	 * Convierte una entidad Grupo a un DTO GrupoResponseDTO.
	 *
	 * @param grupo La entidad Grupo a convertir.
	 * @return El objeto GrupoResponseDTO que representa la entidad Grupo.
	 */
	private GrupoResponseDTO convertirGrupoADTO(Grupo grupo) {
		GrupoResponseDTO grupoDTO = new GrupoResponseDTO();
		grupoDTO.setId(grupo.getId());
		grupoDTO.setNombre(grupo.getNombre());
		return grupoDTO;
	}
}
