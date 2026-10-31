package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.Query;
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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.EmailService;
import com.taemoi.project.services.GestionMiembrosService;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.InvitacionCorreoListener;
import com.taemoi.project.services.JwtService;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.Cookie;

/**
 * Decisión 389 contra MySQL: {@code DELETE /api/miembros/{usuarioId}/invitacion} invalida el enlace, retira los roles de
 * esta escuela y avisa por correo; la cuenta se borra solo si no tiene nada más en ninguna escuela.
 * <ul>
 * <li>Cada referencia a {@code usuario} del esquema está clasificada aquí: una clave ajena nueva hace fallar
 * {@link #cadaClaveAjenaAUsuarioEstaClasificada()} hasta decidir qué hace con ella la cancelación.</li>
 * <li>La carrera con «aceptar» se prueba en los dos órdenes, de forma determinista.</li>
 * </ul>
 * Escuelas sintéticas ({@link EscuelaConSedesDePrueba}); cada test usa emails nuevos y mira solo sus filas, así que el
 * orden no importa. El correo es un mock: no sale nada. Nada en Moiskimdo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CancelarInvitacionIntegrationTest extends MySqlIntegrationTest {

	private static final String RUTA = "/api/miembros";
	private static final String ACEPTAR = "/api/auth/invitacion/aceptar";
	private static final String CONTRASENA = "Nueva-Clave-2026";
	private static final String SOLO_PROPIETARIO = "Solo el propietario da o quita los roles de propietario y gestor.";
	private static final String SIN_PROPIETARIO = "La escuela se quedaría sin propietario.";
	private static final String YA_ACEPTADA = "La invitación ya se aceptó.";
	private static final String SIN_ROL_ACTIVO = "La cuenta no tiene ningún rol activo en esta escuela.";
	private static final String NO_MIEMBRO = "La cuenta no es miembro de esta escuela.";
	private static final String ENLACE_NO_VALE = "El enlace ha caducado o ya se usó. Pide que te lo reenvíen.";
	private static final Pattern TOKEN = Pattern.compile("/crear-contrasena\\?token=([A-Za-z0-9_-]+)");

	/** Filas propias de la invitación: la cancelación las borra de esta escuela (de otra, conservan la cuenta). */
	private static final Set<String> PROPIAS = Set.of("membresia.usuario_id", "asignacion_profesor.usuario_id");
	/** Cualquier fila aquí conserva la cuenta: vínculos con alumnos, legado, dinero, caja y autoría. */
	private static final Set<String> CONSERVAN = Set.of("usuario_alumno.usuario_id", "usuario_rol.usuario_id",
			"cobro.cobrado_por_usuario_id", "cobro.anulado_por_usuario_id", "entrega_caja.entregado_por_usuario_id",
			"entrega_caja.recibido_por_usuario_id", "entrega_caja.anulada_por_usuario_id",
			"movimiento_caja.creado_por_usuario_id", "movimiento_caja.anulado_por_usuario_id",
			"producto_alumno.cancelado_por_usuario_id", "producto_alumno.vendido_por_usuario_id",
			"solicitud.solicitado_por_usuario_id", "solicitud.resuelto_por_usuario_id",
			"solicitud.encargado_por_usuario_id", "solicitud.recibido_por_usuario_id",
			"clase_asistencia.registrado_por_usuario_id", "clase_asistencia.modificado_por_usuario_id");

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;
	@Autowired GestionMiembrosService miembros;
	@Autowired PlatformTransactionManager transacciones;

	@MockitoBean EmailService email;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	long propietario;
	String nombreEscuela, nombreOtra;
	String tPropietario, tGestor, tPropietarioOtra, tResponsable, tProfesor, tFamilia, tResponsableProfesor;
	final AtomicInteger contador = new AtomicInteger();

	/** Un correo enviado: asunto, HTML y nombre del remitente. */
	record Correo(String asunto, String html, String remitente) {
		boolean esCancelacion() {
			return asunto.startsWith("Invitación cancelada");
		}

		String token() {
			Matcher m = TOKEN.matcher(html);
			assertTrue(m.find(), "el correo no lleva el enlace");
			return m.group(1);
		}
	}

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-389");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-389-otra");
		e.crearResponsablesSolos(usuarios);
		nombreEscuela = "Club Cancela " + e.marca;
		nombreOtra = "Club Otro " + otra.marca;
		jdbc.update("UPDATE organizacion SET nombre = ? WHERE id = ?", nombreEscuela, e.org);
		jdbc.update("UPDATE organizacion SET nombre = ? WHERE id = ?", nombreOtra, otra.org);

		propietario = e.cuenta(usuarios, "propietario");
		membresia(e.org, propietario, "PROPIETARIO");
		long propietarioOtra = otra.cuenta(usuarios, "propietario");
		membresia(otra.org, propietarioOtra, "PROPIETARIO");
		long profesor = e.cuenta(usuarios, "profesor");
		membresia(e.org, profesor, "PROFESOR");
		long familia = e.cuentaFamilia(usuarios, "familia");
		long responsableProfesor = e.cuenta(usuarios, "responsable-profesor");
		e.responsable(responsableProfesor, e.sA);
		membresia(e.org, responsableProfesor, "PROFESOR");

		tPropietario = e.token(jwtService, usuarios, propietario);
		tGestor = e.token(jwtService, usuarios, e.gestor);
		tPropietarioOtra = otra.token(jwtService, usuarios, propietarioOtra);
		tResponsable = e.token(jwtService, usuarios, e.respSoloA);
		tProfesor = e.token(jwtService, usuarios, profesor);
		tFamilia = e.token(jwtService, usuarios, familia);
		tResponsableProfesor = e.token(jwtService, usuarios, responsableProfesor);
	}

	@AfterEach
	void sinFugas() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ inventario

	/**
	 * Toda clave ajena a {@code usuario} está clasificada: una columna nueva falla aquí hasta decidir si es fila propia o
	 * conserva la cuenta. Las de {@link #CONSERVAN} salen en la consulta; con {@code ON DELETE SET NULL} o {@code CASCADE}
	 * un borrado sin esa comprobación no daría error: perdería el dato en silencio.
	 */
	@Test
	void cadaClaveAjenaAUsuarioEstaClasificada() throws Exception {
		Set<String> enLaBase = new TreeSet<>(jdbc.queryForList("""
				SELECT CONCAT(TABLE_NAME, '.', COLUMN_NAME) FROM information_schema.KEY_COLUMN_USAGE
				WHERE TABLE_SCHEMA = DATABASE() AND REFERENCED_TABLE_SCHEMA = DATABASE()
				  AND REFERENCED_TABLE_NAME = 'usuario'""", String.class));
		Set<String> clasificadas = new TreeSet<>(PROPIAS);
		clasificadas.addAll(CONSERVAN);
		assertEquals(clasificadas, enLaBase);

		String consulta = UsuarioRepository.class.getMethod("tieneDatosFueraDeLaInvitacion", Long.class, Long.class)
				.getAnnotation(Query.class).value();
		for (String referencia : CONSERVAN) {
			String[] partes = referencia.split("\\.");
			assertTrue(consulta.contains("FROM " + partes[0] + " x WHERE x." + partes[1]), "falta " + referencia);
		}
		assertTrue(consulta.contains("FROM asignacion_profesor x WHERE x.usuario_id"), "asignaciones de otra escuela");
		assertTrue(consulta.contains("u.alumno_id IS NOT NULL"), "vínculo legado con el alumno");
		assertTrue(consulta.contains("u.es_admin_plataforma"), "marca global de plataforma");
	}

	/** Lo único que cuelga de una membresía ({@code membresia_sede}) se borra con ella, y es de esta escuela. */
	@Test
	void soloMembresiaSedeReferenciaAMembresia() {
		assertEquals(List.of("membresia_sede.membresia_id"), jdbc.queryForList("""
				SELECT CONCAT(TABLE_NAME, '.', COLUMN_NAME) FROM information_schema.KEY_COLUMN_USAGE
				WHERE TABLE_SCHEMA = DATABASE() AND REFERENCED_TABLE_SCHEMA = DATABASE()
				  AND REFERENCED_TABLE_NAME = 'membresia'""", String.class));
	}

	// ------------------------------------------------------------------ la cuenta se borra

	@Test
	void cuentaSoloDeEstaEscuela_seBorraYElEnlaceDejaDeValer() throws Exception {
		String correo = emailNuevo("sola");
		long id = invitar(correo, "PROFESOR");
		String token = ultimoEnviadoA(correo).token();
		long antes = ultimoEvento();

		JsonNode respuesta = cancelar(id, tPropietario, 200);

		assertEquals(Set.of("cuentaBorrada"), campos(respuesta));
		assertTrue(respuesta.get("cuentaBorrada").asBoolean());
		assertEquals(0L, filas("usuario", "id", id));
		assertEquals(0L, filas("membresia", "usuario_id", id));
		assertMensaje(aceptar(token), 400, ENLACE_NO_VALE);

		List<Correo> enviados = enviadosA(correo);
		assertEquals(2, enviados.size(), "la invitación y la cancelación");
		Correo aviso = enviados.get(1);
		assertEquals("Invitación cancelada · " + nombreEscuela, aviso.asunto());
		assertEquals(nombreEscuela, aviso.remitente());
		assertTrue(aviso.html().contains("Hola, Ana."), aviso.html());
		assertTrue(aviso.html().contains(nombreEscuela + " ha cancelado tu invitación. El enlace que recibiste ya no "
				+ "sirve para entrar en " + nombreEscuela + "."), aviso.html());

		List<Map<String, Object>> eventos = eventosDesde(antes, e.org);
		assertEquals(List.of("MIEMBROS INVITACION_CANCELADA usuario=#" + id + " cuentaBorrada=SI"), resumenes(eventos),
				"una sola fila: el filtro no la duplica");
		assertEquals("DELETE", eventos.get(0).get("accion"));
		assertEquals(id, numero(eventos.get(0), "recurso_id"));
		String fila = eventos.get(0).toString();
		assertFalse(fila.contains(correo), fila);
		assertFalse(fila.contains(token), fila);
		assertFalse(fila.contains(sha256(token)), fila);
	}

	@Test
	void conAsignacionesSedesYRolYaQuitado_seBorranLasFilasDeEstaEscuela() throws Exception {
		String correo = emailNuevo("filas");
		long id = invitar(correo, "RESPONSABLE_SEDE", e.sA, e.sB);
		insertarMembresia(e.org, id, "PROFESOR", false);
		jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) VALUES (?, ?, ?, 'TITULAR')",
				e.org, id, e.gA);
		long responsable = membresiaDe(id, e.org, "RESPONSABLE_SEDE");
		assertEquals(2L, filas("membresia_sede", "membresia_id", responsable));

		assertTrue(cancelar(id, tPropietario, 200).get("cuentaBorrada").asBoolean());

		assertEquals(0L, filas("usuario", "id", id));
		assertEquals(0L, filas("membresia", "usuario_id", id));
		assertEquals(0L, filas("membresia_sede", "membresia_id", responsable));
		assertEquals(0L, filas("asignacion_profesor", "usuario_id", id));
		assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM grupo WHERE id = ?", Long.class, e.gA));
	}

	@Test
	void familiaActivaSinAlumnos_seRetiraYLaCuentaSeBorra() throws Exception {
		String correo = emailNuevo("familia-sin");
		long id = invitar(correo, "PROFESOR");
		membresia(e.org, id, "FAMILIA");

		assertTrue(cancelar(id, tPropietario, 200).get("cuentaBorrada").asBoolean());

		assertEquals(0L, filas("usuario", "id", id));
	}

	// ------------------------------------------------------------------ otra escuela

	@Test
	void conInvitacionVivaDeOtraYVigenteDeEsta_elEnlaceMuereYSaleLaDeLaOtra() throws Exception {
		String correo = emailNuevo("vigente-esta");
		long id = invitarDesdeOtra(correo, "RESPONSABLE_SEDE", otra.sA);
		anadirYReenviar(id, correo, tPropietario, "PROFESOR");
		assertEquals(e.org, ultimaEscuela(id));
		String tokenEsta = ultimoEnviadoA(correo).token();
		int correosAntes = enviadosA(correo).size();

		assertFalse(cancelar(id, tPropietario, 200).get("cuentaBorrada").asBoolean());

		assertEquals(1L, filas("usuario", "id", id));
		assertEquals(otra.org, ultimaEscuela(id));
		assertEquals(List.of(), rolesActivos(id, e.org));
		assertEquals(List.of("RESPONSABLE_SEDE"), rolesActivos(id, otra.org));
		assertEquals(1L, filas("membresia_sede", "membresia_id", membresiaDe(id, otra.org, "RESPONSABLE_SEDE")));
		assertMensaje(aceptar(tokenEsta), 400, ENLACE_NO_VALE);

		List<Correo> enviados = enviadosA(correo);
		assertEquals(correosAntes + 2, enviados.size(), "dos correos: uno de cada");
		List<Correo> nuevos = enviados.subList(correosAntes, enviados.size());
		assertEquals(1, nuevos.stream().filter(Correo::esCancelacion).count(), "dos correos: uno de cada");
		assertEquals("Invitación cancelada · " + nombreEscuela, nuevos.get(0).asunto());
		assertEquals(nombreEscuela, nuevos.get(0).remitente(), "el aviso sale con el nombre de la escuela que cancela");
		Correo invitacion = nuevos.get(1);
		assertEquals("Tu acceso a " + nombreOtra, invitacion.asunto());
		assertEquals(nombreOtra, invitacion.remitente(), "la invitación nueva, con el de la otra");
		assertTrue(invitacion.html().contains("responsable de sede en " + nombreOtra), invitacion.html());
		assertNotEquals(tokenEsta, invitacion.token());
		assertEquals(sha256(invitacion.token()), hashDe(id));
		assertEquals(200, aceptar(invitacion.token()).getResponse().getStatus());
		assertFalse(pendiente(id));
	}

	@Test
	void conVariasEscuelasActivas_laInvitacionVaALaDeMenorId() throws Exception {
		String correo = emailNuevo("menor-id");
		long id = invitarDesdeOtra(correo, "PROFESOR");
		long tercera = escuelaSinNada("tercera");
		assertTrue(e.org < tercera, "la tercera se crea después");
		membresia(tercera, id, "PROFESOR");
		membresia(e.org, id, "RESPONSABLE_SEDE");

		assertFalse(cancelar(id, tPropietarioOtra, 200).get("cuentaBorrada").asBoolean());

		assertEquals(e.org, ultimaEscuela(id));
		assertTrue(ultimoEnviadoA(correo).html().contains("responsable de sede en " + nombreEscuela));
		assertEquals(List.of(), rolesActivos(id, otra.org));
		assertEquals(List.of("PROFESOR"), rolesActivos(id, tercera));
	}

	@Test
	void conInvitacionVigenteDeLaOtra_suEnlaceSigueValiendoYSaleUnCorreo() throws Exception {
		String correo = emailNuevo("vigente-otra");
		long id = invitar(correo, "PROFESOR");
		anadirYReenviar(id, correo, tPropietarioOtra, "PROFESOR");
		assertEquals(otra.org, ultimaEscuela(id));
		String tokenOtra = ultimoEnviadoA(correo).token();
		String hash = hashDe(id);
		Object caduca = caducidadDe(id);
		int correosAntes = enviadosA(correo).size();

		assertFalse(cancelar(id, tPropietario, 200).get("cuentaBorrada").asBoolean());

		assertEquals(hash, hashDe(id), "el token de la otra escuela no se toca");
		assertEquals(caduca, caducidadDe(id));
		assertEquals(otra.org, ultimaEscuela(id));
		assertEquals(List.of(), rolesActivos(id, e.org));
		assertEquals(List.of("PROFESOR"), rolesActivos(id, otra.org));
		List<Correo> enviados = enviadosA(correo);
		assertEquals(correosAntes + 1, enviados.size());
		assertTrue(enviados.get(enviados.size() - 1).esCancelacion());
		assertEquals(200, aceptar(tokenOtra).getResponse().getStatus());
		assertFalse(pendiente(id));
	}

	@Test
	void conMembresiaInactivaEnOtraEscuela_seConservaSinToken() throws Exception {
		String correo = emailNuevo("inactiva-otra");
		long id = invitarDesdeOtra(correo, "PROFESOR");
		anadirYReenviar(id, correo, tPropietario, "PROFESOR");
		quitarRol(id, "PROFESOR", tPropietarioOtra);
		String token = ultimoEnviadoA(correo).token();

		assertFalse(cancelar(id, tPropietario, 200).get("cuentaBorrada").asBoolean());

		assertEquals(1L, filas("usuario", "id", id));
		assertNull(hashDe(id));
		assertNull(caducidadDe(id));
		assertEquals(List.of(), rolesActivos(id, e.org));
		assertEquals(1L, filas("membresia", "usuario_id", id, "organizacion_id", otra.org), "la inactiva sigue");
		assertMensaje(aceptar(token), 400, ENLACE_NO_VALE);
	}

	@Test
	void invitacionVigenteDeOtraConRolYaQuitado_noQuedaTokenVivo() throws Exception {
		String correo = emailNuevo("inactiva-vigente");
		long id = invitar(correo, "PROFESOR");
		anadirYReenviar(id, correo, tPropietarioOtra, "PROFESOR");
		quitarRol(id, "PROFESOR", tPropietarioOtra);
		String token = ultimoEnviadoA(correo).token();

		assertFalse(cancelar(id, tPropietario, 200).get("cuentaBorrada").asBoolean());

		assertNull(hashDe(id));
		assertMensaje(aceptar(token), 400, ENLACE_NO_VALE);
	}

	/** Una membresía activa en una escuela desactivada no da acceso: no se invita a ella, pero la cuenta se conserva. */
	@Test
	void membresiaActivaEnEscuelaDesactivada_seConservaSinToken() throws Exception {
		String correo = emailNuevo("escuela-baja");
		long id = invitar(correo, "PROFESOR");
		long baja = escuelaSinNada("baja");
		jdbc.update("UPDATE organizacion SET activa = FALSE WHERE id = ?", baja);
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROFESOR')", id, baja);

		assertFalse(cancelar(id, tPropietario, 200).get("cuentaBorrada").asBoolean());

		assertEquals(1L, filas("usuario", "id", id));
		assertNull(hashDe(id));
		assertEquals(e.org, ultimaEscuela(id));
		assertEquals(List.of("PROFESOR"), rolesActivos(id, baja));
	}

	// ------------------------------------------------------------------ otros datos

	@Test
	void conVinculoConAlumno_seConservaYNadaDeAlumnosCambia() throws Exception {
		String correo = emailNuevo("vinculo");
		long id = invitar(correo, "PROFESOR");
		jdbc.update("""
				INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen)
				VALUES (?, ?, ?, 'TUTOR', 'MANUAL')""", id, e.aA, e.org);
		String alumno = alumnoEnTexto(e.aA);
		String token = ultimoEnviadoA(correo).token();

		assertFalse(cancelar(id, tPropietario, 200).get("cuentaBorrada").asBoolean());

		assertEquals(1L, filas("usuario", "id", id));
		assertNull(hashDe(id));
		assertEquals(1L, filas("usuario_alumno", "usuario_id", id));
		assertEquals(alumno, alumnoEnTexto(e.aA));
		assertEquals(List.of(), rolesActivos(id, e.org));
		assertMensaje(aceptar(token), 400, ENLACE_NO_VALE);
	}

	@Test
	void vinculoEnOtraEscuela_conservaLaCuenta() throws Exception {
		String correo = emailNuevo("vinculo-otra");
		long id = invitar(correo, "PROFESOR");
		jdbc.update("""
				INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen)
				VALUES (?, ?, ?, 'TUTOR', 'MANUAL')""", id, otra.aA, otra.org);

		assertFalse(cancelar(id, tPropietario, 200).get("cuentaBorrada").asBoolean());

		assertEquals(1L, filas("usuario", "id", id));
		assertEquals(1L, filas("usuario_alumno", "usuario_id", id));
	}

	@Test
	void familiaActivaConAlumnos_noSeTocaYLaCuentaSeConserva() throws Exception {
		String correo = emailNuevo("familia-con");
		long id = invitar(correo, "PROFESOR");
		membresia(e.org, id, "FAMILIA");
		jdbc.update("""
				INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen)
				VALUES (?, ?, ?, 'TUTOR', 'MANUAL')""", id, e.aB, e.org);
		String token = ultimoEnviadoA(correo).token();

		assertFalse(cancelar(id, tPropietario, 200).get("cuentaBorrada").asBoolean());

		assertEquals(List.of("FAMILIA"), rolesActivos(id, e.org), "sale el resto de roles; FAMILIA sigue");
		assertEquals(1L, filas("usuario_alumno", "usuario_id", id));
		assertNull(hashDe(id));
		assertMensaje(aceptar(token), 400, ENLACE_NO_VALE);
	}

	@Test
	void alumnoLegado_conservaLaCuenta() throws Exception {
		String correo = emailNuevo("legado");
		long id = invitar(correo, "PROFESOR");
		jdbc.update("UPDATE usuario SET alumno_id = ? WHERE id = ?", e.aB, id);

		assertFalse(cancelar(id, tPropietario, 200).get("cuentaBorrada").asBoolean());

		assertEquals(e.aB, jdbc.queryForObject("SELECT alumno_id FROM usuario WHERE id = ?", Long.class, id));
	}

	@Test
	void adminDePlataforma_conservaLaCuenta() throws Exception {
		long id = invitar(emailNuevo("plataforma"), "PROFESOR");
		jdbc.update("UPDATE usuario SET es_admin_plataforma = TRUE WHERE id = ?", id);

		assertFalse(cancelar(id, tPropietario, 200).get("cuentaBorrada").asBoolean());

		assertEquals(1L, filas("usuario", "id", id));
	}

	@Test
	void usuarioRolLegado_conservaLaCuenta() throws Exception {
		long id = invitar(emailNuevo("usuario-rol"), "PROFESOR");
		jdbc.update("INSERT INTO usuario_rol (usuario_id, roles) VALUES (?, 'ROLE_USER')", id);

		assertFalse(cancelar(id, tPropietario, 200).get("cuentaBorrada").asBoolean());

		assertEquals(1L, filas("usuario_rol", "usuario_id", id));
	}

	@Test
	void asignacionEnOtraEscuela_conservaLaCuenta() throws Exception {
		long id = invitar(emailNuevo("asignacion-otra"), "PROFESOR");
		jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) VALUES (?, ?, ?, 'TITULAR')",
				otra.org, id, otra.gA);

		assertFalse(cancelar(id, tPropietario, 200).get("cuentaBorrada").asBoolean());

		assertEquals(1L, filas("asignacion_profesor", "usuario_id", id));
	}

	/**
	 * Dinero, caja y autoría: {@code ON DELETE SET NULL}, así que borrar no daría error y perdería el autor. Cada fila,
	 * por separado, conserva la cuenta y sigue con su autor.
	 */
	@Test
	void autorDeDineroCajaOAsistencia_conservaLaCuentaYElAutor() throws Exception {
		Map<String, Referencia> casos = referenciasDeAutoria(e);
		for (Map.Entry<String, Referencia> caso : casos.entrySet()) {
			String[] partes = caso.getKey().split("\\.");
			long id = invitar(emailNuevo(partes[1].substring(0, 8)), "PROFESOR");
			caso.getValue().crear(id);

			JsonNode respuesta = cancelar(id, tPropietario, 200);

			assertFalse(respuesta.get("cuentaBorrada").asBoolean(), caso.getKey());
			assertEquals(1L, filas("usuario", "id", id), caso.getKey());
			assertEquals(1L, filas(partes[0], partes[1], id), caso.getKey());
		}
		assertEquals(CONSERVAN.stream().filter(r -> !r.startsWith("usuario_")).count(), (long) casos.size(),
				"una por cada columna de dinero, caja o autoría");
	}

	/**
	 * Lo mismo con el dato en OTRA escuela, donde la cuenta no tiene ninguna membresía: la comprobación mira todas las
	 * escuelas, y la fila ajena sigue idéntica.
	 */
	@Test
	void autorDeDineroCajaOAsistenciaEnOtraEscuela_conservaLaCuentaYElAutor() throws Exception {
		Map<String, Referencia> casos = referenciasDeAutoria(otra);
		for (Map.Entry<String, Referencia> caso : casos.entrySet()) {
			String[] partes = caso.getKey().split("\\.");
			long id = invitar(emailNuevo("otra-" + partes[1].substring(0, 8)), "PROFESOR");
			caso.getValue().crear(id);
			assertEquals(1L, filas(partes[0], partes[1], id, "organizacion_id", otra.org), caso.getKey());
			String fila = filaEnTexto(partes[0], partes[1], id);

			JsonNode respuesta = cancelar(id, tPropietario, 200);

			assertFalse(respuesta.get("cuentaBorrada").asBoolean(), caso.getKey());
			assertEquals(1L, filas("usuario", "id", id), caso.getKey());
			assertEquals(fila, filaEnTexto(partes[0], partes[1], id), caso.getKey());
			assertEquals(List.of(), rolesActivos(id, e.org), caso.getKey());
		}
		assertEquals(CONSERVAN.stream().filter(r -> !r.startsWith("usuario_")).count(), (long) casos.size(),
				"una por cada columna de dinero, caja o autoría");
	}

	/** Una inserción por cada columna de dinero, caja o autoría, con los datos de {@code escuela}. */
	private Map<String, Referencia> referenciasDeAutoria(EscuelaConSedesDePrueba escuela) {
		Map<String, Referencia> casos = new LinkedHashMap<>();
		for (String columna : List.of("cobrado_por_usuario_id", "anulado_por_usuario_id")) {
			casos.put("cobro." + columna, id -> {
				jdbc.update("INSERT INTO cobro (organizacion_id, importe, fecha_cobro, origen, " + columna
						+ ") VALUES (?, 10.00, NOW(6), 'TESORERIA', ?)", escuela.org, id);
			});
		}
		for (String columna : List.of("entregado_por_usuario_id", "recibido_por_usuario_id",
				"anulada_por_usuario_id")) {
			casos.put("entrega_caja." + columna, id -> {
				jdbc.update("INSERT INTO entrega_caja (organizacion_id, sede_id, importe_declarado, importe_contado, "
						+ "fecha, " + columna + ") VALUES (?, ?, 10.00, 10.00, NOW(6), ?)", escuela.org, escuela.sA, id);
			});
		}
		for (String columna : List.of("creado_por_usuario_id", "anulado_por_usuario_id")) {
			casos.put("movimiento_caja." + columna, id -> {
				jdbc.update("INSERT INTO movimiento_caja (organizacion_id, sede_id, tipo, importe, nota, fecha, " + columna
						+ ") VALUES (?, ?, 'OTRO', 1.00, 'IT 389', NOW(6), ?)", escuela.org, escuela.sA, id);
			});
		}
		for (String columna : List.of("cancelado_por_usuario_id", "vendido_por_usuario_id")) {
			casos.put("producto_alumno." + columna, id -> {
				jdbc.update("INSERT INTO producto_alumno (alumno_id, concepto, precio, cantidad, pagado, fecha_asignacion, "
						+ "organizacion_id, sede_id, " + columna + ") VALUES (?, 'IT 389', 1.0, 1, 0, NOW(), ?, ?, ?)",
						escuela.aA, escuela.org, escuela.sA, id);
			});
		}
		for (String columna : List.of("solicitado_por_usuario_id", "resuelto_por_usuario_id",
				"encargado_por_usuario_id", "recibido_por_usuario_id")) {
			casos.put("solicitud." + columna, id -> {
				jdbc.update("INSERT INTO solicitud (organizacion_id, tipo, estado, solicitado_en, motivo, " + columna
						+ ") VALUES (?, 'ENCARGO', 'RETIRADA', NOW(6), 'IT 389', ?)", escuela.org, id);
			});
		}
		for (String columna : List.of("registrado_por_usuario_id", "modificado_por_usuario_id")) {
			casos.put("clase_asistencia." + columna, id -> {
				jdbc.update("INSERT INTO clase_asistencia (organizacion_id, grupo_id, fecha, hora_inicio, hora_fin, "
						+ "registrado_en, " + columna + ") VALUES (?, ?, '2026-09-07', '18:00', '19:00', NOW(6), ?)",
						escuela.org, escuela.gA, id);
			});
		}
		return casos;
	}

	@FunctionalInterface
	interface Referencia {
		void crear(long usuario) throws Exception;
	}

	// ------------------------------------------------------------------ rechazos: nada cambia

	@Test
	void cuentaYaAceptada_409YNadaCambia() throws Exception {
		String correo = emailNuevo("aceptada");
		long id = invitar(correo, "PROFESOR");
		assertEquals(200, aceptar(ultimoEnviadoA(correo).token()).getResponse().getStatus());
		int correosAntes = enviadosA(correo).size();
		long antes = ultimoEvento();
		String contrasena = contrasenaDe(id);

		assertMensaje(peticion(delete(ruta(id)), tPropietario), 409, YA_ACEPTADA);

		assertEquals(List.of("PROFESOR"), rolesActivos(id, e.org));
		assertEquals(contrasena, contrasenaDe(id));
		assertEquals(correosAntes, enviadosA(correo).size());
		assertTrue(resumenes(eventosDesde(antes, e.org)).stream().noneMatch(r -> r.contains("INVITACION_CANCELADA")));
	}

	@Test
	void sinRolActivo_409YNadaCambia() throws Exception {
		String correo = emailNuevo("sin-rol");
		long id = invitar(correo, "PROFESOR");
		quitarRol(id, "PROFESOR", tPropietario);
		String hash = hashDe(id);
		int correosAntes = enviadosA(correo).size();

		assertMensaje(peticion(delete(ruta(id)), tPropietario), 409, SIN_ROL_ACTIVO);

		assertEquals(hash, hashDe(id));
		assertEquals(1L, filas("usuario", "id", id));
		assertEquals(correosAntes, enviadosA(correo).size());
	}

	@Test
	void gestorSobrePropietarioOGestor_403YNadaCambia() throws Exception {
		String correo = emailNuevo("reservado");
		long id = invitar(correo, "PROPIETARIO");
		long solo = invitar(emailNuevo("reservado-gestor"), "ADMINISTRADOR");
		long junto = invitar(emailNuevo("reservado-junto"), "ADMINISTRADOR");
		membresia(e.org, junto, "PROFESOR");
		String hash = hashDe(id);

		for (long cuenta : List.of(id, solo, junto)) {
			assertMensaje(peticion(delete(ruta(cuenta)), tGestor), 403, SOLO_PROPIETARIO);
			assertEquals(1L, filas("usuario", "id", cuenta));
		}
		assertEquals(List.of("PROPIETARIO"), rolesActivos(id, e.org));
		assertEquals(List.of("ADMINISTRADOR", "PROFESOR"), rolesActivos(junto, e.org));
		assertEquals(hash, hashDe(id));
		assertEquals(1, enviadosA(correo).size(), "solo la invitación");
	}

	@Test
	void gestorSobreProfesor_200() throws Exception {
		long id = invitar(emailNuevo("gestor-profesor"), "PROFESOR");

		assertTrue(cancelar(id, tGestor, 200).get("cuentaBorrada").asBoolean());

		assertEquals(0L, filas("usuario", "id", id));
	}

	@Test
	void propietarioSobrePropietarioPendienteConOtroPropietario_200() throws Exception {
		long id = invitar(emailNuevo("otro-propietario"), "PROPIETARIO");

		assertTrue(cancelar(id, tPropietario, 200).get("cuentaBorrada").asBoolean());
	}

	@Test
	void ultimoPropietarioPendiente_409YNadaCambia() {
		long sola = escuelaSinNada("sola");
		long id = cuentaPendiente(emailNuevo("ultimo"), sola);
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROPIETARIO')", id, sola);
		String hash = hashDe(id);

		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> comoPropietario(sola, () -> miembros.cancelarInvitacion(id)));

		assertEquals(SIN_PROPIETARIO, error.getMessage());
		assertEquals(List.of("PROPIETARIO"), rolesActivos(id, sola));
		assertEquals(hash, hashDe(id));
		verify(email, never()).sendEmailConImagen(any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	void escuelaAjena_404YNadaCambia() throws Exception {
		String correo = emailNuevo("ajena");
		long id = invitar(correo, "PROFESOR");
		String hash = hashDe(id);

		assertMensaje(peticion(delete(ruta(id)), tPropietarioOtra), 404, NO_MIEMBRO);

		assertEquals(hash, hashDe(id));
		assertEquals(List.of("PROFESOR"), rolesActivos(id, e.org));
	}

	@Test
	void responsableProfesorFamiliaYAnonimo_denegados() throws Exception {
		String correo = emailNuevo("denegados");
		long id = invitar(correo, "PROFESOR");
		String hash = hashDe(id);

		for (String token : List.of(tResponsable, tProfesor, tFamilia)) {
			assertEquals(403, peticion(delete(ruta(id)), token).getResponse().getStatus());
		}
		int anonimo = mvc.perform(delete(ruta(id))).andReturn().getResponse().getStatus();
		assertTrue(anonimo == 401 || anonimo == 403, "anónimo: " + anonimo);

		assertEquals(hash, hashDe(id));
		assertEquals(List.of("PROFESOR"), rolesActivos(id, e.org));
	}

	/** Decisión 371: la cuenta doble responsable + profesor suma su caja y sus grupos, no la gestión de miembros. */
	@Test
	void sesionDeResponsableYProfesor_denegada() throws Exception {
		String correo = emailNuevo("doble");
		long id = invitar(correo, "PROFESOR");
		String hash = hashDe(id);
		int correosAntes = enviadosA(correo).size();

		assertEquals(403, peticion(delete(ruta(id)), tResponsableProfesor).getResponse().getStatus());

		assertEquals(hash, hashDe(id));
		assertEquals(List.of("PROFESOR"), rolesActivos(id, e.org));
		assertEquals(correosAntes, enviadosA(correo).size());
	}

	// ------------------------------------------------------------------ nada que cancelar

	/** FAMILIA con alumnos no se retira: sin token vivo, repetir no tendría ningún efecto. */
	@Test
	void familiaConAlumnosYaCancelada_409SinCorreoNiAuditoria() throws Exception {
		String correo = emailNuevo("familia-repite");
		long id = invitar(correo, "PROFESOR");
		membresia(e.org, id, "FAMILIA");
		vincularAlumno(id, e.aB);
		assertFalse(cancelar(id, tPropietario, 200).get("cuentaBorrada").asBoolean());
		int correosAntes = enviadosA(correo).size();
		long antes = ultimoEvento();

		assertMensaje(peticion(delete(ruta(id)), tPropietario), 409, SIN_ROL_ACTIVO);

		assertEquals(correosAntes, enviadosA(correo).size());
		assertSinCancelacionRegistrada(antes);
		assertEquals(List.of("FAMILIA"), rolesActivos(id, e.org));
		assertEquals(1L, filas("usuario_alumno", "usuario_id", id));
	}

	/** Solo FAMILIA con alumnos: con el enlace vivo, la primera cancelación lo retira y avisa, como siempre. */
	@Test
	void soloFamiliaConAlumnosYTokenVivo_laPrimeraCancelacionSigueIgual() throws Exception {
		String correo = emailNuevo("familia-primera");
		long id = cuentaPendiente(correo, e.org);
		membresia(e.org, id, "FAMILIA");
		vincularAlumno(id, e.aB);
		long antes = ultimoEvento();

		assertFalse(cancelar(id, tPropietario, 200).get("cuentaBorrada").asBoolean());

		assertNull(hashDe(id));
		assertEquals(List.of("FAMILIA"), rolesActivos(id, e.org));
		assertTrue(ultimoEnviadoA(correo).esCancelacion());
		assertEquals(List.of("MIEMBROS INVITACION_CANCELADA usuario=#" + id + " cuentaBorrada=NO"),
				resumenes(eventosDesde(antes, e.org)));

		assertMensaje(peticion(delete(ruta(id)), tPropietario), 409, SIN_ROL_ACTIVO);
		assertEquals(1, enviadosA(correo).size(), "solo el primer aviso");
	}

	/** Un token caducado ya no sirve: tampoco hay nada que cancelar. */
	@Test
	void familiaConAlumnosYTokenCaducado_409SinCorreo() throws Exception {
		String correo = emailNuevo("familia-caducada");
		long id = cuentaPendiente(correo, e.org);
		membresia(e.org, id, "FAMILIA");
		vincularAlumno(id, e.aB);
		jdbc.update("UPDATE usuario SET reset_token_expires_at = NOW() - INTERVAL 1 DAY WHERE id = ?", id);
		long antes = ultimoEvento();

		assertMensaje(peticion(delete(ruta(id)), tPropietario), 409, SIN_ROL_ACTIVO);

		assertEquals(0, enviadosA(correo).size());
		assertSinCancelacionRegistrada(antes);
	}

	// ------------------------------------------------------------------ quitar el último rol

	@Test
	void quitarElUltimoRol_elEnlaceMuereYLaCuentaSigue() throws Exception {
		String correo = emailNuevo("quita-ultimo");
		long id = invitar(correo, "PROFESOR");
		String token = ultimoEnviadoA(correo).token();
		int correosAntes = enviadosA(correo).size();
		long antes = ultimoEvento();

		quitarRol(id, "PROFESOR", tPropietario);

		assertNull(hashDe(id));
		assertNull(caducidadDe(id));
		assertEquals(1L, filas("usuario", "id", id));
		assertTrue(pendiente(id));
		assertEquals(e.org, ultimaEscuela(id));
		assertEquals(correosAntes, enviadosA(correo).size(), "sin correo de cancelación");
		assertEquals(List.of("MIEMBROS ROL_QUITADO usuario=#" + id + " rol=PROFESOR"),
				resumenes(eventosDesde(antes, e.org)));
		assertMensaje(aceptar(token), 400, ENLACE_NO_VALE);
	}

	@Test
	void quitarElUltimoPropietarioDeLaCuenta_elEnlaceMuere() throws Exception {
		String correo = emailNuevo("quita-propietario");
		long id = invitar(correo, "PROPIETARIO");
		String token = ultimoEnviadoA(correo).token();

		quitarRol(id, "PROPIETARIO", tPropietario);

		assertNull(hashDe(id));
		assertMensaje(aceptar(token), 400, ENLACE_NO_VALE);
	}

	@Test
	void quitarUnoDeDosRoles_elEnlaceSigueValiendo() throws Exception {
		String correo = emailNuevo("quita-uno");
		long id = invitar(correo, "PROFESOR");
		membresia(e.org, id, "RESPONSABLE_SEDE");
		String token = ultimoEnviadoA(correo).token();
		String hash = hashDe(id);
		int correosAntes = enviadosA(correo).size();

		quitarRol(id, "PROFESOR", tPropietario);

		assertEquals(hash, hashDe(id));
		assertEquals(correosAntes, enviadosA(correo).size());
		assertEquals(200, aceptar(token).getResponse().getStatus());
		assertFalse(pendiente(id));
	}

	@Test
	void quitarElUltimoRolDeAqui_conInvitacionVigenteDeOtra_noLaToca() throws Exception {
		String correo = emailNuevo("quita-vigente-otra");
		long id = invitar(correo, "PROFESOR");
		anadirYReenviar(id, correo, tPropietarioOtra, "PROFESOR");
		String tokenOtra = ultimoEnviadoA(correo).token();
		String hash = hashDe(id);
		Object caduca = caducidadDe(id);
		int correosAntes = enviadosA(correo).size();

		quitarRol(id, "PROFESOR", tPropietario);

		assertEquals(hash, hashDe(id));
		assertEquals(caduca, caducidadDe(id));
		assertEquals(otra.org, ultimaEscuela(id));
		assertEquals(correosAntes, enviadosA(correo).size());
		assertEquals(200, aceptar(tokenOtra).getResponse().getStatus());
		assertFalse(pendiente(id));
	}

	@Test
	void quitarElUltimoRolDeAqui_conRolFueraEInvitacionDeEsta_seRenuevaParaLaOtra() throws Exception {
		String correo = emailNuevo("quita-renueva");
		long id = invitarDesdeOtra(correo, "RESPONSABLE_SEDE", otra.sA);
		anadirYReenviar(id, correo, tPropietario, "PROFESOR");
		String tokenEsta = ultimoEnviadoA(correo).token();
		int correosAntes = enviadosA(correo).size();

		quitarRol(id, "PROFESOR", tPropietario);

		assertEquals(otra.org, ultimaEscuela(id));
		assertMensaje(aceptar(tokenEsta), 400, ENLACE_NO_VALE);
		List<Correo> enviados = enviadosA(correo);
		assertEquals(correosAntes + 1, enviados.size(), "solo la invitación de la otra, sin aviso de cancelación");
		Correo invitacion = enviados.get(enviados.size() - 1);
		assertEquals("Tu acceso a " + nombreOtra, invitacion.asunto());
		assertEquals(nombreOtra, invitacion.remitente());
		assertEquals(sha256(invitacion.token()), hashDe(id));
		assertEquals(200, aceptar(invitacion.token()).getResponse().getStatus());
		assertFalse(pendiente(id));
	}

	@Test
	void devolverElRolYReenviar_daUnEnlaceNuevoQueFunciona() throws Exception {
		String correo = emailNuevo("quita-devuelve");
		long id = invitar(correo, "PROFESOR");
		String viejo = ultimoEnviadoA(correo).token();
		quitarRol(id, "PROFESOR", tPropietario);
		assertEquals(200, peticion(put(RUTA + "/" + id + "/roles/PROFESOR"), tPropietario).getResponse().getStatus());
		assertNull(hashDe(id), "devolver el rol no da enlace: lo da reenviar");

		assertEquals(200, peticion(post(ruta(id)), tPropietario).getResponse().getStatus());

		String nuevo = ultimoEnviadoA(correo).token();
		assertNotEquals(viejo, nuevo);
		assertMensaje(aceptar(viejo), 400, ENLACE_NO_VALE);
		assertEquals(200, aceptar(nuevo).getResponse().getStatus());
		assertFalse(pendiente(id));
		assertEquals(List.of("PROFESOR"), rolesActivos(id, e.org));
	}

	/** Una cuenta aceptada conserva su token de restablecer: comparte columnas con el de la invitación. */
	@Test
	void quitarElUltimoRolAUnaCuentaAceptada_nadaMasCambia() throws Exception {
		String correo = emailNuevo("quita-aceptada");
		long id = invitar(correo, "PROFESOR");
		assertEquals(200, aceptar(ultimoEnviadoA(correo).token()).getResponse().getStatus());
		jdbc.update("""
				UPDATE usuario SET reset_token_hash = SHA2(?, 256), reset_token_expires_at = NOW() + INTERVAL 1 HOUR
				WHERE id = ?""", correo, id);
		String hash = hashDe(id);
		Object caduca = caducidadDe(id);
		String contrasena = contrasenaDe(id);
		int correosAntes = enviadosA(correo).size();

		quitarRol(id, "PROFESOR", tPropietario);

		assertEquals(hash, hashDe(id));
		assertEquals(caduca, caducidadDe(id));
		assertEquals(contrasena, contrasenaDe(id));
		assertEquals(e.org, ultimaEscuela(id));
		assertEquals(correosAntes, enviadosA(correo).size());
	}

	@Test
	void quitarUnRolYaInactivo_noTocaElToken() throws Exception {
		String correo = emailNuevo("quita-sin-cambios");
		long id = cuentaPendiente(correo, e.org);
		insertarMembresia(e.org, id, "PROFESOR", false);
		String hash = hashDe(id);
		Object caduca = caducidadDe(id);
		long antes = ultimoEvento();

		quitarRol(id, "PROFESOR", tPropietario);

		assertEquals(hash, hashDe(id));
		assertEquals(caduca, caducidadDe(id));
		assertEquals(List.of("MIEMBROS ROL_QUITADO usuario=#" + id + " rol=PROFESOR sinCambios=SI"),
				resumenes(eventosDesde(antes, e.org)));
	}

	@Test
	void quitarAlUltimoPropietarioPendiente_409YElEnlaceSigue() {
		long sola = escuelaSinNada("sola-quitar");
		long id = cuentaPendiente(emailNuevo("quita-ultimo-propietario"), sola);
		membresia(sola, id, "PROPIETARIO");
		String hash = hashDe(id);

		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> comoPropietario(sola, () -> miembros.quitarRol(id, "PROPIETARIO")));

		assertEquals(SIN_PROPIETARIO, error.getMessage());
		assertEquals(hash, hashDe(id));
		assertEquals(List.of("PROPIETARIO"), rolesActivos(id, sola));
	}

	/** La renovación va en la transacción: si se deshace, ni rol quitado ni enlace nuevo ni correo. */
	@Test
	void quitarElUltimoRolDeAqui_siSeDeshace_nadaCambiaNiSaleCorreo() throws Exception {
		String correo = emailNuevo("quita-deshecha");
		long id = invitarDesdeOtra(correo, "PROFESOR");
		anadirYReenviar(id, correo, tPropietario, "PROFESOR");
		String hash = hashDe(id);
		int correosAntes = enviadosA(correo).size();

		assertThrows(IllegalStateException.class, () -> comoPropietario(e.org, () -> transaccion()
				.executeWithoutResult(estado -> {
					miembros.quitarRol(id, "PROFESOR");
					throw new IllegalStateException("se deshace");
				})));

		assertEquals(hash, hashDe(id));
		assertEquals(e.org, ultimaEscuela(id));
		assertEquals(List.of("PROFESOR"), rolesActivos(id, e.org));
		assertEquals(correosAntes, enviadosA(correo).size());
	}

	/**
	 * Gana aceptar y la sesión aún tiene la cuenta pendiente: solo la relectura evita renovar una invitación ya aceptada.
	 * El rol se quita igual: la cuenta ya no está pendiente y nada más cambia.
	 */
	@Test
	void aceptarGanaAQuitarRol_conLaEntidadAntigua_noRenuevaNiFalla() throws Exception {
		String correo = emailNuevo("carrera-quitar");
		long id = invitarDesdeOtra(correo, "PROFESOR");
		anadirYReenviar(id, correo, tPropietario, "PROFESOR");
		String token = ultimoEnviadoA(correo).token();
		int correosAntes = enviadosA(correo).size();
		ExecutorService hilo = Executors.newSingleThreadExecutor();
		try {
			comoPropietario(e.org, () -> transaccion().executeWithoutResult(estado -> {
				assertTrue(usuarios.findById(id).orElseThrow().invitacionPendiente());
				try {
					assertEquals(200, hilo.submit(() -> aceptar(token)).get(30, TimeUnit.SECONDS).getResponse().getStatus());
				} catch (Exception fallo) {
					fail(fallo);
				}
				miembros.quitarRol(id, "PROFESOR");
			}));
		} finally {
			hilo.shutdownNow();
		}
		assertFalse(pendiente(id));
		assertEquals(List.of(), rolesActivos(id, e.org));
		assertEquals(List.of("PROFESOR"), rolesActivos(id, otra.org));
		assertEquals(e.org, ultimaEscuela(id));
		assertEquals(correosAntes, enviadosA(correo).size());
	}

	// ------------------------------------------------------------------ profesores del grupo

	/** La pastilla del grupo: pendiente, y con algún rol activo aquí; un rol en otra escuela no cuenta. */
	@Test
	void profesorDelGrupo_soloSalePendienteConRolActivoEnEstaEscuela() throws Exception {
		String correo = emailNuevo("grupo");
		long id = invitarDesdeOtra(correo, "PROFESOR");
		anadirYReenviar(id, correo, tPropietario, "PROFESOR");
		jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) VALUES (?, ?, ?, 'TITULAR')",
				e.org, id, e.gA2);
		assertTrue(pendienteEnElGrupo(id, e.gA2));

		assertFalse(cancelar(id, tPropietario, 200).get("cuentaBorrada").asBoolean());

		assertTrue(pendiente(id));
		assertFalse(pendienteEnElGrupo(id, e.gA2));
	}

	// ------------------------------------------------------------------ carrera con aceptar

	/**
	 * Gana cancelar: {@code aceptar} ya leyó la cuenta (sigue pendiente) y su UPDATE espera al bloqueo de la fila. Al
	 * confirmar, la condición se reevalúa con el token borrado: 400.
	 */
	@Test
	void cancelarGanaAAceptar_laAceptacionConcurrenteCaeEn400() throws Exception {
		String correo = emailNuevo("carrera-cancela");
		long id = invitar(correo, "PROFESOR");
		String token = ultimoEnviadoA(correo).token();
		ExecutorService hilo = Executors.newSingleThreadExecutor();
		try {
			List<Future<MvcResult>> aceptacion = new ArrayList<>();
			comoPropietario(e.org, () -> transaccion().executeWithoutResult(estado -> {
				miembros.cancelarInvitacion(id);
				aceptacion.add(hilo.submit(() -> aceptar(token)));
				// Sin confirmar la cancelación, aceptar no puede acabar: espera a la fila.
				assertThrows(TimeoutException.class, () -> aceptacion.get(0).get(1500, TimeUnit.MILLISECONDS));
			}));

			assertMensaje(aceptacion.get(0).get(30, TimeUnit.SECONDS), 400, ENLACE_NO_VALE);
		} finally {
			hilo.shutdownNow();
		}
		assertEquals(0L, filas("usuario", "id", id));
	}

	/** Igual, cuando la cuenta se conserva: ni contraseña ni roles nuevos. */
	@Test
	void cancelarGanaAAceptar_cuentaConservada_tampocoEntra() throws Exception {
		String correo = emailNuevo("carrera-conserva");
		long id = invitar(correo, "PROFESOR");
		jdbc.update("""
				INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen)
				VALUES (?, ?, ?, 'TUTOR', 'MANUAL')""", id, e.aInactivoA, e.org);
		String token = ultimoEnviadoA(correo).token();
		ExecutorService hilo = Executors.newSingleThreadExecutor();
		try {
			List<Future<MvcResult>> aceptacion = new ArrayList<>();
			comoPropietario(e.org, () -> transaccion().executeWithoutResult(estado -> {
				miembros.cancelarInvitacion(id);
				aceptacion.add(hilo.submit(() -> aceptar(token)));
				assertThrows(TimeoutException.class, () -> aceptacion.get(0).get(1500, TimeUnit.MILLISECONDS));
			}));

			assertMensaje(aceptacion.get(0).get(30, TimeUnit.SECONDS), 400, ENLACE_NO_VALE);
		} finally {
			hilo.shutdownNow();
		}
		assertTrue(pendiente(id));
		assertNull(hashDe(id));
	}

	/**
	 * Gana aceptar: la transacción de cancelar ya tiene en sesión la cuenta aún pendiente (como puede dejarla
	 * {@code exigirMiembro} antes del bloqueo). {@code aceptar} confirma y cancelar da 409 sin tocar nada.
	 */
	@Test
	void aceptarGanaACancelar_cancelarDa409ConLaEntidadAntigua() throws Exception {
		String correo = emailNuevo("carrera-acepta");
		long id = invitar(correo, "PROFESOR");
		String token = ultimoEnviadoA(correo).token();
		ExecutorService hilo = Executors.newSingleThreadExecutor();
		try {
			IllegalStateException error = assertThrows(IllegalStateException.class,
					() -> comoPropietario(e.org, () -> transaccion().executeWithoutResult(estado -> {
						assertTrue(usuarios.findById(id).orElseThrow().invitacionPendiente());
						try {
							assertEquals(200, hilo.submit(() -> aceptar(token)).get(30, TimeUnit.SECONDS).getResponse()
									.getStatus());
						} catch (Exception fallo) {
							fail(fallo);
						}
						assertTrue(usuarios.findById(id).orElseThrow().invitacionPendiente(),
								"la sesión sigue con la cuenta antigua");
						miembros.cancelarInvitacion(id);
					})));
			assertEquals(YA_ACEPTADA, error.getMessage());
		} finally {
			hilo.shutdownNow();
		}
		assertFalse(pendiente(id));
		assertEquals(List.of("PROFESOR"), rolesActivos(id, e.org));
		assertEquals(1, enviadosA(correo).size(), "solo la invitación");
	}

	/**
	 * El camino sin escritura del token: la invitación vigente es de la otra escuela y se acepta antes. Solo la comprobación
	 * contra la base evita retirar los roles de una cuenta que ya entró.
	 */
	@Test
	void aceptarGanaACancelar_conInvitacionDeLaOtra_cancelarDa409() throws Exception {
		String correo = emailNuevo("carrera-otra");
		long id = invitar(correo, "PROFESOR");
		anadirYReenviar(id, correo, tPropietarioOtra, "PROFESOR");
		String token = ultimoEnviadoA(correo).token();
		int correosAntes = enviadosA(correo).size();
		ExecutorService hilo = Executors.newSingleThreadExecutor();
		try {
			IllegalStateException error = assertThrows(IllegalStateException.class,
					() -> comoPropietario(e.org, () -> transaccion().executeWithoutResult(estado -> {
						assertTrue(usuarios.findById(id).orElseThrow().invitacionPendiente());
						try {
							assertEquals(200, hilo.submit(() -> aceptar(token)).get(30, TimeUnit.SECONDS).getResponse()
									.getStatus());
						} catch (Exception fallo) {
							fail(fallo);
						}
						miembros.cancelarInvitacion(id);
					})));
			assertEquals(YA_ACEPTADA, error.getMessage());
		} finally {
			hilo.shutdownNow();
		}
		assertFalse(pendiente(id));
		assertEquals(List.of("PROFESOR"), rolesActivos(id, e.org));
		assertEquals(correosAntes, enviadosA(correo).size());
	}

	// ------------------------------------------------------------------ correo

	@Test
	void fallaElCorreo_laCancelacionQuedaConfirmada() throws Exception {
		String correo = emailNuevo("falla");
		long id = invitar(correo, "PROFESOR");
		doThrow(new RuntimeException("smtp " + correo)).when(email).sendEmailConImagen(eq(correo), anyString(),
				anyString(), any(), any(), any(), any());
		Logger logger = (Logger) LoggerFactory.getLogger(InvitacionCorreoListener.class);
		ListAppender<ILoggingEvent> lista = new ListAppender<>();
		lista.start();
		logger.addAppender(lista);
		try {
			assertTrue(cancelar(id, tPropietario, 200).get("cuentaBorrada").asBoolean());
		} finally {
			logger.detachAppender(lista);
			lista.stop();
		}

		assertEquals(0L, filas("usuario", "id", id));
		assertEquals(1, lista.list.size(), lista.list.toString());
		String aviso = lista.list.get(0).getFormattedMessage();
		assertTrue(aviso.contains("#" + id), aviso);
		assertFalse(aviso.contains(correo), "el log no lleva el email: " + aviso);
	}

	@Test
	void elCorreoSoloSaleTrasElCommit_yNuncaSiSeDeshace() throws Exception {
		String deshecha = emailNuevo("deshecha");
		long id = invitar(deshecha, "PROFESOR");
		int correosAntes = enviadosA(deshecha).size();

		assertThrows(IllegalStateException.class, () -> comoPropietario(e.org, () -> transaccion()
				.executeWithoutResult(estado -> {
					miembros.cancelarInvitacion(id);
					assertEquals(correosAntes, enviadosA(deshecha).size(), "dentro de la transacción no sale nada");
					throw new IllegalStateException("se deshace");
				})));

		assertEquals(correosAntes, enviadosA(deshecha).size());
		assertEquals(1L, filas("usuario", "id", id));
		assertEquals(List.of("PROFESOR"), rolesActivos(id, e.org));
	}

	// ------------------------------------------------------------------ ayudas

	private JsonNode cancelar(long id, String token, int esperado) throws Exception {
		MvcResult r = peticion(delete(ruta(id)), token);
		assertEquals(esperado, r.getResponse().getStatus(), contenido(r));
		return json.readTree(contenido(r));
	}

	private static String ruta(long id) {
		return RUTA + "/" + id + "/invitacion";
	}

	private long invitar(String correo, String rol, long... sedes) throws Exception {
		return alta(correo, rol, tPropietario, sedes);
	}

	private long invitarDesdeOtra(String correo, String rol, long... sedes) throws Exception {
		return alta(correo, rol, tPropietarioOtra, sedes);
	}

	private long alta(String correo, String rol, String token, long... sedes) throws Exception {
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("email", correo);
		datos.put("rol", rol);
		datos.put("nombre", "Ana");
		datos.put("apellidos", "Invitada");
		if (sedes.length > 0) {
			List<Long> ids = new ArrayList<>();
			for (long sede : sedes) {
				ids.add(sede);
			}
			datos.put("sedeIds", ids);
		}
		MockHttpServletRequestBuilder peticion = post(RUTA).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(datos));
		MvcResult r = peticion(peticion, token);
		assertEquals(200, r.getResponse().getStatus(), contenido(r));
		return json.readTree(contenido(r)).get("usuarioId").asLong();
	}

	/** La escuela de {@code token} añade la cuenta (ya existe, sin correo) y reenvía: la invitación vigente pasa a ser suya. */
	private void anadirYReenviar(long id, String correo, String token, String rol) throws Exception {
		alta(correo, rol, token);
		assertEquals(200, peticion(post(ruta(id)), token).getResponse().getStatus());
	}

	private void quitarRol(long id, String rol, String token) throws Exception {
		assertEquals(200, peticion(delete(RUTA + "/" + id + "/roles/" + rol), token).getResponse().getStatus());
	}

	private MvcResult aceptar(String token) throws Exception {
		return mvc.perform(post(ACEPTAR).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("token", token, "nuevaContrasena", CONTRASENA)))).andReturn();
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

	/** Transacción como la del servicio: {@code READ_COMMITTED}, para ver lo que confirman los demás. */
	private TransactionTemplate transaccion() {
		TransactionTemplate plantilla = new TransactionTemplate(transacciones);
		plantilla.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
		return plantilla;
	}

	private void comoPropietario(long organizacion, Runnable accion) {
		Usuario actor = TenantContext.calcularCon(EscuelaConSedesDePrueba.ORG1,
				() -> usuarios.findById(propietario).orElseThrow());
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null,
				List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
		try {
			TenantContext.ejecutarCon(organizacion, accion);
		} finally {
			SecurityContextHolder.clearContext();
		}
	}

	private String emailNuevo(String nombre) {
		return "it-389-" + nombre + "-" + contador.incrementAndGet() + "-" + e.marca + "@example.test";
	}

	private long escuelaSinNada(String nombre) {
		String slug = "it-389-" + nombre + "-" + contador.incrementAndGet() + "-" + e.marca;
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES (?, 'Escuela IT 389')", slug);
		return jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = ?", Long.class, slug);
	}

	/** Cuenta pendiente con su invitación vigente en {@code organizacion}, sin pasar por la API. */
	private long cuentaPendiente(String correo, long organizacion) {
		jdbc.update("""
				INSERT INTO usuario (nombre, apellidos, email, auth_provider, reset_token_hash, reset_token_expires_at,
				  ultima_organizacion_id) VALUES ('Ana', 'Invitada', ?, 'LOCAL', SHA2(?, 256), NOW() + INTERVAL 7 DAY, ?)""",
				correo, correo, organizacion);
		return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, correo);
	}

	private void membresia(long organizacion, long usuario, String rol) {
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", usuario, organizacion,
				rol);
	}

	private long insertarMembresia(long organizacion, long usuario, String rol, boolean activa) {
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol, activa) VALUES (?, ?, ?, ?)", usuario,
				organizacion, rol, activa);
		return membresiaDe(usuario, organizacion, rol);
	}

	private long membresiaDe(long usuario, long organizacion, String rol) {
		return jdbc.queryForObject("SELECT id FROM membresia WHERE usuario_id = ? AND organizacion_id = ? AND rol = ?",
				Long.class, usuario, organizacion, rol);
	}

	private List<String> rolesActivos(long usuario, long organizacion) {
		return jdbc.queryForList("""
				SELECT rol FROM membresia WHERE usuario_id = ? AND organizacion_id = ? AND activa = TRUE
				ORDER BY rol""", String.class, usuario, organizacion);
	}

	private long filas(String tabla, String columna, long valor) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla + " WHERE " + columna + " = ?", Long.class, valor);
	}

	private long filas(String tabla, String columna, long valor, String otraColumna, long otroValor) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla + " WHERE " + columna + " = ? AND " + otraColumna
				+ " = ?", Long.class, valor, otroValor);
	}

	private boolean pendiente(long id) {
		return jdbc.queryForObject("""
				SELECT COUNT(*) FROM usuario WHERE id = ? AND contrasena IS NULL
				  AND (auth_provider IS NULL OR auth_provider = 'LOCAL')""", Long.class, id) == 1;
	}

	private String hashDe(long id) {
		return jdbc.queryForObject("SELECT reset_token_hash FROM usuario WHERE id = ?", String.class, id);
	}

	private Object caducidadDe(long id) {
		return jdbc.queryForObject("SELECT reset_token_expires_at FROM usuario WHERE id = ?", Object.class, id);
	}

	private String contrasenaDe(long id) {
		return jdbc.queryForObject("SELECT contrasena FROM usuario WHERE id = ?", String.class, id);
	}

	private long ultimaEscuela(long id) {
		return jdbc.queryForObject("SELECT ultima_organizacion_id FROM usuario WHERE id = ?", Long.class, id);
	}

	private String alumnoEnTexto(long alumno) {
		return jdbc.queryForMap("SELECT * FROM alumno WHERE id = ?", alumno).toString();
	}

	/** Sin la fila de la acción; la del filtro ({@code DELETE miembros #id -> 409}) sale con todo rechazo. */
	private void assertSinCancelacionRegistrada(long antes) {
		assertTrue(resumenes(eventosDesde(antes, e.org)).stream().noneMatch(r -> r.contains("INVITACION_CANCELADA")));
	}

	private String filaEnTexto(String tabla, String columna, long valor) {
		return jdbc.queryForMap("SELECT * FROM " + tabla + " WHERE " + columna + " = ?", valor).toString();
	}

	private void vincularAlumno(long usuario, long alumno) {
		jdbc.update("""
				INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen)
				VALUES (?, ?, ?, 'TUTOR', 'MANUAL')""", usuario, alumno, e.org);
	}

	private boolean pendienteEnElGrupo(long usuario, long grupo) throws Exception {
		MvcResult r = peticion(get("/api/grupos/" + grupo + "/profesores"), tPropietario);
		assertEquals(200, r.getResponse().getStatus(), contenido(r));
		for (JsonNode profesor : json.readTree(contenido(r))) {
			if (profesor.get("usuarioId").asLong() == usuario) {
				return profesor.get("invitacionPendiente").asBoolean();
			}
		}
		return fail("no está en el grupo");
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

	private static long numero(Map<String, Object> fila, String columna) {
		return ((Number) fila.get(columna)).longValue();
	}

	private static Set<String> campos(JsonNode nodo) {
		Set<String> claves = new TreeSet<>();
		nodo.fieldNames().forEachRemaining(claves::add);
		return claves;
	}

	private static String sha256(String token) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(token.getBytes(StandardCharsets.UTF_8)));
	}

	private Correo ultimoEnviadoA(String para) {
		List<Correo> correos = enviadosA(para);
		return correos.get(correos.size() - 1);
	}

	private List<Correo> enviadosA(String para) {
		ArgumentCaptor<String> asunto = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> remitente = ArgumentCaptor.forClass(String.class);
		verify(email, atLeast(0)).sendEmailConImagen(eq(para), asunto.capture(), html.capture(), any(), any(), any(),
				remitente.capture());
		List<Correo> correos = new ArrayList<>();
		for (int i = 0; i < html.getAllValues().size(); i++) {
			correos.add(new Correo(asunto.getAllValues().get(i), html.getAllValues().get(i),
					remitente.getAllValues().get(i)));
		}
		return correos;
	}
}
