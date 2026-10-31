package com.taemoi.project.services.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.taemoi.project.dtos.TurnoDTO;
import com.taemoi.project.config.ValidacionRelojConfig;
import com.taemoi.project.dtos.response.TurnoPublicoDTO;
import com.taemoi.project.dtos.response.TurnoResumenDTO;
import com.taemoi.project.entities.Grupo;
import com.taemoi.project.entities.EstadoPreinscripcion;
import com.taemoi.project.entities.Turno;
import com.taemoi.project.exceptions.turno.TurnoNoEncontradoException;
import com.taemoi.project.repositories.GrupoRepository;
import com.taemoi.project.repositories.PreinscripcionRepository;
import com.taemoi.project.repositories.TurnoRepository;
import com.taemoi.project.services.TurnoService;
import com.taemoi.project.utils.DiaSemanaUtils;
import com.taemoi.project.services.AforoPreinscripcionService;
import com.taemoi.project.services.TemporadaService;

import jakarta.persistence.criteria.JoinType;
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
		// R2-P3: aforo de todos los turnos por lotes (antes, por turno: un exists por alumno, un count y el límite).
		AforoPreinscripcionService.Ocupacion ocupacion = aforoPreinscripcionService.ocupacion(turnos, temporada);
		return turnos.stream().map(turno -> {
			TurnoPublicoDTO dto = TurnoPublicoDTO.deTurno(turno, hoy);
			if (turno.getGrupo() != null) {
				dto.setOcupacionEfectiva(ocupacion.ocupacionEfectiva(turno));
				dto.setAforoEfectivo(ocupacion.aforo(turno));
				dto.setCompleto(ocupacion.completo(turno));
			}
			return dto;
		}).collect(Collectors.toList());
	}

	/** Solo para tests: fija la fecha de referencia de las edades. */
	void setReloj(Clock reloj) {
		this.reloj = reloj;
	}

	@Override
	public TurnoDTO obtenerTurnoDTOPorId(@NonNull Long turnoId) {
		return soloAlumnosEnAlcance(List.of(TurnoDTO.deTurno(obtenerTurnoPorId(turnoId)))).get(0);
	}

	/**
	 * R2-P4s (decisión 124): para el responsable de sede, solo los alumnos incrustados de su alcance, con el criterio
	 * del listado de alumnos ({@link com.taemoi.project.acceso.GestionPorSede#idsAlumnosEnAlcance}, D9): una consulta
	 * para toda la respuesta. Gestión: los mismos DTO, sin ninguna consulta más.
	 */
	private List<TurnoDTO> soloAlumnosEnAlcance(List<TurnoDTO> dtos) {
		gestionPorSede.idsAlumnosEnAlcance().ifPresent(visibles -> dtos.forEach(dto -> dto.soloAlumnos(visibles)));
		return dtos;
	}

	/**
	 * R2-P4: los turnos sin sus alumnos (responsable de sede: turnos de grupos de sus sedes; gestión: todos), por id.
	 * Una consulta, con el grupo y su disciplina traídos en el mismo SELECT (el grupo del turno es EAGER), más los aforos
	 * efectivos de todos los turnos: el límite común y los de sus sedes, una vez cada uno.
	 */
	@Override
	public List<TurnoResumenDTO> listarTurnosResumen() {
		Specification<Turno> alcance = gestionPorSede.restriccion()
				.map(com.taemoi.project.acceso.PredicadosAlcance::turnosDeSusSedes)
				.orElseGet(Specification::unrestricted);
		Specification<Turno> conGrupo = (raiz, consulta, cb) -> {
			if (!Long.class.equals(consulta.getResultType())) {
				raiz.fetch("grupo", JoinType.LEFT).fetch("disciplina", JoinType.LEFT);
			}
			return null;
		};
		List<Turno> turnos = turnoRepository.findAll(conGrupo.and(alcance), Sort.by("id"));
		AforoPreinscripcionService.Aforos aforos = aforoPreinscripcionService.aforos(turnos);
		return turnos.stream()
				.map(t -> new TurnoResumenDTO(t.getId(), t.getDiaSemana(), t.getHoraInicio(), t.getHoraFin(),
						t.getGrupo() != null ? t.getGrupo().getId() : null,
						t.getGrupo() != null ? t.getGrupo().getNombre() : "Sin grupo", t.getTipo(), aforos.de(t)))
				.toList();
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

			// Decisión 374: el turno conserva su grupo al cambiar de día (vale cualquier día de lunes a domingo).
			exigirDiaValido(turnoDTO.getDiaSemana());

			turno.setDiaSemana(turnoDTO.getDiaSemana());
			turno.setHoraInicio(turnoDTO.getHoraInicio());
			turno.setHoraFin(turnoDTO.getHoraFin());
			turnoRepository.save(turno);

			// Los alumnos del cuerpo no se leen: el PUT no cambia los alumnos del turno. La respuesta, filtrada.
			return soloAlumnosEnAlcance(List.of(TurnoDTO.deTurno(turno))).get(0);
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

	/** Lanza 400 (IllegalArgumentException) si el valor no es un día de lunes a domingo. */
	private void exigirDiaValido(String dia) {
		if (dia == null) {
			throw new IllegalArgumentException("Día de la semana no válido: null");
		}
		try {
			DiaSemanaUtils.mapGrupoToDayOfWeek(dia);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Día de la semana no válido: " + dia);
		}
	}

}
