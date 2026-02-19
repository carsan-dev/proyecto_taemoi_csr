package com.taemoi.project.dtos.response;

public class MaterialExamenVideoDTO {
	private String id;
	private String title;
	private Integer order;
	private String streamUrl;

	public MaterialExamenVideoDTO() {
	}

	public MaterialExamenVideoDTO(String id, String title, Integer order, String streamUrl) {
		this.id = id;
		this.title = title;
		this.order = order;
		this.streamUrl = streamUrl;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
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

	public String getStreamUrl() {
		return streamUrl;
	}

	public void setStreamUrl(String streamUrl) {
		this.streamUrl = streamUrl;
	}
}
