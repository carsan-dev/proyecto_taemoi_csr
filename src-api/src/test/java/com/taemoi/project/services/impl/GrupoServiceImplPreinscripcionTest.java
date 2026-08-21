package com.taemoi.project.services.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyCollection;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.Grupo;
import com.taemoi.project.entities.Turno;
import com.taemoi.project.repositories.AlumnoDeporteRepository;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.GrupoRepository;
import com.taemoi.project.repositories.PreinscripcionRepository;
import com.taemoi.project.repositories.TurnoRepository;

@ExtendWith(MockitoExtension.class)
class GrupoServiceImplPreinscripcionTest {
	@Mock GrupoRepository grupoRepository;
	@Mock TurnoRepository turnoRepository;
	@Mock AlumnoRepository alumnoRepository;
	@Mock AlumnoDeporteRepository alumnoDeporteRepository;
	@Mock PreinscripcionRepository preinscripciones;
	@InjectMocks GrupoServiceImpl service;

	@Test
	void bloqueaDesvincularTurnoConPreinscripcionesActivas() {
		Grupo grupo = grupo(7L);
		Turno turno = turno(11L, grupo);
		grupo.getTurnos().add(turno);
		when(grupoRepository.findById(7L)).thenReturn(Optional.of(grupo));
		when(turnoRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(turno));
		when(preinscripciones.countActivasByTurnoId(org.mockito.ArgumentMatchers.eq(11L), anyCollection()))
				.thenReturn(1L);

		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> service.eliminarTurnoDeGrupo(7L, 11L));

		assertTrue(error.getMessage().contains("preinscripciones activas"));
		assertEquals(grupo, turno.getGrupo());
		assertEquals(List.of(turno), grupo.getTurnos());
		verify(grupoRepository, never()).save(grupo);
	}

	@Test
	void permiteDesvincularTurnoSinPreinscripcionesActivas() {
		Grupo grupo = grupo(7L);
		Turno turno = turno(11L, grupo);
		grupo.getTurnos().add(turno);
		when(grupoRepository.findById(7L)).thenReturn(Optional.of(grupo));
		when(turnoRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(turno));

		service.eliminarTurnoDeGrupo(7L, 11L);

		assertNull(turno.getGrupo());
		assertTrue(grupo.getTurnos().isEmpty());
		verify(grupoRepository).save(grupo);
	}

	@Test
	void bloqueaEliminarGrupoConTurnoUsadoPorPreinscripcionActiva() {
		Grupo grupo = grupo(7L);
		Turno turno = turno(11L, grupo);
		grupo.getTurnos().add(turno);
		when(grupoRepository.findById(7L)).thenReturn(Optional.of(grupo));
		when(turnoRepository.findAllByIdForUpdate(List.of(11L))).thenReturn(List.of(turno));
		when(preinscripciones.countActivasByTurnoId(org.mockito.ArgumentMatchers.eq(11L), anyCollection()))
				.thenReturn(1L);

		assertThrows(IllegalStateException.class, () -> service.eliminarGrupo(7L));

		verify(turnoRepository, never()).delete(turno);
		verify(grupoRepository, never()).delete(grupo);
	}

	@Test
	void bloqueaReasignarTurnoActivoDesdeOtroGrupo() {
		Grupo origen = grupo(1L), destino = grupo(2L);
		Turno turno = turno(11L, origen);
		when(grupoRepository.findById(2L)).thenReturn(Optional.of(destino));
		when(turnoRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(turno));
		when(preinscripciones.countActivasByTurnoId(org.mockito.ArgumentMatchers.eq(11L), anyCollection()))
				.thenReturn(1L);

		assertThrows(IllegalStateException.class, () -> service.agregarTurnoAGrupo(2L, 11L));

		assertEquals(origen, turno.getGrupo());
		assertTrue(destino.getTurnos().isEmpty());
	}

	private Grupo grupo(Long id) {
		Grupo grupo = new Grupo();
		grupo.setId(id);
		grupo.setNombre("Grupo " + id);
		grupo.setDeporte(Deporte.TAEKWONDO);
		grupo.setTurnos(new ArrayList<>());
		return grupo;
	}

	private Turno turno(Long id, Grupo grupo) {
		Turno turno = new Turno("Lunes", "18:00", "19:00", grupo);
		turno.setId(id);
		return turno;
	}
}
