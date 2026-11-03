package com.taemoi.project.services;

import java.util.List;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.entities.UsuarioAlumno;

/**
 * Vínculos de familia en {@code usuario_alumno}: su escritura en las altas de cuenta de familia
 * (fase3-sedes-permisos.md §5 «Altas nuevas»; fase 3, entrega 2b) y, desde la pieza E10-2, las cuentas de una ficha y
 * las fichas propias de una cuenta, que sustituyen a {@code usuario.alumno_id}. Qué fichas VE una cuenta como familia
 * no se decide aquí: vive en {@link AccesoFamiliaService}.
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

	/**
	 * Cuentas vinculadas a la ficha: sus filas de {@code usuario_alumno} en la organización dada (la del alumno), de
	 * cualquier relación y origen, en orden de {@code usuario_id}. Es el único sitio que responde «la ficha ya tiene
	 * cuenta» (decisión 428: alguna fila de otra cuenta) y «qué cuentas pierden FAMILIA al borrarla» (decisión 429).
	 *
	 * @return vacío si falta el alumno o la organización.
	 */
	List<UsuarioAlumno> cuentasDeLaFicha(Long alumnoId, Long organizacionId);

	/**
	 * Ids (ascendentes) de las fichas con relación {@code PROPIO} de la cuenta en la organización dada (decisión 431).
	 * No dice si las ve: quien llama lo comprueba.
	 *
	 * @return vacío si falta la cuenta o la organización.
	 */
	List<Long> fichasPropiasDeLaCuenta(Long usuarioId, Long organizacionId);
}
