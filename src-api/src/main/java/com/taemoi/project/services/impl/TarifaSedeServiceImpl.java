package com.taemoi.project.services.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.acceso.GestionPorSede;
import com.taemoi.project.config.ValidacionRelojConfig;
import com.taemoi.project.dtos.request.TarifaSedeRequest;
import com.taemoi.project.dtos.response.TarifaSedeDTO;
import com.taemoi.project.entities.Sede;
import com.taemoi.project.entities.Tarifa;
import com.taemoi.project.entities.TarifaSede;
import com.taemoi.project.organizacion.AlcanceSesion;
import com.taemoi.project.repositories.SedeRepository;
import com.taemoi.project.repositories.TarifaRepository;
import com.taemoi.project.repositories.TarifaSedeRepository;
import com.taemoi.project.services.ResolutorCuantiaMensualidad;
import com.taemoi.project.services.TarifaSedeService;
import com.taemoi.project.services.TarifasService;

/**
 * Entrega 6-4 (D6-27 a D6-36). Las lecturas de tarifa y sede van por repositorios filtrados por la escuela activa
 * ({@code @TenantId}): una tarifa o una sede de otra escuela no se encuentra (400).
 */
@Service
public class TarifaSedeServiceImpl implements TarifaSedeService {

	/** Clave única de V31_4 (organizacion_id, tarifa_id, sede_id, vigente_desde). */
	private static final String CLAVE_UNICA = "uk_tarifa_sede_vigencia";

	/** DECIMAL(8,2): hasta 999999.99. */
	private static final BigDecimal IMPORTE_MAXIMO = new BigDecimal("999999.99");

	private final TarifaSedeRepository tarifasSede;
	private final TarifaRepository tarifasRepositorio;
	private final SedeRepository sedes;
	private final TarifasService tarifas;
	private final GestionPorSede gestionPorSede;
	/** Mes en curso para el borrado: Europe/Madrid, no la zona de la JVM (la CI va en UTC). Fijable en los tests. */
	private Clock reloj = Clock.system(ValidacionRelojConfig.ZONA);

	public TarifaSedeServiceImpl(TarifaSedeRepository tarifasSede, TarifaRepository tarifasRepositorio,
			SedeRepository sedes, TarifasService tarifas, GestionPorSede gestionPorSede) {
		this.tarifasSede = tarifasSede;
		this.tarifasRepositorio = tarifasRepositorio;
		this.sedes = sedes;
		this.tarifas = tarifas;
		this.gestionPorSede = gestionPorSede;
	}

	void setReloj(Clock reloj) {
		this.reloj = reloj;
	}

	@Override
	@Transactional(readOnly = true)
	public ResolutorCuantiaMensualidad preparar() {
		List<TarifaSede> filas = tarifasSede.findAll();
		return filas.isEmpty() ? ResolutorCuantiaMensualidad.identidad()
				: new ResolutorCuantiaMensualidad(filas, tarifas);
	}

	@Override
	@Transactional(readOnly = true)
	public List<TarifaSedeDTO> listar() {
		Optional<Set<Long>> suyas = gestionPorSede.restriccion().map(AlcanceSesion::sedes);
		List<TarifaSede> filas = tarifasSede.findAllByOrderByTarifaIdAscSedeIdAscVigenteDesdeAsc();
		if (suyas.isPresent()) {
			filas = filas.stream().filter(f -> suyas.get().contains(f.getSedeId())).toList();
		}
		if (filas.isEmpty()) {
			return List.of();
		}
		Map<Long, String> codigos = tarifasRepositorio.findAll().stream()
				.collect(Collectors.toMap(Tarifa::getId, Tarifa::getCodigo));
		return filas.stream().map(f -> dto(f, codigos.get(f.getTarifaId()))).toList();
	}

	@Override
	@Transactional
	public TarifaSedeDTO crear(TarifaSedeRequest solicitud) {
		if (solicitud == null) {
			throw new IllegalArgumentException("Faltan los datos de la tarifa de la sede.");
		}
		Tarifa tarifa = tarifas.buscarPorCodigo(solicitud.codigoTarifa())
				.orElseThrow(() -> new IllegalArgumentException("La tarifa no existe en la escuela."));
		if (solicitud.sedeId() == null) {
			throw new IllegalArgumentException("Indica la sede.");
		}
		Sede sede = sedes.buscarEnEscuelaActiva(solicitud.sedeId())
				.orElseThrow(() -> new IllegalArgumentException("La sede no existe en la escuela."));
		LocalDate vigenteDesde = mes(solicitud.vigenteDesde()).atDay(1);
		// D6-37: no se crean para meses pasados (los cargos de esos meses ya pueden existir y no se recalculan, D6-32);
		// el mes en curso sí (y después ya no se puede borrar).
		if (vigenteDesde.isBefore(primeroDelMesEnCurso())) {
			throw new IllegalArgumentException(
					"No se crean tarifas de la sede para meses pasados: indica el mes en curso o uno posterior.");
		}
		BigDecimal importe = importe(solicitud.importe(), "importe");
		if (importe == null) {
			throw new IllegalArgumentException("Indica el importe.");
		}
		BigDecimal adulto = importe(solicitud.importeAdulto(), "importeAdulto");
		BigDecimal menor = importe(solicitud.importeMenor(), "importeMenor");
		// Misma forma que la tarifa base: si cobra por rol, la excepción también (si no, un PADRE de PADRES_HIJOS
		// pagaría el importe general, 0 €); si no cobra por rol, la excepción tampoco.
		exigirMismoImportePorRol(tarifa.getImporteAdulto(), adulto, "importeAdulto", "de adulto");
		exigirMismoImportePorRol(tarifa.getImporteMenor(), menor, "importeMenor", "de menor");
		if (tarifasSede.existsByTarifaIdAndSedeIdAndVigenteDesde(tarifa.getId(), sede.getId(), vigenteDesde)) {
			throw new IllegalStateException("Ya existe una tarifa de la sede para esa tarifa y ese mes.");
		}
		TarifaSede fila = new TarifaSede();
		fila.setTarifaId(tarifa.getId());
		fila.setSedeId(sede.getId());
		fila.setImporte(importe);
		fila.setImporteAdulto(adulto);
		fila.setImporteMenor(menor);
		fila.setVigenteDesde(vigenteDesde);
		try {
			return dto(tarifasSede.saveAndFlush(fila), tarifa.getCodigo());
		} catch (DataIntegrityViolationException error) {
			// Dos altas simultáneas del mismo mes: la clave única decide (409). Cualquier otro fallo de integridad (p. ej.
			// una FK) no es un duplicado: se propaga tal cual.
			if (violaClaveUnica(error)) {
				throw new IllegalStateException("Ya existe una tarifa de la sede para esa tarifa y ese mes.");
			}
			throw error;
		}
	}

	@Override
	@Transactional
	public void borrar(Long id) {
		TarifaSede fila = tarifasSede.buscarEnEscuelaActiva(id)
				.orElseThrow(() -> new NoSuchElementException("La tarifa de la sede no existe."));
		if (!fila.getVigenteDesde().isAfter(primeroDelMesEnCurso())) {
			throw new IllegalStateException(
					"Solo se borran las tarifas de la sede de meses futuros: el histórico no se reescribe.");
		}
		tarifasSede.delete(fila);
	}

	// ------------------------------------------------------------------ utilidades

	/** Día 1 del mes en curso, siempre en Madrid, sea cual sea la zona del reloj o de la JVM. */
	private LocalDate primeroDelMesEnCurso() {
		return YearMonth.from(reloj.instant().atZone(ValidacionRelojConfig.ZONA)).atDay(1);
	}

	/** La violación es la de la clave única (tarifa, sede, mes): el nombre de la clave aparece en la causa. */
	static boolean violaClaveUnica(Throwable error) {
		for (Throwable causa = error; causa != null; causa = causa.getCause()) {
			if (causa instanceof org.hibernate.exception.ConstraintViolationException violacion
					&& violacion.getConstraintName() != null
					&& violacion.getConstraintName().contains(CLAVE_UNICA)) {
				return true;
			}
			if (causa.getMessage() != null && causa.getMessage().contains(CLAVE_UNICA)) {
				return true;
			}
			if (causa.getCause() == causa) {
				break;
			}
		}
		return false;
	}

	private static YearMonth mes(String texto) {
		if (texto == null || !texto.matches("\\d{4}-\\d{2}")) {
			throw new IllegalArgumentException("El mes debe tener el formato YYYY-MM.");
		}
		try {
			YearMonth mes = YearMonth.parse(texto);
			if (mes.getYear() < 1000) {
				throw new IllegalArgumentException("El mes debe tener el formato YYYY-MM.");
			}
			return mes;
		} catch (DateTimeParseException e) {
			throw new IllegalArgumentException("El mes debe tener el formato YYYY-MM.");
		}
	}

	/**
	 * Mayor o igual que 0 (como el catálogo: PADRES_HIJOS tiene importe general 0), con 2 decimales como máximo y dentro
	 * de DECIMAL(8,2); {@code null} si no llega.
	 */
	private static BigDecimal importe(BigDecimal valor, String campo) {
		if (valor == null) {
			return null;
		}
		if (valor.signum() < 0) {
			throw new IllegalArgumentException("El campo " + campo + " no puede ser negativo.");
		}
		if (valor.stripTrailingZeros().scale() > 2 || valor.compareTo(IMPORTE_MAXIMO) > 0) {
			throw new IllegalArgumentException("El campo " + campo + " no es un importe válido.");
		}
		return valor.setScale(2);
	}

	private static void exigirMismoImportePorRol(BigDecimal deLaTarifa, BigDecimal deLaExcepcion, String campo,
			String descripcion) {
		if (deLaTarifa != null && deLaExcepcion == null) {
			throw new IllegalArgumentException(
					"La tarifa tiene importe " + descripcion + ": indica también " + campo + ".");
		}
		if (deLaTarifa == null && deLaExcepcion != null) {
			throw new IllegalArgumentException("La tarifa no tiene importe " + descripcion + ": quita " + campo + ".");
		}
	}

	private static TarifaSedeDTO dto(TarifaSede fila, String codigoTarifa) {
		return new TarifaSedeDTO(fila.getId(), codigoTarifa, fila.getSedeId(), fila.getImporte(),
				fila.getImporteAdulto(), fila.getImporteMenor(), YearMonth.from(fila.getVigenteDesde()).toString());
	}
}
