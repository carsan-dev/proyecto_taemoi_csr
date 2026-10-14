package com.taemoi.project.dtos.request;

/** Cuerpo de {@code POST /api/auth/organizacion}. */
public class CambioOrganizacionRequest {

	private Long organizacionId;

	public CambioOrganizacionRequest() {
	}

	public CambioOrganizacionRequest(Long organizacionId) {
		this.organizacionId = organizacionId;
	}

	public Long getOrganizacionId() {
		return organizacionId;
	}

	public void setOrganizacionId(Long organizacionId) {
		this.organizacionId = organizacionId;
	}
}
