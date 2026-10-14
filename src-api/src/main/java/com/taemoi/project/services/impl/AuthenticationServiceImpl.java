package com.taemoi.project.services.impl;

import java.util.Date;
import java.util.Optional;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.taemoi.project.dtos.request.LoginRequest;
import com.taemoi.project.dtos.request.RegistroRequest;
import com.taemoi.project.dtos.response.JwtAuthenticationResponse;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.ResolutorOrganizacion;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.AuthenticationService;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.LoginAttemptService;
import com.taemoi.project.services.MembresiaService;
import com.taemoi.project.utils.EmailUtils;

/**
 * Implementación del servicio de autenticación que proporciona funcionalidades
 * para registro (signup) e inicio de sesión (signin) de usuarios.
 */
@Service
public class AuthenticationServiceImpl implements AuthenticationService {
	/** Duración de una sesión normal (10 h) y con «recordarme» (30 días). */
	public static final long DURACION_SESION_MILLIS = 1000L * 60 * 60 * 10;
	public static final long DURACION_RECORDARME_MILLIS = 1000L * 60 * 60 * 24 * 30;

	/** Motivos de rechazo del inicio de sesión (403). */
	public static final String SIN_ESCUELA = "El usuario no tiene acceso a ninguna escuela.";
	public static final String SIN_ALUMNOS_ACTIVOS = "No hay alumnos activos asociados a esta cuenta.";

	private final UsuarioRepository usuarioRepository;
	private final LoginAttemptService loginAttemptService;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final AuthenticationManager authenticationManager;
	private final MembresiaService membresiaService;
	private final ResolutorOrganizacion resolutorOrganizacion;

	public AuthenticationServiceImpl(UsuarioRepository usuarioRepository,
									 LoginAttemptService loginAttemptService,
									 PasswordEncoder passwordEncoder,
									 JwtService jwtService,
									 AuthenticationManager authenticationManager,
									 MembresiaService membresiaService,
									 ResolutorOrganizacion resolutorOrganizacion) {
		this.usuarioRepository = usuarioRepository;
		this.loginAttemptService = loginAttemptService;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.authenticationManager = authenticationManager;
		this.membresiaService = membresiaService;
		this.resolutorOrganizacion = resolutorOrganizacion;
	}

	/**
	 * Registra a un nuevo usuario en el sistema.
	 *
	 * @param request Objeto RegistroRequest con los datos del usuario a registrar.
	 * @return Objeto JwtAuthenticationResponse con el token de autenticación
	 *         generado.
	 * @throws IllegalArgumentException Si el email proporcionado ya está en uso.
	 */
	@Override
	public JwtAuthenticationResponse signup(RegistroRequest request) {
		String normalizedEmail = EmailUtils.normalizeEmail(request.getEmail());
		if (usuarioRepository.existsByEmailIgnoreCase(normalizedEmail)) {
			throw new IllegalArgumentException("Email ya está en uso.");
		}

		Usuario user = new Usuario();
		user.setNombre(request.getNombre());
		user.setApellidos(request.getApellidos());
		user.setEmail(normalizedEmail);
		user.setContrasena(passwordEncoder.encode(request.getContrasena()));
		user.setAuthProvider(AuthProvider.LOCAL);
		// Alta de familia en la escuela de la petición (anónima: la organización por defecto).
		Long organizacionId = resolutorOrganizacion.organizacionActual();
		user.setUltimaOrganizacionId(organizacionId);
		usuarioRepository.save(user);
		membresiaService.asegurarMembresia(user.getId(), organizacionId, RolMembresia.FAMILIA);
		String jwt = jwtService.generateToken(user, DURACION_SESION_MILLIS, organizacionId);
		return new JwtAuthenticationResponse(jwt);
	}

	/**
	 * Inicia sesión para un usuario existente.
	 *
	 * @param request Objeto LoginRequest con las credenciales del usuario.
	 * @return Objeto JwtAuthenticationResponse con el token de autenticación
	 *         generado.
	 * @throws IllegalArgumentException Si el email o la contraseña proporcionados
	 *                                  son inválidos.
	 */
	@Override
	public JwtAuthenticationResponse signin(LoginRequest request) {
		String email = EmailUtils.normalizeEmail(request.getEmail());
		boolean rememberMe = Boolean.TRUE.equals(request.getRememberMe());
		if (loginAttemptService.isBlocked(email)) {
			throw new LockedException("La cuenta está temporalmente bloqueada debido a múltiples intentos fallidos.");
		}

		try {
			Authentication authentication = authenticationManager
					.authenticate(new UsernamePasswordAuthenticationToken(email, request.getContrasena()));

			SecurityContextHolder.getContext().setAuthentication(authentication);

			Usuario user = (Usuario) authentication.getPrincipal();
			// Escuela de la sesión: ver MembresiaService#elegirOrganizacionParaLogin. Sin ninguna
			// válida (sin membresía activa, o familia sin alumnos activos) se rechaza como antes.
			Long organizacionId = membresiaService.elegirOrganizacionParaLogin(user).orElse(null);
			if (organizacionId == null) {
				SecurityContextHolder.clearContext();
				throw new DisabledException(membresiaService.organizacionesConMembresiaActiva(user.getId()).isEmpty()
						? SIN_ESCUELA
						: SIN_ALUMNOS_ACTIVOS);
			}
			loginAttemptService.loginSucceeded(email);
			membresiaService.registrarUltimaOrganizacion(user, organizacionId);

			long expirationMillis = rememberMe ? DURACION_RECORDARME_MILLIS : DURACION_SESION_MILLIS;
			String jwt = jwtService.generateToken(user, expirationMillis, organizacionId);
			return new JwtAuthenticationResponse(jwt);
		} catch (BadCredentialsException e) {
			loginAttemptService.loginFailed(email);
			throw new BadCredentialsException("Credenciales inválidas.");
		}
	}

	@Override
	public Optional<JwtAuthenticationResponse> cambiarOrganizacion(Usuario usuario, Long organizacionId,
			Date expiraEn) {
		if (usuario == null || organizacionId == null || expiraEn == null
				|| !membresiaService.puedeEntrarEn(usuario, organizacionId)) {
			return Optional.empty();
		}
		long restante = expiraEn.getTime() - System.currentTimeMillis();
		if (restante <= 0) {
			return Optional.empty();
		}
		String jwt = jwtService.generateToken(usuario, restante, organizacionId);
		membresiaService.registrarUltimaOrganizacion(usuario, organizacionId);
		return Optional.of(new JwtAuthenticationResponse(jwt));
	}
}
