package com.taemoi.project.services;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.taemoi.project.entities.Disciplina;

/**
 * Catálogo de disciplinas de la organización activa (tabla {@code disciplina}).
 *
 * <p>Sustituirá a {@code Deporte}, {@code FechaUtils} (14/15 años) y {@code DeporteEdadUtils}.
 * Todavía no lo usa ningún llamador: los tests de paridad comprueban que da lo mismo que esas
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

	/** Disciplinas activas, por {@code orden}. */
	List<Disciplina> listarActivas();

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
}
