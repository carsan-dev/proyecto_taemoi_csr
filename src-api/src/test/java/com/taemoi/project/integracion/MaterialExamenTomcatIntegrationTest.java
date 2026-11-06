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
import java.sql.Date;
import java.time.LocalDate;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Membresia;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.MembresiaRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.ContadorService;
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
	@Autowired AlumnoRepository alumnos;
	@Autowired ContadorService contadores;
	@Autowired JdbcTemplate jdbc;

	@MockitoBean JavaMailSender mailSender;

	private final HttpClient cliente = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
	String sesion;
	long alumnoId;

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
		alumnoId = TenantContext.calcularCon(ORG1, () -> {
			Alumno alumno = new Alumno();
			alumno.setNombre("ALUMNO " + sufijo);
			alumno.setApellidos("PRUEBA TOMCAT");
			alumno.setNumeroExpediente(contadores.siguienteNumeroExpediente());
			alumno.setFechaNacimiento(Date.valueOf(LocalDate.of(1995, 4, 4)));
			alumno.setDireccion("Calle Prueba 8");
			alumno.setTelefono("600000008");
			alumno.setActivo(true);
			alumno.setAutorizacionWeb(true);
			alumno.setEmail("tomcat-" + sufijo + "@example.test");
			return alumnos.save(alumno).getId();
		});
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
				  organizacion_id, fecha_alta, fecha_alta_inicial, fecha_grado, disciplina_id, grado_id)
				SELECT TRUE, FALSE, ?, 0, 1, 1, ?, '2023-09-01', '2023-09-01', '2024-01-01', d.id, g.id
				FROM disciplina d JOIN grado g ON g.sistema_grados_id = d.sistema_grados_id
				WHERE d.organizacion_id = ? AND d.codigo = 'KICKBOXING' AND g.codigo = 'BLANCO'""", alumnoId, ORG1, ORG1);
	}

	@BeforeEach
	void vaciarDisco() throws IOException {
		try (Stream<Path> rutas = Files.walk(DOCUMENTOS)) {
			rutas.filter(r -> !r.equals(DOCUMENTOS)).sorted(Comparator.reverseOrder()).forEach(r -> r.toFile().delete());
		}
	}

	/** Sin borrar nada: el alumno se retira para no interferir con otras clases. */
	@AfterAll
	void borrarRaiz() throws IOException {
		jdbc.update("UPDATE alumno SET activo = FALSE WHERE id = ?", alumnoId);
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
	void unaFamiliaPideUnVideoYUnTemario_conLasCabecerasDeSiempre_yCadaLecturaDejaSuFila() throws Exception {
		Path bloque = DOCUMENTOS.resolve("Materiales_Examen/kickboxing/b01_inicio_a_amarillo");
		byte[] contenido = new byte[256 * 1024];
		System.arraycopy("ftyp".getBytes(StandardCharsets.US_ASCII), 0, contenido, 4, 4);
		Files.createDirectories(bloque.resolve("videos"));
		Files.write(bloque.resolve("videos/01_guardia.mp4"), contenido);
		Files.createDirectories(bloque.resolve("temario"));
		Files.writeString(bloque.resolve("temario/temario.pdf"), "%PDF-1.7\nprueba");
		String raiz = "/api/alumnos/" + alumnoId + "/deportes/KICKBOXING/material-examen";
		String video = raiz + "/videos/01_guardia.mp4";

		HttpResponse<byte[]> completo = cliente.send(peticion(video).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
		assertEquals(200, completo.statusCode());
		assertEquals(contenido.length, completo.body().length);
		assertEquals(String.valueOf(contenido.length), completo.headers().firstValue("Content-Length").orElse(""));
		assertEquals("bytes", completo.headers().firstValue("Accept-Ranges").orElse(""));
		assertEquals("video/mp4", completo.headers().firstValue("Content-Type").orElse(""));
		assertTrue(completo.headers().firstValue("Content-Disposition").orElse("").startsWith("inline"));

		HttpResponse<byte[]> rango = cliente.send(peticion(video).header("Range", "bytes=4-7").GET().build(),
				HttpResponse.BodyHandlers.ofByteArray());
		assertEquals(206, rango.statusCode());
		assertEquals("ftyp", new String(rango.body(), StandardCharsets.US_ASCII));
		assertEquals("bytes 4-7/" + contenido.length, rango.headers().firstValue("Content-Range").orElse(""));
		assertEquals("4", rango.headers().firstValue("Content-Length").orElse(""));

		HttpResponse<byte[]> fuera = cliente.send(peticion(video).header("Range", "bytes=999999-").GET().build(),
				HttpResponse.BodyHandlers.ofByteArray());
		assertEquals(416, fuera.statusCode());

		HttpResponse<byte[]> temario = cliente.send(peticion(raiz + "/temario?download=true").GET().build(),
				HttpResponse.BodyHandlers.ofByteArray());
		assertEquals(200, temario.statusCode());
		assertTrue(temario.headers().firstValue("Content-Disposition").orElse("").startsWith("attachment"));

		// The filter writes the audit row after the body has been sent, so wait for the real write.
		esperar(() -> filas(video) == 3 && filas(raiz + "/temario") == 1, "cada lectura deja su fila");
		assertEquals(List.of(200, 206, 416), jdbc.queryForList(
				"SELECT estado_http FROM auditoria_evento WHERE endpoint = ? ORDER BY id", Integer.class, video));
		assertEquals(List.of(200), jdbc.queryForList(
				"SELECT estado_http FROM auditoria_evento WHERE endpoint = ? ORDER BY id", Integer.class, raiz + "/temario"));
		String parametros = jdbc.queryForObject("SELECT query_params_json FROM auditoria_evento WHERE endpoint = ?",
				String.class, raiz + "/temario");
		assertTrue(parametros.contains("download"), parametros);
	}

	@Test
	void unFormularioAnonimoEnUnaRutaDeMaterial_noSeLee() throws Exception {
		// Tomcat lee el cuerpo si se le piden los parámetros; aquí el cliente solo manda la cabecera y se queda esperando.
		for (String ruta : List.of(RUTA + "?nombre=a.mp4",
				"/api/alumnos/" + alumnoId + "/deportes/KICKBOXING/material-examen/videos/a.mp4")) {
			try (Socket socket = new Socket("localhost", puerto)) {
				socket.setSoTimeout(5_000);
				OutputStream salida = socket.getOutputStream();
				salida.write(("POST " + ruta + " HTTP/1.1\r\nHost: localhost\r\n"
						+ "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: 1000000\r\n\r\n")
						.getBytes(StandardCharsets.US_ASCII));
				salida.flush();

				String estado = new String(socket.getInputStream().readNBytes(12), StandardCharsets.US_ASCII);
				assertTrue(estado.contains("401"), ruta + ": " + estado);
			}
		}
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

	private int filas(String endpoint) {
		Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_evento WHERE endpoint = ?", Integer.class, endpoint);
		return total == null ? 0 : total;
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
