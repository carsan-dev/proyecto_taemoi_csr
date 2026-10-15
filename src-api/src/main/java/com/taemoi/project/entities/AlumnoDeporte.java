package com.taemoi.project.entities;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;

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
	uniqueConstraints = @UniqueConstraint(name = "uk_alumno_deporte_alumno_disciplina",
		columnNames = {"alumno_id", "disciplina_id"}),
	indexes = {
		@Index(name = "idx_alumno_deporte_alumno_id", columnList = "alumno_id"),
		@Index(name = "idx_alumno_deporte_disciplina", columnList = "disciplina_id"),
		@Index(name = "idx_alumno_deporte_disciplina_apto", columnList = "disciplina_id, apto_para_examen")
	})
public class AlumnoDeporte extends EntidadOrganizacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "alumno_id", nullable = false)
	@JsonIgnore
	private Alumno alumno;

	/**
	 * Disciplina (V26, NOT NULL). Desde V28 es la única fuente del deporte: la columna heredada
	 * {@code deporte} se borró y el JSON expone su código como {@code deporte} ({@link #getDeporte()}).
	 * EAGER: el código del deporte se lee también fuera de la sesión (respuestas, correos, informes), como
	 * antes la columna.
	 */
	@JsonIgnore
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "disciplina_id", nullable = false)
	@NotNull(message = "El deporte no puede ser nulo")
	private Disciplina disciplina;

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

	@Column(nullable = false)
	@NotNull(message = "El campo principal no puede ser nulo")
	private Boolean principal = false;

	@Temporal(TemporalType.DATE)
	private Date fechaAlta;

	@Temporal(TemporalType.DATE)
	private Date fechaAltaInicial;

	@Temporal(TemporalType.DATE)
	private Date fechaBaja;

	/**
	 * Tarifa del catálogo (V24). Desde V28 es la única fuente: la columna heredada {@code tipo_tarifa} se
	 * borró y el JSON expone su código como {@code tipoTarifa} ({@link #getTipoTarifa()}). EAGER por la misma
	 * razón que {@link #disciplina}.
	 */
	@JsonIgnore
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "tarifa_id")
	private Tarifa tarifa;

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

	/**
	 * Código de la disciplina ({@code TAEKWONDO}…): el valor que tenía la columna {@code deporte} y que la API
	 * sigue devolviendo en el campo {@code deporte}. Null si no hay disciplina.
	 */
	@JsonProperty("deporte")
	public String getDeporte() {
		return disciplina == null ? null : disciplina.getCodigo();
	}

	public Disciplina getDisciplina() {
		return disciplina;
	}

	public void setDisciplina(Disciplina disciplina) {
		this.disciplina = disciplina;
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

	public Boolean getPrincipal() {
		return principal;
	}

	public void setPrincipal(Boolean principal) {
		this.principal = principal;
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

	/**
	 * Código de la tarifa: el valor que tenía la columna {@code tipo_tarifa} (Taemoi) y que la API sigue
	 * devolviendo en el campo {@code tipoTarifa}. Null si no hay tarifa.
	 */
	@JsonProperty("tipoTarifa")
	public String getTipoTarifa() {
		return getCodigoTarifa();
	}

	public Tarifa getTarifa() {
		return tarifa;
	}

	public void setTarifa(Tarifa tarifa) {
		this.tarifa = tarifa;
	}

	/** Código de la tarifa del catálogo; null si no hay tarifa. */
	@JsonIgnore
	public String getCodigoTarifa() {
		return tarifa == null ? null : tarifa.getCodigo();
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
