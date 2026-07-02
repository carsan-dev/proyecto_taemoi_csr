package com.taemoi.project.services;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.taemoi.project.entities.*;
import com.taemoi.project.repositories.*;

@Service
public class AforoPreinscripcionService {
 private final GrupoRepository grupos; private final TurnoRepository turnos; private final AlumnoDeporteRepository alumnoDeportes; private final PreinscripcionRepository preinscripciones; private final ConfiguracionSistemaService configuracion;
 public AforoPreinscripcionService(GrupoRepository g,TurnoRepository t,AlumnoDeporteRepository ad,PreinscripcionRepository p,ConfiguracionSistemaService c){grupos=g;turnos=t;alumnoDeportes=ad;preinscripciones=p;configuracion=c;}

 @Transactional public Grupo bloquearGrupo(Long grupoId){return grupos.findByIdForUpdate(grupoId).orElseThrow(()->new IllegalArgumentException("Grupo no encontrado."));}
 public int plazasDisponibles(Grupo grupo,String temporada){
  List<Turno> horarios=turnos.findByGrupo(grupo);if(horarios.isEmpty())return 0;
  int limite=configuracion.obtenerLimiteTurno();long pendientes=preinscripciones.countByGrupoAndTemporadaAndEstado(grupo,temporada,EstadoPreinscripcion.PENDIENTE);
  int minimo=horarios.stream().mapToInt(t->limite-alumnosActivos(t,grupo.getDeporte())-(int)pendientes).min().orElse(0);return Math.max(0,minimo);
 }
 public boolean completo(Grupo grupo,String temporada){return plazasDisponibles(grupo,temporada)<=0;}
 public int ocupacionEfectiva(Turno turno,Grupo grupo,String temporada){
  long pendientes=preinscripciones.countByGrupoAndTemporadaAndEstado(grupo,temporada,EstadoPreinscripcion.PENDIENTE);
  return alumnosActivos(turno,grupo.getDeporte())+(int)pendientes;
 }
 public List<Preinscripcion> cola(Grupo grupo,String temporada){return preinscripciones.findByGrupoAndTemporadaAndEstadoOrderByCreadaEnAscIdAsc(grupo,temporada,EstadoPreinscripcion.EN_LISTA_ESPERA);}
 public long posicion(Preinscripcion p){return 1+preinscripciones.countByGrupoAndTemporadaAndEstadoAndCreadaEnLessThan(p.getGrupo(),p.getTemporada(),EstadoPreinscripcion.EN_LISTA_ESPERA,p.getCreadaEn())+preinscripciones.countByGrupoAndTemporadaAndEstadoAndCreadaEnAndIdLessThan(p.getGrupo(),p.getTemporada(),EstadoPreinscripcion.EN_LISTA_ESPERA,p.getCreadaEn(),p.getId());}
 private int alumnosActivos(Turno turno,Deporte deporte){return (int)turno.getAlumnos().stream().filter(a->Boolean.TRUE.equals(a.getActivo())).filter(a->alumnoDeportes.existsByAlumnoIdAndDeporteAndActivoTrue(a.getId(),deporte)).map(Alumno::getId).distinct().count();}
}
