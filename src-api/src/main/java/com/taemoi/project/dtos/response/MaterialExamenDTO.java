package com.taemoi.project.dtos.response;

import java.util.ArrayList;
import java.util.List;

public class MaterialExamenDTO {
	private String deporte;
	private String gradoActual;
	private String siguienteGrado;
	private String bloqueId;
	private MaterialExamenTemarioDTO temario;
	private List<MaterialExamenDocumentoDTO> documentos = new ArrayList<>();
	private List<MaterialExamenVideoDTO> videos = new ArrayList<>();
	private List<MaterialExamenVideoDTO> videosAnteriores = new ArrayList<>();
	private List<MaterialExamenVideoGrupoDTO> gruposVideosAnteriores = new ArrayList<>();

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

	public String getSiguienteGrado() {
		return siguienteGrado;
	}

	public void setSiguienteGrado(String siguienteGrado) {
		this.siguienteGrado = siguienteGrado;
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

	public List<MaterialExamenVideoDTO> getVideosAnteriores() {
		return videosAnteriores;
	}

	public void setVideosAnteriores(List<MaterialExamenVideoDTO> videosAnteriores) {
		this.videosAnteriores = videosAnteriores;
	}

	public List<MaterialExamenVideoGrupoDTO> getGruposVideosAnteriores() {
		return gruposVideosAnteriores;
	}

	public void setGruposVideosAnteriores(List<MaterialExamenVideoGrupoDTO> gruposVideosAnteriores) {
		this.gruposVideosAnteriores = gruposVideosAnteriores;
	}

	public List<MaterialExamenDocumentoDTO> getDocumentos() {
		return documentos;
	}

	public void setDocumentos(List<MaterialExamenDocumentoDTO> documentos) {
		this.documentos = documentos;
	}
}
