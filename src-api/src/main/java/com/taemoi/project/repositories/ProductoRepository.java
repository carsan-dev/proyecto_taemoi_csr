package com.taemoi.project.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entities.Producto;
import com.taemoi.project.entities.TipoProducto;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {

	Page<Producto> findByConceptoContaining(String concepto, Pageable pageable);

	Optional<Producto> findFirstByConcepto(String nombreProducto);

	// --- Catálogo (servicios nuevos, V24) ---

	Optional<Producto> findByCodigo(String codigo);

	/** Producto que concede grado con ese concepto exacto (el clasificado de menor id). */
	Optional<Producto> findFirstByConceptoAndGradoIsNotNullOrderByIdAsc(String concepto);

	/** Productos de ese tipo que conceden grado (los clasificados). */
	List<Producto> findByTipoAndGradoIsNotNullOrderByIdAsc(TipoProducto tipo);

	List<Producto> findByTipoAndDisciplinaIdOrderByIdAsc(TipoProducto tipo, Long disciplinaId);

	List<Producto> findByTipoAndDisciplinaIsNullOrderByIdAsc(TipoProducto tipo);
}
