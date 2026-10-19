package com.taemoi.project.dtos;

import java.util.Date;

public class ProductoAlumnoDTO {
	private Long id;
	private Long productoId;
	private Long alumnoId;
	private String concepto;
	private Date fechaAsignacion;
	private Integer cantidad;
	private Double precio;
	private Boolean pagado;
	private Date fechaPago;
	private String notas;
	private String motivoCambio;
	/**
	 * Entrega 6-3 (D6-9), aditivo y solo de salida: sede del cargo, fijada al crearlo. Se ignora si llega en una
	 * petición (la sede no se elige ni se cambia).
	 */
	@com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
	private Long sedeId;

	/**
	 * Pieza 6b-1, opcional y solo de entrada: método con el que se cobra al pasar el cargo a pagado. Ausente o nulo:
	 * {@code SIN_INDICAR}; un valor que no existe, 400. No sale en las respuestas.
	 */
	@com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
	private com.taemoi.project.entities.MetodoPago metodoPago;

	/**
	 * Pieza 6b-2c, aditivos y solo de salida: cuándo, quién (id de la cuenta) y por qué se canceló el cargo. Solo salen
	 * si está cancelado ({@code NON_NULL}): la respuesta de un cargo vigente no cambia. Se ignoran si llegan en una
	 * petición (se cancela y reactiva por sus rutas).
	 */
	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	@com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
	private java.time.LocalDateTime canceladoEn;

	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	@com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
	private Long canceladoPorUsuarioId;

	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	@com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
	private String motivoCancelacion;

	public java.time.LocalDateTime getCanceladoEn() {
		return canceladoEn;
	}

	public void setCanceladoEn(java.time.LocalDateTime canceladoEn) {
		this.canceladoEn = canceladoEn;
	}

	public Long getCanceladoPorUsuarioId() {
		return canceladoPorUsuarioId;
	}

	public void setCanceladoPorUsuarioId(Long canceladoPorUsuarioId) {
		this.canceladoPorUsuarioId = canceladoPorUsuarioId;
	}

	public String getMotivoCancelacion() {
		return motivoCancelacion;
	}

	public void setMotivoCancelacion(String motivoCancelacion) {
		this.motivoCancelacion = motivoCancelacion;
	}

	public com.taemoi.project.entities.MetodoPago getMetodoPago() {
		return metodoPago;
	}

	public void setMetodoPago(com.taemoi.project.entities.MetodoPago metodoPago) {
		this.metodoPago = metodoPago;
	}

	public Long getSedeId() {
		return sedeId;
	}

	public void setSedeId(Long sedeId) {
		this.sedeId = sedeId;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getProductoId() {
		return productoId;
	}

	public void setProductoId(Long productoId) {
		this.productoId = productoId;
	}

	public Long getAlumnoId() {
		return alumnoId;
	}

	public void setAlumnoId(Long alumnoId) {
		this.alumnoId = alumnoId;
	}

	public String getConcepto() {
		return concepto;
	}

	public void setConcepto(String concepto) {
		this.concepto = concepto;
	}

	public Date getFechaAsignacion() {
		return fechaAsignacion;
	}

	public void setFechaAsignacion(Date fechaAsignacion) {
		this.fechaAsignacion = fechaAsignacion;
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

	public String getMotivoCambio() {
		return motivoCambio;
	}

	public void setMotivoCambio(String motivoCambio) {
		this.motivoCambio = motivoCambio;
	}

}
