package com.taemoi.project.organizacion;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Resuelve la organización activa para Hibernate ({@code @TenantId}), las cachés y las tareas.
 *
 * <p>Orden: valor de {@link TenantContext} si está fijado; si no, la organización por
 * defecto configurada en {@code app.organizacion.por-defecto} (1 = Taemoi).
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

	public Long organizacionActual() {
		Long actual = TenantContext.get();
		return actual != null ? actual : organizacionPorDefecto;
	}

	public Long getOrganizacionPorDefecto() {
		return organizacionPorDefecto;
	}
}
