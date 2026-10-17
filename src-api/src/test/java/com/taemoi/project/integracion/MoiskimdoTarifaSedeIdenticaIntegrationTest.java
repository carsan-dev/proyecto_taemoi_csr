package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.utils.MensualidadUtils;

/**
 * Entrega 6-4 (restricción dura): para Moiskimdo (organización 1, sin tarifas por sede) las cinco generaciones de
 * mensualidades y el alta cobran EXACTAMENTE la cuantía de la inscripción (también una que no es de 2 decimales), con la
 * misma idempotencia y el MISMO JSON de respuesta: ningún campo nuevo. Meses de 2044, que no usa ninguna otra clase;
 * se borran al terminar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MoiskimdoTarifaSedeIdenticaIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;

	@MockitoBean JavaMailSender mailSender;

	final String marca = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
	final List<Long> fichas = new ArrayList<>();
	final List<Long> cuentas = new ArrayList<>();
	/** alumno → cuantía de su inscripción (la que debe cobrarse siempre). */
	final Map<Long, Double> cuantias = new LinkedHashMap<>();
	long umbrete, tkd, adulto, padresHijos;
	double importeAdulto;
	String tokenGestor;
	String conceptoAltaSinSufijo;

	@BeforeAll
	void preparar() {
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM tarifa_sede WHERE organizacion_id = 1", Long.class),
				"Moiskimdo no tiene tarifas por sede");
		umbrete = jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = 1 ORDER BY id LIMIT 1", Long.class);
		tkd = jdbc.queryForObject("SELECT id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'TAEKWONDO'",
				Long.class);
		adulto = jdbc.queryForObject("SELECT id FROM tarifa WHERE organizacion_id = 1 AND codigo = 'ADULTO'", Long.class);
		importeAdulto = jdbc.queryForObject("SELECT importe FROM tarifa WHERE id = ?", Double.class, adulto);
		padresHijos = jdbc.queryForObject("SELECT id FROM tarifa WHERE organizacion_id = 1 AND codigo = 'PADRES_HIJOS'",
				Long.class);
		inscrito("BASE", adulto, importeAdulto, null);
		inscrito("DESCUENTO", adulto, 25.0, null);
		inscrito("RARA", adulto, 34.999999, null);
		inscrito("PADRE", padresHijos, 28.0, "PADRE");
		inscrito("SINTARIFA", null, 33.3, null);
		tokenGestor = token("gestor", "ADMINISTRADOR");
	}

	@AfterAll
	void retirar() {
		jdbc.update("DELETE FROM producto_alumno WHERE organizacion_id = 1 AND concepto LIKE '%2044%'");
		if (conceptoAltaSinSufijo != null) {
			jdbc.update("DELETE FROM producto_alumno WHERE organizacion_id = 1 AND concepto = ?", conceptoAltaSinSufijo);
		}
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

	@Test
	void masivas_mismaCuantia_mismaIdempotencia_mismoJson() throws Exception {
		JsonNode general = generar(post("/api/productos-alumno/mensualidades/general"), "2044-01");
		assertRespuestaMasiva(general, "Mensualidades creadas correctamente.");
		assertCuantias("2044-01");
		long antes = cargosDelMes("2044-01");
		assertRespuestaMasiva(generar(post("/api/productos-alumno/mensualidades/general"), "2044-01"),
				"Mensualidades creadas correctamente.");
		assertEquals(antes, cargosDelMes("2044-01"), "regenerar no duplica");
		assertCuantias("2044-01");

		assertRespuestaMasiva(generar(post("/api/productos-alumno/mensualidades/deporte?deporte=TAEKWONDO"), "2044-02"),
				"Mensualidades creadas correctamente para alumnos de TAEKWONDO.");
		assertCuantias("2044-02");
		assertRespuestaMasiva(generar(post("/api/productos-alumno/mensualidades/multi-deporte"), "2044-03"),
				"Mensualidades multi-deporte creadas correctamente para todos los alumnos.");
		assertCuantias("2044-03");
	}

	@Test
	void individuales_mismaCuantia_mismoJson() throws Exception {
		for (long alumno : cuantias.keySet()) {
			JsonNode individual = generar(post("/api/productos-alumno/mensualidades/individual?alumnoId=" + alumno),
					"2044-04");
			assertEquals(Map.of("mensaje", "Mensualidad individual creada correctamente."), comoMapa(individual));
			JsonNode porDeporte = generar(post("/api/productos-alumno/mensualidades/individual-deporte?deporte=TAEKWONDO"
					+ "&alumnoId=" + alumno), "2044-05");
			assertEquals(Map.of("mensaje", "Mensualidad individual creada correctamente para TAEKWONDO."),
					comoMapa(porDeporte));
		}
		assertCuantias("2044-04");
		assertCuantias("2044-05");
	}

	@Test
	void alta_mismaCuantia() throws Exception {
		LocalDate hoy = LocalDate.now();
		conceptoAltaSinSufijo = MensualidadUtils
				.formatearNombreMensualidad(hoy.getYear() + "-" + "%02d".formatted(hoy.getMonthValue()));
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, concepto, precio, cantidad, pagado, fecha_asignacion,
				  organizacion_id, sede_id)
				VALUES (?, ?, 30.0, 1, 0, NOW(), 1, ?)""", fichas.get(0), conceptoAltaSinSufijo, umbrete);
		long base = alta("UNO", importeAdulto);
		long descuento = alta("DOS", 21.5);
		assertEquals(List.of(importeAdulto), precios(base, conceptoAltaSinSufijo));
		assertEquals(List.of(21.5), precios(descuento, conceptoAltaSinSufijo));
		assertEquals(umbrete, jdbc.queryForObject(
				"SELECT sede_id FROM producto_alumno WHERE alumno_id = ? AND concepto = ?", Long.class, base,
				conceptoAltaSinSufijo));
	}

	@Test
	void apiDeTarifasSede_vaciaParaMoiskimdo() throws Exception {
		MvcResult r = mvc.perform(get("/api/tarifas-sede").cookie(new jakarta.servlet.http.Cookie("jwt", tokenGestor)))
				.andReturn();
		assertEquals(200, r.getResponse().getStatus());
		assertEquals("[]", r.getResponse().getContentAsString());
	}

	// ------------------------------------------------------------------ utilidades

	private void assertRespuestaMasiva(JsonNode respuesta, String mensaje) {
		Map<String, Object> esperado = new LinkedHashMap<>();
		esperado.put("mensaje", mensaje);
		esperado.put("alumnosOmitidosSedeInactiva", 0);
		assertEquals(esperado, comoMapa(respuesta), "el JSON de siempre, sin campos nuevos");
		assertEquals(List.of("mensaje", "alumnosOmitidosSedeInactiva"), List.copyOf(campos(respuesta)),
				"mismo orden");
	}

	private void assertCuantias(String mesAno) {
		String concepto = MensualidadUtils.formatearNombreMensualidad(mesAno) + " - TAEKWONDO";
		for (Map.Entry<Long, Double> esperado : cuantias.entrySet()) {
			assertEquals(List.of(esperado.getValue()), precios(esperado.getKey(), concepto),
					"alumno " + esperado.getKey() + " en " + mesAno);
			assertEquals(umbrete, jdbc.queryForObject(
					"SELECT sede_id FROM producto_alumno WHERE alumno_id = ? AND concepto = ?", Long.class,
					esperado.getKey(), concepto));
		}
	}

	private List<Double> precios(long alumno, String concepto) {
		return jdbc.queryForList("SELECT precio FROM producto_alumno WHERE alumno_id = ? AND concepto = ?",
				Double.class, alumno, concepto);
	}

	private long cargosDelMes(String mesAno) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM producto_alumno WHERE organizacion_id = 1 AND concepto LIKE ?",
				Long.class, "%" + MensualidadUtils.formatearMesAno(mesAno) + "%");
	}

	private Map<String, Object> comoMapa(JsonNode nodo) {
		return json.convertValue(nodo, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
		});
	}

	private static Set<String> campos(JsonNode nodo) {
		Set<String> campos = new LinkedHashSet<>();
		for (Iterator<String> it = nodo.fieldNames(); it.hasNext();) {
			campos.add(it.next());
		}
		return campos;
	}

	private void inscrito(String nombre, Long tarifa, double cuantia, String rol) {
		long expediente = jdbc.queryForObject("SELECT COALESCE(MAX(numero_expediente), 60000) + 1 FROM alumno",
				Long.class);
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id, sede_principal_id)
				VALUES (1, 'PRUEBA', 1, 'Calle 1', '1990-01-01', ?, ?, '600000000', 0, 1, ?)""",
				"E64 " + nombre + " " + marca, expediente, umbrete);
		long alumno = jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = 1",
				Long.class, expediente);
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
				  organizacion_id, fecha_alta, fecha_alta_inicial, disciplina_id, tarifa_id, cuantia_tarifa, rol_familiar)
				VALUES (TRUE, 0, ?, 0, 1, 0, 1, '2023-09-01', '2023-09-01', ?, ?, ?, ?)""", alumno, tkd, tarifa, cuantia,
				rol);
		fichas.add(alumno);
		cuantias.put(alumno, cuantia);
	}

	private long alta(String nombre, double cuantia) throws Exception {
		String cuerpo = """
				{"nombre":"E64 ALTA %s","apellidos":"MOISKIMDO %s","fechaNacimiento":"1990-03-04","direccion":"Calle 1",
				 "telefono":600000009,"autorizacionWeb":true,
				 "deportesInicial":[{"deporte":"TAEKWONDO","grado":"BLANCO","fechaAlta":"2024-09-01","tipoTarifa":"ADULTO",
				   "cuantiaTarifa":%s,"competidor":false,"tieneLicencia":false}]}""".formatted(nombre, marca, cuantia);
		MvcResult r = mvc.perform(multipart("/api/alumnos/crear").param("nuevo", cuerpo)
				.cookie(new jakarta.servlet.http.Cookie("jwt", tokenGestor))).andReturn();
		assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		long id = json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
		fichas.add(id);
		return id;
	}

	private JsonNode generar(MockHttpServletRequestBuilder peticion, String mesAno) throws Exception {
		MvcResult r = mvc.perform(peticion.contentType(MediaType.TEXT_PLAIN).content(mesAno)
				.cookie(new jakarta.servlet.http.Cookie("jwt", tokenGestor))).andReturn();
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString());
	}

	private String token(String nombre, String rol) {
		String email = "it-e6-4-moiskimdo-" + nombre + "-" + marca.toLowerCase() + "@example.test";
		Usuario usuario = TenantContext.calcularCon(ORG1, () -> {
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
}
