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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Mis grupos (lista y contador de la tarjeta) con el criterio de Grupos en gestión (decisión 310,
 * {@code GrupoRepository#contarAlumnosPorGrupo}): cuenta y sale el alumno activo que, si tiene alguna inscripción, la
 * tiene activa en la disciplina del grupo; sin ninguna inscripción, entra. Quien está de baja en Taekwondo y activo en
 * Kickboxing no sale en un grupo de Taekwondo. Escuela sintética ({@link EscuelaConSedesDePrueba}), datos inventados;
 * se usa gA2 (Taekwondo), vacío en el fixture.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProfesorGruposInscripcionInactivaIntegrationTest extends MySqlIntegrationTest {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	long kickboxing;
	long conTaekwondo, bajaEnTaekwondo, sinInscripcion, alumnoDeBaja;
	String tProf;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-mg-inactiva");
		jdbc.update("""
				INSERT INTO disciplina (organizacion_id, codigo, nombre, sistema_grados_id, edad_adulto, orden)
				VALUES (?, 'KICKBOXING', 'Kickboxing IT', NULL, 14, 2)""", e.org);
		kickboxing = jdbc.queryForObject(
				"SELECT id FROM disciplina WHERE organizacion_id = ? AND codigo = 'KICKBOXING'", Long.class, e.org);

		long prof = e.cuenta(usuarios, "profinactiva");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROFESOR')", prof, e.org);
		jdbc.update("""
				INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol)
				VALUES (?, ?, ?, 'TITULAR')""", e.org, prof, e.gA2);

		// Nombres en orden alfabético para fijar el orden de la lista (nombre, apellidos, id).
		conTaekwondo = enGA2("MGA_CONTKD" + e.marca, true);
		e.taekwondo(conTaekwondo, false);

		bajaEnTaekwondo = enGA2("MGB_BAJATKD" + e.marca, true);
		e.taekwondo(bajaEnTaekwondo, false);
		jdbc.update("UPDATE alumno_deporte SET activo = FALSE, principal = 0 WHERE alumno_id = ? AND disciplina_id = ?",
				bajaEnTaekwondo, e.disciplina);
		kickboxingActivo(bajaEnTaekwondo);

		sinInscripcion = enGA2("MGC_SININSCRIPCION" + e.marca, true);

		alumnoDeBaja = enGA2("MGD_DEBAJA" + e.marca, false);
		e.taekwondo(alumnoDeBaja, false);

		tProf = e.token(jwtService, usuarios, prof);
	}

	@AfterEach
	void sinFugas() {
		assertFalse(TenantContext.get() != null, "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	@Test
	void misGrupos_listaYContador_sinElDeBajaEnLaDisciplinaDelGrupo() throws Exception {
		// De baja en Taekwondo y activo en Kickboxing: ni sale ni cuenta. Sin ninguna inscripción: sale (gestión).
		assertEquals(List.of(conTaekwondo, sinInscripcion), alumnosDeGA2(), "lista de gA2");
		assertEquals(2, contadorDeGA2(), "contador de gA2");

		// Al reactivar su Taekwondo vuelve a salir y a contar, sin tocar el grupo.
		jdbc.update("UPDATE alumno_deporte SET activo = TRUE WHERE alumno_id = ? AND disciplina_id = ?",
				bajaEnTaekwondo, e.disciplina);
		assertEquals(List.of(conTaekwondo, bajaEnTaekwondo, sinInscripcion), alumnosDeGA2(), "lista tras reactivar");
		assertEquals(3, contadorDeGA2(), "contador tras reactivar");
	}

	// ------------------------------------------------------------------ apoyo

	private long enGA2(String nombre, boolean activo) {
		long alumno = e.alumno(nombre, e.sA, activo);
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", e.gA2, alumno);
		return alumno;
	}

	private void kickboxingActivo(long alumno) {
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
				  organizacion_id, fecha_alta, fecha_alta_inicial, disciplina_id)
				VALUES (TRUE, FALSE, ?, 0, 1, 0, ?, '2024-09-01', '2024-09-01', ?)""", alumno, e.org, kickboxing);
	}

	private List<Long> alumnosDeGA2() throws Exception {
		List<Long> ids = new ArrayList<>();
		leer(get("/api/profesor/grupos/" + e.gA2 + "/alumnos")).forEach(n -> ids.add(n.get("id").asLong()));
		return ids;
	}

	private long contadorDeGA2() throws Exception {
		for (JsonNode g : leer(get("/api/profesor/grupos"))) {
			if (g.get("id").asLong() == e.gA2) {
				return g.get("alumnosActivos").asLong();
			}
		}
		throw new AssertionError("gA2 no está en Mis grupos");
	}

	private JsonNode leer(MockHttpServletRequestBuilder peticion) throws Exception {
		MvcResult r = mvc.perform(peticion.cookie(new Cookie("jwt", tProf))).andReturn();
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString());
	}
}
