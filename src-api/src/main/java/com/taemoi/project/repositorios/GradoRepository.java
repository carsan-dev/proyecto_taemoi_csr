package com.taemoi.project.repositorios;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entidades.Grado;
import com.taemoi.project.entidades.TipoGrado;

/**
 * Repositorio para la entidad Grado. Proporciona métodos para buscar por tipo de grado.
 */
@Repository
public interface GradoRepository extends JpaRepository<Grado, Long> {
	
    /**
     * Recupera un grado de la base de datos por su tipo de grado.
     * 
     * @param tipoGrado El tipo de grado a buscar.
     * @return El grado correspondiente al tipo de grado especificado.
     */
	Grado findByTipoGrado(TipoGrado tipoGrado);

    /**
     * Recupera grados de la base de datos cuyos tipos de grado están en la lista proporcionada.
     * 
     * @param tiposGrado La lista de tipos de grado a buscar.
     * @return Una lista de grados cuyos tipos de grado están en la lista proporcionada.
     */
	Grado findByTipoGradoIn(List<TipoGrado> tiposGrado);

	boolean existsByTipoGrado(TipoGrado tipoGrado);
}
