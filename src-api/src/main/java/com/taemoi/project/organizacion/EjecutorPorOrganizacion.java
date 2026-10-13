package com.taemoi.project.organizacion;

import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.repositories.OrganizacionRepository;

/**
 * Ejecuta una tarea sin petición (p. ej. {@code @Scheduled}) una vez por cada organización
 * activa, con esa organización fijada en {@link TenantContext}. El fallo de una organización
 * se registra y no impide procesar las demás.
 *
 * <p>No debe llamarse dentro de una transacción: cada acción tiene que abrir su propia
 * sesión de Hibernate para que el filtro por organización sea el correcto.
 */
@Component
public class EjecutorPorOrganizacion {

	private static final Logger logger = LoggerFactory.getLogger(EjecutorPorOrganizacion.class);

	private final OrganizacionRepository organizaciones;
	private final ResolutorOrganizacion resolutor;

	public EjecutorPorOrganizacion(OrganizacionRepository organizaciones, ResolutorOrganizacion resolutor) {
		this.organizaciones = organizaciones;
		this.resolutor = resolutor;
	}

	/** @return número de organizaciones procesadas sin error. */
	public int paraCadaOrganizacionActiva(String tarea, Consumer<Long> accion) {
		// La lista de organizaciones es global, pero Hibernate exige una organización para abrir
		// la sesión (fallo cerrado): se consulta con la organización por defecto fijada.
		List<Long> ids = TenantContext.calcularCon(resolutor.getOrganizacionPorDefecto(),
				() -> organizaciones.findByActivaTrueOrderByIdAsc().stream()
						.map(Organizacion::getId)
						.toList());
		int correctas = 0;
		for (Long id : ids) {
			try {
				TenantContext.ejecutarCon(id, () -> accion.accept(id));
				correctas++;
			} catch (RuntimeException e) {
				logger.error("Tarea '{}' fallida para la organización {}: {}", tarea, id, e.getMessage(), e);
			}
		}
		return correctas;
	}
}
