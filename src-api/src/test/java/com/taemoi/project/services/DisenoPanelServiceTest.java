package com.taemoi.project.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.taemoi.project.dtos.response.DisenoPanelDTO;
import com.taemoi.project.entities.ConfiguracionOrganizacion;
import com.taemoi.project.entities.DisenoPanelUsuario;
import com.taemoi.project.repositories.ConfiguracionOrganizacionRepository;
import com.taemoi.project.repositories.DisenoPanelUsuarioRepository;
import com.taemoi.project.repositories.MembresiaRepository;
import com.taemoi.project.repositories.UsuarioRepository;

/**
 * Regla efectiva del interruptor del diseño nuevo (pieza 8b-I, fase3-e8b-plan.md §3), sin Spring: con fila y
 * {@code permitido}, su {@code activo}; si no, el valor por defecto de la escuela ({@code panel.diseno_por_defecto};
 * sin la clave, VIEJO). {@code permitido} de la sesión: puede cambiarlo para sí (fila permitida o por defecto NUEVO).
 */
class DisenoPanelServiceTest {

	private static final long USUARIO = 41L;

	DisenoPanelUsuarioRepository filas;
	ConfiguracionOrganizacionRepository configuracion;
	DisenoPanelService servicio;

	@BeforeEach
	void preparar() {
		filas = mock(DisenoPanelUsuarioRepository.class);
		configuracion = mock(ConfiguracionOrganizacionRepository.class);
		servicio = new DisenoPanelService(filas, configuracion, mock(MembresiaRepository.class),
				mock(MembresiaService.class), mock(UsuarioRepository.class), mock(RegistroAccionesPanel.class));
		when(filas.findByUsuarioId(USUARIO)).thenReturn(Optional.empty());
		when(configuracion.findByClave(DisenoPanelService.CLAVE_POR_DEFECTO)).thenReturn(Optional.empty());
	}

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
	void filaPermitida_mandaSuActivo() {
		fila(true, true);
		assertTrue(servicio.activoPara(USUARIO));
		assertEquals(new DisenoPanelDTO.Propio(true, true), servicio.estadoPropio(USUARIO));
		fila(true, false);
		assertFalse(servicio.activoPara(USUARIO));
		assertEquals(new DisenoPanelDTO.Propio(false, true), servicio.estadoPropio(USUARIO));
	}

	@Test
	void filaPermitidaApagada_mandaSobreElPorDefectoNuevo() {
		porDefecto("NUEVO");
		fila(true, false);
		assertFalse(servicio.activoPara(USUARIO));
	}

	@Test
	void filaSinPermiso_cuentaElPorDefecto() {
		fila(false, true);
		assertFalse(servicio.activoPara(USUARIO), "sin permiso, su activo no cuenta (VIEJO)");
		assertEquals(new DisenoPanelDTO.Propio(false, false), servicio.estadoPropio(USUARIO));
		porDefecto("NUEVO");
		fila(false, false);
		assertTrue(servicio.activoPara(USUARIO), "sin permiso, el por defecto NUEVO");
	}

	@Test
	void sinUsuario_apagado() {
		porDefecto("NUEVO");
		assertFalse(servicio.activoPara(null));
		assertEquals(new DisenoPanelDTO.Propio(false, false), servicio.estadoPropio(null));
	}

	private void porDefecto(String valor) {
		when(configuracion.findByClave(DisenoPanelService.CLAVE_POR_DEFECTO))
				.thenReturn(Optional.of(new ConfiguracionOrganizacion(DisenoPanelService.CLAVE_POR_DEFECTO, valor)));
	}

	private void fila(boolean permitido, boolean activo) {
		DisenoPanelUsuario fila = new DisenoPanelUsuario(USUARIO);
		fila.setPermitido(permitido);
		fila.setActivo(activo);
		when(filas.findByUsuarioId(USUARIO)).thenReturn(Optional.of(fila));
	}
}
