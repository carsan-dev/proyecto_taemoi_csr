package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.ContadorService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Escrituras sobre las tablas intermedias {@code alumno_grupo} y {@code alumno_turno} (R2, pieza P1): el efecto en
 * la base de datos de asignar/quitar un turno y de añadir/quitar un alumno de un grupo, para un alumno en UN grupo y
 * para uno en DOS grupos.
 *
 * <p>Con el ciclo EAGER Alumno–Grupo–Turno, la bolsa {@code Alumno.turnos} de un alumno de dos grupos se cargaba con
 * cada turno repetido (H2 del informe R2: el JOIN de {@code alumno_turno} en la carga por lotes de
 * {@code Grupo.alumnos} repetía la fila del alumno por grupo). Al modificar una bolsa, Hibernate borra todas sus filas
 * y las vuelve a insertar: con los duplicados en memoria chocaba con la clave única
 * {@code uk_alumno_turno_alumno_turno} (V11) y la petición acababa en 500. Esta clase lo fija: tras pasar las
 * colecciones a LAZY, todas las escrituras del alumno de dos grupos terminan bien y dejan exactamente las filas
 * esperadas; las del alumno de un grupo, igual que antes.
 *
 * <p>Datos sintéticos en la organización 1, nuevos en cada test (marca aleatoria); se retiran al terminar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AsignacionesGrupoTurnoIntegrationTest extends MySqlIntegrationTest {

	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired ContadorService contadores;

	@MockitoBean JavaMailSender mailSender;

	final String marca = UUID.randomUUID().toString().substring(0, 8);
	final List<Long> gruposCreados = new ArrayList<>();
	final List<Long> alumnosCreados = new ArrayList<>();
	long sede;
	long tkd;
	Long gestor;
	String token;

	// Escenario de cada test: G1 (t1a, t1b), G2 (t2a), G3 (t3a, t3b).
	// dosGrupos: G1 + G2, turnos t1a, t1b, t2a. unGrupo: G1, turno t1a.
	long g1;
	long g2;
	long g3;
	long t1a;
	long t1b;
	long t2a;
	long t3a;
	long t3b;
	long dosGrupos;
	long unGrupo;

	@BeforeAll
	void prepararCuenta() {
		sede = jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = 1 ORDER BY id LIMIT 1", Long.class);
		tkd = jdbc.queryForObject("SELECT id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'TAEKWONDO'",
				Long.class);
		Usuario usuario = TenantContext.calcularCon(1L, () -> {
			Usuario u = new Usuario();
			u.setNombre("Gestor");
			u.setApellidos("Asignaciones P1");
			u.setEmail("it-asignaciones-" + marca + "@example.test");
			u.setContrasena("fake-Password-123");
			u.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(u);
		});
		gestor = usuario.getId();
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, 1, 'PROPIETARIO')", gestor);
		token = jwtService.generateToken(usuario, 3_600_000, 1L);
	}

	@BeforeEach
	void sembrar() {
		g1 = grupo();
		g2 = grupo();
		g3 = grupo();
		t1a = turno(g1, "Lunes");
		t1b = turno(g1, "Miércoles");
		t2a = turno(g2, "Martes");
		t3a = turno(g3, "Jueves");
		t3b = turno(g3, "Viernes");
		dosGrupos = alumno();
		unGrupo = alumno();
		apuntarGrupo(dosGrupos, g1);
		apuntarGrupo(dosGrupos, g2);
		apuntarTurno(dosGrupos, t1a);
		apuntarTurno(dosGrupos, t1b);
		apuntarTurno(dosGrupos, t2a);
		apuntarGrupo(unGrupo, g1);
		apuntarTurno(unGrupo, t1a);
	}

	@AfterAll
	void retirar() {
		for (Long g : gruposCreados) {
			jdbc.update("DELETE FROM alumno_turno WHERE turno_id IN (SELECT id FROM turno WHERE grupo_id = ?)", g);
			jdbc.update("DELETE FROM alumno_grupo WHERE grupo_id = ?", g);
			jdbc.update("DELETE FROM turno WHERE grupo_id = ?", g);
			jdbc.update("DELETE FROM grupo WHERE id = ?", g);
		}
		for (Long a : alumnosCreados) {
			jdbc.update("DELETE FROM alumno_turno WHERE alumno_id = ?", a);
			jdbc.update("DELETE FROM alumno_grupo WHERE alumno_id = ?", a);
			jdbc.update("DELETE FROM alumno_deporte WHERE alumno_id = ?", a);
			jdbc.update("DELETE FROM alumno WHERE id = ?", a);
		}
		if (gestor != null) {
			jdbc.update("UPDATE membresia SET activa = FALSE WHERE usuario_id = ?", gestor);
		}
	}

	// ------------------------------------------------------------------ asignar turno (POST /api/alumnos/{id}/turnos/{t})

	/** La hipótesis de H2: antes de P1 respondía 500 por la clave única de alumno_turno (V11). */
	@Test
	void asignarTurno_alumnoDeDosGrupos_noChocaConLaClaveUnica() throws Exception {
		assertEquals(200, estado(post("/api/alumnos/" + dosGrupos + "/turnos/" + t3a)));
		assertEquals(List.of(t1a, t1b, t2a, t3a), turnosDe(dosGrupos));
		assertEquals(List.of(g1, g2, g3), gruposDe(dosGrupos));
	}

	@Test
	void asignarTurno_alumnoDeUnGrupo() throws Exception {
		assertEquals(200, estado(post("/api/alumnos/" + unGrupo + "/turnos/" + t3a)));
		assertEquals(List.of(t1a, t3a), turnosDe(unGrupo));
		assertEquals(List.of(g1, g3), gruposDe(unGrupo));
	}

	@Test
	void asignarTurnoQueYaTiene_alumnoDeDosGrupos_noCambiaNada() throws Exception {
		assertEquals(200, estado(post("/api/alumnos/" + dosGrupos + "/turnos/" + t1b)));
		assertEquals(List.of(t1a, t1b, t2a), turnosDe(dosGrupos));
		assertEquals(List.of(g1, g2), gruposDe(dosGrupos));
	}

	// ------------------------------------------------------------------ quitar turno (DELETE /api/alumnos/{id}/turnos/{t})

	@Test
	void quitarTurno_alumnoDeDosGrupos_conservaElGrupoSiLeQuedanTurnos() throws Exception {
		assertEquals(200, estado(delete("/api/alumnos/" + dosGrupos + "/turnos/" + t1a)));
		assertEquals(List.of(t1b, t2a), turnosDe(dosGrupos));
		assertEquals(List.of(g1, g2), gruposDe(dosGrupos));
	}

	@Test
	void quitarUltimoTurnoDelGrupo_alumnoDeDosGrupos_saleDelGrupo() throws Exception {
		assertEquals(200, estado(delete("/api/alumnos/" + dosGrupos + "/turnos/" + t2a)));
		assertEquals(List.of(t1a, t1b), turnosDe(dosGrupos));
		assertEquals(List.of(g1), gruposDe(dosGrupos));
	}

	@Test
	void quitarUltimoTurno_alumnoDeUnGrupo_saleDelGrupo() throws Exception {
		assertEquals(200, estado(delete("/api/alumnos/" + unGrupo + "/turnos/" + t1a)));
		assertEquals(List.of(), turnosDe(unGrupo));
		assertEquals(List.of(), gruposDe(unGrupo));
	}

	// ------------------------------------------------------------------ grupos (POST/DELETE /api/grupos/{g}/alumnos/{id})

	@Test
	void agregarAGrupo_alumnoDeDosGrupos_entraEnTodosSusTurnos() throws Exception {
		assertEquals(200, estado(post("/api/grupos/" + g3 + "/alumnos/" + dosGrupos)));
		assertEquals(List.of(t1a, t1b, t2a, t3a, t3b), turnosDe(dosGrupos));
		assertEquals(List.of(g1, g2, g3), gruposDe(dosGrupos));
	}

	@Test
	void agregarAGrupo_alumnoDeUnGrupo_entraEnTodosSusTurnos() throws Exception {
		assertEquals(200, estado(post("/api/grupos/" + g3 + "/alumnos/" + unGrupo)));
		assertEquals(List.of(t1a, t3a, t3b), turnosDe(unGrupo));
		assertEquals(List.of(g1, g3), gruposDe(unGrupo));
	}

	@Test
	void agregarVariosAGrupo_completaLosTurnosQueFaltan() throws Exception {
		// unGrupo ya tiene t1a: solo se añade t1b; dosGrupos ya está en G1: no cambia.
		jdbc.update("DELETE FROM alumno_grupo WHERE alumno_id = ? AND grupo_id = ?", unGrupo, g1);
		assertEquals(200, estado(post("/api/grupos/" + g1 + "/alumnos").contentType("application/json")
				.content("[" + unGrupo + "," + dosGrupos + "]")));
		assertEquals(List.of(t1a, t1b), turnosDe(unGrupo));
		assertEquals(List.of(g1), gruposDe(unGrupo));
		assertEquals(List.of(t1a, t1b, t2a), turnosDe(dosGrupos));
		assertEquals(List.of(g1, g2), gruposDe(dosGrupos));
	}

	@Test
	void eliminarDeGrupo_alumnoDeDosGrupos_conservaElOtroGrupoYSusTurnos() throws Exception {
		assertEquals(204, estado(delete("/api/grupos/" + g1 + "/alumnos/" + dosGrupos)));
		assertEquals(List.of(t2a), turnosDe(dosGrupos));
		assertEquals(List.of(g2), gruposDe(dosGrupos));
		// El otro alumno del grupo no se toca.
		assertEquals(List.of(t1a), turnosDe(unGrupo));
		assertEquals(List.of(g1), gruposDe(unGrupo));
	}

	@Test
	void eliminarDeGrupo_alumnoDeUnGrupo() throws Exception {
		assertEquals(204, estado(delete("/api/grupos/" + g1 + "/alumnos/" + unGrupo)));
		assertEquals(List.of(), turnosDe(unGrupo));
		assertEquals(List.of(), gruposDe(unGrupo));
		assertEquals(List.of(t1a, t1b, t2a), turnosDe(dosGrupos));
	}

	// ------------------------------------------------------------------ utilidades

	/** Estado HTTP; una excepción no controlada (MockMvc la relanza) cuenta como 500, con su causa en el mensaje. */
	private int estado(MockHttpServletRequestBuilder peticion) throws Exception {
		try {
			MvcResult r = mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
			if (r.getResponse().getStatus() >= 300) {
				System.err.println("Asignaciones P1: " + r.getResponse().getStatus() + " "
						+ r.getResponse().getContentAsString(StandardCharsets.UTF_8));
			}
			return r.getResponse().getStatus();
		} catch (Exception e) {
			System.err.println("Asignaciones P1: excepción no controlada: " + e);
			return 500;
		}
	}

	private List<Long> turnosDe(long alumno) {
		return jdbc.queryForList("SELECT turno_id FROM alumno_turno WHERE alumno_id = ? ORDER BY turno_id", Long.class,
				alumno);
	}

	private List<Long> gruposDe(long alumno) {
		return jdbc.queryForList("SELECT grupo_id FROM alumno_grupo WHERE alumno_id = ? ORDER BY grupo_id", Long.class,
				alumno);
	}

	private long grupo() {
		String nombre = "IT asignaciones P1 " + marca + " " + gruposCreados.size();
		jdbc.update("""
				INSERT INTO grupo (nombre, tipo, rango_edad_min, rango_edad_max, organizacion_id, disciplina_id, sede_id)
				VALUES (?, NULL, NULL, NULL, 1, ?, ?)""", nombre, tkd, sede);
		long id = jdbc.queryForObject("SELECT id FROM grupo WHERE nombre = ? AND organizacion_id = 1", Long.class,
				nombre);
		gruposCreados.add(id);
		return id;
	}

	private long turno(long grupoId, String dia) {
		jdbc.update("""
				INSERT INTO turno (dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id)
				VALUES (?, '18:00', '19:00', NULL, ?, 1)""", dia, grupoId);
		return jdbc.queryForObject("SELECT MAX(id) FROM turno WHERE grupo_id = ?", Long.class, grupoId);
	}

	private long alumno() {
		long expediente = TenantContext.calcularCon(1L, () -> contadores.siguienteNumeroExpediente());
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre, email,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id, sede_principal_id)
				VALUES (TRUE, 'Sintetico P1', 1, 'Calle Prueba 1', '2012-03-04', ?, ?, ?, '600000001', 0, 1, ?)""",
				"IT ASIGNACIONES " + marca + " " + alumnosCreados.size(),
				"it-asignaciones-" + marca + "-" + alumnosCreados.size() + "@example.test", expediente, sede);
		long id = jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = 1",
				Long.class, expediente);
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
				  organizacion_id, fecha_alta, fecha_alta_inicial, disciplina_id)
				VALUES (TRUE, 0, ?, 0, 1, 0, 1, '2023-09-01', '2023-09-01', ?)""", id, tkd);
		alumnosCreados.add(id);
		return id;
	}

	private void apuntarGrupo(long alumno, long grupo) {
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", grupo, alumno);
	}

	private void apuntarTurno(long alumno, long turno) {
		jdbc.update("INSERT INTO alumno_turno (alumno_id, turno_id) VALUES (?, ?)", alumno, turno);
	}
}
