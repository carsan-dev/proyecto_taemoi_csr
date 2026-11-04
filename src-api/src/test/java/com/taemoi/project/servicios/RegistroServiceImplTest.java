package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.OrigenUsuarioAlumno;
import com.taemoi.project.entities.RegistroPendiente;
import com.taemoi.project.entities.RelacionUsuarioAlumno;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.entities.UsuarioAlumno;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.RegistroPendienteRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.EmailService;
import com.taemoi.project.services.MembresiaService;
import com.taemoi.project.services.VinculoFamiliaService;
import com.taemoi.project.services.impl.RegistroServiceImpl;

/**
 * Confirmación del registro (fase 3, entrega 2b; pieza E10-2: ya sin {@code usuario.alumno_id}): escribe la fila de
 * {@code usuario_alumno} del alumno registrado, salvo que su email ya no sea el de la cuenta.
 */
class RegistroServiceImplTest {

	private static final String EMAIL = "familia@example.test";
	private static final String TOKEN = "token-de-prueba";

	private final RegistroPendienteRepository pendientes = mock(RegistroPendienteRepository.class);
	private final AlumnoRepository alumnos = mock(AlumnoRepository.class);
	private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
	private final MembresiaService membresias = mock(MembresiaService.class);
	private final VinculoFamiliaService vinculos = mock(VinculoFamiliaService.class);
	private final RegistroServiceImpl servicio = new RegistroServiceImpl(pendientes, alumnos, usuarios,
			NoOpPasswordEncoder.getInstance(), mock(EmailService.class), membresias, vinculos,
			com.taemoi.project.services.MarcaEscuelaPruebas.soloMoiskimdo());

	private Alumno alumno;

	@BeforeEach
	void setup() throws Exception {
		alumno = new Alumno();
		alumno.setId(5L);
		alumno.setEmail(EMAIL);
		alumno.setActivo(true);
		ReflectionTestUtils.setField(alumno, "organizacionId", 2L);

		RegistroPendiente pendiente = new RegistroPendiente();
		pendiente.setEmail(EMAIL);
		pendiente.setAlumnoId(5L);
		pendiente.setPasswordHash("hash");
		pendiente.setTokenHash(sha256(TOKEN));
		pendiente.setTokenExpiresAt(LocalDateTime.now().plusHours(1));
		when(pendientes.findByTokenHash(sha256(TOKEN))).thenReturn(Optional.of(pendiente));
		when(usuarios.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());
		when(alumnos.findById(5L)).thenReturn(Optional.of(alumno));
		when(usuarios.save(any(Usuario.class))).thenAnswer(call -> {
			Usuario u = call.getArgument(0);
			u.setId(9L);
			return u;
		});
	}

	@Test
	void confirmarEscribeElVinculoDelAlumnoTrasLaMembresia() {
		when(alumnos.existsByIdAndEmailIgnoreCase(5L, EMAIL)).thenReturn(true);

		servicio.confirmarRegistro(TOKEN);

		ArgumentCaptor<Usuario> cuenta = ArgumentCaptor.forClass(Usuario.class);
		InOrder orden = inOrder(usuarios, membresias, vinculos);
		orden.verify(usuarios).save(cuenta.capture());
		orden.verify(membresias).asegurarMembresia(9L, 2L, RolMembresia.FAMILIA);
		orden.verify(vinculos).vincularAlta(cuenta.getValue(), alumno);
		assertEquals(EMAIL, cuenta.getValue().getEmail());
		// Pieza E10-2: usuario.alumno_id ya no se escribe (ni la ficha se guarda para ello).
		verify(alumnos, never()).save(any(Alumno.class));
	}

	/**
	 * Decisiones 428 y 438: la ficha ya tiene cuenta si tiene una fila de otra cuenta que le da acceso
	 * (VinculoFamiliaService#cuentasQueAccedenALaFicha), de cualquier origen y relación.
	 */
	@ParameterizedTest
	@CsvSource({ "TUTOR,MIGRACION", "PROPIO,MIGRACION", "PROPIO,REGISTRO", "TUTOR,MANUAL" })
	void fichaConFilaDeOtraCuentaRechazaElRegistroSinCrearNada(RelacionUsuarioAlumno relacion,
			OrigenUsuarioAlumno origen) {
		when(vinculos.cuentasQueAccedenALaFicha(alumno))
				.thenReturn(List.of(new UsuarioAlumno(3L, 5L, 2L, relacion, origen)));

		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> servicio.confirmarRegistro(TOKEN));

		assertEquals("El alumno ya tiene usuario asociado.", e.getMessage());
		verify(pendientes).delete(any(RegistroPendiente.class));
		verify(usuarios, never()).save(any(Usuario.class));
		verify(membresias, never()).asegurarMembresia(any(), any(), any());
		verify(vinculos, never()).vincularAlta(any(), any());
	}

	@Test
	void fichaSinFilasSeRegistra() {
		when(vinculos.cuentasQueAccedenALaFicha(alumno)).thenReturn(List.of());
		when(alumnos.existsByIdAndEmailIgnoreCase(5L, EMAIL)).thenReturn(true);

		servicio.confirmarRegistro(TOKEN);

		verify(usuarios).save(any(Usuario.class));
		verify(vinculos).vincularAlta(any(Usuario.class), eq(alumno));
	}

	@Test
	void alumnoCuyoEmailCambioTrasLaSolicitudNoSeVinculaEnUsuarioAlumno() {
		// Como V31 (R1 solo familia con email distinto: sin fila): la cuenta se crea igual que antes, pero el
		// vínculo explícito no daría a Taemoi un acceso que hoy no tiene.
		when(alumnos.existsByIdAndEmailIgnoreCase(5L, EMAIL)).thenReturn(false);

		servicio.confirmarRegistro(TOKEN);

		verify(usuarios).save(any(Usuario.class));
		verify(membresias).asegurarMembresia(eq(9L), eq(2L), eq(RolMembresia.FAMILIA));
		verify(vinculos, never()).vincularAlta(any(), any());
	}

	private static String sha256(String valor) throws Exception {
		return HexFormat.of().formatHex(
				MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));
	}
}
