package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Membresia;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.AlumnoRetoDiarioLogRepository;
import com.taemoi.project.repositories.MembresiaRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.PreinscripcionRepository;
import com.taemoi.project.repositories.RegistroPendienteRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.ContadorService;

import jakarta.servlet.http.Cookie;

/**
 * Borrado completo de un alumno ({@code DELETE /api/alumnos/{id}}) contra MySQL real, con todas las claves
 * ajenas que apuntan a {@code alumno}: la preinscripción de la que salió se conserva como histórico sin alumno,
 * los vínculos de familia desaparecen y nada de otra escuela se toca.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BorradoAlumnoIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String CLAVE = "Clave-Segura-123";

	/**
	 * Toda clave ajena hacia {@code alumno} tras migrar (tabla.columna → ON DELETE). Si aparece una nueva, decide
	 * cómo la trata {@code AlumnoServiceImpl#eliminarAlumno} antes de añadirla aquí.
	 */
	private static final Map<String, String> CLAVES_HACIA_ALUMNO = Map.ofEntries(
			Map.entry("alumno_convocatoria.alumno_id", "NO ACTION"),
			Map.entry("alumno_deporte.alumno_id", "NO ACTION"),
			Map.entry("alumno_grupo.alumno_id", "NO ACTION"),
			Map.entry("alumno_reto_diario_log.alumno_id", "NO ACTION"),
			Map.entry("alumno_salud.alumno_id", "CASCADE"),
			Map.entry("alumno_turno.alumno_id", "NO ACTION"),
			// Pieza 6b-1 (V31_5): instantáneas de caja; al borrar el alumno el cobro o el movimiento se conservan sin
			// alumno (y el cobro, sin cargo: fk_cobro_producto_alumno también es SET NULL). eliminarAlumno no cambia.
			Map.entry("cobro.alumno_id", "SET NULL"),
			Map.entry("movimiento_caja.alumno_id", "SET NULL"),
			Map.entry("consentimiento.alumno_id", "CASCADE"),
			Map.entry("documento.alumno_id", "NO ACTION"),
			Map.entry("preinscripcion.alumno_id", "NO ACTION"),
			Map.entry("producto_alumno.alumno_id", "NO ACTION"),
			Map.entry("registro_pendiente.alumno_id", "NO ACTION"),
			Map.entry("tutor.alumno_id", "CASCADE"),
			Map.entry("usuario.alumno_id", "NO ACTION"),
			Map.entry("usuario_alumno.alumno_id", "CASCADE"));

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired PasswordEncoder encoder;
	@Autowired UsuarioRepository usuarios;
	@Autowired MembresiaRepository membresias;
	@Autowired OrganizacionRepository organizaciones;
	@Autowired AlumnoRepository alumnos;
	@Autowired PreinscripcionRepository preinscripciones;
	@Autowired AlumnoRetoDiarioLogRepository retos;
	@Autowired RegistroPendienteRepository registrosPendientes;
	@Autowired ContadorService contadores;
	@Autowired TransactionTemplate tx;

	@MockitoBean JavaMailSender mailSender;

	final String sufijo = UUID.randomUUID().toString().substring(0, 8);
	long org2;
	long plantilla;
	long disciplina;
	String tokenAdmin;
	String tokenAdminOtra;

	@BeforeAll
	void crearEscuelasYAdministradores() throws Exception {
		org2 = TenantContext.calcularCon(ORG1, () -> {
			Organizacion org = new Organizacion();
			org.setSlug("it-borrado-" + sufijo);
			org.setNombre("Escuela borrado " + sufijo);
			return organizaciones.save(org).getId();
		});
		plantilla = jdbc.queryForObject("""
				SELECT p.id FROM plantilla_preinscripcion p JOIN disciplina d ON d.id = p.disciplina_id
				WHERE p.organizacion_id = 1 AND d.codigo = 'TAEKWONDO' ORDER BY p.id LIMIT 1""", Long.class);
		disciplina = jdbc.queryForObject(
				"SELECT id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'TAEKWONDO'", Long.class);

		Usuario admin = crearUsuario("admin-borrado-" + sufijo + "@example.test");
		dar(admin, ORG1, RolMembresia.PROPIETARIO);
		Usuario adminOtra = crearUsuario("admin-otra-borrado-" + sufijo + "@example.test");
		dar(adminOtra, org2, RolMembresia.PROPIETARIO);
		tokenAdmin = login(admin.getEmail());
		tokenAdminOtra = login(adminOtra.getEmail());
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ inventario

	@Test
	void lasClavesAjenasHaciaAlumnoSonLasInventariadas() {
		Map<String, String> reales = new HashMap<>();
		jdbc.queryForList("""
				SELECT k.TABLE_NAME AS tabla, k.COLUMN_NAME AS columna, r.DELETE_RULE AS regla
				FROM information_schema.KEY_COLUMN_USAGE k
				JOIN information_schema.REFERENTIAL_CONSTRAINTS r
				  ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME
				 AND r.TABLE_NAME = k.TABLE_NAME
				WHERE k.TABLE_SCHEMA = DATABASE() AND k.REFERENCED_TABLE_NAME = 'alumno'
				""").forEach(f -> reales.put(f.get("tabla") + "." + f.get("columna"), (String) f.get("regla")));
		assertEquals(CLAVES_HACIA_ALUMNO, reales,
				"Clave ajena nueva o cambiada hacia alumno: revisa AlumnoServiceImpl#eliminarAlumno");
	}

	// ------------------------------------------------------------------ (a) alumno creado desde preinscripción

	@Test
	void borrarUnAlumnoCreadoDesdeUnaPreinscripcionLaConservaComoHistoricoSinAlumno() throws Exception {
		long alumno = TenantContext.calcularCon(ORG1, () -> crearAlumno("DESDEPRE", null));
		long finalizada = insertarPreinscripcion(ORG1, alumno, "FINALIZADA");
		long otra = insertarPreinscripcion(ORG1, alumno, "FINALIZADA");
		Map<String, Object> antes = filaSinAlumno(finalizada);

		mvc.perform(delete("/api/alumnos/" + alumno).cookie(jwt(tokenAdmin)))
				.andExpect(status().isOk())
				.andExpect(content().string(""));

		assertEquals(0, contar("SELECT COUNT(*) FROM alumno WHERE id = ?", alumno));
		for (long id : List.of(finalizada, otra)) {
			assertEquals(1, contar("SELECT COUNT(*) FROM preinscripcion WHERE id = ?", id), "la preinscripción se conserva");
			assertNull(jdbc.queryForObject("SELECT alumno_id FROM preinscripcion WHERE id = ?", Long.class, id));
			assertEquals("FINALIZADA", jdbc.queryForObject("SELECT estado FROM preinscripcion WHERE id = ?", String.class, id));
		}
		assertEquals(antes, filaSinAlumno(finalizada), "solo cambia alumno_id: estado, fechas y datos intactos");
	}

	@Test
	void borrarUnAlumnoConRetoDiarioYRegistroPendienteLosQuita() throws Exception {
		long alumno = TenantContext.calcularCon(ORG1, () -> crearAlumno("RETO", null));
		insertarRetoDiario(ORG1, alumno);
		insertarRegistroPendiente(ORG1, alumno);

		mvc.perform(delete("/api/alumnos/" + alumno).cookie(jwt(tokenAdmin))).andExpect(status().isOk());

		assertEquals(0, contar("SELECT COUNT(*) FROM alumno WHERE id = ?", alumno));
		assertEquals(0, contar("SELECT COUNT(*) FROM alumno_reto_diario_log WHERE alumno_id = ?", alumno));
		assertEquals(0, contar("SELECT COUNT(*) FROM registro_pendiente WHERE alumno_id = ?", alumno));
	}

	// ------------------------------------------------------------------ (b) vínculos de familia

	@Test
	void borrarUnAlumnoConVinculosDeFamiliaQuitaLosVinculosYConservaLasCuentas() throws Exception {
		long alumno = TenantContext.calcularCon(ORG1, () -> crearAlumno("FAMILIA", null));
		long hermano = TenantContext.calcularCon(ORG1, () -> crearAlumno("HERMANO", null));
		// Cuenta con el vínculo heredado (usuario.alumno_id), familia aquí y en otra escuela: sobrevive.
		Usuario heredado = crearUsuario("heredado-" + sufijo + "@example.test");
		dar(heredado, ORG1, RolMembresia.FAMILIA);
		dar(heredado, org2, RolMembresia.FAMILIA);
		jdbc.update("UPDATE usuario SET alumno_id = ? WHERE id = ?", alumno, heredado.getId());
		vincular(heredado.getId(), alumno, "PROPIO");
		// Cuenta tutora vinculada solo por usuario_alumno, también a otro alumno.
		Usuario tutora = crearUsuario("tutora-" + sufijo + "@example.test");
		dar(tutora, ORG1, RolMembresia.FAMILIA);
		vincular(tutora.getId(), alumno, "TUTOR");
		vincular(tutora.getId(), hermano, "TUTOR");
		insertarPreinscripcion(ORG1, alumno, "FINALIZADA");

		mvc.perform(delete("/api/alumnos/" + alumno).cookie(jwt(tokenAdmin))).andExpect(status().isOk());

		assertEquals(0, contar("SELECT COUNT(*) FROM alumno WHERE id = ?", alumno));
		assertEquals(0, contar("SELECT COUNT(*) FROM usuario_alumno WHERE alumno_id = ?", alumno));
		assertEquals(1, contar("SELECT COUNT(*) FROM usuario_alumno WHERE usuario_id = ? AND alumno_id = ?",
				tutora.getId(), hermano), "el vínculo con el otro alumno sigue");
		assertEquals(1, contar("SELECT COUNT(*) FROM usuario WHERE id = ?", heredado.getId()));
		assertNull(jdbc.queryForObject("SELECT alumno_id FROM usuario WHERE id = ?", Long.class, heredado.getId()));
		assertEquals(0, contar("SELECT COUNT(*) FROM membresia WHERE usuario_id = ? AND organizacion_id = ?",
				heredado.getId(), ORG1), "se retira su acceso de familia a esta escuela (como antes)");
		assertEquals(1, contar("SELECT COUNT(*) FROM membresia WHERE usuario_id = ? AND organizacion_id = ?",
				heredado.getId(), org2));
		assertEquals(1, contar("SELECT COUNT(*) FROM usuario WHERE id = ?", tutora.getId()));
	}

	// ------------------------------------------------------------------ (c) alumno sin nada de lo anterior

	@Test
	void borrarUnAlumnoSinVinculosSigueIgual() throws Exception {
		long alumno = TenantContext.calcularCon(ORG1, () -> crearAlumno("SOLO", null));

		mvc.perform(delete("/api/alumnos/" + alumno).cookie(jwt(tokenAdmin)))
				.andExpect(status().isOk())
				.andExpect(content().string(""));
		assertEquals(0, contar("SELECT COUNT(*) FROM alumno WHERE id = ?", alumno));

		mvc.perform(delete("/api/alumnos/" + alumno).cookie(jwt(tokenAdmin))).andExpect(status().isNotFound());
	}

	// ------------------------------------------------------------------ (d) aislamiento por escuela

	@Test
	void otraEscuelaNoBorraNiDesvinculaLoAjeno() throws Exception {
		long alumno = TenantContext.calcularCon(ORG1, () -> crearAlumno("AJENO", null));
		long pre = insertarPreinscripcion(ORG1, alumno, "FINALIZADA");
		insertarRetoDiario(ORG1, alumno);
		insertarRegistroPendiente(ORG1, alumno);

		mvc.perform(delete("/api/alumnos/" + alumno).cookie(jwt(tokenAdminOtra))).andExpect(status().isNotFound());
		assertEquals(1, contar("SELECT COUNT(*) FROM alumno WHERE id = ?", alumno));
		assertEquals(alumno, jdbc.queryForObject("SELECT alumno_id FROM preinscripcion WHERE id = ?", Long.class, pre));

		// Las sentencias del borrado, lanzadas desde la otra escuela con el id ajeno, no tocan nada.
		int[] tocadas = TenantContext.calcularCon(org2, () -> tx.execute(estado -> new int[] {
				preinscripciones.desvincularAlumno(alumno),
				retos.borrarDelAlumno(alumno),
				registrosPendientes.borrarDelAlumno(alumno) }));
		assertEquals(List.of(0, 0, 0), List.of(tocadas[0], tocadas[1], tocadas[2]));
		assertEquals(alumno, jdbc.queryForObject("SELECT alumno_id FROM preinscripcion WHERE id = ?", Long.class, pre));
		assertEquals(1, contar("SELECT COUNT(*) FROM alumno_reto_diario_log WHERE alumno_id = ?", alumno));
		assertEquals(1, contar("SELECT COUNT(*) FROM registro_pendiente WHERE alumno_id = ?", alumno));

		// Y la escuela dueña sí puede borrarlo.
		mvc.perform(delete("/api/alumnos/" + alumno).cookie(jwt(tokenAdmin))).andExpect(status().isOk());
		assertNull(jdbc.queryForObject("SELECT alumno_id FROM preinscripcion WHERE id = ?", Long.class, pre));
	}

	// ------------------------------------------------------------------ utilidades

	private long insertarPreinscripcion(long organizacion, long alumno, String estado) {
		String referencia = "IT-BOR-" + UUID.randomUUID().toString().substring(0, 12);
		jdbc.update("""
				INSERT INTO preinscripcion (referencia, temporada, estado, nombre, apellidos, dni, identidad_hash,
				  fecha_nacimiento, direccion, telefono, email, consentimiento_fotografico, aceptacion_normas,
				  firmante_nombre, firma, plantilla_id, plantilla_snapshot, pdf_firmado, token_documento_hash,
				  creada_en, actualizada_en, finalizada_en, alumno_id, organizacion_id, disciplina_id)
				VALUES (?, '2025-2026', ?, 'Ana', 'Prueba', NULL, ?, '2012-05-05', 'Calle 1',
				  '600000000', 'ana@example.test', 0, 1, 'Madre Firmante', x'00', ?, '{}', x'00', ?,
				  '2025-09-10 12:00:00', '2025-09-10 12:00:00', '2025-09-11 10:00:00', ?, ?, ?)
				""", referencia, estado, UUID.randomUUID().toString().replace("-", ""), plantilla, "e".repeat(64),
				alumno, organizacion, disciplina);
		return jdbc.queryForObject("SELECT id FROM preinscripcion WHERE referencia = ?", Long.class, referencia);
	}

	private Map<String, Object> filaSinAlumno(long preinscripcion) {
		Map<String, Object> fila = new HashMap<>(
				jdbc.queryForMap("SELECT * FROM preinscripcion WHERE id = ?", preinscripcion));
		fila.remove("alumno_id");
		// Los BLOB llegan como byte[] (igualdad por identidad): se comparan por contenido.
		fila.replaceAll((k, v) -> v instanceof byte[] b ? java.util.HexFormat.of().formatHex(b) : v);
		return fila;
	}

	private void insertarRetoDiario(long organizacion, long alumno) {
		jdbc.update("""
				INSERT INTO alumno_reto_diario_log (alumno_id, fecha_completado, anio_iso, semana_iso, created_at,
				  organizacion_id) VALUES (?, '2025-10-01', 2025, 40, '2025-10-01 18:00:00', ?)""", alumno, organizacion);
	}

	private void insertarRegistroPendiente(long organizacion, long alumno) {
		jdbc.update("""
				INSERT INTO registro_pendiente (email, alumno_id, password_hash, token_hash, token_expires_at,
				  organizacion_id) VALUES (?, ?, 'hash', ?, '2030-01-01 00:00:00', ?)""",
				"pendiente-" + alumno + "-" + sufijo + "@example.test", alumno, "d".repeat(64), organizacion);
	}

	private void vincular(long usuario, long alumno, String relacion) {
		jdbc.update("""
				INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen)
				VALUES (?, ?, 1, ?, 'MANUAL')""", usuario, alumno, relacion);
	}

	private int contar(String sql, Object... args) {
		return jdbc.queryForObject(sql, Integer.class, args);
	}

	private String login(String email) throws Exception {
		MvcResult resultado = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return json.readTree(resultado.getResponse().getContentAsString()).get("token").asText();
	}

	private static Cookie jwt(String token) {
		return new Cookie("jwt", token);
	}

	private Usuario crearUsuario(String email) {
		return TenantContext.calcularCon(ORG1, () -> {
			Usuario usuario = new Usuario();
			usuario.setNombre("Usuario");
			usuario.setApellidos("Prueba");
			usuario.setEmail(email);
			usuario.setContrasena(encoder.encode(CLAVE));
			usuario.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(usuario);
		});
	}

	private Membresia dar(Usuario usuario, Long organizacionId, RolMembresia rol) {
		return TenantContext.calcularCon(ORG1,
				() -> membresias.save(new Membresia(usuario.getId(), organizacionId, rol)));
	}

	private long crearAlumno(String nombre, String email) {
		Alumno alumno = new Alumno();
		alumno.setNombre(nombre + " " + sufijo);
		alumno.setApellidos("PRUEBA BORRADO");
		alumno.setNumeroExpediente(contadores.siguienteNumeroExpediente());
		alumno.setFechaNacimiento(Date.valueOf(LocalDate.of(2012, 5, 5)));
		alumno.setDireccion("Calle Prueba 1");
		alumno.setTelefono("600000000");
		alumno.setActivo(true);
		alumno.setAutorizacionWeb(true);
		alumno.setEmail(email != null ? email : nombre.toLowerCase() + "-" + sufijo + "@example.test");
		return alumnos.save(alumno).getId();
	}
}
