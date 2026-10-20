package com.taemoi.project.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.taemoi.project.acceso.GestionPorSede;
import com.taemoi.project.dtos.request.SolicitudRequests;
import com.taemoi.project.dtos.response.CajaDTOs;
import com.taemoi.project.dtos.response.SolicitudDTOs;
import com.taemoi.project.entities.EstadoSolicitud;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.entities.Solicitud;
import com.taemoi.project.entities.TipoSolicitud;
import com.taemoi.project.organizacion.AlcanceActual;
import com.taemoi.project.organizacion.AlcanceSesion;
import com.taemoi.project.repositories.SolicitudRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Encargos del profesor (pieza 6c-5; fase3-6c-solicitudes.md decisiones 183, 192 y 195, D6c-9, §6.2 y §6.3;
 * fase3-6c-plan.md §4b). Son solicitudes de tipo {@link TipoSolicitud#ENCARGO} en la tabla de la 6c-2.
 *
 * <ul>
 * <li><b>Pedir</b> (el profesor, para un alumno de sus grupos): en una transacción, el cargo PENDIENTE del alumno con
 * el precio del catálogo ({@link ProductoAlumnoService#crearCargoDeEncargo}, sale en «Por cobrar» de Mi caja) y la
 * solicitud en PEDIDO con su sede (D6c-9). La caja no cambia.</li>
 * <li><b>Encargado, recibido y rechazar</b>: quien atiende (propietario, gestor y, por API, el responsable de la sede
 * de la solicitud). <b>Retirar</b> (en PEDIDO) y <b>entregado</b> (en RECIBIDO): solo el profesor que lo pidió.</li>
 * <li><b>Cae el encargo</b> (retirado o rechazado): con el cargo pendiente, se cancela
 * ({@link ProductoAlumnoService#caerCargoDeEncargo}); con el cargo pagado, no se toca y queda
 * {@code devolucion_pendiente}.</li>
 * <li><b>Registrar la devolución</b> (pieza 6c-6, 195, D6c-10) de un encargo caído y pagado: quien tiene el dinero.
 * La sede ({@link #registrarDevolucionEncargo}) si ya está en su caja; el profesor que lo pidió
 * ({@link #registrarDevolucionProfesor}) si aún lo lleva encima. Deja {@code devolucion_pendiente = false} y
 * {@code movimiento_devolucion_id}.</li>
 * </ul>
 *
 * <p>Orden de bloqueos (el de la 6c-3): cada transición bloquea y relee la fila de la solicitud
 * ({@link SolicitudService#bloquear}) y exige la transición ({@link SolicitudTransiciones#exigir}: de dos a la vez, una
 * gana y la otra recibe 409 {@value SolicitudTransiciones#NO_PENDIENTE}); al caer, después, el cargo. Cobrar, cancelar o
 * borrar el cargo bloquean el cargo y solo LEEN la solicitud, nunca la bloquean después. Excepción ya documentada:
 * borrar un alumno o una convocatoria pasa por las FK ON DELETE SET NULL de {@code solicitud} después del cargo; su
 * cruce con rechazar o retirar puede acabar en un interbloqueo que InnoDB resuelve (409, se deshace entero).
 *
 * <p>Toda escritura queda en el registro de acciones ({@link RegistroAccionesSolicitudes}) en su misma transacción,
 * solo con ids (ni la nota ni el motivo).
 */
@Service
public class EncargoService {

	/** 409 al cancelar o borrar el cargo de un encargo PEDIDO, ENCARGADO o RECIBIDO (§6.3). */
	public static final String ENCARGO_EN_CURSO = "Tiene un encargo en curso: recházalo desde Solicitudes";
	public static final String NO_ES_ENCARGO = "No es un encargo.";
	static final String SEDE_OBLIGATORIA = "El alumno está en grupos suyos de varias sedes: indica la sede del encargo.";
	static final String SEDE_NO_VALIDA = "La sede no es la de un grupo suyo en el que esté el alumno.";
	static final String NO_SE_RETIRA_AQUI = "Esta solicitud no se retira desde Mi caja.";
	/** Pieza 6c-6: 409 al registrar la devolución de un encargo que no la tiene pendiente (o ya registrada). */
	public static final String SIN_DEVOLUCION_PENDIENTE = "El encargo no tiene una devolución pendiente.";
	/** Pieza 6c-6 (D6c-10): 409 si el profesor ya no lleva encima el dinero del encargo (o nunca lo cobró él). */
	public static final String NO_LLEVAS_EL_DINERO = "No llevas encima el dinero de este encargo: la devolución la "
			+ "registra la sede.";
	static final String SIN_COBRO_QUE_DEVOLVER = "El cargo del encargo no tiene un cobro activo que devolver.";
	/** Las cerradas que devuelve «Mis solicitudes», las más recientes (las abiertas, todas). */
	static final int MAX_CERRADAS = 200;

	private static final int MAX_TEXTO = 500;
	/**
	 * La nota va a {@code solicitud.motivo} (VARCHAR(500)) y a {@code producto_alumno.notas} (VARCHAR(255)): el menor
	 * de los dos.
	 */
	static final int MAX_NOTA = 255;
	static final String NOTA_DEMASIADO_LARGA = "La nota admite como máximo " + MAX_NOTA + " caracteres.";
	private static final Set<EstadoSolicitud> ABIERTOS = Set.of(EstadoSolicitud.PENDIENTE, EstadoSolicitud.PEDIDO,
			EstadoSolicitud.ENCARGADO, EstadoSolicitud.RECIBIDO);

	private final SolicitudRepository solicitudes;
	private final SolicitudService solicitudService;
	private final ProductoAlumnoService productosAlumno;
	private final RegistroAccionesSolicitudes registro;
	private final GestionPorSede gestionPorSede;
	private final AlcanceActual alcanceActual;
	private final CajaService caja;
	private final CobroService cobros;
	private final TransactionTemplate transaccion;

	@PersistenceContext
	private EntityManager em;

	public EncargoService(SolicitudRepository solicitudes, SolicitudService solicitudService,
			ProductoAlumnoService productosAlumno, RegistroAccionesSolicitudes registro, GestionPorSede gestionPorSede,
			AlcanceActual alcanceActual, CajaService caja, CobroService cobros,
			PlatformTransactionManager transacciones) {
		this.solicitudes = solicitudes;
		this.solicitudService = solicitudService;
		this.productosAlumno = productosAlumno;
		this.registro = registro;
		this.gestionPorSede = gestionPorSede;
		this.alcanceActual = alcanceActual;
		this.caja = caja;
		this.cobros = cobros;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
	}

	// ------------------------------------------------------------------ profesor

	/** Decisión 192: lo que el profesor puede encargar (ver {@link ProductoAlumnoService#productosEncargables}). */
	public List<CajaDTOs.ProductoVendible> productosEncargables() {
		return productosAlumno.productosEncargables().stream()
				.map(p -> new CajaDTOs.ProductoVendible(p.getId(), p.getConcepto(), importe(p.getPrecio()))).toList();
	}

	/**
	 * El profesor de la sesión encarga {@code productoId} para {@code alumnoId} (de sus grupos: lo exige
	 * {@code @acceso.alumnoDeMisGrupos}). Sin producto o sin nota (o de más de {@value #MAX_NOTA}), 400; producto o alumno que no existe
	 * en la escuela, 404; producto no encargable o sede no válida (D6c-9), 400. Si el recurso cambia a la vez (p. ej.
	 * se borra el alumno), 409. Nada queda escrito si falla.
	 */
	public SolicitudDTOs.Solicitud pedirEncargo(Long alumnoId, SolicitudRequests.PedirEncargo peticion) {
		if (peticion == null || peticion.productoId() == null) {
			throw new IllegalArgumentException("Indica el producto.");
		}
		String nota = peticion.nota() == null || peticion.nota().isBlank() ? null : peticion.nota().trim();
		if (nota == null) {
			throw new IllegalArgumentException("La nota es obligatoria (la talla, el color…).");
		}
		if (nota.length() > MAX_NOTA) {
			throw new IllegalArgumentException(NOTA_DEMASIADO_LARGA);
		}
		Long usuario = usuarioDeLaSesion();
		Set<Long> grupos = gruposDelProfesor();
		try {
			return enTransaccion(() -> {
				ProductoAlumno cargo = productosAlumno.crearCargoDeEncargo(alumnoId, peticion.productoId(), nota);
				Long sede = sedeDelEncargo(alumnoId, grupos, peticion.sedeId());
				Solicitud solicitud = new Solicitud();
				solicitud.setTipo(TipoSolicitud.ENCARGO);
				solicitud.setEstado(SolicitudTransiciones.estadoInicial(TipoSolicitud.ENCARGO));
				solicitud.setSolicitadoPorUsuarioId(usuario);
				solicitud.setSolicitadoEn(ahora());
				solicitud.setMotivo(nota);
				solicitud.setSedeId(sede);
				solicitud.setProductoAlumnoId(cargo.getId());
				solicitud.setProductoId(peticion.productoId());
				solicitud.setAlumnoId(alumnoId);
				solicitud.setConcepto(cargo.getConcepto());
				solicitud.setImporte(CobroService.importeDe(cargo));
				Solicitud guardada = solicitudes.saveAndFlush(solicitud);
				registro.registrar(RegistroAccionesSolicitudes.Accion.ENCARGO_PEDIDO,
						"/api/caja/profesor/alumnos/" + alumnoId + "/encargos", guardada);
				em.flush();
				return dto(guardada, usuario);
			});
		} catch (DataIntegrityViolationException error) {
			throw new IllegalStateException(SolicitudService.RECURSO_CAMBIADO, error);
		}
	}

	/**
	 * D6c-9: las sedes distintas (no nulas) de los grupos del profesor en los que está el alumno. Ninguna → sin sede;
	 * una → esa (si llega otra, 400); varias → la que llega, que debe ser una de ellas (400 si falta o no lo es).
	 */
	private Long sedeDelEncargo(Long alumnoId, Set<Long> grupos, Long pedida) {
		List<Long> sedes = grupos.isEmpty() ? List.of()
				: em.createQuery("""
						SELECT DISTINCT g.sedeId FROM Grupo g JOIN g.alumnos a
						WHERE a.id = :alumno AND g.id IN :grupos AND g.sedeId IS NOT NULL""", Long.class)
						.setParameter("alumno", alumnoId).setParameter("grupos", grupos).getResultList();
		if (sedes.size() > 1 && pedida == null) {
			throw new IllegalArgumentException(SEDE_OBLIGATORIA);
		}
		if (pedida != null) {
			if (!sedes.contains(pedida)) {
				throw new IllegalArgumentException(SEDE_NO_VALIDA);
			}
			return pedida;
		}
		return sedes.isEmpty() ? null : sedes.get(0);
	}

	/**
	 * Solo quien la pidió (403). Un encargo, en PEDIDO (409 si no): RETIRADA y cae el encargo. Su tipo 2 pendiente
	 * (6c-4): RETIRADA, como {@link SolicitudService#retirar}. Otro tipo, 400.
	 */
	public SolicitudDTOs.Solicitud retirarEncargo(Long id) {
		Long usuario = usuarioDeLaSesion();
		String endpoint = "/api/caja/profesor/solicitudes/" + id + "/retirar";
		return enTransaccion(() -> {
			Solicitud solicitud = solicitudService.bloquear(id);
			exigirSolicitante(solicitud, usuario);
			if (solicitud.getTipo() != TipoSolicitud.ENCARGO
					&& solicitud.getTipo() != TipoSolicitud.DESHACER_COBRO_ENTREGADO) {
				throw new IllegalArgumentException(NO_SE_RETIRA_AQUI);
			}
			SolicitudTransiciones.exigir(solicitud, EstadoSolicitud.RETIRADA);
			resolver(solicitud, EstadoSolicitud.RETIRADA, usuario, null);
			if (solicitud.getTipo() == TipoSolicitud.ENCARGO) {
				caer(solicitud, usuario, "Encargo retirado (solicitud #" + id + ")", endpoint);
			}
			registro.registrar(RegistroAccionesSolicitudes.Accion.SOLICITUD_RETIRADA, endpoint, solicitud);
			em.flush();
			return dto(solicitud, usuario);
		});
	}

	/** Solo quien lo pidió (403), en RECIBIDO (409 si no). No exige el cargo cobrado: se cobra desde «Por cobrar». */
	public SolicitudDTOs.Solicitud marcarEntregado(Long id) {
		Long usuario = usuarioDeLaSesion();
		return enTransaccion(() -> {
			Solicitud solicitud = solicitudService.bloquear(id);
			exigirSolicitante(solicitud, usuario);
			exigirEncargo(solicitud);
			SolicitudTransiciones.exigir(solicitud, EstadoSolicitud.ENTREGADO);
			solicitud.setEstado(EstadoSolicitud.ENTREGADO);
			solicitud.setEntregadoEn(ahora());
			solicitudes.save(solicitud);
			registro.registrar(RegistroAccionesSolicitudes.Accion.ENCARGO_ENTREGADO,
					"/api/caja/profesor/solicitudes/" + id + "/entregado", solicitud);
			em.flush();
			return dto(solicitud, usuario);
		});
	}

	/**
	 * «Mis solicitudes» del profesor de la sesión (las que pidió él: encargos y tipo 2): las abiertas (pendientes,
	 * encargos en curso y con devolución pendiente), de la más nueva a la más antigua, y después las
	 * {@value #MAX_CERRADAS} cerradas más recientes.
	 */
	public List<SolicitudDTOs.Solicitud> misSolicitudes() {
		Long usuario = usuarioDeLaSesion();
		List<Solicitud> lista = new ArrayList<>(em.createQuery("""
				SELECT s FROM Solicitud s WHERE s.solicitadoPorUsuarioId = :usuario
				  AND (s.estado IN :abiertos OR s.devolucionPendiente = TRUE)
				ORDER BY s.solicitadoEn DESC, s.id DESC""", Solicitud.class)
				.setParameter("usuario", usuario).setParameter("abiertos", ABIERTOS).getResultList());
		lista.addAll(em.createQuery("""
				SELECT s FROM Solicitud s WHERE s.solicitadoPorUsuarioId = :usuario
				  AND s.estado NOT IN :abiertos AND s.devolucionPendiente = FALSE
				ORDER BY COALESCE(s.resueltoEn, s.entregadoEn, s.solicitadoEn) DESC, s.id DESC""", Solicitud.class)
				.setParameter("usuario", usuario).setParameter("abiertos", ABIERTOS).setMaxResults(MAX_CERRADAS)
				.getResultList());
		return solicitudService.dtos(lista, usuario);
	}

	// ------------------------------------------------------------------ quien atiende

	/** PEDIDO → ENCARGADO (quién y cuándo). Ver {@link #atender}. */
	public SolicitudDTOs.Solicitud marcarEncargado(Long id) {
		return atender(id, EstadoSolicitud.ENCARGADO, RegistroAccionesSolicitudes.Accion.ENCARGO_ENCARGADO,
				"encargado", null);
	}

	/** ENCARGADO → RECIBIDO (quién y cuándo). Ver {@link #atender}. */
	public SolicitudDTOs.Solicitud marcarRecibido(Long id) {
		return atender(id, EstadoSolicitud.RECIBIDO, RegistroAccionesSolicitudes.Accion.ENCARGO_RECIBIDO, "recibido",
				null);
	}

	/**
	 * PEDIDO, ENCARGADO o RECIBIDO → RECHAZADO con motivo (obligatorio, hasta 500: 400), que ve el profesor en la
	 * respuesta. Cae el encargo. Ver {@link #atender}.
	 */
	public SolicitudDTOs.Solicitud rechazar(Long id, String motivo) {
		String texto = texto(motivo, "El motivo");
		if (texto == null) {
			throw new IllegalArgumentException("El motivo es obligatorio.");
		}
		return atender(id, EstadoSolicitud.RECHAZADO, RegistroAccionesSolicitudes.Accion.ENCARGO_RECHAZADO,
				"rechazar", texto);
	}

	/**
	 * Quien atiende: propietario, gestor o responsable de la sede de la solicitud ({@code @acceso.solicitudEnSede} en
	 * la ruta y aquí otra vez, 403). No existe en la escuela, 404; no es un encargo, 400; fuera de la tabla de §6.2,
	 * 409.
	 */
	private SolicitudDTOs.Solicitud atender(Long id, EstadoSolicitud destino, RegistroAccionesSolicitudes.Accion accion,
			String ruta, String motivo) {
		Long usuario = usuarioDeLaSesion();
		String endpoint = "/api/solicitudes/" + id + "/" + ruta;
		return enTransaccion(() -> {
			Solicitud solicitud = solicitudService.bloquear(id);
			exigirEncargo(solicitud);
			gestionPorSede.exigirSedePropia(solicitud.getSedeId());
			SolicitudTransiciones.exigir(solicitud, destino);
			LocalDateTime ahora = ahora();
			switch (destino) {
			case ENCARGADO -> {
				solicitud.setEstado(destino);
				solicitud.setEncargadoEn(ahora);
				solicitud.setEncargadoPorUsuarioId(usuario);
				solicitudes.save(solicitud);
			}
			case RECIBIDO -> {
				solicitud.setEstado(destino);
				solicitud.setRecibidoEn(ahora);
				solicitud.setRecibidoPorUsuarioId(usuario);
				solicitudes.save(solicitud);
			}
			case RECHAZADO -> {
				resolver(solicitud, destino, usuario, motivo);
				caer(solicitud, usuario, "Encargo rechazado (solicitud #" + id + ")", endpoint);
			}
			default -> throw new IllegalStateException(SolicitudTransiciones.NO_PENDIENTE);
			}
			registro.registrar(accion, endpoint, solicitud);
			em.flush();
			return dto(solicitud, usuario);
		});
	}

	// ------------------------------------------------------------------ devolución (6c-6)

	/**
	 * Pieza 6c-6 (195): la sede registra la devolución de un encargo caído con el dinero ya en su caja (cobro de origen
	 * distinto de PROFESOR, o entregado). Propietario y gestor; el responsable, si la solicitud
	 * ({@code @acceso.solicitudEnSede}) y el COBRO son de sus sedes (nota F2: la devolución sale en la caja del cobro;
	 * 403). Bloqueos: solicitud → cargo → cobro. No existe en la escuela, 404; no es un encargo, 400; sin devolución
	 * pendiente, 409; el profesor aún lleva el dinero, 409 ({@link CajaService#DINERO_LO_LLEVA_EL_PROFESOR}).
	 */
	public SolicitudDTOs.Solicitud registrarDevolucionEncargo(Long solicitudId, String nota) {
		String limpia = texto(nota, "La nota");
		Long usuario = usuarioDeLaSesion();
		String endpoint = "/api/solicitudes/" + solicitudId + "/devolucion";
		return CajaService.conClaveDeDevolucion(() -> enTransaccion(() -> {
			Solicitud solicitud = bloquearConDevolucionPendiente(solicitudId, null);
			com.taemoi.project.entities.Cobro cobro = bloquearCobroDelEncargo(solicitud);
			gestionPorSede.exigirSedePropia(cobro.getSedeId());
			com.taemoi.project.entities.MovimientoCaja devolucion = caja.devolverEnSede(cobro, usuario,
					notaDeDevolucion(limpia, solicitudId), solicitudId, endpoint);
			return cerrarDevolucion(solicitud, devolucion, usuario, endpoint);
		}));
	}

	/**
	 * Pieza 6c-6 (195, D6c-10): el profesor que pidió el encargo caído registra su devolución si aún lleva el dinero
	 * encima: el cobro es suyo (origen PROFESOR, cobrado por él), ACTIVO, EFECTIVO y sin entregar (si no, 409
	 * {@link #NO_LLEVAS_EL_DINERO}). Ver {@link CajaService#devolverDelProfesor}: saldo de la sede igual y el cobro fuera
	 * de «Llevas encima». Otro profesor, 403; no existe en la escuela, 404; no es un encargo, 400; sin devolución
	 * pendiente, 409. Bloqueos: solicitud → cargo → cobro (registrar una entrega bloquea solo cobros).
	 */
	public SolicitudDTOs.Solicitud registrarDevolucionProfesor(Long solicitudId, String nota) {
		String limpia = texto(nota, "La nota");
		Long usuario = usuarioDeLaSesion();
		String endpoint = "/api/caja/profesor/solicitudes/" + solicitudId + "/devolucion";
		return CajaService.conClaveDeDevolucion(() -> enTransaccion(() -> {
			Solicitud solicitud = bloquearConDevolucionPendiente(solicitudId, usuario);
			com.taemoi.project.entities.Cobro cobro = bloquearCobroDelEncargo(solicitud);
			if (cobro.getOrigen() != com.taemoi.project.entities.OrigenCobro.PROFESOR
					|| !usuario.equals(cobro.getCobradoPorUsuarioId()) || cobro.getEntregaCajaId() != null
					|| cobro.getMetodo() != com.taemoi.project.entities.MetodoPago.EFECTIVO) {
				throw new IllegalStateException(NO_LLEVAS_EL_DINERO);
			}
			com.taemoi.project.entities.MovimientoCaja devolucion = caja.devolverDelProfesor(cobro, usuario,
					notaDeDevolucion(limpia, solicitudId), solicitudId, endpoint);
			return cerrarDevolucion(solicitud, devolucion, usuario, endpoint);
		}));
	}

	/**
	 * Bloquea el encargo y exige que tenga la devolución pendiente (404, 400 si no es un encargo, 409). Con
	 * {@code solicitante}, solo quien lo pidió (403, antes de mirar el estado).
	 */
	private Solicitud bloquearConDevolucionPendiente(Long id, Long solicitante) {
		Solicitud solicitud = solicitudService.bloquear(id);
		if (solicitante != null) {
			exigirSolicitante(solicitud, solicitante);
		}
		exigirEncargo(solicitud);
		if (!solicitud.isDevolucionPendiente()) {
			throw new IllegalStateException(SIN_DEVOLUCION_PENDIENTE);
		}
		return solicitud;
	}

	/** Con la solicitud bloqueada: bloquea el cargo y su cobro ACTIVO (orden cargo → cobro). Sin ellos, 409. */
	private com.taemoi.project.entities.Cobro bloquearCobroDelEncargo(Solicitud solicitud) {
		ProductoAlumno cargo = cobros.bloquearCargo(solicitud.getProductoAlumnoId())
				.orElseThrow(() -> new IllegalStateException(SIN_COBRO_QUE_DEVOLVER));
		return cobros.bloquearActivoDeCargo(cargo.getId())
				.orElseThrow(() -> new IllegalStateException(SIN_COBRO_QUE_DEVOLVER));
	}

	/** Cierra la devolución pendiente con la devolución apuntada y lo registra (solo ids, nunca la nota). */
	private SolicitudDTOs.Solicitud cerrarDevolucion(Solicitud solicitud,
			com.taemoi.project.entities.MovimientoCaja devolucion, Long usuario, String endpoint) {
		solicitud.setDevolucionPendiente(false);
		solicitud.setMovimientoDevolucionId(devolucion.getId());
		solicitudes.save(solicitud);
		registro.registrar(RegistroAccionesSolicitudes.Accion.ENCARGO_DEVOLUCION_REGISTRADA, endpoint, solicitud,
				java.util.Map.of("devolucion", devolucion.getId()));
		em.flush();
		return dto(solicitud, usuario);
	}

	private static String notaDeDevolucion(String nota, Long solicitudId) {
		return nota != null ? nota : "Devolución del encargo (solicitud #" + solicitudId + ")";
	}

	// ------------------------------------------------------------------ utilidades

	/**
	 * Cae el encargo (§6.3), con la solicitud bloqueada y ya cerrada: el cargo pendiente se cancela; el pagado deja la
	 * devolución pendiente (195). Sin cargo (se borró con el alumno), nada más. Pieza 6c-6: si el cobro del cargo (ya
	 * bloqueado) ya tiene una DEVOLUCION ACTIVA, no queda nada pendiente: el encargo enlaza esa devolución.
	 */
	private void caer(Solicitud solicitud, Long usuario, String motivo, String endpoint) {
		if (productosAlumno.caerCargoDeEncargo(solicitud.getProductoAlumnoId(), usuario, motivo, solicitud.getId(),
				endpoint)) {
			java.util.Optional<com.taemoi.project.entities.MovimientoCaja> devuelto = cobros
					.bloquearDevolucionDeCargo(solicitud.getProductoAlumnoId());
			if (devuelto.isPresent()) {
				solicitud.setMovimientoDevolucionId(devuelto.get().getId());
			} else {
				solicitud.setDevolucionPendiente(true);
			}
			solicitudes.save(solicitud);
		}
	}

	private void resolver(Solicitud solicitud, EstadoSolicitud estado, Long usuario, String respuesta) {
		solicitud.setEstado(estado);
		solicitud.setResueltoPorUsuarioId(usuario);
		solicitud.setResueltoEn(ahora());
		solicitud.setRespuesta(respuesta);
		solicitudes.save(solicitud);
	}

	private static void exigirEncargo(Solicitud solicitud) {
		if (solicitud.getTipo() != TipoSolicitud.ENCARGO) {
			throw new IllegalArgumentException(NO_ES_ENCARGO);
		}
	}

	private static void exigirSolicitante(Solicitud solicitud, Long usuario) {
		if (!usuario.equals(solicitud.getSolicitadoPorUsuarioId())) {
			throw new AccessDeniedException("Acceso denegado.");
		}
	}

	private SolicitudDTOs.Solicitud dto(Solicitud solicitud, Long usuario) {
		return solicitudService.dtos(List.of(solicitud), usuario).get(0);
	}

	private Set<Long> gruposDelProfesor() {
		AlcanceSesion alcance = alcanceActual.obtener();
		return alcance.total() ? Set.of() : alcance.grupos();
	}

	private <T> T enTransaccion(Supplier<T> accion) {
		return transaccion.execute(estado -> accion.get());
	}

	private static LocalDateTime ahora() {
		return LocalDateTime.now(CajaService.ZONA);
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

	private static BigDecimal importe(Double precio) {
		return BigDecimal.valueOf(precio == null ? 0.0 : precio).setScale(2, RoundingMode.HALF_UP);
	}
}
