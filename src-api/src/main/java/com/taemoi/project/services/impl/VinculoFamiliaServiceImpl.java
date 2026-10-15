package com.taemoi.project.services.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.OrigenUsuarioAlumno;
import com.taemoi.project.entities.RelacionUsuarioAlumno;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.entities.UsuarioAlumno;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioAlumnoRepository;
import com.taemoi.project.services.VinculoFamiliaService;

@Service
public class VinculoFamiliaServiceImpl implements VinculoFamiliaService {

	/** Zona de «hoy» para la edad (la de los servicios, p. ej. {@code ConsentimientoService.ZONA}). */
	private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

	private final UsuarioAlumnoRepository vinculos;

	public VinculoFamiliaServiceImpl(UsuarioAlumnoRepository vinculos) {
		this.vinculos = vinculos;
	}

	@Override
	@Transactional
	public boolean vincularAlta(Usuario usuario, Alumno alumno) {
		if (usuario == null || usuario.getId() == null || alumno == null || alumno.getId() == null) {
			throw new IllegalArgumentException("La cuenta y el alumno deben existir para vincularlos.");
		}
		// usuario_alumno no es @TenantId: la organización de la fila es la del alumno y tiene que ser la de la
		// sesión (los alumnos de las altas se cargan filtrados por ella).
		Long organizacionId = alumno.getOrganizacionId();
		Long sesion = TenantContext.get();
		if (organizacionId == null || !Objects.equals(organizacionId, sesion)) {
			throw new IllegalStateException("El alumno " + alumno.getId() + " no es de la escuela de la sesión ("
					+ sesion + "): no se vincula.");
		}
		if (vinculos.existsById(new UsuarioAlumno.Clave(usuario.getId(), alumno.getId()))) {
			return false;
		}
		RelacionUsuarioAlumno relacion = RelacionUsuarioAlumno.porEdad(aFecha(alumno.getFechaNacimiento()),
				LocalDate.now(ZONA));
		vinculos.save(new UsuarioAlumno(usuario.getId(), alumno.getId(), organizacionId, relacion,
				OrigenUsuarioAlumno.REGISTRO));
		return true;
	}

	private static LocalDate aFecha(Date fecha) {
		if (fecha == null) {
			return null;
		}
		if (fecha instanceof java.sql.Date sql) {
			return sql.toLocalDate();
		}
		return fecha.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
	}
}
