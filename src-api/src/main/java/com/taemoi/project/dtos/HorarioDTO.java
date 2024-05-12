package com.taemoi.project.dtos;

import com.taemoi.project.entidades.Grupo;
import com.taemoi.project.entidades.Horario;

public class HorarioDTO {
    
    private Long id;

    private int dia;

    private String turno;

    private Grupo grupo;

    public HorarioDTO() {
    }

    public HorarioDTO(int dia, String turno, Grupo grupo) {
        this.dia = dia;
        this.turno = turno;
        this.grupo = grupo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getDia() {
        return dia;
    }

    public void setDia(int dia) {
        this.dia = dia;
    }

    public String getTurno() {
        return turno;
    }

    public void setTurno(String turno) {
        this.turno = turno;
    }

    public Grupo getGrupo() {
        return grupo;
    }

    public void setGrupo(Grupo grupo) {
        this.grupo = grupo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HorarioDTO horario = (HorarioDTO) o;

        if (dia != horario.dia) return false;
        return turno != null ? turno.equals(horario.turno) : horario.turno == null;
    }

    @Override
    public int hashCode() {
        int result = dia;
        result = 31 * result + (turno != null ? turno.hashCode() : 0);
        return result;
    }
}