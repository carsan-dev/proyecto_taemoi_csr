package com.taemoi.project.errores.pago;

import java.io.Serial;

public class PagoNotFoundException extends RuntimeException {
    /**
     * 
     */
    @Serial
    private static final long serialVersionUID = 1L;

	public PagoNotFoundException(String mensaje) {
		super(mensaje);
	}
}