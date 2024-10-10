package com.taemoi.project.servicios.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.taemoi.project.servicios.EmailService;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EmailServiceImpl implements EmailService {

    @Autowired
    private JavaMailSender javaMailSender;

    private static final Logger logger = LoggerFactory.getLogger(EmailServiceImpl.class);

    @Async
    @Override
    public void sendEmail(@NonNull String to, @NonNull String subject, @NonNull String htmlContent) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();

        try {
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            try {
                helper.setFrom("noreplymoiskimdo@gmail.com", "Club Moiskimdo Taekwondo");
            } catch (UnsupportedEncodingException e) {
                logger.error("Error al establecer el emisor del correo: {}", e.getMessage());
            }
            helper.setReplyTo("noreplymoiskimdo@gmail.com");

            javaMailSender.send(mimeMessage);
        } catch (MessagingException e) {
            logger.error("Error al enviar el correo electrónico a {}: {}", to, e.getMessage());
        }
    }
}
