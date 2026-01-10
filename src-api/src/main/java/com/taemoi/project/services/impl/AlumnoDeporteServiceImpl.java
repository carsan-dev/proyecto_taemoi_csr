package com.taemoi.project.services.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.config.ExamEligibilityConfig;
import com.taemoi.project.config.GradeProgressionConfig;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.Categoria;
import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.RolFamiliar;
import com.taemoi.project.entities.TipoCategoria;
import com.taemoi.project.entities.TipoGrado;
import com.taemoi.project.entities.TipoTarifa;
import com.taemoi.project.repositories.AlumnoDeporteRepository;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.CategoriaRepository;
import com.taemoi.project.repositories.GradoRepository;
import com.taemoi.project.services.AlumnoDeporteService;
import com.taemoi.project.utils.FechaUtils;

@Service
@Transactional
public class AlumnoDeporteServiceImpl implements AlumnoDeporteService {

	@Autowired
	private AlumnoDeporteRepository alumnoDeporteRepository;

	@Autowired
	private AlumnoRepository alumnoRepository;

	@Autowired
	private GradoRepository gradoRepository;

	@Autowired
	private CategoriaRepository categoriaRepository;

	@Autowired
	private GradeProgressionConfig gradeProgressionConfig;

	@Autowired
	private ExamEligibilityConfig examEligibilityConfig;

	@Override
	public AlumnoDeporte agregarDeporteAAlumno(Long alumnoId, Deporte deporte, TipoGrado gradoInicial, Date fechaAlta,
			Date fechaAltaInicial, Date fechaGrado) {
		// Verificar que el alumno existe
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new IllegalArgumentException("Alumno no encontrado con ID: " + alumnoId));

		// Verificar que no tenga ya ese deporte activo
		Optional<AlumnoDeporte> existente = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte);
		if (existente.isPresent() && Boolean.TRUE.equals(existente.get().getActivo())) {
			throw new IllegalArgumentException(
					"El alumno ya tiene asignado el deporte: " + deporte);
		}

		// If there's an inactive record, suggest using activate instead
		if (existente.isPresent() && Boolean.FALSE.equals(existente.get().getActivo())) {
			throw new IllegalArgumentException(
					"El alumno ya tiene un registro inactivo de este deporte. Use activar en lugar de agregar.");
		}

		// Crear nuevo AlumnoDeporte
		AlumnoDeporte alumnoDeporte = new AlumnoDeporte();
		alumnoDeporte.setAlumno(alumno);
		alumnoDeporte.setDeporte(deporte);
		alumnoDeporte.setActivo(true);
		// Use provided fechaAlta or default to current date
		Date fechaAltaFinal = fechaAlta != null ? fechaAlta : new Date();
		alumnoDeporte.setFechaAlta(fechaAltaFinal);
		// Use provided fechaAltaInicial or default to fechaAlta
		Date fechaAltaInicialFinal = fechaAltaInicial != null ? fechaAltaInicial : fechaAltaFinal;
		alumnoDeporte.setFechaAltaInicial(fechaAltaInicialFinal);
		alumnoDeporte.setAptoParaExamen(false);

		// Asignar grado si se proporcionó (deportes como Pilates no tienen grado)
		if (gradoInicial != null) {
			Grado grado = gradoRepository.findByTipoGrado(gradoInicial);
			if (grado == null) {
				throw new IllegalArgumentException("Grado no encontrado: " + gradoInicial);
			}
			alumnoDeporte.setGrado(grado);
			// Use provided fechaGrado or default to current date
			alumnoDeporte.setFechaGrado(fechaGrado != null ? fechaGrado : new Date());
		}

		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte agregarDeporteAAlumnoCompleto(Long alumnoId, Deporte deporte, TipoGrado gradoInicial,
			Date fechaAlta, Date fechaAltaInicial, Date fechaGrado, TipoTarifa tipoTarifa, Double cuantiaTarifa,
			String rolFamiliar, String grupoFamiliar, String categoria, Boolean competidor,
			Date fechaAltaCompeticion, Date fechaAltaCompetidorInicial, Double peso,
			Date fechaPeso, Boolean tieneLicencia, Integer numeroLicencia, Date fechaLicencia) {
		// Verificar que el alumno existe
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new IllegalArgumentException("Alumno no encontrado con ID: " + alumnoId));

		// Verificar que no tenga ya ese deporte activo
		Optional<AlumnoDeporte> existente = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte);
		if (existente.isPresent() && Boolean.TRUE.equals(existente.get().getActivo())) {
			throw new IllegalArgumentException(
					"El alumno ya tiene asignado el deporte: " + deporte);
		}

		// If there's an inactive record, suggest using activate instead
		if (existente.isPresent() && Boolean.FALSE.equals(existente.get().getActivo())) {
			throw new IllegalArgumentException(
					"El alumno ya tiene un registro inactivo de este deporte. Use activar en lugar de agregar.");
		}

		// Crear nuevo AlumnoDeporte
		AlumnoDeporte alumnoDeporte = new AlumnoDeporte();
		alumnoDeporte.setAlumno(alumno);
		alumnoDeporte.setDeporte(deporte);
		alumnoDeporte.setActivo(true);
		// Use provided fechaAlta or default to current date
		Date fechaAltaFinal = fechaAlta != null ? fechaAlta : new Date();
		alumnoDeporte.setFechaAlta(fechaAltaFinal);
		// Use provided fechaAltaInicial or default to fechaAlta
		Date fechaAltaInicialFinal = fechaAltaInicial != null ? fechaAltaInicial : fechaAltaFinal;
		alumnoDeporte.setFechaAltaInicial(fechaAltaInicialFinal);
		alumnoDeporte.setAptoParaExamen(false);

		// Asignar grado si se proporcionó (deportes como Pilates no tienen grado)
		if (gradoInicial != null) {
			Grado grado = gradoRepository.findByTipoGrado(gradoInicial);
			if (grado == null) {
				throw new IllegalArgumentException("Grado no encontrado: " + gradoInicial);
			}
			alumnoDeporte.setGrado(grado);
			// Use provided fechaGrado or default to current date
			alumnoDeporte.setFechaGrado(fechaGrado != null ? fechaGrado : new Date());
		}

		// Set per-sport fields
		alumnoDeporte.setTipoTarifa(tipoTarifa);
		alumnoDeporte.setCuantiaTarifa(cuantiaTarifa);
		// Set rolFamiliar and grupoFamiliar if provided
		if (rolFamiliar != null && !rolFamiliar.isEmpty()) {
			alumnoDeporte.setRolFamiliar(RolFamiliar.valueOf(rolFamiliar));
		}
		alumnoDeporte.setGrupoFamiliar(grupoFamiliar);

		// License must be set before competitor
		alumnoDeporte.setTieneLicencia(tieneLicencia != null ? tieneLicencia : false);
		alumnoDeporte.setNumeroLicencia(numeroLicencia);
		alumnoDeporte.setFechaLicencia(fechaLicencia);

		// Validate and set competitor status
		boolean esCompetidor = competidor != null ? competidor : false;
		if (esCompetidor && !Boolean.TRUE.equals(alumnoDeporte.getTieneLicencia())) {
			throw new IllegalArgumentException(
					"El alumno debe tener una licencia federativa activa para ser marcado como competidor");
		}
		alumnoDeporte.setCompetidor(esCompetidor);
		if (esCompetidor) {
			Date fechaCompeticionFinal = fechaAltaCompeticion != null ? fechaAltaCompeticion : fechaAltaFinal;
			Date fechaCompetidorInicialFinal = fechaAltaCompetidorInicial != null
					? fechaAltaCompetidorInicial
					: fechaAltaInicialFinal;
			alumnoDeporte.setFechaAltaCompeticion(fechaCompeticionFinal);
			alumnoDeporte.setFechaAltaCompetidorInicial(fechaCompetidorInicialFinal);
		} else {
			alumnoDeporte.setFechaAltaCompeticion(null);
			alumnoDeporte.setFechaAltaCompetidorInicial(null);
		}

		if (categoria != null && !categoria.isBlank()) {
			Categoria categoriaEntity = categoriaRepository.findByNombre(categoria.trim());
			if (categoriaEntity == null) {
				throw new IllegalArgumentException("Categoria no encontrada: " + categoria);
			}
			alumnoDeporte.setCategoria(categoriaEntity);
		} else if (esCompetidor && deporte == Deporte.TAEKWONDO) {
			int edad = FechaUtils.calcularEdad(alumno.getFechaNacimiento());
			Categoria categoriaEntity = asignarCategoriaSegunEdad(edad);
			alumnoDeporte.setCategoria(categoriaEntity);
		}

		alumnoDeporte.setPeso(peso);
		alumnoDeporte.setFechaPeso(fechaPeso);

		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public void desactivarDeporteDeAlumno(Long alumnoId, Deporte deporte) {
		// Verificar que el alumno tiene más de un deporte activo
		long deportesActivos = alumnoDeporteRepository.countByAlumnoIdAndActivoTrue(alumnoId);
		if (deportesActivos <= 1) {
			throw new IllegalArgumentException(
					"No se puede desactivar el último deporte del alumno. Debe practicar al menos un deporte.");
		}

		// Buscar el AlumnoDeporte
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		// Verificar que está activo
		if (Boolean.FALSE.equals(alumnoDeporte.getActivo())) {
			throw new IllegalArgumentException("El deporte ya está inactivo");
		}

		// Marcar como inactivo (mantiene todos los datos: grado, fechaGrado, aptoParaExamen, etc.)
		alumnoDeporte.setActivo(false);
		alumnoDeporte.setFechaBaja(new Date());
		alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public void activarDeporteDeAlumno(Long alumnoId, Deporte deporte) {
		// Buscar el AlumnoDeporte
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		// Verificar que está inactivo
		if (Boolean.TRUE.equals(alumnoDeporte.getActivo())) {
			throw new IllegalArgumentException("El deporte ya está activo");
		}

		Date fechaAltaHoy = new Date();

		// Reactivar (preserva todos los datos: grado, fechaGrado, aptoParaExamen, etc.)
		alumnoDeporte.setActivo(true);
		alumnoDeporte.setFechaBaja(null);
		alumnoDeporte.setFechaAlta(fechaAltaHoy); // Actualizar fecha de alta a hoy
		// fechaAltaInicial se mantiene sin cambios (conserva la fecha original de alta)
		// NO se resetea aptoParaExamen ni grado - se mantienen como estaban
		alumnoDeporteRepository.save(alumnoDeporte);

		// También actualizar el alumno para mantener consistencia
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new IllegalArgumentException("Alumno no encontrado: " + alumnoId));
		alumno.setActivo(true);
		alumno.setFechaBaja(null);
		alumno.setFechaAlta(fechaAltaHoy);
		// fechaAltaInicial del alumno se mantiene sin cambios
		alumnoRepository.save(alumno);
	}

	@Autowired
	private com.taemoi.project.repositories.ProductoAlumnoRepository productoAlumnoRepository;

	@Override
	public void removerDeporteDeAlumno(Long alumnoId, Deporte deporte) {
		// Verificar que el alumno tiene más de un deporte activo
		long deportesActivos = alumnoDeporteRepository.countByAlumnoIdAndActivoTrue(alumnoId);
		if (deportesActivos <= 1) {
			throw new IllegalArgumentException(
					"No se puede eliminar el último deporte del alumno. Debe practicar al menos un deporte.");
		}

		// Buscar el AlumnoDeporte
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		// Desasociar los ProductoAlumno antes de eliminar (mantiene los productos pero sin referencia al deporte)
		java.util.List<com.taemoi.project.entities.ProductoAlumno> productosAsociados =
				productoAlumnoRepository.findByAlumnoDeporteId(alumnoDeporte.getId());
		for (com.taemoi.project.entities.ProductoAlumno producto : productosAsociados) {
			producto.setAlumnoDeporte(null);
			productoAlumnoRepository.save(producto);
		}

		// Eliminar completamente el registro de la base de datos
		alumnoDeporteRepository.delete(alumnoDeporte);
	}

	@Override
	public List<AlumnoDeporte> obtenerDeportesDelAlumno(Long alumnoId) {
		return alumnoDeporteRepository.findByAlumnoIdWithGrado(alumnoId);
	}

	@Override
	public List<AlumnoDeporte> obtenerDeportesActivosDelAlumno(Long alumnoId) {
		return alumnoDeporteRepository.findByAlumnoIdWithGrado(alumnoId).stream()
				.filter(AlumnoDeporte::getActivo)
				.toList();
	}

	@Override
	public Optional<AlumnoDeporte> obtenerAlumnoDeporte(Long alumnoId, Deporte deporte) {
		return alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte);
	}

	@Override
	public AlumnoDeporte actualizarGradoPorDeporte(Long alumnoId, Deporte deporte, TipoGrado nuevoGrado) {
		// Buscar el AlumnoDeporte
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		// Validar que el deporte admite grados (Pilates y Defensa Personal no tienen grados)
		if (deporte == Deporte.PILATES || deporte == Deporte.DEFENSA_PERSONAL_FEMENINA) {
			throw new IllegalArgumentException(
					"El deporte " + deporte + " no tiene sistema de grados");
		}

		// Buscar el grado
		Grado grado = gradoRepository.findByTipoGrado(nuevoGrado);
		if (grado == null) {
			throw new IllegalArgumentException("Grado no encontrado: " + nuevoGrado);
		}

		// Actualizar grado y fecha
		alumnoDeporte.setGrado(grado);
		alumnoDeporte.setFechaGrado(new Date());

		// Recalcular aptitud para examen
		boolean aptoParaExamen = esAptoParaExamen(alumnoDeporte);
		alumnoDeporte.setAptoParaExamen(aptoParaExamen);

		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarAptoParaExamen(Long alumnoId, Deporte deporte, Boolean aptoParaExamen) {
		// Buscar el AlumnoDeporte
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		// Actualizar estado de aptitud
		alumnoDeporte.setAptoParaExamen(aptoParaExamen);

		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarFechaGrado(Long alumnoId, Deporte deporte, java.util.Date fechaGrado) {
		// Buscar el AlumnoDeporte
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		// Validar que el deporte admite grados
		if (deporte == Deporte.PILATES || deporte == Deporte.DEFENSA_PERSONAL_FEMENINA) {
			throw new IllegalArgumentException(
					"El deporte " + deporte + " no tiene sistema de grados");
		}

		// Actualizar fecha de grado
		alumnoDeporte.setFechaGrado(fechaGrado);

		// Recalcular aptitud para examen basado en la nueva fecha
		boolean aptoParaExamen = esAptoParaExamen(alumnoDeporte);
		alumnoDeporte.setAptoParaExamen(aptoParaExamen);

		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarFechaAlta(Long alumnoId, Deporte deporte, java.util.Date fechaAlta) {
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		alumnoDeporte.setFechaAlta(fechaAlta);
		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarFechaBaja(Long alumnoId, Deporte deporte, java.util.Date fechaBaja) {
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		alumnoDeporte.setFechaBaja(fechaBaja);
		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarFechaAltaInicial(Long alumnoId, Deporte deporte, java.util.Date fechaAltaInicial) {
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		alumnoDeporte.setFechaAltaInicial(fechaAltaInicial);
		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarTipoTarifa(Long alumnoId, Deporte deporte, TipoTarifa tipoTarifa) {
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		alumnoDeporte.setTipoTarifa(tipoTarifa);
		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarCuantiaTarifa(Long alumnoId, Deporte deporte, Double cuantiaTarifa) {
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		alumnoDeporte.setCuantiaTarifa(cuantiaTarifa);
		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarRolFamiliar(Long alumnoId, Deporte deporte, String rolFamiliar) {
	    AlumnoDeporte alumnoDeporte = alumnoDeporteRepository
	            .findByAlumnoIdAndDeporte(alumnoId, deporte)
	            .orElseThrow(() -> new IllegalArgumentException(
	                    "El alumno no tiene asignado el deporte: " + deporte));

	    if (rolFamiliar == null || rolFamiliar.isBlank()) {
	        throw new IllegalArgumentException("rolFamiliar no puede ser nulo o vacío");
	    }

	    RolFamiliar rolEnum;
	    try {
	        rolEnum = RolFamiliar.valueOf(rolFamiliar.trim().toUpperCase(Locale.ROOT));
	    } catch (IllegalArgumentException e) {
	        throw new IllegalArgumentException("rolFamiliar inválido: " + rolFamiliar, e);
	    }

	    alumnoDeporte.setRolFamiliar(rolEnum);
	    return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarGrupoFamiliar(Long alumnoId, Deporte deporte, String grupoFamiliar) {
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		alumnoDeporte.setGrupoFamiliar(grupoFamiliar);
		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarTieneLicencia(Long alumnoId, Deporte deporte, Boolean tieneLicencia) {
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		alumnoDeporte.setTieneLicencia(tieneLicencia);
		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarNumeroLicencia(Long alumnoId, Deporte deporte, Integer numeroLicencia) {
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		alumnoDeporte.setNumeroLicencia(numeroLicencia);
		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarFechaLicencia(Long alumnoId, Deporte deporte, java.util.Date fechaLicencia) {
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		alumnoDeporte.setFechaLicencia(fechaLicencia);
		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarCompetidor(Long alumnoId, Deporte deporte, Boolean competidor) {
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		// Check if student has license before allowing them to become a competitor
		if (Boolean.TRUE.equals(competidor) && !Boolean.TRUE.equals(alumnoDeporte.getTieneLicencia())) {
			throw new IllegalArgumentException(
					"El alumno debe tener una licencia federativa activa para ser marcado como competidor");
		}

		alumnoDeporte.setCompetidor(competidor);

		// Auto-assign categoria for Taekwondo competitors based on age
		if (Boolean.TRUE.equals(competidor) && deporte == Deporte.TAEKWONDO) {
			Alumno alumno = alumnoDeporte.getAlumno();
			int edad = FechaUtils.calcularEdad(alumno.getFechaNacimiento());
			Categoria categoria = asignarCategoriaSegunEdad(edad);
			alumnoDeporte.setCategoria(categoria);
		} else if (Boolean.FALSE.equals(competidor)) {
			// If unmarking as competitor, remove categoria
			alumnoDeporte.setCategoria(null);
		}

		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	/**
	 * Asigna una categoría según la edad del alumno para competidores de Taekwondo
	 *
	 * @param edad La edad del alumno
	 * @return La categoría asignada
	 */
	private Categoria asignarCategoriaSegunEdad(int edad) {
		TipoCategoria tipoCategoria;
		if (edad <= 9) {
			tipoCategoria = TipoCategoria.INFANTIL;
		} else if (edad <= 11) {
			tipoCategoria = TipoCategoria.PRECADETE;
		} else if (edad <= 13) {
			tipoCategoria = TipoCategoria.CADETE;
		} else if (edad <= 16) {
			tipoCategoria = TipoCategoria.JUNIOR;
		} else {
			tipoCategoria = TipoCategoria.SENIOR;
		}

		return categoriaRepository.findByNombre(tipoCategoria.getNombre());
	}

	@Override
	public AlumnoDeporte actualizarPeso(Long alumnoId, Deporte deporte, Double peso) {
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		alumnoDeporte.setPeso(peso);
		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarFechaPeso(Long alumnoId, Deporte deporte, java.util.Date fechaPeso) {
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		alumnoDeporte.setFechaPeso(fechaPeso);
		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarFechaAltaCompeticion(Long alumnoId, Deporte deporte, java.util.Date fechaAltaCompeticion) {
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		alumnoDeporte.setFechaAltaCompeticion(fechaAltaCompeticion);
		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarFechaAltaCompetidorInicial(Long alumnoId, Deporte deporte, java.util.Date fechaAltaCompetidorInicial) {
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		alumnoDeporte.setFechaAltaCompetidorInicial(fechaAltaCompetidorInicial);
		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarCategoria(Long alumnoId, Deporte deporte, String categoriaNombre) {
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		// Validate categoria name is provided
		if (categoriaNombre == null || categoriaNombre.isBlank()) {
			throw new IllegalArgumentException("El nombre de la categoría no puede ser nulo o vacío");
		}

		// Look up categoria by name
		Categoria categoria = categoriaRepository.findByNombre(categoriaNombre.trim());
		if (categoria == null) {
			throw new IllegalArgumentException("Categoría no encontrada: " + categoriaNombre);
		}

		// Update categoria
		alumnoDeporte.setCategoria(categoria);
		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarDatosCompetidor(Long alumnoId, Deporte deporte, Boolean competidor,
			java.util.Date fechaAltaCompeticion, java.util.Date fechaAltaCompetidorInicial,
			String categoriaNombre, Double peso, java.util.Date fechaPeso) {
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		// Validar que tiene licencia antes de marcar como competidor
		if (Boolean.TRUE.equals(competidor) && !Boolean.TRUE.equals(alumnoDeporte.getTieneLicencia())) {
			throw new IllegalArgumentException(
					"El alumno debe tener una licencia federativa activa para ser marcado como competidor");
		}

		// Actualizar todos los campos en una sola transacción
		if (competidor != null) {
			alumnoDeporte.setCompetidor(competidor);

			// Auto-assign categoria for Taekwondo competitors based on age if becoming competitor
			if (Boolean.TRUE.equals(competidor) && deporte == Deporte.TAEKWONDO && categoriaNombre == null) {
				Alumno alumno = alumnoDeporte.getAlumno();
				int edad = FechaUtils.calcularEdad(alumno.getFechaNacimiento());
				Categoria categoria = asignarCategoriaSegunEdad(edad);
				alumnoDeporte.setCategoria(categoria);
			} else if (Boolean.FALSE.equals(competidor)) {
				// If unmarking as competitor, remove categoria
				alumnoDeporte.setCategoria(null);
			}
		}

		if (fechaAltaCompeticion != null) {
			alumnoDeporte.setFechaAltaCompeticion(fechaAltaCompeticion);
		}

		if (fechaAltaCompetidorInicial != null) {
			alumnoDeporte.setFechaAltaCompetidorInicial(fechaAltaCompetidorInicial);
		}

		if (categoriaNombre != null && !categoriaNombre.isBlank()) {
			Categoria categoria = categoriaRepository.findByNombre(categoriaNombre.trim());
			if (categoria == null) {
				throw new IllegalArgumentException("Categoría no encontrada: " + categoriaNombre);
			}
			alumnoDeporte.setCategoria(categoria);
		}

		if (peso != null) {
			alumnoDeporte.setPeso(peso);
		}

		if (fechaPeso != null) {
			alumnoDeporte.setFechaPeso(fechaPeso);
		}

		Boolean competidorFinal = competidor != null ? competidor : alumnoDeporte.getCompetidor();
		if (Boolean.TRUE.equals(competidorFinal)
				&& deporte == Deporte.TAEKWONDO
				&& alumnoDeporte.getCategoria() == null) {
			int edad = FechaUtils.calcularEdad(alumnoDeporte.getAlumno().getFechaNacimiento());
			Categoria categoria = asignarCategoriaSegunEdad(edad);
			alumnoDeporte.setCategoria(categoria);
		}

		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public TipoGrado calcularSiguienteGrado(AlumnoDeporte alumnoDeporte) {
		// Validar que tiene grado actual
		if (alumnoDeporte.getGrado() == null) {
			return null;
		}

		TipoGrado gradoActual = alumnoDeporte.getGrado().getTipoGrado();
		Deporte deporte = alumnoDeporte.getDeporte();

		// Usar FechaUtils.esMenor para aplicar la regla correcta según el deporte
		boolean esMenor = FechaUtils.esMenor(alumnoDeporte.getAlumno().getFechaNacimiento(), deporte);

		// Usar GradeProgressionConfig para obtener el siguiente grado
		return gradeProgressionConfig.obtenerSiguienteGrado(deporte, esMenor, gradoActual);
	}

	@Override
	public boolean esAptoParaExamen(AlumnoDeporte alumnoDeporte) {
		// Deportes sin grados no tienen exámenes
		if (alumnoDeporte.getDeporte() == Deporte.PILATES
				|| alumnoDeporte.getDeporte() == Deporte.DEFENSA_PERSONAL_FEMENINA) {
			return false;
		}

		// Verificar que tiene grado y fecha de grado
		if (alumnoDeporte.getGrado() == null || alumnoDeporte.getFechaGrado() == null) {
			return false;
		}

		// Convertir fecha grado a LocalDate (manejar java.sql.Date que no soporta toInstant())
		Date fechaGradoDate = alumnoDeporte.getFechaGrado();
		LocalDate fechaGrado;
		if (fechaGradoDate instanceof java.sql.Date) {
			fechaGrado = ((java.sql.Date) fechaGradoDate).toLocalDate();
		} else {
			fechaGrado = fechaGradoDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		}

		// Usar FechaUtils.esMenor para aplicar la regla correcta según el deporte
		Date fechaNacimiento = alumnoDeporte.getAlumno().getFechaNacimiento();
		boolean esMenor = FechaUtils.esMenor(fechaNacimiento, alumnoDeporte.getDeporte());
		Long mesesRequeridos = (esMenor
				? examEligibilityConfig.obtenerMesesRequeridosMenores()
				: examEligibilityConfig.obtenerMesesRequeridosMayores())
				.get(alumnoDeporte.getGrado().getTipoGrado());

		// Verificar si es elegible para examen (sin periodo definido)
		if (mesesRequeridos == null || mesesRequeridos == Long.MAX_VALUE) {
			return false;
		}

		// Calcular fecha posible de examen
		LocalDate fechaExamenPosible = fechaGrado.plusMonths(mesesRequeridos);

		// Verificar si ya pasó el tiempo requerido
		return !LocalDate.now().isBefore(fechaExamenPosible);
	}

	@Override
	public void actualizarAptitudParaExamen(Long alumnoId) {
		List<AlumnoDeporte> deportes = alumnoDeporteRepository.findByAlumnoId(alumnoId);

		for (AlumnoDeporte alumnoDeporte : deportes) {
			if (alumnoDeporte.getActivo()) {
				boolean aptoParaExamen = esAptoParaExamen(alumnoDeporte);
				alumnoDeporte.setAptoParaExamen(aptoParaExamen);
				alumnoDeporteRepository.save(alumnoDeporte);
			}
		}
	}

	@Override
	public List<AlumnoDeporte> obtenerAptosParaExamenPorDeporte(Deporte deporte) {
		return alumnoDeporteRepository.findAptosParaExamenPorDeporte(deporte);
	}

	@Override
	public boolean alumnoTieneDeporte(Long alumnoId, Deporte deporte) {
		return alumnoDeporteRepository.existsByAlumnoIdAndDeporte(alumnoId, deporte);
	}

	@Override
	public long contarDeportesActivos(Long alumnoId) {
		return alumnoDeporteRepository.countByAlumnoIdAndActivoTrue(alumnoId);
	}

	@Override
	public Optional<AlumnoDeporte> obtenerPorIdConRelaciones(Long id) {
		return alumnoDeporteRepository.findByIdWithRelaciones(id);
	}
}
