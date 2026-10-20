package com.taemoi.project.services;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.taemoi.project.dtos.request.MiembrosRequests;
import com.taemoi.project.dtos.response.MiembrosDTOs;

/**
 * {@code POST /api/miembros} sin carreras con las invitaciones (pieza E8-4, D8-19, riesgo 13). Dos invitaciones a la
 * vez al mismo email sin cuenta: las dos crean la cuenta y la clave única del email hace esperar a la segunda hasta que
 * la primera confirma, y entonces falla ({@link DataIntegrityViolationException}, ya deshecha toda su transacción). Se
 * repite el alta UNA vez: ahora la cuenta existe y sigue el camino de la cuenta existente (sin segundo correo ni token
 * nuevo). Otro fallo de integridad vuelve a fallar en la repetición y se propaga como antes. Sin transacción propia:
 * cada intento es una transacción de {@link GestionMiembrosService#alta}.
 */
@Service
public class AltaDeMiembro {

	private final GestionMiembrosService miembros;

	public AltaDeMiembro(GestionMiembrosService miembros) {
		this.miembros = miembros;
	}

	public MiembrosDTOs.Ficha alta(MiembrosRequests.Alta solicitud) {
		try {
			return miembros.alta(solicitud);
		} catch (DataIntegrityViolationException carrera) {
			return miembros.alta(solicitud);
		}
	}
}
