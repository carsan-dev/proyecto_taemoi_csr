package com.taemoi.project.services;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.acceso.FiltroSedesTesoreria;
import com.taemoi.project.acceso.GestionPorSede;
import com.taemoi.project.dtos.response.PendienteAlumnoDTO;
import com.taemoi.project.dtos.response.PendienteVencidoDTO;
import com.taemoi.project.entities.TipoProducto;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.PendienteVencidoRepository;
import com.taemoi.project.repositories.PendienteVencidoRepository.CargoCandidato;

/**
 * Pieza 8b-2: «Pendiente de cobro · vencido» del Inicio nuevo (decisiones 242 y 267). Servicio aparte: no toca los
 * métodos de {@code TesoreriaServiceImpl} que vigila {@code MoiskimdoTesoreriaIdenticaIntegrationTest}.
 *
 * <p>Regla (decisión 267, «hoy» con el {@code Clock} de {@code RelojConfig}, Europe/Madrid; fijo en los tests):
 * <ul>
 * <li>Solo cargos no pagados ni cancelados de alumnos activos, como la tesorería de hoy, y solo de las categorías
 * {@code MENSUALIDAD} y {@code EXAMEN} de la tesorería ({@link #categoria}).</li>
 * <li>Mensualidad: su mes es el del concepto (como lo lee la tesorería) o, si no lo trae, el de la asignación. Vencida
 * si su mes es anterior al mes en curso, o si es el mes en curso y hoy es posterior al día
 * {@link #DIA_LIMITE_MENSUALIDAD}.</li>
 * <li>Derecho de examen: vencido si la fecha de su convocatoria es hoy o anterior; sin convocatoria enlazada, la fecha de
 * asignación.</li>
 * </ul>
 * La diferencia con el aviso de hoy del panel («Cobros pendientes del mes»): aquel cuenta todo lo no pagado del mes en
 * curso; este, lo vencido de cualquier mes.
 */
@Service
public class PendienteVencidoService {

	/**
	 * Último día para pagar la mensualidad del mes: el 5, según las normas de Moiskimdo («del día 1 al 5 del mes
	 * corriente», V3 y V17). Pasará a ser un ajuste por escuela en la E9.
	 */
	public static final int DIA_LIMITE_MENSUALIDAD = 5;

	static final String MENSUALIDAD = "MENSUALIDAD";
	static final String EXAMEN = "EXAMEN";
	private static final int MAS_ANTIGUOS = 5;
	/** Pieza 8b-3a: una página del listado de alumnos. */
	public static final int MAX_ALUMNOS = 25;

	/** Como {@code TesoreriaServiceImpl}: meses en mayúsculas y año de cuatro cifras en el concepto. */
	private static final String[] MESES_ES = { "ENERO", "FEBRERO", "MARZO", "ABRIL", "MAYO", "JUNIO", "JULIO",
			"AGOSTO", "SEPTIEMBRE", "OCTUBRE", "NOVIEMBRE", "DICIEMBRE" };
	private static final Pattern ANO_PATTERN = Pattern.compile("\\b(20\\d{2})\\b");
	private static final Pattern MATRICULA_PATTERN = Pattern.compile("\\s*MATR[IÍ]CULA\\b");

	private final PendienteVencidoRepository repositorio;
	private final GestionPorSede gestionPorSede;
	private final ComportamientoLegadoService comportamientoLegado;
	private final Clock reloj;

	public PendienteVencidoService(PendienteVencidoRepository repositorio, GestionPorSede gestionPorSede,
			ComportamientoLegadoService comportamientoLegado, Clock reloj) {
		this.repositorio = repositorio;
		this.gestionPorSede = gestionPorSede;
		this.comportamientoLegado = comportamientoLegado;
		this.reloj = reloj;
	}

	@Transactional(readOnly = true)
	public PendienteVencidoDTO obtener() {
		Long organizacion = organizacionActiva();
		// La gestión ve toda la escuela, como /resumen sin sede (el responsable de sede no entra hasta la E9).
		FiltroSedesTesoreria sedes = gestionPorSede.filtroTesoreria(null);
		return resumir(vencidos(repositorio.candidatos(organizacion, sedes.todas(), sedes.parametro())));
	}

	/**
	 * Pieza 8b-3a: lo vencido de cada alumno indicado (como mucho {@link #MAX_ALUMNOS}), con la misma regla y el mismo
	 * universo que {@link #obtener()}: la suma por alumno coincide con su parte del total. Solo los alumnos de la
	 * escuela activa con algo vencido; los ids ajenos o sin deuda no salen. Orden: el de los ids pedidos.
	 */
	@Transactional(readOnly = true)
	public List<PendienteAlumnoDTO> porAlumno(List<Long> alumnoIds) {
		if (alumnoIds == null || alumnoIds.isEmpty() || alumnoIds.size() > MAX_ALUMNOS) {
			throw new IllegalArgumentException("Indica entre 1 y " + MAX_ALUMNOS + " alumnos.");
		}
		Long organizacion = organizacionActiva();
		FiltroSedesTesoreria sedes = gestionPorSede.filtroTesoreria(null);
		Set<Long> pedidos = new LinkedHashSet<>(alumnoIds);
		Map<Long, List<Vencido>> porAlumno = new HashMap<>();
		for (Vencido v : vencidos(repositorio.candidatosDeAlumnos(organizacion, sedes.todas(), sedes.parametro(),
				pedidos))) {
			porAlumno.computeIfAbsent(v.cargo().getAlumnoId(), k -> new ArrayList<>()).add(v);
		}
		List<PendienteAlumnoDTO> resultado = new ArrayList<>();
		for (Long alumno : pedidos) {
			List<Vencido> suyos = porAlumno.get(alumno);
			if (suyos == null) {
				continue;
			}
			suyos.sort(Comparator.comparing(Vencido::desde).thenComparing(v -> v.cargo().getId()));
			BigDecimal importe = BigDecimal.ZERO;
			List<PendienteAlumnoDTO.Cargo> detalle = new ArrayList<>();
			for (Vencido v : suyos) {
				importe = importe.add(importe(v.cargo()));
				detalle.add(new PendienteAlumnoDTO.Cargo(v.cargo().getId(), v.cargo().getConcepto(), importe(v.cargo()),
						v.desde().toString()));
			}
			resultado.add(new PendienteAlumnoDTO(alumno, importe, detalle.size(), detalle));
		}
		return resultado;
	}

	/**
	 * Pieza 8b-3p: los ids de los cargos vencidos hoy de esos alumnos, con la misma regla y el mismo universo que
	 * {@link #obtener()}, para marcarlos en «Por cobrar» del profesor. De todas las sedes: quien llama ya ha limitado los
	 * alumnos a los que puede ver. Solo los de la escuela activa (los joins llevan {@code organizacion_id}).
	 */
	@Transactional(readOnly = true)
	public Set<Long> cargosVencidosDeAlumnos(Collection<Long> alumnoIds) {
		if (alumnoIds.isEmpty()) {
			return Set.of();
		}
		FiltroSedesTesoreria sedes = FiltroSedesTesoreria.TODAS;
		Set<Long> cargos = new HashSet<>();
		for (Vencido v : vencidos(repositorio.candidatosDeAlumnos(organizacionActiva(), sedes.todas(), sedes.parametro(),
				alumnoIds))) {
			cargos.add(v.cargo().getId());
		}
		return cargos;
	}

	private static Long organizacionActiva() {
		Long organizacion = TenantContext.get();
		if (organizacion == null) {
			throw new AccessDeniedException("Acceso denegado.");
		}
		return organizacion;
	}

	/** La regla común de {@link #obtener()}, {@link #porAlumno} y {@link #cargosVencidosDeAlumnos}: los candidatos vencidos hoy. */
	private List<Vencido> vencidos(List<CargoCandidato> candidatos) {
		LocalDate hoy = LocalDate.now(reloj);
		boolean legado = comportamientoLegado.activo();
		List<Vencido> vencidos = new ArrayList<>();
		for (CargoCandidato c : candidatos) {
			Vencido v = vencido(c, hoy, legado);
			if (v != null) {
				vencidos.add(v);
			}
		}
		return vencidos;
	}

	// ------------------------------------------------------------------ regla

	private record Vencido(CargoCandidato cargo, String tipo, YearMonth mes, LocalDate desde) {
	}

	private static Vencido vencido(CargoCandidato c, LocalDate hoy, boolean legado) {
		String tipo = categoria(c.getConcepto(), tipoDeCatalogo(c, legado));
		LocalDate fechaCargo = c.getFechaAsignacion() != null ? c.getFechaAsignacion() : c.getFechaPago();
		if (MENSUALIDAD.equals(tipo)) {
			YearMonth mes = mesDeMensualidad(c.getConcepto(), fechaCargo);
			if (mes == null) {
				return null;
			}
			LocalDate desde = mes.atDay(DIA_LIMITE_MENSUALIDAD + 1);
			return hoy.isBefore(desde) ? null : new Vencido(c, tipo, mes, desde);
		}
		if (EXAMEN.equals(tipo)) {
			LocalDate desde = c.getFechaConvocatoria() != null ? c.getFechaConvocatoria() : fechaCargo;
			if (desde == null || hoy.isBefore(desde)) {
				return null;
			}
			return new Vencido(c, tipo, YearMonth.from(desde), desde);
		}
		return null;
	}

	private static TipoProducto tipoDeCatalogo(CargoCandidato c, boolean legado) {
		TipoProducto tipo = null;
		if (c.getProductoTipo() != null) {
			try {
				tipo = TipoProducto.valueOf(c.getProductoTipo());
			} catch (IllegalArgumentException ex) {
				tipo = null;
			}
		}
		return CatalogoLegadoTaemoi.tipoDeCatalogo(c.getProductoCodigo(), tipo, legado);
	}

	/**
	 * Categoría de la tesorería, copiada de {@code TesoreriaServiceImpl#obtenerCategoria} (sin tocar el original, que
	 * vigila el dorado): la regla por concepto y, si no lo reconoce, el tipo del catálogo. Lo vigila
	 * {@code PendienteVencidoIntegrationTest#pendienteVencido_coincideConMovimientosNoPagadosVencidos}.
	 */
	static String categoria(String concepto, TipoProducto tipo) {
		String c = concepto != null ? concepto.toUpperCase(Locale.ROOT) : "";
		String porConcepto;
		if (c.startsWith("MENSUALIDAD")) {
			porConcepto = MENSUALIDAD;
		} else if (c.startsWith("TARIFA COMPETIDOR")) {
			porConcepto = "TARIFA_COMPETIDOR";
		} else if (MATRICULA_PATTERN.matcher(c).lookingAt()) {
			porConcepto = "MATRICULA";
		} else if (c.contains("LICENCIA")) {
			porConcepto = "LICENCIA";
		} else if (c.startsWith("RESERVA DE PLAZA")) {
			porConcepto = "RESERVA_PLAZA";
		} else if (c.contains("EXAMEN") || c.contains("RECOMPENSA")) {
			porConcepto = EXAMEN;
		} else {
			porConcepto = "OTRO";
		}
		if (!"OTRO".equals(porConcepto) || tipo == null) {
			return porConcepto;
		}
		return switch (tipo) {
		case MENSUALIDAD -> MENSUALIDAD;
		case COMPETIDOR -> "TARIFA_COMPETIDOR";
		case LICENCIA -> "LICENCIA";
		case RESERVA_PLAZA -> "RESERVA_PLAZA";
		case EXAMEN, RECOMPENSA -> EXAMEN;
		case OTRO -> "OTRO";
		};
	}

	/**
	 * Periodo de la mensualidad como {@code TesoreriaServiceImpl#obtenerPeriodoMovimiento}: el año y el mes del concepto
	 * y, el que falte, el de la fecha del cargo (un concepto con año y sin mes toma su año y el mes de la fecha).
	 */
	static YearMonth mesDeMensualidad(String concepto, LocalDate fechaCargo) {
		String c = concepto != null ? concepto.toUpperCase(Locale.ROOT) : "";
		Integer mes = null;
		for (int i = 0; i < MESES_ES.length; i++) {
			if (c.contains(MESES_ES[i])) {
				mes = i + 1;
				break;
			}
		}
		Matcher ano = ANO_PATTERN.matcher(c);
		Integer anoConcepto = ano.find() ? Integer.parseInt(ano.group(1)) : null;
		Integer anio = anoConcepto != null ? anoConcepto : (fechaCargo != null ? fechaCargo.getYear() : null);
		Integer mesFinal = mes != null ? mes : (fechaCargo != null ? fechaCargo.getMonthValue() : null);
		return anio != null && mesFinal != null ? YearMonth.of(anio, mesFinal) : null;
	}

	// ------------------------------------------------------------------ resumen

	private static PendienteVencidoDTO resumir(List<Vencido> vencidos) {
		BigDecimal total = BigDecimal.ZERO;
		Set<Long> alumnos = new HashSet<>();
		Map<String, Acumulado> porTipo = new LinkedHashMap<>();
		porTipo.put(MENSUALIDAD, new Acumulado());
		porTipo.put(EXAMEN, new Acumulado());
		Map<YearMonth, Acumulado> porMes = new TreeMap<>();
		Map<Long, Acumulado> porSede = new TreeMap<>(Comparator.nullsLast(Comparator.naturalOrder()));
		Map<Long, String> nombresSede = new LinkedHashMap<>();
		for (Vencido v : vencidos) {
			BigDecimal importe = importe(v.cargo());
			total = total.add(importe);
			if (v.cargo().getAlumnoId() != null) {
				alumnos.add(v.cargo().getAlumnoId());
			}
			porTipo.get(v.tipo()).sumar(importe);
			porMes.computeIfAbsent(v.mes(), k -> new Acumulado()).sumar(importe);
			porSede.computeIfAbsent(v.cargo().getSedeId(), k -> new Acumulado()).sumar(importe);
			nombresSede.putIfAbsent(v.cargo().getSedeId(), v.cargo().getSedeNombre());
		}

		List<PendienteVencidoDTO.PorTipo> tipos = new ArrayList<>();
		porTipo.forEach((tipo, a) -> {
			if (a.cargos > 0) {
				tipos.add(new PendienteVencidoDTO.PorTipo(tipo, a.importe, a.cargos));
			}
		});
		List<PendienteVencidoDTO.PorMes> meses = new ArrayList<>();
		porMes.forEach((mes, a) -> meses.add(new PendienteVencidoDTO.PorMes(mes.toString(), a.importe, a.cargos)));
		List<PendienteVencidoDTO.PorSede> sedes = new ArrayList<>();
		porSede.forEach((sede, a) -> sedes.add(new PendienteVencidoDTO.PorSede(sede, nombresSede.get(sede), a.importe,
				a.cargos)));

		List<PendienteVencidoDTO.Cargo> masAntiguos = vencidos.stream()
				.sorted(Comparator.comparing(Vencido::desde).thenComparing(v -> v.cargo().getId()))
				.limit(MAS_ANTIGUOS)
				.map(PendienteVencidoService::cargo)
				.toList();
		return new PendienteVencidoDTO(total, vencidos.size(), alumnos.size(), DIA_LIMITE_MENSUALIDAD, tipos, meses,
				sedes, masAntiguos);
	}

	private static PendienteVencidoDTO.Cargo cargo(Vencido v) {
		CargoCandidato c = v.cargo();
		String nombre = String.join(" ", List.of(Objects.toString(c.getAlumnoNombre(), ""),
				Objects.toString(c.getAlumnoApellidos(), ""))).trim();
		return new PendienteVencidoDTO.Cargo(c.getId(), c.getAlumnoId(), nombre, c.getConcepto(), v.tipo(), importe(c),
				v.mes().toString(), v.desde().toString(), c.getSedeId());
	}

	private static BigDecimal importe(CargoCandidato c) {
		return c.getImporte() != null ? c.getImporte() : BigDecimal.ZERO;
	}

	private static final class Acumulado {
		BigDecimal importe = BigDecimal.ZERO;
		long cargos;

		void sumar(BigDecimal valor) {
			importe = importe.add(valor);
			cargos++;
		}
	}
}
