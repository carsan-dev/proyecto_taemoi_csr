package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.taemoi.project.config.ExamEligibilityConfig;
import com.taemoi.project.config.ExamMaterialBlockConfig;
import com.taemoi.project.config.GradeProgressionConfig;
import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.Disciplina;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.TipoGrado;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.DisciplinaRepository;
import com.taemoi.project.repositories.GradoRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.SistemaGradosRepository;
import com.taemoi.project.services.CatalogoDisciplinasService;
import com.taemoi.project.services.GradoService;
import com.taemoi.project.services.ProgresionGradosService;
import com.taemoi.project.utils.DeporteEdadUtils;
import com.taemoi.project.utils.FechaUtils;
import com.taemoi.project.utils.GradoUtils;

/**
 * Paridad del catálogo (V23) con las clases que sustituye, para Taemoi (organización 1): para
 * cada combinación de los tests de caracterización, el servicio nuevo devuelve exactamente lo
 * mismo que {@link GradeProgressionConfig}, {@link ExamEligibilityConfig},
 * {@link ExamMaterialBlockConfig}, {@link GradoUtils} y {@link FechaUtils}. Las combinaciones se
 * generan desde los enums. Además: comprobaciones literales y aislamiento entre escuelas.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogoParidadIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG_TAEMOI = 1L;
	private static final List<Deporte> DEPORTES_Y_NULO = Arrays.asList(Deporte.TAEKWONDO, Deporte.KICKBOXING,
			Deporte.PILATES, Deporte.DEFENSA_PERSONAL_FEMENINA, null);
	/** Alrededor del umbral de 13 años cumplidos, más extremos. */
	private static final int[] EDADES = { -1, 0, 5, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 30, 50, 80 };

	@Autowired GradeProgressionConfig progresionAntigua;
	@Autowired ExamEligibilityConfig elegibilidadAntigua;
	@Autowired ExamMaterialBlockConfig bloquesAntiguos;
	@Autowired ProgresionGradosService progresion;
	@Autowired CatalogoDisciplinasService catalogo;
	@Autowired GradoRepository grados;
	@Autowired GradoService gradoService;
	@Autowired DisciplinaRepository disciplinas;
	@Autowired SistemaGradosRepository sistemas;
	@Autowired OrganizacionRepository organizaciones;

	long org2;

	@BeforeAll
	void crearOtraOrganizacion() {
		Organizacion org = new Organizacion();
		org.setSlug("it-catalogo-" + UUID.randomUUID().toString().substring(0, 8));
		org.setNombre("Escuela sin catálogo");
		org2 = TenantContext.calcularCon(ORG_TAEMOI, () -> organizaciones.save(org).getId());
		assertNotEquals(ORG_TAEMOI, org2);
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	private static String codigo(Deporte deporte) {
		return deporte == null ? null : deporte.name();
	}

	private static String codigo(TipoGrado grado) {
		return grado == null ? null : grado.name();
	}

	private static String codigoDe(Optional<Grado> grado) {
		return grado.map(Grado::getCodigo).orElse(null);
	}

	private static List<String> codigosDe(List<Grado> lista) {
		return lista.stream().map(Grado::getCodigo).toList();
	}

	private static List<String> nombres(List<TipoGrado> lista) {
		return lista.stream().map(Enum::name).toList();
	}

	// ---------------- GradeProgressionConfig ----------------

	@Test
	void siguienteGrado_igualQueGradeProgressionConfig_entodasLasCombinaciones() {
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			int comparadas = 0;
			for (Deporte deporte : Deporte.values()) {
				for (boolean esMenor : new boolean[] { true, false }) {
					for (TipoGrado grado : TipoGrado.values()) {
						String caso = deporte + " esMenor=" + esMenor + " grado=" + grado;
						if (progresionAntigua.tieneProgresion(deporte)) {
							assertEquals(codigo(progresionAntigua.obtenerSiguienteGrado(deporte, esMenor, grado)),
									codigoDe(progresion.obtenerSiguienteGrado(codigo(deporte), esMenor, codigo(grado))),
									caso);
							assertEquals(nombres(progresionAntigua.obtenerGradosAnteriores(deporte, esMenor, grado)),
									codigosDe(progresion.obtenerGradosAnteriores(codigo(deporte), esMenor, codigo(grado))),
									"anteriores " + caso);
						} else {
							assertThrows(IllegalArgumentException.class,
									() -> progresionAntigua.obtenerSiguienteGrado(deporte, esMenor, grado), caso);
							assertThrows(IllegalArgumentException.class,
									() -> progresion.obtenerSiguienteGrado(codigo(deporte), esMenor, codigo(grado)), caso);
							assertThrows(IllegalArgumentException.class,
									() -> progresion.obtenerGradosAnteriores(codigo(deporte), esMenor, codigo(grado)),
									caso);
						}
						comparadas++;
					}
				}
			}
			assertEquals(4 * 2 * TipoGrado.values().length, comparadas);
		});
	}

	@Test
	void progresion_entradasNulasYTieneProgresion_igualQueAntes() {
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			for (Deporte deporte : DEPORTES_Y_NULO) {
				for (boolean esMenor : new boolean[] { true, false }) {
					assertNull(progresionAntigua.obtenerSiguienteGrado(deporte, esMenor, null));
					assertTrue(progresion.obtenerSiguienteGrado(codigo(deporte), esMenor, null).isEmpty());
					assertEquals(List.of(), progresionAntigua.obtenerGradosAnteriores(deporte, esMenor, null));
					assertEquals(List.of(), progresion.obtenerGradosAnteriores(codigo(deporte), esMenor, null));
				}
			}
			for (TipoGrado grado : TipoGrado.values()) {
				assertNull(progresionAntigua.obtenerSiguienteGrado(null, true, grado));
				assertTrue(progresion.obtenerSiguienteGrado(null, true, codigo(grado)).isEmpty());
			}
			for (Deporte deporte : Deporte.values()) {
				assertEquals(progresionAntigua.tieneProgresion(deporte), progresion.tieneProgresion(codigo(deporte)),
						deporte.name());
			}
			assertFalse(progresion.tieneProgresion(null));
		});
	}

	// ---------------- ExamEligibilityConfig ----------------

	@Test
	void mesesRequeridosPorEdadCumplida_igualQueExamEligibilityConfig() {
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			for (Deporte deporte : DEPORTES_Y_NULO) {
				for (int edad : EDADES) {
					for (TipoGrado grado : TipoGrado.values()) {
						assertEquals(elegibilidadAntigua.obtenerMesesRequeridos(edad, grado),
								progresion.obtenerMesesRequeridosPorEdadCumplida(codigo(deporte), edad, codigo(grado)),
								deporte + " edad=" + edad + " grado=" + grado);
					}
					assertEquals(elegibilidadAntigua.obtenerMesesRequeridos(edad, null),
							progresion.obtenerMesesRequeridosPorEdadCumplida(codigo(deporte), edad, null));
				}
			}
		});
	}

	@Test
	void mesesYPeriodoPorGrupoDeEdad_igualQueLosMapasDeExamEligibilityConfig() {
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			for (Deporte deporte : DEPORTES_Y_NULO) {
				for (boolean esMenor : new boolean[] { true, false }) {
					var mapa = esMenor ? elegibilidadAntigua.obtenerMesesRequeridosMenores()
							: elegibilidadAntigua.obtenerMesesRequeridosMayores();
					for (TipoGrado grado : TipoGrado.values()) {
						String caso = deporte + " esMenor=" + esMenor + " grado=" + grado;
						assertEquals(mapa.getOrDefault(grado, Long.MAX_VALUE),
								progresion.obtenerMesesRequeridos(codigo(deporte), codigo(grado), esMenor), caso);
						assertEquals(elegibilidadAntigua.tienePeriodoDefinido(grado, esMenor),
								progresion.tienePeriodoDefinido(codigo(deporte), codigo(grado), esMenor), caso);
					}
					assertEquals(elegibilidadAntigua.tienePeriodoDefinido(null, esMenor),
							progresion.tienePeriodoDefinido(codigo(deporte), null, esMenor));
				}
			}
		});
	}

	@Test
	void reglaDeExamenPorEdadCumplida_esMenorDe13() {
		for (int edad = -1; edad <= 80; edad++) {
			// ExamEligibilityConfig: BLANCO = 2 meses para menores, 3 para adultos.
			boolean menorAntes = elegibilidadAntigua.obtenerMesesRequeridos(edad, TipoGrado.BLANCO) == 2L;
			assertEquals(menorAntes, catalogo.esMenorParaExamenPorEdadCumplida(edad), "edad=" + edad);
		}
	}

	// ---------------- ExamMaterialBlockConfig ----------------

	@Test
	void bloqueDeTemario_igualQueExamMaterialBlockConfig() {
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			for (Deporte deporte : DEPORTES_Y_NULO) {
				for (TipoGrado grado : TipoGrado.values()) {
					assertEquals(bloquesAntiguos.obtenerBloque(deporte, grado),
							progresion.obtenerBloqueTemario(codigo(deporte), codigo(grado)), deporte + "/" + grado);
				}
				assertEquals(bloquesAntiguos.obtenerBloque(deporte, null),
						progresion.obtenerBloqueTemario(codigo(deporte), null));
			}
		});
	}

	// ---------------- GradoUtils ----------------

	@Test
	void nombreVisible_igualQueGradoUtils() {
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			for (Deporte deporte : DEPORTES_Y_NULO) {
				for (TipoGrado tipo : TipoGrado.values()) {
					assertEquals(GradoUtils.getNombreGradoParaDeporte(tipo, deporte),
							progresion.obtenerNombreVisible(codigo(deporte), tipo.name()), deporte + "/" + tipo);
				}
				assertEquals(GradoUtils.getNombreGradoParaDeporte((TipoGrado) null, deporte),
						progresion.obtenerNombreVisible(codigo(deporte), null), String.valueOf(deporte));
			}
		});
	}

	@Test
	void nombreVisiblePorEntidad_esElNombreDeLaFila() {
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			assertEquals("MARRÓN", progresion.obtenerNombreVisible(
					progresion.buscarGrado("KICKBOXING", "ROJO").orElseThrow()));
			assertEquals("Rojo", progresion.obtenerNombreVisible(grados.findByTipoGrado(TipoGrado.ROJO)));
			assertEquals("N/A", progresion.obtenerNombreVisible((Grado) null));
		});
	}

	// ---------------- FechaUtils ----------------

	@Test
	void edadAdulta_igualQueFechaUtils() {
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			for (Deporte deporte : DEPORTES_Y_NULO) {
				assertEquals(FechaUtils.obtenerEdadAdulto(deporte), catalogo.obtenerEdadAdulto(codigo(deporte)),
						String.valueOf(deporte));
			}
		});
	}

	@Test
	void esMenorPorAnioNatural_igualQueFechaUtils_relativoAHoy() {
		int anio = LocalDate.now().getYear();
		List<LocalDate> nacimientos = fechasDePrueba(anio - 17, anio - 11);
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			for (Deporte deporte : DEPORTES_Y_NULO) {
				for (LocalDate nacimiento : nacimientos) {
					assertEquals(FechaUtils.esMenor(java.sql.Date.valueOf(nacimiento), deporte),
							catalogo.esMenorPorAnioNatural(nacimiento, codigo(deporte)), deporte + " " + nacimiento);
				}
				assertEquals(FechaUtils.esMenor(null, deporte), catalogo.esMenorPorAnioNatural(null, codigo(deporte)));
			}
		});
	}

	/** Regla de DeporteEdadUtils (MaterialExamenServiceImpl): fecha nula = adulto. */
	@Test
	void esMenorFechaNulaComoAdulto_igualQueDeporteEdadUtils() {
		int anio = LocalDate.now().getYear();
		List<LocalDate> nacimientos = fechasDePrueba(anio - 17, anio - 11);
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			for (Deporte deporte : DEPORTES_Y_NULO) {
				for (LocalDate nacimiento : nacimientos) {
					boolean nuevo = catalogo.esMenorPorAnioNaturalFechaNulaComoAdulto(nacimiento, codigo(deporte));
					assertEquals(DeporteEdadUtils.esMenorParaDeporte(deporte, nacimiento), nuevo,
							deporte + " " + nacimiento);
					assertEquals(DeporteEdadUtils.esMenorParaDeporte(deporte, java.sql.Date.valueOf(nacimiento)),
							nuevo, deporte + " " + nacimiento);
				}
				assertEquals(DeporteEdadUtils.esMenorParaDeporte(deporte, (LocalDate) null),
						catalogo.esMenorPorAnioNaturalFechaNulaComoAdulto(null, codigo(deporte)));
				assertEquals(DeporteEdadUtils.esMenorParaDeporte(deporte, (java.util.Date) null),
						catalogo.esMenorPorAnioNaturalFechaNulaComoAdulto(null, codigo(deporte)));
			}
		});
	}

	@Test
	void esMenorPorAnioNatural_igualQueFechaUtils_conAnioDeReferencia() {
		List<LocalDate> nacimientos = new ArrayList<>();
		for (int anio = 2008; anio <= 2016; anio++) {
			nacimientos.add(LocalDate.of(anio, 1, 1));
			nacimientos.add(LocalDate.of(anio, 12, 31));
		}
		nacimientos.add(LocalDate.of(2012, 2, 29));
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			for (Deporte deporte : DEPORTES_Y_NULO) {
				for (int anioRef = 2022; anioRef <= 2031; anioRef++) {
					for (LocalDate nacimiento : nacimientos) {
						assertEquals(FechaUtils.esMenor(java.sql.Date.valueOf(nacimiento), deporte, anioRef),
								catalogo.esMenorPorAnioNatural(nacimiento, codigo(deporte), anioRef),
								deporte + " " + nacimiento + " ref=" + anioRef);
					}
					assertEquals(FechaUtils.esMenor(null, deporte, anioRef),
							catalogo.esMenorPorAnioNatural(null, codigo(deporte), anioRef));
				}
			}
		});
	}

	/** Primer día, día 15 y último día de cada mes, más los 29 de febrero, en [desde, hasta]. */
	private static List<LocalDate> fechasDePrueba(int desde, int hasta) {
		List<LocalDate> fechas = new ArrayList<>();
		for (int anio = desde; anio <= hasta; anio++) {
			for (int mes = 1; mes <= 12; mes++) {
				LocalDate primero = LocalDate.of(anio, mes, 1);
				fechas.add(primero);
				fechas.add(primero.withDayOfMonth(15));
				fechas.add(primero.withDayOfMonth(primero.lengthOfMonth()));
			}
		}
		LocalDate hoy = LocalDate.now();
		for (int anios = 12; anios <= 16; anios++) {
			fechas.add(hoy.minusYears(anios).minusDays(1));
			fechas.add(hoy.minusYears(anios));
			fechas.add(hoy.minusYears(anios).plusDays(1));
		}
		return fechas;
	}

	// ---------------- Comprobaciones literales ----------------

	@Test
	void transicionesClave_literales() {
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			assertEquals("ROJO_NEGRO_1_PUM", codigoDe(progresion.obtenerSiguienteGrado("TAEKWONDO", true, "ROJO")));
			assertEquals("NEGRO_1_DAN", codigoDe(progresion.obtenerSiguienteGrado("TAEKWONDO", false, "ROJO")));
			assertEquals("AMARILLO", codigoDe(progresion.obtenerSiguienteGrado("TAEKWONDO", false, "BLANCO_AMARILLO")));
			assertNull(codigoDe(progresion.obtenerSiguienteGrado("TAEKWONDO", true, "ROJO_NEGRO_3_PUM")));
			assertNull(codigoDe(progresion.obtenerSiguienteGrado("TAEKWONDO", true, "NEGRO_1_DAN")));
			assertNull(codigoDe(progresion.obtenerSiguienteGrado("TAEKWONDO", false, "ROJO_NEGRO_1_PUM")));
			assertEquals("AMARILLO", codigoDe(progresion.obtenerSiguienteGrado("KICKBOXING", true, "BLANCO")));
			assertEquals("NEGRO_2_DAN", codigoDe(progresion.obtenerSiguienteGrado("KICKBOXING", true, "NEGRO_1_DAN")));
			assertNull(codigoDe(progresion.obtenerSiguienteGrado("KICKBOXING", true, "ROJO_NEGRO_2_PUM")));
			assertNull(codigoDe(progresion.obtenerSiguienteGrado("KICKBOXING", false, "NEGRO_5_DAN")));

			assertEquals("MARRÓN", progresion.obtenerNombreVisible("KICKBOXING", "ROJO"));
			assertEquals("Rojo", progresion.obtenerNombreVisible("TAEKWONDO", "ROJO"));
			assertEquals(24L, progresion.obtenerMesesRequeridos("TAEKWONDO", "ROJO_NEGRO_1_PUM", true));
			assertEquals(Long.MAX_VALUE, progresion.obtenerMesesRequeridos("TAEKWONDO", "ROJO_NEGRO_1_PUM", false));
			assertEquals(2L, progresion.obtenerMesesRequeridos("PILATES", "BLANCO", true));
			assertEquals("Rojo", progresion.obtenerNombreVisible("PILATES", "ROJO"));
			assertEquals("Rojo", progresion.obtenerNombreVisible(null, "ROJO"));
			assertEquals("b07_negro_1_a_negro_2", progresion.obtenerBloqueTemario("KICKBOXING", "ROJO_NEGRO_1_PUM"));
			assertEquals(15, catalogo.obtenerEdadAdulto("KICKBOXING"));
			assertThrows(IllegalArgumentException.class, () -> catalogo.obtenerEdadAdulto("NO_EXISTE"));
		});
	}

	@Test
	void disciplinasDeTaemoi_conSusIndicadores() {
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			List<Disciplina> activas = catalogo.listarActivas();
			assertEquals(List.of("TAEKWONDO", "KICKBOXING", "PILATES", "DEFENSA_PERSONAL_FEMENINA"),
					activas.stream().map(Disciplina::getCodigo).toList());
			for (Disciplina d : activas) {
				boolean conGrados = d.getCodigo().equals("TAEKWONDO") || d.getCodigo().equals("KICKBOXING");
				assertEquals(conGrados, d.tieneGrados(), d.getCodigo());
				assertEquals(conGrados, d.getPermiteCompeticion(), d.getCodigo());
				assertEquals(conGrados, d.getRequiereLicencia(), d.getCodigo());
			}
			assertEquals("Defensa personal femenina", catalogo.obtenerPorCodigo("DEFENSA_PERSONAL_FEMENINA").getNombre());
			assertEquals(2, sistemas.count());
		});
	}

	@Test
	void busquedasHeredadas_siguenDevolviendoUnGradoPorTipo_elDeTaekwondo() {
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			Long sistemaTkd = catalogo.obtenerPorCodigo("TAEKWONDO").getSistemaGrados().getId();
			for (TipoGrado tipo : TipoGrado.values()) {
				Grado heredado = grados.findByTipoGrado(tipo);
				assertNotNull(heredado, tipo.name());
				assertEquals(sistemaTkd, heredado.getSistemaGrados().getId(), tipo.name());
				assertEquals(tipo.name(), heredado.getCodigo());
			}
			List<Grado> listado = gradoService.obtenerTodosLosGrados();
			assertEquals(TipoGrado.values().length, listado.size());
			Long sistemaKb = catalogo.obtenerPorCodigo("KICKBOXING").getSistemaGrados().getId();
			assertEquals(TipoGrado.values().length, grados.findBySistemaGradosIdOrderByOrdenAscIdAsc(sistemaTkd).size());
			assertEquals(TipoGrado.values().length, grados.findBySistemaGradosIdOrderByOrdenAscIdAsc(sistemaKb).size());
		});
	}

	// ---------------- Aislamiento ----------------

	@Test
	void otraOrganizacion_noVeElCatalogoDeTaemoi() {
		Long sistemaTkd = TenantContext.calcularCon(ORG_TAEMOI,
				() -> catalogo.obtenerPorCodigo("TAEKWONDO").getSistemaGrados().getId());
		Long disciplinaTkd = TenantContext.calcularCon(ORG_TAEMOI,
				() -> catalogo.obtenerPorCodigo("TAEKWONDO").getId());
		TenantContext.ejecutarCon(org2, () -> {
			assertEquals(List.of(), catalogo.listarActivas());
			assertTrue(catalogo.buscarPorCodigo("TAEKWONDO").isEmpty());
			assertTrue(disciplinas.findById(disciplinaTkd).isEmpty());
			assertEquals(0, disciplinas.count());
			assertEquals(0, sistemas.count());
			assertTrue(sistemas.findById(sistemaTkd).isEmpty());
			assertEquals(0, grados.count());
			assertEquals(List.of(), grados.findBySistemaGradosIdOrderByOrdenAscIdAsc(sistemaTkd));
			assertTrue(grados.findBySistemaGradosIdAndCodigo(sistemaTkd, "BLANCO").isEmpty());
			assertNull(grados.findByTipoGrado(TipoGrado.BLANCO));
			assertThrows(IllegalArgumentException.class,
					() -> progresion.obtenerSiguienteGrado("TAEKWONDO", true, "BLANCO"));
			assertThrows(IllegalArgumentException.class, () -> catalogo.obtenerEdadAdulto("KICKBOXING"));
		});
	}
}
