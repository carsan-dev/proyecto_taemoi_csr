package com.taemoi.project.services;

import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Documento;

public interface DocumentoService {

	Documento guardarDocumento(Alumno alumno, MultipartFile archivo) throws IOException;

	void eliminarDocumento(Documento documento);

	Documento obtenerDocumentoPorId(Long documentoId);
	
}
