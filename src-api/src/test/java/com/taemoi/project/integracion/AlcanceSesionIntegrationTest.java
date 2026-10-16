package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

import com.taemoi.project.dtos.response.GrupoConAlumnosDTO;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.AlcanceSesion;
import com.taemoi.project.organizacion.IdentificadorSesion;
import com.taemoi.project.organizacion.SesionVerificada;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlcanceRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Entrega 5b-2 contra MySQL: la consulta nativa de {@link AlcanceRepository} (entrega5-plan.md §3.3) y el alcance
 * que deja {@code IdentificadorSesionJwt} en la sesión. {@code membresia_sede} y {@code asignacion_profesor} no
 * son {@code @TenantId}: una sede o un grupo de OTRA escuela insertados a mano no aparecen. Datos sintéticos.
 */
@SpringBootTest
class AlcanceSesionIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;

	@Autowired AlcanceRepository alcances;
	@Autowired IdentificadorSesion identificador;
	@Autowired JwtService jwtService;
	@Autowired GrupoService grupoService;
	@Autowired UsuarioRepository usuarios;
	@Autowired JdbcTemplate jdbc;

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	@Test
	void responsable_soloSedesDeSuEscuelaYDeSuMembresiaActiva() {
		Escuela a = escuela("it-alc-a");
		Escuela b = escuela("it-alc-b");
		long u = crearUsuario();
		long resp = membresia(u, a.org, "RESPONSABLE_SEDE");
		long familia = membresia(u, a.org, "FAMILIA");
		long sedeInactivaA = sede(a.org, false);
		jdbc.update("INSERT INTO membresia_sede (membresia_id, sede_id) VALUES (?, ?), (?, ?), (?, ?)",
				resp, a.sede, resp, b.sede, resp, sedeInactivaA);
		// Filas de membresías que no son RESPONSABLE_SEDE: no dan sede.
		jdbc.update("INSERT INTO membresia_sede (membresia_id, sede_id) VALUES (?, ?)", familia, a.sede);

		assertEquals(AlcanceSesion.limitado(Set.of(a.sede, sedeInactivaA), Set.of()), alcance(u, a.org),
				"la sede de otra escuela no aparece; la sede inactiva sí (D5-5)");
		assertEquals(AlcanceSesion.limitado(Set.of(), Set.of()), alcance(u, b.org), "organización filtrada");

		jdbc.update("UPDATE membresia SET activa = FALSE WHERE id = ?", resp);
		assertEquals(AlcanceSesion.limitado(Set.of(), Set.of()), alcance(u, a.org), "membresía inactiva no cuenta");
	}

	@Test
	void profesor_soloGruposDeSuEscuelaYConMembresiaProfesorActiva() {
		Escuela a = escuela("it-alc-pa");
		Escuela b = escuela("it-alc-pb");
		long u = crearUsuario();
		long profA = membresia(u, a.org, "PROFESOR");
		long otraEscuelaA = grupo(b.org, "IT grupo B en fila de A");
		// Fila de A con un grupo de B (a mano: solo FK simples) y fila de B sin membresía PROFESOR en B.
		jdbc.update("""
				INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol)
				VALUES (?, ?, ?, 'TITULAR'), (?, ?, ?, 'TITULAR'), (?, ?, ?, 'AYUDANTE')""",
				a.org, u, a.grupo, a.org, u, otraEscuelaA, b.org, u, b.grupo);
		// Una membresia_sede del profesor no le da sede: las sedes solo vienen de RESPONSABLE_SEDE.
		jdbc.update("INSERT INTO membresia_sede (membresia_id, sede_id) VALUES (?, ?)", profA, a.sede);

		assertEquals(AlcanceSesion.limitado(Set.of(), Set.of(a.grupo)), alcance(u, a.org),
				"el grupo de otra escuela no aparece aunque la fila sea de A");
		assertEquals(AlcanceSesion.limitado(Set.of(), Set.of()), alcance(u, b.org),
				"sin membresía PROFESOR en B, su fila de B no cuenta");

		membresia(u, b.org, "PROFESOR");
		assertEquals(AlcanceSesion.limitado(Set.of(), Set.of(b.grupo)), alcance(u, b.org));
		assertEquals(AlcanceSesion.limitado(Set.of(), Set.of(a.grupo)), alcance(u, a.org), "A no cambia");

		jdbc.update("UPDATE membresia SET activa = FALSE WHERE id = ?", profA);
		assertEquals(AlcanceSesion.limitado(Set.of(), Set.of()), alcance(u, a.org), "membresía inactiva no cuenta");
	}

	@Test
	void consultaNoDependeDeLaEscuelaFijadaEnElHilo() {
		Escuela a = escuela("it-alc-hilo");
		Escuela b = escuela("it-alc-hilo-b");
		long u = crearUsuario();
		long resp = membresia(u, a.org, "RESPONSABLE_SEDE");
		jdbc.update("INSERT INTO membresia_sede (membresia_id, sede_id) VALUES (?, ?)", resp, a.sede);

		AlcanceSesion conOtraEscuela = TenantContext.calcularCon(b.org,
				() -> AlcanceSesion.desde(alcances.filasDeAlcance(u, a.org)));

		assertEquals(AlcanceSesion.limitado(Set.of(a.sede), Set.of()), conOtraEscuela);
	}

	@Test
	void identificarDejaElAlcanceEnLaSesion() {
		Escuela a = escuela("it-alc-sesion");
		long gestor = crearUsuario();
		membresia(gestor, a.org, "PROPIETARIO");
		membresia(gestor, a.org, "PROFESOR");
		jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) VALUES (?, ?, ?, 'TITULAR')",
				a.org, gestor, a.grupo);
		assertSame(AlcanceSesion.TOTAL, sesion(gestor, a.org).alcance());

		long profesorFamilia = crearUsuario();
		membresia(profesorFamilia, a.org, "FAMILIA");
		membresia(profesorFamilia, a.org, "PROFESOR");
		jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) VALUES (?, ?, ?, 'TITULAR')",
				a.org, profesorFamilia, a.grupo);
		vincularAlumno(profesorFamilia, a.org);
		assertEquals(AlcanceSesion.limitado(Set.of(), Set.of(a.grupo)), sesion(profesorFamilia, a.org).alcance());

		long familia = crearUsuario();
		membresia(familia, a.org, "FAMILIA");
		vincularAlumno(familia, a.org);
		assertSame(AlcanceSesion.NINGUNO, sesion(familia, a.org).alcance());
	}

	// ---------------------------------------------------------------------------------------------

	private record Escuela(long org, long sede, long grupo) {
	}

	private AlcanceSesion alcance(long usuarioId, long organizacionId) {
		// Como en verificar: con la organización por defecto fijada.
		return TenantContext.calcularCon(ORG1,
				() -> AlcanceSesion.desde(alcances.filasDeAlcance(usuarioId, organizacionId)));
	}

	private SesionVerificada sesion(long usuarioId, long organizacionId) {
		Usuario usuario = TenantContext.calcularCon(ORG1, () -> usuarios.findById(usuarioId).orElseThrow());
		MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/auth/user");
		peticion.setCookies(new Cookie("jwt", jwtService.generateToken(usuario, 600_000, organizacionId)));
		return TenantContext.calcularCon(ORG1, () -> identificador.identificar(peticion)).orElseThrow();
	}

	/** Escuela nueva con una sede y un grupo en ella. */
	private Escuela escuela(String prefijo) {
		String slug = prefijo + "-" + UUID.randomUUID().toString().substring(0, 8);
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES (?, 'Escuela IT')", slug);
		long org = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = ?", Long.class, slug);
		jdbc.update("""
				INSERT INTO disciplina (organizacion_id, codigo, nombre, sistema_grados_id, edad_adulto, orden)
				VALUES (?, 'TAEKWONDO', 'Taekwondo IT', NULL, 14, 1)""", org);
		long sede = sede(org, true);
		long grupo = grupo(org, "IT grupo " + slug);
		return new Escuela(org, sede, grupo);
	}

	private long sede(long org, boolean activa) {
		String nombre = "IT sede " + UUID.randomUUID().toString().substring(0, 8);
		jdbc.update("INSERT INTO sede (organizacion_id, nombre, activa) VALUES (?, ?, ?)", org, nombre, activa);
		return jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = ? AND nombre = ?", Long.class, org,
				nombre);
	}

	private long grupo(long org, String nombre) {
		GrupoConAlumnosDTO dto = new GrupoConAlumnosDTO();
		dto.setNombre(nombre);
		dto.setDeporte("TAEKWONDO");
		return TenantContext.calcularCon(org, () -> grupoService.crearGrupo(dto).getId());
	}

	private long membresia(long usuarioId, long org, String rol) {
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", usuarioId, org, rol);
		return jdbc.queryForObject("SELECT id FROM membresia WHERE usuario_id = ? AND organizacion_id = ? AND rol = ?",
				Long.class, usuarioId, org, rol);
	}

	/** Alumno activo de la escuela con un vínculo MANUAL a la cuenta: abre la puerta de familia. */
	private void vincularAlumno(long usuarioId, long org) {
		long expediente = jdbc.queryForObject("SELECT COALESCE(MAX(numero_expediente), 80000) + 1 FROM alumno",
				Long.class);
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id, email)
				VALUES (1, 'Prueba', 1, 'Calle 1', '2015-01-01', 'Alumno IT', ?, '600000000', 0, ?, 'otro@example.test')""",
				expediente, org);
		long alumno = jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = ?",
				Long.class, expediente, org);
		jdbc.update("""
				INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen)
				VALUES (?, ?, ?, 'TUTOR', 'MANUAL')""", usuarioId, alumno, org);
	}

	private long crearUsuario() {
		String email = "it-alcance-" + UUID.randomUUID() + "@example.test";
		return TenantContext.calcularCon(ORG1, () -> {
			Usuario usuario = new Usuario();
			usuario.setNombre("Usuario");
			usuario.setApellidos("Prueba");
			usuario.setEmail(email);
			usuario.setContrasena("fake-Password-123");
			usuario.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(usuario).getId();
		});
	}
}
