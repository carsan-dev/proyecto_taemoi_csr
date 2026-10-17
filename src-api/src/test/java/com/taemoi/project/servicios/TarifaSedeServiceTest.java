package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.taemoi.project.acceso.GestionPorSede;
import com.taemoi.project.dtos.response.CuantiaPersonalizadaSuperiorDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.Disciplina;
import com.taemoi.project.entities.RolFamiliar;
import com.taemoi.project.entities.Tarifa;
import com.taemoi.project.entities.TarifaSede;
import com.taemoi.project.repositories.SedeRepository;
import com.taemoi.project.repositories.TarifaRepository;
import com.taemoi.project.repositories.TarifaSedeRepository;
import com.taemoi.project.services.ResolutorCuantiaMensualidad;
import com.taemoi.project.services.impl.TarifaSedeServiceImpl;
import com.taemoi.project.services.impl.TarifasServiceImpl;

/**
 * Entrega 6-4 (D6-27 a D6-34): resolución de la cuantía de una mensualidad con las tarifas por sede, sin base de datos.
 * Ejemplo común: Sierra Sur, tarifa ADULTO 35 €; sede B con 30 € desde enero de 2027; sede A sin excepción.
 */
class TarifaSedeServiceTest {

	private static final long SEDE_A = 1L;
	private static final long SEDE_B = 2L;
	private static final YearMonth DIC_2026 = YearMonth.of(2026, 12);
	private static final YearMonth ENE_2027 = YearMonth.of(2027, 1);

	/** calcularImporte no toca el repositorio. */
	private final TarifasServiceImpl tarifas = new TarifasServiceImpl(null);
	private final Tarifa adulto = tarifa(10L, "ADULTO", "35.00", null, null);
	private final Tarifa padresHijos = tarifa(11L, "PADRES_HIJOS", "0.00", "28.00", "26.00");

	// ------------------------------------------------------------------ sin filas: identidad

	@Test
	void sinFilas_devuelveLaMismaReferencia() {
		TarifaSedeRepository repositorio = mock(TarifaSedeRepository.class);
		when(repositorio.findAll()).thenReturn(List.of());
		ResolutorCuantiaMensualidad resolutor = servicio(repositorio).preparar();
		assertSame(ResolutorCuantiaMensualidad.identidad(), resolutor);

		Double cuantia = Double.valueOf(35.0);
		AlumnoDeporte ana = inscripcion(1L, SEDE_B, adulto, cuantia, null);
		assertSame(cuantia, resolutor.cuantiaMensualidad(ana, ENE_2027));
		assertNull(resolutor.cuantiaMensualidad(inscripcion(2L, SEDE_B, adulto, null, null), ENE_2027));
		assertTrue(resolutor.personalizadasSuperiores().isEmpty());
	}

	@Test
	void conFilasDeOtraTarifaUOtraSede_mismaReferencia() {
		ResolutorCuantiaMensualidad resolutor = resolutor(fila(adulto, SEDE_B, "2027-01-01", "30.00", null, null));
		Double cuantia = Double.valueOf(35.0);
		assertSame(cuantia, resolutor.cuantiaMensualidad(inscripcion(3L, SEDE_A, adulto, cuantia, null), ENE_2027),
				"Carla, sede A sin excepción");
		Tarifa infantil = tarifa(12L, "INFANTIL", "35.00", null, null);
		assertSame(cuantia, resolutor.cuantiaMensualidad(inscripcion(3L, SEDE_B, infantil, cuantia, null), ENE_2027),
				"otra tarifa con el mismo importe");
	}

	// ------------------------------------------------------------------ vigencia (D6-34)

	@Test
	void vigencia_laFilaMasRecienteMenorOIgualQueElMes() {
		ResolutorCuantiaMensualidad resolutor = resolutor(
				fila(adulto, SEDE_B, "2027-06-01", "28.00", null, null),
				fila(adulto, SEDE_B, "2027-01-01", "30.00", null, null));
		AlumnoDeporte ana = inscripcion(1L, SEDE_B, adulto, 35.0, null);
		assertEquals(30.0, resolutor.cuantiaMensualidad(ana, ENE_2027));
		assertEquals(30.0, resolutor.cuantiaMensualidad(ana, YearMonth.of(2027, 5)));
		assertEquals(28.0, resolutor.cuantiaMensualidad(ana, YearMonth.of(2027, 6)));
		assertEquals(28.0, resolutor.cuantiaMensualidad(ana, YearMonth.of(2028, 3)));
	}

	@Test
	void vigencia_mesAnteriorALaExcepcion_mismaReferencia() {
		ResolutorCuantiaMensualidad resolutor = resolutor(fila(adulto, SEDE_B, "2027-01-01", "30.00", null, null));
		Double cuantia = Double.valueOf(35.0);
		assertSame(cuantia, resolutor.cuantiaMensualidad(inscripcion(1L, SEDE_B, adulto, cuantia, null), DIC_2026));
	}

	// ------------------------------------------------------------------ base frente a personalizada (D6-27, D6-28)

	@Test
	void anaBetoEva_baseSeResuelvePersonalizadaSeCobraTalCualYLaMayorSeLista() {
		ResolutorCuantiaMensualidad resolutor = resolutor(fila(adulto, SEDE_B, "2027-01-01", "30.00", null, null));
		AlumnoDeporte ana = inscripcion(1L, SEDE_B, adulto, 35.0, null);
		Double veinticinco = Double.valueOf(25.0);
		AlumnoDeporte beto = inscripcion(2L, SEDE_B, adulto, veinticinco, null);
		Double treintaYDos = Double.valueOf(32.0);
		AlumnoDeporte eva = inscripcion(5L, SEDE_B, adulto, treintaYDos, null);
		AlumnoDeporte igualQueLaSede = inscripcion(6L, SEDE_B, adulto, 30.0, null);

		assertEquals(30.0, resolutor.cuantiaMensualidad(ana, ENE_2027));
		assertSame(veinticinco, resolutor.cuantiaMensualidad(beto, ENE_2027));
		assertSame(treintaYDos, resolutor.cuantiaMensualidad(eva, ENE_2027));
		assertEquals(30.0, resolutor.cuantiaMensualidad(igualQueLaSede, ENE_2027));

		assertEquals(List.of(new CuantiaPersonalizadaSuperiorDTO(5L, "Alumno5 Prueba", "TAEKWONDO", 32.0, 30.0)),
				resolutor.personalizadasSuperiores(), "solo Eva (32 > 30); Beto (25) e igual (30) no");
		// Diciembre: sin excepción, nadie se lista.
		ResolutorCuantiaMensualidad diciembre = resolutor(fila(adulto, SEDE_B, "2027-01-01", "30.00", null, null));
		assertSame(treintaYDos, diciembre.cuantiaMensualidad(eva, DIC_2026));
		assertTrue(diciembre.personalizadasSuperiores().isEmpty());
	}

	@Test
	void rolFamiliar_mismaReglaQueElCatalogo() {
		ResolutorCuantiaMensualidad resolutor = resolutor(
				fila(padresHijos, SEDE_B, "2027-01-01", "0.00", "25.00", "22.00"));
		assertEquals(25.0, resolutor.cuantiaMensualidad(inscripcion(1L, SEDE_B, padresHijos, 28.0, RolFamiliar.PADRE),
				ENE_2027));
		assertEquals(25.0, resolutor.cuantiaMensualidad(inscripcion(2L, SEDE_B, padresHijos, 28.0, RolFamiliar.MADRE),
				ENE_2027));
		assertEquals(22.0, resolutor.cuantiaMensualidad(inscripcion(3L, SEDE_B, padresHijos, 26.0, RolFamiliar.HIJO),
				ENE_2027));
		assertEquals(22.0, resolutor.cuantiaMensualidad(inscripcion(4L, SEDE_B, padresHijos, 26.0, RolFamiliar.HIJA),
				ENE_2027));
		// 26 para un PADRE no es la base (28): personalizada, mayor que 25 → tal cual y listada.
		Double veintiseis = Double.valueOf(26.0);
		assertSame(veintiseis, resolutor.cuantiaMensualidad(
				inscripcion(7L, SEDE_B, padresHijos, veintiseis, RolFamiliar.PADRE), ENE_2027));
		assertEquals(1, resolutor.personalizadasSuperiores().size());
		assertEquals(25.0, resolutor.personalizadasSuperiores().get(0).cuantiaSede());
		// Sin rol: base = importe general (0) → importe general de la sede (0, como el catálogo).
		assertEquals(0.0, resolutor.cuantiaMensualidad(inscripcion(8L, SEDE_B, padresHijos, 0.0, null), ENE_2027));
		assertEquals(0.0, resolutor.cuantiaMensualidad(
				inscripcion(9L, SEDE_B, padresHijos, 0.0, RolFamiliar.NINGUNO), ENE_2027));
	}

	// ------------------------------------------------------------------ sin sede, sin tarifa (D6-29)

	@Test
	void sinSedePrincipalOSinTarifa_mismaReferencia() {
		ResolutorCuantiaMensualidad resolutor = resolutor(fila(adulto, SEDE_B, "2027-01-01", "30.00", null, null));
		Double cuantia = Double.valueOf(35.0);
		assertSame(cuantia, resolutor.cuantiaMensualidad(inscripcion(4L, null, adulto, cuantia, null), ENE_2027),
				"Dani, sin sede principal");
		assertSame(cuantia, resolutor.cuantiaMensualidad(inscripcion(4L, SEDE_B, null, cuantia, null), ENE_2027),
				"tarifa_id NULL");
		assertNull(resolutor.cuantiaMensualidad(inscripcion(4L, SEDE_B, adulto, null, null), ENE_2027));
		assertSame(cuantia, resolutor.cuantiaMensualidad(inscripcion(4L, SEDE_B, adulto, cuantia, null), null));
	}

	@Test
	void laSedeEsLaDelAlumnoDelCargo() {
		ResolutorCuantiaMensualidad resolutor = resolutor(fila(adulto, SEDE_B, "2027-01-01", "30.00", null, null));
		AlumnoDeporte ana = inscripcion(1L, SEDE_A, adulto, 35.0, null);
		Alumno enB = alumno(1L, SEDE_B);
		assertEquals(30.0, resolutor.cuantiaMensualidad(ana, enB, ENE_2027));
		assertEquals(35.0, resolutor.cuantiaMensualidad(ana, ENE_2027));
	}

	// ------------------------------------------------------------------ redondeo DOUBLE / DECIMAL

	@Test
	void redondeo_aDosDecimalesAntesDeComparar() {
		ResolutorCuantiaMensualidad resolutor = resolutor(
				fila(adulto, SEDE_B, "2027-01-01", "30.00", null, null),
				fila(padresHijos, SEDE_B, "2027-01-01", "0.00", "25.00", "22.00"));
		assertEquals(30.0, resolutor.cuantiaMensualidad(inscripcion(1L, SEDE_B, adulto, 34.999999, null), ENE_2027));
		assertEquals(30.0, resolutor.cuantiaMensualidad(inscripcion(1L, SEDE_B, adulto, 35.004, null), ENE_2027));
		Double casi = Double.valueOf(35.006);
		assertSame(casi, resolutor.cuantiaMensualidad(inscripcion(1L, SEDE_B, adulto, casi, null), ENE_2027),
				"35.01 no es la base");
		assertEquals(25.0, resolutor.cuantiaMensualidad(
				inscripcion(2L, SEDE_B, padresHijos, 28.0, RolFamiliar.PADRE), ENE_2027), "28.0 frente a 28.00");
		assertEquals(Double.valueOf(30.0), resolutor.cuantiaMensualidad(
				inscripcion(1L, SEDE_B, adulto, 35.0, null), ENE_2027), "30.00 → 30.0");
	}

	// ------------------------------------------------------------------ borrado: solo meses futuros (Europe/Madrid)

	@Test
	void borrar_soloMesesPosterioresAlEnCurso_enHoraDeMadrid() {
		TarifaSedeRepository repositorio = mock(TarifaSedeRepository.class);
		TarifaSedeServiceImpl servicio = servicio(repositorio);
		// 31/10/2026 23:30 UTC = 01/11/2026 00:30 en Madrid: el mes en curso ya es noviembre.
		ReflectionTestUtils.invokeMethod(servicio, "setReloj",
				Clock.fixed(Instant.parse("2026-10-31T23:30:00Z"), ZoneOffset.UTC));
		TarifaSede noviembre = fila(adulto, SEDE_B, "2026-11-01", "30.00", null, null);
		TarifaSede diciembre = fila(adulto, SEDE_B, "2026-12-01", "30.00", null, null);
		TarifaSede pasada = fila(adulto, SEDE_B, "2026-01-01", "30.00", null, null);
		when(repositorio.buscarEnEscuelaActiva(1L)).thenReturn(Optional.of(noviembre));
		when(repositorio.buscarEnEscuelaActiva(2L)).thenReturn(Optional.of(diciembre));
		when(repositorio.buscarEnEscuelaActiva(3L)).thenReturn(Optional.of(pasada));
		when(repositorio.buscarEnEscuelaActiva(4L)).thenReturn(Optional.empty());

		assertThrows(IllegalStateException.class, () -> servicio.borrar(1L), "mes en curso");
		assertThrows(IllegalStateException.class, () -> servicio.borrar(3L), "pasada");
		assertThrows(java.util.NoSuchElementException.class, () -> servicio.borrar(4L));
		verify(repositorio, never()).delete(any());
		servicio.borrar(2L);
		verify(repositorio).delete(diciembre);
	}

	// ------------------------------------------------------------------ alta: mes pasado (D6-37) y duplicados

	@Test
	void crear_mesPasado400_mesEnCursoSi_enHoraDeMadrid() {
		TarifaSedeRepository repositorio = mock(TarifaSedeRepository.class);
		when(repositorio.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
		TarifaSedeServiceImpl servicio = servicioParaCrear(repositorio);
		// 31/10/2026 23:30 UTC = 01/11/2026 en Madrid: octubre ya es pasado.
		ReflectionTestUtils.invokeMethod(servicio, "setReloj",
				Clock.fixed(Instant.parse("2026-10-31T23:30:00Z"), ZoneOffset.UTC));
		assertThrows(IllegalArgumentException.class, () -> servicio.crear(solicitud("2026-10")));
		assertEquals("2026-11", servicio.crear(solicitud("2026-11")).vigenteDesde());
		assertEquals("2027-01", servicio.crear(solicitud("2027-01")).vigenteDesde());
	}

	@Test
	void crear_soloLaClaveUnicaEsUn409_otraIntegridadSePropaga() {
		TarifaSedeRepository repositorio = mock(TarifaSedeRepository.class);
		TarifaSedeServiceImpl servicio = servicioParaCrear(repositorio);
		ReflectionTestUtils.invokeMethod(servicio, "setReloj",
				Clock.fixed(Instant.parse("2026-10-17T10:00:00Z"), ZoneOffset.UTC));
		when(repositorio.saveAndFlush(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException(
				"duplicado", new org.hibernate.exception.ConstraintViolationException("Duplicate entry",
						new java.sql.SQLIntegrityConstraintViolationException(
								"Duplicate entry '1-10-2-2027-01-01' for key 'tarifa_sede.uk_tarifa_sede_vigencia'"),
						"tarifa_sede.uk_tarifa_sede_vigencia")));
		assertThrows(IllegalStateException.class, () -> servicio.crear(solicitud("2027-01")));

		org.springframework.dao.DataIntegrityViolationException fk = new org.springframework.dao
				.DataIntegrityViolationException("fk", new org.hibernate.exception.ConstraintViolationException(
						"Cannot add or update a child row", new java.sql.SQLIntegrityConstraintViolationException(
								"Cannot add or update a child row: a foreign key constraint fails (fk_tarifa_sede_sede)"),
						"fk_tarifa_sede_sede"));
		org.mockito.Mockito.doThrow(fk).when(repositorio).saveAndFlush(any());
		org.springframework.dao.DataIntegrityViolationException propagada = assertThrows(
				org.springframework.dao.DataIntegrityViolationException.class,
				() -> servicio.crear(solicitud("2027-02")));
		assertSame(fk, propagada);
	}

	private TarifaSedeServiceImpl servicioParaCrear(TarifaSedeRepository repositorio) {
		com.taemoi.project.services.TarifasService catalogo = mock(com.taemoi.project.services.TarifasService.class);
		when(catalogo.buscarPorCodigo("ADULTO")).thenReturn(Optional.of(adulto));
		SedeRepository sedes = mock(SedeRepository.class);
		com.taemoi.project.entities.Sede sede = new com.taemoi.project.entities.Sede();
		ReflectionTestUtils.setField(sede, "id", SEDE_B);
		when(sedes.buscarEnEscuelaActiva(SEDE_B)).thenReturn(Optional.of(sede));
		return new TarifaSedeServiceImpl(repositorio, mock(TarifaRepository.class), sedes, catalogo,
				mock(GestionPorSede.class));
	}

	private static com.taemoi.project.dtos.request.TarifaSedeRequest solicitud(String mes) {
		return new com.taemoi.project.dtos.request.TarifaSedeRequest("ADULTO", SEDE_B, new BigDecimal("30"), null, null,
				mes);
	}

	// ------------------------------------------------------------------ utilidades

	private ResolutorCuantiaMensualidad resolutor(TarifaSede... filas) {
		TarifaSedeRepository repositorio = mock(TarifaSedeRepository.class);
		when(repositorio.findAll()).thenReturn(new ArrayList<>(List.of(filas)));
		return servicio(repositorio).preparar();
	}

	private TarifaSedeServiceImpl servicio(TarifaSedeRepository repositorio) {
		return new TarifaSedeServiceImpl(repositorio, mock(TarifaRepository.class), mock(SedeRepository.class),
				tarifas, mock(GestionPorSede.class));
	}

	private static Tarifa tarifa(long id, String codigo, String importe, String adulto, String menor) {
		Tarifa t = new Tarifa();
		ReflectionTestUtils.setField(t, "id", id);
		t.setCodigo(codigo);
		t.setImporte(new BigDecimal(importe));
		t.setImporteAdulto(adulto == null ? null : new BigDecimal(adulto));
		t.setImporteMenor(menor == null ? null : new BigDecimal(menor));
		return t;
	}

	private static TarifaSede fila(Tarifa tarifa, long sede, String desde, String importe, String adulto,
			String menor) {
		TarifaSede f = new TarifaSede();
		f.setTarifaId(tarifa.getId());
		f.setSedeId(sede);
		f.setVigenteDesde(LocalDate.parse(desde));
		f.setImporte(new BigDecimal(importe));
		f.setImporteAdulto(adulto == null ? null : new BigDecimal(adulto));
		f.setImporteMenor(menor == null ? null : new BigDecimal(menor));
		return f;
	}

	private static Alumno alumno(long id, Long sede) {
		Alumno a = new Alumno();
		a.setId(id);
		a.setNombre("Alumno" + id);
		a.setApellidos("Prueba");
		a.setSedePrincipalId(sede);
		return a;
	}

	private static AlumnoDeporte inscripcion(long alumnoId, Long sede, Tarifa tarifa, Double cuantia, RolFamiliar rol) {
		Disciplina tkd = new Disciplina();
		tkd.setCodigo("TAEKWONDO");
		AlumnoDeporte ad = new AlumnoDeporte();
		ad.setAlumno(alumno(alumnoId, sede));
		ad.setDisciplina(tkd);
		ad.setTarifa(tarifa);
		ad.setCuantiaTarifa(cuantia);
		ad.setRolFamiliar(rol);
		return ad;
	}
}
