package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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
 * {@code GET /api/alumnos?estado=}: el listado pide solo activos, solo inactivos o todos. Con {@code estado}, manda
 * sobre {@code incluirInactivos}; sin él, el comportamiento de siempre. Los anonimizados no salen nunca.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AlumnosEstadoIntegrationTest extends MySqlIntegrationTest {

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

	/** Activo y apto (taekwondo). */
	long activoApto;
	/** Activo, no apto (taekwondo). */
	long activoNoApto;
	/** Inactivo con taekwondo activo y apto. */
	long inactivoApto;
	/** Inactivo, sin deportes. */
	long inactivoNoApto;
	/** Inactivo y anonimizado: no sale en ningún caso. */
	long anonimizado;
	/** Ficha activa, de baja en taekwondo y activo en kickboxing. */
	long bajaTkdActivoKb;
	/** Ficha activa, de baja en taekwondo y sin otro deporte. */
	long soloBajaTkd;

	/** Los de esta clase con la ficha activa / inactiva (no anonimizados). */
	Set<Long> fichasActivas;
	Set<Long> fichasInactivas;
	Set<Long> todas;

	String tokenAdministrador;

	@BeforeAll
	void preparar() throws Exception {
		activoApto = alumno("ESTADO A");
		activoNoApto = alumno("ESTADO B");
		inactivoApto = alumno("ESTADO C");
		inactivoNoApto = alumno("ESTADO D");
		anonimizado = alumno("ESTADO E");
		bajaTkdActivoKb = alumno("ESTADO F");
		soloBajaTkd = alumno("ESTADO G");
		fichasActivas = Set.of(activoApto, activoNoApto, bajaTkdActivoKb, soloBajaTkd);
		fichasInactivas = Set.of(inactivoApto, inactivoNoApto);
		todas = new TreeSet<>(fichasActivas);
		todas.addAll(fichasInactivas);
		jdbc.update("UPDATE alumno SET activo = FALSE WHERE id IN (?, ?, ?)", inactivoApto, inactivoNoApto, anonimizado);
		jdbc.update("UPDATE alumno SET anonimizado_en = NOW() WHERE id = ?", anonimizado);

		Long grado = jdbc.queryForObject("SELECT MIN(id) FROM grado", Long.class);
		Long tkd = jdbc.queryForObject(
				"SELECT id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'TAEKWONDO'", Long.class);
		deporte(activoApto, tkd, grado, true);
		deporte(activoNoApto, tkd, grado, false);
		deporte(inactivoApto, tkd, grado, true);
		deporte(anonimizado, tkd, grado, true);
		Long kb = jdbc.queryForObject(
				"SELECT id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'KICKBOXING'", Long.class);
		deporte(bajaTkdActivoKb, tkd, grado, false);
		deporte(bajaTkdActivoKb, kb, grado, false);
		deporte(soloBajaTkd, tkd, grado, false);
		jdbc.update("UPDATE alumno_deporte SET activo = FALSE, fecha_baja = '2024-06-30' WHERE disciplina_id = ? "
				+ "AND alumno_id IN (?, ?)", tkd, bajaTkdActivoKb, soloBajaTkd);

		Usuario administrador = cuenta("estado-administrador");
		TenantContext.calcularCon(ORG1,
				() -> membresias.save(new Membresia(administrador.getId(), ORG1, RolMembresia.ADMINISTRADOR)));
		tokenAdministrador = login(administrador.getEmail());
	}

	/**
	 * Sin borrar nada: se retiran las cuentas y los alumnos para no interferir con otras clases. El anonimizado deja de
	 * estarlo: otras clases comparan las consultas de ids (JPQL y Criteria) y solo la JPQL excluye a los anonimizados.
	 */
	@AfterAll
	void retirar() {
		jdbc.update("UPDATE alumno SET anonimizado_en = NULL WHERE id = ?", anonimizado);
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
	void estado_activosInactivosOTodos() throws Exception {
		assertEquals(fichasActivas, ids("&estado=activos"));
		assertEquals(fichasInactivas, ids("&estado=inactivos"));
		assertEquals(todas, ids("&estado=todos"));
		// Sin distinguir mayúsculas.
		assertEquals(fichasInactivas, ids("&estado=INACTIVOS"));
	}

	@Test
	void estado_mandaSobreIncluirInactivos() throws Exception {
		assertEquals(fichasInactivas, ids("&estado=inactivos&incluirInactivos=true"));
		assertEquals(fichasInactivas, ids("&estado=inactivos&incluirInactivos=false"));
		assertEquals(fichasActivas, ids("&estado=activos&incluirInactivos=true"));
		assertEquals(todas,
				ids("&estado=todos&incluirInactivos=false"));
	}

	@Test
	void sinEstado_comoAntes() throws Exception {
		assertEquals(fichasActivas, ids(""));
		assertEquals(fichasActivas, ids("&incluirInactivos=false"));
		assertEquals(todas, ids("&incluirInactivos=true"));
	}

	@Test
	void estado_conAptoParaExamenYDeporte() throws Exception {
		assertEquals(Set.of(inactivoApto), ids("&estado=inactivos&aptoParaExamen=true"));
		assertEquals(Set.of(activoApto), ids("&estado=activos&aptoParaExamen=true"));
		assertEquals(Set.of(activoApto, inactivoApto), ids("&estado=todos&aptoParaExamen=true"));
		// Filtro por deporte (camino propio en el servidor): mismo criterio de estado.
		// Con deporte, activo es activo EN ESE deporte (decisión del 25/10, sustituye a D5); solo inactivos es su
		// complemento: ficha inactiva o de baja en el deporte. El de baja en taekwondo y activo en kickboxing es
		// inactivo con taekwondo y activo con kickboxing.
		assertEquals(Set.of(inactivoApto, soloBajaTkd, bajaTkdActivoKb), ids("&estado=inactivos&deporte=TAEKWONDO"));
		assertEquals(Set.of(activoApto, activoNoApto), ids("&estado=activos&deporte=TAEKWONDO"));
		// incluirInactivos=false (sin estado) con deporte sigue la misma regla.
		assertEquals(Set.of(activoApto, activoNoApto), ids("&deporte=TAEKWONDO"));
		assertEquals(Set.of(activoApto, activoNoApto), ids("&incluirInactivos=false&deporte=TAEKWONDO"));
		assertEquals(Set.of(activoApto, activoNoApto, inactivoApto, bajaTkdActivoKb, soloBajaTkd),
				ids("&estado=todos&deporte=TAEKWONDO"));
		assertEquals(Set.of(bajaTkdActivoKb), ids("&estado=activos&deporte=KICKBOXING"));
		assertEquals(Set.of(), ids("&estado=inactivos&deporte=KICKBOXING"));
	}

	@Test
	void conDeporte_activosMasInactivosSonTodos() throws Exception {
		for (String deporte : List.of("TAEKWONDO", "KICKBOXING")) {
			Set<Long> activos = ids("&estado=activos&deporte=" + deporte);
			Set<Long> inactivos = ids("&estado=inactivos&deporte=" + deporte);
			Set<Long> union = new TreeSet<>(activos);
			union.addAll(inactivos);
			assertEquals(activos.size() + inactivos.size(), union.size(), "sin solape, " + deporte);
			assertEquals(ids("&estado=todos&deporte=" + deporte), union, deporte);
			assertEquals(total("&estado=todos&deporte=" + deporte),
					total("&estado=activos&deporte=" + deporte) + total("&estado=inactivos&deporte=" + deporte),
					"totalElements, " + deporte);
		}
	}

	@Test
	void estadoInvalido_400() throws Exception {
		for (String estado : List.of("bajas", "activo", "1")) {
			mvc.perform(get("/api/alumnos?page=1&size=20&estado=" + estado).cookie(new Cookie("jwt", tokenAdministrador)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.mensaje").isNotEmpty());
		}
	}

	@Test
	void estadoVacio_comoAusente() throws Exception {
		assertEquals(ids(""), ids("&estado="));
		assertEquals(total(""), total("&estado="));
	}

	// ------------------------------------------------------------------ utilidades

	/** Ids de la primera página del listado, limitada por nombre a los alumnos de esta clase. */
	private Set<Long> ids(String filtros) throws Exception {
		MvcResult r = mvc.perform(get("/api/alumnos?page=1&size=20&nombre=" + sufijo + filtros)
				.cookie(new Cookie("jwt", tokenAdministrador))).andExpect(status().isOk()).andReturn();
		JsonNode contenido = json.readTree(r.getResponse().getContentAsString()).get("content");
		Set<Long> ids = new TreeSet<>();
		contenido.forEach(n -> ids.add(n.get("id").asLong()));
		return ids;
	}

	private long total(String filtros) throws Exception {
		MvcResult r = mvc.perform(get("/api/alumnos?page=1&size=20&nombre=" + sufijo + filtros)
				.cookie(new Cookie("jwt", tokenAdministrador))).andExpect(status().isOk()).andReturn();
		return json.readTree(r.getResponse().getContentAsString()).get("totalElements").asLong();
	}

	private void deporte(long alumno, Long disciplina, Long grado, boolean apto) {
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
				  organizacion_id, fecha_alta, fecha_alta_inicial, fecha_grado, disciplina_id, grado_id)
				VALUES (TRUE, ?, ?, 0, 1, 1, 1, '2023-09-01', '2023-09-01', '2024-01-01', ?, ?)""",
				apto, alumno, disciplina, grado);
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
			usuario.setApellidos("Estado alumnos");
			usuario.setEmail(nombre + "-" + sufijo + "@example.test");
			usuario.setContrasena(encoder.encode(CLAVE));
			usuario.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(usuario);
		});
		cuentas.add(creada.getId());
		return creada;
	}

	private long alumno(String nombre) {
		long id = TenantContext.calcularCon(ORG1, () -> {
			Alumno alumno = new Alumno();
			alumno.setNombre(nombre + " " + sufijo);
			alumno.setApellidos("PRUEBA ESTADO");
			alumno.setNumeroExpediente(contadores.siguienteNumeroExpediente());
			alumno.setFechaNacimiento(Date.valueOf(LocalDate.of(2013, 4, 4)));
			alumno.setDireccion("Calle Prueba 8");
			alumno.setTelefono("600000008");
			alumno.setActivo(true);
			alumno.setAutorizacionWeb(true);
			alumno.setEmail("estado-" + nombre.substring(7).toLowerCase() + "-" + sufijo + "@example.test");
			return alumnos.save(alumno).getId();
		});
		fichas.add(id);
		return id;
	}
}
