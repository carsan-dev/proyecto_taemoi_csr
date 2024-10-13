package com.taemoi.project.servicios;

import java.io.IOException;
import java.util.List;

import org.springframework.lang.NonNull;
import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.entidades.Evento;
import com.taemoi.project.entidades.Imagen;

public interface EventoService {

	List<Evento> obtenerTodosLosEventos();

	Evento guardarEvento(@NonNull Evento evento, MultipartFile archivo) throws IOException;

	void eliminarEvento(@NonNull Long id);

	Evento actualizarEvento(@NonNull Long id, Evento eventoActualizado, Imagen nuevaImagen);

	Evento obtenerEventoPorId(@NonNull Long eventoId);

	void eliminarImagenEvento(@NonNull Long id);
}
