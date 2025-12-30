package com.taemoi.project.services.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.taemoi.project.dtos.ProductoAlumnoDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoDeporte;
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
	public void crearAltaLicenciaFederativa(Alumno alumno) {
		// Check if product exists - if not, skip license creation instead of failing
		var optionalProducto = productoRepository.findByConcepto("LICENCIA FEDERATIVA");
		if (optionalProducto.isEmpty()) {
			// Log warning but don't fail - allows system to work without this product configured
			System.out.println("ADVERTENCIA: Producto 'LICENCIA FEDERATIVA' no encontrado. " +
					"Se omitirá la creación automática de licencia para el alumno " + alumno.getId());
			return;
		}

		Producto productoLicencia = optionalProducto.get();

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
		} else {
			return;
		}
		LocalDate fechaLicenciaLocal = fechaLicencia.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		String mesEnEspanol = fechaLicenciaLocal.getMonth().getDisplayName(TextStyle.FULL, Locale.of("es", "ES"))
				.toUpperCase();
		String mesAnio = mesEnEspanol + " " + fechaLicenciaLocal.getYear();

		boolean esSegundaMitadDelAno = fechaLicenciaLocal.getMonthValue() >= 9;

		double precio;
		if (Boolean.TRUE.equals(alumno.getTieneDiscapacidad())) {
			precio = esSegundaMitadDelAno ? 20.0 : 30.0;
		} else {
			int edad = FechaUtils.calcularEdad(alumno.getFechaNacimiento());
			if (edad < 14) {
				precio = esSegundaMitadDelAno ? 20.0 : 35.0;
			} else {
				precio = esSegundaMitadDelAno ? 35.0 : 46.0;
			}
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

		Producto productoLicencia = productoRepository.findByConcepto("LICENCIA FEDERATIVA")
				.orElseThrow(() -> new ProductoNoEncontradoException("El producto 'LICENCIA FEDERATIVA' no existe."));

		LocalDate fechaActual = LocalDate.now();
		boolean esSegundaMitadDelAno = fechaActual.getMonthValue() >= 9;

		String mesEnEspanol = fechaActual.getMonth().getDisplayName(TextStyle.FULL, Locale.of("es", "ES"))
				.toUpperCase();

		double precio;
		if (Boolean.TRUE.equals(alumno.getTieneDiscapacidad())) {
			precio = esSegundaMitadDelAno ? 20.0 : 30.0;
		} else {
			int edad = FechaUtils.calcularEdad(alumno.getFechaNacimiento());
			if (edad < 14) {
				precio = esSegundaMitadDelAno ? 20.0 : 35.0;
			} else {
				precio = esSegundaMitadDelAno ? 35.0 : 46.0;
			}
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

		List<AlumnoDeporte> deportesActualizar = alumno.getDeportes().stream()
				.filter(ad -> Boolean.TRUE.equals(ad.getActivo()) && Boolean.TRUE.equals(ad.getTieneLicencia()))
				.collect(Collectors.toList());
		if (deportesActualizar.isEmpty()) {
			deportesActualizar = alumno.getDeportes().stream()
					.filter(ad -> Boolean.TRUE.equals(ad.getActivo()))
					.limit(1)
					.collect(Collectors.toList());
		}

		if (!deportesActualizar.isEmpty()) {
			productoAlumno.setAlumnoDeporte(deportesActualizar.get(0));
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
		dto.setProductoId(productoAlumno.getProducto().getId());
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
