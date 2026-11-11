package com.taemoi.project.services.impl;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.imageio.IIOException;
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
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.archivos.RutasArchivos;
import com.taemoi.project.config.FileUtils;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.exceptions.ArchivoNoValidoException;
import com.taemoi.project.services.ImagenService;
import com.taemoi.project.utils.DocumentoSecurityUtils;
import com.taemoi.project.utils.OrientacionExif;

@Service
public class ImagenServiceImpl implements ImagenService {
	private static final Logger logger = LoggerFactory.getLogger(ImagenServiceImpl.class);

	private static final int MAX_ALUMNO_WIDTH = 1280;
	private static final int MAX_ALUMNO_HEIGHT = 1280;
	private static final float ALUMNO_WEBP_QUALITY = 0.82f;
	private static final int MAX_EVENTO_WIDTH = 1920;
	private static final int MAX_EVENTO_HEIGHT = 1920;
	private static final float EVENTO_WEBP_QUALITY = 0.80f;
	private static final float JPEG_FALLBACK_QUALITY = 0.86f;
	private static final String NOMBRE_BASE_FOTO_EVENTO = "foto";
	private static final String NOMBRE_BASE_MINIATURA_FOTO_EVENTO = "miniatura";
	private static final String MENSAJE_FOTO_ILEGIBLE = "No se pudo leer la foto del evento.";
	private static final String MENSAJE_FOTO_DEMASIADO_GRANDE =
			"La foto es demasiado grande. Usa una de menos de 60 megapíxeles.";
	/** Decodificada ocupa 3 o 4 bytes por píxel: la galería sube varias a la vez, y una cabecera no pesa nada. */
	private static final long MAX_PIXELES_FOTO_EVENTO = 60_000_000L;
	private static final String NOMBRE_BASE_FOTO_SEDE = "sede";
	private static final String MENSAJE_FOTO_SEDE_ILEGIBLE = "No se pudo leer la foto de la sede.";
	private static final String MENSAJE_FOTO_SEDE_FORMATO = "La foto de la sede tiene que ser JPG, PNG o WebP.";
	/** Formatos de la foto de una sede, por el lector de ImageIO que reconoce el contenido (en minúsculas). */
	private static final Set<String> FORMATOS_FOTO_SEDE = Set.of("jpeg", "jpg", "png", "webp");
	private static final String NOMBRE_BASE_FOTO_BLOQUE = "web";
	private static final String MENSAJE_FOTO_BLOQUE_ILEGIBLE = "No se pudo leer la foto.";
	private static final String MENSAJE_FOTO_BLOQUE_FORMATO = "La foto tiene que ser JPG, PNG o WebP.";

	@Value("${app.imagenes.directorio.linux}")
	private String directorioImagenesLinux;

	@Value("${app.imagenes.directorio.windows}")
	private String directorioImagenesWindows;

	/**
	 * Carpetas, separadas por comas, de las que versiones anteriores guardaban la ruta ABSOLUTA de una imagen. Una fila
	 * con ruta absoluta solo se lee o se borra si queda dentro de la carpeta de imágenes o de una de estas. Sin valor,
	 * solo vale la carpeta de imágenes.
	 */
	@Value("${app.imagenes.directorios-heredados:}")
	private String directoriosImagenesHeredados;

	/** Menos segmentos que esto ({@code /}, {@code C:\}, {@code /tmp}) abarcaría casi todo el disco: no vale de raíz. */
	private static final int SEGMENTOS_MINIMOS_RAIZ_HEREDADA = 2;

	/** Carpetas heredadas ya leídas del valor configurado (se vuelven a leer si el valor cambia). */
	private volatile RaicesHeredadas raicesHeredadasLeidas;

	private record RaicesHeredadas(String valor, List<Path> raices) {
	}

	@Override
	public Imagen guardarImagen(MultipartFile archivo) throws IOException {
		DocumentoSecurityUtils.rechazarHeic(archivo.getOriginalFilename(), archivo.getContentType());
		String carpetaRelativa = carpetaDeEscuela(RutasArchivos.CARPETA_ALUMNOS);
		Path rutaImagenesAlumnos = RutasArchivos.resolverDentroDe(obtenerRutaImagenesBase(), carpetaRelativa);

		if (!Files.exists(rutaImagenesAlumnos)) {
			Files.createDirectories(rutaImagenesAlumnos);
		}

		BufferedImage original = leerImagen(archivo, "No se pudo leer la imagen del alumno.");

		BufferedImage redimensionada = redimensionarImagen(original, MAX_ALUMNO_WIDTH, MAX_ALUMNO_HEIGHT);
		String nombreBase = obtenerNombreBase(FileUtils.limpiarNombreArchivo(archivo.getOriginalFilename()));
		return guardarImagenProcesadaConFallback(redimensionada, rutaImagenesAlumnos, nombreBase, carpetaRelativa,
				ALUMNO_WEBP_QUALITY);
	}

	@Override
	public Imagen guardarImagenEvento(MultipartFile archivo) throws IOException {
		DocumentoSecurityUtils.rechazarHeic(archivo.getOriginalFilename(), archivo.getContentType());
		String carpetaRelativa = carpetaDeEscuela(RutasArchivos.CARPETA_EVENTOS);
		Path rutaImagenesEventos = RutasArchivos.resolverDentroDe(obtenerRutaImagenesBase(), carpetaRelativa);

		if (!Files.exists(rutaImagenesEventos)) {
			Files.createDirectories(rutaImagenesEventos);
		}

		BufferedImage original = leerImagen(archivo, "No se pudo leer la imagen del evento.");

		BufferedImage redimensionada = redimensionarParaEvento(original);
		String nombreBase = obtenerNombreBase(FileUtils.limpiarNombreArchivo(archivo.getOriginalFilename()));
		return guardarImagenProcesadaConFallback(redimensionada, rutaImagenesEventos, nombreBase, carpetaRelativa,
				EVENTO_WEBP_QUALITY);
	}

	@Override
	public FotoEventoGuardada guardarImagenFotoEvento(MultipartFile archivo) throws IOException {
		DocumentoSecurityUtils.rechazarHeic(archivo.getOriginalFilename(), archivo.getContentType());
		String carpetaRelativa = carpetaDeEscuela(RutasArchivos.CARPETA_FOTOS_EVENTOS);
		Path directorio = RutasArchivos.resolverDentroDe(obtenerRutaImagenesBase(), carpetaRelativa);

		rechazarFotoDemasiadoGrande(archivo);
		Files.createDirectories(directorio);

		BufferedImage reducida = orientarYReducirFoto(archivo);
		Imagen foto = guardarImagenProcesadaConFallback(reducida, directorio, NOMBRE_BASE_FOTO_EVENTO, carpetaRelativa,
				EVENTO_WEBP_QUALITY);
		try {
			return new FotoEventoGuardada(foto, guardarMiniaturaFotoEvento(reducida, directorio, carpetaRelativa));
		} catch (IOException | RuntimeException | Error e) {
			borrarSinFallar(foto);
			throw e;
		}
	}

	@Override
	public Imagen guardarFotoSede(MultipartFile archivo) throws IOException {
		return guardarFotoPublicaDeLaWeb(archivo, RutasArchivos.CARPETA_WEB_SEDES, NOMBRE_BASE_FOTO_SEDE,
				MENSAJE_FOTO_SEDE_ILEGIBLE, MENSAJE_FOTO_SEDE_FORMATO);
	}

	@Override
	public Imagen guardarFotoBloqueWeb(MultipartFile archivo) throws IOException {
		return guardarFotoPublicaDeLaWeb(archivo, RutasArchivos.CARPETA_WEB_BLOQUES, NOMBRE_BASE_FOTO_BLOQUE,
				MENSAJE_FOTO_BLOQUE_ILEGIBLE, MENSAJE_FOTO_BLOQUE_FORMATO);
	}

	/** Foto de la web en una carpeta PÚBLICA de la escuela activa: la misma conversión para sedes y bloques. */
	private Imagen guardarFotoPublicaDeLaWeb(MultipartFile archivo, String categoria, String nombreBase,
			String mensajeIlegible, String mensajeFormato) throws IOException {
		DocumentoSecurityUtils.rechazarHeic(archivo.getOriginalFilename(), archivo.getContentType());
		String carpetaRelativa = carpetaDeEscuela(categoria);
		Path directorio = RutasArchivos.resolverDentroDe(obtenerRutaImagenesBase(), carpetaRelativa);

		comprobarCabecera(archivo, FORMATOS_FOTO_SEDE, mensajeIlegible, mensajeFormato);
		Files.createDirectories(directorio);

		BufferedImage original = leerImagen(archivo, mensajeIlegible);
		BufferedImage reducida = redimensionarParaEvento(OrientacionExif.aplicar(original, leerOrientacion(archivo)));
		return guardarImagenProcesadaConFallback(reducida, directorio, nombreBase, carpetaRelativa,
				EVENTO_WEBP_QUALITY);
	}

	/** Separado para poder simular su fallo en los tests. */
	protected Imagen guardarMiniaturaFotoEvento(BufferedImage foto, Path directorio, String carpetaRelativa)
			throws IOException {
		BufferedImage miniatura = redimensionarImagen(foto, LADO_MINIATURA_FOTO_EVENTO, LADO_MINIATURA_FOTO_EVENTO);
		return guardarImagenProcesadaConFallback(miniatura, directorio, NOMBRE_BASE_MINIATURA_FOTO_EVENTO, carpetaRelativa,
				EVENTO_WEBP_QUALITY);
	}

	/** Limpieza de un fallo que ya se está propagando: no debe taparlo. Sin la ruta en el log. */
	private void borrarSinFallar(Imagen imagen) {
		try {
			eliminarImagenDeSistema(imagen);
		} catch (RuntimeException e) {
			logger.warn("No se pudo borrar una foto de evento tras fallar su miniatura: {}", e.getClass().getSimpleName());
		}
	}

	/** En su propio método para que los píxeles a tamaño completo se puedan liberar antes de codificar la foto. */
	private BufferedImage orientarYReducirFoto(MultipartFile archivo) throws IOException {
		BufferedImage original = leerImagen(archivo, MENSAJE_FOTO_ILEGIBLE);
		return redimensionarParaEvento(OrientacionExif.aplicar(original, leerOrientacion(archivo)));
	}

	private static int leerOrientacion(MultipartFile archivo) {
		try (InputStream entrada = archivo.getInputStream()) {
			return OrientacionExif.leer(entrada);
		} catch (IOException e) {
			return OrientacionExif.NORMAL;
		}
	}

	/**
	 * Mira el tamaño en la cabecera, sin decodificar: una imagen pequeña en bytes puede ocupar gigas en memoria. Si la
	 * cabecera no se entiende se rechaza aquí, porque de lo que no se conoce el tamaño no se decodifica nada.
	 */
	private static void rechazarFotoDemasiadoGrande(MultipartFile archivo) throws IOException {
		comprobarCabecera(archivo, null, MENSAJE_FOTO_ILEGIBLE, null);
	}

	/**
	 * Lo de {@link #rechazarFotoDemasiadoGrande} y, si {@code formatos} no es {@code null}, que el formato que reconoce
	 * ImageIO por el contenido esté entre ellos (si no, {@code mensajeFormato}).
	 */
	private static void comprobarCabecera(MultipartFile archivo, Set<String> formatos, String mensajeIlegible,
			String mensajeFormato) throws IOException {
		long pixeles;
		try (InputStream entrada = archivo.getInputStream();
				ImageInputStream cabecera = ImageIO.createImageInputStream(entrada)) {
			Iterator<ImageReader> lectores = ImageIO.getImageReaders(cabecera);
			if (!lectores.hasNext()) {
				throw new ArchivoNoValidoException(mensajeIlegible);
			}
			ImageReader lector = lectores.next();
			try {
				if (formatos != null && !formatos.contains(formato(lector))) {
					throw new ArchivoNoValidoException(mensajeFormato);
				}
				lector.setInput(cabecera, true, true);
				pixeles = (long) lector.getWidth(0) * lector.getHeight(0);
			} finally {
				lector.dispose();
			}
		} catch (EOFException | IndexOutOfBoundsException e) {
			// IndexOutOfBounds: cabecera válida sin ningún fotograma, como un GIF vacío.
			throw new ArchivoNoValidoException(mensajeIlegible);
		} catch (IIOException e) {
			if (esErrorDeEntradaSalidaDelServidor(e)) {
				throw e;
			}
			throw new ArchivoNoValidoException(mensajeIlegible);
		}
		if (pixeles > MAX_PIXELES_FOTO_EVENTO) {
			throw new ArchivoNoValidoException(MENSAJE_FOTO_DEMASIADO_GRANDE);
		}
	}

	/** Nombre del formato del lector en minúsculas, o vacío si no lo dice. */
	private static String formato(ImageReader lector) {
		try {
			String nombre = lector.getFormatName();
			return nombre == null ? "" : nombre.toLowerCase(Locale.ROOT);
		} catch (IOException e) {
			return "";
		}
	}

	@Override
	public Imagen guardarLogoEscuela(Long organizacionId, byte[] png) throws IOException {
		if (png == null || png.length == 0) {
			throw new IllegalArgumentException("El logo está vacío.");
		}
		String carpetaRelativa = RutasArchivos.carpetaOrganizacion(organizacionId) + "/" + RutasArchivos.CARPETA_LOGO;
		Path directorio = RutasArchivos.resolverDentroDe(obtenerRutaImagenesBase(), carpetaRelativa);
		Files.createDirectories(directorio);
		String nombre = UUID.randomUUID() + "_logo.png";
		Path fichero = RutasArchivos.resolverDentroDe(directorio, nombre);
		Files.write(fichero, png, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		return crearImagen(nombre, "image/png", carpetaRelativa);
	}

	@Override
	public void eliminarImagenDeSistema(Imagen imagen) {
		if (imagen == null || imagen.getRuta() == null) {
			throw new IllegalArgumentException("La imagen o su ruta no es valida.");
		}

		Path rutaArchivo = resolverRutaArchivo(imagen);
		if (rutaArchivo == null) {
			// Ruta fuera de las carpetas de imágenes o no válida: no es un fichero nuestro y no se toca. Quien borra (el
			// alumno, su foto, el evento) sigue adelante; resolverRutaArchivo ya ha avisado con el id.
			logger.warn("No se borra ningún fichero de la imagen {}: su ruta no es válida", imagen.getId());
			return;
		}
		try {
			if (Files.exists(rutaArchivo)) {
				Files.delete(rutaArchivo);
			}
		} catch (IOException e) {
			throw new RuntimeException("Error al eliminar el archivo de imagen: " + rutaArchivo, e);
		}
	}

	@Override
	public Path resolverRutaArchivo(Imagen imagen) {
		if (imagen == null || imagen.getRuta() == null || imagen.getRuta().isBlank()) {
			return null;
		}
		String ruta = imagen.getRuta().trim();
		try {
			Path base = obtenerRutaImagenesBase();
			Path resuelta;
			List<Path> raices;
			if (RutasArchivos.esRutaAbsoluta(ruta)) {
				// Fila heredada: ruta absoluta guardada por versiones anteriores. Solo dentro de una carpeta permitida.
				raices = raicesPermitidas(base);
				resuelta = Path.of(ruta).toAbsolutePath().normalize();
				if (raices.stream().noneMatch(raiz -> dentroDe(resuelta, raiz))) {
					logger.warn("Ruta absoluta de imagen fuera de las carpetas de imágenes; se ignora (imagen {})",
							imagen.getId());
					return null;
				}
			} else {
				raices = List.of(base);
				resuelta = RutasArchivos.resolverDentroDe(base, ruta);
			}
			Path segura = rutaSeguraDentroDeAlguna(resuelta, raices);
			if (segura == null) {
				logger.warn("Fichero de imagen que sale de las carpetas de imágenes por un enlace; se ignora (imagen {})",
						imagen.getId());
			}
			return segura;
		} catch (RuntimeException e) {
			// Sin el mensaje: el de una ruta mal formada (InvalidPathException) lleva la ruta.
			logger.warn("Ruta de imagen no valida (imagen {}): {}", imagen.getId(), e.getClass().getSimpleName());
			return null;
		}
	}

	/** La carpeta de imágenes y las heredadas configuradas, absolutas y normalizadas, sin repetir. */
	private List<Path> raicesPermitidas(Path base) {
		List<Path> raices = new ArrayList<>();
		raices.add(base);
		for (Path raiz : raicesHeredadas()) {
			if (!raices.contains(raiz)) {
				raices.add(raiz);
			}
		}
		return raices;
	}

	/** Las heredadas del valor configurado; se leen (y avisan de las que se ignoran) una vez por valor. */
	private List<Path> raicesHeredadas() {
		String valor = directoriosImagenesHeredados;
		RaicesHeredadas leidas = raicesHeredadasLeidas;
		if (leidas == null || !Objects.equals(leidas.valor(), valor)) {
			leidas = new RaicesHeredadas(valor, leerRaicesHeredadas(valor));
			raicesHeredadasLeidas = leidas;
		}
		return leidas.raices();
	}

	private static List<Path> leerRaicesHeredadas(String lista) {
		List<Path> raices = new ArrayList<>();
		if (lista == null || lista.isBlank()) {
			return List.of();
		}
		for (String valor : lista.split(",")) {
			String directorio = expandirPerfilUsuario(valor.trim());
			if (directorio.isEmpty()) {
				continue;
			}
			try {
				Path raiz = Path.of(directorio);
				// Una relativa dependería del directorio de trabajo: no se acepta como raíz.
				if (raiz.getRoot() == null) {
					continue;
				}
				Path normalizada = raiz.toAbsolutePath().normalize();
				if (normalizada.getNameCount() < SEGMENTOS_MINIMOS_RAIZ_HEREDADA) {
					// «/» o «C:\» harían válida cualquier ruta absoluta del disco.
					logger.warn("Carpeta heredada de imágenes demasiado amplia ({}): se ignora; ponga la carpeta completa",
							normalizada);
					continue;
				}
				if (!raices.contains(normalizada)) {
					raices.add(normalizada);
				}
			} catch (RuntimeException e) {
				// Valor que no es una ruta en este sistema: no aporta raíz.
			}
		}
		return List.copyOf(raices);
	}

	/** {@code %USERPROFILE%} como en la carpeta de imágenes de Windows; si no hay perfil, el valor no se usa. */
	private static String expandirPerfilUsuario(String directorio) {
		if (!directorio.contains("%USERPROFILE%")) {
			return directorio;
		}
		String perfil = System.getenv("USERPROFILE");
		return perfil == null || perfil.isBlank() ? "" : directorio.replace("%USERPROFILE%", perfil);
	}

	/** Por segmentos (no por texto: {@code imagenes-otra} no está en {@code imagenes}) y sin ser la propia raíz. */
	private static boolean dentroDe(Path ruta, Path raiz) {
		return ruta.startsWith(raiz) && !ruta.equals(raiz);
	}

	/**
	 * La ruta que se usa para leer o borrar. Si el fichero existe, su ruta real (sin enlaces), y solo si sigue dentro de
	 * alguna raíz, también real: quien la usa actúa sobre el fichero comprobado y no sobre una ruta con enlaces que
	 * podrían cambiar entre la comprobación y el uso. Si no existe no hay nada que leer ni borrar y vale la ruta sin
	 * enlaces. {@code null} si queda fuera; cualquier fallo cuenta como fuera.
	 */
	private static Path rutaSeguraDentroDeAlguna(Path ruta, List<Path> raices) {
		if (!Files.exists(ruta, LinkOption.NOFOLLOW_LINKS)) {
			return ruta;
		}
		Path real;
		try {
			real = ruta.toRealPath();
		} catch (IOException | RuntimeException e) {
			return null;
		}
		for (Path raiz : raices) {
			try {
				if (Files.isDirectory(raiz) && dentroDe(real, raiz.toRealPath())) {
					return real;
				}
			} catch (IOException | RuntimeException e) {
				// Raíz que no se puede resolver: no aporta.
			}
		}
		return null;
	}

	/** {@code org-<id>/<categoria>} de la escuela activa; falla cerrado si no hay ninguna. */
	private static String carpetaDeEscuela(String categoria) {
		return RutasArchivos.carpetaOrganizacion(RutasArchivos.organizacionActiva()) + "/" + categoria;
	}

	private Path obtenerRutaImagenesBase() {
		String os = System.getProperty("os.name").toLowerCase();
		String directorioImagenes;

		if (os.contains("win")) {
			String userProfile = System.getenv("USERPROFILE");
			directorioImagenes = directorioImagenesWindows.replace("%USERPROFILE%", userProfile);
		} else if (os.contains("nix") || os.contains("nux") || os.contains("mac")) {
			directorioImagenes = directorioImagenesLinux;
		} else {
			throw new IllegalStateException("Sistema operativo no soportado: " + os);
		}

		return Path.of(directorioImagenes).toAbsolutePath().normalize();
	}

	/**
	 * Decodifica la imagen subida. Si el contenido no es una imagen legible (otro tipo de fichero, imagen dañada,
	 * truncada o formato sin lector), es un fallo de validación: {@link ArchivoNoValidoException} (400). Un fallo de
	 * E/S del servidor (no se puede leer el temporal, ni crear la caché de ImageIO) sigue siendo {@link IOException}.
	 */
	private BufferedImage leerImagen(MultipartFile archivo, String mensajeIlegible) throws IOException {
		BufferedImage original;
		try {
			original = decodificar(archivo.getInputStream());
		} catch (IIOException e) {
			if (esErrorDeEntradaSalidaDelServidor(e)) {
				throw e;
			}
			throw new ArchivoNoValidoException(mensajeIlegible);
		}
		if (original == null) {
			throw new ArchivoNoValidoException(mensajeIlegible);
		}
		return original;
	}

	/**
	 * ImageIO envuelve en {@link IIOException} tanto el contenido dañado (sin causa, o con otra {@code IIOException}
	 * o un {@link EOFException} si el fichero está truncado) como los fallos de E/S de verdad (causa
	 * {@link IOException} de otro tipo, p. ej. al crear su fichero de caché).
	 */
	private static boolean esErrorDeEntradaSalidaDelServidor(IIOException e) {
		Throwable causa = e.getCause();
		return causa instanceof IOException && !(causa instanceof IIOException) && !(causa instanceof EOFException);
	}

	/** Decodificación con ImageIO; separada para poder simular sus fallos en los tests. */
	protected BufferedImage decodificar(InputStream entrada) throws IOException {
		return ImageIO.read(entrada);
	}

	private BufferedImage redimensionarParaEvento(BufferedImage original) {
		return redimensionarImagen(original, MAX_EVENTO_WIDTH, MAX_EVENTO_HEIGHT);
	}

	private BufferedImage redimensionarImagen(BufferedImage original, int maxWidth, int maxHeight) {
		int width = Math.max(1, original.getWidth());
		int height = Math.max(1, original.getHeight());

		double scaleWidth = (double) maxWidth / width;
		double scaleHeight = (double) maxHeight / height;
		double scale = Math.min(1.0, Math.min(scaleWidth, scaleHeight));

		int nuevoAncho = Math.max(1, (int) Math.round(width * scale));
		int nuevoAlto = Math.max(1, (int) Math.round(height * scale));

		BufferedImage salida = new BufferedImage(nuevoAncho, nuevoAlto, BufferedImage.TYPE_3BYTE_BGR);
		Graphics2D graphics = salida.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.drawImage(original, 0, 0, nuevoAncho, nuevoAlto, null);
		graphics.dispose();

		return salida;
	}

	/** Separado para poder simular en los tests un codificador que falla a mitad de escritura. */
	protected void codificar(ImageWriter writer, IIOImage imagen, ImageWriteParam param) throws IOException {
		writer.write(null, imagen, param);
	}

	/** Separado para poder simular su fallo en los tests. */
	protected void borrarFicheroParcial(Path fichero) throws IOException {
		Files.deleteIfExists(fichero);
	}

	private Path guardarComoWebp(BufferedImage imagen, Path directorioDestino, String nombreArchivo, float quality)
			throws IOException {
		Path directorioSeguro = directorioDestino.toAbsolutePath().normalize();
		Path rutaArchivo = directorioSeguro.resolve(nombreArchivo).normalize().toAbsolutePath();
		if (!rutaArchivo.startsWith(directorioSeguro) || !directorioSeguro.equals(rutaArchivo.getParent())) {
			throw new IllegalArgumentException("Ruta de imagen fuera del directorio permitido.");
		}

		Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
		if (!writers.hasNext()) {
			writers = ImageIO.getImageWritersBySuffix("webp");
		}
		if (!writers.hasNext()) {
			throw new IOException("No hay codificador WebP disponible.");
		}

		ImageWriter writer = writers.next();
		// Dos recursos: cerrar el ImageOutputStream no cierra el fichero, y borrarlo abierto no liberaría su espacio.
		try (OutputStream fichero = Files.newOutputStream(rutaArchivo, StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING);
				ImageOutputStream outputStream = ImageIO.createImageOutputStream(fichero)) {
			writer.setOutput(outputStream);
			ImageWriteParam param = writer.getDefaultWriteParam();
			if (param.canWriteCompressed()) {
				configurarCompresionWebp(param, quality);
			}
			codificar(writer, new IIOImage(imagen, null, null), param);
		} finally {
			writer.dispose();
		}
		return rutaArchivo;
	}

	private Path guardarComoJpeg(BufferedImage imagen, Path directorioDestino, String nombreArchivo, float quality)
			throws IOException {
		Path directorioSeguro = directorioDestino.toAbsolutePath().normalize();
		Path rutaArchivo = directorioSeguro.resolve(nombreArchivo).normalize().toAbsolutePath();
		if (!rutaArchivo.startsWith(directorioSeguro) || !directorioSeguro.equals(rutaArchivo.getParent())) {
			throw new IllegalArgumentException("Ruta de imagen fuera del directorio permitido.");
		}

		Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
		if (!writers.hasNext()) {
			writers = ImageIO.getImageWritersByMIMEType("image/jpeg");
		}
		if (!writers.hasNext()) {
			throw new IOException("No hay codificador JPEG disponible.");
		}

		ImageWriter writer = writers.next();
		// Dos recursos: cerrar el ImageOutputStream no cierra el fichero, y borrarlo abierto no liberaría su espacio.
		try (OutputStream fichero = Files.newOutputStream(rutaArchivo, StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING);
				ImageOutputStream outputStream = ImageIO.createImageOutputStream(fichero)) {
			writer.setOutput(outputStream);
			ImageWriteParam param = writer.getDefaultWriteParam();
			if (param.canWriteCompressed()) {
				param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
				float qualitySegura = Math.max(0.1f, Math.min(1.0f, quality));
				param.setCompressionQuality(qualitySegura);
			}
			codificar(writer, new IIOImage(imagen, null, null), param);
		} finally {
			writer.dispose();
		}
		return rutaArchivo;
	}

	private Imagen guardarImagenProcesadaConFallback(BufferedImage imagen, Path directorioDestino, String nombreBase,
			String carpetaPublica, float webpQuality) throws IOException {
		String nombreBaseArchivo = UUID.randomUUID() + "_" + nombreBase;
		String nombreWebp = nombreBaseArchivo + ".webp";
		try {
			guardarComoWebp(imagen, directorioDestino, nombreWebp, webpQuality);
			return crearImagen(nombreWebp, "image/webp", carpetaPublica);
		} catch (Exception e) {
			borrarParcialSinFallar(directorioDestino, nombreWebp);
			// La clase y no el mensaje: puede llevar la ruta del fichero.
			logger.warn("Fallo al guardar imagen en WebP, se usara fallback JPEG. Carpeta: {}, nombreBase: {}. Causa: {}",
					carpetaPublica, nombreBase, e.getClass().getSimpleName());
			return guardarJpegDeReserva(imagen, directorioDestino, nombreBaseArchivo + ".jpg", carpetaPublica);
		} catch (Error e) {
			borrarParcialSinFallar(directorioDestino, nombreWebp);
			throw e;
		}
	}

	private Imagen guardarJpegDeReserva(BufferedImage imagen, Path directorioDestino, String nombreJpeg,
			String carpetaPublica) throws IOException {
		try {
			guardarComoJpeg(imagen, directorioDestino, nombreJpeg, JPEG_FALLBACK_QUALITY);
			return crearImagen(nombreJpeg, "image/jpeg", carpetaPublica);
		} catch (Exception | Error e) {
			borrarParcialSinFallar(directorioDestino, nombreJpeg);
			throw e;
		}
	}

	/**
	 * El fichero se crea antes de codificar: un fallo deja uno a medias al que ninguna fila apunta. Limpieza de un fallo
	 * que ya se está propagando, así que no debe taparlo. Sin la ruta en el log.
	 */
	private void borrarParcialSinFallar(Path directorio, String nombreArchivo) {
		try {
			borrarFicheroParcial(RutasArchivos.resolverDentroDe(directorio, nombreArchivo));
		} catch (Exception | Error e) {
			logger.warn("No se pudo borrar un fichero de imagen a medio escribir: {}", e.getClass().getSimpleName());
		}
	}

	/**
	 * Solo se guarda la ruta RELATIVA a la raíz de imágenes ({@code org-<id>/<categoria>/<fichero>}) y una
	 * URL sin host ({@code /imagenes/…}); la URL absoluta se compone al leer con {@code app.base.url}.
	 */
	private static Imagen crearImagen(String nombreArchivo, String tipo, String carpetaRelativa) {
		String rutaRelativa = carpetaRelativa + "/" + nombreArchivo;
		return new Imagen(nombreArchivo, tipo, "/imagenes/" + rutaRelativa, rutaRelativa);
	}

	private void configurarCompresionWebp(ImageWriteParam param, float quality) throws IOException {
		param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);

		boolean tipoCompresionEstablecido = false;
		String[] compressionTypes = param.getCompressionTypes();
		if (compressionTypes != null && compressionTypes.length > 0) {
			String preferido = seleccionarTipoCompresion(compressionTypes);
			tipoCompresionEstablecido = intentarSetCompressionType(param, preferido);

			if (!tipoCompresionEstablecido) {
				for (String type : compressionTypes) {
					if (intentarSetCompressionType(param, type)) {
						tipoCompresionEstablecido = true;
						break;
					}
				}
			}
		}

		if (!tipoCompresionEstablecido) {
			tipoCompresionEstablecido = intentarSetCompressionType(param, "Lossy")
					|| intentarSetCompressionType(param, "lossy");
		}

		try {
			param.setCompressionQuality(quality);
		} catch (RuntimeException e) {
			throw new IOException("No se pudo configurar la compresion WebP.", e);
		}
	}

	private boolean intentarSetCompressionType(ImageWriteParam param, String type) {
		if (type == null || type.isBlank()) {
			return false;
		}
		try {
			param.setCompressionType(type);
			return true;
		} catch (RuntimeException e) {
			return false;
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

	private String obtenerNombreBase(String nombreArchivo) {
		if (nombreArchivo == null || nombreArchivo.isBlank()) {
			return "evento";
		}
		int ultimoPunto = nombreArchivo.lastIndexOf('.');
		String base = ultimoPunto > 0 ? nombreArchivo.substring(0, ultimoPunto) : nombreArchivo;
		base = base.replaceAll("[^a-zA-Z0-9_-]+", "_").replaceAll("_+", "_");
		if (base.isBlank()) {
			return "evento";
		}
		return base;
	}
}
