package com.taemoi.project.config;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UriUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.taemoi.project.controllers.AlumnoSaludController;
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
	// PATCH desde la 6b-3c (marca «Lo puede vender el profesor» de /api/productos): se audita como el PUT.
	private static final Set<String> AUDITABLE_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
	/**
	 * Datos de salud: nunca se guarda el cuerpo; las lecturas correctas ya las audita AlumnoSaludService.
	 * Se reconoce por el controlador que atendió la petición y, si no llegó a ninguno (401/403/404), por
	 * la ruta DECODIFICADA y normalizada (una ruta como {@code /api/alumnos/1/%73alud} también cuenta). Pieza E7-2: también
	 * la lectura del profesor ({@code /api/profesor/alumnos/{id}/salud}), que registra AlumnoSaludService#obtenerComoProfesor.
	 */
	private static final Pattern RUTA_SALUD = Pattern.compile("^/api/(profesor/)?alumnos/[^/]+/salud/?$");
	static final String PAYLOAD_OMITIDO_SALUD = "[payload omitted: datos de salud]";
	private static final Map<Pattern, Set<String>> CLAVES_REDACTADAS_POR_RUTA = Map.of(
			Pattern.compile("^/api/admin/dni-compartidos/aplicar/?$"), Set.of("grupo"));

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

		boolean rutaSalud = esPeticionDeSalud(request, endpoint);
		if (rutaSalud && accion == AuditoriaAccion.READ && status >= 200 && status < 300) {
			return;
		}

		String payloadRaw = rutaSalud ? "" : leerPayloadRequest(request);
		String payloadSanitizado = rutaSalud
				? PAYLOAD_OMITIDO_SALUD
				: auditoriaPayloadSanitizer.sanitizarPayload(payloadRaw, request.getContentType());
		Set<String> clavesRedactadas = clavesRedactadas(endpoint);
		if (!rutaSalud && !clavesRedactadas.isEmpty()) {
			payloadSanitizado = auditoriaPayloadSanitizer.redactarClaves(payloadSanitizado, clavesRedactadas);
		}
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

	/** Claves del cuerpo que no se guardan en la auditoría para una ruta (p. ej. la referencia cifrada a un DNI). */
	static Set<String> clavesRedactadas(String endpoint) {
		String ruta = normalizarParaComparar(endpoint);
		for (Map.Entry<Pattern, Set<String>> regla : CLAVES_REDACTADAS_POR_RUTA.entrySet()) {
			if (regla.getKey().matcher(ruta).matches()) {
				return regla.getValue();
			}
		}
		return Set.of();
	}

	static boolean esPeticionDeSalud(HttpServletRequest request, String endpoint) {
		Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
		if (handler instanceof HandlerMethod metodo
				&& AlumnoSaludController.class.isAssignableFrom(metodo.getBeanType())) {
			return true;
		}
		return RUTA_SALUD.matcher(normalizarParaComparar(endpoint)).matches();
	}

	/** Decodifica (hasta dos veces), quita parámetros de ruta ({@code ;x}), une barras y pasa a minúsculas. */
	static String normalizarParaComparar(String ruta) {
		if (ruta == null) {
			return "";
		}
		String r = ruta;
		for (int i = 0; i < 2; i++) {
			try {
				r = UriUtils.decode(r, StandardCharsets.UTF_8);
			} catch (IllegalArgumentException e) {
				break;
			}
		}
		r = r.replace('\\', '/').replaceAll(";[^/]*", "").replaceAll("/{2,}", "/");
		return r.toLowerCase(Locale.ROOT);
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
		case "PUT", "PATCH" -> AuditoriaAccion.UPDATE;
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
