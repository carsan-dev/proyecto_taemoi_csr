package com.taemoi.project.dtos.request;

import jakarta.validation.constraints.NotBlank;

public class RegistroConfirmacionRequest {
	@NotBlank(message = "El token no puede estar en blanco")
	private String token;

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}
}
