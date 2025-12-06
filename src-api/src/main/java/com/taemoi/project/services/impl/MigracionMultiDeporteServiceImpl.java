package com.taemoi.project.services.impl;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoConvocatoria;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.Grupo;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.repositories.AlumnoConvocatoriaRepository;
import com.taemoi.project.repositories.AlumnoDeporteRepository;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.GrupoRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.services.MigracionMultiDeporteService;

@Service
public class MigracionMultiDeporteServiceImpl implements MigracionMultiDeporteService {

	private static final Logger logger = LoggerFactory.getLogger(MigracionMultiDeporteServiceImpl.class);
	private static final String MIGRACION_FLAG_FILE = "migracion_multideporte_completada";

	@Autowired
	private AlumnoRepository alumnoRepository;

	@Autowired
	private AlumnoDeporteRepository alumnoDeporteRepository;

	@Autowired
	private ProductoAlumnoRepository productoAlumnoRepository;

	@Autowired
	private AlumnoConvocatoriaRepository alumnoConvocatoriaRepository;

	@Autowired
	private GrupoRepository grupoRepository;

	private boolean migracionCompletada = false;

	@Override
	@Transactional
	public MigracionReporte ejecutarMigracion() {
		MigracionReporte reporte = new MigracionReporte();

		try {
			// Verificar si ya se ejecutó
			if (isMigracionCompletada()) {
				reporte.setExitoso(true);
				reporte.setMensaje("La migración ya fue ejecutada anteriormente");
				logger.info("Migración multi-deporte: Ya completada previamente");
				return reporte;
			}

			logger.info("=== INICIANDO MIGRACIÓN MULTI-DEPORTE ===");

			// Paso 1: Migrar Alumnos a AlumnoDeporte
			int alumnosDeporteCreados = migrarAlumnosAAlumnoDeporte();
			reporte.setAlumnosDeporteCreados(alumnosDeporteCreados);
			logger.info("Paso 1 completado: {} AlumnoDeporte creados", alumnosDeporteCreados);

			// Paso 2: Actualizar ProductoAlumno
			int productosActualizados = actualizarProductosAlumno();
			reporte.setProductosActualizados(productosActualizados);
			logger.info("Paso 2 completado: {} ProductoAlumno actualizados", productosActualizados);

			// Paso 3: Actualizar AlumnoConvocatoria
			int convocatoriasActualizadas = actualizarAlumnoConvocatorias();
			reporte.setConvocatoriasActualizadas(convocatoriasActualizadas);
			logger.info("Paso 3 completado: {} AlumnoConvocatoria actualizadas", convocatoriasActualizadas);

			// Paso 4: Actualizar Grupos con deporte
			int gruposActualizados = actualizarGruposConDeporte();
			reporte.setGruposActualizados(gruposActualizados);
			logger.info("Paso 4 completado: {} Grupos actualizados", gruposActualizados);

			// Marcar migración como completada
			marcarMigracionCompletada();

			reporte.setExitoso(true);
			reporte.setMensaje("Migración completada exitosamente");
			logger.info("=== MIGRACIÓN MULTI-DEPORTE COMPLETADA EXITOSAMENTE ===");
			logger.info(reporte.toString());

		} catch (Exception e) {
			reporte.setExitoso(false);
			reporte.setError(e.getMessage());
			reporte.setMensaje("Error durante la migración");
			logger.error("Error durante la migración multi-deporte", e);
			throw new RuntimeException("Error en migración multi-deporte: " + e.getMessage(), e);
		}

		return reporte;
	}

	/**
	 * Paso 1: Crear AlumnoDeporte para cada alumno existente
	 */
	private int migrarAlumnosAAlumnoDeporte() {
		List<Alumno> alumnos = alumnoRepository.findAll();
		int contador = 0;

		for (Alumno alumno : alumnos) {
			// Determinar deporte: usar el asignado o TAEKWONDO por defecto
			Deporte deporteAlumno = alumno.getDeporte();
			if (deporteAlumno == null) {
				deporteAlumno = Deporte.TAEKWONDO;
				logger.warn("Alumno {} no tiene deporte asignado, usando TAEKWONDO por defecto",
					alumno.getId());
			}

			// Verificar que no exista ya
			boolean yaExiste = alumnoDeporteRepository.existsByAlumnoIdAndDeporte(
					alumno.getId(), deporteAlumno);

			if (!yaExiste) {
				AlumnoDeporte alumnoDeporte = new AlumnoDeporte();
				alumnoDeporte.setAlumno(alumno);
				alumnoDeporte.setDeporte(deporteAlumno);
				alumnoDeporte.setGrado(alumno.getGrado());
				alumnoDeporte.setFechaGrado(alumno.getFechaGrado());
				alumnoDeporte.setAptoParaExamen(alumno.getAptoParaExamen() != null ? alumno.getAptoParaExamen() : false);
				alumnoDeporte.setActivo(true);
				alumnoDeporte.setFechaAlta(alumno.getFechaAlta() != null ? alumno.getFechaAlta() : new Date());

				alumnoDeporteRepository.save(alumnoDeporte);
				contador++;

				logger.debug("Migrado alumno {} - deporte {}", alumno.getId(), deporteAlumno);
			}
		}

		return contador;
	}

	/**
	 * Paso 2: Actualizar ProductoAlumno para vincular con AlumnoDeporte
	 */
	private int actualizarProductosAlumno() {
		List<ProductoAlumno> productos = productoAlumnoRepository.findAll();
		int contador = 0;

		for (ProductoAlumno producto : productos) {
			// Solo actualizar si no tiene ya alumnoDeporte asignado
			if (producto.getAlumnoDeporte() == null && producto.getAlumno() != null) {
				Alumno alumno = producto.getAlumno();

				// Buscar el deporte del alumno
				if (alumno.getDeporte() != null) {
					AlumnoDeporte alumnoDeporte = alumnoDeporteRepository
							.findByAlumnoIdAndDeporte(alumno.getId(), alumno.getDeporte())
							.orElse(null);

					if (alumnoDeporte != null) {
						producto.setAlumnoDeporte(alumnoDeporte);
						productoAlumnoRepository.save(producto);
						contador++;
					} else {
						logger.warn("No se encontró AlumnoDeporte para alumno {} con deporte {}",
								alumno.getId(), alumno.getDeporte());
					}
				}
			}
		}

		return contador;
	}

	/**
	 * Paso 3: Actualizar AlumnoConvocatoria para vincular con AlumnoDeporte
	 */
	private int actualizarAlumnoConvocatorias() {
		List<AlumnoConvocatoria> convocatorias = alumnoConvocatoriaRepository.findAll();
		int contador = 0;

		for (AlumnoConvocatoria convocatoria : convocatorias) {
			// Solo actualizar si no tiene ya alumnoDeporte asignado
			if (convocatoria.getAlumnoDeporte() == null && convocatoria.getAlumno() != null
					&& convocatoria.getConvocatoria() != null) {

				Alumno alumno = convocatoria.getAlumno();
				Deporte deporteConvocatoria = convocatoria.getConvocatoria().getDeporte();

				// Buscar AlumnoDeporte que coincida con el deporte de la convocatoria
				AlumnoDeporte alumnoDeporte = alumnoDeporteRepository
						.findByAlumnoIdAndDeporte(alumno.getId(), deporteConvocatoria)
						.orElse(null);

				if (alumnoDeporte != null) {
					convocatoria.setAlumnoDeporte(alumnoDeporte);
					alumnoConvocatoriaRepository.save(convocatoria);
					contador++;
				} else {
					logger.warn("No se encontró AlumnoDeporte para alumno {} con deporte {} en convocatoria",
							alumno.getId(), deporteConvocatoria);
				}
			}
		}

		return contador;
	}

	/**
	 * Paso 4: Actualizar Grupos para asignar deporte basado en el nombre
	 */
	private int actualizarGruposConDeporte() {
		List<Grupo> grupos = grupoRepository.findAll();
		int contador = 0;

		for (Grupo grupo : grupos) {
			// Solo actualizar si no tiene deporte asignado
			if (grupo.getDeporte() == null && grupo.getNombre() != null) {
				String nombreLower = grupo.getNombre().toLowerCase();

				Deporte deporteAsignado = null;

				// Intentar detectar deporte por nombre del grupo
				if (nombreLower.contains("taekwondo") || nombreLower.contains("tkd")) {
					deporteAsignado = Deporte.TAEKWONDO;
				} else if (nombreLower.contains("kickboxing") || nombreLower.contains("kick")) {
					deporteAsignado = Deporte.KICKBOXING;
				} else if (nombreLower.contains("pilates")) {
					deporteAsignado = Deporte.PILATES;
				} else if (nombreLower.contains("defensa") || nombreLower.contains("femenina")) {
					deporteAsignado = Deporte.DEFENSA_PERSONAL_FEMENINA;
				} else {
					// Si no se puede detectar, asignar Taekwondo por defecto
					deporteAsignado = Deporte.TAEKWONDO;
					logger.warn("No se pudo detectar deporte para grupo '{}', asignando TAEKWONDO por defecto",
							grupo.getNombre());
				}

				grupo.setDeporte(deporteAsignado);
				grupoRepository.save(grupo);
				contador++;

				logger.debug("Grupo '{}' asignado a deporte {}", grupo.getNombre(), deporteAsignado);
			}
		}

		return contador;
	}

	@Override
	public boolean isMigracionCompletada() {
		// Por simplicidad, usar variable en memoria
		// En producción, usar base de datos o archivo de configuración
		return migracionCompletada || verificarMigracionEnBD();
	}

	/**
	 * Verifica si la migración se completó consultando la BD
	 * (verifica si existen AlumnoDeporte)
	 */
	private boolean verificarMigracionEnBD() {
		long countAlumnos = alumnoRepository.count();
		long countAlumnoDeporte = alumnoDeporteRepository.count();

		// Si hay AlumnoDeporte creados, asumimos que la migración se ejecutó
		if (countAlumnoDeporte > 0) {
			logger.info("Migración detectada como completada ({} AlumnoDeporte encontrados)", countAlumnoDeporte);
			migracionCompletada = true;
			return true;
		}

		return false;
	}

	@Override
	public void marcarMigracionCompletada() {
		migracionCompletada = true;
		logger.info("Migración marcada como completada");
		// En producción, guardar en BD o archivo de propiedades
	}

	@Override
	@Transactional
	public com.taemoi.project.controllers.MigracionController.CorreccionGruposReporte corregirDeportesGrupos() {
		com.taemoi.project.controllers.MigracionController.CorreccionGruposReporte reporte =
			new com.taemoi.project.controllers.MigracionController.CorreccionGruposReporte();

		try {
			logger.info("=== INICIANDO CORRECCIÓN DE DEPORTES EN GRUPOS ===");

			List<Grupo> grupos = grupoRepository.findAll();
			int contador = 0;

			for (Grupo grupo : grupos) {
				if (grupo.getNombre() != null) {
					String nombreLower = grupo.getNombre().toLowerCase();
					Deporte deporteOriginal = grupo.getDeporte();
					Deporte deporteCorrecto = null;

					// Detectar deporte correcto basado en el nombre
					if (nombreLower.contains("taekwondo") || nombreLower.contains("tkd")) {
						deporteCorrecto = Deporte.TAEKWONDO;
					} else if (nombreLower.contains("kickboxing") || nombreLower.contains("kick")) {
						deporteCorrecto = Deporte.KICKBOXING;
					} else if (nombreLower.contains("pilates")) {
						deporteCorrecto = Deporte.PILATES;
					} else if (nombreLower.contains("defensa") || nombreLower.contains("femenina")) {
						deporteCorrecto = Deporte.DEFENSA_PERSONAL_FEMENINA;
					} else {
						// Si no se puede detectar, asignar Taekwondo por defecto
						deporteCorrecto = Deporte.TAEKWONDO;
						logger.warn("No se pudo detectar deporte para grupo '{}', asignando TAEKWONDO por defecto",
								grupo.getNombre());
					}

					// Actualizar si es diferente o es null
					if (deporteOriginal == null || deporteOriginal != deporteCorrecto) {
						grupo.setDeporte(deporteCorrecto);
						grupoRepository.save(grupo);
						contador++;

						logger.info("Grupo '{}': {} -> {}",
							grupo.getNombre(),
							deporteOriginal != null ? deporteOriginal : "NULL",
							deporteCorrecto);
					}
				}
			}

			reporte.setExitoso(true);
			reporte.setGruposCorregidos(contador);
			logger.info("=== CORRECCIÓN DE GRUPOS COMPLETADA: {} grupos actualizados ===", contador);

		} catch (Exception e) {
			reporte.setExitoso(false);
			reporte.setError(e.getMessage());
			logger.error("Error durante la corrección de grupos", e);
		}

		return reporte;
	}
}
