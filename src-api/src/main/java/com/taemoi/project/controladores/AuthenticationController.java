package com.taemoi.project.controladores;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.request.LoginRequest;
import com.taemoi.project.dtos.request.RegistroRequest;
import com.taemoi.project.dtos.response.JwtAuthenticationResponse;
import com.taemoi.project.entidades.Usuario;
import com.taemoi.project.servicios.AuthenticationService;

import lombok.RequiredArgsConstructor;

/**
 * Controlador REST que gestiona las operaciones de autenticación y registro de usuarios.
 * Proporciona endpoints para registrar nuevos usuarios y para iniciar sesión.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthenticationController {

	/**
     * Inyección del servicio de autenticación.
     */
	@Autowired AuthenticationService authenticationService;

    /**
     * Registra un nuevo usuario en el sistema.
     *
     * @param request Datos de registro del nuevo usuario.
     * @return ResponseEntity que contiene la respuesta de autenticación con el token JWT.
     */
	@PostMapping("/signup")
	public ResponseEntity<JwtAuthenticationResponse> signup(@RequestBody RegistroRequest request) {
		JwtAuthenticationResponse response = authenticationService.signup(request);
		return ResponseEntity.ok(response);
	}

    /**
     * Inicia sesión para un usuario existente en el sistema.
     *
     * @param request Datos de inicio de sesión del usuario.
     * @return ResponseEntity que contiene la respuesta de autenticación con el token JWT.
     */
	@PostMapping("/signin")
	public ResponseEntity<JwtAuthenticationResponse> signin(@RequestBody LoginRequest request) {
		JwtAuthenticationResponse response = authenticationService.signin(request);
		return ResponseEntity.ok(response);
	}
	
	/**
	 * Obtiene los roles del usuario autenticado.
	 * 
	 * @return Conjunto de cadenas que representan los roles del usuario.
	 */
    @GetMapping("/roles")
    public Set<String> obtenerRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return usuario.getRoles().stream().map(Enum::name).collect(Collectors.toSet());
    }
}