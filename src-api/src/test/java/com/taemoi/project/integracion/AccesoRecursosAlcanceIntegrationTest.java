package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.acceso.AccesoRecursos;
import com.taemoi.project.acceso.ConsultaAccesoRecursos;
import com.taemoi.project.acceso.OperacionAcceso;
import com.taemoi.project.acceso.PredicadosAlcance;
import com.taemoi.project.dtos.response.GrupoConAlumnosDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Grupo;
import com.taemoi.project.entities.Preinscripcion;
import com.taemoi.project.entities.Turno;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.AlcanceSesion;
import com.taemoi.project.organizacion.AutenticacionEscuela;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.GrupoRepository;
import com.taemoi.project.repositories.PreinscripcionRepository;
import com.taemoi.project.repositories.TurnoRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Entrega 5c-1 contra MySQL: la implementación JPA de {@link ConsultaAccesoRecursos} y los {@link PredicadosAlcance}
 * (entrega5-plan.md §3.4 y §4.2) sobre entidades {@code @TenantId}. D9 (sede principal O grupo de la sede), turno y
 * preinscripción por su grupo, recursos sin grupo fuera de cualquier alcance limitado y otra escuela invisible, también
 * con una fila de {@code alumno_grupo} insertada a mano entre escuelas. Además, {@code Alumno.sedePrincipalId} es de
 * solo lectura. Datos sintéticos en escuelas nuevas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccesoRecursosAlcanceIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;

	@Autowired ConsultaAccesoRecursos consulta;
	@Autowired AccesoRecursos acceso;
	@Autowired AlumnoRepository alumnos;
	@Autowired GrupoRepository grupos;
	@Autowired TurnoRepository turnos;
	@Autowired PreinscripcionRepository preinscripciones;
	@Autowired GrupoService grupoService;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired MockMvc mvc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;

	long orgA, s1, s2, g1, g2, gSinSede;
	long aPrincipal, aGrupo1, aGrupo2, aNada;
	long t1, t2, tSin;
	String p1, p2, pSin;

	long orgB, sB, gB, aB, tB;
	String pB;

	@BeforeAll
	void crearEscuelas() {
		orgA = organizacion("it-acc-a");
		s1 = sede(orgA);
		g1 = grupo(orgA, "IT acceso G1");
		g2 = grupo(orgA, "IT acceso G2");
		gSinSede = grupo(orgA, "IT acceso sin sede");
		s2 = sede(orgA);
		jdbc.update("UPDATE grupo SET sede_id = ? WHERE id = ?", s2, g2);
		jdbc.update("UPDATE grupo SET sede_id = NULL WHERE id = ?", gSinSede);

		aPrincipal = alumno(orgA, s1);
		aGrupo1 = alumno(orgA, s2);
		aGrupo2 = alumno(orgA, null);
		aNada = alumno(orgA, null);
		miembro(g1, aGrupo1);
		miembro(g2, aGrupo2);
		miembro(gSinSede, aNada);

		t1 = turno(orgA, g1);
		t2 = turno(orgA, g2);
		tSin = turno(orgA, null);
		p1 = preinscripcion(orgA, g1);
		p2 = preinscripcion(orgA, g2);
		pSin = preinscripcion(orgA, null);

		orgB = organizacion("it-acc-b");
		sB = sede(orgB);
		gB = grupo(orgB, "IT acceso GB");
		aB = alumno(orgB, sB);
		miembro(gB, aB);
		tB = turno(orgB, gB);
		pB = preinscripcion(orgB, gB);
		// Fila entre escuelas (solo FK simples): el alumno aNada de A en el grupo gB de B.
		miembro(gB, aNada);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ puerto

	@Test
	void alumno_d9_sedePrincipalOGrupoDeLaSede() {
		enA(() -> {
			assertTrue(consulta.alumnoEnSedes(aPrincipal, Set.of(s1)), "por sede principal");
			assertTrue(consulta.alumnoEnSedes(aGrupo1, Set.of(s1)), "por grupo de la sede");
			assertTrue(consulta.alumnoEnSedes(aGrupo1, Set.of(s2)), "y por su sede principal");
			assertFalse(consulta.alumnoEnSedes(aGrupo2, Set.of(s1)));
			assertTrue(consulta.alumnoEnSedes(aGrupo2, Set.of(s2)));
			assertFalse(consulta.alumnoEnSedes(aNada, Set.of(s1, s2)), "grupo sin sede: fuera");

			assertTrue(consulta.alumnoEnGrupos(aGrupo1, Set.of(g1)));
			assertFalse(consulta.alumnoEnGrupos(aPrincipal, Set.of(g1, g2)), "la sede principal no da grupo");
			assertFalse(consulta.alumnoEnGrupos(aGrupo2, Set.of(g1)));
			assertTrue(consulta.alumnoEnGrupos(aGrupo2, Set.of(g1, g2)));
			return null;
		});
	}

	@Test
	void grupoTurnoYPreinscripcion_porSuGrupo() {
		enA(() -> {
			assertTrue(consulta.grupoEnSedes(g1, Set.of(s1)));
			assertFalse(consulta.grupoEnSedes(g2, Set.of(s1)));
			assertFalse(consulta.grupoEnSedes(gSinSede, Set.of(s1, s2)));
			assertTrue(consulta.grupoEnGrupos(g1, Set.of(g1)));
			assertFalse(consulta.grupoEnGrupos(g2, Set.of(g1)));

			assertTrue(consulta.turnoEnSedes(t1, Set.of(s1)));
			assertFalse(consulta.turnoEnSedes(t2, Set.of(s1)));
			assertFalse(consulta.turnoEnSedes(tSin, Set.of(s1, s2)), "turno sin grupo");
			assertTrue(consulta.turnoEnGrupos(t2, Set.of(g2)));
			assertFalse(consulta.turnoEnGrupos(tSin, Set.of(g1, g2, gSinSede)), "turno sin grupo");

			assertTrue(consulta.preinscripcionEnSedes(p1, Set.of(s1)));
			assertFalse(consulta.preinscripcionEnSedes(p2, Set.of(s1)));
			assertFalse(consulta.preinscripcionEnSedes(pSin, Set.of(s1, s2)), "preinscripción sin grupo");
			assertTrue(consulta.preinscripcionEnGrupos(p2, Set.of(g2)));
			assertFalse(consulta.preinscripcionEnGrupos(pSin, Set.of(g1, g2, gSinSede)), "preinscripción sin grupo");
			return null;
		});
	}

	@Test
	void otraEscuela_invisibleAunqueSusIdsEstenEnElConjunto() {
		enA(() -> {
			assertFalse(consulta.alumnoEnSedes(aB, Set.of(sB)));
			assertFalse(consulta.alumnoEnGrupos(aB, Set.of(gB)));
			assertFalse(consulta.grupoEnSedes(gB, Set.of(sB)));
			assertFalse(consulta.grupoEnGrupos(gB, Set.of(gB)));
			assertFalse(consulta.turnoEnSedes(tB, Set.of(sB)));
			assertFalse(consulta.turnoEnGrupos(tB, Set.of(gB)));
			assertFalse(consulta.preinscripcionEnSedes(pB, Set.of(sB)));
			assertFalse(consulta.preinscripcionEnGrupos(pB, Set.of(gB)));
			// aNada (de A) está en gB (de B) por una fila a mano: el grupo de B no cuenta.
			assertFalse(consulta.alumnoEnSedes(aNada, Set.of(sB)));
			assertFalse(consulta.alumnoEnGrupos(aNada, Set.of(gB)));
			return null;
		});
		// Control: en su escuela, los mismos datos sí están.
		TenantContext.calcularCon(orgB, () -> {
			assertTrue(consulta.alumnoEnSedes(aB, Set.of(sB)));
			assertTrue(consulta.grupoEnGrupos(gB, Set.of(gB)));
			assertTrue(consulta.turnoEnSedes(tB, Set.of(sB)));
			assertTrue(consulta.preinscripcionEnGrupos(pB, Set.of(gB)));
			assertFalse(consulta.alumnoEnGrupos(aNada, Set.of(gB)), "el alumno de A no aparece en B");
			return null;
		});
	}

	@Test
	void sinEscuelaIdNuloOConjuntoVacio_falseSinConsultar() {
		assertFalse(consulta.alumnoEnSedes(aPrincipal, Set.of(s1)), "sin escuela activa");
		assertFalse(consulta.preinscripcionEnGrupos(p1, Set.of(g1)), "sin escuela activa");
		enA(() -> {
			assertFalse(consulta.alumnoEnSedes(null, Set.of(s1)));
			assertFalse(consulta.alumnoEnSedes(aPrincipal, Set.of()));
			assertFalse(consulta.grupoEnGrupos(g1, null));
			assertFalse(consulta.preinscripcionEnSedes(null, Set.of(s1)));
			return null;
		});
	}

	@Test
	void bean_conElPuertoReal() {
		Usuario cuenta = new Usuario();
		cuenta.setId(999_999L);
		cuenta.setEmail("it-acceso@example.test");
		SecurityContextHolder.getContext().setAuthentication(new AutenticacionEscuela(cuenta,
				List.of(new SimpleGrantedAuthority("ROLE_RESPONSABLE_SEDE"), new SimpleGrantedAuthority("ROLE_PROFESOR")),
				AlcanceSesion.limitado(Set.of(s1), Set.of(g2))));
		enA(() -> {
			assertTrue(acceso.alumno(aGrupo1, OperacionAcceso.EDITAR), "sede s1 por D9");
			assertTrue(acceso.alumno(aGrupo2, "LEER"), "grupo g2 del profesor");
			assertFalse(acceso.alumno(aGrupo2, OperacionAcceso.EDITAR), "el profesor no edita");
			assertTrue(acceso.preinscripcion(p1, OperacionAcceso.EDITAR));
			assertFalse(acceso.turno(tSin, OperacionAcceso.LEER));
			assertFalse(acceso.productoAlumno(1L, OperacionAcceso.LEER));
			return null;
		});
	}

	// ------------------------------------------------------------------ cuentas mixtas en rutas de familia (D5-2)

	@Test
	void cuentaMixta_soloEntraComoFamiliaEnLasRutasDeFamilia() throws Exception {
		long profesor = cuentaFamiliaConAlumnoPropio();
		long alumnoProfesor = ultimoAlumnoVinculado(profesor);
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROFESOR')", profesor,
				orgA);
		jdbc.update("""
				INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol)
				VALUES (?, ?, ?, 'TITULAR')""", orgA, profesor, g1);

		long responsable = cuentaFamiliaConAlumnoPropio();
		long alumnoResponsable = ultimoAlumnoVinculado(responsable);
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'RESPONSABLE_SEDE')",
				responsable, orgA);
		long membresiaResp = jdbc.queryForObject("""
				SELECT id FROM membresia WHERE usuario_id = ? AND organizacion_id = ? AND rol = 'RESPONSABLE_SEDE'""",
				Long.class, responsable, orgA);
		jdbc.update("INSERT INTO membresia_sede (membresia_id, sede_id) VALUES (?, ?)", membresiaResp, s1);

		for (long cuenta : List.of(profesor, responsable)) {
			// aGrupo1 está en g1 (sede s1): en su grupo y en su sede, pero no es suyo como familia.
			MvcResult ajeno = peticion("/api/alumnos/" + aGrupo1 + "/grupos", cuenta);
			assertEquals(403, ajeno.getResponse().getStatus(), "cuenta " + cuenta);
			assertTrue(ajeno.getResponse().getContentAsString().contains("Acceso denegado"),
					"403 de @PreAuthorize (5c-2, D5-4): " + ajeno.getResponse().getContentAsString());
			assertEquals(403, peticion("/api/convocatorias/alumnos/" + aGrupo1, cuenta).getResponse().getStatus());
		}
		assertEquals(200, peticion("/api/alumnos/" + alumnoProfesor + "/grupos", profesor).getResponse().getStatus());
		assertEquals(200,
				peticion("/api/alumnos/" + alumnoResponsable + "/grupos", responsable).getResponse().getStatus());
	}

	// ------------------------------------------------------------------ predicados para listados

	@Test
	void predicados_alumnos() {
		enA(() -> {
			assertEquals(Set.of(aPrincipal, aGrupo1), idsAlumnos(AlcanceSesion.limitado(Set.of(s1), Set.of())));
			assertEquals(Set.of(aGrupo2), idsAlumnos(AlcanceSesion.limitado(Set.of(), Set.of(g2))));
			assertEquals(Set.of(aPrincipal, aGrupo1, aGrupo2), idsAlumnos(AlcanceSesion.limitado(Set.of(s1), Set.of(g2))));
			// Sin restricción (la prueba de cuentas mixtas añade alumnos propios en A, sin sede ni grupo).
			assertTrue(idsAlumnos(AlcanceSesion.TOTAL).containsAll(Set.of(aPrincipal, aGrupo1, aGrupo2, aNada)));
			assertEquals(Set.of(), idsAlumnos(AlcanceSesion.NINGUNO));
			// Con paginación (consulta de recuento con la subconsulta).
			assertEquals(2, alumnos.findAll(PredicadosAlcance.alumnos(AlcanceSesion.limitado(Set.of(s1), Set.of())),
					PageRequest.of(0, 1)).getTotalElements());
			return null;
		});
	}

	@Test
	void predicados_gruposTurnosYPreinscripciones() {
		enA(() -> {
			assertEquals(Set.of(g1, g2), ids(grupos.findAll(PredicadosAlcance.grupos(
					AlcanceSesion.limitado(Set.of(s2), Set.of(g1)))), Grupo::getId));
			assertEquals(Set.of(g1, g2, gSinSede), ids(grupos.findAll(PredicadosAlcance.grupos(AlcanceSesion.TOTAL)),
					Grupo::getId));

			assertEquals(Set.of(t1), ids(turnos.findAll(PredicadosAlcance.turnos(
					AlcanceSesion.limitado(Set.of(s1), Set.of()))), Turno::getId));
			assertEquals(Set.of(t1, t2), ids(turnos.findAll(PredicadosAlcance.turnos(
					AlcanceSesion.limitado(Set.of(s1), Set.of(g2)))), Turno::getId));
			assertEquals(Set.of(t1, t2, tSin), ids(turnos.findAll(PredicadosAlcance.turnos(AlcanceSesion.TOTAL)),
					Turno::getId));

			assertEquals(Set.of(p1, p2), ids(preinscripciones.findAll(PredicadosAlcance.preinscripciones(
					AlcanceSesion.limitado(Set.of(), Set.of(g1, g2, gSinSede)))), Preinscripcion::getReferencia));
			assertEquals(Set.of(p2), ids(preinscripciones.findAll(PredicadosAlcance.preinscripciones(
					AlcanceSesion.limitado(Set.of(s2), Set.of()))), Preinscripcion::getReferencia));
			assertEquals(Set.of(p1, p2, pSin), ids(preinscripciones.findAll(PredicadosAlcance.preinscripciones(
					AlcanceSesion.TOTAL)), Preinscripcion::getReferencia));
			return null;
		});
	}

	// ------------------------------------------------------------------ Alumno.sedePrincipalId

	@Test
	void sedePrincipal_soloLecturaYFueraDelJson() throws Exception {
		Alumno cargado = enA(() -> alumnos.findById(aPrincipal).orElseThrow());
		assertEquals(s1, cargado.getSedePrincipalId());

		// Un UPDATE de la entidad no la pisa, aunque el campo cambie en memoria.
		Field campo = Alumno.class.getDeclaredField("sedePrincipalId");
		campo.setAccessible(true);
		campo.set(cargado, s2);
		cargado.setDireccion("Calle Cambiada 2");
		enA(() -> alumnos.save(cargado));
		assertEquals(s1, jdbc.queryForObject("SELECT sede_principal_id FROM alumno WHERE id = ?", Long.class, aPrincipal));
		assertEquals("Calle Cambiada 2",
				jdbc.queryForObject("SELECT direccion FROM alumno WHERE id = ?", String.class, aPrincipal));

		// Un INSERT tampoco la escribe.
		Alumno nuevo = alumnoEntidad();
		campo.set(nuevo, s1);
		long idNuevo = enA(() -> alumnos.save(nuevo).getId());
		assertNull(jdbc.queryForObject("SELECT sede_principal_id FROM alumno WHERE id = ?", Long.class, idNuevo));

		Alumno conSede = new Alumno();
		campo.set(conSede, s1);
		assertFalse(json.writeValueAsString(conSede).contains("sedePrincipal"));
	}

	// ------------------------------------------------------------------ utilidades

	private MvcResult peticion(String ruta, long usuarioId) throws Exception {
		Usuario usuario = TenantContext.calcularCon(ORG1, () -> usuarios.findById(usuarioId).orElseThrow());
		return mvc.perform(get(ruta).cookie(new Cookie("jwt", jwtService.generateToken(usuario, 600_000, orgA))))
				.andReturn();
	}

	/** Cuenta con membresía FAMILIA en A y un alumno propio vinculado (MANUAL) que abre la puerta de familia. */
	private long cuentaFamiliaConAlumnoPropio() {
		String email = "it-acceso-mixta-" + UUID.randomUUID() + "@example.test";
		long id = TenantContext.calcularCon(ORG1, () -> {
			Usuario usuario = new Usuario();
			usuario.setNombre("Usuario");
			usuario.setApellidos("Prueba");
			usuario.setEmail(email);
			usuario.setContrasena("fake-Password-123");
			usuario.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(usuario).getId();
		});
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'FAMILIA')", id, orgA);
		long propio = alumno(orgA, null);
		jdbc.update("""
				INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen)
				VALUES (?, ?, ?, 'TUTOR', 'MANUAL')""", id, propio, orgA);
		return id;
	}

	private long ultimoAlumnoVinculado(long usuarioId) {
		return jdbc.queryForObject("SELECT MAX(alumno_id) FROM usuario_alumno WHERE usuario_id = ?", Long.class,
				usuarioId);
	}

	private <T> T enA(Supplier<T> accion) {
		return TenantContext.calcularCon(orgA, accion);
	}

	private Set<Long> idsAlumnos(AlcanceSesion alcance) {
		List<Alumno> lista = alumnos.findAll(PredicadosAlcance.alumnos(alcance));
		Set<Long> ids = ids(lista, Alumno::getId);
		assertEquals(lista.size(), ids.size(), "sin filas repetidas");
		return ids;
	}

	private static <E, I> Set<I> ids(List<E> lista, Function<E, I> id) {
		Set<I> ids = new HashSet<>();
		lista.forEach(e -> ids.add(id.apply(e)));
		return ids;
	}

	private long organizacion(String prefijo) {
		String slug = prefijo + "-" + UUID.randomUUID().toString().substring(0, 8);
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES (?, 'Escuela IT')", slug);
		long org = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = ?", Long.class, slug);
		jdbc.update("""
				INSERT INTO disciplina (organizacion_id, codigo, nombre, sistema_grados_id, edad_adulto, orden)
				VALUES (?, 'TAEKWONDO', 'Taekwondo IT', NULL, 14, 1)""", org);
		return org;
	}

	private long sede(long org) {
		String nombre = "IT sede " + UUID.randomUUID().toString().substring(0, 8);
		jdbc.update("INSERT INTO sede (organizacion_id, nombre, activa) VALUES (?, ?, TRUE)", org, nombre);
		return jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = ? AND nombre = ?", Long.class, org,
				nombre);
	}

	/** Con una sola sede activa, el grupo nuevo queda en ella. */
	private long grupo(long org, String nombre) {
		GrupoConAlumnosDTO dto = new GrupoConAlumnosDTO();
		dto.setNombre(nombre);
		dto.setDeporte("TAEKWONDO");
		return TenantContext.calcularCon(org, () -> grupoService.crearGrupo(dto).getId());
	}

	private long alumno(long org, Long sedePrincipal) {
		long expediente = jdbc.queryForObject("SELECT COALESCE(MAX(numero_expediente), 80000) + 1 FROM alumno",
				Long.class);
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id, sede_principal_id)
				VALUES (1, 'Prueba', 1, 'Calle 1', '2015-01-01', 'Alumno acceso IT', ?, '600000000', 0, ?, ?)""",
				expediente, org, sedePrincipal);
		return jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = ?",
				Long.class, expediente, org);
	}

	private Alumno alumnoEntidad() {
		Alumno alumno = new Alumno();
		alumno.setNombre("Alumno acceso IT nuevo");
		alumno.setApellidos("Prueba");
		alumno.setNumeroExpediente(jdbc.queryForObject(
				"SELECT COALESCE(MAX(numero_expediente), 80000) + 1 FROM alumno", Integer.class));
		alumno.setFechaNacimiento(java.sql.Date.valueOf("2015-01-01"));
		alumno.setDireccion("Calle 1");
		alumno.setTelefono("600000000");
		alumno.setActivo(true);
		alumno.setAutorizacionWeb(true);
		return alumno;
	}

	private void miembro(long grupo, long alumno) {
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", grupo, alumno);
	}

	private long turno(long org, Long grupo) {
		jdbc.update("""
				INSERT INTO turno (dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id)
				VALUES ('Lunes', '18:00', '19:00', NULL, ?, ?)""", grupo, org);
		return jdbc.queryForObject("SELECT MAX(id) FROM turno WHERE organizacion_id = ?", Long.class, org);
	}

	/** La plantilla es de la escuela 1 (FK simple): solo importa el grupo y la escuela de la fila. */
	private String preinscripcion(long org, Long grupo) {
		long plantilla = jdbc.queryForObject("""
				SELECT p.id FROM plantilla_preinscripcion p JOIN disciplina d ON d.id = p.disciplina_id
				WHERE p.organizacion_id = ? AND d.codigo = 'TAEKWONDO' ORDER BY p.id LIMIT 1""", Long.class, ORG1);
		long disciplina = jdbc.queryForObject(
				"SELECT id FROM disciplina WHERE organizacion_id = ? AND codigo = 'TAEKWONDO'", Long.class, org);
		String referencia = "IT-ACC-" + UUID.randomUUID().toString().substring(0, 12);
		jdbc.update("""
				INSERT INTO preinscripcion (referencia, temporada, estado, nombre, apellidos, dni, identidad_hash,
				  fecha_nacimiento, direccion, telefono, email, consentimiento_fotografico, aceptacion_normas,
				  firmante_nombre, firma, plantilla_id, plantilla_snapshot, pdf_firmado, token_documento_hash,
				  creada_en, actualizada_en, grupo_id, organizacion_id, disciplina_id)
				VALUES (?, '2025-2026', 'PENDIENTE', 'Ana', 'Prueba', NULL, ?, '2012-05-05', 'Calle 1',
				  '600000000', 'ana@example.test', 0, 1, 'Firmante Prueba', x'00', ?, '{}', x'00', ?,
				  '2025-09-10 12:00:00', '2025-09-10 12:00:00', ?, ?, ?)
				""", referencia, UUID.randomUUID().toString().replace("-", ""), plantilla, "e".repeat(64), grupo, org,
				disciplina);
		return referencia;
	}
}
