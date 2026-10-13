package com.taemoi.project.services.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.Locale;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.repositories.ConsentimientoRepository;
import com.taemoi.project.exceptions.DocumentoReferenciadoException;
import com.taemoi.project.archivos.RutasArchivos;
import com.taemoi.project.config.FileUtils;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Documento;
import com.taemoi.project.entities.Evento;
import com.taemoi.project.repositories.DocumentoRepository;
import com.taemoi.project.services.DocumentoService;
import com.taemoi.project.utils.DocumentoSecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class DocumentoServiceImpl implements DocumentoService {

	private static final Logger logger = LoggerFactory.getLogger(DocumentoServiceImpl.class);

	@Value("${app.documentos.directorio.linux}")
	private String directorioDocumentosLinux;

	@Value("${app.documentos.directorio.windows}")
	private String directorioDocumentosWindows;

	@Autowired
	private DocumentoRepository documentoRepository;

	@Autowired
	private ConsentimientoRepository consentimientoRepository;

	@Override
	public Documento guardarDocumento(Alumno alumno, MultipartFile archivo) throws IOException {
		if (archivo == null || archivo.isEmpty()) {
			throw new IllegalArgumentException("No se ha enviado ningun documento.");
		}
		if (alumno == null || alumno.getId() == null) {
			throw new IllegalArgumentException("El alumno debe existir antes de adjuntarle documentos.");
		}
		// Carpeta por escuela y por id de alumno (el expediente se repite entre escuelas).
		String carpetaRelativa = carpetaDeEscuela(RutasArchivos.CARPETA_ALUMNOS) + "/" + alumno.getId();
		return guardarEnCarpeta(carpetaRelativa, archivo, documento -> documento.setAlumno(alumno));
	}

	@Override
	public Documento guardarDocumentoEvento(Evento evento, MultipartFile archivo) throws IOException {
		if (archivo == null || archivo.isEmpty()) {
			throw new IllegalArgumentException("No se ha enviado ningun documento.");
		}
		if (evento == null || evento.getId() == null) {
			throw new IllegalArgumentException("El evento debe existir antes de adjuntarle documentos.");
		}
		String carpetaRelativa = carpetaDeEscuela(RutasArchivos.CARPETA_EVENTOS) + "/" + evento.getId();
		return guardarEnCarpeta(carpetaRelativa, archivo, documento -> documento.setEvento(evento));
	}

	/**
	 * Guarda el fichero en {@code <raiz>/<carpetaRelativa>/} y persiste SOLO la ruta relativa
	 * ({@code org-<id>/…}) y una URL sin host ({@code /documentos/…}); la absoluta se compone al leer.
	 */
	private Documento guardarEnCarpeta(String carpetaRelativa, MultipartFile archivo,
			java.util.function.Consumer<Documento> vincular) throws IOException {
		String nombreOriginalLimpio = FileUtils.limpiarNombreArchivo(archivo.getOriginalFilename());
		String mimeTypeSeguro = DocumentoSecurityUtils.detectarMimePermitido(nombreOriginalLimpio, archivo.getContentType());

		Path rutaBaseDocumentos = obtenerRutaBaseDocumentos();
		Path carpeta = RutasArchivos.resolverDentroDe(rutaBaseDocumentos, carpetaRelativa);
		Path rutaArchivoFinal = carpeta.resolve(nombreOriginalLimpio).normalize().toAbsolutePath();
		if (!rutaArchivoFinal.startsWith(carpeta) || !carpeta.equals(rutaArchivoFinal.getParent())
				|| !rutaArchivoFinal.startsWith(rutaBaseDocumentos)) {
			throw new IllegalArgumentException("Ruta de documento fuera del directorio permitido.");
		}
		Files.createDirectories(carpeta);
		Files.copy(archivo.getInputStream(), rutaArchivoFinal, StandardCopyOption.REPLACE_EXISTING);

		String rutaRelativa = carpetaRelativa + "/" + nombreOriginalLimpio;
		Documento documento = new Documento();
		documento.setNombre(nombreOriginalLimpio);
		documento.setTipo(mimeTypeSeguro);
		documento.setUrl("/documentos/" + rutaRelativa);
		documento.setRuta(rutaRelativa);
		vincular.accept(documento);

		return documentoRepository.save(documento);
	}

	/** {@code org-<id>/<categoria>} de la escuela activa; falla cerrado si no hay ninguna. */
	private static String carpetaDeEscuela(String categoria) {
		return RutasArchivos.carpetaOrganizacion(RutasArchivos.organizacionActiva()) + "/" + categoria;
	}

	@Override
	public void eliminarDocumento(Documento documento) {
		// Antes de tocar el disco: la prueba de un consentimiento no se borra.
		if (documento != null && documento.getId() != null
				&& consentimientoRepository.existsByDocumentoId(documento.getId())) {
			throw new DocumentoReferenciadoException("El documento es la prueba de un consentimiento registrado "
					+ "y no se puede eliminar.");
		}
		if (documento != null && documento.getRuta() != null) {
			Path rutaArchivo = resolveStoredPath(documento.getRuta());

			try {
				rutaArchivo = resolvePathCaseInsensitive(rutaArchivo);
				if (Files.exists(rutaArchivo)) {
					Files.delete(rutaArchivo);
				}
			} catch (IOException e) {
				throw new RuntimeException("Error al eliminar el documento: " + rutaArchivo, e);
			}

			documentoRepository.delete(documento);

			Alumno alumno = documento.getAlumno();
			if (alumno != null) {
				List<Documento> documentosRestantes = documentoRepository.findByAlumnoId(alumno.getId());
				if (documentosRestantes.isEmpty()) {
					Path carpetaAlumno = rutaArchivo.getParent();
					limpiarCarpetaVacia(carpetaAlumno, "alumno");
				}
			}

			Evento evento = documento.getEvento();
			if (evento != null) {
				List<Documento> documentosRestantes = documentoRepository.findByEventoId(evento.getId());
				if (documentosRestantes.isEmpty()) {
					Path carpetaEvento = rutaArchivo.getParent();
					limpiarCarpetaVacia(carpetaEvento, "evento");
				}
			}
		}
	}

	private void limpiarCarpetaVacia(Path carpeta, String tipo) {
		try {
			if (Files.isDirectory(carpeta)) {
				try (Stream<Path> filesInDir = Files.list(carpeta)) {
					boolean estaVacia = filesInDir.findAny().isEmpty();
					if (estaVacia) {
						Files.delete(carpeta);
					}
				}
			}
		} catch (IOException e) {
			throw new RuntimeException("Error al eliminar la carpeta del " + tipo + ": " + carpeta, e);
		}
	}

	@Override
	public Documento obtenerDocumentoPorId(Long documentoId) {
		return documentoRepository.findById(documentoId)
				.orElseThrow(() -> new RuntimeException("Documento no encontrado: " + documentoId));
	}

	@Override
	public Path resolverRutaArchivo(Documento documento) {
		if (documento == null) {
			throw new IllegalArgumentException("El documento es obligatorio.");
		}
		try {
			return resolvePathCaseInsensitive(resolveStoredPath(documento.getRuta()));
		} catch (IOException e) {
			throw new IllegalStateException("No se pudo resolver la ruta del documento: " + documento.getId(), e);
		}
	}

	@Override
	public Resource obtenerRecursoDocumento(Documento documento) {
		try {
			Path rutaArchivo = resolverRutaArchivo(documento);
			if (!Files.exists(rutaArchivo)) {
				throw new RuntimeException("El archivo no existe en el sistema de archivos: " + rutaArchivo);
			}
			return new UrlResource(rutaArchivo.toUri());
		} catch (Exception e) {
			throw new RuntimeException("No se pudo cargar el documento: " + documento.getNombre(), e);
		}
	}

	private Path resolveStoredPath(String rutaDocumento) {
		if (rutaDocumento == null || rutaDocumento.isBlank()) {
			throw new IllegalArgumentException("La ruta del documento es obligatoria.");
		}

		Path rutaBaseDocumentos = obtenerRutaBaseDocumentos();
		String rutaDocumentoNormalizada = normalizarSeparadores(rutaDocumento.trim());
		String rutaRelativaLegacy = extraerRutaRelativaLegacy(rutaDocumentoNormalizada);
		Path rutaNormalizada;

		if (rutaRelativaLegacy != null) {
			rutaNormalizada = rutaBaseDocumentos.resolve(rutaRelativaLegacy).normalize();
		} else {
			Path rutaDocumentoPath = Path.of(rutaDocumentoNormalizada);

			if (rutaDocumentoPath.isAbsolute() || rutaDocumentoNormalizada.matches("^[A-Za-z]:.*")) {
				logger.warn("Document path stored as absolute path (deprecated): {}", rutaDocumento);
				if (rutaDocumentoNormalizada.startsWith("/opt/taemoi/static_resources/documentos/")) {
					String relativePart = rutaDocumentoNormalizada.substring("/opt/taemoi/static_resources/documentos/".length());
					rutaNormalizada = rutaBaseDocumentos.resolve(relativePart).normalize();
				} else {
					rutaNormalizada = rutaDocumentoPath.toAbsolutePath().normalize();
				}
			} else {
				rutaNormalizada = rutaBaseDocumentos.resolve(rutaDocumentoPath).normalize();
			}
		}

		// Los recursos heredados (sin prefijo de escuela) pueden haber cambiado de carpeta; los nuevos no.
		if (RutasArchivos.organizacionDeRuta(rutaDocumentoNormalizada) == null) {
			rutaNormalizada = resolveLegacyFolderByPrefix(rutaBaseDocumentos, rutaNormalizada);
		}

		if (!rutaNormalizada.startsWith(rutaBaseDocumentos)) {
			throw new IllegalArgumentException("Ruta de documento fuera del directorio permitido.");
		}

		return rutaNormalizada;
	}

	private String normalizarSeparadores(String rutaDocumento) {
		return rutaDocumento.replace('\\', '/');
	}

	private String extraerRutaRelativaLegacy(String rutaDocumentoNormalizada) {
		String rutaLower = rutaDocumentoNormalizada.toLowerCase();
		String[] marcadores = { "documentos_alumnos_moiskimdo/", "documentos_eventos/" };

		for (String marcador : marcadores) {
			int idx = rutaLower.indexOf(marcador);
			if (idx >= 0) {
				return rutaDocumentoNormalizada.substring(idx);
			}
		}

		return null;
	}

	private Path resolveLegacyFolderByPrefix(Path rutaBaseDocumentos, Path rutaNormalizada) {
		try {
			if (!rutaNormalizada.startsWith(rutaBaseDocumentos)) {
				return rutaNormalizada;
			}

			Path carpetaEsperada = rutaNormalizada.getParent();
			if (carpetaEsperada == null || Files.exists(carpetaEsperada)) {
				return rutaNormalizada;
			}

			Path relativa = rutaBaseDocumentos.relativize(rutaNormalizada);
			if (relativa.getNameCount() < 3) {
				return rutaNormalizada;
			}

			Path directorioCategoria = rutaBaseDocumentos.resolve(relativa.getName(0).toString());
			if (!Files.isDirectory(directorioCategoria)) {
				return rutaNormalizada;
			}

			String carpetaLegacy = relativa.getName(1).toString();
			String prefijo = extraerPrefijoCarpetaLegacy(carpetaLegacy);
			if (prefijo == null) {
				return rutaNormalizada;
			}

			List<Path> candidatas;
			try (Stream<Path> hijos = Files.list(directorioCategoria)) {
				candidatas = hijos
						.filter(Files::isDirectory)
						.filter(path -> path.getFileName().toString().startsWith(prefijo + "_"))
						.toList();
			}

			if (candidatas.size() != 1) {
				return rutaNormalizada;
			}

			Path relativaAjustada = Path.of(relativa.getName(0).toString())
					.resolve(candidatas.get(0).getFileName().toString());
			for (int i = 2; i < relativa.getNameCount(); i++) {
				relativaAjustada = relativaAjustada.resolve(relativa.getName(i).toString());
			}

			Path rutaAjustada = rutaBaseDocumentos.resolve(relativaAjustada).normalize();
			logger.warn("Resolved legacy folder by prefix fallback: {} -> {}", rutaNormalizada, rutaAjustada);
			return rutaAjustada;
		} catch (IOException e) {
			logger.warn("Could not resolve legacy folder by prefix for {}: {}", rutaNormalizada, e.getMessage());
			return rutaNormalizada;
		}
	}

	private String extraerPrefijoCarpetaLegacy(String carpetaLegacy) {
		if (carpetaLegacy == null || carpetaLegacy.isBlank()) {
			return null;
		}

		int idx = carpetaLegacy.indexOf('_');
		if (idx <= 0) {
			return null;
		}

		return carpetaLegacy.substring(0, idx);
	}

	private Path obtenerRutaBaseDocumentos() {
		String os = System.getProperty("os.name").toLowerCase();
		String directorioDocumentos;

		if (os.contains("win")) {
			String userProfile = System.getenv("USERPROFILE");
			if (userProfile == null || userProfile.isBlank()) {
				userProfile = System.getProperty("user.home");
			}
			directorioDocumentos = directorioDocumentosWindows.replace("%USERPROFILE%", userProfile);
		} else if (os.contains("nix") || os.contains("nux") || os.contains("mac")) {
			directorioDocumentos = directorioDocumentosLinux;
		} else {
			throw new IllegalStateException("Sistema operativo no soportado: " + os);
		}

		return Path.of(directorioDocumentos).toAbsolutePath().normalize();
	}

	/**
	 * Intenta resolver una ruta existente ignorando diferencias de mayusculas/minusculas
	 * en cada segmento del path.
	 *
	 * @param rutaOriginal Ruta solicitada originalmente
	 * @return Ruta real existente si se encuentra, o la original si no hay coincidencia
	 * @throws IOException Si falla el acceso al sistema de archivos
	 */
	private Path resolvePathCaseInsensitive(Path rutaOriginal) throws IOException {
		if (Files.exists(rutaOriginal)) {
			return rutaOriginal;
		}

		Path rutaNormalizada = rutaOriginal.toAbsolutePath().normalize();
		Path raiz = rutaNormalizada.getRoot();
		if (raiz == null) {
			return rutaOriginal;
		}

		Path actual = raiz;
		for (Path segmento : rutaNormalizada) {
			String nombreSegmento = segmento.toString();
			Path candidato = actual.resolve(nombreSegmento);
			if (Files.exists(candidato)) {
				actual = candidato;
				continue;
			}

			Path coincidencia = findUniquePathIgnoreCase(actual, nombreSegmento);
			if (coincidencia == null) {
				return rutaOriginal;
			}

			actual = coincidencia;
		}

		if (!rutaOriginal.equals(actual)) {
			logger.warn("Resolved path with case-insensitive fallback: {} -> {}", rutaOriginal, actual);
		}

		return actual;
	}

	private Path findUniquePathIgnoreCase(Path directorio, String nombreEsperado) throws IOException {
		if (!Files.isDirectory(directorio)) {
			return null;
		}

		try (Stream<Path> hijos = Files.list(directorio)) {
			List<Path> coincidencias = hijos
					.filter(hijo -> hijo.getFileName().toString().equalsIgnoreCase(nombreEsperado))
					.toList();

			if (coincidencias.size() > 1) {
				throw new IOException("Se encontraron varias coincidencias case-insensitive para '" + nombreEsperado
						+ "' en '" + directorio + "'");
			}

			if (!coincidencias.isEmpty()) {
				return coincidencias.get(0);
			}
		}

		String nombreEsperadoNormalizado = normalizarTokenPath(nombreEsperado);
		try (Stream<Path> hijos = Files.list(directorio)) {
			List<Path> coincidenciasNormalizadas = hijos
					.filter(hijo -> normalizarTokenPath(hijo.getFileName().toString()).equals(nombreEsperadoNormalizado))
					.toList();

			if (coincidenciasNormalizadas.size() > 1) {
				throw new IOException("Se encontraron varias coincidencias normalizadas para '" + nombreEsperado
						+ "' en '" + directorio + "'");
			}

			return coincidenciasNormalizadas.isEmpty() ? null : coincidenciasNormalizadas.get(0);
		}
	}

	private String normalizarTokenPath(String valor) {
		if (valor == null) {
			return "";
		}

		String normalizado = Normalizer.normalize(valor, Normalizer.Form.NFKD)
				.replaceAll("\\p{M}+", "")
				.toLowerCase(Locale.ROOT);
		return normalizado;
	}
}
