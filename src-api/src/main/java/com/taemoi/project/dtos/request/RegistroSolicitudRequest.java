package com.taemoi.project.dtos.request;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegistroSolicitudRequest {
	@NotBlank(message = "El correo electronico no puede estar en blanco")
	@Email(message = "Debe proporcionar un email valido.")
	private String email;

	@NotNull(message = "La fecha de nacimiento no puede estar vacia")
	@JsonFormat(pattern = "yyyy-MM-dd")
	private LocalDate fechaNacimiento;

	@NotBlank(message = "La contrasena no puede estar en blanco")
	@Size(min = 8, message = "La contrasena debe tener al menos 8 caracteres.")
	@Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
			message = "La contrasena debe contener mayusculas, minusculas y numeros.")
	private String contrasena;

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public LocalDate getFechaNacimiento() {
		return fechaNacimiento;
	}

	public void setFechaNacimiento(LocalDate fechaNacimiento) {
		this.fechaNacimiento = fechaNacimiento;
	}

	public String getContrasena() {
		return contrasena;
	}

	public void setContrasena(String contrasena) {
		this.contrasena = contrasena;
	}
}
