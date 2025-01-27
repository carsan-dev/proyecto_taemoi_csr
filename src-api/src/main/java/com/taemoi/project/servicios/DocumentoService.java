package com.taemoi.project.servicios;

import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.entidades.Alumno;
import com.taemoi.project.entidades.Documento;

public interface DocumentoService {

	Documento guardarDocumento(Alumno alumno, MultipartFile archivo) throws IOException;

	void eliminarDocumento(Documento documento);

	Documento obtenerDocumentoPorId(Long documentoId);
	
}
