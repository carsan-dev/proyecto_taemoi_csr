package com.taemoi.project.services;

import java.util.Optional;

import com.taemoi.project.entities.Categoria;

/**
 * Categorías de competición de la organización activa (tabla {@code categoria}, catálogo de V28). Sustituye
 * a {@code TipoCategoria}: a un competidor de una disciplina con categorías se le asigna, por su edad (años
 * cumplidos), la primera categoría de esa disciplina (por {@code orden}) cuyo rango la incluye.
 *
 * <p>En Taemoi solo taekwondo tiene categorías (Infantil hasta 9, Precadete 10-11, Cadete 12-13, Junior
 * 14-16, Senior desde 17): la misma regla que el código anterior aplicaba solo a taekwondo.
 */
public interface CatalogoCategoriasService {

	/** Si la disciplina asigna categoría por edad (tiene alguna categoría). {@code false} si es nula o no existe. */
	boolean asignaCategoriaPorEdad(String codigoDisciplina);

	/** Categoría de la disciplina para esa edad; vacía si la disciplina no tiene ninguna que la incluya. */
	Optional<Categoria> categoriaPorEdad(String codigoDisciplina, int edad);
}
