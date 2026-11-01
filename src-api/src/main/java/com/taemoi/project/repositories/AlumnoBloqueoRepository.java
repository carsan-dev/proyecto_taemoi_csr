package com.taemoi.project.repositories;

import java.util.Optional;

import com.taemoi.project.entities.Alumno;

/** Fragmento de {@link AlumnoRepository}: la ficha de un alumno cuya fila ya está bloqueada. */
public interface AlumnoBloqueoRepository {

	/**
	 * Dentro de una transacción y con la fila ya bloqueada por {@link AlumnoRepository#bloquearEstadoDeSede}: la ficha del
	 * alumno de la escuela activa con lo confirmado en la base, aunque la sesión ya la tuviera cargada (una consulta con
	 * bloqueo no refresca la instancia gestionada, y open-in-view puede haberla cargado antes de la transacción). Sin
	 * releerla, escribir en ella reescribiría todas las columnas con lo que había entonces ({@code Alumno} no tiene
	 * versión). Vacío si no es de la escuela activa.
	 *
	 * <p>El refresco va en cascada a la foto y a los documentos cargados: por eso se comprueba antes
	 * {@code anonimizadoEn} con el estado bloqueado, porque la anonimización borra esas filas.
	 */
	Optional<Alumno> releerBloqueado(Long id);
}
