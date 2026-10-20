package com.taemoi.project.services;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.taemoi.project.entities.AuditoriaAccion;

/**
 * Registro de acciones del profesor (entrega 7, D7-18; fase3-e7-profesor.md §7) en el registro de auditoría de siempre
 * ({@link AuditoriaService#registrarAccesoSensible}): en la MISMA transacción que la acción y sin capturar errores (si no
 * se puede registrar, la acción no se hace). Quién (usuario de la sesión), cuándo y escuela los pone el registro; aquí,
 * el módulo {@value #MODULO}, el tipo de acción y los ids implicados. Copia de {@link RegistroAccionesCaja}.
 *
 * <p>El resumen solo lleva ids ({@code #n}), valores de enumeración y sí/no: nunca texto libre, nombres ni fechas
 * ({@code "PROFESOR PASE_EXAMEN alumno=#12 alumnoDeporte=#30 gradoActual=#5 gradoSiguiente=#6 cargo=#88"}). Cualquier
 * otro tipo de valor es un error de programación y la acción no se hace.
 */
@Component
public class RegistroAccionesProfesor {

	public static final String MODULO = "profesor";

	/**
	 * Tipo de acción registrada y su acción de auditoría. Pieza E7-3: grados; las de asignación de profesores (§7:
	 * {@code ASIGNACION_CREADA}, {@code ASIGNACION_ROL_CAMBIADO}, {@code ASIGNACION_QUITADA},
	 * {@code MEMBRESIA_PROFESOR_CREADA}) las añade la E7-4 al usarlas.
	 */
	public enum Accion {
		APTO_CAMBIADO(AuditoriaAccion.UPDATE),
		FECHA_GRADO_CAMBIADA(AuditoriaAccion.UPDATE),
		PASE_EXAMEN(AuditoriaAccion.CREATE),
		PASE_RECOMPENSA(AuditoriaAccion.CREATE);

		private final AuditoriaAccion auditoria;

		Accion(AuditoriaAccion auditoria) {
			this.auditoria = auditoria;
		}
	}

	private final AuditoriaService auditoria;

	public RegistroAccionesProfesor(AuditoriaService auditoria) {
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
		String resumen = "PROFESOR " + accion.name() + (detalle.isEmpty() ? "" : " " + detalle);
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
		throw new IllegalStateException("Valor no admitido en el registro del profesor: " + nombre);
	}
}
