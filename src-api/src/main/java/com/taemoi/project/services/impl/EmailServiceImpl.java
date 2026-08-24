package com.taemoi.project.services.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.taemoi.project.services.EmailService;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.beans.factory.annotation.Value;
import java.io.UnsupportedEncodingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EmailServiceImpl implements EmailService {

	@Autowired
	private JavaMailSender javaMailSender;
	@Value("${spring.mail.from:${spring.mail.username}}")
	private String fromAddress;
	@Value("${app.mail.from-name:Moi's Kim Do}")
	private String fromName;

	private static final Logger logger = LoggerFactory.getLogger(EmailServiceImpl.class);

	@Async
	@Override
	public void sendEmail(@NonNull String to, @NonNull String subject, @NonNull String htmlContent) {
		try {
			sendEmailSync(to, subject, htmlContent);
		} catch (RuntimeException e) {
			logger.error("Error al enviar el correo electrónico a {}: {}", to, e.getMessage());
		}
	}

	@Override
	public void sendEmailSync(@NonNull String to, @NonNull String subject, @NonNull String htmlContent) {
		MimeMessage mimeMessage = javaMailSender.createMimeMessage();
		try {
			MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
			helper.setTo(to);
			helper.setSubject(subject);
			helper.setText(htmlContent, true);
			helper.setFrom(fromAddress, fromName);
			helper.setReplyTo(fromAddress);
			javaMailSender.send(mimeMessage);
		} catch (MessagingException | UnsupportedEncodingException e) {
			throw new IllegalStateException("No se pudo enviar el correo", e);
		}
	}

	@Override
	public void sendEmailConAdjunto(@NonNull String to, @NonNull String subject, @NonNull String htmlContent,
			@NonNull String nombreAdjunto, @NonNull byte[] adjunto) {
		MimeMessage message=javaMailSender.createMimeMessage();
		try {
			MimeMessageHelper helper=new MimeMessageHelper(message,true,"UTF-8");
			helper.setTo(to); helper.setSubject(subject); helper.setText(htmlContent,true);
			helper.setFrom(fromAddress,fromName);
			helper.setReplyTo(fromAddress);
			helper.addAttachment(nombreAdjunto,new ByteArrayResource(adjunto));
			javaMailSender.send(message);
		} catch(Exception e){ throw new IllegalStateException("No se pudo enviar el correo",e); }
	}
}
