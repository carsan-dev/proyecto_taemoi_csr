package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.services.impl.AccesoFamiliaServiceImpl;

/**
 * Parámetros con los que el servicio central del acceso de familia llama a su consulta: email de la cuenta
 * normalizado como {@code EmailUtils.normalizeEmail}; un email en blanco se pasa vacío (nunca vincula por
 * email, pero los vínculos explícitos siguen contando). La regla en sí se prueba contra MySQL en
 * {@code AccesoFamiliaEquivalenciaIntegrationTest}.
 */
class AccesoFamiliaServiceImplTest {

	private final AlumnoRepository alumnos = mock(AlumnoRepository.class);
	private final AccesoFamiliaServiceImpl servicio = new AccesoFamiliaServiceImpl(alumnos);

	@AfterEach
	void limpiar() {
		TenantContext.clear();
	}

	@Test
	void consultaConElEmailNormalizadoDeLaCuenta() {
		Usuario usuario = cuenta(10L, " Familia@Example.TEST ");
		when(alumnos.idsAccesiblesComoFamilia(10L, "familia@example.test", 1L)).thenReturn(List.of(3L, 7L));
		when(alumnos.contarAccesiblesComoFamilia(10L, "familia@example.test", 1L)).thenReturn(2L);
		when(alumnos.contarAlumnoAccesibleComoFamilia(10L, "familia@example.test", 1L, 7L)).thenReturn(1L);

		assertEquals(List.of(3L, 7L), servicio.idsAccesibles(usuario, 1L));
		assertTrue(servicio.tieneAlgunoAccesible(usuario, 1L));
		assertTrue(servicio.puedeAcceder(usuario, 1L, 7L));
		assertFalse(servicio.puedeAcceder(usuario, 1L, 8L));
	}

	@Test
	void emailEnBlancoSeConsultaVacioParaQueSoloCuentenLosVinculos() {
		for (String email : new String[] { null, "", "   ", "\t" }) {
			Usuario usuario = cuenta(11L, email);
			servicio.idsAccesibles(usuario, 2L);
			servicio.tieneAlgunoAccesible(usuario, 2L);
			servicio.puedeAcceder(usuario, 2L, 5L);
		}
		verify(alumnos, org.mockito.Mockito.times(4)).idsAccesiblesComoFamilia(11L, "", 2L);
		verify(alumnos, org.mockito.Mockito.times(4)).contarAccesiblesComoFamilia(11L, "", 2L);
		verify(alumnos, org.mockito.Mockito.times(4)).contarAlumnoAccesibleComoFamilia(11L, "", 2L, 5L);
	}

	@Test
	void sinCuentaOrganizacionOAlumnoNoHayAccesoNiConsulta() {
		Usuario sinId = cuenta(null, "familia@example.test");
		Usuario usuario = cuenta(10L, "familia@example.test");

		assertEquals(List.of(), servicio.idsAccesibles(null, 1L));
		assertEquals(List.of(), servicio.idsAccesibles(sinId, 1L));
		assertEquals(List.of(), servicio.idsAccesibles(usuario, null));
		assertFalse(servicio.tieneAlgunoAccesible(usuario, null));
		assertFalse(servicio.puedeAcceder(usuario, 1L, null));
		assertFalse(servicio.puedeAcceder(usuario, null, 4L));
		assertEquals(List.of(), servicio.alumnosAccesiblesEnOrganizacionActiva(usuario));
		verifyNoInteractions(alumnos);
	}

	@Test
	void cargaLosAlumnosDeLaOrganizacionActivaEnOrdenDeId() {
		Usuario usuario = cuenta(10L, "familia@example.test");
		Alumno tres = alumno(3L);
		Alumno siete = alumno(7L);
		when(alumnos.idsAccesiblesComoFamilia(10L, "familia@example.test", 4L)).thenReturn(List.of(3L, 7L));
		when(alumnos.findAllById(List.of(3L, 7L))).thenReturn(List.of(siete, tres));

		TenantContext.set(4L);
		List<Alumno> cargados = servicio.alumnosAccesiblesEnOrganizacionActiva(usuario);

		assertEquals(List.of(tres, siete), cargados);
	}

	@Test
	void sinAlumnosAccesiblesNoCargaNada() {
		Usuario usuario = cuenta(10L, "familia@example.test");
		when(alumnos.idsAccesiblesComoFamilia(10L, "familia@example.test", 4L)).thenReturn(List.of());

		TenantContext.set(4L);
		assertEquals(List.of(), servicio.alumnosAccesiblesEnOrganizacionActiva(usuario));
		verify(alumnos, never()).findAllById(org.mockito.ArgumentMatchers.any());
		verify(alumnos, never()).contarAccesiblesComoFamilia(anyLong(), anyString(), anyLong());
	}

	private static Usuario cuenta(Long id, String email) {
		Usuario usuario = new Usuario();
		usuario.setId(id);
		ReflectionTestUtils.setField(usuario, "email", email);
		return usuario;
	}

	private static Alumno alumno(Long id) {
		Alumno alumno = new Alumno();
		alumno.setId(id);
		return alumno;
	}
}
