package com.taemoi.project.dtos.response;

import java.math.BigDecimal;

/**
 * Pieza 9-1 (decisión 369): qué pasa al cambiar de sede a un alumno. Lo devuelven la lectura previa
 * ({@code GET /api/alumnos/{id}/cambio-sede}) y el cambio ({@code PUT /api/alumnos/{id}/sede}), que da el mismo dato
 * ya aplicado: la sede de la que viene el alumno y lo que se movió.
 *
 * @param sedeActualId       sede principal antes del cambio; {@code null} si el alumno no tiene
 * @param sedeActualNombre   su nombre; {@code null} si el alumno no tiene sede
 * @param sedeNuevaId        sede de destino
 * @param sedeNuevaNombre    su nombre
 * @param cargosPendientes   cargos sin cobrar que pasan a la sede nueva
 * @param importePendiente   lo que suman
 * @param cargosCobrados     cargos pagados de la sede actual, que se quedan en ella; sin sede actual, los pagados sin
 *                           sede
 * @param importeCobrado     lo que suman
 * @param gruposEnSedeActual grupos del alumno en la sede actual, que no cambian; sin sede actual, los grupos sin sede
 */
public record CambioSedeAlumnoDTO(Long sedeActualId, String sedeActualNombre, Long sedeNuevaId, String sedeNuevaNombre,
		long cargosPendientes, BigDecimal importePendiente, long cargosCobrados, BigDecimal importeCobrado,
		long gruposEnSedeActual) {
}
