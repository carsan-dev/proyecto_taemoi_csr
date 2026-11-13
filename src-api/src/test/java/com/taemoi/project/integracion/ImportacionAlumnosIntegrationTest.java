package com.taemoi.project.integracion;

import static com.taemoi.project.plataforma.importacion.HojaDePrueba.alumno;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.taemoi.project.dtos.response.GrupoConAlumnosDTO;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Producto;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.organizacion.TransaccionEnEscuela;
import com.taemoi.project.plataforma.importacion.HojaDePrueba;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.CatalogoLegadoTaemoi;
import com.taemoi.project.services.EmailService;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.ProductosCatalogoService;
import com.taemoi.project.utils.MensualidadUtils;

import jakarta.servlet.http.Cookie;

/**
 * Fase 4, pieza I1: importador de alumnos por HTTP real (MockMvc con la cadena de filtros completa) contra MySQL. Cada
 * test crea su escuela con el alta de plataforma (catálogo de taekwondo copiado de la plantilla) y le añade sedes,
 * grupos con turnos y tarifas; las hojas se generan en memoria con POI. Datos inventados.
 *
 * <p>Propiedades del encargo (1-15), cada una con su test; la 10 y la 11 recorren los tres endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
class ImportacionAlumnosIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String CLAVE = "Clave-Segura-123";
	private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
	private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
	private static final LocalDate HOY = LocalDate.now(MADRID);
	private static final LocalDate MENOR = HOY.minusYears(10);
	private static final LocalDate ADULTO = HOY.minusYears(30);
	private static final List<String> OPERACIONES = List.of("ensayo", "carga", "hoja-con-errores");
	/** Tablas que el importador escribiría (o que no debe tocar), comparadas con CHECKSUM TABLE. */
	private static final List<String> TABLAS = List.of("alumno", "alumno_deporte", "tutor", "consentimiento",
			"alumno_grupo", "alumno_turno", "producto_alumno", "usuario", "usuario_alumno", "membresia", "grupo",
			"turno", "sede", "tarifa", "organizacion", "disciplina", "grado");

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired UsuarioRepository usuarios;
	@Autowired PasswordEncoder encoder;
	/** Espía: los tests de fallo a mitad de escritura hacen fallar el alta en un grupo de una fila. */
	@MockitoSpyBean GrupoService grupos;
	@Autowired JwtService jwt;
	@Autowired TransaccionEnEscuela enEscuela;
	@Autowired ProductosCatalogoService productosCatalogo;
	@Autowired AlumnoRepository alumnoRepository;
	@Autowired ProductoAlumnoRepository productosAlumno;

	@MockitoBean EmailService email;

	final String marca = UUID.randomUUID().toString().substring(0, 8);
	Cookie superadmin;
	int siguienteNumero = 100_000 + ThreadLocalRandom.current().nextInt(800_000);

	/** Una escuela de prueba lista para importar. */
	record Escuela(long org, long disciplina, long mollina, long estepa, long herrera, long grupoInfantil,
			long grupoAdultos, List<Long> turnosInfantil, long turnoAdultos, long tarifaMensual, long tarifaGeneral) {
	}

	@BeforeAll
	void preparar() throws Exception {
		Usuario cuenta = cuenta("it-i1-superadmin-" + marca + "@example.test");
		assertEquals(1, jdbc.update("UPDATE usuario SET es_admin_plataforma = TRUE WHERE id = ?", cuenta.getId()));
		superadmin = new Cookie("jwt", login(cuenta.getEmail()));
	}

	@BeforeEach
	void sinCorreos() {
		clearInvocations(email);
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ 1. el ensayo no cambia nada

	@Test
	void p01_ensayo_noCambiaNada_conHojaValidaYConErrores() throws Exception {
		Escuela e = escuela("p01");
		Long contadorAntes = contador(e.org());
		String antes = huella();
		long auditoriaAntes = maxAuditoria();

		HojaDePrueba valida = new HojaDePrueba()
				.fila(alumno(numero(), nombre(1), MENOR, "Mollina", "grupo", "Infantil L-X 17:00", "tarifa",
						"IT_MENSUAL", "tutor_nombre", "Tutora " + marca, "tutor_parentesco", "MADRE",
						"autorizacion_imagen", "SI"))
				.fila(alumno(numero(), nombre(2), ADULTO, "Estepa", "grupo", "Adultos", "dni_nie", dni()));
		JsonNode informe = enviar("ensayo", e.org(), valida.xlsx(), 200);
		assertEquals(2, informe.get("filas").asInt());
		assertEquals(2, informe.get("listas").asInt());
		assertEquals(0, informe.get("creados").asInt());
		assertEquals(0, informe.get("yaCargados").asInt());
		assertEquals(0, informe.get("errores").size(), informe.toString());

		HojaDePrueba conErrores = new HojaDePrueba()
				.fila(alumno(numero(), nombre(3), MENOR, "Mollina"))
				.fila(alumno(numero(), nombre(4), MENOR, "Mollina", "telefono", ""));
		JsonNode conError = enviar("ensayo", e.org(), conErrores.xlsx(), 200);
		assertEquals(1, conError.get("listas").asInt());
		assertEquals("Falta el teléfono.", conError.get("errores").get(0).get("motivo").asText());

		assertEquals(antes, huella(), "El ensayo dejó filas escritas o cambiadas");
		// El contador de expediente: o ya estaba, o queda creado a 0 (transacción propia del primer alta); nunca avanza.
		Long contadorDespues = contador(e.org());
		assertEquals(contadorAntes == null ? Long.valueOf(0) : contadorAntes, contadorDespues);
		// Solo dos apuntes de LECTURA en la escuela destino, con recuentos; ni CREATE ni la fila genérica del filtro.
		List<Map<String, Object>> apuntes = auditoriaDesde(auditoriaAntes);
		assertEquals(2, apuntes.size(), apuntes.toString());
		for (Map<String, Object> apunte : apuntes) {
			assertEquals("READ", apunte.get("accion"));
			assertEquals(e.org(), ((Number) apunte.get("organizacion_id")).longValue());
			assertEquals("plataforma", apunte.get("modulo"));
			assertTrue(((String) apunte.get("resumen")).startsWith("PLATAFORMA IMPORTACION_ALUMNOS_ENSAYO organizacion=#"
					+ e.org() + " filas=2"), apunte.toString());
		}
	}

	// ------------------------------------------------------------------ 2. un error: 422 y nada escrito

	@Test
	void p02_cargaConUnErrorEnUnaFila_422_nadaEscrito() throws Exception {
		Escuela e = escuela("p02");
		String antes = huella();
		long auditoriaAntes = maxAuditoria();
		int malo = numero();
		HojaDePrueba hoja = new HojaDePrueba()
				.fila(alumno(numero(), nombre(1), MENOR, "Mollina"))
				.fila(alumno(numero(), nombre(2), MENOR, "Estepa"))
				.fila(alumno(malo, nombre(3), MENOR, "Estepa centro"));
		JsonNode informe = enviar("carga", e.org(), hoja.xlsx(), 422);
		assertEquals(3, informe.get("filas").asInt());
		assertEquals(2, informe.get("listas").asInt());
		assertEquals(0, informe.get("creados").asInt());
		JsonNode error = informe.get("errores").get(0);
		assertEquals(1, informe.get("errores").size());
		assertEquals(4, error.get("fila").asInt());
		assertEquals(malo, error.get("numeroFederacion").asInt());
		assertEquals("sede", error.get("columna").asText());
		assertEquals("«Estepa centro» no es una sede de esta escuela.", error.get("motivo").asText());

		assertEquals(antes, huella());
		assertEquals(0, contar("SELECT COUNT(*) FROM alumno WHERE organizacion_id = ?", e.org()));
		// Sin apunte de creación en la escuela destino (el 422 lo apunta el filtro, como cualquier 4xx).
		assertTrue(auditoriaDesde(auditoriaAntes).stream().noneMatch(a -> e.org() == org(a)), "Apunte en la escuela");
	}

	// ------------------------------------------------------------------ 3. carga válida

	@Test
	void p03_cargaValida_alumnosDeportesGradosLicenciaTutoresGruposYTurnos() throws Exception {
		Escuela e = escuela("p03");
		long auditoriaAntes = maxAuditoria();
		int n1 = numero(), n2 = numero(), n3 = numero();
		String dni1 = dni(), dniTutor = dni(), dni2 = dni();
		HojaDePrueba hoja = new HojaDePrueba()
				.fila(alumno(n1, nombre(1), MENOR, " mollina ", "grupo", "infantil l-x 17:00", "tarifa", "it_mensual",
						"dni_nie", dni1, "fecha_licencia", "05/01/2026", "telefono2", "955000000", "localidad", "Mollina",
						"codigo_postal", "29532", "tutor_nombre", "Tutora " + marca, "tutor_parentesco", "MADRE",
						"tutor_dni", dniTutor, "observaciones", "Nota ficticia", "autorizacion_imagen", "SI"))
				.fila(alumno(n2, nombre(2), ADULTO, "Estepa", "grupo", "Adultos", "tarifa", "General IT", "dni_nie",
						dni2, "grado", "1 DAN", "fecha_licencia", LocalDate.of(2025, 9, 1), "autorizacion_imagen", "NO",
						"tutor_nombre", "Otro " + marca))
				.fila(alumno(n3, nombre(3), MENOR, "Mollina", "grado", "SIN GRADO", "direccion", ""));
		JsonNode informe = enviar("carga", e.org(), hoja.xlsx(), 200);
		assertEquals(3, informe.get("creados").asInt(), informe.toString());
		assertEquals(3, informe.get("listas").asInt());
		assertEquals(0, informe.get("yaCargados").asInt());

		Map<String, Object> a1 = alumnoPorLicencia(e.org(), n1);
		assertEquals(nombre(1), a1.get("nombre"));
		assertEquals("Prueba Ficticia", a1.get("apellidos"));
		assertEquals(java.sql.Date.valueOf(MENOR), a1.get("fecha_nacimiento"));
		assertEquals(dni1, a1.get("nif"));
		assertEquals("600000" + String.format("%03d", n1 % 1000), a1.get("telefono"));
		assertEquals("955000000", a1.get("telefono2"));
		assertEquals("familia-" + n1 + "@example.test", a1.get("email"));
		assertEquals("Calle Inventada " + n1, a1.get("direccion"));
		assertEquals("Mollina", a1.get("localidad"));
		assertEquals("29532", a1.get("codigo_postal"));
		assertEquals("Nota ficticia", a1.get("observaciones"));
		assertEquals(e.mollina(), ((Number) a1.get("sede_principal_id")).longValue());
		assertEquals(java.sql.Date.valueOf(HOY), a1.get("fecha_alta"));
		assertEquals(Boolean.TRUE, a1.get("activo"));
		assertNotNull(a1.get("numero_expediente"));

		long id1 = ((Number) a1.get("id")).longValue();
		Map<String, Object> d1 = deporte(id1);
		assertEquals(e.disciplina(), ((Number) d1.get("disciplina_id")).longValue());
		assertEquals("AMARILLO", d1.get("grado"));
		assertEquals(Boolean.TRUE, d1.get("tiene_licencia"));
		assertEquals(n1, ((Number) d1.get("numero_licencia")).intValue());
		assertEquals(java.sql.Date.valueOf(LocalDate.of(2026, 1, 5)), d1.get("fecha_licencia"));
		assertEquals(e.tarifaMensual(), ((Number) d1.get("tarifa_id")).longValue());
		assertEquals(31.5, ((Number) d1.get("cuantia_tarifa")).doubleValue());
		assertEquals(Boolean.TRUE, d1.get("activo"));
		assertEquals(List.of(Map.of("nombre", "Tutora " + marca, "nif", dniTutor, "parentesco", "MADRE",
				"responsable_legal", true)), tutores(id1));
		assertEquals(List.of(e.grupoInfantil()), gruposDe(id1));
		assertEquals(e.turnosInfantil(), turnosDe(id1));

		long id2 = ((Number) alumnoPorLicencia(e.org(), n2).get("id")).longValue();
		Map<String, Object> d2 = deporte(id2);
		assertEquals("NEGRO_1_DAN", d2.get("grado"));
		assertEquals(e.tarifaGeneral(), ((Number) d2.get("tarifa_id")).longValue());
		assertEquals(25.0, ((Number) d2.get("cuantia_tarifa")).doubleValue());
		assertEquals(java.sql.Date.valueOf(LocalDate.of(2025, 9, 1)), d2.get("fecha_licencia"));
		// Adulto: el tutor no es responsable legal; parentesco vacío = OTRO.
		assertEquals(List.of(Map.of("nombre", "Otro " + marca, "nif", "", "parentesco", "OTRO",
				"responsable_legal", false)), tutores(id2));
		assertEquals(List.of(e.grupoAdultos()), gruposDe(id2));
		assertEquals(List.of(e.turnoAdultos()), turnosDe(id2));

		long id3 = ((Number) alumnoPorLicencia(e.org(), n3).get("id")).longValue();
		Map<String, Object> d3 = deporte(id3);
		assertEquals("BLANCO", d3.get("grado"));
		assertNull(d3.get("tarifa_id"));
		assertNull(d3.get("cuantia_tarifa"));
		assertNull(d3.get("fecha_licencia"));
		assertEquals(List.of(), tutores(id3));
		assertEquals(List.of(), gruposDe(id3));
		assertEquals(List.of(), turnosDe(id3));

		// Un solo apunte, de creación, en la escuela destino; sin la fila genérica del filtro.
		List<Map<String, Object>> apuntes = auditoriaDesde(auditoriaAntes);
		assertEquals(1, apuntes.size(), apuntes.toString());
		Map<String, Object> apunte = apuntes.get(0);
		assertEquals("CREATE", apunte.get("accion"));
		assertEquals(e.org(), org(apunte));
		assertEquals("/api/plataforma/escuelas/" + e.org() + "/importacion/alumnos/carga", apunte.get("endpoint"));
		assertEquals("PLATAFORMA IMPORTACION_ALUMNOS organizacion=#" + e.org() + " disciplina=TAEKWONDO filas=3 "
				+ "creados=3 yaCargados=0 avisos=" + informe.get("avisos").size(), apunte.get("resumen"));
	}

	// ------------------------------------------------------------------ 4. repetir no duplica; concurrencia

	@Test
	void p04_repetirLaCarga_noDuplica_yUnaHojaConFilasNuevasAnadeSoloEsas() throws Exception {
		Escuela e = escuela("p04");
		Map<String, Object> f1 = alumno(numero(), nombre(1), MENOR, "Mollina", "dni_nie", dni());
		Map<String, Object> f2 = alumno(numero(), nombre(2), ADULTO, "Estepa", "dni_nie", dni());
		byte[] hoja = new HojaDePrueba().fila(f1).fila(f2).xlsx();
		assertEquals(2, enviar("carga", e.org(), hoja, 200).get("creados").asInt());

		JsonNode otraVez = enviar("carga", e.org(), hoja, 200);
		assertEquals(0, otraVez.get("creados").asInt());
		assertEquals(2, otraVez.get("yaCargados").asInt());
		assertEquals(0, otraVez.get("listas").asInt());
		assertEquals(0, otraVez.get("errores").size());
		assertEquals(0, otraVez.get("avisos").size(), "Las ya cargadas no llevan avisos");
		assertEquals(2, contar("SELECT COUNT(*) FROM alumno WHERE organizacion_id = ?", e.org()));

		Map<String, Object> f3 = alumno(numero(), nombre(3), MENOR, "Mollina");
		JsonNode ampliada = enviar("carga", e.org(), new HojaDePrueba().fila(f1).fila(f3).fila(f2).xlsx(), 200);
		assertEquals(1, ampliada.get("creados").asInt());
		assertEquals(2, ampliada.get("yaCargados").asInt());
		// Las ya cargadas, listadas solo con su fila y su número de federación.
		JsonNode yaCargadas = ampliada.get("yaCargadas");
		assertEquals(2, yaCargadas.size(), ampliada.toString());
		assertEquals(List.of(2, 4), List.of(yaCargadas.get(0).get("fila").asInt(), yaCargadas.get(1).get("fila").asInt()));
		assertEquals(List.of(f1.get("numero_federacion"), f2.get("numero_federacion")), List.of(
				yaCargadas.get(0).get("numeroFederacion").asInt(), yaCargadas.get(1).get("numeroFederacion").asInt()));
		for (JsonNode fila : yaCargadas) {
			assertEquals(List.of("fila", "numeroFederacion"), nombresDeCampos(fila));
		}
		assertEquals(3, contar("SELECT COUNT(*) FROM alumno WHERE organizacion_id = ?", e.org()));
		assertEquals(3, contar("""
				SELECT COUNT(DISTINCT ad.numero_licencia) FROM alumno_deporte ad WHERE ad.organizacion_id = ?""", e.org()));
	}

	@Test
	void p04b_dosCargasALaVez_noDuplican() throws Exception {
		Escuela e = escuela("p04b");
		HojaDePrueba hoja = new HojaDePrueba();
		for (int i = 0; i < 12; i++) {
			hoja.fila(alumno(numero(), nombre(i), MENOR, i % 2 == 0 ? "Mollina" : "Estepa", "grupo",
					i % 2 == 0 ? "Infantil L-X 17:00" : "Adultos"));
		}
		byte[] contenido = hoja.xlsx();
		ExecutorService hilos = Executors.newFixedThreadPool(2);
		try {
			List<Callable<JsonNode>> cargas = List.of(() -> enviar("carga", e.org(), contenido, 200),
					() -> enviar("carga", e.org(), contenido, 200));
			List<Integer> creados = new ArrayList<>();
			for (Future<JsonNode> resultado : hilos.invokeAll(cargas)) {
				creados.add(resultado.get().get("creados").asInt());
			}
			creados.sort(null);
			assertEquals(List.of(0, 12), creados);
		} finally {
			hilos.shutdownNow();
		}
		assertEquals(12, contar("SELECT COUNT(*) FROM alumno WHERE organizacion_id = ?", e.org()));
		assertEquals(12, contar("SELECT COUNT(*) FROM alumno_deporte WHERE organizacion_id = ?", e.org()));
	}

	// ------------------------------------------------------------------ 5. aislamiento

	@Test
	void p05_aislamiento_nadaEnOtraEscuela_yLoDeOtraNoCuenta() throws Exception {
		Escuela a = escuela("p05a");
		Escuela b = escuela("p05b");
		// En B: un grupo con nombre propio, una tarifa propia, un alumno con DNI y licencia.
		long sedeB = b.mollina();
		grupo(b.org(), "Solo de B " + marca, sedeB);
		jdbc.update("INSERT INTO tarifa (organizacion_id, disciplina_id, codigo, nombre, importe, activa) "
				+ "VALUES (?, NULL, 'IT_SOLO_B', 'Solo B', 10, TRUE)", b.org());
		String dniDeB = dni();
		int licenciaDeB = numero();
		long alumnoB = alumnoDirecto(b.org(), dniDeB);
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
				  numero_licencia, organizacion_id, fecha_alta, fecha_alta_inicial, disciplina_id)
				VALUES (TRUE, 0, ?, 0, 1, 1, ?, ?, '2025-09-01', '2025-09-01', ?)""", alumnoB, licenciaDeB, b.org(),
				b.disciplina());
		String huellaOrg1 = huellaDeEscuela(ORG1);
		String huellaB = huellaDeEscuela(b.org());

		JsonNode errores = enviar("ensayo", a.org(), new HojaDePrueba()
				.fila(alumno(numero(), nombre(1), MENOR, "Mollina", "grupo", "Solo de B " + marca))
				.fila(alumno(numero(), nombre(2), MENOR, "Mollina", "tarifa", "IT_SOLO_B"))
				.fila(alumno(numero(), nombre(3), MENOR, "Sede Inicial " + b.org()))
				.xlsx(), 200);
		assertEquals(List.of("No hay ningún grupo «Solo de B " + marca + "» en Mollina.",
				"«IT_SOLO_B» no es una tarifa de esta escuela.",
				"«Sede Inicial " + b.org() + "» no es una sede de esta escuela."), motivos(errores.get("errores")));

		// El DNI y la licencia de B no cuentan en A: se crea.
		JsonNode carga = enviar("carga", a.org(), new HojaDePrueba()
				.fila(alumno(licenciaDeB, nombre(4), MENOR, "Mollina", "dni_nie", dniDeB)).xlsx(), 200);
		assertEquals(1, carga.get("creados").asInt(), carga.toString());
		assertEquals(0, carga.get("yaCargados").asInt());
		Map<String, Object> creado = alumnoPorLicencia(a.org(), licenciaDeB);
		assertEquals(dniDeB, creado.get("nif"));

		assertEquals(huellaOrg1, huellaDeEscuela(ORG1), "Escrito en la organización 1");
		assertEquals(huellaB, huellaDeEscuela(b.org()), "Escrito en otra escuela");
		// Todo lo escrito lleva la escuela A, también las filas de unión.
		long id = ((Number) creado.get("id")).longValue();
		assertEquals(0, contar("SELECT COUNT(*) FROM alumno_deporte WHERE alumno_id = ? AND organizacion_id <> ?", id,
				a.org()));
		assertEquals(0, contar("SELECT COUNT(*) FROM consentimiento WHERE alumno_id = ? AND organizacion_id <> ?", id,
				a.org()));
	}

	// ------------------------------------------------------------------ 6. ni cargos, ni correos, ni usuarios

	@Test
	void p06_ceroProductoAlumno_ceroCorreos_ceroUsuarios() throws Exception {
		Escuela e = escuela("p06");
		String concepto = mensualidadDelMesCargada(e);
		clearInvocations(email);
		long cargos = contar("SELECT COUNT(*) FROM producto_alumno");
		long cuentas = contar("SELECT COUNT(*) FROM usuario");
		long vinculos = contar("SELECT COUNT(*) FROM usuario_alumno");
		long membresias = contar("SELECT COUNT(*) FROM membresia");

		JsonNode informe = enviar("carga", e.org(), new HojaDePrueba()
				.fila(alumno(numero(), nombre(1), MENOR, "Mollina", "tarifa", "IT_MENSUAL", "grupo",
						"Infantil L-X 17:00"))
				.fila(alumno(numero(), nombre(2), ADULTO, "Estepa", "tarifa", "IT_GENERAL", "dni_nie", dni()))
				.xlsx(), 200);
		assertEquals(2, informe.get("creados").asInt());

		assertEquals(cargos, contar("SELECT COUNT(*) FROM producto_alumno"));
		assertEquals(1, contar("SELECT COUNT(*) FROM producto_alumno WHERE concepto = ? AND organizacion_id = ?",
				concepto, e.org()), "Solo el cargo sembrado");
		assertEquals(cuentas, contar("SELECT COUNT(*) FROM usuario"));
		assertEquals(vinculos, contar("SELECT COUNT(*) FROM usuario_alumno"));
		assertEquals(membresias, contar("SELECT COUNT(*) FROM membresia"));
		verifyNoInteractions(email);
	}

	// ------------------------------------------------------------------ 7. uso de imagen

	@Test
	void p07_usoDeImagen_siNoYSinRegistrar() throws Exception {
		Escuela e = escuela("p07");
		int si = numero(), no = numero(), vacio = numero(), sinRegistrar = numero(), siAdulto = numero();
		enviar("carga", e.org(), new HojaDePrueba()
				.fila(alumno(si, nombre(1), MENOR, "Mollina", "autorizacion_imagen", "SI"))
				.fila(alumno(no, nombre(2), MENOR, "Mollina", "autorizacion_imagen", "NO"))
				.fila(alumno(vacio, nombre(3), MENOR, "Mollina", "autorizacion_imagen", ""))
				.fila(alumno(sinRegistrar, nombre(4), MENOR, "Mollina", "autorizacion_imagen", "SIN REGISTRAR"))
				.fila(alumno(siAdulto, nombre(5), ADULTO, "Mollina", "autorizacion_imagen", "sí", "dni_nie", dni()))
				.xlsx(), 200);

		assertConsentimiento(e.org(), si, true, "TUTOR");
		assertConsentimiento(e.org(), no, false, "TUTOR");
		assertConsentimiento(e.org(), siAdulto, true, "PROPIO");
		for (int sinNada : List.of(vacio, sinRegistrar)) {
			long id = ((Number) alumnoPorLicencia(e.org(), sinNada).get("id")).longValue();
			assertEquals(0, contar("SELECT COUNT(*) FROM consentimiento WHERE alumno_id = ?", id));
			assertEquals(Boolean.FALSE, alumnoPorLicencia(e.org(), sinNada).get("autorizacion_web"));
		}
		// Nunca el consentimiento automático del alta (origen APP).
		assertEquals(0, contar("SELECT COUNT(*) FROM consentimiento WHERE organizacion_id = ? AND origen = 'APP'",
				e.org()));
	}

	// ------------------------------------------------------------------ 8. grados

	@Test
	void p08_grados_sinGradoBlanco_menorDanPum_adultoDan_menor4DanError() throws Exception {
		Escuela e = escuela("p08");
		int sinGrado = numero(), menorDan = numero(), adultoDan = numero(), menor4 = numero();
		Map<String, Object> filaMenor4 = alumno(menor4, nombre(4), MENOR, "Mollina", "grado", "4 DAN");
		HojaDePrueba hoja = new HojaDePrueba()
				.fila(alumno(sinGrado, nombre(1), MENOR, "Mollina", "grado", "SIN GRADO"))
				.fila(alumno(menorDan, nombre(2), MENOR, "Mollina", "grado", "1 DAN"))
				.fila(alumno(adultoDan, nombre(3), ADULTO, "Mollina", "grado", "1 DAN", "dni_nie", dni()));
		JsonNode ensayo = enviar("ensayo", e.org(), new HojaDePrueba()
				.fila(alumno(sinGrado, nombre(1), MENOR, "Mollina", "grado", "SIN GRADO"))
				.fila(alumno(menorDan, nombre(2), MENOR, "Mollina", "grado", "1 DAN"))
				.fila(alumno(adultoDan, nombre(3), ADULTO, "Mollina", "grado", "1 DAN", "dni_nie", dni()))
				.fila(filaMenor4).xlsx(), 200);
		assertEquals(List.of("Menor con 4 DAN: no hay PUM equivalente; revisa el grado o la fecha de nacimiento."),
				motivos(ensayo.get("errores")));
		assertEquals(5, ensayo.get("errores").get(0).get("fila").asInt());
		List<String> avisos = motivos(ensayo.get("avisos"));
		assertTrue(avisos.contains("Sin grado: entrará como BLANCO."), avisos.toString());
		assertTrue(avisos.contains("Menor con 1 DAN: entrará como 1 PUM."), avisos.toString());

		enviar("carga", e.org(), hoja.xlsx(), 200);
		assertEquals("BLANCO", deporte(id(e, sinGrado)).get("grado"));
		assertEquals("ROJO_NEGRO_1_PUM", deporte(id(e, menorDan)).get("grado"));
		assertEquals("NEGRO_1_DAN", deporte(id(e, adultoDan)).get("grado"));
	}

	// ------------------------------------------------------------------ 9. dirección pendiente; teléfono obligatorio

	@Test
	void p09_direccionVaciaPendienteConAviso_telefonoVacioError() throws Exception {
		Escuela e = escuela("p09");
		int sinDireccion = numero();
		JsonNode carga = enviar("carga", e.org(), new HojaDePrueba()
				.fila(alumno(sinDireccion, nombre(1), MENOR, "Mollina", "direccion", "")).xlsx(), 200);
		assertTrue(motivos(carga.get("avisos")).contains("Sin dirección: entrará como «Pendiente»."));
		assertEquals("Pendiente", alumnoPorLicencia(e.org(), sinDireccion).get("direccion"));

		JsonNode sinTelefono = enviar("carga", e.org(), new HojaDePrueba()
				.fila(alumno(numero(), nombre(2), MENOR, "Mollina", "telefono", "")).xlsx(), 422);
		JsonNode error = sinTelefono.get("errores").get(0);
		assertEquals("telefono", error.get("columna").asText());
		assertEquals("Falta el teléfono.", error.get("motivo").asText());
		assertEquals(1, contar("SELECT COUNT(*) FROM alumno WHERE organizacion_id = ?", e.org()));
	}

	// ------------------------------------------------------------------ 10. seguridad

	@Test
	void p10_sinAutoridadDePlataforma_403_yAnonimo_401_enLosTresEndpoints() throws Exception {
		Escuela e = escuela("p10");
		byte[] hoja = new HojaDePrueba().fila(alumno(numero(), nombre(1), MENOR, "Mollina")).xlsx();
		List<Cookie> sinPermiso = new ArrayList<>();
		for (String rol : List.of("PROPIETARIO", "ADMINISTRADOR", "FAMILIA")) {
			Usuario usuario = cuenta("it-i1-" + rol.toLowerCase() + "-" + marca + "@example.test");
			jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", usuario.getId(),
					e.org(), rol);
			if ("FAMILIA".equals(rol)) {
				long propio = alumnoDirecto(e.org(), null);
				jdbc.update("""
						INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen)
						VALUES (?, ?, ?, 'PROPIO', 'MANUAL')""", usuario.getId(), propio, e.org());
			}
			sinPermiso.add(new Cookie("jwt", login(usuario.getEmail())));
		}
		long alumnos = contar("SELECT COUNT(*) FROM alumno WHERE organizacion_id = ?", e.org());
		for (String operacion : OPERACIONES) {
			for (Cookie cookie : sinPermiso) {
				assertEquals(403, mvc.perform(peticion(operacion, e.org(), hoja).cookie(cookie)).andReturn()
						.getResponse().getStatus(), operacion);
			}
			assertEquals(401, mvc.perform(peticion(operacion, e.org(), hoja)).andReturn().getResponse().getStatus(),
					operacion);
		}
		assertEquals(alumnos, contar("SELECT COUNT(*) FROM alumno WHERE organizacion_id = ?", e.org()));
	}

	// ------------------------------------------------------------------ 11. ficheros que no valen

	@Test
	void p11_ficheroNoValido_400ConMensaje_escuelaInexistente_404_disciplina_400() throws Exception {
		Escuela e = escuela("p11");
		List<String> sinColumnas = new ArrayList<>(HojaDePrueba.CABECERAS);
		sinColumnas.removeAll(List.of("sede", "telefono"));
		Map<String, byte[]> casos = new java.util.LinkedHashMap<>();
		Map<String, String> mensajes = new HashMap<>();
		casos.put("csv", "numero;nombre\n1;Ana".getBytes());
		mensajes.put("csv", "Este fichero no es un Excel .xlsx.");
		casos.put("sinHoja", new HojaDePrueba().conNombreDeHoja("Deportistas").xlsx());
		mensajes.put("sinHoja", "Este fichero no tiene la hoja Alumnos.");
		casos.put("sinColumnas", new HojaDePrueba(sinColumnas).xlsx());
		mensajes.put("sinColumnas", "Este fichero no es la hoja de alumnos: faltan las columnas sede y telefono.");
		casos.put("grande", new byte[5 * 1024 * 1024 + 1]);
		mensajes.put("grande", "El fichero no puede pasar de 5 MB.");
		for (String operacion : OPERACIONES) {
			for (Map.Entry<String, byte[]> caso : casos.entrySet()) {
				MvcResult r = mvc.perform(peticion(operacion, e.org(), caso.getValue()).cookie(superadmin)).andReturn();
				assertEquals(400, r.getResponse().getStatus(), operacion + " " + caso.getKey());
				assertEquals(mensajes.get(caso.getKey()),
						json.readTree(r.getResponse().getContentAsString()).get("mensaje").asText());
			}
			byte[] valida = new HojaDePrueba().fila(alumno(numero(), nombre(1), MENOR, "Mollina")).xlsx();
			MvcResult noExiste = mvc.perform(peticion(operacion, 999_999_999L, valida).cookie(superadmin)).andReturn();
			assertEquals(404, noExiste.getResponse().getStatus(), operacion);
			MvcResult otraDisciplina = mvc.perform(peticion(operacion, e.org(), valida).param("disciplina", "AJEDREZ")
					.cookie(superadmin)).andReturn();
			assertEquals(400, otraDisciplina.getResponse().getStatus(), operacion);
			assertEquals("La disciplina «AJEDREZ» no existe en esta escuela.",
					json.readTree(otraDisciplina.getResponse().getContentAsString()).get("mensaje").asText());
		}
		// Varias disciplinas y sin decir cuál: 400.
		jdbc.update("""
				INSERT INTO disciplina (organizacion_id, codigo, nombre, sistema_grados_id, edad_adulto, orden)
				VALUES (?, 'IT_OTRA', 'Otra IT', NULL, 14, 9)""", e.org());
		MvcResult varias = mvc.perform(peticion("ensayo", e.org(),
				new HojaDePrueba().fila(alumno(numero(), nombre(1), MENOR, "Mollina")).xlsx()).cookie(superadmin))
				.andReturn();
		assertEquals(400, varias.getResponse().getStatus());
		assertTrue(json.readTree(varias.getResponse().getContentAsString()).get("mensaje").asText()
				.startsWith("La escuela tiene varias disciplinas: indica cuál con el parámetro disciplina"));
		// Con la disciplina indicada, sí.
		assertEquals(1, enviar("ensayo", e.org(), new HojaDePrueba().fila(alumno(numero(), nombre(1), MENOR,
				"Mollina")).xlsx(), 200, "disciplina", "TAEKWONDO").get("listas").asInt());
		assertEquals(0, contar("SELECT COUNT(*) FROM alumno WHERE organizacion_id = ?", e.org()));
	}

	// ------------------------------------------------------------------ 12. hoja con errores

	@Test
	void p12_hojaConErrores_mismoLibroConRevisarYCeldasMarcadas_sinEscribir() throws Exception {
		Escuela e = escuela("p12");
		String antes = huella();
		HojaDePrueba hoja = new HojaDePrueba()
				.fila(alumno(numero(), nombre(1), MENOR, "Mollina", "grupo", "Infantil L-X 17:00", "tarifa",
						"IT_MENSUAL", "revisar", "texto anterior"))
				.fila(alumno(numero(), nombre(2), MENOR, "Mollina", "telefono", "", "grupo", "No existe", "tarifa",
						"IT_MENSUAL"))
				.fila(alumno(numero(), nombre(3), MENOR, "Mollina", "direccion", "", "grupo", "Infantil L-X 17:00",
						"tarifa", "IT_MENSUAL"));
		MvcResult r = mvc.perform(peticion("hoja-con-errores", e.org(), hoja.xlsx()).cookie(superadmin)).andReturn();
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertEquals(XLSX, r.getResponse().getContentType());
		String disposicion = r.getResponse().getHeader("Content-Disposition");
		assertTrue(disposicion.startsWith("attachment;"), disposicion);
		assertTrue(disposicion.contains("hoja maestra-revisar.xlsx") || disposicion.contains("hoja%20maestra-revisar.xlsx"),
				disposicion);

		try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(r.getResponse().getContentAsByteArray()))) {
			Sheet alumnos = libro.getSheet("Alumnos");
			assertNotNull(libro.getSheet("Listas"));
			assertEquals(3, alumnos.getLastRowNum());
			Row cabecera = alumnos.getRow(0);
			for (int c = 0; c < HojaDePrueba.CABECERAS.size(); c++) {
				assertEquals(HojaDePrueba.CABECERAS.get(c), cabecera.getCell(c).getStringCellValue());
			}
			int revisar = HojaDePrueba.CABECERAS.indexOf("revisar");
			assertEquals("", alumnos.getRow(1).getCell(revisar).getStringCellValue());
			assertEquals("No hay ningún grupo «No existe» en Mollina.; Falta el teléfono.",
					alumnos.getRow(2).getCell(revisar).getStringCellValue());
			assertEquals("Sin dirección: entrará como «Pendiente».",
					alumnos.getRow(3).getCell(revisar).getStringCellValue());
			assertEquals(nombre(2), alumnos.getRow(2).getCell(HojaDePrueba.CABECERAS.indexOf("nombre"))
					.getStringCellValue());
			assertTrue(enRojo(alumnos.getRow(2), "telefono"));
			assertTrue(enRojo(alumnos.getRow(2), "grupo"));
			assertFalse(enRojo(alumnos.getRow(2), "nombre"));
			assertFalse(enRojo(alumnos.getRow(3), "direccion"));
		}
		assertEquals(antes, huella());
		assertEquals(0, contar("SELECT COUNT(*) FROM alumno WHERE organizacion_id = ?", e.org()));
	}

	// ------------------------------------------------------------------ 13. sin datos personales

	@Test
	void p13_informeLogsYAuditoria_sinDatosPersonales(CapturedOutput salida) throws Exception {
		Escuela e = escuela("p13");
		long auditoriaAntes = maxAuditoria();
		List<String> secretos = new ArrayList<>();
		HojaDePrueba buena = new HojaDePrueba();
		HojaDePrueba mala = new HojaDePrueba();
		for (int i = 0; i < 3; i++) {
			String nombre = "Secreto" + marca + "N" + i;
			String apellidos = "Apellidosecreto" + marca + i;
			String dni = dni(), dniTutor = dni(), telefono = "69" + (1_000_000 + ThreadLocalRandom.current().nextInt(
					8_999_999)), telefono2 = "95" + (1_000_000 + ThreadLocalRandom.current().nextInt(8_999_999));
			String correo = "secreto-" + marca + "-" + i + "@example.test";
			String tutor = "Tutorsecreto" + marca + i;
			secretos.addAll(List.of(nombre, apellidos, dni, dniTutor, telefono, telefono2, correo, tutor));
			Map<String, Object> fila = alumno(numero(), nombre, MENOR, "Mollina", "apellidos", apellidos, "dni_nie", dni,
					"telefono", telefono, "telefono2", telefono2, "email_familia", correo, "tutor_nombre", tutor,
					"tutor_dni", dniTutor, "direccion", "Callesecreta " + marca);
			buena.fila(fila);
			Map<String, Object> conError = new HashMap<>(fila);
			conError.put("sede", "Ninguna");
			mala.fila(conError);
		}
		secretos.add("Callesecreta " + marca);
		List<String> respuestas = new ArrayList<>();
		respuestas.add(enviar("ensayo", e.org(), mala.xlsx(), 200).toString());
		respuestas.add(enviar("carga", e.org(), mala.xlsx(), 422).toString());
		respuestas.add(enviar("ensayo", e.org(), buena.xlsx(), 200).toString());
		respuestas.add(enviar("carga", e.org(), buena.xlsx(), 200).toString());
		respuestas.add(enviar("carga", e.org(), buena.xlsx(), 200).toString());

		List<String> auditoria = new ArrayList<>();
		for (Map<String, Object> apunte : jdbc.queryForList("""
				SELECT resumen, payload_json, query_params_json, endpoint FROM auditoria_evento WHERE id > ?""",
				auditoriaAntes)) {
			auditoria.add(String.valueOf(apunte));
		}
		assertTrue(auditoria.stream().anyMatch(a -> a.contains("PLATAFORMA IMPORTACION_ALUMNOS organizacion")),
				auditoria.toString());
		for (String secreto : secretos) {
			for (String respuesta : respuestas) {
				assertFalse(respuesta.contains(secreto), "Informe con un dato personal: " + respuesta);
			}
			for (String apunte : auditoria) {
				assertFalse(apunte.contains(secreto), "Auditoría con un dato personal: " + apunte);
			}
			assertFalse(salida.getAll().contains(secreto), "Log con un dato personal");
		}
	}

	// ------------------------------------------------------------------ 14. el alta manual no cambia

	@Test
	void p14_altaManual_conservaConsentimientoAutomaticoYMensualidadCondicional() throws Exception {
		Escuela e = escuela("p14");
		String concepto = mensualidadDelMesCargada(e);
		Usuario gestor = cuenta("it-i1-gestor-" + marca + "@example.test");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'ADMINISTRADOR')",
				gestor.getId(), e.org());
		Cookie cookie = new Cookie("jwt", login(gestor.getEmail()));
		ObjectNode nuevo = json.createObjectNode();
		nuevo.put("nombre", "Manual " + marca);
		nuevo.put("apellidos", "Prueba Ficticia");
		nuevo.put("fechaNacimiento", MENOR.toString());
		nuevo.put("direccion", "Calle Manual 1");
		nuevo.put("telefono", "600111222");
		nuevo.put("autorizacionWeb", true);
		nuevo.put("sedePrincipalId", e.mollina());
		ObjectNode deporte = nuevo.putArray("deportesInicial").addObject();
		deporte.put("deporte", "TAEKWONDO");
		deporte.put("grado", "BLANCO");
		deporte.put("cuantiaTarifa", 30);
		MvcResult r = mvc.perform(multipart("/api/alumnos/crear").param("nuevo", nuevo.toString()).cookie(cookie))
				.andReturn();
		assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		long id = json.readTree(r.getResponse().getContentAsString()).get("id").asLong();

		// Consentimiento automático de siempre: USO_IMAGEN, origen APP, de hoy, firmado por quien da el alta.
		Map<String, Object> consentimiento = jdbc.queryForMap("""
				SELECT tipo, otorgado, origen, fecha, fecha_aproximada, firmante_nombre FROM consentimiento
				WHERE alumno_id = ?""", id);
		assertEquals("USO_IMAGEN", consentimiento.get("tipo"));
		assertEquals(Boolean.TRUE, consentimiento.get("otorgado"));
		assertEquals("APP", consentimiento.get("origen"));
		assertEquals(java.sql.Date.valueOf(HOY), consentimiento.get("fecha"));
		assertEquals(Boolean.FALSE, consentimiento.get("fecha_aproximada"));
		assertNotNull(consentimiento.get("firmante_nombre"));
		// Mensualidad condicional de siempre: ya hay cargos de este mes en la escuela, así que el alta recibe el suyo.
		assertEquals(1, contar("SELECT COUNT(*) FROM producto_alumno WHERE alumno_id = ? AND concepto = ?", id,
				concepto));
	}

	// ------------------------------------------------------------------ 15. el ensayo no consume expedientes

	@Test
	void p15_elEnsayoNoConsumeNumerosDeExpediente() throws Exception {
		Escuela e = escuela("p15");
		alumnoDirecto(e.org(), null);
		Long contador = contador(e.org());
		long maximo = contar("SELECT COALESCE(MAX(numero_expediente), 0) FROM alumno WHERE organizacion_id = ?",
				e.org());
		long esperado = Math.max(contador == null ? 0 : contador, maximo) + 1;
		HojaDePrueba hoja = new HojaDePrueba().fila(alumno(numero(), nombre(1), MENOR, "Mollina"))
				.fila(alumno(numero(), nombre(2), MENOR, "Estepa"));
		for (int i = 0; i < 3; i++) {
			assertEquals(2, enviar("ensayo", e.org(), hoja.xlsx(), 200).get("listas").asInt());
		}
		assertEquals(contador == null ? Long.valueOf(0) : contador, contador(e.org()));
		enviar("carga", e.org(), hoja.xlsx(), 200);
		assertEquals(List.of(esperado, esperado + 1), jdbc.queryForList("""
				SELECT a.numero_expediente FROM alumno a JOIN alumno_deporte ad ON ad.alumno_id = a.id
				WHERE a.organizacion_id = ? AND ad.numero_licencia IS NOT NULL ORDER BY a.numero_expediente""",
				Long.class, e.org()));
	}

	// ------------------------------------------------------------------ 16. todo o nada con un fallo a mitad de escritura

	@Test
	void p16_falloAMitadDeEscritura_422_yNoQuedaNada() throws Exception {
		Escuela e = escuela("p16");
		Long contadorAntes = contador(e.org());
		String antes = huella();
		long auditoriaAntes = maxAuditoria();
		int segunda = numero();
		HojaDePrueba hoja = tresFilasConGrupo(e, segunda);
		// El alta en el grupo de la 2.ª fila falla con un error de negocio de los de siempre: la 1.ª ya está escrita.
		fallarAlAgregarAlGrupo(new IllegalArgumentException("El alumno ya pertenece a este grupo"));

		JsonNode informe = enviar("carga", e.org(), hoja.xlsx(), 422);
		assertEquals(0, informe.get("creados").asInt());
		assertEquals(1, informe.get("errores").size(), informe.toString());
		JsonNode error = informe.get("errores").get(0);
		assertEquals(3, error.get("fila").asInt());
		assertEquals(segunda, error.get("numeroFederacion").asInt());
		assertEquals("No se ha podido crear el alumno de esta fila. Avisa al administrador.",
				error.get("motivo").asText());
		verify(espiaDeGrupos(), times(2)).agregarAlumnoAGrupo(anyLong(), anyLong());
		noQuedaNada(e, antes, contadorAntes, auditoriaAntes);
	}

	// ------------------------------------------------------------------ 17. fallo inesperado: 500 y nada escrito

	@Test
	void p17_falloInesperado_500EnLasTresOperaciones_nadaEscrito_yElLogDaLaCausaSinDatos(CapturedOutput salida)
			throws Exception {
		Escuela e = escuela("p17");
		Long contadorAntes = contador(e.org());
		String antes = huella();
		long auditoriaAntes = maxAuditoria();
		int segunda = numero();
		HojaDePrueba hoja = tresFilasConGrupo(e, segunda);
		String secreto = "Secreto" + marca;
		for (String operacion : OPERACIONES) {
			fallarAlAgregarAlGrupo(new RuntimeException("fallo que cita " + secreto));
			MvcResult r = mvc.perform(peticion(operacion, e.org(), hoja.xlsx()).cookie(superadmin)).andReturn();
			assertEquals(500, r.getResponse().getStatus(), operacion + " " + r.getResponse().getContentAsString());
			String cuerpo = r.getResponse().getContentAsString();
			assertEquals("No se ha podido terminar por un fallo inesperado: no se ha escrito nada. Avisa al "
					+ "administrador.", json.readTree(cuerpo).get("mensaje").asText(), operacion);
			assertFalse(cuerpo.contains(secreto), cuerpo);
		}
		noQuedaNada(e, antes, contadorAntes, auditoriaAntes);
		// El log dice dónde (fila y número de federación) y da la traza, sin el mensaje ni datos de la hoja.
		String log = salida.getAll();
		assertTrue(log.contains("fallo inesperado en la fila 3 (número de federación " + segunda + ")"), log);
		assertTrue(log.contains("java.lang.RuntimeException"), "Sin la clase de la causa");
		assertTrue(log.contains("ImportacionAlumnosIntegrationTest"), "Sin la traza");
		assertFalse(log.contains(secreto), "El log cita el mensaje de la excepción");
		for (int i = 1; i <= 3; i++) {
			assertFalse(log.contains(nombre(i)), "El log cita un nombre");
		}

		// Un error de negocio, en cambio, sigue siendo un error de la fila: 200 en el ensayo, 422 en la carga.
		fallarAlAgregarAlGrupo(new IllegalArgumentException("El alumno ya pertenece a este grupo"));
		JsonNode ensayo = enviar("ensayo", e.org(), hoja.xlsx(), 200);
		assertEquals(List.of("No se ha podido crear el alumno de esta fila. Avisa al administrador."),
				motivos(ensayo.get("errores")));
		assertEquals(3, ensayo.get("errores").get(0).get("fila").asInt());
		fallarAlAgregarAlGrupo(new IllegalArgumentException("El alumno ya pertenece a este grupo"));
		enviar("carga", e.org(), hoja.xlsx(), 422);
		assertEquals(0, contar("SELECT COUNT(*) FROM alumno WHERE organizacion_id = ?", e.org()));
	}

	// ------------------------------------------------------------------ 18. tarifas desactivadas y familiares

	@Test
	void p18_tarifaDesactivadaError_tarifaFamiliarAvisoYEntraConElImporteGeneral() throws Exception {
		Escuela e = escuela("p18");
		jdbc.update("""
				INSERT INTO tarifa (organizacion_id, disciplina_id, codigo, nombre, importe, activa)
				VALUES (?, ?, 'IT_APAGADA', 'Apagada IT', 20.00, FALSE)""", e.org(), e.disciplina());
		jdbc.update("""
				INSERT INTO tarifa (organizacion_id, disciplina_id, codigo, nombre, importe, importe_adulto, importe_menor,
				  activa) VALUES (?, ?, 'IT_FAMILIA', 'Familia IT', 40.00, 30.00, 20.00, TRUE)""", e.org(), e.disciplina());
		// HERMANOS viene de la plantilla del alta: se deja activa y con un importe conocido.
		assertEquals(1, jdbc.update("""
				UPDATE tarifa SET importe = 28.00, importe_adulto = NULL, importe_menor = NULL, activa = TRUE,
				  disciplina_id = NULL WHERE organizacion_id = ? AND codigo = 'HERMANOS'""", e.org()));
		int familia = numero(), hermanos = numero();
		JsonNode ensayo = enviar("ensayo", e.org(), new HojaDePrueba()
				.fila(alumno(numero(), nombre(1), MENOR, "Mollina", "tarifa", "IT_APAGADA"))
				.fila(alumno(familia, nombre(2), MENOR, "Mollina", "tarifa", "Familia IT"))
				.fila(alumno(hermanos, nombre(3), MENOR, "Mollina", "tarifa", "HERMANOS")).xlsx(), 200);
		assertEquals(List.of("La tarifa «IT_APAGADA» está desactivada en esta escuela: actívala antes de cargar."),
				motivos(ensayo.get("errores")));
		List<String> familiares = new ArrayList<>();
		ensayo.get("avisos").forEach(a -> {
			if ("tarifa".equals(a.get("columna").asText())) {
				familiares.add(a.get("fila").asInt() + " " + a.get("motivo").asText());
			}
		});
		assertEquals(List.of("3 Tarifa familiar: entra con el importe general; revisa el importe en la ficha.",
				"4 Tarifa familiar: entra con el importe general; revisa el importe en la ficha."), familiares);

		enviar("carga", e.org(), new HojaDePrueba()
				.fila(alumno(familia, nombre(2), MENOR, "Mollina", "tarifa", "Familia IT"))
				.fila(alumno(hermanos, nombre(3), MENOR, "Mollina", "tarifa", "HERMANOS")).xlsx(), 200);
		assertEquals(40.0, ((Number) deporte(id(e, familia)).get("cuantia_tarifa")).doubleValue());
		assertEquals(28.0, ((Number) deporte(id(e, hermanos)).get("cuantia_tarifa")).doubleValue());
	}

	// ------------------------------------------------------------------ 19. superadmin con escuela propia

	@Test
	void p19_superadminConMembresiaYSesionEnOtraEscuela_todoQuedaEnLaDestino() throws Exception {
		Escuela destino = escuela("p19a");
		Escuela suya = escuela("p19b");
		Usuario cuenta = cuenta("it-i1-superadmin-con-escuela-" + marca + "@example.test");
		assertEquals(1, jdbc.update("UPDATE usuario SET es_admin_plataforma = TRUE, ultima_organizacion_id = ? "
				+ "WHERE id = ?", suya.org(), cuenta.getId()));
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'ADMINISTRADOR')",
				cuenta.getId(), suya.org());
		String token = login(cuenta.getEmail());
		assertEquals(suya.org(), jwt.verificar(token).organizacionId(), "La sesión no es la de su escuela");
		Cookie sesion = new Cookie("jwt", token);
		String huellaSuya = huellaDeEscuela(suya.org());
		String huellaOrg1 = huellaDeEscuela(ORG1);
		long auditoriaAntes = maxAuditoria();

		// Las dos escuelas tienen sedes, grupos y tarifas con los mismos nombres: una fuga se resolvería con los de la suya.
		int n1 = numero(), n2 = numero();
		byte[] hoja = new HojaDePrueba()
				.fila(alumno(n1, nombre(1), MENOR, "Mollina", "grupo", "Infantil L-X 17:00", "tarifa", "IT_MENSUAL",
						"tutor_nombre", "Tutora " + marca, "tutor_parentesco", "MADRE", "autorizacion_imagen", "SI"))
				.fila(alumno(n2, nombre(2), ADULTO, "Estepa", "grupo", "Adultos", "tarifa", "IT_GENERAL", "dni_nie", dni(),
						"autorizacion_imagen", "NO"))
				.xlsx();
		assertEquals(2, enviarCon(sesion, "ensayo", destino.org(), hoja, 200).get("listas").asInt());
		assertEquals(2, enviarCon(sesion, "carga", destino.org(), hoja, 200).get("creados").asInt());

		assertEquals(2, contar("SELECT COUNT(*) FROM alumno WHERE organizacion_id = ?", destino.org()));
		assertEquals(huellaSuya, huellaDeEscuela(suya.org()), "Escrito en la escuela de la sesión");
		assertEquals(huellaOrg1, huellaDeEscuela(ORG1), "Escrito en la organización 1");
		long id1 = id(destino, n1), id2 = id(destino, n2);
		assertEquals(destino.mollina(), ((Number) alumnoPorLicencia(destino.org(), n1).get("sede_principal_id"))
				.longValue());
		assertEquals(destino.disciplina(), ((Number) deporte(id1).get("disciplina_id")).longValue());
		assertEquals(destino.tarifaMensual(), ((Number) deporte(id1).get("tarifa_id")).longValue());
		assertEquals(destino.tarifaGeneral(), ((Number) deporte(id2).get("tarifa_id")).longValue());
		assertEquals(List.of(destino.grupoInfantil()), gruposDe(id1));
		assertEquals(destino.turnosInfantil(), turnosDe(id1));
		assertEquals(List.of(destino.grupoAdultos()), gruposDe(id2));
		for (String tabla : List.of("alumno_deporte", "tutor", "consentimiento")) {
			assertEquals(0, contar("SELECT COUNT(*) FROM " + tabla + " WHERE alumno_id IN (?, ?) AND organizacion_id <> ?",
					id1, id2, destino.org()), tabla);
		}
		List<Map<String, Object>> apuntes = auditoriaDesde(auditoriaAntes);
		assertTrue(apuntes.stream().allMatch(a -> org(a) == destino.org()), apuntes.toString());
		assertEquals(1, apuntes.stream().filter(a -> "CREATE".equals(a.get("accion"))).count(), apuntes.toString());
	}

	/** Tres filas válidas de la misma sede y el mismo grupo; la segunda con el número {@code segunda}. */
	private HojaDePrueba tresFilasConGrupo(Escuela e, int segunda) {
		HojaDePrueba hoja = new HojaDePrueba();
		for (int i = 1; i <= 3; i++) {
			hoja.fila(alumno(i == 2 ? segunda : numero(), nombre(i), MENOR, "Mollina", "grupo", "Infantil L-X 17:00",
					"tarifa", "IT_MENSUAL", "tutor_nombre", "Tutora " + marca, "tutor_parentesco", "MADRE",
					"autorizacion_imagen", "SI"));
		}
		return hoja;
	}

	/** La 2.ª llamada al alta en un grupo (la de la 2.ª fila) lanza {@code fallo}; las demás, las de siempre. */
	private void fallarAlAgregarAlGrupo(RuntimeException fallo) {
		AtomicInteger llamadas = new AtomicInteger();
		// Sobre el espía de debajo del proxy transaccional (el stub no debe abrir una transacción).
		doAnswer(llamada -> {
			if (llamadas.incrementAndGet() == 2) {
				throw fallo;
			}
			return llamada.callRealMethod();
		}).when(espiaDeGrupos()).agregarAlumnoAGrupo(anyLong(), anyLong());
	}

	private GrupoService espiaDeGrupos() {
		return AopTestUtils.getUltimateTargetObject(grupos);
	}

	/** Nada escrito: las tablas de p01 iguales, el contador sin consumir y ningún apunte en la escuela. */
	private void noQuedaNada(Escuela e, String huellaAntes, Long contadorAntes, long auditoriaAntes) {
		assertEquals(huellaAntes, huella(), "Quedaron filas escritas o cambiadas");
		assertEquals(contadorAntes == null ? Long.valueOf(0) : contadorAntes, contador(e.org()));
		assertEquals(0, contar("SELECT COUNT(*) FROM alumno WHERE organizacion_id = ?", e.org()));
		List<Map<String, Object>> apuntes = auditoriaDesde(auditoriaAntes);
		assertTrue(apuntes.stream().noneMatch(a -> "CREATE".equals(a.get("accion"))
				&& String.valueOf(a.get("resumen")).startsWith("PLATAFORMA IMPORTACION_ALUMNOS ")), apuntes.toString());
		assertTrue(apuntes.stream().noneMatch(a -> org(a) == e.org() && "CREATE".equals(a.get("accion"))),
				apuntes.toString());
	}

	// ------------------------------------------------------------------ escuela de prueba

	private Escuela escuela(String etiqueta) throws Exception {
		ObjectNode alta = json.createObjectNode();
		alta.put("nombre", "Escuela IT I1 " + etiqueta + " " + marca);
		alta.put("emailContacto", "buzon-" + etiqueta + "-" + marca + "@example.test");
		alta.putObject("sede").put("nombre", "Sede Inicial");
		ObjectNode propietario = alta.putObject("propietario");
		propietario.put("nombre", "Dueña");
		propietario.put("apellidos", "Ficticia");
		propietario.put("email", "it-i1-dueno-" + etiqueta + "-" + marca + "@example.test");
		alta.putArray("disciplinas").add("TAEKWONDO");
		MvcResult r = mvc.perform(post("/api/plataforma/escuelas").contentType(MediaType.APPLICATION_JSON)
				.content(alta.toString()).cookie(superadmin)).andReturn();
		assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		long org = json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
		// Nombre único de la sede inicial (para citarla desde otra escuela en el test de aislamiento).
		jdbc.update("UPDATE sede SET nombre = ? WHERE organizacion_id = ?", "Sede Inicial " + org, org);

		long disciplina = jdbc.queryForObject(
				"SELECT id FROM disciplina WHERE organizacion_id = ? AND codigo = 'TAEKWONDO'", Long.class, org);
		long mollina = sede(org, "Mollina", true);
		long estepa = sede(org, "Estepa", true);
		long herrera = sede(org, "Herrera", false);
		long infantil = grupo(org, "Infantil L-X 17:00", mollina);
		long adultos = grupo(org, "Adultos", estepa);
		List<Long> turnosInfantil = List.of(turno(org, infantil, "Lunes"), turno(org, infantil, "Miércoles"));
		long turnoAdultos = turno(org, adultos, "Martes");
		jdbc.update("""
				INSERT INTO tarifa (organizacion_id, disciplina_id, codigo, nombre, importe, activa)
				VALUES (?, ?, 'IT_MENSUAL', 'Mensual IT', 31.50, TRUE)""", org, disciplina);
		jdbc.update("""
				INSERT INTO tarifa (organizacion_id, disciplina_id, codigo, nombre, importe, activa)
				VALUES (?, NULL, 'IT_GENERAL', 'General IT', 25.00, TRUE)""", org);
		long mensual = jdbc.queryForObject("SELECT id FROM tarifa WHERE organizacion_id = ? AND codigo = 'IT_MENSUAL'",
				Long.class, org);
		long general = jdbc.queryForObject("SELECT id FROM tarifa WHERE organizacion_id = ? AND codigo = 'IT_GENERAL'",
				Long.class, org);
		return new Escuela(org, disciplina, mollina, estepa, herrera, infantil, adultos, turnosInfantil, turnoAdultos,
				mensual, general);
	}

	private long sede(long org, String nombre, boolean activa) {
		jdbc.update("INSERT INTO sede (organizacion_id, nombre, activa) VALUES (?, ?, ?)", org, nombre, activa);
		return jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = ? AND nombre = ?", Long.class, org,
				nombre);
	}

	private long grupo(long org, String nombre, long sede) {
		GrupoConAlumnosDTO dto = new GrupoConAlumnosDTO();
		dto.setNombre(nombre);
		dto.setDeporte("TAEKWONDO");
		dto.setSedeId(sede);
		return TenantContext.calcularCon(org, () -> grupos.crearGrupo(dto).getId());
	}

	private long turno(long org, long grupo, String dia) {
		jdbc.update("""
				INSERT INTO turno (dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id)
				VALUES (?, '17:00', '18:00', NULL, ?, ?)""", dia, grupo, org);
		return jdbc.queryForObject("SELECT MAX(id) FROM turno WHERE organizacion_id = ?", Long.class, org);
	}

	/** Un alumno de la escuela escrito a mano (con su número de expediente), sin pasar por el importador. */
	private long alumnoDirecto(long org, String nif) {
		long expediente = jdbc.queryForObject(
				"SELECT COALESCE(MAX(numero_expediente), 0) + 1 FROM alumno WHERE organizacion_id = ?", Long.class, org);
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre, nif,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id)
				VALUES (TRUE, 'PRUEBA', 1, 'Calle 1', '2012-01-01', 'DIRECTO', ?, ?, '600000000', 0, ?)""", nif,
				expediente, org);
		return jdbc.queryForObject("SELECT id FROM alumno WHERE organizacion_id = ? AND numero_expediente = ?",
				Long.class, org, expediente);
	}

	/**
	 * Deja la escuela con la mensualidad del mes ya cargada a un alumno: la condición con la que el alta de siempre
	 * crea la mensualidad del alumno nuevo ({@code asignarMensualidadGeneralSiCorresponde}). Devuelve el concepto.
	 */
	private String mensualidadDelMesCargada(Escuela e) {
		jdbc.update("""
				INSERT INTO producto (concepto, precio, organizacion_id, tipo, disciplina_id, vendible_profesor)
				VALUES ('Mensualidad IT', 30, ?, 'MENSUALIDAD', ?, FALSE)""", e.org(), e.disciplina());
		long existente = alumnoDirecto(e.org(), null);
		LocalDate hoy = LocalDate.now();
		String mesAno = hoy.getYear() + "-" + "%02d".formatted(hoy.getMonthValue());
		return enEscuela.escribir(e.org(), () -> {
			Producto producto = productosCatalogo.resolverProductoMensualidad("TAEKWONDO").orElseThrow();
			String concepto = MensualidadUtils.formatearNombreMensualidad(CatalogoLegadoTaemoi.conceptoBase(producto,
					ProductosCatalogoService.CONCEPTO_LEGADO_MENSUALIDAD, false), mesAno);
			ProductoAlumno cargo = new ProductoAlumno();
			cargo.setAlumno(alumnoRepository.findById(existente).orElseThrow());
			cargo.setProducto(producto);
			cargo.setConcepto(concepto);
			cargo.setPrecio(30.0);
			cargo.setFechaAsignacion(new Date());
			cargo.setCantidad(1);
			cargo.setPagado(false);
			productosAlumno.save(cargo);
			return concepto;
		});
	}

	// ------------------------------------------------------------------ peticiones

	private MockMultipartHttpServletRequestBuilder peticion(String operacion, long org, byte[] contenido) {
		return multipart("/api/plataforma/escuelas/" + org + "/importacion/alumnos/" + operacion)
				.file(new MockMultipartFile("file", "hoja maestra.xlsx", XLSX, contenido));
	}

	private JsonNode enviar(String operacion, long org, byte[] contenido, int estado, String... parametros)
			throws Exception {
		return enviarCon(superadmin, operacion, org, contenido, estado, parametros);
	}

	private JsonNode enviarCon(Cookie sesion, String operacion, long org, byte[] contenido, int estado,
			String... parametros) throws Exception {
		MockMultipartHttpServletRequestBuilder peticion = peticion(operacion, org, contenido);
		for (int i = 0; i < parametros.length; i += 2) {
			peticion.param(parametros[i], parametros[i + 1]);
		}
		MvcResult r = mvc.perform(peticion.cookie(sesion)).andReturn();
		assertEquals(estado, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString());
	}

	// ------------------------------------------------------------------ datos inventados

	private int numero() {
		return siguienteNumero++;
	}

	private String nombre(int i) {
		return "Nombre" + marca + "x" + i;
	}

	/** Un DNI con el formato válido (sin comprobar la letra), al azar. */
	private static String dni() {
		return String.format("%08d", ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999)) + "T";
	}

	private Usuario cuenta(String correo) {
		return TenantContext.calcularCon(ORG1, () -> {
			Usuario usuario = new Usuario();
			usuario.setNombre("Cuenta");
			usuario.setApellidos("Ficticia");
			usuario.setEmail(correo);
			usuario.setContrasena(encoder.encode(CLAVE));
			usuario.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(usuario);
		});
	}

	private String login(String correo) throws Exception {
		MvcResult resultado = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}")).andReturn();
		assertEquals(200, resultado.getResponse().getStatus());
		return json.readTree(resultado.getResponse().getContentAsString()).get("token").asText();
	}

	// ------------------------------------------------------------------ consultas

	private long contar(String sql, Object... parametros) {
		Long n = jdbc.queryForObject(sql, Long.class, parametros);
		return n == null ? 0 : n;
	}

	private Long contador(long org) {
		List<Long> valores = jdbc.queryForList(
				"SELECT valor FROM contador WHERE organizacion_id = ? AND clave = 'NUMERO_EXPEDIENTE'", Long.class, org);
		return valores.isEmpty() ? null : valores.get(0);
	}

	/** CHECKSUM TABLE de las tablas que el importador escribe (o no debe tocar). */
	private String huella() {
		StringBuilder sb = new StringBuilder();
		for (String tabla : TABLAS) {
			sb.append(jdbc.queryForList("CHECKSUM TABLE " + tabla)).append('\n');
		}
		return sb.toString();
	}

	/** Las filas de una escuela, tabla a tabla (las de unión, por su alumno). */
	private String huellaDeEscuela(long org) {
		StringBuilder sb = new StringBuilder();
		for (String tabla : List.of("alumno", "alumno_deporte", "tutor", "consentimiento", "producto_alumno", "grupo",
				"turno", "sede", "tarifa", "contador")) {
			sb.append(tabla).append(jdbc.queryForList("SELECT * FROM " + tabla + " WHERE organizacion_id = ? ORDER BY id",
					org)).append('\n');
		}
		sb.append(jdbc.queryForList(
				"SELECT * FROM usuario_alumno WHERE organizacion_id = ? ORDER BY usuario_id, alumno_id", org));
		sb.append(jdbc.queryForList("""
				SELECT ag.* FROM alumno_grupo ag JOIN alumno a ON a.id = ag.alumno_id WHERE a.organizacion_id = ?
				ORDER BY ag.alumno_id, ag.grupo_id""", org));
		sb.append(jdbc.queryForList("""
				SELECT t.* FROM alumno_turno t JOIN alumno a ON a.id = t.alumno_id WHERE a.organizacion_id = ?
				ORDER BY t.alumno_id, t.turno_id""", org));
		sb.append(jdbc.queryForList("SELECT * FROM auditoria_evento WHERE organizacion_id = ? ORDER BY id", org));
		return sb.toString();
	}

	private long maxAuditoria() {
		return contar("SELECT COALESCE(MAX(id), 0) FROM auditoria_evento");
	}

	private List<Map<String, Object>> auditoriaDesde(long id) {
		return jdbc.queryForList("""
				SELECT organizacion_id, accion, modulo, endpoint, resumen, estado_http FROM auditoria_evento
				WHERE id > ? AND endpoint LIKE '/api/plataforma/escuelas/%/importacion/%' ORDER BY id""", id);
	}

	private static long org(Map<String, Object> apunte) {
		return apunte.get("organizacion_id") == null ? -1 : ((Number) apunte.get("organizacion_id")).longValue();
	}

	private Map<String, Object> alumnoPorLicencia(long org, int licencia) {
		return jdbc.queryForMap("""
				SELECT a.* FROM alumno a JOIN alumno_deporte ad ON ad.alumno_id = a.id
				WHERE a.organizacion_id = ? AND ad.numero_licencia = ?""", org, licencia);
	}

	private long id(Escuela e, int licencia) {
		return ((Number) alumnoPorLicencia(e.org(), licencia).get("id")).longValue();
	}

	private Map<String, Object> deporte(long alumnoId) {
		return jdbc.queryForMap("""
				SELECT ad.disciplina_id, g.codigo AS grado, ad.tiene_licencia, ad.numero_licencia, ad.fecha_licencia,
				       ad.tarifa_id, ad.cuantia_tarifa, ad.activo
				FROM alumno_deporte ad LEFT JOIN grado g ON g.id = ad.grado_id WHERE ad.alumno_id = ?""", alumnoId);
	}

	private List<Map<String, Object>> tutores(long alumnoId) {
		List<Map<String, Object>> lista = new ArrayList<>();
		for (Map<String, Object> t : jdbc.queryForList(
				"SELECT nombre, nif, parentesco, responsable_legal FROM tutor WHERE alumno_id = ? ORDER BY orden, id",
				alumnoId)) {
			lista.add(Map.of("nombre", t.get("nombre"), "nif", t.get("nif") == null ? "" : t.get("nif"), "parentesco",
					t.get("parentesco"), "responsable_legal", t.get("responsable_legal")));
		}
		return lista;
	}

	private List<Long> gruposDe(long alumnoId) {
		return jdbc.queryForList("SELECT grupo_id FROM alumno_grupo WHERE alumno_id = ? ORDER BY grupo_id", Long.class,
				alumnoId);
	}

	private List<Long> turnosDe(long alumnoId) {
		return jdbc.queryForList("SELECT turno_id FROM alumno_turno WHERE alumno_id = ? ORDER BY turno_id", Long.class,
				alumnoId);
	}

	private void assertConsentimiento(long org, int licencia, boolean otorgado, String relacion) {
		Map<String, Object> alumno = alumnoPorLicencia(org, licencia);
		long id = ((Number) alumno.get("id")).longValue();
		Map<String, Object> c = jdbc.queryForMap("""
				SELECT tipo, otorgado, origen, fecha, fecha_aproximada, firmante_relacion, firmante_nombre
				FROM consentimiento WHERE alumno_id = ?""", id);
		assertEquals("USO_IMAGEN", c.get("tipo"));
		assertEquals(otorgado, c.get("otorgado"));
		assertEquals("FICHA_PAPEL", c.get("origen"));
		assertEquals(java.sql.Date.valueOf(HOY), c.get("fecha"));
		assertEquals(Boolean.TRUE, c.get("fecha_aproximada"));
		assertEquals(relacion, c.get("firmante_relacion"));
		assertNull(c.get("firmante_nombre"));
		assertEquals(otorgado, alumno.get("autorizacion_web"));
	}

	private static List<String> nombresDeCampos(JsonNode objeto) {
		List<String> nombres = new ArrayList<>();
		objeto.fieldNames().forEachRemaining(nombres::add);
		return nombres;
	}

	private static List<String> motivos(JsonNode incidencias) {
		List<String> lista = new ArrayList<>();
		incidencias.forEach(i -> lista.add(i.get("motivo").asText()));
		return lista;
	}

	private static boolean enRojo(Row fila, String columna) {
		var celda = fila.getCell(HojaDePrueba.CABECERAS.indexOf(columna));
		if (celda == null) {
			return false;
		}
		XSSFCellStyle estilo = (XSSFCellStyle) celda.getCellStyle();
		return estilo.getFillPattern() == FillPatternType.SOLID_FOREGROUND && estilo.getFillForegroundXSSFColor() != null
				&& java.util.Arrays.equals(new byte[] { (byte) 0xFF, (byte) 0xC7, (byte) 0xCE },
						estilo.getFillForegroundXSSFColor().getRGB());
	}
}
