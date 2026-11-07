package com.taemoi.project.controllers;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Semaphore;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.taemoi.project.archivos.RutasArchivos;
import com.taemoi.project.archivos.UrlArchivoSerializer;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.organizacion.HostDeLaPeticion;
import com.taemoi.project.services.ImagenService;

/**
 * Respuesta HTTP de la foto de un alumno, común a la ruta de familias y gestión
 * ({@code GET /api/alumnos/{alumnoId}/imagen}) y a la del profesor
 * ({@code GET /api/profesor/alumnos/{alumnoId}/imagen}): busca el fichero, con los respaldos de las fotos heredadas, y
 * aplica {@code ?w=} con sus límites, ETag y Last-Modified.
 *
 * <p>Quien llama decide antes si la sesión puede ver la foto: aquí no se comprueba. El semáforo es uno para toda la JVM:
 * las dos rutas comparten el tope de redimensiones simultáneas.
 *
 * <p>El profesor se diferencia en tres cosas, porque su foto va tras sesión y no puede leer {@code /imagenes/alumnos/**}
 * (solo gestión): caché privada, sin redirección (sin fichero, 404) y solo fotos raster.
 *
 * <p>Decisión 422: en las dos rutas solo se lee un fichero cuya ruta real (sin enlaces) queda dentro de las carpetas
 * de imágenes, y se lee por esa ruta real. Si la fila apunta fuera (o su nombre no es un nombre de fichero simple), la
 * respuesta es 404 sin redirección. La redirección a la URL guardada solo va a {@code /imagenes/…} o a un origen propio
 * ({@code app.imagenes.redireccion.origenes}; por defecto {@code app.base.url} y {@code app.frontend.base-url}): mismo
 * esquema, el mismo host exacto y el mismo puerto, sin usuario en la URL. A otro host no: quien abre la foto acabaría
 * en un sitio ajeno, que además sabría quién la mira.
 */
@Component
public class RespuestaFotoAlumno {

	private static final Logger logger = LoggerFactory.getLogger(RespuestaFotoAlumno.class);
	/** Raíces heredadas de los respaldos por nombre (Docker y local). */
	private static final String RAIZ_HEREDADA_DOCKER = "/var/www/app/imagenes";
	private static final String RAIZ_HEREDADA_LOCAL = "static_resources/imagenes";
	private static final String PREFIJO_URL_IMAGENES = "/imagenes/";
	private static final int MIN_ANCHO_IMAGEN = 120;
	private static final int MAX_ANCHO_IMAGEN = 1800;
	private static final float CALIDAD_WEBP_REDIMENSION = 0.76f;
	private static final long MAX_TAMANO_ORIGEN_RESIZE_BYTES = 8L * 1024 * 1024;
	private static final long MAX_PIXELES_ORIGEN_RESIZE = 12_000_000L;
	private static final Semaphore RESIZE_SEMAPHORE = new Semaphore(2, true);
	private static final CacheControl CACHE_FAMILIA_O_GESTION = CacheControl.maxAge(Duration.ofHours(8)).cachePublic()
			.mustRevalidate();
	/**
	 * La de la imagen de un evento oculto, que también va tras sesión. No {@code no-cache}: el ETag se compara después de
	 * construir la respuesta, así que cada revalidación repetiría la redimensión, y con 22 fotos a la vez las dos plazas
	 * del semáforo no llegan.
	 */
	private static final CacheControl CACHE_PROFESOR = CacheControl.maxAge(Duration.ofHours(8)).cachePrivate()
			.mustRevalidate();

	private final ImagenService imagenService;
	private final String baseUrl;
	private final String directorioImagenesLinux;
	private final String directorioImagenesWindows;
	private final List<Origen> origenesRedireccion;

	public RespuestaFotoAlumno(ImagenService imagenService, @Value("${app.base.url}") String baseUrl,
			@Value("${app.imagenes.directorio.linux:}") String directorioImagenesLinux,
			@Value("${app.imagenes.directorio.windows:}") String directorioImagenesWindows,
			@Value("${app.imagenes.redireccion.origenes:${app.base.url:},${app.frontend.base-url:}}") String origenesRedireccion) {
		this.imagenService = imagenService;
		this.baseUrl = baseUrl;
		this.directorioImagenesLinux = directorioImagenesLinux;
		this.directorioImagenesWindows = directorioImagenesWindows;
		this.origenesRedireccion = leerOrigenes(origenesRedireccion);
	}

	/** Esquema y host en minúsculas y puerto efectivo (443 o 80 si la URL no lo lleva). */
	private record Origen(String esquema, String host, int puerto) {
	}

	/** Los orígenes {@code http(s)://host[:puerto]} de la lista separada por comas; los que no lo son se ignoran. */
	private static List<Origen> leerOrigenes(String lista) {
		List<Origen> origenes = new ArrayList<>();
		if (lista == null) {
			return origenes;
		}
		for (String valor : lista.split(",")) {
			Origen origen = origenDe(valor.trim());
			if (origen != null && !origenes.contains(origen)) {
				origenes.add(origen);
			}
		}
		return origenes;
	}

	/**
	 * El origen de una URL absoluta {@code http(s)} con host y sin usuario, o {@code null}. Sin barras invertidas: cada
	 * navegador las interpreta a su manera.
	 */
	private static Origen origenDe(String url) {
		if (url == null || url.isEmpty() || url.indexOf('\\') >= 0) {
			return null;
		}
		try {
			URI uri = new URI(url);
			if (!uri.isAbsolute() || uri.isOpaque() || uri.getRawUserInfo() != null || uri.getHost() == null
					|| uri.getHost().isEmpty()) {
				return null;
			}
			String esquema = uri.getScheme().toLowerCase(Locale.ROOT);
			int puerto = uri.getPort();
			if ("https".equals(esquema)) {
				puerto = puerto < 0 ? 443 : puerto;
			} else if ("http".equals(esquema)) {
				puerto = puerto < 0 ? 80 : puerto;
			} else {
				return null;
			}
			return new Origen(esquema, uri.getHost().toLowerCase(Locale.ROOT), puerto);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Sin fichero, redirige a la URL guardada de la foto si la hay; si no, 404.
	 *
	 * @param imagen          foto del alumno; {@code null} si no tiene
	 * @param anchoSolicitado {@code ?w=} tal como llega
	 */
	public ResponseEntity<Resource> paraFamiliaOGestion(Imagen imagen, Integer anchoSolicitado) throws IOException {
		return servir(imagen, anchoSolicitado, CACHE_FAMILIA_O_GESTION, false);
	}

	/**
	 * Sin fichero, o con un tipo que no es una foto raster, 404: el panel cae a las iniciales.
	 *
	 * @param imagen          foto del alumno; {@code null} si no tiene
	 * @param anchoSolicitado {@code ?w=} tal como llega
	 */
	public ResponseEntity<Resource> paraProfesor(Imagen imagen, Integer anchoSolicitado) throws IOException {
		return servir(imagen, anchoSolicitado, CACHE_PROFESOR, true);
	}

	private ResponseEntity<Resource> servir(Imagen imagen, Integer anchoSolicitado, CacheControl cacheControl,
			boolean paraProfesor) throws IOException {
		Resolucion resolucion = resolverRutaImagenAlumno(imagen);
		if (resolucion.rechazada()) {
			// Decisión 422: la fila apunta fuera de la carpeta de imágenes. Sin foto y sin redirigir a su URL.
			return ResponseEntity.notFound().build();
		}
		Path rutaArchivo = resolucion.real();
		if (rutaArchivo == null) {
			return sinFichero(imagen, paraProfesor);
		}
		Resource recurso = new UrlResource(rutaArchivo.toUri());
		if (!recurso.exists()) {
			return sinFichero(imagen, paraProfesor);
		}

		MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
		if (imagen.getTipo() != null) {
			try {
				mediaType = MediaType.parseMediaType(imagen.getTipo());
			} catch (Exception e) {
				mediaType = MediaType.APPLICATION_OCTET_STREAM;
			}
		}
		if (paraProfesor && !esFotoParaProfesor(mediaType)) {
			return ResponseEntity.notFound().build();
		}

		int anchoNormalizado = normalizarAnchoImagen(anchoSolicitado);
		if (anchoNormalizado > 0) {
			ResponseEntity<Resource> respuestaRedimensionada = intentarConstruirRespuestaRedimensionada(
					rutaArchivo, mediaType, imagen.getNombre(), anchoNormalizado, cacheControl);
			if (respuestaRedimensionada != null) {
				return respuestaRedimensionada;
			}
		}

		return construirRespuestaImagenOriginal(recurso, mediaType, imagen.getNombre(), rutaArchivo, anchoNormalizado,
				cacheControl);
	}

	private ResponseEntity<Resource> sinFichero(Imagen imagen, boolean paraProfesor) {
		ResponseEntity<Resource> redirect = paraProfesor ? null : construirRedireccionImagenAlumno(imagen);
		return redirect != null ? redirect : ResponseEntity.notFound().build();
	}

	/**
	 * Una imagen concreta que no sea SVG. Sin tipo, o con uno inválido, llega {@code application/octet-stream} y no se
	 * sirve: el fichero podría ser cualquier cosa. Un SVG puede llevar scripts y la sesión va en una cookie: abierto como
	 * página correría en el dominio del panel. Las fotos nuevas nunca lo son ({@code ImagenService} las recodifica); solo
	 * podría venir de filas heredadas.
	 */
	private static boolean esFotoParaProfesor(MediaType mediaType) {
		return "image".equalsIgnoreCase(mediaType.getType()) && !mediaType.isWildcardSubtype()
				&& !mediaType.getSubtype().toLowerCase(Locale.ROOT).contains("svg");
	}

	private ResponseEntity<Resource> construirRespuestaImagenOriginal(
			Resource recurso,
			MediaType mediaType,
			String nombreImagen,
			Path rutaArchivo,
			int anchoNormalizado,
			CacheControl cacheControl) throws IOException {
		return ResponseEntity.ok()
				.cacheControl(cacheControl)
				.lastModified(Files.getLastModifiedTime(rutaArchivo).toMillis())
				.header(HttpHeaders.VARY, "Accept-Encoding")
				.contentType(mediaType)
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nombreImagen + "\"")
				.header(HttpHeaders.ETAG, generarEtagImagen(rutaArchivo, anchoNormalizado, mediaType.toString()))
				.body(recurso);
	}

	private ResponseEntity<Resource> intentarConstruirRespuestaRedimensionada(
			Path rutaArchivo,
			MediaType mediaTypeOriginal,
			String nombreImagen,
			int anchoObjetivo,
			CacheControl cacheControl) {
		if (!esImagenRasterRedimensionable(mediaTypeOriginal)) {
			return null;
		}

		if (!RESIZE_SEMAPHORE.tryAcquire()) {
			return null;
		}

		try {
			long tamanoArchivo = Files.size(rutaArchivo);
			if (tamanoArchivo > MAX_TAMANO_ORIGEN_RESIZE_BYTES) {
				return null;
			}

			int[] dimensionesOriginales = obtenerDimensionesImagen(rutaArchivo);
			if (dimensionesOriginales == null) {
				return null;
			}

			int anchoOriginal = dimensionesOriginales[0];
			int altoOriginal = dimensionesOriginales[1];
			if (anchoOriginal <= 0 || altoOriginal <= 0) {
				return null;
			}
			if (anchoOriginal <= anchoObjetivo) {
				return null;
			}
			if ((long) anchoOriginal * altoOriginal > MAX_PIXELES_ORIGEN_RESIZE) {
				return null;
			}

			BufferedImage original = ImageIO.read(rutaArchivo.toFile());
			if (original == null || original.getWidth() <= 0 || original.getHeight() <= 0) {
				return null;
			}

			int altoObjetivo = Math.max(1,
					(int) Math.round((double) original.getHeight() * anchoObjetivo / original.getWidth()));
			BufferedImage redimensionada = new BufferedImage(anchoObjetivo, altoObjetivo, BufferedImage.TYPE_3BYTE_BGR);

			Graphics2D graphics = redimensionada.createGraphics();
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.drawImage(original, 0, 0, anchoObjetivo, altoObjetivo, null);
			graphics.dispose();

			byte[] imagenBytes = codificarWebp(redimensionada, CALIDAD_WEBP_REDIMENSION);
			MediaType mediaTypeSalida = MediaType.parseMediaType("image/webp");
			ByteArrayResource recurso = new ByteArrayResource(imagenBytes);
			long lastModified = Files.getLastModifiedTime(rutaArchivo).toMillis();

			return ResponseEntity.ok()
					.cacheControl(cacheControl)
					.lastModified(lastModified)
					.header(HttpHeaders.VARY, "Accept-Encoding")
					.contentType(mediaTypeSalida)
					.contentLength(imagenBytes.length)
					.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nombreImagen + "\"")
					.header(HttpHeaders.ETAG, generarEtagImagen(rutaArchivo, anchoObjetivo, mediaTypeSalida.toString()))
					.body(recurso);
		} catch (Throwable e) {
			return null;
		} finally {
			RESIZE_SEMAPHORE.release();
		}
	}

	private int[] obtenerDimensionesImagen(Path rutaArchivo) {
		try (ImageInputStream input = ImageIO.createImageInputStream(rutaArchivo.toFile())) {
			if (input == null) {
				return null;
			}
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				return null;
			}

			ImageReader reader = readers.next();
			try {
				reader.setInput(input);
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				return new int[] { width, height };
			} finally {
				reader.dispose();
			}
		} catch (Exception e) {
			return null;
		}
	}

	private byte[] codificarWebp(BufferedImage imagen, float quality) throws IOException {
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
		if (!writers.hasNext()) {
			writers = ImageIO.getImageWritersBySuffix("webp");
		}
		if (!writers.hasNext()) {
			throw new IOException("No hay codificador WebP disponible.");
		}

		ImageWriter writer = writers.next();
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		try (ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(outputStream)) {
			writer.setOutput(imageOutputStream);
			ImageWriteParam writeParam = writer.getDefaultWriteParam();
			if (writeParam.canWriteCompressed()) {
				writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
				String[] compressionTypes = writeParam.getCompressionTypes();
				if (compressionTypes != null && compressionTypes.length > 0) {
					writeParam.setCompressionType(seleccionarTipoCompresion(compressionTypes));
				}
				writeParam.setCompressionQuality(quality);
			}
			writer.write(null, new IIOImage(imagen, null, null), writeParam);
			imageOutputStream.flush();
			return outputStream.toByteArray();
		} finally {
			writer.dispose();
		}
	}

	private String seleccionarTipoCompresion(String[] compressionTypes) {
		for (String type : compressionTypes) {
			if (type != null && type.equalsIgnoreCase("Lossy")) {
				return type;
			}
		}
		return compressionTypes[0];
	}

	private boolean esImagenRasterRedimensionable(MediaType mediaType) {
		if (mediaType == null || !mediaType.getType().equalsIgnoreCase("image")) {
			return false;
		}
		String subtype = mediaType.getSubtype() != null ? mediaType.getSubtype().toLowerCase() : "";
		// Evitamos redimensionado runtime para WebP por inestabilidad del decoder nativo en prod (SIGSEGV).
		return !subtype.contains("svg") && !subtype.contains("webp");
	}

	private int normalizarAnchoImagen(Integer anchoSolicitado) {
		if (anchoSolicitado == null || anchoSolicitado <= 0) {
			return 0;
		}
		if (anchoSolicitado < MIN_ANCHO_IMAGEN) {
			return MIN_ANCHO_IMAGEN;
		}
		return Math.min(anchoSolicitado, MAX_ANCHO_IMAGEN);
	}

	private String generarEtagImagen(Path rutaArchivo, int ancho, String formato) throws IOException {
		long tamano = Files.size(rutaArchivo);
		long modificado = Files.getLastModifiedTime(rutaArchivo).toMillis();
		String hash = Long.toHexString(tamano) + "-" + Long.toHexString(modificado) + "-" + ancho + "-" + formato;
		return "\"" + hash + "\"";
	}

	private ResponseEntity<Resource> construirRedireccionImagenAlumno(Imagen imagen) {
		if (imagen == null) {
			return null;
		}

		String nombre = imagen.getNombre();
		String urlImagen = imagen.getUrl();
		List<String> candidatos = new ArrayList<>();
		// Fase 4 (pieza 3): el origen del Host de otra escuela de organizacion_dominio; en moiskimdo.es, un Host fuera
		// de la tabla o sin petición, app.base.url como siempre.
		String base = HostDeLaPeticion.baseArchivosPeticion(baseUrl);

		if (urlImagen != null && !urlImagen.isBlank() && esUrlRedirigible(urlImagen.trim(), base)) {
			// Las URL nuevas se guardan sin host (/imagenes/…): se completan al leer con app.base.url.
			candidatos.add(UrlArchivoSerializer.absoluta(urlImagen.trim(), base));
		}
		// Los respaldos por nombre en la carpeta heredada solo valen para imágenes heredadas (sin org-<id>/).
		if (nombre != null && !nombre.isBlank() && esNombreSimple(nombre.trim())
				&& RutasArchivos.organizacionDeRuta(imagen.getRuta()) == null) {
			candidatos.add("/imagenes/alumnos/" + nombre);
			if (base != null && !base.isBlank()) {
				candidatos.add(base + "/imagenes/alumnos/" + nombre);
			}
		}

		for (String candidato : candidatos) {
			if (candidato == null || candidato.isBlank()) {
				continue;
			}
			if (candidato.contains("/api/alumnos/")) {
				continue;
			}
			try {
				return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(candidato)).build();
			} catch (Exception ignored) {
				// Ignore invalid candidates and continue.
			}
		}

		return null;
	}

	/**
	 * Decisión 422: la URL guardada solo se usa para redirigir si es relativa a {@code /imagenes/} sin {@code ..} o de un
	 * origen propio: {@code https} (o {@code http} si el origen propio lo es), el mismo host exacto sin distinguir
	 * mayúsculas (no vale un subdominio ni un sufijo), el mismo puerto y sin usuario delante del host. Cualquier otra
	 * ({@code file:}, {@code javascript:}, {@code //host}, otro host…) no. Fase 4 (pieza 3): también vale el origen de
	 * {@code base}, que solo difiere de {@code app.base.url} en un Host de {@code organizacion_dominio} (lista blanca).
	 */
	private boolean esUrlRedirigible(String url, String base) {
		if (url.startsWith(PREFIJO_URL_IMAGENES)) {
			return url.indexOf('\\') < 0 && !tieneSegmentoPadre(url);
		}
		Origen origen = origenDe(url);
		if (origen == null) {
			return false;
		}
		// En moiskimdo.es la base es app.base.url: solo la lista configurada, exactamente como antes.
		return origenesRedireccion.contains(origen) || (!Objects.equals(base, baseUrl) && origen.equals(origenDe(base)));
	}

	/** Fichero que se puede leer, por su ruta real, o si la fila se rechaza por apuntar fuera de la carpeta. */
	private record Resolucion(Path real, boolean rechazada) {
		static final Resolucion SIN_FICHERO = new Resolucion(null, false);
		static final Resolucion RECHAZADA = new Resolucion(null, true);
	}

	private Resolucion resolverRutaImagenAlumno(Imagen imagen) {
		if (imagen == null) {
			return Resolucion.SIN_FICHERO;
		}

		List<Path> candidatos = new ArrayList<>();
		// Ruta relativa (org-<id>/alumnos/…) bajo la raíz de imágenes, o absoluta heredada tal cual.
		Path rutaGuardada = imagenService.resolverRutaArchivo(imagen);
		if (rutaGuardada != null) {
			candidatos.add(rutaGuardada);
		}
		if (RutasArchivos.esRutaAbsoluta(imagen.getRuta())) {
			// Compatibilidad: rutas heredadas con variables (%USERPROFILE%…).
			agregarCandidatoRuta(candidatos, imagen.getRuta());
		} else if (rutaGuardada == null) {
			// ImagenService la rechaza (p. ej. sale por un enlace): se mira igual para responder 404 sin redirigir.
			agregarCandidatoRelativo(candidatos, imagen.getRuta());
		}

		String nombre = obtenerNombreImagenPrioritario(imagen);
		// Decisión 422: un nombre con separadores, .. o absoluto haría salir a los respaldos de su carpeta.
		boolean nombreRechazado = nombre != null && !nombre.isBlank() && !esNombreSimple(nombre);
		// Una imagen con prefijo org-<id>/ solo se lee de su ruta: nunca de ficheros heredados de la raíz.
		if (nombre != null && !nombre.isBlank() && !nombreRechazado
				&& RutasArchivos.organizacionDeRuta(imagen.getRuta()) == null) {
			agregarCandidatosDesdeBase(candidatos, directorioImagenesLinux, nombre);
			agregarCandidatosDesdeBase(candidatos, directorioImagenesWindows, nombre);
			agregarCandidatoRuta(candidatos, RAIZ_HEREDADA_DOCKER + "/alumnos/" + nombre);
			agregarCandidatoRuta(candidatos, RAIZ_HEREDADA_DOCKER + "/" + nombre);
			agregarCandidatoRuta(candidatos, RAIZ_HEREDADA_LOCAL + "/alumnos/" + nombre);
		}

		List<Path> raices = null;
		boolean fueraDeLaCarpeta = false;
		for (Path candidato : candidatos) {
			try {
				if (candidato != null && Files.exists(candidato) && Files.isRegularFile(candidato)) {
					if (raices == null) {
						raices = raicesDeImagenes();
					}
					Path real = realDentroDeAlguna(candidato, raices);
					if (real == null) {
						fueraDeLaCarpeta = true;
						continue;
					}
					// Todo lo posterior (lectura, redimensión, ETag) usa la ruta real ya comprobada.
					return new Resolucion(real, false);
				}
			} catch (Exception ignored) {
				// Intentionally ignore invalid/unsupported candidate paths.
			}
		}

		if (fueraDeLaCarpeta || nombreRechazado) {
			// Sin la ruta: solo el id de la fila, para localizarla.
			logger.warn("Foto de alumno fuera de la carpeta de imágenes o con nombre no válido; sin foto (imagen {})",
					imagen.getId());
			return Resolucion.RECHAZADA;
		}
		return Resolucion.SIN_FICHERO;
	}

	/**
	 * Decisión 422: carpetas de las que se puede leer una foto, como rutas reales (sin enlaces) de las que existen: las
	 * configuradas y las dos heredadas que ya usan los respaldos por nombre.
	 */
	private List<Path> raicesDeImagenes() {
		List<Path> raices = new ArrayList<>(4);
		for (String raiz : List.of(Objects.toString(directorioImagenesLinux, ""),
				Objects.toString(directorioImagenesWindows, ""), RAIZ_HEREDADA_DOCKER, RAIZ_HEREDADA_LOCAL)) {
			if (raiz.isBlank()) {
				continue; // Path.of("") sería el directorio de trabajo.
			}
			try {
				Path real = Path.of(expandirVariablesRuta(raiz)).toRealPath();
				if (Files.isDirectory(real) && !raices.contains(real)) {
					raices.add(real);
				}
			} catch (IOException | RuntimeException ignored) {
				// Carpeta que no existe en este entorno: no aporta raíz.
			}
		}
		return raices;
	}

	/**
	 * La ruta real del fichero (sin enlaces ni {@code ..}) si es un fichero regular dentro de alguna raíz; si no,
	 * {@code null}. Cualquier fallo al resolverlo cuenta como fuera (falla cerrado).
	 */
	private static Path realDentroDeAlguna(Path fichero, List<Path> raices) {
		try {
			Path real = fichero.toRealPath();
			if (!Files.isRegularFile(real)) {
				return null;
			}
			for (Path raiz : raices) {
				if (real.startsWith(raiz) && !real.equals(raiz)) {
					return real;
				}
			}
		} catch (IOException | RuntimeException e) {
			return null;
		}
		return null;
	}

	/** Un nombre de fichero sin separadores ({@code /} ni {@code \}), que no es {@code .}/{@code ..} ni absoluto. */
	private static boolean esNombreSimple(String nombre) {
		return nombre.indexOf('/') < 0 && nombre.indexOf('\\') < 0 && !".".equals(nombre) && !"..".equals(nombre)
				&& !RutasArchivos.esRutaAbsoluta(nombre);
	}

	/** Algún segmento {@code ..} en una ruta con {@code /} o {@code \}. */
	private static boolean tieneSegmentoPadre(String ruta) {
		for (String segmento : ruta.split("[/\\\\]")) {
			if ("..".equals(segmento)) {
				return true;
			}
		}
		return false;
	}

	private String obtenerNombreImagenPrioritario(Imagen imagen) {
		if (imagen == null) {
			return null;
		}
		if (imagen.getNombre() != null && !imagen.getNombre().isBlank()) {
			return imagen.getNombre().trim();
		}
		String nombreDesdeRuta = extraerNombreDesdeRuta(imagen.getRuta());
		if (nombreDesdeRuta != null && !nombreDesdeRuta.isBlank()) {
			return nombreDesdeRuta;
		}
		return extraerNombreDesdeRuta(imagen.getUrl());
	}

	private String extraerNombreDesdeRuta(String ruta) {
		if (ruta == null || ruta.isBlank()) {
			return null;
		}
		String normalizada = ruta.replace('\\', '/').trim();
		int idx = normalizada.lastIndexOf('/');
		if (idx < 0 || idx >= normalizada.length() - 1) {
			return null;
		}
		String nombre = normalizada.substring(idx + 1);
		int queryIndex = nombre.indexOf('?');
		if (queryIndex >= 0) {
			nombre = nombre.substring(0, queryIndex);
		}
		return nombre.isBlank() ? null : nombre;
	}

	private void agregarCandidatoRuta(List<Path> candidatos, String ruta) {
		if (ruta == null || ruta.isBlank()) {
			return;
		}
		try {
			candidatos.add(Path.of(expandirVariablesRuta(ruta)));
		} catch (Exception ignored) {
			// Ignore malformed paths coming from legacy DB rows.
		}
	}

	/** La ruta relativa bajo la carpeta de imágenes configurada, sin salir de ella por {@code ..}. */
	private void agregarCandidatoRelativo(List<Path> candidatos, String ruta) {
		if (ruta == null || ruta.isBlank() || Objects.toString(directorioImagenesLinux, "").isBlank()
				|| Objects.toString(directorioImagenesWindows, "").isBlank()) {
			return;
		}
		try {
			Path base = RutasArchivos.directorioBase(directorioImagenesLinux, directorioImagenesWindows);
			candidatos.add(RutasArchivos.resolverDentroDe(base, ruta.trim()));
		} catch (RuntimeException ignored) {
			// Ruta no válida: no aporta candidata.
		}
	}

	private void agregarCandidatosDesdeBase(List<Path> candidatos, String baseDir, String nombreArchivo) {
		if (baseDir == null || baseDir.isBlank() || nombreArchivo == null || nombreArchivo.isBlank()) {
			return;
		}
		try {
			Path base = Path.of(expandirVariablesRuta(baseDir));
			candidatos.add(base.resolve(nombreArchivo));
			candidatos.add(base.resolve("alumnos").resolve(nombreArchivo));
		} catch (Exception ignored) {
			// Ignore malformed configured directories.
		}
	}

	private String expandirVariablesRuta(String ruta) {
		if (ruta == null || ruta.isBlank()) {
			return ruta;
		}
		String resultado = ruta;
		String userProfile = System.getenv("USERPROFILE");
		if (userProfile != null && !userProfile.isBlank()) {
			resultado = resultado.replace("%USERPROFILE%", userProfile);
		}
		return resultado;
	}
}
