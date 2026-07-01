package com.taemoi.project.repositories;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository; import com.taemoi.project.entities.*;
public interface PlantillaPreinscripcionRepository extends JpaRepository<PlantillaPreinscripcion,Long>{
 Optional<PlantillaPreinscripcion> findFirstByDeporteAndActivaTrueOrderByVersionDesc(Deporte deporte);
 List<PlantillaPreinscripcion> findByDeporteOrderByVersionDesc(Deporte deporte);
}
