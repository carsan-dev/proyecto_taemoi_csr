package com.taemoi.project.services.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.MembresiaService;
import com.taemoi.project.services.OAuth2UserService;
import com.taemoi.project.services.VinculoFamiliaService;
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
	private final MembresiaService membresiaService;
	private final VinculoFamiliaService vinculoFamiliaService;

	public OAuth2UserServiceImpl(AlumnoRepository alumnoRepository,
								 UsuarioRepository usuarioRepository,
								 PasswordEncoder passwordEncoder,
								 MembresiaService membresiaService,
								 VinculoFamiliaService vinculoFamiliaService) {
		this.alumnoRepository = alumnoRepository;
		this.usuarioRepository = usuarioRepository;
		this.passwordEncoder = passwordEncoder;
		this.membresiaService = membresiaService;
		this.vinculoFamiliaService = vinculoFamiliaService;
	}

	/**
	 * Procesa el login de OAuth2 y crea automáticamente un usuario si existen
	 * alumnos con el email proporcionado por Google.
	 *
	 * El flujo es el siguiente:
	 * 1. Extrae el email del usuario OAuth2
	 * 2. Verifica si ya existe un usuario con ese email
	 * 3. Si no existe, busca todos los alumnos con ese email (puede haber varios)
	 * 4. Si encuentra alumnos sin cuenta, crea automáticamente un usuario y lo vincula al primero de ellos
	 *    (usuario.alumno_id) y escribe usuario_alumno con todos ellos y con los inactivos sin cuenta
	 *    (los alumnos con otra cuenta, no)
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
		// Solo un email que Google haya verificado identifica a la persona.
		if (!emailVerificado(oauth2User.getAttribute("email_verified"))) {
			throw sinAlumnoConEmail(email);
		}

		// Verificar si ya existe un usuario con este email
		Optional<Usuario> existingUser = usuarioRepository.findByEmailIgnoreCase(email);
		if (existingUser.isPresent()) {
			Usuario usuario = existingUser.get();
			// Mismo criterio que el login con contraseña: alguna escuela en la que pueda entrar.
			if (membresiaService.elegirOrganizacionParaLogin(usuario).isEmpty()) {
				throw new IllegalArgumentException(
						membresiaService.organizacionesConMembresiaActiva(usuario.getId()).isEmpty()
								? "El usuario no tiene acceso a ninguna escuela. Contacte con el administrador."
								: "No hay alumnos activos asociados a este email. Contacte con el administrador.");
			}
			if (usuario.getAuthProvider() != AuthProvider.GOOGLE) {
				usuario.setAuthProvider(AuthProvider.GOOGLE);
				usuarioRepository.save(usuario);
			}
			return usuario;
		}

		// Buscar los alumnos con este email que aún no tienen cuenta. Un alumno ya vinculado lo está a
		// otra cuenta (con otro email, porque no hay ninguna con este): nunca se devuelve esa cuenta.
		// Un email en blanco nunca vincula alumnos (aunque haya alumnos activos con el email vacío)
		List<Alumno> alumnos = EmailUtils.puedeVincularAlumnos(email)
				? alumnoRepository.findAllByEmailIgnoreCaseAndActivoTrue(email)
				: List.of();
		List<Alumno> alumnosSinCuenta = alumnos.stream()
				.filter(alumno -> alumno.getUsuario() == null)
				.toList();
		if (alumnosSinCuenta.isEmpty()) {
			// No hay ningún alumno libre con este email, no se puede crear usuario
			throw sinAlumnoConEmail(email);
		}

		// Tomar el primer alumno libre para vincularlo al usuario (usuario.alumno_id es UNIQUE)
		// (Los demás alumnos se accederán a través de usuario_alumno y, en Taemoi, del email compartido)
		Alumno primerAlumno = alumnosSinCuenta.get(0);

		// Crear nuevo usuario automáticamente
		Usuario nuevoUsuario = new Usuario();
		nuevoUsuario.setNombre(nombre != null ? nombre : primerAlumno.getNombre());
		nuevoUsuario.setApellidos(apellidos != null ? apellidos : primerAlumno.getApellidos());
		nuevoUsuario.setEmail(email);
		nuevoUsuario.setAuthProvider(AuthProvider.GOOGLE);

		// Generar una contraseña aleatoria (no se usará para OAuth2, pero es requerida)
		String randomPassword = java.util.UUID.randomUUID().toString();
		nuevoUsuario.setContrasena(passwordEncoder.encode(randomPassword));

		// Vincular con el primer alumno
		nuevoUsuario.setAlumno(primerAlumno);
		nuevoUsuario.setUltimaOrganizacionId(primerAlumno.getOrganizacionId());

		// Guardar el usuario
		Usuario savedUser = usuarioRepository.save(nuevoUsuario);

		// Igual que el registro: familia en la escuela del alumno.
		membresiaService.asegurarMembresia(savedUser.getId(), primerAlumno.getOrganizacionId(), RolMembresia.FAMILIA);

		// Vínculo explícito de familia con TODOS los alumnos libres del email (fase 3, entrega 2b; §5 «Altas
		// nuevas»), también los inactivos: la lectura ya filtra por activo y así se verán al reactivarlos. Los que ya
		// tienen otra cuenta no se vinculan (opción conservadora, pendiente del propietario).
		for (Alumno alumno : alumnosSinCuenta) {
			vinculoFamiliaService.vincularAlta(savedUser, alumno);
		}
		for (Alumno alumno : alumnoRepository.findAllByEmailIgnoreCase(email)) {
			if (!Boolean.TRUE.equals(alumno.getActivo()) && alumno.getUsuario() == null) {
				vinculoFamiliaService.vincularAlta(savedUser, alumno);
			}
		}

		// Actualizar la referencia en el primer alumno
		primerAlumno.setUsuario(savedUser);
		alumnoRepository.save(primerAlumno);

		return savedUser;
	}

	private static boolean emailVerificado(Object emailVerified) {
		return Boolean.TRUE.equals(emailVerified)
				|| (emailVerified instanceof String texto && "true".equalsIgnoreCase(texto.trim()));
	}

	private static IllegalArgumentException sinAlumnoConEmail(String email) {
		return new IllegalArgumentException(
			"No se encontró ningun alumno activo registrado con el email: " + email +
			". Por favor, contacte con el administrador para registrarse."
		);
	}

}

