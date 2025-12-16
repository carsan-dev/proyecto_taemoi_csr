package com.taemoi.project.entities;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "alumno_deporte",
	uniqueConstraints = @UniqueConstraint(columnNames = {"alumno_id", "deporte"}),
	indexes = {
		@Index(name = "idx_alumno_deporte_alumno_id", columnList = "alumno_id"),
		@Index(name = "idx_alumno_deporte_deporte", columnList = "deporte"),
		@Index(name = "idx_alumno_deporte_apto", columnList = "deporte, apto_para_examen")
	})
public class AlumnoDeporte {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "alumno_id", nullable = false)
	@JsonIgnore
	private Alumno alumno;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	@NotNull(message = "El deporte no puede ser nulo")
	private Deporte deporte;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "grado_id")
	@JsonManagedReference
	private Grado grado;

	@Temporal(TemporalType.DATE)
	private Date fechaGrado;

	@Column(name = "apto_para_examen", nullable = false)
	@NotNull(message = "El campo aptoParaExamen no puede ser nulo")
	private Boolean aptoParaExamen = false;

	@Column(nullable = false)
	@NotNull(message = "El campo activo no puede ser nulo")
	private Boolean activo = true;

	@Temporal(TemporalType.DATE)
	private Date fechaAlta;

	@Temporal(TemporalType.DATE)
	private Date fechaAltaInicial;

	@Temporal(TemporalType.DATE)
	private Date fechaBaja;

	// Per-sport tarifa, competidor, and licencia fields
	@Enumerated(EnumType.STRING)
	private TipoTarifa tipoTarifa;

	private Double cuantiaTarifa;

	@Enumerated(EnumType.STRING)
	private RolFamiliar rolFamiliar;

	private String grupoFamiliar;

	@Column(nullable = false)
	@NotNull(message = "Si es competidor o no debe ser true o false, no null")
	private Boolean competidor = false;

	@Temporal(TemporalType.DATE)
	private Date fechaAltaCompeticion;

	@Temporal(TemporalType.DATE)
	private Date fechaAltaCompetidorInicial;

	@ManyToOne
	@JoinColumn(name = "categoria_id")
	private Categoria categoria;

	private Double peso;

	@Temporal(TemporalType.DATE)
	private Date fechaPeso;

	@Column(nullable = false)
	@NotNull(message = "tieneLicencia debe ser true o false, no null")
	private Boolean tieneLicencia = false;

	private Integer numeroLicencia;

	@Temporal(TemporalType.DATE)
	private Date fechaLicencia;

	@OneToMany(mappedBy = "alumnoDeporte", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<AlumnoConvocatoria> convocatorias = new ArrayList<>();

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Alumno getAlumno() {
		return alumno;
	}

	public void setAlumno(Alumno alumno) {
		this.alumno = alumno;
	}

	public Deporte getDeporte() {
		return deporte;
	}

	public void setDeporte(Deporte deporte) {
		this.deporte = deporte;
	}

	public Grado getGrado() {
		return grado;
	}

	public void setGrado(Grado grado) {
		this.grado = grado;
	}

	public Date getFechaGrado() {
		return fechaGrado;
	}

	public void setFechaGrado(Date fechaGrado) {
		this.fechaGrado = fechaGrado;
	}

	public Boolean getAptoParaExamen() {
		return aptoParaExamen;
	}

	public void setAptoParaExamen(Boolean aptoParaExamen) {
		this.aptoParaExamen = aptoParaExamen;
	}

	public Boolean getActivo() {
		return activo;
	}

	public void setActivo(Boolean activo) {
		this.activo = activo;
	}

	public Date getFechaAlta() {
		return fechaAlta;
	}

	public void setFechaAlta(Date fechaAlta) {
		this.fechaAlta = fechaAlta;
	}

	public Date getFechaAltaInicial() {
		return fechaAltaInicial;
	}

	public void setFechaAltaInicial(Date fechaAltaInicial) {
		this.fechaAltaInicial = fechaAltaInicial;
	}

	public Date getFechaBaja() {
		return fechaBaja;
	}

	public void setFechaBaja(Date fechaBaja) {
		this.fechaBaja = fechaBaja;
	}

	public List<AlumnoConvocatoria> getConvocatorias() {
		return convocatorias;
	}

	public void setConvocatorias(List<AlumnoConvocatoria> convocatorias) {
		this.convocatorias = convocatorias;
	}

	public TipoTarifa getTipoTarifa() {
		return tipoTarifa;
	}

	public void setTipoTarifa(TipoTarifa tipoTarifa) {
		this.tipoTarifa = tipoTarifa;
	}

	public Double getCuantiaTarifa() {
		return cuantiaTarifa;
	}

	public void setCuantiaTarifa(Double cuantiaTarifa) {
		this.cuantiaTarifa = cuantiaTarifa;
	}

	public RolFamiliar getRolFamiliar() {
		return rolFamiliar;
	}

	public void setRolFamiliar(RolFamiliar rolFamiliar) {
		this.rolFamiliar = rolFamiliar;
	}

	public String getGrupoFamiliar() {
		return grupoFamiliar;
	}

	public void setGrupoFamiliar(String grupoFamiliar) {
		this.grupoFamiliar = grupoFamiliar;
	}

	public Boolean getCompetidor() {
		return competidor;
	}

	public void setCompetidor(Boolean competidor) {
		this.competidor = competidor;
	}

	public Date getFechaAltaCompeticion() {
		return fechaAltaCompeticion;
	}

	public void setFechaAltaCompeticion(Date fechaAltaCompeticion) {
		this.fechaAltaCompeticion = fechaAltaCompeticion;
	}

	public Date getFechaAltaCompetidorInicial() {
		return fechaAltaCompetidorInicial;
	}

	public void setFechaAltaCompetidorInicial(Date fechaAltaCompetidorInicial) {
		this.fechaAltaCompetidorInicial = fechaAltaCompetidorInicial;
	}

	public Categoria getCategoria() {
		return categoria;
	}

	public void setCategoria(Categoria categoria) {
		this.categoria = categoria;
	}

	public Double getPeso() {
		return peso;
	}

	public void setPeso(Double peso) {
		this.peso = peso;
	}

	public Date getFechaPeso() {
		return fechaPeso;
	}

	public void setFechaPeso(Date fechaPeso) {
		this.fechaPeso = fechaPeso;
	}

	public Boolean getTieneLicencia() {
		return tieneLicencia;
	}

	public void setTieneLicencia(Boolean tieneLicencia) {
		this.tieneLicencia = tieneLicencia;
	}

	public Integer getNumeroLicencia() {
		return numeroLicencia;
	}

	public void setNumeroLicencia(Integer numeroLicencia) {
		this.numeroLicencia = numeroLicencia;
	}

	public Date getFechaLicencia() {
		return fechaLicencia;
	}

	public void setFechaLicencia(Date fechaLicencia) {
		this.fechaLicencia = fechaLicencia;
	}
}
