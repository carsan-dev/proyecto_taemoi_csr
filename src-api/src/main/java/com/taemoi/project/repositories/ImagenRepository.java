package com.taemoi.project.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entities.Imagen;

/**
 * Repositorio para la entidad Imagen.
 */
@Repository
public interface ImagenRepository extends JpaRepository<Imagen, Long> {

	/** Fichero nuevo ({@code org-<id>/…}) por ruta exacta; filtrado por la escuela activa ({@code @TenantId}). */
	List<Imagen> findByRuta(String ruta);

	/** Candidatas heredadas (rutas absolutas) por nombre de fichero; filtrado por escuela. */
	List<Imagen> findByRutaEndingWith(String nombreArchivo);
}
