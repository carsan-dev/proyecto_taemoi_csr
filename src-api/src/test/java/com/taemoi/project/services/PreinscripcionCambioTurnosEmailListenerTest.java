package com.taemoi.project.services;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.taemoi.project.events.PreinscripcionTurnosModificadosEvent;

class PreinscripcionCambioTurnosEmailListenerTest {
	@Test
	void noEnviaAntesDelCommitYEnviaDespues() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class)) {
			PreinscripcionCambioTurnosEmailService email = context.getBean(PreinscripcionCambioTurnosEmailService.class);
			ApplicationEventPublisher eventos = context;
			TransactionTemplate transaccion = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));

			transaccion.executeWithoutResult(status -> {
				eventos.publishEvent(new PreinscripcionTurnosModificadosEvent("PRE-1"));
				verify(email, never()).enviar("PRE-1");
			});

			verify(email).enviar("PRE-1");
		}
	}

	@Test
	void noEnviaTrasRollback() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class)) {
			PreinscripcionCambioTurnosEmailService email = context.getBean(PreinscripcionCambioTurnosEmailService.class);
			ApplicationEventPublisher eventos = context;
			TransactionTemplate transaccion = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));

			transaccion.executeWithoutResult(status -> {
				eventos.publishEvent(new PreinscripcionTurnosModificadosEvent("PRE-1"));
				status.setRollbackOnly();
			});

			verify(email, never()).enviar("PRE-1");
		}
	}

	@Configuration
	@EnableTransactionManagement
	static class Config {
		@Bean
		PreinscripcionCambioTurnosEmailService emailService() {
			return mock(PreinscripcionCambioTurnosEmailService.class);
		}

		@Bean
		PreinscripcionCambioTurnosEmailListener listener(PreinscripcionCambioTurnosEmailService emailService) {
			return new PreinscripcionCambioTurnosEmailListener(emailService);
		}

		@Bean
		PlatformTransactionManager transactionManager() {
			return new StubTransactionManager();
		}
	}

	@SuppressWarnings("serial")
	private static class StubTransactionManager extends AbstractPlatformTransactionManager {
		@Override protected Object doGetTransaction() { return new Object(); }
		@Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
		@Override protected void doCommit(DefaultTransactionStatus status) { }
		@Override protected void doRollback(DefaultTransactionStatus status) { }
	}
}
