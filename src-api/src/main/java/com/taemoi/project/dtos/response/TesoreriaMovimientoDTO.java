package com.taemoi.project.dtos.response;

import java.util.Date;

public class TesoreriaMovimientoDTO {

	private Long productoAlumnoId;
	private Long alumnoId;
	private String alumnoNombreCompleto;
	private String deporte;
	private String concepto;
	private String categoria;
	private Date fechaAsignacion;
	private Boolean pagado;
	private Date fechaPago;
	private Double precio;
	private String notas;

	public Long getProductoAlumnoId() {
		return productoAlumnoId;
	}

	public void setProductoAlumnoId(Long productoAlumnoId) {
		this.productoAlumnoId = productoAlumnoId;
	}

	public Long getAlumnoId() {
		return alumnoId;
	}

	public void setAlumnoId(Long alumnoId) {
		this.alumnoId = alumnoId;
	}

	public String getAlumnoNombreCompleto() {
		return alumnoNombreCompleto;
	}

	public void setAlumnoNombreCompleto(String alumnoNombreCompleto) {
		this.alumnoNombreCompleto = alumnoNombreCompleto;
	}

	public String getDeporte() {
		return deporte;
	}

	public void setDeporte(String deporte) {
		this.deporte = deporte;
	}

	public String getConcepto() {
		return concepto;
	}

	public void setConcepto(String concepto) {
		this.concepto = concepto;
	}

	public String getCategoria() {
		return categoria;
	}

	public void setCategoria(String categoria) {
		this.categoria = categoria;
	}

	public Date getFechaAsignacion() {
		return fechaAsignacion;
	}

	public void setFechaAsignacion(Date fechaAsignacion) {
		this.fechaAsignacion = fechaAsignacion;
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

	public Double getPrecio() {
		return precio;
	}

	public void setPrecio(Double precio) {
		this.precio = precio;
	}

	public String getNotas() {
		return notas;
	}

	public void setNotas(String notas) {
		this.notas = notas;
	}
}
