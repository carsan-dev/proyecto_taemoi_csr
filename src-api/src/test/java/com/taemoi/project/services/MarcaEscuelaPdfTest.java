package com.taemoi.project.services;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import com.taemoi.project.catalogo.CatalogoTaemoiEnMemoria;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.Grupo;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.PlantillaPreinscripcion;
import com.taemoi.project.entities.Preinscripcion;
import com.taemoi.project.entities.Turno;
import com.taemoi.project.legado.Deporte;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoDeporteRepository;
import com.taemoi.project.repositories.ImagenRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.TurnoRepository;
import com.taemoi.project.services.impl.PDFServiceImpl;
import com.taemoi.project.services.impl.TesoreriaServiceImpl;

/**
 * Pieza E7-7a (decisión 211): cabeceras de los PDF por escuela, en sus cuatro caminos (cabecera común de los informes,
 * hoja de asistencia, solicitud de preinscripción firmada y certificado de cobros). {@code moiskimdoPdfsIdenticos} se
 * fijó y pasó ANTES del cambio: el texto de cabecera y el logo (píxel a píxel) que lleva hoy cada camino. Desde la
 * E7-8 (decisión 213), Moiskimdo con un logo subido lo lleva en los cuatro caminos, con sus textos de siempre.
 */
class MarcaEscuelaPdfTest {

	private final AlumnoDeporteRepository alumnoDeportes = mock(AlumnoDeporteRepository.class);
	private final TurnoRepository turnos = mock(TurnoRepository.class);
	private final OrganizacionRepository organizaciones = mock(OrganizacionRepository.class);
	private final ImagenRepository imagenesRepo = mock(ImagenRepository.class);
	private final ImagenService imagenService = mock(ImagenService.class);

	@TempDir
	Path carpeta;

	@AfterEach
	void sinEscuelaEnElHilo() {
		assertNull(TenantContext.get(), "El test dejó una escuela fijada en el hilo");
	}

	// ------------------------------------------------------------------ Moiskimdo: idénticos a hoy

	@Test
	void moiskimdoPdfsIdenticos() throws Exception {
		// Moiskimdo con un logo apuntado que no existe (o no es suyo): no se usa, sale lo de siempre (E7-8).
		Organizacion moiskimdo = MarcaEscuelaPruebas.moiskimdo();
		moiskimdo.setLogoImagenId(77L);
		comprobarMoiskimdoDeSiempre(
				generar(1L, MarcaEscuelaPruebas.real(organizaciones, imagenesRepo, imagenService, moiskimdo)));
	}

	// ------------------------------------------------------------------ E7-8 (decisión 213): logo de Moiskimdo

	@Test
	void moiskimdoSinLogoSubidoIdentico() throws Exception {
		comprobarMoiskimdoDeSiempre(generar(1L, MarcaEscuelaPruebas.real(organizaciones, imagenesRepo, imagenService,
				MarcaEscuelaPruebas.moiskimdo())));
	}

	@Test
	void moiskimdoConLogoSubidoLoUsa() throws Exception {
		BufferedImage logo = logoFicticio(Color.MAGENTA);
		Organizacion moiskimdo = MarcaEscuelaPruebas.moiskimdo();
		moiskimdo.setLogoImagenId(601L);
		escribirLogo(601L, 1L, logo);
		Pdfs pdfs = generar(1L, MarcaEscuelaPruebas.real(organizaciones, imagenesRepo, imagenService, moiskimdo));
		comprobarMoiskimdoConLogo(pdfs, pixeles(logo));
	}

	@Test
	void moiskimdoConLogoDeOtraEscuelaSaleElDeSiempre() throws Exception {
		Organizacion moiskimdo = MarcaEscuelaPruebas.moiskimdo();
		moiskimdo.setLogoImagenId(601L);
		escribirLogo(601L, 2L, logoFicticio(Color.MAGENTA)); // la imagen 601 es de la escuela 2
		comprobarMoiskimdoDeSiempre(
				generar(1L, MarcaEscuelaPruebas.real(organizaciones, imagenesRepo, imagenService, moiskimdo)));
	}

	@Test
	void moiskimdoConLogoSinFicheroSaleElDeSiempre() throws Exception {
		Organizacion moiskimdo = MarcaEscuelaPruebas.moiskimdo();
		moiskimdo.setLogoImagenId(601L);
		escribirLogo(601L, 1L, logoFicticio(Color.MAGENTA));
		Files.delete(carpeta.resolve("logo-601.png")); // imagen de Moiskimdo cuyo fichero ya no existe
		comprobarMoiskimdoDeSiempre(
				generar(1L, MarcaEscuelaPruebas.real(organizaciones, imagenesRepo, imagenService, moiskimdo)));
	}

	@Test
	void quitarLogoMoiskimdoVuelveAlDeSiempre() throws Exception {
		BufferedImage logo = logoFicticio(Color.MAGENTA);
		Organizacion moiskimdo = MarcaEscuelaPruebas.moiskimdo();
		moiskimdo.setLogoImagenId(601L);
		escribirLogo(601L, 1L, logo);
		MarcaEscuelaService marca = MarcaEscuelaPruebas.real(organizaciones, imagenesRepo, imagenService, moiskimdo);
		comprobarMoiskimdoConLogo(generar(1L, marca), pixeles(logo));

		// Quitado (como LogoEscuelaService#quitar): sin invalidar sigue el de la caché; tras invalidar, el de siempre.
		moiskimdo.setLogoImagenId(null);
		assertArrayEquals(pixeles(logo), pixeles(unicaImagen(generar(1L, marca).informe())));
		marca.invalidar(1L);
		comprobarMoiskimdoDeSiempre(generar(1L, marca));
	}

	/** Moiskimdo con su logo subido: ese logo en los cuatro caminos; los textos y datos legales, los de siempre. */
	private static void comprobarMoiskimdoConLogo(Pdfs pdfs, int[] esperado) throws IOException {
		List<String> lineas = lineas(pdfs.informe());
		assertEquals(List.of("MOI'S KIM DO TAEKWONDO", "Informe de Alumnas de D.P.F"), lineas.subList(2, 4),
				String.join("|", lineas));
		assertArrayEquals(esperado, pixeles(unicaImagen(pdfs.informe())));
		List<String> lineasAsistencia = lineas(pdfs.asistencia());
		assertEquals(List.of("CLUB MOI'S KIM DO", "Tae Kwon Do"), lineasAsistencia.subList(0, 2),
				String.join("|", lineasAsistencia));
		assertArrayEquals(esperado, pixeles(unicaImagen(pdfs.asistencia())));
		String texto = texto(pdfs.solicitud());
		assertTrue(texto.contains("Escuela Moi Kim Do · Calle Ficticia · Temporada 2026-2027"), texto);
		List<BufferedImage> logos = sinFirma(imagenes(pdfs.solicitud()));
		assertEquals(1, logos.size(), "logo (además de la firma)");
		assertArrayEquals(esperado, pixeles(logos.get(0)));
		String textoCertificado = texto(pdfs.certificado());
		assertTrue(textoCertificado.contains("Club Deportivo Moi's Kim do Taekwondo con C.I.F.: G90341173"),
				textoCertificado);
		List<String> lineasCertificado = lineas(pdfs.certificado());
		assertEquals(List.of("PRESIDENTA", "C.D. MOI'S KIMDO TAEKWONDO", "C.I.F.: G90341173"),
				lineasCertificado.subList(lineasCertificado.size() - 3, lineasCertificado.size()),
				String.join("|", lineasCertificado));
		assertTrue(campo(pdfs.certificado(), "fechaGeneracion").startsWith("En Umbrete, a "));
		assertArrayEquals(esperado, pixeles(unicaImagen(pdfs.certificado())));
	}

	/** Lo que lleva hoy cada camino de Moiskimdo: texto de cabecera y logo de classpath píxel a píxel. */
	private static void comprobarMoiskimdoDeSiempre(Pdfs pdfs) throws Exception {
		int[] logoEscuela = pixeles(logoEscuelaHoy());

		// 1. Cabecera común (generarCabeceraConLogo, 12+ informes): nombre fijo y logo_escuela.svg a 300x300.
		List<String> lineas = lineas(pdfs.informe());
		// Título del pie de página, fecha y página; después la cabecera (club-name en mayúsculas por CSS) y el título.
		assertEquals(List.of("MOI'S KIM DO TAEKWONDO", "Informe de Alumnas de D.P.F"), lineas.subList(2, 4),
				String.join("|", lineas));
		assertArrayEquals(logoEscuela, pixeles(unicaImagen(pdfs.informe())));

		// 2. Hoja de asistencia (paginaAsistencia): «CLUB MOI'S KIM DO / Tae Kwon Do» y el mismo logo.
		List<String> lineasAsistencia = lineas(pdfs.asistencia());
		assertEquals(List.of("CLUB MOI'S KIM DO", "Tae Kwon Do"), lineasAsistencia.subList(0, 2),
				String.join("|", lineasAsistencia));
		assertArrayEquals(logoEscuela, pixeles(unicaImagen(pdfs.asistencia())));

		// 3. Solicitud de preinscripción: logo_tkd_kick.jpeg y «Escuela Moi Kim Do» si la plantilla no trae cabecera.
		String texto = texto(pdfs.solicitud());
		assertTrue(texto.contains("Escuela Moi Kim Do · Calle Ficticia · Temporada 2026-2027"), texto);
		List<BufferedImage> logos = sinFirma(imagenes(pdfs.solicitud()));
		assertEquals(1, logos.size(), "logo (además de la firma)");
		assertArrayEquals(pixeles(ImageIO.read(clase("static/logo_tkd_kick.jpeg"))), pixeles(logos.get(0)));

		// 4. Certificado de cobros (tesorería): Logo_nuevo_sin_vectorizar.jpeg, datos legales y «En Umbrete».
		String textoCertificado = texto(pdfs.certificado());
		assertTrue(textoCertificado.contains("Ejercicio 2026 - Club Deportivo Moi's Kim do Taekwondo"), textoCertificado);
		assertTrue(textoCertificado.contains("Club Deportivo Moi's Kim do Taekwondo con C.I.F.: G90341173"),
				textoCertificado);
		assertTrue(textoCertificado.contains(", en calidad de Presidenta del"), textoCertificado);
		List<String> lineasCertificado = lineas(pdfs.certificado());
		assertEquals(List.of("PRESIDENTA", "C.D. MOI'S KIMDO TAEKWONDO", "C.I.F.: G90341173"),
				lineasCertificado.subList(lineasCertificado.size() - 3, lineasCertificado.size()),
				String.join("|", lineasCertificado));
		assertTrue(campo(pdfs.certificado(), "fechaGeneracion").startsWith("En Umbrete, a "));
		assertArrayEquals(pixeles(ImageIO.read(clase("static/Logo_nuevo_sin_vectorizar.jpeg"))),
				pixeles(unicaImagen(pdfs.certificado())));
	}

	// ------------------------------------------------------------------ otras escuelas

	@Test
	void escuelaSinLogoSoloNombre_sinRastroDeMoiskimdo() throws Exception {
		Pdfs pdfs = generar(2L, MarcaEscuelaPruebas.real(organizaciones, imagenesRepo, imagenService,
				MarcaEscuelaPruebas.moiskimdo(), MarcaEscuelaPruebas.escuela(2L, "Escuela Ficticia Norte & Sur")));

		List<String> lineas = lineas(pdfs.informe());
		assertEquals(List.of("ESCUELA FICTICIA NORTE & SUR", "Informe de Alumnas de D.P.F"), lineas.subList(2, 4),
				String.join("|", lineas));
		List<String> lineasAsistencia = lineas(pdfs.asistencia());
		assertEquals("ESCUELA FICTICIA NORTE & SUR", lineasAsistencia.get(0));
		assertTrue(lineasAsistencia.get(1).startsWith("LISTADO DE ASISTENCIA"), String.join("|", lineasAsistencia));
		assertTrue(texto(pdfs.solicitud()).contains("Escuela Ficticia Norte & Sur · Calle Ficticia · Temporada"));
		String certificado = texto(pdfs.certificado());
		assertTrue(certificado.contains("Ejercicio 2026 - Escuela Ficticia Norte & Sur"), certificado);
		assertTrue(certificado.contains("________, CON DNI ________, en calidad de ________ del"), certificado);
		assertTrue(campo(pdfs.certificado(), "fechaGeneracion").startsWith("A "));

		for (byte[] documento : pdfs.todos()) {
			sinRastroDeMoiskimdo(texto(documento));
		}
		assertEquals(List.of(), imagenes(pdfs.informe()));
		assertEquals(List.of(), imagenes(pdfs.asistencia()));
		assertEquals(List.of(), sinFirma(imagenes(pdfs.solicitud())));
		assertEquals(List.of(), imagenes(pdfs.certificado()));
	}

	@Test
	void escuelaConLogoLoIncrusta() throws Exception {
		BufferedImage logo = logoFicticio(Color.RED);
		Organizacion escuela = escuelaConLogo(2L, "Escuela Ficticia Roja", 501L, logo);
		escuela.setNombreLegal("Club Ficticio Rojo");
		escuela.setCif("A00000000");
		escuela.setLocalidad("Villaficticia");
		Pdfs pdfs = generar(2L, MarcaEscuelaPruebas.real(organizaciones, imagenesRepo, imagenService,
				MarcaEscuelaPruebas.moiskimdo(), escuela));

		int[] esperado = pixeles(logo);
		assertArrayEquals(esperado, pixeles(unicaImagen(pdfs.informe())));
		assertArrayEquals(esperado, pixeles(unicaImagen(pdfs.asistencia())));
		List<BufferedImage> logos = sinFirma(imagenes(pdfs.solicitud()));
		assertEquals(1, logos.size());
		assertArrayEquals(esperado, pixeles(logos.get(0)));
		assertArrayEquals(esperado, pixeles(unicaImagen(pdfs.certificado())));
		String certificado = texto(pdfs.certificado());
		assertTrue(certificado.contains("Club Ficticio Rojo con C.I.F.: A00000000"), certificado);
		assertTrue(campo(pdfs.certificado(), "fechaGeneracion").startsWith("En Villaficticia, a "));
		for (byte[] documento : pdfs.todos()) {
			sinRastroDeMoiskimdo(texto(documento));
		}
	}

	@Test
	void cachePorEscuela() throws Exception {
		BufferedImage roja = logoFicticio(Color.RED);
		BufferedImage azul = logoFicticio(Color.BLUE);
		Organizacion dos = escuelaConLogo(2L, "Escuela Ficticia Roja", 501L, roja);
		Organizacion tres = escuelaConLogo(3L, "Escuela Ficticia Azul", 502L, azul);
		MarcaEscuelaService marca = MarcaEscuelaPruebas.real(organizaciones, imagenesRepo, imagenService,
				MarcaEscuelaPruebas.moiskimdo(), dos, tres);

		Pdfs deLaDos = generar(2L, marca);
		Pdfs deLaTres = generar(3L, marca);
		Pdfs deMoiskimdo = generar(1L, marca);

		assertArrayEquals(pixeles(roja), pixeles(unicaImagen(deLaDos.informe())));
		assertArrayEquals(pixeles(azul), pixeles(unicaImagen(deLaTres.informe())));
		assertArrayEquals(pixeles(azul), pixeles(unicaImagen(deLaTres.asistencia())));
		assertArrayEquals(pixeles(logoEscuelaHoy()), pixeles(unicaImagen(deMoiskimdo.informe())));
		assertEquals("ESCUELA FICTICIA AZUL", lineas(deLaTres.asistencia()).get(0));
		// Una lectura por escuela para sus cuatro documentos.
		verify(organizaciones, times(1)).findById(2L);
		verify(organizaciones, times(1)).findById(3L);

		// Logo nuevo de la 2: tras invalidar, el siguiente documento lo lleva.
		BufferedImage verde = logoFicticio(Color.GREEN);
		escribirLogo(501L, 2L, verde);
		marca.invalidar(2L);
		assertArrayEquals(pixeles(verde), pixeles(unicaImagen(generar(2L, marca).informe())));
	}

	// ------------------------------------------------------------------ generación

	/** Los cuatro caminos de cabecera de una escuela, generados con ella como escuela activa. */
	private record Pdfs(byte[] informe, byte[] asistencia, byte[] solicitud, byte[] certificado) {
		List<byte[]> todos() {
			return List.of(informe, asistencia, solicitud, certificado);
		}
	}

	private Pdfs generar(long escuela, MarcaEscuelaService marca) throws Exception {
		when(alumnoDeportes.findActivosByDeporteWithAlumno(Deporte.DEFENSA_PERSONAL_FEMENINA.name()))
				.thenReturn(List.of());
		TenantContext.set(escuela);
		try {
			return new Pdfs(pdf(marca).generarInformeAlumnasDpf(true), asistencia(pdf(marca)),
					pdf(marca).generarPreinscripcionFirmada(preinscripcion()), certificado(tesoreria(marca)));
		} finally {
			TenantContext.clear();
		}
	}

	private PDFServiceImpl pdf(MarcaEscuelaService marca) {
		PDFServiceImpl servicio = new PDFServiceImpl();
		ReflectionTestUtils.setField(servicio, "alumnoDeporteRepository", alumnoDeportes);
		ReflectionTestUtils.setField(servicio, "turnoRepository", turnos);
		ReflectionTestUtils.setField(servicio, "marcaEscuela", marca);
		return servicio;
	}

	private static TesoreriaServiceImpl tesoreria(MarcaEscuelaService marca) {
		TesoreriaServiceImpl servicio = new TesoreriaServiceImpl();
		ReflectionTestUtils.setField(servicio, "marcaEscuela", marca);
		return servicio;
	}

	private byte[] asistencia(PDFServiceImpl servicio) throws IOException {
		Alumno alumno = new Alumno();
		alumno.setId(5L);
		alumno.setNombre("Ana");
		alumno.setApellidos("Prueba");
		alumno.setNumeroExpediente(1);
		alumno.setActivo(true);
		alumno.setFechaNacimiento(java.sql.Date.valueOf(LocalDate.of(2012, 3, 3)));
		AlumnoDeporte inscripcion = new AlumnoDeporte();
		inscripcion.setAlumno(alumno);
		inscripcion.setDisciplina(CatalogoTaemoiEnMemoria.disciplinaTaemoi(Deporte.TAEKWONDO.name()));
		inscripcion.setActivo(true);
		Grupo grupo = new Grupo();
		grupo.setId(3L);
		grupo.setNombre("Grupo prueba");
		grupo.setDisciplina(CatalogoTaemoiEnMemoria.disciplinaTaemoi(Deporte.TAEKWONDO.name()));
		grupo.setAlumnos(List.of(alumno));
		Turno turno = new Turno();
		turno.setId(9L);
		turno.setDiaSemana("Lunes");
		turno.setHoraInicio("17:00");
		turno.setHoraFin("18:00");
		turno.setGrupo(grupo);
		turno.setAlumnos(new ArrayList<>(List.of(alumno)));
		when(turnos.findAllById(anyList())).thenReturn(List.of(turno));
		when(alumnoDeportes.findActivosByDeporteIn(List.of(Deporte.TAEKWONDO.name())))
				.thenReturn(new ArrayList<>(List.of(inscripcion)));
		return servicio.generarListadoAsistenciaPorTurnos(2026, 9, List.of(9L));
	}

	static Preinscripcion preinscripcion() throws IOException {
		Preinscripcion p = new Preinscripcion();
		p.setReferencia("PRE-2026-MARCA");
		p.setTemporada("2026-2027");
		p.setDisciplina(CatalogoTaemoiEnMemoria.disciplinaTaemoi(Deporte.TAEKWONDO.name()));
		p.setNombre("María");
		p.setApellidos("Prueba Ficticia");
		p.setDni("12345678Z");
		p.setFechaNacimiento(LocalDate.of(2000, 1, 1));
		p.setDireccion("Calle Ficticia 1");
		p.setTelefono("600000000");
		p.setEmail("maria@example.test");
		p.setAceptacionNormas(true);
		p.setConsentimientoFotografico(false);
		p.setFirmanteNombre("María Prueba");
		p.setGrupoSnapshot("Taekwondo · Lunes 18:00-19:00");
		PlantillaPreinscripcion plantilla = new PlantillaPreinscripcion();
		plantilla.setContenido("{\"contacto\":\"Calle Ficticia\",\"normas\":[\"Primera norma.\"]}");
		p.setPlantilla(plantilla);
		BufferedImage firma = new BufferedImage(80, 30, BufferedImage.TYPE_INT_RGB);
		var g = firma.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, 80, 30);
		g.setColor(Color.BLUE);
		g.drawLine(5, 20, 70, 8);
		g.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(firma, "png", out);
		p.setFirma(out.toByteArray());
		return p;
	}

	static byte[] certificado(TesoreriaServiceImpl tesoreria) throws Exception {
		Alumno alumno = new Alumno();
		alumno.setId(5L);
		alumno.setNombre("Ana");
		alumno.setApellidos("Prueba");
		Class<?> datos = Class.forName(TesoreriaServiceImpl.class.getName() + "$CertificadoCobrosData");
		Constructor<?> constructor = datos.getDeclaredConstructor(Alumno.class, Integer.class, double[].class);
		constructor.setAccessible(true);
		Object certificado = constructor.newInstance(alumno, 2026, new double[12]);
		return (byte[]) ReflectionTestUtils.invokeMethod(tesoreria, "generarPdfCertificadoCobros", certificado);
	}

	private Organizacion escuelaConLogo(long id, String nombre, long imagenId, BufferedImage logo) throws IOException {
		Organizacion escuela = MarcaEscuelaPruebas.escuela(id, nombre);
		escuela.setLogoImagenId(imagenId);
		escribirLogo(imagenId, id, logo);
		return escuela;
	}

	/** Imagen {@code imagenId} de la escuela, con su PNG en la carpeta temporal. */
	private void escribirLogo(long imagenId, long escuela, BufferedImage logo) throws IOException {
		Path fichero = carpeta.resolve("logo-" + imagenId + ".png");
		ImageIO.write(logo, "png", fichero.toFile());
		Imagen imagen = new Imagen("logo.png", "image/png", "/imagenes/org-" + escuela + "/logo/logo.png",
				"org-" + escuela + "/logo/logo.png");
		imagen.setId(imagenId);
		ReflectionTestUtils.setField(imagen, "organizacionId", escuela);
		when(imagenesRepo.findById(imagenId)).thenReturn(Optional.of(imagen));
		when(imagenService.resolverRutaArchivo(imagen)).thenReturn(fichero);
	}

	// ------------------------------------------------------------------ utilidades

	/** Ni el nombre, ni la sede, ni los datos legales de Moiskimdo en el texto de un documento de otra escuela. */
	static void sinRastroDeMoiskimdo(String texto) {
		String normalizado = texto.toUpperCase(Locale.ROOT);
		for (String rastro : List.of("MOI'S", "MOI KIM", "KIM DO", "KIMDO", "MOISKIMDO", "UMBRETE", "TAE KWON DO",
				"G90341173", "PRESIDENTA")) {
			assertFalse(normalizado.contains(rastro), rastro + " en: " + texto);
		}
	}

	/** PNG generado en el test (nunca un logo real): un cuadro de color con una franja. */
	static BufferedImage logoFicticio(Color color) {
		BufferedImage imagen = new BufferedImage(48, 32, BufferedImage.TYPE_INT_RGB);
		var g = imagen.createGraphics();
		g.setColor(color);
		g.fillRect(0, 0, 48, 32);
		g.setColor(Color.WHITE);
		g.fillRect(0, 12, 48, 6);
		g.dispose();
		return imagen;
	}

	/** El logo de los informes de hoy: logo_escuela.svg pasado a PNG de 300x300 con Batik. */
	static BufferedImage logoEscuelaHoy() throws Exception {
		try (InputStream svg = clase("static/logo_escuela.svg")) {
			PNGTranscoder transcoder = new PNGTranscoder();
			transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, 300f);
			transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, 300f);
			ByteArrayOutputStream png = new ByteArrayOutputStream();
			transcoder.transcode(new TranscoderInput(svg), new TranscoderOutput(png));
			return ImageIO.read(new ByteArrayInputStream(png.toByteArray()));
		}
	}

	static InputStream clase(String ruta) throws IOException {
		return new ClassPathResource(ruta).getInputStream();
	}

	/** Quita la firma de prueba (80x30) de las imágenes de una solicitud. */
	static List<BufferedImage> sinFirma(List<BufferedImage> imagenes) {
		return imagenes.stream().filter(i -> i.getWidth() != 80 || i.getHeight() != 30).toList();
	}

	static int[] pixeles(BufferedImage imagen) {
		return imagen.getRGB(0, 0, imagen.getWidth(), imagen.getHeight(), null, 0, imagen.getWidth());
	}

	static String texto(byte[] pdf) throws IOException {
		try (PDDocument documento = PDDocument.load(pdf)) {
			return new PDFTextStripper().getText(documento);
		}
	}

	static List<String> lineas(byte[] pdf) throws IOException {
		return texto(pdf).lines().map(String::trim).filter(l -> !l.isEmpty()).toList();
	}

	static String campo(byte[] pdf, String nombre) throws IOException {
		try (PDDocument documento = PDDocument.load(pdf)) {
			return documento.getDocumentCatalog().getAcroForm().getField(nombre).getValueAsString();
		}
	}

	static BufferedImage unicaImagen(byte[] pdf) throws IOException {
		List<BufferedImage> imagenes = imagenes(pdf);
		assertEquals(1, imagenes.size(), "imágenes de la primera página");
		return imagenes.get(0);
	}

	/** Imágenes de la primera página, decodificadas antes de cerrar el documento (en el orden de sus recursos). */
	static List<BufferedImage> imagenes(byte[] pdf) throws IOException {
		try (PDDocument documento = PDDocument.load(pdf)) {
			List<PDImageXObject> encontradas = new ArrayList<>();
			recoger(documento.getPage(0).getResources(), encontradas);
			List<BufferedImage> decodificadas = new ArrayList<>();
			for (PDImageXObject imagen : encontradas) {
				decodificadas.add(imagen.getImage());
			}
			return decodificadas;
		}
	}

	private static void recoger(PDResources recursos, List<PDImageXObject> encontradas) throws IOException {
		if (recursos == null) {
			return;
		}
		for (COSName nombre : recursos.getXObjectNames()) {
			PDXObject objeto = recursos.getXObject(nombre);
			if (objeto instanceof PDImageXObject imagen) {
				encontradas.add(imagen);
			} else if (objeto instanceof PDFormXObject formulario) {
				recoger(formulario.getResources(), encontradas);
			}
		}
	}
}
