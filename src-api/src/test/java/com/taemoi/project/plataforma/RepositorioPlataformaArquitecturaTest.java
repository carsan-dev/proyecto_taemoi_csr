package com.taemoi.project.plataforma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Test de arquitectura (sin ArchUnit, que no es dependencia del proyecto) de la excepción documentada en
 * docs/multi-escuela/fase3-sedes-permisos.md §4: las lecturas entre escuelas del administrador de plataforma
 * van por un repositorio de solo lectura con {@code JdbcTemplate}, columnas explícitas y paquete propio, usado
 * solo por {@code /api/plataforma/**}.
 *
 * <p>Lee las fuentes de {@code src/main/java} (Maven ejecuta los tests con {@code src-api} como directorio
 * de trabajo): una consulta SQL que se salte {@code @TenantId} fuera de este paquete hace fallar la suite.
 */
class RepositorioPlataformaArquitecturaTest {

	private static final Path FUENTES = Path.of("src", "main", "java");
	private static final Path PAQUETE = FUENTES.resolve(Path.of("com", "taemoi", "project", "plataforma"));
	private static final Path REPOSITORIO = PAQUETE.resolve("ErroresPlataformaRepository.java");

	/** Accesos JDBC que no pasan por Hibernate (y por tanto no filtran por organización). */
	private static final Pattern JDBC_DIRECTO = Pattern.compile("\\b(JdbcTemplate|NamedParameterJdbcTemplate|JdbcClient)\\b");

	/** Columnas de auditoría que la plataforma no debe leer (datos personales y contenido de la petición). */
	private static final List<String> COLUMNAS_PROHIBIDAS = List.of("usuario_email", "usuario_nombre", "ip_cliente",
			"user_agent", "payload_json", "query_params_json");

	@Test
	void jdbcDirectoSoloEnElPaqueteDePlataforma() {
		List<Path> fuera = fuentes().filter(f -> !f.startsWith(PAQUETE))
				.filter(f -> JDBC_DIRECTO.matcher(leer(f)).find())
				.toList();
		assertEquals(List.of(), fuera, "JdbcTemplate/JdbcClient solo se permite en " + PAQUETE);
	}

	@Test
	void elRepositorioSoloSeUsaDesdeElPaqueteDePlataforma() {
		assertTrue(Files.isRegularFile(REPOSITORIO), REPOSITORIO + " no existe");
		Pattern uso = Pattern.compile("\\bErroresPlataformaRepository\\b");
		List<Path> fuera = fuentes().filter(f -> !f.startsWith(PAQUETE))
				.filter(f -> uso.matcher(leer(f)).find())
				.toList();
		assertEquals(List.of(), fuera);

		// Dentro del paquete, solo el servicio lo usa (el controlador pasa por el servicio).
		List<Path> usuarios = fuentes().filter(f -> f.startsWith(PAQUETE) && !f.equals(REPOSITORIO))
				.filter(f -> uso.matcher(leer(f)).find())
				.toList();
		assertEquals(List.of(PAQUETE.resolve("ErroresPlataformaService.java")), usuarios);
	}

	@Test
	void elRepositorioEsDeSoloLecturaConColumnasExplicitas() {
		String fuente = leer(REPOSITORIO);
		String sql = literales(fuente).toLowerCase(Locale.ROOT);

		assertTrue(sql.contains("select"), "El repositorio debe contener sus consultas SELECT");
		assertFalse(Pattern.compile("\\b(insert|update|delete|replace|merge|alter|drop|truncate|create|grant|call)\\b")
				.matcher(sql).find(), "Solo SELECT: " + sql);
		assertFalse(Pattern.compile("select\\s+(\\w+\\.)?\\*").matcher(sql).find(), "Columnas explícitas, nunca *");
		assertFalse(Pattern.compile("\\b(for\\s+update|lock\\s+in\\s+share\\s+mode)\\b").matcher(sql).find());
		for (String columna : COLUMNAS_PROHIBIDAS) {
			assertFalse(fuente.toLowerCase(Locale.ROOT).contains(columna), "Columna prohibida: " + columna);
		}
		// Solo métodos de lectura de JdbcTemplate.
		assertFalse(Pattern.compile("\\.(update|batchUpdate|execute)\\s*\\(").matcher(fuente).find(),
				"El repositorio no escribe");
		assertNotNull(ErroresPlataformaRepository.class.getAnnotation(Repository.class));
	}

	@Test
	void elControladorEstaProtegidoPorLaAutoridadDePlataforma() {
		PreAuthorize regla = PlataformaController.class.getAnnotation(PreAuthorize.class);
		assertNotNull(regla, "@PreAuthorize a nivel de clase");
		assertEquals("hasAuthority('" + AutoridadPlataforma.ADMIN_PLATAFORMA + "')", regla.value());
		assertEquals("ROLE_ADMIN_PLATAFORMA", AutoridadPlataforma.ADMIN_PLATAFORMA);
		RequestMapping ruta = PlataformaController.class.getAnnotation(RequestMapping.class);
		assertNotNull(ruta);
		for (String valor : ruta.value()) {
			assertTrue(valor.startsWith("/api/plataforma"), valor);
		}
	}

	// ---------------------------------------------------------------------------------

	private static Stream<Path> fuentes() {
		assertTrue(Files.isDirectory(FUENTES), "No se encuentra " + FUENTES.toAbsolutePath());
		try {
			return Files.walk(FUENTES).filter(f -> f.toString().endsWith(".java")).toList().stream();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String leer(Path fichero) {
		try {
			return Files.readString(fichero, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Concatenación de los literales de texto (simples y bloques de texto) del fichero. */
	private static String literales(String fuente) {
		StringBuilder sb = new StringBuilder();
		Matcher bloques = Pattern.compile("\"\"\"(.*?)\"\"\"", Pattern.DOTALL).matcher(fuente);
		while (bloques.find()) {
			sb.append(bloques.group(1)).append(' ');
		}
		String sinBloques = bloques.replaceAll("");
		Matcher simples = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(sinBloques);
		while (simples.find()) {
			sb.append(simples.group(1)).append(' ');
		}
		return sb.toString();
	}
}
