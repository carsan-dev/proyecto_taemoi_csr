package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;

import java.time.Clock;
import java.time.LocalDate;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza E8-1 contra MySQL (D8-14): {@code GET /api/auth/permisos}, la fuente única de menús y guards del frontend.
 * <ul>
 * <li>Roles de la escuela activa; en la sesión solo-profesor (también R+Pr) solo {@code PROFESOR} y sin las sedes del
 * responsable; en la solo-plataforma, roles vacíos.</li>
 * <li>Sedes visibles: con gestión, todas las de la escuela (también inactivas); el responsable, las suyas; el resto,
 * ninguna. {@code variasSedes} = más de una activa. Nunca sedes de otra escuela.</li>
 * <li>Capacidades MIEMBROS, DAR_GESTION, SEDES, ASIGNAR_PROFESORES, MIS_GRUPOS y MI_CAJA (decisión 217: MIS_GRUPOS en
 * gestión y familia solo con grupos asignados).</li>
 * </ul>
 * Dos escuelas sintéticas ({@link EscuelaConSedesDePrueba}): en la primera A y B activas y C inactiva; en la otra solo A
 * activa. Datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PermisosSesionIntegrationTest extends MySqlIntegrationTest {

	private static final String RUTA = "/api/auth/permisos";
	private static final List<String> GESTION_P = List.of("MIEMBROS", "DAR_GESTION", "SEDES", "ASIGNAR_PROFESORES");
	private static final List<String> GESTION_G = List.of("MIEMBROS", "SEDES", "ASIGNAR_PROFESORES");
	private static final List<String> PROFESOR = List.of("MIS_GRUPOS", "MI_CAJA");

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;
	@Autowired Clock reloj;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	String tPropietario, tGestor, tFamilia, tRespA, tRespAC, tFamiliaProf, tSoloProf, tSoloProfSin, tRespProf,
			tPropProf, tGestorProfSin, tPlataforma, tPropietarioOtra;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e8-1p");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e8-1p-otra");
		// La otra escuela con una sola sede activa (A): B y C cerradas.
		jdbc.update("UPDATE sede SET activa = FALSE WHERE id = ?", otra.sB);

		long propietario = e.cuenta(usuarios, "propietario");
		membresia(e, propietario, "PROPIETARIO");
		long familia = e.cuentaFamilia(usuarios, "familia");
		long familiaProf = e.cuentaFamilia(usuarios, "familiaprof");
		membresia(e, familiaProf, "PROFESOR");
		asignar(e, familiaProf, e.gA2);
		long soloProf = e.cuenta(usuarios, "soloprof");
		membresia(e, soloProf, "PROFESOR");
		asignar(e, soloProf, e.gA);
		long soloProfSin = e.cuenta(usuarios, "soloprofsin");
		membresia(e, soloProfSin, "PROFESOR");
		long respProf = e.cuenta(usuarios, "respprof");
		responsableDeSede(e, respProf, e.sA);
		membresia(e, respProf, "PROFESOR");
		asignar(e, respProf, e.gB);
		long propProf = e.cuenta(usuarios, "propprof");
		membresia(e, propProf, "PROPIETARIO");
		membresia(e, propProf, "PROFESOR");
		asignar(e, propProf, e.gA);
		long gestorProfSin = e.cuenta(usuarios, "gestorprofsin");
		membresia(e, gestorProfSin, "ADMINISTRADOR");
		membresia(e, gestorProfSin, "PROFESOR");
		long plataforma = e.cuenta(usuarios, "plataforma");
		jdbc.update("UPDATE usuario SET es_admin_plataforma = TRUE WHERE id = ?", plataforma);
		long propietarioOtra = otra.cuenta(usuarios, "propietario");
		membresia(otra, propietarioOtra, "PROPIETARIO");

		tPropietario = e.token(jwtService, usuarios, propietario);
		tGestor = e.token(jwtService, usuarios, e.gestor);
		tFamilia = e.token(jwtService, usuarios, familia);
		tRespA = e.token(jwtService, usuarios, e.respA);
		tRespAC = e.token(jwtService, usuarios, e.respAC);
		tFamiliaProf = e.token(jwtService, usuarios, familiaProf);
		tSoloProf = e.token(jwtService, usuarios, soloProf);
		tSoloProfSin = e.token(jwtService, usuarios, soloProfSin);
		tRespProf = e.token(jwtService, usuarios, respProf);
		tPropProf = e.token(jwtService, usuarios, propProf);
		tGestorProfSin = e.token(jwtService, usuarios, gestorProfSin);
		// Solo plataforma: sin membresía, solo en la organización por defecto (fase3-sedes-permisos.md §4).
		Usuario cuentaPlataforma = TenantContext.calcularCon(EscuelaConSedesDePrueba.ORG1,
				() -> usuarios.findById(plataforma).orElseThrow());
		tPlataforma = jwtService.generateToken(cuentaPlataforma, 600_000, EscuelaConSedesDePrueba.ORG1);
		tPropietarioOtra = otra.token(jwtService, usuarios, propietarioOtra);
	}

	@AfterEach
	void sinFugas() {
		assertFalse(TenantContext.get() != null, "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ gestión

	@Test
	void permisos_propietario() throws Exception {
		JsonNode p = permisos(tPropietario);
		assertEquals(Set.of("roles", "esAdminPlataforma", "sedes", "variasSedes", "tieneGrupos", "capacidades",
				"disenoNuevo", "nombreVisible", "cumpleanos"),
				campos(p), "claves del DTO");
		assertEquals("Usuario P.", p.get("nombreVisible").asText(), "8b-T: nombre e inicial del primer apellido");
		assertFalse(p.get("cumpleanos").asBoolean(), "8b-M2: sin ficha propia, nunca cumpleaños");
		assertEquals(List.of("PROPIETARIO"), textos(p.get("roles")));
		assertFalse(p.get("esAdminPlataforma").asBoolean());
		assertEquals(List.of(e.sA, e.sB, e.sC), ids(p.get("sedes")), "todas las de la escuela, por id: " + p);
		JsonNode sedeA = p.get("sedes").get(0);
		assertEquals(Set.of("id", "nombre", "activa"), campos(sedeA));
		assertEquals("IT E6 A " + e.marca, sedeA.get("nombre").asText());
		assertTrue(sedeA.get("activa").asBoolean());
		assertFalse(p.get("sedes").get(2).get("activa").asBoolean(), "C es inactiva y también sale");
		assertTrue(p.get("variasSedes").asBoolean(), "A y B activas");
		assertFalse(p.get("tieneGrupos").asBoolean());
		assertEquals(GESTION_P, textos(p.get("capacidades")));
		assertEquals(200, estado(head(RUTA), tPropietario));
	}

	@Test
	void permisos_gestor() throws Exception {
		JsonNode p = permisos(tGestor);
		assertEquals(List.of("ADMINISTRADOR"), textos(p.get("roles")));
		assertEquals(List.of(e.sA, e.sB, e.sC), ids(p.get("sedes")));
		assertEquals(GESTION_G, textos(p.get("capacidades")), "el gestor no da ni quita gestión (D8)");
		assertFalse(p.get("tieneGrupos").asBoolean());
	}

	@Test
	void permisos_propietarioProfesorConGrupos_misGrupos() throws Exception {
		JsonNode p = permisos(tPropProf);
		assertEquals(List.of("PROPIETARIO", "PROFESOR"), textos(p.get("roles")));
		assertEquals(List.of(e.sA, e.sB, e.sC), ids(p.get("sedes")));
		assertTrue(p.get("tieneGrupos").asBoolean());
		List<String> esperadas = new ArrayList<>(GESTION_P);
		esperadas.addAll(PROFESOR);
		assertEquals(esperadas, textos(p.get("capacidades")));
	}

	@Test
	void permisos_gestorProfesorSinGrupos_sinMisGrupos() throws Exception {
		JsonNode p = permisos(tGestorProfSin);
		assertEquals(List.of("ADMINISTRADOR", "PROFESOR"), textos(p.get("roles")));
		assertFalse(p.get("tieneGrupos").asBoolean());
		List<String> esperadas = new ArrayList<>(GESTION_G);
		esperadas.add("MI_CAJA");
		assertEquals(esperadas, textos(p.get("capacidades")), "sin asignaciones no ve «Mis grupos» (217)");
	}

	// ------------------------------------------------------------------ familia y responsable

	@Test
	void permisos_familia() throws Exception {
		JsonNode p = permisos(tFamilia);
		assertEquals(List.of("FAMILIA"), textos(p.get("roles")));
		assertEquals(0, p.get("sedes").size());
		assertTrue(p.get("variasSedes").asBoolean(), "dato de la escuela");
		assertFalse(p.get("tieneGrupos").asBoolean());
		assertEquals(List.of(), textos(p.get("capacidades")));
	}

	@Test
	void permisos_familiaResponsable_susSedes() throws Exception {
		JsonNode a = permisos(tRespA);
		assertEquals(List.of("RESPONSABLE_SEDE", "FAMILIA"), textos(a.get("roles")));
		assertEquals(List.of(e.sA), ids(a.get("sedes")), "solo la suya");
		assertEquals(List.of(), textos(a.get("capacidades")), "sin pantallas de sedes hasta la E9");
		JsonNode ac = permisos(tRespAC);
		assertEquals(List.of(e.sA, e.sC), ids(ac.get("sedes")));
		assertFalse(ac.get("sedes").get(1).get("activa").asBoolean(), "su sede cerrada sigue saliendo (D5-5)");
	}

	@Test
	void permisos_familiaProfesor_misGruposYMiCaja() throws Exception {
		JsonNode p = permisos(tFamiliaProf);
		assertEquals(List.of("PROFESOR", "FAMILIA"), textos(p.get("roles")));
		assertTrue(p.get("tieneGrupos").asBoolean());
		assertEquals(0, p.get("sedes").size());
		assertEquals(PROFESOR, textos(p.get("capacidades")));
	}

	// ------------------------------------------------------------------ sesión solo-profesor (180 y 184)

	@Test
	void permisos_sesionSoloProfesor_soloProfesorYMisGrupos() throws Exception {
		JsonNode con = permisos(tSoloProf);
		assertEquals(List.of("PROFESOR"), textos(con.get("roles")));
		assertTrue(con.get("tieneGrupos").asBoolean());
		assertEquals(0, con.get("sedes").size());
		assertEquals(PROFESOR, textos(con.get("capacidades")));
		assertEquals(200, estado(head(RUTA), tSoloProf), "HEAD como GET en la lista blanca");

		JsonNode sin = permisos(tSoloProfSin);
		assertEquals(List.of("PROFESOR"), textos(sin.get("roles")));
		assertFalse(sin.get("tieneGrupos").asBoolean());
		assertEquals(PROFESOR, textos(sin.get("capacidades")), "la sesión solo-profesor siempre ve «Mis grupos»");
	}

	@Test
	void permisos_responsableMasProfesor_sinSedes() throws Exception {
		MvcResult r = peticion(RUTA, tRespProf);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		String cuerpo = r.getResponse().getContentAsString();
		JsonNode p = json.readTree(cuerpo);
		assertEquals(List.of("PROFESOR"), textos(p.get("roles")), "como /api/auth/roles: solo PROFESOR");
		assertEquals(0, p.get("sedes").size(), "las sedes del responsable no salen en la sesión 184");
		assertFalse(cuerpo.contains("IT E6 A " + e.marca), cuerpo);
		assertTrue(p.get("tieneGrupos").asBoolean());
		assertEquals(PROFESOR, textos(p.get("capacidades")));
	}

	// ------------------------------------------------------------------ plataforma, anónimo y escuelas

	@Test
	void permisos_soloPlataforma_rolesVacios() throws Exception {
		JsonNode p = permisos(tPlataforma);
		assertEquals(List.of(), textos(p.get("roles")));
		assertTrue(p.get("esAdminPlataforma").asBoolean());
		assertEquals(0, p.get("sedes").size());
		assertFalse(p.get("variasSedes").asBoolean());
		assertFalse(p.get("tieneGrupos").asBoolean());
		assertEquals(List.of(), textos(p.get("capacidades")));
	}

	@Test
	void permisos_anonimo_401() throws Exception {
		assertEquals(401, mvc.perform(get(RUTA)).andReturn().getResponse().getStatus());
		assertEquals(401, mvc.perform(get(RUTA).cookie(new Cookie("jwt", "no-es-un-token"))).andReturn().getResponse()
				.getStatus());
	}

	@Test
	void permisos_unaSedeActiva_variasSedesFalse() throws Exception {
		JsonNode p = permisos(tPropietarioOtra);
		assertEquals(List.of(otra.sA, otra.sB, otra.sC), ids(p.get("sedes")));
		assertFalse(p.get("variasSedes").asBoolean(), "solo A activa");
	}

	@Test
	void permisos_noCruzaEscuelas() throws Exception {
		Set<Long> deE = Set.of(e.sA, e.sB, e.sC);
		Set<Long> deOtra = new HashSet<>(ids(permisos(tPropietarioOtra).get("sedes")));
		assertFalse(deOtra.removeAll(deE), "la otra escuela no ve sedes de esta");
		Set<Long> vistas = new HashSet<>(ids(permisos(tPropietario).get("sedes")));
		assertFalse(vistas.removeAll(Set.of(otra.sA, otra.sB, otra.sC)), "esta escuela no ve sedes de la otra");
	}

	@Test
	void permisos_noSeAudita() throws Exception {
		long antes = jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_evento WHERE endpoint = ?", Long.class, RUTA);
		permisos(tPropietario);
		permisos(tSoloProf);
		assertEquals(antes,
				jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_evento WHERE endpoint = ?", Long.class, RUTA),
				"/api/auth/** no se audita");
	}

	// ------------------------------------------------------------------ cumpleaños (8b-M2)

	@Test
	void permisos_cumpleanos_fichaPropiaQueCumpleHoyEnLaEscuelaActiva_true() throws Exception {
		long cuenta = cuentaFamiliaConFicha("cumple-propio", e, "PROPIO", hoyHaceAnios());
		assertTrue(permisos(e.token(jwtService, usuarios, cuenta)).get("cumpleanos").asBoolean());
	}

	@Test
	void permisos_cumpleanos_fichaPropiaDeOtraEscuela_false() throws Exception {
		long cuenta = cuentaFamiliaConFicha("cumple-otra-org", otra, "PROPIO", hoyHaceAnios());
		assertFalse(permisos(e.token(jwtService, usuarios, cuenta)).get("cumpleanos").asBoolean(),
				"la ficha que cumple hoy es de otra organización; la activa no tiene ninguna");
	}

	@Test
	void permisos_cumpleanos_fichaDeTutor_false() throws Exception {
		long cuenta = cuentaFamiliaConFicha("cumple-tutor", e, "TUTOR", hoyHaceAnios());
		assertFalse(permisos(e.token(jwtService, usuarios, cuenta)).get("cumpleanos").asBoolean(),
				"solo cuenta la relación PROPIO");
	}

	// ------------------------------------------------------------------ utilidades

	/** Hoy según el {@code Clock} del contexto, 8 años atrás (múltiplo de 4: un 29-feb sigue existiendo). */
	private LocalDate hoyHaceAnios() {
		return LocalDate.now(reloj).minusYears(8);
	}

	/** Cuenta con membresía FAMILIA en {@code e} y una sola ficha en {@code escuelaFicha} con esa relación. */
	private long cuentaFamiliaConFicha(String nombre, EscuelaConSedesDePrueba escuelaFicha, String relacion,
			LocalDate nacimiento) {
		long cuenta = e.cuenta(usuarios, nombre);
		membresia(e, cuenta, "FAMILIA");
		if (escuelaFicha != e) {
			// Puerta de entrada de familia: sin ficha en la escuela activa la sesión es 401. Esta no cumple hoy (2010-01-01).
			long deLaActiva = e.alumno("E8M2ACT" + nombre.toUpperCase() + e.marca, null, true);
			jdbc.update("""
					INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen)
					VALUES (?, ?, ?, 'TUTOR', 'MANUAL')""", cuenta, deLaActiva, e.org);
		}
		long ficha = escuelaFicha.alumno("E8M2" + nombre.toUpperCase() + escuelaFicha.marca, null, true);
		jdbc.update("UPDATE alumno SET fecha_nacimiento = ? WHERE id = ?", java.sql.Date.valueOf(nacimiento), ficha);
		jdbc.update("""
				INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen)
				VALUES (?, ?, ?, ?, 'MANUAL')""", cuenta, ficha, escuelaFicha.org, relacion);
		return cuenta;
	}

	private JsonNode permisos(String token) throws Exception {
		MvcResult r = peticion(RUTA, token);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString());
	}

	private int estado(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion,
			String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn().getResponse().getStatus();
	}

	private MvcResult peticion(String ruta, String token) throws Exception {
		return mvc.perform(get(ruta).cookie(new Cookie("jwt", token))).andReturn();
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

	private void asignar(EscuelaConSedesDePrueba x, long profesor, long grupo) {
		jdbc.update("""
				INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol)
				VALUES (?, ?, ?, 'TITULAR')""", x.org, profesor, grupo);
	}

	private static Set<String> campos(JsonNode nodo) {
		Set<String> campos = new HashSet<>();
		for (Iterator<String> it = nodo.fieldNames(); it.hasNext();) {
			campos.add(it.next());
		}
		return campos;
	}

	private static List<String> textos(JsonNode lista) {
		List<String> textos = new ArrayList<>();
		lista.forEach(n -> textos.add(n.asText()));
		return textos;
	}

	private static List<Long> ids(JsonNode lista) {
		List<Long> ids = new ArrayList<>();
		lista.forEach(n -> ids.add(n.get("id").asLong()));
		return ids;
	}
}
