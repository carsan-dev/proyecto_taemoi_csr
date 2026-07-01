package com.taemoi.project.services;

import org.springframework.lang.NonNull;

public interface EmailService {

	void sendEmail(@NonNull String to, @NonNull String subject, @NonNull String htmlContent);

	void sendEmailConAdjunto(@NonNull String to, @NonNull String subject, @NonNull String htmlContent,
			@NonNull String nombreAdjunto, @NonNull byte[] adjunto);
}
