package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

/**
 * Decisión 145: {@code GET /api/grupos}, {@code GET /api/turnos} y {@code GET /api/alumnos/con-deportes} ya no existen.
 * Fija qué responde cada una, también con {@code HEAD}, a cada rol: ninguna da 500, ninguna devuelve datos y ningún rol
 * gana acceso. Con un servidor real, no con MockMvc: los 404 y 405 de Spring MVC pasan por el despacho ERROR
 * ({@code /error}), que MockMvc no ejecuta.
 *
 * <ul>
 * <li>{@code /api/grupos} sigue existiendo para el {@code POST}, de ahí el 405 de la gestión. El responsable se queda
 * en la regla amplia {@code /api/grupos/**}, que es solo de gestión: 403.</li>
 * <li>{@code /api/turnos}: sin ningún método; 404 para la gestión y, por la regla amplia, 403 para el responsable.</li>
 * <li>{@code /api/alumnos/con-deportes} cae en {@code /api/alumnos/{id}}: pasa la regla de URL de esa ruta (la misma que
 * tenía) y la conversión a {@code Long} falla antes del {@code @PreAuthorize}: 400, como cualquier id no numérico.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ListadosCompletosRetiradosIntegrationTest extends MySqlIntegrationTest {

	private static final String ANONIMO = "anónimo";
	private static final String FAMILIA = "familia";
	private static final String ADMINISTRADOR = "administrador";
	private static final String PROPIETARIO = "propietario";
	private static final String RESPONSABLE = "responsable";
	private static final String PROFESOR = "profesor";

	/** Respuesta esperada por ruta y rol; mismo orden que {@link #ROLES}. */
	private static final Map<String, int[]> ESPERADO = Map.of(
			"/api/grupos", new int[] { 401, 403, 405, 405, 403, 403 },
			"/api/turnos", new int[] { 401, 403, 404, 404, 403, 403 },
			"/api/alumnos/con-deportes", new int[] { 401, 403, 400, 400, 400, 403 });
	private static final List<String> ROLES = List.of(ANONIMO, FAMILIA, ADMINISTRADOR, PROPIETARIO, RESPONSABLE,
			PROFESOR);

	@LocalServerPort int puerto;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	final HttpClient http = HttpClient.newHttpClient();
	final Map<String, String> tokens = new LinkedHashMap<>();

	EscuelaConSedesDePrueba e;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-145-retirados");
		long familia = e.cuentaFamilia(usuarios, "familia");
		long propietario = e.cuenta(usuarios, "propietario");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROPIETARIO')", propietario,
				e.org);
		long profesor = e.cuenta(usuarios, "profesor");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROFESOR')", profesor, e.org);
		tokens.put(FAMILIA, e.token(jwtService, usuarios, familia));
		tokens.put(ADMINISTRADOR, e.token(jwtService, usuarios, e.gestor));
		tokens.put(PROPIETARIO, e.token(jwtService, usuarios, propietario));
		tokens.put(RESPONSABLE, e.token(jwtService, usuarios, e.respA));
		tokens.put(PROFESOR, e.token(jwtService, usuarios, profesor));
	}

	@Test
	void cadaRolRecibeSuCodigoYNingunoVeDatos() {
		List<Executable> comprobaciones = new ArrayList<>();
		ESPERADO.forEach((ruta, codigos) -> {
			for (int i = 0; i < ROLES.size(); i++) {
				String rol = ROLES.get(i);
				int esperado = codigos[i];
				comprobaciones.add(() -> sinDatos(pedir("GET", ruta, rol), esperado, "GET " + ruta + " como " + rol));
				comprobaciones.add(() -> sinDatos(pedir("HEAD", ruta, rol), esperado, "HEAD " + ruta + " como " + rol));
			}
		});
		assertAll(comprobaciones);
	}

	/** Los sustitutos siguen respondiendo a quien tenía la ruta retirada, en el mismo servidor real. */
	@Test
	void losSustitutosSiguenRespondiendoALaGestionYAlResponsable() throws Exception {
		for (String rol : List.of(ADMINISTRADOR, PROPIETARIO, RESPONSABLE)) {
			for (String ruta : List.of("/api/grupos/resumen", "/api/turnos/resumen", "/api/alumnos",
					"/api/alumnos?page=1&size=5")) {
				HttpResponse<String> respuesta = pedir("GET", ruta, rol);
				assertEquals(200, respuesta.statusCode(), "GET " + ruta + " como " + rol + ": " + respuesta.body());
			}
		}
	}

	private HttpResponse<String> pedir(String metodo, String ruta, String rol) throws Exception {
		HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
				.header("Accept", "application/json").method(metodo, HttpRequest.BodyPublishers.noBody());
		String token = tokens.get(rol);
		if (token != null) {
			peticion.header("Cookie", "jwt=" + token);
		}
		return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
	}

	/** Ni lista ni nada de la escuela: el cuerpo es, como mucho, el error. */
	private void sinDatos(HttpResponse<String> respuesta, int esperado, String caso) {
		assertEquals(esperado, respuesta.statusCode(), caso + ": " + respuesta.body());
		String cuerpo = respuesta.body() == null ? "" : respuesta.body();
		assertFalse(cuerpo.stripLeading().startsWith("["), caso + ": devuelve una lista: " + cuerpo);
		assertFalse(cuerpo.contains(e.marca), caso + ": devuelve datos de la escuela: " + cuerpo);
	}
}
