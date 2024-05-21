package com.taemoi.project.dtos.request;

public class LoginRequest {
	private String email;
	private String contrasena;

	public LoginRequest() {
	}

	public LoginRequest(String email, String contrasena) {
		this.email = email;
		this.contrasena = contrasena;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getContrasena() {
		return contrasena;
	}

	public void setContrasena(String contrasena) {
		this.contrasena = contrasena;
	}

}
