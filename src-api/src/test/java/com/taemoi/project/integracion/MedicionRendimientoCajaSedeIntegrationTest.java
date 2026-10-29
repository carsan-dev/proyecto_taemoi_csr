package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Arnés de MEDICIÓN de la apertura de «Caja de sede» (panel nuevo), no un test de regresión. Siembra datos SINTÉTICOS
 * en la organización 1 (800 alumnos con deportes, grupos y tutores; 24 cargos por alumno; cobros en efectivo del
 * profesor, entregas y movimientos del mes que referencian alumnos) y mide, por petición y por rol (propietario y
 * responsable de sede), nº de sentencias SQL y la mediana de 5 tiempos tras un calentamiento.
 *
 * <p>La apertura que hace el navegador: las sedes (antes, {@code /api/tesoreria/resumen} sin fechas; ahora, las
 * {@code sedes} de {@code /api/auth/permisos}, que el panel ya tiene en caché), las cuatro lecturas de la caja de la sede
 * (resumen, movimientos, pendiente de entrega y entregas) y, antes, todos los alumnos ({@code /api/alumnos}) para
 * poner nombre a los movimientos. Se miden todas para comparar antes y después; el informe va a
 * {@code target/caja-medicion/medicion.md}.
 *
 * <p>Fuera de la batería por defecto, como {@link MedicionRendimientoAlumnosIntegrationTest}: deja 800 alumnos en la
 * organización 1. Solo corre con {@code -Dmedicion.rendimiento=true}, en solitario:
 * {@code ./mvnw -o test -Dtest=MedicionRendimientoCajaSedeIntegrationTest -Dmedicion.rendimiento=true}.
 */
@EnabledIfSystemProperty(named = "medicion.rendimiento", matches = "true")
@SpringBootTest(properties = {
		"spring.jpa.properties.hibernate.session_factory.statement_inspector=com.taemoi.project.integracion.ContadorSentencias",
		"logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN" })
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MedicionRendimientoCajaSedeIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final int N_ALUMNOS = Integer.getInteger("caja.alumnos", 800);
	private static final int CARGOS_POR_ALUMNO = 24;
	private static final int REPETICIONES = 5;
	private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");
	private static final Path SALIDA = Path.of("target", System.getProperty("caja.salida", "caja-medicion"));

	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;

	@MockitoBean JavaMailSender mailSender;

	long sede;
	String tPropietario;
	String tResponsable;

	@BeforeAll
	void sembrar() {
		long t0 = System.nanoTime();
		Long existente = jdbc.queryForObject("SELECT MIN(id) FROM sede WHERE organizacion_id = 1", Long.class);
		if (existente == null) {
			jdbc.update("INSERT INTO sede (organizacion_id, nombre, activa) VALUES (1, 'Sede Caja', TRUE)");
			existente = jdbc.queryForObject("SELECT MIN(id) FROM sede WHERE organizacion_id = 1", Long.class);
		}
		sede = existente;
		jdbc.update("INSERT INTO sede (organizacion_id, nombre, activa) VALUES (1, 'Sede Caja Dos', TRUE)");
		long tkd = jdbc.queryForObject(
				"SELECT id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'TAEKWONDO'", Long.class);
		Long grado = jdbc.queryForObject("SELECT MIN(id) FROM grado", Long.class);

		List<Long> grupos = new ArrayList<>();
		for (int g = 0; g < 4; g++) {
			jdbc.update("INSERT INTO grupo (nombre, tipo, organizacion_id, disciplina_id, sede_id, rango_edad_min, "
					+ "rango_edad_max) VALUES (?, NULL, 1, ?, ?, 4, 60)", "Caja Grupo " + g, tkd, sede);
			grupos.add(jdbc.queryForObject("SELECT MAX(id) FROM grupo WHERE organizacion_id = 1", Long.class));
		}

		// Alumnos: 90 % activos, sede principal la de la caja, taekwondo; uno de cada 5 con tutor.
		List<Object[]> filas = new ArrayList<>();
		for (int i = 0; i < N_ALUMNOS; i++) {
			filas.add(new Object[] { i % 10 != 0, String.format("SINTETICO CAJA %03d", N_ALUMNOS - i),
					"Calle Sintética " + i, Date.valueOf(LocalDate.of(2005 + (i % 15), 1 + (i % 12), 1 + (i % 27))),
					String.format("Caja Alumno %03d", i), 800000 + i, "611" + String.format("%06d", i),
					"caja-alumno-" + i + "@example.test", sede, Date.valueOf(LocalDate.of(2020, 9, 1)) });
		}
		jdbc.batchUpdate("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, tiene_discapacidad, email, organizacion_id,
				  sede_principal_id, fecha_alta, fecha_alta_inicial)
				VALUES (?, ?, 1, ?, ?, ?, ?, ?, 0, 0, ?, 1, ?, ?, ?)""",
				filas.stream().map(f -> new Object[] { f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8], f[9], f[9] })
						.toList());
		List<Long> alumnos = jdbc.queryForList("SELECT id FROM alumno WHERE organizacion_id = 1 "
				+ "AND numero_expediente BETWEEN 800000 AND 899999 ORDER BY numero_expediente", Long.class);

		List<Object[]> deportes = new ArrayList<>();
		List<Object[]> enGrupo = new ArrayList<>();
		List<Object[]> tutores = new ArrayList<>();
		for (int i = 0; i < alumnos.size(); i++) {
			deportes.add(new Object[] { alumnos.get(i), tkd, grado });
			enGrupo.add(new Object[] { grupos.get(i % grupos.size()), alumnos.get(i) });
			if (i % 5 == 0) {
				tutores.add(new Object[] { alumnos.get(i), "Tutor Caja " + i });
			}
		}
		jdbc.batchUpdate("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
				  organizacion_id, fecha_alta, fecha_alta_inicial, fecha_grado, disciplina_id, grado_id)
				VALUES (TRUE, 0, ?, 0, 1, 0, 1, '2020-09-01', '2020-09-01', '2021-01-01', ?, ?)""", deportes);
		jdbc.batchUpdate("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", enGrupo);
		jdbc.batchUpdate("""
				INSERT INTO tutor (organizacion_id, alumno_id, nombre, nif, parentesco, responsable_legal, orden)
				VALUES (1, ?, ?, NULL, 'TUTOR_LEGAL', TRUE, 0)""", tutores);

		// Histórico de cargos: 24 mensualidades por alumno (90 % pagadas), con la sede de la caja.
		LocalDate hoy = LocalDate.now(ZONA);
		List<Object[]> cargos = new ArrayList<>();
		for (int i = 0; i < alumnos.size(); i++) {
			for (int m = 0; m < CARGOS_POR_ALUMNO; m++) {
				LocalDate mes = hoy.withDayOfMonth(1).minusMonths(m);
				boolean pagado = (i + m) % 10 != 0;
				cargos.add(new Object[] { alumnos.get(i), "MENSUALIDAD CAJA " + mes, pagado,
						Timestamp.valueOf(mes.atTime(10, 0)), pagado ? Timestamp.valueOf(mes.atTime(18, 0)) : null,
						sede });
			}
		}
		jdbc.batchUpdate("""
				INSERT INTO producto_alumno (alumno_id, concepto, precio, cantidad, pagado, fecha_asignacion, fecha_pago,
				  organizacion_id, sede_id)
				VALUES (?, ?, 35.0, 1, ?, ?, ?, 1, ?)""", cargos);

		// Cuentas: propietario, profesor (cobrador) y responsable de la sede (FAMILIA + RESPONSABLE_SEDE).
		long propietario = cuenta("propietario");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, 1, 'PROPIETARIO')",
				propietario);
		long profesor = cuenta("profesor");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, 1, 'PROFESOR')", profesor);
		long responsable = cuenta("responsable");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, 1, 'FAMILIA')", responsable);
		jdbc.update("""
				INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen)
				VALUES (?, ?, 1, 'TUTOR', 'MANUAL')""", responsable, alumnos.get(1));
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, 1, 'RESPONSABLE_SEDE')",
				responsable);
		long membresia = jdbc.queryForObject("SELECT id FROM membresia WHERE usuario_id = ? AND organizacion_id = 1 "
				+ "AND rol = 'RESPONSABLE_SEDE'", Long.class, responsable);
		jdbc.update("INSERT INTO membresia_sede (membresia_id, sede_id) VALUES (?, ?)", membresia, sede);

		// Caja: 20 entregas con 10 cobros cada una y 40 cobros aún pendientes de entrega (del mes).
		List<Long> pagados = jdbc.queryForList("SELECT id FROM producto_alumno WHERE organizacion_id = 1 AND sede_id = ? "
				+ "AND pagado = 1 AND concepto LIKE 'MENSUALIDAD CAJA %' ORDER BY id LIMIT 240", Long.class, sede);
		LocalDateTime ahora = LocalDateTime.now(ZONA);
		for (int e = 0; e < 20; e++) {
			jdbc.update("""
					INSERT INTO entrega_caja (organizacion_id, sede_id, entregado_por_usuario_id, recibido_por_usuario_id,
					  importe_declarado, importe_contado, fecha, estado) VALUES (1, ?, ?, ?, 350.00, 350.00, ?, 'ACTIVA')""",
					sede, profesor, propietario, Timestamp.valueOf(ahora.minusDays(40 - e)));
		}
		List<Long> entregas = jdbc.queryForList(
				"SELECT id FROM entrega_caja WHERE organizacion_id = 1 AND sede_id = ? ORDER BY id", Long.class, sede);
		List<Object[]> cobros = new ArrayList<>();
		for (int c = 0; c < pagados.size(); c++) {
			long cargo = pagados.get(c);
			Long entrega = c < 200 ? entregas.get(c / 10) : null;
			cobros.add(new Object[] { cargo, sede, cargo, Timestamp.valueOf(ahora.minusDays(c < 200 ? 45 : 2)), profesor,
					entrega });
		}
		jdbc.batchUpdate("""
				INSERT INTO cobro (organizacion_id, producto_alumno_id, sede_id, alumno_id, concepto, importe, metodo,
				  fecha_cobro, cobrado_por_usuario_id, origen, estado, entrega_caja_id)
				SELECT 1, ?, ?, pa.alumno_id, pa.concepto, 35.00, 'EFECTIVO', ?, ?, 'PROFESOR', 'ACTIVO', ?
				FROM producto_alumno pa WHERE pa.id = ?""",
				cobros.stream().map(f -> new Object[] { f[0], f[1], f[3], f[4], f[5], f[2] }).toList());

		// 40 movimientos del mes: 30 con alumno (distintos) y 10 sin alumno.
		List<Object[]> movimientos = new ArrayList<>();
		for (int m = 0; m < 40; m++) {
			Long alumno = m < 30 ? alumnos.get(m * 7) : null;
			movimientos.add(new Object[] { sede, m < 30 ? "DEVOLUCION" : "OTRO", m < 30 ? -5.00 : 3.00, alumno,
					"Movimiento sintético " + m, propietario, Timestamp.valueOf(ahora.withDayOfMonth(1).plusHours(m)) });
		}
		jdbc.batchUpdate("""
				INSERT INTO movimiento_caja (organizacion_id, sede_id, tipo, importe, alumno_id, nota, creado_por_usuario_id,
				  fecha, estado) VALUES (1, ?, ?, ?, ?, ?, ?, ?, 'ACTIVO')""", movimientos);

		tPropietario = token(propietario);
		tResponsable = token(responsable);
		System.out.printf("Caja siembra: %d alumnos, %d cargos en %d ms%n", alumnos.size(), cargos.size(),
				(System.nanoTime() - t0) / 1_000_000);
	}

	@Test
	void medir() throws Exception {
		Files.createDirectories(SALIDA);
		LocalDate hoy = LocalDate.now(ZONA);
		String periodo = "desde=" + hoy.withDayOfMonth(1) + "&hasta=" + hoy.withDayOfMonth(hoy.lengthOfMonth());
		String base = "/api/caja/sedes/" + sede;
		Map<String, String> rutas = new LinkedHashMap<>();
		rutas.put("sedes_tesoreria_resumen", "/api/tesoreria/resumen");
		rutas.put("sedes_permisos", "/api/auth/permisos");
		rutas.put("caja_resumen", base + "/resumen?" + periodo);
		rutas.put("caja_movimientos", base + "/movimientos?" + periodo);
		rutas.put("caja_pendiente_entrega", base + "/pendiente-entrega");
		rutas.put("caja_entregas", base + "/entregas?page=1&size=25");
		rutas.put("alumnos_todos", "/api/alumnos?incluirInactivos=true");

		StringBuilder informe = new StringBuilder("# Apertura de Caja de sede (")
				.append(N_ALUMNOS).append(" alumnos)\n");
		for (Map.Entry<String, String> rol : Map.of("propietario", tPropietario, "responsable", tResponsable)
				.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
			informe.append("\n## ").append(rol.getKey()).append("\n\n| petición | SQL | ms (mediana de ")
					.append(REPETICIONES).append(") | KB |\n|---|---|---|---|\n");
			Map<String, long[]> medidas = new LinkedHashMap<>();
			for (Map.Entry<String, String> e : rutas.entrySet()) {
				long[] m = medir(e.getValue(), rol.getValue());
				medidas.put(e.getKey(), m);
				informe.append(String.format("| %s | %d | %d | %.1f |%n", e.getKey(), m[0], m[1], m[2] / 1024.0));
			}
			long[] caja = suma(medidas, "caja_resumen", "caja_movimientos", "caja_pendiente_entrega", "caja_entregas");
			long cajaMax = Math.max(Math.max(medidas.get("caja_resumen")[1], medidas.get("caja_movimientos")[1]),
					Math.max(medidas.get("caja_pendiente_entrega")[1], medidas.get("caja_entregas")[1]));
			long[] tes = medidas.get("sedes_tesoreria_resumen");
			long[] alu = medidas.get("alumnos_todos");
			long[] per = medidas.get("sedes_permisos");
			informe.append(String.format("%nAntes (tesorería + 4 de caja + alumnos): %d SQL, %d ms sumados; ruta crítica "
					+ "tesorería → caja → alumnos ≈ %d ms%n", tes[0] + caja[0] + alu[0], tes[1] + caja[1] + alu[1],
					tes[1] + cajaMax + alu[1]));
			informe.append(String.format("Después (4 de caja; permisos ya en caché): %d SQL, %d ms sumados; ruta crítica "
					+ "≈ %d ms (con permisos sin caché: +%d SQL, +%d ms)%n", caja[0], caja[1], cajaMax, per[0], per[1]));
		}
		Files.writeString(SALIDA.resolve("medicion.md"), informe.toString(), StandardCharsets.UTF_8);
		System.out.println(informe);
	}

	/** {SQL, mediana en ms, bytes} de una petición GET tras un calentamiento. */
	private long[] medir(String ruta, String token) throws Exception {
		peticion(ruta, token); // calentamiento
		ContadorSentencias.Conteo<String> conteo = ContadorSentencias.contar(() -> peticion(ruta, token));
		long[] tiempos = new long[REPETICIONES];
		for (int i = 0; i < REPETICIONES; i++) {
			long t = System.nanoTime();
			peticion(ruta, token);
			tiempos[i] = (System.nanoTime() - t) / 1_000_000;
		}
		Arrays.sort(tiempos);
		return new long[] { conteo.total(), tiempos[REPETICIONES / 2],
				conteo.resultado().getBytes(StandardCharsets.UTF_8).length };
	}

	private static long[] suma(Map<String, long[]> medidas, String... claves) {
		long[] total = new long[3];
		for (String c : claves) {
			for (int i = 0; i < 3; i++) {
				total[i] += medidas.get(c)[i];
			}
		}
		return total;
	}

	private String peticion(String ruta, String token) throws Exception {
		MvcResult r = mvc.perform(get(ruta).cookie(new Cookie("jwt", token))).andReturn();
		assertEquals(200, r.getResponse().getStatus(), ruta + " → " + r.getResponse().getContentAsString());
		return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	private long cuenta(String nombre) {
		return TenantContext.calcularCon(ORG1, () -> {
			Usuario u = new Usuario();
			u.setNombre("Caja");
			u.setApellidos(nombre);
			u.setEmail("caja-medicion-" + nombre + "@example.test");
			u.setContrasena("fake-Password-123");
			u.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(u).getId();
		});
	}

	private String token(long usuarioId) {
		Usuario usuario = TenantContext.calcularCon(ORG1, () -> usuarios.findById(usuarioId).orElseThrow());
		return jwtService.generateToken(usuario, 600_000, ORG1);
	}
}
