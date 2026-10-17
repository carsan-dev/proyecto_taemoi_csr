package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.acceso.PredicadosAlcance;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Disciplina;
import com.taemoi.project.entities.Grupo;
import com.taemoi.project.entities.Preinscripcion;
import com.taemoi.project.entities.Turno;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.AlcanceSesion;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoDeporteRepository;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.ConvocatoriaRepository;
import com.taemoi.project.repositories.PreinscripcionRepository;
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
 *
 * <p>Entrega 6-2: también el listado de preinscripciones, los alumnos y el reporte de una convocatoria, y el
 * formulario público con una sola sede (D6-7 no se aplica: misma respuesta).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MoiskimdoGestionIdenticaIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	/** Temporada de las preinscripciones de este test (6-2): ninguna otra clase la usa. */
	private static final String TEMPORADA_IT = "1999-2000";

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
	@Autowired PreinscripcionRepository preinscripciones;
	@Autowired ConvocatoriaRepository convocatorias;
	@Autowired PlatformTransactionManager transacciones;

	@MockitoBean JavaMailSender mailSender;

	final String marca = UUID.randomUUID().toString().substring(0, 8);
	final List<Long> cuentas = new ArrayList<>();
	final List<Long> fichas = new ArrayList<>();
	final List<Long> gruposCreados = new ArrayList<>();
	final List<String> referenciasCreadas = new ArrayList<>();
	long convocatoria;
	long umbrete;
	long tkd;
	String tokenPropietario;
	String tokenGestor;

	@BeforeAll
	void preparar() {
		umbrete = jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = 1 ORDER BY id LIMIT 1",
				Long.class);
		tkd = jdbc.queryForObject("SELECT id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'TAEKWONDO'",
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

		// 6-2: preinscripciones (canceladas: no ocupan aforo) y una convocatoria con tres de las fichas.
		long grupo = grupo("Lunes");
		long turno = jdbc.queryForObject("SELECT MAX(id) FROM turno WHERE grupo_id = ?", Long.class, grupo);
		for (int i = 0; i < 3; i++) {
			referenciasCreadas.add(preinscripcionCancelada(grupo, turno));
		}
		jdbc.update("INSERT INTO convocatoria (fecha_convocatoria, organizacion_id, disciplina_id) VALUES "
				+ "('2026-06-01', 1, ?)", tkd);
		convocatoria = jdbc.queryForObject("SELECT MAX(id) FROM convocatoria WHERE organizacion_id = 1", Long.class);
		for (long ficha : fichas.subList(0, 3)) {
			jdbc.update("""
					INSERT INTO alumno_convocatoria (cuantia_examen, pagado, alumno_id, convocatoria_id, grado_actual,
					  grado_siguiente, organizacion_id) VALUES (20, 0, ?, ?, NULL, NULL, 1)""", ficha, convocatoria);
		}
	}

	/** Sin borrar nada: se retiran las cuentas y las fichas para no interferir con otras clases. */
	@AfterAll
	void retirar() {
		// Filas propias de la 6-2 sin dependientes (ni alumnos ni cargos): se borran.
		jdbc.update("DELETE FROM alumno_convocatoria WHERE convocatoria_id = ?", convocatoria);
		jdbc.update("DELETE FROM convocatoria WHERE id = ?", convocatoria);
		for (String referencia : referenciasCreadas) {
			jdbc.update("DELETE FROM preinscripcion WHERE referencia = ?", referencia);
		}
		for (Long grupo : gruposCreados) {
			jdbc.update("DELETE FROM turno WHERE grupo_id = ?", grupo);
			jdbc.update("DELETE FROM grupo WHERE id = ?", grupo);
		}
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

	// ------------------------------------------------------------------ 6-2

	@Test
	void preinscripciones_igualesQueAntes() throws Exception {
		// Filtrado por la temporada propia de este test: otras clases dejan en la escuela 1 solicitudes sin turnos, que
		// el listado (antes y después de la 6-2) no puede pintar. La consulta «de antes» lleva el mismo filtro.
		Sort orden = Sort.by(Sort.Order.desc("creadaEn"), Sort.Order.desc("id"));
		Specification<Preinscripcion> deEsteTest = (root, q, cb) -> cb.equal(root.get("temporada"), TEMPORADA_IT);
		for (int[] pagina : new int[][] { { 0, 100 }, { 1, 2 } }) {
			Page<Preinscripcion> antes = enOrg1(() -> preinscripciones.findAll(deEsteTest,
					PageRequest.of(pagina[0], pagina[1], orden)));
			String ruta = "/api/preinscripciones?temporada=" + TEMPORADA_IT + "&page=" + pagina[0] + "&size="
					+ pagina[1];
			JsonNode propietario = leer(ruta, tokenPropietario);
			assertEquals(antes.getContent().stream().map(Preinscripcion::getReferencia).toList(),
					referencias(propietario));
			assertEquals(antes.getTotalElements(), propietario.get("totalElements").asLong());
			assertEquals(propietario, leer(ruta, tokenGestor));
		}
		assertEquals(java.util.Set.copyOf(referenciasCreadas.subList(0, 3)), java.util.Set.copyOf(referencias(
				leer("/api/preinscripciones?temporada=" + TEMPORADA_IT + "&size=100", tokenGestor))));
	}

	@Test
	void convocatoria_alumnosYReporteIgualesQueAntes() throws Exception {
		List<Long> antes = enOrg1(() -> new TransactionTemplate(transacciones).execute(
				estado -> convocatorias.findById(convocatoria).orElseThrow().getAlumnosConvocatoria().stream()
						.map(ac -> ac.getAlumno().getId()).toList()));
		assertEquals(fichas.subList(0, 3).stream().sorted().toList(), antes.stream().sorted().toList());
		for (String token : List.of(tokenPropietario, tokenGestor)) {
			assertEquals(antes, alumnosDe(leer("/api/convocatorias/" + convocatoria + "/alumnos", token)));
			assertEquals(antes, alumnosDe(leer("/api/convocatorias/" + convocatoria + "/reporte", token)));
		}
		assertEquals(leer("/api/convocatorias/" + convocatoria + "/alumnos", tokenPropietario),
				leer("/api/convocatorias/" + convocatoria + "/alumnos", tokenGestor));
		assertEquals(leer("/api/convocatorias", tokenPropietario), leer("/api/convocatorias", tokenGestor));
	}

	/** D6-7 no se aplica con una sola sede: dos grupos de Umbrete en una solicitud pública, la respuesta de siempre. */
	@Test
	void formularioPublico_unaSolaSede_mismaRespuesta() throws Exception {
		long lunes = jdbc.queryForObject("SELECT MAX(id) FROM turno WHERE grupo_id = ?", Long.class, grupo("Lunes"));
		long martes = jdbc.queryForObject("SELECT MAX(id) FROM turno WHERE grupo_id = ?", Long.class,
				grupo("Martes"));
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("deporte", "TAEKWONDO");
		body.put("turnoIds", List.of(lunes, martes));
		body.put("nombre", "Eva");
		body.put("apellidos", "Moiskimdo " + marca);
		body.put("dni", String.format("%08d", java.util.concurrent.ThreadLocalRandom.current()
				.nextLong(10_000_000L, 99_999_999L)) + "M");
		body.put("fechaNacimiento", "1990-01-01");
		body.put("direccion", "Calle Mayor 1");
		body.put("telefono", "612345678");
		body.put("telefono2", "");
		body.put("email", "eva-" + marca + "@example.test");
		body.put("observaciones", "");
		body.put("tutorNombre", "");
		body.put("tutorDni", "");
		body.put("tieneDiscapacidad", false);
		body.put("consentimientoFotografico", false);
		body.put("aceptacionNormas", true);
		body.put("firmanteNombre", "Eva");
		body.put("firmaBase64", firma());
		MvcResult r = mvc.perform(post("/api/preinscripciones").header("X-Forwarded-For", "10.62.0.7")
				.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andReturn();
		assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		JsonNode respuesta = json.readTree(r.getResponse().getContentAsString());
		referenciasCreadas.add(respuesta.get("referencia").asText());
		List<String> campos = new ArrayList<>();
		respuesta.fieldNames().forEachRemaining(campos::add);
		assertEquals(List.of("referencia", "temporada", "tokenDocumento", "emailEnviado", "estado", "plazaAsignada",
				"posicionListaEspera", "mensaje"), campos);
		assertEquals("PENDIENTE", respuesta.get("estado").asText());
	}

	/** Grupo de Umbrete con un turno ese día (se borran al terminar). */
	private long grupo(String dia) {
		String nombre = "IT E6-2 Moiskimdo " + dia + " " + marca + " " + gruposCreados.size();
		jdbc.update("""
				INSERT INTO grupo (nombre, tipo, rango_edad_min, rango_edad_max, organizacion_id, disciplina_id, sede_id)
				VALUES (?, NULL, 0, 99, 1, ?, ?)""", nombre, tkd, umbrete);
		long grupo = jdbc.queryForObject("SELECT id FROM grupo WHERE nombre = ? AND organizacion_id = 1", Long.class,
				nombre);
		jdbc.update("""
				INSERT INTO turno (dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id)
				VALUES (?, '18:00', '19:00', NULL, ?, 1)""", dia, grupo);
		gruposCreados.add(grupo);
		return grupo;
	}

	private String preinscripcionCancelada(long grupo, long turno) {
		long plantilla = jdbc.queryForObject("""
				SELECT p.id FROM plantilla_preinscripcion p JOIN disciplina d ON d.id = p.disciplina_id
				WHERE p.organizacion_id = 1 AND d.codigo = 'TAEKWONDO' ORDER BY p.id LIMIT 1""", Long.class);
		String referencia = "IT-E62-M-" + UUID.randomUUID().toString().substring(0, 12);
		jdbc.update("""
				INSERT INTO preinscripcion (referencia, temporada, estado, nombre, apellidos, dni, identidad_hash,
				  fecha_nacimiento, direccion, telefono, email, tiene_discapacidad, consentimiento_fotografico,
				  aceptacion_normas, firmante_nombre, firma, plantilla_id, plantilla_snapshot, pdf_firmado,
				  token_documento_hash, creada_en, actualizada_en, grupo_id, organizacion_id, disciplina_id)
				VALUES (?, '1999-2000', 'CANCELADA', 'Ana', 'Prueba', NULL, ?, '1990-05-05', 'Calle 1', '600000000',
				  'ana@example.test', 0, 0, 1, 'Firmante Prueba', x'00', ?, '{}', x'00', ?, '2025-09-10 12:00:00',
				  '2025-09-10 12:00:00', ?, 1, ?)""", referencia, UUID.randomUUID().toString().replace("-", ""),
				plantilla, "e".repeat(64), grupo, tkd);
		long id = jdbc.queryForObject("SELECT id FROM preinscripcion WHERE referencia = ?", Long.class, referencia);
		jdbc.update("INSERT INTO preinscripcion_turno (preinscripcion_id, turno_id) VALUES (?, ?)", id, turno);
		return referencia;
	}

	private static List<String> referencias(JsonNode pagina) {
		List<String> referencias = new ArrayList<>();
		pagina.get("content").forEach(n -> referencias.add(n.get("referencia").asText()));
		return referencias;
	}

	private static List<Long> alumnosDe(JsonNode lista) {
		List<Long> ids = new ArrayList<>();
		lista.forEach(n -> ids.add(n.get("alumnoId").asLong()));
		return ids;
	}

	private static String firma() {
		try {
			java.awt.image.BufferedImage imagen = new java.awt.image.BufferedImage(320, 100,
					java.awt.image.BufferedImage.TYPE_INT_RGB);
			java.awt.Graphics2D g = imagen.createGraphics();
			g.setColor(java.awt.Color.WHITE);
			g.fillRect(0, 0, imagen.getWidth(), imagen.getHeight());
			g.setColor(java.awt.Color.BLACK);
			g.drawString("Firma de prueba", 20, 50);
			g.dispose();
			java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
			javax.imageio.ImageIO.write(imagen, "png", bytes);
			return "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(bytes.toByteArray());
		} catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
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
