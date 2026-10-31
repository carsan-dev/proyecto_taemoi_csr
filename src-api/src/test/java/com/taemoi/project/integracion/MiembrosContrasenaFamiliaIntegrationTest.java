package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GestionMiembrosService;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.Cookie;

/**
 * Pieza 9-8 contra MySQL (decisiones 373 y 396; cambia la D8-6): lo que hacía «Roles y permisos» pasa a la ficha del
 * miembro.
 * <ul>
 * <li>{@code PUT /api/miembros/{id}/contrasena}: solo el propietario, con las reglas de {@code /api/admin/users/{id}/password}
 * y además 409 con invitación pendiente; la contraseña no queda en ninguna fila de auditoría.</li>
 * <li>Campo {@code contrasena} de la ficha: los cuatro valores y su prioridad; la lista no lo lleva.</li>
 * <li>FAMILIA por {@code PUT} y {@code DELETE /roles/FAMILIA}: solo el propietario; nunca toca {@code usuario_alumno}.</li>
 * </ul>
 * Escuelas sintéticas ({@link EscuelaConSedesDePrueba}); cada test crea sus cuentas y mira solo sus filas, así que el orden
 * no importa. La escuela por defecto solo la usan los casos de cuentas legadas, con cuentas propias. Datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MiembrosContrasenaFamiliaIntegrationTest extends MySqlIntegrationTest {

	private static final String RUTA = "/api/miembros";
	private static final String NO_MIEMBRO = "La cuenta no es miembro de esta escuela.";
	private static final String SOLO_PROPIETARIO = "Solo el propietario da o quita los roles de propietario y gestor.";
	private static final String FAMILIA_EN_ALTA = "La familia se vincula desde la ficha del alumno.";
	private static final String FAMILIA_SIN_CARGO = "El rol de familia no lleva cargo.";
	private static final String SEDES_SOLO_RESPONSABLE = "Las sedes solo se indican para el rol de responsable de sede.";
	private static final String SIN_ESE_ROL = "La cuenta no tiene ese rol en esta escuela.";
	private static final String OTRA_ESCUELA = "El usuario también pertenece a otra escuela: no se puede cambiar su contraseña desde esta escuela.";
	private static final String GOOGLE = "Las cuentas de Google no permiten restablecer contrasena manualmente.";
	private static final String PENDIENTE = "Tiene una invitación pendiente: reenvíala o cancélala.";
	private static final String SIN_ESCUELA = "El usuario no tiene acceso a ninguna escuela.";
	private static final String SIN_ALUMNOS_ACTIVOS = "No hay alumnos activos asociados a esta cuenta.";
	private static final String PAYLOAD_OMITIDO = "[payload omitted: contrasena]";
	private static final String CUERPO_NO_VALIDO = "El cuerpo de la petición no es válido.";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;
	@Autowired GestionMiembrosService miembros;
	@Autowired PasswordEncoder encoder;
	@Autowired PlatformTransactionManager transacciones;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	long propietario, propietarioOrg1;
	String tPropietario, tGestor, tFamilia, tResponsable, tProfesor, tPlataforma, tPropietarioOrg1;
	final AtomicInteger contador = new AtomicInteger();

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-9-8");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-9-8-otra");

		propietario = e.cuenta(usuarios, "propietario");
		membresia(e.org, propietario, "PROPIETARIO");
		long familia = e.cuentaFamilia(usuarios, "familia");
		long profesor = e.cuenta(usuarios, "profesor");
		membresia(e.org, profesor, "PROFESOR");
		long plataforma = e.cuenta(usuarios, "plataforma");
		jdbc.update("UPDATE usuario SET es_admin_plataforma = TRUE WHERE id = ?", plataforma);
		propietarioOrg1 = e.cuenta(usuarios, "propietario-org1");
		membresia(EscuelaConSedesDePrueba.ORG1, propietarioOrg1, "PROPIETARIO");

		tPropietario = e.token(jwtService, usuarios, propietario);
		tGestor = e.token(jwtService, usuarios, e.gestor);
		tFamilia = e.token(jwtService, usuarios, familia);
		tResponsable = e.token(jwtService, usuarios, e.respA);
		tProfesor = e.token(jwtService, usuarios, profesor);
		tPlataforma = jwtService.generateToken(cuentaEnOrg1(plataforma), 600_000, EscuelaConSedesDePrueba.ORG1);
		tPropietarioOrg1 = jwtService.generateToken(cuentaEnOrg1(propietarioOrg1), 600_000, EscuelaConSedesDePrueba.ORG1);
	}

	@AfterAll
	void retirar() {
		jdbc.update("UPDATE membresia SET activa = FALSE WHERE usuario_id = ? AND organizacion_id = ?", propietarioOrg1,
				EscuelaConSedesDePrueba.ORG1);
	}

	@AfterEach
	void sinFugas() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ contraseña

	@Test
	void propietarioRestablece_laCuentaEntraConLaNueva_unaFilaDeAuditoria() throws Exception {
		long cuenta = cuenta("restablece");
		membresia(e.org, cuenta, "PROFESOR");
		String clave = clave();
		String antes = hash(cuenta);

		long evento = ultimoEvento();
		JsonNode ficha = leer(contrasena(cuenta, clave), tPropietario, 200);
		assertEquals(cuenta, ficha.get("usuarioId").asLong(), "devuelve la ficha");
		assertEquals("RESTABLECIBLE", ficha.get("contrasena").asText());

		assertNotEquals(antes, hash(cuenta));
		assertTrue(encoder.matches(clave, hash(cuenta)));
		List<Map<String, Object>> filas = eventosDesde(evento);
		assertEquals(List.of("MIEMBROS CONTRASENA_RESTABLECIDA usuario=#" + cuenta), resumenes(filas));
		Map<String, Object> fila = filas.get(0);
		assertEquals("UPDATE", fila.get("accion"));
		assertEquals("miembros", fila.get("modulo"));
		assertEquals(e.org, ((Number) fila.get("organizacion_id")).longValue());
		assertEquals(RUTA + "/" + cuenta + "/contrasena", fila.get("endpoint"));
		sinTexto(evento, clave);

		assertEquals(200, entrar(emailDe(cuenta), clave).getResponse().getStatus());
		assertEquals(401, entrar(emailDe(cuenta), "fake-Password-123").getResponse().getStatus(), "la anterior ya no vale");
	}

	@Test
	void soloElPropietario_losDemasDenegados() throws Exception {
		long cuenta = cuenta("denegada");
		membresia(e.org, cuenta, "PROFESOR");
		String antes = hash(cuenta);
		for (String token : List.of(tGestor, tResponsable, tProfesor, tFamilia, tPlataforma)) {
			assertEquals(403, peticion(contrasena(cuenta, clave()), token).getResponse().getStatus());
		}
		assertEquals(401, mvc.perform(contrasena(cuenta, clave())).andReturn().getResponse().getStatus());
		assertEquals(antes, hash(cuenta));
	}

	@Test
	void cuentaDeOtraEscuela_404() throws Exception {
		long ajena = otra.cuenta(usuarios, "ajena-" + contador.incrementAndGet());
		membresia(otra.org, ajena, "PROFESOR");
		String antes = hash(ajena);
		assertMensaje(peticion(contrasena(ajena, clave()), tPropietario), 404, NO_MIEMBRO);
		assertMensaje(peticion(contrasena(99_999_999L, clave()), tPropietario), 404, NO_MIEMBRO);
		assertEquals(antes, hash(ajena));
	}

	@Test
	void conMembresiaEnOtraEscuela_403_aunqueEsteInactiva() throws Exception {
		long activa = cuenta("otra-activa");
		membresia(e.org, activa, "PROFESOR");
		membresia(otra.org, activa, "PROFESOR");
		long inactiva = cuenta("otra-inactiva");
		membresia(e.org, inactiva, "PROFESOR");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol, activa) VALUES (?, ?, 'FAMILIA', FALSE)",
				inactiva, otra.org);
		for (long cuenta : List.of(activa, inactiva)) {
			String antes = hash(cuenta);
			assertMensaje(peticion(contrasena(cuenta, clave()), tPropietario), 403, OTRA_ESCUELA);
			assertEquals(antes, hash(cuenta));
		}
	}

	@Test
	void cuentaDeGoogle_400() throws Exception {
		long cuenta = cuenta("google");
		membresia(e.org, cuenta, "PROFESOR");
		jdbc.update("UPDATE usuario SET auth_provider = 'GOOGLE' WHERE id = ?", cuenta);
		String antes = hash(cuenta);
		assertMensaje(peticion(contrasena(cuenta, clave()), tPropietario), 400, GOOGLE);
		assertEquals(antes, hash(cuenta));
	}

	@Test
	void conInvitacionPendiente_409_yElEnlaceSigueVivo() throws Exception {
		long cuenta = pendiente("pendiente");
		membresia(e.org, cuenta, "PROFESOR");
		jdbc.update("UPDATE usuario SET reset_token_hash = 'hash-de-la-invitacion' WHERE id = ?", cuenta);

		long evento = ultimoEvento();
		assertMensaje(peticion(contrasena(cuenta, clave()), tPropietario), 409, PENDIENTE);

		assertNull(hash(cuenta), "sigue sin contraseña");
		assertEquals("hash-de-la-invitacion",
				jdbc.queryForObject("SELECT reset_token_hash FROM usuario WHERE id = ?", String.class, cuenta));
		assertEquals(0, eventosDesde(evento).stream().filter(f -> f.get("resumen").toString().startsWith("MIEMBROS "))
				.count(), "sin registro explícito");
	}

	/** Reglas en el orden de {@code AdminController}: otra escuela antes que Google e invitación pendiente. */
	@Test
	void variasReglas_primeroOtraEscuela() throws Exception {
		long google = cuenta("varias-google");
		membresia(e.org, google, "PROFESOR");
		membresia(otra.org, google, "PROFESOR");
		jdbc.update("UPDATE usuario SET auth_provider = 'GOOGLE' WHERE id = ?", google);
		assertMensaje(peticion(contrasena(google, clave()), tPropietario), 403, OTRA_ESCUELA);

		long invitada = pendiente("varias-pendiente");
		membresia(e.org, invitada, "PROFESOR");
		membresia(otra.org, invitada, "PROFESOR");
		assertMensaje(peticion(contrasena(invitada, clave()), tPropietario), 403, OTRA_ESCUELA);
	}

	@Test
	void contrasenaDebil_400() throws Exception {
		long cuenta = cuenta("debil");
		membresia(e.org, cuenta, "PROFESOR");
		String antes = hash(cuenta);
		for (String mala : List.of("Ab1", "sinmayuscula1", "SINMINUSCULA1", "SinNumeroNinguno", "        ")) {
			long evento = ultimoEvento();
			assertEquals(400, peticion(contrasena(cuenta, mala), tPropietario).getResponse().getStatus(), mala);
			assertEquals(antes, hash(cuenta));
			if (!mala.isBlank()) {
				sinTexto(evento, mala);
			}
		}
		assertEquals(400, peticion(conCuerpo(put(ruta(cuenta)), "{}"), tPropietario).getResponse().getStatus());
		assertEquals(400, peticion(put(ruta(cuenta)), tPropietario).getResponse().getStatus(), "sin cuerpo");
		assertEquals(antes, hash(cuenta));
	}

	/**
	 * Ni el saneador de JSON sirve aquí: un cuerpo malformado o de otro tipo llega como texto plano a la auditoría. Toda
	 * fila genérica de esta ruta omite el cuerpo, responda lo que responda.
	 */
	@Test
	void elCuerpoNoAcabaEnNingunaAuditoria() throws Exception {
		long cuenta = cuenta("auditoria");
		membresia(e.org, cuenta, "PROFESOR");
		String clave = clave();
		String valido = cuerpoContrasena(clave);
		String malformado = "{\"nuevaContrasena\":\"" + clave + "\"";

		long evento = ultimoEvento();
		assertEquals(400, peticion(conCuerpo(put(ruta(cuenta)), malformado), tPropietario).getResponse().getStatus());
		assertEquals(400, peticion(conCuerpo(put(ruta(cuenta)), "{\"nuevaContrasena\":\"" + clave.toLowerCase() + "\"}"),
				tPropietario).getResponse().getStatus());
		assertEquals(415, peticion(put(ruta(cuenta)).contentType(MediaType.TEXT_PLAIN).content(valido), tPropietario)
				.getResponse().getStatus());
		assertEquals(403, peticion(conCuerpo(put(ruta(cuenta)), valido), tGestor).getResponse().getStatus());
		assertEquals(405, peticion(conCuerpo(post(ruta(cuenta)), valido), tPropietario).getResponse().getStatus());
		assertEquals(404, peticion(conCuerpo(put(ruta(cuenta) + "/otra"), valido), tPropietario).getResponse().getStatus(),
				"fuera de la ruta exacta manda el saneador de JSON");
		mvc.perform(conCuerpo(put(ruta(cuenta)), valido)).andReturn();

		List<Map<String, Object>> filas = eventosDesde(evento);
		assertTrue(filas.size() >= 5, filas.toString());
		filas.stream().filter(f -> f.get("endpoint").toString().endsWith("/contrasena"))
				.forEach(f -> assertEquals(PAYLOAD_OMITIDO, f.get("payload_json"), f.toString()));
		sinTexto(evento, clave);
		sinTexto(evento, clave.toLowerCase());
	}

	/** Spring apunta el valor rechazado en un WARN: una contraseña casi válida es casi la real. */
	@Test
	void laContrasenaRechazadaNoLlegaAlLog_yElMotivoVaEnMensaje() throws Exception {
		long cuenta = cuenta("log");
		membresia(e.org, cuenta, "PROFESOR");
		String casiBuena = clave().toLowerCase();
		Logger raiz = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		ListAppender<ILoggingEvent> eventos = new ListAppender<>();
		eventos.start();
		raiz.addAppender(eventos);
		try {
			assertMensaje(peticion(contrasena(cuenta, casiBuena), tPropietario), 400,
					"La contrasena debe contener mayusculas, minusculas y numeros");
			assertEquals(400, peticion(conCuerpo(put(ruta(cuenta)), "{\"nuevaContrasena\":\"" + casiBuena + "\""),
					tPropietario).getResponse().getStatus());
		} finally {
			raiz.detachAppender(eventos);
		}
		for (ILoggingEvent evento : List.copyOf(eventos.list)) {
			String error = evento.getThrowableProxy() == null ? "" : evento.getThrowableProxy().getMessage();
			assertFalse((evento.getFormattedMessage() + error).contains(casiBuena), evento.getFormattedMessage());
		}
	}

	/**
	 * Jackson cita el token que no entiende y {@code DefaultHandlerExceptionResolver} lo deja en un WARN: sin comillas, ese
	 * token es la contraseña. Tampoco con otro tipo de contenido.
	 */
	@Test
	void cuerpoMalformadoOdeOtroTipo_elValorNoLlegaAlLog() throws Throwable {
		long cuenta = cuenta("log-malformado");
		membresia(e.org, cuenta, "PROFESOR");
		String antes = hash(cuenta);
		String valor = valorSinComillas();
		List<MvcResult> malformados = new ArrayList<>();

		sinValorEnElLog(valor, () -> {
			for (String cuerpo : List.of("{\"nuevaContrasena\":" + valor + "}", valor, "{\"nuevaContrasena\":\"" + valor + "\"")) {
				malformados.add(peticion(conCuerpo(put(ruta(cuenta)), cuerpo), tPropietario));
			}
			assertEquals(415, peticion(put(ruta(cuenta)).contentType(MediaType.TEXT_PLAIN).content(valor), tPropietario)
					.getResponse().getStatus());
			assertEquals(415, peticion(put(ruta(cuenta)).contentType(MediaType.TEXT_PLAIN)
					.content("{\"nuevaContrasena\":" + valor + "}"), tPropietario).getResponse().getStatus());
		});

		assertEquals(3, malformados.size());
		for (MvcResult malformado : malformados) {
			assertMensaje(malformado, 400, CUERPO_NO_VALIDO);
			assertFalse(contenido(malformado).contains(valor));
		}
		assertEquals(antes, hash(cuenta));
	}

	/** Sin cuerpo tampoco se puede leer: sigue en 400, ahora con su mensaje. */
	@Test
	void sinCuerpo_400ConMensaje() throws Exception {
		long cuenta = cuenta("sin-cuerpo");
		membresia(e.org, cuenta, "PROFESOR");
		assertMensaje(peticion(put(ruta(cuenta)), tPropietario), 400, CUERPO_NO_VALIDO);
	}

	/** El resto de endpoints con cuerpo sigue en 400 y sin escribir nada; el handler es de todo el controlador. */
	@Test
	void losDemasEndpointsConCuerpo_400SiEstaMalformado() throws Throwable {
		long cuenta = cuenta("malformado-demas");
		membresia(e.org, cuenta, "PROFESOR");
		String valor = valorSinComillas();
		String cuerpo = "{\"campo\":" + valor + "}";

		sinValorEnElLog(valor, () -> {
			for (String token : List.of(tPropietario, tGestor)) {
				assertMensaje(peticion(conCuerpo(post(RUTA), cuerpo), token), 400, CUERPO_NO_VALIDO);
				assertMensaje(peticion(conCuerpo(put(RUTA + "/" + cuenta + "/roles/PROFESOR"), cuerpo), token), 400,
						CUERPO_NO_VALIDO);
				assertMensaje(peticion(conCuerpo(put(RUTA + "/" + cuenta + "/sedes"), cuerpo), token), 400, CUERPO_NO_VALIDO);
				assertMensaje(peticion(conCuerpo(put(RUTA + "/" + cuenta + "/grupos"), cuerpo), token), 400, CUERPO_NO_VALIDO);
			}
		});
		assertEquals("PROFESOR:true", roles(leer(get(RUTA + "/" + cuenta), tPropietario, 200)));
	}

	@Test
	void elTokenDeRestablecerDeUnaCuentaConContrasenaSigueVivo() throws Exception {
		long cuenta = cuenta("token");
		membresia(e.org, cuenta, "PROFESOR");
		jdbc.update("""
				UPDATE usuario SET reset_token_hash = 'hash-de-restablecer', reset_token_expires_at = NOW() + INTERVAL 1 DAY
				WHERE id = ?""", cuenta);

		assertEquals(200, peticion(contrasena(cuenta, clave()), tPropietario).getResponse().getStatus());

		assertEquals(1, jdbc.queryForObject("""
				SELECT COUNT(*) FROM usuario WHERE id = ? AND reset_token_hash = 'hash-de-restablecer'
				AND reset_token_expires_at IS NOT NULL""", Long.class, cuenta));
	}

	/**
	 * La entidad de la sesión se cargó antes del bloqueo (como {@code exigirMiembro}) y otra transacción confirmó entre
	 * medias. Con la fila bloqueada manda la base: la cuenta YA no tiene contraseña, 409.
	 */
	@Test
	void laCuentaPasaAPendienteAntesDelBloqueo_409ConLaEntidadAntigua() throws Exception {
		long cuenta = cuenta("carrera-pendiente");
		membresia(e.org, cuenta, "PROFESOR");

		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> conEntidadAntigua(cuenta, "UPDATE usuario SET contrasena = NULL WHERE id = ?", clave()));
		assertEquals(PENDIENTE, error.getMessage());
		assertNull(hash(cuenta));
	}

	/** Al revés: la entidad antigua seguía pendiente, pero la invitación ya se aceptó. No es un 409 falso. */
	@Test
	void laInvitacionSeAceptaAntesDelBloqueo_seRestablece() throws Exception {
		long cuenta = pendiente("carrera-aceptada");
		membresia(e.org, cuenta, "PROFESOR");
		String clave = clave();

		conEntidadAntigua(cuenta, "UPDATE usuario SET contrasena = 'aceptada' WHERE id = ?", clave);

		assertTrue(encoder.matches(clave, hash(cuenta)));
	}

	/** Cuenta legada: sin ninguna membresía sigue siendo miembro de la escuela por defecto; de las demás, no. */
	@Test
	void cuentaLegada_soloEnLaEscuelaPorDefecto() throws Exception {
		long legada = cuenta("legada-contrasena");
		assertMensaje(peticion(contrasena(legada, clave()), tPropietario), 404, NO_MIEMBRO);

		JsonNode ficha = leer(get(RUTA + "/" + legada), tPropietarioOrg1, 200);
		assertEquals("RESTABLECIBLE", ficha.get("contrasena").asText());
		String clave = clave();
		assertEquals(200, peticion(contrasena(legada, clave), tPropietarioOrg1).getResponse().getStatus());
		assertTrue(encoder.matches(clave, hash(legada)));
	}

	// ------------------------------------------------------------------ cuentas de plataforma (D8-13, decisión 397)

	/** Ni la ruta nueva ni la antigua; la ficha y el 403 dicen «otra escuela», nunca que es de plataforma. */
	@Test
	void superadminConMembresiaEnLaEscuela_403PorLasDosRutas() throws Exception {
		long cuenta = superadmin("sa-miembro");
		membresia(e.org, cuenta, "PROFESOR");
		String antes = hash(cuenta);

		assertEquals("OTRA_ESCUELA", campoContrasena(cuenta));
		assertMensaje(peticion(contrasena(cuenta, clave()), tPropietario), 403, OTRA_ESCUELA);
		assertMensaje(peticion(contrasenaRutaAntigua(cuenta, clave()), tPropietario), 403, OTRA_ESCUELA);
		assertEquals(antes, hash(cuenta));
	}

	/** Darla de alta por su email le crea su única membresía aquí: eso no la hace de esta escuela. */
	@Test
	void superadminSinMembresiaTrasUnAlta_403PorLasDosRutas() throws Exception {
		long cuenta = superadmin("sa-alta");
		String antes = hash(cuenta);
		String alta = json.writeValueAsString(Map.of("email", emailDe(cuenta), "rol", "PROFESOR"));

		assertEquals("OTRA_ESCUELA", leer(conCuerpo(post(RUTA), alta), tPropietario, 200).get("contrasena").asText());
		assertMensaje(peticion(contrasena(cuenta, clave()), tPropietario), 403, OTRA_ESCUELA);
		assertMensaje(peticion(contrasenaRutaAntigua(cuenta, clave()), tPropietario), 403, OTRA_ESCUELA);
		assertEquals(antes, hash(cuenta));
	}

	/** Sin ninguna membresía es miembro de la escuela por defecto, como una cuenta legada; tampoco desde ahí. */
	@Test
	void superadminSinMembresia_403DesdeLaEscuelaPorDefecto() throws Exception {
		long cuenta = superadmin("sa-legada");
		String antes = hash(cuenta);

		assertEquals("OTRA_ESCUELA", leer(get(RUTA + "/" + cuenta), tPropietarioOrg1, 200).get("contrasena").asText());
		assertMensaje(peticion(contrasena(cuenta, clave()), tPropietarioOrg1), 403, OTRA_ESCUELA);
		assertMensaje(peticion(contrasenaRutaAntigua(cuenta, clave()), tPropietarioOrg1), 403, OTRA_ESCUELA);
		assertEquals(antes, hash(cuenta));
	}

	/** Control de los tres anteriores: una cuenta normal se restablece por las dos rutas. */
	@Test
	void cuentaNormal_200PorLasDosRutas() throws Exception {
		long cuenta = cuenta("control-rutas");
		membresia(e.org, cuenta, "PROFESOR");

		assertEquals("RESTABLECIBLE", campoContrasena(cuenta));
		String nueva = clave();
		assertEquals(200, peticion(contrasena(cuenta, nueva), tPropietario).getResponse().getStatus());
		assertTrue(encoder.matches(nueva, hash(cuenta)));
		String antigua = clave();
		assertEquals(200, peticion(contrasenaRutaAntigua(cuenta, antigua), tPropietario).getResponse().getStatus());
		assertTrue(encoder.matches(antigua, hash(cuenta)));
	}

	// ------------------------------------------------------------------ campo de la ficha

	@Test
	void fichaIndicaSiSePuedeRestablecer_losCuatroValores() throws Exception {
		long restablecible = cuenta("ficha-ok");
		membresia(e.org, restablecible, "PROFESOR");
		long google = cuenta("ficha-google");
		membresia(e.org, google, "PROFESOR");
		jdbc.update("UPDATE usuario SET auth_provider = 'GOOGLE' WHERE id = ?", google);
		long otraEscuela = cuenta("ficha-otra");
		membresia(e.org, otraEscuela, "PROFESOR");
		membresia(otra.org, otraEscuela, "PROFESOR");
		long invitada = pendiente("ficha-pendiente");
		membresia(e.org, invitada, "PROFESOR");

		assertEquals("RESTABLECIBLE", campoContrasena(restablecible));
		assertEquals("GOOGLE", campoContrasena(google));
		assertEquals("OTRA_ESCUELA", campoContrasena(otraEscuela));
		assertEquals("INVITACION_PENDIENTE", campoContrasena(invitada));
	}

	@Test
	void fichaPrioridad_pendiente_google_otraEscuela() throws Exception {
		long invitadaEnDos = pendiente("prioridad-pendiente");
		membresia(e.org, invitadaEnDos, "PROFESOR");
		membresia(otra.org, invitadaEnDos, "PROFESOR");
		long googleEnDos = cuenta("prioridad-google");
		membresia(e.org, googleEnDos, "PROFESOR");
		membresia(otra.org, googleEnDos, "PROFESOR");
		jdbc.update("UPDATE usuario SET auth_provider = 'GOOGLE' WHERE id = ?", googleEnDos);

		assertEquals("INVITACION_PENDIENTE", campoContrasena(invitadaEnDos));
		assertEquals("GOOGLE", campoContrasena(googleEnDos));
	}

	@Test
	void elCampoVaEnLaFichaDeCadaEscritura_noEnLaLista() throws Exception {
		String marca = "Campo" + e.marca;
		long cuenta = cuenta(marca.toLowerCase());
		jdbc.update("UPDATE usuario SET apellidos = ? WHERE id = ?", marca, cuenta);
		membresia(e.org, cuenta, "PROFESOR");

		assertEquals("RESTABLECIBLE", leer(put(RUTA + "/" + cuenta + "/roles/FAMILIA"), tPropietario, 200).get("contrasena")
				.asText());
		assertEquals("RESTABLECIBLE", leer(get(RUTA + "/" + cuenta), tGestor, 200).get("contrasena").asText());
		JsonNode pagina = leer(get(RUTA).param("q", marca), tPropietario, 200);
		assertEquals(1, pagina.get("contenido").size());
		assertFalse(pagina.get("contenido").get(0).has("contrasena"), "la lista no lo calcula");
	}

	// ------------------------------------------------------------------ familia

	@Test
	void darReactivarYQuitarFamilia_conSuRegistro() throws Exception {
		long cuenta = cuenta("familia-ciclo");
		membresia(e.org, cuenta, "PROFESOR");
		String rol = RUTA + "/" + cuenta + "/roles/FAMILIA";

		long evento = ultimoEvento();
		assertEquals("PROFESOR:true,FAMILIA:true", roles(leer(put(rol), tPropietario, 200)));
		assertEquals("PROFESOR:true,FAMILIA:true", roles(leer(conCuerpo(put(rol), "{}"), tPropietario, 200)), "ya activa");
		assertEquals(1, filasFamilia(cuenta));
		assertEquals("PROFESOR:true,FAMILIA:false", roles(leer(delete(rol), tPropietario, 200)));
		assertEquals(1, filasFamilia(cuenta), "quitar no borra la fila");
		assertEquals(0, familiaActiva(cuenta));
		assertEquals("PROFESOR:true,FAMILIA:false", roles(leer(delete(rol), tPropietario, 200)), "ya quitada");
		assertEquals("PROFESOR:true,FAMILIA:true", roles(leer(put(rol), tPropietario, 200)));
		assertEquals(1, filasFamilia(cuenta), "reactiva la misma fila");
		assertEquals(1, familiaActiva(cuenta));

		String sufijo = " usuario=#" + cuenta + " rol=FAMILIA";
		assertEquals(List.of("MIEMBROS ROL_DADO" + sufijo, "MIEMBROS ROL_DADO" + sufijo + " sinCambios=SI",
				"MIEMBROS ROL_QUITADO" + sufijo, "MIEMBROS ROL_QUITADO" + sufijo + " sinCambios=SI",
				"MIEMBROS ROL_REACTIVADO" + sufijo), resumenes(eventosDesde(evento)), "una fila por escritura");
	}

	@Test
	void gestor403EnFamilia_enTodosLosCasos() throws Exception {
		long sinRol = cuenta("gestor-sin");
		membresia(e.org, sinRol, "PROFESOR");
		long activa = cuenta("gestor-activa");
		membresia(e.org, activa, "FAMILIA");
		long inactiva = cuenta("gestor-inactiva");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol, activa) VALUES (?, ?, 'FAMILIA', FALSE)",
				inactiva, e.org);
		long ajena = otra.cuenta(usuarios, "gestor-ajena-" + contador.incrementAndGet());
		membresia(otra.org, ajena, "PROFESOR");

		for (long cuenta : List.of(sinRol, activa, inactiva, ajena)) {
			String rol = RUTA + "/" + cuenta + "/roles/FAMILIA";
			assertMensaje(peticion(put(rol), tGestor), 403, SOLO_PROPIETARIO);
			assertMensaje(peticion(conCuerpo(put(rol), "{\"cargo\":\"Tutora\"}"), tGestor), 403, SOLO_PROPIETARIO);
			assertMensaje(peticion(conCuerpo(put(rol), "{\"cargo\":\"" + "x".repeat(101) + "\"}"), tGestor), 403,
					SOLO_PROPIETARIO);
			assertMensaje(peticion(conCuerpo(put(rol), "{\"sedeIds\":[" + e.sA + "]}"), tGestor), 403, SOLO_PROPIETARIO);
			assertMensaje(peticion(delete(rol), tGestor), 403, SOLO_PROPIETARIO);
		}
		assertEquals(0, familiaActiva(sinRol));
		assertEquals(0, filasFamilia(sinRol));
		assertEquals(1, familiaActiva(activa));
		assertEquals(0, familiaActiva(inactiva));
		assertEquals(0, filasFamilia(ajena));
	}

	@Test
	void losDemasRolesNoLlegan() throws Exception {
		long cuenta = cuenta("familia-denegada");
		membresia(e.org, cuenta, "PROFESOR");
		String rol = RUTA + "/" + cuenta + "/roles/FAMILIA";
		for (String token : List.of(tResponsable, tProfesor, tFamilia, tPlataforma)) {
			assertEquals(403, peticion(put(rol), token).getResponse().getStatus());
			assertEquals(403, peticion(delete(rol), token).getResponse().getStatus());
		}
		assertEquals(401, mvc.perform(put(rol)).andReturn().getResponse().getStatus());
		assertEquals(0, filasFamilia(cuenta));
	}

	@Test
	void quitarFamilia_noTocaLosVinculosNiElAlumno() throws Exception {
		long cuenta = e.cuentaFamilia(usuarios, "vinculos-" + contador.incrementAndGet());
		long alumno = jdbc.queryForObject("SELECT alumno_id FROM usuario_alumno WHERE usuario_id = ?", Long.class, cuenta);
		List<Map<String, Object>> vinculos = vinculosDe(cuenta);
		Map<String, Object> filaAlumno = jdbc.queryForMap("SELECT * FROM alumno WHERE id = ?", alumno);
		String rol = RUTA + "/" + cuenta + "/roles/FAMILIA";
		assertEquals(1, vinculos.size());

		assertEquals(200, peticion(delete(rol), tPropietario).getResponse().getStatus());
		assertEquals(vinculos, vinculosDe(cuenta), "los vínculos siguen tal cual");
		assertEquals(filaAlumno, jdbc.queryForMap("SELECT * FROM alumno WHERE id = ?", alumno));

		assertEquals(200, peticion(put(rol), tPropietario).getResponse().getStatus());
		assertEquals(vinculos, vinculosDe(cuenta), "ni al devolverlo");
		assertEquals(filaAlumno, jdbc.queryForMap("SELECT * FROM alumno WHERE id = ?", alumno));
	}

	@Test
	void familiaSinCargoNiSedes_400() throws Exception {
		long cuenta = cuenta("familia-cargo");
		membresia(e.org, cuenta, "PROFESOR");
		String rol = RUTA + "/" + cuenta + "/roles/FAMILIA";

		assertMensaje(peticion(conCuerpo(put(rol), "{\"cargo\":\"Tutora\"}"), tPropietario), 400, FAMILIA_SIN_CARGO);
		assertMensaje(peticion(conCuerpo(put(rol), "{\"sedeIds\":[" + e.sA + "]}"), tPropietario), 400,
				SEDES_SOLO_RESPONSABLE);
		assertEquals(0, filasFamilia(cuenta));

		assertEquals("PROFESOR:true,FAMILIA:true", roles(leer(conCuerpo(put(rol), "{\"cargo\":\"   \"}"), tPropietario, 200)),
				"cargo vacío: como si no llegara");
		assertEquals(1, jdbc.queryForObject("""
				SELECT COUNT(*) FROM membresia WHERE usuario_id = ? AND organizacion_id = ? AND rol = 'FAMILIA'
				AND cargo IS NULL""", Long.class, cuenta, e.org));
	}

	@Test
	void altaConFamilia_sigueDando400() throws Exception {
		long cuenta = cuenta("familia-alta");
		membresia(e.org, cuenta, "PROFESOR");
		String cuerpo = json.writeValueAsString(Map.of("email", emailDe(cuenta), "rol", "FAMILIA"));
		assertMensaje(peticion(conCuerpo(post(RUTA), cuerpo), tPropietario), 400, FAMILIA_EN_ALTA);
		assertMensaje(peticion(conCuerpo(post(RUTA), cuerpo), tGestor), 400, FAMILIA_EN_ALTA);
		assertEquals(0, filasFamilia(cuenta));
	}

	@Test
	void familiaEnLaEscuelaActivaYNadaMas() throws Exception {
		long enDos = cuenta("familia-dos");
		membresia(e.org, enDos, "FAMILIA");
		membresia(otra.org, enDos, "FAMILIA");
		assertEquals(200, peticion(delete(RUTA + "/" + enDos + "/roles/FAMILIA"), tPropietario).getResponse().getStatus());
		assertEquals(0, familiaActiva(enDos));
		assertEquals(1, jdbc.queryForObject("""
				SELECT COUNT(*) FROM membresia WHERE usuario_id = ? AND organizacion_id = ? AND rol = 'FAMILIA'
				AND activa = TRUE""", Long.class, enDos, otra.org), "la de la otra escuela sigue activa");

		long ajena = otra.cuenta(usuarios, "familia-ajena-" + contador.incrementAndGet());
		membresia(otra.org, ajena, "PROFESOR");
		assertMensaje(peticion(put(RUTA + "/" + ajena + "/roles/FAMILIA"), tPropietario), 404, NO_MIEMBRO);
		assertMensaje(peticion(delete(RUTA + "/" + ajena + "/roles/FAMILIA"), tPropietario), 404, NO_MIEMBRO);
		long sinFamilia = cuenta("familia-sin");
		membresia(e.org, sinFamilia, "PROFESOR");
		assertMensaje(peticion(delete(RUTA + "/" + sinFamilia + "/roles/FAMILIA"), tPropietario), 404, SIN_ESE_ROL);
	}

	/** Cuenta legada: PUT crea la fila, como con los demás roles. */
	@Test
	void cuentaLegada_creaLaFilaFamilia() throws Exception {
		long legada = cuenta("legada-familia");
		assertEquals("FAMILIA:true", roles(leer(put(RUTA + "/" + legada + "/roles/FAMILIA"), tPropietarioOrg1, 200)));
		assertEquals(1, jdbc.queryForObject("""
				SELECT COUNT(*) FROM membresia WHERE usuario_id = ? AND organizacion_id = ? AND rol = 'FAMILIA'
				AND activa = TRUE""", Long.class, legada, EscuelaConSedesDePrueba.ORG1));
	}

	/**
	 * Sin ninguna membresía activa, {@code motivoRechazo} responde SIN_ESCUELA: ROL_SIN_ACCESO exige membresías activas de
	 * roles que no abren sesión, y desde la pieza 9-5 todos la abren.
	 */
	@Test
	void conFamiliaQuitadaYSinOtroRol_noEntra() throws Exception {
		long cuenta = e.cuentaFamilia(usuarios, "login-sola-" + contador.incrementAndGet());
		String clave = clave();
		assertEquals(200, peticion(contrasena(cuenta, clave), tPropietario).getResponse().getStatus());
		String rol = RUTA + "/" + cuenta + "/roles/FAMILIA";
		assertEquals(200, entrar(emailDe(cuenta), clave).getResponse().getStatus());

		assertEquals(200, peticion(delete(rol), tPropietario).getResponse().getStatus());
		assertMensaje(entrar(emailDe(cuenta), clave), 403, SIN_ESCUELA);

		assertEquals(200, peticion(put(rol), tPropietario).getResponse().getStatus());
		assertEquals(200, entrar(emailDe(cuenta), clave).getResponse().getStatus(), "devolverlo abre la puerta");
	}

	@Test
	void conFamiliaQuitadaYOtroRol_sigueEntrando() throws Exception {
		long cuenta = cuenta("login-otro");
		membresia(e.org, cuenta, "PROFESOR");
		membresia(e.org, cuenta, "FAMILIA");
		String clave = clave();
		assertEquals(200, peticion(contrasena(cuenta, clave), tPropietario).getResponse().getStatus());

		assertEquals(200, peticion(delete(RUTA + "/" + cuenta + "/roles/FAMILIA"), tPropietario).getResponse().getStatus());

		assertEquals(200, entrar(emailDe(cuenta), clave).getResponse().getStatus());
	}

	/** D5-2: FAMILIA + PROFESOR necesita alumno accesible; darle FAMILIA a un profesor sin alumnos le cierra la puerta. */
	@Test
	void darFamiliaAUnProfesorSinAlumnos_leCierraLaEntrada() throws Exception {
		long cuenta = cuenta("login-profesor");
		membresia(e.org, cuenta, "PROFESOR");
		String clave = clave();
		assertEquals(200, peticion(contrasena(cuenta, clave), tPropietario).getResponse().getStatus());
		assertEquals(200, entrar(emailDe(cuenta), clave).getResponse().getStatus());

		assertEquals(200, peticion(put(RUTA + "/" + cuenta + "/roles/FAMILIA"), tPropietario).getResponse().getStatus());

		assertMensaje(entrar(emailDe(cuenta), clave), 403, SIN_ALUMNOS_ACTIVOS);
	}

	// ------------------------------------------------------------------ datos

	/** Cuenta nueva sin membresías, con su contraseña de prueba. */
	private long cuenta(String nombre) {
		return e.cuenta(usuarios, "m98-" + contador.incrementAndGet() + "-" + nombre);
	}

	/** Cuenta con invitación pendiente: LOCAL y sin contraseña. */
	private long pendiente(String nombre) {
		long id = cuenta(nombre);
		jdbc.update("UPDATE usuario SET contrasena = NULL WHERE id = ?", id);
		return id;
	}

	/** Cuenta nueva sin membresías, marcada como administradora de plataforma. */
	private long superadmin(String nombre) {
		long id = cuenta(nombre);
		jdbc.update("UPDATE usuario SET es_admin_plataforma = TRUE WHERE id = ?", id);
		return id;
	}

	private void membresia(long organizacion, long usuario, String rol) {
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", usuario, organizacion,
				rol);
	}

	private Usuario cuentaEnOrg1(long usuario) {
		return TenantContext.calcularCon(EscuelaConSedesDePrueba.ORG1, () -> usuarios.findById(usuario).orElseThrow());
	}

	/** Contraseña válida y única: se busca tal cual en las filas de auditoría. */
	private String clave() {
		return "Clave-" + contador.incrementAndGet() + "-" + e.marca + "-Zq9";
	}

	/** Contraseña que Jackson cita entera sin comillas: solo letras y dígitos, única en todo el log. */
	private String valorSinComillas() {
		return "ClaveSinComillas" + contador.incrementAndGet() + e.marca + "Zq9";
	}

	/** Ejecuta las peticiones escuchando el log raíz: {@code valor} no aparece en ningún mensaje ni error encadenado. */
	private void sinValorEnElLog(String valor, Executable peticiones) throws Throwable {
		Logger raiz = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		ListAppender<ILoggingEvent> eventos = new ListAppender<>();
		eventos.start();
		raiz.addAppender(eventos);
		try {
			peticiones.execute();
		} finally {
			raiz.detachAppender(eventos);
		}
		for (ILoggingEvent evento : List.copyOf(eventos.list)) {
			StringBuilder texto = new StringBuilder(evento.getFormattedMessage());
			for (IThrowableProxy error = evento.getThrowableProxy(); error != null; error = error.getCause()) {
				texto.append(' ').append(error.getMessage());
			}
			assertFalse(texto.toString().contains(valor), texto.toString());
		}
	}

	private String hash(long usuario) {
		return jdbc.queryForObject("SELECT contrasena FROM usuario WHERE id = ?", String.class, usuario);
	}

	private String emailDe(long usuario) {
		return jdbc.queryForObject("SELECT email FROM usuario WHERE id = ?", String.class, usuario);
	}

	private long filasFamilia(long usuario) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM membresia WHERE usuario_id = ? AND rol = 'FAMILIA'", Long.class,
				usuario);
	}

	private long familiaActiva(long usuario) {
		return jdbc.queryForObject("""
				SELECT COUNT(*) FROM membresia WHERE usuario_id = ? AND organizacion_id = ? AND rol = 'FAMILIA'
				AND activa = TRUE""", Long.class, usuario, e.org);
	}

	private List<Map<String, Object>> vinculosDe(long usuario) {
		return jdbc.queryForList("SELECT * FROM usuario_alumno WHERE usuario_id = ? ORDER BY alumno_id", usuario);
	}

	private String campoContrasena(long cuenta) throws Exception {
		return leer(get(RUTA + "/" + cuenta), tPropietario, 200).get("contrasena").asText();
	}

	/**
	 * Llama al servicio como propietario en una transacción {@code READ_COMMITTED} que ya tiene la cuenta en sesión, tras
	 * confirmar {@code sql} en otra conexión.
	 */
	private void conEntidadAntigua(long cuenta, String sql, String clave) {
		Usuario actor = cuentaEnOrg1(propietario);
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null,
				List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
		TransactionTemplate plantilla = new TransactionTemplate(transacciones);
		plantilla.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
		ExecutorService hilo = Executors.newSingleThreadExecutor();
		try {
			TenantContext.ejecutarCon(e.org, () -> plantilla.executeWithoutResult(estado -> {
				usuarios.findById(cuenta).orElseThrow();
				try {
					assertEquals(1, hilo.submit(() -> jdbc.update(sql, cuenta)).get(30, TimeUnit.SECONDS));
				} catch (Exception fallo) {
					fail(fallo);
				}
				miembros.restablecerContrasena(cuenta, clave);
			}));
		} finally {
			hilo.shutdownNow();
			SecurityContextHolder.clearContext();
		}
	}

	// ------------------------------------------------------------------ auditoría y HTTP

	private long ultimoEvento() {
		return jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM auditoria_evento", Long.class);
	}

	/** Filas posteriores de cualquier escuela: una petición anónima no se apunta en la de la sesión. */
	private List<Map<String, Object>> eventosDesde(long id) {
		return jdbc.queryForList("SELECT * FROM auditoria_evento WHERE id > ? ORDER BY id", id);
	}

	private static List<String> resumenes(List<Map<String, Object>> filas) {
		return filas.stream().map(f -> (String) f.get("resumen")).toList();
	}

	private void sinTexto(long desde, String texto) {
		List<Map<String, Object>> filas = new ArrayList<>(eventosDesde(desde));
		for (Map<String, Object> fila : filas) {
			for (Object valor : fila.values()) {
				assertFalse(String.valueOf(valor).contains(texto), "la contraseña está en la auditoría: " + fila);
			}
		}
	}

	private String ruta(long cuenta) {
		return RUTA + "/" + cuenta + "/contrasena";
	}

	private String cuerpoContrasena(String clave) throws Exception {
		return json.writeValueAsString(Map.of("nuevaContrasena", clave));
	}

	private MockHttpServletRequestBuilder contrasena(long cuenta, String clave) throws Exception {
		return conCuerpo(put(ruta(cuenta)), cuerpoContrasena(clave));
	}

	private MockHttpServletRequestBuilder contrasenaRutaAntigua(long cuenta, String clave) throws Exception {
		return conCuerpo(put("/api/admin/users/" + cuenta + "/password"), cuerpoContrasena(clave));
	}

	private MvcResult entrar(String email, String clave) throws Exception {
		return mvc.perform(conCuerpo(post("/api/auth/signin"),
				json.writeValueAsString(Map.of("email", email, "contrasena", clave)))).andReturn();
	}

	private static MockHttpServletRequestBuilder conCuerpo(MockHttpServletRequestBuilder peticion, String cuerpo) {
		return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private JsonNode leer(MockHttpServletRequestBuilder peticion, String token, int esperado) throws Exception {
		MvcResult r = peticion(peticion, token);
		assertEquals(esperado, r.getResponse().getStatus(), contenido(r));
		return json.readTree(contenido(r));
	}

	private MvcResult peticion(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
	}

	private void assertMensaje(MvcResult r, int estado, String mensaje) throws Exception {
		assertEquals(estado, r.getResponse().getStatus(), contenido(r));
		assertEquals(mensaje, json.readTree(contenido(r)).get("mensaje").asText());
	}

	private static String contenido(MvcResult r) throws Exception {
		return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	/** {@code "ROL:activa,..."} en el orden de la respuesta. */
	private static String roles(JsonNode ficha) {
		List<String> roles = new ArrayList<>();
		ficha.get("roles").forEach(r -> roles.add(r.get("rol").asText() + ":" + r.get("activa").asBoolean()));
		return String.join(",", roles);
	}
}
