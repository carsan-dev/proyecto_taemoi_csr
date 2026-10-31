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
import com.taemoi.project.dtos.response.TurnoPublicoDTO;
import com.taemoi.project.legado.Deporte;
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
	/** Entrega 6-1: sin sedes (lista vacía) el reparto por día mira todos los grupos, como siempre. */
	@Mock com.taemoi.project.repositories.SedeRepository sedes;
	@Mock com.taemoi.project.acceso.GestionPorSede gestionPorSede;
	@InjectMocks TurnoServiceImpl service;

	@Test
	void calculaEstadoDeAforoIndependienteParaTurnosDelMismoGrupo() {
		Grupo grupo = grupo(7L);
		Turno lunes = turno(11L, "Lunes", grupo);
		Turno miercoles = turno(12L, "Miércoles", grupo);
		when(turnoRepository.findAllWithAlumnos()).thenReturn(List.of(lunes, miercoles));
		when(temporadas.actual()).thenReturn("2026/2027");
		// R2-P3: el aforo de todos los turnos sale de una sola ocupación por lotes (límite 36: lunes lleno, miércoles no).
		when(aforo.ocupacion(List.of(lunes, miercoles), "2026/2027")).thenReturn(
				new AforoPreinscripcionService.Ocupacion(36, java.util.Map.of(11L, 30, 12L, 20), java.util.Map.of(11L, 6)));

		List<TurnoPublicoDTO> resultado = service.listarTurnosDTO();

		assertTrue(resultado.get(0).getCompleto());
		assertFalse(resultado.get(1).getCompleto());
		assertEquals(36, resultado.get(0).getOcupacionEfectiva());
		assertEquals(20, resultado.get(1).getOcupacionEfectiva());
	}

	/**
	 * La edad pública de /horarios se calcula con la fecha de Madrid, no con la de la JVM: a las 00:30 de Madrid
	 * (22:30 UTC del día anterior) quien nace ese día ya ha cumplido años.
	 */
	@Test
	void edadesPublicasConLaFechaDeMadrid() {
		Grupo grupo = grupo(7L);
		Turno turno = turno(11L, "Lunes", grupo);
		com.taemoi.project.entities.Alumno alumno = new com.taemoi.project.entities.Alumno();
		alumno.setActivo(true);
		alumno.setFechaNacimiento(java.sql.Date.valueOf("2016-10-17"));
		com.taemoi.project.entities.AlumnoDeporte deporte = new com.taemoi.project.entities.AlumnoDeporte();
		deporte.setDisciplina(com.taemoi.project.catalogo.CatalogoTaemoiEnMemoria.disciplinaTaemoi(Deporte.TAEKWONDO.name()));
		deporte.setActivo(true);
		alumno.setDeportes(new java.util.ArrayList<>(List.of(deporte)));
		turno.setAlumnos(new java.util.ArrayList<>(List.of(alumno)));
		when(turnoRepository.findAllWithAlumnos()).thenReturn(List.of(turno));
		when(temporadas.actual()).thenReturn("2026/2027");
		when(aforo.ocupacion(List.of(turno), "2026/2027")).thenReturn(
				new AforoPreinscripcionService.Ocupacion(36, java.util.Map.of(), java.util.Map.of()));
		service.setReloj(java.time.Clock.fixed(java.time.Instant.parse("2026-10-16T22:30:00Z"),
				java.time.ZoneOffset.UTC));

		TurnoPublicoDTO dto = service.listarTurnosDTO().get(0);

		assertEquals(10, dto.getEdadMinAlumnos());
		assertEquals(10, dto.getEdadMaxAlumnos());
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

	/** Decisión 374: cambiar el día no mueve el turno de grupo, así que no mira preinscripciones ni grupos. */
	@Test
	void cambiarDeLunesAMartes_conservaElGrupo_ySinComprobarPreinscripciones() {
		Grupo g1 = grupo(1L, 10L), g2 = grupo(2L, 10L);
		Turno turno = turno(11L, "Lunes", g1);
		when(turnoRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(turno));

		service.actualizarTurno(11L, cambiosA("martes"));

		assertEquals(g1, turno.getGrupo());
		assertEquals("martes", turno.getDiaSemana());
		verify(turnoRepository).save(turno);
		verify(preinscripciones, never()).countActivasByTurnoId(org.mockito.ArgumentMatchers.anyLong(), anyCollection());
		verify(grupoRepository, never()).findAll();
		verify(sedes, never()).findAllByOrderByIdAsc();
		verify(gestionPorSede, never()).exigirGrupoPropio(org.mockito.ArgumentMatchers.anyLong());
	}

	/** Con varias sedes tampoco salta de sede: el grupo (y su sede) no cambian. */
	@Test
	void cambiarElDia_conVariasSedes_noSaltaDeSede() {
		Grupo a1 = grupo(1L, 10L), b1 = grupo(2L, 20L);
		Turno turno = turno(11L, "Lunes", b1);
		when(turnoRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(turno));

		service.actualizarTurno(11L, cambiosA("miércoles"));

		assertEquals(b1, turno.getGrupo());
		assertEquals(20L, turno.getGrupo().getSedeId());
	}

	@Test
	void aceptaViernesSabadoYDomingo_conYSinAcento() {
		for (String dia : List.of("viernes", "Sábado", "sabado", "DOMINGO", "Miércoles", "miercoles")) {
			Grupo g = grupo(1L, 10L);
			Turno turno = turno(11L, "Lunes", g);
			when(turnoRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(turno));

			service.actualizarTurno(11L, cambiosA(dia));

			assertEquals(dia, turno.getDiaSemana());
			assertEquals(g, turno.getGrupo());
		}
	}

	@Test
	void rechazaUnDiaQueNoEsDiaDeLaSemana() {
		Grupo g = grupo(1L, 10L);
		Turno turno = turno(11L, "Lunes", g);
		when(turnoRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(turno));

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> service.actualizarTurno(11L, cambiosA("foo")));

		assertEquals("Día de la semana no válido: foo", error.getMessage());
		assertThrows(IllegalArgumentException.class, () -> service.actualizarTurno(11L, cambiosA(null)));
		verify(turnoRepository, never()).save(turno);
		assertEquals("Lunes", turno.getDiaSemana());
	}

	private static TurnoDTO cambiosA(String dia) {
		TurnoDTO cambios = new TurnoDTO();
		cambios.setDiaSemana(dia);
		cambios.setHoraInicio("18:00");
		cambios.setHoraFin("19:00");
		return cambios;
	}

	private Grupo grupo(Long id, Long sede) {
		Grupo grupo = grupo(id);
		grupo.setSedeId(sede);
		return grupo;
	}

	private Grupo grupo(Long id) {
		Grupo grupo = new Grupo();
		grupo.setId(id);
		grupo.setNombre("Grupo " + id);
		grupo.setDisciplina(com.taemoi.project.catalogo.CatalogoTaemoiEnMemoria.disciplinaTaemoi(Deporte.TAEKWONDO.name()));
		grupo.setTurnos(new java.util.ArrayList<>());
		return grupo;
	}

	private Turno turno(Long id, String dia, Grupo grupo) {
		Turno turno = new Turno(dia, "18:00", "19:00", grupo);
		turno.setId(id);
		return turno;
	}
}
