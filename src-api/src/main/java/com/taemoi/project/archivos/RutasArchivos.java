package com.taemoi.project.archivos;

import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.util.UriUtils;

import com.taemoi.project.organizacion.TenantContext;

/**
 * Reglas comunes de las rutas de ficheros (imágenes y documentos) por escuela.
 *
 * <p>Los ficheros nuevos viven bajo {@code org-<id>/…} y en base de datos solo se guarda la ruta
 * RELATIVA a la raíz configurada ({@code app.imagenes.directorio.*} / {@code app.documentos.directorio.*}).
 * Las rutas sin prefijo son las heredadas de la escuela por defecto.
 */
public final class RutasArchivos {

	public static final String PREFIJO_ORGANIZACION = "org-";
	public static final String CARPETA_ALUMNOS = "alumnos";
	public static final String CARPETA_EVENTOS = "eventos";
	public static final String CARPETA_MATERIALES = "materiales";
	/** Carpetas heredadas (sin prefijo de escuela). */
	public static final String CARPETA_DOCUMENTOS_ALUMNOS_LEGADO = "Documentos_Alumnos_Moiskimdo";
	public static final String CARPETA_DOCUMENTOS_EVENTOS_LEGADO = "Documentos_Eventos";
	public static final String CARPETA_MATERIALES_LEGADO = "Materiales_Examen";

	private static final Pattern SEGMENTO_ORGANIZACION = Pattern.compile("^org-(\\d{1,18})$", Pattern.CASE_INSENSITIVE);
	private static final Pattern UNIDAD_WINDOWS = Pattern.compile("^[A-Za-z]:.*");
	private static final int LONGITUD_MAXIMA = 1024;

	private RutasArchivos() {
	}

	/** @return {@code org-<id>} */
	public static String carpetaOrganizacion(Long organizacionId) {
		if (organizacionId == null || organizacionId <= 0) {
			throw new IllegalArgumentException("Organización no válida para la ruta de ficheros: " + organizacionId);
		}
		return PREFIJO_ORGANIZACION + organizacionId;
	}

	/**
	 * Organización activa del hilo. FALLA CERRADO: sin organización no se lee ni se escribe ningún
	 * fichero de escuela.
	 */
	public static Long organizacionActiva() {
		Long actual = TenantContext.get();
		if (actual == null) {
			throw new IllegalStateException("No hay organización activa para acceder a ficheros");
		}
		return actual;
	}

	/**
	 * @return la organización del primer segmento ({@code org-<id>}) o {@code null} si la ruta es heredada
	 *         (sin prefijo).
	 */
	public static Long organizacionDeRuta(String rutaRelativa) {
		if (rutaRelativa == null || rutaRelativa.isBlank()) {
			return null;
		}
		String normalizada = rutaRelativa.replace('\\', '/');
		int fin = normalizada.indexOf('/');
		String primero = fin < 0 ? normalizada : normalizada.substring(0, fin);
		Matcher m = SEGMENTO_ORGANIZACION.matcher(primero);
		if (!m.matches()) {
			return null;
		}
		try {
			return Long.valueOf(m.group(1));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Ruta absoluta (Unix, UNC o con unidad de Windows), como las guardadas por versiones anteriores. */
	public static boolean esRutaAbsoluta(String ruta) {
		if (ruta == null || ruta.isBlank()) {
			return false;
		}
		String r = ruta.trim();
		if (r.startsWith("/") || r.startsWith("\\") || UNIDAD_WINDOWS.matcher(r).matches()) {
			return true;
		}
		try {
			return Path.of(r).isAbsolute();
		} catch (InvalidPathException e) {
			// Ruta no representable en este sistema: se trata como no relativa (falla cerrado).
			return true;
		}
	}

	/**
	 * Resuelve {@code rutaRelativa} bajo {@code base} y garantiza que el resultado no sale de ella.
	 *
	 * @throws IllegalArgumentException si la ruta es absoluta o escapa de la base.
	 */
	public static Path resolverDentroDe(Path base, String rutaRelativa) {
		if (rutaRelativa == null || rutaRelativa.isBlank() || esRutaAbsoluta(rutaRelativa)) {
			throw new IllegalArgumentException("Ruta de fichero no permitida.");
		}
		Path baseNormalizada = base.toAbsolutePath().normalize();
		Path resuelta = baseNormalizada.resolve(rutaRelativa.replace('\\', '/')).normalize();
		if (!resuelta.startsWith(baseNormalizada) || resuelta.equals(baseNormalizada)) {
			throw new IllegalArgumentException("Ruta de fichero fuera del directorio permitido.");
		}
		return resuelta;
	}

	/**
	 * Valida y decodifica la parte de una URL por debajo de {@code /imagenes/} o {@code /documentos/}
	 * (tal como llega, codificada). Rechaza cualquier forma de recorrido: segmentos vacíos, {@code .},
	 * {@code ..} (también codificados), separadores codificados, barras invertidas, {@code :} (unidades
	 * y flujos alternativos NTFS), caracteres de control, {@code ;} y segmentos que acaban en punto o
	 * espacio (Windows los ignora y apuntarían a otro fichero).
	 *
	 * @return la ruta relativa decodificada con {@code /} como separador, o vacío si no es válida.
	 */
	public static Optional<String> normalizarRutaSolicitada(String rutaCodificada) {
		if (rutaCodificada == null || rutaCodificada.isEmpty() || rutaCodificada.length() > LONGITUD_MAXIMA
				|| rutaCodificada.indexOf('\\') >= 0) {
			return Optional.empty();
		}
		String[] segmentos = rutaCodificada.split("/", -1);
		List<String> decodificados = new ArrayList<>(segmentos.length);
		for (String segmento : segmentos) {
			if (segmento.isEmpty()) {
				return Optional.empty();
			}
			String decodificado;
			try {
				decodificado = UriUtils.decode(segmento, StandardCharsets.UTF_8);
			} catch (IllegalArgumentException e) {
				return Optional.empty();
			}
			if (!segmentoValido(decodificado)) {
				return Optional.empty();
			}
			decodificados.add(decodificado);
		}
		return Optional.of(String.join("/", decodificados));
	}

	private static boolean segmentoValido(String segmento) {
		if (segmento.isEmpty() || ".".equals(segmento) || "..".equals(segmento)
				|| segmento.endsWith(".") || segmento.endsWith(" ")) {
			return false;
		}
		for (int i = 0; i < segmento.length(); i++) {
			char c = segmento.charAt(i);
			if (c < 0x20 || c == 0x7f || c == '/' || c == '\\' || c == ':' || c == ';') {
				return false;
			}
		}
		return true;
	}

	/**
	 * LISTA BLANCA: una imagen solo es PÚBLICA si es de un evento, {@code eventos/…} (heredada, sin prefijo)
	 * u {@code org-<id>/eventos/…}, y no tiene ningún segmento {@code alumnos}. Todo lo demás (fotos de
	 * alumnos, ficheros sueltos en la raíz, carpetas desconocidas) es privado.
	 */
	public static boolean esImagenPublica(String rutaRelativa) {
		return esDeEventos(rutaRelativa, CARPETA_EVENTOS);
	}

	/**
	 * Un documento es PÚBLICO solo si es de un evento: {@code Documentos_Eventos/…} (heredado, sin prefijo)
	 * o {@code org-<id>/eventos/…}. Todo lo demás (alumnos, materiales, desconocido) es privado.
	 */
	public static boolean esDocumentoPublico(String rutaRelativa) {
		return esDeEventos(rutaRelativa, CARPETA_DOCUMENTOS_EVENTOS_LEGADO);
	}

	/**
	 * {@code <carpetaLegado>/…} sin prefijo, u {@code org-<id>/eventos/…}; con al menos un nivel por debajo
	 * de la carpeta de eventos y sin ningún segmento de alumnos.
	 */
	private static boolean esDeEventos(String rutaRelativa, String carpetaLegado) {
		List<String> segmentos = segmentos(rutaRelativa);
		for (String segmento : segmentos) {
			if (segmento.equalsIgnoreCase(CARPETA_ALUMNOS)
					|| segmento.equalsIgnoreCase(CARPETA_DOCUMENTOS_ALUMNOS_LEGADO)) {
				return false;
			}
		}
		boolean conPrefijo = organizacionDeRuta(rutaRelativa) != null;
		int indiceCategoria = conPrefijo ? 1 : 0;
		if (segmentos.size() < indiceCategoria + 2) {
			return false;
		}
		String categoria = segmentos.get(indiceCategoria);
		return conPrefijo ? categoria.equalsIgnoreCase(CARPETA_EVENTOS) : categoria.equalsIgnoreCase(carpetaLegado);
	}

	private static List<String> segmentos(String rutaRelativa) {
		if (rutaRelativa == null || rutaRelativa.isBlank()) {
			return List.of();
		}
		List<String> resultado = new ArrayList<>();
		for (String s : rutaRelativa.replace('\\', '/').split("/")) {
			if (!s.isEmpty()) {
				resultado.add(s);
			}
		}
		return resultado;
	}

	/**
	 * Raíz configurada según el sistema operativo (misma regla que los servicios de imágenes y documentos:
	 * en Windows se expande {@code %USERPROFILE%}).
	 */
	public static Path directorioBase(String directorioLinux, String directorioWindows) {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		String directorio;
		if (os.contains("win")) {
			String userProfile = System.getenv("USERPROFILE");
			if (userProfile == null || userProfile.isBlank()) {
				userProfile = System.getProperty("user.home", "");
			}
			directorio = directorioWindows.replace("%USERPROFILE%", userProfile);
		} else {
			directorio = directorioLinux;
		}
		return Path.of(directorio).toAbsolutePath().normalize();
	}
}
