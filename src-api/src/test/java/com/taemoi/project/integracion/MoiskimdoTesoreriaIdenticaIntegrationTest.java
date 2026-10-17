package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.acceso.FiltroSedesTesoreria;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Entrega 6-3 (entrega6-plan.md §4.3 y §6): para Moiskimdo (organización 1, una sede) el propietario y el gestor
 * obtienen EXACTAMENTE lo mismo en la tesorería.
 * <ul>
 * <li>Las consultas de la gestión ({@code todasLasSedes = true}) devuelven lo mismo que las de antes de la 6-3, copiadas
 * literalmente en {@link ProductoAlumnoConsultasAntesDeE6}: mismos ids en el mismo orden, mismos recuentos, sumas y
 * páginas, con una batería de filtros sobre todos los cargos de la organización 1.</li>
 * <li>Por HTTP: el resumen, los movimientos, el CSV y las deudas son iguales con ROLE_ADMIN y ROLE_MANAGER, cuadran con
 * los cargos sembrados (año 2039, que no usa ninguna otra clase), el filtro por su única sede no cambia nada y
 * {@code porSede} tiene una sola fila con el total. JSON solo con campos nuevos ({@code porSede}, {@code sedeId}).</li>
 * <li>Un cargo nuevo de Moiskimdo nace en «Umbrete».</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MoiskimdoTesoreriaIdenticaIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final int ANO = 2039;

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired ProductoAlumnoRepository repositorio;
	@Autowired ProductoAlumnoConsultasAntesDeE6 antes;

	@MockitoBean JavaMailSender mailSender;

	final String marca = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
	final List<Long> cuentas = new ArrayList<>();
	final List<Long> fichas = new ArrayList<>();
	final List<Long> cargos = new ArrayList<>();
	long umbrete;
	long tkd;
	long producto;
	String tokenPropietario;
	String tokenGestor;

	@BeforeAll
	void preparar() {
		umbrete = jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = 1 ORDER BY id LIMIT 1", Long.class);
		tkd = jdbc.queryForObject("SELECT id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'TAEKWONDO'",
				Long.class);
		jdbc.update("INSERT INTO producto (concepto, precio, organizacion_id) VALUES (?, 12.5, 1)", "E6TES " + marca);
		producto = jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = 1", Long.class);

		long a1 = alumno("E6TES UNO " + marca, true);
		long a2 = alumno("E6TES DOS " + marca, true);
		long a3 = alumno("E6TES TRES " + marca, false);
		long ad1 = inscripcion(a1);
		long ad2 = inscripcion(a2);
		// Mensualidades, tarifas, reservas, matrículas y otros; pagados, pendientes y NULL; con y sin fecha (el período
		// sale del concepto); por alumno_id, solo por la inscripción o por las dos.
		cargo(a1, ad1, "MENSUALIDAD MARZO 2039 - TAEKWONDO", 30.0, true, "2039-03-02", "2039-03-05");
		cargo(a1, ad1, "TARIFA COMPETIDOR TAEKWONDO MARZO 2039", 20.0, false, "2039-03-02", null);
		cargo(a1, null, "RESERVA DE PLAZA 2039/2040", 25.0, null, "2039-06-10", null);
		cargo(null, ad2, "MENSUALIDAD ABRIL 2039 - TAEKWONDO", 30.0, false, null, null);
		cargo(a2, ad2, "MATRÍCULA - TAEKWONDO", 15.5, true, "2039-04-01", "2039-04-01");
		cargo(a2, null, "E6TES " + marca, 12.5, false, "2039-04-20", null);
		cargo(a3, null, "MENSUALIDAD MAYO 2039", 30.0, false, "2039-05-01", null);
		tokenPropietario = token("propietario", "PROPIETARIO");
		tokenGestor = token("gestor", "ADMINISTRADOR");
	}

	/** Se borran los cargos sembrados (sin dependientes); fichas y cuentas se retiran como en las demás clases. */
	@AfterAll
	void retirar() {
		for (Long cargo : cargos) {
			jdbc.update("DELETE FROM producto_alumno WHERE id = ?", cargo);
		}
		jdbc.update("DELETE FROM producto_alumno WHERE producto_id = ?", producto);
		jdbc.update("DELETE FROM producto WHERE id = ?", producto);
		for (Long cuenta : cuentas) {
			jdbc.update("UPDATE membresia SET activa = FALSE WHERE usuario_id = ?", cuenta);
		}
		for (Long ficha : fichas) {
			jdbc.update("UPDATE alumno SET activo = FALSE WHERE id = ?", ficha);
			jdbc.update("UPDATE alumno_deporte SET activo = FALSE WHERE alumno_id = ?", ficha);
		}
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ consultas: iguales que antes de la 6-3

	@Test
	void consultasDeGestion_igualesQueLasDeAntes() {
		SedesGestion sinSedes = new SedesGestion(FiltroSedesTesoreria.TODAS);
		int combinaciones = 0;
		for (Integer[] periodo : new Integer[][] { { null, null }, { null, ANO }, { 3, ANO }, { 4, ANO } }) {
			Integer mes = periodo[0];
			Integer ano = periodo[1];
			String anoTexto = ano == null ? null : String.valueOf(ano);
			String mesNombre = mes == null ? null : (mes == 3 ? "MARZO" : "ABRIL");
			for (String deporte : Arrays.asList(null, "TAEKWONDO")) {
				for (Boolean pagado : Arrays.asList(null, true, false)) {
					for (String texto : Arrays.asList(null, "%E6TES%")) {
						for (boolean soloActivos : List.of(true, false)) {
							combinaciones++;
							String caso = mes + "/" + ano + "/" + deporte + "/" + pagado + "/" + texto + "/" + soloActivos;
							assertEquals(
									ids(enOrg1(() -> antes.findMovimientosTesoreriaFiltrados(deporte, deporte, pagado,
											texto, soloActivos, ano, anoTexto, mes, mesNombre, true))),
									ids(enOrg1(() -> repositorio.findMovimientosTesoreriaFiltrados(deporte, deporte,
											pagado, texto, soloActivos, ano, anoTexto, mes, mesNombre, true,
											sinSedes.todas, sinSedes.sedes))),
									"Filtrados " + caso);
							assertEquals(
									enOrg1(() -> antes.findMovimientosTesoreriaPeriodoBase(deporte, deporte, pagado,
											texto, soloActivos, ano, anoTexto, mes, mesNombre, true)).stream()
											.map(p -> p.getId() + ":" + p.getAlumnoId() + ":" + p.getPrecio()).toList(),
									enOrg1(() -> repositorio.findMovimientosTesoreriaPeriodoBase(deporte, deporte,
											pagado, texto, soloActivos, ano, anoTexto, mes, mesNombre, true,
											sinSedes.todas, sinSedes.sedes)).stream()
											.map(p -> p.getId() + ":" + p.getAlumnoId() + ":" + p.getPrecio()).toList(),
									"PeriodoBase " + caso);
							for (int pagina = 0; pagina < 2; pagina++) {
								PageRequest pageable = PageRequest.of(pagina, 3);
								Page<ProductoAlumno> deAntes = enOrg1(() -> antes.findMovimientosTesoreriaPaginados(
										deporte, deporte, pagado, texto, soloActivos, ano, anoTexto, mes, mesNombre,
										pageable, true));
								Page<ProductoAlumno> ahora = enOrg1(() -> repositorio.findMovimientosTesoreriaPaginados(
										deporte, deporte, pagado, texto, soloActivos, ano, anoTexto, mes, mesNombre,
										pageable, true, sinSedes.todas, sinSedes.sedes));
								assertEquals(deAntes.getTotalElements(), ahora.getTotalElements(), "total " + caso);
								assertEquals(ids(deAntes.getContent()), ids(ahora.getContent()), "página " + caso);
							}
							assertEquals(
									enOrg1(() -> antes.contarMovimientosTesoreria(deporte, deporte, pagado, soloActivos,
											ano, anoTexto, mes, mesNombre, true)),
									enOrg1(() -> repositorio.contarMovimientosTesoreria(deporte, deporte, pagado,
											soloActivos, ano, anoTexto, mes, mesNombre, true, sinSedes.todas,
											sinSedes.sedes)),
									"contar " + caso);
							assertEquals(
									enOrg1(() -> antes.sumarImporteMovimientosTesoreria(deporte, deporte, pagado,
											soloActivos, ano, anoTexto, mes, mesNombre, true)),
									enOrg1(() -> repositorio.sumarImporteMovimientosTesoreria(deporte, deporte, pagado,
											soloActivos, ano, anoTexto, mes, mesNombre, true, sinSedes.todas,
											sinSedes.sedes)),
									"sumar " + caso);
							assertEquals(
									enOrg1(() -> antes.contarAlumnosConPendientesTesoreria(deporte, deporte, soloActivos,
											ano, anoTexto, mes, mesNombre, true)),
									enOrg1(() -> repositorio.contarAlumnosConPendientesTesoreria(deporte, deporte,
											soloActivos, ano, anoTexto, mes, mesNombre, true, sinSedes.todas,
											sinSedes.sedes)),
									"alumnos " + caso);
						}
					}
				}
			}
		}
		assertEquals(4 * 2 * 3 * 2 * 2, combinaciones);
		// Las deudas del informe (sin cambios en el repositorio) y la batería no es vacía.
		assertEquals(ids(enOrg1(() -> antes.findAllUnpaidWithAlumno())),
				ids(enOrg1(() -> repositorio.findAllUnpaidWithAlumno())));
		assertTrue(enOrg1(() -> repositorio.contarMovimientosTesoreria(null, null, null, false, ANO,
				String.valueOf(ANO), null, null, true, true, sinSedes.sedes)) >= 7);
	}

	// ------------------------------------------------------------------ HTTP: propietario y gestor

	@Test
	void resumen_igualParaPropietarioYGestor_yCuadraConLosCargos() throws Exception {
		for (String filtro : List.of("", "?ano=" + ANO, "?ano=" + ANO + "&mes=3", "?soloActivos=false",
				"?ano=" + ANO + "&soloActivos=false&deporte=TAEKWONDO")) {
			JsonNode propietario = leer("/api/tesoreria/resumen" + filtro, tokenPropietario);
			assertEquals(propietario, leer("/api/tesoreria/resumen" + filtro, tokenGestor), filtro);
			assertPorSedeSumaElTotal(propietario, filtro);
		}
		JsonNode ano = leer("/api/tesoreria/resumen?ano=" + ANO + "&soloActivos=false", tokenGestor);
		assertEquals(7, ano.get("totalMovimientos").asLong());
		assertEquals(2, ano.get("totalPagados").asLong());
		assertEquals(163.0, ano.get("importeTotal").asDouble(), 0.0001);
		assertEquals(45.5, ano.get("importePagado").asDouble(), 0.0001);
		assertEquals(117.5, ano.get("importePendiente").asDouble(), 0.0001);
		assertEquals(3, ano.get("alumnosConPendientes").asLong());
		assertEquals(1, ano.get("porSede").size(), "una sede: una fila");
		assertEquals(umbrete, ano.get("porSede").get(0).get("sedeId").asLong());
		assertEquals("Umbrete", ano.get("porSede").get(0).get("nombre").asText());
		// Campos de antes intactos (JSON solo aditivo).
		for (String campo : List.of("mes", "ano", "deporte", "totalMovimientos", "totalPagados", "totalPendientes",
				"importeTotal", "importePagado", "importePendiente", "alumnosConPendientes")) {
			assertTrue(ano.has(campo), campo);
		}
		// Filtrar por su única sede no cambia nada.
		JsonNode conSede = leer("/api/tesoreria/resumen?ano=" + ANO + "&soloActivos=false&sedeId=" + umbrete,
				tokenGestor);
		assertEquals(ano, conSede);
		long sinSede = jdbc.queryForObject(
				"SELECT COUNT(*) FROM producto_alumno WHERE organizacion_id = 1 AND sede_id IS NULL", Long.class);
		if (sinSede == 0) {
			assertEquals(leer("/api/tesoreria/resumen", tokenGestor),
					leer("/api/tesoreria/resumen?sedeId=" + umbrete, tokenGestor));
		}
	}

	@Test
	void movimientosYCsv_igualesParaPropietarioYGestor() throws Exception {
		for (String filtro : List.of("?size=200", "?ano=" + ANO + "&soloActivos=false&size=200",
				"?ano=" + ANO + "&mes=4&pagado=false&soloActivos=false", "?texto=e6tes&soloActivos=false&size=200",
				"?page=2&size=2&ano=" + ANO + "&soloActivos=false")) {
			assertEquals(leer("/api/tesoreria/movimientos" + filtro, tokenPropietario),
					leer("/api/tesoreria/movimientos" + filtro, tokenGestor), filtro);
		}
		JsonNode delAno = leer("/api/tesoreria/movimientos?ano=" + ANO + "&soloActivos=false&size=200", tokenGestor);
		assertEquals(7, delAno.get("totalElements").asLong());
		delAno.get("content").forEach(m -> assertEquals(umbrete, m.get("sedeId").asLong()));
		byte[] csvPropietario = bytes("/api/tesoreria/export/csv?ano=" + ANO + "&soloActivos=false", tokenPropietario);
		assertArrayEquals(csvPropietario, bytes("/api/tesoreria/export/csv?ano=" + ANO + "&soloActivos=false",
				tokenGestor));
		String csv = new String(csvPropietario, java.nio.charset.StandardCharsets.UTF_8);
		assertTrue(csv.startsWith("Alumno,Deporte,Concepto,Categoria,Fecha cargo,Estado,Fecha pago,Importe,Notas\n"),
				"misma cabecera: sin columna de sede");
		assertEquals(8, csv.strip().split("\n").length);
	}

	@Test
	void informesDeDeudasYListado_igualesParaPropietarioYGestor() throws Exception {
		byte[] deudas = bytes("/api/informes/deudas/csv?soloActivos=false", tokenPropietario);
		assertArrayEquals(deudas, bytes("/api/informes/deudas/csv?soloActivos=false", tokenGestor));
		String texto = new String(deudas, java.nio.charset.StandardCharsets.UTF_8);
		assertTrue(texto.contains("E6TES UNO " + marca));
		assertTrue(texto.contains("E6TES TRES " + marca), "inactivo con soloActivos=false");
		assertEquals(200, mvc.perform(get("/api/informes/deudas").cookie(new Cookie("jwt", tokenGestor))).andReturn()
				.getResponse().getStatus());
		assertEquals(200, mvc.perform(get("/api/informes/listado-mensualidad-mensual?mesAno=2039-03")
				.cookie(new Cookie("jwt", tokenPropietario))).andReturn().getResponse().getStatus());
	}

	@Test
	void cargoNuevo_naceEnUmbrete() throws Exception {
		long ficha = fichas.get(0);
		MvcResult r = mvc.perform(post("/api/productos-alumno/alumno/" + ficha + "/producto/" + producto)
				.contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":1,\"pagado\":false}")
				.cookie(new Cookie("jwt", tokenGestor))).andReturn();
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		long id = json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
		cargos.add(id);
		assertEquals(umbrete, jdbc.queryForObject("SELECT sede_id FROM producto_alumno WHERE id = ?", Long.class, id));
		assertEquals(umbrete, json.readTree(r.getResponse().getContentAsString()).get("sedeId").asLong());

		// Revisión 6-3 (alternativa A): sin consultas en el alta del cargo. Un alumno sin sede principal (en una escuela
		// de una sede no debería existir: alta 6-1, finalización 6-2 y V31_2) deja el cargo SIN sede, y el postcheck de
		// V31_3 lo detecta.
		long sinSede = alumno("E6TES SIN SEDE " + marca, true);
		jdbc.update("UPDATE alumno SET sede_principal_id = NULL WHERE id = ?", sinSede);
		MvcResult otro = mvc.perform(post("/api/productos-alumno/alumno/" + sinSede + "/producto/" + producto)
				.contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":1,\"pagado\":false}")
				.cookie(new Cookie("jwt", tokenGestor))).andReturn();
		assertEquals(200, otro.getResponse().getStatus(), otro.getResponse().getContentAsString());
		long idOtro = json.readTree(otro.getResponse().getContentAsString()).get("id").asLong();
		cargos.add(idOtro);
		assertNull(jdbc.queryForObject("SELECT sede_id FROM producto_alumno WHERE id = ?", Long.class, idOtro));
		java.util.Map<String, Object> postcheck = SedesVinculosMigracionIntegrationTest
				.ejecutarScriptDocs(jdbc.getDataSource(), "postcheck-v31_3.sql").get(0).get(0);
		assertTrue(((Number) postcheck.get("cargos_sin_sede_escuelas_una_sede")).longValue() >= 1,
				"el postcheck de V31_3 detecta el cargo sin sede");
		jdbc.update("DELETE FROM producto_alumno WHERE id = ?", idOtro);
		cargos.remove(Long.valueOf(idOtro));
	}

	// ------------------------------------------------------------------ utilidades

	/** Parámetros de sede de la gestión, como los pasa el servicio. */
	private record SedesGestion(boolean todas, java.util.Collection<Long> sedes) {
		SedesGestion(FiltroSedesTesoreria filtro) {
			this(filtro.todas(), filtro.parametro());
		}
	}

	private void assertPorSedeSumaElTotal(JsonNode resumen, String caso) {
		long movimientos = 0;
		double importe = 0;
		for (JsonNode fila : resumen.get("porSede")) {
			movimientos += fila.get("totalMovimientos").asLong();
			importe += fila.get("importeTotal").asDouble();
		}
		assertEquals(resumen.get("totalMovimientos").asLong(), movimientos, caso);
		assertEquals(resumen.get("importeTotal").asDouble(), importe, 0.0001, caso);
	}

	private long alumno(String nombre, boolean activo) {
		long expediente = jdbc.queryForObject("SELECT COALESCE(MAX(numero_expediente), 60000) + 1 FROM alumno",
				Long.class);
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id, sede_principal_id)
				VALUES (?, 'PRUEBA', 1, 'Calle 1', '2010-01-01', ?, ?, '600000000', 0, 1, ?)""",
				activo, nombre, expediente, umbrete);
		long id = jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = 1",
				Long.class, expediente);
		fichas.add(id);
		return id;
	}

	private long inscripcion(long alumno) {
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
				  organizacion_id, fecha_alta, fecha_alta_inicial, disciplina_id)
				VALUES (TRUE, 0, ?, 0, 1, 0, 1, '2023-09-01', '2023-09-01', ?)""", alumno, tkd);
		return jdbc.queryForObject("SELECT MAX(id) FROM alumno_deporte WHERE alumno_id = ?", Long.class, alumno);
	}

	/** Como tras V31_3 en Moiskimdo: el cargo en su única sede. */
	private void cargo(Long alumno, Long inscripcion, String concepto, double precio, Boolean pagado, String fecha,
			String fechaPago) {
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, alumno_deporte_id, concepto, precio, cantidad, pagado,
				  fecha_asignacion, fecha_pago, organizacion_id, sede_id)
				VALUES (?, ?, ?, ?, 1, ?, ?, ?, 1, ?)""", alumno, inscripcion, concepto, precio, pagado, fecha,
				fechaPago, umbrete);
		cargos.add(jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE organizacion_id = 1", Long.class));
	}

	private String token(String nombre, String rol) {
		String email = "it-e6-3-moiskimdo-" + nombre + "-" + marca.toLowerCase() + "@example.test";
		Usuario usuario = enOrg1(() -> {
			Usuario u = new Usuario();
			u.setNombre("Usuario");
			u.setApellidos("Prueba");
			u.setEmail(email);
			u.setContrasena("fake-Password-123");
			u.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(u);
		});
		cuentas.add(usuario.getId());
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, 1, ?)", usuario.getId(), rol);
		return jwtService.generateToken(usuario, 600_000, ORG1);
	}

	private static List<Long> ids(List<ProductoAlumno> cargos) {
		return cargos.stream().map(ProductoAlumno::getId).toList();
	}

	private static <T> T enOrg1(Supplier<T> accion) {
		return TenantContext.calcularCon(ORG1, accion::get);
	}

	private JsonNode leer(String ruta, String token) throws Exception {
		MvcResult r = mvc.perform(get(ruta).cookie(new Cookie("jwt", token))).andReturn();
		assertEquals(200, r.getResponse().getStatus(), ruta + ": " + r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString());
	}

	private byte[] bytes(String ruta, String token) throws Exception {
		MvcResult r = mvc.perform(get(ruta).cookie(new Cookie("jwt", token))).andReturn();
		assertEquals(200, r.getResponse().getStatus(), ruta);
		return r.getResponse().getContentAsByteArray();
	}
}
