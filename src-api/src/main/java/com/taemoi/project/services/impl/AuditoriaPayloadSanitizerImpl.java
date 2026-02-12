package com.taemoi.project.services.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.services.AuditoriaPayloadSanitizer;

@Service
public class AuditoriaPayloadSanitizerImpl implements AuditoriaPayloadSanitizer {

	private static final String MASK = "***";
	private static final Set<String> CLAVES_SENSIBLES = Set.of(
			"password",
			"contrasena",
			"token",
			"authorization",
			"jwt",
			"nuevacontrasena",
			"reset_token",
			"resetToken",
			"secret",
			"client_secret");
	private static final Pattern FORM_SENSITIVE_PATTERN = Pattern.compile(
			"(?i)(password|contrasena|token|authorization|jwt|nuevaContrasena|secret)=([^&\\s]+)");
	private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile(
			"(?i)(Bearer\\s+)[A-Za-z0-9\\-._~+/]+=*");

	private final ObjectMapper objectMapper;

	public AuditoriaPayloadSanitizerImpl(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public String sanitizarPayload(String payload, String contentType) {
		if (payload == null || payload.isBlank()) {
			return "";
		}

		String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
		if (normalizedContentType.contains("multipart/form-data")) {
			return "[payload omitted: multipart/form-data]";
		}
		if (normalizedContentType.contains("application/octet-stream")) {
			return "[payload omitted: application/octet-stream]";
		}

		if (normalizedContentType.contains("application/json")) {
			return sanitizarJson(payload);
		}

		return sanitizarTextoPlano(payload);
	}

	@Override
	public String serializarQueryParams(Map<String, String[]> parameterMap) {
		if (parameterMap == null || parameterMap.isEmpty()) {
			return "{}";
		}

		Map<String, Object> clean = new LinkedHashMap<>();
		parameterMap.forEach((key, values) -> {
			String keyLower = key == null ? "" : key.toLowerCase(Locale.ROOT);
			if (esClaveSensible(keyLower)) {
				clean.put(key, MASK);
				return;
			}

			if (values == null) {
				clean.put(key, "");
			} else if (values.length == 1) {
				clean.put(key, sanitizarTextoPlano(values[0]));
			} else {
				List<String> sanitizedValues = new ArrayList<>(values.length);
				for (String value : values) {
					sanitizedValues.add(sanitizarTextoPlano(value));
				}
				clean.put(key, sanitizedValues);
			}
		});

		try {
			return objectMapper.writeValueAsString(clean);
		} catch (JsonProcessingException ex) {
			return "{}";
		}
	}

	@Override
	public String truncarSiSuperaLimite(String valor, int maxChars) {
		if (valor == null || maxChars < 1 || valor.length() <= maxChars) {
			return valor;
		}
		return valor.substring(0, maxChars);
	}

	private String sanitizarJson(String payload) {
		try {
			Object parsed = objectMapper.readValue(payload, Object.class);
			Object sanitized = sanitizeRecursive(parsed, "");
			return objectMapper.writeValueAsString(sanitized);
		} catch (Exception ex) {
			return sanitizarTextoPlano(payload);
		}
	}

	@SuppressWarnings("unchecked")
	private Object sanitizeRecursive(Object source, String parentKey) {
		if (source instanceof Map<?, ?> map) {
			Map<String, Object> sanitized = new LinkedHashMap<>();
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				String key = entry.getKey() == null ? "" : entry.getKey().toString();
				String keyLower = key.toLowerCase(Locale.ROOT);
				if (esClaveSensible(keyLower) || esClaveSensible(parentKey.toLowerCase(Locale.ROOT))) {
					sanitized.put(key, MASK);
				} else {
					sanitized.put(key, sanitizeRecursive(entry.getValue(), key));
				}
			}
			return sanitized;
		}
		if (source instanceof List<?> list) {
			List<Object> sanitized = new ArrayList<>(list.size());
			for (Object item : list) {
				sanitized.add(sanitizeRecursive(item, parentKey));
			}
			return sanitized;
		}
		if (source instanceof String text) {
			return sanitizarTextoPlano(text);
		}
		return source;
	}

	private boolean esClaveSensible(String keyLower) {
		if (keyLower == null || keyLower.isBlank()) {
			return false;
		}
		return CLAVES_SENSIBLES.stream().anyMatch(keyLower::contains);
	}

	private String sanitizarTextoPlano(String input) {
		if (input == null || input.isBlank()) {
			return "";
		}

		String output = input;
		Matcher formMatcher = FORM_SENSITIVE_PATTERN.matcher(output);
		StringBuffer sb = new StringBuffer();
		while (formMatcher.find()) {
			formMatcher.appendReplacement(sb, formMatcher.group(1) + "=" + MASK);
		}
		formMatcher.appendTail(sb);
		output = sb.toString();

		Matcher bearerMatcher = BEARER_TOKEN_PATTERN.matcher(output);
		sb = new StringBuffer();
		while (bearerMatcher.find()) {
			bearerMatcher.appendReplacement(sb, bearerMatcher.group(1) + MASK);
		}
		bearerMatcher.appendTail(sb);
		return sb.toString();
	}
}
