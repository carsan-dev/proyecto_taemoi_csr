package com.taemoi.project.dtos.response;

import java.util.ArrayList;
import java.util.List;

public class MaterialExamenVideoGrupoDTO {
	private String grado;
	private String titulo;
	private String bloqueId;
	private List<MaterialExamenVideoDTO> videos = new ArrayList<>();

	public MaterialExamenVideoGrupoDTO() {
	}

	public MaterialExamenVideoGrupoDTO(String grado, String titulo, String bloqueId, List<MaterialExamenVideoDTO> videos) {
		this.grado = grado;
		this.titulo = titulo;
		this.bloqueId = bloqueId;
		this.videos = videos;
	}

	public String getGrado() {
		return grado;
	}

	public void setGrado(String grado) {
		this.grado = grado;
	}

	public String getTitulo() {
		return titulo;
	}

	public void setTitulo(String titulo) {
		this.titulo = titulo;
	}

	public String getBloqueId() {
		return bloqueId;
	}

	public void setBloqueId(String bloqueId) {
		this.bloqueId = bloqueId;
	}

	public List<MaterialExamenVideoDTO> getVideos() {
		return videos;
	}

	public void setVideos(List<MaterialExamenVideoDTO> videos) {
		this.videos = videos;
	}
}
