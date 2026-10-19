package com.taemoi.project.services;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.taemoi.project.entities.AuditoriaAccion;

/**
 * Registro de acciones de la caja (pieza 6b-2, decisión 69 del propietario) en el registro de auditoría de siempre
 * ({@link AuditoriaService#registrarAccesoSensible}): en la MISMA transacción que la acción y sin capturar errores (si no
 * se puede registrar, la acción no se hace). Quién (usuario de la sesión), cuándo y escuela los pone el registro; aquí,
 * el módulo {@value #MODULO}, el tipo de acción, la sede y los ids implicados. El resumen NO lleva nombres ni datos
 * personales: solo el tipo de acción e ids ({@code "CAJA COBRO_PROFESOR sede=#3 cargo=#12 cobro=#45"}).
 */
@Component
public class RegistroAccionesCaja {

	public static final String MODULO = "caja";

	/** Tipo de acción registrada y su acción de auditoría. */
	public enum Accion {
		COBRO_PROFESOR(AuditoriaAccion.CREATE),
		COBRO_PROFESOR_PRODUCTO(AuditoriaAccion.CREATE),
		COBRO_PROFESOR_DESHECHO(AuditoriaAccion.UPDATE),
		ENTREGA_REGISTRADA(AuditoriaAccion.CREATE),
		/** Entrega en la que quien entrega es quien recibe (decisión del propietario: permitida, pero señalada). */
		AUTOENTREGA_REGISTRADA(AuditoriaAccion.CREATE),
		ENTREGA_ANULADA(AuditoriaAccion.UPDATE),
		MOVIMIENTO_CREADO(AuditoriaAccion.CREATE),
		MOVIMIENTO_ANULADO(AuditoriaAccion.UPDATE),
		/** Pieza 6c-3, decisión 193: cobro del profesor ya entregado, anulado con su ERROR +importe en la entrega. */
		COBRO_ENTREGADO_DESHECHO(AuditoriaAccion.UPDATE),
		/** Pieza 6c-6 (153, 195): devolución enlazada al cobro de un encargo, registrada por la sede. */
		DEVOLUCION_CREADA(AuditoriaAccion.CREATE),
		/** Pieza 6c-6 (D6c-10): devolución del profesor que lleva el dinero encima (entrega con contado 0). */
		DEVOLUCION_PROFESOR(AuditoriaAccion.CREATE);

		private final AuditoriaAccion auditoria;

		Accion(AuditoriaAccion auditoria) {
			this.auditoria = auditoria;
		}
	}

	private final AuditoriaService auditoria;

	public RegistroAccionesCaja(AuditoriaService auditoria) {
		this.auditoria = auditoria;
	}

	/**
	 * Registra la acción. {@code ids}: nombre → id (o lista de ids), en el orden dado; el primero es el recurso.
	 * Debe llamarse dentro de la transacción de la acción.
	 */
	public void registrar(Accion accion, String endpoint, Long sedeId, Map<String, ?> ids) {
		Map<String, ?> implicados = ids == null ? Map.of() : new LinkedHashMap<>(ids);
		Long recursoId = implicados.values().stream().filter(Long.class::isInstance).map(Long.class::cast).findFirst()
				.orElse(null);
		String detalle = implicados.entrySet().stream().map(e -> e.getKey() + "=" + formatear(e.getValue()))
				.collect(Collectors.joining(" "));
		String resumen = "CAJA " + accion.name() + " sede=#" + sedeId + (detalle.isEmpty() ? "" : " " + detalle);
		auditoria.registrarAccesoSensible(accion.auditoria, MODULO, endpoint, recursoId, resumen);
	}

	private static String formatear(Object valor) {
		if (valor instanceof java.util.Collection<?> lista) {
			return lista.stream().map(v -> "#" + v).collect(Collectors.joining(",", "[", "]"));
		}
		return "#" + valor;
	}
}
