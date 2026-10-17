package com.taemoi.project.services.impl;


import com.taemoi.project.services.CatalogoLegadoTaemoi;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.taemoi.project.acceso.GestionPorSede;
import com.taemoi.project.acceso.PredicadosAlcance;
import com.taemoi.project.dtos.response.AlumnoCortoDTO;
import com.taemoi.project.dtos.response.GrupoConAlumnosDTO;
import com.taemoi.project.dtos.response.GrupoResponseDTO;
import com.taemoi.project.dtos.response.TurnoCortoDTO;
import com.taemoi.project.dtos.response.TurnoDelAlumnoEnGrupoDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.EstadoPreinscripcion;
import com.taemoi.project.entities.Grupo;
import com.taemoi.project.entities.Turno;
import com.taemoi.project.exceptions.alumno.AlumnoNoEncontradoEnGrupoException;
import com.taemoi.project.exceptions.grupo.GrupoNoEncontradoException;
import com.taemoi.project.exceptions.turno.TurnoNoEncontradoException;
import com.taemoi.project.repositories.AlumnoDeporteRepository;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.GrupoRepository;
import com.taemoi.project.repositories.PreinscripcionRepository;
import com.taemoi.project.repositories.TurnoRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.SedeService;
import com.taemoi.project.utils.OrdenAlfabetico;

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

	@Autowired
	private AlumnoDeporteRepository alumnoDeporteRepository;

	/**
	 * Inyección del repositorio de turno.
	 */
	@Autowired
	private TurnoRepository turnoRepository;

	@Autowired
	private PreinscripcionRepository preinscripcionRepository;

	@Autowired
	private com.taemoi.project.services.CatalogoDisciplinasService catalogoDisciplinas;

	@Autowired
	private SedeService sedeService;

	/** Entrega 6: alcance y comprobaciones del responsable de sede (sin efecto para la gestión). */
	@Autowired
	private GestionPorSede gestionPorSede;

	private static final Set<EstadoPreinscripcion> ESTADOS_PREINSCRIPCION_ACTIVA =
			Set.of(EstadoPreinscripcion.PENDIENTE, EstadoPreinscripcion.EN_LISTA_ESPERA);

	/**
	 * Obtiene todos los grupos.
	 *
	 * @return Una lista de objetos GrupoConAlumnosDTO que representan todos los
	 *         grupos.
	 */
	@Override
	public List<GrupoConAlumnosDTO> obtenerTodosLosGrupos() {
		// Responsable de sede (entrega 6-1d): solo los grupos de sus sedes. Gestión: la consulta de siempre.
		List<Grupo> grupos = gestionPorSede.restriccion()
				.map(alcance -> grupoRepository.findAll(PredicadosAlcance.gruposDeSusSedes(alcance)))
				.orElseGet(grupoRepository::findAll);
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
	@Transactional
	public GrupoConAlumnosDTO crearGrupo(GrupoConAlumnosDTO grupoDTO) {
		Grupo grupo = convertirDTOAEntidad(grupoDTO);
		// D6-5: sede del cuerpo (de la escuela; del responsable, una de las suyas; activa, D6-17). Sin sede: la del
		// responsable si tiene una; para la gestión, la única sede activa (o ninguna, como hasta ahora).
		grupo.setSedeId(gestionPorSede.sedeDeAlta(grupoDTO.getSedeId(), true));
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
	@Transactional
	public GrupoConAlumnosDTO actualizarGrupo(@NonNull Long id, GrupoConAlumnosDTO grupoDTO) {
		Optional<Grupo> grupoOptional = grupoRepository.findById(id);
		if (grupoOptional.isPresent()) {
			Grupo grupo = grupoOptional.get();
			grupo.setNombre(grupoDTO.getNombre());
			grupo.setRangoEdadMin(grupoDTO.getRangoEdadMin());
			grupo.setRangoEdadMax(grupoDTO.getRangoEdadMax());
			// Sin sede en el cuerpo (o la misma), el grupo conserva la suya. Un cambio de sede se valida como un
			// alta: de la escuela, del responsable si lo es, y activa (D6-5, D6-17).
			if (grupoDTO.getSedeId() != null && !Objects.equals(grupoDTO.getSedeId(), grupo.getSedeId())) {
				grupo.setSedeId(gestionPorSede.sedeDeAlta(grupoDTO.getSedeId(), true));
			}
			asignarSedePorDefecto(grupo);
			grupo = grupoRepository.save(grupo);

			return convertirEntidadADTO(grupo);
		} else {
			return null;
		}
	}

	/**
	 * La API de grupos no pide sede (fase 3, entrega 1): un grupo sin sede recibe la única sede activa de la
	 * escuela (Taemoi: «Umbrete»). Con varias o ninguna, queda sin sede hasta que la API permita elegirla. Un
	 * grupo que ya tiene sede la conserva.
	 */
	private void asignarSedePorDefecto(Grupo grupo) {
		if (grupo.getSedeId() == null) {
			sedeService.sedePorDefecto().ifPresent(grupo::setSedeId);
		}
	}

	/**
	 * Elimina un grupo por su ID.
	 *
	 * @param id El ID del grupo a eliminar.
	 */
	@Override
	@Transactional
	public void eliminarGrupo(@NonNull Long id) {
		grupoRepository.findById(id).ifPresent(grupo -> {
			List<Long> turnoIds = grupo.getTurnos().stream().map(Turno::getId).sorted().toList();
			List<Turno> turnosBloqueados = turnoIds.isEmpty()
					? List.of()
					: turnoRepository.findAllByIdForUpdate(turnoIds);
			turnosBloqueados.forEach(this::validarSinPreinscripcionesActivas);
			for (Turno turno : turnosBloqueados) {
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

				// Guardar el grupo y el alumno para actualizar las relaciones en la base de
				// datos
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
	@Transactional
	public void agregarAlumnosAGrupo(@NonNull Long grupoId, @NonNull List<Long> alumnosIds) {
		// D6-6: el responsable solo añade alumnos que ya están en su alcance (403 si alguno no).
		gestionPorSede.exigirAlumnosEnAlcance(alumnosIds);
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
	public Map<String, Map<String, Long>> contarAlumnosPorGrupo() {
		Map<String, Map<String, Long>> conteoAlumnosPorGrupo = new HashMap<>();

		List<String> tipos = Arrays.asList("Taekwondo", "Taekwondo Competición", "Pilates", "Kickboxing",
				"Defensa Personal Femenina");

		for (String tipo : tipos) {
			String tipoUpper = tipo.toUpperCase();
			boolean esCompeticion = tipoUpper.contains("COMPET");
			String deporte;

			if (tipoUpper.contains("TAEKWONDO")) {
				deporte = CatalogoLegadoTaemoi.TAEKWONDO;
			} else if (tipoUpper.contains("KICKBOXING")) {
				deporte = CatalogoLegadoTaemoi.KICKBOXING;
			} else if (tipoUpper.contains("PILATES")) {
				deporte = CatalogoLegadoTaemoi.PILATES;
			} else if (tipoUpper.contains("DEFENSA")) {
				deporte = CatalogoLegadoTaemoi.DEFENSA_PERSONAL_FEMENINA;
			} else {
				continue;
			}

			long totalCount;
			long activeCount;

			if (esCompeticion) {
				totalCount = alumnoDeporteRepository.countCompetidoresByDeporte(deporte);
				activeCount = alumnoDeporteRepository.countCompetidoresActivosByDeporte(deporte);
			} else {
				totalCount = alumnoDeporteRepository.countByDeporteAndActivoTrue(deporte);
				activeCount = alumnoDeporteRepository.countActivosByDeporte(deporte);
			}

			Map<String, Long> counts = new HashMap<>();
			counts.put("active", activeCount);
			counts.put("total", totalCount);

			conteoAlumnosPorGrupo.put(tipo, counts);
		}

		return conteoAlumnosPorGrupo;
	}

	@Override
	public List<AlumnoCortoDTO> obtenerAlumnosPorTipo(String tipo) {
		String tipoUpper = tipo.toUpperCase();
		boolean esCompeticion = tipoUpper.contains("COMPET");
		String deporte;
		List<AlumnoDeporte> alumnosDeporte;

		if (tipoUpper.contains("TAEKWONDO")) {
			deporte = CatalogoLegadoTaemoi.TAEKWONDO;
		} else if (tipoUpper.contains("KICKBOXING")) {
			deporte = CatalogoLegadoTaemoi.KICKBOXING;
		} else if (tipoUpper.contains("PILATES")) {
			deporte = CatalogoLegadoTaemoi.PILATES;
		} else if (tipoUpper.contains("DEFENSA")) {
			deporte = CatalogoLegadoTaemoi.DEFENSA_PERSONAL_FEMENINA;
		} else {
			throw new GrupoNoEncontradoException("Deporte no válido: " + tipo);
		}

		if (esCompeticion) {
			alumnosDeporte = alumnoDeporteRepository.findCompetidoresByDeporteWithAlumno(deporte);
		} else {
			alumnosDeporte = alumnoDeporteRepository.findActivosByDeporteWithAlumno(deporte);
		}

		return alumnosDeporte.stream()
			.filter(ad -> ad.getAlumno() != null)
			.map(AlumnoCortoDTO::deAlumnoDeporte)
			.collect(Collectors.toList());
	}

	@Override
	@Transactional
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
				grupo.removeAlumno(alumno); // Remover el alumno del grupo
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
	@Transactional
	public void agregarTurnoAGrupo(@NonNull Long grupoId, @NonNull Long turnoId) {
		Optional<Grupo> grupoOptional = grupoRepository.findById(grupoId);
		Optional<Turno> turnoOptional = turnoRepository.findByIdForUpdate(turnoId);

		if (grupoOptional.isPresent() && turnoOptional.isPresent()) {
			Grupo grupo = grupoOptional.get();
			Turno turno = turnoOptional.get();
			if (turno.getGrupo() != null && !Objects.equals(turno.getGrupo().getId(), grupoId)) {
				validarSinPreinscripcionesActivas(turno);
			}
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
	@Transactional
	public void eliminarTurnoDeGrupo(@NonNull Long grupoId, @NonNull Long turnoId) {
		Optional<Grupo> grupoOptional = grupoRepository.findById(grupoId);

		if (grupoOptional.isPresent()) {
			Grupo grupo = grupoOptional.get();
			Optional<Turno> turnoOptional = turnoRepository.findByIdForUpdate(turnoId)
					.filter(turno -> turno.getGrupo() != null && Objects.equals(turno.getGrupo().getId(), grupoId));

			if (turnoOptional.isPresent()) {
				Turno turno = turnoOptional.get();
				validarSinPreinscripcionesActivas(turno);
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
			return grupo.getTurnos().stream().map(TurnoCortoDTO::deTurno).collect(Collectors.toList());
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
			// D2 (R2-P1): por nombre de grupo (Alumno.grupos es un Set sin orden).
			return alumno.getGrupos().stream().map(this::convertirGrupoADTO)
					.sorted(OrdenAlfabetico.porNombre(GrupoResponseDTO::getNombre, GrupoResponseDTO::getId))
					.collect(Collectors.toList());
		}
		return Collections.emptyList();
	}

	@Override
	public List<TurnoDelAlumnoEnGrupoDTO> obtenerTurnosDelAlumnoEnGrupo(Long grupoId, Long alumnoId) {
		Optional<Grupo> grupoOptional = grupoRepository.findById(grupoId);
		Optional<Alumno> alumnoOptional = alumnoRepository.findById(alumnoId);

		if (grupoOptional.isPresent() && alumnoOptional.isPresent()) {
			Alumno alumno = alumnoOptional.get();
			// Filtrar los turnos del grupo para los que el alumno está inscrito
			List<Turno> turnosAlumno = alumno.getTurnos().stream()
					.filter(turno -> turno.getGrupo().getId().equals(grupoId)).collect(Collectors.toList());

			// Convertir los turnos a DTO, sin los alumnos del turno (la familia del alumno también usa este endpoint)
			return turnosAlumno.stream().map(TurnoDelAlumnoEnGrupoDTO::deTurno).collect(Collectors.toList());
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
		grupoDTO.setDeporte(grupo.getDeporte() != null ? grupo.getDeporte() : null);
		grupoDTO.setRangoEdadMin(grupo.getRangoEdadMin());
		grupoDTO.setRangoEdadMax(grupo.getRangoEdadMax());
		grupoDTO.setSedeId(grupo.getSedeId());

		String deporteGrupo = grupo.getDeporte();
		List<AlumnoCortoDTO> alumnosDTO = grupo.getAlumnos().stream()
				.filter(alumno -> Boolean.TRUE.equals(alumno.getActivo()))
				.map(alumno -> {
					if (deporteGrupo != null && alumno.getDeportes() != null && !alumno.getDeportes().isEmpty()) {
						AlumnoDeporte alumnoDeporte = alumno.getDeportes().stream()
								.filter(ad -> java.util.Objects.equals(ad.getDeporte(), deporteGrupo) && Boolean.TRUE.equals(ad.getActivo()))
								.findFirst()
								.orElse(null);
						if (alumnoDeporte != null) {
							return AlumnoCortoDTO.deAlumnoDeporte(alumnoDeporte);
						}
						return null;
					}
					return AlumnoCortoDTO.deAlumno(alumno);
				})
				.filter(Objects::nonNull)
				// D2 (R2-P1): por nombre y apellidos (Grupo.alumnos es un Set sin orden).
				.sorted(OrdenAlfabetico.porNombreYApellidos(AlumnoCortoDTO::getNombre, AlumnoCortoDTO::getApellidos,
						AlumnoCortoDTO::getId))
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
		grupo.setRangoEdadMin(grupoDTO.getRangoEdadMin());
		grupo.setRangoEdadMax(grupoDTO.getRangoEdadMax());

		// Set deporte from DTO
		if (grupoDTO.getDeporte() != null && !grupoDTO.getDeporte().isEmpty()) {
			try {
				grupo.setDisciplina(catalogoDisciplinas.obtenerPorCodigo(
						catalogoDisciplinas.validarCodigo(grupoDTO.getDeporte())));
			} catch (IllegalArgumentException e) {
				// If invalid deporte, leave it null
			}
		}

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
		grupoDTO.setDeporte(grupo.getDeporte() != null ? grupo.getDeporte() : null);
		grupoDTO.setRangoEdadMin(grupo.getRangoEdadMin());
		grupoDTO.setRangoEdadMax(grupo.getRangoEdadMax());
		grupoDTO.setSedeId(grupo.getSedeId());
		return grupoDTO;
	}

	private void validarSinPreinscripcionesActivas(Turno turno) {
		if (preinscripcionRepository.countActivasByTurnoId(turno.getId(), ESTADOS_PREINSCRIPCION_ACTIVA) > 0) {
			throw new IllegalStateException(
					"No se puede modificar el turno porque existen preinscripciones activas asociadas.");
		}
	}
}
