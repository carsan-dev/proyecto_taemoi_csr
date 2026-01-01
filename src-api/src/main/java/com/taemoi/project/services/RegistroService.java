package com.taemoi.project.services;

import com.taemoi.project.dtos.request.RegistroSolicitudRequest;

public interface RegistroService {
	void solicitarRegistro(RegistroSolicitudRequest request);

	void confirmarRegistro(String token);
}
