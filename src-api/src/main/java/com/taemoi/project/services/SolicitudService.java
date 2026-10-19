package com.taemoi.project.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.taemoi.project.acceso.GestionPorSede;
import com.taemoi.project.dtos.request.CajaRequests;
import com.taemoi.project.dtos.request.SolicitudRequests;
import com.taemoi.project.dtos.response.SolicitudDTOs;
import com.taemoi.project.entities.Cobro;
import com.taemoi.project.entities.EntregaCaja;
import com.taemoi.project.entities.EstadoCobro;
import com.taemoi.project.entities.EstadoEntregaCaja;
import com.taemoi.project.entities.EstadoMovimientoCaja;
import com.taemoi.project.entities.EstadoSolicitud;
import com.taemoi.project.entities.MovimientoCaja;
import com.taemoi.project.entities.OrigenCobro;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Solicitud;
import com.taemoi.project.entities.TipoSolicitud;
import com.taemoi.project.exceptions.producto.ProductoNoEncontradoException;
import com.taemoi.project.organizacion.AlcanceSesion;
import com.taemoi.project.repositories.CobroRepository;
import com.taemoi.project.repositories.EntregaCajaRepository;
import com.taemoi.project.repositories.MovimientoCajaRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.repositories.SolicitudRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

/**
 * Solicitudes al propietario (pieza 6c-2, docs/multi-escuela/fase3-6c-solicitudes.md y fase3-6c-plan.md §3): pedir los
 * tipos 1 (con o sin cobro, decisión 191) y 3, retirar, denegar, la bandeja por segmento y el contador. Aceptar (tipos 1
 * a 3) es de la 6c-3 ({@link #aceptar}, plan §4); los encargos, de la 6c-5 ({@link EncargoService}, plan §4b).
 *
 * <ul>
 * <li><b>Pedir</b> no cambia nada más (el pago sigue marcado, la caja igual). Se valida el recurso: existe en la
 * escuela (404), para el responsable es de sus sedes (403), está en un estado que se puede pedir (409) y no tiene otra
 * solicitud pendiente (409; la clave única de V31_8 es la última red ante dos peticiones a la vez).</li>
 * <li><b>Retirar</b> (solo quien pidió) y <b>denegar</b> (solo el propietario, con respuesta) bloquean la fila
 * ({@link SolicitudRepository#bloquearPorId}), la releen y exigen la transición ({@link SolicitudTransiciones}): de dos
 * a la vez, una gana y la otra recibe 409 {@value SolicitudTransiciones#NO_PENDIENTE}.</li>
 * <li><b>Bandeja</b>: P ve todas las de los tipos 1 a 3; G y R, las suyas. Encargos: P y G todos; R, los de sus
 * sedes. El <b>contador</b> cuenta lo que espera acción de la sesión (§5.1).</li>
 * </ul>
 * Toda escritura queda en el registro de acciones ({@link RegistroAccionesSolicitudes}) en su misma transacción, solo
 * con ids.
 */
@Service
public class SolicitudService {

	/** Segmentos de la bandeja (§5.1). */
	public enum Segmento {
		PENDIENTES, ENCARGOS, RESUELTAS;

		/** Del parámetro de la petición; vacío o desconocido → 400. */
		public static Segmento de(String valor) {
			if (valor != null) {
				for (Segmento segmento : values()) {
					if (segmento.name().equals(valor.trim())) {
						return segmento;
					}
				}
			}
			throw new IllegalArgumentException("Indica el segmento: PENDIENTES, ENCARGOS o RESUELTAS.");
		}
	}

	public static final String PROPIETARIO_LO_HACE_DIRECTO = "El propietario lo hace directamente: no necesita pedirlo.";
	public static final String YA_PENDIENTE = "Ya hay una solicitud pendiente sobre este recurso.";
	/** 409 de una violación de integridad al crear que no es la clave única (el recurso cambió a la vez). */
	public static final String RECURSO_CAMBIADO = "El recurso ha cambiado mientras se pedía: vuelve a intentarlo.";
	/** Pieza 6c-3: 400 al aceptar un encargo (se marca encargado, 6c-5). */
	public static final String ENCARGO_NO_SE_ACEPTA = "Un encargo no se acepta: se marca encargado.";
	/** Pieza 6c-4: 409 al pedir deshacer un cobro que aún no va en una entrega (lo deshace él mismo, A4). */
	public static final String COBRO_SIN_ENTREGA = "El cobro no va en una entrega: deshazlo tú mismo desde lo que "
			+ "llevas encima.";
	/** Pieza 6c-4: 409 al pedir deshacer un cobro cuyo cargo se borró. */
	public static final String CARGO_DEL_COBRO_NO_EXISTE = "El cargo del cobro ya no existe.";
	/** MySQL: entrada duplicada en una clave única. */
	private static final int MYSQL_CLAVE_DUPLICADA = 1062;
	/** Las resueltas que devuelve la bandeja, las más recientes. */
	static final int MAX_RESUELTAS = 200;

	private static final int MAX_TEXTO = 500;
	private static final Set<String> CLAVES_PENDIENTE = Set.of("uk_solicitud_cobro_pendiente",
			"uk_solicitud_entrega_pendiente", "uk_solicitud_movimiento_pendiente", "uk_solicitud_cargo_pendiente");
	private static final Set<EstadoSolicitud> ENCARGO_EN_CURSO = Set.of(EstadoSolicitud.PEDIDO,
			EstadoSolicitud.ENCARGADO, EstadoSolicitud.RECIBIDO);
	private static final Set<EstadoSolicitud> ENCARGO_ESPERA_ACCION = Set.of(EstadoSolicitud.PEDIDO,
			EstadoSolicitud.ENCARGADO);
	private static final Set<EstadoSolicitud> ENCARGO_CERRADO = Set.of(EstadoSolicitud.ENTREGADO,
			EstadoSolicitud.RECHAZADO, EstadoSolicitud.RETIRADA);

	private final SolicitudRepository solicitudes;
	private final CobroRepository cobros;
	private final ProductoAlumnoRepository cargos;
	private final EntregaCajaRepository entregas;
	private final MovimientoCajaRepository movimientos;
	private final GestionPorSede gestionPorSede;
	private final RegistroAccionesSolicitudes registro;
	private final ProductoAlumnoService productosAlumno;
	private final CajaService caja;
	private final CobroService cobroService;
	private final TransactionTemplate transaccion;

	@PersistenceContext
	private EntityManager em;

	public SolicitudService(SolicitudRepository solicitudes, CobroRepository cobros, ProductoAlumnoRepository cargos,
			EntregaCajaRepository entregas, MovimientoCajaRepository movimientos, GestionPorSede gestionPorSede,
			RegistroAccionesSolicitudes registro, ProductoAlumnoService productosAlumno, CajaService caja,
			CobroService cobroService, PlatformTransactionManager transacciones) {
		this.solicitudes = solicitudes;
		this.cobros = cobros;
		this.cargos = cargos;
		this.entregas = entregas;
		this.movimientos = movimientos;
		this.gestionPorSede = gestionPorSede;
		this.registro = registro;
		this.productosAlumno = productosAlumno;
		this.caja = caja;
		this.cobroService = cobroService;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
	}

	// ------------------------------------------------------------------ pedir

	/** Pide al propietario (tipos 1 y 3). Ver la cabecera para las validaciones. */
	public SolicitudDTOs.Solicitud crear(SolicitudRequests.Crear peticion) {
		if (peticion == null || peticion.tipo() == null) {
			throw new IllegalArgumentException("Indica el tipo de solicitud.");
		}
		if (tieneAutoridad(Roles.ROLE_ADMIN)) {
			throw new IllegalArgumentException(PROPIETARIO_LO_HACE_DIRECTO);
		}
		String motivo = texto(peticion.motivo(), "El motivo");
		if (motivo == null) {
			throw new IllegalArgumentException("El motivo es obligatorio.");
		}
		TipoSolicitud tipo = peticion.tipo();
		exigirRecursoDelTipo(peticion);
		Long usuario = usuarioDeLaSesion();
		try {
			return enTransaccion(() -> {
				Solicitud solicitud = new Solicitud();
				solicitud.setTipo(tipo);
				solicitud.setEstado(SolicitudTransiciones.estadoInicial(tipo));
				solicitud.setSolicitadoPorUsuarioId(usuario);
				solicitud.setSolicitadoEn(LocalDateTime.now(CajaService.ZONA));
				solicitud.setMotivo(motivo);
				switch (tipo) {
				case REVERTIR_COBRO -> rellenarRevertir(solicitud, peticion);
				case ANULAR_ENTREGA -> rellenarEntrega(solicitud, peticion.entregaId());
				case ANULAR_MOVIMIENTO -> rellenarMovimiento(solicitud, peticion.movimientoId());
				default -> throw new IllegalArgumentException("Esta solicitud no se pide aquí.");
				}
				Solicitud guardada = solicitudes.saveAndFlush(solicitud);
				registro.registrar(RegistroAccionesSolicitudes.Accion.SOLICITUD_CREADA, "/api/solicitudes", guardada);
				em.flush();
				return dtos(List.of(guardada), usuario).get(0);
			});
		} catch (DataIntegrityViolationException error) {
			throw traducirViolacionAlCrear(error);
		}
	}

	/**
	 * Red de la clave única de V31_8 al crear (dos peticiones a la vez sobre el mismo recurso pasan las dos la
	 * comprobación previa; la segunda choca al insertar). Nunca un 500: clave única (por su nombre o por el error 1062
	 * de MySQL) → 409 {@value #YA_PENDIENTE}; cualquier otra violación (p. ej. la FK de un cargo borrado a la vez) → 409
	 * {@value #RECURSO_CAMBIADO}. La transacción ya se deshizo: no queda nada escrito.
	 */
	static IllegalStateException traducirViolacionAlCrear(DataIntegrityViolationException error) {
		if (esClavePendiente(error) || esClaveUnica(error)) {
			return new IllegalStateException(YA_PENDIENTE, error);
		}
		return new IllegalStateException(RECURSO_CAMBIADO, error);
	}

	/** Un solo recurso, el de su tipo (400 si no). */
	private static void exigirRecursoDelTipo(SolicitudRequests.Crear p) {
		boolean valido = switch (p.tipo()) {
		case REVERTIR_COBRO -> (p.cobroId() != null) != (p.productoAlumnoId() != null) && p.entregaId() == null
				&& p.movimientoId() == null;
		case ANULAR_ENTREGA -> p.entregaId() != null && p.cobroId() == null && p.productoAlumnoId() == null
				&& p.movimientoId() == null;
		case ANULAR_MOVIMIENTO -> p.movimientoId() != null && p.cobroId() == null && p.productoAlumnoId() == null
				&& p.entregaId() == null;
		default -> throw new IllegalArgumentException("Esta solicitud no se pide aquí.");
		};
		if (!valido) {
			throw new IllegalArgumentException("Indica un solo recurso, el que corresponde al tipo de solicitud.");
		}
	}

	/**
	 * Tipo 1. Con {@code cobroId}, o con {@code productoAlumnoId} cuyo cargo tiene cobro ACTIVO: cobro ACTIVO, sin
	 * entrega y con su cargo. Sin cobro (191): el cargo está pagado. Una pendiente por cobro y por cargo.
	 */
	private void rellenarRevertir(Solicitud solicitud, SolicitudRequests.Crear p) {
		Cobro cobro;
		ProductoAlumno cargo = null;
		if (p.cobroId() != null) {
			cobro = cobros.findById(p.cobroId()).orElseThrow(() -> new NoSuchElementException("El cobro no existe."));
		} else {
			cargo = cargos.findById(p.productoAlumnoId())
					.orElseThrow(() -> new NoSuchElementException("El cargo no existe."));
			cobro = cobros.findFirstByProductoAlumnoIdAndEstado(cargo.getId(), EstadoCobro.ACTIVO).orElse(null);
		}
		if (cobro != null) {
			gestionPorSede.exigirSedePropia(cobro.getSedeId());
			if (cobro.getEstado() != EstadoCobro.ACTIVO) {
				throw new IllegalStateException("El cobro ya no está activo.");
			}
			if (cobro.getEntregaCajaId() != null) {
				throw new IllegalStateException("El cobro ya va en una entrega de caja: pide anular la entrega.");
			}
			if (cobro.getProductoAlumnoId() == null) {
				throw new IllegalStateException("El cargo del cobro ya no existe.");
			}
			if (solicitudes.existsByCobroIdAndEstado(cobro.getId(), EstadoSolicitud.PENDIENTE)
					|| solicitudes.existeRevertirPendienteDeCargo(cobro.getProductoAlumnoId())) {
				throw new IllegalStateException(YA_PENDIENTE);
			}
			solicitud.setCobroId(cobro.getId());
			solicitud.setProductoAlumnoId(cobro.getProductoAlumnoId());
			solicitud.setSedeId(cobro.getSedeId());
			solicitud.setAlumnoId(cobro.getAlumnoId());
			solicitud.setConcepto(cobro.getConcepto());
			solicitud.setImporte(dosDecimales(cobro.getImporte()));
			return;
		}
		gestionPorSede.exigirSedePropia(cargo.getSedeId());
		if (!Boolean.TRUE.equals(cargo.getPagado())) {
			throw new IllegalStateException("El cargo no está pagado.");
		}
		if (solicitudes.existeRevertirPendienteDeCargo(cargo.getId())) {
			throw new IllegalStateException(YA_PENDIENTE);
		}
		solicitud.setProductoAlumnoId(cargo.getId());
		solicitud.setSedeId(cargo.getSedeId());
		solicitud.setAlumnoId(CobroService.alumnoDe(cargo));
		solicitud.setConcepto(cargo.getConcepto());
		solicitud.setImporte(CobroService.importeDe(cargo));
	}

	/** Tipo 3, entrega: ACTIVA; importe = lo declarado. */
	private void rellenarEntrega(Solicitud solicitud, Long entregaId) {
		EntregaCaja entrega = entregas.findById(entregaId)
				.orElseThrow(() -> new NoSuchElementException("La entrega no existe."));
		gestionPorSede.exigirSedePropia(entrega.getSedeId());
		if (entrega.getEstado() != EstadoEntregaCaja.ACTIVA) {
			throw new IllegalStateException("La entrega ya está anulada.");
		}
		if (solicitudes.existsByEntregaCajaIdAndEstado(entrega.getId(), EstadoSolicitud.PENDIENTE)) {
			throw new IllegalStateException(YA_PENDIENTE);
		}
		solicitud.setEntregaCajaId(entrega.getId());
		solicitud.setSedeId(entrega.getSedeId());
		solicitud.setImporte(dosDecimales(entrega.getImporteDeclarado()));
	}

	/** Tipo 3, movimiento: ACTIVO y suelto (el automático de una entrega se anula con ella). */
	private void rellenarMovimiento(Solicitud solicitud, Long movimientoId) {
		MovimientoCaja movimiento = movimientos.findById(movimientoId)
				.orElseThrow(() -> new NoSuchElementException("El movimiento no existe."));
		gestionPorSede.exigirSedePropia(movimiento.getSedeId());
		if (movimiento.getEstado() != EstadoMovimientoCaja.ACTIVO) {
			throw new IllegalStateException("El movimiento ya está anulado.");
		}
		if (movimiento.getEntregaCajaId() != null) {
			throw new IllegalStateException(EjecucionSolicitud.razonMovimientoDeEntrega(movimiento.getTipo()));
		}
		if (solicitudes.existsByMovimientoCajaIdAndEstado(movimiento.getId(), EstadoSolicitud.PENDIENTE)) {
			throw new IllegalStateException(YA_PENDIENTE);
		}
		solicitud.setMovimientoCajaId(movimiento.getId());
		solicitud.setSedeId(movimiento.getSedeId());
		solicitud.setAlumnoId(movimiento.getAlumnoId());
		solicitud.setImporte(dosDecimales(movimiento.getImporte()));
	}

	/**
	 * Pieza 6c-4 (decisión 193, fase3-6c-plan.md §4d): el profesor de la sesión pide deshacer un cobro suyo ya entregado
	 * a la sede (tipo 2). Valida lo mismo que la 6c-3 revalida al aceptar: motivo (400), el cobro existe (404), es suyo
	 * con origen PROFESOR (403; ya lo exige antes {@code @acceso.cobroPropioDelProfesor}), ACTIVO, en una entrega
	 * ACTIVA, con su cargo, sin devolución ni devolución pendiente y con el cargo pagado (409); con NO_HUBO_VENTA, que
	 * fue su venta (400, como A4); y ninguna otra solicitud pendiente sobre el cobro (409; la clave única de V31_8 es la
	 * red). No bloquea filas (como los tipos 1 y 3): si el estado cambia antes de aceptar, la 6c-3 la cierra
	 * {@code NO_APLICABLE}. Sede = la del cobro (la entrega es de la misma sede). Sin {@code motivoDeshacer},
	 * {@code AUN_NO_PAGADO}. Registro {@code SOLICITUD_CREADA} con la entrega, solo ids.
	 */
	public SolicitudDTOs.Solicitud pedirDeshacer(Long cobroId, SolicitudRequests.PedirDeshacer peticion) {
		String motivo = texto(peticion == null ? null : peticion.motivo(), "El motivo");
		if (motivo == null) {
			throw new IllegalArgumentException("El motivo es obligatorio.");
		}
		CajaRequests.MotivoDeshacer motivoDeshacer = peticion.motivoDeshacer() == null
				? CajaRequests.MotivoDeshacer.AUN_NO_PAGADO
				: peticion.motivoDeshacer();
		Long usuario = usuarioDeLaSesion();
		String endpoint = "/api/caja/profesor/cobros/" + cobroId + "/pedir-deshacer";
		try {
			return enTransaccion(() -> {
				Cobro cobro = (cobroId == null ? Optional.<Cobro>empty() : cobros.findById(cobroId))
						.orElseThrow(() -> new NoSuchElementException("El cobro no existe."));
				if (cobro.getOrigen() != OrigenCobro.PROFESOR || !usuario.equals(cobro.getCobradoPorUsuarioId())) {
					throw new AccessDeniedException("Acceso denegado.");
				}
				if (cobro.getEstado() != EstadoCobro.ACTIVO) {
					throw new IllegalStateException(EjecucionSolicitud.COBRO_NO_ACTIVO);
				}
				if (cobro.getEntregaCajaId() == null) {
					throw new IllegalStateException(COBRO_SIN_ENTREGA);
				}
				EntregaCaja entrega = entregas.findById(cobro.getEntregaCajaId()).orElse(null);
				if (entrega == null || entrega.getEstado() != EstadoEntregaCaja.ACTIVA) {
					throw new IllegalStateException(EjecucionSolicitud.ENTREGA_ANULADA);
				}
				ProductoAlumno cargo = cobro.getProductoAlumnoId() == null ? null
						: cargos.findById(cobro.getProductoAlumnoId()).orElse(null);
				if (cargo == null) {
					throw new IllegalStateException(CARGO_DEL_COBRO_NO_EXISTE);
				}
				cobroService.razonDeDevolucion(cobro.getId(), cargo.getId()).ifPresent(razon -> {
					throw new IllegalStateException(razon);
				});
				if (!Boolean.TRUE.equals(cargo.getPagado())) {
					throw new IllegalStateException(EjecucionSolicitud.CARGO_NO_PAGADO);
				}
				if (motivoDeshacer == CajaRequests.MotivoDeshacer.NO_HUBO_VENTA
						&& !productosAlumno.cobrosQueSonVentaDelProfesor(List.of(cobro)).contains(cobro.getId())) {
					throw new IllegalArgumentException(EjecucionSolicitud.NO_ES_VENTA);
				}
				if (solicitudes.existsByCobroIdAndEstado(cobro.getId(), EstadoSolicitud.PENDIENTE)) {
					throw new IllegalStateException(YA_PENDIENTE);
				}
				Solicitud solicitud = new Solicitud();
				solicitud.setTipo(TipoSolicitud.DESHACER_COBRO_ENTREGADO);
				solicitud.setEstado(SolicitudTransiciones.estadoInicial(TipoSolicitud.DESHACER_COBRO_ENTREGADO));
				solicitud.setSolicitadoPorUsuarioId(usuario);
				solicitud.setSolicitadoEn(LocalDateTime.now(CajaService.ZONA));
				solicitud.setMotivo(motivo);
				solicitud.setMotivoDeshacer(motivoDeshacer);
				solicitud.setCobroId(cobro.getId());
				solicitud.setProductoAlumnoId(cargo.getId());
				solicitud.setSedeId(cobro.getSedeId());
				solicitud.setAlumnoId(cobro.getAlumnoId());
				solicitud.setConcepto(cobro.getConcepto());
				solicitud.setImporte(dosDecimales(cobro.getImporte()));
				Solicitud guardada = solicitudes.saveAndFlush(solicitud);
				registro.registrar(RegistroAccionesSolicitudes.Accion.SOLICITUD_CREADA, endpoint, guardada,
						Map.of("entrega", entrega.getId()));
				em.flush();
				return dtos(List.of(guardada), usuario).get(0);
			});
		} catch (DataIntegrityViolationException error) {
			throw traducirViolacionAlCrear(error);
		}
	}

	// ------------------------------------------------------------------ retirar y denegar

	/** Solo quien la pidió (403 si no), mientras esté pendiente (409). Un encargo se retira desde Mi caja (400). */
	public SolicitudDTOs.Solicitud retirar(Long id) {
		Long usuario = usuarioDeLaSesion();
		return enTransaccion(() -> {
			Solicitud solicitud = bloquear(id);
			if (!usuario.equals(solicitud.getSolicitadoPorUsuarioId())) {
				throw new AccessDeniedException("Acceso denegado.");
			}
			if (solicitud.getTipo() == TipoSolicitud.ENCARGO) {
				throw new IllegalArgumentException("Un encargo se retira desde Mi caja.");
			}
			SolicitudTransiciones.exigir(solicitud, EstadoSolicitud.RETIRADA);
			resolver(solicitud, EstadoSolicitud.RETIRADA, usuario, null);
			registro.registrar(RegistroAccionesSolicitudes.Accion.SOLICITUD_RETIRADA,
					"/api/solicitudes/" + id + "/retirar", solicitud);
			em.flush();
			return dtos(List.of(solicitud), usuario).get(0);
		});
	}

	/**
	 * Solo el propietario, con respuesta (400 sin ella), mientras esté pendiente (409). Un encargo no se deniega: se
	 * rechaza (6c-5), 400.
	 */
	public SolicitudDTOs.Solicitud denegar(Long id, String respuesta) {
		if (!tieneAutoridad(Roles.ROLE_ADMIN)) {
			throw new AccessDeniedException("Acceso denegado.");
		}
		String texto = texto(respuesta, "La respuesta");
		if (texto == null) {
			throw new IllegalArgumentException("La respuesta es obligatoria.");
		}
		Long usuario = usuarioDeLaSesion();
		return enTransaccion(() -> {
			Solicitud solicitud = bloquear(id);
			if (solicitud.getTipo() == TipoSolicitud.ENCARGO) {
				throw new IllegalArgumentException("Un encargo no se deniega: se rechaza.");
			}
			SolicitudTransiciones.exigir(solicitud, EstadoSolicitud.DENEGADA);
			resolver(solicitud, EstadoSolicitud.DENEGADA, usuario, texto);
			registro.registrar(RegistroAccionesSolicitudes.Accion.SOLICITUD_DENEGADA,
					"/api/solicitudes/" + id + "/denegar", solicitud);
			em.flush();
			return dtos(List.of(solicitud), usuario).get(0);
		});
	}

	// ------------------------------------------------------------------ aceptar (6c-3)

	/**
	 * Pieza 6c-3 (decisión 189, §6.5): solo el propietario. Con la fila bloqueada y releída exige PENDIENTE (409
	 * {@value SolicitudTransiciones#NO_PENDIENTE}; un encargo, 400) y, en la MISMA transacción, revalida y ejecuta la
	 * acción de siempre con su identidad (tipo 1, A1; tipo 2, la 193; tipo 3, A7 o A9). Si ya no aplica, la cierra
	 * {@code NO_APLICABLE} con la razón y no toca nada más. Registro {@code SOLICITUD_ACEPTADA} o
	 * {@code SOLICITUD_NO_APLICABLE}, más el de la acción ejecutada con {@code solicitud=#n}.
	 *
	 * <p>Orden de bloqueos: la solicitud primero y después los de la acción (cargo → cobro; cargo → entrega → cobro;
	 * entrega → cobros; movimiento). Las demás vías de solicitudes y de caja no bloquean una solicitud después de esas
	 * filas. Excepción: borrar un cargo (quitar de una convocatoria, borrar un alumno o una convocatoria) pasa por las
	 * FK ON DELETE SET NULL de {@code solicitud} (V31_8) y bloquea sus filas DESPUÉS del cargo. Ese cruce con aceptar
	 * puede producir un interbloqueo: InnoDB lo detecta y elige una víctima, que {@code GlobalExceptionHandler} devuelve
	 * como 409 (concurrencia) y cuya transacción se deshace entera. Si la víctima es aceptar, la solicitud sigue
	 * PENDIENTE y al reintentar queda NO_APLICABLE (el cargo ya no existe). Sin efecto en el dinero.
	 *
	 * <p>Red: si la ejecución lanza un error de negocio pese a la revalidación, se deshace todo y, en otra transacción,
	 * la solicitud (si sigue PENDIENTE) se cierra {@code NO_APLICABLE} con ese mensaje: no se queda pendiente. Los
	 * efectos de un cambio de pago (convocatoria, derecho a examen) van después de confirmar, como en A1.
	 */
	public SolicitudDTOs.Solicitud aceptar(Long id) {
		if (!tieneAutoridad(Roles.ROLE_ADMIN)) {
			throw new AccessDeniedException("Acceso denegado.");
		}
		Long usuario = usuarioDeLaSesion();
		Aceptacion aceptacion;
		try {
			aceptacion = enTransaccion(() -> {
				Solicitud solicitud = bloquear(id);
				if (solicitud.getTipo() == TipoSolicitud.ENCARGO) {
					throw new IllegalArgumentException(ENCARGO_NO_SE_ACEPTA);
				}
				SolicitudTransiciones.exigir(solicitud, EstadoSolicitud.ACEPTADA);
				EjecucionSolicitud ejecucion;
				try {
					ejecucion = ejecutar(solicitud);
				} catch (IllegalStateException | IllegalArgumentException | NoSuchElementException
						| ProductoNoEncontradoException error) {
					throw new EjecucionRechazada(error);
				}
				cerrar(solicitud, ejecucion, usuario);
				em.flush();
				return new Aceptacion(dtos(List.of(solicitud), usuario).get(0), ejecucion);
			});
		} catch (EjecucionRechazada rechazo) {
			String mensaje = rechazo.getCause().getMessage();
			String razon = mensaje == null || mensaje.isBlank() ? EjecucionSolicitud.YA_NO_APLICA
					: mensaje.length() > MAX_TEXTO ? mensaje.substring(0, MAX_TEXTO) : mensaje;
			return enTransaccion(() -> {
				Solicitud solicitud = bloquear(id);
				SolicitudTransiciones.exigir(solicitud, EstadoSolicitud.NO_APLICABLE);
				cerrar(solicitud, EjecucionSolicitud.noAplica(razon), usuario);
				em.flush();
				return dtos(List.of(solicitud), usuario).get(0);
			});
		}
		productosAlumno.aplicarEfectosTrasSolicitud(aceptacion.ejecucion());
		return aceptacion.solicitud();
	}

	/** La acción de la solicitud (bloqueada y PENDIENTE), dentro de su transacción. */
	private EjecucionSolicitud ejecutar(Solicitud s) {
		long solicitudId = s.getId();
		return switch (s.getTipo()) {
		case REVERTIR_COBRO -> productosAlumno.revertirPorSolicitud(s.getProductoAlumnoId(), s.getCobroId(),
				solicitudId, s.getMotivo());
		case DESHACER_COBRO_ENTREGADO -> productosAlumno.deshacerEntregadoPorSolicitud(s.getCobroId(),
				s.getProductoAlumnoId(), s.getSolicitadoPorUsuarioId(), s.getMotivoDeshacer(), solicitudId,
				s.getMotivo());
		case ANULAR_ENTREGA -> caja.anularEntregaPorSolicitud(s.getEntregaCajaId(), solicitudId, s.getMotivo());
		case ANULAR_MOVIMIENTO -> caja.anularMovimientoPorSolicitud(s.getMovimientoCajaId(), solicitudId,
				s.getMotivo());
		case ENCARGO -> throw new IllegalArgumentException(ENCARGO_NO_SE_ACEPTA);
		};
	}

	/** ACEPTADA (sin respuesta) o NO_APLICABLE (con la razón), resuelta por quien acepta, y su registro. */
	private void cerrar(Solicitud solicitud, EjecucionSolicitud ejecucion, Long usuario) {
		String endpoint = "/api/solicitudes/" + solicitud.getId() + "/aceptar";
		if (ejecucion.aplica()) {
			resolver(solicitud, EstadoSolicitud.ACEPTADA, usuario, null);
			registro.registrar(RegistroAccionesSolicitudes.Accion.SOLICITUD_ACEPTADA, endpoint, solicitud);
		} else {
			resolver(solicitud, EstadoSolicitud.NO_APLICABLE, usuario, ejecucion.noAplicable());
			registro.registrar(RegistroAccionesSolicitudes.Accion.SOLICITUD_NO_APLICABLE, endpoint, solicitud);
		}
	}

	private record Aceptacion(SolicitudDTOs.Solicitud solicitud, EjecucionSolicitud ejecucion) {
	}

	/** La ejecución falló con un error de negocio: la transacción se deshace y la solicitud se cierra NO_APLICABLE. */
	private static final class EjecucionRechazada extends RuntimeException {
		private static final long serialVersionUID = 1L;

		EjecucionRechazada(RuntimeException causa) {
			super(causa.getMessage(), causa);
		}
	}

	/**
	 * Dentro de una transacción: bloquea la fila (SELECT ... FOR UPDATE) y la devuelve releída de la base. No existe en
	 * la escuela activa → 404. Para la 6c-3, la 6c-4 y la 6c-5.
	 */
	public Solicitud bloquear(Long id) {
		if (id == null) {
			throw new NoSuchElementException("La solicitud no existe.");
		}
		Solicitud solicitud = solicitudes.bloquearPorId(id)
				.orElseThrow(() -> new NoSuchElementException("La solicitud no existe."));
		if (em.contains(solicitud)) {
			em.refresh(solicitud);
		}
		return solicitud;
	}

	private void resolver(Solicitud solicitud, EstadoSolicitud estado, Long usuario, String respuesta) {
		solicitud.setEstado(estado);
		solicitud.setResueltoPorUsuarioId(usuario);
		solicitud.setResueltoEn(LocalDateTime.now(CajaService.ZONA));
		solicitud.setRespuesta(respuesta);
		solicitudes.save(solicitud);
	}

	// ------------------------------------------------------------------ bandeja y contador

	/** La bandeja de la sesión por segmento (ver la cabecera). */
	public List<SolicitudDTOs.Solicitud> listar(Segmento segmento) {
		Long usuario = usuarioDeLaSesion();
		boolean propietario = tieneAutoridad(Roles.ROLE_ADMIN);
		Optional<AlcanceSesion> restriccion = gestionPorSede.restriccion();
		Specification<Solicitud> tipos1a3 = noEncargo().and(propietario ? todas() : suyas(usuario));
		Specification<Solicitud> encargos = encargo().and(encargosVisibles(restriccion));
		Specification<Solicitud> filtro = switch (segmento) {
		case PENDIENTES -> tipos1a3.and(enEstados(Set.of(EstadoSolicitud.PENDIENTE)));
		case ENCARGOS -> encargos.and(enEstados(ENCARGO_EN_CURSO).or(devolucionPendiente(true)));
		case RESUELTAS -> tipos1a3.and(Specification.not(enEstados(Set.of(EstadoSolicitud.PENDIENTE))))
				.or(encargos.and(enEstados(ENCARGO_CERRADO)).and(devolucionPendiente(false)));
		};
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Solicitud> consulta = cb.createQuery(Solicitud.class);
		Root<Solicitud> raiz = consulta.from(Solicitud.class);
		consulta.where(filtro.toPredicate(raiz, consulta, cb));
		if (segmento == Segmento.RESUELTAS) {
			consulta.orderBy(cb.desc(cb.coalesce(raiz.<LocalDateTime>get("resueltoEn"),
					cb.coalesce(raiz.<LocalDateTime>get("entregadoEn"), raiz.<LocalDateTime>get("solicitadoEn")))),
					cb.desc(raiz.get("id")));
		} else {
			consulta.orderBy(cb.asc(raiz.get("solicitadoEn")), cb.asc(raiz.get("id")));
		}
		TypedQuery<Solicitud> query = em.createQuery(consulta);
		if (segmento == Segmento.RESUELTAS) {
			query.setMaxResults(MAX_RESUELTAS);
		}
		return dtos(query.getResultList(), usuario);
	}

	/**
	 * Lo que espera acción de la sesión (§5.1, D6c-4), con un {@code COUNT} por índice (dos para el propietario): P,
	 * tipos 1 a 3 PENDIENTES + encargos PEDIDO o ENCARGADO; G, los encargos; R, los encargos de sus sedes. Lo que uno
	 * pidió no cuenta. Pieza 6c-6 (195): más los encargos visibles con la devolución pendiente que le tocan a la sede
	 * (el dinero ya está en su caja); los que aún lleva el profesor le tocan a él (Mi caja).
	 */
	public long contador() {
		usuarioDeLaSesion();
		Optional<AlcanceSesion> restriccion = gestionPorSede.restriccion();
		long total = solicitudes
				.count(encargo().and(encargosVisibles(restriccion)).and(enEstados(ENCARGO_ESPERA_ACCION)));
		total += solicitudes.count(encargo().and(encargosVisibles(restriccion)).and(devolucionPendiente(true))
				.and(devolucionEnLaSede()));
		if (tieneAutoridad(Roles.ROLE_ADMIN)) {
			total += solicitudes.count(noEncargo().and(enEstados(Set.of(EstadoSolicitud.PENDIENTE))));
		}
		return total;
	}

	private static Specification<Solicitud> todas() {
		return (root, query, cb) -> cb.conjunction();
	}

	private static Specification<Solicitud> noEncargo() {
		return (root, query, cb) -> cb.notEqual(root.get("tipo"), TipoSolicitud.ENCARGO);
	}

	private static Specification<Solicitud> encargo() {
		return (root, query, cb) -> cb.equal(root.get("tipo"), TipoSolicitud.ENCARGO);
	}

	private static Specification<Solicitud> suyas(Long usuario) {
		return (root, query, cb) -> cb.equal(root.get("solicitadoPorUsuarioId"), usuario);
	}

	private static Specification<Solicitud> enEstados(Collection<EstadoSolicitud> estados) {
		return (root, query, cb) -> root.get("estado").in(estados);
	}

	private static Specification<Solicitud> devolucionPendiente(boolean valor) {
		return (root, query, cb) -> cb.equal(root.get("devolucionPendiente"), valor);
	}

	/**
	 * Pieza 6c-6: el cobro ACTIVO del cargo del encargo ya está en la caja de la sede (origen distinto de PROFESOR, o
	 * entregado): la devolución la registra la sede (195).
	 */
	private static Specification<Solicitud> devolucionEnLaSede() {
		return (root, query, cb) -> {
			jakarta.persistence.criteria.Subquery<Long> cobro = query.subquery(Long.class);
			Root<Cobro> c = cobro.from(Cobro.class);
			cobro.select(c.get("id")).where(cb.equal(c.get("productoAlumnoId"), root.get("productoAlumnoId")),
					cb.equal(c.get("estado"), EstadoCobro.ACTIVO),
					cb.or(cb.notEqual(c.get("origen"), com.taemoi.project.entities.OrigenCobro.PROFESOR),
							cb.isNotNull(c.get("entregaCajaId"))));
			return cb.exists(cobro);
		};
	}

	/** Gestión (alcance total): todos los encargos; responsable: los de sus sedes (sin sede, ninguno). */
	private static Specification<Solicitud> encargosVisibles(Optional<AlcanceSesion> restriccion) {
		if (restriccion.isEmpty()) {
			return todas();
		}
		Set<Long> sedes = restriccion.get().sedes();
		if (sedes.isEmpty()) {
			return (root, query, cb) -> cb.disjunction();
		}
		return (root, query, cb) -> root.get("sedeId").in(sedes);
	}

	// ------------------------------------------------------------------ utilidades

	/**
	 * DTOs con los nombres de cuentas, alumnos y sedes en tres consultas para toda la lista. De paquete: también los
	 * usa {@link EncargoService} (6c-5).
	 */
	List<SolicitudDTOs.Solicitud> dtos(List<Solicitud> lista, Long usuario) {
		if (lista.isEmpty()) {
			return List.of();
		}
		List<Long> cuentas = new ArrayList<>();
		lista.forEach(s -> {
			cuentas.add(s.getSolicitadoPorUsuarioId());
			cuentas.add(s.getResueltoPorUsuarioId());
		});
		Map<Long, String> nombresCuentas = new HashMap<>();
		for (Object[] f : filas("SELECT u.id, u.nombre, u.apellidos FROM Usuario u WHERE u.id IN :ids", cuentas)) {
			nombresCuentas.put((Long) f[0], ((f[1] == null ? "" : f[1]) + " " + (f[2] == null ? "" : f[2])).trim());
		}
		Map<Long, String[]> alumnos = new HashMap<>();
		for (Object[] f : filas("SELECT a.id, a.nombre, a.apellidos FROM Alumno a WHERE a.id IN :ids",
				lista.stream().map(Solicitud::getAlumnoId).toList())) {
			alumnos.put((Long) f[0], new String[] { (String) f[1], (String) f[2] });
		}
		Map<Long, String> sedes = new HashMap<>();
		for (Object[] f : filas("SELECT s.id, s.nombre FROM Sede s WHERE s.id IN :ids",
				lista.stream().map(Solicitud::getSedeId).toList())) {
			sedes.put((Long) f[0], (String) f[1]);
		}
		// Pieza 6c-6: con la devolución pendiente, si el dinero ya está en la sede (una consulta para la lista).
		Map<Long, Boolean> enSede = new HashMap<>();
		for (Object[] f : filas("SELECT c.productoAlumnoId, c.origen, c.entregaCajaId FROM Cobro c "
				+ "WHERE c.productoAlumnoId IN :ids AND c.estado = com.taemoi.project.entities.EstadoCobro.ACTIVO",
				lista.stream().filter(Solicitud::isDevolucionPendiente).map(Solicitud::getProductoAlumnoId).toList())) {
			enSede.put((Long) f[0], f[1] != com.taemoi.project.entities.OrigenCobro.PROFESOR || f[2] != null);
		}
		List<SolicitudDTOs.Solicitud> dtos = new ArrayList<>(lista.size());
		for (Solicitud s : lista) {
			String[] alumno = alumnos.getOrDefault(s.getAlumnoId(), new String[2]);
			dtos.add(new SolicitudDTOs.Solicitud(s.getId(), s.getTipo(), s.getEstado(), s.getSedeId(),
					sedes.get(s.getSedeId()), s.getSolicitadoPorUsuarioId(), nombresCuentas.get(s.getSolicitadoPorUsuarioId()),
					s.getSolicitadoEn(), s.getMotivo(), s.getResueltoPorUsuarioId(),
					nombresCuentas.get(s.getResueltoPorUsuarioId()), s.getResueltoEn(), s.getRespuesta(), s.getCobroId(),
					s.getEntregaCajaId(), s.getMovimientoCajaId(), s.getProductoAlumnoId(), s.getProductoId(),
					s.getAlumnoId(), alumno[0], alumno[1], s.getConcepto(),
					s.getImporte() == null ? null : dosDecimales(s.getImporte()), s.getMotivoDeshacer(),
					s.getEncargadoEn(), s.getRecibidoEn(), s.getEntregadoEn(), s.isDevolucionPendiente(),
					usuario != null && usuario.equals(s.getSolicitadoPorUsuarioId()), s.getMovimientoDevolucionId(),
					s.isDevolucionPendiente() ? enSede.get(s.getProductoAlumnoId()) : null));
		}
		return dtos;
	}

	private List<Object[]> filas(String jpql, Collection<Long> ids) {
		List<Long> distintos = ids.stream().filter(Objects::nonNull).distinct().toList();
		if (distintos.isEmpty()) {
			return List.of();
		}
		return em.createQuery(jpql, Object[].class).setParameter("ids", distintos).getResultList();
	}

	private <T> T enTransaccion(Supplier<T> accion) {
		return transaccion.execute(estado -> accion.get());
	}

	/** Alguna causa es una entrada duplicada en una clave única (MySQL 1062, o el tipo de Spring). */
	private static boolean esClaveUnica(Throwable error) {
		for (Throwable causa = error; causa != null; causa = causa.getCause()) {
			if (causa instanceof org.springframework.dao.DuplicateKeyException
					|| causa instanceof java.sql.SQLException sql && sql.getErrorCode() == MYSQL_CLAVE_DUPLICADA) {
				return true;
			}
			if (causa.getCause() == causa) {
				break;
			}
		}
		return false;
	}

	private static boolean esClavePendiente(Throwable error) {
		for (Throwable causa = error; causa != null; causa = causa.getCause()) {
			String mensaje = causa.getMessage();
			if (mensaje != null && CLAVES_PENDIENTE.stream().anyMatch(mensaje::contains)) {
				return true;
			}
			if (causa.getCause() == causa) {
				break;
			}
		}
		return false;
	}

	private static boolean tieneAutoridad(Roles rol) {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion == null) {
			return false;
		}
		for (GrantedAuthority autoridad : autenticacion.getAuthorities()) {
			if (rol.name().equals(autoridad.getAuthority())) {
				return true;
			}
		}
		return false;
	}

	private static Long usuarioDeLaSesion() {
		Long usuario = CobroService.usuarioActualId();
		if (usuario == null) {
			throw new AccessDeniedException("Acceso denegado.");
		}
		return usuario;
	}

	/** Texto recortado; {@code null} si está vacío; más de 500 caracteres → 400. */
	private static String texto(String valor, String campo) {
		if (valor == null || valor.isBlank()) {
			return null;
		}
		String limpio = valor.trim();
		if (limpio.length() > MAX_TEXTO) {
			throw new IllegalArgumentException(campo + " no puede superar " + MAX_TEXTO + " caracteres.");
		}
		return limpio;
	}

	private static BigDecimal dosDecimales(BigDecimal valor) {
		return (valor == null ? BigDecimal.ZERO : valor).setScale(2, RoundingMode.HALF_UP);
	}
}
