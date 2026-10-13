package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Despacho ERROR ({@code /error}) con un servidor real: con open-in-view, la sesión de Hibernate que
 * se abre al despachar {@code /error} necesita organización. Las respuestas de error (404, 400, 401,
 * 403) deben conservar su cuerpo JSON sin trazas de «No hay organización activa».
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DespachoDeErrorIntegrationTest extends MySqlIntegrationTest {

	private static final String SIN_ORGANIZACION = "No hay organización activa";

	@LocalServerPort int puerto;
	@Autowired ObjectMapper json;
	@Autowired JwtService jwtService;
	@Autowired PasswordEncoder encoder;
	@Autowired UsuarioRepository usuarios;
	@Autowired MembresiaRepository membresias;
	@Autowired AlumnoRepository alumnos;
	@Autowired ContadorService contadores;

	final HttpClient http = HttpClient.newHttpClient();
	String tokenFamilia;

	@BeforeAll
	void crearFamilia() {
		String email = "familia-error-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
		Usuario familia = TenantContext.calcularCon(1L, () -> {
			Alumno alumno = new Alumno();
			alumno.setNombre("ERROR");
			alumno.setApellidos("DESPACHO");
			alumno.setNumeroExpediente(contadores.siguienteNumeroExpediente());
			alumno.setFechaNacimiento(Date.valueOf(LocalDate.of(2010, 5, 5)));
			alumno.setDireccion("Calle 1");
			alumno.setTelefono(600000000);
			alumno.setActivo(true);
			alumno.setAutorizacionWeb(true);
			alumno.setEmail(email);
			alumnos.save(alumno);
			Usuario usuario = new Usuario();
			usuario.setNombre("Familia");
			usuario.setApellidos("Error");
			usuario.setEmail(email);
			usuario.setContrasena(encoder.encode("Clave-Segura-123"));
			usuario.setAuthProvider(AuthProvider.LOCAL);
			Usuario guardado = usuarios.save(usuario);
			membresias.save(new Membresia(guardado.getId(), 1L, RolMembresia.FAMILIA));
			return guardado;
		});
		tokenFamilia = jwtService.generateToken(familia, 600_000, 1L);
	}

	@Test
	void rutaDesconocida404(CapturedOutput salida) throws Exception {
		HttpResponse<String> r = enviar(get("/api/no-existe-" + UUID.randomUUID()).header("Cookie", "jwt=" + tokenFamilia));

		assertEquals(404, r.statusCode(), r.body());
		assertEquals(404, cuerpo(r).get("status").asInt(), r.body());
		sinErrorDeOrganizacion(salida);
	}

	@Test
	void cuerpoInvalido400(CapturedOutput salida) throws Exception {
		HttpResponse<String> malformado = enviar(post("/api/auth/signin", "{malformado"));
		assertEquals(400, malformado.statusCode(), malformado.body());
		assertEquals(400, cuerpo(malformado).get("status").asInt(), malformado.body());

		HttpResponse<String> invalido = enviar(post("/api/auth/password/forgot", "{}"));
		assertEquals(400, invalido.statusCode(), invalido.body());
		assertEquals(400, cuerpo(invalido).get("status").asInt(), invalido.body());
		sinErrorDeOrganizacion(salida);
	}

	@Test
	void sinAutenticar401(CapturedOutput salida) throws Exception {
		HttpResponse<String> api = enviar(get("/api/admin/users"));
		assertEquals(401, api.statusCode(), api.body());
		assertEquals("No autenticado.", cuerpo(api).get("mensaje").asText());

		// Fuera de /api la cadena de seguridad usa sendError(401) → despacho ERROR a /error.
		HttpResponse<String> documento = enviar(get("/documentos/privado.pdf"));
		assertEquals(401, documento.statusCode(), documento.body());
		assertEquals(401, cuerpo(documento).get("status").asInt(), documento.body());
		sinErrorDeOrganizacion(salida);
	}

	@Test
	void autenticadoSinRol403(CapturedOutput salida) throws Exception {
		HttpResponse<String> api = enviar(get("/api/admin/users").header("Cookie", "jwt=" + tokenFamilia));
		assertEquals(403, api.statusCode(), api.body());
		assertEquals("Acceso denegado.", cuerpo(api).get("mensaje").asText());

		HttpResponse<String> documento = enviar(get("/documentos/privado.pdf").header("Cookie", "jwt=" + tokenFamilia));
		assertEquals(403, documento.statusCode(), documento.body());
		assertEquals(403, cuerpo(documento).get("status").asInt(), documento.body());
		sinErrorDeOrganizacion(salida);
	}

	// ---------------------------------------------------------------------------------

	private HttpRequest.Builder get(String ruta) {
		return HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
				.header("Accept", "application/json").GET();
	}

	private HttpRequest.Builder post(String ruta, String cuerpo) {
		return HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
				.header("Accept", "application/json").header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(cuerpo));
	}

	private HttpResponse<String> enviar(HttpRequest.Builder peticion) throws Exception {
		return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
	}

	private JsonNode cuerpo(HttpResponse<String> respuesta) throws Exception {
		assertFalse(respuesta.body() == null || respuesta.body().isBlank(), "Respuesta sin cuerpo JSON");
		return json.readTree(respuesta.body());
	}

	private static void sinErrorDeOrganizacion(CapturedOutput salida) {
		assertFalse(salida.getAll().contains(SIN_ORGANIZACION),
				"Se registró un error por falta de organización durante el despacho de error");
	}
}
