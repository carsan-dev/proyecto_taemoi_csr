package com.taemoi.project.acceso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.AlcanceActual;
import com.taemoi.project.organizacion.AlcanceSesion;
import com.taemoi.project.organizacion.AutenticacionEscuela;

/**
 * Lógica de rol × operación × recurso de {@link AccesoRecursos} (entrega 5c-1, entrega5-plan.md §4.2) con el puerto
 * de datos simulado. Gestión decide sin consultar; la familia solo lee alumnos y pregunta al puerto como antes.
 */
@ExtendWith(MockitoExtension.class)
class AccesoRecursosTest {

	private static final Set<Long> SEDES = Set.of(3L);
	private static final Set<Long> GRUPOS = Set.of(30L);

	@Mock
	private ConsultaAccesoRecursos consulta;

	private AccesoRecursos acceso;

	@BeforeEach
	void crear() {
		acceso = new AccesoRecursos(new AlcanceActual(), consulta);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ gestión: total sin consulta

	@ParameterizedTest
	@ValueSource(strings = { "ROLE_ADMIN", "ROLE_MANAGER" })
	void gestion_todoPermitidoSinConsultar(String autoridad) {
		sesion(token(autoridad));
		for (OperacionAcceso op : OperacionAcceso.values()) {
			assertTrue(acceso.alumno(7L, op));
			assertTrue(acceso.grupo(7L, op));
			assertTrue(acceso.turno(7L, op));
			assertTrue(acceso.preinscripcion("REF-1", op));
			assertTrue(acceso.productoAlumno(7L, op));
			assertTrue(acceso.alumno(7L, op.name()));
		}
		verifyNoInteractions(consulta);
	}

	@Test
	void gestionConRolesNuevosYFamilia_sigueTotalSinConsultar() {
		sesion(new AutenticacionEscuela(usuario(), autoridades("ROLE_ADMIN", "ROLE_USER", "ROLE_PROFESOR",
				"ROLE_RESPONSABLE_SEDE"), AlcanceSesion.TOTAL));

		assertTrue(acceso.alumno(7L, OperacionAcceso.EDITAR));
		assertTrue(acceso.preinscripcion("REF-1", OperacionAcceso.LEER));
		verifyNoInteractions(consulta);
	}

	// ------------------------------------------------------------------ entradas inválidas

	@Test
	void idNulo_denegadoSinConsultarAunConGestion() {
		sesion(token("ROLE_ADMIN"));

		assertFalse(acceso.alumno(null, OperacionAcceso.LEER));
		assertFalse(acceso.grupo(null, OperacionAcceso.LEER));
		assertFalse(acceso.turno(null, OperacionAcceso.LEER));
		assertFalse(acceso.preinscripcion(null, OperacionAcceso.LEER));
		assertFalse(acceso.productoAlumno(null, OperacionAcceso.LEER));
		assertFalse(acceso.alumno(null, "LEER"));
		verifyNoInteractions(consulta);
	}

	@Test
	void operacionDesconocidaONula_denegadaSinConsultar() {
		sesion(token("ROLE_ADMIN"));

		assertFalse(acceso.alumno(7L, "BORRAR"));
		assertFalse(acceso.alumno(7L, "leer"));
		assertFalse(acceso.grupo(7L, ""));
		assertFalse(acceso.turno(7L, (String) null));
		assertFalse(acceso.preinscripcion("REF-1", "OTRA"));
		assertFalse(acceso.productoAlumno(7L, "X"));
		assertFalse(acceso.alumno(7L, (OperacionAcceso) null));
		verifyNoInteractions(consulta);
	}

	@Test
	void sinSesionAnonimaONoAutenticada_denegado() {
		assertFalse(acceso.alumno(7L, OperacionAcceso.LEER));

		sesion(new AnonymousAuthenticationToken("clave", "anonymousUser",
				List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
		assertFalse(acceso.alumno(7L, OperacionAcceso.LEER));

		sesion(new UsernamePasswordAuthenticationToken("x@example.test", "clave"));
		assertFalse(acceso.alumno(7L, OperacionAcceso.LEER));
		verifyNoInteractions(consulta);
	}

	@Test
	void sinRolDeEscuela_denegadoSinConsultar() {
		sesion(token("ROLE_ADMIN_PLATAFORMA"));

		assertFalse(acceso.alumno(7L, OperacionAcceso.LEER));
		assertFalse(acceso.grupo(7L, OperacionAcceso.LEER));
		verifyNoInteractions(consulta);
	}

	// ------------------------------------------------------------------ familia

	@Test
	void familia_alumnoLeer_preguntaAlPuertoConSuAutenticacion() {
		Authentication familia = token("ROLE_USER");
		sesion(familia);
		when(consulta.familiaVeAlumno(familia, 7L)).thenReturn(true);
		when(consulta.familiaVeAlumno(familia, 8L)).thenReturn(false);

		assertTrue(acceso.alumno(7L, OperacionAcceso.LEER));
		assertTrue(acceso.alumno(7L, "LEER"));
		assertFalse(acceso.alumno(8L, OperacionAcceso.LEER));
		verify(consulta, never()).alumnoEnSedes(anyLong(), anySet());
		verify(consulta, never()).alumnoEnGrupos(anyLong(), anySet());
	}

	@Test
	void familia_cualquierOtraCombinacion_denegadaSinConsultar() {
		sesion(token("ROLE_USER"));

		assertFalse(acceso.alumno(7L, OperacionAcceso.EDITAR));
		for (OperacionAcceso op : OperacionAcceso.values()) {
			assertFalse(acceso.grupo(7L, op));
			assertFalse(acceso.turno(7L, op));
			assertFalse(acceso.preinscripcion("REF-1", op));
			assertFalse(acceso.productoAlumno(7L, op));
		}
		verifyNoInteractions(consulta);
	}

	// ------------------------------------------------------------------ responsable de sede

	@Test
	void responsable_alumnoPorSusSedesEnAmbasOperaciones() {
		sesion(escuela(AlcanceSesion.limitado(SEDES, Set.of()), "ROLE_RESPONSABLE_SEDE"));
		when(consulta.alumnoEnSedes(7L, SEDES)).thenReturn(true);
		when(consulta.alumnoEnSedes(8L, SEDES)).thenReturn(false);

		assertTrue(acceso.alumno(7L, OperacionAcceso.LEER));
		assertTrue(acceso.alumno(7L, OperacionAcceso.EDITAR));
		assertFalse(acceso.alumno(8L, OperacionAcceso.EDITAR));
		verify(consulta, never()).alumnoEnGrupos(anyLong(), anySet());
		verify(consulta, never()).familiaVeAlumno(any(), anyLong());
	}

	@Test
	void responsable_grupoTurnoYPreinscripcionPorSusSedes() {
		sesion(escuela(AlcanceSesion.limitado(SEDES, Set.of()), "ROLE_RESPONSABLE_SEDE"));
		when(consulta.grupoEnSedes(1L, SEDES)).thenReturn(true);
		when(consulta.turnoEnSedes(2L, SEDES)).thenReturn(true);
		when(consulta.preinscripcionEnSedes("REF-1", SEDES)).thenReturn(true);
		when(consulta.grupoEnSedes(9L, SEDES)).thenReturn(false);
		when(consulta.turnoEnSedes(9L, SEDES)).thenReturn(false);
		when(consulta.preinscripcionEnSedes("REF-9", SEDES)).thenReturn(false);

		for (OperacionAcceso op : OperacionAcceso.values()) {
			assertTrue(acceso.grupo(1L, op));
			assertTrue(acceso.turno(2L, op));
			assertTrue(acceso.preinscripcion("REF-1", op));
			assertFalse(acceso.grupo(9L, op));
			assertFalse(acceso.turno(9L, op));
			assertFalse(acceso.preinscripcion("REF-9", op));
		}
		verify(consulta, never()).grupoEnGrupos(anyLong(), anySet());
		verify(consulta, never()).turnoEnGrupos(anyLong(), anySet());
		verify(consulta, never()).preinscripcionEnGrupos(anyString(), anySet());
	}

	@Test
	void responsable_productoAlumnoDenegadoSinConsultar() {
		sesion(escuela(AlcanceSesion.limitado(SEDES, Set.of()), "ROLE_RESPONSABLE_SEDE"));

		assertFalse(acceso.productoAlumno(7L, OperacionAcceso.LEER));
		assertFalse(acceso.productoAlumno(7L, OperacionAcceso.EDITAR));
		verifyNoInteractions(consulta);
	}

	@Test
	void responsableSinSedes_denegadoSinConsultar() {
		sesion(escuela(AlcanceSesion.limitado(Set.of(), Set.of()), "ROLE_RESPONSABLE_SEDE"));

		assertFalse(acceso.alumno(7L, OperacionAcceso.LEER));
		assertFalse(acceso.grupo(7L, OperacionAcceso.EDITAR));
		verifyNoInteractions(consulta);
	}

	// ------------------------------------------------------------------ profesor

	@Test
	void profesor_leerPorSusGrupos() {
		sesion(escuela(AlcanceSesion.limitado(Set.of(), GRUPOS), "ROLE_PROFESOR"));
		when(consulta.alumnoEnGrupos(7L, GRUPOS)).thenReturn(true);
		when(consulta.alumnoEnGrupos(8L, GRUPOS)).thenReturn(false);
		when(consulta.grupoEnGrupos(30L, GRUPOS)).thenReturn(true);
		when(consulta.turnoEnGrupos(2L, GRUPOS)).thenReturn(true);
		when(consulta.preinscripcionEnGrupos("REF-1", GRUPOS)).thenReturn(true);

		assertTrue(acceso.alumno(7L, OperacionAcceso.LEER));
		assertTrue(acceso.grupo(30L, OperacionAcceso.LEER));
		assertTrue(acceso.turno(2L, OperacionAcceso.LEER));
		assertTrue(acceso.preinscripcion("REF-1", OperacionAcceso.LEER));
		assertFalse(acceso.alumno(8L, OperacionAcceso.LEER));
		assertFalse(acceso.productoAlumno(7L, OperacionAcceso.LEER));
		verify(consulta, never()).alumnoEnSedes(anyLong(), anySet());
		verify(consulta, never()).grupoEnSedes(anyLong(), anySet());
	}

	@Test
	void profesor_editarDenegadoSinConsultar() {
		sesion(escuela(AlcanceSesion.limitado(Set.of(), GRUPOS), "ROLE_PROFESOR"));

		assertFalse(acceso.alumno(7L, OperacionAcceso.EDITAR));
		assertFalse(acceso.grupo(30L, OperacionAcceso.EDITAR));
		assertFalse(acceso.turno(2L, OperacionAcceso.EDITAR));
		assertFalse(acceso.preinscripcion("REF-1", OperacionAcceso.EDITAR));
		assertFalse(acceso.productoAlumno(7L, OperacionAcceso.EDITAR));
		verifyNoInteractions(consulta);
	}

	// ------------------------------------------------------------------ varios roles

	@Test
	void responsableYProfesor_editarSoloPorSedeYLeerPorCualquiera() {
		sesion(escuela(AlcanceSesion.limitado(SEDES, GRUPOS), "ROLE_RESPONSABLE_SEDE", "ROLE_PROFESOR"));
		when(consulta.alumnoEnSedes(7L, SEDES)).thenReturn(false);
		when(consulta.alumnoEnGrupos(7L, GRUPOS)).thenReturn(true);

		assertTrue(acceso.alumno(7L, OperacionAcceso.LEER), "basta con que un rol conceda");
		assertFalse(acceso.alumno(7L, OperacionAcceso.EDITAR), "EDITAR solo por sede");
	}

	@Test
	void familiaYProfesor_leerAlumnoSiCualquieraConcede() {
		AutenticacionEscuela mixta = escuela(AlcanceSesion.limitado(Set.of(), GRUPOS), "ROLE_USER", "ROLE_PROFESOR");
		sesion(mixta);
		when(consulta.familiaVeAlumno(mixta, 7L)).thenReturn(false);
		when(consulta.alumnoEnGrupos(7L, GRUPOS)).thenReturn(true);
		when(consulta.familiaVeAlumno(mixta, 8L)).thenReturn(true);

		assertTrue(acceso.alumno(7L, OperacionAcceso.LEER));
		assertTrue(acceso.alumno(8L, OperacionAcceso.LEER));
		verify(consulta, never()).alumnoEnGrupos(8L, GRUPOS);
	}

	@Test
	void alcanceSinLaAutoridadCorrespondiente_noConcede() {
		// Defensa: un alcance con sedes o grupos no basta sin la autoridad del rol que lo da.
		sesion(escuela(AlcanceSesion.limitado(SEDES, GRUPOS), "ROLE_USER"));

		assertFalse(acceso.grupo(1L, OperacionAcceso.LEER));
		assertFalse(acceso.turno(1L, OperacionAcceso.EDITAR));
		verifyNoInteractions(consulta);
	}

	// ------------------------------------------------------------------ alumnoComoFamilia (D5-2)

	@ParameterizedTest
	@ValueSource(strings = { "ROLE_ADMIN", "ROLE_MANAGER" })
	void comoFamilia_gestionSinConsultar(String autoridad) {
		sesion(token(autoridad));

		assertTrue(acceso.alumnoComoFamilia(7L));
		assertFalse(acceso.alumnoComoFamilia(null));
		verifyNoInteractions(consulta);
	}

	@Test
	void comoFamilia_familiaIgualQueHoy() {
		Authentication familia = token("ROLE_USER");
		sesion(familia);
		when(consulta.familiaVeAlumno(familia, 7L)).thenReturn(true);
		when(consulta.familiaVeAlumno(familia, 8L)).thenReturn(false);

		assertTrue(acceso.alumnoComoFamilia(7L));
		assertFalse(acceso.alumnoComoFamilia(8L));
	}

	@Test
	void comoFamilia_sinSesionAnonimaOSinRolDeFamilia_denegado() {
		assertFalse(acceso.alumnoComoFamilia(7L));
		sesion(new AnonymousAuthenticationToken("clave", "anonymousUser",
				List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
		assertFalse(acceso.alumnoComoFamilia(7L));
		sesion(escuela(AlcanceSesion.limitado(SEDES, GRUPOS), "ROLE_RESPONSABLE_SEDE", "ROLE_PROFESOR"));
		assertFalse(acceso.alumnoComoFamilia(7L));
		verifyNoInteractions(consulta);
	}

	@Test
	void comoFamilia_familiaYProfesor_alumnoDeSuGrupoNoAccesibleComoFamilia_denegado() {
		AutenticacionEscuela mixta = escuela(AlcanceSesion.limitado(Set.of(), GRUPOS), "ROLE_USER", "ROLE_PROFESOR");
		sesion(mixta);
		when(consulta.familiaVeAlumno(mixta, 7L)).thenReturn(false);

		assertFalse(acceso.alumnoComoFamilia(7L));
		verify(consulta, never()).alumnoEnGrupos(anyLong(), anySet());
		verify(consulta, never()).alumnoEnSedes(anyLong(), anySet());
	}

	@Test
	void comoFamilia_familiaYResponsable_alumnoDeSuSedeNoAccesibleComoFamilia_denegado() {
		AutenticacionEscuela mixta = escuela(AlcanceSesion.limitado(SEDES, Set.of()), "ROLE_USER",
				"ROLE_RESPONSABLE_SEDE");
		sesion(mixta);
		when(consulta.familiaVeAlumno(mixta, 7L)).thenReturn(false);
		when(consulta.familiaVeAlumno(mixta, 8L)).thenReturn(true);

		assertFalse(acceso.alumnoComoFamilia(7L));
		assertTrue(acceso.alumnoComoFamilia(8L), "su alumno de familia, sí");
		verify(consulta, never()).alumnoEnSedes(anyLong(), anySet());
		verify(consulta, never()).alumnoEnGrupos(anyLong(), anySet());
	}

	// ------------------------------------------------------------------ métodos solo por sede (entrega 6, §2.1)

	@ParameterizedTest
	@ValueSource(strings = { "ROLE_ADMIN", "ROLE_MANAGER" })
	void enSede_gestionTodoSinConsultar(String autoridad) {
		sesion(token(autoridad));
		for (OperacionAcceso op : OperacionAcceso.values()) {
			assertTrue(acceso.alumnoEnSede(7L, op.name()));
			assertTrue(acceso.grupoEnSede(7L, op.name()));
			assertTrue(acceso.turnoEnSede(7L, op.name()));
		}
		assertTrue(acceso.alumnoComoFamiliaOSede(7L));
		assertFalse(acceso.alumnoEnSede(null, "LEER"));
		assertFalse(acceso.alumnoEnSede(7L, "BORRAR"));
		assertFalse(acceso.alumnoComoFamiliaOSede(null));
		verifyNoInteractions(consulta);
	}

	@Test
	void enSede_responsable_leerPorD9EditarSoloPorSedePrincipal() {
		sesion(escuela(AlcanceSesion.limitado(SEDES, Set.of()), "ROLE_RESPONSABLE_SEDE"));
		when(consulta.alumnoEnSedes(7L, SEDES)).thenReturn(true);
		when(consulta.alumnoConSedePrincipalEn(7L, SEDES)).thenReturn(false);
		when(consulta.alumnoConSedePrincipalEn(8L, SEDES)).thenReturn(true);

		assertTrue(acceso.alumnoEnSede(7L, "LEER"), "D9: por grupo de su sede");
		assertFalse(acceso.alumnoEnSede(7L, "EDITAR"), "D6-3: sin ser su sede principal, solo lectura");
		assertTrue(acceso.alumnoEnSede(8L, "EDITAR"));
		verify(consulta, never()).alumnoEnSedes(8L, SEDES);
		verify(consulta, never()).alumnoEnGrupos(anyLong(), anySet());
		verify(consulta, never()).familiaVeAlumno(any(), anyLong());
	}

	@Test
	void enSede_responsable_grupoYTurnoPorSusSedes() {
		sesion(escuela(AlcanceSesion.limitado(SEDES, Set.of()), "ROLE_RESPONSABLE_SEDE"));
		when(consulta.grupoEnSedes(1L, SEDES)).thenReturn(true);
		when(consulta.turnoEnSedes(2L, SEDES)).thenReturn(true);
		when(consulta.grupoEnSedes(9L, SEDES)).thenReturn(false);
		when(consulta.turnoEnSedes(9L, SEDES)).thenReturn(false);

		for (OperacionAcceso op : OperacionAcceso.values()) {
			assertTrue(acceso.grupoEnSede(1L, op.name()));
			assertTrue(acceso.turnoEnSede(2L, op.name()));
			assertFalse(acceso.grupoEnSede(9L, op.name()));
			assertFalse(acceso.turnoEnSede(9L, op.name()));
		}
		verify(consulta, never()).grupoEnGrupos(anyLong(), anySet());
		verify(consulta, never()).turnoEnGrupos(anyLong(), anySet());
	}

	@Test
	void enSede_profesor_nuncaConcedeNiConsulta() {
		// La rama de profesor llega en la entrega 7: los métodos de la E6 no la activan.
		sesion(escuela(AlcanceSesion.limitado(Set.of(), GRUPOS), "ROLE_PROFESOR"));

		assertFalse(acceso.alumnoEnSede(7L, "LEER"));
		assertFalse(acceso.grupoEnSede(30L, "LEER"));
		assertFalse(acceso.turnoEnSede(2L, "LEER"));
		assertFalse(acceso.alumnoComoFamiliaOSede(7L));
		verifyNoInteractions(consulta);
	}

	@Test
	void enSede_responsableYProfesor_soloPorSedes() {
		sesion(escuela(AlcanceSesion.limitado(SEDES, GRUPOS), "ROLE_RESPONSABLE_SEDE", "ROLE_PROFESOR"));
		when(consulta.alumnoEnSedes(7L, SEDES)).thenReturn(false);

		assertFalse(acceso.alumnoEnSede(7L, "LEER"), "su grupo de profesor no cuenta");
		assertFalse(acceso.grupoEnSede(30L, "LEER"));
		verify(consulta, never()).alumnoEnGrupos(anyLong(), anySet());
		verify(consulta, never()).grupoEnGrupos(anyLong(), anySet());
	}

	@Test
	void enSede_familia_denegadoSinConsultar() {
		sesion(token("ROLE_USER"));

		for (OperacionAcceso op : OperacionAcceso.values()) {
			assertFalse(acceso.alumnoEnSede(7L, op.name()));
			assertFalse(acceso.grupoEnSede(7L, op.name()));
			assertFalse(acceso.turnoEnSede(7L, op.name()));
		}
		verifyNoInteractions(consulta);
	}

	@Test
	void comoFamiliaOSede_familiaIgualQueAlumnoComoFamilia() {
		Authentication familia = token("ROLE_USER");
		sesion(familia);
		when(consulta.familiaVeAlumno(familia, 7L)).thenReturn(true);
		when(consulta.familiaVeAlumno(familia, 8L)).thenReturn(false);

		assertTrue(acceso.alumnoComoFamiliaOSede(7L));
		assertFalse(acceso.alumnoComoFamiliaOSede(8L));
		verify(consulta, never()).alumnoEnSedes(anyLong(), anySet());
	}

	@Test
	void comoFamiliaOSede_familiaYProfesor_soloComoFamilia() {
		AutenticacionEscuela mixta = escuela(AlcanceSesion.limitado(Set.of(), GRUPOS), "ROLE_USER", "ROLE_PROFESOR");
		sesion(mixta);
		when(consulta.familiaVeAlumno(mixta, 7L)).thenReturn(false);

		assertFalse(acceso.alumnoComoFamiliaOSede(7L), "D5-2: la rama de profesor no se activa");
		verify(consulta, never()).alumnoEnGrupos(anyLong(), anySet());
		verify(consulta, never()).alumnoEnSedes(anyLong(), anySet());
	}

	@Test
	void comoFamiliaOSede_familiaYResponsable_porSuSedeOComoFamilia() {
		AutenticacionEscuela mixta = escuela(AlcanceSesion.limitado(SEDES, Set.of()), "ROLE_USER",
				"ROLE_RESPONSABLE_SEDE");
		sesion(mixta);
		when(consulta.familiaVeAlumno(mixta, 7L)).thenReturn(false);
		when(consulta.alumnoEnSedes(7L, SEDES)).thenReturn(true);
		when(consulta.familiaVeAlumno(mixta, 8L)).thenReturn(true);
		when(consulta.familiaVeAlumno(mixta, 9L)).thenReturn(false);
		when(consulta.alumnoEnSedes(9L, SEDES)).thenReturn(false);

		assertTrue(acceso.alumnoComoFamiliaOSede(7L), "por su sede (D6-15)");
		assertTrue(acceso.alumnoComoFamiliaOSede(8L), "su alumno de familia");
		assertFalse(acceso.alumnoComoFamiliaOSede(9L));
	}

	// ------------------------------------------------------------------ preinscripciones y convocatorias (6-2)

	@ParameterizedTest
	@ValueSource(strings = { "ROLE_ADMIN", "ROLE_MANAGER" })
	void preinscripcionYConvocatoriaEnSede_gestionTodoSinConsultar(String autoridad) {
		sesion(token(autoridad));
		for (OperacionAcceso op : OperacionAcceso.values()) {
			assertTrue(acceso.preinscripcionEnSede("PRE-1", op.name()));
			assertTrue(acceso.alumnoConvocatoriaEnSede(5L, op.name()));
		}
		assertFalse(acceso.preinscripcionEnSede(null, "LEER"));
		assertFalse(acceso.alumnoConvocatoriaEnSede(null, "EDITAR"));
		assertFalse(acceso.preinscripcionEnSede("PRE-1", "BORRAR"));
		verifyNoInteractions(consulta);
	}

	@Test
	void preinscripcionEnSede_responsablePorElGrupoDeLaSolicitud() {
		sesion(escuela(AlcanceSesion.limitado(SEDES, Set.of()), "ROLE_RESPONSABLE_SEDE"));
		when(consulta.preinscripcionEnSedes("PRE-1", SEDES)).thenReturn(true);
		when(consulta.preinscripcionEnSedes("PRE-2", SEDES)).thenReturn(false);

		for (OperacionAcceso op : OperacionAcceso.values()) {
			assertTrue(acceso.preinscripcionEnSede("PRE-1", op.name()));
			assertFalse(acceso.preinscripcionEnSede("PRE-2", op.name()));
		}
		verify(consulta, never()).preinscripcionEnGrupos(anyString(), anySet());
	}

	@Test
	void alumnoConvocatoriaEnSede_responsableLeerPorD9EditarPorSedePrincipal() {
		sesion(escuela(AlcanceSesion.limitado(SEDES, Set.of()), "ROLE_RESPONSABLE_SEDE"));
		when(consulta.alumnoConvocatoriaEnSedes(5L, SEDES)).thenReturn(true);
		when(consulta.alumnoConvocatoriaConSedePrincipalEn(5L, SEDES)).thenReturn(false);
		when(consulta.alumnoConvocatoriaConSedePrincipalEn(6L, SEDES)).thenReturn(true);

		assertTrue(acceso.alumnoConvocatoriaEnSede(5L, "LEER"), "D9: su alumno por grupo");
		assertFalse(acceso.alumnoConvocatoriaEnSede(5L, "EDITAR"), "D6-3: marcar el pago, solo su sede principal");
		assertTrue(acceso.alumnoConvocatoriaEnSede(6L, "EDITAR"));
		verify(consulta, never()).alumnoConvocatoriaEnSedes(6L, SEDES);
	}

	@Test
	void preinscripcionYConvocatoriaEnSede_familiaYProfesorNuncaConcedenNiConsultan() {
		sesion(escuela(AlcanceSesion.limitado(Set.of(), GRUPOS), "ROLE_USER", "ROLE_PROFESOR"));

		for (OperacionAcceso op : OperacionAcceso.values()) {
			assertFalse(acceso.preinscripcionEnSede("PRE-1", op.name()));
			assertFalse(acceso.alumnoConvocatoriaEnSede(5L, op.name()));
		}
		verifyNoInteractions(consulta);
	}

	@Test
	void preinscripcionEnSede_responsableYProfesor_soloPorSedes() {
		sesion(escuela(AlcanceSesion.limitado(SEDES, GRUPOS), "ROLE_RESPONSABLE_SEDE", "ROLE_PROFESOR"));
		when(consulta.preinscripcionEnSedes("PRE-1", SEDES)).thenReturn(false);

		assertFalse(acceso.preinscripcionEnSede("PRE-1", "LEER"), "su grupo de profesor no cuenta");
		verify(consulta, never()).preinscripcionEnGrupos(anyString(), anySet());
	}

	// ------------------------------------------------------------------ tesorería (6-3)

	@ParameterizedTest
	@ValueSource(strings = { "ROLE_ADMIN", "ROLE_MANAGER" })
	void productoAlumnoEnSede_gestionSinConsultar(String autoridad) {
		sesion(token(autoridad));
		for (OperacionAcceso op : OperacionAcceso.values()) {
			assertTrue(acceso.productoAlumnoEnSede(9L, op.name()));
		}
		assertFalse(acceso.productoAlumnoEnSede(null, "EDITAR"));
		assertFalse(acceso.productoAlumnoEnSede(9L, "BORRAR"));
		verifyNoInteractions(consulta);
	}

	@Test
	void productoAlumnoEnSede_responsablePorLaSedeDelCargo() {
		sesion(escuela(AlcanceSesion.limitado(SEDES, Set.of()), "ROLE_RESPONSABLE_SEDE"));
		when(consulta.productoAlumnoEnSedes(9L, SEDES)).thenReturn(true);
		when(consulta.productoAlumnoEnSedes(10L, SEDES)).thenReturn(false);

		for (OperacionAcceso op : OperacionAcceso.values()) {
			assertTrue(acceso.productoAlumnoEnSede(9L, op.name()));
			assertFalse(acceso.productoAlumnoEnSede(10L, op.name()));
		}
		assertFalse(acceso.productoAlumno(9L, "EDITAR"), "el método de antes sigue solo con alcance total");
	}

	@Test
	void productoAlumnoEnSede_familiaYProfesorNuncaConcedenNiConsultan() {
		sesion(escuela(AlcanceSesion.limitado(Set.of(), GRUPOS), "ROLE_USER", "ROLE_PROFESOR"));
		for (OperacionAcceso op : OperacionAcceso.values()) {
			assertFalse(acceso.productoAlumnoEnSede(9L, op.name()));
		}
		verifyNoInteractions(consulta);
	}

	@Test
	void productoAlumnoEnSede_responsableSinSedes_noConsulta() {
		sesion(escuela(AlcanceSesion.limitado(Set.of(), Set.of()), "ROLE_RESPONSABLE_SEDE", "ROLE_USER"));
		assertFalse(acceso.productoAlumnoEnSede(9L, "LEER"));
		verifyNoInteractions(consulta);
	}

	// ------------------------------------------------------------------ SpEL (5c-2)

	@Test
	void spel_resuelveLaFirmaConStringSinAmbiguedad() {
		sesion(escuela(AlcanceSesion.limitado(SEDES, Set.of()), "ROLE_RESPONSABLE_SEDE"));
		when(consulta.grupoEnSedes(1L, SEDES)).thenReturn(true);
		StandardEvaluationContext contexto = new StandardEvaluationContext();
		contexto.setBeanResolver((ctx, nombre) -> {
			assertEquals("acceso", nombre);
			return acceso;
		});
		contexto.setVariable("grupoId", 1L);
		SpelExpressionParser parser = new SpelExpressionParser();

		assertEquals(Boolean.TRUE, parser.parseExpression("@acceso.grupo(#grupoId, 'EDITAR')").getValue(contexto));
		assertEquals(Boolean.FALSE, parser.parseExpression("@acceso.grupo(#grupoId, 'BORRAR')").getValue(contexto));
	}

	@Test
	void comprobacionesDelPuertoNoSeMezclanEntreRecursos() {
		// Cada recurso usa su propio par de consultas.
		List<BiPredicate<Long, OperacionAcceso>> recursos = List.of(acceso::alumno, acceso::grupo, acceso::turno);
		sesion(escuela(AlcanceSesion.limitado(SEDES, Set.of()), "ROLE_RESPONSABLE_SEDE"));
		for (BiPredicate<Long, OperacionAcceso> recurso : recursos) {
			assertFalse(recurso.test(5L, OperacionAcceso.LEER));
		}
		verify(consulta).alumnoEnSedes(5L, SEDES);
		verify(consulta).grupoEnSedes(5L, SEDES);
		verify(consulta).turnoEnSedes(5L, SEDES);
	}

	// ------------------------------------------------------------------ pieza 6b-2: caja

	@Test
	void cargoDeMisGrupos_soloLaRamaDeProfesor_tambienParaEscribir() {
		sesion(escuela(AlcanceSesion.limitado(Set.of(), GRUPOS), "ROLE_USER", "ROLE_PROFESOR"));
		org.mockito.Mockito.lenient().when(consulta.productoAlumnoEnGrupos(7L, GRUPOS)).thenReturn(true);
		assertTrue(acceso.cargoDeMisGrupos(7L));
		assertFalse(acceso.cargoDeMisGrupos(8L));
		assertFalse(acceso.cargoDeMisGrupos(null));
	}

	@ParameterizedTest
	@ValueSource(strings = { "ROLE_ADMIN", "ROLE_MANAGER", "ROLE_RESPONSABLE_SEDE", "ROLE_USER" })
	void cargoDeMisGrupos_niGestionNiResponsableNiFamilia(String autoridad) {
		sesion(escuela(AlcanceSesion.limitado(SEDES, Set.of()), autoridad));
		assertFalse(acceso.cargoDeMisGrupos(7L));
		// Gestión que además es profesor: alcance total, sin grupos → no entra por las rutas del profesor.
		sesion(escuela(AlcanceSesion.TOTAL, autoridad, "ROLE_PROFESOR"));
		assertFalse(acceso.cargoDeMisGrupos(7L));
		verify(consulta, never()).productoAlumnoEnGrupos(anyLong(), anySet());
	}

	@Test
	void alumnoDeMisGrupos_soloLaRamaDeProfesor() {
		sesion(escuela(AlcanceSesion.limitado(Set.of(), GRUPOS), "ROLE_USER", "ROLE_PROFESOR"));
		org.mockito.Mockito.lenient().when(consulta.alumnoEnGrupos(7L, GRUPOS)).thenReturn(true);
		assertTrue(acceso.alumnoDeMisGrupos(7L));
		assertFalse(acceso.alumnoDeMisGrupos(8L));
		sesion(escuela(AlcanceSesion.TOTAL, "ROLE_ADMIN", "ROLE_PROFESOR"));
		assertFalse(acceso.alumnoDeMisGrupos(7L), "la gestión no vende por la ruta del profesor");
		sesion(escuela(AlcanceSesion.limitado(SEDES, Set.of()), "ROLE_RESPONSABLE_SEDE"));
		assertFalse(acceso.alumnoDeMisGrupos(7L));
	}

	@Test
	void cargoDeMisGrupos_profesorSinGrupos_noConsulta() {
		sesion(escuela(AlcanceSesion.limitado(Set.of(), Set.of()), "ROLE_PROFESOR"));
		assertFalse(acceso.cargoDeMisGrupos(7L));
		verifyNoInteractions(consulta);
	}

	@Test
	void cobroPropioDelProfesor_porSuCuenta() {
		sesion(escuela(AlcanceSesion.limitado(Set.of(), GRUPOS), "ROLE_PROFESOR"));
		org.mockito.Mockito.lenient().when(consulta.cobroDelProfesor(9L, 5L)).thenReturn(true);
		assertTrue(acceso.cobroPropioDelProfesor(9L));
		assertFalse(acceso.cobroPropioDelProfesor(10L));
		sesion(escuela(AlcanceSesion.TOTAL, "ROLE_ADMIN"));
		assertFalse(acceso.cobroPropioDelProfesor(9L), "el propietario no deshace por la ruta del profesor");
	}

	@Test
	void cajaDeSede_gestionSinConsultar_responsablePorSusSedes_profesorNunca() {
		sesion(token("ROLE_MANAGER"));
		assertTrue(acceso.cajaDeSede(3L));
		verifyNoInteractions(consulta);

		sesion(escuela(AlcanceSesion.limitado(SEDES, Set.of()), "ROLE_RESPONSABLE_SEDE"));
		org.mockito.Mockito.lenient().when(consulta.sedeEnSedes(3L, SEDES)).thenReturn(true);
		assertTrue(acceso.cajaDeSede(3L));
		assertFalse(acceso.cajaDeSede(4L));

		sesion(escuela(AlcanceSesion.limitado(Set.of(), GRUPOS), "ROLE_PROFESOR"));
		assertFalse(acceso.cajaDeSede(3L));
	}

	// ------------------------------------------------------------------ utilidades

	private static void sesion(Authentication autenticacion) {
		SecurityContextHolder.getContext().setAuthentication(autenticacion);
	}

	private static Authentication token(String... autoridades) {
		return new UsernamePasswordAuthenticationToken("cuenta@example.test", null, autoridades(autoridades));
	}

	private static AutenticacionEscuela escuela(AlcanceSesion alcance, String... autoridades) {
		return new AutenticacionEscuela(usuario(), autoridades(autoridades), alcance);
	}

	private static List<GrantedAuthority> autoridades(String... nombres) {
		return Arrays.stream(nombres).<GrantedAuthority>map(SimpleGrantedAuthority::new).toList();
	}

	private static Usuario usuario() {
		Usuario usuario = new Usuario();
		usuario.setId(5L);
		usuario.setEmail("cuenta@example.test");
		return usuario;
	}
}
