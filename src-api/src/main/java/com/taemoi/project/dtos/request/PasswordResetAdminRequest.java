package com.taemoi.project.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PasswordResetAdminRequest {
	@NotBlank(message = "La contrasena no puede estar en blanco")
	@Size(min = 8, message = "La contrasena debe tener al menos 8 caracteres")
	@Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
			message = "La contrasena debe contener mayusculas, minusculas y numeros")
	private String nuevaContrasena;

	public String getNuevaContrasena() {
		return nuevaContrasena;
	}

	public void setNuevaContrasena(String nuevaContrasena) {
		this.nuevaContrasena = nuevaContrasena;
	}
}
