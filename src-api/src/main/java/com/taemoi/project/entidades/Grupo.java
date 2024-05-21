package com.taemoi.project.entidades;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonBackReference;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotBlank;

@Entity
public class Grupo {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@NotBlank(message = "El nombre no puede estar en blanco")
	private String nombre;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "alumno_grupo",
        joinColumns = @JoinColumn(name = "grupo_id"),
        inverseJoinColumns = @JoinColumn(name = "alumno_id")
    )
    @JsonBackReference
    private List<Alumno> alumnos;
    
    @OneToMany(mappedBy = "grupo")
    @JsonBackReference
    private List<Turno> turnos;

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
	
	public List<Alumno> getAlumnos() {
		return alumnos;
	}

	public void setAlumnos(List<Alumno> alumnos) {
		this.alumnos = alumnos;
	}
	
	public List<Turno> getTurnos() {
		return turnos;
	}

	public void setTurnos(List<Turno> turnos) {
		this.turnos = turnos;
	}

	public void addAlumno(Alumno alumno) {
        if (alumno != null) {
            alumnos.add(alumno);
            alumno.getGrupos().add(this);
        }
    }

    public void removeAlumno(Alumno alumno) {
        if (alumno != null) {
            alumnos.remove(alumno);
            alumno.getGrupos().remove(this);
        }
    }
}