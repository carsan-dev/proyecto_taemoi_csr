package com.taemoi.project.repositories;
import java.util.*; import org.springframework.data.jpa.repository.*; import com.taemoi.project.entities.*;
public interface PreinscripcionRepository extends JpaRepository<Preinscripcion,Long>,JpaSpecificationExecutor<Preinscripcion>{
 Optional<Preinscripcion> findByReferencia(String referencia);
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @Query("select p from Preinscripcion p where p.referencia = :referencia")
 Optional<Preinscripcion> findByReferenciaForUpdate(@org.springframework.data.repository.query.Param("referencia") String referencia);
 boolean existsByIdentidadHashAndDisciplinaCodigoAndTemporadaAndEstadoIn(String identidadHash,String deporte,String temporada,Collection<EstadoPreinscripcion> estados);
 boolean existsByIdempotencyKeyHash(String idempotencyKeyHash);
 long countDistinctByTurnosContainingAndTemporadaAndEstado(Turno turno,String temporada,EstadoPreinscripcion estado);
 @Query("select count(distinct p.id) from Preinscripcion p left join p.turnos t left join p.turno turnoLegacy where p.estado in :estados and (t.id=:turnoId or turnoLegacy.id=:turnoId)")
 long countActivasByTurnoId(@org.springframework.data.repository.query.Param("turnoId") Long turnoId,@org.springframework.data.repository.query.Param("estados") Collection<EstadoPreinscripcion> estados);
 List<Preinscripcion> findByTemporadaAndEstadoOrderByCreadaEnAscIdAsc(String temporada,EstadoPreinscripcion estado);
 /** R2-P3 (aforo por lotes): por turno, solicitudes distintas de la temporada en ese estado ({@code preinscripcion_turno}). Filas {@code [turnoId, número]}. */
 @Query("select t.id, count(distinct p.id) from Preinscripcion p join p.turnos t where t.id in :turnoIds and p.temporada=:temporada and p.estado=:estado group by t.id")
 List<Object[]> contarPorTurnoTemporadaYEstado(@org.springframework.data.repository.query.Param("turnoIds") Collection<Long> turnoIds,@org.springframework.data.repository.query.Param("temporada") String temporada,@org.springframework.data.repository.query.Param("estado") EstadoPreinscripcion estado);
 /**
  * R2-P3: turnos que piden las solicitudes de la temporada en ese estado, con la misma prioridad que
  * {@code PreinscripcionService.turnosDe}: los de {@code preinscripcion_turno}; si no tiene, su {@code turno_id}; si
  * tampoco, los turnos de su {@code grupo_id}.
  */
 @Query("select t.id from Turno t where exists (select 1 from Preinscripcion p join p.turnos pt where pt.id=t.id and p.temporada=:temporada and p.estado=:estado)"
  +" or exists (select 1 from Preinscripcion p where p.turno.id=t.id and p.turnos is empty and p.temporada=:temporada and p.estado=:estado)"
  +" or exists (select 1 from Preinscripcion p where p.grupo.id=t.grupo.id and p.turno is null and p.turnos is empty and p.temporada=:temporada and p.estado=:estado)")
 List<Long> findTurnoIdsSolicitados(@org.springframework.data.repository.query.Param("temporada") String temporada,@org.springframework.data.repository.query.Param("estado") EstadoPreinscripcion estado);
 /** R2-P3: estado confirmado de la solicitud (lectura escalar: no la devuelve el contexto de persistencia). */
 @Query("select p.estado from Preinscripcion p where p.id=:id")
 EstadoPreinscripcion findEstadoById(@org.springframework.data.repository.query.Param("id") Long id);
 @Query("select count(distinct previa.id) from Preinscripcion previa join previa.turnos t where previa.temporada=:temporada and previa.estado=:estado and t in :turnos and (previa.creadaEn<:creadaEn or (previa.creadaEn=:creadaEn and previa.id<:id))")
 long countAnterioresSolapadas(@org.springframework.data.repository.query.Param("temporada") String temporada,@org.springframework.data.repository.query.Param("estado") EstadoPreinscripcion estado,@org.springframework.data.repository.query.Param("turnos") Collection<Turno> turnos,@org.springframework.data.repository.query.Param("creadaEn") java.time.Instant creadaEn,@org.springframework.data.repository.query.Param("id") Long id);
 /**
  * Solo para el borrado completo del alumno: la preinscripción se conserva como histórico (mismo estado) y deja
  * de apuntar al alumno. Consulta JPQL, así que Hibernate la restringe a la escuela activa.
  */
 @Modifying(flushAutomatically = true)
 @Query("update Preinscripcion p set p.alumno = null where p.alumno.id = :alumnoId")
 int desvincularAlumno(@org.springframework.data.repository.query.Param("alumnoId") Long alumnoId);
}
