package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

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
 * Pieza E7-1 contra MySQL (fase3-e7-plan.md §2): las rutas de lectura del profesor bajo {@code /api/profesor/**}.
 * <ul>
 * <li>Solo sus grupos ({@code alcance.grupos()}), por id y en listados; un grupo o alumno ajeno (otro profesor, otra
 * escuela o inexistente) da 403 sin revelar si existe; un alumno de baja de su grupo, 404 (D7-13).</li>
 * <li>DTO reducidos: nunca NIF, email, dirección, observaciones, tarifa ni cobros (D7-12).</li>
 * <li>Cuentas mixtas: R+Pr (sesión 184) no ve sus sedes por estas rutas; F+Pr usa sus grupos; P/G (también con
 * asignación) no usa estas rutas (D7-10). La sesión solo-profesor sigue cerrada fuera de su lista blanca.</li>
 * </ul>
 * Escuela sintética ({@link EscuelaConSedesDePrueba}, con un sistema de grados propio y
 * {@code comportamiento_legado_taemoi} solo en ella) y otra escuela para el aislamiento. Datos inventados; todo vive
 * en esas dos escuelas nuevas, como el resto de la batería.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProfesorGruposIntegrationTest extends MySqlIntegrationTest {

	private static final String NIF_ALUMNO = "X9990001Z";
	private static final String EMAIL_ALUMNO = "alumno-e71@example.test";
	private static final String DIRECCION_ALUMNO = "Calle Ficticia E71";
	private static final String OBSERVACIONES = "OBSERVACION-E71";
	private static final String NIF_TUTOR = "X9990002Z";
	private static final String EMAIL_TUTOR = "tutor-e71@example.test";
	private static final LocalDate NACIMIENTO = LocalDate.of(2017, 5, 10);

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	long profA, profB, profRP, profFP, gestorProf, profOtra, profSinGrupos, propietario;
	long familiaPropioFP;
	long tutelado, deBaja, alumnoGA2, kickboxing, gAmarillo, gRojo;
	String tProfA, tProfB, tProfRP, tProfFP, tGestorProf, tProfOtra, tProfSinGrupos, tGestor, tPropietario;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e7-1");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e7-1-otra");
		// Solo esta escuela sintética: el pase a rojo pide elegir bordado, como en Taemoi.
		jdbc.update("UPDATE organizacion SET comportamiento_legado_taemoi = TRUE WHERE id = ?", e.org);
		sistemaDeGrados();
		jdbc.update("""
				INSERT INTO disciplina (organizacion_id, codigo, nombre, sistema_grados_id, edad_adulto, orden)
				VALUES (?, 'KICKBOXING', 'Kickboxing IT', NULL, 14, 2)""", e.org);
		kickboxing = jdbc.queryForObject(
				"SELECT id FROM disciplina WHERE organizacion_id = ? AND codigo = 'KICKBOXING'", Long.class, e.org);

		profA = soloProfesor(e, "profa");
		asignar(e, profA, e.gA, "TITULAR");
		asignar(e, profA, e.gSin, "AYUDANTE");
		profB = soloProfesor(e, "profb");
		asignar(e, profB, e.gB, "TITULAR");
		profRP = soloProfesor(e, "profrp");
		responsableDeSede(e, profRP, e.sA);
		asignar(e, profRP, e.gB, "TITULAR");
		profFP = e.cuentaFamilia(usuarios, "proffp");
		familiaPropioFP = jdbc.queryForObject("SELECT MAX(alumno_id) FROM usuario_alumno WHERE usuario_id = ?",
				Long.class, profFP);
		membresia(e, profFP, "PROFESOR");
		asignar(e, profFP, e.gA2, "AYUDANTE");
		gestorProf = e.cuenta(usuarios, "gestorprof");
		membresia(e, gestorProf, "ADMINISTRADOR");
		membresia(e, gestorProf, "PROFESOR");
		asignar(e, gestorProf, e.gA, "TITULAR");
		profSinGrupos = soloProfesor(e, "profsingrupos");
		propietario = e.cuenta(usuarios, "propietario");
		membresia(e, propietario, "PROPIETARIO");
		profOtra = soloProfesor(otra, "profotra");
		asignar(otra, profOtra, otra.gA, "TITULAR");

		// Alumno de gA con todos los datos sensibles rellenos (no deben salir) y dos tutores.
		tutelado = e.alumno("E71TUTELADO" + e.marca, e.sA, true);
		jdbc.update("""
				UPDATE alumno SET nombre = 'LUCIA', apellidos = 'AAA FICTICIA', nif = ?, email = ?, direccion = ?,
				  observaciones = ?, telefono = '600111001', telefono2 = '600111002', fecha_nacimiento = ?
				WHERE id = ?""", NIF_ALUMNO, EMAIL_ALUMNO, DIRECCION_ALUMNO, OBSERVACIONES, NACIMIENTO, tutelado);
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", e.gA, tutelado);
		jdbc.update("""
				INSERT INTO tutor (organizacion_id, alumno_id, nombre, apellidos, nif, parentesco, telefono, email,
				  responsable_legal, orden)
				VALUES (?, ?, 'MARIA', 'FICTICIA', ?, 'MADRE', '600222001', ?, TRUE, 0),
				       (?, ?, 'PEDRO', 'FICTICIO', NULL, 'PADRE', NULL, NULL, FALSE, 1)""",
				e.org, tutelado, NIF_TUTOR, EMAIL_TUTOR, e.org, tutelado);
		e.taekwondo(tutelado, true);
		jdbc.update("""
				UPDATE alumno_deporte SET grado_id = ?, fecha_grado = '2026-03-12'
				WHERE alumno_id = ? AND disciplina_id = ?""", gAmarillo, tutelado, e.disciplina);
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
				  organizacion_id, fecha_alta, fecha_alta_inicial, disciplina_id)
				VALUES (TRUE, FALSE, ?, 0, 0, 0, ?, '2024-09-01', '2024-09-01', ?)""", tutelado, e.org, kickboxing);
		// aGrupoA (gA y gB): Rojo en taekwondo, su siguiente es Negro.
		jdbc.update("UPDATE alumno_deporte SET grado_id = ? WHERE alumno_id = ? AND disciplina_id = ?", gRojo,
				e.aGrupoA, e.disciplina);
		deBaja = e.alumno("E71BAJA" + e.marca, e.sA, false);
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", e.gA, deBaja);
		alumnoGA2 = e.alumno("E71GA2" + e.marca, e.sA, true);
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", e.gA2, alumnoGA2);

		tProfA = e.token(jwtService, usuarios, profA);
		tProfB = e.token(jwtService, usuarios, profB);
		tProfRP = e.token(jwtService, usuarios, profRP);
		tProfFP = e.token(jwtService, usuarios, profFP);
		tGestorProf = e.token(jwtService, usuarios, gestorProf);
		tProfSinGrupos = e.token(jwtService, usuarios, profSinGrupos);
		tGestor = e.token(jwtService, usuarios, e.gestor);
		tPropietario = e.token(jwtService, usuarios, propietario);
		tProfOtra = otra.token(jwtService, usuarios, profOtra);
	}

	@AfterEach
	void sinFugas() {
		assertFalse(TenantContext.get() != null, "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ mis grupos

	@Test
	void misGrupos_soloLosSuyos_conSedeRolDisciplinaYAlumnosActivos() throws Exception {
		JsonNode grupos = leer(get("/api/profesor/grupos"), tProfA, 200);
		assertEquals(Set.of(e.gA, e.gSin), ids(grupos, "id"), "solo gA y gSin: " + grupos);
		JsonNode gA = fila(grupos, "id", e.gA);
		assertEquals("IT E6 gA " + e.marca, gA.get("nombre").asText());
		assertEquals("TITULAR", gA.get("rol").asText());
		assertEquals(e.sA, gA.get("sede").get("id").asLong());
		assertEquals("IT E6 A " + e.marca, gA.get("sede").get("nombre").asText());
		assertEquals("TAEKWONDO", gA.get("disciplina").get("codigo").asText());
		assertEquals("Taekwondo IT", gA.get("disciplina").get("nombre").asText());
		// gA: aGrupoA, aSinSede y el tutelado; el de baja no cuenta.
		assertEquals(3, gA.get("alumnosActivos").asLong());
		JsonNode gSin = fila(grupos, "id", e.gSin);
		assertEquals("AYUDANTE", gSin.get("rol").asText());
		assertTrue(gSin.get("sede").isNull(), "grupo sin sede");
		assertEquals(0, gSin.get("alumnosActivos").asLong());
		assertEquals(Set.of("id", "nombre", "sede", "rol", "alumnosActivos", "disciplina"), campos(gA));

		assertEquals(Set.of(e.gB), ids(leer(get("/api/profesor/grupos"), tProfB, 200), "id"));
		assertEquals(Set.of(otra.gA), ids(leer(get("/api/profesor/grupos"), tProfOtra, 200), "id"),
				"otra escuela, solo el suyo");
		assertEquals(200, estado(head("/api/profesor/grupos"), tProfA));
	}

	@Test
	void misGrupos_sinGrupos_listaVacia() throws Exception {
		assertEquals(0, leer(get("/api/profesor/grupos"), tProfSinGrupos, 200).size());
	}

	// ------------------------------------------------------------------ alumnos de un grupo

	@Test
	void alumnosDelGrupo_activosConDeportesGradoYApto_sinDatosSensibles() throws Exception {
		JsonNode alumnos = leer(get("/api/profesor/grupos/" + e.gA + "/alumnos"), tProfA, 200);
		assertEquals(List.of(tutelado, e.aGrupoA, e.aSinSede), idsEnOrden(alumnos, "id"),
				"activos, por apellidos y nombre; sin el de baja: " + alumnos);
		JsonNode lucia = fila(alumnos, "id", tutelado);
		assertEquals(Set.of("id", "nombre", "apellidos", "deportes"), campos(lucia));
		assertEquals("LUCIA", lucia.get("nombre").asText());
		assertEquals("AAA FICTICIA", lucia.get("apellidos").asText());
		JsonNode deportes = lucia.get("deportes");
		assertEquals(2, deportes.size(), "taekwondo y kickboxing activos: " + deportes);
		JsonNode tkd = fila(deportes, "deporte", "TAEKWONDO");
		assertEquals(Set.of("deporte", "deporteNombre", "grado", "fechaGrado", "apto", "delGrupo"), campos(tkd));
		assertEquals("AMARILLO", tkd.get("grado").get("codigo").asText());
		assertEquals("Amarillo IT", tkd.get("grado").get("nombre").asText());
		assertEquals("2026-03-12", tkd.get("fechaGrado").asText());
		assertTrue(tkd.get("apto").asBoolean());
		assertTrue(tkd.get("delGrupo").asBoolean());
		JsonNode kick = fila(deportes, "deporte", "KICKBOXING");
		assertTrue(kick.get("grado").isNull());
		assertFalse(kick.get("apto").asBoolean());
		assertFalse(kick.get("delGrupo").asBoolean(), "gA es de taekwondo");
		assertSinSensibles(alumnos);

		// El alumno compartido (gA y gB) sale en el grupo de cada profesor.
		assertTrue(ids(leer(get("/api/profesor/grupos/" + e.gB + "/alumnos"), tProfB, 200), "id").contains(e.aGrupoA));
		assertEquals(200, estado(head("/api/profesor/grupos/" + e.gA + "/alumnos"), tProfA));
	}

	@Test
	void grupoDeOtroProfesor_403() throws Exception {
		assertEquals(403, estado(get("/api/profesor/grupos/" + e.gB + "/alumnos"), tProfA));
		assertEquals(403, estado(get("/api/profesor/grupos/" + e.gA + "/alumnos"), tProfB));
		assertEquals(403, estado(head("/api/profesor/grupos/" + e.gB + "/alumnos"), tProfA));
		assertEquals(403, estado(get("/api/profesor/grupos/" + e.gA + "/alumnos"), tProfSinGrupos));
	}

	@Test
	void grupoDeOtraEscuela_403() throws Exception {
		// Mismo código para «de otra escuela» que para «no existe»: no se revela la existencia.
		assertEquals(403, estado(get("/api/profesor/grupos/" + otra.gA + "/alumnos"), tProfA));
		assertEquals(403, estado(get("/api/profesor/grupos/" + e.gA + "/alumnos"), tProfOtra));
		assertEquals(403, estado(get("/api/profesor/grupos/999999999/alumnos"), tProfA));
	}

	// ------------------------------------------------------------------ ficha reducida

	@Test
	void ficha_reducidaConEdadGruposCompartidosContactoYDeportes() throws Exception {
		JsonNode ficha = leer(get("/api/profesor/alumnos/" + tutelado), tProfA, 200);
		assertEquals(tutelado, ficha.get("id").asLong());
		assertEquals("LUCIA", ficha.get("nombre").asText());
		assertEquals(Period.between(NACIMIENTO, LocalDate.now()).getYears(), ficha.get("edad").asInt());
		assertEquals(List.of(e.gA), idsEnOrden(ficha.get("grupos"), "id"));
		assertEquals(e.sA, ficha.get("grupos").get(0).get("sede").get("id").asLong());

		JsonNode contacto = ficha.get("contacto");
		assertEquals(3, contacto.size(), "dos teléfonos del alumno y el de la madre; el padre no tiene: " + contacto);
		assertEquals(List.of("600111001", "600111002", "600222001"), textos(contacto, "telefono"));
		assertEquals("ALUMNO", contacto.get(0).get("tipo").asText());
		assertTrue(contacto.get(0).get("parentesco").isNull());
		JsonNode madre = contacto.get(2);
		assertEquals("TUTOR", madre.get("tipo").asText());
		assertEquals("MARIA", madre.get("nombre").asText());
		assertEquals("FICTICIA", madre.get("apellidos").asText());
		assertEquals("MADRE", madre.get("parentesco").asText());

		JsonNode tkd = fila(ficha.get("deportes"), "deporte", "TAEKWONDO");
		assertEquals("AMARILLO", tkd.get("grado").get("codigo").asText());
		assertEquals("Amarillo IT", tkd.get("grado").get("nombre").asText());
		assertEquals("2026-03-12", tkd.get("fechaGrado").asText());
		assertTrue(tkd.get("apto").asBoolean());
		assertTrue(tkd.get("deMisGrupos").asBoolean());
		assertTrue(tkd.get("admitePases").asBoolean());
		assertEquals("ROJO", tkd.get("siguienteGrado").get("codigo").asText());
		assertEquals("Rojo IT", tkd.get("siguienteGrado").get("nombre").asText());
		assertTrue(tkd.get("pideRojoBordado").asBoolean(), "legado y siguiente ROJO, como en gestión");
		JsonNode kick = fila(ficha.get("deportes"), "deporte", "KICKBOXING");
		assertFalse(kick.get("deMisGrupos").asBoolean(), "ningún grupo compartido es de kickboxing (§11, 2)");
		assertFalse(kick.get("admitePases").asBoolean());
		assertTrue(kick.get("siguienteGrado").isNull());
		assertFalse(kick.get("pideRojoBordado").asBoolean());

		// Alumno compartido: cada profesor ve solo los grupos que comparte con él. Rojo → Negro: sin bordado.
		JsonNode compartidoA = leer(get("/api/profesor/alumnos/" + e.aGrupoA), tProfA, 200);
		assertEquals(List.of(e.gA), idsEnOrden(compartidoA.get("grupos"), "id"));
		JsonNode rojo = fila(compartidoA.get("deportes"), "deporte", "TAEKWONDO");
		assertEquals("NEGRO_1_DAN", rojo.get("siguienteGrado").get("codigo").asText());
		assertTrue(rojo.get("admitePases").asBoolean());
		assertFalse(rojo.get("apto").asBoolean(), "admitePases no mira el apto: va aparte");
		assertFalse(rojo.get("pideRojoBordado").asBoolean());
		assertEquals(List.of(e.gB), idsEnOrden(leer(get("/api/profesor/alumnos/" + e.aGrupoA), tProfB, 200)
				.get("grupos"), "id"));
		assertEquals(200, estado(head("/api/profesor/alumnos/" + tutelado), tProfA));
	}

	@Test
	void fichaSinNifEmailDireccion() throws Exception {
		MvcResult r = peticion(get("/api/profesor/alumnos/" + tutelado), tProfA);
		assertEquals(200, r.getResponse().getStatus());
		String cuerpo = r.getResponse().getContentAsString();
		JsonNode ficha = json.readTree(cuerpo);
		assertEquals(Set.of("id", "nombre", "apellidos", "edad", "grupos", "contacto", "deportes"), campos(ficha),
				"claves de la ficha");
		assertEquals(Set.of("id", "nombre", "sede"), campos(ficha.get("grupos").get(0)));
		assertEquals(Set.of("id", "nombre"), campos(ficha.get("grupos").get(0).get("sede")));
		for (JsonNode c : ficha.get("contacto")) {
			assertEquals(Set.of("tipo", "nombre", "apellidos", "parentesco", "telefono"), campos(c));
		}
		for (JsonNode d : ficha.get("deportes")) {
			assertEquals(Set.of("deporte", "deporteNombre", "grado", "fechaGrado", "apto", "deMisGrupos", "admitePases",
					"siguienteGrado", "pideRojoBordado"), campos(d));
		}
		assertEquals(Set.of("codigo", "nombre"),
				campos(fila(ficha.get("deportes"), "deporte", "TAEKWONDO").get("grado")));
		assertSinSensibles(ficha);
	}

	@Test
	void alumnoDeOtroGrupo_403() throws Exception {
		assertEquals(403, estado(get("/api/profesor/alumnos/" + e.aB), tProfA), "aB solo está en gB");
		assertEquals(403, estado(get("/api/profesor/alumnos/" + e.aA), tProfA), "aA no está en ningún grupo");
		assertEquals(403, estado(get("/api/profesor/alumnos/" + tutelado), tProfB));
		assertEquals(403, estado(head("/api/profesor/alumnos/" + e.aB), tProfA));
		assertEquals(403, estado(get("/api/profesor/alumnos/" + tutelado), tProfSinGrupos));
		assertEquals(403, estado(get("/api/profesor/alumnos/999999999"), tProfA));
	}

	@Test
	void alumnoDeOtraEscuela_403() throws Exception {
		assertEquals(403, estado(get("/api/profesor/alumnos/" + otra.aGrupoA), tProfA));
		assertEquals(403, estado(get("/api/profesor/alumnos/" + e.aGrupoA), tProfOtra));
	}

	@Test
	void alumnoDeBaja_404_yFueraDeLaLista() throws Exception {
		assertEquals(404, estado(get("/api/profesor/alumnos/" + deBaja), tProfA), "D7-13");
		assertFalse(ids(leer(get("/api/profesor/grupos/" + e.gA + "/alumnos"), tProfA, 200), "id").contains(deBaja));
	}

	// ------------------------------------------------------------------ cuentas mixtas y gestión

	@Test
	void responsableMasProfesorNoVeSusSedes() throws Exception {
		// Responsable de la sede A y profesor de gB (sede B): por estas rutas, solo gB.
		assertEquals(Set.of(e.gB), ids(leer(get("/api/profesor/grupos"), tProfRP, 200), "id"));
		assertEquals(403, estado(get("/api/profesor/grupos/" + e.gA + "/alumnos"), tProfRP), "gA es de su sede");
		assertEquals(403, estado(get("/api/profesor/grupos/" + e.gA2 + "/alumnos"), tProfRP));
		assertEquals(403, estado(get("/api/profesor/alumnos/" + e.aA), tProfRP), "aA es de su sede, no de su grupo");
		assertEquals(403, estado(get("/api/profesor/alumnos/" + tutelado), tProfRP));
		assertEquals(Set.of(e.aGrupoA, e.aB),
				ids(leer(get("/api/profesor/grupos/" + e.gB + "/alumnos"), tProfRP, 200), "id"));
		assertEquals(200, estado(get("/api/profesor/alumnos/" + e.aB), tProfRP));
		// Y la sesión 184 sigue cerrada en las rutas de gestión de su sede.
		assertEquals(403, estado(get("/api/alumnos/" + e.aA), tProfRP));
		assertEquals(403, estado(get("/api/grupos/" + e.gA), tProfRP));
	}

	@Test
	void familiaMasProfesorUsaSusGrupos() throws Exception {
		assertEquals(Set.of(e.gA2), ids(leer(get("/api/profesor/grupos"), tProfFP, 200), "id"));
		assertEquals(Set.of(alumnoGA2), ids(leer(get("/api/profesor/grupos/" + e.gA2 + "/alumnos"), tProfFP, 200), "id"));
		assertEquals(200, estado(get("/api/profesor/alumnos/" + alumnoGA2), tProfFP));
		assertEquals(403, estado(get("/api/profesor/grupos/" + e.gA + "/alumnos"), tProfFP));
		assertEquals(403, estado(get("/api/profesor/alumnos/" + familiaPropioFP), tProfFP),
				"su alumno de familia no está en sus grupos: no entra por la ficha del profesor");
		assertEquals(403, estado(get("/api/profesor/alumnos/" + tutelado), tProfFP));
	}

	@Test
	void sesionSoloProfesorFueraDeListaBlanca_403() throws Exception {
		assertEquals(200, estado(get("/api/profesor/grupos"), tProfA), "dentro de la lista blanca");
		for (String ruta : List.of("/api/alumnos", "/api/grupos", "/api/grupos/" + e.gA, "/api/catalogo",
				"/api/grados", "/api/turnos")) {
			assertEquals(403, estado(get(ruta), tProfA), ruta);
		}
		assertEquals(403, estado(get("/api/profesorx"), tProfA), "el prefijo no se estira");
	}

	@Test
	void profesorNoEntraEnRutasDeGestion_403() throws Exception {
		for (String token : List.of(tProfA, tProfFP, tProfRP)) {
			// Un alumno de SUS grupos: ni así entra por las rutas de gestión.
			long alumno = token.equals(tProfRP) ? e.aB : token.equals(tProfFP) ? alumnoGA2 : tutelado;
			for (MockHttpServletRequestBuilder peticion : List.of(get("/api/alumnos"),
					get("/api/alumnos/" + alumno), get("/api/alumnos/" + alumno + "/tutores"),
					get("/api/alumnos/" + alumno + "/salud"), get("/api/informes/asistencia?year=2026&month=10"),
					put("/api/alumnos/" + alumno + "/deportes/TAEKWONDO/grado").contentType(MediaType.APPLICATION_JSON)
							.content("{\"nuevoGrado\":\"ROJO\"}"))) {
				MvcResult r = peticion(peticion, token);
				assertEquals(403, r.getResponse().getStatus(),
						r.getRequest().getMethod() + " " + r.getRequest().getRequestURI());
			}
		}
		assertEquals(gAmarillo, jdbc.queryForObject(
				"SELECT grado_id FROM alumno_deporte WHERE alumno_id = ? AND disciplina_id = ?", Long.class, tutelado,
				e.disciplina), "el grado no cambió");
	}

	@Test
	void gestionNoUsaLasRutasDelProfesor_403() throws Exception {
		for (String token : List.of(tGestor, tPropietario, tGestorProf)) {
			assertEquals(403, estado(get("/api/profesor/grupos"), token));
			assertEquals(403, estado(get("/api/profesor/grupos/" + e.gA + "/alumnos"), token));
			assertEquals(403, estado(get("/api/profesor/alumnos/" + tutelado), token));
		}
	}

	// ------------------------------------------------------------------ utilidades

	private void sistemaDeGrados() {
		jdbc.update("INSERT INTO sistema_grados (organizacion_id, nombre, plantilla_origen) VALUES (?, 'Sistema IT E71', NULL)",
				e.org);
		long sistema = jdbc.queryForObject("SELECT id FROM sistema_grados WHERE organizacion_id = ?", Long.class, e.org);
		jdbc.update("""
				INSERT INTO grado (organizacion_id, sistema_grados_id, codigo, nombre, orden, color_1, color_2, aplica_a)
				VALUES (?, ?, 'BLANCO', 'Blanco IT', 1, NULL, NULL, 'TODOS'),
				       (?, ?, 'AMARILLO', 'Amarillo IT', 2, NULL, NULL, 'TODOS'),
				       (?, ?, 'ROJO', 'Rojo IT', 3, NULL, NULL, 'TODOS'),
				       (?, ?, 'NEGRO_1_DAN', 'Negro IT', 4, NULL, NULL, 'TODOS')""",
				e.org, sistema, e.org, sistema, e.org, sistema, e.org, sistema);
		jdbc.update("UPDATE disciplina SET sistema_grados_id = ? WHERE id = ?", sistema, e.disciplina);
		gAmarillo = grado(sistema, "AMARILLO");
		gRojo = grado(sistema, "ROJO");
	}

	private long grado(long sistema, String codigo) {
		return jdbc.queryForObject("SELECT id FROM grado WHERE sistema_grados_id = ? AND codigo = ?", Long.class,
				sistema, codigo);
	}

	private long soloProfesor(EscuelaConSedesDePrueba x, String nombre) {
		long id = x.cuenta(usuarios, nombre);
		membresia(x, id, "PROFESOR");
		return id;
	}

	private void membresia(EscuelaConSedesDePrueba x, long usuario, String rol) {
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", usuario, x.org, rol);
	}

	private void responsableDeSede(EscuelaConSedesDePrueba x, long usuario, long sede) {
		membresia(x, usuario, "RESPONSABLE_SEDE");
		long membresia = jdbc.queryForObject("""
				SELECT id FROM membresia WHERE usuario_id = ? AND organizacion_id = ? AND rol = 'RESPONSABLE_SEDE'""",
				Long.class, usuario, x.org);
		jdbc.update("INSERT INTO membresia_sede (membresia_id, sede_id) VALUES (?, ?)", membresia, sede);
	}

	private void asignar(EscuelaConSedesDePrueba x, long profesor, long grupo, String rol) {
		jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) VALUES (?, ?, ?, ?)",
				x.org, profesor, grupo, rol);
	}

	/** Ninguna clave ni valor sensible en todo el árbol (D7-12). */
	private static void assertSinSensibles(JsonNode raiz) {
		Set<String> prohibidas = Set.of("nif", "email", "direccion", "observaciones", "tarifa", "cuantiaTarifa",
				"fechaNacimiento", "cobros", "productosAlumno", "localidad", "codigoPostal", "salud", "responsableLegal");
		List<JsonNode> pendientes = new ArrayList<>(List.of(raiz));
		while (!pendientes.isEmpty()) {
			JsonNode nodo = pendientes.remove(pendientes.size() - 1);
			for (Iterator<String> it = nodo.fieldNames(); it.hasNext();) {
				String campo = it.next();
				assertFalse(prohibidas.contains(campo), "clave sensible '" + campo + "' en " + nodo);
			}
			nodo.forEach(pendientes::add);
			if (nodo.isTextual()) {
				String v = nodo.asText();
				for (String sensible : List.of(NIF_ALUMNO, EMAIL_ALUMNO, DIRECCION_ALUMNO, OBSERVACIONES, NIF_TUTOR,
						EMAIL_TUTOR, NACIMIENTO.toString())) {
					assertFalse(v.contains(sensible), "valor sensible '" + sensible + "' en la respuesta");
				}
			}
		}
	}

	private JsonNode leer(MockHttpServletRequestBuilder peticion, String token, int esperado) throws Exception {
		MvcResult r = peticion(peticion, token);
		assertEquals(esperado, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString());
	}

	private int estado(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return peticion(peticion, token).getResponse().getStatus();
	}

	private MvcResult peticion(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
	}

	private static Set<String> campos(JsonNode nodo) {
		Set<String> campos = new HashSet<>();
		for (Iterator<String> it = nodo.fieldNames(); it.hasNext();) {
			campos.add(it.next());
		}
		return campos;
	}

	private static List<String> textos(JsonNode lista, String campo) {
		List<String> textos = new ArrayList<>();
		lista.forEach(n -> textos.add(n.get(campo).asText()));
		return textos;
	}

	private static Set<Long> ids(JsonNode lista, String campo) {
		return new HashSet<>(idsEnOrden(lista, campo));
	}

	private static List<Long> idsEnOrden(JsonNode lista, String campo) {
		List<Long> ids = new ArrayList<>();
		lista.forEach(n -> ids.add(n.get(campo).asLong()));
		return ids;
	}

	private static JsonNode fila(JsonNode lista, String campo, long valor) {
		for (JsonNode n : lista) {
			if (n.get(campo).asLong() == valor) {
				return n;
			}
		}
		throw new AssertionError("Sin fila con " + campo + " = " + valor + " en " + lista);
	}

	private static JsonNode fila(JsonNode lista, String campo, String valor) {
		for (JsonNode n : lista) {
			if (valor.equals(n.get(campo).asText())) {
				return n;
			}
		}
		throw new AssertionError("Sin fila con " + campo + " = " + valor + " en " + lista);
	}
}
