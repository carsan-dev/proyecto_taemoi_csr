package com.taemoi.project.services;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.entities.Imagen;

public interface ImagenService {
	/** Lado mayor de la miniatura de una foto de la galería; con {@code ?w=} hasta este valor se sirve ella. */
	int LADO_MINIATURA_FOTO_EVENTO = 640;

	/** Los dos ficheros ya escritos de una foto de la galería, con sus imágenes sin guardar en base de datos. */
	record FotoEventoGuardada(Imagen foto, Imagen miniatura) {
	}

	Imagen guardarImagen(MultipartFile archivo) throws IOException;

	Imagen guardarImagenEvento(MultipartFile archivo) throws IOException;

	/**
	 * Decisión 342: foto de la galería de un evento, en {@code org-<id>/fotos-eventos/}, una carpeta privada. Como
	 * {@link #guardarImagenEvento}, y además aplica la orientación EXIF, rechaza más de 60 megapíxeles sin decodificar y
	 * escribe una miniatura de {@value #LADO_MINIATURA_FOTO_EVENTO} px, porque WebP no se reduce al servir. Si la miniatura
	 * falla no queda la foto. El nombre del fichero subido no se usa: puede ser el de un menor.
	 */
	FotoEventoGuardada guardarImagenFotoEvento(MultipartFile archivo) throws IOException;

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
