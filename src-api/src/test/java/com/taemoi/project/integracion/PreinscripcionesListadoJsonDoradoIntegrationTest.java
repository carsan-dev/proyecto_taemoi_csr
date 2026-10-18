package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import com.taemoi.project.services.TemporadaService;

import jakarta.servlet.http.Cookie;

/**
 * Red de seguridad de R2-P7a (listado de gestión de preinscripciones): el JSON del listado
 * ({@code GET /api/preinscripciones}) y del detalle ({@code GET /api/preinscripciones/{referencia}}) para Moiskimdo
 * (organización 1), congelado en {@code src/test/resources/dorado/r2-p7a/*.json}, y un límite de sentencias SQL por
 * petición, al estilo de {@link MoiskimdoAlumnosJsonDoradoIntegrationTest}.
 *
 * <h2>Decisión del propietario (R2-P7a)</h2>
 * Las filas del listado dejan de llevar {@code alumnosCoincidentes} (los datos de las fichas compatibles); se sirven
 * solo en el detalle, que el panel pide al abrir una fila. Es la ÚNICA diferencia con el JSON anterior: los ficheros
 * {@code listado_*.json} se regeneraron una vez sin ese campo; los de {@code detalle_*.json}, no.
 * {@code coincidentes_<rol>.json} guarda, por referencia sembrada y rol, las {@code alumnosCoincidentes} que traía la
 * fila del listado ANTES del cambio (mismo contenido y mismo orden): el test exige que el detalle devuelva
 * exactamente eso (y, mientras una fila del listado lo traiga, que coincida también).
 *
 * <h2>Datos</h2>
 * Sintéticos, con prefijo {@value #PREFIJO} y ids explícitos en rangos reservados (8.100.000 alumnos, 8.200.000
 * grupos...), en la organización 1, más una segunda sede INACTIVA (para no cambiar la sede por defecto de la escuela
 * si algo quedara sin borrar). Casos de {@code alumnosCoincidentes}: coincidencia exacta por DNI (P01: A01, aunque
 * A09 tenga el mismo nombre y fecha), varias fichas con la misma fecha y nombre (P02: A02 con tutores, A03 de baja en
 * la sede 2, A04 sin sede; A05 tiene la fecha pero otro nombre), adulto con DNI sin ficha exacta (P03, en la sede 2:
 * A07 sin NIF entra, A08 con otro NIF no), solicitud de la sede 1 cuyo único candidato es de la sede 2 (P09), y
 * solicitudes sin candidatos en lista de espera (P04, P05), finalizada (P06), cancelada de la temporada anterior (P07)
 * y de kickboxing (P08). Roles: propietario (ADMIN), administrador (MANAGER) y responsable de la sede 1.
 *
 * <h2>Normalización</h2>
 * Ids sembrados → claves simbólicas ({@code alumno:A02}, {@code turno:T1}, {@code grupo:G1}); la temporada actual y
 * la anterior → {@code <temporada-actual>}/{@code <temporada-anterior>} (cambian con la fecha); campos {@code fecha*}
 * en milisegundos → {@code fecha:AAAA-MM-DD} (zona de la JVM); campos {@code *En} (Instant en segundos) →
 * {@code instante:} ISO en UTC. Arrays en orden estricto: todos están ordenados por
 * construcción (listado por {@code creadaEn, id} desc; {@code turnosSolicitados} por día y hora) salvo
 * {@code alumnosCoincidentes}, cuyo orden (el de {@code findByFechaNacimiento}) se exige igual por decisión.
 * Todos los listados filtran con {@code q=}{@value #PREFIJO} (página por defecto, 25): sin filtro, la página depende
 * de lo que dejen otras clases de la batería, y una solicitud ajena sin turnos hace responder 409 a todo el listado
 * (comportamiento anterior a R2-P7a, no se cambia aquí).
 *
 * <h2>Regenerar</h2>
 * Solo tras un cambio de contrato decidido: {@code ./mvnw -o test -Dtest=PreinscripcionesListadoJsonDoradoIntegrationTest
 * -Ddorado.regenerar=true}. Si difiere, la respuesta normalizada queda en {@code target/dorado-r2-p7a-real/}.
 */
@SpringBootTest(properties = {
		"spring.jpa.properties.hibernate.session_factory.statement_inspector=com.taemoi.project.integracion.ContadorSentencias",
		// Mismas propiedades que MoiskimdoAlumnosJsonDoradoIntegrationTest: comparten contexto de Spring.
		"app.base.url=http://localhost:8080" })
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PreinscripcionesListadoJsonDoradoIntegrationTest extends MySqlIntegrationTest {

	static final String PREFIJO = "DORADOP7A";
	private static final boolean REGENERAR = Boolean.getBoolean("dorado.regenerar");
	private static final String RECURSOS = "dorado/r2-p7a/";
	private static final Path FUENTES = Path.of("src", "test", "resources", "dorado", "r2-p7a");
	private static final Path REALES = Path.of("target", "dorado-r2-p7a-real");

	private static final long ALUMNO = 8_100_000L;
	private static final long GRUPO = 8_200_000L;
	private static final long TURNO = 8_300_000L;
	private static final long ALUMNO_DEPORTE = 8_400_000L;
	private static final long TUTOR = 8_500_000L;
	private static final long PREINSCRIPCION = 8_700_000L;
	private static final long ANCHO = 9_999L;
	private static final int EXPEDIENTE = 8_800_000;
	private static final List<String> TABLAS_CON_RANGO = List.of("preinscripcion", "tutor", "alumno_deporte", "alumno",
			"turno", "grupo");

	enum Rol {
		ADMIN, MANAGER, RESPONSABLE
	}

	/**
	 * Una petición capturada.
	 *
	 * @param limiteSql límite absoluto de sentencias SQL de la petición.
	 */
	record Caso(String nombre, String ruta, Rol rol, int limiteSql, int estado) {

		@Override
		public String toString() {
			return nombre;
		}
	}

	private static Caso listado(String nombre, String parametros, Rol rol, int limiteSql) {
		return new Caso(nombre, "/api/preinscripciones?q=" + PREFIJO + "&" + parametros, rol, limiteSql, 200);
	}

	private static Caso detalle(String nombre, String referencia, Rol rol, int limiteSql) {
		return new Caso(nombre, "/api/preinscripciones/" + PREFIJO + "-" + referencia, rol, limiteSql, 200);
	}

	/** Límites: lo medido tras R2-P7a + 2; al final de cada línea, lo medido antes (9ef8e11c) y después. */
	static Stream<Caso> casos() {
		return Stream.of(
				listado("listado_admin", "page=0&size=25", Rol.ADMIN, 12), // 42 → 10
				listado("listado_manager", "page=0&size=25", Rol.MANAGER, 12), // 42 → 10
				listado("listado_responsable", "page=0&size=25", Rol.RESPONSABLE, 14), // 42 → 12
				listado("listado_temporada_pendientes", "page=0&size=25&temporada={temporada}&estado=PENDIENTE",
						Rol.ADMIN, 10), // 30 → 8
				listado("listado_lista_espera", "page=0&size=25&estado=EN_LISTA_ESPERA", Rol.ADMIN, 11), // 12 → 9
				listado("listado_kickboxing", "page=0&size=25&deporte=KICKBOXING", Rol.ADMIN, 9), // 14 → 7
				listado("listado_pagina_2", "page=1&size=3", Rol.ADMIN, 13), // 16 → 11
				listado("listado_responsable_pendientes", "page=0&size=25&estado=PENDIENTE", Rol.RESPONSABLE, 12), // 30 → 10
				detalle("detalle_P01_dni_exacto", "P01", Rol.ADMIN, 13), // 11 → 11
				detalle("detalle_P02_misma_fecha", "P02", Rol.ADMIN, 15), // 13 → 13
				detalle("detalle_P02_misma_fecha_responsable", "P02", Rol.RESPONSABLE, 19), // 17 → 17
				detalle("detalle_P03_sin_exacto", "P03", Rol.ADMIN, 13), // 11 → 11
				detalle("detalle_P05_lista_espera", "P05", Rol.ADMIN, 11), // 9 → 9
				detalle("detalle_P06_finalizada", "P06", Rol.MANAGER, 11), // 9 → 9
				detalle("detalle_P09_candidato_otra_sede_responsable", "P09", Rol.RESPONSABLE, 14), // 12 → 12
				new Caso("detalle_P03_otra_sede_responsable", "/api/preinscripciones/" + PREFIJO + "-P03",
						Rol.RESPONSABLE, 8, 403)); // 6 → 6
	}

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired TemporadaService temporadaService;

	@MockitoBean JavaMailSender mailSender;

	final Map<Long, String> simbolos = new HashMap<>();
	final Map<Rol, String> tokens = new HashMap<>();
	final List<Long> usuariosCreados = new ArrayList<>();
	final Map<String, String> medidas = new TreeMap<>();
	/** Referencias sembradas (sin prefijo) por sede del grupo de la solicitud. */
	final Set<String> referencias = new LinkedHashSet<>();
	final Set<String> deLaSede2 = new LinkedHashSet<>();
	String temporada;
	String temporadaAnterior;
	long sede1;
	Long sede2;

	// ------------------------------------------------------------------ siembra

	@BeforeAll
	void preparar() {
		borrarRangos();
		temporada = temporadaService.actual();
		temporadaAnterior = (Integer.parseInt(temporada.substring(0, 4)) - 1) + "-" + temporada.substring(0, 4);
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
		long g1 = grupo(1, "Taekwondo Sede1", tkd, sede1);
		long g2 = grupo(2, "Taekwondo Sede2", tkd, sede2);
		long g3 = grupo(3, "Kickboxing Sede1", kb, sede1);
		long t1 = turno(1, g1, "Lunes", "17:00", "18:00");
		long t2 = turno(2, g1, "Miércoles", "17:00", "18:00");
		long t3 = turno(3, g2, "Martes", "18:00", "19:00");
		long t4 = turno(4, g3, "Jueves", "19:00", "20:00");

		// Fichas. nombre/apellidos/fecha deciden si son candidatas (coincideIdentidadPersonal).
		alumno(1, "Adulto", "Exacto Sintetico", "1988-02-10", "X8800001A", sede1, true);
		alumno(9, "Adulto", "Exacto Sintetico", "1988-02-10", null, sede1, true);
		alumno(2, "Lucia", "Gomez Sintetica", "2015-03-04", null, sede1, true);
		alumno(3, "Lucia", "Gomez Sintetica", "2015-03-04", null, sede2, false);
		alumno(4, "Lucia", "Gomez Sintetica", "2015-03-04", null, null, true);
		alumno(5, "Otra", "Gomez Sintetica", "2015-03-04", null, sede1, true);
		alumno(7, "Adulta", "Sin Exacto", "1990-01-01", null, sede2, true);
		alumno(8, "Adulta", "Sin Exacto", "1990-01-01", "X8800010B", sede1, true);
		alumno(10, "Marco", "Otra Sede", "2012-06-07", null, sede2, true);
		alumno(90, "Propio", "Responsable", "1980-01-01", null, null, true);

		long infantil = tarifa("INFANTIL");
		deporte(1, 1, tkd, true, infantil, 25.0, "NINGUNO", null);
		deporte(2, 2, tkd, true, infantil, 22.5, "HIJO", "FAM-P7A-1");
		deporte(3, 3, tkd, false, null, null, null, null);
		deporte(4, 7, kb, true, null, null, null, null);
		deporte(5, 10, tkd, true, null, null, null, null);
		tutor(1, 2, "Tutora Legal", "Gomez", "X8800099T", "MADRE", true);
		tutor(2, 2, "Tutor Contacto", "Gomez", null, "OTRO", false);

		long plantilla = jdbc.queryForObject("""
				SELECT p.id FROM plantilla_preinscripcion p JOIN disciplina d ON d.id = p.disciplina_id
				WHERE p.organizacion_id = 1 AND d.codigo = 'TAEKWONDO' ORDER BY p.id LIMIT 1""", Long.class);
		pre(1, "PENDIENTE", temporada, "Adulto", "Exacto Sintetico", "X8800001A", "1988-02-10", null, null, g1, tkd,
				plantilla, "2030-01-09 10:00:00", null, List.of(t1));
		pre(2, "PENDIENTE", temporada, "Lucía", "Gómez  Sintética", null, "2015-03-04", "Madre Sintetica", "X8800099T",
				g1, tkd, plantilla, "2030-01-08 10:00:00", null, List.of(t2, t1));
		pre(3, "PENDIENTE", temporada, "Adulta", "Sin Exacto", "X8800009Z", "1990-01-01", null, null, g2, tkd,
				plantilla, "2030-01-07 10:00:00", null, List.of(t3));
		pre(4, "EN_LISTA_ESPERA", temporada, "Espera", "Primera Sintetica", null, "2016-05-05", "Padre", "X8800098R",
				g1, tkd, plantilla, "2030-01-06 10:00:00", null, List.of(t1));
		pre(5, "EN_LISTA_ESPERA", temporada, "Espera", "Segunda Sintetica", null, "2016-06-06", "Padre", "X8800097W",
				g1, tkd, plantilla, "2030-01-05 10:00:00", null, List.of(t1, t2));
		pre(6, "FINALIZADA", temporada, "Final", "Kick Sintetico", "X8800020C", "1995-07-07", null, null, g3, kb,
				plantilla, "2030-01-04 10:00:00", ALUMNO + 8, List.of(t4));
		pre(7, "CANCELADA", temporadaAnterior, "Cancelada", "Anterior Sintetica", "X8800030D", "1985-08-08", null,
				null, g1, tkd, plantilla, "2030-01-03 10:00:00", null, List.of(t2));
		pre(8, "PENDIENTE", temporada, "Adulta", "Sin Exacto", null, "1990-01-01", null, null, g3, kb, plantilla,
				"2030-01-02 10:00:00", null, List.of(t4));
		pre(9, "PENDIENTE", temporada, "Marco", "Otra Sede", null, "2012-06-07", "Madre", "X8800096Q", g1, tkd,
				plantilla, "2030-01-01 10:00:00", null, List.of(t1));
		jdbc.update("UPDATE preinscripcion SET motivo_cancelacion = 'Motivo sintetico', cancelada_en = "
				+ "'2030-01-03 12:00:00', email_cancelacion_estado = 'ENVIADO' WHERE id = ?", PREINSCRIPCION + 7);
		jdbc.update("UPDATE preinscripcion SET finalizada_en = '2030-01-04 12:00:00', observaciones = 'Prefiere jueves', "
				+ "telefono2 = '611222333' WHERE id = ?", PREINSCRIPCION + 6);
		deLaSede2.add("P03");
	}

	private long disciplina(String codigo) {
		return jdbc.queryForObject("SELECT id FROM disciplina WHERE organizacion_id = 1 AND codigo = ?", Long.class,
				codigo);
	}

	private long tarifa(String codigo) {
		return jdbc.queryForObject("SELECT id FROM tarifa WHERE organizacion_id = 1 AND codigo = ?", Long.class, codigo);
	}

	private long grupo(int n, String nombre, long disciplina, Long sede) {
		long id = GRUPO + n;
		jdbc.update("""
				INSERT INTO grupo (id, nombre, tipo, rango_edad_min, rango_edad_max, organizacion_id, disciplina_id, sede_id)
				VALUES (?, ?, NULL, NULL, NULL, 1, ?, ?)""", id, PREFIJO + " " + nombre, disciplina, sede);
		simbolos.put(id, "grupo:G" + n);
		return id;
	}

	private long turno(int n, long grupo, String dia, String inicio, String fin) {
		long id = TURNO + n;
		jdbc.update("INSERT INTO turno (id, dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id) VALUES "
				+ "(?, ?, ?, ?, NULL, ?, 1)", id, dia, inicio, fin, grupo);
		simbolos.put(id, "turno:T" + n);
		return id;
	}

	private void alumno(int n, String nombre, String apellidos, String nacimiento, String nif, Long sede,
			boolean activo) {
		long id = ALUMNO + n;
		jdbc.update("""
				INSERT INTO alumno (id, activo, nombre, apellidos, numero_expediente, fecha_nacimiento, nif, direccion,
				  telefono, telefono2, genero, localidad, codigo_postal, email, fecha_alta, fecha_alta_inicial, fecha_baja,
				  autorizacion_web, tiene_discapacidad, tiene_derecho_examen, organizacion_id, sede_principal_id)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, 'FEMENINO', 'Sevilla', '41001', ?, '2020-09-01', '2019-09-01', ?,
				  1, ?, 0, 1, ?)""",
				id, activo, nombre, apellidos, EXPEDIENTE + n, Date.valueOf(nacimiento), nif, "Calle P7A " + n,
				String.format("6100000%02d", n), String.format("dorado-p7a-a%02d@example.test", n),
				activo ? null : Date.valueOf("2025-06-30"), n == 7, sede);
		simbolos.put(id, String.format("alumno:A%02d", n));
	}

	private void deporte(int n, int alumno, long disciplina, boolean activo, Long tarifa, Double cuantia, String rol,
			String grupoFamiliar) {
		long id = ALUMNO_DEPORTE + n;
		jdbc.update("""
				INSERT INTO alumno_deporte (id, activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
				  organizacion_id, fecha_alta, fecha_alta_inicial, fecha_grado, fecha_baja, disciplina_id, grado_id,
				  tarifa_id, cuantia_tarifa, rol_familiar, grupo_familiar)
				VALUES (?, ?, 0, ?, 0, 1, 0, 1, '2020-09-01', '2019-09-01', NULL, ?, ?, NULL, ?, ?, ?, ?)""",
				id, activo, ALUMNO + alumno, activo ? null : Date.valueOf("2025-06-30"), disciplina, tarifa, cuantia, rol,
				grupoFamiliar);
		simbolos.put(id, String.format("alumnoDeporte:A%02d/%d", alumno, n));
	}

	private void tutor(int n, int alumno, String nombre, String apellidos, String nif, String parentesco,
			boolean legal) {
		jdbc.update("""
				INSERT INTO tutor (id, organizacion_id, alumno_id, nombre, apellidos, nif, parentesco, telefono, email,
				  responsable_legal, orden) VALUES (?, 1, ?, ?, ?, ?, ?, '611000000', NULL, ?, ?)""",
				TUTOR + n, ALUMNO + alumno, nombre, apellidos, nif, parentesco, legal, n);
	}

	private void pre(int n, String estado, String temporadaPre, String nombre, String apellidos, String dni,
			String nacimiento, String tutorNombre, String tutorDni, long grupo, long disciplina, long plantilla,
			String creada, Long alumno, List<Long> turnos) {
		long id = PREINSCRIPCION + n;
		String referencia = String.format("P%02d", n);
		jdbc.update("""
				INSERT INTO preinscripcion (id, referencia, temporada, estado, nombre, apellidos, dni, identidad_hash,
				  fecha_nacimiento, direccion, telefono, email, tiene_discapacidad, consentimiento_fotografico, tutor_nombre,
				  tutor_dni, aceptacion_normas, firmante_nombre, firma, plantilla_id, plantilla_snapshot, pdf_firmado,
				  token_documento_hash, creada_en, actualizada_en, grupo_id, organizacion_id, disciplina_id, alumno_id,
				  grupo_snapshot)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 'Firmante P7A', x'00', ?, '{}', x'00', ?, ?, ?, ?,
				  1, ?, ?, ?)""",
				id, PREFIJO + "-" + referencia, temporadaPre, estado, nombre, apellidos, dni,
				String.format("%064d", id), Date.valueOf(nacimiento), "Calle Pre " + n, "62000000" + n,
				"dorado-p7a-p" + n + "@example.test", n == 3 ? null : Boolean.FALSE, n % 2 == 0, tutorNombre, tutorDni,
				plantilla, "f".repeat(64), creada, creada, grupo, disciplina, alumno, "Snapshot original " + referencia);
		for (Long turno : turnos) {
			jdbc.update("INSERT INTO preinscripcion_turno (preinscripcion_id, turno_id) VALUES (?, ?)", id, turno);
		}
		referencias.add(referencia);
	}

	/** Cuenta de la organización 1 con ese rol (y, para el responsable, FAMILIA con su alumno propio, D6-15). */
	private String token(String rol, Long sede) {
		Usuario usuario = TenantContext.calcularCon(1L, () -> {
			Usuario u = new Usuario();
			u.setNombre("Dorado");
			u.setApellidos("P7A " + rol);
			u.setEmail("dorado-p7a-" + rol.toLowerCase() + "-" + System.nanoTime() + "@example.test");
			u.setContrasena("fake-Password-123");
			u.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(u);
		});
		usuariosCreados.add(usuario.getId());
		if (sede != null) {
			jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, 1, 'FAMILIA')",
					usuario.getId());
			jdbc.update("INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen) VALUES "
					+ "(?, ?, 1, 'TUTOR', 'MANUAL')", usuario.getId(), ALUMNO + 90);
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
						System.err.println("Dorado P7a: no se pudo restaurar AUTO_INCREMENT de " + tabla + ": " + e);
					}
				}
			} finally {
				try {
					if (sede2 != null) {
						jdbc.update("DELETE FROM sede WHERE id = ?", sede2);
					}
				} finally {
					System.out.println("Dorado P7a · sentencias por petición:");
					medidas.forEach((k, v) -> System.out.println("  " + k + " = " + v));
				}
			}
		}
	}

	private void borrarRangos() {
		borrar("DELETE FROM preinscripcion_turno WHERE preinscripcion_id BETWEEN ? AND ?", PREINSCRIPCION);
		borrar("DELETE FROM preinscripcion WHERE id BETWEEN ? AND ?", PREINSCRIPCION);
		borrar("DELETE FROM usuario_alumno WHERE alumno_id BETWEEN ? AND ?", ALUMNO);
		borrar("DELETE FROM tutor WHERE id BETWEEN ? AND ?", TUTOR);
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
		String ruta = caso.ruta().replace("{temporada}", temporada);
		peticion(caso.rol(), ruta, caso.estado()); // calentamiento
		ContadorSentencias.Conteo<String> conteo = ContadorSentencias.contar(() -> peticion(caso.rol(), ruta,
				caso.estado()));
		medidas.put(caso.nombre(), conteo.total() + " (límite " + caso.limiteSql() + ")");
		JsonNode real = caso.estado() == 200 ? normalizarNodo(json.readTree(conteo.resultado()), "$")
				: JsonNodeFactory.instance.objectNode().put("estado", caso.estado());
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
	 * Por rol: las {@code alumnosCoincidentes} del detalle de cada solicitud sembrada que el rol ve en el listado son
	 * las que traía la fila del listado antes de R2-P7a ({@code coincidentes_<rol>.json}, mismo orden); el detalle
	 * de las que no ve responde 403 (mismo alcance que el listado).
	 */
	@ParameterizedTest(name = "{0}")
	@EnumSource(Rol.class)
	void coincidentesDelDetalleIgualesALasDelListadoAnterior(Rol rol) throws Exception {
		JsonNode listado = json.readTree(peticion(rol, "/api/preinscripciones?q=" + PREFIJO + "&page=0&size=100", 200));
		ObjectNode delDetalle = JsonNodeFactory.instance.objectNode();
		ObjectNode delListado = JsonNodeFactory.instance.objectNode();
		Set<String> visibles = new LinkedHashSet<>();
		for (JsonNode fila : listado.get("content")) {
			String referencia = fila.get("referencia").asText().substring(PREFIJO.length() + 1);
			visibles.add(referencia);
			JsonNode detalle = json.readTree(peticion(rol, "/api/preinscripciones/" + PREFIJO + "-" + referencia, 200));
			delDetalle.set(referencia, normalizarNodo(detalle.get("alumnosCoincidentes"), "$"));
			if (fila.has("alumnosCoincidentes")) { // antes de R2-P7a: la fila también debe coincidir
				delListado.set(referencia, normalizarNodo(fila.get("alumnosCoincidentes"), "$"));
			}
		}
		Set<String> esperadas = new LinkedHashSet<>(referencias);
		if (rol == Rol.RESPONSABLE) {
			esperadas.removeAll(deLaSede2);
		}
		assertEquals(esperadas, visibles, rol + ": solicitudes sembradas visibles en el listado");
		for (String referencia : referencias) {
			if (!visibles.contains(referencia)) {
				peticion(rol, "/api/preinscripciones/" + PREFIJO + "-" + referencia, 403);
			}
		}
		String fichero = "coincidentes_" + rol.name().toLowerCase() + ".json";
		if (REGENERAR) {
			escribir(fichero, delDetalle);
		} else {
			compararConDorado(fichero, delDetalle);
		}
		if (!delListado.isEmpty()) {
			compararConDorado(fichero, delListado);
		}
		assertFalse(delDetalle.get("P02").isEmpty(), "P02 debe tener candidatas para cualquier rol");
	}

	// ------------------------------------------------------------------ utilidades

	private String peticion(Rol rol, String ruta, int estado) throws Exception {
		MvcResult r = mvc.perform(get(ruta).cookie(new Cookie("jwt", tokens.get(rol)))).andReturn();
		assertEquals(estado, r.getResponse().getStatus(), rol + " " + ruta + ": " + r.getResponse().getContentAsString());
		return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
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
		// Solo el campo escalar (java.util.Date en ms, como en MoiskimdoAlumnosJsonDoradoIntegrationTest): las LocalDate
		// llegan como [año, mes, día] y esos números no son milisegundos (ruta «fechaNacimiento[*]»), se quedan tal cual.
		if (nodo.isIntegralNumber() && !ruta.endsWith("[*]")
				&& ruta.substring(ruta.lastIndexOf('.') + 1).startsWith("fecha")) {
			return TextNode.valueOf(fecha(nodo.asLong()));
		}
		if (nodo.isNumber() && ruta.endsWith("En")) {
			// Instant en segundos con decimales (creadaEn...): Hibernate lo lee de la DATETIME sembrada como UTC, así que
			// no depende de la zona de la JVM; se escribe legible (ISO, UTC) para que el dorado se pueda revisar.
			return TextNode.valueOf("instante:" + Instant.ofEpochMilli(nodo.decimalValue().movePointRight(3).longValue()));
		}
		if (nodo.isIntegralNumber()) {
			String simbolo = simbolos.get(nodo.asLong());
			return simbolo != null ? TextNode.valueOf(simbolo) : nodo;
		}
		if (nodo.isTextual() && nodo.asText().equals(temporada)) {
			return TextNode.valueOf("<temporada-actual>");
		}
		if (nodo.isTextual() && nodo.asText().equals(temporadaAnterior)) {
			return TextNode.valueOf("<temporada-anterior>");
		}
		return nodo;
	}

	/** {@code java.sql.Date} de MySQL en milisegundos: la medianoche de la zona de la JVM, vuelta a fecha de calendario. */
	private static String fecha(long milisegundos) {
		LocalDateTime local = LocalDateTime.ofInstant(Instant.ofEpochMilli(milisegundos), ZoneId.systemDefault());
		return "fecha:" + (local.toLocalTime().equals(LocalTime.MIDNIGHT) ? local.toLocalDate() : local);
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
