package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.archivos.CuerpoCrudo;
import com.taemoi.project.catalogo.CatalogoTaemoiEnMemoria;
import com.taemoi.project.dtos.response.MaterialGestionDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.AuditoriaAccion;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.exceptions.ArchivoNoValidoException;
import com.taemoi.project.exceptions.MaterialExamenException;
import com.taemoi.project.legado.TipoGrado;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.organizacion.TransaccionEnEscuela;
import com.taemoi.project.services.AlumnoDeporteService;
import com.taemoi.project.services.AuditoriaService;
import com.taemoi.project.services.MaterialExamenGestionService;
import com.taemoi.project.services.MaterialExamenService.BloqueEnDisco;
import com.taemoi.project.services.MaterialExamenService.DocumentoEnDisco;
import com.taemoi.project.services.MaterialExamenService.VideoEnDisco;
import com.taemoi.project.services.impl.MaterialExamenServiceImpl;

/**
 * Material de examen de la pieza R1 (fase3-e8c-plan.md) sobre una carpeta temporal, con el lector real de las familias.
 * Lo primero es el invariante de Moiskimdo: el mismo resultado de {@code obtenerMaterialExamen}, carácter por carácter,
 * antes y después de cualquier escritura que no cambie el material.
 */
class MaterialExamenGestionServiceTest {

	private static final String TKD = "TAEKWONDO";
	private static final String KB = "KICKBOXING";
	private static final String B01 = "b01_inicio_a_amarillo";
	private static final String B02 = "b02_amarillo_a_naranja";
	private static final String B03 = "b03_naranja_a_verde";
	private static final String B11 = "b11_negro_5_a_maximo";
	private static final String B11_LEGADO = "b11_negro_5_a_negro_6";

	@TempDir
	Path raiz;

	private final ObjectMapper json = new ObjectMapper();
	private final AlumnoDeporteService alumnoDeportes = mock(AlumnoDeporteService.class);
	private final AuditoriaService auditoria = mock(AuditoriaService.class);
	private CatalogoTaemoiEnMemoria catalogo;
	private LectorDePrueba lectura;
	private MaterialExamenGestionService gestion;

	/**
	 * El lector real con lo que un disco de Windows no admite: dos nombres que solo difieren en mayúsculas. {@code nombres}
	 * cambia el nombre con el que se lee un fichero; su ruta sigue siendo la real.
	 */
	private class LectorDePrueba extends MaterialExamenServiceImpl {
		final Map<String, String> nombres = new HashMap<>();
		/** Se ejecuta una vez, justo después de resolver la carpeta: la adopción que llega a mitad de una lectura. */
		Runnable alResolverLaCarpeta;
		int carpetasResueltas;

		@Override
		public BloqueEnDisco leerBloque(String disciplina, String bloqueId) {
			return renombrado(super.leerBloque(disciplina, bloqueId));
		}

		@Override
		protected Path obtenerCarpetaBloque(String deporte, String bloqueId) {
			Path carpeta = super.obtenerCarpetaBloque(deporte, bloqueId);
			carpetasResueltas++;
			if (alResolverLaCarpeta != null) {
				Runnable intercalado = alResolverLaCarpeta;
				alResolverLaCarpeta = null;
				intercalado.run();
			}
			return carpeta;
		}

		BloqueEnDisco renombrado(BloqueEnDisco disco) {
			return new BloqueEnDisco(disco.carpeta(), disco.temario(),
					disco.videos().stream()
							.map(v -> new VideoEnDisco(nombres.getOrDefault(v.fichero(), v.fichero()), v.titulo(), v.orden(),
									v.ruta()))
							.toList(),
					disco.documentos().stream()
							.map(d -> new DocumentoEnDisco(nombres.getOrDefault(d.fichero(), d.fichero()), d.titulo(), d.mime(),
									d.ruta()))
							.toList());
		}
	}

	/** Doble con lo que un test no puede provocar de verdad: los movimientos, el borrado y el espacio en disco. */
	private class GestionDePrueba extends MaterialExamenGestionService {
		IOException falloAlMover;
		/** Destinos de {@link #moverFichero} que fallan, por nombre de fichero. */
		java.util.function.Predicate<String> falloAlPublicar = nombre -> false;
		final List<String> publicados = new ArrayList<>();
		IOException falloAlBorrar;
		long espacio = Long.MAX_VALUE;

		GestionDePrueba() {
			super(catalogo.catalogo(), catalogo.progresion(), lectura, auditoria, transaccionDirecta(), json,
					raiz.toString(), raiz.toString(), 1L);
		}

		@Override
		protected void moverCarpeta(Path origen, Path destino) throws IOException {
			if (falloAlMover != null) {
				throw falloAlMover;
			}
			super.moverCarpeta(origen, destino);
		}

		@Override
		protected void moverFichero(Path origen, Path destino) throws IOException {
			String nombre = destino.getFileName().toString();
			publicados.add(nombre);
			if (falloAlPublicar.test(nombre)) {
				throw new IOException("sin poder publicar " + nombre);
			}
			super.moverFichero(origen, destino);
		}

		@Override
		protected void borrarFichero(Path fichero) throws IOException {
			if (falloAlBorrar != null) {
				throw falloAlBorrar;
			}
			super.borrarFichero(fichero);
		}

		@Override
		protected long espacioLibre() {
			return espacio;
		}
	}

	private GestionDePrueba doble;

	@BeforeEach
	void preparar() {
		catalogo = new CatalogoTaemoiEnMemoria();
		lectura = new LectorDePrueba();
		ReflectionTestUtils.setField(lectura, "alumnoDeporteService", alumnoDeportes);
		ReflectionTestUtils.setField(lectura, "catalogo", catalogo.catalogo());
		ReflectionTestUtils.setField(lectura, "progresion", catalogo.progresion());
		ReflectionTestUtils.setField(lectura, "objectMapper", json);
		ReflectionTestUtils.setField(lectura, "directorioDocumentosWindows", raiz.toString());
		ReflectionTestUtils.setField(lectura, "directorioDocumentosLinux", raiz.toString());
		ReflectionTestUtils.setField(lectura, "organizacionPorDefecto", 1L);
		doble = new GestionDePrueba();
		gestion = doble;
		TenantContext.set(1L);
	}

	@AfterEach
	void limpiar() {
		TenantContext.clear();
	}

	/** En los tests no hay sesión de Hibernate que soltar: la acción se ejecuta tal cual. */
	@SuppressWarnings("unchecked")
	private static TransaccionEnEscuela transaccionDirecta() {
		TransaccionEnEscuela transaccion = mock(TransaccionEnEscuela.class);
		lenient().when(transaccion.leer(any(), any()))
				.thenAnswer(invocacion -> ((Supplier<Object>) invocacion.getArgument(1)).get());
		lenient().when(transaccion.escribir(any(), any()))
				.thenAnswer(invocacion -> ((Supplier<Object>) invocacion.getArgument(1)).get());
		return transaccion;
	}

	// ------------------------------------------------------------------ invariante de Moiskimdo

	@Test
	void adoptar_mismoResultadoExacto_enUnBloqueConPrefijosYSinIndice() throws Exception {
		Path legada = bloqueLegado(TKD, B03);
		video(legada, "02_tecnicas_base_COMP.mp4");
		video(legada, "01_presentacion.mp4");
		video(legada, "sin_prefijo.mov");
		video(legada, "10-Pumse Il Jang.m4v");
		pdf(legada.resolve("temario").resolve("temario.pdf"));
		pdf(legada.resolve("documentacion").resolve("02_reglamento.pdf"));
		Files.writeString(legada.resolve("documentacion").resolve("01_glosario.docx"), "docx");
		alumno(40L, TKD, TipoGrado.NARANJA);
		String antes = instantanea(40L, TKD);

		gestion.cambiarTitulo(TKD, B03, "01_presentacion.mp4", "presentacion");

		assertFalse(Files.exists(legada), "la carpeta heredada se mueve entera");
		assertTrue(Files.isDirectory(propia(1L, "taekwondo", B03).resolve("videos")));
		assertEquals(antes, instantanea(40L, TKD));
		assertTrue(antes.contains("/api/alumnos/40/deportes/TAEKWONDO/material-examen/videos/10-Pumse%20Il%20Jang.m4v"));
	}

	@Test
	void adoptarUnBloqueAnterior_noCambiaLosVideosAnteriores() throws Exception {
		Path anterior = bloqueLegado(TKD, B01);
		video(anterior, "01_taeguk_il_jang.mp4");
		video(anterior, "02_sogui.mp4");
		Path amarillo = bloqueLegado(TKD, B02);
		video(amarillo, "01_amarillo.mp4");
		Path actual = bloqueLegado(TKD, B03);
		video(actual, "01_naranja.mp4");
		alumno(41L, TKD, TipoGrado.NARANJA);
		String antes = instantanea(41L, TKD);

		gestion.cambiarTitulo(TKD, B01, "01_taeguk_il_jang.mp4", "taeguk il jang");

		assertEquals(antes, instantanea(41L, TKD));
		assertTrue(antes.contains("anterior__b01_inicio_a_amarillo__01_taeguk_il_jang.mp4"));
	}

	@Test
	void bloque11_mismoResultadoAntesYDespues_conLaCarpetaPropiaConElId() throws Exception {
		Path legada = bloqueLegado(TKD, B11_LEGADO);
		video(legada, "01_kukkiwon.mp4");
		video(legada, "02_hansu.mp4");
		pdf(legada.resolve("temario").resolve("temario.pdf"));
		alumno(42L, TKD, TipoGrado.NEGRO_5_DAN);
		String antes = instantanea(42L, TKD);
		assertTrue(antes.contains("01_kukkiwon.mp4"), "el alias se lee antes de adoptar");

		gestion.cambiarTitulo(TKD, B11, "01_kukkiwon.mp4", "kukkiwon");

		assertFalse(Files.exists(legada));
		assertTrue(Files.isDirectory(propia(1L, "taekwondo", B11).resolve("videos")));
		assertFalse(Files.exists(propia(1L, "taekwondo", B11_LEGADO)));
		assertEquals(antes, instantanea(42L, TKD));
	}

	@Test
	void laPrimeraEscritura_generaElIndiceConTodosLosVideosYLoQueYaSeLeia() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		video(legada, "02_SOGUIS_COMP.mp4");
		video(legada, "01_Saludo.mp4");
		video(legada, "sin_prefijo.webm");

		gestion.cambiarTitulo(TKD, B01, "01_Saludo.mp4", "El saludo");

		JsonNode indice = json.readTree(propia(1L, "taekwondo", B01).resolve("index.json").toFile()).get("videos");
		assertEquals(3, indice.size());
		assertEquals("01_Saludo.mp4", indice.get(0).get("file").asText());
		assertEquals("El saludo", indice.get(0).get("title").asText());
		assertEquals(1, indice.get(0).get("order").asInt());
		assertEquals("02_SOGUIS_COMP.mp4", indice.get(1).get("file").asText());
		assertEquals("SOGUIS", indice.get(1).get("title").asText());
		assertEquals(2, indice.get(1).get("order").asInt());
		assertEquals("sin_prefijo.webm", indice.get(2).get("file").asText());
		assertEquals("sin prefijo", indice.get(2).get("title").asText());
		assertEquals(10_000, indice.get(2).get("order").asInt());
	}

	@Test
	void unaSubida_vaAlFinalYLoDemasNoCambia() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		video(legada, "01_saludo.mp4");
		video(legada, "02_soguis.mp4");
		alumno(43L, TKD, TipoGrado.BLANCO);
		JsonNode antes = json.readTree(instantanea(43L, TKD)).get("videos");

		MaterialGestionDTO.Subida subida = gestion.subirVideo(TKD, B01, "Técnica de pierna.mp4", cuerpoVideo(100));

		assertEquals("Tecnica_de_pierna.mp4", subida.fichero());
		JsonNode despues = json.readTree(instantanea(43L, TKD)).get("videos");
		assertEquals(3, despues.size());
		assertEquals(antes.get(0), despues.get(0));
		assertEquals(antes.get(1), despues.get(1));
		assertEquals("Técnica de pierna", despues.get(2).get("title").asText());
		assertEquals(3, despues.get(2).get("order").asInt());
	}

	@Test
	void lasLecturasNuncaAdoptan() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		video(legada, "01_saludo.mp4");
		pdf(legada.resolve("temario").resolve("temario.pdf"));
		pdf(legada.resolve("documentacion").resolve("01_guia.pdf"));

		gestion.listar();
		gestion.detalle(TKD, B01);
		gestion.temario(TKD, B01);
		gestion.video(TKD, B01, "01_saludo.mp4");
		gestion.documento(TKD, B01, "01_guia.pdf");

		assertTrue(Files.isDirectory(legada));
		assertFalse(Files.exists(raiz.resolve("org-1")), "leer no crea carpetas");
	}

	@Test
	void adopcionFallida_laEscrituraFallaYElMaterialQuedaIntacto() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		video(legada, "01_saludo.mp4");
		alumno(44L, TKD, TipoGrado.BLANCO);
		String antes = instantanea(44L, TKD);
		doble.falloAlMover = new AtomicMoveNotSupportedException("a", "b", "otro sistema de ficheros");

		MaterialExamenException error = assertThrows(MaterialExamenException.class,
				() -> gestion.subirVideo(TKD, B01, "nuevo.mp4", cuerpoVideo(100)));

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, error.getEstado());
		assertTrue(Files.isRegularFile(legada.resolve("videos").resolve("01_saludo.mp4")));
		assertFalse(Files.exists(propia(1L, "taekwondo", B01)), "nunca una carpeta propia vacía");
		assertFalse(Files.exists(legada.resolve("index.json")));
		assertEquals(antes, instantanea(44L, TKD));
		verify(auditoria, never()).registrarAccesoSensible(any(), any(), any(), any(), any());
	}

	@Test
	void sinPoderCrearLosPadres_laEscrituraFallaYLaLegadaSigue() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		video(legada, "01_saludo.mp4");
		Files.createDirectories(raiz.resolve("org-1"));
		Files.writeString(raiz.resolve("org-1").resolve("materiales"), "esto es un fichero");

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, estadoDe(
				() -> gestion.cambiarTitulo(TKD, B01, "01_saludo.mp4", "Saludo")));

		assertTrue(Files.isRegularFile(legada.resolve("videos").resolve("01_saludo.mp4")));
	}

	@Test
	void propiaYLegadaALaVez_noSeMezcla() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		video(legada, "01_legado.mp4");
		Path propia = propia(1L, "taekwondo", B01);
		video(propia, "01_propio.mp4");

		MaterialExamenException error = assertThrows(MaterialExamenException.class,
				() -> gestion.cambiarTitulo(TKD, B01, "01_propio.mp4", "Otro"));

		assertEquals(HttpStatus.CONFLICT, error.getEstado());
		assertTrue(error.getMessage().contains("dos carpetas"));
		assertEquals(Set.of("01_legado.mp4"), nombres(legada.resolve("videos")));
		assertEquals(Set.of("01_propio.mp4"), nombres(propia.resolve("videos")));
		assertFalse(Files.exists(propia.resolve("index.json")));
	}

	@Test
	void sinLegada_seCreaLaPropia() throws Exception {
		MaterialGestionDTO.Subida subida = gestion.subirVideo(KB, B01, "01_guardia.mp4", cuerpoVideo(100));

		assertTrue(Files.isRegularFile(propia(1L, "kickboxing", B01).resolve("videos").resolve(subida.fichero())));
	}

	@Test
	void escriturasConcurrentes_noPierdenEntradas() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		video(legada, "01_saludo.mp4");
		int hilos = 8;
		CyclicBarrier salida = new CyclicBarrier(hilos);
		ExecutorService ejecutor = Executors.newFixedThreadPool(hilos);
		List<Future<String>> futuros = new ArrayList<>();
		for (int i = 0; i < hilos; i++) {
			// La mitad con el mismo nombre: la colisión también se resuelve bajo el bloqueo.
			String nombre = i % 2 == 0 ? "repetido.mp4" : "video_" + i + ".mp4";
			futuros.add(ejecutor.submit(() -> {
				TenantContext.set(1L);
				try {
					salida.await();
					return gestion.subirVideo(TKD, B01, nombre, cuerpoVideo(5_000)).fichero();
				} finally {
					TenantContext.clear();
				}
			}));
		}
		Set<String> subidos = new TreeSet<>();
		for (Future<String> futuro : futuros) {
			subidos.add(futuro.get());
		}
		ejecutor.shutdown();

		assertEquals(hilos, subidos.size());
		Path carpeta = propia(1L, "taekwondo", B01);
		JsonNode indice = json.readTree(carpeta.resolve("index.json").toFile()).get("videos");
		assertEquals(hilos + 1, indice.size());
		assertEquals(hilos + 1, nombres(carpeta.resolve("videos")).size());
		Set<Integer> ordenes = new TreeSet<>();
		indice.forEach(entrada -> ordenes.add(entrada.get("order").asInt()));
		assertEquals(hilos + 1, ordenes.size(), "sin órdenes repetidos");
		sinTemporales(carpeta);
	}

	@Test
	void subidaCortadaPorElCliente_noDejaTemporal() throws Exception {
		Path carpeta = bloqueLegado(TKD, B01);
		video(carpeta, "01_saludo.mp4");
		byte[] inicio = mp4(4_096);
		InputStream cortado = new InputStream() {
			private final InputStream datos = new ByteArrayInputStream(inicio);

			@Override
			public int read() throws IOException {
				int leido = datos.read();
				if (leido < 0) {
					throw new IOException("el cliente cortó");
				}
				return leido;
			}
		};

		assertEquals(HttpStatus.BAD_REQUEST, estadoDe(() -> gestion.subirVideo(TKD, B01, "nuevo.mp4",
				new CuerpoCrudo(cortado, 1_000_000L, "video/mp4"))));

		Path propia = propia(1L, "taekwondo", B01);
		sinTemporales(propia);
		assertEquals(Set.of("01_saludo.mp4"), nombres(propia.resolve("videos")));
		assertFalse(Files.exists(propia.resolve("index.json")), "sin entrada huérfana");
	}

	@Test
	void cuerpoMasCortoQueLoDeclarado_seRechazaYNoDejaTemporal() throws Exception {
		assertEquals(HttpStatus.BAD_REQUEST, estadoDe(() -> gestion.subirVideo(KB, B01, "nuevo.mp4",
				new CuerpoCrudo(new ByteArrayInputStream(mp4(2_000)), 3_000L, "video/mp4"))));

		Path videos = propia(1L, "kickboxing", B01).resolve("videos");
		assertEquals(Set.of(), nombres(videos));
		sinTemporales(propia(1L, "kickboxing", B01));
	}

	@Test
	void sinContentLength_411() {
		assertEquals(HttpStatus.LENGTH_REQUIRED, estadoDe(() -> gestion.subirVideo(KB, B01, "nuevo.mp4",
				new CuerpoCrudo(new ByteArrayInputStream(mp4(100)), -1L, "video/mp4"))));
		assertEquals(HttpStatus.LENGTH_REQUIRED, estadoDe(() -> gestion.subirTemario(KB, B01,
				new CuerpoCrudo(new ByteArrayInputStream(pdfBytes()), -1L, "application/pdf"))));
	}

	@Test
	void demasiadoGrande_413_sinLeerNiAdoptar() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		video(legada, "01_saludo.mp4");
		InputStream prohibido = new InputStream() {
			@Override
			public int read() {
				throw new AssertionError("no se lee el cuerpo");
			}
		};

		assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, estadoDe(() -> gestion.subirVideo(TKD, B01, "nuevo.mp4",
				new CuerpoCrudo(prohibido, MaterialExamenGestionService.MAX_BYTES_VIDEO + 1, "video/mp4"))));
		assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, estadoDe(() -> gestion.subirTemario(TKD, B01,
				new CuerpoCrudo(prohibido, MaterialExamenGestionService.MAX_BYTES_DOCUMENTO + 1, "application/pdf"))));
		assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, estadoDe(() -> gestion.subirDocumento(TKD, B01, "a.pdf",
				new CuerpoCrudo(prohibido, MaterialExamenGestionService.MAX_BYTES_DOCUMENTO + 1, "application/pdf"))));

		assertEquals(524_288_000L, MaterialExamenGestionService.MAX_BYTES_VIDEO);
		assertEquals(52_428_800L, MaterialExamenGestionService.MAX_BYTES_DOCUMENTO);
		assertTrue(Files.isDirectory(legada));
	}

	@Test
	void masBytesQueElTope_seCuentanAlLeer_413() {
		// La longitud declarada cabe; el flujo trae más: el tope es el contado, no el declarado.
		InputStream sinFin = new InputStream() {
			private final byte[] cabecera = mp4(16);
			private int posicion;

			@Override
			public int read() {
				return posicion < cabecera.length ? cabecera[posicion++] & 0xff : 0;
			}
		};

		assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, estadoDe(() -> gestion.subirDocumento(KB, B01, "guia.pdf",
				new CuerpoCrudo(prefijadoConPdf(sinFin), MaterialExamenGestionService.MAX_BYTES_DOCUMENTO,
						"application/pdf"))));

		sinTemporales(propia(1L, "kickboxing", B01));
	}

	@Test
	void espacioLibreInsuficiente_507_antesDeAdoptar() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		video(legada, "01_saludo.mp4");
		doble.espacio = 2L * 1024 * 1024 * 1024 + 99;

		assertEquals(HttpStatus.INSUFFICIENT_STORAGE,
				estadoDe(() -> gestion.subirVideo(TKD, B01, "nuevo.mp4", cuerpoVideo(100))));
		assertTrue(Files.isDirectory(legada));

		doble.espacio = 2L * 1024 * 1024 * 1024 + 100;
		gestion.subirVideo(TKD, B01, "nuevo.mp4", cuerpoVideo(100));
	}

	@Test
	void cabeceraFalsa_seRechazaSinDejarNada() throws Exception {
		byte[] falso = "esto no es un video, solo texto con extension de video".getBytes(StandardCharsets.UTF_8);

		assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, estadoDe(() -> gestion.subirVideo(KB, B01, "nuevo.mp4",
				new CuerpoCrudo(new ByteArrayInputStream(falso), falso.length, "video/mp4"))));
		assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, estadoDe(() -> gestion.subirVideo(KB, B01, "nuevo.webm",
				new CuerpoCrudo(new ByteArrayInputStream(mp4(100)), 100, "video/webm"))));
		assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, estadoDe(() -> gestion.subirTemario(KB, B01,
				new CuerpoCrudo(new ByteArrayInputStream(falso), falso.length, "application/pdf"))));

		sinTemporales(propia(1L, "kickboxing", B01));
		assertEquals(Set.of(), nombres(propia(1L, "kickboxing", B01).resolve("videos")));
	}

	@ParameterizedTest
	@ValueSource(strings = { "ftyp", "moov", "mdat", "wide", "free" })
	void cabecerasDeVideoAdmitidas(String caja) {
		byte[] contenido = new byte[64];
		System.arraycopy(caja.getBytes(StandardCharsets.US_ASCII), 0, contenido, 4, 4);

		gestion.subirVideo(KB, B01, caja + ".mov", new CuerpoCrudo(new ByteArrayInputStream(contenido), 64, "video/quicktime"));

		assertTrue(Files.isRegularFile(propia(1L, "kickboxing", B01).resolve("videos").resolve(caja + ".mov")));
	}

	@Test
	void webmConEbml_seAdmite() {
		byte[] contenido = new byte[64];
		contenido[0] = 0x1A;
		contenido[1] = 0x45;
		contenido[2] = (byte) 0xDF;
		contenido[3] = (byte) 0xA3;

		assertEquals("a.webm", gestion.subirVideo(KB, B01, "a.webm",
				new CuerpoCrudo(new ByteArrayInputStream(contenido), 64, "video/webm")).fichero());
	}

	@Test
	void contenidoDeFormulario_415_sinLeerElCuerpo() {
		assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, estadoDe(() -> gestion.subirVideo(KB, B01, "a.mp4",
				new CuerpoCrudo(new ByteArrayInputStream(mp4(100)), 100, "application/x-www-form-urlencoded"))));
		assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, estadoDe(() -> gestion.subirVideo(KB, B01, "a.mp4",
				new CuerpoCrudo(new ByteArrayInputStream(mp4(100)), 100, "multipart/form-data; boundary=x"))));
	}

	@ParameterizedTest
	@ValueSource(strings = { "../fuera.mp4", "..\\fuera.mp4", "a/b.mp4", "a\\b.mp4", ".oculto.mp4", "..", ".mp4",
			"   ", "video.exe", "video", "a..b.mp4", "video\u0000.mp4" })
	void nombreNoValido_400(String nombre) throws Exception {
		assertEquals(HttpStatus.BAD_REQUEST, estadoDe(() -> gestion.subirVideo(KB, B01, nombre, cuerpoVideo(100))));
		assertFalse(Files.exists(raiz.resolve("org-1")));
	}

	@Test
	void nombreNulo_400() {
		assertEquals(HttpStatus.BAD_REQUEST, estadoDe(() -> gestion.subirVideo(KB, B01, null, cuerpoVideo(100))));
	}

	@ParameterizedTest
	@ValueSource(strings = { "../fuera.mp4", "a/b.mp4", "..\\x.mp4", ".subida-1" })
	void ficheroGuardadoConNombreNoValido_400(String fichero) {
		assertEquals(HttpStatus.BAD_REQUEST, estadoDe(() -> gestion.video(KB, B01, fichero)));
		assertEquals(HttpStatus.BAD_REQUEST, estadoDe(() -> gestion.eliminarVideo(KB, B01, fichero)));
		assertEquals(HttpStatus.BAD_REQUEST, estadoDe(() -> gestion.documento(KB, B01, fichero)));
		assertEquals(HttpStatus.BAD_REQUEST, estadoDe(() -> gestion.cambiarTitulo(KB, B01, fichero, "Título")));
	}

	@Test
	void colisionDeNombre_sufijoSinSobrescribir_tambienSinDistinguirMayusculas() throws Exception {
		gestion.subirVideo(KB, B01, "guardia.mp4", cuerpoVideo(100));
		byte[] original = Files.readAllBytes(propia(1L, "kickboxing", B01).resolve("videos").resolve("guardia.mp4"));

		assertEquals("guardia_2.mp4", gestion.subirVideo(KB, B01, "guardia.mp4", cuerpoVideo(200)).fichero());
		assertEquals("GUARDIA_3.mp4", gestion.subirVideo(KB, B01, "GUARDIA.mp4", cuerpoVideo(300)).fichero());

		assertArrayEquals(original,
				Files.readAllBytes(propia(1L, "kickboxing", B01).resolve("videos").resolve("guardia.mp4")));
	}

	@Test
	void sustituir_conservaTituloYOrden() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		video(legada, "01_saludo.mp4");
		video(legada, "02_soguis.mp4");
		gestion.cambiarTitulo(TKD, B01, "02_soguis.mp4", "Posiciones");

		MaterialGestionDTO.Detalle detalle = gestion.sustituirVideo(TKD, B01, "02_soguis.mp4", cuerpoVideo(777));

		MaterialGestionDTO.VideoDetalle video = detalle.videos().get(1);
		assertEquals("02_soguis.mp4", video.fichero());
		assertEquals("Posiciones", video.titulo());
		assertEquals(2, video.orden());
		assertEquals(777L, video.bytes());
		sinTemporales(propia(1L, "taekwondo", B01));
	}

	@Test
	void sustituirUnoQueNoExiste_404_sinLeerNiAdoptar() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		video(legada, "01_saludo.mp4");

		assertThrows(NoSuchElementException.class,
				() -> gestion.sustituirVideo(TKD, B01, "no_existe.mp4", cuerpoVideo(100)));
		assertTrue(Files.isDirectory(legada));
	}

	@Test
	void sustituirConOtroFormato_seRechazaYElOriginalSigue() throws Exception {
		gestion.subirVideo(KB, B01, "guardia.webm", new CuerpoCrudo(new ByteArrayInputStream(webm(100)), 100, "video/webm"));

		assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
				estadoDe(() -> gestion.sustituirVideo(KB, B01, "guardia.webm", cuerpoVideo(300))));

		assertEquals(100L, gestion.detalle(KB, B01).videos().get(0).bytes());
		sinTemporales(propia(1L, "kickboxing", B01));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   " })
	void titulo_vacio_400(String titulo) throws Exception {
		gestion.subirVideo(KB, B01, "guardia.mp4", cuerpoVideo(100));

		assertEquals(HttpStatus.BAD_REQUEST, estadoDe(() -> gestion.cambiarTitulo(KB, B01, "guardia.mp4", titulo)));
	}

	@Test
	void titulo_entre1y120Caracteres() throws Exception {
		gestion.subirVideo(KB, B01, "guardia.mp4", cuerpoVideo(100));

		assertEquals(HttpStatus.BAD_REQUEST,
				estadoDe(() -> gestion.cambiarTitulo(KB, B01, "guardia.mp4", "x".repeat(121))));
		assertEquals(HttpStatus.BAD_REQUEST, estadoDe(() -> gestion.cambiarTitulo(KB, B01, "guardia.mp4", null)));
		String ciento20 = "x".repeat(120);
		assertEquals(ciento20, gestion.cambiarTitulo(KB, B01, "guardia.mp4", "  " + ciento20 + "  ").videos().get(0).titulo());
		assertEquals("a", gestion.cambiarTitulo(KB, B01, "guardia.mp4", "a").videos().get(0).titulo());
	}

	@Test
	void reordenar_permutacionExacta_yLoQueNoLoEs_409() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		video(legada, "01_a.mp4");
		video(legada, "02_b.mp4");
		video(legada, "03_c.mp4");

		for (List<String> incorrecta : List.of(List.of("01_a.mp4", "02_b.mp4"), // falta uno
				List.of("01_a.mp4", "02_b.mp4", "03_c.mp4", "04_d.mp4"), // sobra uno
				List.of("01_a.mp4", "01_a.mp4", "02_b.mp4"), // repetido
				List.of("01_a.mp4", "02_b.mp4", "otro.mp4"), // ajeno
				List.<String>of())) {
			assertEquals(HttpStatus.CONFLICT, estadoDe(() -> gestion.reordenarVideos(TKD, B01, incorrecta)), incorrecta.toString());
		}
		assertEquals(HttpStatus.CONFLICT, estadoDe(() -> gestion.reordenarVideos(TKD, B01, null)));
		assertTrue(Files.isDirectory(legada), "una permutación incorrecta no adopta");

		MaterialGestionDTO.Detalle detalle = gestion.reordenarVideos(TKD, B01, List.of("03_c.mp4", "01_a.mp4", "02_b.mp4"));

		assertEquals(List.of("03_c.mp4", "01_a.mp4", "02_b.mp4"),
				detalle.videos().stream().map(MaterialGestionDTO.VideoDetalle::fichero).toList());
		assertEquals(List.of(1, 2, 3), detalle.videos().stream().map(MaterialGestionDTO.VideoDetalle::orden).toList());
	}

	@Test
	void eliminar_sinFicheroNiEntradaHuerfana() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		video(legada, "01_a.mp4");
		video(legada, "02_b.mp4");
		alumno(45L, TKD, TipoGrado.BLANCO);

		gestion.eliminarVideo(TKD, B01, "01_a.mp4");

		Path carpeta = propia(1L, "taekwondo", B01);
		assertEquals(Set.of("02_b.mp4"), nombres(carpeta.resolve("videos")));
		JsonNode indice = json.readTree(carpeta.resolve("index.json").toFile()).get("videos");
		assertEquals(1, indice.size());
		assertEquals("02_b.mp4", indice.get(0).get("file").asText());
		sinTemporales(carpeta);
		assertEquals(1, json.readTree(instantanea(45L, TKD)).get("videos").size());
	}

	@Test
	void eliminarUnoQueNoExiste_404_sinAdoptar() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		video(legada, "01_a.mp4");

		assertThrows(NoSuchElementException.class, () -> gestion.eliminarVideo(TKD, B01, "no.mp4"));
		assertTrue(Files.isDirectory(legada));
	}

	@Test
	void temario_dejaTemarioPdf_yRetiraElAnteriorConOtroNombre() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		pdf(legada.resolve("temario").resolve("Programa antiguo.pdf"));
		video(legada, "01_a.mp4");
		alumno(46L, TKD, TipoGrado.BLANCO);

		MaterialGestionDTO.Detalle detalle = gestion.subirTemario(TKD, B01, cuerpoPdf());

		Path carpeta = propia(1L, "taekwondo", B01);
		assertEquals(Set.of("temario.pdf"), nombres(carpeta.resolve("temario")));
		assertEquals("temario.pdf", detalle.temario().nombre());
		assertEquals("temario.pdf", lectura.obtenerMaterialExamen(46L, TKD).getTemario().getFileName());
		assertTrue(Files.isRegularFile(carpeta.resolve("index.json")), "toda primera escritura genera el índice");
		sinTemporales(carpeta);
	}

	@Test
	void temario_sustituyeElMismoNombre() throws Exception {
		gestion.subirTemario(KB, B01, cuerpoPdf());
		byte[] nuevo = (new String(pdfBytes(), StandardCharsets.ISO_8859_1) + " nuevo").getBytes(StandardCharsets.ISO_8859_1);

		gestion.subirTemario(KB, B01, new CuerpoCrudo(new ByteArrayInputStream(nuevo), nuevo.length, "application/pdf"));

		assertArrayEquals(nuevo, Files.readAllBytes(propia(1L, "kickboxing", B01).resolve("temario").resolve("temario.pdf")));
	}

	@Test
	void documentos_subirSustituirYEliminar() throws Exception {
		Path carpeta = propia(1L, "kickboxing", B01).resolve("documentacion");

		MaterialGestionDTO.Subida subida = gestion.subirDocumento(KB, B01, "Guía de examen.pdf", cuerpoPdf());
		assertEquals("Guia_de_examen.pdf", subida.fichero());
		assertEquals("Guia_de_examen_2.pdf", gestion.subirDocumento(KB, B01, "Guía de examen.pdf", cuerpoPdf()).fichero());
		MaterialGestionDTO.DocumentoDetalle documento = gestion.detalle(KB, B01).documentos().get(0);
		assertEquals("Guia de examen", documento.titulo());
		assertEquals("application/pdf", documento.mime());

		byte[] nuevo = (new String(pdfBytes(), StandardCharsets.ISO_8859_1) + " otro").getBytes(StandardCharsets.ISO_8859_1);
		gestion.sustituirDocumento(KB, B01, subida.fichero(),
				new CuerpoCrudo(new ByteArrayInputStream(nuevo), nuevo.length, "application/pdf"));
		assertArrayEquals(nuevo, Files.readAllBytes(carpeta.resolve(subida.fichero())));

		gestion.eliminarDocumento(KB, B01, subida.fichero());
		assertEquals(Set.of("Guia_de_examen_2.pdf"), nombres(carpeta));
		sinTemporales(propia(1L, "kickboxing", B01));
		assertThrows(NoSuchElementException.class, () -> gestion.eliminarDocumento(KB, B01, subida.fichero()));
	}

	@Test
	void documentoDeTipoNoPermitido_400ConElMensajeDeLaValidacion() {
		byte[] contenido = "MZ".getBytes(StandardCharsets.US_ASCII);

		ArchivoNoValidoException error = assertThrows(ArchivoNoValidoException.class,
				() -> gestion.subirDocumento(KB, B01, "programa.exe",
						new CuerpoCrudo(new ByteArrayInputStream(contenido), contenido.length, null)));

		assertEquals("Tipo de documento no permitido.", error.getMessage());
	}

	@Test
	void temporalesHuerfanos_masDe24h_seLimpianEnLaSiguienteEscritura() throws Exception {
		gestion.subirVideo(KB, B01, "guardia.mp4", cuerpoVideo(100));
		Path carpeta = propia(1L, "kickboxing", B01);
		Path viejo = carpeta.resolve("videos").resolve(".subida-vieja");
		Path reciente = carpeta.resolve("videos").resolve(".subida-reciente");
		Path viejoRaiz = carpeta.resolve(".subida-indice-viejo");
		Files.writeString(viejo, "x");
		Files.writeString(reciente, "x");
		Files.writeString(viejoRaiz, "x");
		FileTime hace25h = FileTime.from(Instant.now().minus(25, ChronoUnit.HOURS));
		Files.setLastModifiedTime(viejo, hace25h);
		Files.setLastModifiedTime(viejoRaiz, hace25h);

		gestion.cambiarTitulo(KB, B01, "guardia.mp4", "Guardia");

		assertFalse(Files.exists(viejo));
		assertFalse(Files.exists(viejoRaiz));
		assertTrue(Files.exists(reciente), "una subida en curso no se toca");
	}

	@Test
	void losLectoresNoVenLosTemporales() throws Exception {
		gestion.subirVideo(KB, B01, "guardia.mp4", cuerpoVideo(100));
		Path carpeta = propia(1L, "kickboxing", B01);
		Files.writeString(carpeta.resolve("videos").resolve(".subida-x"), "x");
		Files.createDirectories(carpeta.resolve("documentacion"));
		Files.writeString(carpeta.resolve("documentacion").resolve(".subida-y"), "x");

		MaterialGestionDTO.Detalle detalle = gestion.detalle(KB, B01);

		assertEquals(1, detalle.videos().size());
		assertEquals(0, detalle.documentos().size());
	}

	@Test
	void aislamiento_otraEscuelaNoVeNiTocaElLegado() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		video(legada, "01_saludo.mp4");
		pdf(legada.resolve("temario").resolve("temario.pdf"));
		pdf(legada.resolve("documentacion").resolve("01_guia.pdf"));
		Path organizacion1 = propia(1L, "taekwondo", B02);
		video(organizacion1, "01_propio_de_la_1.mp4");

		TenantContext.set(2L);

		MaterialGestionDTO.Detalle detalle = gestion.detalle(TKD, B01);
		assertEquals(0, detalle.videos().size());
		assertEquals(null, detalle.temario());
		assertEquals(0, gestion.detalle(TKD, B02).videos().size());
		assertThrows(NoSuchElementException.class, () -> gestion.temario(TKD, B01));
		assertThrows(NoSuchElementException.class, () -> gestion.video(TKD, B01, "01_saludo.mp4"));
		assertThrows(NoSuchElementException.class, () -> gestion.video(TKD, B02, "01_propio_de_la_1.mp4"));
		assertThrows(NoSuchElementException.class, () -> gestion.documento(TKD, B01, "01_guia.pdf"));
		assertThrows(NoSuchElementException.class, () -> gestion.eliminarVideo(TKD, B01, "01_saludo.mp4"));

		gestion.subirVideo(TKD, B01, "01_saludo.mp4", cuerpoVideo(100));

		assertTrue(Files.isRegularFile(propia(2L, "taekwondo", B01).resolve("videos").resolve("01_saludo.mp4")));
		assertTrue(Files.isRegularFile(legada.resolve("videos").resolve("01_saludo.mp4")), "la legada sigue en su sitio");
		assertFalse(Files.exists(legada.resolve("index.json")));
		assertFalse(Files.exists(propia(1L, "taekwondo", B01)), "la escuela 2 no adopta nada de la 1");
	}

	@Test
	void disciplinaOBloqueFueraDelCatalogo_404() {
		assertThrows(NoSuchElementException.class, () -> gestion.detalle("NATACION", B01));
		assertThrows(NoSuchElementException.class, () -> gestion.detalle(TKD, "b99_no_existe"));
		assertThrows(NoSuchElementException.class, () -> gestion.detalle(TKD, "../" + B01));
		assertThrows(NoSuchElementException.class, () -> gestion.subirVideo(TKD, "b99_no_existe", "a.mp4", cuerpoVideo(100)));
		// Disciplina sin sistema de grados: no tiene bloques.
		assertThrows(NoSuchElementException.class, () -> gestion.detalle("PILATES", B01));
	}

	@Test
	void disciplinaEnMinusculas_seResuelve() {
		assertEquals(TKD, gestion.detalle("taekwondo", B01).disciplina());
	}

	@Test
	void listar_conGradosDelCatalogoNombresYTotales() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		Files.createDirectories(legada.resolve("videos"));
		Files.write(legada.resolve("videos").resolve("01_a.mp4"), new byte[10]);
		Files.write(legada.resolve("videos").resolve("02_b.mp4"), new byte[20]);
		Files.createDirectories(legada.resolve("temario"));
		Files.write(legada.resolve("temario").resolve("temario.pdf"), new byte[5]);
		Files.createDirectories(legada.resolve("documentacion"));
		Files.write(legada.resolve("documentacion").resolve("guia.pdf"), new byte[1]);
		Files.writeString(legada.resolve("documentacion").resolve(".gitkeep"), "no cuenta");
		Files.createDirectories(bloqueLegado(TKD, B11_LEGADO).resolve("videos"));
		Files.write(bloqueLegado(TKD, B11_LEGADO).resolve("videos").resolve("01_c.mp4"), new byte[7]);

		MaterialGestionDTO.Listado listado = gestion.listar();

		assertEquals(List.of(TKD, KB), listado.disciplinas().stream().map(MaterialGestionDTO.DisciplinaConBloques::codigo).toList());
		MaterialGestionDTO.DisciplinaConBloques tkd = listado.disciplinas().get(0);
		assertEquals(11, tkd.bloques().size());
		MaterialGestionDTO.BloqueResumen primero = tkd.bloques().get(0);
		assertEquals(B01, primero.id());
		assertEquals("Inicio a amarillo", primero.nombre());
		assertEquals(List.of("BLANCO", "BLANCO_AMARILLO"),
				primero.grados().stream().map(MaterialGestionDTO.GradoDelBloque::codigo).toList());
		assertEquals("BLANCO-AMARILLO", primero.grados().get(1).nombre());
		assertTrue(primero.tieneTemario());
		assertEquals(2, primero.videos());
		assertEquals(36L, primero.bytes());
		MaterialGestionDTO.BloqueResumen ultimo = tkd.bloques().get(10);
		assertEquals("Negro 5.º en adelante", ultimo.nombre());
		assertEquals(1, ultimo.videos());
		assertEquals(3, tkd.totales().videos());
		assertEquals(43L, tkd.totales().bytes());
		assertEquals(1, tkd.totales().temarios());
		assertEquals(3, listado.totales().videos());
		assertEquals(43L, listado.totales().bytes());
	}

	@Test
	void nombresDeBloque_losDelBoceto_yUnoLegibleParaOtroId() {
		assertEquals("Inicio a amarillo", MaterialExamenGestionService.nombreDeBloque(B01));
		assertEquals("Amarillo a naranja", MaterialExamenGestionService.nombreDeBloque(B02));
		assertEquals("Naranja a verde", MaterialExamenGestionService.nombreDeBloque(B03));
		assertEquals("Verde a azul", MaterialExamenGestionService.nombreDeBloque("b04_verde_a_azul"));
		assertEquals("Azul a rojo", MaterialExamenGestionService.nombreDeBloque("b05_azul_a_rojo"));
		assertEquals("Rojo a negro", MaterialExamenGestionService.nombreDeBloque("b06_rojo_a_negro_1"));
		assertEquals("Negro 1.º a 2.º", MaterialExamenGestionService.nombreDeBloque("b07_negro_1_a_negro_2"));
		assertEquals("Negro 2.º a 3.º", MaterialExamenGestionService.nombreDeBloque("b08_negro_2_a_negro_3"));
		assertEquals("Negro 3.º a 4.º", MaterialExamenGestionService.nombreDeBloque("b09_negro_3_a_negro_4"));
		assertEquals("Negro 4.º a 5.º", MaterialExamenGestionService.nombreDeBloque("b10_negro_4_a_negro_5"));
		assertEquals("Negro 5.º en adelante", MaterialExamenGestionService.nombreDeBloque(B11));
		assertEquals("Tatami avanzado", MaterialExamenGestionService.nombreDeBloque("b12_tatami_avanzado"));
		assertEquals("Competicion", MaterialExamenGestionService.nombreDeBloque("competicion"));
	}

	@Test
	void detalle_tambienEnSuperiores_soloTaegukOPumseYSoloEnTaekwondo() throws Exception {
		Path tkd = bloqueLegado(TKD, B01);
		video(tkd, "01_taeguk_il_jang.mp4");
		video(tkd, "02_sogui.mp4");
		gestion.cambiarTitulo(TKD, B01, "02_sogui.mp4", "Pumse de pie");
		video(bloqueLegado(KB, B01), "01_taeguk_il_jang.mp4");

		MaterialGestionDTO.Detalle detalle = gestion.detalle(TKD, B01);
		assertEquals(List.of(true, true),
				detalle.videos().stream().map(MaterialGestionDTO.VideoDetalle::tambienEnSuperiores).toList());
		assertEquals("Inicio a amarillo", detalle.nombre());
		assertFalse(gestion.detalle(KB, B01).videos().get(0).tambienEnSuperiores());
	}

	@Test
	void detalle_conBytesYFechasEnIso() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		video(legada, "01_a.mp4");
		pdf(legada.resolve("temario").resolve("temario.pdf"));
		pdf(legada.resolve("documentacion").resolve("guia.pdf"));

		MaterialGestionDTO.Detalle detalle = gestion.detalle(TKD, B01);

		assertEquals(detalle.temario().bytes(), pdfBytes().length);
		assertTrue(detalle.temario().fecha().endsWith("Z"));
		Instant.parse(detalle.videos().get(0).fecha());
		Instant.parse(detalle.documentos().get(0).fecha());
	}

	@Test
	void cadaEscritura_dejaSuRegistroEnElModuloMateriales() throws Exception {
		String base = "/api/material-examen/KICKBOXING/" + B01;

		gestion.subirVideo(KB, B01, "guardia.mp4", cuerpoVideo(100));
		verify(auditoria).registrarAccesoSensible(eq(AuditoriaAccion.CREATE), eq("materiales"), eq(base + "/videos"),
				isNull(), contains("guardia.mp4"));

		gestion.sustituirVideo(KB, B01, "guardia.mp4", cuerpoVideo(100));
		verify(auditoria).registrarAccesoSensible(eq(AuditoriaAccion.UPDATE), eq("materiales"),
				eq(base + "/videos/guardia.mp4/contenido"), isNull(), anyString());

		gestion.cambiarTitulo(KB, B01, "guardia.mp4", "Guardia");
		verify(auditoria).registrarAccesoSensible(eq(AuditoriaAccion.UPDATE), eq("materiales"),
				eq(base + "/videos/guardia.mp4"), isNull(), anyString());

		gestion.reordenarVideos(KB, B01, List.of("guardia.mp4"));
		verify(auditoria).registrarAccesoSensible(eq(AuditoriaAccion.UPDATE), eq("materiales"), eq(base + "/videos/orden"),
				isNull(), anyString());

		gestion.subirTemario(KB, B01, cuerpoPdf());
		verify(auditoria).registrarAccesoSensible(eq(AuditoriaAccion.UPDATE), eq("materiales"), eq(base + "/temario"),
				isNull(), anyString());

		gestion.subirDocumento(KB, B01, "guia.pdf", cuerpoPdf());
		verify(auditoria).registrarAccesoSensible(eq(AuditoriaAccion.CREATE), eq("materiales"), eq(base + "/documentacion"),
				isNull(), contains("guia.pdf"));

		gestion.sustituirDocumento(KB, B01, "guia.pdf", cuerpoPdf());
		verify(auditoria).registrarAccesoSensible(eq(AuditoriaAccion.UPDATE), eq("materiales"),
				eq(base + "/documentacion/guia.pdf/contenido"), isNull(), anyString());

		gestion.eliminarDocumento(KB, B01, "guia.pdf");
		verify(auditoria).registrarAccesoSensible(eq(AuditoriaAccion.DELETE), eq("materiales"),
				eq(base + "/documentacion/guia.pdf"), isNull(), anyString());

		gestion.eliminarVideo(KB, B01, "guardia.mp4");
		verify(auditoria).registrarAccesoSensible(eq(AuditoriaAccion.DELETE), eq("materiales"),
				eq(base + "/videos/guardia.mp4"), isNull(), anyString());

		verify(auditoria, times(9)).registrarAccesoSensible(any(), any(), any(), any(), any());
	}

	@Test
	void unFalloDeLaAuditoria_noDeshaceNiDaPorFallidaLaEscritura() {
		org.mockito.Mockito.doThrow(new IllegalStateException("base de datos caída")).when(auditoria)
				.registrarAccesoSensible(any(), any(), any(), any(), any());

		assertEquals("guardia.mp4", gestion.subirVideo(KB, B01, "guardia.mp4", cuerpoVideo(100)).fichero());
	}

	// ------------------------------------------------------------------ identidad de un fichero

	private void dosVideosQueSoloDifierenEnMayusculas(Path bloque) throws IOException {
		video(bloque, "01_mayuscula.mp4");
		video(bloque, "02_minuscula.mp4");
		lectura.nombres.put("01_mayuscula.mp4", "A.mp4");
		lectura.nombres.put("02_minuscula.mp4", "a.mp4");
	}

	@Test
	void identidad_laExactaPrimero_sinExactaUnaSola_yVarias409() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		dosVideosQueSoloDifierenEnMayusculas(legada);
		video(legada, "Saludo.mp4");
		pdf(legada.resolve("documentacion").resolve("01_mayuscula.pdf"));
		pdf(legada.resolve("documentacion").resolve("02_minuscula.pdf"));
		pdf(legada.resolve("documentacion").resolve("Guia.pdf"));
		lectura.nombres.put("01_mayuscula.pdf", "G.pdf");
		lectura.nombres.put("02_minuscula.pdf", "g.pdf");

		assertEquals("02_minuscula.mp4", gestion.video(TKD, B01, "a.mp4").getFileName());
		assertEquals("01_mayuscula.mp4", gestion.video(TKD, B01, "A.mp4").getFileName());
		assertEquals("Saludo.mp4", gestion.video(TKD, B01, "saludo.MP4").getFileName());
		assertEquals(HttpStatus.CONFLICT, estadoDe(() -> gestion.video(TKD, B01, "A.MP4")));
		assertEquals("02_minuscula.pdf", gestion.documento(TKD, B01, "g.pdf").getFileName());
		assertEquals("01_mayuscula.pdf", gestion.documento(TKD, B01, "G.pdf").getFileName());
		assertEquals("Guia.pdf", gestion.documento(TKD, B01, "guia.PDF").getFileName());
		assertEquals(HttpStatus.CONFLICT, estadoDe(() -> gestion.documento(TKD, B01, "G.PDF")));
	}

	@Test
	void nombresQueSoloDifierenEnMayusculas_ningunaEscrituraQueReescribaElIndiceTocaNada() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		dosVideosQueSoloDifierenEnMayusculas(legada);
		pdf(legada.resolve("documentacion").resolve("guia.pdf"));

		todasLasEscrituras("a.mp4", "guia.pdf", List.of("a.mp4", "A.mp4"))
				.forEach((nombre, escritura) -> assertEquals(HttpStatus.CONFLICT, estadoDe(escritura), nombre));

		assertEquals(Set.of("01_mayuscula.mp4", "02_minuscula.mp4"), nombres(legada.resolve("videos")));
		assertEquals(Set.of("guia.pdf"), nombres(legada.resolve("documentacion")));
		assertFalse(Files.exists(legada.resolve("index.json")));
		assertFalse(Files.exists(raiz.resolve("org-1")), "ni siquiera se adopta");
		verify(auditoria, never()).registrarAccesoSensible(any(), any(), any(), any(), any());
	}

	@Test
	void sustituirConIndice_noLoReescribeYActuaSobreElExacto() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		dosVideosQueSoloDifierenEnMayusculas(legada);
		Files.writeString(legada.resolve("index.json"), "{\"videos\":[]}");

		gestion.sustituirVideo(TKD, B01, "a.mp4", cuerpoVideo(777));

		Path videos = propia(1L, "taekwondo", B01).resolve("videos");
		assertEquals(777L, Files.size(videos.resolve("02_minuscula.mp4")));
		assertEquals(32L, Files.size(videos.resolve("01_mayuscula.mp4")));
	}

	@Test
	void eliminarUnDocumento_borraElExacto() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		pdf(legada.resolve("documentacion").resolve("01_mayuscula.pdf"));
		pdf(legada.resolve("documentacion").resolve("02_minuscula.pdf"));
		lectura.nombres.put("01_mayuscula.pdf", "G.pdf");
		lectura.nombres.put("02_minuscula.pdf", "g.pdf");

		gestion.eliminarDocumento(TKD, B01, "g.pdf");

		assertEquals(Set.of("01_mayuscula.pdf"), nombres(propia(1L, "taekwondo", B01).resolve("documentacion")));
	}

	/** Solo corre en un disco que distinga mayúsculas: el de producción y el de la CI, no NTFS. */
	@Test
	void conDosFicherosDeVerdad_borrarResponde409YNoBorraNinguno() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		video(legada, "A.mp4");
		assumeTrue(Files.notExists(legada.resolve("videos").resolve("a.mp4")), "el disco no distingue mayúsculas");
		video(legada, "a.mp4");

		assertEquals(HttpStatus.CONFLICT, estadoDe(() -> gestion.eliminarVideo(TKD, B01, "a.mp4")));

		assertEquals(Set.of("A.mp4", "a.mp4"), nombres(legada.resolve("videos")));
		assertFalse(Files.exists(legada.resolve("index.json")));
	}

	// ------------------------------------------------------------------ índice ilegible

	@ParameterizedTest
	@ValueSource(strings = { "esto no es json", "", "null", "{}", "{\"videos\":null}", "{\"videos\":\"x\"}",
			"{\"videos\":[null]}", "{\"videos\":[{\"title\":\"Sin fichero\"}]}", "{\"videos\":[{\"file\":\"  \"}]}",
			"{\"videos\":[{\"file\":\"a.mp4\",\"order\":\"primero\"}]}" })
	void indiceIlegible_ningunaEscrituraLoPisa_yLasFamiliasSiguenLeyendo(String contenido) throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		video(legada, "02_soguis.mp4");
		video(legada, "01_saludo.mp4");
		pdf(legada.resolve("documentacion").resolve("guia.pdf"));
		Path indice = legada.resolve("index.json");
		Files.writeString(indice, contenido);
		alumno(50L, TKD, TipoGrado.BLANCO);
		String antes = instantanea(50L, TKD);

		todasLasEscrituras("01_saludo.mp4", "guia.pdf", List.of("02_soguis.mp4", "01_saludo.mp4"))
				.forEach((nombre, escritura) -> {
					MaterialExamenException error = assertThrows(MaterialExamenException.class, escritura, nombre);
					assertEquals(HttpStatus.CONFLICT, error.getEstado(), nombre);
					assertTrue(error.getMessage().contains("índice"), nombre + ": " + error.getMessage());
				});

		assertEquals(contenido, Files.readString(indice));
		assertEquals(Set.of("01_saludo.mp4", "02_soguis.mp4"), nombres(legada.resolve("videos")));
		assertFalse(Files.exists(raiz.resolve("org-1")), "ni siquiera se adopta");
		assertEquals(antes, instantanea(50L, TKD));
		assertEquals("01_saludo.mp4", lectura.obtenerMaterialExamen(50L, TKD).getVideos().get(0).getId(),
				"la familia sigue con el orden por nombre");
		assertEquals(2, gestion.detalle(TKD, B01).videos().size(), "el panel también lee");
		verify(auditoria, never()).registrarAccesoSensible(any(), any(), any(), any(), any());
	}

	@Test
	void unaCarpetaLlamadaIndex_tampocoSeEscribe() throws Exception {
		Path carpeta = propia(1L, "kickboxing", B01);
		video(carpeta, "01_a.mp4");
		Files.createDirectories(carpeta.resolve("index.json"));

		assertEquals(HttpStatus.CONFLICT, estadoDe(() -> gestion.cambiarTitulo(KB, B01, "01_a.mp4", "Otro")));

		assertTrue(Files.isDirectory(carpeta.resolve("index.json")));
	}

	@Test
	void indiceQueSeDanaDuranteLaSubida_noSePublicaNada() throws Exception {
		Path carpeta = propia(1L, "kickboxing", B01);
		video(carpeta, "01_a.mp4");
		Files.writeString(carpeta.resolve("index.json"), "{\"videos\":[{\"file\":\"01_a.mp4\",\"title\":\"Mi título\"}]}");
		byte[] bytes = mp4(100);
		InputStream danaAlAcabar = new ByteArrayInputStream(bytes) {
			@Override
			public synchronized int read(byte[] destino, int desde, int cuantos) {
				int leidos = super.read(destino, desde, cuantos);
				if (leidos < 0) {
					escribirSinFallar(carpeta.resolve("index.json"), "roto");
				}
				return leidos;
			}
		};

		assertEquals(HttpStatus.CONFLICT,
				estadoDe(() -> gestion.subirVideo(KB, B01, "nuevo.mp4", new CuerpoCrudo(danaAlAcabar, 100, "video/mp4"))));

		assertEquals("roto", Files.readString(carpeta.resolve("index.json")));
		assertEquals(Set.of("01_a.mp4"), nombres(carpeta.resolve("videos")));
		sinTemporales(carpeta);
	}

	@Test
	void indiceSinTituloNiOrden_yConUnaEntradaSinFichero_sigueSiendoLegible() throws Exception {
		Path carpeta = propia(1L, "kickboxing", B01);
		video(carpeta, "01_a.mp4");
		video(carpeta, "02_b.mp4");
		Files.writeString(carpeta.resolve("index.json"), "{\"videos\":[{\"file\":\"02_b.mp4\"},{\"file\":\"fantasma.mp4\",\"title\":\"x\"}]}");

		assertEquals("Nuevo", gestion.cambiarTitulo(KB, B01, "01_a.mp4", "Nuevo").videos().get(0).titulo());
	}

	// ------------------------------------------------------------------ lectores durante la adopción

	/** Lo que hace la adopción: mover la carpeta entera. Con el cerrojo en otra petición, el lector no se entera. */
	private Runnable adopcionDe(Path legada, Path propia) {
		return () -> {
			try {
				Files.createDirectories(propia.getParent());
				Files.move(legada, propia, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException e) {
				throw new AssertionError(e);
			}
		};
	}

	@Test
	void familiaQueResuelveLaLegadaYOtraPeticionLaMueve_veLoMismo_noUnBloqueVacio() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		video(legada, "01_saludo.mp4");
		pdf(legada.resolve("temario").resolve("temario.pdf"));
		pdf(legada.resolve("documentacion").resolve("guia.pdf"));
		alumno(60L, TKD, TipoGrado.BLANCO);
		String antes = instantanea(60L, TKD);
		lectura.alResolverLaCarpeta = adopcionDe(legada, propia(1L, "taekwondo", B01));

		assertEquals(antes, instantanea(60L, TKD));

		assertFalse(Files.exists(legada));
	}

	@Test
	void laCarpetaQueDesapareceAMitadDeLeer_tambienSeReintenta() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		video(legada, "01_saludo.mp4");
		video(legada, "02_soguis.mp4");
		pdf(legada.resolve("temario").resolve("temario.pdf"));
		Files.writeString(legada.resolve("index.json"), "{\"videos\":[{\"file\":\"01_saludo.mp4\",\"title\":\"El saludo\"}]}");
		alumno(61L, TKD, TipoGrado.BLANCO);
		String antes = instantanea(61L, TKD);
		// El índice es lo primero que se lee de los vídeos: el temario ya salió de la legada y los vídeos ya no están ahí.
		Runnable adopcion = adopcionDe(legada, propia(1L, "taekwondo", B01));
		ReflectionTestUtils.setField(lectura, "objectMapper", new ObjectMapper() {
			private boolean movida;

			@Override
			public <T> T readValue(java.io.File fichero, Class<T> tipo) throws IOException {
				T leido = super.readValue(fichero, tipo);
				if (!movida) {
					movida = true;
					adopcion.run();
				}
				return leido;
			}
		});

		assertEquals(antes, instantanea(61L, TKD));
	}

	@Test
	void unBloqueAnteriorQueSeAdoptaAMitad_noPierdeSusVideos() throws Exception {
		Path anterior = bloqueLegado(TKD, B01);
		video(anterior, "01_taeguk_il_jang.mp4");
		Path actual = propia(1L, "taekwondo", B03);
		video(actual, "01_naranja.mp4");
		alumno(62L, TKD, TipoGrado.NARANJA);
		String antes = instantanea(62L, TKD);
		assertTrue(antes.contains("anterior__b01_inicio_a_amarillo__01_taeguk_il_jang.mp4"));
		// El bloque actual es propio; la segunda carpeta que se resuelve es la del bloque anterior.
		lectura.alResolverLaCarpeta = () -> lectura.alResolverLaCarpeta = adopcionDe(anterior, propia(1L, "taekwondo", B01));

		assertEquals(antes, instantanea(62L, TKD));
	}

	@Test
	void elPanelTambienReintenta() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		video(legada, "01_saludo.mp4");
		lectura.alResolverLaCarpeta = adopcionDe(legada, propia(1L, "taekwondo", B01));

		assertEquals(1, gestion.detalle(TKD, B01).videos().size());
	}

	@Test
	void elReintentoEsUnoSolo_yNoHayReintentoSiNoSeEligioLaLegada() throws Exception {
		alumno(63L, KB, TipoGrado.BLANCO);
		lectura.carpetasResueltas = 0;

		assertEquals(0, lectura.leerBloque(KB, B01).videos().size());
		assertEquals(2, lectura.carpetasResueltas, "ni propia ni legada: una vuelta más y se acaba");

		Path carpeta = propia(1L, "kickboxing", B02);
		video(carpeta, "01_a.mp4");
		lectura.carpetasResueltas = 0;
		assertEquals(1, lectura.leerBloque(KB, B02).videos().size());
		assertEquals(1, lectura.carpetasResueltas, "la propia no se mueve");

		TenantContext.set(2L);
		lectura.carpetasResueltas = 0;
		assertEquals(0, lectura.leerBloque(KB, B01).videos().size());
		assertEquals(1, lectura.carpetasResueltas, "otra escuela nunca recurre a la legada");
	}

	// ------------------------------------------------------------------ publicación

	/** Un bloque con el índice ya escrito, un título propio y lo que ve la familia antes de la escritura que va a fallar. */
	private String bloqueConIndice(Long alumnoId) throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		video(legada, "01_saludo.mp4");
		video(legada, "02_soguis.mp4");
		gestion.cambiarTitulo(TKD, B01, "01_saludo.mp4", "El saludo");
		org.mockito.Mockito.clearInvocations(auditoria);
		doble.publicados.clear();
		alumno(alumnoId, TKD, TipoGrado.BLANCO);
		return instantanea(alumnoId, TKD);
	}

	private List<String> ficherosDelIndice(Path carpeta) throws IOException {
		List<String> ficheros = new ArrayList<>();
		json.readTree(carpeta.resolve("index.json").toFile()).get("videos").forEach(e -> ficheros.add(e.get("file").asText()));
		return ficheros;
	}

	@Test
	void subirVideo_primeroElIndiceYDespuesElFichero() throws Exception {
		bloqueConIndice(70L);

		gestion.subirVideo(TKD, B01, "nuevo.mp4", cuerpoVideo(100));

		assertEquals(List.of("index.json", "nuevo.mp4"), doble.publicados);
	}

	@Test
	void subirVideo_sinPoderEscribirElIndice_500YNoAparece() throws Exception {
		String antes = bloqueConIndice(71L);
		Path carpeta = propia(1L, "taekwondo", B01);
		String indice = Files.readString(carpeta.resolve("index.json"));
		doble.falloAlPublicar = "index.json"::equals;

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
				estadoDe(() -> gestion.subirVideo(TKD, B01, "nuevo.mp4", cuerpoVideo(100))));

		assertEquals(indice, Files.readString(carpeta.resolve("index.json")));
		assertEquals(Set.of("01_saludo.mp4", "02_soguis.mp4"), nombres(carpeta.resolve("videos")));
		sinTemporales(carpeta);
		assertEquals(antes, instantanea(71L, TKD));
		verify(auditoria, never()).registrarAccesoSensible(any(), any(), any(), any(), any());
	}

	@Test
	void subirVideo_sinPoderMover_restauraElIndice() throws Exception {
		String antes = bloqueConIndice(72L);
		Path carpeta = propia(1L, "taekwondo", B01);
		doble.falloAlPublicar = "nuevo.mp4"::equals;

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
				estadoDe(() -> gestion.subirVideo(TKD, B01, "nuevo.mp4", cuerpoVideo(100))));

		assertEquals(List.of("01_saludo.mp4", "02_soguis.mp4"), ficherosDelIndice(carpeta));
		assertEquals(Set.of("01_saludo.mp4", "02_soguis.mp4"), nombres(carpeta.resolve("videos")));
		sinTemporales(carpeta);
		assertEquals(antes, instantanea(72L, TKD));
		assertEquals("El saludo", gestion.detalle(TKD, B01).videos().get(0).titulo());
		verify(auditoria, never()).registrarAccesoSensible(any(), any(), any(), any(), any());
	}

	@Test
	void trasUnaCaidaEntreElIndiceYElMovimiento_laEntradaEsInocuaYSePuedeRepetir() throws Exception {
		String antes = bloqueConIndice(73L);
		Path carpeta = propia(1L, "taekwondo", B01);
		// Mover falla y restaurar también: lo que dejaría una caída entre los dos pasos.
		java.util.concurrent.atomic.AtomicInteger indices = new java.util.concurrent.atomic.AtomicInteger();
		doble.falloAlPublicar = nombre -> "nuevo.mp4".equals(nombre)
				|| ("index.json".equals(nombre) && indices.incrementAndGet() > 1);
		estadoDe(() -> gestion.subirVideo(TKD, B01, "nuevo.mp4", cuerpoVideo(100)));
		assertTrue(ficherosDelIndice(carpeta).contains("nuevo.mp4"), "la entrada sin fichero");

		assertEquals(antes, instantanea(73L, TKD));
		assertEquals(2, gestion.detalle(TKD, B01).videos().size());
		assertEquals(2, gestion.listar().totales().videos());

		doble.falloAlPublicar = nombre -> false;
		assertEquals("nuevo.mp4", gestion.subirVideo(TKD, B01, "nuevo.mp4", cuerpoVideo(100)).fichero());
		assertEquals(List.of("01_saludo.mp4", "02_soguis.mp4", "nuevo.mp4"), ficherosDelIndice(carpeta));
		assertEquals(3, gestion.detalle(TKD, B01).videos().get(2).orden());
	}

	@Test
	void eliminarVideo_sinPoderBorrar_restauraElIndice() throws Exception {
		String antes = bloqueConIndice(74L);
		Path carpeta = propia(1L, "taekwondo", B01);
		doble.falloAlBorrar = new IOException("fichero en uso");

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, estadoDe(() -> gestion.eliminarVideo(TKD, B01, "01_saludo.mp4")));

		assertEquals(List.of("01_saludo.mp4", "02_soguis.mp4"), ficherosDelIndice(carpeta));
		assertEquals(Set.of("01_saludo.mp4", "02_soguis.mp4"), nombres(carpeta.resolve("videos")));
		assertEquals(antes, instantanea(74L, TKD));
		sinTemporales(carpeta);
		verify(auditoria, never()).registrarAccesoSensible(any(), any(), any(), any(), any());
	}

	@Test
	void adoptar_conEmpatesDeOrden_mismaInstantaneaExacta() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		video(legada, "01_b.mp4");
		video(legada, "01_a.mp4");
		video(legada, "zeta.mp4");
		video(legada, "Alfa.mov");
		alumno(75L, TKD, TipoGrado.BLANCO);
		String antes = instantanea(75L, TKD);
		assertEquals(List.of("01_a.mp4", "01_b.mp4", "Alfa.mov", "zeta.mp4"),
				lectura.obtenerMaterialExamen(75L, TKD).getVideos().stream().map(v -> v.getId()).toList());

		gestion.cambiarTitulo(TKD, B01, "01_a.mp4", "a");

		assertFalse(Files.exists(legada));
		assertEquals(antes, instantanea(75L, TKD));
	}

	@Test
	void un415SobreUnBloqueLegado_noCambiaLoQueVenLasFamilias() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		video(legada, "02_soguis.mp4");
		video(legada, "01_saludo.mp4");
		pdf(legada.resolve("temario").resolve("Programa antiguo.pdf"));
		pdf(legada.resolve("documentacion").resolve("guia.pdf"));
		alumno(76L, TKD, TipoGrado.BLANCO);
		String antes = instantanea(76L, TKD);
		byte[] falso = "esto no es un video ni un pdf, solo texto".getBytes(StandardCharsets.UTF_8);

		assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, estadoDe(() -> gestion.subirVideo(TKD, B01, "nuevo.mp4",
				new CuerpoCrudo(new ByteArrayInputStream(falso), falso.length, "video/mp4"))));
		assertEquals(antes, instantanea(76L, TKD), "también con la carpeta ya adoptada");
		assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, estadoDe(() -> gestion.subirTemario(TKD, B01,
				new CuerpoCrudo(new ByteArrayInputStream(falso), falso.length, "application/pdf"))));

		assertEquals(antes, instantanea(76L, TKD));
		assertEquals(Set.of("Programa antiguo.pdf"), nombres(propia(1L, "taekwondo", B01).resolve("temario")));
		sinTemporales(propia(1L, "taekwondo", B01));
	}

	@Test
	void unaSustitucionIntercaladaConUnaLectura_conservaLoQueSeVe() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		video(legada, "01_saludo.mp4");
		alumno(77L, TKD, TipoGrado.BLANCO);
		String antes = instantanea(77L, TKD);
		lectura.alResolverLaCarpeta = () -> gestion.sustituirVideo(TKD, B01, "01_saludo.mp4", cuerpoVideo(500));

		assertEquals(antes, instantanea(77L, TKD));

		assertEquals(500L, Files.size(propia(1L, "taekwondo", B01).resolve("videos").resolve("01_saludo.mp4")));
	}

	// ------------------------------------------------------------------ carpeta heredada vacía tras desplegar

	/** Lo que recrea un {@code git reset --hard} en el servidor: el repositorio versiona estos {@code .gitkeep}. */
	private Path esqueletoLegado(String disciplina, String carpeta) throws IOException {
		Path legada = bloqueLegado(disciplina, carpeta);
		for (String subcarpeta : List.of("temario", "videos", "documentacion")) {
			Files.createDirectories(legada.resolve(subcarpeta));
			Files.writeString(legada.resolve(subcarpeta).resolve(".gitkeep"), "");
		}
		return legada;
	}

	@Test
	void esqueletoLegadoJuntoALaPropia_noEsConflicto_yNoSeToca() throws Exception {
		Path carpeta = propia(1L, "kickboxing", B01);
		video(carpeta, "01_guardia.mp4");
		Path legada = esqueletoLegado(KB, B01);

		assertEquals("Guardia", gestion.cambiarTitulo(KB, B01, "01_guardia.mp4", "Guardia").videos().get(0).titulo());
		assertEquals("nuevo.mp4", gestion.subirVideo(KB, B01, "nuevo.mp4", cuerpoVideo(100)).fichero());

		assertEquals(Set.of(".gitkeep"), nombres(legada.resolve("videos")));
		assertEquals(Set.of(".gitkeep"), nombres(legada.resolve("temario")));
		assertEquals(Set.of(".gitkeep"), nombres(legada.resolve("documentacion")));
	}

	@Test
	void carpetaLegadaSoloConDirectoriosVaciosYEsqueleto_noCuentaComoLegada() throws Exception {
		Path carpeta = propia(1L, "kickboxing", B01);
		video(carpeta, "01_guardia.mp4");
		Path legada = bloqueLegado(KB, B01);
		Files.createDirectories(legada.resolve("videos").resolve("vacia"));
		Files.writeString(legada.resolve(".gitkeep"), "");

		assertEquals(1, gestion.cambiarTitulo(KB, B01, "01_guardia.mp4", "Guardia").videos().size());

		assertTrue(Files.isRegularFile(legada.resolve(".gitkeep")));
	}

	@Test
	void sinPropiaYConEsqueleto_seCreaLaPropiaYNoSeMueveNada() throws Exception {
		Path legada = esqueletoLegado(KB, B01);

		gestion.subirVideo(KB, B01, "guardia.mp4", cuerpoVideo(100));

		Path carpeta = propia(1L, "kickboxing", B01);
		assertEquals(Set.of("guardia.mp4"), nombres(carpeta.resolve("videos")));
		assertEquals(Set.of(), nombres(carpeta.resolve("temario")), "los .gitkeep no viajan");
		assertEquals(Set.of(".gitkeep"), nombres(legada.resolve("videos")));
		assertEquals(Set.of(".gitkeep"), nombres(legada.resolve("documentacion")));
		verify(auditoria, times(1)).registrarAccesoSensible(any(), any(), any(), any(), any());
	}

	@Test
	void trasAdoptar_elEsqueletoRecreadoNoBloqueaLaSiguienteEscritura() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		video(legada, "01_saludo.mp4");
		alumno(80L, TKD, TipoGrado.BLANCO);
		String antes = instantanea(80L, TKD);
		gestion.cambiarTitulo(TKD, B01, "01_saludo.mp4", "saludo");
		assertFalse(Files.exists(legada));

		esqueletoLegado(TKD, B01);

		assertEquals(antes, instantanea(80L, TKD));
		assertEquals(1, gestion.cambiarTitulo(TKD, B01, "01_saludo.mp4", "saludo").videos().size());
		assertEquals(antes, instantanea(80L, TKD));
	}

	@ParameterizedTest
	@ValueSource(strings = { "videos/01_legado.mp4", "temario/temario.pdf", "documentacion/subcarpeta/guia.pdf", "index.json",
			"cualquier_cosa.txt" })
	void unFicheroRealEnLaLegada_sigueSiendoConflicto(String fichero) throws Exception {
		Path carpeta = propia(1L, "kickboxing", B01);
		video(carpeta, "01_guardia.mp4");
		Path legada = esqueletoLegado(KB, B01);
		Files.createDirectories(legada.resolve(fichero).getParent());
		Files.writeString(legada.resolve(fichero), "material real");

		MaterialExamenException error = assertThrows(MaterialExamenException.class,
				() -> gestion.cambiarTitulo(KB, B01, "01_guardia.mp4", "Guardia"));

		assertEquals(HttpStatus.CONFLICT, error.getEstado());
		assertTrue(error.getMessage().contains("dos carpetas"));
		assertEquals("material real", Files.readString(legada.resolve(fichero)));
		assertFalse(Files.exists(carpeta.resolve("index.json")));
	}

	@Test
	void unFicheroRealEnLaLegadaSinPropia_seAdoptaLoDemasIncluido() throws Exception {
		Path legada = esqueletoLegado(KB, B01);
		video(legada, "01_guardia.mp4");

		gestion.cambiarTitulo(KB, B01, "01_guardia.mp4", "Guardia");

		assertFalse(Files.exists(legada));
		assertEquals(Set.of(".gitkeep", "01_guardia.mp4"), nombres(propia(1L, "kickboxing", B01).resolve("videos")));
	}

	@Test
	void legadaConSoloOcultosReales_esMaterialYNuncaQuedaOculta() throws Exception {
		Path legada = bloqueLegado(TKD, B01);
		Files.createDirectories(legada.resolve("videos"));
		Files.writeString(legada.resolve("videos").resolve(".gitkeep"), "");
		Files.write(legada.resolve("videos").resolve(".taeguk.mp4"), mp4(32));
		alumno(81L, TKD, TipoGrado.BLANCO);
		String antes = instantanea(81L, TKD);
		assertEquals(Set.of(".taeguk.mp4"), videosDeLaFamilia(81L));

		assertThrows(NoSuchElementException.class, () -> gestion.cambiarTitulo(TKD, B01, "no.mp4", "Otro"));

		assertEquals(antes, instantanea(81L, TKD));
		assertEquals(Set.of(".taeguk.mp4"), videosDeLaFamilia(81L));

		gestion.subirVideo(TKD, B01, "nuevo.mp4", cuerpoVideo(100));

		assertFalse(Files.exists(legada), "la legada se adopta entera, ocultos incluidos");
		assertEquals(Set.of(".gitkeep", ".taeguk.mp4", "nuevo.mp4"), nombres(propia(1L, "taekwondo", B01).resolve("videos")));
		assertEquals(Set.of(".taeguk.mp4", "nuevo.mp4"), videosDeLaFamilia(81L));
	}

	@Test
	void legadaConSubidaTemporal_sigueSiendoEsqueleto() throws Exception {
		Path legada = esqueletoLegado(TKD, B01);
		Files.writeString(legada.resolve("videos").resolve(".subida-abc"), "a medias");

		gestion.subirVideo(TKD, B01, "nuevo.mp4", cuerpoVideo(100));

		assertEquals(Set.of(".gitkeep", ".subida-abc"), nombres(legada.resolve("videos")));
		assertEquals(Set.of("nuevo.mp4"), nombres(propia(1L, "taekwondo", B01).resolve("videos")));
	}

	// ------------------------------------------------------------------ apoyo

	/** Las nueve escrituras, con cuerpos que fallan si se leen: lo que se rechaza antes no recibe nada. */
	private Map<String, org.junit.jupiter.api.function.Executable> todasLasEscrituras(String video, String documento,
			List<String> orden) {
		InputStream prohibido = new InputStream() {
			@Override
			public int read() {
				throw new AssertionError("no se lee el cuerpo");
			}
		};
		Map<String, org.junit.jupiter.api.function.Executable> escrituras = new LinkedHashMap<>();
		escrituras.put("subir", () -> gestion.subirVideo(TKD, B01, "nuevo.mp4", new CuerpoCrudo(prohibido, 100, "video/mp4")));
		escrituras.put("sustituir",
				() -> gestion.sustituirVideo(TKD, B01, video, new CuerpoCrudo(prohibido, 100, "video/mp4")));
		escrituras.put("título", () -> gestion.cambiarTitulo(TKD, B01, video, "Otro"));
		escrituras.put("orden", () -> gestion.reordenarVideos(TKD, B01, orden));
		escrituras.put("eliminar", () -> gestion.eliminarVideo(TKD, B01, video));
		escrituras.put("temario", () -> gestion.subirTemario(TKD, B01, new CuerpoCrudo(prohibido, 100, "application/pdf")));
		escrituras.put("documento",
				() -> gestion.subirDocumento(TKD, B01, "a.pdf", new CuerpoCrudo(prohibido, 100, "application/pdf")));
		escrituras.put("sustituir documento",
				() -> gestion.sustituirDocumento(TKD, B01, documento, new CuerpoCrudo(prohibido, 100, "application/pdf")));
		escrituras.put("eliminar documento", () -> gestion.eliminarDocumento(TKD, B01, documento));
		return escrituras;
	}

	private static void escribirSinFallar(Path fichero, String contenido) {
		try {
			Files.writeString(fichero, contenido);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	private HttpStatus estadoDe(org.junit.jupiter.api.function.Executable accion) {
		return assertThrows(MaterialExamenException.class, accion).getEstado();
	}

	private String instantanea(Long alumnoId, String disciplina) throws Exception {
		return json.writeValueAsString(lectura.obtenerMaterialExamen(alumnoId, disciplina));
	}

	private Set<String> videosDeLaFamilia(Long alumnoId) throws Exception {
		return lectura.obtenerMaterialExamen(alumnoId, TKD).getVideos().stream().map(v -> v.getId())
				.collect(java.util.stream.Collectors.toSet());
	}

	private void alumno(Long alumnoId, String disciplina, TipoGrado tipo) {
		AlumnoDeporte inscripcion = new AlumnoDeporte();
		inscripcion.setDisciplina(CatalogoTaemoiEnMemoria.disciplinaTaemoi(disciplina));
		Grado grado = new Grado();
		grado.setCodigo(tipo.name());
		inscripcion.setGrado(grado);
		Alumno ficha = new Alumno();
		ficha.setFechaNacimiento(java.sql.Date.valueOf(java.time.LocalDate.now().minusYears(30)));
		inscripcion.setAlumno(ficha);
		when(alumnoDeportes.obtenerDeportesActivosDelAlumno(alumnoId)).thenReturn(List.of(inscripcion));
	}

	private Path bloqueLegado(String disciplina, String carpeta) throws IOException {
		return Files.createDirectories(raiz.resolve("Materiales_Examen").resolve(disciplina.toLowerCase()).resolve(carpeta));
	}

	private Path propia(Long organizacion, String disciplina, String bloque) {
		return raiz.resolve("org-" + organizacion).resolve("materiales").resolve(disciplina).resolve(bloque);
	}

	private void video(Path bloque, String nombre) throws IOException {
		Files.createDirectories(bloque.resolve("videos"));
		Files.write(bloque.resolve("videos").resolve(nombre), mp4(32));
	}

	private void pdf(Path fichero) throws IOException {
		Files.createDirectories(fichero.getParent());
		Files.write(fichero, pdfBytes());
	}

	private static byte[] pdfBytes() {
		return "%PDF-1.7\nprueba".getBytes(StandardCharsets.ISO_8859_1);
	}

	private static byte[] mp4(int bytes) {
		byte[] contenido = new byte[bytes];
		byte[] ftyp = "ftyp".getBytes(StandardCharsets.US_ASCII);
		System.arraycopy(ftyp, 0, contenido, 4, 4);
		return contenido;
	}

	private static byte[] webm(int bytes) {
		byte[] contenido = new byte[bytes];
		contenido[0] = 0x1A;
		contenido[1] = 0x45;
		contenido[2] = (byte) 0xDF;
		contenido[3] = (byte) 0xA3;
		return contenido;
	}

	private static CuerpoCrudo cuerpoVideo(int bytes) {
		return new CuerpoCrudo(new ByteArrayInputStream(mp4(bytes)), bytes, "video/mp4");
	}

	private static CuerpoCrudo cuerpoPdf() {
		byte[] contenido = pdfBytes();
		return new CuerpoCrudo(new ByteArrayInputStream(contenido), contenido.length, "application/pdf");
	}

	private static InputStream prefijadoConPdf(InputStream resto) {
		return new java.io.SequenceInputStream(new ByteArrayInputStream(pdfBytes()), resto);
	}

	private static Set<String> nombres(Path carpeta) throws IOException {
		if (!Files.isDirectory(carpeta)) {
			return Set.of();
		}
		try (Stream<Path> ficheros = Files.list(carpeta)) {
			return ficheros.map(f -> f.getFileName().toString()).collect(java.util.stream.Collectors.toSet());
		}
	}

	private static void sinTemporales(Path carpeta) {
		try (Stream<Path> ficheros = Files.walk(carpeta)) {
			assertEquals(List.of(), ficheros.filter(f -> f.getFileName().toString().startsWith(".subida-")).toList());
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}
}
