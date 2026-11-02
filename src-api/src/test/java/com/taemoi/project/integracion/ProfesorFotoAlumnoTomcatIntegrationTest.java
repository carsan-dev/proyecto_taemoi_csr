package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.taemoi.project.controllers.RespuestaFotoAlumno;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

/**
 * Foto del profesor cuando falla el servidor, sobre Tomcat real: MockMvc no hace el despacho de error y no puede decir qué
 * recibe el cliente. Un {@code IOException} no tiene handler: sale por los filtros con el estado aún en 200 y es el
 * contenedor quien fija el 500. Lo que no se sirvió deja fila, con ese estado, y una foto servida no.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProfesorFotoAlumnoTomcatIntegrationTest extends MySqlIntegrationTest {

	private static final int TAMANO_FOTO = 200_000;

	@LocalServerPort int puerto;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoSpyBean RespuestaFotoAlumno fotos;
	@MockitoBean JavaMailSender mailSender;

	private final HttpClient cliente = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
	EscuelaConSedesDePrueba e;
	String ruta;
	String sesion;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-foto-tomcat");
		long profesor = e.cuenta(usuarios, "proftomcat");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROFESOR')", profesor, e.org);
		jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) VALUES (?, ?, ?, 'TITULAR')",
				e.org, profesor, e.gA);
		long alumno = e.alumno("FOTOTOMCAT" + e.marca, null, true);
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", e.gA, alumno);
		ruta = "/api/profesor/alumnos/" + alumno + "/imagen";
		sesion = "jwt=" + e.token(jwtService, usuarios, profesor);
	}

	/** El punto de partida: una foto servida llega entera y sin fila. */
	@Test
	void unaFotoServida_200_sinFila() throws Exception {
		doReturn(foto(new ByteArrayResource(new byte[TAMANO_FOTO]))).when(fotos).paraProfesor(any(), any());
		long antes = filas(ruta);

		HttpResponse<byte[]> r = pedir();

		assertEquals(200, r.statusCode());
		assertEquals(TAMANO_FOTO, r.body().length);
		assertEquals(antes, filas(ruta));
	}

	@Test
	void unaExcepcionAlPrepararLaFoto_500_conFila() throws Exception {
		doThrow(new IOException("ruta-interna-del-disco")).when(fotos).paraProfesor(any(), any());
		long antes = filasCon500(ruta);

		HttpResponse<byte[]> r = pedir();

		assertEquals(500, r.statusCode());
		assertFalse(new String(r.body()).contains("ruta-interna-del-disco"), "el cuerpo no da detalles del fallo");
		assertEquals(antes + 1, filasCon500(ruta));
	}

	@Test
	void siNoSePuedeAbrirElFichero_500_conFila() throws Exception {
		doReturn(foto(new FotoQueFalla(() -> {
			throw new IOException("sin permiso");
		}))).when(fotos).paraProfesor(any(), any());
		long antes = filasCon500(ruta);

		HttpResponse<byte[]> r = pedir();

		assertEquals(500, r.statusCode());
		assertNotEquals("image/png", r.headers().firstValue("Content-Type").orElse(""));
		assertFalse(r.headers().firstValue("Cache-Control").orElse("").contains("max-age=28800"), "ni 8 h en el navegador");
		assertEquals(antes + 1, filasCon500(ruta));
	}

	/** Con las cabeceras ya enviadas no cabe un 500: Tomcat corta la conexión y el cliente no da la foto por buena. */
	@Test
	void unFalloAMitadDelCuerpo_cortaLaConexion_conFila() throws Exception {
		doReturn(foto(new FotoQueFalla(() -> new SequenceInputStream(new ByteArrayInputStream(new byte[TAMANO_FOTO / 2]),
				new InputStream() {
					@Override
					public int read() throws IOException {
						throw new IOException("disco");
					}
				})))).when(fotos).paraProfesor(any(), any());
		long antes = filasCon500(ruta);

		assertThrows(IOException.class, this::pedir);

		assertEquals(antes + 1, filasCon500(ruta));
	}

	private HttpResponse<byte[]> pedir() throws Exception {
		HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).GET()
				.timeout(Duration.ofSeconds(30)).header("Cookie", sesion).header("Accept", "image/webp,image/*;q=0.8").build();
		return cliente.send(peticion, HttpResponse.BodyHandlers.ofByteArray());
	}

	private static ResponseEntity<Resource> foto(Resource cuerpo) {
		return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
				.cacheControl(CacheControl.maxAge(Duration.ofHours(8)).cachePrivate().mustRevalidate()).body(cuerpo);
	}

	private long filas(String endpoint) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_evento WHERE endpoint = ?", Long.class, endpoint);
	}

	private long filasCon500(String endpoint) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_evento WHERE endpoint = ? AND estado_http = 500",
				Long.class, endpoint);
	}

	private interface Apertura {
		InputStream abrir() throws IOException;
	}

	/** Declara el tamaño de una foto y falla al abrirla o al leerla. */
	private static final class FotoQueFalla extends AbstractResource {

		private final Apertura apertura;

		FotoQueFalla(Apertura apertura) {
			this.apertura = apertura;
		}

		@Override
		public String getDescription() {
			return "foto que falla";
		}

		@Override
		public long contentLength() {
			return TAMANO_FOTO;
		}

		@Override
		public InputStream getInputStream() throws IOException {
			return apertura.abrir();
		}
	}
}
