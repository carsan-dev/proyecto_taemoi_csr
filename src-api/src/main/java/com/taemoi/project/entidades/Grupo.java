package com.taemoi.project.entidades;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Entity
public class Grupo {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "alumno_id")
	private Alumno alumno;

	@NotBlank(message = "El número de expediente del alumno no puede estar en blanco")
	@Column(name = "numero_expediente_alumno")
	private String numeroExpedienteAlumno;

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

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Alumno getAlumno() {
		return alumno;
	}

	public void setAlumno(Alumno alumno) {
		this.alumno = alumno;
	}

	public String getNumeroExpedienteAlumno() {
		return numeroExpedienteAlumno;
	}

	public void setNumeroExpedienteAlumno(String numeroExpedienteAlumno) {
		this.numeroExpedienteAlumno = numeroExpedienteAlumno;
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
		actualizarHorasPorTurno(turno);
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

	private void actualizarHorasPorTurno(String turno) {
		// Verificar si el formato del turno es correcto
		if (turno != null && turno.matches("TURNO DE \\d{2}:\\d{2} A \\d{2}:\\d{2}")) {
			// Extraer las horas de inicio y fin del turno
			String[] partes = turno.split(" ");
			String horaInicioFin = partes[3]; // "hh:mm A hh:mm"
			String[] horas = horaInicioFin.split(" A ");

			// Actualizar las horas en la entidad Grupo
			this.horaInicio = horas[0];
			this.horaFin = horas[1];
		} else {
			// Manejar el caso en que el formato del turno no sea correcto
			throw new IllegalArgumentException("Formato de turno no válido: " + turno);
		}
	}

	// Equals y hashCode para comparar objetos Grupo en caso de querer mapearlos
	// fácilmente

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		Grupo grupo = (Grupo) o;
		return Objects.equals(alumno, grupo.alumno) && diaSemana == grupo.diaSemana
				&& Objects.equals(turno, grupo.turno);
	}

	@Override
	public int hashCode() {
		return Objects.hash(alumno, diaSemana, turno);
	}

}