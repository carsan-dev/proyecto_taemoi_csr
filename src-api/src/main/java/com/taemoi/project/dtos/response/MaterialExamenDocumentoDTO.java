package com.taemoi.project.dtos.response;

public class MaterialExamenDocumentoDTO {
	private String id;
	private String fileName;
	private String title;
	private Integer order;
	private String mimeType;
	private boolean previewable;
	private String openUrl;
	private String downloadUrl;

	public MaterialExamenDocumentoDTO() {
	}

	public MaterialExamenDocumentoDTO(
			String id,
			String fileName,
			String title,
			Integer order,
			String mimeType,
			boolean previewable,
			String openUrl,
			String downloadUrl) {
		this.id = id;
		this.fileName = fileName;
		this.title = title;
		this.order = order;
		this.mimeType = mimeType;
		this.previewable = previewable;
		this.openUrl = openUrl;
		this.downloadUrl = downloadUrl;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public Integer getOrder() {
		return order;
	}

	public void setOrder(Integer order) {
		this.order = order;
	}

	public String getMimeType() {
		return mimeType;
	}

	public void setMimeType(String mimeType) {
		this.mimeType = mimeType;
	}

	public boolean isPreviewable() {
		return previewable;
	}

	public void setPreviewable(boolean previewable) {
		this.previewable = previewable;
	}

	public String getOpenUrl() {
		return openUrl;
	}

	public void setOpenUrl(String openUrl) {
		this.openUrl = openUrl;
	}

	public String getDownloadUrl() {
		return downloadUrl;
	}

	public void setDownloadUrl(String downloadUrl) {
		this.downloadUrl = downloadUrl;
	}
}
