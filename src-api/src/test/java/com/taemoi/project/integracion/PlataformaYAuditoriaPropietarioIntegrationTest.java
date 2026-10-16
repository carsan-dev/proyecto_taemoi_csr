package com.taemoi.project.integracion;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.config.OAuth2AuthenticationSuccessHandler;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AuditoriaAccion;
import com.taemoi.project.entities.AuditoriaEvento;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Membresia;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.AuditoriaEventoRepository;
import com.taemoi.project.repositories.MembresiaRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.ContadorService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Fase 3, entrega 3 (docs/multi-escuela/fase3-sedes-permisos.md §4 y D2), por HTTP real (MockMvc con la cadena
 * de filtros completa) contra MySQL:
 * <ul>
 * <li>administrador de plataforma sin membresía: entra en la escuela por defecto SOLO con
 * {@code ROLE_ADMIN_PLATAFORMA} (403 en datos de escuela, 200 en {@code /api/plataforma/errores});</li>
 * <li>administrador de plataforma que además es propietario: todo igual que antes (mismos roles visibles);</li>
 * <li>{@code /api/plataforma/errores}: errores técnicos (4xx/5xx o sin sesión) de TODAS las escuelas, sin datos
 * personales ni contenido de la petición;</li>
 * <li>registro de acciones del propietario filtrado en servidor y aislado por escuela.</li>
 * </ul>
 * Los eventos sembrados llevan una fecha fija antigua para no mezclarse con los que generan otros tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlataformaYAuditoriaPropietarioIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String CLAVE = "Clave-Segura-123";
	private static final LocalDate DIA = LocalDate.of(2001, 2, 3);

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired PasswordEncoder encoder;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired MembresiaRepository membresias;
	@Autowired OrganizacionRepository organizaciones;
	@Autowired AlumnoRepository alumnos;
	@Autowired AuditoriaEventoRepository eventos;
	@Autowired ContadorService contadores;

	@Autowired OAuth2AuthenticationSuccessHandler oauth2SuccessHandler;

	@MockitoBean JavaMailSender mailSender;

	@Value("${cors.allowed.origin}") String frontendUrl;

	final String sufijo = UUID.randomUUID().toString().substring(0, 8);
	final String moduloSoloError = "it-solo-error-" + sufijo;
	long org2;
	long alumnoOrg1;
	Usuario propietario1;
	Usuario propietario2;
	Usuario plataformaSola;
	Usuario plataformaPropietario;

	// Escuela 1
	long a1Alta, a2Cambio, a3Baja, a4LecturaNormal, a5LecturaSalud, a6Error400, a7Error500, a8AltaAnonima,
			a9LecturaAnonima, a10Error403;
	// Escuela 2
	long b1Alta, b2LecturaSalud, b3Anonimo404, b4Error500;

	@BeforeAll
	void sembrar() {
		org2 = TenantContext.calcularCon(ORG1, () -> {
			Organizacion org = new Organizacion();
			org.setSlug("it-plataforma-" + sufijo);
			org.setNombre("Escuela plataforma " + sufijo);
			return organizaciones.save(org).getId();
		});
		assertNotEquals(ORG1, org2);
		alumnoOrg1 = TenantContext.calcularCon(ORG1, () -> crearAlumno("PLATAFORMA " + sufijo));

		propietario1 = crearUsuario("prop1-" + sufijo + "@example.test");
		dar(propietario1, ORG1, RolMembresia.PROPIETARIO);
		propietario2 = crearUsuario("prop2-" + sufijo + "@example.test");
		dar(propietario2, org2, RolMembresia.PROPIETARIO);
		plataformaSola = crearUsuario("plataforma-" + sufijo + "@example.test");
		marcarPlataforma(plataformaSola);
		plataformaPropietario = crearUsuario("plataforma-prop-" + sufijo + "@example.test");
		dar(plataformaPropietario, ORG1, RolMembresia.PROPIETARIO);
		marcarPlataforma(plataformaPropietario);

		Long u1 = propietario1.getId();
		Long u2 = propietario2.getId();
		a1Alta = evento(ORG1, 0, AuditoriaAccion.CREATE, "POST", "/api/alumnos", "alumnos", 201, u1);
		a2Cambio = evento(ORG1, 1, AuditoriaAccion.UPDATE, "PUT", "/api/alumnos/5", "alumnos", 200, u1);
		a3Baja = evento(ORG1, 2, AuditoriaAccion.DELETE, "DELETE", "/api/grupos/3", "grupos", 204, u1);
		a4LecturaNormal = evento(ORG1, 3, AuditoriaAccion.READ, "GET", "/api/alumnos", "alumnos", 200, u1);
		a5LecturaSalud = evento(ORG1, 4, AuditoriaAccion.READ, "GET", "/api/alumnos/5/salud", "salud", 200, u1);
		a6Error400 = evento(ORG1, 5, AuditoriaAccion.CREATE, "POST", "/api/alumnos", moduloSoloError, 400, u1);
		a7Error500 = evento(ORG1, 6, AuditoriaAccion.UPDATE, "PUT", "/api/alumnos/5", "alumnos", 500, u1);
		a8AltaAnonima = evento(ORG1, 7, AuditoriaAccion.CREATE, "POST", "/api/preinscripciones", "preinscripciones",
				201, null);
		a9LecturaAnonima = evento(ORG1, 8, AuditoriaAccion.READ, "GET", "/api/eventos", "eventos", 200, null);
		a10Error403 = evento(ORG1, 9, AuditoriaAccion.READ, "GET", "/api/alumnos", "alumnos", 403, u1);
		b1Alta = evento(org2, 10, AuditoriaAccion.CREATE, "POST", "/api/alumnos", "alumnos", 201, u2);
		b2LecturaSalud = evento(org2, 11, AuditoriaAccion.READ, "GET", "/api/alumnos/9/salud", "salud", 200, u2);
		b3Anonimo404 = evento(org2, 12, AuditoriaAccion.CREATE, "POST", "/api/v1/upload", "v1", 404, null);
		b4Error500 = evento(org2, 13, AuditoriaAccion.UPDATE, "PUT", "/api/grupos/1", "grupos", 500, u2);
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ administrador de plataforma sin escuela

	@Test
	void plataformaSinMembresiaEntraEnLaEscuelaPorDefectoSinDatosDeEscuela() throws Exception {
		String token = login(plataformaSola.getEmail());
		assertEquals(ORG1, jwtService.verificar(token).organizacionId());
		Cookie c = jwt(token);

		mvc.perform(get("/api/plataforma/errores").cookie(c)).andExpect(status().isOk());

		for (MockHttpServletRequestBuilder peticion : List.of(
				get("/api/alumnos"),
				get("/api/alumnos").param("page", "1").param("size", "5"),
				get("/api/alumnos/" + alumnoOrg1),
				get("/api/grupos"),
				get("/api/turnos"),
				get("/api/catalogo"),
				get("/api/admin/" + alumnoOrg1),
				get("/api/admin/users"),
				get("/api/admin/auditoria/eventos"),
				get("/api/admin/auditoria/eventos/" + a1Alta),
				get("/api/admin/auditoria/modulos"),
				get("/api/auth/roles"),
				get("/api/auth/user"),
				get("/api/auth/user/alumnos"),
				get("/api/alumnos/" + alumnoOrg1 + "/imagen"),
				get("/imagenes/alumnos/foto-" + sufijo + ".jpg"),
				get("/imagenes/org-1/alumnos/foto-" + sufijo + ".jpg"),
				get("/documentos/alumnos/doc-" + sufijo + ".pdf"),
				get("/documentos/org-1/alumnos/doc-" + sufijo + ".pdf"))) {
			MvcResult r = mvc.perform(peticion.cookie(c)).andReturn();
			assertEquals(403, r.getResponse().getStatus(), r.getRequest().getRequestURI());
		}

		// No puede pasar a ninguna escuela sin membresía.
		for (long org : new long[] { ORG1, org2 }) {
			mvc.perform(post("/api/auth/organizacion").cookie(c).contentType(MediaType.APPLICATION_JSON)
					.content("{\"organizacionId\":" + org + "}"))
					.andExpect(status().isForbidden());
		}
		mvc.perform(get("/api/auth/organizaciones").cookie(c))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void googleParaPlataformaSinMembresiaAbreSesionSoloDePlataforma() throws Exception {
		Usuario google = crearUsuario("plataforma-google-" + sufijo + "@example.test");
		marcarPlataforma(google);

		MockHttpServletResponse respuesta = loginGoogle(google.getEmail());

		assertEquals(frontendUrl + "/userpage", respuesta.getRedirectedUrl());
		String token = respuesta.getHeaders(HttpHeaders.SET_COOKIE).stream()
				.filter(c -> c.startsWith("jwt=") && !c.startsWith("jwt=;"))
				.map(c -> c.substring(4, c.indexOf(';')))
				.findFirst().orElseThrow();
		assertEquals(ORG1, jwtService.verificar(token).organizacionId());
		mvc.perform(get("/api/plataforma/errores").cookie(jwt(token))).andExpect(status().isOk());
		mvc.perform(get("/api/alumnos/" + alumnoOrg1).cookie(jwt(token))).andExpect(status().isForbidden());
		mvc.perform(get("/api/admin/users").cookie(jwt(token))).andExpect(status().isForbidden());
	}

	@Test
	void googleSinMarcaYSinMembresiaSigueRechazado() throws Exception {
		Usuario sinMarca = crearUsuario("google-sin-marca-" + sufijo + "@example.test");

		MockHttpServletResponse respuesta = loginGoogle(sinMarca.getEmail());

		assertTrue(respuesta.getRedirectedUrl().startsWith(frontendUrl + "/login?error=no_alumno_found"),
				respuesta.getRedirectedUrl());
		assertTrue(respuesta.getHeaders(HttpHeaders.SET_COOKIE).stream()
				.noneMatch(c -> c.startsWith("jwt=") && !c.startsWith("jwt=;")), "Sin cookie de sesión");
	}

	@Test
	void sinMarcaYSinMembresiaElLoginSigueRechazado() throws Exception {
		Usuario usuario = crearUsuario("sin-marca-" + sufijo + "@example.test");
		mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + usuario.getEmail() + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void plataformaConMembresiaSigueIgualQueAntesEnSuEscuela() throws Exception {
		String token = login(plataformaPropietario.getEmail());
		assertEquals(ORG1, jwtService.verificar(token).organizacionId());
		Cookie c = jwt(token);

		// Mismos roles visibles que un propietario (la autoridad de plataforma no se lista).
		mvc.perform(get("/api/auth/roles").cookie(c))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*]", containsInAnyOrder("ROLE_ADMIN")));
		mvc.perform(get("/api/admin/" + alumnoOrg1).cookie(c)).andExpect(status().isOk());
		mvc.perform(get("/api/alumnos/" + alumnoOrg1).cookie(c)).andExpect(status().isOk());
		mvc.perform(get("/api/grupos").cookie(c)).andExpect(status().isOk());
		mvc.perform(get("/api/catalogo").cookie(c)).andExpect(status().isOk());
		mvc.perform(get("/api/plataforma/errores").cookie(c)).andExpect(status().isOk());

		// El administrador de plataforma ve el registro completo de su escuela (D2), como antes.
		assertEquals(Set.of(a1Alta, a2Cambio, a3Baja, a4LecturaNormal, a5LecturaSalud, a6Error400, a7Error500,
				a8AltaAnonima, a9LecturaAnonima, a10Error403), idsAuditoria(c, null, null));
		mvc.perform(get("/api/admin/auditoria/eventos/" + a1Alta).cookie(c))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.payloadJson").value("{\"secreto\":\"PAYLOAD-" + sufijo + "\"}"))
				.andExpect(jsonPath("$.queryParamsJson").value("{\"q\":[\"QUERY-" + sufijo + "\"]}"));
		mvc.perform(get("/api/admin/auditoria/eventos/" + a6Error400).cookie(c)).andExpect(status().isOk());
	}

	// ------------------------------------------------------------------ /api/plataforma/errores

	@Test
	void erroresDePlataformaSoloParaLaAutoridadDePlataforma() throws Exception {
		mvc.perform(get("/api/plataforma/errores")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/plataforma/errores").cookie(jwt(login(propietario1.getEmail()))))
				.andExpect(status().isForbidden());
		mvc.perform(get("/api/plataforma/errores").cookie(jwt(login(propietario2.getEmail()))))
				.andExpect(status().isForbidden());
	}

	@Test
	void plataformaVeLosErroresDeTodasLasEscuelasSinDatosPersonales() throws Exception {
		Cookie c = jwt(login(plataformaSola.getEmail()));

		Set<Long> errores = Set.of(a6Error400, a7Error500, a8AltaAnonima, a9LecturaAnonima, a10Error403,
				b3Anonimo404, b4Error500);
		assertEquals(errores, idsErrores(c, ""));
		assertEquals(Set.of(b3Anonimo404, b4Error500), idsErrores(c, "&organizacionId=" + org2));
		assertEquals(Set.of(a6Error400, a7Error500, a8AltaAnonima, a9LecturaAnonima, a10Error403),
				idsErrores(c, "&organizacionId=" + ORG1));
		assertEquals(Set.of(a6Error400, a10Error403, b3Anonimo404), idsErrores(c, "&estado=4xx"));
		assertEquals(Set.of(a7Error500, b4Error500), idsErrores(c, "&estado=5XX"));
		assertEquals(Set.of(b4Error500), idsErrores(c, "&estado=5xx&organizacionId=" + org2));

		// Paginación: orden por fecha descendente, total correcto.
		MvcResult pagina = mvc.perform(get("/api/plataforma/errores?desde=" + DIA + "&hasta=" + DIA
				+ "&page=1&size=2").cookie(c))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(errores.size()))
				.andExpect(jsonPath("$.content", hasSize(2)))
				.andReturn();
		JsonNode cuerpo = json.readTree(pagina.getResponse().getContentAsString());
		assertEquals(b4Error500, cuerpo.get("content").get(0).get("id").asLong());
		assertEquals(b3Anonimo404, cuerpo.get("content").get(1).get("id").asLong());

		// Solo los campos acordados: nunca email, nombre, IP, agente, payload ni parámetros.
		MvcResult todo = mvc.perform(get("/api/plataforma/errores?desde=" + DIA + "&hasta=" + DIA + "&size=200")
				.cookie(c)).andExpect(status().isOk()).andReturn();
		String texto = todo.getResponse().getContentAsString();
		JsonNode primero = json.readTree(texto).get("content").get(0);
		List<String> campos = new ArrayList<>();
		primero.fieldNames().forEachRemaining(campos::add);
		assertEquals(Set.of("id", "fechaEvento", "organizacionId", "metodoHttp", "endpoint", "modulo", "estadoHttp",
				"usuarioId", "resumen"), Set.copyOf(campos));
		for (String prohibido : List.of("PAYLOAD-" + sufijo, "QUERY-" + sufijo, "10.9.8.7", "AgenteIT",
				propietario1.getEmail(), "Nombre IT")) {
			assertFalse(texto.contains(prohibido), prohibido);
		}
		JsonNode b4 = buscar(json.readTree(texto).get("content"), b4Error500);
		assertEquals(org2, b4.get("organizacionId").asLong());
		assertEquals(500, b4.get("estadoHttp").asInt());
		assertEquals(propietario2.getId(), b4.get("usuarioId").asLong());
		assertEquals("PUT", b4.get("metodoHttp").asText());
		assertEquals("/api/grupos/1", b4.get("endpoint").asText());
		assertEquals("grupos", b4.get("modulo").asText());
		assertEquals("IT " + sufijo + " 13", b4.get("resumen").asText());
		assertTrue(b4.get("fechaEvento").isNumber() || b4.get("fechaEvento").isTextual());
		assertTrue(buscar(json.readTree(texto).get("content"), b3Anonimo404).get("usuarioId").isNull());

		mvc.perform(get("/api/plataforma/errores?estado=3xx").cookie(c)).andExpect(status().isBadRequest());
		// Fechas fuera de rango (LocalDate.MAX hacía fallar plusDays con 500): 400.
		for (String[] fecha : List.of(new String[] { "hasta", LocalDate.MAX.toString() },
				new String[] { "desde", LocalDate.MIN.toString() }, new String[] { "hasta", "9999-12-31" },
				new String[] { "desde", "0999-12-31" })) {
			MvcResult r = mvc.perform(get("/api/plataforma/errores").param(fecha[0], fecha[1]).cookie(c)).andReturn();
			assertEquals(400, r.getResponse().getStatus(), fecha[0] + "=" + fecha[1]);
		}
		mvc.perform(get("/api/plataforma/errores?desde=1000-01-01&hasta=9998-12-31").cookie(c))
				.andExpect(status().isOk());
	}

	// ------------------------------------------------------------------ registro del propietario (D2)

	@Test
	void propietarioVeSoloOperacionesConSesionCorrectasYLecturasDeSalud() throws Exception {
		Cookie c = jwt(login(propietario1.getEmail()));
		Set<Long> visibles = Set.of(a1Alta, a2Cambio, a3Baja, a5LecturaSalud);

		assertEquals(visibles, idsAuditoria(c, null, null));
		assertEquals(visibles, idsAuditoria(c, "EXITO", null));
		assertEquals(visibles, idsAuditoria(c, "TODOS", null));
		assertEquals(visibles, idsAuditoria(c, null, "IT " + sufijo));
		// La búsqueda libre no mira el payload ni los parámetros (no se exponen).
		assertEquals(Set.of(), idsAuditoria(c, null, "PAYLOAD-" + sufijo));
		assertEquals(Set.of(), idsAuditoria(c, null, "QUERY-" + sufijo));

		// Detalle sin payload ni parámetros, con resumen.
		mvc.perform(get("/api/admin/auditoria/eventos/" + a1Alta).cookie(c))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(a1Alta))
				.andExpect(jsonPath("$.payloadJson").value(nullValue()))
				.andExpect(jsonPath("$.queryParamsJson").value(nullValue()))
				.andExpect(jsonPath("$.resumen").value("IT " + sufijo + " 0"));
		mvc.perform(get("/api/admin/auditoria/eventos/" + a5LecturaSalud).cookie(c)).andExpect(status().isOk());
		for (long oculto : new long[] { a4LecturaNormal, a6Error400, a7Error500, a8AltaAnonima, a9LecturaAnonima,
				a10Error403 }) {
			mvc.perform(get("/api/admin/auditoria/eventos/" + oculto).cookie(c)).andExpect(status().isNotFound());
		}

		// Los módulos que solo aparecen en errores no se listan.
		mvc.perform(get("/api/admin/auditoria/modulos").cookie(c))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", not(hasItem(moduloSoloError))))
				.andExpect(jsonPath("$", hasItem("salud")));
	}

	@Test
	void filtroErrorDelPropietarioDevuelveVacioConLaMismaFormaY200() throws Exception {
		Cookie c = jwt(login(propietario1.getEmail()));

		// Misma consulta que la alerta «Errores recientes» del panel (resultado=ERROR, page=1, size=1).
		MvcResult r = mvc.perform(get("/api/admin/auditoria/eventos").cookie(c)
				.param("desde", DIA.toString()).param("hasta", DIA.toString())
				.param("resultado", "ERROR").param("page", "1").param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0))
				.andExpect(jsonPath("$.content", hasSize(0)))
				.andReturn();
		// Sin fechas (cualquier error de la escuela): también vacío.
		mvc.perform(get("/api/admin/auditoria/eventos").cookie(c).param("resultado", "ERROR"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0))
				.andExpect(jsonPath("$.content", hasSize(0)));
		mvc.perform(get("/api/admin/auditoria/eventos").cookie(c).param("resultado", "ERROR")
				.param("incluirRuido", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));

		// Misma forma (claves de primer nivel) que la respuesta con resultados.
		MvcResult conDatos = mvc.perform(get("/api/admin/auditoria/eventos").cookie(c)
				.param("desde", DIA.toString()).param("hasta", DIA.toString()).param("page", "1").param("size", "1"))
				.andExpect(status().isOk()).andReturn();
		assertEquals(claves(conDatos), claves(r));
	}

	@Test
	void propietarioDeLaEscuela2NoVeNadaDeLaEscuela1() throws Exception {
		Cookie c = jwt(login(propietario2.getEmail()));

		assertEquals(Set.of(b1Alta, b2LecturaSalud), idsAuditoria(c, null, null));
		for (long ajeno : new long[] { a1Alta, a5LecturaSalud, a6Error400 }) {
			mvc.perform(get("/api/admin/auditoria/eventos/" + ajeno).cookie(c)).andExpect(status().isNotFound());
		}
		mvc.perform(get("/api/admin/auditoria/eventos/" + b1Alta).cookie(c))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.payloadJson").value(nullValue()));
		mvc.perform(get("/api/admin/auditoria/eventos/" + b4Error500).cookie(c)).andExpect(status().isNotFound());
	}

	// ---------------------------------------------------------------------------------

	private Set<Long> idsAuditoria(Cookie c, String resultado, String texto) throws Exception {
		MockHttpServletRequestBuilder peticion = get("/api/admin/auditoria/eventos").cookie(c)
				.param("desde", DIA.toString()).param("hasta", DIA.toString()).param("size", "200");
		if (resultado != null) {
			peticion.param("resultado", resultado);
		}
		if (texto != null) {
			peticion.param("texto", texto);
		}
		MvcResult r = mvc.perform(peticion).andExpect(status().isOk()).andReturn();
		return ids(json.readTree(r.getResponse().getContentAsString()));
	}

	private Set<Long> idsErrores(Cookie c, String filtros) throws Exception {
		MvcResult r = mvc.perform(get("/api/plataforma/errores?desde=" + DIA + "&hasta=" + DIA + "&size=200" + filtros)
				.cookie(c)).andExpect(status().isOk()).andReturn();
		JsonNode cuerpo = json.readTree(r.getResponse().getContentAsString());
		Set<Long> ids = ids(cuerpo);
		assertEquals(ids.size(), cuerpo.get("totalElements").asInt());
		return ids;
	}

	private static Set<Long> ids(JsonNode pagina) {
		Set<Long> ids = new HashSet<>();
		pagina.get("content").forEach(e -> ids.add(e.get("id").asLong()));
		return ids;
	}

	private static JsonNode buscar(JsonNode contenido, long id) {
		for (JsonNode e : contenido) {
			if (e.get("id").asLong() == id) {
				return e;
			}
		}
		throw new AssertionError("No está el evento " + id);
	}

	private Set<String> claves(MvcResult r) throws Exception {
		Set<String> claves = new HashSet<>();
		json.readTree(r.getResponse().getContentAsString()).fieldNames().forEachRemaining(claves::add);
		return claves;
	}

	private long evento(long org, int orden, AuditoriaAccion accion, String metodo, String endpoint, String modulo,
			int estado, Long usuarioId) {
		return TenantContext.calcularCon(org, () -> {
			AuditoriaEvento e = new AuditoriaEvento();
			e.setFechaEvento(java.util.Date.from(LocalDateTime.of(DIA, java.time.LocalTime.NOON).plusSeconds(orden)
					.atZone(ZoneId.systemDefault()).toInstant()));
			e.setAccion(accion);
			e.setMetodoHttp(metodo);
			e.setEndpoint(endpoint);
			e.setModulo(modulo);
			e.setEstadoHttp(estado);
			e.setUsuarioId(usuarioId);
			if (usuarioId != null) {
				e.setUsuarioEmail(usuarioId.equals(propietario1.getId()) ? propietario1.getEmail()
						: propietario2.getEmail());
				e.setUsuarioNombre("Nombre IT");
			}
			e.setIpCliente("10.9.8.7");
			e.setUserAgent("AgenteIT/1.0");
			e.setQueryParamsJson("{\"q\":[\"QUERY-" + sufijo + "\"]}");
			e.setPayloadJson("{\"secreto\":\"PAYLOAD-" + sufijo + "\"}");
			e.setPayloadTruncado(false);
			e.setResumen("IT " + sufijo + " " + orden);
			return eventos.save(e).getId();
		});
	}

	/** Éxito de Google con el manejador real (el proveedor no se puede invocar en el test). */
	private MockHttpServletResponse loginGoogle(String email) {
		Map<String, Object> atributos = Map.of("email", email, "email_verified", true);
		OAuth2User usuarioGoogle = mock(OAuth2User.class);
		when(usuarioGoogle.getAttributes()).thenReturn(atributos);
		when(usuarioGoogle.getAttribute(any())).thenAnswer(call -> atributos.get(call.<String>getArgument(0)));
		Authentication autenticacion = mock(Authentication.class);
		when(autenticacion.getPrincipal()).thenReturn(usuarioGoogle);
		MockHttpServletResponse respuesta = new MockHttpServletResponse();
		// Como en una petición anónima: el login se hace con la organización por defecto fijada.
		TenantContext.ejecutarCon(ORG1, () -> {
			try {
				oauth2SuccessHandler.onAuthenticationSuccess(new MockHttpServletRequest(), respuesta, autenticacion);
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		});
		return respuesta;
	}

	private String login(String email) throws Exception {
		MvcResult resultado = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return json.readTree(resultado.getResponse().getContentAsString()).get("token").asText();
	}

	private static Cookie jwt(String token) {
		return new Cookie("jwt", token);
	}

	private Usuario crearUsuario(String email) {
		return TenantContext.calcularCon(ORG1, () -> {
			Usuario usuario = new Usuario();
			usuario.setNombre("Usuario");
			usuario.setApellidos("Plataforma");
			usuario.setEmail(email);
			usuario.setContrasena(encoder.encode(CLAVE));
			usuario.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(usuario);
		});
	}

	/** La marca no se escribe desde la aplicación (columna no insertable ni actualizable): se pone por SQL. */
	private void marcarPlataforma(Usuario usuario) {
		assertEquals(1, jdbc.update("UPDATE usuario SET es_admin_plataforma = TRUE WHERE id = ?", usuario.getId()));
	}

	private Membresia dar(Usuario usuario, Long organizacionId, RolMembresia rol) {
		return TenantContext.calcularCon(ORG1,
				() -> membresias.save(new Membresia(usuario.getId(), organizacionId, rol)));
	}

	private long crearAlumno(String nombre) {
		Alumno alumno = new Alumno();
		alumno.setNombre(nombre);
		alumno.setApellidos("PRUEBA PLATAFORMA");
		alumno.setNumeroExpediente(contadores.siguienteNumeroExpediente());
		alumno.setFechaNacimiento(Date.valueOf(LocalDate.of(2010, 5, 5)));
		alumno.setDireccion("Calle Prueba 1");
		alumno.setTelefono("600000000");
		alumno.setActivo(true);
		alumno.setAutorizacionWeb(true);
		alumno.setEmail("alumno-plataforma-" + sufijo + "@example.test");
		return alumnos.save(alumno).getId();
	}
}
