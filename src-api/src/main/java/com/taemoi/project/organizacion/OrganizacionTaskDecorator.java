package com.taemoi.project.organizacion;

import org.springframework.core.task.TaskDecorator;

/**
 * Propaga la organización activa del hilo que encola la tarea al hilo que la ejecuta
 * ({@code @Async} y el executor de Spring Boot). Si no había organización, la tarea corre
 * sin ella (resolución por defecto) y nunca hereda la de una tarea anterior del pool.
 */
public class OrganizacionTaskDecorator implements TaskDecorator {

	@Override
	public Runnable decorate(Runnable tarea) {
		Long capturada = TenantContext.get();
		return () -> {
			Long anterior = TenantContext.get();
			TenantContext.restaurar(capturada);
			try {
				tarea.run();
			} finally {
				TenantContext.restaurar(anterior);
			}
		};
	}
}
