package com.taemoi.project.repositories;
import java.util.*; import org.springframework.data.jpa.repository.*; import com.taemoi.project.entities.*;
public interface PreinscripcionRepository extends JpaRepository<Preinscripcion,Long>,JpaSpecificationExecutor<Preinscripcion>{
 Optional<Preinscripcion> findByReferencia(String referencia);
 boolean existsByDniAndDeporteAndTemporadaAndEstadoNot(String dni,Deporte deporte,String temporada,EstadoPreinscripcion estado);
 long countByGrupoAndTemporadaAndEstado(Grupo grupo,String temporada,EstadoPreinscripcion estado);
 List<Preinscripcion> findByGrupoAndTemporadaAndEstadoOrderByCreadaEnAscIdAsc(Grupo grupo,String temporada,EstadoPreinscripcion estado);
 long countByGrupoAndTemporadaAndEstadoAndCreadaEnLessThan(Grupo grupo,String temporada,EstadoPreinscripcion estado,java.time.Instant creadaEn);
 long countByGrupoAndTemporadaAndEstadoAndCreadaEnAndIdLessThan(Grupo grupo,String temporada,EstadoPreinscripcion estado,java.time.Instant creadaEn,Long id);
}
