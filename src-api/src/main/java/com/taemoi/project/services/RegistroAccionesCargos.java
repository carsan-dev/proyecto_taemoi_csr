package com.taemoi.project.services;

import org.springframework.stereotype.Component;

import com.taemoi.project.entities.AuditoriaAccion;

/**
 * Registro de acciones sobre cargos (pieza 6b-2c) en el registro de auditoría de siempre
 * ({@link AuditoriaService#registrarAccesoSensible}), como {@link RegistroAccionesCaja}: en la MISMA transacción que la
 * acción y sin capturar errores (si no se puede registrar, la acción no se hace). Quién, cuándo y escuela los pone el
 * registro; aquí, el módulo {@value #MODULO} (el mismo que el registro HTTP da a {@code /api/productos-alumno}), el tipo
 * de acción, la sede y el id del cargo. El resumen NO lleva nombres, conceptos, motivos ni importes: solo ids
 * ({@code "CARGO CARGO_CANCELADO sede=#3 cargo=#12"}).
 */
@Component
public class RegistroAccionesCargos {

	public static final String MODULO = "productos-alumno";

	/** Tipo de acción registrada y su acción de auditoría. */
	public enum Accion {
		CARGO_CANCELADO(AuditoriaAccion.UPDATE),
		CARGO_REACTIVADO(AuditoriaAccion.UPDATE),
		/** Borrado manual del cargo por el propietario ({@code DELETE /api/productos-alumno/{id}}). */
		CARGO_BORRADO(AuditoriaAccion.DELETE);

		private final AuditoriaAccion auditoria;

		Accion(AuditoriaAccion auditoria) {
			this.auditoria = auditoria;
		}
	}

	private final AuditoriaService auditoria;

	public RegistroAccionesCargos(AuditoriaService auditoria) {
		this.auditoria = auditoria;
	}

	/** Registra la acción sobre el cargo. Debe llamarse dentro de la transacción de la acción. */
	public void registrar(Accion accion, String endpoint, Long sedeId, Long cargoId) {
		registrar(accion, endpoint, sedeId, cargoId, null);
	}

	/**
	 * Pieza 6c-3: como {@link #registrar(Accion, String, Long, Long)}; si la acción la ejecuta una solicitud aceptada,
	 * su id va detrás ({@code "CARGO CARGO_CANCELADO sede=#3 cargo=#12 solicitud=#7"}).
	 */
	public void registrar(Accion accion, String endpoint, Long sedeId, Long cargoId, Long solicitudId) {
		String resumen = "CARGO " + accion.name() + " sede=#" + sedeId + " cargo=#" + cargoId
				+ (solicitudId == null ? "" : " solicitud=#" + solicitudId);
		auditoria.registrarAccesoSensible(accion.auditoria, MODULO, endpoint, cargoId, resumen);
	}
}
