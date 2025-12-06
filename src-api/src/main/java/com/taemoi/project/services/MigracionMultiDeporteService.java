package com.taemoi.project.services;

/**
 * Servicio para migrar datos existentes de single-sport a multi-sport
 */
public interface MigracionMultiDeporteService {

	/**
	 * Ejecuta la migración completa de datos
	 * @return Reporte de migración con estadísticas
	 */
	MigracionReporte ejecutarMigracion();

	/**
	 * Verifica si la migración ya se ejecutó
	 * @return true si ya se migró
	 */
	boolean isMigracionCompletada();

	/**
	 * Marca la migración como completada
	 */
	void marcarMigracionCompletada();

	/**
	 * Corrige los deportes asignados a los grupos basándose en sus nombres
	 * @return Reporte de corrección con estadísticas
	 */
	com.taemoi.project.controllers.MigracionController.CorreccionGruposReporte corregirDeportesGrupos();

	/**
	 * Reporte de migración con estadísticas
	 */
	class MigracionReporte {
		private int alumnosMigrados;
		private int alumnosDeporteCreados;
		private int productosActualizados;
		private int convocatoriasActualizadas;
		private int gruposActualizados;
		private boolean exitoso;
		private String mensaje;
		private String error;

		public MigracionReporte() {
		}

		public int getAlumnosMigrados() {
			return alumnosMigrados;
		}

		public void setAlumnosMigrados(int alumnosMigrados) {
			this.alumnosMigrados = alumnosMigrados;
		}

		public int getAlumnosDeporteCreados() {
			return alumnosDeporteCreados;
		}

		public void setAlumnosDeporteCreados(int alumnosDeporteCreados) {
			this.alumnosDeporteCreados = alumnosDeporteCreados;
		}

		public int getProductosActualizados() {
			return productosActualizados;
		}

		public void setProductosActualizados(int productosActualizados) {
			this.productosActualizados = productosActualizados;
		}

		public int getConvocatoriasActualizadas() {
			return convocatoriasActualizadas;
		}

		public void setConvocatoriasActualizadas(int convocatoriasActualizadas) {
			this.convocatoriasActualizadas = convocatoriasActualizadas;
		}

		public int getGruposActualizados() {
			return gruposActualizados;
		}

		public void setGruposActualizados(int gruposActualizados) {
			this.gruposActualizados = gruposActualizados;
		}

		public boolean isExitoso() {
			return exitoso;
		}

		public void setExitoso(boolean exitoso) {
			this.exitoso = exitoso;
		}

		public String getMensaje() {
			return mensaje;
		}

		public void setMensaje(String mensaje) {
			this.mensaje = mensaje;
		}

		public String getError() {
			return error;
		}

		public void setError(String error) {
			this.error = error;
		}

		@Override
		public String toString() {
			return String.format(
					"MigracionReporte{exitoso=%s, alumnosMigrados=%d, alumnosDeporteCreados=%d, " +
					"productosActualizados=%d, convocatoriasActualizadas=%d, gruposActualizados=%d, " +
					"mensaje='%s', error='%s'}",
					exitoso, alumnosMigrados, alumnosDeporteCreados, productosActualizados,
					convocatoriasActualizadas, gruposActualizados, mensaje, error);
		}
	}
}
