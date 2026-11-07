package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.EmailService;

import jakarta.servlet.http.Cookie;

/**
 * Fase 4, pieza F4-2: dos altas de escuela a la vez que chocan en un índice único, y el superadmin como propietario de la
 * escuela que crea. Para que el choque sea seguro y no cuestión de suerte, las dos altas se esperan justo antes de insertar
 * la escuela ({@code OrganizacionRepository#insertarConId}): las dos ya han comprobado el nombre, calculado el slug y
 * buscado la cuenta del propietario dentro de su transacción, así que ninguna ve lo que la otra escribe.
 *
 * <ul>
 * <li>Mismo propietario NUEVO en dos escuelas distintas: las dos crean la cuenta; la segunda choca con el índice único de
 * {@code usuario.email} y se repite entera, ahora por el camino de la cuenta existente: 201 y 201, un solo correo.</li>
 * <li>Dos nombres distintos con el mismo slug: la segunda choca con {@code uk_organizacion_slug} y se repite con el slug
 * recalculado ({@code -2}): 201 y 201, no un 409 falso de «nombre repetido».</li>
 * </ul>
 * Datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AltaEscuelaCarrerasIntegrationTest extends MySqlIntegrationTest {

	private static final String CLAVE = "Clave-Segura-123";
	private static final String RUTA = "/api/plataforma/escuelas";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired UsuarioRepository usuarios;
	@Autowired PasswordEncoder encoder;

	@MockitoBean EmailService email;
	@MockitoSpyBean OrganizacionRepository organizaciones;

	final String marca = java.util.UUID.randomUUID().toString().substring(0, 8);
	Cookie cookieSuperadmin;
	Usuario superadmin;
	/** Si no es null, cada alta espera aquí (hasta 10 s) a las otras antes de insertar la escuela. */
	volatile CountDownLatch encuentro;
	/** Si no es null, cada alta que se repite espera aquí (hasta 10 s) a las otras antes de volver a insertar. */
	volatile CountDownLatch reencuentro;
	/** Si el hilo ya pasó por {@link #encuentro}: su siguiente inserción es la de la repetición. */
	final ThreadLocal<Boolean> yaSeEncontro = ThreadLocal.withInitial(() -> false);
	/** Si no es null, la próxima alta encuentra su id reservado ya ocupado por una fila con este slug. */
	volatile String ocupaElId;

	@BeforeAll
	void preparar() throws Exception {
		String correo = "it-f42c-superadmin-" + marca + "@example.test";
		superadmin = TenantContext.calcularCon(1L, () -> {
			Usuario usuario = new Usuario();
			usuario.setNombre("Superadmin");
			usuario.setApellidos("Ficticio");
			usuario.setEmail(correo);
			usuario.setContrasena(encoder.encode(CLAVE));
			usuario.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(usuario);
		});
		assertEquals(1, jdbc.update("UPDATE usuario SET es_admin_plataforma = TRUE WHERE id = ?", superadmin.getId()));
		MvcResult login = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}")).andReturn();
		assertEquals(200, login.getResponse().getStatus());
		cookieSuperadmin = new Cookie("jwt", json.readTree(login.getResponse().getContentAsString()).get("token").asText());
	}

	@BeforeEach
	void puntoDeEncuentro() {
		encuentro = null;
		reencuentro = null;
		ocupaElId = null;
		// El espía se reinicia tras cada test: se vuelve a poner la espera y después se llama al repositorio real.
		doAnswer(invocacion -> {
			CountDownLatch punto = yaSeEncontro.get() ? reencuentro : encuentro;
			if (punto != null) {
				yaSeEncontro.set(true);
				punto.countDown();
				punto.await(10, TimeUnit.SECONDS);
			}
			String slug = ocupaElId;
			if (slug != null) {
				ocupaElId = null;
				// Otra conexión (otro hilo, confirmada al momento) ocupa el id reservado, como tras mover el
				// AUTO_INCREMENT. En este hilo, JdbcTemplate usaría la conexión de la transacción del alta.
				Long reservado = invocacion.getArgument(0);
				CompletableFuture.runAsync(() -> jdbc.update(
						"INSERT INTO organizacion (id, slug, nombre) VALUES (?, ?, 'Escuela IT ocupa id')", reservado, slug))
						.get(10, TimeUnit.SECONDS);
			}
			return mockingDetails(organizaciones).getMockCreationSettings().getDefaultAnswer().answer(invocacion);
		}).when(organizaciones).insertarConId(any(), any(), any(), any(), any());
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	@Test
	void mismoPropietarioNuevoEnDosAltasALaVez_lasDosSalen_unaCuentaYUnSoloCorreo() throws Exception {
		String emailPropietario = "it-f42c-mismo-" + marca + "@example.test";
		String nombreA = "Escuela IT Carrera A " + marca;
		String nombreB = "Escuela IT Carrera B " + marca;

		List<String> resultados = aLaVez(cuerpo(nombreA, "Eva", "Ficticia", emailPropietario),
				cuerpo(nombreB, "Eva", "Ficticia", emailPropietario));

		// Una alta invita (la cuenta es suya) y la otra, al repetirse, encuentra la cuenta y solo da el rol.
		assertEquals(List.of("201 invitacion=" + emailPropietario, "201 invitacion=null"), resultados);
		assertEquals(1, contar("SELECT COUNT(*) FROM usuario WHERE email = ?", emailPropietario));
		assertEquals(1, contar("SELECT COUNT(*) FROM organizacion WHERE nombre = ?", nombreA));
		assertEquals(1, contar("SELECT COUNT(*) FROM organizacion WHERE nombre = ?", nombreB));
		assertEquals(List.of("PROPIETARIO", "PROPIETARIO"), jdbc.queryForList("""
				SELECT m.rol FROM membresia m JOIN usuario u ON u.id = m.usuario_id
				JOIN organizacion o ON o.id = m.organizacion_id
				WHERE u.email = ? AND m.activa = TRUE AND o.nombre IN (?, ?)""", String.class, emailPropietario, nombreA,
				nombreB));
		// Cada escuela, con su sede y su catálogo, sin restos del intento deshecho.
		for (String nombre : List.of(nombreA, nombreB)) {
			assertEquals(1, contar("""
					SELECT COUNT(*) FROM sede s JOIN organizacion o ON o.id = s.organizacion_id WHERE o.nombre = ?""",
					nombre), nombre);
			assertEquals(1, contar("""
					SELECT COUNT(*) FROM disciplina d JOIN organizacion o ON o.id = d.organizacion_id WHERE o.nombre = ?""",
					nombre), nombre);
		}
		// El correo de la invitación sale una vez: el del intento deshecho no se envía.
		verify(email, times(1)).sendEmailConImagen(eq(emailPropietario), any(), any(), any(), any(), any(), any());
		assertEquals(0, encuentro.getCount(), "Las dos altas llegaron a la vez a insertar la escuela");
	}

	@Test
	void dosNombresDistintosConElMismoSlugALaVez_lasDosSalen_conSufijo() throws Exception {
		String nombreA = "Club IT Nandu " + marca;
		String nombreB = "Club-IT Nandu " + marca;

		List<String> resultados = aLaVez(cuerpo(nombreA, "Eva", "Ficticia", "it-f42c-slug-a-" + marca + "@example.test"),
				cuerpo(nombreB, "Eva", "Ficticia", "it-f42c-slug-b-" + marca + "@example.test"));

		assertEquals(2, resultados.size());
		assertTrue(resultados.stream().allMatch(r -> r.startsWith("201 ")), resultados.toString());
		String base = "club-it-nandu-" + marca;
		List<String> slugs = jdbc.queryForList("SELECT slug FROM organizacion WHERE nombre IN (?, ?) ORDER BY slug",
				String.class, nombreA, nombreB);
		assertEquals(List.of(base, base + "-2"), slugs);
		List<String> nombres = new ArrayList<>(jdbc.queryForList("SELECT nombre FROM organizacion WHERE slug IN (?, ?)",
				String.class, base, base + "-2"));
		nombres.sort(null);
		assertEquals(List.of(nombreA, nombreB), nombres);
		assertEquals(0, encuentro.getCount(), "Las dos altas llegaron a la vez a insertar la escuela");
	}

	@Test
	void elMismoNombreConYSinTildesALaVez_unaSale_laOtra409() throws Exception {
		String nombreA = "Club IT Ñandú Sur " + marca;
		String nombreB = "club it nandu sur " + marca;

		List<String> resultados = aLaVez(cuerpo(nombreA, "Eva", "Ficticia", "it-f42c-tilde-a-" + marca + "@example.test"),
				cuerpo(nombreB, "Eva", "Ficticia", "it-f42c-tilde-b-" + marca + "@example.test"));

		assertEquals(2, resultados.size(), resultados.toString());
		assertTrue(resultados.get(0).startsWith("201 "), resultados.toString());
		assertEquals("409 {\"mensaje\":\"Ya hay una escuela con ese nombre.\"}", resultados.get(1));
		// La intercalación de MySQL no distingue tildes: una sola fila con cualquiera de los dos nombres.
		assertEquals(1, contar("SELECT COUNT(*) FROM organizacion WHERE slug LIKE ?", "club-it-nandu-sur-" + marca + "%"));
		assertEquals(0, encuentro.getCount(), "Las dos altas llegaron a la vez a insertar la escuela");
	}

	@Test
	void idReservadoYaOcupado_seRepiteConOtroId_noEsNombreRepetido() throws Exception {
		String nombre = "Escuela IT Id Ocupado " + marca;
		String emailPropietario = "it-f42c-id-" + marca + "@example.test";
		String ocupante = "it-f42c-ocupa-" + marca;
		ocupaElId = ocupante;

		MvcResult r = mvc.perform(alta(cuerpo(nombre, "Eva", "Ficticia", emailPropietario)).cookie(cookieSuperadmin))
				.andReturn();

		assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		long org = json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
		long ocupado = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = ?", Long.class, ocupante);
		assertTrue(org > ocupado, "El alta repetida recibe un id reservado nuevo");
		assertEquals(nombre, jdbc.queryForObject("SELECT nombre FROM organizacion WHERE id = ?", String.class, org));
		// Nada del intento deshecho en el id ocupado; la cuenta y el correo, una vez.
		assertEquals(0, contar("SELECT COUNT(*) FROM sede WHERE organizacion_id = ?", ocupado));
		assertEquals(0, contar("SELECT COUNT(*) FROM membresia WHERE organizacion_id = ?", ocupado));
		assertEquals(1, contar("SELECT COUNT(*) FROM usuario WHERE email = ?", emailPropietario));
		verify(email, times(1)).sendEmailConImagen(eq(emailPropietario), any(), any(), any(), any(), any(), any());
		jdbc.update("DELETE FROM organizacion WHERE id = ?", ocupado);
	}

	@Test
	void elSuperadminComoPropietario_soloMembresia_sinCorreo() throws Exception {
		MvcResult r = mvc.perform(alta(cuerpo("Escuela IT Superadmin " + marca, null, null,
				superadmin.getEmail().toUpperCase())).cookie(cookieSuperadmin)).andReturn();
		assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		JsonNode creada = json.readTree(r.getResponse().getContentAsString());
		long org = creada.get("id").asLong();
		assertTrue(creada.get("invitacionEnviadaA").isNull(), creada.toString());
		assertEquals(List.of("PROPIETARIO"), jdbc.queryForList(
				"SELECT rol FROM membresia WHERE usuario_id = ? AND organizacion_id = ? AND activa = TRUE", String.class,
				superadmin.getId(), org));
		// Sigue siendo superadmin y con su contraseña; ni correo ni token nuevo.
		assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT es_admin_plataforma FROM usuario WHERE id = ?",
				Boolean.class, superadmin.getId()));
		assertEquals(superadmin.getContrasena(),
				jdbc.queryForObject("SELECT contrasena FROM usuario WHERE id = ?", String.class, superadmin.getId()));
		assertNull(jdbc.queryForObject("SELECT reset_token_hash FROM usuario WHERE id = ?", String.class,
				superadmin.getId()));
		verify(email, never()).sendEmailConImagen(any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	void tresNombresConElMismoSlugALaVez_laQueVuelveAChocar_409() throws Exception {
		// Nombres distintos con el mismo slug. Una sale a la primera; las otras dos chocan, se repiten a la vez con el
		// mismo slug con sufijo y vuelven a chocar: una sale y la otra ya no se repite más (409, no 500).
		String nombreA = "Club IT Tres " + marca;
		String nombreB = "Club-IT Tres " + marca;
		String nombreC = "Club IT Tres! " + marca;
		reencuentro = new CountDownLatch(2);

		List<String> resultados = aLaVez(cuerpo(nombreA, "Eva", "Ficticia", "it-f42c-tres-a-" + marca + "@example.test"),
				cuerpo(nombreB, "Eva", "Ficticia", "it-f42c-tres-b-" + marca + "@example.test"),
				cuerpo(nombreC, "Eva", "Ficticia", "it-f42c-tres-c-" + marca + "@example.test"));

		assertEquals(3, resultados.size(), resultados.toString());
		assertTrue(resultados.get(0).startsWith("201 ") && resultados.get(1).startsWith("201 "), resultados.toString());
		assertEquals("409 {\"mensaje\":\"Otra alta de escuela ha coincidido con esta. Vuelve a intentarlo.\"}",
				resultados.get(2));
		String base = "club-it-tres-" + marca;
		assertEquals(List.of(base, base + "-2"), jdbc.queryForList(
				"SELECT slug FROM organizacion WHERE slug LIKE ? ORDER BY slug", String.class, base + "%"));
		// La que no sale no deja nada: ni escuela, ni cuenta, ni correo.
		assertEquals(2, contar("SELECT COUNT(*) FROM usuario WHERE email LIKE ?", "it-f42c-tres-%-" + marca + "%"));
		verify(email, times(2)).sendEmailConImagen(any(), any(), any(), any(), any(), any(), any());
		assertEquals(0, encuentro.getCount(), "Las tres altas llegaron a la vez a insertar la escuela");
		assertEquals(0, reencuentro.getCount(), "Las dos repeticiones llegaron a la vez a insertar la escuela");
	}

	// ------------------------------------------------------------------ apoyo

	/** Las altas a la vez; cada resultado como texto («201 invitacion=…», «409 …» o la excepción), ordenados. */
	private List<String> aLaVez(String... cuerpos) throws Exception {
		encuentro = new CountDownLatch(cuerpos.length);
		ExecutorService hilos = Executors.newFixedThreadPool(cuerpos.length);
		try {
			List<Future<String>> futuros = new ArrayList<>();
			for (String cuerpo : cuerpos) {
				Callable<String> peticion = () -> {
					MvcResult r = mvc.perform(alta(cuerpo).cookie(cookieSuperadmin)).andReturn();
					String contenido = r.getResponse().getContentAsString();
					int estado = r.getResponse().getStatus();
					return estado == 201
							? "201 invitacion=" + json.readTree(contenido).get("invitacionEnviadaA").asText(null)
							: estado + " " + contenido;
				};
				futuros.add(hilos.submit(peticion));
			}
			List<String> resultados = new ArrayList<>();
			for (Future<String> futuro : futuros) {
				try {
					resultados.add(futuro.get(60, TimeUnit.SECONDS));
				} catch (ExecutionException fallo) {
					Throwable causa = fallo.getCause();
					while (causa.getCause() != null && causa.getCause() != causa) {
						causa = causa.getCause();
					}
					resultados.add("excepción " + fallo.getCause().getClass().getSimpleName() + ": "
							+ causa.getClass().getSimpleName());
				}
			}
			resultados.sort(null);
			return resultados;
		} finally {
			hilos.shutdownNow();
		}
	}

	private String cuerpo(String nombre, String nombrePropietario, String apellidos, String emailPropietario) {
		ObjectNode raiz = json.createObjectNode();
		raiz.put("nombre", nombre);
		raiz.put("emailContacto", "buzon-" + marca + "@example.test");
		raiz.putObject("sede").put("nombre", "Sede");
		ObjectNode propietario = raiz.putObject("propietario");
		propietario.put("nombre", nombrePropietario);
		propietario.put("apellidos", apellidos);
		propietario.put("email", emailPropietario);
		raiz.putArray("disciplinas").add("PILATES");
		return raiz.toString();
	}

	private MockHttpServletRequestBuilder alta(String cuerpo) {
		return post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private long contar(String sql, Object... parametros) {
		Long n = jdbc.queryForObject(sql, Long.class, parametros);
		return n == null ? 0 : n;
	}
}
