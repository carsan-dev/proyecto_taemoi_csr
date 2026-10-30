package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.config.ValidacionRelojConfig;
import com.taemoi.project.dtos.response.GrupoConAlumnosDTO;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Pasar lista (decisión 324, fase3-pasar-lista.md) contra MySQL, por los dos prefijos: acceso (grupo ajeno, otra escuela,
 * responsable de sede, sesión solo-profesor), validaciones del PUT, lista de un turno (y no del grupo), que coincide con
 * el PDF, resumen y detalle mensual, y los flujos de borrado y anonimización. «Hoy» es el {@code Clock} simulado: el
 * lunes 26/10/2026 a las 20:30 salvo que el test lo cambie.
 *
 * <p>Escuela sintética ({@link EscuelaConSedesDePrueba}) y otra para el aislamiento; cada test crea su propio grupo, así
 * que el orden no importa. Datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AsistenciaIntegrationTest extends MySqlIntegrationTest {

	private static final String LUNES = "2026-10-26";
	private static final Set<String> CLAVES_CLASE = Set.of("turnoId", "fecha", "horaInicio", "horaFin", "pasada",
			"presentes", "total", "alumnos");
	private static final Set<String> CLAVES_ALUMNO = Set.of("id", "nombre", "apellidos", "presente", "deportes");

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;
	@MockitoBean Clock reloj;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	long profA, profB, profMixto, propietario, profOtra;
	String tProfA, tProfB, tProfMixto, tGestor, tPropietario, tResp, tProfOtra, tGestorOtra;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-lista");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-lista-otra");

		profA = soloProfesor(e, "profa");
		asignar(e, profA, e.gA);
		profB = soloProfesor(e, "profb");
		asignar(e, profB, e.gB);
		// Familia + responsable de la sede A + profesor de gB (sede B): la sesión pasa la URL de gestión.
		profMixto = e.cuentaFamilia(usuarios, "mixto");
		membresia(e, profMixto, "PROFESOR");
		responsableDeSede(e, profMixto, e.sA);
		asignar(e, profMixto, e.gB);
		propietario = e.cuenta(usuarios, "propietario");
		membresia(e, propietario, "PROPIETARIO");
		profOtra = soloProfesor(otra, "profotra");
		asignar(otra, profOtra, otra.gA);

		tProfA = e.token(jwtService, usuarios, profA);
		tProfB = e.token(jwtService, usuarios, profB);
		tProfMixto = e.token(jwtService, usuarios, profMixto);
		tGestor = e.token(jwtService, usuarios, e.gestor);
		tPropietario = e.token(jwtService, usuarios, propietario);
		tResp = e.token(jwtService, usuarios, e.respA);
		tProfOtra = otra.token(jwtService, usuarios, profOtra);
		tGestorOtra = otra.token(jwtService, usuarios, otra.gestor);
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

	// ------------------------------------------------------------------ acceso

	@Test
	void grupoAjeno_403_enLasCincoRutas() throws Exception {
		Fijo f = grupoConDosAlumnos(e.sB, profB);
		String base = "/api/profesor/grupos/" + f.grupo;
		assertEquals(403, estado(get(base + "/clases"), tProfA));
		assertEquals(403, estado(head(base + "/clases"), tProfA));
		assertEquals(403, estado(get(base + "/clases/" + f.turno + "/" + LUNES), tProfA));
		assertEquals(403, estado(guardar(base, f.turno, LUNES, f.a), tProfA));
		assertEquals(403, estado(get(base + "/asistencia/resumen?year=2026&month=10"), tProfA));
		assertEquals(403, estado(get(base + "/alumnos/" + f.a + "/asistencia?year=2026&month=10"), tProfA));
		assertEquals(403, estado(get("/api/profesor/grupos/999999999/clases"), tProfA), "inexistente, sin revelarlo");
		assertEquals(0, clasesDe(f.grupo));
	}

	@Test
	void gestionSinPROFESOR_noEntraPorLaRutaDelProfesor() throws Exception {
		Fijo f = grupoConDosAlumnos(e.sA, profA);
		assertEquals(403, estado(get("/api/profesor/grupos/" + f.grupo + "/clases"), tGestor));
		assertEquals(403, estado(guardar("/api/profesor/grupos/" + f.grupo, f.turno, LUNES, f.a), tPropietario));
	}

	@Test
	void otraEscuela_sinAccesoNiFiltracion() throws Exception {
		Fijo f = grupoConDosAlumnos(e.sA, profA);
		String gestion = "/api/grupos/" + f.grupo;
		guardarOk("/api/profesor/grupos/" + f.grupo, f.turno, LUNES, tProfA, f.a);

		// Profesor de otra escuela: 403, como cualquier grupo que no es suyo.
		assertEquals(403, estado(get("/api/profesor/grupos/" + f.grupo + "/clases"), tProfOtra));
		assertEquals(403, estado(get("/api/profesor/grupos/" + f.grupo + "/asistencia/resumen?year=2026&month=10"),
				tProfOtra));
		// Gestor de otra escuela: su alcance total pasa @acceso, pero el grupo no existe en su escuela: 404.
		assertEquals(404, estado(get(gestion + "/clases"), tGestorOtra));
		assertEquals(404, estado(get(gestion + "/clases/" + f.turno + "/" + LUNES), tGestorOtra));
		assertEquals(404, estado(guardar(gestion, f.turno, LUNES), tGestorOtra));
		assertEquals(404, estado(get(gestion + "/asistencia/resumen?year=2026&month=10"), tGestorOtra));
		assertEquals(404, estado(get(gestion + "/alumnos/" + f.a + "/asistencia?year=2026&month=10"), tGestorOtra));
		assertEquals(1, filasPresentes(f.grupo), "no ha cambiado nada");

		// Un turno de otra escuela bajo un grupo propio tampoco existe.
		assertEquals(404, estado(guardar(gestion, otra.tA, LUNES), tGestor));
		assertEquals(404, estado(get(gestion + "/clases/" + otra.tA + "/" + LUNES), tGestor));
		assertEquals(404, estado(get("/api/grupos/" + otra.gA + "/clases"), tGestor));
	}

	@Test
	void turnoDeOtroGrupo_404_sinRevelarNada() throws Exception {
		Fijo f = grupoConDosAlumnos(e.sA, profA);
		Fijo ajeno = grupoConDosAlumnos(e.sB, profB);
		for (String base : List.of("/api/profesor/grupos/" + f.grupo, "/api/grupos/" + f.grupo)) {
			String token = base.contains("profesor") ? tProfA : tGestor;
			MvcResult lectura = peticion(get(base + "/clases/" + ajeno.turno + "/" + LUNES), token);
			assertEquals(404, lectura.getResponse().getStatus());
			MvcResult escritura = peticion(guardar(base, ajeno.turno, LUNES, ajeno.a), token);
			assertEquals(404, escritura.getResponse().getStatus());
			assertEquals("La clase no existe.", mensaje(escritura));
			assertEquals(404, estado(guardar(base, 999999999L, LUNES), token));
		}
		assertEquals(0, clasesDe(ajeno.grupo));
	}

	@Test
	void responsableFueraDeSusSedes_403_yEnLaSuya200() throws Exception {
		Fijo suya = grupoConDosAlumnos(e.sA, profA);
		Fijo ajena = grupoConDosAlumnos(e.sB, profB);
		String fuera = "/api/grupos/" + ajena.grupo;
		assertEquals(403, estado(get(fuera + "/clases"), tResp));
		assertEquals(403, estado(get(fuera + "/clases/" + ajena.turno + "/" + LUNES), tResp));
		assertEquals(403, estado(guardar(fuera, ajena.turno, LUNES, ajena.a), tResp));
		assertEquals(403, estado(get(fuera + "/asistencia/resumen?year=2026&month=10"), tResp));
		assertEquals(403, estado(get(fuera + "/alumnos/" + ajena.a + "/asistencia?year=2026&month=10"), tResp));
		assertEquals(0, clasesDe(ajena.grupo));

		String dentro = "/api/grupos/" + suya.grupo;
		assertEquals(200, estado(get(dentro + "/clases"), tResp));
		assertEquals(200, estado(guardar(dentro, suya.turno, LUNES, suya.a), tResp));
		assertEquals(200, estado(get(dentro + "/asistencia/resumen?year=2026&month=10"), tResp));
		assertEquals(200, estado(get(dentro + "/alumnos/" + suya.a + "/asistencia?year=2026&month=10"), tResp));
	}

	/** Da clase en gB (sede B) y es responsable de la sede A: por gestión solo entra en su sede. */
	@Test
	void responsableQueEsProfesorDeOtraSede_noEntraPorGestion() throws Exception {
		assertEquals(200, estado(get("/api/profesor/grupos/" + e.gB + "/clases"), tProfMixto), "como profesor, sí");
		assertEquals(403, estado(get("/api/grupos/" + e.gB + "/clases"), tProfMixto));
		assertEquals(403, estado(get("/api/grupos/" + e.gB + "/asistencia/resumen?year=2026&month=10"), tProfMixto));
		assertEquals(200, estado(get("/api/grupos/" + e.gA + "/clases"), tProfMixto), "su sede");
	}

	@Test
	void sesionSoloProfesor_noEntraPorGestion_niEnSuGrupo() throws Exception {
		Fijo f = grupoConDosAlumnos(e.sA, profA);
		String base = "/api/grupos/" + f.grupo;
		assertEquals(403, estado(get(base + "/clases"), tProfA));
		assertEquals(403, estado(get(base + "/clases/" + f.turno + "/" + LUNES), tProfA));
		assertEquals(403, estado(guardar(base, f.turno, LUNES, f.a), tProfA));
		assertEquals(403, estado(get(base + "/asistencia/resumen?year=2026&month=10"), tProfA));
		assertEquals(403, estado(get(base + "/alumnos/" + f.a + "/asistencia?year=2026&month=10"), tProfA));
	}

	@Test
	void gestion_grupoInexistente_404() throws Exception {
		assertEquals(404, estado(get("/api/grupos/999999999/clases"), tGestor));
	}

	// ------------------------------------------------------------------ validaciones del PUT

	@Test
	void idFueraDeLaLista_400_sinEscribirNada() throws Exception {
		Fijo f = grupoConDosAlumnos(e.sA, profA);
		long deOtroTurno = alumno("FUERA", f.grupo);
		MvcResult r = peticion(guardar("/api/profesor/grupos/" + f.grupo, f.turno, LUNES, f.a, deOtroTurno), tProfA);
		assertEquals(400, r.getResponse().getStatus());
		assertEquals("Hay alumnos que no están en esta lista.", mensaje(r));
		assertEquals(400, estado(guardar("/api/profesor/grupos/" + f.grupo, f.turno, LUNES, e.aB), tProfA));
		assertEquals(400, estado(guardar("/api/profesor/grupos/" + f.grupo, f.turno, LUNES, otra.aA), tProfA));
		assertEquals(0, clasesDe(f.grupo));
	}

	@Test
	void diaDeLaSemanaEquivocado_400() throws Exception {
		Fijo f = grupoConDosAlumnos(e.sA, profA);
		for (String fecha : List.of("2026-10-25", "2026-10-20")) {
			MvcResult r = peticion(guardar("/api/profesor/grupos/" + f.grupo, f.turno, fecha, f.a), tProfA);
			assertEquals(400, r.getResponse().getStatus(), fecha);
			assertEquals("Ese día no hay clase de este turno.", mensaje(r));
			assertEquals(400, estado(guardar("/api/grupos/" + f.grupo, f.turno, fecha, f.a), tGestor), "gestión, igual");
		}
		assertEquals(400, estado(get("/api/grupos/" + f.grupo + "/clases/" + f.turno + "/2026-10-20"), tGestor));
		assertEquals(0, clasesDe(f.grupo));
	}

	@Test
	void fechaFutura_400_paraTodos() throws Exception {
		Fijo f = grupoConDosAlumnos(e.sA, profA);
		MvcResult r = peticion(guardar("/api/profesor/grupos/" + f.grupo, f.turno, "2026-11-02", f.a), tProfA);
		assertEquals(400, r.getResponse().getStatus());
		assertEquals("No se puede pasar lista de una fecha futura.", mensaje(r));
		assertEquals(400, estado(guardar("/api/grupos/" + f.grupo, f.turno, "2026-11-02", f.a), tGestor));
		assertEquals(0, clasesDe(f.grupo));
	}

	/** «Hoy» es el de Madrid: a las 00:30 del lunes, en UTC aún es domingo, y el lunes ya se puede pasar. */
	@Test
	void hoyEsEuropeMadrid_noUtc() throws Exception {
		ahora("2026-10-26T00:30:00");
		Fijo f = grupoConDosAlumnos(e.sA, profA);
		assertEquals(200, estado(guardar("/api/profesor/grupos/" + f.grupo, f.turno, LUNES, f.a), tProfA));
	}

	@Test
	void fechaNoValida_400_yPresentesObligatorios() throws Exception {
		Fijo f = grupoConDosAlumnos(e.sA, profA);
		String base = "/api/profesor/grupos/" + f.grupo;
		for (String fecha : List.of("no-es-fecha", "2026-02-30", "26-10-2026", "1800-01-06")) {
			assertEquals(400, estado(get(base + "/clases/" + f.turno + "/" + fecha), tProfA), fecha);
			assertEquals(400, estado(guardar(base, f.turno, fecha, f.a), tProfA), fecha);
		}
		String ruta = base + "/clases/" + f.turno + "/" + LUNES;
		for (String cuerpo : List.of("{}", "{\"presentes\":null}", "{\"presentes\":[null]}")) {
			MvcResult r = peticion(put(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo), tProfA);
			assertEquals(400, r.getResponse().getStatus(), cuerpo);
			assertEquals("Falta la lista de presentes.", mensaje(r), cuerpo);
		}
		assertEquals(0, clasesDe(f.grupo));
	}

	@Test
	void turnoSinAlumnos_400_sinCrearLaClase() throws Exception {
		long grupo = grupoNuevo(e.sA, profA);
		long turno = turno(grupo, "Lunes", "18:00", "19:00");
		MvcResult r = peticion(guardar("/api/profesor/grupos/" + grupo, turno, LUNES), tProfA);
		assertEquals(400, r.getResponse().getStatus());
		assertEquals("Este turno no tiene alumnos.", mensaje(r));
		assertEquals(0, clasesDe(grupo));
	}

	@Test
	void turnoConHoraDeMasDeCincoCaracteres_400_no500() throws Exception {
		Fijo f = grupoConDosAlumnos(e.sA, profA);
		jdbc.update("UPDATE turno SET hora_fin = '19:00:00' WHERE id = ?", f.turno);
		MvcResult r = peticion(guardar("/api/profesor/grupos/" + f.grupo, f.turno, LUNES, f.a), tProfA);
		assertEquals(400, r.getResponse().getStatus());
		assertEquals("Este turno no tiene un horario válido.", mensaje(r));
	}

	/** Lunes 26: el lunes 12 (14 días) entra; el domingo 11 (15) y el lunes 5 (21), no. Gestión, sin límite. */
	@Test
	void limiteDe14Dias_paraProfesor_ySinLimiteParaGestion() throws Exception {
		Fijo f = grupoConDosAlumnos(e.sA, profA);
		long domingo = turno(f.grupo, "Domingo", "10:00", "11:00");
		e.enTurno(domingo, f.a);
		String profesor = "/api/profesor/grupos/" + f.grupo;
		String gestion = "/api/grupos/" + f.grupo;

		assertEquals(200, estado(guardar(profesor, f.turno, "2026-10-12", f.a), tProfA), "14 días, incluido");
		MvcResult quince = peticion(guardar(profesor, domingo, "2026-10-11", f.a), tProfA);
		assertEquals(400, quince.getResponse().getStatus(), "15 días");
		assertEquals("Solo puedes pasar lista de los últimos 14 días.", mensaje(quince));
		assertEquals(400, estado(guardar(profesor, f.turno, "2026-10-05", f.a), tProfA), "21 días");

		assertEquals(200, estado(guardar(gestion, domingo, "2026-10-11", f.a), tGestor));
		assertEquals(200, estado(guardar(gestion, f.turno, "2026-10-05", f.a), tGestor));
		assertEquals(200, estado(guardar(gestion, f.turno, "2025-09-01", f.a), tGestor), "un año atrás");
		assertEquals(200, estado(guardar(gestion, f.turno, "2026-10-05", f.a), tResp), "el responsable también");
	}

	// ------------------------------------------------------------------ guardar

	@Test
	void putIdempotente_yCorreccion_unaFilaPorAlumno_yMarcas() throws Exception {
		Fijo f = grupoConDosAlumnos(e.sA, profA);
		String base = "/api/profesor/grupos/" + f.grupo;
		ahora("2026-10-26T18:30:00");

		JsonNode primera = leer(guardar(base, f.turno, LUNES, f.a), tProfA, 200);
		assertEquals(CLAVES_CLASE, campos(primera));
		assertEquals(f.turno, primera.get("turnoId").asLong());
		assertEquals(LUNES, primera.get("fecha").asText());
		assertEquals("18:00", primera.get("horaInicio").asText());
		assertEquals("19:00", primera.get("horaFin").asText());
		assertFalse(primera.get("pasada").asBoolean(), "aún no ha terminado");
		assertEquals(1, primera.get("presentes").asInt());
		assertEquals(2, primera.get("total").asInt());
		assertEquals(2, primera.get("alumnos").asInt());
		var creada = jdbc.queryForMap("SELECT * FROM clase_asistencia WHERE grupo_id = ?", f.grupo);
		assertEquals(profA, ((Number) creada.get("registrado_por_usuario_id")).longValue());
		assertEquals(e.org, ((Number) creada.get("organizacion_id")).longValue());
		assertNull(creada.get("modificado_en"), "la primera vez no hay modificación");
		assertNull(creada.get("modificado_por_usuario_id"));
		assertEquals(2, filas(f.grupo), "una fila por cada alumno, presente o no");
		assertEquals(1, filasPresentes(f.grupo));

		// Lo mismo otra vez: nada cambia salvo la marca.
		ahora("2026-10-26T18:40:00");
		JsonNode repetida = leer(guardar(base, f.turno, LUNES, f.a), tProfA, 200);
		assertEquals(primera, repetida);
		assertEquals(1, clasesDe(f.grupo));
		assertEquals(2, filas(f.grupo));
		assertEquals(1, filasPresentes(f.grupo));

		// Corrige gestión: gana la última escritura, con su marca.
		ahora("2026-10-27T09:00:00");
		JsonNode corregida = leer(guardar("/api/grupos/" + f.grupo, f.turno, LUNES, f.b), tGestor, 200);
		assertEquals(1, corregida.get("presentes").asInt());
		assertTrue(corregida.get("pasada").asBoolean());
		assertEquals(1, clasesDe(f.grupo));
		var modificada = jdbc.queryForMap("SELECT * FROM clase_asistencia WHERE grupo_id = ?", f.grupo);
		assertEquals(e.gestor, ((Number) modificada.get("modificado_por_usuario_id")).longValue());
		assertEquals(profA, ((Number) modificada.get("registrado_por_usuario_id")).longValue(), "quien la registró sigue");
		assertEquals(creada.get("registrado_en"), modificada.get("registrado_en"));
		assertEquals(LocalDateTime.parse("2026-10-27T09:00:00"), modificada.get("modificado_en"));
		assertFalse(presenteEnLista(leer(get("/api/grupos/" + f.grupo + "/clases/" + f.turno + "/" + LUNES), tGestor, 200),
				f.a));
		assertTrue(presenteEnLista(leer(get(base + "/clases/" + f.turno + "/" + LUNES), tProfA, 200), f.b));

		// Vacía: todos faltan, y siguen contando.
		assertEquals(0, leer(guardar(base, f.turno, LUNES), tProfA, 200).get("presentes").asInt());
		assertEquals(2, filas(f.grupo));
		assertEquals(0, filasPresentes(f.grupo));
	}

	@Test
	void idsRepetidosEnElCuerpo_cuentanUnaVez() throws Exception {
		Fijo f = grupoConDosAlumnos(e.sA, profA);
		JsonNode clase = leer(guardar("/api/profesor/grupos/" + f.grupo, f.turno, LUNES, f.a, f.a), tProfA, 200);
		assertEquals(1, clase.get("presentes").asInt());
		assertEquals(1, filasPresentes(f.grupo));
	}

	@Test
	void unaFilaDeAuditoriaPorPut_yUltimaCorreccion() throws Exception {
		Fijo f = grupoConDosAlumnos(e.sA, profA);
		String base = "/api/profesor/grupos/" + f.grupo;
		String ruta = base + "/clases/" + f.turno + "/" + LUNES;
		leer(guardar(base, f.turno, LUNES, f.a), tProfA, 200);
		leer(guardar(base, f.turno, LUNES, f.a), tProfA, 200);
		List<String> resumenes = jdbc.queryForList(
				"SELECT resumen FROM auditoria_evento WHERE endpoint = ? ORDER BY id", String.class, ruta);
		assertEquals(2, resumenes.size(), resumenes.toString());
		long clase = jdbc.queryForObject("SELECT id FROM clase_asistencia WHERE grupo_id = ?", Long.class, f.grupo);
		String cuerpo = "clase=#" + clase + " grupo=#" + f.grupo + " turno=#" + f.turno;
		assertEquals("PROFESOR ASISTENCIA_REGISTRADA " + cuerpo, resumenes.get(0));
		assertEquals("PROFESOR ASISTENCIA_CORREGIDA " + cuerpo, resumenes.get(1));
		String detalle = jdbc.queryForObject(
				"SELECT COALESCE(payload_json, '') FROM auditoria_evento WHERE endpoint = ? ORDER BY id LIMIT 1", String.class,
				ruta);
		assertFalse(detalle.contains(String.valueOf(f.a)), "el 2xx no guarda el cuerpo");
	}

	/** Varios PUT simultáneos de la primera lista: todos 200, una clase, una fila por alumno. Nunca 500. */
	@Test
	void carreraSobreLaUnique_sin500() throws Exception {
		Fijo f = grupoConDosAlumnos(e.sA, profA);
		String base = "/api/profesor/grupos/" + f.grupo;
		int hilos = 6;
		ExecutorService pool = Executors.newFixedThreadPool(hilos);
		CountDownLatch salida = new CountDownLatch(1);
		try {
			List<Future<Integer>> resultados = new ArrayList<>();
			for (int i = 0; i < hilos; i++) {
				long presente = i % 2 == 0 ? f.a : f.b;
				Callable<Integer> tarea = () -> {
					salida.await();
					return estado(guardar(base, f.turno, LUNES, presente), tProfA);
				};
				resultados.add(pool.submit(tarea));
			}
			salida.countDown();
			for (Future<Integer> resultado : resultados) {
				assertEquals(200, resultado.get());
			}
		} finally {
			pool.shutdownNow();
		}
		assertEquals(1, clasesDe(f.grupo));
		assertEquals(2, filas(f.grupo));
		assertEquals(1, filasPresentes(f.grupo), "gana una escritura entera, sin mezclas");
	}

	// ------------------------------------------------------------------ lista

	/** Mismo fixture para la lista y el PDF por turnos: salen los mismos alumnos y en el mismo orden. */
	@Test
	void laListaCoincideConElPdf() throws Exception {
		long grupo = grupoNuevo(e.sA, profA);
		long turno = turno(grupo, "Lunes", "18:00", "19:00");
		String marca = "L" + System.nanoTime();
		long zeta = alumnoEnTurno("ZZZ" + marca, grupo, turno);
		long alfa = alumnoEnTurno("AAA" + marca, grupo, turno);
		long media = alumnoEnTurno("MMM" + marca, grupo, turno);

		long baja = alumnoEnTurno("BAJA" + marca, grupo, turno);
		jdbc.update("UPDATE alumno SET activo = FALSE WHERE id = ?", baja);
		long deporteInactivo = alumnoEnTurno("DEPINACTIVO" + marca, grupo, turno);
		jdbc.update("UPDATE alumno_deporte SET activo = FALSE WHERE alumno_id = ?", deporteInactivo);
		long sinDeporte = e.alumno("SINDEPORTE" + marca + e.marca, e.sA, true);
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", grupo, sinDeporte);
		e.enTurno(turno, sinDeporte);
		long soloTurno = e.alumno("SOLOTURNO" + marca + e.marca, e.sA, true);
		e.taekwondo(soloTurno, false);
		e.enTurno(turno, soloTurno);
		long soloGrupo = alumno("SOLOGRUPO" + marca, grupo);

		// alumno() añade la marca de la escuela al nombre.
		String sufijo = marca + e.marca;
		List<String> enApi = new ArrayList<>();
		leer(get("/api/profesor/grupos/" + grupo + "/clases/" + turno + "/" + LUNES), tProfA, 200).get("alumnos")
				.forEach(a -> enApi.add(a.get("nombre").asText()));
		assertEquals(List.of("AAA" + sufijo, "MMM" + sufijo, "ZZZ" + sufijo), enApi, "nombre, apellidos");

		MvcResult pdf = peticion(get("/api/profesor/grupos/" + grupo + "/asistencia?year=2026&month=10"), tProfA);
		assertEquals(200, pdf.getResponse().getStatus());
		String texto = texto(pdf.getResponse().getContentAsByteArray());
		List<String> candidatos = List.of("ZZZ", "AAA", "MMM", "BAJA", "DEPINACTIVO", "SINDEPORTE", "SOLOTURNO", "SOLOGRUPO");
		List<String> enPdf = candidatos.stream().map(c -> c + sufijo).filter(texto::contains)
				.sorted(Comparator.comparingInt(texto::indexOf)).toList();
		assertEquals(enPdf, enApi, "los mismos y en el mismo orden que el PDF");

		leer(guardar("/api/profesor/grupos/" + grupo, turno, LUNES, alfa), tProfA, 200);
		assertEquals(3, filas(grupo));
		assertEquals(0, jdbc.queryForObject(
				"SELECT COUNT(*) FROM asistencia_alumno WHERE alumno_id IN (?, ?, ?, ?, ?)", Long.class, baja,
				deporteInactivo, sinDeporte, soloTurno, soloGrupo));
		assertTrue(zeta != media);
	}

	@Test
	void lista_formaJson_sinSaludNiContacto_yDeportesComoElListado() throws Exception {
		Fijo f = grupoConDosAlumnos(e.sA, profA);
		JsonNode lista = leer(get("/api/profesor/grupos/" + f.grupo + "/clases/" + f.turno + "/" + LUNES), tProfA, 200);
		assertEquals(Set.of("clase", "alumnos"), campos(lista));
		assertEquals(CLAVES_CLASE, campos(lista.get("clase")));
		assertNull(jsonNulo(lista.get("clase").get("presentes")), "sin lista, presentes null");
		assertNull(jsonNulo(lista.get("clase").get("total")), "sin lista, total null");
		assertEquals(2, lista.get("clase").get("alumnos").asInt());
		assertEquals(2, lista.get("alumnos").size());

		JsonNode alumno = lista.get("alumnos").get(0);
		assertEquals(CLAVES_ALUMNO, campos(alumno));
		assertFalse(alumno.get("presente").asBoolean(), "sin fila, false");
		JsonNode listado = leer(get("/api/profesor/grupos/" + f.grupo + "/alumnos"), tProfA, 200);
		for (JsonNode delListado : listado) {
			JsonNode enLista = buscar(lista.get("alumnos"), delListado.get("id").asLong());
			assertEquals(delListado.get("deportes"), enLista.get("deportes"), "misma forma y datos que el listado");
		}
		assertEquals(1, alumno.get("deportes").size());
		assertTrue(alumno.get("deportes").get(0).get("delGrupo").asBoolean());
	}

	@Test
	void elAlumnoQueCausaBaja_sigueEnLaListaGuardada_ySaleDeLaNueva() throws Exception {
		Fijo f = grupoConDosAlumnos(e.sA, profA);
		String base = "/api/profesor/grupos/" + f.grupo;
		ahora("2026-10-26T20:30:00");
		leer(guardar(base, f.turno, LUNES, f.a), tProfA, 200);

		assertEquals(200, estado(put("/api/alumnos/" + f.a + "/baja"), tGestor));

		JsonNode guardada = leer(get(base + "/clases/" + f.turno + "/" + LUNES), tProfA, 200);
		assertEquals(2, guardada.get("alumnos").size(), "lista actual ∪ filas guardadas");
		assertTrue(presenteEnLista(guardada, f.a));
		assertEquals(1, guardada.get("clase").get("alumnos").asInt(), "la lista actual ya no lo tiene");
		assertEquals(2, guardada.get("clase").get("total").asInt());
		// Corregir la lista guardada no le quita la fila.
		assertEquals(200, estado(guardar(base, f.turno, LUNES, f.a, f.b), tProfA));
		assertEquals(2, filasPresentes(f.grupo));

		ahora("2026-11-02T20:30:00");
		JsonNode nueva = leer(get(base + "/clases/" + f.turno + "/2026-11-02"), tProfA, 200);
		assertEquals(1, nueva.get("alumnos").size());
		assertEquals(f.b, nueva.get("alumnos").get(0).get("id").asLong());
		assertEquals(400, estado(guardar(base, f.turno, "2026-11-02", f.a), tProfA), "en una clase nueva no está");
	}

	/** Cambia de turno dentro del grupo: su fila del turno antiguo sigue contando y el turno antiguo ya no le sale. */
	@Test
	void quitarAlAlumnoDeUnTurno_sigueEnLaListaGuardada() throws Exception {
		Fijo f = grupoConDosAlumnos(e.sA, profA);
		String base = "/api/profesor/grupos/" + f.grupo;
		long martes = turno(f.grupo, "Martes", "18:00", "19:00");
		e.enTurno(martes, f.a);
		leer(guardar(base, f.turno, LUNES, f.a), tProfA, 200);
		assertEquals(200, estado(delete("/api/alumnos/" + f.a + "/turnos/" + f.turno), tGestor));

		JsonNode lista = leer(get(base + "/clases/" + f.turno + "/" + LUNES), tProfA, 200);
		assertEquals(2, lista.get("alumnos").size());
		assertEquals(1, lista.get("clase").get("alumnos").asInt());
		JsonNode detalle = leer(get(base + "/alumnos/" + f.a + "/asistencia?year=2026&month=10"), tProfA, 200);
		assertEquals(1, detalle.get("presentes").asInt());
		assertEquals(1, detalle.get("clases").asInt());
		assertEquals(List.of("2026-10-06 18:00 SIN_LISTA", "2026-10-13 18:00 SIN_LISTA", "2026-10-20 18:00 SIN_LISTA",
				LUNES + " 18:00 PRESENTE"), dias(detalle));
	}

	/**
	 * Fuera del grupo (quitarlo de su único turno también lo saca del grupo): su fila sigue en la lista guardada y en el
	 * resumen, y el detalle da 404 porque el alumno debe ser del grupo.
	 */
	@Test
	void quitarAlAlumnoDelGrupo_sigueEnLaListaYElResumen_yElDetalleDa404() throws Exception {
		Fijo f = grupoConDosAlumnos(e.sA, profA);
		String base = "/api/profesor/grupos/" + f.grupo;
		leer(guardar(base, f.turno, LUNES, f.a), tProfA, 200);
		assertEquals(200, estado(delete("/api/alumnos/" + f.a + "/turnos/" + f.turno), tGestor));

		JsonNode lista = leer(get(base + "/clases/" + f.turno + "/" + LUNES), tProfA, 200);
		assertEquals(Set.of(f.a, f.b), ids(lista));
		assertEquals(1, lista.get("clase").get("alumnos").asInt());
		JsonNode resumen = leer(get(base + "/asistencia/resumen?year=2026&month=10"), tProfA, 200);
		assertEquals(1, delAlumno(resumen, f.a).get("clases").asInt());
		assertEquals(404, estado(get(base + "/alumnos/" + f.a + "/asistencia?year=2026&month=10"), tProfA));
	}

	/** Una lista guardada con otro grupo (turno movido) no se mezcla: 409, no 500. */
	@Test
	void turnoMovidoAOtroGrupo_laListaAntiguaNoSeMezcla_409() throws Exception {
		Fijo origen = grupoConDosAlumnos(e.sA, profA);
		Fijo destino = grupoConDosAlumnos(e.sA, profA);
		leer(guardar("/api/profesor/grupos/" + origen.grupo, origen.turno, LUNES, origen.a), tProfA, 200);
		jdbc.update("UPDATE turno SET grupo_id = ? WHERE id = ?", destino.grupo, origen.turno);
		e.enTurno(origen.turno, destino.a);

		assertEquals(409, estado(guardar("/api/profesor/grupos/" + destino.grupo, origen.turno, LUNES, destino.a), tProfA));
		assertEquals(1, clasesDe(origen.grupo));
		assertEquals(0, clasesDe(destino.grupo));
		assertEquals(200, estado(get("/api/profesor/grupos/" + destino.grupo + "/clases/" + origen.turno + "/" + LUNES),
				tProfA));
	}

	// ------------------------------------------------------------------ una lista por turno, no por grupo

	/**
	 * Grupo con turnos de lunes y miércoles; un alumno solo en el del lunes. Solo le cuentan sus lunes: el miércoles no le
	 * suma falta ni le sale en el detalle, y {@code Clase.alumnos} y {@code Clase.total} son los de cada lista.
	 */
	@Test
	void alumnoDeUnSoloTurno_soloCuentanSusLunes() throws Exception {
		ahora("2026-10-29T20:30:00"); // Jueves: el lunes 26 y el miércoles 28 ya han pasado.
		long grupo = grupoNuevo(e.sA, profA);
		long lunes = turno(grupo, "Lunes", "18:00", "19:00");
		long miercoles = turno(grupo, "Miércoles", "18:00", "19:00");
		long soloLunes = alumnoEnTurno("SOLOLUNES", grupo, lunes);
		long enLosDos = alumnoEnTurno("ENLOSDOS", grupo, lunes);
		e.enTurno(miercoles, enLosDos);
		alumno("SINTURNO", grupo); // Del grupo, en ningún turno: el grupo tiene 3 alumnos.
		String base = "/api/profesor/grupos/" + grupo;

		// 1. Sale en la lista del lunes y no en la del miércoles.
		assertEquals(Set.of(soloLunes, enLosDos), ids(leer(get(base + "/clases/" + lunes + "/" + LUNES), tProfA, 200)));
		assertEquals(Set.of(enLosDos), ids(leer(get(base + "/clases/" + miercoles + "/2026-10-28"), tProfA, 200)));
		assertEquals(400, estado(guardar(base, miercoles, "2026-10-28", soloLunes), tProfA), "no está en esa lista");

		leer(guardar(base, lunes, "2026-10-19", new long[0]), tProfA, 200);
		leer(guardar(base, lunes, LUNES, soloLunes), tProfA, 200);
		JsonNode deMiercoles = leer(guardar(base, miercoles, "2026-10-28", enLosDos), tProfA, 200);

		// 4. Clase.alumnos y Clase.total son los de esa lista, no los del grupo.
		assertEquals(1, deMiercoles.get("alumnos").asInt());
		assertEquals(1, deMiercoles.get("total").asInt());
		JsonNode clases = leer(get(base + "/clases"), tProfA, 200);
		JsonNode claseLunes = claseDe(clases.get("recientes"), lunes, LUNES);
		assertEquals(2, claseLunes.get("alumnos").asInt());
		assertEquals(2, claseLunes.get("total").asInt());
		assertEquals(1, claseLunes.get("presentes").asInt());
		JsonNode claseMiercoles = claseDe(clases.get("recientes"), miercoles, "2026-10-28");
		assertEquals(1, claseMiercoles.get("alumnos").asInt());
		assertEquals(1, claseMiercoles.get("total").asInt());
		assertEquals(1, claseMiercoles.get("presentes").asInt());
		JsonNode miercolesSinLista = claseDe(clases.get("recientes"), miercoles, "2026-10-21");
		assertEquals(1, miercolesSinLista.get("alumnos").asInt());
		assertNull(jsonNulo(miercolesSinLista.get("total")));

		// 2. Resumen: clases solo cuenta sus lunes; el miércoles pasado no le suma falta.
		JsonNode resumen = leer(get(base + "/asistencia/resumen?year=2026&month=10"), tProfA, 200);
		assertEquals(2, resumen.size());
		JsonNode suyo = delAlumno(resumen, soloLunes);
		assertEquals(2, suyo.get("clases").asInt(), "lunes 19 y 26");
		assertEquals(1, suyo.get("presentes").asInt());
		JsonNode delOtro = delAlumno(resumen, enLosDos);
		assertEquals(3, delOtro.get("clases").asInt(), "sus dos lunes y su miércoles");
		assertEquals(1, delOtro.get("presentes").asInt());

		// 3. Detalle: dias solo trae sus lunes.
		JsonNode detalle = leer(get(base + "/alumnos/" + soloLunes + "/asistencia?year=2026&month=10"), tProfA, 200);
		assertEquals(List.of("2026-10-05 18:00 SIN_LISTA", "2026-10-12 18:00 SIN_LISTA", "2026-10-19 18:00 FALTA",
				"2026-10-26 18:00 PRESENTE"), dias(detalle));
		assertEquals(1, detalle.get("presentes").asInt());
		assertEquals(2, detalle.get("clases").asInt());
		JsonNode detalleDelOtro = leer(get(base + "/alumnos/" + enLosDos + "/asistencia?year=2026&month=10"), tProfA, 200);
		assertEquals(8, detalleDelOtro.get("dias").size(), "sus 4 lunes y sus 4 miércoles hasta hoy");
		assertEquals(3, detalleDelOtro.get("clases").asInt());
	}

	// ------------------------------------------------------------------ resumen y detalle

	@Test
	void resumenYDetalle_lasClasesSinListaNoCuentan() throws Exception {
		Fijo f = grupoConDosAlumnos(e.sA, profA);
		String base = "/api/profesor/grupos/" + f.grupo;
		leer(guardar(base, f.turno, "2026-10-19", f.a), tProfA, 200);
		leer(guardar(base, f.turno, "2026-10-26", f.b), tProfA, 200);
		// Otro mes: no suma.
		leer(guardar("/api/grupos/" + f.grupo, f.turno, "2026-09-28", f.a), tGestor, 200);
		// Otro grupo con el mismo alumno: no suma.
		long otroGrupo = grupoNuevo(e.sA, profA);
		long otroTurno = turno(otroGrupo, "Lunes", "20:00", "21:00");
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", otroGrupo, f.a);
		e.enTurno(otroTurno, f.a);
		leer(guardar("/api/profesor/grupos/" + otroGrupo, otroTurno, LUNES, f.a), tProfA, 200);

		JsonNode resumen = leer(get(base + "/asistencia/resumen?year=2026&month=10"), tProfA, 200);
		assertEquals(2, resumen.size());
		assertEquals(Set.of("alumnoId", "presentes", "clases"), campos(resumen.get(0)));
		assertEquals(1, delAlumno(resumen, f.a).get("presentes").asInt());
		assertEquals(2, delAlumno(resumen, f.a).get("clases").asInt(), "dos con lista; 5 y 12 de octubre, sin lista");

		JsonNode detalle = leer(get(base + "/alumnos/" + f.a + "/asistencia?year=2026&month=10"), tProfA, 200);
		assertEquals(Set.of("presentes", "clases", "dias"), campos(detalle));
		assertEquals(1, detalle.get("presentes").asInt());
		assertEquals(2, detalle.get("clases").asInt(), "las mismas cifras que el resumen");
		assertEquals(List.of("2026-10-05 18:00 SIN_LISTA", "2026-10-12 18:00 SIN_LISTA", "2026-10-19 18:00 PRESENTE",
				"2026-10-26 18:00 FALTA"), dias(detalle));

		// Hasta hoy: el lunes 2 de noviembre aún no sale. Mes sin clases ni filas: vacío.
		assertEquals(List.of(), dias(leer(get(base + "/alumnos/" + f.a + "/asistencia?year=2026&month=11"), tProfA, 200)));
		assertEquals(0, leer(get(base + "/asistencia/resumen?year=2026&month=11"), tProfA, 200).size());

		// Mismas respuestas por gestión.
		assertEquals(resumen, leer(get("/api/grupos/" + f.grupo + "/asistencia/resumen?year=2026&month=10"), tGestor, 200));
		assertEquals(detalle, leer(get("/api/grupos/" + f.grupo + "/alumnos/" + f.a + "/asistencia?year=2026&month=10"),
				tGestor, 200));
	}

	@Test
	void alumnoDeFueraDelGrupo_404() throws Exception {
		Fijo f = grupoConDosAlumnos(e.sA, profA);
		String ruta = "/alumnos/" + e.aB + "/asistencia?year=2026&month=10";
		assertEquals(404, estado(get("/api/profesor/grupos/" + f.grupo + ruta), tProfA));
		assertEquals(404, estado(get("/api/grupos/" + f.grupo + ruta), tGestor));
		assertEquals(404, estado(get("/api/grupos/" + f.grupo + "/alumnos/" + otra.aA + "/asistencia?year=2026&month=10"),
				tGestor), "de otra escuela");
	}

	@Test
	void anioOMesNoValidos_400() throws Exception {
		Fijo f = grupoConDosAlumnos(e.sA, profA);
		for (String base : List.of("/api/profesor/grupos/" + f.grupo, "/api/grupos/" + f.grupo)) {
			String token = base.contains("profesor") ? tProfA : tGestor;
			for (String consulta : List.of("?year=2026&month=13", "?year=2026&month=0", "?year=0&month=9",
					"?year=99999&month=9", "?year=2026", "?month=9", "?year=abc&month=9")) {
				assertEquals(400, estado(get(base + "/asistencia/resumen" + consulta), token), consulta);
				assertEquals(400, estado(get(base + "/alumnos/" + f.a + "/asistencia" + consulta), token), consulta);
			}
		}
		assertEquals("El año o el mes no son válidos.",
				mensaje(peticion(get("/api/grupos/" + f.grupo + "/asistencia/resumen?year=2026&month=13"), tGestor)));
	}

	// ------------------------------------------------------------------ GET /clases

	@Test
	void clases_hoyRecientesYProxima() throws Exception {
		ahora("2026-10-26T18:30:00"); // Lunes, durante la clase de las 18:00.
		long grupo = grupoNuevo(e.sA, profA);
		long temprano = turno(grupo, "Lunes", "10:00", "11:00");
		long enCurso = turno(grupo, "lunes", "18:00", "19:00");
		long tarde = turno(grupo, "Lunes", "20:00", "21:00");
		long martes = turno(grupo, "Martes", "17:00", "18:00");
		turno(grupo, "Festivo", "09:00", "10:00"); // Día desconocido: se descarta, como en el PDF.
		long conAlumno = alumnoEnTurno("CLASES", grupo, enCurso);
		String base = "/api/profesor/grupos/" + grupo;
		leer(guardar(base, enCurso, LUNES, conAlumno), tProfA, 200);

		JsonNode r = leer(get(base + "/clases"), tProfA, 200);
		assertEquals(Set.of("hoy", "recientes", "proxima"), campos(r));

		List<Long> deHoy = new ArrayList<>();
		r.get("hoy").forEach(c -> deHoy.add(c.get("turnoId").asLong()));
		assertEquals(List.of(temprano, enCurso, tarde), deHoy, "por hora");
		assertTrue(r.get("hoy").get(0).get("pasada").asBoolean(), "la de las 10 ya terminó");
		assertFalse(r.get("hoy").get(1).get("pasada").asBoolean(), "la de las 18 sigue");
		assertFalse(r.get("hoy").get(2).get("pasada").asBoolean());
		assertEquals(1, r.get("hoy").get(1).get("presentes").asInt());
		assertEquals(1, r.get("hoy").get(1).get("total").asInt());
		assertEquals(1, r.get("hoy").get(1).get("alumnos").asInt());
		assertNull(jsonNulo(r.get("hoy").get(0).get("presentes")));
		assertEquals(0, r.get("hoy").get(0).get("alumnos").asInt(), "turno sin alumnos");

		// Lunes 12, 19 y 26 (3 × 3 turnos) y martes 13 y 20; de más nueva a más antigua y por hora dentro del día.
		List<String> recientes = new ArrayList<>();
		r.get("recientes").forEach(c -> recientes.add(c.get("fecha").asText() + " " + c.get("horaInicio").asText()));
		assertEquals(List.of("2026-10-26 10:00", "2026-10-26 18:00", "2026-10-26 20:00", "2026-10-20 17:00",
				"2026-10-19 10:00", "2026-10-19 18:00", "2026-10-19 20:00", "2026-10-13 17:00", "2026-10-12 10:00",
				"2026-10-12 18:00", "2026-10-12 20:00"), recientes);
		assertEquals(martes, r.get("recientes").get(3).get("turnoId").asLong());
		assertTrue(r.get("recientes").get(3).get("pasada").asBoolean());

		assertEquals("2026-10-26", r.get("proxima").get("fecha").asText());
		assertEquals("20:00", r.get("proxima").get("horaInicio").asText(), "la primera que no ha empezado");
		assertEquals(Set.of("fecha", "horaInicio"), campos(r.get("proxima")));

		// Gestión ve lo mismo.
		assertEquals(r, leer(get("/api/grupos/" + grupo + "/clases"), tGestor, 200));

		// Ya acabadas las de hoy: la próxima es la del martes.
		ahora("2026-10-26T21:30:00");
		JsonNode noche = leer(get(base + "/clases"), tProfA, 200);
		assertEquals("2026-10-27", noche.get("proxima").get("fecha").asText());
		assertEquals("17:00", noche.get("proxima").get("horaInicio").asText());
		assertTrue(noche.get("hoy").get(2).get("pasada").asBoolean());
	}

	@Test
	void clases_grupoSinTurnos_proximaNull_yListasVacias() throws Exception {
		long grupo = grupoNuevo(e.sA, profA);
		JsonNode r = leer(get("/api/profesor/grupos/" + grupo + "/clases"), tProfA, 200);
		assertEquals(0, r.get("hoy").size());
		assertEquals(0, r.get("recientes").size());
		assertNull(jsonNulo(r.get("proxima")));
	}

	/** La hora de una lista guardada es la de entonces, y sigue en recientes aunque el turno cambie de día. */
	@Test
	void clases_cambiarElTurnoNoCambiaLaListaGuardada() throws Exception {
		Fijo f = grupoConDosAlumnos(e.sA, profA);
		String base = "/api/profesor/grupos/" + f.grupo;
		leer(guardar(base, f.turno, "2026-10-19", f.a), tProfA, 200);
		jdbc.update("UPDATE turno SET dia_semana = 'Martes', hora_inicio = '09:00', hora_fin = '10:00' WHERE id = ?",
				f.turno);

		JsonNode guardada = claseDe(leer(get(base + "/clases"), tProfA, 200).get("recientes"), f.turno, "2026-10-19");
		assertEquals("18:00", guardada.get("horaInicio").asText());
		assertEquals("19:00", guardada.get("horaFin").asText());
		assertEquals(1, guardada.get("presentes").asInt());
		assertEquals("18:00",
				leer(get(base + "/clases/" + f.turno + "/2026-10-19"), tProfA, 200).get("clase").get("horaInicio").asText());
	}

	// ------------------------------------------------------------------ borrados y anonimización

	@Test
	void borrarTurno_conservaElHistorico() throws Exception {
		Fijo f = grupoConDosAlumnos(e.sA, profA);
		String base = "/api/profesor/grupos/" + f.grupo;
		leer(guardar(base, f.turno, LUNES, f.a), tProfA, 200);
		jdbc.update("DELETE FROM alumno_turno WHERE turno_id = ?", f.turno);

		assertEquals(204, estado(delete("/api/turnos/" + f.turno), tGestor));

		assertEquals(1, clasesDe(f.grupo));
		assertEquals(0, jdbc.queryForObject(
				"SELECT COUNT(*) FROM clase_asistencia WHERE grupo_id = ? AND turno_id IS NOT NULL", Long.class, f.grupo));
		assertEquals(2, filas(f.grupo));
		JsonNode resumen = leer(get(base + "/asistencia/resumen?year=2026&month=10"), tProfA, 200);
		assertEquals(1, delAlumno(resumen, f.a).get("presentes").asInt());
		assertEquals(1, delAlumno(resumen, f.b).get("clases").asInt());
		assertEquals(List.of(LUNES + " 18:00 PRESENTE"),
				dias(leer(get(base + "/alumnos/" + f.a + "/asistencia?year=2026&month=10"), tProfA, 200)));
		assertEquals(0, leer(get(base + "/clases"), tProfA, 200).get("recientes").size(), "sin turno no se abre");
		assertEquals(404, estado(guardar(base, f.turno, LUNES, f.a), tProfA));

		// Al rehacerlo a la misma hora, el día con fila no sale además como sin lista.
		long rehecho = turno(f.grupo, "Lunes", "18:00", "19:00");
		e.enTurno(rehecho, f.a);
		assertEquals(List.of("2026-10-05 18:00 SIN_LISTA", "2026-10-12 18:00 SIN_LISTA", "2026-10-19 18:00 SIN_LISTA",
				LUNES + " 18:00 PRESENTE"),
				dias(leer(get(base + "/alumnos/" + f.a + "/asistencia?year=2026&month=10"), tProfA, 200)));
		// Y se puede pasar lista otra vez sobre el turno nuevo: varias filas sin turno no chocan.
		assertEquals(200, estado(guardar(base, rehecho, LUNES, f.a), tProfA));
	}

	@Test
	void borrarGrupo_seLlevaSusListas_ySigueFuncionando() throws Exception {
		Fijo f = grupoConDosAlumnos(e.sA, profA);
		leer(guardar("/api/profesor/grupos/" + f.grupo, f.turno, LUNES, f.a), tProfA, 200);
		jdbc.update("DELETE FROM alumno_turno WHERE turno_id = ?", f.turno);
		jdbc.update("DELETE FROM asignacion_profesor WHERE grupo_id = ?", f.grupo);

		assertEquals(204, estado(delete("/api/grupos/" + f.grupo), tGestor));

		assertEquals(0, clasesDe(f.grupo));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM asistencia_alumno WHERE alumno_id IN (?, ?)", Long.class,
				f.a, f.b));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM grupo WHERE id = ?", Long.class, f.grupo));
	}

	@Test
	void borrarAlumno_seLlevaSusFilas_yLaListaSigue() throws Exception {
		Fijo f = grupoConDosAlumnos(e.sA, profA);
		String base = "/api/profesor/grupos/" + f.grupo;
		leer(guardar(base, f.turno, LUNES, f.a), tProfA, 200);

		assertEquals(200, estado(delete("/api/alumnos/" + f.a), tPropietario));

		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM alumno WHERE id = ?", Long.class, f.a));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM asistencia_alumno WHERE alumno_id = ?", Long.class, f.a));
		assertEquals(1, clasesDe(f.grupo));
		JsonNode lista = leer(get(base + "/clases/" + f.turno + "/" + LUNES), tProfA, 200);
		assertEquals(Set.of(f.b), ids(lista));
		assertEquals(1, lista.get("clase").get("total").asInt());
	}

	@Test
	void anonimizar_quitaSuAsistencia_yNoSaleEnNingunaLista() throws Exception {
		Fijo f = grupoConDosAlumnos(e.sA, profA);
		String base = "/api/profesor/grupos/" + f.grupo;
		leer(guardar(base, f.turno, LUNES, f.a, f.b), tProfA, 200);

		assertEquals(200, estado(post("/api/alumnos/" + f.a + "/anonimizar"), tPropietario));

		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM alumno WHERE id = ? AND anonimizado_en IS NOT NULL",
				Long.class, f.a), "sigue la ficha, anonimizada");
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM asistencia_alumno WHERE alumno_id = ?", Long.class, f.a));
		JsonNode guardada = leer(get(base + "/clases/" + f.turno + "/" + LUNES), tProfA, 200);
		assertEquals(Set.of(f.b), ids(guardada));
		assertEquals(1, guardada.get("clase").get("total").asInt());
		assertEquals(1, guardada.get("clase").get("alumnos").asInt());
		assertFalse(leer(get(base + "/asistencia/resumen?year=2026&month=10"), tProfA, 200).toString()
				.contains("\"alumnoId\":" + f.a + ","));
		assertEquals(Set.of(f.b), ids(leer(get(base + "/clases/" + f.turno + "/2026-10-19"), tProfA, 200)));
		assertEquals(400, estado(guardar(base, f.turno, LUNES, f.a, f.b), tProfA), "ya no está en la lista");
		assertEquals(404, estado(get(base + "/alumnos/" + f.a + "/asistencia?year=2026&month=10"), tProfA),
				"ya no es del grupo");
		assertEquals(200, estado(post("/api/alumnos/" + f.a + "/anonimizar"), tPropietario), "repetirlo no hace nada");
	}

	/** Una lista cuyo único alumno se anonimiza sigue existiendo, vacía: sus cifras son 0, no «sin lista». */
	@Test
	void anonimizarAlUnicoAlumno_laClaseQuedaVacia_yUnPutDa400() throws Exception {
		long grupo = grupoNuevo(e.sA, profA);
		long turno = turno(grupo, "Lunes", "18:00", "19:00");
		long unico = alumnoEnTurno("UNICO", grupo, turno);
		String base = "/api/profesor/grupos/" + grupo;
		leer(guardar(base, turno, LUNES, unico), tProfA, 200);

		assertEquals(200, estado(post("/api/alumnos/" + unico + "/anonimizar"), tPropietario));

		JsonNode lista = leer(get(base + "/clases/" + turno + "/" + LUNES), tProfA, 200);
		assertEquals(0, lista.get("alumnos").size());
		assertEquals(0, lista.get("clase").get("total").asInt());
		assertEquals(0, lista.get("clase").get("presentes").asInt());
		assertEquals(400, estado(guardar(base, turno, LUNES), tProfA));
	}

	// ------------------------------------------------------------------ utilidades

	/** Un grupo del profesor con un turno de lunes (18:00-19:00) y dos alumnos en él. */
	private record Fijo(long grupo, long turno, long a, long b) {
	}

	private Fijo grupoConDosAlumnos(long sede, long profesor) {
		long grupo = grupoNuevo(sede, profesor);
		long turno = turno(grupo, "Lunes", "18:00", "19:00");
		return new Fijo(grupo, turno, alumnoEnTurno("LISTAA", grupo, turno), alumnoEnTurno("LISTAB", grupo, turno));
	}

	private long grupoNuevo(long sede, long profesor) {
		long grupo = TenantContext.calcularCon(e.org, () -> {
			GrupoConAlumnosDTO dto = new GrupoConAlumnosDTO();
			dto.setNombre("IT lista " + System.nanoTime());
			dto.setDeporte("TAEKWONDO");
			dto.setSedeId(sede);
			return grupoService.crearGrupo(dto).getId();
		});
		asignar(e, profesor, grupo);
		return grupo;
	}

	private long turno(long grupo, String dia, String inicio, String fin) {
		jdbc.update("""
				INSERT INTO turno (dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id)
				VALUES (?, ?, ?, NULL, ?, ?)""", dia, inicio, fin, grupo, e.org);
		return jdbc.queryForObject("SELECT MAX(id) FROM turno WHERE organizacion_id = ?", Long.class, e.org);
	}

	/** Activo, con taekwondo activo, en el grupo y en ningún turno. */
	private long alumno(String nombre, long grupo) {
		long id = e.alumno(nombre + e.marca, e.sA, true);
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", grupo, id);
		e.taekwondo(id, false);
		return id;
	}

	private long alumnoEnTurno(String nombre, long grupo, long turno) {
		long id = alumno(nombre, grupo);
		e.enTurno(turno, id);
		return id;
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

	private void responsableDeSede(EscuelaConSedesDePrueba x, long usuario, long sede) {
		membresia(x, usuario, "RESPONSABLE_SEDE");
		long membresia = jdbc.queryForObject("""
				SELECT id FROM membresia WHERE usuario_id = ? AND organizacion_id = ? AND rol = 'RESPONSABLE_SEDE'""",
				Long.class, usuario, x.org);
		jdbc.update("INSERT INTO membresia_sede (membresia_id, sede_id) VALUES (?, ?)", membresia, sede);
	}

	private void asignar(EscuelaConSedesDePrueba x, long profesor, long grupo) {
		jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) VALUES (?, ?, ?, 'TITULAR')",
				x.org, profesor, grupo);
	}

	private static MockHttpServletRequestBuilder guardar(String base, long turno, String fecha, long... presentes) {
		String ids = java.util.Arrays.stream(presentes).mapToObj(String::valueOf).collect(Collectors.joining(","));
		return put(base + "/clases/" + turno + "/" + fecha).contentType(MediaType.APPLICATION_JSON)
				.content("{\"presentes\":[" + ids + "]}");
	}

	private void guardarOk(String base, long turno, String fecha, String token, long... presentes) throws Exception {
		leer(guardar(base, turno, fecha, presentes), token, 200);
	}

	private long clasesDe(long grupo) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM clase_asistencia WHERE grupo_id = ?", Long.class, grupo);
	}

	private long filas(long grupo) {
		return jdbc.queryForObject("""
				SELECT COUNT(*) FROM asistencia_alumno f JOIN clase_asistencia c ON c.id = f.clase_id
				WHERE c.grupo_id = ?""", Long.class, grupo);
	}

	private long filasPresentes(long grupo) {
		return jdbc.queryForObject("""
				SELECT COUNT(*) FROM asistencia_alumno f JOIN clase_asistencia c ON c.id = f.clase_id
				WHERE c.grupo_id = ? AND f.presente = TRUE""", Long.class, grupo);
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

	private static JsonNode jsonNulo(JsonNode nodo) {
		assertNotNull(nodo, "la clave debe estar en el JSON");
		return nodo.isNull() ? null : nodo;
	}

	private static Set<Long> ids(JsonNode lista) {
		Set<Long> ids = new TreeSet<>();
		lista.get("alumnos").forEach(a -> ids.add(a.get("id").asLong()));
		return ids;
	}

	private static boolean presenteEnLista(JsonNode lista, long alumno) {
		return buscar(lista.get("alumnos"), alumno).get("presente").asBoolean();
	}

	private static JsonNode buscar(JsonNode alumnos, long id) {
		for (JsonNode alumno : alumnos) {
			if (alumno.get("id").asLong() == id) {
				return alumno;
			}
		}
		throw new AssertionError("Sin el alumno " + id + " en " + alumnos);
	}

	private static JsonNode delAlumno(JsonNode resumen, long id) {
		for (JsonNode fila : resumen) {
			if (fila.get("alumnoId").asLong() == id) {
				return fila;
			}
		}
		throw new AssertionError("Sin el alumno " + id + " en " + resumen);
	}

	private static JsonNode claseDe(JsonNode clases, long turno, String fecha) {
		for (JsonNode clase : clases) {
			if (clase.get("turnoId").asLong() == turno && fecha.equals(clase.get("fecha").asText())) {
				return clase;
			}
		}
		throw new AssertionError("Sin la clase " + turno + " " + fecha + " en " + clases);
	}

	private static List<String> dias(JsonNode detalle) {
		List<String> dias = new ArrayList<>();
		detalle.get("dias").forEach(d -> dias.add(d.get("fecha").asText() + " " + d.get("horaInicio").asText() + " "
				+ d.get("estado").asText()));
		return dias;
	}

	private static Set<String> campos(JsonNode nodo) {
		Set<String> campos = new java.util.HashSet<>();
		for (Iterator<String> it = nodo.fieldNames(); it.hasNext();) {
			campos.add(it.next());
		}
		return campos;
	}

	private static String texto(byte[] pdf) {
		try (PDDocument documento = PDDocument.load(pdf)) {
			return new PDFTextStripper().getText(documento).replaceAll("\\s+", "");
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}
}
