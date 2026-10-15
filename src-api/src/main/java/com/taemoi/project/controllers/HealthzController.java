package com.taemoi.project.controllers;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Comprobación de vida para el healthcheck de Docker y los scripts de despliegue.
 *
 * <p>Está FUERA de {@code /api} a propósito: {@code AuditoriaRequestFilter} solo audita rutas {@code /api/*},
 * así que el sondeo cada 30 s no genera filas en {@code auditoria_evento}. No consulta la base de datos ni
 * expone datos: solo indica que el proceso atiende peticiones HTTP. SecurityConfiguration la abre sin sesión
 * únicamente para {@code GET /healthz}.
 */
@RestController
public class HealthzController {

	private static final Map<String, String> UP = Map.of("status", "UP");

	@GetMapping("/healthz")
	public Map<String, String> healthz() {
		return UP;
	}
}
