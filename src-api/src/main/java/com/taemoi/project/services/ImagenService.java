package com.taemoi.project.services;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.entities.Imagen;

public interface ImagenService {
	Imagen guardarImagen(MultipartFile archivo) throws IOException;

	Imagen guardarImagenEvento(MultipartFile archivo) throws IOException;

	/**
	 * Pieza E7-7a: escribe el PNG ya validado del logo de una escuela EXPLÍCITA (no la activa: lo sube el superadmin)
	 * en {@code org-<id>/logo/<uuid>_logo.png}. Devuelve la imagen sin guardar en base de datos; el nombre del fichero
	 * subido no se usa.
	 */
	Imagen guardarLogoEscuela(Long organizacionId, byte[] png) throws IOException;

	void eliminarImagenDeSistema(Imagen imagen);

	/**
	 * Fichero de una imagen: las rutas relativas (nuevas, {@code org-<id>/…}) se resuelven bajo la raíz de
	 * imágenes sin poder salir de ella; las absolutas (heredadas) se usan tal cual.
	 *
	 * @return la ruta, o {@code null} si la imagen no tiene ruta o no es válida.
	 */
	Path resolverRutaArchivo(Imagen imagen);
}
