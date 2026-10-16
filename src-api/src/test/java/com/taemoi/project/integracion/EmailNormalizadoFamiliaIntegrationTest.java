package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import com.taemoi.project.services.MembresiaService;

/**
 * La coincidencia de una familia con sus alumnos usa el email NORMALIZADO de la cuenta (sin espacios ni
 * mayúsculas), igual que {@code /user/alumnos} y el control de acceso a alumnos. Contra MySQL real, cuya
 * colación de tablas (utf8mb4_0900_ai_ci, NO PAD) no iguala {@code 'x '} con {@code 'x'}.
 *
 * <p>Las entidades normalizan el email al guardarlo, así que el espacio final se escribe por JDBC (datos
 * escritos fuera de la aplicación).
 *
 * <p>No se prueba por HTTP: el login busca la cuenta por el email normalizado de la petición y, con esta
 * colación, no encuentra la que tiene el espacio final (401 antes de llegar a la regla de membresía). Esa
 * búsqueda queda fuera de este cambio. {@code @AutoConfigureMockMvc} solo mantiene la misma configuración
 * que las otras clases para reutilizar el contexto de Spring.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EmailNormalizadoFamiliaIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String CLAVE = "Clave-Segura-123";

	@Autowired JdbcTemplate jdbc;
	@Autowired PasswordEncoder encoder;
	@Autowired UsuarioRepository usuarios;
	@Autowired MembresiaRepository membresias;
	@Autowired AlumnoRepository alumnos;
	@Autowired ContadorService contadores;
	@Autowired MembresiaService membresiaService;

	@MockitoBean JavaMailSender mailSender;

	final String sufijo = UUID.randomUUID().toString().substring(0, 8);
	final String email = "norm-" + sufijo + "@example.test";
	long alumnoId;
	long usuarioId;
	long membresiaId;

	@BeforeAll
	void crearFamiliaConEspacioFinalEnElEmail() {
		alumnoId = TenantContext.calcularCon(ORG1, this::crearAlumno);
		usuarioId = TenantContext.calcularCon(ORG1, this::crearUsuario);
		membresiaId = TenantContext.calcularCon(ORG1,
				() -> membresias.save(new Membresia(usuarioId, ORG1, RolMembresia.FAMILIA)).getId());

		jdbc.update("UPDATE usuario SET email = CONCAT(email, ' ') WHERE id = ?", usuarioId);

		assertEquals(email + " ", jdbc.queryForObject("SELECT email FROM usuario WHERE id = ?", String.class,
				usuarioId));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE id = ? AND email = ?",
				Integer.class, usuarioId, email), "La colación no iguala el espacio final");
		assertEquals(Boolean.TRUE, jdbc.queryForObject(
				"SELECT activo FROM alumno WHERE id = ? AND email = ? AND organizacion_id = ?", Boolean.class,
				alumnoId, email, ORG1));
	}

	/** Sin borrar nada: se retira la cuenta y el alumno para no interferir con otras clases. */
	@AfterAll
	void retirar() {
		jdbc.update("UPDATE membresia SET activa = FALSE WHERE id = ?", membresiaId);
		jdbc.update("UPDATE usuario SET email = ? WHERE id = ?", "retirado-norm-" + sufijo + "@example.test",
				usuarioId);
		jdbc.update("UPDATE alumno SET activo = FALSE, email = ? WHERE id = ?",
				"retirado-alumno-norm-" + sufijo + "@example.test", alumnoId);
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	@Test
	void familiaConEspacioFinalEnElEmailEntraEnLaEscuelaDeSuAlumno() {
		Usuario cuenta = TenantContext.calcularCon(ORG1, () -> usuarios.findById(usuarioId).orElseThrow());
		assertEquals(email + " ", cuenta.getEmail());

		assertTrue(TenantContext.calcularCon(ORG1, () -> membresiaService.puedeEntrarEn(cuenta, ORG1)));
		assertEquals(Optional.of(ORG1),
				TenantContext.calcularCon(ORG1, () -> membresiaService.elegirOrganizacionParaLogin(cuenta)));
	}

	private long crearUsuario() {
		Usuario usuario = new Usuario();
		usuario.setNombre("Usuario");
		usuario.setApellidos("Email normalizado");
		usuario.setEmail(email);
		usuario.setContrasena(encoder.encode(CLAVE));
		usuario.setAuthProvider(AuthProvider.LOCAL);
		return usuarios.save(usuario).getId();
	}

	private long crearAlumno() {
		Alumno alumno = new Alumno();
		alumno.setNombre("Norm " + sufijo);
		alumno.setApellidos("PRUEBA EMAIL NORMALIZADO");
		alumno.setNumeroExpediente(contadores.siguienteNumeroExpediente());
		alumno.setFechaNacimiento(Date.valueOf(LocalDate.of(2012, 7, 7)));
		alumno.setDireccion("Calle Prueba 3");
		alumno.setTelefono("600000002");
		alumno.setActivo(true);
		alumno.setAutorizacionWeb(true);
		alumno.setEmail(email);
		return alumnos.save(alumno).getId();
	}
}
