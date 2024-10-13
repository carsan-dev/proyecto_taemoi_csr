package com.taemoi.project.servicios.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.configuracion.FileUtils;
import com.taemoi.project.entidades.Imagen;
import com.taemoi.project.servicios.ImagenService;

@Service
public class ImagenServiceImpl implements ImagenService {

	@Value("${app.imagenes.directorio.linux}")
	private String directorioImagenesLinux;

	@Value("${app.imagenes.directorio.windows}")
	private String directorioImagenesWindows;

	@Value("${app.base.url}") // Base URL for serving images
	private String baseUrl;

	@Override
	public Imagen guardarImagen(MultipartFile archivo) throws IOException {
		// Detectar el sistema operativo
		String os = System.getProperty("os.name").toLowerCase();
		String directorioImagenes;

		// Seleccionar la ruta correcta dependiendo del sistema operativo
		if (os.contains("win")) {
			String userProfile = System.getenv("USERPROFILE");
			directorioImagenes = directorioImagenesWindows.replace("%USERPROFILE%", userProfile);
		} else if (os.contains("nix") || os.contains("nux") || os.contains("mac")) {
			directorioImagenes = directorioImagenesLinux;
		} else {
			throw new IllegalStateException("Sistema operativo no soportado: " + os);
		}

		// Obtener la ruta correcta del directorio
		Path rutaImagenes = Paths.get(directorioImagenes);

		// Verificar si el directorio de imágenes existe, si no, crearlo
		if (!Files.exists(rutaImagenes)) {
			Files.createDirectories(rutaImagenes);
		}

		// Obtener el nombre original del archivo y formatearlo
		String nombreOriginalArchivo = archivo.getOriginalFilename();
		String nombreLimpioArchivo = FileUtils.limpiarNombreArchivo(nombreOriginalArchivo);

		// Crear un nombre único para la imagen con UUID
		String nombreArchivoFinal = UUID.randomUUID().toString() + "_" + nombreLimpioArchivo;
		Path rutaArchivo = rutaImagenes.resolve(nombreArchivoFinal);

		// Guardar el archivo en el sistema de archivos
		Files.copy(archivo.getInputStream(), rutaArchivo, StandardCopyOption.REPLACE_EXISTING);

		// Construir la URL pública para acceder a la imagen
		String urlAcceso = baseUrl + "/imagenes/" + nombreArchivoFinal;

		// Crear la entidad `Imagen` con la ruta del archivo y la URL de acceso
		return new Imagen(nombreArchivoFinal, archivo.getContentType(), urlAcceso, rutaArchivo.toString());
	}

	public void eliminarImagenDeSistema(Imagen imagen) {
	    if (imagen != null && imagen.getRuta() != null) {
	        Path rutaArchivo = Paths.get(imagen.getRuta()); // Usar la ruta física

	        try {
	            // Comprobar si el archivo existe antes de intentar eliminarlo
	            if (Files.exists(rutaArchivo)) {
	                Files.delete(rutaArchivo); // Eliminar el archivo
	                System.out.println("Imagen eliminada correctamente del sistema de archivos: " + rutaArchivo);
	            } else {
	                System.out.println("La imagen no existe en el sistema de archivos: " + rutaArchivo);
	            }
	        } catch (IOException e) {
	            throw new RuntimeException("Error al eliminar el archivo de imagen: " + rutaArchivo, e);
	        }
	    } else {
	        throw new IllegalArgumentException("La imagen o su ruta no es válida.");
	    }
	}

}