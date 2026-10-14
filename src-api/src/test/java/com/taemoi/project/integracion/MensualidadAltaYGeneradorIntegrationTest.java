package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import com.taemoi.project.controllers.AlumnoController;
import com.taemoi.project.dtos.AlumnoDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.Producto;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.legado.TipoGrado;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.DisciplinaRepository;
import com.taemoi.project.repositories.GradoRepository;
import com.taemoi.project.repositories.SistemaGradosRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.repositories.ProductoRepository;
import com.taemoi.project.services.ContadorService;
import com.taemoi.project.services.ProductoAlumnoService;
import com.taemoi.project.utils.MensualidadUtils;

/**
 * La mensualidad que crea el alta frente al generador mensual (cargarMensualidadesGenerales).
 * El generador decide si ya existe por alumno + concepto con sufijo de deporte
 * ("MENSUALIDAD &lt;MES&gt; - &lt;DEPORTE&gt;"); no mira el enlace a AlumnoDeporte. El alta solo crea
 * mensualidad si en la escuela ya hay alguna con el concepto sin sufijo ("MENSUALIDAD &lt;MES&gt;").
 */
@SpringBootTest
class MensualidadAltaYGeneradorIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG_TAEMOI = 1L;

	@Autowired OrganizacionRepository organizaciones;
	@Autowired AlumnoRepository alumnos;
	@Autowired GradoRepository grados;
	@Autowired DisciplinaRepository disciplinas;
	@Autowired SistemaGradosRepository sistemas;
	@Autowired ProductoRepository productos;
	@Autowired com.taemoi.project.repositories.TarifaRepository tarifas;
	@Autowired ProductoAlumnoRepository productosAlumno;
	@Autowired ProductoAlumnoService productoAlumnoService;
	@Autowired ContadorService contadores;
	@Autowired AlumnoController alumnoController;
	@Autowired TransactionTemplate tx;
	@Autowired JdbcTemplate jdbc;

	private final String mes = mesAno(LocalDate.now());
	private final String mesSiguiente = mesAno(LocalDate.now().plusMonths(1));

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		assertNull(TenantContext.get());
	}

	@Test
	void flujoNormal_generadorDelMesAltaYRegeneracion_unaMensualidadPorAlumnoYDeporte() {
		long org = escuela("it-mens-normal");
		long previo = TenantContext.calcularCon(org, () -> alumnoConDeporte("PREVIO"));
		generar(org, mes);
		assertEquals(List.of(mensualidad(mes, "TAEKWONDO")), conceptos(previo));

		long unico = altaCamposDeporteUnico(org);
		long multi = altaDeportesInicial(org);
		// Con la mensualidad del mes ya generada (con sufijo), el alta no crea ninguna: lo hace el generador.
		assertEquals(List.of(), conceptos(unico));
		assertEquals(List.of(), conceptos(multi));

		generar(org, mes);
		generar(org, mes); // repetir no duplica

		assertCargo(unico, mes, "KICKBOXING", 30.0);
		assertCargo(multi, mes, "TAEKWONDO", 27.0);
		assertCargo(multi, mes, "PILATES", 25.0);
		assertEquals(1, conceptos(unico).size());
		assertEquals(2, conceptos(multi).size());
		assertEquals(1, conceptos(previo).size());

		generar(org, mesSiguiente);
		assertCargo(unico, mesSiguiente, "KICKBOXING", 30.0);
		assertCargo(multi, mesSiguiente, "TAEKWONDO", 27.0);
		assertCargo(multi, mesSiguiente, "PILATES", 25.0);
		assertEquals(2, conceptos(unico).size());
		assertEquals(4, conceptos(multi).size());
	}

	/**
	 * Caracteriza el comportamiento previo (idéntico antes y después de quitar las columnas): si en la
	 * escuela hay una mensualidad del mes con el concepto sin sufijo (datos heredados), el alta crea otra
	 * igual sin sufijo y el generador, que solo reconoce el concepto con sufijo, añade la suya. El enlace
	 * al AlumnoDeporte no interviene en esa decisión. Lo único nuevo es que la del alta lleva precio.
	 */
	@Test
	void conMensualidadHeredadaSinSufijo_elAltaYElGeneradorCreanCadaUnoLaSuya() {
		long org = escuela("it-mens-heredada");
		TenantContext.ejecutarCon(org, () -> {
			Alumno previo = alumnos.findById(alumnoConDeporte("PREVIO")).orElseThrow();
			ProductoAlumno pa = new ProductoAlumno();
			pa.setAlumno(previo);
			pa.setProducto(productos.findFirstByConcepto("MENSUALIDAD").orElseThrow());
			pa.setConcepto(MensualidadUtils.formatearNombreMensualidad(mes));
			pa.setPrecio(30.0);
			pa.setCantidad(1);
			pa.setPagado(false);
			productosAlumno.save(pa);
		});

		long unico = altaCamposDeporteUnico(org);
		long multi = altaDeportesInicial(org);
		String sinSufijo = MensualidadUtils.formatearNombreMensualidad(mes);
		assertEquals(30.0, precio(unico, sinSufijo));
		assertEquals(27.0, precio(multi, sinSufijo), "precio del deporte principal (antes: NULL)");
		assertEquals(idDeporte(multi, "TAEKWONDO"), jdbc.queryForObject(
				"SELECT alumno_deporte_id FROM producto_alumno WHERE alumno_id = ? AND concepto = ?", Long.class,
				multi, sinSufijo));

		generar(org, mes);

		assertEquals(List.of(sinSufijo, mensualidad(mes, "KICKBOXING")), conceptos(unico));
		assertEquals(List.of(sinSufijo, mensualidad(mes, "PILATES"), mensualidad(mes, "TAEKWONDO")),
				conceptos(multi));
	}

	private void assertCargo(long alumnoId, String mesAno, String deporte, double precio) {
		String concepto = mensualidad(mesAno, deporte);
		List<Map<String, Object>> filas = jdbc.queryForList(
				"SELECT precio, alumno_deporte_id FROM producto_alumno WHERE alumno_id = ? AND concepto = ?",
				alumnoId, concepto);
		assertEquals(1, filas.size(), concepto);
		assertEquals(precio, ((Number) filas.get(0).get("precio")).doubleValue(), concepto);
		assertEquals(idDeporte(alumnoId, deporte), ((Number) filas.get(0).get("alumno_deporte_id")).longValue());
	}

	private long altaCamposDeporteUnico(long org) {
		return crear(org, """
				{"nombre":"UNICO","apellidos":"MENSUALIDAD","fechaNacimiento":"1990-03-04","direccion":"Calle 1",
				 "telefono":600000001,"email":"unico-%s@example.test","deporte":"KICKBOXING","tipoTarifa":"KICKBOXING"}
				""".formatted(sufijo()));
	}

	private long altaDeportesInicial(long org) {
		return crear(org, """
				{"nombre":"MULTI","apellidos":"MENSUALIDAD","fechaNacimiento":"2012-03-04","direccion":"Calle 2",
				 "telefono":600000002,"email":"multi-%s@example.test","rolFamiliar":"NINGUNO","grupoFamiliar":"",
				 "deportesInicial":[
				   {"deporte":"PILATES","fechaAlta":"2024-09-01","tipoTarifa":"PILATES","cuantiaTarifa":25.0},
				   {"deporte":"TAEKWONDO","grado":"BLANCO","fechaAlta":"2021-09-01","tipoTarifa":"INFANTIL",
				    "cuantiaTarifa":27.0}]}""".formatted(sufijo()));
	}

	private long crear(long org, String json) {
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				"admin-it", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
		ResponseEntity<?> respuesta = TenantContext.calcularCon(org, () -> alumnoController.crearAlumno(json, null));
		assertEquals(HttpStatus.CREATED, respuesta.getStatusCode(), String.valueOf(respuesta.getBody()));
		return ((AlumnoDTO) respuesta.getBody()).getId();
	}

	private void generar(long org, String mesAno) {
		TenantContext.ejecutarCon(org, () -> tx.executeWithoutResult(
				s -> productoAlumnoService.cargarMensualidadesGenerales(mesAno, null)));
	}

	private long escuela(String prefijo) {
		Organizacion o = new Organizacion();
		o.setSlug(prefijo + "-" + UUID.randomUUID().toString().substring(0, 8));
		o.setNombre("Escuela " + prefijo);
		long org = TenantContext.calcularCon(ORG_TAEMOI, () -> organizaciones.save(o).getId());
		assertNotEquals(ORG_TAEMOI, org);
		TenantContext.ejecutarCon(org, () -> {
			// Catálogo propio de la escuela (V26): grados por disciplina.
			DisciplinasDePrueba.conGrados(disciplinas, sistemas, grados, "TAEKWONDO", 14, TipoGrado.BLANCO);
			com.taemoi.project.entities.Disciplina kb = DisciplinasDePrueba.conGrados(disciplinas, sistemas, grados,
					"KICKBOXING", 15, TipoGrado.BLANCO);
			DisciplinasDePrueba.asegurar(disciplinas, "PILATES");
			// Tarifa propia de la escuela (V27): la cuantía por defecto del alta sale de ella.
			DisciplinasDePrueba.tarifa(tarifas, "KICKBOXING", "30.00", kb);
			// V28: las demás tarifas de estas altas también son de la escuela (un código que la escuela no tiene es
			// un 400; antes se guardaba en tipo_tarifa sin tarifa_id).
			DisciplinasDePrueba.tarifa(tarifas, "PILATES", "30.00", disciplinas.findByCodigo("PILATES").orElseThrow());
			DisciplinasDePrueba.tarifa(tarifas, "INFANTIL", "28.00", null);
			productos.save(new Producto("MENSUALIDAD", 30.0, null));
		});
		return org;
	}

	/** Alumno ya existente con un deporte (TAEKWONDO, 30 €), como los de producción. */
	private long alumnoConDeporte(String nombre) {
		Alumno a = new Alumno();
		a.setNombre(nombre);
		a.setApellidos("MENSUALIDAD");
		a.setNumeroExpediente(contadores.siguienteNumeroExpediente());
		a.setFechaNacimiento(Date.valueOf(LocalDate.of(2000, 1, 1)));
		a.setDireccion("Calle 0");
		a.setTelefono("600000000");
		long id = alumnos.save(a).getId();
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal,
				  tarifa_id, cuantia_tarifa, tiene_licencia, organizacion_id, fecha_alta, fecha_alta_inicial, disciplina_id)
				VALUES (1, 0, ?, 0, 1, (SELECT t.id FROM tarifa t WHERE t.organizacion_id = ? AND t.codigo = 'ADULTO'),
				  30, 0, ?, '2020-09-01', '2020-09-01', ?)
				""", id, TenantContext.get(), TenantContext.get(),
				DisciplinasDePrueba.asegurar(disciplinas, "TAEKWONDO").getId());
		return id;
	}

	private List<String> conceptos(long alumnoId) {
		return jdbc.queryForList("SELECT concepto FROM producto_alumno WHERE alumno_id = ? ORDER BY concepto",
				String.class, alumnoId);
	}

	private Double precio(long alumnoId, String concepto) {
		return jdbc.queryForObject("SELECT precio FROM producto_alumno WHERE alumno_id = ? AND concepto = ?",
				Double.class, alumnoId, concepto);
	}

	private long idDeporte(long alumnoId, String deporte) {
		return jdbc.queryForObject("""
				SELECT ad.id FROM alumno_deporte ad JOIN disciplina d ON d.id = ad.disciplina_id
				WHERE ad.alumno_id = ? AND d.codigo = ?""", Long.class, alumnoId, deporte);
	}

	private static String mensualidad(String mesAno, String deporte) {
		return MensualidadUtils.formatearNombreMensualidad(mesAno) + " - " + deporte;
	}

	private static String mesAno(LocalDate fecha) {
		return fecha.getYear() + "-" + "%02d".formatted(fecha.getMonthValue());
	}

	private static String sufijo() {
		return UUID.randomUUID().toString().substring(0, 8);
	}
}
