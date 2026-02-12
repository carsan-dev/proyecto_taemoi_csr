package com.taemoi.project.entities;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

@Entity
@Table(name = "auditoria_evento", indexes = {
		@Index(name = "idx_auditoria_evento_fecha", columnList = "fechaEvento"),
		@Index(name = "idx_auditoria_evento_accion", columnList = "accion"),
		@Index(name = "idx_auditoria_evento_modulo", columnList = "modulo"),
		@Index(name = "idx_auditoria_evento_usuario_email", columnList = "usuarioEmail")
})
public class AuditoriaEvento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Temporal(TemporalType.TIMESTAMP)
	@Column(nullable = false)
	private Date fechaEvento;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private AuditoriaAccion accion;

	@Column(nullable = false, length = 8)
	private String metodoHttp;

	@Column(nullable = false, length = 512)
	private String endpoint;

	@Column(length = 80)
	private String modulo;

	private Long recursoId;

	@Column(nullable = false)
	private Integer estadoHttp;

	private Long usuarioId;

	@Column(length = 160)
	private String usuarioEmail;

	@Column(length = 180)
	private String usuarioNombre;

	@Column(length = 120)
	private String ipCliente;

	@Column(length = 400)
	private String userAgent;

	@Lob
	@Column(columnDefinition = "TEXT")
	private String queryParamsJson;

	@Lob
	@Column(columnDefinition = "TEXT")
	private String payloadJson;

	@Column(nullable = false)
	private Boolean payloadTruncado = Boolean.FALSE;

	@Column(length = 500)
	private String resumen;

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

	public AuditoriaAccion getAccion() {
		return accion;
	}

	public void setAccion(AuditoriaAccion accion) {
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

	public String getIpCliente() {
		return ipCliente;
	}

	public void setIpCliente(String ipCliente) {
		this.ipCliente = ipCliente;
	}

	public String getUserAgent() {
		return userAgent;
	}

	public void setUserAgent(String userAgent) {
		this.userAgent = userAgent;
	}

	public String getQueryParamsJson() {
		return queryParamsJson;
	}

	public void setQueryParamsJson(String queryParamsJson) {
		this.queryParamsJson = queryParamsJson;
	}

	public String getPayloadJson() {
		return payloadJson;
	}

	public void setPayloadJson(String payloadJson) {
		this.payloadJson = payloadJson;
	}

	public Boolean getPayloadTruncado() {
		return payloadTruncado;
	}

	public void setPayloadTruncado(Boolean payloadTruncado) {
		this.payloadTruncado = payloadTruncado;
	}

	public String getResumen() {
		return resumen;
	}

	public void setResumen(String resumen) {
		this.resumen = resumen;
	}
}
