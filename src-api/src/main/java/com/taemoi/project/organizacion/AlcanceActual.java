package com.taemoi.project.organizacion;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.taemoi.project.entities.Roles;

/**
 * Alcance de la petición en curso, leído del {@code SecurityContext} (entrega 5b-2, entrega5-plan.md §3.4).
 * Singleton sin estado: funciona también fuera de una petición web (tareas), donde no hay sesión y da
 * {@link AlcanceSesion#NINGUNO}.
 *
 * <ol>
 * <li>Autenticación con {@code ROLE_ADMIN} o {@code ROLE_MANAGER} → {@link AlcanceSesion#TOTAL}, sea cual sea el
 * tipo de token (así los tests WebMvc con usuarios simulados siguen con alcance total).</li>
 * <li>Si no, una {@link AutenticacionEscuela} → su alcance.</li>
 * <li>En cualquier otro caso → {@link AlcanceSesion#NINGUNO} (falla cerrado).</li>
 * </ol>
 */
@Component
public class AlcanceActual {

	public AlcanceSesion obtener() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion == null || !autenticacion.isAuthenticated()) {
			return AlcanceSesion.NINGUNO;
		}
		if (tieneGestion(autenticacion)) {
			return AlcanceSesion.TOTAL;
		}
		if (autenticacion instanceof AutenticacionEscuela escuela) {
			return escuela.alcance();
		}
		return AlcanceSesion.NINGUNO;
	}

	private static boolean tieneGestion(Authentication autenticacion) {
		for (GrantedAuthority autoridad : autenticacion.getAuthorities()) {
			String nombre = autoridad.getAuthority();
			if (Roles.ROLE_ADMIN.name().equals(nombre) || Roles.ROLE_MANAGER.name().equals(nombre)) {
				return true;
			}
		}
		return false;
	}
}
