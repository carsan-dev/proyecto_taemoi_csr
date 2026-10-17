package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.acceso.PredicadosAlcance;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Disciplina;
import com.taemoi.project.entities.Grupo;
import com.taemoi.project.entities.Turno;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.AlcanceSesion;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoDeporteRepository;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.GrupoRepository;
import com.taemoi.project.repositories.TurnoRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.CatalogoDisciplinasService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Entrega 6-1 (entrega6-plan.md §4.1 y §6): para Moiskimdo (organización 1) el propietario y el gestor obtienen
 * EXACTAMENTE lo mismo que antes en los listados que ahora filtran al responsable de sede: mismos ids, mismo orden y
 * mismos totales que las consultas de antes de la entrega 6 (las del repositorio sin predicado), y lo mismo con
 * ROLE_ADMIN que con ROLE_MANAGER. Con alcance total los predicados no restringen nada.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MoiskimdoGestionIdenticaIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired AlumnoRepository alumnos;
	@Autowired AlumnoDeporteRepository alumnoDeportes;
	@Autowired GrupoRepository grupos;
	@Autowired TurnoRepository turnos;
	@Autowired CatalogoDisciplinasService catalogo;

	@MockitoBean JavaMailSender mailSender;

	final String marca = UUID.randomUUID().toString().substring(0, 8);
	final List<Long> cuentas = new ArrayList<>();
	final List<Long> fichas = new ArrayList<>();
	String tokenPropietario;
	String tokenGestor;

	@BeforeAll
	void preparar() {
		long umbrete = jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = 1 ORDER BY id LIMIT 1",
				Long.class);
		long tkd = jdbc.queryForObject("SELECT id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'TAEKWONDO'",
				Long.class);
		// Alumnos con y sin sede principal (altas posteriores a V31), activos e inactivos, aptos o no.
		for (int i = 0; i < 4; i++) {
			long alumno = alumno("E6 MOISKIMDO " + i + " " + marca, i % 2 == 0 ? umbrete : null, i != 3);
			jdbc.update("""
					INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
					  organizacion_id, fecha_alta, fecha_alta_inicial, disciplina_id)
					VALUES (TRUE, ?, ?, 0, 1, 0, 1, '2023-09-01', '2023-09-01', ?)""", i < 2, alumno, tkd);
		}
		tokenPropietario = token("propietario", "PROPIETARIO");
		tokenGestor = token("gestor", "ADMINISTRADOR");
	}

	/** Sin borrar nada: se retiran las cuentas y las fichas para no interferir con otras clases. */
	@AfterAll
	void retirar() {
		for (Long cuenta : cuentas) {
			jdbc.update("UPDATE membresia SET activa = FALSE WHERE usuario_id = ?", cuenta);
		}
		for (Long ficha : fichas) {
			jdbc.update("UPDATE alumno SET activo = FALSE WHERE id = ?", ficha);
			jdbc.update("UPDATE alumno_deporte SET activo = FALSE, apto_para_examen = FALSE WHERE alumno_id = ?", ficha);
		}
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	@Test
	void listadoEIds_igualesQueAntes() throws Exception {
		for (boolean incluir : List.of(false, true)) {
			List<Long> antes = enOrg1(() -> alumnos.buscarIdsEnOrganizacionActiva(incluir));
			assertTrue(antes.containsAll(incluir ? fichas : fichas.subList(0, 3)));
			// La consulta nueva de ids (Criteria) con alcance total da lo mismo que la JPQL de siempre.
			assertEquals(antes, enOrg1(() -> alumnos.buscarIds(
					PredicadosAlcance.alumnosDeSusSedes(AlcanceSesion.TOTAL), incluir)));
			for (String token : List.of(tokenPropietario, tokenGestor)) {
				assertEquals(antes, numeros(leer("/api/alumnos/ids?incluirInactivos=" + incluir, token)));
				assertEquals(antes, ids(leer("/api/alumnos?incluirInactivos=" + incluir, token)));
			}
		}
		JsonNode paginaPropietario = leer("/api/alumnos?page=1&size=3&incluirInactivos=true", tokenPropietario);
		assertEquals(paginaPropietario, leer("/api/alumnos?page=1&size=3&incluirInactivos=true", tokenGestor));
		assertEquals(enOrg1(() -> alumnos.count()), paginaPropietario.get("totalElements").asLong());
		assertEquals(leer("/api/alumnos/con-deportes", tokenPropietario), leer("/api/alumnos/con-deportes", tokenGestor));
	}

	@Test
	void contadorYDistribucion_igualesQueAntes() throws Exception {
		long antes = enOrg1(() -> alumnos.count());
		Map<String, Long> distribucion = enOrg1(() -> {
			Map<String, Long> d = new LinkedHashMap<>();
			for (Disciplina disciplina : catalogo.listarTodas()) {
				long total = alumnoDeportes.countByDisciplinaIdAndActivoTrue(disciplina.getId());
				if (total > 0) {
					d.put(disciplina.getCodigo(), total);
				}
			}
			return d;
		});
		for (String token : List.of(tokenPropietario, tokenGestor)) {
			assertEquals(antes, leer("/api/alumnos/count", token).asLong());
			JsonNode respuesta = leer("/api/alumnos/deportes/distribucion", token);
			assertEquals(json.readTree(json.writeValueAsString(distribucion)), respuesta);
		}
	}

	@Test
	void aptos_igualesQueAntes() throws Exception {
		List<Long> antes = enOrg1(() -> alumnos.findAlumnosAptosParaExamen().stream().map(Alumno::getId).toList());
		assertTrue(antes.containsAll(fichas.subList(0, 2)));
		for (String token : List.of(tokenPropietario, tokenGestor)) {
			assertEquals(antes, ids(leer("/api/alumnos/aptos", token)));
		}
		assertEquals(leer("/api/alumnos/aptos/deporte?deporte=TAEKWONDO", tokenPropietario),
				leer("/api/alumnos/aptos/deporte?deporte=TAEKWONDO", tokenGestor));
		assertEquals(leer("/api/alumnos/aptos/convocatoria?deporte=TAEKWONDO", tokenPropietario),
				leer("/api/alumnos/aptos/convocatoria?deporte=TAEKWONDO", tokenGestor));
	}

	@Test
	void gruposYTurnos_igualesQueAntes() throws Exception {
		List<Long> gruposAntes = enOrg1(() -> grupos.findAll().stream().map(Grupo::getId).toList());
		List<Long> turnosAntes = enOrg1(() -> turnos.findAll().stream().map(Turno::getId).toList());
		assertEquals(gruposAntes, enOrg1(() -> grupos.findAll(PredicadosAlcance.gruposDeSusSedes(AlcanceSesion.TOTAL))
				.stream().map(Grupo::getId).toList()));
		for (String token : List.of(tokenPropietario, tokenGestor)) {
			assertEquals(gruposAntes, ids(leer("/api/grupos", token)));
			assertEquals(turnosAntes, ids(leer("/api/turnos", token)));
		}
	}

	// ------------------------------------------------------------------ utilidades

	private <T> T enOrg1(java.util.function.Supplier<T> accion) {
		return TenantContext.calcularCon(ORG1, accion);
	}

	private String token(String nombre, String rol) {
		String email = "it-e6-moiskimdo-" + nombre + "-" + marca + "@example.test";
		Usuario usuario = enOrg1(() -> {
			Usuario u = new Usuario();
			u.setNombre("Usuario");
			u.setApellidos("Prueba");
			u.setEmail(email);
			u.setContrasena("fake-Password-123");
			u.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(u);
		});
		cuentas.add(usuario.getId());
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, 1, ?)", usuario.getId(), rol);
		return jwtService.generateToken(usuario, 600_000, ORG1);
	}

	private long alumno(String nombre, Long sede, boolean activo) {
		long expediente = jdbc.queryForObject("SELECT COALESCE(MAX(numero_expediente), 60000) + 1 FROM alumno",
				Long.class);
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id, sede_principal_id)
				VALUES (?, 'PRUEBA', 1, 'Calle 1', '2010-01-01', ?, ?, '600000000', 0, 1, ?)""",
				activo, nombre, expediente, sede);
		long id = jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = 1",
				Long.class, expediente);
		fichas.add(id);
		return id;
	}

	private JsonNode leer(String ruta, String token) throws Exception {
		MvcResult r = mvc.perform(get(ruta).cookie(new Cookie("jwt", token))).andReturn();
		assertEquals(200, r.getResponse().getStatus(), ruta + ": " + r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString());
	}

	private static List<Long> ids(JsonNode lista) {
		List<Long> ids = new ArrayList<>();
		lista.forEach(n -> ids.add(n.get("id").asLong()));
		return ids;
	}

	private static List<Long> numeros(JsonNode lista) {
		List<Long> ids = new ArrayList<>();
		lista.forEach(n -> ids.add(n.asLong()));
		return ids;
	}
}
