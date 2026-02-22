package com.taemoi.project.dtos.response;

public class RetoDiarioRankingGeneralMiPosicionResponse {

	private Integer posicion;
	private String alias;
	private Integer mejorRacha;
	private Integer diasCompletadosTotales;
	private Integer diasParaSuperarSiguiente;

	public RetoDiarioRankingGeneralMiPosicionResponse() {
	}

	public RetoDiarioRankingGeneralMiPosicionResponse(Integer posicion, String alias, Integer mejorRacha,
			Integer diasCompletadosTotales, Integer diasParaSuperarSiguiente) {
		this.posicion = posicion;
		this.alias = alias;
		this.mejorRacha = mejorRacha;
		this.diasCompletadosTotales = diasCompletadosTotales;
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

	public Integer getMejorRacha() {
		return mejorRacha;
	}

	public void setMejorRacha(Integer mejorRacha) {
		this.mejorRacha = mejorRacha;
	}

	public Integer getDiasCompletadosTotales() {
		return diasCompletadosTotales;
	}

	public void setDiasCompletadosTotales(Integer diasCompletadosTotales) {
		this.diasCompletadosTotales = diasCompletadosTotales;
	}

	public Integer getDiasParaSuperarSiguiente() {
		return diasParaSuperarSiguiente;
	}

	public void setDiasParaSuperarSiguiente(Integer diasParaSuperarSiguiente) {
		this.diasParaSuperarSiguiente = diasParaSuperarSiguiente;
	}
}
