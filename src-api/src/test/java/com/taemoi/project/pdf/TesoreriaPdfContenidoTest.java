package com.taemoi.project.pdf;

import static com.taemoi.project.pdf.ContenidoPdf.comprobar;
import static com.taemoi.project.pdf.ContenidoPdf.conHoy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.taemoi.project.acceso.FiltroSedesTesoreria;
import com.taemoi.project.acceso.GestionPorSede;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.pdf.ContenidoPdf.Presentacion;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.repositories.SedeRepository;
import com.taemoi.project.services.ComportamientoLegadoService;
import com.taemoi.project.services.MarcaEscuelaPruebas;
import com.taemoi.project.services.impl.TesoreriaServiceImpl;

/**
 * Caracterización del contenido de los PDF de tesorería antes del rediseño (decisión 314): el informe de movimientos y
 * el certificado de cobros (PDFBox con AcroForm: también todos sus campos por nombre y valor). Regla en
 * {@link ContenidoPdf}.
 *
 * <p>
 * El certificado se genera con una escuela FICTICIA con todos sus datos legales (nombre legal, CIF, firmante, NIF, cargo
 * y localidad), no con Moiskimdo: el de Moiskimdo lleva escritos el nombre y el DNI reales de su presidenta y no deben
 * acabar en un fichero dorado. El texto es el mismo con otros datos (MarcaEscuelaPdfTest ya fija los de Moiskimdo).
 */
class TesoreriaPdfContenidoTest {

	private final DatosPdfPrueba datos = new DatosPdfPrueba();
	private final ProductoAlumnoRepository productoAlumnoRepository = mock(ProductoAlumnoRepository.class);
	private final AlumnoRepository alumnoRepository = mock(AlumnoRepository.class);
	private final GestionPorSede gestionPorSede = mock(GestionPorSede.class);
	private final ComportamientoLegadoService comportamientoLegado = mock(ComportamientoLegadoService.class);
	private final TesoreriaServiceImpl servicio = new TesoreriaServiceImpl();

	@BeforeEach
	void montar() {
		Organizacion escuela = MarcaEscuelaPruebas.escuela(2L, "Escuela Ficticia Sierra");
		escuela.setNombreLegal("Club Deportivo Ficticio Sierra");
		escuela.setCif("G00000000");
		escuela.setLocalidad("Villaficticia");
		escuela.setFirmanteNombre("Laura Prueba Ficticia");
		escuela.setFirmanteNif("00000000T");
		escuela.setFirmanteCargo("Secretaria");
		ReflectionTestUtils.setField(servicio, "marcaEscuela", MarcaEscuelaPruebas.real(escuela));
		ReflectionTestUtils.setField(servicio, "comportamientoLegado", comportamientoLegado);
		ReflectionTestUtils.setField(servicio, "productoAlumnoRepository", productoAlumnoRepository);
		ReflectionTestUtils.setField(servicio, "alumnoRepository", alumnoRepository);
		ReflectionTestUtils.setField(servicio, "catalogoDisciplinas", datos.catalogo.catalogo());
		ReflectionTestUtils.setField(servicio, "gestionPorSede", gestionPorSede);
		ReflectionTestUtils.setField(servicio, "sedeRepository", mock(SedeRepository.class));
		when(comportamientoLegado.activo()).thenReturn(true);
		when(gestionPorSede.filtroTesoreria(any())).thenReturn(FiltroSedesTesoreria.TODAS);
		TenantContext.set(2L);
	}

	@AfterEach
	void limpiar() {
		TenantContext.clear();
	}

	/** Lo que devuelve la consulta de tesorería: los cargos sin cancelar ({@code canceladoEn IS NULL}). */
	private void movimientos(List<ProductoAlumno> cargos) {
		List<ProductoAlumno> vigentes = cargos.stream().filter(c -> !c.estaCancelado()).toList();
		when(productoAlumnoRepository.findMovimientosTesoreriaFiltrados(any(), any(), any(), any(), any(), any(),
				any(), any(), any(), any(), any(), any())).thenAnswer(inv -> new ArrayList<>(vigentes));
	}

	@Test
	void informeDeMovimientos() throws Exception {
		List<ProductoAlumno> todos = new ArrayList<>(datos.mensualidades);
		todos.addAll(datos.reservas);
		todos.add(datos.dianaCamiseta);
		movimientos(todos);
		comprobar("tesoreria-movimientos",
				conHoy(() -> servicio.exportarMovimientosPDF(null, 2026, "TODOS", null, null, null, null)),
				Presentacion.de("Informe de Tesoreria"),
				"Filtro: año 2026, todas las disciplinas y estados. Sin fecha de generación.");
	}

	/**
	 * Antes de la decisión 315, con un cargo de DEFENSA_PERSONAL_FEMENINA (palabra sin cortes) la tabla desbordaba la
	 * página y las columnas «Importe» y parte de «Fecha pago» quedaban fuera; desde entonces (A4 apaisado) caben y el
	 * dorado del informe de arriba lleva también los importes. Sin esa disciplina ya cabían: este dorado fija también el
	 * importe de cada movimiento.
	 */
	@Test
	void informeDeMovimientosQueCabeEnLaPagina() throws Exception {
		movimientos(datos.mensualidades);
		byte[] pdf = conHoy(() -> servicio.exportarMovimientosPDF(10, 2026, "TODOS", null, null, null, null));
		comprobar("tesoreria-movimientos-taekwondo-kickboxing", pdf, Presentacion.de("Informe de Tesoreria"),
				"Filtro: octubre de 2026, todas las disciplinas y estados (solo cargos de taekwondo y kickboxing).");
		// Importes en es-ES («113,00 €»), no «113.00 EUR»: el dorado compara por palabras y no ve la coma ni el símbolo.
		String texto = String.join("\n", ContenidoPdf.lineas(pdf));
		assertTrue(texto.contains("Importe total: 113,00 €"), texto);
		assertTrue(texto.contains("28,00 €"), texto);
		assertFalse(texto.contains("EUR"), texto);
	}

	@Test
	void certificadoDeCobros() throws Exception {
		when(alumnoRepository.findById(1L)).thenReturn(Optional.of(datos.ana));
		movimientos(datos.pagados);
		comprobar("tesoreria-certificado-cobros", conHoy(() -> servicio.generarCertificadoCobrosAlumno(1L, 2026)),
				Presentacion.de("CERTIFICADO DE COBROS"),
				"Ana, ejercicio 2026: enero, octubre y la reserva de plaza (junio). Escuela ficticia con datos legales.",
				"Campo fechaGeneracion: LocalDate.now() sin Clock, fijada a " + ContenidoPdf.HOY + " en el test.");
	}
}
