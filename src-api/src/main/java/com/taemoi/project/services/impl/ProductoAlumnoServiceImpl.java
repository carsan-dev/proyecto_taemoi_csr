package com.taemoi.project.services.impl;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.taemoi.project.dtos.ProductoAlumnoDTO;
import com.taemoi.project.dtos.response.CuantiaPersonalizadaSuperiorDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.Cobro;
import com.taemoi.project.entities.EstadoCobro;
import com.taemoi.project.entities.MetodoPago;
import com.taemoi.project.entities.OrigenCobro;
import com.taemoi.project.entities.Producto;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.entities.TipoProducto;
import com.taemoi.project.exceptions.alumno.AlumnoNoEncontradoException;
import com.taemoi.project.exceptions.pago.CobroEntregadoException;
import com.taemoi.project.exceptions.producto.ProductoNoEncontradoException;
import com.taemoi.project.exceptions.producto.MensualidadIncompletaException;
import com.taemoi.project.repositories.AlumnoConvocatoriaRepository;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.CobroRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.repositories.ProductoRepository;
import com.taemoi.project.services.AlumnoDeporteService;
import com.taemoi.project.services.CatalogoLegadoTaemoi;
import com.taemoi.project.services.CatalogoDisciplinasService;
import com.taemoi.project.services.CobroService;
import com.taemoi.project.services.ProductoAlumnoService;
import com.taemoi.project.services.ProductosCatalogoService;
import com.taemoi.project.services.ProgresionGradosService;
import com.taemoi.project.services.RegistroAccionesCaja;
import com.taemoi.project.services.ResolutorCuantiaMensualidad;
import com.taemoi.project.services.ResultadoGeneracionMensualidades;
import com.taemoi.project.services.TarifaSedeService;
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

	/**
	 * Entrega 6-4: cuantía de cada mensualidad con la tarifa de la sede del alumno para el mes que se cobra (D6-27 a
	 * D6-34). Sin tarifas por sede (Moiskimdo), la cuantía de la inscripción tal cual.
	 */
	@Autowired
	private TarifaSedeService tarifaSede;

	/**
	 * Pieza 6b-1: registro de cobros. Cada paso de un cargo a pagado deja un cobro ACTIVO y cada paso a no pagado (o el
	 * borrado del cargo) lo anula; {@code pagado} y {@code fecha_pago} se siguen escribiendo igual que antes.
	 */
	@Autowired
	private CobroService cobros;

	/** Pieza 6b-2: lectura del cobro que el profesor deshace (sin bloqueo; se bloquea después en la transacción). */
	@Autowired
	private CobroRepository cobroRepository;

	/** Pieza 6b-2: registro de acciones de la caja (cobrar y deshacer del profesor). */
	@Autowired
	private RegistroAccionesCaja registroCaja;

	/** Pieza 6b-2c: registro de acciones sobre cargos (cancelar, reactivar y borrar). */
	@Autowired
	private com.taemoi.project.services.RegistroAccionesCargos registroCargos;

	/** Pieza 6b-3: nombre de quien canceló el cargo (una consulta para toda la lista). */
	@Autowired
	private com.taemoi.project.repositories.UsuarioRepository usuarioRepository;

	/** Motivo de la anulación del cobro al revertir el pago por el PUT del cargo sin {@code motivoCambio}. */
	private static final String MOTIVO_REVERSION = "pago revertido";

	/**
	 * Guarda un cargo NUEVO. Si nace pagado (asignación o reserva con {@code pagado = true}, licencias), en la misma
	 * transacción registra su cobro (pieza 6b-1): ese dinero se cobró al crearlo. Si no, lo guarda como siempre.
	 */
	private ProductoAlumno guardarCargoNuevo(ProductoAlumno cargo, MetodoPago metodo) {
		return guardarCargoNuevo(cargo, metodo, OrigenCobro.TESORERIA);
	}

	/** Como {@link #guardarCargoNuevo(ProductoAlumno, MetodoPago)}, con el origen del cobro (6b-2: PROFESOR). */
	private ProductoAlumno guardarCargoNuevo(ProductoAlumno cargo, MetodoPago metodo, OrigenCobro origen) {
		if (!Boolean.TRUE.equals(cargo.getPagado())) {
			return productoAlumnoRepository.save(cargo);
		}
		return cobros.enTransaccion(() -> {
			ProductoAlumno guardado = productoAlumnoRepository.save(cargo);
			cobros.registrar(guardado, metodo, guardado.getFechaPago(), origen);
			return guardado;
		});
	}

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
		return convertirADTO(crearCargoDeProducto(alumno, producto, detallesDTO, OrigenCobro.TESORERIA, null));
	}

	/**
	 * Pieza 6b-2: el profesor apunta y cobra en efectivo un producto del catálogo a un alumno de sus grupos (permiso:
	 * {@code @acceso.alumnoDeMisGrupos}). Misma vía que asignar un producto ({@link #crearCargoDeProducto}): precio y
	 * concepto del catálogo, cantidad 1, cargo YA PAGADO (fecha de pago ahora) y, en la misma transacción, su cobro
	 * ACTIVO en EFECTIVO, origen PROFESOR y cobrado por él; la sede, la del {@code @PrePersist} del cargo. El cargo guarda
	 * quién lo vendió ({@code vendido_por_usuario_id}, decisión 172): el mismo usuario que cobra; es la ÚNICA vía que lo
	 * fija. Solo productos sin efectos colaterales ({@link #exigirProductoVendiblePorProfesor}, 400 si no). Alumno o
	 * producto que no existe en la escuela → 404. Queda en el registro de acciones.
	 */
	@Override
	public Cobro apuntarYCobrarProductoComoProfesor(Long alumnoId, Long productoId) {
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado"));
		Producto producto = productoRepository.findById(productoId)
				.orElseThrow(() -> new ProductoNoEncontradoException("Producto no encontrado"));
		exigirProductoVendiblePorProfesor(producto);
		ProductoAlumnoDTO detalles = new ProductoAlumnoDTO();
		detalles.setCantidad(1);
		detalles.setPagado(true);
		detalles.setMetodoPago(MetodoPago.EFECTIVO);
		return cobros.enTransaccion(() -> {
			ProductoAlumno cargo = crearCargoDeProducto(alumno, producto, detalles, OrigenCobro.PROFESOR,
					CobroService.usuarioActualId());
			Cobro cobro = cobroRepository.findFirstByProductoAlumnoIdAndEstado(cargo.getId(), EstadoCobro.ACTIVO)
					.orElseThrow(() -> new IllegalStateException("No se registró el cobro."));
			Map<String, Long> implicados = ids("cobro", cobro.getId(), "cargo", cargo.getId());
			implicados.put("producto", productoId);
			implicados.put("alumno", alumnoId);
			registroCaja.registrar(RegistroAccionesCaja.Accion.COBRO_PROFESOR_PRODUCTO,
					"/api/caja/profesor/alumnos/" + alumnoId + "/productos/" + productoId + "/cobrar",
					cargo.getSedeId(), implicados);
			return cobro;
		});
	}

	static final String PRODUCTO_NO_VENDIBLE_POR_PROFESOR = "Este producto no lo puede cobrar un profesor: solo "
			+ "productos sin efectos (no mensualidades, licencias, matrículas, reservas de plaza, tarifas de "
			+ "competidor, exámenes ni productos que conceden grado o derecho a examen).";

	/** Palabras del concepto (sin tildes, en mayúsculas) que excluyen un producto aunque esté sin clasificar. */
	private static final List<String> CONCEPTOS_NO_VENDIBLES_POR_PROFESOR = List.of("MENSUALIDAD", "LICENCIA",
			"MATRICULA", "RESERVA", "COMPETIDOR", "EXAMEN", "PASE DE GRADO", "RECOMPENSA");

	/**
	 * Pieza 6b-2, lista blanca de lo que el profesor puede apuntar y cobrar: SOLO productos de tipo
	 * {@link TipoProducto#OTRO} (material, camisetas…), sin grado enlazado, cuyo concepto no nombra nada con efectos y
	 * que el catálogo no resuelve como concesión de grado. Fuera: MENSUALIDAD, RESERVA_PLAZA, LICENCIA, COMPETIDOR
	 * (recurrentes o con efectos en la ficha), EXAMEN y RECOMPENSA (derecho a examen y promoción de grado), y por
	 * concepto los de Taemoi sin clasificar por código. Ante la duda (el catálogo falla), se excluye.
	 */
	private void exigirProductoVendiblePorProfesor(Producto producto) {
		if (!esVendiblePorProfesor(producto)) {
			throw new IllegalArgumentException(PRODUCTO_NO_VENDIBLE_POR_PROFESOR);
		}
	}

	/**
	 * La regla ÚNICA de la lista blanca (la usan apuntar y cobrar y {@link #productosVendiblesPorProfesor}). El grado
	 * enlazado se mira en la entidad; el catálogo solo resuelve el concedido por concepto (sin releer el producto).
	 */
	private boolean esVendiblePorProfesor(Producto producto) {
		if (producto == null || producto.getTipo() != TipoProducto.OTRO || producto.getGrado() != null) {
			return false;
		}
		String concepto = normalizarConcepto(producto.getConcepto());
		if (concepto.isBlank() || CONCEPTOS_NO_VENDIBLES_POR_PROFESOR.stream().anyMatch(concepto::contains)) {
			return false;
		}
		try {
			return productosCatalogo.buscarCodigoGradoConcedido(null, producto.getConcepto()).isEmpty();
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** Pieza 6b-2: productos del catálogo de la escuela que el profesor puede apuntar y cobrar, por concepto. */
	@Override
	public List<Producto> productosVendiblesPorProfesor() {
		return productoRepository.findAll().stream().filter(this::esVendiblePorProfesor)
				.sorted(java.util.Comparator.comparing(Producto::getConcepto,
						java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
						.thenComparing(Producto::getId))
				.toList();
	}

	/**
	 * Crea el cargo de un producto del catálogo para el alumno (la vía de «asignar producto a alumno», común desde la
	 * 6b-2 con la venta del profesor): D6-17, matrícula con deporte, precio y concepto del catálogo, cobro si nace
	 * pagado (con ese origen) y derecho a examen si lo da. {@code vendidoPor}: el profesor que lo vende (decisión 172),
	 * solo desde {@link #apuntarYCobrarProductoComoProfesor}; {@code null} en la asignación de la gestión.
	 */
	private ProductoAlumno crearCargoDeProducto(Alumno alumno, Producto producto, ProductoAlumnoDTO detallesDTO,
			OrigenCobro origen, Long vendidoPor) {
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
		Date asignadoEn = new Date();
		productoAlumno.setFechaAsignacion(asignadoEn);
		productoAlumno.setNotas(detallesDTO.getNotas());
		productoAlumno.setVendidoPorUsuarioId(vendidoPor);

		if (productoAlumno.getPagado()) {
			// Pieza 6b-2c: en la venta del profesor la fecha de pago (y la del cobro) es el MISMO instante que la de
			// asignación (se apunta y se cobra a la vez). Ya no es la marca de «venta suya»: desde la decisión 172 lo es
			// vendido_por_usuario_id ({@link #esVentaDelProfesor}). La gestión, como siempre.
			productoAlumno.setFechaPago(origen == OrigenCobro.PROFESOR ? asignadoEn : new Date());
		}

		ProductoAlumno savedProductoAlumno = guardarCargoNuevo(productoAlumno, detallesDTO.getMetodoPago(), origen);
		if (Boolean.TRUE.equals(savedProductoAlumno.getPagado()) && daDerechoAExamen(savedProductoAlumno)) {
			Alumno a = savedProductoAlumno.getAlumno();
			a.setTieneDerechoExamen(true);
			alumnoRepository.save(a);
		}
		return savedProductoAlumno;
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
		java.util.Map<Long, String> nombres = nombresDeQuienCancelo(productosAlumno);
		return productosAlumno.stream().map(pa -> convertirADTO(pa, nombres)).collect(Collectors.toList());
	}

	@Override
	public ProductoAlumnoDTO actualizarProductoAlumno(Long id, ProductoAlumnoDTO detallesDTO) {
		// Pieza 6b-1: el cargo se bloquea y se relee, se cambia, se guarda y se registra o anula su cobro en una sola
		// transacción. Lo demás (convocatoria, derecho a examen, promoción de grado) sigue fuera, como antes.
		CargoActualizado cambio = cobros.enTransaccion(() -> aplicarCambiosAlCargo(id, detallesDTO));
		aplicarEfectosDelCambioDePago(cambio);
		return convertirADTO(cambio.cargo());
	}

	/** Motivo de la anulación del cobro cuando el profesor deshace el suyo (pieza 6b-2). */
	static final String MOTIVO_DESHACER_PROFESOR = "cobro deshecho por el profesor";
	static final String CARGO_YA_COBRADO = "El cargo ya está cobrado.";
	static final String COBRO_NO_ACTIVO = "El cobro ya no está activo.";

	/**
	 * Pieza 6b-2: el profesor cobra en efectivo un cargo PENDIENTE de un alumno de sus grupos (el permiso lo da
	 * {@code @acceso.cargoDeMisGrupos}). Misma lógica que el cobro del PUT del cargo ({@link #aplicarCambiosAlCargoBloqueado}
	 * con el cargo bloqueado): {@code pagado = true}, {@code fecha_pago} de ahora y cobro ACTIVO en EFECTIVO con origen
	 * PROFESOR y cobrado por él; y los mismos efectos fuera de la transacción ({@link #aplicarEfectosDelCambioDePago}).
	 * Ya pagado → 409; sin alumno resoluble (ni {@code alumno_id} ni inscripción, {@link ProductoAlumno#alumnoEfectivo})
	 * → 400 (pieza 6b-2d: un cargo antiguo con el alumno solo en la inscripción sí se cobra). Queda en el registro de
	 * acciones.
	 */
	@Override
	public Cobro cobrarEnEfectivoComoProfesor(Long cargoId) {
		CobroDelProfesor resultado = cobros.enTransaccion(() -> {
			ProductoAlumno cargo = cobros.bloquearCargo(cargoId)
					.orElseThrow(() -> new ProductoNoEncontradoException("ProductoAlumno no encontrado"));
			if (cargo.estaCancelado()) {
				throw new IllegalStateException(CobroService.CARGO_CANCELADO);
			}
			if (Boolean.TRUE.equals(cargo.getPagado())) {
				throw new IllegalStateException(CARGO_YA_COBRADO);
			}
			if (cargo.alumnoEfectivo() == null) {
				throw new IllegalArgumentException(CARGO_SIN_ALUMNO);
			}
			ProductoAlumnoDTO cambios = new ProductoAlumnoDTO();
			cambios.setPagado(true);
			cambios.setMetodoPago(MetodoPago.EFECTIVO);
			CargoActualizado cambio = aplicarCambiosAlCargoBloqueado(cargo, cambios, OrigenCobro.PROFESOR);
			Cobro cobro = cobroRepository.findFirstByProductoAlumnoIdAndEstado(cargoId, EstadoCobro.ACTIVO)
					.orElseThrow(() -> new IllegalStateException("No se registró el cobro."));
			registroCaja.registrar(RegistroAccionesCaja.Accion.COBRO_PROFESOR,
					"/api/caja/profesor/cargos/" + cargoId + "/cobrar", cargo.getSedeId(),
					ids("cobro", cobro.getId(), "cargo", cargoId));
			return new CobroDelProfesor(cambio, cobro);
		});
		aplicarEfectosDelCambioDePago(resultado.cambio());
		return resultado.cobro();
	}

	/**
	 * Pieza 6b-2: el profesor deshace SU cobro (cobrado por él, origen PROFESOR, ACTIVO y sin entrega). Bloquea el
	 * CARGO y después el COBRO (mismo orden que la reversión del PUT) y deja el cargo como no pagado con la misma lógica
	 * que la reversión del PUT, sin exigir ROLE_ADMIN solo en este caso; mismos efectos fuera de la transacción. Ajeno o
	 * de otro origen → 403; entregado → 409 ({@link CobroEntregadoException}); ya anulado o sin cargo → 409. Queda en el
	 * registro de acciones.
	 */
	@Override
	public void deshacerCobroDeProfesor(Long cobroId, Long profesorId) {
		deshacerCobroDeProfesor(cobroId, profesorId, null);
	}

	/** Motivo de la cancelación del cargo cuando el profesor deshace su venta con NO_HUBO_VENTA (pieza 6b-2c). */
	static final String MOTIVO_NO_HUBO_VENTA = "no hubo venta";
	static final String NO_ES_VENTA_DEL_PROFESOR = "Solo se puede deshacer como «no hubo venta» un producto que "
			+ "apuntó y cobró usted mismo; si el alumno aún no ha pagado, deshaga el cobro sin ese motivo.";

	/**
	 * Pieza 6b-2c. Con {@code AUN_NO_PAGADO} (o {@code null}), lo de la 6b-2: el cargo vuelve a pendiente. Con
	 * {@code NO_HUBO_VENTA}, además de las comprobaciones de siempre (suyo, origen PROFESOR, sin entrega, ACTIVO), el
	 * cargo debe haber nacido de SU venta del catálogo (el cargo guarda que lo vendió él, decisión 172:
	 * {@link #esVentaDelProfesor}; si no → 400): en la misma
	 * transacción, con el cargo y el cobro bloqueados, se anula el cobro (como AUN_NO_PAGADO) y se CANCELA el cargo
	 * (por el profesor, motivo «no hubo venta»). El estado final cumple el invariante (cancelado, no pagado, sin cobro
	 * ACTIVO). Queda en el registro de acciones (deshacer y cancelación).
	 */
	@Override
	public void deshacerCobroDeProfesor(Long cobroId, Long profesorId,
			com.taemoi.project.dtos.request.CajaRequests.MotivoDeshacer motivo) {
		boolean noHuboVenta = motivo == com.taemoi.project.dtos.request.CajaRequests.MotivoDeshacer.NO_HUBO_VENTA;
		Long cargoId = cobroRepository.findById(cobroId)
				.orElseThrow(() -> new NoSuchElementException("Cobro no encontrado."))
				.getProductoAlumnoId();
		if (cargoId == null) {
			throw new IllegalStateException(COBRO_NO_ACTIVO);
		}
		CargoActualizado cambio = cobros.enTransaccion(() -> {
			ProductoAlumno cargo = cobros.bloquearCargo(cargoId)
					.orElseThrow(() -> new IllegalStateException(COBRO_NO_ACTIVO));
			Cobro cobro = cobros.bloquearCobro(cobroId)
					.orElseThrow(() -> new NoSuchElementException("Cobro no encontrado."));
			if (profesorId == null || !profesorId.equals(cobro.getCobradoPorUsuarioId())
					|| cobro.getOrigen() != OrigenCobro.PROFESOR) {
				throw new AccessDeniedException("Acceso denegado.");
			}
			if (cobro.getEntregaCajaId() != null) {
				throw new CobroEntregadoException();
			}
			if (cobro.getEstado() != EstadoCobro.ACTIVO || !cargoId.equals(cobro.getProductoAlumnoId())) {
				throw new IllegalStateException(COBRO_NO_ACTIVO);
			}
			if (noHuboVenta && !esVentaDelProfesor(cargo, cobro, profesorId)) {
				throw new IllegalArgumentException(NO_ES_VENTA_DEL_PROFESOR);
			}
			ProductoAlumnoDTO cambios = new ProductoAlumnoDTO();
			cambios.setPagado(false);
			cambios.setMotivoCambio(noHuboVenta ? MOTIVO_NO_HUBO_VENTA : MOTIVO_DESHACER_PROFESOR);
			CargoActualizado resultado = aplicarCambiosAlCargoBloqueado(cargo, cambios, OrigenCobro.PROFESOR);
			registroCaja.registrar(RegistroAccionesCaja.Accion.COBRO_PROFESOR_DESHECHO,
					"/api/caja/profesor/cobros/" + cobroId + "/deshacer", cobro.getSedeId(),
					ids("cobro", cobroId, "cargo", cargoId));
			if (noHuboVenta) {
				ProductoAlumno cancelado = marcarCancelado(resultado.cargo(), profesorId, MOTIVO_NO_HUBO_VENTA);
				registroCargos.registrar(com.taemoi.project.services.RegistroAccionesCargos.Accion.CARGO_CANCELADO,
						"/api/caja/profesor/cobros/" + cobroId + "/deshacer", cancelado.getSedeId(), cargoId);
				return new CargoActualizado(cancelado, resultado.pagadoAntes());
			}
			return resultado;
		});
		aplicarEfectosDelCambioDePago(cambio);
	}

	/**
	 * Pieza 6b-2c: el cargo nació de la venta del profesor {@code profesorId} ({@link #apuntarYCobrarProductoComoProfesor})
	 * con ESTE cobro. Con el cargo y el cobro bloqueados:
	 * <ul>
	 * <li>el cargo guarda que lo vendió él ({@code vendido_por_usuario_id}, decisión 172): solo esa venta lo fija, así
	 * que un cargo de la gestión (aunque su fecha de asignación coincida con la del cobro) nunca lo es. Hasta la
	 * decisión 172 se deducía de que la fecha del cobro fuera la de asignación al milisegundo. V31_6 rellenó las ventas
	 * anteriores desde su fila de auditoría (COBRO_PROFESOR_PRODUCTO);</li>
	 * <li>el cobro es el PRIMERO del cargo (ninguno anterior, ni anulado): es el cobro de la venta, no uno posterior tras
	 * deshacerla como «aún no pagado»;</li>
	 * <li>el producto del cargo sigue siendo de los que el profesor puede vender ({@link #esVendiblePorProfesor}).</li>
	 * </ul>
	 * Origen PROFESOR, cobrado por él, ACTIVO y sin entrega ya lo exige el llamador.
	 */
	private boolean esVentaDelProfesor(ProductoAlumno cargo, Cobro cobro, Long profesorId) {
		if (profesorId == null || !profesorId.equals(cargo.getVendidoPorUsuarioId())) {
			return false;
		}
		List<Cobro> delCargo = cobroRepository.findByProductoAlumnoIdOrderByIdAsc(cargo.getId());
		if (delCargo.isEmpty() || !cobro.getId().equals(delCargo.get(0).getId())) {
			return false;
		}
		return esVendiblePorProfesor(cargo.getProducto());
	}

	private static Map<String, Long> ids(String clave1, Long id1, String clave2, Long id2) {
		Map<String, Long> ids = new LinkedHashMap<>();
		ids.put(clave1, id1);
		ids.put(clave2, id2);
		return ids;
	}

	private record CobroDelProfesor(CargoActualizado cambio, Cobro cobro) {
	}

	/**
	 * Fuera de la transacción del cobro, como antes de la 6b-1: sincroniza la inscripción a la convocatoria (cuantía y
	 * pagado), el derecho a examen del alumno y, si pasa a pagado, promueve el grado que concede el producto. Común al
	 * PUT del cargo y a las rutas del profesor (pieza 6b-2).
	 *
	 * <p>Desde la 6b-2 el derecho a examen SOLO lo tocan los cargos de examen ({@link #daDerechoAExamen}): antes, cobrar
	 * o revertir cualquier cargo lo ponía a {@code esExamen && pagado}, así que pagar una mensualidad quitaba el derecho
	 * a examen ya ganado. Cambio deliberado (decisión del propietario).
	 */
	private void aplicarEfectosDelCambioDePago(CargoActualizado cambio) {
		ProductoAlumno updatedProductoAlumno = cambio.cargo();
		boolean pagadoAntes = cambio.pagadoAntes();

		alumnoConvocatoriaRepository.findByProductoAlumnoId(updatedProductoAlumno.getId())
				.ifPresent(ac -> {
					ac.setCuantiaExamen(updatedProductoAlumno.getPrecio());
					ac.setPagado(updatedProductoAlumno.getPagado());
					alumnoConvocatoriaRepository.save(ac);
				});

		// Pieza 6b-2 (decisión del propietario, cambio DELIBERADO del comportamiento anterior, también en el PUT de
		// Moiskimdo): solo un cargo de examen (daDerechoAExamen) fija alumno.tiene_derecho_examen (= pagado). Cobrar o
		// deshacer cualquier otro cargo (una mensualidad, material…) ya no lo pone a false.
		// Pieza 6b-2d: el alumno resuelto como en la tesorería (los cargos antiguos sin alumno_id lo tienen en la
		// inscripción). Sin alumno resoluble no hay a quién darle o quitarle el derecho: no se toca a nadie.
		Alumno alumno = updatedProductoAlumno.alumnoEfectivo();
		if (alumno != null && daDerechoAExamen(updatedProductoAlumno)) {
			alumno.setTieneDerechoExamen(Boolean.TRUE.equals(updatedProductoAlumno.getPagado()));
			alumnoRepository.save(alumno);
		}

		if (Boolean.TRUE.equals(updatedProductoAlumno.getPagado()) && !pagadoAntes) {
			activarGradoPorRecompensaPagada(updatedProductoAlumno);
		}
	}

	/** El cargo guardado y si estaba pagado antes del cambio (leído con la fila bloqueada). */
	private record CargoActualizado(ProductoAlumno cargo, boolean pagadoAntes) {
	}

	/**
	 * Dentro de la transacción de {@link CobroService#enTransaccion}: las mismas reglas que antes de la 6b-1 sobre el
	 * cargo bloqueado y releído, y el cobro (registrar si pasa a pagado, anular si pasa a no pagado; 409 si va en una
	 * entrega de caja). Se puede repetir: vuelve a leerlo todo.
	 */
	private CargoActualizado aplicarCambiosAlCargo(Long id, ProductoAlumnoDTO detallesDTO) {
		ProductoAlumno productoAlumno = cobros.bloquearCargo(id)
				.orElseThrow(() -> new ProductoNoEncontradoException("ProductoAlumno no encontrado"));
		return aplicarCambiosAlCargoBloqueado(productoAlumno, detallesDTO, OrigenCobro.TESORERIA);
	}

	/**
	 * Las reglas del PUT del cargo sobre un cargo YA bloqueado y releído (pieza 6b-2: común con las rutas del profesor).
	 * Con origen {@link OrigenCobro#PROFESOR} (solo las rutas del profesor, que ya comprobaron su permiso y lo que
	 * cambian) no se aplican las restricciones del responsable de sede ni la de revertir solo con ROLE_ADMIN: el
	 * profesor solo cobra un cargo pendiente o deshace su propio cobro.
	 */
	private CargoActualizado aplicarCambiosAlCargoBloqueado(ProductoAlumno productoAlumno,
			ProductoAlumnoDTO detallesDTO, OrigenCobro origen) {
		boolean viaProfesor = origen == OrigenCobro.PROFESOR;
		// Pieza 6b-2c: en un cargo cancelado solo se cambian las notas (cobrarlo, o cambiar precio, cantidad, concepto o
		// fechas → 409; para cobrarlo, reactivarlo antes). La guarda central está en CobroService.registrar.
		if (productoAlumno.estaCancelado() && cambiaAlgoMasQueLasNotasDeUnCancelado(productoAlumno, detallesDTO)) {
			throw new IllegalStateException(CARGO_CANCELADO_SOLO_NOTAS);
		}
		if (!viaProfesor) {
			exigirEdicionPermitidaAlResponsable(productoAlumno, detallesDTO);
		}

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
			if (pagadoAntes && !pagadoSolicitado && !viaProfesor && !usuarioActualEsAdmin()) {
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
		String motivo = detallesDTO.getMotivoCambio() != null && !detallesDTO.getMotivoCambio().isBlank()
				? detallesDTO.getMotivoCambio()
				: MOTIVO_REVERSION;
		cobros.alCambiarPago(updatedProductoAlumno, pagadoAntes, detallesDTO.getMetodoPago(), origen, motivo);
		return new CargoActualizado(updatedProductoAlumno, pagadoAntes);
	}

	/** Cargo sin alumno resoluble (ni {@code alumno_id} ni inscripción, pieza 6b-2d; ver D6-20). */
	private static final String CARGO_SIN_ALUMNO = "El cargo no tiene alumno asociado: lo gestiona la administración.";

	/**
	 * Entrega 6-3, solo para el responsable de sede (la gestión, como siempre):
	 * <ul>
	 * <li>D6-20: en un cargo YA COBRADO solo puede cambiar las notas; cualquier otro campo que llegue con un valor
	 * distinto del guardado (precio, cantidad, concepto, fechas o deshacer el pago) → 403.</li>
	 * <li>Un cargo sin alumno resoluble (ni {@code alumno_id} ni inscripción, {@link ProductoAlumno#alumnoEfectivo}) →
	 * 400 antes de guardar nada. Pieza 6b-2d: un cargo antiguo con el alumno solo en la inscripción ya se edita (antes
	 * daba 400 porque el derecho a examen fallaba sin {@code alumno_id}).</li>
	 * </ul>
	 */
	private void exigirEdicionPermitidaAlResponsable(ProductoAlumno cargo, ProductoAlumnoDTO cambios) {
		if (gestionPorSede.restriccion().isEmpty()) {
			return;
		}
		if (Boolean.TRUE.equals(cargo.getPagado()) && cambiaAlgoMasQueLasNotas(cargo, cambios)) {
			throw new org.springframework.security.access.AccessDeniedException("Acceso denegado.");
		}
		if (cargo.alumnoEfectivo() == null) {
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

	static final String CARGO_CANCELADO_SOLO_NOTAS = "El cargo está cancelado: solo se pueden cambiar las notas. "
			+ "Reactívelo para cobrarlo o cambiarlo.";

	/**
	 * Pieza 6b-2c: como {@link #cambiaAlgoMasQueLasNotas}, para un cargo cancelado (siempre no pagado): reenviar
	 * {@code pagado:false} no es un cambio; {@code pagado:true} sí.
	 */
	private static boolean cambiaAlgoMasQueLasNotasDeUnCancelado(ProductoAlumno cargo, ProductoAlumnoDTO cambios) {
		return (cambios.getCantidad() != null && !cambios.getCantidad().equals(cargo.getCantidad()))
				|| (cambios.getPrecio() != null
						&& (cargo.getPrecio() == null || Double.compare(cambios.getPrecio(), cargo.getPrecio()) != 0))
				|| (cambios.getConcepto() != null && !cambios.getConcepto().equals(cargo.getConcepto()))
				|| (cambios.getFechaAsignacion() != null && !mismoInstante(cambios.getFechaAsignacion(),
						cargo.getFechaAsignacion()))
				|| (cambios.getFechaPago() != null && !mismoInstante(cambios.getFechaPago(), cargo.getFechaPago()))
				|| Boolean.TRUE.equals(cambios.getPagado());
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


	static final String CARGO_CON_COBROS_NO_SE_BORRA = "Solo se borran cargos no pagados que nunca se cobraron. "
			+ "Cancélelo en su lugar.";

	/**
	 * Borrado MANUAL de un cargo ({@code DELETE /api/productos-alumno/{id}}). Pieza 6b-2c (decisión del propietario,
	 * cambio DELIBERADO también para Moiskimdo): solo el propietario ({@code ROLE_ADMIN}, en el controlador) y solo
	 * cargos no pagados que NUNCA se cobraron (sin ninguna fila en {@code cobro}, tampoco ANULADA); si no → 409 («cancélelo
	 * en su lugar»). Con el cargo bloqueado, en una transacción, y queda en el registro de acciones. Los borrados
	 * AUTOMÁTICOS (quitar a un alumno de una convocatoria, borrar la convocatoria o el alumno) no pasan por aquí.
	 */
	@Override
	public void eliminarProductoAlumno(Long id) {
		ProductoAlumno pa = cobros.enTransaccion(() -> {
			ProductoAlumno cargo = cobros.bloquearCargo(id)
					.orElseThrow(() -> new ProductoNoEncontradoException("ProductoAlumno no encontrado con ID: " + id));
			if (Boolean.TRUE.equals(cargo.getPagado()) || !cobroRepository.findByProductoAlumnoIdOrderByIdAsc(id).isEmpty()) {
				throw new IllegalStateException(CARGO_CON_COBROS_NO_SE_BORRA);
			}
			// Sin cobros: no anula nada; se mantiene como red de seguridad (409 si alguno va en una entrega).
			cobros.alBorrarCargo(cargo);
			productoAlumnoRepository.deleteById(id);
			registroCargos.registrar(com.taemoi.project.services.RegistroAccionesCargos.Accion.CARGO_BORRADO,
					"/api/productos-alumno/" + id, cargo.getSedeId(), id);
			return cargo;
		});

		// Pieza 6b-2d: alumno resuelto como en la tesorería; sin alumno resoluble no se toca a nadie (el cargo ya se borró).
		Alumno alumno = pa.alumnoEfectivo();
		if (alumno != null && daDerechoAExamen(pa)) {
			alumno.setTieneDerechoExamen(false);
			alumnoRepository.save(alumno);
		}
	}

	static final String CARGO_YA_CANCELADO = "El cargo ya está cancelado.";
	static final String CARGO_NO_CANCELADO = "El cargo no está cancelado.";
	static final String CARGO_PAGADO_NO_SE_CANCELA = "El cargo está cobrado: revierta antes el cobro para cancelarlo.";
	static final String CARGO_DE_CONVOCATORIA_NO_SE_CANCELA = "Este cargo es la inscripción del alumno a una "
			+ "convocatoria: para que no lo pague, quítelo de la convocatoria.";
	private static final int MAX_MOTIVO_CANCELACION = 500;

	/**
	 * Pieza 6b-2c: cancela un cargo PENDIENTE. Con el cargo bloqueado (FOR UPDATE, el orden cargo → cobro de la 6b-2):
	 * ya cancelado → 409; pagado (o con un cobro ACTIVO) → 409, se revierte antes el cobro; unido a una inscripción a
	 * convocatoria → 400. No toca al alumno ni al derecho a examen (un cargo pendiente no lo da). Queda en el registro.
	 */
	@Override
	public ProductoAlumnoDTO cancelarProductoAlumno(Long id, String motivo) {
		String limpio = motivoDeCancelacion(motivo);
		ProductoAlumno cancelado = cobros.enTransaccion(() -> {
			ProductoAlumno cargo = cobros.bloquearCargo(id)
					.orElseThrow(() -> new ProductoNoEncontradoException("ProductoAlumno no encontrado con ID: " + id));
			if (cargo.estaCancelado()) {
				throw new IllegalStateException(CARGO_YA_CANCELADO);
			}
			if (Boolean.TRUE.equals(cargo.getPagado())
					|| cobroRepository.findFirstByProductoAlumnoIdAndEstado(id, EstadoCobro.ACTIVO).isPresent()) {
				throw new IllegalStateException(CARGO_PAGADO_NO_SE_CANCELA);
			}
			if (alumnoConvocatoriaRepository.findByProductoAlumnoId(id).isPresent()) {
				throw new IllegalArgumentException(CARGO_DE_CONVOCATORIA_NO_SE_CANCELA);
			}
			ProductoAlumno guardado = marcarCancelado(cargo, CobroService.usuarioActualId(), limpio);
			registroCargos.registrar(com.taemoi.project.services.RegistroAccionesCargos.Accion.CARGO_CANCELADO,
					"/api/productos-alumno/" + id + "/cancelar", guardado.getSedeId(), id);
			return guardado;
		});
		return convertirADTO(cancelado);
	}

	/** Pieza 6b-2c: deja el cargo cancelado pendiente otra vez y borra quién, cuándo y por qué. No cancelado → 409. */
	@Override
	public ProductoAlumnoDTO reactivarProductoAlumno(Long id) {
		ProductoAlumno reactivado = cobros.enTransaccion(() -> {
			ProductoAlumno cargo = cobros.bloquearCargo(id)
					.orElseThrow(() -> new ProductoNoEncontradoException("ProductoAlumno no encontrado con ID: " + id));
			if (!cargo.estaCancelado()) {
				throw new IllegalStateException(CARGO_NO_CANCELADO);
			}
			cargo.setCanceladoEn(null);
			cargo.setCanceladoPorUsuarioId(null);
			cargo.setMotivoCancelacion(null);
			ProductoAlumno guardado = productoAlumnoRepository.save(cargo);
			registroCargos.registrar(com.taemoi.project.services.RegistroAccionesCargos.Accion.CARGO_REACTIVADO,
					"/api/productos-alumno/" + id + "/reactivar", guardado.getSedeId(), id);
			return guardado;
		});
		return convertirADTO(reactivado);
	}

	/** Dentro de la transacción, con el cargo bloqueado y no pagado: lo marca cancelado y lo guarda. */
	private ProductoAlumno marcarCancelado(ProductoAlumno cargo, Long usuarioId, String motivo) {
		cargo.setCanceladoEn(java.time.LocalDateTime.now());
		cargo.setCanceladoPorUsuarioId(usuarioId);
		cargo.setMotivoCancelacion(motivo);
		return productoAlumnoRepository.save(cargo);
	}

	private static String motivoDeCancelacion(String motivo) {
		String limpio = motivo == null ? "" : motivo.trim();
		if (limpio.isEmpty()) {
			throw new IllegalArgumentException("El motivo es obligatorio.");
		}
		if (limpio.length() > MAX_MOTIVO_CANCELACION) {
			throw new IllegalArgumentException(
					"El motivo no puede superar " + MAX_MOTIVO_CANCELACION + " caracteres.");
		}
		return limpio;
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
		guardarCargoNuevo(productoAlumno, MetodoPago.SIN_INDICAR);

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
		guardarCargoNuevo(productoAlumno, MetodoPago.SIN_INDICAR);

		return convertirADTO(productoAlumno);
	}

	@Override
	public ResultadoGeneracionMensualidades cargarMensualidadesGenerales(String mesAno,
			LocalDate fechaAsignacionManual) {
		MensualidadUtils.formatearNombreMensualidad(mesAno); // valida el formato antes de nada
		YearMonth mes = YearMonth.parse(mesAno);

		// Obtener todos los AlumnoDeporte activos
		List<AlumnoDeporte> alumnosDeportes = alumnoDeporteRepository.findAll().stream()
				.filter(ad -> Boolean.TRUE.equals(ad.getActivo()))
				.collect(Collectors.toList());
		Map<String, Producto> productosMensualidad = resolverProductosMensualidad(alumnosDeportes);

		// Cargos existentes en lote (sin N+1): se buscan por clave exacta alumno-CONCEPTO
		Set<String> conceptosExistentes = clavesDeCargosExistentes();

		Date fechaAsignacion = resolverFechaAsignacion(fechaAsignacionManual);
		Set<Long> sedesInactivas = gestionPorSede.sedesInactivas();
		ResolutorCuantiaMensualidad cuantias = tarifaSede.preparar();
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
				Double precioMensualidad = cuantias.cuantiaMensualidad(alumnoDeporte, alumno, mes);

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
		return new ResultadoGeneracionMensualidades(omitidos.size(), cuantias.personalizadasSuperiores());
	}

	@Override
	public ResultadoGeneracionMensualidades cargarMensualidadesPorDeporte(String mesAno, String deporte,
			LocalDate fechaAsignacionManual) {
		MensualidadUtils.formatearNombreMensualidad(mesAno); // valida el formato antes de nada
		YearMonth mes = YearMonth.parse(mesAno);

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
		ResolutorCuantiaMensualidad cuantias = tarifaSede.preparar();
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
				Double precioMensualidad = cuantias.cuantiaMensualidad(alumnoDeporte, alumno, mes);

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
		return new ResultadoGeneracionMensualidades(omitidos.size(), cuantias.personalizadasSuperiores());
	}

	@Override
	public List<CuantiaPersonalizadaSuperiorDTO> cargarMensualidadIndividual(Long alumnoId, String mesAno, boolean forzar,
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
		ResolutorCuantiaMensualidad cuantias = tarifaSede.preparar();
		YearMonth mes = YearMonth.parse(mesAno);
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
				Double precioMensualidad = cuantias.cuantiaMensualidad(alumnoDeporte, alumno, mes);

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
		return cuantias.personalizadasSuperiores();
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

		guardarCargoNuevo(productoAlumno, MetodoPago.SIN_INDICAR);
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

		ProductoAlumno savedProductoAlumno = guardarCargoNuevo(productoAlumno, MetodoPago.SIN_INDICAR);

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
		Alumno alumno = productoAlumno.alumnoEfectivo();
		return alumno != null ? alumno.getId() : null;
	}

	private ProductoAlumnoDTO convertirADTO(ProductoAlumno productoAlumno) {
		return convertirADTO(productoAlumno, nombresDeQuienCancelo(List.of(productoAlumno)));
	}

	/**
	 * Pieza 6b-3: nombre visible (nombre y apellidos) de las cuentas que cancelaron esos cargos, con una sola consulta
	 * y ninguna si no hay cancelados. Mismo formato que el {@code entregadoPorNombre} de la caja.
	 */
	private java.util.Map<Long, String> nombresDeQuienCancelo(java.util.Collection<ProductoAlumno> cargos) {
		java.util.Map<Long, String> nombres = new java.util.HashMap<>();
		List<Long> ids = cargos.stream().map(ProductoAlumno::getCanceladoPorUsuarioId)
				.filter(java.util.Objects::nonNull).distinct().toList();
		if (ids.isEmpty()) {
			return nombres;
		}
		for (Object[] f : usuarioRepository.nombresPorIds(ids)) {
			String nombre = ((f[1] == null ? "" : f[1]) + " " + (f[2] == null ? "" : f[2])).trim();
			nombres.put((Long) f[0], nombre);
		}
		return nombres;
	}

	private ProductoAlumnoDTO convertirADTO(ProductoAlumno productoAlumno, java.util.Map<Long, String> nombres) {
		ProductoAlumnoDTO dto = new ProductoAlumnoDTO();
		dto.setId(productoAlumno.getId());
		Alumno alumno = productoAlumno.alumnoEfectivo();
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
		// Pieza 6b-2c: solo si está cancelado (NON_NULL en el DTO: un cargo vigente responde igual que antes).
		dto.setCanceladoEn(productoAlumno.getCanceladoEn());
		dto.setCanceladoPorUsuarioId(productoAlumno.getCanceladoPorUsuarioId());
		dto.setMotivoCancelacion(productoAlumno.getMotivoCancelacion());
		if (productoAlumno.getCanceladoPorUsuarioId() != null) {
			String nombre = nombres.get(productoAlumno.getCanceladoPorUsuarioId());
			dto.setCanceladoPorNombre(nombre == null || nombre.isEmpty() ? null : nombre);
		}
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

		ProductoAlumno savedProductoAlumno = guardarCargoNuevo(productoAlumno, detallesDTO.getMetodoPago());

		return convertirADTO(savedProductoAlumno);
	}

	@Override
	public ResultadoGeneracionMensualidades cargarMensualidadesMultiDeporte(String mesAno) {
		MensualidadUtils.formatearNombreMensualidad(mesAno); // valida el formato antes de nada
		YearMonth mes = YearMonth.parse(mesAno);

		// Obtener todos los AlumnoDeporte activos
		List<com.taemoi.project.entities.AlumnoDeporte> alumnosDeportes =
			alumnoDeporteRepository.findAll().stream()
				.filter(ad -> ad.getActivo() != null && ad.getActivo())
				.collect(Collectors.toList());
		Map<String, Producto> productosMensualidad = resolverProductosMensualidad(alumnosDeportes);

		Date fechaAsignacion = new Date();
		Set<Long> sedesInactivas = gestionPorSede.sedesInactivas();
		ResolutorCuantiaMensualidad cuantias = tarifaSede.preparar();
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
				Double precioMensualidad = cuantias.cuantiaMensualidad(alumnoDeporte, alumno, mes);

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
		return new ResultadoGeneracionMensualidades(omitidos.size(), cuantias.personalizadasSuperiores());
	}

	@Override
	public List<CuantiaPersonalizadaSuperiorDTO> cargarMensualidadIndividualPorDeporte(Long alumnoId, String deporte, String mesAno, boolean forzar,
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
		List<CuantiaPersonalizadaSuperiorDTO> superiores = List.of();

		if (!mensualidadExiste) {
			ResolutorCuantiaMensualidad cuantias = tarifaSede.preparar();
			Double precioMensualidad = cuantias.cuantiaMensualidad(alumnoDeporte, alumno, YearMonth.parse(mesAno));
			superiores = cuantias.personalizadasSuperiores();

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
		return superiores;
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
