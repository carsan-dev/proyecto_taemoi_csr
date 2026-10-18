package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.HashSet;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * R2-P4s (decisión 124) contra MySQL: en los turnos que ve el responsable de sede ({@code GET /api/turnos},
 * {@code GET /api/turnos/{id}}, la respuesta de {@code PUT /api/turnos/{id}} y {@code GET /api/grupos/{g}/turnos}) solo
 * van incrustados los alumnos de su alcance, con el criterio del listado de alumnos (D9: sede principal en sus sedes
 * o alumno de algún grupo de sus sedes). La gestión (administrador y propietario) ve todos, como antes. El PUT no
 * toca los alumnos del turno: reenviar la lista filtrada no borra a los que el responsable no ve, ni una lista ajena
 * los añade. Datos sintéticos en una escuela nueva ({@link EscuelaConSedesDePrueba}).
 *
 * <p>En el turno {@code tA} (grupo gA, sede A) están:
 * <ul>
 * <li>aA: sede principal A → lo ve A.</li>
 * <li>aGrupoA: sede principal B, en gA y gB → lo ve A por grupo (D9).</li>
 * <li>aSinSede: sin sede principal, en gA → lo ve A por grupo.</li>
 * <li>aB: sede principal B, solo en gB → fuera del alcance de A.</li>
 * <li>aNada: sin sede ni grupo → fuera del alcance de A.</li>
 * </ul>
 */
@SpringBootTest(properties = {
		"spring.jpa.properties.hibernate.session_factory.statement_inspector=com.taemoi.project.integracion.ContadorSentencias",
		// Mismas propiedades que ResumenGruposTurnosJsonDoradoIntegrationTest: comparten contexto de Spring.
		"app.base.url=http://localhost:8080" })
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResponsableSedeAlumnosDeTurnosIntegrationTest extends MySqlIntegrationTest {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	String tokenA;
	String tokenAC;
	String tokenGestor;
	String tokenPropietario;
	Set<Long> deA;
	Set<Long> todos;
	long tA2;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-r2p4s");
		// aA ya está en tA; aGrupoA, aB y aA ya practican taekwondo (el deporte del grupo).
		for (long alumno : List.of(e.aGrupoA, e.aSinSede, e.aB, e.aNada)) {
			e.enTurno(e.tA, alumno);
		}
		e.taekwondo(e.aSinSede, false);
		e.taekwondo(e.aNada, false);
		// Un segundo turno de la sede A, para comprobar que el filtro es una consulta para toda la respuesta.
		jdbc.update("""
				INSERT INTO turno (dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id)
				VALUES ('Lunes', '19:00', '20:00', NULL, ?, ?)""", e.gA, e.org);
		tA2 = jdbc.queryForObject("SELECT MAX(id) FROM turno WHERE organizacion_id = ?", Long.class, e.org);
		e.enTurno(tA2, e.aA);
		e.enTurno(tA2, e.aB);
		long propietario = e.cuenta(usuarios, "propietario");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROPIETARIO')",
				propietario, e.org);
		tokenA = e.token(jwtService, usuarios, e.respA);
		tokenAC = e.token(jwtService, usuarios, e.respAC);
		tokenGestor = e.token(jwtService, usuarios, e.gestor);
		tokenPropietario = e.token(jwtService, usuarios, propietario);
		deA = Set.of(e.aA, e.aGrupoA, e.aSinSede);
		todos = Set.of(e.aA, e.aGrupoA, e.aSinSede, e.aB, e.aNada);
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	@Test
	void listado_responsableSoloVeLosAlumnosDeSuAlcance() throws Exception {
		assertEquals(deA, alumnos(turnoDe(leer("/api/turnos", tokenA), e.tA)));
		assertEquals(deA, alumnos(turnoDe(leer("/api/turnos", tokenAC), e.tA)), "con dos sedes (A y C), igual");
		assertEquals(todos, alumnos(turnoDe(leer("/api/turnos", tokenGestor), e.tA)));
		assertEquals(todos, alumnos(turnoDe(leer("/api/turnos", tokenPropietario), e.tA)));
		// El turno de B lo sigue viendo entero la gestión.
		assertEquals(Set.of(e.aB), alumnos(turnoDe(leer("/api/turnos", tokenGestor), e.tB)));
	}

	@Test
	void porId_responsableSoloVeLosAlumnosDeSuAlcance() throws Exception {
		assertEquals(deA, alumnos(leer("/api/turnos/" + e.tA, tokenA)));
		assertEquals(todos, alumnos(leer("/api/turnos/" + e.tA, tokenGestor)));
		assertEquals(todos, alumnos(leer("/api/turnos/" + e.tA, tokenPropietario)));
		assertEquals(404, peticion(get("/api/turnos/999999999"), tokenGestor).getResponse().getStatus());
	}

	@Test
	void turnosDelGrupo_responsableSoloVeLosAlumnosDeSuAlcance() throws Exception {
		String ruta = "/api/grupos/" + e.gA + "/turnos";
		assertEquals(deA, alumnos(turnoDe(leer(ruta, tokenA), e.tA)));
		assertEquals(todos, alumnos(turnoDe(leer(ruta, tokenGestor), e.tA)));
		assertEquals(todos, alumnos(turnoDe(leer(ruta, tokenPropietario), e.tA)));
	}

	/** Sin N+1: una sola consulta de alcance para todos los turnos de la respuesta; ninguna para la gestión. */
	@Test
	void filtrado_unaConsultaParaTodaLaRespuesta() throws Exception {
		for (String ruta : List.of("/api/turnos", "/api/grupos/" + e.gA + "/turnos")) {
			ContadorSentencias.Conteo<JsonNode> responsable = ContadorSentencias.contar(() -> leer(ruta, tokenA));
			assertEquals(deA, alumnos(turnoDe(responsable.resultado(), e.tA)), ruta);
			assertEquals(Set.of(e.aA), alumnos(turnoDe(responsable.resultado(), tA2)), ruta);
			assertEquals(1, consultasDeAlcance(responsable), ruta + ": " + responsable.sentencias());

			ContadorSentencias.Conteo<JsonNode> gestion = ContadorSentencias.contar(() -> leer(ruta, tokenGestor));
			assertEquals(Set.of(e.aA, e.aB), alumnos(turnoDe(gestion.resultado(), tA2)), ruta);
			assertEquals(0, consultasDeAlcance(gestion), ruta + ": " + gestion.sentencias());
		}
	}

	/** La consulta de ids del alcance (D9): la única que filtra alumnos por sede principal. */
	private static long consultasDeAlcance(ContadorSentencias.Conteo<?> conteo) {
		return conteo.sentencias().stream().filter(sql -> sql.contains("sede_principal_id in")).count();
	}

	/**
	 * El PUT no toca los alumnos del turno: el responsable reenvía la lista filtrada que recibió (o una con alumnos
	 * ajenos) y en la base siguen exactamente los mismos; la respuesta va filtrada para él y entera para la gestión.
	 */
	@Test
	void put_noQuitaNiAnadeAlumnosYFiltraLaRespuesta() throws Exception {
		ObjectNode recibido = (ObjectNode) leer("/api/turnos/" + e.tA, tokenA);
		assertEquals(deA, alumnos(recibido));

		JsonNode respuesta = escribir(e.tA, recibido.toString(), tokenA);
		assertEquals(deA, alumnos(respuesta));
		assertEquals(todos, enBase(e.tA), "reenviar la lista filtrada no borra a los que no ve");

		// Una lista con solo un alumno ajeno (y sin los suyos): ni lo añade ni quita a nadie.
		ObjectNode ajeno = recibido.deepCopy();
		ArrayNode lista = ajeno.putArray("alumnos");
		lista.addObject().put("id", e.aB);
		lista.addObject().put("id", e.aInactivoA);
		assertEquals(deA, alumnos(escribir(e.tA, ajeno.toString(), tokenA)));
		assertEquals(todos, enBase(e.tA));

		// Sin lista: igual.
		ObjectNode sinLista = recibido.deepCopy();
		sinLista.remove("alumnos");
		assertEquals(deA, alumnos(escribir(e.tA, sinLista.toString(), tokenA)));
		assertEquals(todos, enBase(e.tA));

		// La gestión: respuesta con todos, y tampoco cambia los alumnos.
		assertEquals(todos, alumnos(escribir(e.tA, recibido.toString(), tokenGestor)));
		assertEquals(todos, alumnos(escribir(e.tA, recibido.toString(), tokenPropietario)));
		assertEquals(todos, enBase(e.tA));
	}

	// ------------------------------------------------------------------ utilidades

	private JsonNode escribir(long turno, String cuerpo, String token) throws Exception {
		MvcResult r = peticion(put("/api/turnos/" + turno).contentType(MediaType.APPLICATION_JSON).content(cuerpo),
				token);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString());
	}

	private Set<Long> enBase(long turno) {
		return new HashSet<>(
				jdbc.queryForList("SELECT alumno_id FROM alumno_turno WHERE turno_id = ?", Long.class, turno));
	}

	private static JsonNode turnoDe(JsonNode lista, long turno) {
		for (JsonNode t : lista) {
			if (t.get("id").asLong() == turno) {
				return t;
			}
		}
		throw new AssertionError("Falta el turno " + turno + " en " + lista);
	}

	private static Set<Long> alumnos(JsonNode turno) {
		JsonNode lista = turno.get("alumnos");
		assertNotNull(lista, "el turno lleva la lista de alumnos: " + turno);
		Set<Long> ids = new HashSet<>();
		lista.forEach(a -> ids.add(a.get("id").asLong()));
		assertEquals(lista.size(), ids.size(), "sin repetidos");
		return ids;
	}

	private MvcResult peticion(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
	}

	private JsonNode leer(String ruta, String token) throws Exception {
		MvcResult r = peticion(get(ruta), token);
		assertEquals(200, r.getResponse().getStatus(), ruta + ": " + r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString());
	}
}
