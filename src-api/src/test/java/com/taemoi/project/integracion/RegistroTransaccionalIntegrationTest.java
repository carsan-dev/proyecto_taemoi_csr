package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.RegistroPendiente;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.RegistroPendienteRepository;
import com.taemoi.project.services.ContadorService;
import com.taemoi.project.services.RegistroService;
import com.taemoi.project.services.VinculoFamiliaService;

/**
 * {@code RegistroServiceImpl.confirmarRegistro} es transaccional (revisión de la entrega 2b): si el vínculo
 * {@code usuario_alumno} falla, no quedan la cuenta ni la membresía; los rechazos que borran la solicitud
 * ({@code IllegalArgumentException}) la siguen borrando. {@link VinculoFamiliaService} es un mock para poder
 * hacerlo fallar.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RegistroTransaccionalIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;

	@Autowired JdbcTemplate jdbc;
	@Autowired PasswordEncoder encoder;
	@Autowired AlumnoRepository alumnos;
	@Autowired RegistroPendienteRepository registrosPendientes;
	@Autowired ContadorService contadores;
	@Autowired RegistroService registroService;

	@MockitoBean VinculoFamiliaService vinculos;
	@MockitoBean JavaMailSender mailSender;

	final String sufijo = UUID.randomUUID().toString().substring(0, 8);
	final List<Long> alumnosCreados = new ArrayList<>();

	@AfterAll
	void retirar() {
		for (Long id : alumnosCreados) {
			jdbc.update("UPDATE alumno SET activo = FALSE, email = ? WHERE id = ?",
					"retirado-rt-" + id + "-" + sufijo + "@example.test", id);
		}
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	@Test
	void siElVinculoFallaNoQuedanCuentaNiMembresia() {
		String email = "rt-atomico-" + sufijo + "@example.test";
		long alumnoId = alumno(email);
		doThrow(new IllegalStateException("fallo simulado")).when(vinculos).vincularAlta(any(), any());
		String token = pendiente(email, alumnoId, LocalDateTime.now().plusHours(1));

		assertThrows(IllegalStateException.class,
				() -> TenantContext.ejecutarCon(ORG1, () -> registroService.confirmarRegistro(token)));

		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE email = ?", Integer.class, email));
		assertEquals(0, jdbc.queryForObject(
				"SELECT COUNT(*) FROM membresia m JOIN usuario u ON u.id = m.usuario_id WHERE u.email = ?",
				Integer.class, email));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE alumno_id = ?", Integer.class,
				alumnoId));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM registro_pendiente WHERE email = ?", Integer.class,
				email), "la solicitud no se consume");
	}

	@Test
	void tokenCaducadoSigueBorrandoLaSolicitud() {
		String email = "rt-caducado-" + sufijo + "@example.test";
		long alumnoId = alumno(email);
		String token = pendiente(email, alumnoId, LocalDateTime.now().minusMinutes(1));

		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> TenantContext.ejecutarCon(ORG1, () -> registroService.confirmarRegistro(token)));

		assertEquals("Token invalido o caducado.", e.getMessage());
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM registro_pendiente WHERE email = ?", Integer.class,
				email));
	}

	private String pendiente(String email, long alumnoId, LocalDateTime caduca) {
		String token = "token-rt-" + UUID.randomUUID();
		TenantContext.ejecutarCon(ORG1, () -> {
			RegistroPendiente pendiente = new RegistroPendiente();
			pendiente.setEmail(email);
			pendiente.setAlumnoId(alumnoId);
			pendiente.setPasswordHash(encoder.encode("Clave-Segura-123"));
			pendiente.setTokenHash(sha256(token));
			pendiente.setTokenExpiresAt(caduca);
			registrosPendientes.save(pendiente);
		});
		return token;
	}

	private long alumno(String email) {
		long id = TenantContext.calcularCon(ORG1, () -> {
			Alumno alumno = new Alumno();
			alumno.setNombre("RT " + sufijo);
			alumno.setApellidos("PRUEBA REGISTRO TRANSACCIONAL");
			alumno.setNumeroExpediente(contadores.siguienteNumeroExpediente());
			alumno.setFechaNacimiento(Date.valueOf(LocalDate.now().minusYears(10)));
			alumno.setDireccion("Calle Prueba 3");
			alumno.setTelefono("600000003");
			alumno.setActivo(true);
			alumno.setAutorizacionWeb(true);
			alumno.setEmail(email);
			return alumnos.save(alumno).getId();
		});
		alumnosCreados.add(id);
		return id;
	}

	private static String sha256(String valor) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
