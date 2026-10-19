package com.taemoi.project.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.sql.SQLIntegrityConstraintViolationException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

/**
 * Pieza 6c-2: la red de la clave única al crear una solicitud nunca acaba en 500. Clave única (por su nombre, por el
 * error 1062 de MySQL o por el tipo de Spring) → 409 «ya hay una pendiente»; cualquier otra violación → 409 «el
 * recurso ha cambiado».
 */
class SolicitudServiceErroresTest {

	@Test
	void claveDePendientePorSuNombre_yaPendiente() {
		assertTraduce(SolicitudService.YA_PENDIENTE, new DataIntegrityViolationException(
				"Duplicate entry '7' for key 'solicitud.uk_solicitud_cobro_pendiente'"));
	}

	@Test
	void claveUnicaSinNombreReconocible_porElCodigo1062_yaPendiente() {
		assertTraduce(SolicitudService.YA_PENDIENTE, new DataIntegrityViolationException("could not execute statement",
				new SQLIntegrityConstraintViolationException("Duplicate entry '7' for key 'otra'", "23000", 1062)));
		assertTraduce(SolicitudService.YA_PENDIENTE, new DuplicateKeyException("clave duplicada"));
	}

	@Test
	void otraViolacion_recursoCambiado_no500() {
		assertTraduce(SolicitudService.RECURSO_CAMBIADO, new DataIntegrityViolationException("could not execute statement",
				new SQLIntegrityConstraintViolationException(
						"Cannot add or update a child row: a foreign key constraint fails (fk_solicitud_producto_alumno)",
						"23000", 1452)));
		assertTraduce(SolicitudService.RECURSO_CAMBIADO, new DataIntegrityViolationException("sin causa"));
	}

	private static void assertTraduce(String mensaje, DataIntegrityViolationException error) {
		IllegalStateException traducida = SolicitudService.traducirViolacionAlCrear(error);
		assertEquals(mensaje, traducida.getMessage());
		assertSame(error, traducida.getCause(), "conserva la causa para el registro");
	}
}
