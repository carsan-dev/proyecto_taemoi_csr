package com.taemoi.project.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.taemoi.project.dtos.response.CuantiaPersonalizadaSuperiorDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.RolFamiliar;
import com.taemoi.project.entities.Tarifa;
import com.taemoi.project.entities.TarifaSede;

/**
 * Cuantía que se cobra en una mensualidad (entrega 6-4, D6-27 a D6-34). Se prepara UNA vez por generación con las
 * tarifas por sede de la escuela activa ({@link TarifaSedeService#preparar()}) y no consulta la base de datos.
 *
 * <ul>
 * <li>Sin filas (Moiskimdo), sin tarifa en la inscripción, sin cuantía, sin sede principal o sin excepción vigente en
 * el mes: devuelve {@code alumnoDeporte.getCuantiaTarifa()}, la MISMA referencia (D6-29).</li>
 * <li>Con excepción vigente (la fila con el mayor {@code vigente_desde} menor o igual que el día 1 del mes, D6-34) y la
 * cuantía «base» (igual, redondeada a 2 decimales, que {@link TarifasService#calcularImporte} de la tarifa con el rol
 * de la inscripción, D6-27): el importe de la excepción con la misma regla de rol.</li>
 * <li>Cuantía personalizada (distinta de la base): se cobra tal cual. Si es MAYOR que la de la sede se anota en
 * {@link #personalizadasSuperiores()} para revisarla (D6-28).</li>
 * </ul>
 * La sede es la sede principal del alumno del cargo en el momento de generar, la misma que fija
 * {@code ProductoAlumno @PrePersist} (D6-29). No es seguro entre hilos: uno por generación.
 */
public final class ResolutorCuantiaMensualidad {

	private static final ResolutorCuantiaMensualidad IDENTIDAD = new ResolutorCuantiaMensualidad(List.of(), null);

	/** (tarifa, sede) → vigente_desde → fila. */
	private final Map<Clave, TreeMap<LocalDate, TarifaSede>> filas;
	private final TarifasService tarifas;
	private final List<CuantiaPersonalizadaSuperiorDTO> superiores = new ArrayList<>();

	public ResolutorCuantiaMensualidad(List<TarifaSede> filas, TarifasService tarifas) {
		Map<Clave, TreeMap<LocalDate, TarifaSede>> porClave = new HashMap<>();
		for (TarifaSede fila : filas) {
			porClave.computeIfAbsent(new Clave(fila.getTarifaId(), fila.getSedeId()), c -> new TreeMap<>())
					.put(fila.getVigenteDesde(), fila);
		}
		this.filas = porClave;
		this.tarifas = tarifas;
	}

	/** Sin tarifas por sede: siempre la cuantía de la inscripción. Compartido (no anota nada). */
	public static ResolutorCuantiaMensualidad identidad() {
		return IDENTIDAD;
	}

	/** Cuantía de la mensualidad de {@code mes} para la inscripción; la sede, la de su alumno. */
	public Double cuantiaMensualidad(AlumnoDeporte alumnoDeporte, YearMonth mes) {
		return cuantiaMensualidad(alumnoDeporte, alumnoDeporte == null ? null : alumnoDeporte.getAlumno(), mes);
	}

	/**
	 * Cuantía de la mensualidad de {@code mes} para la inscripción, con la sede principal de {@code alumnoDelCargo} (el
	 * alumno que se pone en el cargo, cuya sede fija {@code @PrePersist}).
	 */
	public Double cuantiaMensualidad(AlumnoDeporte alumnoDeporte, Alumno alumnoDelCargo, YearMonth mes) {
		if (alumnoDeporte == null) {
			return null;
		}
		Double cuantia = alumnoDeporte.getCuantiaTarifa();
		if (filas.isEmpty()) {
			return cuantia;
		}
		Tarifa tarifa = alumnoDeporte.getTarifa();
		Long sedeId = alumnoDelCargo == null ? null : alumnoDelCargo.getSedePrincipalId();
		if (cuantia == null || tarifa == null || tarifa.getId() == null || sedeId == null || mes == null) {
			return cuantia;
		}
		TreeMap<LocalDate, TarifaSede> vigencias = filas.get(new Clave(tarifa.getId(), sedeId));
		Entry<LocalDate, TarifaSede> vigente = vigencias == null ? null : vigencias.floorEntry(mes.atDay(1));
		if (vigente == null) {
			return cuantia;
		}
		RolFamiliar rol = alumnoDeporte.getRolFamiliar();
		BigDecimal base = tarifas.calcularImporte(tarifa, rol);
		BigDecimal deLaSede = importeDeLaSede(vigente.getValue(), rol);
		if (base == null || deLaSede == null) {
			return cuantia;
		}
		BigDecimal actual = BigDecimal.valueOf(cuantia).setScale(2, RoundingMode.HALF_UP);
		if (actual.compareTo(base.setScale(2, RoundingMode.HALF_UP)) == 0) {
			return deLaSede.doubleValue();
		}
		if (actual.compareTo(deLaSede.setScale(2, RoundingMode.HALF_UP)) > 0) {
			superiores.add(new CuantiaPersonalizadaSuperiorDTO(alumnoDelCargo.getId(), nombre(alumnoDelCargo),
					alumnoDeporte.getDeporte(), cuantia, deLaSede.doubleValue()));
		}
		return cuantia;
	}

	/** D6-28: mensualidades resueltas con una cuantía personalizada mayor que la de la sede (en orden de cálculo). */
	public List<CuantiaPersonalizadaSuperiorDTO> personalizadasSuperiores() {
		return Collections.unmodifiableList(new ArrayList<>(superiores));
	}

	/** Misma regla de rol que la tarifa del catálogo ({@link TarifasService#calcularImporte}). */
	private BigDecimal importeDeLaSede(TarifaSede fila, RolFamiliar rol) {
		Tarifa comoTarifa = new Tarifa();
		comoTarifa.setImporte(fila.getImporte());
		comoTarifa.setImporteAdulto(fila.getImporteAdulto());
		comoTarifa.setImporteMenor(fila.getImporteMenor());
		return tarifas.calcularImporte(comoTarifa, rol);
	}

	private static String nombre(Alumno alumno) {
		String nombre = alumno.getNombre() == null ? "" : alumno.getNombre();
		String apellidos = alumno.getApellidos() == null ? "" : alumno.getApellidos();
		return (nombre + " " + apellidos).trim();
	}

	private record Clave(Long tarifaId, Long sedeId) {
	}
}
