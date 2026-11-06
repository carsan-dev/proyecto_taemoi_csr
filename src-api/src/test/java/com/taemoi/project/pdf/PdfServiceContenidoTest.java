package com.taemoi.project.pdf;

import static com.taemoi.project.pdf.ContenidoPdf.comprobar;
import static com.taemoi.project.pdf.ContenidoPdf.conHoy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.taemoi.project.acceso.GestionPorSede;
import com.taemoi.project.dtos.response.AlumnoConvocatoriaReporteDTO;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.AsistenciaAlumno;
import com.taemoi.project.entities.ClaseAsistencia;
import com.taemoi.project.entities.Convocatoria;
import com.taemoi.project.entities.MotivoFalta;
import com.taemoi.project.entities.TipoFestivo;
import com.taemoi.project.pdf.ContenidoPdf.Presentacion;
import com.taemoi.project.repositories.AlumnoDeporteRepository;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.AsistenciaAlumnoRepository;
import com.taemoi.project.repositories.ClaseAsistenciaRepository;
import com.taemoi.project.repositories.ConvocatoriaRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.repositories.ProductoRepository;
import com.taemoi.project.repositories.TurnoRepository;
import com.taemoi.project.services.ConvocatoriaService;
import com.taemoi.project.services.FestivoService;
import com.taemoi.project.services.MarcaEscuelaPruebas;
import com.taemoi.project.services.ResolutorCuantiaMensualidad;
import com.taemoi.project.services.TarifaSedeService;
import com.taemoi.project.services.impl.PDFServiceImpl;

/**
 * Caracterización del contenido de los PDF de {@link PDFServiceImpl} antes del rediseño (decisión 314): cada informe,
 * con los datos ficticios de {@link DatosPdfPrueba}, contra su dorado {@code pdf-contenido/<informe>.txt} (regla en
 * {@link ContenidoPdf}). Escuela: Moiskimdo sin logo (cabecera «MOI'S KIM DO TAEKWONDO»). La fecha de generación sale
 * de {@code LocalDate.now()} sin Clock: se fija con {@link ContenidoPdf#conHoy} y queda como presentación.
 */
class PdfServiceContenidoTest {

	private static final String ESCUELA = "MOI'S KIM DO TAEKWONDO";
	private static final String FECHA = "Fecha de generación: LocalDate.now() sin Clock, fijada a " + ContenidoPdf.HOY
			+ " en el test (presentación).";

	private final DatosPdfPrueba datos = new DatosPdfPrueba();
	private final AlumnoRepository alumnoRepository = mock(AlumnoRepository.class);
	private final AlumnoDeporteRepository alumnoDeporteRepository = mock(AlumnoDeporteRepository.class);
	private final TurnoRepository turnoRepository = mock(TurnoRepository.class);
	private final ProductoRepository productoRepository = mock(ProductoRepository.class);
	private final ProductoAlumnoRepository productoAlumnoRepository = mock(ProductoAlumnoRepository.class);
	private final ConvocatoriaRepository convocatoriaRepository = mock(ConvocatoriaRepository.class);
	private final ConvocatoriaService convocatoriaService = mock(ConvocatoriaService.class);
	private final TarifaSedeService tarifaSede = mock(TarifaSedeService.class);
	private final ClaseAsistenciaRepository claseAsistenciaRepository = mock(ClaseAsistenciaRepository.class);
	private final AsistenciaAlumnoRepository asistenciaAlumnoRepository = mock(AsistenciaAlumnoRepository.class);
	private final FestivoService festivoService = mock(FestivoService.class);
	private final PDFServiceImpl servicio = new PDFServiceImpl();

	@BeforeEach
	void montar() {
		ReflectionTestUtils.setField(servicio, "alumnoRepository", alumnoRepository);
		ReflectionTestUtils.setField(servicio, "gestionPorSede", mock(GestionPorSede.class));
		ReflectionTestUtils.setField(servicio, "alumnoDeporteRepository", alumnoDeporteRepository);
		ReflectionTestUtils.setField(servicio, "turnoRepository", turnoRepository);
		ReflectionTestUtils.setField(servicio, "claseAsistenciaRepository", claseAsistenciaRepository);
		ReflectionTestUtils.setField(servicio, "asistenciaAlumnoRepository", asistenciaAlumnoRepository);
		ReflectionTestUtils.setField(servicio, "festivoService", festivoService);
		ReflectionTestUtils.setField(servicio, "productoRepository", productoRepository);
		ReflectionTestUtils.setField(servicio, "productoAlumnoRepository", productoAlumnoRepository);
		ReflectionTestUtils.setField(servicio, "convocatoriaRepository", convocatoriaRepository);
		ReflectionTestUtils.setField(servicio, "convocatoriaService", convocatoriaService);
		ReflectionTestUtils.setField(servicio, "catalogoDisciplinas", datos.catalogo.catalogo());
		ReflectionTestUtils.setField(servicio, "progresionGrados", datos.catalogo.progresion());
		ReflectionTestUtils.setField(servicio, "tarifaSede", tarifaSede);
		ReflectionTestUtils.setField(servicio, "marcaEscuela", MarcaEscuelaPruebas.soloMoiskimdo());

		// Las consultas devuelven listas nuevas y modificables (algunos generadores las ordenan en sitio).
		when(alumnoRepository.findAll()).thenAnswer(inv -> new ArrayList<>(datos.alumnos));
		when(alumnoDeporteRepository.findAll()).thenAnswer(inv -> new ArrayList<>(datos.inscripciones));
		when(alumnoDeporteRepository.findByGradoNotNullAndDeporteIn(anyList())).thenAnswer(
				inv -> filtrar(i -> i.getGrado() != null && inv.<List<String>>getArgument(0).contains(i.getDeporte())));
		when(alumnoDeporteRepository.findActivosByDeporteIn(anyList())).thenAnswer(inv -> filtrar(
				i -> Boolean.TRUE.equals(i.getActivo()) && inv.<List<String>>getArgument(0).contains(i.getDeporte())));
		when(alumnoDeporteRepository.findActivosByDeporteWithAlumno(anyString())).thenAnswer(
				inv -> filtrar(i -> Boolean.TRUE.equals(i.getActivo()) && inv.getArgument(0).equals(i.getDeporte())));
		when(alumnoDeporteRepository.findCompetidoresActivosByDeporteIn(anyList())).thenAnswer(
				inv -> filtrar(i -> Boolean.TRUE.equals(i.getCompetidor())
						&& inv.<List<String>>getArgument(0).contains(i.getDeporte())));
		when(turnoRepository.findAllById(anyList())).thenAnswer(inv -> datos.turnos.stream()
				.filter(t -> inv.<List<Long>>getArgument(0).contains(t.getId())).toList());
		when(productoRepository.findAll()).thenAnswer(inv -> new ArrayList<>(datos.productos));
		when(productoAlumnoRepository.findAllUnpaidWithAlumno()).thenAnswer(inv -> new ArrayList<>(datos.impagados));
		when(productoAlumnoRepository.findAllMensualidadesYTarifasCompetidorWithAlumno())
				.thenAnswer(inv -> new ArrayList<>(datos.mensualidades));
		when(productoAlumnoRepository.findMensualidadesYTarifasCompetidorByDeporteOrConcepto(anyString(), anyString()))
				.thenAnswer(inv -> new ArrayList<>(datos.mensualidades.stream()
						.filter(pa -> inv.getArgument(0).equals(pa.getAlumnoDeporte().getDeporte())).toList()));
		when(productoAlumnoRepository.findMensualidadesYTarifasCompetidorByMes("MENSUALIDAD OCTUBRE 2026",
				"OCTUBRE 2026")).thenAnswer(inv -> new ArrayList<>(datos.octubre));
		when(productoAlumnoRepository.findReservasPlazaByTemporada(eq("2026/2027"), org.mockito.ArgumentMatchers.anyBoolean()))
				.thenAnswer(inv -> new ArrayList<>(datos.reservas));
		when(tarifaSede.preparar()).thenReturn(ResolutorCuantiaMensualidad.identidad());
	}

	private List<AlumnoDeporte> filtrar(java.util.function.Predicate<AlumnoDeporte> condicion) {
		return new ArrayList<>(datos.inscripciones.stream().filter(condicion).toList());
	}

	// ------------------------------------------------------------------ alumnos por grado

	@Test
	void alumnosPorGrado() throws Exception {
		comprobar("alumnos-por-grado", conHoy(() -> servicio.generarInformeAlumnosPorGrado(false)),
				Presentacion.de(ESCUELA, "Listado de Alumnos por Grado"), FECHA);
	}

	@Test
	void taekwondoPorGrado() throws Exception {
		comprobar("taekwondo-por-grado", conHoy(() -> servicio.generarInformeTaekwondoPorGrado(false)),
				Presentacion.de(ESCUELA, "Listado de Alumnos por Grado", "Taekwondo por Grado"), FECHA);
	}

	@Test
	void kickboxingPorGrado() throws Exception {
		comprobar("kickboxing-por-grado", conHoy(() -> servicio.generarInformeKickboxingPorGrado(false)),
				Presentacion.de(ESCUELA, "Listado de Alumnos por Grado", "Kickboxing por Grado"), FECHA);
	}

	// ------------------------------------------------------------------ licencias

	@Test
	void licencias() throws Exception {
		comprobar("licencias", conHoy(() -> servicio.generarInformeLicencias(false)),
				Presentacion.de(ESCUELA, "Informe de Licencias de Alumnos", "Informe de Licencias"), FECHA,
				"Licencia en vigor o caducada según el año de HOY.");
	}

	// ------------------------------------------------------------------ promociones

	@Test
	void infantilesAPromocionar() throws Exception {
		comprobar("infantiles-a-promocionar", conHoy(() -> servicio.generarInformeInfantilesAPromocionar(false)),
				Presentacion.de(ESCUELA, "Alumnos Infantiles a Promocionar", "Infantiles a Promocionar"), FECHA,
				"Edad y grupo infantil/adulto calculados con HOY.");
	}

	@Test
	void adultosAPromocionar() throws Exception {
		comprobar("adultos-a-promocionar", conHoy(() -> servicio.generarInformeAdultosAPromocionar(false)),
				Presentacion.de(ESCUELA, "Alumnos Adultos a Promocionar", "Adultos a Promocionar"), FECHA,
				"Edad y grupo infantil/adulto calculados con HOY.");
	}

	@Test
	void infantilesAPromocionarTaekwondo() throws Exception {
		comprobar("infantiles-a-promocionar-taekwondo",
				conHoy(() -> servicio.generarInformeInfantilesAPromocionarTaekwondo(false)),
				Presentacion.de(ESCUELA, "Alumnos Infantiles a Promocionar - Taekwondo",
						"Infantiles a Promocionar - Taekwondo"),
				FECHA, "Edad y grupo infantil/adulto calculados con HOY.");
	}

	@Test
	void infantilesAPromocionarKickboxing() throws Exception {
		comprobar("infantiles-a-promocionar-kickboxing",
				conHoy(() -> servicio.generarInformeInfantilesAPromocionarKickboxing(false)),
				Presentacion.de(ESCUELA, "Alumnos Infantiles a Promocionar - Kickboxing",
						"Infantiles a Promocionar - Kickboxing"),
				FECHA, "Sin infantiles de kickboxing en los datos: fija el texto del informe vacío.");
	}

	@Test
	void adultosAPromocionarTaekwondo() throws Exception {
		comprobar("adultos-a-promocionar-taekwondo",
				conHoy(() -> servicio.generarInformeAdultosAPromocionarTaekwondo(false)),
				Presentacion.de(ESCUELA, "Alumnos Adultos a Promocionar - Taekwondo",
						"Adultos a Promocionar - Taekwondo"),
				FECHA, "Edad y grupo infantil/adulto calculados con HOY.");
	}

	@Test
	void adultosAPromocionarKickboxing() throws Exception {
		comprobar("adultos-a-promocionar-kickboxing",
				conHoy(() -> servicio.generarInformeAdultosAPromocionarKickboxing(false)),
				Presentacion.de(ESCUELA, "Alumnos Adultos a Promocionar - Kickboxing",
						"Adultos a Promocionar - Kickboxing"),
				FECHA, "Edad y grupo infantil/adulto calculados con HOY.");
	}

	// ------------------------------------------------------------------ asistencia

	@Test
	void listadoAsistenciaPorGrupos() throws Exception {
		comprobar("listado-asistencia-grupos",
				conHoy(() -> servicio.generarListadoAsistencia(2026, 10, List.of("Lunes", "Miércoles"), null)),
				Presentacion.de("CLUB MOI'S KIM DO", "Tae Kwon Do"),
				"Octubre de 2026, días Lunes y Miércoles, todas las disciplinas. Edad y meses en el grado con HOY.");
	}

	@Test
	void listadoAsistenciaUnGrupo() throws Exception {
		comprobar("listado-asistencia-grupo",
				conHoy(() -> servicio.generarListadoAsistencia(2026, 10, "Martes", "PILATES")),
				Presentacion.de("CLUB MOI'S KIM DO", "Tae Kwon Do"),
				"Octubre de 2026, día Martes, solo Pilates. Edad con HOY.");
	}

	@Test
	void asistenciaPorTurnosDelProfesor() throws Exception {
		comprobar("asistencia-turnos-profesor",
				conHoy(() -> servicio.generarListadoAsistenciaPorTurnos(2026, 10, List.of(11L, 9L))),
				Presentacion.de("CLUB MOI'S KIM DO", "Tae Kwon Do"),
				"Octubre de 2026, turnos 9 y 11. Edad y meses en el grado con HOY.");
	}

	// ------------------------------------------------------------------ asistencia marcada

	private static final String LEYENDA = "X: asistió · –: faltó · I: injustificada · M: médica · F: familiar"
			+ " · J: otra justificada · Gris: festivo · Vacía: sin lista";

	@Test
	void asistenciaSinMarcarNoConsultaListasNiPintaLeyenda() throws Exception {
		byte[] sin = conHoy(() -> servicio.generarListadoAsistenciaPorTurnos(2026, 10, List.of(11L, 9L)));
		byte[] enFalse = conHoy(() -> servicio.generarListadoAsistenciaPorTurnos(2026, 10, List.of(11L, 9L), false));
		byte[] gestion = conHoy(() -> servicio.generarListadoAsistencia(2026, 10, List.of("Lunes"), null, null, false));

		assertEquals(ContenidoPdf.lineas(sin), ContenidoPdf.lineas(enFalse));
		assertFalse(String.join("\n", ContenidoPdf.lineas(enFalse)).contains("asistió"));
		assertFalse(String.join("\n", ContenidoPdf.lineas(gestion)).contains("asistió"));
		verifyNoInteractions(claseAsistenciaRepository, asistenciaAlumnoRepository, festivoService);
	}

	@Test
	void asistenciaMarcadaPintaLoGuardadoPorTurnoYFecha() throws Exception {
		// Turno 9 (grupo 3): lunes 5 con lista (Ana presente, Carla falta); lunes 12 sin lista. Turno 11 (grupo 5):
		// lunes 5 con lista de Bruno presente y sin fila de Eva (alumno sin fila en una lista guardada).
		ClaseAsistencia infantil5 = clase(100L, 3L, 9L, LocalDate.of(2026, 10, 5));
		ClaseAsistencia adultos5 = clase(101L, 5L, 11L, LocalDate.of(2026, 10, 5));
		// Ignoradas: otro grupo en el turno 9 (lunes 12), día que no es columna (martes 6) y un turno que no sale.
		ClaseAsistencia deOtroGrupo = clase(102L, 99L, 9L, LocalDate.of(2026, 10, 12));
		ClaseAsistencia noEsColumna = clase(103L, 3L, 9L, LocalDate.of(2026, 10, 6));
		ClaseAsistencia otroTurno = clase(104L, 3L, 77L, LocalDate.of(2026, 10, 19));
		when(claseAsistenciaRepository.findByTurnoIdInAndFechaBetween(any(), eq(LocalDate.of(2026, 10, 1)),
				eq(LocalDate.of(2026, 10, 31)))).thenReturn(
						List.of(infantil5, adultos5, deOtroGrupo, noEsColumna, otroTurno));
		when(asistenciaAlumnoRepository.findByClaseIdIn(any())).thenReturn(List.of(
				new AsistenciaAlumno(100L, 1L, true), new AsistenciaAlumno(100L, 3L, false),
				new AsistenciaAlumno(101L, 2L, true),
				// Ana y Carla en la clase de otro grupo y en el martes: no marcan.
				new AsistenciaAlumno(102L, 1L, true), new AsistenciaAlumno(103L, 3L, true),
				new AsistenciaAlumno(104L, 1L, true),
				// Ana en la lista del turno 11: no está en esa página.
				new AsistenciaAlumno(101L, 1L, true)));

		List<String> lineas = ContenidoPdf.lineas(
				conHoy(() -> servicio.generarListadoAsistenciaPorTurnos(2026, 10, List.of(11L, 9L), true)));

		assertTrue(lineas.contains("7m 5001 11 Ana Prueba Uno 101 X"), String.join("\n", lineas));
		assertTrue(lineas.contains("10m NO 13 Carla Prueba Tres 103 –"), String.join("\n", lineas));
		assertTrue(lineas.contains("16m 5002 36 Bruno Prueba Dos 102 X"), String.join("\n", lineas));
		assertTrue(lineas.contains("29m 1º 5005 26 Eva Prueba Cinco 105"), String.join("\n", lineas));
		// Mismas columnas y una leyenda por página.
		assertEquals(2, lineas.stream().filter(l -> l.equals("5 12 19 26")).count());
		assertEquals(2, lineas.stream().filter(l -> l.contains(LEYENDA)).count(), String.join("\n", lineas));
		// Una consulta de clases para todos los turnos y una de filas para todas las clases.
		verify(claseAsistenciaRepository, times(1)).findByTurnoIdInAndFechaBetween(any(), any(), any());
		verify(asistenciaAlumnoRepository, times(1)).findByClaseIdIn(any());
	}

	@Test
	void asistenciaMarcadaDeGestionUsaLasMismasMarcas() throws Exception {
		ClaseAsistencia infantil5 = clase(100L, 3L, 9L, LocalDate.of(2026, 10, 5));
		when(claseAsistenciaRepository.findByTurnoIdInAndFechaBetween(any(), any(), any()))
				.thenReturn(List.of(infantil5));
		when(asistenciaAlumnoRepository.findByClaseIdIn(any())).thenReturn(List.of(new AsistenciaAlumno(100L, 1L, true)));

		List<String> lineas = ContenidoPdf.lineas(
				conHoy(() -> servicio.generarListadoAsistencia(2026, 10, List.of("Lunes"), null, null, true)));

		assertTrue(lineas.contains("7m 5001 11 Ana Prueba Uno 101 X"), String.join("\n", lineas));
		verify(claseAsistenciaRepository, times(1)).findByTurnoIdInAndFechaBetween(any(), any(), any());
		verify(asistenciaAlumnoRepository, times(1)).findByClaseIdIn(any());
	}

	@Test
	void asistenciaMarcadaSinListasQuedaVaciaConLeyenda() throws Exception {
		List<String> lineas = ContenidoPdf.lineas(
				conHoy(() -> servicio.generarListadoAsistenciaPorTurnos(2026, 10, List.of(9L), true)));

		assertTrue(lineas.contains("7m 5001 11 Ana Prueba Uno 101"), String.join("\n", lineas));
		assertTrue(lineas.stream().anyMatch(l -> l.contains(LEYENDA)), String.join("\n", lineas));
		// Sin clases guardadas no hay nada que leer en las filas.
		verifyNoInteractions(asistenciaAlumnoRepository);
	}

	/** Decisiones 455-463: una falta con motivo sale con su letra; sin motivo, «–». */
	@Test
	void asistenciaMarcadaPintaLaLetraDelMotivo() throws Exception {
		ClaseAsistencia infantil5 = clase(100L, 3L, 9L, LocalDate.of(2026, 10, 5));
		ClaseAsistencia infantil12 = clase(101L, 3L, 9L, LocalDate.of(2026, 10, 12));
		ClaseAsistencia infantil19 = clase(102L, 3L, 9L, LocalDate.of(2026, 10, 19));
		ClaseAsistencia infantil26 = clase(103L, 3L, 9L, LocalDate.of(2026, 10, 26));
		when(claseAsistenciaRepository.findByTurnoIdInAndFechaBetween(any(), any(), any()))
				.thenReturn(List.of(infantil5, infantil12, infantil19, infantil26));
		when(asistenciaAlumnoRepository.findByClaseIdIn(any())).thenReturn(List.of(
				new AsistenciaAlumno(100L, 1L, false, MotivoFalta.INJUSTIFICADA),
				new AsistenciaAlumno(101L, 1L, false, MotivoFalta.MEDICA),
				new AsistenciaAlumno(102L, 1L, false, MotivoFalta.FAMILIAR),
				new AsistenciaAlumno(103L, 1L, false, MotivoFalta.OTRA_JUSTIFICADA),
				new AsistenciaAlumno(100L, 3L, false), new AsistenciaAlumno(101L, 3L, true),
				// Un presente nunca lleva motivo, aunque se le pase.
				new AsistenciaAlumno(102L, 3L, true, MotivoFalta.MEDICA)));

		List<String> lineas = ContenidoPdf.lineas(
				conHoy(() -> servicio.generarListadoAsistenciaPorTurnos(2026, 10, List.of(9L), true)));

		assertTrue(lineas.contains("7m 5001 11 Ana Prueba Uno 101 I M F J"), String.join("\n", lineas));
		assertTrue(lineas.contains("10m NO 13 Carla Prueba Tres 103 – X X"), String.join("\n", lineas));
		assertTrue(lineas.stream().anyMatch(l -> l.contains(LEYENDA)), String.join("\n", lineas));
	}

	/**
	 * Lunes 12 festivo en la sede del grupo y sin lista: columna gris con «Festivo». Lunes 19 festivo pero con lista
	 * guardada: manda la lista y salen sus marcas. Una consulta de festivos para todas las páginas.
	 */
	@Test
	void asistenciaMarcadaPintaLaColumnaFestiva_yConListaMandaLaLista() throws Exception {
		datos.lunesInfantil.getGrupo().setSedeId(7L);
		ClaseAsistencia infantil19 = clase(100L, 3L, 9L, LocalDate.of(2026, 10, 19));
		when(claseAsistenciaRepository.findByTurnoIdInAndFechaBetween(any(), any(), any()))
				.thenReturn(List.of(infantil19));
		when(asistenciaAlumnoRepository.findByClaseIdIn(any())).thenReturn(List.of(
				new AsistenciaAlumno(100L, 1L, true), new AsistenciaAlumno(100L, 3L, false, MotivoFalta.MEDICA)));
		FestivoService.DiaFestivo puente = new FestivoService.DiaFestivo(1L, "Puente", TipoFestivo.RANGO,
				LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 19), 1, false);
		when(festivoService.festivosEn(any(), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31)))).thenReturn(
				java.util.Map.of(new FestivoService.SedeYFecha(7L, LocalDate.of(2026, 10, 12)), puente,
						new FestivoService.SedeYFecha(7L, LocalDate.of(2026, 10, 19)), puente,
						// Otra sede: no afecta a este grupo.
						new FestivoService.SedeYFecha(8L, LocalDate.of(2026, 10, 26)), puente));

		List<String> lineas = ContenidoPdf.lineas(
				conHoy(() -> servicio.generarListadoAsistenciaPorTurnos(2026, 10, List.of(9L), true)));
		String texto = String.join("\n", lineas);

		assertEquals(1, texto.split("Festivo", -1).length - 1, texto);
		assertTrue(lineas.stream().anyMatch(l -> l.startsWith("7m 5001 11 Ana Prueba Uno 101") && l.endsWith(" X")), texto);
		assertTrue(lineas.stream().anyMatch(l -> l.startsWith("10m NO 13 Carla Prueba Tres 103") && l.endsWith(" M")),
				texto);
		verify(festivoService, times(1)).festivosEn(any(), any(), any());
	}

	/** Gestión por día y deporte: letra de la falta con motivo, columna festiva sin lista y leyenda. */
	@Test
	void asistenciaMarcadaDeGestionPintaLetras_yLaColumnaFestiva() throws Exception {
		datos.lunesInfantil.getGrupo().setSedeId(7L);
		ClaseAsistencia infantil5 = clase(100L, 3L, 9L, LocalDate.of(2026, 10, 5));
		ClaseAsistencia infantil19 = clase(102L, 3L, 9L, LocalDate.of(2026, 10, 19));
		when(claseAsistenciaRepository.findByTurnoIdInAndFechaBetween(any(), any(), any()))
				.thenReturn(List.of(infantil5, infantil19));
		when(asistenciaAlumnoRepository.findByClaseIdIn(any())).thenReturn(List.of(
				new AsistenciaAlumno(100L, 1L, false, MotivoFalta.MEDICA), new AsistenciaAlumno(102L, 1L, true),
				new AsistenciaAlumno(100L, 3L, false), new AsistenciaAlumno(102L, 3L, false, MotivoFalta.FAMILIAR)));
		FestivoService.DiaFestivo fiesta = new FestivoService.DiaFestivo(1L, "Fiesta", TipoFestivo.DIA,
				LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12), 1, true);
		when(festivoService.festivosEn(any(), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31)))).thenReturn(
				java.util.Map.of(new FestivoService.SedeYFecha(7L, LocalDate.of(2026, 10, 12)), fiesta));

		List<String> lineas = ContenidoPdf.lineas(
				conHoy(() -> servicio.generarListadoAsistencia(2026, 10, List.of("Lunes"), null, null, true)));
		String texto = String.join("\n", lineas);

		assertTrue(lineas.stream().anyMatch(l -> l.startsWith("7m 5001 11 Ana Prueba Uno 101") && l.endsWith(" M X")), texto);
		assertTrue(lineas.stream().anyMatch(l -> l.startsWith("10m NO 13 Carla Prueba Tres 103") && l.endsWith(" – F")),
				texto);
		assertEquals(1, texto.split("Festivo", -1).length - 1, texto);
		assertTrue(lineas.stream().anyMatch(l -> l.contains(LEYENDA)), texto);
	}

	private static ClaseAsistencia clase(long id, long grupoId, long turnoId, LocalDate fecha) {
		ClaseAsistencia clase = new ClaseAsistencia(grupoId, turnoId, fecha, "17:00", "18:00", null,
				fecha.atStartOfDay());
		ReflectionTestUtils.setField(clase, "id", id);
		return clase;
	}

	// ------------------------------------------------------------------ cobros

	@Test
	void deudas() throws Exception {
		byte[] pdf = conHoy(() -> servicio.generarInformeDeudas(false));
		comprobar("deudas", pdf, Presentacion.de(ESCUELA, "Informe de Deudas de Alumnos", "Informe de Deudas"), FECHA);
		// Importes en es-ES («143,00 €»): el dorado compara por palabras y no ve la coma.
		String texto = String.join("\n", ContenidoPdf.lineas(pdf));
		assertTrue(texto.contains("TOTAL GENERAL: 143,00 €"), texto);
		assertTrue(texto.contains("12,50 €"), texto);
	}

	@Test
	void deudasCsv_conLaJvmEnEspanol_importesConPuntoYMismasColumnas() {
		Locale original = Locale.getDefault();
		String csv;
		try {
			Locale.setDefault(Locale.of("es", "ES"));
			csv = new String(servicio.generarInformeDeudasCSV(false), StandardCharsets.UTF_8);
		} finally {
			Locale.setDefault(original);
		}
		List<String> lineas = csv.lines().toList();
		assertEquals("\ufeffAlumno,Nº Expediente,Concepto,Fecha Asignación,Cantidad,Precio (€),Notas", lineas.get(0));
		List<String> filas = lineas.subList(1, lineas.size()).stream().filter(l -> !l.isEmpty()).toList();
		assertFalse(filas.isEmpty(), csv);
		for (String fila : filas) {
			List<String> campos = camposCsv(fila);
			assertEquals(7, campos.size(), fila);
			assertTrue(campos.get(5).matches("\\d+\\.\\d{2}"), fila);
		}
		assertTrue(filas.stream().anyMatch(f -> camposCsv(f).get(5).equals("12.50")), csv);
		String total = filas.get(filas.size() - 1);
		assertEquals("TOTAL GENERAL,,,,,143.00,", total);
	}

	/** Campos de una fila del CSV (comas fuera de comillas; {@code ""} dentro de comillas es una comilla). */
	private static List<String> camposCsv(String fila) {
		List<String> campos = new ArrayList<>();
		StringBuilder campo = new StringBuilder();
		boolean entreComillas = false;
		for (int i = 0; i < fila.length(); i++) {
			char c = fila.charAt(i);
			if (c == '"') {
				if (entreComillas && i + 1 < fila.length() && fila.charAt(i + 1) == '"') {
					campo.append('"');
					i++;
				} else {
					entreComillas = !entreComillas;
				}
			} else if (c == ',' && !entreComillas) {
				campos.add(campo.toString());
				campo.setLength(0);
			} else {
				campo.append(c);
			}
		}
		campos.add(campo.toString());
		return campos;
	}

	@Test
	void productos() throws Exception {
		byte[] pdf = conHoy(servicio::generarInformeProductos);
		comprobar("productos", pdf, Presentacion.de(ESCUELA, "Listado de Productos"), FECHA,
				"Antes de la decisión 315 el pie salía como «PÃ¡gina X de Y» (texto mal codificado); con el pie común, «Página X de Y».");
		// Decisión 315: el pie bien codificado.
		String texto = String.join(" | ", ContenidoPdf.lineas(pdf));
		assertTrue(texto.contains("8,50 €"), texto);
		assertTrue(texto.contains("Página 1 de 1"), texto);
		assertFalse(texto.contains("PÃ"), texto);
	}

	@Test
	void mensualidades() throws Exception {
		byte[] pdf = conHoy(() -> servicio.generarInformeMensualidades(false));
		comprobar("mensualidades", pdf,
				Presentacion.de(ESCUELA, "Informe de Mensualidades"), FECHA);
		String texto = String.join("\n", ContenidoPdf.lineas(pdf));
		assertTrue(texto.contains("TOTAL GENERAL: 167,00 €"), texto);
	}

	@Test
	void mensualidadesTaekwondo() throws Exception {
		byte[] pdf = conHoy(() -> servicio.generarInformeMensualidadesTaekwondo(false));
		comprobar("mensualidades-taekwondo", pdf,
				Presentacion.de(ESCUELA, "Informe de Mensualidades", "Informe de Mensualidades - Taekwondo"), FECHA);
		String texto = String.join("\n", ContenidoPdf.lineas(pdf));
		assertTrue(texto.contains("TOTAL GENERAL: 137,00 €"), texto);
	}

	@Test
	void mensualidadesKickboxing() throws Exception {
		byte[] pdf = conHoy(() -> servicio.generarInformeMensualidadesKickboxing(false));
		comprobar("mensualidades-kickboxing", pdf,
				Presentacion.de(ESCUELA, "Informe de Mensualidades", "Informe de Mensualidades - Kickboxing"), FECHA);
		String texto = String.join("\n", ContenidoPdf.lineas(pdf));
		assertTrue(texto.contains("TOTAL GENERAL: 30,00 €"), texto);
	}

	@Test
	void reservasDePlaza() throws Exception {
		comprobar("reservas-plaza", conHoy(() -> servicio.generarInformeReservasPlaza("2026/2027", false)),
				Presentacion.de(ESCUELA, "Informe de Reservas de Plaza",
						"Informe de Reservas de Plaza - 2026/2027"),
				FECHA);
	}

	@Test
	void listadoMensualidadMensual() throws Exception {
		byte[] pdf = conHoy(() -> servicio.generarListadoMensualidadMensual("2026-10", false));
		comprobar("listado-mensualidad-mensual", pdf,
				Presentacion.de(ESCUELA, "Listado Mensualidad OCTUBRE 2026"), FECHA,
				"Sin tarifas por sede (cuantía esperada = la de la inscripción).");
		String texto = String.join("\n", ContenidoPdf.lineas(pdf));
		assertTrue(texto.contains("55,00 €"), texto);
		assertTrue(texto.contains("25,00 €"), texto);
		assertFalse(texto.contains("EUR"), texto);
	}

	// ------------------------------------------------------------------ otros

	@Test
	void alumnasDpf() throws Exception {
		comprobar("alumnas-dpf", conHoy(() -> servicio.generarInformeAlumnasDpf(false)),
				Presentacion.de(ESCUELA, "Informe de Alumnas de D.P.F"), FECHA);
	}

	@Test
	void competidores() throws Exception {
		comprobar("competidores", conHoy(servicio::generarInformeCompetidores),
				Presentacion.de(ESCUELA, "Listado de Competidores"), FECHA);
	}

	@Test
	void convocatoria() throws Exception {
		Convocatoria convocatoria = new Convocatoria();
		convocatoria.setDisciplina(datos.catalogo.disciplina("TAEKWONDO"));
		convocatoria.setFechaConvocatoria(DatosPdfPrueba.fecha(2026, 11, 20));
		when(convocatoriaRepository.findById(7L)).thenReturn(Optional.of(convocatoria));
		when(convocatoriaService.obtenerReporteDeConvocatoria(7L)).thenReturn(new ArrayList<>(List.of(
				new AlumnoConvocatoriaReporteDTO(1L, "Ana Prueba Uno", 101, 5001, 11, "Infantil -40 kg", 38.5, true,
						"AMARILLO", "AMARILLO_NARANJA"),
				new AlumnoConvocatoriaReporteDTO(2L, "Bruno Prueba Dos", 102, 5002, 36, "Senior -80 kg", 79.0, false,
						"AZUL", "ROJO"),
				new AlumnoConvocatoriaReporteDTO(3L, "Carla Prueba Tres", 103, null, 13, null, null, false,
						"BLANCO_AMARILLO", "AMARILLO"))));
		comprobar("convocatoria", conHoy(() -> servicio.generarInformeConvocatoria(7L)),
				Presentacion.de(ESCUELA, "Convocatoria a Exámen 20/11/2026"), FECHA);
	}

	@Test
	void preinscripcionFirmada() throws Exception {
		var solicitud = datos.preinscripcion();
		comprobar("preinscripcion-firmada", conHoy(() -> servicio.generarPreinscripcionFirmada(solicitud)),
				Presentacion.de("Escuela Moi Kim Do · Calle Ficticia 1 · Temporada 2026-2027"),
				"«Firmado por … el <fecha>»: LocalDate.now(Europe/Madrid) sin Clock, fijada a " + ContenidoPdf.HOY
						+ " (presentación).");
	}

	@Test
	void preinscripcionPreview() throws Exception {
		comprobar("preinscripcion-preview",
				conHoy(() -> servicio.generarPreviewPreinscripcion("TAEKWONDO",
						"{\"contacto\":\"Calle Ficticia 1\",\"normas\":[\"Norma de vista previa.\"]}",
						"Trae el DNI el primer día.")),
				Presentacion.de("Escuela Moi Kim Do · Calle Ficticia 1 · Temporada 2026/2027"),
				"Vista previa con los datos de ejemplo fijos del generador; fecha de firma = HOY (presentación).");
	}
}
