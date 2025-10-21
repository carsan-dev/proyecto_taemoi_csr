package com.taemoi.project.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entities.Producto;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {

	Page<Producto> findByConceptoContaining(String concepto, Pageable pageable);

	Optional<Producto> findByConcepto(String nombreProducto);
}
