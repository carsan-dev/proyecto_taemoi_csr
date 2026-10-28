package com.taemoi.project.services;

/**
 * Pieza 279: recibo de pago en PDF de un cobro. Se genera al momento, sin guardarlo: uno por cobro ({@code Cobro}), con
 * el número {@code R-{año del cobro}-{id del cobro con 6 cifras}}, estable al regenerarlo (solo cambia la fecha de
 * emisión). Un cargo pagado de legado (sin ninguna fila de cobro, anterior a V31_5) tiene su recibo desde el propio
 * cargo, con la serie {@code R-{año del pago}-C{id del cargo con 6 cifras}}.
 */
public interface ReciboPagoService {

	/**
	 * Recibo del cobro ACTIVO de un cargo de la escuela activa o, si el cargo está pagado y no tiene ninguna fila de
	 * cobro (legado), del propio cargo.
	 *
	 * @throws java.util.NoSuchElementException si el cargo no existe en la escuela activa (404)
	 * @throws IllegalStateException            si el cargo no tiene cobro ACTIVO (sin cobrar o con el cobro anulado) o
	 *                                          su cobro ACTIVO tiene una DEVOLUCION ACTIVA en la caja; o, de legado,
	 *                                          si está cancelado, devuelto o sin fecha de pago (409)
	 */
	ReciboPago generar(Long productoAlumnoId);

	/** Número del recibo y el PDF. */
	record ReciboPago(String numero, byte[] pdf) {

		/** Nombre del fichero: {@code recibo-R-2026-000123.pdf} (de legado, {@code recibo-R-2025-C014192.pdf}). */
		public String nombreFichero() {
			return "recibo-" + numero + ".pdf";
		}
	}
}
