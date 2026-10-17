package com.taemoi.project.services.impl;


import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.Categoria;
import com.taemoi.project.entities.Disciplina;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.RolFamiliar;
import com.taemoi.project.repositories.AlumnoDeporteRepository;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.CategoriaRepository;
import com.taemoi.project.services.AlumnoDeporteService;
import com.taemoi.project.services.CatalogoCategoriasService;
import com.taemoi.project.services.CatalogoDisciplinasService;
import com.taemoi.project.services.ProgresionGradosService;
import com.taemoi.project.services.TarifasService;
import com.taemoi.project.utils.FechaUtils;

@Service
@Transactional
public class AlumnoDeporteServiceImpl implements AlumnoDeporteService {

	@Autowired
	private AlumnoDeporteRepository alumnoDeporteRepository;

	@Autowired
	private AlumnoRepository alumnoRepository;

	@Autowired
	private CategoriaRepository categoriaRepository;

	@Autowired
	private CatalogoDisciplinasService catalogo;

	@Autowired
	private ProgresionGradosService progresion;

	@Autowired
	private TarifasService tarifas;

	@Autowired
	private CatalogoCategoriasService categorias;

	/** Entrega 6: alcance del responsable de sede (sin efecto para la gestión). */
	@Autowired
	private com.taemoi.project.acceso.GestionPorSede gestionPorSede;

	/** Disciplina de la organización activa con el código del deporte. */
	private Disciplina disciplinaDe(String deporte) {
		if (deporte == null) {
			throw new IllegalArgumentException("El deporte es obligatorio");
		}
		return catalogo.obtenerPorCodigo(deporte);
	}

	/** Grado con ese código en el sistema de grados de la disciplina. */
	private Grado resolverGrado(String deporte, String codigoGrado) {
		if (!progresion.tieneProgresion(deporte)) {
			throw new IllegalArgumentException("El deporte " + deporte + " no tiene sistema de grados");
		}
		return progresion.buscarGrado(deporte, codigoGrado)
				.orElseThrow(() -> new IllegalArgumentException("Grado no encontrado: " + codigoGrado));
	}

	@Override
	public AlumnoDeporte agregarDeporteAAlumno(Long alumnoId, String deporte, String gradoInicial, Date fechaAlta,
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
		alumnoDeporte.setDisciplina(disciplinaDe(deporte));
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
			Grado grado = resolverGrado(deporte, gradoInicial);
			alumnoDeporte.setGrado(grado);
			// Use provided fechaGrado or default to current date
			alumnoDeporte.setFechaGrado(fechaGrado != null ? fechaGrado : new Date());
		}

		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte agregarDeporteAAlumnoCompleto(Long alumnoId, String deporte, String gradoInicial,
			Date fechaAlta, Date fechaAltaInicial, Date fechaGrado, String codigoTarifa, Double cuantiaTarifa,
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
		alumnoDeporte.setDisciplina(disciplinaDe(deporte));
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
			Grado grado = resolverGrado(deporte, gradoInicial);
			alumnoDeporte.setGrado(grado);
			// Use provided fechaGrado or default to current date
			alumnoDeporte.setFechaGrado(fechaGrado != null ? fechaGrado : new Date());
		}

		// Set per-sport fields: tarifa_id del catálogo y, en paralelo, tipo_tarifa
		tarifas.asignarTarifa(alumnoDeporte, codigoTarifa);
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
		} else if (esCompetidor && categorias.asignaCategoriaPorEdad(deporte)) {
			int edad = FechaUtils.calcularEdad(alumno.getFechaNacimiento());
			Categoria categoriaEntity = asignarCategoriaSegunEdad(deporte, edad);
			alumnoDeporte.setCategoria(categoriaEntity);
		}

		alumnoDeporte.setPeso(peso);
		alumnoDeporte.setFechaPeso(fechaPeso);

		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte establecerDeportePrincipal(Long alumnoId, String deporte) {
		List<AlumnoDeporte> deportesDelAlumno = alumnoDeporteRepository.findByAlumnoId(alumnoId);
		if (deportesDelAlumno.isEmpty()) {
			throw new IllegalArgumentException("El alumno no tiene deportes asignados");
		}

		AlumnoDeporte deportePrincipal = deportesDelAlumno.stream()
				.filter(ad -> java.util.Objects.equals(ad.getDeporte(), deporte))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		if (Boolean.FALSE.equals(deportePrincipal.getActivo())) {
			throw new IllegalArgumentException("No se puede marcar como principal un deporte inactivo");
		}

		for (AlumnoDeporte alumnoDeporte : deportesDelAlumno) {
			alumnoDeporte.setPrincipal(java.util.Objects.equals(alumnoDeporte.getDeporte(), deporte));
		}
		alumnoDeporteRepository.saveAll(deportesDelAlumno);

		return deportePrincipal;
	}

	@Override
	public void desactivarDeporteDeAlumno(Long alumnoId, String deporte) {
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
		alumnoDeporte.setPrincipal(false);
		alumnoDeporte.setFechaBaja(new Date());
		alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public void activarDeporteDeAlumno(Long alumnoId, String deporte) {
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
	public void removerDeporteDeAlumno(Long alumnoId, String deporte) {
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
	public Optional<AlumnoDeporte> obtenerAlumnoDeporte(Long alumnoId, String deporte) {
		return alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<String, Long> obtenerDistribucionActivaPorDeporte() {
		Map<String, Long> distribucion = new LinkedHashMap<>();
		java.util.Optional<java.util.Set<Long>> enAlcance = gestionPorSede.idsAlumnosEnAlcance();
		if (enAlcance.isPresent()) {
			// Responsable de sede: solo sus alumnos (D9), mismo orden y mismas claves que la gestión.
			Map<Long, Long> porDisciplina = new java.util.HashMap<>();
			if (!enAlcance.get().isEmpty()) {
				for (Object[] fila : alumnoDeporteRepository.contarActivosPorDisciplinaDeAlumnos(enAlcance.get())) {
					porDisciplina.put((Long) fila[0], (Long) fila[1]);
				}
			}
			for (Disciplina disciplina : catalogo.listarTodas()) {
				long total = porDisciplina.getOrDefault(disciplina.getId(), 0L);
				if (total > 0) {
					distribucion.put(disciplina.getCodigo(), total);
				}
			}
			return distribucion;
		}
		for (Disciplina disciplina : catalogo.listarTodas()) {
			long total = alumnoDeporteRepository.countByDisciplinaIdAndActivoTrue(disciplina.getId());
			if (total > 0) {
				distribucion.put(disciplina.getCodigo(), total);
			}
		}
		return distribucion;
	}

	@Override
	public AlumnoDeporte actualizarGradoPorDeporte(Long alumnoId, String deporte, String nuevoGrado) {
		return actualizarGradoPorCodigo(alumnoId, deporte, nuevoGrado);
	}

	@Override
	public AlumnoDeporte actualizarGradoPorCodigo(Long alumnoId, String deporte, String nuevoGrado) {
		// Buscar el AlumnoDeporte
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		// Validar que el deporte admite grados (sin sistema de grados: Pilates, Defensa Personal)
		// y buscar el grado en el sistema de su disciplina
		Grado grado = resolverGrado(deporte, nuevoGrado);

		// Actualizar grado y fecha
		alumnoDeporte.setGrado(grado);
		alumnoDeporte.setFechaGrado(new Date());

		// Recalcular aptitud para examen
		boolean aptoParaExamen = esAptoParaExamen(alumnoDeporte);
		alumnoDeporte.setAptoParaExamen(aptoParaExamen);

		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarAptoParaExamen(Long alumnoId, String deporte, Boolean aptoParaExamen) {
		// Buscar el AlumnoDeporte
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		// Actualizar estado de aptitud
		alumnoDeporte.setAptoParaExamen(aptoParaExamen);

		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarFechaGrado(Long alumnoId, String deporte, java.util.Date fechaGrado) {
		// Buscar el AlumnoDeporte
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		// Validar que el deporte admite grados
		if (!progresion.tieneProgresion(deporte)) {
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
	public AlumnoDeporte actualizarFechaAlta(Long alumnoId, String deporte, java.util.Date fechaAlta) {
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		alumnoDeporte.setFechaAlta(fechaAlta);
		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarFechaBaja(Long alumnoId, String deporte, java.util.Date fechaBaja) {
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		alumnoDeporte.setFechaBaja(fechaBaja);
		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarFechaAltaInicial(Long alumnoId, String deporte, java.util.Date fechaAltaInicial) {
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		alumnoDeporte.setFechaAltaInicial(fechaAltaInicial);
		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarTipoTarifa(Long alumnoId, String deporte, String tipoTarifa) {
		return actualizarTarifa(alumnoId, deporte, tipoTarifa == null ? null : tipoTarifa);
	}

	@Override
	public AlumnoDeporte actualizarTarifa(Long alumnoId, String deporte, String codigoTarifa) {
		if (codigoTarifa != null) {
			// Código que no es de la organización: el mismo error que daba TipoTarifa.valueOf
			tarifas.validarCodigo(codigoTarifa);
		}
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		tarifas.asignarTarifa(alumnoDeporte, codigoTarifa);
		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarCuantiaTarifa(Long alumnoId, String deporte, Double cuantiaTarifa) {
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		alumnoDeporte.setCuantiaTarifa(cuantiaTarifa);
		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarRolFamiliar(Long alumnoId, String deporte, String rolFamiliar) {
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
	public AlumnoDeporte actualizarGrupoFamiliar(Long alumnoId, String deporte, String grupoFamiliar) {
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		alumnoDeporte.setGrupoFamiliar(grupoFamiliar);
		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarTieneLicencia(Long alumnoId, String deporte, Boolean tieneLicencia) {
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		alumnoDeporte.setTieneLicencia(tieneLicencia);
		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarNumeroLicencia(Long alumnoId, String deporte, Integer numeroLicencia) {
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		alumnoDeporte.setNumeroLicencia(numeroLicencia);
		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarFechaLicencia(Long alumnoId, String deporte, java.util.Date fechaLicencia) {
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		alumnoDeporte.setFechaLicencia(fechaLicencia);
		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarCompetidor(Long alumnoId, String deporte, Boolean competidor) {
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		// Check if student has license before allowing them to become a competitor
		if (Boolean.TRUE.equals(competidor) && !Boolean.TRUE.equals(alumnoDeporte.getTieneLicencia())) {
			throw new IllegalArgumentException(
					"El alumno debe tener una licencia federativa activa para ser marcado como competidor");
		}

		alumnoDeporte.setCompetidor(competidor);

		// Categoría por edad si la disciplina tiene categorías (en Taemoi, solo taekwondo)
		if (Boolean.TRUE.equals(competidor) && categorias.asignaCategoriaPorEdad(deporte)) {
			Alumno alumno = alumnoDeporte.getAlumno();
			int edad = FechaUtils.calcularEdad(alumno.getFechaNacimiento());
			Categoria categoria = asignarCategoriaSegunEdad(deporte, edad);
			alumnoDeporte.setCategoria(categoria);
		} else if (Boolean.FALSE.equals(competidor)) {
			// If unmarking as competitor, remove categoria
			alumnoDeporte.setCategoria(null);
		}

		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	/**
	 * Categoría de la disciplina según la edad del competidor (catálogo de categorías, V28); null si la
	 * disciplina no tiene ninguna para esa edad.
	 */
	private Categoria asignarCategoriaSegunEdad(String deporte, int edad) {
		return categorias.categoriaPorEdad(deporte, edad).orElse(null);
	}

	@Override
	public AlumnoDeporte actualizarPeso(Long alumnoId, String deporte, Double peso) {
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		alumnoDeporte.setPeso(peso);
		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarFechaPeso(Long alumnoId, String deporte, java.util.Date fechaPeso) {
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		alumnoDeporte.setFechaPeso(fechaPeso);
		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarFechaAltaCompeticion(Long alumnoId, String deporte, java.util.Date fechaAltaCompeticion) {
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		alumnoDeporte.setFechaAltaCompeticion(fechaAltaCompeticion);
		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarFechaAltaCompetidorInicial(Long alumnoId, String deporte, java.util.Date fechaAltaCompetidorInicial) {
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));

		alumnoDeporte.setFechaAltaCompetidorInicial(fechaAltaCompetidorInicial);
		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public AlumnoDeporte actualizarCategoria(Long alumnoId, String deporte, String categoriaNombre) {
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
	public AlumnoDeporte actualizarDatosCompetidor(Long alumnoId, String deporte, Boolean competidor,
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

			// Categoría por edad si la disciplina tiene categorías (en Taemoi, solo taekwondo)
			if (Boolean.TRUE.equals(competidor) && categorias.asignaCategoriaPorEdad(deporte) && categoriaNombre == null) {
				Alumno alumno = alumnoDeporte.getAlumno();
				int edad = FechaUtils.calcularEdad(alumno.getFechaNacimiento());
				Categoria categoria = asignarCategoriaSegunEdad(deporte, edad);
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
				&& categorias.asignaCategoriaPorEdad(deporte)
				&& alumnoDeporte.getCategoria() == null) {
			int edad = FechaUtils.calcularEdad(alumnoDeporte.getAlumno().getFechaNacimiento());
			Categoria categoria = asignarCategoriaSegunEdad(deporte, edad);
			alumnoDeporte.setCategoria(categoria);
		}

		return alumnoDeporteRepository.save(alumnoDeporte);
	}

	@Override
	public String calcularSiguienteGrado(AlumnoDeporte alumnoDeporte) {
		// Validar que tiene grado actual
		if (alumnoDeporte.getGrado() == null) {
			return null;
		}

		String disciplina = alumnoDeporte.getDeporte();

		// Grupo de edad por disciplina.edad_adulto (año natural)
		boolean esMenor = catalogo.esMenorPorAnioNatural(alumnoDeporte.getAlumno().getFechaNacimiento(), disciplina);

		// Siguiente grado del sistema de la disciplina (orden + aplica_a), con los códigos de cada escuela
		return progresion.obtenerSiguienteGrado(disciplina, esMenor, alumnoDeporte.getGrado().getCodigo())
				.map(Grado::getCodigo)
				.orElse(null);
	}

	@Override
	public boolean esAptoParaExamen(AlumnoDeporte alumnoDeporte) {
		// Deportes sin grados no tienen exámenes
		String disciplina = alumnoDeporte.getDeporte();
		if (!progresion.tieneProgresion(disciplina)) {
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

		// Grupo de edad por disciplina.edad_adulto (año natural); meses del grado en su sistema
		Date fechaNacimiento = alumnoDeporte.getAlumno().getFechaNacimiento();
		boolean esMenor = catalogo.esMenorPorAnioNatural(fechaNacimiento, disciplina);
		long mesesRequeridos = progresion.obtenerMesesRequeridos(disciplina, alumnoDeporte.getGrado().getCodigo(),
				esMenor);

		// Verificar si es elegible para examen (sin periodo definido)
		if (mesesRequeridos == Long.MAX_VALUE) {
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
	public List<AlumnoDeporte> obtenerAptosParaExamenPorDeporte(String deporte) {
		return alumnoDeporteRepository.findAptosParaExamenPorDeporte(deporte);
	}

	@Override
	public boolean alumnoTieneDeporte(Long alumnoId, String deporte) {
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
