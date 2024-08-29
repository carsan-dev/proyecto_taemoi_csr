package com.taemoi.project.entidades;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
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

    private String tipo;

    @ManyToOne
    @JoinColumn(name = "grupo_id")
    @JsonManagedReference
    private Grupo grupo;

    @ManyToMany(mappedBy = "turnos", fetch = FetchType.EAGER)
    @JsonBackReference
    private List<Alumno> alumnos = new ArrayList<>();

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

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public Grupo getGrupo() {
        return grupo;
    }

    public void setGrupo(Grupo grupo) {
        this.grupo = grupo;
    }

    public List<Alumno> getAlumnos() {
        return alumnos;
    }

    public void setAlumnos(List<Alumno> alumnos) {
        this.alumnos = alumnos;
    }
    
    public String getTurno() {
        return "De " + horaInicio + " a " + horaFin;
    }

    public String getTipoTurno() {
        if ("17:00 a 18:00".equals(getTurno())) {
            return "Infantil";
        } else if ("18:00 a 19:00".equals(getTurno())) {
            return "Joven";
        } else if ("19:00 a 20:00".equals(getTurno()) || "19:00 a 20:30".equals(getTurno())) {
            return "Adulto";
        } else if ("20:00 a 21:30".equals(getTurno())) {
            return "Competición";
        } else {
            return "No definido";
        }
    }
}
