package com.taemoi.project.dtos.response;

public class RetoDiarioRankingGeneralItemResponse {

	private Integer posicion;
	private String alias;
	private Integer mejorRacha;
	private Integer diasCompletadosTotales;
	private Boolean esUsuarioActual;

	public RetoDiarioRankingGeneralItemResponse() {
	}

	public RetoDiarioRankingGeneralItemResponse(Integer posicion, String alias, Integer mejorRacha,
			Integer diasCompletadosTotales, Boolean esUsuarioActual) {
		this.posicion = posicion;
		this.alias = alias;
		this.mejorRacha = mejorRacha;
		this.diasCompletadosTotales = diasCompletadosTotales;
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

	public Boolean getEsUsuarioActual() {
		return esUsuarioActual;
	}

	public void setEsUsuarioActual(Boolean esUsuarioActual) {
		this.esUsuarioActual = esUsuarioActual;
	}
}
