package com.taemoi.project.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.RolFamiliar;
import com.taemoi.project.entities.Tarifa;

/**
 * Tarifas de la organización activa (tabla {@code tarifa}).
 *
 * <p>Sustituye a {@code TipoTarifa} + {@code TarifaConfig} desde V27: las altas y cambios de tarifa
 * escriben {@code alumno_deporte.tarifa_id} (y, hasta la contracción, {@code tipo_tarifa} en
 * paralelo) y la cuantía por defecto sale de aquí. Los tests de paridad comprueban que da lo mismo
 * que {@code TarifaConfig} para Taemoi.
 *
 * <p>Regla del importe ({@link #calcularImporte}), la de {@code TarifaConfig.obtenerCuantiaConRol}
 * expresada con datos:
 * <ul>
 * <li>{@code PADRE}/{@code MADRE} y la tarifa tiene {@code importe_adulto}: ese importe.</li>
 * <li>{@code HIJO}/{@code HIJA} y la tarifa tiene {@code importe_menor}: ese importe.</li>
 * <li>En cualquier otro caso (sin rol, {@code NINGUNO}, o tarifa sin importes por rol):
 * {@code importe}.</li>
 * </ul>
 * La edad del alumno no interviene: {@code TarifaConfig} tampoco la usa ({@code PADRES_HIJOS}
 * depende del rol, {@code HERMANOS} es fijo por miembro).
 */
public interface TarifasService {

	Optional<Tarifa> buscarPorCodigo(String codigo);

	/** @throws IllegalArgumentException si el código es nulo o no existe en la organización activa. */
	Tarifa obtenerPorCodigo(String codigo);

	/** Tarifas activas, en orden de alta. */
	List<Tarifa> listarActivas();

	/**
	 * Importe general de la tarifa (como {@code TarifaConfig.obtenerCuantia}).
	 *
	 * @throws IllegalArgumentException si el código es nulo o no existe.
	 */
	BigDecimal obtenerImporte(String codigoTarifa);

	/**
	 * Importe según el rol familiar (como {@code TarifaConfig.obtenerCuantiaConRol}).
	 *
	 * @throws IllegalArgumentException si el código es nulo o no existe.
	 */
	BigDecimal obtenerImporte(String codigoTarifa, RolFamiliar rolFamiliar);

	/** Regla de la clase sobre una tarifa ya cargada (no consulta la base). */
	BigDecimal calcularImporte(Tarifa tarifa, RolFamiliar rolFamiliar);

	/**
	 * Si la tarifa se admite en la disciplina (como {@code PreinscripcionService.validarDatosDeporte}):
	 * tarifa sin disciplina, o con esa disciplina.
	 *
	 * @throws IllegalArgumentException si la tarifa es nula o no existe.
	 */
	boolean admiteDisciplina(String codigoTarifa, String codigoDisciplina);

	/**
	 * Escribe la tarifa de una inscripción por su código: {@code tarifa_id} (la tarifa de la organización
	 * activa) y, en paralelo hasta la contracción, {@code tipo_tarifa} si el código es de
	 * {@code TipoTarifa} (si no, NULL). Código nulo: las dos a NULL. Un código del enum que la organización
	 * no tiene deja {@code tarifa_id} a NULL (WARN), como el código anterior.
	 *
	 * @throws IllegalArgumentException si el código no es ni de la organización ni de {@code TipoTarifa}
	 *         (el mismo error que {@code TipoTarifa.valueOf}).
	 */
	void asignarTarifa(AlumnoDeporte inscripcion, String codigoTarifa);

	/**
	 * Si la tarifa exige rol familiar (la preinscripción lo pide): tiene importes por rol
	 * ({@code PADRES_HIJOS} en Taemoi).
	 */
	boolean requiereRolFamiliar(Tarifa tarifa);

	/**
	 * Si la tarifa exige grupo familiar (la preinscripción lo pide). Regla heredada por código
	 * ({@code HERMANOS}): la tabla no tiene todavía una columna para ello.
	 */
	boolean requiereGrupoFamiliar(Tarifa tarifa);
}
