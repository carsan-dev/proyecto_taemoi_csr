package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.dtos.request.MiembrosRequests;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.EmailService;
import com.taemoi.project.services.GestionMiembrosService;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.impl.AuthenticationServiceImpl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.Cookie;

/**
 * Pieza E8-4 contra MySQL (D8-13, D8-16, D8-19; decisiones 219 y 223; fase3-e8-miembros.md §5.3, §7 y §9 riesgos 1, 2,
 * 12 y 13): invitaciones por correo.
 * <ul>
 * <li>Alta sin cuenta con nombre y apellidos: cuenta LOCAL sin contraseña («pendiente»), membresía, asignaciones e
 * invitación en una transacción; el correo, solo tras el commit, y su fallo no deshace nada.</li>
 * <li>Una cuenta existente nunca recibe invitación, token ni cambio de contraseña.</li>
 * <li>Token de un solo uso y 7 días; reenviar lo reemplaza; nunca en el registro, los logs ni las respuestas.</li>
 * <li>Crear la contraseña inicia sesión en la escuela de la invitación.</li>
 * <li>{@code invitacionPendiente} en la lista, la ficha y los profesores del grupo.</li>
 * </ul>
 * Escuelas sintéticas ({@link EscuelaConSedesDePrueba}); cada test usa emails nuevos con la marca de la escuela y un
 * contador y mira solo sus filas, así que el orden no importa. El correo es un mock: no sale nada. Nada en Moiskimdo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvitacionesIntegrationTest extends MySqlIntegrationTest {

	private static final String RUTA = "/api/miembros";
	private static final String ACEPTAR = "/api/auth/invitacion/aceptar";
	private static final String SIN_CUENTA = "No hay ninguna cuenta con ese email.";
	private static final String NO_MIEMBRO = "La cuenta no es miembro de esta escuela.";
	private static final String SOLO_PROPIETARIO = "Solo el propietario da o quita los roles de propietario y gestor.";
	private static final String YA_ACEPTADA = "La invitación ya se aceptó.";
	private static final String ENLACE_NO_VALE = "El enlace ha caducado o ya se usó. Pide que te lo reenvíen.";
	private static final String CONTRASENA = "Nueva-Clave-2026";
	private static final Pattern TOKEN = Pattern.compile("/crear-contrasena\\?token=([A-Za-z0-9_-]+)");
	private static final String RESUMEN_GENERICO = "^(CREATE|UPDATE|DELETE|READ|OPERACION) \\S+( #\\d+)? -> \\d+$";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;
	@Autowired GestionMiembrosService miembros;
	@Autowired PlatformTransactionManager transacciones;

	@MockitoBean EmailService email;

	@Value("${app.frontend.base-url:https://moiskimdo.es}")
	String baseEnlaces;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	long propietario;
	String nombreEscuela;
	String tPropietario, tGestor, tPropietarioOtra;
	final AtomicInteger contador = new AtomicInteger();

	/** Un correo enviado: destinatario, asunto, HTML, imagen en línea y remitente. */
	record Correo(String para, String asunto, String html, byte[] imagen, String remitente) {
		String token() {
			Matcher m = TOKEN.matcher(html);
			assertTrue(m.find(), "el correo no lleva el enlace");
			return m.group(1);
		}
	}

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e8-4");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e8-4-otra");
		nombreEscuela = "Club Invitaciones " + e.marca;
		jdbc.update("UPDATE organizacion SET nombre = ? WHERE id = ?", nombreEscuela, e.org);

		propietario = e.cuenta(usuarios, "propietario");
		membresia(e.org, propietario, "PROPIETARIO");
		long propietarioOtra = otra.cuenta(usuarios, "propietario");
		membresia(otra.org, propietarioOtra, "PROPIETARIO");

		tPropietario = e.token(jwtService, usuarios, propietario);
		tGestor = e.token(jwtService, usuarios, e.gestor);
		tPropietarioOtra = otra.token(jwtService, usuarios, propietarioOtra);
	}

	@AfterEach
	void sinFugas() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ alta sin cuenta

	@Test
	void altaSinCuenta_conNombre_creaCuentaPendienteMembresiaYEnviaCorreoTrasCommit() throws Exception {
		String correo = emailNuevo("alta");
		long antes = ultimoEvento();
		Map<String, Object> datos = invitacion(correo.toUpperCase(), "PROFESOR", "  Lucía ", " Martín Prueba ");
		datos.put("cargo", "Técnico deportivo");
		datos.put("grupos", List.of(asig(e.gA, "TITULAR")));

		JsonNode ficha = leer(conCuerpo(post(RUTA), cuerpo(datos)), tPropietario, 200);

		long id = ficha.get("usuarioId").asLong();
		assertTrue(ficha.get("invitacionPendiente").asBoolean());
		assertEquals("LOCAL", ficha.get("authProvider").asText());
		assertEquals("Lucía", ficha.get("nombre").asText());
		assertEquals("Martín Prueba", ficha.get("apellidos").asText());
		assertEquals(correo, ficha.get("email").asText());
		Map<String, Object> fila = jdbc.queryForMap("""
				SELECT contrasena, auth_provider, reset_token_hash, ultima_organizacion_id FROM usuario WHERE id = ?""",
				id);
		assertNull(fila.get("contrasena"));
		assertEquals("LOCAL", fila.get("auth_provider"));
		assertTrue(String.valueOf(fila.get("reset_token_hash")).matches("[0-9a-f]{64}"), fila.toString());
		assertEquals(e.org, numero(fila, "ultima_organizacion_id"));
		LocalDateTime caduca = cuentaEnOrg1(id).getResetTokenExpiresAt();
		LocalDateTime enUnaSemana = LocalDateTime.now().plusDays(7);
		assertTrue(caduca.isAfter(enUnaSemana.minusMinutes(5)) && caduca.isBefore(enUnaSemana.plusMinutes(5)),
				"caduca a los 7 días: " + caduca);
		assertEquals(List.of("PROFESOR"), rolesActivos(id, e.org));
		assertEquals("Técnico deportivo", jdbc.queryForObject(
				"SELECT cargo FROM membresia WHERE usuario_id = ? AND organizacion_id = ? AND rol = 'PROFESOR'",
				String.class, id, e.org));
		assertEquals(Map.of(e.gA, "TITULAR"), asignaciones(id, e.org));

		List<String> filas = resumenes(eventosDesde(antes, e.org));
		assertEquals("MIEMBROS MIEMBRO_INVITADO usuario=#" + id + " rol=PROFESOR", filas.get(0));
		assertTrue(filas.contains("MIEMBROS ROL_DADO usuario=#" + id + " rol=PROFESOR"), filas.toString());
		assertTrue(filas.contains("PROFESOR ASIGNACION_CREADA grupo=#" + e.gA + " usuario=#" + id + " rol=TITULAR"),
				filas.toString());
		assertTrue(filas.stream().noneMatch(r -> r.matches(RESUMEN_GENERICO)), filas.toString());

		Correo enviado = enviadoA(correo);
		assertTrue(enviado.html().contains("Hola, Lucía."), enviado.html());
		assertTrue(enviado.html().contains("Te han dado acceso como profesor en " + nombreEscuela + "."));
		assertTrue(enviado.html().contains("Crear mi contraseña"));
		assertTrue(enviado.html().contains("El enlace caduca en 7 días."));
		assertEquals(fila.get("reset_token_hash"), sha256(enviado.token()), "solo se guarda el hash del token");
		verify(email, times(1)).sendEmailConImagen(eq(correo), anyString(), anyString(), any(), any(), any(), any());
	}

	@Test
	void elCorreoSoloSaleTrasElCommit_yNuncaSiSeDeshace() {
		String confirmada = emailNuevo("commit");
		comoPropietario(() -> new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
			miembros.alta(alta(confirmada, "PROFESOR", "Ana", "Commit"));
			verify(email, never()).sendEmailConImagen(any(), any(), any(), any(), any(), any(), any());
		}));
		verify(email, times(1)).sendEmailConImagen(eq(confirmada), anyString(), anyString(), any(), any(), any(),
				any());

		String deshecha = emailNuevo("rollback");
		comoPropietario(() -> new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
			miembros.alta(alta(deshecha, "PROFESOR", "Ana", "Deshecha"));
			estado.setRollbackOnly();
		}));
		verify(email, never()).sendEmailConImagen(eq(deshecha), any(), any(), any(), any(), any(), any());
		assertEquals(0, cuentasCon(deshecha));
	}

	@Test
	void altaSinCuenta_sinNombre_404() throws Exception {
		String correo = emailNuevo("sin-nombre");
		assertMensaje(peticion(conCuerpo(post(RUTA), cuerpo(Map.of("email", correo, "rol", "PROFESOR"))),
				tPropietario), 404, SIN_CUENTA);
		Map<String, Object> nulos = new LinkedHashMap<>();
		nulos.put("email", correo);
		nulos.put("rol", "PROFESOR");
		nulos.put("nombre", null);
		nulos.put("apellidos", null);
		assertMensaje(peticion(conCuerpo(post(RUTA), cuerpo(nulos)), tPropietario), 404, SIN_CUENTA);
		assertEquals(0, cuentasCon(correo));
		verifyNoInteractions(email);
	}

	@Test
	void altaSinCuenta_nombreNoValido_400() throws Exception {
		String correo = emailNuevo("no-valido");
		List<Map<String, Object>> cuerpos = new ArrayList<>();
		Map<String, Object> soloNombre = new LinkedHashMap<>(Map.of("email", correo, "rol", "PROFESOR", "nombre", "Ana"));
		cuerpos.add(soloNombre);
		cuerpos.add(new LinkedHashMap<>(Map.of("email", correo, "rol", "PROFESOR", "apellidos", "Prueba")));
		cuerpos.add(invitacion(correo, "PROFESOR", "   ", "Prueba"));
		cuerpos.add(invitacion(correo, "PROFESOR", "Ana", " "));
		cuerpos.add(invitacion(correo, "PROFESOR", "N".repeat(51), "Prueba"));
		cuerpos.add(invitacion(correo, "PROFESOR", "Ana", "A".repeat(51)));
		cuerpos.add(invitacion("no-es-un-email-" + e.marca, "PROFESOR", "Ana", "Prueba"));
		for (Map<String, Object> datos : cuerpos) {
			MvcResult r = peticion(conCuerpo(post(RUTA), cuerpo(datos)), tPropietario);
			assertEquals(400, r.getResponse().getStatus(), datos + " -> " + contenido(r));
			assertFalse(mensaje(r).isBlank(), contenido(r));
		}
		// 50 caracteres sí caben (como las columnas).
		String justo = emailNuevo("justo");
		assertEquals(200, estado(conCuerpo(post(RUTA), cuerpo(invitacion(justo, "PROFESOR", "N".repeat(50),
				"A".repeat(50)))), tPropietario));
		assertEquals(0, cuentasCon(correo));
		assertEquals(0, cuentasCon("no-es-un-email-" + e.marca));
		verify(email, never()).sendEmailConImagen(eq(correo), any(), any(), any(), any(), any(), any());
	}

	@Test
	void altaSinCuenta_grupoAjeno_noCreaNada() throws Exception {
		String correo = emailNuevo("grupo-ajeno");
		Map<String, Object> datos = invitacion(correo, "PROFESOR", "Ana", "Ajena");
		datos.put("grupos", List.of(asig(e.gA, "TITULAR"), asig(otra.gA, "AYUDANTE")));
		assertMensaje(peticion(conCuerpo(post(RUTA), cuerpo(datos)), tPropietario), 404, "El grupo no existe.");
		Map<String, Object> responsable = invitacion(correo, "RESPONSABLE_SEDE", "Ana", "Ajena");
		responsable.put("sedeIds", List.of(otra.sA));
		assertMensaje(peticion(conCuerpo(post(RUTA), cuerpo(responsable)), tPropietario), 400,
				"La sede no existe en la escuela.");
		assertEquals(0, cuentasCon(correo));
		verifyNoInteractions(email);
	}

	@Test
	void gestorInvitaPropietario_403() throws Exception {
		for (String rol : List.of("PROPIETARIO", "ADMINISTRADOR")) {
			String correo = emailNuevo("gestor-invita-" + rol.toLowerCase());
			assertMensaje(peticion(conCuerpo(post(RUTA), cuerpo(invitacion(correo, rol, "Ana", "Gestora"))), tGestor),
					403, SOLO_PROPIETARIO);
			assertEquals(0, cuentasCon(correo));
		}
		verifyNoInteractions(email);
		String profesor = emailNuevo("gestor-invita-profesor");
		assertEquals(200, estado(conCuerpo(post(RUTA), cuerpo(invitacion(profesor, "PROFESOR", "Ana", "Gestora"))),
				tGestor));
		enviadoA(profesor);
	}

	// ------------------------------------------------------------------ sin toma de cuentas

	@Test
	void invitarEmailConCuenta_noTocaContrasenaNiEnviaCorreo() throws Exception {
		long cuenta = e.cuenta(usuarios, "existente-" + contador.incrementAndGet());
		String correo = emailDe(cuenta);
		String consulta = """
				SELECT nombre, apellidos, contrasena, auth_provider, reset_token_hash, reset_token_expires_at,
				       ultima_organizacion_id
				FROM usuario WHERE id = ?""";
		Map<String, Object> antes = jdbc.queryForMap(consulta, cuenta);
		long evento = ultimoEvento();

		JsonNode ficha = leer(conCuerpo(post(RUTA), cuerpo(invitacion(correo, "PROFESOR", "Otro", "Nombre"))),
				tPropietario, 200);

		assertFalse(ficha.get("invitacionPendiente").asBoolean());
		assertEquals(antes, jdbc.queryForMap(consulta, cuenta));
		assertEquals(List.of("PROFESOR"), rolesActivos(cuenta, e.org));
		assertTrue(resumenes(eventosDesde(evento, e.org)).stream().noneMatch(r -> r.contains("MIEMBRO_INVITADO")));

		// Pendiente por una invitación de otra escuela: esta escuela solo la añade; ni token nuevo ni correo.
		String deOtra = emailNuevo("pendiente-otra");
		long pendiente = leer(conCuerpo(post(RUTA), cuerpo(invitacion(deOtra, "PROFESOR", "Eva", "Otra"))),
				tPropietarioOtra, 200).get("usuarioId").asLong();
		Map<String, Object> pendienteAntes = jdbc.queryForMap(consulta, pendiente);
		assertTrue(leer(conCuerpo(post(RUTA), cuerpo(invitacion(deOtra, "PROFESOR", "Cambiado", "Nombre"))),
				tPropietario, 200).get("invitacionPendiente").asBoolean());
		assertEquals(pendienteAntes, jdbc.queryForMap(consulta, pendiente));
		verify(email, never()).sendEmailConImagen(eq(correo), any(), any(), any(), any(), any(), any());
		verify(email, times(1)).sendEmailConImagen(eq(deOtra), any(), any(), any(), any(), any(), any());
	}

	@Test
	void cuentaPendienteNoEntraConContrasena() throws Exception {
		String correo = emailNuevo("no-entra");
		invitar(correo, "PROFESOR");
		for (String intento : List.of("Cualquier-Clave-1", "", "null")) {
			MvcResult r = mvc.perform(conCuerpo(post("/api/auth/signin"),
					cuerpo(Map.of("email", correo, "contrasena", intento)))).andReturn();
			assertEquals(401, r.getResponse().getStatus(), intento + " -> " + contenido(r));
		}
	}

	// ------------------------------------------------------------------ token

	@Test
	void tokenDeInvitacionNoApareceEnAuditoriaNiLogs() throws Exception {
		String correo = emailNuevo("sin-fugas");
		long evento = ultimoEvento();
		List<String> respuestas = new ArrayList<>();
		List<String> secretos = new ArrayList<>();
		List<ILoggingEvent> logs = conLogsCapturados(() -> {
			MvcResult alta = peticion(conCuerpo(post(RUTA), cuerpo(invitacion(correo, "PROFESOR", "Ana", "Fugas"))),
					tPropietario);
			assertEquals(200, alta.getResponse().getStatus());
			respuestas.add(contenido(alta));
			long id = json.readTree(contenido(alta)).get("usuarioId").asLong();
			String primero = enviadoA(correo).token();
			MvcResult reenvio = peticion(post(RUTA + "/" + id + "/invitacion"), tPropietario);
			assertEquals(200, reenvio.getResponse().getStatus());
			respuestas.add(contenido(reenvio));
			String segundo = ultimoEnviadoA(correo).token();
			MvcResult viejo = aceptar(primero, CONTRASENA);
			assertEquals(400, viejo.getResponse().getStatus());
			respuestas.add(contenido(viejo));
			MvcResult bueno = aceptar(segundo, CONTRASENA);
			assertEquals(200, bueno.getResponse().getStatus());
			respuestas.add(contenido(bueno));
			secretos.addAll(List.of(primero, segundo, sha256(primero), sha256(segundo), CONTRASENA));
		});

		assertEquals(5, secretos.size());
		// Los hashes (con los que no se acepta nada: se busca por el hash del token recibido) solo los imprime, en
		// DEBUG, el volcado de entidades de Hibernate (EntityPrinter), como cualquier columna; nunca a partir de INFO.
		Set<String> hashes = Set.of(secretos.get(2), secretos.get(3));
		for (String secreto : secretos) {
			for (ILoggingEvent log : logs) {
				if (hashes.contains(secreto) && !log.getLevel().isGreaterOrEqual(Level.INFO)) {
					continue;
				}
				assertFalse(textoDe(log).contains(secreto), "el log contiene un secreto: " + textoDe(log));
			}
			for (Map<String, Object> fila : jdbc.queryForList("SELECT * FROM auditoria_evento WHERE id > ?", evento)) {
				for (Object valor : fila.values()) {
					assertFalse(String.valueOf(valor).contains(secreto), "fila de auditoría con un secreto: " + fila);
				}
			}
			for (String respuesta : respuestas) {
				assertFalse(respuesta.contains(secreto), "respuesta con un secreto: " + respuesta);
			}
		}
		assertTrue(logs.size() > 0, "se capturaron los logs");
		assertEquals(0, jdbc.queryForObject(
				"SELECT COUNT(*) FROM auditoria_evento WHERE id > ? AND endpoint LIKE '/api/auth/%'", Long.class, evento));
	}

	@Test
	void reenviarInvalidaElTokenAnterior() throws Exception {
		String correo = emailNuevo("reenviar");
		long id = invitar(correo, "PROFESOR");
		String primero = enviadoA(correo).token();
		long evento = ultimoEvento();

		JsonNode ficha = leer(post(RUTA + "/" + id + "/invitacion"), tPropietario, 200);

		assertTrue(ficha.get("invitacionPendiente").asBoolean());
		assertEquals(List.of("MIEMBROS INVITACION_REENVIADA usuario=#" + id), resumenes(eventosDesde(evento, e.org)));
		String segundo = ultimoEnviadoA(correo).token();
		assertNotEquals(primero, segundo);
		assertMensaje(aceptar(primero, CONTRASENA), 400, ENLACE_NO_VALE);
		assertTrue(pendiente(id));
		assertEquals(200, aceptar(segundo, CONTRASENA).getResponse().getStatus());
		assertFalse(pendiente(id));
	}

	@Test
	void reenviarSinPendiente_409() throws Exception {
		long cuenta = e.cuenta(usuarios, "con-clave-" + contador.incrementAndGet());
		membresia(e.org, cuenta, "PROFESOR");
		assertMensaje(peticion(post(RUTA + "/" + cuenta + "/invitacion"), tPropietario), 409, YA_ACEPTADA);

		String correo = emailNuevo("ya-aceptada");
		long id = invitar(correo, "PROFESOR");
		assertEquals(200, aceptar(enviadoA(correo).token(), CONTRASENA).getResponse().getStatus());
		assertMensaje(peticion(post(RUTA + "/" + id + "/invitacion"), tPropietario), 409, YA_ACEPTADA);
		verify(email, times(1)).sendEmailConImagen(eq(correo), any(), any(), any(), any(), any(), any());
		verify(email, never()).sendEmailConImagen(eq(emailDe(cuenta)), any(), any(), any(), any(), any(), any());
	}

	@Test
	void reenviarOtraEscuela_404() throws Exception {
		String correo = emailNuevo("de-otra");
		long id = leer(conCuerpo(post(RUTA), cuerpo(invitacion(correo, "PROFESOR", "Eva", "Otra"))), tPropietarioOtra,
				200).get("usuarioId").asLong();
		String hash = hashDe(id);

		assertMensaje(peticion(post(RUTA + "/" + id + "/invitacion"), tPropietario), 404, NO_MIEMBRO);
		assertMensaje(peticion(post(RUTA + "/999999999/invitacion"), tPropietario), 404, NO_MIEMBRO);
		assertEquals(hash, hashDe(id));
		verify(email, times(1)).sendEmailConImagen(eq(correo), any(), any(), any(), any(), any(), any());
	}

	@Test
	void gestorReenviaPropietario_403() throws Exception {
		String gestor = emailNuevo("invita-gestor");
		long idGestor = invitar(gestor, "ADMINISTRADOR");
		String hash = hashDe(idGestor);
		assertMensaje(peticion(post(RUTA + "/" + idGestor + "/invitacion"), tGestor), 403, SOLO_PROPIETARIO);
		assertEquals(hash, hashDe(idGestor));
		assertEquals(200, estado(post(RUTA + "/" + idGestor + "/invitacion"), tPropietario));

		String profesor = emailNuevo("invita-profesor");
		long idProfesor = invitar(profesor, "PROFESOR");
		assertEquals(200, estado(post(RUTA + "/" + idProfesor + "/invitacion"), tGestor));
		verify(email, times(2)).sendEmailConImagen(eq(profesor), any(), any(), any(), any(), any(), any());
	}

	/** Ronda 2 (F5): sin ningún rol activo en la escuela, la invitación no se reenvía; el correo usa solo los activos. */
	@Test
	void reenviarSinRolActivo_409_yElCorreoUsaSoloLosActivos() throws Exception {
		String correo = emailNuevo("sin-rol-activo");
		long id = invitar(correo, "PROFESOR");
		String hash = hashDe(id);
		jdbc.update("UPDATE membresia SET activa = FALSE WHERE usuario_id = ? AND organizacion_id = ?", id, e.org);
		assertMensaje(peticion(post(RUTA + "/" + id + "/invitacion"), tPropietario), 409,
				"La cuenta no tiene ningún rol activo en esta escuela.");
		assertEquals(hash, hashDe(id));
		verify(email, times(1)).sendEmailConImagen(eq(correo), any(), any(), any(), any(), any(), any());

		// Invitada como gestora; luego profesora y sin el rol de gestora: el reenvío habla de profesor.
		String otro = emailNuevo("solo-activos");
		long cuenta = invitar(otro, "ADMINISTRADOR");
		membresia(e.org, cuenta, "PROFESOR");
		jdbc.update("UPDATE membresia SET activa = FALSE WHERE usuario_id = ? AND organizacion_id = ? AND rol = ?",
				cuenta, e.org, "ADMINISTRADOR");
		assertEquals(200, estado(post(RUTA + "/" + cuenta + "/invitacion"), tGestor),
				"con PROFESOR activo, el gestor sí la reenvía");
		String html = ultimoEnviadoA(otro).html();
		assertTrue(html.contains("Te han dado acceso como profesor en " + nombreEscuela + "."), html);
	}

	/** Ronda 2 (F2): dos usos a la vez del mismo token: uno gana (200) y el otro 400; una sola contraseña. */
	@Test
	void dosAceptacionesALaVezMismoToken_unaGana() throws Exception {
		for (int ronda = 0; ronda < 4; ronda++) {
			String correo = emailNuevo("aceptar-a-la-vez-" + ronda);
			long id = invitar(correo, "PROFESOR");
			String token = enviadoA(correo).token();
			String primera = "Primera-Clave-" + ronda + "A";
			String segunda = "Segunda-Clave-" + ronda + "B";

			List<Integer> estados = enParalelo(() -> aceptar(token, primera).getResponse().getStatus(),
					() -> aceptar(token, segunda).getResponse().getStatus());

			assertEquals(List.of(200, 400), estados, "ronda " + ronda);
			assertFalse(pendiente(id), "ronda " + ronda);
			assertNull(hashDe(id), "ronda " + ronda);
			int entran = 0;
			for (String clave : List.of(primera, segunda)) {
				if (mvc.perform(conCuerpo(post("/api/auth/signin"), cuerpo(Map.of("email", correo, "contrasena", clave))))
						.andReturn().getResponse().getStatus() == 200) {
					entran++;
				}
			}
			assertEquals(1, entran, "una sola contraseña queda puesta, ronda " + ronda);
		}
	}

	@Test
	void tokenCaducadoOUsado_rechazado() throws Exception {
		String correo = emailNuevo("caducado");
		long id = invitar(correo, "PROFESOR");
		String caducado = enviadoA(correo).token();
		jdbc.update("UPDATE usuario SET reset_token_expires_at = ? WHERE id = ?", LocalDateTime.now().minusMinutes(1),
				id);
		assertMensaje(aceptar(caducado, CONTRASENA), 400, ENLACE_NO_VALE);
		assertTrue(pendiente(id));

		assertEquals(200, estado(post(RUTA + "/" + id + "/invitacion"), tPropietario));
		String token = ultimoEnviadoA(correo).token();
		for (String debil : List.of("corta1A", "sinmayusculas1", "SINMINUSCULAS1", "SinNumeros", "")) {
			assertEquals(400, aceptar(token, debil).getResponse().getStatus(), debil);
		}
		assertTrue(pendiente(id));
		assertEquals(200, aceptar(token, CONTRASENA).getResponse().getStatus());
		assertMensaje(aceptar(token, "Otra-Clave-2027"), 400, ENLACE_NO_VALE);
		assertMensaje(aceptar("desconocido-" + e.marca, CONTRASENA), 400, ENLACE_NO_VALE);
		assertEquals(400, aceptar("", CONTRASENA).getResponse().getStatus());
		assertEquals(200, mvc.perform(conCuerpo(post("/api/auth/signin"),
				cuerpo(Map.of("email", correo, "contrasena", CONTRASENA)))).andReturn().getResponse().getStatus(),
				"la contraseña buena sigue siendo la primera");
	}

	@Test
	void crearContrasenaIniciaSesionEnLaEscuela() throws Exception {
		String correo = emailNuevo("entra");
		Map<String, Object> datos = invitacion(correo, "PROFESOR", "Ana", "Entra");
		datos.put("grupos", List.of(asig(e.gA, "TITULAR")));
		long id = leer(conCuerpo(post(RUTA), cuerpo(datos)), tPropietario, 200).get("usuarioId").asLong();
		// También es profesor en otra escuela: entra en la de la invitación.
		membresia(otra.org, id, "PROFESOR");
		String token = enviadoA(correo).token();
		long evento = ultimoEvento();

		MvcResult r = aceptar(token, CONTRASENA);

		assertEquals(200, r.getResponse().getStatus(), contenido(r));
		String jwt = cookieJwt(r);
		assertNotNull(jwt, "abre sesión con la cookie de siempre");
		assertEquals(e.org, jwtService.verificar(jwt).organizacionId());
		assertEquals(correo, jwtService.verificar(jwt).email());
		JsonNode roles = json.readTree(contenido(mvc.perform(get("/api/auth/roles").cookie(new Cookie("jwt", jwt)))
				.andReturn()));
		assertTrue(roles.toString().contains("ROLE_PROFESOR"), roles.toString());
		JsonNode grupos = json.readTree(contenido(mvc.perform(get("/api/profesor/grupos").cookie(new Cookie("jwt", jwt)))
				.andReturn()));
		List<Long> misGrupos = new ArrayList<>();
		grupos.forEach(g -> misGrupos.add(g.get("id").asLong()));
		assertEquals(List.of(e.gA), misGrupos, "sus grupos de la escuela de la invitación");
		assertFalse(pendiente(id));
		assertNull(hashDe(id));
		assertTrue(jdbc.queryForObject("SELECT contrasena FROM usuario WHERE id = ?", String.class, id)
				.startsWith("$2"));
		assertEquals(0, jdbc.queryForObject(
				"SELECT COUNT(*) FROM auditoria_evento WHERE id > ? AND endpoint LIKE '/api/auth/%'", Long.class, evento),
				"/api/auth/** no se audita");
		assertEquals(200, mvc.perform(conCuerpo(post("/api/auth/signin"),
				cuerpo(Map.of("email", correo, "contrasena", CONTRASENA)))).andReturn().getResponse().getStatus());
	}

	@Test
	void aceptarComoResponsableSolo_abreSesion() throws Exception {
		String correo = emailNuevo("solo-responsable");
		Map<String, Object> datos = invitacion(correo, "RESPONSABLE_SEDE", "Ana", "Responsable");
		datos.put("sedeIds", List.of(e.sA));
		long id = leer(conCuerpo(post(RUTA), cuerpo(datos)), tPropietario, 200).get("usuarioId").asLong();

		MvcResult r = aceptar(enviadoA(correo).token(), CONTRASENA);

		// Decisión 370: el responsable de sede entra por su invitación, como el resto de roles.
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertNotNull(cookieJwt(r));
		assertFalse(pendiente(id), "la contraseña queda creada");
	}

	@Test
	void fallaElCorreo_noDeshaceLaCuenta() throws Exception {
		doThrow(new IllegalStateException("SMTP caído")).when(email).sendEmailConImagen(any(), any(), any(), any(),
				any(), any(), any());
		String correo = emailNuevo("sin-smtp");
		long evento = ultimoEvento();

		JsonNode ficha = leer(conCuerpo(post(RUTA), cuerpo(invitacion(correo, "PROFESOR", "Ana", "Smtp"))),
				tPropietario, 200);

		long id = ficha.get("usuarioId").asLong();
		assertEquals(1, cuentasCon(correo));
		assertTrue(pendiente(id));
		assertEquals(List.of("PROFESOR"), rolesActivos(id, e.org));
		assertEquals(200, estado(post(RUTA + "/" + id + "/invitacion"), tPropietario));
		List<String> filas = resumenes(eventosDesde(evento, e.org));
		assertTrue(filas.contains("MIEMBROS MIEMBRO_INVITADO usuario=#" + id + " rol=PROFESOR"), filas.toString());
		assertTrue(filas.contains("MIEMBROS INVITACION_REENVIADA usuario=#" + id), filas.toString());
	}

	@Test
	void dosInvitacionesALaVezMismoEmail_sin500() throws Exception {
		for (int ronda = 0; ronda < 4; ronda++) {
			String correo = emailNuevo("a-la-vez-" + ronda);
			Map<String, Object> datos = invitacion(correo, "PROFESOR", "Ana", "Ronda " + ronda);
			datos.put("grupos", List.of(asig(e.gA, "TITULAR")));
			String cuerpo = cuerpo(datos);
			List<Integer> estados = enParalelo(() -> estado(conCuerpo(post(RUTA), cuerpo), tPropietario),
					() -> estado(conCuerpo(post(RUTA), cuerpo), tPropietario));
			assertEquals(List.of(200, 200), estados, "ronda " + ronda);
			assertEquals(1, cuentasCon(correo), "ronda " + ronda);
			long id = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, correo);
			assertEquals(1, jdbc.queryForObject("""
					SELECT COUNT(*) FROM auditoria_evento WHERE organizacion_id = ? AND resumen = ?""", Long.class,
					e.org, "MIEMBROS MIEMBRO_INVITADO usuario=#" + id + " rol=PROFESOR"), "ronda " + ronda);
			assertEquals(List.of("PROFESOR"), rolesActivos(id, e.org), "ronda " + ronda);
			assertEquals(Map.of(e.gA, "TITULAR"), asignaciones(id, e.org), "ronda " + ronda);
			verify(email, times(1)).sendEmailConImagen(eq(correo), any(), any(), any(), any(), any(), any());
		}
	}

	// ------------------------------------------------------------------ correo y respuestas

	@Test
	void correoConMarcaDeLaEscuela() throws Exception {
		String correo = emailNuevo("marca");
		invitar(correo, "RESPONSABLE_SEDE", e.sA);

		Correo enviado = enviadoA(correo);

		assertEquals(nombreEscuela, enviado.remitente());
		assertTrue(enviado.asunto().contains(nombreEscuela), enviado.asunto());
		assertTrue(enviado.html().contains(nombreEscuela));
		assertTrue(enviado.html().contains("Te han dado acceso como responsable de sede en " + nombreEscuela + "."));
		assertFalse(enviado.html().contains("Moiskimdo"));
		assertFalse(enviado.html().contains("Moi's"));
		assertNull(enviado.imagen(), "escuela sin logo: solo el nombre");
		assertFalse(enviado.html().contains("<img"));
		assertTrue(enviado.html().contains("href=\"" + baseEnlaces + "/crear-contrasena?token="), enviado.html());
	}

	@Test
	void invitacionPendienteEnLasTresRespuestas() throws Exception {
		String marca = "tres" + contador.incrementAndGet() + e.marca;
		String correo = "it-e8-4-" + marca + "-invitada@example.test";
		Map<String, Object> datos = invitacion(correo, "PROFESOR", "Ana", "Tres");
		datos.put("grupos", List.of(asig(e.gA2, "AYUDANTE")));
		long invitada = leer(conCuerpo(post(RUTA), cuerpo(datos)), tPropietario, 200).get("usuarioId").asLong();
		long conCuenta = e.cuenta(usuarios, marca + "-con-cuenta");
		membresia(e.org, conCuenta, "PROFESOR");
		jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) VALUES (?, ?, ?, ?)",
				e.org, conCuenta, e.gA2, "TITULAR");

		JsonNode pagina = leer(get(RUTA).param("q", marca), tPropietario, 200);
		assertTrue(fila(pagina.get("contenido"), invitada).get("invitacionPendiente").asBoolean());
		assertFalse(fila(pagina.get("contenido"), conCuenta).get("invitacionPendiente").asBoolean());
		assertTrue(leer(get(RUTA + "/" + invitada), tPropietario, 200).get("invitacionPendiente").asBoolean());
		assertFalse(leer(get(RUTA + "/" + conCuenta), tPropietario, 200).get("invitacionPendiente").asBoolean());

		JsonNode profesores = leer(get("/api/grupos/" + e.gA2 + "/profesores"), tPropietario, 200);
		JsonNode deInvitada = fila(profesores, invitada);
		assertEquals(Set.of("usuarioId", "nombre", "apellidos", "email", "rol", "invitacionPendiente"),
				campos(deInvitada));
		assertTrue(deInvitada.get("invitacionPendiente").asBoolean());
		assertEquals("AYUDANTE", deInvitada.get("rol").asText());
		assertFalse(fila(profesores, conCuenta).get("invitacionPendiente").asBoolean());
	}

	/** La página «Crea tu contraseña» antes de escribirla: solo email, nombre y escuela de una invitación vigente. */
	@Test
	void datosDeLaInvitacion() throws Exception {
		String correo = emailNuevo("datos");
		long id = leer(conCuerpo(post(RUTA), cuerpo(invitacion(correo, "PROFESOR", "Lucía", "Datos"))), tPropietario,
				200).get("usuarioId").asLong();
		String token = enviadoA(correo).token();
		long evento = ultimoEvento();

		MvcResult r = datos(token);

		assertEquals(200, r.getResponse().getStatus(), contenido(r));
		JsonNode cuerpo = json.readTree(contenido(r));
		assertEquals(Set.of("email", "nombre", "escuela"), campos(cuerpo));
		assertEquals(correo, cuerpo.get("email").asText());
		assertEquals("Lucía", cuerpo.get("nombre").asText());
		assertEquals(nombreEscuela, cuerpo.get("escuela").asText());
		assertFalse(contenido(r).contains(token));
		assertTrue(pendiente(id), "consultar no cambia nada");

		assertMensaje(datos("desconocido-" + e.marca), 400, ENLACE_NO_VALE);
		assertMensaje(datos(""), 400, ENLACE_NO_VALE);
		assertMensaje(mvc.perform(conCuerpo(post("/api/auth/invitacion/datos"), "{}")).andReturn(), 400,
				ENLACE_NO_VALE);
		// Caducada.
		jdbc.update("UPDATE usuario SET reset_token_expires_at = ? WHERE id = ?", LocalDateTime.now().minusMinutes(1),
				id);
		assertMensaje(datos(token), 400, ENLACE_NO_VALE);
		// Usada: aceptada con otro token, el anterior ya no da datos.
		assertEquals(200, estado(post(RUTA + "/" + id + "/invitacion"), tPropietario));
		String nuevo = ultimoEnviadoA(correo).token();
		assertEquals(200, datos(nuevo).getResponse().getStatus());
		assertEquals(200, aceptar(nuevo, CONTRASENA).getResponse().getStatus());
		assertMensaje(datos(nuevo), 400, ENLACE_NO_VALE);
		// Cuenta con contraseña y un token de restablecer vigente: no es una invitación.
		long conClave = e.cuenta(usuarios, "datos-con-clave-" + contador.incrementAndGet());
		membresia(e.org, conClave, "PROFESOR");
		String reset = "reset-" + e.marca;
		jdbc.update("UPDATE usuario SET reset_token_hash = ?, reset_token_expires_at = ? WHERE id = ?", sha256(reset),
				LocalDateTime.now().plusHours(1), conClave);
		assertMensaje(datos(reset), 400, ENLACE_NO_VALE);
		assertEquals(0, jdbc.queryForObject(
				"SELECT COUNT(*) FROM auditoria_evento WHERE id > ? AND endpoint LIKE '/api/auth/%'", Long.class, evento));
	}

	/**
	 * Ronda 1: el propietario pone la contraseña de una invitación pendiente desde «Gestión de Roles»: el enlace deja de
	 * servir. Una cuenta con contraseña conserva su token de restablecer, como siempre.
	 */
	@Test
	void adminPoneContrasenaAPendiente_borraElToken() throws Exception {
		String correo = emailNuevo("admin-clave");
		long id = invitar(correo, "PROFESOR");
		String token = enviadoA(correo).token();

		assertEquals(200, estado(conCuerpo(put("/api/admin/users/" + id + "/password"),
				cuerpo(Map.of("nuevaContrasena", CONTRASENA))), tPropietario));

		assertFalse(pendiente(id));
		assertNull(hashDe(id));
		assertMensaje(aceptar(token, "Otra-Clave-2027"), 400, ENLACE_NO_VALE);
		assertEquals(400, mvc.perform(conCuerpo(post("/api/auth/password/reset"), cuerpo(Map.of("token", token,
				"nuevaContrasena", "Otra-Clave-2027")))).andReturn().getResponse().getStatus());
		assertEquals(200, mvc.perform(conCuerpo(post("/api/auth/signin"),
				cuerpo(Map.of("email", correo, "contrasena", CONTRASENA)))).andReturn().getResponse().getStatus());

		long conClave = e.cuenta(usuarios, "admin-con-clave-" + contador.incrementAndGet());
		membresia(e.org, conClave, "PROFESOR");
		String hash = "c".repeat(64);
		jdbc.update("UPDATE usuario SET reset_token_hash = ?, reset_token_expires_at = ? WHERE id = ?", hash,
				LocalDateTime.now().plusHours(1), conClave);
		assertEquals(200, estado(conCuerpo(put("/api/admin/users/" + conClave + "/password"),
				cuerpo(Map.of("nuevaContrasena", CONTRASENA))), tPropietario));
		assertEquals(hash, hashDe(conClave));
	}

	@Test
	void contrasenaEnBlancoSigueRechazada() throws Exception {
		String correo = emailNuevo("en-blanco");
		assertEquals(400, mvc.perform(conCuerpo(post("/api/auth/signup"), cuerpo(Map.of("nombre", "Ana",
				"apellidos", "Blanco", "email", correo, "contrasena", "")))).andReturn().getResponse().getStatus());
		assertEquals(400, mvc.perform(conCuerpo(post("/api/auth/register/request"), cuerpo(Map.of("email", correo,
				"fechaNacimiento", "2010-01-01", "contrasena", "  ")))).andReturn().getResponse().getStatus());
		assertEquals(400, mvc.perform(conCuerpo(post("/api/auth/password/reset"), cuerpo(Map.of("token",
				"cualquiera", "nuevaContrasena", "")))).andReturn().getResponse().getStatus());
		assertEquals(0, cuentasCon(correo));
		// La entidad tampoco guarda una cadena vacía (solo NULL, la invitación pendiente).
		Usuario vacia = new Usuario();
		vacia.setNombre("Ana");
		vacia.setApellidos("Blanco");
		vacia.setEmail(correo);
		vacia.setContrasena("");
		vacia.setAuthProvider(AuthProvider.LOCAL);
		assertThrows(Exception.class,
				() -> TenantContext.calcularCon(EscuelaConSedesDePrueba.ORG1, () -> usuarios.saveAndFlush(vacia)));
		assertEquals(0, cuentasCon(correo));
	}

	// ------------------------------------------------------------------ ayudas

	private long invitar(String correo, String rol, long... sedes) throws Exception {
		Map<String, Object> datos = invitacion(correo, rol, "Ana", "Invitada");
		if (sedes.length > 0) {
			List<Long> ids = new ArrayList<>();
			for (long sede : sedes) {
				ids.add(sede);
			}
			datos.put("sedeIds", ids);
		}
		return leer(conCuerpo(post(RUTA), cuerpo(datos)), tPropietario, 200).get("usuarioId").asLong();
	}

	private static Map<String, Object> invitacion(String correo, String rol, String nombre, String apellidos) {
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("email", correo);
		datos.put("rol", rol);
		datos.put("nombre", nombre);
		datos.put("apellidos", apellidos);
		return datos;
	}

	private static MiembrosRequests.Alta alta(String correo, String rol, String nombre, String apellidos) {
		return new MiembrosRequests.Alta(correo, rol, null, null, null, null, nombre, apellidos);
	}

	private static Map<String, Object> asig(long grupo, String rol) {
		Map<String, Object> asignacion = new LinkedHashMap<>();
		asignacion.put("grupoId", grupo);
		asignacion.put("rol", rol);
		return asignacion;
	}

	private MvcResult aceptar(String token, String contrasena) throws Exception {
		return mvc.perform(conCuerpo(post(ACEPTAR), cuerpo(Map.of("token", token, "nuevaContrasena", contrasena))))
				.andReturn();
	}

	private MvcResult datos(String token) throws Exception {
		return mvc.perform(conCuerpo(post("/api/auth/invitacion/datos"), cuerpo(Map.of("token", token)))).andReturn();
	}

	/** El valor de la cookie {@code jwt} no vacía que fija la respuesta, o {@code null}. */
	private static String cookieJwt(MvcResult r) {
		for (String cabecera : r.getResponse().getHeaders(HttpHeaders.SET_COOKIE)) {
			if (cabecera.startsWith("jwt=") && !cabecera.startsWith("jwt=;")) {
				return cabecera.substring(4, cabecera.indexOf(';') < 0 ? cabecera.length() : cabecera.indexOf(';'));
			}
		}
		return null;
	}

	/** El único correo de invitación enviado a {@code para}. */
	private Correo enviadoA(String para) {
		List<Correo> correos = enviadosA(para);
		assertEquals(1, correos.size(), "correos a " + para);
		return correos.get(0);
	}

	private Correo ultimoEnviadoA(String para) {
		List<Correo> correos = enviadosA(para);
		return correos.get(correos.size() - 1);
	}

	private List<Correo> enviadosA(String para) {
		ArgumentCaptor<String> asunto = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<byte[]> imagen = ArgumentCaptor.forClass(byte[].class);
		ArgumentCaptor<String> remitente = ArgumentCaptor.forClass(String.class);
		verify(email, atLeastOnce()).sendEmailConImagen(eq(para), asunto.capture(), html.capture(), any(),
				imagen.capture(), any(), remitente.capture());
		List<Correo> correos = new ArrayList<>();
		for (int i = 0; i < html.getAllValues().size(); i++) {
			correos.add(new Correo(para, asunto.getAllValues().get(i), html.getAllValues().get(i),
					imagen.getAllValues().get(i), remitente.getAllValues().get(i)));
		}
		return correos;
	}

	@FunctionalInterface
	interface Bloque {
		void ejecutar() throws Exception;
	}

	/**
	 * Ejecuta el bloque con la raíz de logback en DEBUG y devuelve todo lo que se registró (sin pintarlo en la
	 * consola); deja la raíz como estaba.
	 */
	private static List<ILoggingEvent> conLogsCapturados(Bloque bloque) throws Exception {
		Logger raiz = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
		Level nivel = raiz.getLevel();
		List<Appender<ILoggingEvent>> anteriores = new ArrayList<>();
		for (Iterator<Appender<ILoggingEvent>> it = raiz.iteratorForAppenders(); it.hasNext();) {
			anteriores.add(it.next());
		}
		ListAppender<ILoggingEvent> lista = new ListAppender<>();
		lista.start();
		anteriores.forEach(raiz::detachAppender);
		raiz.addAppender(lista);
		raiz.setLevel(Level.DEBUG);
		try {
			bloque.ejecutar();
		} finally {
			raiz.setLevel(nivel);
			raiz.detachAppender(lista);
			anteriores.forEach(raiz::addAppender);
			lista.stop();
		}
		return new ArrayList<>(lista.list);
	}

	private static String textoDe(ILoggingEvent log) {
		StringBuilder texto = new StringBuilder(String.valueOf(log.getFormattedMessage()));
		for (IThrowableProxy error = log.getThrowableProxy(); error != null; error = error.getCause()) {
			texto.append(' ').append(error.getMessage());
		}
		return texto.toString();
	}

	private void comoPropietario(Runnable accion) {
		Usuario actor = cuentaEnOrg1(propietario);
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null,
				List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
		try {
			TenantContext.ejecutarCon(e.org, accion);
		} finally {
			SecurityContextHolder.clearContext();
		}
	}

	private String emailNuevo(String nombre) {
		return "it-e8-4-" + nombre + "-" + contador.incrementAndGet() + "-" + e.marca + "@example.test";
	}

	private long cuentasCon(String correo) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE email = ?", Long.class, correo.toLowerCase());
	}

	private boolean pendiente(long id) {
		return jdbc.queryForObject("""
				SELECT COUNT(*) FROM usuario WHERE id = ? AND contrasena IS NULL
				  AND (auth_provider IS NULL OR auth_provider = 'LOCAL')""", Long.class, id) == 1;
	}

	private String hashDe(long id) {
		return jdbc.queryForObject("SELECT reset_token_hash FROM usuario WHERE id = ?", String.class, id);
	}

	private String emailDe(long usuario) {
		return jdbc.queryForObject("SELECT email FROM usuario WHERE id = ?", String.class, usuario);
	}

	private void membresia(long organizacion, long usuario, String rol) {
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", usuario, organizacion,
				rol);
	}

	private List<String> rolesActivos(long usuario, long organizacion) {
		return jdbc.queryForList("""
				SELECT rol FROM membresia WHERE usuario_id = ? AND organizacion_id = ? AND activa = TRUE
				ORDER BY rol""", String.class, usuario, organizacion);
	}

	private Map<Long, String> asignaciones(long usuario, long organizacion) {
		Map<Long, String> resultado = new LinkedHashMap<>();
		for (Map<String, Object> f : jdbc.queryForList(
				"SELECT grupo_id, rol FROM asignacion_profesor WHERE usuario_id = ? AND organizacion_id = ?", usuario,
				organizacion)) {
			resultado.put(numero(f, "grupo_id"), (String) f.get("rol"));
		}
		return resultado;
	}

	private Usuario cuentaEnOrg1(long usuario) {
		return TenantContext.calcularCon(EscuelaConSedesDePrueba.ORG1, () -> usuarios.findById(usuario).orElseThrow());
	}

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

	private static String sha256(String token) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(token.getBytes(StandardCharsets.UTF_8)));
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
		String contenido = contenido(r);
		if (contenido.isBlank()) {
			return "";
		}
		JsonNode nodo = json.readTree(contenido);
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

	/** La fila de la cuenta en una lista de miembros o de profesores. */
	private static JsonNode fila(JsonNode lista, long usuario) {
		for (JsonNode n : lista) {
			if (n.get("usuarioId").asLong() == usuario) {
				return n;
			}
		}
		throw new AssertionError("sin la cuenta " + usuario + ": " + lista);
	}
}
