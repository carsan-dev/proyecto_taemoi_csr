package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
 * {@code GET /api/alumnos/ids}: la navegación anterior/siguiente de editar-alumno. Debe devolver exactamente los
 * ids, y en el mismo orden, que el listado sin paginar {@code GET /api/alumnos?incluirInactivos=} que usaba antes
 * la pantalla, y con la misma autorización (rol a rol, mismo código HTTP).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AlumnosIdsIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String CLAVE = "Clave-Segura-123";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired PasswordEncoder encoder;
	@Autowired UsuarioRepository usuarios;
	@Autowired MembresiaRepository membresias;
	@Autowired AlumnoRepository alumnos;
	@Autowired ContadorService contadores;

	@MockitoBean JavaMailSender mailSender;

	final String sufijo = UUID.randomUUID().toString().substring(0, 8);
	final List<Long> cuentas = new ArrayList<>();
	final List<Long> fichas = new ArrayList<>();

	String tokenPropietario;
	String tokenAdministrador;
	String tokenFamilia;

	@BeforeAll
	void preparar() throws Exception {
		// Nombres en orden inverso al de alta (id) y una baja intermedia: el orden por nombre, por id y por
		// expediente no coinciden, y el filtro de inactivos cambia la lista.
		for (String letra : List.of("Z", "M", "B", "T", "A")) {
			alumno("IDS " + letra, "ids-" + letra.toLowerCase() + "-" + sufijo + "@example.test");
		}
		jdbc.update("UPDATE alumno SET activo = FALSE WHERE id = ?", fichas.get(2));

		Usuario propietario = cuenta("ids-propietario");
		dar(propietario, RolMembresia.PROPIETARIO);
		Usuario administrador = cuenta("ids-administrador");
		dar(administrador, RolMembresia.ADMINISTRADOR);
		Usuario familia = cuenta("ids-familia");
		dar(familia, RolMembresia.FAMILIA);
		alumno("IDS FAMILIA", familia.getEmail());

		tokenPropietario = login(propietario.getEmail());
		tokenAdministrador = login(administrador.getEmail());
		tokenFamilia = login(familia.getEmail());
	}

	/** Sin borrar nada: se retiran las cuentas y los alumnos para no interferir con otras clases. */
	@AfterAll
	void retirar() {
		for (Long cuenta : cuentas) {
			jdbc.update("UPDATE membresia SET activa = FALSE WHERE usuario_id = ?", cuenta);
		}
		for (Long ficha : fichas) {
			jdbc.update("UPDATE alumno SET activo = FALSE WHERE id = ?", ficha);
		}
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	@Test
	void ids_coincidenConElListadoSinPaginar_enContenidoYOrden() throws Exception {
		for (String incluir : List.of("false", "true")) {
			List<Long> listado = idsDe(leer("/api/alumnos?incluirInactivos=" + incluir, tokenAdministrador));
			List<Long> ids = numeros(leer("/api/alumnos/ids?incluirInactivos=" + incluir, tokenAdministrador));
			assertTrue(listado.size() >= 5, "El listado debe incluir los alumnos de la prueba");
			assertEquals(listado, ids, "incluirInactivos=" + incluir);
		}
		List<Long> activos = numeros(leer("/api/alumnos/ids?incluirInactivos=false", tokenAdministrador));
		List<Long> todos = numeros(leer("/api/alumnos/ids?incluirInactivos=true", tokenAdministrador));
		assertTrue(!activos.contains(fichas.get(2)) && todos.contains(fichas.get(2)));
		// Sin el parámetro, como el listado: solo activos.
		assertEquals(activos, numeros(leer("/api/alumnos/ids", tokenAdministrador)));
	}

	@Test
	void autorizacion_igualQueLaDelListado() throws Exception {
		String[][] casos = {
				{ "propietario", tokenPropietario, "200" },
				{ "administrador", tokenAdministrador, "200" },
				{ "familia", tokenFamilia, "403" },
				{ "anonimo", null, "401" } };
		for (String[] caso : casos) {
			for (String incluir : List.of("false", "true")) {
				int listado = estado("/api/alumnos?incluirInactivos=" + incluir, caso[1]);
				int ids = estado("/api/alumnos/ids?incluirInactivos=" + incluir, caso[1]);
				assertEquals(Integer.parseInt(caso[2]), listado, "listado, " + caso[0]);
				assertEquals(listado, ids, "ids, " + caso[0] + ", incluirInactivos=" + incluir);
			}
		}
	}

	/**
	 * El listado de alumnos dejó de pedir {@code GET /api/alumnos/{id}/deportes} por cada alumno de la página y usa
	 * {@code deportes} de la respuesta paginada: deben ser los mismos datos (activos e inactivos) y en el mismo orden.
	 */
	@Test
	void listadoPaginado_traeLosMismosDeportesQueLaPeticionIndividual() throws Exception {
		Long grado = jdbc.queryForObject("SELECT MIN(id) FROM grado", Long.class);
		Long tkd = jdbc.queryForObject(
				"SELECT id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'TAEKWONDO'", Long.class);
		Long kb = jdbc.queryForObject(
				"SELECT id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'KICKBOXING'", Long.class);
		for (int i = 0; i < 3; i++) {
			long alumno = fichas.get(i == 2 ? 3 : i);
			jdbc.update("""
					INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
					  organizacion_id, fecha_alta, fecha_alta_inicial, fecha_grado, disciplina_id, grado_id)
					VALUES (TRUE, 0, ?, 0, 1, 1, 1, '2023-09-01', '2023-09-01', '2024-01-01', ?, ?)""", alumno, tkd, grado);
			jdbc.update("""
					INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
					  organizacion_id, fecha_alta, fecha_alta_inicial, fecha_baja, disciplina_id)
					VALUES (?, 0, ?, 0, 0, 0, 1, '2022-01-01', '2022-01-01', ?, ?)""",
					i != 1, alumno, i == 1 ? Date.valueOf(LocalDate.of(2024, 6, 30)) : null, kb);
		}

		JsonNode pagina = leer("/api/alumnos?page=1&size=9&incluirInactivos=true&nombre=" + sufijo, tokenAdministrador)
				.get("content");
		assertEquals(fichas.size(), pagina.size());
		int conDeportes = 0;
		for (JsonNode alumno : pagina) {
			JsonNode individual = leer("/api/alumnos/" + alumno.get("id").asLong() + "/deportes", tokenAdministrador);
			assertEquals(individual, alumno.get("deportes"), "alumno " + alumno.get("id"));
			conDeportes += individual.size() > 0 ? 1 : 0;
		}
		assertEquals(3, conDeportes);
	}

	// ------------------------------------------------------------------ utilidades

	private int estado(String ruta, String token) throws Exception {
		MockHttpServletRequestBuilder req = get(ruta);
		if (token != null) {
			req = req.cookie(new Cookie("jwt", token));
		}
		return mvc.perform(req).andReturn().getResponse().getStatus();
	}

	private JsonNode leer(String ruta, String token) throws Exception {
		MvcResult r = mvc.perform(get(ruta).cookie(new Cookie("jwt", token))).andExpect(status().isOk()).andReturn();
		return json.readTree(r.getResponse().getContentAsString());
	}

	private static List<Long> idsDe(JsonNode lista) {
		List<Long> ids = new ArrayList<>();
		lista.forEach(n -> ids.add(n.get("id").asLong()));
		return ids;
	}

	private static List<Long> numeros(JsonNode lista) {
		List<Long> ids = new ArrayList<>();
		lista.forEach(n -> ids.add(n.asLong()));
		return ids;
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
			usuario.setApellidos("Ids alumnos");
			usuario.setEmail(nombre + "-" + sufijo + "@example.test");
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

	private long alumno(String nombre, String email) {
		long id = TenantContext.calcularCon(ORG1, () -> {
			Alumno alumno = new Alumno();
			alumno.setNombre(nombre + " " + sufijo);
			alumno.setApellidos("PRUEBA IDS");
			alumno.setNumeroExpediente(contadores.siguienteNumeroExpediente());
			alumno.setFechaNacimiento(Date.valueOf(LocalDate.of(2013, 4, 4)));
			alumno.setDireccion("Calle Prueba 7");
			alumno.setTelefono("600000007");
			alumno.setActivo(true);
			alumno.setAutorizacionWeb(true);
			alumno.setEmail(email);
			return alumnos.save(alumno).getId();
		});
		fichas.add(id);
		return id;
	}
}
