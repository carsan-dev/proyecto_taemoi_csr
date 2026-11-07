package com.taemoi.project.services.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.services.ComportamientoLegadoService;
import com.taemoi.project.services.DiaLimiteMensualidadService;

/**
 * Lee {@code organizacion.comportamiento_legado_taemoi} de la organización activa. El valor se guarda en
 * memoria {@link #VIGENCIA_MS} milisegundos por organización: se consulta en bucles (cargas mensuales,
 * tesorería) y solo cambia a mano (SQL), así que un cambio tarda como mucho ese tiempo en aplicarse.
 *
 * <p>Pieza 394: la misma lectura guarda {@code organizacion.dia_limite_mensualidad} ({@link DiaLimiteMensualidadService}),
 * así que el pendiente vencido, que pide los dos valores, no suma consultas. Ese sí lo cambia la escuela («Datos de la
 * escuela»): al guardarlo se llama a {@link #olvidar} y el cambio se aplica al momento (una sola instancia de la API).
 */
@Service
public class ComportamientoLegadoServiceImpl implements ComportamientoLegadoService, DiaLimiteMensualidadService {

	static final long VIGENCIA_MS = 30_000L;

	private record Lectura(boolean valor, int diaLimite, long leidaEn) {
	}

	private final OrganizacionRepository organizaciones;
	private final Map<Long, Lectura> lecturas = new ConcurrentHashMap<>();

	private final LongSupplier reloj;

	@Autowired
	public ComportamientoLegadoServiceImpl(OrganizacionRepository organizaciones) {
		this(organizaciones, System::currentTimeMillis);
	}

	/** Para los tests: reloj controlable (milisegundos) en vez de {@link System#currentTimeMillis()}. */
	ComportamientoLegadoServiceImpl(OrganizacionRepository organizaciones, LongSupplier reloj) {
		this.organizaciones = organizaciones;
		this.reloj = reloj;
	}

	@Override
	public boolean activo() {
		return lectura().valor();
	}

	@Override
	public int diaLimiteMensualidad() {
		return lectura().diaLimite();
	}

	@Override
	public void olvidar(Long organizacionId) {
		if (organizacionId != null) {
			lecturas.remove(organizacionId);
		}
	}

	/** Lo leído de la organización activa, vigente {@link #VIGENCIA_MS}; si no, una consulta (findById). */
	private Lectura lectura() {
		Long organizacion = TenantContext.get();
		if (organizacion == null) {
			throw new IllegalStateException(
					"No hay organización activa en este hilo: fíjala con TenantContext antes de acceder a datos");
		}
		long ahora = reloj.getAsLong();
		Lectura lectura = lecturas.get(organizacion);
		if (lectura == null || ahora - lectura.leidaEn() > VIGENCIA_MS) {
			Organizacion escuela = organizaciones.findById(organizacion).orElse(null);
			boolean valor = escuela != null && Boolean.TRUE.equals(escuela.getComportamientoLegadoTaemoi());
			int dia = escuela != null && escuela.getDiaLimiteMensualidad() != null ? escuela.getDiaLimiteMensualidad()
					: Organizacion.DIA_LIMITE_MENSUALIDAD_POR_DEFECTO;
			lectura = new Lectura(valor, dia, ahora);
			lecturas.put(organizacion, lectura);
		}
		return lectura;
	}
}
