package com.taemoi.project.services;

import java.io.IOException;
import java.util.List;

import org.springframework.lang.NonNull;
import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.entities.Documento;
import com.taemoi.project.entities.Evento;
import com.taemoi.project.entities.Imagen;

public interface EventoService {

	List<Evento> obtenerTodosLosEventos();

	List<Evento> obtenerEventosVisibles();

	Evento guardarEvento(@NonNull Evento evento, MultipartFile archivo) throws IOException;

	void eliminarEvento(@NonNull Long id);

	Evento actualizarEvento(@NonNull Long id, Evento eventoActualizado, Imagen nuevaImagen);

	Evento obtenerEventoPorId(@NonNull Long eventoId);

	void eliminarImagenEvento(@NonNull Long id);

	void toggleVisibilidad(@NonNull Long id);

	void actualizarOrdenEventos(@NonNull List<Long> ordenIds);

	Documento agregarDocumentoAEvento(@NonNull Long eventoId, MultipartFile archivo) throws IOException;

	List<Documento> obtenerDocumentosEvento(@NonNull Long eventoId);

	void eliminarDocumentoDeEvento(@NonNull Long eventoId, @NonNull Long documentoId);

	Documento obtenerDocumentoDeEvento(@NonNull Long eventoId, @NonNull Long documentoId);
}
