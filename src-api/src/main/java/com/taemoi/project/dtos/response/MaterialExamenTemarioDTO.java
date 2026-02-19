package com.taemoi.project.dtos.response;

public class MaterialExamenTemarioDTO {
	private String fileName;
	private String downloadUrl;

	public MaterialExamenTemarioDTO() {
	}

	public MaterialExamenTemarioDTO(String fileName, String downloadUrl) {
		this.fileName = fileName;
		this.downloadUrl = downloadUrl;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getDownloadUrl() {
		return downloadUrl;
	}

	public void setDownloadUrl(String downloadUrl) {
		this.downloadUrl = downloadUrl;
	}
}
