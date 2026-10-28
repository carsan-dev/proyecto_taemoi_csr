package com.taemoi.project.pdf;

import static com.taemoi.project.pdf.ContenidoPdf.comprobar;
import static com.taemoi.project.pdf.ContenidoPdf.conHoy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.taemoi.project.config.ValidacionRelojConfig;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Cobro;
import com.taemoi.project.entities.EstadoCobro;
import com.taemoi.project.entities.MetodoPago;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.Sede;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.pdf.ContenidoPdf.Presentacion;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.CobroRepository;
import com.taemoi.project.repositories.ImagenRepository;
import com.taemoi.project.repositories.MovimientoCajaRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.repositories.SedeRepository;
import com.taemoi.project.services.ImagenService;
import com.taemoi.project.services.MarcaEscuelaPruebas;
import com.taemoi.project.services.impl.ReciboPagoServiceImpl;

/**
 * Pieza 279: contenido del recibo de pago contra su dorado {@code pdf-contenido/recibo-pago.txt} (regla en
 * {@link ContenidoPdf}). PDF nuevo: el dorado se regeneró con la tarjeta A5 apaisada (los mismos datos que la
 * primera versión, en el orden de la tarjeta). Escuela ficticia con todos sus datos; alumna ficticia con DNI y correo, que NO deben salir. Emisión: reloj fijo el {@link ContenidoPdf#HOY}.
 */
class ReciboPagoContenidoTest {

	private final ProductoAlumnoRepository cargos = mock(ProductoAlumnoRepository.class);
	private final CobroRepository cobros = mock(CobroRepository.class);
	private final AlumnoRepository alumnos = mock(AlumnoRepository.class);
	private final SedeRepository sedes = mock(SedeRepository.class);
	private final OrganizacionRepository organizaciones = mock(OrganizacionRepository.class);
	private ReciboPagoServiceImpl servicio;
	private Cobro cobro;

	@BeforeEach
	void montar() {
		Organizacion escuela = MarcaEscuelaPruebas.escuela(2L, "Escuela Ficticia Sierra");
		escuela.setNombreLegal("Club Deportivo Ficticio Sierra");
		escuela.setCif("G00000000");
		escuela.setDireccion("Calle Inventada 12");
		escuela.setCodigoPostal("41999");
		escuela.setLocalidad("Villaficticia");
		escuela.setProvincia("Sevilla");
		escuela.setEmailContacto("contacto@escuela-ficticia.example");
		escuela.setTelefono("955000000");
		Clock reloj = Clock.fixed(ContenidoPdf.HOY.atTime(10, 0).atZone(ValidacionRelojConfig.ZONA).toInstant(),
				ValidacionRelojConfig.ZONA);
		servicio = new ReciboPagoServiceImpl(cargos, cobros, mock(MovimientoCajaRepository.class), alumnos, sedes, organizaciones,
				MarcaEscuelaPruebas.real(organizaciones, mock(ImagenRepository.class), mock(ImagenService.class),
						escuela),
				reloj);

		Alumno ana = new Alumno();
		ana.setNombre("Ana");
		ana.setApellidos("Prueba Uno");
		ana.setNif("00000000T");
		ana.setEmail("ana.prueba@example.test");
		Sede sede = new Sede();
		sede.setNombre("Sede Ficticia Norte");
		cobro = new Cobro();
		ReflectionTestUtils.setField(cobro, "id", 123L);
		cobro.setProductoAlumnoId(50L);
		cobro.setAlumnoId(1L);
		cobro.setSedeId(3L);
		cobro.setConcepto("MENSUALIDAD OCTUBRE 2026 - TAEKWONDO");
		cobro.setImporte(new BigDecimal("28.00"));
		cobro.setMetodo(MetodoPago.TRANSFERENCIA);
		cobro.setFechaCobro(Date.from(LocalDateTime.parse("2026-10-02T18:30").atZone(ZoneId.systemDefault())
				.toInstant()));
		cobro.setEstado(EstadoCobro.ACTIVO);
		when(cargos.existsById(50L)).thenReturn(true);
		when(cobros.findFirstByProductoAlumnoIdAndEstado(50L, EstadoCobro.ACTIVO)).thenReturn(Optional.of(cobro));
		when(alumnos.findById(1L)).thenReturn(Optional.of(ana));
		when(sedes.findById(3L)).thenReturn(Optional.of(sede));
		TenantContext.set(2L);
	}

	@AfterEach
	void limpiar() {
		TenantContext.clear();
	}

	@Test
	void reciboDePago() throws Exception {
		comprobar("recibo-pago", conHoy(() -> servicio.generar(50L).pdf()),
				Presentacion.de("Escuela Ficticia Sierra", "Recibo"),
				"Pieza 279: cobro 123 de Ana (transferencia, 28 €, sede Norte) del 02/10/2026. Escuela ficticia.",
				"Emisión: Clock fijo el " + ContenidoPdf.HOY + " (presentación). Ni DNI ni correo de la alumna.");
	}

	@Test
	void tarjetaA5Apaisada_unaSolaPagina_sinContadorDePaginas() throws Exception {
		try (PDDocument pdf = PDDocument.load(conHoy(() -> servicio.generar(50L).pdf()))) {
			assertEquals(1, pdf.getNumberOfPages());
			PDRectangle pagina = pdf.getPage(0).getMediaBox();
			assertEquals(PDRectangle.A5.getHeight(), pagina.getWidth(), 1f, "ancho: el alto de A5 (210 mm)");
			assertEquals(PDRectangle.A5.getWidth(), pagina.getHeight(), 1f, "alto: el ancho de A5 (148 mm)");
		}
		assertFalse(String.join("\n", ContenidoPdf.lineas(conHoy(() -> servicio.generar(50L).pdf()))).contains("Página"),
				"una sola página: sin «Página 1 de 1»");
	}

	@Test
	void conceptoYNombreLargos_partenLineaYCabenEnUnaPagina() throws Exception {
		String concepto = "MENSUALIDAD OCTUBRE 2026 - TAEKWONDO INFANTIL GRUPO DE TARDE CON SUPLEMENTO DE COMPETICIÓN"
				+ " Y SEGURO DEPORTIVO ANUAL INCLUIDO";
		assertTrue(concepto.length() >= 120, "concepto de ~120 caracteres");
		cobro.setConcepto(concepto);
		Alumno larga = new Alumno();
		larga.setNombre("María de los Ángeles Inmaculada");
		larga.setApellidos("Fernández Villaverde de la Concepción Prueba");
		when(alumnos.findById(1L)).thenReturn(Optional.of(larga));
		cobro.setImporte(new BigDecimal("1234.56"));
		byte[] bytes = conHoy(() -> servicio.generar(50L).pdf());
		try (PDDocument pdf = PDDocument.load(bytes)) {
			assertEquals(1, pdf.getNumberOfPages());
		}
		String texto = String.join(" ", ContenidoPdf.lineas(bytes)).replaceAll("\\s+", "");
		assertTrue(texto.contains(concepto.replaceAll("\\s+", "")), texto);
		// El nombre parte línea junto a «Importe» (otra columna, misma altura): se busca cada palabra.
		for (String palabra : (larga.getNombre() + " " + larga.getApellidos()).split(" ")) {
			assertTrue(texto.contains(palabra), palabra + " en " + texto);
		}
		assertTrue(texto.contains("1.234,56€"), texto);
		assertTrue(texto.contains("Estedocumentojustificaelpago"), texto);
	}
}
