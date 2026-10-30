package com.taemoi.project.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.taemoi.project.dtos.response.PerfilCuentaDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.RelacionUsuarioAlumno;
import com.taemoi.project.entities.UsuarioAlumno;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.SedeRepository;
import com.taemoi.project.repositories.UsuarioAlumnoRepository;

/**
 * Pieza 8b-M2 y decisión 322: el booleano {@code cumpleanos} de {@code GET /api/auth/permisos} y la ficha que lo decide,
 * sin Spring.
 */
class PermisosSesionCumpleanosTest {

	private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
	private static final long USUARIO = 7L;
	private static final long ORG = 1L;
	private static final long OTRA_ORG = 2L;
	private static final long ALUMNO = 40L;

	private final UsuarioAlumnoRepository vinculos = mock(UsuarioAlumnoRepository.class);
	private final AlumnoRepository alumnos = mock(AlumnoRepository.class);

	private PermisosSesionService servicio(String instante) {
		Clock reloj = Clock.fixed(Instant.parse(instante), MADRID);
		return new PermisosSesionService(null, mock(SedeRepository.class), vinculos, alumnos, reloj);
	}

	private static Date fecha(int anio, int mes, int dia) {
		return java.sql.Date.valueOf(LocalDate.of(anio, mes, dia));
	}

	/** Una ficha PROPIA del usuario en ORG con la fecha dada. */
	private void fichaPropia(Date nacimiento, boolean anonimizado) {
		fichasPropias(ALUMNO);
		ficha(ALUMNO, nacimiento, anonimizado);
	}

	/** Vínculos PROPIOS del usuario en ORG, en el orden dado (el de la consulta no está garantizado). */
	private void fichasPropias(long... ids) {
		List<UsuarioAlumno> lista = java.util.Arrays.stream(ids).mapToObj(id -> {
			UsuarioAlumno vinculo = mock(UsuarioAlumno.class);
			when(vinculo.getAlumnoId()).thenReturn(id);
			return vinculo;
		}).toList();
		when(vinculos.findByUsuarioIdAndOrganizacionIdAndRelacion(USUARIO, ORG, RelacionUsuarioAlumno.PROPIO))
				.thenReturn(lista);
	}

	private void ficha(long id, Date nacimiento, boolean anonimizado) {
		Alumno alumno = new Alumno();
		alumno.setFechaNacimiento(nacimiento);
		if (anonimizado) {
			alumno.setAnonimizadoEn(java.time.LocalDateTime.now());
		}
		when(alumnos.buscarPorIdEnOrganizacionActiva(id)).thenReturn(Optional.of(alumno));
	}

	@Test
	void cumpleHoy_true() {
		fichaPropia(fecha(2010, 10, 28), false);
		assertTrue(servicio("2026-10-28T10:00:00Z").cumpleanos(USUARIO, ORG, null, null));
	}

	@Test
	void hoyEsMadridNoUtc() {
		fichaPropia(fecha(2010, 10, 29), false);
		// 28/10 23:30 UTC ya es 29/10 en Madrid.
		assertTrue(servicio("2026-10-28T23:30:00Z").cumpleanos(USUARIO, ORG, null, null));
	}

	@Test
	void otroDia_false() {
		fichaPropia(fecha(2010, 10, 27), false);
		assertFalse(servicio("2026-10-28T10:00:00Z").cumpleanos(USUARIO, ORG, null, null));
	}

	@Test
	void relacionTutor_false() {
		// Solo hay ficha TUTOR: la consulta por PROPIO no devuelve nada.
		when(vinculos.findByUsuarioIdAndOrganizacionIdAndRelacion(USUARIO, ORG, RelacionUsuarioAlumno.TUTOR))
				.thenReturn(List.of(mock(UsuarioAlumno.class)));
		assertFalse(servicio("2026-10-28T10:00:00Z").cumpleanos(USUARIO, ORG, null, null));
		verify(vinculos).findByUsuarioIdAndOrganizacionIdAndRelacion(USUARIO, ORG, RelacionUsuarioAlumno.PROPIO);
	}

	@Test
	void sinFicha_false() {
		assertFalse(servicio("2026-10-28T10:00:00Z").cumpleanos(USUARIO, ORG, null, null));
	}

	@Test
	void fichaDeOtraOrganizacion_false() {
		fichaPropia(fecha(2010, 10, 28), false);
		assertFalse(servicio("2026-10-28T10:00:00Z").cumpleanos(USUARIO, OTRA_ORG, null, null),
				"se filtra por la organización de la sesión");
		verify(vinculos).findByUsuarioIdAndOrganizacionIdAndRelacion(eq(USUARIO), eq(OTRA_ORG), any());
	}

	@Test
	void fechaNula_false() {
		fichaPropia(null, false);
		assertFalse(servicio("2026-10-28T10:00:00Z").cumpleanos(USUARIO, ORG, null, null));
	}

	@Test
	void anonimizado_false() {
		fichaPropia(fecha(2010, 10, 28), true);
		assertFalse(servicio("2026-10-28T10:00:00Z").cumpleanos(USUARIO, ORG, null, null));
	}

	@Test
	void sinSesion_false() {
		assertFalse(servicio("2026-10-28T10:00:00Z").cumpleanos(null, ORG, 28, 10));
		assertFalse(servicio("2026-10-28T10:00:00Z").cumpleanos(USUARIO, null, 28, 10));
	}

	@Test
	void veintinueveFebrero_enAnioNoBisiestoSeCelebraElVeintiocho() {
		assertTrue(PermisosSesionService.esCumpleanos(2, 29, LocalDate.of(2027, 2, 28)));
		assertFalse(PermisosSesionService.esCumpleanos(2, 29, LocalDate.of(2027, 3, 1)));
		// En año bisiesto es el 29, no el 28.
		assertTrue(PermisosSesionService.esCumpleanos(2, 29, LocalDate.of(2028, 2, 29)));
		assertFalse(PermisosSesionService.esCumpleanos(2, 29, LocalDate.of(2028, 2, 28)));
	}

	@Test
	void veintiochoFebrero_nacidoNormal_noSeDuplica() {
		assertTrue(PermisosSesionService.esCumpleanos(2, 28, LocalDate.of(2028, 2, 28)));
		assertFalse(PermisosSesionService.esCumpleanos(2, 28, LocalDate.of(2028, 2, 29)));
	}

	@Test
	void veintinueveFebrero_conReloj() {
		fichaPropia(fecha(2012, 2, 29), false);
		assertTrue(servicio("2027-02-28T12:00:00Z").cumpleanos(USUARIO, ORG, null, null));
	}

	// ------------------------------------------------------------------ decisión 322: la cuenta, si no hay ficha

	@Test
	void sinFicha_decideLaCuenta() {
		assertTrue(servicio("2026-10-28T10:00:00Z").cumpleanos(USUARIO, ORG, 28, 10));
		assertFalse(servicio("2026-10-28T10:00:00Z").cumpleanos(USUARIO, ORG, 27, 10));
		assertFalse(servicio("2026-10-28T10:00:00Z").cumpleanos(USUARIO, ORG, 28, 9));
	}

	@Test
	void cuenta_soloUnValor_noCuenta() {
		assertFalse(servicio("2026-10-28T10:00:00Z").cumpleanos(USUARIO, ORG, 28, null));
		assertFalse(servicio("2026-10-28T10:00:00Z").cumpleanos(USUARIO, ORG, null, 10));
	}

	@Test
	void cuenta_hoyEsMadridNoUtc() {
		assertTrue(servicio("2026-10-28T23:30:00Z").cumpleanos(USUARIO, ORG, 29, 10));
	}

	@Test
	void cuenta_veintinueveFebrero() {
		assertTrue(servicio("2027-02-28T12:00:00Z").cumpleanos(USUARIO, ORG, 29, 2));
		assertFalse(servicio("2027-03-01T12:00:00Z").cumpleanos(USUARIO, ORG, 29, 2));
		assertTrue(servicio("2028-02-29T12:00:00Z").cumpleanos(USUARIO, ORG, 29, 2));
		assertFalse(servicio("2028-02-28T12:00:00Z").cumpleanos(USUARIO, ORG, 29, 2));
	}

	@Test
	void fichaConFecha_decideSola_aunqueLaCuentaCumplaHoy() {
		fichaPropia(fecha(2010, 3, 14), false);
		assertFalse(servicio("2026-10-28T10:00:00Z").cumpleanos(USUARIO, ORG, 28, 10), "manda la ficha");
		assertTrue(servicio("2026-03-14T10:00:00Z").cumpleanos(USUARIO, ORG, 28, 10));
	}

	@Test
	void fichaSinFecha_decideLaCuenta() {
		fichaPropia(null, false);
		assertTrue(servicio("2026-10-28T10:00:00Z").cumpleanos(USUARIO, ORG, 28, 10));
	}

	@Test
	void fichaAnonimizada_decideLaCuenta() {
		fichaPropia(fecha(2010, 3, 14), true);
		assertTrue(servicio("2026-10-28T10:00:00Z").cumpleanos(USUARIO, ORG, 28, 10));
		assertFalse(servicio("2026-03-14T10:00:00Z").cumpleanos(USUARIO, ORG, 28, 10),
				"la fecha de una ficha anonimizada no cuenta");
	}

	// ------------------------------------------------------------------ la ficha que decide

	@Test
	void cumpleFicha_diaYMes() {
		fichaPropia(fecha(2010, 3, 14), false);
		assertEquals(Optional.of(new PerfilCuentaDTO.DiaMes(14, 3)),
				servicio("2026-10-28T10:00:00Z").cumpleFicha(USUARIO, ORG));
	}

	@Test
	void cumpleFicha_sinFichaSinFechaOAnonimizada_vacia() {
		assertEquals(Optional.empty(), servicio("2026-10-28T10:00:00Z").cumpleFicha(USUARIO, ORG));
		fichaPropia(null, false);
		assertEquals(Optional.empty(), servicio("2026-10-28T10:00:00Z").cumpleFicha(USUARIO, ORG));
		fichaPropia(fecha(2010, 3, 14), true);
		assertEquals(Optional.empty(), servicio("2026-10-28T10:00:00Z").cumpleFicha(USUARIO, ORG));
	}

	@Test
	void cumpleFicha_sinSesion_vacia() {
		assertEquals(Optional.empty(), servicio("2026-10-28T10:00:00Z").cumpleFicha(null, ORG));
		assertEquals(Optional.empty(), servicio("2026-10-28T10:00:00Z").cumpleFicha(USUARIO, null));
	}

	@Test
	void cumpleFicha_conVarias_laDeMenorId_sinLeerLaSiguiente() {
		fichasPropias(50L, 41L);
		ficha(41L, fecha(2010, 3, 14), false);
		ficha(50L, fecha(2011, 7, 2), false);
		assertEquals(Optional.of(new PerfilCuentaDTO.DiaMes(14, 3)),
				servicio("2026-10-28T10:00:00Z").cumpleFicha(USUARIO, ORG));
		verify(alumnos, times(1)).buscarPorIdEnOrganizacionActiva(anyLong());
		verify(alumnos, never()).buscarPorIdEnOrganizacionActiva(50L);
	}

	@Test
	void cumpleFicha_saltaLasQueNoValen() {
		fichasPropias(41L, 42L, 43L);
		ficha(41L, null, false);
		ficha(42L, fecha(2010, 1, 1), true);
		ficha(43L, fecha(2011, 7, 2), false);
		assertEquals(Optional.of(new PerfilCuentaDTO.DiaMes(2, 7)),
				servicio("2026-10-28T10:00:00Z").cumpleFicha(USUARIO, ORG));
	}

	@Test
	void conVariasFichas_decideLaDeMenorId() {
		fichasPropias(41L, 42L);
		ficha(41L, fecha(2010, 3, 14), false);
		ficha(42L, fecha(2011, 10, 28), false);
		assertFalse(servicio("2026-10-28T10:00:00Z").cumpleanos(USUARIO, ORG, null, null));
	}

	@Test
	void fichaDeOtraEscuelaNoEncontrada_noCuenta() {
		fichasPropias(ALUMNO);
		when(alumnos.buscarPorIdEnOrganizacionActiva(ALUMNO)).thenReturn(Optional.empty());
		assertEquals(Optional.empty(), servicio("2026-10-28T10:00:00Z").cumpleFicha(USUARIO, ORG));
	}
}
