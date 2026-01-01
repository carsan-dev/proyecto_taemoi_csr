package com.taemoi.project.services.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.taemoi.project.dtos.request.RegistroSolicitudRequest;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.RegistroPendiente;
import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.RegistroPendienteRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.EmailService;
import com.taemoi.project.services.RegistroService;
import com.taemoi.project.utils.EmailUtils;

@Service
public class RegistroServiceImpl implements RegistroService {
	private static final int TOKEN_BYTES = 32;

	private final RegistroPendienteRepository registroPendienteRepository;
	private final AlumnoRepository alumnoRepository;
	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;
	private final EmailService emailService;
	private final SecureRandom secureRandom = new SecureRandom();

	@Value("${app.frontend.base-url:https://moiskimdo.es}")
	private String frontendBaseUrl;

	@Value("${app.auth.registro-token-hours:1}")
	private long tokenHours;

	public RegistroServiceImpl(RegistroPendienteRepository registroPendienteRepository,
			AlumnoRepository alumnoRepository,
			UsuarioRepository usuarioRepository,
			PasswordEncoder passwordEncoder,
			EmailService emailService) {
		this.registroPendienteRepository = registroPendienteRepository;
		this.alumnoRepository = alumnoRepository;
		this.usuarioRepository = usuarioRepository;
		this.passwordEncoder = passwordEncoder;
		this.emailService = emailService;
	}

	@Override
	public void solicitarRegistro(RegistroSolicitudRequest request) {
		String normalizedEmail = EmailUtils.normalizeEmail(request.getEmail());
		if (normalizedEmail == null || normalizedEmail.isBlank()) {
			throw new IllegalArgumentException("Email no valido.");
		}

		Optional<Usuario> usuarioExistente = usuarioRepository.findByEmailIgnoreCase(normalizedEmail);
		if (usuarioExistente.isPresent()) {
			Usuario usuario = usuarioExistente.get();
			if (usuario.getAuthProvider() == AuthProvider.GOOGLE) {
				throw new IllegalArgumentException("Este email usa Google. Inicia sesion con Google.");
			}
			throw new IllegalArgumentException("Ya existe una cuenta con ese email.");
		}

		List<Alumno> alumnos = alumnoRepository.findAllByEmailIgnoreCase(normalizedEmail);
		if (alumnos.isEmpty()) {
			throw new IllegalArgumentException("No se encontraron alumnos con ese email.");
		}

		LocalDate fechaNacimiento = request.getFechaNacimiento();
		Alumno alumno = alumnos.stream()
				.filter(a -> fechaNacimiento.equals(toLocalDate(a.getFechaNacimiento())))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("La fecha de nacimiento no coincide."));

		String token = generarToken();
		String tokenHash = hashToken(token);

		RegistroPendiente registro = registroPendienteRepository.findByEmail(normalizedEmail)
				.orElseGet(RegistroPendiente::new);
		registro.setEmail(normalizedEmail);
		registro.setAlumnoId(alumno.getId());
		registro.setPasswordHash(passwordEncoder.encode(request.getContrasena()));
		registro.setTokenHash(tokenHash);
		registro.setTokenExpiresAt(LocalDateTime.now().plusHours(tokenHours));
		registroPendienteRepository.save(registro);

		String urlConfirmacion = frontendBaseUrl + "/registro-confirmar?token=" + token;
		String contenido = buildRegistroEmailHtml(alumno.getNombre(), urlConfirmacion, tokenHours);
		emailService.sendEmail(normalizedEmail, "Confirmar registro", contenido);
	}

	@Override
	public void confirmarRegistro(String token) {
		if (token == null || token.isBlank()) {
			throw new IllegalArgumentException("Token invalido o caducado.");
		}

		String tokenHash = hashToken(token);
		RegistroPendiente registro = registroPendienteRepository.findByTokenHash(tokenHash)
				.orElseThrow(() -> new IllegalArgumentException("Token invalido o caducado."));

		if (registro.getTokenExpiresAt().isBefore(LocalDateTime.now())) {
			registroPendienteRepository.delete(registro);
			throw new IllegalArgumentException("Token invalido o caducado.");
		}

		Optional<Usuario> usuarioExistente = usuarioRepository.findByEmailIgnoreCase(registro.getEmail());
		if (usuarioExistente.isPresent()) {
			registroPendienteRepository.delete(registro);
			throw new IllegalArgumentException("Ya existe una cuenta con ese email.");
		}

		Alumno alumno = alumnoRepository.findById(registro.getAlumnoId())
				.orElseThrow(() -> new IllegalArgumentException("Alumno no encontrado."));

		if (alumno.getUsuario() != null) {
			registroPendienteRepository.delete(registro);
			throw new IllegalArgumentException("El alumno ya tiene usuario asociado.");
		}

		Usuario usuario = new Usuario();
		usuario.setNombre(alumno.getNombre());
		usuario.setApellidos(alumno.getApellidos());
		usuario.setEmail(registro.getEmail());
		usuario.setContrasena(registro.getPasswordHash());
		usuario.setAuthProvider(AuthProvider.LOCAL);
		usuario.getRoles().add(Roles.ROLE_USER);
		usuario.setAlumno(alumno);
		usuarioRepository.save(usuario);

		alumno.setUsuario(usuario);
		alumnoRepository.save(alumno);

		registroPendienteRepository.delete(registro);
	}

	private LocalDate toLocalDate(java.util.Date fecha) {
		if (fecha == null) {
			return null;
		}
		if (fecha instanceof java.sql.Date date) {
			return date.toLocalDate();
		}
		return fecha.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
	}

	private String generarToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String hashToken(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder();
			for (byte b : hash) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("No se pudo generar el hash del token", e);
		}
	}

	private String buildRegistroEmailHtml(String nombre, String urlConfirmacion, long horas) {
		String safeNombre = nombre == null || nombre.isBlank() ? "hola" : nombre;
		return """
			<div style="font-family: Arial, sans-serif; background-color: #f6f6f6; padding: 24px;">
			  <div style="max-width: 520px; margin: 0 auto; background: #ffffff; border-radius: 8px; padding: 24px;">
			    <h2 style="margin-top: 0; color: #222222;">Confirmar registro</h2>
			    <p>Hola %s,</p>
			    <p>Has solicitado crear una cuenta. Pulsa el boton para confirmar:</p>
			    <p style="text-align: center; margin: 24px 0;">
			      <a href="%s" style="background: #1d4ed8; color: #ffffff; text-decoration: none; padding: 12px 20px; border-radius: 6px; display: inline-block;">
			        Confirmar registro
			      </a>
			    </p>
			    <p>Este enlace caduca en %d hora(s).</p>
			    <p>Si no has solicitado este registro, ignora este mensaje.</p>
			  </div>
			</div>
			""".formatted(safeNombre, urlConfirmacion, horas);
	}
}
