package com.taemoi.project.entities;

import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Consentimiento firmado (o retirado) por un alumno o su tutor. Solo se añaden filas: una retirada es
 * una fila nueva con {@code otorgado = false}; el vigente de cada tipo es el de fecha más reciente.
 */
@Entity
@Immutable
@Table(name = "consentimiento")
public class Consentimiento extends EntidadOrganizacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "alumno_id", nullable = false, updatable = false)
	private Long alumnoId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false,
			columnDefinition = "ENUM('NORMAS','PRIMEROS_AUXILIOS','USO_IMAGEN','COMPROMISO_PAGO','PROTECCION_DATOS')")
	private TipoConsentimiento tipo;

	@Column(nullable = false, updatable = false)
	private Boolean otorgado;

	@Column(updatable = false)
	private LocalDate fecha;

	@Column(name = "fecha_aproximada", nullable = false, updatable = false)
	private Boolean fechaAproximada = Boolean.FALSE;

	@Column(name = "firmante_nombre", length = 180, updatable = false)
	private String firmanteNombre;

	@Enumerated(EnumType.STRING)
	@Column(name = "firmante_relacion", updatable = false, columnDefinition = "ENUM('PROPIO','TUTOR')")
	private RelacionFirmante firmanteRelacion;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, columnDefinition = "ENUM('FICHA_PAPEL','PREINSCRIPCION','APP')")
	private OrigenDato origen;

	@Column(name = "documento_id", updatable = false)
	private Long documentoId;

	@Column(name = "preinscripcion_id", updatable = false)
	private Long preinscripcionId;

	@Column(name = "registrado_en", nullable = false, updatable = false)
	private Instant registradoEn;

	protected Consentimiento() {
	}

	public Consentimiento(Long alumnoId, TipoConsentimiento tipo, boolean otorgado, OrigenDato origen) {
		this.alumnoId = alumnoId;
		this.tipo = tipo;
		this.otorgado = otorgado;
		this.origen = origen;
	}

	@PrePersist
	void alRegistrar() {
		if (registradoEn == null) {
			registradoEn = Instant.now();
		}
	}

	public Long getId() {
		return id;
	}

	public Long getAlumnoId() {
		return alumnoId;
	}

	public TipoConsentimiento getTipo() {
		return tipo;
	}

	public boolean isOtorgado() {
		return Boolean.TRUE.equals(otorgado);
	}

	public LocalDate getFecha() {
		return fecha;
	}

	public void setFecha(LocalDate fecha) {
		this.fecha = fecha;
	}

	public boolean isFechaAproximada() {
		return Boolean.TRUE.equals(fechaAproximada);
	}

	public void setFechaAproximada(boolean fechaAproximada) {
		this.fechaAproximada = fechaAproximada;
	}

	public String getFirmanteNombre() {
		return firmanteNombre;
	}

	public void setFirmanteNombre(String firmanteNombre) {
		this.firmanteNombre = firmanteNombre;
	}

	public RelacionFirmante getFirmanteRelacion() {
		return firmanteRelacion;
	}

	public void setFirmanteRelacion(RelacionFirmante firmanteRelacion) {
		this.firmanteRelacion = firmanteRelacion;
	}

	public OrigenDato getOrigen() {
		return origen;
	}

	public Long getDocumentoId() {
		return documentoId;
	}

	public void setDocumentoId(Long documentoId) {
		this.documentoId = documentoId;
	}

	public Long getPreinscripcionId() {
		return preinscripcionId;
	}

	public void setPreinscripcionId(Long preinscripcionId) {
		this.preinscripcionId = preinscripcionId;
	}

	public Instant getRegistradoEn() {
		return registradoEn;
	}
}
