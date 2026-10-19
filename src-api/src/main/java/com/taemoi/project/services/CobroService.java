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
import com.taemoi.project.entities.EstadoCobro;
import com.taemoi.project.entities.MetodoPago;
import com.taemoi.project.entities.OrigenCobro;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.exceptions.pago.CobroEntregadoException;
import com.taemoi.project.repositories.CobroRepository;
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

	private final CobroRepository cobros;
	private final ProductoAlumnoRepository cargos;
	private final TransactionTemplate transaccion;

	@PersistenceContext
	private EntityManager em;

	public CobroService(CobroRepository cobros, ProductoAlumnoRepository cargos,
			PlatformTransactionManager transacciones) {
		this.cobros = cobros;
		this.cargos = cargos;
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
	 */
	public void alBorrarCargos(Collection<Long> cargoIds) {
		List<Long> ids = cargoIds == null ? List.of()
				: cargoIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
		if (ids.isEmpty()) {
			return;
		}
		List<Long> entregados = cobros.idsActivosEntregadosDeCargos(ids);
		if (!entregados.isEmpty()) {
			LOG.warn("Se borran cargos con cobros ya entregados en caja; esos cobros siguen activos sin cargo: {}",
					entregados);
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
