package com.taemoi.project.organizacion;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Escuela del Host de una petición (fase 4, escuela por dominio; {@code docs/multi-escuela/fase4-dominio-plan.md} §3.1).
 *
 * <p>El host sale de {@link HttpServletRequest#getServerName()}: detrás de nginx, en producción, ya lo corrige
 * {@code ForwardedHeaderFilter} ({@code server.forward-headers-strategy=framework}) con {@code X-Forwarded-Host}. Se
 * normaliza (minúsculas, sin puerto ni punto final) y se busca EXACTO en la tabla {@code organizacion_dominio}. Un host
 * que no está (local, tests, una IP, {@code /healthz}) da {@code app.organizacion.por-defecto}, como hasta ahora.
 *
 * <p>La tabla entera se guarda en memoria: buscar NO consulta la base (ni una sentencia más por petición). Se carga la
 * primera vez que hace falta y se recarga con {@link #recargar()} (cada minuto, {@link OrganizacionWebConfig}; aún no
 * hay pantalla que escriba la tabla). Si la carga falla se queda la última buena y, si no hubo ninguna, todo va a la por
 * defecto: un fallo nunca deja una petición sin escuela.
 */
public class ResolutorEscuelaPorHost {

	/** Atributo de la petición con la escuela de su Host ({@code Long}), para el login y las URL (piezas posteriores). */
	public static final String ATRIBUTO = ResolutorEscuelaPorHost.class.getName() + ".ESCUELA_DEL_HOST";

	/** Inicio del aviso cuando la sesión es de otra escuela que el Host (manda la sesión). */
	public static final String AVISO_SESION_Y_HOST = "Sesión de otra escuela que la del host";

	/** Longitud máxima de un nombre DNS (y de la columna {@code host}). */
	static final int LONGITUD_MAXIMA = 253;

	private static final Logger log = LoggerFactory.getLogger(ResolutorEscuelaPorHost.class);

	/**
	 * Contenido de {@code organizacion_dominio}.
	 *
	 * @param hosts host → escuela
	 * @param principales escuela → host principal (el de los enlaces de correo)
	 */
	public record Dominios(Map<String, Long> hosts, Map<Long, String> principales) {
	}

	private final Supplier<Dominios> cargador;
	private final Long organizacionPorDefecto;
	private final Object cerrojo = new Object();
	private volatile Dominios tabla;

	/**
	 * @param cargador devuelve la tabla {@code host → organizacion_id} (se normalizan los hosts al cargarla)
	 * @param organizacionPorDefecto {@code app.organizacion.por-defecto}
	 */
	public ResolutorEscuelaPorHost(Supplier<Map<String, Long>> cargador, Long organizacionPorDefecto) {
		this(organizacionPorDefecto, conPrincipales(Objects.requireNonNull(cargador, "cargador")));
	}

	/**
	 * @param organizacionPorDefecto {@code app.organizacion.por-defecto}
	 * @param cargador devuelve los hosts y el host principal de cada escuela
	 */
	public ResolutorEscuelaPorHost(Long organizacionPorDefecto, Supplier<Dominios> cargador) {
		this.cargador = Objects.requireNonNull(cargador, "cargador");
		this.organizacionPorDefecto = Objects.requireNonNull(organizacionPorDefecto, "organizacionPorDefecto");
	}

	private static Supplier<Dominios> conPrincipales(Supplier<Map<String, Long>> hosts) {
		return () -> new Dominios(hosts.get(), Map.of());
	}

	/** Sin tabla: siempre la escuela por defecto (el comportamiento de antes de la pieza). */
	public static ResolutorEscuelaPorHost sinDominios(Long organizacionPorDefecto) {
		return new ResolutorEscuelaPorHost(Map::of, organizacionPorDefecto);
	}

	public Long getOrganizacionPorDefecto() {
		return organizacionPorDefecto;
	}

	/** Escuela del Host de la petición; la por defecto si no está en la tabla. */
	public Long escuelaDe(HttpServletRequest request) {
		return escuelaDelHost(request.getServerName());
	}

	/** Escuela de {@code host}; la por defecto si no está en la tabla. */
	public Long escuelaDelHost(String host) {
		return escuelaRegistrada(host).orElse(organizacionPorDefecto);
	}

	/** Escuela de {@code host} solo si está en la tabla (lista blanca para construir URL en piezas posteriores). */
	public Optional<Long> escuelaRegistrada(String host) {
		String normalizado = normalizar(host);
		return normalizado == null ? Optional.empty() : Optional.ofNullable(tabla().hosts().get(normalizado));
	}

	/**
	 * Host principal de la escuela (normalizado), de la tabla en memoria: no consulta la base. Vacío si la escuela no
	 * tiene dominio principal.
	 */
	public Optional<String> hostPrincipalDe(Long organizacionId) {
		return organizacionId == null ? Optional.empty() : Optional.ofNullable(tabla().principales().get(organizacionId));
	}

	/** Vuelve a leer la tabla. Si falla, se queda la anterior (o ninguna: todo a la por defecto). */
	public void recargar() {
		synchronized (cerrojo) {
			cargar();
		}
	}

	private Dominios tabla() {
		Dominios actual = tabla;
		if (actual == null) {
			synchronized (cerrojo) {
				if (tabla == null) {
					cargar();
				}
				actual = tabla;
			}
		}
		return actual;
	}

	private void cargar() {
		try {
			Dominios leidos = cargador.get();
			Map<String, Long> nueva = new HashMap<>();
			leidos.hosts().forEach((host, organizacion) -> {
				String normalizado = normalizar(host);
				if (normalizado != null && organizacion != null) {
					nueva.put(normalizado, organizacion);
				}
			});
			Map<Long, String> principales = new HashMap<>();
			leidos.principales().forEach((organizacion, host) -> {
				String normalizado = normalizar(host);
				if (normalizado != null && organizacion != null) {
					principales.put(organizacion, normalizado);
				}
			});
			tabla = new Dominios(Map.copyOf(nueva), Map.copyOf(principales));
		} catch (RuntimeException e) {
			log.warn("No se pudo leer organizacion_dominio; se mantiene la tabla anterior: {}", e.toString());
			if (tabla == null) {
				tabla = new Dominios(Map.of(), Map.of());
			}
		}
	}

	/**
	 * Host en minúsculas, sin espacios, puerto ni punto final; {@code null} si queda vacío o es más largo que un nombre
	 * DNS. Un IPv6 entre corchetes se deja tal cual (nunca está en la tabla).
	 */
	public static String normalizar(String host) {
		if (host == null) {
			return null;
		}
		String valor = host.trim().toLowerCase(Locale.ROOT);
		if (valor.startsWith("[")) {
			int cierre = valor.indexOf(']');
			valor = cierre > 0 ? valor.substring(0, cierre + 1) : valor;
		} else {
			int dosPuntos = valor.indexOf(':');
			if (dosPuntos >= 0 && dosPuntos == valor.lastIndexOf(':')) {
				valor = valor.substring(0, dosPuntos);
			}
		}
		while (valor.endsWith(".")) {
			valor = valor.substring(0, valor.length() - 1);
		}
		return valor.isEmpty() || valor.length() > LONGITUD_MAXIMA ? null : valor;
	}
}
