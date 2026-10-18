package com.taemoi.project.services.impl;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.taemoi.project.dtos.ConvocatoriaDTO;
import com.taemoi.project.dtos.response.AlumnoConvocatoriaDTO;
import com.taemoi.project.dtos.response.AlumnoConvocatoriaReporteDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoConvocatoria;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.Convocatoria;
import com.taemoi.project.entities.OrigenCobro;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.repositories.AlumnoConvocatoriaRepository;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.ConvocatoriaRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.services.CatalogoDisciplinasService;
import com.taemoi.project.services.CobroService;
import com.taemoi.project.services.ConvocatoriaService;
import com.taemoi.project.services.ProgresionGradosService;
import com.taemoi.project.utils.FechaUtils;

@Service
public class ConvocatoriaServiceImpl implements ConvocatoriaService {

	@Autowired
	private ConvocatoriaRepository convocatoriaRepository;

	@Autowired
	private AlumnoConvocatoriaRepository alumnoConvocatoriaRepository;

	@Autowired
	private ProductoAlumnoRepository productoAlumnoRepository;

	@Autowired
	private AlumnoRepository alumnoRepository;

	@Autowired
	private CatalogoDisciplinasService catalogo;

	@Autowired
	private ProgresionGradosService progresion;

	@Autowired
	private com.taemoi.project.services.AlumnoDeporteService alumnoDeporteService;

	/** Entrega 6-2 (D6-8): el responsable de sede ve dentro de cada convocatoria solo a sus alumnos (D9). */
	@Autowired
	private com.taemoi.project.acceso.GestionPorSede gestionPorSede;

	/** Pieza 6b-1: registro de cobros de la segunda puerta (el pago del examen se copia a su cargo). */
	@Autowired
	private CobroService cobros;

	private static final String MOTIVO_REVERSION_CONVOCATORIA = "pago revertido desde la convocatoria";

	@Override
	public ConvocatoriaDTO crearConvocatoria(ConvocatoriaDTO convocatoriaDTO) {
		Convocatoria convocatoria = new Convocatoria();
		convocatoria.setFechaConvocatoria(convocatoriaDTO.getFechaConvocatoria());
		if (convocatoriaDTO.getDeporte() != null) {
			convocatoria.setDisciplina(catalogo.obtenerPorCodigo(convocatoriaDTO.getDeporte()));
		}
		Convocatoria convocatoriaGuardada = convocatoriaRepository.save(convocatoria);

		return convertirAConvocatoriaDTO(convocatoriaGuardada);
	}

	@Override
	public List<ConvocatoriaDTO> obtenerConvocatorias() {
		return convocatoriaRepository.findAll().stream().map(this::convertirAConvocatoriaDTO)
				.collect(Collectors.toList());
	}

	@Override
	public ConvocatoriaDTO obtenerConvocatoriaPorId(Long id) {
		Convocatoria convocatoria = convocatoriaRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Convocatoria no encontrada con ID: " + id));

		return convertirAConvocatoriaDTO(convocatoria);
	}

	@Override
	public List<ConvocatoriaDTO> obtenerConvocatoriasDeAlumno(Long alumnoId) {
		List<AlumnoConvocatoria> relaciones = alumnoConvocatoriaRepository.findByAlumnoId(alumnoId);
		return relaciones.stream()
				.filter(ac -> !esConvocatoriaYaProcesada(ac))
				.map(ac -> convertirAConvocatoriaDTO(ac.getConvocatoria()))
				.collect(Collectors.toList());
	}

	@Override
	public Optional<ConvocatoriaDTO> obtenerConvocatoriaActualPorDeporte(String deporte) {
		return convocatoriaRepository.findConvocatoriaActualPorDeporte(deporte).map(this::convertirAConvocatoriaDTO);
	}

	@Override
	public List<ConvocatoriaDTO> obtenerConvocatoriasPorDeporte(String deporte) {
		return convocatoriaRepository.findByDeporte(deporte).stream().map(this::convertirAConvocatoriaDTO)
				.collect(Collectors.toList());
	}

	@Override
	public List<AlumnoConvocatoriaDTO> obtenerAlumnosDeConvocatoria(Long convocatoriaId) {
		Convocatoria convocatoria = convocatoriaRepository.findById(convocatoriaId).orElseThrow(
				() -> new IllegalArgumentException("Convocatoria no encontrada con ID: " + convocatoriaId));

		Predicate<AlumnoConvocatoria> enAlcance = enAlcanceDelResponsable();
		return convocatoria.getAlumnosConvocatoria().stream()
				.filter(enAlcance)
				.map(alumnoConvocatoria -> new AlumnoConvocatoriaDTO(alumnoConvocatoria.getId(),
						alumnoConvocatoria.getAlumno().getId(), alumnoConvocatoria.getAlumno().getNombre(),
						alumnoConvocatoria.getAlumno().getApellidos(), alumnoConvocatoria.getCuantiaExamen(),
						alumnoConvocatoria.getGradoActual(), alumnoConvocatoria.getGradoSiguiente(),
						alumnoConvocatoria.getPagado()))
				.collect(Collectors.toList());
	}

	@Override
	public List<AlumnoConvocatoriaReporteDTO> obtenerReporteDeConvocatoria(Long convocatoriaId) {
		Convocatoria convocatoria = convocatoriaRepository.findById(convocatoriaId).orElseThrow(
				() -> new IllegalArgumentException("Convocatoria no encontrada con ID: " + convocatoriaId));

		Predicate<AlumnoConvocatoria> enAlcance = enAlcanceDelResponsable();
		return convocatoria.getAlumnosConvocatoria().stream()
				.filter(enAlcance)
				.map(alumnoConvocatoria -> {
					Alumno alumno = alumnoConvocatoria.getAlumno();
					String nombreCompleto = alumno.getNombre() + " " + alumno.getApellidos();
					Integer edad = FechaUtils.calcularEdad(alumno.getFechaNacimiento());
					// Get per-sport data from AlumnoDeporte
					AlumnoDeporte alumnoDeporte = alumnoConvocatoria.getAlumnoDeporte();
					if (alumnoDeporte == null && alumno != null) {
						alumnoDeporte = alumno.getDeportes().stream()
								.filter(ad -> java.util.Objects.equals(ad.getDeporte(), convocatoria.getDeporte()))
								.findFirst()
								.orElse(null);
					}
					String categoriaNombre = alumnoDeporte != null && alumnoDeporte.getCategoria() != null
							? alumnoDeporte.getCategoria().getNombre()
							: null;
					Integer numeroLicencia = alumnoDeporte != null ? alumnoDeporte.getNumeroLicencia() : null;
					Double peso = alumnoDeporte != null ? alumnoDeporte.getPeso() : null;

					return new AlumnoConvocatoriaReporteDTO(
							alumno.getId(),
							nombreCompleto,
							alumno.getNumeroExpediente(),
							numeroLicencia,
							edad,
							categoriaNombre,
							peso,
							alumnoConvocatoria.getPagado(),
							alumnoConvocatoria.getGradoActual(),
							alumnoConvocatoria.getGradoSiguiente()
					);
				})
				.collect(Collectors.toList());
	}

	@Override
	public void eliminarConvocatoria(Long convocatoriaId) {
		Convocatoria convocatoria = convocatoriaRepository.findById(convocatoriaId).orElseThrow(
				() -> new IllegalArgumentException("Convocatoria no encontrada con ID: " + convocatoriaId));

		for (AlumnoConvocatoria alumnoConvocatoria : convocatoria.getAlumnosConvocatoria()) {
			Alumno alumno = alumnoConvocatoria.getAlumno();

			ProductoAlumno productoAlumno = alumnoConvocatoria.getProductoAlumno();
			if (productoAlumno != null) {
				alumnoConvocatoria.setProductoAlumno(null);
				alumno.getProductosAlumno().remove(productoAlumno);
				// Pieza 6b-1: el método no es transaccional y cada borrado iba en su propia transacción; sigue igual, y
				// en ella se anula antes el cobro ACTIVO del cargo («cargo borrado»).
				cobros.enTransaccion(() -> {
					cobros.alBorrarCargos(List.of(productoAlumno.getId()));
					productoAlumnoRepository.delete(productoAlumno);
					return null;
				});
			}
			if (alumno != null) {
				alumno.getConvocatorias().remove(alumnoConvocatoria);
				alumno.setTieneDerechoExamen(false);
				alumnoRepository.save(alumno);
			}
			alumnoConvocatoriaRepository.delete(alumnoConvocatoria);
		}
		convocatoriaRepository.delete(convocatoria);
	}

	@Override
	public void actualizarGradosDeConvocatoria(Long convocatoriaId) {
	    Convocatoria convocatoria = convocatoriaRepository.findById(convocatoriaId)
	        .orElseThrow(() -> new IllegalArgumentException("Convocatoria no encontrada"));

	    for (AlumnoConvocatoria ac : convocatoria.getAlumnosConvocatoria()) {
	        AlumnoDeporte alumnoDeporte = ac.getAlumnoDeporte();
	        if (alumnoDeporte == null && ac.getAlumno() != null) {
	        	alumnoDeporte = alumnoDeporteService.obtenerDeportesDelAlumno(ac.getAlumno().getId()).stream()
	        			.filter(ad -> java.util.Objects.equals(ad.getDeporte(), convocatoria.getDeporte()))
	        			.findFirst()
	        			.orElse(null);
	        }

	        if (alumnoDeporte == null) {
	        	continue;
	        }

	        String nuevoTipo = alumnoDeporteService.calcularSiguienteGrado(alumnoDeporte);
	        if (nuevoTipo != null) {
	        	// El siguiente grado debe existir en el sistema de grados de la disciplina
	        	if (progresion.buscarGrado(alumnoDeporte.getDeporte(), nuevoTipo).isPresent()) {
	        		alumnoDeporteService.actualizarGradoPorDeporte(
	        				alumnoDeporte.getAlumno().getId(),
	        				alumnoDeporte.getDeporte(),
	        				nuevoTipo
	        		);
	        	}
	        }

	        Alumno alumno = alumnoDeporte.getAlumno();
	        if (alumno != null) {
	        	alumno.setTieneDerechoExamen(false);
	        	alumnoRepository.save(alumno);
	        }
	    }
	}

	@Override
	public void actualizarAlumnoConvocatoria(Long alumnoConvocatoriaId, AlumnoConvocatoriaDTO alumnoConvocatoriaDTO) {
		AlumnoConvocatoria alumnoConvocatoria = alumnoConvocatoriaRepository.findById(alumnoConvocatoriaId)
				.orElseThrow(() -> new IllegalArgumentException("AlumnoConvocatoria no encontrado"));
		// Pieza 6b-1: revertir el pago de un examen cuyo cobro ya va en una entrega de caja → 409 antes de cambiar nada.
		if (alumnoConvocatoria.getProductoAlumno() != null && !Boolean.TRUE.equals(alumnoConvocatoriaDTO.getPagado())) {
			cobros.exigirSinEntrega(alumnoConvocatoria.getProductoAlumno().getId());
		}

		// Pieza 6b-2: el cambio de pago de la inscripción va ENTERO en una transacción y con el orden de bloqueo de las
		// demás reversiones: primero el cargo (FOR UPDATE), después su cobro (registrar o anular, que bloquea el cobro)
		// y solo entonces se guarda la inscripción. Si una entrega de caja tomó el cobro entre la comprobación previa y
		// el bloqueo, el 409 deshace todo y la inscripción queda como estaba. La fecha de grado y el derecho a examen se
		// actualizan después, como antes, solo si el pago se guardó.
		ProductoAlumno cargoDelExamen = alumnoConvocatoria.getProductoAlumno();
		cobros.enTransaccion(() -> {
			if (cargoDelExamen != null) {
				// Pieza 6b-1: el cargo se bloquea y se relee, y su cobro se registra (si pasa a pagado) o se anula (si
				// pasa a no pagado). Si ya estaba pagado (p. ej. por el PUT del cargo), no hay otro cobro.
				ProductoAlumno productoAlumno = cobros.bloquearCargo(cargoDelExamen.getId()).orElse(cargoDelExamen);
				boolean pagadoAntes = Boolean.TRUE.equals(productoAlumno.getPagado());
				productoAlumno.setPrecio(alumnoConvocatoriaDTO.getCuantiaExamen());
				productoAlumno.setPagado(alumnoConvocatoriaDTO.getPagado());
				productoAlumno.setFechaPago(alumnoConvocatoriaDTO.getPagado() ? new Date() : null);
				ProductoAlumno guardado = productoAlumnoRepository.save(productoAlumno);
				cobros.alCambiarPago(guardado, pagadoAntes, alumnoConvocatoriaDTO.getMetodoPago(),
						OrigenCobro.CONVOCATORIA, MOTIVO_REVERSION_CONVOCATORIA);
			}
			alumnoConvocatoria.setCuantiaExamen(alumnoConvocatoriaDTO.getCuantiaExamen());
			alumnoConvocatoria.setPagado(alumnoConvocatoriaDTO.getPagado());
			if (alumnoConvocatoriaDTO.getPagado()) {
				alumnoConvocatoria.setFechaPago(new Date());
			} else {
				alumnoConvocatoria.setFechaPago(null);
			}
			alumnoConvocatoriaRepository.save(alumnoConvocatoria);
			return null;
		});

		Alumno alumno = alumnoConvocatoria.getAlumno();
		AlumnoDeporte alumnoDeporte = alumnoConvocatoria.getAlumnoDeporte();
		if (alumnoDeporte == null && alumno != null) {
			alumnoDeporte = alumnoDeporteService.obtenerDeportesDelAlumno(alumno.getId()).stream()
					.filter(ad -> java.util.Objects.equals(ad.getDeporte(), alumnoConvocatoria.getConvocatoria().getDeporte()))
					.findFirst()
					.orElse(null);
		}

		if (alumnoDeporte != null && alumnoDeporte.getFechaGrado() == null) {
			alumnoDeporteService.actualizarFechaGrado(
					alumnoDeporte.getAlumno().getId(),
					alumnoDeporte.getDeporte(),
					new Date()
			);
		}

		if (alumno != null) {
			alumno.setTieneDerechoExamen(alumnoConvocatoriaDTO.getPagado());
			alumnoRepository.save(alumno);
		}
	}

	/**
	 * Entrega 6-2 (D6-8): para el responsable de sede, solo las inscripciones de alumnos de su alcance (D9). Para la
	 * gestión no filtra (mismos alumnos y mismo orden que antes).
	 */
	private Predicate<AlumnoConvocatoria> enAlcanceDelResponsable() {
		Optional<Set<Long>> ids = gestionPorSede.idsAlumnosEnAlcance();
		if (ids.isEmpty()) {
			return inscripcion -> true;
		}
		Set<Long> visibles = ids.get();
		return inscripcion -> inscripcion.getAlumno() != null && visibles.contains(inscripcion.getAlumno().getId());
	}

	private ConvocatoriaDTO convertirAConvocatoriaDTO(Convocatoria convocatoria) {
		ConvocatoriaDTO dto = new ConvocatoriaDTO();
		dto.setId(convocatoria.getId());
		dto.setFechaConvocatoria(convocatoria.getFechaConvocatoria());
		dto.setDeporte(convocatoria.getDeporte());
		return dto;
	}

	private boolean esConvocatoriaYaProcesada(AlumnoConvocatoria alumnoConvocatoria) {
		if (alumnoConvocatoria == null || alumnoConvocatoria.getGradoSiguiente() == null) {
			return false;
		}

		AlumnoDeporte alumnoDeporte = alumnoConvocatoria.getAlumnoDeporte();
		if (alumnoDeporte == null && alumnoConvocatoria.getAlumno() != null
				&& alumnoConvocatoria.getConvocatoria() != null) {
			String deporteConvocatoria = alumnoConvocatoria.getConvocatoria().getDeporte();
			alumnoDeporte = alumnoConvocatoria.getAlumno().getDeportes().stream()
					.filter(ad -> java.util.Objects.equals(ad.getDeporte(), deporteConvocatoria))
					.findFirst()
					.orElse(null);
		}

		if (alumnoDeporte == null || alumnoDeporte.getGrado() == null || alumnoDeporte.getGrado().getCodigo() == null) {
			return false;
		}

		// Ya procesada si el alumno tiene ya el grado al que se presentaba (por codigo, en su sistema)
		return alumnoDeporte.getGrado().getCodigo().equals(alumnoConvocatoria.getGradoSiguiente());
	}
}
