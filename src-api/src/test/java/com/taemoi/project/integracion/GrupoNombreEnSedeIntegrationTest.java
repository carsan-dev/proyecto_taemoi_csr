package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.UUID;

import org.hibernate.engine.spi.SessionFactoryImplementor;
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
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.acceso.GestionPorSede;
import com.taemoi.project.entities.Grupo;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.GrupoRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;

/**
 * Pieza E10-4 (fase3-e10-plan.md §4) contra MySQL: el alta de grupo sin ninguna sede activa da 400; el alta y la
 * edición con un nombre que ya tiene otro grupo de la misma sede dan 409 antes de guardar, con la misma comparación que
 * hará la clave única {@code (sede_id, nombre)} de la V32_1 (la colación de la columna: sin distinguir mayúsculas ni
 * tildes). Editar un grupo sin cambiar nombre ni sede no comprueba nada (duplicados heredados). Y {@code Grupo.sedeId}
 * con {@code nullable = false} no impide guardar un grupo heredado sin sede mientras la columna admita NULL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GrupoNombreEnSedeIntegrationTest extends MySqlIntegrationTest {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;
	@Autowired GrupoRepository grupoRepository;
	@Autowired GestionPorSede gestionPorSede;
	@Autowired EntityManagerFactory emf;
	@Autowired TransactionTemplate transacciones;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	String tokenGestor;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e10-4");
		tokenGestor = e.token(jwtService, usuarios, e.gestor);
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ sin sede activa

	@Test
	void sedeDeAlta_sinNingunaSedeActiva_400() {
		long sinSedes = organizacion("it-e10-4-sin");
		IllegalArgumentException sinNinguna = assertThrows(IllegalArgumentException.class,
				() -> TenantContext.calcularCon(sinSedes, () -> gestionPorSede.sedeDeAlta(null, true)));
		assertEquals("La escuela no tiene ninguna sede activa. Crea una en Sedes y vuelve a intentarlo.",
				sinNinguna.getMessage());
		assertEquals(GestionPorSede.SIN_SEDE_ACTIVA, sinNinguna.getMessage());

		long soloInactiva = organizacion("it-e10-4-inactiva");
		jdbc.update("INSERT INTO sede (organizacion_id, nombre, activa) VALUES (?, 'Cerrada', FALSE)", soloInactiva);
		IllegalArgumentException inactiva = assertThrows(IllegalArgumentException.class,
				() -> TenantContext.calcularCon(soloInactiva, () -> gestionPorSede.sedeDeAlta(null, true)));
		assertEquals(GestionPorSede.SIN_SEDE_ACTIVA, inactiva.getMessage());
	}

	// ------------------------------------------------------------------ alta

	@Test
	void alta_nombreRepetidoEnLaSede_409ConElNombreDeLaSede() throws Exception {
		String nombre = "IT E10-4 alta " + e.marca;
		alta(nombre, e.sA);
		long antes = gruposDeLaEscuela();

		MvcResult repetido = peticion(post("/api/grupos").contentType(MediaType.APPLICATION_JSON)
				.content(grupo(nombre, e.sA)));
		assertEquals(409, repetido.getResponse().getStatus(), contenido(repetido));
		assertEquals("Ya hay un grupo con ese nombre en " + nombreSede(e.sA) + ". Elige otro nombre.",
				json.readTree(contenido(repetido)).get("mensaje").asText());
		assertEquals(antes, gruposDeLaEscuela(), "no se crea");
	}

	@Test
	void alta_nombreRepetidoSinIndicarSede_409() throws Exception {
		// Escuela con una sola sede activa: sin sede en el cuerpo, el grupo va a ella y choca igual.
		long org = organizacion("it-e10-4-una");
		jdbc.update("INSERT INTO sede (organizacion_id, nombre, activa) VALUES (?, 'Única IT', TRUE)", org);
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'ADMINISTRADOR')",
				e.gestor, org);
		String token = jwtService.generateToken(
				TenantContext.calcularCon(EscuelaConSedesDePrueba.ORG1, () -> usuarios.findById(e.gestor).orElseThrow()),
				600_000, org);
		assertEquals(201, peticion(post("/api/grupos").contentType(MediaType.APPLICATION_JSON)
				.content(grupo("Infantil", null)), token).getResponse().getStatus());
		MvcResult repetido = peticion(post("/api/grupos").contentType(MediaType.APPLICATION_JSON)
				.content(grupo("Infantil", null)), token);
		assertEquals(409, repetido.getResponse().getStatus(), contenido(repetido));
		assertEquals("Ya hay un grupo con ese nombre en Única IT. Elige otro nombre.",
				json.readTree(contenido(repetido)).get("mensaje").asText());
	}

	@Test
	void alta_mismoNombreEnOtraSede_201() throws Exception {
		String nombre = "IT E10-4 otra sede " + e.marca;
		alta(nombre, e.sA);
		alta(nombre, e.sB);
	}

	@Test
	void alta_mismoNombreConOtraMayusculaOTilde_409PorLaColacion() throws Exception {
		// La colación de grupo.nombre (utf8mb4_0900_ai_ci) no distingue mayúsculas ni tildes: la clave única de la V32_1
		// tampoco las distinguirá, así que la comprobación previa responde igual.
		alta("IT E10-4 Infantil " + e.marca, e.sA);
		assertEquals(409, estado(post("/api/grupos").contentType(MediaType.APPLICATION_JSON)
				.content(grupo("it e10-4 infantil " + e.marca, e.sA))));
		assertEquals(409, estado(post("/api/grupos").contentType(MediaType.APPLICATION_JSON)
				.content(grupo("IT E10-4 Ínfantil " + e.marca, e.sA))));
	}

	// ------------------------------------------------------------------ edición

	@Test
	void edicion_sinCambiarNombreNiSede_conDuplicadoHeredado_200() throws Exception {
		String nombre = "IT E10-4 heredado " + e.marca;
		long uno = alta(nombre, e.sA);
		long otro = alta("IT E10-4 heredado bis " + e.marca, e.sA);
		jdbc.update("UPDATE grupo SET nombre = ? WHERE id = ?", nombre, otro);

		MvcResult r = peticion(put("/api/grupos/" + uno).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"" + nombre + "\",\"deporte\":\"TAEKWONDO\",\"rangoEdadMin\":6}"));
		assertEquals(200, r.getResponse().getStatus(), contenido(r));
		assertEquals(6, jdbc.queryForObject("SELECT rango_edad_min FROM grupo WHERE id = ?", Integer.class, uno));
		assertEquals(200, estado(put("/api/grupos/" + otro).contentType(MediaType.APPLICATION_JSON)
				.content(grupo(nombre, e.sA))), "con la misma sede en el cuerpo, tampoco");
	}

	@Test
	void edicion_aUnNombreRepetido_409YSinCambios() throws Exception {
		String ocupado = "IT E10-4 ocupado " + e.marca;
		alta(ocupado, e.sA);
		long grupo = alta("IT E10-4 libre " + e.marca, e.sA);

		MvcResult r = peticion(put("/api/grupos/" + grupo).contentType(MediaType.APPLICATION_JSON)
				.content(grupo(ocupado.toUpperCase(), null)));
		assertEquals(409, r.getResponse().getStatus(), contenido(r));
		assertEquals("Ya hay un grupo con ese nombre en " + nombreSede(e.sA) + ". Elige otro nombre.",
				json.readTree(contenido(r)).get("mensaje").asText());
		assertEquals("IT E10-4 libre " + e.marca,
				jdbc.queryForObject("SELECT nombre FROM grupo WHERE id = ?", String.class, grupo));
	}

	@Test
	void edicion_cambiarDeSedeAUnaConEseNombre_409() throws Exception {
		String nombre = "IT E10-4 mudanza " + e.marca;
		alta(nombre, e.sB);
		long grupo = alta(nombre, e.sA);

		MvcResult r = peticion(put("/api/grupos/" + grupo).contentType(MediaType.APPLICATION_JSON)
				.content(grupo(nombre, e.sB)));
		assertEquals(409, r.getResponse().getStatus(), contenido(r));
		assertEquals("Ya hay un grupo con ese nombre en " + nombreSede(e.sB) + ". Elige otro nombre.",
				json.readTree(contenido(r)).get("mensaje").asText());
		assertEquals(e.sA, jdbc.queryForObject("SELECT sede_id FROM grupo WHERE id = ?", Long.class, grupo));
	}

	@Test
	void edicion_cambiarSoloMayusculasDeSuNombre_200() throws Exception {
		long grupo = alta("IT E10-4 propio " + e.marca, e.sA);
		assertEquals(200, estado(put("/api/grupos/" + grupo).contentType(MediaType.APPLICATION_JSON)
				.content(grupo("IT E10-4 PROPIO " + e.marca, null))), "no choca consigo mismo");
	}

	// ------------------------------------------------------------------ Grupo.sedeId nullable = false

	@Test
	void grupoHeredadoSinSede_seGuardaAunqueLaEntidadNoAdmitaNull() {
		// Hibernate no comprueba la nulabilidad al guardar (Bean Validation en el classpath): un grupo heredado con
		// sede_id NULL se sigue guardando hasta que la V32_1 lo impida en la base.
		assertFalse(emf.unwrap(SessionFactoryImplementor.class).getSessionFactoryOptions().isCheckNullability());
		TenantContext.ejecutarCon(e.org, () -> transacciones.executeWithoutResult(estado -> {
			Grupo sinSede = grupoRepository.findById(e.gSin).orElseThrow();
			assertNull(sinSede.getSedeId());
			sinSede.setRangoEdadMax(99);
			grupoRepository.saveAndFlush(sinSede);
		}));
		assertEquals(99, jdbc.queryForObject("SELECT rango_edad_max FROM grupo WHERE id = ?", Integer.class, e.gSin));
		assertNull(jdbc.queryForObject("SELECT sede_id FROM grupo WHERE id = ?", Long.class, e.gSin));
	}

	// ------------------------------------------------------------------ ayudas

	private long alta(String nombre, long sede) throws Exception {
		MvcResult r = peticion(post("/api/grupos").contentType(MediaType.APPLICATION_JSON).content(grupo(nombre, sede)));
		assertEquals(201, r.getResponse().getStatus(), contenido(r));
		return json.readTree(contenido(r)).get("id").asLong();
	}

	private String nombreSede(long sede) {
		return jdbc.queryForObject("SELECT nombre FROM sede WHERE id = ?", String.class, sede);
	}

	private long gruposDeLaEscuela() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM grupo WHERE organizacion_id = ?", Long.class, e.org);
	}

	/** Escuela nueva sin sedes, con la disciplina TAEKWONDO. */
	private long organizacion(String prefijo) {
		String slug = prefijo + "-" + UUID.randomUUID().toString().substring(0, 8);
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES (?, 'Escuela IT')", slug);
		long org = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = ?", Long.class, slug);
		jdbc.update("""
				INSERT INTO disciplina (organizacion_id, codigo, nombre, sistema_grados_id, edad_adulto, orden)
				VALUES (?, 'TAEKWONDO', 'Taekwondo IT', NULL, 14, 1)""", org);
		return org;
	}

	private static String grupo(String nombre, Long sede) {
		return "{\"nombre\":\"" + nombre + "\",\"deporte\":\"TAEKWONDO\"" + (sede == null ? "" : ",\"sedeId\":" + sede)
				+ "}";
	}

	private int estado(MockHttpServletRequestBuilder peticion) throws Exception {
		return peticion(peticion).getResponse().getStatus();
	}

	private MvcResult peticion(MockHttpServletRequestBuilder peticion) throws Exception {
		return peticion(peticion, tokenGestor);
	}

	private MvcResult peticion(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
	}

	private static String contenido(MvcResult r) throws Exception {
		return r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
	}
}
