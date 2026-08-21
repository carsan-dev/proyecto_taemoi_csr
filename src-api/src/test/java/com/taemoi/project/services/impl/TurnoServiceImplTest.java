package com.taemoi.project.services.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyCollection;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.taemoi.project.dtos.TurnoDTO;
import com.taemoi.project.dtos.response.TurnoCortoDTO;
import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.Grupo;
import com.taemoi.project.entities.Turno;
import com.taemoi.project.repositories.GrupoRepository;
import com.taemoi.project.repositories.PreinscripcionRepository;
import com.taemoi.project.repositories.TurnoRepository;
import com.taemoi.project.services.AforoPreinscripcionService;
import com.taemoi.project.services.TemporadaService;

@ExtendWith(MockitoExtension.class)
class TurnoServiceImplTest {
	@Mock TurnoRepository turnoRepository;
	@Mock GrupoRepository grupoRepository;
	@Mock AforoPreinscripcionService aforo;
	@Mock TemporadaService temporadas;
	@Mock PreinscripcionRepository preinscripciones;
	@InjectMocks TurnoServiceImpl service;

	@Test
	void calculaEstadoDeAforoIndependienteParaTurnosDelMismoGrupo() {
		Grupo grupo = grupo(7L);
		Turno lunes = turno(11L, "Lunes", grupo);
		Turno miercoles = turno(12L, "Miércoles", grupo);
		when(turnoRepository.findAllWithAlumnos()).thenReturn(List.of(lunes, miercoles));
		when(temporadas.actual()).thenReturn("2026/2027");
		when(aforo.ocupacionEfectiva(lunes, grupo, "2026/2027")).thenReturn(36);
		when(aforo.ocupacionEfectiva(miercoles, grupo, "2026/2027")).thenReturn(20);
		when(aforo.completo(lunes, "2026/2027")).thenReturn(true);
		when(aforo.completo(miercoles, "2026/2027")).thenReturn(false);

		List<TurnoCortoDTO> resultado = service.listarTurnosDTO();

		assertTrue(resultado.get(0).getCompleto());
		assertFalse(resultado.get(1).getCompleto());
		assertEquals(36, resultado.get(0).getOcupacionEfectiva());
		assertEquals(20, resultado.get(1).getOcupacionEfectiva());
	}

	@Test
	void bloqueaEliminarTurnoConPreinscripcionesActivas() {
		Turno turno = turno(11L, "Lunes", grupo(7L));
		when(turnoRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(turno));
		when(preinscripciones.countActivasByTurnoId(org.mockito.ArgumentMatchers.eq(11L), anyCollection()))
				.thenReturn(1L);

		IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.eliminarTurno(11L));

		assertTrue(error.getMessage().contains("preinscripciones activas"));
		verify(turnoRepository, never()).deleteById(11L);
	}

	@Test
	void permiteEliminarTurnoSinPreinscripcionesActivas() {
		Turno turno = turno(11L, "Lunes", grupo(7L));
		when(turnoRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(turno));

		assertTrue(service.eliminarTurno(11L));
		verify(turnoRepository).deleteById(11L);
	}

	@Test
	void bloqueaReasignarTurnoConPreinscripcionesActivas() {
		Grupo origen = grupo(1L), destino = grupo(2L);
		Turno turno = turno(11L, "Lunes", origen);
		TurnoDTO cambios = new TurnoDTO();
		cambios.setDiaSemana("martes");
		cambios.setHoraInicio("18:00");
		cambios.setHoraFin("19:00");
		when(turnoRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(turno));
		when(grupoRepository.findAll()).thenReturn(List.of(origen, destino));
		when(preinscripciones.countActivasByTurnoId(org.mockito.ArgumentMatchers.eq(11L), anyCollection()))
				.thenReturn(1L);

		assertThrows(IllegalStateException.class, () -> service.actualizarTurno(11L, cambios));
		verify(turnoRepository, never()).save(turno);
		assertEquals(origen, turno.getGrupo());
	}

	private Grupo grupo(Long id) {
		Grupo grupo = new Grupo();
		grupo.setId(id);
		grupo.setNombre("Grupo " + id);
		grupo.setDeporte(Deporte.TAEKWONDO);
		grupo.setTurnos(new java.util.ArrayList<>());
		return grupo;
	}

	private Turno turno(Long id, String dia, Grupo grupo) {
		Turno turno = new Turno(dia, "18:00", "19:00", grupo);
		turno.setId(id);
		return turno;
	}
}
