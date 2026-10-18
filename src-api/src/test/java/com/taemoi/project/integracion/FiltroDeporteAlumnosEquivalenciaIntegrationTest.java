package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * R2-P6 (decisión D5): red de seguridad del filtro por deporte del listado de alumnos, que pasa del navegador al
 * servidor. Compara {@code GET /api/alumnos?page&size&incluirInactivos&nombre&deporte} con lo que hacía
 * listado-alumnos ({@code actualizarAlumnosPaginadosCliente}) sobre {@code GET /api/alumnos/con-deportes}, que aquí se
 * reproduce tal cual:
 * <ul>
 * <li>sin inactivos, solo los que tienen algún deporte con {@code activo !== false} ({@code isAlumnoActivo});</li>
 * <li>los que tienen el deporte, activo o de baja ({@code practicaDeporte});</li>
 * <li>nombre: {@code trim().toLowerCase()} contenido en {@code nombre} o {@code apellidos} en minúsculas;</li>
 * <li>orden estable por {@code nombre} en minúsculas ({@code localeCompare}, aquí el {@link Collator} español) sobre
 * el orden de con-deportes; páginas por corte; {@code totalPaginas = ceil(n / tamaño)}.</li>
 * </ul>
 * Datos SINTÉTICOS en una escuela nueva ({@link EscuelaConSedesDePrueba} y ~90 alumnos aleatorios con semilla fija):
 * activos e inactivos, de baja en un deporte y activos en otro, varios deportes, sin deportes, nombres repetidos, con
 * tildes, comodines de LIKE en los apellidos; como gestión y como responsable de la sede A.
 *
 * <p>Nombres sin pares que solo difieran en tildes ni con «ñ»: ahí el orden del navegador ({@code localeCompare}) y
 * el de MySQL ({@code utf8mb4_0900_ai_ci}) no coinciden; está anotado como decisión pendiente en el informe de R2-P6.
 */
@SpringBootTest(properties = {
		"spring.jpa.properties.hibernate.session_factory.statement_inspector=com.taemoi.project.integracion.ContadorSentencias" })
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FiltroDeporteAlumnosEquivalenciaIntegrationTest extends MySqlIntegrationTest {

	private static final List<String> DEPORTES = List.of("TAEKWONDO", "KICKBOXING", "PILATES");
	private static final String[] NOMBRES = { "Lucía", "LUCÍA", "lucía", "Mateo", "Álvaro", "Ángela", "Andrés", "Sofía",
			"Hugo", "Hugo", "Martina", "Leo", "Irene", "Óscar", "Zoe", "Bruno", "Carla", "Élia", "Úrsula", "Inés" };
	private static final String[] APELLIDOS = { "Pérez Sintético", "Perez Sintetico", "García-López", "Martín 50% Prueba",
			"O_Neil Prueba", "Sintético", "Gómez", "Gomez" };
	/** Búsquedas: vacías, con espacios, mayúsculas, sin tilde, comodines de LIKE y nombre y apellidos juntos. */
	private static final List<String> BUSQUEDAS = List.of("", "   ", "lu", " LUCÍA ", "lucia", "pérez", "perez", "50%",
			"_", "%", "o_n", "hugo pérez", "gómez", "z");
	private static final int TAMANO = 5;
	/** Sentencias SQL de una página con deporte (medido 7 + margen 2, como los límites de los dorados de R2). */
	private static final int LIMITE_SQL_PAGINA_DEPORTE = 9;

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	Map<String, String> tokens;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-r2p6-deporte");
		Map<String, Long> disciplinas = new java.util.HashMap<>();
		disciplinas.put("TAEKWONDO", e.disciplina);
		int orden = 2;
		for (String codigo : List.of("KICKBOXING", "PILATES")) {
			jdbc.update("""
					INSERT INTO disciplina (organizacion_id, codigo, nombre, sistema_grados_id, edad_adulto, orden)
					VALUES (?, ?, ?, NULL, 14, ?)""", e.org, codigo, codigo + " IT", orden++);
			disciplinas.put(codigo, jdbc.queryForObject(
					"SELECT id FROM disciplina WHERE organizacion_id = ? AND codigo = ?", Long.class, e.org, codigo));
		}
		Random azar = new Random(20261018L);
		Long[] sedes = { e.sA, e.sB, null };
		long[] grupos = { e.gA, e.gB };
		for (int i = 0; i < 90; i++) {
			long alumno = e.alumno(NOMBRES[i % NOMBRES.length], sedes[azar.nextInt(3)], azar.nextInt(100) < 85);
			jdbc.update("UPDATE alumno SET apellidos = ? WHERE id = ?", APELLIDOS[azar.nextInt(APELLIDOS.length)], alumno);
			if (azar.nextInt(4) == 0) {
				jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", grupos[azar.nextInt(2)], alumno);
			}
			// Cada deporte con probabilidad 1/2 (algunos sin ninguno); de baja en él 3 de cada 10.
			boolean principal = true;
			for (String deporte : DEPORTES) {
				if (azar.nextBoolean()) {
					jdbc.update("""
							INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal,
							  tiene_licencia, organizacion_id, fecha_alta, fecha_alta_inicial, disciplina_id)
							VALUES (?, 0, ?, 0, ?, 0, ?, '2023-09-01', '2023-09-01', ?)""", azar.nextInt(10) >= 3,
							alumno, principal, e.org, disciplinas.get(deporte));
					principal = false;
				}
			}
		}
		tokens = Map.of("gestion", e.token(jwtService, usuarios, e.gestor), "responsableA",
				e.token(jwtService, usuarios, e.respA));
	}

	@Test
	void elFiltroDelServidorDevuelveLoMismoQueElNavegador() throws Exception {
		List<Executable> comprobaciones = new ArrayList<>();
		int casosConAlumnos = 0;
		for (Map.Entry<String, String> quien : tokens.entrySet()) {
			for (boolean inactivos : List.of(false, true)) {
				JsonNode conDeportes = leer("/api/alumnos/con-deportes?incluirInactivos=" + inactivos, quien.getValue());
				for (String deporte : List.of("TAEKWONDO", "KICKBOXING", "PILATES", "kickboxing", "NO_EXISTE")) {
					for (String busqueda : BUSQUEDAS) {
						List<Long> esperado = comoElNavegador(conDeportes, inactivos, deporte, busqueda);
						if (!esperado.isEmpty()) {
							casosConAlumnos++;
						}
						String caso = quien.getKey() + " inactivos=" + inactivos + " deporte=" + deporte + " nombre='"
								+ busqueda + "'";
						int paginas = (esperado.size() + TAMANO - 1) / TAMANO;
						// Todas las páginas y una más allá de la última (vacía, con el mismo total).
						for (int pagina = 1; pagina <= paginas + 1; pagina++) {
							// Parámetros tal cual (sin plantilla de URI): espacios, «%» y tildes llegan como los escribe el usuario.
							JsonNode real = leer(get("/api/alumnos").param("page", String.valueOf(pagina))
									.param("size", String.valueOf(TAMANO)).param("incluirInactivos", String.valueOf(inactivos))
									.param("deporte", deporte).param("nombre", busqueda), quien.getValue());
							List<Long> trozo = esperado.subList(Math.min(esperado.size(), (pagina - 1) * TAMANO),
									Math.min(esperado.size(), pagina * TAMANO));
							String donde = caso + " página " + pagina;
							List<Long> ids = ids(real.get("content"));
							int totalPaginas = real.get("totalPages").asInt();
							long total = real.get("totalElements").asLong();
							comprobaciones.add(() -> assertEquals(trozo, ids, donde));
							comprobaciones.add(() -> assertEquals(paginas, totalPaginas, donde + " (totalPages)"));
							comprobaciones.add(() -> assertEquals(esperado.size(), total, donde + " (totalElements)"));
						}
					}
				}
			}
		}
		int conAlumnos = casosConAlumnos;
		comprobaciones.add(() -> assertTrue(conAlumnos > 60, "pocos casos con alumnos: " + conAlumnos));
		assertAll(comprobaciones);
	}

	/**
	 * Producción pudo nacer de un esquema antiguo (baseline-on-migrate): con columnas utf8mb3 o latin1, un
	 * {@code COLLATE utf8mb4_bin} directo es el ERROR 1253 (un 500). Las funciones del filtro convierten antes. Se
	 * prueba su SQL sobre una tabla TEMPORAL (solo esta conexión): cambiar el juego de caracteres de las tablas
	 * compartidas alteraba los planes de otras clases.
	 */
	@Test
	void lasFuncionesBinariasNoDependenDelJuegoDeCaracteresDeLaColumna() {
		jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) conexion -> {
			try (java.sql.Statement s = conexion.createStatement()) {
				s.execute("""
						CREATE TEMPORARY TABLE r2p6_juegos (id INT PRIMARY KEY,
						  n3 VARCHAR(60) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci,
						  n3bin VARCHAR(60) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin,
						  l1 VARCHAR(60) CHARACTER SET latin1 COLLATE latin1_swedish_ci,
						  n4 VARCHAR(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci)""");
				try {
					s.execute("INSERT INTO r2p6_juegos VALUES (1, 'Lucía Pérez', 'Lucía Pérez', 'Lucía Pérez', 'Lucía Pérez'),"
							+ " (2, 'LUCIA PEREZ', 'LUCIA PEREZ', 'LUCIA PEREZ', 'LUCIA PEREZ'),"
							+ " (3, 'KICKBOXING', 'KICKBOXING', 'KICKBOXING', 'KICKBOXING'),"
							+ " (4, 'kickboxing', 'kickboxing', 'kickboxing', 'kickboxing')");
					for (String columna : List.of("n3", "n3bin", "l1", "n4")) {
						String minusculas = com.taemoi.project.config.FuncionesSqlContributor.PATRON_MINUSCULAS_UTF8MB4_BIN
								.replace("?1", columna);
						String binario = com.taemoi.project.config.FuncionesSqlContributor.PATRON_UTF8MB4_BIN
								.replace("?1", columna);
						// includes() del navegador: «lucía» (con tilde) solo en 1; «lucia» solo en 2; código exacto.
						assertEquals(List.of(1), ids(s, "SELECT id FROM r2p6_juegos WHERE LOCATE('lucía', " + minusculas
								+ ") > 0 ORDER BY id"), columna);
						assertEquals(List.of(2), ids(s, "SELECT id FROM r2p6_juegos WHERE LOCATE('lucia', " + minusculas
								+ ") > 0 ORDER BY id"), columna);
						assertEquals(List.of(3), ids(s, "SELECT id FROM r2p6_juegos WHERE " + binario
								+ " = 'KICKBOXING' ORDER BY id"), columna);
					}
					// Lo que se evita: el COLLATE directo sobre una columna que no es utf8mb4.
					java.sql.SQLException error = org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class,
							() -> s.executeQuery("SELECT id FROM r2p6_juegos WHERE n3 COLLATE utf8mb4_bin = 'x'").close());
					assertEquals(1253, error.getErrorCode());
				} finally {
					s.execute("DROP TEMPORARY TABLE r2p6_juegos");
				}
			}
			return null;
		});
	}

	private static List<Integer> ids(java.sql.Statement s, String sql) throws java.sql.SQLException {
		List<Integer> ids = new ArrayList<>();
		try (java.sql.ResultSet rs = s.executeQuery(sql)) {
			while (rs.next()) {
				ids.add(rs.getInt(1));
			}
		}
		return ids;
	}

	/** Los datos cubren lo que importa: de baja en el deporte pero activos en otro (D5), sin deportes, inactivos. */
	@Test
	void losDatosCubrenLosCasosDelCriterio() {
		Integer deBajaEnKbActivosEnOtro = jdbc.queryForObject("""
				SELECT COUNT(*) FROM alumno a WHERE a.organizacion_id = ? AND a.activo = TRUE
				  AND EXISTS (SELECT 1 FROM alumno_deporte ad JOIN disciplina d ON d.id = ad.disciplina_id
				              WHERE ad.alumno_id = a.id AND d.codigo = 'KICKBOXING' AND ad.activo = FALSE)
				  AND EXISTS (SELECT 1 FROM alumno_deporte ad WHERE ad.alumno_id = a.id AND ad.activo = TRUE)""",
				Integer.class, e.org);
		Integer sinDeportes = jdbc.queryForObject("""
				SELECT COUNT(*) FROM alumno a WHERE a.organizacion_id = ?
				  AND NOT EXISTS (SELECT 1 FROM alumno_deporte ad WHERE ad.alumno_id = a.id)""", Integer.class, e.org);
		Integer activosSinDeporteActivo = jdbc.queryForObject("""
				SELECT COUNT(*) FROM alumno a WHERE a.organizacion_id = ? AND a.activo = TRUE
				  AND EXISTS (SELECT 1 FROM alumno_deporte ad WHERE ad.alumno_id = a.id)
				  AND NOT EXISTS (SELECT 1 FROM alumno_deporte ad WHERE ad.alumno_id = a.id AND ad.activo = TRUE)""",
				Integer.class, e.org);
		Integer inactivos = jdbc.queryForObject(
				"SELECT COUNT(*) FROM alumno WHERE organizacion_id = ? AND activo = FALSE", Integer.class, e.org);
		assertAll(() -> assertTrue(deBajaEnKbActivosEnOtro >= 3, "de baja en KB y activos en otro: " + deBajaEnKbActivosEnOtro),
				() -> assertTrue(sinDeportes >= 3, "sin deportes: " + sinDeportes),
				() -> assertTrue(activosSinDeporteActivo >= 2, "ficha activa sin deporte activo: " + activosSinDeporteActivo),
				() -> assertTrue(inactivos >= 5, "inactivos: " + inactivos));
	}

	@Test
	void unaPaginaConDeporteNoCreceConLaEscuela() throws Exception {
		for (String token : tokens.values()) {
			String ruta = "/api/alumnos?page=1&size=" + TAMANO + "&incluirInactivos=true&deporte=KICKBOXING";
			leer(ruta, token);
			int sql = ContadorSentencias.contar(() -> leer(ruta, token)).total();
			assertTrue(sql <= LIMITE_SQL_PAGINA_DEPORTE, "Δ " + sql + " sentencias SQL > límite " + LIMITE_SQL_PAGINA_DEPORTE);
		}
	}

	// ------------------------------------------------------------------ el navegador, tal cual

	private static List<Long> comoElNavegador(JsonNode conDeportes, boolean inactivos, String deporte, String busqueda) {
		String termino = busqueda.strip().toLowerCase(Locale.ROOT);
		List<JsonNode> filtrados = new ArrayList<>();
		for (JsonNode alumno : conDeportes) {
			List<JsonNode> deportes = new ArrayList<>();
			alumno.path("deportes").forEach(deportes::add);
			if (!inactivos && deportes.stream().noneMatch(d -> !d.path("activo").isBoolean() || d.get("activo").asBoolean())) {
				continue;
			}
			if (deportes.stream().noneMatch(d -> deporte.equals(d.path("deporte").asText(null)))) {
				continue;
			}
			if (!termino.isEmpty() && !contiene(alumno.path("nombre"), termino) && !contiene(alumno.path("apellidos"), termino)) {
				continue;
			}
			filtrados.add(alumno);
		}
		Collator espanol = Collator.getInstance(Locale.forLanguageTag("es"));
		filtrados.sort(Comparator.comparing((JsonNode a) -> a.path("nombre").asText("").toLowerCase(Locale.ROOT), espanol));
		return filtrados.stream().map(a -> a.get("id").asLong()).toList();
	}

	private static boolean contiene(JsonNode texto, String termino) {
		return texto.isTextual() && texto.asText().toLowerCase(Locale.ROOT).contains(termino);
	}

	private JsonNode leer(String ruta, String token) throws Exception {
		return leer(get(ruta), token);
	}

	private JsonNode leer(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		MvcResult r = mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

	private static List<Long> ids(JsonNode lista) {
		List<Long> ids = new ArrayList<>();
		lista.forEach(n -> ids.add(n.get("id").asLong()));
		return ids;
	}
}
