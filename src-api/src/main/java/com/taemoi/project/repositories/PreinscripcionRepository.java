package com.taemoi.project.repositories;
import java.util.*; import org.springframework.data.jpa.repository.*; import com.taemoi.project.entities.*;
public interface PreinscripcionRepository extends JpaRepository<Preinscripcion,Long>,JpaSpecificationExecutor<Preinscripcion>{
 Optional<Preinscripcion> findByReferencia(String referencia);
 boolean existsByDniAndDeporteAndTemporadaAndEstadoNot(String dni,Deporte deporte,String temporada,EstadoPreinscripcion estado);
}
