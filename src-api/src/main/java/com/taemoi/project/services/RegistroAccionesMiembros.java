package com.taemoi.project.services;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.taemoi.project.entities.AuditoriaAccion;

/**
 * Registro de acciones sobre los miembros de la escuela (pieza E8-3; D8-16, fase3-e8-miembros.md §7) en el registro de
 * auditoría de siempre ({@link AuditoriaService#registrarAccesoSensible}): en la MISMA transacción que la acción y sin
 * capturar errores (si no se puede registrar, la acción no se hace). Quién (usuario de la sesión), cuándo y escuela los
 * pone el registro; aquí, el módulo {@value #MODULO}, el tipo de acción y los ids implicados. Copia de
 * {@link RegistroAccionesSedes}.
 *
 * <p>El resumen solo lleva ids ({@code #n}, también una lista de ids ordenada: {@code sedes=#3,#4}), valores de
 * enumeración y sí/no ({@code "MIEMBROS ROL_DADO usuario=#21 rol=PROFESOR"}): nunca el email, el nombre ni el cargo.
 * Cualquier otro tipo de valor es un error de programación y la acción no se hace. Las asignaciones a grupos van con las
 * acciones del módulo {@code profesor} ({@link RegistroAccionesProfesor}).
 */
@Component
public class RegistroAccionesMiembros {

	public static final String MODULO = "miembros";

	/**
	 * Tipo de acción registrada y su acción de auditoría ({@code usuario=#id rol=ROL}; {@code SEDES_CAMBIADAS},
	 * {@code usuario=#id sedes=#…}; {@code sinCambios=SI} si no cambió nada). Quitar un rol no borra la fila: UPDATE.
	 */
	public enum Accion {
		ROL_DADO(AuditoriaAccion.CREATE),
		ROL_REACTIVADO(AuditoriaAccion.UPDATE),
		ROL_QUITADO(AuditoriaAccion.UPDATE),
		CARGO_CAMBIADO(AuditoriaAccion.UPDATE),
		SEDES_CAMBIADAS(AuditoriaAccion.UPDATE),
		/** Reemplazar los grupos del profesor sin cambiar nada ({@code usuario=#id sinCambios=SI}). */
		GRUPOS_CAMBIADOS(AuditoriaAccion.UPDATE),
		/** Pieza E8-4: cuenta pendiente creada por una invitación ({@code usuario=#id rol=ROL}; nunca el token). */
		MIEMBRO_INVITADO(AuditoriaAccion.CREATE),
		/** Pieza E8-4: invitación reenviada con un token nuevo ({@code usuario=#id}). */
		INVITACION_REENVIADA(AuditoriaAccion.UPDATE),
		/** Decisión 389: invitación cancelada ({@code usuario=#id cuentaBorrada=SI|NO}; nunca el token). */
		INVITACION_CANCELADA(AuditoriaAccion.DELETE);

		private final AuditoriaAccion auditoria;

		Accion(AuditoriaAccion auditoria) {
			this.auditoria = auditoria;
		}
	}

	private final AuditoriaService auditoria;

	public RegistroAccionesMiembros(AuditoriaService auditoria) {
		this.auditoria = auditoria;
	}

	/**
	 * Registra la acción. {@code valores}: nombre → id ({@link Long}), colección de ids, enumeración o {@link Boolean}
	 * (SI/NO), en el orden dado; el primer id es el recurso. Debe llamarse dentro de la transacción de la acción.
	 *
	 * @throws IllegalStateException si algún valor no es un id, una lista de ids, una enumeración ni un sí/no (nunca
	 *         texto libre)
	 */
	public void registrar(Accion accion, String endpoint, Map<String, ?> valores) {
		Map<String, ?> implicados = valores == null ? Map.of() : new LinkedHashMap<>(valores);
		String detalle = implicados.entrySet().stream().map(e -> e.getKey() + "=" + formatear(e.getKey(), e.getValue()))
				.collect(Collectors.joining(" "));
		Long recursoId = implicados.values().stream().filter(Long.class::isInstance).map(Long.class::cast).findFirst()
				.orElse(null);
		String resumen = "MIEMBROS " + accion.name() + (detalle.isEmpty() ? "" : " " + detalle);
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
		if (valor instanceof Collection<?> lista) {
			if (lista.isEmpty()) {
				return "NINGUNO";
			}
			if (!lista.stream().allMatch(Long.class::isInstance)) {
				throw new IllegalStateException("Valor no admitido en el registro de miembros: " + nombre);
			}
			return lista.stream().map(Long.class::cast).sorted().map(id -> "#" + id).collect(Collectors.joining(","));
		}
		throw new IllegalStateException("Valor no admitido en el registro de miembros: " + nombre);
	}
}
