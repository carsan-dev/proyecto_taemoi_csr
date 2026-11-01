package com.taemoi.project.archivos;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Carpetas del material de examen de un bloque (fase3-e8c-plan.md, 8c-R). Cada escuela tiene la suya, {@code
 * org-<id>/materiales/<disciplina>/<bloque>}. La escuela por defecto conserva además la carpeta heredada, {@code
 * Materiales_Examen/<disciplina>/<bloque>}, hasta que la primera escritura del bloque la adopta.
 */
public final class CarpetasMaterial {

	/**
	 * Decisiones 406 y 415: id del catálogo → nombre de la carpeta heredada. El catálogo y la carpeta propia usan el id;
	 * solo la carpeta heredada conserva el nombre antiguo.
	 */
	private static final Map<String, String> ALIAS_LEGADO = Map.of("b11_negro_5_a_maximo", "b11_negro_5_a_negro_6");

	private CarpetasMaterial() {
	}

	public static Path propia(Path base, Long organizacion, String disciplina, String bloque) {
		Path raiz = base.toAbsolutePath().normalize();
		return dentroDeLaBase(raiz, raiz.resolve(RutasArchivos.carpetaOrganizacion(organizacion))
				.resolve(RutasArchivos.CARPETA_MATERIALES)
				.resolve(disciplina.toLowerCase(Locale.ROOT))
				.resolve(bloque));
	}

	public static Path legada(Path base, String disciplina, String bloque) {
		Path raiz = base.toAbsolutePath().normalize();
		return dentroDeLaBase(raiz, raiz.resolve(RutasArchivos.CARPETA_MATERIALES_LEGADO)
				.resolve(disciplina.toLowerCase(Locale.ROOT))
				.resolve(ALIAS_LEGADO.getOrDefault(bloque, bloque)));
	}

	private static Path dentroDeLaBase(Path raiz, Path carpeta) {
		Path normalizada = carpeta.normalize();
		if (!normalizada.startsWith(raiz)) {
			throw new IllegalArgumentException("Bloque de material fuera del directorio permitido");
		}
		return normalizada;
	}
}
