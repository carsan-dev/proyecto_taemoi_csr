package com.taemoi.project.services;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.taemoi.project.entities.AuditoriaAccion;
import com.taemoi.project.entities.Solicitud;

/**
 * Registro de acciones de las solicitudes (pieza 6c-2, fase3-6c-solicitudes.md §8) en el registro de auditoría de
 * siempre ({@link AuditoriaService#registrarAccesoSensible}), como {@link RegistroAccionesCaja}: en la MISMA transacción
 * que la acción y sin capturar errores (si no se puede registrar, la acción no se hace). Quién, cuándo y escuela los
 * pone el registro; aquí, el módulo {@value #MODULO}, el tipo de acción, la sede y los ids implicados. El resumen NO
 * lleva el motivo, la respuesta, nombres ni importes: solo ids
 * ({@code "SOLICITUDES SOLICITUD_CREADA sede=#3 solicitud=#7 cobro=#45 cargo=#12"}; {@code sede=-} sin sede).
 */
@Component
public class RegistroAccionesSolicitudes {

	public static final String MODULO = "solicitudes";

	/** Tipo de acción registrada y su acción de auditoría (§8). */
	public enum Accion {
		SOLICITUD_CREADA(AuditoriaAccion.CREATE),
		SOLICITUD_RETIRADA(AuditoriaAccion.UPDATE),
		SOLICITUD_DENEGADA(AuditoriaAccion.UPDATE),
		SOLICITUD_ACEPTADA(AuditoriaAccion.UPDATE),
		SOLICITUD_NO_APLICABLE(AuditoriaAccion.UPDATE),
		ENCARGO_PEDIDO(AuditoriaAccion.CREATE),
		ENCARGO_ENCARGADO(AuditoriaAccion.UPDATE),
		ENCARGO_RECIBIDO(AuditoriaAccion.UPDATE),
		ENCARGO_ENTREGADO(AuditoriaAccion.UPDATE),
		ENCARGO_RECHAZADO(AuditoriaAccion.UPDATE),
		ENCARGO_DEVOLUCION_REGISTRADA(AuditoriaAccion.UPDATE);

		private final AuditoriaAccion auditoria;

		Accion(AuditoriaAccion auditoria) {
			this.auditoria = auditoria;
		}
	}

	private final AuditoriaService auditoria;

	public RegistroAccionesSolicitudes(AuditoriaService auditoria) {
		this.auditoria = auditoria;
	}

	/** Registra la acción sobre la solicitud (ya guardada), con los ids de su recurso. */
	public void registrar(Accion accion, String endpoint, Solicitud solicitud) {
		registrar(accion, endpoint, solicitud, Map.of());
	}

	/**
	 * Registra la acción con los ids del recurso de la solicitud y, detrás, los {@code extra} (nombre → id), en el orden
	 * dado. El recurso registrado es la solicitud. Debe llamarse dentro de la transacción de la acción.
	 */
	public void registrar(Accion accion, String endpoint, Solicitud solicitud, Map<String, ?> extra) {
		Map<String, Object> ids = new LinkedHashMap<>();
		ids.put("solicitud", solicitud.getId());
		ids.put("cobro", solicitud.getCobroId());
		ids.put("cargo", solicitud.getProductoAlumnoId());
		ids.put("entrega", solicitud.getEntregaCajaId());
		ids.put("movimiento", solicitud.getMovimientoCajaId());
		ids.put("producto", solicitud.getProductoId());
		if (extra != null) {
			ids.putAll(extra);
		}
		String detalle = ids.entrySet().stream().filter(e -> e.getValue() != null)
				.map(e -> e.getKey() + "=#" + e.getValue()).collect(Collectors.joining(" "));
		String sede = solicitud.getSedeId() == null ? "-" : "#" + solicitud.getSedeId();
		String resumen = "SOLICITUDES " + accion.name() + " sede=" + sede + " " + detalle;
		auditoria.registrarAccesoSensible(accion.auditoria, MODULO, endpoint, solicitud.getId(), resumen);
	}
}
