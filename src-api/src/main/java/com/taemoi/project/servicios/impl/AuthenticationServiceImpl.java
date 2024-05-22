package com.taemoi.project.servicios.impl;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.taemoi.project.controladores.AdminController;
import com.taemoi.project.dtos.request.LoginRequest;
import com.taemoi.project.dtos.request.RegistroRequest;
import com.taemoi.project.dtos.response.JwtAuthenticationResponse;
import com.taemoi.project.entidades.Roles;
import com.taemoi.project.entidades.Usuario;
import com.taemoi.project.repositorios.UsuarioRepository;
import com.taemoi.project.servicios.AuthenticationService;
import com.taemoi.project.servicios.JwtService;

import lombok.Builder;

/**
 * Implementación del servicio de autenticación que proporciona funcionalidades para
 * registro (signup) e inicio de sesión (signin) de usuarios.
 */
@Builder
@Service
public class AuthenticationServiceImpl implements AuthenticationService {
	private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

	/**
     * Inyección del repositorio de usuario.
     */
	@Autowired
	private UsuarioRepository usuarioRepository;

	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final AuthenticationManager authenticationManager;

    /**
     * Constructor de la clase AuthenticationServiceImpl.
     *
     * @param usuarioRepository     Repositorio de usuarios para acceder a los datos de los usuarios.
     * @param passwordEncoder       Codificador de contraseñas para codificar las contraseñas de los usuarios.
     * @param jwtService            Servicio JWT para generar tokens de autenticación.
     * @param authenticationManager Administrador de autenticación para autenticar a los usuarios.
     */
	public AuthenticationServiceImpl(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
			AuthenticationManager authenticationManager) {
		this.usuarioRepository = usuarioRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.authenticationManager = authenticationManager;
	}

    /**
     * Registra a un nuevo usuario en el sistema.
     *
     * @param request Objeto RegistroRequest con los datos del usuario a registrar.
     * @return Objeto JwtAuthenticationResponse con el token de autenticación generado.
     * @throws IllegalArgumentException Si el email proporcionado ya está en uso.
     */
	@Override
	public JwtAuthenticationResponse signup(RegistroRequest request) {
		if (usuarioRepository.existsByEmail(request.getEmail())) {
			throw new IllegalArgumentException("Email ya está en uso.");
		}

		Usuario user = new Usuario();
		user.setNombre(request.getNombre());
		user.setApellidos(request.getApellidos());
		user.setEmail(request.getEmail());
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
     * @return Objeto JwtAuthenticationResponse con el token de autenticación generado.
     * @throws IllegalArgumentException Si el email o la contraseña proporcionados son inválidos.
     */
	@Override
    public JwtAuthenticationResponse signin(LoginRequest request) {
        logger.info("Iniciando sesión para: {}", request.getEmail());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getContrasena()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        Optional<Usuario> optionalUser = usuarioRepository.findByEmail(request.getEmail());
        Usuario user = optionalUser.orElseThrow(() -> {
            logger.error("Usuario no encontrado: {}", request.getEmail());
            return new IllegalArgumentException("Email o contraseña inválidos.");
        });

        logger.info("Usuario autenticado: {}", user.getEmail());
        String jwt = jwtService.generateToken(user);
        logger.info("Token JWT generado para: {}", user.getEmail());
        return JwtAuthenticationResponse.builder().token(jwt).build();
    }
}
