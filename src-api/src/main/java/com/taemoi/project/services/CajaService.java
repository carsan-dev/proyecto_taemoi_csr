package com.taemoi.project.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.taemoi.project.acceso.PredicadosAlcance;
import com.taemoi.project.dtos.request.CajaRequests;
import com.taemoi.project.dtos.response.CajaDTOs;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Cobro;
import com.taemoi.project.entities.EntregaCaja;
import com.taemoi.project.entities.EstadoCobro;
import com.taemoi.project.entities.EstadoEntregaCaja;
import com.taemoi.project.entities.EstadoMovimientoCaja;
import com.taemoi.project.entities.MetodoPago;
import com.taemoi.project.entities.MovimientoCaja;
import com.taemoi.project.entities.OrigenCobro;
import com.taemoi.project.entities.TipoMovimientoCaja;
import com.taemoi.project.organizacion.AlcanceActual;
import com.taemoi.project.organizacion.AlcanceSesion;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.CobroRepository;
import com.taemoi.project.repositories.EntregaCajaRepository;
import com.taemoi.project.repositories.MovimientoCajaRepository;
import com.taemoi.project.repositories.SedeRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Caja de sede (pieza 6b-2, docs/multi-escuela/entrega6-plan.md §7 y fase3-sedes-permisos.md D4).
 *
 * <p><b>Flujo.</b> El profesor cobra en efectivo cargos pendientes de alumnos de sus grupos (origen PROFESOR): ese
 * dinero queda PENDIENTE DE ENTREGA hasta que quien lo recibe (responsable de la sede, gestor o propietario) registra
 * una entrega con los cobros, lo declarado (Σ de sus importes) y lo contado. Lo que cobran el responsable, el gestor o el
 * propietario entra en la caja al momento (decisión 1). Una diferencia entre contado y declarado crea su movimiento
 * SOBRANTE o FALTANTE automático (decisión 5). El propietario anula entregas (sus cobros vuelven a pendientes y su
 * movimiento automático se anula) y movimientos sueltos (decisión 4).
 *
 * <p><b>Saldo de caja de una sede</b> (estado actual):
 *
 * <pre>
 * saldo = Σ cobro.importe  [ACTIVO, metodo EFECTIVO, sede, y (origen ≠ PROFESOR o entrega_caja_id NOT NULL)]
 *       + Σ movimiento.importe [ACTIVO, sede, cualquier tipo]
 * </pre>
 *
 * Una entrega ANULADA libera sus cobros (vuelven a pendientes, dejan de sumar) y anula su movimiento automático (deja
 * de sumar): la caja deja de contarla. El SOBRANTE o FALTANTE automático hace que lo entregado sume lo CONTADO.
 *
 * <p><b>Concurrencia</b> (READ_COMMITTED, como {@link CobroService}): registrar una entrega bloquea sus cobros con
 * SELECT ... FOR UPDATE en orden de id ANTES de validarlos; anular una entrega bloquea la entrega y después sus cobros
 * (en orden de id); la reversión de un cobro (PUT del cargo, convocatoria, deshacer del profesor) bloquea el cargo y
 * después el cobro ({@link CobroService#anular}). Ninguna vía bloquea un cobro y después un cargo o una entrega, así
 * que no hay ciclos de espera. {@code @Version} en las tres entidades como red de seguridad.
 *
 * <p>Toda escritura queda en el registro de acciones ({@link RegistroAccionesCaja}) en su misma transacción.
 */
@Service
public class CajaService {

	/** Zona de la escuela para el período por defecto y la fecha de entregas y movimientos. */
	public static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

	private static final int MAX_TEXTO = 500;
	private static final int TAMANO_PAGINA_MAXIMO = 100;

	static final String NOTA_OBLIGATORIA_DIFERENCIA = "Lo contado no coincide con lo declarado: la nota es obligatoria.";
	static final String COBRO_YA_ENTREGADO = "Algún cobro ya está incluido en una entrega de caja.";
	static final String COBRO_NO_ACTIVO = "Algún cobro ya no está activo.";

	private final CobroRepository cobros;
	private final EntregaCajaRepository entregas;
	private final MovimientoCajaRepository movimientos;
	private final SedeRepository sedes;
	private final AlumnoRepository alumnos;
	private final AlcanceActual alcanceActual;
	private final ProductoAlumnoService productosAlumno;
	private final RegistroAccionesCaja registro;
	private final TransactionTemplate transaccion;

	@PersistenceContext
	private EntityManager em;

	public CajaService(CobroRepository cobros, EntregaCajaRepository entregas, MovimientoCajaRepository movimientos,
			SedeRepository sedes, AlumnoRepository alumnos, AlcanceActual alcanceActual,
			ProductoAlumnoService productosAlumno, RegistroAccionesCaja registro,
			PlatformTransactionManager transacciones) {
		this.cobros = cobros;
		this.entregas = entregas;
		this.movimientos = movimientos;
		this.sedes = sedes;
		this.alumnos = alumnos;
		this.alcanceActual = alcanceActual;
		this.productosAlumno = productosAlumno;
		this.registro = registro;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
	}

	// ------------------------------------------------------------------ profesor (filas; solo sus propios totales)

	/**
	 * Cargos PENDIENTES (pagado falso o nulo) de los alumnos de los grupos del profesor de la sesión, de cualquier tipo.
	 * Una consulta con los datos que se muestran (sin cargar entidades). Solo cargos con {@code alumno_id} (los que el
	 * profesor puede cobrar) y no cancelados (pieza 6b-2c). Sin grupos en el alcance (o con alcance total), lista vacía.
	 */
	public List<CajaDTOs.CargoPendienteProfesor> cargosPendientesDelProfesor() {
		Set<Long> grupos = gruposDelProfesor();
		if (grupos.isEmpty()) {
			return List.of();
		}
		List<Object[]> filas = em.createQuery("""
				SELECT pa.id, a.id, a.nombre, a.apellidos, pa.concepto, pa.precio, pa.fechaAsignacion, pa.sedeId, s.nombre
				FROM ProductoAlumno pa JOIN pa.alumno a LEFT JOIN Sede s ON s.id = pa.sedeId
				WHERE (pa.pagado IS NULL OR pa.pagado = FALSE) AND pa.canceladoEn IS NULL
				  AND EXISTS (SELECT g.id FROM Grupo g JOIN g.alumnos m WHERE m.id = a.id AND g.id IN :grupos)
				ORDER BY a.apellidos, a.nombre, pa.fechaAsignacion, pa.id""", Object[].class)
				.setParameter("grupos", grupos).getResultList();
		List<CajaDTOs.CargoPendienteProfesor> resultado = new ArrayList<>(filas.size());
		for (Object[] f : filas) {
			resultado.add(new CajaDTOs.CargoPendienteProfesor((Long) f[0], (Long) f[1], (String) f[2], (String) f[3],
					(String) f[4], importe((Double) f[5]), (Date) f[6], (Long) f[7], (String) f[8]));
		}
		return resultado;
	}

	/** Cobra en efectivo un cargo de sus grupos (ver {@link ProductoAlumnoService#cobrarEnEfectivoComoProfesor}). */
	public CajaDTOs.CobroCaja cobrarComoProfesor(Long cargoId) {
		Cobro cobro = productosAlumno.cobrarEnEfectivoComoProfesor(cargoId);
		return filasDeCobros(List.of(cobro)).get(0);
	}

	/** Deshace su cobro (ver {@link ProductoAlumnoService#deshacerCobroDeProfesor}). */
	public void deshacerCobroDelProfesor(Long cobroId) {
		deshacerCobroDelProfesor(cobroId, null);
	}

	/** Pieza 6b-2c: deshace su cobro con su motivo ({@code null} = AUN_NO_PAGADO, como en la 6b-2). */
	public void deshacerCobroDelProfesor(Long cobroId, CajaRequests.MotivoDeshacer motivo) {
		productosAlumno.deshacerCobroDeProfesor(cobroId, CobroService.usuarioActualId(), motivo);
	}

	/** Productos que el profesor puede apuntar y cobrar (la lista blanca de {@link ProductoAlumnoService}). */
	public List<CajaDTOs.ProductoVendible> productosVendiblesPorProfesor() {
		return productosAlumno.productosVendiblesPorProfesor().stream()
				.map(p -> new CajaDTOs.ProductoVendible(p.getId(), p.getConcepto(), importe(p.getPrecio()))).toList();
	}

	/** Apunta y cobra un producto (ver {@link ProductoAlumnoService#apuntarYCobrarProductoComoProfesor}). */
	public CajaDTOs.CobroCaja apuntarYCobrarProductoComoProfesor(Long alumnoId, Long productoId) {
		Cobro cobro = productosAlumno.apuntarYCobrarProductoComoProfesor(alumnoId, productoId);
		return filasDeCobros(List.of(cobro)).get(0);
	}

	/**
	 * Sus cobros pendientes de entrega, con SUS totales (número e importe): lo que lleva encima. Nunca totales de sede,
	 * caja, escuela ni de otros profesores.
	 */
	public CajaDTOs.MisCobrosPendientes cobrosPendientesDeEntregaDelProfesor() {
		Long usuarioId = CobroService.usuarioActualId();
		List<CajaDTOs.CobroCaja> filas = usuarioId == null ? List.of()
				: filasDeCobros(cobros.pendientesDeEntregaDelProfesor(usuarioId));
		BigDecimal total = dosDecimales(
				filas.stream().map(CajaDTOs.CobroCaja::importe).reduce(BigDecimal.ZERO, BigDecimal::add));
		return new CajaDTOs.MisCobrosPendientes(filas.size(), total, filas);
	}

	// ------------------------------------------------------------------ caja de sede: lectura

	/**
	 * Resumen de la caja de la sede para [{@code desde}, {@code hasta}] (fechas de Europe/Madrid, ambas incluidas; por
	 * defecto, el mes en curso). Fórmulas en {@link CajaDTOs.ResumenCajaSede} y en la cabecera.
	 */
	public CajaDTOs.ResumenCajaSede resumen(Long sedeId, LocalDate desde, LocalDate hasta) {
		exigirSede(sedeId);
		LocalDate hoy = LocalDate.now(ZONA);
		LocalDate inicio = desde != null ? desde : hoy.withDayOfMonth(1);
		LocalDate fin = hasta != null ? hasta : hoy.withDayOfMonth(hoy.lengthOfMonth());
		if (fin.isBefore(inicio)) {
			throw new IllegalArgumentException("La fecha final es anterior a la inicial.");
		}
		LocalDateTime desdeHora = inicio.atStartOfDay();
		LocalDateTime hastaHora = fin.plusDays(1).atStartOfDay();
		BigDecimal pendiente = dosDecimales(cobros.sumarPendienteDeEntregaEnSede(sedeId));
		BigDecimal recibido = dosDecimales(entregas.sumarContadoActivas(sedeId, desdeHora, hastaHora));
		BigDecimal diferencias = dosDecimales(movimientos.sumarDiferenciasActivas(sedeId, desdeHora, hastaHora));
		BigDecimal saldo = dosDecimales(
				cobros.sumarEfectivoEnCajaDeSede(sedeId).add(movimientos.sumarActivos(sedeId)));
		return new CajaDTOs.ResumenCajaSede(sedeId, inicio, fin, pendiente, recibido, diferencias, saldo);
	}

	/**
	 * Lo pendiente de entregar en la sede agrupado por cobrador (profesor): número de cobros, importe, cobro más antiguo y
	 * la lista. Del cobro más antiguo al más nuevo. Tres consultas (cobros, alumnos y cuentas).
	 */
	public List<CajaDTOs.PendienteCobrador> pendientePorCobrador(Long sedeId) {
		exigirSede(sedeId);
		List<Cobro> pendientes = cobros.pendientesDeEntregaEnSede(sedeId);
		List<CajaDTOs.CobroCaja> filas = filasDeCobros(pendientes);
		Map<Long, List<CajaDTOs.CobroCaja>> porCobrador = new LinkedHashMap<>();
		for (int i = 0; i < pendientes.size(); i++) {
			porCobrador.computeIfAbsent(pendientes.get(i).getCobradoPorUsuarioId(), k -> new ArrayList<>())
					.add(filas.get(i));
		}
		Map<Long, String> nombres = nombresDeCuentas(porCobrador.keySet());
		List<CajaDTOs.PendienteCobrador> resultado = new ArrayList<>();
		porCobrador.forEach((usuarioId, suyos) -> {
			BigDecimal total = dosDecimales(
					suyos.stream().map(CajaDTOs.CobroCaja::importe).reduce(BigDecimal.ZERO, BigDecimal::add));
			Date masAntiguo = suyos.stream().map(CajaDTOs.CobroCaja::fechaCobro).filter(Objects::nonNull)
					.min(Comparator.naturalOrder()).orElse(null);
			resultado.add(new CajaDTOs.PendienteCobrador(usuarioId, nombres.get(usuarioId), suyos.size(), total,
					masAntiguo, suyos));
		});
		resultado.sort(Comparator.comparing(CajaDTOs.PendienteCobrador::cobroMasAntiguo,
				Comparator.nullsLast(Comparator.naturalOrder())));
		return resultado;
	}

	/** Historial de entregas de la sede (paginado desde 1, de la más reciente a la más antigua). */
	public Page<CajaDTOs.EntregaCaja> historialDeEntregas(Long sedeId, int pagina, int tamano) {
		exigirSede(sedeId);
		if (pagina < 1 || tamano < 1) {
			throw new IllegalArgumentException("Página y tamaño deben ser mayores que cero.");
		}
		Page<EntregaCaja> encontradas = entregas.historialDeSede(sedeId,
				PageRequest.of(pagina - 1, Math.min(tamano, TAMANO_PAGINA_MAXIMO)));
		return new PageImpl<>(dtosDeEntregas(encontradas.getContent()), encontradas.getPageable(),
				encontradas.getTotalElements());
	}

	/** Movimientos de la sede con fecha en [desde, hasta] (por defecto, el mes en curso), del más reciente al más antiguo. */
	public List<CajaDTOs.MovimientoCaja> movimientosDeSede(Long sedeId, LocalDate desde, LocalDate hasta) {
		exigirSede(sedeId);
		LocalDate hoy = LocalDate.now(ZONA);
		LocalDate inicio = desde != null ? desde : hoy.withDayOfMonth(1);
		LocalDate fin = hasta != null ? hasta : hoy.withDayOfMonth(hoy.lengthOfMonth());
		if (fin.isBefore(inicio)) {
			throw new IllegalArgumentException("La fecha final es anterior a la inicial.");
		}
		return movimientos.deSedeEnPeriodo(sedeId, inicio.atStartOfDay(), fin.plusDays(1).atStartOfDay()).stream()
				.map(CajaService::dto).toList();
	}

	// ------------------------------------------------------------------ caja de sede: escritura

	/**
	 * Registra la entrega del profesor {@code entregadoPorUsuarioId} en la sede: recibe el usuario de la sesión.
	 * Bloquea los cobros (FOR UPDATE, en orden de id) ANTES de validarlos: dos entregas a la vez con un cobro común, una
	 * gana y la otra 409 sin efectos. Cada cobro: existe en la escuela (si no, 400), ACTIVO y sin entrega (si no, 409),
	 * EFECTIVO de origen PROFESOR, cobrado por quien entrega y de esa sede (si no, 400). Declarado = Σ importes; contado
	 * &gt;= 0; si difieren, nota obligatoria y movimiento SOBRANTE (contado &gt; declarado) o FALTANTE automático.
	 */
	public CajaDTOs.EntregaCaja registrarEntrega(Long sedeId, CajaRequests.RegistrarEntrega peticion) {
		if (peticion == null) {
			throw new IllegalArgumentException("Faltan los datos de la entrega.");
		}
		exigirSede(sedeId);
		Long recibe = usuarioDeLaSesion();
		Long entregadoPor = peticion.entregadoPorUsuarioId();
		if (entregadoPor == null) {
			throw new IllegalArgumentException("Indica quién entrega el dinero.");
		}
		List<Long> ids = peticion.cobroIds() == null ? List.of()
				: peticion.cobroIds().stream().filter(Objects::nonNull).distinct().toList();
		if (ids.isEmpty() || ids.size() != peticion.cobroIds().size()) {
			throw new IllegalArgumentException("Indica los cobros que se entregan (sin repetir).");
		}
		BigDecimal contado = importeValido(peticion.importeContado(), "El importe contado");
		if (contado.signum() < 0) {
			throw new IllegalArgumentException("El importe contado no puede ser negativo.");
		}
		String nota = texto(peticion.nota(), "La nota");

		return enTransaccion(() -> {
			List<Cobro> bloqueados = cobros.bloquearPorIdsEnOrden(ids).stream().map(this::releer).toList();
			if (bloqueados.size() != ids.size()) {
				throw new IllegalArgumentException("Algún cobro no existe.");
			}
			for (Cobro cobro : bloqueados) {
				if (cobro.getEstado() != EstadoCobro.ACTIVO) {
					throw new IllegalStateException(COBRO_NO_ACTIVO);
				}
				if (cobro.getEntregaCajaId() != null) {
					throw new IllegalStateException(COBRO_YA_ENTREGADO);
				}
				if (cobro.getOrigen() != OrigenCobro.PROFESOR || cobro.getMetodo() != MetodoPago.EFECTIVO) {
					throw new IllegalArgumentException("Solo se entregan cobros en efectivo de un profesor.");
				}
				if (!entregadoPor.equals(cobro.getCobradoPorUsuarioId())) {
					throw new IllegalArgumentException("Algún cobro no lo cobró quien entrega.");
				}
				if (!sedeId.equals(cobro.getSedeId())) {
					throw new IllegalArgumentException("Algún cobro no es de esta sede.");
				}
			}
			BigDecimal declarado = dosDecimales(
					bloqueados.stream().map(Cobro::getImporte).reduce(BigDecimal.ZERO, BigDecimal::add));
			BigDecimal diferencia = contado.subtract(declarado);
			if (diferencia.signum() != 0 && nota == null) {
				throw new IllegalArgumentException(NOTA_OBLIGATORIA_DIFERENCIA);
			}
			LocalDateTime ahora = LocalDateTime.now(ZONA);
			EntregaCaja nueva = new EntregaCaja();
			nueva.setSedeId(sedeId);
			nueva.setEntregadoPorUsuarioId(entregadoPor);
			nueva.setRecibidoPorUsuarioId(recibe);
			nueva.setImporteDeclarado(declarado);
			nueva.setImporteContado(contado);
			nueva.setFecha(ahora);
			nueva.setNota(nota);
			nueva.setEstado(EstadoEntregaCaja.ACTIVA);
			EntregaCaja guardada = entregas.save(nueva);
			for (Cobro cobro : bloqueados) {
				cobro.setEntregaCajaId(guardada.getId());
				cobros.save(cobro);
			}
			Long movimientoId = null;
			if (diferencia.signum() != 0) {
				MovimientoCaja automatico = new MovimientoCaja();
				automatico.setSedeId(sedeId);
				automatico.setTipo(diferencia.signum() > 0 ? TipoMovimientoCaja.SOBRANTE : TipoMovimientoCaja.FALTANTE);
				automatico.setImporte(diferencia);
				automatico.setEntregaCajaId(guardada.getId());
				automatico.setNota(nota);
				automatico.setCreadoPorUsuarioId(recibe);
				automatico.setFecha(ahora);
				automatico.setEstado(EstadoMovimientoCaja.ACTIVO);
				movimientoId = movimientos.save(automatico).getId();
			}
			Map<String, Object> implicados = new LinkedHashMap<>();
			implicados.put("entrega", guardada.getId());
			implicados.put("cobros", ids.stream().sorted().toList());
			if (movimientoId != null) {
				implicados.put("movimiento", movimientoId);
			}
			implicados.put("entregadoPor", entregadoPor);
			// Autoentrega (decisión del propietario): quien recibe es quien entrega (responsable de la sede que además es
			// profesor). Se permite y queda con su propio tipo de acción.
			registro.registrar(entregadoPor.equals(recibe) ? RegistroAccionesCaja.Accion.AUTOENTREGA_REGISTRADA
					: RegistroAccionesCaja.Accion.ENTREGA_REGISTRADA, "/api/caja/sedes/" + sedeId + "/entregas", sedeId,
					implicados);
			em.flush();
			return dtosDeEntregas(List.of(guardada)).get(0);
		});
	}

	/**
	 * Solo el propietario: anula la entrega (motivo obligatorio). La entrega pasa a ANULADA, sus cobros vuelven a
	 * pendientes de entrega ({@code entrega_caja_id} NULL) y su movimiento automático, si lo tiene, se anula. Un cobro
	 * cuyo cargo se borró mientras estaba entregado ({@code producto_alumno_id} NULL) no vuelve a pendiente: pasa a
	 * ANULADO con motivo «cargo borrado», como los cobros no entregados de un cargo borrado (6b-1). Bloquea la entrega y
	 * después sus cobros. Ya anulada → 409; no existe en la escuela → 404.
	 */
	public CajaDTOs.EntregaCaja anularEntrega(Long entregaId, CajaRequests.Anulacion peticion) {
		String motivo = motivoObligatorio(peticion);
		Long usuario = usuarioDeLaSesion();
		return enTransaccion(() -> {
			EntregaCaja entrega = entregas.bloquearPorId(entregaId).map(this::releer)
					.orElseThrow(() -> new NoSuchElementException("La entrega no existe."));
			if (entrega.getEstado() == EstadoEntregaCaja.ANULADA) {
				throw new IllegalStateException("La entrega ya está anulada.");
			}
			LocalDateTime ahora = LocalDateTime.now(ZONA);
			entrega.setEstado(EstadoEntregaCaja.ANULADA);
			entrega.setAnuladaPorUsuarioId(usuario);
			entrega.setAnuladaEn(ahora);
			entrega.setMotivoAnulacion(motivo);
			entregas.save(entrega);
			List<Long> liberados = new ArrayList<>();
			List<Long> sinCargo = new ArrayList<>();
			for (Cobro cobro : cobros.bloquearDeEntregaEnOrden(entregaId).stream().map(this::releer).toList()) {
				cobro.setEntregaCajaId(null);
				if (cobro.getProductoAlumnoId() == null && cobro.getEstado() == EstadoCobro.ACTIVO) {
					// Su cargo se borró mientras estaba entregado (6b-1: siguió ACTIVO sin cargo). Al anular la entrega no
					// vuelve a pendiente: se anula como los cobros no entregados de un cargo borrado.
					cobro.setEstado(EstadoCobro.ANULADO);
					cobro.setAnuladoPorUsuarioId(usuario);
					cobro.setAnuladoEn(LocalDateTime.now());
					cobro.setMotivoAnulacion(CobroService.MOTIVO_CARGO_BORRADO);
					sinCargo.add(cobro.getId());
				} else {
					liberados.add(cobro.getId());
				}
				cobros.save(cobro);
			}
			List<Long> anulados = new ArrayList<>();
			for (MovimientoCaja movimiento : movimientos.findByEntregaCajaIdInOrderByIdAsc(List.of(entregaId))) {
				if (movimiento.getEstado() == EstadoMovimientoCaja.ACTIVO) {
					anularMovimiento(movimiento, usuario, ahora, motivo);
					anulados.add(movimiento.getId());
				}
			}
			Map<String, Object> implicados = new LinkedHashMap<>();
			implicados.put("entrega", entregaId);
			implicados.put("cobros", liberados);
			if (!sinCargo.isEmpty()) {
				implicados.put("cobrosAnuladosSinCargo", sinCargo);
			}
			implicados.put("movimientos", anulados);
			registro.registrar(RegistroAccionesCaja.Accion.ENTREGA_ANULADA,
					"/api/caja/entregas/" + entregaId + "/anular", entrega.getSedeId(), implicados);
			em.flush();
			return dtosDeEntregas(List.of(entrega)).get(0);
		});
	}

	/**
	 * Apunta un movimiento de caja en la sede (decisión 5): importe distinto de cero con dos decimales y nota
	 * obligatoria. DEVOLUCION: negativo y con alumno; SOBRANTE: positivo y sin alumno; FALTANTE: negativo y sin alumno;
	 * ERROR y OTRO: cualquier signo, alumno opcional. El alumno, si lo hay, debe ser de esa sede (D9: sede principal o
	 * grupo de la sede), que para el responsable es una de las suyas.
	 */
	public CajaDTOs.MovimientoCaja crearMovimiento(Long sedeId, CajaRequests.CrearMovimiento peticion) {
		if (peticion == null || peticion.tipo() == null) {
			throw new IllegalArgumentException("Indica el tipo de movimiento.");
		}
		exigirSede(sedeId);
		Long usuario = usuarioDeLaSesion();
		BigDecimal importe = importeValido(peticion.importe(), "El importe");
		String nota = texto(peticion.nota(), "La nota");
		if (nota == null) {
			throw new IllegalArgumentException("La nota es obligatoria.");
		}
		TipoMovimientoCaja tipo = peticion.tipo();
		Long alumnoId = peticion.alumnoId();
		if (importe.signum() == 0) {
			throw new IllegalArgumentException("El importe no puede ser cero.");
		}
		switch (tipo) {
		case DEVOLUCION -> {
			if (importe.signum() > 0) {
				throw new IllegalArgumentException("Una devolución tiene importe negativo.");
			}
			if (alumnoId == null) {
				throw new IllegalArgumentException("Una devolución necesita el alumno.");
			}
		}
		case SOBRANTE, FALTANTE -> {
			if (tipo == TipoMovimientoCaja.SOBRANTE && importe.signum() < 0) {
				throw new IllegalArgumentException("Un sobrante tiene importe positivo.");
			}
			if (tipo == TipoMovimientoCaja.FALTANTE && importe.signum() > 0) {
				throw new IllegalArgumentException("Un faltante tiene importe negativo.");
			}
			if (alumnoId != null) {
				throw new IllegalArgumentException("Un sobrante o un faltante no lleva alumno.");
			}
		}
		case ERROR, OTRO -> {
			// Cualquier signo; alumno opcional.
		}
		}
		if (alumnoId != null) {
			Specification<Alumno> porId = (root, query, cb) -> cb.equal(root.get("id"), alumnoId);
			if (!alumnos.exists(porId.and(PredicadosAlcance.alumnosEnSedes(Set.of(sedeId))))) {
				throw new IllegalArgumentException("El alumno no es de esta sede.");
			}
		}
		return enTransaccion(() -> {
			MovimientoCaja movimiento = new MovimientoCaja();
			movimiento.setSedeId(sedeId);
			movimiento.setTipo(tipo);
			movimiento.setImporte(importe);
			movimiento.setAlumnoId(alumnoId);
			movimiento.setNota(nota);
			movimiento.setCreadoPorUsuarioId(usuario);
			movimiento.setFecha(LocalDateTime.now(ZONA));
			movimiento.setEstado(EstadoMovimientoCaja.ACTIVO);
			MovimientoCaja guardado = movimientos.save(movimiento);
			Map<String, Object> implicados = new LinkedHashMap<>();
			implicados.put("movimiento", guardado.getId());
			if (alumnoId != null) {
				implicados.put("alumno", alumnoId);
			}
			registro.registrar(RegistroAccionesCaja.Accion.MOVIMIENTO_CREADO,
					"/api/caja/sedes/" + sedeId + "/movimientos", sedeId, implicados);
			em.flush();
			return dto(guardado);
		});
	}

	/**
	 * Solo el propietario: anula un movimiento suelto (motivo obligatorio). El automático de una entrega no se anula
	 * suelto (409: se anula la entrega). Ya anulado → 409; no existe en la escuela → 404.
	 */
	public CajaDTOs.MovimientoCaja anularMovimiento(Long movimientoId, CajaRequests.Anulacion peticion) {
		String motivo = motivoObligatorio(peticion);
		Long usuario = usuarioDeLaSesion();
		return enTransaccion(() -> {
			MovimientoCaja movimiento = movimientos.bloquearPorId(movimientoId).map(this::releer)
					.orElseThrow(() -> new NoSuchElementException("El movimiento no existe."));
			if (movimiento.getEntregaCajaId() != null) {
				throw new IllegalStateException(
						"Es la diferencia automática de una entrega: se anula anulando la entrega.");
			}
			if (movimiento.getEstado() == EstadoMovimientoCaja.ANULADO) {
				throw new IllegalStateException("El movimiento ya está anulado.");
			}
			anularMovimiento(movimiento, usuario, LocalDateTime.now(ZONA), motivo);
			registro.registrar(RegistroAccionesCaja.Accion.MOVIMIENTO_ANULADO,
					"/api/caja/movimientos/" + movimientoId + "/anular", movimiento.getSedeId(),
					Map.of("movimiento", movimientoId));
			em.flush();
			return dto(movimiento);
		});
	}

	// ------------------------------------------------------------------ utilidades

	private void anularMovimiento(MovimientoCaja movimiento, Long usuario, LocalDateTime ahora, String motivo) {
		movimiento.setEstado(EstadoMovimientoCaja.ANULADO);
		movimiento.setAnuladoPorUsuarioId(usuario);
		movimiento.setAnuladoEn(ahora);
		movimiento.setMotivoAnulacion(motivo);
		movimientos.save(movimiento);
	}

	private <T> T enTransaccion(Supplier<T> accion) {
		return transaccion.execute(estado -> accion.get());
	}

	/** Relee de la base una entidad ya bloqueada (una consulta con bloqueo no refresca la instancia de la sesión). */
	private <T> T releer(T entidad) {
		if (em.contains(entidad)) {
			em.refresh(entidad);
		}
		return entidad;
	}

	private Set<Long> gruposDelProfesor() {
		AlcanceSesion alcance = alcanceActual.obtener();
		return alcance.total() ? Set.of() : alcance.grupos();
	}

	private void exigirSede(Long sedeId) {
		if (sedeId == null || sedes.buscarEnEscuelaActiva(sedeId).isEmpty()) {
			throw new NoSuchElementException("La sede no existe en la escuela.");
		}
	}

	private static Long usuarioDeLaSesion() {
		Long usuario = CobroService.usuarioActualId();
		if (usuario == null) {
			throw new AccessDeniedException("Acceso denegado.");
		}
		return usuario;
	}

	private static String motivoObligatorio(CajaRequests.Anulacion peticion) {
		String motivo = texto(peticion == null ? null : peticion.motivo(), "El motivo");
		if (motivo == null) {
			throw new IllegalArgumentException("El motivo es obligatorio.");
		}
		return motivo;
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

	/** Importe obligatorio con como mucho dos decimales, devuelto con escala 2. */
	private static BigDecimal importeValido(BigDecimal valor, String campo) {
		if (valor == null) {
			throw new IllegalArgumentException(campo + " es obligatorio.");
		}
		try {
			BigDecimal escalado = valor.setScale(2, RoundingMode.UNNECESSARY);
			if (escalado.abs().compareTo(new BigDecimal("99999999.99")) > 0) {
				throw new IllegalArgumentException(campo + " es demasiado grande.");
			}
			return escalado;
		} catch (ArithmeticException e) {
			throw new IllegalArgumentException(campo + " admite como mucho dos decimales.");
		}
	}

	private static BigDecimal dosDecimales(BigDecimal valor) {
		return (valor == null ? BigDecimal.ZERO : valor).setScale(2, RoundingMode.HALF_UP);
	}

	private static BigDecimal importe(Double precio) {
		return BigDecimal.valueOf(precio != null ? precio : 0.0).setScale(2, RoundingMode.HALF_UP);
	}

	/** Filas de cobros con el nombre del alumno (una consulta para todos los alumnos). */
	private List<CajaDTOs.CobroCaja> filasDeCobros(List<Cobro> lista) {
		Map<Long, String[]> nombres = nombresDeAlumnos(
				lista.stream().map(Cobro::getAlumnoId).filter(Objects::nonNull).collect(
						java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
		List<CajaDTOs.CobroCaja> filas = new ArrayList<>(lista.size());
		for (Cobro c : lista) {
			String[] nombre = nombres.getOrDefault(c.getAlumnoId(), new String[2]);
			filas.add(new CajaDTOs.CobroCaja(c.getId(), c.getProductoAlumnoId(), c.getAlumnoId(), nombre[0], nombre[1],
					c.getConcepto(), dosDecimales(c.getImporte()), c.getFechaCobro(), c.getSedeId()));
		}
		return filas;
	}

	private Map<Long, String[]> nombresDeAlumnos(Collection<Long> ids) {
		Map<Long, String[]> nombres = new HashMap<>();
		if (ids.isEmpty()) {
			return nombres;
		}
		for (Object[] f : em.createQuery("SELECT a.id, a.nombre, a.apellidos FROM Alumno a WHERE a.id IN :ids",
				Object[].class).setParameter("ids", ids).getResultList()) {
			nombres.put((Long) f[0], new String[] { (String) f[1], (String) f[2] });
		}
		return nombres;
	}

	/** Nombre visible (nombre y apellidos) de esas cuentas, con una consulta. */
	private Map<Long, String> nombresDeCuentas(Collection<Long> ids) {
		Map<Long, String> nombres = new HashMap<>();
		List<Long> distintos = ids.stream().filter(Objects::nonNull).distinct().toList();
		if (distintos.isEmpty()) {
			return nombres;
		}
		for (Object[] f : em.createQuery("SELECT u.id, u.nombre, u.apellidos FROM Usuario u WHERE u.id IN :ids",
				Object[].class).setParameter("ids", distintos).getResultList()) {
			String nombre = ((f[1] == null ? "" : f[1]) + " " + (f[2] == null ? "" : f[2])).trim();
			nombres.put((Long) f[0], nombre);
		}
		return nombres;
	}

	/** DTOs de entregas: cobros, movimientos automáticos y nombres con tres consultas para toda la lista. */
	private List<CajaDTOs.EntregaCaja> dtosDeEntregas(List<EntregaCaja> lista) {
		if (lista.isEmpty()) {
			return List.of();
		}
		List<Long> ids = lista.stream().map(EntregaCaja::getId).toList();
		Map<Long, List<Long>> cobrosPorEntrega = new HashMap<>();
		for (Cobro c : cobros.deEntregas(ids)) {
			cobrosPorEntrega.computeIfAbsent(c.getEntregaCajaId(), k -> new ArrayList<>()).add(c.getId());
		}
		Map<Long, Long> movimientoPorEntrega = new HashMap<>();
		for (MovimientoCaja m : movimientos.findByEntregaCajaIdInOrderByIdAsc(ids)) {
			movimientoPorEntrega.putIfAbsent(m.getEntregaCajaId(), m.getId());
		}
		Set<Long> cuentas = new LinkedHashSet<>();
		lista.forEach(e -> {
			cuentas.add(e.getEntregadoPorUsuarioId());
			cuentas.add(e.getRecibidoPorUsuarioId());
		});
		Map<Long, String> nombres = nombresDeCuentas(cuentas);
		List<CajaDTOs.EntregaCaja> dtos = new ArrayList<>(lista.size());
		for (EntregaCaja e : lista) {
			dtos.add(new CajaDTOs.EntregaCaja(e.getId(), e.getSedeId(), e.getEntregadoPorUsuarioId(),
					nombres.get(e.getEntregadoPorUsuarioId()), e.getRecibidoPorUsuarioId(),
					nombres.get(e.getRecibidoPorUsuarioId()), dosDecimales(e.getImporteDeclarado()),
					dosDecimales(e.getImporteContado()), dosDecimales(e.getDiferencia()), e.getFecha(), e.getNota(),
					e.getEstado().name(), cobrosPorEntrega.getOrDefault(e.getId(), List.of()),
					movimientoPorEntrega.get(e.getId()), e.getAnuladaPorUsuarioId(), e.getAnuladaEn(),
					e.getMotivoAnulacion(), e.getEntregadoPorUsuarioId() != null
							&& e.getEntregadoPorUsuarioId().equals(e.getRecibidoPorUsuarioId())));
		}
		return dtos;
	}

	private static CajaDTOs.MovimientoCaja dto(MovimientoCaja m) {
		return new CajaDTOs.MovimientoCaja(m.getId(), m.getSedeId(), m.getTipo().name(), dosDecimales(m.getImporte()),
				m.getAlumnoId(), m.getEntregaCajaId(), m.getNota(), m.getCreadoPorUsuarioId(), m.getFecha(),
				m.getEstado().name(), m.getAnuladoPorUsuarioId(), m.getAnuladoEn(), m.getMotivoAnulacion());
	}
}
