package com.taemoi.project.dtos.response;

public class RetoDiarioRankingItemResponse {

	private Integer posicion;
	private String alias;
	private Integer diasCompletados;
	private Boolean esUsuarioActual;

	public RetoDiarioRankingItemResponse() {
	}

	public RetoDiarioRankingItemResponse(Integer posicion, String alias, Integer diasCompletados,
			Boolean esUsuarioActual) {
		this.posicion = posicion;
		this.alias = alias;
		this.diasCompletados = diasCompletados;
		this.esUsuarioActual = esUsuarioActual;
	}

	public Integer getPosicion() {
		return posicion;
	}

	public void setPosicion(Integer posicion) {
		this.posicion = posicion;
	}

	public String getAlias() {
		return alias;
	}

	public void setAlias(String alias) {
		this.alias = alias;
	}

	public Integer getDiasCompletados() {
		return diasCompletados;
	}

	public void setDiasCompletados(Integer diasCompletados) {
		this.diasCompletados = diasCompletados;
	}

	public Boolean getEsUsuarioActual() {
		return esUsuarioActual;
	}

	public void setEsUsuarioActual(Boolean esUsuarioActual) {
		this.esUsuarioActual = esUsuarioActual;
	}
}
