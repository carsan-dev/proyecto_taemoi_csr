package com.taemoi.project.services.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.taemoi.project.dtos.ProductoAlumnoDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.Producto;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.exceptions.alumno.AlumnoNoEncontradoException;
import com.taemoi.project.exceptions.producto.ProductoNoEncontradoException;
import com.taemoi.project.repositories.AlumnoConvocatoriaRepository;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.repositories.ProductoRepository;
import com.taemoi.project.services.ProductoAlumnoService;
import com.taemoi.project.utils.FechaUtils;
import com.taemoi.project.utils.MensualidadUtils;

@Service
public class ProductoAlumnoServiceImpl implements ProductoAlumnoService {

	private static final String TARIFA_COMPETIDOR_TAEKWONDO = "TARIFA COMPETIDOR TAEKWONDO";
	private static final String TARIFA_COMPETIDOR_KICKBOXING = "TARIFA COMPETIDOR KICKBOXING";
	private static final Double PRECIO_TARIFA_COMPETIDOR = 20.0;
	private static final String LICENCIA_FEDERATIVA_ADULTO = "LICENCIA FEDERATIVA TAEKWONDO ADULTO";
	private static final String LICENCIA_FEDERATIVA_INFANTIL = "LICENCIA FEDERATIVA TAEKWONDO INFANTIL";
	private static final String LICENCIA_FEDERATIVA_DISCAPACIDAD = "LICENCIA FEDERATIVA TAEKWONDO DISCAPACIDAD";
	private static final String LICENCIA_FEDERATIVA_KICKBOXING_ADULTO = "LICENCIA FEDERATIVA KICKBOXING ADULTO";
	private static final String LICENCIA_FEDERATIVA_KICKBOXING_INFANTIL = "LICENCIA FEDERATIVA KICKBOXING INFANTIL";
	private static final String PARTE_PROPORCIONAL_LICENCIA_FEDERATIVA_ADULTO =
			"PARTE PROPORCIONAL LICENCIA FEDERATIVA TAEKWONDO ADULTO";
	private static final String PARTE_PROPORCIONAL_LICENCIA_FEDERATIVA_INFANTIL =
			"PARTE PROPORCIONAL LICENCIA FEDERATIVA TAEKWONDO INFANTIL";
	private static final String PARTE_PROPORCIONAL_LICENCIA_FEDERATIVA_DISCAPACIDAD =
			"PARTE PROPORCIONAL LICENCIA FEDERATIVA TAEKWONDO DISCAPACIDAD";
	@SuppressWarnings("unused")
	private static final int EDAD_LIMITE_INFANTIL_TAEKWONDO = 14;
	@SuppressWarnings("unused")
	private static final int EDAD_LIMITE_INFANTIL_KICKBOXING = 15;
	private static final int MES_CORTE_LICENCIA = 9;
	private static final DateTimeFormatter FORMATO_FECHA_LICENCIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	@Autowired
	private ProductoAlumnoRepository productoAlumnoRepository;

	@Autowired
	private ProductoRepository productoRepository;

	@Autowired
	private AlumnoRepository alumnoRepository;

	@Autowired
	private AlumnoConvocatoriaRepository alumnoConvocatoriaRepository;

	@Autowired
	private com.taemoi.project.repositories.AlumnoDeporteRepository alumnoDeporteRepository;

	@Override
	public ProductoAlumnoDTO asignarProductoAAlumno(Long alumnoId, Long productoId, ProductoAlumnoDTO detallesDTO) {
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado"));

		Producto producto = productoRepository.findById(productoId)
				.orElseThrow(() -> new ProductoNoEncontradoException("Producto no encontrado"));

		ProductoAlumno productoAlumno = new ProductoAlumno();
		productoAlumno.setAlumno(alumno);
		productoAlumno.setProducto(producto);
		productoAlumno.setConcepto(producto.getConcepto());
		productoAlumno.setPrecio(producto.getPrecio());
		productoAlumno.setCantidad(detallesDTO.getCantidad() != null ? detallesDTO.getCantidad() : 1);
		productoAlumno.setPagado(detallesDTO.getPagado() != null ? detallesDTO.getPagado() : false);
		productoAlumno.setFechaAsignacion(new Date());
		productoAlumno.setNotas(detallesDTO.getNotas());

		if (productoAlumno.getPagado()) {
			productoAlumno.setFechaPago(new Date());
		}

		ProductoAlumno savedProductoAlumno = productoAlumnoRepository.save(productoAlumno);
		if (Boolean.TRUE.equals(savedProductoAlumno.getPagado())
				&& (savedProductoAlumno.getConcepto().contains("DERECHO A EXAMEN")
						|| savedProductoAlumno.getConcepto().contains("PASE DE GRADO POR RECOMPENSA"))) {
			Alumno a = savedProductoAlumno.getAlumno();
			a.setTieneDerechoExamen(true);
			alumnoRepository.save(a);
		}

		// Convertir a DTO antes de retornar
		return convertirADTO(savedProductoAlumno);
	}

	@Override
	public List<ProductoAlumnoDTO> obtenerProductosDeAlumno(Long alumnoId) {
		List<ProductoAlumno> productosAlumno = productoAlumnoRepository.findByAlumnoId(alumnoId);
		return productosAlumno.stream().map(this::convertirADTO).collect(Collectors.toList());
	}

	@Override
	public ProductoAlumnoDTO actualizarProductoAlumno(Long id, ProductoAlumnoDTO detallesDTO) {
	    ProductoAlumno productoAlumno = productoAlumnoRepository.findById(id)
	            .orElseThrow(() -> new ProductoNoEncontradoException("ProductoAlumno no encontrado"));

	    if (detallesDTO.getCantidad() != null) {
	        productoAlumno.setCantidad(detallesDTO.getCantidad());
	    }
	    if (detallesDTO.getPrecio() != null) {
	        productoAlumno.setPrecio(detallesDTO.getPrecio());
	    }
	    if (detallesDTO.getConcepto() != null) {
	        productoAlumno.setConcepto(detallesDTO.getConcepto());
	    }
	    if (detallesDTO.getNotas() != null) {
	        productoAlumno.setNotas(detallesDTO.getNotas());
	    }

	    Boolean pagadoAntes = productoAlumno.getPagado();
	    Boolean pagadoAhora = detallesDTO.getPagado();
	    if (pagadoAhora != null && !pagadoAhora.equals(pagadoAntes)) {
	        productoAlumno.setPagado(pagadoAhora);
	        productoAlumno.setFechaPago(pagadoAhora ? new Date() : null);
	    }

	    ProductoAlumno updatedProductoAlumno = productoAlumnoRepository.save(productoAlumno);

	    alumnoConvocatoriaRepository.findByProductoAlumnoId(updatedProductoAlumno.getId())
	        .ifPresent(ac -> {
	            ac.setCuantiaExamen(updatedProductoAlumno.getPrecio());
	            ac.setPagado(updatedProductoAlumno.getPagado());
	            alumnoConvocatoriaRepository.save(ac);
	        });

	    Alumno alumno = updatedProductoAlumno.getAlumno();
	    String concepto = updatedProductoAlumno.getConcepto() != null 
	            ? updatedProductoAlumno.getConcepto() 
	            : "";
	    boolean esProductoExamen = concepto.contains("DERECHO A EXAMEN")
	                             || concepto.contains("PASE DE GRADO POR RECOMPENSA");
	    alumno.setTieneDerechoExamen(esProductoExamen && Boolean.TRUE.equals(updatedProductoAlumno.getPagado()));
	    alumnoRepository.save(alumno);

	    return convertirADTO(updatedProductoAlumno);
	}


	@Override
	public void eliminarProductoAlumno(Long id) {
		ProductoAlumno pa = productoAlumnoRepository.findById(id)
				.orElseThrow(() -> new ProductoNoEncontradoException("ProductoAlumno no encontrado con ID: " + id));

		productoAlumnoRepository.deleteById(id);

		String concepto = pa.getConcepto() != null ? pa.getConcepto() : "";
		if (concepto.contains("DERECHO A EXAMEN") || concepto.contains("PASE DE GRADO POR RECOMPENSA")) {
			Alumno alumno = pa.getAlumno();
			alumno.setTieneDerechoExamen(false);
			alumnoRepository.save(alumno);
		}
	}

	@Override
	public ProductoAlumnoDTO reservarPlaza(Long alumnoId, String concepto, boolean pagado, boolean forzar) {
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado"));

		if (!forzar) {
			boolean existeReserva = alumno.getProductosAlumno().stream()
					.anyMatch(productoAlumno -> concepto.equals(productoAlumno.getConcepto()));

			if (existeReserva) {
				throw new IllegalStateException("Ya existe una reserva de plaza para esta temporada.");
			}
		}

		Producto productoReserva = productoRepository.findByConcepto("RESERVA DE PLAZA")
				.orElseThrow(() -> new ProductoNoEncontradoException("Producto 'RESERVA DE PLAZA' no encontrado"));

		ProductoAlumno productoAlumno = new ProductoAlumno();
		productoAlumno.setAlumno(alumno);
		productoAlumno.setProducto(productoReserva);
		productoAlumno.setConcepto(concepto);
		productoAlumno.setFechaAsignacion(new Date());
		productoAlumno.setCantidad(1);
		productoAlumno.setPrecio(productoReserva.getPrecio());
		productoAlumno.setPagado(pagado);
		if (pagado) {
			productoAlumno.setFechaPago(new Date());
		}
		productoAlumnoRepository.save(productoAlumno);

		return convertirADTO(productoAlumno);
	}

	@Override
	public ProductoAlumnoDTO reservarPlazaPorDeporte(Long alumnoId, String deporte, String concepto, boolean pagado, boolean forzar) {
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado"));

		// Convert string to Deporte enum
		com.taemoi.project.entities.Deporte deporteEnum;
		try {
			deporteEnum = com.taemoi.project.entities.Deporte.valueOf(deporte.toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Deporte no válido: " + deporte);
		}

		// Find alumnoDeporte for this sport
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporteEnum)
				.orElseThrow(() -> new IllegalArgumentException(
					"El alumno no está inscrito en el deporte: " + deporte));

		if (!forzar) {
			// Check if reservation already exists for this exact concepto (includes year + sport)
			// Only check this alumno's products, not all products
			boolean existeReserva = alumno.getProductosAlumno().stream()
					.anyMatch(pa -> concepto.equals(pa.getConcepto()));

			if (existeReserva) {
				throw new IllegalStateException("Ya existe una reserva de plaza para " + deporte + " en esta temporada.");
			}
		}

		Producto productoReserva = productoRepository.findByConcepto("RESERVA DE PLAZA")
				.orElseThrow(() -> new ProductoNoEncontradoException("Producto 'RESERVA DE PLAZA' no encontrado"));

		ProductoAlumno productoAlumno = new ProductoAlumno();
		productoAlumno.setAlumno(alumno);
		productoAlumno.setAlumnoDeporte(alumnoDeporte);
		productoAlumno.setProducto(productoReserva);
		productoAlumno.setConcepto(concepto);
		productoAlumno.setFechaAsignacion(new Date());
		productoAlumno.setCantidad(1);
		productoAlumno.setPrecio(productoReserva.getPrecio());
		productoAlumno.setPagado(pagado);
		if (pagado) {
			productoAlumno.setFechaPago(new Date());
		}
		productoAlumnoRepository.save(productoAlumno);

		return convertirADTO(productoAlumno);
	}

	@Override
	public void cargarMensualidadesGenerales(String mesAno) {
		String nombreMensualidad = MensualidadUtils.formatearNombreMensualidad(mesAno);

		Producto productoMensualidad = productoRepository.findByConcepto("MENSUALIDAD")
				.orElseThrow(() -> new IllegalArgumentException("Producto 'MENSUALIDAD' no encontrado"));

		// Obtener todos los AlumnoDeporte activos
		List<AlumnoDeporte> alumnosDeportes = alumnoDeporteRepository.findAll().stream()
				.filter(ad -> Boolean.TRUE.equals(ad.getActivo()))
				.collect(Collectors.toList());

		// Get existing mensualidades in batch to avoid N+1 queries
		// Ahora buscamos por concepto que contenga el nombreMensualidad (puede tener sufijo de deporte)
		java.util.Set<String> conceptosExistentes = productoAlumnoRepository.findAll().stream()
				.filter(pa -> pa.getConcepto() != null && pa.getConcepto().startsWith(nombreMensualidad))
				.map(pa -> pa.getAlumno().getId() + "-" + pa.getConcepto())
				.collect(java.util.stream.Collectors.toSet());

		Date fechaAsignacion = new Date();
		for (AlumnoDeporte alumnoDeporte : alumnosDeportes) {
			Alumno alumno = alumnoDeporte.getAlumno();
			String conceptoCompleto = nombreMensualidad + " - " + alumnoDeporte.getDeporte().name();
			String claveUnica = alumno.getId() + "-" + conceptoCompleto;

			// Verificar si ya existe esta mensualidad para este alumno y deporte
			if (!conceptosExistentes.contains(claveUnica)) {
				// Usar la tarifa del AlumnoDeporte, con fallback a la del alumno si es null
				Double precioMensualidad = alumnoDeporte.getCuantiaTarifa() != null
						? alumnoDeporte.getCuantiaTarifa()
						: alumno.getCuantiaTarifa();

				ProductoAlumno productoAlumno = new ProductoAlumno();
				productoAlumno.setAlumno(alumno);
				productoAlumno.setProducto(productoMensualidad);
				productoAlumno.setAlumnoDeporte(alumnoDeporte);
				productoAlumno.setConcepto(conceptoCompleto);
				productoAlumno.setPrecio(precioMensualidad);
				productoAlumno.setFechaAsignacion(fechaAsignacion);
				productoAlumno.setCantidad(1);
				productoAlumno.setPagado(false);

				productoAlumnoRepository.save(productoAlumno);

				// Añadir tarifa competidor si es Taekwondo o Kickboxing y es competidor
				if ((alumnoDeporte.getDeporte() == com.taemoi.project.entities.Deporte.TAEKWONDO
						|| alumnoDeporte.getDeporte() == com.taemoi.project.entities.Deporte.KICKBOXING)
						&& Boolean.TRUE.equals(alumnoDeporte.getCompetidor())) {
					asignarTarifaCompetidorPorDeporte(alumno, alumnoDeporte, mesAno, fechaAsignacion);
				}
			}
		}
	}

	@Override
	public void cargarMensualidadesPorDeporte(String mesAno, String deporte) {
		String nombreMensualidad = MensualidadUtils.formatearNombreMensualidad(mesAno);

		// Convert string to Deporte enum
		com.taemoi.project.entities.Deporte deporteEnum;
		try {
			deporteEnum = com.taemoi.project.entities.Deporte.valueOf(deporte.toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Deporte no válido: " + deporte +
					". Valores válidos: TAEKWONDO, KICKBOXING, PILATES, DEFENSA_PERSONAL_FEMENINA");
		}

		Producto productoMensualidad = productoRepository.findByConcepto("MENSUALIDAD")
				.orElseThrow(() -> new IllegalArgumentException("Producto 'MENSUALIDAD' no encontrado"));

		// Obtener todos los AlumnoDeporte activos del deporte especificado
		List<AlumnoDeporte> alumnosDeportes = alumnoDeporteRepository.findAll().stream()
				.filter(ad -> Boolean.TRUE.equals(ad.getActivo()) && ad.getDeporte() == deporteEnum)
				.collect(Collectors.toList());

		String conceptoCompleto = nombreMensualidad + " - " + deporteEnum.name();

		// Get existing mensualidades in batch to avoid N+1 queries
		java.util.Set<Long> alumnosConMensualidad = productoAlumnoRepository.findAll().stream()
				.filter(pa -> conceptoCompleto.equalsIgnoreCase(pa.getConcepto()) && pa.getAlumno() != null)
				.map(pa -> pa.getAlumno().getId())
				.collect(java.util.stream.Collectors.toSet());

		Date fechaAsignacion = new Date();
		for (AlumnoDeporte alumnoDeporte : alumnosDeportes) {
			Alumno alumno = alumnoDeporte.getAlumno();

			if (!alumnosConMensualidad.contains(alumno.getId())) {
				// Usar la tarifa del AlumnoDeporte, con fallback a la del alumno si es null
				Double precioMensualidad = alumnoDeporte.getCuantiaTarifa() != null
						? alumnoDeporte.getCuantiaTarifa()
						: alumno.getCuantiaTarifa();

				ProductoAlumno productoAlumno = new ProductoAlumno();
				productoAlumno.setAlumno(alumno);
				productoAlumno.setProducto(productoMensualidad);
				productoAlumno.setAlumnoDeporte(alumnoDeporte);
				productoAlumno.setConcepto(conceptoCompleto);
				productoAlumno.setPrecio(precioMensualidad);
				productoAlumno.setFechaAsignacion(fechaAsignacion);
				productoAlumno.setCantidad(1);
				productoAlumno.setPagado(false);

				productoAlumnoRepository.save(productoAlumno);

				// Añadir tarifa competidor si es Taekwondo o Kickboxing y es competidor
				if ((deporteEnum == com.taemoi.project.entities.Deporte.TAEKWONDO
						|| deporteEnum == com.taemoi.project.entities.Deporte.KICKBOXING)
						&& Boolean.TRUE.equals(alumnoDeporte.getCompetidor())) {
					asignarTarifaCompetidorPorDeporte(alumno, alumnoDeporte, mesAno, fechaAsignacion);
				}
			}
		}
	}

	@Override
	public void cargarMensualidadIndividual(Long alumnoId, String mesAno, boolean forzar) {
		String nombreMensualidad = MensualidadUtils.formatearNombreMensualidad(mesAno);
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new IllegalArgumentException("Alumno no encontrado"));
		Producto productoMensualidad = productoRepository.findByConcepto("MENSUALIDAD")
				.orElseThrow(() -> new IllegalArgumentException("Producto 'MENSUALIDAD' no encontrado"));

		// Obtener todos los deportes activos del alumno
		List<AlumnoDeporte> deportesActivos = alumnoDeporteRepository.findByAlumnoId(alumnoId).stream()
				.filter(ad -> Boolean.TRUE.equals(ad.getActivo()))
				.collect(Collectors.toList());

		if (deportesActivos.isEmpty()) {
			throw new IllegalStateException("El alumno no tiene deportes activos asignados");
		}

		Date fechaAsignacion = new Date();
		boolean algunoCreado = false;
		StringBuilder mensajesExistentes = new StringBuilder();

		// Crear una mensualidad por cada deporte activo
		for (AlumnoDeporte alumnoDeporte : deportesActivos) {
			String conceptoCompleto = nombreMensualidad + " - " + alumnoDeporte.getDeporte().name();

			// Verificar si ya existe esta mensualidad para este deporte
			boolean yaExiste = alumno.getProductosAlumno().stream()
					.anyMatch(pa -> pa.getConcepto().equalsIgnoreCase(conceptoCompleto));

			if (yaExiste && !forzar) {
				if (mensajesExistentes.length() > 0) {
					mensajesExistentes.append(", ");
				}
				mensajesExistentes.append(alumnoDeporte.getDeporte().name());
				continue;
			}

			// Usar la tarifa del AlumnoDeporte, con fallback a la del alumno si es null
			Double precioMensualidad = alumnoDeporte.getCuantiaTarifa() != null
					? alumnoDeporte.getCuantiaTarifa()
					: alumno.getCuantiaTarifa();

			ProductoAlumno productoAlumno = new ProductoAlumno();
			productoAlumno.setAlumno(alumno);
			productoAlumno.setProducto(productoMensualidad);
			productoAlumno.setAlumnoDeporte(alumnoDeporte);
			productoAlumno.setConcepto(conceptoCompleto);
			productoAlumno.setPrecio(precioMensualidad);
			productoAlumno.setFechaAsignacion(fechaAsignacion);
			productoAlumno.setCantidad(1);
			productoAlumno.setPagado(false);

			productoAlumnoRepository.save(productoAlumno);
			algunoCreado = true;

			// Añadir tarifa competidor si es Taekwondo o Kickboxing y es competidor
			if ((alumnoDeporte.getDeporte() == com.taemoi.project.entities.Deporte.TAEKWONDO
					|| alumnoDeporte.getDeporte() == com.taemoi.project.entities.Deporte.KICKBOXING)
					&& Boolean.TRUE.equals(alumnoDeporte.getCompetidor())) {
				asignarTarifaCompetidorPorDeporte(alumno, alumnoDeporte, mesAno, fechaAsignacion);
			}
		}

		// Si no se creó ninguna mensualidad y había existentes, lanzar excepción
		if (!algunoCreado && mensajesExistentes.length() > 0) {
			throw new IllegalStateException("El alumno ya tiene asignada la mensualidad para: " + mensajesExistentes);
		}
	}

	@Override
	public void cargarLicenciasGenerales(int ano, String deporte) {
		boolean esSegundaMitadDelAno = esSegundaMitadDelAno();
		LocalDate fechaInicio = obtenerFechaInicioLicencia(ano, esSegundaMitadDelAno);
		LocalDate fechaFin = LocalDate.of(ano, 12, 31);

		List<AlumnoDeporte> alumnosDeportes = obtenerAlumnosDeportesLicencia(deporte);
		Set<String> conceptosExistentes = obtenerConceptosLicenciaExistentes();
		Date fechaAsignacion = Date.from(fechaInicio.atStartOfDay(ZoneId.systemDefault()).toInstant());

		for (AlumnoDeporte alumnoDeporte : alumnosDeportes) {
			Alumno alumno = alumnoDeporte.getAlumno();
			Producto productoLicencia = obtenerProductoLicencia(alumno, esSegundaMitadDelAno, alumnoDeporte.getDeporte(), ano);
			String concepto = construirConceptoLicencia(
					productoLicencia.getConcepto(),
					fechaInicio,
					fechaFin);
			String clave = alumno.getId() + "-" + concepto;

			if (conceptosExistentes.contains(clave)) {
				continue;
			}

			ProductoAlumno productoAlumno = new ProductoAlumno();
			productoAlumno.setAlumno(alumno);
			productoAlumno.setAlumnoDeporte(alumnoDeporte);
			productoAlumno.setProducto(productoLicencia);
			productoAlumno.setConcepto(concepto);
			productoAlumno.setPrecio(productoLicencia.getPrecio());
			productoAlumno.setFechaAsignacion(fechaAsignacion);
			productoAlumno.setCantidad(1);
			productoAlumno.setPagado(false);

			productoAlumnoRepository.save(productoAlumno);
		}
	}

	@Override
	public void cargarLicenciaIndividual(Long alumnoId, int ano, String deporte, boolean forzar) {
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new IllegalArgumentException("Alumno no encontrado"));

		if (!Boolean.TRUE.equals(alumno.getActivo())) {
			throw new IllegalArgumentException("El alumno no está activo.");
		}

		List<AlumnoDeporte> deportesObjetivo = obtenerDeportesAlumnoParaLicencia(alumnoId, deporte);
		if (deportesObjetivo.isEmpty()) {
			throw new IllegalArgumentException("El alumno no tiene deportes activos con licencia federativa en los deportes seleccionados.");
		}

		boolean esSegundaMitadDelAno = esSegundaMitadDelAno();
		LocalDate fechaInicio = obtenerFechaInicioLicencia(ano, esSegundaMitadDelAno);
		LocalDate fechaFin = LocalDate.of(ano, 12, 31);
		Date fechaAsignacion = Date.from(fechaInicio.atStartOfDay(ZoneId.systemDefault()).toInstant());

		boolean algunoCreado = false;
		StringBuilder mensajesExistentes = new StringBuilder();

		for (AlumnoDeporte alumnoDeporte : deportesObjetivo) {
			Producto productoLicencia = obtenerProductoLicencia(alumno, esSegundaMitadDelAno, alumnoDeporte.getDeporte(), ano);
			String concepto = construirConceptoLicencia(
					productoLicencia.getConcepto(),
					fechaInicio,
					fechaFin);

			boolean yaExiste = alumno.getProductosAlumno().stream()
					.anyMatch(pa -> pa.getConcepto() != null && concepto.equalsIgnoreCase(pa.getConcepto()));

			if (yaExiste && !forzar) {
				if (mensajesExistentes.length() > 0) {
					mensajesExistentes.append(", ");
				}
				mensajesExistentes.append(alumnoDeporte.getDeporte().name());
				continue;
			}

			ProductoAlumno productoAlumno = new ProductoAlumno();
			productoAlumno.setAlumno(alumno);
			productoAlumno.setAlumnoDeporte(alumnoDeporte);
			productoAlumno.setProducto(productoLicencia);
			productoAlumno.setConcepto(concepto);
			productoAlumno.setPrecio(productoLicencia.getPrecio());
			productoAlumno.setFechaAsignacion(fechaAsignacion);
			productoAlumno.setCantidad(1);
			productoAlumno.setPagado(false);

			productoAlumnoRepository.save(productoAlumno);
			algunoCreado = true;
		}

		if (!algunoCreado && mensajesExistentes.length() > 0) {
			throw new IllegalStateException("El alumno ya tiene asignada la licencia para: " + mensajesExistentes);
		}
	}

	@Override
	public void crearAltaLicenciaFederativa(Alumno alumno) {
		List<AlumnoDeporte> deportesConLicencia = alumno.getDeportes().stream()
				.filter(ad -> Boolean.TRUE.equals(ad.getTieneLicencia()) && ad.getFechaLicencia() != null)
				.collect(Collectors.toList());

		AlumnoDeporte deporteBase = null;
		Date fechaLicencia = null;
		if (!deportesConLicencia.isEmpty()) {
			deporteBase = deportesConLicencia.get(0);
			fechaLicencia = deporteBase.getFechaLicencia();
		} else if (Boolean.TRUE.equals(alumno.getTieneLicencia()) && alumno.getFechaLicencia() != null) {
			fechaLicencia = alumno.getFechaLicencia();
			deporteBase = alumno.getDeportes().stream()
					.filter(ad -> Boolean.TRUE.equals(ad.getActivo()))
					.filter(ad -> deporteRequiereLicencia(ad.getDeporte()))
					.findFirst()
					.orElse(null);
		} else {
			return;
		}
		LocalDate fechaLicenciaLocal = fechaLicencia.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		String mesEnEspanol = fechaLicenciaLocal.getMonth().getDisplayName(TextStyle.FULL, Locale.of("es", "ES"))
				.toUpperCase();
		String mesAnio = mesEnEspanol + " " + fechaLicenciaLocal.getYear();

		boolean esSegundaMitadDelAno = fechaLicenciaLocal.getMonthValue() >= MES_CORTE_LICENCIA;
		Deporte deporteLicencia = deporteBase != null ? deporteBase.getDeporte() : Deporte.TAEKWONDO;
		Producto productoLicencia;
		try {
			productoLicencia = obtenerProductoLicencia(alumno, esSegundaMitadDelAno, deporteLicencia, fechaLicenciaLocal.getYear());
		} catch (ProductoNoEncontradoException ex) {
			System.out.println("ADVERTENCIA: " + ex.getMessage()
					+ ". Se omitirá la creación automática de licencia para el alumno " + alumno.getId());
			return;
		}

		Double precio = productoLicencia.getPrecio();
		if (precio == null) {
			precio = calcularPrecioLicenciaFallback(alumno, esSegundaMitadDelAno, deporteLicencia, fechaLicenciaLocal.getYear());
		}

		ProductoAlumno productoAlumno = new ProductoAlumno();
		productoAlumno.setAlumno(alumno);
		if (deporteBase != null) {
			productoAlumno.setAlumnoDeporte(deporteBase);
		}
		productoAlumno.setProducto(productoLicencia);
		productoAlumno.setConcepto("ALTA LICENCIA FEDERATIVA " + mesAnio);
		productoAlumno.setCantidad(1);
		productoAlumno.setPrecio(precio);
		productoAlumno.setFechaAsignacion(fechaLicencia);
		productoAlumno.setPagado(true);

		productoAlumnoRepository.save(productoAlumno);
	}


	@Override
	public ProductoAlumnoDTO renovarLicencia(Long alumnoId) {
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + alumnoId));

		LocalDate fechaActual = LocalDate.now();
		boolean esSegundaMitadDelAno = fechaActual.getMonthValue() >= MES_CORTE_LICENCIA;
		List<AlumnoDeporte> deportesActualizar = alumno.getDeportes().stream()
				.filter(ad -> Boolean.TRUE.equals(ad.getActivo()) && Boolean.TRUE.equals(ad.getTieneLicencia()))
				.collect(Collectors.toList());
		if (deportesActualizar.isEmpty()) {
			deportesActualizar = alumno.getDeportes().stream()
					.filter(ad -> Boolean.TRUE.equals(ad.getActivo()))
					.limit(1)
					.collect(Collectors.toList());
		}
		AlumnoDeporte deporteBase = deportesActualizar.isEmpty() ? null : deportesActualizar.get(0);
		Deporte deporteLicencia = deporteBase != null ? deporteBase.getDeporte() : Deporte.TAEKWONDO;
		Producto productoLicencia = obtenerProductoLicencia(alumno, esSegundaMitadDelAno, deporteLicencia, fechaActual.getYear());

		String mesEnEspanol = fechaActual.getMonth().getDisplayName(TextStyle.FULL, Locale.of("es", "ES"))
				.toUpperCase();

		Double precio = productoLicencia.getPrecio();
		if (precio == null) {
			precio = calcularPrecioLicenciaFallback(alumno, esSegundaMitadDelAno, deporteLicencia, fechaActual.getYear());
		}

		ProductoAlumno productoAlumno = new ProductoAlumno();
		productoAlumno.setAlumno(alumno);
		productoAlumno.setProducto(productoLicencia);
		productoAlumno.setConcepto(
				"RENOVACION " + productoLicencia.getConcepto() + " " + mesEnEspanol + " " + fechaActual.getYear());
		productoAlumno.setCantidad(1);
		productoAlumno.setPrecio(precio);
		productoAlumno.setFechaAsignacion(Date.from(fechaActual.atStartOfDay(ZoneId.systemDefault()).toInstant()));
		productoAlumno.setPagado(true);

		if (deporteBase != null) {
			productoAlumno.setAlumnoDeporte(deporteBase);
		}

		ProductoAlumno savedProductoAlumno = productoAlumnoRepository.save(productoAlumno);

		Date nuevaFechaLicencia = Date.from(fechaActual.atStartOfDay(ZoneId.systemDefault()).toInstant());
		for (AlumnoDeporte alumnoDeporte : deportesActualizar) {
			if (!Boolean.TRUE.equals(alumnoDeporte.getTieneLicencia())) {
				alumnoDeporte.setTieneLicencia(true);
			}
			alumnoDeporte.setFechaLicencia(nuevaFechaLicencia);
			alumnoDeporteRepository.save(alumnoDeporte);
		}

		return convertirADTO(savedProductoAlumno);
	}


	private boolean esSegundaMitadDelAno() {
		return LocalDate.now().getMonthValue() >= MES_CORTE_LICENCIA;
	}

	private LocalDate obtenerFechaInicioLicencia(int ano, boolean esSegundaMitadDelAno) {
		return esSegundaMitadDelAno ? LocalDate.of(ano, MES_CORTE_LICENCIA, 1) : LocalDate.of(ano, 1, 1);
	}

	private Producto obtenerProductoLicencia(Alumno alumno, boolean esSegundaMitadDelAno, Deporte deporte, int anioReferencia) {
		String concepto;
		if (deporte == Deporte.KICKBOXING) {
			boolean esMenor = FechaUtils.esMenor(alumno.getFechaNacimiento(), deporte, anioReferencia);
			concepto = esMenor
					? LICENCIA_FEDERATIVA_KICKBOXING_INFANTIL
					: LICENCIA_FEDERATIVA_KICKBOXING_ADULTO;
		} else if (Boolean.TRUE.equals(alumno.getTieneDiscapacidad())) {
			concepto = esSegundaMitadDelAno
					? PARTE_PROPORCIONAL_LICENCIA_FEDERATIVA_DISCAPACIDAD
					: LICENCIA_FEDERATIVA_DISCAPACIDAD;
		} else {
			boolean esMenor = FechaUtils.esMenor(alumno.getFechaNacimiento(), deporte, anioReferencia);
			if (esMenor) {
				concepto = esSegundaMitadDelAno
						? PARTE_PROPORCIONAL_LICENCIA_FEDERATIVA_INFANTIL
						: LICENCIA_FEDERATIVA_INFANTIL;
			} else {
				concepto = esSegundaMitadDelAno
						? PARTE_PROPORCIONAL_LICENCIA_FEDERATIVA_ADULTO
						: LICENCIA_FEDERATIVA_ADULTO;
			}
		}

		return productoRepository.findByConcepto(concepto)
				.orElseThrow(() -> new ProductoNoEncontradoException("Producto '" + concepto + "' no encontrado"));
	}

	private double calcularPrecioLicenciaFallback(Alumno alumno, boolean esSegundaMitadDelAno, Deporte deporte, int anioReferencia) {
		if (Boolean.TRUE.equals(alumno.getTieneDiscapacidad())) {
			return esSegundaMitadDelAno ? 20.0 : 30.0;
		}

		// Usar la regla de edad correcta según el deporte
		boolean esMenor = FechaUtils.esMenor(alumno.getFechaNacimiento(), deporte, anioReferencia);
		if (esMenor) {
			return esSegundaMitadDelAno ? 20.0 : 35.0;
		}
		return esSegundaMitadDelAno ? 35.0 : 46.0;
	}

	private String construirConceptoLicencia(
			String conceptoProducto,
			LocalDate fechaInicio,
			LocalDate fechaFin) {
		String rango = "DEL " + fechaInicio.format(FORMATO_FECHA_LICENCIA)
				+ " AL " + fechaFin.format(FORMATO_FECHA_LICENCIA);
		return conceptoProducto + " " + rango;
	}

	

	private List<AlumnoDeporte> obtenerAlumnosDeportesLicencia(String deporte) {
		List<AlumnoDeporte> alumnosDeportes;
		if (deporte == null || "TODOS".equalsIgnoreCase(deporte)) {
			alumnosDeportes = alumnoDeporteRepository.findActivosByDeporteIn(
					List.of(Deporte.TAEKWONDO, Deporte.KICKBOXING));
		} else {
			Deporte deporteEnum = parseDeporteLicencia(deporte);
			alumnosDeportes = alumnoDeporteRepository.findActivosByDeporteWithAlumno(deporteEnum);
		}

		return alumnosDeportes.stream()
				.filter(ad -> Boolean.TRUE.equals(ad.getActivo()))
				.filter(ad -> ad.getAlumno() != null && Boolean.TRUE.equals(ad.getAlumno().getActivo()))
				.collect(Collectors.toList());
	}

	private List<AlumnoDeporte> obtenerDeportesAlumnoParaLicencia(Long alumnoId, String deporte) {
		if (deporte == null || "TODOS".equalsIgnoreCase(deporte)) {
			return alumnoDeporteRepository.findByAlumnoId(alumnoId).stream()
					.filter(ad -> Boolean.TRUE.equals(ad.getActivo())
							&& deporteRequiereLicencia(ad.getDeporte())
							&& ad.getAlumno() != null
							&& Boolean.TRUE.equals(ad.getAlumno().getActivo()))
					.collect(Collectors.toList());
		}

		Deporte deporteEnum = parseDeporteLicencia(deporte);
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporteEnum)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no practica el deporte: " + deporte));

		if (!Boolean.TRUE.equals(alumnoDeporte.getActivo())) {
			throw new IllegalArgumentException("El alumno no está activo en el deporte: " + deporte);
		}
		if (alumnoDeporte.getAlumno() != null && !Boolean.TRUE.equals(alumnoDeporte.getAlumno().getActivo())) {
			throw new IllegalArgumentException("El alumno no está activo.");
		}
		return List.of(alumnoDeporte);
	}

	private Deporte parseDeporteLicencia(String deporte) {
		Deporte deporteEnum;
		try {
			deporteEnum = Deporte.valueOf(deporte.toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Deporte no v\u00e1lido: " + deporte +
					". Valores v\u00e1lidos: TAEKWONDO, KICKBOXING");
		}

		if (!deporteRequiereLicencia(deporteEnum)) {
			throw new IllegalArgumentException("Deporte no v\u00e1lido para licencias: " + deporte);
		}

		return deporteEnum;
	}

	private boolean deporteRequiereLicencia(Deporte deporte) {
		return deporte == Deporte.TAEKWONDO || deporte == Deporte.KICKBOXING;
	}

	private Set<String> obtenerConceptosLicenciaExistentes() {
		return productoAlumnoRepository.findAll().stream()
				.filter(pa -> pa.getConcepto() != null && pa.getConcepto().contains("LICENCIA FEDERATIVA"))
				.map(pa -> {
					Long alumnoId = obtenerAlumnoIdProducto(pa);
					return alumnoId != null ? alumnoId + "-" + pa.getConcepto() : null;
				})
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
	}

	private Long obtenerAlumnoIdProducto(ProductoAlumno productoAlumno) {
		if (productoAlumno.getAlumno() != null) {
			return productoAlumno.getAlumno().getId();
		}
		if (productoAlumno.getAlumnoDeporte() != null && productoAlumno.getAlumnoDeporte().getAlumno() != null) {
			return productoAlumno.getAlumnoDeporte().getAlumno().getId();
		}
		return null;
	}

	private ProductoAlumnoDTO convertirADTO(ProductoAlumno productoAlumno) {
		ProductoAlumnoDTO dto = new ProductoAlumnoDTO();
		dto.setId(productoAlumno.getId());
		Alumno alumno = productoAlumno.getAlumno();
		if (alumno == null && productoAlumno.getAlumnoDeporte() != null) {
			alumno = productoAlumno.getAlumnoDeporte().getAlumno();
		}
		if (alumno != null) {
			dto.setAlumnoId(alumno.getId());
		}
		Producto producto = productoAlumno.getProducto();
		if (producto != null) {
			dto.setProductoId(producto.getId());
		} else {
			dto.setProductoId(null);
		}
		dto.setConcepto(productoAlumno.getConcepto());
		dto.setPrecio(productoAlumno.getPrecio());
		dto.setCantidad(productoAlumno.getCantidad());
		dto.setPagado(productoAlumno.getPagado());
		dto.setFechaAsignacion(productoAlumno.getFechaAsignacion());
		dto.setFechaPago(productoAlumno.getFechaPago());
		dto.setNotas(productoAlumno.getNotas());
		return dto;
	}

	// ===== IMPLEMENTACIÓN MÉTODOS MULTI-DEPORTE =====

	@Override
	public ProductoAlumnoDTO asignarProductoAAlumnoDeporte(Long alumnoId, Long productoId, String deporte, ProductoAlumnoDTO detallesDTO) {
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado"));

		Producto producto = productoRepository.findById(productoId)
				.orElseThrow(() -> new ProductoNoEncontradoException("Producto no encontrado"));

		// Convertir string a Deporte enum
		com.taemoi.project.entities.Deporte deporteEnum;
		try {
			deporteEnum = com.taemoi.project.entities.Deporte.valueOf(deporte.toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Deporte no válido: " + deporte +
					". Valores válidos: TAEKWONDO, KICKBOXING, PILATES, DEFENSA_PERSONAL_FEMENINA");
		}

		// Buscar AlumnoDeporte correspondiente
		com.taemoi.project.entities.AlumnoDeporte alumnoDeporte = alumnoDeporteRepository
				.findByAlumnoIdAndDeporte(alumnoId, deporteEnum)
				.orElseThrow(() -> new IllegalArgumentException(
					"El alumno no practica el deporte " + deporte));

		ProductoAlumno productoAlumno = new ProductoAlumno();
		productoAlumno.setAlumno(alumno);
		productoAlumno.setProducto(producto);
		productoAlumno.setAlumnoDeporte(alumnoDeporte);
		productoAlumno.setConcepto(producto.getConcepto());
		productoAlumno.setPrecio(producto.getPrecio());
		productoAlumno.setCantidad(detallesDTO.getCantidad() != null ? detallesDTO.getCantidad() : 1);
		productoAlumno.setPagado(detallesDTO.getPagado() != null ? detallesDTO.getPagado() : false);
		productoAlumno.setFechaAsignacion(new Date());
		productoAlumno.setNotas(detallesDTO.getNotas());

		if (productoAlumno.getPagado()) {
			productoAlumno.setFechaPago(new Date());
		}

		ProductoAlumno savedProductoAlumno = productoAlumnoRepository.save(productoAlumno);

		return convertirADTO(savedProductoAlumno);
	}

	@Override
	public void cargarMensualidadesMultiDeporte(String mesAno) {
		String nombreMensualidad = MensualidadUtils.formatearNombreMensualidad(mesAno);
		Producto productoMensualidad = productoRepository.findByConcepto("MENSUALIDAD")
				.orElseThrow(() -> new IllegalArgumentException("Producto 'MENSUALIDAD' no encontrado"));

		// Obtener todos los AlumnoDeporte activos
		List<com.taemoi.project.entities.AlumnoDeporte> alumnosDeportes =
			alumnoDeporteRepository.findAll().stream()
				.filter(ad -> ad.getActivo() != null && ad.getActivo())
				.collect(Collectors.toList());

		Date fechaAsignacion = new Date();
		for (com.taemoi.project.entities.AlumnoDeporte alumnoDeporte : alumnosDeportes) {
			Alumno alumno = alumnoDeporte.getAlumno();
			String conceptoCompleto = nombreMensualidad + " - " + alumnoDeporte.getDeporte().name();

			// Verificar si ya existe esta mensualidad para este deporte
			boolean yaExiste = productoAlumnoRepository.findByAlumnoId(alumno.getId()).stream()
					.anyMatch(pa -> pa.getConcepto().equalsIgnoreCase(conceptoCompleto));

			if (!yaExiste) {
				// Usar la tarifa del AlumnoDeporte, con fallback a la del alumno si es null
				Double precioMensualidad = alumnoDeporte.getCuantiaTarifa() != null
						? alumnoDeporte.getCuantiaTarifa()
						: alumno.getCuantiaTarifa();

				ProductoAlumno productoAlumno = new ProductoAlumno();
				productoAlumno.setAlumno(alumno);
				productoAlumno.setProducto(productoMensualidad);
				productoAlumno.setAlumnoDeporte(alumnoDeporte);
				productoAlumno.setConcepto(conceptoCompleto);
				productoAlumno.setPrecio(precioMensualidad);
				productoAlumno.setFechaAsignacion(fechaAsignacion);
				productoAlumno.setCantidad(1);
				productoAlumno.setPagado(false);

				productoAlumnoRepository.save(productoAlumno);

				// Añadir tarifa competidor si es Taekwondo o Kickboxing y es competidor
				if ((alumnoDeporte.getDeporte() == com.taemoi.project.entities.Deporte.TAEKWONDO
						|| alumnoDeporte.getDeporte() == com.taemoi.project.entities.Deporte.KICKBOXING)
						&& Boolean.TRUE.equals(alumnoDeporte.getCompetidor())) {
					asignarTarifaCompetidorPorDeporte(alumno, alumnoDeporte, mesAno, fechaAsignacion);
				}
			}
		}
	}

	@Override
	public void cargarMensualidadIndividualPorDeporte(Long alumnoId, String deporte, String mesAno, boolean forzar) {
		String nombreMensualidad = MensualidadUtils.formatearNombreMensualidad(mesAno);

		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new IllegalArgumentException("Alumno no encontrado"));

		// Convertir string a Deporte enum
		com.taemoi.project.entities.Deporte deporteEnum;
		try {
			deporteEnum = com.taemoi.project.entities.Deporte.valueOf(deporte.toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Deporte no válido: " + deporte +
					". Valores válidos: TAEKWONDO, KICKBOXING, PILATES, DEFENSA_PERSONAL_FEMENINA");
		}

		// Buscar AlumnoDeporte correspondiente
		com.taemoi.project.entities.AlumnoDeporte alumnoDeporte = alumnoDeporteRepository
				.findByAlumnoIdAndDeporte(alumnoId, deporteEnum)
				.orElseThrow(() -> new IllegalArgumentException(
					"El alumno no practica el deporte " + deporte));

		Producto productoMensualidad = productoRepository.findByConcepto("MENSUALIDAD")
				.orElseThrow(() -> new IllegalArgumentException("Producto 'MENSUALIDAD' no encontrado"));

		String conceptoCompleto = nombreMensualidad + " - " + deporteEnum.name();

		boolean yaExiste = productoAlumnoRepository.findByAlumnoId(alumno.getId()).stream()
				.anyMatch(pa -> pa.getConcepto().equalsIgnoreCase(conceptoCompleto));

		if (yaExiste && !forzar) {
			throw new IllegalStateException("El alumno ya tiene asignada la " + conceptoCompleto);
		}

		// Usar la tarifa del AlumnoDeporte, con fallback a la del alumno si es null
		Double precioMensualidad = alumnoDeporte.getCuantiaTarifa() != null
				? alumnoDeporte.getCuantiaTarifa()
				: alumno.getCuantiaTarifa();

		Date fechaAsignacion = new Date();
		ProductoAlumno productoAlumno = new ProductoAlumno();
		productoAlumno.setAlumno(alumno);
		productoAlumno.setProducto(productoMensualidad);
		productoAlumno.setAlumnoDeporte(alumnoDeporte);
		productoAlumno.setConcepto(conceptoCompleto);
		productoAlumno.setPrecio(precioMensualidad);
		productoAlumno.setFechaAsignacion(fechaAsignacion);
		productoAlumno.setCantidad(1);
		productoAlumno.setPagado(false);

		productoAlumnoRepository.save(productoAlumno);

		// Añadir tarifa competidor si es Taekwondo o Kickboxing y es competidor
		if ((deporteEnum == com.taemoi.project.entities.Deporte.TAEKWONDO
				|| deporteEnum == com.taemoi.project.entities.Deporte.KICKBOXING)
				&& Boolean.TRUE.equals(alumnoDeporte.getCompetidor())) {
			asignarTarifaCompetidorPorDeporte(alumno, alumnoDeporte, mesAno, fechaAsignacion);
		}
	}

	// ===== MÉTODOS AUXILIARES PARA TARIFA COMPETIDOR =====

	/**
	 * Asigna la tarifa competidor a un alumno para un deporte específico (Taekwondo o Kickboxing).
	 */
	private void asignarTarifaCompetidorPorDeporte(Alumno alumno, AlumnoDeporte alumnoDeporte,
			String mesAno, Date fechaAsignacion) {
		// Determinar el nombre del producto según el deporte
		String nombreProducto;
		if (alumnoDeporte.getDeporte() == com.taemoi.project.entities.Deporte.TAEKWONDO) {
			nombreProducto = TARIFA_COMPETIDOR_TAEKWONDO;
		} else if (alumnoDeporte.getDeporte() == com.taemoi.project.entities.Deporte.KICKBOXING) {
			nombreProducto = TARIFA_COMPETIDOR_KICKBOXING;
		} else {
			// No aplicar tarifa competidor para otros deportes
			return;
		}

		String conceptoTarifaCompetidor = nombreProducto + " " +
				MensualidadUtils.formatearNombreMensualidad(mesAno).replace("MENSUALIDAD ", "");

		// Verificar si ya existe esta tarifa competidor para este mes
		boolean yaExiste = productoAlumnoRepository.findByAlumnoId(alumno.getId()).stream()
				.anyMatch(pa -> pa.getConcepto().equalsIgnoreCase(conceptoTarifaCompetidor));

		if (!yaExiste) {
			Producto productoTarifaCompetidor = productoRepository.findByConcepto(nombreProducto)
					.orElseGet(() -> {
						// Crear el producto si no existe
						Producto nuevo = new Producto();
						nuevo.setConcepto(nombreProducto);
						nuevo.setPrecio(PRECIO_TARIFA_COMPETIDOR);
						return productoRepository.save(nuevo);
					});

			ProductoAlumno productoAlumno = new ProductoAlumno();
			productoAlumno.setAlumno(alumno);
			productoAlumno.setProducto(productoTarifaCompetidor);
			productoAlumno.setAlumnoDeporte(alumnoDeporte);
			productoAlumno.setConcepto(conceptoTarifaCompetidor);
			productoAlumno.setPrecio(PRECIO_TARIFA_COMPETIDOR);
			productoAlumno.setFechaAsignacion(fechaAsignacion);
			productoAlumno.setCantidad(1);
			productoAlumno.setPagado(false);

			productoAlumnoRepository.save(productoAlumno);
		}
	}
}
