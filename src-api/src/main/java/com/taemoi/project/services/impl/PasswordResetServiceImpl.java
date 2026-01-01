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
		emailService.sendEmail(usuario.getEmail(), "Restablecer contrasena", htmlContent);
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
		String safeNombre = nombre == null || nombre.isBlank() ? "hola" : nombre;
		return """
			<div style="font-family: Arial, sans-serif; background-color: #f6f6f6; padding: 24px;">
			  <div style="max-width: 520px; margin: 0 auto; background: #ffffff; border-radius: 8px; padding: 24px;">
			    <h2 style="margin-top: 0; color: #222222;">Restablecer contrasena</h2>
			    <p>Hola %s,</p>
			    <p>Hemos recibido una solicitud para cambiar tu contrasena. Haz clic en el boton para continuar:</p>
			    <p style="text-align: center; margin: 24px 0;">
			      <a href="%s" style="background: #1d4ed8; color: #ffffff; text-decoration: none; padding: 12px 20px; border-radius: 6px; display: inline-block;">
			        Cambiar contrasena
			      </a>
			    </p>
			    <p>Este enlace caduca en %d hora(s).</p>
			    <p>Si no has solicitado este cambio, puedes ignorar este mensaje.</p>
			  </div>
			</div>
			""".formatted(safeNombre, resetUrl, tokenHours);
	}
}
