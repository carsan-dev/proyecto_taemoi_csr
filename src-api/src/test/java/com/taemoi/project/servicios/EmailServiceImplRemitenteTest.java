package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import com.taemoi.project.services.EmailService;
import com.taemoi.project.services.impl.EmailServiceImpl;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/**
 * Pieza E7-7a (decisión 211): nombre visible del remitente. Otra escuela, el suyo; Moiskimdo (sin remitente propio), el
 * de siempre ({@code app.mail.from-name}). La dirección no cambia.
 */
class EmailServiceImplRemitenteTest {

	private final JavaMailSender mailSender = mock(JavaMailSender.class);
	private final EmailServiceImpl servicio = new EmailServiceImpl();

	@BeforeEach
	void preparar() {
		ReflectionTestUtils.setField(servicio, "javaMailSender", mailSender);
		ReflectionTestUtils.setField(servicio, "fromAddress", "noreply@example.test");
		ReflectionTestUtils.setField(servicio, "fromName", "Moi's Kim Do");
		when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
	}

	@Test
	void otraEscuelaConSuNombreYMoiskimdoConElDeSiempre() throws Exception {
		EmailService.enviarSync(servicio, "familia@example.test", "Asunto", "<p>x</p>", "Escuela Ficticia Norte");
		EmailService.enviarSync(servicio, "familia@example.test", "Asunto", "<p>x</p>", null);
		EmailService.enviarConAdjunto(servicio, "familia@example.test", "Asunto", "<p>x</p>", "a.pdf", new byte[] { 1 },
				"Escuela Ficticia Norte");
		servicio.sendEmailSync("familia@example.test", "Asunto", "<p>x</p>", "  ");

		ArgumentCaptor<MimeMessage> enviados = ArgumentCaptor.forClass(MimeMessage.class);
		verify(mailSender, org.mockito.Mockito.times(4)).send(enviados.capture());
		assertEquals(java.util.List.of("Escuela Ficticia Norte", "Moi's Kim Do", "Escuela Ficticia Norte", "Moi's Kim Do"),
				enviados.getAllValues().stream().map(EmailServiceImplRemitenteTest::nombre).toList());
		for (MimeMessage mensaje : enviados.getAllValues()) {
			assertEquals("noreply@example.test", ((InternetAddress) mensaje.getFrom()[0]).getAddress());
		}
		verify(mailSender, org.mockito.Mockito.never()).send(any(MimeMessage[].class));
	}

	private static String nombre(MimeMessage mensaje) {
		try {
			return ((InternetAddress) mensaje.getFrom()[0]).getPersonal();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
