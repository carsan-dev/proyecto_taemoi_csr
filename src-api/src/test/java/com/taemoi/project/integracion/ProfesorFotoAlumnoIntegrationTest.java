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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.FileSystemUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.controllers.RespuestaFotoAlumno;
import com.taemoi.project.dtos.response.GrupoConAlumnosDTO;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;

/**
 * Foto de los alumnos para el profesor contra MySQL y la cadena de filtros completa: {@code fotoId} en la lista del
 * grupo y {@code GET /api/profesor/alumnos/{alumnoId}/imagen}, con ficheros en un directorio temporal. Fija también la
 * ruta de familias y gestión ({@code /api/alumnos/{alumnoId}/imagen}), escrita sobre el código anterior a la extracción
 * de {@code RespuestaFotoAlumno}: debe seguir igual después. Datos inventados, en dos escuelas nuevas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProfesorFotoAlumnoIntegrationTest extends MySqlIntegrationTest {

	private static final String CACHE_PROFESOR = "max-age=28800, must-revalidate, private";
	private static final String CACHE_FAMILIAS = "max-age=28800, must-revalidate, public";
	private static final byte[] SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
			.getBytes(StandardCharsets.UTF_8);
	private static final Path IMAGENES;

	static {
		try {
			IMAGENES = Files.createTempDirectory("taemoi-it-foto-profesor-").toAbsolutePath().normalize();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@DynamicPropertySource
	static void raizDeImagenes(DynamicPropertyRegistry registro) {
		registro.add("app.imagenes.directorio.linux", () -> IMAGENES + "/");
		registro.add("app.imagenes.directorio.windows", () -> IMAGENES + "/");
	}

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;
	@Autowired EntityManagerFactory emf;
	@Autowired RespuestaFotoAlumno fotos;

	@Value("${app.base.url}") String baseUrl;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	long profA, profDosEscuelas, profRP, profFP, gestorProf;
	long conFoto, enSuOtroGrupo, deOtroGrupo, deOtraEscuela, deBaja, ficheroPerdido, conSvg, conHtml, propioFamilia;
	long fotoConFoto, grupoSoloDeProfA;
	byte[] png;
	String tProfA, tProfB, tProfRP, tProfFP, tGestorProf, tProfOtra, tProfSinGrupos, tProfDosEnE, tProfDosEnOtra;
	String tGestor, tPropietario, tFamilia, tResponsable;

	@BeforeAll
	void preparar() throws IOException {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-foto-prof");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-foto-prof-otra");
		e.crearResponsablesSolos(usuarios);
		png = imagen(1000, 500, "png");

		profA = soloProfesor(e, "profa");
		asignar(e, profA, e.gA);
		asignar(e, profA, e.gA2);
		grupoSoloDeProfA = e.gA2;
		long profB = soloProfesor(e, "profb");
		asignar(e, profB, e.gB);
		profRP = soloProfesor(e, "profrp");
		e.responsable(profRP, e.sA);
		asignar(e, profRP, e.gB);
		profFP = e.cuentaFamilia(usuarios, "proffp");
		membresia(e, profFP, "PROFESOR");
		asignar(e, profFP, e.gB);
		gestorProf = e.cuenta(usuarios, "gestorprof");
		membresia(e, gestorProf, "ADMINISTRADOR");
		membresia(e, gestorProf, "PROFESOR");
		asignar(e, gestorProf, e.gA);
		long profSinGrupos = soloProfesor(e, "profsingrupos");
		long profOtra = soloProfesor(otra, "profotra");
		asignar(otra, profOtra, otra.gA);
		// La misma cuenta da clase en las dos escuelas: su sesión ve solo los grupos de la escuela activa.
		profDosEscuelas = soloProfesor(e, "profdos");
		membresia(otra, profDosEscuelas, "PROFESOR");
		asignar(e, profDosEscuelas, e.gA);
		asignar(otra, profDosEscuelas, otra.gA);
		long propietario = e.cuenta(usuarios, "propietario");
		membresia(e, propietario, "PROPIETARIO");
		long familia = e.cuentaFamilia(usuarios, "familia");

		conFoto = enGrupo(e, e.gA, "FOTOPNG");
		fotoConFoto = foto(e, conFoto, "image/png", "png", png);
		enSuOtroGrupo = enGrupo(e, e.gA2, "FOTOJPEG");
		foto(e, enSuOtroGrupo, "image/jpeg", "jpg", imagen(400, 300, "jpg"));
		deOtroGrupo = enGrupo(e, e.gB, "FOTOGB");
		foto(e, deOtroGrupo, "image/png", "png", png);
		deOtraEscuela = enGrupo(otra, otra.gA, "FOTOOTRA");
		foto(otra, deOtraEscuela, "image/png", "png", png);
		deBaja = e.alumno("FOTOBAJA" + e.marca, e.sA, false);
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", e.gA, deBaja);
		foto(e, deBaja, "image/png", "png", png);
		ficheroPerdido = enGrupo(e, e.gA, "FOTOPERDIDA");
		foto(e, ficheroPerdido, "image/png", "png", null);
		conSvg = enGrupo(e, e.gA, "FOTOSVG");
		foto(e, conSvg, "image/svg+xml", "svg", SVG);
		conHtml = enGrupo(e, e.gA, "FOTOHTML");
		foto(e, conHtml, "text/html", "html", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));
		// El alumno de la familia está en gA: ni así la familia entra por la ruta del profesor.
		propioFamilia = jdbc.queryForObject("SELECT MAX(alumno_id) FROM usuario_alumno WHERE usuario_id = ?", Long.class,
				familia);
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", e.gA, propioFamilia);
		foto(e, propioFamilia, "image/png", "png", png);

		tProfA = e.token(jwtService, usuarios, profA);
		tProfB = e.token(jwtService, usuarios, profB);
		tProfRP = e.token(jwtService, usuarios, profRP);
		tProfFP = e.token(jwtService, usuarios, profFP);
		tGestorProf = e.token(jwtService, usuarios, gestorProf);
		tProfSinGrupos = e.token(jwtService, usuarios, profSinGrupos);
		tProfOtra = otra.token(jwtService, usuarios, profOtra);
		tProfDosEnE = e.token(jwtService, usuarios, profDosEscuelas);
		tProfDosEnOtra = otra.token(jwtService, usuarios, profDosEscuelas);
		tGestor = e.token(jwtService, usuarios, e.gestor);
		tPropietario = e.token(jwtService, usuarios, propietario);
		tFamilia = e.token(jwtService, usuarios, familia);
		tResponsable = e.token(jwtService, usuarios, e.respSoloA);
	}

	@AfterAll
	void borrarFicheros() throws IOException {
		FileSystemUtils.deleteRecursively(IMAGENES);
	}

	@AfterEach
	void sinFugas() {
		assertFalse(TenantContext.get() != null, "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ fotoId en la lista del grupo

	@Test
	void lista_traeElIdDeLaFoto_nuloSinFoto_ySoloEseDato() throws Exception {
		JsonNode alumnos = leer(get("/api/profesor/grupos/" + e.gA + "/alumnos"), tProfA);
		JsonNode alumno = fila(alumnos, conFoto);
		assertEquals(fotoConFoto, alumno.get("fotoId").asLong());
		assertEquals(Set.of("id", "nombre", "apellidos", "fotoId", "deportes"), campos(alumno));
		JsonNode sinFoto = fila(alumnos, e.aSinSede);
		assertTrue(sinFoto.has("fotoId") && sinFoto.get("fotoId").isNull(), sinFoto.toString());
		String cuerpo = alumnos.toString();
		assertFalse(cuerpo.contains("/alumnos/") || cuerpo.contains("/imagen") || cuerpo.contains("org-"),
				"ni ruta ni URL de la foto: " + cuerpo);
	}

	@Test
	void lista_sinConsultasDeMasPorCadaFoto() throws Exception {
		long conUna = sentenciasDeLaLista();
		long alumno = enGrupo(e, grupoSoloDeProfA, "FOTOEXTRA" + UUID.randomUUID().toString().substring(0, 4));
		long otroAlumno = enGrupo(e, grupoSoloDeProfA, "FOTOEXTRA" + UUID.randomUUID().toString().substring(0, 4));
		foto(e, alumno, "image/png", "png", png);
		foto(e, otroAlumno, "image/png", "png", png);
		assertEquals(conUna, sentenciasDeLaLista(), "dos alumnos más con foto no añaden consultas");
	}

	// ------------------------------------------------------------------ ruta del profesor: la foto

	@Test
	void profesorDelGrupo_200_conElTipoYSinCacheCompartida() throws Exception {
		MockHttpServletResponse r = respuesta(get(fotoProfesor(conFoto)), tProfA);
		assertEquals(200, r.getStatus());
		assertEquals("image/png", r.getContentType());
		assertArrayEquals(png, r.getContentAsByteArray());
		assertEquals(CACHE_PROFESOR, r.getHeader("Cache-Control"));
		assertEquals("nosniff", r.getHeader("X-Content-Type-Options"));
		assertNotNull(r.getHeader("ETag"));
		assertNotNull(r.getHeader("Last-Modified"));
		assertTrue(r.getHeader("Content-Disposition").startsWith("inline;"), r.getHeader("Content-Disposition"));
		assertNull(r.getHeader("Location"));
		assertEquals(200, respuesta(head(fotoProfesor(conFoto)), tProfA).getStatus());
	}

	@Test
	void conAncho_redimensionaComoLaRutaDeFamilias() throws Exception {
		MockHttpServletResponse original = respuesta(get(fotoProfesor(conFoto)), tProfA);
		MockHttpServletResponse reducida = respuesta(get(fotoProfesor(conFoto)).param("w", "300"), tProfA);
		assertEquals(200, reducida.getStatus());
		assertEquals(CACHE_PROFESOR, reducida.getHeader("Cache-Control"));
		assertNotEquals(original.getHeader("ETag"), reducida.getHeader("ETag"));
		if (hayCodificadorWebp()) {
			assertEquals("image/webp", reducida.getContentType());
			assertTrue(reducida.getContentAsByteArray().length < png.length);
		} else {
			assertEquals("image/png", reducida.getContentType());
		}

		// Sin ampliar: por encima del ancho original se sirve tal cual.
		MockHttpServletResponse ancha = respuesta(get(fotoProfesor(conFoto)).param("w", "1500"), tProfA);
		assertEquals("image/png", ancha.getContentType());
		assertArrayEquals(png, ancha.getContentAsByteArray());
		assertEquals(400, respuesta(get(fotoProfesor(conFoto)).param("w", "x"), tProfA).getStatus());
	}

	@Test
	void conElEtagDeLaFoto_304() throws Exception {
		String etag = respuesta(get(fotoProfesor(conFoto)), tProfA).getHeader("ETag");
		MockHttpServletResponse r = respuesta(get(fotoProfesor(conFoto)).header("If-None-Match", etag), tProfA);
		assertEquals(304, r.getStatus());
		assertEquals(0, r.getContentAsByteArray().length);
		// El 304 también pasa por el permiso: el ETag de una foto no abre la de un alumno ajeno.
		assertEquals(403, respuesta(get(fotoProfesor(conFoto)).header("If-None-Match", etag), tProfB).getStatus());
	}

	@Test
	void alumnoDeOtroGrupoSuyo_200() throws Exception {
		MockHttpServletResponse r = respuesta(get(fotoProfesor(enSuOtroGrupo)), tProfA);
		assertEquals(200, r.getStatus(), "no hay 403 falso: profA da clase en gA y en gA2");
		assertEquals("image/jpeg", r.getContentType());
	}

	@Test
	void alumnoDeOtroGrupo_403() throws Exception {
		assertEquals(403, respuesta(get(fotoProfesor(deOtroGrupo)), tProfA).getStatus(), "solo está en gB");
		assertEquals(403, respuesta(head(fotoProfesor(deOtroGrupo)), tProfA).getStatus());
		assertEquals(403, respuesta(get(fotoProfesor(conFoto)), tProfB).getStatus());
		assertEquals(403, respuesta(get(fotoProfesor(conFoto)), tProfSinGrupos).getStatus());
		assertEquals(403, respuesta(get(fotoProfesor(999_999_999L)), tProfA).getStatus(), "no revela si existe");
		assertEquals(403, respuesta(get(fotoProfesor(e.aNada)), tProfA).getStatus(), "sin foto ni grupo, igual");
		assertEquals(403, respuesta(get(fotoProfesor(e.aInactivoA)), tProfA).getStatus(), "de baja y ajeno, 403");
	}

	@Test
	void alumnoDeOtraEscuela_403_tambienConLaMismaCuentaEnLasDos() throws Exception {
		assertEquals(403, respuesta(get(fotoProfesor(deOtraEscuela)), tProfA).getStatus());
		assertEquals(403, respuesta(get(fotoProfesor(conFoto)), tProfOtra).getStatus());

		assertEquals(200, respuesta(get(fotoProfesor(conFoto)), tProfDosEnE).getStatus());
		assertEquals(403, respuesta(get(fotoProfesor(deOtraEscuela)), tProfDosEnE).getStatus(),
				"la otra escuela no está activa");
		assertEquals(200, respuesta(get(fotoProfesor(deOtraEscuela)), tProfDosEnOtra).getStatus());
		assertEquals(403, respuesta(get(fotoProfesor(conFoto)), tProfDosEnOtra).getStatus(),
				"el grupo de la escuela no activa no cuenta");
	}

	@Test
	void alumnoDeBaja_404() throws Exception {
		MockHttpServletResponse r = respuesta(get(fotoProfesor(deBaja)), tProfA);
		assertEquals(404, r.getStatus(), "D7-13, como la ficha");
		assertNull(r.getHeader("Location"));
		assertEquals(0, r.getContentAsByteArray().length);
	}

	@Test
	void sinFoto_404() throws Exception {
		assertEquals(404, respuesta(get(fotoProfesor(e.aSinSede)), tProfA).getStatus());
	}

	/** {@code /imagenes/...} es solo de gestión: redirigir al profesor le daría un 403 en vez de las iniciales. */
	@Test
	void ficheroBorrado_404_sinRedireccion() throws Exception {
		MockHttpServletResponse r = respuesta(get(fotoProfesor(ficheroPerdido)), tProfA);
		assertEquals(404, r.getStatus());
		assertNull(r.getHeader("Location"));
		assertEquals(404, respuesta(get(fotoProfesor(ficheroPerdido)).param("w", "300"), tProfA).getStatus());
	}

	@Test
	void unTipoQueNoEsFotoRasterNoSeSirve_404() throws Exception {
		for (long alumno : List.of(conSvg, conHtml)) {
			MockHttpServletResponse r = respuesta(get(fotoProfesor(alumno)), tProfA);
			assertEquals(404, r.getStatus());
			assertFalse(r.getContentAsString().contains("alert(1)"));
			assertEquals(404, respuesta(get(fotoProfesor(alumno)).param("w", "300"), tProfA).getStatus());
		}
	}

	/** Con {@code application/octet-stream} por defecto daría 200 con el SVG dentro: sin tipo válido no hay foto. */
	@Test
	void unTipoVacioOInvalido_404_sinBytes() throws Exception {
		for (String tipo : List.of("", "   ", "no es un tipo", "image/", "/", "application/octet-stream", "image/*")) {
			long alumno = enGrupo(e, e.gA, "FOTOTIPO" + UUID.randomUUID().toString().substring(0, 6));
			foto(e, alumno, tipo, "svg", SVG);
			for (MockHttpServletRequestBuilder peticion : List.of(get(fotoProfesor(alumno)),
					get(fotoProfesor(alumno)).param("w", "300"))) {
				MockHttpServletResponse r = respuesta(peticion, tProfA);
				assertEquals(404, r.getStatus(), "tipo «" + tipo + "»");
				assertEquals(0, r.getContentAsByteArray().length, "tipo «" + tipo + "»");
				assertNull(r.getHeader("Location"));
			}
		}
	}

	/** {@code imagen.tipo} es NOT NULL en MySQL: el tipo nulo solo se puede probar contra la respuesta, sin fila. */
	@Test
	void unTipoNulo_404_sinBytes() throws Exception {
		String ruta = "org-" + e.org + "/alumnos/" + UUID.randomUUID() + ".svg";
		Files.createDirectories(IMAGENES.resolve(ruta).getParent());
		Files.write(IMAGENES.resolve(ruta), SVG);

		ResponseEntity<Resource> conTipo = fotos.paraProfesor(new Imagen("foto.svg", "image/png", null, ruta), null);
		assertEquals(200, conTipo.getStatusCode().value(), "el fichero se encuentra: el 404 viene del tipo");

		ResponseEntity<Resource> sinTipo = fotos.paraProfesor(new Imagen("foto.svg", null, null, ruta), null);
		assertEquals(404, sinTipo.getStatusCode().value());
		assertNull(sinTipo.getBody());
		assertEquals(404, fotos.paraProfesor(new Imagen("foto.svg", null, null, ruta), 300).getStatusCode().value());
	}

	@Test
	void sesionSinRolDeProfesor_403() throws Exception {
		for (String token : List.of(tGestor, tPropietario, tFamilia, tResponsable)) {
			assertEquals(403, respuesta(get(fotoProfesor(propioFamilia)), token).getStatus());
			assertEquals(403, respuesta(get(fotoProfesor(conFoto)), token).getStatus());
		}
		assertEquals(401, mvc.perform(get(fotoProfesor(conFoto))).andReturn().getResponse().getStatus());
	}

	@Test
	void cuentasMixtas_soloPorSusGrupos() throws Exception {
		assertEquals(200, respuesta(get(fotoProfesor(deOtroGrupo)), tProfRP).getStatus(), "su grupo gB");
		assertEquals(403, respuesta(get(fotoProfesor(conFoto)), tProfRP).getStatus(), "su sede A no abre esta ruta");
		assertEquals(200, respuesta(get(fotoProfesor(deOtroGrupo)), tProfFP).getStatus(), "su grupo gB");
		assertEquals(403, respuesta(get(fotoProfesor(conFoto)), tProfFP).getStatus());
		assertEquals(200, respuesta(get(fotoProfesor(conFoto)), tGestorProf).getStatus(), "su grupo gA");
		assertEquals(403, respuesta(get(fotoProfesor(deOtroGrupo)), tGestorProf).getStatus(), "el total no abre gB");
	}

	// ------------------------------------------------------------------ el permiso cae: de 200 a lo que toque

	@Test
	void asignacionRetirada_403() throws Exception {
		long profesor = soloProfesor(e, "profretirado");
		asignar(e, profesor, e.gA);
		String token = e.token(jwtService, usuarios, profesor);
		assertEquals(200, respuesta(get(fotoProfesor(conFoto)), token).getStatus());

		assertEquals(204, respuesta(delete("/api/grupos/" + e.gA + "/profesores/" + profesor), tPropietario).getStatus());

		assertEquals(403, respuesta(get(fotoProfesor(conFoto)), token).getStatus());
	}

	@Test
	void alumnoQuitadoDelGrupo_403() throws Exception {
		long alumno = enGrupo(e, e.gA, "FOTOQUITADO");
		foto(e, alumno, "image/png", "png", png);
		assertEquals(200, respuesta(get(fotoProfesor(alumno)), tProfA).getStatus());

		assertEquals(204, respuesta(delete("/api/grupos/" + e.gA + "/alumnos/" + alumno), tPropietario).getStatus());

		assertEquals(403, respuesta(get(fotoProfesor(alumno)), tProfA).getStatus());
	}

	@Test
	void grupoBorrado_403() throws Exception {
		GrupoConAlumnosDTO nuevo = new GrupoConAlumnosDTO();
		nuevo.setNombre("Grupo a borrar " + e.marca);
		nuevo.setDeporte("TAEKWONDO");
		nuevo.setSedeId(e.sA);
		long grupo = TenantContext.calcularCon(e.org, () -> grupoService.crearGrupo(nuevo).getId());
		long profesor = soloProfesor(e, "profgrupoborrado");
		asignar(e, profesor, grupo);
		String token = e.token(jwtService, usuarios, profesor);
		long alumno = enGrupo(e, grupo, "FOTOGRUPOBORRADO");
		foto(e, alumno, "image/png", "png", png);
		assertEquals(200, respuesta(get(fotoProfesor(alumno)), token).getStatus());

		assertEquals(204, respuesta(delete("/api/grupos/" + grupo), tPropietario).getStatus());

		assertEquals(403, respuesta(get(fotoProfesor(alumno)), token).getStatus());
	}

	/** Solo tenía la membresía de PROFESOR: sin ninguna activa, la sesión ya no vale en esta escuela. */
	@Test
	void membresiaRetirada_401() throws Exception {
		long profesor = soloProfesor(e, "profsinmembresia");
		asignar(e, profesor, e.gA);
		String token = e.token(jwtService, usuarios, profesor);
		assertEquals(200, respuesta(get(fotoProfesor(conFoto)), token).getStatus());

		assertEquals(200, respuesta(delete("/api/miembros/" + profesor + "/roles/PROFESOR"), tPropietario).getStatus());

		assertEquals(401, respuesta(get(fotoProfesor(conFoto)), token).getStatus());
	}

	/** Le queda FAMILIA: la sesión sigue viva y la asignación sigue en la tabla, pero sin ROLE_PROFESOR no entra. */
	@Test
	void cuentaQueSeQuedaSoloConFamilia_403() throws Exception {
		long cuenta = e.cuentaFamilia(usuarios, "famexprof");
		membresia(e, cuenta, "PROFESOR");
		asignar(e, cuenta, e.gB);
		String token = e.token(jwtService, usuarios, cuenta);
		assertEquals(200, respuesta(get(fotoProfesor(deOtroGrupo)), token).getStatus());

		assertEquals(200, respuesta(delete("/api/miembros/" + cuenta + "/roles/PROFESOR"), tPropietario).getStatus());

		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM asignacion_profesor WHERE usuario_id = ?", Long.class, cuenta));
		assertEquals(403, respuesta(get(fotoProfesor(deOtroGrupo)), token).getStatus());
	}

	/** El flujo real le saca de sus grupos y borra la foto con su fichero: queda fuera de los grupos del profesor. */
	@Test
	void alumnoAnonimizado_403_ySinFichero() throws Exception {
		long alumno = enGrupo(e, e.gA, "FOTOANONIMIZADO");
		foto(e, alumno, "image/png", "png", png);
		Path fichero = IMAGENES.resolve(jdbc.queryForObject("""
				SELECT i.ruta FROM imagen i JOIN alumno a ON a.foto_alumno_id = i.id WHERE a.id = ?""", String.class, alumno));
		assertEquals(200, respuesta(get(fotoProfesor(alumno)), tProfA).getStatus());

		assertEquals(200, respuesta(post("/api/alumnos/" + alumno + "/anonimizar"), tPropietario).getStatus());

		assertFalse(Files.exists(fichero));
		assertEquals(403, respuesta(get(fotoProfesor(alumno)), tProfA).getStatus());
	}

	/**
	 * Defensa extra: ningún flujo deja a un alumno anonimizado activo, en el grupo y con foto, así que esta fila solo sale
	 * con SQL. Sigue en la lista, porque la condición afecta solo a {@code fotoId}.
	 */
	@Test
	void anonimizadoQueConservaraSuFoto_404_ySigueEnLaListaSinFotoId() throws Exception {
		long alumno = enGrupo(e, e.gA, "FOTORESTO");
		long imagen = foto(e, alumno, "image/png", "png", png);
		assertEquals(200, respuesta(get(fotoProfesor(alumno)), tProfA).getStatus());
		assertEquals(imagen, fila(leer(get("/api/profesor/grupos/" + e.gA + "/alumnos"), tProfA), alumno).get("fotoId").asLong());

		jdbc.update("UPDATE alumno SET anonimizado_en = NOW(6) WHERE id = ?", alumno);

		MockHttpServletResponse r = respuesta(get(fotoProfesor(alumno)), tProfA);
		assertEquals(404, r.getStatus());
		assertEquals(0, r.getContentAsByteArray().length);
		assertTrue(fila(leer(get("/api/profesor/grupos/" + e.gA + "/alumnos"), tProfA), alumno).get("fotoId").isNull());
	}

	// ------------------------------------------------------------------ auditoría

	@Test
	void unaFotoServidaNoDejaFila_unIntentoDenegadoSi() throws Exception {
		String servida = fotoProfesor(conFoto);
		long sinServir = filasDeAuditoria(servida);
		assertEquals(200, respuesta(get(servida), tProfA).getStatus());
		assertEquals(200, respuesta(get(servida).param("w", "300"), tProfA).getStatus());
		String etag = respuesta(get(servida), tProfA).getHeader("ETag");
		assertEquals(304, respuesta(get(servida).header("If-None-Match", etag), tProfA).getStatus());
		assertEquals(sinServir, filasDeAuditoria(servida), "22 fotos por pantalla no llenan auditoria_evento");

		String denegada = fotoProfesor(deOtroGrupo);
		long antes = filasDeAuditoria(denegada);
		assertEquals(403, respuesta(get(denegada), tProfA).getStatus());
		assertEquals(antes + 1, filasDeAuditoria(denegada), "un intento fuera de sus grupos queda apuntado");
	}

	// ------------------------------------------------------------------ ruta de familias y gestión: sigue igual

	@Test
	void rutaAntigua_sesionSoloProfesorSigueCerrada_403() throws Exception {
		assertEquals(403, respuesta(get(fotoAntigua(conFoto)), tProfA).getStatus(), "aunque sea alumno de su grupo");
		assertEquals(403, respuesta(head(fotoAntigua(conFoto)), tProfA).getStatus());
	}

	@Test
	void rutaAntigua_gestion_mismasCabeceras() throws Exception {
		for (String token : List.of(tGestor, tPropietario)) {
			MockHttpServletResponse r = respuesta(get(fotoAntigua(conFoto)), token);
			assertEquals(200, r.getStatus());
			assertEquals("image/png", r.getContentType());
			assertEquals(CACHE_FAMILIAS, r.getHeader("Cache-Control"));
			assertEquals(List.of("Origin", "Access-Control-Request-Method", "Access-Control-Request-Headers",
					"Accept-Encoding"), r.getHeaders("Vary"));
			assertEquals("inline; filename=\"" + nombreDeFoto(conFoto) + "\"", r.getHeader("Content-Disposition"));
			assertEquals(etag(conFoto, 0, "image/png"), r.getHeader("ETag"));
			assertEquals("nosniff", r.getHeader("X-Content-Type-Options"));
			assertArrayEquals(png, r.getContentAsByteArray());
		}
	}

	@Test
	void rutaAntigua_familiaYResponsable_soloLosSuyos() throws Exception {
		assertEquals(200, respuesta(get(fotoAntigua(propioFamilia)), tFamilia).getStatus());
		assertEquals(403, respuesta(get(fotoAntigua(conFoto)), tFamilia).getStatus());
		assertEquals(200, respuesta(get(fotoAntigua(conFoto)), tResponsable).getStatus(), "gA es de su sede");
		assertEquals(403, respuesta(get(fotoAntigua(deOtroGrupo)), tResponsable).getStatus());
		assertEquals(404, respuesta(get(fotoAntigua(deOtraEscuela)), tGestor).getStatus(), "su total no sale de su escuela");
	}

	@Test
	void rutaAntigua_conAncho_redimensionaConSusLimites() throws Exception {
		MockHttpServletResponse r = respuesta(get(fotoAntigua(conFoto)).param("w", "50"), tGestor);
		assertEquals(200, r.getStatus());
		assertEquals(CACHE_FAMILIAS, r.getHeader("Cache-Control"));
		if (hayCodificadorWebp()) {
			assertEquals("image/webp", r.getContentType());
			assertEquals(etag(conFoto, 120, "image/webp"), r.getHeader("ETag"), "mínimo de 120 px");
		}
		MockHttpServletResponse ancha = respuesta(get(fotoAntigua(conFoto)).param("w", "5000"), tGestor);
		assertEquals("image/png", ancha.getContentType(), "tope de 1800 px, sin ampliar");
		assertEquals(etag(conFoto, 1800, "image/png"), ancha.getHeader("ETag"));
	}

	@Test
	void rutaAntigua_ficheroBorrado_redirigeASuUrl() throws Exception {
		MockHttpServletResponse r = respuesta(get(fotoAntigua(ficheroPerdido)), tGestor);
		assertEquals(302, r.getStatus());
		assertEquals(baseUrl + urlDeFoto(ficheroPerdido), r.getHeader("Location"));
	}

	@Test
	void rutaAntigua_sinFoto_404() throws Exception {
		assertEquals(404, respuesta(get(fotoAntigua(e.aSinSede)), tGestor).getStatus());
	}

	@Test
	void rutaAntigua_elSvgSigueSirviendoseComoHoy() throws Exception {
		MockHttpServletResponse r = respuesta(get(fotoAntigua(conSvg)), tGestor);
		assertEquals(200, r.getStatus());
		assertEquals("image/svg+xml", r.getContentType());
		assertTrue(r.getHeader("Content-Disposition").startsWith("inline;"));
	}

	@Test
	void rutaAntigua_cadaLecturaSigueDejandoFila() throws Exception {
		String ruta = fotoAntigua(enSuOtroGrupo);
		long antes = filasDeAuditoria(ruta);
		assertEquals(200, respuesta(get(ruta), tGestor).getStatus());
		assertEquals(antes + 1, filasDeAuditoria(ruta));
	}

	// ------------------------------------------------------------------ utilidades

	private static String fotoProfesor(long alumno) {
		return "/api/profesor/alumnos/" + alumno + "/imagen";
	}

	private static String fotoAntigua(long alumno) {
		return "/api/alumnos/" + alumno + "/imagen";
	}

	private long soloProfesor(EscuelaConSedesDePrueba x, String nombre) {
		long id = x.cuenta(usuarios, nombre);
		membresia(x, id, "PROFESOR");
		return id;
	}

	private void membresia(EscuelaConSedesDePrueba x, long usuario, String rol) {
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", usuario, x.org, rol);
	}

	private void asignar(EscuelaConSedesDePrueba x, long profesor, long grupo) {
		jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) VALUES (?, ?, ?, 'TITULAR')",
				x.org, profesor, grupo);
	}

	private long enGrupo(EscuelaConSedesDePrueba x, long grupo, String nombre) {
		long alumno = x.alumno(nombre + x.marca, null, true);
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", grupo, alumno);
		return alumno;
	}

	/** Fila de {@code imagen} en la carpeta de la escuela y, salvo con {@code contenido} nulo, su fichero. */
	private long foto(EscuelaConSedesDePrueba x, long alumno, String tipo, String extension, byte[] contenido)
			throws IOException {
		String nombre = UUID.randomUUID() + "." + extension;
		String ruta = "org-" + x.org + "/alumnos/" + nombre;
		if (contenido != null) {
			Path fichero = IMAGENES.resolve(ruta);
			Files.createDirectories(fichero.getParent());
			Files.write(fichero, contenido);
		}
		jdbc.update("INSERT INTO imagen (nombre, ruta, tipo, url, organizacion_id) VALUES (?, ?, ?, ?, ?)", nombre, ruta,
				tipo, "/imagenes/" + ruta, x.org);
		long imagen = jdbc.queryForObject("SELECT id FROM imagen WHERE ruta = ?", Long.class, ruta);
		jdbc.update("UPDATE alumno SET foto_alumno_id = ? WHERE id = ?", imagen, alumno);
		return imagen;
	}

	private String nombreDeFoto(long alumno) {
		return jdbc.queryForObject("""
				SELECT i.nombre FROM imagen i JOIN alumno a ON a.foto_alumno_id = i.id WHERE a.id = ?""", String.class,
				alumno);
	}

	private String urlDeFoto(long alumno) {
		return jdbc.queryForObject("""
				SELECT i.url FROM imagen i JOIN alumno a ON a.foto_alumno_id = i.id WHERE a.id = ?""", String.class,
				alumno);
	}

	/** El ETag de hoy: tamaño y fecha del fichero, ancho y tipo. */
	private String etag(long alumno, int ancho, String tipo) throws IOException {
		Path fichero = IMAGENES.resolve(jdbc.queryForObject("""
				SELECT i.ruta FROM imagen i JOIN alumno a ON a.foto_alumno_id = i.id WHERE a.id = ?""", String.class,
				alumno));
		return "\"" + Long.toHexString(Files.size(fichero)) + "-"
				+ Long.toHexString(Files.getLastModifiedTime(fichero).toMillis()) + "-" + ancho + "-" + tipo + "\"";
	}

	private long filasDeAuditoria(String endpoint) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_evento WHERE endpoint = ?", Long.class, endpoint);
	}

	/** Sentencias de Hibernate en la lista de gA2, la menor de tres por si salta un job. */
	private long sentenciasDeLaLista() throws Exception {
		Statistics estadisticas = emf.unwrap(SessionFactory.class).getStatistics();
		boolean antes = estadisticas.isStatisticsEnabled();
		estadisticas.setStatisticsEnabled(true);
		try {
			long menor = Long.MAX_VALUE;
			for (int i = 0; i < 3; i++) {
				estadisticas.clear();
				assertEquals(200, respuesta(get("/api/profesor/grupos/" + grupoSoloDeProfA + "/alumnos"), tProfA).getStatus());
				menor = Math.min(menor, estadisticas.getPrepareStatementCount());
			}
			return menor;
		} finally {
			estadisticas.setStatisticsEnabled(antes);
		}
	}

	private JsonNode leer(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		MockHttpServletResponse r = respuesta(peticion, token);
		assertEquals(200, r.getStatus(), r.getContentAsString());
		return json.readTree(r.getContentAsString());
	}

	private MockHttpServletResponse respuesta(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn().getResponse();
	}

	private static JsonNode fila(JsonNode lista, long id) {
		for (JsonNode n : lista) {
			if (n.get("id").asLong() == id) {
				return n;
			}
		}
		throw new AssertionError("Sin fila " + id + " en " + lista);
	}

	private static Set<String> campos(JsonNode nodo) {
		Set<String> campos = new HashSet<>();
		for (Iterator<String> it = nodo.fieldNames(); it.hasNext();) {
			campos.add(it.next());
		}
		return campos;
	}

	private static byte[] imagen(int ancho, int alto, String formato) throws IOException {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB), formato, salida);
		// Comprueba que el fichero de prueba se puede leer: el redimensionado lo decodifica.
		assertNotNull(ImageIO.read(new ByteArrayInputStream(salida.toByteArray())));
		return salida.toByteArray();
	}

	private static boolean hayCodificadorWebp() {
		return ImageIO.getImageWritersByMIMEType("image/webp").hasNext()
				|| ImageIO.getImageWritersBySuffix("webp").hasNext();
	}
}
