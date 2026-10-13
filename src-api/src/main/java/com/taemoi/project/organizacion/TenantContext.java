package com.taemoi.project.organizacion;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Organización (escuela) activa en el hilo actual.
 *
 * <p>Hibernate la lee al ABRIR cada sesión ({@link ResolutorOrganizacion}); cambiarla con
 * una sesión ya abierta (p. ej. dentro de una transacción o de una petición con
 * open-in-view) no cambia el filtro de esa sesión. Por eso {@link #ejecutarCon} debe
 * envolver la llamada transaccional, no ejecutarse dentro de ella.
 *
 * <p>Quien la fija debe limpiarla siempre en un {@code finally}.
 */
public final class TenantContext {

	private static final ThreadLocal<Long> ACTUAL = new ThreadLocal<>();

	private TenantContext() {
	}

	public static void set(Long organizacionId) {
		ACTUAL.set(Objects.requireNonNull(organizacionId, "organizacionId"));
	}

	/** @return la organización fijada en este hilo, o {@code null} si no hay ninguna. */
	public static Long get() {
		return ACTUAL.get();
	}

	public static void clear() {
		ACTUAL.remove();
	}

	/** Ejecuta {@code accion} con {@code organizacionId} activa y restaura la anterior al terminar. */
	public static void ejecutarCon(Long organizacionId, Runnable accion) {
		calcularCon(organizacionId, () -> {
			accion.run();
			return null;
		});
	}

	/** Igual que {@link #ejecutarCon} pero devolviendo un resultado. */
	public static <T> T calcularCon(Long organizacionId, Supplier<T> accion) {
		Long anterior = ACTUAL.get();
		set(organizacionId);
		try {
			return accion.get();
		} finally {
			restaurar(anterior);
		}
	}

	/** Deja en el hilo exactamente {@code valor} (o ninguna organización si es {@code null}). */
	static void restaurar(Long valor) {
		if (valor == null) {
			ACTUAL.remove();
		} else {
			ACTUAL.set(valor);
		}
	}
}
