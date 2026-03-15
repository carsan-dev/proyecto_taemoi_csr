package com.taemoi.project.dtos.response;

public class RetoDiarioRankingMiPosicionResponse {

	private Integer posicion;
	private String alias;
	private Integer diasCompletados;
	private Integer diasParaSuperarSiguiente;

	public RetoDiarioRankingMiPosicionResponse() {
	}

	public RetoDiarioRankingMiPosicionResponse(Integer posicion, String alias, Integer diasCompletados,
			Integer diasParaSuperarSiguiente) {
		this.posicion = posicion;
		this.alias = alias;
		this.diasCompletados = diasCompletados;
		this.diasParaSuperarSiguiente = diasParaSuperarSiguiente;
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

	public Integer getDiasParaSuperarSiguiente() {
		return diasParaSuperarSiguiente;
	}

	public void setDiasParaSuperarSiguiente(Integer diasParaSuperarSiguiente) {
		this.diasParaSuperarSiguiente = diasParaSuperarSiguiente;
	}
}
