package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import com.taemoi.project.controllers.AlumnoController;
import com.taemoi.project.controllers.GradoController;
import com.taemoi.project.dtos.AlumnoDTO;
import com.taemoi.project.dtos.ConvocatoriaDTO;
import com.taemoi.project.dtos.ProductoAlumnoDTO;
import com.taemoi.project.dtos.request.FinalizarPreinscripcionRequest;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.Disciplina;
import com.taemoi.project.entities.EstadoPreinscripcion;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.Grupo;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.Preinscripcion;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.entities.RolFamiliar;
import com.taemoi.project.entities.SistemaGrados;
import com.taemoi.project.entities.TipoGrado;
import com.taemoi.project.entities.TipoTarifa;
import com.taemoi.project.entities.Turno;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoDeporteRepository;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.DisciplinaRepository;
import com.taemoi.project.repositories.GradoRepository;
import com.taemoi.project.repositories.GrupoRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.PlantillaPreinscripcionRepository;
import com.taemoi.project.repositories.PreinscripcionRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.repositories.ProductoRepository;
import com.taemoi.project.repositories.SistemaGradosRepository;
import com.taemoi.project.repositories.TurnoRepository;
import com.taemoi.project.services.AlumnoDeporteService;
import com.taemoi.project.services.AlumnoService;
import com.taemoi.project.services.ContadorService;
import com.taemoi.project.services.ConvocatoriaService;
import com.taemoi.project.services.GradoService;
import com.taemoi.project.services.PDFService;
import com.taemoi.project.services.PreinscripcionService;
import com.taemoi.project.services.ProductoAlumnoService;
import com.taemoi.project.services.ProgresionGradosService;

/**
 * Cambio de llamadores a grados y disciplinas del catálogo (V26), de punta a punta para Taemoi
 * (organización 1) sobre la base migrada. Los valores esperados son LITERALES de
 * {@code docs/multi-escuela/valores-actuales.md} (no se calculan con las clases antiguas): alta,
 * siguiente grado, aptitud para examen, promoción al pagar, convocatoria, informes por grado, filtro
 * por grado, preinscripción y disciplinas sin grados. Únicos cambios esperados respecto al código
 * anterior (decisión del usuario): los exámenes intermedios y PUM promueven al grado correcto y la
 * recompensa «ROJO BORDADO» promueve a ROJO.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CambioGradosDisciplinasIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG_TAEMOI = 1L;
	private static final int ANIO = LocalDate.now().getYear();

	/** valores-actuales.md §2: siguiente grado. «—» = null. */
	private static final Map<String, String> SIGUIENTE_TKD_MENOR = mapa(
			"BLANCO", "BLANCO_AMARILLO", "BLANCO_AMARILLO", "AMARILLO", "AMARILLO", "AMARILLO_NARANJA",
			"AMARILLO_NARANJA", "NARANJA", "NARANJA", "NARANJA_VERDE", "NARANJA_VERDE", "VERDE", "VERDE", "VERDE_AZUL",
			"VERDE_AZUL", "AZUL", "AZUL", "AZUL_ROJO", "AZUL_ROJO", "ROJO", "ROJO", "ROJO_NEGRO_1_PUM",
			"ROJO_NEGRO_1_PUM", "ROJO_NEGRO_2_PUM", "ROJO_NEGRO_2_PUM", "ROJO_NEGRO_3_PUM", "ROJO_NEGRO_3_PUM", null,
			"NEGRO_1_DAN", null, "NEGRO_2_DAN", null, "NEGRO_3_DAN", null, "NEGRO_4_DAN", null, "NEGRO_5_DAN", null);
	/** Taekwondo adultos y kickboxing (menores y adultos). */
	private static final Map<String, String> SIGUIENTE_ADULTO_Y_KB = mapa(
			"BLANCO", "AMARILLO", "BLANCO_AMARILLO", "AMARILLO", "AMARILLO", "NARANJA", "AMARILLO_NARANJA", "NARANJA",
			"NARANJA", "VERDE", "NARANJA_VERDE", "VERDE", "VERDE", "AZUL", "VERDE_AZUL", "AZUL", "AZUL", "ROJO",
			"AZUL_ROJO", "ROJO", "ROJO", "NEGRO_1_DAN", "ROJO_NEGRO_1_PUM", null, "ROJO_NEGRO_2_PUM", null,
			"ROJO_NEGRO_3_PUM", null, "NEGRO_1_DAN", "NEGRO_2_DAN", "NEGRO_2_DAN", "NEGRO_3_DAN", "NEGRO_3_DAN",
			"NEGRO_4_DAN", "NEGRO_4_DAN", "NEGRO_5_DAN", "NEGRO_5_DAN", null);

	/** valores-actuales.md §3: meses mínimos {menores, adultos}; null = no apto. */
	private static final Map<String, Integer[]> MESES = new LinkedHashMap<>();

	static {
		MESES.put("BLANCO", new Integer[] { 2, 3 });
		MESES.put("BLANCO_AMARILLO", new Integer[] { 2, null });
		MESES.put("AMARILLO", new Integer[] { 3, 5 });
		MESES.put("AMARILLO_NARANJA", new Integer[] { 3, null });
		MESES.put("NARANJA", new Integer[] { 4, 6 });
		MESES.put("NARANJA_VERDE", new Integer[] { 4, null });
		MESES.put("VERDE", new Integer[] { 6, 8 });
		MESES.put("VERDE_AZUL", new Integer[] { 6, null });
		MESES.put("AZUL", new Integer[] { 8, 10 });
		MESES.put("AZUL_ROJO", new Integer[] { 10, null });
		MESES.put("ROJO", new Integer[] { 12, 12 });
		MESES.put("ROJO_NEGRO_1_PUM", new Integer[] { 24, null });
		MESES.put("ROJO_NEGRO_2_PUM", new Integer[] { 36, null });
		MESES.put("ROJO_NEGRO_3_PUM", new Integer[] { null, null });
		MESES.put("NEGRO_1_DAN", new Integer[] { null, 24 });
		MESES.put("NEGRO_2_DAN", new Integer[] { null, 36 });
		MESES.put("NEGRO_3_DAN", new Integer[] { null, 48 });
		MESES.put("NEGRO_4_DAN", new Integer[] { null, 60 });
		MESES.put("NEGRO_5_DAN", new Integer[] { null, null });
	}

	/**
	 * valores-actuales.md §1: grado que concede cada producto al pagarlo, con los dos cambios
	 * intencionados (intermedios y PUM al grado correcto; recompensa ROJO BORDADO a ROJO).
	 */
	private static final Map<String, String> GRADO_POR_PRODUCTO = new LinkedHashMap<>();

	static {
		String[][] filas = { { "DERECHO A EXAMEN BLANCO/AMARILLO", "BLANCO_AMARILLO" },
				{ "DERECHO A EXAMEN AMARILLO", "AMARILLO" }, { "DERECHO A EXAMEN AMARILLO/NARANJA", "AMARILLO_NARANJA" },
				{ "DERECHO A EXAMEN NARANJA", "NARANJA" }, { "DERECHO A EXAMEN NARANJA/VERDE", "NARANJA_VERDE" },
				{ "DERECHO A EXAMEN VERDE", "VERDE" }, { "DERECHO A EXAMEN VERDE/AZUL", "VERDE_AZUL" },
				{ "DERECHO A EXAMEN AZUL", "AZUL" }, { "DERECHO A EXAMEN AZUL/ROJO", "AZUL_ROJO" },
				{ "DERECHO A EXAMEN ROJO", "ROJO" }, { "DERECHO A EXAMEN ROJO/NEGRO 1º PUM", "ROJO_NEGRO_1_PUM" },
				{ "DERECHO A EXAMEN ROJO/NEGRO 2º PUM", "ROJO_NEGRO_2_PUM" },
				{ "DERECHO A EXAMEN NEGRO 1º DAN", "NEGRO_1_DAN" }, { "DERECHO A EXAMEN NEGRO 2º DAN", "NEGRO_2_DAN" },
				{ "DERECHO A EXAMEN NEGRO 3º DAN", "NEGRO_3_DAN" }, { "DERECHO A EXAMEN ROJO BORDADO", "ROJO" },
				{ "DERECHO CAMBIO A BLANCO-AMARILLO RECOMPENSA", "BLANCO_AMARILLO" },
				{ "DERECHO DE CAMBIO A AMARILLO POR RECOMPENSA", "AMARILLO" },
				{ "DERECHO DE CAMBIO A AMARILLO-NARANJA POR RECOMPENSA", "AMARILLO_NARANJA" },
				{ "DERECHO DE CAMBIO A NARANJA POR RECOMPENSA", "NARANJA" },
				{ "DERECHO DE CAMBIO A NARANJA-VERDE POR RECOMPENSA", "NARANJA_VERDE" },
				{ "DERECHO DE CAMBIO A VERDE RECOMPENSA", "VERDE" },
				{ "DERECHO DE CAMBIO A VERDE-AZUL POR RECOMPENSA", "VERDE_AZUL" },
				{ "DERECHO DE CAMBIO A AZUL POR RECOMPENSA", "AZUL" },
				{ "DERECHO DE CAMBIO A AZUL-ROJO POR RECOMPENSA", "AZUL_ROJO" },
				{ "DERECHO DE CAMBIO A ROJO POR RECOMPENSA", "ROJO" },
				{ "DERECHO DE CAMBIO A ROJO BORDADO POR RECOMPENSA", "ROJO" } };
		for (String[] fila : filas) {
			GRADO_POR_PRODUCTO.put(fila[0], fila[1]);
		}
	}

	@Autowired AlumnoController alumnoController;
	@Autowired GradoController gradoController;
	@Autowired AlumnoService alumnoService;
	@Autowired AlumnoDeporteService alumnoDeporteService;
	@Autowired ProductoAlumnoService productoAlumnoService;
	@Autowired ConvocatoriaService convocatoriaService;
	@Autowired PreinscripcionService preinscripcionService;
	@Autowired PDFService pdfService;
	@Autowired GradoService gradoService;
	@Autowired ProgresionGradosService progresion;
	@Autowired AlumnoRepository alumnos;
	@Autowired AlumnoDeporteRepository alumnoDeportes;
	@Autowired GradoRepository grados;
	@Autowired DisciplinaRepository disciplinas;
	@Autowired SistemaGradosRepository sistemas;
	@Autowired com.taemoi.project.repositories.ConvocatoriaRepository convocatorias;
	@Autowired com.taemoi.project.repositories.AlumnoConvocatoriaRepository alumnosConvocatoria;
	@Autowired ContadorService contadores;
	@Autowired ProductoRepository productos;
	@Autowired ProductoAlumnoRepository productosAlumno;
	@Autowired GrupoRepository grupos;
	@Autowired TurnoRepository turnos;
	@Autowired PlantillaPreinscripcionRepository plantillas;
	@Autowired PreinscripcionRepository preinscripciones;
	@Autowired OrganizacionRepository organizaciones;
	@Autowired TransactionTemplate tx;
	@Autowired DataSource dataSource;
	@Autowired JdbcTemplate jdbc;

	final List<Long> productosInsertados = new ArrayList<>();
	long org2;

	@BeforeAll
	void prepararProductosYOtraEscuela() throws Exception {
		// En producción los productos los crea la gestión: se asegura uno por concepto y se
		// clasifican con la resincronización de V24 (incluida en V26).
		for (String concepto : GRADO_POR_PRODUCTO.keySet()) {
			boolean existe = jdbc.queryForObject("""
					SELECT COUNT(*) FROM producto
					WHERE organizacion_id = 1 AND CAST(concepto AS BINARY) = CAST(? AS BINARY)""",
					Integer.class, concepto) > 0;
			if (!existe) {
				jdbc.update("INSERT INTO producto (concepto, precio, organizacion_id) VALUES (?, 15.0, 1)", concepto);
				productosInsertados.add(jdbc.queryForObject("""
						SELECT MAX(id) FROM producto
						WHERE organizacion_id = 1 AND CAST(concepto AS BINARY) = CAST(? AS BINARY)""", Long.class,
						concepto));
			}
		}
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion, new EncodedResource(
					new ClassPathResource("db/resync/resync_catalogo_v24.sql"), StandardCharsets.UTF_8));
		}

		Organizacion otra = new Organizacion();
		otra.setSlug("it-cambio-grados-" + UUID.randomUUID().toString().substring(0, 8));
		otra.setNombre("Escuela sin catálogo");
		org2 = TenantContext.calcularCon(ORG_TAEMOI, () -> organizaciones.save(otra).getId());
	}

	@AfterAll
	void limpiarProductos() {
		for (Long id : productosInsertados) {
			jdbc.update("DELETE ac FROM alumno_convocatoria ac JOIN producto_alumno pa ON pa.id = ac.producto_alumno_id"
					+ " WHERE pa.producto_id = ?", id);
			jdbc.update("DELETE FROM producto_alumno WHERE producto_id = ?", id);
			jdbc.update("UPDATE grado SET producto_examen_id = NULL WHERE producto_examen_id = ?", id);
			jdbc.update("UPDATE grado SET producto_recompensa_id = NULL WHERE producto_recompensa_id = ?", id);
			jdbc.update("DELETE FROM producto WHERE id = ?", id);
		}
	}

	@AfterEach
	void sinFugas() {
		SecurityContextHolder.clearContext();
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// =============================================================================
	// Alta
	// =============================================================================

	@Test
	void alta_taekwondoMenorYAdultoYKickboxing_gradoEnElSistemaDeSuDisciplina() {
		long menor = altaCon("TKD-MENOR", ANIO - 10, "TAEKWONDO", "AMARILLO_NARANJA", "INFANTIL");
		long adulto = altaCon("TKD-ADULTO", ANIO - 30, "TAEKWONDO", "NEGRO_1_DAN", "ADULTO");
		long kb = altaCon("KB", ANIO - 30, "KICKBOXING", "ROJO", "KICKBOXING");

		assertInscripcion(menor, "TAEKWONDO", "AMARILLO_NARANJA", "Amarillo-Naranja");
		assertInscripcion(adulto, "TAEKWONDO", "NEGRO_1_DAN", "Negro-1-Dan");
		assertInscripcion(kb, "KICKBOXING", "ROJO", "MARRÓN");
		// La respuesta mantiene el código de grado de siempre.
		assertEquals("ROJO", alumnoDto(kb).getGrado());
	}

	@Test
	void alta_camposDeDeporteUnico_kickboxingEmpiezaEnBlancoDeSuSistema() {
		String json = """
				{"nombre":"UNICO","apellidos":"KB-%s","fechaNacimiento":"1990-03-04","direccion":"Calle 3",
				 "telefono":600000002,"email":"unico-%s@example.test","deporte":"KICKBOXING","tipoTarifa":"KICKBOXING"}
				""".formatted(sufijo(), sufijo());
		long id = crear(json).getId();
		assertInscripcion(id, "KICKBOXING", "BLANCO", "Blanco");
	}

	@Test
	void alta_pilatesYDefensaPersonal_sinGrado_yUnGradoParaEllasEsUn400() {
		long id = crear("""
				{"nombre":"SIN","apellidos":"GRADOS-%s","fechaNacimiento":"1990-03-04","direccion":"Calle 4",
				 "telefono":600000003,"email":"sin-grados-%s@example.test","rolFamiliar":"NINGUNO",
				 "deportesInicial":[
				   {"deporte":"PILATES","tipoTarifa":"PILATES","cuantiaTarifa":30.0},
				   {"deporte":"DEFENSA_PERSONAL_FEMENINA","tipoTarifa":"DEFENSA_PERSONAL_FEMENINA","cuantiaTarifa":25.0}
				 ]}""".formatted(sufijo(), sufijo())).getId();
		for (String deporte : List.of("PILATES", "DEFENSA_PERSONAL_FEMENINA")) {
			Map<String, Object> ad = inscripcion(id, deporte);
			assertNull(ad.get("grado_id"), deporte);
			assertEquals(deporte, ad.get("disciplina"), deporte);
			assertEquals(Boolean.FALSE, ad.get("apto_para_examen"), deporte);
		}

		// Cambio documentado: antes se guardaba un grado de taekwondo en una disciplina sin grados.
		ResponseEntity<?> conGrado = llamarCrear("""
				{"nombre":"PILATES","apellidos":"CON-GRADO","fechaNacimiento":"1990-03-04","direccion":"Calle 5",
				 "telefono":600000004,"email":"pilates-grado-%s@example.test",
				 "deportesInicial":[{"deporte":"PILATES","grado":"BLANCO","tipoTarifa":"PILATES","cuantiaTarifa":30.0}]}
				""".formatted(sufijo()));
		assertEquals(HttpStatus.BAD_REQUEST, conGrado.getStatusCode());
		assertTrue(String.valueOf(conGrado.getBody()).contains("no tiene sistema de grados"),
				String.valueOf(conGrado.getBody()));
	}

	// =============================================================================
	// Progresión y aptitud (valores literales)
	// =============================================================================

	@Test
	void siguienteGrado_porDisciplinaYEdad_igualQueLaTablaDeValoresActuales() {
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			for (String codigo : MESES.keySet()) {
				// Taekwondo: menor = nacido en A-13; adulto = A-14 (cumple 14 este año).
				assertEquals(SIGUIENTE_TKD_MENOR.get(codigo), siguiente("TAEKWONDO", codigo, ANIO - 13), "TKD menor " + codigo);
				assertEquals(SIGUIENTE_ADULTO_Y_KB.get(codigo), siguiente("TAEKWONDO", codigo, ANIO - 14), "TKD adulto " + codigo);
				// Kickboxing: A-14 todavía es menor (edad adulta 15) y A-15 adulto; el mismo mapa.
				assertEquals(SIGUIENTE_ADULTO_Y_KB.get(codigo), siguiente("KICKBOXING", codigo, ANIO - 14), "KB menor " + codigo);
				assertEquals(SIGUIENTE_ADULTO_Y_KB.get(codigo), siguiente("KICKBOXING", codigo, ANIO - 15), "KB adulto " + codigo);
			}
		});
	}

	@Test
	void aptitudParaExamen_mesesPorGrupoDeEdadYDisciplina_igualQueLaTablaDeValoresActuales() {
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			int comprobadas = 0;
			for (Map.Entry<String, Integer[]> fila : MESES.entrySet()) {
				String codigo = fila.getKey();
				// {disciplina, año de nacimiento, ¿menor?}: 14 años este año = adulto en TKD y menor en KB.
				Object[][] casos = { { "TAEKWONDO", ANIO - 13, true }, { "TAEKWONDO", ANIO - 14, false },
						{ "KICKBOXING", ANIO - 14, true }, { "KICKBOXING", ANIO - 15, false } };
				for (Object[] caso : casos) {
					String disciplina = (String) caso[0];
					int nacimiento = (Integer) caso[1];
					Integer meses = fila.getValue()[(Boolean) caso[2] ? 0 : 1];
					String etiqueta = disciplina + " " + nacimiento + " " + codigo;
					if (meses == null) {
						assertFalse(apto(disciplina, codigo, nacimiento, LocalDate.now().minusYears(30)), etiqueta);
					} else {
						assertTrue(apto(disciplina, codigo, nacimiento, LocalDate.now().minusMonths(meses)), etiqueta);
						assertFalse(apto(disciplina, codigo, nacimiento, LocalDate.now().minusMonths(meses).plusDays(2)),
								etiqueta + " (antes de tiempo)");
					}
					comprobadas++;
				}
			}
			assertEquals(19 * 4, comprobadas);
		});
	}

	// =============================================================================
	// Promoción al pagar un producto de examen o recompensa
	// =============================================================================

	@Test
	void pagarCadaProductoDeExamenORecompensa_promueveAlGradoQueConcede_enCadaDisciplina() {
		long tkd = altaCon("PAGO-TKD", ANIO - 10, "TAEKWONDO", "BLANCO", "INFANTIL");
		long kb = altaCon("PAGO-KB", ANIO - 30, "KICKBOXING", "BLANCO", "KICKBOXING");
		for (Map.Entry<String, String> fila : GRADO_POR_PRODUCTO.entrySet()) {
			for (Object[] caso : new Object[][] { { tkd, "TAEKWONDO" }, { kb, "KICKBOXING" } }) {
				long alumnoId = (Long) caso[0];
				String disciplina = (String) caso[1];
				ponerGrado(alumnoId, disciplina, "BLANCO");
				Long productoId = productoCanonico(fila.getKey());
				pagar(cargo(alumnoId, disciplina, productoId, fila.getKey() + " - " + disciplina));
				assertInscripcion(alumnoId, disciplina, fila.getValue(), null);
			}
		}
	}

	@Test
	void pagarUnCargoHistoricoDeProductoBorrado_resuelvePorElConceptoExacto() {
		long tkd = altaCon("HISTORICO", ANIO - 10, "TAEKWONDO", "BLANCO", "INFANTIL");
		pagar(cargo(tkd, "TAEKWONDO", null, "DERECHO A EXAMEN VERDE/AZUL - TAEKWONDO"));
		assertInscripcion(tkd, "TAEKWONDO", "VERDE_AZUL", "Verde-Azul");

		// Un cargo que no concede grado no cambia nada.
		pagar(cargo(tkd, "TAEKWONDO", null, "CAMISETA TAEMOI - TAEKWONDO"));
		assertInscripcion(tkd, "TAEKWONDO", "VERDE_AZUL", "Verde-Azul");
	}

	// =============================================================================
	// Convocatoria
	// =============================================================================

	@Test
	void convocatoriaDeKickboxing_creaConDisciplina_promueveEnElSistemaDeKickboxing() {
		long kb = altaCon("CONVOCATORIA", ANIO - 30, "KICKBOXING", "AZUL", "KICKBOXING");
		ConvocatoriaDTO dto = new ConvocatoriaDTO();
		dto.setFechaConvocatoria(java.sql.Date.valueOf(LocalDate.now().plusDays(10)));
		dto.setDeporte(Deporte.KICKBOXING);
		long convocatoria = TenantContext.calcularCon(ORG_TAEMOI, () -> convocatoriaService.crearConvocatoria(dto).getId());
		assertEquals("KICKBOXING", jdbc.queryForObject("""
				SELECT d.codigo FROM convocatoria c JOIN disciplina d ON d.id = c.disciplina_id WHERE c.id = ?""",
				String.class, convocatoria));

		// Como en una petición (open-in-view): las colecciones perezosas se leen dentro de una transacción.
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> tx.executeWithoutResult(s -> {
			alumnoDeporteService.actualizarAptoParaExamen(kb, Deporte.KICKBOXING, true);
			var inscrito = alumnoService.agregarAlumnoAConvocatoria(kb, convocatoria, false, false);
			assertEquals(TipoGrado.AZUL, inscrito.getGradoActual());
			assertEquals(TipoGrado.ROJO, inscrito.getGradoSiguiente());
			convocatoriaService.actualizarGradosDeConvocatoria(convocatoria);
			// Ya procesada: el alumno tiene el grado al que se presentaba.
			assertTrue(convocatoriaService.obtenerConvocatoriasDeAlumno(kb).isEmpty());
		}));
		assertInscripcion(kb, "KICKBOXING", "ROJO", "MARRÓN");
		String pdf = texto(TenantContext.calcularCon(ORG_TAEMOI,
				() -> tx.execute(s -> pdfService.generarInformeConvocatoria(convocatoria))));
		assertTrue(pdf.contains("DE AZUL"), pdf);
		assertTrue(pdf.contains("A MARRÓN"), pdf);
	}

	// =============================================================================
	// Informes por grado y filtro por grado
	// =============================================================================

	@Test
	void informesPorGrado_unApartadoPorGrado_yRojoDeKickboxingEsMarron() {
		altaCon("PDF-KB-1", ANIO - 30, "KICKBOXING", "ROJO", "KICKBOXING");
		altaCon("PDF-KB-2", ANIO - 30, "KICKBOXING", "ROJO", "KICKBOXING");
		altaCon("PDF-TKD", ANIO - 30, "TAEKWONDO", "ROJO", "ADULTO");

		String kb = texto(TenantContext.calcularCon(ORG_TAEMOI, () -> pdfService.generarInformeKickboxingPorGrado(true)));
		assertEquals(1, apariciones(kb, "MARRÓN"), kb);
		assertFalse(java.util.regex.Pattern.compile("(?m)^\\s*Rojo(\\s|$)").matcher(kb).find(),
				"en kickboxing el rojo se llama MARRÓN: " + kb);
		String tkd = texto(TenantContext.calcularCon(ORG_TAEMOI, () -> pdfService.generarInformeTaekwondoPorGrado(true)));
		assertEquals(0, apariciones(tkd, "MARRÓN"), tkd);
		assertTrue(apariciones(tkd, "Rojo") >= 1, tkd);
		String ambos = texto(TenantContext.calcularCon(ORG_TAEMOI, () -> pdfService.generarInformeAlumnosPorGrado(true)));
		assertEquals(1, apariciones(ambos, "MARRÓN"), ambos);
	}

	/**
	 * Orden de siempre en los informes (del grado más alto al más bajo): DAN, después PUM, después rojo,
	 * en taekwondo y en kickboxing (aunque en kickboxing el orden de progresión ponga los PUM al final).
	 */
	@Test
	void informesPorGradoYConvocatoria_mantienenElOrdenDeSiempre_DanAntesQuePumAntesQueRojo() {
		Object[][] disciplinas = { { "TAEKWONDO", "ADULTO", "Rojo", "ROJO" }, { "KICKBOXING", "KICKBOXING", "MARRÓN", "MARRÓN" } };
		for (Object[] d : disciplinas) {
			String disciplina = (String) d[0];
			String tarifa = (String) d[1];
			long dan = altaCon("ORDEN-DAN", ANIO - 30, disciplina, "NEGRO_1_DAN", tarifa);
			long pum = altaCon("ORDEN-PUM", ANIO - 30, disciplina, "ROJO_NEGRO_1_PUM", tarifa);
			long rojo = altaCon("ORDEN-ROJO", ANIO - 30, disciplina, "ROJO", tarifa);

			String porGrado = texto(TenantContext.calcularCon(ORG_TAEMOI, () -> disciplina.equals("TAEKWONDO")
					? pdfService.generarInformeTaekwondoPorGrado(true)
					: pdfService.generarInformeKickboxingPorGrado(true)));
			int iDan = porGrado.indexOf("Negro-1-Dan");
			int iPum = porGrado.indexOf("Rojo-Negro-1-Pum");
			int iRojo = java.util.regex.Pattern.compile("(?m)^\\s*" + d[2] + "(\\s|$)").matcher(porGrado).results()
					.mapToInt(java.util.regex.MatchResult::start).findFirst().orElse(-1);
			assertTrue(iDan >= 0 && iPum > iDan && iRojo > iPum, disciplina + " por grado: " + porGrado);

			Deporte deporte = Deporte.valueOf(disciplina);
			ConvocatoriaDTO dto = new ConvocatoriaDTO();
			dto.setFechaConvocatoria(java.sql.Date.valueOf(LocalDate.now().plusDays(20)));
			dto.setDeporte(deporte);
			long convocatoria = TenantContext.calcularCon(ORG_TAEMOI, () -> convocatoriaService.crearConvocatoria(dto).getId());
			TenantContext.ejecutarCon(ORG_TAEMOI, () -> tx.executeWithoutResult(s -> {
				// Filas como las que dejan convocatorias anteriores (en kickboxing un PUM no tiene siguiente).
				inscribir(convocatoria, rojo, deporte, TipoGrado.ROJO, TipoGrado.NEGRO_1_DAN);
				inscribir(convocatoria, pum, deporte, TipoGrado.ROJO_NEGRO_1_PUM, TipoGrado.ROJO_NEGRO_2_PUM);
				inscribir(convocatoria, dan, deporte, TipoGrado.NEGRO_1_DAN, TipoGrado.NEGRO_2_DAN);
			}));
			String pdf = texto(TenantContext.calcularCon(ORG_TAEMOI,
					() -> tx.execute(s -> pdfService.generarInformeConvocatoria(convocatoria))));
			int cDan = pdf.indexOf("DE NEGRO-1-DAN");
			int cPum = pdf.indexOf("DE ROJO-NEGRO-1-PUM");
			int cRojo = pdf.indexOf("DE " + d[3] + "\n") >= 0 ? pdf.indexOf("DE " + d[3] + "\n")
					: java.util.regex.Pattern.compile("DE " + d[3] + "(?!-)").matcher(pdf).results()
							.mapToInt(java.util.regex.MatchResult::start).findFirst().orElse(-1);
			assertTrue(cDan >= 0 && cPum > cDan && cRojo > cPum, disciplina + " convocatoria: " + pdf);
		}
	}

	private void inscribir(long convocatoriaId, long alumnoId, Deporte deporte, TipoGrado actual, TipoGrado siguiente) {
		com.taemoi.project.entities.AlumnoConvocatoria ac = new com.taemoi.project.entities.AlumnoConvocatoria();
		ac.setAlumno(alumnos.findById(alumnoId).orElseThrow());
		ac.setConvocatoria(convocatorias.findById(convocatoriaId).orElseThrow());
		ac.setAlumnoDeporte(alumnoDeportes.findByAlumnoIdAndDeporte(alumnoId, deporte).orElseThrow());
		ac.setGradoActual(actual);
		ac.setGradoSiguiente(siguiente);
		ac.setCuantiaExamen(15.0);
		ac.setPagado(false);
		alumnosConvocatoria.save(ac);
	}

	@Test
	void filtroPorGrado_conElIdDeApiGrados_encuentraTambienAlumnosDeKickboxing() {
		String apellido = "FILTRO-" + sufijo();
		long kb = altaConApellido(apellido, ANIO - 30, "KICKBOXING", "VERDE", "KICKBOXING");
		long tkd = altaConApellido(apellido, ANIO - 30, "TAEKWONDO", "VERDE", "ADULTO");
		long otro = altaConApellido(apellido, ANIO - 30, "KICKBOXING", "AZUL", "KICKBOXING");
		autenticar();
		Long verdeApi = TenantContext.calcularCon(ORG_TAEMOI, () -> gradoController.obtenerTodosLosGrados(null).getBody()
				.stream().filter(g -> g.getTipoGrado() == TipoGrado.VERDE).findFirst().orElseThrow().getId());

		List<Long> encontrados = TenantContext.calcularCon(ORG_TAEMOI, () -> alumnoService
				.obtenerAlumnosFiltrados(apellido, verdeApi, null, false).stream().map(Alumno::getId).toList());
		assertEquals(Set.of(kb, tkd), Set.copyOf(encontrados));
		assertFalse(encontrados.contains(otro));
	}

	// =============================================================================
	// /api/grados
	// =============================================================================

	@Test
	void apiGrados_sinParametroTaekwondoComoSiempre_yPorDisciplina() {
		autenticar();
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			List<Grado> porDefecto = gradoController.obtenerTodosLosGrados(null).getBody();
			assertEquals(19, porDefecto.size());
			assertEquals(Arrays.stream(TipoGrado.values()).toList(),
					porDefecto.stream().sorted(java.util.Comparator.comparing(Grado::getOrden))
							.map(Grado::getTipoGrado).toList());
			assertEquals(porDefecto.stream().map(Grado::getId).toList(),
					gradoController.obtenerTodosLosGrados("TAEKWONDO").getBody().stream().map(Grado::getId).toList());
			List<Grado> kb = gradoController.obtenerTodosLosGrados("kickboxing").getBody();
			assertEquals(19, kb.size());
			assertTrue(kb.stream().noneMatch(g -> porDefecto.stream().anyMatch(t -> t.getId().equals(g.getId()))),
					"los grados de kickboxing son los de su sistema");
			assertEquals(List.of(), gradoController.obtenerTodosLosGrados("PILATES").getBody());
			assertEquals(List.of(), gradoController.obtenerTodosLosGrados("NO_EXISTE").getBody());

			String menor = LocalDate.of(ANIO - 13, 6, 1).toString();
			String adulto = LocalDate.of(ANIO - 14, 6, 1).toString();
			List<String> tkdMenor = List.of("BLANCO", "BLANCO_AMARILLO", "AMARILLO", "AMARILLO_NARANJA", "NARANJA",
					"NARANJA_VERDE", "VERDE", "VERDE_AZUL", "AZUL", "AZUL_ROJO", "ROJO", "ROJO_NEGRO_1_PUM",
					"ROJO_NEGRO_2_PUM", "ROJO_NEGRO_3_PUM");
			List<String> adultoOKb = List.of("BLANCO", "AMARILLO", "NARANJA", "VERDE", "AZUL", "ROJO", "NEGRO_1_DAN",
					"NEGRO_2_DAN", "NEGRO_3_DAN", "NEGRO_4_DAN", "NEGRO_5_DAN");
			assertEquals(tkdMenor, gradoController.obtenerGradosDisponiblesPorFechaNacimiento(menor, "TAEKWONDO").getBody());
			assertEquals(adultoOKb, gradoController.obtenerGradosDisponiblesPorFechaNacimiento(adulto, "TAEKWONDO").getBody());
			assertEquals(adultoOKb, gradoController.obtenerGradosDisponiblesPorFechaNacimiento(menor, "KICKBOXING").getBody());
			// Contrato heredado: sin grados o desconocido = taekwondo.
			assertEquals(tkdMenor, gradoController.obtenerGradosDisponiblesPorFechaNacimiento(menor, "PILATES").getBody());
			assertEquals(adultoOKb, gradoController.obtenerGradosDisponiblesPorFechaNacimiento(adulto, "XX").getBody());
		});
	}

	// =============================================================================
	// Preinscripción -> alumno con grado
	// =============================================================================

	@Test
	void finalizarPreinscripcionDeKickboxing_creaElAlumnoConElGradoDeSuSistema() {
		String referencia = preinscripcionPendiente("KICKBOXING", "Kickboxing preinscripcion");
		var datos = new FinalizarPreinscripcionRequest.DatosAltaDeporte(TipoTarifa.KICKBOXING, 30.0,
				RolFamiliar.NINGUNO, null, TipoGrado.AMARILLO, LocalDate.now().minusMonths(1));
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> preinscripcionService.finalizar(referencia, new FinalizarPreinscripcionRequest(
				FinalizarPreinscripcionRequest.AccionAlumno.CREAR_NUEVO, null, Set.of(), false, null, datos)));

		Long alumnoId = jdbc.queryForObject("SELECT alumno_id FROM preinscripcion WHERE referencia = ?", Long.class,
				referencia);
		assertInscripcion(alumnoId, "KICKBOXING", "AMARILLO", "Amarillo");

		// Un grado intermedio no es válido en kickboxing (mismo mensaje que antes).
		String otra = preinscripcionPendiente("KICKBOXING", "Kickboxing preinscripcion 2");
		var intermedio = new FinalizarPreinscripcionRequest.DatosAltaDeporte(TipoTarifa.KICKBOXING, 30.0,
				RolFamiliar.NINGUNO, null, TipoGrado.AMARILLO_NARANJA, LocalDate.now());
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> TenantContext.ejecutarCon(ORG_TAEMOI, () -> preinscripcionService.finalizar(otra,
						new FinalizarPreinscripcionRequest(FinalizarPreinscripcionRequest.AccionAlumno.CREAR_NUEVO, null,
								Set.of(), false, null, intermedio))));
		assertEquals("El grado no es válido para Kickboxing.", error.getMessage());
	}

	// =============================================================================
	// Disciplinas sin grados
	// =============================================================================

	@Test
	void pilatesYDefensaPersonal_sinExamenNiGrados_comoAntes() {
		long id = crear("""
				{"nombre":"PILATES","apellidos":"SOLO-%s","fechaNacimiento":"1990-03-04","direccion":"Calle 6",
				 "telefono":600000005,"email":"solo-pilates-%s@example.test","rolFamiliar":"NINGUNO",
				 "deportesInicial":[{"deporte":"PILATES","tipoTarifa":"PILATES","cuantiaTarifa":30.0},
				   {"deporte":"DEFENSA_PERSONAL_FEMENINA","tipoTarifa":"DEFENSA_PERSONAL_FEMENINA","cuantiaTarifa":25.0}]}
				""".formatted(sufijo(), sufijo())).getId();
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			for (Deporte deporte : List.of(Deporte.PILATES, Deporte.DEFENSA_PERSONAL_FEMENINA)) {
				AlumnoDeporte ad = alumnoDeportes.findByAlumnoIdAndDeporte(id, deporte).orElseThrow();
				ad.setFechaGrado(java.sql.Date.valueOf(LocalDate.now().minusYears(5)));
				assertFalse(alumnoDeporteService.esAptoParaExamen(ad), deporte.name());
				assertNull(alumnoDeporteService.calcularSiguienteGrado(ad), deporte.name());
				IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
						() -> alumnoDeporteService.actualizarGradoPorDeporte(id, deporte, TipoGrado.AMARILLO));
				assertEquals("El deporte " + deporte + " no tiene sistema de grados", error.getMessage());
				assertThrows(IllegalArgumentException.class,
						() -> alumnoDeporteService.actualizarFechaGrado(id, deporte, new java.util.Date()));
				assertThrows(IllegalArgumentException.class,
						() -> alumnoService.pasarGradoConDerechoExamen(id, deporte, false));
			}
			AlumnoDeporte pilates = alumnoDeportes.findByAlumnoIdAndDeporte(id, Deporte.PILATES).orElseThrow();
			assertNotNull(pilates.getId());
		});
		// Pagar un examen en una inscripción sin grados: el cobro se registra y no se promueve (WARN).
		long cargo = cargo(id, "PILATES", productoCanonico("DERECHO A EXAMEN AMARILLO"), "DERECHO A EXAMEN AMARILLO - PILATES");
		pagar(cargo);
		assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT pagado FROM producto_alumno WHERE id = ?", Boolean.class, cargo));
		assertNull(inscripcion(id, "PILATES").get("grado_id"));
	}

	// =============================================================================
	// Promoción: productos nombrados libremente y escuelas con códigos propios
	// =============================================================================

	@Test
	void productoSinClasificarConNombreLibre_promuevePorElNombreContenidoMasLargo() {
		long tkd = altaCon("NOMBRE-LIBRE", ANIO - 10, "TAEKWONDO", "BLANCO", "INFANTIL");
		long verde = productoSinClasificar("DERECHO A EXAMEN VERDE MARZO 2026");
		pagar(cargo(tkd, "TAEKWONDO", verde, "DERECHO A EXAMEN VERDE MARZO 2026 - TAEKWONDO"));
		assertInscripcion(tkd, "TAEKWONDO", "VERDE", "Verde");

		long intermedio = productoSinClasificar("DERECHO A EXAMEN AMARILLO/NARANJA ESPECIAL");
		pagar(cargo(tkd, "TAEKWONDO", intermedio, "DERECHO A EXAMEN AMARILLO/NARANJA ESPECIAL - TAEKWONDO"));
		assertInscripcion(tkd, "TAEKWONDO", "AMARILLO_NARANJA", "Amarillo-Naranja");

		long bordado = productoSinClasificar("Derecho de cambio a rojo bordado por recompensa (especial)");
		pagar(cargo(tkd, "TAEKWONDO", bordado, "Derecho de cambio a rojo bordado por recompensa (especial) - TAEKWONDO"));
		assertInscripcion(tkd, "TAEKWONDO", "ROJO", "Rojo");

		// En kickboxing, el mismo nombre se resuelve en su propio sistema.
		long kb = altaCon("NOMBRE-LIBRE-KB", ANIO - 30, "KICKBOXING", "BLANCO", "KICKBOXING");
		pagar(cargo(kb, "KICKBOXING", verde, "DERECHO A EXAMEN VERDE MARZO 2026 - KICKBOXING"));
		assertInscripcion(kb, "KICKBOXING", "VERDE", "Verde");
	}

	@Test
	void productoConGradoAsignado_ganaASuNombre() {
		long tkd = altaCon("GRADO-GANA", ANIO - 10, "TAEKWONDO", "BLANCO", "INFANTIL");
		long producto = productoSinClasificar("DERECHO A EXAMEN VERDE (CURSO ESPECIAL)");
		Long azulTkd = TenantContext.calcularCon(ORG_TAEMOI, () -> progresion.buscarGrado("TAEKWONDO", "AZUL").orElseThrow().getId());
		jdbc.update("UPDATE producto SET tipo = 'EXAMEN', grado_id = ? WHERE id = ?", azulTkd, producto);
		pagar(cargo(tkd, "TAEKWONDO", producto, "DERECHO A EXAMEN VERDE (CURSO ESPECIAL) - TAEKWONDO"));
		assertInscripcion(tkd, "TAEKWONDO", "AZUL", "Azul");
	}

	/**
	 * Una escuela con su propio sistema de grados (códigos que no son de {@link TipoGrado}): el pago
	 * promueve por código en el sistema de la disciplina; si la disciplina no tiene ese código, el cobro
	 * se registra y no se promueve.
	 */
	@Test
	void escuelaConCodigosPropios_promueveSinDependerDeTipoGrado_yNuncaFallaElCobro() {
		Organizacion o = new Organizacion();
		o.setSlug("it-codigos-propios-" + sufijo());
		o.setNombre("Escuela con códigos propios");
		long org = TenantContext.calcularCon(ORG_TAEMOI, () -> organizaciones.save(o).getId());
		long[] ids = TenantContext.calcularCon(org, () -> tx.execute(s -> {
			SistemaGrados propio = sistemaPropio("Propio");
			Grado inicio = gradoPropio(propio, "INICIO", 1, TipoGrado.BLANCO);
			Grado avanzado = gradoPropio(propio, "AVANZADO", 2, TipoGrado.AMARILLO);
			SistemaGrados corto = sistemaPropio("Corto");
			Grado inicioCorto = gradoPropio(corto, "INICIO", 1, TipoGrado.BLANCO);
			Disciplina principal = disciplinaPropia("TAEKWONDO", propio);
			Disciplina otra = disciplinaPropia("KICKBOXING", corto);

			com.taemoi.project.entities.Producto paso = new com.taemoi.project.entities.Producto("PASO A AVANZADO", 20.0, null);
			paso.setTipo(com.taemoi.project.entities.TipoProducto.EXAMEN);
			paso.setGrado(avanzado);
			paso = productos.save(paso);

			Alumno alumno = new Alumno();
			alumno.setNombre("PROPIO");
			alumno.setApellidos("CODIGOS " + sufijo());
			alumno.setNumeroExpediente(contadores.siguienteNumeroExpediente());
			alumno.setFechaNacimiento(java.sql.Date.valueOf(LocalDate.of(1990, 1, 1)));
			alumno.setDireccion("Calle 1");
			alumno.setTelefono("600000000");
			alumno = alumnos.save(alumno);
			inscripcionPropia(alumno, Deporte.TAEKWONDO, principal, inicio);
			inscripcionPropia(alumno, Deporte.KICKBOXING, otra, inicioCorto);
			return new long[] { alumno.getId(), paso.getId() };
		}));
		long alumnoId = ids[0];
		long productoId = ids[1];

		long cargoTkd = TenantContext.calcularCon(org, () -> tx.execute(s -> cargoEnEscuela(alumnoId, Deporte.TAEKWONDO, productoId)));
		ProductoAlumnoDTO pago = new ProductoAlumnoDTO();
		pago.setPagado(true);
		TenantContext.ejecutarCon(org, () -> productoAlumnoService.actualizarProductoAlumno(cargoTkd, pago));
		assertEquals("AVANZADO", gradoDeInscripcion(alumnoId, "TAEKWONDO"));

		long cargoKb = TenantContext.calcularCon(org, () -> tx.execute(s -> cargoEnEscuela(alumnoId, Deporte.KICKBOXING, productoId)));
		TenantContext.ejecutarCon(org, () -> productoAlumnoService.actualizarProductoAlumno(cargoKb, pago));
		assertEquals("INICIO", gradoDeInscripcion(alumnoId, "KICKBOXING"), "sin ese código en su sistema: no promueve");
		assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT pagado FROM producto_alumno WHERE id = ?", Boolean.class, cargoKb));
	}

	private long productoSinClasificar(String concepto) {
		jdbc.update("INSERT INTO producto (concepto, precio, organizacion_id) VALUES (?, 15.0, 1)", concepto);
		long id = jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = 1 AND concepto = ?", Long.class,
				concepto);
		productosInsertados.add(id);
		return id;
	}

	private SistemaGrados sistemaPropio(String nombre) {
		SistemaGrados sistema = new SistemaGrados();
		sistema.setNombre(nombre);
		return sistemas.save(sistema);
	}

	private Grado gradoPropio(SistemaGrados sistema, String codigo, int orden, TipoGrado columnaHeredada) {
		Grado grado = new Grado();
		grado.setTipoGrado(columnaHeredada);
		grado.setCodigo(codigo);
		grado.setNombre(codigo);
		grado.setOrden(orden);
		grado.setSistemaGrados(sistema);
		grado.setMesesMinimosMenor(2);
		grado.setMesesMinimosAdulto(3);
		return grados.save(grado);
	}

	private Disciplina disciplinaPropia(String codigo, SistemaGrados sistema) {
		Disciplina disciplina = new Disciplina();
		disciplina.setCodigo(codigo);
		disciplina.setNombre(codigo);
		disciplina.setSistemaGrados(sistema);
		disciplina.setEdadAdulto(14);
		return disciplinas.save(disciplina);
	}

	private void inscripcionPropia(Alumno alumno, Deporte deporte, Disciplina disciplina, Grado grado) {
		AlumnoDeporte ad = new AlumnoDeporte();
		ad.setAlumno(alumno);
		ad.setDeporte(deporte);
		ad.setDisciplina(disciplina);
		ad.setActivo(true);
		ad.setAptoParaExamen(false);
		ad.setGrado(grado);
		ad.setFechaGrado(java.sql.Date.valueOf(LocalDate.now().minusMonths(1)));
		alumnoDeportes.save(ad);
	}

	private long cargoEnEscuela(long alumnoId, Deporte deporte, long productoId) {
		ProductoAlumno pa = new ProductoAlumno();
		pa.setAlumno(alumnos.findById(alumnoId).orElseThrow());
		pa.setAlumnoDeporte(alumnoDeportes.findByAlumnoIdAndDeporte(alumnoId, deporte).orElseThrow());
		pa.setProducto(productos.findById(productoId).orElseThrow());
		pa.setConcepto("PASO A AVANZADO - " + deporte.name());
		pa.setPrecio(20.0);
		pa.setCantidad(1);
		pa.setPagado(false);
		pa.setFechaAsignacion(new java.util.Date());
		return productosAlumno.save(pa).getId();
	}

	private String gradoDeInscripcion(long alumnoId, String deporte) {
		return jdbc.queryForObject("""
				SELECT g.codigo FROM alumno_deporte ad JOIN grado g ON g.id = ad.grado_id
				WHERE ad.alumno_id = ? AND ad.deporte = ?""", String.class, alumnoId, deporte);
	}

	@Test
	void distribucionPorDeporte_usaLasDisciplinasDelCatalogo() {
		altaCon("DISTRIBUCION", ANIO - 30, "KICKBOXING", "BLANCO", "KICKBOXING");
		Map<String, Long> distribucion = TenantContext.calcularCon(ORG_TAEMOI,
				() -> alumnoDeporteService.obtenerDistribucionActivaPorDeporte());
		assertTrue(distribucion.get("KICKBOXING") >= 1);
		List<String> orden = new ArrayList<>(distribucion.keySet());
		List<String> catalogo = List.of("TAEKWONDO", "KICKBOXING", "PILATES", "DEFENSA_PERSONAL_FEMENINA");
		assertEquals(catalogo.stream().filter(orden::contains).toList(), orden, "orden del catálogo");
		Long esperadoKb = jdbc.queryForObject("""
				SELECT COUNT(*) FROM alumno_deporte WHERE organizacion_id = 1 AND deporte = 'KICKBOXING' AND activo = 1""",
				Long.class);
		assertEquals(esperadoKb, distribucion.get("KICKBOXING"));
	}

	// =============================================================================
	// Aislamiento
	// =============================================================================

	@Test
	void otraEscuelaSinCatalogo_noVeLosGradosDeTaemoi() {
		autenticar();
		long kb = altaCon("AISLADO", ANIO - 30, "KICKBOXING", "VERDE", "KICKBOXING");
		Long verdeKb = jdbc.queryForObject("SELECT grado_id FROM alumno_deporte WHERE alumno_id = ? AND deporte = 'KICKBOXING'",
				Long.class, kb);
		TenantContext.ejecutarCon(org2, () -> {
			assertEquals(List.of(), gradoController.obtenerTodosLosGrados(null).getBody());
			assertEquals(List.of(), gradoService.obtenerGradosDeDisciplina("KICKBOXING"));
			assertThrows(IllegalArgumentException.class, () -> progresion.buscarGrado("TAEKWONDO", "BLANCO"));
			assertTrue(grados.findById(verdeKb).isEmpty());
			assertEquals(List.of(), alumnoService.obtenerAlumnosFiltrados(null, verdeKb, null, true));
		});
		// Alta en una escuela sin la disciplina: 400, no se guarda nada.
		String email = "org2-" + sufijo() + "@example.test";
		ResponseEntity<?> respuesta = TenantContext.calcularCon(org2, () -> alumnoController.crearAlumno("""
				{"nombre":"OTRA","apellidos":"ESCUELA","fechaNacimiento":"1990-03-04","direccion":"Calle 7",
				 "telefono":600000006,"email":"%s",
				 "deportesInicial":[{"deporte":"TAEKWONDO","grado":"BLANCO","tipoTarifa":"ADULTO","cuantiaTarifa":30.0}]}
				""".formatted(email), null));
		assertEquals(HttpStatus.BAD_REQUEST, respuesta.getStatusCode(), String.valueOf(respuesta.getBody()));
		assertFalse(TenantContext.calcularCon(org2, () -> alumnos.existsByEmailIgnoreCase(email)));
	}

	// =============================================================================
	// Ayudantes
	// =============================================================================

	private long altaCon(String marca, int anioNacimiento, String deporte, String grado, String tarifa) {
		return altaConApellido(marca + "-" + sufijo(), anioNacimiento, deporte, grado, tarifa);
	}

	private long altaConApellido(String apellidos, int anioNacimiento, String deporte, String grado, String tarifa) {
		String json = """
				{"nombre":"CAMBIO","apellidos":"%s","fechaNacimiento":"%d-06-01","direccion":"Calle 1",
				 "telefono":600000001,"email":"cambio-%s@example.test","rolFamiliar":"NINGUNO",
				 "deportesInicial":[{"deporte":"%s","grado":"%s","fechaGrado":"%s","tipoTarifa":"%s","cuantiaTarifa":30.0}]}
				""".formatted(apellidos, anioNacimiento, sufijo(), deporte, grado, LocalDate.now().minusMonths(1), tarifa);
		return crear(json).getId();
	}

	private AlumnoDTO crear(String json) {
		ResponseEntity<?> respuesta = llamarCrear(json);
		assertEquals(HttpStatus.CREATED, respuesta.getStatusCode(), String.valueOf(respuesta.getBody()));
		return (AlumnoDTO) respuesta.getBody();
	}

	private ResponseEntity<?> llamarCrear(String json) {
		autenticar();
		return TenantContext.calcularCon(ORG_TAEMOI, () -> alumnoController.crearAlumno(json, null));
	}

	private static void autenticar() {
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				"admin-it", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
	}

	private AlumnoDTO alumnoDto(long alumnoId) {
		return TenantContext.calcularCon(ORG_TAEMOI, () -> tx.execute(s -> {
			Alumno alumno = alumnos.findById(alumnoId).orElseThrow();
			return AlumnoDTO.deAlumno(alumno);
		}));
	}

	private Map<String, Object> inscripcion(long alumnoId, String deporte) {
		return jdbc.queryForMap("""
				SELECT ad.*, d.codigo AS disciplina, g.codigo AS grado_codigo, g.nombre AS grado_nombre,
				       g.tipo_grado AS grado_tipo, sd.id AS sistema_disciplina, g.sistema_grados_id AS sistema_grado
				FROM alumno_deporte ad
				JOIN disciplina d ON d.id = ad.disciplina_id
				LEFT JOIN grado g ON g.id = ad.grado_id
				LEFT JOIN sistema_grados sd ON sd.id = d.sistema_grados_id
				WHERE ad.alumno_id = ? AND ad.deporte = ?""", alumnoId, deporte);
	}

	/** Inscripción en su disciplina, con el grado de ese código en el sistema de la disciplina. */
	private void assertInscripcion(long alumnoId, String deporte, String codigoGrado, String nombreVisible) {
		Map<String, Object> ad = inscripcion(alumnoId, deporte);
		String caso = alumnoId + "/" + deporte;
		assertEquals(deporte, ad.get("disciplina"), caso);
		assertEquals(codigoGrado, ad.get("grado_codigo"), caso);
		assertEquals(codigoGrado, ad.get("grado_tipo"), "la columna heredada sigue coherente: " + caso);
		assertEquals(((Number) ad.get("sistema_disciplina")).longValue(), ((Number) ad.get("sistema_grado")).longValue(),
				"grado del sistema de su disciplina: " + caso);
		if (nombreVisible != null) {
			assertEquals(nombreVisible, ad.get("grado_nombre"), caso);
		}
	}

	private String siguiente(String disciplina, String codigo, int anioNacimiento) {
		AlumnoDeporte ad = inscripcionEnMemoria(disciplina, codigo, anioNacimiento, LocalDate.now());
		TipoGrado siguiente = alumnoDeporteService.calcularSiguienteGrado(ad);
		return siguiente == null ? null : siguiente.name();
	}

	private boolean apto(String disciplina, String codigo, int anioNacimiento, LocalDate fechaGrado) {
		return alumnoDeporteService.esAptoParaExamen(inscripcionEnMemoria(disciplina, codigo, anioNacimiento, fechaGrado));
	}

	/** Inscripción sin guardar, con el grado real del catálogo de la disciplina. */
	private AlumnoDeporte inscripcionEnMemoria(String disciplina, String codigo, int anioNacimiento, LocalDate fechaGrado) {
		Alumno alumno = new Alumno();
		alumno.setFechaNacimiento(java.sql.Date.valueOf(LocalDate.of(anioNacimiento, 6, 1)));
		AlumnoDeporte ad = new AlumnoDeporte();
		ad.setAlumno(alumno);
		ad.setDeporte(Deporte.valueOf(disciplina));
		ad.setGrado(progresion.buscarGrado(disciplina, codigo).orElseThrow());
		ad.setFechaGrado(java.sql.Date.valueOf(fechaGrado));
		return ad;
	}

	private void ponerGrado(long alumnoId, String disciplina, String codigo) {
		TenantContext.ejecutarCon(ORG_TAEMOI,
				() -> alumnoDeporteService.actualizarGradoPorDeporte(alumnoId, Deporte.valueOf(disciplina), TipoGrado.valueOf(codigo)));
	}

	private Long productoCanonico(String concepto) {
		return jdbc.queryForObject("""
				SELECT MIN(id) FROM producto
				WHERE organizacion_id = 1 AND grado_id IS NOT NULL AND CAST(concepto AS BINARY) = CAST(? AS BINARY)""",
				Long.class, concepto);
	}

	/** Cargo sin pagar enlazado a la inscripción, como lo crea la aplicación (concepto + " - DEPORTE"). */
	private long cargo(long alumnoId, String disciplina, Long productoId, String concepto) {
		return TenantContext.calcularCon(ORG_TAEMOI, () -> tx.execute(s -> {
			ProductoAlumno pa = new ProductoAlumno();
			pa.setAlumno(alumnos.findById(alumnoId).orElseThrow());
			pa.setAlumnoDeporte(alumnoDeportes.findByAlumnoIdAndDeporte(alumnoId, Deporte.valueOf(disciplina)).orElseThrow());
			pa.setProducto(productoId == null ? null : productos.findById(productoId).orElseThrow());
			pa.setConcepto(concepto);
			pa.setPrecio(15.0);
			pa.setCantidad(1);
			pa.setPagado(false);
			pa.setFechaAsignacion(new java.util.Date());
			return productosAlumno.save(pa).getId();
		}));
	}

	private void pagar(long cargoId) {
		ProductoAlumnoDTO pago = new ProductoAlumnoDTO();
		pago.setPagado(true);
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> productoAlumnoService.actualizarProductoAlumno(cargoId, pago));
	}

	private String preinscripcionPendiente(String deporte, String nombreGrupo) {
		String marca = sufijo();
		return TenantContext.calcularCon(ORG_TAEMOI, () -> tx.execute(s -> {
			Grupo grupo = new Grupo();
			grupo.setNombre(nombreGrupo + " " + marca);
			grupo.setDeporte(Deporte.valueOf(deporte));
			grupo.setDisciplina(disciplinas.findByCodigo(deporte).orElseThrow());
			grupo = grupos.save(grupo);
			Turno turno = turnos.save(new Turno("Lunes", "18:00", "19:00", grupo));
			Preinscripcion p = new Preinscripcion();
			p.setReferencia("ITCG" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
			p.setTemporada("2099-2100");
			p.setDeporte(Deporte.valueOf(deporte));
			p.setDisciplina(disciplinas.findByCodigo(deporte).orElseThrow());
			p.setEstado(EstadoPreinscripcion.PENDIENTE);
			p.setNombre("Pre");
			p.setApellidos("Cambio " + marca);
			p.setIdentidadHash(UUID.randomUUID().toString().replace("-", "") + "0".repeat(32));
			p.setFechaNacimiento(LocalDate.of(1990, 1, 1));
			p.setDireccion("Calle 1");
			p.setTelefono("600000000");
			p.setEmail("pre-" + marca + "@example.test");
			p.setAceptacionNormas(true);
			p.setTieneDiscapacidad(false);
			p.setFirmanteNombre("PRE");
			p.setFirma("firma".getBytes(StandardCharsets.UTF_8));
			p.setPlantilla(plantillas.findFirstByDeporteAndActivaTrueOrderByVersionDesc(Deporte.valueOf(deporte)).orElseThrow());
			p.setPlantillaSnapshot("{}");
			p.setPdfFirmado("pdf".getBytes(StandardCharsets.UTF_8));
			p.setTokenDocumentoHash("c".repeat(64));
			p.setGrupo(grupo);
			p.setTurnos(Set.of(turno));
			return preinscripciones.save(p).getReferencia();
		}));
	}

	private static String texto(byte[] pdf) {
		try (PDDocument documento = PDDocument.load(pdf)) {
			return new PDFTextStripper().getText(documento);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static int apariciones(String texto, String fragmento) {
		int cuenta = 0;
		for (int i = texto.indexOf(fragmento); i >= 0; i = texto.indexOf(fragmento, i + fragmento.length())) {
			cuenta++;
		}
		return cuenta;
	}

	private static Map<String, String> mapa(String... claveValor) {
		Map<String, String> mapa = new LinkedHashMap<>();
		for (int i = 0; i < claveValor.length; i += 2) {
			mapa.put(claveValor[i], claveValor[i + 1]);
		}
		return mapa;
	}

	private static String sufijo() {
		return UUID.randomUUID().toString().substring(0, 8);
	}
}
