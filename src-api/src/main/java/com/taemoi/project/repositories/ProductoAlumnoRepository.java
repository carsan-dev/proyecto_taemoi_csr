package com.taemoi.project.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.taemoi.project.entities.ProductoAlumno;

public interface ProductoAlumnoRepository extends JpaRepository<ProductoAlumno, Long> {
	List<ProductoAlumno> findByAlumnoId(Long alumnoId);

	Optional<ProductoAlumno> findByAlumnoIdAndProductoId(Long id, Long id2);

	boolean existsByConcepto(String concepto);

	@Query("SELECT pa FROM ProductoAlumno pa JOIN FETCH pa.alumno a WHERE pa.pagado = false ORDER BY a.nombre, a.apellidos, pa.fechaAsignacion")
	List<ProductoAlumno> findAllUnpaidWithAlumno();
}
