package com.taemoi.project.services;

/**
 * Pieza 394 (decisiones 267 y 375): último día del mes para pagar la mensualidad del mes en curso en la organización
 * activa ({@code organizacion.dia_limite_mensualidad}, V32_3, de 1 a 28; 5 por defecto). La mensualidad vence el día
 * siguiente.
 *
 * <p>Lo implementa {@code ComportamientoLegadoServiceImpl} con la MISMA lectura en memoria que
 * {@link ComportamientoLegadoService#activo()}: el pendiente vencido pide los dos valores y no suma consultas.
 */
public interface DiaLimiteMensualidadService {

	/**
	 * @return el día límite de la organización activa; 5 si no tiene valor.
	 * @throws IllegalStateException si no hay organización activa en el hilo
	 */
	int diaLimiteMensualidad();

	/** Descarta lo leído de esa organización (tras guardar sus datos): la siguiente consulta vuelve a la base de datos. */
	void olvidar(Long organizacionId);
}
