package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;

import com.taemoi.project.dtos.response.AuditoriaEventoDTO;
import com.taemoi.project.entities.AuditoriaAccion;
import com.taemoi.project.entities.AuditoriaEvento;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AuditoriaEventoRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.services.AuditoriaService;

/**
 * Auditoría frente al healthcheck y a la pantalla «Eventos de auditoría», contra MySQL real.
 *
 * <ul>
 * <li>{@code GET /healthz} responde 200 sin sesión y no deja fila en {@code auditoria_evento};
 * {@code GET /api/eventos} se sigue auditando.</li>
 * <li>Con {@code resultado=EXITO} e {@code incluirRuido=false} se devuelven exactamente las mismas filas y el
 * mismo total que con la consulta anterior. La referencia «anterior» se calcula con la consulta que SIGUE
 * aplicando el filtro de ruido en SQL (sin filtro de resultado e {@code incluirRuido=false}) restringida
 * después a 2xx: {@code 2xx ∧ ¬ruido} es justo lo que hacía la consulta antigua de EXITO.</li>
 * </ul>
 * Los datos de la consulta viven en una escuela propia para no depender de lo que escriban otros tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuditoriaEventosConsultaIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG_TAEMOI = 1L;

	@LocalServerPort int puerto;
	@Autowired JdbcTemplate jdbc;
	@Autowired OrganizacionRepository organizaciones;
	@Autowired AuditoriaEventoRepository eventos;
	@Autowired AuditoriaService auditoria;

	final HttpClient http = HttpClient.newHttpClient();

	long org;
	/** Filas 2xx sembradas (incluidas las que «parecen» ruido salvo por el estado). */
	Set<Long> exitos;
	/** Filas 4xx que cumplen todas las condiciones de ruido de escáner. */
	Set<Long> ruidosas;

	@BeforeAll
	void sembrar() {
		Organizacion o = new Organizacion();
		o.setSlug("it-auditoria-" + UUID.randomUUID().toString().substring(0, 8));
		o.setNombre("Escuela auditoría");
		org = TenantContext.calcularCon(ORG_TAEMOI, () -> organizaciones.save(o).getId());
		assertNotEquals(ORG_TAEMOI, org);

		long base = System.currentTimeMillis() - 3_600_000L;
		List<Fila> filas = List.of(
				// 2xx (EXITO)
				new Fila("e", AuditoriaAccion.READ, "GET", "/api/eventos", "eventos", 200, null, null),
				new Fila("e", AuditoriaAccion.CREATE, "POST", "/api/v1/upload", "v1", 201, null, null),
				new Fila("e", AuditoriaAccion.UPDATE, "PUT", "/api/alumnos/5", "alumnos", 204, 7L, "a@example.test"),
				new Fila("e", AuditoriaAccion.DELETE, "DELETE", "/api/storage/files", "storage", 200, null, ""),
				new Fila("e", AuditoriaAccion.READ, "GET", "/api/eventos", "eventos", 299, null, null),
				new Fila("e", AuditoriaAccion.CREATE, "POST", "/api/batch/import", null, 200, null, "  "),
				// 4xx ruido de escáner (anónimo, escritura, módulo desconocido, fragmento sospechoso, 401/403/404/405/415)
				new Fila("n", AuditoriaAccion.CREATE, "POST", "/api/v1/upload", "v1", 404, null, null),
				new Fila("n", AuditoriaAccion.UPDATE, "PUT", "/api/files/batch", "files", 405, null, "   "),
				new Fila("n", AuditoriaAccion.DELETE, "DELETE", "/api/storage/blob", "storage", 401, null, null),
				new Fila("n", AuditoriaAccion.CREATE, "POST", "/API/V2/Content", "v2", 415, null, null),
				// 4xx/5xx que NO son ruido (falta alguna condición)
				new Fila("m", AuditoriaAccion.CREATE, "POST", "/api/v1/upload", "v1", 404, 9L, "u@example.test"),
				new Fila("m", AuditoriaAccion.READ, "GET", "/api/v1/upload", "v1", 404, null, null),
				new Fila("m", AuditoriaAccion.CREATE, "POST", "/api/alumnos/upload", "alumnos", 403, null, null),
				new Fila("m", AuditoriaAccion.CREATE, "POST", "/api/v1/upload", "v1", 500, null, null),
				new Fila("m", AuditoriaAccion.CREATE, "POST", "/api/v1/upload", "v1", 400, null, null),
				// 3xx: ni EXITO ni ERROR
				new Fila("m", AuditoriaAccion.READ, "GET", "/api/eventos", "eventos", 302, null, null));

		List<Long> ids = TenantContext.calcularCon(org, () -> {
			List<Long> guardados = new ArrayList<>();
			for (int i = 0; i < filas.size(); i++) {
				// Dos filas con la misma fecha para cubrir el desempate por id.
				guardados.add(eventos.save(filas.get(i).entidad(new Date(base + (i / 2) * 1000L))).getId());
			}
			return guardados;
		});
		exitos = idsDeTipo(filas, ids, "e");
		ruidosas = idsDeTipo(filas, ids, "n");
	}

	@Test
	void exitoSinRuidoDevuelveLasMismasFilasYElMismoTotalQueAntes() {
		Page<AuditoriaEventoDTO> todosSinRuido = consultar(null, false, 1, 200);
		Page<AuditoriaEventoDTO> todosConRuido = consultar(null, true, 1, 200);

		// El filtro de ruido sigue activo en SQL y descarta justo las filas ruidosas sembradas.
		Set<Long> descartadas = ids(todosConRuido.getContent()).stream()
				.filter(id -> !ids(todosSinRuido.getContent()).contains(id))
				.collect(Collectors.toSet());
		assertEquals(ruidosas, descartadas);

		// Referencia de la consulta anterior de EXITO: 2xx ∧ ¬ruido, mismo orden.
		List<Long> referencia = todosSinRuido.getContent().stream()
				.filter(e -> e.getEstadoHttp() >= 200 && e.getEstadoHttp() <= 299)
				.map(AuditoriaEventoDTO::getId)
				.toList();
		assertEquals(exitos, Set.copyOf(referencia));

		// Tamaños menores que el total fuerzan la consulta COUNT del Page.
		for (int tamano : new int[] { 1, 2, 4, 25, 200 }) {
			for (boolean incluirRuido : new boolean[] { false, true }) {
				List<Long> obtenidos = new ArrayList<>();
				long total = -1;
				int paginas = -1;
				for (int pagina = 1; ; pagina++) {
					Page<AuditoriaEventoDTO> p = consultar("EXITO", incluirRuido, pagina, tamano);
					total = p.getTotalElements();
					paginas = p.getTotalPages();
					obtenidos.addAll(ids(p.getContent()));
					if (!p.hasNext()) {
						break;
					}
				}
				String caso = "tamano=" + tamano + ", incluirRuido=" + incluirRuido;
				assertEquals(referencia, obtenidos, caso);
				assertEquals(referencia.size(), total, caso);
				assertEquals((referencia.size() + tamano - 1) / tamano, paginas, caso);
			}
		}
	}

	@Test
	void errorSinRuidoSigueDescartandoElRuido() {
		Set<Long> errores = Set.copyOf(ids(consultar("ERROR", false, 1, 200).getContent()));
		Set<Long> erroresConRuido = Set.copyOf(ids(consultar("ERROR", true, 1, 200).getContent()));

		assertEquals(Set.of(), errores.stream().filter(ruidosas::contains).collect(Collectors.toSet()));
		assertEquals(true, erroresConRuido.containsAll(ruidosas));
		assertEquals(erroresConRuido.size() - ruidosas.size(), errores.size());
	}

	@Test
	void healthzResponde200SinSesionYNoSeAudita() throws Exception {
		long antes = contar("SELECT COUNT(*) FROM auditoria_evento");

		HttpResponse<String> r = enviar("/healthz");

		assertEquals(200, r.statusCode(), r.body());
		assertEquals("{\"status\":\"UP\"}", r.body());
		assertEquals(antes, contar("SELECT COUNT(*) FROM auditoria_evento"));
		assertEquals(0, contar("SELECT COUNT(*) FROM auditoria_evento WHERE endpoint LIKE '%healthz%'"));
	}

	@Test
	void apiEventosSeSigueAuditando() throws Exception {
		String sql = "SELECT COUNT(*) FROM auditoria_evento WHERE endpoint = '/api/eventos' AND metodo_http = 'GET'"
				+ " AND estado_http = 200 AND usuario_id IS NULL";
		long antes = contar(sql);

		HttpResponse<String> r = enviar("/api/eventos");

		assertEquals(200, r.statusCode(), r.body());
		assertEquals(antes + 1, contar(sql));
	}

	// ---------------------------------------------------------------------------------

	private Page<AuditoriaEventoDTO> consultar(String resultado, boolean incluirRuido, int pagina, int tamano) {
		return TenantContext.calcularCon(org, () -> auditoria.obtenerEventos(
				null, null, resultado, null, null, null, null, null, pagina, tamano, incluirRuido));
	}

	private long contar(String sql) {
		return jdbc.queryForObject(sql, Long.class);
	}

	private HttpResponse<String> enviar(String ruta) throws Exception {
		HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).GET().build();
		return http.send(peticion, HttpResponse.BodyHandlers.ofString());
	}

	private static List<Long> ids(List<AuditoriaEventoDTO> dtos) {
		return dtos.stream().map(AuditoriaEventoDTO::getId).toList();
	}

	private static Set<Long> idsDeTipo(List<Fila> filas, List<Long> ids, String tipo) {
		Set<Long> resultado = new java.util.HashSet<>();
		for (int i = 0; i < filas.size(); i++) {
			if (filas.get(i).tipo().equals(tipo)) {
				resultado.add(ids.get(i));
			}
		}
		return Set.copyOf(resultado);
	}

	private record Fila(String tipo, AuditoriaAccion accion, String metodo, String endpoint, String modulo,
			int estado, Long usuarioId, String usuarioEmail) {

		AuditoriaEvento entidad(Date fecha) {
			AuditoriaEvento e = new AuditoriaEvento();
			e.setFechaEvento(fecha);
			e.setAccion(accion);
			e.setMetodoHttp(metodo);
			e.setEndpoint(endpoint);
			e.setModulo(modulo);
			e.setEstadoHttp(estado);
			e.setUsuarioId(usuarioId);
			e.setUsuarioEmail(usuarioEmail);
			e.setPayloadTruncado(false);
			e.setResumen("IT " + tipo + " " + estado);
			return e;
		}
	}
}
