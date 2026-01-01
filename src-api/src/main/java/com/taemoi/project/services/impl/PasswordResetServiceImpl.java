package com.taemoi.project.services.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.EmailService;
import com.taemoi.project.services.PasswordResetService;
import com.taemoi.project.utils.EmailUtils;

@Service
public class PasswordResetServiceImpl implements PasswordResetService {
	private static final Logger logger = LoggerFactory.getLogger(PasswordResetServiceImpl.class);
	private static final int TOKEN_BYTES = 32;

	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;
	private final EmailService emailService;
	private final SecureRandom secureRandom = new SecureRandom();

	@Value("${app.frontend.base-url:https://moiskimdo.es}")
	private String frontendBaseUrl;

	@Value("${app.auth.reset-token-hours:1}")
	private long resetTokenHours;

	public PasswordResetServiceImpl(UsuarioRepository usuarioRepository,
			PasswordEncoder passwordEncoder,
			EmailService emailService) {
		this.usuarioRepository = usuarioRepository;
		this.passwordEncoder = passwordEncoder;
		this.emailService = emailService;
	}

	@Override
	public void solicitarResetContrasena(String email) {
		String normalizedEmail = EmailUtils.normalizeEmail(email);
		if (normalizedEmail == null || normalizedEmail.isBlank()) {
			return;
		}

		Optional<Usuario> usuarioOptional = usuarioRepository.findByEmailIgnoreCase(normalizedEmail);
		if (usuarioOptional.isEmpty()) {
			return;
		}

		Usuario usuario = usuarioOptional.get();
		if (usuario.getAuthProvider() == AuthProvider.GOOGLE) {
			logger.info("Password reset requested for GOOGLE account: {}", normalizedEmail);
			return;
		}

		String token = generarToken();
		String tokenHash = hashToken(token);

		usuario.setResetTokenHash(tokenHash);
		usuario.setResetTokenExpiresAt(LocalDateTime.now().plusHours(resetTokenHours));
		usuarioRepository.save(usuario);

		String resetUrl = frontendBaseUrl + "/reset-password?token=" + token;
		String htmlContent = buildResetEmailHtml(usuario.getNombre(), resetUrl, resetTokenHours);
		emailService.sendEmail(usuario.getEmail(), "Restablecer contraseña", htmlContent);
	}

	@Override
	public void resetearContrasena(String token, String nuevaContrasena) {
		if (token == null || token.isBlank()) {
			throw new IllegalArgumentException("Token invalido o caducado.");
		}

		String tokenHash = hashToken(token);
		Usuario usuario = usuarioRepository.findByResetTokenHash(tokenHash)
				.orElseThrow(() -> new IllegalArgumentException("Token invalido o caducado."));

		if (usuario.getAuthProvider() == AuthProvider.GOOGLE) {
			throw new IllegalArgumentException("Las cuentas de Google no pueden restablecer contrasena.");
		}

		LocalDateTime expiresAt = usuario.getResetTokenExpiresAt();
		if (expiresAt == null || expiresAt.isBefore(LocalDateTime.now())) {
			usuario.setResetTokenHash(null);
			usuario.setResetTokenExpiresAt(null);
			usuarioRepository.save(usuario);
			throw new IllegalArgumentException("Token invalido o caducado.");
		}

		usuario.setContrasena(passwordEncoder.encode(nuevaContrasena));
		usuario.setResetTokenHash(null);
		usuario.setResetTokenExpiresAt(null);
		usuarioRepository.save(usuario);
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

	private String buildResetEmailHtml(String nombre, String resetUrl, long tokenHours) {
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
			              <h1 style="margin:10px 0 0;font-size:22px;font-weight:700;">Restablecer contraseña</h1>
			            </td>
			          </tr>
			          <tr>
			            <td style="padding:28px;color:#1f2933;font-family:Arial,sans-serif;font-size:15px;line-height:1.6;">
			              <p style="margin:0 0 16px;">%s</p>
			              <p style="margin:0 0 18px;">Hemos recibido una solicitud para cambiar tu contraseña. Haz clic en el botón para continuar:</p>
			              <div style="text-align:center;margin:24px 0;">
			                <a href="%s" style="background:#0d47a1;color:#ffffff;text-decoration:none;padding:12px 26px;border-radius:999px;display:inline-block;font-weight:600;">
			                  Cambiar contraseña
			                </a>
			              </div>
			              <p style="margin:0 0 12px;">Este enlace caduca en %d hora(s).</p>
			              <p style="margin:18px 0 6px;font-size:13px;color:#6b7280;">Si el botón no funciona, copia y pega este enlace:</p>
			              <p style="margin:0 0 18px;word-break:break-all;">
			                <a href="%s" style="color:#0d47a1;text-decoration:none;">%s</a>
			              </p>
			              <hr style="border:none;border-top:1px solid #e5e7eb;margin:24px 0;">
			              <p style="margin:0;font-size:12px;color:#9aa2a9;">
			                Si no has solicitado este cambio, puedes ignorar este mensaje.
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
			""".formatted(saludo, resetUrl, tokenHours, resetUrl, resetUrl);
	}
}
