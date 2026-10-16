package com.taemoi.project.seguridad;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * La matriz evalúa @PreAuthorize con argumentos nulos y sin objeto devuelto: una expresión que dependa de ellos
 * (o de un bean) debe hacer fallar el test nombrando el endpoint, nunca dar una decisión silenciosa.
 */
class EvaluadorAutorizacionSalvaguardaTest {

	@Test
	void unaExpresionSoloDeRolesSeAdmite() throws Exception {
		assertDoesNotThrow(() -> comprobar(Controlador.class, "soloRoles", Long.class));
		assertDoesNotThrow(() -> comprobar(Controlador.class, "sinAnotacion", Long.class));
	}

	@Test
	void unaExpresionConArgumentosFallaNombrandoElEndpoint() {
		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> comprobar(Controlador.class, "conArgumento", Long.class));
		assertTrue(error.getMessage().contains("GET /api/prueba/{id}"), error.getMessage());
		assertTrue(error.getMessage().contains("Controlador#conArgumento"), error.getMessage());
		assertTrue(error.getMessage().contains("ampliar"), error.getMessage());
	}

	@Test
	void unaExpresionConBeanFalla() {
		assertThrows(IllegalStateException.class, () -> comprobar(Controlador.class, "conBean", Long.class));
	}

	@Test
	void unPostAuthorizeSobreElObjetoDevueltoFalla() {
		assertThrows(IllegalStateException.class,
				() -> comprobar(Controlador.class, "conObjetoDevuelto", Long.class));
	}

	@Test
	void unaExpresionConBeanEnLaClaseFalla() {
		assertThrows(IllegalStateException.class,
				() -> comprobar(ControladorConBeanEnClase.class, "metodo", Long.class));
	}

	private static void comprobar(Class<?> clase, String nombre, Class<?>... parametros) throws Exception {
		Method metodo = clase.getDeclaredMethod(nombre, parametros);
		EvaluadorAutorizacion.comprobarExpresionesSoportadas("GET /api/prueba/{id}", metodo, clase);
	}

	static class Controlador {
		@PreAuthorize("hasRole('ROLE_MANAGER') || hasAuthority('ROLE_ADMIN')")
		public void soloRoles(Long id) {
		}

		public void sinAnotacion(Long id) {
		}

		@PreAuthorize("#id == 1")
		public void conArgumento(Long id) {
		}

		@PreAuthorize("@acceso.alumno(#id, 'EDITAR')")
		public void conBean(Long id) {
		}

		@PostAuthorize("returnObject != null")
		public Object conObjetoDevuelto(Long id) {
			return null;
		}
	}

	@PreAuthorize("@acceso.puede()")
	static class ControladorConBeanEnClase {
		public void metodo(Long id) {
		}
	}
}
