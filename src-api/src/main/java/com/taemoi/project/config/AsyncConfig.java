package com.taemoi.project.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;

import com.taemoi.project.organizacion.OrganizacionTaskDecorator;

@Configuration
@EnableAsync
public class AsyncConfig {

	/**
	 * Spring Boot aplica el único {@link TaskDecorator} del contexto al executor que usa
	 * {@code @Async} ({@code applicationTaskExecutor}): propaga la organización activa.
	 */
	@Bean
	TaskDecorator organizacionTaskDecorator() {
		return new OrganizacionTaskDecorator();
	}
}
