package com.taemoi.project.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.services.MigracionMultiDeporteService;
import com.taemoi.project.services.MigracionMultiDeporteService.MigracionReporte;

/**
 * Controlador para ejecutar y verificar la migración multi-deporte
 * SOLO accesible para administradores
 */
@RestController
@RequestMapping("/api/admin/migracion")
public class MigracionController {

	@Autowired
	private MigracionMultiDeporteService migracionService;

	/**
	 * Ejecuta la migración de single-sport a multi-sport
	 * POST /api/admin/migracion/ejecutar
	 */
	@PostMapping("/ejecutar")
	@PreAuthorize("hasRole('ROLE_ADMIN')")
	public ResponseEntity<MigracionReporte> ejecutarMigracion() {
		try {
			MigracionReporte reporte = migracionService.ejecutarMigracion();

			if (reporte.isExitoso()) {
				return ResponseEntity.ok(reporte);
			} else {
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(reporte);
			}
		} catch (Exception e) {
			MigracionReporte reporteError = new MigracionReporte();
			reporteError.setExitoso(false);
			reporteError.setError(e.getMessage());
			reporteError.setMensaje("Error al ejecutar la migración");
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(reporteError);
		}
	}

	/**
	 * Verifica si la migración ya se completó
	 * GET /api/admin/migracion/estado
	 */
	@GetMapping("/estado")
	@PreAuthorize("hasRole('ROLE_ADMIN')")
	public ResponseEntity<EstadoMigracion> verificarEstado() {
		boolean completada = migracionService.isMigracionCompletada();

		EstadoMigracion estado = new EstadoMigracion();
		estado.setCompletada(completada);
		estado.setMensaje(completada ? "Migración completada" : "Migración pendiente");

		return ResponseEntity.ok(estado);
	}

	/**
	 * Fuerza la corrección de deportes en grupos
	 * POST /api/admin/migracion/corregir-grupos
	 */
	@PostMapping("/corregir-grupos")
	@PreAuthorize("hasRole('ROLE_ADMIN')")
	public ResponseEntity<CorreccionGruposReporte> corregirGrupos() {
		try {
			CorreccionGruposReporte reporte = migracionService.corregirDeportesGrupos();
			return ResponseEntity.ok(reporte);
		} catch (Exception e) {
			CorreccionGruposReporte reporteError = new CorreccionGruposReporte();
			reporteError.setExitoso(false);
			reporteError.setError(e.getMessage());
			reporteError.setGruposCorregidos(0);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(reporteError);
		}
	}

	/**
	 * DTO para el estado de la migración
	 */
	public static class EstadoMigracion {
		private boolean completada;
		private String mensaje;

		public boolean isCompletada() {
			return completada;
		}

		public void setCompletada(boolean completada) {
			this.completada = completada;
		}

		public String getMensaje() {
			return mensaje;
		}

		public void setMensaje(String mensaje) {
			this.mensaje = mensaje;
		}
	}

	/**
	 * DTO para el reporte de corrección de grupos
	 */
	public static class CorreccionGruposReporte {
		private boolean exitoso;
		private int gruposCorregidos;
		private String error;

		public boolean isExitoso() {
			return exitoso;
		}

		public void setExitoso(boolean exitoso) {
			this.exitoso = exitoso;
		}

		public int getGruposCorregidos() {
			return gruposCorregidos;
		}

		public void setGruposCorregidos(int gruposCorregidos) {
			this.gruposCorregidos = gruposCorregidos;
		}

		public String getError() {
			return error;
		}

		public void setError(String error) {
			this.error = error;
		}
	}
}
