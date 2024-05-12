package com.taemoi.project.entidades;

import java.util.List;
import java.util.Objects;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Entity
public class Horario {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Min(value = 1, message = "El día de la semana debe ser al menos 1")
	@Max(value = 7, message = "El día de la semana debe ser como máximo 7")
	private int diaSemana;

	@NotBlank(message = "El día no puede estar en blanco")
	private String dia;

	@NotBlank(message = "El turno no puede estar en blanco")
	private String turno;

	@NotBlank(message = "La hora de inicio no puede estar en blanco")
	@Pattern(regexp = "^(0[0-9]|1[0-9]|2[0-3]):[0-5][0-9]$", message = "Formato de hora no válido")
	private String horaInicio;

	@NotBlank(message = "La hora de fin no puede estar en blanco")
	@Pattern(regexp = "^(0[0-9]|1[0-9]|2[0-3]):[0-5][0-9]$", message = "Formato de hora no válido")
	private String horaFin;
	
    @OneToOne
    private Grupo grupo;
	
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public int getDiaSemana() {
		return diaSemana;
	}

	public void setDiaSemana(int diaSemana) {
		this.diaSemana = diaSemana;
	}

	public String getDia() {
		return dia;
	}

	public void setDia(String dia) {
		this.dia = dia;
	}

	public String getTurno() {
		return turno;
	}

	public void setTurno(String turno) {
		this.turno = turno;
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

	// Equals y hashCode para comparar objetos Horario en caso de querer mapearlos
	// fácilmente

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		Horario horario = (Horario) o;
		return Objects.equals(grupo, horario.grupo) && diaSemana == horario.diaSemana
				&& Objects.equals(turno, horario.turno);
	}

	@Override
	public int hashCode() {
		return Objects.hash(grupo, diaSemana, turno);
	}
}
