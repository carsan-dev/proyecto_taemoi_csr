package com.taemoi.project.services.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.services.AccesoFamiliaService;
import com.taemoi.project.utils.EmailUtils;

@Service
public class AccesoFamiliaServiceImpl implements AccesoFamiliaService {

	/** Valor de {@code :email} cuando la cuenta no puede vincular por email (nunca coincide). */
	private static final String SIN_EMAIL = "";

	private final AlumnoRepository alumnos;

	public AccesoFamiliaServiceImpl(AlumnoRepository alumnos) {
		this.alumnos = alumnos;
	}

	@Override
	public List<Long> idsAccesibles(Usuario usuario, Long organizacionId) {
		if (!consultable(usuario, organizacionId)) {
			return List.of();
		}
		return alumnos.idsAccesiblesComoFamilia(usuario.getId(), emailParaVincular(usuario), organizacionId);
	}

	@Override
	public boolean puedeAcceder(Usuario usuario, Long organizacionId, Long alumnoId) {
		if (alumnoId == null || !consultable(usuario, organizacionId)) {
			return false;
		}
		return alumnos.contarAlumnoAccesibleComoFamilia(usuario.getId(), emailParaVincular(usuario), organizacionId,
				alumnoId) > 0;
	}

	@Override
	public boolean tieneAlgunoAccesible(Usuario usuario, Long organizacionId) {
		if (!consultable(usuario, organizacionId)) {
			return false;
		}
		return alumnos.contarAccesiblesComoFamilia(usuario.getId(), emailParaVincular(usuario), organizacionId) > 0;
	}

	@Override
	public List<Alumno> alumnosAccesiblesEnOrganizacionActiva(Usuario usuario) {
		List<Long> ids = idsAccesibles(usuario, TenantContext.get());
		if (ids.isEmpty()) {
			return List.of();
		}
		// Carga filtrada por @TenantId (la organización activa es la consultada).
		List<Alumno> cargados = new ArrayList<>(alumnos.findAllById(ids));
		cargados.sort(Comparator.comparing(Alumno::getId));
		return cargados;
	}

	private static boolean consultable(Usuario usuario, Long organizacionId) {
		return usuario != null && usuario.getId() != null && organizacionId != null;
	}

	private static String emailParaVincular(Usuario usuario) {
		String email = EmailUtils.normalizeEmail(usuario.getEmail());
		return EmailUtils.puedeVincularAlumnos(email) ? email : SIN_EMAIL;
	}
}
