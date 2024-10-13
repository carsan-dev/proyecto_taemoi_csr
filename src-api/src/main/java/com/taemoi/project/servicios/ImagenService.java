package com.taemoi.project.servicios;

import java.io.IOException;

import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.entidades.Imagen;

public interface ImagenService {
	Imagen guardarImagen(MultipartFile archivo) throws IOException;
	
	void eliminarImagenDeSistema(Imagen imagen);
}
