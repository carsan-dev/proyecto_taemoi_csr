package com.taemoi.project.services.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.RolFamiliar;
import com.taemoi.project.entities.Tarifa;
import com.taemoi.project.repositories.TarifaRepository;
import com.taemoi.project.services.CatalogoLegadoTaemoi;
import com.taemoi.project.services.TarifasService;

/**
 * Sin {@code @Transactional} de clase: {@link #calcularImporte} no toca la base (no exige
 * organización activa); el resto usa la transacción del repositorio.
 */
@Service
public class TarifasServiceImpl implements TarifasService {

	private static final Logger log = LoggerFactory.getLogger(TarifasServiceImpl.class);

	/** Regla heredada de la preinscripción: la tarifa de hermanos exige grupo familiar. */
	private static final String CODIGO_HERMANOS = "HERMANOS";

	private final TarifaRepository tarifas;

	public TarifasServiceImpl(TarifaRepository tarifas) {
		this.tarifas = tarifas;
	}

	@Override
	public Optional<Tarifa> buscarPorCodigo(String codigo) {
		if (codigo == null) {
			return Optional.empty();
		}
		// Código exacto (la intercalación de la columna no distingue mayúsculas; el antiguo enum sí)
		return tarifas.findByCodigo(codigo).filter(t -> codigo.equals(t.getCodigo()));
	}

	@Override
	public Tarifa obtenerPorCodigo(String codigo) {
		if (codigo == null) {
			throw new IllegalArgumentException("El tipo de tarifa no puede ser null");
		}
		return buscarPorCodigo(codigo)
				.orElseThrow(() -> new IllegalArgumentException("Tarifa no encontrada: " + codigo));
	}

	@Override
	public List<Tarifa> listarActivas() {
		return tarifas.findByActivaTrueOrderByOrdenAscIdAsc();
	}

	@Override
	public List<Tarifa> listarTodas() {
		return tarifas.findAllByOrderByOrdenAscIdAsc();
	}

	@Override
	public BigDecimal obtenerImporte(String codigoTarifa) {
		return obtenerPorCodigo(codigoTarifa).getImporte();
	}

	@Override
	public BigDecimal obtenerImporte(String codigoTarifa, RolFamiliar rolFamiliar) {
		return calcularImporte(obtenerPorCodigo(codigoTarifa), rolFamiliar);
	}

	@Override
	public BigDecimal calcularImporte(Tarifa tarifa, RolFamiliar rolFamiliar) {
		if (tarifa == null) {
			throw new IllegalArgumentException("El tipo de tarifa no puede ser null");
		}
		if (rolFamiliar != null) {
			switch (rolFamiliar) {
			case PADRE, MADRE -> {
				if (tarifa.getImporteAdulto() != null) {
					return tarifa.getImporteAdulto();
				}
			}
			case HIJO, HIJA -> {
				if (tarifa.getImporteMenor() != null) {
					return tarifa.getImporteMenor();
				}
			}
			case NINGUNO -> {
				// importe general
			}
			}
		}
		return tarifa.getImporte();
	}

	@Override
	@Transactional(readOnly = true)
	public boolean admiteDisciplina(String codigoTarifa, String codigoDisciplina) {
		Tarifa tarifa = obtenerPorCodigo(codigoTarifa);
		return tarifa.getDisciplina() == null
				|| (codigoDisciplina != null && codigoDisciplina.equals(tarifa.getDisciplina().getCodigo()));
	}

	@Override
	public String validarCodigo(String codigoTarifa) {
		if (buscarPorCodigo(codigoTarifa).isEmpty()) {
			throw CatalogoLegadoTaemoi.valorNoValido("TipoTarifa", codigoTarifa);
		}
		return codigoTarifa;
	}

	@Override
	public void asignarTarifa(AlumnoDeporte inscripcion, String codigoTarifa) {
		if (codigoTarifa == null) {
			inscripcion.setTarifa(null);
			return;
		}
		Tarifa tarifa = buscarPorCodigo(codigoTarifa)
				.orElseThrow(() -> CatalogoLegadoTaemoi.valorNoValido("TipoTarifa", codigoTarifa));
		inscripcion.setTarifa(tarifa);
	}

	@Override
	public boolean requiereRolFamiliar(Tarifa tarifa) {
		return tarifa != null && (tarifa.getImporteAdulto() != null || tarifa.getImporteMenor() != null);
	}

	@Override
	public boolean requiereGrupoFamiliar(Tarifa tarifa) {
		return tarifa != null && CODIGO_HERMANOS.equals(tarifa.getCodigo());
	}

}
