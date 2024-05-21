package com.taemoi.project.dtos.response;

import java.util.List;

import com.taemoi.project.dtos.TurnoDTO;

public class GrupoConTurnosDTO {
    private Long id;
    private String nombre;
    private List<TurnoDTO> turnos;
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getNombre() {
        return nombre;
    }
    
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }
    
    public List<TurnoDTO> getTurnos() {
        return turnos;
    }
    
    public void setTurnos(List<TurnoDTO> turnos) {
        this.turnos = turnos;
    }
}
