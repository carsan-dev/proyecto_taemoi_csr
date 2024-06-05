package com.taemoi.project.servicios.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.taemoi.project.entidades.Evento;
import com.taemoi.project.entidades.Imagen;
import com.taemoi.project.errores.evento.EventoNoEncontradoException;
import com.taemoi.project.repositorios.EventoRepository;
import com.taemoi.project.repositorios.ImagenRepository;
import com.taemoi.project.servicios.EventoService;

@Service
public class EventoServiceImpl implements EventoService{
	
    @Autowired
    private EventoRepository eventoRepository;
    
    @Autowired
    private ImagenRepository imagenRepository;

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
    public Evento guardarEvento(@NonNull Evento evento) {
        return eventoRepository.save(evento);
    }
    
    @Override
    public Imagen guardarImagen(@NonNull Imagen imagen) {
        return imagenRepository.save(imagen);
    }
    
    @Override
    public Evento actualizarEvento(@NonNull Long id, Evento eventoActualizado, Imagen imagen) {
        Optional<Evento> optionalEvento = eventoRepository.findById(id);
        if (optionalEvento.isPresent()) {
            Evento eventoExistente = optionalEvento.get();
            eventoExistente.setTitulo(eventoActualizado.getTitulo());
            eventoExistente.setDescripcion(eventoActualizado.getDescripcion());
            if (imagen != null) {
                eventoExistente.setFotoEvento(imagen);
            }
            return eventoRepository.save(eventoExistente);
        } else {
            throw new RuntimeException("No se encontró el evento con ID: " + id);
        }
    }
    
	@Override
    public void eliminarImagenEvento(@NonNull Long id) {
        Optional<Evento> optionalEvento = eventoRepository.findById(id);
        if (optionalEvento.isPresent()) {
            Evento evento = optionalEvento.get();
            Imagen imagen = evento.getFotoEvento();
            if (imagen != null) {
            	evento.setFotoEvento(null);
                eventoRepository.save(evento);
                imagenRepository.delete(imagen);
            } else {
                throw new RuntimeException("El evento no tiene una imagen asociada.");
            }
        } else {
            throw new RuntimeException("No se encontró el evento con ID: " + id);
        }
    }
    
    @Override
    public void eliminarEvento(@NonNull Long id) {
        Optional<Evento> optionalEvento = eventoRepository.findById(id);
        if (optionalEvento.isPresent()) {
            Evento evento = optionalEvento.get();
            Imagen imagen = evento.getFotoEvento();
            if (imagen != null) {
            	evento.setFotoEvento(null);
                imagenRepository.delete(imagen);
            }
            eventoRepository.delete(evento);
        } else {
            throw new RuntimeException("No se encontró el evento con ID: " + id);
        }
    }
}