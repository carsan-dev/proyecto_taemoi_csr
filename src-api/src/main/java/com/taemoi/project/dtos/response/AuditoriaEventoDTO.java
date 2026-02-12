package com.taemoi.project.dtos.response;

import java.util.Date;

public class AuditoriaEventoDTO {

	private Long id;
	private Date fechaEvento;
	private String accion;
	private String metodoHttp;
	private String endpoint;
	private String modulo;
	private Long recursoId;
	private Integer estadoHttp;
	private Long usuarioId;
	private String usuarioEmail;
	private String usuarioNombre;
	private String resumen;
	private Boolean payloadTruncado;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Date getFechaEvento() {
		return fechaEvento;
	}

	public void setFechaEvento(Date fechaEvento) {
		this.fechaEvento = fechaEvento;
	}

	public String getAccion() {
		return accion;
	}

	public void setAccion(String accion) {
		this.accion = accion;
	}

	public String getMetodoHttp() {
		return metodoHttp;
	}

	public void setMetodoHttp(String metodoHttp) {
		this.metodoHttp = metodoHttp;
	}

	public String getEndpoint() {
		return endpoint;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public String getModulo() {
		return modulo;
	}

	public void setModulo(String modulo) {
		this.modulo = modulo;
	}

	public Long getRecursoId() {
		return recursoId;
	}

	public void setRecursoId(Long recursoId) {
		this.recursoId = recursoId;
	}

	public Integer getEstadoHttp() {
		return estadoHttp;
	}

	public void setEstadoHttp(Integer estadoHttp) {
		this.estadoHttp = estadoHttp;
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public void setUsuarioId(Long usuarioId) {
		this.usuarioId = usuarioId;
	}

	public String getUsuarioEmail() {
		return usuarioEmail;
	}

	public void setUsuarioEmail(String usuarioEmail) {
		this.usuarioEmail = usuarioEmail;
	}

	public String getUsuarioNombre() {
		return usuarioNombre;
	}

	public void setUsuarioNombre(String usuarioNombre) {
		this.usuarioNombre = usuarioNombre;
	}

	public String getResumen() {
		return resumen;
	}

	public void setResumen(String resumen) {
		this.resumen = resumen;
	}

	public Boolean getPayloadTruncado() {
		return payloadTruncado;
	}

	public void setPayloadTruncado(Boolean payloadTruncado) {
		this.payloadTruncado = payloadTruncado;
	}
}
