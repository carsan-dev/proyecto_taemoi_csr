package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
import com.taemoi.project.dtos.response.GrupoConAlumnosDTO;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza E7-2 contra MySQL (fase3-e7-plan.md §3): la salud a petición y la hoja de asistencia del grupo del profesor.
 * <ul>
 * <li>Salud (D7-14): solo alumnos activos de sus grupos ({@code alcance.grupos()}); cada lectura deja un evento en
 * {@code auditoria_evento} (módulo {@code salud}, recurso = alumno, usuario = el profesor) y uno solo; respuesta
 * {@code no-store}. Fuera de sus grupos o de otra escuela, 403; de baja, 404; sin evento de salud en esos casos.</li>
 * <li>Asistencia (D7-15): el PDF de siempre con los turnos de ESE grupo por id: un grupo homónimo de otra sede con
 * turno el mismo lunes no se mezcla; solo alumnos activos que están en el grupo. El PDF de gestión no cambia.</li>
 * <li>Cuentas mixtas: R+Pr no ve sus sedes por aquí; P/G (también con asignación) no usan estas rutas (D7-10).</li>
 * </ul>
 * Escuela sintética ({@link EscuelaConSedesDePrueba}) y otra para el aislamiento. Datos inventados; todo vive en esas
 * dos escuelas nuevas, como el resto de la batería.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProfesorSaludAsistenciaIntegrationTest extends MySqlIntegrationTest {

	private static final String DESCRIPCION = "ASMA-FICTICIA-E72";
	private static final String ALERGIAS = "ALERGIA-FICTICIA-E72";
	private static final String IMPIDE = "IMPIDE-FICTICIO-E72";
	private static final LocalDate NACIMIENTO = LocalDate.of(2015, 3, 20);
	private static final Set<String> CLAVES_SALUD = Set.of("salud", "registrado", "lesionEnfermedad", "descripcion",
			"impideActividad", "impideDetalle", "alergias", "origen", "actualizadoEn");

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	long profA, profB, profRP, gestorProf, propietario, profOtra;
	long tutelado, deBaja, homonimo, gHomonimo, tHomonimo;
	String tProfA, tProfB, tProfRP, tGestorProf, tGestor, tPropietario, tProfOtra;

	@BeforeAll
	void preparar() throws Exception {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e7-2");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e7-2-otra");

		profA = soloProfesor(e, "profa");
		asignar(e, profA, e.gA, "TITULAR");
		profB = soloProfesor(e, "profb");
		asignar(e, profB, e.gB, "TITULAR");
		profRP = soloProfesor(e, "profrp");
		responsableDeSede(e, profRP, e.sA);
		asignar(e, profRP, e.gB, "TITULAR");
		gestorProf = e.cuenta(usuarios, "gestorprof");
		membresia(e, gestorProf, "ADMINISTRADOR");
		membresia(e, gestorProf, "PROFESOR");
		asignar(e, gestorProf, e.gA, "TITULAR");
		propietario = e.cuenta(usuarios, "propietario");
		membresia(e, propietario, "PROPIETARIO");
		profOtra = soloProfesor(otra, "profotra");
		asignar(otra, profOtra, otra.gA, "TITULAR");

		// tutelado: en gA y en su turno del lunes (tA), con salud registrada (texto cifrado en la base).
		tutelado = e.alumno("E72TUTELADO" + e.marca, e.sA, true);
		jdbc.update("UPDATE alumno SET fecha_nacimiento = ? WHERE id = ?", NACIMIENTO, tutelado);
		miembro(e.gA, tutelado);
		e.enTurno(e.tA, tutelado);
		e.taekwondo(tutelado, true);
		// De baja: sigue en gA y en tA (no sale en ningún PDF; su salud, 404 para el profesor).
		deBaja = e.alumno("E72BAJA" + e.marca, e.sA, false);
		miembro(e.gA, deBaja);
		e.enTurno(e.tA, deBaja);
		e.taekwondo(deBaja, true);
		// Grupo homónimo de gA en la sede B, con turno el mismo lunes a la misma hora y un alumno suyo.
		gHomonimo = TenantContext.calcularCon(e.org, () -> {
			GrupoConAlumnosDTO dto = new GrupoConAlumnosDTO();
			dto.setNombre("IT E6 gA " + e.marca);
			dto.setDeporte("TAEKWONDO");
			dto.setSedeId(e.sB);
			return grupoService.crearGrupo(dto).getId();
		});
		jdbc.update("""
				INSERT INTO turno (dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id)
				VALUES ('Lunes', '18:00', '19:00', NULL, ?, ?)""", gHomonimo, e.org);
		tHomonimo = jdbc.queryForObject("SELECT MAX(id) FROM turno WHERE organizacion_id = ?", Long.class, e.org);
		homonimo = e.alumno("E72HOMONIMO" + e.marca, e.sB, true);
		miembro(gHomonimo, homonimo);
		e.enTurno(tHomonimo, homonimo);
		e.taekwondo(homonimo, true);

		tProfA = e.token(jwtService, usuarios, profA);
		tProfB = e.token(jwtService, usuarios, profB);
		tProfRP = e.token(jwtService, usuarios, profRP);
		tGestorProf = e.token(jwtService, usuarios, gestorProf);
		tGestor = e.token(jwtService, usuarios, e.gestor);
		tPropietario = e.token(jwtService, usuarios, propietario);
		tProfOtra = otra.token(jwtService, usuarios, profOtra);

		// La salud la registra gestión por su ruta de siempre (cifrada en la base).
		for (long alumno : List.of(tutelado, deBaja, e.aA, e.aB)) {
			MvcResult r = peticion(put("/api/alumnos/" + alumno + "/salud").contentType(MediaType.APPLICATION_JSON)
					.content("{\"lesionEnfermedad\":true,\"descripcion\":\"" + DESCRIPCION
							+ "\",\"impideActividad\":true,\"impideDetalle\":\"" + IMPIDE + "\",\"alergias\":\"" + ALERGIAS
							+ "\"}"), tGestor);
			assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		}
	}

	@AfterEach
	void sinFugas() {
		assertFalse(TenantContext.get() != null, "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ salud

	@Test
	void lecturaSaludRegistraEvento_yNoStore() throws Exception {
		String ruta = "/api/profesor/alumnos/" + tutelado + "/salud";
		long antes = eventosSalud(tutelado, profA);
		long antesRuta = eventosConEndpoint(ruta);

		MvcResult r = peticion(get(ruta), tProfA);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		String cacheControl = r.getResponse().getHeader("Cache-Control");
		assertNotNull(cacheControl);
		assertTrue(cacheControl.contains("no-store"), cacheControl);

		JsonNode salud = json.readTree(r.getResponse().getContentAsString());
		assertEquals(Set.of("alumnoId", "registrado", "lesionEnfermedad", "descripcion", "impideActividad",
				"impideDetalle", "alergias", "origen", "actualizadoEn"), campos(salud), "mismo DTO que gestión");
		assertEquals(tutelado, salud.get("alumnoId").asLong());
		assertTrue(salud.get("registrado").asBoolean());
		assertEquals(DESCRIPCION, salud.get("descripcion").asText());
		assertEquals(ALERGIAS, salud.get("alergias").asText());
		assertEquals(IMPIDE, salud.get("impideDetalle").asText());

		assertEquals(antes + 1, eventosSalud(tutelado, profA), "un evento de salud con el usuario del profesor");
		assertEquals(antesRuta + 1, eventosConEndpoint(ruta), "uno solo: el filtro HTTP no duplica la lectura");
		Long ultimo = jdbc.queryForObject("""
				SELECT MAX(id) FROM auditoria_evento WHERE modulo = 'salud' AND recurso_id = ? AND usuario_id = ?""",
				Long.class, tutelado, profA);
		var evento = jdbc.queryForMap("""
				SELECT accion, endpoint, modulo, recurso_id, usuario_id, estado_http, resumen, organizacion_id, payload_json
				FROM auditoria_evento WHERE id = ?""", ultimo);
		assertEquals("READ", evento.get("accion"));
		assertEquals(ruta, evento.get("endpoint"));
		assertEquals(200, ((Number) evento.get("estado_http")).intValue());
		assertEquals(e.org, ((Number) evento.get("organizacion_id")).longValue());
		String resumen = (String) evento.get("resumen");
		assertEquals("Lectura de datos de salud del alumno #" + tutelado + " (profesor)", resumen,
				"solo el id y un texto fijo");
		Object payload = evento.get("payload_json");
		assertFalse(payload != null && payload.toString().contains(DESCRIPCION), "nunca el contenido");

		assertEquals(200, estado(head(ruta), tProfA), "HEAD como GET (también registra la lectura)");
	}

	@Test
	void saludDeAlumnoDeOtroGrupo_403() throws Exception {
		long antes = eventosSaludDelUsuario(profA);
		assertEquals(403, estado(get("/api/profesor/alumnos/" + e.aB + "/salud"), tProfA), "aB solo está en gB");
		assertEquals(403, estado(get("/api/profesor/alumnos/" + e.aA + "/salud"), tProfA),
				"aA está en el turno tA pero no en el grupo gA");
		assertEquals(403, estado(get("/api/profesor/alumnos/" + tutelado + "/salud"), tProfB));
		assertEquals(403, estado(head("/api/profesor/alumnos/" + e.aB + "/salud"), tProfA));
		assertEquals(403, estado(get("/api/profesor/alumnos/999999999/salud"), tProfA));
		assertEquals(antes, eventosSaludDelUsuario(profA), "sin lectura, sin evento de salud");
	}

	@Test
	void saludDeAlumnoDeOtraEscuela_403() throws Exception {
		long antes = eventosSaludDelUsuario(profA);
		assertEquals(403, estado(get("/api/profesor/alumnos/" + otra.aGrupoA + "/salud"), tProfA));
		assertEquals(403, estado(get("/api/profesor/alumnos/" + tutelado + "/salud"), tProfOtra));
		assertEquals(antes, eventosSaludDelUsuario(profA));
	}

	@Test
	void saludAlumnoDeBaja_404() throws Exception {
		long antes = eventosSaludDelUsuario(profA);
		assertEquals(404, estado(get("/api/profesor/alumnos/" + deBaja + "/salud"), tProfA), "D7-13");
		assertEquals(antes, eventosSaludDelUsuario(profA), "de baja: no se lee ni se registra");
	}

	@Test
	void saludResponsableMasProfesorNoVeSusSedes() throws Exception {
		// Responsable de la sede A y profesor de gB: aA y el tutelado son de su sede, no de su grupo.
		assertEquals(403, estado(get("/api/profesor/alumnos/" + e.aA + "/salud"), tProfRP));
		assertEquals(403, estado(get("/api/profesor/alumnos/" + tutelado + "/salud"), tProfRP));
		assertEquals(403, estado(get("/api/profesor/grupos/" + e.gA + "/asistencia?year=2026&month=9"), tProfRP));
		assertEquals(200, estado(get("/api/profesor/alumnos/" + e.aB + "/salud"), tProfRP), "aB está en gB");
		assertEquals(200, estado(get("/api/profesor/grupos/" + e.gB + "/asistencia?year=2026&month=9"), tProfRP));
		// Y la salud de gestión sigue cerrada a la sesión 184, también para su sede.
		assertEquals(403, estado(get("/api/alumnos/" + e.aA + "/salud"), tProfRP));
	}

	@Test
	void gestionNoUsaSaludDelProfesor_403() throws Exception {
		for (String token : List.of(tGestor, tPropietario, tGestorProf)) {
			assertEquals(403, estado(get("/api/profesor/alumnos/" + tutelado + "/salud"), token));
			assertEquals(403, estado(get("/api/profesor/grupos/" + e.gA + "/asistencia?year=2026&month=9"), token));
		}
		assertEquals(200, estado(get("/api/alumnos/" + tutelado + "/salud"), tGestor), "gestión, por su ruta");
	}

	@Test
	void saludNoSaleEnListadosNiFicha() throws Exception {
		for (String ruta : List.of("/api/profesor/grupos", "/api/profesor/grupos/" + e.gA + "/alumnos",
				"/api/profesor/alumnos/" + tutelado)) {
			MvcResult r = peticion(get(ruta), tProfA);
			assertEquals(200, r.getResponse().getStatus(), ruta);
			String cuerpo = r.getResponse().getContentAsString();
			for (String texto : List.of(DESCRIPCION, ALERGIAS, IMPIDE)) {
				assertFalse(cuerpo.contains(texto), "texto de salud en " + ruta);
			}
			assertSinClavesDeSalud(json.readTree(cuerpo), ruta);
		}
	}

	// ------------------------------------------------------------------ asistencia

	@Test
	void pdfAsistenciaSoloAlumnosDelGrupo() throws Exception {
		MvcResult r = peticion(get("/api/profesor/grupos/" + e.gA + "/asistencia").param("year", "2026")
				.param("month", "9"), tProfA);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertEquals(MediaType.APPLICATION_PDF_VALUE, r.getResponse().getContentType());
		assertEquals("attachment; filename=\"Asistencia-grupo-" + e.gA + "-2026-09.pdf\"",
				r.getResponse().getHeader("Content-Disposition"));
		String texto = texto(r.getResponse().getContentAsByteArray());
		assertTrue(texto.contains("E72TUTELADO" + e.marca), "alumno de gA en su turno: " + texto);
		assertTrue(texto.contains("LISTADODEASISTENCIA-SEPTIEMBRE2026"), texto);
		assertTrue(texto.contains("LUNES-Turnode18:00a19:00"), texto);
		assertTrue(texto.contains("Total:1alumnos"), texto);
		assertFalse(texto.contains("E72HOMONIMO" + e.marca), "grupo homónimo de otra sede, mismo lunes y hora");
		assertFalse(texto.contains("E6B" + e.marca), "turno de gB, el mismo lunes");
		assertFalse(texto.contains("E6A" + e.marca), "en el turno de gA pero no en el grupo");
		assertFalse(texto.contains("E72BAJA" + e.marca), "de baja");
		for (String salud : List.of(DESCRIPCION, ALERGIAS, IMPIDE)) {
			assertFalse(texto.contains(salud), "sin salud en el PDF (D7-3)");
		}

		// El homónimo sale en el PDF de gestión (su turno existe), pero no en el del profesor.
		String deGestion = texto(pdfGestion());
		assertTrue(deGestion.contains("E72HOMONIMO" + e.marca) && deGestion.contains("E72TUTELADO" + e.marca));

		assertEquals(200, estado(head("/api/profesor/grupos/" + e.gA + "/asistencia?year=2026&month=9"), tProfA));
		MvcResult deB = peticion(get("/api/profesor/grupos/" + e.gB + "/asistencia?year=2026&month=9"), tProfB);
		assertEquals(200, deB.getResponse().getStatus());
		String textoB = texto(deB.getResponse().getContentAsByteArray());
		assertTrue(textoB.contains("E6B" + e.marca));
		assertFalse(textoB.contains("E72TUTELADO" + e.marca));
	}

	@Test
	void pdfAsistenciaGrupoAjeno_403() throws Exception {
		assertEquals(403, estado(get("/api/profesor/grupos/" + e.gB + "/asistencia?year=2026&month=9"), tProfA));
		assertEquals(403, estado(get("/api/profesor/grupos/" + gHomonimo + "/asistencia?year=2026&month=9"), tProfA),
				"mismo nombre que su grupo, otra sede");
		assertEquals(403, estado(get("/api/profesor/grupos/" + otra.gA + "/asistencia?year=2026&month=9"), tProfA));
		assertEquals(403, estado(get("/api/profesor/grupos/" + e.gA + "/asistencia?year=2026&month=9"), tProfOtra));
		assertEquals(403, estado(get("/api/profesor/grupos/999999999/asistencia?year=2026&month=9"), tProfA));
		assertEquals(403, estado(head("/api/profesor/grupos/" + e.gB + "/asistencia?year=2026&month=9"), tProfA));
		assertEquals(403, estado(get("/api/profesor/grupos/" + e.gB + "/asistencia?year=2026&month=13"), tProfA),
				"grupo ajeno con mes inválido: 403 antes que 400");
	}

	@Test
	void pdfAsistenciaMesInvalido_400() throws Exception {
		String base = "/api/profesor/grupos/" + e.gA + "/asistencia";
		for (String consulta : List.of("?year=2026&month=13", "?year=2026&month=0", "?year=2026&month=-1",
				"?year=0&month=9", "?year=99999&month=9", "?year=2026", "?month=9", "?year=abc&month=9")) {
			assertEquals(400, estado(get(base + consulta), tProfA), consulta);
		}
	}

	/**
	 * El PDF de gestión (firma por {@code String}) no cambia: mismo texto en cada página, fijado antes de la E7-2 con
	 * los datos de esta escuela (cuatro alumnos en tres turnos del lunes; el de baja no sale).
	 */
	@Test
	void pdfAsistenciaGestionNoCambia() throws Exception {
		List<String> paginas = paginas(pdfGestion());
		List<String> esperadas = new ArrayList<>(List.of(
				pagina(List.of(new Fila(e.aA, "E6A"), new Fila(tutelado, "E72TUTELADO"))),
				pagina(List.of(new Fila(e.aB, "E6B"))),
				pagina(List.of(new Fila(homonimo, "E72HOMONIMO")))));
		List<String> reales = new ArrayList<>(paginas);
		esperadas.sort(null);
		reales.sort(null);
		assertEquals(esperadas, reales);
	}

	// ------------------------------------------------------------------ utilidades

	private record Fila(long alumno, String prefijo) {
	}

	/** Texto de una página del PDF de asistencia de gestión (septiembre de 2026, lunes, taekwondo). */
	private String pagina(List<Fila> filas) {
		StringBuilder s = new StringBuilder("CLUBMOI'SKIMDOTaeKwonDoLISTADODEASISTENCIA-SEPTIEMBRE2026TAEKWONDO");
		s.append("Total:").append(filas.size()).append("alumnosLUNES-Turnode18:00a19:00");
		s.append("APTOGRADOLIC.FEDEDADNOMBREYAPELLIDOSNºEXP."); // cabecera en mayúsculas (CSS)
		for (Fila f : filas) {
			var datos = jdbc.queryForMap("SELECT nombre, apellidos, fecha_nacimiento, numero_expediente FROM alumno WHERE id = ?",
					f.alumno());
			LocalDate nacimiento = ((java.sql.Date) datos.get("fecha_nacimiento")).toLocalDate();
			assertTrue(((String) datos.get("nombre")).startsWith(f.prefijo()));
			s.append("0m").append(GRADO_SIN).append("NO").append(Period.between(nacimiento, LocalDate.now()).getYears())
					.append(datos.get("nombre")).append(datos.get("apellidos")).append(datos.get("numero_expediente"));
		}
		s.append("7142128");
		s.append("7142128");
		return s.toString();
	}

	/** Celda del grado de un alumno sin grado ({@code CinturonesPdf.celdaAsistencia(null)}), sin espacios. */
	private static final String GRADO_SIN = "";

	private byte[] pdfGestion() throws Exception {
		MvcResult r = peticion(get("/api/informes/asistencia").param("year", "2026").param("month", "9")
				.param("grupo", "lunes").param("deporte", "TAEKWONDO"), tGestor);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return r.getResponse().getContentAsByteArray();
	}

	private long eventosSalud(long alumno, long usuario) {
		return jdbc.queryForObject("""
				SELECT COUNT(*) FROM auditoria_evento
				WHERE modulo = 'salud' AND accion = 'READ' AND recurso_id = ? AND usuario_id = ?""", Long.class,
				alumno, usuario);
	}

	private long eventosSaludDelUsuario(long usuario) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_evento WHERE modulo = 'salud' AND usuario_id = ?",
				Long.class, usuario);
	}

	private long eventosConEndpoint(String endpoint) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_evento WHERE endpoint = ?", Long.class, endpoint);
	}

	private void assertSinClavesDeSalud(JsonNode raiz, String ruta) {
		List<JsonNode> pendientes = new ArrayList<>(List.of(raiz));
		while (!pendientes.isEmpty()) {
			JsonNode nodo = pendientes.remove(pendientes.size() - 1);
			for (Iterator<String> it = nodo.fieldNames(); it.hasNext();) {
				String campo = it.next();
				assertFalse(CLAVES_SALUD.contains(campo), "clave de salud '" + campo + "' en " + ruta);
			}
			nodo.forEach(pendientes::add);
		}
	}

	private long soloProfesor(EscuelaConSedesDePrueba x, String nombre) {
		long id = x.cuenta(usuarios, nombre);
		membresia(x, id, "PROFESOR");
		return id;
	}

	private void membresia(EscuelaConSedesDePrueba x, long usuario, String rol) {
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", usuario, x.org, rol);
	}

	private void responsableDeSede(EscuelaConSedesDePrueba x, long usuario, long sede) {
		membresia(x, usuario, "RESPONSABLE_SEDE");
		long membresia = jdbc.queryForObject("""
				SELECT id FROM membresia WHERE usuario_id = ? AND organizacion_id = ? AND rol = 'RESPONSABLE_SEDE'""",
				Long.class, usuario, x.org);
		jdbc.update("INSERT INTO membresia_sede (membresia_id, sede_id) VALUES (?, ?)", membresia, sede);
	}

	private void asignar(EscuelaConSedesDePrueba x, long profesor, long grupo, String rol) {
		jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) VALUES (?, ?, ?, ?)",
				x.org, profesor, grupo, rol);
	}

	private void miembro(long grupo, long alumno) {
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", grupo, alumno);
	}

	private static String texto(byte[] pdf) {
		try (PDDocument documento = PDDocument.load(pdf)) {
			return new PDFTextStripper().getText(documento).replaceAll("\\s+", "");
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static List<String> paginas(byte[] pdf) {
		try (PDDocument documento = PDDocument.load(pdf)) {
			List<String> paginas = new ArrayList<>();
			PDFTextStripper stripper = new PDFTextStripper();
			for (int i = 1; i <= documento.getNumberOfPages(); i++) {
				stripper.setStartPage(i);
				stripper.setEndPage(i);
				paginas.add(stripper.getText(documento).replaceAll("\\s+", ""));
			}
			return paginas;
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private int estado(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return peticion(peticion, token).getResponse().getStatus();
	}

	private MvcResult peticion(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
	}

	private static java.util.Set<String> campos(JsonNode nodo) {
		java.util.Set<String> campos = new java.util.HashSet<>();
		for (Iterator<String> it = nodo.fieldNames(); it.hasNext();) {
			campos.add(it.next());
		}
		return campos;
	}
}
