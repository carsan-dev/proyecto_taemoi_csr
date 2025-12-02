package com.taemoi.project.dtos;

import java.util.Date;

import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.TipoTarifa;

public class AlumnoDeporteDTO {
	private Long id;
	private Deporte deporte;
	private String grado;
	private Date fechaGrado;
	private Boolean aptoParaExamen;
	private Boolean competidor;
	private Double peso;
	private Date fechaPeso;
	private Boolean tieneLicencia;
	private Integer numeroLicencia;
	private Date fechaLicencia;
	private Double cuantiaTarifa;
	private TipoTarifa tipoTarifa;

	public AlumnoDeporteDTO() {
	}

	public AlumnoDeporteDTO(AlumnoDeporte alumnoDeporte) {
		this.id = alumnoDeporte.getId();
		this.deporte = alumnoDeporte.getDeporte();
		this.grado = alumnoDeporte.getGrado() != null ? alumnoDeporte.getGrado().getTipoGrado().name() : null;
		this.fechaGrado = alumnoDeporte.getFechaGrado();
		this.aptoParaExamen = alumnoDeporte.getAptoParaExamen();
		this.competidor = alumnoDeporte.getCompetidor();
		this.peso = alumnoDeporte.getPeso();
		this.fechaPeso = alumnoDeporte.getFechaPeso();
		this.tieneLicencia = alumnoDeporte.getTieneLicencia();
		this.numeroLicencia = alumnoDeporte.getNumeroLicencia();
		this.fechaLicencia = alumnoDeporte.getFechaLicencia();
		this.cuantiaTarifa = alumnoDeporte.getCuantiaTarifa();
		this.tipoTarifa = alumnoDeporte.getTipoTarifa();
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Deporte getDeporte() {
		return deporte;
	}

	public void setDeporte(Deporte deporte) {
		this.deporte = deporte;
	}

	public String getGrado() {
		return grado;
	}

	public void setGrado(String grado) {
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

	public Boolean getCompetidor() {
		return competidor;
	}

	public void setCompetidor(Boolean competidor) {
		this.competidor = competidor;
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

	public Double getCuantiaTarifa() {
		return cuantiaTarifa;
	}

	public void setCuantiaTarifa(Double cuantiaTarifa) {
		this.cuantiaTarifa = cuantiaTarifa;
	}

	public TipoTarifa getTipoTarifa() {
		return tipoTarifa;
	}

	public void setTipoTarifa(TipoTarifa tipoTarifa) {
		this.tipoTarifa = tipoTarifa;
	}
}
