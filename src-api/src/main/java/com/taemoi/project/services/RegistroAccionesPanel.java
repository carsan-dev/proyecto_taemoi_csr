package com.taemoi.project.services;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.taemoi.project.entities.AuditoriaAccion;

/**
 * Registro de acciones del interruptor del diseño nuevo (pieza 8b-I, fase3-e8b-plan.md §3) en el registro de auditoría
 * de siempre ({@link AuditoriaService#registrarAccesoSensible}): en la MISMA transacción que la acción y sin capturar
 * errores (si no se puede registrar, la acción no se hace). Quién, cuándo y escuela los pone el registro; aquí, el módulo
 * {@value #MODULO}, el tipo de acción, el id de la cuenta y sí/no ({@code "PANEL DISENO_PERMITIR usuario=#21"}). Copia
 * de {@link RegistroAccionesSedes}.
 *
 * <p>Nunca el email, el nombre ni el cargo. Cualquier otro tipo de valor es un error de programación y la acción no se
 * hace.
 */
@Component
public class RegistroAccionesPanel {

	public static final String MODULO = "panel";

	/** Tipo de acción registrada ({@code sinCambios=SI} si no cambió nada). */
	public enum Accion {
		/** El propietario da el permiso ({@code usuario=#id}). */
		DISENO_PERMITIR,
		/** El propietario quita el permiso ({@code usuario=#id}). */
		DISENO_QUITAR,
		/** La cuenta lo enciende para sí ({@code usuario=#id}). */
		DISENO_ACTIVAR,
		/** La cuenta lo apaga para sí ({@code usuario=#id}). */
		DISENO_DESACTIVAR,
		/** El propietario cambia el valor por defecto de la escuela ({@code nuevo=SI|NO}). */
		DISENO_POR_DEFECTO
	}

	private final AuditoriaService auditoria;

	public RegistroAccionesPanel(AuditoriaService auditoria) {
		this.auditoria = auditoria;
	}

	/**
	 * Registra la acción. {@code valores}: nombre → id ({@link Long}) o {@link Boolean} (SI/NO), en el orden dado; el
	 * primer id es el recurso. Debe llamarse dentro de la transacción de la acción.
	 *
	 * @throws IllegalStateException si algún valor no es un id ni un sí/no (nunca texto libre)
	 */
	public void registrar(Accion accion, String endpoint, Map<String, ?> valores) {
		Map<String, ?> implicados = valores == null ? Map.of() : new LinkedHashMap<>(valores);
		Long recursoId = implicados.values().stream().filter(Long.class::isInstance).map(Long.class::cast).findFirst()
				.orElse(null);
		String detalle = implicados.entrySet().stream().map(e -> e.getKey() + "=" + formatear(e.getKey(), e.getValue()))
				.collect(Collectors.joining(" "));
		String resumen = "PANEL " + accion.name() + (detalle.isEmpty() ? "" : " " + detalle);
		auditoria.registrarAccesoSensible(AuditoriaAccion.UPDATE, MODULO, endpoint, recursoId, resumen);
	}

	static String formatear(String nombre, Object valor) {
		if (valor instanceof Long id) {
			return "#" + id;
		}
		if (valor instanceof Boolean si) {
			return si ? "SI" : "NO";
		}
		throw new IllegalStateException("Valor no admitido en el registro del panel: " + nombre);
	}
}
