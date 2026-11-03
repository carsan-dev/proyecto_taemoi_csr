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
			// Mismo criterio que el login con contraseña: alguna escuela en la que pueda entrar; y el mismo motivo
			// propio para quien solo tiene roles sin acceso (hoy, ninguno: todos abren sesión desde la pieza 9-5).
			if (membresiaService.elegirOrganizacionParaLogin(usuario).isEmpty()) {
				throw new IllegalArgumentException(
						membresiaService.organizacionesConMembresiaActiva(usuario.getId()).isEmpty()
								? "El usuario no tiene acceso a ninguna escuela. Contacte con el administrador."
								: membresiaService.soloTieneRolesSinAcceso(usuario.getId())
										? AuthenticationServiceImpl.ROL_SIN_ACCESO
										: "No hay alumnos activos asociados a este email. Contacte con el administrador.");
			}
			if (usuario.getAuthProvider() != AuthProvider.GOOGLE) {
				// Pieza E8-4 (decisión 223): una invitación pendiente que entra con Google (email verificado) queda
				// aceptada: pasa a GOOGLE y su enlace deja de servir. Una cuenta con contraseña conserva su token.
				if (usuario.invitacionPendiente()) {
					usuario.setResetTokenHash(null);
					usuario.setResetTokenExpiresAt(null);
				}
				usuario.setAuthProvider(AuthProvider.GOOGLE);
				usuarioRepository.save(usuario);
			}
			return usuario;
		}

		// Buscar los alumnos con este email que aún no tienen cuenta (decisión 428: sin ninguna fila en
		// usuario_alumno; la cuenta nueva aún no existe, así que cualquier fila es de otra). Un alumno ya vinculado lo
		// está a otra cuenta (con otro email, porque no hay ninguna con este): nunca se devuelve esa cuenta.
		// Un email en blanco nunca vincula alumnos (aunque haya alumnos activos con el email vacío)
		List<Alumno> alumnos = EmailUtils.puedeVincularAlumnos(email)
				? alumnoRepository.findAllByEmailIgnoreCaseAndActivoTrue(email)
				: List.of();
		List<Alumno> alumnosSinCuenta = alumnos.stream()
				.filter(this::sinCuenta)
				.toList();
		if (alumnosSinCuenta.isEmpty()) {
			// No hay ningún alumno libre con este email, no se puede crear usuario
			throw sinAlumnoConEmail(email);
		}
		// Los inactivos libres del email, decididos ANTES de crear la cuenta (con el mismo criterio).
		List<Alumno> inactivosSinCuenta = alumnoRepository.findAllByEmailIgnoreCase(email).stream()
				.filter(alumno -> !Boolean.TRUE.equals(alumno.getActivo()))
				.filter(this::sinCuenta)
				.toList();

		// El primer alumno libre da el nombre (si Google no lo trae) y la escuela de la cuenta nueva.
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
		for (Alumno alumno : inactivosSinCuenta) {
			vinculoFamiliaService.vincularAlta(savedUser, alumno);
		}

		return savedUser;
	}

	/** Decisión 428: la ficha no tiene ninguna cuenta vinculada en {@code usuario_alumno}. */
	private boolean sinCuenta(Alumno alumno) {
		return vinculoFamiliaService.cuentasDeLaFicha(alumno.getId(), alumno.getOrganizacionId()).isEmpty();
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

