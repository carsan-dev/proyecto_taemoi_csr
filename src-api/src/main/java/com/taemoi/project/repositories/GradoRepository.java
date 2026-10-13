package com.taemoi.project.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.TipoGrado;

/**
 * Repositorio para la entidad Grado.
 *
 * <p>Desde V23 cada {@link TipoGrado} existe una vez por sistema de grados (taekwondo y
 * kickboxing en Taemoi). Las búsquedas heredadas por {@code tipoGrado} se limitan a los grados
 * heredados (los del sistema de la disciplina TAEKWONDO, o sin sistema) para seguir devolviendo
 * un único grado, como antes de V23. Desaparecen al pasar los llamadores al catálogo.
 */
@Repository
public interface GradoRepository extends JpaRepository<Grado, Long> {

	String SOLO_GRADOS_HEREDADOS = "(g.sistemaGrados IS NULL OR g.sistemaGrados.id IN "
			+ "(SELECT d.sistemaGrados.id FROM Disciplina d WHERE d.codigo = 'TAEKWONDO'))";

	/**
	 * Recupera el grado heredado (sistema de taekwondo) de un tipo de grado.
	 *
	 * @param tipoGrado El tipo de grado a buscar.
	 * @return El grado correspondiente al tipo de grado especificado.
	 */
	@Query("SELECT g FROM Grado g WHERE g.tipoGrado = :tipoGrado AND " + SOLO_GRADOS_HEREDADOS)
	Grado findByTipoGrado(@Param("tipoGrado") TipoGrado tipoGrado);

	/**
	 * Recupera el grado heredado (sistema de taekwondo) cuyo tipo está en la lista.
	 *
	 * @param tiposGrado La lista de tipos de grado a buscar.
	 * @return El grado heredado cuyo tipo está en la lista.
	 */
	@Query("SELECT g FROM Grado g WHERE g.tipoGrado IN :tiposGrado AND " + SOLO_GRADOS_HEREDADOS)
	Grado findByTipoGradoIn(@Param("tiposGrado") List<TipoGrado> tiposGrado);

	boolean existsByTipoGrado(TipoGrado tipoGrado);

	/** Grados heredados (lo que devolvía {@code findAll()} antes de V23), por id. */
	@Query("SELECT g FROM Grado g WHERE " + SOLO_GRADOS_HEREDADOS + " ORDER BY g.id")
	List<Grado> findGradosHeredados();

	/** Grado heredado (sistema de taekwondo) con ese código; mismo criterio que {@link #findByTipoGrado}. */
	@Query("SELECT g FROM Grado g WHERE g.codigo = :codigo AND " + SOLO_GRADOS_HEREDADOS)
	Optional<Grado> findHeredadoPorCodigo(@Param("codigo") String codigo);

	// --- Catálogo (servicios nuevos) ---

	List<Grado> findBySistemaGradosIdOrderByOrdenAscIdAsc(Long sistemaGradosId);

	Optional<Grado> findBySistemaGradosIdAndCodigo(Long sistemaGradosId, String codigo);
}
