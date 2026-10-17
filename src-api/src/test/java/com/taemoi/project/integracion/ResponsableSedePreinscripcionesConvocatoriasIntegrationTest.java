package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.dtos.request.PlantillaPreinscripcionRequest;
import com.taemoi.project.dtos.request.PreinscripcionRequest;
import com.taemoi.project.dtos.response.GrupoConAlumnosDTO;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.PreinscripcionService;

import jakarta.servlet.http.Cookie;

/**
 * Entrega 6-2 (entrega6-plan.md §3.3, §4.2) contra MySQL: el responsable de sede gestiona las preinscripciones de SUS
 * sedes (por el grupo de la solicitud) y, en las convocatorias comunes a la escuela (D6-8), solo a sus alumnos.
 * Finalizar da al alumno nuevo la sede del grupo (D6-2) y nunca una sede inactiva (D6-18); vincular solo fichas de su
 * sede principal y sin cambiar el email de una cuenta (D6-3); el alta pública no mezcla sedes (D6-7). Datos
 * sintéticos en escuelas nuevas ({@link EscuelaConSedesDePrueba} y una escuela de una sola sede).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResponsableSedePreinscripcionesConvocatoriasIntegrationTest extends MySqlIntegrationTest {

	private static final String TARIFA = "IT_E6_2";
	private static final String FINALIZAR_NUEVO = "{\"accionAlumno\":\"CREAR_NUEVO\",\"datosDeporte\":"
			+ "{\"tipoTarifa\":\"" + TARIFA + "\",\"cuantiaTarifa\":30.0}}";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;
	@Autowired PreinscripcionService preinscripcionService;

	@MockitoBean JavaMailSender mailSender;

	final AtomicInteger dnis = new AtomicInteger(1000);
	EscuelaConSedesDePrueba e;
	String tokenA;
	String tokenAC;
	String tokenGestor;
	long plantilla;
	long tA2;
	long tB2;
	long tC;
	long tSinSede;
	String pA;
	String pB;
	String pSinSede;
	String pSinGrupo;
	long convocatoria;
	long acA;
	long acGrupoA;
	long acB;
	long acInactivoA;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e6-preins");
		tokenA = e.token(jwtService, usuarios, e.respA);
		tokenAC = e.token(jwtService, usuarios, e.respAC);
		tokenGestor = e.token(jwtService, usuarios, e.gestor);
		plantilla = jdbc.queryForObject("""
				SELECT p.id FROM plantilla_preinscripcion p JOIN disciplina d ON d.id = p.disciplina_id
				WHERE p.organizacion_id = 1 AND d.codigo = 'TAEKWONDO' ORDER BY p.id LIMIT 1""", Long.class);
		jdbc.update("INSERT INTO tarifa (organizacion_id, codigo, nombre, importe) VALUES (?, ?, 'Tarifa IT', 30.00)",
				e.org, TARIFA);
		tA2 = turno(e.org, e.gA2, "Martes");
		tB2 = turno(e.org, e.gB, "Martes");
		tC = turno(e.org, e.gC, "Lunes");
		tSinSede = turno(e.org, e.gSin, "Lunes");

		pA = preinscripcion(e.org, e.gA, List.of(e.tA));
		pB = preinscripcion(e.org, e.gB, List.of(e.tB));
		pSinSede = preinscripcion(e.org, e.gSin, List.of(tSinSede));
		// Sin grupo (anterior a V4): invisible para el responsable aunque su turno sea de la sede A.
		pSinGrupo = preinscripcion(e.org, null, List.of(e.tA));

		jdbc.update("INSERT INTO convocatoria (fecha_convocatoria, organizacion_id, disciplina_id) VALUES "
				+ "('2026-06-01', ?, ?)", e.org, e.disciplina);
		convocatoria = jdbc.queryForObject("SELECT MAX(id) FROM convocatoria WHERE organizacion_id = ?", Long.class,
				e.org);
		// Con fecha de grado, marcar el pago no la rellena (esta disciplina de prueba no tiene sistema de grados).
		jdbc.update("UPDATE alumno_deporte SET fecha_grado = '2024-01-01' WHERE alumno_id = ?", e.aA);
		acA = inscripcion(e.aA);
		acGrupoA = inscripcion(e.aGrupoA);
		acB = inscripcion(e.aB);
		acInactivoA = inscripcion(e.aInactivoA);
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ preinscripciones: lectura

	@Test
	void preinscripciones_listadoSoloDeSusSedes() throws Exception {
		Set<String> deA = referencias(leer("/api/preinscripciones?size=100", tokenA));
		assertTrue(deA.contains(pA));
		assertFalse(deA.contains(pB), "otra sede");
		assertFalse(deA.contains(pSinSede), "grupo sin sede: solo gestión");
		assertFalse(deA.contains(pSinGrupo), "sin grupo: solo gestión");
		assertEquals(referenciasEnSedes(e.sA), deA, "exactamente las de los grupos de su sede");
		assertEquals(deA.size(), leer("/api/preinscripciones?size=100", tokenA).get("totalElements").asInt());

		Set<String> gestion = referencias(leer("/api/preinscripciones?size=100", tokenGestor));
		assertTrue(gestion.containsAll(Set.of(pA, pB, pSinSede, pSinGrupo)));
	}

	@Test
	void preinscripciones_porReferenciaSoloDeSuSede() throws Exception {
		assertEquals(200, estado(get("/api/preinscripciones/" + pA), tokenA));
		assertEquals(200, estado(get("/api/preinscripciones/" + pA + "/documento-firmado"), tokenA));
		assertEquals(200, estado(get("/api/preinscripciones/" + pA + "/firma"), tokenA));
		for (String ajena : List.of(pB, pSinSede, pSinGrupo, "PRE-NO-EXISTE")) {
			assertEquals(403, estado(get("/api/preinscripciones/" + ajena), tokenA), ajena);
			assertEquals(403, estado(get("/api/preinscripciones/" + ajena + "/documento-firmado"), tokenA));
			assertEquals(403, estado(get("/api/preinscripciones/" + ajena + "/firma"), tokenA));
			assertEquals(403, estado(post("/api/preinscripciones/" + ajena + "/cancelar"), tokenA));
			assertEquals(403, estado(post("/api/preinscripciones/" + ajena + "/reenviar"), tokenA));
			assertEquals(403, estado(post("/api/preinscripciones/" + ajena + "/reenviar-cambio-turnos"), tokenA));
			assertEquals(403, estado(post("/api/preinscripciones/" + ajena + "/reenviar-finalizacion"), tokenA));
			assertEquals(403, estado(post("/api/preinscripciones/" + ajena + "/finalizar")
					.contentType(MediaType.APPLICATION_JSON).content(FINALIZAR_NUEVO), tokenA));
			assertEquals(403, estado(put("/api/preinscripciones/" + ajena + "/turnos")
					.contentType(MediaType.APPLICATION_JSON).content(turnos(e.tA)), tokenA));
		}
		assertEquals("PENDIENTE", estadoDe(pB), "la de otra sede no se toca");
		// Sin cambios: borrar y plantillas, solo gestión.
		assertEquals(403, estado(delete("/api/preinscripciones/" + pA), tokenA));
		assertEquals(403, estado(get("/api/preinscripciones/plantillas/TAEKWONDO"), tokenA));
	}

	@Test
	void preinscripciones_candidatosSoloLosQuePuedeVincular() throws Exception {
		// Fichas con el DNI de una solicitud de la sede A: de la sede B; de la sede B pero en un grupo de A (la lee por
		// D9, pero no la puede vincular, D6-3); de la sede A. La gestión ve siempre la candidata; el responsable de A,
		// solo la de su sede principal.
		long fichaB = e.alumno("E6CANDIDATAB" + e.marca, e.sB, true);
		long fichaGrupoA = e.alumno("E6CANDIDATAGRUPOA" + e.marca, e.sB, true);
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", e.gA, fichaGrupoA);
		long fichaA = e.alumno("E6CANDIDATAA" + e.marca, e.sA, true);
		for (long ficha : List.of(fichaB, fichaGrupoA, fichaA)) {
			String dni = dni();
			jdbc.update("UPDATE alumno SET nif = ? WHERE id = ?", dni, ficha);
			String referencia = preinscripcion(e.org, e.gA, List.of(e.tA), dni, "otra@example.test");

			JsonNode responsable = leer("/api/preinscripciones/" + referencia, tokenA).get("alumnosCoincidentes");
			assertEquals(ficha == fichaA ? 1 : 0, responsable.size(), "ficha " + ficha);
			JsonNode gestion = leer("/api/preinscripciones/" + referencia, tokenGestor).get("alumnosCoincidentes");
			assertEquals(1, gestion.size());
			assertEquals(ficha, gestion.get(0).get("id").asLong());
		}
	}

	@Test
	void preinscripciones_otraEscuelaAunqueSuSedeFigureEnSuMembresia403() throws Exception {
		// Defensa (§5 «Integridad»): una sede de otra escuela en membresia_sede no abre nada de esa escuela.
		UnaSede otra = unaSede();
		long turnoOtra = turno(otra.org, otra.grupo, "Lunes");
		String preinscripcionOtra = preinscripcion(otra.org, otra.grupo, List.of(turnoOtra));
		jdbc.update("INSERT INTO convocatoria (fecha_convocatoria, organizacion_id, disciplina_id) VALUES "
				+ "('2026-06-01', ?, (SELECT id FROM disciplina WHERE organizacion_id = ? AND codigo = 'TAEKWONDO'))",
				otra.org, otra.org);
		long convocatoriaOtra = jdbc.queryForObject("SELECT MAX(id) FROM convocatoria WHERE organizacion_id = ?",
				Long.class, otra.org);
		long alumnoOtra = jdbc.queryForObject("SELECT COALESCE(MAX(numero_expediente), 70000) + 1 FROM alumno",
				Long.class);
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id, sede_principal_id)
				VALUES (1, 'PRUEBA', 1, 'Calle 1', '2010-01-01', 'E6OTRA', ?, '600000000', 0, ?, ?)""", alumnoOtra,
				otra.org, otra.sede);
		long idAlumnoOtra = jdbc.queryForObject(
				"SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = ?", Long.class, alumnoOtra,
				otra.org);
		jdbc.update("""
				INSERT INTO alumno_convocatoria (cuantia_examen, pagado, alumno_id, convocatoria_id, grado_actual,
				  grado_siguiente, organizacion_id) VALUES (20, 0, ?, ?, NULL, NULL, ?)""", idAlumnoOtra,
				convocatoriaOtra, otra.org);
		long inscripcionOtra = jdbc.queryForObject(
				"SELECT id FROM alumno_convocatoria WHERE convocatoria_id = ?", Long.class, convocatoriaOtra);

		// Responsable de A cuya membresía (de esta escuela) lista además la sede de la otra escuela.
		String token = tokenResponsable(e.sA, otra.sede);
		assertEquals(200, estado(get("/api/preinscripciones/" + pA), token), "su sede sigue funcionando");
		assertEquals(403, estado(get("/api/preinscripciones/" + preinscripcionOtra), token));
		assertEquals(403, estado(post("/api/preinscripciones/" + preinscripcionOtra + "/cancelar"), token));
		assertEquals(403, estado(post("/api/preinscripciones/" + preinscripcionOtra + "/finalizar")
				.contentType(MediaType.APPLICATION_JSON).content(FINALIZAR_NUEVO), token));
		assertEquals(403, estado(put("/api/convocatorias/alumno/" + inscripcionOtra)
				.contentType(MediaType.APPLICATION_JSON).content("{\"cuantiaExamen\":25.0,\"pagado\":true}"), token));
		assertFalse(referencias(leer("/api/preinscripciones?size=100", token)).contains(preinscripcionOtra));
		assertEquals("PENDIENTE", estadoDe(preinscripcionOtra));
		assertFalse(jdbc.queryForObject("SELECT pagado FROM alumno_convocatoria WHERE id = ?", Boolean.class,
				inscripcionOtra));
	}

	@Test
	void d67_cambioDeTurnosConDosSedes400ParaTodos() throws Exception {
		String referencia = preinscripcion(e.org, e.gA, List.of(e.tA));
		for (String token : List.of(tokenGestor, tokenA)) {
			MvcResult r = peticion(put("/api/preinscripciones/" + referencia + "/turnos")
					.contentType(MediaType.APPLICATION_JSON).content(turnos(e.tA, tB2)), token);
			assertEquals(400, r.getResponse().getStatus(), r.getResponse().getContentAsString());
			assertTrue(r.getResponse().getContentAsString().contains("Todos los turnos deben ser de la misma sede."));
		}
		assertEquals(e.gA, grupoDe(referencia));
		assertEquals(List.of(e.tA), jdbc.queryForList("""
				SELECT pt.turno_id FROM preinscripcion_turno pt JOIN preinscripcion p ON p.id = pt.preinscripcion_id
				WHERE p.referencia = ?""", Long.class, referencia));
	}

	// ------------------------------------------------------------------ preinscripciones: gestión

	@Test
	void preinscripciones_cancelaLaDeSuSede() throws Exception {
		String referencia = preinscripcion(e.org, e.gA, List.of(e.tA));
		MvcResult r = peticion(post("/api/preinscripciones/" + referencia + "/cancelar")
				.contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Grupo completo\"}"), tokenA);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertEquals("CANCELADA", estadoDe(referencia));
		assertEquals("Grupo completo",
				jdbc.queryForObject("SELECT motivo_cancelacion FROM preinscripcion WHERE referencia = ?", String.class,
						referencia));
	}

	@Test
	void preinscripciones_cambioDeTurnosSoloASusSedes() throws Exception {
		String referencia = preinscripcion(e.org, e.gA, List.of(e.tA));
		assertEquals(403, estado(put("/api/preinscripciones/" + referencia + "/turnos")
				.contentType(MediaType.APPLICATION_JSON).content(turnos(tB2)), tokenA), "turno de otra sede");
		assertEquals(e.gA, grupoDe(referencia));

		MvcResult r = peticion(put("/api/preinscripciones/" + referencia + "/turnos")
				.contentType(MediaType.APPLICATION_JSON).content(turnos(tA2)), tokenA);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertEquals(e.gA2, grupoDe(referencia), "otro grupo de su sede");
	}

	@Test
	void finalizar_alumnoNuevoConLaSedeDelGrupo() throws Exception {
		String deA = preinscripcion(e.org, e.gA, List.of(e.tA));
		MvcResult r = peticion(post("/api/preinscripciones/" + deA + "/finalizar")
				.contentType(MediaType.APPLICATION_JSON).content(FINALIZAR_NUEVO), tokenA);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertEquals("FINALIZADA", estadoDe(deA));
		long alumnoA = alumnoDe(deA);
		assertEquals(e.sA, e.sedeDe(alumnoA), "D6-2: la sede del grupo de la solicitud");
		assertEquals(List.of(e.gA), gruposDe(alumnoA));

		// La gestión, igual (D6-2 para todos los roles): una solicitud de la sede B da un alumno de la sede B.
		String deB = preinscripcion(e.org, e.gB, List.of(e.tB));
		MvcResult g = peticion(post("/api/preinscripciones/" + deB + "/finalizar")
				.contentType(MediaType.APPLICATION_JSON).content(FINALIZAR_NUEVO), tokenGestor);
		assertEquals(200, g.getResponse().getStatus(), g.getResponse().getContentAsString());
		assertEquals(e.sB, e.sedeDe(alumnoDe(deB)));
	}

	@Test
	void finalizar_haciaUnaSedeInactiva400() throws Exception {
		String deC = preinscripcion(e.org, e.gC, List.of(tC));
		for (String token : List.of(tokenAC, tokenGestor)) {
			MvcResult r = peticion(post("/api/preinscripciones/" + deC + "/finalizar")
					.contentType(MediaType.APPLICATION_JSON).content(FINALIZAR_NUEVO), token);
			assertEquals(400, r.getResponse().getStatus(), r.getResponse().getContentAsString());
			assertTrue(r.getResponse().getContentAsString().contains("La sede está inactiva."));
		}
		assertEquals("PENDIENTE", estadoDe(deC));
		assertNull(jdbc.queryForObject("SELECT alumno_id FROM preinscripcion WHERE referencia = ?", Long.class, deC));
	}

	@Test
	void finalizar_vincularFichaDeOtraSede403() throws Exception {
		for (long ajena : List.of(e.aB, e.aGrupoA, e.aSinSede)) {
			String referencia = preinscripcion(e.org, e.gA, List.of(e.tA));
			MvcResult r = peticion(post("/api/preinscripciones/" + referencia + "/finalizar")
					.contentType(MediaType.APPLICATION_JSON).content(vincular(ajena, "CONSERVAR_FICHA")), tokenA);
			assertEquals(403, r.getResponse().getStatus(), "D6-3: alumno " + ajena);
			assertEquals("PENDIENTE", estadoDe(referencia));
		}
		assertEquals(List.of(e.gB), gruposDe(e.aB), "la ficha ajena no se toca");
	}

	@Test
	void finalizar_vincularNoCambiaElEmailDeUnaCuenta() throws Exception {
		String dni = dni();
		String emailCuenta = "it-e6-2-cuenta-" + e.marca + "@example.test";
		long ficha = e.alumno("E6VINCULADA" + e.marca, e.sA, true);
		jdbc.update("UPDATE alumno SET nif = ?, email = ? WHERE id = ?", dni, emailCuenta, ficha);
		long cuenta = cuenta(emailCuenta);
		jdbc.update("UPDATE usuario SET alumno_id = ? WHERE id = ?", ficha, cuenta);
		String referencia = preinscripcion(e.org, e.gA, List.of(e.tA), dni, "nuevo-" + e.marca + "@example.test");

		MvcResult cambio = peticion(post("/api/preinscripciones/" + referencia + "/finalizar")
				.contentType(MediaType.APPLICATION_JSON).content(vincular(ficha, "USAR_PREINSCRIPCION")), tokenA);
		assertEquals(403, cambio.getResponse().getStatus(), cambio.getResponse().getContentAsString());
		assertEquals("PENDIENTE", estadoDe(referencia));
		assertEquals(emailCuenta, jdbc.queryForObject("SELECT email FROM alumno WHERE id = ?", String.class, ficha));

		MvcResult conserva = peticion(post("/api/preinscripciones/" + referencia + "/finalizar")
				.contentType(MediaType.APPLICATION_JSON).content(vincular(ficha, "CONSERVAR_FICHA")), tokenA);
		assertEquals(200, conserva.getResponse().getStatus(), conserva.getResponse().getContentAsString());
		assertEquals(ficha, alumnoDe(referencia));
		assertEquals(emailCuenta, jdbc.queryForObject("SELECT email FROM alumno WHERE id = ?", String.class, ficha));
		assertEquals(emailCuenta, jdbc.queryForObject("SELECT email FROM usuario WHERE id = ?", String.class, cuenta));
	}

	@Test
	void finalizar_vincularNoQuitaGruposNiTurnosDeOtraSede() throws Exception {
		String dni = dni();
		String email = "it-e6-2-dos-sedes-" + e.marca + "@example.test";
		long ficha = e.alumno("E6DOSSEDES" + e.marca, e.sA, true);
		jdbc.update("UPDATE alumno SET nif = ?, email = ? WHERE id = ?", dni, email, ficha);
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", e.gB, ficha);
		jdbc.update("INSERT INTO alumno_turno (turno_id, alumno_id) VALUES (?, ?)", tB2, ficha);
		String referencia = preinscripcion(e.org, e.gA, List.of(e.tA), dni, email);

		MvcResult r = peticion(post("/api/preinscripciones/" + referencia + "/finalizar")
				.contentType(MediaType.APPLICATION_JSON).content(vincular(ficha, "CONSERVAR_FICHA")), tokenA);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertEquals(List.of(e.gA, e.gB).stream().sorted().toList(), gruposDe(ficha), "D6-16: el grupo de B sigue");
		assertEquals(List.of(e.tA, tB2).stream().sorted().toList(), jdbc.queryForList(
				"SELECT turno_id FROM alumno_turno WHERE alumno_id = ? ORDER BY turno_id", Long.class, ficha));
		assertEquals(e.sA, e.sedeDe(ficha), "vincular no cambia la sede principal");
	}

	@Test
	void finalizar_unaSolaSede_laUnicaTambienConGrupoSinSede() throws Exception {
		UnaSede u = unaSede();
		for (long grupo : List.of(u.grupo, u.grupoSinSede)) {
			long turno = turno(u.org, grupo, "Lunes");
			String referencia = preinscripcion(u.org, grupo, List.of(turno));
			MvcResult r = peticion(post("/api/preinscripciones/" + referencia + "/finalizar")
					.contentType(MediaType.APPLICATION_JSON).content(FINALIZAR_NUEVO), u.token);
			assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
			assertEquals(u.sede, e.sedeDe(alumnoDe(referencia)), "con una sola sede, la única (como el alta de la 6-1)");
		}
	}

	// ------------------------------------------------------------------ alta pública (D6-7)

	@Test
	void d67_altaPublicaConTurnosDeDosSedes400() {
		PreinscripcionRequest solicitud = solicitud(List.of(e.tA, tB2));
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> TenantContext.calcularCon(e.org, () -> preinscripcionService.crear(solicitud, null)));
		assertEquals("Todos los turnos deben ser de la misma sede.", error.getMessage());
	}

	@Test
	void d67_altaPublicaDeUnaSolaSedeComoHastaAhora() {
		// Escuela con varias sedes: dos grupos de la misma sede pasan.
		TenantContext.calcularCon(e.org, () -> preinscripcionService.nuevaPlantilla("TAEKWONDO", plantilla()));
		Map<String, Object> dosGruposA = TenantContext.calcularCon(e.org,
				() -> preinscripcionService.crear(solicitud(List.of(e.tA, tA2)), null));
		assertNotNull(dosGruposA.get("referencia"));
		// Escuela de una sede, con un grupo con sede y otro sin ella: la validación no se aplica.
		UnaSede u = unaSede();
		long lunes = turno(u.org, u.grupo, "Lunes");
		long martes = turno(u.org, u.grupoSinSede, "Martes");
		TenantContext.calcularCon(u.org, () -> preinscripcionService.nuevaPlantilla("TAEKWONDO", plantilla()));
		Map<String, Object> unaSede = TenantContext.calcularCon(u.org,
				() -> preinscripcionService.crear(solicitud(List.of(lunes, martes)), null));
		assertNotNull(unaSede.get("referencia"));
	}

	// ------------------------------------------------------------------ convocatorias (D6-8)

	@Test
	void convocatorias_listaYDetalleComunes() throws Exception {
		assertTrue(ids(leer("/api/convocatorias", tokenA)).contains(convocatoria));
		assertEquals(convocatoria, leer("/api/convocatorias/" + convocatoria, tokenA).get("id").asLong());
		assertEquals(leer("/api/convocatorias", tokenGestor), leer("/api/convocatorias", tokenA));
	}

	@Test
	void convocatoria_soloSusAlumnos() throws Exception {
		Set<Long> deA = alumnos(leer("/api/convocatorias/" + convocatoria + "/alumnos", tokenA));
		assertTrue(deA.containsAll(Set.of(e.aA, e.aGrupoA)), "D9: por sede principal y por grupo");
		assertFalse(deA.contains(e.aB));
		assertTrue(Set.of(e.aA, e.aGrupoA, e.aInactivoA).containsAll(deA));
		Set<Long> reporte = alumnos(leer("/api/convocatorias/" + convocatoria + "/reporte", tokenA));
		assertEquals(deA, reporte);
		assertTrue(alumnos(leer("/api/convocatorias/" + convocatoria + "/alumnos", tokenGestor)).contains(e.aB));
		MvcResult pdf = peticion(get("/api/convocatorias/" + convocatoria + "/informe-pdf"), tokenA);
		assertEquals(200, pdf.getResponse().getStatus(), pdf.getResponse().getContentAsString());
		String texto = texto(pdf.getResponse().getContentAsByteArray());
		// El informe escribe los nombres en mayúsculas.
		assertTrue(texto.contains(("E6A" + e.marca).toUpperCase()), "su alumno en el informe");
		assertFalse(texto.contains(("E6B" + e.marca).toUpperCase()), "el alumno de otra sede no sale en el informe");
	}

	private static String texto(byte[] pdf) {
		try (org.apache.pdfbox.pdmodel.PDDocument documento = org.apache.pdfbox.pdmodel.PDDocument.load(pdf)) {
			return new org.apache.pdfbox.text.PDFTextStripper().getText(documento).replaceAll("\\s+", "");
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	@Test
	void convocatoria_marcaElPagoSoloDeSusAlumnos() throws Exception {
		MvcResult r = peticion(put("/api/convocatorias/alumno/" + acA).contentType(MediaType.APPLICATION_JSON)
				.content("{\"cuantiaExamen\":25.0,\"pagado\":true}"), tokenA);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertTrue(jdbc.queryForObject("SELECT pagado FROM alumno_convocatoria WHERE id = ?", Boolean.class, acA));
		// aGrupoA: lo ve por grupo, pero su sede principal es B (D6-3); aB, de otra sede.
		for (long ajena : List.of(acGrupoA, acB)) {
			assertEquals(403, estado(put("/api/convocatorias/alumno/" + ajena).contentType(MediaType.APPLICATION_JSON)
					.content("{\"cuantiaExamen\":25.0,\"pagado\":true}"), tokenA));
			assertFalse(jdbc.queryForObject("SELECT pagado FROM alumno_convocatoria WHERE id = ?", Boolean.class,
					ajena));
		}
	}

	@Test
	void convocatoria_apuntaYQuitaSoloASusAlumnos() throws Exception {
		assertEquals(403, estado(post("/api/alumnos/" + convocatoria + "/alumno/" + e.aB), tokenA));
		assertEquals(403, estado(delete("/api/alumnos/" + convocatoria + "/alumno/" + e.aB), tokenA));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM alumno_convocatoria WHERE id = ?", Integer.class,
				acB));
		// Su alumno pasa la autorización (el servicio rechaza apuntarlo por datos de grado: 400, no 403).
		assertEquals(400, estado(post("/api/alumnos/" + convocatoria + "/alumno/" + e.aA), tokenA));
		assertEquals(204, estado(delete("/api/alumnos/" + convocatoria + "/alumno/" + e.aInactivoA), tokenA));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM alumno_convocatoria WHERE id = ?", Integer.class,
				acInactivoA));
	}

	@Test
	void convocatoria_crearBorrarYActualizarGradosSoloGestion() throws Exception {
		assertEquals(403, estado(post("/api/convocatorias").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fechaConvocatoria\":\"2026-07-01\",\"deporte\":\"TAEKWONDO\"}"), tokenA));
		assertEquals(403, estado(delete("/api/convocatorias/" + convocatoria), tokenA));
		assertEquals(403, estado(put("/api/convocatorias/" + convocatoria + "/actualizar-grados"), tokenA));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM convocatoria WHERE id = ?", Integer.class,
				convocatoria));
	}

	@Test
	void cuentaFamiliaYResponsable_familiaIntactaYSedeLimitada() throws Exception {
		// Como familia, su alumno propio (sin sede); como responsable, los de su sede; nunca los de otra sede.
		assertEquals(200, estado(get("/api/convocatorias/alumnos/" + e.propioA), tokenA));
		assertEquals(200, estado(get("/api/convocatorias/alumnos/" + e.aA), tokenA));
		assertEquals(403, estado(get("/api/convocatorias/alumnos/" + e.aB), tokenA));
		assertEquals(403, estado(get("/api/preinscripciones/" + pB), tokenA));
		assertEquals(403, estado(post("/api/alumnos/" + convocatoria + "/alumno/" + e.propioA), tokenA),
				"su alumno de familia no tiene sede: no lo apunta como responsable");
	}

	// ------------------------------------------------------------------ datos

	private long turno(long org, Long grupo, String dia) {
		jdbc.update("""
				INSERT INTO turno (dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id)
				VALUES (?, '18:00', '19:00', NULL, ?, ?)""", dia, grupo, org);
		return jdbc.queryForObject("SELECT MAX(id) FROM turno WHERE organizacion_id = ?", Long.class, org);
	}

	private String preinscripcion(long org, Long grupo, List<Long> turnos) {
		return preinscripcion(org, grupo, turnos, dni(), "ana@example.test");
	}

	/** Solicitud PENDIENTE de un adulto (la plantilla es la de la escuela 1: FK simple). */
	private String preinscripcion(long org, Long grupo, List<Long> turnos, String dni, String email) {
		long disciplina = jdbc.queryForObject(
				"SELECT id FROM disciplina WHERE organizacion_id = ? AND codigo = 'TAEKWONDO'", Long.class, org);
		String referencia = "IT-E62-" + UUID.randomUUID().toString().substring(0, 12);
		jdbc.update("""
				INSERT INTO preinscripcion (referencia, temporada, estado, nombre, apellidos, dni, identidad_hash,
				  fecha_nacimiento, direccion, telefono, email, tiene_discapacidad, consentimiento_fotografico,
				  aceptacion_normas, firmante_nombre, firma, plantilla_id, plantilla_snapshot, pdf_firmado,
				  token_documento_hash, creada_en, actualizada_en, grupo_id, organizacion_id, disciplina_id)
				VALUES (?, '2025-2026', 'PENDIENTE', 'Ana', 'Prueba', ?, ?, '1990-05-05', 'Calle 1', '600000000', ?, 0,
				  0, 1, 'Firmante Prueba', x'00', ?, '{}', x'00', ?, '2025-09-10 12:00:00', '2025-09-10 12:00:00', ?,
				  ?, ?)""", referencia, dni, UUID.randomUUID().toString().replace("-", ""), email, plantilla,
				"e".repeat(64), grupo, org, disciplina);
		long id = jdbc.queryForObject("SELECT id FROM preinscripcion WHERE referencia = ?", Long.class, referencia);
		for (long turno : turnos) {
			jdbc.update("INSERT INTO preinscripcion_turno (preinscripcion_id, turno_id) VALUES (?, ?)", id, turno);
		}
		return referencia;
	}

	private long inscripcion(long alumno) {
		jdbc.update("""
				INSERT INTO alumno_convocatoria (cuantia_examen, pagado, alumno_id, convocatoria_id, grado_actual,
				  grado_siguiente, organizacion_id) VALUES (20, 0, ?, ?, NULL, NULL, ?)""", alumno, convocatoria, e.org);
		return jdbc.queryForObject("SELECT id FROM alumno_convocatoria WHERE alumno_id = ? AND convocatoria_id = ?",
				Long.class, alumno, convocatoria);
	}

	/** DNI sintético con formato válido y distinto en cada llamada (la letra no se comprueba). */
	private String dni() {
		return String.format("%08d", java.util.concurrent.ThreadLocalRandom.current().nextLong(10_000_000L,
				99_999_999L)) + "T";
	}

	private long cuenta(String email) {
		return TenantContext.calcularCon(EscuelaConSedesDePrueba.ORG1, () -> {
			Usuario u = new Usuario();
			u.setNombre("Cuenta");
			u.setApellidos("Vinculada");
			u.setEmail(email);
			u.setContrasena("fake-Password-123");
			u.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(u).getId();
		});
	}

	/**
	 * Cuenta FAMILIA (con un alumno propio, para la puerta de entrada, D6-15) + RESPONSABLE_SEDE en la escuela de
	 * prueba con esas sedes en membresia_sede; token de esa escuela.
	 */
	private String tokenResponsable(long... sedes) {
		long usuario = cuenta("it-e6-2-resp-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'FAMILIA')", usuario, e.org);
		long propio = e.alumno("E6PROPIORESP" + e.marca, null, true);
		jdbc.update("""
				INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen)
				VALUES (?, ?, ?, 'TUTOR', 'MANUAL')""", usuario, propio, e.org);
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'RESPONSABLE_SEDE')",
				usuario, e.org);
		long membresia = jdbc.queryForObject("""
				SELECT id FROM membresia WHERE usuario_id = ? AND organizacion_id = ? AND rol = 'RESPONSABLE_SEDE'""",
				Long.class, usuario, e.org);
		for (long sede : sedes) {
			jdbc.update("INSERT INTO membresia_sede (membresia_id, sede_id) VALUES (?, ?)", membresia, sede);
		}
		return e.token(jwtService, usuarios, usuario);
	}

	/** Escuela de una sola sede: un grupo con la sede (puesta sola, como el alta de grupos) y otro sin sede. */
	private UnaSede unaSede() {
		UnaSede u = new UnaSede();
		String slug = "it-e6-2-una-sede-" + UUID.randomUUID().toString().substring(0, 8);
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES (?, 'Escuela IT una sede')", slug);
		u.org = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = ?", Long.class, slug);
		jdbc.update("""
				INSERT INTO disciplina (organizacion_id, codigo, nombre, sistema_grados_id, edad_adulto, orden)
				VALUES (?, 'TAEKWONDO', 'Taekwondo IT', NULL, 14, 1)""", u.org);
		jdbc.update("INSERT INTO sede (organizacion_id, nombre, activa) VALUES (?, 'Única', TRUE)", u.org);
		u.sede = jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = ?", Long.class, u.org);
		jdbc.update("INSERT INTO tarifa (organizacion_id, codigo, nombre, importe) VALUES (?, ?, 'Tarifa IT', 30.00)",
				u.org, TARIFA);
		u.grupo = grupo(u.org, "IT E6-2 única " + slug);
		u.grupoSinSede = grupo(u.org, "IT E6-2 sin sede " + slug);
		jdbc.update("UPDATE grupo SET sede_id = NULL WHERE id = ?", u.grupoSinSede);
		long gestor = cuenta("it-e6-2-gestor-" + slug + "@example.test");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'ADMINISTRADOR')", gestor,
				u.org);
		Usuario usuario = TenantContext.calcularCon(EscuelaConSedesDePrueba.ORG1,
				() -> usuarios.findById(gestor).orElseThrow());
		u.token = jwtService.generateToken(usuario, 600_000, u.org);
		return u;
	}

	private long grupo(long org, String nombre) {
		GrupoConAlumnosDTO dto = new GrupoConAlumnosDTO();
		dto.setNombre(nombre);
		dto.setDeporte("TAEKWONDO");
		return TenantContext.calcularCon(org, () -> grupoService.crearGrupo(dto).getId());
	}

	static final class UnaSede {
		long org;
		long sede;
		long grupo;
		long grupoSinSede;
		String token;
	}

	private PreinscripcionRequest solicitud(List<Long> turnos) {
		return new PreinscripcionRequest("TAEKWONDO", turnos, "Eva", "Prueba Sedes " + dnis.incrementAndGet(), dni(),
				LocalDate.of(1990, 1, 1), "Calle Mayor 1", "612345678", "", "eva@example.test", "", "", "", false,
				false, true, "Eva Prueba", firma());
	}

	private static PlantillaPreinscripcionRequest plantilla() {
		return new PlantillaPreinscripcionRequest("Cabecera IT", "Contacto IT", "Título IT", "Consentimiento IT",
				List.of("Norma IT"), "Importes IT", "Instrucciones IT");
	}

	private static String firma() {
		try {
			BufferedImage imagen = new BufferedImage(320, 100, BufferedImage.TYPE_INT_RGB);
			Graphics2D graphics = imagen.createGraphics();
			graphics.setColor(Color.WHITE);
			graphics.fillRect(0, 0, imagen.getWidth(), imagen.getHeight());
			graphics.setColor(Color.BLACK);
			graphics.drawString("Firma de prueba", 20, 50);
			graphics.dispose();
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			ImageIO.write(imagen, "png", bytes);
			return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	// ------------------------------------------------------------------ consultas

	private String vincular(long alumno, String decisionEmail) {
		return "{\"accionAlumno\":\"VINCULAR_EXISTENTE\",\"alumnoId\":" + alumno + ",\"decisionEmail\":\""
				+ decisionEmail + "\",\"datosDeporte\":{\"tipoTarifa\":\"" + TARIFA + "\",\"cuantiaTarifa\":30.0}}";
	}

	private static String turnos(long... ids) {
		StringBuilder s = new StringBuilder("{\"turnoIds\":[");
		for (int i = 0; i < ids.length; i++) {
			s.append(i == 0 ? "" : ",").append(ids[i]);
		}
		return s.append("]}").toString();
	}

	private Set<String> referenciasEnSedes(long sede) {
		return new HashSet<>(jdbc.queryForList("""
				SELECT p.referencia FROM preinscripcion p JOIN grupo g ON g.id = p.grupo_id
				WHERE p.organizacion_id = ? AND g.sede_id = ?""", String.class, e.org, sede));
	}

	private String estadoDe(String referencia) {
		return jdbc.queryForObject("SELECT estado FROM preinscripcion WHERE referencia = ?", String.class, referencia);
	}

	private long grupoDe(String referencia) {
		return jdbc.queryForObject("SELECT grupo_id FROM preinscripcion WHERE referencia = ?", Long.class, referencia);
	}

	private long alumnoDe(String referencia) {
		Long alumno = jdbc.queryForObject("SELECT alumno_id FROM preinscripcion WHERE referencia = ?", Long.class,
				referencia);
		assertNotNull(alumno, "la preinscripción no tiene alumno");
		return alumno;
	}

	private List<Long> gruposDe(long alumno) {
		return jdbc.queryForList("SELECT grupo_id FROM alumno_grupo WHERE alumno_id = ? ORDER BY grupo_id", Long.class,
				alumno);
	}

	private int estado(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return peticion(peticion, token).getResponse().getStatus();
	}

	private MvcResult peticion(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
	}

	private JsonNode leer(String ruta, String token) throws Exception {
		MvcResult r = peticion(get(ruta), token);
		assertEquals(200, r.getResponse().getStatus(), ruta + ": " + r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString());
	}

	private static Set<String> referencias(JsonNode pagina) {
		Set<String> referencias = new HashSet<>();
		pagina.get("content").forEach(n -> referencias.add(n.get("referencia").asText()));
		return referencias;
	}

	private static Set<Long> ids(JsonNode lista) {
		Set<Long> ids = new HashSet<>();
		lista.forEach(n -> ids.add(n.get("id").asLong()));
		return ids;
	}

	private static Set<Long> alumnos(JsonNode lista) {
		List<Long> ids = new ArrayList<>();
		lista.forEach(n -> ids.add(n.get("alumnoId").asLong()));
		Set<Long> distintos = new HashSet<>(ids);
		assertEquals(ids.size(), distintos.size(), "sin repetidos");
		return distintos;
	}
}
