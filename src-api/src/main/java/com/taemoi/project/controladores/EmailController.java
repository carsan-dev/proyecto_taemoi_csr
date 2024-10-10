package com.taemoi.project.controladores;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.request.EmailRequest;
import com.taemoi.project.servicios.EmailService;

@RestController
@RequestMapping("/api/mail")
public class EmailController {

    @Autowired
    private EmailService emailService;

    @PostMapping("/enviar")
    public void sendEmail(@RequestBody EmailRequest emailRequest) {
        String htmlMsg = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<style>" +
                "body {font-family: Arial, sans-serif; background-color: #f9f9f9; margin: 0; padding: 20px;}" +
                ".container {max-width: 600px; margin: auto; background-color: #ffffff; border-radius: 10px; box-shadow: 0 0 10px rgba(0,0,0,0.1); padding: 20px;}" +
                ".header {font-size: 24px; font-weight: bold; color: #333; margin-bottom: 20px; text-align: center;}" +
                ".content {margin-bottom: 15px;}" +
                ".content label {font-weight: bold; color: #555; display: block; margin-bottom: 5px;}" +
                ".footer {margin-top: 30px; font-size: 14px; color: #777; text-align: center;}" +
                ".logo {text-align: center; margin-bottom: 20px;}" +
                ".logo img {max-width: 150px;}" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='container'>" +
                "<div class='logo'><img src='https://promociones.globalum.es/wp-content/uploads/2020/06/Mois-Kim-Do-Escuela-de-Taekwondo-Umbrete.jpg' alt='Logo'></div>" +
                "<div class='header'>Nuevo mensaje de " + emailRequest.getNombre() + " " + emailRequest.getApellidos() + "</div>" +
                "<div class='content'><label>Nombre:</label> <div>" + emailRequest.getNombre() + "</div></div>" +
                "<div class='content'><label>Apellidos:</label> <div>" + emailRequest.getApellidos() + "</div></div>" +
                "<div class='content'><label>Correo electrónico:</label> <div>" + emailRequest.getEmail() + "</div></div>" +
                "<div class='content'><label>Asunto:</label> <div>" + emailRequest.getAsunto() + "</div></div>" +
                "<div class='content'><label>Mensaje:</label> <div>" + emailRequest.getMensaje() + "</div></div>" +
                "<div class='footer'>Atentamente,<br>La aplicación de mensajería del Club Moiskimdo Taekwondo</div>" +
                "</div>" +
                "</body>" +
                "</html>";

        emailService.sendEmail(
                "crolyx16@gmail.com",
                "Nuevo mensaje de " + emailRequest.getNombre() + " " + emailRequest.getApellidos(),
                htmlMsg
        );
    }
}
