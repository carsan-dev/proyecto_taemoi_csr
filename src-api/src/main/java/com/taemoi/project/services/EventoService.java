package com.taemoi.project.services;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.springframework.lang.NonNull;
import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.entities.Documento;
import com.taemoi.project.entities.Evento;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.exceptions.evento.DatosEventoNoValidosException;

public interface EventoService {

	List<Evento> obtenerTodosLosEventos();

	List<Evento> obtenerEventosVisibles();

	Evento guardarEvento(@NonNull Evento evento, MultipartFile archivo) throws IOException;

	void eliminarEvento(@NonNull Long id);

	/** Como {@link #actualizarEvento(Long, Evento, Imagen, Set)} sin campos de la web recibidos: los conserva. */
	default Evento actualizarEvento(@NonNull Long id, Evento eventoActualizado, Imagen nuevaImagen) {
		return actualizarEvento(id, eventoActualizado, nuevaImagen, Set.of());
	}

	/**
	 * Reemplaza título, descripción y fecha (y la imagen si llega). De los campos de la web ({@link #CAMPOS_WEB}), solo
	 * los de {@code camposWebRecibidos}: los demás se conservan (el panel de hoy no los envía).
	 *
	 * @throws DatosEventoNoValidosException (400) datos de la web no válidos
	 */
	Evento actualizarEvento(@NonNull Long id, Evento eventoActualizado, Imagen nuevaImagen,
			Set<String> camposWebRecibidos);

	/** Campos de la web por plantilla (W2, decisión 466) que la edición conserva si no llegan. */
	Set<String> CAMPOS_WEB = Set.of("hora", "lugar", "sedeId");

	/**
	 * Recorta el lugar (en blanco, {@code null}) y comprueba los datos de la web: lugar hasta 160 caracteres y sede de
	 * la escuela activa.
	 *
	 * @throws DatosEventoNoValidosException (400) no válidos
	 */
	void normalizarDatosWeb(Evento evento);

	Evento obtenerEventoPorId(@NonNull Long eventoId);

	void eliminarImagenEvento(@NonNull Long id);

	void toggleVisibilidad(@NonNull Long id);

	void actualizarOrdenEventos(@NonNull List<Long> ordenIds);

	Documento agregarDocumentoAEvento(@NonNull Long eventoId, MultipartFile archivo) throws IOException;

	List<Documento> obtenerDocumentosEvento(@NonNull Long eventoId);

	void eliminarDocumentoDeEvento(@NonNull Long eventoId, @NonNull Long documentoId);

	Documento obtenerDocumentoDeEvento(@NonNull Long eventoId, @NonNull Long documentoId);
}
