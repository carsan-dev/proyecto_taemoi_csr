package com.taemoi.project.services;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
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
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.dtos.response.AsistenciaDTOs;
import com.taemoi.project.dtos.response.AsistenciaDTOs.EstadoDia;
import com.taemoi.project.dtos.response.ProfesorDTOs;
import com.taemoi.project.entities.AsistenciaAlumno;
import com.taemoi.project.entities.ClaseAsistencia;
import com.taemoi.project.entities.Turno;
import com.taemoi.project.organizacion.AlcanceActual;
import com.taemoi.project.repositories.AsistenciaAlumnoRepository;
import com.taemoi.project.repositories.ClaseAsistenciaRepository;
import com.taemoi.project.repositories.TurnoRepository;
import com.taemoi.project.utils.DiaSemanaUtils;

import jakarta.persistence.EntityManager;

/**
 * Pasar lista (decisión 324, fase3-pasar-lista.md). Una clase es un turno y una fecha; una lista guardada escribe una
 * fila por cada alumno de la lista, presente o no. Sin lista, la clase no cuenta.
 *
 * <p>La lista es del TURNO, no del grupo: un alumno solo cuenta en los turnos a los que está asignado. Un alumno del turno
 * del lunes no sale en la lista del miércoles ni le suma una falta ese día.
 *
 * <p>El mismo servicio atiende dos prefijos ({@link Via}): el del profesor, solo con {@code alcance.grupos()} (como
 * {@link ProfesorService}; el {@code @PreAuthorize} ya filtra y aquí se vuelve a comprobar) y hasta
 * {@value #DIAS_ATRAS_PROFESOR} días atrás; y el de gestión, sin límite hacia atrás, con su {@code @acceso.grupoEnSede}.
 *
 * <p>Escuela: todas las tablas se acotan por {@code @TenantId}; un grupo, turno o alumno de otra escuela no existe (404).
 * «Hoy» y «ahora» salen del {@link Clock} de la aplicación (Europe/Madrid). Las rutas de lectura no hacen consultas por
 * turno ni por alumno.
 */
@Service
public class AsistenciaService {

	/** Por dónde llega la petición. Decide el límite hacia atrás y la ruta que queda en la auditoría. */
	public enum Via {
		PROFESOR("/api/profesor/grupos/"), GESTION("/api/grupos/");

		private final String prefijo;

		Via(String prefijo) {
			this.prefijo = prefijo;
		}
	}

	/** El profesor pasa lista hasta hoy − 14 días, incluido; gestión, sin límite. */
	public static final int DIAS_ATRAS_PROFESOR = 14;
	/** Ventana de {@code recientes}: hoy − 14 días hasta hoy, incluidos. */
	static final int DIAS_RECIENTES = 14;

	public static final String CLASE_NO_EXISTE = "La clase no existe.";
	public static final String GRUPO_NO_EXISTE = "El grupo no existe.";
	public static final String ALUMNO_NO_ES_DEL_GRUPO = "El alumno no está en este grupo.";
	public static final String FECHA_NO_VALIDA = "La fecha no es válida.";
	public static final String FECHA_OTRO_DIA = "Ese día no hay clase de este turno.";
	public static final String FECHA_FUTURA = "No se puede pasar lista de una fecha futura.";
	public static final String FECHA_DEMASIADO_ANTIGUA = "Solo puedes pasar lista de los últimos 14 días.";
	public static final String PRESENTES_OBLIGATORIOS = "Falta la lista de presentes.";
	public static final String ALUMNO_FUERA_DE_LA_LISTA = "Hay alumnos que no están en esta lista.";
	public static final String TURNO_SIN_ALUMNOS = "Este turno no tiene alumnos.";
	public static final String TURNO_SIN_HORARIO = "Este turno no tiene un horario válido.";
	public static final String CLASE_DE_OTRO_GRUPO = "Esta lista se guardó con otro grupo y no se puede cambiar desde aquí.";
	public static final String ANIO_MES_NO_VALIDOS = "El año o el mes no son válidos.";

	private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

	private final EntityManager em;
	private final AlcanceActual alcanceActual;
	private final ClaseAsistenciaRepository clases;
	private final AsistenciaAlumnoRepository filas;
	private final TurnoRepository turnos;
	private final ProfesorService profesor;
	private final RegistroAccionesProfesor registro;
	private final Clock reloj;

	public AsistenciaService(EntityManager em, AlcanceActual alcanceActual, ClaseAsistenciaRepository clases,
			AsistenciaAlumnoRepository filas, TurnoRepository turnos, ProfesorService profesor,
			RegistroAccionesProfesor registro, Clock reloj) {
		this.em = em;
		this.alcanceActual = alcanceActual;
		this.clases = clases;
		this.filas = filas;
		this.turnos = turnos;
		this.profesor = profesor;
		this.registro = registro;
		this.reloj = reloj;
	}

	// ------------------------------------------------------------------ lectura

	/** {@code GET /clases}: las de hoy, las recientes y la próxima. Un turno con día desconocido se descarta, como en el PDF. */
	@Transactional(readOnly = true)
	public AsistenciaDTOs.Clases clases(Via via, Long grupoId) {
		exigirGrupo(via, grupoId);
		LocalDateTime ahora = LocalDateTime.now(reloj);
		LocalDate hoy = ahora.toLocalDate();
		LocalDate desde = hoy.minusDays(DIAS_RECIENTES);

		Map<Long, TurnoConDia> delGrupo = turnosDelGrupo(grupoId);
		Map<Long, Long> tamano = tamanoDeLasListas(grupoId);
		List<ClaseAsistencia> guardadas = clases.findByGrupoIdAndFechaBetween(grupoId, desde, hoy);
		Map<Long, long[]> cuentas = cuentasPorClase(guardadas.stream().map(ClaseAsistencia::getId).toList());
		Map<ClaveClase, ClaseAsistencia> porClave = new HashMap<>();
		for (ClaseAsistencia guardada : guardadas) {
			if (guardada.getTurnoId() != null) {
				porClave.put(new ClaveClase(guardada.getTurnoId(), guardada.getFecha()), guardada);
			}
		}

		List<AsistenciaDTOs.Clase> recientes = new ArrayList<>();
		for (TurnoConDia turno : delGrupo.values()) {
			for (LocalDate fecha = desde; !fecha.isAfter(hoy); fecha = fecha.plusDays(1)) {
				ClaseAsistencia guardada = porClave.remove(new ClaveClase(turno.id(), fecha));
				if (guardada != null || fecha.getDayOfWeek() == turno.dia()) {
					recientes.add(clase(turno.id(), fecha, turno.horaInicio(), turno.horaFin(), guardada, cuentas, tamano,
							ahora));
				}
			}
		}
		recientes.sort(Comparator.comparing(AsistenciaDTOs.Clase::fecha, Comparator.reverseOrder())
				.thenComparing(AsistenciaDTOs.Clase::horaInicio, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(AsistenciaDTOs.Clase::turnoId));
		List<AsistenciaDTOs.Clase> deHoy = recientes.stream().filter(c -> c.fecha().equals(hoy)).toList();
		return new AsistenciaDTOs.Clases(deHoy, recientes, proxima(delGrupo.values(), ahora));
	}

	/**
	 * {@code GET /clases/{turnoId}/{fecha}}: lista actual ∪ alumnos con fila guardada, por nombre, apellidos e id. Un
	 * alumno sin fila sale como no presente. Una fecha que no cae en el día del turno solo se lee si tiene lista guardada.
	 */
	@Transactional(readOnly = true)
	public AsistenciaDTOs.ListaDeClase lista(Via via, Long grupoId, Long turnoId, String fechaTexto) {
		String disciplina = exigirGrupo(via, grupoId);
		TurnoConDia turno = turnosDelGrupo(grupoId).get(turnoId);
		if (turno == null) {
			throw new NoSuchElementException(CLASE_NO_EXISTE);
		}
		LocalDate fecha = fecha(fechaTexto);
		ClaseAsistencia guardada = clases.findByTurnoIdAndFecha(turnoId, fecha)
				.filter(c -> grupoId.equals(c.getGrupoId())).orElse(null);
		if (guardada == null && fecha.getDayOfWeek() != turno.dia()) {
			throw new IllegalArgumentException(FECHA_OTRO_DIA);
		}
		Map<Long, Boolean> presente = new HashMap<>();
		if (guardada != null) {
			for (AsistenciaAlumno fila : filas.findByClaseId(guardada.getId())) {
				presente.put(fila.getAlumnoId(), fila.isPresente());
			}
		}
		Map<Long, List<Long>> listas = listasActuales(grupoId);
		Set<Long> union = new LinkedHashSet<>(listas.getOrDefault(turnoId, List.of()));
		union.addAll(presente.keySet());

		Map<Long, List<ProfesorDTOs.DeporteEnLista>> deportes = profesor.deportesEnLista(union, disciplina);
		List<AsistenciaDTOs.AlumnoDeLista> alumnos = new ArrayList<>();
		for (Object[] f : alumnosPorNombre(union)) {
			Long id = (Long) f[0];
			alumnos.add(new AsistenciaDTOs.AlumnoDeLista(id, (String) f[1], (String) f[2],
					Boolean.TRUE.equals(presente.get(id)), deportes.getOrDefault(id, List.of())));
		}
		Map<Long, long[]> cuentas = guardada == null ? Map.of() : cuentasPorClase(List.of(guardada.getId()));
		AsistenciaDTOs.Clase clase = clase(turnoId, fecha, turno.horaInicio(), turno.horaFin(), guardada, cuentas,
				Map.of(turnoId, (long) listas.getOrDefault(turnoId, List.of()).size()), LocalDateTime.now(reloj));
		return new AsistenciaDTOs.ListaDeClase(clase, alumnos);
	}

	/** {@code GET /asistencia/resumen}: los alumnos con alguna fila en clases del grupo ese mes. Una consulta. */
	@Transactional(readOnly = true)
	public List<AsistenciaDTOs.ResumenAlumno> resumen(Via via, Long grupoId, int year, int month) {
		exigirGrupo(via, grupoId);
		YearMonth mes = mes(year, month);
		return filas.resumenDelGrupo(grupoId, mes.atDay(1), mes.atEndOfMonth()).stream()
				.map(f -> new AsistenciaDTOs.ResumenAlumno((Long) f[0], ((Number) f[1]).longValue(),
						((Number) f[2]).longValue()))
				.toList();
	}

	/**
	 * {@code GET /alumnos/{alumnoId}/asistencia}: las fechas del mes en que caen SUS turnos en ese grupo, hasta hoy, más
	 * las clases en las que tiene fila (un turno borrado o que ya no es suyo sigue contando), por fecha y hora.
	 * {@code presentes} y {@code clases} son sus filas: las mismas cifras que el resumen. Si el mismo día y hora tiene fila
	 * en un turno borrado, no sale además como sin lista el turno que lo sustituyó.
	 */
	@Transactional(readOnly = true)
	public AsistenciaDTOs.AsistenciaDeAlumno asistenciaDeAlumno(Via via, Long grupoId, Long alumnoId, int year,
			int month) {
		exigirGrupo(via, grupoId);
		YearMonth mes = mes(year, month);
		Long enGrupo = em.createQuery(
				"SELECT COUNT(a.id) FROM Grupo g JOIN g.alumnos a WHERE g.id = :grupo AND a.id = :alumno", Long.class)
				.setParameter("grupo", grupoId).setParameter("alumno", alumnoId).getSingleResult();
		if (enGrupo == null || enGrupo == 0) {
			throw new NoSuchElementException(ALUMNO_NO_ES_DEL_GRUPO);
		}
		LocalDate hoy = LocalDate.now(reloj);
		LocalDate ultimo = mes.atEndOfMonth().isAfter(hoy) ? hoy : mes.atEndOfMonth();

		long presentes = 0;
		long total = 0;
		List<AsistenciaDTOs.Dia> dias = new ArrayList<>();
		Set<ClaveClase> conFila = new HashSet<>();
		Set<ClaveHora> horasConFila = new HashSet<>();
		for (Object[] f : filas.filasDelAlumno(alumnoId, grupoId, mes.atDay(1), mes.atEndOfMonth())) {
			boolean presente = Boolean.TRUE.equals(f[3]);
			LocalDate fecha = (LocalDate) f[1];
			String hora = (String) f[2];
			total++;
			presentes += presente ? 1 : 0;
			dias.add(new AsistenciaDTOs.Dia(fecha, hora, presente ? EstadoDia.PRESENTE : EstadoDia.FALTA));
			conFila.add(new ClaveClase((Long) f[0], fecha));
			horasConFila.add(new ClaveHora(fecha, hora));
		}
		for (Object[] f : em.createQuery("""
				SELECT t.id, t.diaSemana, t.horaInicio FROM Turno t JOIN t.alumnos a
				WHERE t.grupo.id = :grupo AND a.id = :alumno""", Object[].class)
				.setParameter("grupo", grupoId).setParameter("alumno", alumnoId).getResultList()) {
			DayOfWeek dia = diaDeLaSemana((String) f[1]);
			if (dia == null) {
				continue;
			}
			String hora = (String) f[2];
			for (LocalDate fecha = mes.atDay(1); !fecha.isAfter(ultimo); fecha = fecha.plusDays(1)) {
				if (fecha.getDayOfWeek() == dia && !conFila.contains(new ClaveClase((Long) f[0], fecha))
						&& !horasConFila.contains(new ClaveHora(fecha, hora))) {
					dias.add(new AsistenciaDTOs.Dia(fecha, hora, EstadoDia.SIN_LISTA));
				}
			}
		}
		dias.sort(Comparator.comparing(AsistenciaDTOs.Dia::fecha).thenComparing(AsistenciaDTOs.Dia::horaInicio,
				Comparator.nullsLast(Comparator.naturalOrder())));
		return new AsistenciaDTOs.AsistenciaDeAlumno(presentes, total, List.copyOf(dias));
	}

	// ------------------------------------------------------------------ escritura

	/**
	 * {@code PUT /clases/{turnoId}/{fecha}}: reemplaza la lista. Escribe una fila por cada alumno de la unión (lista actual ∪
	 * filas guardadas); crea la clase la primera vez, con el horario del turno, y después marca la modificación. Es
	 * idempotente y la última escritura gana. Una sola fila de auditoría por PUT, también si no cambia nada.
	 *
	 * <p>Carrera: READ COMMITTED y, antes de leer nada de la clase, la fila del turno bloqueada ({@code FOR UPDATE}), como
	 * el pase de grado con su inscripción. Dos PUT de la misma clase se ponen en fila: el segundo ve la clase del primero
	 * y la corrige, así que no llega a chocar con la UNIQUE (con REPEATABLE READ vería la foto de antes de esperar). Espera
	 * agotada o interbloqueo: 409 con el mensaje de concurrencia, sin nada escrito. También ordena el PUT frente a borrar
	 * el turno (que bloquea la misma fila): si ya se borró, 404.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public AsistenciaDTOs.Clase guardar(Via via, Long grupoId, Long turnoId, String fechaTexto, List<Long> presentes) {
		exigirGrupo(via, grupoId);
		Turno turno = turnoId == null ? null : turnos.findByIdForUpdate(turnoId).orElse(null);
		if (turno == null || turno.getGrupo() == null || !grupoId.equals(turno.getGrupo().getId())) {
			throw new NoSuchElementException(CLASE_NO_EXISTE);
		}
		LocalDate fecha = fecha(fechaTexto);
		LocalDateTime ahora = LocalDateTime.now(reloj);
		exigirFechaEscribible(via, turno, fecha, ahora.toLocalDate());
		if (presentes == null || presentes.stream().anyMatch(id -> id == null)) {
			throw new IllegalArgumentException(PRESENTES_OBLIGATORIOS);
		}
		exigirHorario(turno);

		ClaseAsistencia guardada = clases.findByTurnoIdAndFecha(turnoId, fecha).orElse(null);
		if (guardada != null && !grupoId.equals(guardada.getGrupoId())) {
			throw new IllegalStateException(CLASE_DE_OTRO_GRUPO);
		}
		Map<Long, AsistenciaAlumno> existentes = new LinkedHashMap<>();
		if (guardada != null) {
			for (AsistenciaAlumno fila : filas.findByClaseId(guardada.getId())) {
				existentes.put(fila.getAlumnoId(), fila);
			}
		}
		List<Long> listaActual = listasActuales(grupoId).getOrDefault(turnoId, List.of());
		Set<Long> union = new LinkedHashSet<>(listaActual);
		union.addAll(existentes.keySet());
		if (union.isEmpty()) {
			throw new IllegalArgumentException(TURNO_SIN_ALUMNOS);
		}
		if (!union.containsAll(presentes)) {
			throw new IllegalArgumentException(ALUMNO_FUERA_DE_LA_LISTA);
		}

		Long usuario = CobroService.usuarioActualId();
		boolean nueva = guardada == null;
		if (nueva) {
			guardada = clases.save(new ClaseAsistencia(grupoId, turnoId, fecha, turno.getHoraInicio(), turno.getHoraFin(),
					usuario, ahora));
		} else {
			guardada.marcarModificada(usuario, ahora);
		}
		Set<Long> marcados = new HashSet<>(presentes);
		List<AsistenciaAlumno> nuevas = new ArrayList<>();
		for (Long alumno : union) {
			AsistenciaAlumno fila = existentes.get(alumno);
			if (fila == null) {
				nuevas.add(new AsistenciaAlumno(guardada.getId(), alumno, marcados.contains(alumno)));
			} else {
				fila.setPresente(marcados.contains(alumno));
			}
		}
		filas.saveAll(nuevas);

		Map<String, Object> valores = new LinkedHashMap<>();
		valores.put("clase", guardada.getId());
		valores.put("grupo", grupoId);
		valores.put("turno", turnoId);
		registro.registrar(nueva ? RegistroAccionesProfesor.Accion.ASISTENCIA_REGISTRADA
				: RegistroAccionesProfesor.Accion.ASISTENCIA_CORREGIDA,
				via.prefijo + grupoId + "/clases/" + turnoId + "/" + fecha, valores);

		return new AsistenciaDTOs.Clase(turnoId, fecha, guardada.getHoraInicio(), guardada.getHoraFin(),
				pasada(fecha, guardada.getHoraFin(), ahora), (long) marcados.size(), (long) union.size(),
				listaActual.size());
	}

	// ------------------------------------------------------------------ apoyo

	private record TurnoConDia(Long id, DayOfWeek dia, String horaInicio, String horaFin) {
	}

	private record ClaveClase(Long turnoId, LocalDate fecha) {
	}

	private record ClaveHora(LocalDate fecha, String horaInicio) {
	}

	/**
	 * Profesor: el grupo es de {@code alcance.grupos()} (403). El grupo existe en la escuela activa (404): gestión con
	 * alcance total pasa el {@code @acceso} sin consultar. Devuelve el código de la disciplina del grupo.
	 */
	private String exigirGrupo(Via via, Long grupoId) {
		if (via == Via.PROFESOR && (grupoId == null || !alcanceActual.obtener().grupos().contains(grupoId))) {
			throw new AccessDeniedException("Acceso denegado.");
		}
		List<String> disciplina = em
				.createQuery("SELECT d.codigo FROM Grupo g LEFT JOIN g.disciplina d WHERE g.id = :grupo", String.class)
				.setParameter("grupo", grupoId).getResultList();
		if (disciplina.isEmpty()) {
			throw new NoSuchElementException(GRUPO_NO_EXISTE);
		}
		return disciplina.get(0);
	}

	private void exigirFechaEscribible(Via via, Turno turno, LocalDate fecha, LocalDate hoy) {
		if (fecha.getDayOfWeek() != diaDeLaSemana(turno.getDiaSemana())) {
			throw new IllegalArgumentException(FECHA_OTRO_DIA);
		}
		if (fecha.isAfter(hoy)) {
			throw new IllegalArgumentException(FECHA_FUTURA);
		}
		if (via == Via.PROFESOR && ChronoUnit.DAYS.between(fecha, hoy) > DIAS_ATRAS_PROFESOR) {
			throw new IllegalArgumentException(FECHA_DEMASIADO_ANTIGUA);
		}
	}

	/** Las columnas son {@code VARCHAR(5) NOT NULL}: un turno heredado con otra forma daría 500 al guardar. */
	private static void exigirHorario(Turno turno) {
		for (String hora : new String[] { turno.getHoraInicio(), turno.getHoraFin() }) {
			if (hora == null || hora.isBlank() || hora.length() > 5) {
				throw new IllegalArgumentException(TURNO_SIN_HORARIO);
			}
		}
	}

	/** {@code yyyy-MM-dd} de una fecha que existe; años como el PDF. */
	private static LocalDate fecha(String texto) {
		LocalDate fecha;
		try {
			fecha = LocalDate.parse(texto == null ? "" : texto);
		} catch (DateTimeParseException ex) {
			throw new IllegalArgumentException(FECHA_NO_VALIDA);
		}
		if (fecha.getYear() < ProfesorService.ANIO_MINIMO || fecha.getYear() > ProfesorService.ANIO_MAXIMO) {
			throw new IllegalArgumentException(FECHA_NO_VALIDA);
		}
		return fecha;
	}

	/** Como el PDF del profesor. */
	private static YearMonth mes(int year, int month) {
		if (year < ProfesorService.ANIO_MINIMO || year > ProfesorService.ANIO_MAXIMO || month < 1 || month > 12) {
			throw new IllegalArgumentException(ANIO_MES_NO_VALIDOS);
		}
		return YearMonth.of(year, month);
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

	/** Turnos del grupo por id, sin los de día desconocido. */
	private Map<Long, TurnoConDia> turnosDelGrupo(Long grupoId) {
		Map<Long, TurnoConDia> resultado = new LinkedHashMap<>();
		for (Object[] f : turnos.findResumenPorGrupos(List.of(grupoId))) {
			DayOfWeek dia = diaDeLaSemana((String) f[2]);
			if (dia != null) {
				resultado.put((Long) f[1], new TurnoConDia((Long) f[1], dia, (String) f[3], (String) f[4]));
			}
		}
		return resultado;
	}

	/** Lista actual de cada turno del grupo, por nombre, apellidos e id. */
	private Map<Long, List<Long>> listasActuales(Long grupoId) {
		Map<Long, List<Long>> listas = new HashMap<>();
		for (Object[] f : clases.listaActualDeLosTurnos(grupoId)) {
			listas.computeIfAbsent((Long) f[0], k -> new ArrayList<>()).add((Long) f[1]);
		}
		return listas;
	}

	private Map<Long, Long> tamanoDeLasListas(Long grupoId) {
		Map<Long, Long> tamano = new HashMap<>();
		listasActuales(grupoId).forEach((turno, lista) -> tamano.put(turno, (long) lista.size()));
		return tamano;
	}

	/** {@code [filas, presentes]} por clase. */
	private Map<Long, long[]> cuentasPorClase(Collection<Long> ids) {
		Map<Long, long[]> cuentas = new HashMap<>();
		if (!ids.isEmpty()) {
			for (Object[] f : filas.contarPorClase(ids)) {
				cuentas.put((Long) f[0], new long[] { ((Number) f[1]).longValue(), ((Number) f[2]).longValue() });
			}
		}
		return cuentas;
	}

	/** Id, nombre y apellidos, sin los anonimizados (sus filas se borran al anonimizar; esto cubre una carrera). */
	private List<Object[]> alumnosPorNombre(Collection<Long> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		return em.createQuery("""
				SELECT a.id, a.nombre, a.apellidos FROM Alumno a
				WHERE a.id IN :ids AND a.anonimizadoEn IS NULL ORDER BY a.nombre, a.apellidos, a.id""", Object[].class)
				.setParameter("ids", ids).getResultList();
	}

	private static AsistenciaDTOs.Clase clase(Long turnoId, LocalDate fecha, String horaInicio, String horaFin,
			ClaseAsistencia guardada, Map<Long, long[]> cuentas, Map<Long, Long> tamano, LocalDateTime ahora) {
		String inicio = guardada == null ? horaInicio : guardada.getHoraInicio();
		String fin = guardada == null ? horaFin : guardada.getHoraFin();
		Long presentes = null;
		Long total = null;
		if (guardada != null) {
			long[] cuenta = cuentas.getOrDefault(guardada.getId(), new long[2]);
			total = cuenta[0];
			presentes = cuenta[1];
		}
		return new AsistenciaDTOs.Clase(turnoId, fecha, inicio, fin, pasada(fecha, fin, ahora), presentes, total,
				tamano.getOrDefault(turnoId, 0L));
	}

	/** Ya terminó: un día anterior a hoy, u hoy con la hora de fin alcanzada. */
	private static boolean pasada(LocalDate fecha, String horaFin, LocalDateTime ahora) {
		LocalDate hoy = ahora.toLocalDate();
		if (!fecha.equals(hoy)) {
			return fecha.isBefore(hoy);
		}
		return horaFin != null && horaFin.compareTo(ahora.toLocalTime().format(HORA)) <= 0;
	}

	/** La primera clase que aún no ha empezado, en los próximos 7 días; sin turnos, {@code null}. */
	private static AsistenciaDTOs.Proxima proxima(Collection<TurnoConDia> delGrupo, LocalDateTime ahora) {
		LocalDate hoy = ahora.toLocalDate();
		String hora = ahora.toLocalTime().format(HORA);
		AsistenciaDTOs.Proxima mejor = null;
		for (TurnoConDia turno : delGrupo) {
			if (turno.horaInicio() == null) {
				continue;
			}
			for (int dias = 0; dias <= 7; dias++) {
				LocalDate fecha = hoy.plusDays(dias);
				if (fecha.getDayOfWeek() == turno.dia() && (dias > 0 || turno.horaInicio().compareTo(hora) > 0)) {
					if (mejor == null || fecha.isBefore(mejor.fecha()) || (fecha.equals(mejor.fecha())
							&& turno.horaInicio().compareTo(mejor.horaInicio()) < 0)) {
						mejor = new AsistenciaDTOs.Proxima(fecha, turno.horaInicio());
					}
					break;
				}
			}
		}
		return mejor;
	}
}
