package com.taemoi.project.services;

import java.util.Map;
import java.util.Set;

public interface AuditoriaPayloadSanitizer {

	String sanitizarPayload(String payload, String contentType);

	String serializarQueryParams(Map<String, String[]> parameterMap);

	String truncarSiSuperaLimite(String valor, int maxChars);

	/**
	 * Sustituye por {@code "[redactado]"} el valor de las claves indicadas (exactas, sin distinguir
	 * mayúsculas, a cualquier profundidad) de un payload JSON ya saneado. Si no es JSON válido devuelve
	 * {@code "[redactado]"} entero.
	 */
	String redactarClaves(String payloadJson, Set<String> claves);
}
