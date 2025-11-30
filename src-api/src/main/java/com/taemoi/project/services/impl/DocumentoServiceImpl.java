package com.taemoi.project.services.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.config.FileUtils;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Documento;
import com.taemoi.project.repositories.DocumentoRepository;
import com.taemoi.project.services.DocumentoService;

@Service
public class DocumentoServiceImpl implements DocumentoService {

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
		Path rutaCarpetaAlumno = findExistingAlumnoFolder(rutaBaseDocumentos, numeroExpediente);

		// If no folder exists, create a new one
		if (rutaCarpetaAlumno == null) {
			String nombreLimpio = FileUtils.limpiarNombreArchivo(alumno.getNombre());
			String apellidosLimpio = FileUtils.limpiarNombreArchivo(alumno.getApellidos());
			String carpetaAlumno = alumno.getNumeroExpediente() + "_" + nombreLimpio + "_" + apellidosLimpio;
			rutaCarpetaAlumno = rutaBaseDocumentos.resolve(carpetaAlumno);

			if (!Files.exists(rutaCarpetaAlumno)) {
				Files.createDirectories(rutaCarpetaAlumno);
			}
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

		Documento documento = new Documento();
		documento.setNombre(nombreArchivoFinal);
		documento.setTipo(archivo.getContentType());
		documento.setUrl(urlAcceso);
		documento.setRuta(rutaArchivoFinal.toString());
		documento.setAlumno(alumno);

		return documentoRepository.save(documento);
	}

	@Override
	public void eliminarDocumento(Documento documento) {
		if (documento != null && documento.getRuta() != null) {
			Path rutaArchivo = Path.of(documento.getRuta());
			try {
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
					try {
						if (Files.isDirectory(carpetaAlumno)) {
							try (Stream<Path> filesInDir = Files.list(carpetaAlumno)) {
								boolean estaVacia = filesInDir.findAny().isEmpty();
								if (estaVacia) {
									Files.delete(carpetaAlumno);
								}
							}
						}
					} catch (IOException e) {
						throw new RuntimeException("Error al eliminar la carpeta del alumno: " + carpetaAlumno, e);
					}
				}
			}
		}
	}

	@Override
	public Documento obtenerDocumentoPorId(Long documentoId) {
		return documentoRepository.findById(documentoId)
				.orElseThrow(() -> new RuntimeException("Documento no encontrado: " + documentoId));
	}

	/**
	 * Busca una carpeta existente para el alumno basándose en el número de expediente.
	 * La búsqueda es case-insensitive para manejar diferencias de capitalización.
	 *
	 * @param rutaBase La ruta base donde buscar
	 * @param numeroExpediente El número de expediente del alumno
	 * @return La ruta de la carpeta existente, o null si no existe
	 * @throws IOException Si ocurre un error al listar directorios
	 */
	private Path findExistingAlumnoFolder(Path rutaBase, String numeroExpediente) throws IOException {
		if (!Files.exists(rutaBase) || !Files.isDirectory(rutaBase)) {
			return null;
		}

		// List all directories that start with the numero expediente
		try (Stream<Path> paths = Files.list(rutaBase)) {
			return paths
					.filter(Files::isDirectory)
					.filter(path -> {
						String folderName = path.getFileName().toString();
						// Check if folder starts with "numeroExpediente_"
						return folderName.toLowerCase().startsWith(numeroExpediente.toLowerCase() + "_");
					})
					.findFirst()
					.orElse(null);
		}
	}
}
