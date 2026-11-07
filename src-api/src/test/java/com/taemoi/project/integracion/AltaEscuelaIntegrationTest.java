package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.EmailService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Fase 4, pieza F4-2 (decisiones 390, 392 y 444): alta de una escuela por el superadmin, por HTTP real contra MySQL con
 * la cadena de filtros completa. {@code GET /api/plataforma/plantilla/disciplinas} y {@code POST /api/plataforma/escuelas}:
 * escuela, primera sede, propietario (invitación o cuenta existente) y catálogo de las disciplinas elegidas copiado de la
 * plantilla (la organización 1), con las tarifas desactivadas.
 *
 * <p>La plantilla es la organización 1 de la base de pruebas; para cubrir los productos de examen compartidos entre
 * disciplinas (sin disciplina y con grado, como en producción) se añaden dos y se enlazan a sus grados, y se retiran al
 * terminar. Datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
class AltaEscuelaIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String CLAVE = "Clave-Segura-123";
	private static final String RUTA = "/api/plataforma/escuelas";
	private static final String RUTA_PLANTILLA = "/api/plataforma/plantilla/disciplinas";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired PasswordEncoder encoder;

	@MockitoBean EmailService email;

	final String marca = java.util.UUID.randomUUID().toString().substring(0, 8);
	Cookie cookieSuperadmin;
	String tPropietario;
	long examenAmarillo, recompensaAmarillo, gradoTkdAmarillo, gradoKbAmarillo;
	long disciplinaTkd, licenciaTkd, gradoTkdEquivalente;
	Long licenciaTkdAntes, equivalenteAmarilloAntes;

	@BeforeAll
	void preparar() throws Exception {
		Usuario superadmin = crearCuenta("it-f42-superadmin-" + marca + "@example.test");
		assertEquals(1, jdbc.update("UPDATE usuario SET es_admin_plataforma = TRUE WHERE id = ?", superadmin.getId()));
		cookieSuperadmin = new Cookie("jwt", login(superadmin.getEmail()));

		// Propietario (ROLE_ADMIN) de una escuela sintética, sin la autoridad de plataforma.
		String slug = "it-f42-otra-" + marca;
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES (?, 'Escuela IT F42 otra')", slug);
		long otra = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = ?", Long.class, slug);
		Usuario propietario = crearCuenta("it-f42-propietario-" + marca + "@example.test");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROPIETARIO')",
				propietario.getId(), otra);
		tPropietario = jwtService.generateToken(propietario, 600_000, otra);

		// Productos compartidos de la plantilla (sin disciplina, con grado de taekwondo) y su enlace desde los grados.
		gradoTkdAmarillo = gradoDe("TAEKWONDO", "AMARILLO");
		gradoKbAmarillo = gradoDe("KICKBOXING", "AMARILLO");
		examenAmarillo = producto("IT F42 EXAMEN AMARILLO " + marca, "EXAMEN", gradoTkdAmarillo, 18.5);
		recompensaAmarillo = producto("IT F42 RECOMPENSA AMARILLO " + marca, "RECOMPENSA", gradoTkdAmarillo, 5.0);
		jdbc.update("UPDATE grado SET producto_examen_id = ?, producto_recompensa_id = ? WHERE id IN (?, ?)",
				examenAmarillo, recompensaAmarillo, gradoTkdAmarillo, gradoKbAmarillo);

		// Enlaces que la base de pruebas no trae (la migración los deja vacíos): la licencia de taekwondo (un producto
		// suyo) y el equivalente adulto del amarillo (el último grado de su sistema). Se restauran al terminar.
		disciplinaTkd = jdbc.queryForObject("SELECT id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'TAEKWONDO'",
				Long.class);
		licenciaTkdAntes = jdbc.queryForObject("SELECT producto_licencia_id FROM disciplina WHERE id = ?", Long.class,
				disciplinaTkd);
		equivalenteAmarilloAntes = jdbc.queryForObject("SELECT equivalente_adulto_id FROM grado WHERE id = ?", Long.class,
				gradoTkdAmarillo);
		jdbc.update("""
				INSERT INTO producto (concepto, precio, organizacion_id, tipo, disciplina_id, vendible_profesor)
				VALUES (?, 30, 1, 'LICENCIA', ?, FALSE)""", "IT F42 LICENCIA " + marca, disciplinaTkd);
		licenciaTkd = jdbc.queryForObject("SELECT id FROM producto WHERE organizacion_id = 1 AND concepto = ?", Long.class,
				"IT F42 LICENCIA " + marca);
		gradoTkdEquivalente = jdbc.queryForObject("""
				SELECT g.id FROM grado g JOIN disciplina d ON d.sistema_grados_id = g.sistema_grados_id
				WHERE d.id = ? AND g.id <> ? ORDER BY g.orden DESC, g.id DESC LIMIT 1""", Long.class, disciplinaTkd,
				gradoTkdAmarillo);
		jdbc.update("UPDATE disciplina SET producto_licencia_id = ? WHERE id = ?", licenciaTkd, disciplinaTkd);
		jdbc.update("UPDATE grado SET equivalente_adulto_id = ? WHERE id = ?", gradoTkdEquivalente, gradoTkdAmarillo);
	}

	@AfterAll
	void retirar() {
		jdbc.update("UPDATE disciplina SET producto_licencia_id = ? WHERE id = ?", licenciaTkdAntes, disciplinaTkd);
		jdbc.update("UPDATE grado SET equivalente_adulto_id = ? WHERE id = ?", equivalenteAmarilloAntes, gradoTkdAmarillo);
		jdbc.update("DELETE FROM producto WHERE id = ?", licenciaTkd);
		jdbc.update("UPDATE grado SET producto_examen_id = NULL, producto_recompensa_id = NULL WHERE id IN (?, ?)",
				gradoTkdAmarillo, gradoKbAmarillo);
		jdbc.update("DELETE FROM producto WHERE id IN (?, ?)", examenAmarillo, recompensaAmarillo);
	}

	@BeforeEach
	void sinCorreos() {
		clearInvocations(email);
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ plantilla

	@Test
	void plantilla_disciplinasConSusCuentas_enElOrdenDelCatalogo() throws Exception {
		JsonNode lista = leer(get(RUTA_PLANTILLA), cookieSuperadmin, 200);
		List<Map<String, Object>> esperadas = jdbc.queryForList("""
				SELECT d.id, d.codigo, d.nombre, d.sistema_grados_id FROM disciplina d
				WHERE d.organizacion_id = 1 AND d.activa = TRUE ORDER BY d.orden, d.id""");
		assertEquals(esperadas.size(), lista.size(), lista.toString());
		for (int i = 0; i < esperadas.size(); i++) {
			Map<String, Object> d = esperadas.get(i);
			JsonNode dto = lista.get(i);
			long id = ((Number) d.get("id")).longValue();
			assertEquals(d.get("codigo"), dto.get("codigo").asText());
			assertEquals(d.get("nombre"), dto.get("nombre").asText());
			assertEquals(d.get("sistema_grados_id") != null, dto.get("conGrados").asBoolean());
			assertEquals(contar("SELECT COUNT(*) FROM tarifa WHERE organizacion_id = 1 AND disciplina_id = ?", id),
					dto.get("tarifas").asLong(), dto.toString());
			// Los suyos más los compartidos (sin disciplina, con grado) cuyo código de grado está en su sistema.
			long propios = contar("SELECT COUNT(*) FROM producto WHERE organizacion_id = 1 AND disciplina_id = ?", id);
			long compartidos = contar("""
					SELECT COUNT(*) FROM producto p JOIN grado g ON g.id = p.grado_id
					WHERE p.organizacion_id = 1 AND p.disciplina_id IS NULL
					  AND EXISTS (SELECT 1 FROM grado g2 JOIN disciplina d ON d.sistema_grados_id = g2.sistema_grados_id
					              WHERE d.id = ? AND g2.codigo = g.codigo)""", id);
			assertEquals(propios + compartidos, dto.get("productos").asLong(), dto.toString());
		}
		java.util.Set<String> campos = new java.util.TreeSet<>();
		lista.get(0).fieldNames().forEachRemaining(campos::add);
		assertEquals(new java.util.TreeSet<>(List.of("codigo", "conGrados", "nombre", "productos", "tarifas")), campos);
		JsonNode taekwondo = lista.get(0);
		assertEquals("TAEKWONDO", taekwondo.get("codigo").asText());
		assertTrue(taekwondo.get("productos").asLong() >= 2, "Al menos los dos compartidos de la prueba");
	}

	// ------------------------------------------------------------------ alta

	@Test
	void alta_conInvitacion_creaEscuelaSedePropietarioYCatalogoDeLasElegidas() throws Exception {
		String antes = huellaOrg1();
		String nombre = "Club IT Ñandú Ávila " + marca;
		String emailPropietario = "it-f42-nuevo-" + marca + "@example.test";
		JsonNode creada = leer(alta(cuerpo("  " + nombre + "  ", "buzon-" + marca + "@example.test", "Sede Centro",
				"Ana", "Prueba Ficticia", emailPropietario, "TAEKWONDO")), cookieSuperadmin, 201);
		long org = creada.get("id").asLong();
		assertEquals(nombre, creada.get("nombre").asText());
		assertEquals(emailPropietario, creada.get("invitacionEnviadaA").asText());
		assertEquals(3, creada.size(), creada.toString());

		Map<String, Object> fila = jdbc.queryForMap("""
				SELECT slug, nombre, email_contacto, activa, comportamiento_legado_taemoi, zona_horaria
				FROM organizacion WHERE id = ?""", org);
		assertEquals("club-it-nandu-avila-" + marca, fila.get("slug"));
		assertEquals(nombre, fila.get("nombre"));
		assertEquals("buzon-" + marca + "@example.test", fila.get("email_contacto"));
		assertEquals(Boolean.TRUE, fila.get("activa"));
		assertEquals(Boolean.FALSE, fila.get("comportamiento_legado_taemoi"));
		assertEquals("Europe/Madrid", fila.get("zona_horaria"));

		assertEquals(List.of("Sede Centro"),
				jdbc.queryForList("SELECT nombre FROM sede WHERE organizacion_id = ? AND activa = TRUE", String.class, org));

		// Cuenta pendiente con la invitación de ESTA escuela; propietario y nada más.
		Map<String, Object> cuenta = jdbc.queryForMap("""
				SELECT id, nombre, apellidos, contrasena, reset_token_hash, ultima_organizacion_id
				FROM usuario WHERE email = ?""", emailPropietario);
		long usuario = ((Number) cuenta.get("id")).longValue();
		assertEquals("Ana", cuenta.get("nombre"));
		assertNull(cuenta.get("contrasena"));
		assertNotNull(cuenta.get("reset_token_hash"));
		assertEquals(org, ((Number) cuenta.get("ultima_organizacion_id")).longValue());
		assertEquals(List.of("PROPIETARIO"), jdbc.queryForList(
				"SELECT rol FROM membresia WHERE usuario_id = ? AND activa = TRUE", String.class, usuario));
		assertEquals(List.of(org), jdbc.queryForList(
				"SELECT organizacion_id FROM membresia WHERE usuario_id = ?", Long.class, usuario));
		verify(email).sendEmailConImagen(eq(emailPropietario), contains(nombre),
				anyString(), any(), any(), any(), any());

		// Catálogo: solo taekwondo (y lo común); sistema con plantilla_origen y grados con nombre, colores y orden.
		assertEquals(List.of("TAEKWONDO"),
				jdbc.queryForList("SELECT codigo FROM disciplina WHERE organizacion_id = ?", String.class, org));
		assertEquals(List.of("TAEKWONDO"), jdbc.queryForList(
				"SELECT plantilla_origen FROM sistema_grados WHERE organizacion_id = ?", String.class, org));
		assertEquals(huella("""
				SELECT g.codigo, g.nombre, g.orden, g.color_1, g.color_2, g.aplica_a, g.requiere_examen,
				       g.meses_minimos_menor, g.meses_minimos_adulto, g.bloque_temario
				FROM grado g JOIN disciplina d ON d.sistema_grados_id = g.sistema_grados_id
				WHERE d.organizacion_id = ? AND d.codigo = 'TAEKWONDO' ORDER BY g.codigo""", ORG1),
				huella("""
						SELECT g.codigo, g.nombre, g.orden, g.color_1, g.color_2, g.aplica_a, g.requiere_examen,
						       g.meses_minimos_menor, g.meses_minimos_adulto, g.bloque_temario
						FROM grado g WHERE g.organizacion_id = ? ORDER BY g.codigo""", org));
		assertEquals(0, contar("""
				SELECT COUNT(*) FROM grado g JOIN sistema_grados s ON s.id = g.sistema_grados_id
				WHERE g.organizacion_id = ? AND s.organizacion_id <> g.organizacion_id""", org));
		assertEquals(huella("""
				SELECT c.codigo, c.nombre, c.edad_min, c.edad_max, c.orden FROM categoria c
				LEFT JOIN disciplina d ON d.id = c.disciplina_id
				WHERE c.organizacion_id = ? AND (d.codigo = 'TAEKWONDO' OR c.disciplina_id IS NULL) ORDER BY c.nombre""",
				ORG1), huella("""
						SELECT c.codigo, c.nombre, c.edad_min, c.edad_max, c.orden FROM categoria c
						WHERE c.organizacion_id = ? ORDER BY c.nombre""", org));

		// Tarifas desactivadas, con los importes de la plantilla como referencia.
		assertEquals(huella("""
				SELECT t.codigo, t.nombre, t.importe, t.importe_adulto, t.importe_menor, t.orden FROM tarifa t
				LEFT JOIN disciplina d ON d.id = t.disciplina_id
				WHERE t.organizacion_id = ? AND (d.codigo = 'TAEKWONDO' OR t.disciplina_id IS NULL) ORDER BY t.codigo""",
				ORG1), huella("""
						SELECT t.codigo, t.nombre, t.importe, t.importe_adulto, t.importe_menor, t.orden FROM tarifa t
						WHERE t.organizacion_id = ? ORDER BY t.codigo""", org));
		assertTrue(contar("SELECT COUNT(*) FROM tarifa WHERE organizacion_id = ?", org) > 0);
		assertEquals(0, contar("SELECT COUNT(*) FROM tarifa WHERE organizacion_id = ? AND activa = TRUE", org));
		assertEquals(0, contar("""
				SELECT COUNT(*) FROM tarifa t JOIN disciplina d ON d.id = t.disciplina_id
				WHERE t.organizacion_id = ? AND d.organizacion_id <> t.organizacion_id""", org));

		// Productos: los de taekwondo, los comunes y los compartidos, con precio, tipo y código; ninguno de kickboxing.
		assertEquals(huella("""
				SELECT p.concepto, p.precio, p.codigo, p.tipo, d.codigo AS disciplina, g.codigo AS grado,
				       p.vendible_profesor
				FROM producto p LEFT JOIN disciplina d ON d.id = p.disciplina_id LEFT JOIN grado g ON g.id = p.grado_id
				WHERE p.organizacion_id = ?
				  AND (d.codigo = 'TAEKWONDO' OR (p.disciplina_id IS NULL AND (p.grado_id IS NULL OR g.codigo IN (
				       SELECT g2.codigo FROM grado g2 JOIN disciplina d2 ON d2.sistema_grados_id = g2.sistema_grados_id
				       WHERE d2.organizacion_id = 1 AND d2.codigo = 'TAEKWONDO'))))
				ORDER BY p.concepto""", ORG1), huella("""
						SELECT p.concepto, p.precio, p.codigo, p.tipo, d.codigo AS disciplina, g.codigo AS grado,
						       p.vendible_profesor
						FROM producto p LEFT JOIN disciplina d ON d.id = p.disciplina_id
						LEFT JOIN grado g ON g.id = p.grado_id
						WHERE p.organizacion_id = ? ORDER BY p.concepto""", org));
		assertEquals(0, contar("""
				SELECT COUNT(*) FROM producto p LEFT JOIN disciplina d ON d.id = p.disciplina_id
				LEFT JOIN grado g ON g.id = p.grado_id
				WHERE p.organizacion_id = ? AND (d.organizacion_id <> p.organizacion_id
				                                 OR g.organizacion_id <> p.organizacion_id)""", org));
		assertEquals(0, contar("SELECT COUNT(*) FROM producto WHERE organizacion_id = ? AND concepto LIKE '%KICKBOXING%'",
				org));
		// Enlaces remapeados a las filas nuevas: examen y recompensa del grado; competidor de la disciplina.
		Map<String, Object> amarillo = jdbc.queryForMap("""
				SELECT pe.concepto AS examen, pe.organizacion_id AS org_examen, pr.concepto AS recompensa,
				       pe.grado_id = g.id AS mismo_grado
				FROM grado g JOIN producto pe ON pe.id = g.producto_examen_id
				JOIN producto pr ON pr.id = g.producto_recompensa_id
				WHERE g.organizacion_id = ? AND g.codigo = 'AMARILLO'""", org);
		assertEquals("IT F42 EXAMEN AMARILLO " + marca, amarillo.get("examen"));
		assertEquals(org, ((Number) amarillo.get("org_examen")).longValue());
		assertEquals("IT F42 RECOMPENSA AMARILLO " + marca, amarillo.get("recompensa"));
		assertEquals(1L, ((Number) amarillo.get("mismo_grado")).longValue());
		Long competidorPlantilla = jdbc.queryForObject(
				"SELECT producto_competidor_id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'TAEKWONDO'",
				Long.class);
		if (competidorPlantilla != null) {
			assertEquals(jdbc.queryForObject("SELECT codigo FROM producto WHERE id = ?", String.class, competidorPlantilla),
					jdbc.queryForObject("""
							SELECT p.codigo FROM disciplina d JOIN producto p ON p.id = d.producto_competidor_id
							WHERE d.organizacion_id = ? AND p.organizacion_id = d.organizacion_id""", String.class, org));
		}

		// Licencia de la disciplina y equivalente adulto del grado: a las filas NUEVAS (enlaces puestos en preparar()).
		Map<String, Object> licencia = jdbc.queryForMap("""
				SELECT p.concepto, p.organizacion_id, p.disciplina_id = d.id AS suya
				FROM disciplina d JOIN producto p ON p.id = d.producto_licencia_id
				WHERE d.organizacion_id = ? AND d.codigo = 'TAEKWONDO'""", org);
		assertEquals("IT F42 LICENCIA " + marca, licencia.get("concepto"));
		assertEquals(org, ((Number) licencia.get("organizacion_id")).longValue());
		assertEquals(1L, ((Number) licencia.get("suya")).longValue());
		Map<String, Object> equivalente = jdbc.queryForMap("""
				SELECT e.codigo, e.organizacion_id, e.sistema_grados_id = g.sistema_grados_id AS mismo_sistema
				FROM grado g JOIN grado e ON e.id = g.equivalente_adulto_id
				WHERE g.organizacion_id = ? AND g.codigo = 'AMARILLO'""", org);
		assertEquals(jdbc.queryForObject("SELECT codigo FROM grado WHERE id = ?", String.class, gradoTkdEquivalente),
				equivalente.get("codigo"));
		assertEquals(org, ((Number) equivalente.get("organizacion_id")).longValue());
		assertEquals(1L, ((Number) equivalente.get("mismo_sistema")).longValue());

		// Nada de lo que no se copia.
		for (String tabla : List.of("plantilla_preinscripcion", "tarifa_sede", "grupo", "turno", "alumno",
				"configuracion_organizacion")) {
			assertEquals(0, contar("SELECT COUNT(*) FROM " + tabla + " WHERE organizacion_id = ?", org), tabla);
		}

		// Registro en la escuela NUEVA, sin datos personales; ninguna fila del filtro HTTP para el 2xx.
		List<String> resumenes = jdbc.queryForList(
				"SELECT resumen FROM auditoria_evento WHERE organizacion_id = ? ORDER BY id", String.class, org);
		assertTrue(resumenes.stream().anyMatch(r -> r.startsWith("PLATAFORMA ESCUELA_CREADA organizacion=#" + org)),
				resumenes.toString());
		assertTrue(resumenes.contains("MIEMBROS MIEMBRO_INVITADO usuario=#" + usuario + " rol=PROPIETARIO"),
				resumenes.toString());
		for (String r : resumenes) {
			assertFalse(r.contains("@") || r.contains("Ana") || r.contains(marca), r);
		}
		assertEquals(0, contar("SELECT COUNT(*) FROM auditoria_evento WHERE endpoint LIKE ? AND estado_http = 201",
				RUTA + "%"));

		assertEquals(antes, huellaOrg1(), "La plantilla (organización 1) no cambia");
	}

	@Test
	void alta_variasDisciplinas_productosCompartidosUnaVez_yElSlugDeUnNombreParecidoLlevaSufijo() throws Exception {
		String base = "Escuela IT Varias " + marca;
		long primera = leer(alta(cuerpo(base, "b1-" + marca + "@example.test", "Sede", "Eva", "Ficticia",
				"it-f42-varias-" + marca + "@example.test", "KICKBOXING", "TAEKWONDO", "PILATES")), cookieSuperadmin, 201)
				.get("id").asLong();
		// Distinto nombre, mismo slug: sufijo -2.
		long segunda = leer(alta(cuerpo(base + " !", "b2-" + marca + "@example.test", "Sede", "Eva", "Ficticia",
				"it-f42-varias-2-" + marca + "@example.test", "PILATES")), cookieSuperadmin, 201).get("id").asLong();
		String slug = jdbc.queryForObject("SELECT slug FROM organizacion WHERE id = ?", String.class, primera);
		assertEquals("escuela-it-varias-" + marca, slug);
		assertEquals(slug + "-2", jdbc.queryForObject("SELECT slug FROM organizacion WHERE id = ?", String.class, segunda));

		assertEquals(List.of("TAEKWONDO", "KICKBOXING", "PILATES"), jdbc.queryForList(
				"SELECT codigo FROM disciplina WHERE organizacion_id = ? ORDER BY orden, id", String.class, primera));
		assertEquals(2, contar("SELECT COUNT(*) FROM sistema_grados WHERE organizacion_id = ?", primera));
		// Compartido: una sola copia, con el grado de taekwondo (como en la plantilla), y los dos sistemas lo enlazan.
		assertEquals(1, contar("SELECT COUNT(*) FROM producto WHERE organizacion_id = ? AND concepto = ?", primera,
				"IT F42 EXAMEN AMARILLO " + marca));
		assertEquals(2, contar("""
				SELECT COUNT(*) FROM grado g JOIN producto p ON p.id = g.producto_examen_id
				WHERE g.organizacion_id = ? AND p.concepto = ?""", primera, "IT F42 EXAMEN AMARILLO " + marca));
		assertEquals("TAEKWONDO", jdbc.queryForObject("""
				SELECT d.codigo FROM producto p JOIN grado g ON g.id = p.grado_id
				JOIN disciplina d ON d.sistema_grados_id = g.sistema_grados_id
				WHERE p.organizacion_id = ? AND p.concepto = ?""", String.class, primera, "IT F42 EXAMEN AMARILLO " + marca));

		// Solo pilates: sin grados ni productos de examen; sí lo común.
		assertEquals(0, contar("SELECT COUNT(*) FROM sistema_grados WHERE organizacion_id = ?", segunda));
		assertEquals(0, contar("SELECT COUNT(*) FROM producto WHERE organizacion_id = ? AND grado_id IS NOT NULL",
				segunda));
		assertEquals(contar("""
				SELECT COUNT(*) FROM producto WHERE organizacion_id = 1 AND disciplina_id IS NULL AND grado_id IS NULL"""),
				contar("SELECT COUNT(*) FROM producto WHERE organizacion_id = ?", segunda));
	}

	@Test
	void alta_soloKickboxing_elCompartidoApuntaAlGradoDeKickboxing() throws Exception {
		long org = leer(alta(cuerpo("Escuela IT Kick " + marca, "k-" + marca + "@example.test", "Sede", "Eva", "Ficticia",
				"it-f42-kick-" + marca + "@example.test", "KICKBOXING")), cookieSuperadmin, 201).get("id").asLong();
		Map<String, Object> fila = jdbc.queryForMap("""
				SELECT g.codigo, d.codigo AS disciplina, g.organizacion_id FROM producto p JOIN grado g ON g.id = p.grado_id
				JOIN disciplina d ON d.sistema_grados_id = g.sistema_grados_id
				WHERE p.organizacion_id = ? AND p.concepto = ?""", org, "IT F42 EXAMEN AMARILLO " + marca);
		assertEquals("AMARILLO", fila.get("codigo"));
		assertEquals("KICKBOXING", fila.get("disciplina"));
		assertEquals(org, ((Number) fila.get("organizacion_id")).longValue());
		assertEquals(0, contar("SELECT COUNT(*) FROM tarifa WHERE organizacion_id = ? AND codigo = 'ADULTO'", org));
	}

	@Test
	void alta_conCuentaExistente_soloMembresiaPropietario_sinInvitacion() throws Exception {
		Usuario existente = crearCuenta("it-f42-existente-" + marca + "@example.test");
		JsonNode creada = leer(alta(cuerpo("Escuela IT Existente " + marca, "e-" + marca + "@example.test", "Sede",
				null, null, existente.getEmail().toUpperCase(), "PILATES")), cookieSuperadmin, 201);
		long org = creada.get("id").asLong();
		assertTrue(creada.get("invitacionEnviadaA").isNull(), creada.toString());
		assertEquals(List.of("PROPIETARIO"), jdbc.queryForList(
				"SELECT rol FROM membresia WHERE usuario_id = ? AND organizacion_id = ? AND activa = TRUE", String.class,
				existente.getId(), org));
		assertNotNull(jdbc.queryForObject("SELECT contrasena FROM usuario WHERE id = ?", String.class, existente.getId()));
		verify(email, never()).sendEmailConImagen(any(), any(), any(), any(), any(), any(), any());
		assertTrue(jdbc.queryForList("SELECT resumen FROM auditoria_evento WHERE organizacion_id = ?", String.class, org)
				.contains("MIEMBROS ROL_DADO usuario=#" + existente.getId() + " rol=PROPIETARIO"));
	}

	// ------------------------------------------------------------------ errores

	@Test
	void soloPlataforma_403_y_sinSesion_401() throws Exception {
		long antes = contar("SELECT COUNT(*) FROM organizacion");
		String cuerpo = cuerpo("Escuela IT Prohibida " + marca, "p@example.test", "Sede", "Eva", "Ficticia",
				"it-f42-prohibida-" + marca + "@example.test", "TAEKWONDO");
		leer(alta(cuerpo), new Cookie("jwt", tPropietario), 403);
		leer(get(RUTA_PLANTILLA), new Cookie("jwt", tPropietario), 403);
		assertEquals(401, mvc.perform(alta(cuerpo)).andReturn().getResponse().getStatus());
		assertEquals(antes, contar("SELECT COUNT(*) FROM organizacion"));
	}

	@Test
	void nombreRepetido_409_sinMayusculasNiEspacios() throws Exception {
		String nombre = "Escuela IT Repetida " + marca;
		leer(alta(cuerpo(nombre, "r1-" + marca + "@example.test", "Sede", "Eva", "Ficticia",
				"it-f42-rep1-" + marca + "@example.test", "PILATES")), cookieSuperadmin, 201);
		long antes = contar("SELECT COUNT(*) FROM organizacion");
		JsonNode error = leer(alta(cuerpo("  " + nombre.toUpperCase() + " ", "r2-" + marca + "@example.test", "Sede",
				"Eva", "Ficticia", "it-f42-rep2-" + marca + "@example.test", "PILATES")), cookieSuperadmin, 409);
		assertEquals("Ya hay una escuela con ese nombre.", error.get("mensaje").asText());
		assertEquals(antes, contar("SELECT COUNT(*) FROM organizacion"));
		assertEquals(0, contar("SELECT COUNT(*) FROM usuario WHERE email = ?", "it-f42-rep2-" + marca + "@example.test"));
	}

	@Test
	void nombreRepetido_409_sinTildesNiEnes() throws Exception {
		String nombre = "Club IT Ñandú Pérez " + marca;
		leer(alta(cuerpo(nombre, "t1-" + marca + "@example.test", "Sede", "Eva", "Ficticia",
				"it-f42-tilde1-" + marca + "@example.test", "PILATES")), cookieSuperadmin, 201);
		long antes = contar("SELECT COUNT(*) FROM organizacion");
		for (String parecido : List.of("Club IT Nandu Perez " + marca, "CLUB IT ÑANDU PÉREZ " + marca,
				"club it nandú perez " + marca)) {
			JsonNode error = leer(alta(cuerpo(parecido, "t2-" + marca + "@example.test", "Sede", "Eva", "Ficticia",
					"it-f42-tilde2-" + marca + "@example.test", "PILATES")), cookieSuperadmin, 409);
			assertEquals("Ya hay una escuela con ese nombre.", error.get("mensaje").asText(), parecido);
		}
		assertEquals(antes, contar("SELECT COUNT(*) FROM organizacion"));
		assertEquals(0, contar("SELECT COUNT(*) FROM usuario WHERE email = ?", "it-f42-tilde2-" + marca + "@example.test"));
		verify(email, times(1)).sendEmailConImagen(any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	void dosAltasALaVezConElMismoNombre_unaSola() throws Exception {
		String nombre = "Escuela IT Carrera " + marca;
		ExecutorService hilos = Executors.newFixedThreadPool(2);
		try {
			List<Future<MvcResult>> resultados = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				String cuerpo = cuerpo(nombre, "c" + i + "-" + marca + "@example.test", "Sede", "Eva", "Ficticia",
						"it-f42-carrera-" + i + "-" + marca + "@example.test", "TAEKWONDO", "KICKBOXING");
				Callable<MvcResult> peticion = () -> mvc.perform(alta(cuerpo).cookie(cookieSuperadmin)).andReturn();
				resultados.add(hilos.submit(peticion));
			}
			List<Integer> estados = new ArrayList<>();
			for (Future<MvcResult> r : resultados) {
				estados.add(r.get().getResponse().getStatus());
			}
			estados.sort(null);
			assertEquals(List.of(201, 409), estados);
		} finally {
			hilos.shutdownNow();
		}
		assertEquals(1, contar("SELECT COUNT(*) FROM organizacion WHERE nombre = ?", nombre));
		assertEquals(1, contar("SELECT COUNT(*) FROM usuario WHERE email LIKE ?", "it-f42-carrera-%-" + marca + "%"));
	}

	@Test
	void datosQueNoValen_400_yNadaCambia() throws Exception {
		long antes = contar("SELECT COUNT(*) FROM organizacion");
		String ok = "it-f42-400-" + marca + "@example.test";
		Map<String, String> casos = new java.util.LinkedHashMap<>();
		casos.put(cuerpo(" ", "a@example.test", "Sede", "Eva", "F", ok, "PILATES"), "El nombre de la escuela es obligatorio.");
		casos.put(cuerpo("x".repeat(151), "a@example.test", "Sede", "Eva", "F", ok, "PILATES"),
				"El nombre de la escuela no puede pasar de 150 caracteres.");
		casos.put(cuerpo("Escuela IT 400 " + marca, null, "Sede", "Eva", "F", ok, "PILATES"),
				"El email de la escuela es obligatorio.");
		casos.put(cuerpo("Escuela IT 400 " + marca, "no-es-email", "Sede", "Eva", "F", ok, "PILATES"),
				"El email de la escuela no es válido.");
		casos.put(cuerpo("Escuela IT 400 " + marca, "a@example.test", "", "Eva", "F", ok, "PILATES"),
				"El nombre de la sede es obligatorio.");
		casos.put(cuerpo("Escuela IT 400 " + marca, "a@example.test", "s".repeat(101), "Eva", "F", ok, "PILATES"),
				"El nombre de la sede no puede pasar de 100 caracteres.");
		casos.put(cuerpo("Escuela IT 400 " + marca, "a@example.test", "Sede", "Eva", "F", " ", "PILATES"),
				"El email del propietario es obligatorio.");
		casos.put(cuerpo("Escuela IT 400 " + marca, "a@example.test", "Sede", "Eva", "F", "no-es-email", "PILATES"),
				"El email del propietario no es válido.");
		casos.put(cuerpo("Escuela IT 400 " + marca, "a@example.test", "Sede", null, null, ok, "PILATES"),
				"Indica el nombre y los apellidos.");
		casos.put(cuerpo("Escuela IT 400 " + marca, "a@example.test", "Sede", "Eva", "F", ok),
				"Elige al menos una disciplina.");
		casos.put(cuerpo("Escuela IT 400 " + marca, "a@example.test", "Sede", "Eva", "F", ok, "PILATES", "PILATES"),
				"Hay disciplinas repetidas.");
		casos.put(cuerpo("Escuela IT 400 " + marca, "a@example.test", "Sede", "Eva", "F", ok, "AJEDREZ"),
				"La disciplina no está en la plantilla.");
		casos.put("{}", "El nombre de la escuela es obligatorio.");
		for (Map.Entry<String, String> caso : casos.entrySet()) {
			JsonNode error = leer(alta(caso.getKey()), cookieSuperadmin, 400);
			assertEquals(caso.getValue(), error.get("mensaje").asText(), caso.getKey());
		}
		assertEquals(antes, contar("SELECT COUNT(*) FROM organizacion"));
		assertEquals(0, contar("SELECT COUNT(*) FROM usuario WHERE email = ?", ok));

		// El 4xx lo apunta el filtro HTTP, con los datos personales redactados.
		List<String> cuerpos = jdbc.queryForList("""
				SELECT payload_json FROM auditoria_evento WHERE endpoint = ? AND estado_http = 400
				ORDER BY id DESC LIMIT 3""", String.class, RUTA);
		assertFalse(cuerpos.isEmpty());
		for (String guardado : cuerpos) {
			assertFalse(guardado != null && (guardado.contains("@example.test") || guardado.contains(marca)), guardado);
		}
	}

	@Test
	void cuerpoIlegible_400ConMensajeFijo_sinDetallesDeJackson(CapturedOutput salida) throws Exception {
		long antes = contar("SELECT COUNT(*) FROM organizacion");
		String rastro = "ilegible-" + marca;
		List<String> cuerpos = List.of(
				// Forma equivocada: la sede es un objeto.
				"{\"nombre\": \"Escuela IT " + rastro + "\", \"sede\": [], \"propietario\": {\"email\": \"" + rastro
						+ "@example.test\"}}",
				// JSON malformado.
				"{\"nombre\": \"Escuela IT " + rastro + "\", \"propietario\": {\"email\": \"" + rastro + "@example.test");
		for (String cuerpo : cuerpos) {
			MvcResult r = mvc.perform(alta(cuerpo).cookie(cookieSuperadmin)).andReturn();
			assertEquals(400, r.getResponse().getStatus(), cuerpo);
			JsonNode error = json.readTree(r.getResponse().getContentAsString());
			assertEquals("El cuerpo de la petición no es válido.", error.get("mensaje").asText(), cuerpo);
			assertEquals(1, error.size(), error.toString());
		}
		assertEquals(antes, contar("SELECT COUNT(*) FROM organizacion"));
		// Ni el cuerpo ni el detalle de Jackson en el log.
		assertFalse(salida.getAll().contains(rastro), "El log cita el cuerpo");
		assertFalse(salida.getAll().contains("JSON parse error"), "El log trae el error de Jackson");
	}

	// ------------------------------------------------------------------ apoyo

	private String cuerpo(String nombre, String emailContacto, String sede, String nombrePropietario,
			String apellidos, String emailPropietario, String... disciplinas) {
		ObjectNode raiz = json.createObjectNode();
		raiz.put("nombre", nombre);
		raiz.put("emailContacto", emailContacto);
		raiz.putObject("sede").put("nombre", sede);
		ObjectNode propietario = raiz.putObject("propietario");
		propietario.put("nombre", nombrePropietario);
		propietario.put("apellidos", apellidos);
		propietario.put("email", emailPropietario);
		ArrayNode lista = raiz.putArray("disciplinas");
		for (String d : disciplinas) {
			lista.add(d);
		}
		return raiz.toString();
	}

	private MockHttpServletRequestBuilder alta(String cuerpo) {
		return post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private JsonNode leer(MockHttpServletRequestBuilder peticion, Cookie cookie, int estado) throws Exception {
		MvcResult resultado = mvc.perform(peticion.cookie(cookie)).andReturn();
		assertEquals(estado, resultado.getResponse().getStatus(), resultado.getResponse().getContentAsString());
		String cuerpo = resultado.getResponse().getContentAsString();
		return cuerpo.isEmpty() ? json.nullNode() : json.readTree(cuerpo);
	}

	private long contar(String sql, Object... parametros) {
		Long n = jdbc.queryForObject(sql, Long.class, parametros);
		return n == null ? 0 : n;
	}

	/** Las filas de la consulta como texto, para comparar dos conjuntos columna a columna. */
	private String huella(String sql, Object... parametros) {
		StringBuilder sb = new StringBuilder();
		for (Map<String, Object> fila : jdbc.queryForList(sql, parametros)) {
			fila.forEach((k, v) -> sb.append(k).append('=').append(v instanceof BigDecimal b ? b.stripTrailingZeros() : v)
					.append(';'));
			sb.append('\n');
		}
		return sb.toString();
	}

	/** El catálogo entero de la organización 1, fila a fila (ids incluidos). */
	String huellaOrg1() {
		return huella("SELECT id, codigo, nombre, sistema_grados_id, activa, orden, producto_licencia_id, "
				+ "producto_competidor_id FROM disciplina WHERE organizacion_id = 1 ORDER BY id")
				+ huella("SELECT id, nombre, plantilla_origen FROM sistema_grados WHERE organizacion_id = 1 ORDER BY id")
				+ huella("SELECT id, codigo, nombre, producto_examen_id, producto_recompensa_id FROM grado "
						+ "WHERE organizacion_id = 1 ORDER BY id")
				+ huella("SELECT id, codigo, nombre, disciplina_id FROM categoria WHERE organizacion_id = 1 ORDER BY id")
				+ huella("SELECT id, codigo, nombre, importe, importe_adulto, importe_menor, activa FROM tarifa "
						+ "WHERE organizacion_id = 1 ORDER BY id")
				+ huella("SELECT id, concepto, precio, codigo, tipo, disciplina_id, grado_id FROM producto "
						+ "WHERE organizacion_id = 1 ORDER BY id")
				+ huella("SELECT id, nombre, activa FROM sede WHERE organizacion_id = 1 ORDER BY id");
	}

	private long gradoDe(String disciplina, String codigo) {
		return jdbc.queryForObject("""
				SELECT g.id FROM grado g JOIN disciplina d ON d.sistema_grados_id = g.sistema_grados_id
				WHERE d.organizacion_id = 1 AND d.codigo = ? AND g.codigo = ?""", Long.class, disciplina, codigo);
	}

	private long producto(String concepto, String tipo, long grado, double precio) {
		jdbc.update("""
				INSERT INTO producto (concepto, precio, organizacion_id, tipo, grado_id, vendible_profesor)
				VALUES (?, ?, 1, ?, ?, FALSE)""", concepto, precio, tipo, grado);
		return jdbc.queryForObject("SELECT id FROM producto WHERE organizacion_id = 1 AND concepto = ?", Long.class,
				concepto);
	}

	private Usuario crearCuenta(String correo) {
		return TenantContext.calcularCon(ORG1, () -> {
			Usuario usuario = new Usuario();
			usuario.setNombre("Cuenta");
			usuario.setApellidos("Ficticia");
			usuario.setEmail(correo);
			usuario.setContrasena(encoder.encode(CLAVE));
			usuario.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(usuario);
		});
	}

	private String login(String correo) throws Exception {
		MvcResult resultado = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}")).andReturn();
		assertEquals(200, resultado.getResponse().getStatus());
		return json.readTree(resultado.getResponse().getContentAsString()).get("token").asText();
	}
}
