package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

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
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.ProfesorService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza E7-3 contra MySQL (fase3-e7-plan.md §4): apto, fecha de grado y pases del profesor bajo
 * {@code /api/profesor/alumnos/{alumnoId}/deportes/{deporte}/}.
 * <ul>
 * <li>Mismos servicios y mensajes que gestión (D7-16): el pase crea un cargo pendiente con el precio del producto y el
 * grado no cambia hasta cobrarlo (en Mi caja, camino E); deshacer ese cobro no revierte el grado (comportamiento actual,
 * §9 fila 7).</li>
 * <li>D7-17: un segundo pase con otro pendiente de cobro da 409 y no crea cargo, también a la vez (fila de
 * {@code alumno_deporte} bloqueada). Gestión no cambia: dos pases siguen creando dos cargos.</li>
 * <li>Solo alumnos activos de sus grupos y solo en las disciplinas de los grupos que comparte (§11 pregunta 2); sin
 * «Cambiar grado» (207); registro {@code RegistroAccionesProfesor} con ids y enumeraciones (§7).</li>
 * </ul>
 * Escuela sintética ({@link EscuelaConSedesDePrueba}) con un sistema de grados propio, productos de examen y recompensa a
 * Rojo (normal y bordado) y {@code comportamiento_legado_taemoi} solo en ella; otra escuela para el aislamiento. Datos
 * inventados; un alumno nuevo por test, así que el orden de los tests no importa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProfesorGradosIntegrationTest extends MySqlIntegrationTest {

	private static final String RESUMEN_SOLO_IDS = "^PROFESOR [A-Z_]+( [A-Za-z]+=(#\\d+|[A-Z_]+))+$";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired DataSource dataSource;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	long profA, profB, profRP, gestorProf, propietario, profOtra;
	long kickboxing, gAmarillo, gRojo;
	long pExamenRojo, pRecompensaRojo, pExamenRojoBordado, pRecompensaRojoBordado, pCamiseta;
	String tProfA, tProfB, tProfRP, tGestorProf, tGestor, tPropietario, tProfOtra;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e7-3");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e7-3-otra");
		// Solo esta escuela sintética: el pase a rojo pide elegir bordado, como en Taemoi.
		jdbc.update("UPDATE organizacion SET comportamiento_legado_taemoi = TRUE WHERE id = ?", e.org);
		sistemaDeGrados();
		jdbc.update("""
				INSERT INTO disciplina (organizacion_id, codigo, nombre, sistema_grados_id, edad_adulto, orden)
				VALUES (?, 'KICKBOXING', 'Kickboxing IT', NULL, 14, 2)""", e.org);
		kickboxing = jdbc.queryForObject(
				"SELECT id FROM disciplina WHERE organizacion_id = ? AND codigo = 'KICKBOXING'", Long.class, e.org);
		pExamenRojo = productoDeGrado("EXAMEN_ROJO", "EXAMEN", 25.0);
		pRecompensaRojo = productoDeGrado("RECOMPENSA_ROJO", "RECOMPENSA", 15.0);
		pExamenRojoBordado = productoDeGrado("EXAMEN_ROJO_BORDADO", "EXAMEN", 27.0);
		pRecompensaRojoBordado = productoDeGrado("RECOMPENSA_ROJO_BORDADO", "RECOMPENSA", 17.0);
		jdbc.update("INSERT INTO producto (concepto, precio, codigo, organizacion_id) VALUES ('CAMISETA E73', 10.0, NULL, ?)",
				e.org);
		pCamiseta = jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = ?", Long.class, e.org);

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

		tProfA = e.token(jwtService, usuarios, profA);
		tProfB = e.token(jwtService, usuarios, profB);
		tProfRP = e.token(jwtService, usuarios, profRP);
		tGestorProf = e.token(jwtService, usuarios, gestorProf);
		tGestor = e.token(jwtService, usuarios, e.gestor);
		tPropietario = e.token(jwtService, usuarios, propietario);
		tProfOtra = otra.token(jwtService, usuarios, profOtra);
	}

	@AfterEach
	void sinFugas() {
		assertFalse(TenantContext.get() != null, "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ pases

	@Test
	void paseCreaCargoYNoSubeGrado() throws Exception {
		long a = alumno("E73PASE", e.gA, e.sA, gAmarillo, true);
		long ad = alumnoDeporte(a);

		JsonNode r = leer(pase(a, "TAEKWONDO", "pase-examen", "{\"rojoBordado\":false}"), tProfA, 200);
		assertEquals(Set.of("deporte", "cargo"), campos(r));
		JsonNode deporte = r.get("deporte");
		assertEquals("TAEKWONDO", deporte.get("deporte").asText());
		assertEquals("AMARILLO", deporte.get("grado").get("codigo").asText(), "el grado no cambia con el pase");
		assertEquals("ROJO", deporte.get("siguienteGrado").get("codigo").asText());
		assertTrue(deporte.get("apto").asBoolean());
		assertTrue(deporte.get("deMisGrupos").asBoolean());
		assertTrue(deporte.get("pideRojoBordado").asBoolean());
		JsonNode cargo = r.get("cargo");
		assertEquals(Set.of("id", "concepto", "importe"), campos(cargo));
		assertEquals(25.0, cargo.get("importe").asDouble(), 0.001);
		assertTrue(cargo.get("concepto").asText().endsWith(" - TAEKWONDO"), cargo.toString());

		List<Map<String, Object>> cargos = cargos(ad);
		assertEquals(1, cargos.size());
		Map<String, Object> fila = cargos.get(0);
		assertEquals(cargo.get("id").asLong(), ((Number) fila.get("id")).longValue());
		assertEquals(pExamenRojo, ((Number) fila.get("producto_id")).longValue());
		assertEquals(a, ((Number) fila.get("alumno_id")).longValue());
		assertEquals(25.0, ((Number) fila.get("precio")).doubleValue(), 0.001);
		assertFalse((Boolean) fila.get("pagado"), "cargo pendiente");
		assertNull(fila.get("cancelado_en"));
		assertEquals(e.sA, ((Number) fila.get("sede_id")).longValue());
		assertEquals(gAmarillo, gradoDe(a), "el grado sube al cobrar, no al hacer el pase");
	}

	@Test
	void paseSinApto_400_comoGestion() throws Exception {
		// Sin apto.
		long sinApto = alumno("E73SINAPTO", e.gA, e.sA, gAmarillo, false);
		// Sede principal inactiva (la C), en un grupo suyo.
		long sedeInactiva = alumno("E73SEDEC", e.gA, e.sC, gAmarillo, true);
		// Rojo: el siguiente (Negro) no tiene producto de examen en el catálogo.
		long sinProducto = alumno("E73SINPRODUCTO", e.gA, e.sA, gRojo, true);
		for (long a : List.of(sinApto, sedeInactiva, sinProducto)) {
			for (String tipo : List.of("pase-examen", "pase-recompensa")) {
				MvcResult gestion = peticion(post("/api/alumnos/" + a + "/deportes/TAEKWONDO/" + tipo), tGestor);
				assertEquals(400, gestion.getResponse().getStatus(), gestion.getResponse().getContentAsString());
				String mensajeGestion = gestion.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
				assertFalse(mensajeGestion.isBlank());

				MvcResult profesor = peticion(pase(a, "TAEKWONDO", tipo, null), tProfA);
				assertEquals(400, profesor.getResponse().getStatus(), profesor.getResponse().getContentAsString());
				assertEquals(mensajeGestion, mensaje(profesor), "mismo mensaje que gestión (" + tipo + ")");
			}
			assertTrue(cargos(alumnoDeporte(a)).isEmpty(), "ningún cargo");
			assertEquals(0, accionesDe(a).size(), "sin pase, sin registro");
		}
		assertEquals("El alumno no está apto para examen en el deporte TAEKWONDO",
				mensaje(peticion(pase(sinApto, "TAEKWONDO", "pase-examen", null), tProfA)));
	}

	@Test
	void dosPasesSeguidos_409_unSoloCargo() throws Exception {
		long a = alumno("E73DOBLE", e.gA, e.sA, gAmarillo, true);
		long ad = alumnoDeporte(a);
		assertEquals(200, estado(pase(a, "TAEKWONDO", "pase-examen", null), tProfA));
		for (String tipo : List.of("pase-examen", "pase-recompensa")) {
			MvcResult r = peticion(pase(a, "TAEKWONDO", tipo, null), tProfA);
			assertEquals(409, r.getResponse().getStatus(), r.getResponse().getContentAsString());
			assertEquals(ProfesorService.PASE_PENDIENTE, mensaje(r));
		}
		assertEquals("Ya hay un pase pendiente de cobro para este deporte", ProfesorService.PASE_PENDIENTE);
		assertEquals(1, cargos(ad).size());
		assertEquals(1, accionesDe(a).size(), "el 409 no registra otro pase");
		assertEquals(gAmarillo, gradoDe(a));
	}

	/**
	 * D7-17 con dos peticiones a la vez. Primero, con la fila de {@code alumno_deporte} bloqueada desde otra conexión: las
	 * dos esperan y, al soltarla, una crea el cargo y la otra lo ve (409). Después, cinco carreras libres.
	 */
	@Test
	void dosPasesALaVez_unSoloCargo() throws Exception {
		long a = alumno("E73ALAVEZ", e.gA, e.sA, gAmarillo, true);
		long ad = alumnoDeporte(a);
		ExecutorService hilos = Executors.newFixedThreadPool(2);
		try (Connection otraConexion = dataSource.getConnection()) {
			otraConexion.setAutoCommit(false);
			try (PreparedStatement bloqueo = otraConexion
					.prepareStatement("SELECT id FROM alumno_deporte WHERE id = ? FOR UPDATE")) {
				bloqueo.setLong(1, ad);
				bloqueo.executeQuery().close();
			}
			Future<Integer> primera = hilos.submit(() -> estado(pase(a, "TAEKWONDO", "pase-examen", null), tProfA));
			Future<Integer> segunda = hilos.submit(() -> estado(pase(a, "TAEKWONDO", "pase-recompensa", null), tProfA));
			Thread.sleep(1500);
			assertFalse(primera.isDone() || segunda.isDone(), "los dos pases esperan al bloqueo de la fila");
			otraConexion.commit();
			List<Integer> estados = new ArrayList<>(List.of(primera.get(60, TimeUnit.SECONDS),
					segunda.get(60, TimeUnit.SECONDS)));
			estados.sort(Integer::compareTo);
			assertEquals(List.of(200, 409), estados);
		} finally {
			hilos.shutdownNow();
		}
		assertEquals(1, cargos(ad).size(), "un solo cargo");

		for (int vuelta = 0; vuelta < 5; vuelta++) {
			long otro = alumno("E73CARRERA" + vuelta, e.gA, e.sA, gAmarillo, true);
			List<Integer> estados = enParalelo(() -> estado(pase(otro, "TAEKWONDO", "pase-examen", null), tProfA),
					() -> estado(pase(otro, "TAEKWONDO", "pase-examen", null), tProfA));
			assertEquals(List.of(200, 409), estados, "vuelta " + vuelta);
			assertEquals(1, cargos(alumnoDeporte(otro)).size(), "vuelta " + vuelta);
		}
	}

	@Test
	void soloBloqueaUnPasePendiente() throws Exception {
		long a = alumno("E73SOLOPASE", e.gA, e.sA, gAmarillo, true);
		long ad = alumnoDeporte(a);
		// Un cargo pendiente del mismo deporte que no concede grado no bloquea.
		jdbc.update("""
				INSERT INTO producto_alumno (organizacion_id, producto_id, alumno_id, alumno_deporte_id, concepto,
				  fecha_asignacion, cantidad, precio, pagado, sede_id)
				VALUES (?, ?, ?, ?, 'CAMISETA E73', NOW(), 1, 10.0, FALSE, ?)""", e.org, pCamiseta, a, ad, e.sA);
		long camiseta = jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE alumno_deporte_id = ?", Long.class,
				ad);
		try {
			JsonNode primero = leer(pase(a, "TAEKWONDO", "pase-examen", null), tProfA, 200);
			// Un pase cancelado tampoco bloquea.
			jdbc.update("""
					UPDATE producto_alumno SET cancelado_en = NOW(6), cancelado_por_usuario_id = ?,
					  motivo_cancelacion = 'prueba E73' WHERE id = ?""", e.gestor, primero.get("cargo").get("id").asLong());
			JsonNode segundo = leer(pase(a, "TAEKWONDO", "pase-recompensa", null), tProfA, 200);
			assertEquals(pRecompensaRojo, productoDe(segundo.get("cargo").get("id").asLong()));
			assertEquals(3, cargos(ad).size());
		} finally {
			jdbc.update("DELETE FROM producto_alumno WHERE id = ?", camiseta);
		}
	}

	@Test
	void paseRegistraAccionConIds() throws Exception {
		long a = alumno("E73REGISTRO", e.gA, e.sA, gAmarillo, true);
		long ad = alumnoDeporte(a);
		String ruta = "/api/profesor/alumnos/" + a + "/deportes/TAEKWONDO/pase-examen";
		long cargo = leer(post(ruta), tProfA, 200).get("cargo").get("id").asLong();

		List<Map<String, Object>> acciones = accionesDe(a);
		assertEquals(1, acciones.size());
		Map<String, Object> evento = acciones.get(0);
		assertEquals("CREATE", evento.get("accion"));
		assertEquals("profesor", evento.get("modulo"));
		assertEquals(ruta, evento.get("endpoint"));
		assertEquals(profA, ((Number) evento.get("usuario_id")).longValue());
		assertEquals(e.org, ((Number) evento.get("organizacion_id")).longValue());
		String resumen = (String) evento.get("resumen");
		assertEquals("PROFESOR PASE_EXAMEN alumno=#" + a + " alumnoDeporte=#" + ad + " gradoActual=#" + gAmarillo
				+ " gradoSiguiente=#" + gRojo + " cargo=#" + cargo, resumen);
		assertTrue(resumen.matches(RESUMEN_SOLO_IDS), resumen);
		assertFalse(resumen.contains(e.marca), "sin nombres");
	}

	@Test
	void aptoYFechaRegistranAccion() throws Exception {
		long a = alumno("E73APTO", e.gA, e.sA, gAmarillo, true);
		long ad = alumnoDeporte(a);

		JsonNode apto = leer(put(base(a, "TAEKWONDO") + "/apto").contentType(MediaType.APPLICATION_JSON)
				.content("{\"aptoParaExamen\":false}"), tProfA, 200);
		assertEquals(Set.of("deporte", "deporteNombre", "grado", "fechaGrado", "apto", "deMisGrupos", "admitePases",
				"siguienteGrado", "pideRojoBordado"), campos(apto), "el DeporteDelAlumno de la ficha");
		assertFalse(apto.get("apto").asBoolean());
		assertEquals("AMARILLO", apto.get("grado").get("codigo").asText());
		assertFalse(aptoDe(a));

		JsonNode fecha = leer(put(base(a, "TAEKWONDO") + "/fecha-grado").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fechaGrado\":\"2024-02-01\"}"), tProfA, 200);
		assertEquals("2024-02-01", fecha.get("fechaGrado").asText());
		assertEquals("2024-02-01", jdbc.queryForObject("SELECT fecha_grado FROM alumno_deporte WHERE id = ?",
				java.sql.Date.class, ad).toString());
		boolean aptoTrasFecha = aptoDe(a);
		assertEquals(aptoTrasFecha, fecha.get("apto").asBoolean(), "la fecha recalcula el apto, como gestión");

		List<Map<String, Object>> acciones = accionesDe(a);
		assertEquals(2, acciones.size());
		assertEquals("UPDATE", acciones.get(0).get("accion"));
		assertEquals("PROFESOR APTO_CAMBIADO alumno=#" + a + " alumnoDeporte=#" + ad + " apto=NO",
				acciones.get(0).get("resumen"));
		assertEquals("UPDATE", acciones.get(1).get("accion"));
		String resumenFecha = (String) acciones.get(1).get("resumen");
		assertEquals("PROFESOR FECHA_GRADO_CAMBIADA alumno=#" + a + " alumnoDeporte=#" + ad + " apto="
				+ (aptoTrasFecha ? "SI" : "NO"), resumenFecha);
		for (Map<String, Object> accion : acciones) {
			assertTrue(((String) accion.get("resumen")).matches(RESUMEN_SOLO_IDS));
			assertEquals("profesor", accion.get("modulo"));
			assertEquals(profA, ((Number) accion.get("usuario_id")).longValue());
		}
		assertFalse(resumenFecha.contains("2024"), "sin la fecha");

		leer(put(base(a, "TAEKWONDO") + "/apto").contentType(MediaType.APPLICATION_JSON)
				.content("{\"aptoParaExamen\":true}"), tProfA, 200);
		assertTrue(aptoDe(a));
		assertTrue(((String) accionesDe(a).get(2).get("resumen")).endsWith(" apto=SI"));
	}

	@Test
	void cuerposInvalidos_400() throws Exception {
		long a = alumno("E73CUERPOS", e.gA, e.sA, gAmarillo, true);
		MvcResult sinApto = peticion(put(base(a, "TAEKWONDO") + "/apto").contentType(MediaType.APPLICATION_JSON)
				.content("{}"), tProfA);
		assertEquals(400, sinApto.getResponse().getStatus());
		assertEquals("El campo aptoParaExamen es requerido", mensaje(sinApto));
		MvcResult sinFecha = peticion(put(base(a, "TAEKWONDO") + "/fecha-grado").contentType(MediaType.APPLICATION_JSON)
				.content("{}"), tProfA);
		assertEquals(400, sinFecha.getResponse().getStatus());
		assertEquals("La fecha de grado es requerida", mensaje(sinFecha));
		MvcResult malFecha = peticion(put(base(a, "TAEKWONDO") + "/fecha-grado").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fechaGrado\":\"01/02/2024\"}"), tProfA);
		assertEquals(400, malFecha.getResponse().getStatus());
		assertEquals("La fecha de grado no es válida", mensaje(malFecha));
		assertTrue(aptoDe(a));
		assertEquals(0, accionesDe(a).size());
	}

	// ------------------------------------------------------------------ alcance

	@Test
	void paseEnDisciplinaNoCompartida_403() throws Exception {
		long a = alumno("E73KICK", e.gA, e.sA, gAmarillo, true);
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
				  organizacion_id, fecha_alta, fecha_alta_inicial, disciplina_id)
				VALUES (TRUE, TRUE, ?, 0, 0, 0, ?, '2024-09-01', '2024-09-01', ?)""", a, e.org, kickboxing);
		for (String deporte : List.of("KICKBOXING", "INVENTADO")) {
			for (MockHttpServletRequestBuilder peticion : List.of(pase(a, deporte, "pase-examen", null),
					pase(a, deporte, "pase-recompensa", null),
					put(base(a, deporte) + "/apto").contentType(MediaType.APPLICATION_JSON)
							.content("{\"aptoParaExamen\":false}"),
					put(base(a, deporte) + "/fecha-grado").contentType(MediaType.APPLICATION_JSON)
							.content("{\"fechaGrado\":\"2024-02-01\"}"))) {
				MvcResult r = peticion(peticion, tProfA);
				assertEquals(403, r.getResponse().getStatus(), r.getRequest().getMethod() + " " + r.getRequest().getRequestURI());
			}
		}
		assertTrue(Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT apto_para_examen FROM alumno_deporte WHERE alumno_id = ? AND disciplina_id = ?", Boolean.class, a,
				kickboxing)), "el apto de kickboxing no cambió");
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM producto_alumno WHERE alumno_id = ?", Long.class, a));
		assertEquals(0, accionesDe(a).size());
	}

	/** Seguimiento de la E7-1: un alumno que estuvo en su grupo y se le quitó (sin fila en {@code alumno_grupo}). */
	@Test
	void alumnoQuitadoDelGrupo_403() throws Exception {
		long a = alumno("E73QUITADO", e.gA, e.sA, gAmarillo, true);
		assertEquals(200, estado(put(base(a, "TAEKWONDO") + "/apto").contentType(MediaType.APPLICATION_JSON)
				.content("{\"aptoParaExamen\":true}"), tProfA), "mientras está en el grupo");
		jdbc.update("DELETE FROM alumno_grupo WHERE grupo_id = ? AND alumno_id = ?", e.gA, a);
		try {
			assertEquals(403, estado(pase(a, "TAEKWONDO", "pase-examen", null), tProfA));
			assertEquals(403, estado(pase(a, "TAEKWONDO", "pase-recompensa", null), tProfA));
			assertEquals(403, estado(put(base(a, "TAEKWONDO") + "/apto").contentType(MediaType.APPLICATION_JSON)
					.content("{\"aptoParaExamen\":false}"), tProfA));
			assertEquals(403, estado(put(base(a, "TAEKWONDO") + "/fecha-grado").contentType(MediaType.APPLICATION_JSON)
					.content("{\"fechaGrado\":\"2024-02-01\"}"), tProfA));
			assertEquals(403, estado(get("/api/profesor/alumnos/" + a), tProfA), "ni la ficha");
			assertTrue(cargos(alumnoDeporte(a)).isEmpty());
			assertTrue(aptoDe(a));
		} finally {
			jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", e.gA, a);
		}
	}

	@Test
	void alumnoDeOtroGrupoOEscuela_403_yDeBaja_404() throws Exception {
		long deB = alumno("E73DEB", e.gB, e.sB, gAmarillo, true);
		long deBaja = alumno("E73BAJA", e.gA, e.sA, gAmarillo, true);
		jdbc.update("UPDATE alumno SET activo = FALSE WHERE id = ?", deBaja);
		for (long ajeno : List.of(deB, otra.aGrupoA, 999_999_999L)) {
			assertEquals(403, estado(pase(ajeno, "TAEKWONDO", "pase-examen", null), tProfA), "alumno " + ajeno);
			assertEquals(403, estado(put(base(ajeno, "TAEKWONDO") + "/apto").contentType(MediaType.APPLICATION_JSON)
					.content("{\"aptoParaExamen\":false}"), tProfA), "alumno " + ajeno);
		}
		assertEquals(403, estado(pase(deB, "TAEKWONDO", "pase-examen", null), tProfOtra), "otra escuela");
		MvcResult baja = peticion(pase(deBaja, "TAEKWONDO", "pase-examen", null), tProfA);
		assertEquals(404, baja.getResponse().getStatus(), "D7-13");
		assertEquals(404, estado(put(base(deBaja, "TAEKWONDO") + "/apto").contentType(MediaType.APPLICATION_JSON)
				.content("{\"aptoParaExamen\":false}"), tProfA));
		assertTrue(cargos(alumnoDeporte(deB)).isEmpty());
		assertTrue(cargos(alumnoDeporte(deBaja)).isEmpty());
		assertTrue(aptoDe(deB) && aptoDe(deBaja));
	}

	// ------------------------------------------------------------------ cobro (camino E)

	@Test
	void cobrarElPaseEnMiCajaSubeElGrado() throws Exception {
		long a = alumno("E73COBRO", e.gA, e.sA, gAmarillo, true);
		long cargo = leer(pase(a, "TAEKWONDO", "pase-examen", null), tProfA, 200).get("cargo").get("id").asLong();

		JsonNode porCobrar = leer(get("/api/caja/profesor/cargos-pendientes"), tProfA, 200);
		JsonNode fila = null;
		for (JsonNode n : porCobrar) {
			if (n.get("cargoId").asLong() == cargo) {
				fila = n;
			}
		}
		assertNotNull(fila, "el cargo del pase sale en «Por cobrar» de Mi caja: " + porCobrar);
		assertEquals(a, fila.get("alumnoId").asLong());
		assertEquals(25.0, fila.get("importe").asDouble(), 0.001);

		JsonNode cobro = leer(post("/api/caja/profesor/cargos/" + cargo + "/cobrar"), tProfA, 201);
		assertEquals(cargo, cobro.get("cargoId").asLong());
		assertEquals(gRojo, gradoDe(a), "cobrar el cargo del pase sube el grado");
		assertTrue(jdbc.queryForObject("SELECT pagado FROM producto_alumno WHERE id = ?", Boolean.class, cargo));
		JsonNode ficha = leer(get("/api/profesor/alumnos/" + a), tProfA, 200);
		assertEquals("ROJO", ficha.get("deportes").get(0).get("grado").get("codigo").asText());
	}

	/** Comportamiento ACTUAL (§9 fila 7): deshacer el cobro no revierte el grado. Se fija, no se cambia. */
	@Test
	void deshacerElCobroNoRevierteElGrado() throws Exception {
		long a = alumno("E73DESHACER", e.gA, e.sA, gAmarillo, true);
		long cargo = leer(pase(a, "TAEKWONDO", "pase-examen", null), tProfA, 200).get("cargo").get("id").asLong();
		long cobro = leer(post("/api/caja/profesor/cargos/" + cargo + "/cobrar"), tProfA, 201).get("cobroId").asLong();
		assertEquals(gRojo, gradoDe(a));

		assertEquals(204, estado(post("/api/caja/profesor/cobros/" + cobro + "/deshacer"), tProfA));
		assertFalse(jdbc.queryForObject("SELECT pagado FROM producto_alumno WHERE id = ?", Boolean.class, cargo));
		assertEquals("ANULADO", jdbc.queryForObject("SELECT estado FROM cobro WHERE id = ?", String.class, cobro));
		assertEquals(gRojo, gradoDe(a), "el grado se queda en Rojo");
	}

	@Test
	void pasePorRecompensaConRojoBordado() throws Exception {
		long a = alumno("E73BORDADO", e.gA, e.sA, gAmarillo, true);
		long ad = alumnoDeporte(a);
		JsonNode ficha = leer(get("/api/profesor/alumnos/" + a), tProfA, 200);
		assertTrue(ficha.get("deportes").get(0).get("pideRojoBordado").asBoolean(), "la ficha pide elegir");

		JsonNode r = leer(pase(a, "TAEKWONDO", "pase-recompensa", "{\"rojoBordado\":true}"), tProfA, 200);
		long cargo = r.get("cargo").get("id").asLong();
		assertEquals(17.0, r.get("cargo").get("importe").asDouble(), 0.001);
		assertEquals(pRecompensaRojoBordado, productoDe(cargo));
		assertEquals(gAmarillo, gradoDe(a));
		assertEquals("PROFESOR PASE_RECOMPENSA alumno=#" + a + " alumnoDeporte=#" + ad + " gradoActual=#" + gAmarillo
				+ " gradoSiguiente=#" + gRojo + " cargo=#" + cargo, accionesDe(a).get(0).get("resumen"));
		assertEquals("CREATE", accionesDe(a).get(0).get("accion"));
	}

	// ------------------------------------------------------------------ gestión

	/** Gestión no cambia (§11 pregunta 5): dos pases seguidos siguen creando dos cargos. */
	@Test
	void gestionSigueCreandoDosCargos() throws Exception {
		long a = alumno("E73GESTION", e.gA, e.sA, gAmarillo, true);
		long ad = alumnoDeporte(a);
		for (int i = 0; i < 2; i++) {
			MvcResult r = peticion(post("/api/alumnos/" + a + "/deportes/TAEKWONDO/pase-examen"), tGestor);
			assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		}
		assertEquals(2, cargos(ad).size(), "gestión: dos cargos, como antes de la E7");
		assertEquals(0, accionesDe(a).size(), "gestión no escribe en el registro del profesor");
		assertEquals(409, estado(pase(a, "TAEKWONDO", "pase-examen", null), tProfA), "el profesor ve el pendiente");
		assertEquals(2, cargos(ad).size());
	}

	/** Decisión 207: el profesor no tiene «Cambiar grado». */
	@Test
	void cambiarGradoDirectoNoExisteParaElProfesor() throws Exception {
		long a = alumno("E73GRADO", e.gA, e.sA, gAmarillo, true);
		int propia = estado(put(base(a, "TAEKWONDO") + "/grado").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nuevoGrado\":\"ROJO\"}"), tProfA);
		assertTrue(propia == 404 || propia == 405, "no hay ruta propia de cambio de grado: " + propia);
		assertEquals(403, estado(put("/api/alumnos/" + a + "/deportes/TAEKWONDO/grado")
				.contentType(MediaType.APPLICATION_JSON).content("{\"nuevoGrado\":\"ROJO\"}"), tProfA));
		assertEquals(gAmarillo, gradoDe(a));
	}

	@Test
	void gestionNoUsaLasRutasDelProfesor_403() throws Exception {
		long a = alumno("E73NOGESTION", e.gA, e.sA, gAmarillo, true);
		for (String token : List.of(tGestor, tPropietario, tGestorProf)) {
			assertEquals(403, estado(pase(a, "TAEKWONDO", "pase-examen", null), token));
			assertEquals(403, estado(pase(a, "TAEKWONDO", "pase-recompensa", null), token));
			assertEquals(403, estado(put(base(a, "TAEKWONDO") + "/apto").contentType(MediaType.APPLICATION_JSON)
					.content("{\"aptoParaExamen\":false}"), token));
			assertEquals(403, estado(put(base(a, "TAEKWONDO") + "/fecha-grado").contentType(MediaType.APPLICATION_JSON)
					.content("{\"fechaGrado\":\"2024-02-01\"}"), token));
		}
		assertTrue(cargos(alumnoDeporte(a)).isEmpty());
		assertTrue(aptoDe(a));
	}

	@Test
	void sesionSoloProfesorAlcanzaLasRutas() throws Exception {
		long a = alumno("E73SOLOPROF", e.gA, e.sA, gAmarillo, true);
		assertEquals(200, estado(put(base(a, "TAEKWONDO") + "/fecha-grado").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fechaGrado\":\"2024-02-01\"}"), tProfA));
		assertEquals(200, estado(put(base(a, "TAEKWONDO") + "/apto").contentType(MediaType.APPLICATION_JSON)
				.content("{\"aptoParaExamen\":true}"), tProfA));
		assertEquals(200, estado(pase(a, "TAEKWONDO", "pase-recompensa", null), tProfA));
		assertEquals(409, estado(pase(a, "TAEKWONDO", "pase-examen", null), tProfA));

		// R+Pr (sesión 184): su grupo gB sí; un alumno de su sede A que no está en sus grupos, no.
		long deB = alumno("E73RPB", e.gB, e.sB, gAmarillo, true);
		assertEquals(200, estado(pase(deB, "TAEKWONDO", "pase-examen", null), tProfRP));
		long deSuSede = alumno("E73RPSEDE", e.gA, e.sA, gAmarillo, true);
		assertEquals(403, estado(pase(deSuSede, "TAEKWONDO", "pase-examen", null), tProfRP));
		assertEquals(403, estado(put(base(deSuSede, "TAEKWONDO") + "/apto").contentType(MediaType.APPLICATION_JSON)
				.content("{\"aptoParaExamen\":false}"), tProfRP));
		assertEquals(403, estado(pase(deB, "TAEKWONDO", "pase-examen", null), tProfA), "profA no da clase en gB");
		assertTrue(cargos(alumnoDeporte(deSuSede)).isEmpty());
	}

	// ------------------------------------------------------------------ utilidades

	private String base(long alumno, String deporte) {
		return "/api/profesor/alumnos/" + alumno + "/deportes/" + deporte;
	}

	private MockHttpServletRequestBuilder pase(long alumno, String deporte, String tipo, String cuerpo) {
		MockHttpServletRequestBuilder p = post(base(alumno, deporte) + "/" + tipo);
		return cuerpo == null ? p : p.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	/** Alumno activo de la escuela, en ese grupo, con taekwondo en ese grado y fecha de grado antigua. */
	private long alumno(String prefijo, long grupo, long sede, long grado, boolean apto) {
		long id = e.alumno(prefijo + e.marca, sede, true);
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", grupo, id);
		e.taekwondo(id, apto);
		jdbc.update("""
				UPDATE alumno_deporte SET grado_id = ?, fecha_grado = '2025-01-10'
				WHERE alumno_id = ? AND disciplina_id = ?""", grado, id, e.disciplina);
		return id;
	}

	private long alumnoDeporte(long alumno) {
		return jdbc.queryForObject("SELECT id FROM alumno_deporte WHERE alumno_id = ? AND disciplina_id = ?", Long.class,
				alumno, e.disciplina);
	}

	private long gradoDe(long alumno) {
		return jdbc.queryForObject("SELECT grado_id FROM alumno_deporte WHERE alumno_id = ? AND disciplina_id = ?",
				Long.class, alumno, e.disciplina);
	}

	private boolean aptoDe(long alumno) {
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT apto_para_examen FROM alumno_deporte WHERE alumno_id = ? AND disciplina_id = ?", Boolean.class,
				alumno, e.disciplina));
	}

	private List<Map<String, Object>> cargos(long alumnoDeporte) {
		return jdbc.queryForList("SELECT * FROM producto_alumno WHERE alumno_deporte_id = ? ORDER BY id", alumnoDeporte);
	}

	private long productoDe(long cargo) {
		return jdbc.queryForObject("SELECT producto_id FROM producto_alumno WHERE id = ?", Long.class, cargo);
	}

	/** Acciones explícitas del profesor sobre ese alumno (no las del filtro HTTP), en orden. */
	private List<Map<String, Object>> accionesDe(long alumno) {
		return jdbc.queryForList("""
				SELECT accion, modulo, endpoint, usuario_id, organizacion_id, resumen FROM auditoria_evento
				WHERE modulo = 'profesor' AND recurso_id = ? AND resumen LIKE 'PROFESOR %' ORDER BY id""", alumno);
	}

	private void sistemaDeGrados() {
		jdbc.update("INSERT INTO sistema_grados (organizacion_id, nombre, plantilla_origen) VALUES (?, 'Sistema IT E73', NULL)",
				e.org);
		long sistema = jdbc.queryForObject("SELECT id FROM sistema_grados WHERE organizacion_id = ?", Long.class, e.org);
		jdbc.update("""
				INSERT INTO grado (organizacion_id, sistema_grados_id, codigo, nombre, orden, color_1, color_2, aplica_a)
				VALUES (?, ?, 'BLANCO', 'Blanco IT', 1, NULL, NULL, 'TODOS'),
				       (?, ?, 'AMARILLO', 'Amarillo IT', 2, NULL, NULL, 'TODOS'),
				       (?, ?, 'ROJO', 'Rojo IT', 3, NULL, NULL, 'TODOS'),
				       (?, ?, 'NEGRO_1_DAN', 'Negro IT', 4, NULL, NULL, 'TODOS')""",
				e.org, sistema, e.org, sistema, e.org, sistema, e.org, sistema);
		jdbc.update("UPDATE disciplina SET sistema_grados_id = ? WHERE id = ?", sistema, e.disciplina);
		gAmarillo = jdbc.queryForObject("SELECT id FROM grado WHERE sistema_grados_id = ? AND codigo = 'AMARILLO'",
				Long.class, sistema);
		gRojo = jdbc.queryForObject("SELECT id FROM grado WHERE sistema_grados_id = ? AND codigo = 'ROJO'", Long.class,
				sistema);
	}

	/** Producto de grado clasificado (código, tipo y grado concedido Rojo) de la escuela sintética. */
	private long productoDeGrado(String codigo, String tipo, double precio) {
		jdbc.update("""
				INSERT INTO producto (concepto, precio, codigo, tipo, grado_id, organizacion_id)
				VALUES (?, ?, ?, ?, ?, ?)""", codigo.replace('_', ' ') + " E73", precio, codigo, tipo, gRojo, e.org);
		return jdbc.queryForObject("SELECT id FROM producto WHERE organizacion_id = ? AND codigo = ?", Long.class, e.org,
				codigo);
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

	private String mensaje(MvcResult r) throws Exception {
		return json.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).get("mensaje")
				.asText();
	}

	private JsonNode leer(MockHttpServletRequestBuilder peticion, String token, int esperado) throws Exception {
		MvcResult r = peticion(peticion, token);
		assertEquals(esperado, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
	}

	private int estado(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return peticion(peticion, token).getResponse().getStatus();
	}

	private MvcResult peticion(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
	}

	private static Set<String> campos(JsonNode nodo) {
		Set<String> campos = new java.util.HashSet<>();
		nodo.fieldNames().forEachRemaining(campos::add);
		return campos;
	}
}
