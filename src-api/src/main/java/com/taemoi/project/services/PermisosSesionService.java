package com.taemoi.project.services;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.dtos.response.PermisosSesionDTO;
import com.taemoi.project.dtos.response.PermisosSesionDTO.Capacidad;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Sede;
import com.taemoi.project.organizacion.AlcanceActual;
import com.taemoi.project.organizacion.AlcanceSesion;
import com.taemoi.project.plataforma.AutoridadPlataforma;
import com.taemoi.project.repositories.SedeRepository;

/**
 * Permisos de la sesión para menús y guards del frontend (pieza E8-1, D8-14; {@code GET /api/auth/permisos}). Solo
 * dice qué enseñar: la autorización real sigue en la cadena de seguridad y en cada servicio.
 *
 * <ul>
 * <li>Roles: los {@link RolMembresia} de las autoridades de la sesión (las membresías activas de la escuela activa,
 * ya calculadas al verificar el JWT; sin consulta). En la sesión solo-profesor ({@link RolMembresia#sesionSoloProfesor},
 * también R+Pr, decisiones 180 y 184) solo {@code PROFESOR}, como {@code /api/auth/roles}.</li>
 * <li>Sedes visibles: alcance total → todas las de la escuela, también inactivas; {@code ROLE_RESPONSABLE_SEDE} fuera
 * de la sesión solo-profesor → las de su alcance; resto, ninguna. {@code sede} es {@code @TenantId}: nunca sale una
 * de otra escuela.</li>
 * <li>{@code variasSedes}: más de una sede activa en la escuela (de la lista ya cargada con total; si no, un
 * {@code COUNT}). {@code tieneGrupos}: {@code alcance.grupos()} no vacío.</li>
 * <li>Sesión solo-plataforma (ninguna autoridad de escuela): roles, sedes y capacidades vacíos y sin consultar la
 * escuela.</li>
 * </ul>
 * Reutiliza el alcance de la petición ({@link AlcanceActual}): como mucho dos consultas pequeñas de sedes, sin N+1.
 */
@Service
public class PermisosSesionService {

	private final AlcanceActual alcanceActual;
	private final SedeRepository sedes;

	public PermisosSesionService(AlcanceActual alcanceActual, SedeRepository sedes) {
		this.alcanceActual = alcanceActual;
		this.sedes = sedes;
	}

	@Transactional(readOnly = true)
	public PermisosSesionDTO permisos() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		Set<Roles> autoridades = autoridades(autenticacion);
		boolean plataforma = tieneAutoridad(autenticacion, AutoridadPlataforma.ADMIN_PLATAFORMA);
		if (autoridades.isEmpty()) {
			return new PermisosSesionDTO(List.of(), plataforma, List.of(), false, false, List.of());
		}
		boolean soloProfesor = RolMembresia.sesionSoloProfesor(autoridades);
		AlcanceSesion alcance = alcanceActual.obtener();
		boolean tieneGrupos = !alcance.grupos().isEmpty();

		List<Sede> visibles;
		long activas;
		if (alcance.total()) {
			visibles = sedes.findAllByOrderByIdAsc();
			activas = visibles.stream().filter(Sede::isActiva).count();
		} else {
			visibles = !soloProfesor && autoridades.contains(Roles.ROLE_RESPONSABLE_SEDE) && !alcance.sedes().isEmpty()
					? sedes.findByIdInOrderByIdAsc(alcance.sedes())
					: List.of();
			activas = sedes.countByActivaTrue();
		}
		return new PermisosSesionDTO(roles(autoridades, soloProfesor), plataforma,
				visibles.stream().map(s -> new PermisosSesionDTO.Sede(s.getId(), s.getNombre(), s.isActiva())).toList(),
				activas > 1, tieneGrupos, capacidades(autoridades, tieneGrupos));
	}

	/**
	 * Capacidades por autoridades (D8-14): MIEMBROS, SEDES y ASIGNAR_PROFESORES con gestión; DAR_GESTION solo el
	 * propietario (D8); MIS_GRUPOS en la sesión solo-profesor siempre y, fuera de ella, con PROFESOR y grupos (217);
	 * MI_CAJA con PROFESOR. En el orden de {@link Capacidad}.
	 */
	static List<Capacidad> capacidades(Set<Roles> autoridades, boolean tieneGrupos) {
		boolean gestion = autoridades.contains(Roles.ROLE_ADMIN) || autoridades.contains(Roles.ROLE_MANAGER);
		boolean profesor = autoridades.contains(Roles.ROLE_PROFESOR);
		Set<Capacidad> resultado = EnumSet.noneOf(Capacidad.class);
		if (gestion) {
			resultado.add(Capacidad.MIEMBROS);
			resultado.add(Capacidad.SEDES);
			resultado.add(Capacidad.ASIGNAR_PROFESORES);
		}
		if (autoridades.contains(Roles.ROLE_ADMIN)) {
			resultado.add(Capacidad.DAR_GESTION);
		}
		if (RolMembresia.sesionSoloProfesor(autoridades) || (profesor && tieneGrupos)) {
			resultado.add(Capacidad.MIS_GRUPOS);
		}
		if (profesor) {
			resultado.add(Capacidad.MI_CAJA);
		}
		return List.copyOf(resultado);
	}

	private static List<String> roles(Set<Roles> autoridades, boolean soloProfesor) {
		if (soloProfesor) {
			return List.of(RolMembresia.PROFESOR.name());
		}
		List<String> roles = new ArrayList<>();
		for (RolMembresia rol : RolMembresia.values()) {
			if (autoridades.contains(rol.autoridad())) {
				roles.add(rol.name());
			}
		}
		return roles;
	}

	/** Autoridades de escuela de la sesión (las de {@link Roles}); la de plataforma no cuenta. */
	private static Set<Roles> autoridades(Authentication autenticacion) {
		Set<Roles> roles = EnumSet.noneOf(Roles.class);
		if (autenticacion == null || autenticacion.getAuthorities() == null) {
			return roles;
		}
		for (GrantedAuthority autoridad : autenticacion.getAuthorities()) {
			for (Roles rol : Roles.values()) {
				if (rol.name().equals(autoridad.getAuthority())) {
					roles.add(rol);
				}
			}
		}
		return roles;
	}

	private static boolean tieneAutoridad(Authentication autenticacion, String nombre) {
		return autenticacion != null && autenticacion.getAuthorities() != null
				&& autenticacion.getAuthorities().stream().anyMatch(a -> nombre.equals(a.getAuthority()));
	}
}
