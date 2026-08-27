package com.taemoi.project.repositories;
import java.util.*; import org.springframework.data.jpa.repository.*; import com.taemoi.project.entities.*;
public interface PreinscripcionRepository extends JpaRepository<Preinscripcion,Long>,JpaSpecificationExecutor<Preinscripcion>{
 Optional<Preinscripcion> findByReferencia(String referencia);
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @Query("select p from Preinscripcion p where p.referencia = :referencia")
 Optional<Preinscripcion> findByReferenciaForUpdate(@org.springframework.data.repository.query.Param("referencia") String referencia);
 boolean existsByIdentidadHashAndDeporteAndTemporadaAndEstadoIn(String identidadHash,Deporte deporte,String temporada,Collection<EstadoPreinscripcion> estados);
 boolean existsByIdempotencyKeyHash(String idempotencyKeyHash);
 long countDistinctByTurnosContainingAndTemporadaAndEstado(Turno turno,String temporada,EstadoPreinscripcion estado);
 @Query("select count(distinct p.id) from Preinscripcion p left join p.turnos t left join p.turno turnoLegacy where p.estado in :estados and (t.id=:turnoId or turnoLegacy.id=:turnoId)")
 long countActivasByTurnoId(@org.springframework.data.repository.query.Param("turnoId") Long turnoId,@org.springframework.data.repository.query.Param("estados") Collection<EstadoPreinscripcion> estados);
 List<Preinscripcion> findByTemporadaAndEstadoOrderByCreadaEnAscIdAsc(String temporada,EstadoPreinscripcion estado);
 @Query("select count(distinct previa.id) from Preinscripcion previa join previa.turnos t where previa.temporada=:temporada and previa.estado=:estado and t in :turnos and (previa.creadaEn<:creadaEn or (previa.creadaEn=:creadaEn and previa.id<:id))")
 long countAnterioresSolapadas(@org.springframework.data.repository.query.Param("temporada") String temporada,@org.springframework.data.repository.query.Param("estado") EstadoPreinscripcion estado,@org.springframework.data.repository.query.Param("turnos") Collection<Turno> turnos,@org.springframework.data.repository.query.Param("creadaEn") java.time.Instant creadaEn,@org.springframework.data.repository.query.Param("id") Long id);
}
