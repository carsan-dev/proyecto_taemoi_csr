package com.taemoi.project.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.taemoi.project.web.PlazasWeb.Estado;

/**
 * Reglas de plazas de la web por plantilla (fase4-web-plan.md §4.3, decisión d482): «Quedan N» con al menos una libre
 * (sin «Últimas N»), «Lleno» sin libres y nadie en espera, «Lista de espera» sin libres y alguien en espera. Del grupo,
 * el peor de sus turnos.
 */
class PlazasWebTest {

	@Test
	void turno_ceroLibres_sinEspera_lleno() {
		PlazasWeb p = PlazasWeb.deTurno(0, 0);
		assertEquals(Estado.LLENO, p.estado());
		assertNull(p.quedan());
	}

	@Test
	void turno_ceroLibres_conEspera_listaDeEspera() {
		PlazasWeb p = PlazasWeb.deTurno(0, 1);
		assertEquals(Estado.LISTA_ESPERA, p.estado());
		assertNull(p.quedan());
	}

	@Test
	void turno_unaLibre_quedanUna() {
		assertEquals(new PlazasWeb(Estado.QUEDAN, 1), PlazasWeb.deTurno(1, 0));
	}

	/** Sin «Últimas N»: con 2 (el antiguo umbral n <= 2 del boceto) sigue siendo «Quedan 2». */
	@Test
	void turno_dosLibres_quedanDos() {
		assertEquals(new PlazasWeb(Estado.QUEDAN, 2), PlazasWeb.deTurno(2, 0));
	}

	@Test
	void turno_tresLibres_quedanTres() {
		assertEquals(new PlazasWeb(Estado.QUEDAN, 3), PlazasWeb.deTurno(3, 0));
	}

	/** Con plazas libres la lista de espera no cuenta (puede quedar alguien de una temporada con otro aforo). */
	@Test
	void turno_conLibresYEspera_quedan() {
		assertEquals(new PlazasWeb(Estado.QUEDAN, 2), PlazasWeb.deTurno(2, 3));
	}

	@Test
	void turno_negativos_noValen() {
		assertThrows(IllegalArgumentException.class, () -> PlazasWeb.deTurno(-1, 0));
		assertThrows(IllegalArgumentException.class, () -> PlazasWeb.deTurno(0, -1));
	}

	@Test
	void grupo_elPeorDeSusTurnos_menosLibres() {
		assertEquals(new PlazasWeb(Estado.QUEDAN, 1),
				PlazasWeb.peor(List.of(PlazasWeb.deTurno(3, 0), PlazasWeb.deTurno(1, 0), PlazasWeb.deTurno(2, 0))));
	}

	@Test
	void grupo_unTurnoLleno_lleno() {
		assertEquals(Estado.LLENO,
				PlazasWeb.peor(List.of(PlazasWeb.deTurno(3, 0), PlazasWeb.deTurno(0, 0))).estado());
	}

	@Test
	void grupo_llenoYListaDeEspera_listaDeEspera() {
		assertEquals(Estado.LISTA_ESPERA, PlazasWeb.peor(
				List.of(PlazasWeb.deTurno(0, 2), PlazasWeb.deTurno(0, 0), PlazasWeb.deTurno(5, 0))).estado());
	}

	@Test
	void grupo_sinTurnos_nulo() {
		assertNull(PlazasWeb.peor(List.of()));
	}
}
