package com.taemoi.project.servicios;

import org.springframework.lang.NonNull;

public interface EmailService {

	void sendEmail(@NonNull String to, @NonNull String subject, @NonNull String htmlContent);
}
