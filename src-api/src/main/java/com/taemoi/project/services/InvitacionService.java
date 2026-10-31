package com.taemoi.project.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.events.InvitacionCanceladaEvent;
import com.taemoi.project.events.InvitacionEnviableEvent;
import com.taemoi.project.repositories.UsuarioRepository;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * Invitaciones por correo (pieza E8-4; D8-13; decisiones 219 y 223; fase3-e8-plan.md §5).
 *
 * <p>Invitación pendiente = cuenta LOCAL sin contraseña ({@link Usuario#invitacionPendiente()}; V31_9). El token es el
 * mecanismo de restablecer contraseña: las mismas columnas ({@code reset_token_hash}, SHA-256 en hex, y
 * {@code reset_token_expires_at}) y el mismo generador (32 bytes aleatorios en Base64 URL); solo se guarda el hash y
 * caduca a los {@value #DIAS_VALIDEZ} días. Un solo token vivo por cuenta: reenviar lo reemplaza. El token en claro solo
 * viaja en el {@link InvitacionEnviableEvent} hasta el correo (tras el commit): nunca en una respuesta, el registro ni
 * un log.
 *
 * <p>Una cuenta existente nunca pasa por aquí (anti-objetivo: sin toma de cuentas): {@link #crearCuentaPendiente} solo
 * crea cuentas nuevas y {@link #renovarToken} solo escribe si la cuenta sigue pendiente.
 */
@Service
public class InvitacionService {

	public static final int DIAS_VALIDEZ = 7;
	static final String ENLACE_NO_VALE = "El enlace ha caducado o ya se usó. Pide que te lo reenvíen.";
	static final String NOMBRE_Y_APELLIDOS = "Indica el nombre y los apellidos.";
	static final String NOMBRE_LARGO = "El nombre no puede pasar de 50 caracteres.";
	static final String APELLIDOS_LARGOS = "Los apellidos no pueden pasar de 50 caracteres.";
	static final String EMAIL_NO_VALIDO = "El email no es válido.";
	private static final int TOKEN_BYTES = 32;
	/** Las columnas {@code usuario.nombre} y {@code usuario.apellidos} (y la entidad). */
	private static final int MAXIMO_NOMBRE = 50;
	/** La columna {@code usuario.email}. */
	private static final int MAXIMO_EMAIL = 255;

	private final UsuarioRepository usuarios;
	private final PasswordEncoder passwordEncoder;
	private final ApplicationEventPublisher eventos;
	private final Validator validador;
	private final MarcaEscuelaService marcaEscuela;
	private final SecureRandom aleatorio = new SecureRandom();

	public InvitacionService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder,
			ApplicationEventPublisher eventos, Validator validador, MarcaEscuelaService marcaEscuela) {
		this.usuarios = usuarios;
		this.passwordEncoder = passwordEncoder;
		this.eventos = eventos;
		this.validador = validador;
		this.marcaEscuela = marcaEscuela;
	}

	/** Lo que enseña la página «Crea tu contraseña» antes de escribirla: el email, el nombre y la escuela. */
	public record DatosInvitacion(String email, String nombre, String escuela) {
	}

	/** Nombre y apellidos recortados de una invitación. */
	public record Invitado(String nombre, String apellidos) {
	}

	/**
	 * Nombre y apellidos del cuerpo de un alta sin cuenta: ninguno de los dos, vacío (la API responde el 404 de siempre);
	 * uno solo, en blanco o de más de 50 caracteres, 400.
	 *
	 * @throws IllegalArgumentException (400) datos no válidos
	 */
	public static Optional<Invitado> invitadoDe(String nombre, String apellidos) {
		if (nombre == null && apellidos == null) {
			return Optional.empty();
		}
		String n = nombre == null ? "" : nombre.strip();
		String a = apellidos == null ? "" : apellidos.strip();
		if (n.isEmpty() || a.isEmpty()) {
			throw new IllegalArgumentException(NOMBRE_Y_APELLIDOS);
		}
		if (n.codePointCount(0, n.length()) > MAXIMO_NOMBRE) {
			throw new IllegalArgumentException(NOMBRE_LARGO);
		}
		if (a.codePointCount(0, a.length()) > MAXIMO_NOMBRE) {
			throw new IllegalArgumentException(APELLIDOS_LARGOS);
		}
		return Optional.of(new Invitado(n, a));
	}

	/**
	 * Crea la cuenta pendiente (LOCAL, sin contraseña, con el token y la escuela de la invitación como última) y deja el
	 * correo para después del commit. Va dentro de la transacción del alta ({@code MANDATORY}): si el alta falla, no
	 * queda cuenta ni sale correo. Dos altas a la vez del mismo email: la clave única del email hace fallar la segunda
	 * ({@code DataIntegrityViolationException}); quien llama la repite ({@code AltaDeMiembro}).
	 *
	 * @throws IllegalArgumentException (400) email, nombre o apellidos que la entidad no admite
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Usuario crearCuentaPendiente(String email, Invitado invitado, Long organizacionId, RolMembresia rol) {
		Usuario cuenta = new Usuario();
		cuenta.setNombre(invitado.nombre());
		cuenta.setApellidos(invitado.apellidos());
		cuenta.setEmail(email);
		cuenta.setAuthProvider(AuthProvider.LOCAL);
		cuenta.setUltimaOrganizacionId(organizacionId);
		Set<ConstraintViolation<Usuario>> errores = validador.validate(cuenta);
		boolean deEmail = errores.stream().anyMatch(v -> "email".equals(v.getPropertyPath().toString()));
		if (deEmail || cuenta.getEmail() == null || cuenta.getEmail().length() > MAXIMO_EMAIL) {
			throw new IllegalArgumentException(EMAIL_NO_VALIDO);
		}
		if (!errores.isEmpty()) {
			throw new IllegalArgumentException(errores.iterator().next().getMessage());
		}
		String token = generarToken();
		cuenta.setResetTokenHash(hash(token));
		cuenta.setResetTokenExpiresAt(LocalDateTime.now().plusDays(DIAS_VALIDEZ));
		Usuario guardada = usuarios.saveAndFlush(cuenta);
		eventos.publishEvent(new InvitacionEnviableEvent(guardada.getId(), organizacionId, guardada.getEmail(),
				guardada.getNombre(), rol, token));
		return guardada;
	}

	/**
	 * Token nuevo para una cuenta que SIGUE pendiente (el anterior deja de valer), con la escuela que reenvía como la de
	 * la invitación; el correo, tras el commit. {@code false} si ya no está pendiente (nada cambia).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean renovarToken(Usuario cuenta, Long organizacionId, RolMembresia rol) {
		String token = generarToken();
		int filas = usuarios.renovarInvitacion(cuenta.getId(), hash(token), LocalDateTime.now().plusDays(DIAS_VALIDEZ),
				organizacionId);
		if (filas != 1) {
			return false;
		}
		eventos.publishEvent(new InvitacionEnviableEvent(cuenta.getId(), organizacionId, cuenta.getEmail(),
				cuenta.getNombre(), rol, token));
		return true;
	}

	/**
	 * Deja para después del commit el aviso de invitación cancelada (decisión 389), con el email y el nombre copiados: la
	 * cuenta puede ya no existir cuando salga. Si la transacción se deshace, no sale.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void avisarCancelacion(Usuario cuenta, Long organizacionId) {
		eventos.publishEvent(new InvitacionCanceladaEvent(cuenta.getId(), organizacionId, cuenta.getEmail(),
				cuenta.getNombre()));
	}

	/**
	 * Los datos de la página «Crea tu contraseña» para un token vigente: email, nombre y la escuela de la invitación
	 * (con el mismo nombre que el correo). Nada más.
	 *
	 * @throws IllegalArgumentException (400) {@value #ENLACE_NO_VALE}: token vacío, desconocido, caducado, ya usado o de
	 *         una cuenta que no está pendiente
	 */
	@Transactional(readOnly = true)
	public DatosInvitacion datos(String token) {
		Usuario cuenta = vigente(token);
		Long escuela = cuenta.getUltimaOrganizacionId();
		if (escuela == null) {
			throw new IllegalArgumentException(ENLACE_NO_VALE);
		}
		return new DatosInvitacion(cuenta.getEmail(), cuenta.getNombre(),
				InvitacionCorreo.nombreEscuela(marcaEscuela.de(escuela)));
	}

	/** La cuenta pendiente del token, si no ha caducado; si no, 400 {@value #ENLACE_NO_VALE}. */
	private Usuario vigente(String token) {
		if (token == null || token.isBlank()) {
			throw new IllegalArgumentException(ENLACE_NO_VALE);
		}
		LocalDateTime ahora = LocalDateTime.now();
		return usuarios.findByResetTokenHash(hash(token)).filter(Usuario::invitacionPendiente)
				.filter(u -> u.getResetTokenExpiresAt() != null && u.getResetTokenExpiresAt().isAfter(ahora))
				.orElseThrow(() -> new IllegalArgumentException(ENLACE_NO_VALE));
	}

	/**
	 * Acepta la invitación: guarda la contraseña (ya validada por el DTO) y borra el token, una sola vez (un
	 * {@code UPDATE} condicionado al hash, a que siga pendiente y a que no haya caducado: de dos usos a la vez, uno gana).
	 * Devuelve la cuenta ya aceptada.
	 *
	 * @throws IllegalArgumentException (400) {@value #ENLACE_NO_VALE}: token vacío, desconocido, caducado, ya usado o de
	 *         una cuenta que no está pendiente
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Usuario aceptar(String token, String nuevaContrasena) {
		if (token == null || token.isBlank() || nuevaContrasena == null || nuevaContrasena.isBlank()) {
			throw new IllegalArgumentException(ENLACE_NO_VALE);
		}
		String hash = hash(token);
		Usuario cuenta = usuarios.findByResetTokenHash(hash).filter(Usuario::invitacionPendiente)
				.orElseThrow(() -> new IllegalArgumentException(ENLACE_NO_VALE));
		int filas = usuarios.aceptarInvitacion(cuenta.getId(), hash, passwordEncoder.encode(nuevaContrasena),
				LocalDateTime.now());
		if (filas != 1) {
			throw new IllegalArgumentException(ENLACE_NO_VALE);
		}
		return usuarios.findById(cuenta.getId()).orElseThrow(() -> new IllegalArgumentException(ENLACE_NO_VALE));
	}

	private String generarToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		aleatorio.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** SHA-256 en hex, como {@code PasswordResetServiceImpl}: el token se busca por su hash. */
	static String hash(String token) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("No se pudo generar el hash del token", e);
		}
	}
}
