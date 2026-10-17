package com.taemoi.project.services.impl;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.taemoi.project.dtos.ProductoAlumnoDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.Producto;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.entities.TipoProducto;
import com.taemoi.project.exceptions.alumno.AlumnoNoEncontradoException;
import com.taemoi.project.exceptions.producto.ProductoNoEncontradoException;
import com.taemoi.project.exceptions.producto.MensualidadIncompletaException;
import com.taemoi.project.repositories.AlumnoConvocatoriaRepository;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.repositories.ProductoRepository;
import com.taemoi.project.services.AlumnoDeporteService;
import com.taemoi.project.services.CatalogoLegadoTaemoi;
import com.taemoi.project.services.CatalogoDisciplinasService;
import com.taemoi.project.services.ProductoAlumnoService;
import com.taemoi.project.services.ProductosCatalogoService;
import com.taemoi.project.services.ProgresionGradosService;
import com.taemoi.project.utils.MensualidadUtils;

@Service
public class ProductoAlumnoServiceImpl implements ProductoAlumnoService {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ProductoAlumnoServiceImpl.class);

	/** Nombre que daba el código hasta V27 al producto de competidor (si no hay producto que lo diga). */
	private static final String CONCEPTO_LEGADO_COMPETIDOR = "TARIFA COMPETIDOR ";
	private static final int MES_CORTE_LICENCIA = 9;
	private static final DateTimeFormatter FORMATO_FECHA_LICENCIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	@Autowired
	private com.taemoi.project.services.ComportamientoLegadoService comportamientoLegado;

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

	@Autowired
	private AlumnoDeporteService alumnoDeporteService;

	@Autowired
	private CatalogoDisciplinasService catalogoDisciplinas;

	@Autowired
	private ProductosCatalogoService productosCatalogo;

	@Autowired
	private ProgresionGradosService progresionGrados;

	/** Entrega 6-3: sede inactiva (D6-17) y restricción del responsable de sede. */
	@Autowired
	private com.taemoi.project.acceso.GestionPorSede gestionPorSede;

	private String normalizarConcepto(String concepto) {
		return concepto == null ? "" : Normalizer.normalize(concepto, Normalizer.Form.NFD)
				.replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT);
	}

	private boolean esMatricula(String concepto) {
		return normalizarConcepto(concepto).matches(".*\\bMATRICULA\\b.*");
	}


	/**
	 * Si el cargo da derecho a examen: el concepto que miraba el código hasta V27 o, además, un producto
	 * de tipo EXAMEN con código PROPIO (otras escuelas). Los códigos heredados de Taemoi solo por concepto,
	 * como antes ({@link CatalogoLegadoTaemoi#tipoDeCatalogo}).
	 */
	private boolean daDerechoAExamen(ProductoAlumno productoAlumno) {
		String concepto = productoAlumno.getConcepto() != null ? productoAlumno.getConcepto() : "";
		if (concepto.contains("DERECHO A EXAMEN") || concepto.contains("PASE DE GRADO POR RECOMPENSA")) {
			return true;
		}
		Producto producto = productoAlumno.getProducto();
		return CatalogoLegadoTaemoi.tipoDeCatalogo(producto, legado()) == TipoProducto.EXAMEN;
	}

	@Override
	public ProductoAlumnoDTO asignarProductoAAlumno(Long alumnoId, Long productoId, ProductoAlumnoDTO detallesDTO) {
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado"));

		Producto producto = productoRepository.findById(productoId)
				.orElseThrow(() -> new ProductoNoEncontradoException("Producto no encontrado"));
		exigirSedeActivaParaCargo(alumno);

		if (esMatricula(producto.getConcepto())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Seleccione un deporte del alumno para asignar la matrícula.");
		}

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
		if (Boolean.TRUE.equals(savedProductoAlumno.getPagado()) && daDerechoAExamen(savedProductoAlumno)) {
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
		// Entrega 6-3 (D6-9): el responsable de sede ve solo los cargos de sus sedes. Gestión: todos, como siempre.
		java.util.Optional<Set<Long>> suyas = gestionPorSede.restriccion()
				.map(com.taemoi.project.organizacion.AlcanceSesion::sedes);
		if (suyas.isPresent()) {
			productosAlumno = productosAlumno.stream()
					.filter(pa -> pa.getSedeId() != null && suyas.get().contains(pa.getSedeId()))
					.collect(Collectors.toList());
		}
		return productosAlumno.stream().map(this::convertirADTO).collect(Collectors.toList());
	}

	@Override
	public ProductoAlumnoDTO actualizarProductoAlumno(Long id, ProductoAlumnoDTO detallesDTO) {
		ProductoAlumno productoAlumno = productoAlumnoRepository.findById(id)
				.orElseThrow(() -> new ProductoNoEncontradoException("ProductoAlumno no encontrado"));
		exigirEdicionPermitidaAlResponsable(productoAlumno, detallesDTO);

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
		if (detallesDTO.getFechaAsignacion() != null) {
			productoAlumno.setFechaAsignacion(detallesDTO.getFechaAsignacion());
		}

		boolean pagadoAntes = Boolean.TRUE.equals(productoAlumno.getPagado());
		Boolean pagadoSolicitado = detallesDTO.getPagado();
		boolean hayCambioEstadoPago = pagadoSolicitado != null && pagadoSolicitado != pagadoAntes;

		if (hayCambioEstadoPago) {
			if (pagadoAntes && !pagadoSolicitado && !usuarioActualEsAdmin()) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo los administradores pueden revertir cobros pagados.");
			}
			productoAlumno.setPagado(pagadoSolicitado);
		}

		if (Boolean.TRUE.equals(productoAlumno.getPagado())) {
			if (detallesDTO.getFechaPago() != null) {
				productoAlumno.setFechaPago(detallesDTO.getFechaPago());
			} else if (hayCambioEstadoPago && Boolean.TRUE.equals(pagadoSolicitado)) {
				productoAlumno.setFechaPago(new Date());
			} else if (productoAlumno.getFechaPago() == null) {
				productoAlumno.setFechaPago(new Date());
			}
		} else {
			productoAlumno.setFechaPago(null);
		}

		ProductoAlumno updatedProductoAlumno = productoAlumnoRepository.save(productoAlumno);

		alumnoConvocatoriaRepository.findByProductoAlumnoId(updatedProductoAlumno.getId())
				.ifPresent(ac -> {
					ac.setCuantiaExamen(updatedProductoAlumno.getPrecio());
					ac.setPagado(updatedProductoAlumno.getPagado());
					alumnoConvocatoriaRepository.save(ac);
				});

		Alumno alumno = updatedProductoAlumno.getAlumno();
		boolean esProductoExamen = daDerechoAExamen(updatedProductoAlumno);
		alumno.setTieneDerechoExamen(esProductoExamen && Boolean.TRUE.equals(updatedProductoAlumno.getPagado()));
		alumnoRepository.save(alumno);

		if (Boolean.TRUE.equals(updatedProductoAlumno.getPagado()) && !pagadoAntes) {
			activarGradoPorRecompensaPagada(updatedProductoAlumno);
		}

		return convertirADTO(updatedProductoAlumno);
	}

	/** Cargo de una inscripción sin {@code alumno_id}: el código de cobro de siempre no lo admite (ver D6-20). */
	private static final String CARGO_SIN_ALUMNO = "El cargo no tiene alumno asociado: lo gestiona la administración.";

	/**
	 * Entrega 6-3, solo para el responsable de sede (la gestión, como siempre):
	 * <ul>
	 * <li>D6-20: en un cargo YA COBRADO solo puede cambiar las notas; cualquier otro campo que llegue con un valor
	 * distinto del guardado (precio, cantidad, concepto, fechas o deshacer el pago) → 403.</li>
	 * <li>Un cargo sin {@code alumno_id} (solo inscripción) → 400 antes de guardar nada: el resto del método no lo
	 * admite (NullPointerException al sincronizar el derecho a examen, comportamiento anterior a la entrega 6).</li>
	 * </ul>
	 */
	private void exigirEdicionPermitidaAlResponsable(ProductoAlumno cargo, ProductoAlumnoDTO cambios) {
		if (gestionPorSede.restriccion().isEmpty()) {
			return;
		}
		if (Boolean.TRUE.equals(cargo.getPagado()) && cambiaAlgoMasQueLasNotas(cargo, cambios)) {
			throw new org.springframework.security.access.AccessDeniedException("Acceso denegado.");
		}
		if (cargo.getAlumno() == null) {
			throw new IllegalArgumentException(CARGO_SIN_ALUMNO);
		}
	}

	private static boolean cambiaAlgoMasQueLasNotas(ProductoAlumno cargo, ProductoAlumnoDTO cambios) {
		return (cambios.getCantidad() != null && !cambios.getCantidad().equals(cargo.getCantidad()))
				|| (cambios.getPrecio() != null
						&& (cargo.getPrecio() == null || Double.compare(cambios.getPrecio(), cargo.getPrecio()) != 0))
				|| (cambios.getConcepto() != null && !cambios.getConcepto().equals(cargo.getConcepto()))
				|| (cambios.getFechaAsignacion() != null && !mismoInstante(cambios.getFechaAsignacion(),
						cargo.getFechaAsignacion()))
				|| (cambios.getFechaPago() != null && !mismoInstante(cambios.getFechaPago(), cargo.getFechaPago()))
				|| Boolean.FALSE.equals(cambios.getPagado());
	}

	private static boolean mismoInstante(Date a, Date b) {
		return b != null && a.getTime() == b.getTime();
	}

	private boolean usuarioActualEsAdmin() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || authentication.getAuthorities() == null) {
			return false;
		}

		for (GrantedAuthority authority : authentication.getAuthorities()) {
			if ("ROLE_ADMIN".equalsIgnoreCase(authority.getAuthority())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Promueve al alumno al grado que concede el producto pagado (ver
	 * {@link ProductosCatalogoService#buscarCodigoGradoConcedido}), resuelto por código en el sistema de
	 * grados de la disciplina de la inscripción. Si esa disciplina no tiene un grado con ese código (o
	 * no tiene grados), no promueve y lo registra: el cobro nunca falla por el grado.
	 */
	private void activarGradoPorRecompensaPagada(ProductoAlumno productoAlumno) {
		try {
			activarGradoConcedido(productoAlumno);
		} catch (RuntimeException e) {
			// El cobro ya está guardado: un fallo al resolver el grado en el catálogo no lo deshace.
			LOG.warn("Cobro {} pagado: no se pudo resolver el grado que concede ({}); no se promueve",
					productoAlumno.getId(), e.toString());
		}
	}

	private void activarGradoConcedido(ProductoAlumno productoAlumno) {
		Long productoId = productoAlumno.getProducto() != null ? productoAlumno.getProducto().getId() : null;
		String codigoGrado = productosCatalogo
				.buscarCodigoGradoConcedido(productoId, productoAlumno.getConcepto())
				.orElse(null);

		if (codigoGrado == null) {
			return;
		}

		AlumnoDeporte alumnoDeporte = productoAlumno.getAlumnoDeporte();
		if (alumnoDeporte == null || alumnoDeporte.getAlumno() == null) {
			return;
		}

		String disciplina = alumnoDeporte.getDeporte() == null ? null : alumnoDeporte.getDeporte();
		if (progresionGrados.buscarGrado(disciplina, codigoGrado).isEmpty()) {
			LOG.warn("Cobro {} pagado: la disciplina {} no tiene el grado {}; no se promueve al alumno {}",
					productoAlumno.getId(), disciplina, codigoGrado, alumnoDeporte.getAlumno().getId());
			return;
		}

		alumnoDeporteService.actualizarGradoPorCodigo(
				alumnoDeporte.getAlumno().getId(),
				alumnoDeporte.getDeporte(),
				codigoGrado
		);
	}


	@Override
	public void eliminarProductoAlumno(Long id) {
		ProductoAlumno pa = productoAlumnoRepository.findById(id)
				.orElseThrow(() -> new ProductoNoEncontradoException("ProductoAlumno no encontrado con ID: " + id));
		// Entrega 6-3 (D6-22): borrar un cargo cobrado equivale a revertir el cobro, que el responsable de sede no
		// puede (entrega6-plan.md §7). La gestión, como siempre.
		if (gestionPorSede.restriccion().isPresent()) {
			if (Boolean.TRUE.equals(pa.getPagado())) {
				throw new org.springframework.security.access.AccessDeniedException("Acceso denegado.");
			}
			if (pa.getAlumno() == null && daDerechoAExamen(pa)) {
				throw new IllegalArgumentException(CARGO_SIN_ALUMNO);
			}
		}

		productoAlumnoRepository.deleteById(id);

		if (daDerechoAExamen(pa)) {
			Alumno alumno = pa.getAlumno();
			alumno.setTieneDerechoExamen(false);
			alumnoRepository.save(alumno);
		}
	}

	@Override
	public ProductoAlumnoDTO reservarPlaza(Long alumnoId, String concepto, boolean pagado, boolean forzar) {
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado"));

		exigirSedeActivaParaCargo(alumno);
		if (!forzar) {
			boolean existeReserva = alumno.getProductosAlumno().stream()
					.anyMatch(productoAlumno -> concepto.equals(productoAlumno.getConcepto()));

			if (existeReserva) {
				throw new IllegalStateException("Ya existe una reserva de plaza para esta temporada.");
			}
		}

		Producto productoReserva = productosCatalogo.resolverProductoReservaPlaza(null)
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
		String deporteEnum;
		try {
			deporteEnum = catalogoDisciplinas.validarCodigo(deporte.toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Deporte no válido: " + deporte);
		}

		// Find alumnoDeporte for this sport
		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporteEnum)
				.orElseThrow(() -> new IllegalArgumentException(
					"El alumno no está inscrito en el deporte: " + deporte));

		exigirSedeActivaParaCargo(alumno);
		if (!forzar) {
			// Check if reservation already exists for this exact concepto (includes year + sport)
			// Only check this alumno's products, not all products
			boolean existeReserva = alumno.getProductosAlumno().stream()
					.anyMatch(pa -> concepto.equals(pa.getConcepto()));

			if (existeReserva) {
				throw new IllegalStateException("Ya existe una reserva de plaza para " + deporte + " en esta temporada.");
			}
		}

		Producto productoReserva = productosCatalogo.resolverProductoReservaPlaza(deporteEnum)
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
	public int cargarMensualidadesGenerales(String mesAno, LocalDate fechaAsignacionManual) {
		MensualidadUtils.formatearNombreMensualidad(mesAno); // valida el formato antes de nada

		// Obtener todos los AlumnoDeporte activos
		List<AlumnoDeporte> alumnosDeportes = alumnoDeporteRepository.findAll().stream()
				.filter(ad -> Boolean.TRUE.equals(ad.getActivo()))
				.collect(Collectors.toList());
		Map<String, Producto> productosMensualidad = resolverProductosMensualidad(alumnosDeportes);

		// Cargos existentes en lote (sin N+1): se buscan por clave exacta alumno-CONCEPTO
		Set<String> conceptosExistentes = clavesDeCargosExistentes();

		Date fechaAsignacion = resolverFechaAsignacion(fechaAsignacionManual);
		Set<Long> sedesInactivas = gestionPorSede.sedesInactivas();
		Set<Long> omitidos = new java.util.HashSet<>();
		for (AlumnoDeporte alumnoDeporte : alumnosDeportes) {
			Alumno alumno = alumnoDeporte.getAlumno();
			if (enSedeInactiva(alumno, sedesInactivas)) {
				omitidos.add(alumno.getId());
				continue;
			}
			Producto productoMensualidad = productosMensualidad.get(codigoDisciplina(alumnoDeporte));
			String nombreMensualidad = nombreMensualidad(productoMensualidad, mesAno);
			String conceptoCompleto = nombreMensualidad + " - " + alumnoDeporte.getDeporte();
			String claveUnica = alumno.getId() + "-" + conceptoCompleto.toUpperCase(Locale.ROOT);

			// Verificar si ya existe esta mensualidad para este alumno y deporte
			boolean mensualidadExiste = conceptosExistentes.contains(claveUnica);
			if (!mensualidadExiste) {
				Double precioMensualidad = alumnoDeporte.getCuantiaTarifa();

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
				conceptosExistentes.add(claveUnica);
			}

			if (aplicaTarifaCompetidor(alumnoDeporte)) {
				String conceptoTarifaCompetidor = construirConceptoTarifaCompetidor(alumnoDeporte.getDeporte(), mesAno);
				if (conceptoTarifaCompetidor != null) {
					String claveTarifa = alumno.getId() + "-" + conceptoTarifaCompetidor.toUpperCase(Locale.ROOT);
					if (!conceptosExistentes.contains(claveTarifa)) {
						asignarTarifaCompetidorPorDeporte(alumno, alumnoDeporte, mesAno, fechaAsignacion);
						conceptosExistentes.add(claveTarifa);
					}
				}
			}
		}
		return omitidos.size();
	}

	@Override
	public int cargarMensualidadesPorDeporte(String mesAno, String deporte, LocalDate fechaAsignacionManual) {
		MensualidadUtils.formatearNombreMensualidad(mesAno); // valida el formato antes de nada

		// Convert string to Deporte enum
		String deporteEnum;
		try {
			deporteEnum = catalogoDisciplinas.validarCodigo(deporte.toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Deporte no válido: " + deporte +
					". Valores válidos: " + String.join(", ", codigosDisciplinas()));
		}

		Producto productoMensualidad = resolverProductoMensualidad(deporteEnum);

		// Obtener todos los AlumnoDeporte activos del deporte especificado
		List<AlumnoDeporte> alumnosDeportes = alumnoDeporteRepository.findAll().stream()
				.filter(ad -> Boolean.TRUE.equals(ad.getActivo()) && java.util.Objects.equals(ad.getDeporte(), deporteEnum))
				.collect(Collectors.toList());

		String conceptoCompleto = nombreMensualidad(productoMensualidad, mesAno) + " - " + deporteEnum;

		// Cargos existentes en lote (sin N+1): se buscan por clave exacta alumno-CONCEPTO
		Set<String> conceptosExistentes = clavesDeCargosExistentes();

		Date fechaAsignacion = resolverFechaAsignacion(fechaAsignacionManual);
		Set<Long> sedesInactivas = gestionPorSede.sedesInactivas();
		Set<Long> omitidos = new java.util.HashSet<>();
		for (AlumnoDeporte alumnoDeporte : alumnosDeportes) {
			Alumno alumno = alumnoDeporte.getAlumno();
			if (enSedeInactiva(alumno, sedesInactivas)) {
				omitidos.add(alumno.getId());
				continue;
			}

			String claveMensualidad = alumno.getId() + "-" + conceptoCompleto.toUpperCase(Locale.ROOT);
			boolean mensualidadExiste = conceptosExistentes.contains(claveMensualidad);

			if (!mensualidadExiste) {
				Double precioMensualidad = alumnoDeporte.getCuantiaTarifa();

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
				conceptosExistentes.add(claveMensualidad);
			}

			if (aplicaTarifaCompetidor(alumnoDeporte)) {
				String conceptoTarifaCompetidor = construirConceptoTarifaCompetidor(alumnoDeporte.getDeporte(), mesAno);
				if (conceptoTarifaCompetidor != null) {
					String claveTarifa = alumno.getId() + "-" + conceptoTarifaCompetidor.toUpperCase(Locale.ROOT);
					if (!conceptosExistentes.contains(claveTarifa)) {
						asignarTarifaCompetidorPorDeporte(alumno, alumnoDeporte, mesAno, fechaAsignacion);
						conceptosExistentes.add(claveTarifa);
					}
				}
			}
		}
		return omitidos.size();
	}

	@Override
	public void cargarMensualidadIndividual(Long alumnoId, String mesAno, boolean forzar,
			LocalDate fechaAsignacionManual) {
		MensualidadUtils.formatearNombreMensualidad(mesAno); // valida el formato antes de nada
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new IllegalArgumentException("Alumno no encontrado"));
		exigirSedeActivaParaCargo(alumno);

		// Obtener todos los deportes activos del alumno
		List<AlumnoDeporte> deportesActivos = alumnoDeporteRepository.findByAlumnoId(alumnoId).stream()
				.filter(ad -> Boolean.TRUE.equals(ad.getActivo()))
				.collect(Collectors.toList());

		// Producto(s) antes que nada (el error de producto ausente va primero, como antes de V27)
		Map<String, Producto> productosMensualidad = resolverProductosMensualidad(deportesActivos);

		if (deportesActivos.isEmpty()) {
			throw new IllegalStateException("El alumno no tiene deportes activos asignados");
		}

		Set<String> conceptosExistentes = alumno.getProductosAlumno().stream()
				.filter(pa -> pa.getConcepto() != null)
				.map(pa -> pa.getConcepto().trim().toUpperCase(Locale.ROOT))
				.collect(Collectors.toSet());

		List<String> mensualidadesExistentes = new ArrayList<>();
		List<String> mensualidadesFaltantes = new ArrayList<>();
		List<String> tarifasCompetidorExistentes = new ArrayList<>();
		List<String> tarifasCompetidorFaltantes = new ArrayList<>();

		for (AlumnoDeporte alumnoDeporte : deportesActivos) {
			String deporteNombre = alumnoDeporte.getDeporte();
			String nombreMensualidad = nombreMensualidad(productosMensualidad.get(codigoDisciplina(alumnoDeporte)), mesAno);
			String conceptoMensualidad = (nombreMensualidad + " - " + deporteNombre).toUpperCase(Locale.ROOT);
			boolean mensualidadExiste = conceptosExistentes.contains(conceptoMensualidad);
			boolean tarifaAplica = aplicaTarifaCompetidor(alumnoDeporte);
			boolean tarifaExiste = false;

			if (tarifaAplica) {
				String conceptoTarifaCompetidor = construirConceptoTarifaCompetidor(alumnoDeporte.getDeporte(), mesAno);
				tarifaExiste = conceptoTarifaCompetidor != null
						&& conceptosExistentes.contains(conceptoTarifaCompetidor.toUpperCase(Locale.ROOT));
			}

			if (mensualidadExiste && tarifaAplica && !tarifaExiste) {
				mensualidadesExistentes.add(deporteNombre);
				tarifasCompetidorFaltantes.add(deporteNombre);
			} else if (!mensualidadExiste && tarifaAplica && tarifaExiste) {
				mensualidadesFaltantes.add(deporteNombre);
				tarifasCompetidorExistentes.add(deporteNombre);
			}
		}

		if (!forzar && (!mensualidadesExistentes.isEmpty() || !mensualidadesFaltantes.isEmpty()
				|| !tarifasCompetidorExistentes.isEmpty() || !tarifasCompetidorFaltantes.isEmpty())) {
			throw new MensualidadIncompletaException(
					"El alumno tiene productos de mensualidad incompletos.",
					mensualidadesExistentes,
					mensualidadesFaltantes,
					tarifasCompetidorExistentes,
					tarifasCompetidorFaltantes);
		}

		Date fechaAsignacion = resolverFechaAsignacion(fechaAsignacionManual);
		boolean algunoCreado = false;
		boolean tarifaCreada = false;
		StringBuilder mensajesExistentes = new StringBuilder();

		// Crear una mensualidad por cada deporte activo
		for (AlumnoDeporte alumnoDeporte : deportesActivos) {
			Producto productoMensualidad = productosMensualidad.get(codigoDisciplina(alumnoDeporte));
			String conceptoCompleto = nombreMensualidad(productoMensualidad, mesAno) + " - "
					+ alumnoDeporte.getDeporte();

			// Verificar si ya existe esta mensualidad para este deporte
			boolean yaExiste = conceptosExistentes.contains(conceptoCompleto.toUpperCase(Locale.ROOT));

			if (yaExiste) {
				if (mensajesExistentes.length() > 0) {
					mensajesExistentes.append(", ");
				}
				mensajesExistentes.append(alumnoDeporte.getDeporte());
			} else {
				Double precioMensualidad = alumnoDeporte.getCuantiaTarifa();

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
			}

			if (aplicaTarifaCompetidor(alumnoDeporte)) {
				String conceptoTarifaCompetidor = construirConceptoTarifaCompetidor(alumnoDeporte.getDeporte(), mesAno);
				boolean tarifaExiste = conceptoTarifaCompetidor != null
						&& conceptosExistentes.contains(conceptoTarifaCompetidor.toUpperCase(Locale.ROOT));
				if (!tarifaExiste) {
					asignarTarifaCompetidorPorDeporte(alumno, alumnoDeporte, mesAno, fechaAsignacion);
					tarifaCreada = true;
				}
			}
		}

		// Si no se creó ninguna mensualidad y había existentes, lanzar excepción
		if (!algunoCreado && !tarifaCreada && mensajesExistentes.length() > 0 && !forzar) {
			throw new IllegalStateException("El alumno ya tiene asignada la mensualidad para: " + mensajesExistentes);
		}
	}

	@Override
	public int cargarLicenciasGenerales(int ano, String deporte, LocalDate fechaAsignacionManual) {
		boolean esSegundaMitadDelAno = esSegundaMitadDelAno();
		LocalDate fechaInicio = obtenerFechaInicioLicencia(ano, esSegundaMitadDelAno);
		LocalDate fechaFin = LocalDate.of(ano, 12, 31);

		List<AlumnoDeporte> alumnosDeportes = obtenerAlumnosDeportesLicencia(deporte);
		Set<String> conceptosExistentes = obtenerConceptosLicenciaExistentes();
		Date fechaAsignacion = resolverFechaAsignacion(fechaAsignacionManual);

		Set<Long> sedesInactivas = gestionPorSede.sedesInactivas();
		Set<Long> omitidos = new java.util.HashSet<>();
		for (AlumnoDeporte alumnoDeporte : alumnosDeportes) {
			Alumno alumno = alumnoDeporte.getAlumno();
			if (enSedeInactiva(alumno, sedesInactivas)) {
				omitidos.add(alumno.getId());
				continue;
			}
			Producto productoLicencia = obtenerProductoLicencia(alumno, esSegundaMitadDelAno, alumnoDeporte.getDeporte(), ano);
			String concepto = construirConceptoLicencia(
					CatalogoLegadoTaemoi.conceptoBase(productoLicencia, null, legado()),
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
		return omitidos.size();
	}

	@Override
	public void cargarLicenciaIndividual(Long alumnoId, int ano, String deporte, boolean forzar,
			LocalDate fechaAsignacionManual) {
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new IllegalArgumentException("Alumno no encontrado"));

		if (!Boolean.TRUE.equals(alumno.getActivo())) {
			throw new IllegalArgumentException("El alumno no está activo.");
		}
		exigirSedeActivaParaCargo(alumno);

		List<AlumnoDeporte> deportesObjetivo = obtenerDeportesAlumnoParaLicencia(alumnoId, deporte);
		if (deportesObjetivo.isEmpty()) {
			throw new IllegalArgumentException("El alumno no tiene deportes activos con licencia federativa en los deportes seleccionados.");
		}

		boolean esSegundaMitadDelAno = esSegundaMitadDelAno();
		LocalDate fechaInicio = obtenerFechaInicioLicencia(ano, esSegundaMitadDelAno);
		LocalDate fechaFin = LocalDate.of(ano, 12, 31);
		Date fechaAsignacion = resolverFechaAsignacion(fechaAsignacionManual);

		boolean algunoCreado = false;
		StringBuilder mensajesExistentes = new StringBuilder();

		for (AlumnoDeporte alumnoDeporte : deportesObjetivo) {
			Producto productoLicencia = obtenerProductoLicencia(alumno, esSegundaMitadDelAno, alumnoDeporte.getDeporte(), ano);
			String concepto = construirConceptoLicencia(
					CatalogoLegadoTaemoi.conceptoBase(productoLicencia, null, legado()),
					fechaInicio,
					fechaFin);

			boolean yaExiste = alumno.getProductosAlumno().stream()
					.anyMatch(pa -> pa.getConcepto() != null && concepto.equalsIgnoreCase(pa.getConcepto()));

			if (yaExiste && !forzar) {
				if (mensajesExistentes.length() > 0) {
					mensajesExistentes.append(", ");
				}
				mensajesExistentes.append(alumnoDeporte.getDeporte());
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
		} else {
			return;
		}
		LocalDate fechaLicenciaLocal = fechaLicencia.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		String mesEnEspanol = fechaLicenciaLocal.getMonth().getDisplayName(TextStyle.FULL, Locale.of("es", "ES"))
				.toUpperCase();
		String mesAnio = mesEnEspanol + " " + fechaLicenciaLocal.getYear();

		boolean esSegundaMitadDelAno = fechaLicenciaLocal.getMonthValue() >= MES_CORTE_LICENCIA;
		String deporteLicencia = deporteBase != null ? deporteBase.getDeporte() : CatalogoLegadoTaemoi.TAEKWONDO;
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
		exigirSedeActivaParaCargo(alumno);

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
		String deporteLicencia = deporteBase != null ? deporteBase.getDeporte() : CatalogoLegadoTaemoi.TAEKWONDO;
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
				"RENOVACION " + CatalogoLegadoTaemoi.conceptoBase(productoLicencia, null, legado()) + " " + mesEnEspanol + " " + fechaActual.getYear());
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


	/**
	 * D6-17 (entrega 6-3): un cargo nuevo va a la sede principal del alumno ({@code ProductoAlumno @PrePersist}); si
	 * esa sede está inactiva, 400. Sin sede principal no se comprueba. Cobrar los cargos existentes sigue permitido.
	 */
	private void exigirSedeActivaParaCargo(Alumno alumno) {
		gestionPorSede.exigirSedeActiva(alumno.getSedePrincipalId());
	}

	/** D6-17 en las generaciones masivas: el alumno es de una sede inactiva (se salta, sin detener el resto). */
	private static boolean enSedeInactiva(Alumno alumno, Set<Long> sedesInactivas) {
		return !sedesInactivas.isEmpty() && alumno != null && alumno.getSedePrincipalId() != null
				&& sedesInactivas.contains(alumno.getSedePrincipalId());
	}

	private Date resolverFechaAsignacion(LocalDate fechaAsignacion) {
		if (fechaAsignacion == null) {
			return new Date();
		}
		return Date.from(fechaAsignacion.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant());
	}

	private boolean esSegundaMitadDelAno() {
		return LocalDate.now().getMonthValue() >= MES_CORTE_LICENCIA;
	}

	private LocalDate obtenerFechaInicioLicencia(int ano, boolean esSegundaMitadDelAno) {
		return esSegundaMitadDelAno ? LocalDate.of(ano, MES_CORTE_LICENCIA, 1) : LocalDate.of(ano, 1, 1);
	}

	/**
	 * Producto de licencia por codigo del catálogo (variante por discapacidad, edad y parte proporcional
	 * desde septiembre; ver {@link ProductosCatalogoService#resolverProductoLicencia}).
	 */
	private Producto obtenerProductoLicencia(Alumno alumno, boolean esSegundaMitadDelAno, String deporte, int anioReferencia) {
		String disciplina = deporte == null ? null : deporte;
		boolean discapacidad = Boolean.TRUE.equals(alumno.getTieneDiscapacidad());
		boolean esMenor = catalogoDisciplinas.esMenorPorAnioNatural(alumno.getFechaNacimiento(), disciplina,
				anioReferencia);
		return productosCatalogo.resolverProductoLicencia(disciplina, discapacidad, esMenor, esSegundaMitadDelAno)
				.orElseThrow(() -> new ProductoNoEncontradoException("Producto '"
						+ productosCatalogo.conceptoLegadoLicencia(disciplina, discapacidad, esMenor, esSegundaMitadDelAno)
						+ "' no encontrado"));
	}

	private double calcularPrecioLicenciaFallback(Alumno alumno, boolean esSegundaMitadDelAno, String deporte, int anioReferencia) {
		if (Boolean.TRUE.equals(alumno.getTieneDiscapacidad())) {
			return esSegundaMitadDelAno ? 20.0 : 30.0;
		}

		// Usar la regla de edad correcta según el deporte
		boolean esMenor = catalogoDisciplinas.esMenorPorAnioNatural(alumno.getFechaNacimiento(),
				deporte == null ? null : deporte, anioReferencia);
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
			// Disciplinas que exigen licencia federativa (disciplina.requiere_licencia)
			alumnosDeportes = alumnoDeporteRepository.findActivosByDeporteIn(
					catalogoDisciplinas.codigosQueRequierenLicencia());
		} else {
			String deporteEnum = parseDeporteLicencia(deporte);
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

		String deporteEnum = parseDeporteLicencia(deporte);
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

	private String parseDeporteLicencia(String deporte) {
		String deporteEnum;
		try {
			deporteEnum = catalogoDisciplinas.validarCodigo(deporte.toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Deporte no v\u00e1lido: " + deporte +
					". Valores v\u00e1lidos: " + String.join(", ", catalogoDisciplinas.codigosQueRequierenLicencia()));
		}

		if (!deporteRequiereLicencia(deporteEnum)) {
			throw new IllegalArgumentException("Deporte no v\u00e1lido para licencias: " + deporte);
		}

		return deporteEnum;
	}

	private boolean deporteRequiereLicencia(String deporte) {
		return deporte != null && catalogoDisciplinas.requiereLicencia(deporte);
	}

	/**
	 * Claves alumno-concepto de todos los cargos: se busca la clave exacta de cada licencia, así que no
	 * hace falta filtrar por el nombre del producto (el de otra organización puede ser distinto).
	 */
	private Set<String> obtenerConceptosLicenciaExistentes() {
		return productoAlumnoRepository.findAll().stream()
				.filter(pa -> pa.getConcepto() != null)
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
		dto.setSedeId(productoAlumno.getSedeId());
		return dto;
	}

	// ===== IMPLEMENTACIÓN MÉTODOS MULTI-DEPORTE =====

	@Override
	public ProductoAlumnoDTO asignarProductoAAlumnoDeporte(Long alumnoId, Long productoId, String deporte, ProductoAlumnoDTO detallesDTO) {
		if (deporte == null || deporte.isBlank()) {
			throw new IllegalArgumentException("Seleccione un deporte del alumno.");
		}
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado"));

		Producto producto = productoRepository.findById(productoId)
				.orElseThrow(() -> new ProductoNoEncontradoException("Producto no encontrado"));
		exigirSedeActivaParaCargo(alumno);

		// Convertir string a Deporte enum
		String deporteEnum;
		try {
			deporteEnum = catalogoDisciplinas.validarCodigo(deporte.toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Deporte no válido: " + deporte +
					". Valores válidos: " + String.join(", ", codigosDisciplinas()));
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
		String concepto = producto.getConcepto();
		String deporteNombre = deporteEnum.replace('_', ' ');
		if (esMatricula(concepto)
				&& !normalizarConcepto(concepto).replace('_', ' ').matches(
						".*\\b" + deporteNombre + "\\b.*")) {
			concepto += " - " + deporteEnum;
		}
		productoAlumno.setConcepto(concepto);
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
	public int cargarMensualidadesMultiDeporte(String mesAno) {
		MensualidadUtils.formatearNombreMensualidad(mesAno); // valida el formato antes de nada

		// Obtener todos los AlumnoDeporte activos
		List<com.taemoi.project.entities.AlumnoDeporte> alumnosDeportes =
			alumnoDeporteRepository.findAll().stream()
				.filter(ad -> ad.getActivo() != null && ad.getActivo())
				.collect(Collectors.toList());
		Map<String, Producto> productosMensualidad = resolverProductosMensualidad(alumnosDeportes);

		Date fechaAsignacion = new Date();
		Set<Long> sedesInactivas = gestionPorSede.sedesInactivas();
		Set<Long> omitidos = new java.util.HashSet<>();
		for (com.taemoi.project.entities.AlumnoDeporte alumnoDeporte : alumnosDeportes) {
			Alumno alumno = alumnoDeporte.getAlumno();
			if (enSedeInactiva(alumno, sedesInactivas)) {
				omitidos.add(alumno.getId());
				continue;
			}
			Producto productoMensualidad = productosMensualidad.get(codigoDisciplina(alumnoDeporte));
			String conceptoCompleto = nombreMensualidad(productoMensualidad, mesAno) + " - "
					+ alumnoDeporte.getDeporte();

			// Verificar si ya existe esta mensualidad para este deporte
			boolean yaExiste = productoAlumnoRepository.findByAlumnoId(alumno.getId()).stream()
					.anyMatch(pa -> pa.getConcepto().equalsIgnoreCase(conceptoCompleto));

			if (!yaExiste) {
				Double precioMensualidad = alumnoDeporte.getCuantiaTarifa();

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

				// Añadir tarifa competidor si la disciplina admite competidores y lo es
				if (aplicaTarifaCompetidor(alumnoDeporte)) {
					asignarTarifaCompetidorPorDeporte(alumno, alumnoDeporte, mesAno, fechaAsignacion);
				}
			}
		}
		return omitidos.size();
	}

	@Override
	public void cargarMensualidadIndividualPorDeporte(Long alumnoId, String deporte, String mesAno, boolean forzar,
			LocalDate fechaAsignacionManual) {
		MensualidadUtils.formatearNombreMensualidad(mesAno); // valida el formato antes de nada

		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new IllegalArgumentException("Alumno no encontrado"));
		exigirSedeActivaParaCargo(alumno);

		// Convertir string a Deporte enum
		String deporteEnum;
		try {
			deporteEnum = catalogoDisciplinas.validarCodigo(deporte.toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Deporte no válido: " + deporte +
					". Valores válidos: " + String.join(", ", codigosDisciplinas()));
		}

		// Buscar AlumnoDeporte correspondiente
		com.taemoi.project.entities.AlumnoDeporte alumnoDeporte = alumnoDeporteRepository
				.findByAlumnoIdAndDeporte(alumnoId, deporteEnum)
				.orElseThrow(() -> new IllegalArgumentException(
					"El alumno no practica el deporte " + deporte));

		Producto productoMensualidad = resolverProductoMensualidad(deporteEnum);

		String conceptoCompleto = nombreMensualidad(productoMensualidad, mesAno) + " - " + deporteEnum;

		Set<String> conceptosExistentes = alumno.getProductosAlumno().stream()
				.filter(pa -> pa.getConcepto() != null)
				.map(pa -> pa.getConcepto().trim().toUpperCase(Locale.ROOT))
				.collect(Collectors.toSet());

		boolean mensualidadExiste = conceptosExistentes.contains(conceptoCompleto.toUpperCase(Locale.ROOT));
		boolean tarifaAplica = aplicaTarifaCompetidor(alumnoDeporte);
		boolean tarifaExiste = false;
		if (tarifaAplica) {
			String conceptoTarifaCompetidor = construirConceptoTarifaCompetidor(alumnoDeporte.getDeporte(), mesAno);
			tarifaExiste = conceptoTarifaCompetidor != null
					&& conceptosExistentes.contains(conceptoTarifaCompetidor.toUpperCase(Locale.ROOT));
		}

		if (!forzar) {
			if (mensualidadExiste && tarifaAplica && !tarifaExiste) {
				throw new MensualidadIncompletaException(
						"El alumno tiene productos de mensualidad incompletos.",
						List.of(deporteEnum),
						List.of(),
						List.of(),
						List.of(deporteEnum));
			}
			if (!mensualidadExiste && tarifaAplica && tarifaExiste) {
				throw new MensualidadIncompletaException(
						"El alumno tiene productos de mensualidad incompletos.",
						List.of(),
						List.of(deporteEnum),
						List.of(deporteEnum),
						List.of());
			}
			if (mensualidadExiste && (!tarifaAplica || tarifaExiste)) {
				throw new IllegalStateException("El alumno ya tiene asignada la " + conceptoCompleto);
			}
		}

		Date fechaAsignacion = resolverFechaAsignacion(fechaAsignacionManual);
		boolean mensualidadCreada = false;
		boolean tarifaCreada = false;

		if (!mensualidadExiste) {
			Double precioMensualidad = alumnoDeporte.getCuantiaTarifa();

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
			mensualidadCreada = true;
		}

		if (tarifaAplica && !tarifaExiste) {
			asignarTarifaCompetidorPorDeporte(alumno, alumnoDeporte, mesAno, fechaAsignacion);
			tarifaCreada = true;
		}

		if (!mensualidadCreada && !tarifaCreada && !forzar) {
			throw new IllegalStateException("El alumno ya tiene asignada la " + conceptoCompleto);
		}
	}

	// ===== MÉTODOS AUXILIARES PARA TARIFA COMPETIDOR =====

	/** Competidor en una disciplina que admite competidores ({@code disciplina.permite_competicion}). */
	private boolean aplicaTarifaCompetidor(AlumnoDeporte alumnoDeporte) {
		return Boolean.TRUE.equals(alumnoDeporte.getCompetidor())
				&& alumnoDeporte.getDeporte() != null
				&& catalogoDisciplinas.permiteCompeticion(alumnoDeporte.getDeporte());
	}

	/**
	 * Nombre del producto de competidor de la disciplina (el concepto del producto del catálogo) o, si
	 * no hay producto, el que le daba el código hasta V27 («TARIFA COMPETIDOR <DISCIPLINA>»).
	 */
	private String obtenerNombreProductoCompetidor(String deporte) {
		if (deporte == null || !catalogoDisciplinas.permiteCompeticion(deporte)) {
			return null;
		}
		String legado = CONCEPTO_LEGADO_COMPETIDOR + deporte;
		return productosCatalogo.resolverProductoCompetidor(deporte)
				.map(producto -> CatalogoLegadoTaemoi.conceptoBase(producto, legado, legado()))
				.orElse(legado);
	}

	private String construirConceptoTarifaCompetidor(String deporte, String mesAno) {
		String nombreProducto = obtenerNombreProductoCompetidor(deporte);
		if (nombreProducto == null) {
			return null;
		}
		return nombreProducto + " " + MensualidadUtils.formatearMesAno(mesAno);
	}

	/**
	 * Asigna la tarifa competidor a un alumno para un deporte (el producto de competidor de su
	 * disciplina, que se crea como antes si no existe). Importe: 20 € para el código heredado (como antes);
	 * el precio del producto para códigos propios.
	 */
	private void asignarTarifaCompetidorPorDeporte(Alumno alumno, AlumnoDeporte alumnoDeporte,
			String mesAno, Date fechaAsignacion) {
		String conceptoTarifaCompetidor = construirConceptoTarifaCompetidor(alumnoDeporte.getDeporte(), mesAno);
		if (conceptoTarifaCompetidor == null) {
			return;
		}

		// Verificar si ya existe esta tarifa competidor para este mes
		boolean yaExiste = productoAlumnoRepository.findByAlumnoId(alumno.getId()).stream()
				.anyMatch(pa -> pa.getConcepto().equalsIgnoreCase(conceptoTarifaCompetidor));

		if (!yaExiste) {
			Producto productoTarifaCompetidor = productosCatalogo
					.asegurarProductoCompetidor(alumnoDeporte.getDeporte())
					.orElse(null);
			if (productoTarifaCompetidor == null) {
				return;
			}
			// Taemoi (comportamiento heredado): códigos de V24 o sin clasificar, 20 € fijos, como antes de V27,
			// aunque el producto tenga otro precio. Códigos propios y cualquier código en otra escuela: el
			// precio del producto.
			boolean codigoPropio = CatalogoLegadoTaemoi.tipoDeCatalogo(productoTarifaCompetidor, legado()) != null;
			Double precio = codigoPropio ? productoTarifaCompetidor.getPrecio()
					: ProductosCatalogoService.PRECIO_LEGADO_COMPETIDOR;
			if (precio == null) {
				LOG.warn("El producto de competidor {} no tiene precio: se cobra el de antes de V27 ({} €)",
						productoTarifaCompetidor.getId(), ProductosCatalogoService.PRECIO_LEGADO_COMPETIDOR);
				precio = ProductosCatalogoService.PRECIO_LEGADO_COMPETIDOR;
			}

			ProductoAlumno productoAlumno = new ProductoAlumno();
			productoAlumno.setAlumno(alumno);
			productoAlumno.setProducto(productoTarifaCompetidor);
			productoAlumno.setAlumnoDeporte(alumnoDeporte);
			productoAlumno.setConcepto(conceptoTarifaCompetidor);
			productoAlumno.setPrecio(precio);
			productoAlumno.setFechaAsignacion(fechaAsignacion);
			productoAlumno.setCantidad(1);
			productoAlumno.setPagado(false);

			productoAlumnoRepository.save(productoAlumno);
		}
	}

	// ===== MENSUALIDADES: PRODUCTO DEL CATÁLOGO =====

	private static String codigoDisciplina(AlumnoDeporte alumnoDeporte) {
		return alumnoDeporte.getDeporte() == null ? null : alumnoDeporte.getDeporte();
	}

	/** @throws IllegalArgumentException si no hay producto de mensualidad (como antes de V27). */
	private Producto resolverProductoMensualidad(String codigoDisciplina) {
		return productosCatalogo.resolverProductoMensualidad(codigoDisciplina)
				.orElseThrow(() -> new IllegalArgumentException("Producto 'MENSUALIDAD' no encontrado"));
	}

	/**
	 * Producto de mensualidad de cada disciplina de las inscripciones, resuelto ANTES de crear ningún
	 * cargo: si falta, la carga falla sin dejar cargos a medias (como antes de V27). Sin inscripciones,
	 * el general (la carga fallaba igual si no existía).
	 */
	private Map<String, Producto> resolverProductosMensualidad(List<AlumnoDeporte> inscripciones) {
		Set<String> disciplinas = new LinkedHashSet<>();
		for (AlumnoDeporte inscripcion : inscripciones) {
			disciplinas.add(codigoDisciplina(inscripcion));
		}
		if (disciplinas.isEmpty()) {
			disciplinas.add(null);
		}
		Map<String, Producto> productos = new HashMap<>();
		for (String disciplina : disciplinas) {
			productos.put(disciplina, resolverProductoMensualidad(disciplina));
		}
		return productos;
	}

	/**
	 * «<NOMBRE> <MES> <AÑO>»: «MENSUALIDAD» para el código heredado o un producto sin clasificar (como
	 * antes de V27, aunque se renombre el producto); el nombre del producto para códigos propios.
	 */
	private String nombreMensualidad(Producto productoMensualidad, String mesAno) {
		return MensualidadUtils.formatearNombreMensualidad(CatalogoLegadoTaemoi.conceptoBase(productoMensualidad,
				ProductosCatalogoService.CONCEPTO_LEGADO_MENSUALIDAD, legado()), mesAno);
	}

	/** Códigos de las disciplinas de la escuela, por orden (en Taemoi, los del antiguo enum Deporte). */
	private List<String> codigosDisciplinas() {
		return catalogoDisciplinas.listarTodas().stream().map(com.taemoi.project.entities.Disciplina::getCodigo).toList();
	}

	/** Comportamiento heredado de Taemoi en la organización activa (V28). */
	private boolean legado() {
		return comportamientoLegado.activo();
	}

	/** Claves alumno-CONCEPTO (mayúsculas) de todos los cargos, para comprobar existencias exactas. */
	private Set<String> clavesDeCargosExistentes() {
		return productoAlumnoRepository.findAll().stream()
				.filter(pa -> pa.getConcepto() != null)
				.map(pa -> {
					Long alumnoId = obtenerAlumnoIdProducto(pa);
					return alumnoId != null ? alumnoId + "-" + pa.getConcepto().toUpperCase(Locale.ROOT) : null;
				})
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
	}
}
