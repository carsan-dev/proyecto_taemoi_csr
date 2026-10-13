package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.archivos.AccesoArchivos;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Documento;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.entities.Membresia;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.DocumentoRepository;
import com.taemoi.project.repositories.ImagenRepository;
import com.taemoi.project.repositories.MembresiaRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.ContadorService;
import com.taemoi.project.services.ImagenService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Ficheros por escuela: subida con prefijo {@code org-<id>/} y ruta relativa, y servicio de
 * {@code /imagenes/**} y {@code /documentos/**} con la cadena de filtros completa (escuela activa +
 * Spring Security) contra MySQL real. Las raíces de ficheros apuntan a un directorio temporal.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArchivosPorOrganizacionIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final Path RAIZ;
	private static final Path IMAGENES;
	private static final Path DOCUMENTOS;

	static {
		try {
			RAIZ = Files.createTempDirectory("taemoi-it-archivos-").toAbsolutePath().normalize();
			IMAGENES = Files.createDirectories(RAIZ.resolve("imagenes"));
			DOCUMENTOS = Files.createDirectories(RAIZ.resolve("documentos"));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@DynamicPropertySource
	static void raicesDeFicheros(DynamicPropertyRegistry registro) {
		registro.add("app.imagenes.directorio.linux", () -> IMAGENES + "/");
		registro.add("app.imagenes.directorio.windows", () -> IMAGENES + "/");
		registro.add("app.documentos.directorio.linux", () -> DOCUMENTOS + "/");
		registro.add("app.documentos.directorio.windows", () -> DOCUMENTOS + "/");
	}

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired PasswordEncoder encoder;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired MembresiaRepository membresias;
	@Autowired OrganizacionRepository organizaciones;
	@Autowired AlumnoRepository alumnos;
	@Autowired DocumentoRepository documentos;
	@Autowired ImagenRepository imagenes;
	@Autowired ImagenService imagenService;
	@Autowired ContadorService contadores;
	@Autowired AccesoArchivos accesoArchivos;
	@Value("${app.base.url}") String baseUrl;

	final String sufijo = UUID.randomUUID().toString().substring(0, 8);
	long org2;
	long alumnoFamiliaA;
	long alumnoFamiliaB;
	long alumnoOrg2;
	String tokenAdmin1;
	String tokenAdmin2;
	String tokenFamiliaA;

	String rutaLegadoB;
	long documentoLegadoB;
	long documentoLegadoA;
	String imagenOrg2;
	String imagenLegado;

	@BeforeAll
	void preparar() throws Exception {
		org2 = TenantContext.calcularCon(ORG1, () -> {
			Organizacion org = new Organizacion();
			org.setSlug("it-archivos-" + sufijo);
			org.setNombre("Escuela archivos " + sufijo);
			return organizaciones.save(org).getId();
		});
		assertNotEquals(ORG1, org2);

		String emailFamiliaA = "fam-a-" + sufijo + "@example.test";
		alumnoFamiliaA = TenantContext.calcularCon(ORG1, () -> crearAlumno("FAMILIA A", emailFamiliaA));
		alumnoFamiliaB = TenantContext.calcularCon(ORG1, () -> crearAlumno("FAMILIA B", "fam-b-" + sufijo + "@example.test"));
		alumnoOrg2 = TenantContext.calcularCon(org2, () -> crearAlumno("ORG2", "org2-" + sufijo + "@example.test"));

		Usuario admin1 = crearUsuario("adm1-" + sufijo + "@example.test");
		dar(admin1, ORG1, RolMembresia.PROPIETARIO);
		Usuario admin2 = crearUsuario("adm2-" + sufijo + "@example.test");
		dar(admin2, org2, RolMembresia.PROPIETARIO);
		Usuario familiaA = crearUsuario(emailFamiliaA);
		dar(familiaA, ORG1, RolMembresia.FAMILIA);
		tokenAdmin1 = jwtService.generateToken(admin1, 600_000, ORG1);
		tokenAdmin2 = jwtService.generateToken(admin2, 600_000, org2);
		tokenFamiliaA = jwtService.generateToken(familiaA, 600_000, ORG1);

		// Documentos heredados (sin prefijo) de la escuela 1, con su fila.
		rutaLegadoB = "Documentos_Alumnos_Moiskimdo/9" + sufijo + "_FAMILIA_B/legado-b.pdf";
		escribir(DOCUMENTOS.resolve(rutaLegadoB), "legado-b");
		documentoLegadoB = TenantContext.calcularCon(ORG1, () -> crearDocumento(alumnoFamiliaB, rutaLegadoB));
		String rutaLegadoA = "Documentos_Alumnos_Moiskimdo/8" + sufijo + "_FAMILIA_A/legado-a.pdf";
		escribir(DOCUMENTOS.resolve(rutaLegadoA), "legado-a");
		documentoLegadoA = TenantContext.calcularCon(ORG1, () -> crearDocumento(alumnoFamiliaA, rutaLegadoA));
		// Fichero heredado SIN fila: no se sirve aunque esté en la escuela por defecto.
		escribir(DOCUMENTOS.resolve("Documentos_Alumnos_Moiskimdo/7" + sufijo + "_SIN_FILA/huerfano.pdf"), "huerfano");

		// Imagen privada de la escuela 2 (ruta relativa) e imagen heredada de la 1 (ruta absoluta).
		Imagen deOrg2 = TenantContext.calcularCon(org2, () -> {
			try {
				return imagenes.save(imagenService.guardarImagen(
						new MockMultipartFile("archivo", "foto.png", "image/png", png())));
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		});
		assertEquals("org-" + org2 + "/alumnos/" + deOrg2.getNombre(), deOrg2.getRuta());
		imagenOrg2 = deOrg2.getRuta();
		String nombreLegado = UUID.randomUUID() + "_legado.webp";
		Path archivoLegado = escribir(IMAGENES.resolve("alumnos").resolve(nombreLegado), "img-legado");
		TenantContext.ejecutarCon(ORG1,
				() -> imagenes.save(new Imagen(nombreLegado, "image/webp", "http://antiguo/imagenes/alumnos/" + nombreLegado,
						archivoLegado.toString())));
		imagenLegado = "alumnos/" + nombreLegado;

		// Ficheros públicos de eventos (sin fila).
		escribir(IMAGENES.resolve("eventos/cartel-" + sufijo + ".webp"), "cartel-legado");
		escribir(IMAGENES.resolve("org-" + org2 + "/eventos/cartel-" + sufijo + ".webp"), "cartel-org2");
		escribir(DOCUMENTOS.resolve("Documentos_Eventos/3_Open/bases-" + sufijo + ".pdf"), "bases-legado");
		escribir(DOCUMENTOS.resolve("org-" + org2 + "/eventos/7/bases-" + sufijo + ".pdf"), "bases-org2");
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	@AfterAll
	void borrarRaizTemporal() throws IOException {
		try (Stream<Path> rutas = Files.walk(RAIZ)) {
			rutas.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		}
	}

	// ------------------------------------------------------------------ subida

	@Test
	void subidaEnLaEscuela2QuedaBajoSuPrefijoConRutaRelativaYSoloLaVeSuEscuela() throws Exception {
		MockHttpServletResponse subida = mvc.perform(multipart("/api/alumnos/" + alumnoOrg2 + "/documentos")
				.file(new MockMultipartFile("archivo", "informe.pdf", "application/pdf", "pdf-org2".getBytes()))
				.cookie(jwt(tokenAdmin2))).andReturn().getResponse();
		assertEquals(200, subida.getStatus(), subida.getContentAsString());
		JsonNode documento = json.readTree(subida.getContentAsString());
		String ruta = "org-" + org2 + "/alumnos/" + alumnoOrg2 + "/informe.pdf";
		assertEquals(ruta, documento.get("ruta").asText());
		// Se guarda sin host y se sirve absoluta con app.base.url al serializar.
		assertEquals(baseUrl + "/documentos/" + ruta, documento.get("url").asText());
		assertEquals("/documentos/" + ruta,
				TenantContext.calcularCon(org2, () -> documentos.findByRuta(ruta).get(0).getUrl()));
		assertTrue(Files.isRegularFile(DOCUMENTOS.resolve(ruta)));

		MockHttpServletResponse propia = mvc.perform(get("/documentos/" + ruta).cookie(jwt(tokenAdmin2))).andReturn().getResponse();
		assertEquals(200, propia.getStatus());
		assertArrayEquals("pdf-org2".getBytes(), propia.getContentAsByteArray());
		assertTrue(propia.getHeader("Cache-Control").contains("no-store"));

		assertDenegado(get("/documentos/" + ruta).cookie(jwt(tokenAdmin1)));
		assertEquals(401, estado(get("/documentos/" + ruta)));
		assertEquals(403, estado(get("/documentos/" + ruta).cookie(jwt(tokenFamiliaA))));
	}

	// ------------------------------------------------------------------ heredados

	@Test
	void documentoHeredadoSoloSeSirveEnLaEscuelaPorDefecto() throws Exception {
		MockHttpServletResponse admin1 = mvc.perform(get("/documentos/" + rutaLegadoB).cookie(jwt(tokenAdmin1)))
				.andReturn().getResponse();
		assertEquals(200, admin1.getStatus());
		assertEquals("legado-b", admin1.getContentAsString());

		assertDenegado(get("/documentos/" + rutaLegadoB).cookie(jwt(tokenAdmin2)));
		assertDenegado(get("/documentos/Documentos_Alumnos_Moiskimdo/7" + sufijo + "_SIN_FILA/huerfano.pdf")
				.cookie(jwt(tokenAdmin1)));
	}

	@Test
	void imagenesPrivadasPorEscuela() throws Exception {
		assertEquals(200, estado(get("/imagenes/" + imagenOrg2).cookie(jwt(tokenAdmin2))));
		assertDenegado(get("/imagenes/" + imagenOrg2).cookie(jwt(tokenAdmin1)));
		assertEquals(401, estado(get("/imagenes/" + imagenOrg2)));
		assertDenegado(get("/imagenes/" + imagenOrg2.toUpperCase()));
		assertDenegado(get("/imagenes/" + imagenOrg2).cookie(jwt(tokenFamiliaA)));

		MockHttpServletResponse legado = mvc.perform(get("/imagenes/" + imagenLegado).cookie(jwt(tokenAdmin1)))
				.andReturn().getResponse();
		assertEquals(200, legado.getStatus());
		assertEquals("img-legado", legado.getContentAsString());
		assertDenegado(get("/imagenes/" + imagenLegado).cookie(jwt(tokenAdmin2)));
		assertEquals(401, estado(get("/imagenes/" + imagenLegado)));
	}

	// ------------------------------------------------------------------ públicos

	@Test
	void ficherosPublicosDeEventosSeSirvenSinSesion() throws Exception {
		MockHttpServletResponse cartel = mvc.perform(get("/imagenes/eventos/cartel-" + sufijo + ".webp")).andReturn().getResponse();
		assertEquals(200, cartel.getStatus());
		assertEquals("cartel-legado", cartel.getContentAsString());
		assertEquals(200, estado(get("/imagenes/org-" + org2 + "/eventos/cartel-" + sufijo + ".webp")));
		assertEquals(200, estado(get("/imagenes/org-" + org2 + "/eventos/cartel-" + sufijo + ".webp").cookie(jwt(tokenAdmin1))));
		assertEquals(200, estado(get("/documentos/Documentos_Eventos/3_Open/bases-" + sufijo + ".pdf")));
		assertEquals(200, estado(get("/documentos/org-" + org2 + "/eventos/7/bases-" + sufijo + ".pdf")));
		assertDenegado(get("/imagenes/eventos/no-existe-" + sufijo + ".webp"));
	}

	@Test
	void soloLasImagenesDeEventosSonPublicas() throws Exception {
		// Lista blanca: ficheros sueltos en la raíz o en carpetas desconocidas son privados y, sin fila, no se sirven.
		escribir(IMAGENES.resolve("suelta-" + sufijo + ".png"), "suelta");
		escribir(IMAGENES.resolve("otra-" + sufijo + "/x.png"), "otra");
		escribir(IMAGENES.resolve("org-" + org2 + "/logo-" + sufijo + ".png"), "logo");
		for (String ruta : List.of("suelta-" + sufijo + ".png", "otra-" + sufijo + "/x.png", "org-" + org2 + "/logo-" + sufijo + ".png")) {
			assertDenegado(get("/imagenes/" + ruta));
			assertDenegado(get("/imagenes/" + ruta).cookie(jwt(tokenAdmin1)));
			assertDenegado(get("/imagenes/" + ruta).cookie(jwt(tokenAdmin2)));
		}
	}

	@Test
	void lasUrlDeImagenSeSerializanAbsolutas() throws Exception {
		Imagen imagen = new Imagen("x.webp", "image/webp", "/imagenes/org-2/alumnos/x.webp", "org-2/alumnos/x.webp");
		JsonNode nodo = json.readTree(json.writeValueAsString(imagen));
		assertEquals(baseUrl + "/imagenes/org-2/alumnos/x.webp", nodo.get("url").asText());
		Imagen heredada = new Imagen("y.webp", "image/webp", "http://antiguo/imagenes/alumnos/y.webp", "/var/y.webp");
		assertEquals("http://antiguo/imagenes/alumnos/y.webp", json.readTree(json.writeValueAsString(heredada)).get("url").asText());
	}

	@Test
	void unaFotoConPrefijoNoRecurreAFicherosHeredadosDeLaRaiz() throws Exception {
		String nombre = UUID.randomUUID() + "_perdida.webp";
		// Fichero heredado con el mismo nombre en la carpeta de la escuela 1; el de la escuela 2 no existe.
		escribir(IMAGENES.resolve("alumnos").resolve(nombre), "fuga");
		long alumno = TenantContext.calcularCon(org2, () -> {
			Imagen foto = imagenes.save(new Imagen(nombre, "image/webp", "/imagenes/org-" + org2 + "/alumnos/" + nombre,
					"org-" + org2 + "/alumnos/" + nombre));
			Alumno a = alumnos.findById(crearAlumno("FOTO PERDIDA", "foto-" + sufijo + "@example.test")).orElseThrow();
			a.setFotoAlumno(foto);
			return alumnos.save(a).getId();
		});
		MockHttpServletResponse r = mvc.perform(get("/api/alumnos/" + alumno + "/imagen").cookie(jwt(tokenAdmin2)))
				.andReturn().getResponse();
		assertNotEquals(200, r.getStatus());
		assertFalse(r.getContentAsString().contains("fuga"));
		if (r.getStatus() == 302) {
			assertEquals(baseUrl + "/imagenes/org-" + org2 + "/alumnos/" + nombre, r.getHeader("Location"));
		}
	}

	// ------------------------------------------------------------------ recorridos

	@Test
	void recorridosDeRutaRechazados() throws Exception {
		String[] intentos = {
				"/documentos/org-" + org2 + "/eventos/..%2f..%2f" + rutaLegadoB,
				"/documentos/org-" + org2 + "/eventos/%2e%2e/%2e%2e/" + rutaLegadoB,
				"/documentos/Documentos_Eventos/../" + rutaLegadoB,
				"/documentos/Documentos_Eventos/..%5c" + rutaLegadoB,
				"/imagenes/eventos/../" + imagenLegado,
				"/imagenes/eventos/%2e%2e/" + imagenLegado,
				"/imagenes/eventos/..%2f" + imagenLegado,
				"/imagenes/" + imagenLegado.replace("alumnos/", "alumnos./"),
		};
		for (String intento : intentos) {
			MockHttpServletResponse r = mvc.perform(get(URI.create(intento))).andReturn().getResponse();
			assertNotEquals(200, r.getStatus(), intento);
			assertFalse(r.getContentAsString().contains("legado"), intento);
		}

		// La capa propia también los rechaza aunque el cortafuegos de Spring Security no estuviera.
		Authentication admin = new UsernamePasswordAuthenticationToken("adm", null,
				List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
		for (String intento : List.of("Documentos_Eventos/../" + rutaLegadoB, "Documentos_Eventos/%2e%2e/" + rutaLegadoB,
				"Documentos_Eventos/..%2f" + rutaLegadoB, DOCUMENTOS.resolve(rutaLegadoB).toString(),
				"/" + rutaLegadoB, rutaLegadoB.replace('/', '\\'))) {
			assertTrue(TenantContext.calcularCon(ORG1, () -> accesoArchivos.documento(intento, admin)).isEmpty(), intento);
		}
		for (String intento : List.of("eventos/../" + imagenLegado, IMAGENES.resolve(imagenLegado).toString())) {
			assertTrue(TenantContext.calcularCon(ORG1, () -> accesoArchivos.imagen(intento, admin)).isEmpty(), intento);
		}
		Optional<AccesoArchivos.ArchivoServible> legitimo = TenantContext.calcularCon(ORG1,
				() -> accesoArchivos.documento(rutaLegadoB, admin));
		assertTrue(legitimo.isPresent());
		assertFalse(legitimo.get().publico());
	}

	// ------------------------------------------------------------------ familias

	@Test
	void unaFamiliaNoDescargaDocumentosDeOtraFamilia() throws Exception {
		assertEquals(403, estado(get("/documentos/" + rutaLegadoB).cookie(jwt(tokenFamiliaA))));
		assertEquals(403, estado(get("/api/alumnos/" + alumnoFamiliaB + "/documentos/" + documentoLegadoB + "/descargar")
				.cookie(jwt(tokenFamiliaA))));
		assertEquals(403, estado(get("/api/alumnos/" + alumnoFamiliaB + "/documentos").cookie(jwt(tokenFamiliaA))));
		// Documento ajeno pedido a través del alumno propio: tampoco.
		assertNotEquals(200, estado(get("/api/alumnos/" + alumnoFamiliaA + "/documentos/" + documentoLegadoB + "/descargar")
				.cookie(jwt(tokenFamiliaA))));
		// Y su propio documento sí (la regla no se ha endurecido por error).
		MockHttpServletResponse propio = mvc.perform(get("/api/alumnos/" + alumnoFamiliaA + "/documentos/" + documentoLegadoA
				+ "/descargar").cookie(jwt(tokenFamiliaA))).andReturn().getResponse();
		assertEquals(200, propio.getStatus());
		assertEquals("legado-a", propio.getContentAsString(StandardCharsets.UTF_8));
		// Otra escuela tampoco lo alcanza por la API.
		assertEquals(404, estado(get("/api/alumnos/" + alumnoFamiliaB + "/documentos/" + documentoLegadoB + "/descargar")
				.cookie(jwt(tokenAdmin2))));
	}

	// ---------------------------------------------------------------------------------

	private void assertDenegado(MockHttpServletRequestBuilder peticion) throws Exception {
		MockHttpServletResponse r = mvc.perform(peticion).andReturn().getResponse();
		assertTrue(r.getStatus() == 403 || r.getStatus() == 404, "Esperado 403/404 y fue " + r.getStatus());
		String tipo = r.getContentType();
		assertTrue(r.getContentAsByteArray().length == 0 || (tipo != null && tipo.contains("json")),
				"No se devuelve el fichero");
	}

	private int estado(MockHttpServletRequestBuilder peticion) throws Exception {
		return mvc.perform(peticion).andReturn().getResponse().getStatus();
	}

	private static Cookie jwt(String token) {
		return new Cookie("jwt", token);
	}

	private static Path escribir(Path ruta, String contenido) throws IOException {
		Files.createDirectories(ruta.getParent());
		return Files.writeString(ruta, contenido);
	}

	private static byte[] png() throws IOException {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", salida);
		return salida.toByteArray();
	}

	private long crearDocumento(long alumnoId, String ruta) {
		Documento documento = new Documento();
		documento.setNombre(Path.of(ruta).getFileName().toString());
		documento.setTipo("application/pdf");
		documento.setRuta(ruta);
		documento.setUrl("http://antiguo/documentos/" + ruta);
		documento.setAlumno(alumnos.findById(alumnoId).orElseThrow());
		return documentos.save(documento).getId();
	}

	private Usuario crearUsuario(String email) {
		return TenantContext.calcularCon(ORG1, () -> {
			Usuario usuario = new Usuario();
			usuario.setNombre("Usuario");
			usuario.setApellidos("Archivos");
			usuario.setEmail(email);
			usuario.setContrasena(encoder.encode("Clave-Segura-123"));
			usuario.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(usuario);
		});
	}

	private void dar(Usuario usuario, Long organizacionId, RolMembresia rol) {
		TenantContext.ejecutarCon(ORG1, () -> membresias.save(new Membresia(usuario.getId(), organizacionId, rol)));
	}

	private long crearAlumno(String nombre, String email) {
		Alumno alumno = new Alumno();
		alumno.setNombre(nombre);
		alumno.setApellidos("PRUEBA ARCHIVOS");
		alumno.setNumeroExpediente(contadores.siguienteNumeroExpediente());
		alumno.setFechaNacimiento(Date.valueOf(LocalDate.of(2010, 5, 5)));
		alumno.setDireccion("Calle Prueba 1");
		alumno.setTelefono(600000000);
		alumno.setActivo(true);
		alumno.setAutorizacionWeb(true);
		alumno.setEmail(email);
		return alumnos.save(alumno).getId();
	}
}
