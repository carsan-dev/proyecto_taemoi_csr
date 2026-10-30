package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.CRC32;

import javax.imageio.IIOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.taemoi.project.entities.Imagen;
import com.taemoi.project.exceptions.ArchivoNoValidoException;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.services.ImagenService.FotoEventoGuardada;
import com.taemoi.project.services.impl.ImagenServiceImpl;
import com.taemoi.project.utils.DocumentoSecurityUtils;

class ImagenServiceImplTest {

	private static final int ANCHO_FOTO = 1000;
	private static final String WEBP_FOTO = "webp" + ANCHO_FOTO;
	private static final String WEBP_MINIATURA = "webp640";
	private static final String JPEG_FOTO = "jpeg" + ANCHO_FOTO;
	private static final String JPEG_MINIATURA = "jpeg640";
	/** Lo que escribe el codificador roto: ningún fichero sano mide esto en estas pruebas. */
	private static final long BYTES_A_MEDIAS = 1234;

	@TempDir
	Path tempDir;

	ImagenServiceImpl imagenService;

	@BeforeEach
	void setUp() {
		imagenService = new ImagenServiceImpl();
		ReflectionTestUtils.setField(imagenService, "directorioImagenesLinux", tempDir.toString());
		ReflectionTestUtils.setField(imagenService, "directorioImagenesWindows", tempDir.toString());
		TenantContext.set(1L);
	}

	@AfterEach
	void limpiar() {
		TenantContext.clear();
	}

	@Test
	void guardarImagenes_debeMantenerLasEscriturasDentroDeLosDirectoriosPermitidos() throws Exception {
		byte[] contenidoImagen = crearPng();
		Imagen imagenAlumno = imagenService.guardarImagen(new MockMultipartFile(
				"archivo", "../../foto.png", "image/png", contenidoImagen));
		Imagen imagenEvento = imagenService.guardarImagenEvento(new MockMultipartFile(
				"archivo", "..\\..\\cartel.png", "image/png", contenidoImagen));

		assertRutaDentroDelDirectorio(imagenAlumno, tempDir.resolve("org-1").resolve("alumnos"));
		assertRutaDentroDelDirectorio(imagenEvento, tempDir.resolve("org-1").resolve("eventos"));
	}

	@Test
	void guardarImagenes_enOtraEscuelaGuardaRutaRelativaConSuPrefijo() throws Exception {
		TenantContext.set(2L);
		Imagen alumno = imagenService.guardarImagen(new MockMultipartFile("archivo", "foto.png", "image/png", crearPng()));
		Imagen evento = imagenService.guardarImagenEvento(new MockMultipartFile("archivo", "cartel.png", "image/png", crearPng()));

		assertEquals("org-2/alumnos/" + alumno.getNombre(), alumno.getRuta());
		assertEquals("/imagenes/org-2/alumnos/" + alumno.getNombre(), alumno.getUrl());
		assertEquals("org-2/eventos/" + evento.getNombre(), evento.getRuta());
		assertEquals("/imagenes/org-2/eventos/" + evento.getNombre(), evento.getUrl());
		assertEquals(tempDir.resolve(alumno.getRuta()).toAbsolutePath().normalize(), imagenService.resolverRutaArchivo(alumno));
	}

	@Test
	void guardarImagen_sinEscuelaActivaFallaCerrado() {
		TenantContext.clear();
		assertThrows(IllegalStateException.class, () -> imagenService.guardarImagen(
				new MockMultipartFile("archivo", "foto.png", "image/png", crearPng())));
	}

	@Test
	void resolverYEliminar_mantienenLasRutasAbsolutasHeredadas() throws Exception {
		Path heredada = Files.createDirectories(tempDir.resolve("alumnos")).resolve("vieja.webp");
		Files.write(heredada, new byte[] { 1 });
		Imagen imagen = new Imagen("vieja.webp", "image/webp", "http://antiguo/imagenes/alumnos/vieja.webp",
				heredada.toString());

		assertEquals(heredada.toAbsolutePath().normalize(), imagenService.resolverRutaArchivo(imagen));
		imagenService.eliminarImagenDeSistema(imagen);
		assertFalse(Files.exists(heredada));
	}

	@Test
	void eliminarImagen_conRutaRelativaBorraDentroDeLaBaseYNoSaleDeElla() throws Exception {
		Imagen imagen = imagenService.guardarImagen(new MockMultipartFile("archivo", "foto.png", "image/png", crearPng()));
		Path archivo = tempDir.resolve(imagen.getRuta());
		assertTrue(Files.exists(archivo));

		imagenService.eliminarImagenDeSistema(imagen);
		assertFalse(Files.exists(archivo));

		Imagen maliciosa = new Imagen("x.webp", "image/webp", null, "../fuera.webp");
		assertNull(imagenService.resolverRutaArchivo(maliciosa));
		assertThrows(IllegalArgumentException.class, () -> imagenService.eliminarImagenDeSistema(maliciosa));
	}

	@Test
	void guardarImagenes_rechazanHeicPorExtensionOTipoSinEscribirNada() throws Exception {
		byte[] contenido = new byte[] { 0, 0, 0, 24, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c' };
		ArchivoNoValidoException alumno = assertThrows(ArchivoNoValidoException.class, () -> imagenService
				.guardarImagen(new MockMultipartFile("file", "IMG_0001.HEIC", "application/octet-stream", contenido)));
		ArchivoNoValidoException evento = assertThrows(ArchivoNoValidoException.class, () -> imagenService
				.guardarImagenEvento(new MockMultipartFile("file", "cartel.jpg", "image/heif", contenido)));

		assertEquals(DocumentoSecurityUtils.MENSAJE_HEIC, alumno.getMessage());
		assertEquals(DocumentoSecurityUtils.MENSAJE_HEIC, evento.getMessage());
		assertFalse(Files.exists(tempDir.resolve("org-1")), "No debe crear carpetas ni ficheros");
	}

	@Test
	void guardarImagenes_conContenidoQueNoEsImagenLanzanArgumentoInvalido() {
		byte[] noImagen = "MZ esto no es una imagen".getBytes();
		ArchivoNoValidoException alumno = assertThrows(ArchivoNoValidoException.class, () -> imagenService
				.guardarImagen(new MockMultipartFile("file", "foto.jpg", "image/jpeg", noImagen)));
		ArchivoNoValidoException evento = assertThrows(ArchivoNoValidoException.class, () -> imagenService
				.guardarImagenEvento(new MockMultipartFile("file", "cartel.png", "image/png", noImagen)));

		assertEquals("No se pudo leer la imagen del alumno.", alumno.getMessage());
		assertEquals("No se pudo leer la imagen del evento.", evento.getMessage());
	}

	@Test
	void errorDeEntradaSalidaDelServidorAlDecodificarSigueSiendoIOException() {
		IOException disco = new IOException("No queda espacio en el dispositivo");
		ImagenServiceImpl conDiscoRoto = decodificadorQueLanza(new IIOException("Can't create cache file!", disco));

		IOException alumno = assertThrows(IOException.class, () -> conDiscoRoto
				.guardarImagen(new MockMultipartFile("file", "foto.png", "image/png", crearPng())));
		IOException evento = assertThrows(IOException.class, () -> conDiscoRoto
				.guardarImagenEvento(new MockMultipartFile("file", "cartel.png", "image/png", crearPng())));

		assertSame(disco, alumno.getCause());
		assertSame(disco, evento.getCause());
	}

	@Test
	void imagenDanadaAlDecodificarEsArchivoNoValido() {
		ImagenServiceImpl sinCausa = decodificadorQueLanza(new IIOException("Unsupported Image Type"));
		ImagenServiceImpl truncada = decodificadorQueLanza(
				new IIOException("Error reading", new java.io.EOFException()));
		ImagenServiceImpl anidada = decodificadorQueLanza(
				new IIOException("Error reading", new IIOException("Bogus marker length")));

		for (ImagenServiceImpl servicio : new ImagenServiceImpl[] { sinCausa, truncada, anidada }) {
			ArchivoNoValidoException error = assertThrows(ArchivoNoValidoException.class, () -> servicio
					.guardarImagen(new MockMultipartFile("file", "foto.jpg", "image/jpeg", new byte[] { 1 })));
			assertEquals("No se pudo leer la imagen del alumno.", error.getMessage());
		}
	}

	@Test
	void guardarFotoEvento_vaALaCarpetaPrivadaDeLaEscuela() throws Exception {
		TenantContext.set(2L);
		Imagen foto = imagenService.guardarImagenFotoEvento(
				new MockMultipartFile("archivo", "foto.png", "image/png", crearPng())).foto();

		assertRutaDentroDelDirectorio(foto, tempDir.resolve("org-2").resolve("fotos-eventos"));
		assertEquals("org-2/fotos-eventos/" + foto.getNombre(), foto.getRuta());
		assertFalse(Files.exists(tempDir.resolve("org-2").resolve("eventos")), "Nunca bajo la carpeta pública");
		assertFalse(com.taemoi.project.archivos.RutasArchivos.esImagenPublica(foto.getRuta()));
	}

	/** El nombre puede ser el de un menor o traer un recorrido: no llega al disco ni a la fila. */
	@Test
	void guardarFotoEvento_noUsaElNombreDelFicheroSubido() throws Exception {
		Imagen foto = imagenService.guardarImagenFotoEvento(new MockMultipartFile("archivo",
				"../../Lucia_Garcia_Lopez_" + "x".repeat(300) + ".png", "image/png", crearPng())).foto();

		assertRutaDentroDelDirectorio(foto, tempDir.resolve("org-1").resolve("fotos-eventos"));
		assertFalse(foto.getNombre().contains("Lucia"), foto.getNombre());
		assertTrue(foto.getNombre().length() <= 200, "Cabe en imagen.nombre");
	}

	@Test
	void guardarFotoEvento_reduceComoElCartelSinAmpliar() throws Exception {
		Imagen grande = imagenService.guardarImagenFotoEvento(
				new MockMultipartFile("archivo", "grande.png", "image/png", crearPng(3000, 1500))).foto();
		Imagen pequena = imagenService.guardarImagenFotoEvento(
				new MockMultipartFile("archivo", "pequena.png", "image/png", crearPng(300, 200))).foto();
		Imagen cartel = imagenService.guardarImagenEvento(
				new MockMultipartFile("archivo", "cartel.png", "image/png", crearPng(3000, 1500)));

		assertEquals(cartel.getTipo(), grande.getTipo(), "Mismo formato que el cartel");
		BufferedImage leidaCartel = ImageIO.read(tempDir.resolve(cartel.getRuta()).toFile());
		BufferedImage leidaGrande = ImageIO.read(tempDir.resolve(grande.getRuta()).toFile());
		BufferedImage leidaPequena = ImageIO.read(tempDir.resolve(pequena.getRuta()).toFile());
		assertEquals(1920, leidaGrande.getWidth());
		assertEquals(960, leidaGrande.getHeight());
		assertEquals(leidaCartel.getWidth(), leidaGrande.getWidth());
		assertEquals(300, leidaPequena.getWidth());
	}

	@Test
	void guardarFotoEvento_rechazaHeicYContenidoQueNoEsImagen_sinEscribirNada() {
		byte[] heic = new byte[] { 0, 0, 0, 24, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c' };
		ArchivoNoValidoException porExtension = assertThrows(ArchivoNoValidoException.class, () -> imagenService
				.guardarImagenFotoEvento(new MockMultipartFile("archivo", "IMG_0001.HEIC", "application/octet-stream", heic)));
		ArchivoNoValidoException porTipo = assertThrows(ArchivoNoValidoException.class, () -> imagenService
				.guardarImagenFotoEvento(new MockMultipartFile("archivo", "foto.jpg", "image/heif", heic)));
		ArchivoNoValidoException noImagen = assertThrows(ArchivoNoValidoException.class, () -> imagenService
				.guardarImagenFotoEvento(new MockMultipartFile("archivo", "foto.png", "image/png", "MZ no".getBytes())));

		assertEquals(DocumentoSecurityUtils.MENSAJE_HEIC, porExtension.getMessage());
		assertEquals(DocumentoSecurityUtils.MENSAJE_HEIC, porTipo.getMessage());
		assertEquals("No se pudo leer la foto del evento.", noImagen.getMessage());
		assertFalse(Files.exists(tempDir.resolve("org-1").resolve("eventos")));
	}

	@Test
	void guardarFotoEvento_sinEscuelaActivaFallaCerrado() {
		TenantContext.clear();
		assertThrows(IllegalStateException.class, () -> imagenService.guardarImagenFotoEvento(
				new MockMultipartFile("archivo", "foto.png", "image/png", crearPng())));
	}

	// ------------------------------------------------------------------ miniatura

	@Test
	void guardarFotoEvento_guardaUnaMiniaturaDe640PorElLadoMayor() throws Exception {
		FotoEventoGuardada apaisada = imagenService.guardarImagenFotoEvento(
				new MockMultipartFile("archivo", "a.png", "image/png", crearPng(3000, 1500)));
		FotoEventoGuardada vertical = imagenService.guardarImagenFotoEvento(
				new MockMultipartFile("archivo", "b.png", "image/png", crearPng(1500, 3000)));

		assertDimensiones(1920, 960, apaisada.foto());
		assertDimensiones(640, 320, apaisada.miniatura());
		assertDimensiones(960, 1920, vertical.foto());
		assertDimensiones(320, 640, vertical.miniatura());
	}

	@Test
	void guardarFotoEvento_laMiniaturaNoAmplia() throws Exception {
		FotoEventoGuardada pequena = imagenService.guardarImagenFotoEvento(
				new MockMultipartFile("archivo", "a.png", "image/png", crearPng(300, 200)));

		assertDimensiones(300, 200, pequena.foto());
		assertDimensiones(300, 200, pequena.miniatura());
	}

	@Test
	void guardarFotoEvento_laMiniaturaVaALaMismaCarpetaPrivada_sinElNombreSubido() throws Exception {
		TenantContext.set(2L);
		FotoEventoGuardada guardada = imagenService.guardarImagenFotoEvento(new MockMultipartFile("archivo",
				"Lucia_Garcia_Lopez.png", "image/png", crearPng(1000, 800)));
		Imagen miniatura = guardada.miniatura();

		assertRutaDentroDelDirectorio(miniatura, tempDir.resolve("org-2").resolve("fotos-eventos"));
		assertEquals("org-2/fotos-eventos/" + miniatura.getNombre(), miniatura.getRuta());
		assertEquals("/imagenes/org-2/fotos-eventos/" + miniatura.getNombre(), miniatura.getUrl());
		assertFalse(com.taemoi.project.archivos.RutasArchivos.esImagenPublica(miniatura.getRuta()));
		assertFalse(miniatura.getNombre().contains("Lucia"), miniatura.getNombre());
		assertTrue(miniatura.getNombre().length() <= 200, "Cabe en imagen.nombre");
		assertFalse(miniatura.getRuta().equals(guardada.foto().getRuta()), "Cada una con su fichero");
		assertEquals(2, ficherosDeFotos("org-2"));
	}

	@Test
	void guardarFotoEvento_laMiniaturaTambienSeOrienta() throws Exception {
		Imagen miniatura = imagenService.guardarImagenFotoEvento(
				new MockMultipartFile("archivo", "movil.jpg", "image/jpeg", jpegConOrientacion(4000, 2000, 6))).miniatura();

		BufferedImage leida = ImageIO.read(tempDir.resolve(miniatura.getRuta()).toFile());
		assertEquals(320, leida.getWidth());
		assertEquals(640, leida.getHeight());
		assertColor(Color.BLUE, leida, 80, 160);
		assertColor(Color.RED, leida, 240, 160);
		assertColor(Color.WHITE, leida, 80, 480);
		assertColor(Color.GREEN, leida, 240, 480);
	}

	@Test
	void siFallaLaMiniatura_noQuedaLaFoto() throws Exception {
		for (Throwable fallo : new Throwable[] { new IOException("disco"), new IllegalStateException("codificador"),
				new OutOfMemoryError("memoria") }) {
			ImagenServiceImpl servicio = new ImagenServiceImpl() {
				@Override
				protected Imagen guardarMiniaturaFotoEvento(BufferedImage foto, Path directorio, String carpetaRelativa)
						throws IOException {
					if (fallo instanceof IOException e) {
						throw e;
					}
					if (fallo instanceof RuntimeException e) {
						throw e;
					}
					throw (Error) fallo;
				}
			};
			ReflectionTestUtils.setField(servicio, "directorioImagenesLinux", tempDir.toString());
			ReflectionTestUtils.setField(servicio, "directorioImagenesWindows", tempDir.toString());

			assertSame(fallo, assertThrows(Throwable.class, () -> servicio.guardarImagenFotoEvento(
					new MockMultipartFile("archivo", "a.png", "image/png", crearPng(100, 100)))));
			assertEquals(0, ficherosDeFotos("org-1"), "Sin miniatura no hay foto que registrar");
		}
	}

	// ------------------------------------------------------------------ escritura a medias

	@Test
	void siElWebPDeLaFotoFallaAMedias_caeAJpegSinResiduo() throws Exception {
		for (Throwable fallo : excepciones()) {
			Map<String, Long> antes = fotosEnDisco();
			ConCodificadorRoto servicio = new ConCodificadorRoto(fallo, WEBP_FOTO);

			FotoEventoGuardada guardada = servicio.guardarImagenFotoEvento(foto());

			assertEquals("image/jpeg", guardada.foto().getTipo(), fallo.toString());
			assertEquals("image/webp", guardada.miniatura().getTipo(), fallo.toString());
			assertSoloNuevas(antes, guardada);
			borrar(guardada);
			assertEquals(antes, fotosEnDisco(), fallo.toString());
		}
	}

	@Test
	void siElWebPDeLaMiniaturaFallaAMedias_caeAJpegSinResiduo() throws Exception {
		for (Throwable fallo : excepciones()) {
			Map<String, Long> antes = fotosEnDisco();
			ConCodificadorRoto servicio = new ConCodificadorRoto(fallo, WEBP_MINIATURA);

			FotoEventoGuardada guardada = servicio.guardarImagenFotoEvento(foto());

			assertEquals("image/webp", guardada.foto().getTipo(), fallo.toString());
			assertEquals("image/jpeg", guardada.miniatura().getTipo(), fallo.toString());
			assertSoloNuevas(antes, guardada);
			borrar(guardada);
			assertEquals(antes, fotosEnDisco(), fallo.toString());
		}
	}

	/** Un error no cae a JPEG: el fallo sigue su camino y no deja rastro. */
	@Test
	void siElWebPFallaConUnError_seRelanzaSinResiduo_yNoIntentaElJpeg() throws Exception {
		Map<String, Long> antes = fotosEnDisco();
		for (String roto : new String[] { WEBP_FOTO, WEBP_MINIATURA }) {
			OutOfMemoryError fallo = new OutOfMemoryError("memoria");
			ConCodificadorRoto servicio = new ConCodificadorRoto(fallo, roto);

			assertSame(fallo, assertThrows(Throwable.class, () -> servicio.guardarImagenFotoEvento(foto())), roto);

			assertEquals(antes, fotosEnDisco(), roto);
			assertFalse(servicio.codificados.stream().anyMatch(c -> c.startsWith("jpeg")), roto);
		}
	}

	@Test
	void siTambienFallaElJpeg_seRelanzaSinResiduo() throws Exception {
		Map<String, Long> antes = fotosEnDisco();
		String[][] casos = { { WEBP_FOTO, JPEG_FOTO }, { WEBP_MINIATURA, JPEG_MINIATURA } };
		for (String[] rotos : casos) {
			for (Throwable fallo : todos()) {
				ConCodificadorRoto servicio = new ConCodificadorRoto(new IOException("webp"), rotos);
				servicio.falloDelJpeg = fallo;

				assertSame(fallo, assertThrows(Throwable.class, () -> servicio.guardarImagenFotoEvento(foto())));

				assertEquals(antes, fotosEnDisco(), rotos[1] + " " + fallo);
				assertEquals(1, servicio.codificados.stream().filter(rotos[1]::equals).count(), rotos[1]);
			}
		}
	}

	/** Pasa también por la limpieza del camino de rollback: la foto ya escrita se va con la miniatura a medias. */
	@Test
	void siLaMiniaturaFallaAMedias_laFotoYaEscritaTambienSeVa() throws Exception {
		Map<String, Long> antes = fotosEnDisco();
		OutOfMemoryError fallo = new OutOfMemoryError("memoria");
		ConCodificadorRoto servicio = new ConCodificadorRoto(fallo, WEBP_MINIATURA);

		assertSame(fallo, assertThrows(Throwable.class, () -> servicio.guardarImagenFotoEvento(foto())));

		assertTrue(servicio.codificados.contains(WEBP_FOTO), "La foto llegó a escribirse");
		assertEquals(antes, fotosEnDisco());
	}

	@Test
	void siNoSePuedeBorrarElParcial_noTapaElErrorOriginal() throws Exception {
		for (Throwable falloAlBorrar : new Throwable[] { new IOException("borrado"), new IllegalStateException("borrado"),
				new StackOverflowError("borrado") }) {
			for (Throwable fallo : todos()) {
				ConCodificadorRoto webpRoto = new ConCodificadorRoto(new OutOfMemoryError("webp"), WEBP_FOTO);
				webpRoto.falloAlBorrar = falloAlBorrar;
				ConCodificadorRoto jpegRoto = new ConCodificadorRoto(new IOException("webp"), WEBP_FOTO, JPEG_FOTO);
				jpegRoto.falloDelJpeg = fallo;
				jpegRoto.falloAlBorrar = falloAlBorrar;

				assertSame(webpRoto.fallo, assertThrows(Throwable.class, () -> webpRoto.guardarImagenFotoEvento(foto())));
				assertSame(fallo, assertThrows(Throwable.class, () -> jpegRoto.guardarImagenFotoEvento(foto())));
				assertEquals(1, webpRoto.intentosDeBorrado.size(), "Se intentó borrar");
				assertEquals(2, jpegRoto.intentosDeBorrado.size(), "El WebP y el JPEG");
			}
		}
	}

	@Test
	void siNoSePuedeBorrarElWebP_laReservaJpegSigue() throws Exception {
		ConCodificadorRoto servicio = new ConCodificadorRoto(new IOException("disco"), WEBP_FOTO);
		servicio.falloAlBorrar = new IOException("borrado");

		assertEquals("image/jpeg", servicio.guardarImagenFotoEvento(foto()).foto().getTipo());
		assertEquals(1, servicio.intentosDeBorrado.size());
	}

	@Test
	void elLogDelFalloNoLlevaLaRuta() throws Exception {
		String ruta = tempDir.resolve("org-1").toString();
		Logger logger = (Logger) LoggerFactory.getLogger(ImagenServiceImpl.class);
		ListAppender<ILoggingEvent> registro = new ListAppender<>();
		registro.start();
		logger.addAppender(registro);
		try {
			ConCodificadorRoto servicio = new ConCodificadorRoto(new IOException("No se pudo escribir " + ruta), WEBP_FOTO);
			servicio.falloAlBorrar = new IOException("No se pudo borrar " + ruta);

			servicio.guardarImagenFotoEvento(foto());
		} finally {
			logger.detachAppender(registro);
		}

		assertEquals(2, registro.list.size(), registro.list.toString());
		for (ILoggingEvent evento : registro.list) {
			assertFalse(evento.getFormattedMessage().contains(ruta), evento.getFormattedMessage());
			assertTrue(evento.getFormattedMessage().contains("IOException"), evento.getFormattedMessage());
			assertNull(evento.getThrowableProxy(), "Sin traza: lleva el mismo mensaje");
		}
	}

	/** Comparten el código de escritura: la reserva sigue igual y tampoco ellos dejan residuo. */
	@Test
	void elCartelYLaFotoDeAlumnoCaenAJpegSinResiduo() throws Exception {
		ConCodificadorRoto servicio = new ConCodificadorRoto(new IOException("disco"), "webp8");

		Imagen cartel = servicio.guardarImagenEvento(new MockMultipartFile("archivo", "c.png", "image/png", crearPng()));
		Imagen alumno = servicio.guardarImagen(new MockMultipartFile("archivo", "a.png", "image/png", crearPng()));

		for (Imagen imagen : new Imagen[] { cartel, alumno }) {
			assertEquals("image/jpeg", imagen.getTipo());
			assertEquals(8, ImageIO.read(tempDir.resolve(imagen.getRuta()).toFile()).getWidth());
			assertEquals(1, ficheros(tempDir.resolve(imagen.getRuta()).getParent()), imagen.getRuta());
		}
	}

	private static Throwable[] excepciones() {
		return new Throwable[] { new IOException("disco lleno"), new IllegalStateException("codificador") };
	}

	private static Throwable[] todos() {
		return new Throwable[] { new IOException("disco lleno"), new IllegalStateException("codificador"),
				new OutOfMemoryError("memoria") };
	}

	private MockMultipartFile foto() {
		return new MockMultipartFile("archivo", "foto.png", "image/png", crearPng(ANCHO_FOTO, 800));
	}

	private void borrar(FotoEventoGuardada guardada) {
		imagenService.eliminarImagenDeSistema(guardada.foto());
		imagenService.eliminarImagenDeSistema(guardada.miniatura());
	}

	private void assertSoloNuevas(Map<String, Long> antes, FotoEventoGuardada guardada) throws IOException {
		Set<String> esperados = new HashSet<>(antes.keySet());
		esperados.add(guardada.foto().getNombre());
		esperados.add(guardada.miniatura().getNombre());
		Map<String, Long> ahora = fotosEnDisco();
		assertEquals(esperados, ahora.keySet());
		assertTrue(ahora.values().stream().allMatch(tamano -> tamano != BYTES_A_MEDIAS), "Ningún fichero a medias");
	}

	private Map<String, Long> fotosEnDisco() throws IOException {
		Path carpeta = tempDir.resolve("org-1").resolve("fotos-eventos");
		Map<String, Long> ficheros = new HashMap<>();
		if (Files.isDirectory(carpeta)) {
			try (Stream<Path> rutas = Files.list(carpeta)) {
				for (Path ruta : rutas.toList()) {
					ficheros.put(ruta.getFileName().toString(), Files.size(ruta));
				}
			}
		}
		return ficheros;
	}

	/** Escribe bytes de verdad en el fichero de destino y falla: un disco lleno o una memoria agotada a mitad. */
	private class ConCodificadorRoto extends ImagenServiceImpl {

		private final Throwable fallo;
		private final Set<String> rotos;
		final List<String> codificados = new ArrayList<>();
		final List<Path> intentosDeBorrado = new ArrayList<>();
		Throwable falloDelJpeg;
		Throwable falloAlBorrar;

		ConCodificadorRoto(Throwable fallo, String... rotos) {
			this.fallo = fallo;
			this.rotos = Set.of(rotos);
			ReflectionTestUtils.setField(this, "directorioImagenesLinux", tempDir.toString());
			ReflectionTestUtils.setField(this, "directorioImagenesWindows", tempDir.toString());
		}

		@Override
		protected void codificar(ImageWriter writer, IIOImage imagen, ImageWriteParam param) throws IOException {
			String clave = writer.getOriginatingProvider().getFormatNames()[0].toLowerCase()
					+ imagen.getRenderedImage().getWidth();
			codificados.add(clave);
			if (!rotos.contains(clave)) {
				super.codificar(writer, imagen, param);
				return;
			}
			ImageOutputStream salida = (ImageOutputStream) writer.getOutput();
			salida.write(new byte[(int) BYTES_A_MEDIAS]);
			salida.flush();
			lanzar(falloDelJpeg != null && clave.startsWith("jpeg") ? falloDelJpeg : fallo);
		}

		@Override
		protected void borrarFicheroParcial(Path fichero) throws IOException {
			intentosDeBorrado.add(fichero);
			if (falloAlBorrar != null) {
				lanzar(falloAlBorrar);
			}
			super.borrarFicheroParcial(fichero);
		}

		private static void lanzar(Throwable fallo) throws IOException {
			if (fallo instanceof IOException e) {
				throw e;
			}
			if (fallo instanceof RuntimeException e) {
				throw e;
			}
			throw (Error) fallo;
		}
	}

	@Test
	void elCartelYLasFotosDeAlumnoNoGuardanMiniatura() throws Exception {
		Imagen cartel = imagenService.guardarImagenEvento(new MockMultipartFile("archivo", "c.png", "image/png", crearPng()));
		Imagen alumno = imagenService.guardarImagen(new MockMultipartFile("archivo", "a.png", "image/png", crearPng()));

		assertEquals(1, ficheros(tempDir.resolve(cartel.getRuta()).getParent()));
		assertEquals(1, ficheros(tempDir.resolve(alumno.getRuta()).getParent()));
	}

	private long ficherosDeFotos(String organizacion) throws IOException {
		return ficheros(tempDir.resolve(organizacion).resolve("fotos-eventos"));
	}

	private static long ficheros(Path carpeta) throws IOException {
		if (!Files.isDirectory(carpeta)) {
			return 0;
		}
		try (Stream<Path> rutas = Files.list(carpeta)) {
			return rutas.count();
		}
	}

	private void assertDimensiones(int ancho, int alto, Imagen imagen) throws IOException {
		BufferedImage leida = ImageIO.read(tempDir.resolve(imagen.getRuta()).toFile());
		assertEquals(ancho, leida.getWidth(), imagen.getRuta());
		assertEquals(alto, leida.getHeight(), imagen.getRuta());
	}

	// ------------------------------------------------------------------ orientación EXIF

	/** Cuadrantes de colores: si la foto saliera en espejo o girada al revés, no coincidirían. */
	@Test
	void guardarFotoEvento_aplicaLaOrientacionAntesDeReducir() throws Exception {
		Imagen foto = imagenService.guardarImagenFotoEvento(
				new MockMultipartFile("archivo", "movil.jpg", "image/jpeg", jpegConOrientacion(4000, 2000, 6))).foto();

		BufferedImage leida = ImageIO.read(tempDir.resolve(foto.getRuta()).toFile());
		assertEquals(960, leida.getWidth());
		assertEquals(1920, leida.getHeight());
		// Guardada: rojo verde / azul blanco. Girada 90° a la derecha: azul rojo / blanco verde.
		assertColor(Color.BLUE, leida, 240, 480);
		assertColor(Color.RED, leida, 720, 480);
		assertColor(Color.WHITE, leida, 240, 1440);
		assertColor(Color.GREEN, leida, 720, 1440);
	}

	@Test
	void guardarFotoEvento_sinNecesidadDeReducirTambienSeOrienta() throws Exception {
		Imagen foto = imagenService.guardarImagenFotoEvento(
				new MockMultipartFile("archivo", "movil.jpg", "image/jpeg", jpegConOrientacion(400, 200, 8))).foto();

		BufferedImage leida = ImageIO.read(tempDir.resolve(foto.getRuta()).toFile());
		assertEquals(200, leida.getWidth());
		assertEquals(400, leida.getHeight());
		// Girada 90° a la izquierda: verde blanco / rojo azul.
		assertColor(Color.GREEN, leida, 50, 100);
		assertColor(Color.WHITE, leida, 150, 100);
		assertColor(Color.RED, leida, 50, 300);
		assertColor(Color.BLUE, leida, 150, 300);
	}

	@Test
	void guardarFotoEvento_sinEtiquetaOConEtiquetaCorrupta_seGuardaTalCual() throws Exception {
		byte[] corrupta = conApp1(jpeg(400, 200), "Exif\0\0MM\0*\u0000\u0000\u00ff\u00ff".getBytes(StandardCharsets.ISO_8859_1));
		byte[] truncada = conApp1(jpeg(400, 200), "Exif\0\0I".getBytes(StandardCharsets.ISO_8859_1));

		for (byte[] contenido : new byte[][] { jpeg(400, 200), corrupta, truncada }) {
			Imagen foto = imagenService.guardarImagenFotoEvento(
					new MockMultipartFile("archivo", "foto.jpg", "image/jpeg", contenido)).foto();

			BufferedImage leida = ImageIO.read(tempDir.resolve(foto.getRuta()).toFile());
			assertEquals(400, leida.getWidth());
			assertEquals(200, leida.getHeight());
			assertColor(Color.RED, leida, 100, 50);
		}
	}

	/** Solo la galería: un cartel o una foto de alumno ya guardados no deben cambiar de conducta. */
	@Test
	void elCartelYLasFotosDeAlumnoNoAplicanLaOrientacion() throws Exception {
		byte[] girada = jpegConOrientacion(400, 200, 6);

		Imagen cartel = imagenService.guardarImagenEvento(new MockMultipartFile("archivo", "c.jpg", "image/jpeg", girada));
		Imagen alumno = imagenService.guardarImagen(new MockMultipartFile("archivo", "a.jpg", "image/jpeg", girada));

		for (Imagen imagen : new Imagen[] { cartel, alumno }) {
			BufferedImage leida = ImageIO.read(tempDir.resolve(imagen.getRuta()).toFile());
			assertEquals(400, leida.getWidth(), imagen.getRuta());
			assertEquals(200, leida.getHeight(), imagen.getRuta());
			assertColor(Color.RED, leida, 100, 50);
		}
	}

	// ------------------------------------------------------------------ tope de píxeles

	@Test
	void guardarFotoEvento_rechazaMasDe60Megapixeles_sinDecodificar() {
		AtomicInteger decodificadas = new AtomicInteger();
		ImagenServiceImpl servicio = contandoDecodificadas(decodificadas);

		for (byte[] enorme : new byte[][] { cabeceraPng(10_000, 6_001), cabeceraPng(30_000, 30_000),
				cabeceraPng(Integer.MAX_VALUE, Integer.MAX_VALUE) }) {
			ArchivoNoValidoException error = assertThrows(ArchivoNoValidoException.class, () -> servicio
					.guardarImagenFotoEvento(new MockMultipartFile("archivo", "foto.png", "image/png", enorme)));
			assertEquals("La foto es demasiado grande. Usa una de menos de 60 megapíxeles.", error.getMessage());
		}
		assertEquals(0, decodificadas.get(), "Ni un píxel en memoria");
		assertFalse(Files.exists(tempDir.resolve("org-1").resolve("fotos-eventos")), "Sin rastro en disco");
	}

	@Test
	void guardarFotoEvento_admite60MegapixelesJustos() throws Exception {
		AtomicInteger decodificadas = new AtomicInteger();

		contandoDecodificadas(decodificadas).guardarImagenFotoEvento(
				new MockMultipartFile("archivo", "foto.png", "image/png", cabeceraPng(10_000, 6_000)));

		assertEquals(1, decodificadas.get());
	}

	/** El mismo tamaño pasa por el cartel: su conducta no cambia. */
	@Test
	void elTopeDePixelesEsSoloDeLaGaleria() throws Exception {
		AtomicInteger decodificadas = new AtomicInteger();

		contandoDecodificadas(decodificadas).guardarImagenEvento(
				new MockMultipartFile("archivo", "cartel.png", "image/png", cabeceraPng(10_000, 6_001)));

		assertEquals(1, decodificadas.get());
	}

	@Test
	void guardarFotoEvento_sinCabeceraLegible_noDecodifica() {
		AtomicInteger decodificadas = new AtomicInteger();
		ImagenServiceImpl servicio = contandoDecodificadas(decodificadas);
		byte[] cortada = new byte[20];
		System.arraycopy(cabeceraPng(100, 100), 0, cortada, 0, cortada.length);

		ArchivoNoValidoException error = assertThrows(ArchivoNoValidoException.class,
				() -> servicio.guardarImagenFotoEvento(new MockMultipartFile("archivo", "foto.png", "image/png", cortada)));

		assertEquals("No se pudo leer la foto del evento.", error.getMessage());
		assertEquals(0, decodificadas.get());
	}

	@Test
	void guardarFotoEvento_leeLaCabeceraDeCadaFormato() throws Exception {
		for (MockMultipartFile archivo : new MockMultipartFile[] {
				new MockMultipartFile("archivo", "a.jpg", "image/jpeg", jpeg(40, 30)),
				new MockMultipartFile("archivo", "a.png", "image/png", crearPng(40, 30)),
				new MockMultipartFile("archivo", "a.webp", "image/webp", codificada("webp")),
				new MockMultipartFile("archivo", "a.gif", "image/gif", codificada("gif")),
				new MockMultipartFile("archivo", "a.bmp", "image/bmp", codificada("bmp")) }) {
			Imagen foto = imagenService.guardarImagenFotoEvento(archivo).foto();
			assertEquals(40, ImageIO.read(tempDir.resolve(foto.getRuta()).toFile()).getWidth(), archivo.getOriginalFilename());
		}
	}

	private static byte[] codificada(String formato) throws IOException {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		assertTrue(ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_3BYTE_BGR), formato, salida), formato);
		return salida.toByteArray();
	}

	private ImagenServiceImpl contandoDecodificadas(AtomicInteger decodificadas) {
		ImagenServiceImpl servicio = new ImagenServiceImpl() {
			@Override
			protected BufferedImage decodificar(InputStream entrada) {
				decodificadas.incrementAndGet();
				return new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
			}
		};
		ReflectionTestUtils.setField(servicio, "directorioImagenesLinux", tempDir.toString());
		ReflectionTestUtils.setField(servicio, "directorioImagenesWindows", tempDir.toString());
		return servicio;
	}

	/** Solo firma e IHDR: declara el tamaño sin pesar nada. */
	private static byte[] cabeceraPng(int ancho, int alto) {
		ByteBuffer cuerpo = ByteBuffer.allocate(17);
		cuerpo.put("IHDR".getBytes(StandardCharsets.ISO_8859_1)).putInt(ancho).putInt(alto);
		cuerpo.put(new byte[] { 8, 2, 0, 0, 0 });
		CRC32 crc = new CRC32();
		crc.update(cuerpo.array());
		ByteBuffer png = ByteBuffer.allocate(8 + 4 + 17 + 4);
		png.put(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A });
		png.putInt(13).put(cuerpo.array()).putInt((int) crc.getValue());
		return png.array();
	}

	/** Cuadrantes: rojo verde / azul blanco. */
	private static byte[] jpeg(int ancho, int alto) throws IOException {
		BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = imagen.createGraphics();
		graphics.setColor(Color.RED);
		graphics.fillRect(0, 0, ancho / 2, alto / 2);
		graphics.setColor(Color.GREEN);
		graphics.fillRect(ancho / 2, 0, ancho - ancho / 2, alto / 2);
		graphics.setColor(Color.BLUE);
		graphics.fillRect(0, alto / 2, ancho / 2, alto - alto / 2);
		graphics.setColor(Color.WHITE);
		graphics.fillRect(ancho / 2, alto / 2, ancho - ancho / 2, alto - alto / 2);
		graphics.dispose();
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		assertTrue(ImageIO.write(imagen, "jpeg", salida));
		return salida.toByteArray();
	}

	private static byte[] jpegConOrientacion(int ancho, int alto, int orientacion) throws IOException {
		ByteBuffer exif = ByteBuffer.allocate(6 + 8 + 2 + 12 + 4);
		exif.put("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1));
		exif.put(new byte[] { 'M', 'M' }).putShort((short) 42).putInt(8).putShort((short) 1);
		exif.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientacion).putShort((short) 0);
		return conApp1(jpeg(ancho, alto), exif.array());
	}

	/** Mete el segmento APP1 justo detrás del inicio del JPEG. */
	private static byte[] conApp1(byte[] jpeg, byte[] contenido) {
		ByteBuffer salida = ByteBuffer.allocate(jpeg.length + 4 + contenido.length);
		salida.put(jpeg, 0, 2).put((byte) 0xFF).put((byte) 0xE1).putShort((short) (contenido.length + 2)).put(contenido);
		salida.put(jpeg, 2, jpeg.length - 2);
		return salida.array();
	}

	private static void assertColor(Color esperado, BufferedImage imagen, int x, int y) {
		Color leido = new Color(imagen.getRGB(x, y));
		int diferencia = Math.abs(esperado.getRed() - leido.getRed()) + Math.abs(esperado.getGreen() - leido.getGreen())
				+ Math.abs(esperado.getBlue() - leido.getBlue());
		assertTrue(diferencia < 60, "Píxel " + x + "," + y + ": esperado " + esperado + ", leído " + leido);
	}

	private ImagenServiceImpl decodificadorQueLanza(IIOException error) {
		ImagenServiceImpl servicio = new ImagenServiceImpl() {
			@Override
			protected BufferedImage decodificar(InputStream entrada) throws IOException {
				throw error;
			}
		};
		ReflectionTestUtils.setField(servicio, "directorioImagenesLinux", tempDir.toString());
		ReflectionTestUtils.setField(servicio, "directorioImagenesWindows", tempDir.toString());
		return servicio;
	}

	private byte[] crearPng() {
		return crearPng(8, 8);
	}

	private byte[] crearPng(int ancho, int alto) {
		try {
			BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			ImageIO.write(imagen, "png", output);
			return output.toByteArray();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private void assertRutaDentroDelDirectorio(Imagen imagen, Path directorioPermitido) {
		Path directorioSeguro = directorioPermitido.toAbsolutePath().normalize();
		assertFalse(Path.of(imagen.getRuta()).isAbsolute(), "La ruta guardada debe ser relativa");
		Path rutaGuardada = tempDir.resolve(imagen.getRuta()).toAbsolutePath().normalize();
		assertTrue(rutaGuardada.startsWith(directorioSeguro));
		assertEquals(directorioSeguro, rutaGuardada.getParent());
		assertTrue(Files.exists(rutaGuardada));
	}
}
