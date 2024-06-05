package com.taemoi.project.servicios;

import java.util.List;

import org.springframework.lang.NonNull;

import com.taemoi.project.entidades.Evento;
import com.taemoi.project.entidades.Imagen;

public interface EventoService {

	List<Evento> obtenerTodosLosEventos();

	Evento guardarEvento(@NonNull Evento evento);

	void eliminarEvento(@NonNull Long id);

	Imagen guardarImagen(@NonNull Imagen imagen);

	Evento actualizarEvento(@NonNull Long id, Evento eventoActualizado, Imagen imagen);

	Evento obtenerEventoPorId(@NonNull Long eventoId);

	void eliminarImagenEvento(@NonNull Long id);
}
