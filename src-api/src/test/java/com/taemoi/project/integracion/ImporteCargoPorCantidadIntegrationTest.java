package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

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

import jakarta.servlet.http.Cookie;

/**
 * Decisión 297 contra MySQL: el importe de un cargo es precio × cantidad (cantidad nula o menor que 1 cuenta como 1) en
 * el cobro, la caja, la tesorería (totales, por sede, con y sin período, CSV), lo vencido, el resumen de cargos de la
 * ficha, «Por cobrar» de Mi caja, las solicitudes y las devoluciones. Escuela sintética propia ({@link
 * EscuelaConSedesDePrueba}); un CAMISETA de 12,50 € con cantidad 3 vale 37,50 €. Datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImporteCargoPorCantidadIntegrationTest extends MySqlIntegrationTest {

	private static final BigDecimal TRES_CAMISETAS = new BigDecimal("37.50");

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	long propietario, profesor;
	String tGestor, tPropietario, tProfesor;
	long camiseta, mensualidad;
	String concepto;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-297-cantidad");
		propietario = e.cuenta(usuarios, "propietario");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROPIETARIO')",
				propietario, e.org);
		profesor = e.cuenta(usuarios, "profesor");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROFESOR')", profesor,
				e.org);
		jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) "
				+ "VALUES (?, ?, ?, 'TITULAR')", e.org, profesor, e.gA);
		tGestor = e.token(jwtService, usuarios, e.gestor);
		tPropietario = e.token(jwtService, usuarios, propietario);
		tProfesor = e.token(jwtService, usuarios, profesor);
		concepto = "CAMISETA 297 " + e.marca;
		jdbc.update("INSERT INTO producto (concepto, precio, codigo, organizacion_id, vendible_profesor, tipo) "
				+ "VALUES (?, 12.5, NULL, ?, FALSE, 'OTRO')", concepto, e.org);
		camiseta = jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = ?", Long.class, e.org);
		jdbc.update("INSERT INTO producto (concepto, precio, codigo, tipo, organizacion_id) "
				+ "VALUES ('CUOTA MENSUAL 297', 1.0, 'MENSUALIDAD_297', 'MENSUALIDAD', ?)", e.org);
		mensualidad = jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = ?", Long.class, e.org);
	}

	@AfterEach
	void sinFugas() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ cobro y caja

	@Test
	void cobrarEnTesoreria_registraPrecioPorCantidad_enElCobroYEnLaCaja() throws Exception {
		long cargo = cargo(e.aA, 3);
		long sede = sedeDelCargo(cargo);
		BigDecimal saldoAntes = saldo(sede);

		assertEquals(200, estado(pago(cargo, "{\"pagado\":true,\"metodoPago\":\"EFECTIVO\"}"), tGestor));

		assertImporte(TRES_CAMISETAS, importeDelCobro(cargo));
		assertImporte(saldoAntes.add(TRES_CAMISETAS), saldo(sede));
	}

	@Test
	void cantidadUno_igualQueAntes() throws Exception {
		long cargo = cargo(e.aA, 1);
		assertEquals(200, estado(pago(cargo, "{\"pagado\":true}"), tGestor));
		assertImporte(new BigDecimal("12.50"), importeDelCobro(cargo));
	}

	@Test
	void cantidadNulaOCero_cuentaComoUna() throws Exception {
		long nula = cargo(e.aA, 1);
		long cero = cargo(e.aA, 1);
		jdbc.update("UPDATE producto_alumno SET cantidad = NULL WHERE id = ?", nula);
		jdbc.update("UPDATE producto_alumno SET cantidad = 0 WHERE id = ?", cero);
		long sede = sedeDelCargo(nula);
		assertImporte(new BigDecimal("12.50"), filaTesoreria(nula, sede).get("precio").decimalValue());
		assertImporte(new BigDecimal("12.50"), filaTesoreria(cero, sede).get("precio").decimalValue());
		assertEquals(200, estado(pago(nula, "{\"pagado\":true}"), tGestor));
		assertEquals(200, estado(pago(cero, "{\"pagado\":true}"), tGestor));
		assertImporte(new BigDecimal("12.50"), importeDelCobro(nula));
		assertImporte(new BigDecimal("12.50"), importeDelCobro(cero));
	}

	@Test
	void miCajaDelProfesor_porCobrarYCobrar_precioPorCantidad() throws Exception {
		long cargo = cargo(e.aGrupoA, 3);
		JsonNode pendiente = null;
		for (JsonNode c : leer(get("/api/caja/profesor/cargos-pendientes"), tProfesor, 200)) {
			if (c.get("cargoId").asLong() == cargo) {
				pendiente = c;
			}
		}
		assertTrue(pendiente != null, "el cargo sale en «Por cobrar»");
		assertImporte(TRES_CAMISETAS, pendiente.get("importe").decimalValue());

		JsonNode cobro = leer(post("/api/caja/profesor/cargos/" + cargo + "/cobrar"), tProfesor, 201);
		assertImporte(TRES_CAMISETAS, cobro.get("importe").decimalValue());
		assertImporte(TRES_CAMISETAS, importeDelCobro(cargo));
	}

	// ------------------------------------------------------------------ devolución y revertir

	@Test
	void devolucionEnlazada_esPorElImporteEnteroDelCobro() throws Exception {
		long cargo = cargo(e.aA, 3);
		long sede = sedeDelCargo(cargo);
		assertEquals(200, estado(pago(cargo, "{\"pagado\":true,\"metodoPago\":\"EFECTIVO\"}"), tGestor));
		long cobro = cobroActivo(cargo);
		BigDecimal saldoAntes = saldo(sede);
		double devueltoAntes = resumen(sede, null).get("importeDevuelto").asDouble();

		assertEquals(400, estado(devolucion(sede, cobro, "-12.50"), tGestor), "solo el precio: parcial");
		JsonNode dto = leer(devolucion(sede, cobro, "-37.50"), tGestor, 201);
		assertImporte(TRES_CAMISETAS.negate(), dto.get("importe").decimalValue());
		assertImporte(saldoAntes.subtract(TRES_CAMISETAS), saldo(sede));
		assertEquals(devueltoAntes + 37.5, resumen(sede, null).get("importeDevuelto").asDouble(), 0.001);
	}

	@Test
	void solicitudDeRevertir_llevaElImporteDelCargo() throws Exception {
		// Con cobro: el del cobro.
		long conCobro = cargo(e.aA, 3);
		assertEquals(200, estado(pago(conCobro, "{\"pagado\":true}"), tGestor));
		assertImporte(TRES_CAMISETAS, revertir(Map.of("cobroId", cobroActivo(conCobro))).get("importe").decimalValue());

		// Pagado sin fila de cobro (anterior a V31_5): el del cargo.
		long sinCobro = cargo(e.aA, 3);
		jdbc.update("UPDATE producto_alumno SET pagado = 1, fecha_pago = NOW(6) WHERE id = ?", sinCobro);
		assertImporte(TRES_CAMISETAS, revertir(Map.of("productoAlumnoId", sinCobro)).get("importe").decimalValue());
	}

	/**
	 * La reversión de verdad: el gestor pide revertir el cobro de un cargo de 12,50 × 3, el propietario la acepta
	 * ({@code POST /api/solicitudes/{id}/aceptar}, el camino de la app) y se vuelve a cobrar. El cobro anulado y el nuevo
	 * son de 37,50; la caja baja y sube 37,50, y en la tesorería el cargo pasa de pagado a pendiente y a pagado.
	 */
	@Test
	void revertirAceptadoYVolverACobrar_precioPorCantidad() throws Exception {
		long alumno = e.alumno("E297REV" + e.marca, e.sB, true);
		JsonNode resumenAntes = resumen(e.sB, null);
		BigDecimal saldoAntes = saldo(e.sB);
		long cargo = cargo(alumno, 3);
		assertEquals(e.sB, sedeDelCargo(cargo));
		assertTrue(!filaTesoreria(cargo, e.sB).get("pagado").asBoolean(), "nace pendiente");
		assertDelta(resumenAntes, resumen(e.sB, null), 37.5, 0.0, 37.5);
		assertImporte(saldoAntes, saldo(e.sB));

		assertEquals(200, estado(pago(cargo, "{\"pagado\":true,\"metodoPago\":\"EFECTIVO\"}"), tGestor));
		long primerCobro = cobroActivo(cargo);
		assertImporte(TRES_CAMISETAS, importeDelCobro(cargo));
		assertImporte(saldoAntes.add(TRES_CAMISETAS), saldo(e.sB));
		assertTrue(filaTesoreria(cargo, e.sB).get("pagado").asBoolean(), "pagado");
		assertDelta(resumenAntes, resumen(e.sB, null), 37.5, 37.5, 0.0);

		JsonNode solicitud = revertir(Map.of("cobroId", primerCobro));
		assertImporte(TRES_CAMISETAS, solicitud.get("importe").decimalValue());
		JsonNode aceptada = leer(post("/api/solicitudes/" + solicitud.get("id").asLong() + "/aceptar"), tPropietario,
				200);
		assertEquals("ACEPTADA", aceptada.get("estado").asText());

		Map<String, Object> anulado = jdbc.queryForMap("SELECT estado, importe FROM cobro WHERE id = ?", primerCobro);
		assertEquals("ANULADO", anulado.get("estado"));
		assertImporte(TRES_CAMISETAS, (BigDecimal) anulado.get("importe"));
		assertEquals(0L, jdbc.queryForObject(
				"SELECT COUNT(*) FROM cobro WHERE producto_alumno_id = ? AND estado = 'ACTIVO'", Long.class, cargo));
		assertImporte(saldoAntes, saldo(e.sB));
		assertTrue(!filaTesoreria(cargo, e.sB).get("pagado").asBoolean(), "vuelve a pendiente");
		assertDelta(resumenAntes, resumen(e.sB, null), 37.5, 0.0, 37.5);

		assertEquals(200, estado(pago(cargo, "{\"pagado\":true,\"metodoPago\":\"EFECTIVO\"}"), tGestor));
		long segundoCobro = cobroActivo(cargo);
		assertTrue(segundoCobro != primerCobro, "un cobro nuevo");
		assertImporte(TRES_CAMISETAS, importeDelCobro(cargo));
		assertEquals("ANULADO", jdbc.queryForObject("SELECT estado FROM cobro WHERE id = ?", String.class, primerCobro));
		assertImporte(saldoAntes.add(TRES_CAMISETAS), saldo(e.sB));
		assertTrue(filaTesoreria(cargo, e.sB).get("pagado").asBoolean(), "pagado otra vez");
		assertDelta(resumenAntes, resumen(e.sB, null), 37.5, 37.5, 0.0);
	}

	// ------------------------------------------------------------------ precio con dos decimales como mucho

	@Test
	void precioDelCargo_masDeDosDecimalesONegativo_400_yNoSeGuarda() throws Exception {
		long cargo = cargo(e.aA, 3);
		for (String precio : new String[] { "3.335", "12.501", "0.001", "-1", "-0.5" }) {
			JsonNode r = leer(pago(cargo, "{\"precio\":" + precio + "}"), tGestor, 400);
			assertTrue(r.get("mensaje").asText().startsWith("El precio"), precio + ": " + r);
			assertEquals(12.5, precioDelCargo(cargo), 0.0, precio);
		}
		assertTrue(leer(pago(cargo, "{\"precio\":3.335}"), tGestor, 400).get("mensaje").asText().contains("2 decimales"));
		assertTrue(leer(pago(cargo, "{\"precio\":-1}"), tGestor, 400).get("mensaje").asText().contains("negativo"));
		// Con dos decimales o menos, también los que en binario no son exactos (0,07; 1,1; 0,3).
		for (String precio : new String[] { "3.30", "0.07", "1.1", "0.3", "0", "1234.56" }) {
			assertEquals(200, estado(pago(cargo, "{\"precio\":" + precio + "}"), tGestor), precio);
			assertEquals(Double.parseDouble(precio), precioDelCargo(cargo), 0.0, precio);
		}
	}

	@Test
	void precioDelCargoAntiguoConTresDecimales_sinCambiarlo_seEditaLoDemas() throws Exception {
		long cargo = cargo(e.aA, 1);
		jdbc.update("UPDATE producto_alumno SET precio = 3.335 WHERE id = ?", cargo);
		assertEquals(200, estado(pago(cargo, "{\"precio\":3.335,\"notas\":\"297 antiguo\"}"), tGestor));
		assertEquals(200, estado(pago(cargo, "{\"notas\":\"297 antiguo 2\"}"), tGestor));
		assertEquals(400, estado(pago(cargo, "{\"precio\":3.336}"), tGestor));
		assertEquals(3.335, precioDelCargo(cargo), 0.0);
	}

	@Test
	void precioDelCatalogo_masDeDosDecimalesONegativo_400() throws Exception {
		String nombre = "GORRA 297 " + e.marca;
		for (String precio : new String[] { "3.335", "-2" }) {
			JsonNode r = leer(post("/api/productos").contentType(MediaType.APPLICATION_JSON)
					.content("{\"concepto\":\"" + nombre + "\",\"precio\":" + precio + "}"), tGestor, 400);
			assertTrue(r.get("mensaje").asText().startsWith("El precio"), precio + ": " + r);
		}
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM producto WHERE concepto = ?", Long.class, nombre));
		long producto = leer(post("/api/productos").contentType(MediaType.APPLICATION_JSON)
				.content("{\"concepto\":\"" + nombre + "\",\"precio\":3.35}"), tGestor, 200).get("id").asLong();

		for (String precio : new String[] { "3.335", "-2" }) {
			JsonNode r = leer(put("/api/productos/" + producto).contentType(MediaType.APPLICATION_JSON)
					.content("{\"concepto\":\"" + nombre + "\",\"precio\":" + precio + "}"), tGestor, 400);
			assertTrue(r.get("mensaje").asText().startsWith("El precio"), precio + ": " + r);
			// POST con el id de uno existente (merge): la misma regla.
			assertEquals(400, estado(post("/api/productos").contentType(MediaType.APPLICATION_JSON)
					.content("{\"id\":" + producto + ",\"concepto\":\"" + nombre + "\",\"precio\":" + precio + "}"),
					tGestor));
		}
		assertEquals(3.35, precioDelProducto(producto), 0.0);
		assertEquals(200, estado(put("/api/productos/" + producto).contentType(MediaType.APPLICATION_JSON)
				.content("{\"concepto\":\"" + nombre + "\",\"precio\":4.1}"), tGestor));
		assertEquals(4.1, precioDelProducto(producto), 0.0);

		// Precio antiguo con tres decimales: sin cambiarlo se edita lo demás.
		jdbc.update("UPDATE producto SET precio = 3.335 WHERE id = ?", producto);
		assertEquals(200, estado(put("/api/productos/" + producto).contentType(MediaType.APPLICATION_JSON)
				.content("{\"concepto\":\"" + nombre + "\",\"precio\":3.335,\"vendibleProfesor\":false}"), tGestor));
	}

	@Test
	void copiaDesdeElCatalogo_conPrecioDeTresDecimales_400_ySinCargo() throws Exception {
		String nombre = "PARCHE 297 " + e.marca;
		jdbc.update("INSERT INTO producto (concepto, precio, codigo, organizacion_id, vendible_profesor, tipo) "
				+ "VALUES (?, 3.335, NULL, ?, TRUE, 'OTRO')", nombre, e.org);
		long producto = jdbc.queryForObject("SELECT id FROM producto WHERE concepto = ?", Long.class, nombre);
		long antes = cargosDelProducto(producto);

		JsonNode r = leer(post("/api/productos-alumno/alumno/" + e.aA + "/producto/" + producto)
				.contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":3,\"pagado\":true}"), tGestor, 400);
		assertTrue(r.get("mensaje").asText().contains("2 decimales"), r.toString());
		assertEquals(400, estado(post("/api/caja/profesor/alumnos/" + e.aGrupoA + "/productos/" + producto
				+ "/cobrar"), tProfesor));
		assertEquals(antes, cargosDelProducto(producto));
	}

	@Test
	void cuantiaDelExamen_masDeDosDecimales_400_yNoCambiaElCargo() throws Exception {
		// Alumno sin inscripción en deportes: el PUT no toca la fecha de grado (la disciplina de prueba no tiene grados).
		long alumno = e.alumno("E297EXAMEN" + e.marca, e.sA, true);
		jdbc.update("INSERT INTO convocatoria (fecha_convocatoria, organizacion_id, disciplina_id) VALUES "
				+ "('2026-06-01', ?, ?)", e.org, e.disciplina);
		long convocatoria = jdbc.queryForObject("SELECT MAX(id) FROM convocatoria WHERE organizacion_id = ?",
				Long.class, e.org);
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, concepto, precio, cantidad, pagado, fecha_asignacion,
				  organizacion_id, sede_id)
				VALUES (?, 'DERECHO A EXAMEN 297', 20.0, 1, 0, NOW(), ?,
				  (SELECT sede_principal_id FROM alumno WHERE id = ?))""", alumno, e.org, alumno);
		long cargo = jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE organizacion_id = ?", Long.class,
				e.org);
		jdbc.update("""
				INSERT INTO alumno_convocatoria (cuantia_examen, pagado, alumno_id, convocatoria_id, grado_actual,
				  grado_siguiente, organizacion_id, producto_alumno_id) VALUES (20, 0, ?, ?, NULL, NULL, ?, ?)""",
				alumno, convocatoria, e.org, cargo);
		long inscripcion = jdbc.queryForObject(
				"SELECT id FROM alumno_convocatoria WHERE alumno_id = ? AND convocatoria_id = ?", Long.class, alumno,
				convocatoria);

		try {
			JsonNode r = leer(put("/api/convocatorias/alumno/" + inscripcion).contentType(MediaType.APPLICATION_JSON)
					.content("{\"cuantiaExamen\":20.005,\"pagado\":false}"), tGestor, 400);
			assertTrue(r.get("mensaje").asText().contains("2 decimales"), r.toString());
			assertEquals(20.0, precioDelCargo(cargo), 0.0);
			assertEquals(20.0, jdbc.queryForObject("SELECT cuantia_examen FROM alumno_convocatoria WHERE id = ?",
					Double.class, inscripcion), 0.0);
			assertEquals(200, estado(put("/api/convocatorias/alumno/" + inscripcion)
					.contentType(MediaType.APPLICATION_JSON).content("{\"cuantiaExamen\":22.5,\"pagado\":false}"), tGestor));
			assertEquals(22.5, precioDelCargo(cargo), 0.0);
		} finally {
			// Fuera del total de lo vencido de la escuela de prueba (lo comprueba pendienteVencido_precioPorCantidad).
			jdbc.update("DELETE FROM alumno_convocatoria WHERE id = ?", inscripcion);
			jdbc.update("DELETE FROM producto_alumno WHERE id = ?", cargo);
		}
	}

	/**
	 * Ronda 3: la cuantía personalizada de la inscripción (alumno_deporte.cuantia_tarifa) llega tal cual al precio de las
	 * mensualidades, así que tiene la misma regla que el precio. 3,335 → 400 con mensaje y no se guarda.
	 */
	@Test
	void cuantiaDeLaInscripcion_masDeDosDecimales_400_yNoSeGuarda() throws Exception {
		long alumno = e.alumno("E297CUANTIA" + e.marca, e.sA, true);
		e.taekwondo(alumno, false);
		for (String cuantia : new String[] { "3.335", "-1" }) {
			JsonNode r = leer(put("/api/alumnos/" + alumno + "/deportes/TAEKWONDO/cuantia-tarifa")
					.contentType(MediaType.APPLICATION_JSON).content("{\"cuantiaTarifa\":" + cuantia + "}"), tGestor,
					400);
			assertTrue(r.get("mensaje").asText().startsWith("El precio"), cuantia + ": " + r);
			assertNull(cuantiaDe(alumno), cuantia);
		}
		assertEquals(200, estado(put("/api/alumnos/" + alumno + "/deportes/TAEKWONDO/cuantia-tarifa")
				.contentType(MediaType.APPLICATION_JSON).content("{\"cuantiaTarifa\":33.35}"), tGestor));
		assertEquals(33.35, cuantiaDe(alumno), 0.0);
		// Dato antiguo con tres decimales: reenviarlo sin cambiarlo no falla.
		jdbc.update("UPDATE alumno_deporte SET cuantia_tarifa = 3.335 WHERE alumno_id = ?", alumno);
		assertEquals(200, estado(put("/api/alumnos/" + alumno + "/deportes/TAEKWONDO/cuantia-tarifa")
				.contentType(MediaType.APPLICATION_JSON).content("{\"cuantiaTarifa\":3.335}"), tGestor));
	}

	@Test
	void altaConCuantiaDeTresDecimales_400_yNoSeCreaElAlumno() throws Exception {
		String apellidos = "CUANTIA 297 " + e.marca;
		MvcResult r = alta(apellidos, "3.335");
		assertEquals(400, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertTrue(json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("mensaje").asText()
				.contains("2 decimales"));
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM alumno WHERE apellidos = ?", Long.class, apellidos));
		// Control: la misma alta con 2 decimales se crea, con esa cuantía.
		MvcResult bien = alta(apellidos + " BIEN", "33.35");
		assertEquals(201, bien.getResponse().getStatus(), bien.getResponse().getContentAsString());
		assertEquals(33.35, jdbc.queryForObject("""
				SELECT ad.cuantia_tarifa FROM alumno_deporte ad JOIN alumno a ON a.id = ad.alumno_id
				WHERE a.apellidos = ?""", Double.class, apellidos + " BIEN"), 0.0);
		// Fuera de los totales de la escuela de prueba (lo vencido solo cuenta alumnos activos y cargos sin cancelar).
		jdbc.update("UPDATE producto_alumno SET cancelado_en = NOW(6) WHERE alumno_id IN "
				+ "(SELECT id FROM alumno WHERE apellidos = ?)", apellidos + " BIEN");
		jdbc.update("UPDATE alumno SET activo = 0 WHERE apellidos = ?", apellidos + " BIEN");
	}

	private MvcResult alta(String apellidos, String cuantia) throws Exception {
		String cuerpo = """
				{"nombre":"ALTA","apellidos":"%s","fechaNacimiento":"1990-03-04","direccion":"Calle 1",
				 "telefono":600000009,"autorizacionWeb":true,"sedePrincipalId":%d,
				 "deportesInicial":[{"deporte":"TAEKWONDO","fechaAlta":"2024-09-01","cuantiaTarifa":%s,
				   "competidor":false,"tieneLicencia":false}]}""".formatted(apellidos, e.sA, cuantia);
		return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.multipart("/api/alumnos/crear").param("nuevo", cuerpo).cookie(new Cookie("jwt", tGestor))).andReturn();
	}

	/** Ronda 3 (P2): la asignación por deporte (también la matrícula) devuelve el 400 con su mensaje, no vacío. */
	@Test
	void asignarPorDeporte_conPrecioDeTresDecimales_400ConMensaje() throws Exception {
		long alumno = e.alumno("E297DEPORTE" + e.marca, e.sA, true);
		e.taekwondo(alumno, false);
		String nombre = "DOBOK 297 " + e.marca;
		jdbc.update("INSERT INTO producto (concepto, precio, codigo, organizacion_id, vendible_profesor, tipo) "
				+ "VALUES (?, 3.335, NULL, ?, FALSE, 'OTRO')", nombre, e.org);
		long producto = jdbc.queryForObject("SELECT id FROM producto WHERE concepto = ?", Long.class, nombre);
		JsonNode r = leer(post("/api/productos-alumno/alumno/" + alumno + "/producto/" + producto + "/deporte/TAEKWONDO")
				.contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":1,\"pagado\":false}"), tGestor, 400);
		assertTrue(r.get("mensaje").asText().contains("2 decimales"), r.toString());
		assertEquals(0L, cargosDelProducto(producto));
	}

	// ------------------------------------------------------------------ tesorería

	@Test
	void tesoreria_movimientosTotalesPorSedeYCsv_sumanPrecioPorCantidad() throws Exception {
		long alumno = e.alumno("E297TES" + e.marca, e.sB, true);
		int ano = java.time.LocalDate.now().getYear();
		JsonNode sinPeriodoAntes = resumen(e.sB, null);
		JsonNode conPeriodoAntes = resumen(e.sB, ano);

		long cargo = cargo(alumno, 3);
		assertEquals(e.sB, sedeDelCargo(cargo));
		assertImporte(TRES_CAMISETAS, filaTesoreria(cargo, e.sB).get("precio").decimalValue());
		assertDelta(sinPeriodoAntes, resumen(e.sB, null), 37.5, 0.0, 37.5);
		assertDelta(conPeriodoAntes, resumen(e.sB, ano), 37.5, 0.0, 37.5);

		assertEquals(200, estado(pago(cargo, "{\"pagado\":true}"), tGestor));
		assertDelta(sinPeriodoAntes, resumen(e.sB, null), 37.5, 37.5, 0.0);
		assertDelta(conPeriodoAntes, resumen(e.sB, ano), 37.5, 37.5, 0.0);

		MvcResult csv = mvc.perform(get("/api/tesoreria/export/csv").param("soloActivos", "false")
				.param("sedeId", String.valueOf(e.sB)).cookie(new Cookie("jwt", tPropietario))).andReturn();
		assertEquals(200, csv.getResponse().getStatus());
		String linea = csv.getResponse().getContentAsString(StandardCharsets.UTF_8).lines()
				.filter(l -> l.contains("E297TES")).findFirst().orElseThrow();
		assertTrue(linea.contains(",37.50,"), linea);
	}

	@Test
	void informeDeDeudasCsv_totalPrecioPorCantidad_yColumnaPrecioUnitaria() throws Exception {
		BigDecimal antes = totalDeudasCsv();
		long alumno = e.alumno("E297DEUDA" + e.marca, e.sA, true);
		cargo(alumno, 3);
		String linea = deudasCsv().lines().filter(l -> l.contains("E297DEUDA")).findFirst().orElseThrow();
		// El informe formatea con la configuración regional de la JVM: 12.50 o 12,50.
		assertTrue(linea.contains(",3,12.50,") || linea.contains(",3,12,50,"), "cantidad y precio unitario: " + linea);
		assertImporte(antes.add(TRES_CAMISETAS), totalDeudasCsv());
	}

	@Test
	void resumenDeCargosDeLaFicha_precioPorCantidad() throws Exception {
		long alumno = e.alumno("E297FICHA" + e.marca, e.sA, true);
		cargo(alumno, 3);
		long pagado = cargo(alumno, 2);
		assertEquals(200, estado(pago(pagado, "{\"pagado\":true}"), tGestor));
		JsonNode fila = leer(get("/api/alumnos/cargos-resumen").param("alumnoIds", String.valueOf(alumno)),
				tPropietario, 200).get(0);
		assertImporte(TRES_CAMISETAS, fila.get("importePendiente").decimalValue());
		assertImporte(new BigDecimal("25.00"), fila.get("importePagado").decimalValue());
	}

	@Test
	void pendienteVencido_precioPorCantidad() throws Exception {
		long alumno = e.alumno("E297VENC" + e.marca, e.sA, true);
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, producto_id, concepto, precio, cantidad, pagado, fecha_asignacion,
				  organizacion_id, sede_id)
				VALUES (?, ?, 'MENSUALIDAD ENERO 2026 - TAEKWONDO', 12.50, 3, 0, '2026-01-02 12:00:00', ?, ?)""",
				alumno, mensualidad, e.org, e.sA);
		long cargo = jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE organizacion_id = ?", Long.class,
				e.org);
		assertImporte(TRES_CAMISETAS, leer(get("/api/tesoreria/pendiente-vencido"), tGestor, 200).get("total")
				.decimalValue());
		JsonNode porAlumno = leer(get("/api/tesoreria/pendientes-por-alumno").param("alumnoIds",
				String.valueOf(alumno)), tGestor, 200).get(0);
		assertImporte(TRES_CAMISETAS, porAlumno.get("importe").decimalValue());
		assertEquals(cargo, porAlumno.get("detalle").get(0).get("productoAlumnoId").asLong());
		assertImporte(TRES_CAMISETAS, porAlumno.get("detalle").get(0).get("importe").decimalValue());
	}

	// ------------------------------------------------------------------ ayudantes

	/** Cargo del CAMISETA (12,50 €) con esa cantidad, pendiente, creado por el gestor como en la tesorería. */
	private long cargo(long alumno, int cantidad) throws Exception {
		return leer(post("/api/productos-alumno/alumno/" + alumno + "/producto/" + camiseta)
				.contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":" + cantidad + ",\"pagado\":false}"),
				tGestor, 200).get("id").asLong();
	}

	private Double cuantiaDe(long alumno) {
		return jdbc.queryForObject("SELECT cuantia_tarifa FROM alumno_deporte WHERE alumno_id = ?", Double.class, alumno);
	}

	private double precioDelCargo(long cargo) {
		return jdbc.queryForObject("SELECT precio FROM producto_alumno WHERE id = ?", Double.class, cargo);
	}

	private double precioDelProducto(long producto) {
		return jdbc.queryForObject("SELECT precio FROM producto WHERE id = ?", Double.class, producto);
	}

	private long cargosDelProducto(long producto) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM producto_alumno WHERE producto_id = ?", Long.class, producto);
	}

	private long sedeDelCargo(long cargo) {
		return jdbc.queryForObject("SELECT sede_id FROM producto_alumno WHERE id = ?", Long.class, cargo);
	}

	private BigDecimal importeDelCobro(long cargo) {
		return jdbc.queryForObject("SELECT importe FROM cobro WHERE producto_alumno_id = ? AND estado = 'ACTIVO'",
				BigDecimal.class, cargo);
	}

	private long cobroActivo(long cargo) {
		return jdbc.queryForObject("SELECT id FROM cobro WHERE producto_alumno_id = ? AND estado = 'ACTIVO'",
				Long.class, cargo);
	}

	private BigDecimal saldo(long sede) throws Exception {
		return leer(get("/api/caja/sedes/" + sede + "/resumen"), tPropietario, 200).get("saldoCaja").decimalValue();
	}

	private JsonNode resumen(long sede, Integer ano) throws Exception {
		MockHttpServletRequestBuilder peticion = get("/api/tesoreria/resumen").param("soloActivos", "false")
				.param("sedeId", String.valueOf(sede));
		if (ano != null) {
			peticion = peticion.param("ano", String.valueOf(ano));
		}
		return leer(peticion, tPropietario, 200);
	}

	/** Diferencias del resumen (total, pagado, pendiente) y de su fila por sede, que es la única. */
	private static void assertDelta(JsonNode antes, JsonNode despues, double total, double pagado, double pendiente) {
		for (String[] par : new String[][] { { "importeTotal", String.valueOf(total) },
				{ "importePagado", String.valueOf(pagado) }, { "importePendiente", String.valueOf(pendiente) } }) {
			double esperado = Double.parseDouble(par[1]);
			assertEquals(esperado, despues.get(par[0]).asDouble() - antes.get(par[0]).asDouble(), 0.001,
					"resumen " + par[0]);
			assertEquals(esperado, filaSede(despues).get(par[0]).asDouble() - filaSedeOVacia(antes, par[0]), 0.001,
					"por sede " + par[0]);
		}
	}

	private static JsonNode filaSede(JsonNode resumen) {
		assertEquals(1, resumen.get("porSede").size(), resumen.toString());
		return resumen.get("porSede").get(0);
	}

	private static double filaSedeOVacia(JsonNode resumen, String campo) {
		return resumen.get("porSede").size() == 0 ? 0.0 : filaSede(resumen).get(campo).asDouble();
	}

	private JsonNode filaTesoreria(long cargo, long sede) throws Exception {
		JsonNode pagina = leer(get("/api/tesoreria/movimientos").param("soloActivos", "false").param("size", "200")
				.param("sedeId", String.valueOf(sede)), tPropietario, 200);
		for (JsonNode fila : pagina.get("content")) {
			if (fila.get("productoAlumnoId").asLong() == cargo) {
				return fila;
			}
		}
		throw new AssertionError("El cargo " + cargo + " no sale en la tesorería");
	}

	private String deudasCsv() throws Exception {
		MvcResult r = mvc.perform(get("/api/informes/deudas/csv").param("soloActivos", "false")
				.cookie(new Cookie("jwt", tPropietario))).andReturn();
		assertEquals(200, r.getResponse().getStatus());
		return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	/** La fila «TOTAL GENERAL,,,,,<total>,» del informe de deudas en CSV (total con punto o coma decimal). */
	private BigDecimal totalDeudasCsv() throws Exception {
		String fila = deudasCsv().lines().filter(l -> l.startsWith("TOTAL GENERAL,,,,,")).findFirst().orElseThrow();
		String total = fila.substring("TOTAL GENERAL,,,,,".length(), fila.length() - 1);
		return new BigDecimal(total.replace(',', '.'));
	}

	private MockHttpServletRequestBuilder devolucion(long sede, long cobro, String importe) {
		return post("/api/caja/sedes/" + sede + "/movimientos").contentType(MediaType.APPLICATION_JSON)
				.content("{\"tipo\":\"DEVOLUCION\",\"importe\":" + importe + ",\"cobroId\":" + cobro
						+ ",\"nota\":\"Devolución\"}");
	}

	private JsonNode revertir(Map<String, Object> referencia) throws Exception {
		Map<String, Object> cuerpo = new java.util.HashMap<>(referencia);
		cuerpo.put("tipo", "REVERTIR_COBRO");
		cuerpo.put("motivo", "x");
		return leer(post("/api/solicitudes").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(cuerpo)), tGestor, 201);
	}

	private static MockHttpServletRequestBuilder pago(long cargo, String cuerpo) {
		return put("/api/productos-alumno/" + cargo).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private static void assertImporte(BigDecimal esperado, BigDecimal valor) {
		assertEquals(0, esperado.compareTo(valor), "importe " + valor + " frente a " + esperado);
	}

	private int estado(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn().getResponse().getStatus();
	}

	private JsonNode leer(MockHttpServletRequestBuilder peticion, String token, int esperado) throws Exception {
		MvcResult r = mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
		assertEquals(esperado, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
	}
}
