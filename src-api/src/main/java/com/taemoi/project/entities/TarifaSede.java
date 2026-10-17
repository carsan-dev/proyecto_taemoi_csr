package com.taemoi.project.entities;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Excepción de una tarifa del catálogo en una sede desde un mes (V31_4, entrega 6-4, D6-27 a D6-36). Solo cambia lo que
 * se cobra en las mensualidades de los alumnos con la cuantía base de la tarifa ({@code TarifaSedeService}).
 * {@code vigenteDesde} es siempre el día 1 del mes; no hay fin (D6-36): para volver al catálogo se añade otra fila.
 * Ids simples ({@code tarifaId}, {@code sedeId}), como {@code Alumno.sedePrincipalId}: el servicio comprueba que las dos
 * son de la escuela activa.
 */
@Entity
@Table(name = "tarifa_sede")
public class TarifaSede extends EntidadOrganizacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tarifa_id", nullable = false)
	private Long tarifaId;

	@Column(name = "sede_id", nullable = false)
	private Long sedeId;

	@Column(nullable = false, precision = 8, scale = 2)
	private BigDecimal importe;

	@Column(name = "importe_adulto", precision = 8, scale = 2)
	private BigDecimal importeAdulto;

	@Column(name = "importe_menor", precision = 8, scale = 2)
	private BigDecimal importeMenor;

	@Column(name = "vigente_desde", nullable = false)
	private LocalDate vigenteDesde;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getTarifaId() {
		return tarifaId;
	}

	public void setTarifaId(Long tarifaId) {
		this.tarifaId = tarifaId;
	}

	public Long getSedeId() {
		return sedeId;
	}

	public void setSedeId(Long sedeId) {
		this.sedeId = sedeId;
	}

	public BigDecimal getImporte() {
		return importe;
	}

	public void setImporte(BigDecimal importe) {
		this.importe = importe;
	}

	public BigDecimal getImporteAdulto() {
		return importeAdulto;
	}

	public void setImporteAdulto(BigDecimal importeAdulto) {
		this.importeAdulto = importeAdulto;
	}

	public BigDecimal getImporteMenor() {
		return importeMenor;
	}

	public void setImporteMenor(BigDecimal importeMenor) {
		this.importeMenor = importeMenor;
	}

	public LocalDate getVigenteDesde() {
		return vigenteDesde;
	}

	public void setVigenteDesde(LocalDate vigenteDesde) {
		this.vigenteDesde = vigenteDesde;
	}
}
