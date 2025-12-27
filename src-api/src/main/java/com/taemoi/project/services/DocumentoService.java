package com.taemoi.project.services;

import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Documento;
import com.taemoi.project.entities.Evento;

public interface DocumentoService {

	Documento guardarDocumento(Alumno alumno, MultipartFile archivo) throws IOException;

	Documento guardarDocumentoEvento(Evento evento, MultipartFile archivo) throws IOException;

	void eliminarDocumento(Documento documento);

	Documento obtenerDocumentoPorId(Long documentoId);

	Resource obtenerRecursoDocumento(Documento documento);

}
