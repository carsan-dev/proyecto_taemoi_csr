package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.TarifaRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza 8b-3l bis (decisión 287) contra MySQL: un alumno con pagos no se borra para siempre; se elimina anonimizando.
 *
 * <ul>
 * <li>{@code DELETE /api/alumnos/{id}} de un alumno con pagos (cargo pagado, o cobro en cualquier estado, también el de
 * un cargo ya borrado): 409 «Este alumno tiene pagos. Dale de baja o elimínalo anonimizando.» sin tocar nada. Sin pagos,
 * se borra como hoy.</li>
 * <li>{@code POST /api/alumnos/{id}/anonimizar}: quita sus datos personales (foto y documentos con sus archivos,
 * tutores, salud, acceso de familia, grupos y turnos) y conserva cargos y cobros con el mismo alumno: la tesorería no
 * cambia. Solo el propietario; otra escuela, 404; repetirlo no hace nada; no sale en los listados.</li>
 * </ul>
 * Datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnonimizarAlumnoIntegrationTest extends MySqlIntegrationTest {

	private static final String CON_PAGOS = "Este alumno tiene pagos. Dale de baja o elimínalo anonimizando.";
	private static final String SOLO_PROPIETARIO = "Solo el propietario puede eliminar alumnos definitivamente.";
	private static final String ANONIMIZADO = "Este alumno está eliminado (anonimizado) y no se puede modificar.";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;
	@Autowired TarifaRepository tarifas;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	long producto;
	String tokenPropietario;
	String tokenGestor;
	String tokenOtraPropietario;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-8b3lbis");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-8b3lbis-otra");
		producto = producto(e);
		tokenPropietario = e.token(jwtService, usuarios, propietario(e, "propietario"));
		tokenGestor = e.token(jwtService, usuarios, e.gestor);
		tokenOtraPropietario = otra.token(jwtService, usuarios, propietario(otra, "propietario-otra"));
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ borrado definitivo

	@Test
	void eliminar_conCargoPagado_409_yNoCambiaNada() throws Exception {
		long alumno = e.alumno("IT8B3LBISPAGADO" + e.marca, e.sA, true);
		cargo(alumno, "30.00", true);
		cargo(alumno, "20.00", false);
		String antes = foto(alumno);

		MvcResult r = mvc.perform(delete("/api/alumnos/" + alumno).cookie(jwt(tokenPropietario))).andReturn();

		assertEquals(409, r.getResponse().getStatus());
		assertEquals(CON_PAGOS, json.readTree(r.getResponse().getContentAsString()).get("mensaje").asText());
		assertEquals(antes, foto(alumno), "ni el alumno, ni sus cargos, ni sus cobros cambian");
	}

	@Test
	void eliminar_conCobroAnuladoOSinCargo_409() throws Exception {
		// Un cobro cuenta en cualquier estado: anulado, sobre un cargo que ya no figura como pagado.
		long anulado = e.alumno("IT8B3LBISANULADO" + e.marca, e.sA, true);
		long cargo = cargo(anulado, "25.00", false);
		cobro(anulado, cargo, "25.00", "ANULADO");
		// Y el cobro de un cargo ya borrado (producto_alumno_id NULL), por su alumno_id.
		long huerfano = e.alumno("IT8B3LBISHUERFANO" + e.marca, e.sA, true);
		cobro(huerfano, null, "12.00", "ACTIVO");

		assertEquals(409, estado(delete("/api/alumnos/" + anulado), tokenPropietario));
		assertEquals(409, estado(delete("/api/alumnos/" + huerfano), tokenPropietario));
		assertEquals(2, contar("SELECT COUNT(*) FROM alumno WHERE id IN (?, ?)", anulado, huerfano));
		assertEquals(1, contar("SELECT COUNT(*) FROM producto_alumno WHERE id = ?", cargo));
	}

	@Test
	void eliminar_sinPagos_borraComoHoy() throws Exception {
		long alumno = e.alumno("IT8B3LBISSINPAGOS" + e.marca, e.sA, true);
		long pendiente = cargo(alumno, "20.00", false);

		assertEquals(200, estado(delete("/api/alumnos/" + alumno), tokenPropietario));
		assertEquals(0, contar("SELECT COUNT(*) FROM alumno WHERE id = ?", alumno));
		assertEquals(0, contar("SELECT COUNT(*) FROM producto_alumno WHERE id = ?", pendiente));
	}

	@Test
	void resumenCargos_cuentaLosPagos() throws Exception {
		long conPagos = e.alumno("IT8B3LBISRESUMEN" + e.marca, e.sA, true);
		long cargo = cargo(conPagos, "30.00", true);
		cobro(conPagos, cargo, "30.00", "ACTIVO");
		cobro(conPagos, null, "5.00", "ACTIVO");
		long sinPagos = e.alumno("IT8B3LBISRESUMEN0" + e.marca, e.sA, true);
		cargo(sinPagos, "10.00", false);

		JsonNode r = leer("/api/alumnos/cargos-resumen?alumnoIds=" + conPagos + "," + sinPagos, tokenPropietario);
		assertEquals(2, r.get(0).get("pagos").asLong(), "el cargo pagado con su cobro es uno; el cobro sin cargo, otro");
		assertEquals(0, r.get(1).get("pagos").asLong());
	}

	// ------------------------------------------------------------------ eliminar anonimizando

	@Test
	void anonimizar_conservaLoPagado_yLaTesoreriaDeLoPagadoNoCambia() throws Exception {
		// Marzo de 2026: en esta escuela solo tiene cargos este alumno, así que el resumen es exactamente lo suyo.
		long alumno = e.alumno("IT8B3LBISTESORERIA" + e.marca, e.sA, true);
		long pagado = cargo(alumno, "30.00", true, "2026-03-02 12:00:00", null);
		cobro(alumno, pagado, "30.00", "ACTIVO");
		long pendiente = cargo(alumno, "20.00", false, "2026-03-02 12:00:00", null);
		String loPagado = cargoYCobros(pagado);
		JsonNode antes = leer("/api/tesoreria/resumen?mes=3&ano=2026&soloActivos=false", tokenPropietario);
		assertEquals(30.0, antes.get("importePagado").asDouble(), "lo pagado del alumno está en el resumen");
		assertEquals(1, antes.get("totalPagados").asLong());
		assertEquals(20.0, antes.get("importePendiente").asDouble(), "y lo pendiente");

		assertEquals(200, estado(post("/api/alumnos/" + alumno + "/anonimizar"), tokenPropietario));

		assertEquals(loPagado, cargoYCobros(pagado), "el cargo pagado y su cobro, iguales y con el mismo alumno");
		assertEquals(1, contar("SELECT COUNT(*) FROM producto_alumno WHERE id = ? AND alumno_id = ? "
				+ "AND cancelado_en IS NOT NULL", pendiente, alumno), "el pendiente se cancela, no se borra");
		JsonNode despues = leer("/api/tesoreria/resumen?mes=3&ano=2026&soloActivos=false", tokenPropietario);
		assertEquals(antes.get("importePagado"), despues.get("importePagado"), "lo pagado no cambia");
		assertEquals(antes.get("totalPagados"), despues.get("totalPagados"));
		assertEquals(antes.get("importeDevuelto"), despues.get("importeDevuelto"));
		assertEquals(0.0, despues.get("importePendiente").asDouble(), "lo pendiente cancelado ya no suma");
		assertEquals("Alumno eliminado", jdbc.queryForObject(
				"SELECT CONCAT(nombre, ' ', apellidos) FROM alumno WHERE id = ?", String.class, alumno));
	}

	@Test
	void anonimizar_cancelaLosCargosSinPagar_losMismosQueAvisa() throws Exception {
		long alumno = e.alumno("IT8B3LBISCANCELA" + e.marca, e.sA, true);
		e.taekwondo(alumno, false);
		long inscripcion = jdbc.queryForObject("SELECT MAX(id) FROM alumno_deporte WHERE alumno_id = ?", Long.class,
				alumno);
		long pagado = cargo(alumno, "30.00", true);
		cobro(alumno, pagado, "30.00", "ACTIVO");
		// Un cobro ANULADO no impide cancelar (la misma regla que cancelar a mano); uno ACTIVO sí, aunque el cargo no
		// figure como pagado (datos antiguos).
		long conCobroAnulado = cargo(alumno, "15.00", false);
		cobro(alumno, conCobroAnulado, "15.00", "ANULADO");
		long conCobroActivo = cargo(alumno, "8.00", false);
		cobro(alumno, conCobroActivo, "8.00", "ACTIVO");
		long yaCancelado = cargo(alumno, "12.00", false);
		jdbc.update("UPDATE producto_alumno SET cancelado_en = '2026-09-03 10:00:00', motivo_cancelacion = 'Antes' "
				+ "WHERE id = ?", yaCancelado);
		long propio = cargo(alumno, "20.00", false);
		long deInscripcion = cargo(alumno, "25.00", false, "2026-09-02 12:00:00", inscripcion);
		long deConvocatoria = cargo(alumno, "10.00", false);
		long convocatoria = convocatoria();
		jdbc.update("""
				INSERT INTO alumno_convocatoria (cuantia_examen, pagado, alumno_id, convocatoria_id, grado_actual,
				  grado_siguiente, organizacion_id, producto_alumno_id) VALUES (10, 0, ?, ?, NULL, NULL, ?, ?)""",
				alumno, convocatoria, e.org, deConvocatoria);
		String intactos = cargoYCobros(pagado) + cargoYCobros(conCobroActivo) + cargoYCobros(yaCancelado);
		String cobros = jdbc.queryForList("SELECT * FROM cobro WHERE alumno_id = ? ORDER BY id", alumno).toString();
		JsonNode aviso = leer("/api/alumnos/cargos-resumen?alumnoIds=" + alumno, tokenPropietario).get(0);

		assertEquals(200, estado(post("/api/alumnos/" + alumno + "/anonimizar"), tokenPropietario));

		List<Long> esperados = List.of(conCobroAnulado, propio, deInscripcion, deConvocatoria);
		for (long cancelado : esperados) {
			Map<String, Object> fila = jdbc.queryForMap(
					"SELECT cancelado_en, motivo_cancelacion, pagado FROM producto_alumno WHERE id = ?", cancelado);
			assertNotNull(fila.get("cancelado_en"), cancelado + " cancelado");
			assertEquals("Alumno eliminado", fila.get("motivo_cancelacion"));
			assertEquals(Boolean.FALSE, toBool(fila.get("pagado")));
		}
		assertEquals(esperados.size(), aviso.get("pendientes").asLong(), "el aviso cuenta los que se cancelan");
		assertEquals(0, new BigDecimal("70.00").compareTo(aviso.get("importePendiente").decimalValue()),
				"y su importe: " + aviso);
		assertEquals(intactos, cargoYCobros(pagado) + cargoYCobros(conCobroActivo) + cargoYCobros(yaCancelado),
				"lo pagado, lo que tiene un cobro activo y lo ya cancelado no cambian");
		assertEquals(cobros, jdbc.queryForList("SELECT * FROM cobro WHERE alumno_id = ? ORDER BY id", alumno).toString(),
				"los cobros no cambian");
		assertEquals(1, contar("SELECT COUNT(*) FROM alumno_convocatoria WHERE producto_alumno_id = ?", deConvocatoria));
	}

	@Test
	void anonimizado_susCargosNoSeReactivanNiBorranNiEditan_409_peroSeRevierteLoCobrado() throws Exception {
		long alumno = e.alumno("IT8B3LBISREACTIVAR" + e.marca, e.sA, true);
		long pagado = cargo(alumno, "30.00", true);
		cobro(alumno, pagado, "30.00", "ACTIVO");
		long pendiente = cargo(alumno, "20.00", false);
		assertEquals(200, estado(post("/api/alumnos/" + alumno + "/anonimizar"), tokenPropietario));
		String antes = cargoYCobros(pendiente);

		MvcResult reactivar = mvc.perform(post("/api/productos-alumno/" + pendiente + "/reactivar")
				.cookie(jwt(tokenPropietario))).andReturn();
		assertEquals(409, reactivar.getResponse().getStatus(), reactivar.getResponse().getContentAsString());
		assertEquals(ANONIMIZADO, json.readTree(reactivar.getResponse().getContentAsString()).get("mensaje").asText());
		assertEquals(409, estado(delete("/api/productos-alumno/" + pendiente), tokenPropietario), "ni se borra");
		assertEquals(409, estado(put("/api/productos-alumno/" + pendiente).contentType(MediaType.APPLICATION_JSON)
				.content("{\"notas\":\"vuelve\"}"), tokenPropietario), "ni se editan sus notas");
		assertEquals(409, estado(put("/api/productos-alumno/" + pagado).contentType(MediaType.APPLICATION_JSON)
				.content("{\"precio\":99}"), tokenPropietario), "ni el precio de lo cobrado");
		assertEquals(antes, cargoYCobros(pendiente), "el cargo sigue cancelado e igual");

		// La tesorería sí revierte un cobro suyo (devolución del dinero).
		assertEquals(200, estado(put("/api/productos-alumno/" + pagado).contentType(MediaType.APPLICATION_JSON)
				.content("{\"pagado\":false,\"motivoCambio\":\"devolución\"}"), tokenPropietario));
		assertEquals(1, contar("SELECT COUNT(*) FROM cobro WHERE producto_alumno_id = ? AND estado = 'ANULADO'", pagado));
	}

	@Test
	void anonimizado_propietarioDeOtraEscuela_noRecibe409() throws Exception {
		long alumno = e.alumno("IT8B3LBISCRUZADO" + e.marca, e.sA, true);
		long pendiente = cargo(alumno, "20.00", false);
		assertEquals(200, estado(post("/api/alumnos/" + alumno + "/anonimizar"), tokenPropietario));
		String antes = foto(alumno);

		int observaciones = estado(put("/api/alumnos/" + alumno + "/observaciones")
				.contentType(MediaType.APPLICATION_JSON).content("{\"observaciones\":\"x\"}"), tokenOtraPropietario);
		assertTrue(observaciones == 404 || observaciones == 403, "lo normal de otra escuela, no 409: " + observaciones);
		int reactivar = estado(post("/api/productos-alumno/" + pendiente + "/reactivar"), tokenOtraPropietario);
		assertTrue(reactivar == 404 || reactivar == 403, "lo normal de otra escuela, no 409: " + reactivar);
		assertEquals(antes, foto(alumno));
	}

	@Test
	void peticionAnonima_conAlumnoAnonimizado_noRecibe409() throws Exception {
		// Una petición sin sesión va a la escuela por defecto: no debe delatar si un id suyo está anonimizado.
		long expediente = jdbc.queryForObject("SELECT COALESCE(MAX(numero_expediente), 60000) + 1 FROM alumno",
				Long.class);
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id, anonimizado_en)
				VALUES (0, 'eliminado', 0, '', '2010-01-01', 'Alumno', ?, '', 0, 1, NOW())""", expediente);
		long alumno = jdbc.queryForObject(
				"SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = 1", Long.class, expediente);
		try {
			MvcResult r = mvc.perform(post("/api/preinscripciones?alumnoId=" + alumno)
					.contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
			assertFalse(r.getResponse().getStatus() == 409, r.getResponse().getContentAsString());
		} finally {
			jdbc.update("DELETE FROM alumno WHERE id = ?", alumno);
		}
	}

	// ------------------------------------------------------------------ anonimizado: solo lectura

	@Test
	void anonimizado_noSeEdita_409_yNoCambiaNada() throws Exception {
		long alumno = e.alumno("IT8B3LBISEDITAR" + e.marca, e.sA, true);
		e.taekwondo(alumno, false);
		cargo(alumno, "30.00", true);
		assertEquals(200, estado(post("/api/alumnos/" + alumno + "/anonimizar"), tokenPropietario));
		String antes = foto(alumno) + "|" + jdbc.queryForList(
				"SELECT * FROM alumno_deporte WHERE alumno_id = ? ORDER BY id", alumno) + "|" + jdbc.queryForList(
				"SELECT * FROM alumno_grupo WHERE alumno_id = ?", alumno);
		String ficha = """
				{"nombre":"Lucía","apellidos":"Recuperada","fechaNacimiento":"2010-01-01","direccion":"Calle 2",
				 "email":"lucia.recuperada@example.test","telefono":"600000001"}""";

		MvcResult editar = mvc.perform(multipart(HttpMethod.PUT, "/api/alumnos/" + alumno).param("alumnoEditado", ficha)
				.cookie(jwt(tokenPropietario))).andReturn();
		assertEquals(409, editar.getResponse().getStatus(), editar.getResponse().getContentAsString());
		assertEquals(ANONIMIZADO, json.readTree(editar.getResponse().getContentAsString()).get("mensaje").asText());
		assertEquals(409, estado(put("/api/alumnos/" + alumno + "/deportes/TAEKWONDO/activar"), tokenPropietario));
		assertEquals(409, estado(put("/api/alumnos/" + alumno + "/observaciones").contentType(MediaType.APPLICATION_JSON)
				.content("{\"observaciones\":\"vuelve\"}"), tokenPropietario));
		assertEquals(409, estado(post("/api/grupos/" + e.gA + "/alumnos/" + alumno), tokenPropietario));
		assertEquals(409, estado(post("/api/productos-alumno/mensualidades/individual?alumnoId=" + alumno)
				.contentType(MediaType.TEXT_PLAIN).content("2031-05"), tokenPropietario), "ni cargos nuevos");

		assertEquals(antes, foto(alumno) + "|" + jdbc.queryForList(
				"SELECT * FROM alumno_deporte WHERE alumno_id = ? ORDER BY id", alumno) + "|" + jdbc.queryForList(
				"SELECT * FROM alumno_grupo WHERE alumno_id = ?", alumno), "la ficha, sus deportes y grupos, iguales");
		assertEquals(200, estado(get("/api/alumnos/" + alumno), tokenPropietario), "leerlo sigue funcionando");
	}

	@Test
	void generarMensualidades_saltaAlAnonimizado() throws Exception {
		long alumno = e.alumno("IT8B3LBISMENSUAL" + e.marca, e.sA, true);
		e.taekwondo(alumno, false);
		cargo(alumno, "30.00", true);
		TenantContext.ejecutarCon(e.org, () -> DisciplinasDePrueba.tarifa(tarifas, "ADULTO", "35.00", null));
		long tarifa = jdbc.queryForObject("SELECT MAX(id) FROM tarifa WHERE organizacion_id = ? AND codigo = 'ADULTO'",
				Long.class, e.org);
		jdbc.update("INSERT INTO producto (concepto, precio, organizacion_id) VALUES ('MENSUALIDAD', 30.0, ?)", e.org);
		jdbc.update("UPDATE alumno_deporte SET tarifa_id = ?, cuantia_tarifa = 35.0 WHERE organizacion_id = ?", tarifa,
				e.org);
		assertEquals(200, estado(post("/api/alumnos/" + alumno + "/anonimizar"), tokenPropietario));
		// Un deporte que se quedó activo (datos antiguos o a mano): la generación tampoco le carga nada.
		jdbc.update("UPDATE alumno_deporte SET activo = TRUE, fecha_baja = NULL WHERE alumno_id = ?", alumno);
		int antes = contar("SELECT COUNT(*) FROM producto_alumno WHERE alumno_id = ?", alumno);

		assertEquals(200, estado(post("/api/productos-alumno/mensualidades/general?fechaAsignacion=2031-04-01")
				.contentType(MediaType.TEXT_PLAIN).content("2031-04"), tokenPropietario));
		assertEquals(200, estado(post("/api/productos-alumno/mensualidades/deporte?deporte=TAEKWONDO"
				+ "&fechaAsignacion=2031-04-01").contentType(MediaType.TEXT_PLAIN).content("2031-04"),
				tokenPropietario));

		assertEquals(antes, contar("SELECT COUNT(*) FROM producto_alumno WHERE alumno_id = ?", alumno),
				"ningún cargo nuevo para el alumno eliminado");
		assertTrue(contar("SELECT COUNT(*) FROM producto_alumno WHERE alumno_id = ? AND concepto LIKE '%2031%'",
				e.aA) > 0, "a los demás sí");
	}

	@Test
	void responsableDeSede_sigueViendoConvocatoriaYTesoreriaDelAnonimizado() throws Exception {
		String tokenResponsable = e.token(jwtService, usuarios, e.respA);
		long alumno = e.alumno("IT8B3LBISSEDE" + e.marca, e.sA, true);
		long pagado = cargo(alumno, "30.00", true);
		cobro(alumno, pagado, "30.00", "ACTIVO");
		long convocatoria = convocatoria();
		jdbc.update("""
				INSERT INTO alumno_convocatoria (cuantia_examen, pagado, alumno_id, convocatoria_id, grado_actual,
				  grado_siguiente, organizacion_id, producto_alumno_id) VALUES (30, 1, ?, ?, NULL, NULL, ?, ?)""",
				alumno, convocatoria, e.org, pagado);
		String inscritos = "/api/convocatorias/" + convocatoria + "/alumnos";
		String movimientos = "/api/tesoreria/movimientos?mes=9&ano=2026&soloActivos=false&size=500";
		assertTrue(alumnosDe(leer(inscritos, tokenResponsable)).contains(alumno), "antes lo ve");
		assertTrue(cargosDe(leer(movimientos, tokenResponsable)).contains(pagado));

		assertEquals(200, estado(post("/api/alumnos/" + alumno + "/anonimizar"), tokenPropietario));

		assertTrue(alumnosDe(leer(inscritos, tokenResponsable)).contains(alumno), "la inscripción sigue a la vista");
		assertEquals(alumnosDe(leer(inscritos, tokenPropietario)).contains(alumno),
				alumnosDe(leer(inscritos, tokenResponsable)).contains(alumno), "como para el propietario");
		JsonNode fila = null;
		for (JsonNode m : leer(movimientos, tokenResponsable).get("content")) {
			if (m.get("productoAlumnoId").asLong() == pagado) {
				fila = m;
			}
		}
		assertNotNull(fila, "y su cargo en la tesorería de la sede");
		assertTrue(fila.get("alumnoEliminado").asBoolean(), "marcado como eliminado (sin enlace a su ficha)");
		assertEquals("Alumno eliminado", fila.get("alumnoNombreCompleto").asText());
	}

	@Test
	void anonimizar_quitaLosDatosPersonales_conSusArchivos() throws Exception {
		long alumno = e.alumno("IT8B3LBISDATOS" + e.marca, e.sA, true);
		long pagado = cargo(alumno, "30.00", true);
		jdbc.update("""
				UPDATE alumno SET nif = '12345678Z', email = 'lucia@example.test', telefono2 = '611111111',
				  localidad = 'Jaén', codigo_postal = '23001', observaciones = 'Notas', genero = 'FEMENINO'
				WHERE id = ?""", alumno);
		Path fotoArchivo = archivo("target/it-files/imagenes/org-" + e.org + "/alumnos/it8b3lbis-" + e.marca + ".jpg");
		jdbc.update("INSERT INTO imagen (nombre, ruta, tipo, url, organizacion_id) VALUES (?, ?, 'image/jpeg', ?, ?)",
				"it8b3lbis-" + e.marca + ".jpg", "org-" + e.org + "/alumnos/it8b3lbis-" + e.marca + ".jpg",
				"/imagenes/org-" + e.org + "/alumnos/it8b3lbis-" + e.marca + ".jpg", e.org);
		long imagen = jdbc.queryForObject("SELECT MAX(id) FROM imagen WHERE organizacion_id = ?", Long.class, e.org);
		jdbc.update("UPDATE alumno SET foto_alumno_id = ? WHERE id = ?", imagen, alumno);
		String rutaDoc = "org-" + e.org + "/alumnos/it8b3lbis-" + e.marca + "/ficha.pdf";
		Path docArchivo = archivo("target/it-files/documentos/" + rutaDoc);
		jdbc.update("""
				INSERT INTO documento (nombre, ruta, tipo, url, alumno_id, organizacion_id)
				VALUES ('ficha.pdf', ?, 'application/pdf', NULL, ?, ?)""", rutaDoc, alumno, e.org);
		jdbc.update("""
				INSERT INTO tutor (organizacion_id, alumno_id, nombre, nif, parentesco, responsable_legal, orden)
				VALUES (?, ?, 'Madre Prueba', NULL, 'MADRE', TRUE, 1)""", e.org, alumno);
		jdbc.update("""
				INSERT INTO alumno_salud (alumno_id, organizacion_id, alergias, origen, actualizado_en)
				VALUES (?, ?, 'x', 'APP', NOW(6))""", alumno, e.org);
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", e.gA, alumno);
		e.enTurno(e.tA, alumno);
		long cuenta = e.cuenta(usuarios, "familia8b3lbis");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'FAMILIA')", cuenta, e.org);
		jdbc.update("UPDATE usuario SET alumno_id = ? WHERE id = ?", alumno, cuenta);
		jdbc.update("""
				INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen)
				VALUES (?, ?, ?, 'TUTOR', 'MANUAL')""", cuenta, alumno, e.org);

		assertEquals(200, estado(post("/api/alumnos/" + alumno + "/anonimizar"), tokenPropietario));

		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM alumno WHERE id = ?", alumno);
		assertEquals("Alumno", fila.get("nombre"));
		assertEquals("eliminado", fila.get("apellidos"));
		for (String campo : List.of("nif", "email", "telefono", "telefono2", "localidad", "codigo_postal",
				"observaciones", "genero", "foto_alumno_id")) {
			assertNull(fila.get(campo), campo + " borrado");
		}
		assertEquals("Sin datos", fila.get("direccion"));
		assertEquals("1900-01-01", fila.get("fecha_nacimiento").toString());
		assertEquals(Boolean.FALSE, toBool(fila.get("activo")));
		assertNotNull(fila.get("fecha_baja"));
		assertNotNull(fila.get("anonimizado_en"));
		assertFalse(Files.exists(fotoArchivo), "el archivo de la foto se borra");
		assertFalse(Files.exists(docArchivo), "el archivo del documento se borra");
		assertEquals(0, contar("SELECT COUNT(*) FROM imagen WHERE id = ?", imagen));
		assertEquals(0, contar("SELECT COUNT(*) FROM documento WHERE alumno_id = ?", alumno));
		assertEquals(0, contar("SELECT COUNT(*) FROM tutor WHERE alumno_id = ?", alumno));
		assertEquals(0, contar("SELECT COUNT(*) FROM alumno_salud WHERE alumno_id = ?", alumno));
		assertEquals(0, contar("SELECT COUNT(*) FROM alumno_grupo WHERE alumno_id = ?", alumno));
		assertEquals(0, contar("SELECT COUNT(*) FROM alumno_turno WHERE alumno_id = ?", alumno));
		assertEquals(0, contar("SELECT COUNT(*) FROM usuario_alumno WHERE alumno_id = ?", alumno));
		assertEquals(0, contar("SELECT COUNT(*) FROM usuario WHERE id = ?", cuenta), "sin membresías, la cuenta cae");
		assertEquals(0, contar("SELECT COUNT(*) FROM alumno_deporte WHERE alumno_id = ? AND activo = TRUE", alumno));
		assertEquals(1, contar("SELECT COUNT(*) FROM producto_alumno WHERE id = ? AND alumno_id = ?", pagado, alumno));
	}

	@Test
	void anonimizar_gestor_403ConMensaje_yNoCambiaNada() throws Exception {
		long alumno = e.alumno("IT8B3LBISGESTOR" + e.marca, e.sA, true);
		cargo(alumno, "30.00", true);
		String antes = foto(alumno);

		MvcResult r = mvc.perform(post("/api/alumnos/" + alumno + "/anonimizar").cookie(jwt(tokenGestor))).andReturn();

		assertEquals(403, r.getResponse().getStatus());
		assertEquals(SOLO_PROPIETARIO, json.readTree(r.getResponse().getContentAsString()).get("mensaje").asText());
		assertEquals(antes, foto(alumno));
	}

	@Test
	void anonimizar_propietarioDeOtraEscuela_404_yNoCambiaNada() throws Exception {
		long alumno = e.alumno("IT8B3LBISAJENO" + e.marca, e.sA, true);
		cargo(alumno, "30.00", true);
		String antes = foto(alumno);

		assertEquals(404, estado(post("/api/alumnos/" + alumno + "/anonimizar"), tokenOtraPropietario));
		assertEquals(antes, foto(alumno));
	}

	@Test
	void anonimizado_noSaleEnLosListados_niPuedeVolverDeAlta() throws Exception {
		String nombre = "IT8B3LBISLISTA" + e.marca;
		long alumno = e.alumno(nombre, e.sA, true);
		e.taekwondo(alumno, false);
		cargo(alumno, "30.00", true);
		assertTrue(leer("/api/alumnos/ids?incluirInactivos=true", tokenPropietario).toString()
				.contains(String.valueOf(alumno)), "antes sale");

		assertEquals(200, estado(post("/api/alumnos/" + alumno + "/anonimizar"), tokenPropietario));

		assertFalse(ids(leer("/api/alumnos/ids?incluirInactivos=true", tokenPropietario)).contains(alumno));
		assertFalse(ids(leer("/api/alumnos?incluirInactivos=true", tokenPropietario)).contains(alumno));
		assertFalse(ids(leer("/api/alumnos?page=1&size=100&incluirInactivos=true&nombre=Alumno%20eliminado",
				tokenPropietario).get("content")).contains(alumno), "ni buscando por su nombre nuevo");
		assertFalse(ids(leer("/api/alumnos?page=1&size=100&incluirInactivos=true&deporte=TAEKWONDO",
				tokenPropietario).get("content")).contains(alumno), "ni por deporte");
		assertEquals(409, estado(put("/api/alumnos/" + alumno + "/alta"), tokenPropietario), "es irreversible");
		assertEquals(0, contar("SELECT COUNT(*) FROM alumno WHERE id = ? AND activo = TRUE", alumno));
	}

	@Test
	void anonimizar_dosVeces_esInofensivo() throws Exception {
		long alumno = e.alumno("IT8B3LBISDOBLE" + e.marca, e.sA, true);
		long pagado = cargo(alumno, "30.00", true);
		cobro(alumno, pagado, "30.00", "ACTIVO");

		assertEquals(200, estado(post("/api/alumnos/" + alumno + "/anonimizar"), tokenPropietario));
		String despues = foto(alumno);
		assertEquals(200, estado(post("/api/alumnos/" + alumno + "/anonimizar"), tokenPropietario));
		assertEquals(despues, foto(alumno), "la segunda vez no cambia nada");
		assertEquals(409, estado(delete("/api/alumnos/" + alumno), tokenPropietario), "y sigue sin borrarse");
	}

	// ------------------------------------------------------------------ ayudantes

	private long propietario(EscuelaConSedesDePrueba escuela, String nombre) {
		long id = escuela.cuenta(usuarios, nombre);
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROPIETARIO')", id,
				escuela.org);
		return id;
	}

	private long producto(EscuelaConSedesDePrueba escuela) {
		jdbc.update("INSERT INTO producto (concepto, precio, codigo, tipo, organizacion_id) VALUES (?, 1.0, ?, 'OTRO', ?)",
				"CARGO IT 8B3LBIS", "CARGO_IT_8B3LBIS", escuela.org);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = ?", Long.class, escuela.org);
	}

	private long cargo(long alumno, String precio, boolean pagado) {
		return cargo(alumno, precio, pagado, "2026-09-02 12:00:00", null);
	}

	private long cargo(long alumno, String precio, boolean pagado, String fecha, Long alumnoDeporte) {
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, producto_id, concepto, precio, cantidad, pagado, fecha_asignacion,
				  fecha_pago, organizacion_id, sede_id, alumno_deporte_id)
				VALUES (?, ?, 'CARGO IT 8B3LBIS', ?, 1, ?, ?, ?, ?, ?, ?)""", alumno, producto,
				new BigDecimal(precio), pagado, fecha, pagado ? fecha : null, e.org, e.sA, alumnoDeporte);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE alumno_id = ?", Long.class, alumno);
	}

	private long convocatoria() {
		jdbc.update("INSERT INTO convocatoria (fecha_convocatoria, organizacion_id, disciplina_id) VALUES "
				+ "('2026-06-01', ?, ?)", e.org, e.disciplina);
		return jdbc.queryForObject("SELECT MAX(id) FROM convocatoria WHERE organizacion_id = ?", Long.class, e.org);
	}

	/** Un cargo (todas sus columnas) y sus cobros, para comparar antes y después. */
	private String cargoYCobros(long cargo) {
		return jdbc.queryForList("SELECT * FROM producto_alumno WHERE id = ?", cargo) + "|"
				+ jdbc.queryForList("SELECT * FROM cobro WHERE producto_alumno_id = ? ORDER BY id", cargo);
	}

	private static List<Long> alumnosDe(JsonNode lista) {
		List<Long> r = new java.util.ArrayList<>();
		lista.forEach(n -> r.add(n.get("alumnoId").asLong()));
		return r;
	}

	private static List<Long> cargosDe(JsonNode pagina) {
		List<Long> r = new java.util.ArrayList<>();
		pagina.get("content").forEach(n -> r.add(n.get("productoAlumnoId").asLong()));
		return r;
	}

	private void cobro(long alumno, Long cargo, String importe, String estado) {
		jdbc.update("""
				INSERT INTO cobro (organizacion_id, producto_alumno_id, sede_id, alumno_id, concepto, importe, metodo,
				  fecha_cobro, origen, estado) VALUES (?, ?, ?, ?, 'CARGO IT 8B3LBIS', ?, 'EFECTIVO', '2026-09-02 12:00:00',
				  'TESORERIA', ?)""", e.org, cargo, e.sA, alumno, new BigDecimal(importe), estado);
	}

	/** La fila del alumno, sus cargos y sus cobros, para comparar antes y después. */
	private String foto(long alumno) {
		return jdbc.queryForList("SELECT * FROM alumno WHERE id = ?", alumno) + "|" + cargosYCobros(alumno);
	}

	private String cargosYCobros(long alumno) {
		return jdbc.queryForList("""
				SELECT id, alumno_id, precio, pagado, fecha_pago, cancelado_en FROM producto_alumno
				WHERE alumno_id = ? ORDER BY id""", alumno) + "|" + jdbc.queryForList("""
				SELECT id, producto_alumno_id, alumno_id, importe, estado, entrega_caja_id FROM cobro
				WHERE alumno_id = ? ORDER BY id""", alumno);
	}

	private static Path archivo(String ruta) throws IOException {
		Path p = Path.of(ruta).toAbsolutePath();
		Files.createDirectories(p.getParent());
		Files.writeString(p, "it");
		return p;
	}

	private static Boolean toBool(Object v) {
		return v instanceof Boolean b ? b : v == null ? null : ((Number) v).intValue() != 0;
	}

	private static List<Long> ids(JsonNode lista) {
		List<Long> r = new java.util.ArrayList<>();
		lista.forEach(n -> r.add(n.isNumber() ? n.asLong() : n.get("id").asLong()));
		return r;
	}

	private int contar(String sql, Object... args) {
		return jdbc.queryForObject(sql, Integer.class, args);
	}

	private int estado(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(jwt(token))).andReturn().getResponse().getStatus();
	}

	private JsonNode leer(String ruta, String token) throws Exception {
		MvcResult r = mvc.perform(get(ruta).cookie(jwt(token))).andReturn();
		assertEquals(200, r.getResponse().getStatus(), ruta + ": " + r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString());
	}

	private static Cookie jwt(String token) {
		return new Cookie("jwt", token);
	}
}
