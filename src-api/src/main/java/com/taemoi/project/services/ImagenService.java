package com.taemoi.project.services;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.entities.Imagen;

public interface ImagenService {
	Imagen guardarImagen(MultipartFile archivo) throws IOException;

	Imagen guardarImagenEvento(MultipartFile archivo) throws IOException;

	void eliminarImagenDeSistema(Imagen imagen);

	/**
	 * Fichero de una imagen: las rutas relativas (nuevas, {@code org-<id>/…}) se resuelven bajo la raíz de
	 * imágenes sin poder salir de ella; las absolutas (heredadas) se usan tal cual.
	 *
	 * @return la ruta, o {@code null} si la imagen no tiene ruta o no es válida.
	 */
	Path resolverRutaArchivo(Imagen imagen);
}
