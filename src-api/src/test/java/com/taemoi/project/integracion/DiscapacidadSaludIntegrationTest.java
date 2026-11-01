package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.cifrado.CifradorDatos;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.TarifaRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.AlumnoSaludService;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Tipo de discapacidad por HTTP real contra MySQL. El booleano sigue en {@code alumno} y en claro; los tipos y el texto de
 * «Otra» van cifrados en {@code alumno_salud}, ligados a su fila y columna. {@code PUT /salud} es la escritura de todo
 * ello, sin escrituras parciales; {@code tiene_discapacidad = false} implica sin tipos, venga de donde venga la escritura.
 * Escuelas sintéticas de {@link EscuelaConSedesDePrueba}. Datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
class DiscapacidadSaludIntegrationTest extends MySqlIntegrationTest {

	private static final String TARIFA = "ADULTO";
	private static final String FORMATO_NO_VALIDO = "Los datos de salud no tienen un formato válido.";
	private static final String INDICA_SI_TIENE = "Indica si tiene discapacidad.";
	private static final String INDICA_CUAL = "Indica cuál es la discapacidad.";
	private static final String ANONIMIZADO = "Este alumno está eliminado (anonimizado) y no se puede modificar.";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;
	@Autowired TarifaRepository tarifas;
	@Autowired CifradorDatos cifrador;
	@Autowired AlumnoSaludService alumnoSalud;
	@Autowired TransactionTemplate tx;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	String tGestor, tPropietario, tRespA, tGestorOtra;
	long plantilla;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-discapacidad");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-discapacidad-otra");
		TenantContext.ejecutarCon(e.org, () -> DisciplinasDePrueba.tarifa(tarifas, TARIFA, "30.00", null));
		long propietario = e.cuenta(usuarios, "propietario");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROPIETARIO')", propietario,
				e.org);
		tGestor = e.token(jwtService, usuarios, e.gestor);
		tPropietario = e.token(jwtService, usuarios, propietario);
		tRespA = e.token(jwtService, usuarios, e.respA);
		tGestorOtra = otra.token(jwtService, usuarios, otra.gestor);
		plantilla = jdbc.queryForObject("""
				SELECT p.id FROM plantilla_preinscripcion p JOIN disciplina d ON d.id = p.disciplina_id
				WHERE p.organizacion_id = 1 AND d.codigo = 'TAEKWONDO' ORDER BY p.id LIMIT 1""", Long.class);
	}

	@AfterEach
	void sinFugas() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ PUT y GET

	@Test
	void conTipos_guardaCifrado_yLaLecturaDevuelveLoGuardado(CapturedOutput salida) throws Exception {
		long alumno = alumno(e.sA);
		String otraTexto = "Texto-ficticio-" + marca();

		JsonNode guardado = ok(putSalud(alumno, """
				{"tieneDiscapacidad":true,"discapacidadTipos":["OTRA","FISICA","FISICA"],"discapacidadOtra":"  %s  "}
				""".formatted(otraTexto), tGestor));

		assertTrue(guardado.get("tieneDiscapacidad").asBoolean());
		assertEquals(List.of("FISICA", "OTRA"), tipos(guardado), "sin duplicados y en el orden del enum");
		assertEquals(otraTexto, guardado.get("discapacidadOtra").asText(), "tras strip");

		assertEquals(Boolean.TRUE, bandera(alumno));
		Map<String, Object> fila = fila(alumno);
		String tiposCrudos = (String) fila.get("discapacidad_tipos");
		String otraCruda = (String) fila.get("discapacidad_otra");
		assertTrue(tiposCrudos.startsWith("v1:"), "tipos cifrados con versión de clave");
		assertTrue(otraCruda.startsWith("v1:"), "texto cifrado con versión de clave");
		assertFalse(tiposCrudos.contains("FISICA"), "los tipos no están en claro");
		assertFalse(tiposCrudos.contains("OTRA"), "los tipos no están en claro");
		assertFalse(otraCruda.contains(otraTexto), "el texto no está en claro");
		String tiposEnClaro = cifrador.descifrar(tiposCrudos, contexto("discapacidad_tipos", alumno));
		assertEquals("FISICA,OTRA", tiposEnClaro.stripTrailing(), "nombres del enum, en su orden");
		assertEquals(64, tiposEnClaro.length(), "con relleno hasta la longitud fija");
		assertEquals(otraTexto, cifrador.descifrar(otraCruda, contexto("discapacidad_otra", alumno)));

		JsonNode leido = ok(getSalud(alumno, tGestor));
		assertTrue(leido.get("tieneDiscapacidad").asBoolean());
		assertEquals(List.of("FISICA", "OTRA"), tipos(leido));
		assertEquals(otraTexto, leido.get("discapacidadOtra").asText());
		assertFalse(salida.getAll().contains(otraTexto), "el texto de «Otra» aparece en el log");
	}

	@Test
	void conTrue_losTiposSonOpcionales_ySinEllosLasColumnasQuedanNull() throws Exception {
		long alumno = alumno(e.sA);
		ok(putSalud(alumno, "{\"tieneDiscapacidad\":true,\"discapacidadTipos\":[\"VISUAL\"]}", tGestor));

		JsonNode vacios = ok(putSalud(alumno, "{\"tieneDiscapacidad\":true,\"discapacidadTipos\":[]}", tGestor));
		assertEquals(List.of(), tipos(vacios));
		assertNull(fila(alumno).get("discapacidad_tipos"), "nunca se cifra una cadena vacía");
		assertEquals(Boolean.TRUE, bandera(alumno));

		ok(putSalud(alumno, "{\"tieneDiscapacidad\":true,\"discapacidadTipos\":[\"VISUAL\"]}", tGestor));
		JsonNode nulos = ok(putSalud(alumno, "{\"tieneDiscapacidad\":true}", tGestor));
		assertEquals(List.of(), tipos(nulos));
		assertNull(fila(alumno).get("discapacidad_tipos"));
		assertNull(fila(alumno).get("discapacidad_otra"));
	}

	@Test
	void sinOtra_elTextoSeDescarta_aunqueSeaDemasiadoLargo() throws Exception {
		long alumno = alumno(e.sA);
		JsonNode r = ok(putSalud(alumno, """
				{"tieneDiscapacidad":true,"discapacidadTipos":["VISUAL","AUDITIVA"],"discapacidadOtra":"%s"}
				""".formatted("x".repeat(5000)), tGestor));

		assertEquals(List.of("VISUAL", "AUDITIVA"), tipos(r));
		assertTrue(r.get("discapacidadOtra").isNull());
		assertNull(fila(alumno).get("discapacidad_otra"));
	}

	@Test
	void conFalse_seEnvieLoQueSeEnvie_booleanoAFalseYColumnasNull() throws Exception {
		long alumno = alumno(e.sA);
		guardarConTipos(alumno);

		JsonNode r = ok(putSalud(alumno, """
				{"tieneDiscapacidad":false,"discapacidadTipos":["OTRA",null],"discapacidadOtra":"%s"}
				""".formatted("x".repeat(5000)), tGestor));

		assertFalse(r.get("tieneDiscapacidad").asBoolean());
		assertEquals(List.of(), tipos(r));
		assertTrue(r.get("discapacidadOtra").isNull());
		assertEquals(Boolean.FALSE, bandera(alumno));
		assertNull(fila(alumno).get("discapacidad_tipos"));
		assertNull(fila(alumno).get("discapacidad_otra"));
	}

	@Test
	void sinCamposDeDiscapacidad_noCambiaNadaDeDiscapacidad() throws Exception {
		long alumno = alumno(e.sA);
		guardarConTipos(alumno);
		Map<String, Object> antes = fila(alumno);

		JsonNode r = ok(putSalud(alumno, "{\"alergias\":\"Alergia ficticia\"}", tGestor));

		assertTrue(r.get("tieneDiscapacidad").asBoolean(), "el booleano es del alumno y va en claro");
		assertEquals(List.of(), tipos(r), "no se enseña lo guardado: sería una lectura sin auditar");
		assertTrue(r.get("discapacidadOtra").isNull(), "no se enseña lo guardado: sería una lectura sin auditar");
		assertEquals(Boolean.TRUE, bandera(alumno));
		Map<String, Object> despues = fila(alumno);
		assertEquals(antes.get("discapacidad_tipos"), despues.get("discapacidad_tipos"), "ni se recifra");
		assertEquals(antes.get("discapacidad_otra"), despues.get("discapacidad_otra"), "ni se recifra");
		assertNotNull(despues.get("alergias"), "lo demás sigue siendo reemplazo completo");

		long sinBandera = alumno(e.sA);
		ok(putSalud(sinBandera, "{\"alergias\":\"Alergia ficticia\"}", tGestor));
		assertNull(bandera(sinBandera), "un alumno sin dato sigue sin dato");
	}

	/** {@code PUT /salud} no es una lectura: no devuelve nada que no viniera en la petición ni deja evento READ. */
	@Test
	void putVacio_noDevuelveLoGuardado_niDejaLectura() throws Exception {
		long alumno = alumno(e.sA);
		ok(putSalud(alumno, """
				{"descripcion":"Descripcion ficticia","impideDetalle":"Detalle ficticio","alergias":"Alergia ficticia",
				 "tieneDiscapacidad":true,"discapacidadTipos":["FISICA","OTRA"],"discapacidadOtra":"Texto ficticio"}
				""", tGestor));
		int lecturas = lecturas(alumno);

		JsonNode r = ok(putSalud(alumno, "{}", tGestor));

		assertTrue(r.get("tieneDiscapacidad").asBoolean());
		assertEquals(List.of(), tipos(r));
		assertTrue(r.get("discapacidadOtra").isNull());
		assertTrue(r.get("descripcion").isNull(), "reemplazo completo: lo que no se envía queda vacío");
		assertTrue(r.get("impideDetalle").isNull());
		assertTrue(r.get("alergias").isNull());
		assertEquals(lecturas, lecturas(alumno), "el PUT no es una lectura");
	}

	/** Lo enviado y ya validado: sin duplicados, en el orden del enum y tras strip; el texto de «Otra», también. */
	@Test
	void conBooleano_laRespuestaLlevaLoEnviado_yLosTextos() throws Exception {
		long alumno = alumno(e.sA);

		JsonNode r = ok(putSalud(alumno, """
				{"descripcion":"  Descripcion ficticia  ","impideDetalle":"Detalle ficticio","alergias":"Alergia ficticia",
				 "tieneDiscapacidad":true,"discapacidadTipos":["OTRA","VISUAL"],"discapacidadOtra":"  Texto ficticio  "}
				""", tGestor));

		assertEquals("Descripcion ficticia", r.get("descripcion").asText());
		assertEquals("Detalle ficticio", r.get("impideDetalle").asText());
		assertEquals("Alergia ficticia", r.get("alergias").asText());
		assertEquals(List.of("VISUAL", "OTRA"), tipos(r));
		assertEquals("Texto ficticio", r.get("discapacidadOtra").asText());
		assertTrue(r.get("registrado").asBoolean());
	}

	@Test
	void sinBooleano_conTiposOTexto_400_sinEscritura() throws Exception {
		long alumno = alumno(e.sA);
		guardarConTipos(alumno);
		Map<String, Object> antes = fila(alumno);

		assertRechazado(putSalud(alumno, "{\"alergias\":\"Cambio\",\"discapacidadTipos\":[\"FISICA\"]}", tGestor), 400,
				INDICA_SI_TIENE);
		assertRechazado(putSalud(alumno, "{\"alergias\":\"Cambio\",\"discapacidadOtra\":\"Texto\"}", tGestor), 400,
				INDICA_SI_TIENE);

		assertEquals(antes, fila(alumno));
		assertEquals(Boolean.TRUE, bandera(alumno));
	}

	@Test
	void sinBooleano_conListaVaciaYTextoEnBlanco_noCambiaNada() throws Exception {
		long alumno = alumno(e.sA);
		guardarConTipos(alumno);
		Map<String, Object> antes = fila(alumno);

		ok(putSalud(alumno, "{\"discapacidadTipos\":[],\"discapacidadOtra\":\"   \"}", tGestor));

		assertEquals(antes.get("discapacidad_tipos"), fila(alumno).get("discapacidad_tipos"));
		assertEquals(antes.get("discapacidad_otra"), fila(alumno).get("discapacidad_otra"));
		assertEquals(Boolean.TRUE, bandera(alumno));
	}

	@Test
	void otraSinTexto_400_sinEscrituraParcial() throws Exception {
		long alumno = alumno(e.sA);
		ok(putSalud(alumno, "{\"tieneDiscapacidad\":false,\"alergias\":\"Alergia ficticia\"}", tGestor));
		Map<String, Object> antes = fila(alumno);

		for (String sinTexto : List.of("", ",\"discapacidadOtra\":null", ",\"discapacidadOtra\":\"   \"")) {
			assertRechazado(putSalud(alumno,
					"{\"tieneDiscapacidad\":true,\"discapacidadTipos\":[\"OTRA\"],\"alergias\":\"Cambio\"" + sinTexto + "}",
					tGestor), 400, INDICA_CUAL);
		}

		assertEquals(antes, fila(alumno), "ni los textos ni actualizado_en cambian");
		assertEquals(Boolean.FALSE, bandera(alumno));
	}

	@Test
	void enumInvalido_400_sinRepetirElValor_niEnLaRespuestaNiEnElLog(CapturedOutput salida) throws Exception {
		long alumno = alumno(e.sA);
		guardarConTipos(alumno);
		Map<String, Object> antes = fila(alumno);
		String valor = "VALOR_FICTICIO_" + marca();

		MvcResult r = putSalud(alumno,
				"{\"tieneDiscapacidad\":false,\"discapacidadTipos\":[\"" + valor + "\"],\"alergias\":\"Cambio\"}", tGestor);

		assertRechazado(r, 400, FORMATO_NO_VALIDO);
		assertFalse(cuerpo(r).contains(valor));
		assertFalse(salida.getAll().contains(valor), "el valor rechazado aparece en el log");
		assertEquals(antes, fila(alumno));
		assertEquals(Boolean.TRUE, bandera(alumno));
		assertEquals(0, jdbc.queryForObject("""
				SELECT COUNT(*) FROM auditoria_evento
				WHERE payload_json LIKE ? OR resumen LIKE ? OR query_params_json LIKE ?""", Integer.class,
				"%" + valor + "%", "%" + valor + "%", "%" + valor + "%"));
	}

	@Test
	void elementoNuloEnLaLista_400_sinEscritura() throws Exception {
		long alumno = alumno(e.sA);
		guardarConTipos(alumno);
		Map<String, Object> antes = fila(alumno);

		MvcResult r = putSalud(alumno,
				"{\"tieneDiscapacidad\":true,\"discapacidadTipos\":[\"FISICA\",null],\"alergias\":\"Cambio\"}", tGestor);

		assertEquals(400, r.getResponse().getStatus(), cuerpo(r));
		assertEquals(antes, fila(alumno));
	}

	@Test
	void textoDeOtraDemasiadoLargo_400_sinRepetirlo_ySinEscritura(CapturedOutput salida) throws Exception {
		long alumno = alumno(e.sA);
		ok(putSalud(alumno, "{\"tieneDiscapacidad\":false}", tGestor));
		Map<String, Object> antes = fila(alumno);
		String marcador = "Largo-ficticio-" + marca();

		MvcResult r = putSalud(alumno, """
				{"tieneDiscapacidad":true,"discapacidadTipos":["OTRA"],"discapacidadOtra":"%s%s"}
				""".formatted(marcador, "x".repeat(4001)), tGestor);

		assertEquals(400, r.getResponse().getStatus(), cuerpo(r));
		assertFalse(cuerpo(r).contains(marcador));
		assertFalse(salida.getAll().contains(marcador), "el texto rechazado aparece en el log");
		assertEquals(antes, fila(alumno));
		assertEquals(Boolean.FALSE, bandera(alumno));
	}

	// ------------------------------------------------------------------ contexto de cifrado

	@Test
	void elValorCifradoCopiadoAOtroAlumno_oAOtraColumna_noSeDescifra() throws Exception {
		long origen = alumno(e.sA);
		guardarConTipos(origen);
		String tiposCrudos = (String) fila(origen).get("discapacidad_tipos");

		long destino = alumno(e.sA);
		ok(putSalud(destino, "{\"tieneDiscapacidad\":true}", tGestor));
		jdbc.update("UPDATE alumno_salud SET discapacidad_tipos = ? WHERE alumno_id = ?", tiposCrudos, destino);
		MvcResult otroAlumno = getSalud(destino, tGestor);
		assertEquals(500, otroAlumno.getResponse().getStatus(), cuerpo(otroAlumno));
		assertFalse(cuerpo(otroAlumno).contains("FISICA"));

		jdbc.update("UPDATE alumno_salud SET discapacidad_tipos = NULL, discapacidad_otra = ? WHERE alumno_id = ?",
				tiposCrudos, destino);
		MvcResult otraColumna = getSalud(destino, tGestor);
		assertEquals(500, otraColumna.getResponse().getStatus(), cuerpo(otraColumna));
		assertFalse(cuerpo(otraColumna).contains("FISICA"));
	}

	/** Quien lee la base sin la clave no distingue qué tipos hay por la longitud del cifrado. */
	@Test
	void dosCombinacionesDeLongitudDistinta_danLaMismaLongitudCifrada_yLaLecturaQuitaElRelleno() throws Exception {
		long corta = alumno(e.sA);
		long larga = alumno(e.sA);
		ok(putSalud(corta, "{\"tieneDiscapacidad\":true,\"discapacidadTipos\":[\"VISUAL\"]}", tGestor));
		ok(putSalud(larga, """
				{"tieneDiscapacidad":true,"discapacidadTipos":["FISICA","INTELECTUAL","VISUAL","AUDITIVA","PSICOSOCIAL","OTRA"],
				 "discapacidadOtra":"Texto ficticio"}""", tGestor));

		assertEquals(((String) fila(corta).get("discapacidad_tipos")).length(),
				((String) fila(larga).get("discapacidad_tipos")).length());

		assertEquals(List.of("VISUAL"), tipos(ok(getSalud(corta, tGestor))));
		assertEquals(List.of("FISICA", "INTELECTUAL", "VISUAL", "AUDITIVA", "PSICOSOCIAL", "OTRA"),
				tipos(ok(getSalud(larga, tGestor))));
	}

	/** Lo guardado antes del relleno se sigue leyendo. */
	@Test
	void unValorSinRelleno_seSigueLeyendo() throws Exception {
		long alumno = alumno(e.sA);
		ok(putSalud(alumno, "{\"tieneDiscapacidad\":true}", tGestor));
		jdbc.update("UPDATE alumno_salud SET discapacidad_tipos = ? WHERE alumno_id = ?",
				cifrador.cifrar("AUDITIVA", contexto("discapacidad_tipos", alumno)), alumno);

		assertEquals(List.of("AUDITIVA"), tipos(ok(getSalud(alumno, tGestor))));
	}

	@Test
	void losTextosLibresNoSeRellenan() throws Exception {
		long alumno = alumno(e.sA);
		ok(putSalud(alumno, """
				{"alergias":"Alergia ficticia","tieneDiscapacidad":true,"discapacidadTipos":["OTRA"],
				 "discapacidadOtra":"Texto ficticio"}""", tGestor));
		Map<String, Object> crudo = fila(alumno);

		assertEquals("Alergia ficticia", cifrador.descifrar((String) crudo.get("alergias"), contexto("alergias", alumno)));
		assertEquals("Texto ficticio",
				cifrador.descifrar((String) crudo.get("discapacidad_otra"), contexto("discapacidad_otra", alumno)));
	}

	// ------------------------------------------------------------------ lectura

	@Test
	void sinDiscapacidad_laLecturaNoEnsenaRestos_niLosDescifra() throws Exception {
		long alumno = alumno(e.sA);
		ok(putSalud(alumno, "{\"tieneDiscapacidad\":false}", tGestor));
		// Restos que no deberían existir y que además no se pueden descifrar: leerlos daría 500.
		jdbc.update("UPDATE alumno_salud SET discapacidad_tipos = 'v1:resto', discapacidad_otra = 'v1:resto'"
				+ " WHERE alumno_id = ?", alumno);

		JsonNode falso = ok(getSalud(alumno, tGestor));
		assertFalse(falso.get("tieneDiscapacidad").asBoolean());
		assertEquals(List.of(), tipos(falso));
		assertTrue(falso.get("discapacidadOtra").isNull());

		jdbc.update("UPDATE alumno SET tiene_discapacidad = NULL WHERE id = ?", alumno);
		JsonNode nulo = ok(getSalud(alumno, tGestor));
		assertTrue(nulo.get("tieneDiscapacidad").isNull());
		assertEquals(List.of(), tipos(nulo));
		assertTrue(nulo.get("discapacidadOtra").isNull());
	}

	@Test
	void sinFila_laLecturaDaElBooleanoDelAlumno_yNoCreaFila() throws Exception {
		long alumno = alumno(e.sA);
		jdbc.update("UPDATE alumno SET tiene_discapacidad = 1 WHERE id = ?", alumno);

		JsonNode r = ok(getSalud(alumno, tGestor));

		assertFalse(r.get("registrado").asBoolean());
		assertTrue(r.get("tieneDiscapacidad").asBoolean());
		assertEquals(List.of(), tipos(r));
		assertTrue(r.get("discapacidadOtra").isNull());
		assertEquals(0, filas(alumno));
	}

	@Test
	void laLecturaSigueAuditadaYNoStore_yElPutNoDejaCuerpoEnLaAuditoria() throws Exception {
		long alumno = alumno(e.sA);
		String marcador = "Auditoria-ficticia-" + marca();
		MvcResult guardado = putSalud(alumno, """
				{"tieneDiscapacidad":true,"discapacidadTipos":["OTRA"],"discapacidadOtra":"%s"}
				""".formatted(marcador), tGestor);
		assertEquals(200, guardado.getResponse().getStatus(), cuerpo(guardado));
		assertTrue(guardado.getResponse().getHeader("Cache-Control").contains("no-store"));
		assertRechazado(putSalud(alumno, "{\"tieneDiscapacidad\":true,\"discapacidadTipos\":[\"OTRA\"]}", tGestor), 400,
				INDICA_CUAL);

		int lecturas = lecturas(alumno);
		MvcResult leido = getSalud(alumno, tGestor);
		assertEquals(200, leido.getResponse().getStatus());
		assertTrue(leido.getResponse().getHeader("Cache-Control").contains("no-store"));
		assertEquals(lecturas + 1, lecturas(alumno), "una fila por lectura");

		String ruta = "/api/alumnos/" + alumno + "/salud";
		assertEquals(2, jdbc.queryForObject("""
				SELECT COUNT(*) FROM auditoria_evento
				WHERE endpoint = ? AND accion = 'UPDATE' AND payload_json = '[payload omitted: datos de salud]'""",
				Integer.class, ruta), "el PUT aceptado y el rechazado, sin cuerpo");
		assertEquals(0, jdbc.queryForObject("""
				SELECT COUNT(*) FROM auditoria_evento
				WHERE payload_json LIKE ? OR resumen LIKE ? OR query_params_json LIKE ?""", Integer.class,
				"%" + marcador + "%", "%" + marcador + "%", "%" + marcador + "%"));
	}

	/**
	 * En una ruta de salud ningún parámetro de la URL llega a {@code auditoria_evento}: con cualquier método y estado,
	 * también en las peticiones que no llegan al controlador.
	 */
	@Test
	void losParametrosDeLaUrlDeSalud_noLleganALaAuditoria_conCualquierMetodoYEstado() throws Exception {
		long alumno = alumno(e.sA);
		long deOtraSede = alumno(e.sB);
		long sinSesion = alumno(e.sA);
		long noPermitido = alumno(e.sA);
		String marcador = "Parametro-ficticio-" + marca();
		String parametros = "?discapacidadOtra=" + marcador + "&alergias=" + marcador;
		String ruta = "/api/alumnos/" + alumno + "/salud";

		assertEquals(200, putSalud(ruta + parametros, "{\"tieneDiscapacidad\":false}", tGestor).getResponse().getStatus());
		assertEquals(200, peticion(get(ruta + parametros), tGestor).getResponse().getStatus());
		assertEquals(400, putSalud(ruta + parametros, "{\"lesionEnfermedad\":\"asma\"}", tGestor).getResponse().getStatus());
		assertEquals(403, putSalud("/api/alumnos/" + deOtraSede + "/salud" + parametros, "{}", tRespA).getResponse()
				.getStatus());
		assertEquals(403, peticion(get("/api/alumnos/" + deOtraSede + "/salud" + parametros), tRespA).getResponse()
				.getStatus());
		assertEquals(404, putSalud(ruta + parametros, "{}", tGestorOtra).getResponse().getStatus());
		assertEquals(405, peticion(post("/api/alumnos/" + noPermitido + "/salud" + parametros), tGestor).getResponse()
				.getStatus());
		assertEquals(404, peticion(delete("/api/alumnos/" + noPermitido + "/salud/" + parametros), tGestor).getResponse()
				.getStatus());
		assertEquals(405, peticion(patch("/api/alumnos/" + noPermitido + "/salud" + parametros), tGestor).getResponse()
				.getStatus());
		int sinCookie = mvc.perform(get("/api/alumnos/" + sinSesion + "/salud" + parametros)).andReturn().getResponse()
				.getStatus();
		assertTrue(sinCookie == 401 || sinCookie == 403, "sin sesión no se atiende: " + sinCookie);
		int delProfesor = peticion(get("/api/profesor/alumnos/" + alumno + "/salud" + parametros), tGestor).getResponse()
				.getStatus();
		assertTrue(delProfesor >= 400, "un gestor sin grupo no lee por la ruta del profesor: " + delProfesor);

		assertEquals(0, jdbc.queryForObject("""
				SELECT COUNT(*) FROM auditoria_evento
				WHERE query_params_json LIKE ? OR payload_json LIKE ? OR resumen LIKE ? OR endpoint LIKE ?""", Integer.class,
				"%" + marcador + "%", "%" + marcador + "%", "%" + marcador + "%", "%" + marcador + "%"));
		// No es un cero vacío: las filas existen, con la columna sin parámetros.
		assertEquals(2, jdbc.queryForObject("""
				SELECT COUNT(*) FROM auditoria_evento
				WHERE endpoint = ? AND accion = 'UPDATE' AND estado_http IN (200, 400) AND query_params_json = '{}'""",
				Integer.class, ruta));
		assertEquals(1, jdbc.queryForObject("""
				SELECT COUNT(*) FROM auditoria_evento
				WHERE endpoint LIKE ? AND estado_http = 405 AND metodo_http = 'POST' AND query_params_json = '{}'""",
				Integer.class, "/api/alumnos/" + noPermitido + "/salud"));
	}

	/** Solo las rutas de salud: las demás siguen guardando sus parámetros. */
	@Test
	void fueraDeSalud_losParametrosDeLaUrlSeSiguenGuardando() throws Exception {
		long alumno = alumno(e.sA);
		String marcador = "Otra-ruta-ficticia-" + marca();

		assertEquals(200, peticion(get("/api/alumnos/" + alumno + "/turnos?marca=" + marcador), tGestor).getResponse()
				.getStatus());

		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_evento WHERE query_params_json LIKE ?",
				Integer.class, "%" + marcador + "%"));
	}

	// ------------------------------------------------------------------ actualizarAlumno

	@Test
	void editarLaFicha_deTrueAFalse_limpiaLosTipos_yNadaMasDeSalud() throws Exception {
		long alumno = alumno(e.sA);
		ok(putSalud(alumno, """
				{"alergias":"Alergia ficticia","tieneDiscapacidad":true,"discapacidadTipos":["OTRA"],
				 "discapacidadOtra":"Texto ficticio"}""", tGestor));
		jdbc.update("UPDATE alumno_salud SET actualizado_en = '2020-01-01 10:00:00' WHERE alumno_id = ?", alumno);
		Map<String, Object> antes = fila(alumno);

		editarFicha(alumno, "\"tieneDiscapacidad\":false");

		assertEquals(Boolean.FALSE, bandera(alumno));
		Map<String, Object> despues = fila(alumno);
		assertNull(despues.get("discapacidad_tipos"));
		assertNull(despues.get("discapacidad_otra"));
		assertEquals(antes.get("alergias"), despues.get("alergias"));
		assertNotEquals(antes.get("actualizado_en"), despues.get("actualizado_en"));
	}

	@Test
	void editarLaFicha_sinElCampo_conservaElBooleanoYLosTipos() throws Exception {
		long alumno = alumno(e.sA);
		guardarConTipos(alumno);
		Map<String, Object> antes = fila(alumno);

		editarFicha(alumno, "\"telefono\":\"600111222\"");

		assertEquals(Boolean.TRUE, bandera(alumno));
		assertEquals(antes, fila(alumno));
	}

	@Test
	void editarLaFicha_conTrue_conservaLosTipos() throws Exception {
		long alumno = alumno(e.sA);
		guardarConTipos(alumno);
		Map<String, Object> antes = fila(alumno);

		editarFicha(alumno, "\"tieneDiscapacidad\":true");

		assertEquals(Boolean.TRUE, bandera(alumno));
		assertEquals(antes, fila(alumno));
	}

	@Test
	void editarLaFicha_quePasaAFalse_sinFilaDeSalud_noCreaFila() throws Exception {
		long alumno = alumno(e.sA);
		jdbc.update("UPDATE alumno SET tiene_discapacidad = 1 WHERE id = ?", alumno);

		editarFicha(alumno, "\"tieneDiscapacidad\":false");

		assertEquals(Boolean.FALSE, bandera(alumno));
		assertEquals(0, filas(alumno));
	}

	@Test
	void editarLaFicha_deFalseAFalse_noTocaLaFila() throws Exception {
		long alumno = alumno(e.sA);
		ok(putSalud(alumno, "{\"tieneDiscapacidad\":false}", tGestor));
		jdbc.update("UPDATE alumno_salud SET actualizado_en = '2020-01-01 10:00:00' WHERE alumno_id = ?", alumno);
		Map<String, Object> antes = fila(alumno);

		editarFicha(alumno, "\"tieneDiscapacidad\":false");

		assertEquals(antes, fila(alumno));
	}

	/** La misma transacción: si la edición falla después, la limpieza no se queda. */
	@Test
	void editarLaFicha_queFalla_noLimpiaLosTipos() throws Exception {
		long alumno = alumno(e.sA);
		guardarConTipos(alumno);
		Map<String, Object> antes = fila(alumno);

		// La foto se procesa después del booleano y una que no vale aborta la edición.
		MvcResult r = mvc.perform(multipart(HttpMethod.PUT, "/api/alumnos/" + alumno)
				.file("file", new byte[] { 1, 2, 3 }).param("alumnoEditado", cuerpoFicha("\"tieneDiscapacidad\":false"))
				.cookie(new Cookie("jwt", tGestor))).andReturn();

		assertEquals(400, r.getResponse().getStatus(), cuerpo(r));
		assertEquals(Boolean.TRUE, bandera(alumno));
		assertEquals(antes, fila(alumno));
	}

	/** Sin transacción no limpia: fallar es lo que garantiza que sea siempre la del llamador. */
	@Test
	void limpiarDiscapacidad_exigeTransaccion() throws Exception {
		long alumno = alumno(e.sA);
		guardarConTipos(alumno);
		Map<String, Object> antes = fila(alumno);

		assertThrows(IllegalTransactionStateException.class,
				() -> TenantContext.ejecutarCon(e.org, () -> alumnoSalud.limpiarDiscapacidad(alumno)));

		assertEquals(antes, fila(alumno));
	}

	/** {@code @TenantId} también filtra el UPDATE: con otra escuela activa no toca la fila. */
	@Test
	void limpiarDiscapacidad_soloEnLaEscuelaActiva() throws Exception {
		long alumno = alumno(e.sA);
		guardarConTipos(alumno);
		Map<String, Object> antes = fila(alumno);

		TenantContext.ejecutarCon(otra.org, () -> tx.executeWithoutResult(s -> alumnoSalud.limpiarDiscapacidad(alumno)));

		assertEquals(antes, fila(alumno));

		TenantContext.ejecutarCon(e.org, () -> tx.executeWithoutResult(s -> alumnoSalud.limpiarDiscapacidad(alumno)));

		assertNull(fila(alumno).get("discapacidad_tipos"));
		assertNull(fila(alumno).get("discapacidad_otra"));
	}

	// ------------------------------------------------------------------ preinscripción

	@Test
	void finalizarConTieneDiscapacidadAFalse_limpiaLosTipos() throws Exception {
		long alumno = alumno(e.sA);
		guardarConTipos(alumno);

		finalizarVinculando(alumno, "\"TIENE_DISCAPACIDAD\"");

		assertEquals(Boolean.FALSE, bandera(alumno));
		assertNull(fila(alumno).get("discapacidad_tipos"));
		assertNull(fila(alumno).get("discapacidad_otra"));
	}

	@Test
	void finalizarSinElCampo_conservaBooleanoYTipos() throws Exception {
		long alumno = alumno(e.sA);
		guardarConTipos(alumno);
		Map<String, Object> antes = fila(alumno);

		finalizarVinculando(alumno, "");

		assertEquals(Boolean.TRUE, bandera(alumno));
		assertEquals(antes, fila(alumno));
	}

	@Test
	void finalizarConTieneDiscapacidadAFalse_sinFilaDeSalud_noCreaFila() throws Exception {
		long alumno = alumno(e.sA);
		jdbc.update("UPDATE alumno SET tiene_discapacidad = 1 WHERE id = ?", alumno);

		finalizarVinculando(alumno, "\"TIENE_DISCAPACIDAD\"");

		assertEquals(Boolean.FALSE, bandera(alumno));
		assertEquals(0, filas(alumno));
	}

	// ------------------------------------------------------------------ anonimizar

	@Test
	void anonimizarBorraLaFila_yDespuesElPutSeRechaza_yLaLecturaNoCambia() throws Exception {
		long alumno = alumno(e.sA);
		guardarConTipos(alumno);

		MvcResult r = mvc.perform(post("/api/alumnos/" + alumno + "/anonimizar").cookie(new Cookie("jwt", tPropietario)))
				.andReturn();
		assertEquals(200, r.getResponse().getStatus(), cuerpo(r));
		assertEquals(0, filas(alumno));
		assertEquals(Boolean.FALSE, bandera(alumno));

		assertRechazado(putSalud(alumno, "{\"alergias\":\"Cambio\"}", tGestor), 409, ANONIMIZADO);
		assertRechazado(putSalud(alumno, "{\"tieneDiscapacidad\":true,\"discapacidadTipos\":[\"FISICA\"]}", tGestor), 409,
				ANONIMIZADO);
		assertEquals(0, filas(alumno));
		assertEquals(Boolean.FALSE, bandera(alumno));

		JsonNode leido = ok(getSalud(alumno, tGestor));
		assertFalse(leido.get("registrado").asBoolean());
	}

	// ------------------------------------------------------------------ permisos

	@Test
	void unResponsableDeOtraSede_403_sinEscritura() throws Exception {
		long alumno = alumno(e.sB);
		guardarConTipos(alumno);
		Map<String, Object> antes = fila(alumno);

		MvcResult r = putSalud(alumno, "{\"tieneDiscapacidad\":false}", tRespA);

		assertEquals(403, r.getResponse().getStatus(), cuerpo(r));
		assertEquals(antes, fila(alumno));
		assertEquals(Boolean.TRUE, bandera(alumno));
	}

	@Test
	void unResponsableDeLaSede_escribe() throws Exception {
		long alumno = alumno(e.sA);

		ok(putSalud(alumno, "{\"tieneDiscapacidad\":true,\"discapacidadTipos\":[\"PSICOSOCIAL\"]}", tRespA));

		assertEquals(Boolean.TRUE, bandera(alumno));
	}

	@Test
	void otraEscuela_404_sinLecturaNiEscritura() throws Exception {
		long alumno = alumno(e.sA);
		guardarConTipos(alumno);
		Map<String, Object> antes = fila(alumno);
		int lecturas = lecturas(alumno);

		assertEquals(404, getSalud(alumno, tGestorOtra).getResponse().getStatus());
		assertEquals(404, putSalud(alumno, "{\"tieneDiscapacidad\":false}", tGestorOtra).getResponse().getStatus());

		assertEquals(antes, fila(alumno));
		assertEquals(Boolean.TRUE, bandera(alumno));
		assertEquals(lecturas, lecturas(alumno));
	}

	// ------------------------------------------------------------------ utilidades

	private long alumno(long sede) {
		return e.alumno("DISC" + marca(), sede, true);
	}

	private static String marca() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private void guardarConTipos(long alumno) throws Exception {
		ok(putSalud(alumno, """
				{"tieneDiscapacidad":true,"discapacidadTipos":["FISICA","OTRA"],"discapacidadOtra":"Texto ficticio"}
				""", tGestor));
	}

	private MvcResult putSalud(long alumno, String cuerpo, String token) throws Exception {
		return putSalud("/api/alumnos/" + alumno + "/salud", cuerpo, token);
	}

	private MvcResult putSalud(String ruta, String cuerpo, String token) throws Exception {
		return peticion(put(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo), token);
	}

	private MvcResult getSalud(long alumno, String token) throws Exception {
		return peticion(get("/api/alumnos/" + alumno + "/salud"), token);
	}

	private MvcResult peticion(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
	}

	private JsonNode ok(MvcResult r) throws Exception {
		assertEquals(200, r.getResponse().getStatus(), cuerpo(r));
		return json.readTree(cuerpo(r));
	}

	private void assertRechazado(MvcResult r, int estado, String mensaje) throws Exception {
		assertEquals(estado, r.getResponse().getStatus(), cuerpo(r));
		assertEquals(mensaje, json.readTree(cuerpo(r)).get("mensaje").asText());
	}

	private static String cuerpo(MvcResult r) throws Exception {
		return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	private List<String> tipos(JsonNode salud) {
		JsonNode lista = salud.get("discapacidadTipos");
		assertTrue(lista.isArray(), "discapacidadTipos nunca es null");
		return json.convertValue(lista, json.getTypeFactory().constructCollectionType(List.class, String.class));
	}

	private Map<String, Object> fila(long alumno) {
		return jdbc.queryForMap("SELECT * FROM alumno_salud WHERE alumno_id = ?", alumno);
	}

	private int filas(long alumno) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM alumno_salud WHERE alumno_id = ?", Integer.class, alumno);
	}

	private Boolean bandera(long alumno) {
		return jdbc.queryForObject("SELECT tiene_discapacidad FROM alumno WHERE id = ?", Boolean.class, alumno);
	}

	private int lecturas(long alumno) {
		return jdbc.queryForObject(
				"SELECT COUNT(*) FROM auditoria_evento WHERE modulo = 'salud' AND recurso_id = ? AND accion = 'READ'",
				Integer.class, alumno);
	}

	private static String contexto(String columna, long alumno) {
		return CifradorDatos.contexto("alumno_salud", columna, alumno);
	}

	private String cuerpoFicha(String camposExtra) {
		return """
				{"nombre":"FICHA","apellidos":"PRUEBA","fechaNacimiento":"2012-05-05","direccion":"Calle 1",
				 "email":"ficha-%s@example.test","autorizacionWeb":true,"tipoTarifa":"INFANTIL","fechaAlta":"2020-09-01",%s}
				""".formatted(marca(), camposExtra);
	}

	private void editarFicha(long alumno, String camposExtra) throws Exception {
		MvcResult r = mvc.perform(multipart(HttpMethod.PUT, "/api/alumnos/" + alumno)
				.param("alumnoEditado", cuerpoFicha(camposExtra)).cookie(new Cookie("jwt", tGestor))).andReturn();
		assertEquals(200, r.getResponse().getStatus(), cuerpo(r));
	}

	/** Solicitud sin discapacidad de la misma persona que {@code alumno} (mismo DNI) y que la vincula a su ficha. */
	private void finalizarVinculando(long alumno, String campos) throws Exception {
		String dni = String.format("%08d", java.util.concurrent.ThreadLocalRandom.current().nextLong(10_000_000L,
				99_999_999L)) + "T";
		String correo = "ana-" + marca() + "@example.test";
		jdbc.update("UPDATE alumno SET nif = ?, email = ? WHERE id = ?", dni, correo, alumno);
		String referencia = "IT-DISC-" + marca();
		jdbc.update("""
				INSERT INTO preinscripcion (referencia, temporada, estado, nombre, apellidos, dni, identidad_hash,
				  fecha_nacimiento, direccion, telefono, email, tiene_discapacidad, consentimiento_fotografico,
				  aceptacion_normas, firmante_nombre, firma, plantilla_id, plantilla_snapshot, pdf_firmado,
				  token_documento_hash, creada_en, actualizada_en, grupo_id, organizacion_id, disciplina_id)
				VALUES (?, '2025-2026', 'PENDIENTE', 'Ana', 'Prueba', ?, ?, '1990-05-05', 'Calle 1', '600000000', ?, 0,
				  0, 1, 'Firmante Prueba', x'00', ?, '{}', x'00', ?, '2025-09-10 12:00:00', '2025-09-10 12:00:00', ?,
				  ?, ?)""", referencia, dni, UUID.randomUUID().toString().replace("-", ""), correo, plantilla,
				"e".repeat(64), e.gA, e.org, e.disciplina);
		long id = jdbc.queryForObject("SELECT id FROM preinscripcion WHERE referencia = ?", Long.class, referencia);
		jdbc.update("INSERT INTO preinscripcion_turno (preinscripcion_id, turno_id) VALUES (?, ?)", id, e.tA);

		String cuerpo = "{\"accionAlumno\":\"VINCULAR_EXISTENTE\",\"alumnoId\":" + alumno + ",\"camposActualizar\":["
				+ campos + "],\"decisionEmail\":\"CONSERVAR_FICHA\",\"datosDeporte\":{\"tipoTarifa\":\"" + TARIFA
				+ "\",\"cuantiaTarifa\":30.0}}";
		MvcResult r = peticion(post("/api/preinscripciones/" + referencia + "/finalizar")
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo), tGestor);
		assertEquals(200, r.getResponse().getStatus(), cuerpo(r));
	}
}
