package com.taemoi.project.entities;

import java.time.LocalDateTime;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "producto_alumno")
public class ProductoAlumno extends EntidadOrganizacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "producto_id")
	private Producto producto;

	// Relación con Alumno (DEPRECATED: usar alumnoDeporte)
	@ManyToOne
	@JoinColumn(name = "alumno_id")
	@JsonIgnore
	private Alumno alumno;

	// NUEVO: Relación con AlumnoDeporte (para productos específicos por deporte)
	@ManyToOne
	@JoinColumn(name = "alumno_deporte_id")
	@JsonIgnore
	private AlumnoDeporte alumnoDeporte;

	private String concepto;

	private Date fechaAsignacion;

	private Integer cantidad;

	private Double precio;

	private Boolean pagado;

	private Date fechaPago;

	private String notas;

	/**
	 * Sede a la que se imputa el cargo (V31_3, entrega 6-3, D6-9): la sede principal del alumno AL CREARLO. Se fija
	 * en {@link #fijarSedeAlCrear()} (cubre todos los puntos que crean cargos) y nunca cambia ({@code updatable =
	 * false}): cambiar al alumno de sede no mueve sus cargos antiguos. NULL si el alumno no tiene sede principal (solo
	 * la gestión lo ve, en «Sin sede»). Sin consultas: en una escuela de una sola sede todo alumno tiene sede principal
	 * (alta 6-1, finalización 6-2 y V31_2), y los postchecks de V31_2 y V31_3 lo vigilan. Fuera del JSON de la
	 * entidad; los DTO la exponen como {@code sedeId}.
	 */
	@JsonIgnore
	@Column(name = "sede_id", updatable = false)
	private Long sedeId;

	/**
	 * Pieza 6b-2c (V31_6): cuándo se canceló el cargo; NULL = vigente. Invariante: cancelado ⇒ no pagado y sin cobro
	 * ACTIVO (solo se cancelan cargos pendientes). Un cargo cancelado no cuenta como pendiente ni como deuda en ningún
	 * lector, pero SÍ cuenta como existente para la generación de mensualidades y licencias (no se regenera). Fuera del
	 * JSON de la entidad (/api/admin/{id}); los DTO lo exponen solo si está cancelado.
	 */
	@JsonIgnore
	@Column(name = "cancelado_en")
	private LocalDateTime canceladoEn;

	/** Pieza 6b-2c: quién lo canceló (FK a usuario, ON DELETE SET NULL). */
	@JsonIgnore
	@Column(name = "cancelado_por_usuario_id")
	private Long canceladoPorUsuarioId;

	/** Pieza 6b-2c: por qué se canceló (obligatorio al cancelar, hasta 500 caracteres). */
	@JsonIgnore
	@Column(name = "motivo_cancelacion", length = 500)
	private String motivoCancelacion;

	/**
	 * Entrega 6-3 (D6-9): si nadie fijó la sede, la sede principal del alumno del cargo en este momento. El alumno se
	 * resuelve como en la tesorería: {@code COALESCE(alumno, alumnoDeporte.alumno)}. Sin acceso a la base de datos.
	 */
	@PrePersist
	void fijarSedeAlCrear() {
		if (sedeId != null) {
			return;
		}
		Alumno delCargo = alumno != null ? alumno : (alumnoDeporte != null ? alumnoDeporte.getAlumno() : null);
		if (delCargo != null) {
			sedeId = delCargo.getSedePrincipalId();
		}
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getConcepto() {
		return concepto;
	}

	public void setConcepto(String concepto) {
		this.concepto = concepto;
	}

	public Producto getProducto() {
		return producto;
	}

	public void setProducto(Producto producto) {
		this.producto = producto;
	}

	public Alumno getAlumno() {
		return alumno;
	}

	public void setAlumno(Alumno alumno) {
		this.alumno = alumno;
	}

	public Integer getCantidad() {
		return cantidad;
	}

	public void setCantidad(Integer cantidad) {
		this.cantidad = cantidad;
	}

	public Double getPrecio() {
		return precio;
	}

	public void setPrecio(Double precio) {
		this.precio = precio;
	}

	public Boolean getPagado() {
		return pagado;
	}

	public void setPagado(Boolean pagado) {
		this.pagado = pagado;
	}

	public Date getFechaAsignacion() {
		return fechaAsignacion;
	}

	public void setFechaAsignacion(Date fechaAsignacion) {
		this.fechaAsignacion = fechaAsignacion;
	}

	public Date getFechaPago() {
		return fechaPago;
	}

	public void setFechaPago(Date fechaPago) {
		this.fechaPago = fechaPago;
	}

	public String getNotas() {
		return notas;
	}

	public void setNotas(String notas) {
		this.notas = notas;
	}

	@JsonIgnore
	public Long getSedeId() {
		return sedeId;
	}

	/** Solo antes de guardarlo por primera vez (la columna no se actualiza). */
	public void setSedeId(Long sedeId) {
		this.sedeId = sedeId;
	}

	@JsonIgnore
	public LocalDateTime getCanceladoEn() {
		return canceladoEn;
	}

	public void setCanceladoEn(LocalDateTime canceladoEn) {
		this.canceladoEn = canceladoEn;
	}

	@JsonIgnore
	public Long getCanceladoPorUsuarioId() {
		return canceladoPorUsuarioId;
	}

	public void setCanceladoPorUsuarioId(Long canceladoPorUsuarioId) {
		this.canceladoPorUsuarioId = canceladoPorUsuarioId;
	}

	@JsonIgnore
	public String getMotivoCancelacion() {
		return motivoCancelacion;
	}

	public void setMotivoCancelacion(String motivoCancelacion) {
		this.motivoCancelacion = motivoCancelacion;
	}

	/** Pieza 6b-2c: el cargo está cancelado ({@code cancelado_en} no nulo). */
	@JsonIgnore
	public boolean estaCancelado() {
		return canceladoEn != null;
	}

	public AlumnoDeporte getAlumnoDeporte() {
		return alumnoDeporte;
	}

	public void setAlumnoDeporte(AlumnoDeporte alumnoDeporte) {
		this.alumnoDeporte = alumnoDeporte;
	}
}
