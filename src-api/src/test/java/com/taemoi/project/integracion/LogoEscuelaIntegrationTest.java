package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Membresia;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.MembresiaRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.ContadorService;
import com.taemoi.project.services.MarcaEscuela;
import com.taemoi.project.services.MarcaEscuelaService;
import com.taemoi.project.services.PDFService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza E7-7a (decisión 212), por HTTP real (MockMvc con la cadena de filtros completa) contra MySQL: el superadmin
 * sube y quita el logo de una escuela; nadie más puede; la acción queda en el registro de ESA escuela con ids y sin el
 * nombre del fichero; el logo anterior no queda huérfano; los documentos de la escuela lo usan (y dejan de usarlo).
 *
 * <p>Cada test crea su propia escuela (independiente del orden); los ficheros van a {@code target/it-files}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LogoEscuelaIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String CLAVE = "Clave-Segura-123";
	private static final String NOMBRE_FICHERO = "nombre-privado-del-fichero-subido.png";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired PasswordEncoder encoder;
	@Autowired UsuarioRepository usuarios;
	@Autowired MembresiaRepository membresias;
	@Autowired OrganizacionRepository organizaciones;
	@Autowired MarcaEscuelaService marcaEscuela;
	@Autowired PDFService pdfService;
	@Autowired AlumnoRepository alumnos;
	@Autowired ContadorService contadores;

	@MockitoBean JavaMailSender mailSender;

	@Value("${app.imagenes.directorio.windows}") String raizImagenes;

	final String sufijo = UUID.randomUUID().toString().substring(0, 8);
	Usuario superadmin;
	Cookie cookieSuperadmin;

	@BeforeAll
	void sembrar() throws Exception {
		superadmin = crearUsuario("logo-superadmin-" + sufijo + "@example.test");
		assertEquals(1, jdbc.update("UPDATE usuario SET es_admin_plataforma = TRUE WHERE id = ?", superadmin.getId()));
		cookieSuperadmin = new Cookie("jwt", login(superadmin.getEmail()));
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ permisos

	@Test
	void subirLogoSoloSuperadmin_403ParaElResto() throws Exception {
		long escuela = nuevaEscuela("Escuela Ficticia Permisos");
		for (RolMembresia rol : List.of(RolMembresia.PROPIETARIO, RolMembresia.ADMINISTRADOR, RolMembresia.PROFESOR,
				RolMembresia.FAMILIA)) {
			Usuario usuario = crearUsuario("logo-" + rol.name().toLowerCase() + "-" + sufijo + "@example.test");
			dar(usuario, escuela, rol);
			if (rol == RolMembresia.FAMILIA) {
				vincularAlumno(usuario, escuela);
			}
			Cookie cookie = new Cookie("jwt", login(usuario.getEmail()));
			for (MockHttpServletRequestBuilder peticion : peticionesDeLogo(escuela)) {
				mvc.perform(peticion.cookie(cookie)).andExpect(status().isForbidden());
			}
		}
		for (MockHttpServletRequestBuilder peticion : peticionesDeLogo(escuela)) {
			mvc.perform(peticion).andExpect(status().isUnauthorized());
		}
		assertNull(logoImagenId(escuela));
		assertEquals(0, ficherosDeLogo(escuela));
	}

	// ------------------------------------------------------------------ subir, sustituir, registrar

	@Test
	void subirLogoRegistraAccionConIds() throws Exception {
		long escuela = nuevaEscuela("Escuela Ficticia Logo");
		Usuario propietario = crearUsuario("logo-prop-" + sufijo + "@example.test");
		dar(propietario, escuela, RolMembresia.PROPIETARIO);

		byte[] original = png(logoFicticio(Color.RED, 120, 80));
		JsonNode respuesta = subir(escuela, original, NOMBRE_FICHERO, "image/png");
		assertEquals(escuela, respuesta.get("id").asLong());
		assertEquals("Escuela Ficticia Logo", respuesta.get("nombre").asText());
		assertTrue(respuesta.get("tieneLogo").asBoolean());
		assertFalse(respuesta.get("marcaHeredada").asBoolean());

		// Imagen de ESA escuela, en org-<id>/logo, solo el PNG y sin el nombre subido.
		Long imagenId = logoImagenId(escuela);
		assertNotNull(imagenId);
		Map<String, Object> imagen = jdbc.queryForMap(
				"SELECT organizacion_id, tipo, ruta, nombre, url FROM imagen WHERE id = ?", imagenId);
		assertEquals(escuela, ((Number) imagen.get("organizacion_id")).longValue());
		assertEquals("image/png", imagen.get("tipo"));
		String ruta = (String) imagen.get("ruta");
		assertTrue(ruta.matches("org-" + escuela + "/logo/[0-9a-f-]{36}_logo\\.png"), ruta);
		assertFalse(imagen.toString().contains("nombre-privado"), imagen.toString());
		Path fichero = Path.of(raizImagenes).resolve(ruta);
		BufferedImage guardada = ImageIO.read(fichero.toFile());
		assertEquals(120, guardada.getWidth());
		assertEquals(80, guardada.getHeight());

		// Registro de acciones de ESA escuela (D3): superadmin, acción, imagen y escuela por id.
		Map<String, Object> evento = jdbc.queryForMap("SELECT id, usuario_id, accion, metodo_http, endpoint, modulo, "
				+ "recurso_id, estado_http, resumen FROM auditoria_evento WHERE organizacion_id = ? AND modulo = "
				+ "'plataforma'", escuela);
		assertEquals(superadmin.getId(), ((Number) evento.get("usuario_id")).longValue());
		assertEquals("UPDATE", evento.get("accion"));
		assertEquals("PUT", evento.get("metodo_http"));
		assertEquals("/api/plataforma/escuelas/" + escuela + "/logo", evento.get("endpoint"));
		assertEquals(imagenId, ((Number) evento.get("recurso_id")).longValue());
		assertEquals(200, ((Number) evento.get("estado_http")).intValue());
		assertEquals("Superadmin: logo de la escuela " + escuela + " cambiado (imagen " + imagenId + ")",
				evento.get("resumen"));
		sinRegistroGenericoFueraDeLaEscuela(escuela);
		// El nombre del fichero subido no está en ninguna parte de la auditoría.
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_evento WHERE CONCAT_WS('|', endpoint, "
				+ "resumen, payload_json, query_params_json) LIKE ?", Integer.class, "%nombre-privado%"));

		// El propietario lo ve en su registro de acciones.
		MvcResult registro = mvc.perform(get("/api/admin/auditoria/eventos").param("modulo", "plataforma")
				.cookie(new Cookie("jwt", login(propietario.getEmail())))).andExpect(status().isOk()).andReturn();
		JsonNode eventos = json.readTree(registro.getResponse().getContentAsString()).get("content");
		assertEquals(1, eventos.size(), eventos.toString());
		assertEquals(imagenId, eventos.get(0).get("recursoId").asLong());
		assertEquals(superadmin.getId(), eventos.get(0).get("usuarioId").asLong());

		// Previsualización: el PNG guardado.
		MvcResult vista = mvc.perform(get("/api/plataforma/escuelas/" + escuela + "/logo").cookie(cookieSuperadmin))
				.andExpect(status().isOk()).andReturn();
		assertEquals(MediaType.IMAGE_PNG_VALUE, vista.getResponse().getContentType());
		assertArrayEquals(Files.readAllBytes(fichero), vista.getResponse().getContentAsByteArray());

		// Los documentos de la escuela lo llevan.
		MarcaEscuela marca = marcaEscuela.de(escuela);
		assertEquals("data:image/png;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(fichero)),
				marca.logoInformes());

		// Sustituir por un SVG: el anterior se borra (fila y fichero), sin huérfanos.
		String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"50\">"
				+ "<rect width=\"100\" height=\"50\" fill=\"#123456\"/></svg>";
		subir(escuela, svg.getBytes(StandardCharsets.UTF_8), "otro.svg", "image/svg+xml");
		Long nuevaId = logoImagenId(escuela);
		assertNotEquals(imagenId, nuevaId);
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM imagen WHERE id = ?", Integer.class, imagenId));
		assertFalse(Files.exists(fichero), "el fichero anterior no queda huérfano");
		assertEquals(1, ficherosDeLogo(escuela));
		String rutaNueva = jdbc.queryForObject("SELECT ruta FROM imagen WHERE id = ?", String.class, nuevaId);
		BufferedImage deSvg = ImageIO.read(Path.of(raizImagenes).resolve(rutaNueva).toFile());
		assertEquals(600, deSvg.getWidth());
		assertEquals(300, deSvg.getHeight());
		assertEquals("Superadmin: logo de la escuela " + escuela + " cambiado (imagen " + nuevaId
				+ ", sustituye a la imagen " + imagenId + ")", jdbc.queryForObject("SELECT resumen FROM "
						+ "auditoria_evento WHERE organizacion_id = ? AND modulo = 'plataforma' AND recurso_id = ?",
						String.class, escuela, nuevaId));
		assertNotEquals(marca.logoInformes(), marcaEscuela.de(escuela).logoInformes(), "caché invalidada");

		// Aparece en la lista de escuelas.
		JsonNode lista = json.readTree(mvc.perform(get("/api/plataforma/escuelas").cookie(cookieSuperadmin))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		for (JsonNode e : lista) {
			if (e.get("id").asLong() == escuela) {
				assertTrue(e.get("tieneLogo").asBoolean());
			}
			if (e.get("id").asLong() == ORG1) {
				assertTrue(e.get("marcaHeredada").asBoolean());
			}
		}
	}

	@Test
	void quitarLogoVuelveASoloNombre() throws Exception {
		long escuela = nuevaEscuela("Escuela Ficticia Sin Logo");
		subir(escuela, png(logoFicticio(Color.BLUE, 64, 64)), "a.png", "image/png");
		Long imagenId = logoImagenId(escuela);
		Path fichero = Path.of(raizImagenes)
				.resolve(jdbc.queryForObject("SELECT ruta FROM imagen WHERE id = ?", String.class, imagenId));
		assertFalse(marcaEscuela.de(escuela).logoInformes().isEmpty());

		JsonNode respuesta = json.readTree(mvc.perform(delete("/api/plataforma/escuelas/" + escuela + "/logo")
				.cookie(cookieSuperadmin)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		assertFalse(respuesta.get("tieneLogo").asBoolean());
		assertNull(logoImagenId(escuela));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM imagen WHERE id = ?", Integer.class, imagenId));
		assertFalse(Files.exists(fichero));
		Map<String, Object> evento = jdbc.queryForMap("SELECT usuario_id, accion, recurso_id, resumen FROM "
				+ "auditoria_evento WHERE organizacion_id = ? AND modulo = 'plataforma' AND accion = 'DELETE'", escuela);
		assertEquals(superadmin.getId(), ((Number) evento.get("usuario_id")).longValue());
		assertEquals(imagenId, ((Number) evento.get("recurso_id")).longValue());
		assertEquals("Superadmin: logo de la escuela " + escuela + " quitado (imagen " + imagenId + ")",
				evento.get("resumen"));
		sinRegistroGenericoFueraDeLaEscuela(escuela);

		// Solo el nombre: marca sin logo y un informe real de la escuela sin imágenes.
		MarcaEscuela marca = marcaEscuela.de(escuela);
		assertEquals("", marca.logoInformes());
		assertEquals("Escuela Ficticia Sin Logo", marca.nombre());
		byte[] informe = TenantContext.calcularCon(escuela, () -> pdfService.generarInformeAlumnasDpf(true));
		String texto = texto(informe);
		assertTrue(texto.contains("ESCUELA FICTICIA SIN LOGO"), texto);
		assertFalse(texto.toUpperCase().contains("KIM DO"), texto);
		assertEquals(0, imagenesDelPdf(informe));

		mvc.perform(get("/api/plataforma/escuelas/" + escuela + "/logo").cookie(cookieSuperadmin))
				.andExpect(status().isNotFound());
		// Quitar otra vez: sin cambios ni nuevo registro.
		mvc.perform(delete("/api/plataforma/escuelas/" + escuela + "/logo").cookie(cookieSuperadmin))
				.andExpect(status().isOk());
		assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_evento WHERE organizacion_id = ? AND "
				+ "modulo = 'plataforma'", Integer.class, escuela));
	}

	// ------------------------------------------------------------------ rechazos

	@Test
	void subirTipoNoPermitido_400() throws Exception {
		long escuela = nuevaEscuela("Escuela Ficticia Tipos");
		ByteArrayOutputStream gif = new ByteArrayOutputStream();
		ImageIO.write(logoFicticio(Color.RED, 32, 32), "gif", gif);
		String xxe = "<?xml version=\"1.0\"?><!DOCTYPE svg [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
				+ "<svg xmlns=\"http://www.w3.org/2000/svg\"><text>&x;</text></svg>";
		for (byte[] contenido : List.of(gif.toByteArray(), "no soy una imagen".getBytes(StandardCharsets.UTF_8),
				xxe.getBytes(StandardCharsets.UTF_8))) {
			// Con extensión y tipo declarado de PNG: decide el contenido.
			MvcResult r = mvc.perform(subida(escuela, contenido, "logo.png", "image/png").cookie(cookieSuperadmin))
					.andExpect(status().isBadRequest()).andReturn();
			assertTrue(json.readTree(r.getResponse().getContentAsString()).hasNonNull("mensaje"));
		}
		assertNull(logoImagenId(escuela));
		assertEquals(0, ficherosDeLogo(escuela));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_evento WHERE organizacion_id = ? AND "
				+ "modulo = 'plataforma'", Integer.class, escuela));
	}

	@Test
	void subirDemasiadoGrande_400() throws Exception {
		long escuela = nuevaEscuela("Escuela Ficticia Grande");
		byte[] grande = new byte[1024 * 1024 + 1];
		System.arraycopy(png(logoFicticio(Color.RED, 32, 32)), 0, grande, 0, 100);
		mvc.perform(subida(escuela, grande, "logo.png", "image/png").cookie(cookieSuperadmin))
				.andExpect(status().isBadRequest());
		assertNull(logoImagenId(escuela));
		assertEquals(0, ficherosDeLogo(escuela));
	}

	@Test
	void moiskimdoNoAdmiteLogoYEscuelaInexistente404() throws Exception {
		Long antes = logoImagenId(ORG1);
		byte[] logo = png(logoFicticio(Color.RED, 32, 32));
		mvc.perform(subida(ORG1, logo, "a.png", "image/png").cookie(cookieSuperadmin)).andExpect(status().isConflict());
		mvc.perform(delete("/api/plataforma/escuelas/" + ORG1 + "/logo").cookie(cookieSuperadmin))
				.andExpect(status().isConflict());
		assertEquals(antes, logoImagenId(ORG1));
		assertEquals(0, ficherosDeLogo(ORG1));
		assertTrue(marcaEscuela.de(ORG1).legado());

		long inexistente = 9_000_000_000L + Math.abs(sufijo.hashCode() % 1000);
		mvc.perform(subida(inexistente, logo, "a.png", "image/png").cookie(cookieSuperadmin))
				.andExpect(status().isNotFound());
		mvc.perform(delete("/api/plataforma/escuelas/" + inexistente + "/logo").cookie(cookieSuperadmin))
				.andExpect(status().isNotFound());
		mvc.perform(get("/api/plataforma/escuelas/" + inexistente + "/logo").cookie(cookieSuperadmin))
				.andExpect(status().isNotFound());
	}

	// ------------------------------------------------------------------ apoyo

	private List<MockHttpServletRequestBuilder> peticionesDeLogo(long escuela) throws Exception {
		return List.of(subida(escuela, png(logoFicticio(Color.RED, 32, 32)), "a.png", "image/png"),
				delete("/api/plataforma/escuelas/" + escuela + "/logo"),
				get("/api/plataforma/escuelas/" + escuela + "/logo"));
	}

	private MockHttpServletRequestBuilder subida(long escuela, byte[] contenido, String nombre, String tipo) {
		return multipart(HttpMethod.PUT, "/api/plataforma/escuelas/" + escuela + "/logo")
				.file(new MockMultipartFile("archivo", nombre, tipo, contenido));
	}

	private JsonNode subir(long escuela, byte[] contenido, String nombre, String tipo) throws Exception {
		MvcResult r = mvc.perform(subida(escuela, contenido, nombre, tipo).cookie(cookieSuperadmin))
				.andExpect(status().isOk()).andReturn();
		return json.readTree(r.getResponse().getContentAsString());
	}

	/**
	 * El cambio correcto del logo solo queda en la escuela destino: el filtro HTTP no lo apunta en la escuela activa del
	 * superadmin (su propietario vería la acción sobre otra escuela).
	 */
	private void sinRegistroGenericoFueraDeLaEscuela(long escuela) {
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_evento WHERE endpoint = ? AND metodo_http "
				+ "IN ('PUT', 'DELETE') AND estado_http BETWEEN 200 AND 299 AND organizacion_id <> ?", Integer.class,
				"/api/plataforma/escuelas/" + escuela + "/logo", escuela));
	}

	private Long logoImagenId(long escuela) {
		return jdbc.queryForObject("SELECT logo_imagen_id FROM organizacion WHERE id = ?", Long.class, escuela);
	}

	private long ficherosDeLogo(long escuela) throws Exception {
		Path carpeta = Path.of(raizImagenes).resolve("org-" + escuela).resolve("logo");
		if (!Files.isDirectory(carpeta)) {
			return 0;
		}
		try (Stream<Path> ficheros = Files.list(carpeta)) {
			return ficheros.count();
		}
	}

	/**
	 * Escuela nueva. Los ids se repiten entre ejecuciones (MySQL nuevo en cada JVM) y {@code target/it-files} no: se
	 * vacía la carpeta de logos que dejara una ejecución anterior con el mismo id.
	 */
	private long nuevaEscuela(String nombre) throws Exception {
		long id = TenantContext.calcularCon(ORG1, () -> {
			Organizacion org = new Organizacion();
			org.setSlug("it-logo-" + UUID.randomUUID().toString().substring(0, 12));
			org.setNombre(nombre);
			return organizaciones.save(org).getId();
		});
		Path carpeta = Path.of(raizImagenes).resolve("org-" + id).resolve("logo");
		if (Files.isDirectory(carpeta)) {
			try (Stream<Path> ficheros = Files.list(carpeta)) {
				for (Path fichero : ficheros.toList()) {
					Files.delete(fichero);
				}
			}
		}
		return id;
	}

	private Usuario crearUsuario(String email) {
		return TenantContext.calcularCon(ORG1, () -> {
			Usuario usuario = new Usuario();
			usuario.setNombre("Usuario");
			usuario.setApellidos("Logo");
			usuario.setEmail(email);
			usuario.setContrasena(encoder.encode(CLAVE));
			usuario.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(usuario);
		});
	}

	/** La familia solo abre sesión con algún alumno activo accesible: uno de la escuela, vinculado a la cuenta. */
	private void vincularAlumno(Usuario usuario, long escuela) {
		long alumnoId = TenantContext.calcularCon(escuela, () -> {
			Alumno alumno = new Alumno();
			alumno.setNombre("ALUMNO LOGO " + sufijo);
			alumno.setApellidos("PRUEBA");
			alumno.setNumeroExpediente(contadores.siguienteNumeroExpediente());
			alumno.setFechaNacimiento(java.sql.Date.valueOf(java.time.LocalDate.of(2012, 5, 5)));
			alumno.setDireccion("Calle Ficticia 1");
			alumno.setTelefono("600000000");
			alumno.setActivo(true);
			alumno.setAutorizacionWeb(true);
			alumno.setEmail(usuario.getEmail());
			return alumnos.save(alumno).getId();
		});
		jdbc.update("INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen) "
				+ "VALUES (?, ?, ?, 'PROPIO', 'MANUAL')", usuario.getId(), alumnoId, escuela);
	}

	private void dar(Usuario usuario, long organizacionId, RolMembresia rol) {
		TenantContext.calcularCon(ORG1, () -> membresias.save(new Membresia(usuario.getId(), organizacionId, rol)));
	}

	private String login(String email) throws Exception {
		MvcResult resultado = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return json.readTree(resultado.getResponse().getContentAsString()).get("token").asText();
	}

	/** PNG generado en el test (nunca un logo real). */
	private static BufferedImage logoFicticio(Color color, int ancho, int alto) {
		BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
		var g = imagen.createGraphics();
		g.setColor(color);
		g.fillRect(0, 0, ancho, alto);
		g.dispose();
		return imagen;
	}

	private static byte[] png(BufferedImage imagen) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(imagen, "png", out);
		return out.toByteArray();
	}

	private static String texto(byte[] pdf) throws Exception {
		try (org.apache.pdfbox.pdmodel.PDDocument documento = org.apache.pdfbox.pdmodel.PDDocument.load(pdf)) {
			return new org.apache.pdfbox.text.PDFTextStripper().getText(documento);
		}
	}

	private static int imagenesDelPdf(byte[] pdf) throws Exception {
		try (org.apache.pdfbox.pdmodel.PDDocument documento = org.apache.pdfbox.pdmodel.PDDocument
				.load(new ByteArrayInputStream(pdf))) {
			int total = 0;
			for (org.apache.pdfbox.cos.COSName nombre : documento.getPage(0).getResources().getXObjectNames()) {
				if (documento.getPage(0).getResources().getXObject(nombre)
						instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) {
					total++;
				}
			}
			return total;
		}
	}
}
