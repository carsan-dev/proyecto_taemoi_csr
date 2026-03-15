package com.taemoi.project.dtos.response;

import java.util.ArrayList;
import java.util.List;

public class RetoDiarioRankingGeneralResponse {

	private String deporte;
	private Integer totalParticipantes;
	private List<RetoDiarioRankingGeneralItemResponse> top = new ArrayList<>();
	private RetoDiarioRankingGeneralMiPosicionResponse miPosicion;

	public RetoDiarioRankingGeneralResponse() {
	}

	public RetoDiarioRankingGeneralResponse(String deporte, Integer totalParticipantes,
			List<RetoDiarioRankingGeneralItemResponse> top, RetoDiarioRankingGeneralMiPosicionResponse miPosicion) {
		this.deporte = deporte;
		this.totalParticipantes = totalParticipantes;
		this.top = top;
		this.miPosicion = miPosicion;
	}

	public String getDeporte() {
		return deporte;
	}

	public void setDeporte(String deporte) {
		this.deporte = deporte;
	}

	public Integer getTotalParticipantes() {
		return totalParticipantes;
	}

	public void setTotalParticipantes(Integer totalParticipantes) {
		this.totalParticipantes = totalParticipantes;
	}

	public List<RetoDiarioRankingGeneralItemResponse> getTop() {
		return top;
	}

	public void setTop(List<RetoDiarioRankingGeneralItemResponse> top) {
		this.top = top;
	}

	public RetoDiarioRankingGeneralMiPosicionResponse getMiPosicion() {
		return miPosicion;
	}

	public void setMiPosicion(RetoDiarioRankingGeneralMiPosicionResponse miPosicion) {
		this.miPosicion = miPosicion;
	}
}
