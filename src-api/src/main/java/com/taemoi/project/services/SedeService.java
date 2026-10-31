package com.taemoi.project.services;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.taemoi.project.entities.Sede;
import com.taemoi.project.repositories.SedeRepository;

/** Sedes de la escuela activa (fase 3). */
@Service
public class SedeService {

	private final SedeRepository sedes;

	public SedeService(SedeRepository sedes) {
		this.sedes = sedes;
	}

	/**
	 * Sede que recibe lo que se da de alta sin sede: la única sede activa de la escuela activa. Vacío si no tiene
	 * ninguna o tiene varias; {@link #variasActivas()} distingue los dos casos.
	 */
	public Optional<Long> sedePorDefecto() {
		List<Sede> activas = sedes.findByActivaTrueOrderByIdAsc();
		return activas.size() == 1 ? Optional.of(activas.get(0).getId()) : Optional.empty();
	}

	/** Decisiones 368 y 377: la escuela activa tiene dos o más sedes activas, así que la sede hay que indicarla. */
	public boolean variasActivas() {
		return sedes.countByActivaTrue() > 1;
	}
}
