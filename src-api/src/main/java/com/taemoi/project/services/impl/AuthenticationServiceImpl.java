package com.taemoi.project.services.impl;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.taemoi.project.dtos.request.LoginRequest;
import com.taemoi.project.dtos.request.RegistroRequest;
import com.taemoi.project.dtos.response.JwtAuthenticationResponse;
import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.AuthenticationService;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.LoginAttemptService;
import com.taemoi.project.utils.EmailUtils;

/**
 * Implementación del servicio de autenticación que proporciona funcionalidades
 * para registro (signup) e inicio de sesión (signin) de usuarios.
 */
@Service
public class AuthenticationServiceImpl implements AuthenticationService {
	private final UsuarioRepository usuarioRepository;
	private final LoginAttemptService loginAttemptService;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final AuthenticationManager authenticationManager;

	public AuthenticationServiceImpl(UsuarioRepository usuarioRepository,
									 LoginAttemptService loginAttemptService,
									 PasswordEncoder passwordEncoder,
									 JwtService jwtService,
									 AuthenticationManager authenticationManager) {
		this.usuarioRepository = usuarioRepository;
		this.loginAttemptService = loginAttemptService;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.authenticationManager = authenticationManager;
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
		user.getRoles().add(Roles.ROLE_USER);
		usuarioRepository.save(user);
		String jwt = jwtService.generateToken(user);
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
			loginAttemptService.loginSucceeded(email);

			Usuario user = (Usuario) authentication.getPrincipal();
			long expirationMillis = rememberMe ? 1000L * 60 * 60 * 24 * 30 : 1000L * 60 * 60 * 10;
			String jwt = jwtService.generateToken(user, expirationMillis);
			return new JwtAuthenticationResponse(jwt);
		} catch (BadCredentialsException e) {
			loginAttemptService.loginFailed(email);
			throw new BadCredentialsException("Credenciales inválidas.");
		}
	}
}
