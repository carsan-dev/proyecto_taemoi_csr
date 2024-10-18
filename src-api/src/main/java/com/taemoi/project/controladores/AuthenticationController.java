package com.taemoi.project.controladores;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.request.LoginRequest;
import com.taemoi.project.dtos.request.RegistroRequest;
import com.taemoi.project.dtos.response.AlumnoParaUsuarioDTO;
import com.taemoi.project.dtos.response.JwtAuthenticationResponse;
import com.taemoi.project.dtos.response.UsuarioConAlumnoAsociadoDTO;
import com.taemoi.project.entidades.Usuario;
import com.taemoi.project.repositorios.UsuarioRepository;
import com.taemoi.project.servicios.AuthenticationService;
import com.taemoi.project.servicios.UsuarioService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controlador REST que gestiona las operaciones de autenticación y registro de
 * usuarios. Proporciona endpoints para registrar nuevos usuarios y para iniciar
 * sesión.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthenticationController {
	private static final Logger logger = LoggerFactory.getLogger(AuthenticationController.class);

	/**
	 * Inyección del servicio de autenticación.
	 */
	@Autowired
	private AuthenticationService authenticationService;

	@Autowired
	private UsuarioService usuarioService;

	@Autowired
	private UsuarioRepository usuarioRepository;

	/**
	 * Registra un nuevo usuario en el sistema.
	 *
	 * @param request Datos de registro del nuevo usuario.
	 * @return ResponseEntity que contiene la respuesta de autenticación con el
	 *         token JWT.
	 */
	@PostMapping("/signup")
	public ResponseEntity<JwtAuthenticationResponse> signup(@Valid @RequestBody RegistroRequest request) {
		if (usuarioRepository.existsByEmail(request.getEmail())) {
			logger.warn("Intento de registro con email ya existente: {}", request.getEmail());
			throw new IllegalArgumentException("Credenciales inválidas.");
		}
		JwtAuthenticationResponse response = authenticationService.signup(request);
		return ResponseEntity.ok(response);
	}

	/**
	 * Inicia sesión para un usuario existente en el sistema.
	 *
	 * @param request Datos de inicio de sesión del usuario.
	 * @return ResponseEntity que contiene la respuesta de autenticación con el
	 *         token JWT.
	 */
	@PostMapping("/signin")
	public ResponseEntity<JwtAuthenticationResponse> signin(@RequestBody LoginRequest request, HttpServletResponse response) {
	    JwtAuthenticationResponse jwtResponse = authenticationService.signin(request);

	    // Crear la cookie HTTP-Only
	    Cookie jwtCookie = new Cookie("jwt", jwtResponse.getToken());
	    jwtCookie.setHttpOnly(true); // No accesible desde JavaScript
	    jwtCookie.setSecure(false); // Solo se envía en conexiones HTTPS
	    jwtCookie.setPath("/"); // Disponible para todo el dominio
	    jwtCookie.setMaxAge(60 * 60 * 10); // 10 horas de validez

	    // Agregar la cookie a la respuesta HTTP
	    response.addCookie(jwtCookie);

	    // Retornar la respuesta con el cuerpo
	    return ResponseEntity.ok(jwtResponse);
	}

	@PostMapping("/logout")
	public ResponseEntity<?> logout(HttpServletResponse response) {
	    Cookie jwtCookie = new Cookie("jwt", null);
	    jwtCookie.setHttpOnly(true);
	    jwtCookie.setSecure(false); // Cambiar a true en producción
	    jwtCookie.setPath("/");
	    jwtCookie.setMaxAge(0); // Eliminar la cookie

	    response.addCookie(jwtCookie);
	    return ResponseEntity.ok().build();
	}
	
	@GetMapping("/auth-status")
	public ResponseEntity<Boolean> checkAuthStatus(HttpServletRequest request) {
	    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
	    boolean isAuthenticated = authentication != null &&
	            authentication.isAuthenticated() &&
	            !(authentication instanceof AnonymousAuthenticationToken);
	    
	    logger.info("Estado de autenticación: {}", isAuthenticated);
	    return ResponseEntity.ok(isAuthenticated);
	}

	/**
	 * Obtiene los roles del usuario autenticado.
	 * 
	 * @return Conjunto de cadenas que representan los roles del usuario.
	 */
	@GetMapping("/roles")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN') || hasRole('ROLE_USER')")
	public ResponseEntity<?> obtenerRoles() {
	    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

	    // Verificar si el usuario está autenticado
	    if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
	        Map<String, String> response = new HashMap<>();
	        response.put("error", "El usuario no está autenticado o es anónimo.");
	        return ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED).body(response);
	    }

	    Object principal = authentication.getPrincipal();

	    if (principal instanceof Usuario) {
	        Usuario usuario = (Usuario) principal;
	        Set<String> roles = usuario.getRoles().stream().map(Enum::name).collect(Collectors.toSet());
	        return ResponseEntity.ok(roles);
	    } else if (principal instanceof UserDetails) {
	        String email = ((UserDetails) principal).getUsername();
	        Usuario usuario = usuarioService.encontrarPorEmail(email)
	            .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con el email: " + email));
	        Set<String> roles = usuario.getRoles().stream().map(Enum::name).collect(Collectors.toSet());
	        return ResponseEntity.ok(roles);
	    } else {
	        throw new ClassCastException("El principal no es un Usuario ni un UserDetails. Tipo: " + principal.getClass().getName());
	    }
	}

	@GetMapping("/user")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN') || hasRole('ROLE_USER')")
	public UsuarioConAlumnoAsociadoDTO obtenerUsuarioAutenticado() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		String email = authentication.getName();
		Optional<Usuario> usuarioOptional = usuarioService.encontrarPorEmail(email);
		if (usuarioOptional.isPresent()) {
			Usuario usuario = usuarioOptional.get();
			UsuarioConAlumnoAsociadoDTO usuarioDTO = new UsuarioConAlumnoAsociadoDTO();
			usuarioDTO.setId(usuario.getId());
			usuarioDTO.setEmail(usuario.getEmail());

			AlumnoParaUsuarioDTO alumnoUsuarioDTO = AlumnoParaUsuarioDTO.deAlumno(usuario.getAlumno());
			usuarioDTO.setAlumnoDTO(alumnoUsuarioDTO);

			return usuarioDTO;
		} else {
			return null;
		}
	}

}