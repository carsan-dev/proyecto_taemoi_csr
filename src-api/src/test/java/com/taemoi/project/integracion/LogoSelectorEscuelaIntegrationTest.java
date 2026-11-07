package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Membresia;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.MembresiaRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.UsuarioRepository;

import jakarta.servlet.http.Cookie;

/**
 * Selector de escuela: {@code GET /api/auth/organizaciones/{id}/logo} sirve el logo subido de una escuela de la que
 * el usuario es miembro (aunque no sea la activa), con ETag y 304; para quien no es miembro, escuela sin logo
 * subido, escuela inexistente o logo que apunta a la imagen de otra escuela, 404 (nunca 403); anónimo, 401.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LogoSelectorEscuelaIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String CLAVE = "Clave-Segura-123";
	private static final byte[] PNG = { (byte) 0x89, 0x50, 0x4E, 0x47, 13, 10, 26, 10, 1, 2, 3, 4 };

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired PasswordEncoder encoder;
	@Autowired UsuarioRepository usuarios;
	@Autowired MembresiaRepository membresias;
	@Autowired OrganizacionRepository organizaciones;
	@Value("${app.imagenes.directorio.windows}") String raizImagenes;

	@MockitoBean JavaMailSender mailSender;

	final String sufijo = UUID.randomUUID().toString().substring(0, 8);
	long conLogo;
	long sinLogo;
	long ajena;
	long apuntaAOtra;
	long imagenLogo;
	Path fichero;
	Usuario miembro;
	Usuario extrano;

	@BeforeAll
	void preparar() throws Exception {
		conLogo = crearEscuela("it-slogo-a-");
		sinLogo = crearEscuela("it-slogo-b-");
		ajena = crearEscuela("it-slogo-c-");
		apuntaAOtra = crearEscuela("it-slogo-d-");
		String nombre = "it-slogo-" + sufijo + ".png";
		fichero = Path.of(raizImagenes).resolve(nombre);
		Files.createDirectories(fichero.getParent());
		Files.write(fichero, PNG);
		imagenLogo = imagen(nombre, conLogo);
		jdbc.update("UPDATE organizacion SET logo_imagen_id = ? WHERE id = ?", imagenLogo, conLogo);
		// Logo de la escuela "ajena": existe, pero el usuario no es miembro.
		long imagenAjena = imagen(nombre, ajena);
		jdbc.update("UPDATE organizacion SET logo_imagen_id = ? WHERE id = ?", imagenAjena, ajena);
		// Una escuela cuyo logo apunta a la imagen de otra escuela: nunca se sirve.
		jdbc.update("UPDATE organizacion SET logo_imagen_id = ? WHERE id = ?", imagenLogo, apuntaAOtra);

		miembro = usuario("miembro");
		extrano = usuario("extrano");
		dar(miembro, conLogo);
		dar(miembro, sinLogo);
		dar(miembro, apuntaAOtra);
		dar(extrano, sinLogo);
	}

	@AfterAll
	void limpiar() throws Exception {
		Files.deleteIfExists(fichero);
	}

	@Test
	void miembroRecibePngConEtagY304() throws Exception {
		String token = login(miembro);
		MvcResult r = mvc.perform(get("/api/auth/organizaciones/" + conLogo + "/logo").cookie(jwt(token)))
				.andExpect(status().isOk()).andReturn();
		assertEquals(MediaType.IMAGE_PNG_VALUE, r.getResponse().getContentType());
		assertArrayEquals(PNG, r.getResponse().getContentAsByteArray());
		String etag = r.getResponse().getHeader("ETag");
		assertEquals("\"logo-" + imagenLogo + "\"", etag);
		String cache = r.getResponse().getHeader("Cache-Control");
		assertNotNull(cache);
		assertTrue(cache.contains("private") && cache.contains("max-age=300"), cache);

		MvcResult igual = mvc.perform(get("/api/auth/organizaciones/" + conLogo + "/logo")
				.header("If-None-Match", etag).cookie(jwt(token))).andExpect(status().isNotModified()).andReturn();
		assertEquals(0, igual.getResponse().getContentAsByteArray().length);
		assertEquals(etag, igual.getResponse().getHeader("ETag"));
		assertTrue(igual.getResponse().getHeader("Cache-Control").contains("max-age=300"));
		// Otra etiqueta: se sirve de nuevo.
		mvc.perform(get("/api/auth/organizaciones/" + conLogo + "/logo").header("If-None-Match", "\"logo-0\"")
				.cookie(jwt(token))).andExpect(status().isOk());
	}

	@Test
	void noMiembroEscuelaSinLogoOInexistenteDan404() throws Exception {
		String token = login(miembro);
		// Sin membresía en "ajena" (aunque tenga logo): 404, no 403, tampoco con If-None-Match.
		mvc.perform(get("/api/auth/organizaciones/" + ajena + "/logo").cookie(jwt(token)))
				.andExpect(status().isNotFound());
		mvc.perform(get("/api/auth/organizaciones/" + ajena + "/logo").header("If-None-Match", "*")
				.cookie(jwt(token))).andExpect(status().isNotFound());
		mvc.perform(get("/api/auth/organizaciones/999999999/logo").cookie(jwt(token)))
				.andExpect(status().isNotFound());
		// El otro usuario no es miembro de la escuela con logo.
		mvc.perform(get("/api/auth/organizaciones/" + conLogo + "/logo").cookie(jwt(login(extrano))))
				.andExpect(status().isNotFound());
		// Miembro, pero sin logo subido.
		mvc.perform(get("/api/auth/organizaciones/" + sinLogo + "/logo").cookie(jwt(token)))
				.andExpect(status().isNotFound());
	}

	@Test
	void laImagenDeOtraEscuelaNuncaSeSirve() throws Exception {
		mvc.perform(get("/api/auth/organizaciones/" + apuntaAOtra + "/logo").cookie(jwt(login(miembro))))
				.andExpect(status().isNotFound());
	}

	@Test
	void sinFicheroEnDisco404YAnonimo401() throws Exception {
		mvc.perform(get("/api/auth/organizaciones/" + conLogo + "/logo")).andExpect(status().isUnauthorized());
		String token = login(miembro);
		Path apartado = fichero.resolveSibling(fichero.getFileName() + ".apartado");
		Files.move(fichero, apartado);
		try {
			mvc.perform(get("/api/auth/organizaciones/" + conLogo + "/logo").cookie(jwt(token)))
					.andExpect(status().isNotFound());
		} finally {
			Files.move(apartado, fichero);
		}
	}

	private long imagen(String ruta, long organizacionId) {
		jdbc.update("INSERT INTO imagen (nombre, ruta, tipo, organizacion_id) VALUES ('logo', ?, 'image/png', ?)",
				ruta, organizacionId);
		return jdbc.queryForObject("SELECT MAX(id) FROM imagen WHERE organizacion_id = ?", Long.class,
				organizacionId);
	}

	private long crearEscuela(String prefijo) {
		return TenantContext.calcularCon(ORG1, () -> {
			Organizacion org = new Organizacion();
			org.setSlug(prefijo + sufijo);
			org.setNombre("Escuela " + prefijo + sufijo);
			org.setComportamientoLegadoTaemoi(false);
			return organizaciones.save(org).getId();
		});
	}

	private Usuario usuario(String nombre) {
		return TenantContext.calcularCon(ORG1, () -> {
			Usuario u = new Usuario();
			u.setNombre("Usuario");
			u.setApellidos("Logo");
			u.setEmail("slogo-" + nombre + "-" + sufijo + "@example.test");
			u.setContrasena(encoder.encode(CLAVE));
			u.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(u);
		});
	}

	private void dar(Usuario u, long organizacionId) {
		TenantContext.calcularCon(ORG1,
				() -> membresias.save(new Membresia(u.getId(), organizacionId, RolMembresia.PROPIETARIO)));
	}

	private String login(Usuario u) throws Exception {
		MvcResult r = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + u.getEmail() + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk()).andReturn();
		return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
	}

	private static Cookie jwt(String token) {
		return new Cookie("jwt", token);
	}
}
