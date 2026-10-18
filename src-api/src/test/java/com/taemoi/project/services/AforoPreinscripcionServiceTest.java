package com.taemoi.project.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import com.taemoi.project.entities.*;
import com.taemoi.project.repositories.*;

/**
 * R2-P3: el aforo sale de dos consultas agregadas (alumnos que cuentan y pendientes, por turno) y del límite. Qué alumnos
 * cuentan (ficha activa, deporte del grupo activo) lo decide la consulta: lo fija AforoPreinscripcionesIntegrationTest
 * contra MySQL.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness=Strictness.LENIENT)
class AforoPreinscripcionServiceTest {
 private static final String TEMPORADA="2026/2027";
 @Mock TurnoRepository turnos; @Mock AlumnoDeporteRepository deportes; @Mock PreinscripcionRepository preinscripciones; @Mock ConfiguracionSistemaService configuracion;
 AforoPreinscripcionService service; Grupo grupo; Turno lunes; Turno miercoles;
 @BeforeEach void setUp(){service=new AforoPreinscripcionService(turnos,deportes,preinscripciones,configuracion);grupo=new Grupo();grupo.setId(7L);grupo.setDisciplina(com.taemoi.project.catalogo.CatalogoTaemoiEnMemoria.disciplinaTaemoi("TAEKWONDO"));lunes=new Turno();lunes.setId(11L);lunes.setGrupo(grupo);miercoles=new Turno();miercoles.setId(12L);miercoles.setGrupo(grupo);when(turnos.findByGrupo(grupo)).thenReturn(List.of(lunes,miercoles));when(configuracion.obtenerLimiteTurno()).thenReturn(3);ocupar(Map.of(),Map.of());}
 @Test void pendienteConsumePlazaSoloEnTurnoSeleccionado(){ocupar(Map.of(11L,1L),Map.of(11L,1L));assertEquals(1,service.plazasDisponibles(lunes,TEMPORADA));assertEquals(3,service.plazasDisponibles(miercoles,TEMPORADA));}
 @Test void turnoSinGrupoNoTienePlazasYNoSeConsulta(){Turno suelto=new Turno();suelto.setId(13L);assertEquals(0,service.plazasDisponibles(suelto,TEMPORADA));assertTrue(service.completo(suelto,TEMPORADA));verify(turnos,never()).contarAlumnosQueCuentanPorTurno(anyList());verify(preinscripciones,never()).contarPorTurnoTemporadaYEstado(anyCollection(),anyString(),any());}
 @Test void concedeCombinacionCuandoAlMenosUnTurnoTienePlaza(){ocupar(Map.of(),Map.of(11L,2L,12L,3L));assertTrue(service.algunaPlaza(List.of(lunes,miercoles),TEMPORADA));}
 @Test void esperaCuandoTodosLosTurnosEstanCompletos(){ocupar(Map.of(),Map.of(11L,3L,12L,4L));assertFalse(service.algunaPlaza(List.of(lunes,miercoles),TEMPORADA));assertTrue(service.completo(grupo,TEMPORADA));assertEquals(0,service.plazasDisponibles(grupo,TEMPORADA));}
 @Test void ocupacionEfectivaIncluyeAlumnosActivosYPendientes(){ocupar(Map.of(11L,1L),Map.of(11L,2L));assertEquals(3,service.ocupacionEfectiva(lunes,grupo,TEMPORADA));}
 @Test void grupoSinTurnosEstaCompletoYSinPlazas(){Grupo vacio=new Grupo();vacio.setId(8L);when(turnos.findByGrupo(vacio)).thenReturn(List.of());assertTrue(service.completo(vacio,TEMPORADA));assertEquals(0,service.plazasDisponibles(vacio,TEMPORADA));}
 @Test void ocupacionDeVariosTurnosEnDosConsultasYUnaLecturaDelLimite(){ocupar(Map.of(11L,2L),Map.of(12L,1L));AforoPreinscripcionService.Ocupacion o=service.ocupacion(List.of(lunes,miercoles,lunes),TEMPORADA);assertEquals(1,o.plazas(lunes));assertEquals(2,o.plazas(miercoles));o.sumarPendiente(List.of(lunes,miercoles));assertTrue(o.completo(lunes));assertEquals(1,o.plazas(miercoles));verify(turnos,times(1)).contarAlumnosQueCuentanPorTurno(List.of(11L,12L));verify(preinscripciones,times(1)).contarPorTurnoTemporadaYEstado(List.of(11L,12L),TEMPORADA,EstadoPreinscripcion.PENDIENTE);verify(configuracion,times(1)).obtenerLimiteTurno();}
 @Test void bloquearConAdicionalesFallaSoloSiFaltaUnExigido(){when(turnos.findAllByIdForUpdate(List.of(11L,12L,99L))).thenReturn(List.of(miercoles,lunes));Map<Long,Turno> bloqueados=service.bloquearTurnos(Set.of(11L),Set.of(12L,99L));assertEquals(List.of(11L,12L),List.copyOf(bloqueados.keySet()));when(turnos.findAllByIdForUpdate(List.of(12L,99L))).thenReturn(List.of(miercoles));IllegalArgumentException e=assertThrows(IllegalArgumentException.class,()->service.bloquearTurnos(Set.of(99L),Set.of(12L)));assertEquals("Alguno de los turnos seleccionados no existe.",e.getMessage());}
 @Test void posicionCuentaSolicitudesAnterioresConTurnosSolapados(){Preinscripcion p=new Preinscripcion();p.setTurnos(new java.util.LinkedHashSet<>(List.of(lunes,miercoles)));p.setTemporada(TEMPORADA);Instant fecha=Instant.parse("2026-07-02T10:00:00Z");setCreadaEn(p,fecha);when(preinscripciones.countAnterioresSolapadas(TEMPORADA,EstadoPreinscripcion.EN_LISTA_ESPERA,p.getTurnos(),fecha,null)).thenReturn(3L);assertEquals(4,service.posicion(p));}
 /** Simula las dos consultas agregadas: solo devuelven filas de los turnos pedidos con algún recuento. */
 private void ocupar(Map<Long,Long> activos,Map<Long,Long> pendientes){
  when(turnos.contarAlumnosQueCuentanPorTurno(anyList())).thenAnswer(inv->filas(inv.getArgument(0),activos));
  when(preinscripciones.contarPorTurnoTemporadaYEstado(anyCollection(),eq(TEMPORADA),eq(EstadoPreinscripcion.PENDIENTE))).thenAnswer(inv->filas(inv.getArgument(0),pendientes));
 }
 private static List<Object[]> filas(Collection<Long> ids,Map<Long,Long> valores){return ids.stream().filter(valores::containsKey).map(id->new Object[]{id,valores.get(id)}).toList();}
 private void setCreadaEn(Preinscripcion p,Instant fecha){try{var f=Preinscripcion.class.getDeclaredField("creadaEn");f.setAccessible(true);f.set(p,fecha);}catch(Exception e){throw new AssertionError(e);}}
}
