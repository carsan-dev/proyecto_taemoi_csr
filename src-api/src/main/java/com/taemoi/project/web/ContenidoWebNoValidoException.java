package com.taemoi.project.web;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 400 del guardado del contenido de la web: {@code {mensaje, errores: {clave: texto}}}. No se guarda nada. */
public class ContenidoWebNoValidoException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final transient Map<String, String> errores;

	public ContenidoWebNoValidoException(Map<String, String> errores) {
		super(ContenidoWebService.NO_VALIDO);
		this.errores = Collections.unmodifiableMap(new LinkedHashMap<>(errores));
	}

	public Map<String, String> getErrores() {
		return errores;
	}
}
