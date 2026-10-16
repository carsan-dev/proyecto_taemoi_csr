package com.taemoi.project.seguridad;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * La matriz evalúa la seguridad de método con una invocación simulada: solo admite las llamadas al bean
 * {@code @acceso} con argumentos {@code #parámetro} o literales (a las que pasa un id de ejemplo no nulo). Cualquier
 * otra expresión que dependa de argumentos, de otro bean o del objeto devuelto debe hacer fallar el test nombrando
 * el endpoint, nunca dar una decisión silenciosa.
 */
class EvaluadorAutorizacionSalvaguardaTest {

	@Test
	void unaExpresionSoloDeRolesSeAdmite() throws Exception {
		assertDoesNotThrow(() -> comprobar(Controlador.class, "soloRoles", Long.class));
		assertDoesNotThrow(() -> comprobar(Controlador.class, "sinAnotacion", Long.class));
	}

	@Test
	void unaLlamadaAAccesoConArgumentosSeAdmite() {
		assertDoesNotThrow(() -> comprobar(Controlador.class, "rutaDeFamilia", Long.class));
		assertDoesNotThrow(() -> comprobar(Controlador.class, "accesoConOperacion", Long.class));
		assertDoesNotThrow(() -> comprobar(Controlador.class, "accesoPorReferencia", String.class));
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
	void unArgumentoSueltoJuntoAAccesoFalla() {
		assertThrows(IllegalStateException.class,
				() -> comprobar(Controlador.class, "accesoYArgumentoSuelto", Long.class));
	}

	@Test
	void unaExpresionConOtroBeanFalla() {
		assertThrows(IllegalStateException.class, () -> comprobar(Controlador.class, "conOtroBean", Long.class));
	}

	@Test
	void unArgumentoDeAccesoQueNoEsParametroNiLiteralFalla() {
		assertThrows(IllegalStateException.class,
				() -> comprobar(Controlador.class, "accesoConBeanDeArgumento", Long.class));
		assertThrows(IllegalStateException.class,
				() -> comprobar(Controlador.class, "accesoConOperacionDeArgumento", Long.class));
		assertThrows(IllegalStateException.class,
				() -> comprobar(Controlador.class, "accesoConLlamadaDeArgumento", Long.class));
	}

	@Test
	void unPostAuthorizeSobreElObjetoDevueltoFalla() {
		assertThrows(IllegalStateException.class,
				() -> comprobar(Controlador.class, "conObjetoDevuelto", Long.class));
	}

	@Test
	void unaExpresionConOtroBeanEnLaClaseFalla() {
		assertThrows(IllegalStateException.class,
				() -> comprobar(ControladorConBeanEnClase.class, "metodo", Long.class));
	}

	@Test
	void losParametrosQueUsaAccesoRecibenUnValorDeEjemploNoNulo() throws Exception {
		assertArrayEquals(new Object[] { 1L, null, null },
				argumentos(Controlador.class, "variosParametros", Long.class, String.class, boolean.class));
		assertArrayEquals(new Object[] { "1" }, argumentos(Controlador.class, "accesoPorReferencia", String.class));
		assertArrayEquals(new Object[] { null }, argumentos(Controlador.class, "soloRoles", Long.class));
	}

	@Test
	void unArgumentoDeAccesoQueNoEsUnParametroDelMetodoFalla() {
		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> argumentos(Controlador.class, "parametroInexistente", Long.class));
		assertTrue(error.getMessage().contains("#alumnoId"), error.getMessage());
	}

	@Test
	void unParametroDeAccesoDeTipoSinValorDeEjemploFalla() {
		assertThrows(IllegalStateException.class,
				() -> argumentos(Controlador.class, "tipoSinEjemplo", Object.class));
	}

	private static void comprobar(Class<?> clase, String nombre, Class<?>... parametros) throws Exception {
		Method metodo = clase.getDeclaredMethod(nombre, parametros);
		EvaluadorAutorizacion.comprobarExpresionesSoportadas("GET /api/prueba/{id}", metodo, clase);
	}

	private static Object[] argumentos(Class<?> clase, String nombre, Class<?>... parametros) throws Exception {
		Method metodo = clase.getDeclaredMethod(nombre, parametros);
		return EvaluadorAutorizacion.argumentosDeEjemplo(metodo, clase);
	}

	static class Controlador {
		@PreAuthorize("hasRole('ROLE_MANAGER') || hasAuthority('ROLE_ADMIN')")
		public void soloRoles(Long id) {
		}

		public void sinAnotacion(Long id) {
		}

		@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_USER') and @acceso.alumnoComoFamilia(#id)")
		public void rutaDeFamilia(Long id) {
		}

		@PreAuthorize("hasAuthority('ROLE_RESPONSABLE_SEDE') and @acceso.alumno(#id, 'EDITAR')")
		public void accesoConOperacion(Long id) {
		}

		@PreAuthorize("@acceso.preinscripcion(#referencia,'LEER')")
		public void accesoPorReferencia(String referencia) {
		}

		@PreAuthorize("@acceso.alumnoComoFamilia(#alumnoId)")
		public void variosParametros(Long alumnoId, String deporte, boolean descargar) {
		}

		@PreAuthorize("#id == 1")
		public void conArgumento(Long id) {
		}

		@PreAuthorize("@acceso.alumnoComoFamilia(#id) and #id > 0")
		public void accesoYArgumentoSuelto(Long id) {
		}

		@PreAuthorize("@otroBean.alumno(#id, 'EDITAR')")
		public void conOtroBean(Long id) {
		}

		@PreAuthorize("@acceso.alumno(@otroBean.id, 'LEER')")
		public void accesoConBeanDeArgumento(Long id) {
		}

		@PreAuthorize("@acceso.alumno(#id, #operacion)")
		public void accesoConOperacionDeArgumento(Long id) {
		}

		@PreAuthorize("@acceso.alumno(#id.longValue(), 'LEER')")
		public void accesoConLlamadaDeArgumento(Long id) {
		}

		@PreAuthorize("@acceso.alumnoComoFamilia(#alumnoId)")
		public void parametroInexistente(Long id) {
		}

		@PreAuthorize("@acceso.alumnoComoFamilia(#objeto)")
		public void tipoSinEjemplo(Object objeto) {
		}

		@PostAuthorize("returnObject != null")
		public Object conObjetoDevuelto(Long id) {
			return null;
		}
	}

	@PreAuthorize("@otroBean.puede()")
	static class ControladorConBeanEnClase {
		public void metodo(Long id) {
		}
	}
}
