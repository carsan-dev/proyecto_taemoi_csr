package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.taemoi.project.controllers.AlumnoController;
import com.taemoi.project.dtos.AlumnoDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.legado.Deporte;
import com.taemoi.project.entities.Disciplina;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.Producto;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.entities.SistemaGrados;
import com.taemoi.project.legado.TipoGrado;
import com.taemoi.project.legado.TipoTarifa;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.DisciplinaRepository;
import com.taemoi.project.repositories.GradoRepository;
import com.taemoi.project.repositories.SistemaGradosRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.repositories.ProductoRepository;
import com.taemoi.project.services.ContadorService;
import com.taemoi.project.utils.MensualidadUtils;

/**
 * Alta de alumnos sin las columnas de deporte único: todo alumno nace con al menos un
 * AlumnoDeporte, tanto si llega con {@code deportesInicial} (lo que envía el frontend) como con
 * los campos de deporte único del nivel superior (contrato anterior), y sin deporte es un 400.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AltaAlumnoDeportesIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG_TAEMOI = 1L;

	@Autowired OrganizacionRepository organizaciones;
	@Autowired AlumnoRepository alumnos;
	@Autowired GradoRepository grados;
	@Autowired SistemaGradosRepository sistemas;
	@Autowired DisciplinaRepository disciplinas;
	@Autowired ProductoRepository productos;
	@Autowired com.taemoi.project.repositories.TarifaRepository tarifas;
	@Autowired ProductoAlumnoRepository productosAlumno;
	@Autowired ContadorService contadores;
	@Autowired AlumnoController alumnoController;
	@Autowired JdbcTemplate jdbc;

	long org;
	String nombreMensualidad;

	@BeforeAll
	void prepararEscuela() {
		Organizacion o = new Organizacion();
		o.setSlug("it-alta-deportes-" + UUID.randomUUID().toString().substring(0, 8));
		o.setNombre("Escuela alta deportes");
		org = TenantContext.calcularCon(ORG_TAEMOI, () -> organizaciones.save(o).getId());
		assertNotEquals(ORG_TAEMOI, org);

		LocalDate hoy = LocalDate.now();
		nombreMensualidad = MensualidadUtils.formatearNombreMensualidad(
				hoy.getYear() + "-" + "%02d".formatted(hoy.getMonthValue()));
		TenantContext.ejecutarCon(org, () -> {
			// Catálogo propio de la escuela (V26: los grados se resuelven por disciplina): taekwondo con
			// tres grados, kickboxing con su grado de entrada y pilates sin grados.
			SistemaGrados tkd = sistema("Taekwondo");
			int orden = 1;
			for (TipoGrado tipo : List.of(TipoGrado.BLANCO, TipoGrado.AMARILLO, TipoGrado.VERDE)) {
				grado(tkd, tipo, orden++);
			}
			SistemaGrados kb = sistema("Kickboxing");
			grado(kb, TipoGrado.BLANCO, 1);
			disciplina("TAEKWONDO", tkd, 14, 1);
			disciplina("KICKBOXING", kb, 15, 2);
			disciplina("PILATES", null, 14, 3);
			// Tarifa propia de la escuela (V27): la cuantía por defecto del alta sale de ella.
			DisciplinasDePrueba.tarifa(tarifas, "KICKBOXING", "30.00", disciplinas.findByCodigo("KICKBOXING").orElseThrow());
			// V28: las demás tarifas que usan estas altas también son de la escuela (sin tipo_tarifa, un código que la
			// escuela no tiene ya no se guarda: es un 400).
			DisciplinasDePrueba.tarifa(tarifas, "ADULTO", "30.00", null);
			DisciplinasDePrueba.tarifa(tarifas, "INFANTIL", "28.00", null);
			DisciplinasDePrueba.tarifa(tarifas, "PILATES", "30.00", disciplinas.findByCodigo("PILATES").orElseThrow());
			// Mensualidad del mes ya cargada: el alta debe añadir la del nuevo alumno.
			Producto mensualidad = productos.save(new Producto("MENSUALIDAD", 30.0, null));
			// Si el alta cobrara la licencia, este producto lo permitiría: no debe usarse.
			productos.save(new Producto("LICENCIA FEDERATIVA KICKBOXING ADULTO", 50.0, null));
			Alumno previo = new Alumno();
			previo.setNombre("PREVIO");
			previo.setApellidos("ALTA DEPORTES");
			previo.setNumeroExpediente(contadores.siguienteNumeroExpediente());
			previo.setFechaNacimiento(Date.valueOf(LocalDate.of(2000, 1, 1)));
			previo.setDireccion("Calle 1");
			previo.setTelefono("600000000");
			Alumno previoGuardado = alumnos.save(previo);
			ProductoAlumno pa = new ProductoAlumno();
			pa.setAlumno(previoGuardado);
			pa.setProducto(mensualidad);
			pa.setConcepto(nombreMensualidad);
			pa.setPrecio(30.0);
			pa.setCantidad(1);
			pa.setPagado(false);
			productosAlumno.save(pa);
		});
	}

	private SistemaGrados sistema(String nombre) {
		SistemaGrados sistema = new SistemaGrados();
		sistema.setNombre(nombre);
		return sistemas.save(sistema);
	}

	private void grado(SistemaGrados sistema, TipoGrado tipo, int orden) {
		Grado g = new Grado();
		g.setCodigo(tipo.name());
		g.setNombre(tipo.getNombre());
		g.setOrden(orden);
		g.setSistemaGrados(sistema);
		g.setMesesMinimosMenor(2);
		g.setMesesMinimosAdulto(3);
		grados.save(g);
	}

	private void disciplina(String codigo, SistemaGrados sistema, int edadAdulto, int orden) {
		Disciplina d = new Disciplina();
		d.setCodigo(codigo);
		d.setNombre(codigo);
		d.setSistemaGrados(sistema);
		d.setEdadAdulto(edadAdulto);
		d.setOrden(orden);
		d.setPermiteCompeticion(sistema != null);
		d.setRequiereLicencia(sistema != null);
		disciplinas.save(d);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	@Test
	void altaConDeportesInicialCreaCadaDeporteYLaRespuestaSaleDelPrincipal() {
		String json = """
				{"nombre":"MULTI","apellidos":"DEPORTE","fechaNacimiento":"2012-03-04","direccion":"Calle 2",
				 "telefono":600000001,"email":"multi-%s@example.test","autorizacionWeb":true,
				 "rolFamiliar":"NINGUNO","grupoFamiliar":"","tieneDiscapacidad":false,"aptoParaExamen":false,
				 "deportesInicial":[
				   {"deporte":"PILATES","fechaAlta":"2024-09-01","tipoTarifa":"PILATES","cuantiaTarifa":25.0,
				    "competidor":false,"tieneLicencia":false},
				   {"deporte":"TAEKWONDO","grado":"AMARILLO","fechaGrado":"2023-06-01","fechaAlta":"2021-09-01",
				    "tipoTarifa":"INFANTIL","cuantiaTarifa":27.0,"competidor":false,"tieneLicencia":false}
				 ]}""".formatted(sufijo());

		AlumnoDTO respuesta = crear(json, HttpStatus.CREATED);

		assertEquals(2, deportesDe(respuesta.getId()).size());
		// Sin principal marcado, el principal es el de alta más antigua (TAEKWONDO).
		assertEquals(Deporte.TAEKWONDO.name(), respuesta.getDeporte());
		assertEquals("AMARILLO", respuesta.getGrado());
		assertEquals(TipoTarifa.INFANTIL.name(), respuesta.getTipoTarifa());
		assertEquals(27.0, respuesta.getCuantiaTarifa());
		assertEquals(2, respuesta.getDeportes().size());
		assertEquals(27.0, precioMensualidadDe(respuesta.getId()), "mensualidad con la tarifa del deporte principal");
		assertEquals(idDeporte(respuesta.getId(), "TAEKWONDO"), deporteDeMensualidad(respuesta.getId()),
				"mensualidad enlazada al deporte principal");
	}

	@Test
	void altaConCamposDeDeporteUnicoLosTraduceAlDeportePrincipal() {
		String json = """
				{"nombre":"UNICO","apellidos":"DEPORTE","fechaNacimiento":"1990-03-04","direccion":"Calle 3",
				 "telefono":600000002,"email":"unico-%s@example.test","fechaAlta":"2024-09-01",
				 "deporte":"KICKBOXING","tipoTarifa":"KICKBOXING","tieneLicencia":true,"numeroLicencia":777,
				 "competidor":true,"peso":70.5}""".formatted(sufijo());

		AlumnoDTO respuesta = crear(json, HttpStatus.CREATED);

		List<Map<String, Object>> deportes = deportesDe(respuesta.getId());
		assertEquals(1, deportes.size());
		Map<String, Object> ad = deportes.get(0);
		assertEquals("KICKBOXING", ad.get("deporte"));
		assertEquals(Boolean.TRUE, ad.get("principal"));
		assertEquals(Boolean.TRUE, ad.get("activo"));
		assertEquals("KICKBOXING", ad.get("tipo_tarifa"));
		assertEquals(30.0, ((Number) ad.get("cuantia_tarifa")).doubleValue(), "cuantía por defecto de la tarifa");
		assertEquals("NINGUNO", ad.get("rol_familiar"));
		assertEquals(Boolean.TRUE, ad.get("tiene_licencia"));
		assertEquals(777, ((Number) ad.get("numero_licencia")).intValue());
		assertNotNull(ad.get("fecha_licencia"));
		assertEquals(Boolean.TRUE, ad.get("competidor"));
		assertEquals(70.5, ((Number) ad.get("peso")).doubleValue());
		assertNotNull(ad.get("fecha_peso"));
		assertEquals(Boolean.FALSE, ad.get("apto_para_examen"), "grado recién asignado: aún no apto");
		assertEquals("BLANCO", jdbc.queryForObject("SELECT codigo FROM grado WHERE id = ?", String.class,
				ad.get("grado_id")));

		// La respuesta mantiene la forma de siempre, con los campos planos del deporte principal.
		assertEquals(Deporte.KICKBOXING.name(), respuesta.getDeporte());
		assertEquals("BLANCO", respuesta.getGrado());
		assertEquals(TipoTarifa.KICKBOXING.name(), respuesta.getTipoTarifa());
		assertEquals(30.0, respuesta.getCuantiaTarifa());
		assertEquals(Boolean.TRUE, respuesta.getTieneLicencia());
		assertEquals(777, respuesta.getNumeroLicencia());
		assertEquals(Boolean.TRUE, respuesta.getCompetidor());
		assertEquals(30.0, precioMensualidadDe(respuesta.getId()));
		assertEquals(((Number) ad.get("id")).longValue(), deporteDeMensualidad(respuesta.getId()));
		// La licencia federativa se gestiona aparte: el alta no la cobra aunque exista el producto.
		assertEquals(List.of(nombreMensualidad), conceptosDe(respuesta.getId()));
	}

	@Test
	void altaConCamposDeDeporteUnicoRespetaCuantiaYAptitudIndicadas() {
		String json = """
				{"nombre":"UNICO","apellidos":"APTO","fechaNacimiento":"2015-03-04","direccion":"Calle 4",
				 "telefono":600000003,"email":"apto-%s@example.test","deporte":"TAEKWONDO","grado":"VERDE",
				 "tipoTarifa":"INFANTIL","cuantiaTarifa":19.5,"aptoParaExamen":true}""".formatted(sufijo());

		AlumnoDTO respuesta = crear(json, HttpStatus.CREATED);

		Map<String, Object> ad = deportesDe(respuesta.getId()).get(0);
		assertEquals(19.5, ((Number) ad.get("cuantia_tarifa")).doubleValue());
		assertEquals(Boolean.TRUE, ad.get("apto_para_examen"));
		assertEquals(Boolean.FALSE, ad.get("tiene_licencia"));
		assertEquals(Boolean.FALSE, ad.get("competidor"));
		assertEquals("VERDE", respuesta.getGrado());
		assertEquals(Boolean.TRUE, respuesta.getAptoParaExamen());
	}

	@Test
	void altaSinDeporteEsUn400YNoGuardaNada() {
		String email = "sin-deporte-" + sufijo() + "@example.test";
		String json = """
				{"nombre":"SIN","apellidos":"DEPORTE","fechaNacimiento":"2012-03-04","direccion":"Calle 5",
				 "telefono":600000004,"email":"%s","tipoTarifa":"INFANTIL","deportesInicial":[]}""".formatted(email);

		ResponseEntity<?> respuesta = llamarCrear(json);

		assertEquals(HttpStatus.BAD_REQUEST, respuesta.getStatusCode());
		assertTrue(String.valueOf(respuesta.getBody()).contains("deporte"), String.valueOf(respuesta.getBody()));
		assertFalse(TenantContext.calcularCon(org, () -> alumnos.existsByEmailIgnoreCase(email)));
	}

	@Test
	void competidorSinLicenciaPorCamposDeDeporteUnicoEsUn400() {
		String json = """
				{"nombre":"SIN","apellidos":"LICENCIA","fechaNacimiento":"2012-03-04","direccion":"Calle 6",
				 "telefono":600000005,"email":"sin-licencia-%s@example.test","deporte":"TAEKWONDO",
				 "tipoTarifa":"INFANTIL","competidor":true,"tieneLicencia":false}""".formatted(sufijo());

		assertEquals(HttpStatus.BAD_REQUEST, llamarCrear(json).getStatusCode());
	}

	@Test
	void deportesRepetidosEnElAltaSonUn400() {
		assertAltaRechazada("""
				[{"deporte":"PILATES","tipoTarifa":"PILATES","cuantiaTarifa":25.0},
				 {"deporte":"PILATES","tipoTarifa":"PILATES","cuantiaTarifa":25.0}]""", "repetido");
	}

	@Test
	void gradoNoConfiguradoEnLaEscuelaEsUn400() {
		// AZUL no está sembrado en esta escuela (solo BLANCO, AMARILLO y VERDE).
		assertAltaRechazada("""
				[{"deporte":"TAEKWONDO","grado":"AZUL","tipoTarifa":"ADULTO","cuantiaTarifa":30.0}]""",
				"no está configurado");
	}

	@Test
	void gradoConNombreNoValidoEsUn400() {
		assertAltaRechazada("""
				[{"deporte":"TAEKWONDO","grado":"MORADO","tipoTarifa":"ADULTO","cuantiaTarifa":30.0}]""",
				"Grado no válido");
	}

	@Test
	void competidorSinLicenciaEnDeportesInicialEsUn400() {
		assertAltaRechazada("""
				[{"deporte":"TAEKWONDO","grado":"BLANCO","tipoTarifa":"ADULTO","cuantiaTarifa":30.0,
				  "competidor":true,"tieneLicencia":false}]""", "licencia");
	}

	@Test
	void deporteSinIndicarEnDeportesInicialEsUn400() {
		assertAltaRechazada("""
				[{"grado":"BLANCO","tipoTarifa":"ADULTO","cuantiaTarifa":30.0}]""", "debe indicar el deporte");
		assertAltaRechazada("[null]", "debe indicar el deporte");
	}

	@Test
	void errorDelServicioDeDeportesTambienEsUn400SinDejarElAlumno() {
		// La categoría solo la comprueba AlumnoDeporteService: su IllegalArgumentException debe ser 400 y deshacer el alta.
		assertAltaRechazada("""
				[{"deporte":"TAEKWONDO","grado":"BLANCO","tipoTarifa":"ADULTO","cuantiaTarifa":30.0,
				  "categoria":"CATEGORIA INEXISTENTE"}]""", "Categoria no encontrada");
	}

	private AlumnoDTO crear(String json, HttpStatus esperado) {
		ResponseEntity<?> respuesta = llamarCrear(json);
		assertEquals(esperado, respuesta.getStatusCode(), String.valueOf(respuesta.getBody()));
		return (AlumnoDTO) respuesta.getBody();
	}

	private ResponseEntity<?> llamarCrear(String json) {
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				"admin-it", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
		return TenantContext.calcularCon(org, () -> alumnoController.crearAlumno(json, null));
	}

	private List<Map<String, Object>> deportesDe(Long alumnoId) {
		// V28: deporte y tipo_tarifa ya no son columnas; las mismas claves salen del catálogo.
		return jdbc.queryForList("""
				SELECT ad.*, d.codigo AS deporte, t.codigo AS tipo_tarifa FROM alumno_deporte ad
				JOIN disciplina d ON d.id = ad.disciplina_id LEFT JOIN tarifa t ON t.id = ad.tarifa_id
				WHERE ad.alumno_id = ? AND ad.organizacion_id = ?""", alumnoId, org);
	}

	private Double precioMensualidadDe(Long alumnoId) {
		return jdbc.queryForObject("SELECT precio FROM producto_alumno WHERE alumno_id = ? AND concepto = ?",
				Double.class, alumnoId, nombreMensualidad);
	}

	private Long deporteDeMensualidad(Long alumnoId) {
		return jdbc.queryForObject("SELECT alumno_deporte_id FROM producto_alumno WHERE alumno_id = ? AND concepto = ?",
				Long.class, alumnoId, nombreMensualidad);
	}

	private Long idDeporte(Long alumnoId, String deporte) {
		return jdbc.queryForObject("""
				SELECT ad.id FROM alumno_deporte ad JOIN disciplina d ON d.id = ad.disciplina_id
				WHERE ad.alumno_id = ? AND d.codigo = ?""", Long.class, alumnoId, deporte);
	}

	private List<String> conceptosDe(Long alumnoId) {
		return jdbc.queryForList("SELECT concepto FROM producto_alumno WHERE alumno_id = ?", String.class, alumnoId);
	}

	private void assertAltaRechazada(String deportesInicial, String fragmentoMensaje) {
		String email = "rechazo-" + sufijo() + "@example.test";
		String json = ("{\"nombre\":\"RECHAZO\",\"apellidos\":\"ALTA\",\"fechaNacimiento\":\"1990-03-04\","
				+ "\"direccion\":\"Calle 7\",\"telefono\":600000006,\"email\":\"%s\",\"deportesInicial\":%s}")
				.formatted(email, deportesInicial);

		ResponseEntity<?> respuesta = llamarCrear(json);

		assertEquals(HttpStatus.BAD_REQUEST, respuesta.getStatusCode(), String.valueOf(respuesta.getBody()));
		assertTrue(String.valueOf(respuesta.getBody()).contains(fragmentoMensaje), String.valueOf(respuesta.getBody()));
		assertFalse(TenantContext.calcularCon(org, () -> alumnos.existsByEmailIgnoreCase(email)),
				"no queda alumno guardado");
	}

	private static String sufijo() {
		return UUID.randomUUID().toString().substring(0, 8);
	}
}
