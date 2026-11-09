package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.config.ValidacionRelojConfig;
import com.taemoi.project.dtos.response.GrupoConAlumnosDTO;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoDeporteRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.RallasService;

import jakarta.servlet.http.Cookie;

/**
 * Rallas hacia el examen (decisiones 510, 511 y 513) contra MySQL: pasos y límites, apto desde la hoja, reglas de quién
 * las cambia (titular, ayudante, responsable de sede, gestión, otra escuela), cuándo no se usan (disciplina apagada,
 * sin grado, PUM y DAN), cambio de grado, registro de acciones, concurrencia, configuración y lectura en pasar lista,
 * Mis grupos, el listado de gestión y alumnos por tipo. «Hoy» es el lunes 26/10/2026 a las 20:30 ({@code Clock}
 * simulado, como {@code AsistenciaIntegrationTest}, para compartir contexto).
 *
 * <p>Escuela sintética ({@link EscuelaConSedesDePrueba}) con un sistema de grados propio y otra para el aislamiento;
 * cada test crea sus alumnos. Datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RallasIntegrationTest extends MySqlIntegrationTest {

	private static final String LUNES = "2026-10-26";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;
	@Autowired AlumnoDeporteRepository inscripciones;
	@Autowired TransactionTemplate tx;

	@MockitoBean JavaMailSender mailSender;
	@MockitoBean Clock reloj;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	long gBlanco, gAmarillo, gRojo, gPum, gDan, kickboxing, grupoKb;
	long titular, ayudante, titularB, propietario, propietarioOtra, titularOtra, familia;
	String tTitular, tAyudante, tTitularB, tGestor, tPropietario, tResp, tPropietarioOtra, tGestorOtra, tTitularOtra,
			tFamilia, tRespA;

	@BeforeAll
	void preparar() throws Exception {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-rallas");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-rallas-otra");
		e.crearResponsablesSolos(usuarios);
		long sistema = sistemaDeGrados(e);
		sistemaDeGrados(otra);
		jdbc.update("""
				INSERT INTO disciplina (organizacion_id, codigo, nombre, sistema_grados_id, edad_adulto, orden)
				VALUES (?, 'KICKBOXING', 'Kickboxing IT', ?, 14, 2)""", e.org, sistema);
		kickboxing = jdbc.queryForObject(
				"SELECT id FROM disciplina WHERE organizacion_id = ? AND codigo = 'KICKBOXING'", Long.class, e.org);
		grupoKb = grupo(e, "IT rallas KB " + e.marca, "KICKBOXING", e.sA);

		titular = soloProfesor(e, "titular");
		asignar(e, titular, e.gA, "TITULAR");
		asignar(e, titular, grupoKb, "TITULAR");
		ayudante = soloProfesor(e, "ayudante");
		asignar(e, ayudante, e.gA, "AYUDANTE");
		titularB = soloProfesor(e, "titularb");
		asignar(e, titularB, e.gB, "TITULAR");
		propietario = e.cuenta(usuarios, "propietario");
		membresia(e, propietario, "PROPIETARIO");
		propietarioOtra = otra.cuenta(usuarios, "propietario");
		membresia(otra, propietarioOtra, "PROPIETARIO");
		titularOtra = soloProfesor(otra, "titular");
		asignar(otra, titularOtra, otra.gA, "TITULAR");
		familia = e.cuentaFamilia(usuarios, "familia");

		tTitular = e.token(jwtService, usuarios, titular);
		tAyudante = e.token(jwtService, usuarios, ayudante);
		tTitularB = e.token(jwtService, usuarios, titularB);
		tGestor = e.token(jwtService, usuarios, e.gestor);
		tPropietario = e.token(jwtService, usuarios, propietario);
		tResp = e.token(jwtService, usuarios, e.respSoloA);
		tPropietarioOtra = otra.token(jwtService, usuarios, propietarioOtra);
		tGestorOtra = otra.token(jwtService, usuarios, otra.gestor);
		tTitularOtra = otra.token(jwtService, usuarios, titularOtra);
		tFamilia = e.token(jwtService, usuarios, familia);
		tRespA = e.token(jwtService, usuarios, e.respA);

		// Taekwondo con rallas en las dos escuelas (por la ruta del propietario); kickboxing, apagado.
		leer(configurar("{\"TAEKWONDO\":true}"), tPropietario, 200);
		leer(configurar("{\"TAEKWONDO\":true}"), tPropietarioOtra, 200);
	}

	@BeforeEach
	void lunesPorLaNoche() {
		ahora("2026-10-26T20:30:00");
		when(reloj.getZone()).thenReturn(ValidacionRelojConfig.ZONA);
	}

	@AfterEach
	void sinFugas() {
		assertFalse(TenantContext.get() != null, "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ pasos y límites

	@Test
	void titular_daYQuitaPasos_yDevuelveElValorNuevo() throws Exception {
		long a = alumno("PASOS", e.gA, gAmarillo);
		JsonNode r = leer(paso(prof(e.gA, a), 1), tTitular, 200);
		assertEquals(Set.of("alumnoId", "deporte", "mediasRallas", "aptoParaExamen"), campos(r));
		assertEquals(a, r.get("alumnoId").asLong());
		assertEquals("TAEKWONDO", r.get("deporte").asText());
		assertEquals(1, r.get("mediasRallas").asInt());
		assertFalse(r.get("aptoParaExamen").asBoolean());
		assertEquals(3, leer(paso(prof(e.gA, a), 2), tTitular, 200).get("mediasRallas").asInt());
		assertEquals(2, leer(paso(prof(e.gA, a), -1), tTitular, 200).get("mediasRallas").asInt());
		assertEquals(0, leer(paso(prof(e.gA, a), -2), tTitular, 200).get("mediasRallas").asInt());
		assertEquals(0, medias(a));
	}

	@Test
	void limites_seRechazan_nuncaSeRecortan() throws Exception {
		long a = alumno("LIMITES", e.gA, gAmarillo);
		MvcResult r = peticion(paso(prof(e.gA, a), -1), tTitular);
		assertEquals(409, r.getResponse().getStatus());
		assertEquals(RallasService.NO_HAY_TANTAS, mensaje(r));
		assertEquals(0, medias(a));

		fijar(a, 1);
		assertEquals(409, estado(paso(prof(e.gA, a), -2), tTitular), "−2 con 1");
		assertEquals(1, medias(a));
		assertEquals(0, leer(paso(prof(e.gA, a), -1), tTitular, 200).get("mediasRallas").asInt());

		fijar(a, 9);
		r = peticion(paso(prof(e.gA, a), 2), tTitular);
		assertEquals(409, r.getResponse().getStatus(), "+2 con 9");
		assertEquals(RallasService.NO_CABEN, mensaje(r));
		assertEquals(9, medias(a));
		assertEquals(10, leer(paso(prof(e.gA, a), 1), tTitular, 200).get("mediasRallas").asInt());
		assertEquals(409, estado(paso(prof(e.gA, a), 1), tTitular), "+1 con 10");
		assertEquals(409, estado(paso(prof(e.gA, a), 2), tTitular), "+2 con 10");
		assertEquals(10, medias(a));
		assertEquals(8, leer(paso(prof(e.gA, a), -2), tTitular, 200).get("mediasRallas").asInt());
	}

	@Test
	void pasoNoValido_400_sinCambiarNada() throws Exception {
		long a = alumno("PASONOVALIDO", e.gA, gAmarillo);
		fijar(a, 4);
		for (String cuerpo : List.of("{\"paso\":0}", "{\"paso\":3}", "{\"paso\":-3}", "{\"paso\":10}", "{}",
				"{\"paso\":null}")) {
			MvcResult r = peticion(post(prof(e.gA, a)).contentType(MediaType.APPLICATION_JSON).content(cuerpo), tTitular);
			assertEquals(400, r.getResponse().getStatus(), cuerpo);
			assertEquals(RallasService.PASO_NO_VALIDO, mensaje(r), cuerpo);
		}
		assertEquals(400, estado(post(prof(e.gA, a)), tTitular), "sin cuerpo");
		assertEquals(4, medias(a));
		assertTrue(registroDe(a).isEmpty());
	}

	@Test
	void blancoLleva_sinGradoPumYDanNo_yConservanLoGuardado() throws Exception {
		long blanco = alumno("BLANCO", e.gA, gBlanco);
		assertEquals(1, leer(paso(prof(e.gA, blanco), 1), tTitular, 200).get("mediasRallas").asInt());

		long sinGrado = alumno("SINGRADO", e.gA, null);
		MvcResult r = peticion(paso(prof(e.gA, sinGrado), 1), tTitular);
		assertEquals(409, r.getResponse().getStatus());
		assertEquals(RallasService.SIN_GRADO, mensaje(r));
		assertEquals(0, medias(sinGrado));

		for (long grado : List.of(gPum, gDan)) {
			long a = alumno("PUMDAN" + grado, e.gA, grado);
			fijar(a, 4);
			r = peticion(paso(prof(e.gA, a), 1), tTitular);
			assertEquals(409, r.getResponse().getStatus());
			assertEquals(RallasService.GRADO_SIN_RALLAS, mensaje(r));
			assertEquals(409, estado(post(prof(e.gA, a) + "/apto"), tTitular));
			assertEquals(4, medias(a), "se conservan, ocultas");
			assertFalse(apto(a));
		}
	}

	@Test
	void alumnoFueraDelGrupoODeBaja_404() throws Exception {
		long deB = alumno("DEB", e.gB, gAmarillo);
		MvcResult r = peticion(paso(gest(e.gA, deB), 1), tGestor);
		assertEquals(404, r.getResponse().getStatus());
		assertEquals(RallasService.ALUMNO_NO_ES_DEL_GRUPO, mensaje(r));
		long deBaja = alumno("DEBAJA", e.gA, gAmarillo);
		jdbc.update("UPDATE alumno SET activo = FALSE WHERE id = ?", deBaja);
		assertEquals(404, estado(paso(prof(e.gA, deBaja), 1), tTitular));
		assertEquals(404, estado(paso(gest(e.gA, 999999999L), 1), tGestor));
		assertEquals(0, medias(deB));
		assertEquals(0, medias(deBaja));
	}

	// ------------------------------------------------------------------ permisos

	@Test
	void ayudante_soloVe_403_sinCambiarNada() throws Exception {
		long a = alumno("AYUDANTE", e.gA, gAmarillo);
		fijar(a, 10);
		MvcResult r = peticion(paso(prof(e.gA, a), 1), tAyudante);
		assertEquals(403, r.getResponse().getStatus());
		assertEquals(RallasService.SOLO_TITULAR, mensaje(r));
		assertEquals(403, estado(paso(prof(e.gA, a), -1), tAyudante));
		r = peticion(post(prof(e.gA, a) + "/apto"), tAyudante);
		assertEquals(403, r.getResponse().getStatus());
		assertEquals(RallasService.SOLO_TITULAR, mensaje(r));
		assertEquals(10, medias(a));
		assertFalse(apto(a));
		assertTrue(registroDe(a).isEmpty());
	}

	@Test
	void cadaRol_porSuRuta() throws Exception {
		long a = alumno("ROLES", e.gA, gAmarillo);
		assertEquals(1, leer(paso(gest(e.gA, a), 1), tPropietario, 200).get("mediasRallas").asInt(), "propietario");
		assertEquals(2, leer(paso(gest(e.gA, a), 1), tGestor, 200).get("mediasRallas").asInt(), "gestor");
		assertEquals(3, leer(paso(gest(e.gA, a), 1), tResp, 200).get("mediasRallas").asInt(), "responsable de su sede");
		assertEquals(4, leer(paso(prof(e.gA, a), 1), tTitular, 200).get("mediasRallas").asInt(), "titular");

		// Responsable de otra sede, titular de otro grupo, sesión solo-profesor por gestión, gestión por la del profesor.
		long deB = alumno("ROLESB", e.gB, gAmarillo);
		assertEquals(403, estado(paso(gest(e.gB, deB), 1), tResp), "otra sede");
		assertEquals(403, estado(paso(prof(e.gA, a), 1), tTitularB), "no es su grupo");
		assertEquals(403, estado(paso(gest(e.gA, a), 1), tTitular), "solo-profesor por gestión");
		assertEquals(403, estado(paso(prof(e.gA, a), 1), tPropietario), "sin PROFESOR por la del profesor");
		assertEquals(403, estado(paso(prof(e.gB, deB), 1), tTitular), "titular de gA en gB");
		assertEquals(1, leer(paso(prof(e.gB, deB), 1), tTitularB, 200).get("mediasRallas").asInt(), "el titular de gB");
		assertEquals(4, medias(a));
	}

	@Test
	void otraEscuela_niLeeNiCambia() throws Exception {
		long a = alumno("OTRAESC", e.gA, gAmarillo);
		fijar(a, 5);
		assertEquals(404, estado(paso(gest(e.gA, a), 1), tGestorOtra), "gestor de otra: el grupo no existe en su escuela");
		assertEquals(404, estado(post(gest(e.gA, a) + "/apto"), tGestorOtra));
		assertEquals(403, estado(paso(prof(e.gA, a), 1), tTitularOtra), "profesor de otra: no es su grupo");
		// Un alumno de otra escuela bajo un grupo propio tampoco existe.
		assertEquals(404, estado(paso(gest(e.gA, otra.aA), 1), tGestor));
		assertEquals(5, medias(a));
		assertEquals(404, estado(get("/api/grupos/" + e.gA + "/clases/" + e.tA + "/" + LUNES), tGestorOtra));

		// Configuración: cada propietario ve y cambia solo la suya.
		JsonNode suya = leer(get("/api/admin/configuracion/rallas"), tPropietarioOtra, 200);
		assertFalse(suya.toString().contains("Kickboxing IT"), "no ve las disciplinas de la otra escuela");
		assertEquals(400, estado(configurar("{\"KICKBOXING\":true}"), tPropietarioOtra), "su escuela no tiene KICKBOXING");
		assertFalse(activa(kickboxing));
		leer(configurar("{\"TAEKWONDO\":false}"), tPropietarioOtra, 200);
		assertTrue(activa(e.disciplina), "la de la escuela e no cambia");
		leer(configurar("{\"TAEKWONDO\":true}"), tPropietarioOtra, 200);
	}

	// ------------------------------------------------------------------ disciplina apagada y configuración

	@Test
	void disciplinaDesactivada_rechazaYConserva_yVuelveAlReactivar() throws Exception {
		long a = alumno("KB", grupoKb, gAmarillo, kickboxing);
		fijar(a, kickboxing, 6);
		MvcResult r = peticion(paso(prof(grupoKb, a), 1), tTitular);
		assertEquals(409, r.getResponse().getStatus());
		assertEquals(RallasService.DESACTIVADAS, mensaje(r));
		JsonNode kb = deporteDe(leer(get("/api/profesor/grupos/" + grupoKb + "/alumnos"), tTitular, 200), a, "KICKBOXING");
		assertTrue(kb.get("mediasRallas").isNull(), "oculta");
		assertFalse(kb.get("aplicaRallas").asBoolean());

		try {
			JsonNode lista = leer(configurar("{\"KICKBOXING\":true}"), tPropietario, 200);
			assertTrue(disciplina(lista, "KICKBOXING").get("activas").asBoolean());
			kb = deporteDe(leer(get("/api/profesor/grupos/" + grupoKb + "/alumnos"), tTitular, 200), a, "KICKBOXING");
			assertEquals(6, kb.get("mediasRallas").asInt(), "vuelve tal cual");
			assertEquals(7, leer(paso(prof(grupoKb, a), 1), tTitular, 200).get("mediasRallas").asInt());

			lista = leer(configurar("{\"KICKBOXING\":false}"), tPropietario, 200);
			assertFalse(disciplina(lista, "KICKBOXING").get("activas").asBoolean());
			assertEquals(409, estado(paso(prof(grupoKb, a), 1), tTitular));
			assertEquals(7, medias(a, kickboxing), "desactivar no borra");
		} finally {
			jdbc.update("UPDATE disciplina SET rallas_activas = FALSE WHERE id = ?", kickboxing);
		}
		List<String> resumenes = jdbc.queryForList("""
				SELECT resumen FROM auditoria_evento WHERE modulo = 'rallas' AND recurso_id = ? ORDER BY id""",
				String.class, kickboxing);
		assertEquals(List.of("RALLAS ACTIVADAS disciplina=#" + kickboxing, "RALLAS DESACTIVADAS disciplina=#" + kickboxing),
				resumenes);
	}

	@Test
	void configuracion_soloElPropietario_yValidaTodoONada() throws Exception {
		JsonNode lista = leer(get("/api/admin/configuracion/rallas"), tPropietario, 200);
		assertEquals(Set.of("deporte", "nombre", "activas"), campos(lista.get(0)));
		assertTrue(disciplina(lista, "TAEKWONDO").get("activas").asBoolean());
		assertFalse(disciplina(lista, "KICKBOXING").get("activas").asBoolean());
		for (String token : List.of(tGestor, tResp, tTitular)) {
			assertEquals(403, estado(get("/api/admin/configuracion/rallas"), token));
			assertEquals(403, estado(configurar("{\"KICKBOXING\":true}"), token));
		}
		for (String cuerpo : List.of("{}", "{\"NO_EXISTE\":true}", "{\"KICKBOXING\":true,\"NO_EXISTE\":true}",
				"{\"KICKBOXING\":null}")) {
			assertEquals(400, estado(configurar(cuerpo), tPropietario), cuerpo);
		}
		assertFalse(activa(kickboxing), "todo o nada");
		// Sin cambios reales: sin registro.
		long antes = registroConfiguracion();
		leer(configurar("{\"TAEKWONDO\":true}"), tPropietario, 200);
		assertEquals(antes, registroConfiguracion());
	}

	@Test
	void porDefecto_unaDisciplinaNuevaNoLasTiene() throws Exception {
		assertFalse(activa(kickboxing));
		assertTrue(jdbc.queryForList("SELECT rallas_activas FROM disciplina WHERE organizacion_id = ? AND codigo <> 'TAEKWONDO'",
				Boolean.class, e.org).stream().noneMatch(Boolean.TRUE::equals));
	}

	// ------------------------------------------------------------------ apto desde la hoja

	@Test
	void marcarApto_soloCon10_yAtomico() throws Exception {
		long a = alumno("APTO", e.gA, gAmarillo);
		fijar(a, 9);
		MvcResult r = peticion(post(prof(e.gA, a) + "/apto"), tTitular);
		assertEquals(409, r.getResponse().getStatus());
		assertEquals(RallasService.SOLO_CON_CINCO, mensaje(r));
		assertEquals(9, medias(a));
		assertFalse(apto(a));

		fijar(a, 10);
		JsonNode ok = leer(post(prof(e.gA, a) + "/apto"), tTitular, 200);
		assertEquals(0, ok.get("mediasRallas").asInt());
		assertTrue(ok.get("aptoParaExamen").asBoolean());
		assertEquals(0, medias(a));
		assertTrue(apto(a));
		assertEquals(409, estado(post(prof(e.gA, a) + "/apto"), tTitular), "otra vez: ya no tiene las 10");
		assertTrue(registroDe(a).stream().anyMatch(s -> s.startsWith("RALLAS APTO_DESDE_HOJA alumno=#" + a)));

		long porGestion = alumno("APTOGEST", e.gA, gAmarillo);
		fijar(porGestion, 10);
		assertTrue(leer(post(gest(e.gA, porGestion) + "/apto"), tResp, 200).get("aptoParaExamen").asBoolean());
		assertEquals(0, medias(porGestion));
	}

	@Test
	void aptoPorOtraVia_noTocaLasRallas() throws Exception {
		long a = alumno("APTOOTRA", e.gA, gAmarillo);
		fijar(a, 6);
		assertEquals(200, estado(put("/api/profesor/alumnos/" + a + "/deportes/TAEKWONDO/apto")
				.contentType(MediaType.APPLICATION_JSON).content("{\"aptoParaExamen\":true}"), tTitular));
		assertTrue(apto(a));
		assertEquals(6, medias(a));
	}

	/**
	 * Otra ruta carga la inscripción, a la vez cambia un paso (aquí por jdbc) y luego guarda la entidad que tenía
	 * cargada, incluso con otras medias en memoria: queda el valor de la sentencia, porque la columna no se escribe
	 * desde la entidad ({@code updatable = false}).
	 */
	@Test
	void guardarLaInscripcionCargadaAntes_noPisaUnPasoDadoALaVez() {
		long a = alumno("PASOALAVEZ", e.gA, gAmarillo);
		fijar(a, 6);
		long ad = alumnoDeporte(a, e.disciplina);
		TenantContext.ejecutarCon(e.org, () -> tx.executeWithoutResult(s -> {
			AlumnoDeporte cargada = inscripciones.findById(ad).orElseThrow();
			assertEquals(6, cargada.getMediasRallas());
			jdbc.update("UPDATE alumno_deporte SET medias_rallas = 3 WHERE id = ?", ad);
			cargada.reflejarMediasRallas(0);
			cargada.setCuantiaTarifa(41.5);
			inscripciones.saveAndFlush(cargada);
		}));
		assertEquals(3, medias(a));
		assertEquals(41.5, cuantia(ad), "la otra ruta sí guarda lo suyo");
	}

	/**
	 * «Apto desde la hoja» mientras otra ruta tiene la inscripción cargada (apto = false) y después la guarda por otro
	 * campo: el apto de la hoja no se pierde (la entidad solo escribe las columnas que cambió, {@code @DynamicUpdate}).
	 */
	@Test
	void aptoDesdeLaHoja_noLoPisaGuardarLaInscripcionCargadaAntes() {
		long a = alumno("APTOALAVEZ", e.gA, gAmarillo);
		fijar(a, 10);
		long ad = alumnoDeporte(a, e.disciplina);
		TenantContext.ejecutarCon(e.org, () -> tx.executeWithoutResult(s -> {
			AlumnoDeporte cargada = inscripciones.findById(ad).orElseThrow();
			assertFalse(cargada.getAptoParaExamen());
			// La misma sentencia que POST .../rallas/apto.
			assertEquals(1, inscripciones.marcarAptoConRallasCompletas(ad, e.org, gAmarillo));
			cargada.setCuantiaTarifa(42.5);
			inscripciones.saveAndFlush(cargada);
		}));
		assertTrue(apto(a), "el apto de la hoja sigue");
		assertEquals(0, medias(a));
		assertEquals(42.5, cuantia(ad), "la otra ruta sí guarda lo suyo");
	}

	/**
	 * Decisión 513 j: el área de familias no expone las rallas, tampoco en el JSON. Una cuenta familia (y una cuenta
	 * familia + responsable de sede con un alumno propio fuera de sus sedes) no recibe {@code mediasRallas} ni
	 * {@code aplicaRallas} en {@code GET /api/alumnos/{id}/deportes}; el panel (gestión y responsable de la sede), sí.
	 */
	@Test
	void familia_noRecibeLasRallas_elPanelSi() throws Exception {
		long a = alumno("FAMILIA", e.gA, gAmarillo);
		fijar(a, 3);
		jdbc.update("""
				INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen)
				VALUES (?, ?, ?, 'TUTOR', 'MANUAL')""", familia, a, e.org);

		JsonNode comoFamilia = leer(get("/api/alumnos/" + a + "/deportes"), tFamilia, 200);
		assertEquals(1, comoFamilia.size());
		assertFalse(comoFamilia.get(0).has("mediasRallas"), comoFamilia.toString());
		assertFalse(comoFamilia.get(0).has("aplicaRallas"), comoFamilia.toString());
		assertEquals("TAEKWONDO", comoFamilia.get(0).get("deporte").asText(), "el resto sigue igual");
		assertEquals("AMARILLO", comoFamilia.get(0).get("grado").asText());

		// Cuenta mixta familia + responsable de sede: su alumno propio (sin sede) lo ve como familia.
		inscribir(e.propioA, gAmarillo, e.disciplina);
		jdbc.update("UPDATE alumno_deporte SET medias_rallas = 4 WHERE alumno_id = ?", e.propioA);
		JsonNode propio = leer(get("/api/alumnos/" + e.propioA + "/deportes"), tRespA, 200);
		assertFalse(propio.get(0).has("mediasRallas"), propio.toString());
		assertFalse(propio.get(0).has("aplicaRallas"), propio.toString());

		for (String token : List.of(tGestor, tPropietario, tResp, tRespA)) {
			JsonNode panel = leer(get("/api/alumnos/" + a + "/deportes"), token, 200);
			assertEquals(3, panel.get(0).get("mediasRallas").asInt());
			assertTrue(panel.get(0).get("aplicaRallas").asBoolean());
		}
		// En el panel, null cuando no se usan (se sigue enviando la clave).
		long dan = alumno("FAMILIADAN", e.gA, gDan);
		JsonNode panelDan = leer(get("/api/alumnos/" + dan + "/deportes"), tGestor, 200);
		assertTrue(panelDan.get(0).has("mediasRallas"));
		assertTrue(panelDan.get(0).get("mediasRallas").isNull());
		assertFalse(panelDan.get(0).get("aplicaRallas").asBoolean());
	}

	// ------------------------------------------------------------------ cambio de grado

	@Test
	void cambioDeGrado_ponLasRallasACero_yQuedaEnElRegistro() throws Exception {
		long a = alumno("GRADO", e.gA, gAmarillo);
		fijar(a, 7);
		assertEquals(200, estado(cambiarGrado(a, "AMARILLO"), tGestor), "mismo grado");
		assertEquals(7, medias(a), "con el mismo grado no cambian");

		JsonNode dto = leer(cambiarGrado(a, "ROJO"), tGestor, 200);
		assertEquals(0, medias(a));
		assertEquals(0, dto.get("mediasRallas").asInt(), "la respuesta ya sale con 0");
		long ad = alumnoDeporte(a, e.disciplina);
		assertTrue(registroDe(a).contains("RALLAS A_CERO_POR_GRADO alumno=#" + a + " alumnoDeporte=#" + ad
				+ " gradoAnterior=#" + gAmarillo + " gradoNuevo=#" + gRojo), registroDe(a).toString());

		// Ya en 0: otro cambio no deja registro.
		int antes = registroDe(a).size();
		assertEquals(200, estado(cambiarGrado(a, "AMARILLO"), tGestor));
		assertEquals(antes, registroDe(a).size());
	}

	@Test
	void cambioDeGrado_conDisciplinaApagada_tambienLasPoneACero() throws Exception {
		long a = alumno("GRADOKB", grupoKb, gAmarillo, kickboxing);
		fijar(a, kickboxing, 5);
		assertEquals(200, estado(put("/api/alumnos/" + a + "/deportes/KICKBOXING/grado")
				.contentType(MediaType.APPLICATION_JSON).content("{\"nuevoGrado\":\"ROJO\"}"), tGestor));
		assertEquals(0, medias(a, kickboxing));
	}

	// ------------------------------------------------------------------ registro

	@Test
	void cadaPasoQuedaEnElRegistro() throws Exception {
		long a = alumno("REGISTRO", e.gA, gAmarillo);
		long ad = alumnoDeporte(a, e.disciplina);
		leer(paso(prof(e.gA, a), 2), tTitular, 200);
		leer(paso(gest(e.gA, a), -1), tGestor, 200);
		List<Map<String, Object>> filas = jdbc.queryForList("""
				SELECT accion, endpoint, usuario_id, organizacion_id, resumen FROM auditoria_evento
				WHERE modulo = 'rallas' AND recurso_id = ? AND resumen LIKE 'RALLAS PASO%' ORDER BY id""", a);
		assertEquals(2, filas.size());
		assertEquals("RALLAS PASO alumno=#" + a + " grupo=#" + e.gA + " alumnoDeporte=#" + ad + " paso=2 medias=2",
				filas.get(0).get("resumen"));
		assertEquals("/api/profesor/grupos/" + e.gA + "/alumnos/" + a + "/rallas", filas.get(0).get("endpoint"));
		assertEquals(titular, ((Number) filas.get(0).get("usuario_id")).longValue());
		assertEquals(e.org, ((Number) filas.get(0).get("organizacion_id")).longValue());
		assertEquals("RALLAS PASO alumno=#" + a + " grupo=#" + e.gA + " alumnoDeporte=#" + ad + " paso=-1 medias=1",
				filas.get(1).get("resumen"));
		assertEquals("/api/grupos/" + e.gA + "/alumnos/" + a + "/rallas", filas.get(1).get("endpoint"));
		assertEquals("UPDATE", filas.get(1).get("accion"));
	}

	// ------------------------------------------------------------------ concurrencia

	@Test
	void dosProfesoresALaVez_noPierdenPasos() throws Exception {
		long a = alumno("CONCURRENCIA", e.gA, gAmarillo);
		for (int vuelta = 0; vuelta < 3; vuelta++) {
			List<Integer> estados = enParalelo(() -> estado(paso(prof(e.gA, a), 1), tTitular),
					() -> estado(paso(gest(e.gA, a), 1), tGestor));
			assertEquals(List.of(200, 200), estados);
		}
		assertEquals(6, medias(a), "los seis pasos");

		fijar(a, 9);
		List<Integer> estados = enParalelo(() -> estado(paso(prof(e.gA, a), 1), tTitular),
				() -> estado(paso(gest(e.gA, a), 1), tGestor));
		assertEquals(List.of(200, 409), estados, "solo cabe uno");
		assertEquals(10, medias(a));
	}

	// ------------------------------------------------------------------ lectura

	@Test
	void pasarLista_llevaRallasPermisoGeneroYColores() throws Exception {
		long turno = turnoLunes(e.gA);
		long a = alumno("LISTA", e.gA, gAmarillo);
		long dan = alumno("LISTADAN", e.gA, gDan);
		e.enTurno(turno, a);
		e.enTurno(turno, dan);
		jdbc.update("UPDATE alumno SET genero = 'FEMENINO' WHERE id = ?", a);
		fijar(a, 5);
		fijar(dan, 3);

		JsonNode lista = leer(get("/api/profesor/grupos/" + e.gA + "/clases/" + turno + "/" + LUNES), tTitular, 200);
		assertTrue(lista.get("puedeCambiarRallas").asBoolean());
		JsonNode alumna = buscar(lista.get("alumnos"), a);
		assertEquals(Set.of("id", "nombre", "apellidos", "presente", "deportes", "motivo", "genero", "puedeCambiarRallas"),
				campos(alumna));
		assertEquals("FEMENINO", alumna.get("genero").asText());
		assertTrue(alumna.get("puedeCambiarRallas").asBoolean());
		JsonNode tkd = deporte(alumna, "TAEKWONDO");
		assertTrue(tkd.get("delGrupo").asBoolean());
		assertEquals(5, tkd.get("mediasRallas").asInt());
		assertTrue(tkd.get("aplicaRallas").asBoolean());
		assertEquals(Set.of("codigo", "nombre", "color1", "color2"), campos(tkd.get("grado")));
		assertEquals("AMARILLO", tkd.get("grado").get("codigo").asText());
		assertEquals("#FFFF00", tkd.get("grado").get("color1").asText());
		assertTrue(tkd.get("grado").get("color2").isNull());

		JsonNode delDan = buscar(lista.get("alumnos"), dan);
		assertTrue(delDan.get("genero").isNull());
		assertFalse(delDan.get("puedeCambiarRallas").asBoolean(), "DAN no lleva");
		assertTrue(deporte(delDan, "TAEKWONDO").get("mediasRallas").isNull(), "DAN: oculta");
		assertEquals("#000000", deporte(delDan, "TAEKWONDO").get("grado").get("color1").asText());

		// Ayudante: ve las rallas, no puede cambiarlas.
		JsonNode suya = leer(get("/api/profesor/grupos/" + e.gA + "/clases/" + turno + "/" + LUNES), tAyudante, 200);
		assertFalse(suya.get("puedeCambiarRallas").asBoolean());
		assertFalse(buscar(suya.get("alumnos"), a).get("puedeCambiarRallas").asBoolean());
		assertEquals(5, deporte(buscar(suya.get("alumnos"), a), "TAEKWONDO").get("mediasRallas").asInt());

		// Gestión y responsable de la sede, sí; responsable de otra sede no entra (403, como pasar lista).
		for (String token : List.of(tGestor, tResp, tPropietario)) {
			JsonNode g = leer(get("/api/grupos/" + e.gA + "/clases/" + turno + "/" + LUNES), token, 200);
			assertTrue(g.get("puedeCambiarRallas").asBoolean());
			assertTrue(buscar(g.get("alumnos"), a).get("puedeCambiarRallas").asBoolean());
		}
	}

	@Test
	void misGruposFichaYListados_llevanLasRallasEnLectura() throws Exception {
		long a = alumno("LECTURA", e.gA, gAmarillo);
		fijar(a, 3);
		jdbc.update("UPDATE alumno SET genero = 'FEMENINO' WHERE id = ?", a);
		JsonNode grupo = leer(get("/api/profesor/grupos/" + e.gA + "/alumnos"), tAyudante, 200);
		JsonNode enGrupo = deporteDe(grupo, a, "TAEKWONDO");
		assertEquals(3, enGrupo.get("mediasRallas").asInt());
		assertTrue(enGrupo.get("aplicaRallas").asBoolean());
		assertEquals("FEMENINO", buscar(grupo, a).get("genero").asText(), "listo/lista (decisión 513 d)");

		JsonNode ficha = leer(get("/api/profesor/alumnos/" + a), tTitular, 200);
		assertEquals("FEMENINO", ficha.get("genero").asText());
		JsonNode enFicha = deporte(ficha, "TAEKWONDO");
		assertEquals(3, enFicha.get("mediasRallas").asInt());
		assertTrue(enFicha.get("aplicaRallas").asBoolean());

		JsonNode deportes = leer(get("/api/alumnos/" + a + "/deportes"), tGestor, 200);
		assertEquals(3, deportes.get(0).get("mediasRallas").asInt());
		assertTrue(deportes.get(0).get("aplicaRallas").asBoolean());

		JsonNode porTipo = leer(get("/api/grupos/tipo/taekwondo/alumnos"), tGestor, 200);
		JsonNode corto = null;
		for (JsonNode fila : porTipo) {
			if (fila.get("id").asLong() == a) {
				corto = fila;
			}
		}
		assertTrue(corto != null, "sale en alumnos por tipo");
		assertEquals("AMARILLO", corto.get("codigoGrado").asText());
		assertEquals("TAEKWONDO", corto.get("deporte").asText());
		assertEquals(3, corto.get("mediasRallas").asInt());
		assertTrue(corto.get("aplicaRallas").asBoolean());
		assertEquals("FEMENINO", corto.get("genero").asText());

		long sinGrado = alumno("LECTURASIN", e.gA, null);
		JsonNode sin = deporteDe(leer(get("/api/profesor/grupos/" + e.gA + "/alumnos"), tTitular, 200), sinGrado,
				"TAEKWONDO");
		assertTrue(sin.get("mediasRallas").isNull());
		assertFalse(sin.get("aplicaRallas").asBoolean());
		assertNull(jsonNulo(sin.get("grado")));
		JsonNode sinGenero = buscar(leer(get("/api/profesor/grupos/" + e.gA + "/alumnos"), tTitular, 200), sinGrado);
		assertTrue(sinGenero.has("genero") && sinGenero.get("genero").isNull(), "sin género: null");
	}

	@Test
	void misGrupos_ordenaLosAlumnosComoPasarLista_porNombreYApellidos() throws Exception {
		long g = grupo(e, "IT orden " + e.marca, "TAEKWONDO", e.sA);
		long profesor = soloProfesor(e, "orden");
		asignar(e, profesor, g, "TITULAR");
		String token = e.token(jwtService, usuarios, profesor);
		long turno = turnoLunes(g);
		List<Long> esperado = new ArrayList<>();
		// Por nombre y luego apellidos: «Nuria Calvo» antes de «Nuria Santos»; «Alma» antes que «Candela».
		for (String[] n : new String[][] { { "Alma", "Diez" }, { "Candela", "Calvo" }, { "Nuria", "Calvo" },
				{ "Nuria", "Santos" } }) {
			long id = alumno("ORDEN" + n[0] + n[1], g, gAmarillo);
			jdbc.update("UPDATE alumno SET nombre = ?, apellidos = ? WHERE id = ?", n[0], n[1], id);
			esperado.add(id);
		}
		// Se dan de alta en otro orden que el esperado.
		List<Long> alta = List.of(esperado.get(3), esperado.get(1), esperado.get(0), esperado.get(2));
		for (long id : alta) {
			e.enTurno(turno, id);
		}

		List<Long> enGrupo = new ArrayList<>();
		leer(get("/api/profesor/grupos/" + g + "/alumnos"), token, 200).forEach(a -> enGrupo.add(a.get("id").asLong()));
		assertEquals(esperado, enGrupo, "Mis grupos: por nombre y apellidos");

		List<Long> enLista = new ArrayList<>();
		leer(get("/api/profesor/grupos/" + g + "/clases/" + turno + "/" + LUNES), token, 200).get("alumnos")
				.forEach(a -> enLista.add(a.get("id").asLong()));
		assertEquals(enLista, enGrupo, "el mismo orden que Pasar lista");
	}

	@Test
	void fichaDeMisGrupos_diceSiPuedeCambiarLasRallasEnCadaGrupo() throws Exception {
		long a = alumno("FICHAHOJA", e.gA, gAmarillo);
		fijar(a, 4);

		// Titular: puede en su grupo; el grupo trae su disciplina para saber qué cinturón abre la hoja.
		JsonNode delTitular = buscar(leer(get("/api/profesor/alumnos/" + a), tTitular, 200).get("grupos"), e.gA);
		assertTrue(delTitular.get("puedeCambiarRallas").asBoolean());
		assertEquals("TAEKWONDO", delTitular.get("deporte").asText());
		assertTrue(delTitular.has("id") && delTitular.has("nombre") && delTitular.has("sede"), "los campos de siempre siguen");

		// Ayudante: ve la ficha, pero no puede cambiarlas.
		JsonNode delAyudante = buscar(leer(get("/api/profesor/alumnos/" + a), tAyudante, 200).get("grupos"), e.gA);
		assertFalse(delAyudante.get("puedeCambiarRallas").asBoolean());
		assertEquals("TAEKWONDO", delAyudante.get("deporte").asText());

		// Otro grupo u otra escuela: sin acceso a la ficha, como hoy.
		assertEquals(403, estado(get("/api/profesor/alumnos/" + a), tTitularB));
		assertEquals(403, estado(get("/api/profesor/alumnos/" + a), tTitularOtra));
	}

	// ------------------------------------------------------------------ utilidades

	private long sistemaDeGrados(EscuelaConSedesDePrueba x) {
		jdbc.update("INSERT INTO sistema_grados (organizacion_id, nombre, plantilla_origen) VALUES (?, 'Sistema IT rallas', NULL)",
				x.org);
		long sistema = jdbc.queryForObject("SELECT MAX(id) FROM sistema_grados WHERE organizacion_id = ?", Long.class,
				x.org);
		jdbc.update("""
				INSERT INTO grado (organizacion_id, sistema_grados_id, codigo, nombre, orden, color_1, color_2, aplica_a)
				VALUES (?, ?, 'BLANCO', 'Blanco IT', 1, '#FFFFFF', NULL, 'TODOS'),
				       (?, ?, 'AMARILLO', 'Amarillo IT', 2, '#FFFF00', NULL, 'TODOS'),
				       (?, ?, 'ROJO', 'Rojo IT', 3, '#FF0000', NULL, 'TODOS'),
				       (?, ?, 'ROJO_NEGRO_1_PUM', 'Rojo-negro IT', 4, '#FF0000', '#000000', 'TODOS'),
				       (?, ?, 'NEGRO_1_DAN', 'Negro IT', 5, '#000000', NULL, 'TODOS')""",
				x.org, sistema, x.org, sistema, x.org, sistema, x.org, sistema, x.org, sistema);
		jdbc.update("UPDATE disciplina SET sistema_grados_id = ? WHERE id = ?", sistema, x.disciplina);
		if (x == e) {
			gBlanco = grado(sistema, "BLANCO");
			gAmarillo = grado(sistema, "AMARILLO");
			gRojo = grado(sistema, "ROJO");
			gPum = grado(sistema, "ROJO_NEGRO_1_PUM");
			gDan = grado(sistema, "NEGRO_1_DAN");
		}
		return sistema;
	}

	private long grado(long sistema, String codigo) {
		return jdbc.queryForObject("SELECT id FROM grado WHERE sistema_grados_id = ? AND codigo = ?", Long.class, sistema,
				codigo);
	}

	private long grupo(EscuelaConSedesDePrueba x, String nombre, String deporte, long sede) {
		GrupoConAlumnosDTO dto = new GrupoConAlumnosDTO();
		dto.setNombre(nombre);
		dto.setDeporte(deporte);
		dto.setSedeId(sede);
		return TenantContext.calcularCon(x.org, () -> grupoService.crearGrupo(dto).getId());
	}

	private long alumno(String prefijo, long grupo, Long grado) {
		return alumno(prefijo, grupo, grado, e.disciplina);
	}

	/** Alumno activo de la escuela e, en ese grupo, con esa disciplina en ese grado (o sin grado). */
	private long alumno(String prefijo, long grupo, Long grado, long disciplina) {
		long id = e.alumno("RALLAS" + prefijo + e.marca, e.sA, true);
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", grupo, id);
		inscribir(id, grado, disciplina);
		return id;
	}

	private long turnoLunes(long grupo) {
		jdbc.update("""
				INSERT INTO turno (dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id)
				VALUES ('Lunes', '17:00', '18:00', NULL, ?, ?)""", grupo, e.org);
		return jdbc.queryForObject("SELECT MAX(id) FROM turno WHERE organizacion_id = ?", Long.class, e.org);
	}

	private void fijar(long alumno, int medias) {
		fijar(alumno, e.disciplina, medias);
	}

	private void fijar(long alumno, long disciplina, int medias) {
		jdbc.update("UPDATE alumno_deporte SET medias_rallas = ? WHERE alumno_id = ? AND disciplina_id = ?", medias,
				alumno, disciplina);
	}

	private double cuantia(long alumnoDeporte) {
		return jdbc.queryForObject("SELECT cuantia_tarifa FROM alumno_deporte WHERE id = ?", Double.class, alumnoDeporte);
	}

	/** Inscripción activa del alumno en esa disciplina y ese grado (como {@link #alumno}, para un alumno ya creado). */
	private void inscribir(long alumno, Long grado, long disciplina) {
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
				  organizacion_id, fecha_alta, fecha_alta_inicial, disciplina_id, grado_id, fecha_grado)
				VALUES (TRUE, FALSE, ?, 0, 1, 0, ?, '2023-09-01', '2023-09-01', ?, ?, '2025-01-10')""", alumno, e.org,
				disciplina, grado);
	}

	private int medias(long alumno) {
		return medias(alumno, e.disciplina);
	}

	private int medias(long alumno, long disciplina) {
		return jdbc.queryForObject("SELECT medias_rallas FROM alumno_deporte WHERE alumno_id = ? AND disciplina_id = ?",
				Integer.class, alumno, disciplina);
	}

	private boolean apto(long alumno) {
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT apto_para_examen FROM alumno_deporte WHERE alumno_id = ? AND disciplina_id = ?", Boolean.class,
				alumno, e.disciplina));
	}

	private boolean activa(long disciplina) {
		return Boolean.TRUE.equals(
				jdbc.queryForObject("SELECT rallas_activas FROM disciplina WHERE id = ?", Boolean.class, disciplina));
	}

	private long alumnoDeporte(long alumno, long disciplina) {
		return jdbc.queryForObject("SELECT id FROM alumno_deporte WHERE alumno_id = ? AND disciplina_id = ?", Long.class,
				alumno, disciplina);
	}

	/** Resúmenes del registro de rallas de ese alumno, en orden. */
	private List<String> registroDe(long alumno) {
		return jdbc.queryForList("""
				SELECT resumen FROM auditoria_evento WHERE modulo = 'rallas' AND recurso_id = ? AND resumen LIKE 'RALLAS %'
				ORDER BY id""", String.class, alumno);
	}

	private long registroConfiguracion() {
		return jdbc.queryForObject("""
				SELECT COUNT(*) FROM auditoria_evento WHERE modulo = 'rallas'
				AND (resumen LIKE 'RALLAS ACTIVADAS%' OR resumen LIKE 'RALLAS DESACTIVADAS%')""", Long.class);
	}

	private static String prof(long grupo, long alumno) {
		return "/api/profesor/grupos/" + grupo + "/alumnos/" + alumno + "/rallas";
	}

	private static String gest(long grupo, long alumno) {
		return "/api/grupos/" + grupo + "/alumnos/" + alumno + "/rallas";
	}

	private static MockHttpServletRequestBuilder paso(String ruta, int paso) {
		return post(ruta).contentType(MediaType.APPLICATION_JSON).content("{\"paso\":" + paso + "}");
	}

	private static MockHttpServletRequestBuilder configurar(String cuerpo) {
		return put("/api/admin/configuracion/rallas").contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private static MockHttpServletRequestBuilder cambiarGrado(long alumno, String grado) {
		return put("/api/alumnos/" + alumno + "/deportes/TAEKWONDO/grado").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nuevoGrado\":\"" + grado + "\"}");
	}

	private void ahora(String fechaHora) {
		when(reloj.instant()).thenReturn(LocalDateTime.parse(fechaHora).atZone(ValidacionRelojConfig.ZONA).toInstant());
	}

	private long soloProfesor(EscuelaConSedesDePrueba x, String nombre) {
		long id = x.cuenta(usuarios, nombre);
		membresia(x, id, "PROFESOR");
		return id;
	}

	private void membresia(EscuelaConSedesDePrueba x, long usuario, String rol) {
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", usuario, x.org, rol);
	}

	private void asignar(EscuelaConSedesDePrueba x, long profesor, long grupo, String rol) {
		jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) VALUES (?, ?, ?, ?)",
				x.org, profesor, grupo, rol);
	}

	private static JsonNode disciplina(JsonNode lista, String codigo) {
		for (JsonNode d : lista) {
			if (codigo.equals(d.get("deporte").asText())) {
				return d;
			}
		}
		throw new AssertionError("Sin " + codigo + " en " + lista);
	}

	private static JsonNode deporteDe(JsonNode alumnos, long alumno, String deporte) {
		return deporte(buscar(alumnos, alumno), deporte);
	}

	private static JsonNode deporte(JsonNode alumno, String codigo) {
		for (JsonNode d : alumno.get("deportes")) {
			if (codigo.equals(d.get("deporte").asText())) {
				return d;
			}
		}
		throw new AssertionError("Sin " + codigo + " en " + alumno);
	}

	private static JsonNode buscar(JsonNode alumnos, long id) {
		for (JsonNode alumno : alumnos) {
			if (alumno.get("id").asLong() == id) {
				return alumno;
			}
		}
		throw new AssertionError("Sin el alumno " + id + " en " + alumnos);
	}

	private static JsonNode jsonNulo(JsonNode nodo) {
		return nodo == null || nodo.isNull() ? null : nodo;
	}

	private static Set<String> campos(JsonNode nodo) {
		Set<String> campos = new TreeSet<>();
		for (Iterator<String> it = nodo.fieldNames(); it.hasNext();) {
			campos.add(it.next());
		}
		return campos;
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

	private JsonNode leer(MockHttpServletRequestBuilder peticion, String token, int esperado) throws Exception {
		MvcResult r = peticion(peticion, token);
		assertEquals(esperado, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString());
	}

	private int estado(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return peticion(peticion, token).getResponse().getStatus();
	}

	private MvcResult peticion(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
	}

	private String mensaje(MvcResult r) throws Exception {
		return json.readTree(r.getResponse().getContentAsString()).get("mensaje").asText();
	}
}
