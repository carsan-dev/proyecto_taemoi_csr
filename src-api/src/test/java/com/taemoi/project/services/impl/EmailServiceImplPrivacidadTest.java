package com.taemoi.project.services.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/**
 * Fallo SMTP en el envío con imagen, con la implementación real (el camino de las invitaciones y de su cancelación): el
 * registro no lleva el destinatario, ni el token del enlace, ni el mensaje de la excepción, que puede traer ambos.
 */
class EmailServiceImplPrivacidadTest {

	private static final String DESTINO = "persona.invitada@example.test";
	private static final String TOKEN = "tokenSecretoDeInvitacion123";
	private static final String ENLACE = "https://escuela.example.test/crear-contrasena?token=" + TOKEN;
	private static final byte[] LOGO = "logo".getBytes(StandardCharsets.UTF_8);

	private final JavaMailSender remitente = mock(JavaMailSender.class);
	private final Logger logger = (Logger) LoggerFactory.getLogger(EmailServiceImpl.class);
	private final ListAppender<ILoggingEvent> registro = new ListAppender<>();
	private EmailServiceImpl servicio;

	@BeforeEach
	void preparar() {
		servicio = new EmailServiceImpl();
		ReflectionTestUtils.setField(servicio, "javaMailSender", remitente);
		ReflectionTestUtils.setField(servicio, "fromAddress", "correo@example.test");
		ReflectionTestUtils.setField(servicio, "fromName", "Escuela");
		when(remitente.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
		registro.start();
		logger.addAppender(registro);
	}

	@AfterEach
	void limpiar() {
		logger.detachAppender(registro);
		registro.stop();
	}

	@Test
	void falloConImagen_noRegistraEmailTokenNiMensaje() {
		doThrow(new MailSendException("550 rechazado " + DESTINO + " en " + ENLACE)).when(remitente)
				.send(any(MimeMessage.class));

		servicio.sendEmailConImagen(DESTINO, "Tu acceso", "<a href=\"" + ENLACE + "\">Crear</a>", "logo", LOGO,
				"image/png", "Escuela");

		verify(remitente).send(any(MimeMessage.class));
		assertEquals(1, registro.list.size(), registro.list.toString());
		ILoggingEvent evento = registro.list.get(0);
		String mensaje = evento.getFormattedMessage();
		assertEquals(Level.ERROR, evento.getLevel());
		assertTrue(mensaje.contains("MailSendException"), mensaje);
		assertFalse(mensaje.contains(DESTINO), mensaje);
		assertFalse(mensaje.contains(TOKEN), mensaje);
		assertFalse(mensaje.contains("550 rechazado"), mensaje);
		assertNull(evento.getThrowableProxy(), "sin la excepción adjunta: su mensaje trae los datos");
	}
}
