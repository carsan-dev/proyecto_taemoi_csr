package com.taemoi.project.servicios.impl;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.taemoi.project.dtos.TurnoDTO;
import com.taemoi.project.dtos.response.TurnoCortoDTO;
import com.taemoi.project.entidades.Grupo;
import com.taemoi.project.entidades.Turno;
import com.taemoi.project.errores.turno.TurnoNoEncontradoException;
import com.taemoi.project.repositorios.GrupoRepository;
import com.taemoi.project.repositorios.TurnoRepository;
import com.taemoi.project.servicios.TurnoService;

/**
 * Implementación del servicio de turno que proporciona operaciones relacionadas con turnos
 * para los grupos.
 */
@Service
public class TurnoServiceImpl implements TurnoService {
	
	/**
     * Inyección del repositorio de turno.
     */
    @Autowired
    private TurnoRepository turnoRepository;

	/**
     * Inyección del repositorio de grupo.
     */
    @Autowired
    private GrupoRepository grupoRepository;
    
    /**
     * Obtiene una lista de todos los turnos disponibles.
     *
     * @return Una lista de objetos Turno.
     */
    @Override
    public List<Turno> listarTurnos() {
        return turnoRepository.findAll();
    }
    
    /**
     * Obtiene un turno por su ID.
     *
     * @param turnoId El ID del turno a obtener.
     * @return El objeto Turno correspondiente al ID especificado.
     * @throws TurnoNoEncontradoException Si no se encuentra el turno con el ID especificado.
     */
    @Override
    public Turno obtenerTurnoPorId(@NonNull Long turnoId) {
        return turnoRepository.findById(turnoId)
                .orElseThrow(() -> new TurnoNoEncontradoException("El turno con ID " + turnoId + " no existe."));
    }
    
    /**
     * Obtiene una lista de todos los turnos disponibles en forma de objetos DTO.
     *
     * @return Una lista de objetos TurnoDTO.
     */
    @Override
    public List<TurnoCortoDTO> listarTurnosDTO() {
        List<Turno> turnos = turnoRepository.findAll();
        return turnos.stream()
                .map(TurnoCortoDTO::deTurno)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<TurnoDTO> listarTurnosDTOCompleto() {
        List<Turno> turnos = turnoRepository.findAll();
        return turnos.stream()
                .map(TurnoDTO::deTurno) // Utiliza el método actualizado que incluye alumnos
                .collect(Collectors.toList());
    }

    
    /**
     * Crea un nuevo turno sin asignarlo a ningún grupo.
     *
     * @param turnoDTO Los datos del turno a crear.
     */
    @Override
    public void crearTurnoSinGrupo(TurnoDTO turnoDTO) {
        Turno turno = new Turno();
        turno.setDiaSemana(turnoDTO.getDiaSemana());
        turno.setHoraInicio(turnoDTO.getHoraInicio());
        turno.setHoraFin(turnoDTO.getHoraFin());
        
        turnoRepository.save(turno);
    }
    
    /**
     * Crea un nuevo turno y lo asigna a un grupo según el día de la semana.
     *
     * @param turnoDTO Los datos del turno a crear.
     */
    @Override
    public void crearTurnoYAsignarAGrupo(TurnoDTO turnoDTO) {
        Turno turno = new Turno();
        turno.setDiaSemana(turnoDTO.getDiaSemana());
        turno.setHoraInicio(turnoDTO.getHoraInicio());
        turno.setHoraFin(turnoDTO.getHoraFin());

        // Obtener el grupo por ID
        Grupo grupo = grupoRepository.findById(turnoDTO.getGrupoId())
            .orElseThrow(() -> new IllegalArgumentException("No se encontró el grupo con el ID: " + turnoDTO.getGrupoId()));

        // Usar el tipo de grupo directamente del frontend
        turno.setTipo(turnoDTO.getTipoGrupo());  // <- Aquí usamos el tipo de grupo del DTO

        // Asignar el grupo al turno
        turno.setGrupo(grupo);
        grupo.getTurnos().add(turno);

        // Guardar ambos objetos
        turnoRepository.save(turno);
        grupoRepository.save(grupo);
    }

    /**
     * Actualiza los detalles de un turno existente.
     *
     * @param turnoId  El ID del turno a actualizar.
     * @param turnoDTO Los nuevos datos del turno.
     * @return El objeto TurnoDTO actualizado.
     * @throws TurnoNoEncontradoException Si no se encuentra el turno con el ID especificado.
     */
    @Override
    public TurnoDTO actualizarTurno(@NonNull Long turnoId, TurnoDTO turnoDTO) {
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

    /**
     * Elimina un turno por su ID.
     *
     * @param turnoId El ID del turno a eliminar.
     * @throws TurnoNoEncontradoException Si no se encuentra el turno con el ID especificado.
     */
    @Override
    public boolean eliminarTurno(@NonNull Long turnoId) {
        Optional<Turno> turnoOptional = turnoRepository.findById(turnoId);
        if (turnoOptional.isPresent()) {
            turnoRepository.deleteById(turnoId);
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * Obtiene un grupo de la lista según su índice.
     *
     * @param grupos La lista de grupos disponibles.
     * @param indice El índice del grupo a obtener.
     * @return El grupo correspondiente al índice especificado.
     * @throws IllegalArgumentException Si no hay suficientes grupos para asignar el turno.
     */
    private Grupo obtenerGrupoPorIndice(List<Grupo> grupos, int indice) {
        if (grupos.size() > indice) {
            return grupos.get(indice);
        } else {
            throw new IllegalArgumentException("No hay suficientes grupos para asignar el turno");
        }
    }

}
