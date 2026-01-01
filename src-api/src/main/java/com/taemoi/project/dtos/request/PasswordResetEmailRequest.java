package com.taemoi.project.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class PasswordResetEmailRequest {
	@Email(message = "La direccion de correo electronico debe ser valida")
	@NotBlank(message = "El correo electronico no puede estar en blanco")
	private String email;

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}
}
