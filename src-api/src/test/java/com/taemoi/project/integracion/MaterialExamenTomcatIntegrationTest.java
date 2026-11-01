package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Membresia;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.MembresiaRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.JwtService;

/**
 * Pieza R1 sobre Tomcat real, por socket: lo que MockMvc no puede probar. Un vídeo por encima del límite global de multipart,
 * una petición sin {@code Content-Length} y un cliente que corta la conexión a mitad de subida.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MaterialExamenTomcatIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String RUTA = "/api/material-examen/KICKBOXING/b01_inicio_a_amarillo/videos";
	private static final Path DOCUMENTOS;

	static {
		try {
			DOCUMENTOS = Files.createTempDirectory("taemoi-it-tomcat-").toAbsolutePath().normalize();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@DynamicPropertySource
	static void raizDeDocumentos(DynamicPropertyRegistry registro) {
		registro.add("app.documentos.directorio.linux", () -> DOCUMENTOS + "/");
		registro.add("app.documentos.directorio.windows", () -> DOCUMENTOS + "/");
	}

	@LocalServerPort int puerto;
	@Autowired PasswordEncoder encoder;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired MembresiaRepository membresias;

	@MockitoBean JavaMailSender mailSender;

	private final HttpClient cliente = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
	String sesion;

	@BeforeAll
	void preparar() {
		String sufijo = UUID.randomUUID().toString().substring(0, 8);
		Usuario usuario = TenantContext.calcularCon(ORG1, () -> {
			Usuario nuevo = new Usuario();
			nuevo.setNombre("Usuario");
			nuevo.setApellidos("Tomcat");
			nuevo.setEmail("gestor-tomcat-" + sufijo + "@example.test");
			nuevo.setContrasena(encoder.encode("Clave-Segura-123"));
			nuevo.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(nuevo);
		});
		TenantContext.ejecutarCon(ORG1,
				() -> membresias.save(new Membresia(usuario.getId(), ORG1, RolMembresia.ADMINISTRADOR)));
		sesion = "jwt=" + jwtService.generateToken(usuario, 600_000, ORG1);
	}

	@BeforeEach
	void vaciarDisco() throws IOException {
		try (Stream<Path> rutas = Files.walk(DOCUMENTOS)) {
			rutas.filter(r -> !r.equals(DOCUMENTOS)).sorted(Comparator.reverseOrder()).forEach(r -> r.toFile().delete());
		}
	}

	@AfterAll
	void borrarRaiz() throws IOException {
		vaciarDisco();
		Files.deleteIfExists(DOCUMENTOS);
	}

	@Test
	void unVideoDe60MB_supera50MBDeMultipart_yElVisorPideUnRango() throws Exception {
		Path video = Files.createTempFile("taemoi-video-", ".mp4");
		try {
			try (RandomAccessFile fichero = new RandomAccessFile(video.toFile(), "rw")) {
				fichero.setLength(60L * 1024 * 1024);
				fichero.seek(4);
				fichero.write("ftyp".getBytes(StandardCharsets.US_ASCII));
			}

			HttpResponse<String> subida = cliente.send(peticion(RUTA + "?nombre=grande.mp4").header("Content-Type", "video/mp4")
					.POST(HttpRequest.BodyPublishers.ofFile(video)).build(), HttpResponse.BodyHandlers.ofString());

			assertEquals(201, subida.statusCode(), subida.body());
			Path guardado = carpetaDeVideos().resolve("grande.mp4");
			assertEquals(Files.size(video), Files.size(guardado));

			HttpResponse<byte[]> rango = cliente.send(peticion(RUTA + "/grande.mp4").header("Range", "bytes=4-7").GET().build(),
					HttpResponse.BodyHandlers.ofByteArray());
			assertEquals(206, rango.statusCode());
			assertEquals("ftyp", new String(rango.body(), StandardCharsets.US_ASCII));
		} finally {
			Files.deleteIfExists(video);
		}
	}

	@Test
	void sinContentLength_411() throws Exception {
		// Un cuerpo de longitud desconocida sale en chunked.
		HttpResponse<String> respuesta = cliente.send(peticion(RUTA + "?nombre=a.mp4")
				.POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(new byte[64]))).build(),
				HttpResponse.BodyHandlers.ofString());

		assertEquals(411, respuesta.statusCode(), respuesta.body());
		assertTrue(respuesta.body().contains("mensaje"), respuesta.body());
	}

	@Test
	void elJsonDelPanel_sinContentLength_411() throws Exception {
		for (HttpRequest.Builder peticion : List.of(peticion(RUTA + "/orden").method("PUT", cuerpoSinLongitud("[]")),
				peticion(RUTA + "/a.mp4").method("PATCH", cuerpoSinLongitud("{\"titulo\":\"Hola\"}")))) {
			HttpResponse<String> respuesta = cliente.send(peticion.header("Content-Type", "application/json").build(),
					HttpResponse.BodyHandlers.ofString());

			assertEquals(411, respuesta.statusCode(), respuesta.body());
			assertTrue(respuesta.body().contains("mensaje"), respuesta.body());
		}
	}

	@Test
	void elJsonDelPanel_masDe64KB_413_yHasta64KBSeLee() throws Exception {
		String relleno = "x".repeat(64 * 1024);
		String limite = "{\"titulo\":\"" + relleno.substring(13) + "\"}";
		assertEquals(64 * 1024, limite.length());

		HttpResponse<String> cabe = cliente.send(json(RUTA + "/a.mp4", "PATCH", limite), HttpResponse.BodyHandlers.ofString());
		assertEquals(400, cabe.statusCode(), "el título es largo, pero el cuerpo se ha leído");
		assertTrue(cabe.body().contains("120 caracteres"), cabe.body());

		HttpResponse<String> titulo = cliente.send(json(RUTA + "/a.mp4", "PATCH", limite + " "),
				HttpResponse.BodyHandlers.ofString());
		assertEquals(413, titulo.statusCode(), titulo.body());
		assertTrue(titulo.body().contains("mensaje"), titulo.body());

		HttpResponse<String> orden = cliente.send(json(RUTA + "/orden", "PUT", "[\"" + relleno + "\"]"),
				HttpResponse.BodyHandlers.ofString());
		assertEquals(413, orden.statusCode(), orden.body());
	}

	@Test
	void unClienteQueCorta_noDejaTemporalNiEntrada() throws Exception {
		byte[] inicio = new byte[1024 * 1024];
		System.arraycopy("ftyp".getBytes(StandardCharsets.US_ASCII), 0, inicio, 4, 4);
		try (Socket socket = new Socket("localhost", puerto)) {
			OutputStream salida = socket.getOutputStream();
			salida.write(("POST " + RUTA + "?nombre=cortado.mp4 HTTP/1.1\r\nHost: localhost\r\nCookie: " + sesion
					+ "\r\nContent-Type: video/mp4\r\nContent-Length: 10485760\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
			salida.write(inicio);
			salida.flush();
			esperar(() -> temporales().size() == 1, "el temporal aparece mientras se sube");
		}

		esperar(() -> temporales().isEmpty(), "el temporal desaparece al cortar");
		assertEquals(List.of(), temporales());
		assertFalse(Files.exists(carpetaDeVideos().resolve("cortado.mp4")));
		assertFalse(Files.exists(carpetaDeVideos().getParent().resolve("index.json")));
	}

	private HttpRequest json(String ruta, String metodo, String cuerpo) {
		return peticion(ruta).header("Content-Type", "application/json")
				.method(metodo, HttpRequest.BodyPublishers.ofString(cuerpo)).build();
	}

	private static HttpRequest.BodyPublisher cuerpoSinLongitud(String cuerpo) {
		// Un cuerpo de longitud desconocida sale en chunked.
		return HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(cuerpo.getBytes(StandardCharsets.UTF_8)));
	}

	private HttpRequest.Builder peticion(String ruta) {
		return HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).header("Cookie", sesion);
	}

	private static Path carpetaDeVideos() {
		return DOCUMENTOS.resolve("org-1/materiales/kickboxing/b01_inicio_a_amarillo/videos");
	}

	private static List<Path> temporales() {
		if (!Files.isDirectory(carpetaDeVideos())) {
			return List.of();
		}
		try (Stream<Path> ficheros = Files.list(carpetaDeVideos())) {
			return ficheros.filter(f -> f.getFileName().toString().startsWith(".subida-")).toList();
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	private static void esperar(BooleanSupplier condicion, String descripcion) throws InterruptedException {
		for (int intento = 0; intento < 200; intento++) {
			if (condicion.getAsBoolean()) {
				return;
			}
			Thread.sleep(50);
		}
		throw new AssertionError("Tiempo agotado: " + descripcion);
	}
}
