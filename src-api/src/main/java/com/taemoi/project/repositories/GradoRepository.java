package com.taemoi.project.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.TipoGrado;

/**
 * Grados del catálogo (filtrados por {@code @TenantId}). Cada código de grado existe una vez por
 * sistema de grados (taekwondo y kickboxing en Taemoi): las búsquedas van siempre por sistema
 * ({@code ProgresionGradosService}), nunca solo por código.
 */
@Repository
public interface GradoRepository extends JpaRepository<Grado, Long> {

	/** Solo para las semillas de {@code InicializadorDatos}. */
	boolean existsByTipoGrado(TipoGrado tipoGrado);

	List<Grado> findBySistemaGradosIdOrderByOrdenAscIdAsc(Long sistemaGradosId);

	List<Grado> findBySistemaGradosIdOrderByIdAsc(Long sistemaGradosId);

	Optional<Grado> findBySistemaGradosIdAndCodigo(Long sistemaGradosId, String codigo);

}
