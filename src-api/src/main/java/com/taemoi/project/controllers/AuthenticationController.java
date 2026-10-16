package com.taemoi.project.controllers;

import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.time.Duration;
import java.net.URI;
import java.util.Locale;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.request.CambioOrganizacionRequest;
import com.taemoi.project.dtos.request.LoginRequest;
import com.taemoi.project.dtos.request.PasswordResetEmailRequest;
import com.taemoi.project.dtos.request.PasswordResetUpdateRequest;
import com.taemoi.project.dtos.request.RecordatorioRachaEmailRequest;
import com.taemoi.project.dtos.request.RegistroConfirmacionRequest;
import com.taemoi.project.dtos.request.RegistroRequest;
import com.taemoi.project.dtos.request.RegistroSolicitudRequest;
import com.taemoi.project.dtos.response.AlumnoParaUsuarioDTO;
import com.taemoi.project.dtos.response.JwtAuthenticationResponse;
import com.taemoi.project.dtos.response.OrganizacionUsuarioDTO;
import com.taemoi.project.dtos.response.RecordatorioRachaEmailResponse;
import com.taemoi.project.dtos.response.UsuarioConAlumnoAsociadoDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.SesionVerificada;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.AccesoFamiliaService;
import com.taemoi.project.services.AuthenticationService;
import com.taemoi.project.services.MembresiaService;
import com.taemoi.project.services.PasswordResetService;
import com.taemoi.project.services.RegistroService;
import com.taemoi.project.services.UsuarioService;
import com.taemoi.project.services.impl.AuthenticationServiceImpl;
import com.taemoi.project.utils.EmailUtils;

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

	@Value("${app.base.url:}")
	private String appBaseUrl;

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
	private AccesoFamiliaService accesoFamilia;

	@Autowired
	private RegistroService registroService;

	@Autowired
	private MembresiaService membresiaService;

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
	public ResponseEntity<?> signin(@RequestBody LoginRequest request,
			HttpServletResponse response) {
		JwtAuthenticationResponse jwtResponse;
		try {
			jwtResponse = authenticationService.signin(request);
		} catch (LockedException e) {
			return ResponseEntity.status(HttpStatus.LOCKED)
					.body(Map.of("mensaje", "Cuenta bloqueada temporalmente."));
		} catch (DisabledException e) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("mensaje", mensajeDeRechazo(e)));
		} catch (BadCredentialsException e) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(Map.of("mensaje", "Credenciales invalidas."));
		}

		// Determinar si usar Secure basado en el perfil activo
		boolean isProduction = "production".equals(activeProfile) || "docker".equals(activeProfile);
		boolean rememberMe = Boolean.TRUE.equals(request.getRememberMe());

		Optional<String> domainOpt = resolverDominioCookie();
		// Limpiar posibles cookies heredadas (host-only y domain) antes de fijar la canónica
		response.addHeader(HttpHeaders.SET_COOKIE,
				construirCookieJwt("", isProduction, false, true, null).toString());
		if (domainOpt.isPresent()) {
			response.addHeader(HttpHeaders.SET_COOKIE,
					construirCookieJwt("", isProduction, false, true, domainOpt.get()).toString());
		}

		String cookieDomain = domainOpt.orElse(null);
		response.addHeader(HttpHeaders.SET_COOKIE,
				construirCookieJwt(jwtResponse.getToken(), isProduction, rememberMe, false, cookieDomain).toString());

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

		Optional<String> domainOpt = resolverDominioCookie();
		response.addHeader(HttpHeaders.SET_COOKIE,
				construirCookieJwt("", isProduction, false, true, null).toString());
		if (domainOpt.isPresent()) {
			response.addHeader(HttpHeaders.SET_COOKIE,
					construirCookieJwt("", isProduction, false, true, domainOpt.get()).toString());
		}
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

		// Roles de la sesión: los de las membresías del usuario en la escuela activa (mismo formato de siempre).
		return ResponseEntity.ok(autoridadesDeSesion(authentication).stream().map(Enum::name)
				.collect(Collectors.toSet()));
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
			usuarioDTO.setAlumnoDTO(obtenerAlumnoVisibleParaUsuario(usuario, autoridadesDeSesion(authentication)));

			return usuarioDTO;
		} else {
			return null;
		}
	}

	@GetMapping("/user/recordatorios-racha-email")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN') || hasRole('ROLE_USER')")
	public ResponseEntity<RecordatorioRachaEmailResponse> obtenerRecordatorioRachaEmail() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		String email = authentication.getName();
		Usuario usuario = usuarioService.encontrarPorEmail(email)
				.orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con el email: " + email));

		boolean habilitado = Boolean.TRUE.equals(usuario.getRecordatorioRachaEmailHabilitado());
		return ResponseEntity.ok(new RecordatorioRachaEmailResponse(habilitado));
	}

	@PutMapping("/user/recordatorios-racha-email")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN') || hasRole('ROLE_USER')")
	public ResponseEntity<RecordatorioRachaEmailResponse> actualizarRecordatorioRachaEmail(
			@Valid @RequestBody RecordatorioRachaEmailRequest request) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		String email = authentication.getName();
		Usuario usuario = usuarioService.encontrarPorEmail(email)
				.orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con el email: " + email));

		boolean habilitado = Boolean.TRUE.equals(request.getHabilitado());
		usuario.setRecordatorioRachaEmailHabilitado(habilitado);
		if (!habilitado) {
			usuario.setUltimoRecordatorioRachaReset(null);
		}
		usuarioService.actualizarUsuario(usuario);
		return ResponseEntity.ok(new RecordatorioRachaEmailResponse(habilitado));
	}

	/**
	 * Obtiene todos los alumnos que el usuario autenticado ve como familia en la escuela de la sesión
	 * (p. ej. hermanos). Regla única de {@link AccesoFamiliaService}, para cualquier rol.
	 *
	 * @return Lista de DTOs con información de todos los alumnos.
	 */
	@GetMapping("/user/alumnos")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN') || hasRole('ROLE_USER')")
	public ResponseEntity<List<AlumnoParaUsuarioDTO>> obtenerTodosLosAlumnosDelUsuario() {
		List<Alumno> alumnos = accesoFamilia.alumnosAccesiblesEnOrganizacionActiva(usuarioAutenticado());

		// Convertir a DTOs
		List<AlumnoParaUsuarioDTO> alumnosDTO = alumnos.stream()
				.map(AlumnoParaUsuarioDTO::deAlumno)
				.collect(java.util.stream.Collectors.toList());

		return ResponseEntity.ok(alumnosDTO);
	}

	private AlumnoParaUsuarioDTO obtenerAlumnoVisibleParaUsuario(Usuario usuario, Set<Roles> autoridades) {
		if (usuario == null) {
			return null;
		}

		Long alumnoAsociadoId = usuario.getAlumnoId();
		if (!autoridades.contains(Roles.ROLE_USER)) {
			// El alumno asociado puede ser de otra escuela: solo se muestra si es de la activa.
			Alumno alumnoAsociado = alumnoAsociadoId == null ? null
					: alumnoRepository.buscarPorIdEnOrganizacionActiva(alumnoAsociadoId).orElse(null);
			if (alumnoAsociado != null && Boolean.TRUE.equals(alumnoAsociado.getActivo())) {
				return AlumnoParaUsuarioDTO.deAlumno(alumnoAsociado);
			}
			return null;
		}

		List<Alumno> alumnosActivos = accesoFamilia.alumnosAccesiblesEnOrganizacionActiva(usuario);
		if (alumnosActivos.isEmpty()) {
			return null;
		}

		if (alumnoAsociadoId != null) {
			for (Alumno alumnoActivo : alumnosActivos) {
				if (alumnoAsociadoId.equals(alumnoActivo.getId())) {
					return AlumnoParaUsuarioDTO.deAlumno(alumnoActivo);
				}
			}
		}

		return AlumnoParaUsuarioDTO.deAlumno(alumnosActivos.get(0));
	}

	/**
	 * Escuelas del usuario autenticado, con sus roles en cada una y cuál es la de la sesión.
	 */
	@GetMapping("/organizaciones")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<List<OrganizacionUsuarioDTO>> obtenerOrganizaciones() {
		Usuario usuario = usuarioAutenticado();
		return ResponseEntity.ok(membresiaService.organizacionesDe(usuario.getId(), TenantContext.get()));
	}

	/**
	 * Cambia la escuela de la sesión: valida la membresía, emite un token nuevo (misma caducidad que
	 * el actual) en la cookie {@code jwt} y recuerda la escuela para el próximo inicio de sesión.
	 * 403 si el usuario no puede entrar en esa escuela.
	 */
	@PostMapping("/organizacion")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<?> cambiarOrganizacion(@RequestBody CambioOrganizacionRequest request,
			HttpServletRequest httpRequest, HttpServletResponse response) {
		if (!(httpRequest.getAttribute(SesionVerificada.ATRIBUTO) instanceof SesionVerificada sesion)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("mensaje", "No autenticado."));
		}
		Usuario usuario = usuarioAutenticado();
		Long organizacionId = request == null ? null : request.getOrganizacionId();
		Optional<JwtAuthenticationResponse> nuevo = authenticationService.cambiarOrganizacion(usuario,
				organizacionId, sesion.token().expiraEn());
		if (nuevo.isEmpty()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("mensaje", "Acceso denegado."));
		}

		// Misma cookie que el login: persistente si la sesión actual lo era (recordarme).
		Date emitido = sesion.token().emitidoEn();
		Date expira = sesion.token().expiraEn();
		boolean persistente = emitido != null
				&& expira.getTime() - emitido.getTime() > AuthenticationServiceImpl.DURACION_SESION_MILLIS;
		Duration maxAge = persistente
				? Duration.ofMillis(Math.max(0, expira.getTime() - System.currentTimeMillis()))
				: null;
		escribirCookieSesion(response, nuevo.get().getToken(), maxAge);
		return ResponseEntity.ok(nuevo.get());
	}

	private Usuario usuarioAutenticado() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof Usuario usuario) {
			return usuario;
		}
		String email = authentication == null ? null : authentication.getName();
		return usuarioService.encontrarPorEmail(email)
				.orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con el email: " + email));
	}

	/** Solo los motivos conocidos llegan tal cual; cualquier otro (p. ej. cuenta deshabilitada), el de siempre. */
	private static String mensajeDeRechazo(DisabledException e) {
		String mensaje = e.getMessage();
		if (AuthenticationServiceImpl.SIN_ESCUELA.equals(mensaje)
				|| AuthenticationServiceImpl.ROL_SIN_ACCESO.equals(mensaje)) {
			return mensaje;
		}
		return AuthenticationServiceImpl.SIN_ALUMNOS_ACTIVOS;
	}

	private static Set<Roles> autoridadesDeSesion(Authentication authentication) {
		Set<Roles> roles = EnumSet.noneOf(Roles.class);
		if (authentication == null || authentication.getAuthorities() == null) {
			return roles;
		}
		for (GrantedAuthority authority : authentication.getAuthorities()) {
			for (Roles rol : Roles.values()) {
				if (rol.name().equals(authority.getAuthority())) {
					roles.add(rol);
				}
			}
		}
		return roles;
	}

	private void escribirCookieSesion(HttpServletResponse response, String token, Duration maxAge) {
		boolean isProduction = "production".equals(activeProfile) || "docker".equals(activeProfile);
		Optional<String> domainOpt = resolverDominioCookie();
		response.addHeader(HttpHeaders.SET_COOKIE,
				construirCookieJwt("", isProduction, false, true, null).toString());
		if (domainOpt.isPresent()) {
			response.addHeader(HttpHeaders.SET_COOKIE,
					construirCookieJwt("", isProduction, false, true, domainOpt.get()).toString());
		}
		ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from("jwt", token)
				.httpOnly(true)
				.secure(isProduction)
				.path("/")
				.sameSite("Lax");
		if (maxAge != null) {
			builder.maxAge(maxAge);
		}
		domainOpt.ifPresent(builder::domain);
		response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
	}

	private Optional<String> resolverDominioCookie() {
		if (appBaseUrl == null || appBaseUrl.isBlank()) {
			return Optional.empty();
		}

		try {
			String host = URI.create(appBaseUrl.trim()).getHost();
			if (host == null || host.isBlank()) {
				return Optional.empty();
			}
			host = host.toLowerCase(Locale.ROOT);
			if ("localhost".equals(host) || host.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+$")) {
				return Optional.empty();
			}
			if (host.startsWith("www.")) {
				host = host.substring(4);
			}
			return host.isBlank() ? Optional.empty() : Optional.of(host);
		} catch (Exception e) {
			return Optional.empty();
		}
	}

	private ResponseCookie construirCookieJwt(
			String valor,
			boolean secure,
			boolean rememberMe,
			boolean eliminar,
			String domain) {
		ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from("jwt", valor)
				.httpOnly(true)
				.secure(secure)
				.path("/")
				.sameSite("Lax");

		if (eliminar) {
			builder.maxAge(Duration.ZERO);
		} else if (rememberMe) {
			builder.maxAge(Duration.ofDays(30));
		}

		if (domain != null && !domain.isBlank()) {
			builder.domain(domain);
		}
		return builder.build();
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
