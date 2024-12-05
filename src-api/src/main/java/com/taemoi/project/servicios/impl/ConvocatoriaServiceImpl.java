package com.taemoi.project.servicios.impl;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.taemoi.project.dtos.ConvocatoriaDTO;
import com.taemoi.project.dtos.response.AlumnoConvocatoriaDTO;
import com.taemoi.project.entidades.Convocatoria;
import com.taemoi.project.entidades.Deporte;
import com.taemoi.project.repositorios.ConvocatoriaRepository;
import com.taemoi.project.servicios.ConvocatoriaService;

@Service
public class ConvocatoriaServiceImpl implements ConvocatoriaService {
	
	@Autowired
	private ConvocatoriaRepository convocatoriaRepository;

    @Override
    public ConvocatoriaDTO crearConvocatoria(ConvocatoriaDTO convocatoriaDTO) {
        // Crear directamente la entidad desde el DTO y guardarla
        Convocatoria convocatoria = new Convocatoria();
        convocatoria.setFechaConvocatoria(convocatoriaDTO.getFechaConvocatoria());
        convocatoria.setDeporte(convocatoriaDTO.getDeporte());
        Convocatoria convocatoriaGuardada = convocatoriaRepository.save(convocatoria);

        // Retornar el DTO generado
        return convertirAConvocatoriaDTO(convocatoriaGuardada);
    }

    @Override
    public List<ConvocatoriaDTO> obtenerConvocatorias() {
        return convocatoriaRepository.findAll().stream()
                .map(this::convertirAConvocatoriaDTO) // Convertir cada entidad a DTO
                .collect(Collectors.toList());
    }

    @Override
    public ConvocatoriaDTO obtenerConvocatoriaPorId(Long id) {
        Convocatoria convocatoria = convocatoriaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Convocatoria no encontrada con ID: " + id));

        return convertirAConvocatoriaDTO(convocatoria);
    }

    @Override
    public Optional<ConvocatoriaDTO> obtenerConvocatoriaActualPorDeporte(Deporte deporte) {
        return convocatoriaRepository.findConvocatoriaActualPorDeporte(deporte)
                .map(this::convertirAConvocatoriaDTO); // Convertir la entidad encontrada (si existe) a DTO
    }
	
    @Override
    public List<ConvocatoriaDTO> obtenerConvocatoriasPorDeporte(Deporte deporte) {
        return convocatoriaRepository.findByDeporte(deporte).stream()
                .map(this::convertirAConvocatoriaDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<AlumnoConvocatoriaDTO> obtenerAlumnosDeConvocatoria(Long convocatoriaId) {
        Convocatoria convocatoria = convocatoriaRepository.findById(convocatoriaId)
                .orElseThrow(() -> new IllegalArgumentException("Convocatoria no encontrada con ID: " + convocatoriaId));

        return convocatoria.getAlumnosConvocatoria().stream()
                .map(alumnoConvocatoria -> new AlumnoConvocatoriaDTO(
                        alumnoConvocatoria.getAlumno().getId(),
                        alumnoConvocatoria.getAlumno().getNombre(),
                        alumnoConvocatoria.getAlumno().getApellidos(),
                        alumnoConvocatoria.getCuantiaExamen(),
                        alumnoConvocatoria.getGradoActual(),
                        alumnoConvocatoria.getGradoSiguiente(),
                        alumnoConvocatoria.getPagado()
                ))
                .collect(Collectors.toList());
    }
    
    private ConvocatoriaDTO convertirAConvocatoriaDTO(Convocatoria convocatoria) {
        ConvocatoriaDTO dto = new ConvocatoriaDTO();
        dto.setId(convocatoria.getId());
        dto.setFechaConvocatoria(convocatoria.getFechaConvocatoria());
        dto.setDeporte(convocatoria.getDeporte());
        return dto;
    }
}
