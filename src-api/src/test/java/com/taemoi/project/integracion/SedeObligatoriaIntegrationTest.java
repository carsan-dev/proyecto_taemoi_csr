package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.dtos.response.GrupoConAlumnosDTO;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.TarifaRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza 9-1 (decisiones 368 y 377) contra MySQL: con dos o más sedes activas, ninguna alta se queda sin sede.
 * <ul>
 * <li>Alta de alumno y de grupo sin sede: 400 «Indica la sede.» para propietario y gestión, como el responsable; con
 * una sede activa, esa; con ninguna, sin sede, como hasta ahora.</li>
 * <li>Editar un grupo: sin sede en el cuerpo conserva la suya; sin sede propia y con varias activas, 400; se puede
 * corregir a otra sede activa de la escuela, sin mover cargos ni cobros; el responsable, solo dentro de las suyas.</li>
 * <li>Finalizar una preinscripción con grupo sin sede y varias activas: 400, sin alumno ni correo.</li>
 * </ul>
 * Escuelas sintéticas de {@link EscuelaConSedesDePrueba} (A y B activas, C inactiva), una con B cerrada (una sede
 * activa) y otra con todas cerradas (ninguna). Datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SedeObligatoriaIntegrationTest extends MySqlIntegrationTest {

	private static final String INDICA_LA_SEDE = "Indica la sede.";
	private static final String GRUPO_SIN_SEDE = "El grupo no tiene sede. Asígnasela antes de finalizar.";
	private static final String TARIFA = "ADULTO";
	private static final String FINALIZAR_NUEVO = "{\"accionAlumno\":\"CREAR_NUEVO\",\"datosDeporte\":"
			+ "{\"tipoTarifa\":\"" + TARIFA + "\",\"cuantiaTarifa\":30.0}}";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;
	@Autowired TarifaRepository tarifas;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba una;
	EscuelaConSedesDePrueba ninguna;
	EscuelaConSedesDePrueba otra;
	String tPropietario, tGestor, tRespA, tGestorUna, tGestorNinguna;
	long plantilla;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e9-1-oblig");
		una = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e9-1-una");
		ninguna = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e9-1-ninguna");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e9-1-otra");
		jdbc.update("UPDATE sede SET activa = FALSE WHERE id = ?", una.sB);
		jdbc.update("UPDATE sede SET activa = FALSE WHERE organizacion_id = ?", ninguna.org);
		for (EscuelaConSedesDePrueba escuela : List.of(e, una, ninguna)) {
			TenantContext.ejecutarCon(escuela.org, () -> DisciplinasDePrueba.tarifa(tarifas, TARIFA, "30.00", null));
		}
		long propietario = e.cuenta(usuarios, "propietario");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROPIETARIO')", propietario,
				e.org);
		tPropietario = e.token(jwtService, usuarios, propietario);
		tGestor = e.token(jwtService, usuarios, e.gestor);
		tRespA = e.token(jwtService, usuarios, e.respA);
		tGestorUna = una.token(jwtService, usuarios, una.gestor);
		tGestorNinguna = ninguna.token(jwtService, usuarios, ninguna.gestor);
		plantilla = jdbc.queryForObject("""
				SELECT p.id FROM plantilla_preinscripcion p JOIN disciplina d ON d.id = p.disciplina_id
				WHERE p.organizacion_id = 1 AND d.codigo = 'TAEKWONDO' ORDER BY p.id LIMIT 1""", Long.class);
	}

	@AfterEach
	void sinFugas() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ altas

	@Test
	void altaDeAlumno_variasSedes_sinSede400ParaPropietarioYGestion() throws Exception {
		long alumnos = alumnosDe(e.org);
		for (String token : List.of(tPropietario, tGestor)) {
			MvcResult r = altaDeAlumno(token, null);
			assertEquals(400, r.getResponse().getStatus(), contenido(r));
			assertTrue(contenido(r).contains(INDICA_LA_SEDE), contenido(r));
		}
		assertEquals(alumnos, alumnosDe(e.org), "ningún alumno sin sede");
		MvcResult conSede = altaDeAlumno(tGestor, e.sB);
		assertEquals(201, conSede.getResponse().getStatus(), contenido(conSede));
		assertEquals(e.sB, e.sedeDe(json.readTree(contenido(conSede)).get("id").asLong()));
	}

	@Test
	void altaDeGrupo_variasSedes_sinSede400ParaPropietarioYGestion() throws Exception {
		long grupos = gruposDe(e.org);
		for (String token : List.of(tPropietario, tGestor)) {
			MvcResult r = peticion(conCuerpo(post("/api/grupos"), grupo("IT E9 sin sede " + marca(), null)), token);
			assertEquals(400, r.getResponse().getStatus(), contenido(r));
			assertTrue(contenido(r).contains(INDICA_LA_SEDE), contenido(r));
		}
		assertEquals(grupos, gruposDe(e.org), "ningún grupo sin sede");
		MvcResult conSede = peticion(conCuerpo(post("/api/grupos"), grupo("IT E9 con sede " + marca(), e.sB)), tGestor);
		assertEquals(201, conSede.getResponse().getStatus(), contenido(conSede));
		assertEquals(e.sB, sedeDelGrupo(json.readTree(contenido(conSede)).get("id").asLong()));
	}

	@Test
	void altas_unaSedeActiva_sinCambios() throws Exception {
		MvcResult alumno = altaDeAlumno(tGestorUna, null);
		assertEquals(201, alumno.getResponse().getStatus(), contenido(alumno));
		assertEquals(una.sA, una.sedeDe(json.readTree(contenido(alumno)).get("id").asLong()), "la única activa");
		MvcResult grupo = peticion(conCuerpo(post("/api/grupos"), grupo("IT E9 una " + marca(), null)), tGestorUna);
		assertEquals(201, grupo.getResponse().getStatus(), contenido(grupo));
		assertEquals(una.sA, sedeDelGrupo(json.readTree(contenido(grupo)).get("id").asLong()), "la única activa");
	}

	@Test
	void altas_ningunaSedeActiva_sinCambios() throws Exception {
		MvcResult alumno = altaDeAlumno(tGestorNinguna, null);
		assertEquals(201, alumno.getResponse().getStatus(), contenido(alumno));
		assertNull(ninguna.sedeDe(json.readTree(contenido(alumno)).get("id").asLong()), "sin sede, como hasta ahora");
		MvcResult grupo = peticion(conCuerpo(post("/api/grupos"), grupo("IT E9 ninguna " + marca(), null)),
				tGestorNinguna);
		assertEquals(201, grupo.getResponse().getStatus(), contenido(grupo));
		assertNull(sedeDelGrupo(json.readTree(contenido(grupo)).get("id").asLong()), "sin sede, como hasta ahora");
	}

	@Test
	void altaDelResponsable_sinCambios() throws Exception {
		MvcResult grupo = peticion(conCuerpo(post("/api/grupos"), grupo("IT E9 resp " + marca(), null)), tRespA);
		assertEquals(201, grupo.getResponse().getStatus(), contenido(grupo));
		assertEquals(e.sA, sedeDelGrupo(json.readTree(contenido(grupo)).get("id").asLong()), "su única sede");
		assertEquals(403, estado(conCuerpo(post("/api/grupos"), grupo("IT E9 resp B " + marca(), e.sB)), tRespA));
	}

	// ------------------------------------------------------------------ editar grupo

	@Test
	void editarGrupo_sinSedeEnElCuerpo_conservaLaSuya() throws Exception {
		long grupo = grupoDe(e.sB);
		assertEquals(200, estado(conCuerpo(put("/api/grupos/" + grupo), grupo("IT E9 editado " + marca(), null)), tGestor));
		assertEquals(e.sB, sedeDelGrupo(grupo));
	}

	@Test
	void editarGrupo_sinSedePropia_variasActivas_400SinCambiarNada() throws Exception {
		long grupo = grupoSinSede();
		String nombre = nombreDelGrupo(grupo);
		MvcResult r = peticion(conCuerpo(put("/api/grupos/" + grupo), grupo("IT E9 no cambia " + marca(), null)), tGestor);
		assertEquals(400, r.getResponse().getStatus(), contenido(r));
		assertTrue(contenido(r).contains(INDICA_LA_SEDE), contenido(r));
		assertNull(sedeDelGrupo(grupo));
		assertEquals(nombre, nombreDelGrupo(grupo), "la transacción se deshace entera");
		assertEquals(200, estado(conCuerpo(put("/api/grupos/" + grupo), grupo("IT E9 corregido " + marca(), e.sA)), tGestor));
		assertEquals(e.sA, sedeDelGrupo(grupo));
	}

	@Test
	void editarGrupo_unaSedeActiva_leRellenaLaUnica() throws Exception {
		jdbc.update("UPDATE grupo SET sede_id = NULL WHERE id = ?", una.gA2);
		assertEquals(200, estado(conCuerpo(put("/api/grupos/" + una.gA2), grupo("IT E9 rellena " + marca(), null)),
				tGestorUna));
		assertEquals(una.sA, sedeDelGrupo(una.gA2));
	}

	@Test
	void editarGrupo_corrigeASedeActivaDeLaEscuela_noInactivaNiAjena() throws Exception {
		long grupo = grupoDe(e.sA);
		assertEquals(200, estado(conCuerpo(put("/api/grupos/" + grupo), grupo("IT E9 a B " + marca(), e.sB)), tGestor));
		assertEquals(e.sB, sedeDelGrupo(grupo));
		assertEquals(400, estado(conCuerpo(put("/api/grupos/" + grupo), grupo("IT E9 a C " + marca(), e.sC)), tGestor),
				"sede inactiva");
		assertEquals(400, estado(conCuerpo(put("/api/grupos/" + grupo), grupo("IT E9 ajena " + marca(), otra.sA)), tGestor),
				"sede de otra escuela");
		assertEquals(e.sB, sedeDelGrupo(grupo));
	}

	@Test
	void editarGrupo_responsable_noMueveFueraDeSusSedes() throws Exception {
		long propio = grupoDe(e.sA);
		assertEquals(403, estado(conCuerpo(put("/api/grupos/" + propio), grupo("IT E9 fuera " + marca(), e.sB)), tRespA),
				"su grupo, a una sede que no es suya");
		assertEquals(e.sA, sedeDelGrupo(propio));
		long ajeno = grupoDe(e.sB);
		assertEquals(403, estado(conCuerpo(put("/api/grupos/" + ajeno), grupo("IT E9 ajeno " + marca(), e.sA)), tRespA),
				"un grupo de otra sede");
		assertEquals(e.sB, sedeDelGrupo(ajeno));
	}

	@Test
	void cambiarLaSedeDeUnGrupo_noMueveCargosNiCobros() throws Exception {
		long grupo = grupoDe(e.sA);
		long alumno = e.alumno("E9GRUPO" + marca(), e.sA, true);
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", grupo, alumno);
		long pendiente = cargo(alumno, e.sA, false);
		long cobrado = cargo(alumno, e.sA, true);
		jdbc.update("""
				INSERT INTO cobro (organizacion_id, producto_alumno_id, sede_id, alumno_id, concepto, importe, metodo,
				  fecha_cobro, origen, estado) VALUES (?, ?, ?, ?, 'CARGO IT', 10.00, 'EFECTIVO', NOW(), 'TESORERIA',
				  'ACTIVO')""", e.org, cobrado, e.sA, alumno);

		assertEquals(200, estado(conCuerpo(put("/api/grupos/" + grupo), grupo("IT E9 mueve " + marca(), e.sB)), tGestor));

		assertEquals(e.sB, sedeDelGrupo(grupo));
		assertEquals(e.sA, sedeDelCargo(pendiente));
		assertEquals(e.sA, sedeDelCargo(cobrado));
		assertEquals(e.sA, jdbc.queryForObject("SELECT sede_id FROM cobro WHERE producto_alumno_id = ?", Long.class, cobrado));
		assertEquals(e.sA, e.sedeDe(alumno), "ni la sede del alumno");
	}

	// ------------------------------------------------------------------ preinscripción

	@Test
	void finalizar_grupoSinSede_variasActivas_400SinAlumnoNiCorreo() throws Exception {
		long grupo = grupoSinSede();
		long turno = turno(e.org, grupo);
		String referencia = preinscripcion(e.org, grupo, turno);
		long alumnos = alumnosDe(e.org);
		long expediente = contadorExpediente(e.org);
		clearInvocations(mailSender);

		for (String token : List.of(tPropietario, tGestor)) {
			MvcResult r = peticion(conCuerpo(post("/api/preinscripciones/" + referencia + "/finalizar"), FINALIZAR_NUEVO),
					token);
			assertEquals(400, r.getResponse().getStatus(), contenido(r));
			assertTrue(contenido(r).contains(GRUPO_SIN_SEDE), contenido(r));
		}

		assertEquals(alumnos, alumnosDe(e.org), "ningún alumno");
		assertEquals(expediente, contadorExpediente(e.org), "ningún número de expediente gastado");
		Map<String, Object> fila = jdbc.queryForMap(
				"SELECT estado, alumno_id, email_finalizacion_estado FROM preinscripcion WHERE referencia = ?", referencia);
		assertEquals("PENDIENTE", fila.get("estado"));
		assertNull(fila.get("alumno_id"));
		assertEquals("NO_ENVIADO", fila.get("email_finalizacion_estado"), "ningún correo pendiente");
		verifyNoInteractions(mailSender);

		// Con la sede ya asignada al grupo, se finaliza y el alumno nace en ella.
		jdbc.update("UPDATE grupo SET sede_id = ? WHERE id = ?", e.sB, grupo);
		MvcResult r = peticion(conCuerpo(post("/api/preinscripciones/" + referencia + "/finalizar"), FINALIZAR_NUEVO),
				tGestor);
		assertEquals(200, r.getResponse().getStatus(), contenido(r));
		long alumno = jdbc.queryForObject("SELECT alumno_id FROM preinscripcion WHERE referencia = ?", Long.class, referencia);
		assertEquals(e.sB, e.sedeDe(alumno));
	}

	@Test
	void finalizar_vincularFichaExistente_grupoSinSede_variasActivas_400() throws Exception {
		long grupo = grupoSinSede();
		String referencia = preinscripcion(e.org, grupo, turno(e.org, grupo));
		String cuerpo = "{\"accionAlumno\":\"VINCULAR_EXISTENTE\",\"alumnoId\":" + e.aA + ",\"datosDeporte\":"
				+ "{\"tipoTarifa\":\"" + TARIFA + "\",\"cuantiaTarifa\":30.0}}";
		MvcResult r = peticion(conCuerpo(post("/api/preinscripciones/" + referencia + "/finalizar"), cuerpo), tGestor);
		assertEquals(400, r.getResponse().getStatus(), contenido(r));
		assertTrue(contenido(r).contains(GRUPO_SIN_SEDE), contenido(r));
		assertEquals("PENDIENTE",
				jdbc.queryForObject("SELECT estado FROM preinscripcion WHERE referencia = ?", String.class, referencia));
	}

	@Test
	void finalizar_grupoSinSede_unaSedeActiva_laUnica() throws Exception {
		jdbc.update("UPDATE grupo SET sede_id = NULL WHERE id = ?", una.gSin);
		String referencia = preinscripcion(una.org, una.gSin, turno(una.org, una.gSin));
		MvcResult r = peticion(conCuerpo(post("/api/preinscripciones/" + referencia + "/finalizar"), FINALIZAR_NUEVO),
				tGestorUna);
		assertEquals(200, r.getResponse().getStatus(), contenido(r));
		long alumno = jdbc.queryForObject("SELECT alumno_id FROM preinscripcion WHERE referencia = ?", Long.class, referencia);
		assertEquals(una.sA, una.sedeDe(alumno));
	}

	@Test
	void finalizar_grupoSinSede_ningunaActiva_sinSedeComoHastaAhora() throws Exception {
		jdbc.update("UPDATE grupo SET sede_id = NULL WHERE id = ?", ninguna.gSin);
		String referencia = preinscripcion(ninguna.org, ninguna.gSin, turno(ninguna.org, ninguna.gSin));
		MvcResult r = peticion(conCuerpo(post("/api/preinscripciones/" + referencia + "/finalizar"), FINALIZAR_NUEVO),
				tGestorNinguna);
		assertEquals(200, r.getResponse().getStatus(), contenido(r));
		long alumno = jdbc.queryForObject("SELECT alumno_id FROM preinscripcion WHERE referencia = ?", Long.class, referencia);
		assertNull(ninguna.sedeDe(alumno));
	}

	// ------------------------------------------------------------------ datos

	private static String marca() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private long grupoDe(long sede) {
		GrupoConAlumnosDTO dto = new GrupoConAlumnosDTO();
		dto.setNombre("IT E9 grupo " + marca());
		dto.setDeporte("TAEKWONDO");
		dto.setSedeId(sede);
		return TenantContext.calcularCon(e.org, () -> grupoService.crearGrupo(dto).getId());
	}

	private long grupoSinSede() {
		long grupo = grupoDe(e.sA);
		jdbc.update("UPDATE grupo SET sede_id = NULL WHERE id = ?", grupo);
		return grupo;
	}

	private long turno(long org, long grupo) {
		jdbc.update("""
				INSERT INTO turno (dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id)
				VALUES ('Lunes', '17:00', '18:00', NULL, ?, ?)""", grupo, org);
		return jdbc.queryForObject("SELECT MAX(id) FROM turno WHERE grupo_id = ?", Long.class, grupo);
	}

	/** Solicitud PENDIENTE de un adulto (la plantilla es la de la escuela 1: FK simple). */
	private String preinscripcion(long org, long grupo, long turno) {
		long disciplina = jdbc.queryForObject(
				"SELECT id FROM disciplina WHERE organizacion_id = ? AND codigo = 'TAEKWONDO'", Long.class, org);
		String referencia = "IT-E91-" + marca() + marca().substring(0, 4);
		String dni = String.format("%08d", java.util.concurrent.ThreadLocalRandom.current().nextLong(10_000_000L,
				99_999_999L)) + "T";
		jdbc.update("""
				INSERT INTO preinscripcion (referencia, temporada, estado, nombre, apellidos, dni, identidad_hash,
				  fecha_nacimiento, direccion, telefono, email, tiene_discapacidad, consentimiento_fotografico,
				  aceptacion_normas, firmante_nombre, firma, plantilla_id, plantilla_snapshot, pdf_firmado,
				  token_documento_hash, creada_en, actualizada_en, grupo_id, organizacion_id, disciplina_id)
				VALUES (?, '2025-2026', 'PENDIENTE', 'Ana', 'Prueba', ?, ?, '1990-05-05', 'Calle 1', '600000000', ?, 0,
				  0, 1, 'Firmante Prueba', x'00', ?, '{}', x'00', ?, '2025-09-10 12:00:00', '2025-09-10 12:00:00', ?,
				  ?, ?)""", referencia, dni, UUID.randomUUID().toString().replace("-", ""), "ana@example.test", plantilla,
				"e".repeat(64), grupo, org, disciplina);
		long id = jdbc.queryForObject("SELECT id FROM preinscripcion WHERE referencia = ?", Long.class, referencia);
		jdbc.update("INSERT INTO preinscripcion_turno (preinscripcion_id, turno_id) VALUES (?, ?)", id, turno);
		return referencia;
	}

	private long cargo(long alumno, long sede, boolean pagado) {
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, concepto, precio, cantidad, pagado, fecha_asignacion,
				  organizacion_id, sede_id) VALUES (?, 'CARGO IT', 10.0, 1, ?, NOW(), ?, ?)""", alumno, pagado, e.org, sede);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE alumno_id = ?", Long.class, alumno);
	}

	private MvcResult altaDeAlumno(String token, Long sede) throws Exception {
		String cuerpo = """
				{"nombre":"ALTA E9","apellidos":"PRUEBA %s","fechaNacimiento":"1990-03-04","direccion":"Calle 1",
				 "telefono":600000009,"autorizacionWeb":true%s,
				 "deportesInicial":[{"deporte":"TAEKWONDO","fechaAlta":"2024-09-01","tipoTarifa":"%s",
				   "cuantiaTarifa":30.0,"competidor":false,"tieneLicencia":false}]}"""
				.formatted(marca(), sede == null ? "" : ",\"sedePrincipalId\":" + sede, TARIFA);
		return peticion(multipart("/api/alumnos/crear").param("nuevo", cuerpo), token);
	}

	private static String grupo(String nombre, Long sede) {
		return "{\"nombre\":\"" + nombre + "\",\"deporte\":\"TAEKWONDO\"" + (sede == null ? "" : ",\"sedeId\":" + sede)
				+ "}";
	}

	private Long sedeDelGrupo(long grupo) {
		return jdbc.queryForObject("SELECT sede_id FROM grupo WHERE id = ?", Long.class, grupo);
	}

	private String nombreDelGrupo(long grupo) {
		return jdbc.queryForObject("SELECT nombre FROM grupo WHERE id = ?", String.class, grupo);
	}

	private Long sedeDelCargo(long cargo) {
		return jdbc.queryForObject("SELECT sede_id FROM producto_alumno WHERE id = ?", Long.class, cargo);
	}

	private long alumnosDe(long org) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM alumno WHERE organizacion_id = ?", Long.class, org);
	}

	private long gruposDe(long org) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM grupo WHERE organizacion_id = ?", Long.class, org);
	}

	private long contadorExpediente(long org) {
		return jdbc.queryForObject("SELECT COALESCE(MAX(numero_expediente), 0) FROM alumno WHERE organizacion_id = ?",
				Long.class, org);
	}

	// ------------------------------------------------------------------ HTTP

	private static MockHttpServletRequestBuilder conCuerpo(MockHttpServletRequestBuilder peticion, String cuerpo) {
		return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private int estado(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return peticion(peticion, token).getResponse().getStatus();
	}

	private MvcResult peticion(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
	}

	private static String contenido(MvcResult r) throws Exception {
		return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}
}
