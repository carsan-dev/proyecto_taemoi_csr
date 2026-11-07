package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Membresia;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.MembresiaRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.UsuarioRepository;

import jakarta.servlet.http.Cookie;

/**
 * Selector de escuela: {@code GET /api/auth/organizaciones} lleva logo, versión del logo, color y número de sedes
 * activas de cada escuela del usuario, cargados por lotes (mismas sentencias con 2 que con 3 escuelas) y sin fugas: un
 * logo que apunta a la imagen de otra escuela no cuenta.
 */
@SpringBootTest(properties = {
		"spring.jpa.properties.hibernate.session_factory.statement_inspector=com.taemoi.project.integracion.ContadorSentencias" })
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrganizacionesMarcaIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String CLAVE = "Clave-Segura-123";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired PasswordEncoder encoder;
	@Autowired UsuarioRepository usuarios;
	@Autowired MembresiaRepository membresias;
	@Autowired OrganizacionRepository organizaciones;

	@MockitoBean JavaMailSender mailSender;

	final String sufijo = UUID.randomUUID().toString().substring(0, 8);
	long conLogo;
	long legada;
	long imagenLogo;
	Usuario usuario;

	@BeforeAll
	void preparar() {
		conLogo = crearEscuela("it-marca-a-", false);
		legada = crearEscuela("it-marca-b-", true);
		jdbc.update("INSERT INTO imagen (nombre, ruta, tipo, organizacion_id) VALUES ('logo', 'x/logo.png', "
				+ "'image/png', ?)", conLogo);
		imagenLogo = jdbc.queryForObject("SELECT MAX(id) FROM imagen WHERE organizacion_id = ?", Long.class, conLogo);
		jdbc.update("UPDATE organizacion SET logo_imagen_id = ?, color_primario = '#1A8F5C' WHERE id = ?", imagenLogo,
				conLogo);
		// La escuela legada apunta (mal) a la imagen de otra escuela: no debe salir como logo subido.
		jdbc.update("UPDATE organizacion SET logo_imagen_id = ? WHERE id = ?", imagenLogo, legada);
		for (String nombre : new String[] { "A", "B", "C" }) {
			jdbc.update("INSERT INTO sede (organizacion_id, nombre, activa) VALUES (?, ?, TRUE)", conLogo, nombre);
		}
		jdbc.update("INSERT INTO sede (organizacion_id, nombre, activa) VALUES (?, 'Cerrada', FALSE)", conLogo);
		jdbc.update("INSERT INTO sede (organizacion_id, nombre, activa) VALUES (?, 'Unica', TRUE)", legada);

		usuario = TenantContext.calcularCon(ORG1, () -> {
			Usuario u = new Usuario();
			u.setNombre("Usuario");
			u.setApellidos("Marca");
			u.setEmail("marca-" + sufijo + "@example.test");
			u.setContrasena(encoder.encode(CLAVE));
			u.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(u);
		});
		dar(conLogo);
		dar(legada);
	}

	@Test
	void listaConMarcaYSedesPorEscuelaYSinConsultaPorEscuela() throws Exception {
		String token = login();
		organizaciones(token); // calienta cachés
		ContadorSentencias.Conteo<JsonNode> dos = ContadorSentencias.contar(() -> organizaciones(token));
		JsonNode lista = dos.resultado();
		assertEquals(2, lista.size());
		JsonNode a = lista.get(0);
		JsonNode b = lista.get(1);
		assertEquals(conLogo, a.get("id").asLong());
		assertEquals("SUBIDO", a.get("logo").asText());
		assertEquals(imagenLogo, a.get("logoVersion").asLong());
		assertEquals("#1A8F5C", a.get("colorPrimario").asText());
		assertEquals(3, a.get("numeroSedes").asInt(), "solo las sedes activas");
		assertEquals(legada, b.get("id").asLong());
		assertEquals("LEGADO", b.get("logo").asText(), "la imagen de otra escuela no es su logo");
		assertTrue(b.get("logoVersion").isNull());
		assertTrue(b.get("colorPrimario").isNull());
		assertEquals(1, b.get("numeroSedes").asInt());

		// Una tercera escuela no añade sentencias.
		long tercera = crearEscuela("it-marca-c-", false);
		dar(tercera);
		ContadorSentencias.Conteo<JsonNode> tres = ContadorSentencias.contar(() -> organizaciones(token));
		assertEquals(3, tres.resultado().size());
		assertEquals("NINGUNO", tres.resultado().get(2).get("logo").asText());
		assertEquals(0, tres.resultado().get(2).get("numeroSedes").asInt());
		assertFalse(tres.sentencias().isEmpty());
		assertEquals(dos.total(), tres.total(), "mismas sentencias con 2 y 3 escuelas: " + tres.sentencias());
	}

	private long crearEscuela(String prefijo, boolean legado) {
		return TenantContext.calcularCon(ORG1, () -> {
			Organizacion org = new Organizacion();
			org.setSlug(prefijo + sufijo);
			org.setNombre("Escuela " + prefijo + sufijo);
			org.setComportamientoLegadoTaemoi(legado);
			return organizaciones.save(org).getId();
		});
	}

	private void dar(long organizacionId) {
		TenantContext.calcularCon(ORG1,
				() -> membresias.save(new Membresia(usuario.getId(), organizacionId, RolMembresia.PROPIETARIO)));
	}

	private String login() throws Exception {
		MvcResult r = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + usuario.getEmail() + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk()).andReturn();
		return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
	}

	private JsonNode organizaciones(String token) throws Exception {
		MvcResult r = mvc.perform(get("/api/auth/organizaciones").cookie(new Cookie("jwt", token)))
				.andExpect(status().isOk()).andReturn();
		return json.readTree(r.getResponse().getContentAsString());
	}
}
