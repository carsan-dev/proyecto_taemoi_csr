package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.config.ValidacionRelojConfig;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.pdf.ContenidoPdf;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza 279 contra MySQL: {@code GET /api/productos-alumno/{productoAlumnoId}/recibo} genera al momento el recibo en
 * PDF del cobro ACTIVO del cargo. Gestión y, pieza 9-5, el responsable de la sede del cargo (propietario y administrador); el responsable de sede, la familia y
 * la sesión solo-profesor, 403; sin sesión, 401. Cargo sin cobrar, o con el cobro anulado o devuelto, 409; cargo de otra escuela,
 * 404 (aislamiento por organización). El PDF lleva los datos de la escuela, el número, el alumno (solo nombre y
 * apellidos), el concepto, el importe, la forma de pago, la sede y el pie; ni DNI, ni contacto del alumno, ni quién
 * cobró. Escuelas sintéticas de {@link EscuelaConSedesDePrueba}; datos inventados. «Hoy»: 15/10/2026 (Europe/Madrid)
 * con un {@code Clock} simulado.
 *
 * <p>Cargos de legado (pagados antes de V31_5, sin ninguna fila de cobro): el recibo sale del propio cargo, con la
 * serie {@code R-{año del pago}-C{id del cargo con 6 cifras}}, el importe precio × cantidad ({@code ImporteCargo}), la
 * fecha de pago y la sede del cargo, sin forma de pago (el cargo no la guarda). Cancelado, devuelto (DEVOLUCION ACTIVA
 * enlazada al cargo en la caja) o sin fecha de pago, 409.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReciboPagoIntegrationTest extends MySqlIntegrationTest {

	private static final String DNI_ALUMNO = "12345678Z";
	private static final String EMAIL_ALUMNO = "alumna.recibo.it@example.test";
	private static final String TELEFONO_ALUMNO = "699111222";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;
	@MockitoBean Clock reloj;
	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	String tokenGestor, tokenPropietario, tokenResponsable, tokenFamilia, tokenProfesor, tokenOtraGestor;
	long cargoCobrado, cobroCobrado, cargoSinCobrar, cargoAnulado, cargoDevuelto, cargoDevolucionAnulada, cargoOtra;
	long legado, legadoCantidadNula, legadoSinSede, legadoDevuelto, legadoDevolucionAnulada, legadoSinFecha,
			legadoCancelado, legadoAnuladoPagado, legadoOtra;
	String nombreAlumno, nombreSede;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-279-recibo");
		jdbc.update("""
				UPDATE organizacion SET nombre_legal = 'Club Deportivo Recibo IT', cif = 'G11111111',
				  direccion = 'Calle Ficticia 7', codigo_postal = '41000', localidad = 'Villaprueba',
				  provincia = 'Sevilla', email_contacto = 'contacto@recibo-it.example', telefono = '955000111'
				WHERE id = ?""", e.org);
		jdbc.update("UPDATE alumno SET nif = ?, email = ?, telefono = ? WHERE id = ?", DNI_ALUMNO, EMAIL_ALUMNO,
				TELEFONO_ALUMNO, e.aA);
		nombreAlumno = jdbc.queryForObject("SELECT CONCAT(nombre, ' ', apellidos) FROM alumno WHERE id = ?",
				String.class, e.aA);
		long producto = producto(e);
		long propietario = e.cuenta(usuarios, "propietario279");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROPIETARIO')",
				propietario, e.org);
		long profesor = e.cuenta(usuarios, "profesor279");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROFESOR')", profesor,
				e.org);

		cargoCobrado = cargo(e, producto, "MENSUALIDAD OCTUBRE 2026 - TAEKWONDO", true);
		cobroCobrado = cobro(e, cargoCobrado, "MENSUALIDAD OCTUBRE 2026 - TAEKWONDO", "BIZUM", "ACTIVO");
		cargoSinCobrar = cargo(e, producto, "MENSUALIDAD NOVIEMBRE 2026 - TAEKWONDO", false);
		cargoAnulado = cargo(e, producto, "MENSUALIDAD SEPTIEMBRE 2026 - TAEKWONDO", false);
		cobro(e, cargoAnulado, "MENSUALIDAD SEPTIEMBRE 2026 - TAEKWONDO", "EFECTIVO", "ANULADO");
		cargoDevuelto = cargo(e, producto, "MENSUALIDAD AGOSTO 2026 - TAEKWONDO", true);
		devolucion(e, cargoDevuelto,
				cobro(e, cargoDevuelto, "MENSUALIDAD AGOSTO 2026 - TAEKWONDO", "EFECTIVO", "ACTIVO"), "ACTIVO");
		cargoDevolucionAnulada = cargo(e, producto, "MENSUALIDAD JULIO 2026 - TAEKWONDO", true);
		devolucion(e, cargoDevolucionAnulada,
				cobro(e, cargoDevolucionAnulada, "MENSUALIDAD JULIO 2026 - TAEKWONDO", "EFECTIVO", "ACTIVO"), "ANULADO");

		nombreSede = jdbc.queryForObject("SELECT nombre FROM sede WHERE id = ?", String.class, e.sA);
		legado = legado(e, producto, "CAMISETA LEGADO IT", 14.50, 2, "2025-03-10 19:00:00", e.sA);
		legadoCantidadNula = legado(e, producto, "MENSUALIDAD MARZO 2025 - TAEKWONDO", 28.00, null,
				"2025-03-05 10:00:00", e.sA);
		legadoSinSede = legado(e, producto, "MENSUALIDAD FEBRERO 2025 - TAEKWONDO", 28.00, 1, "2025-02-05 10:00:00",
				null);
		legadoDevuelto = legado(e, producto, "MENSUALIDAD ENERO 2025 - TAEKWONDO", 28.00, 1, "2025-01-05 10:00:00",
				e.sA);
		devolucion(e, legadoDevuelto, null, "ACTIVO");
		legadoDevolucionAnulada = legado(e, producto, "MENSUALIDAD DICIEMBRE 2024 - TAEKWONDO", 28.00, 1,
				"2024-12-05 10:00:00", e.sA);
		devolucion(e, legadoDevolucionAnulada, null, "ANULADO");
		legadoSinFecha = legado(e, producto, "MENSUALIDAD NOVIEMBRE 2024 - TAEKWONDO", 28.00, 1, null, e.sA);
		legadoCancelado = legado(e, producto, "MENSUALIDAD OCTUBRE 2024 - TAEKWONDO", 28.00, 1, "2024-10-05 10:00:00",
				e.sA);
		jdbc.update("UPDATE producto_alumno SET cancelado_en = '2024-10-06 10:00:00', motivo_cancelacion = 'IT' "
				+ "WHERE id = ?", legadoCancelado);
		legadoAnuladoPagado = legado(e, producto, "MENSUALIDAD SEPTIEMBRE 2024 - TAEKWONDO", 28.00, 1,
				"2024-09-05 10:00:00", e.sA);
		cobro(e, legadoAnuladoPagado, "MENSUALIDAD SEPTIEMBRE 2024 - TAEKWONDO", "EFECTIVO", "ANULADO");

		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-279-otra");
		long productoOtra = producto(otra);
		cargoOtra = cargo(otra, productoOtra, "MENSUALIDAD OCTUBRE 2026 - TAEKWONDO", true);
		cobro(otra, cargoOtra, "MENSUALIDAD OCTUBRE 2026 - TAEKWONDO", "EFECTIVO", "ACTIVO");
		legadoOtra = legado(otra, productoOtra, "MENSUALIDAD MARZO 2025 - TAEKWONDO", 28.00, 1, "2025-03-05 10:00:00",
				otra.sA);

		tokenGestor = e.token(jwtService, usuarios, e.gestor);
		tokenPropietario = e.token(jwtService, usuarios, propietario);
		tokenResponsable = e.token(jwtService, usuarios, e.respA);
		tokenFamilia = e.token(jwtService, usuarios, e.cuentaFamilia(usuarios, "familia279"));
		tokenProfesor = e.token(jwtService, usuarios, profesor);
		tokenOtraGestor = otra.token(jwtService, usuarios, otra.gestor);
	}

	@BeforeEach
	void hoy() {
		when(reloj.instant()).thenReturn(
				LocalDateTime.parse("2026-10-15T10:00").atZone(ValidacionRelojConfig.ZONA).toInstant());
		when(reloj.getZone()).thenReturn(ValidacionRelojConfig.ZONA);
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	@Test
	void gestion_descargaElReciboEnPdf() throws Exception {
		MvcResult r = pedir(cargoCobrado, tokenGestor);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertEquals("application/pdf", r.getResponse().getContentType());
		String numero = String.format("R-2026-%06d", cobroCobrado);
		assertEquals("attachment; filename=\"recibo-" + numero + ".pdf\"",
				r.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION));

		String texto = texto(r.getResponse().getContentAsByteArray());
		for (String esperado : new String[] { "Recibo", numero, "02/09/2026", nombreAlumno,
				"MENSUALIDAD OCTUBRE 2026 - TAEKWONDO", "28,00 €", "Bizum", "IT E6 A " + e.marca,
				"Club Deportivo Recibo IT", "G11111111", "Calle Ficticia 7", "41000 Villaprueba", "Sevilla",
				"contacto@recibo-it.example", "955000111",
				"Este documento justifica el pago del concepto indicado. No es una factura.",
				"Emitido el 15/10/2026" }) {
			assertTrue(texto.contains(esperado), "falta «" + esperado + "» en:\n" + texto);
		}
		for (String prohibido : new String[] { DNI_ALUMNO, EMAIL_ALUMNO, TELEFONO_ALUMNO, "Calle 1",
				"it-e6-gestor-" + e.marca, "Usuario" }) {
			assertFalse(texto.contains(prohibido), "el recibo no debe llevar «" + prohibido + "»:\n" + texto);
		}
	}

	@Test
	void regenerar_daElMismoNumero() throws Exception {
		MvcResult primero = pedir(cargoCobrado, tokenPropietario);
		MvcResult segundo = pedir(cargoCobrado, tokenGestor);
		assertEquals(200, primero.getResponse().getStatus());
		assertEquals(200, segundo.getResponse().getStatus());
		assertEquals(primero.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION),
				segundo.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION));
		assertEquals(texto(primero.getResponse().getContentAsByteArray()),
				texto(segundo.getResponse().getContentAsByteArray()));
	}

	@Test
	void cargoSinCobrar_409() throws Exception {
		MvcResult r = pedir(cargoSinCobrar, tokenGestor);
		assertEquals(409, r.getResponse().getStatus());
		assertEquals("Este cargo no tiene ningún cobro registrado: no se puede emitir el recibo.",
				json.readTree(r.getResponse().getContentAsString()).get("mensaje").asText());
	}

	@Test
	void cobroAnulado_409() throws Exception {
		MvcResult r = pedir(cargoAnulado, tokenGestor);
		assertEquals(409, r.getResponse().getStatus());
		assertEquals("El cobro de este cargo está anulado: no se puede emitir el recibo.",
				json.readTree(r.getResponse().getContentAsString()).get("mensaje").asText());
	}

	@Test
	void cobroDevuelto_409() throws Exception {
		MvcResult r = pedir(cargoDevuelto, tokenGestor);
		assertEquals(409, r.getResponse().getStatus());
		assertEquals("El cobro de este cargo está devuelto: no se puede emitir el recibo.",
				json.readTree(r.getResponse().getContentAsString()).get("mensaje").asText());
		assertEquals(200, pedir(cargoDevolucionAnulada, tokenGestor).getResponse().getStatus(),
				"una devolución anulada no cuenta: el cobro sigue vigente");
	}

	@Test
	void cargoDeOtraEscuela_404() throws Exception {
		assertEquals(404, pedir(cargoOtra, tokenGestor).getResponse().getStatus(), "un cargo de otra escuela");
		assertEquals(404, pedir(cargoCobrado, tokenOtraGestor).getResponse().getStatus(), "y al revés");
		assertEquals(200, pedir(cargoOtra, tokenOtraGestor).getResponse().getStatus(), "en su escuela, sí");
		assertEquals(404, pedir(Long.MAX_VALUE, tokenGestor).getResponse().getStatus(), "un cargo que no existe");
	}

	@Test
	void gestionYResponsableDeLaSede() throws Exception {
		assertEquals(403, pedir(cargoCobrado, tokenProfesor).getResponse().getStatus(), "sesión solo-profesor");
		assertEquals(403, pedir(cargoCobrado, tokenFamilia).getResponse().getStatus(), "familia");
		assertEquals(200, pedir(cargoCobrado, tokenResponsable).getResponse().getStatus(), "responsable de su sede");
		assertEquals(403, pedir(cargoOtra, tokenResponsable).getResponse().getStatus(), "cargo de otra escuela");
		assertEquals(401, mvc.perform(get(ruta(cargoCobrado))).andReturn().getResponse().getStatus(), "sin sesión");
	}

	// ------------------------------------------------------------------ cargos de legado (sin ninguna fila de cobro)

	@Test
	void legado_reciboDelCargo() throws Exception {
		MvcResult r = pedir(legado, tokenGestor);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertEquals("application/pdf", r.getResponse().getContentType());
		String numero = String.format("R-2025-C%06d", legado);
		assertEquals("attachment; filename=\"recibo-" + numero + ".pdf\"",
				r.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION));

		String texto = texto(r.getResponse().getContentAsByteArray());
		for (String esperado : new String[] { "Recibo", numero, "10/03/2025", nombreAlumno, "CAMISETA LEGADO IT",
				"29,00 €", nombreSede, "IT E6 A " + e.marca, "Club Deportivo Recibo IT", "G11111111",
				"Este documento justifica el pago del concepto indicado. No es una factura.",
				"Emitido el 15/10/2026" }) {
			assertTrue(texto.contains(esperado), "falta «" + esperado + "» en:\n" + texto);
		}
		for (String prohibido : new String[] { DNI_ALUMNO, EMAIL_ALUMNO, TELEFONO_ALUMNO, "Calle 1",
				"it-e6-gestor-" + e.marca, "Usuario", "Forma de pago", "Sin indicar", "14,50 €" }) {
			assertFalse(texto.contains(prohibido), "el recibo no debe llevar «" + prohibido + "»:\n" + texto);
		}
		assertEquals(texto, texto(pedir(legado, tokenPropietario).getResponse().getContentAsByteArray()),
				"regenerarlo da el mismo recibo");
	}

	@Test
	void legado_cantidadNulaCuentaComoUna_ySinSedeNoHayFilaSede() throws Exception {
		MvcResult r = pedir(legadoCantidadNula, tokenGestor);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		String texto = texto(r.getResponse().getContentAsByteArray());
		assertTrue(texto.contains("28,00 €"), texto);
		assertTrue(texto.contains("05/03/2025"), texto);

		MvcResult sinSede = pedir(legadoSinSede, tokenGestor);
		assertEquals(200, sinSede.getResponse().getStatus(), sinSede.getResponse().getContentAsString());
		String textoSinSede = texto(sinSede.getResponse().getContentAsByteArray());
		assertTrue(textoSinSede.contains(String.format("R-2025-C%06d", legadoSinSede)), textoSinSede);
		assertFalse(textoSinSede.contains("Sede"), "sin sede: sin fila «Sede»:\n" + textoSinSede);
	}

	@Test
	void legadoDevuelto_409() throws Exception {
		MvcResult r = pedir(legadoDevuelto, tokenGestor);
		assertEquals(409, r.getResponse().getStatus());
		assertEquals("Este cargo está devuelto: no tiene recibo.",
				json.readTree(r.getResponse().getContentAsString()).get("mensaje").asText());
		assertEquals(200, pedir(legadoDevolucionAnulada, tokenGestor).getResponse().getStatus(),
				"una devolución anulada no cuenta");
	}

	@Test
	void legadoSinFechaDePago_409() throws Exception {
		MvcResult r = pedir(legadoSinFecha, tokenGestor);
		assertEquals(409, r.getResponse().getStatus());
		assertEquals("Este cargo no tiene fecha de pago: no se puede emitir el recibo.",
				json.readTree(r.getResponse().getContentAsString()).get("mensaje").asText());
	}

	@Test
	void legadoCancelado_409() throws Exception {
		MvcResult r = pedir(legadoCancelado, tokenGestor);
		assertEquals(409, r.getResponse().getStatus());
		assertEquals("Este cargo está cancelado: no tiene recibo.",
				json.readTree(r.getResponse().getContentAsString()).get("mensaje").asText());
	}

	@Test
	void pagadoConSoloCobrosAnulados_noEsLegado_409() throws Exception {
		MvcResult r = pedir(legadoAnuladoPagado, tokenGestor);
		assertEquals(409, r.getResponse().getStatus());
		assertEquals("El cobro de este cargo está anulado: no se puede emitir el recibo.",
				json.readTree(r.getResponse().getContentAsString()).get("mensaje").asText());
	}

	@Test
	void legadoDeOtraEscuela_404() throws Exception {
		assertEquals(404, pedir(legadoOtra, tokenGestor).getResponse().getStatus(), "un cargo de otra escuela");
		assertEquals(404, pedir(legado, tokenOtraGestor).getResponse().getStatus(), "y al revés");
		assertEquals(200, pedir(legadoOtra, tokenOtraGestor).getResponse().getStatus(), "en su escuela, sí");
	}

	@Test
	void legado_gestionYResponsableDeLaSede() throws Exception {
		assertEquals(403, pedir(legado, tokenProfesor).getResponse().getStatus(), "sesión solo-profesor");
		assertEquals(403, pedir(legado, tokenFamilia).getResponse().getStatus(), "familia");
		assertEquals(200, pedir(legado, tokenResponsable).getResponse().getStatus(), "responsable de su sede");
		assertEquals(403, pedir(legadoSinSede, tokenResponsable).getResponse().getStatus(), "cargo sin sede: solo gestión");
		assertEquals(401, mvc.perform(get(ruta(legado))).andReturn().getResponse().getStatus(), "sin sesión");
	}

	// ------------------------------------------------------------------ ayudantes

	private static String ruta(long cargo) {
		return "/api/productos-alumno/" + cargo + "/recibo";
	}

	private MvcResult pedir(long cargo, String token) throws Exception {
		return mvc.perform(get(ruta(cargo)).cookie(new Cookie("jwt", token))).andReturn();
	}

	/** Texto del PDF con los espacios normalizados (también los duros), una línea por renglón. */
	private static String texto(byte[] pdf) throws Exception {
		return String.join("\n", ContenidoPdf.lineas(pdf));
	}

	private long producto(EscuelaConSedesDePrueba escuela) {
		jdbc.update("INSERT INTO producto (concepto, precio, codigo, tipo, organizacion_id) "
				+ "VALUES ('CUOTA MENSUAL IT', 28.0, 'MENSUALIDAD_IT', 'MENSUALIDAD', ?)", escuela.org);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = ?", Long.class, escuela.org);
	}

	private long cargo(EscuelaConSedesDePrueba escuela, long producto, String concepto, boolean pagado) {
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, producto_id, concepto, precio, cantidad, pagado, fecha_pago,
				  fecha_asignacion, organizacion_id, sede_id)
				VALUES (?, ?, ?, 28.00, 1, ?, ?, '2026-09-01 12:00:00', ?, ?)""", escuela.aA, producto, concepto,
				pagado, pagado ? "2026-09-02 12:00:00" : null, escuela.org, escuela.sA);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE organizacion_id = ?", Long.class,
				escuela.org);
	}

	/** Cargo pagado de legado (como los anteriores a V31_5): sin ninguna fila de cobro. */
	private long legado(EscuelaConSedesDePrueba escuela, long producto, String concepto, double precio,
			Integer cantidad, String fechaPago, Long sede) {
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, producto_id, concepto, precio, cantidad, pagado, fecha_pago,
				  fecha_asignacion, organizacion_id, sede_id)
				VALUES (?, ?, ?, ?, ?, TRUE, ?, '2024-09-01 12:00:00', ?, ?)""", escuela.aA, producto, concepto, precio,
				cantidad, fechaPago, escuela.org, sede);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE organizacion_id = ?", Long.class,
				escuela.org);
	}

	/** DEVOLUCION en la caja enlazada al cargo; {@code cobro} nulo en un cargo de legado. */
	private void devolucion(EscuelaConSedesDePrueba escuela, long cargo, Long cobro, String estado) {
		jdbc.update("""
				INSERT INTO movimiento_caja (organizacion_id, sede_id, tipo, importe, alumno_id, nota, fecha, estado,
				  cobro_id, producto_alumno_id, metodo)
				VALUES (?, ?, 'DEVOLUCION', -28.00, ?, 'Devolución IT', '2026-09-03 10:00:00', ?, ?, ?, 'EFECTIVO')""",
				escuela.org, escuela.sA, escuela.aA, estado, cobro, cargo);
	}

	private long cobro(EscuelaConSedesDePrueba escuela, long cargo, String concepto, String metodo, String estado) {
		jdbc.update("""
				INSERT INTO cobro (organizacion_id, producto_alumno_id, sede_id, alumno_id, concepto, importe, metodo,
				  fecha_cobro, cobrado_por_usuario_id, origen, estado)
				VALUES (?, ?, ?, ?, ?, ?, ?, '2026-09-02 12:00:00', ?, 'TESORERIA', ?)""", escuela.org, cargo,
				escuela.sA, escuela.aA, concepto, new BigDecimal("28.00"), metodo, escuela.gestor, estado);
		return jdbc.queryForObject("SELECT MAX(id) FROM cobro WHERE organizacion_id = ?", Long.class, escuela.org);
	}
}
