package com.taemoi.project.services;

import java.util.Map;

public interface AuditoriaPayloadSanitizer {

	String sanitizarPayload(String payload, String contentType);

	String serializarQueryParams(Map<String, String[]> parameterMap);

	String truncarSiSuperaLimite(String valor, int maxChars);
}
