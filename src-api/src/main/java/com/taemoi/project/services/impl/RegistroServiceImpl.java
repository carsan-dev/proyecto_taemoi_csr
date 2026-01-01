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
		String saludo = (nombre == null || nombre.isBlank()) ? "Hola," : "Hola " + nombre + ",";
		return """
			<!doctype html>
			<html lang="es">
			<head>
			  <meta charset="UTF-8">
			  <meta name="viewport" content="width=device-width, initial-scale=1.0">
			</head>
			<body style="margin:0;padding:0;background-color:#f5f7fa;">
			  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background-color:#f5f7fa;padding:24px 12px;">
			    <tr>
			      <td align="center">
			        <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:560px;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 10px 30px rgba(0,0,0,0.08);">
			          <tr>
			            <td style="background:#1b2b2e;color:#ffffff;padding:24px 28px;">
			              <div style="font-size:12px;letter-spacing:1.2px;text-transform:uppercase;color:#b7c4c8;">Club Moiskimdo Taekwondo</div>
			              <h1 style="margin:10px 0 0;font-size:22px;font-weight:700;">Confirma tu registro</h1>
			            </td>
			          </tr>
			          <tr>
			            <td style="padding:28px;color:#1f2933;font-family:Arial,sans-serif;font-size:15px;line-height:1.6;">
			              <p style="margin:0 0 16px;">%s</p>
			              <p style="margin:0 0 18px;">Has solicitado crear una cuenta. Pulsa el botón para confirmar:</p>
			              <div style="text-align:center;margin:24px 0;">
			                <a href="%s" style="background:#0d47a1;color:#ffffff;text-decoration:none;padding:12px 26px;border-radius:999px;display:inline-block;font-weight:600;">
			                  Confirmar registro
			                </a>
			              </div>
			              <p style="margin:0 0 12px;">Este enlace caduca en %d hora(s).</p>
			              <p style="margin:18px 0 6px;font-size:13px;color:#6b7280;">Si el botón no funciona, copia y pega este enlace:</p>
			              <p style="margin:0 0 18px;word-break:break-all;">
			                <a href="%s" style="color:#0d47a1;text-decoration:none;">%s</a>
			              </p>
			              <hr style="border:none;border-top:1px solid #e5e7eb;margin:24px 0;">
			              <p style="margin:0;font-size:12px;color:#9aa2a9;">
			                Si no has solicitado este registro, ignora este mensaje.
			              </p>
			            </td>
			          </tr>
			        </table>
			        <div style="font-size:11px;color:#9aa2a9;margin-top:16px;">
			          © Club Moiskimdo Taekwondo
			        </div>
			      </td>
			    </tr>
			  </table>
			</body>
			</html>
			""".formatted(saludo, urlConfirmacion, horas, urlConfirmacion, urlConfirmacion);
	}
}
