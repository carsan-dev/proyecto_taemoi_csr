package com.taemoi.project.acceso;

import java.util.Map;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.method.MethodAuthorizationDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Pieza 8b-3l (P-4, decisión 285): respuesta del {@code DELETE /api/alumnos/{id}} denegado. Solo el propietario
 * (PROPIETARIO, {@code ROLE_ADMIN}) elimina alumnos definitivamente; a cualquier otra sesión que llegue al método (el
 * gestor, sobre todo) se le responde 403 con un motivo claro, en lugar del «Acceso denegado.» genérico, para que las
 * pantallas lo enseñen tal cual. La decisión sigue siendo la del {@code @PreAuthorize} (y la de la matriz de permisos).
 */
@Component
public class SoloPropietarioEliminaAlumnos implements MethodAuthorizationDeniedHandler {

	public static final String MENSAJE = "Solo el propietario puede eliminar alumnos definitivamente.";

	@Override
	public Object handleDeniedInvocation(MethodInvocation methodInvocation, AuthorizationResult authorizationResult) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("mensaje", MENSAJE));
	}
}
