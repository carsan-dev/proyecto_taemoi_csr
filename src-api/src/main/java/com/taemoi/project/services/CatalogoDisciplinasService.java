package com.taemoi.project.services;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import com.taemoi.project.entities.Disciplina;

/**
 * Catálogo de disciplinas de la organización activa (tabla {@code disciplina}).
 *
 * <p>Sustituye a {@code Deporte} (indicadores de cada disciplina), {@code FechaUtils} (14/15
 * años) y {@code DeporteEdadUtils}. Los tests de paridad comprueban que da lo mismo que esas
 * clases para Taemoi.
 *
 * <p>Hay DOS reglas de «menor» y se exponen por separado, sin unificar:
 * <ul>
 * <li>{@link #esMenorPorAnioNatural}: la de {@code FechaUtils.esMenor}, por disciplina
 * ({@code disciplina.edad_adulto}) y año natural. Variante con fecha nula = adulto, como
 * {@code DeporteEdadUtils}: {@link #esMenorPorAnioNaturalFechaNulaComoAdulto}.</li>
 * <li>{@link #esMenorParaExamenPorEdadCumplida}: la de
 * {@code ExamEligibilityConfig.obtenerMesesRequeridos(edad, grado)}, {@code edad < 13} años
 * cumplidos, igual para todas las disciplinas.</li>
 * </ul>
 */
public interface CatalogoDisciplinasService {

	/**
	 * Edad adulta que {@code FechaUtils} aplica cuando no hay disciplina ({@code deporte == null}).
	 * Solo existe para reproducir ese caso; con el catálogo, toda inscripción tiene disciplina.
	 */
	int EDAD_ADULTO_SIN_DISCIPLINA = 14;

	/** {@code ExamEligibilityConfig}: menor si {@code edad < 13} (años cumplidos). */
	int EDAD_CUMPLIDA_ADULTO_EXAMEN = 13;

	Optional<Disciplina> buscarPorCodigo(String codigo);

	/** @throws IllegalArgumentException si la organización activa no tiene esa disciplina. */
	Disciplina obtenerPorCodigo(String codigo);

	/**
	 * Valida un código de disciplina recibido en una petición (lo que antes hacía {@code Deporte.valueOf}):
	 * devuelve el código si la organización activa tiene una disciplina con ese código EXACTO (mayúsculas
	 * incluidas).
	 *
	 * @throws IllegalArgumentException con el mismo texto que {@code Deporte.valueOf} si no existe.
	 * @throws NullPointerException con un código nulo, como {@code Deporte.valueOf}.
	 */
	String validarCodigo(String codigo);

	/**
	 * Código de disciplina recibido como parámetro de ruta o de consulta (antes convertido por Spring al enum
	 * {@code Deporte}): nulo o en blanco = sin filtro ({@code null}, como Spring con el enum); si la escuela no
	 * tiene esa disciplina, 400 sin cuerpo propio, como el error de conversión del enum.
	 */
	default String validarParametro(String codigo) {
		if (codigo == null || codigo.isBlank()) {
			return null;
		}
		try {
			return validarCodigo(codigo);
		} catch (IllegalArgumentException e) {
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.BAD_REQUEST);
		}
	}

	/** Disciplinas activas, por {@code orden}. */
	List<Disciplina> listarActivas();

	/** Todas las disciplinas (activas o no), por {@code orden}. */
	List<Disciplina> listarTodas();

	/**
	 * Edad adulta de la disciplina ({@code disciplina.edad_adulto}); con {@code codigo == null},
	 * {@link #EDAD_ADULTO_SIN_DISCIPLINA}.
	 *
	 * @throws IllegalArgumentException si el código no existe en la organización activa.
	 */
	int obtenerEdadAdulto(String codigoDisciplina);

	/**
	 * Regla de {@code FechaUtils.esMenor(fecha, deporte)}: adulto si
	 * {@code año_nacimiento + edad_adulto <= año actual}. Fecha nula = menor (como FechaUtils;
	 * {@code DeporteEdadUtils} devuelve lo contrario y no se reproduce).
	 */
	boolean esMenorPorAnioNatural(LocalDate fechaNacimiento, String codigoDisciplina);

	/**
	 * Regla de {@code DeporteEdadUtils.esMenorParaDeporte} (la que usa
	 * {@code MaterialExamenServiceImpl}): igual que {@link #esMenorPorAnioNatural(LocalDate, String)}
	 * salvo con fecha nula, que cuenta como ADULTO ({@code false}).
	 */
	boolean esMenorPorAnioNaturalFechaNulaComoAdulto(LocalDate fechaNacimiento, String codigoDisciplina);

	/** Igual que {@link #esMenorPorAnioNatural(LocalDate, String)} con un año de referencia. */
	boolean esMenorPorAnioNatural(LocalDate fechaNacimiento, String codigoDisciplina, int anioReferencia);

	/** Regla de exámenes por edad cumplida: {@code edadCumplida < 13}, sin disciplina. */
	boolean esMenorParaExamenPorEdadCumplida(int edadCumplida);

	/** Si la disciplina admite competidores ({@code permite_competicion}); {@code false} si no existe. */
	boolean permiteCompeticion(String codigoDisciplina);

	/** Si la disciplina exige licencia federativa ({@code requiere_licencia}); {@code false} si no existe. */
	boolean requiereLicencia(String codigoDisciplina);

	/** Códigos de las disciplinas que admiten competidores, por {@code orden}. */
	List<String> codigosQuePermitenCompeticion();

	/** Códigos de las disciplinas que exigen licencia federativa, por {@code orden}. */
	List<String> codigosQueRequierenLicencia();

	// --- Variantes con java.util.Date (fechas de las entidades) ---

	/** {@link #esMenorPorAnioNatural(LocalDate, String)} con una fecha de entidad. */
	default boolean esMenorPorAnioNatural(Date fechaNacimiento, String codigoDisciplina) {
		return esMenorPorAnioNatural(aLocalDate(fechaNacimiento), codigoDisciplina);
	}

	/** {@link #esMenorPorAnioNatural(LocalDate, String, int)} con una fecha de entidad. */
	default boolean esMenorPorAnioNatural(Date fechaNacimiento, String codigoDisciplina, int anioReferencia) {
		return esMenorPorAnioNatural(aLocalDate(fechaNacimiento), codigoDisciplina, anioReferencia);
	}

	/** {@link #esMenorPorAnioNaturalFechaNulaComoAdulto(LocalDate, String)} con una fecha de entidad. */
	default boolean esMenorPorAnioNaturalFechaNulaComoAdulto(Date fechaNacimiento, String codigoDisciplina) {
		return esMenorPorAnioNaturalFechaNulaComoAdulto(aLocalDate(fechaNacimiento), codigoDisciplina);
	}

	/** Igual que la conversión de {@code FechaUtils}: {@code java.sql.Date} sin zona horaria. */
	private static LocalDate aLocalDate(Date fecha) {
		if (fecha == null) {
			return null;
		}
		if (fecha instanceof java.sql.Date fechaSql) {
			return fechaSql.toLocalDate();
		}
		return fecha.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
	}
}
