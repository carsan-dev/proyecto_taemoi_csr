package com.taemoi.project.entities;

import java.util.Date;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.validation.constraints.NotNull;

@Entity
public class AlumnoConvocatoria {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "alumno_id")
	private Alumno alumno;

	@ManyToOne
	@JoinColumn(name = "convocatoria_id")
	private Convocatoria convocatoria;

	@ManyToOne
	@JoinColumn(name = "producto_alumno_id")
	private ProductoAlumno productoAlumno;

	@NotNull(message = "La cuantía del examen no puede ser nula")
	private Double cuantiaExamen;

	@NotNull(message = "El campo pagado no puede ser nulo")
	private Boolean pagado = false;

	@Temporal(TemporalType.DATE)
	private Date fechaPago;

	@Enumerated(EnumType.STRING)
	private TipoGrado gradoActual;

	@Enumerated(EnumType.STRING)
	private TipoGrado gradoSiguiente;

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

	public Convocatoria getConvocatoria() {
		return convocatoria;
	}

	public void setConvocatoria(Convocatoria convocatoria) {
		this.convocatoria = convocatoria;
	}

	public ProductoAlumno getProductoAlumno() {
		return productoAlumno;
	}

	public void setProductoAlumno(ProductoAlumno productoAlumno) {
		this.productoAlumno = productoAlumno;
	}

	public Double getCuantiaExamen() {
		return cuantiaExamen;
	}

	public void setCuantiaExamen(Double cuantiaExamen) {
		this.cuantiaExamen = cuantiaExamen;
	}

	public Boolean getPagado() {
		return pagado;
	}

	public void setPagado(Boolean pagado) {
		this.pagado = pagado;
	}

	public Date getFechaPago() {
		return fechaPago;
	}

	public void setFechaPago(Date fechaPago) {
		this.fechaPago = fechaPago;
	}

	public TipoGrado getGradoActual() {
		return gradoActual;
	}

	public void setGradoActual(TipoGrado gradoActual) {
		this.gradoActual = gradoActual;
	}

	public TipoGrado getGradoSiguiente() {
		return gradoSiguiente;
	}

	public void setGradoSiguiente(TipoGrado gradoSiguiente) {
		this.gradoSiguiente = gradoSiguiente;
	}
}
