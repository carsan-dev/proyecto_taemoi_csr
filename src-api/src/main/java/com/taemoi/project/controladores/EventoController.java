package com.taemoi.project.controladores;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.entidades.Evento;
import com.taemoi.project.entidades.Imagen;
import com.taemoi.project.errores.evento.EventoNoEncontradoException;
import com.taemoi.project.errores.turno.TurnoNoEncontradoException;
import com.taemoi.project.servicios.EventoService;
import com.taemoi.project.servicios.ImagenService;

@RestController
@RequestMapping("/api/eventos")
public class EventoController {

    @Autowired
    private EventoService eventoService;
    
    @Autowired
    private ImagenService imagenService;
    
    @GetMapping
    public List<Evento> obtenerTodosLosEventos() {
        return eventoService.obtenerTodosLosEventos();
    }
    
    @GetMapping("/{eventoId}")
    @PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<Evento> obtenerEventoPorId(@PathVariable @NonNull Long eventoId) {
        try {
            Evento evento = eventoService.obtenerEventoPorId(eventoId);
            return ResponseEntity.ok(evento);
        } catch (TurnoNoEncontradoException e) {
            return ResponseEntity.notFound().build();
        }
    }
	
    @PostMapping(value = "/crear", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> crearEvento(@RequestParam("nuevo") String eventoJson,
                                         @RequestParam(value = "file", required = false) MultipartFile file) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Evento nuevoEvento = objectMapper.readValue(eventoJson, Evento.class);

            // Guardar el evento y la imagen (si existe)
            Evento eventoCreado = eventoService.guardarEvento(nuevoEvento, file);
            return new ResponseEntity<>(eventoCreado, HttpStatus.CREATED);

        } catch (IOException e) {
            return new ResponseEntity<>("Error al procesar la solicitud", HttpStatus.BAD_REQUEST);
        }
    }


    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> actualizarEvento(@PathVariable @NonNull Long id,
                                              @RequestParam(value = "file", required = false) MultipartFile file,
                                              @RequestParam("eventoEditado") String eventoJson) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Evento eventoActualizado = objectMapper.readValue(eventoJson, Evento.class);

            Imagen nuevaImagen = null;

            // Si se proporciona una nueva imagen, guardar la imagen y eliminar la anterior
            if (file != null && !file.isEmpty()) {
                nuevaImagen = imagenService.guardarImagen(file);  // Guardar la nueva imagen
            }

            // Actualizar el evento en la base de datos, incluyendo la nueva imagen
            Evento evento = eventoService.actualizarEvento(id, eventoActualizado, nuevaImagen);
            return new ResponseEntity<>(evento, HttpStatus.OK);

        } catch (IOException e) {
            return new ResponseEntity<>("Error al procesar la solicitud", HttpStatus.BAD_REQUEST);
        }
    }
    
    @DeleteMapping("/{id}/imagen")
    @PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> eliminarImagenEvento(@PathVariable @NonNull Long id) {
        try {
            eventoService.eliminarImagenEvento(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al eliminar la imagen del evento.");
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> eliminarEvento(@PathVariable @NonNull Long id) {
        try {
            eventoService.eliminarEvento(id);
            return ResponseEntity.ok().build();
        } catch (EventoNoEncontradoException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "El evento no fue encontrado."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error al eliminar el evento: " + e.getMessage()));
        }
    }



}
