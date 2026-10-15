package com.taemoi.project.repositories;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository; import com.taemoi.project.entities.*;
public interface PlantillaPreinscripcionRepository extends JpaRepository<PlantillaPreinscripcion,Long>{
 /* Desde V28 el deporte es el código de la disciplina (disciplina.codigo). */
 Optional<PlantillaPreinscripcion> findFirstByDisciplinaCodigoAndActivaTrueOrderByVersionDesc(String deporte);
 List<PlantillaPreinscripcion> findByDisciplinaCodigoOrderByVersionDesc(String deporte);
 Optional<PlantillaPreinscripcion> findByDisciplinaCodigoAndVersion(String deporte,Integer version);
}
