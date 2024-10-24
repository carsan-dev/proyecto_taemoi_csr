package com.taemoi.project.errores.pago;

public class PagoNotFoundException extends RuntimeException {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public PagoNotFoundException(String mensaje) {
        super(mensaje);
    }
}