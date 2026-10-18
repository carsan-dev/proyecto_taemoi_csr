package com.taemoi.project.entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Entrega de caja de una sede (pieza 6b-2, tabla {@code entrega_caja} de V31_5): un profesor entrega el efectivo que
 * cobró y quien lo recibe (responsable de esa sede, gestor o propietario) registra lo declarado (Σ de los cobros) y lo
 * contado. Solo la crea y la anula {@code CajaService}. Ids simples, como {@link Cobro}. No se expone en JSON.
 *
 * <p>{@code fecha} es la hora local de la escuela (Europe/Madrid) al registrarla: el resumen de caja filtra por ella.
 */
@Entity
@Table(name = "entrega_caja")
public class EntregaCaja extends EntidadOrganizacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "sede_id", nullable = false)
	private Long sedeId;

	@Column(name = "entregado_por_usuario_id")
	private Long entregadoPorUsuarioId;

	@Column(name = "recibido_por_usuario_id")
	private Long recibidoPorUsuarioId;

	@Column(name = "importe_declarado", nullable = false, precision = 10, scale = 2)
	private BigDecimal importeDeclarado;

	@Column(name = "importe_contado", nullable = false, precision = 10, scale = 2)
	private BigDecimal importeContado;

	@Column(nullable = false)
	private LocalDateTime fecha;

	@Column(length = 500)
	private String nota;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private EstadoEntregaCaja estado = EstadoEntregaCaja.ACTIVA;

	@Column(name = "anulada_por_usuario_id")
	private Long anuladaPorUsuarioId;

	@Column(name = "anulada_en")
	private LocalDateTime anuladaEn;

	@Column(name = "motivo_anulacion", length = 500)
	private String motivoAnulacion;

	@Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
	private LocalDateTime creadoEn;

	@Version
	@Column(nullable = false)
	private Long version;

	public Long getId() {
		return id;
	}

	public Long getSedeId() {
		return sedeId;
	}

	public void setSedeId(Long sedeId) {
		this.sedeId = sedeId;
	}

	public Long getEntregadoPorUsuarioId() {
		return entregadoPorUsuarioId;
	}

	public void setEntregadoPorUsuarioId(Long entregadoPorUsuarioId) {
		this.entregadoPorUsuarioId = entregadoPorUsuarioId;
	}

	public Long getRecibidoPorUsuarioId() {
		return recibidoPorUsuarioId;
	}

	public void setRecibidoPorUsuarioId(Long recibidoPorUsuarioId) {
		this.recibidoPorUsuarioId = recibidoPorUsuarioId;
	}

	public BigDecimal getImporteDeclarado() {
		return importeDeclarado;
	}

	public void setImporteDeclarado(BigDecimal importeDeclarado) {
		this.importeDeclarado = importeDeclarado;
	}

	public BigDecimal getImporteContado() {
		return importeContado;
	}

	public void setImporteContado(BigDecimal importeContado) {
		this.importeContado = importeContado;
	}

	/** Contado − declarado: positivo, sobra dinero; negativo, falta. */
	public BigDecimal getDiferencia() {
		return importeContado.subtract(importeDeclarado);
	}

	public LocalDateTime getFecha() {
		return fecha;
	}

	public void setFecha(LocalDateTime fecha) {
		this.fecha = fecha;
	}

	public String getNota() {
		return nota;
	}

	public void setNota(String nota) {
		this.nota = nota;
	}

	public EstadoEntregaCaja getEstado() {
		return estado;
	}

	public void setEstado(EstadoEntregaCaja estado) {
		this.estado = estado;
	}

	public Long getAnuladaPorUsuarioId() {
		return anuladaPorUsuarioId;
	}

	public void setAnuladaPorUsuarioId(Long anuladaPorUsuarioId) {
		this.anuladaPorUsuarioId = anuladaPorUsuarioId;
	}

	public LocalDateTime getAnuladaEn() {
		return anuladaEn;
	}

	public void setAnuladaEn(LocalDateTime anuladaEn) {
		this.anuladaEn = anuladaEn;
	}

	public String getMotivoAnulacion() {
		return motivoAnulacion;
	}

	public void setMotivoAnulacion(String motivoAnulacion) {
		this.motivoAnulacion = motivoAnulacion;
	}

	public LocalDateTime getCreadoEn() {
		return creadoEn;
	}

	public Long getVersion() {
		return version;
	}
}
