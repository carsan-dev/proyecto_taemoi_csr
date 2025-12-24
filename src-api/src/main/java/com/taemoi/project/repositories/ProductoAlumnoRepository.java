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

	@Query("SELECT pa FROM ProductoAlumno pa JOIN FETCH pa.alumno a WHERE pa.concepto LIKE 'MENSUALIDAD%' ORDER BY a.nombre, a.apellidos, pa.fechaAsignacion DESC")
	List<ProductoAlumno> findAllMensualidadesWithAlumno();

	@Query("SELECT pa FROM ProductoAlumno pa JOIN FETCH pa.alumno a WHERE pa.concepto LIKE 'MENSUALIDAD%' AND a.deporte = :deporte ORDER BY a.nombre, a.apellidos, pa.fechaAsignacion DESC")
	List<ProductoAlumno> findMensualidadesByDeporteWithAlumno(com.taemoi.project.entities.Deporte deporte);

	@Query("SELECT pa FROM ProductoAlumno pa JOIN FETCH pa.alumno a WHERE pa.concepto = :concepto AND (a.deporte = com.taemoi.project.entities.Deporte.TAEKWONDO OR a.deporte = com.taemoi.project.entities.Deporte.KICKBOXING) ORDER BY a.nombre, a.apellidos")
	List<ProductoAlumno> findMensualidadByConceptoAndDeportes(String concepto);
}
