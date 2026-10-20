package com.taemoi.project.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.taemoi.project.events.InvitacionEnviableEvent;

/**
 * Pieza E8-4 (D8-13): el correo de invitación sale solo tras confirmar la transacción que creó o renovó el token, como
 * los de preinscripción: una invitación deshecha no avisa a nadie y un fallo al preparar o entregar el correo nunca
 * deshace la cuenta ni responde 500 (se puede reenviar). El log del fallo lleva solo el id de la cuenta y el tipo de
 * error: nunca el token, el enlace ni el mensaje del error.
 *
 * <p>Enlace: {@code app.frontend.base-url} (la misma base que el correo de restablecer contraseña; pendiente conocido
 * del dominio único) + {@code /crear-contrasena?token=…}, la página «Crea tu contraseña» (E8-6).
 */
@Component
public class InvitacionCorreoListener {

	private static final Logger log = LoggerFactory.getLogger(InvitacionCorreoListener.class);

	static final String RUTA_PAGINA = "/crear-contrasena?token=";

	private final EmailService emailService;
	private final MarcaEscuelaService marcaEscuela;

	@Value("${app.frontend.base-url:https://moiskimdo.es}")
	private String frontendBaseUrl;

	public InvitacionCorreoListener(EmailService emailService, MarcaEscuelaService marcaEscuela) {
		this.emailService = emailService;
		this.marcaEscuela = marcaEscuela;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void alConfirmar(InvitacionEnviableEvent evento) {
		try {
			MarcaEscuela marca = marcaEscuela.de(evento.organizacionId());
			InvitacionCorreo.Correo correo = InvitacionCorreo.de(marca, evento.nombre(), evento.rol(),
					frontendBaseUrl + RUTA_PAGINA + evento.token());
			emailService.sendEmailConImagen(evento.email(), correo.asunto(), correo.html(), InvitacionCorreo.CID_LOGO,
					correo.logo(), correo.tipoLogo(), correo.remitente());
		} catch (RuntimeException e) {
			log.warn("No se pudo enviar la invitación de la cuenta #{} ({}); se puede reenviar.", evento.usuarioId(),
					e.getClass().getSimpleName());
		}
	}
}
