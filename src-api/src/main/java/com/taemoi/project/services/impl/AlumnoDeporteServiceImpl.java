package com.taemoi.project.services.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.config.ExamEligibilityConfig;
import com.taemoi.project.config.GradeProgressionConfig;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.TipoGrado;
import com.taemoi.project.repositories.AlumnoDeporteRepository;
import com.taemoi.project.repositories.AlumnoRepository;
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
	private GradeProgressionConfig gradeProgressionConfig;

	@Autowired
	private ExamEligibilityConfig examEligibilityConfig;

	@Override
	public AlumnoDeporte agregarDeporteAAlumno(Long alumnoId, Deporte deporte, TipoGrado gradoInicial) {
		// Verificar que el alumno existe
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new IllegalArgumentException("Alumno no encontrado con ID: " + alumnoId));

		// Verificar que no tenga ya ese deporte
		if (alumnoDeporteRepository.existsByAlumnoIdAndDeporte(alumnoId, deporte)) {
			throw new IllegalArgumentException(
					"El alumno ya tiene asignado el deporte: " + deporte);
		}

		// Crear nuevo AlumnoDeporte
		AlumnoDeporte alumnoDeporte = new AlumnoDeporte();
		alumnoDeporte.setAlumno(alumno);
		alumnoDeporte.setDeporte(deporte);
		alumnoDeporte.setActivo(true);
		alumnoDeporte.setFechaAlta(new Date());
		alumnoDeporte.setAptoParaExamen(false);

		// Asignar grado si se proporcionó (deportes como Pilates no tienen grado)
		if (gradoInicial != null) {
			Grado grado = gradoRepository.findByTipoGrado(gradoInicial);
			if (grado == null) {
				throw new IllegalArgumentException("Grado no encontrado: " + gradoInicial);
			}
			alumnoDeporte.setGrado(grado);
			alumnoDeporte.setFechaGrado(new Date());
		}

		return alumnoDeporteRepository.save(alumnoDeporte);
	}

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

		// Marcar como inactivo en lugar de eliminar (para mantener histórico)
		alumnoDeporte.setActivo(false);
		alumnoDeporte.setFechaBaja(new Date());
		alumnoDeporteRepository.save(alumnoDeporte);
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
	public TipoGrado calcularSiguienteGrado(AlumnoDeporte alumnoDeporte) {
		// Validar que tiene grado actual
		if (alumnoDeporte.getGrado() == null) {
			return null;
		}

		TipoGrado gradoActual = alumnoDeporte.getGrado().getTipoGrado();
		Deporte deporte = alumnoDeporte.getDeporte();

		// Calcular edad del alumno
		int edad = FechaUtils.calcularEdad(alumnoDeporte.getAlumno().getFechaNacimiento());
		boolean esMenor = edad < 13 || (edad == 13 && !cumple14EsteAnio(alumnoDeporte.getAlumno().getFechaNacimiento()));

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

		// Convertir fecha grado a LocalDate
		LocalDate fechaGrado = alumnoDeporte.getFechaGrado().toInstant()
				.atZone(ZoneId.systemDefault()).toLocalDate();

		// Calcular edad y meses requeridos
		int edad = FechaUtils.calcularEdad(alumnoDeporte.getAlumno().getFechaNacimiento());
		long mesesRequeridos = examEligibilityConfig.obtenerMesesRequeridos(edad,
				alumnoDeporte.getGrado().getTipoGrado());

		// Verificar si es elegible para examen (no para cinturones negros 5º DAN)
		if (mesesRequeridos == -1) {
			return false; // No es elegible para más exámenes
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

	/**
	 * Verifica si el alumno cumple 14 años en el año actual
	 */
	private boolean cumple14EsteAnio(Date fechaNacimiento) {
		LocalDate fechaNac = fechaNacimiento.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		int anioNacimiento = fechaNac.getYear();
		int anioActual = LocalDate.now().getYear();
		int edadEsteAnio = anioActual - anioNacimiento;
		return edadEsteAnio >= 14;
	}
}
