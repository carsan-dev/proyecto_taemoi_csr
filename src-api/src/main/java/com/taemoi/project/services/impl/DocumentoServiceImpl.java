package com.taemoi.project.services.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.config.FileUtils;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Documento;
import com.taemoi.project.entities.Evento;
import com.taemoi.project.repositories.DocumentoRepository;
import com.taemoi.project.services.DocumentoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class DocumentoServiceImpl implements DocumentoService {

	private static final Logger logger = LoggerFactory.getLogger(DocumentoServiceImpl.class);

	@Value("${app.base.url}")
	private String baseUrl;

	@Value("${app.documentos.directorio.linux}")
	private String directorioDocumentosLinux;

	@Value("${app.documentos.directorio.windows}")
	private String directorioDocumentosWindows;

	@Autowired
	private DocumentoRepository documentoRepository;

	@Override
	public Documento guardarDocumento(Alumno alumno, MultipartFile archivo) throws IOException {
		String os = System.getProperty("os.name").toLowerCase();
		String directorioDocumentos;

		if (os.contains("win")) {
			String userProfile = System.getenv("USERPROFILE");
			directorioDocumentos = directorioDocumentosWindows.replace("%USERPROFILE%", userProfile);
		} else if (os.contains("nix") || os.contains("nux") || os.contains("mac")) {
			directorioDocumentos = directorioDocumentosLinux;
		} else {
			throw new IllegalStateException("Sistema operativo no soportado: " + os);
		}

		Path rutaBaseDocumentos = Path.of(directorioDocumentos, "Documentos_Alumnos_Moiskimdo");
		if (!Files.exists(rutaBaseDocumentos)) {
			Files.createDirectories(rutaBaseDocumentos);
		}

		// Try to find existing folder for this alumno (case-insensitive)
		String numeroExpediente = String.valueOf(alumno.getNumeroExpediente());
		logger.info("*** Searching for existing folder for alumno expediente: {}", numeroExpediente);
		logger.info("*** Base path: {}", rutaBaseDocumentos);

		Path rutaCarpetaAlumno = findExistingAlumnoFolder(rutaBaseDocumentos, numeroExpediente);

		// If no folder exists, create a new one
		if (rutaCarpetaAlumno == null) {
			logger.warn("*** NO existing folder found for expediente: {}. Creating new folder.", numeroExpediente);
			String nombreLimpio = FileUtils.limpiarNombreArchivo(alumno.getNombre());
			String apellidosLimpio = FileUtils.limpiarNombreArchivo(alumno.getApellidos());
			String carpetaAlumno = alumno.getNumeroExpediente() + "_" + nombreLimpio + "_" + apellidosLimpio;
			rutaCarpetaAlumno = rutaBaseDocumentos.resolve(carpetaAlumno);
			logger.info("*** New folder path: {}", rutaCarpetaAlumno);

			if (!Files.exists(rutaCarpetaAlumno)) {
				Files.createDirectories(rutaCarpetaAlumno);
			}
		} else {
			logger.info("*** FOUND existing folder: {}", rutaCarpetaAlumno);
		}

		String nombreOriginalArchivo = archivo.getOriginalFilename();
		String nombreOriginalLimpio = FileUtils.limpiarNombreArchivo(nombreOriginalArchivo);
		String nombreArchivoFinal = nombreOriginalLimpio;

		Path rutaArchivoFinal = rutaCarpetaAlumno.resolve(nombreArchivoFinal);
		Files.copy(archivo.getInputStream(), rutaArchivoFinal, StandardCopyOption.REPLACE_EXISTING);

		// Use the actual folder name (not the generated one) for the URL
		String carpetaNombreReal = rutaCarpetaAlumno.getFileName().toString();
		String urlAcceso = "%s/documentos/Documentos_Alumnos_Moiskimdo/%s/%s".formatted(baseUrl, carpetaNombreReal,
                nombreArchivoFinal);

		// Store RELATIVE path (from documentos directory) to be environment-independent
		String rutaRelativa = "Documentos_Alumnos_Moiskimdo/" + carpetaNombreReal + "/" + nombreArchivoFinal;

		Documento documento = new Documento();
		documento.setNombre(nombreArchivoFinal);
		documento.setTipo(archivo.getContentType());
		documento.setUrl(urlAcceso);
		documento.setRuta(rutaRelativa);  // Store relative path instead of absolute
		documento.setAlumno(alumno);

		return documentoRepository.save(documento);
	}

	@Override
	public Documento guardarDocumentoEvento(Evento evento, MultipartFile archivo) throws IOException {
		String os = System.getProperty("os.name").toLowerCase();
		String directorioDocumentos;

		if (os.contains("win")) {
			String userProfile = System.getenv("USERPROFILE");
			if (userProfile == null) {
				userProfile = System.getProperty("user.home");
			}
			directorioDocumentos = directorioDocumentosWindows.replace("%USERPROFILE%", userProfile);
		} else if (os.contains("nix") || os.contains("nux") || os.contains("mac")) {
			directorioDocumentos = directorioDocumentosLinux;
		} else {
			throw new IllegalStateException("Sistema operativo no soportado: " + os);
		}

		Path rutaBaseDocumentos = Path.of(directorioDocumentos, "Documentos_Eventos");
		if (!Files.exists(rutaBaseDocumentos)) {
			Files.createDirectories(rutaBaseDocumentos);
		}

		String tituloLimpio = FileUtils.limpiarNombreArchivo(evento.getTitulo());
		String carpetaEvento = evento.getId() + "_" + tituloLimpio;
		Path rutaCarpetaEvento = rutaBaseDocumentos.resolve(carpetaEvento);

		if (!Files.exists(rutaCarpetaEvento)) {
			Files.createDirectories(rutaCarpetaEvento);
		}

		String nombreOriginalArchivo = archivo.getOriginalFilename();
		String nombreOriginalLimpio = FileUtils.limpiarNombreArchivo(nombreOriginalArchivo);

		Path rutaArchivoFinal = rutaCarpetaEvento.resolve(nombreOriginalLimpio);
		Files.copy(archivo.getInputStream(), rutaArchivoFinal, StandardCopyOption.REPLACE_EXISTING);

		String urlAcceso = "%s/documentos/Documentos_Eventos/%s/%s".formatted(baseUrl, carpetaEvento, nombreOriginalLimpio);
		String rutaRelativa = "Documentos_Eventos/" + carpetaEvento + "/" + nombreOriginalLimpio;

		Documento documento = new Documento();
		documento.setNombre(nombreOriginalLimpio);
		documento.setTipo(archivo.getContentType());
		documento.setUrl(urlAcceso);
		documento.setRuta(rutaRelativa);
		documento.setEvento(evento);

		return documentoRepository.save(documento);
	}

	@Override
	public void eliminarDocumento(Documento documento) {
		if (documento != null && documento.getRuta() != null) {
			String rutaDocumento = documento.getRuta();
			Path rutaArchivo;

			// Handle both relative and absolute paths
			if (rutaDocumento.startsWith("/") || rutaDocumento.matches("^[A-Za-z]:.*")) {
				// Absolute path (old format)
				// Check if this is an old path from /opt/taemoi/static_resources/documentos/
				if (rutaDocumento.startsWith("/opt/taemoi/static_resources/documentos/")) {
					// Extract the relative part and resolve against current base directory
					String relativePart = rutaDocumento.substring("/opt/taemoi/static_resources/documentos/".length());
					String os = System.getProperty("os.name").toLowerCase();
					String directorioDocumentos;

					if (os.contains("win")) {
						String userProfile = System.getenv("USERPROFILE");
						directorioDocumentos = directorioDocumentosWindows.replace("%USERPROFILE%", userProfile);
					} else if (os.contains("nix") || os.contains("nux") || os.contains("mac")) {
						directorioDocumentos = directorioDocumentosLinux;
					} else {
						throw new IllegalStateException("Sistema operativo no soportado: " + os);
					}

					rutaArchivo = Path.of(directorioDocumentos).resolve(relativePart);
					logger.info("Converted old absolute path for deletion: {} -> {}", rutaDocumento, rutaArchivo);
				} else {
					// Other absolute path - use as is
					rutaArchivo = Path.of(rutaDocumento);
				}
			} else {
				// Relative path (new format) - resolve against base directory
				String os = System.getProperty("os.name").toLowerCase();
				String directorioDocumentos;

				if (os.contains("win")) {
					String userProfile = System.getenv("USERPROFILE");
					directorioDocumentos = directorioDocumentosWindows.replace("%USERPROFILE%", userProfile);
				} else if (os.contains("nix") || os.contains("nux") || os.contains("mac")) {
					directorioDocumentos = directorioDocumentosLinux;
				} else {
					throw new IllegalStateException("Sistema operativo no soportado: " + os);
				}

				rutaArchivo = Path.of(directorioDocumentos).resolve(rutaDocumento);
			}

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
	public Resource obtenerRecursoDocumento(Documento documento) {
		try {
			String rutaDocumento = documento.getRuta();
			Path rutaArchivo;

			// Handle both relative and absolute paths for backward compatibility
			if (rutaDocumento.startsWith("/") || rutaDocumento.matches("^[A-Za-z]:.*")) {
				// Absolute path (old format)
				logger.warn("Document {} uses absolute path (deprecated): {}", documento.getId(), rutaDocumento);

				// Check if this is an old path from /opt/taemoi/static_resources/documentos/
				// and convert it to the new location
				if (rutaDocumento.startsWith("/opt/taemoi/static_resources/documentos/")) {
					// Extract the relative part after /opt/taemoi/static_resources/documentos/
					String relativePart = rutaDocumento.substring("/opt/taemoi/static_resources/documentos/".length());
					String os = System.getProperty("os.name").toLowerCase();
					String directorioDocumentos;

					if (os.contains("win")) {
						String userProfile = System.getenv("USERPROFILE");
						directorioDocumentos = directorioDocumentosWindows.replace("%USERPROFILE%", userProfile);
					} else if (os.contains("nix") || os.contains("nux") || os.contains("mac")) {
						directorioDocumentos = directorioDocumentosLinux;
					} else {
						throw new IllegalStateException("Sistema operativo no soportado: " + os);
					}

					rutaArchivo = Path.of(directorioDocumentos).resolve(relativePart);
					logger.info("Converted old absolute path to new location: {} -> {}", rutaDocumento, rutaArchivo);
				} else {
					// Other absolute path - use as is
					rutaArchivo = Path.of(rutaDocumento);
				}
			} else {
				// Relative path (new format) - resolve against base directory
				String os = System.getProperty("os.name").toLowerCase();
				String directorioDocumentos;

				if (os.contains("win")) {
					String userProfile = System.getenv("USERPROFILE");
					directorioDocumentos = directorioDocumentosWindows.replace("%USERPROFILE%", userProfile);
				} else if (os.contains("nix") || os.contains("nux") || os.contains("mac")) {
					directorioDocumentos = directorioDocumentosLinux;
				} else {
					throw new IllegalStateException("Sistema operativo no soportado: " + os);
				}

				rutaArchivo = Path.of(directorioDocumentos).resolve(rutaDocumento);
				logger.info("Resolved relative path {} to absolute: {}", rutaDocumento, rutaArchivo);
			}

			rutaArchivo = resolvePathCaseInsensitive(rutaArchivo);
			if (!Files.exists(rutaArchivo)) {
				throw new RuntimeException("El archivo no existe en el sistema de archivos: " + rutaArchivo);
			}
			return new UrlResource(rutaArchivo.toUri());
		} catch (Exception e) {
			throw new RuntimeException("No se pudo cargar el documento: " + documento.getNombre(), e);
		}
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

			return coincidencias.isEmpty() ? null : coincidencias.get(0);
		}
	}
	/**
	 * Busca una carpeta existente para el alumno basandose en el numero de expediente.
	 * La busqueda es case-insensitive para manejar diferencias de capitalizacion.
	 *
	 * @param rutaBase La ruta base donde buscar
	 * @param numeroExpediente El numero de expediente del alumno
	 * @return La ruta de la carpeta existente, o null si no existe
	 * @throws IOException Si ocurre un error al listar directorios
	 */
	private Path findExistingAlumnoFolder(Path rutaBase, String numeroExpediente) throws IOException {
		logger.info("*** findExistingAlumnoFolder called with: rutaBase={}, numeroExpediente={}", rutaBase, numeroExpediente);

		if (!Files.exists(rutaBase)) {
			logger.warn("*** Base path does NOT exist: {}", rutaBase);
			return null;
		}

		if (!Files.isDirectory(rutaBase)) {
			logger.warn("*** Base path is NOT a directory: {}", rutaBase);
			return null;
		}

		logger.info("*** Listing directories in: {}", rutaBase);

		// List all directories that start with the numero expediente
		try (Stream<Path> paths = Files.list(rutaBase)) {
			List<Path> allDirs = paths.filter(Files::isDirectory).collect(java.util.stream.Collectors.toList());
			logger.info("*** Found {} directories total", allDirs.size());

			for (Path dir : allDirs) {
				String folderName = dir.getFileName().toString();
				logger.info("*** Checking directory: {}", folderName);
				if (folderName.toLowerCase().startsWith(numeroExpediente.toLowerCase() + "_")) {
					logger.info("*** MATCH FOUND: {}", dir);
					return dir;
				}
			}

			logger.warn("*** NO MATCH found for pattern: {}_{}", numeroExpediente, "*");
			return null;
		}
	}
}


