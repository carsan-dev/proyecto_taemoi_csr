package com.taemoi.project.services;

import org.springframework.security.core.Authentication;

public interface AlumnoAccessControlService {

	boolean canAccessAlumno(Long alumnoId, Authentication authentication);
}
