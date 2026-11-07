package com.taemoi.project.organizacion;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Sesión, Google y archivos por Host (fase 4, escuela por dominio, pieza 3; {@code docs/multi-escuela/fase4-dominio-plan.md}
 * §3.1 punto 5, §3.2 y §4.2).
 *
 * <p>{@link OrganizacionRequestFilter} anota en la petición el Host SOLO si está en {@code organizacion_dominio}
 * ({@link Registrado}). Todo lo de aquí parte de esa anotación: un Host que no está en la tabla (local, tests, una IP, el
 * nombre interno del contenedor) no se usa nunca para construir una URL ni para decidir una cookie, y todo sale como
 * antes de la pieza. El Host de la configuración ({@code app.base.url}, {@code cors.allowed.origin}), con o sin
 * {@code www.}, tampoco cambia nada: en moiskimdo.es se devuelve la configuración tal cual, byte a byte.
 *
 * <p>Sin dependencias de Spring: los controladores y el serializador lo usan sin cambiar sus constructores ni los tests
 * que los montan sin el filtro (sin anotación, el comportamiento de antes).
 */
public final class HostDeLaPeticion {

	/** Atributo de la petición con el {@link Registrado} del Host (solo si está en la tabla). */
	public static final String ATRIBUTO = HostDeLaPeticion.class.getName() + ".HOST_REGISTRADO";

	/** Host de la configuración ya normalizado (las bases son pocas y fijas: la caché no crece). */
	private static final Map<String, String> HOST_DE_LA_BASE = new ConcurrentHashMap<>();

	/**
	 * Host de la petición que está en {@code organizacion_dominio}.
	 *
	 * @param host normalizado ({@link ResolutorEscuelaPorHost#normalizar}), sacado de la tabla
	 * @param organizacionId escuela del host
	 * @param escuelaPorDefecto si esa escuela es {@code app.organizacion.por-defecto}
	 */
	public record Registrado(String host, Long organizacionId, boolean escuelaPorDefecto) {
	}

	private HostDeLaPeticion() {
	}

	/** Lo llama el filtro una vez por petición: anota el Host si está en la tabla. */
	static void anotar(HttpServletRequest request, ResolutorEscuelaPorHost porHost, Long organizacionPorDefecto) {
		String host = ResolutorEscuelaPorHost.normalizar(request.getServerName());
		porHost.escuelaRegistrada(host).ifPresent(organizacion -> request.setAttribute(ATRIBUTO,
				new Registrado(host, organizacion, organizacion.equals(organizacionPorDefecto))));
	}

	/** El Host de la petición si está en la tabla. */
	public static Optional<Registrado> de(HttpServletRequest request) {
		return request != null && request.getAttribute(ATRIBUTO) instanceof Registrado registrado
				? Optional.of(registrado)
				: Optional.empty();
	}

	/** La petición del hilo actual (vacío en trabajos programados, hilos asíncronos o sin Spring MVC). */
	public static Optional<HttpServletRequest> actual() {
		RequestAttributes atributos = RequestContextHolder.getRequestAttributes();
		return atributos instanceof ServletRequestAttributes servlet ? Optional.of(servlet.getRequest())
				: Optional.empty();
	}

	/**
	 * Origen para URL absolutas y redirecciones: el del propio Host ({@code esquema://host[:puerto]}) si está en la tabla
	 * y no es el Host de {@code porDefecto}; si no, {@code porDefecto} tal cual (lista blanca: nunca un Host ajeno).
	 */
	public static String origen(HttpServletRequest request, String porDefecto) {
		if (porDefecto == null || porDefecto.isBlank()) {
			return porDefecto;
		}
		Optional<Registrado> registrado = de(request);
		String hostPorDefecto = hostDe(porDefecto);
		// Sin host legible en la configuración no se sabe cuál es «el de siempre»: la configuración tal cual.
		if (registrado.isEmpty() || hostPorDefecto.isEmpty() || mismoSitio(registrado.get().host(), hostPorDefecto)) {
			return porDefecto;
		}
		String esquema = request.getScheme() == null ? "" : request.getScheme().toLowerCase(Locale.ROOT);
		if (!"https".equals(esquema) && !"http".equals(esquema)) {
			return porDefecto;
		}
		int puerto = request.getServerPort();
		boolean puertoPorDefecto = puerto <= 0 || ("https".equals(esquema) && puerto == 443)
				|| ("http".equals(esquema) && puerto == 80);
		return esquema + "://" + registrado.get().host() + (puertoPorDefecto ? "" : ":" + puerto);
	}

	/**
	 * Base de las URL de imágenes y documentos de la petición en curso ({@code UrlsEscuela.baseArchivosPeticion} del
	 * plan): {@link #origen} con {@code app.base.url}; sin petición, {@code app.base.url}.
	 */
	public static String baseArchivosPeticion(String appBaseUrl) {
		return actual().map(request -> origen(request, appBaseUrl)).orElse(appBaseUrl);
	}

	/**
	 * Dominio de la cookie de sesión. {@code configurado} es el de siempre (sacado de {@code app.base.url}); se usa salvo
	 * en un Host de la tabla que no sea ese dominio (ni su {@code www.}): allí la cookie es solo del host (sin
	 * {@code Domain}), porque el navegador rechazaría {@code Domain=moiskimdo.es} y, en un subdominio, la sesión viajaría
	 * a moiskimdo.es.
	 */
	public static Optional<String> dominioCookie(HttpServletRequest request, Optional<String> configurado) {
		if (configurado.isEmpty()) {
			return configurado;
		}
		Optional<Registrado> registrado = de(request);
		if (registrado.isPresent() && !mismoSitio(registrado.get().host(), configurado.get())) {
			return Optional.empty();
		}
		return configurado;
	}

	/**
	 * Escuela que se prefiere al iniciar sesión: la del Host si está en la tabla y NO es la escuela por defecto. En
	 * moiskimdo.es (escuela por defecto) o un Host fuera de la tabla, ninguna: la regla de siempre.
	 */
	public static Optional<Long> escuelaPreferidaParaLogin(HttpServletRequest request) {
		return de(request).filter(registrado -> !registrado.escuelaPorDefecto()).map(Registrado::organizacionId);
	}

	/** Mismo host, sin distinguir el {@code www.} delante (nginx redirige www.moiskimdo.es a moiskimdo.es). */
	static boolean mismoSitio(String host, String otro) {
		return host != null && otro != null && sinWww(host).equals(sinWww(otro));
	}

	private static String sinWww(String host) {
		String normalizado = host.toLowerCase(Locale.ROOT);
		return normalizado.startsWith("www.") ? normalizado.substring(4) : normalizado;
	}

	private static String hostDe(String url) {
		return HOST_DE_LA_BASE.computeIfAbsent(url, valor -> {
			try {
				String host = ResolutorEscuelaPorHost.normalizar(URI.create(valor.trim()).getHost());
				return host == null ? "" : host;
			} catch (RuntimeException e) {
				return "";
			}
		});
	}
}
