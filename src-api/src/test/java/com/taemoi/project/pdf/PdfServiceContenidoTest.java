package com.taemoi.project.pdf;

import static com.taemoi.project.pdf.ContenidoPdf.comprobar;
import static com.taemoi.project.pdf.ContenidoPdf.conHoy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.taemoi.project.acceso.GestionPorSede;
import com.taemoi.project.dtos.response.AlumnoConvocatoriaReporteDTO;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.Convocatoria;
import com.taemoi.project.pdf.ContenidoPdf.Presentacion;
import com.taemoi.project.repositories.AlumnoDeporteRepository;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.ConvocatoriaRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.repositories.ProductoRepository;
import com.taemoi.project.repositories.TurnoRepository;
import com.taemoi.project.services.ConvocatoriaService;
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
	private final PDFServiceImpl servicio = new PDFServiceImpl();

	@BeforeEach
	void montar() {
		ReflectionTestUtils.setField(servicio, "alumnoRepository", alumnoRepository);
		ReflectionTestUtils.setField(servicio, "gestionPorSede", mock(GestionPorSede.class));
		ReflectionTestUtils.setField(servicio, "alumnoDeporteRepository", alumnoDeporteRepository);
		ReflectionTestUtils.setField(servicio, "turnoRepository", turnoRepository);
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

	// ------------------------------------------------------------------ cobros

	@Test
	void deudas() throws Exception {
		comprobar("deudas", conHoy(() -> servicio.generarInformeDeudas(false)),
				Presentacion.de(ESCUELA, "Informe de Deudas de Alumnos", "Informe de Deudas"), FECHA);
	}

	@Test
	void productos() throws Exception {
		comprobar("productos", conHoy(servicio::generarInformeProductos),
				Presentacion.de(ESCUELA, "Listado de Productos"), FECHA,
				"El pie sale hoy como «PÃ¡gina X de Y» (texto mal codificado en el generador).");
	}

	@Test
	void mensualidades() throws Exception {
		comprobar("mensualidades", conHoy(() -> servicio.generarInformeMensualidades(false)),
				Presentacion.de(ESCUELA, "Informe de Mensualidades"), FECHA);
	}

	@Test
	void mensualidadesTaekwondo() throws Exception {
		comprobar("mensualidades-taekwondo", conHoy(() -> servicio.generarInformeMensualidadesTaekwondo(false)),
				Presentacion.de(ESCUELA, "Informe de Mensualidades", "Informe de Mensualidades - Taekwondo"), FECHA);
	}

	@Test
	void mensualidadesKickboxing() throws Exception {
		comprobar("mensualidades-kickboxing", conHoy(() -> servicio.generarInformeMensualidadesKickboxing(false)),
				Presentacion.de(ESCUELA, "Informe de Mensualidades", "Informe de Mensualidades - Kickboxing"), FECHA);
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
		comprobar("listado-mensualidad-mensual",
				conHoy(() -> servicio.generarListadoMensualidadMensual("2026-10", false)),
				Presentacion.de(ESCUELA, "Listado Mensualidad OCTUBRE 2026"), FECHA,
				"Sin tarifas por sede (cuantía esperada = la de la inscripción).");
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
