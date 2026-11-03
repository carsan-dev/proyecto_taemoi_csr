package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.OrigenUsuarioAlumno;
import com.taemoi.project.entities.RelacionUsuarioAlumno;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.entities.UsuarioAlumno;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioAlumnoRepository;
import com.taemoi.project.services.impl.VinculoFamiliaServiceImpl;

/**
 * Escritura de {@code usuario_alumno} en las altas (registro y Google; fase 3, entrega 2b): origen REGISTRO,
 * relación por edad como V31, organización del alumno = la de la sesión, sin duplicar la PK.
 */
class VinculoFamiliaServiceImplTest {

	private final UsuarioAlumnoRepository vinculos = mock(UsuarioAlumnoRepository.class);
	private final VinculoFamiliaServiceImpl servicio = new VinculoFamiliaServiceImpl(vinculos);

	@AfterEach
	void limpiar() {
		TenantContext.clear();
	}

	@Test
	void escribeLaFilaConOrigenRegistroYLaOrganizacionDelAlumno() {
		TenantContext.set(2L);
		Alumno menor = alumno(5L, 2L, LocalDate.now().minusYears(10));

		assertTrue(servicio.vincularAlta(cuenta(9L), menor));

		ArgumentCaptor<UsuarioAlumno> fila = ArgumentCaptor.forClass(UsuarioAlumno.class);
		verify(vinculos).save(fila.capture());
		assertEquals(9L, fila.getValue().getUsuarioId());
		assertEquals(5L, fila.getValue().getAlumnoId());
		assertEquals(2L, fila.getValue().getOrganizacionId());
		assertEquals(OrigenUsuarioAlumno.REGISTRO, fila.getValue().getOrigen());
		assertEquals(RelacionUsuarioAlumno.TUTOR, fila.getValue().getRelacion());
	}

	// ------------------------------------------------------------------ pieza E10-2: lecturas

	@Test
	void cuentasDeLaFicha_filtraPorLaOrganizacionYOrdenaPorCuenta() {
		when(vinculos.findByAlumnoIdAndOrganizacionId(5L, 2L)).thenReturn(List.of(
				new UsuarioAlumno(9L, 5L, 2L, RelacionUsuarioAlumno.TUTOR, OrigenUsuarioAlumno.MIGRACION),
				new UsuarioAlumno(3L, 5L, 2L, RelacionUsuarioAlumno.PROPIO, OrigenUsuarioAlumno.REGISTRO)));

		assertEquals(List.of(3L, 9L), servicio.cuentasDeLaFicha(5L, 2L).stream().map(UsuarioAlumno::getUsuarioId)
				.toList());
		verify(vinculos).findByAlumnoIdAndOrganizacionId(5L, 2L);
	}

	@Test
	void cuentasDeLaFicha_sinAlumnoOSinOrganizacion_vacioSinConsultar() {
		assertEquals(List.of(), servicio.cuentasDeLaFicha(null, 2L));
		assertEquals(List.of(), servicio.cuentasDeLaFicha(5L, null));
		verify(vinculos, never()).findByAlumnoIdAndOrganizacionId(any(), any());
	}

	@Test
	void fichasPropiasDeLaCuenta_soloPropioDeEsaOrganizacionEnOrden() {
		when(vinculos.findByUsuarioIdAndOrganizacionIdAndRelacion(9L, 2L, RelacionUsuarioAlumno.PROPIO))
				.thenReturn(List.of(
						new UsuarioAlumno(9L, 8L, 2L, RelacionUsuarioAlumno.PROPIO, OrigenUsuarioAlumno.MANUAL),
						new UsuarioAlumno(9L, 4L, 2L, RelacionUsuarioAlumno.PROPIO, OrigenUsuarioAlumno.MIGRACION)));

		assertEquals(List.of(4L, 8L), servicio.fichasPropiasDeLaCuenta(9L, 2L));
		assertEquals(List.of(), servicio.fichasPropiasDeLaCuenta(9L, null));
		assertEquals(List.of(), servicio.fichasPropiasDeLaCuenta(null, 2L));
	}

	@Test
	void mayorDeEdadEsPropio() {
		TenantContext.set(1L);

		servicio.vincularAlta(cuenta(9L), alumno(5L, 1L, LocalDate.now().minusYears(30)));

		ArgumentCaptor<UsuarioAlumno> fila = ArgumentCaptor.forClass(UsuarioAlumno.class);
		verify(vinculos).save(fila.capture());
		assertEquals(RelacionUsuarioAlumno.PROPIO, fila.getValue().getRelacion());
	}

	@Test
	void relacionPorEdadComoV31() {
		LocalDate hoy = LocalDate.of(2026, 10, 15);
		assertEquals(RelacionUsuarioAlumno.PROPIO, RelacionUsuarioAlumno.porEdad(LocalDate.of(2008, 10, 15), hoy));
		assertEquals(RelacionUsuarioAlumno.TUTOR, RelacionUsuarioAlumno.porEdad(LocalDate.of(2008, 10, 16), hoy));
		assertEquals(RelacionUsuarioAlumno.PROPIO, RelacionUsuarioAlumno.porEdad(LocalDate.of(1980, 1, 1), hoy));
		assertEquals(RelacionUsuarioAlumno.TUTOR, RelacionUsuarioAlumno.porEdad(null, hoy));
		// 29 de febrero: como TIMESTAMPDIFF(YEAR, ...), los 18 se cumplen el 1 de marzo en un año no bisiesto.
		LocalDate bisiesto = LocalDate.of(2008, 2, 29);
		assertEquals(RelacionUsuarioAlumno.TUTOR, RelacionUsuarioAlumno.porEdad(bisiesto, LocalDate.of(2026, 2, 28)));
		assertEquals(RelacionUsuarioAlumno.PROPIO, RelacionUsuarioAlumno.porEdad(bisiesto, LocalDate.of(2026, 3, 1)));
	}

	@Test
	void sinFechaDeNacimientoEsTutor() {
		TenantContext.set(1L);
		Alumno sinFecha = alumno(5L, 1L, null);

		servicio.vincularAlta(cuenta(9L), sinFecha);

		ArgumentCaptor<UsuarioAlumno> fila = ArgumentCaptor.forClass(UsuarioAlumno.class);
		verify(vinculos).save(fila.capture());
		assertEquals(RelacionUsuarioAlumno.TUTOR, fila.getValue().getRelacion());
	}

	@Test
	void filaExistenteNoSeDuplicaNiSeToca() {
		TenantContext.set(1L);
		when(vinculos.existsById(new UsuarioAlumno.Clave(9L, 5L))).thenReturn(true);

		assertFalse(servicio.vincularAlta(cuenta(9L), alumno(5L, 1L, LocalDate.now().minusYears(10))));

		verify(vinculos, never()).save(any());
	}

	@Test
	void alumnoDeOtraEscuelaQueLaDeLaSesionNoSeVincula() {
		TenantContext.set(1L);

		assertThrows(IllegalStateException.class,
				() -> servicio.vincularAlta(cuenta(9L), alumno(5L, 2L, LocalDate.now().minusYears(10))));

		verify(vinculos, never()).save(any());
	}

	@Test
	void sinEscuelaEnLaSesionNoSeVincula() {
		assertThrows(IllegalStateException.class,
				() -> servicio.vincularAlta(cuenta(9L), alumno(5L, 1L, LocalDate.now().minusYears(10))));

		verify(vinculos, never()).save(any());
	}

	@Test
	void cuentaOAlumnoSinIdNoSeVincula() {
		TenantContext.set(1L);

		assertThrows(IllegalArgumentException.class,
				() -> servicio.vincularAlta(cuenta(null), alumno(5L, 1L, null)));
		assertThrows(IllegalArgumentException.class,
				() -> servicio.vincularAlta(cuenta(9L), alumno(null, 1L, null)));

		verify(vinculos, never()).save(any());
	}

	private static Usuario cuenta(Long id) {
		Usuario u = new Usuario();
		u.setId(id);
		u.setEmail("familia@example.test");
		return u;
	}

	private static Alumno alumno(Long id, Long organizacionId, LocalDate nacimiento) {
		Alumno a = new Alumno();
		a.setId(id);
		a.setEmail("familia@example.test");
		a.setActivo(true);
		a.setFechaNacimiento(nacimiento == null ? null : Date.valueOf(nacimiento));
		ReflectionTestUtils.setField(a, "organizacionId", organizacionId);
		return a;
	}
}
