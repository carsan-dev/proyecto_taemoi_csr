package com.taemoi.project.services.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.services.ComportamientoLegadoService;

/**
 * Lee {@code organizacion.comportamiento_legado_taemoi} de la organización activa. El valor se guarda en
 * memoria {@link #VIGENCIA_MS} milisegundos por organización: se consulta en bucles (cargas mensuales,
 * tesorería) y solo cambia a mano (SQL), así que un cambio tarda como mucho ese tiempo en aplicarse.
 */
@Service
public class ComportamientoLegadoServiceImpl implements ComportamientoLegadoService {

	static final long VIGENCIA_MS = 30_000L;

	private record Lectura(boolean valor, long leidaEn) {
	}

	private final OrganizacionRepository organizaciones;
	private final Map<Long, Lectura> lecturas = new ConcurrentHashMap<>();

	public ComportamientoLegadoServiceImpl(OrganizacionRepository organizaciones) {
		this.organizaciones = organizaciones;
	}

	@Override
	public boolean activo() {
		Long organizacion = TenantContext.get();
		if (organizacion == null) {
			throw new IllegalStateException(
					"No hay organización activa en este hilo: fíjala con TenantContext antes de acceder a datos");
		}
		long ahora = System.currentTimeMillis();
		Lectura lectura = lecturas.get(organizacion);
		if (lectura == null || ahora - lectura.leidaEn() > VIGENCIA_MS) {
			boolean valor = organizaciones.findById(organizacion)
					.map(o -> Boolean.TRUE.equals(o.getComportamientoLegadoTaemoi()))
					.orElse(false);
			lectura = new Lectura(valor, ahora);
			lecturas.put(organizacion, lectura);
		}
		return lectura.valor();
	}
}
