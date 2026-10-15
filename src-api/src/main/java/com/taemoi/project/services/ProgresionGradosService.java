package com.taemoi.project.services;

import java.util.List;
import java.util.Optional;

import com.taemoi.project.entities.Grado;

/**
 * Progresión de grados leída del catálogo de la organización activa ({@code grado.orden},
 * {@code aplica_a}, {@code meses_minimos_*}, {@code bloque_temario}, {@code nombre}).
 *
 * <p>Sustituye a {@code GradeProgressionConfig}, {@code ExamEligibilityConfig},
 * {@code ExamMaterialBlockConfig} y {@code GradoUtils}. Los tests de paridad comprueban que da lo
 * mismo que esas clases para Taemoi en las disciplinas con grados.
 *
 * <p>Disciplina nula o sin sistema de grados (pilates, defensa personal): no hay grados. Los
 * llamadores no preguntan por ellas (salen antes por {@link #tieneProgresion}); si lo hacen, no
 * hay periodo ({@link Long#MAX_VALUE}), ni bloque, ni nombre («N/A»), y el siguiente grado lanza
 * {@link IllegalArgumentException} como {@code GradeProgressionConfig}.
 *
 * <p>Regla del siguiente grado, dentro del sistema de grados de la disciplina:
 * <ol>
 * <li>Candidatos: grados con {@code orden} mayor que el actual, en orden.</li>
 * <li>Si el grado actual es {@code TODOS} o del grupo de edad del alumno ({@code MENORES} /
 * {@code ADULTOS}), el siguiente es el primer candidato {@code TODOS} o de ese grupo.</li>
 * <li>Si no (un adulto con un grado de menores, un menor con uno de adultos, o un grado
 * {@code NINGUNO}), el siguiente es el primer candidato {@code TODOS}.</li>
 * <li>{@code NINGUNO} nunca es destino.</li>
 * </ol>
 *
 * <p>Los códigos de disciplina y de grado son los del catálogo ({@code TAEKWONDO},
 * {@code NEGRO_1_DAN}…). Un código que no existe en la organización activa lanza
 * {@link IllegalArgumentException}.
 */
public interface ProgresionGradosService {

	/** Si la disciplina tiene sistema de grados. {@code false} si es nula. */
	boolean tieneProgresion(String codigoDisciplina);

	/**
	 * Valida un código de grado recibido en una petición (lo que antes hacía {@code TipoGrado.valueOf}): lo
	 * devuelve si algún sistema de grados de la organización activa tiene un grado con ese código EXACTO o si es
	 * uno de los códigos de Taemoi (los del antiguo enum, válidos en cualquier escuela como antes). Que el grado
	 * exista en el sistema de una disciplina concreta se comprueba después ({@link #buscarGrado}).
	 *
	 * @throws IllegalArgumentException con el mismo texto que {@code TipoGrado.valueOf} si no existe.
	 * @throws NullPointerException con un código nulo, como {@code TipoGrado.valueOf}.
	 */
	String validarCodigoGrado(String codigoGrado);

	/** Si algún sistema de grados de la organización activa tiene ese código exacto. */
	boolean existeCodigoGrado(String codigoGrado);

	/** Grado del sistema de la disciplina con ese código. Vacío si algo es nulo o no hay grados. */
	Optional<Grado> buscarGrado(String codigoDisciplina, String codigoGrado);

	/**
	 * Grados del sistema de la disciplina, por {@code id} (el orden de {@code /api/grados}). Vacío
	 * si la disciplina no tiene grados.
	 */
	List<Grado> listarGrados(String codigoDisciplina);

	/**
	 * Grados que puede tener un alumno de ese grupo de edad en la disciplina ({@code aplica_a}
	 * {@code TODOS} o su grupo), por {@code orden}. Vacío si la disciplina no tiene grados.
	 */
	List<Grado> listarGradosAplicables(String codigoDisciplina, boolean esMenor);

	/** Grado de entrada de la disciplina (el de menor {@code orden}). Vacío si no tiene grados. */
	Optional<Grado> obtenerGradoInicial(String codigoDisciplina);

	/**
	 * Siguiente grado (ver regla en la clase). Vacío si la disciplina o el grado son nulos o no hay
	 * siguiente.
	 *
	 * @throws IllegalArgumentException si la disciplina no tiene sistema de grados.
	 */
	Optional<Grado> obtenerSiguienteGrado(String codigoDisciplina, boolean esMenor, String codigoGrado);

	/**
	 * Reproduce {@code GradeProgressionConfig.obtenerGradosAnteriores}: los grados que tienen
	 * siguiente para ese grupo de edad («claves del mapa»), en orden, anteriores al actual. Si el
	 * actual no tiene siguiente (no es clave), devuelve TODOS los que tienen siguiente.
	 *
	 * @throws IllegalArgumentException si la disciplina no tiene sistema de grados.
	 */
	List<Grado> obtenerGradosAnteriores(String codigoDisciplina, boolean esMenor, String codigoGrado);

	/**
	 * Meses mínimos en el grado antes de examinarse ({@code meses_minimos_menor} /
	 * {@code meses_minimos_adulto}); {@link Long#MAX_VALUE} = no apto (sin dato, grado nulo o
	 * disciplina sin grados).
	 */
	long obtenerMesesRequeridos(String codigoDisciplina, String codigoGrado, boolean esMenor);

	/**
	 * Como {@code ExamEligibilityConfig.obtenerMesesRequeridos(edad, grado)}: menor si
	 * {@code edadCumplida < 13} ({@link CatalogoDisciplinasService#esMenorParaExamenPorEdadCumplida}).
	 */
	long obtenerMesesRequeridosPorEdadCumplida(String codigoDisciplina, int edadCumplida, String codigoGrado);

	/** Si hay meses mínimos definidos para el grado y grupo de edad. */
	boolean tienePeriodoDefinido(String codigoDisciplina, String codigoGrado, boolean esMenor);

	/** Bloque de temario del grado; {@code null} si la disciplina no tiene grados o hay nulos. */
	String obtenerBloqueTemario(String codigoDisciplina, String codigoGrado);

	/** Nombre visible del grado en su disciplina; «N/A» si es nulo o no tiene nombre. */
	String obtenerNombreVisible(Grado grado);

	/**
	 * Nombre visible del grado con ese código en el sistema de la disciplina; «N/A» si el código
	 * de grado es nulo o la disciplina no tiene grados.
	 *
	 * @throws IllegalArgumentException si la disciplina tiene grados pero no ese código.
	 */
	String obtenerNombreVisible(String codigoDisciplina, String codigoGrado);
}
