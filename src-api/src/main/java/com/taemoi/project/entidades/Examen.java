package com.taemoi.project.entidades;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class Examen {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "alumno_id")
    private Alumno alumno;

    @ManyToOne
    @JoinColumn(name = "grado_actual_id")
    private Grado gradoActual; // Grado que el alumno tiene actualmente

    @ManyToOne
    @JoinColumn(name = "grado_proximo_id")
    private Grado gradoProximo; // Grado al que va a pasar

    @ManyToOne
    @JoinColumn(name = "pago_id", nullable = true)
    private Pago pago; // Relación con el pago del examen

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

	public Grado getGradoActual() {
		return gradoActual;
	}

	public void setGradoActual(Grado gradoActual) {
		this.gradoActual = gradoActual;
	}

	public Grado getGradoProximo() {
		return gradoProximo;
	}

	public void setGradoProximo(Grado gradoProximo) {
		this.gradoProximo = gradoProximo;
	}

	public Pago getPago() {
		return pago;
	}

	public void setPago(Pago pago) {
		this.pago = pago;
	}
}
