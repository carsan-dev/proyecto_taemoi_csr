package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.dtos.response.GrupoConAlumnosDTO;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * «Mi caja» del profesor ({@code GET /api/caja/profesor/alumnos}) con el criterio de Mis grupos (decisiones 310 y 515):
 * en cada grupo sale el alumno activo que, si tiene alguna inscripción, la tiene activa en la disciplina del grupo; sin
 * ninguna inscripción, sale. El criterio es por cada par alumno-grupo: {@code grupos} y {@code sedes} solo llevan los
 * grupos donde se cumple. Escuela sintética ({@link EscuelaConSedesDePrueba}), datos inventados; el profesor tiene un
 * grupo de Taekwondo en la sede A y otro de Kickboxing en la sede B.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MiCajaInscripcionInactivaIntegrationTest extends MySqlIntegrationTest {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	long kickboxing, gTkd, gKick;
	long activoEnTkd, bajaEnTkdActivoEnKick, activoEnAmbos, bajaEnTodo, sinInscripcion, inactivo;
	String tProf;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-mc-inactiva");
		jdbc.update("""
				INSERT INTO disciplina (organizacion_id, codigo, nombre, sistema_grados_id, edad_adulto, orden)
				VALUES (?, 'KICKBOXING', 'Kickboxing IT', NULL, 14, 2)""", e.org);
		kickboxing = jdbc.queryForObject(
				"SELECT id FROM disciplina WHERE organizacion_id = ? AND codigo = 'KICKBOXING'", Long.class, e.org);
		gTkd = grupo("IT MC Tkd " + e.marca, "TAEKWONDO", e.sA);
		gKick = grupo("IT MC Kick " + e.marca, "KICKBOXING", e.sB);

		long prof = e.cuenta(usuarios, "profmicaja");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROFESOR')", prof, e.org);
		asignar(prof, gTkd);
		asignar(prof, gKick);

		// Nombres en orden alfabético para fijar el orden de la lista (apellidos iguales, luego nombre).
		activoEnTkd = alumno("MCA_ACTIVOTKD" + e.marca, true, gTkd);
		e.taekwondo(activoEnTkd, false);

		// Baja en Taekwondo, activo en Kickboxing: en los dos grupos, solo cuenta el de Kickboxing.
		bajaEnTkdActivoEnKick = alumno("MCB_BAJATKD" + e.marca, true, gTkd, gKick);
		e.taekwondo(bajaEnTkdActivoEnKick, false);
		bajaTaekwondo(bajaEnTkdActivoEnKick);
		kickboxingActivo(bajaEnTkdActivoEnKick);

		// Activo en ambas disciplinas: en los dos grupos.
		activoEnAmbos = alumno("MCC_AMBOS" + e.marca, true, gTkd, gKick);
		e.taekwondo(activoEnAmbos, false);
		kickboxingActivo(activoEnAmbos);

		// De baja en Taekwondo y sin más inscripciones: no sale en ningún grupo.
		bajaEnTodo = alumno("MCD_BAJATODO" + e.marca, true, gTkd, gKick);
		e.taekwondo(bajaEnTodo, false);
		bajaTaekwondo(bajaEnTodo);

		sinInscripcion = alumno("MCE_SININSCRIPCION" + e.marca, true, gTkd);

		inactivo = alumno("MCF_INACTIVO" + e.marca, false, gTkd);
		e.taekwondo(inactivo, false);

		tProf = e.token(jwtService, usuarios, prof);
	}

	@AfterEach
	void sinFugas() {
		assertFalse(TenantContext.get() != null, "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	@Test
	void miCaja_listaPorParAlumnoGrupo_conSuInscripcionActivaEnLaDisciplinaDelGrupo() throws Exception {
		JsonNode lista = leer();
		// El de baja en todo y el inactivo no salen.
		assertEquals(List.of(activoEnTkd, bajaEnTkdActivoEnKick, activoEnAmbos, sinInscripcion), ids(lista),
				"alumnos de Mi caja");

		assertEquals(List.of(nombre(gTkd)), textos(fila(lista, activoEnTkd).get("grupos")));
		assertEquals(List.of(sede(e.sA)), textos(fila(lista, activoEnTkd).get("sedes"), "nombre"));

		// Baja en Taekwondo y activo en Kickboxing: solo el grupo (y la sede) de Kickboxing.
		assertEquals(List.of(nombre(gKick)), textos(fila(lista, bajaEnTkdActivoEnKick).get("grupos")));
		assertEquals(List.of(sede(e.sB)), textos(fila(lista, bajaEnTkdActivoEnKick).get("sedes"), "nombre"));

		// Activo en las dos: los dos grupos (por nombre) y las dos sedes.
		assertEquals(List.of(nombre(gKick), nombre(gTkd)), textos(fila(lista, activoEnAmbos).get("grupos")));
		assertEquals(2, fila(lista, activoEnAmbos).get("sedes").size());

		// Sin ninguna inscripción: entra, como en Grupos de gestión.
		assertEquals(List.of(nombre(gTkd)), textos(fila(lista, sinInscripcion).get("grupos")));
	}

	@Test
	void miCaja_alReactivarLaInscripcion_vuelveASalir() throws Exception {
		try {
			assertFalse(ids(leer()).contains(bajaEnTodo), "de baja en su única disciplina");
			jdbc.update("UPDATE alumno_deporte SET activo = TRUE WHERE alumno_id = ? AND disciplina_id = ?",
					bajaEnTodo, e.disciplina);
			JsonNode lista = leer();
			assertEquals(List.of(nombre(gTkd)), textos(fila(lista, bajaEnTodo).get("grupos")),
					"reactivado: solo en el grupo de su disciplina");
		} finally {
			bajaTaekwondo(bajaEnTodo);
		}
	}

	@Test
	void cargosPendientes_losGruposSoloIncluyenLosDeLaDisciplinaActivaDelAlumno() throws Exception {
		long cBaja = 0, cTodo = 0, cAmbos = 0;
		try {
			cBaja = cargo(bajaEnTkdActivoEnKick, "CARGO MCB");
			cTodo = cargo(bajaEnTodo, "CARGO MCD");
			cAmbos = cargo(activoEnAmbos, "CARGO MCC");
			MvcResult r = mvc.perform(get("/api/caja/profesor/cargos-pendientes").cookie(new Cookie("jwt", tProf)))
					.andReturn();
			assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
			JsonNode cargos = json.readTree(r.getResponse().getContentAsString());
			// Baja en Taekwondo y activo en Kickboxing: solo el grupo de Kickboxing.
			assertEquals(List.of(nombre(gKick)), textos(cargoDe(cargos, cBaja).get("grupos")));
			// Baja en todo: el cargo sigue listado, sin grupos.
			assertEquals(List.of(), textos(cargoDe(cargos, cTodo).get("grupos")));
			// Activo en las dos: los dos grupos, por nombre.
			assertEquals(List.of(nombre(gKick), nombre(gTkd)), textos(cargoDe(cargos, cAmbos).get("grupos")));
		} finally {
			jdbc.update("DELETE FROM producto_alumno WHERE id IN (?, ?, ?)", cBaja, cTodo, cAmbos);
		}
	}

	// ------------------------------------------------------------------ apoyo

	private long cargo(long alumno, String concepto) {
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, concepto, precio, cantidad, pagado, fecha_asignacion,
				  organizacion_id, sede_id)
				VALUES (?, ?, 30, 1, 0, '2026-02-01 12:00:00', ?, ?)""", alumno, concepto, e.org, e.sA);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE organizacion_id = ?", Long.class, e.org);
	}

	private static JsonNode cargoDe(JsonNode lista, long cargoId) {
		for (JsonNode n : lista) {
			if (n.get("cargoId").asLong() == cargoId) {
				return n;
			}
		}
		throw new AssertionError("El cargo " + cargoId + " no está en los pendientes: " + lista);
	}

	private long grupo(String nombre, String deporte, long sede) {
		GrupoConAlumnosDTO dto = new GrupoConAlumnosDTO();
		dto.setNombre(nombre);
		dto.setDeporte(deporte);
		dto.setSedeId(sede);
		return TenantContext.calcularCon(e.org, () -> grupoService.crearGrupo(dto).getId());
	}

	private void asignar(long profesor, long grupo) {
		jdbc.update("""
				INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol)
				VALUES (?, ?, ?, 'TITULAR')""", e.org, profesor, grupo);
	}

	private long alumno(String nombre, boolean activo, long... grupos) {
		long alumno = e.alumno(nombre, e.sA, activo);
		for (long g : grupos) {
			jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", g, alumno);
		}
		return alumno;
	}

	private void bajaTaekwondo(long alumno) {
		jdbc.update("UPDATE alumno_deporte SET activo = FALSE, principal = 0 WHERE alumno_id = ? AND disciplina_id = ?",
				alumno, e.disciplina);
	}

	private void kickboxingActivo(long alumno) {
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
				  organizacion_id, fecha_alta, fecha_alta_inicial, disciplina_id)
				VALUES (TRUE, FALSE, ?, 0, 0, 0, ?, '2024-09-01', '2024-09-01', ?)""", alumno, e.org, kickboxing);
	}

	private String nombre(long grupo) {
		return jdbc.queryForObject("SELECT nombre FROM grupo WHERE id = ?", String.class, grupo);
	}

	private String sede(long sede) {
		return jdbc.queryForObject("SELECT nombre FROM sede WHERE id = ?", String.class, sede);
	}

	private JsonNode leer() throws Exception {
		MvcResult r = mvc.perform(get("/api/caja/profesor/alumnos").cookie(new Cookie("jwt", tProf))).andReturn();
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString());
	}

	private static List<Long> ids(JsonNode lista) {
		List<Long> ids = new ArrayList<>();
		lista.forEach(n -> ids.add(n.get("alumnoId").asLong()));
		return ids;
	}

	private static JsonNode fila(JsonNode lista, long alumno) {
		for (JsonNode n : lista) {
			if (n.get("alumnoId").asLong() == alumno) {
				return n;
			}
		}
		throw new AssertionError("El alumno " + alumno + " no está en Mi caja");
	}

	private static List<String> textos(JsonNode lista) {
		List<String> r = new ArrayList<>();
		lista.forEach(n -> r.add(n.asText()));
		return r;
	}

	private static List<String> textos(JsonNode lista, String campo) {
		List<String> r = new ArrayList<>();
		lista.forEach(n -> r.add(n.get(campo).asText()));
		return r;
	}
}
