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
	 * Sede que recibe un grupo dado de alta sin sede (la API de grupos aún no la pide): la única sede activa de
	 * la escuela activa. Vacío si no tiene ninguna o tiene varias; entonces el grupo queda sin sede
	 * ({@code grupo.sede_id} admite NULL hasta V32) hasta que la API de sedes permita elegirla (entrega 8).
	 */
	public Optional<Long> sedePorDefecto() {
		List<Sede> activas = sedes.findByActivaTrueOrderByIdAsc();
		return activas.size() == 1 ? Optional.of(activas.get(0).getId()) : Optional.empty();
	}
}
