package com.taemoi.project.services;

public interface PasswordResetService {
	void solicitarResetContrasena(String email);

	void resetearContrasena(String token, String nuevaContrasena);
}
