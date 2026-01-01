package com.taemoi.project.controllers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
import com.taemoi.project.dtos.request.PasswordResetEmailRequest;
import com.taemoi.project.dtos.request.PasswordResetUpdateRequest;
import com.taemoi.project.dtos.request.RegistroConfirmacionRequest;
import com.taemoi.project.dtos.request.RegistroRequest;
import com.taemoi.project.dtos.request.RegistroSolicitudRequest;
import com.taemoi.project.dtos.response.AlumnoParaUsuarioDTO;
import com.taemoi.project.dtos.response.JwtAuthenticationResponse;
import com.taemoi.project.dtos.response.UsuarioConAlumnoAsociadoDTO;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.AuthenticationService;
import com.taemoi.project.services.PasswordResetService;
import com.taemoi.project.services.RegistroService;
import com.taemoi.project.services.UsuarioService;
import com.taemoi.project.utils.EmailUtils;

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

	@Value("${spring.profiles.active:default}")
	private String activeProfile;

	/**
	 * Inyección del servicio de autenticación.
	 */
	@Autowired
	private AuthenticationService authenticationService;

	@Autowired
	private UsuarioService usuarioService;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private com.taemoi.project.repositories.AlumnoRepository alumnoRepository;

	@Autowired
	private PasswordResetService passwordResetService;

	@Autowired
	private RegistroService registroService;

	/**
	 * Registra un nuevo usuario en el sistema.
	 *
	 * @param request Datos de registro del nuevo usuario.
	 * @return ResponseEntity que contiene la respuesta de autenticación con el
	 *         token JWT.
	 */
	@PostMapping("/signup")
	public ResponseEntity<JwtAuthenticationResponse> signup(@Valid @RequestBody RegistroRequest request) {
		String normalizedEmail = EmailUtils.normalizeEmail(request.getEmail());
		if (usuarioRepository.existsByEmailIgnoreCase(normalizedEmail)) {
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
	public ResponseEntity<JwtAuthenticationResponse> signin(@RequestBody LoginRequest request,
			HttpServletResponse response) {
		JwtAuthenticationResponse jwtResponse = authenticationService.signin(request);

		// Determinar si usar Secure basado en el perfil activo
		boolean isProduction = "production".equals(activeProfile) || "docker".equals(activeProfile);
		boolean rememberMe = Boolean.TRUE.equals(request.getRememberMe());

		// Crear la cookie HTTP-Only
		Cookie jwtCookie = new Cookie("jwt", jwtResponse.getToken());
		jwtCookie.setHttpOnly(true); // No accesible desde JavaScript
		jwtCookie.setSecure(isProduction); // Solo HTTPS en producción
		jwtCookie.setPath("/"); // Disponible para todo el dominio
		if (rememberMe) {
			jwtCookie.setMaxAge(60 * 60 * 24 * 30); // 30 dias de validez
		} else {
			jwtCookie.setMaxAge(-1); // Sesion hasta cerrar el navegador
		}
		if (isProduction) {
			jwtCookie.setAttribute("SameSite", "Strict"); // Protección CSRF
		}

		// Agregar la cookie a la respuesta HTTP
		response.addCookie(jwtCookie);

		// Retornar la respuesta con el cuerpo
		return ResponseEntity.ok(jwtResponse);
	}

	@PostMapping("/password/forgot")
	public ResponseEntity<Void> solicitarResetContrasena(
			@Valid @RequestBody PasswordResetEmailRequest request) {
		passwordResetService.solicitarResetContrasena(request.getEmail());
		return ResponseEntity.ok().build();
	}

	@PostMapping("/password/reset")
	public ResponseEntity<Void> resetearContrasena(
			@Valid @RequestBody PasswordResetUpdateRequest request) {
		passwordResetService.resetearContrasena(request.getToken(), request.getNuevaContrasena());
		return ResponseEntity.ok().build();
	}

	@PostMapping("/register/request")
	public ResponseEntity<?> solicitarRegistro(@Valid @RequestBody RegistroSolicitudRequest request) {
		try {
			registroService.solicitarRegistro(request);
			return ResponseEntity.ok(Map.of("mensaje", "Si el email existe, enviaremos un enlace de verificacion."));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensaje", e.getMessage()));
		}
	}

	@PostMapping("/register/confirm")
	public ResponseEntity<?> confirmarRegistro(@Valid @RequestBody RegistroConfirmacionRequest request) {
		try {
			registroService.confirmarRegistro(request.getToken());
			return ResponseEntity.ok(Map.of("mensaje", "Registro confirmado."));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensaje", e.getMessage()));
		}
	}

	@PostMapping("/logout")
	public ResponseEntity<?> logout(HttpServletResponse response) {
		// Determinar si usar Secure basado en el perfil activo
		boolean isProduction = "production".equals(activeProfile) || "docker".equals(activeProfile);

		Cookie jwtCookie = new Cookie("jwt", null);
		jwtCookie.setHttpOnly(true);
		jwtCookie.setSecure(isProduction); // Solo HTTPS en producción
		jwtCookie.setPath("/");
		jwtCookie.setMaxAge(0); // Eliminar la cookie
		if (isProduction) {
			jwtCookie.setAttribute("SameSite", "Strict");
		}

		response.addCookie(jwtCookie);
		return ResponseEntity.ok().build();
	}

	@GetMapping("/auth-status")
	public ResponseEntity<Boolean> checkAuthStatus(HttpServletRequest request) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		boolean isAuthenticated = authentication != null && authentication.isAuthenticated()
				&& !(authentication instanceof AnonymousAuthenticationToken);

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
		if (authentication == null || !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {
			Map<String, String> response = new HashMap<>();
			response.put("error", "El usuario no está autenticado o es anónimo.");
			return ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED).body(response);
		}

		Object principal = authentication.getPrincipal();

		if (principal instanceof Usuario usuario) {
			Set<String> roles = usuario.getRoles().stream().map(Enum::name).collect(Collectors.toSet());
			return ResponseEntity.ok(roles);
		} else if (principal instanceof UserDetails details) {
			String email = details.getUsername();
			Usuario usuario = usuarioService.encontrarPorEmail(email)
					.orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con el email: " + email));
			Set<String> roles = usuario.getRoles().stream().map(Enum::name).collect(Collectors.toSet());
			return ResponseEntity.ok(roles);
		} else {
			throw new ClassCastException(
					"El principal no es un Usuario ni un UserDetails. Tipo: " + principal.getClass().getName());
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

	/**
	 * Obtiene todos los alumnos asociados al email del usuario autenticado.
	 * Útil cuando múltiples alumnos (ej. familia) comparten el mismo email.
	 *
	 * @return Lista de DTOs con información de todos los alumnos.
	 */
	@GetMapping("/user/alumnos")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN') || hasRole('ROLE_USER')")
	public ResponseEntity<List<AlumnoParaUsuarioDTO>> obtenerTodosLosAlumnosDelUsuario() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		String email = authentication.getName();

		// Buscar todos los alumnos con el email del usuario
		List<com.taemoi.project.entities.Alumno> alumnos = alumnoRepository.findAllByEmailIgnoreCase(EmailUtils.normalizeEmail(email));

		// Convertir a DTOs
		List<AlumnoParaUsuarioDTO> alumnosDTO = alumnos.stream()
				.map(AlumnoParaUsuarioDTO::deAlumno)
				.collect(java.util.stream.Collectors.toList());

		return ResponseEntity.ok(alumnosDTO);
	}

	/**
	 * Endpoint para obtener la URL de inicio de sesión con Google OAuth2.
	 *
	 * @return Mapa con la URL de redirección para iniciar sesión con Google.
	 */
	@GetMapping("/oauth2/google-login-url")
	public ResponseEntity<Map<String, String>> getGoogleLoginUrl() {
		Map<String, String> response = new HashMap<>();
		response.put("url", "/oauth2/authorization/google");
		return ResponseEntity.ok(response);
	}

}
