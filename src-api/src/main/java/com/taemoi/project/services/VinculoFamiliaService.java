package com.taemoi.project.services;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Usuario;

/**
 * Escritura de {@code usuario_alumno} en las altas de cuenta de familia (fase3-sedes-permisos.md §5 «Altas
 * nuevas»; fase 3, entrega 2b). La lectura vive en {@link AccesoFamiliaService}.
 */
public interface VinculoFamiliaService {

	/**
	 * Vincula la cuenta recién creada (registro o primer login con Google) con el alumno: fila con origen
	 * {@code REGISTRO}, la organización del alumno y la relación por edad (como V31). Idempotente: si ya existe la
	 * fila (usuario, alumno) no se duplica ni se modifica.
	 *
	 * <p>No comprueba el email: quien llama solo pasa alumnos de la escuela con el email de la cuenta.
	 *
	 * @return {@code true} si se ha escrito la fila; {@code false} si ya existía.
	 * @throws IllegalStateException si el alumno no es de la escuela de la sesión ({@code TenantContext}) o no
	 *         hay ninguna fijada: nunca se mezclan escuelas.
	 * @throws IllegalArgumentException si la cuenta o el alumno no tienen id.
	 */
	boolean vincularAlta(Usuario usuario, Alumno alumno);
}
