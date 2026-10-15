package com.taemoi.project.entities;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
 * Disciplina que ofrece una organización. Sustituye al antiguo enum {@code Deporte} (V28): {@code codigo}
 * conserva sus valores ({@code TAEKWONDO}…). Sin {@link SistemaGrados} = disciplina sin grados.
 */
@Entity
@Table(name = "disciplina")
public class Disciplina extends EntidadOrganizacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 40)
	private String codigo;

	@Column(nullable = false, length = 100)
	private String nombre;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "sistema_grados_id")
	private SistemaGrados sistemaGrados;

	@Column(name = "permite_competicion", nullable = false)
	private Boolean permiteCompeticion = Boolean.FALSE;

	@Column(name = "requiere_licencia", nullable = false)
	private Boolean requiereLicencia = Boolean.FALSE;

	/** Edad que se cumple en el año natural a partir de la cual el alumno es adulto. */
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "edad_adulto", nullable = false)
	private Integer edadAdulto;

	@Column(nullable = false)
	private Integer orden = 0;

	@Column(nullable = false)
	private Boolean activa = Boolean.TRUE;

	/** Producto de licencia de la disciplina (V24). NULL en Taemoi: sus licencias tienen variantes. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "producto_licencia_id")
	private Producto productoLicencia;

	/** Producto de tarifa de competidor de la disciplina (V24). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "producto_competidor_id")
	private Producto productoCompetidor;

	public Long getId() {
		return id;
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

	public SistemaGrados getSistemaGrados() {
		return sistemaGrados;
	}

	public void setSistemaGrados(SistemaGrados sistemaGrados) {
		this.sistemaGrados = sistemaGrados;
	}

	public boolean tieneGrados() {
		return sistemaGrados != null;
	}

	public Boolean getPermiteCompeticion() {
		return permiteCompeticion;
	}

	public void setPermiteCompeticion(Boolean permiteCompeticion) {
		this.permiteCompeticion = permiteCompeticion;
	}

	public Boolean getRequiereLicencia() {
		return requiereLicencia;
	}

	public void setRequiereLicencia(Boolean requiereLicencia) {
		this.requiereLicencia = requiereLicencia;
	}

	public Integer getEdadAdulto() {
		return edadAdulto;
	}

	public void setEdadAdulto(Integer edadAdulto) {
		this.edadAdulto = edadAdulto;
	}

	public Integer getOrden() {
		return orden;
	}

	public void setOrden(Integer orden) {
		this.orden = orden;
	}

	public Boolean getActiva() {
		return activa;
	}

	public void setActiva(Boolean activa) {
		this.activa = activa;
	}

	public Producto getProductoLicencia() {
		return productoLicencia;
	}

	public void setProductoLicencia(Producto productoLicencia) {
		this.productoLicencia = productoLicencia;
	}

	public Producto getProductoCompetidor() {
		return productoCompetidor;
	}

	public void setProductoCompetidor(Producto productoCompetidor) {
		this.productoCompetidor = productoCompetidor;
	}
}
