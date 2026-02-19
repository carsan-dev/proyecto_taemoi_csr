package com.taemoi.project.dtos.response;

import java.util.ArrayList;
import java.util.List;

public class MaterialExamenDTO {
	private String deporte;
	private String gradoActual;
	private String bloqueId;
	private MaterialExamenTemarioDTO temario;
	private List<MaterialExamenVideoDTO> videos = new ArrayList<>();

	public MaterialExamenDTO() {
	}

	public String getDeporte() {
		return deporte;
	}

	public void setDeporte(String deporte) {
		this.deporte = deporte;
	}

	public String getGradoActual() {
		return gradoActual;
	}

	public void setGradoActual(String gradoActual) {
		this.gradoActual = gradoActual;
	}

	public String getBloqueId() {
		return bloqueId;
	}

	public void setBloqueId(String bloqueId) {
		this.bloqueId = bloqueId;
	}

	public MaterialExamenTemarioDTO getTemario() {
		return temario;
	}

	public void setTemario(MaterialExamenTemarioDTO temario) {
		this.temario = temario;
	}

	public List<MaterialExamenVideoDTO> getVideos() {
		return videos;
	}

	public void setVideos(List<MaterialExamenVideoDTO> videos) {
		this.videos = videos;
	}
}
