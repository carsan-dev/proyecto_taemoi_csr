package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterAll;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Membresia;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.MembresiaRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.ContadorService;

import jakarta.servlet.http.Cookie;

/**
 * Regresión de privacidad: {@code GET /api/grupos/{grupoId}/alumnos/{alumnoId}/turnos} admite a la familia
 * ({@code ROLE_USER}) dueña del alumno. Hasta esta versión cada turno incrustaba el {@code AlumnoDTO} completo de
 * todos los alumnos del turno (NIF, dirección, email, teléfonos...): la familia veía los datos de los compañeros
 * de su hijo. La respuesta ya no lleva alumnos; el acceso (403 a una familia ajena) y el 404 con la lista vacía
 * siguen igual.
 *
 * <p>Datos sintéticos en la organización 1 (Moiskimdo): la familia entra por el email del alumno (D6).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TurnosDelAlumnoEnGrupoSinCompanerosIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String CLAVE = "Clave-Segura-123";
	/** Forma exacta de cada turno: cualquier campo nuevo debe revisarse aquí. */
	private static final Set<String> CLAVES_TURNO = new TreeSet<>(Set.of("id", "diaSemana", "horaInicio", "horaFin",
			"grupoId", "tipoGrupo", "rangoEdadMin", "rangoEdadMax", "ocupacionEfectiva", "completo"));
	private static final List<String> CLAVES_PERSONALES = List.of("alumnos", "nif", "email", "telefono",
			"telefono2", "direccion", "fechaNacimiento", "nombre", "apellidos", "responsableLegal",
			"observaciones", "fotoAlumno");

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired PasswordEncoder encoder;
	@Autowired UsuarioRepository usuarios;
	@Autowired MembresiaRepository membresias;
	@Autowired AlumnoRepository alumnos;
	@Autowired ContadorService contadores;

	@MockitoBean JavaMailSender mailSender;

	final String marca = UUID.randomUUID().toString().substring(0, 8);
	final List<Long> cuentas = new ArrayList<>();
	final List<Long> fichas = new ArrayList<>();
	final List<Long> gruposCreados = new ArrayList<>();
	final List<String> valoresCompaneros = new ArrayList<>();
	long sede;
	long tkd;
	long grupo;
	long otroGrupo;
	long turno;
	long alumnoFamilia;
	String tokenFamilia;
	String tokenOtraFamilia;

	@BeforeAll
	void preparar() throws Exception {
		sede = jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = 1 ORDER BY id LIMIT 1", Long.class);
		tkd = jdbc.queryForObject("SELECT id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'TAEKWONDO'",
				Long.class);

		Usuario familia = cuenta("tg-familia");
		dar(familia, RolMembresia.FAMILIA);
		alumnoFamilia = alumnoDeFamilia("TG FAMILIA", familia.getEmail());

		Usuario otra = cuenta("tg-otra");
		dar(otra, RolMembresia.FAMILIA);
		alumnoDeFamilia("TG OTRA", otra.getEmail());

		grupo = grupo();
		otroGrupo = grupo();
		turno = turno(grupo);
		apuntar(alumnoFamilia, turno);
		deporte(alumnoFamilia);
		// Compañeros activos con el deporte del grupo: son los que TurnoCortoDTO incrustaba.
		for (int i = 0; i < 2; i++) {
			long companero = companero(i);
			deporte(companero);
			apuntar(companero, turno);
		}

		tokenFamilia = login(familia.getEmail());
		tokenOtraFamilia = login(otra.getEmail());
	}

	/** Sin borrar fichas (tienen dependientes): se retiran como en las demás clases de la organización 1. */
	@AfterAll
	void retirar() {
		for (Long g : gruposCreados) {
			jdbc.update("DELETE FROM alumno_turno WHERE turno_id IN (SELECT id FROM turno WHERE grupo_id = ?)", g);
			jdbc.update("DELETE FROM turno WHERE grupo_id = ?", g);
			jdbc.update("DELETE FROM grupo WHERE id = ?", g);
		}
		for (Long ficha : fichas) {
			jdbc.update("UPDATE alumno SET activo = FALSE WHERE id = ?", ficha);
			jdbc.update("UPDATE alumno_deporte SET activo = FALSE WHERE alumno_id = ?", ficha);
		}
		for (Long cuenta : cuentas) {
			jdbc.update("UPDATE membresia SET activa = FALSE WHERE usuario_id = ?", cuenta);
		}
	}

	@Test
	void familiaRecibeSusTurnosSinDatosDeLosCompaneros() throws Exception {
		MvcResult r = pedir(grupo, tokenFamilia);
		String cuerpo = r.getResponse().getContentAsString();
		assertEquals(200, r.getResponse().getStatus(), cuerpo);

		JsonNode turnos = json.readTree(cuerpo);
		assertTrue(turnos.isArray());
		assertEquals(1, turnos.size(), cuerpo);
		JsonNode t = turnos.get(0);
		Set<String> claves = new TreeSet<>();
		t.fieldNames().forEachRemaining(claves::add);
		assertEquals(CLAVES_TURNO, claves, "Forma del turno");
		assertEquals(turno, t.get("id").asLong());
		assertEquals(grupo, t.get("grupoId").asLong());
		assertEquals("Lunes", t.get("diaSemana").asText());
		assertEquals("18:00", t.get("horaInicio").asText());
		assertEquals("19:00", t.get("horaFin").asText());
		assertEquals("Taekwondo", t.get("tipoGrupo").asText());
		assertEquals(6, t.get("rangoEdadMin").asInt());
		assertEquals(12, t.get("rangoEdadMax").asInt());

		Set<String> todas = new TreeSet<>();
		recogerClaves(turnos, todas);
		for (String prohibida : CLAVES_PERSONALES) {
			assertFalse(todas.contains(prohibida), "La respuesta expone '" + prohibida + "'");
		}
		assertFalse(valoresCompaneros.isEmpty());
		for (String valor : valoresCompaneros) {
			assertFalse(cuerpo.contains(valor), "La respuesta contiene un dato personal sintético de un compañero");
		}
	}

	@Test
	void familiaAjenaSigueRecibiendo403() throws Exception {
		MvcResult r = pedir(grupo, tokenOtraFamilia);
		assertEquals(403, r.getResponse().getStatus());
		assertTrue(r.getResponse().getContentAsString().contains("Acceso denegado"));
	}

	@Test
	void sinTurnosEnEseGrupoSigueDevolviendo404() throws Exception {
		assertEquals(404, pedir(otroGrupo, tokenFamilia).getResponse().getStatus());
	}

	// ------------------------------------------------------------------ utilidades

	private MvcResult pedir(long grupoId, String token) throws Exception {
		return mvc.perform(get("/api/grupos/" + grupoId + "/alumnos/" + alumnoFamilia + "/turnos")
				.cookie(new Cookie("jwt", token))).andReturn();
	}

	private static void recogerClaves(JsonNode nodo, Set<String> claves) {
		if (nodo.isObject()) {
			for (Iterator<Map.Entry<String, JsonNode>> it = nodo.fields(); it.hasNext();) {
				Map.Entry<String, JsonNode> campo = it.next();
				claves.add(campo.getKey());
				recogerClaves(campo.getValue(), claves);
			}
		} else if (nodo.isArray()) {
			nodo.forEach(hijo -> recogerClaves(hijo, claves));
		}
	}

	private String login(String email) throws Exception {
		MvcResult resultado = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return json.readTree(resultado.getResponse().getContentAsString()).get("token").asText();
	}

	private Usuario cuenta(String nombre) {
		Usuario creada = TenantContext.calcularCon(ORG1, () -> {
			Usuario usuario = new Usuario();
			usuario.setNombre("Usuario");
			usuario.setApellidos("Turnos grupo");
			usuario.setEmail(nombre + "-" + marca + "@example.test");
			usuario.setContrasena(encoder.encode(CLAVE));
			usuario.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(usuario);
		});
		cuentas.add(creada.getId());
		return creada;
	}

	private Membresia dar(Usuario usuario, RolMembresia rol) {
		return TenantContext.calcularCon(ORG1, () -> membresias.save(new Membresia(usuario.getId(), ORG1, rol)));
	}

	private long alumnoDeFamilia(String nombre, String email) {
		long id = TenantContext.calcularCon(ORG1, () -> {
			Alumno alumno = new Alumno();
			alumno.setNombre(nombre + " " + marca);
			alumno.setApellidos("PRUEBA TURNOS GRUPO");
			alumno.setNumeroExpediente(contadores.siguienteNumeroExpediente());
			alumno.setFechaNacimiento(Date.valueOf(LocalDate.of(2015, 4, 4)));
			alumno.setDireccion("Calle Prueba 8");
			alumno.setTelefono("600000008");
			alumno.setActivo(true);
			alumno.setAutorizacionWeb(true);
			alumno.setEmail(email);
			return alumnos.save(alumno).getId();
		});
		fichas.add(id);
		return id;
	}

	private long companero(int n) {
		long expediente = TenantContext.calcularCon(ORG1, () -> contadores.siguienteNumeroExpediente());
		String nif = String.format("X%07dT", ThreadLocalRandom.current().nextInt(10_000_000));
		String email = "it-companero-" + marca + "-" + n + "@example.test";
		String telefono = "7" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
		String direccion = "Avenida Inventada " + marca + " " + n;
		String nombre = "ITCOMPANERO" + marca + n;
		valoresCompaneros.addAll(List.of(nif, email, telefono, direccion, nombre));
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre, nif, email,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id, sede_principal_id)
				VALUES (TRUE, 'PRUEBA', 1, ?, '2014-05-06', ?, ?, ?, ?, ?, 0, 1, ?)""",
				direccion, nombre, nif, email, expediente, telefono, sede);
		long id = jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = 1",
				Long.class, expediente);
		fichas.add(id);
		return id;
	}

	private long grupo() {
		String nombre = "IT turnos alumno grupo " + marca + " " + gruposCreados.size();
		jdbc.update("""
				INSERT INTO grupo (nombre, tipo, rango_edad_min, rango_edad_max, organizacion_id, disciplina_id, sede_id)
				VALUES (?, NULL, 6, 12, 1, ?, ?)""", nombre, tkd, sede);
		long id = jdbc.queryForObject("SELECT id FROM grupo WHERE nombre = ? AND organizacion_id = 1", Long.class,
				nombre);
		gruposCreados.add(id);
		return id;
	}

	private long turno(long grupoId) {
		jdbc.update("""
				INSERT INTO turno (dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id)
				VALUES ('Lunes', '18:00', '19:00', 'Taekwondo', ?, 1)""", grupoId);
		return jdbc.queryForObject("SELECT MAX(id) FROM turno WHERE grupo_id = ?", Long.class, grupoId);
	}

	private void deporte(long alumno) {
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
				  organizacion_id, fecha_alta, fecha_alta_inicial, disciplina_id)
				VALUES (TRUE, 0, ?, 0, 1, 0, 1, '2023-09-01', '2023-09-01', ?)""", alumno, tkd);
	}

	private void apuntar(long alumno, long turnoId) {
		jdbc.update("INSERT INTO alumno_turno (turno_id, alumno_id) VALUES (?, ?)", turnoId, alumno);
	}
}
