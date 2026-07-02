package com.taemoi.project.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import com.taemoi.project.entities.*;
import com.taemoi.project.repositories.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness=Strictness.LENIENT)
class AforoPreinscripcionServiceTest {
 @Mock GrupoRepository grupos; @Mock TurnoRepository turnos; @Mock AlumnoDeporteRepository deportes; @Mock PreinscripcionRepository preinscripciones; @Mock ConfiguracionSistemaService configuracion;
 AforoPreinscripcionService service; Grupo grupo; Turno lunes; Turno miercoles;
 @BeforeEach void setUp(){service=new AforoPreinscripcionService(grupos,turnos,deportes,preinscripciones,configuracion);grupo=new Grupo();grupo.setId(7L);grupo.setDeporte(Deporte.TAEKWONDO);lunes=new Turno();miercoles=new Turno();when(turnos.findByGrupo(grupo)).thenReturn(List.of(lunes,miercoles));when(configuracion.obtenerLimiteTurno()).thenReturn(3);}
 @Test void pendienteConsumePlazaEnTodosLosTurnos(){Alumno a=alumno(1L,true);lunes.setAlumnos(List.of(a));miercoles.setAlumnos(List.of());when(deportes.existsByAlumnoIdAndDeporteAndActivoTrue(1L,Deporte.TAEKWONDO)).thenReturn(true);when(preinscripciones.countByGrupoAndTemporadaAndEstado(grupo,"2026/2027",EstadoPreinscripcion.PENDIENTE)).thenReturn(1L);assertEquals(1,service.plazasDisponibles(grupo,"2026/2027"));}
 @Test void alumnoInactivoNoConsumePlaza(){lunes.setAlumnos(List.of(alumno(1L,false)));miercoles.setAlumnos(List.of());when(preinscripciones.countByGrupoAndTemporadaAndEstado(grupo,"2026/2027",EstadoPreinscripcion.PENDIENTE)).thenReturn(0L);assertEquals(3,service.plazasDisponibles(grupo,"2026/2027"));}
 @Test void ocupacionEfectivaIncluyeAlumnosActivosYPendientes(){Alumno a=alumno(1L,true);lunes.setAlumnos(List.of(a));when(deportes.existsByAlumnoIdAndDeporteAndActivoTrue(1L,Deporte.TAEKWONDO)).thenReturn(true);when(preinscripciones.countByGrupoAndTemporadaAndEstado(grupo,"2026/2027",EstadoPreinscripcion.PENDIENTE)).thenReturn(2L);assertEquals(3,service.ocupacionEfectiva(lunes,grupo,"2026/2027"));}
 @Test void posicionUsaFechaYIdComoDesempate(){Preinscripcion p=new Preinscripcion();p.setGrupo(grupo);p.setTemporada("2026/2027");Instant fecha=Instant.parse("2026-07-02T10:00:00Z");setCreadaEn(p,fecha);when(preinscripciones.countByGrupoAndTemporadaAndEstadoAndCreadaEnLessThan(grupo,"2026/2027",EstadoPreinscripcion.EN_LISTA_ESPERA,fecha)).thenReturn(2L);when(preinscripciones.countByGrupoAndTemporadaAndEstadoAndCreadaEnAndIdLessThan(grupo,"2026/2027",EstadoPreinscripcion.EN_LISTA_ESPERA,fecha,null)).thenReturn(1L);assertEquals(4,service.posicion(p));}
 private Alumno alumno(Long id,boolean activo){Alumno a=mock(Alumno.class);when(a.getId()).thenReturn(id);when(a.getActivo()).thenReturn(activo);return a;}
 private void setCreadaEn(Preinscripcion p,Instant fecha){try{var f=Preinscripcion.class.getDeclaredField("creadaEn");f.setAccessible(true);f.set(p,fecha);}catch(Exception e){throw new AssertionError(e);}}
}
