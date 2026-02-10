package com.taemoi.project.services.impl;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.services.AlumnoAccessControlService;
import com.taemoi.project.utils.EmailUtils;

@Service
public class AlumnoAccessControlServiceImpl implements AlumnoAccessControlService {

	private final AlumnoRepository alumnoRepository;

	public AlumnoAccessControlServiceImpl(AlumnoRepository alumnoRepository) {
		this.alumnoRepository = alumnoRepository;
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

		String email = EmailUtils.normalizeEmail(authentication.getName());
		if (email == null || email.isBlank()) {
			return false;
		}

		return alumnoRepository.existsByIdAndEmailIgnoreCaseAndActivoTrue(alumnoId, email);
	}
}
