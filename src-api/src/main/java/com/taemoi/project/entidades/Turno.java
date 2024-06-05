package com.taemoi.project.entidades;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Entity
public class Turno {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El día de la semana no puede estar en blanco")
    private String diaSemana;

    @NotBlank(message = "La hora de inicio no puede estar en blanco")
    @Pattern(regexp = "^([01]\\d|2[0-3]):([0-5]\\d)$", message = "La hora de inicio debe estar en formato HH:mm")
    private String horaInicio;

    @NotBlank(message = "La hora de fin no puede estar en blanco")
    @Pattern(regexp = "^([01]\\d|2[0-3]):([0-5]\\d)$", message = "La hora de fin debe estar en formato HH:mm")
    private String horaFin;

    @ManyToOne
    @JoinColumn(name = "grupo_id")
    @JsonManagedReference
    private Grupo grupo;

    public Turno() {}

    public Turno(String diaSemana, String horaInicio, String horaFin, Grupo grupo) {
        this.diaSemana = diaSemana;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
        this.grupo = grupo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDiaSemana() {
        return diaSemana;
    }

    public void setDiaSemana(String diaSemana) {
        this.diaSemana = diaSemana;
    }

	public String getHoraInicio() {
        return horaInicio;
    }

    public void setHoraInicio(String horaInicio) {
        this.horaInicio = horaInicio;
    }

    public String getHoraFin() {
        return horaFin;
    }

    public void setHoraFin(String horaFin) {
        this.horaFin = horaFin;
    }

    public Grupo getGrupo() {
        return grupo;
    }

    public void setGrupo(Grupo grupo) {
        this.grupo = grupo;
    }
    
    public String getTurno() {
        return "De " + horaInicio + " a " + horaFin;
    }
}
