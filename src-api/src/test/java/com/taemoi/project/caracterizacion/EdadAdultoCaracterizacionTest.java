package com.taemoi.project.caracterizacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import org.junit.jupiter.api.Test;

import com.taemoi.project.legado.Deporte;
import com.taemoi.project.legado.DeporteEdadUtils;
import com.taemoi.project.legado.FechaUtils;

/**
 * Caracterizacion de las reglas de edad adulta: FechaUtils y DeporteEdadUtils.
 *
 * Regla observada: un alumno es adulto si cumple la edad adulta (14 TKD/otros, 15 KB)
 * en el ano natural de referencia o antes. Es decir, decide el ano de nacimiento,
 * no el dia del cumpleanos.
 */
class EdadAdultoCaracterizacionTest {

	private static final LocalDate HOY = LocalDate.now();
	private static final int ANIO = HOY.getYear();

	private static Date sqlDate(LocalDate fecha) {
		return java.sql.Date.valueOf(fecha);
	}

	private static Date utilDate(LocalDate fecha) {
		return Date.from(fecha.atStartOfDay(ZoneId.systemDefault()).toInstant());
	}

	private static boolean esUltimoDiaDelAnio() {
		return HOY.getMonthValue() == 12 && HOY.getDayOfMonth() == 31;
	}

	// ---------------- Constantes y edad adulta por deporte ----------------

	@Test
	void edadAdultoPorDeporte() {
		assertEquals(14, FechaUtils.EDAD_ADULTO_TAEKWONDO);
		assertEquals(15, FechaUtils.EDAD_ADULTO_KICKBOXING);
		assertEquals(14, FechaUtils.obtenerEdadAdulto(Deporte.TAEKWONDO));
		assertEquals(15, FechaUtils.obtenerEdadAdulto(Deporte.KICKBOXING));
		assertEquals(14, FechaUtils.obtenerEdadAdulto(Deporte.PILATES));
		assertEquals(14, FechaUtils.obtenerEdadAdulto(Deporte.DEFENSA_PERSONAL_FEMENINA));
		assertEquals(14, FechaUtils.obtenerEdadAdulto(null));
	}

	// ---------------- calcularEdad: dia antes / dia / dia despues del cumpleanos ----------------

	@Test
	void calcularEdad_limitesCumpleanos() {
		assertEquals(14, FechaUtils.calcularEdad(sqlDate(HOY.minusYears(14))));             // hoy cumple 14
		assertEquals(13, FechaUtils.calcularEdad(sqlDate(HOY.minusYears(14).plusDays(1)))); // cumple manana
		assertEquals(14, FechaUtils.calcularEdad(sqlDate(HOY.minusYears(14).minusDays(1)))); // cumplio ayer
		assertEquals(14, FechaUtils.calcularEdad(utilDate(HOY.minusYears(14))));
		assertEquals(13, FechaUtils.calcularEdad(utilDate(HOY.minusYears(14).plusDays(1))));
	}

	// ---------------- FechaUtils.esMenor(fecha, deporte, anioReferencia): determinista ----------------

	@Test
	void esMenorConAnioReferencia_taekwondo() {
		// adulto si anioNacimiento + 14 <= anioReferencia
		assertFalse(FechaUtils.esMenor(sqlDate(LocalDate.of(2012, 1, 1)), Deporte.TAEKWONDO, 2026));
		assertFalse(FechaUtils.esMenor(sqlDate(LocalDate.of(2012, 12, 31)), Deporte.TAEKWONDO, 2026));
		assertTrue(FechaUtils.esMenor(sqlDate(LocalDate.of(2013, 1, 1)), Deporte.TAEKWONDO, 2026));
		assertTrue(FechaUtils.esMenor(sqlDate(LocalDate.of(2012, 12, 31)), Deporte.TAEKWONDO, 2025));
		assertFalse(FechaUtils.esMenor(sqlDate(LocalDate.of(2000, 6, 15)), Deporte.TAEKWONDO, 2026));
		// PILATES y DPF usan la regla de Taekwondo (14)
		assertFalse(FechaUtils.esMenor(sqlDate(LocalDate.of(2012, 12, 31)), Deporte.PILATES, 2026));
		assertTrue(FechaUtils.esMenor(sqlDate(LocalDate.of(2013, 1, 1)), Deporte.DEFENSA_PERSONAL_FEMENINA, 2026));
	}

	@Test
	void esMenorConAnioReferencia_kickboxing() {
		// adulto si anioNacimiento + 15 <= anioReferencia
		assertFalse(FechaUtils.esMenor(sqlDate(LocalDate.of(2011, 12, 31)), Deporte.KICKBOXING, 2026));
		assertTrue(FechaUtils.esMenor(sqlDate(LocalDate.of(2012, 1, 1)), Deporte.KICKBOXING, 2026));
		assertTrue(FechaUtils.esMenor(sqlDate(LocalDate.of(2012, 12, 31)), Deporte.KICKBOXING, 2026));
		assertFalse(FechaUtils.esMenor(sqlDate(LocalDate.of(2012, 1, 1)), Deporte.KICKBOXING, 2027));
	}

	@Test
	void esMenorConAnioReferencia_fechaNula_esMenor() {
		assertTrue(FechaUtils.esMenor(null, Deporte.TAEKWONDO, 2026));
		assertTrue(FechaUtils.esMenor(null, Deporte.KICKBOXING, 2026));
	}

	// ---------------- FechaUtils.esMenor(fecha, deporte) y DeporteEdadUtils: relativo a hoy ----------------

	/** Comprueba la misma expectativa en los tres caminos (FechaUtils sql/util, DeporteEdadUtils Date/LocalDate). */
	private static void assertMenor(boolean esperado, LocalDate nacimiento, Deporte deporte) {
		String msg = deporte + " nacimiento=" + nacimiento + " hoy=" + HOY;
		assertEquals(esperado, FechaUtils.esMenor(sqlDate(nacimiento), deporte), "FechaUtils sql " + msg);
		assertEquals(esperado, FechaUtils.esMenor(utilDate(nacimiento), deporte), "FechaUtils util " + msg);
		assertEquals(esperado, DeporteEdadUtils.esMenorParaDeporte(deporte, sqlDate(nacimiento)),
				"DeporteEdadUtils Date " + msg);
		assertEquals(esperado, DeporteEdadUtils.esMenorParaDeporte(deporte, nacimiento),
				"DeporteEdadUtils LocalDate " + msg);
	}

	@Test
	void taekwondo_decideElAnioNatural() {
		assertMenor(false, LocalDate.of(ANIO - 14, 1, 1), Deporte.TAEKWONDO);   // ya tiene 14
		assertMenor(false, LocalDate.of(ANIO - 14, 12, 31), Deporte.TAEKWONDO); // 13, cumple 14 este ano
		assertMenor(true, LocalDate.of(ANIO - 13, 1, 1), Deporte.TAEKWONDO);    // 13, cumple 14 el ano que viene
		assertMenor(true, LocalDate.of(ANIO - 13, 12, 31), Deporte.TAEKWONDO);
		assertMenor(true, LocalDate.of(ANIO - 5, 6, 1), Deporte.TAEKWONDO);
		assertMenor(false, LocalDate.of(ANIO - 40, 6, 1), Deporte.TAEKWONDO);
	}

	@Test
	void kickboxing_decideElAnioNatural() {
		assertMenor(false, LocalDate.of(ANIO - 15, 1, 1), Deporte.KICKBOXING);
		assertMenor(false, LocalDate.of(ANIO - 15, 12, 31), Deporte.KICKBOXING); // 14, cumple 15 este ano
		assertMenor(true, LocalDate.of(ANIO - 14, 1, 1), Deporte.KICKBOXING);    // 14, cumple 15 el ano que viene
		assertMenor(true, LocalDate.of(ANIO - 14, 12, 31), Deporte.KICKBOXING);
		// Mismo nacimiento: adulto en TKD, menor en KB
		assertMenor(false, LocalDate.of(ANIO - 14, 1, 1), Deporte.TAEKWONDO);
	}

	@Test
	void otrosDeportes_usanReglaTaekwondo() {
		assertMenor(false, LocalDate.of(ANIO - 14, 12, 31), Deporte.PILATES);
		assertMenor(true, LocalDate.of(ANIO - 13, 1, 1), Deporte.PILATES);
		assertMenor(false, LocalDate.of(ANIO - 14, 12, 31), Deporte.DEFENSA_PERSONAL_FEMENINA);
		assertMenor(true, LocalDate.of(ANIO - 13, 1, 1), Deporte.DEFENSA_PERSONAL_FEMENINA);
		assertMenor(false, LocalDate.of(ANIO - 14, 12, 31), null);
		assertMenor(true, LocalDate.of(ANIO - 13, 1, 1), null);
	}

	@Test
	void limitesDelCumpleanos_noCambianElResultadoDentroDelAnio() {
		assumeTrue(!esUltimoDiaDelAnio(), "El dia siguiente cae en otro ano");
		// TKD: dia antes / dia del / dia despues del 14 cumpleanos -> adulto en los tres
		assertMenor(false, HOY.minusYears(14).minusDays(1), Deporte.TAEKWONDO);
		assertMenor(false, HOY.minusYears(14), Deporte.TAEKWONDO);
		assertMenor(false, HOY.minusYears(14).plusDays(1), Deporte.TAEKWONDO);
		// KB: lo mismo con 15
		assertMenor(false, HOY.minusYears(15).minusDays(1), Deporte.KICKBOXING);
		assertMenor(false, HOY.minusYears(15), Deporte.KICKBOXING);
		assertMenor(false, HOY.minusYears(15).plusDays(1), Deporte.KICKBOXING);
		// Un ano menos: menor en los tres
		assertMenor(true, HOY.minusYears(13).plusDays(1), Deporte.TAEKWONDO);
		assertMenor(true, HOY.minusYears(13), Deporte.TAEKWONDO);
		assertMenor(true, HOY.minusYears(14), Deporte.KICKBOXING);
	}

	@Test
	void fechaNula_divergenciaEntreUtilidades() {
		// FechaUtils trata la fecha nula como menor; DeporteEdadUtils como adulto
		assertTrue(FechaUtils.esMenor(null, Deporte.TAEKWONDO));
		assertTrue(FechaUtils.esMenor(null, Deporte.KICKBOXING));
		assertFalse(DeporteEdadUtils.esMenorParaDeporte(Deporte.TAEKWONDO, (Date) null));
		assertFalse(DeporteEdadUtils.esMenorParaDeporte(Deporte.KICKBOXING, (LocalDate) null));
		assertFalse(FechaUtils.cumpleEdadEsteAnio(null, 14));
	}

	@Test
	void cumpleEdadEsteAnio() {
		assertTrue(FechaUtils.cumpleEdadEsteAnio(sqlDate(LocalDate.of(ANIO - 14, 1, 1)), 14));
		assertTrue(FechaUtils.cumpleEdadEsteAnio(sqlDate(LocalDate.of(ANIO - 14, 12, 31)), 14));
		assertFalse(FechaUtils.cumpleEdadEsteAnio(sqlDate(LocalDate.of(ANIO - 13, 1, 1)), 14));
		assertFalse(FechaUtils.cumpleEdadEsteAnio(sqlDate(LocalDate.of(ANIO - 15, 12, 31)), 14));
	}
}
