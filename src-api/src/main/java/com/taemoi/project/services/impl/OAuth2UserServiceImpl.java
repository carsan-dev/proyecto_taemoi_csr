package com.taemoi.project.services.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.OAuth2UserService;
import com.taemoi.project.utils.EmailUtils;

/**
 * Implementación del servicio OAuth2UserService que gestiona la autenticación
 * mediante Google OAuth2 y crea automáticamente usuarios basándose en alumnos existentes.
 */
@Service
public class OAuth2UserServiceImpl implements OAuth2UserService {

	private final AlumnoRepository alumnoRepository;
	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;

	public OAuth2UserServiceImpl(AlumnoRepository alumnoRepository,
								 UsuarioRepository usuarioRepository,
								 PasswordEncoder passwordEncoder) {
		this.alumnoRepository = alumnoRepository;
		this.usuarioRepository = usuarioRepository;
		this.passwordEncoder = passwordEncoder;
	}

	/**
	 * Procesa el login de OAuth2 y crea automáticamente un usuario si existen
	 * alumnos con el email proporcionado por Google.
	 *
	 * El flujo es el siguiente:
	 * 1. Extrae el email del usuario OAuth2
	 * 2. Verifica si ya existe un usuario con ese email
	 * 3. Si no existe, busca todos los alumnos con ese email (puede haber varios)
	 * 4. Si encuentra alumnos, crea automáticamente un usuario y lo vincula al primer alumno
	 *    (los demás alumnos se pueden acceder a través del email compartido)
	 *
	 * @param oauth2User El usuario OAuth2 autenticado (de Google).
	 * @param registrationId El ID del proveedor (ej. "google").
	 * @return El usuario creado o encontrado.
	 */
	@Override
	@Transactional
	public Usuario processOAuth2Login(OAuth2User oauth2User, String registrationId) {
		String email = EmailUtils.normalizeEmail(oauth2User.getAttribute("email"));
		String nombre = oauth2User.getAttribute("given_name");
		String apellidos = oauth2User.getAttribute("family_name");

		if (email == null) {
			throw new IllegalArgumentException("No se pudo obtener el email del proveedor OAuth2");
		}

		// Verificar si ya existe un usuario con este email
		Optional<Usuario> existingUser = usuarioRepository.findByEmailIgnoreCase(email);
		if (existingUser.isPresent()) {
			Usuario usuario = existingUser.get();
			if (usuario.getRoles().contains(Roles.ROLE_USER)
					&& !alumnoRepository.existsByEmailIgnoreCaseAndActivoTrue(email)) {
				throw new IllegalArgumentException(
						"No hay alumnos activos asociados a este email. Contacte con el administrador.");
			}
			if (usuario.getAuthProvider() != AuthProvider.GOOGLE) {
				usuario.setAuthProvider(AuthProvider.GOOGLE);
				usuarioRepository.save(usuario);
			}
			return usuario;
		}

		// Buscar TODOS los alumnos con este email (puede haber múltiples)
		List<Alumno> alumnos = alumnoRepository.findAllByEmailIgnoreCaseAndActivoTrue(email);
		if (alumnos.isEmpty()) {
			// No hay ningún alumno con este email, no se puede crear usuario
			throw new IllegalArgumentException(
				"No se encontró ningun alumno activo registrado con el email: " + email +
				". Por favor, contacte con el administrador para registrarse."
			);
		}

		// Tomar el primer alumno para vincularlo al usuario
		// (Los demás alumnos se accederán a través del email compartido)
		Alumno primerAlumno = alumnos.get(0);

		// Verificar si el primer alumno ya tiene un usuario asociado
		if (primerAlumno.getUsuario() != null) {
			return primerAlumno.getUsuario();
		}

		// Crear nuevo usuario automáticamente
		Usuario nuevoUsuario = new Usuario();
		nuevoUsuario.setNombre(nombre != null ? nombre : primerAlumno.getNombre());
		nuevoUsuario.setApellidos(apellidos != null ? apellidos : primerAlumno.getApellidos());
		nuevoUsuario.setEmail(email);
		nuevoUsuario.setAuthProvider(AuthProvider.GOOGLE);

		// Generar una contraseña aleatoria (no se usará para OAuth2, pero es requerida)
		String randomPassword = java.util.UUID.randomUUID().toString();
		nuevoUsuario.setContrasena(passwordEncoder.encode(randomPassword));

		// Asignar rol de usuario
		nuevoUsuario.getRoles().add(Roles.ROLE_USER);

		// Vincular con el primer alumno
		nuevoUsuario.setAlumno(primerAlumno);

		// Guardar el usuario
		Usuario savedUser = usuarioRepository.save(nuevoUsuario);

		// Actualizar la referencia en el primer alumno
		primerAlumno.setUsuario(savedUser);
		alumnoRepository.save(primerAlumno);

		return savedUser;
	}
}

