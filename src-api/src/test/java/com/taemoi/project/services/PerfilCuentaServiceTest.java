package com.taemoi.project.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.dtos.request.PerfilCuentaRequest;
import com.taemoi.project.dtos.response.PerfilCuentaDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AuditoriaAccion;
import com.taemoi.project.entities.RelacionUsuarioAlumno;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.entities.UsuarioAlumno;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.SedeRepository;
import com.taemoi.project.repositories.UsuarioAlumnoRepository;
import com.taemoi.project.repositories.UsuarioRepository;

/** Decisión 322: el perfil de la cuenta de la sesión, sin Spring. */
class PerfilCuentaServiceTest {

	private static final long USUARIO = 7L;
	private static final long ORG = 1L;
	private static final long FICHA = 40L;
	private static final String RUTA = "/api/panel/mi-perfil";

	private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
	private final UsuarioAlumnoRepository vinculos = mock(UsuarioAlumnoRepository.class);
	private final AlumnoRepository alumnos = mock(AlumnoRepository.class);
	private final AuditoriaService auditoria = mock(AuditoriaService.class);
	private final Usuario gestionada = cuenta();
	private final Usuario principal = cuenta();
	private PerfilCuentaService servicio;

	private static Usuario cuenta() {
		Usuario usuario = new Usuario();
		usuario.setId(USUARIO);
		usuario.setNombre("Lucía");
		usuario.setApellidos("Martín Ruiz");
		return usuario;
	}

	@BeforeEach
	void sesion() {
		Clock reloj = Clock.fixed(Instant.parse("2026-10-28T10:00:00Z"), ZoneId.of("Europe/Madrid"));
		PermisosSesionService permisos = new PermisosSesionService(null, mock(SedeRepository.class), vinculos, alumnos,
				reloj);
		servicio = new PerfilCuentaService(usuarios, permisos, new RegistroAccionesPanel(auditoria));
		when(usuarios.bloquearPorId(USUARIO)).thenReturn(Optional.of(USUARIO));
		when(usuarios.findById(USUARIO)).thenReturn(Optional.of(gestionada));
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
		TenantContext.set(ORG);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		TenantContext.clear();
	}

	private void fichaPropia(LocalDate nacimiento, boolean anonimizada) {
		UsuarioAlumno vinculo = mock(UsuarioAlumno.class);
		when(vinculo.getAlumnoId()).thenReturn(FICHA);
		when(vinculos.findByUsuarioIdAndOrganizacionIdAndRelacion(USUARIO, ORG, RelacionUsuarioAlumno.PROPIO))
				.thenReturn(List.of(vinculo));
		Alumno alumno = new Alumno();
		alumno.setFechaNacimiento(nacimiento == null ? null : java.sql.Date.valueOf(nacimiento));
		if (anonimizada) {
			alumno.setAnonimizadoEn(java.time.LocalDateTime.now());
		}
		when(alumnos.buscarPorIdEnOrganizacionActiva(FICHA)).thenReturn(Optional.of(alumno));
	}

	private void guardados(String nombre, Integer dia, Integer mes) {
		gestionada.setNombrePreferido(nombre);
		gestionada.setCumpleDia(dia);
		gestionada.setCumpleMes(mes);
	}

	private PerfilCuentaDTO guardar(String nombre, Integer dia, Integer mes) {
		return servicio.guardar(new PerfilCuentaRequest(nombre, dia, mes));
	}

	private void noEscribio() {
		assertNull(gestionada.getNombrePreferido());
		assertNull(gestionada.getCumpleDia());
		assertNull(gestionada.getCumpleMes());
		verifyNoInteractions(auditoria);
	}

	private String mensajeDe(Runnable accion) {
		return assertThrows(IllegalArgumentException.class, accion::run).getMessage();
	}

	// ------------------------------------------------------------------ lectura

	@Test
	void obtener_sinPerfil_todoNuloMenosLaBase() {
		assertEquals(new PerfilCuentaDTO(null, "Lucía M.", null, null, null), servicio.obtener());
	}

	@Test
	void obtener_conPerfilYFicha() {
		guardados("Luci", 2, 5);
		fichaPropia(LocalDate.of(1990, 3, 14), false);
		assertEquals(new PerfilCuentaDTO("Luci", "Lucía M.", 2, 5, new PerfilCuentaDTO.DiaMes(14, 3)),
				servicio.obtener());
	}

	@Test
	void obtener_fichaAnonimizadaOSinFecha_sinCumpleFicha() {
		fichaPropia(LocalDate.of(1990, 3, 14), true);
		assertNull(servicio.obtener().cumpleFicha());
		fichaPropia(null, false);
		assertNull(servicio.obtener().cumpleFicha());
	}

	@Test
	void obtener_sinNombre_nombreBaseNulo() {
		gestionada.setNombre("  ");
		assertNull(servicio.obtener().nombreBase());
	}

	@Test
	void sinUsuarioUOrganizacion_accesoDenegado() {
		TenantContext.clear();
		assertThrows(AccessDeniedException.class, servicio::obtener);
		assertThrows(AccessDeniedException.class, () -> guardar("Luci", null, null));
		TenantContext.set(ORG);
		SecurityContextHolder.clearContext();
		assertThrows(AccessDeniedException.class, servicio::obtener);
		assertThrows(AccessDeniedException.class, () -> guardar("Luci", null, null));
		noEscribio();
	}

	// ------------------------------------------------------------------ nombre

	@Test
	void nombre_recortaYColapsaEspacios() {
		assertEquals("Ana María", guardar("  Ana   María  ", null, null).nombrePreferido());
		assertEquals("Ana María", gestionada.getNombrePreferido());
		assertEquals("Ana María", guardar(" Ana 　María ", null, null).nombrePreferido(),
				"también los espacios Unicode");
	}

	@Test
	void nombre_vacioPasaANull() {
		guardados("Luci", null, null);
		assertNull(guardar("   ", null, null).nombrePreferido());
		assertNull(gestionada.getNombrePreferido());
		guardados("Luci", null, null);
		assertNull(guardar("", null, null).nombrePreferido());
		guardados("Luci", null, null);
		assertNull(guardar(null, null, null).nombrePreferido());
		assertNull(gestionada.getNombrePreferido());
	}

	@Test
	void nombre_limiteDeCuarenta() {
		assertEquals("a".repeat(40), guardar("a".repeat(40), null, null).nombrePreferido());
		assertEquals("a".repeat(40), guardar("  " + "a".repeat(40) + "  ", null, null).nombrePreferido(),
				"se mide tras recortar");
		gestionada.setNombrePreferido(null);
		assertEquals(PerfilCuentaService.NOMBRE_LARGO, mensajeDe(() -> guardar("a".repeat(41), null, null)));
		assertNull(gestionada.getNombrePreferido());
	}

	@Test
	void nombre_elLimiteEsDePuntosDeCodigo() {
		String cuarenta = "😀".repeat(40);
		assertEquals(80, cuarenta.length());
		assertEquals(cuarenta, guardar(cuarenta, null, null).nombrePreferido());
		gestionada.setNombrePreferido(null);
		assertEquals(PerfilCuentaService.NOMBRE_LARGO, mensajeDe(() -> guardar(cuarenta + "😀", null, null)));
	}

	@Test
	void nombre_caracteresDeControl_400() {
		for (String nombre : List.of("Lu\u0000ci", "Lu\nci", "Lu\tci", "Luci\r\n", "\u0007", "Lu\u007Fci", "Lu\u0085ci",
				"Lu\u009Fci")) {
			assertEquals(PerfilCuentaService.NOMBRE_NO_VALIDO, mensajeDe(() -> guardar(nombre, null, null)), nombre);
		}
		noEscribio();
	}

	@Test
	void nombre_surrogadoSuelto_400() {
		assertEquals(PerfilCuentaService.NOMBRE_NO_VALIDO, mensajeDe(() -> guardar("Lu\uD83Dci", null, null)));
		noEscribio();
	}

	@Test
	void nombre_siNoValeNoSeGuardaElCumple() {
		assertThrows(IllegalArgumentException.class, () -> guardar("a".repeat(41), 14, 3));
		noEscribio();
	}

	// ------------------------------------------------------------------ cumpleaños

	@Test
	void cumple_dosNulos_borra() {
		guardados(null, 14, 3);
		PerfilCuentaDTO perfil = guardar(null, null, null);
		assertNull(perfil.cumpleDia());
		assertNull(perfil.cumpleMes());
		assertNull(gestionada.getCumpleDia());
		assertNull(gestionada.getCumpleMes());
	}

	@Test
	void cumple_dosValidos_guarda() {
		PerfilCuentaDTO perfil = guardar(null, 14, 3);
		assertEquals(14, perfil.cumpleDia());
		assertEquals(3, perfil.cumpleMes());
		assertEquals(14, gestionada.getCumpleDia());
		assertEquals(3, gestionada.getCumpleMes());
	}

	@Test
	void cumple_soloUno_400() {
		assertEquals(PerfilCuentaService.CUMPLE_INCOMPLETO, mensajeDe(() -> guardar(null, 14, null)));
		assertEquals(PerfilCuentaService.CUMPLE_INCOMPLETO, mensajeDe(() -> guardar(null, null, 3)));
		noEscribio();
	}

	@Test
	void cumple_mensajesDelBoceto() {
		assertEquals("Elige día y mes, o deja los dos vacíos.", PerfilCuentaService.CUMPLE_INCOMPLETO);
		assertEquals("Esa fecha no existe.", PerfilCuentaService.CUMPLE_IMPOSIBLE);
	}

	@ParameterizedTest
	@CsvSource({ "29,2", "28,2", "31,1", "31,3", "31,5", "31,7", "31,8", "31,10", "31,12", "30,4", "30,6", "30,9",
			"30,11", "1,1" })
	void cumple_fechasValidas(int dia, int mes) {
		assertEquals(dia, guardar(null, dia, mes).cumpleDia());
	}

	@ParameterizedTest
	@CsvSource({ "30,2", "31,2", "31,4", "31,6", "31,9", "31,11", "0,1", "32,1", "-1,1", "1,0", "1,13", "1,-1",
			"300,300" })
	void cumple_fechasImposibles_400(int dia, int mes) {
		assertEquals(PerfilCuentaService.CUMPLE_IMPOSIBLE, mensajeDe(() -> guardar(null, dia, mes)));
		noEscribio();
	}

	// ------------------------------------------------------------------ la ficha manda (invariante multiescuela)

	@Test
	void conFicha_elCumpleDelCuerpoSeIgnora_yLoGuardadoNoSeToca() {
		guardados(null, 2, 5);
		fichaPropia(LocalDate.of(1990, 3, 14), false);
		PerfilCuentaDTO perfil = guardar("Luci", 20, 7);
		assertEquals(2, gestionada.getCumpleDia());
		assertEquals(5, gestionada.getCumpleMes());
		assertEquals(2, perfil.cumpleDia());
		assertEquals(5, perfil.cumpleMes());
		assertEquals(new PerfilCuentaDTO.DiaMes(14, 3), perfil.cumpleFicha());
		assertEquals("Luci", gestionada.getNombrePreferido(), "el nombre sí se guarda");
	}

	@Test
	void conFicha_cuerpoConCumpleNulo_noBorraLoGuardado() {
		guardados(null, 2, 5);
		fichaPropia(LocalDate.of(1990, 3, 14), false);
		guardar(null, null, null);
		assertEquals(2, gestionada.getCumpleDia());
		assertEquals(5, gestionada.getCumpleMes());
	}

	@Test
	void conFicha_cumpleImposibleOIncompleto_noEsError() {
		fichaPropia(LocalDate.of(1990, 3, 14), false);
		guardar(null, 31, 4);
		guardar(null, 14, null);
		assertNull(gestionada.getCumpleDia());
		assertNull(gestionada.getCumpleMes());
	}

	@Test
	void fichaAnonimizadaOSinFecha_elCuerpoCuenta() {
		fichaPropia(LocalDate.of(1990, 3, 14), true);
		assertEquals(20, guardar(null, 20, 7).cumpleDia());
		fichaPropia(null, false);
		assertEquals(21, guardar(null, 21, 7).cumpleDia());
		assertEquals(PerfilCuentaService.CUMPLE_IMPOSIBLE, mensajeDe(() -> guardar(null, 31, 4)));
	}

	// ------------------------------------------------------------------ escritura

	@Test
	void guardar_sinCuerpo_400_noBorraNada() {
		guardados("Luci", 14, 3);
		assertEquals(PerfilCuentaService.SIN_DATOS, mensajeDe(() -> servicio.guardar(null)));
		assertEquals("Luci", gestionada.getNombrePreferido());
		assertEquals(14, gestionada.getCumpleDia());
		verify(usuarios, never()).bloquearPorId(any());
	}

	@Test
	void guardar_bloqueaLaFilaAntesDeLeerla_yEscribeEnLaGestionada() {
		guardar("Luci", 14, 3);
		InOrder orden = inOrder(usuarios);
		orden.verify(usuarios).bloquearPorId(USUARIO);
		orden.verify(usuarios).findById(USUARIO);
		assertEquals("Luci", gestionada.getNombrePreferido());
		assertNull(principal.getNombrePreferido(), "el principal de la sesión no se toca");
		assertNull(principal.getCumpleDia());
	}

	@Test
	void guardar_cuentaDesaparecida_accesoDenegado() {
		when(usuarios.bloquearPorId(USUARIO)).thenReturn(Optional.empty());
		assertThrows(AccessDeniedException.class, () -> guardar("Luci", null, null));
		verifyNoInteractions(auditoria);
	}

	@Test
	void guardar_devuelveLoMismoQueElGet() {
		fichaPropia(LocalDate.of(1990, 3, 14), false);
		assertEquals(guardar("Luci", null, null), servicio.obtener());
	}

	// ------------------------------------------------------------------ registro

	private String resumenRegistrado() {
		ArgumentCaptor<String> resumen = ArgumentCaptor.forClass(String.class);
		verify(auditoria).registrarAccesoSensible(eq(AuditoriaAccion.UPDATE), eq(RegistroAccionesPanel.MODULO), eq(RUTA),
				eq(USUARIO), resumen.capture());
		return resumen.getValue();
	}

	@Test
	void registro_soloLaCuentaYQueCambio_nuncaElNombreNiLaFecha() {
		guardar("Luci", 14, 3);
		String resumen = resumenRegistrado();
		assertEquals("PANEL PERFIL_CAMBIADO usuario=#7", resumen);
		assertFalse(resumen.contains("Luci"));
		assertFalse(resumen.contains("14"));
	}

	@Test
	void registro_sinCambios_tambienSeRegistra() {
		guardados("Luci", 14, 3);
		guardar("Luci", 14, 3);
		assertEquals("PANEL PERFIL_CAMBIADO usuario=#7 sinCambios=SI", resumenRegistrado());
	}

	@Test
	void registro_conFicha_elCumpleDelCuerpoNoCuentaComoCambio() {
		guardados("Luci", null, null);
		fichaPropia(LocalDate.of(1990, 3, 14), false);
		guardar("Luci", 20, 7);
		assertEquals("PANEL PERFIL_CAMBIADO usuario=#7 sinCambios=SI", resumenRegistrado());
	}

	@Test
	void registro_siFallaLaValidacion_noSeRegistra() {
		assertThrows(IllegalArgumentException.class, () -> guardar(null, 31, 4));
		verifyNoInteractions(auditoria);
	}

	// ------------------------------------------------------------------ la entidad en JSON

	/**
	 * {@code Alumno#getUsuario} saca la entidad en el JSON de alumno ({@code GET /api/admin/{id}}): los datos del perfil
	 * no pueden llegar a otra cuenta por ahí.
	 */
	@Test
	void entidad_fueraDelJson() {
		guardados("Luci", 14, 3);
		JsonNode nodo = new ObjectMapper().findAndRegisterModules().valueToTree(gestionada);
		assertTrue(nodo.has("nombre"), nodo.toString());
		assertFalse(nodo.has("nombrePreferido"), nodo.toString());
		assertFalse(nodo.has("cumpleDia"), nodo.toString());
		assertFalse(nodo.has("cumpleMes"), nodo.toString());
	}
}
