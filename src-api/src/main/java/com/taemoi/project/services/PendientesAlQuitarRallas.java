package com.taemoi.project.services;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.taemoi.project.config.ValidacionRelojConfig;
import com.taemoi.project.dtos.response.RallasDTOs;
import com.taemoi.project.entities.AlumnoConvocatoria;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.repositories.CobroRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.repositories.SolicitudRepository;
import com.taemoi.project.utils.ImporteCargo;

import jakarta.persistence.EntityManager;

/**
 * Lo que se deshace al bajar las rallas desde las 10 medias, además del apto de esa disciplina: los pases de grado sin
 * cobrar de esa inscripción y las convocatorias pendientes de esa disciplina a las que está apuntado (de ellas sale y su
 * cargo se borra). Lo usa {@link RallasService}; todo en SU transacción.
 *
 * <p><b>Pase pendiente</b> (el criterio de {@code ProfesorService#tienePasePendiente} y del cobro al subir el grado):
 * cargo de esa inscripción ({@code producto_alumno.alumno_deporte_id}) y de ese alumno, sin pagar ni cancelar, cuyo
 * producto concede grado ({@link ProductosCatalogoService#buscarCodigoGradoConcedido}). Los cargos de examen puestos a
 * mano y los de convocatoria no llevan inscripción: no son pases.
 *
 * <p><b>Convocatoria pendiente</b>: el modelo no tiene estado «cerrada»; vale la de la pantalla de convocatorias del
 * alumno ({@code ConvocatoriaServiceImpl#esConvocatoriaYaProcesada}: aún no tiene el grado al que se presentaba) y,
 * además, aún no celebrada: fecha de hoy (Europe/Madrid) o posterior. De la misma disciplina y del alumno (por
 * {@code alumno_id} o por la inscripción).
 *
 * <p><b>Qué se puede deshacer</b>: un pase sin ninguna fila en {@code cobro} (tampoco ANULADA), sin encargo en curso y
 * sin convocatoria; una convocatoria con la inscripción sin pagar y, si tiene cargo, el cargo sin pagar ni cancelar, sin
 * cobros y sin encargo en curso. Si algo no se puede, 409 y no se cambia nada: no se anulan cobros ni se tocan
 * devoluciones. El derecho a examen del alumno no se toca (un pase sin cobrar nunca lo dio).
 *
 * <p>Sin dependencias de {@code ProductoAlumnoService}, {@code AlumnoDeporteService} ni {@code AlumnoService}: hay un
 * ciclo de beans con {@link RallasService}.
 */
@Component
public class PendientesAlQuitarRallas {

	public static final String PASES_CAMBIARON = "Algo ha cambiado mientras tanto. Vuelve a abrir la hoja.";
	public static final String PASE_NO_SE_BORRA = "Tiene un pase de grado con cobros o con un encargo en curso. "
			+ "Primero hay que resolverlo desde gestión.";
	public static final String CONVOCATORIA_NO_SE_QUITA = "Está apuntado a una convocatoria con el examen pagado, con "
			+ "cobros o con un encargo en curso. Primero hay que resolverlo desde gestión.";

	/** Pases (cargos) y convocatorias (inscripciones en ellas) afectados, por id ascendente. */
	public record Pendientes(List<ProductoAlumno> pases, List<AlumnoConvocatoria> convocatorias) {

		public boolean vacios() {
			return pases.isEmpty() && convocatorias.isEmpty();
		}

		Set<Long> idsPases() {
			Set<Long> ids = new HashSet<>();
			pases.forEach(pase -> ids.add(pase.getId()));
			return ids;
		}

		Set<Long> idsConvocatorias() {
			Set<Long> ids = new HashSet<>();
			convocatorias.forEach(inscripcion -> ids.add(inscripcion.getConvocatoria().getId()));
			return ids;
		}
	}

	/** Lo borrado, como lo devuelve la API. */
	public record Quitados(List<RallasDTOs.PasePendiente> pases, List<RallasDTOs.ConvocatoriaPendiente> convocatorias) {
	}

	private final EntityManager em;
	private final Clock reloj;
	private final CobroService cobros;
	private final CobroRepository cobroRepository;
	private final ProductoAlumnoRepository productoAlumnoRepository;
	private final SolicitudRepository solicitudRepository;
	private final ProductosCatalogoService productosCatalogo;
	private final RegistroAccionesCargos registroCargos;

	public PendientesAlQuitarRallas(EntityManager em, Clock reloj, CobroService cobros, CobroRepository cobroRepository,
			ProductoAlumnoRepository productoAlumnoRepository, SolicitudRepository solicitudRepository,
			ProductosCatalogoService productosCatalogo, RegistroAccionesCargos registroCargos) {
		this.em = em;
		this.reloj = reloj;
		this.cobros = cobros;
		this.cobroRepository = cobroRepository;
		this.productoAlumnoRepository = productoAlumnoRepository;
		this.solicitudRepository = solicitudRepository;
		this.productosCatalogo = productosCatalogo;
		this.registroCargos = registroCargos;
	}

	/**
	 * Pases pendientes de la inscripción y convocatorias pendientes del alumno en la disciplina, sin bloquear.
	 * {@code codigoGrado}: el grado actual de la inscripción (una convocatoria con ese grado como siguiente ya se
	 * procesó).
	 */
	public Pendientes buscar(Long inscripcionId, Long alumnoId, String disciplina, String codigoGrado) {
		List<ProductoAlumno> pases = new ArrayList<>();
		for (ProductoAlumno cargo : em.createQuery("""
				SELECT pa FROM ProductoAlumno pa
				WHERE pa.alumnoDeporte.id = :inscripcion AND (pa.alumno IS NULL OR pa.alumno.id = :alumno)
				AND (pa.pagado IS NULL OR pa.pagado = FALSE) AND pa.canceladoEn IS NULL
				ORDER BY pa.id""", ProductoAlumno.class)
				.setParameter("inscripcion", inscripcionId).setParameter("alumno", alumnoId).getResultList()) {
			Long productoId = cargo.getProducto() == null ? null : cargo.getProducto().getId();
			if (productosCatalogo.buscarCodigoGradoConcedido(productoId, cargo.getConcepto()).isPresent()) {
				pases.add(cargo);
			}
		}
		List<AlumnoConvocatoria> convocatorias = em.createQuery("""
				SELECT ac FROM AlumnoConvocatoria ac JOIN ac.convocatoria c JOIN c.disciplina d
				LEFT JOIN ac.alumno al LEFT JOIN ac.alumnoDeporte ad
				WHERE (al.id = :alumno OR ad.id = :inscripcion) AND d.codigo = :disciplina
				AND c.fechaConvocatoria >= :hoy
				AND (ac.gradoSiguiente IS NULL OR ac.gradoSiguiente <> :grado)
				ORDER BY c.id, ac.id""", AlumnoConvocatoria.class)
				.setParameter("alumno", alumnoId).setParameter("inscripcion", inscripcionId)
				.setParameter("disciplina", disciplina).setParameter("hoy", java.sql.Date.valueOf(LocalDate.now(reloj)))
				.setParameter("grado", codigoGrado == null ? "" : codigoGrado).getResultList();
		return new Pendientes(pases, convocatorias);
	}

	/** 409 si algún pase o convocatoria no se puede deshacer (ver la clase); no cambia nada. */
	public void exigirQuitables(Pendientes pendientes) {
		for (ProductoAlumno pase : pendientes.pases()) {
			Long id = pase.getId();
			if (!cobroRepository.findByProductoAlumnoIdOrderByIdAsc(id).isEmpty()
					|| solicitudRepository.existeEncargoEnCursoDeCargo(id) || tieneConvocatoria(id)) {
				throw new IllegalStateException(PASE_NO_SE_BORRA);
			}
		}
		for (AlumnoConvocatoria inscripcion : pendientes.convocatorias()) {
			if (Boolean.TRUE.equals(inscripcion.getPagado())) {
				throw new IllegalStateException(CONVOCATORIA_NO_SE_QUITA);
			}
			ProductoAlumno cargo = inscripcion.getProductoAlumno();
			if (cargo != null && (Boolean.TRUE.equals(cargo.getPagado()) || cargo.getCanceladoEn() != null
					|| !cobroRepository.findByProductoAlumnoIdOrderByIdAsc(cargo.getId()).isEmpty()
					|| solicitudRepository.existeEncargoEnCursoDeCargo(cargo.getId()))) {
				throw new IllegalStateException(CONVOCATORIA_NO_SE_QUITA);
			}
		}
	}

	/**
	 * Dentro de la transacción y con la inscripción ya bloqueada: comprueba que los pendientes son EXACTAMENTE los
	 * confirmados (si no, 409 {@value #PASES_CAMBIARON}), bloquea sus cargos por id ascendente, los vuelve a leer y a
	 * comprobar (lo mismo, y {@link #exigirQuitables}). Devuelve los pendientes ya bloqueados, sin cambiar nada.
	 */
	public Pendientes bloquearYConfirmar(Long inscripcionId, Long alumnoId, String disciplina, String codigoGrado,
			Collection<Long> pasesConfirmados, Collection<Long> convocatoriasConfirmadas) {
		if (!TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("Quitar los pendientes de las rallas exige una transacción en curso.");
		}
		Pendientes antes = buscar(inscripcionId, alumnoId, disciplina, codigoGrado);
		exigirConfirmados(antes, pasesConfirmados, convocatoriasConfirmadas);
		TreeSet<Long> cargos = new TreeSet<>(antes.idsPases());
		antes.convocatorias().stream().map(AlumnoConvocatoria::getProductoAlumno).filter(java.util.Objects::nonNull)
				.forEach(cargo -> cargos.add(cargo.getId()));
		for (Long cargo : cargos) {
			if (cobros.bloquearCargo(cargo).isEmpty()) {
				throw new IllegalStateException(PASES_CAMBIARON);
			}
		}
		Pendientes ahora = buscar(inscripcionId, alumnoId, disciplina, codigoGrado);
		ahora.convocatorias().forEach(em::refresh);
		exigirConfirmados(ahora, pasesConfirmados, convocatoriasConfirmadas);
		exigirQuitables(ahora);
		return ahora;
	}

	/**
	 * Con los pendientes de {@link #bloquearYConfirmar}: borra los pases y saca al alumno de las convocatorias (y borra
	 * su cargo), cada cargo borrado en el registro ({@code CARGO_BORRADO}, con {@code ruta}). Devuelve lo borrado.
	 */
	public Quitados borrar(Pendientes pendientes, String ruta) {
		List<RallasDTOs.PasePendiente> pases = new ArrayList<>();
		for (ProductoAlumno pase : pendientes.pases()) {
			pases.add(pase(pase));
			borrarCargo(pase, ruta);
		}
		List<RallasDTOs.ConvocatoriaPendiente> convocatorias = new ArrayList<>();
		for (AlumnoConvocatoria inscripcion : pendientes.convocatorias()) {
			convocatorias.add(convocatoria(inscripcion));
			ProductoAlumno cargo = inscripcion.getProductoAlumno();
			// Primero la inscripción (apunta al cargo) y, ya borrada, el cargo. Fuera también de las colecciones ya
			// cargadas de la convocatoria, la inscripción del alumno y el alumno: dos de ellas propagan la persistencia y,
			// al vaciar la sesión, la volverían a guardar.
			quitarDeColeccionCargada(inscripcion.getConvocatoria() == null ? null
					: inscripcion.getConvocatoria().getAlumnosConvocatoria(), inscripcion);
			quitarDeColeccionCargada(inscripcion.getAlumnoDeporte() == null ? null
					: inscripcion.getAlumnoDeporte().getConvocatorias(), inscripcion);
			quitarDeColeccionCargada(inscripcion.getAlumno() == null ? null : inscripcion.getAlumno().getConvocatorias(),
					inscripcion);
			em.remove(inscripcion);
			em.flush();
			if (cargo != null) {
				borrarCargo(cargo, ruta);
			}
		}
		em.flush();
		return new Quitados(pases, convocatorias);
	}

	public RallasDTOs.PasePendiente pase(ProductoAlumno cargo) {
		return new RallasDTOs.PasePendiente(cargo.getId(), cargo.getConcepto(), importe(cargo));
	}

	public RallasDTOs.ConvocatoriaPendiente convocatoria(AlumnoConvocatoria inscripcion) {
		ProductoAlumno cargo = inscripcion.getProductoAlumno();
		return new RallasDTOs.ConvocatoriaPendiente(inscripcion.getConvocatoria().getId(),
				fecha(inscripcion.getConvocatoria().getFechaConvocatoria()), cargo == null ? null : cargo.getId(),
				cargo == null ? null : cargo.getConcepto(), cargo == null ? null : importe(cargo));
	}

	// ------------------------------------------------------------------ apoyo

	private void borrarCargo(ProductoAlumno cargo, String ruta) {
		// Sin cobros (comprobado con el cargo bloqueado): no anula nada; red de seguridad, como el DELETE del cargo.
		cobros.alBorrarCargo(cargo);
		productoAlumnoRepository.delete(cargo);
		registroCargos.registrar(RegistroAccionesCargos.Accion.CARGO_BORRADO, ruta, cargo.getSedeId(), cargo.getId());
	}

	private static void quitarDeColeccionCargada(Collection<AlumnoConvocatoria> coleccion, AlumnoConvocatoria inscripcion) {
		if (coleccion != null && org.hibernate.Hibernate.isInitialized(coleccion)) {
			coleccion.remove(inscripcion);
		}
	}

	private static void exigirConfirmados(Pendientes pendientes, Collection<Long> pases, Collection<Long> convocatorias) {
		if (!pendientes.idsPases().equals(conjunto(pases))
				|| !pendientes.idsConvocatorias().equals(conjunto(convocatorias))) {
			throw new IllegalStateException(PASES_CAMBIARON);
		}
	}

	/** Los ids confirmados como conjunto; un id nulo nunca coincide. */
	private static Set<Long> conjunto(Collection<Long> ids) {
		Set<Long> conjunto = new HashSet<>();
		if (ids != null) {
			for (Long id : ids) {
				conjunto.add(id == null ? Long.MIN_VALUE : id);
			}
		}
		return conjunto;
	}

	private boolean tieneConvocatoria(Long cargoId) {
		return em.createQuery("SELECT COUNT(ac) FROM AlumnoConvocatoria ac WHERE ac.productoAlumno.id = :cargo", Long.class)
				.setParameter("cargo", cargoId).getSingleResult() > 0;
	}

	private static java.math.BigDecimal importe(ProductoAlumno cargo) {
		return cargo.getPrecio() == null ? null : ImporteCargo.de(cargo);
	}

	private static LocalDate fecha(Date fecha) {
		if (fecha == null) {
			return null;
		}
		if (fecha instanceof java.sql.Date dia) {
			return dia.toLocalDate();
		}
		return fecha.toInstant().atZone(ValidacionRelojConfig.ZONA).toLocalDate();
	}
}
