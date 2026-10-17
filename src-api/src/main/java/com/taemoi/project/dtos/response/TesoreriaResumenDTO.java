package com.taemoi.project.dtos.response;

public class TesoreriaResumenDTO {

	private Integer mes;
	private Integer ano;
	private String deporte;
	private Long totalMovimientos;
	private Long totalPagados;
	private Long totalPendientes;
	private Double importeTotal;
	private Double importePagado;
	private Double importePendiente;
	private Long alumnosConPendientes;
	/** Entrega 6-3 (D6-12), aditivo: los mismos totales por sede del cargo (ver {@link TesoreriaResumenSedeDTO}). */
	private java.util.List<TesoreriaResumenSedeDTO> porSede = new java.util.ArrayList<>();

	public java.util.List<TesoreriaResumenSedeDTO> getPorSede() {
		return porSede;
	}

	public void setPorSede(java.util.List<TesoreriaResumenSedeDTO> porSede) {
		this.porSede = porSede;
	}

	public Integer getMes() {
		return mes;
	}

	public void setMes(Integer mes) {
		this.mes = mes;
	}

	public Integer getAno() {
		return ano;
	}

	public void setAno(Integer ano) {
		this.ano = ano;
	}

	public String getDeporte() {
		return deporte;
	}

	public void setDeporte(String deporte) {
		this.deporte = deporte;
	}

	public Long getTotalMovimientos() {
		return totalMovimientos;
	}

	public void setTotalMovimientos(Long totalMovimientos) {
		this.totalMovimientos = totalMovimientos;
	}

	public Long getTotalPagados() {
		return totalPagados;
	}

	public void setTotalPagados(Long totalPagados) {
		this.totalPagados = totalPagados;
	}

	public Long getTotalPendientes() {
		return totalPendientes;
	}

	public void setTotalPendientes(Long totalPendientes) {
		this.totalPendientes = totalPendientes;
	}

	public Double getImporteTotal() {
		return importeTotal;
	}

	public void setImporteTotal(Double importeTotal) {
		this.importeTotal = importeTotal;
	}

	public Double getImportePagado() {
		return importePagado;
	}

	public void setImportePagado(Double importePagado) {
		this.importePagado = importePagado;
	}

	public Double getImportePendiente() {
		return importePendiente;
	}

	public void setImportePendiente(Double importePendiente) {
		this.importePendiente = importePendiente;
	}

	public Long getAlumnosConPendientes() {
		return alumnosConPendientes;
	}

	public void setAlumnosConPendientes(Long alumnosConPendientes) {
		this.alumnosConPendientes = alumnosConPendientes;
	}
}
