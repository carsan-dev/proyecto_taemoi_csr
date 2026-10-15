package com.taemoi.project.entities;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.validation.constraints.NotNull;

@Entity
public class AlumnoConvocatoria extends EntidadOrganizacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// DEPRECATED: Mantenido temporalmente para migración y rollback
	@ManyToOne
	@JoinColumn(name = "alumno_id")
	private Alumno alumno;

	// NUEVO: Relación con AlumnoDeporte (vincula examen a deporte específico)
	@ManyToOne
	@JoinColumn(name = "alumno_deporte_id")
	private AlumnoDeporte alumnoDeporte;

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

	/** Código del grado actual (texto; V28 lo pasa de ENUM de TipoGrado a VARCHAR para cualquier escuela). */
	@Column(name = "grado_actual", length = 40)
	private String gradoActual;

	/** Código del grado siguiente (texto, como {@link #gradoActual}). */
	@Column(name = "grado_siguiente", length = 40)
	private String gradoSiguiente;

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

	public String getGradoActual() {
		return gradoActual;
	}

	public void setGradoActual(String gradoActual) {
		this.gradoActual = gradoActual;
	}

	public String getGradoSiguiente() {
		return gradoSiguiente;
	}

	public void setGradoSiguiente(String gradoSiguiente) {
		this.gradoSiguiente = gradoSiguiente;
	}

	public AlumnoDeporte getAlumnoDeporte() {
		return alumnoDeporte;
	}

	public void setAlumnoDeporte(AlumnoDeporte alumnoDeporte) {
		this.alumnoDeporte = alumnoDeporte;
	}
}
