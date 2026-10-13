package com.taemoi.project.entities;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Tarifa de una organización. Sustituirá a {@link TipoTarifa} + {@code TarifaConfig}:
 * {@code codigo} conserva los valores del enum ({@code ADULTO}…).
 *
 * <p>{@code importe} es la cuantía general. {@code importeAdulto} / {@code importeMenor} solo
 * existen en tarifas cuyo precio depende del rol familiar ({@code PADRES_HIJOS} en Taemoi):
 * progenitor ({@code PADRE}/{@code MADRE}) e hijo ({@code HIJO}/{@code HIJA}). La regla está en
 * {@code TarifasService}. Sin disciplina = válida para cualquier disciplina.
 */
@Entity
@Table(name = "tarifa")
public class Tarifa extends EntidadOrganizacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "disciplina_id")
	private Disciplina disciplina;

	@Column(nullable = false, length = 40)
	private String codigo;

	@Column(nullable = false, length = 100)
	private String nombre;

	@Column(nullable = false, precision = 8, scale = 2)
	private BigDecimal importe;

	/** Importe del progenitor (PADRE/MADRE) en tarifas por rol familiar; NULL si no aplica. */
	@Column(name = "importe_adulto", precision = 8, scale = 2)
	private BigDecimal importeAdulto;

	/** Importe del hijo (HIJO/HIJA) en tarifas por rol familiar; NULL si no aplica. */
	@Column(name = "importe_menor", precision = 8, scale = 2)
	private BigDecimal importeMenor;

	@Column(nullable = false)
	private Boolean activa = Boolean.TRUE;

	public Long getId() {
		return id;
	}

	public Disciplina getDisciplina() {
		return disciplina;
	}

	public void setDisciplina(Disciplina disciplina) {
		this.disciplina = disciplina;
	}

	public String getCodigo() {
		return codigo;
	}

	public void setCodigo(String codigo) {
		this.codigo = codigo;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
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

	public Boolean getActiva() {
		return activa;
	}

	public void setActiva(Boolean activa) {
		this.activa = activa;
	}
}
