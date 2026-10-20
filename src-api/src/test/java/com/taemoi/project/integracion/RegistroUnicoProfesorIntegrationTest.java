package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.config.AuditoriaRequestFilter.EscrituraConRegistroPropio;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza E7-9 (decisión 214) contra MySQL, con la cadena de filtros completa: cada escritura de
 * {@link EscrituraConRegistroPropio} deja, si responde 2xx, SOLO su registro explícito (con ids; sin la fila genérica
 * del filtro HTTP), que el propietario ve en su registro de acciones; si responde 4xx, solo la fila genérica. Una
 * entrada de la lista sin caso aquí hace fallar el test (salvo el logo, con su caso en LogoEscuelaIntegrationTest).
 * Fuera de la lista, todo se apunta como antes: el alta de profesor con el mismo rol (por eso queda fuera), las
 * lecturas, la gestión y Moiskimdo.
 *
 * <p>Escuela sintética ({@link EscuelaConSedesDePrueba}) con un profesor (solo PROFESOR) en gA; cada caso prepara sus
 * propios datos y solo mira las filas de auditoría posteriores de esa escuela, así que el orden no importa. Datos
 * inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RegistroUnicoProfesorIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	/** Resumen de la fila genérica del filtro HTTP ({@code AuditoriaServiceImpl#construirResumen}). */
	private static final String RESUMEN_GENERICO = "^(CREATE|UPDATE|DELETE|READ|OPERACION) \\S+( #\\d+)? -> \\d+$";
	/** El logo tiene su caso en LogoEscuelaIntegrationTest (superadmin y escuela destino). */
	private static final Set<EscrituraConRegistroPropio> CON_CASO_EN_OTRO_TEST = EnumSet.of(
			EscrituraConRegistroPropio.LOGO_ESCUELA_CAMBIAR, EscrituraConRegistroPropio.LOGO_ESCUELA_QUITAR);

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	long profesor, propietario, gestorOrg1, camiseta, dobok, gAmarillo, gRojo;
	String tProfesor, tPropietario, tGestor, tGestorOrg1;
	final List<Long> productosOrg1 = new ArrayList<>();

	@FunctionalInterface
	interface Peticion {
		MockHttpServletRequestBuilder crear() throws Exception;
	}

	/** Una escritura ya preparada: la petición correcta, sus registros explícitos y una petición que falla. */
	record Caso(String token, long actor, Peticion correcta, int estadoCorrecto, List<String> registros,
			Peticion fallida, int estadoFallido) {
	}

	@FunctionalInterface
	interface Escenario {
		Caso preparar() throws Exception;
	}

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e7-9");
		sistemaDeGrados();
		productoDeGrado("EXAMEN_ROJO", "EXAMEN", 25.0);
		productoDeGrado("RECOMPENSA_ROJO", "RECOMPENSA", 15.0);
		camiseta = producto("CAMISETA E79 " + e.marca, "10.5", true);
		dobok = producto("DOBOK E79 " + e.marca, "25.0", false);

		profesor = e.cuenta(usuarios, "prof");
		membresia(e.org, profesor, "PROFESOR");
		asignar(profesor, e.gA);
		propietario = e.cuenta(usuarios, "propietario");
		membresia(e.org, propietario, "PROPIETARIO");
		gestorOrg1 = e.cuenta(usuarios, "gestor-org1");
		membresia(ORG1, gestorOrg1, "ADMINISTRADOR");

		tProfesor = e.token(jwtService, usuarios, profesor);
		tPropietario = e.token(jwtService, usuarios, propietario);
		tGestor = e.token(jwtService, usuarios, e.gestor);
		Long org = ORG1;
		tGestorOrg1 = jwtService.generateToken(
				TenantContext.calcularCon(ORG1, () -> usuarios.findById(gestorOrg1).orElseThrow()), 600_000, org);
	}

	@AfterAll
	void retirar() {
		for (Long producto : productosOrg1) {
			jdbc.update("DELETE FROM producto WHERE id = ? AND organizacion_id = 1", producto);
		}
		jdbc.update("UPDATE membresia SET activa = FALSE WHERE usuario_id = ? AND organizacion_id = 1", gestorOrg1);
	}

	@AfterEach
	void sinFugas() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ rutas con registro propio

	@ParameterizedTest
	@EnumSource(EscrituraConRegistroPropio.class)
	void correcta_soloElRegistroExplicito_fallida_soloElGenerico(EscrituraConRegistroPropio entrada) throws Exception {
		if (CON_CASO_EN_OTRO_TEST.contains(entrada)) {
			return;
		}
		Escenario escenario = escenarios().get(entrada);
		assertNotNull(escenario, entrada + " está en la lista de AuditoriaRequestFilter sin caso aquí: añade el caso "
				+ "(o quita la ruta de la lista si algún 2xx no deja registro explícito)");
		Caso caso = escenario.preparar();

		// 2xx: solo las filas explícitas, todas visibles para el propietario.
		long antes = ultimoEvento();
		MvcResult correcta = peticion(caso.correcta().crear(), caso.token());
		assertEquals(caso.estadoCorrecto(), correcta.getResponse().getStatus(),
				correcta.getResponse().getContentAsString());
		assertTrue(entrada.reconoce(entrada.metodo(), uri(correcta)), "el caso usa la ruta de " + entrada);
		assertEquals(entrada.metodo(), correcta.getRequest().getMethod());
		List<Map<String, Object>> filas = eventosDesde(antes);
		assertEquals(caso.registros(), filas.stream().map(f -> accion((String) f.get("resumen"))).toList(),
				entrada + ": " + filas);
		for (Map<String, Object> fila : filas) {
			String resumen = (String) fila.get("resumen");
			assertFalse(resumen.matches(RESUMEN_GENERICO), "sin la fila genérica: " + resumen);
			assertEquals(caso.actor(), numero(fila, "usuario_id"));
			assertEquals(200, numero(fila, "estado_http"));
			assertTrue(Set.of("CREATE", "UPDATE", "DELETE").contains(fila.get("accion")), fila.toString());
			JsonNode visto = leer(get("/api/admin/auditoria/eventos/" + numero(fila, "id")), tPropietario, 200);
			assertEquals(resumen, visto.get("resumen").asText(), "el propietario la ve");
			assertEquals(fila.get("modulo"), visto.get("modulo").asText());
			assertEquals(fila.get("accion"), visto.get("accion").asText());
		}

		// 4xx: solo la fila genérica, como siempre.
		antes = ultimoEvento();
		MvcResult fallida = peticion(caso.fallida().crear(), caso.token());
		assertEquals(caso.estadoFallido(), fallida.getResponse().getStatus(), fallida.getResponse().getContentAsString());
		assertTrue(entrada.reconoce(entrada.metodo(), uri(fallida)), "el caso usa la ruta de " + entrada);
		List<Map<String, Object>> errores = eventosDesde(antes);
		assertEquals(1, errores.size(), entrada + ": " + errores);
		Map<String, Object> error = errores.get(0);
		assertTrue(((String) error.get("resumen")).matches(RESUMEN_GENERICO), error.toString());
		assertEquals(entrada.metodo(), error.get("metodo_http"));
		assertEquals(fallida.getRequest().getRequestURI(), error.get("endpoint"));
		assertEquals(caso.estadoFallido(), numero(error, "estado_http"));
		assertEquals(caso.actor(), numero(error, "usuario_id"));
	}

	/** Un caso por entrada de la lista (menos el logo). */
	private Map<EscrituraConRegistroPropio, Escenario> escenarios() {
		Map<EscrituraConRegistroPropio, Escenario> casos = new EnumMap<>(EscrituraConRegistroPropio.class);
		casos.put(EscrituraConRegistroPropio.PROFESOR_APTO, () -> {
			long a = alumnoConGrado("E79APTO", false);
			return delProfesor(() -> conCuerpo(put(deporte(a) + "/apto"), "{\"aptoParaExamen\":true}"), 200,
					List.of("PROFESOR APTO_CAMBIADO"), () -> conCuerpo(put(deporte(a) + "/apto"), "{}"), 400);
		});
		casos.put(EscrituraConRegistroPropio.PROFESOR_FECHA_GRADO, () -> {
			long a = alumnoConGrado("E79FECHA", true);
			return delProfesor(() -> conCuerpo(put(deporte(a) + "/fecha-grado"), "{\"fechaGrado\":\"2025-02-01\"}"),
					200, List.of("PROFESOR FECHA_GRADO_CAMBIADA"),
					() -> conCuerpo(put(deporte(a) + "/fecha-grado"), "{\"fechaGrado\":\"no-es-fecha\"}"), 400);
		});
		casos.put(EscrituraConRegistroPropio.PROFESOR_PASE_EXAMEN, () -> {
			long a = alumnoConGrado("E79EXAMEN", true);
			Peticion pase = () -> conCuerpo(post(deporte(a) + "/pase-examen"), "{\"rojoBordado\":false}");
			// Segundo pase con el primero sin cobrar: 409 (D7-17).
			return delProfesor(pase, 200, List.of("PROFESOR PASE_EXAMEN"), pase, 409);
		});
		casos.put(EscrituraConRegistroPropio.PROFESOR_PASE_RECOMPENSA, () -> {
			long a = alumnoConGrado("E79RECOMPENSA", true);
			Peticion pase = () -> conCuerpo(post(deporte(a) + "/pase-recompensa"), "{\"rojoBordado\":false}");
			return delProfesor(pase, 200, List.of("PROFESOR PASE_RECOMPENSA"), pase, 409);
		});
		casos.put(EscrituraConRegistroPropio.CAJA_PROFESOR_COBRAR_CARGO, () -> {
			long cargo = cargo(alumnoDeCaja("E79COBRAR"));
			Peticion cobrar = () -> post("/api/caja/profesor/cargos/" + cargo + "/cobrar");
			// Ya cobrado: 409.
			return delProfesor(cobrar, 201, List.of("CAJA COBRO_PROFESOR"), cobrar, 409);
		});
		casos.put(EscrituraConRegistroPropio.CAJA_PROFESOR_DESHACER_COBRO, () -> {
			long cobro = cobroDelProfesor(alumnoDeCaja("E79DESHACER"));
			Peticion deshacer = () -> post("/api/caja/profesor/cobros/" + cobro + "/deshacer");
			// Ya deshecho: el cobro no está activo.
			return delProfesor(deshacer, 204, List.of("CAJA COBRO_PROFESOR_DESHECHO"), deshacer, 409);
		});
		casos.put(EscrituraConRegistroPropio.CAJA_PROFESOR_VENDER_PRODUCTO, () -> {
			long a = alumnoDeCaja("E79VENDER");
			// El dobok no se puede vender desde Mi caja: 400.
			return delProfesor(() -> post("/api/caja/profesor/alumnos/" + a + "/productos/" + camiseta + "/cobrar"),
					201, List.of("CAJA COBRO_PROFESOR_PRODUCTO"),
					() -> post("/api/caja/profesor/alumnos/" + a + "/productos/" + dobok + "/cobrar"), 400);
		});
		casos.put(EscrituraConRegistroPropio.CAJA_PROFESOR_PEDIR_ENCARGO, () -> {
			long a = alumnoDeCaja("E79ENCARGO");
			// Sin nota: 400.
			return delProfesor(() -> encargo(a, "Talla 150"), 201, List.of("SOLICITUDES ENCARGO_PEDIDO"),
					() -> encargo(a, null), 400);
		});
		casos.put(EscrituraConRegistroPropio.CAJA_PROFESOR_RETIRAR_SOLICITUD, () -> {
			long solicitud = encargoPedido(alumnoDeCaja("E79RETIRAR"));
			Peticion retirar = () -> post("/api/caja/profesor/solicitudes/" + solicitud + "/retirar");
			// Ya retirada: 409. Retirar cancela el cargo pendiente del encargo (su propio registro).
			return delProfesor(retirar, 200, List.of("CARGO CARGO_CANCELADO", "SOLICITUDES SOLICITUD_RETIRADA"),
					retirar, 409);
		});
		casos.put(EscrituraConRegistroPropio.CAJA_PROFESOR_ENCARGO_ENTREGADO, () -> {
			long solicitud = encargoPedido(alumnoDeCaja("E79ENTREGADO"));
			leer(post("/api/solicitudes/" + solicitud + "/encargado"), tGestor, 200);
			leer(post("/api/solicitudes/" + solicitud + "/recibido"), tGestor, 200);
			Peticion entregado = () -> post("/api/caja/profesor/solicitudes/" + solicitud + "/entregado");
			return delProfesor(entregado, 200, List.of("SOLICITUDES ENCARGO_ENTREGADO"), entregado, 409);
		});
		casos.put(EscrituraConRegistroPropio.CAJA_PROFESOR_DEVOLUCION, () -> {
			// Encargo cobrado por el profesor y retirado: queda la devolución pendiente y el dinero lo lleva él.
			long solicitud = encargoPedido(alumnoDeCaja("E79DEVOLUCION"));
			assertEquals(201, estado(post("/api/caja/profesor/cargos/" + cargoDe(solicitud) + "/cobrar"), tProfesor));
			leer(post("/api/caja/profesor/solicitudes/" + solicitud + "/retirar"), tProfesor, 200);
			Peticion devolver = () -> post("/api/caja/profesor/solicitudes/" + solicitud + "/devolucion");
			// Ya devuelta: sin devolución pendiente, 409.
			return delProfesor(devolver, 200,
					List.of("CAJA DEVOLUCION_PROFESOR", "SOLICITUDES ENCARGO_DEVOLUCION_REGISTRADA"), devolver, 409);
		});
		casos.put(EscrituraConRegistroPropio.CAJA_PROFESOR_PEDIR_DESHACER, () -> {
			long cobro = cobroDelProfesor(alumnoDeCaja("E79PEDIR"));
			entregar(cobro);
			Peticion pedir = () -> conCuerpo(post("/api/caja/profesor/cobros/" + cobro + "/pedir-deshacer"),
					json.writeValueAsString(Map.of("motivo", "No pagó al final", "motivoDeshacer", "AUN_NO_PAGADO")));
			// Ya hay una pendiente sobre el cobro: 409.
			return delProfesor(pedir, 201, List.of("SOLICITUDES SOLICITUD_CREADA"), pedir, 409);
		});
		casos.put(EscrituraConRegistroPropio.GRUPO_QUITAR_PROFESOR, () -> {
			long otro = e.cuenta(usuarios, "quitado-" + UUID.randomUUID().toString().substring(0, 6));
			membresia(e.org, otro, "PROFESOR");
			asignar(otro, e.gA2);
			Peticion quitar = () -> delete("/api/grupos/" + e.gA2 + "/profesores/" + otro);
			// Ya quitado: 404.
			return new Caso(tPropietario, propietario, quitar, 204, List.of("PROFESOR ASIGNACION_QUITADA"), quitar,
					404);
		});
		return casos;
	}

	// ------------------------------------------------------------------ fuera de la lista: como antes

	/**
	 * El alta de un profesor a un grupo con el rol que ya tiene responde 200 sin registro explícito: por eso la ruta
	 * queda fuera de la lista y su 2xx sigue dejando la fila genérica (con el email redactado).
	 */
	@Test
	void altaDeProfesor_sigueConLaFilaGenerica_tambienConElMismoRol() throws Exception {
		long cuenta = e.cuenta(usuarios, "alta-" + UUID.randomUUID().toString().substring(0, 6));
		String email = jdbc.queryForObject("SELECT email FROM usuario WHERE id = ?", String.class, cuenta);
		String ruta = "/api/grupos/" + e.gA2 + "/profesores";
		Peticion alta = () -> conCuerpo(post(ruta), json.writeValueAsString(Map.of("email", email, "rol", "AYUDANTE")));

		long antes = ultimoEvento();
		leer(alta.crear(), tPropietario, 200);
		assertEquals(List.of("PROFESOR MEMBRESIA_PROFESOR_CREADA", "PROFESOR ASIGNACION_CREADA", "CREATE grupos"),
				eventosDesde(antes).stream().map(f -> accion((String) f.get("resumen"))).toList());

		antes = ultimoEvento();
		leer(alta.crear(), tPropietario, 200);
		List<Map<String, Object>> mismoRol = eventosDesde(antes);
		assertEquals(1, mismoRol.size(), mismoRol.toString());
		assertTrue(((String) mismoRol.get(0).get("resumen")).matches(RESUMEN_GENERICO), mismoRol.toString());
		assertEquals(ruta, mismoRol.get(0).get("endpoint"));
		assertEquals(200, numero(mismoRol.get(0), "estado_http"));
		assertTrue(((String) mismoRol.get(0).get("payload_json")).contains("\"email\":\"[redactado]\""));
	}

	@Test
	void lecturasDelProfesorYGestion_sinCambios() throws Exception {
		long antes = ultimoEvento();
		leer(get("/api/profesor/grupos"), tProfesor, 200);
		List<Map<String, Object>> lectura = eventosDesde(antes);
		assertEquals(1, lectura.size(), lectura.toString());
		assertEquals("GET", lectura.get(0).get("metodo_http"));
		assertEquals("READ", lectura.get(0).get("accion"));
		assertTrue(((String) lectura.get(0).get("resumen")).matches(RESUMEN_GENERICO));

		antes = ultimoEvento();
		long cargo = cargo(alumnoDeCaja("E79GESTION"));
		List<Map<String, Object>> gestion = eventosDesde(antes);
		assertEquals(1, gestion.stream().filter(f -> "POST".equals(f.get("metodo_http"))
				&& ((String) f.get("resumen")).matches(RESUMEN_GENERICO) && numero(f, "estado_http") == 200).count(),
				gestion.toString());
		assertTrue(cargo > 0);
	}

	@Test
	void moiskimdo_suGestionSigueConLaFilaGenerica() throws Exception {
		String nombre = "E79 MOISKIMDO " + e.marca;
		jdbc.update("INSERT INTO producto (concepto, precio, organizacion_id, vendible_profesor) VALUES (?, 9.5, 1, FALSE)",
				nombre);
		long producto = jdbc.queryForObject("SELECT id FROM producto WHERE concepto = ? AND organizacion_id = 1",
				Long.class, nombre);
		productosOrg1.add(producto);
		String ruta = "/api/productos/" + producto + "/vendible-profesor";

		long antes = ultimoEvento();
		leer(conCuerpo(patch(ruta), "{\"vendibleProfesor\":true}"), tGestorOrg1, 200);
		List<Map<String, Object>> filas = jdbc.queryForList(
				"SELECT * FROM auditoria_evento WHERE id > ? AND endpoint = ? ORDER BY id", antes, ruta);
		assertEquals(1, filas.size(), filas.toString());
		assertEquals(ORG1, numero(filas.get(0), "organizacion_id"));
		assertEquals("PATCH", filas.get(0).get("metodo_http"));
		assertEquals(200, numero(filas.get(0), "estado_http"));
		assertTrue(((String) filas.get(0).get("resumen")).matches(RESUMEN_GENERICO));
	}

	// ------------------------------------------------------------------ datos

	private Caso delProfesor(Peticion correcta, int estadoCorrecto, List<String> registros, Peticion fallida,
			int estadoFallido) {
		return new Caso(tProfesor, profesor, correcta, estadoCorrecto, registros, fallida, estadoFallido);
	}

	private void sistemaDeGrados() {
		jdbc.update("INSERT INTO sistema_grados (organizacion_id, nombre, plantilla_origen) VALUES (?, 'Sistema IT E79', NULL)",
				e.org);
		long sistema = jdbc.queryForObject("SELECT id FROM sistema_grados WHERE organizacion_id = ?", Long.class, e.org);
		jdbc.update("""
				INSERT INTO grado (organizacion_id, sistema_grados_id, codigo, nombre, orden, color_1, color_2, aplica_a)
				VALUES (?, ?, 'BLANCO', 'Blanco IT', 1, NULL, NULL, 'TODOS'),
				       (?, ?, 'AMARILLO', 'Amarillo IT', 2, NULL, NULL, 'TODOS'),
				       (?, ?, 'ROJO', 'Rojo IT', 3, NULL, NULL, 'TODOS'),
				       (?, ?, 'NEGRO_1_DAN', 'Negro IT', 4, NULL, NULL, 'TODOS')""",
				e.org, sistema, e.org, sistema, e.org, sistema, e.org, sistema);
		jdbc.update("UPDATE disciplina SET sistema_grados_id = ? WHERE id = ?", sistema, e.disciplina);
		gAmarillo = jdbc.queryForObject("SELECT id FROM grado WHERE sistema_grados_id = ? AND codigo = 'AMARILLO'",
				Long.class, sistema);
		gRojo = jdbc.queryForObject("SELECT id FROM grado WHERE sistema_grados_id = ? AND codigo = 'ROJO'", Long.class,
				sistema);
	}

	private void productoDeGrado(String codigo, String tipo, double precio) {
		jdbc.update("""
				INSERT INTO producto (concepto, precio, codigo, tipo, grado_id, organizacion_id)
				VALUES (?, ?, ?, ?, ?, ?)""", codigo.replace('_', ' ') + " E79", precio, codigo, tipo, gRojo, e.org);
	}

	private long producto(String concepto, String precio, boolean vendible) {
		jdbc.update("INSERT INTO producto (concepto, precio, codigo, organizacion_id, vendible_profesor, tipo) "
				+ "VALUES (?, ?, NULL, ?, ?, 'OTRO')", concepto, new BigDecimal(precio), e.org, vendible);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = ?", Long.class, e.org);
	}

	private void membresia(long organizacion, long usuario, String rol) {
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", usuario, organizacion,
				rol);
	}

	private void asignar(long usuario, long grupo) {
		jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) "
				+ "VALUES (?, ?, ?, 'TITULAR')", e.org, usuario, grupo);
	}

	/** Alumno activo de gA (sede A) con taekwondo en amarillo y fecha de grado antigua. */
	private long alumnoConGrado(String prefijo, boolean apto) {
		long id = alumnoDeCaja(prefijo);
		e.taekwondo(id, apto);
		jdbc.update("""
				UPDATE alumno_deporte SET grado_id = ?, fecha_grado = '2025-01-10'
				WHERE alumno_id = ? AND disciplina_id = ?""", gAmarillo, id, e.disciplina);
		return id;
	}

	/** Alumno activo de gA, el grupo del profesor, con sede principal A. */
	private long alumnoDeCaja(String prefijo) {
		long id = e.alumno(prefijo + e.marca, e.sA, true);
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", e.gA, id);
		return id;
	}

	private static String deporte(long alumno) {
		return "/api/profesor/alumnos/" + alumno + "/deportes/TAEKWONDO";
	}

	/** Cargo pendiente de la camiseta, creado por el gestor como siempre. */
	private long cargo(long alumno) throws Exception {
		return leer(conCuerpo(post("/api/productos-alumno/alumno/" + alumno + "/producto/" + camiseta),
				"{\"cantidad\":1,\"pagado\":false}"), tGestor, 200).get("id").asLong();
	}

	/** El profesor cobra en efectivo un cargo nuevo del alumno: devuelve el cobro. */
	private long cobroDelProfesor(long alumno) throws Exception {
		long cargo = cargo(alumno);
		return leer(post("/api/caja/profesor/cargos/" + cargo + "/cobrar"), tProfesor, 201).get("cobroId").asLong();
	}

	/** El gestor recibe del profesor la entrega de ese cobro (contado = declarado). */
	private void entregar(long cobro) throws Exception {
		Map<String, Object> cuerpo = new HashMap<>();
		cuerpo.put("entregadoPorUsuarioId", profesor);
		cuerpo.put("cobroIds", List.of(cobro));
		cuerpo.put("importeContado", new BigDecimal("10.50"));
		leer(conCuerpo(post("/api/caja/sedes/" + e.sA + "/entregas"), json.writeValueAsString(cuerpo)), tGestor, 201);
	}

	private MockHttpServletRequestBuilder encargo(long alumno, String nota) throws Exception {
		Map<String, Object> cuerpo = new HashMap<>();
		cuerpo.put("productoId", dobok);
		cuerpo.put("nota", nota);
		return conCuerpo(post("/api/caja/profesor/alumnos/" + alumno + "/encargos"), json.writeValueAsString(cuerpo));
	}

	/** El profesor encarga un dobok para el alumno: devuelve la solicitud. */
	private long encargoPedido(long alumno) throws Exception {
		return leer(encargo(alumno, "Talla 150"), tProfesor, 201).get("id").asLong();
	}

	private long cargoDe(long solicitud) {
		return jdbc.queryForObject("SELECT producto_alumno_id FROM solicitud WHERE id = ?", Long.class, solicitud);
	}

	// ------------------------------------------------------------------ auditoría y HTTP

	private long ultimoEvento() {
		return jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM auditoria_evento", Long.class);
	}

	/** Filas de auditoría de la escuela de prueba posteriores a {@code id}, en orden. */
	private List<Map<String, Object>> eventosDesde(long id) {
		return jdbc.queryForList("SELECT * FROM auditoria_evento WHERE id > ? AND organizacion_id = ? ORDER BY id", id,
				e.org);
	}

	/** Las dos primeras palabras del resumen: «CAJA COBRO_PROFESOR», «CREATE grupos»… */
	private static String accion(String resumen) {
		String[] partes = resumen.split(" ");
		return partes.length < 2 ? resumen : partes[0] + " " + partes[1];
	}

	private static String uri(MvcResult r) {
		return r.getRequest().getRequestURI().toLowerCase(Locale.ROOT);
	}

	private static long numero(Map<String, Object> fila, String columna) {
		return ((Number) fila.get(columna)).longValue();
	}

	private static MockHttpServletRequestBuilder conCuerpo(MockHttpServletRequestBuilder peticion, String cuerpo) {
		return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private JsonNode leer(MockHttpServletRequestBuilder peticion, String token, int esperado) throws Exception {
		MvcResult r = peticion(peticion, token);
		assertEquals(esperado, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		String cuerpo = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
		return cuerpo.isBlank() ? json.nullNode() : json.readTree(cuerpo);
	}

	private int estado(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return peticion(peticion, token).getResponse().getStatus();
	}

	private MvcResult peticion(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
	}
}
