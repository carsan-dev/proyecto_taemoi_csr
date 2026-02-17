package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.EmailService;
import com.taemoi.project.services.impl.RecordatorioRachaServiceImpl;

@ExtendWith(MockitoExtension.class)
class RecordatorioRachaServiceImplTest {

	@Mock
	private UsuarioRepository usuarioRepository;

	@Mock
	private AlumnoRepository alumnoRepository;

	@Mock
	private EmailService emailService;

	@InjectMocks
	private RecordatorioRachaServiceImpl recordatorioRachaService;

	private ZoneId zoneId;

	@BeforeEach
	void setUp() {
		zoneId = ZoneId.systemDefault();
		ReflectionTestUtils.setField(recordatorioRachaService, "streakReminderEnabled", true);
		ReflectionTestUtils.setField(recordatorioRachaService, "streakReminderWindowHours", 3);
		ReflectionTestUtils.setField(recordatorioRachaService, "frontendBaseUrl", "https://moiskimdo.es");
	}

	@Test
	void enviarRecordatoriosSiCorresponde_enviaEmailCuandoHayRachaEnRiesgo() {
		fijarHora("2026-02-17T22:30:00");
		Usuario usuario = crearUsuario("familia@example.com", true, null);
		when(usuarioRepository.findByRolAndRecordatorioRachaEmailHabilitadoTrue(Roles.ROLE_USER))
				.thenReturn(List.of(usuario));
		when(alumnoRepository.findAllByEmailIgnoreCaseAndActivoTrue("familia@example.com"))
				.thenReturn(List.of(crearAlumno("Ana", "Activa", "familia@example.com", 5, LocalDate.of(2026, 2, 16))));

		recordatorioRachaService.enviarRecordatoriosSiCorresponde();

		verify(emailService).sendEmail(eq("familia@example.com"), any(String.class), any(String.class));
		verify(usuarioRepository).save(usuario);
	}

	@Test
	void enviarRecordatoriosSiCorresponde_noEnviaFueraDeVentana() {
		fijarHora("2026-02-17T18:00:00");

		recordatorioRachaService.enviarRecordatoriosSiCorresponde();

		verify(usuarioRepository, never()).findByRolAndRecordatorioRachaEmailHabilitadoTrue(any());
		verify(emailService, never()).sendEmail(any(), any(), any());
	}

	@Test
	void enviarRecordatoriosSiCorresponde_noEnviaSiToggleDesactivado() {
		fijarHora("2026-02-17T22:30:00");
		Usuario usuario = crearUsuario("familia@example.com", false, null);
		when(usuarioRepository.findByRolAndRecordatorioRachaEmailHabilitadoTrue(Roles.ROLE_USER))
				.thenReturn(List.of(usuario));

		recordatorioRachaService.enviarRecordatoriosSiCorresponde();

		verify(emailService, never()).sendEmail(any(), any(), any());
		verify(usuarioRepository, never()).save(any(Usuario.class));
	}

	@Test
	void enviarRecordatoriosSiCorresponde_noEnviaSiNoHayRachaMantenible() {
		fijarHora("2026-02-17T22:30:00");
		Usuario usuario = crearUsuario("familia@example.com", true, null);
		when(usuarioRepository.findByRolAndRecordatorioRachaEmailHabilitadoTrue(Roles.ROLE_USER))
				.thenReturn(List.of(usuario));
		when(alumnoRepository.findAllByEmailIgnoreCaseAndActivoTrue("familia@example.com"))
				.thenReturn(List.of(
						crearAlumno("Ana", "Hoy", "familia@example.com", 6, LocalDate.of(2026, 2, 17)),
						crearAlumno("Beto", "Perdida", "familia@example.com", 6, LocalDate.of(2026, 2, 15))));

		recordatorioRachaService.enviarRecordatoriosSiCorresponde();

		verify(emailService, never()).sendEmail(any(), any(), any());
		verify(usuarioRepository, never()).save(any(Usuario.class));
	}

	@Test
	void enviarRecordatoriosSiCorresponde_deduplicaPorResetObjetivo() {
		fijarHora("2026-02-17T22:30:00");
		Usuario usuario = crearUsuario("familia@example.com", true, LocalDate.of(2026, 2, 18));
		when(usuarioRepository.findByRolAndRecordatorioRachaEmailHabilitadoTrue(Roles.ROLE_USER))
				.thenReturn(List.of(usuario));

		recordatorioRachaService.enviarRecordatoriosSiCorresponde();

		verify(emailService, never()).sendEmail(any(), any(), any());
		verify(usuarioRepository, never()).save(any(Usuario.class));
	}

	@Test
	void enviarRecordatoriosSiCorresponde_agrupaVariosAlumnosEnUnSoloCorreo() {
		fijarHora("2026-02-17T22:30:00");
		Usuario usuario = crearUsuario("familia@example.com", true, null);
		when(usuarioRepository.findByRolAndRecordatorioRachaEmailHabilitadoTrue(Roles.ROLE_USER))
				.thenReturn(List.of(usuario));
		when(alumnoRepository.findAllByEmailIgnoreCaseAndActivoTrue("familia@example.com"))
				.thenReturn(List.of(
						crearAlumno("Ana", "Activa", "familia@example.com", 7, LocalDate.of(2026, 2, 16)),
						crearAlumno("Beto", "Activo", "familia@example.com", 4, LocalDate.of(2026, 2, 16))));

		recordatorioRachaService.enviarRecordatoriosSiCorresponde();

		ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
		verify(emailService).sendEmail(eq("familia@example.com"), any(String.class), htmlCaptor.capture());
		String html = htmlCaptor.getValue();
		assertTrue(html.contains("Ana Activa"));
		assertTrue(html.contains("Beto Activo"));
	}

	private void fijarHora(String localDateTimeIso) {
		LocalDateTime localDateTime = LocalDateTime.parse(localDateTimeIso);
		Instant instant = ZonedDateTime.of(localDateTime, zoneId).toInstant();
		Clock fixedClock = Clock.fixed(instant, zoneId);
		ReflectionTestUtils.setField(recordatorioRachaService, "clock", fixedClock);
	}

	private Usuario crearUsuario(String email, boolean habilitado, LocalDate ultimoReset) {
		Usuario usuario = new Usuario();
		usuario.setEmail(email);
		usuario.setNombre("Familia");
		usuario.setRoles(Set.of(Roles.ROLE_USER));
		usuario.setRecordatorioRachaEmailHabilitado(habilitado);
		usuario.setUltimoRecordatorioRachaReset(ultimoReset);
		return usuario;
	}

	private Alumno crearAlumno(String nombre, String apellidos, String email, int racha, LocalDate fechaCompletado) {
		Alumno alumno = new Alumno();
		alumno.setNombre(nombre);
		alumno.setApellidos(apellidos);
		alumno.setEmail(email);
		alumno.setActivo(true);
		alumno.setRachaRetoDiario(racha);
		alumno.setFechaRetoDiarioCompletado(toDate(fechaCompletado));
		return alumno;
	}

	private Date toDate(LocalDate fecha) {
		return Date.from(fecha.atStartOfDay(zoneId).toInstant());
	}
}
