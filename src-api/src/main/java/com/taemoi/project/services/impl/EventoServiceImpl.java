package com.taemoi.project.services.impl;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.entities.Documento;
import com.taemoi.project.entities.Evento;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.exceptions.evento.EventoNoEncontradoException;
import com.taemoi.project.repositories.DocumentoRepository;
import com.taemoi.project.repositories.EventoRepository;
import com.taemoi.project.repositories.ImagenRepository;
import com.taemoi.project.services.DocumentoService;
import com.taemoi.project.services.EventoService;
import com.taemoi.project.services.ImagenService;

@Service
public class EventoServiceImpl implements EventoService {

	@Autowired
	private EventoRepository eventoRepository;

	@Autowired
	private ImagenRepository imagenRepository;

	@Autowired
	private DocumentoRepository documentoRepository;

	@Autowired
	private ImagenService imagenService;

	@Autowired
	private DocumentoService documentoService;

	@Override
	public List<Evento> obtenerTodosLosEventos() {
		return eventoRepository.findAll();
	}

	@Override
	public List<Evento> obtenerEventosVisibles() {
		return eventoRepository.findByVisibleTrue();
	}

	@Override
	public Evento obtenerEventoPorId(@NonNull Long eventoId) {
		return eventoRepository.findById(eventoId)
				.orElseThrow(() -> new EventoNoEncontradoException("El evento con ID " + eventoId + " no existe."));
	}

	@Override
	public Evento guardarEvento(@NonNull Evento evento, MultipartFile archivoImagen) throws IOException {
		if (archivoImagen != null && !archivoImagen.isEmpty()) {
			// Guardar la imagen en el sistema de archivos y obtener la entidad `Imagen` con
			// la ruta
			Imagen imagenGuardada = imagenService.guardarImagen(archivoImagen);

			// Persist the Imagen entity before assigning it to the Evento entity
			imagenRepository.save(imagenGuardada);

			evento.setFotoEvento(imagenGuardada);
		}

		// Save the Evento after the Imagen is saved
		return eventoRepository.save(evento);
	}

	@Override
	public Evento actualizarEvento(@NonNull Long id, Evento eventoActualizado, Imagen nuevaImagen) {
		Optional<Evento> optionalEvento = eventoRepository.findById(id);
		if (optionalEvento.isPresent()) {
			Evento eventoExistente = optionalEvento.get();
			eventoExistente.setTitulo(eventoActualizado.getTitulo());
			eventoExistente.setDescripcion(eventoActualizado.getDescripcion());

			// Si hay una nueva imagen, primero debemos guardarla en la base de datos antes
			// de asignarla
			if (nuevaImagen != null) {
				Imagen imagenAnterior = eventoExistente.getFotoEvento();

				// Eliminar la imagen anterior del sistema de archivos si existe
				if (imagenAnterior != null) {
					imagenService.eliminarImagenDeSistema(imagenAnterior);
					imagenRepository.delete(imagenAnterior); // Eliminar de la base de datos
				}

				// Guardar la nueva imagen en la base de datos antes de asignarla
				imagenRepository.save(nuevaImagen);

				// Asignar la nueva imagen al evento
				eventoExistente.setFotoEvento(nuevaImagen);
			}

			// Guardar el evento actualizado
			return eventoRepository.save(eventoExistente);
		} else {
			throw new EventoNoEncontradoException("No se encontró el evento con ID: " + id);
		}
	}

	@Override
	public void eliminarImagenEvento(@NonNull Long id) {
		Optional<Evento> optionalEvento = eventoRepository.findById(id);
		if (optionalEvento.isPresent()) {
			Evento evento = optionalEvento.get();
			Imagen imagen = evento.getFotoEvento();

			// Desvincular la imagen del evento
			if (imagen != null) {
				evento.setFotoEvento(null);
				eventoRepository.save(evento); // Actualizar el evento para eliminar la referencia

				// Ahora podemos eliminar la imagen
				imagenService.eliminarImagenDeSistema(imagen); // Eliminar del sistema de archivos
				imagenRepository.delete(imagen); // Eliminar de la base de datos
			} else {
				throw new RuntimeException("El evento no tiene una imagen asociada.");
			}
		} else {
			throw new EventoNoEncontradoException("No se encontró el evento con ID: " + id);
		}
	}

	@Override
	public void eliminarEvento(@NonNull Long id) {
		Optional<Evento> optionalEvento = eventoRepository.findById(id);
		if (optionalEvento.isPresent()) {
			Evento evento = optionalEvento.get();
			Imagen imagen = evento.getFotoEvento();

			// Desvincular la imagen del evento
			if (imagen != null) {
				evento.setFotoEvento(null); // Desvincular la imagen del evento
				eventoRepository.save(evento); // Guardar el evento sin la referencia de imagen

				// Ahora podemos eliminar la imagen del sistema de archivos y la base de datos
				imagenService.eliminarImagenDeSistema(imagen); // Eliminar la imagen del sistema de archivos
				imagenRepository.delete(imagen); // Eliminar la imagen de la base de datos
			}

			// Finalmente eliminar el evento
			eventoRepository.delete(evento); // Eliminar el evento
		} else {
			throw new EventoNoEncontradoException("No se encontró el evento con ID: " + id);
		}
	}

	@Override
	public void toggleVisibilidad(@NonNull Long id) {
		Evento evento = eventoRepository.findById(id)
				.orElseThrow(() -> new EventoNoEncontradoException("No se encontró el evento con ID: " + id));

		evento.setVisible(!Boolean.TRUE.equals(evento.getVisible()));
		eventoRepository.save(evento);
	}

	@Override
	public Documento agregarDocumentoAEvento(@NonNull Long eventoId, MultipartFile archivo) throws IOException {
		Evento evento = eventoRepository.findById(eventoId)
				.orElseThrow(() -> new EventoNoEncontradoException("No se encontró el evento con ID: " + eventoId));

		Documento documento = documentoService.guardarDocumentoEvento(evento, archivo);
		evento.getDocumentos().add(documento);
		eventoRepository.save(evento);

		return documento;
	}

	@Override
	public List<Documento> obtenerDocumentosEvento(@NonNull Long eventoId) {
		Evento evento = eventoRepository.findById(eventoId)
				.orElseThrow(() -> new EventoNoEncontradoException("No se encontró el evento con ID: " + eventoId));
		return evento.getDocumentos();
	}

	@Override
	public void eliminarDocumentoDeEvento(@NonNull Long eventoId, @NonNull Long documentoId) {
		Documento documento = obtenerDocumentoDeEvento(eventoId, documentoId);
		Evento evento = documento.getEvento();

		documentoService.eliminarDocumento(documento);

		if (evento != null) {
			evento.getDocumentos().remove(documento);
			eventoRepository.save(evento);
		}
	}

	@Override
	public Documento obtenerDocumentoDeEvento(@NonNull Long eventoId, @NonNull Long documentoId) {
		Evento evento = eventoRepository.findById(eventoId)
				.orElseThrow(() -> new EventoNoEncontradoException("No se encontró el evento con ID: " + eventoId));

		return evento.getDocumentos().stream()
				.filter(doc -> doc.getId().equals(documentoId))
				.findFirst()
				.orElseThrow(() -> new RuntimeException(
						"El documento con ID " + documentoId + " no pertenece al evento con ID " + eventoId));
	}

}