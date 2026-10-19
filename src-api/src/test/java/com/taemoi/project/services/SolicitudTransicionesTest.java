package com.taemoi.project.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.taemoi.project.entities.EstadoSolicitud;
import com.taemoi.project.entities.Solicitud;
import com.taemoi.project.entities.TipoSolicitud;

/**
 * Pieza 6c-2: la tabla de transiciones de §6.2 (fase3-6c-solicitudes.md) en un solo sitio. Se recorre entera: cada
 * tipo × estado de origen × estado de destino.
 */
class SolicitudTransicionesTest {

	private static final List<TipoSolicitud> TIPOS_1_A_3 = List.of(TipoSolicitud.REVERTIR_COBRO,
			TipoSolicitud.DESHACER_COBRO_ENTREGADO, TipoSolicitud.ANULAR_ENTREGA, TipoSolicitud.ANULAR_MOVIMIENTO);

	@Test
	void tipos1a3_desdePendiente_aRetiradaDenegadaAceptadaONoAplicable() {
		Set<EstadoSolicitud> destinos = EnumSet.of(EstadoSolicitud.RETIRADA, EstadoSolicitud.DENEGADA,
				EstadoSolicitud.ACEPTADA, EstadoSolicitud.NO_APLICABLE);
		for (TipoSolicitud tipo : TIPOS_1_A_3) {
			for (EstadoSolicitud desde : EstadoSolicitud.values()) {
				for (EstadoSolicitud destino : EstadoSolicitud.values()) {
					boolean esperado = desde == EstadoSolicitud.PENDIENTE && destinos.contains(destino);
					assertEquals(esperado, SolicitudTransiciones.permitida(tipo, desde, destino),
							tipo + ": " + desde + " -> " + destino);
				}
			}
		}
	}

	@Test
	void encargo_tablaDeLa6_2() {
		Map<EstadoSolicitud, Set<EstadoSolicitud>> tabla = Map.of(
				EstadoSolicitud.PEDIDO,
				EnumSet.of(EstadoSolicitud.RETIRADA, EstadoSolicitud.ENCARGADO, EstadoSolicitud.RECHAZADO),
				EstadoSolicitud.ENCARGADO, EnumSet.of(EstadoSolicitud.RECIBIDO, EstadoSolicitud.RECHAZADO),
				EstadoSolicitud.RECIBIDO, EnumSet.of(EstadoSolicitud.ENTREGADO, EstadoSolicitud.RECHAZADO));
		for (EstadoSolicitud desde : EstadoSolicitud.values()) {
			for (EstadoSolicitud destino : EstadoSolicitud.values()) {
				boolean esperado = tabla.getOrDefault(desde, Set.of()).contains(destino);
				assertEquals(esperado, SolicitudTransiciones.permitida(TipoSolicitud.ENCARGO, desde, destino),
						"ENCARGO: " + desde + " -> " + destino);
			}
		}
	}

	@Test
	void exigir_fueraDeLaTabla_409NoPendiente_sinCambiarElEstado() {
		Solicitud pendiente = solicitud(TipoSolicitud.REVERTIR_COBRO, EstadoSolicitud.PENDIENTE);
		assertDoesNotThrow(() -> SolicitudTransiciones.exigir(pendiente, EstadoSolicitud.RETIRADA));
		assertEquals(EstadoSolicitud.PENDIENTE, pendiente.getEstado(), "exigir no cambia el estado");

		Solicitud retirada = solicitud(TipoSolicitud.ANULAR_ENTREGA, EstadoSolicitud.RETIRADA);
		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> SolicitudTransiciones.exigir(retirada, EstadoSolicitud.DENEGADA));
		assertEquals(SolicitudTransiciones.NO_PENDIENTE, error.getMessage());
		assertEquals("La solicitud ya no está pendiente", SolicitudTransiciones.NO_PENDIENTE);
		assertEquals(EstadoSolicitud.RETIRADA, retirada.getEstado());

		Solicitud encargo = solicitud(TipoSolicitud.ENCARGO, EstadoSolicitud.PEDIDO);
		assertThrows(IllegalStateException.class,
				() -> SolicitudTransiciones.exigir(encargo, EstadoSolicitud.DENEGADA),
				"un encargo no se deniega: se rechaza");
		assertThrows(IllegalStateException.class, () -> SolicitudTransiciones.exigir(pendiente, null));
		assertThrows(IllegalStateException.class,
				() -> SolicitudTransiciones.exigir(solicitud(null, EstadoSolicitud.PENDIENTE), EstadoSolicitud.RETIRADA));
	}

	@Test
	void estadoInicial_pendienteOPedido() {
		for (TipoSolicitud tipo : TIPOS_1_A_3) {
			assertEquals(EstadoSolicitud.PENDIENTE, SolicitudTransiciones.estadoInicial(tipo));
		}
		assertEquals(EstadoSolicitud.PEDIDO, SolicitudTransiciones.estadoInicial(TipoSolicitud.ENCARGO));
		assertEquals(5, TipoSolicitud.values().length, "un tipo nuevo necesita su fila en la tabla");
	}

	private static Solicitud solicitud(TipoSolicitud tipo, EstadoSolicitud estado) {
		Solicitud s = new Solicitud();
		s.setTipo(tipo);
		s.setEstado(estado);
		return s;
	}
}
