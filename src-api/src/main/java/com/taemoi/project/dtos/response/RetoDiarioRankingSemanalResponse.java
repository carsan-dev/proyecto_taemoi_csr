package com.taemoi.project.dtos.response;

import java.util.ArrayList;
import java.util.List;

public class RetoDiarioRankingSemanalResponse {

	private String deporte;
	private Integer anioIso;
	private Integer semanaIso;
	private Integer totalParticipantes;
	private List<RetoDiarioRankingItemResponse> top = new ArrayList<>();
	private RetoDiarioRankingMiPosicionResponse miPosicion;

	public RetoDiarioRankingSemanalResponse() {
	}

	public RetoDiarioRankingSemanalResponse(String deporte, Integer anioIso, Integer semanaIso,
			Integer totalParticipantes, List<RetoDiarioRankingItemResponse> top,
			RetoDiarioRankingMiPosicionResponse miPosicion) {
		this.deporte = deporte;
		this.anioIso = anioIso;
		this.semanaIso = semanaIso;
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

	public Integer getAnioIso() {
		return anioIso;
	}

	public void setAnioIso(Integer anioIso) {
		this.anioIso = anioIso;
	}

	public Integer getSemanaIso() {
		return semanaIso;
	}

	public void setSemanaIso(Integer semanaIso) {
		this.semanaIso = semanaIso;
	}

	public Integer getTotalParticipantes() {
		return totalParticipantes;
	}

	public void setTotalParticipantes(Integer totalParticipantes) {
		this.totalParticipantes = totalParticipantes;
	}

	public List<RetoDiarioRankingItemResponse> getTop() {
		return top;
	}

	public void setTop(List<RetoDiarioRankingItemResponse> top) {
		this.top = top;
	}

	public RetoDiarioRankingMiPosicionResponse getMiPosicion() {
		return miPosicion;
	}

	public void setMiPosicion(RetoDiarioRankingMiPosicionResponse miPosicion) {
		this.miPosicion = miPosicion;
	}
}
