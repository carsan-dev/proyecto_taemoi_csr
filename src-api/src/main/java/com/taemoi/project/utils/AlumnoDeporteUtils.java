package com.taemoi.project.utils;

import java.util.Comparator;
import java.util.Date;
import java.util.List;

import com.taemoi.project.entities.AlumnoDeporte;

public final class AlumnoDeporteUtils {

	private AlumnoDeporteUtils() {
	}

	public static AlumnoDeporte seleccionarDeportePrincipal(List<AlumnoDeporte> deportes) {
		if (deportes == null || deportes.isEmpty()) {
			return null;
		}

		List<AlumnoDeporte> activos = deportes.stream()
				.filter(ad -> Boolean.TRUE.equals(ad.getActivo()))
				.toList();

		AlumnoDeporte principalActivo = seleccionarDeportePrincipalDesdeLista(activos);
		if (principalActivo != null) {
			return principalActivo;
		}

		return seleccionarDeportePrincipalDesdeLista(deportes);
	}

	private static AlumnoDeporte seleccionarDeportePrincipalDesdeLista(List<AlumnoDeporte> deportes) {
		if (deportes == null || deportes.isEmpty()) {
			return null;
		}

		List<AlumnoDeporte> principales = deportes.stream()
				.filter(ad -> Boolean.TRUE.equals(ad.getPrincipal()))
				.toList();
		List<AlumnoDeporte> candidatos = !principales.isEmpty() ? principales : deportes;

		return candidatos.stream()
				.min(Comparator.comparing(
						AlumnoDeporteUtils::obtenerFechaReferencia,
						Comparator.nullsLast(Comparator.naturalOrder())
				))
				.orElse(null);
	}

	private static Date obtenerFechaReferencia(AlumnoDeporte deporte) {
		if (deporte == null) {
			return null;
		}
		return deporte.getFechaAltaInicial() != null
				? deporte.getFechaAltaInicial()
				: deporte.getFechaAlta();
	}
}
