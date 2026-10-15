package com.taemoi.project.services.impl;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.AccesoFamiliaService;
import com.taemoi.project.services.AlumnoAccessControlService;
import com.taemoi.project.utils.EmailUtils;

@Service
public class AlumnoAccessControlServiceImpl implements AlumnoAccessControlService {

	private final AccesoFamiliaService accesoFamilia;
	private final UsuarioRepository usuarios;

	public AlumnoAccessControlServiceImpl(AccesoFamiliaService accesoFamilia, UsuarioRepository usuarios) {
		this.accesoFamilia = accesoFamilia;
		this.usuarios = usuarios;
	}

	@Override
	public boolean canAccessAlumno(Long alumnoId, Authentication authentication) {
		if (alumnoId == null || authentication == null || !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {
			return false;
		}

		Set<String> roles = authentication.getAuthorities().stream()
				.map(grantedAuthority -> grantedAuthority.getAuthority())
				.collect(Collectors.toSet());

		if (roles.contains("ROLE_ADMIN") || roles.contains("ROLE_MANAGER")) {
			return true;
		}

		if (!roles.contains("ROLE_USER")) {
			return false;
		}

		// Familia: regla única de AccesoFamiliaService, en la escuela de la sesión.
		Long organizacionId = TenantContext.get();
		if (organizacionId == null) {
			return false;
		}
		Usuario usuario = cuentaDe(authentication);
		return usuario != null && accesoFamilia.puedeAcceder(usuario, organizacionId, alumnoId);
	}

	/** La sesión lleva la cuenta como principal; con otra autenticación se busca por su email. */
	private Usuario cuentaDe(Authentication authentication) {
		if (authentication.getPrincipal() instanceof Usuario usuario) {
			return usuario;
		}
		String email = EmailUtils.normalizeEmail(authentication.getName());
		if (!EmailUtils.puedeVincularAlumnos(email)) {
			return null;
		}
		return usuarios.findByEmailIgnoreCase(email).orElse(null);
	}
}
