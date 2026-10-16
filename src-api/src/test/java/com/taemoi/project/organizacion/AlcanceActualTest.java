package com.taemoi.project.organizacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.taemoi.project.entities.Usuario;

/** {@link AlcanceActual}: alcance de la petición en curso, que falla cerrado (entrega5-plan.md §3.4). */
class AlcanceActualTest {

	private final AlcanceActual alcanceActual = new AlcanceActual();

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void sinAutenticacionNinguno() {
		assertSame(AlcanceSesion.NINGUNO, alcanceActual.obtener());
	}

	@Test
	void anonimoNinguno() {
		autenticar(new AnonymousAuthenticationToken("clave", "anonymousUser",
				List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
		assertSame(AlcanceSesion.NINGUNO, alcanceActual.obtener());
	}

	@Test
	void adminOManagerSimuladosTotalConCualquierTipoDeToken() {
		// Así siguen los tests WebMvc con usuarios simulados (UsernamePasswordAuthenticationToken).
		for (String rol : new String[] { "ROLE_ADMIN", "ROLE_MANAGER" }) {
			autenticar(new UsernamePasswordAuthenticationToken("simulado@example.test", null,
					List.of(new SimpleGrantedAuthority(rol))));
			assertSame(AlcanceSesion.TOTAL, alcanceActual.obtener(), rol);
		}
	}

	@Test
	void adminDeAutenticacionEscuelaTotalAunqueSuAlcanceGuardadoSeaOtro() {
		autenticar(new AutenticacionEscuela(new Usuario(), List.of(new SimpleGrantedAuthority("ROLE_MANAGER")),
				AlcanceSesion.NINGUNO));
		assertSame(AlcanceSesion.TOTAL, alcanceActual.obtener());
	}

	@Test
	void autenticacionEscuelaSinGestionDevuelveSuAlcance() {
		AlcanceSesion limitado = AlcanceSesion.limitado(Set.of(3L), Set.of(4L));
		autenticar(new AutenticacionEscuela(new Usuario(),
				List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_PROFESOR")),
				limitado));
		assertEquals(limitado, alcanceActual.obtener());
	}

	@Test
	void otroTipoDeTokenSinGestionNinguno() {
		autenticar(new UsernamePasswordAuthenticationToken("familia@example.test", null,
				List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_RESPONSABLE_SEDE"))));
		assertSame(AlcanceSesion.NINGUNO, alcanceActual.obtener());
	}

	@Test
	void tokenNoAutenticadoConRolDeGestionNinguno() {
		TestingAuthenticationToken token = new TestingAuthenticationToken("x", null, "ROLE_ADMIN");
		token.setAuthenticated(false);
		autenticar(token);
		assertSame(AlcanceSesion.NINGUNO, alcanceActual.obtener());
	}

	private static void autenticar(Authentication autenticacion) {
		SecurityContextHolder.getContext().setAuthentication(autenticacion);
	}
}
