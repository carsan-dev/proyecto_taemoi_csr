package com.taemoi.project.services;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.taemoi.project.entities.AuditoriaAccion;

/**
 * Registro de acciones sobre las sedes (pieza E8-2; D8-16, fase3-e8-miembros.md §7) en el registro de auditoría de
 * siempre ({@link AuditoriaService#registrarAccesoSensible}): en la MISMA transacción que la acción y sin capturar
 * errores (si no se puede registrar, la acción no se hace). Quién (usuario de la sesión), cuándo y escuela los pone el
 * registro; aquí, el módulo {@value #MODULO}, el tipo de acción y los ids implicados. Copia de
 * {@link RegistroAccionesProfesor}.
 *
 * <p>El resumen solo lleva ids ({@code #n}), valores de enumeración y sí/no ({@code "SEDES SEDE_CREADA sede=#5"}):
 * nunca el nombre, la dirección, el teléfono ni el email de la sede. Cualquier otro tipo de valor es un error de
 * programación y la acción no se hace.
 */
@Component
public class RegistroAccionesSedes {

	public static final String MODULO = "sedes";

	/** Tipo de acción registrada y su acción de auditoría ({@code sede=#id}; {@code sinCambios=SI} si no cambió nada). */
	public enum Accion {
		SEDE_CREADA(AuditoriaAccion.CREATE),
		SEDE_EDITADA(AuditoriaAccion.UPDATE),
		SEDE_DESACTIVADA(AuditoriaAccion.UPDATE),
		SEDE_ACTIVADA(AuditoriaAccion.UPDATE),
		/** Pieza 9-1 (decisión 369): alumno, sede anterior (NINGUNO si no tenía), sede nueva y cargos movidos. */
		ALUMNO_SEDE_CAMBIADA(AuditoriaAccion.UPDATE);

		private final AuditoriaAccion auditoria;

		Accion(AuditoriaAccion auditoria) {
			this.auditoria = auditoria;
		}
	}

	private final AuditoriaService auditoria;

	public RegistroAccionesSedes(AuditoriaService auditoria) {
		this.auditoria = auditoria;
	}

	/**
	 * Registra la acción. {@code valores}: nombre → id ({@link Long}), enumeración o {@link Boolean} (SI/NO), en el
	 * orden dado; el primer id es el recurso. Debe llamarse dentro de la transacción de la acción.
	 *
	 * @throws IllegalStateException si algún valor no es un id, una enumeración ni un sí/no (nunca texto libre)
	 */
	public void registrar(Accion accion, String endpoint, Map<String, ?> valores) {
		Map<String, ?> implicados = valores == null ? Map.of() : new LinkedHashMap<>(valores);
		Long recursoId = implicados.values().stream().filter(Long.class::isInstance).map(Long.class::cast).findFirst()
				.orElse(null);
		String detalle = implicados.entrySet().stream().map(e -> e.getKey() + "=" + formatear(e.getKey(), e.getValue()))
				.collect(Collectors.joining(" "));
		String resumen = "SEDES " + accion.name() + (detalle.isEmpty() ? "" : " " + detalle);
		auditoria.registrarAccesoSensible(accion.auditoria, MODULO, endpoint, recursoId, resumen);
	}

	static String formatear(String nombre, Object valor) {
		if (valor instanceof Long id) {
			return "#" + id;
		}
		if (valor instanceof Boolean si) {
			return si ? "SI" : "NO";
		}
		if (valor instanceof Enum<?> enumeracion) {
			return enumeracion.name();
		}
		if (valor == null) {
			return "NINGUNO";
		}
		throw new IllegalStateException("Valor no admitido en el registro de sedes: " + nombre);
	}
}
