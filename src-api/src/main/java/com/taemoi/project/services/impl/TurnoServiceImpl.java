package com.taemoi.project.services.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.taemoi.project.dtos.TurnoDTO;
import com.taemoi.project.config.ValidacionRelojConfig;
import com.taemoi.project.dtos.response.TurnoPublicoDTO;
import com.taemoi.project.entities.Grupo;
import com.taemoi.project.entities.EstadoPreinscripcion;
import com.taemoi.project.entities.Turno;
import com.taemoi.project.exceptions.turno.TurnoNoEncontradoException;
import com.taemoi.project.repositories.GrupoRepository;
import com.taemoi.project.repositories.PreinscripcionRepository;
import com.taemoi.project.repositories.TurnoRepository;
import com.taemoi.project.services.TurnoService;
import com.taemoi.project.services.AforoPreinscripcionService;
import com.taemoi.project.services.TemporadaService;

import jakarta.transaction.Transactional;

/**
 * Implementación del servicio de turno que proporciona operaciones relacionadas
 * con turnos para los grupos.
 */
@Service
public class TurnoServiceImpl implements TurnoService {

	/**
	 * Inyección del repositorio de turno.
	 */
	@Autowired
	private TurnoRepository turnoRepository;

	/**
	 * Inyección del repositorio de grupo.
	 */
	@Autowired
	private GrupoRepository grupoRepository;
	@Autowired private AforoPreinscripcionService aforoPreinscripcionService;
	@Autowired private TemporadaService temporadaService;
	@Autowired private PreinscripcionRepository preinscripcionRepository;
	/** Entrega 6: alcance y comprobaciones del responsable de sede (sin efecto para la gestión). */
	@Autowired private com.taemoi.project.acceso.GestionPorSede gestionPorSede;
	@Autowired private com.taemoi.project.repositories.SedeRepository sedeRepository;
	/**
	 * Reloj de la fecha de referencia de las edades de /horarios: Madrid (el visitante está en España; antes la
	 * calculaba su navegador con su fecha local). Fijo en zona, no en la de la JVM (que suele ir en UTC).
	 */
	private Clock reloj = Clock.system(ValidacionRelojConfig.ZONA);
	private static final Set<EstadoPreinscripcion> ESTADOS_PREINSCRIPCION_ACTIVA =
			Set.of(EstadoPreinscripcion.PENDIENTE, EstadoPreinscripcion.EN_LISTA_ESPERA);

	/**
	 * Obtiene una lista de todos los turnos disponibles.
	 *
	 * @return Una lista de objetos Turno.
	 */
	@Override
	public List<Turno> listarTurnos() {
		return turnoRepository.findAll();
	}

	/**
	 * Obtiene un turno por su ID.
	 *
	 * @param turnoId El ID del turno a obtener.
	 * @return El objeto Turno correspondiente al ID especificado.
	 * @throws TurnoNoEncontradoException Si no se encuentra el turno con el ID
	 *                                    especificado.
	 */
	@Override
	public Turno obtenerTurnoPorId(@NonNull Long turnoId) {
		return turnoRepository.findById(turnoId)
				.orElseThrow(() -> new TurnoNoEncontradoException("El turno con ID " + turnoId + " no existe."));
	}

	/**
	 * Obtiene una lista de todos los turnos disponibles en forma de objetos DTO.
	 *
	 * @return Una lista de objetos TurnoDTO.
	 */
	@Override
	public List<TurnoPublicoDTO> listarTurnosDTO() {
		List<Turno> turnos = turnoRepository.findAllWithAlumnos();
		String temporada = temporadaService.actual();
		// Fecha de Madrid sea cual sea la zona del reloj (la CI corre en UTC).
		LocalDate hoy = LocalDate.ofInstant(reloj.instant(), ValidacionRelojConfig.ZONA);
		return turnos.stream().map(turno -> {
			TurnoPublicoDTO dto = TurnoPublicoDTO.deTurno(turno, hoy);
			if (turno.getGrupo() != null) {
				dto.setOcupacionEfectiva(aforoPreinscripcionService.ocupacionEfectiva(turno, turno.getGrupo(), temporada));
				dto.setCompleto(aforoPreinscripcionService.completo(turno, temporada));
			}
			return dto;
		}).collect(Collectors.toList());
	}

	/** Solo para tests: fija la fecha de referencia de las edades. */
	void setReloj(Clock reloj) {
		this.reloj = reloj;
	}

	@Override
	public List<TurnoDTO> listarTurnosDTOCompleto() {
		// Responsable de sede (entrega 6-1d): turnos de grupos de sus sedes. Gestión: la consulta de siempre.
		List<Turno> turnos = gestionPorSede.restriccion()
				.map(alcance -> turnoRepository.findAll(
						com.taemoi.project.acceso.PredicadosAlcance.turnosDeSusSedes(alcance)))
				.orElseGet(turnoRepository::findAll);
		return turnos.stream().map(TurnoDTO::deTurno) // Utiliza el método actualizado que incluye alumnos
				.collect(Collectors.toList());
	}

	/**
	 * Crea un nuevo turno sin asignarlo a ningún grupo.
	 *
	 * @param turnoDTO Los datos del turno a crear.
	 */
	@Override
	public void crearTurnoSinGrupo(TurnoDTO turnoDTO) {
		Turno turno = new Turno();
		turno.setDiaSemana(turnoDTO.getDiaSemana());
		turno.setHoraInicio(turnoDTO.getHoraInicio());
		turno.setHoraFin(turnoDTO.getHoraFin());

		turnoRepository.save(turno);
	}

	/**
	 * Crea un nuevo turno y lo asigna a un grupo según el día de la semana.
	 *
	 * @param turnoDTO Los datos del turno a crear.
	 */
	@Override
	public void crearTurnoYAsignarAGrupo(TurnoDTO turnoDTO) {
		// D6-5: el responsable solo crea turnos en grupos de sus sedes (403).
		gestionPorSede.exigirGrupoPropio(turnoDTO.getGrupoId());
		Turno turno = new Turno();
		turno.setDiaSemana(turnoDTO.getDiaSemana());
		turno.setHoraInicio(turnoDTO.getHoraInicio());
		turno.setHoraFin(turnoDTO.getHoraFin());

		// Obtener el grupo por ID
		Grupo grupo = grupoRepository.findById(turnoDTO.getGrupoId()).orElseThrow(
				() -> new IllegalArgumentException("No se encontró el grupo con el ID: " + turnoDTO.getGrupoId()));

		// D6-17: en una sede inactiva no se crean turnos (400).
		gestionPorSede.exigirSedeActiva(grupo.getSedeId());

		// Usar el tipo de grupo directamente del frontend
		turno.setTipo(turnoDTO.getTipoGrupo()); // <- Aquí usamos el tipo de grupo del DTO

		// Asignar el grupo al turno
		turno.setGrupo(grupo);
		grupo.getTurnos().add(turno);

		// Guardar ambos objetos
		turnoRepository.save(turno);
		grupoRepository.save(grupo);
	}

	/**
	 * Actualiza los detalles de un turno existente.
	 *
	 * @param turnoId  El ID del turno a actualizar.
	 * @param turnoDTO Los nuevos datos del turno.
	 * @return El objeto TurnoDTO actualizado.
	 * @throws TurnoNoEncontradoException Si no se encuentra el turno con el ID
	 *                                    especificado.
	 */
	@Override
	@Transactional
	public TurnoDTO actualizarTurno(@NonNull Long turnoId, TurnoDTO turnoDTO) {
		Optional<Turno> turnoOptional = turnoRepository.findByIdForUpdate(turnoId);
		if (turnoOptional.isPresent()) {
			Turno turno = turnoOptional.get();

			if (!turno.getDiaSemana().equalsIgnoreCase(turnoDTO.getDiaSemana())) {
				if (turno.getGrupo() != null) {
					List<Grupo> grupos = grupoRepository.findAll();
					// Entrega 6-1: con varias sedes, el reparto por índice (lógica heredada de Taemoi) solo mira los
					// grupos de la MISMA sede que el turno, en el mismo orden. Con una sola sede, como siempre.
					if (sedeRepository.findAllByOrderByIdAsc().size() > 1) {
						Long sedeDelTurno = turno.getGrupo().getSedeId();
						grupos = grupos.stream().filter(g -> Objects.equals(g.getSedeId(), sedeDelTurno)).toList();
					}
					Grupo nuevoGrupoAsignado;
					if (turnoDTO.getDiaSemana().equalsIgnoreCase("lunes")
							|| turnoDTO.getDiaSemana().equalsIgnoreCase("miércoles")) {
						nuevoGrupoAsignado = obtenerGrupoPorIndice(grupos, 0);
					} else if (turnoDTO.getDiaSemana().equalsIgnoreCase("martes")
							|| turnoDTO.getDiaSemana().equalsIgnoreCase("jueves")) {
						nuevoGrupoAsignado = obtenerGrupoPorIndice(grupos, 1);
					} else {
						throw new IllegalArgumentException("Día de la semana no válido: " + turnoDTO.getDiaSemana());
					}

					if (!Objects.equals(turno.getGrupo().getId(), nuevoGrupoAsignado.getId())) {
						validarSinPreinscripcionesActivas(turno);
						// El responsable no mueve un turno a un grupo de otra sede (403).
						gestionPorSede.exigirGrupoPropio(nuevoGrupoAsignado.getId());
						turno.setGrupo(nuevoGrupoAsignado);
					}
				}
			}

			turno.setDiaSemana(turnoDTO.getDiaSemana());
			turno.setHoraInicio(turnoDTO.getHoraInicio());
			turno.setHoraFin(turnoDTO.getHoraFin());
			turnoRepository.save(turno);

			return TurnoDTO.deTurno(turno);
		} else {
			throw new TurnoNoEncontradoException("El turno con ID " + turnoId + " no existe.");
		}
	}

	/**
	 * Elimina un turno por su ID.
	 *
	 * @param turnoId El ID del turno a eliminar.
	 * @throws TurnoNoEncontradoException Si no se encuentra el turno con el ID
	 *                                    especificado.
	 */
	@Override
	@Transactional
	public boolean eliminarTurno(@NonNull Long turnoId) {
		Optional<Turno> turnoOptional = turnoRepository.findByIdForUpdate(turnoId);
		if (turnoOptional.isPresent()) {
			validarSinPreinscripcionesActivas(turnoOptional.get());
			turnoRepository.deleteById(turnoId);
			return true;
		} else {
			return false;
		}
	}

	private void validarSinPreinscripcionesActivas(Turno turno) {
		if (preinscripcionRepository.countActivasByTurnoId(turno.getId(), ESTADOS_PREINSCRIPCION_ACTIVA) > 0) {
			throw new IllegalStateException(
					"No se puede modificar el turno porque existen preinscripciones activas asociadas.");
		}
	}

	/**
	 * Obtiene un grupo de la lista según su índice.
	 *
	 * @param grupos La lista de grupos disponibles.
	 * @param indice El índice del grupo a obtener.
	 * @return El grupo correspondiente al índice especificado.
	 * @throws IllegalArgumentException Si no hay suficientes grupos para asignar el
	 *                                  turno.
	 */
	private Grupo obtenerGrupoPorIndice(List<Grupo> grupos, int indice) {
		if (grupos.size() > indice) {
			return grupos.get(indice);
		} else {
			throw new IllegalArgumentException("No hay suficientes grupos para asignar el turno");
		}
	}

}
