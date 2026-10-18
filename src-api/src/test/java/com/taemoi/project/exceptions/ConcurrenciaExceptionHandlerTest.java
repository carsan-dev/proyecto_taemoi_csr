package com.taemoi.project.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.entities.Cobro;

/**
 * Pieza 6b-2: los conflictos de concurrencia llegan como 409 con un mensaje para repetir, no como 500. Las excepciones
 * son las que se vieron al quitar el FOR UPDATE de la caja (versión de {@link Cobro}) y las de bloqueo de MySQL (espera
 * agotada, interbloqueo), que el controlador de prueba lanza tal cual las traduce Spring.
 */
class ConcurrenciaExceptionHandlerTest {

	@RestController
	static class Lanzador {
		static Supplier<RuntimeException> siguiente;

		@GetMapping("/lanzar")
		String lanzar() {
			throw siguiente.get();
		}
	}

	@Test
	void conflictosDeConcurrencia_409ConMensajeDeReintento() throws Exception {
		MockMvc mvc = MockMvcBuilders.standaloneSetup(new Lanzador())
				.setControllerAdvice(new GlobalExceptionHandler()).build();
		List<Supplier<RuntimeException>> casos = List.of(
				() -> new ObjectOptimisticLockingFailureException(Cobro.class, 2L),
				() -> new CannotAcquireLockException("Lock wait timeout exceeded; try restarting transaction"),
				() -> new DeadlockLoserDataAccessException("Deadlock found when trying to get lock", null),
				() -> new PessimisticLockingFailureException("bloqueo"));
		for (Supplier<RuntimeException> caso : casos) {
			Lanzador.siguiente = caso;
			MvcResult r = mvc.perform(get("/lanzar")).andReturn();
			assertEquals(409, r.getResponse().getStatus(), caso.get().getClass().getSimpleName());
			assertEquals(GlobalExceptionHandler.MENSAJE_CONCURRENCIA, new ObjectMapper()
					.readTree(r.getResponse().getContentAsString()).get("mensaje").asText());
		}
	}
}
