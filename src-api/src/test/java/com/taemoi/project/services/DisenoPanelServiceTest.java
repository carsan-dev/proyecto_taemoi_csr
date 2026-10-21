package com.taemoi.project.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.taemoi.project.dtos.request.DisenoPanelRequests;
import com.taemoi.project.dtos.response.DisenoPanelDTO;
import com.taemoi.project.entities.ConfiguracionOrganizacion;
import com.taemoi.project.entities.DisenoPanelUsuario;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.ConfiguracionOrganizacionRepository;
import com.taemoi.project.repositories.DisenoPanelUsuarioRepository;
import com.taemoi.project.repositories.MembresiaRepository;
import com.taemoi.project.repositories.UsuarioRepository;

/**
 * Regla efectiva del interruptor del diseño nuevo (pieza 8b-I, fase3-e8b-plan.md §3), sin Spring: con fila y
 * ({@code permitido} o el por defecto NUEVO), su {@code activo}; si no, el valor por defecto de la escuela
 * ({@code panel.diseno_por_defecto}; sin la clave, VIEJO). {@code permitido} es solo el que da el propietario
 * (decisión 256): {@code mi-diseno} nunca lo toca. Que la sesión pueda cambiarlo para sí: fila permitida o NUEVO.
 */
class DisenoPanelServiceTest {

	private static final long USUARIO = 41L;
	private static final long ESCUELA = 7L;

	DisenoPanelUsuarioRepository filas;
	ConfiguracionOrganizacionRepository configuracion;
	MembresiaRepository membresias;
	MembresiaService membresiaService;
	UsuarioRepository usuarios;
	DisenoPanelService servicio;

	@BeforeEach
	void preparar() {
		filas = mock(DisenoPanelUsuarioRepository.class);
		configuracion = mock(ConfiguracionOrganizacionRepository.class);
		membresias = mock(MembresiaRepository.class);
		membresiaService = mock(MembresiaService.class);
		usuarios = mock(UsuarioRepository.class);
		servicio = new DisenoPanelService(filas, configuracion, membresias, membresiaService, usuarios,
				mock(RegistroAccionesPanel.class));
		when(filas.findByUsuarioId(USUARIO)).thenReturn(Optional.empty());
		when(configuracion.findByClave(DisenoPanelService.CLAVE_POR_DEFECTO)).thenReturn(Optional.empty());
	}

	@AfterEach
	void limpiar() {
		TenantContext.clear();
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ regla efectiva

	@Test
	void sinFilaNiClave_apagado() {
		assertFalse(servicio.activoPara(USUARIO));
		assertEquals(new DisenoPanelDTO.Propio(false, false), servicio.estadoPropio(USUARIO));
	}

	@Test
	void sinFila_porDefectoNuevo_encendido() {
		porDefecto("NUEVO");
		assertTrue(servicio.activoPara(USUARIO));
		assertEquals(new DisenoPanelDTO.Propio(true, true), servicio.estadoPropio(USUARIO),
				"con NUEVO puede volver al anterior para sí");
	}

	@Test
	void sinFila_porDefectoViejoOValorDesconocido_apagado() {
		porDefecto("VIEJO");
		assertFalse(servicio.activoPara(USUARIO));
		porDefecto("nuevo ");
		assertFalse(servicio.activoPara(USUARIO), "solo NUEVO exacto enciende");
	}

	@Test
	void conPermisoDelPropietario_mantieneSuEleccionEnNuevoYEnViejo() {
		for (String valor : List.of("VIEJO", "NUEVO")) {
			porDefecto(valor);
			fila(true, true);
			assertTrue(servicio.activoPara(USUARIO), valor);
			assertEquals(new DisenoPanelDTO.Propio(true, true), servicio.estadoPropio(USUARIO), valor);
			fila(true, false);
			assertFalse(servicio.activoPara(USUARIO), valor);
			assertEquals(new DisenoPanelDTO.Propio(false, true), servicio.estadoPropio(USUARIO), valor);
		}
	}

	@Test
	void porDefectoNuevo_filaSinPermisoApagada_mandaSuEleccion() {
		porDefecto("NUEVO");
		fila(false, false);
		assertFalse(servicio.activoPara(USUARIO), "con NUEVO su elección cuenta aunque no tenga permiso");
		assertEquals(new DisenoPanelDTO.Propio(false, true), servicio.estadoPropio(USUARIO),
				"puede volver a encenderlo (por el NUEVO), pero no tiene permiso del propietario");
	}

	@Test
	void vueltaAViejo_filaSinPermiso_quedaEnViejoAunqueTengaActivo() {
		porDefecto("VIEJO");
		fila(false, true);
		assertFalse(servicio.activoPara(USUARIO));
		assertEquals(new DisenoPanelDTO.Propio(false, false), servicio.estadoPropio(USUARIO));
	}

	@Test
	void sinUsuario_apagado() {
		porDefecto("NUEVO");
		assertFalse(servicio.activoPara(null));
		assertEquals(new DisenoPanelDTO.Propio(false, false), servicio.estadoPropio(null));
	}

	// ------------------------------------------------------------------ mi-diseno no da permiso

	@Test
	void miDiseno_porDefectoNuevoSinFila_creaLaFilaSinPermiso() {
		porDefecto("NUEVO");
		sesionDeGestion();
		DisenoPanelDTO.Propio respuesta = TenantContext.calcularCon(ESCUELA,
				() -> servicio.cambiarMiDiseno(new DisenoPanelRequests.MiDiseno(false)));
		DisenoPanelUsuario guardada = guardada();
		assertFalse(guardada.isPermitido(), "mi-diseno nunca da permiso");
		assertFalse(guardada.isActivo());
		assertEquals(USUARIO, guardada.getUsuarioId());
		assertFalse(respuesta.activo());
	}

	@Test
	void miDiseno_porDefectoNuevoConPermisoQuitado_noLoDevuelve() {
		porDefecto("NUEVO");
		sesionDeGestion();
		DisenoPanelUsuario quitado = fila(false, true);
		TenantContext.calcularCon(ESCUELA, () -> servicio.cambiarMiDiseno(new DisenoPanelRequests.MiDiseno(false)));
		assertFalse(guardada().isPermitido());
		assertFalse(quitado.isActivo());
		// El propietario vuelve a VIEJO: sin permiso, en VIEJO, aunque encienda su fila otra vez.
		quitado.setActivo(true);
		porDefecto("VIEJO");
		assertFalse(servicio.activoPara(USUARIO));
		assertFalse(servicio.estadoPropio(USUARIO).permitido());
	}

	@Test
	void miDiseno_conPermiso_soloCambiaActivo() {
		sesionDeGestion();
		fila(true, true);
		TenantContext.calcularCon(ESCUELA, () -> servicio.cambiarMiDiseno(new DisenoPanelRequests.MiDiseno(false)));
		DisenoPanelUsuario guardada = guardada();
		assertTrue(guardada.isPermitido());
		assertFalse(guardada.isActivo());
	}

	// ------------------------------------------------------------------ clave única a la vez: 409

	@Test
	void claveUnicaAlaVez_alGuardarElPermiso_409ConMensaje() {
		sesionDeGestion();
		when(membresiaService.esUsuarioDeOrganizacion(USUARIO, ESCUELA)).thenReturn(true);
		when(filas.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_diseno_panel_usuario"));
		IllegalStateException error = assertThrows(IllegalStateException.class, () -> TenantContext.calcularCon(ESCUELA,
				() -> servicio.cambiarPermiso(USUARIO, new DisenoPanelRequests.Permiso(true))));
		assertEquals(DisenoPanelService.A_LA_VEZ, error.getMessage());
	}

	@Test
	void claveUnicaAlaVez_enMiDiseno_409ConMensaje() {
		porDefecto("NUEVO");
		sesionDeGestion();
		when(filas.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_diseno_panel_usuario"));
		IllegalStateException error = assertThrows(IllegalStateException.class, () -> TenantContext.calcularCon(ESCUELA,
				() -> servicio.cambiarMiDiseno(new DisenoPanelRequests.MiDiseno(false))));
		assertEquals(DisenoPanelService.A_LA_VEZ, error.getMessage());
	}

	// ------------------------------------------------------------------ utilidades

	private void sesionDeGestion() {
		Usuario cuenta = new Usuario();
		cuenta.setId(USUARIO);
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(cuenta, null, List.of()));
		when(usuarios.bloquearPorId(USUARIO)).thenReturn(Optional.of(USUARIO));
		when(membresias.findRolesActivos(USUARIO, ESCUELA)).thenReturn(List.of(RolMembresia.ADMINISTRADOR));
	}

	private DisenoPanelUsuario guardada() {
		ArgumentCaptor<DisenoPanelUsuario> captor = ArgumentCaptor.forClass(DisenoPanelUsuario.class);
		verify(filas).saveAndFlush(captor.capture());
		return captor.getValue();
	}

	private void porDefecto(String valor) {
		when(configuracion.findByClave(DisenoPanelService.CLAVE_POR_DEFECTO))
				.thenReturn(Optional.of(new ConfiguracionOrganizacion(DisenoPanelService.CLAVE_POR_DEFECTO, valor)));
	}

	private DisenoPanelUsuario fila(boolean permitido, boolean activo) {
		DisenoPanelUsuario fila = new DisenoPanelUsuario(USUARIO);
		fila.setPermitido(permitido);
		fila.setActivo(activo);
		when(filas.findByUsuarioId(USUARIO)).thenReturn(Optional.of(fila));
		return fila;
	}
}
