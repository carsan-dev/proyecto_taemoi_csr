package com.taemoi.project.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.RelacionUsuarioAlumno;
import com.taemoi.project.entities.UsuarioAlumno;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.SedeRepository;
import com.taemoi.project.repositories.UsuarioAlumnoRepository;

/** Pieza 8b-M2: el booleano {@code cumpleanos} de {@code GET /api/auth/permisos}, sin Spring. */
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
		UsuarioAlumno vinculo = mock(UsuarioAlumno.class);
		when(vinculo.getAlumnoId()).thenReturn(ALUMNO);
		when(vinculos.findByUsuarioIdAndOrganizacionIdAndRelacion(USUARIO, ORG, RelacionUsuarioAlumno.PROPIO))
				.thenReturn(List.of(vinculo));
		Alumno alumno = new Alumno();
		alumno.setFechaNacimiento(nacimiento);
		if (anonimizado) {
			alumno.setAnonimizadoEn(java.time.LocalDateTime.now());
		}
		when(alumnos.buscarPorIdEnOrganizacionActiva(ALUMNO)).thenReturn(Optional.of(alumno));
	}

	@Test
	void cumpleHoy_true() {
		fichaPropia(fecha(2010, 10, 28), false);
		assertTrue(servicio("2026-10-28T10:00:00Z").cumpleanos(USUARIO, ORG));
	}

	@Test
	void hoyEsMadridNoUtc() {
		fichaPropia(fecha(2010, 10, 29), false);
		// 28/10 23:30 UTC ya es 29/10 en Madrid.
		assertTrue(servicio("2026-10-28T23:30:00Z").cumpleanos(USUARIO, ORG));
	}

	@Test
	void otroDia_false() {
		fichaPropia(fecha(2010, 10, 27), false);
		assertFalse(servicio("2026-10-28T10:00:00Z").cumpleanos(USUARIO, ORG));
	}

	@Test
	void relacionTutor_false() {
		// Solo hay ficha TUTOR: la consulta por PROPIO no devuelve nada.
		when(vinculos.findByUsuarioIdAndOrganizacionIdAndRelacion(USUARIO, ORG, RelacionUsuarioAlumno.TUTOR))
				.thenReturn(List.of(mock(UsuarioAlumno.class)));
		assertFalse(servicio("2026-10-28T10:00:00Z").cumpleanos(USUARIO, ORG));
		verify(vinculos).findByUsuarioIdAndOrganizacionIdAndRelacion(USUARIO, ORG, RelacionUsuarioAlumno.PROPIO);
	}

	@Test
	void sinFicha_false() {
		assertFalse(servicio("2026-10-28T10:00:00Z").cumpleanos(USUARIO, ORG));
	}

	@Test
	void fichaDeOtraOrganizacion_false() {
		fichaPropia(fecha(2010, 10, 28), false);
		assertFalse(servicio("2026-10-28T10:00:00Z").cumpleanos(USUARIO, OTRA_ORG), "se filtra por la organización de la sesión");
		verify(vinculos).findByUsuarioIdAndOrganizacionIdAndRelacion(eq(USUARIO), eq(OTRA_ORG), any());
	}

	@Test
	void fechaNula_false() {
		fichaPropia(null, false);
		assertFalse(servicio("2026-10-28T10:00:00Z").cumpleanos(USUARIO, ORG));
	}

	@Test
	void anonimizado_false() {
		fichaPropia(fecha(2010, 10, 28), true);
		assertFalse(servicio("2026-10-28T10:00:00Z").cumpleanos(USUARIO, ORG));
	}

	@Test
	void sinSesion_false() {
		assertFalse(servicio("2026-10-28T10:00:00Z").cumpleanos(null, ORG));
		assertFalse(servicio("2026-10-28T10:00:00Z").cumpleanos(USUARIO, null));
	}

	@Test
	void veintinueveFebrero_enAnioNoBisiestoSeCelebraElVeintiocho() {
		Date nacimiento = fecha(2012, 2, 29);
		assertTrue(PermisosSesionService.esCumpleanos(nacimiento, LocalDate.of(2027, 2, 28)));
		assertFalse(PermisosSesionService.esCumpleanos(nacimiento, LocalDate.of(2027, 3, 1)));
		// En año bisiesto es el 29, no el 28.
		assertTrue(PermisosSesionService.esCumpleanos(nacimiento, LocalDate.of(2028, 2, 29)));
		assertFalse(PermisosSesionService.esCumpleanos(nacimiento, LocalDate.of(2028, 2, 28)));
	}

	@Test
	void veintiochoFebrero_nacidoNormal_noSeDuplica() {
		assertTrue(PermisosSesionService.esCumpleanos(fecha(2012, 2, 28), LocalDate.of(2028, 2, 28)));
	}

	@Test
	void veintinueveFebrero_conReloj() {
		fichaPropia(fecha(2012, 2, 29), false);
		assertTrue(servicio("2027-02-28T12:00:00Z").cumpleanos(USUARIO, ORG));
	}
}
