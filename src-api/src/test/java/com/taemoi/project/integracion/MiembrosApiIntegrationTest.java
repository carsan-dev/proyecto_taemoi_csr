package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
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
import java.util.concurrent.atomic.AtomicInteger;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GestionMiembrosService;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.MembresiaService;

import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;

/**
 * Pieza E8-3 contra MySQL (D8-1, D8-4 a D8-12, D8-16, D8-19, D8-20 y decisión 221; fase3-e8-miembros.md §6.2 y §7):
 * API de miembros.
 * <ul>
 * <li>Solo propietario y gestor; los miembros de la escuela activa y nada más (cuenta de otra escuela: 404).</li>
 * <li>Lista paginada y con búsqueda en el servidor, sin familias por defecto y sin N+1.</li>
 * <li>Alta de cuentas existentes todo o nada, también con dos altas a la vez; FAMILIA no.</li>
 * <li>D8-8: el gestor no da ni quita propietario ni gestor. D8-9: nunca sin propietario activo, también con dos quitas a
 * la vez y por el endpoint viejo de roles.</li>
 * <li>Sedes del responsable y grupos del profesor solo de la escuela; reemplazo todo o nada.</li>
 * <li>Registro solo con ids y una fila por escritura; los 4xx, la genérica con email y cargo redactados.</li>
 * </ul>
 * Escuelas sintéticas ({@link EscuelaConSedesDePrueba}) y escuelas mínimas por SQL con su propietario; cada test crea
 * sus cuentas (email con la marca de la escuela y un contador) y mira solo sus filas, así que el orden no importa.
 * Nada en Moiskimdo. Datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MiembrosApiIntegrationTest extends MySqlIntegrationTest {

	private static final String RUTA = "/api/miembros";
	private static final String NO_MIEMBRO = "La cuenta no es miembro de esta escuela.";
	private static final String SIN_CUENTA = "No hay ninguna cuenta con ese email.";
	private static final String FAMILIA = "La familia se vincula desde la ficha del alumno.";
	private static final String SOLO_PROPIETARIO = "Solo el propietario da o quita los roles de propietario y gestor.";
	private static final String SIN_PROPIETARIO = "La escuela se quedaría sin propietario.";
	private static final String SEDE_AJENA = "La sede no existe en la escuela.";
	private static final String GRUPO_AJENO = "El grupo no existe.";
	private static final String SIN_SEDES = "Indica al menos una sede.";
	private static final String NO_RESPONSABLE = "La cuenta no es responsable de sede en esta escuela.";
	private static final String SIN_PROFESOR_ACTIVO = "La cuenta no tiene el rol de profesor activo en esta escuela.";
	/** E8-4 añade {@code invitacionPendiente}. */
	private static final Set<String> CLAVES_FILA = Set.of("usuarioId", "nombre", "apellidos", "email", "authProvider",
			"invitacionPendiente", "roles", "sedes", "numeroGrupos");
	private static final Set<String> CLAVES_FICHA = Set.of("usuarioId", "nombre", "apellidos", "email", "authProvider",
			"invitacionPendiente", "roles", "sedes", "numeroGrupos", "grupos");
	/** Resumen de la fila genérica del filtro HTTP ({@code AuditoriaServiceImpl#construirResumen}). */
	private static final String RESUMEN_GENERICO = "^(CREATE|UPDATE|DELETE|READ|OPERACION) \\S+( #\\d+)? -> \\d+$";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;
	@Autowired GestionMiembrosService miembros;
	@Autowired MembresiaService membresiaService;
	@Autowired EntityManagerFactory emf;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	long propietario;
	String tPropietario, tGestor, tFamilia, tRespA, tProfesor, tPlataforma, tPropietarioOtra;
	final AtomicInteger contador = new AtomicInteger();

	/** Escuela mínima por SQL: sus propietarios (el primero, con su token). */
	record Mini(long org, List<Long> propietarios, String token) {
	}

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e8-3");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e8-3-otra");

		propietario = e.cuenta(usuarios, "propietario");
		membresia(e.org, propietario, "PROPIETARIO");
		long familia = e.cuentaFamilia(usuarios, "familia");
		long profesor = e.cuenta(usuarios, "profesor");
		membresia(e.org, profesor, "PROFESOR");
		long plataforma = e.cuenta(usuarios, "plataforma");
		jdbc.update("UPDATE usuario SET es_admin_plataforma = TRUE WHERE id = ?", plataforma);
		long propietarioOtra = otra.cuenta(usuarios, "propietario");
		membresia(otra.org, propietarioOtra, "PROPIETARIO");

		tPropietario = e.token(jwtService, usuarios, propietario);
		tGestor = e.token(jwtService, usuarios, e.gestor);
		tFamilia = e.token(jwtService, usuarios, familia);
		tRespA = e.token(jwtService, usuarios, e.respA);
		tProfesor = e.token(jwtService, usuarios, profesor);
		tPlataforma = jwtService.generateToken(cuentaEnOrg1(plataforma), 600_000, EscuelaConSedesDePrueba.ORG1);
		tPropietarioOtra = otra.token(jwtService, usuarios, propietarioOtra);
	}

	@AfterEach
	void sinFugas() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ lista

	@Test
	void listaPorDefectoSinFamilias() throws Exception {
		String marca = "Lis" + e.marca;
		long profesor = persona(marca, "Ana", "A");
		membresia(e.org, profesor, "PROFESOR");
		long responsableInactivo = persona(marca, "Bea", "B");
		membresiaInactiva(e.org, responsableInactivo, "RESPONSABLE_SEDE");
		long familia = persona(marca, "Carla", "C");
		membresia(e.org, familia, "FAMILIA");
		long mixta = persona(marca, "Dora", "D");
		membresia(e.org, mixta, "FAMILIA");
		membresia(e.org, mixta, "PROFESOR");

		JsonNode pagina = leer(get(RUTA).param("q", marca), tPropietario, 200);
		assertEquals(List.of(profesor, responsableInactivo, mixta), ids(pagina), "personal, también inactivo");
		assertEquals(3, pagina.get("total").asLong());
		for (JsonNode fila : pagina.get("contenido")) {
			assertEquals(CLAVES_FILA, campos(fila));
		}
		JsonNode filaMixta = fila(pagina, mixta);
		assertEquals("PROFESOR:true,FAMILIA:true", roles(filaMixta), "orden del enum");
		assertEquals("RESPONSABLE_SEDE:false", roles(fila(pagina, responsableInactivo)));
		assertEquals("LOCAL", filaMixta.get("authProvider").asText());
		assertEquals(emailDe(mixta), filaMixta.get("email").asText());
		assertEquals(200, estado(head(RUTA).param("q", marca), tPropietario));
	}

	@Test
	void listaConFamilias() throws Exception {
		String marca = "Fam" + e.marca;
		long profesor = persona(marca, "Ana", "A");
		membresia(e.org, profesor, "PROFESOR");
		long familia = persona(marca, "Bea", "B");
		membresia(e.org, familia, "FAMILIA");
		long familiaInactiva = persona(marca, "Carla", "C");
		membresiaInactiva(e.org, familiaInactiva, "FAMILIA");

		JsonNode pagina = leer(get(RUTA).param("q", marca).param("incluirFamilias", "true"), tPropietario, 200);
		assertEquals(List.of(profesor, familia, familiaInactiva), ids(pagina));
		assertEquals("FAMILIA:true", roles(fila(pagina, familia)));
		assertEquals(List.of(profesor), ids(leer(get(RUTA).param("q", marca).param("incluirFamilias", "false"),
				tPropietario, 200)));
	}

	@Test
	void filtroPorRol() throws Exception {
		String marca = "Rol" + e.marca;
		long profesor = persona(marca, "Ana", "A");
		membresia(e.org, profesor, "PROFESOR");
		long profesorInactivo = persona(marca, "Bea", "B");
		membresiaInactiva(e.org, profesorInactivo, "PROFESOR");
		long gestor = persona(marca, "Carla", "C");
		membresia(e.org, gestor, "ADMINISTRADOR");
		long familiaProfesor = persona(marca, "Dora", "D");
		membresia(e.org, familiaProfesor, "FAMILIA");
		membresia(e.org, familiaProfesor, "PROFESOR");
		long familia = persona(marca, "Eva", "E");
		membresia(e.org, familia, "FAMILIA");

		assertEquals(List.of(profesor, profesorInactivo, familiaProfesor),
				ids(leer(get(RUTA).param("q", marca).param("rol", "PROFESOR"), tPropietario, 200)));
		assertEquals(List.of(gestor), ids(leer(get(RUTA).param("q", marca).param("rol", "ADMINISTRADOR"), tPropietario,
				200)));
		assertEquals(List.of(familiaProfesor), ids(leer(get(RUTA).param("q", marca).param("rol", "FAMILIA"),
				tPropietario, 200)), "sin incluirFamilias, el personal que también es familia");
		assertEquals(List.of(familiaProfesor, familia), ids(leer(get(RUTA).param("q", marca).param("rol", "FAMILIA")
				.param("incluirFamilias", "true"), tPropietario, 200)));
		assertMensaje(peticion(get(RUTA).param("rol", "JEFE"), tPropietario), 400, "El rol no es válido.");
	}

	@Test
	void busquedaYPaginacionEnServidor() throws Exception {
		String marca = "Pag" + e.marca;
		List<Long> todas = new ArrayList<>();
		for (int i = 1; i <= 25; i++) {
			long cuenta = persona(marca, "Nombre" + i, String.format("%02d", i));
			membresia(e.org, cuenta, i % 2 == 0 ? "PROFESOR" : "RESPONSABLE_SEDE");
			todas.add(cuenta);
		}
		JsonNode primera = leer(get(RUTA).param("q", marca.toUpperCase()).param("size", "10"), tPropietario, 200);
		assertEquals(todas.subList(0, 10), ids(primera), "orden por apellidos; búsqueda sin mayúsculas");
		assertEquals(25, primera.get("total").asLong());
		assertEquals(3, primera.get("totalPaginas").asInt());
		assertEquals(0, primera.get("pagina").asInt());
		assertEquals(10, primera.get("tamano").asInt());
		assertEquals(todas.subList(20, 25), ids(leer(get(RUTA).param("q", marca).param("page", "2").param("size", "10"),
				tPropietario, 200)));
		assertEquals(20, leer(get(RUTA).param("q", marca), tPropietario, 200).get("tamano").asInt(), "20 por defecto");

		// Por nombre y apellidos juntos, y por el email.
		long quinta = todas.get(4);
		assertEquals(List.of(quinta), ids(leer(get(RUTA).param("q", "nombre5 " + marca + " 05"), tPropietario, 200)));
		assertEquals(List.of(quinta), ids(leer(get(RUTA).param("q", " " + emailDe(quinta).toUpperCase() + " "),
				tPropietario, 200)));
		// Comodines literales.
		assertEquals(0, leer(get(RUTA).param("q", marca + "%"), tPropietario, 200).get("total").asLong());
		assertEquals(0, leer(get(RUTA).param("q", marca.substring(0, 2) + "_"), tPropietario, 200).get("total")
				.asLong());

		for (String[] mal : List.of(new String[] { "size", "0" }, new String[] { "size", "101" },
				new String[] { "page", "-1" }, new String[] { "q", "x".repeat(101) })) {
			assertEquals(400, estado(get(RUTA).param(mal[0], mal[1]), tPropietario), mal[0] + "=" + mal[1]);
		}
		// page × size por encima de Integer.MAX_VALUE: 400 con mensaje, nunca 500.
		assertMensaje(peticion(get(RUTA).param("page", "30000000").param("size", "100"), tPropietario), 400,
				"La página no es válida.");

		// Sin N+1: las sentencias de una página de 25 filas, las mismas que las de una de 1.
		long una = sentencias(get(RUTA).param("q", marca).param("size", "1"));
		assertTrue(una > 0, "las estadísticas cuentan");
		assertEquals(una, sentencias(get(RUTA).param("q", marca).param("size", "25")));
	}

	@Test
	void miembrosNoSeVenEntreEscuelas() throws Exception {
		String marca = "Esc" + e.marca;
		long ajena = persona(marca, "Ana", "A");
		membresia(otra.org, ajena, "PROFESOR");
		long enLasDos = persona(marca, "Bea", "B");
		membresia(e.org, enLasDos, "PROFESOR");
		membresia(otra.org, enLasDos, "ADMINISTRADOR");
		membresia(otra.org, enLasDos, "RESPONSABLE_SEDE");
		jdbc.update("INSERT INTO membresia_sede (membresia_id, sede_id) VALUES (?, ?)",
				membresiaId(enLasDos, otra.org, "RESPONSABLE_SEDE"), otra.sA);
		asignacion(otra.org, enLasDos, otra.gA, "TITULAR");

		JsonNode deE = leer(get(RUTA).param("q", marca).param("incluirFamilias", "true"), tPropietario, 200);
		assertEquals(List.of(enLasDos), ids(deE));
		JsonNode fila = fila(deE, enLasDos);
		assertEquals("PROFESOR:true", roles(fila), "solo sus roles en esta escuela");
		assertEquals(0, fila.get("sedes").size());
		assertEquals(0, fila.get("numeroGrupos").asInt());
		JsonNode ficha = leer(get(RUTA + "/" + enLasDos), tPropietario, 200);
		assertEquals(0, ficha.get("grupos").size());

		JsonNode deOtra = leer(get(RUTA).param("q", marca), tPropietarioOtra, 200);
		assertEquals(List.of(ajena, enLasDos), ids(deOtra));
		assertEquals("ADMINISTRADOR:true,RESPONSABLE_SEDE:true", roles(fila(deOtra, enLasDos)));
		assertEquals(1, fila(deOtra, enLasDos).get("numeroGrupos").asInt());
	}

	// ------------------------------------------------------------------ ficha

	@Test
	void fichaDeCuentaDeOtraEscuela_404() throws Exception {
		long ajena = cuenta("ajena");
		membresia(otra.org, ajena, "PROFESOR");
		membresia(otra.org, ajena, "RESPONSABLE_SEDE");
		long sinNada = cuenta("sin-nada");
		for (long id : List.of(ajena, sinNada, 999_999_999L)) {
			assertMensaje(peticion(get(RUTA + "/" + id), tPropietario), 404, NO_MIEMBRO);
			assertMensaje(peticion(conCuerpo(put(RUTA + "/" + id + "/roles/PROFESOR"), "{}"), tPropietario), 404,
					NO_MIEMBRO);
			assertMensaje(peticion(delete(RUTA + "/" + id + "/roles/PROFESOR"), tPropietario), 404, NO_MIEMBRO);
			assertMensaje(peticion(conCuerpo(put(RUTA + "/" + id + "/sedes"), "{\"sedeIds\":[" + e.sA + "]}"),
					tPropietario), 404, NO_MIEMBRO);
			assertMensaje(peticion(conCuerpo(put(RUTA + "/" + id + "/grupos"),
					"{\"asignaciones\":[{\"grupoId\":" + e.gA + ",\"rol\":\"TITULAR\"}]}"), tPropietario), 404,
					NO_MIEMBRO);
		}
		assertEquals(List.of(), rolesActivos(ajena, e.org), "nada se crea en esta escuela");
		assertEquals(List.of("RESPONSABLE_SEDE", "PROFESOR"), rolesActivos(ajena, otra.org), "ni cambia en la otra");
		assertEquals(0, asignaciones(ajena, e.org).size());
	}

	@Test
	void fichaConGruposYSedes() throws Exception {
		long cuenta = cuenta("ficha");
		membresia(e.org, cuenta, "RESPONSABLE_SEDE");
		jdbc.update("INSERT INTO membresia_sede (membresia_id, sede_id) VALUES (?, ?), (?, ?)",
				membresiaId(cuenta, e.org, "RESPONSABLE_SEDE"), e.sB, membresiaId(cuenta, e.org, "RESPONSABLE_SEDE"),
				e.sA);
		membresia(e.org, cuenta, "PROFESOR");
		jdbc.update("UPDATE membresia SET cargo = 'Técnico inventado' WHERE usuario_id = ? AND organizacion_id = ? "
				+ "AND rol = 'PROFESOR'", cuenta, e.org);
		asignacion(e.org, cuenta, e.gA, "TITULAR");
		asignacion(e.org, cuenta, e.gB, "AYUDANTE");

		JsonNode ficha = leer(get(RUTA + "/" + cuenta), tPropietario, 200);
		assertEquals(CLAVES_FICHA, campos(ficha));
		assertEquals(cuenta, ficha.get("usuarioId").asLong());
		assertEquals("RESPONSABLE_SEDE:true,PROFESOR:true", roles(ficha));
		JsonNode rolProfesor = ficha.get("roles").get(1);
		assertEquals("Técnico inventado", rolProfesor.get("cargo").asText());
		assertTrue(ficha.get("roles").get(0).get("cargo").isNull());
		assertEquals(List.of(e.sA, e.sB), idsDe(ficha.get("sedes"), "id"), "por id");
		assertEquals(nombreSede(e.sA), ficha.get("sedes").get(0).get("nombre").asText());
		assertEquals(2, ficha.get("numeroGrupos").asInt());
		Map<Long, String> grupos = new LinkedHashMap<>();
		for (JsonNode g : ficha.get("grupos")) {
			grupos.put(g.get("grupoId").asLong(), g.get("rol").asText() + "@" + g.get("sedeId").asLong());
			assertEquals(Set.of("grupoId", "nombre", "sedeId", "rol"), campos(g));
		}
		assertEquals(Map.of(e.gA, "TITULAR@" + e.sA, e.gB, "AYUDANTE@" + e.sB), grupos);
		assertEquals(200, estado(head(RUTA + "/" + cuenta), tPropietario));
	}

	// ------------------------------------------------------------------ alta

	@Test
	void altaCuentaExistente_profesorConVariosGrupos_todoONada() throws Exception {
		long cuenta = cuenta("alta-prof");
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("email", "  " + emailDe(cuenta).toUpperCase() + " ");
		datos.put("rol", "PROFESOR");
		datos.put("cargo", "  Director técnico ");
		datos.put("grupos", List.of(asig(e.gA, "TITULAR"), asig(e.gB, "AYUDANTE")));

		long antes = ultimoEvento();
		JsonNode ficha = leer(conCuerpo(post(RUTA), cuerpo(datos)), tPropietario, 200);
		assertEquals(CLAVES_FICHA, campos(ficha));
		assertEquals("PROFESOR:true", roles(ficha));
		assertEquals("Director técnico", ficha.get("roles").get(0).get("cargo").asText(), "recortado");
		assertEquals(Map.of(e.gA, "TITULAR", e.gB, "AYUDANTE"), asignaciones(cuenta, e.org));
		assertEquals(List.of("MIEMBROS ROL_DADO usuario=#" + cuenta + " rol=PROFESOR",
				"MIEMBROS CARGO_CAMBIADO usuario=#" + cuenta + " rol=PROFESOR",
				"PROFESOR ASIGNACION_CREADA grupo=#" + e.gA + " usuario=#" + cuenta + " rol=TITULAR",
				"PROFESOR ASIGNACION_CREADA grupo=#" + e.gB + " usuario=#" + cuenta + " rol=AYUDANTE"),
				resumenes(eventosDesde(antes, e.org)));

		// Otra alta suma: cambia el rol de gB, añade gA2 y gA sigue.
		datos.remove("cargo");
		datos.put("grupos", List.of(asig(e.gB, "TITULAR"), asig(e.gA2, "AYUDANTE")));
		antes = ultimoEvento();
		leer(conCuerpo(post(RUTA), cuerpo(datos)), tPropietario, 200);
		assertEquals(Map.of(e.gA, "TITULAR", e.gB, "TITULAR", e.gA2, "AYUDANTE"), asignaciones(cuenta, e.org));
		assertEquals("Director técnico", cargo(cuenta, e.org, "PROFESOR"), "sin cargo en el cuerpo: se conserva");
		assertEquals(List.of("PROFESOR ASIGNACION_ROL_CAMBIADO grupo=#" + e.gB + " usuario=#" + cuenta + " rol=TITULAR",
				"PROFESOR ASIGNACION_CREADA grupo=#" + e.gA2 + " usuario=#" + cuenta + " rol=AYUDANTE"),
				resumenes(eventosDesde(antes, e.org)));

		// Igual otra vez: 200 y una fila «sin cambios».
		antes = ultimoEvento();
		leer(conCuerpo(post(RUTA), cuerpo(datos)), tPropietario, 200);
		assertEquals(List.of("MIEMBROS ROL_DADO usuario=#" + cuenta + " rol=PROFESOR sinCambios=SI"),
				resumenes(eventosDesde(antes, e.org)));
		assertEquals(1, membresias(cuenta, e.org, "PROFESOR"));
	}

	@Test
	void altaConUnGrupoAjeno_noCreaNada() throws Exception {
		long cuenta = cuenta("alta-ajeno");
		for (long ajeno : List.of(otra.gA, 999_999_999L)) {
			long antes = ultimoEvento();
			assertMensaje(peticion(conCuerpo(post(RUTA), cuerpo(alta(cuenta, "PROFESOR", "grupos",
					List.of(asig(e.gA, "TITULAR"), asig(ajeno, "TITULAR"))))), tPropietario), 404, GRUPO_AJENO);
			assertEquals(0, membresias(cuenta, e.org, "PROFESOR"), "sin membresía");
			assertEquals(Map.of(), asignaciones(cuenta, e.org), "sin asignaciones");
			assertTrue(resumenes(eventosDesde(antes, e.org)).stream().allMatch(r -> r.matches(RESUMEN_GENERICO)));
		}
		assertEquals(Map.of(), asignaciones(cuenta, otra.org), "ni en la otra escuela");

		// Profesor ya activo: tampoco se añade el grupo válido.
		long profesor = cuenta("alta-ajeno-prof");
		membresia(e.org, profesor, "PROFESOR");
		asignacion(e.org, profesor, e.gA, "TITULAR");
		assertEquals(404, estado(conCuerpo(post(RUTA), cuerpo(alta(profesor, "PROFESOR", "grupos",
				List.of(asig(e.gA2, "TITULAR"), asig(otra.gB, "AYUDANTE"))))), tPropietario));
		assertEquals(Map.of(e.gA, "TITULAR"), asignaciones(profesor, e.org));
	}

	@Test
	void altaResponsableConSedes() throws Exception {
		long cuenta = cuenta("alta-resp");
		assertMensaje(peticion(conCuerpo(post(RUTA), cuerpo(alta(cuenta, "RESPONSABLE_SEDE", null, null))),
				tPropietario), 400, SIN_SEDES);
		assertMensaje(peticion(conCuerpo(post(RUTA), cuerpo(alta(cuenta, "RESPONSABLE_SEDE", "sedeIds",
				List.of(e.sA, otra.sA)))), tPropietario), 400, SEDE_AJENA);
		assertEquals(0, membresias(cuenta, e.org, "RESPONSABLE_SEDE"));

		long antes = ultimoEvento();
		JsonNode ficha = leer(conCuerpo(post(RUTA), cuerpo(alta(cuenta, "RESPONSABLE_SEDE", "sedeIds",
				List.of(e.sC, e.sA)))), tPropietario, 200);
		assertEquals("RESPONSABLE_SEDE:true", roles(ficha));
		assertEquals(List.of(e.sA, e.sC), idsDe(ficha.get("sedes"), "id"), "también una sede inactiva");
		assertEquals(Set.of(e.sA, e.sC), sedesDe(cuenta, e.org));
		assertEquals(List.of("MIEMBROS ROL_DADO usuario=#" + cuenta + " rol=RESPONSABLE_SEDE",
				"MIEMBROS SEDES_CAMBIADAS usuario=#" + cuenta + " sedes=#" + Math.min(e.sA, e.sC) + ",#"
						+ Math.max(e.sA, e.sC)), resumenes(eventosDesde(antes, e.org)));

		// Ya activo: sin sedes, se conservan; con sedes, se reemplazan.
		leer(conCuerpo(post(RUTA), cuerpo(alta(cuenta, "RESPONSABLE_SEDE", null, null))), tPropietario, 200);
		assertEquals(Set.of(e.sA, e.sC), sedesDe(cuenta, e.org));
		leer(conCuerpo(post(RUTA), cuerpo(alta(cuenta, "RESPONSABLE_SEDE", "sedeIds", List.of(e.sB)))), tPropietario,
				200);
		assertEquals(Set.of(e.sB), sedesDe(cuenta, e.org));
	}

	@Test
	void altaSinCuenta_404() throws Exception {
		String email = "nadie-" + e.marca + "@example.test";
		assertMensaje(peticion(conCuerpo(post(RUTA), cuerpo(Map.of("email", email, "rol", "PROFESOR"))), tPropietario),
				404, SIN_CUENTA);
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE email = ?", Long.class, email));
	}

	@Test
	void darFamilia_400() throws Exception {
		long cuenta = cuenta("familia-no");
		membresia(e.org, cuenta, "PROFESOR");
		assertMensaje(peticion(conCuerpo(post(RUTA), cuerpo(alta(cuenta, "FAMILIA", null, null))), tPropietario), 400,
				FAMILIA);
		assertMensaje(peticion(conCuerpo(put(RUTA + "/" + cuenta + "/roles/FAMILIA"), "{}"), tPropietario), 400,
				FAMILIA);
		long familia = cuenta("familia-no-quitar");
		membresia(e.org, familia, "FAMILIA");
		assertMensaje(peticion(delete(RUTA + "/" + familia + "/roles/FAMILIA"), tPropietario), 400, FAMILIA);
		assertEquals(List.of("PROFESOR"), rolesActivos(cuenta, e.org));
		assertEquals(List.of("FAMILIA"), rolesActivos(familia, e.org));
	}

	@Test
	void validaciones_400() throws Exception {
		long cuenta = cuenta("validar");
		membresia(e.org, cuenta, "PROFESOR");
		String email = emailDe(cuenta);
		List<Map<String, Object>> malas = new ArrayList<>();
		malas.add(Map.of("rol", "PROFESOR"));
		malas.add(Map.of("email", "  ", "rol", "PROFESOR"));
		malas.add(Map.of("email", email));
		malas.add(Map.of("email", email, "rol", "JEFE"));
		malas.add(Map.of("email", email, "rol", "PROFESOR", "cargo", "x".repeat(101)));
		malas.add(Map.of("email", email, "rol", "PROFESOR", "sedeIds", List.of(e.sA)));
		malas.add(Map.of("email", email, "rol", "RESPONSABLE_SEDE", "sedeIds", List.of(e.sA), "grupos",
				List.of(asig(e.gA, "TITULAR"))));
		malas.add(Map.of("email", email, "rol", "PROFESOR", "grupos", List.of(asig(e.gA, "TITULAR"),
				asig(e.gA, "AYUDANTE"))));
		malas.add(Map.of("email", email, "rol", "PROFESOR", "grupos", List.of(asig(e.gA, "JEFE"))));
		malas.add(Map.of("email", email, "rol", "RESPONSABLE_SEDE", "sedeIds", List.of(e.sA, e.sA)));
		for (Map<String, Object> mala : malas) {
			assertEquals(400, estado(conCuerpo(post(RUTA), cuerpo(mala)), tPropietario), mala.toString());
		}
		assertEquals(400, estado(conCuerpo(post(RUTA), "{\"email\":\"" + email
				+ "\",\"rol\":\"RESPONSABLE_SEDE\",\"sedeIds\":[null]}"), tPropietario));
		assertEquals(400, estado(conCuerpo(put(RUTA + "/" + cuenta + "/roles/JEFE"), "{}"), tPropietario));
		assertEquals(400, estado(delete(RUTA + "/" + cuenta + "/roles/profesor"), tPropietario));
		assertEquals(400, estado(conCuerpo(put(RUTA + "/" + cuenta + "/roles/PROFESOR"),
				cuerpo(Map.of("cargo", "x".repeat(101)))), tPropietario));
		assertEquals(400, estado(conCuerpo(put(RUTA + "/" + cuenta + "/roles/PROFESOR"),
				cuerpo(Map.of("sedeIds", List.of(e.sA)))), tPropietario), "sedes solo para el responsable");
		assertEquals(List.of("PROFESOR"), rolesActivos(cuenta, e.org));
		assertEquals(Map.of(), asignaciones(cuenta, e.org));
		assertNull(cargo(cuenta, e.org, "PROFESOR"));
	}

	@Test
	void rolDesactivadoSinReactivar_409() throws Exception {
		Map<String, String> enPantalla = Map.of("PROFESOR", "profesor", "RESPONSABLE_SEDE", "responsable de sede",
				"ADMINISTRADOR", "gestor", "PROPIETARIO", "propietario");
		for (Map.Entry<String, String> rol : enPantalla.entrySet()) {
			long cuenta = cuenta("desactivado-" + rol.getKey().toLowerCase());
			membresiaInactiva(e.org, cuenta, rol.getKey());
			Map<String, Object> datos = alta(cuenta, rol.getKey(), null, null);
			if (rol.getKey().equals("PROFESOR")) {
				datos.put("grupos", List.of(asig(e.gA, "TITULAR")));
			}
			if (rol.getKey().equals("RESPONSABLE_SEDE")) {
				datos.put("sedeIds", List.of(e.sA));
			}
			datos.put("reactivar", false);
			assertMensaje(peticion(conCuerpo(post(RUTA), cuerpo(datos)), tPropietario), 409,
					"La cuenta tiene el rol de " + rol.getValue() + " desactivado en esta escuela.");
			assertEquals(List.of(), rolesActivos(cuenta, e.org));
			assertEquals(Map.of(), asignaciones(cuenta, e.org));
			assertEquals(Set.of(), sedesDe(cuenta, e.org));
		}
	}

	@Test
	void reactivar_ok() throws Exception {
		long cuenta = cuenta("reactivar");
		membresiaInactiva(e.org, cuenta, "PROFESOR");
		Map<String, Object> datos = alta(cuenta, "PROFESOR", "grupos", List.of(asig(e.gA, "AYUDANTE")));
		datos.put("reactivar", true);
		long antes = ultimoEvento();
		JsonNode ficha = leer(conCuerpo(post(RUTA), cuerpo(datos)), tPropietario, 200);
		assertEquals("PROFESOR:true", roles(ficha));
		assertEquals(1, membresias(cuenta, e.org, "PROFESOR"), "la misma fila");
		assertEquals(Map.of(e.gA, "AYUDANTE"), asignaciones(cuenta, e.org));
		assertEquals(List.of("MIEMBROS ROL_REACTIVADO usuario=#" + cuenta + " rol=PROFESOR",
				"PROFESOR ASIGNACION_CREADA grupo=#" + e.gA + " usuario=#" + cuenta + " rol=AYUDANTE"),
				resumenes(eventosDesde(antes, e.org)));

		long responsable = cuenta("reactivar-resp");
		membresiaInactiva(e.org, responsable, "RESPONSABLE_SEDE");
		Map<String, Object> sinSedes = alta(responsable, "RESPONSABLE_SEDE", null, null);
		sinSedes.put("reactivar", true);
		assertMensaje(peticion(conCuerpo(post(RUTA), cuerpo(sinSedes)), tPropietario), 400, SIN_SEDES);
		sinSedes.put("sedeIds", List.of(e.sB));
		leer(conCuerpo(post(RUTA), cuerpo(sinSedes)), tPropietario, 200);
		assertEquals(List.of("RESPONSABLE_SEDE"), rolesActivos(responsable, e.org));
		assertEquals(Set.of(e.sB), sedesDe(responsable, e.org));
	}

	@Test
	void dosAltasALaVezMismoEmail_sin500() throws Exception {
		for (int ronda = 0; ronda < 4; ronda++) {
			long cuenta = cuenta("a-la-vez-" + ronda);
			String datos = cuerpo(alta(cuenta, "PROFESOR", "grupos", List.of(asig(e.gA, "TITULAR"),
					asig(e.gB, "AYUDANTE"))));
			List<Integer> estados = enParalelo(() -> estado(conCuerpo(post(RUTA), datos), tPropietario),
					() -> estado(conCuerpo(post(RUTA), datos), tPropietario));
			assertEquals(List.of(200, 200), estados, "ronda " + ronda);
			assertEquals(1, membresias(cuenta, e.org, "PROFESOR"), "ronda " + ronda);
			assertEquals(Map.of(e.gA, "TITULAR", e.gB, "AYUDANTE"), asignaciones(cuenta, e.org), "ronda " + ronda);
		}
	}

	// ------------------------------------------------------------------ D8-8

	@Test
	void gestorDaPropietario_403() throws Exception {
		long cuenta = cuenta("gestor-da");
		membresia(e.org, cuenta, "FAMILIA");
		long inactivo = cuenta("gestor-reactiva");
		membresiaInactiva(e.org, inactivo, "ADMINISTRADOR");
		for (String rol : List.of("PROPIETARIO", "ADMINISTRADOR")) {
			assertMensaje(peticion(conCuerpo(post(RUTA), cuerpo(alta(cuenta, rol, null, null))), tGestor), 403,
					SOLO_PROPIETARIO);
			assertMensaje(peticion(conCuerpo(put(RUTA + "/" + cuenta + "/roles/" + rol), "{}"), tGestor), 403,
					SOLO_PROPIETARIO);
		}
		Map<String, Object> reactivar = alta(inactivo, "ADMINISTRADOR", null, null);
		reactivar.put("reactivar", true);
		assertMensaje(peticion(conCuerpo(post(RUTA), cuerpo(reactivar)), tGestor), 403, SOLO_PROPIETARIO);
		assertMensaje(peticion(conCuerpo(put(RUTA + "/" + inactivo + "/roles/ADMINISTRADOR"),
				cuerpo(Map.of("cargo", "Gestión"))), tGestor), 403, SOLO_PROPIETARIO);
		assertEquals(List.of("FAMILIA"), rolesActivos(cuenta, e.org));
		assertEquals(List.of(), rolesActivos(inactivo, e.org));
		assertNull(cargo(inactivo, e.org, "ADMINISTRADOR"));

		// El propietario sí.
		leer(conCuerpo(put(RUTA + "/" + cuenta + "/roles/ADMINISTRADOR"), "{}"), tPropietario, 200);
		leer(conCuerpo(post(RUTA), cuerpo(alta(cuenta, "PROPIETARIO", null, null))), tPropietario, 200);
		assertEquals(List.of("PROPIETARIO", "ADMINISTRADOR", "FAMILIA"), rolesActivos(cuenta, e.org));
	}

	@Test
	void gestorQuitaAdministrador_403() throws Exception {
		long gestor = cuenta("quitado-gestor");
		membresia(e.org, gestor, "ADMINISTRADOR");
		long dueno = cuenta("quitado-dueno");
		membresia(e.org, dueno, "PROPIETARIO");
		assertMensaje(peticion(delete(RUTA + "/" + gestor + "/roles/ADMINISTRADOR"), tGestor), 403, SOLO_PROPIETARIO);
		assertMensaje(peticion(delete(RUTA + "/" + dueno + "/roles/PROPIETARIO"), tGestor), 403, SOLO_PROPIETARIO);
		assertMensaje(peticion(delete(RUTA + "/" + e.gestor + "/roles/ADMINISTRADOR"), tGestor), 403,
				SOLO_PROPIETARIO);
		assertEquals(List.of("ADMINISTRADOR"), rolesActivos(gestor, e.org));
		assertEquals(List.of("PROPIETARIO"), rolesActivos(dueno, e.org));

		leer(delete(RUTA + "/" + gestor + "/roles/ADMINISTRADOR"), tPropietario, 200);
		leer(delete(RUTA + "/" + dueno + "/roles/PROPIETARIO"), tPropietario, 200);
		assertEquals(List.of(), rolesActivos(gestor, e.org));
		assertEquals(List.of(), rolesActivos(dueno, e.org));
	}

	@Test
	void gestorDaProfesorYResponsable() throws Exception {
		long cuenta = cuenta("del-gestor");
		long antes = ultimoEvento();
		leer(conCuerpo(post(RUTA), cuerpo(alta(cuenta, "PROFESOR", "grupos", List.of(asig(e.gA, "TITULAR"))))),
				tGestor, 200);
		leer(conCuerpo(post(RUTA), cuerpo(alta(cuenta, "RESPONSABLE_SEDE", "sedeIds", List.of(e.sA)))), tGestor, 200);
		leer(conCuerpo(put(RUTA + "/" + cuenta + "/sedes"), "{\"sedeIds\":[" + e.sB + "]}"), tGestor, 200);
		leer(conCuerpo(put(RUTA + "/" + cuenta + "/grupos"),
				"{\"asignaciones\":[{\"grupoId\":" + e.gB + ",\"rol\":\"AYUDANTE\"}]}"), tGestor, 200);
		leer(conCuerpo(put(RUTA + "/" + cuenta + "/roles/PROFESOR"), cuerpo(Map.of("cargo", "Monitor"))), tGestor, 200);
		leer(delete(RUTA + "/" + cuenta + "/roles/RESPONSABLE_SEDE"), tGestor, 200);
		assertEquals(List.of("PROFESOR"), rolesActivos(cuenta, e.org));
		assertEquals(Set.of(e.sB), sedesDe(cuenta, e.org), "quitar el rol conserva sus sedes");
		assertEquals(Map.of(e.gB, "AYUDANTE"), asignaciones(cuenta, e.org));
		assertEquals("Monitor", cargo(cuenta, e.org, "PROFESOR"));
		List<Map<String, Object>> filas = eventosDesde(antes, e.org);
		assertFalse(filas.isEmpty());
		for (Map<String, Object> fila : filas) {
			assertEquals(e.gestor, numero(fila, "usuario_id"), fila.toString());
		}
	}

	@Test
	void gestorCambiaCargoDeUnGestorActivo() throws Exception {
		long cuenta = cuenta("cargo-gestor");
		membresia(e.org, cuenta, "ADMINISTRADOR");
		JsonNode ficha = leer(conCuerpo(put(RUTA + "/" + cuenta + "/roles/ADMINISTRADOR"),
				cuerpo(Map.of("cargo", "Coordinación"))), tGestor, 200);
		assertEquals("Coordinación", ficha.get("roles").get(0).get("cargo").asText());
		assertEquals("Coordinación", cargo(cuenta, e.org, "ADMINISTRADOR"));
	}

	// ------------------------------------------------------------------ dar y quitar por la ruta del rol

	@Test
	void darRolPorPut_yCargo() throws Exception {
		long cuenta = cuenta("put-rol");
		membresia(e.org, cuenta, "FAMILIA");
		String ruta = RUTA + "/" + cuenta + "/roles/PROFESOR";

		long antes = ultimoEvento();
		JsonNode ficha = leer(conCuerpo(put(ruta), cuerpo(Map.of("cargo", "Técnico"))), tPropietario, 200);
		assertEquals("PROFESOR:true,FAMILIA:true", roles(ficha));
		assertEquals(List.of("MIEMBROS ROL_DADO usuario=#" + cuenta + " rol=PROFESOR",
				"MIEMBROS CARGO_CAMBIADO usuario=#" + cuenta + " rol=PROFESOR"), resumenes(eventosDesde(antes, e.org)));

		antes = ultimoEvento();
		leer(put(ruta), tPropietario, 200);
		leer(conCuerpo(put(ruta), "{\"cargo\":null}"), tPropietario, 200);
		assertEquals("Técnico", cargo(cuenta, e.org, "PROFESOR"), "ausente o null: se conserva");
		assertEquals(List.of("MIEMBROS ROL_DADO usuario=#" + cuenta + " rol=PROFESOR sinCambios=SI",
				"MIEMBROS ROL_DADO usuario=#" + cuenta + " rol=PROFESOR sinCambios=SI"),
				resumenes(eventosDesde(antes, e.org)));

		antes = ultimoEvento();
		leer(conCuerpo(put(ruta), "{\"cargo\":\"  \"}"), tPropietario, 200);
		assertNull(cargo(cuenta, e.org, "PROFESOR"), "en blanco: se borra");
		assertEquals(List.of("MIEMBROS CARGO_CAMBIADO usuario=#" + cuenta + " rol=PROFESOR"),
				resumenes(eventosDesde(antes, e.org)));

		// Quitar y volver a dar: la misma fila, reactivada.
		antes = ultimoEvento();
		leer(delete(ruta), tPropietario, 200);
		leer(delete(ruta), tPropietario, 200);
		leer(put(ruta), tPropietario, 200);
		assertEquals(List.of("MIEMBROS ROL_QUITADO usuario=#" + cuenta + " rol=PROFESOR",
				"MIEMBROS ROL_QUITADO usuario=#" + cuenta + " rol=PROFESOR sinCambios=SI",
				"MIEMBROS ROL_REACTIVADO usuario=#" + cuenta + " rol=PROFESOR"), resumenes(eventosDesde(antes, e.org)));
		assertEquals(1, membresias(cuenta, e.org, "PROFESOR"));
		assertMensaje(peticion(delete(RUTA + "/" + cuenta + "/roles/RESPONSABLE_SEDE"), tPropietario), 404,
				"La cuenta no tiene ese rol en esta escuela.");
	}

	@Test
	void responsableSinSedesPorPut_400() throws Exception {
		long cuenta = cuenta("put-resp");
		membresia(e.org, cuenta, "FAMILIA");
		String ruta = RUTA + "/" + cuenta + "/roles/RESPONSABLE_SEDE";
		assertMensaje(peticion(conCuerpo(put(ruta), "{}"), tPropietario), 400, SIN_SEDES);
		assertMensaje(peticion(conCuerpo(put(ruta), "{\"sedeIds\":[]}"), tPropietario), 400, SIN_SEDES);
		assertMensaje(peticion(conCuerpo(put(ruta), "{\"sedeIds\":[" + otra.sA + "]}"), tPropietario), 400, SEDE_AJENA);
		assertEquals(0, membresias(cuenta, e.org, "RESPONSABLE_SEDE"));

		JsonNode ficha = leer(conCuerpo(put(ruta), "{\"sedeIds\":[" + e.sA + "]}"), tPropietario, 200);
		assertEquals(List.of(e.sA), idsDe(ficha.get("sedes"), "id"));
		leer(delete(ruta), tPropietario, 200);
		assertEquals(Set.of(e.sA), sedesDe(cuenta, e.org), "desactivar conserva sus sedes");
		// Reactivar sin sedes en el cuerpo: tiene las suyas guardadas.
		leer(put(ruta), tPropietario, 200);
		assertEquals(List.of("RESPONSABLE_SEDE", "FAMILIA"), rolesActivos(cuenta, e.org));
		assertEquals(Set.of(e.sA), sedesDe(cuenta, e.org));
	}

	// ------------------------------------------------------------------ último propietario (D8-9, decisión 221)

	@Test
	void quitarUltimoPropietario_409() throws Exception {
		Mini mini = mini("ultimo", 2);
		long a = mini.propietarios().get(0);
		long b = mini.propietarios().get(1);
		jdbc.update("UPDATE membresia SET activa = FALSE WHERE usuario_id = ? AND organizacion_id = ?", b, mini.org());
		assertMensaje(peticion(delete(RUTA + "/" + a + "/roles/PROPIETARIO"), mini.token()), 409, SIN_PROPIETARIO);
		assertEquals(List.of("PROPIETARIO"), rolesActivos(a, mini.org()));

		// Con otro propietario activo sí (también quitarse a sí mismo); después, el que queda no.
		leer(conCuerpo(put(RUTA + "/" + b + "/roles/PROPIETARIO"), "{}"), mini.token(), 200);
		leer(delete(RUTA + "/" + a + "/roles/PROPIETARIO"), mini.token(), 200);
		assertEquals(List.of(), rolesActivos(a, mini.org()));
		String tokenB = tokenEn(b, mini.org());
		assertMensaje(peticion(delete(RUTA + "/" + b + "/roles/PROPIETARIO"), tokenB), 409, SIN_PROPIETARIO);
		assertEquals(List.of("PROPIETARIO"), rolesActivos(b, mini.org()));
	}

	/**
	 * D8-19: dos quitas a la vez de los dos únicos propietarios (y una por el endpoint viejo) se ordenan con el bloqueo
	 * de las filas PROPIETARIO: una pasa, la otra ve la primera confirmada y da 409. Llama al servicio desde dos hilos
	 * (cada uno con su escuela y su sesión de propietario) para que las dos lleguen siempre a la transacción.
	 */
	@Test
	void dosQuitasALaVez_quedaUnPropietario() throws Exception {
		Mini mini = mini("a-la-vez", 2);
		long a = mini.propietarios().get(0);
		long b = mini.propietarios().get(1);
		Usuario actor = cuentaEnOrg1(a);
		for (int ronda = 0; ronda < 6; ronda++) {
			jdbc.update("UPDATE membresia SET activa = TRUE WHERE organizacion_id = ? AND rol = 'PROPIETARIO'",
					mini.org());
			boolean viejo = ronda % 2 == 1;
			List<String> resultados = enParaleloComoPropietario(mini.org(), actor,
					() -> miembros.quitarRol(a, "PROPIETARIO"),
					viejo ? () -> membresiaService.reemplazarAutoridades(b, mini.org(), Set.of(Roles.ROLE_MANAGER))
							: () -> miembros.quitarRol(b, "PROPIETARIO"));
			assertEquals(List.of("409: " + SIN_PROPIETARIO, "ok"), resultados, "ronda " + ronda);
			assertEquals(1, jdbc.queryForObject("""
					SELECT COUNT(*) FROM membresia WHERE organizacion_id = ? AND rol = 'PROPIETARIO' AND activa = TRUE""",
					Long.class, mini.org()), "ronda " + ronda + ": siempre un propietario");
		}
	}

	@Test
	void endpointViejo_dejarSinPropietario_409() throws Exception {
		Mini mini = mini("viejo", 1);
		long a = mini.propietarios().get(0);
		for (String roles : List.of("[\"ROLE_MANAGER\"]", "[]", "[\"ROLE_USER\"]")) {
			long antes = ultimoEvento();
			assertMensaje(peticion(conCuerpo(put("/api/admin/users/" + a + "/roles"), roles), mini.token()), 409,
					SIN_PROPIETARIO);
			assertEquals(List.of("PROPIETARIO"), rolesActivos(a, mini.org()), roles);
			assertEquals(0, membresias(a, mini.org(), "ADMINISTRADOR"), "nada cambia: " + roles);
			assertEquals(0, membresias(a, mini.org(), "FAMILIA"), "nada cambia: " + roles);
			List<Map<String, Object>> filas = eventosDesde(antes, mini.org());
			assertEquals(1, filas.size(), filas.toString());
			assertTrue(((String) filas.get(0).get("resumen")).matches(RESUMEN_GENERICO), "la genérica, como hoy");
		}
		// Conservando ROLE_ADMIN, como hoy.
		MvcResult r = peticion(conCuerpo(put("/api/admin/users/" + a + "/roles"), "[\"ROLE_ADMIN\",\"ROLE_MANAGER\"]"),
				mini.token());
		assertEquals(200, r.getResponse().getStatus(), contenido(r));
		assertEquals("[ROLE_ADMIN, ROLE_MANAGER]", json.readTree(contenido(r)).get("rol").asText());
	}

	@Test
	void endpointViejo_conOtroPropietario_comoHoy() throws Exception {
		Mini mini = mini("viejo-dos", 2);
		long b = mini.propietarios().get(1);
		long antes = ultimoEvento();
		MvcResult r = peticion(conCuerpo(put("/api/admin/users/" + b + "/roles"), "[\"ROLE_MANAGER\"]"), mini.token());
		assertEquals(200, r.getResponse().getStatus(), contenido(r));
		assertEquals("[ROLE_MANAGER]", json.readTree(contenido(r)).get("rol").asText());
		assertEquals(List.of("ADMINISTRADOR"), rolesActivos(b, mini.org()));
		assertEquals(1, membresias(b, mini.org(), "PROPIETARIO"), "la fila queda, desactivada");
		List<Map<String, Object>> filas = eventosDesde(antes, mini.org());
		assertEquals(1, filas.size(), filas.toString());
		assertTrue(((String) filas.get(0).get("resumen")).matches(RESUMEN_GENERICO), "la genérica, como hoy");
	}

	// ------------------------------------------------------------------ sedes del responsable

	@Test
	void sedeDeOtraEscuela_400() throws Exception {
		long cuenta = responsable("sede-ajena", e.sA);
		for (String sedes : List.of("[" + otra.sA + "]", "[" + e.sB + "," + otra.sB + "]", "[999999999]")) {
			assertMensaje(peticion(conCuerpo(put(RUTA + "/" + cuenta + "/sedes"), "{\"sedeIds\":" + sedes + "}"),
					tPropietario), 400, SEDE_AJENA);
		}
		assertEquals(Set.of(e.sA), sedesDe(cuenta, e.org), "no cambia");
	}

	@Test
	void sedesVacias_400() throws Exception {
		long cuenta = responsable("sedes-vacias", e.sA);
		for (String cuerpo : List.of("{\"sedeIds\":[]}", "{}", "{\"sedeIds\":null}")) {
			assertMensaje(peticion(conCuerpo(put(RUTA + "/" + cuenta + "/sedes"), cuerpo), tPropietario), 400,
					SIN_SEDES);
		}
		for (String cuerpo : List.of("{\"sedeIds\":[null]}", "{\"sedeIds\":[" + e.sB + "," + e.sB + "]}")) {
			assertEquals(400, estado(conCuerpo(put(RUTA + "/" + cuenta + "/sedes"), cuerpo), tPropietario), cuerpo);
		}
		assertEquals(Set.of(e.sA), sedesDe(cuenta, e.org));

		long antes = ultimoEvento();
		leer(conCuerpo(put(RUTA + "/" + cuenta + "/sedes"), "{\"sedeIds\":[" + e.sB + "," + e.sA + "]}"), tPropietario,
				200);
		leer(conCuerpo(put(RUTA + "/" + cuenta + "/sedes"), "{\"sedeIds\":[" + e.sA + "," + e.sB + "]}"), tPropietario,
				200);
		assertEquals(Set.of(e.sA, e.sB), sedesDe(cuenta, e.org));
		String sedes = "sedes=#" + Math.min(e.sA, e.sB) + ",#" + Math.max(e.sA, e.sB);
		assertEquals(List.of("MIEMBROS SEDES_CAMBIADAS usuario=#" + cuenta + " " + sedes,
				"MIEMBROS SEDES_CAMBIADAS usuario=#" + cuenta + " " + sedes + " sinCambios=SI"),
				resumenes(eventosDesde(antes, e.org)));
	}

	@Test
	void sedesSinRolDeResponsable_409() throws Exception {
		long cuenta = cuenta("sin-resp");
		membresia(e.org, cuenta, "PROFESOR");
		assertMensaje(peticion(conCuerpo(put(RUTA + "/" + cuenta + "/sedes"), "{\"sedeIds\":[" + e.sA + "]}"),
				tPropietario), 409, NO_RESPONSABLE);
		assertEquals(0, membresias(cuenta, e.org, "RESPONSABLE_SEDE"));

		// Desactivado sí (activa o no).
		long inactivo = responsable("resp-inactivo", e.sA);
		jdbc.update("UPDATE membresia SET activa = FALSE WHERE usuario_id = ? AND organizacion_id = ?", inactivo, e.org);
		leer(conCuerpo(put(RUTA + "/" + inactivo + "/sedes"), "{\"sedeIds\":[" + e.sB + "]}"), tPropietario, 200);
		assertEquals(Set.of(e.sB), sedesDe(inactivo, e.org));
		assertEquals(List.of(), rolesActivos(inactivo, e.org), "sigue desactivado");
	}

	// ------------------------------------------------------------------ grupos del profesor

	@Test
	void grupoDeOtraEscuela_404() throws Exception {
		long cuenta = profesor("grupo-ajeno");
		asignacion(e.org, cuenta, e.gA, "TITULAR");
		for (long ajeno : List.of(otra.gA, 999_999_999L)) {
			assertMensaje(peticion(conCuerpo(put(RUTA + "/" + cuenta + "/grupos"), grupos(asig(e.gB, "TITULAR"),
					asig(ajeno, "AYUDANTE"))), tPropietario), 404, GRUPO_AJENO);
		}
		assertEquals(Map.of(e.gA, "TITULAR"), asignaciones(cuenta, e.org), "no cambia");
	}

	@Test
	void reemplazarGrupos_todoONada() throws Exception {
		long cuenta = profesor("reemplazar");
		asignacion(e.org, cuenta, e.gA, "TITULAR");
		asignacion(e.org, cuenta, e.gB, "AYUDANTE");
		String ruta = RUTA + "/" + cuenta + "/grupos";
		assertMensaje(peticion(conCuerpo(put(ruta), grupos(asig(e.gB, "TITULAR"), asig(e.gB, "AYUDANTE"))),
				tPropietario), 400, "Hay grupos repetidos.");
		assertEquals(400, estado(conCuerpo(put(ruta), grupos(asig(e.gA2, "TITULAR"), asig(e.gB, "JEFE"))),
				tPropietario));
		assertEquals(400, estado(conCuerpo(put(ruta), "{}"), tPropietario));
		assertEquals(Map.of(e.gA, "TITULAR", e.gB, "AYUDANTE"), asignaciones(cuenta, e.org));

		long antes = ultimoEvento();
		JsonNode ficha = leer(conCuerpo(put(ruta), grupos(asig(e.gB, "TITULAR"), asig(e.gA2, "AYUDANTE"))),
				tPropietario, 200);
		assertEquals(Map.of(e.gB, "TITULAR", e.gA2, "AYUDANTE"), asignaciones(cuenta, e.org));
		assertEquals(2, ficha.get("grupos").size());
		List<String> filas = resumenes(eventosDesde(antes, e.org));
		assertEquals(Set.of("PROFESOR ASIGNACION_QUITADA grupo=#" + e.gA + " usuario=#" + cuenta + " rol=TITULAR",
				"PROFESOR ASIGNACION_ROL_CAMBIADO grupo=#" + e.gB + " usuario=#" + cuenta + " rol=TITULAR",
				"PROFESOR ASIGNACION_CREADA grupo=#" + e.gA2 + " usuario=#" + cuenta + " rol=AYUDANTE"),
				new HashSet<>(filas.subList(0, 3)));
		assertEquals(3, filas.size(), "una fila por cambio y sin la genérica: " + filas);

		// El mismo conjunto: 200 y una fila «sin cambios».
		antes = ultimoEvento();
		leer(conCuerpo(put(ruta), grupos(asig(e.gA2, "AYUDANTE"), asig(e.gB, "TITULAR"))), tPropietario, 200);
		assertEquals(List.of("MIEMBROS GRUPOS_CAMBIADOS usuario=#" + cuenta + " sinCambios=SI"),
				resumenes(eventosDesde(antes, e.org)));

		leer(conCuerpo(put(ruta), "{\"asignaciones\":[]}"), tPropietario, 200);
		assertEquals(Map.of(), asignaciones(cuenta, e.org), "lista vacía: las quita todas");
		assertEquals(List.of("PROFESOR"), rolesActivos(cuenta, e.org), "la membresía queda");
	}

	@Test
	void grupos_sinProfesorActivo_409() throws Exception {
		long inactivo = cuenta("grupos-inactivo");
		membresiaInactiva(e.org, inactivo, "PROFESOR");
		asignacion(e.org, inactivo, e.gA, "TITULAR");
		long familia = cuenta("grupos-familia");
		membresia(e.org, familia, "FAMILIA");
		for (long cuenta : List.of(inactivo, familia)) {
			assertMensaje(peticion(conCuerpo(put(RUTA + "/" + cuenta + "/grupos"), grupos(asig(e.gB, "TITULAR"))),
					tPropietario), 409, SIN_PROFESOR_ACTIVO);
		}
		assertEquals(Map.of(e.gA, "TITULAR"), asignaciones(inactivo, e.org));
		assertEquals(Map.of(), asignaciones(familia, e.org));
	}

	@Test
	void desactivarProfesorConservaAsignaciones_yReactivarLasRecupera() throws Exception {
		long cuenta = e.cuentaFamilia(usuarios, "fam-prof-" + contador.incrementAndGet());
		membresia(e.org, cuenta, "PROFESOR");
		asignacion(e.org, cuenta, e.gA, "TITULAR");
		String token = e.token(jwtService, usuarios, cuenta);
		assertTrue(leer(get("/api/auth/permisos"), token, 200).get("tieneGrupos").asBoolean());

		leer(delete(RUTA + "/" + cuenta + "/roles/PROFESOR"), tPropietario, 200);
		assertEquals(Map.of(e.gA, "TITULAR"), asignaciones(cuenta, e.org), "conserva sus asignaciones");
		JsonNode permisos = leer(get("/api/auth/permisos"), token, 200);
		assertFalse(permisos.get("tieneGrupos").asBoolean(), "sin efecto mientras está desactivado: " + permisos);
		assertFalse(permisos.get("roles").toString().contains("PROFESOR"), permisos.toString());
		JsonNode ficha = leer(get(RUTA + "/" + cuenta), tPropietario, 200);
		assertEquals(1, ficha.get("numeroGrupos").asInt());
		assertEquals(1, ficha.get("grupos").size());

		leer(put(RUTA + "/" + cuenta + "/roles/PROFESOR"), tPropietario, 200);
		permisos = leer(get("/api/auth/permisos"), token, 200);
		assertTrue(permisos.get("tieneGrupos").asBoolean(), "reactivar las recupera: " + permisos);
	}

	// ------------------------------------------------------------------ registro

	@Test
	void cargoNoApareceEnElRegistro() throws Exception {
		long cuenta = cuenta("cargo-secreto");
		String secreto = "CargoSecreto" + e.marca;
		long antes = ultimoEvento();
		leer(conCuerpo(post(RUTA), cuerpo(Map.of("email", emailDe(cuenta), "rol", "PROFESOR", "cargo", secreto))),
				tPropietario, 200);
		leer(conCuerpo(put(RUTA + "/" + cuenta + "/roles/PROFESOR"), cuerpo(Map.of("cargo", secreto + " bis"))),
				tPropietario, 200);
		// Y los 4xx: cargo largo y FAMILIA.
		assertEquals(400, estado(conCuerpo(put(RUTA + "/" + cuenta + "/roles/PROFESOR"),
				cuerpo(Map.of("cargo", secreto + "x".repeat(100)))), tPropietario));
		assertEquals(400, estado(conCuerpo(post(RUTA), cuerpo(Map.of("email", emailDe(cuenta), "rol", "FAMILIA",
				"cargo", secreto))), tPropietario));
		assertEquals(secreto + " bis", cargo(cuenta, e.org, "PROFESOR"));
		List<Map<String, Object>> filas = eventosDesde(antes, e.org);
		assertEquals(5, filas.size(), filas.toString());
		sinDatos(filas, List.of(secreto));
	}

	@Test
	void registroSoloConIds() throws Exception {
		String marca = "Secr" + e.marca;
		long cuenta = persona(marca, "Nombresecreto", "Apellidosecreto");
		membresia(e.org, cuenta, "FAMILIA");
		String email = emailDe(cuenta);
		long antes = ultimoEvento();
		leer(conCuerpo(post(RUTA), cuerpo(alta(cuenta, "RESPONSABLE_SEDE", "sedeIds", List.of(e.sA)))), tPropietario,
				200);
		leer(conCuerpo(put(RUTA + "/" + cuenta + "/sedes"), "{\"sedeIds\":[" + e.sB + "]}"), tPropietario, 200);
		leer(conCuerpo(put(RUTA + "/" + cuenta + "/roles/PROFESOR"), "{}"), tPropietario, 200);
		leer(conCuerpo(put(RUTA + "/" + cuenta + "/grupos"), grupos(asig(e.gA, "TITULAR"))), tPropietario, 200);
		leer(delete(RUTA + "/" + cuenta + "/roles/RESPONSABLE_SEDE"), tPropietario, 200);
		assertEquals(404, estado(conCuerpo(post(RUTA), cuerpo(Map.of("email", "x" + email, "rol", "PROFESOR"))),
				tPropietario));
		List<Map<String, Object>> filas = eventosDesde(antes, e.org);
		assertTrue(filas.size() >= 7, filas.toString());
		sinDatos(filas, List.of(email, "Nombresecreto", "Apellidosecreto", marca));
		for (Map<String, Object> fila : filas) {
			String resumen = (String) fila.get("resumen");
			if (!resumen.matches(RESUMEN_GENERICO)) {
				assertTrue(resumen.matches("^(MIEMBROS|PROFESOR) [A-Z_]+( [a-zA-Z]+=(#\\d+(,#\\d+)*|[A-Z_]+))*$"),
						resumen);
			}
		}
	}

	@Test
	void unaFilaPorEscritura() throws Exception {
		long cuenta = cuenta("una-fila");
		membresia(e.org, cuenta, "FAMILIA");
		List<Object[]> escrituras = List.of(
				new Object[] { conCuerpo(post(RUTA), cuerpo(alta(cuenta, "PROFESOR", null, null))),
						"MIEMBROS ROL_DADO" },
				new Object[] { conCuerpo(put(RUTA + "/" + cuenta + "/roles/RESPONSABLE_SEDE"),
						"{\"sedeIds\":[" + e.sA + "]}"), "MIEMBROS ROL_DADO" },
				new Object[] { conCuerpo(put(RUTA + "/" + cuenta + "/sedes"), "{\"sedeIds\":[" + e.sB + "]}"),
						"MIEMBROS SEDES_CAMBIADAS" },
				new Object[] { delete(RUTA + "/" + cuenta + "/roles/RESPONSABLE_SEDE"), "MIEMBROS ROL_QUITADO" },
				new Object[] { conCuerpo(put(RUTA + "/" + cuenta + "/grupos"), grupos(asig(e.gA, "TITULAR"))),
						"PROFESOR ASIGNACION_CREADA" },
				new Object[] { conCuerpo(put(RUTA + "/" + cuenta + "/grupos"), grupos(asig(e.gA, "TITULAR"))),
						"MIEMBROS GRUPOS_CAMBIADOS" });
		for (Object[] escritura : escrituras) {
			long antes = ultimoEvento();
			MvcResult r = peticion((MockHttpServletRequestBuilder) escritura[0], tPropietario);
			assertEquals(200, r.getResponse().getStatus(), contenido(r));
			List<String> filas = resumenes(eventosDesde(antes, e.org));
			// PUT …/roles/RESPONSABLE_SEDE con sedes: el rol y sus sedes.
			assertTrue(filas.get(0).startsWith((String) escritura[1] + " "), filas.toString());
			assertTrue(filas.stream().noneMatch(f -> f.matches(RESUMEN_GENERICO)), "sin la genérica: " + filas);
		}
	}

	/** La búsqueda ({@code q}: email o nombre) no queda en {@code query_params_json}, ni en el 200 ni en el 400. */
	@Test
	void busquedaNoQuedaEnElRegistro() throws Exception {
		String marca = "Bus" + e.marca;
		long cuenta = persona(marca, "Nombrebuscado", "Apellidobuscado");
		membresia(e.org, cuenta, "PROFESOR");
		String email = emailDe(cuenta);
		long antes = ultimoEvento();
		assertEquals(List.of(cuenta), ids(leer(get(RUTA).param("q", email).param("size", "5"), tPropietario, 200)));
		assertEquals(List.of(cuenta), ids(leer(get(RUTA).param("q", "Nombrebuscado " + marca), tPropietario, 200)));
		assertEquals(400, estado(get(RUTA).param("q", email + "x".repeat(101)), tPropietario));
		List<Map<String, Object>> filas = eventosDesde(antes, e.org);
		assertEquals(3, filas.size(), filas.toString());
		for (Map<String, Object> fila : filas) {
			String parametros = (String) fila.get("query_params_json");
			assertTrue(parametros.contains("\"q\":\"[redactado]\""), parametros);
		}
		assertTrue(((String) filas.get(0).get("query_params_json")).contains("\"size\":\"5\""), "lo demás, igual");
		sinDatos(filas, List.of(email, "Nombrebuscado", "Apellidobuscado", marca));
	}

	@Test
	void clavesRedactadas_4xx() throws Exception {
		String email = "redactado-" + e.marca + "@example.test";
		String cargo = "CargoRedactado" + e.marca;
		long antes = ultimoEvento();
		// E8-4: con nombre y apellidos y sin cuenta es una invitación; un grupo de otra escuela la deja en 404.
		assertEquals(404, estado(conCuerpo(post(RUTA), cuerpo(Map.of("email", email, "rol", "PROFESOR", "cargo", cargo,
				"grupos", List.of(asig(e.gA, "TITULAR"), asig(otra.gA, "AYUDANTE")), "nombre", "NombreRedactado",
				"apellidos", "ApellidoRedactado"))), tPropietario));
		long cuenta = cuenta("redactar");
		membresia(e.org, cuenta, "FAMILIA");
		assertEquals(400, estado(conCuerpo(put(RUTA + "/" + cuenta + "/roles/FAMILIA"), cuerpo(Map.of("cargo", cargo))),
				tPropietario));
		List<Map<String, Object>> filas = eventosDesde(antes, e.org);
		assertEquals(2, filas.size(), filas.toString());
		String alta = (String) filas.get(0).get("payload_json");
		assertTrue(alta.contains("\"email\":\"[redactado]\""), alta);
		assertTrue(alta.contains("\"cargo\":\"[redactado]\""), alta);
		assertTrue(alta.contains("\"nombre\":\"[redactado]\""), alta);
		assertTrue(alta.contains("\"apellidos\":\"[redactado]\""), alta);
		assertTrue(alta.contains("\"grupoId\":" + e.gA), "los ids sí: " + alta);
		String rol = (String) filas.get(1).get("payload_json");
		assertTrue(rol.contains("\"cargo\":\"[redactado]\""), rol);
		sinDatos(filas, List.of(email, cargo, "NombreRedactado", "ApellidoRedactado"));
	}

	// ------------------------------------------------------------------ permisos

	@Test
	void responsableProfesorFamiliaYPlataforma_403() throws Exception {
		long cuenta = cuenta("intruso");
		membresia(e.org, cuenta, "PROFESOR");
		for (String token : List.of(tRespA, tProfesor, tFamilia, tPlataforma)) {
			for (MockHttpServletRequestBuilder p : rutas(cuenta)) {
				assertEquals(403, estado(p, token), p.toString());
			}
		}
		for (MockHttpServletRequestBuilder p : rutas(cuenta)) {
			assertEquals(401, mvc.perform(p).andReturn().getResponse().getStatus());
		}
		assertEquals(List.of("PROFESOR"), rolesActivos(cuenta, e.org), "nada cambia");
		assertEquals(Map.of(), asignaciones(cuenta, e.org));
	}

	// ------------------------------------------------------------------ datos

	private List<MockHttpServletRequestBuilder> rutas(long cuenta) throws Exception {
		return List.of(get(RUTA), head(RUTA), get(RUTA + "/" + cuenta), head(RUTA + "/" + cuenta),
				conCuerpo(post(RUTA), cuerpo(alta(cuenta, "PROFESOR", "grupos", List.of(asig(e.gA, "TITULAR"))))),
				conCuerpo(put(RUTA + "/" + cuenta + "/roles/RESPONSABLE_SEDE"), "{\"sedeIds\":[" + e.sA + "]}"),
				delete(RUTA + "/" + cuenta + "/roles/PROFESOR"),
				conCuerpo(put(RUTA + "/" + cuenta + "/sedes"), "{\"sedeIds\":[" + e.sA + "]}"),
				conCuerpo(put(RUTA + "/" + cuenta + "/grupos"), grupos(asig(e.gA, "TITULAR"))));
	}

	/** Cuenta nueva sin membresías (email con la marca de la escuela y un contador). */
	private long cuenta(String nombre) {
		return e.cuenta(usuarios, "m" + contador.incrementAndGet() + "-" + nombre);
	}

	/** Cuenta nueva con nombre y apellidos «{marca} {apellidos}», para buscarla por la marca. */
	private long persona(String marca, String nombre, String apellidos) {
		long id = cuenta(marca.toLowerCase());
		jdbc.update("UPDATE usuario SET nombre = ?, apellidos = ? WHERE id = ?", nombre, marca + " " + apellidos, id);
		return id;
	}

	private long responsable(String nombre, long sede) {
		long id = cuenta(nombre);
		membresia(e.org, id, "RESPONSABLE_SEDE");
		jdbc.update("INSERT INTO membresia_sede (membresia_id, sede_id) VALUES (?, ?)",
				membresiaId(id, e.org, "RESPONSABLE_SEDE"), sede);
		return id;
	}

	private long profesor(String nombre) {
		long id = cuenta(nombre);
		membresia(e.org, id, "PROFESOR");
		return id;
	}

	private void membresia(long organizacion, long usuario, String rol) {
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", usuario, organizacion,
				rol);
	}

	private void membresiaInactiva(long organizacion, long usuario, String rol) {
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol, activa) VALUES (?, ?, ?, FALSE)", usuario,
				organizacion, rol);
	}

	private long membresiaId(long usuario, long organizacion, String rol) {
		return jdbc.queryForObject("SELECT id FROM membresia WHERE usuario_id = ? AND organizacion_id = ? AND rol = ?",
				Long.class, usuario, organizacion, rol);
	}

	private void asignacion(long organizacion, long usuario, long grupo, String rol) {
		jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) VALUES (?, ?, ?, ?)",
				organizacion, usuario, grupo, rol);
	}

	/** Roles activos de la cuenta en la escuela, en el orden del enum. */
	private List<String> rolesActivos(long usuario, long organizacion) {
		return jdbc.queryForList("""
				SELECT rol FROM membresia WHERE usuario_id = ? AND organizacion_id = ? AND activa = TRUE
				ORDER BY rol""", String.class, usuario, organizacion);
	}

	private long membresias(long usuario, long organizacion, String rol) {
		return jdbc.queryForObject("""
				SELECT COUNT(*) FROM membresia WHERE usuario_id = ? AND organizacion_id = ? AND rol = ?""", Long.class,
				usuario, organizacion, rol);
	}

	private String cargo(long usuario, long organizacion, String rol) {
		List<String> cargos = jdbc.queryForList("""
				SELECT cargo FROM membresia WHERE usuario_id = ? AND organizacion_id = ? AND rol = ?""", String.class,
				usuario, organizacion, rol);
		return cargos.isEmpty() ? null : cargos.get(0);
	}

	private Map<Long, String> asignaciones(long usuario, long organizacion) {
		Map<Long, String> resultado = new LinkedHashMap<>();
		jdbc.queryForList("SELECT grupo_id, rol FROM asignacion_profesor WHERE usuario_id = ? AND organizacion_id = ?",
				usuario, organizacion).forEach(f -> resultado.put(((Number) f.get("grupo_id")).longValue(),
						(String) f.get("rol")));
		return resultado;
	}

	private Set<Long> sedesDe(long usuario, long organizacion) {
		return new HashSet<>(jdbc.queryForList("""
				SELECT ms.sede_id FROM membresia_sede ms JOIN membresia m ON m.id = ms.membresia_id
				WHERE m.usuario_id = ? AND m.organizacion_id = ? AND m.rol = 'RESPONSABLE_SEDE'""", Long.class, usuario,
				organizacion));
	}

	private String emailDe(long usuario) {
		return jdbc.queryForObject("SELECT email FROM usuario WHERE id = ?", String.class, usuario);
	}

	private String nombreSede(long sede) {
		return jdbc.queryForObject("SELECT nombre FROM sede WHERE id = ?", String.class, sede);
	}

	private Map<String, Object> alta(long cuenta, String rol, String campo, Object valor) {
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("email", emailDe(cuenta));
		datos.put("rol", rol);
		if (campo != null) {
			datos.put(campo, valor);
		}
		return datos;
	}

	private static Map<String, Object> asig(long grupo, String rol) {
		Map<String, Object> asignacion = new LinkedHashMap<>();
		asignacion.put("grupoId", grupo);
		asignacion.put("rol", rol);
		return asignacion;
	}

	@SafeVarargs
	private String grupos(Map<String, Object>... asignaciones) throws Exception {
		return cuerpo(Map.of("asignaciones", List.of(asignaciones)));
	}

	/** Escuela mínima con {@code propietarios} propietarios activos; el token es del primero. */
	private Mini mini(String nombre, int propietarios) {
		String slug = "it-e8-3-" + nombre + "-" + e.marca;
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES (?, 'Escuela IT miembros mínima')", slug);
		long org = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = ?", Long.class, slug);
		List<Long> cuentas = new ArrayList<>();
		for (int i = 0; i < propietarios; i++) {
			long cuenta = cuenta("prop-" + nombre + "-" + i);
			membresia(org, cuenta, "PROPIETARIO");
			cuentas.add(cuenta);
		}
		return new Mini(org, cuentas, tokenEn(cuentas.get(0), org));
	}

	private String tokenEn(long usuario, long organizacion) {
		return jwtService.generateToken(cuentaEnOrg1(usuario), 600_000, organizacion);
	}

	private Usuario cuentaEnOrg1(long usuario) {
		return TenantContext.calcularCon(EscuelaConSedesDePrueba.ORG1, () -> usuarios.findById(usuario).orElseThrow());
	}

	// ------------------------------------------------------------------ auditoría, concurrencia y HTTP

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

	private static void sinDatos(List<Map<String, Object>> filas, List<String> datos) {
		for (Map<String, Object> fila : filas) {
			for (String columna : List.of("resumen", "payload_json", "query_params_json", "endpoint")) {
				String valor = String.valueOf(fila.get(columna)).toLowerCase();
				for (String dato : datos) {
					assertFalse(valor.contains(dato.toLowerCase()), columna + " contiene «" + dato + "»: " + fila);
				}
			}
		}
	}

	/** Sentencias preparadas por Hibernate en la petición (la menor de tres, por si salta un job a la vez). */
	private long sentencias(MockHttpServletRequestBuilder peticion) throws Exception {
		Statistics estadisticas = emf.unwrap(SessionFactory.class).getStatistics();
		boolean antes = estadisticas.isStatisticsEnabled();
		estadisticas.setStatisticsEnabled(true);
		try {
			long menor = Long.MAX_VALUE;
			for (int i = 0; i < 3; i++) {
				estadisticas.clear();
				assertEquals(200, estado(peticion, tPropietario));
				menor = Math.min(menor, estadisticas.getPrepareStatementCount());
			}
			return menor;
		} finally {
			estadisticas.setStatisticsEnabled(antes);
		}
	}

	@FunctionalInterface
	interface Accion {
		Object ejecutar() throws Exception;
	}

	/**
	 * Dos llamadas al servicio a la vez, cada una en su hilo con la escuela y una sesión de propietario: «ok» o
	 * «409: mensaje» (IllegalStateException), ordenados.
	 */
	private List<String> enParaleloComoPropietario(long organizacion, Usuario actor, Accion primera, Accion segunda)
			throws Exception {
		List<Callable<String>> llamadas = new ArrayList<>();
		for (Accion accion : List.of(primera, segunda)) {
			llamadas.add(() -> {
				SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null,
						List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
				try {
					return TenantContext.calcularCon(organizacion, () -> {
						try {
							accion.ejecutar();
							return "ok";
						} catch (IllegalStateException conflicto) {
							return "409: " + conflicto.getMessage();
						} catch (Exception otro) {
							throw new IllegalStateException("inesperado", otro);
						}
					});
				} finally {
					SecurityContextHolder.clearContext();
				}
			});
		}
		return enParalelo(llamadas.get(0), llamadas.get(1));
	}

	private static <T extends Comparable<T>> List<T> enParalelo(Callable<T> primera, Callable<T> segunda)
			throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch preparados = new CountDownLatch(2);
		CountDownLatch salida = new CountDownLatch(1);
		try {
			List<Future<T>> futuros = new ArrayList<>();
			for (Callable<T> accion : List.of(primera, segunda)) {
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
			List<T> resultados = new ArrayList<>();
			for (Future<T> futuro : futuros) {
				resultados.add(futuro.get(60, TimeUnit.SECONDS));
			}
			resultados.sort(Comparable::compareTo);
			return resultados;
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
		return nodo != null && nodo.hasNonNull("mensaje") ? nodo.get("mensaje").asText() : "";
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

	private static List<Long> ids(JsonNode pagina) {
		return idsDe(pagina.get("contenido"), "usuarioId");
	}

	private static List<Long> idsDe(JsonNode lista, String campo) {
		List<Long> ids = new ArrayList<>();
		lista.forEach(n -> ids.add(n.get(campo).asLong()));
		return ids;
	}

	private static JsonNode fila(JsonNode pagina, long usuario) {
		for (JsonNode n : pagina.get("contenido")) {
			if (n.get("usuarioId").asLong() == usuario) {
				return n;
			}
		}
		throw new AssertionError("sin la cuenta " + usuario + ": " + pagina);
	}

	/** {@code "ROL:activa,..."} en el orden de la respuesta. */
	private static String roles(JsonNode fila) {
		List<String> roles = new ArrayList<>();
		fila.get("roles").forEach(r -> roles.add(r.get("rol").asText() + ":" + r.get("activa").asBoolean()));
		return String.join(",", roles);
	}
}
