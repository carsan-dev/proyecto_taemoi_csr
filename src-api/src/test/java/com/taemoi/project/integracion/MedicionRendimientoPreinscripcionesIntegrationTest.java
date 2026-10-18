package com.taemoi.project.integracion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Membresia;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.MembresiaRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.TemporadaService;

import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;

/**
 * Arnés de MEDICIÓN R2-P7 (preinscripciones), no un test de regresión. Siembra datos SINTÉTICOS en la organización 1:
 * los 800 alumnos de {@link MedicionRendimientoAlumnosIntegrationTest} (16 grupos en 2 sedes, ~40 turnos) y
 * {@code r2.preinscripciones} (500 por defecto) preinscripciones en varios estados, deportes, sedes y temporadas, con
 * turnos seleccionados y firma y PDF reales (generados por el alta pública). Mide por endpoint nº de SQL, ms y bytes.
 *
 * <p>Solo con {@code -Dmedicion.rendimiento=true}, en solitario:
 * {@code ./mvnw -o test -Dtest=MedicionRendimientoPreinscripcionesIntegrationTest -Dmedicion.rendimiento=true}.
 * Resultado en {@code target/r2-p7-medicion/}.
 */
@EnabledIfSystemProperty(named = "medicion.rendimiento", matches = "true")
@SpringBootTest(properties = {
		"spring.jpa.properties.hibernate.generate_statistics=true",
		"spring.jpa.properties.hibernate.session_factory.statement_inspector=com.taemoi.project.integracion.ContadorSentencias",
		"logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN" })
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MedicionRendimientoPreinscripcionesIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String CLAVE = "Clave-Segura-123";
	private static final int N_ALUMNOS = Integer.getInteger("r2.alumnos", 800);
	private static final int N_PRE = Integer.getInteger("r2.preinscripciones", 500);
	/** R2-P7c: {@code -Dr2.repeticiones} para medianas más estables en las peticiones cortas. */
	private static final int REPETICIONES = Integer.getInteger("r2.repeticiones", 5);
	private static final Path SALIDA = Path.of("target", System.getProperty("r2.salida", "r2-p7-medicion"));

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired PasswordEncoder encoder;
	@Autowired UsuarioRepository usuarios;
	@Autowired MembresiaRepository membresias;
	@Autowired EntityManagerFactory emf;
	@Autowired TemporadaService temporadas;
	@Autowired JwtService jwt;
	@Autowired org.springframework.transaction.PlatformTransactionManager txManager;
	@Autowired com.taemoi.project.repositories.PreinscripcionRepository preinscripcionRepo;
	@Autowired com.taemoi.project.repositories.AlumnoDeporteRepository alumnoDeporteRepo;

	@MockitoBean JavaMailSender mailSender;

	String token;
	String tokenResponsable;
	String temporada;
	long tkd;
	long kb;
	long sede1;
	long sede2;
	final List<Long> gruposTkd = new ArrayList<>();
	final List<Long> gruposKb = new ArrayList<>();
	final Map<Long, List<Long>> turnosDe = new LinkedHashMap<>();
	final Map<Long, Long> sedeDeGrupo = new LinkedHashMap<>();
	List<Long> alumnos;
	final StringBuilder notas = new StringBuilder();
	int ipSecuencia = 1;
	int dniSecuencia = 10_000_000;

	@BeforeAll
	void sembrar() throws Exception {
		long t0 = System.nanoTime();
		temporada = temporadas.actual();
		sembrarAlumnos();
		sembrarPreinscripciones();
		token = login("r2p7-gestor@example.test");
		tokenResponsable = responsable();
		System.out.printf("R2-P7 siembra: %d alumnos, %d preinscripciones en %d ms%n", alumnos.size(),
				jdbc.queryForObject("SELECT COUNT(*) FROM preinscripcion WHERE organizacion_id = 1", Long.class),
				(System.nanoTime() - t0) / 1_000_000);
	}

	// ------------------------------------------------------------------ siembra

	private void sembrarAlumnos() {
		Long sede = jdbc.queryForObject("SELECT MIN(id) FROM sede WHERE organizacion_id = 1", Long.class);
		if (sede == null) {
			jdbc.update("INSERT INTO sede (organizacion_id, nombre, activa) VALUES (1, 'Sede R2', TRUE)");
			sede = jdbc.queryForObject("SELECT MIN(id) FROM sede WHERE organizacion_id = 1", Long.class);
		}
		sede1 = sede;
		jdbc.update("INSERT INTO sede (organizacion_id, nombre, activa) VALUES (1, 'Sede R2 bis', TRUE)");
		sede2 = jdbc.queryForObject("SELECT MAX(id) FROM sede WHERE organizacion_id = 1", Long.class);
		tkd = jdbc.queryForObject("SELECT id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'TAEKWONDO'", Long.class);
		kb = jdbc.queryForObject("SELECT id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'KICKBOXING'", Long.class);
		Long grado = jdbc.queryForObject("SELECT MIN(id) FROM grado", Long.class);
		String[] dias = { "Lunes", "Martes", "Miércoles", "Jueves", "Viernes" };
		for (int g = 0; g < 16; g++) {
			boolean esTkd = g < 10;
			long sedeGrupo = g % 2 == 0 ? sede1 : sede2;
			jdbc.update("INSERT INTO grupo (nombre, tipo, organizacion_id, disciplina_id, sede_id, rango_edad_min, rango_edad_max) "
					+ "VALUES (?, NULL, 1, ?, ?, ?, ?)", "R2 Grupo " + (esTkd ? "TKD " : "KB ") + g, esTkd ? tkd : kb,
					sedeGrupo, 4 + g, 10 + g);
			long id = jdbc.queryForObject("SELECT MAX(id) FROM grupo WHERE organizacion_id = 1", Long.class);
			(esTkd ? gruposTkd : gruposKb).add(id);
			sedeDeGrupo.put(id, sedeGrupo);
			List<Long> turnos = new ArrayList<>();
			for (int t = 0; t < (g % 3 == 0 ? 3 : 2); t++) {
				jdbc.update("INSERT INTO turno (dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id) "
						+ "VALUES (?, ?, ?, NULL, ?, 1)", dias[(g + t) % 5], String.format("%02d:00", 16 + t),
						String.format("%02d:00", 17 + t), id);
				turnos.add(jdbc.queryForObject("SELECT MAX(id) FROM turno WHERE organizacion_id = 1", Long.class));
			}
			turnosDe.put(id, turnos);
		}
		List<Object[]> filas = new ArrayList<>();
		for (int i = 0; i < N_ALUMNOS; i++) {
			filas.add(new Object[] { i % 10 != 0, String.format("SINTETICO %03d", (N_ALUMNOS - i)), "Calle Sintética " + i,
					Date.valueOf(LocalDate.of(2005 + (i % 15), 1 + (i % 12), 1 + (i % 27))),
					String.format("R2 Alumno %03d", i), 900000 + i, "600" + String.format("%06d", i),
					"r2-alumno-" + i + "@example.test", i % 2 == 0 ? sede1 : sede2, Date.valueOf(LocalDate.of(2020, 9, 1)) });
		}
		jdbc.batchUpdate("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre, numero_expediente,
				  telefono, tiene_derecho_examen, tiene_discapacidad, email, organizacion_id, sede_principal_id, fecha_alta,
				  fecha_alta_inicial)
				VALUES (?, ?, 1, ?, ?, ?, ?, ?, 0, 0, ?, 1, ?, ?, ?)""",
				filas.stream().map(f -> new Object[] { f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8], f[9], f[9] })
						.toList());
		alumnos = jdbc.queryForList(
				"SELECT id FROM alumno WHERE organizacion_id = 1 AND numero_expediente >= 900000 ORDER BY numero_expediente",
				Long.class);
		List<Object[]> deportes = new ArrayList<>();
		List<Object[]> enGrupo = new ArrayList<>();
		List<Object[]> enTurno = new ArrayList<>();
		List<Object[]> tutores = new ArrayList<>();
		for (int i = 0; i < alumnos.size(); i++) {
			long a = alumnos.get(i);
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
		String hash = encoder.encode(CLAVE);
		Usuario gestor = TenantContext.calcularCon(ORG1, () -> {
			Usuario u = new Usuario();
			u.setNombre("Gestor");
			u.setApellidos("R2P7");
			u.setEmail("r2p7-gestor@example.test");
			u.setContrasena(hash);
			u.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(u);
		});
		TenantContext.calcularCon(ORG1, () -> membresias.save(new Membresia(gestor.getId(), ORG1, RolMembresia.PROPIETARIO)));
	}

	/**
	 * Dos altas públicas REALES (firma de lienzo y PDF generados por el servicio) y, copiando sus columnas binarias,
	 * {@link #N_PRE} filas por JDBC en varios estados, deportes, sedes y temporadas.
	 */
	private void sembrarPreinscripciones() throws Exception {
		String refTkd = altaPublica("TAEKWONDO", List.of(turnosDe.get(gruposTkd.get(9)).get(0)), "Plantilla");
		Long kbPlantilla = jdbc.queryForObject("""
				SELECT COUNT(*) FROM plantilla_preinscripcion p JOIN disciplina d ON d.id = p.disciplina_id
				WHERE p.organizacion_id = 1 AND d.codigo = 'KICKBOXING' AND p.activa = TRUE""", Long.class);
		String refKb = kbPlantilla > 0 ? altaPublica("KICKBOXING", List.of(turnosDe.get(gruposKb.get(1)).get(0)), "Plantilla")
				: refTkd;
		Map<String, Object> base = jdbc.queryForMap("SELECT firma, pdf_firmado, plantilla_id, plantilla_snapshot FROM preinscripcion WHERE referencia = ?", refTkd);
		Map<String, Object> baseKb = jdbc.queryForMap("SELECT firma, pdf_firmado, plantilla_id, plantilla_snapshot FROM preinscripcion WHERE referencia = ?", refKb);
		notas.append(String.format("- Alta pública real: firma %d B, pdf_firmado %d B, plantilla_snapshot %d B (TKD); KB: pdf %d B.%n",
				((byte[]) base.get("firma")).length, ((byte[]) base.get("pdf_firmado")).length,
				((String) base.get("plantilla_snapshot")).length(), ((byte[]) baseKb.get("pdf_firmado")).length));

		String temporadaAnterior = (Integer.parseInt(temporada.substring(0, 4)) - 1) + "-" + temporada.substring(0, 4);
		Random r = new Random(7);
		Instant ahora = Instant.now();
		List<Object[]> filas = new ArrayList<>();
		List<String[]> meta = new ArrayList<>(); // referencia, estado, turnos (coma)
		for (int i = 0; i < N_PRE; i++) {
			int dado = r.nextInt(100);
			String estado = dado < 35 ? "PENDIENTE" : dado < 50 ? "EN_LISTA_ESPERA" : dado < 85 ? "FINALIZADA" : "CANCELADA";
			// Lista de espera coherente con el aforo: solo turnos llenos (TKD, grupos pares 2-8, turnos 0 y 1: 80
			// alumnos activos cada uno > límite 36), para que cancelar/cambiar no promocione por un artefacto de la siembra.
			boolean espera = "EN_LISTA_ESPERA".equals(estado);
			boolean esTkd = espera || i % 10 < 7;
			List<Long> grupos = esTkd ? gruposTkd : gruposKb;
			long grupo = espera ? gruposTkd.get(2 + 2 * r.nextInt(4)) : grupos.get(r.nextInt(grupos.size()));
			List<Long> turnosGrupo = turnosDe.get(grupo);
			List<Long> elegidos = r.nextBoolean() ? List.of(turnosGrupo.get(0)) : turnosGrupo.subList(0, 2);
			String temp = i % 5 == 0 ? temporadaAnterior : temporada;
			// La mitad, «renovaciones»: misma fecha de nacimiento y nombre que un alumno (alumnosCoincidentes > 0).
			boolean renovacion = i % 2 == 0;
			int idx = r.nextInt(N_ALUMNOS);
			LocalDate nacimiento = renovacion ? LocalDate.of(2005 + (idx % 15), 1 + (idx % 12), 1 + (idx % 27))
					: LocalDate.of(2008 + r.nextInt(14), 1 + r.nextInt(12), 1 + r.nextInt(28));
			String nombre = renovacion ? String.format("R2 Alumno %03d", idx) : "Pre R2 " + i;
			String apellidos = renovacion ? String.format("SINTETICO %03d", (N_ALUMNOS - idx)) : "SINTETICO PRE " + i;
			boolean menor = nacimiento.isAfter(LocalDate.now().minusYears(18));
			String dni = menor ? null : dni();
			Map<String, Object> b = esTkd ? base : baseKb;
			String referencia = "PRE-R2-" + String.format("%05d", i);
			Instant creada = ahora.minus(N_PRE - i, ChronoUnit.HOURS);
			Long alumno = "FINALIZADA".equals(estado) ? alumnos.get(idx) : null;
			filas.add(new Object[] { referencia, temp, estado, nombre, apellidos, dni,
					UUID.randomUUID().toString().replace("-", "") + "00000000000000000000000000000000".substring(0, 32),
					Date.valueOf(nacimiento), "Calle Sintética " + i, "600" + String.format("%06d", i), "r2-pre-" + i + "@example.test",
					r.nextInt(20) == 0, r.nextBoolean(), menor ? "Tutor Pre " + i : null, menor ? dni() : null,
					i % 7 == 0 ? "Observación sintética " + i : null, b.get("firma"), b.get("plantilla_id"),
					b.get("plantilla_snapshot"), b.get("pdf_firmado"), "e".repeat(64), Timestamp.from(creada),
					Timestamp.from(creada), grupo, esTkd ? tkd : kb, alumno,
					(esTkd ? "Taekwondo" : "Kickboxing") + " · Lunes 16:00-17:00 (R2 Grupo)",
					"FINALIZADA".equals(estado) ? Timestamp.from(creada.plusSeconds(3600)) : null,
					"CANCELADA".equals(estado) ? Timestamp.from(creada.plusSeconds(3600)) : null });
			meta.add(new String[] { referencia, estado,
					elegidos.stream().map(String::valueOf).collect(Collectors.joining(",")) });
		}
		jdbc.batchUpdate("""
				INSERT INTO preinscripcion (referencia, temporada, estado, nombre, apellidos, dni, identidad_hash,
				  fecha_nacimiento, direccion, telefono, email, tiene_discapacidad, consentimiento_fotografico, tutor_nombre,
				  tutor_dni, observaciones, aceptacion_normas, firmante_nombre, firma, plantilla_id, plantilla_snapshot,
				  pdf_firmado, token_documento_hash, creada_en, actualizada_en, grupo_id, organizacion_id, disciplina_id,
				  alumno_id, grupo_snapshot, finalizada_en, cancelada_en)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 'Firmante R2', ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?)""",
				filas.stream().map(f -> new Object[] { f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8], f[9], f[10],
						f[11], f[12], f[13], f[14], f[15], f[16], f[17], f[18], f[19], f[20], f[21], f[22], f[23], f[24],
						f[25], f[26], f[27], f[28] }).toList());
		Map<String, Long> ids = new LinkedHashMap<>();
		jdbc.query("SELECT referencia, id FROM preinscripcion WHERE referencia LIKE 'PRE-R2-%'",
				rs -> { ids.put(rs.getString(1), rs.getLong(2)); });
		List<Object[]> pt = new ArrayList<>();
		for (String[] m : meta) {
			for (String t : m[2].split(",")) {
				pt.add(new Object[] { ids.get(m[0]), Long.valueOf(t) });
			}
		}
		jdbc.batchUpdate("INSERT INTO preinscripcion_turno (preinscripcion_id, turno_id) VALUES (?, ?)", pt);
		StringBuilder dist = new StringBuilder("- Distribución sembrada (estado × temporada): ");
		jdbc.query("SELECT estado, temporada, COUNT(*) FROM preinscripcion WHERE organizacion_id = 1 GROUP BY estado, temporada ORDER BY 1, 2",
				rs -> { dist.append(rs.getString(1)).append('/').append(rs.getString(2)).append('=').append(rs.getLong(3)).append("; "); });
		notas.append(dist).append('\n');
	}

	private String altaPublica(String deporte, List<Long> turnos, String nombre) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("deporte", deporte);
		body.put("turnoIds", turnos);
		body.put("nombre", nombre);
		body.put("apellidos", "Sintetica " + UUID.randomUUID().toString().substring(0, 6));
		body.put("dni", dni());
		body.put("fechaNacimiento", "2008-03-01");
		body.put("direccion", "Calle Sintética 1");
		body.put("telefono", "612345678");
		body.put("telefono2", "");
		body.put("email", "r2p7-alta@example.test");
		body.put("observaciones", "");
		body.put("tutorNombre", "");
		body.put("tutorDni", "");
		body.put("tieneDiscapacidad", false);
		body.put("consentimientoFotografico", true);
		body.put("aceptacionNormas", true);
		body.put("firmanteNombre", "Firmante Sintético");
		body.put("firmaBase64", firmaLienzo());
		MvcResult r = mvc.perform(post("/api/preinscripciones").contentType(MediaType.APPLICATION_JSON)
				.header("X-Forwarded-For", "10.0.0." + (ipSecuencia++)).content(json.writeValueAsString(body))).andReturn();
		String cuerpo = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
		if (r.getResponse().getStatus() != 201) {
			throw new IllegalStateException("Alta pública " + deporte + " -> " + r.getResponse().getStatus() + " " + cuerpo);
		}
		return json.readTree(cuerpo).get("referencia").asText();
	}

	/** Firma como la del lienzo del formulario (1200×220, trazo de 4 px), en PNG. */
	private static String firmaLienzo() throws Exception {
		BufferedImage img = new BufferedImage(1200, 220, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, 1200, 220);
		g.setColor(new Color(0x17, 0x21, 0x26));
		g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		Random r = new Random(3);
		int x = 80, y = 110;
		for (int i = 0; i < 260; i++) {
			int nx = Math.min(1120, x + r.nextInt(9)), ny = Math.max(30, Math.min(190, y + r.nextInt(41) - 20));
			g.drawLine(x, y, nx, ny);
			x = nx;
			y = ny;
		}
		g.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(img, "png", out);
		return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
	}

	private String dni() {
		int n = dniSecuencia++;
		return n + String.valueOf("TRWAGMYFPDXBNJZSQVHLCKE".charAt(n % 23));
	}

	/** Responsable de la sede 1 (FAMILIA + RESPONSABLE_SEDE con su alumno propio, como EscuelaConSedesDePrueba). */
	private String responsable() {
		Usuario u = TenantContext.calcularCon(ORG1, () -> {
			Usuario n = new Usuario();
			n.setNombre("Responsable");
			n.setApellidos("R2P7");
			n.setEmail("r2p7-responsable@example.test");
			n.setContrasena(encoder.encode(CLAVE));
			n.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(n);
		});
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, 1, 'FAMILIA')", u.getId());
		jdbc.update("INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen) VALUES (?, ?, 1, 'TUTOR', 'MANUAL')",
				u.getId(), alumnos.get(1));
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, 1, 'RESPONSABLE_SEDE')", u.getId());
		long m = jdbc.queryForObject("SELECT id FROM membresia WHERE usuario_id = ? AND rol = 'RESPONSABLE_SEDE'", Long.class, u.getId());
		jdbc.update("INSERT INTO membresia_sede (membresia_id, sede_id) VALUES (?, ?)", m, sede1);
		Usuario cargado = TenantContext.calcularCon(ORG1, () -> usuarios.findById(u.getId()).orElseThrow());
		return jwt.generateToken(cargado, 600_000, ORG1);
	}

	// ------------------------------------------------------------------ medición

	@Test
	void medir() throws Exception {
		Files.createDirectories(SALIDA);
		String pendiente = jdbc.queryForObject("SELECT MIN(referencia) FROM preinscripcion WHERE referencia LIKE 'PRE-R2-%' AND estado = 'PENDIENTE' AND temporada = ?", String.class, temporada);
		String espera = jdbc.queryForObject("SELECT MAX(referencia) FROM preinscripcion WHERE referencia LIKE 'PRE-R2-%' AND estado = 'EN_LISTA_ESPERA' AND temporada = ?", String.class, temporada);
		Map<String, String> rutas = new LinkedHashMap<>();
		rutas.put("listado_defecto", "/api/preinscripciones?page=0&size=25");
		rutas.put("listado_pagina_3", "/api/preinscripciones?page=3&size=25");
		rutas.put("listado_temporada_pendientes", "/api/preinscripciones?page=0&size=25&temporada=" + temporada + "&estado=PENDIENTE");
		rutas.put("listado_lista_espera", "/api/preinscripciones?page=0&size=25&estado=EN_LISTA_ESPERA");
		rutas.put("listado_kickboxing", "/api/preinscripciones?page=0&size=25&deporte=KICKBOXING");
		rutas.put("listado_busqueda", "/api/preinscripciones?page=0&size=25&q=sintetico%20pre");
		rutas.put("listado_100", "/api/preinscripciones?page=0&size=100");
		rutas.put("listado_responsable_sede", "RESP:/api/preinscripciones?page=0&size=25");
		rutas.put("detalle", "/api/preinscripciones/" + pendiente);
		rutas.put("detalle_lista_espera", "/api/preinscripciones/" + espera);
		rutas.put("firma", "/api/preinscripciones/" + pendiente + "/firma");
		rutas.put("documento_firmado", "/api/preinscripciones/" + pendiente + "/documento-firmado");
		rutas.put("plantillas_tkd", "/api/preinscripciones/plantillas/TAEKWONDO");
		rutas.put("public_temporada", "ANON:/api/preinscripciones/public/temporada");
		rutas.put("public_configuracion_tkd", "ANON:/api/preinscripciones/public/configuracion/TAEKWONDO");
		rutas.put("public_grupos_tkd", "ANON:/api/preinscripciones/public/grupos/TAEKWONDO");
		rutas.put("public_grupos_kb", "ANON:/api/preinscripciones/public/grupos/KICKBOXING");
		rutas.put("public_turnos_tkd", "ANON:/api/preinscripciones/public/turnos/TAEKWONDO");
		rutas.put("catalogo_publico", "ANON:/api/catalogo/publico");
		rutas.put("turnos_dto", "/api/turnos/dto");

		Statistics st = emf.unwrap(SessionFactory.class).getStatistics();
		st.setStatisticsEnabled(true);
		long s0 = bytesEnviadosMysql();
		costeSonda = bytesEnviadosMysql() - s0;
		StringBuilder tabla = new StringBuilder();
		tabla.append("| endpoint | ruta | SQL | entidades cargadas | ms (mediana de ").append(REPETICIONES)
				.append(") | bytes | bytes MySQL→app |\n|---|---|---|---|---|---|---|\n");
		StringBuilder detalle = new StringBuilder();
		for (Map.Entry<String, String> e : rutas.entrySet()) {
			medirUna(e.getKey(), e.getValue(), st, tabla, detalle, REPETICIONES);
		}

		// Sonda: lo que pesan las columnas LOB en la consulta del listado (25 filas, misma ordenación).
		detalle.append("\n### Sonda JDBC: página de 25 con y sin columnas LOB\n");
		long[] con = new long[REPETICIONES], sin = new long[REPETICIONES];
		long bytesLob = 0;
		for (int i = 0; i < REPETICIONES; i++) {
			long t = System.nanoTime();
			List<Map<String, Object>> filas = jdbc.queryForList("SELECT * FROM preinscripcion WHERE organizacion_id = 1 ORDER BY creada_en DESC, id DESC LIMIT 25");
			con[i] = (System.nanoTime() - t) / 1_000;
			bytesLob = filas.stream().mapToLong(f -> len(f.get("firma")) + len(f.get("pdf_firmado")) + len(f.get("plantilla_snapshot"))).sum();
			t = System.nanoTime();
			jdbc.queryForList("SELECT id, referencia, temporada, estado, nombre, apellidos, dni, fecha_nacimiento, direccion, telefono, telefono2, email, observaciones, grupo_snapshot, turno_snapshot, creada_en FROM preinscripcion WHERE organizacion_id = 1 ORDER BY creada_en DESC, id DESC LIMIT 25");
			sin[i] = (System.nanoTime() - t) / 1_000;
		}
		Arrays.sort(con);
		Arrays.sort(sin);
		detalle.append(String.format("- con LOB: %d µs (mediana), %d bytes de firma+pdf+plantilla_snapshot leídos; sin LOB: %d µs%n",
				con[REPETICIONES / 2], bytesLob, sin[REPETICIONES / 2]));
		detalle.append("- EXPLAIN listado por defecto: ")
				.append(jdbc.queryForList("EXPLAIN SELECT id FROM preinscripcion WHERE organizacion_id = 1 ORDER BY creada_en DESC, id DESC LIMIT 25"))
				.append('\n');

		// Sonda: coste de la MISMA consulta (exists de alumno_deporte) dentro de una transacción vacía y dentro de una
		// con las preinscripciones de la lista de espera cargadas (con sus LOB): el auto-flush revisa todo el contexto.
		detalle.append("\n### Sonda auto-flush: 2000 × existsByAlumnoIdAndDeporteAndActivoTrue\n");
		for (boolean conEntidades : new boolean[] { false, true, false, true }) {
			long ms = TenantContext.calcularCon(ORG1, () -> new org.springframework.transaction.support.TransactionTemplate(txManager).execute(s -> {
				int cargadas = conEntidades ? preinscripcionRepo.findByTemporadaAndEstadoOrderByCreadaEnAscIdAsc(temporada,
						com.taemoi.project.entities.EstadoPreinscripcion.EN_LISTA_ESPERA).size() : 0;
				long t = System.nanoTime();
				for (int i = 0; i < 2000; i++) {
					alumnoDeporteRepo.existsByAlumnoIdAndDeporteAndActivoTrue(alumnos.get(i % alumnos.size()), "TAEKWONDO");
				}
				long dur = (System.nanoTime() - t) / 1_000_000;
				detalle.append(String.format("- %s (%d preinscripciones gestionadas): %d ms -> %.2f ms/consulta%n",
						conEntidades ? "con lista de espera cargada" : "transacción vacía", cargadas, dur, dur / 2000.0));
				return dur;
			}));
		}

		// Escrituras (una vez cada una; cambian datos).
		tabla.append("| **escrituras** | | | | | | |\n");
		long turnoNuevo = turnosDe.get(gruposTkd.get(0)).get(0);
		String pendTkd = jdbc.queryForObject("""
				SELECT MIN(p.referencia) FROM preinscripcion p JOIN disciplina d ON d.id = p.disciplina_id
				WHERE p.referencia LIKE 'PRE-R2-%' AND p.estado = 'PENDIENTE' AND p.temporada = ? AND d.codigo = 'TAEKWONDO'
				  AND p.grupo_id <> ?""", String.class, temporada, gruposTkd.get(0));
		escritura("alta_publica_tkd", () -> altaPublicaMedida(), tabla, detalle, st);
		escritura("actualizar_turnos_pendiente", () -> mvc.perform(put("/api/preinscripciones/" + pendTkd + "/turnos")
				.cookie(new Cookie("jwt", token)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"turnoIds\":[" + turnoNuevo + "]}")).andReturn(), tabla, detalle, st);
		String pendCancelar = jdbc.queryForObject("SELECT MAX(referencia) FROM preinscripcion WHERE referencia LIKE 'PRE-R2-%' AND estado = 'PENDIENTE' AND temporada = ?", String.class, temporada);
		escritura("cancelar_pendiente", () -> mvc.perform(post("/api/preinscripciones/" + pendCancelar + "/cancelar")
				.cookie(new Cookie("jwt", token)).contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"\"}")).andReturn(),
				tabla, detalle, st);
		// Fuera de preinscripciones: dar de baja a un alumno también recorre la lista de espera (promocionarTodas).
		long alumnoBaja = alumnos.get(37);
		escritura("baja_alumno_promocionar_todas", () -> mvc.perform(put("/api/alumnos/" + alumnoBaja + "/baja")
				.cookie(new Cookie("jwt", token))).andReturn(), tabla, detalle, st);
		jdbc.update("INSERT INTO tarifa (organizacion_id, codigo, nombre, importe) VALUES (1, 'R2P7', 'Tarifa R2P7', 30.00)");
		String grado = jdbc.queryForObject("""
				SELECT g.codigo FROM grado g JOIN disciplina d ON d.sistema_grados_id = g.sistema_grados_id
				WHERE d.id = ? AND g.aplica_a <> 'NINGUNO' ORDER BY g.orden LIMIT 1""", String.class, tkd);
		String pendFinalizar = jdbc.queryForObject("""
				SELECT MIN(p.referencia) FROM preinscripcion p JOIN disciplina d ON d.id = p.disciplina_id
				WHERE p.referencia LIKE 'PRE-R2-%' AND p.estado = 'PENDIENTE' AND d.codigo = 'TAEKWONDO' AND p.dni IS NOT NULL
				  AND p.nombre LIKE 'Pre R2%' AND p.referencia <> ? AND p.referencia <> ?""", String.class, pendTkd, pendCancelar);
		escritura("finalizar_crear_nuevo", () -> mvc.perform(post("/api/preinscripciones/" + pendFinalizar + "/finalizar")
				.cookie(new Cookie("jwt", token)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"accionAlumno\":\"CREAR_NUEVO\",\"camposActualizar\":[],\"discapacidadHistorica\":false,"
						+ "\"datosDeporte\":{\"tipoTarifa\":\"R2P7\",\"cuantiaTarifa\":30.0,\"rolFamiliar\":\"NINGUNO\","
						+ "\"grupoFamiliar\":\"\",\"grado\":\"" + grado + "\",\"fechaGrado\":\"" + LocalDate.now() + "\"}}"))
				.andReturn(), tabla, detalle, st);

		String informe = "## Medición R2-P7 (" + N_ALUMNOS + " alumnos, " + N_PRE + " preinscripciones sintéticas)\n\n"
				+ notas + "\n" + tabla + "\n" + detalle;
		Files.writeString(SALIDA.resolve("medicion.md"), informe, StandardCharsets.UTF_8);
		System.out.println(informe);
	}

	private MvcResult altaPublicaMedida() throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("deporte", "TAEKWONDO");
		body.put("turnoIds", List.of(turnosDe.get(gruposTkd.get(9)).get(0), turnosDe.get(gruposTkd.get(9)).get(1)));
		body.put("nombre", "Medida");
		body.put("apellidos", "Sintetica Alta");
		body.put("dni", dni());
		body.put("fechaNacimiento", "2008-03-01");
		body.put("direccion", "Calle Sintética 2");
		body.put("telefono", "612345678");
		body.put("telefono2", "");
		body.put("email", "r2p7-alta2@example.test");
		body.put("observaciones", "");
		body.put("tutorNombre", "");
		body.put("tutorDni", "");
		body.put("tieneDiscapacidad", false);
		body.put("consentimientoFotografico", true);
		body.put("aceptacionNormas", true);
		body.put("firmanteNombre", "Firmante Sintético");
		body.put("firmaBase64", firmaLienzo());
		return mvc.perform(post("/api/preinscripciones").contentType(MediaType.APPLICATION_JSON)
				.header("X-Forwarded-For", "10.0.1." + (ipSecuencia++)).content(json.writeValueAsString(body))).andReturn();
	}

	@FunctionalInterface
	interface Accion {
		MvcResult ejecutar() throws Exception;
	}

	private void escritura(String nombre, Accion accion, StringBuilder tabla, StringBuilder detalle, Statistics st)
			throws Exception {
		st.clear();
		long b0 = bytesEnviadosMysql();
		long t = System.nanoTime();
		ContadorSentencias.Conteo<MvcResult> conteo = ContadorSentencias.contar(accion::ejecutar);
		long ms = (System.nanoTime() - t) / 1_000_000;
		long leidos = bytesEnviadosMysql() - b0 - costeSonda;
		MvcResult r = conteo.resultado();
		tabla.append(String.format("| %s | HTTP %d | %d | %d | %d | %d | %d |%n", nombre, r.getResponse().getStatus(),
				conteo.total(), st.getEntityLoadCount(), ms, r.getResponse().getContentAsByteArray().length, leidos));
		detalle.append("\n- ").append(nombre).append(": flushes=").append(st.getFlushCount()).append(", transacciones=")
				.append(st.getTransactionCount()).append('\n');
		if (r.getResponse().getStatus() >= 400) {
			detalle.append("\n- ").append(nombre).append(" respuesta: ").append(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).append('\n');
		}
		formas(nombre, conteo.sentencias(), st, detalle);
	}

	private void medirUna(String nombre, String ruta, Statistics st, StringBuilder tabla, StringBuilder detalle,
			int repeticiones) throws Exception {
		peticion(ruta); // calentamiento
		st.clear();
		long b0 = bytesEnviadosMysql();
		ContadorSentencias.Conteo<byte[]> conteo = ContadorSentencias.contar(() -> peticion(ruta));
		long leidos = bytesEnviadosMysql() - b0 - costeSonda;
		long cargas = st.getEntityLoadCount();
		formas(nombre, conteo.sentencias(), st, detalle);
		long[] tiempos = new long[repeticiones];
		for (int i = 0; i < repeticiones; i++) {
			long t = System.nanoTime();
			peticion(ruta);
			tiempos[i] = (System.nanoTime() - t) / 1_000_000;
		}
		Arrays.sort(tiempos);
		byte[] cuerpo = conteo.resultado();
		if (!nombre.equals("firma") && !nombre.equals("documento_firmado")) {
			Files.write(SALIDA.resolve(nombre + ".json"), cuerpo);
		}
		tabla.append(String.format("| %s | `%s` | %d | %d | %d | %d | %d |%n", nombre, ruta, conteo.total(), cargas,
				tiempos[repeticiones / 2], cuerpo.length, leidos));
	}

	private void formas(String nombre, List<String> sql, Statistics st, StringBuilder detalle) {
		Map<String, Long> cargasPorEntidad = new TreeMap<>();
		for (String n : st.getEntityNames()) {
			var es = st.getEntityStatistics(n);
			if (es.getLoadCount() > 0) cargasPorEntidad.put(n.replace("com.taemoi.project.entities.", ""), es.getLoadCount());
		}
		Map<String, Long> coleccion = new TreeMap<>();
		for (String r : st.getCollectionRoleNames()) {
			var cs = st.getCollectionStatistics(r);
			if (cs.getFetchCount() > 0) coleccion.put(r.replace("com.taemoi.project.entities.", ""), cs.getFetchCount());
		}
		detalle.append("\n### ").append(nombre).append(" (").append(sql.size()).append(" SQL)\n");
		detalle.append("- cargas por entidad: ").append(cargasPorEntidad).append('\n');
		detalle.append("- colecciones inicializadas: ").append(coleccion).append('\n');
		boolean lob = sql.stream().anyMatch(s -> s.toLowerCase().contains("pdf_firmado") && s.toLowerCase().startsWith("select"));
		detalle.append("- ¿algún SELECT trae pdf_firmado?: ").append(lob).append('\n');
		// R2-P7c: también la firma y la plantilla congelada (los tres LOB de preinscripcion).
		boolean firma = sql.stream().anyMatch(s -> s.toLowerCase().startsWith("select") && s.toLowerCase().matches("(?s).*\\.firma\\b.*"));
		boolean plantilla = sql.stream().anyMatch(s -> s.toLowerCase().startsWith("select") && s.toLowerCase().contains("plantilla_snapshot"));
		detalle.append("- ¿algún SELECT trae firma / plantilla_snapshot?: ").append(firma).append(" / ").append(plantilla).append('\n');
		detalle.append("- formas de SQL:\n");
		sql.stream().collect(Collectors.groupingBy(MedicionRendimientoPreinscripcionesIntegrationTest::forma,
				LinkedHashMap::new, Collectors.counting())).forEach((k, v) -> detalle.append("  - ").append(v).append(" × ").append(k).append('\n'));
	}

	/**
	 * R2-P7c: bytes que el servidor MySQL lleva enviados (todas las conexiones; el arnés corre en solitario). La
	 * diferencia antes/después de una petición aproxima lo que la aplicación leyó de la base de datos.
	 */
	private long bytesEnviadosMysql() {
		return Long.parseLong(String.valueOf(jdbc.queryForMap("SHOW GLOBAL STATUS LIKE 'Bytes_sent'").get("Value")));
	}

	/** Bytes que cuesta la propia sonda (una lectura de {@code Bytes_sent}), para descontarlos. */
	private long costeSonda;

	private static long len(Object o) {
		if (o instanceof byte[] b) return b.length;
		if (o instanceof String s) return s.length();
		return 0;
	}

	private static String forma(String sql) {
		String s = sql.toLowerCase().replaceAll("\\s+", " ");
		boolean lote = s.matches(".* in \\((\\?,)+\\?\\).*");
		String tabla = "?";
		var m = java.util.regex.Pattern.compile(" from ([a-z_]+)").matcher(s);
		if (m.find()) tabla = m.group(1);
		if (s.startsWith("insert into ")) tabla = "INSERT " + s.substring(12, Math.min(s.length(), 12 + 30)).split(" ")[0];
		if (s.startsWith("update ")) tabla = "UPDATE " + s.substring(7).split(" ")[0];
		if (s.startsWith("delete from ")) tabla = "DELETE " + s.substring(12).split(" ")[0];
		List<String> js = new ArrayList<>();
		var j = java.util.regex.Pattern.compile("join ([a-z_]+)").matcher(s);
		while (j.find()) js.add(j.group(1));
		String joins = js.isEmpty() ? "" : " join " + String.join(",", js);
		String where = s.contains(" where ") ? s.substring(s.indexOf(" where ")).replaceAll("in \\((\\?,)+\\?\\)", "in (?…)") : "";
		if (where.length() > 80) where = where.substring(0, 80) + "…";
		return (s.startsWith("select count") ? "count " : "") + tabla + joins + (lote ? " [lote]" : "") + where;
	}

	private byte[] peticion(String ruta) throws Exception {
		boolean anonimo = ruta.startsWith("ANON:");
		boolean resp = ruta.startsWith("RESP:");
		String r = anonimo || resp ? ruta.substring(5) : ruta;
		MockHttpServletRequestBuilder req = get(r);
		if (!anonimo) {
			req = req.cookie(new Cookie("jwt", resp ? tokenResponsable : token));
		}
		MvcResult res = mvc.perform(req).andReturn();
		if (res.getResponse().getStatus() != 200) {
			throw new IllegalStateException(ruta + " -> HTTP " + res.getResponse().getStatus() + " "
					+ res.getResponse().getContentAsString(StandardCharsets.UTF_8));
		}
		return res.getResponse().getContentAsByteArray();
	}

	private String login(String email) throws Exception {
		MvcResult resultado = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"contrasena\":\"" + CLAVE + "\"}")).andReturn();
		return json.readTree(resultado.getResponse().getContentAsString()).get("token").asText();
	}
}
