package com.taemoi.project.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.taemoi.project.entities.RolFamiliar;
import com.taemoi.project.entities.Tarifa;

/**
 * Tarifas de la organización activa (tabla {@code tarifa}).
 *
 * <p>Sustituirá a {@code TipoTarifa} + {@code TarifaConfig}. Todavía no lo usa ningún llamador:
 * los tests de paridad comprueban que da lo mismo que {@code TarifaConfig} para Taemoi.
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
}
