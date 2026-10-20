package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.TarifaRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza E8-2 contra MySQL (D8-1, D8-2, D8-3, D8-16; fase3-e8-miembros.md §6.2 y §7): API de sedes.
 * <ul>
 * <li>Solo propietario y gestor; la escuela activa y nada más (sede de otra escuela: 404).</li>
 * <li>Nombre único en la escuela (409, también con dos altas a la vez); longitudes de las columnas (400).</li>
 * <li>Nunca sin sedes activas (409, también con dos desactivaciones a la vez); desactivar conserva a sus
 * responsables.</li>
 * <li>Una fila de registro por escritura, solo con ids; los 4xx, la genérica con los datos de la sede redactados.</li>
 * <li>D8-3: con dos sedes activas, el alta de grupo y de alumno sin sede de la gestión, como hoy.</li>
 * </ul>
 * Escuelas sintéticas ({@link EscuelaConSedesDePrueba}: A y B activas, C inactiva) y escuelas mínimas por SQL; cada
 * test crea sus sedes con nombre único y mira solo sus filas, así que el orden no importa. Nada en Moiskimdo. Datos
 * inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SedesApiIntegrationTest extends MySqlIntegrationTest {

	private static final String RUTA = "/api/sedes";
	private static final String NO_EXISTE = "La sede no existe.";
	private static final String REPETIDO = "Ya hay una sede con ese nombre.";
	private static final String SIN_ACTIVAS = "La escuela se quedaría sin sedes activas.";
	private static final Set<String> CLAVES_DTO = Set.of("id", "nombre", "direccion", "localidad", "codigoPostal",
			"telefono", "email", "codigoClubFederacion", "aforoTurno", "activa");
	/** Resumen de la fila genérica del filtro HTTP ({@code AuditoriaServiceImpl#construirResumen}). */
	private static final String RESUMEN_GENERICO = "^(CREATE|UPDATE|DELETE|READ|OPERACION) \\S+( #\\d+)? -> \\d+$";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;
	@Autowired TarifaRepository tarifas;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	EscuelaConSedesDePrueba d83;
	long propietario, profesor;
	String tPropietario, tGestor, tFamilia, tRespA, tRespAC, tProfesor, tPlataforma, tPropietarioOtra, tGestorD83;

	/** Escuela mínima por SQL: sus sedes y un propietario. */
	record Mini(long org, String token, List<Long> sedes) {
	}

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e8-2");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e8-2-otra");
		d83 = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e8-2-d83");
		// D8-3: la escuela empieza con una sola sede activa (A); B y C cerradas.
		jdbc.update("UPDATE sede SET activa = FALSE WHERE id = ?", d83.sB);
		TenantContext.ejecutarCon(d83.org, () -> DisciplinasDePrueba.tarifa(tarifas, "ADULTO", "30.00", null));

		propietario = e.cuenta(usuarios, "propietario");
		membresia(e.org, propietario, "PROPIETARIO");
		long familia = e.cuentaFamilia(usuarios, "familia");
		profesor = e.cuenta(usuarios, "profesor");
		membresia(e.org, profesor, "PROFESOR");
		long plataforma = e.cuenta(usuarios, "plataforma");
		jdbc.update("UPDATE usuario SET es_admin_plataforma = TRUE WHERE id = ?", plataforma);
		long propietarioOtra = otra.cuenta(usuarios, "propietario");
		membresia(otra.org, propietarioOtra, "PROPIETARIO");

		tPropietario = e.token(jwtService, usuarios, propietario);
		tGestor = e.token(jwtService, usuarios, e.gestor);
		tFamilia = e.token(jwtService, usuarios, familia);
		tRespA = e.token(jwtService, usuarios, e.respA);
		tRespAC = e.token(jwtService, usuarios, e.respAC);
		tProfesor = e.token(jwtService, usuarios, profesor);
		Usuario cuentaPlataforma = TenantContext.calcularCon(EscuelaConSedesDePrueba.ORG1,
				() -> usuarios.findById(plataforma).orElseThrow());
		tPlataforma = jwtService.generateToken(cuentaPlataforma, 600_000, EscuelaConSedesDePrueba.ORG1);
		tPropietarioOtra = otra.token(jwtService, usuarios, propietarioOtra);
		tGestorD83 = d83.token(jwtService, usuarios, d83.gestor);
	}

	@AfterEach
	void sinFugas() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ lecturas

	@Test
	void listarSedes_soloDeSuEscuela() throws Exception {
		JsonNode lista = leer(get(RUTA), tPropietario, 200);
		List<Long> ids = new ArrayList<>();
		lista.forEach(n -> ids.add(n.get("id").asLong()));
		assertTrue(ids.containsAll(List.of(e.sA, e.sB, e.sC)), ids.toString());
		assertEquals(ids.stream().sorted().toList(), ids, "por id");
		Set<Long> deLaEscuela = new HashSet<>(jdbc.queryForList("SELECT id FROM sede WHERE organizacion_id = ?",
				Long.class, e.org));
		assertEquals(deLaEscuela, new HashSet<>(ids), "todas las de la escuela y ninguna más");
		for (long ajena : List.of(otra.sA, otra.sB, otra.sC)) {
			assertFalse(ids.contains(ajena));
		}
		for (JsonNode sede : lista) {
			assertEquals(CLAVES_DTO, campos(sede));
		}
		assertFalse(sede(lista, e.sC).get("activa").asBoolean(), "también la inactiva");
		assertTrue(sede(lista, e.sA).get("activa").asBoolean());
		assertTrue(sede(lista, e.sA).get("aforoTurno").isNull(), "null = el límite de la escuela");
		assertEquals(200, estado(head(RUTA), tPropietario));
	}

	@Test
	void sedeDeOtraEscuela_404() throws Exception {
		JsonNode propia = leer(get(RUTA + "/" + e.sA), tPropietario, 200);
		assertEquals(e.sA, propia.get("id").asLong());
		assertEquals(nombreDe(e.sA), propia.get("nombre").asText());
		assertEquals(CLAVES_DTO, campos(propia));

		String nombreAjena = nombreDe(otra.sA);
		for (long ajena : List.of(otra.sA, 999_999_999L)) {
			assertMensaje(peticion(get(RUTA + "/" + ajena), tPropietario), 404, NO_EXISTE);
			assertMensaje(peticion(conCuerpo(put(RUTA + "/" + ajena), cuerpo(Map.of("nombre", "IT E8-2 x " + e.marca))),
					tPropietario), 404, NO_EXISTE);
			assertMensaje(peticion(conCuerpo(put(RUTA + "/" + ajena + "/activa"), "{\"activa\":false}"), tPropietario),
					404, NO_EXISTE);
		}
		assertEquals(nombreAjena, nombreDe(otra.sA), "la de la otra escuela no cambia");
		assertTrue(activa(otra.sA));
	}

	// ------------------------------------------------------------------ alta y edición

	@Test
	void crearSede_valida_201_yRegistra() throws Exception {
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("nombre", "  IT E8-2 nueva " + e.marca + "  ");
		datos.put("direccion", " Calle Inventada 1 ");
		datos.put("localidad", "Villainventada");
		datos.put("codigoPostal", "41000");
		datos.put("telefono", "  ");
		datos.put("email", "SEDE-NUEVA@example.test");
		datos.put("codigoClubFederacion", "");
		datos.put("aforoTurno", 25);

		long antes = ultimoEvento();
		MvcResult r = peticion(conCuerpo(post(RUTA), cuerpo(datos)), tPropietario);
		assertEquals(201, r.getResponse().getStatus(), contenido(r));
		JsonNode creada = json.readTree(contenido(r));
		long id = creada.get("id").asLong();
		assertEquals(CLAVES_DTO, campos(creada));
		assertEquals("IT E8-2 nueva " + e.marca, creada.get("nombre").asText(), "recortado");
		assertEquals("Calle Inventada 1", creada.get("direccion").asText());
		assertEquals("Villainventada", creada.get("localidad").asText());
		assertEquals("41000", creada.get("codigoPostal").asText());
		assertTrue(creada.get("telefono").isNull(), "en blanco: null");
		assertTrue(creada.get("codigoClubFederacion").isNull());
		assertEquals("SEDE-NUEVA@example.test", creada.get("email").asText());
		assertEquals(25, creada.get("aforoTurno").asInt());
		assertTrue(creada.get("activa").asBoolean(), "nace activa");

		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM sede WHERE id = ?", id);
		assertEquals(e.org, numero(fila, "organizacion_id"));
		assertEquals("IT E8-2 nueva " + e.marca, fila.get("nombre"));
		assertNull(fila.get("telefono"));

		List<Map<String, Object>> filas = eventosDesde(antes, e.org);
		assertEquals(1, filas.size(), filas.toString());
		Map<String, Object> registro = filas.get(0);
		assertEquals("SEDES SEDE_CREADA sede=#" + id, registro.get("resumen"));
		assertEquals("sedes", registro.get("modulo"));
		assertEquals("CREATE", registro.get("accion"));
		assertEquals(id, numero(registro, "recurso_id"));
		assertEquals(propietario, numero(registro, "usuario_id"));
		assertEquals(RUTA, registro.get("endpoint"));

		// El propietario la ve en su registro de acciones.
		JsonNode visto = leer(get("/api/admin/auditoria/eventos/" + numero(registro, "id")), tPropietario, 200);
		assertEquals("SEDES SEDE_CREADA sede=#" + id, visto.get("resumen").asText());
	}

	@Test
	void nombreRepetidoEnLaEscuela_409() throws Exception {
		long a = crear("IT E8-2 repetida " + e.marca);
		long b = crear("IT E8-2 otra repetida " + e.marca);
		long total = sedesDe(e.org);

		assertMensaje(peticion(conCuerpo(post(RUTA), cuerpo(Map.of("nombre", " it e8-2 REPETIDA " + e.marca + " "))),
				tPropietario), 409, REPETIDO);
		assertEquals(total, sedesDe(e.org), "no se crea");

		assertMensaje(peticion(conCuerpo(put(RUTA + "/" + b), cuerpo(Map.of("nombre", "IT E8-2 REPETIDA " + e.marca))),
				tPropietario), 409, REPETIDO);
		assertEquals("IT E8-2 otra repetida " + e.marca, nombreDe(b), "no cambia");

		// Su propio nombre con otras mayúsculas: vale.
		leer(conCuerpo(put(RUTA + "/" + a), cuerpo(Map.of("nombre", "IT E8-2 REPETIDA " + e.marca))), tPropietario, 200);
		assertEquals("IT E8-2 REPETIDA " + e.marca, nombreDe(a));
	}

	@Test
	void mismoNombreEnOtraEscuela_ok() throws Exception {
		String nombre = "IT E8-2 compartida " + e.marca;
		long propia = crear(nombre);
		MvcResult r = peticion(conCuerpo(post(RUTA), cuerpo(Map.of("nombre", nombre))), tPropietarioOtra);
		assertEquals(201, r.getResponse().getStatus(), contenido(r));
		long ajena = json.readTree(contenido(r)).get("id").asLong();
		assertEquals(otra.org, jdbc.queryForObject("SELECT organizacion_id FROM sede WHERE id = ?", Long.class, ajena));
		assertEquals(e.org, jdbc.queryForObject("SELECT organizacion_id FROM sede WHERE id = ?", Long.class, propia));
	}

	@Test
	void validaciones_400() throws Exception {
		List<Map<String, Object>> invalidos = new ArrayList<>();
		invalidos.add(con("nombre", "x".repeat(101)));
		invalidos.add(con("nombre", ""));
		invalidos.add(con("nombre", "   "));
		invalidos.add(con("nombre", null));
		invalidos.add(con("direccion", "d".repeat(256)));
		invalidos.add(con("localidad", "l".repeat(101)));
		invalidos.add(con("codigoPostal", "1".repeat(11)));
		invalidos.add(con("telefono", "6".repeat(21)));
		invalidos.add(con("email", "a".repeat(169) + "@example.test"));
		invalidos.add(con("email", "no-es-un-email"));
		invalidos.add(con("email", "dos@@example.test"));
		invalidos.add(con("codigoClubFederacion", "C".repeat(41)));
		invalidos.add(con("aforoTurno", 0));
		invalidos.add(con("aforoTurno", -3));

		long editable = crear("IT E8-2 validar " + e.marca);
		long total = sedesDe(e.org);
		for (Map<String, Object> datos : invalidos) {
			MvcResult alta = peticion(conCuerpo(post(RUTA), cuerpo(datos)), tPropietario);
			assertEquals(400, alta.getResponse().getStatus(), datos + ": " + contenido(alta));
			assertFalse(mensaje(alta).isBlank(), datos.toString());
			MvcResult edicion = peticion(conCuerpo(put(RUTA + "/" + editable), cuerpo(datos)), tPropietario);
			assertEquals(400, edicion.getResponse().getStatus(), datos + ": " + contenido(edicion));
		}
		assertMensaje(peticion(conCuerpo(post(RUTA), "{}"), tPropietario), 400, "Indica el nombre de la sede.");
		assertEquals(total, sedesDe(e.org), "no se crea ninguna");
		assertEquals("IT E8-2 validar " + e.marca, nombreDe(editable), "no cambia");

		// En el límite, vale (caracteres, no unidades UTF-16: 𝔸 ocupa dos).
		Map<String, Object> limite = new LinkedHashMap<>();
		limite.put("nombre", "IT " + e.marca + " " + "𝔸".repeat(100 - 4 - e.marca.length()));
		limite.put("direccion", "d".repeat(255));
		limite.put("localidad", "l".repeat(100));
		limite.put("codigoPostal", "1".repeat(10));
		limite.put("telefono", "6".repeat(20));
		limite.put("email", "a".repeat(167) + "@example.test");
		limite.put("codigoClubFederacion", "C".repeat(40));
		limite.put("aforoTurno", 1);
		MvcResult r = peticion(conCuerpo(post(RUTA), cuerpo(limite)), tPropietario);
		assertEquals(201, r.getResponse().getStatus(), contenido(r));
	}

	@Test
	void editarSede_registraUnaFila() throws Exception {
		long sede = crear("IT E8-2 editar " + e.marca);
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("nombre", "IT E8-2 editada " + e.marca);
		datos.put("localidad", "Otra Villa");
		datos.put("aforoTurno", 12);

		long antes = ultimoEvento();
		JsonNode editada = leer(conCuerpo(put(RUTA + "/" + sede), cuerpo(datos)), tPropietario, 200);
		assertEquals("IT E8-2 editada " + e.marca, editada.get("nombre").asText());
		assertEquals("Otra Villa", editada.get("localidad").asText());
		assertEquals(12, editada.get("aforoTurno").asInt());
		assertTrue(editada.get("activa").asBoolean(), "editar no toca activa");
		assertEquals(12, jdbc.queryForObject("SELECT aforo_turno FROM sede WHERE id = ?", Integer.class, sede));
		assertEquals(List.of("SEDES SEDE_EDITADA sede=#" + sede), resumenes(eventosDesde(antes, e.org)));

		// Mismo cuerpo: 200 y su fila, marcada sin cambios.
		antes = ultimoEvento();
		leer(conCuerpo(put(RUTA + "/" + sede), cuerpo(datos)), tPropietario, 200);
		assertEquals(List.of("SEDES SEDE_EDITADA sede=#" + sede + " sinCambios=SI"),
				resumenes(eventosDesde(antes, e.org)));

		// PUT es un reemplazo: lo que no llega queda null (aforo: el límite de la escuela).
		leer(conCuerpo(put(RUTA + "/" + sede), cuerpo(Map.of("nombre", "IT E8-2 editada " + e.marca))), tPropietario,
				200);
		assertNull(jdbc.queryForObject("SELECT aforo_turno FROM sede WHERE id = ?", Integer.class, sede));
		assertNull(jdbc.queryForObject("SELECT localidad FROM sede WHERE id = ?", String.class, sede));
	}

	// ------------------------------------------------------------------ activar y desactivar

	@Test
	void desactivarUltimaSedeActiva_409() throws Exception {
		Mini mini = mini("ultima", true, false);
		long activa = mini.sedes().get(0);
		long antes = ultimoEvento();
		assertMensaje(peticion(conCuerpo(put(RUTA + "/" + activa + "/activa"), "{\"activa\":false}"), mini.token()), 409,
				SIN_ACTIVAS);
		assertTrue(activa(activa), "sigue activa");
		List<Map<String, Object>> filas = eventosDesde(antes, mini.org());
		assertEquals(1, filas.size(), filas.toString());
		assertTrue(((String) filas.get(0).get("resumen")).matches(RESUMEN_GENERICO), "solo la genérica del 409");

		// Desactivar la que ya está inactiva no deja a la escuela sin activas: 200 sin cambios.
		long inactiva = mini.sedes().get(1);
		leer(conCuerpo(put(RUTA + "/" + inactiva + "/activa"), "{\"activa\":false}"), mini.token(), 200);
		assertTrue(activa(activa));
	}

	@Test
	void desactivarConOtraActiva_ok_yRegistra() throws Exception {
		long sede = crear("IT E8-2 desactivar " + e.marca);
		long antes = ultimoEvento();
		JsonNode r = leer(conCuerpo(put(RUTA + "/" + sede + "/activa"), "{\"activa\":false}"), tPropietario, 200);
		assertFalse(r.get("activa").asBoolean());
		assertFalse(activa(sede));
		List<Map<String, Object>> filas = eventosDesde(antes, e.org);
		assertEquals(List.of("SEDES SEDE_DESACTIVADA sede=#" + sede), resumenes(filas));
		assertEquals("UPDATE", filas.get(0).get("accion"));
		assertEquals(RUTA + "/" + sede + "/activa", filas.get(0).get("endpoint"));

		antes = ultimoEvento();
		leer(conCuerpo(put(RUTA + "/" + sede + "/activa"), "{\"activa\":false}"), tPropietario, 200);
		assertEquals(List.of("SEDES SEDE_DESACTIVADA sede=#" + sede + " sinCambios=SI"),
				resumenes(eventosDesde(antes, e.org)));
		assertFalse(activa(sede));
		assertTrue(activa(e.sA) && activa(e.sB), "las demás, igual");
	}

	@Test
	void reactivar_ok() throws Exception {
		long sede = crear("IT E8-2 reactivar " + e.marca);
		leer(conCuerpo(put(RUTA + "/" + sede + "/activa"), "{\"activa\":false}"), tPropietario, 200);
		long antes = ultimoEvento();
		JsonNode r = leer(conCuerpo(put(RUTA + "/" + sede + "/activa"), "{\"activa\":true}"), tPropietario, 200);
		assertTrue(r.get("activa").asBoolean());
		assertTrue(activa(sede));
		assertEquals(List.of("SEDES SEDE_ACTIVADA sede=#" + sede), resumenes(eventosDesde(antes, e.org)));
		assertMensaje(peticion(conCuerpo(put(RUTA + "/" + sede + "/activa"), "{}"), tPropietario), 400,
				"Indica si la sede queda activa.");
		// Al terminar, inactiva: la escuela de prueba vuelve a A y B activas.
		leer(conCuerpo(put(RUTA + "/" + sede + "/activa"), "{\"activa\":false}"), tPropietario, 200);
	}

	@Test
	void dosDesactivacionesALaVez_quedaUnaActiva() throws Exception {
		Mini mini = mini("carrera", true, true);
		long s1 = mini.sedes().get(0);
		long s2 = mini.sedes().get(1);
		for (int ronda = 0; ronda < 6; ronda++) {
			jdbc.update("UPDATE sede SET activa = TRUE WHERE organizacion_id = ?", mini.org());
			List<Integer> estados = enParalelo(
					() -> estado(conCuerpo(put(RUTA + "/" + s1 + "/activa"), "{\"activa\":false}"), mini.token()),
					() -> estado(conCuerpo(put(RUTA + "/" + s2 + "/activa"), "{\"activa\":false}"), mini.token()));
			assertEquals(List.of(200, 409), estados, "ronda " + ronda);
			assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sede WHERE organizacion_id = ? AND activa = TRUE",
					Long.class, mini.org()), "ronda " + ronda + ": siempre una activa");
		}
	}

	@Test
	void dosAltasALaVezMismoNombre_sin500() throws Exception {
		Mini mini = mini("doble", true, false);
		for (int ronda = 0; ronda < 4; ronda++) {
			String nombre = "IT E8-2 a la vez " + ronda + " " + e.marca;
			String datos = cuerpo(Map.of("nombre", nombre));
			List<Integer> estados = enParalelo(() -> estado(conCuerpo(post(RUTA), datos), mini.token()),
					() -> estado(conCuerpo(post(RUTA), datos), mini.token()));
			assertEquals(List.of(201, 409), estados, "ronda " + ronda);
			assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sede WHERE organizacion_id = ? AND nombre = ?",
					Long.class, mini.org(), nombre));
		}
	}

	@Test
	void desactivarSedeConservaResponsables() throws Exception {
		long sede = crear("IT E8-2 con responsable " + e.marca);
		long membresia = jdbc.queryForObject("""
				SELECT id FROM membresia WHERE usuario_id = ? AND organizacion_id = ? AND rol = 'RESPONSABLE_SEDE'""",
				Long.class, e.respAC, e.org);
		jdbc.update("INSERT INTO membresia_sede (membresia_id, sede_id) VALUES (?, ?)", membresia, sede);
		try {
			leer(conCuerpo(put(RUTA + "/" + sede + "/activa"), "{\"activa\":false}"), tPropietario, 200);
			assertEquals(1, jdbc.queryForObject(
					"SELECT COUNT(*) FROM membresia_sede WHERE membresia_id = ? AND sede_id = ?", Long.class, membresia,
					sede), "la fila de membresia_sede sigue");
			JsonNode permisos = leer(get("/api/auth/permisos"), tRespAC, 200);
			JsonNode vista = null;
			for (JsonNode s : permisos.get("sedes")) {
				if (s.get("id").asLong() == sede) {
					vista = s;
				}
			}
			assertTrue(vista != null, "el responsable la sigue viendo: " + permisos);
			assertFalse(vista.get("activa").asBoolean());
		} finally {
			jdbc.update("DELETE FROM membresia_sede WHERE membresia_id = ? AND sede_id = ?", membresia, sede);
		}
	}

	// ------------------------------------------------------------------ permisos

	@Test
	void responsableYProfesorYFamilia_403() throws Exception {
		long sede = crear("IT E8-2 permisos " + e.marca);
		long total = sedesDe(e.org);
		for (String token : List.of(tRespA, tRespAC, tProfesor, tFamilia, tPlataforma)) {
			for (MockHttpServletRequestBuilder p : rutas(sede, "IT E8-2 intruso " + e.marca)) {
				assertEquals(403, estado(p, token), p.toString());
			}
		}
		for (MockHttpServletRequestBuilder p : rutas(sede, "IT E8-2 anonimo " + e.marca)) {
			assertEquals(401, mvc.perform(p).andReturn().getResponse().getStatus());
		}
		assertEquals(total, sedesDe(e.org), "nada se crea");
		assertEquals("IT E8-2 permisos " + e.marca, nombreDe(sede), "nada cambia");
		assertTrue(activa(sede));
	}

	@Test
	void gestorPuede() throws Exception {
		assertEquals(200, estado(get(RUTA), tGestor));
		assertEquals(200, estado(get(RUTA + "/" + e.sA), tGestor));
		long antes = ultimoEvento();
		MvcResult alta = peticion(conCuerpo(post(RUTA), cuerpo(Map.of("nombre", "IT E8-2 del gestor " + e.marca))),
				tGestor);
		assertEquals(201, alta.getResponse().getStatus(), contenido(alta));
		long sede = json.readTree(contenido(alta)).get("id").asLong();
		leer(conCuerpo(put(RUTA + "/" + sede), cuerpo(Map.of("nombre", "IT E8-2 del gestor bis " + e.marca))), tGestor,
				200);
		leer(conCuerpo(put(RUTA + "/" + sede + "/activa"), "{\"activa\":false}"), tGestor, 200);
		List<Map<String, Object>> filas = eventosDesde(antes, e.org);
		assertEquals(List.of("SEDES SEDE_CREADA sede=#" + sede, "SEDES SEDE_EDITADA sede=#" + sede,
				"SEDES SEDE_DESACTIVADA sede=#" + sede), resumenes(filas));
		for (Map<String, Object> fila : filas) {
			assertEquals(e.gestor, numero(fila, "usuario_id"));
		}
	}

	// ------------------------------------------------------------------ registro

	@Test
	void registroSoloConIds() throws Exception {
		String nombre = "IT E8-2 Secreta " + e.marca;
		String direccion = "Calle Inventada Secreta 99";
		String telefono = "699111222";
		String email = "sede-secreta-" + e.marca + "@example.test";
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("nombre", nombre);
		datos.put("direccion", direccion);
		datos.put("telefono", telefono);
		datos.put("email", email);

		long antes = ultimoEvento();
		MvcResult alta = peticion(conCuerpo(post(RUTA), cuerpo(datos)), tPropietario);
		assertEquals(201, alta.getResponse().getStatus(), contenido(alta));
		long sede = json.readTree(contenido(alta)).get("id").asLong();
		datos.put("localidad", "Villa Secreta");
		leer(conCuerpo(put(RUTA + "/" + sede), cuerpo(datos)), tPropietario, 200);
		leer(conCuerpo(put(RUTA + "/" + sede + "/activa"), "{\"activa\":false}"), tPropietario, 200);
		leer(conCuerpo(put(RUTA + "/" + sede + "/activa"), "{\"activa\":true}"), tPropietario, 200);
		// Y los 4xx (nombre repetido; edición con el código postal largo).
		assertEquals(409, estado(conCuerpo(post(RUTA), cuerpo(datos)), tPropietario));
		datos.put("codigoPostal", "1".repeat(11));
		assertEquals(400, estado(conCuerpo(put(RUTA + "/" + sede), cuerpo(datos)), tPropietario));
		leer(conCuerpo(put(RUTA + "/" + sede + "/activa"), "{\"activa\":false}"), tPropietario, 200);

		List<Map<String, Object>> filas = eventosDesde(antes, e.org);
		assertEquals(7, filas.size(), filas.toString());
		for (Map<String, Object> fila : filas) {
			for (String columna : List.of("resumen", "payload_json", "query_params_json", "endpoint")) {
				String valor = String.valueOf(fila.get(columna));
				for (String dato : List.of(nombre, direccion, telefono, email, "Villa Secreta", "Secreta")) {
					assertFalse(valor.contains(dato), columna + " contiene «" + dato + "»: " + fila);
				}
			}
		}
	}

	@Test
	void clavesRedactadas_4xx() throws Exception {
		long sede = crear("IT E8-2 redactar " + e.marca);
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("nombre", "IT E8-2 Redactada " + e.marca);
		datos.put("direccion", "Calle Redactada 7");
		datos.put("localidad", "Villa Redactada");
		datos.put("codigoPostal", "1".repeat(11));
		datos.put("telefono", "611222333");
		datos.put("email", "redactada-" + e.marca + "@example.test");
		datos.put("codigoClubFederacion", "TKD REDACTADA");
		datos.put("aforoTurno", 30);

		for (MockHttpServletRequestBuilder p : List.of(conCuerpo(post(RUTA), cuerpo(datos)),
				conCuerpo(put(RUTA + "/" + sede), cuerpo(datos)))) {
			long antes = ultimoEvento();
			assertEquals(400, estado(p, tPropietario));
			List<Map<String, Object>> filas = eventosDesde(antes, e.org);
			assertEquals(1, filas.size(), filas.toString());
			Map<String, Object> fila = filas.get(0);
			assertTrue(((String) fila.get("resumen")).matches(RESUMEN_GENERICO), fila.toString());
			assertEquals(400, numero(fila, "estado_http"));
			String payload = (String) fila.get("payload_json");
			for (String clave : List.of("nombre", "direccion", "localidad", "codigoPostal", "telefono", "email",
					"codigoClubFederacion")) {
				assertTrue(payload.contains("\"" + clave + "\":\"[redactado]\""), clave + ": " + payload);
			}
			assertFalse(payload.contains("Redactada"), payload);
			assertFalse(payload.contains("611222333"), payload);
			assertTrue(payload.contains("\"aforoTurno\":30"), "el aforo no es un dato personal: " + payload);
		}
	}

	// ------------------------------------------------------------------ D8-3: dos sedes activas, como hoy

	/**
	 * D8-3 (fase3-e8-miembros.md §9 riesgo 9): con una sede activa, el grupo que la gestión da de alta sin sede recibe
	 * esa sede; con dos, {@code SedeService.sedePorDefecto()} queda vacío y el grupo se crea igual (201) SIN sede
	 * ({@code grupo.sede_id} NULL), también al editarlo sin sede. Con sede explícita, la pedida. No se cambia en la E8.
	 */
	@Test
	void dosSedesActivas_altaDeGrupo_comportamientoActual() throws Exception {
		assertEquals(d83.sA, sedeDelGrupo(altaDeGrupo("IT E8-2 d83 una " + d83.marca, null)), "una activa: esa");
		long segunda = crearEn(tGestorD83, "IT E8-2 d83 segunda grupos " + d83.marca);
		try {
			long sinSede = altaDeGrupo("IT E8-2 d83 sin sede " + d83.marca, null);
			assertNull(sedeDelGrupo(sinSede), "dos activas: el grupo queda sin sede");
			assertEquals(200, estado(conCuerpo(put("/api/grupos/" + sinSede),
					grupo("IT E8-2 d83 sin sede editado " + d83.marca, null)), tGestorD83));
			assertNull(sedeDelGrupo(sinSede), "editarlo sin sede tampoco le pone una");
			assertEquals(segunda, sedeDelGrupo(altaDeGrupo("IT E8-2 d83 en la segunda " + d83.marca, segunda)));
			assertEquals(d83.sA, sedeDelGrupo(altaDeGrupo("IT E8-2 d83 en A " + d83.marca, d83.sA)));
		} finally {
			jdbc.update("UPDATE sede SET activa = FALSE WHERE id = ?", segunda);
		}
		assertEquals(d83.sA, sedeDelGrupo(altaDeGrupo("IT E8-2 d83 otra vez una " + d83.marca, null)),
				"otra vez una activa: esa");
	}

	/**
	 * D8-3: el alumno que la gestión da de alta sin sede principal recibe la única sede activa; con dos, se crea igual
	 * (201) con {@code sede_principal_id} NULL. Con sede explícita, la pedida.
	 */
	@Test
	void dosSedesActivas_altaDeAlumno_comportamientoActual() throws Exception {
		assertEquals(d83.sA, sedePrincipal(altaDeAlumno("UNA", null)), "una activa: esa");
		long segunda = crearEn(tGestorD83, "IT E8-2 d83 segunda alumnos " + d83.marca);
		try {
			assertNull(sedePrincipal(altaDeAlumno("SINSEDE", null)), "dos activas: el alumno queda sin sede");
			assertEquals(segunda, sedePrincipal(altaDeAlumno("SEGUNDA", segunda)));
		} finally {
			jdbc.update("UPDATE sede SET activa = FALSE WHERE id = ?", segunda);
		}
		assertEquals(d83.sA, sedePrincipal(altaDeAlumno("OTRAVEZ", null)), "otra vez una activa: esa");
	}

	// ------------------------------------------------------------------ datos

	private void membresia(long organizacion, long usuario, String rol) {
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", usuario, organizacion,
				rol);
	}

	/** Escuela mínima con dos sedes (activas o no) y su propietario. */
	private Mini mini(String nombre, boolean primeraActiva, boolean segundaActiva) {
		String slug = "it-e8-2-" + nombre + "-" + e.marca;
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES (?, 'Escuela IT sedes mínima')", slug);
		long org = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = ?", Long.class, slug);
		List<Long> sedes = new ArrayList<>();
		for (boolean activa : List.of(primeraActiva, segundaActiva)) {
			String n = "IT E8-2 " + nombre + " " + sedes.size() + " " + e.marca;
			jdbc.update("INSERT INTO sede (organizacion_id, nombre, activa) VALUES (?, ?, ?)", org, n, activa);
			sedes.add(jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = ? AND nombre = ?", Long.class, org,
					n));
		}
		long cuenta = e.cuenta(usuarios, "prop-" + nombre);
		membresia(org, cuenta, "PROPIETARIO");
		Usuario usuario = TenantContext.calcularCon(EscuelaConSedesDePrueba.ORG1,
				() -> usuarios.findById(cuenta).orElseThrow());
		return new Mini(org, jwtService.generateToken(usuario, 600_000, org), sedes);
	}

	private long crear(String nombre) throws Exception {
		return crearEn(tPropietario, nombre);
	}

	private long crearEn(String token, String nombre) throws Exception {
		MvcResult r = peticion(conCuerpo(post(RUTA), cuerpo(Map.of("nombre", nombre))), token);
		assertEquals(201, r.getResponse().getStatus(), contenido(r));
		return json.readTree(contenido(r)).get("id").asLong();
	}

	private long altaDeGrupo(String nombre, Long sede) throws Exception {
		MvcResult r = peticion(conCuerpo(post("/api/grupos"), grupo(nombre, sede)), tGestorD83);
		assertEquals(201, r.getResponse().getStatus(), contenido(r));
		return json.readTree(contenido(r)).get("id").asLong();
	}

	private static String grupo(String nombre, Long sede) {
		return "{\"nombre\":\"" + nombre + "\",\"deporte\":\"TAEKWONDO\"" + (sede == null ? "" : ",\"sedeId\":" + sede)
				+ "}";
	}

	private long altaDeAlumno(String sufijo, Long sede) throws Exception {
		String cuerpo = """
				{"nombre":"ALTA D83","apellidos":"PRUEBA %s %s","fechaNacimiento":"1990-03-04","direccion":"Calle 1",
				 "telefono":600000009,"autorizacionWeb":true%s,
				 "deportesInicial":[{"deporte":"TAEKWONDO","fechaAlta":"2024-09-01","tipoTarifa":"ADULTO",
				   "cuantiaTarifa":30.0,"competidor":false,"tieneLicencia":false}]}"""
				.formatted(sufijo, d83.marca, sede == null ? "" : ",\"sedePrincipalId\":" + sede);
		MvcResult r = peticion(multipart("/api/alumnos/crear").param("nuevo", cuerpo), tGestorD83);
		assertEquals(201, r.getResponse().getStatus(), contenido(r));
		long id = json.readTree(contenido(r)).get("id").asLong();
		assertEquals(d83.org, jdbc.queryForObject("SELECT organizacion_id FROM alumno WHERE id = ?", Long.class, id));
		return id;
	}

	private Long sedeDelGrupo(long grupo) {
		return jdbc.queryForObject("SELECT sede_id FROM grupo WHERE id = ?", Long.class, grupo);
	}

	private Long sedePrincipal(long alumno) {
		return jdbc.queryForObject("SELECT sede_principal_id FROM alumno WHERE id = ?", Long.class, alumno);
	}

	private String nombreDe(long sede) {
		return jdbc.queryForObject("SELECT nombre FROM sede WHERE id = ?", String.class, sede);
	}

	private boolean activa(long sede) {
		return Boolean.TRUE.equals(jdbc.queryForObject("SELECT activa FROM sede WHERE id = ?", Boolean.class, sede));
	}

	private long sedesDe(long organizacion) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM sede WHERE organizacion_id = ?", Long.class, organizacion);
	}

	private List<MockHttpServletRequestBuilder> rutas(long sede, String nombre) throws Exception {
		String datos = cuerpo(Map.of("nombre", nombre));
		return List.of(get(RUTA), head(RUTA), get(RUTA + "/" + sede), head(RUTA + "/" + sede),
				conCuerpo(post(RUTA), datos), conCuerpo(put(RUTA + "/" + sede), datos),
				conCuerpo(put(RUTA + "/" + sede + "/activa"), "{\"activa\":false}"));
	}

	private static Map<String, Object> con(String campo, Object valor) {
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("nombre", "IT E8-2 valida");
		datos.put(campo, valor);
		return datos;
	}

	// ------------------------------------------------------------------ auditoría y HTTP

	private long ultimoEvento() {
		return jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM auditoria_evento", Long.class);
	}

	private List<Map<String, Object>> eventosDesde(long id, long organizacion) {
		return jdbc.queryForList("SELECT * FROM auditoria_evento WHERE id > ? AND organizacion_id = ? ORDER BY id", id,
				organizacion);
	}

	private static List<String> resumenes(List<Map<String, Object>> filas) {
		return filas.stream().map(f -> (String) f.get("resumen")).toList();
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

	private String cuerpo(Map<String, ?> datos) throws Exception {
		return json.writeValueAsString(datos);
	}

	private static MockHttpServletRequestBuilder conCuerpo(MockHttpServletRequestBuilder peticion, String cuerpo) {
		return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private JsonNode leer(MockHttpServletRequestBuilder peticion, String token, int esperado) throws Exception {
		MvcResult r = peticion(peticion, token);
		assertEquals(esperado, r.getResponse().getStatus(), contenido(r));
		return json.readTree(contenido(r));
	}

	private int estado(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return peticion(peticion, token).getResponse().getStatus();
	}

	private MvcResult peticion(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
	}

	private void assertMensaje(MvcResult r, int estado, String mensaje) throws Exception {
		assertEquals(estado, r.getResponse().getStatus(), contenido(r));
		assertEquals(mensaje, mensaje(r));
	}

	private String mensaje(MvcResult r) throws Exception {
		JsonNode nodo = json.readTree(contenido(r));
		return nodo.hasNonNull("mensaje") ? nodo.get("mensaje").asText() : "";
	}

	private static String contenido(MvcResult r) throws Exception {
		return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	private static long numero(Map<String, Object> fila, String columna) {
		return ((Number) fila.get(columna)).longValue();
	}

	private static Set<String> campos(JsonNode nodo) {
		Set<String> claves = new HashSet<>();
		for (Iterator<String> it = nodo.fieldNames(); it.hasNext();) {
			claves.add(it.next());
		}
		return claves;
	}

	private static JsonNode sede(JsonNode lista, long id) {
		for (JsonNode n : lista) {
			if (n.get("id").asLong() == id) {
				return n;
			}
		}
		throw new AssertionError("sin la sede " + id + ": " + lista);
	}
}
