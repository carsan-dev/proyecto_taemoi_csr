package com.taemoi.project.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegistroRequest {
	private String nombre;
	private String apellidos;
	@NotBlank
	@Email(message = "Debe proporcionar un email válido.")
	private String email;
	@NotBlank
	@Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres.")
	@Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$", message = "La contraseña debe contener mayúsculas, minúsculas y números.")
	private String contrasena;

	public RegistroRequest() {

	}

	public RegistroRequest(String nombre, String apellidos, String email, String contrasena) {
		this.nombre = nombre;
		this.apellidos = apellidos;
		this.email = email;
		this.contrasena = contrasena;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public String getApellidos() {
		return apellidos;
	}

	public void setApellidos(String apellidos) {
		this.apellidos = apellidos;
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
