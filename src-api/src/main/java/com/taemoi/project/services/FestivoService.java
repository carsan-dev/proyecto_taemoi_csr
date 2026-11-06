package com.taemoi.project.services;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.acceso.GestionPorSede;
import com.taemoi.project.dtos.request.FestivoRequest;
import com.taemoi.project.dtos.response.FestivoDTOs;
import com.taemoi.project.entities.ClaseAsistencia;
import com.taemoi.project.entities.Festivo;
import com.taemoi.project.entities.FestivoSede;
import com.taemoi.project.entities.Sede;
import com.taemoi.project.entities.TipoFestivo;
import com.taemoi.project.organizacion.AlcanceSesion;
import com.taemoi.project.repositories.AsistenciaAlumnoRepository;
import com.taemoi.project.repositories.ClaseAsistenciaRepository;
import com.taemoi.project.repositories.FestivoRepository;
import com.taemoi.project.repositories.FestivoSedeRepository;
import com.taemoi.project.repositories.SedeRepository;
import com.taemoi.project.utils.DiaSemanaUtils;

import jakarta.persistence.EntityManager;

/**
 * Festivos por sede (decisiones 455-463, fase3-pasar-lista.md, «Motivos y festivos»): días sin clase en una o varias
 * sedes. La regla única «¿es festivo en esta sede esta fecha?» es {@link #festivosEn}; la usan pasar lista
 * ({@link AsistenciaService}) y el PDF de asistencia marcado. Una clase festiva que ya tiene lista guardada sigue normal:
 * manda la lista, y eso lo decide quien llama (aquí no se mira ninguna lista salvo en {@link #impacto}).
 *
 * <p>Gestionan los festivos el propietario y el gestor (todas las sedes de la escuela) y el responsable de sede, solo
 * los que tienen TODAS sus sedes entre las suyas ({@link GestionPorSede#restriccion()}); el resto de los que tocan una
 * sede suya los ve sin poder cambiarlos. El profesor no entra (regla de URL). Errores: 400 datos, 403 sede de otra escuela
 * o fuera de su alcance, o festivo que no puede cambiar, 404 festivo de otra escuela o inexistente.
 *
 * <p>Escuela: las dos tablas se acotan por {@code @TenantId}. «Hoy» sale del {@link Clock} de la aplicación
 * (Europe/Madrid). Las lecturas no hacen consultas por festivo ni por sede.
 */
@Service
public class FestivoService {

	/** Un periodo, como mucho, de 366 días contando los dos extremos. */
	public static final int MAX_DIAS_RANGO = 366;
	/** Filas de {@code conListaDetalle} y de {@code clasesDetalle} en el impacto. */
	public static final int MAX_DETALLE_IMPACTO = 20;
	public static final int MAX_NOMBRE = 60;

	public static final String TIPO_NO_VALIDO = "Elige si el festivo es un día, un periodo o cada año.";
	public static final String FECHA_OBLIGATORIA = "Indica la fecha.";
	public static final String FECHAS_OBLIGATORIAS = "Indica la fecha de inicio y la de fin.";
	public static final String FECHA_NO_VALIDA = "La fecha no es válida.";
	public static final String DIA_CON_OTRO_FIN = "Un festivo de un día empieza y acaba el mismo día.";
	public static final String RANGO_AL_REVES = "La fecha de fin debe ser posterior a la de inicio.";
	public static final String RANGO_DEMASIADO_LARGO = "Un periodo no puede pasar de 366 días.";
	public static final String DIA_Y_MES_OBLIGATORIOS = "Indica el día y el mes.";
	public static final String MES_NO_VALIDO = "El mes no es válido.";
	public static final String DIA_NO_VALIDO = "Ese día no existe en ese mes.";
	public static final String VEINTINUEVE_DE_FEBRERO = "Elige otro día: el 29 de febrero no existe todos los años.";
	public static final String NOMBRE_DEMASIADO_LARGO = "El nombre no puede pasar de 60 caracteres.";
	public static final String SEDES_OBLIGATORIAS = "Elige al menos una sede.";
	public static final String FESTIVO_NO_EXISTE = "El festivo no existe.";

	/** Una sede y una fecha. */
	public record SedeYFecha(Long sedeId, LocalDate fecha) {
	}

	/**
	 * El festivo que cae en una sede y fecha. {@code fechaDesde} y {@code fechaHasta}: las suyas (ANUAL, la fecha de ese
	 * día en las dos). {@code sedes}: cuántas sedes cubre. {@code deUnDiaYUnaSede}: es de tipo DIA y solo tiene esa sede
	 * (quitarlo no afecta a ningún otro día ni sede).
	 */
	public record DiaFestivo(Long id, String nombre, TipoFestivo tipo, LocalDate fechaDesde, LocalDate fechaHasta,
			int sedes, boolean deUnDiaYUnaSede) {
	}

	/** Forma validada de un cuerpo: fechas para DIA y RANGO; mes y día para ANUAL. */
	private record Forma(TipoFestivo tipo, LocalDate desde, LocalDate hasta, Integer mes, Integer dia, String nombre) {
	}

	private final FestivoRepository festivos;
	private final FestivoSedeRepository festivoSedes;
	private final SedeRepository sedes;
	private final ClaseAsistenciaRepository clases;
	private final AsistenciaAlumnoRepository filas;
	private final GestionPorSede gestionPorSede;
	private final EntityManager em;
	private final Clock reloj;

	public FestivoService(FestivoRepository festivos, FestivoSedeRepository festivoSedes, SedeRepository sedes,
			ClaseAsistenciaRepository clases, AsistenciaAlumnoRepository filas, GestionPorSede gestionPorSede,
			EntityManager em, Clock reloj) {
		this.festivos = festivos;
		this.festivoSedes = festivoSedes;
		this.sedes = sedes;
		this.clases = clases;
		this.filas = filas;
		this.gestionPorSede = gestionPorSede;
		this.em = em;
		this.reloj = reloj;
	}

	// ------------------------------------------------------------------ regla única

	/**
	 * Los festivos de esas sedes entre {@code desde} y {@code hasta}, ambas incluidas, por sede y fecha. Dos consultas en
	 * total (festivos que pueden caer en el intervalo y el número de sedes de cada uno), el resto en memoria. Si en la
	 * misma sede y fecha caen varios, gana el más concreto (DIA, luego RANGO, luego ANUAL) y, a igualdad, el de id menor.
	 * Sin sedes o con el intervalo al revés, vacío.
	 */
	@Transactional(readOnly = true)
	public Map<SedeYFecha, DiaFestivo> festivosEn(Collection<Long> sedeIds, LocalDate desde, LocalDate hasta) {
		return festivosEn(sedeIds, desde, hasta, null);
	}

	private Map<SedeYFecha, DiaFestivo> festivosEn(Collection<Long> sedeIds, LocalDate desde, LocalDate hasta,
			Long excluirFestivoId) {
		Set<Long> ids = sedeIds == null ? Set.of() : new HashSet<>(sedeIds);
		ids.remove(null);
		if (ids.isEmpty() || desde == null || hasta == null || hasta.isBefore(desde)) {
			return Map.of();
		}
		List<Object[]> filas = festivos.deLasSedesEntre(ids, desde, hasta, TipoFestivo.ANUAL);
		Map<Long, Festivo> porId = new HashMap<>();
		for (Object[] fila : filas) {
			Festivo festivo = (Festivo) fila[1];
			if (!Objects.equals(festivo.getId(), excluirFestivoId)) {
				porId.put(festivo.getId(), festivo);
			}
		}
		if (porId.isEmpty()) {
			return Map.of();
		}
		Map<Long, Integer> numeroDeSedes = new HashMap<>();
		for (FestivoSede fila : festivoSedes.findByFestivoIdIn(porId.keySet())) {
			numeroDeSedes.merge(fila.getFestivoId(), 1, Integer::sum);
		}
		Map<SedeYFecha, Festivo> elegido = new HashMap<>();
		for (Object[] fila : filas) {
			Long sede = (Long) fila[0];
			Festivo festivo = porId.get(((Festivo) fila[1]).getId());
			if (festivo == null) {
				continue;
			}
			for (LocalDate fecha : fechasEntre(festivo, desde, hasta)) {
				elegido.merge(new SedeYFecha(sede, fecha), festivo, FestivoService::masConcreto);
			}
		}
		Map<SedeYFecha, DiaFestivo> resultado = new HashMap<>();
		elegido.forEach((clave, festivo) -> {
			boolean anual = festivo.getTipo() == TipoFestivo.ANUAL;
			int numero = numeroDeSedes.getOrDefault(festivo.getId(), 0);
			resultado.put(clave, new DiaFestivo(festivo.getId(), festivo.getNombre(), festivo.getTipo(),
					anual ? clave.fecha() : festivo.getFechaDesde(), anual ? clave.fecha() : festivo.getFechaHasta(), numero,
					festivo.getTipo() == TipoFestivo.DIA && numero == 1));
		});
		return resultado;
	}

	private static Festivo masConcreto(Festivo a, Festivo b) {
		int porTipo = Integer.compare(a.getTipo().ordinal(), b.getTipo().ordinal());
		if (porTipo != 0) {
			return porTipo < 0 ? a : b;
		}
		return a.getId() <= b.getId() ? a : b;
	}

	/** Fechas del festivo dentro del intervalo, ambas incluidas. */
	private static List<LocalDate> fechasEntre(Festivo festivo, LocalDate desde, LocalDate hasta) {
		List<LocalDate> fechas = new ArrayList<>();
		if (festivo.getTipo() == TipoFestivo.ANUAL) {
			if (festivo.getMes() == null || festivo.getDia() == null) {
				return fechas;
			}
			for (int anio = desde.getYear(); anio <= hasta.getYear(); anio++) {
				LocalDate fecha = enAnio(festivo.getMes(), festivo.getDia(), anio);
				if (fecha != null && !fecha.isBefore(desde) && !fecha.isAfter(hasta)) {
					fechas.add(fecha);
				}
			}
			return fechas;
		}
		if (festivo.getFechaDesde() == null || festivo.getFechaHasta() == null) {
			return fechas;
		}
		LocalDate inicio = festivo.getFechaDesde().isAfter(desde) ? festivo.getFechaDesde() : desde;
		LocalDate fin = festivo.getFechaHasta().isBefore(hasta) ? festivo.getFechaHasta() : hasta;
		for (LocalDate fecha = inicio; !fecha.isAfter(fin); fecha = fecha.plusDays(1)) {
			fechas.add(fecha);
		}
		return fechas;
	}

	/** El día y mes en ese año; {@code null} si no existe (no debería: el 29 de febrero se rechaza al guardar). */
	private static LocalDate enAnio(int mes, int dia, int anio) {
		try {
			return LocalDate.of(anio, mes, dia);
		} catch (java.time.DateTimeException e) {
			return null;
		}
	}

	/** Próxima aparición de un día y mes desde {@code hoy}, incluido. */
	static LocalDate proximaAparicion(int mes, int dia, LocalDate hoy) {
		LocalDate fecha = enAnio(mes, dia, hoy.getYear());
		for (int anio = hoy.getYear(); fecha == null || fecha.isBefore(hoy); anio++) {
			fecha = enAnio(mes, dia, anio + 1);
		}
		return fecha;
	}

	// ------------------------------------------------------------------ lectura

	/**
	 * {@code GET /api/festivos}: los de hoy en adelante y los anuales ({@code pasados = false}) o los ya pasados, por
	 * {@code proximaFecha} y id. Con {@code sedeId}, solo los de esa sede (de la escuela, 400; del responsable, 403). El
	 * responsable, solo los que tocan alguna de sus sedes. Tres consultas.
	 */
	@Transactional(readOnly = true)
	public List<FestivoDTOs.Festivo> listar(Long sedeId, boolean pasados) {
		gestionPorSede.exigirSedeDeFiltro(sedeId);
		Optional<AlcanceSesion> restriccion = gestionPorSede.restriccion();
		LocalDate hoy = LocalDate.now(reloj);
		List<Festivo> encontrados = pasados ? festivos.pasados(hoy, TipoFestivo.ANUAL)
				: festivos.deHoyEnAdelante(hoy, TipoFestivo.ANUAL);
		if (encontrados.isEmpty()) {
			return List.of();
		}
		Map<Long, Set<Long>> sedesDe = sedesDe(encontrados.stream().map(Festivo::getId).toList());
		Map<Long, Sede> sedesDeLaEscuela = sedesDeLaEscuela();
		List<FestivoDTOs.Festivo> resultado = new ArrayList<>();
		for (Festivo festivo : encontrados) {
			Set<Long> suyas = sedesDe.getOrDefault(festivo.getId(), Set.of());
			if (sedeId != null && !suyas.contains(sedeId)) {
				continue;
			}
			if (restriccion.isPresent() && suyas.stream().noneMatch(restriccion.get().sedes()::contains)) {
				continue;
			}
			resultado.add(dto(festivo, suyas, sedesDeLaEscuela, restriccion, hoy));
		}
		resultado.sort(Comparator.comparing(FestivoDTOs.Festivo::proximaFecha).thenComparing(FestivoDTOs.Festivo::id));
		return resultado;
	}

	// ------------------------------------------------------------------ escritura

	/** {@code POST /api/festivos}: 400 datos; 403 sede de otra escuela o fuera del alcance del responsable. */
	@Transactional
	public FestivoDTOs.Festivo crear(FestivoRequest cuerpo) {
		Forma forma = forma(cuerpo);
		Set<Long> sedeIds = exigirSedes(cuerpo.sedeIds());
		LocalDateTime ahora = LocalDateTime.now(reloj);
		Festivo festivo = new Festivo(CobroService.usuarioActualId(), ahora);
		festivo.definir(forma.tipo(), forma.desde(), forma.hasta(), forma.mes(), forma.dia(), forma.nombre());
		festivos.save(festivo);
		for (Long sede : sedeIds) {
			em.persist(new FestivoSede(festivo.getId(), sede));
		}
		return dto(festivo, sedeIds, sedesDeLaEscuela(), gestionPorSede.restriccion(), ahora.toLocalDate());
	}

	/**
	 * {@code PUT /api/festivos/{id}}: reemplaza el festivo y sus sedes. Por orden: 404 festivo de otra escuela o
	 * inexistente; 403 si el responsable no puede cambiarlo; 400 datos; 403 sedes nuevas fuera de su alcance. La fila del
	 * festivo se bloquea antes de leer sus sedes ({@code FOR UPDATE}, READ COMMITTED): dos cambios a la vez se ponen en
	 * fila.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public FestivoDTOs.Festivo editar(Long id, FestivoRequest cuerpo) {
		Festivo festivo = bloquear(id);
		List<FestivoSede> actuales = festivoSedes.findByFestivoIdIn(List.of(festivo.getId()));
		exigirEditable(actuales.stream().map(FestivoSede::getSedeId).toList());
		Forma forma = forma(cuerpo);
		Set<Long> sedeIds = exigirSedes(cuerpo.sedeIds());
		LocalDateTime ahora = LocalDateTime.now(reloj);
		festivo.definir(forma.tipo(), forma.desde(), forma.hasta(), forma.mes(), forma.dia(), forma.nombre());
		festivo.marcarModificado(CobroService.usuarioActualId(), ahora);
		Set<Long> yaEstaban = new HashSet<>();
		for (FestivoSede actual : actuales) {
			if (sedeIds.contains(actual.getSedeId())) {
				yaEstaban.add(actual.getSedeId());
			} else {
				em.remove(actual);
			}
		}
		for (Long sede : sedeIds) {
			if (!yaEstaban.contains(sede)) {
				em.persist(new FestivoSede(festivo.getId(), sede));
			}
		}
		return dto(festivo, sedeIds, sedesDeLaEscuela(), gestionPorSede.restriccion(), ahora.toLocalDate());
	}

	/** {@code DELETE /api/festivos/{id}}: 404 de otra escuela o inexistente; 403 si el responsable no puede quitarlo. */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void borrar(Long id) {
		Festivo festivo = bloquear(id);
		List<FestivoSede> actuales = festivoSedes.findByFestivoIdIn(List.of(festivo.getId()));
		exigirEditable(actuales.stream().map(FestivoSede::getSedeId).toList());
		for (FestivoSede actual : actuales) {
			em.remove(actual);
		}
		festivos.delete(festivo);
	}

	// ------------------------------------------------------------------ impacto

	/**
	 * {@code POST /api/festivos/impacto}: lo que pasaría con ese cuerpo, sin guardar nada. Mismas validaciones y permisos
	 * que el alta; {@code excluirFestivoId} (el que se edita) debe existir en la escuela (404). Cuenta las clases como
	 * {@code AsistenciaService#clases}: cada turno de los grupos de esas sedes, en cada fecha del festivo que cae en su
	 * día (ANUAL: solo su próxima aparición). No cuentan las que ya son festivo por OTRO festivo. {@code conLista}: las que
	 * ya tienen lista guardada (con su grupo), que siguen normales; {@code clasesDetalle}, las primeras clases afectadas
	 * por fecha y hora, con sus cifras si tienen lista. Cuatro consultas más las de las sedes.
	 */
	@Transactional(readOnly = true)
	public FestivoDTOs.Impacto impacto(FestivoRequest cuerpo) {
		if (cuerpo != null && cuerpo.excluirFestivoId() != null
				&& festivos.buscarEnEscuelaActiva(cuerpo.excluirFestivoId()).isEmpty()) {
			throw new NoSuchElementException(FESTIVO_NO_EXISTE);
		}
		Forma forma = forma(cuerpo);
		Set<Long> sedeIds = exigirSedes(cuerpo.sedeIds());
		LocalDate hoy = LocalDate.now(reloj);
		List<LocalDate> fechas = fechasDe(forma, hoy);
		LocalDate desde = fechas.get(0);
		LocalDate hasta = fechas.get(fechas.size() - 1);

		List<Object[]> turnos = em.createQuery("""
				SELECT t.id, t.diaSemana, t.horaInicio, g.id, g.nombre, g.sedeId, t.horaFin FROM Turno t JOIN t.grupo g
				WHERE g.sedeId IN :sedes""", Object[].class).setParameter("sedes", sedeIds).getResultList();
		Map<Long, Long> grupoDelTurno = new HashMap<>();
		for (Object[] turno : turnos) {
			grupoDelTurno.put((Long) turno[0], (Long) turno[3]);
		}
		Map<String, ClaseAsistencia> conListaGuardada = new HashMap<>();
		if (!grupoDelTurno.isEmpty()) {
			for (ClaseAsistencia clase : clases.findByTurnoIdInAndFechaBetween(grupoDelTurno.keySet(), desde, hasta)) {
				if (clase.getTurnoId() != null && Objects.equals(clase.getGrupoId(), grupoDelTurno.get(clase.getTurnoId()))) {
					conListaGuardada.put(clase.getTurnoId() + "|" + clase.getFecha(), clase);
				}
			}
		}
		// [filas, presentes] de las listas guardadas, en una consulta (como AsistenciaService#clases).
		Map<Long, long[]> cuentas = new HashMap<>();
		if (!conListaGuardada.isEmpty()) {
			for (Object[] fila : filas.contarPorClase(
					conListaGuardada.values().stream().map(ClaseAsistencia::getId).toList())) {
				cuentas.put((Long) fila[0], new long[] { ((Number) fila[1]).longValue(), ((Number) fila[2]).longValue() });
			}
		}
		Map<SedeYFecha, DiaFestivo> otros = festivosEn(sedeIds, desde, hasta,
				cuerpo.excluirFestivoId());
		Map<Long, Sede> sedesDeLaEscuela = sedesDeLaEscuela();

		long total = 0;
		List<FestivoDTOs.ClaseConLista> detalle = new ArrayList<>();
		List<FestivoDTOs.ClaseAfectada> afectadas = new ArrayList<>();
		for (Object[] turno : turnos) {
			DayOfWeek dia = diaDeLaSemana((String) turno[1]);
			if (dia == null) {
				continue;
			}
			Long sede = (Long) turno[5];
			for (LocalDate fecha : fechas) {
				if (fecha.getDayOfWeek() != dia || otros.containsKey(new SedeYFecha(sede, fecha))) {
					continue;
				}
				total++;
				Sede s = sedesDeLaEscuela.get(sede);
				String nombreSede = s == null ? null : s.getNombre();
				ClaseAsistencia guardada = conListaGuardada.get(turno[0] + "|" + fecha);
				Long presentes = null;
				Long filasDeLaLista = null;
				if (guardada != null) {
					long[] cuenta = cuentas.getOrDefault(guardada.getId(), new long[2]);
					filasDeLaLista = cuenta[0];
					presentes = cuenta[1];
					detalle.add(new FestivoDTOs.ClaseConLista(sede, nombreSede, fecha, (String) turno[2],
							(String) turno[4]));
				}
				afectadas.add(new FestivoDTOs.ClaseAfectada(sede, nombreSede, fecha, (String) turno[2], (String) turno[6],
						(String) turno[4], guardada != null, presentes, filasDeLaLista));
			}
		}
		detalle.sort(Comparator.comparing(FestivoDTOs.ClaseConLista::fecha)
				.thenComparing(FestivoDTOs.ClaseConLista::horaInicio, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(FestivoDTOs.ClaseConLista::sede, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(FestivoDTOs.ClaseConLista::grupo, Comparator.nullsLast(Comparator.naturalOrder())));
		afectadas.sort(Comparator.comparing(FestivoDTOs.ClaseAfectada::fecha)
				.thenComparing(FestivoDTOs.ClaseAfectada::horaInicio, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(FestivoDTOs.ClaseAfectada::sede, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(FestivoDTOs.ClaseAfectada::grupo, Comparator.nullsLast(Comparator.naturalOrder())));
		long conLista = detalle.size();
		return new FestivoDTOs.Impacto(total, conLista,
				List.copyOf(detalle.subList(0, Math.min(MAX_DETALLE_IMPACTO, detalle.size()))),
				List.copyOf(afectadas.subList(0, Math.min(MAX_DETALLE_IMPACTO, afectadas.size()))));
	}

	// ------------------------------------------------------------------ apoyo

	private Festivo bloquear(Long id) {
		if (id == null) {
			throw new NoSuchElementException(FESTIVO_NO_EXISTE);
		}
		return festivos.bloquearEnEscuelaActiva(id).orElseThrow(() -> new NoSuchElementException(FESTIVO_NO_EXISTE));
	}

	/** El responsable solo cambia los festivos con TODAS sus sedes entre las suyas (403). Gestión, siempre. */
	private void exigirEditable(Collection<Long> sedesDelFestivo) {
		if (!editable(sedesDelFestivo, gestionPorSede.restriccion())) {
			throw new AccessDeniedException("Acceso denegado.");
		}
	}

	private static boolean editable(Collection<Long> sedesDelFestivo, Optional<AlcanceSesion> restriccion) {
		return restriccion.isEmpty() || (!sedesDelFestivo.isEmpty()
				&& restriccion.get().sedes().containsAll(sedesDelFestivo));
	}

	/**
	 * Sedes del cuerpo, sin repetir: al menos una (400); todas de la escuela activa y, para el responsable, suyas (si
	 * no, 403).
	 */
	private Set<Long> exigirSedes(List<Long> pedidas) {
		Set<Long> ids = new TreeSet<>();
		if (pedidas != null) {
			pedidas.stream().filter(Objects::nonNull).forEach(ids::add);
		}
		if (ids.isEmpty()) {
			throw new IllegalArgumentException(SEDES_OBLIGATORIAS);
		}
		if (sedes.findByIdInOrderByIdAsc(ids).size() != ids.size()) {
			throw new AccessDeniedException("Acceso denegado.");
		}
		Optional<AlcanceSesion> restriccion = gestionPorSede.restriccion();
		if (restriccion.isPresent() && !restriccion.get().sedes().containsAll(ids)) {
			throw new AccessDeniedException("Acceso denegado.");
		}
		return new LinkedHashSet<>(ids);
	}

	/** Valida la forma del cuerpo (400 con el primer error). */
	private static Forma forma(FestivoRequest cuerpo) {
		if (cuerpo == null) {
			throw new IllegalArgumentException(TIPO_NO_VALIDO);
		}
		TipoFestivo tipo = TipoFestivo.deNombre(cuerpo.tipo());
		if (tipo == null) {
			throw new IllegalArgumentException(TIPO_NO_VALIDO);
		}
		String nombre = cuerpo.nombre() == null ? null : cuerpo.nombre().strip();
		if (nombre != null && nombre.isEmpty()) {
			nombre = null;
		}
		if (nombre != null && nombre.length() > MAX_NOMBRE) {
			throw new IllegalArgumentException(NOMBRE_DEMASIADO_LARGO);
		}
		switch (tipo) {
		case DIA -> {
			if (vacio(cuerpo.fechaDesde())) {
				throw new IllegalArgumentException(FECHA_OBLIGATORIA);
			}
			LocalDate fecha = fecha(cuerpo.fechaDesde());
			if (!vacio(cuerpo.fechaHasta()) && !fecha(cuerpo.fechaHasta()).equals(fecha)) {
				throw new IllegalArgumentException(DIA_CON_OTRO_FIN);
			}
			return new Forma(tipo, fecha, fecha, null, null, nombre);
		}
		case RANGO -> {
			if (vacio(cuerpo.fechaDesde()) || vacio(cuerpo.fechaHasta())) {
				throw new IllegalArgumentException(FECHAS_OBLIGATORIAS);
			}
			LocalDate desde = fecha(cuerpo.fechaDesde());
			LocalDate hasta = fecha(cuerpo.fechaHasta());
			if (!hasta.isAfter(desde)) {
				throw new IllegalArgumentException(RANGO_AL_REVES);
			}
			if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAX_DIAS_RANGO) {
				throw new IllegalArgumentException(RANGO_DEMASIADO_LARGO);
			}
			return new Forma(tipo, desde, hasta, null, null, nombre);
		}
		default -> {
			Integer mes = cuerpo.mes();
			Integer dia = cuerpo.dia();
			if (mes == null || dia == null) {
				throw new IllegalArgumentException(DIA_Y_MES_OBLIGATORIOS);
			}
			if (mes < 1 || mes > 12) {
				throw new IllegalArgumentException(MES_NO_VALIDO);
			}
			if (mes == 2 && dia == 29) {
				throw new IllegalArgumentException(VEINTINUEVE_DE_FEBRERO);
			}
			if (dia < 1 || dia > Month.of(mes).minLength()) {
				throw new IllegalArgumentException(DIA_NO_VALIDO);
			}
			return new Forma(tipo, null, null, mes, dia, nombre);
		}
		}
	}

	private static boolean vacio(String texto) {
		return texto == null || texto.isBlank();
	}

	/** {@code yyyy-MM-dd} de una fecha que existe; años como pasar lista. */
	private static LocalDate fecha(String texto) {
		LocalDate fecha;
		try {
			fecha = LocalDate.parse(texto.strip());
		} catch (DateTimeParseException ex) {
			throw new IllegalArgumentException(FECHA_NO_VALIDA);
		}
		if (fecha.getYear() < ProfesorService.ANIO_MINIMO || fecha.getYear() > ProfesorService.ANIO_MAXIMO) {
			throw new IllegalArgumentException(FECHA_NO_VALIDA);
		}
		return fecha;
	}

	/** Fechas que cubre la forma, en orden: DIA, una; RANGO, todas; ANUAL, su próxima aparición desde hoy. */
	private static List<LocalDate> fechasDe(Forma forma, LocalDate hoy) {
		if (forma.tipo() == TipoFestivo.ANUAL) {
			return List.of(proximaAparicion(forma.mes(), forma.dia(), hoy));
		}
		List<LocalDate> fechas = new ArrayList<>();
		for (LocalDate fecha = forma.desde(); !fecha.isAfter(forma.hasta()); fecha = fecha.plusDays(1)) {
			fechas.add(fecha);
		}
		return fechas;
	}

	/** Sedes de cada festivo, en una consulta. */
	private Map<Long, Set<Long>> sedesDe(Collection<Long> festivoIds) {
		Map<Long, Set<Long>> resultado = new HashMap<>();
		for (FestivoSede fila : festivoSedes.findByFestivoIdIn(festivoIds)) {
			resultado.computeIfAbsent(fila.getFestivoId(), k -> new TreeSet<>()).add(fila.getSedeId());
		}
		return resultado;
	}

	/** Todas las sedes de la escuela activa por id (pocas), para los nombres y {@code todasLasSedes}. */
	private Map<Long, Sede> sedesDeLaEscuela() {
		Map<Long, Sede> porId = new LinkedHashMap<>();
		for (Sede sede : sedes.findAllByOrderByIdAsc()) {
			porId.put(sede.getId(), sede);
		}
		return porId;
	}

	private static FestivoDTOs.Festivo dto(Festivo festivo, Collection<Long> sedesDelFestivo,
			Map<Long, Sede> sedesDeLaEscuela, Optional<AlcanceSesion> restriccion, LocalDate hoy) {
		List<FestivoDTOs.SedeDeFestivo> suyas = new ArrayList<>();
		for (Long id : new TreeSet<>(sedesDelFestivo)) {
			Sede sede = sedesDeLaEscuela.get(id);
			suyas.add(new FestivoDTOs.SedeDeFestivo(id, sede == null ? null : sede.getNombre()));
		}
		List<Long> activas = sedesDeLaEscuela.values().stream().filter(Sede::isActiva)
				.map(Sede::getId).toList();
		boolean todas = !activas.isEmpty() && sedesDelFestivo.containsAll(activas);
		LocalDate proxima = festivo.getTipo() == TipoFestivo.ANUAL
				? proximaAparicion(festivo.getMes(), festivo.getDia(), hoy)
				: festivo.getFechaDesde();
		return new FestivoDTOs.Festivo(festivo.getId(), festivo.getTipo().name(), festivo.getFechaDesde(),
				festivo.getFechaHasta(), festivo.getMes(), festivo.getDia(), proxima, festivo.getNombre(),
				List.copyOf(suyas), todas, editable(sedesDelFestivo, restriccion));
	}

	/** El analizador del PDF: día en español, con o sin tilde; {@code null} si es desconocido. */
	private static DayOfWeek diaDeLaSemana(String diaSemana) {
		if (diaSemana == null) {
			return null;
		}
		try {
			return DiaSemanaUtils.mapGrupoToDayOfWeek(diaSemana);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
