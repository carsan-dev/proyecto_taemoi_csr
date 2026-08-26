package com.taemoi.project.services;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.taemoi.project.entities.*;
import com.taemoi.project.repositories.*;

@Service
public class AforoPreinscripcionService {
 private final TurnoRepository turnos; private final AlumnoDeporteRepository alumnoDeportes; private final PreinscripcionRepository preinscripciones; private final ConfiguracionSistemaService configuracion;
 public AforoPreinscripcionService(TurnoRepository t,AlumnoDeporteRepository ad,PreinscripcionRepository p,ConfiguracionSistemaService c){turnos=t;alumnoDeportes=ad;preinscripciones=p;configuracion=c;}

 @Transactional public List<Turno> bloquearTurnos(Collection<Long> turnoIds){
  List<Long> ids=turnoIds.stream().distinct().sorted().toList();
  List<Turno> bloqueados=turnos.findAllByIdForUpdate(ids);
  if(bloqueados.size()!=ids.size())throw new IllegalArgumentException("Alguno de los turnos seleccionados no existe.");
  return bloqueados.stream().sorted(Comparator.comparing(Turno::getId)).toList();
 }
 public int plazasDisponibles(Turno turno,String temporada){
  Grupo grupo=turno.getGrupo();if(grupo==null)return 0;
  int ocupacion=alumnosActivos(turno,grupo.getDeporte())+(int)preinscripciones.countDistinctByTurnosContainingAndTemporadaAndEstado(turno,temporada,EstadoPreinscripcion.PENDIENTE);
  return Math.max(0,configuracion.obtenerLimiteTurno()-ocupacion);
 }
 public int plazasDisponibles(Grupo grupo,String temporada){
  List<Turno> horarios=turnos.findByGrupo(grupo);if(horarios.isEmpty())return 0;
  return horarios.stream().mapToInt(t->plazasDisponibles(t,temporada)).max().orElse(0);
 }
 public boolean completo(Turno turno,String temporada){return plazasDisponibles(turno,temporada)<=0;}
 public boolean completo(Grupo grupo,String temporada){List<Turno> horarios=turnos.findByGrupo(grupo);return horarios.isEmpty()||horarios.stream().allMatch(t->completo(t,temporada));}
 public boolean algunaPlaza(Collection<Turno> seleccion,String temporada){return seleccion.stream().anyMatch(t->!completo(t,temporada));}
 public int ocupacionEfectiva(Turno turno,Grupo grupo,String temporada){
  long pendientes=preinscripciones.countDistinctByTurnosContainingAndTemporadaAndEstado(turno,temporada,EstadoPreinscripcion.PENDIENTE);
  return alumnosActivos(turno,grupo.getDeporte())+(int)pendientes;
 }
 public List<Preinscripcion> cola(String temporada){return preinscripciones.findByTemporadaAndEstadoOrderByCreadaEnAscIdAsc(temporada,EstadoPreinscripcion.EN_LISTA_ESPERA);}
 public long posicion(Preinscripcion p){return 1+preinscripciones.countAnterioresSolapadas(p.getTemporada(),EstadoPreinscripcion.EN_LISTA_ESPERA,p.getTurnos(),p.getCreadaEn(),p.getId());}
 private int alumnosActivos(Turno turno,Deporte deporte){return (int)turno.getAlumnos().stream().filter(a->Boolean.TRUE.equals(a.getActivo())).filter(a->alumnoDeportes.existsByAlumnoIdAndDeporteAndActivoTrue(a.getId(),deporte)).map(Alumno::getId).distinct().count();}
}
