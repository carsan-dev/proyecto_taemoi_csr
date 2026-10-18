package com.taemoi.project.integracion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
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
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Membresia;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.MembresiaRepository;
import com.taemoi.project.repositories.UsuarioRepository;

import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;

/**
 * Arnés de MEDICIÓN (rendimiento de alumnos, R2), no un test de regresión: siembra datos SINTÉTICOS en la
 * organización 1 (800 alumnos, 16 grupos, ~40 turnos, deportes, usuarios y tutores) y mide, por endpoint, nº de
 * sentencias SQL, cargas de entidades/colecciones y tiempo. Escribe el resultado y las respuestas JSON en
 * {@code target/r2-medicion/}.
 *
 * <p>Fuera de la batería por defecto: deja 800 alumnos en la organización 1 (falsearía las demás clases) y tarda
 * ~1 min. Solo corre con {@code -Dmedicion.rendimiento=true}, en solitario:
 * {@code ./mvnw -o test -Dtest=MedicionRendimientoAlumnosIntegrationTest -Dmedicion.rendimiento=true -Dr2.salida=<carpeta>}.
 * La red de seguridad del contrato y los límites de sentencias están en
 * {@link MoiskimdoAlumnosJsonDoradoIntegrationTest}.
 */
@EnabledIfSystemProperty(named = "medicion.rendimiento", matches = "true")
@SpringBootTest(properties = {
		"spring.jpa.properties.hibernate.generate_statistics=true",
		"spring.jpa.properties.hibernate.session_factory.statement_inspector=com.taemoi.project.integracion.ContadorSentencias",
		"logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN" })
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MedicionRendimientoAlumnosIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String CLAVE = "Clave-Segura-123";
	private static final int N_ALUMNOS = Integer.getInteger("r2.alumnos", 800);
	private static final int REPETICIONES = 5;
	private static final Path SALIDA = Path.of("target", System.getProperty("r2.salida", "r2-medicion"));

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired PasswordEncoder encoder;
	@Autowired UsuarioRepository usuarios;
	@Autowired MembresiaRepository membresias;
	@Autowired EntityManagerFactory emf;

	@MockitoBean JavaMailSender mailSender;

	String token;
	long alumnoMuestra;
	long grupoMuestra;
	long turnoMuestra;
	final List<Long> grupos = new ArrayList<>();

	@BeforeAll
	void sembrar() throws Exception {
		long t0 = System.nanoTime();
		Long sede = jdbc.queryForObject("SELECT MIN(id) FROM sede WHERE organizacion_id = 1", Long.class);
		if (sede == null) {
			jdbc.update("INSERT INTO sede (organizacion_id, nombre, activa) VALUES (1, 'Sede R2', TRUE)");
			sede = jdbc.queryForObject("SELECT MIN(id) FROM sede WHERE organizacion_id = 1", Long.class);
		}
		long tkd = jdbc.queryForObject(
				"SELECT id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'TAEKWONDO'", Long.class);
		long kb = jdbc.queryForObject(
				"SELECT id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'KICKBOXING'", Long.class);
		Long grado = jdbc.queryForObject("SELECT MIN(id) FROM grado", Long.class);

		// Grupos: 10 de taekwondo y 6 de kickboxing; 2 o 3 turnos por grupo y 3 turnos sin grupo.
		List<Long> gruposTkd = new ArrayList<>();
		List<Long> gruposKb = new ArrayList<>();
		Map<Long, List<Long>> turnosDe = new LinkedHashMap<>();
		String[] dias = { "Lunes", "Martes", "Miércoles", "Jueves", "Viernes" };
		for (int g = 0; g < 16; g++) {
			boolean esTkd = g < 10;
			jdbc.update("INSERT INTO grupo (nombre, tipo, organizacion_id, disciplina_id, sede_id, rango_edad_min, rango_edad_max) "
					+ "VALUES (?, NULL, 1, ?, ?, ?, ?)", "R2 Grupo " + (esTkd ? "TKD " : "KB ") + g, esTkd ? tkd : kb, sede,
					4 + g, 10 + g);
			long id = jdbc.queryForObject("SELECT MAX(id) FROM grupo WHERE organizacion_id = 1", Long.class);
			(esTkd ? gruposTkd : gruposKb).add(id);
			grupos.add(id);
			List<Long> turnos = new ArrayList<>();
			for (int t = 0; t < (g % 3 == 0 ? 3 : 2); t++) {
				jdbc.update("INSERT INTO turno (dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id) "
						+ "VALUES (?, ?, ?, NULL, ?, 1)", dias[(g + t) % 5], String.format("%02d:00", 16 + t),
						String.format("%02d:00", 17 + t), id);
				turnos.add(jdbc.queryForObject("SELECT MAX(id) FROM turno WHERE organizacion_id = 1", Long.class));
			}
			turnosDe.put(id, turnos);
		}
		for (int t = 0; t < 3; t++) {
			jdbc.update("INSERT INTO turno (dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id) "
					+ "VALUES ('Sábado', '10:00', '11:00', NULL, NULL, 1)");
		}

		// Alumnos: 90 % activos; todos con taekwondo principal; uno de cada 3 también kickboxing (inactivo 1 de 9).
		List<Object[]> filas = new ArrayList<>();
		for (int i = 0; i < N_ALUMNOS; i++) {
			filas.add(new Object[] { i % 10 != 0, String.format("SINTETICO %03d", (N_ALUMNOS - i)), "Calle Sintética " + i,
					Date.valueOf(LocalDate.of(2005 + (i % 15), 1 + (i % 12), 1 + (i % 27))),
					String.format("R2 Alumno %03d", i), 900000 + i, "600" + String.format("%06d", i),
					"r2-alumno-" + i + "@example.test", sede, Date.valueOf(LocalDate.of(2020, 9, 1))});
		}
		jdbc.batchUpdate("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre, numero_expediente,
				  telefono, tiene_derecho_examen, tiene_discapacidad, email, organizacion_id, sede_principal_id, fecha_alta,
				  fecha_alta_inicial)
				VALUES (?, ?, 1, ?, ?, ?, ?, ?, 0, 0, ?, 1, ?, ?, ?)""",
				filas.stream().map(f -> new Object[] { f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8], f[9], f[9] })
						.toList());
		List<Long> ids = jdbc.queryForList(
				"SELECT id FROM alumno WHERE organizacion_id = 1 AND numero_expediente >= 900000 ORDER BY numero_expediente",
				Long.class);

		List<Object[]> deportes = new ArrayList<>();
		List<Object[]> enGrupo = new ArrayList<>();
		List<Object[]> enTurno = new ArrayList<>();
		List<Object[]> tutores = new ArrayList<>();
		for (int i = 0; i < ids.size(); i++) {
			long a = ids.get(i);
			deportes.add(new Object[] { true, a, tkd, grado });
			long gt = gruposTkd.get(i % gruposTkd.size());
			enGrupo.add(new Object[] { gt, a });
			enTurno.add(new Object[] { a, turnosDe.get(gt).get(0) });
			if (i % 2 == 0) {
				enTurno.add(new Object[] { a, turnosDe.get(gt).get(1) });
			}
			if (i % 3 == 0) {
				deportes.add(new Object[] { i % 9 != 0, a, kb, null });
				long gk = gruposKb.get((i / 3) % gruposKb.size());
				enGrupo.add(new Object[] { gk, a });
				enTurno.add(new Object[] { a, turnosDe.get(gk).get(0) });
			}
			if (i % 5 == 0) {
				tutores.add(new Object[] { a, "Tutor R2 " + i });
			}
		}
		jdbc.batchUpdate("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
				  organizacion_id, fecha_alta, fecha_alta_inicial, fecha_grado, disciplina_id, grado_id)
				VALUES (?, 0, ?, 0, 0, 0, 1, '2020-09-01', '2020-09-01', '2021-01-01', ?, ?)""", deportes);
		jdbc.update("UPDATE alumno_deporte ad JOIN disciplina d ON d.id = ad.disciplina_id SET ad.principal = 1 "
				+ "WHERE d.codigo = 'TAEKWONDO' AND ad.alumno_id IN (SELECT id FROM alumno WHERE numero_expediente >= 900000)");
		jdbc.batchUpdate("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", enGrupo);
		jdbc.batchUpdate("INSERT INTO alumno_turno (alumno_id, turno_id) VALUES (?, ?)", enTurno);
		jdbc.batchUpdate("""
				INSERT INTO tutor (organizacion_id, alumno_id, nombre, nif, parentesco, responsable_legal, orden)
				VALUES (1, ?, ?, NULL, 'TUTOR_LEGAL', TRUE, 0)""", tutores);
		// Uno de cada 4 alumnos con cuenta de usuario vinculada.
		String hash = encoder.encode(CLAVE);
		List<Object[]> cuentas = new ArrayList<>();
		for (int i = 0; i < ids.size(); i += 4) {
			cuentas.add(new Object[] { "Familia", "R2 " + i, "r2-familia-" + i + "@example.test", hash, ids.get(i) });
		}
		jdbc.batchUpdate("INSERT INTO usuario (nombre, apellidos, email, contrasena, auth_provider, alumno_id, "
				+ "recordatorio_racha_email_habilitado) VALUES (?, ?, ?, ?, 'LOCAL', ?, 0)", cuentas);

		alumnoMuestra = ids.get(12);
		grupoMuestra = gruposTkd.get(2);
		turnoMuestra = turnosDe.get(grupoMuestra).get(0);

		Usuario gestor = TenantContext.calcularCon(ORG1, () -> {
			Usuario u = new Usuario();
			u.setNombre("Gestor");
			u.setApellidos("R2");
			u.setEmail("r2-gestor@example.test");
			u.setContrasena(hash);
			u.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(u);
		});
		TenantContext.calcularCon(ORG1, () -> membresias.save(new Membresia(gestor.getId(), ORG1, RolMembresia.PROPIETARIO)));
		token = login(gestor.getEmail());
		System.out.printf("R2 siembra: %d alumnos, %d grupos en %d ms%n", ids.size(), grupos.size(),
				(System.nanoTime() - t0) / 1_000_000);
	}

	@Test
	void medir() throws Exception {
		Files.createDirectories(SALIDA);
		Map<String, String> rutas = new LinkedHashMap<>();
		rutas.put("base_count", "/api/alumnos/count");
		rutas.put("alumnos_pagina", "/api/alumnos?page=1&size=9&incluirInactivos=false");
		rutas.put("alumnos_pagina_inactivos", "/api/alumnos?page=1&size=9&incluirInactivos=true");
		rutas.put("alumnos_sin_paginar", "/api/alumnos?incluirInactivos=false");
		rutas.put("alumnos_con_deportes", "/api/alumnos/con-deportes?incluirInactivos=false");
		rutas.put("alumnos_ids", "/api/alumnos/ids?incluirInactivos=false");
		rutas.put("alumno_ficha", "/api/alumnos/" + alumnoMuestra);
		rutas.put("alumno_completo", "/api/alumnos/" + alumnoMuestra + "/completo");
		rutas.put("alumno_grupos", "/api/alumnos/" + alumnoMuestra + "/grupos");
		rutas.put("alumno_turnos", "/api/alumnos/" + alumnoMuestra + "/turnos");
		rutas.put("alumno_deportes", "/api/alumnos/" + alumnoMuestra + "/deportes");
		rutas.put("admin_alumno_entidad", "/api/admin/" + alumnoMuestra);
		rutas.put("grupos", "/api/grupos");
		rutas.put("grupo_ficha", "/api/grupos/" + grupoMuestra);
		rutas.put("grupo_turnos", "/api/grupos/" + grupoMuestra + "/turnos");
		rutas.put("grupos_conteo", "/api/grupos/conteo-alumnos");
		rutas.put("turnos", "/api/turnos");
		rutas.put("grupos_resumen", "/api/grupos/resumen"); // R2-P4
		rutas.put("turnos_resumen", "/api/turnos/resumen"); // R2-P4
		rutas.put("turno_ficha", "/api/turnos/" + turnoMuestra);
		rutas.put("turnos_dto", "/api/turnos/dto");
		rutas.put("turnos_dto_anonimo", "ANON:/api/turnos/dto");
		// R2-P6: el listado filtrado por deporte (antes, el navegador pedía con-deportes entero y filtraba) y los turnos
		// y grupos públicos de preinscripción de un deporte.
		rutas.put("alumnos_deporte_kb_pagina", "/api/alumnos?page=1&size=8&incluirInactivos=false&deporte=KICKBOXING");
		rutas.put("alumnos_deporte_kb_inactivos",
				"/api/alumnos?page=1&size=8&incluirInactivos=true&deporte=KICKBOXING");
		rutas.put("alumnos_deporte_kb_nombre",
				"/api/alumnos?page=1&size=8&incluirInactivos=false&deporte=KICKBOXING&nombre=r2%20alumno%2001");
		rutas.put("public_turnos_tkd", "/api/preinscripciones/public/turnos/TAEKWONDO");
		rutas.put("public_turnos_kb", "/api/preinscripciones/public/turnos/KICKBOXING");
		rutas.put("public_grupos_tkd", "ANON:/api/preinscripciones/public/grupos/TAEKWONDO");
		rutas.put("public_grupos_kb", "ANON:/api/preinscripciones/public/grupos/KICKBOXING");

		Statistics st = emf.unwrap(SessionFactory.class).getStatistics();
		st.setStatisticsEnabled(true);

		StringBuilder tabla = new StringBuilder();
		tabla.append("| endpoint | ruta | SQL | entidades cargadas | ms (mediana de ").append(REPETICIONES)
				.append(") | KB |\n|---|---|---|---|---|---|\n");
		StringBuilder detalle = new StringBuilder();
		for (Map.Entry<String, String> e : rutas.entrySet()) {
			boolean anonimo = e.getValue().startsWith("ANON:");
			String ruta = anonimo ? e.getValue().substring(5) : e.getValue();
			peticion(ruta, anonimo); // calentamiento
			st.clear();
			ContadorSentencias.Conteo<String> conteo = ContadorSentencias.contar(() -> peticion(ruta, anonimo));
			String cuerpo = conteo.resultado();
			List<String> sql = conteo.sentencias();
			long cargas = st.getEntityLoadCount();
			Map<String, Long> cargasPorEntidad = new TreeMap<>();
			Map<String, Long> fetchPorEntidad = new TreeMap<>();
			for (String n : st.getEntityNames()) {
				var es = st.getEntityStatistics(n);
				if (es.getLoadCount() > 0) cargasPorEntidad.put(simple(n), es.getLoadCount());
				if (es.getFetchCount() > 0) fetchPorEntidad.put(simple(n), es.getFetchCount());
			}
			Map<String, Long> coleccion = new TreeMap<>();
			for (String r : st.getCollectionRoleNames()) {
				var cs = st.getCollectionStatistics(r);
				if (cs.getFetchCount() > 0) coleccion.put(simple(r), cs.getFetchCount());
			}
			long[] tiempos = new long[REPETICIONES];
			for (int i = 0; i < REPETICIONES; i++) {
				long t = System.nanoTime();
				peticion(ruta, anonimo);
				tiempos[i] = (System.nanoTime() - t) / 1_000_000;
			}
			Arrays.sort(tiempos);
			long mediana = tiempos[REPETICIONES / 2];
			Files.writeString(SALIDA.resolve(e.getKey() + ".json"), cuerpo, StandardCharsets.UTF_8);
			tabla.append(String.format("| %s | `%s` | %d | %d | %d | %.1f |%n", e.getKey(), e.getValue(), sql.size(),
					cargas, mediana, cuerpo.getBytes(StandardCharsets.UTF_8).length / 1024.0));
			detalle.append("\n### ").append(e.getKey()).append(" (").append(sql.size()).append(" SQL)\n");
			detalle.append("- cargas por entidad: ").append(cargasPorEntidad).append('\n');
			detalle.append("- entidades por select propio (fetch): ").append(fetchPorEntidad).append('\n');
			detalle.append("- colecciones inicializadas por select propio: ").append(coleccion).append('\n');
			detalle.append("- formas de SQL (veces · tabla principal · ¿IN por lotes?):\n");
			sql.stream().collect(Collectors.groupingBy(MedicionRendimientoAlumnosIntegrationTest::forma, LinkedHashMap::new,
					Collectors.counting())).forEach((k, v) -> detalle.append("  - ").append(v).append(" × ").append(k).append('\n'));
		}
		// R2-P4: la pantalla «Grupos y Turnos» antes (grupos + los turnos de cada grupo + turnos) y después (resúmenes).
		List<Long> idsGrupos = new ArrayList<>();
		json.readTree(Files.readString(SALIDA.resolve("grupos.json"))).forEach(g -> idsGrupos.add(g.get("id").asLong()));
		List<String> antes = new ArrayList<>(List.of("/api/grupos", "/api/turnos"));
		idsGrupos.forEach(id -> antes.add("/api/grupos/" + id + "/turnos"));
		tabla.append(pantalla("pantalla_grupos_antes (" + antes.size() + " peticiones)", antes));
		tabla.append(pantalla("pantalla_grupos_despues", List.of("/api/grupos/resumen", "/api/turnos/resumen")));
		String informe = "## Medición R2 (" + N_ALUMNOS + " alumnos sintéticos)\n\n" + tabla + "\n" + detalle;
		Files.writeString(SALIDA.resolve("medicion.md"), informe, StandardCharsets.UTF_8);
		System.out.println(informe);

		// Exposición pública: ¿el anónimo recibe datos personales de alumnos en /api/turnos/dto?
		JsonNode anon = json.readTree(Files.readString(SALIDA.resolve("turnos_dto_anonimo.json")));
		int conAlumnos = 0;
		List<String> campos = new ArrayList<>();
		for (JsonNode t : anon) {
			JsonNode al = t.get("alumnos");
			if (al != null && al.size() > 0) {
				conAlumnos++;
				if (campos.isEmpty()) al.get(0).fieldNames().forEachRemaining(campos::add);
			}
		}
		String exp = "turnos=" + anon.size() + ", turnos con alumnos=" + conAlumnos + ", campos del alumno=" + campos;
		Files.writeString(SALIDA.resolve("exposicion-turnos-dto.txt"), exp, StandardCharsets.UTF_8);
		System.out.println("R2 exposición anónima /api/turnos/dto: " + exp);
	}

	/**
	 * Sonda: asignar un turno nuevo a un alumno que está en dos grupos. Hoy su bolsa de turnos puede venir duplicada
	 * (carga EAGER en cadena); si Hibernate reescribe la tabla intermedia con duplicados choca con la clave única.
	 */
	@Test
	void sondaAsignarTurnoAAlumnoDeDosGrupos() throws Exception {
		Long otroTurno = jdbc.queryForObject("""
				SELECT MIN(t.id) FROM turno t WHERE t.grupo_id IS NOT NULL AND t.organizacion_id = 1
				  AND t.id NOT IN (SELECT turno_id FROM alumno_turno WHERE alumno_id = ?)""", Long.class, alumnoMuestra);
		int antes = jdbc.queryForObject("SELECT COUNT(*) FROM alumno_turno WHERE alumno_id = ?", Integer.class,
				alumnoMuestra);
		MvcResult r = mvc.perform(post("/api/alumnos/" + alumnoMuestra + "/turnos/" + otroTurno)
				.cookie(new Cookie("jwt", token))).andReturn();
		int despues = jdbc.queryForObject("SELECT COUNT(*) FROM alumno_turno WHERE alumno_id = ?", Integer.class,
				alumnoMuestra);
		String res = "POST /api/alumnos/" + alumnoMuestra + "/turnos/" + otroTurno + " -> HTTP "
				+ r.getResponse().getStatus() + " body=" + r.getResponse().getContentAsString(StandardCharsets.UTF_8)
				+ " | filas alumno_turno antes=" + antes + " despues=" + despues;
		Files.createDirectories(SALIDA);
		Files.writeString(SALIDA.resolve("sonda-asignar-turno.txt"), res, StandardCharsets.UTF_8);
		System.out.println("R2 sonda: " + res);
	}

	/** Fila de la tabla con el total (SQL, ms de una pasada, KB) de varias peticiones seguidas. */
	private String pantalla(String nombre, List<String> rutas) throws Exception {
		for (String r : rutas) {
			libre(r); // calentamiento
		}
		int[] sql = { 0 };
		long[] bytes = { 0 };
		long t = System.nanoTime();
		for (String r : rutas) {
			ContadorSentencias.Conteo<String> c = ContadorSentencias.contar(() -> libre(r));
			sql[0] += c.total();
			bytes[0] += c.resultado().getBytes(StandardCharsets.UTF_8).length;
		}
		return String.format("| %s | (suma) | %d | - | %d | %.1f |%n", nombre, sql[0],
				(System.nanoTime() - t) / 1_000_000, bytes[0] / 1024.0);
	}

	/** Como {@link #peticion}, sin exigir 200: el listado de turnos de un grupo sin turnos responde 404. */
	private String libre(String ruta) throws Exception {
		return mvc.perform(get(ruta).cookie(new Cookie("jwt", token))).andReturn().getResponse()
				.getContentAsString(StandardCharsets.UTF_8);
	}

	private static String simple(String n) {
		return n.replace("com.taemoi.project.entities.", "");
	}

	private static String forma(String sql) {
		String s = sql.toLowerCase().replaceAll("\\s+", " ");
		boolean lote = s.matches(".* in \\((\\?,)+\\?\\).*") || s.contains("array_contains") || s.contains("= any");
		String tabla = "?";
		var m = java.util.regex.Pattern.compile(" from ([a-z_]+)").matcher(s);
		if (m.find()) tabla = m.group(1);
		String joins = "";
		var j = java.util.regex.Pattern.compile("join ([a-z_]+)").matcher(s);
		List<String> js = new ArrayList<>();
		while (j.find()) js.add(j.group(1));
		if (!js.isEmpty()) joins = " join " + String.join(",", js);
		String where = s.contains(" where ") ? s.substring(s.indexOf(" where ")).replaceAll("in \\((\\?,)+\\?\\)", "in (?…)") : "";
		if (where.length() > 70) where = where.substring(0, 70) + "…";
		return (s.startsWith("select count") ? "count " : "") + tabla + joins + (lote ? " [lote]" : "") + where;
	}

	private String peticion(String ruta, boolean anonimo) throws Exception {
		MockHttpServletRequestBuilder req = get(ruta);
		if (!anonimo) {
			req = req.cookie(new Cookie("jwt", token));
		}
		MvcResult r = mvc.perform(req).andExpect(status().isOk()).andReturn();
		return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	private String login(String email) throws Exception {
		MvcResult resultado = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return json.readTree(resultado.getResponse().getContentAsString()).get("token").asText();
	}
}
