package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.AsignacionProfesorService;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza E7-4 contra MySQL (fase3-e7-plan.md §5; decisión 208, D7-19 a D7-21): propietario y gestor asignan y quitan
 * profesores de un grupo en {@code /api/grupos/{grupoId}/profesores}.
 * <ul>
 * <li>{@code asignacion_profesor} y {@code membresia} no son {@code @TenantId}: un grupo de otra escuela da 404 y nada
 * de una escuela se ve ni se toca desde la otra.</li>
 * <li>La cuenta se busca por email (sin cuenta, 404 con el mensaje del diseño); si no tiene membresía PROFESOR en la
 * escuela se le crea activa (desactivada: 409, §11.4); las demás membresías no se tocan; quitar no la borra.</li>
 * <li>El alcance del profesor se recalcula en cada petición: la asignación abre su grupo en la siguiente y quitarla lo
 * cierra en la siguiente.</li>
 * <li>Solo P y G: responsable de sede, profesor y familia, 403.</li>
 * </ul>
 * Dos escuelas sintéticas ({@link EscuelaConSedesDePrueba}) con su propietario; cada test usa cuentas nuevas, así que el
 * orden de los tests no importa. Datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AsignacionProfesoresIntegrationTest extends MySqlIntegrationTest {

	private static final String RESUMEN_SOLO_IDS = "^PROFESOR [A-Z_]+( [A-Za-z]+=(#\\d+|[A-Z_]+))+$";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	long propietario, propietarioOtra;
	String tPropietario, tGestor, tPropietarioOtra, tResp;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e7-4");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e7-4-otra");
		propietario = e.cuenta(usuarios, "propietario");
		membresia(e, propietario, "PROPIETARIO");
		propietarioOtra = otra.cuenta(usuarios, "propietario");
		membresia(otra, propietarioOtra, "PROPIETARIO");
		tPropietario = e.token(jwtService, usuarios, propietario);
		tGestor = e.token(jwtService, usuarios, e.gestor);
		tPropietarioOtra = otra.token(jwtService, usuarios, propietarioOtra);
		tResp = e.token(jwtService, usuarios, e.respA);
	}

	@AfterEach
	void sinFugas() {
		assertFalse(TenantContext.get() != null, "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ aislamiento

	@Test
	void asignarEnOtraEscuela_404() throws Exception {
		long cuenta = otra.cuenta(usuarios, nombre("ajena"));
		membresia(otra, cuenta, "PROFESOR");
		asignar(otra, cuenta, otra.gA, "TITULAR");

		for (String token : List.of(tPropietario, tGestor)) {
			assertEquals(404, estado(get(ruta(otra.gA)), token));
			assertEquals(404, estado(alta(otra.gA, email(cuenta), "AYUDANTE"), token));
			assertEquals(404, estado(delete(ruta(otra.gA) + "/" + cuenta), token));
			// Un grupo que no existe en ninguna escuela, igual.
			assertEquals(404, estado(get(ruta(Long.MAX_VALUE)), token));
		}
		// Nada cambió en la otra escuela ni se creó nada en esta.
		assertEquals("TITULAR", rolAsignado(cuenta, otra.gA));
		assertEquals(0L, membresias(cuenta, e.org));
		assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM asignacion_profesor WHERE usuario_id = ?", Long.class,
				cuenta));
	}

	@Test
	void asignacionesNoSeVenEntreEscuelas() throws Exception {
		long cuenta = e.cuenta(usuarios, nombre("dos"));
		leer(alta(e.gA, email(cuenta), "TITULAR"), tPropietario, 200);
		assertEquals(0L, membresias(cuenta, otra.org), "asignar en una escuela no crea nada en la otra");
		leer(alta(otra.gA, email(cuenta), "AYUDANTE"), tPropietarioOtra, 200);

		// Cada GET devuelve exactamente las asignaciones de su escuela (otros tests de la clase comparten los grupos).
		JsonNode deE = leer(get(ruta(e.gA)), tPropietario, 200);
		assertEquals("TITULAR", fila(deE, cuenta).get("rol").asText());
		assertEquals(asignadosEnBD(e.gA, e.org), idsDe(deE, "usuarioId"));
		JsonNode deOtra = leer(get(ruta(otra.gA)), tPropietarioOtra, 200);
		assertEquals("AYUDANTE", fila(deOtra, cuenta).get("rol").asText());
		assertEquals(asignadosEnBD(otra.gA, otra.org), idsDe(deOtra, "usuarioId"));
		assertEquals(404, estado(get(ruta(otra.gA)), tPropietario));
		assertEquals(404, estado(get(ruta(e.gA)), tPropietarioOtra));

		// Quitar en una no toca la otra: ni la asignación ni la membresía ni el acceso de su sesión allí.
		assertEquals(204, estado(delete(ruta(e.gA) + "/" + cuenta), tPropietario));
		assertEquals(null, rolAsignado(cuenta, e.gA));
		assertEquals("AYUDANTE", rolAsignado(cuenta, otra.gA));
		assertEquals(1L, membresiasActivas(cuenta, otra.org, "PROFESOR"));
		assertEquals(Set.of(otra.gA),
				ids(leer(get("/api/profesor/grupos"), otra.token(jwtService, usuarios, cuenta), 200)));
		// Y quitarla desde la escuela equivocada da 404 y no la toca.
		assertEquals(404, estado(delete(ruta(otra.gA) + "/" + cuenta), tPropietario));
		assertEquals("AYUDANTE", rolAsignado(cuenta, otra.gA));
	}

	// ------------------------------------------------------------------ alta

	@Test
	void emailSinCuenta_404_conMensaje() throws Exception {
		String email = "it-e7-4-nadie-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
		MvcResult r = peticion(alta(e.gA, email, "TITULAR"), tPropietario);
		assertEquals(404, r.getResponse().getStatus());
		assertEquals(AsignacionProfesorService.EMAIL_SIN_CUENTA, mensaje(r));
		assertEquals("No hay ninguna cuenta con ese email. Que se registre primero.",
				AsignacionProfesorService.EMAIL_SIN_CUENTA);
	}

	@Test
	void asignarCreaMembresiaProfesor() throws Exception {
		long cuenta = e.cuenta(usuarios, nombre("nueva"));
		String token = e.token(jwtService, usuarios, cuenta);
		// Sin membresía en la escuela, su sesión no entra.
		assertEquals(401, estado(get("/api/profesor/grupos"), token));

		JsonNode profesor = leer(alta(e.gA, email(cuenta), "TITULAR"), tPropietario, 200);
		assertEquals(Set.of("usuarioId", "nombre", "apellidos", "email", "rol"), campos(profesor));
		assertEquals(cuenta, profesor.get("usuarioId").asLong());
		assertEquals(email(cuenta), profesor.get("email").asText());
		assertEquals("TITULAR", profesor.get("rol").asText());
		assertEquals(1L, membresiasActivas(cuenta, e.org, "PROFESOR"));
		assertEquals(1L, membresias(cuenta, e.org));
		assertEquals("TITULAR", rolAsignado(cuenta, e.gA));
		assertEquals(e.org, jdbc.queryForObject(
				"SELECT organizacion_id FROM asignacion_profesor WHERE usuario_id = ? AND grupo_id = ?", Long.class,
				cuenta, e.gA));

		// En la siguiente petición, su sesión solo-profesor entra y ve el grupo; la gestión sigue cerrada.
		assertEquals(Set.of(e.gA), ids(leer(get("/api/profesor/grupos"), token, 200)));
		assertEquals(200, estado(get("/api/profesor/grupos/" + e.gA + "/alumnos"), token));
		assertEquals(403, estado(get(ruta(e.gA)), token));
		assertEquals(403, estado(get("/api/grupos/" + e.gA), token));
		// Los grupos los comparten otros tests de la clase: la lista es la de la BD de esta escuela y la incluye.
		Set<Long> lista = idsDe(leer(get(ruta(e.gA)), tPropietario, 200), "usuarioId");
		assertTrue(lista.contains(cuenta), lista.toString());
		assertEquals(asignadosEnBD(e.gA, e.org), lista);
	}

	@Test
	void asignarDosVecesCambiaRol() throws Exception {
		long cuenta = e.cuenta(usuarios, nombre("rol"));
		leer(alta(e.gA, email(cuenta), "TITULAR"), tPropietario, 200);
		// El email sin distinguir mayúsculas ni espacios.
		JsonNode profesor = leer(alta(e.gA, "  " + email(cuenta).toUpperCase() + " ", "AYUDANTE"), tPropietario, 200);
		assertEquals("AYUDANTE", profesor.get("rol").asText());
		assertEquals("AYUDANTE", rolAsignado(cuenta, e.gA));
		assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM asignacion_profesor WHERE usuario_id = ?", Long.class,
				cuenta));
		assertEquals(1L, membresias(cuenta, e.org));
		JsonNode lista = leer(get(ruta(e.gA)), tPropietario, 200);
		assertEquals("AYUDANTE", fila(lista, cuenta).get("rol").asText());
		// Mismo rol otra vez: no cambia nada.
		leer(alta(e.gA, email(cuenta), "AYUDANTE"), tPropietario, 200);
		assertEquals("AYUDANTE", rolAsignado(cuenta, e.gA));
	}

	@Test
	void asignarConMembresiaProfesorDesactivada_409() throws Exception {
		long cuenta = e.cuenta(usuarios, nombre("desactivada"));
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol, activa) VALUES (?, ?, 'PROFESOR', FALSE)",
				cuenta, e.org);
		MvcResult r = peticion(alta(e.gA, email(cuenta), "TITULAR"), tPropietario);
		assertEquals(409, r.getResponse().getStatus());
		assertEquals("La cuenta tiene el rol de profesor desactivado en esta escuela", mensaje(r));
		assertEquals(AsignacionProfesorService.PROFESOR_DESACTIVADO, mensaje(r));
		assertEquals(null, rolAsignado(cuenta, e.gA), "sin asignación");
		assertEquals(0L, membresiasActivas(cuenta, e.org, "PROFESOR"), "no la reactiva (E8)");
		assertEquals(1L, membresias(cuenta, e.org));
	}

	@Test
	void asignarNoTocaOtrasMembresias() throws Exception {
		long cuenta = e.cuentaFamilia(usuarios, nombre("familia"));
		membresia(otra, cuenta, "FAMILIA");
		leer(alta(e.gA, email(cuenta), "AYUDANTE"), tPropietario, 200);
		assertEquals(1L, membresiasActivas(cuenta, e.org, "FAMILIA"));
		assertEquals(1L, membresiasActivas(cuenta, e.org, "PROFESOR"));
		assertEquals(2L, membresias(cuenta, e.org), "F+Pr");
		assertEquals(1L, membresias(cuenta, otra.org), "la otra escuela, igual");
		assertEquals(1L, membresiasActivas(cuenta, otra.org, "FAMILIA"));
		// Su sesión F+Pr usa las rutas del profesor con ese grupo.
		assertEquals(Set.of(e.gA), ids(leer(get("/api/profesor/grupos"), e.token(jwtService, usuarios, cuenta), 200)));
	}

	@Test
	void dosAltasALaVezMismaCuenta_sin500() throws Exception {
		long cuenta = e.cuenta(usuarios, nombre("carrera"));
		String email = email(cuenta);
		List<Integer> estados = enParalelo(() -> estado(alta(e.gA, email, "TITULAR"), tPropietario),
				() -> estado(alta(e.gB, email, "AYUDANTE"), tGestor));
		assertEquals(List.of(200, 200), estados);
		assertEquals(1L, membresias(cuenta, e.org), "una sola membresía PROFESOR");
		assertEquals("TITULAR", rolAsignado(cuenta, e.gA));
		assertEquals("AYUDANTE", rolAsignado(cuenta, e.gB));

		// Y en el mismo grupo: una sola asignación.
		long otraCuenta = e.cuenta(usuarios, nombre("carrera2"));
		String otroEmail = email(otraCuenta);
		List<Integer> mismos = enParalelo(() -> estado(alta(e.gA2, otroEmail, "TITULAR"), tPropietario),
				() -> estado(alta(e.gA2, otroEmail, "TITULAR"), tGestor));
		assertEquals(List.of(200, 200), mismos);
		assertEquals(1L, membresias(otraCuenta, e.org));
		assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM asignacion_profesor WHERE usuario_id = ?", Long.class,
				otraCuenta));
	}

	@Test
	void cuerposInvalidos_400() throws Exception {
		long cuenta = e.cuenta(usuarios, nombre("cuerpo"));
		assertEquals(400, estado(alta(e.gA, "", "TITULAR"), tPropietario));
		assertEquals(400, estado(alta(e.gA, null, "TITULAR"), tPropietario));
		assertEquals(400, estado(alta(e.gA, email(cuenta), "JEFE"), tPropietario));
		assertEquals(400, estado(alta(e.gA, email(cuenta), null), tPropietario));
		assertEquals(400, estado(alta(e.gA, email(cuenta), "titular"), tPropietario));
		assertEquals(0L, membresias(cuenta, e.org));
		assertEquals(null, rolAsignado(cuenta, e.gA));
	}

	// ------------------------------------------------------------------ quitar

	@Test
	void quitarAsignacionConservaMembresia() throws Exception {
		long cuenta = e.cuenta(usuarios, nombre("quitar"));
		leer(alta(e.gA, email(cuenta), "TITULAR"), tPropietario, 200);
		leer(alta(e.gB, email(cuenta), "AYUDANTE"), tPropietario, 200);
		assertEquals(204, estado(delete(ruta(e.gA) + "/" + cuenta), tPropietario));
		assertEquals(null, rolAsignado(cuenta, e.gA));
		assertEquals("AYUDANTE", rolAsignado(cuenta, e.gB), "solo esa fila");
		assertEquals(1L, membresiasActivas(cuenta, e.org, "PROFESOR"));
		// Otra vez, o a quien nunca estuvo: 404.
		assertEquals(404, estado(delete(ruta(e.gA) + "/" + cuenta), tPropietario));
		assertEquals(404, estado(delete(ruta(e.gA) + "/" + e.gestor), tPropietario));
		assertEquals(404, estado(delete(ruta(e.gA) + "/" + Long.MAX_VALUE), tPropietario));
		assertFalse(idsDe(leer(get(ruta(e.gA)), tPropietario, 200), "usuarioId").contains(cuenta));
	}

	@Test
	void quitarAsignacionCierraElAccesoEnLaSiguientePeticion() throws Exception {
		long cuenta = e.cuenta(usuarios, nombre("cierra"));
		String token = e.token(jwtService, usuarios, cuenta);
		leer(alta(e.gA, email(cuenta), "TITULAR"), tPropietario, 200);
		assertEquals(200, estado(get("/api/profesor/grupos/" + e.gA + "/alumnos"), token));

		assertEquals(204, estado(delete(ruta(e.gA) + "/" + cuenta), tGestor));
		assertEquals(403, estado(get("/api/profesor/grupos/" + e.gA + "/alumnos"), token));
		assertEquals(403, estado(get("/api/profesor/alumnos/" + e.aGrupoA), token));
		// Sigue entrando (D7-21), sin grupos.
		assertTrue(leer(get("/api/profesor/grupos"), token, 200).isEmpty());
	}

	// ------------------------------------------------------------------ permisos

	@Test
	void gestorAsigna() throws Exception {
		long cuenta = e.cuenta(usuarios, nombre("gestor"));
		leer(alta(e.gB, email(cuenta), "AYUDANTE"), tGestor, 200);
		Set<Long> lista = idsDe(leer(get(ruta(e.gB)), tGestor, 200), "usuarioId");
		assertTrue(lista.contains(cuenta), lista.toString());
		assertEquals(asignadosEnBD(e.gB, e.org), lista);
		assertEquals(1L, membresiasActivas(cuenta, e.org, "PROFESOR"));
		assertEquals(204, estado(delete(ruta(e.gB) + "/" + cuenta), tGestor));
		assertEquals(null, rolAsignado(cuenta, e.gB));
	}

	@Test
	void responsableDeSedeNoAsigna_403() throws Exception {
		long cuenta = e.cuenta(usuarios, nombre("resp"));
		// respA es responsable de la sede A, la de gA.
		assertEquals(403, estado(get(ruta(e.gA)), tResp));
		assertEquals(403, estado(alta(e.gA, email(cuenta), "TITULAR"), tResp));
		leer(alta(e.gA, email(cuenta), "TITULAR"), tPropietario, 200);
		assertEquals(403, estado(delete(ruta(e.gA) + "/" + cuenta), tResp));
		assertEquals("TITULAR", rolAsignado(cuenta, e.gA));
		// Solo con la membresía que creó el propietario.
		assertEquals(1L, membresias(cuenta, e.org));
	}

	@Test
	void profesorYFamiliaNoAsignan_403() throws Exception {
		long profesor = e.cuenta(usuarios, nombre("prof"));
		leer(alta(e.gA, email(profesor), "TITULAR"), tPropietario, 200);
		long familia = e.cuentaFamilia(usuarios, nombre("fam"));
		long cuenta = e.cuenta(usuarios, nombre("objetivo"));
		for (long quien : List.of(profesor, familia)) {
			String token = e.token(jwtService, usuarios, quien);
			assertEquals(403, estado(get(ruta(e.gA)), token));
			assertEquals(403, estado(alta(e.gA, email(cuenta), "TITULAR"), token));
			assertEquals(403, estado(delete(ruta(e.gA) + "/" + profesor), token));
		}
		assertEquals(0L, membresias(cuenta, e.org));
		assertEquals("TITULAR", rolAsignado(profesor, e.gA));
		// Sin sesión, 401.
		assertEquals(401, mvc.perform(get(ruta(e.gA))).andReturn().getResponse().getStatus());
	}

	// ------------------------------------------------------------------ registro

	@Test
	void registroConIdsSinEmail() throws Exception {
		long cuenta = e.cuenta(usuarios, nombre("registro"));
		String email = email(cuenta);
		leer(alta(e.gA, email, "TITULAR"), tPropietario, 200);
		leer(alta(e.gA, email, "AYUDANTE"), tGestor, 200);
		assertEquals(204, estado(delete(ruta(e.gA) + "/" + cuenta), tPropietario));

		List<Map<String, Object>> acciones = jdbc.queryForList("""
				SELECT accion, modulo, endpoint, usuario_id, organizacion_id, recurso_id, resumen FROM auditoria_evento
				WHERE modulo = 'profesor' AND resumen REGEXP CONCAT('^PROFESOR .* usuario=#', ?, '( |$)') ORDER BY id""",
				cuenta);
		List<String> resumenes = acciones.stream().map(a -> (String) a.get("resumen")).toList();
		assertEquals(List.of(
				"PROFESOR MEMBRESIA_PROFESOR_CREADA usuario=#" + cuenta,
				"PROFESOR ASIGNACION_CREADA grupo=#" + e.gA + " usuario=#" + cuenta + " rol=TITULAR",
				"PROFESOR ASIGNACION_ROL_CAMBIADO grupo=#" + e.gA + " usuario=#" + cuenta + " rol=AYUDANTE",
				"PROFESOR ASIGNACION_QUITADA grupo=#" + e.gA + " usuario=#" + cuenta + " rol=AYUDANTE"), resumenes);
		assertEquals(List.of("CREATE", "CREATE", "UPDATE", "DELETE"),
				acciones.stream().map(a -> (String) a.get("accion")).toList());
		assertEquals(List.of(propietario, propietario, e.gestor, propietario),
				acciones.stream().map(a -> ((Number) a.get("usuario_id")).longValue()).toList());
		assertEquals(List.of(cuenta, e.gA, e.gA, e.gA),
				acciones.stream().map(a -> ((Number) a.get("recurso_id")).longValue()).toList());
		for (Map<String, Object> accion : acciones) {
			String resumen = (String) accion.get("resumen");
			assertTrue(resumen.matches(RESUMEN_SOLO_IDS), resumen);
			assertFalse(resumen.contains("@") || resumen.contains(e.marca), "sin email ni nombres: " + resumen);
			assertEquals(e.org, ((Number) accion.get("organizacion_id")).longValue());
			assertTrue(((String) accion.get("endpoint")).startsWith(ruta(e.gA)), (String) accion.get("endpoint"));
		}
	}

	@Test
	void auditoriaHttpNoGuardaElEmail() throws Exception {
		long cuenta = e.cuenta(usuarios, nombre("auditada"));
		String email = email(cuenta);
		String sinCuenta = "it-e7-4-nadie-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
		long antes = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM auditoria_evento", Long.class);
		leer(alta(e.gA2, email, "TITULAR"), tPropietario, 200);
		assertEquals(404, estado(alta(e.gA2, sinCuenta, "TITULAR"), tPropietario));

		// El filtro HTTP (AuditoriaRequestFilter) guarda el cuerpo con el email redactado, en el 200 y en el 404 (las
		// acciones explícitas del módulo profesor van aparte y solo llevan ids).
		List<Map<String, Object>> peticiones = jdbc.queryForList("""
				SELECT estado_http, payload_json FROM auditoria_evento
				WHERE id > ? AND endpoint = ? AND metodo_http = 'POST' AND modulo <> 'profesor' ORDER BY id""", antes,
				ruta(e.gA2));
		assertEquals(List.of(200, 404),
				peticiones.stream().map(p -> ((Number) p.get("estado_http")).intValue()).toList());
		for (Map<String, Object> peticion : peticiones) {
			String payload = (String) peticion.get("payload_json");
			assertTrue(payload.contains("\"email\":\"[redactado]\""), payload);
			assertTrue(payload.contains("TITULAR"), payload);
		}
		for (String valor : List.of(email, sinCuenta)) {
			assertEquals(0L, jdbc.queryForObject("""
					SELECT COUNT(*) FROM auditoria_evento
					WHERE id > ? AND (payload_json LIKE ? OR resumen LIKE ? OR query_params_json LIKE ?)""", Long.class,
					antes, "%" + valor + "%", "%" + valor + "%", "%" + valor + "%"), valor);
		}
	}

	// ------------------------------------------------------------------ utilidades

	private static String ruta(long grupo) {
		return "/api/grupos/" + grupo + "/profesores";
	}

	private MockHttpServletRequestBuilder alta(long grupo, String email, String rol) throws Exception {
		Map<String, String> cuerpo = new java.util.HashMap<>();
		cuerpo.put("email", email);
		cuerpo.put("rol", rol);
		return post(ruta(grupo)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
	}

	private String nombre(String base) {
		return base + "-" + UUID.randomUUID().toString().substring(0, 6);
	}

	private String email(long usuario) {
		return jdbc.queryForObject("SELECT email FROM usuario WHERE id = ?", String.class, usuario);
	}

	private void membresia(EscuelaConSedesDePrueba x, long usuario, String rol) {
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", usuario, x.org, rol);
	}

	private void asignar(EscuelaConSedesDePrueba x, long profesor, long grupo, String rol) {
		jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) VALUES (?, ?, ?, ?)",
				x.org, profesor, grupo, rol);
	}

	private long membresias(long usuario, long organizacion) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM membresia WHERE usuario_id = ? AND organizacion_id = ?",
				Long.class, usuario, organizacion);
	}

	private long membresiasActivas(long usuario, long organizacion, String rol) {
		return jdbc.queryForObject("""
				SELECT COUNT(*) FROM membresia WHERE usuario_id = ? AND organizacion_id = ? AND rol = ? AND activa = TRUE""",
				Long.class, usuario, organizacion, rol);
	}

	private Set<Long> asignadosEnBD(long grupo, long organizacion) {
		return new HashSet<>(jdbc.queryForList(
				"SELECT usuario_id FROM asignacion_profesor WHERE grupo_id = ? AND organizacion_id = ?", Long.class, grupo,
				organizacion));
	}

	private String rolAsignado(long usuario, long grupo) {
		List<String> roles = jdbc.queryForList("SELECT rol FROM asignacion_profesor WHERE usuario_id = ? AND grupo_id = ?",
				String.class, usuario, grupo);
		return roles.isEmpty() ? null : roles.get(0);
	}

	private static List<Integer> enParalelo(Callable<Integer> primera, Callable<Integer> segunda) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch preparados = new CountDownLatch(2);
		CountDownLatch salida = new CountDownLatch(1);
		try {
			List<Future<Integer>> futuros = new ArrayList<>();
			for (Callable<Integer> accion : List.of(primera, segunda)) {
				futuros.add(executor.submit(() -> {
					preparados.countDown();
					if (!salida.await(10, TimeUnit.SECONDS)) {
						throw new IllegalStateException("No arrancaron ambas peticiones.");
					}
					return accion.call();
				}));
			}
			assertTrue(preparados.await(10, TimeUnit.SECONDS));
			salida.countDown();
			List<Integer> estados = new ArrayList<>();
			for (Future<Integer> futuro : futuros) {
				estados.add(futuro.get(60, TimeUnit.SECONDS));
			}
			estados.sort(Integer::compareTo);
			return estados;
		} finally {
			salida.countDown();
			executor.shutdownNow();
		}
	}

	private String mensaje(MvcResult r) throws Exception {
		return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("mensaje").asText();
	}

	private JsonNode leer(MockHttpServletRequestBuilder peticion, String token, int esperado) throws Exception {
		MvcResult r = peticion(peticion, token);
		assertEquals(esperado, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

	private int estado(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return peticion(peticion, token).getResponse().getStatus();
	}

	private MvcResult peticion(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
	}

	private static Set<Long> ids(JsonNode lista) {
		return idsDe(lista, "id");
	}

	private static Set<Long> idsDe(JsonNode lista, String campo) {
		Set<Long> ids = new HashSet<>();
		lista.forEach(n -> ids.add(n.get(campo).asLong()));
		return ids;
	}

	private static JsonNode fila(JsonNode lista, long usuarioId) {
		for (JsonNode n : lista) {
			if (n.get("usuarioId").asLong() == usuarioId) {
				return n;
			}
		}
		throw new AssertionError("sin fila para " + usuarioId + ": " + lista);
	}

	private static Set<String> campos(JsonNode nodo) {
		Set<String> campos = new HashSet<>();
		nodo.fieldNames().forEachRemaining(campos::add);
		return campos;
	}
}
