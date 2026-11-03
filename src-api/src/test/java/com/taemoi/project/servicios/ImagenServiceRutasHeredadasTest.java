package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.taemoi.project.entities.Imagen;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.services.impl.ImagenServiceImpl;

/**
 * Una fila de {@code imagen} con ruta ABSOLUTA (heredada o forzada) solo se lee o se borra si su fichero queda, sin
 * {@code ..} y sin enlaces, dentro de la carpeta de imágenes o de una carpeta heredada configurada
 * ({@code app.imagenes.directorios-heredados}). Fuera, no se resuelve, no se borra y quien borra (alumno, evento)
 * sigue adelante.
 */
class ImagenServiceRutasHeredadasTest {

	@TempDir
	Path tempDir;

	private Path base;
	private Path heredada;
	private Path fuera;
	private Path secreto;
	private ImagenServiceImpl imagenService;

	@BeforeEach
	void setUp() throws IOException {
		base = Files.createDirectories(tempDir.resolve("imagenes"));
		heredada = Files.createDirectories(tempDir.resolve("antigua").resolve("imagenes"));
		fuera = Files.createDirectories(tempDir.resolve("trampa"));
		secreto = Files.write(fuera.resolve("secreto.webp"), new byte[] { 9, 9, 9 });
		imagenService = new ImagenServiceImpl();
		ReflectionTestUtils.setField(imagenService, "directorioImagenesLinux", base.toString());
		ReflectionTestUtils.setField(imagenService, "directorioImagenesWindows", base.toString());
		// Con espacios, una vacía y barra final: así llega de una variable de entorno.
		ReflectionTestUtils.setField(imagenService, "directoriosImagenesHeredados",
				" " + tempDir.resolve("no-existe") + " ,, " + heredada + File.separator + " ");
		TenantContext.set(1L);
	}

	@AfterEach
	void limpiar() {
		TenantContext.clear();
	}

	@Test
	void rutaAbsolutaEnLaCarpetaDeImagenes_seResuelveYSeBorra() throws IOException {
		Path fichero = Files.write(Files.createDirectories(base.resolve("alumnos")).resolve("vieja.webp"), new byte[] { 1 });
		Imagen imagen = imagen(1L, fichero.toString());

		assertEquals(fichero.toAbsolutePath().normalize(), imagenService.resolverRutaArchivo(imagen));
		imagenService.eliminarImagenDeSistema(imagen);
		assertFalse(Files.exists(fichero));
	}

	@Test
	void rutaAbsolutaEnUnaCarpetaHeredadaConfigurada_seResuelveYSeBorra() throws IOException {
		Path fichero = Files.write(Files.createDirectories(heredada.resolve("alumnos")).resolve("vieja.webp"),
				new byte[] { 1 });
		Imagen imagen = imagen(2L, fichero.toString());

		assertEquals(fichero.toAbsolutePath().normalize(), imagenService.resolverRutaArchivo(imagen));
		imagenService.eliminarImagenDeSistema(imagen);
		assertFalse(Files.exists(fichero));
	}

	@Test
	void rutaAbsolutaEnUnaCarpetaHeredadaQueAunNoExiste_seResuelveSinFichero() {
		// La fila apunta dentro de una raíz permitida aunque el fichero no esté: el que llama decide (404, nada que borrar).
		Path fichero = heredada.resolve("alumnos").resolve("perdida.webp");
		Imagen imagen = imagen(3L, fichero.toString());

		assertEquals(fichero.toAbsolutePath().normalize(), imagenService.resolverRutaArchivo(imagen));
		assertDoesNotThrow(() -> imagenService.eliminarImagenDeSistema(imagen));
	}

	@Test
	void rutaAbsolutaFueraDeLasCarpetas_niSeResuelveNiSeBorraYAvisaSinLaRuta() {
		Imagen imagen = imagen(77L, secreto.toString());

		List<ILoggingEvent> avisos = conAvisos(() -> {
			assertNull(imagenService.resolverRutaArchivo(imagen));
			assertDoesNotThrow(() -> imagenService.eliminarImagenDeSistema(imagen));
		});

		assertTrue(Files.exists(secreto), "El fichero de fuera no se borra");
		assertFalse(avisos.isEmpty());
		for (ILoggingEvent aviso : avisos) {
			assertEquals(Level.WARN, aviso.getLevel());
			assertTrue(aviso.getFormattedMessage().contains("77"), aviso.getFormattedMessage());
			assertFalse(aviso.getFormattedMessage().contains(fuera.getFileName().toString()), aviso.getFormattedMessage());
			assertFalse(aviso.getFormattedMessage().contains(tempDir.toString()), aviso.getFormattedMessage());
			assertFalse(aviso.getFormattedMessage().contains("secreto"), aviso.getFormattedMessage());
			assertNull(aviso.getThrowableProxy());
		}
	}

	@Test
	void carpetaHermanaConElMismoPrefijo_quedaFuera() throws IOException {
		// «imagenes-otra» empieza como «imagenes» en texto, pero no es una subcarpeta.
		Path hermana = Files.createDirectories(tempDir.resolve(base.getFileName() + "-otra"));
		Path fichero = Files.write(hermana.resolve("x.webp"), new byte[] { 1 });
		Imagen imagen = imagen(4L, fichero.toString());

		assertNull(imagenService.resolverRutaArchivo(imagen));
		imagenService.eliminarImagenDeSistema(imagen);
		assertTrue(Files.exists(fichero));
	}

	@Test
	void laPropiaRaizNoEsUnFichero() {
		for (Path raiz : List.of(base, heredada)) {
			assertNull(imagenService.resolverRutaArchivo(imagen(5L, raiz.toString())), raiz.toString());
		}
	}

	@Test
	void recorridoConPuntos_desdeLaBaseODesdeUnaHeredada_noSaleDeLaCarpeta() {
		String s = File.separator;
		List<String> rutas = List.of(
				base + s + "alumnos" + s + ".." + s + ".." + s + "trampa" + s + "secreto.webp",
				heredada + s + ".." + s + ".." + s + "trampa" + s + "secreto.webp",
				base + "/alumnos/../../trampa/secreto.webp",
				"../trampa/secreto.webp",
				"org-1/../../trampa/secreto.webp");
		for (String ruta : rutas) {
			Imagen imagen = imagen(6L, ruta);
			assertNull(imagenService.resolverRutaArchivo(imagen), ruta);
			assertDoesNotThrow(() -> imagenService.eliminarImagenDeSistema(imagen), ruta);
			assertTrue(Files.exists(secreto), ruta);
		}
	}

	@Test
	void enlaceDeFicheroDentroQueApuntaFuera_noSeResuelveNiBorraElDestino() throws IOException {
		Path alumnos = Files.createDirectories(base.resolve("org-1").resolve("alumnos"));
		Path enlace = alumnos.resolve("enlace.webp");
		crearEnlaceOSaltar(enlace, secreto);

		for (String ruta : List.of(enlace.toString(), "org-1/alumnos/enlace.webp")) {
			Imagen imagen = imagen(8L, ruta);
			assertNull(imagenService.resolverRutaArchivo(imagen), ruta);
			assertDoesNotThrow(() -> imagenService.eliminarImagenDeSistema(imagen), ruta);
			assertTrue(Files.exists(secreto), ruta);
		}
	}

	@Test
	void enlaceDeDirectorioEnUnaHeredadaQueApuntaFuera_noSeResuelveNiBorraElDestino() throws IOException {
		Path enlace = heredada.resolve("alumnos");
		crearEnlaceOSaltar(enlace, fuera);
		Imagen imagen = imagen(9L, enlace.resolve("secreto.webp").toString());

		assertNull(imagenService.resolverRutaArchivo(imagen));
		assertDoesNotThrow(() -> imagenService.eliminarImagenDeSistema(imagen));
		assertTrue(Files.exists(secreto));
	}

	@Test
	void sinCarpetasHeredadasConfiguradas_soloValeLaDeImagenes() throws IOException {
		ReflectionTestUtils.setField(imagenService, "directoriosImagenesHeredados", null);
		Path enHeredada = Files.write(Files.createDirectories(heredada.resolve("alumnos")).resolve("v.webp"),
				new byte[] { 1 });
		Path enBase = Files.write(Files.createDirectories(base.resolve("alumnos")).resolve("v.webp"), new byte[] { 1 });

		assertNull(imagenService.resolverRutaArchivo(imagen(10L, enHeredada.toString())));
		assertEquals(enBase.toAbsolutePath().normalize(), imagenService.resolverRutaArchivo(imagen(11L, enBase.toString())));
	}

	/**
	 * Si el fichero existe, se devuelve su ruta real (sin enlaces): quien lo sirve o lo borra actúa sobre el fichero que
	 * se comprobó, no sobre una ruta con un enlace que podría cambiar entre la comprobación y el uso.
	 */
	@Test
	void ficheroQueExisteTrasUnEnlaceDentroDeLaBase_devuelveSuRutaReal() throws IOException {
		Path destino = Files.createDirectories(base.resolve("org-1").resolve("reales"));
		Path fichero = Files.write(destino.resolve("x.webp"), new byte[] { 1 });
		crearEnlaceOSaltar(base.resolve("org-1").resolve("alumnos"), destino);

		for (String ruta : List.of("org-1/alumnos/x.webp", base.resolve("org-1/alumnos/x.webp").toString())) {
			Path resuelta = imagenService.resolverRutaArchivo(imagen(13L, ruta));
			assertEquals(fichero.toRealPath(), resuelta, ruta);
			assertEquals(fichero.toRealPath().toString(), resuelta.toString(), ruta);
		}
	}

	@Test
	void ficheroQueExisteEscritoConOtrasMayusculasEnWindows_devuelveSuRutaReal() throws IOException {
		Assumptions.assumeTrue(File.separatorChar == '\\', "Solo en Windows: el disco no distingue mayúsculas");
		Path fichero = Files.write(Files.createDirectories(base.resolve("alumnos")).resolve("vieja.webp"), new byte[] { 1 });
		String otraForma = fichero.toString().replace("alumnos", "ALUMNOS").replace("vieja", "VIEJA");

		Path resuelta = imagenService.resolverRutaArchivo(imagen(14L, otraForma));
		assertEquals(fichero.toRealPath().toString(), resuelta.toString());
	}

	@Test
	void ficheroQueNoExiste_devuelveLaRutaSinEnlaces() {
		Path perdida = base.resolve("org-1").resolve("alumnos").resolve("perdida.webp");
		assertEquals(perdida.toAbsolutePath().normalize(),
				imagenService.resolverRutaArchivo(imagen(15L, "org-1/alumnos/perdida.webp")));
	}

	/**
	 * Una carpeta heredada con menos de 2 segmentos ({@code /}, {@code C:\}, {@code /tmp}…) lo abarcaría casi todo: se
	 * ignora con un aviso (una sola vez) y las demás siguen valiendo.
	 */
	@Test
	void carpetaHeredadaDemasiadoAmplia_seIgnoraConUnAvisoYLasDemasSiguen() throws IOException {
		Path raizDelDisco = tempDir.getRoot();
		Path unSegmento = raizDelDisco.resolve(tempDir.getName(0));
		Path enHeredada = Files.write(Files.createDirectories(heredada.resolve("alumnos")).resolve("v.webp"),
				new byte[] { 1 });
		for (Path amplia : List.of(raizDelDisco, unSegmento)) {
			ReflectionTestUtils.setField(imagenService, "directoriosImagenesHeredados", amplia + "," + heredada);

			List<ILoggingEvent> avisos = conAvisos(() -> {
				assertNull(imagenService.resolverRutaArchivo(imagen(16L, secreto.toString())), amplia.toString());
				imagenService.eliminarImagenDeSistema(imagen(16L, secreto.toString()));
			});
			assertTrue(Files.exists(secreto), amplia.toString());
			assertEquals(1, avisos.stream().filter(a -> a.getFormattedMessage().contains("demasiado amplia")).count(),
					amplia.toString());
			assertEquals(enHeredada.toRealPath(), imagenService.resolverRutaArchivo(imagen(17L, enHeredada.toString())));

			List<ILoggingEvent> otraVez = conAvisos(() -> imagenService.resolverRutaArchivo(imagen(18L, secreto.toString())));
			assertTrue(otraVez.stream().noneMatch(a -> a.getFormattedMessage().contains("demasiado amplia")),
					"El aviso sale una vez por valor configurado");
		}
	}

	private static Imagen imagen(Long id, String ruta) {
		Imagen imagen = new Imagen("foto.webp", "image/webp", null, ruta);
		imagen.setId(id);
		return imagen;
	}

	private static List<ILoggingEvent> conAvisos(Runnable accion) {
		Logger logger = (Logger) LoggerFactory.getLogger(ImagenServiceImpl.class);
		ListAppender<ILoggingEvent> registro = new ListAppender<>();
		registro.start();
		logger.addAppender(registro);
		try {
			accion.run();
		} finally {
			logger.detachAppender(registro);
		}
		return registro.list;
	}

	private static void crearEnlaceOSaltar(Path enlace, Path destino) {
		try {
			Files.createSymbolicLink(enlace, destino);
		} catch (IOException | UnsupportedOperationException | SecurityException ex) {
			Assumptions.assumeTrue(false, "Sin permiso para crear enlaces simbólicos: " + ex.getClass().getSimpleName());
		}
	}
}
