package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Red de seguridad de R2-P4 (listas ligeras): el JSON de {@code GET /api/grupos/resumen} y
 * {@code GET /api/turnos/resumen} para propietario (ADMIN), administrador (MANAGER) y responsable de la sede 1,
 * congelado en {@code src/test/resources/dorado/r2-p4/*.json}; un límite de sentencias SQL por petición; y la
 * comprobación de que, sobre los mismos datos, el resumen cuenta y alcanza lo mismo que los endpoints de siempre
 * ({@code GET /api/grupos} y {@code GET /api/turnos}, que no cambian).
 *
 * <h2>Datos</h2>
 * Sintéticos, con prefijo {@value #PREFIJO} y ids explícitos en rangos reservados, en la organización 1, más una
 * segunda sede INACTIVA (como en {@link PreinscripcionesListadoJsonDoradoIntegrationTest}). Cinco grupos: G1 (sede 1,
 * con rango de edad y dos turnos), G2 (sede 2), G3 (sede 1, kickboxing, sin rango, dos turnos), G4 (sin sede) y G5
 * (sede 1, vacío, sin turnos); más un turno sin grupo. Casos de {@code numAlumnos} en G1: activo con la ficha del
 * deporte activa (cuenta), activo con la ficha del deporte dada de baja (no), de baja (no), activo sin ninguna ficha
 * de deporte (cuenta, como en la lista de alumnos del grupo) y activo con solo la ficha de otro deporte (no); G3
 * recibe a un alumno con dos deportes activos (cuenta en G1 y en G3).
 *
 * <h2>Normalización</h2>
 * Ids sembrados → claves simbólicas ({@code grupo:G1}, {@code turno:T1}). Las respuestas no llevan fechas. Solo se
 * comparan las filas sembradas (otras clases de la batería pueden dejar grupos y turnos en la organización). Ambos
 * listados van ordenados por id por construcción, así que los arrays se comparan en orden estricto.
 *
 * <h2>Regenerar</h2>
 * Solo tras un cambio de contrato decidido: {@code ./mvnw -o test -Dtest=ResumenGruposTurnosJsonDoradoIntegrationTest
 * -Ddorado.regenerar=true}. Si difiere, la respuesta normalizada queda en {@code target/dorado-r2-p4-real/}.
 */
@SpringBootTest(properties = {
		"spring.jpa.properties.hibernate.session_factory.statement_inspector=com.taemoi.project.integracion.ContadorSentencias",
		// Mismas propiedades que MoiskimdoAlumnosJsonDoradoIntegrationTest: comparten contexto de Spring.
		"app.base.url=http://localhost:8080" })
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResumenGruposTurnosJsonDoradoIntegrationTest extends MySqlIntegrationTest {

	static final String PREFIJO = "DORADOP4";
	private static final boolean REGENERAR = Boolean.getBoolean("dorado.regenerar");
	private static final String RECURSOS = "dorado/r2-p4/";
	private static final Path FUENTES = Path.of("src", "test", "resources", "dorado", "r2-p4");
	private static final Path REALES = Path.of("target", "dorado-r2-p4-real");

	private static final long ALUMNO = 7_100_000L;
	private static final long GRUPO = 7_200_000L;
	private static final long TURNO = 7_300_000L;
	private static final long ALUMNO_DEPORTE = 7_400_000L;
	private static final long ANCHO = 9_999L;
	private static final int EXPEDIENTE = 6_600_000;
	private static final List<String> TABLAS_CON_RANGO = List.of("alumno_deporte", "alumno", "turno", "grupo");

	enum Rol {
		ADMIN, MANAGER, RESPONSABLE
	}

	/**
	 * Una petición capturada.
	 *
	 * @param limiteSql límite absoluto de sentencias SQL de la petición.
	 */
	record Caso(String nombre, String ruta, Rol rol, int limiteSql) {

		@Override
		public String toString() {
			return nombre;
		}
	}

	/**
	 * Límites: lo medido (6, 6, 8, 4, 4, 6) más un margen de 2. No dependen del número de filas: grupos = autenticación +
	 * grupos + alumnos + turnos; turnos = autenticación + una consulta. El responsable suma las de su alcance.
	 */
	static Stream<Caso> casos() {
		return Stream.of(
				new Caso("grupos_resumen_admin", "/api/grupos/resumen", Rol.ADMIN, 8),
				new Caso("grupos_resumen_manager", "/api/grupos/resumen", Rol.MANAGER, 8),
				new Caso("grupos_resumen_responsable", "/api/grupos/resumen", Rol.RESPONSABLE, 10),
				new Caso("turnos_resumen_admin", "/api/turnos/resumen", Rol.ADMIN, 6),
				new Caso("turnos_resumen_manager", "/api/turnos/resumen", Rol.MANAGER, 6),
				new Caso("turnos_resumen_responsable", "/api/turnos/resumen", Rol.RESPONSABLE, 8));
	}

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;

	@MockitoBean JavaMailSender mailSender;

	final Map<Long, String> simbolos = new HashMap<>();
	final Map<Rol, String> tokens = new HashMap<>();
	final List<Long> usuariosCreados = new ArrayList<>();
	final Map<String, String> medidas = new TreeMap<>();
	long sede1;
	Long sede2;

	// ------------------------------------------------------------------ siembra

	@BeforeAll
	void preparar() {
		borrarRangos();
		sede1 = jdbc.queryForObject("SELECT MIN(id) FROM sede WHERE organizacion_id = 1", Long.class);
		jdbc.update("INSERT INTO sede (organizacion_id, nombre, activa) VALUES (1, ?, FALSE)", PREFIJO + " sede 2");
		sede2 = jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = 1 AND nombre = ?", Long.class,
				PREFIJO + " sede 2");
		sembrar();
		tokens.put(Rol.ADMIN, token("PROPIETARIO", null));
		tokens.put(Rol.MANAGER, token("ADMINISTRADOR", null));
		tokens.put(Rol.RESPONSABLE, token("RESPONSABLE_SEDE", sede1));
	}

	private void sembrar() {
		long tkd = disciplina("TAEKWONDO");
		long kb = disciplina("KICKBOXING");
		long g1 = grupo(1, "Taekwondo Sede1", tkd, sede1, 6, 12);
		long g2 = grupo(2, "Taekwondo Sede2", tkd, sede2, null, null);
		long g3 = grupo(3, "Kickboxing Sede1", kb, sede1, null, null);
		long g4 = grupo(4, "Taekwondo SinSede", tkd, null, 4, null);
		long g5 = grupo(5, "Taekwondo Vacio", tkd, sede1, null, 9);
		// Ids de turno no correlativos con el orden de inserción: el orden esperado es el de id.
		turno(2, g1, "Miércoles", "17:00", "18:00", "Infantil");
		turno(1, g1, "Lunes", "17:00", "18:00", "Infantil");
		turno(3, g2, "Martes", "18:00", "19:00", null);
		turno(5, g3, "Viernes", "20:00", "21:00", "");
		turno(4, g3, "Jueves", "19:00", "20:00", "Kickboxing");
		turno(6, null, "Sábado", "10:00", "11:00", null);

		alumno(1, true);
		alumno(2, true);
		alumno(3, false);
		alumno(4, true);
		alumno(5, true);
		alumno(6, true);
		alumno(7, true);
		alumno(8, true);
		alumno(9, true);
		deporte(1, 1, tkd, true);
		deporte(2, 2, tkd, false);
		deporte(3, 3, tkd, true);
		// A04: sin ninguna ficha de deporte.
		deporte(4, 5, tkd, true);
		deporte(5, 5, kb, true);
		deporte(6, 6, kb, true);
		deporte(7, 7, tkd, true);
		deporte(8, 8, kb, true);
		deporte(9, 9, tkd, true);
		miembro(g1, 1);
		miembro(g1, 2);
		miembro(g1, 3);
		miembro(g1, 4);
		miembro(g1, 5);
		miembro(g1, 6);
		miembro(g2, 7);
		miembro(g3, 5);
		miembro(g3, 8);
		miembro(g4, 9);
		// g5: sin alumnos.
		simbolos.put(g5, "grupo:G5");
	}

	private long disciplina(String codigo) {
		return jdbc.queryForObject("SELECT id FROM disciplina WHERE organizacion_id = 1 AND codigo = ?", Long.class,
				codigo);
	}

	private long grupo(int n, String nombre, long disciplina, Long sede, Integer min, Integer max) {
		long id = GRUPO + n;
		jdbc.update("""
				INSERT INTO grupo (id, nombre, tipo, rango_edad_min, rango_edad_max, organizacion_id, disciplina_id, sede_id)
				VALUES (?, ?, NULL, ?, ?, 1, ?, ?)""", id, PREFIJO + " " + nombre, min, max, disciplina, sede);
		simbolos.put(id, "grupo:G" + n);
		return id;
	}

	private void turno(int n, Long grupo, String dia, String inicio, String fin, String tipo) {
		long id = TURNO + n;
		jdbc.update("INSERT INTO turno (id, dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id) VALUES "
				+ "(?, ?, ?, ?, ?, ?, 1)", id, dia, inicio, fin, tipo, grupo);
		simbolos.put(id, "turno:T" + n);
	}

	private void alumno(int n, boolean activo) {
		long id = ALUMNO + n;
		jdbc.update("""
				INSERT INTO alumno (id, activo, nombre, apellidos, numero_expediente, fecha_nacimiento, nif, direccion,
				  telefono, telefono2, genero, localidad, codigo_postal, email, fecha_alta, fecha_alta_inicial, fecha_baja,
				  autorizacion_web, tiene_discapacidad, tiene_derecho_examen, organizacion_id, sede_principal_id)
				VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?, NULL, 'FEMENINO', 'Sevilla', '41001', ?, '2020-09-01', '2019-09-01', ?,
				  1, 0, 0, 1, NULL)""",
				id, activo, "Alumno" + n, "Sintetico P4 " + n, EXPEDIENTE + n, Date.valueOf("2012-03-04"),
				"Calle P4 " + n, String.format("6200000%02d", n), String.format("dorado-p4-a%02d@example.test", n),
				activo ? null : Date.valueOf("2025-06-30"));
	}

	private void deporte(int n, int alumno, long disciplina, boolean activo) {
		long id = ALUMNO_DEPORTE + n;
		jdbc.update("""
				INSERT INTO alumno_deporte (id, activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
				  organizacion_id, fecha_alta, fecha_alta_inicial, fecha_grado, fecha_baja, disciplina_id, grado_id,
				  tarifa_id, cuantia_tarifa, rol_familiar, grupo_familiar)
				VALUES (?, ?, 0, ?, 0, 1, 0, 1, '2020-09-01', '2019-09-01', NULL, ?, ?, NULL, NULL, NULL, NULL, NULL)""",
				id, activo, ALUMNO + alumno, activo ? null : Date.valueOf("2025-06-30"), disciplina);
	}

	private void miembro(long grupo, int alumno) {
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", grupo, ALUMNO + alumno);
	}

	/** Cuenta de la organización 1 con ese rol (y, para el responsable, FAMILIA con su alumno propio, D6-15). */
	private String token(String rol, Long sede) {
		Usuario usuario = TenantContext.calcularCon(1L, () -> {
			Usuario u = new Usuario();
			u.setNombre("Dorado");
			u.setApellidos("P4 " + rol);
			u.setEmail("dorado-p4-" + rol.toLowerCase() + "-" + System.nanoTime() + "@example.test");
			u.setContrasena("fake-Password-123");
			u.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(u);
		});
		usuariosCreados.add(usuario.getId());
		if (sede != null) {
			jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, 1, 'FAMILIA')",
					usuario.getId());
			jdbc.update("INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen) VALUES "
					+ "(?, ?, 1, 'TUTOR', 'MANUAL')", usuario.getId(), ALUMNO + 9);
		}
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, 1, ?)", usuario.getId(), rol);
		if (sede != null) {
			long membresia = jdbc.queryForObject("SELECT id FROM membresia WHERE usuario_id = ? AND rol = ?", Long.class,
					usuario.getId(), rol);
			jdbc.update("INSERT INTO membresia_sede (membresia_id, sede_id) VALUES (?, ?)", membresia, sede);
		}
		Usuario cargado = TenantContext.calcularCon(1L, () -> usuarios.findById(usuario.getId()).orElseThrow());
		return jwtService.generateToken(cargado, 3_600_000, 1L);
	}

	/** Cada paso en su propio try/finally: un fallo no deja sin restaurar los siguientes. */
	@AfterAll
	void retirar() {
		try {
			for (Long u : usuariosCreados) {
				jdbc.update("DELETE FROM usuario_alumno WHERE usuario_id = ?", u);
				jdbc.update("DELETE FROM membresia_sede WHERE membresia_id IN (SELECT id FROM membresia WHERE usuario_id = ?)",
						u);
				jdbc.update("UPDATE membresia SET activa = FALSE WHERE usuario_id = ?", u);
			}
			borrarRangos();
		} finally {
			try {
				for (String tabla : TABLAS_CON_RANGO) {
					try {
						jdbc.execute("ALTER TABLE " + tabla + " AUTO_INCREMENT = 1");
					} catch (RuntimeException e) {
						System.err.println("Dorado P4: no se pudo restaurar AUTO_INCREMENT de " + tabla + ": " + e);
					}
				}
			} finally {
				try {
					if (sede2 != null) {
						jdbc.update("DELETE FROM sede WHERE id = ?", sede2);
					}
				} finally {
					System.out.println("Dorado P4 · sentencias por petición:");
					medidas.forEach((k, v) -> System.out.println("  " + k + " = " + v));
				}
			}
		}
	}

	private void borrarRangos() {
		borrar("DELETE FROM usuario_alumno WHERE alumno_id BETWEEN ? AND ?", ALUMNO);
		borrar("DELETE FROM alumno_grupo WHERE grupo_id BETWEEN ? AND ?", GRUPO);
		borrar("DELETE FROM alumno_turno WHERE turno_id BETWEEN ? AND ?", TURNO);
		borrar("DELETE FROM alumno_deporte WHERE id BETWEEN ? AND ?", ALUMNO_DEPORTE);
		borrar("DELETE FROM alumno WHERE id BETWEEN ? AND ?", ALUMNO);
		borrar("DELETE FROM turno WHERE id BETWEEN ? AND ?", TURNO);
		borrar("DELETE FROM grupo WHERE id BETWEEN ? AND ?", GRUPO);
	}

	private void borrar(String sql, long base) {
		jdbc.update(sql, base, base + ANCHO);
	}

	// ------------------------------------------------------------------ tests

	@ParameterizedTest(name = "{0}")
	@MethodSource("casos")
	void jsonIgualAlDoradoYSentenciasDentroDelLimite(Caso caso) throws Exception {
		peticion(caso.rol(), caso.ruta()); // calentamiento
		ContadorSentencias.Conteo<String> conteo = ContadorSentencias.contar(() -> peticion(caso.rol(), caso.ruta()));
		medidas.put(caso.nombre(), conteo.total() + " (límite " + caso.limiteSql() + ")");
		JsonNode real = normalizarNodo(sembradas(json.readTree(conteo.resultado())), "$");
		String fichero = caso.nombre() + ".json";
		if (REGENERAR) {
			escribir(fichero, real);
		}
		assertAll(() -> {
			if (!REGENERAR) {
				compararConDorado(fichero, real);
			}
		}, () -> assertTrue(conteo.total() <= caso.limiteSql(),
				caso.nombre() + ": " + conteo.total() + " sentencias SQL > límite " + caso.limiteSql()));
	}

	/**
	 * Sobre los mismos datos, el resumen de grupos ve los mismos grupos que {@code GET /api/grupos}, con tantos
	 * alumnos como trae su lista y los mismos turnos que {@code GET /api/turnos}; y el resumen de turnos, los mismos
	 * turnos que {@code GET /api/turnos}, con los mismos campos. Siempre por id ascendente.
	 */
	@ParameterizedTest(name = "{0}")
	@EnumSource(Rol.class)
	void losConteosYElAlcanceCoincidenConLosEndpointsDeSiempre(Rol rol) throws Exception {
		JsonNode gruposAntes = sembradas(json.readTree(peticion(rol, "/api/grupos")));
		JsonNode turnosAntes = sembradas(json.readTree(peticion(rol, "/api/turnos")));
		JsonNode gruposResumen = sembradas(json.readTree(peticion(rol, "/api/grupos/resumen")));
		JsonNode turnosResumen = sembradas(json.readTree(peticion(rol, "/api/turnos/resumen")));

		Map<Long, List<Long>> turnosPorGrupoAntes = new TreeMap<>();
		Map<Long, JsonNode> turnoAntes = new TreeMap<>();
		for (JsonNode t : turnosAntes) {
			turnoAntes.put(t.get("id").asLong(), t);
			if (!t.get("grupoId").isNull()) {
				turnosPorGrupoAntes.computeIfAbsent(t.get("grupoId").asLong(), k -> new ArrayList<>())
						.add(t.get("id").asLong());
			}
		}
		turnosPorGrupoAntes.values().forEach(l -> l.sort(null));

		// Mismos grupos, por id ascendente.
		List<Long> idsAntes = new ArrayList<>();
		gruposAntes.forEach(g -> idsAntes.add(g.get("id").asLong()));
		idsAntes.sort(null);
		List<Long> idsResumen = new ArrayList<>();
		gruposResumen.forEach(g -> idsResumen.add(g.get("id").asLong()));
		assertEquals(idsAntes, idsResumen, rol + ": grupos del resumen = grupos de /api/grupos, por id");
		assertTrue(rol != Rol.RESPONSABLE || idsResumen.size() == 3, rol + ": el responsable ve G1, G3 y G5");
		assertTrue(rol == Rol.RESPONSABLE || idsResumen.size() == 5, rol + ": la gestión ve los cinco grupos");

		Map<Long, JsonNode> grupoAntes = new TreeMap<>();
		gruposAntes.forEach(g -> grupoAntes.put(g.get("id").asLong(), g));
		for (JsonNode resumen : gruposResumen) {
			long id = resumen.get("id").asLong();
			JsonNode antes = grupoAntes.get(id);
			String grupo = simbolos.get(id);
			assertEquals(antes.get("alumnos").size(), resumen.get("numAlumnos").asInt(), rol + " " + grupo + ": numAlumnos");
			assertEquals(antes.get("nombre"), resumen.get("nombre"), rol + " " + grupo + ": nombre");
			assertEquals(antes.get("deporte"), resumen.get("deporte"), rol + " " + grupo + ": deporte");
			assertEquals(antes.get("rangoEdadMin"), resumen.get("rangoEdadMin"), rol + " " + grupo + ": rangoEdadMin");
			assertEquals(antes.get("rangoEdadMax"), resumen.get("rangoEdadMax"), rol + " " + grupo + ": rangoEdadMax");
			assertTrue(!resumen.has("alumnos"), grupo + ": el resumen no lleva alumnos");
			List<Long> turnosResumenDelGrupo = new ArrayList<>();
			resumen.get("turnos").forEach(t -> turnosResumenDelGrupo.add(t.get("id").asLong()));
			assertEquals(turnosPorGrupoAntes.getOrDefault(id, List.of()), turnosResumenDelGrupo,
					rol + " " + grupo + ": turnos del grupo, por id");
			for (JsonNode t : resumen.get("turnos")) {
				JsonNode otro = turnoAntes.get(t.get("id").asLong());
				assertEquals(otro.get("diaSemana"), t.get("diaSemana"));
				assertEquals(otro.get("horaInicio"), t.get("horaInicio"));
				assertEquals(otro.get("horaFin"), t.get("horaFin"));
			}
		}

		// Turnos: mismos, por id ascendente, con los mismos campos.
		List<Long> turnosIdsResumen = new ArrayList<>();
		for (JsonNode t : turnosResumen) {
			long id = t.get("id").asLong();
			turnosIdsResumen.add(id);
			JsonNode antes = turnoAntes.get(id);
			assertNotNull(antes, rol + " " + simbolos.get(id) + ": no está en /api/turnos");
			for (String campo : List.of("diaSemana", "horaInicio", "horaFin", "grupoId", "grupoNombre", "tipoGrupo")) {
				assertEquals(antes.get(campo), t.get(campo), rol + " " + simbolos.get(id) + ": " + campo);
			}
			assertTrue(!t.has("alumnos"), "el resumen de turnos no lleva alumnos");
		}
		assertEquals(new ArrayList<>(turnoAntes.keySet()), turnosIdsResumen,
				rol + ": turnos del resumen = turnos de /api/turnos, por id");
	}

	// ------------------------------------------------------------------ utilidades

	private String peticion(Rol rol, String ruta) throws Exception {
		MvcResult r = mvc.perform(get(ruta).cookie(new Cookie("jwt", tokens.get(rol)))).andReturn();
		assertEquals(200, r.getResponse().getStatus(), rol + " " + ruta + ": " + r.getResponse().getContentAsString());
		return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	/** Solo las filas sembradas (por el rango de ids), en el orden en que llegan. */
	private JsonNode sembradas(JsonNode lista) {
		assertTrue(lista.isArray(), "Se esperaba una lista: " + lista);
		ArrayNode filtrada = JsonNodeFactory.instance.arrayNode();
		lista.forEach(n -> {
			if (simbolos.containsKey(n.get("id").asLong())) {
				filtrada.add(n);
			}
		});
		return filtrada;
	}

	private JsonNode normalizarNodo(JsonNode nodo, String ruta) {
		if (nodo.isObject()) {
			ObjectNode o = JsonNodeFactory.instance.objectNode();
			List<String> campos = new ArrayList<>();
			nodo.fieldNames().forEachRemaining(campos::add);
			campos.sort(null);
			for (String campo : campos) {
				o.set(campo, normalizarNodo(nodo.get(campo), ruta + "." + campo));
			}
			return o;
		}
		if (nodo.isArray()) {
			ArrayNode a = JsonNodeFactory.instance.arrayNode();
			nodo.forEach(h -> a.add(normalizarNodo(h, ruta + "[*]")));
			return a;
		}
		if (nodo.isIntegralNumber() && (ruta.endsWith(".id") || ruta.endsWith(".grupoId"))) {
			String simbolo = simbolos.get(nodo.asLong());
			return simbolo != null ? TextNode.valueOf(simbolo) : nodo;
		}
		return nodo;
	}

	private void compararConDorado(String fichero, JsonNode real) throws IOException {
		JsonNode esperado;
		try (InputStream in = getClass().getClassLoader().getResourceAsStream(RECURSOS + fichero)) {
			assertNotNull(in, RECURSOS + fichero + " no existe: genéralo con -Ddorado.regenerar=true");
			esperado = json.readTree(in);
		}
		Optional<String> diferencia = MoiskimdoAlumnosJsonDoradoIntegrationTest.primeraDiferencia("$", esperado, real);
		if (diferencia.isPresent()) {
			Files.createDirectories(REALES);
			Files.writeString(REALES.resolve(fichero), aTexto(real), StandardCharsets.UTF_8);
			fail(RECURSOS + fichero + " difiere del JSON actual en " + diferencia.get() + "\n(respuesta normalizada en "
					+ REALES.resolve(fichero) + ")");
		}
	}

	private void escribir(String fichero, JsonNode nodo) throws IOException {
		Files.createDirectories(FUENTES);
		Files.writeString(FUENTES.resolve(fichero), aTexto(nodo), StandardCharsets.UTF_8);
	}

	private String aTexto(JsonNode nodo) throws IOException {
		DefaultPrettyPrinter impresora = new DefaultPrettyPrinter().withObjectIndenter(new DefaultIndenter("  ", "\n"))
				.withArrayIndenter(new DefaultIndenter("  ", "\n"));
		return json.writer(impresora).writeValueAsString(nodo) + "\n";
	}
}
