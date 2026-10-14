package com.taemoi.project.organizacion;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

/** Identifica la sesión (usuario + organización) de una petición. */
@FunctionalInterface
public interface IdentificadorSesion {

	/** Petición sin identificar: anónima, en la organización por defecto. */
	IdentificadorSesion ANONIMO = request -> Optional.empty();

	/**
	 * @return la sesión verificada, o vacío si la petición es anónima o sus credenciales no son
	 *         válidas (se trata como no autenticada).
	 */
	Optional<SesionVerificada> identificar(HttpServletRequest request);
}
