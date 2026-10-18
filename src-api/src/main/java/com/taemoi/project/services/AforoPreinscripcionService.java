package com.taemoi.project.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.taemoi.project.entities.*;
import com.taemoi.project.repositories.*;

/**
 * Aforo de los turnos para las preinscripciones: plazas = límite de turno de la organización − (alumnos que cuentan +
 * preinscripciones PENDIENTES de la temporada en {@code preinscripcion_turno}); un turno sin grupo no tiene plazas.
 * Cuentan los alumnos del turno con la ficha activa y el deporte del grupo activo.
 *
 * <p>R2-P3: la ocupación se calcula por lotes ({@link #ocupacion}): alumnos que cuentan por turno en una consulta
 * agregada, pendientes por turno en otra y el límite una vez, para todos los turnos de la operación. Antes, cada turno
 * cargaba sus alumnos y lanzaba un exists por alumno, un count y una lectura del límite.
 */
@Service
public class AforoPreinscripcionService {
 private final TurnoRepository turnos; private final AlumnoDeporteRepository alumnoDeportes; private final PreinscripcionRepository preinscripciones; private final ConfiguracionSistemaService configuracion;
 @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
 public AforoPreinscripcionService(TurnoRepository t,AlumnoDeporteRepository ad,PreinscripcionRepository p,ConfiguracionSistemaService c){turnos=t;alumnoDeportes=ad;preinscripciones=p;configuracion=c;}

 @Transactional public List<Turno> bloquearTurnos(Collection<Long> turnoIds){
  List<Long> ids=turnoIds.stream().distinct().sorted().toList();
  List<Turno> bloqueados=turnos.findAllByIdForUpdate(ids);
  if(bloqueados.size()!=ids.size())throw new IllegalArgumentException("Alguno de los turnos seleccionados no existe.");
  return bloqueados.stream().sorted(Comparator.comparing(Turno::getId)).toList();
 }
 /**
  * R2-P3: bloquea en UNA sentencia, en orden de id, los turnos {@code exigidos} y los {@code adicionales} (los de la
  * lista de espera que la operación puede promocionar), para que dos operaciones que bloquean turnos solapados (otra
  * cancelación, un alta pública) los pidan en el mismo orden. Como {@link #bloquearTurnos(Collection)}, falla si falta
  * alguno de los exigidos; los adicionales que no se encuentren (inexistentes o sin grupo) se ignoran aquí: la
  * promoción falla con ellos cuando le llega su turno a la solicitud que los pide, como antes.
  * @return los turnos bloqueados por id (en orden de id).
  */
 @Transactional public Map<Long,Turno> bloquearTurnos(Collection<Long> exigidos,Collection<Long> adicionales){
  TreeSet<Long> ids=new TreeSet<>(exigidos);ids.addAll(adicionales);
  Map<Long,Turno> bloqueados=new LinkedHashMap<>();
  if(!ids.isEmpty())turnos.findAllByIdForUpdate(new ArrayList<>(ids)).stream().sorted(Comparator.comparing(Turno::getId)).forEach(t->bloqueados.put(t.getId(),t));
  if(!bloqueados.keySet().containsAll(exigidos))throw new IllegalArgumentException("Alguno de los turnos seleccionados no existe.");
  return bloqueados;
 }
 /** Turnos que piden las solicitudes en lista de espera de la temporada (también las antiguas por {@code turno_id}/{@code grupo_id}): lo que una promoción va a bloquear. */
 public Set<Long> turnosEnEspera(String temporada){return new TreeSet<>(preinscripciones.findTurnoIdsSolicitados(temporada,EstadoPreinscripcion.EN_LISTA_ESPERA));}
 /**
  * R2-P3: bloquea (FOR UPDATE) la solicitud y la relee de la base de datos, con sus turnos. Se llama con sus turnos ya
  * bloqueados (mismo orden que la promoción: turnos y luego solicitudes): otra operación pudo cambiarla mientras se
  * esperaba. La instancia anterior se separa del contexto (un refresh no relee la colección de turnos) y se devuelve una
  * nueva; la consulta JPQL bloquea solo la fila de {@code preinscripcion} (sin JOIN).
  */
 @Transactional public Preinscripcion releerBloqueada(Preinscripcion p){String referencia=p.getReferencia();em.detach(p);return preinscripciones.findByReferenciaForUpdate(referencia).orElseThrow(()->new java.util.NoSuchElementException("Preinscripción no encontrada."));}
 /** Estado confirmado de la solicitud, sin pasar por el contexto de persistencia. */
 public EstadoPreinscripcion estadoConfirmado(Long id){return preinscripciones.findEstadoById(id);}
 /** Ocupación de los turnos dados en la temporada, en tres consultas en total (alumnos que cuentan, pendientes y límite). */
 public Ocupacion ocupacion(Collection<Turno> horarios,String temporada){
  List<Long> ids=horarios.stream().filter(t->t.getGrupo()!=null).map(Turno::getId).filter(Objects::nonNull).distinct().sorted().toList();
  Map<Long,Integer> activos=new HashMap<>(),pendientes=new HashMap<>();
  if(!ids.isEmpty()){
   for(Object[] fila:turnos.contarAlumnosQueCuentanPorTurno(ids))activos.put((Long)fila[0],((Number)fila[1]).intValue());
   for(Object[] fila:preinscripciones.contarPorTurnoTemporadaYEstado(ids,temporada,EstadoPreinscripcion.PENDIENTE))pendientes.put((Long)fila[0],((Number)fila[1]).intValue());
  }
  return new Ocupacion(configuracion.obtenerLimiteTurno(),activos,pendientes);
 }
 public int plazasDisponibles(Turno turno,String temporada){return ocupacion(List.of(turno),temporada).plazas(turno);}
 public int plazasDisponibles(Grupo grupo,String temporada){
  List<Turno> horarios=turnos.findByGrupo(grupo);if(horarios.isEmpty())return 0;
  Ocupacion o=ocupacion(horarios,temporada);return horarios.stream().mapToInt(o::plazas).max().orElse(0);
 }
 public boolean completo(Turno turno,String temporada){return plazasDisponibles(turno,temporada)<=0;}
 public boolean completo(Grupo grupo,String temporada){List<Turno> horarios=turnos.findByGrupo(grupo);return horarios.isEmpty()||ocupacion(horarios,temporada).completos(horarios);}
 public boolean algunaPlaza(Collection<Turno> seleccion,String temporada){return !seleccion.isEmpty()&&ocupacion(seleccion,temporada).algunaPlaza(seleccion);}
 /** @param grupo el grupo del turno (el deporte de los alumnos que cuentan es el suyo). */
 public int ocupacionEfectiva(Turno turno,Grupo grupo,String temporada){return ocupacion(List.of(turno),temporada).ocupacionEfectiva(turno);}
 public List<Preinscripcion> cola(String temporada){return preinscripciones.findByTemporadaAndEstadoOrderByCreadaEnAscIdAsc(temporada,EstadoPreinscripcion.EN_LISTA_ESPERA);}
 public long posicion(Preinscripcion p){return 1+preinscripciones.countAnterioresSolapadas(p.getTemporada(),EstadoPreinscripcion.EN_LISTA_ESPERA,p.getTurnos(),p.getCreadaEn(),p.getId());}

 /**
  * Ocupación calculada una vez para un conjunto de turnos. La promoción la actualiza en memoria
  * ({@link #sumarPendiente}) igual que la contaría una nueva consulta tras guardar la solicitud.
  */
 public static final class Ocupacion {
  private final int limite; private final Map<Long,Integer> activos; private final Map<Long,Integer> pendientes;
  /** @param activos alumnos que cuentan por id de turno; @param pendientes solicitudes PENDIENTES por id de turno (se copia: la promoción lo actualiza). */
  public Ocupacion(int limite,Map<Long,Integer> activos,Map<Long,Integer> pendientes){this.limite=limite;this.activos=Map.copyOf(activos);this.pendientes=new HashMap<>(pendientes);}
  public int limite(){return limite;}
  /** Alumnos que cuentan + pendientes de la temporada. */
  public int ocupacionEfectiva(Turno t){return activos.getOrDefault(t.getId(),0)+pendientes.getOrDefault(t.getId(),0);}
  public int plazas(Turno t){if(t.getGrupo()==null)return 0;return Math.max(0,limite-ocupacionEfectiva(t));}
  public boolean completo(Turno t){return plazas(t)<=0;}
  /** Un grupo está completo si no tiene turnos o todos están completos. */
  public boolean completos(Collection<Turno> horariosDelGrupo){return horariosDelGrupo.isEmpty()||horariosDelGrupo.stream().allMatch(this::completo);}
  public boolean algunaPlaza(Collection<Turno> seleccion){return seleccion.stream().anyMatch(t->!completo(t));}
  /** Una solicitud pasa a PENDIENTE: cuenta una vez más en cada turno suyo de {@code preinscripcion_turno}. */
  public void sumarPendiente(Collection<Turno> turnosDeLaSolicitud){turnosDeLaSolicitud.stream().map(Turno::getId).distinct().forEach(id->pendientes.merge(id,1,Integer::sum));}
 }
}
