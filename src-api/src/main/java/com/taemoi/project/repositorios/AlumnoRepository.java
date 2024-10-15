package com.taemoi.project.repositorios;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.taemoi.project.dtos.AlumnoDTO;
import com.taemoi.project.entidades.Alumno;

/**
 * Repositorio para la entidad Alumno. Proporciona métodos para realizar operaciones de persistencia
 * y consulta relacionadas con los alumnos en la base de datos.
 */
@Repository
public interface AlumnoRepository extends JpaRepository<Alumno, Long>, JpaSpecificationExecutor<Alumno> {

    /**
     * Guarda un objeto AlumnoDTO en la base de datos.
     *
     * @param alumno El objeto AlumnoDTO a guardar.
     * @return El objeto AlumnoDTO guardado.
     */
    AlumnoDTO save(AlumnoDTO alumno);

    /**
     * Busca un alumno por su número de identificación fiscal (NIF).
     *
     * @param nif El NIF del alumno a buscar.
     * @return Un Optional que contiene el alumno encontrado, o vacío si no se encuentra ningún alumno con el NIF especificado.
     */
    Optional<Alumno> findByNif(String nif);
    
    /**
     * Busca una página de alumnos cuyo nombre contenga la cadena especificada, ignorando mayúsculas y minúsculas.
     *
     * @param nombre   La cadena a buscar en el nombre de los alumnos.
     * @param pageable Objeto Pageable para la paginación de resultados.
     * @return Una página de objetos Alumno que cumplen con el criterio de búsqueda.
     */
	Page<Alumno> findByNombreContainingIgnoreCase(String nombre, Pageable pageable);

    /**
     * Busca una lista de alumnos cuyo nombre contenga la cadena especificada, ignorando mayúsculas y minúsculas.
     *
     * @param nombre La cadena a buscar en el nombre de los alumnos.
     * @return Una lista de objetos Alumno que cumplen con el criterio de búsqueda.
     */
	List<Alumno> findByNombreContainingIgnoreCase(String nombre);
	
    /**
     * Busca una página de alumnos por el ID de la categoría especificada.
     *
     * @param categoriaId El ID de la categoría de los alumnos a buscar.
     * @param pageable    Objeto Pageable para la paginación de resultados.
     * @return Una página de objetos Alumno que pertenecen a la categoría especificada.
     */
	Page<Alumno> findByCategoriaId(Long categoriaId, Pageable pageable);

    /**
     * Busca una lista de alumnos por el ID de la categoría especificada.
     *
     * @param categoriaId El ID de la categoría de los alumnos a buscar.
     * @return Una lista de objetos Alumno que pertenecen a la categoría especificada.
     */
	List<Alumno> findByCategoriaId(Long categoriaId);

    /**
     * Busca una página de alumnos por el ID del grado especificado.
     *
     * @param gradoId  El ID del grado de los alumnos a buscar.
     * @param pageable Objeto Pageable para la paginación de resultados.
     * @return Una página de objetos Alumno que pertenecen al grado especificado.
     */
	Page<Alumno> findByGradoId(Long gradoId, Pageable pageable);

    /**
     * Busca una lista de alumnos por el ID del grado especificado.
     *
     * @param gradoId El ID del grado de los alumnos a buscar.
     * @return Una lista de objetos Alumno que pertenecen al grado especificado.
     */
	List<Alumno> findByGradoId(Long gradoId);

    /**
     * Busca una página de alumnos cuyo nombre contenga la cadena especificada y que pertenezcan al grado con el ID especificado.
     *
     * @param nombre    La cadena a buscar en el nombre de los alumnos.
     * @param gradoId   El ID del grado de los alumnos a buscar.
     * @param pageable  Objeto Pageable para la paginación de resultados.
     * @return Una página de objetos Alumno que cumplen con los criterios de búsqueda.
     */
	Page<Alumno> findByNombreContainingIgnoreCaseAndGradoId(String nombre, Long gradoId, Pageable pageable);

    /**
     * Busca una lista de alumnos cuyo nombre contenga la cadena especificada y que pertenezcan al grado con el ID especificado.
     *
     * @param nombre   La cadena a buscar en el nombre de los alumnos.
     * @param gradoId  El ID del grado de los alumnos a buscar.
     * @return Una lista de objetos Alumno que cumplen con los criterios de búsqueda.
     */
	List<Alumno> findByNombreContainingIgnoreCaseAndGradoId(String nombre, Long gradoId);

    /**
     * Busca una página de alumnos cuyo nombre contenga la cadena especificada y que pertenezcan a la categoría con el ID especificado.
     *
     * @param nombre       La cadena a buscar en el nombre de los alumnos.
     * @param categoriaId  El ID de la categoría de los alumnos a buscar.
     * @param pageable     Objeto Pageable para la paginación de resultados.
     * @return Una página de objetos Alumno que cumplen con los criterios de búsqueda.
     */
	Page<Alumno> findByNombreContainingIgnoreCaseAndCategoriaId(String nombre, Long categoriaId, Pageable pageable);
	
    /**
     * Busca una lista de alumnos cuyo nombre contenga la cadena especificada y que pertenezcan a la categoría con el ID especificado.
     *
     * @param nombre       La cadena a buscar en el nombre de los alumnos.
     * @param categoriaId  El ID de la categoría de los alumnos a buscar.
     * @return Una lista de objetos Alumno que cumplen con los criterios de búsqueda.
     */
	List<Alumno> findByNombreContainingIgnoreCaseAndCategoriaId(String nombre, Long categoriaId);

    /**
     * Busca una página de alumnos que pertenecen tanto al grado con el ID especificado como a la categoría con el ID especificado.
     *
     * @param gradoId      El ID del grado de los alumnos a buscar.
     * @param categoriaId  El ID de la categoría de los alumnos a buscar.
     * @param pageable     Objeto Pageable para la paginación de resultados.
     * @return Una página de objetos Alumno que cumplen con los criterios de búsqueda.
     */
	Page<Alumno> findByGradoIdAndCategoriaId(Long gradoId, Long categoriaId, Pageable pageable);
	
    /**
     * Busca una lista de alumnos que pertenecen tanto al grado con el ID especificado como a la categoría con el ID especificado.
     *
     * @param gradoId      El ID del grado de los alumnos a buscar.
     * @param categoriaId  El ID de la categoría de los alumnos a buscar.
     * @return Una lista de objetos Alumno que cumplen con los criterios de búsqueda.
     */
	List<Alumno> findByGradoIdAndCategoriaId(Long gradoId, Long categoriaId);
	
    /**
     * Busca una página de alumnos cuyo nombre contenga la cadena especificada y que pertenezcan tanto al grado con el ID especificado como a la categoría con el ID especificado.
     *
     * @param nombre       La cadena a buscar en el nombre de los alumnos.
     * @param gradoId      El ID del grado de los alumnos a buscar.
     * @param categoriaId  El ID de la categoría de los alumnos a buscar.
     * @param pageable     Objeto Pageable para la paginación de resultados.
     * @return Una página de objetos Alumno que cumplen con los criterios de búsqueda.
     */
	Page<Alumno> findByNombreContainingIgnoreCaseAndGradoIdAndCategoriaId(String nombre, Long gradoId, Long categoriaId,
			Pageable pageable);

    /**
     * Busca una lista de alumnos cuyo nombre contenga la cadena especificada y que pertenezcan tanto al grado con el ID especificado como a la categoría con el ID especificado.
     *
     * @param nombre       La cadena a buscar en el nombre de los alumnos.
     * @param gradoId      El ID del grado de los alumnos a buscar.
     * @param categoriaId  El ID de la categoría de los alumnos a buscar.
     * @return Una lista de objetos Alumno que cumplen con los criterios de búsqueda.
     */
	List<Alumno> findByNombreContainingIgnoreCaseAndGradoIdAndCategoriaId(String nombre, Long gradoId,
			Long categoriaId);
	
    boolean existsByEmail(String email);
    
    @Query("SELECT a FROM Alumno a WHERE a.grupos IS EMPTY")
    List<Alumno> findAlumnosSinGrupo();
    
    @Query("SELECT MAX(a.numeroExpediente) FROM Alumno a")
    Integer findMaxNumeroExpediente();
    
    @Query("SELECT COUNT(a) FROM Alumno a JOIN a.grupos g WHERE g.nombre = :nombreGrupo")
    Long contarAlumnosPorGrupo(@Param("nombreGrupo") String nombreGrupo);
}