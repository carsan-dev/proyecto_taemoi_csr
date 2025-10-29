package com.taemoi.project.services.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
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

		String nombreLimpio = FileUtils.limpiarNombreArchivo(alumno.getNombre());
		String apellidosLimpio = FileUtils.limpiarNombreArchivo(alumno.getApellidos());
		String carpetaAlumno = alumno.getNumeroExpediente() + "_" + nombreLimpio + "_" + apellidosLimpio;

		Path rutaCarpetaAlumno = rutaBaseDocumentos.resolve(carpetaAlumno);
		if (!Files.exists(rutaCarpetaAlumno)) {
			Files.createDirectories(rutaCarpetaAlumno);
		}

		String nombreOriginalArchivo = archivo.getOriginalFilename();
		String nombreOriginalLimpio = FileUtils.limpiarNombreArchivo(nombreOriginalArchivo);
		String nombreArchivoFinal = nombreOriginalLimpio;

		Path rutaArchivoFinal = rutaCarpetaAlumno.resolve(nombreArchivoFinal);
		Files.copy(archivo.getInputStream(), rutaArchivoFinal, StandardCopyOption.REPLACE_EXISTING);

		String urlAcceso = "%s/documentos/Documentos_Alumnos_Moiskimdo/%s/%s".formatted(baseUrl, carpetaAlumno,
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
}
