package com.taemoi.project.config;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.taemoi.project.entities.AuditoriaAccion;
import com.taemoi.project.services.AuditoriaPayloadSanitizer;
import com.taemoi.project.services.AuditoriaService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AuditoriaRequestFilter extends OncePerRequestFilter {

	private static final Logger logger = LoggerFactory.getLogger(AuditoriaRequestFilter.class);
	private static final int MAX_ENDPOINT_CHARS = 512;
	private static final int MAX_USER_AGENT_CHARS = 400;
	private static final int MAX_PAYLOAD_CHARS = 12000;
	private static final Set<String> AUDITABLE_METHODS = Set.of("GET", "POST", "PUT", "DELETE");

	private final AuditoriaService auditoriaService;
	private final AuditoriaPayloadSanitizer auditoriaPayloadSanitizer;

	public AuditoriaRequestFilter(
			AuditoriaService auditoriaService,
			AuditoriaPayloadSanitizer auditoriaPayloadSanitizer) {
		this.auditoriaService = auditoriaService;
		this.auditoriaPayloadSanitizer = auditoriaPayloadSanitizer;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, java.io.IOException {
		String method = request.getMethod() == null ? "" : request.getMethod().toUpperCase();
		String endpoint = normalizarPath(request);

		if (!debeAuditar(method, endpoint)) {
			filterChain.doFilter(request, response);
			return;
		}

		ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
		ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

		try {
			filterChain.doFilter(wrappedRequest, wrappedResponse);
		} finally {
			try {
				registrarEventoSiCorresponde(wrappedRequest, wrappedResponse, method, endpoint);
			} catch (Exception ex) {
				logger.error("Error registrando auditoria de request", ex);
			}
			wrappedResponse.copyBodyToResponse();
		}
	}

	private void registrarEventoSiCorresponde(
			ContentCachingRequestWrapper request,
			ContentCachingResponseWrapper response,
			String method,
			String endpoint) {
		int status = response.getStatus();
		if (!esEstadoAuditable(status)) {
			return;
		}

		AuditoriaAccion accion = parsearAccion(method);
		if (accion == null) {
			return;
		}

		String payloadRaw = leerPayloadRequest(request);
		String payloadSanitizado = auditoriaPayloadSanitizer.sanitizarPayload(payloadRaw, request.getContentType());
		boolean payloadTruncado = payloadSanitizado != null && payloadSanitizado.length() > MAX_PAYLOAD_CHARS;
		String payloadFinal = auditoriaPayloadSanitizer.truncarSiSuperaLimite(payloadSanitizado, MAX_PAYLOAD_CHARS);
		String queryParamsJson = auditoriaPayloadSanitizer.serializarQueryParams(request.getParameterMap());
		String ipCliente = obtenerIpCliente(request);
		String userAgent = truncar(request.getHeader("User-Agent"), MAX_USER_AGENT_CHARS);

		auditoriaService.registrarEvento(
				accion,
				method,
				truncar(endpoint, MAX_ENDPOINT_CHARS),
				queryParamsJson,
				payloadFinal,
				payloadTruncado,
				status,
				ipCliente,
				userAgent);
	}

	private boolean debeAuditar(String method, String endpoint) {
		if (endpoint == null || endpoint.isBlank()) {
			return false;
		}
		if (!endpoint.startsWith("/api/")) {
			return false;
		}
		if (!AUDITABLE_METHODS.contains(method)) {
			return false;
		}
		return !endpoint.startsWith("/api/auth/")
				&& !"/api/auth".equals(endpoint)
				&& !endpoint.startsWith("/api/admin/auditoria/");
	}

	private String normalizarPath(HttpServletRequest request) {
		String uri = request.getRequestURI();
		String contextPath = request.getContextPath();
		if (contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath)) {
			return uri.substring(contextPath.length());
		}
		return uri;
	}

	private String leerPayloadRequest(ContentCachingRequestWrapper request) {
		byte[] buffer = request.getContentAsByteArray();
		if (buffer == null || buffer.length == 0) {
			return "";
		}

		Charset charset = StandardCharsets.UTF_8;
		String encoding = request.getCharacterEncoding();
		if (encoding != null && !encoding.isBlank()) {
			try {
				charset = Charset.forName(encoding);
			} catch (Exception ignored) {
				charset = StandardCharsets.UTF_8;
			}
		}
		return new String(buffer, charset);
	}

	private AuditoriaAccion parsearAccion(String method) {
		return switch (method) {
		case "GET" -> AuditoriaAccion.READ;
		case "POST" -> AuditoriaAccion.CREATE;
		case "PUT" -> AuditoriaAccion.UPDATE;
		case "DELETE" -> AuditoriaAccion.DELETE;
		default -> null;
		};
	}

	private String obtenerIpCliente(HttpServletRequest request) {
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			return truncar(forwardedFor.split(",")[0].trim(), 120);
		}

		String realIp = request.getHeader("X-Real-IP");
		if (realIp != null && !realIp.isBlank()) {
			return truncar(realIp.trim(), 120);
		}

		return truncar(request.getRemoteAddr(), 120);
	}

	private String truncar(String value, int maxChars) {
		if (value == null || value.length() <= maxChars) {
			return value;
		}
		return value.substring(0, maxChars);
	}

	private boolean esEstadoAuditable(int status) {
		return (status >= 200 && status < 300) || (status >= 400 && status < 600);
	}
}
