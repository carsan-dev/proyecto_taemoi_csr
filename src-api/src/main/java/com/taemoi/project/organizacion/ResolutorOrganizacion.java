package com.taemoi.project.organizacion;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Resuelve la organización activa para Hibernate ({@code @TenantId}), las cachés y las tareas.
 *
 * <p>FALLA CERRADO: devuelve el valor de {@link TenantContext} y, si no hay ninguno, lanza
 * {@link IllegalStateException}. No hay organización implícita: cada camino legítimo la fija
 * explícitamente (filtro HTTP {@link OrganizacionRequestFilter}, {@link OrganizacionTaskDecorator}
 * para {@code @Async}, {@link EjecutorPorOrganizacion} para tareas programadas y
 * {@link TenantContext#ejecutarCon} en el arranque).
 *
 * <p>Como Hibernate resuelve la organización al abrir CUALQUIER sesión, también las consultas a
 * tablas globales ({@code usuario}, {@code membresia}, {@code organizacion}) necesitan una fijada.
 *
 * <p>{@link #getOrganizacionPorDefecto()} ({@code app.organizacion.por-defecto}, 1 = Taemoi) solo
 * la usan quienes la fijan a propósito: peticiones anónimas, arranque y búsquedas globales.
 */
public class ResolutorOrganizacion implements CurrentTenantIdentifierResolver<Long> {

	private final Long organizacionPorDefecto;

	public ResolutorOrganizacion(Long organizacionPorDefecto) {
		if (organizacionPorDefecto == null) {
			throw new IllegalArgumentException("app.organizacion.por-defecto es obligatorio");
		}
		this.organizacionPorDefecto = organizacionPorDefecto;
	}

	@Override
	public Long resolveCurrentTenantIdentifier() {
		return organizacionActual();
	}

	@Override
	public boolean validateExistingCurrentSessions() {
		return true;
	}

	/** @throws IllegalStateException si no hay organización fijada en el hilo. */
	public Long organizacionActual() {
		Long actual = TenantContext.get();
		if (actual == null) {
			throw new IllegalStateException(
					"No hay organización activa en este hilo: fíjala con TenantContext antes de acceder a datos");
		}
		return actual;
	}

	public Long getOrganizacionPorDefecto() {
		return organizacionPorDefecto;
	}
}
