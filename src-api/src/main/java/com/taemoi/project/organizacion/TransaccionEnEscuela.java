package com.taemoi.project.organizacion;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManagerFactory;

/**
 * Pieza E7-7a: ejecuta una acción en una transacción NUEVA con una escuela EXPLÍCITA fijada, con su propia sesión de
 * Hibernate (que resuelve la escuela al abrirse, {@link ResolutorOrganizacion}). Para lo que no es de la escuela del hilo:
 * la escritura del superadmin en una escuela (su logo) y la marca de una escuela leída desde otro hilo o escuela.
 *
 * <p>{@code PROPAGATION_REQUIRES_NEW} suspende una transacción en curso, pero NO el {@code EntityManager} de
 * open-in-view de la petición (sin transacción, Spring lo reutilizaría, con la escuela de la petición): ese se desliga
 * mientras dura la acción y se vuelve a ligar al terminar.
 */
@Component
public class TransaccionEnEscuela {

	private final EntityManagerFactory emf;
	private final TransactionTemplate escritura;
	private final TransactionTemplate lectura;

	public TransaccionEnEscuela(EntityManagerFactory emf, PlatformTransactionManager transacciones) {
		this.emf = emf;
		this.escritura = new TransactionTemplate(transacciones);
		this.escritura.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.lectura = new TransactionTemplate(transacciones);
		this.lectura.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.lectura.setReadOnly(true);
	}

	public <T> T escribir(Long organizacionId, Supplier<T> accion) {
		return ejecutar(organizacionId, escritura, accion);
	}

	public <T> T leer(Long organizacionId, Supplier<T> accion) {
		return ejecutar(organizacionId, lectura, accion);
	}

	private <T> T ejecutar(Long organizacionId, TransactionTemplate transaccion, Supplier<T> accion) {
		return TenantContext.calcularCon(organizacionId, () -> {
			Object deLaPeticion = null;
			if (!TransactionSynchronizationManager.isActualTransactionActive()
					&& TransactionSynchronizationManager.hasResource(emf)) {
				deLaPeticion = TransactionSynchronizationManager.unbindResource(emf);
			}
			try {
				return transaccion.execute(estado -> accion.get());
			} finally {
				if (deLaPeticion != null) {
					TransactionSynchronizationManager.bindResource(emf, deLaPeticion);
				}
			}
		});
	}
}
