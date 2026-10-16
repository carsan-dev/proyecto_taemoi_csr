package com.taemoi.project.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.AlcanceActual;
import com.taemoi.project.organizacion.AlcanceSesion;
import com.taemoi.project.organizacion.AutenticacionEscuela;
import com.taemoi.project.organizacion.OrganizacionRequestFilter;
import com.taemoi.project.organizacion.ResolutorOrganizacion;
import com.taemoi.project.organizacion.SesionVerificada;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.plataforma.AutoridadPlataforma;

/**
 * {@link JwtAuthenticationFilter} con el token {@link AutenticacionEscuela} (entrega 5b-2): mismo principal
 * {@link Usuario} y mismas autoridades que antes, más el alcance de la sesión; y la cadena completa con
 * {@link OrganizacionRequestFilter}, que verifica la sesión una vez y la deja en el atributo de la petición.
 */
class JwtAuthenticationFilterTest {

	private final JwtAuthenticationFilter filtro = new JwtAuthenticationFilter();

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		TenantContext.clear();
	}

	@Test
	void creaAutenticacionEscuelaConElMismoPrincipalYLasMismasAutoridades() throws Exception {
		Usuario usuario = new Usuario();
		AlcanceSesion alcance = AlcanceSesion.limitado(Set.of(2L), Set.of(3L));
		SesionVerificada sesion = new SesionVerificada(usuario, 4L, Set.of(Roles.ROLE_USER, Roles.ROLE_PROFESOR),
				null, true, alcance);
		MockHttpServletRequest peticion = new MockHttpServletRequest();
		peticion.setAttribute(SesionVerificada.ATRIBUTO, sesion);
		AtomicReference<Authentication> vista = new AtomicReference<>();

		TenantContext.ejecutarCon(4L, () -> {
			try {
				filtro.doFilter(peticion, new MockHttpServletResponse(),
						(req, res) -> vista.set(SecurityContextHolder.getContext().getAuthentication()));
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		});

		AutenticacionEscuela token = assertInstanceOf(AutenticacionEscuela.class, vista.get());
		assertInstanceOf(UsernamePasswordAuthenticationToken.class, token, "los instanceof de hoy siguen valiendo");
		assertSame(usuario, token.getPrincipal());
		assertNull(token.getCredentials());
		assertNull(token.getDetails());
		assertTrue(token.isAuthenticated());
		assertEquals(Set.of("ROLE_USER", "ROLE_PROFESOR", AutoridadPlataforma.ADMIN_PLATAFORMA), nombres(token));
		assertEquals(alcance, token.alcance());
	}

	@Test
	void sesionDeOtraOrganizacionNoSeAutentica() throws Exception {
		MockHttpServletRequest peticion = new MockHttpServletRequest();
		peticion.setAttribute(SesionVerificada.ATRIBUTO,
				new SesionVerificada(new Usuario(), 4L, Set.of(Roles.ROLE_ADMIN), null));
		AtomicReference<Authentication> vista = new AtomicReference<>();

		TenantContext.ejecutarCon(5L, () -> {
			try {
				filtro.doFilter(peticion, new MockHttpServletResponse(),
						(req, res) -> vista.set(SecurityContextHolder.getContext().getAuthentication()));
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		});

		assertNull(vista.get());
	}

	@Test
	void cadenaConOrganizacionRequestFilterLlevaElAlcanceHastaAlcanceActual() throws Exception {
		AlcanceSesion alcance = AlcanceSesion.limitado(Set.of(), Set.of(9L));
		OrganizacionRequestFilter organizacion = new OrganizacionRequestFilter(new ResolutorOrganizacion(1L),
				request -> Optional.of(new SesionVerificada(new Usuario(), 3L,
						Set.of(Roles.ROLE_USER, Roles.ROLE_PROFESOR), null, false, alcance)));
		AlcanceActual alcanceActual = new AlcanceActual();
		AtomicReference<AlcanceSesion> visto = new AtomicReference<>();
		AtomicReference<Long> escuela = new AtomicReference<>();

		organizacion.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
				(req, res) -> filtro.doFilter(req, res, (r, s) -> {
					escuela.set(TenantContext.get());
					visto.set(alcanceActual.obtener());
				}));

		assertEquals(3L, escuela.get());
		assertEquals(alcance, visto.get());
	}

	@Test
	void cadenaConGestionDaAlcanceTotalYConstructorCortoNinguno() throws Exception {
		SesionVerificada corta = new SesionVerificada(new Usuario(), 3L, Set.of(Roles.ROLE_MANAGER), null);
		assertSame(AlcanceSesion.NINGUNO, corta.alcance(), "el constructor corto usa NINGUNO");
		OrganizacionRequestFilter organizacion = new OrganizacionRequestFilter(new ResolutorOrganizacion(1L),
				request -> Optional.of(corta));
		AlcanceActual alcanceActual = new AlcanceActual();
		AtomicReference<AlcanceSesion> visto = new AtomicReference<>();

		organizacion.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
				(req, res) -> filtro.doFilter(req, res, (r, s) -> visto.set(alcanceActual.obtener())));

		assertSame(AlcanceSesion.TOTAL, visto.get(), "ROLE_MANAGER en el contexto: total");
	}

	private static Set<String> nombres(Authentication token) {
		return token.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
	}
}
