package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Regresión de privacidad: {@code GET /api/turnos/dto} es público (página /horarios) y un anónimo se resuelve
 * en la organización por defecto (Moiskimdo). La respuesta no debe incrustar los alumnos de cada turno (NIF,
 * dirección, email, teléfonos...), solo lo que /horarios pinta; el rango de edad de los alumnos lo calcula el
 * servidor con los mismos alumnos que antes (activos y con el deporte del grupo activo).
 *
 * <p>Datos sintéticos en la organización 1. Las edades esperadas no dependen de la hora de la máquina: cada
 * nacimiento está a N años y 30 días de hoy en Madrid, así que la edad es N aunque el día cambie durante el test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TurnosPublicosSinDatosPersonalesIntegrationTest extends MySqlIntegrationTest {

	private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
	/** Forma exacta de cada turno público: cualquier campo nuevo debe revisarse aquí. */
	private static final Set<String> CLAVES_TURNO = new TreeSet<>(Set.of("id", "diaSemana", "horaInicio", "horaFin",
			"grupoId", "tipoGrupo", "rangoEdadMin", "rangoEdadMax", "edadMinAlumnos", "edadMaxAlumnos",
			"ocupacionEfectiva", "completo"));
	private static final List<String> CLAVES_PERSONALES = List.of("alumnos", "nif", "email", "telefono",
			"telefono2", "direccion", "fechaNacimiento", "nombre", "apellidos", "responsableLegal",
			"observaciones", "fotoAlumno");

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;

	@MockitoBean JavaMailSender mailSender;

	final String marca = UUID.randomUUID().toString().substring(0, 8);
	final List<Long> gruposCreados = new ArrayList<>();
	final List<Long> fichas = new ArrayList<>();
	final List<String> valoresPersonales = new ArrayList<>();
	long tkd;
	long kb;
	long sede;
	long turnoConAlumnos;
	long turnoSinAlumnos;
	long turnoConRango;
	long turnoSoloNoCuentan;

	@BeforeAll
	void preparar() {
		LocalDate hoy = LocalDate.now(MADRID);
		sede = jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = 1 ORDER BY id LIMIT 1", Long.class);
		tkd = disciplina("TAEKWONDO");
		kb = disciplina("KICKBOXING");

		turnoConAlumnos = turno(grupo(null, null));
		long a10 = alumno(hoy.minusYears(10).minusDays(30), true);
		long a14 = alumno(hoy.minusYears(14).minusDays(30), true);
		long inactivo = alumno(hoy.minusYears(40).minusDays(30), false);
		long otroDeporte = alumno(hoy.minusYears(50).minusDays(30), true);
		long deporteDeBaja = alumno(hoy.minusYears(3).minusDays(30), true);
		deporte(a10, tkd, true);
		deporte(a14, tkd, true);
		deporte(inactivo, tkd, true);
		deporte(otroDeporte, kb, true);
		deporte(deporteDeBaja, tkd, false);
		for (long a : List.of(a10, a14, inactivo, otroDeporte, deporteDeBaja)) {
			apuntar(a, turnoConAlumnos);
		}

		turnoSinAlumnos = turno(grupo(null, null));

		turnoConRango = turno(grupo(6, 12));
		long conRango = alumno(hoy.minusYears(9).minusDays(30), true);
		deporte(conRango, tkd, true);
		apuntar(conRango, turnoConRango);

		turnoSoloNoCuentan = turno(grupo(null, null));
		apuntar(inactivo, turnoSoloNoCuentan);
		apuntar(otroDeporte, turnoSoloNoCuentan);
	}

	/** Sin borrar fichas (tienen dependientes): se retiran como en las demás clases de la organización 1. */
	@AfterAll
	void retirar() {
		for (Long grupo : gruposCreados) {
			jdbc.update("DELETE FROM alumno_turno WHERE turno_id IN (SELECT id FROM turno WHERE grupo_id = ?)", grupo);
			jdbc.update("DELETE FROM turno WHERE grupo_id = ?", grupo);
			jdbc.update("DELETE FROM grupo WHERE id = ?", grupo);
		}
		for (Long ficha : fichas) {
			jdbc.update("UPDATE alumno SET activo = FALSE WHERE id = ?", ficha);
			jdbc.update("UPDATE alumno_deporte SET activo = FALSE WHERE alumno_id = ?", ficha);
		}
	}

	@Test
	void anonimoNoRecibeAlumnosNiDatosPersonales() throws Exception {
		String cuerpo = leerAnonimo();
		JsonNode turnos = json.readTree(cuerpo);
		assertTrue(turnos.isArray());
		assertTrue(turnos.size() >= 4);

		for (JsonNode turno : turnos) {
			Set<String> claves = new TreeSet<>();
			turno.fieldNames().forEachRemaining(claves::add);
			assertEquals(CLAVES_TURNO, claves, "Forma del turno público " + turno.get("id"));
		}
		Set<String> todas = new TreeSet<>();
		recogerClaves(turnos, todas);
		for (String prohibida : CLAVES_PERSONALES) {
			assertFalse(todas.contains(prohibida), "La respuesta pública expone '" + prohibida + "'");
		}
		for (String valor : valoresPersonales) {
			assertFalse(cuerpo.contains(valor), "La respuesta pública contiene un dato personal sintético");
		}
	}

	@Test
	void rangoDeEdadDeLosAlumnosQueCuentan() throws Exception {
		Map<Long, JsonNode> porId = porId(json.readTree(leerAnonimo()));

		JsonNode conAlumnos = porId.get(turnoConAlumnos);
		assertEquals(10, conAlumnos.get("edadMinAlumnos").asInt());
		assertEquals(14, conAlumnos.get("edadMaxAlumnos").asInt());
		assertTrue(conAlumnos.get("rangoEdadMin").isNull());
		assertTrue(conAlumnos.get("rangoEdadMax").isNull());
		assertNotNull(conAlumnos.get("ocupacionEfectiva"));
		assertNotNull(conAlumnos.get("completo"));

		for (long sinEdades : List.of(turnoSinAlumnos, turnoSoloNoCuentan)) {
			assertTrue(porId.get(sinEdades).get("edadMinAlumnos").isNull());
			assertTrue(porId.get(sinEdades).get("edadMaxAlumnos").isNull());
		}

		// Con rango configurado /horarios pinta ese rango: las edades de los alumnos no se envían.
		JsonNode conRango = porId.get(turnoConRango);
		assertEquals(6, conRango.get("rangoEdadMin").asInt());
		assertEquals(12, conRango.get("rangoEdadMax").asInt());
		assertTrue(conRango.get("edadMinAlumnos").isNull());
		assertTrue(conRango.get("edadMaxAlumnos").isNull());
	}

	private String leerAnonimo() throws Exception {
		MvcResult r = mvc.perform(get("/api/turnos/dto")).andReturn();
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertNull(r.getRequest().getCookies());
		return r.getResponse().getContentAsString();
	}

	private static Map<Long, JsonNode> porId(JsonNode turnos) {
		Map<Long, JsonNode> porId = new HashMap<>();
		turnos.forEach(t -> porId.put(t.get("id").asLong(), t));
		return porId;
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

	private long disciplina(String codigo) {
		return jdbc.queryForObject("SELECT id FROM disciplina WHERE organizacion_id = 1 AND codigo = ?", Long.class,
				codigo);
	}

	private long grupo(Integer rangoMin, Integer rangoMax) {
		String nombre = "IT turnos publicos " + marca + " " + gruposCreados.size();
		jdbc.update("""
				INSERT INTO grupo (nombre, tipo, rango_edad_min, rango_edad_max, organizacion_id, disciplina_id, sede_id)
				VALUES (?, NULL, ?, ?, 1, ?, ?)""", nombre, rangoMin, rangoMax, tkd, sede);
		long grupo = jdbc.queryForObject("SELECT id FROM grupo WHERE nombre = ? AND organizacion_id = 1", Long.class,
				nombre);
		gruposCreados.add(grupo);
		return grupo;
	}

	private long turno(long grupo) {
		jdbc.update("""
				INSERT INTO turno (dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id)
				VALUES ('Lunes', '18:00', '19:00', 'Taekwondo', ?, 1)""", grupo);
		return jdbc.queryForObject("SELECT MAX(id) FROM turno WHERE grupo_id = ?", Long.class, grupo);
	}

	private long alumno(LocalDate nacimiento, boolean activo) {
		long expediente = jdbc.queryForObject("SELECT COALESCE(MAX(numero_expediente), 60000) + 1 FROM alumno",
				Long.class);
		int n = fichas.size();
		String nif = String.format("Y%07dZ", ThreadLocalRandom.current().nextInt(10_000_000));
		String email = "it-turnos-" + marca + "-" + n + "@example.test";
		String telefono = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
		String direccion = "Calle Ficticia " + marca + " " + n;
		String nombre = "ITTURNOS" + marca + n;
		valoresPersonales.addAll(List.of(nif, email, telefono, direccion, nombre, nacimiento.toString()));
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre, nif, email,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id, sede_principal_id)
				VALUES (?, 'PRUEBA', 1, ?, ?, ?, ?, ?, ?, ?, 0, 1, ?)""",
				activo, direccion, java.sql.Date.valueOf(nacimiento), nombre, nif, email, expediente, telefono, sede);
		long id = jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = 1",
				Long.class, expediente);
		fichas.add(id);
		return id;
	}

	private void deporte(long alumno, long disciplina, boolean activo) {
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
				  organizacion_id, fecha_alta, fecha_alta_inicial, disciplina_id)
				VALUES (?, 0, ?, 0, 1, 0, 1, '2023-09-01', '2023-09-01', ?)""", activo, alumno, disciplina);
	}

	private void apuntar(long alumno, long turno) {
		jdbc.update("INSERT INTO alumno_turno (turno_id, alumno_id) VALUES (?, ?)", turno, alumno);
	}
}
