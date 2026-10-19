package com.taemoi.project.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Cobro;
import com.taemoi.project.entities.EntregaCaja;
import com.taemoi.project.entities.EstadoCobro;
import com.taemoi.project.entities.EstadoMovimientoCaja;
import com.taemoi.project.entities.MetodoPago;
import com.taemoi.project.entities.MovimientoCaja;
import com.taemoi.project.entities.OrigenCobro;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.entities.TipoMovimientoCaja;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.exceptions.pago.CobroEntregadoException;
import com.taemoi.project.repositories.CobroRepository;
import com.taemoi.project.repositories.EntregaCajaRepository;
import com.taemoi.project.repositories.MovimientoCajaRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Registro de cobros (pieza 6b-1). Única vía que crea y anula filas de {@code cobro}; la usan las dos puertas que
 * cambian el pago de un cargo (PUT del cargo y PUT de la inscripción a una convocatoria), los cargos que nacen pagados y
 * el borrado de un cargo. {@code producto_alumno.pagado} y {@code fecha_pago} se siguen escribiendo como siempre (las
 * escriben esas puertas): son el resumen que lee la tesorería.
 *
 * <p>Reglas: un cargo que pasa de no pagado a pagado deja UN cobro ACTIVO (quién cobra: el usuario autenticado, o NULL);
 * si ya estaba pagado no se crea otro; al pasar a no pagado su cobro ACTIVO pasa a ANULADO. Un cobro que va en una entrega
 * de caja no se anula ({@link CobroEntregadoException}, 409, sin cambiar nada).
 *
 * <p>Concurrencia: quien cambia el pago lo hace dentro de {@link #enTransaccion} (READ_COMMITTED, como R2-P3) y empieza
 * por {@link #bloquearCargo}: SELECT ... FOR UPDATE de la fila del cargo y relectura de su estado, así que dos cobros
 * simultáneos del mismo cargo se ordenan y el segundo lo ve pagado. La clave única {@code uk_cobro_activo_por_cargo} es la
 * red de seguridad: si salta, la transacción se repite una vez (y entonces ve el cobro) en lugar de dar un 500. La
 * transacción solo bloquea el cargo y escribe el cargo y el cobro: la promoción de grado y las sincronizaciones con la
 * convocatoria y el alumno se quedan fuera, como antes, así que no se cruza con sus bloqueos.
 */
@Service
public class CobroService {

	private static final Logger LOG = LoggerFactory.getLogger(CobroService.class);

	/** Nombre de la clave única de V31_5 («como mucho un cobro ACTIVO por cargo»). */
	public static final String CLAVE_COBRO_ACTIVO = "uk_cobro_activo_por_cargo";
	public static final String MOTIVO_CARGO_BORRADO = "cargo borrado";
	/** Pieza 6b-2c: mensaje del 409 al intentar cobrar un cargo cancelado. */
	public static final String CARGO_CANCELADO = "El cargo está cancelado: reactívelo antes de cobrarlo.";
	/**
	 * Pieza 6c-1, decisión 186: 409 al quitar de una convocatoria a un alumno con el examen pagado. Texto literal del
	 * diseño; desde la 6c-6, el completo con la devolución (D6c-15: un examen devuelto se desvincula).
	 */
	public static final String EXAMEN_PAGADO_REVIERTE_ANTES = "El examen está pagado: haz la devolución o revierte el "
			+ "cobro antes";
	/** Pieza 6c-1, decisión 187: 409 al cambiar el precio o la cantidad de un cargo con cobro ACTIVO. */
	public static final String PRECIO_CON_COBRO_ACTIVO = "El cargo está cobrado: no se puede cambiar el precio ni la "
			+ "cantidad. Revierte el cobro antes.";
	/** Pieza 6c-1, decisión 194: 409 al cambiar desde la convocatoria la cuantía de un examen pagado. */
	public static final String CUANTIA_EXAMEN_PAGADO = "El examen está pagado: no se puede cambiar la cuantía. "
			+ "Revierte el cobro antes.";

	private final CobroRepository cobros;
	private final ProductoAlumnoRepository cargos;
	private final EntregaCajaRepository entregas;
	private final MovimientoCajaRepository movimientos;
	private final TransactionTemplate transaccion;

	@PersistenceContext
	private EntityManager em;

	public CobroService(CobroRepository cobros, ProductoAlumnoRepository cargos, EntregaCajaRepository entregas,
			MovimientoCajaRepository movimientos, PlatformTransactionManager transacciones) {
		this.cobros = cobros;
		this.cargos = cargos;
		this.entregas = entregas;
		this.movimientos = movimientos;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
	}

	// ------------------------------------------------------------------ transacción y bloqueo

	/**
	 * Ejecuta la acción en una transacción READ_COMMITTED (o en la que ya haya). Si la clave única de cobro activo salta,
	 * la repite una vez: la acción debe volver a leer lo que necesite (empezar por {@link #bloquearCargo}). Si vuelve a
	 * saltar, 409. Dentro de una transacción ajena no se puede repetir: el error sube tal cual.
	 *
	 * <p>Con open-session-in-view, el rollback del primer intento LIMPIA el EntityManager de la petición
	 * ({@code JpaTransactionManager} hace {@code clear()} en un EntityManager previo a la transacción): las entidades
	 * que el llamador tuviera cargadas quedan desvinculadas. Camino casi inalcanzable, porque el FOR UPDATE de
	 * {@link #bloquearCargo} va antes de comprobar y registrar el cobro.
	 */
	public <T> T enTransaccion(Supplier<T> accion) {
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			return accion.get();
		}
		try {
			return transaccion.execute(estado -> accion.get());
		} catch (DataIntegrityViolationException error) {
			if (!esCobroActivoDuplicado(error)) {
				throw error;
			}
			LOG.warn("Cobro activo duplicado al registrar un cobro; se repite la operación una vez");
			try {
				return transaccion.execute(estado -> accion.get());
			} catch (DataIntegrityViolationException otra) {
				if (esCobroActivoDuplicado(otra)) {
					throw new IllegalStateException(
							"Se está registrando otro cobro de este cargo a la vez. Vuelva a intentarlo.", otra);
				}
				throw otra;
			}
		}
	}

	/**
	 * Dentro de {@link #enTransaccion}: bloquea la fila del cargo (FOR UPDATE, solo esa fila) y devuelve el cargo con el
	 * estado que tiene ahora en la base (lo relee aunque la sesión ya lo tuviera cargado). Vacío si no existe en la
	 * escuela activa.
	 */
	public Optional<ProductoAlumno> bloquearCargo(Long cargoId) {
		if (cargoId == null || cargos.bloquearPorId(cargoId).isEmpty()) {
			return Optional.empty();
		}
		ProductoAlumno cargo = em.find(ProductoAlumno.class, cargoId);
		if (cargo == null) {
			return Optional.empty();
		}
		em.refresh(cargo);
		return Optional.of(cargo);
	}

	/**
	 * Pieza 6b-2, dentro de una transacción: bloquea la fila del cobro (FOR UPDATE) y la devuelve con el estado que
	 * tiene ahora en la base. Vacío si no existe en la escuela activa.
	 */
	public Optional<Cobro> bloquearCobro(Long cobroId) {
		if (cobroId == null) {
			return Optional.empty();
		}
		return cobros.bloquearPorId(cobroId).map(this::releer);
	}

	/**
	 * Pieza 6c-3, dentro de una transacción: bloquea la entrega de caja (FOR UPDATE) y la devuelve releída. Vacío si no
	 * existe en la escuela activa. Orden: cargo → ENTREGA → cobro (la anulación de una entrega bloquea entrega → cobros;
	 * registrar una entrega, solo cobros), así que no hay ciclo.
	 */
	public Optional<EntregaCaja> bloquearEntrega(Long entregaId) {
		if (entregaId == null) {
			return Optional.empty();
		}
		return entregas.bloquearPorId(entregaId).map(entrega -> {
			if (em != null && em.contains(entrega)) {
				em.refresh(entrega);
			}
			return entrega;
		});
	}

	/**
	 * Relee el cobro de la base (ya bloqueado): una consulta con bloqueo devuelve la instancia que la sesión ya tuviera
	 * cargada SIN actualizar su estado (open-session-in-view).
	 */
	private Cobro releer(Cobro cobro) {
		if (em != null && em.contains(cobro)) {
			em.refresh(cobro);
		}
		return cobro;
	}

	// ------------------------------------------------------------------ operaciones

	/**
	 * Registra el cobro ACTIVO del cargo (ya guardado y pagado). Si ya tiene uno, lo devuelve sin crear otro. Importe: lo
	 * que la tesorería suma para el cargo ({@code COALESCE(precio, 0)}); sede, alumno y concepto, copiados del cargo.
	 */
	public Cobro registrar(ProductoAlumno cargo, MetodoPago metodo, Date fechaCobro, OrigenCobro origen) {
		// Pieza 6b-2c, guarda CENTRAL del invariante «cancelado ⇒ no pagado y sin cobro ACTIVO»: ninguna vía (PUT del
		// cargo, profesor, puerta de la convocatoria, cargo que nace pagado) cobra un cargo cancelado. 409; quien llama
		// está en la transacción del cobro, así que el cambio del cargo se deshace entero.
		if (cargo.estaCancelado()) {
			throw new IllegalStateException(CARGO_CANCELADO);
		}
		Optional<Cobro> activo = cobros.findFirstByProductoAlumnoIdAndEstado(cargo.getId(), EstadoCobro.ACTIVO);
		if (activo.isPresent()) {
			return activo.get();
		}
		Cobro cobro = new Cobro();
		cobro.setProductoAlumnoId(cargo.getId());
		cobro.setSedeId(cargo.getSedeId());
		cobro.setAlumnoId(alumnoDe(cargo));
		cobro.setConcepto(cargo.getConcepto());
		cobro.setImporte(importeDe(cargo));
		cobro.setMetodo(metodo != null ? metodo : MetodoPago.SIN_INDICAR);
		cobro.setFechaCobro(fechaCobro != null ? fechaCobro : new Date());
		cobro.setCobradoPorUsuarioId(usuarioActualId());
		cobro.setOrigen(origen);
		cobro.setEstado(EstadoCobro.ACTIVO);
		return cobros.save(cobro);
	}

	/**
	 * Anula el cobro ACTIVO del cargo, si lo tiene (los cargos pagados antes de la 6b-1 no tienen). Si va en una entrega
	 * de caja, {@link CobroEntregadoException} sin cambiar nada.
	 *
	 * <p>Pieza 6b-2: BLOQUEA la fila del cobro (SELECT ... FOR UPDATE) y la relee ANTES de mirar {@code entrega_caja_id}.
	 * Con READ_COMMITTED, una lectura sin bloqueo no vería una entrega que acaba de tomar el cobro y aún no ha
	 * confirmado: la anulación esperaría después al UPDATE y dejaría un cobro ANULADO dentro de una entrega ACTIVA (o un
	 * error de versión). Con el bloqueo, espera a que la entrega confirme, ve el cobro entregado y responde 409. Vale
	 * para las tres vías que revierten (PUT del cargo, puerta de la convocatoria y deshacer del profesor) y para el
	 * borrado del cargo. Orden de bloqueo: el llamador ya tiene el CARGO bloqueado ({@link #bloquearCargo}) y aquí se
	 * bloquea el COBRO; la entrega de caja solo bloquea cobros, así que no hay ciclo.
	 */
	public Optional<Cobro> anular(ProductoAlumno cargo, Long usuarioId, String motivo) {
		Optional<Cobro> activo = cobros.bloquearActivoDeCargo(cargo.getId()).map(this::releer)
				.filter(cobro -> cobro.getEstado() == EstadoCobro.ACTIVO);
		activo.ifPresent(cobro -> {
			// Pieza 6c-6 (153, §6.3 y §6.4): un cobro con devolución, o con la devolución de su encargo pendiente, no se
			// anula por ninguna vía (A1, A2, A4, borrar el cargo): 409 sin cambiar nada. Con el cobro ya bloqueado: una
			// devolución que se registra a la vez bloquea el mismo cobro antes de escribir.
			razonDeDevolucion(cobro.getId(), cargo.getId()).ifPresent(razon -> {
				throw new IllegalStateException(razon);
			});
			if (cobro.getEntregaCajaId() != null) {
				throw new CobroEntregadoException();
			}
			cobro.setEstado(EstadoCobro.ANULADO);
			cobro.setAnuladoPorUsuarioId(usuarioId);
			cobro.setAnuladoEn(LocalDateTime.now());
			cobro.setMotivoAnulacion(recortar(motivo));
			cobros.save(cobro);
		});
		return activo;
	}

	/**
	 * Tras cambiar el pago de un cargo bloqueado y guardarlo: de no pagado a pagado registra el cobro (fecha: la
	 * {@code fecha_pago} del cargo); de pagado a no pagado anula el suyo (con el usuario autenticado). Sin cambio, nada.
	 */
	public void alCambiarPago(ProductoAlumno cargo, boolean pagadoAntes, MetodoPago metodo, OrigenCobro origen,
			String motivoAnulacion) {
		boolean pagadoAhora = Boolean.TRUE.equals(cargo.getPagado());
		if (!pagadoAntes && pagadoAhora) {
			registrar(cargo, metodo, cargo.getFechaPago(), origen);
		} else if (pagadoAntes && !pagadoAhora) {
			anular(cargo, usuarioActualId(), motivoAnulacion);
		}
	}

	/**
	 * Pieza 6c-1 (decisión 187): si el cargo tiene un cobro ACTIVO (entregado o no). Lectura sin bloqueo: quien la usa ya
	 * tiene el CARGO bloqueado ({@link #bloquearCargo}), y todo cobro de un cargo se registra o anula con esa fila
	 * bloqueada. Null → false. Un cargo pagado antes de la 6b-1 (sin fila {@code cobro}) da false.
	 */
	public boolean tieneCobroActivo(Long cargoId) {
		return cargoId != null && cobros.findFirstByProductoAlumnoIdAndEstado(cargoId, EstadoCobro.ACTIVO).isPresent();
	}

	/**
	 * Pieza 6c-3, decisión 193 (aceptar «deshacer un cobro ya entregado»), dentro de la transacción, con el cargo, la
	 * entrega y el cobro YA bloqueados y revalidados por quien llama (cobro ACTIVO, de esa entrega ACTIVA, sin
	 * devolución): el cobro pasa a ANULADO como en A4 pero sin exigir que no esté entregado, y sale de la entrega
	 * ({@code entrega_caja_id} NULL: invariante de la 6b-2, ningún cobro ANULADO en una entrega ACTIVA); en esa misma
	 * entrega se apunta un ERROR por +importe enlazado al cobro y a su cargo (con el alumno y el método del cobro), que
	 * deja el rastro de la entrega. La entrega no se anula. El saldo de la sede no cambia: deja de sumar el cobro y suma
	 * el ERROR. Si después se anula la entrega, {@code anularEntrega} anula también el ERROR (anula todos sus movimientos
	 * ACTIVOS).
	 *
	 * @return el ERROR apuntado
	 */
	public MovimientoCaja deshacerEntregado(Cobro cobro, EntregaCaja entrega, Long usuarioId, String motivo,
			long solicitudId) {
		cobro.setEntregaCajaId(null);
		cobro.setEstado(EstadoCobro.ANULADO);
		cobro.setAnuladoPorUsuarioId(usuarioId);
		cobro.setAnuladoEn(LocalDateTime.now());
		cobro.setMotivoAnulacion(recortar(motivo));
		cobros.save(cobro);
		MovimientoCaja error = new MovimientoCaja();
		error.setSedeId(entrega.getSedeId());
		error.setTipo(TipoMovimientoCaja.ERROR);
		error.setImporte(cobro.getImporte().setScale(2, RoundingMode.HALF_UP));
		error.setAlumnoId(cobro.getAlumnoId());
		error.setEntregaCajaId(entrega.getId());
		error.setCobroId(cobro.getId());
		error.setProductoAlumnoId(cobro.getProductoAlumnoId());
		error.setMetodo(cobro.getMetodo());
		error.setNota("Cobro #" + cobro.getId() + " deshecho por la solicitud #" + solicitudId);
		error.setCreadoPorUsuarioId(usuarioId);
		error.setFecha(LocalDateTime.now(CajaService.ZONA));
		error.setEstado(EstadoMovimientoCaja.ACTIVO);
		return movimientos.save(error);
	}

	/**
	 * Pieza 6c-3 (decisión 153, §6.3 y §6.4): por qué el cobro no se puede anular por tener devolución, o vacío. Un cobro
	 * con una DEVOLUCION ACTIVA enlazada no se anula; el cargo de un encargo caído con la devolución pendiente tampoco
	 * (no hay dos caminos para el mismo dinero). Lectura sin bloqueo, con el cargo y el cobro ya bloqueados por quien
	 * llama. La 6c-6 la usa en las demás vías ({@link #anular}); la 6c-4 la reutiliza al pedir el tipo 2.
	 */
	public Optional<String> razonDeDevolucion(Long cobroId, Long cargoId) {
		if (cobroId != null && movimientos.existsByCobroIdAndTipoAndEstado(cobroId, TipoMovimientoCaja.DEVOLUCION,
				EstadoMovimientoCaja.ACTIVO)) {
			return Optional.of(EjecucionSolicitud.CON_DEVOLUCION);
		}
		if (cargoId != null && em != null && em.createQuery("SELECT COUNT(s) FROM Solicitud s WHERE "
				+ "s.productoAlumnoId = :cargo AND s.tipo = com.taemoi.project.entities.TipoSolicitud.ENCARGO "
				+ "AND s.devolucionPendiente = TRUE", Long.class).setParameter("cargo", cargoId).getSingleResult() > 0) {
			return Optional.of(EjecucionSolicitud.DEVOLUCION_PENDIENTE);
		}
		return Optional.empty();
	}

	/**
	 * Pieza 6c-6, dentro de una transacción y con el cargo ya bloqueado: bloquea (FOR UPDATE) el cobro ACTIVO del cargo y
	 * lo devuelve releído. Vacío si no tiene. Orden cargo → cobro, el de {@link #anular}.
	 */
	public Optional<Cobro> bloquearActivoDeCargo(Long cargoId) {
		if (cargoId == null) {
			return Optional.empty();
		}
		return cobros.bloquearActivoDeCargo(cargoId).map(this::releer)
				.filter(cobro -> cobro.getEstado() == EstadoCobro.ACTIVO);
	}

	/**
	 * Pieza 6c-6 (D6c-15), dentro de una transacción y con el cargo (y, si hace falta, el cobro) ya bloqueados: bloquea
	 * (FOR UPDATE) la DEVOLUCION ACTIVA del cobro ACTIVO del cargo y la devuelve releída: el cargo está «devuelto».
	 * Vacío si no lo está. Anular esa devolución (A9) bloquea solo el movimiento: no hay ciclo.
	 */
	public Optional<MovimientoCaja> bloquearDevolucionDeCargo(Long cargoId) {
		Optional<Cobro> activo = cargoId == null ? Optional.empty()
				: cobros.findFirstByProductoAlumnoIdAndEstado(cargoId, EstadoCobro.ACTIVO);
		return activo.flatMap(cobro -> movimientos.bloquearDevolucionActivaDeCobro(cobro.getId())).map(devolucion -> {
			if (em != null && em.contains(devolucion)) {
				em.refresh(devolucion);
			}
			return devolucion;
		}).filter(devolucion -> devolucion.getEstado() == EstadoMovimientoCaja.ACTIVO);
	}

	/**
	 * Pieza 6c-6: 409 {@link EjecucionSolicitud#DEVOLUCION_PENDIENTE} si algún encargo del alumno, o de esos cargos,
	 * tiene la devolución pendiente (no hay dos caminos para ese dinero). Comprobación previa al borrado del alumno,
	 * antes de tocar nada; {@link #alBorrarCargos} lo vuelve a mirar en su transacción.
	 */
	public void exigirSinDevolucionPendiente(Long alumnoId, Collection<Long> cargoIds) {
		List<Long> ids = cargoIds == null ? List.of()
				: cargoIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
		if (em == null || (alumnoId == null && ids.isEmpty())) {
			return;
		}
		Long pendientes = em.createQuery("SELECT COUNT(s) FROM Solicitud s WHERE "
				+ "s.tipo = com.taemoi.project.entities.TipoSolicitud.ENCARGO AND s.devolucionPendiente = TRUE "
				+ "AND (s.alumnoId = :alumno OR s.productoAlumnoId IN :cargos)", Long.class)
				.setParameter("alumno", alumnoId == null ? -1L : alumnoId)
				.setParameter("cargos", ids.isEmpty() ? List.of(-1L) : ids).getSingleResult();
		if (pendientes > 0) {
			throw new IllegalStateException(EjecucionSolicitud.DEVOLUCION_PENDIENTE);
		}
	}

	/** Comprobación previa (sin bloquear ni escribir): 409 si algún cobro del cargo va en una entrega de caja. */
	public void exigirSinEntrega(Long cargoId) {
		if (cargoId != null && cobros.existsByProductoAlumnoIdAndEntregaCajaIdIsNotNull(cargoId)) {
			throw new CobroEntregadoException();
		}
	}

	/**
	 * Antes de borrar un cargo bloqueado: 409 si algún cobro suyo va en una entrega; si no, su cobro ACTIVO pasa a
	 * ANULADO (motivo «cargo borrado») y deja de apuntar al cargo (la FK, además, es ON DELETE SET NULL).
	 */
	public void alBorrarCargo(ProductoAlumno cargo) {
		exigirSinEntrega(cargo.getId());
		anular(cargo, usuarioActualId(), MOTIVO_CARGO_BORRADO).ifPresent(cobro -> {
			cobro.setProductoAlumnoId(null);
			cobros.save(cobro);
		});
	}

	/**
	 * Antes de borrar cargos por caminos que no son el DELETE del cargo (borrar un alumno, sacarlo de una convocatoria,
	 * borrar una convocatoria), en la misma transacción que el borrado: sus cobros ACTIVOS pasan a ANULADO (motivo «cargo
	 * borrado», usuario autenticado) y dejan de apuntar al cargo, con un solo UPDATE. Los que ya van en una entrega de
	 * caja NO bloquean el borrado ni se anulan (ese dinero ya está en una entrega cuadrada): siguen ACTIVOS y la FK ON
	 * DELETE SET NULL los desvincula; se avisa en el registro. Lista vacía: nada (sin sentencias).
	 *
	 * <p>Pieza 6c-6 (153): los cobros con una DEVOLUCION ACTIVA tampoco se anulan (el cobro y su devolución quedan en el
	 * historial; siguen ACTIVOS y la FK los desvincula, como los entregados). Si algún cargo es de un encargo con la
	 * devolución pendiente, 409 {@link EjecucionSolicitud#DEVOLUCION_PENDIENTE} sin borrar nada (no hay dos caminos para
	 * ese dinero).
	 */
	public void alBorrarCargos(Collection<Long> cargoIds) {
		List<Long> ids = cargoIds == null ? List.of()
				: cargoIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
		if (ids.isEmpty()) {
			return;
		}
		exigirSinDevolucionPendiente(null, ids);
		List<Long> entregados = cobros.idsActivosEntregadosDeCargos(ids);
		if (!entregados.isEmpty()) {
			LOG.warn("Se borran cargos con cobros ya entregados en caja; esos cobros siguen activos sin cargo: {}",
					entregados);
		}
		List<Long> devueltos = cobros.idsActivosDevueltosDeCargos(ids);
		if (!devueltos.isEmpty()) {
			LOG.info("Se borran cargos con cobros devueltos; esos cobros y su devolución siguen activos sin cargo: {}",
					devueltos);
		}
		cobros.anularDeCargosBorrados(ids, usuarioActualId(), LocalDateTime.now(), MOTIVO_CARGO_BORRADO);
	}

	// ------------------------------------------------------------------ utilidades

	/** Lo que suma la tesorería para el cargo: {@code COALESCE(pa.precio, 0)}, con dos decimales. */
	public static BigDecimal importeDe(ProductoAlumno cargo) {
		double precio = cargo.getPrecio() != null ? cargo.getPrecio() : 0.0;
		return BigDecimal.valueOf(precio).setScale(2, RoundingMode.HALF_UP);
	}

	/** El alumno del cargo como lo resuelve la tesorería: {@code COALESCE(alumno, alumnoDeporte.alumno)}. */
	static Long alumnoDe(ProductoAlumno cargo) {
		Alumno alumno = cargo.alumnoEfectivo();
		return alumno != null ? alumno.getId() : null;
	}

	/** Id del usuario autenticado, o NULL (sin sesión, o con un principal que no es una cuenta). */
	public static Long usuarioActualId() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion != null && autenticacion.getPrincipal() instanceof Usuario usuario) {
			return usuario.getId();
		}
		return null;
	}

	static boolean esCobroActivoDuplicado(Throwable error) {
		for (Throwable causa = error; causa != null; causa = causa.getCause()) {
			String mensaje = causa.getMessage();
			if (mensaje != null && mensaje.toLowerCase(Locale.ROOT).contains(CLAVE_COBRO_ACTIVO)) {
				return true;
			}
			if (causa.getCause() == causa) {
				break;
			}
		}
		return false;
	}

	private static String recortar(String motivo) {
		if (motivo == null || motivo.isBlank()) {
			return null;
		}
		String limpio = motivo.trim();
		return limpio.length() > 500 ? limpio.substring(0, 500) : limpio;
	}
}
