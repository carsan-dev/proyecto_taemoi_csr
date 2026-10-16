package com.taemoi.project.controllers;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import jakarta.validation.Valid;

import com.taemoi.project.dtos.request.EmailRequest;
import com.taemoi.project.services.EmailService;

@RestController
@RequestMapping("/api/mail")
public class EmailController {

	private static final Pattern SALTOS_DE_LINEA = Pattern.compile("[\\r\\n]+");
	private static final Pattern SALTO_DE_LINEA = Pattern.compile("\\r\\n|\\r|\\n");

	@Autowired
	private EmailService emailService;

	@PostMapping("/enviar")
	public void sendEmail(@Valid @RequestBody EmailRequest emailRequest) {
		// Texto del visitante: los campos de una línea, sin CR/LF (nombre y apellidos van en la cabecera
		// Subject), y todo escapado antes de entrar en el HTML; los saltos del mensaje pasan a <br> tras escapar.
		String nombre = unaLinea(emailRequest.getNombre());
		String apellidos = unaLinea(emailRequest.getApellidos());
		String nombreHtml = html(nombre);
		String apellidosHtml = html(apellidos);
		String emailHtml = html(unaLinea(emailRequest.getEmail()));
		String asuntoHtml = html(unaLinea(emailRequest.getAsunto()));
		String mensajeHtml = conSaltosDeLinea(html(emailRequest.getMensaje()));

		String htmlMsg = "<!DOCTYPE html>" + "<html>" + "<head>" + "<style>"
				+ "body {font-family: Arial, sans-serif; background-color: #f9f9f9; margin: 0; padding: 20px;}"
				+ ".container {max-width: 600px; margin: auto; background-color: #ffffff; border-radius: 10px; box-shadow: 0 0 10px rgba(0,0,0,0.1); padding: 20px;}"
				+ ".header {font-size: 24px; font-weight: bold; color: #333; margin-bottom: 20px; text-align: center;}"
				+ ".content {margin-bottom: 15px;}"
				+ ".content label {font-weight: bold; color: #555; display: block; margin-bottom: 5px;}"
				+ ".footer {margin-top: 30px; font-size: 14px; color: #777; text-align: center;}"
				+ ".logo {text-align: center; margin-bottom: 20px;}" + ".logo img {max-width: 150px;}" + "</style>"
				+ "</head>" + "<body>" + "<div class='container'>"
				+ "<div class='logo'><img src='https://promociones.globalum.es/wp-content/uploads/2020/06/Mois-Kim-Do-Escuela-de-Taekwondo-Umbrete.jpg' alt='Logo'></div>"
				+ "<div class='header'>Nuevo mensaje de " + nombreHtml + " " + apellidosHtml
				+ "</div>" + "<div class='content'><label>Nombre:</label> <div>" + nombreHtml
				+ "</div></div>" + "<div class='content'><label>Apellidos:</label> <div>" + apellidosHtml
				+ "</div></div>" + "<div class='content'><label>Correo electrónico:</label> <div>"
				+ emailHtml + "</div></div>" + "<div class='content'><label>Asunto:</label> <div>"
				+ asuntoHtml + "</div></div>" + "<div class='content'><label>Mensaje:</label> <div>"
				+ mensajeHtml + "</div></div>"
				+ "<div class='footer'>Atentamente,<br>La aplicación de mensajería del Club Moiskimdo Taekwondo</div>"
				+ "</div>" + "</body>" + "</html>";

		emailService.sendEmail("moiskimdotaekwondo@gmail.com", "Nuevo mensaje de " + nombre + " " + apellidos,
				htmlMsg);
	}

	/** Sustituye cada secuencia de CR/LF por un espacio: impide inyectar cabeceras de correo. */
	private static String unaLinea(String texto) {
		return texto == null ? "" : SALTOS_DE_LINEA.matcher(texto).replaceAll(" ");
	}

	/** Escapa {@code & < > " '}; el resto de caracteres (acentos, eñes) se mantiene tal cual. */
	private static String html(String texto) {
		return texto == null ? "" : HtmlUtils.htmlEscape(texto, StandardCharsets.UTF_8.name());
	}

	/** Convierte los saltos de línea de un texto YA escapado en {@code <br>}. */
	private static String conSaltosDeLinea(String textoEscapado) {
		return SALTO_DE_LINEA.matcher(textoEscapado).replaceAll("<br>");
	}
}
