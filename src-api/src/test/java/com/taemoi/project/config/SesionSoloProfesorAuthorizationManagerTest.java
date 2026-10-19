package com.taemoi.project.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import com.taemoi.project.plataforma.AutoridadPlataforma;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Decisión 180: una sesión que solo es profesor solo pasa la capa de URL en su lista blanca (caja del profesor y lo
 * imprescindible de sesión) o donde pasaría un anónimo (lo público). El delegado simula las reglas de siempre:
 * {@code /publico/**} permitAll y el resto {@code anyRequest().authenticated()}, así se ve que la regla cierra lo que
 * el delegado abriría a cualquier sesión.
 */
class SesionSoloProfesorAuthorizationManagerTest {

	/** Peticiones y autenticaciones que llegan al delegado. */
	private final List<String> llamadas = new ArrayList<>();
	private boolean delegadoDeniegaTodo;

	private final AuthorizationManager<HttpServletRequest> delegado = new AuthorizationManager<>() {
		@Override
		@Deprecated
		public AuthorizationDecision check(Supplier<Authentication> autenticacion, HttpServletRequest peticion) {
			Authentication a = autenticacion.get();
			boolean anonimo = a == null || a instanceof AnonymousAuthenticationToken;
			llamadas.add((anonimo ? "anonimo " : "sesion ") + peticion.getMethod() + " " + peticion.getRequestURI());
			if (delegadoDeniegaTodo) {
				return new AuthorizationDecision(false);
			}
			return new AuthorizationDecision(peticion.getRequestURI().startsWith("/publico/") || !anonimo);
		}
	};

	private final AuthorizationManager<HttpServletRequest> regla = SesionSoloProfesorAuthorizationManager
			.envolver(delegado);

	@Test
	void soloProfesorNoPasaFueraDeLaListaBlancaAunqueLaReglaFueraAuthenticated() {
		for (String[] ruta : new String[][] { { "GET", "/api/alumnos" }, { "GET", "/api/grupos" },
				{ "GET", "/api/tesoreria/resumen" }, { "GET", "/api/productos" }, { "GET", "/api/auth/user/alumnos" },
				{ "PUT", "/api/auth/user/recordatorios-racha-email" }, { "GET", "/api/admin/auditoria/eventos" },
				{ "GET", "/api/informes/deudas" }, { "GET", "/api/caja/sedes/1/entregas" }, { "GET", "/api/caja" },
				{ "POST", "/api/auth/roles" }, { "GET", "/api/auth/organizacion" }, { "DELETE", "/api/auth/user" },
				{ "GET", "/api/ruta-nueva-sin-preautorize" },
				{ "GET", "/api/caja/profesorx" }, { "GET", "/imagenes/alumnos/1.jpg" } }) {
			assertFalse(decide(soloProfesor(), ruta[0], ruta[1]), ruta[0] + " " + ruta[1]);
		}
	}

	@Test
	void soloProfesorPasaPorLasReglasDeSiempreEnSuListaBlanca() {
		for (String[] ruta : new String[][] { { "GET", "/api/caja/profesor/cargos-pendientes" },
				{ "HEAD", "/api/caja/profesor/cargos-pendientes" }, { "POST", "/api/caja/profesor/cargos/1/cobrar" },
				{ "POST", "/api/caja/profesor/cobros/1/deshacer" }, { "GET", "/api/caja/profesor/alumnos" },
				{ "GET", "/api/auth/roles" }, { "HEAD", "/api/auth/roles" }, { "GET", "/api/auth/user" },
				{ "GET", "/api/auth/organizaciones" }, { "POST", "/api/auth/organizacion" } }) {
			llamadas.clear();
			assertTrue(decide(soloProfesor(), ruta[0], ruta[1]), ruta[0] + " " + ruta[1]);
			assertEquals(List.of("sesion " + ruta[0] + " " + ruta[1]), llamadas, "decide la regla de siempre");
		}
		// La lista blanca no concede por sí misma: si la regla de siempre deniega, deniega.
		delegadoDeniegaTodo = true;
		assertFalse(decide(soloProfesor(), "GET", "/api/caja/profesor/cargos-pendientes"));
	}

	@Test
	void soloProfesorPasaDondePasariaUnAnonimo() {
		assertTrue(decide(soloProfesor(), "GET", "/publico/eventos"));
		assertTrue(decide(soloProfesor(), "POST", "/publico/logout"));
		assertTrue(llamadas.stream().allMatch(l -> l.startsWith("anonimo ")), llamadas.toString());
	}

	/** Decisión 184: responsable de sede + profesor es la misma sesión limitada; su parte de responsable no abre nada. */
	@Test
	void responsableYProfesorSeTrataIgualQueSoloProfesor() {
		Authentication sesion = sesion("ROLE_RESPONSABLE_SEDE", "ROLE_PROFESOR");
		for (String ruta : List.of("/api/alumnos", "/api/grupos", "/api/tesoreria/resumen", "/api/preinscripciones",
				"/api/convocatorias", "/api/caja/sedes/1/resumen", "/api/tarifas-sede", "/api/informes/asistencia")) {
			assertFalse(decide(sesion, "GET", ruta), ruta);
		}
		assertTrue(decide(sesion, "GET", "/api/caja/profesor/cargos-pendientes"));
		assertTrue(decide(sesion, "GET", "/api/auth/roles"));
		assertTrue(decide(sesion, "GET", "/publico/eventos"));
	}

	@Test
	void administradorDePlataformaQueSoloEsProfesorConservaSusRutas() {
		Authentication sesion = sesion("ROLE_PROFESOR", AutoridadPlataforma.ADMIN_PLATAFORMA);
		assertTrue(decide(sesion, "GET", "/api/plataforma/errores"));
		assertTrue(decide(sesion, "GET", "/api/caja/profesor/productos"));
		assertFalse(decide(sesion, "GET", "/api/alumnos"));
	}

	@Test
	void lasDemasSesionesNoCambian() {
		for (Authentication sesion : List.of(sesion("ROLE_ADMIN"), sesion("ROLE_MANAGER"), sesion("ROLE_USER"),
				sesion("ROLE_PROFESOR", "ROLE_USER"), sesion("ROLE_PROFESOR", "ROLE_MANAGER"),
				sesion("ROLE_PROFESOR", "ROLE_ADMIN"), sesion("ROLE_RESPONSABLE_SEDE"),
				sesion(AutoridadPlataforma.ADMIN_PLATAFORMA), sesion())) {
			llamadas.clear();
			assertTrue(decide(sesion, "GET", "/api/alumnos"), sesion.getAuthorities().toString());
			assertEquals(List.of("sesion GET /api/alumnos"), llamadas, "solo decide la regla de siempre");
		}
		Authentication anonimo = new AnonymousAuthenticationToken("k", "anonymousUser",
				AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
		assertFalse(decide(anonimo, "GET", "/api/alumnos"));
		assertTrue(decide(anonimo, "GET", "/publico/eventos"));
		assertFalse(decide(null, "GET", "/api/alumnos"));
	}

	@Test
	void sesionSoloProfesorNoAutenticadaNoSeTrataComoSesion() {
		UsernamePasswordAuthenticationToken sinAutenticar = UsernamePasswordAuthenticationToken.unauthenticated("x",
				null);
		llamadas.clear();
		decide(sinAutenticar, "GET", "/api/alumnos");
		assertEquals(1, llamadas.size(), "lo decide la regla de siempre");
	}

	@Test
	void otrosObjetosPasanSinTocar() {
		AuthorizationManager<Object> sinPeticion = (a, o) -> new AuthorizationDecision(true);
		AuthorizationManager<Object> envuelto = SesionSoloProfesorAuthorizationManager.envolver(sinPeticion);
		assertTrue(envuelto.authorize(this::soloProfesor, "no es una petición").isGranted());
	}

	@Test
	void elPostProcesadorEnvuelveUnaSolaVez() {
		Object envuelto = SesionSoloProfesorAuthorizationManager.postProcesador().postProcess(delegado);
		assertTrue(envuelto instanceof SesionSoloProfesorAuthorizationManager);
		assertSame(envuelto, SesionSoloProfesorAuthorizationManager.postProcesador().postProcess(envuelto));
		Object otro = new Object();
		assertSame(otro, SesionSoloProfesorAuthorizationManager.postProcesador().postProcess(otro));
	}

	private boolean decide(Authentication autenticacion, String metodo, String ruta) {
		MockHttpServletRequest peticion = new MockHttpServletRequest(metodo, ruta);
		AuthorizationResult resultado = regla.authorize(() -> autenticacion, peticion);
		return resultado != null && resultado.isGranted();
	}

	private Authentication soloProfesor() {
		return sesion("ROLE_PROFESOR");
	}

	private static Authentication sesion(String... autoridades) {
		return UsernamePasswordAuthenticationToken.authenticated("profesor@example.test", null,
				AuthorityUtils.createAuthorityList(autoridades));
	}
}
