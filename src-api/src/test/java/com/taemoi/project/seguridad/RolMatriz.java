package com.taemoi.project.seguridad;

import java.util.List;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.taemoi.project.entities.Roles;
import com.taemoi.project.plataforma.AutoridadPlataforma;

/**
 * Columnas de la matriz: las autoridades que deja en el contexto {@code JwtAuthenticationFilter} para cada tipo
 * de sesión (PROPIETARIO→ROLE_ADMIN; ADMINISTRADOR→ROLE_MANAGER; FAMILIA→ROLE_USER;
 * RESPONSABLE_SEDE→ROLE_RESPONSABLE_SEDE; PROFESOR→ROLE_PROFESOR; un administrador de plataforma sin membresía,
 * SOLO ROLE_ADMIN_PLATAFORMA). El anónimo es el token que pone {@code AnonymousAuthenticationFilter}. Las columnas
 * nuevas van al final para que las antiguas no cambien de posición.
 */
enum RolMatriz {
	ANONIMO(null),
	FAMILIA(Roles.ROLE_USER.name()),
	GESTOR(Roles.ROLE_MANAGER.name()),
	PROPIETARIO(Roles.ROLE_ADMIN.name()),
	SOLO_PLATAFORMA(AutoridadPlataforma.ADMIN_PLATAFORMA),
	RESPONSABLE_SEDE(Roles.ROLE_RESPONSABLE_SEDE.name()),
	PROFESOR(Roles.ROLE_PROFESOR.name());

	private final String autoridad;

	RolMatriz(String autoridad) {
		this.autoridad = autoridad;
	}

	Authentication autenticacion() {
		if (autoridad == null) {
			return new AnonymousAuthenticationToken("matriz-permisos", "anonymousUser",
					AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
		}
		// Mismo tipo de token que crea JwtAuthenticationFilter (autenticado, con las autoridades de la sesión).
		return new UsernamePasswordAuthenticationToken("matriz-" + name().toLowerCase() + "@example.test", null,
				List.of(new SimpleGrantedAuthority(autoridad)));
	}
}
