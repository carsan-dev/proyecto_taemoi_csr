package com.taemoi.project.acceso;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.AlcanceActual;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.GrupoRepository;
import com.taemoi.project.repositories.PreinscripcionRepository;
import com.taemoi.project.repositories.TurnoRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.AccesoFamiliaService;

/**
 * Los casos de {@code AlumnoAccessControlServiceImplTest} (anterior a la entrega 5c-1), trasladados a
 * {@code acceso.alumnoComoFamilia(id)} con el bean y la implementación real del puerto: gestión ve todo sin consultar
 * (ni la regla de familia ni ningún repositorio); la familia pregunta a la regla única
 * ({@link AccesoFamiliaService}) en la escuela de la sesión ({@code TenantContext}), con la misma cuenta que antes.
 */
@ExtendWith(MockitoExtension.class)
class AccesoRecursosFamiliaTest {

	@Mock
	private AccesoFamiliaService accesoFamilia;

	@Mock
	private UsuarioRepository usuarios;

	@Mock
	private AlumnoRepository alumnos;

	@Mock
	private GrupoRepository grupos;

	@Mock
	private TurnoRepository turnos;

	@Mock
	private PreinscripcionRepository preinscripciones;

	@Mock
	private com.taemoi.project.repositories.ProductoAlumnoRepository productosAlumno;

	private AccesoRecursos acceso;

	@BeforeEach
	void escuelaDeLaSesion() {
		TenantContext.set(1L);
		acceso = new AccesoRecursos(new AlcanceActual(),
				new ConsultaAccesoRecursosJpa(accesoFamilia, usuarios, alumnos, grupos, turnos, preinscripciones,
						productosAlumno, org.mockito.Mockito.mock(com.taemoi.project.repositories.CobroRepository.class),
						org.mockito.Mockito.mock(com.taemoi.project.repositories.SedeRepository.class)));
	}

	@AfterEach
	void limpiar() {
		TenantContext.clear();
		SecurityContextHolder.clearContext();
	}

	@Test
	void adminSiemprePermitidoSinConsultar() {
		sesion(buildAuthentication("admin@taemoi.com", "ROLE_ADMIN"));

		assertTrue(acceso.alumnoComoFamilia(99L));
		verifyNoInteractions(accesoFamilia, usuarios, alumnos, grupos, turnos, preinscripciones);
	}

	@Test
	void managerSiemprePermitidoSinConsultar() {
		sesion(buildAuthentication("manager@taemoi.com", "ROLE_MANAGER"));

		assertTrue(acceso.alumnoComoFamilia(99L));
		verifyNoInteractions(accesoFamilia, usuarios, alumnos, grupos, turnos, preinscripciones);
	}

	@Test
	void familiaDeLaSesion_preguntaALaReglaUnica() {
		Usuario cuenta = cuenta(5L, "familia@taemoi.com");
		sesion(new UsernamePasswordAuthenticationToken(cuenta, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
		when(accesoFamilia.puedeAcceder(cuenta, 1L, 7L)).thenReturn(true);
		when(accesoFamilia.puedeAcceder(cuenta, 1L, 8L)).thenReturn(false);

		assertTrue(acceso.alumnoComoFamilia(7L));
		assertFalse(acceso.alumnoComoFamilia(8L));
		verifyNoInteractions(usuarios, alumnos, grupos, turnos, preinscripciones);
	}

	@Test
	void familiaConOtraAutenticacion_seBuscaLaCuentaPorSuEmail() {
		Usuario cuenta = cuenta(6L, "familia@taemoi.com");
		when(usuarios.findByEmailIgnoreCase("familia@taemoi.com")).thenReturn(Optional.of(cuenta));
		when(accesoFamilia.puedeAcceder(cuenta, 1L, 7L)).thenReturn(true);
		sesion(buildAuthentication(" Familia@Taemoi.com ", "ROLE_USER"));

		assertTrue(acceso.alumnoComoFamilia(7L));
	}

	@Test
	void familiaSinCuentaOSinEscuelaActiva_denegado() {
		when(usuarios.findByEmailIgnoreCase("nadie@taemoi.com")).thenReturn(Optional.empty());
		sesion(buildAuthentication("nadie@taemoi.com", "ROLE_USER"));
		assertFalse(acceso.alumnoComoFamilia(7L));
		sesion(buildAuthentication("   ", "ROLE_USER"));
		assertFalse(acceso.alumnoComoFamilia(7L));

		TenantContext.clear();
		sesion(buildAuthentication("familia@taemoi.com", "ROLE_USER"));
		assertFalse(acceso.alumnoComoFamilia(7L));
		verifyNoInteractions(accesoFamilia);
	}

	@Test
	void sinRolDeFamiliaNiGestion_denegado() {
		sesion(buildAuthentication("x@taemoi.com", "ROLE_OTRO"));

		assertFalse(acceso.alumnoComoFamilia(7L));
		verifyNoInteractions(accesoFamilia, usuarios);
	}

	@Test
	void denegadoParaAutenticacionAnonima() {
		sesion(new AnonymousAuthenticationToken("anonymous", "anonymousUser",
				List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

		assertFalse(acceso.alumnoComoFamilia(7L));
		verifyNoInteractions(accesoFamilia, usuarios);
	}

	@Test
	void idNulo_denegadoAunConGestion() {
		sesion(buildAuthentication("admin@taemoi.com", "ROLE_ADMIN"));

		assertFalse(acceso.alumnoComoFamilia(null));
		verifyNoInteractions(accesoFamilia, usuarios);
	}

	private static void sesion(Authentication autenticacion) {
		SecurityContextHolder.getContext().setAuthentication(autenticacion);
	}

	private static Usuario cuenta(Long id, String email) {
		Usuario usuario = new Usuario();
		usuario.setId(id);
		usuario.setEmail(email);
		return usuario;
	}

	private Authentication buildAuthentication(String email, String... roles) {
		return new UsernamePasswordAuthenticationToken(
				email,
				"password",
				Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
	}
}
