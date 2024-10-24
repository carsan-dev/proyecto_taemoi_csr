package com.taemoi.project.repositorios;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entidades.Pago;

/**
 * Repositorio para la entidad Pago.
 */
@Repository
public interface PagoRepository extends JpaRepository<Pago, Long> {
	List<Pago> findByAlumnoId(Long alumnoId);
}
