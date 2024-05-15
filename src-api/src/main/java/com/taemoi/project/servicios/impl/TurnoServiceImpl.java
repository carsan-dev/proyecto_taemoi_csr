package com.taemoi.project.servicios.impl;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.taemoi.project.dtos.TurnoDTO;
import com.taemoi.project.entidades.Grupo;
import com.taemoi.project.entidades.Turno;
import com.taemoi.project.errores.turno.TurnoNoEncontradoException;
import com.taemoi.project.repositorios.GrupoRepository;
import com.taemoi.project.repositorios.TurnoRepository;
import com.taemoi.project.servicios.TurnoService;

@Service
public class TurnoServiceImpl implements TurnoService {
	
    @Autowired
    private TurnoRepository turnoRepository;

    @Autowired
    private GrupoRepository grupoRepository;
    
    @Override
    public List<Turno> listarTurnos() {
        return turnoRepository.findAll();
    }
    
    @Override
    public List<TurnoDTO> listarTurnosDTO() {
        List<Turno> turnos = turnoRepository.findAll();
        return turnos.stream()
                .map(TurnoDTO::deTurno)
                .collect(Collectors.toList());
    }
    
    @Override
    public void crearTurnoSinGrupo(TurnoDTO turnoDTO) {
        Turno turno = new Turno();
        turno.setDiaSemana(turnoDTO.getDiaSemana());
        turno.setHoraInicio(turnoDTO.getHoraInicio());
        turno.setHoraFin(turnoDTO.getHoraFin());
        
        turnoRepository.save(turno);
    }
    
    @Override
    public void crearTurnoYAsignarAGrupo(TurnoDTO turnoDTO) {
        Turno turno = new Turno();
        turno.setDiaSemana(turnoDTO.getDiaSemana());
        turno.setHoraInicio(turnoDTO.getHoraInicio());
        turno.setHoraFin(turnoDTO.getHoraFin());

        List<Grupo> grupos = grupoRepository.findAll();

        Grupo grupoAsignado;
        if (turno.getDiaSemana().equalsIgnoreCase("lunes") || turno.getDiaSemana().equalsIgnoreCase("miércoles")) {
            grupoAsignado = obtenerGrupoPorIndice(grupos, 0);
        } else if (turno.getDiaSemana().equalsIgnoreCase("martes") || turno.getDiaSemana().equalsIgnoreCase("jueves")) {
            grupoAsignado = obtenerGrupoPorIndice(grupos, 1);
        } else {
            throw new IllegalArgumentException("Día de la semana no válido: " + turno.getDiaSemana());
        }

        turno.setGrupo(grupoAsignado);

        turnoRepository.save(turno);
    }
    
    @Override
    public TurnoDTO actualizarTurno(Long turnoId, TurnoDTO turnoDTO) {
        Optional<Turno> turnoOptional = turnoRepository.findById(turnoId);
        if (turnoOptional.isPresent()) {
            Turno turno = turnoOptional.get();

            if (!turno.getDiaSemana().equalsIgnoreCase(turnoDTO.getDiaSemana())) {
                if (turno.getGrupo() != null) {
                    List<Grupo> grupos = grupoRepository.findAll();
                    Grupo nuevoGrupoAsignado;
                    if (turnoDTO.getDiaSemana().equalsIgnoreCase("lunes") || turnoDTO.getDiaSemana().equalsIgnoreCase("miércoles")) {
                        nuevoGrupoAsignado = obtenerGrupoPorIndice(grupos, 0);
                    } else if (turnoDTO.getDiaSemana().equalsIgnoreCase("martes") || turnoDTO.getDiaSemana().equalsIgnoreCase("jueves")) {
                        nuevoGrupoAsignado = obtenerGrupoPorIndice(grupos, 1);
                    } else {
                        throw new IllegalArgumentException("Día de la semana no válido: " + turnoDTO.getDiaSemana());
                    }

                    turno.setGrupo(nuevoGrupoAsignado);
                }
            }

            turno.setDiaSemana(turnoDTO.getDiaSemana());
            turno.setHoraInicio(turnoDTO.getHoraInicio());
            turno.setHoraFin(turnoDTO.getHoraFin());
            turnoRepository.save(turno);
            
            return TurnoDTO.deTurno(turno);
        } else {
            throw new TurnoNoEncontradoException("El turno con ID " + turnoId + " no existe.");
        }
    }

    @Override
    public void eliminarTurno(Long turnoId) {
        if (turnoRepository.existsById(turnoId)) {
            turnoRepository.deleteById(turnoId);
        } else {
            throw new TurnoNoEncontradoException("El turno con ID " + turnoId + " no existe.");
        }
    }

    private Grupo obtenerGrupoPorIndice(List<Grupo> grupos, int indice) {
        if (grupos.size() > indice) {
            return grupos.get(indice);
        } else {
            throw new IllegalArgumentException("No hay suficientes grupos para asignar el turno");
        }
    }
}
