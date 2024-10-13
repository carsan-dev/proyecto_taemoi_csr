package com.taemoi.project.servicios.impl;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.entidades.Evento;
import com.taemoi.project.entidades.Imagen;
import com.taemoi.project.errores.evento.EventoNoEncontradoException;
import com.taemoi.project.repositorios.EventoRepository;
import com.taemoi.project.repositorios.ImagenRepository;
import com.taemoi.project.servicios.EventoService;
import com.taemoi.project.servicios.ImagenService;

@Service
public class EventoServiceImpl implements EventoService{
	
    @Autowired
    private EventoRepository eventoRepository;
    
    @Autowired
    private ImagenRepository imagenRepository;
    
    @Autowired
    private ImagenService imagenService;

    @Override
    public List<Evento> obtenerTodosLosEventos() {
        return eventoRepository.findAll();
    }
    
    @Override
    public Evento obtenerEventoPorId(@NonNull Long eventoId) {
        return eventoRepository.findById(eventoId)
                .orElseThrow(() -> new EventoNoEncontradoException("El evento con ID " + eventoId + " no existe."));
    }
    
    @Override
    public Evento guardarEvento(@NonNull Evento evento, MultipartFile archivoImagen) throws IOException {
        if (archivoImagen != null && !archivoImagen.isEmpty()) {
            // Guardar la imagen en el sistema de archivos y obtener la entidad `Imagen` con la ruta
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

            // Si hay una nueva imagen, primero debemos guardarla en la base de datos antes de asignarla
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
                evento.setFotoEvento(null);  // Desvincular la imagen del evento
                eventoRepository.save(evento);  // Guardar el evento sin la referencia de imagen

                // Ahora podemos eliminar la imagen del sistema de archivos y la base de datos
                imagenService.eliminarImagenDeSistema(imagen);  // Eliminar la imagen del sistema de archivos
                imagenRepository.delete(imagen);  // Eliminar la imagen de la base de datos
            }

            // Finalmente eliminar el evento
            eventoRepository.delete(evento);  // Eliminar el evento
        } else {
            throw new EventoNoEncontradoException("No se encontró el evento con ID: " + id);
        }
    }

}